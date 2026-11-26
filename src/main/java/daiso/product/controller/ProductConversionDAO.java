package daiso.product.controller;

import daiso.product.controller.AdminProductPerformanceController.Filter;
import daiso.product.controller.ProductPerformanceDAO.Coverage;
import daiso.product.controller.ProductPerformanceDAO.Query;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ProductConversionDAO {
    public static final int DAYS = 7;
    private static final String WINDOW = " + INTERVAL " + DAYS + " DAY";
    private static final String[] MEMBER_FIELDS = {
        "f_view", "f_wish", "f_view_cart", "f_view_paid", "f_view_net", "f_direct", "f_via",
        "f_cart", "f_cart_paid", "f_cart_mature", "f_cart_lost"
    };
    private static final String[] ORDER_FIELDS = {"f_orders", "f_paid", "f_failed", "f_cancel", "f_refund"};
    private static final String DEMO_MARKS = "o.order_no REGEXP '^DEMO_RANK_V1_' OR o.checkout_token REGEXP '^DEMO-CHECKOUT-'";

    private ProductConversionDAO() {}

    static void append(Query query, Filter filter, Coverage coverage) {
        // The window can extend past the selected cohort end; a seven-day lookback is used only to verify demo orders.
        if (coverage.ready && coverage.firstDay != null) {
            query.append(", f_actions AS (SELECT e.product_id,e.member_id,e.event_type,e.occurred_at at_utc"
                    + " FROM product_behavior_events e JOIN scoped s USING(product_id)"
                    + " JOIN members m ON m.member_id=e.member_id AND m.role='MEMBER'"
                    + " WHERE e.occurred_at>=? AND e.occurred_at<? AND e.occurred_at<=UTC_TIMESTAMP(6)"
                    + " AND e.event_type IN ('VIEW','WISHLIST_ADD','CART_ADD')"
                    + ProductPerformanceDAO.liveMember("e.member_id", filter.includeDemo) + ")",
                    filter.utcStart().minusDays(DAYS), filter.utcEnd().plusDays(DAYS));
        } else {
            query.append(", f_actions AS (SELECT s.product_id,m.member_id,'' event_type,"
                    + "CAST(NULL AS DATETIME) at_utc FROM scoped s CROSS JOIN members m WHERE FALSE)");
        }
        query.append(", f_anchors AS (SELECT member_id,product_id,"
                + " MIN(CASE WHEN event_type='VIEW' THEN at_utc END) view_at,"
                + " MIN(CASE WHEN event_type='CART_ADD' THEN at_utc END) cart_at"
                + " FROM f_actions WHERE at_utc>=? AND at_utc<? AND event_type IN ('VIEW','CART_ADD')"
                + " GROUP BY member_id,product_id)",
                filter.utcStart(), filter.utcEnd());

        // First reduce payment attempts to one row per order, then attach purchased products.
        query.append(", f_order_lines AS (SELECT i.product_id,o.order_id,o.member_id,o.created_at,o.total_amount,"
                + " o.status,o.canceled_at,(" + DEMO_MARKS + " OR " + ProductPerformanceDAO.demoMember("o.member_id") + ") demo_order"
                + " FROM orders o JOIN order_items i USING(order_id) JOIN scoped s USING(product_id)"
                + " JOIN members m ON m.member_id=o.member_id AND m.role='MEMBER'"
                + " WHERE o.created_at>=? AND o.created_at<? AND o.created_at<=UTC_TIMESTAMP(6)+INTERVAL 9 HOUR"
                + (filter.includeDemo ? "" : " AND NOT (" + DEMO_MARKS + ")"
                    + ProductPerformanceDAO.liveMember("o.member_id", false)) + ")",
                filter.start.atStartOfDay(), filter.endExclusive.plusDays(DAYS).atStartOfDay());
        query.append(", f_order_ids AS (SELECT DISTINCT order_id,member_id,created_at,total_amount,status,canceled_at,demo_order FROM f_order_lines)"
                + ", f_payments AS (SELECT o.order_id,"
                + " MIN(CASE WHEN p.status IN ('APPROVED','CANCELLED','CANCELLING','REFUNDING')"
                + " AND p.tid<>'' AND p.amount=o.total_amount AND p.approved_at>=o.created_at"
                + " AND p.approved_at<=UTC_TIMESTAMP(6)+INTERVAL 9 HOUR THEN p.approved_at END) approved_at,"
                + " MAX(p.status IN ('FAILED','PAYMENT_FAILED')) failed,"
                + " MAX(COALESCE(p.tid REGEXP '^DEMO-TID-',0)) demo_payment,"
                + " MAX(p.amount=o.total_amount AND p.tid<>'' AND p.approved_at>=o.created_at"
                + " AND p.approved_at<=UTC_TIMESTAMP(6)+INTERVAL 9 HOUR AND"
                + " ((o.status='PAID' AND p.status='APPROVED')"
                + " OR (o.status IN ('CANCELLING','REFUNDING') AND p.status=o.status))) net_payment"
                + " FROM f_order_ids o JOIN payment_attempts p ON p.order_id=o.order_id AND p.method='KAKAOPAY'"
                + " GROUP BY o.order_id)"
                + ", f_orders AS (SELECT l.product_id,l.order_id,l.member_id,l.created_at,"
                + " l.created_at-INTERVAL 9 HOUR ordered_utc,p.approved_at-INTERVAL 9 HOUR approved_utc,"
                + " (l.status='REFUNDED' OR r.status <=> 'REFUNDED') refunded,"
                + " l.status='CANCELLED' cancelled,"
                + " (l.canceled_at IS NULL AND NOT (r.status <=> 'REFUNDED') AND COALESCE(p.net_payment,0)) net_paid,"
                + " (COALESCE(p.failed,0) OR l.status IN ('FAILED','PAYMENT_FAILED')) failed,"
                + " (l.demo_order OR COALESCE(p.demo_payment,0)) is_demo"
                + " FROM f_order_lines l LEFT JOIN f_payments p USING(order_id)"
                + " LEFT JOIN order_returns r USING(order_id)"
                + (filter.includeDemo ? "" : " WHERE COALESCE(p.demo_payment,0)=0") + ")");

        query.append(", f_matches AS (SELECT a.member_id,a.product_id,o.order_id,o.ordered_utc,o.approved_utc,o.net_paid,"
                + " COALESCE(o.ordered_utc>a.view_at AND o.approved_utc<a.view_at" + WINDOW + ",0) from_view,"
                + " COALESCE(o.ordered_utc>a.cart_at AND o.approved_utc<a.cart_at" + WINDOW + ",0) from_cart"
                + " FROM f_anchors a JOIN f_orders o USING(member_id,product_id) WHERE o.approved_utc IS NOT NULL AND"
                + " ((o.ordered_utc>a.view_at AND o.approved_utc<a.view_at" + WINDOW + ")"
                + " OR (o.ordered_utc>a.cart_at AND o.approved_utc<a.cart_at" + WINDOW + ")))"
                + ", f_purchase_flags AS (SELECT member_id,product_id,MAX(from_view) view_paid,"
                + " MAX(from_view AND net_paid) view_net,MAX(from_cart) cart_paid FROM f_matches GROUP BY member_id,product_id)"
                + ", f_first_payment AS (SELECT member_id,product_id,ordered_utc,approved_utc, ROW_NUMBER() OVER"
                + " (PARTITION BY member_id,product_id ORDER BY approved_utc,order_id) sequence_no FROM f_matches WHERE from_view)"
                + ", f_pairs AS (SELECT a.member_id,a.product_id,a.view_at,a.cart_at,"
                + actionMatch("WISHLIST_ADD", "a.view_at" + WINDOW, "<") + " wish_after_view,"
                + actionMatch("CART_ADD", "a.view_at" + WINDOW, "<") + " cart_after_view,"
                + actionMatch("CART_ADD", "first_purchase.ordered_utc", "<") + " before_first_order,"
                + " first_purchase.approved_utc first_approval,COALESCE(f.view_paid,0) view_paid,COALESCE(f.view_net,0) view_net,COALESCE(f.cart_paid,0) cart_paid,"
                + " (a.cart_at" + WINDOW + "<=UTC_TIMESTAMP(6)) cart_mature"
                + " FROM f_anchors a LEFT JOIN f_purchase_flags f USING(member_id,product_id)"
                + " LEFT JOIN f_first_payment first_purchase ON first_purchase.member_id=a.member_id"
                + " AND first_purchase.product_id=a.product_id AND first_purchase.sequence_no=1)"
                + ", f_period_orders AS (SELECT o.* FROM f_orders o WHERE o.created_at>=? AND o.created_at<?"
                + " AND (NOT o.is_demo OR EXISTS (SELECT 1 FROM f_actions e WHERE e.member_id=o.member_id"
                + " AND e.product_id=o.product_id AND e.event_type IN ('VIEW','CART_ADD')"
                + " AND e.at_utc<o.ordered_utc AND e.at_utc>=o.ordered_utc-INTERVAL " + DAYS + " DAY)))",
                filter.start.atStartOfDay(), filter.endExclusive.atStartOfDay());
    }

    private static String actionMatch(String type, String upperBound, String comparator) {
        return " EXISTS (SELECT 1 FROM f_actions e WHERE e.member_id=a.member_id AND e.product_id=a.product_id"
                + " AND e.event_type='" + type + "' AND e.at_utc>a.view_at AND e.at_utc" + comparator + upperBound + ")";
    }

    private static String count(String id, String condition, String alias) {
        return "COUNT(DISTINCT CASE WHEN " + condition + " THEN " + id + " END) " + alias;
    }

    private static String memberCounts(boolean acrossProducts) {
        String[] conditions = {"view_at IS NOT NULL", "wish_after_view", "cart_after_view", "view_paid", "view_net",
            acrossProducts ? "view_paid AND NOT route_via_cart" : "view_paid AND NOT before_first_order",
            acrossProducts ? "view_paid AND route_via_cart" : "view_paid AND before_first_order",
            "cart_at IS NOT NULL", "cart_paid",
            "cart_mature", "cart_mature AND NOT cart_paid"
                    + (acrossProducts ? " AND NOT EXISTS (SELECT 1 FROM f_pairs other"
                        + " WHERE other.member_id=f_pairs.member_id AND other.cart_mature AND other.cart_paid)" : "")};
        List<String> expressions = new ArrayList<>();
        for (int i = 0; i < conditions.length; i++) expressions.add(count("member_id", conditions[i], MEMBER_FIELDS[i]));
        return String.join(",", expressions);
    }

    private static String orderCounts() {
        return "COUNT(DISTINCT order_id) f_orders,"
                + count("order_id", "approved_utc IS NOT NULL", "f_paid") + ","
                + count("order_id", "approved_utc IS NULL AND failed", "f_failed") + ","
                + count("order_id", "approved_utc IS NOT NULL AND cancelled AND NOT refunded", "f_cancel") + ","
                + count("order_id", "approved_utc IS NOT NULL AND refunded", "f_refund");
    }

    static void rows(Query query, Filter filter, Coverage coverage) {
        append(query, filter, coverage);
        query.append(", f_product_pairs AS (SELECT product_id," + memberCounts(false) + " FROM f_pairs GROUP BY product_id)"
                + ", f_product_orders AS (SELECT product_id," + orderCounts() + " FROM f_period_orders GROUP BY product_id)"
                + ", conversion_rows AS (SELECT metrics.*," + projections("fp", MEMBER_FIELDS) + ","
                + projections("fo", ORDER_FIELDS) + " FROM metrics LEFT JOIN f_product_pairs fp USING(product_id)"
                + " LEFT JOIN f_product_orders fo USING(product_id))");
    }

    private static String projections(String table, String[] names) {
        List<String> expressions = new ArrayList<>();
        for (String name : names) expressions.add("COALESCE(" + table + "." + name + ",0) " + name);
        return String.join(",", expressions);
    }

    static Metrics summary(Connection conn, Query query, Filter filter, Coverage coverage, boolean category)
            throws SQLException {
        append(query, filter, coverage);
        if (category) {
            // Pool the member/product cohorts (weighted average), separately from the dashboard's distinct-member counts.
            query.append(", f_peer_rates AS (SELECT product_id," + memberCounts(false) + " FROM f_pairs GROUP BY product_id)"
                    + " SELECT COALESCE(SUM(f_view),0) f_view,COALESCE(SUM(f_view_paid),0) f_view_paid"
                    + " FROM f_peer_rates");
        } else {
            query.append(", f_member_routes AS (SELECT member_id route_member,before_first_order route_via_cart,"
                    + " ROW_NUMBER() OVER (PARTITION BY member_id ORDER BY first_approval,before_first_order DESC,product_id) n"
                    + " FROM f_pairs WHERE view_paid)"
                    + " SELECT pairs.*,orders.* FROM (SELECT " + memberCounts(true)
                    + " FROM f_pairs LEFT JOIN f_member_routes fr ON fr.route_member=f_pairs.member_id AND fr.n=1) pairs"
                    + " CROSS JOIN (SELECT " + orderCounts() + " FROM f_period_orders) orders");
        }
        try (PreparedStatement statement = query.prepare(conn); ResultSet row = statement.executeQuery()) {
            row.next();
            return new Metrics(row, category);
        }
    }

    public static final class Metrics {
        public final long viewed, wished, cartAfterView, viewPaid, viewNet, direct, viaCart;
        public final long cart, cartPaid, matureCart, lostCart, orders, paid, failed, cancelled, refunded;

        Metrics(ResultSet row, boolean category) throws SQLException {
            viewed = row.getLong("f_view");
            viewPaid = row.getLong("f_view_paid");
            wished = category ? 0 : row.getLong("f_wish");
            cartAfterView = category ? 0 : row.getLong("f_view_cart");
            viewNet = category ? 0 : row.getLong("f_view_net");
            direct = category ? 0 : row.getLong("f_direct");
            viaCart = category ? 0 : row.getLong("f_via");
            cart = category ? 0 : row.getLong("f_cart");
            cartPaid = category ? 0 : row.getLong("f_cart_paid");
            matureCart = category ? 0 : row.getLong("f_cart_mature");
            lostCart = category ? 0 : row.getLong("f_cart_lost");
            orders = category ? 0 : row.getLong("f_orders");
            paid = category ? 0 : row.getLong("f_paid");
            failed = category ? 0 : row.getLong("f_failed");
            cancelled = category ? 0 : row.getLong("f_cancel");
            refunded = category ? 0 : row.getLong("f_refund");
        }

        public static BigDecimal percent(long numerator, long denominator) {
            return denominator == 0 ? null : BigDecimal.valueOf(numerator).movePointRight(2)
                    .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
        }
    }
}
