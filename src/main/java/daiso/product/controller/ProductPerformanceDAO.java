package daiso.product.controller;

import daiso.product.dao.ReviewVisibility;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class ProductPerformanceDAO {
    private static final String EVENT_TABLE = "product_behavior_events";
    private static final String BEHAVIOR_COUNTS = "COUNT(CASE WHEN e.event_type = 'VIEW' THEN 1 END) views,"
            + "COUNT(DISTINCT CASE WHEN e.event_type = 'VIEW' THEN e.member_id END) viewers,"
            + "COUNT(CASE WHEN e.event_type = 'WISHLIST_ADD' THEN 1 END) wish_add,"
            + "COUNT(CASE WHEN e.event_type = 'WISHLIST_REMOVE' THEN 1 END) wish_remove,"
            + "COUNT(CASE WHEN e.event_type = 'CART_ADD' THEN 1 END) cart_add,"
            + "COUNT(CASE WHEN e.event_type = 'CART_REMOVE' THEN 1 END) cart_remove";
    private static final String ZERO_BEHAVIOR = "0 views,0 viewers,0 wish_add,0 wish_remove,0 cart_add,0 cart_remove";

    static final class Coverage {
        final boolean ready;
        final LocalDate firstDay;

        Coverage(boolean ready, LocalDate firstDay) {
            this.ready = ready;
            this.firstDay = firstDay;
        }
    }

    Coverage coverage(Connection conn) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT MIN(occurred_at) FROM " + EVENT_TABLE);
                ResultSet row = query.executeQuery()) {
            row.next();
            LocalDateTime first = row.getObject(1, LocalDateTime.class);
            return new Coverage(true, first == null ? null : first.atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(AdminProductPerformanceController.SEOUL).toLocalDate());
        } catch (SQLException error) {
            if (error.getErrorCode() == 1146) return new Coverage(false, null);
            throw error;
        }
    }

    // Demo actions inherit the existing fixture member ID, login and email. Demo purchases also carry their own markers.
    static String demoMember(String id) {
        return "EXISTS (SELECT 1 FROM members dm WHERE dm.member_id = " + id
                + " AND dm.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'"
                + " AND dm.login_id = CONCAT('demorank', RIGHT(dm.member_id, 4))"
                + " AND dm.email = CONCAT(dm.login_id, '@example.invalid'))";
    }

    static String liveMember(String id, boolean includeDemo) {
        return includeDemo ? "" : " AND NOT " + demoMember(id);
    }

    static Query scope(AdminProductFilter filter, String excluded) {
        ProductDAO.AdminScope scope = ProductDAO.adminScope(filter, true);
        Query query = new Query(scope.prefix.isEmpty() ? "WITH " : scope.prefix.stripTrailing() + ", ");
        query.sql.append("scoped AS (SELECT p.product_id, p.name, p.product_code, p.category_id, p.status, p.stock_quantity")
                .append(scope.from).append(scope.where);
        query.args.addAll(scope.parameters);
        if (!excluded.isEmpty()) query.append(" AND p.category_id=? AND p.product_id<>?", filter.category, excluded);
        return query.append(")");
    }

    // Sales are attributed to the original payment approval day. Completed full reversals leave that cohort.
    private static void sales(Query query, AdminProductPerformanceController.Filter filter, String group) {
        query.append(", valid_orders AS (SELECT o.order_id,o.member_id,MIN(pay.approved_at) approved_at"
                + " FROM orders o JOIN payment_attempts pay ON pay.order_id=o.order_id"
                + " WHERE o.canceled_at IS NULL AND ((o.status='PAID' AND pay.status='APPROVED')"
                + " OR (o.status IN ('REFUNDING','CANCELLING') AND pay.status=o.status))"
                + " AND pay.method='KAKAOPAY' AND pay.tid IS NOT NULL AND pay.tid<>''"
                + " AND pay.approved_at>=? AND pay.approved_at<? AND pay.amount=o.total_amount"
                + " AND NOT EXISTS (SELECT 1 FROM payment_attempts earlier WHERE earlier.order_id=o.order_id"
                + " AND earlier.status=pay.status AND earlier.method='KAKAOPAY' AND earlier.amount=o.total_amount"
                + " AND earlier.tid IS NOT NULL AND earlier.tid<>'' AND earlier.approved_at<?"
                + (filter.includeDemo ? "" : " AND earlier.tid NOT REGEXP '^DEMO-TID-'") + ")"
                + " AND NOT EXISTS (SELECT 1 FROM order_returns r WHERE r.order_id=o.order_id AND r.status='REFUNDED')"
                + liveMember("o.member_id", filter.includeDemo)
                + (filter.includeDemo ? "" : " AND o.order_no NOT REGEXP '^DEMO_RANK_V1_'"
                        + " AND o.checkout_token NOT REGEXP '^DEMO-CHECKOUT-' AND pay.tid NOT REGEXP '^DEMO-TID-'")
                + " GROUP BY o.order_id,o.member_id)"
                + ", sales AS (SELECT " + group + " AS bucket,"
                + " COUNT(DISTINCT v.member_id) buyers,SUM(i.quantity) units,SUM(i.item_total) revenue"
                + " FROM valid_orders v JOIN order_items i ON i.order_id=v.order_id"
                + " JOIN scoped s ON s.product_id=i.product_id"
                + " GROUP BY bucket)",
                filter.start.atStartOfDay(), filter.endExclusive.atStartOfDay(), filter.start.atStartOfDay());
    }

    private static void behavior(Query query, AdminProductPerformanceController.Filter filter, Coverage coverage,
            String group) {
        if (coverage.ready) {
            query.append(", acts AS (SELECT " + group + " AS bucket," + BEHAVIOR_COUNTS
                    + " FROM " + EVENT_TABLE + " e JOIN scoped s ON s.product_id=e.product_id"
                    + " WHERE e.occurred_at>=? AND e.occurred_at<?" + liveMember("e.member_id", filter.includeDemo)
                    + " GROUP BY bucket)", filter.utcStart(), filter.utcEnd());
        } else {
            query.append(", acts AS (SELECT NULL bucket," + ZERO_BEHAVIOR + " WHERE FALSE)");
        }
    }

    private static Query metrics(AdminProductPerformanceController.Filter filter, AdminProductFilter products,
            Coverage coverage, String excluded) {
        Query query = scope(products, excluded);
        sales(query, filter, "i.product_id");
        behavior(query, filter, coverage, "e.product_id");
        query.append(", metrics AS (SELECT s.*, COALESCE(a.views,0) views, COALESCE(a.viewers,0) viewers,"
                + " COALESCE(a.wish_add,0) wish_add,COALESCE(a.wish_remove,0) wish_remove,"
                + " COALESCE(a.cart_add,0) cart_add, COALESCE(a.cart_remove,0) cart_remove,"
                + " COALESCE(v.buyers,0) buyers,COALESCE(v.units,0) units,COALESCE(v.revenue,0) revenue"
                + " FROM scoped s LEFT JOIN acts a ON a.bucket=s.product_id"
                + " LEFT JOIN sales v ON v.bucket=s.product_id)");
        return query;
    }

    public static final class Summary {
        public final int products;
        public final long views, wishAdds, cartAdds, units, revenue, unsold;

        Summary(ResultSet row) throws SQLException {
            products = row.getInt("products");
            views = row.getLong("views");
            wishAdds = row.getLong("wish_add");
            cartAdds = row.getLong("cart_add");
            units = row.getLong("units");
            revenue = row.getLong("revenue");
            unsold = row.getLong("unsold");
        }
    }

    Summary summary(Connection conn, AdminProductPerformanceController.Filter filter, AdminProductFilter products,
            Coverage coverage, String excluded) throws SQLException {
        Query query = metrics(filter, products, coverage, excluded);
        query.append(" SELECT COUNT(*) products,SUM(views) views,SUM(wish_add) wish_add,SUM(cart_add) cart_add,"
                + "SUM(units) units,SUM(revenue) revenue,COUNT(CASE WHEN units=0 THEN 1 END) unsold FROM metrics");
        try (PreparedStatement statement = query.prepare(conn); ResultSet row = statement.executeQuery()) {
            row.next();
            return new Summary(row);
        }
    }

    public static final class Row {
        public final String id, name, code, category, status;
        public final int stock;
        public final long views, viewers, wishAdds, wishRemoves, cartAdds, cartRemoves, buyers, units, revenue;
        public final long currentWish, currentCart, reviews;
        public final BigDecimal rating;
        public final ProductConversionDAO.Metrics conversion;

        Row(ResultSet row) throws SQLException {
            id = row.getString("product_id");
            name = row.getString("name");
            code = row.getString("product_code");
            category = row.getString("category_id");
            status = row.getString("status");
            stock = row.getInt("stock_quantity");
            views = row.getLong("views");
            viewers = row.getLong("viewers");
            wishAdds = row.getLong("wish_add");
            wishRemoves = row.getLong("wish_remove");
            cartAdds = row.getLong("cart_add");
            cartRemoves = row.getLong("cart_remove");
            buyers = row.getLong("buyers");
            units = row.getLong("units");
            revenue = row.getLong("revenue");
            currentWish = row.getLong("current_wish");
            currentCart = row.getLong("current_cart");
            reviews = row.getLong("reviews");
            rating = row.getBigDecimal("rating");
            conversion = new ProductConversionDAO.Metrics(row, false);
        }
    }

    List<Row> rows(Connection conn, AdminProductPerformanceController.Filter filter, AdminProductFilter products,
            Coverage coverage, int limit, int offset, String detailId) throws SQLException {
        Query query = metrics(filter, products, coverage, "");
        ProductConversionDAO.rows(query, filter, coverage);
        query.append(", page_rows AS (SELECT * FROM conversion_rows");
        if (!detailId.isEmpty()) query.append(" WHERE product_id=?", detailId);
        query.append(" ORDER BY " + filter.sort.order + ",product_id");
        if (limit > 0) query.append(" LIMIT ? OFFSET ?", limit, offset);
        query.append("), wish_now AS (SELECT w.product_id,COUNT(*) members FROM product_wishlists w"
                + " JOIN page_rows p ON p.product_id=w.product_id WHERE TRUE" + liveMember("w.member_id", filter.includeDemo)
                + " GROUP BY w.product_id)"
                + ", cart_members AS (SELECT c.product_id,c.member_id FROM cart_items c JOIN page_rows p ON p.product_id=c.product_id"
                + " WHERE c.quantity>0" + liveMember("c.member_id", filter.includeDemo)
                + " UNION SELECT c.product_id,c.member_id FROM pickup_cart_items c JOIN page_rows p ON p.product_id=c.product_id"
                + " WHERE c.quantity>0" + liveMember("c.member_id", filter.includeDemo) + ")"
                + ", cart_now AS (SELECT product_id,COUNT(*) members FROM cart_members GROUP BY product_id)"
                + ", review_now AS (SELECT rv.product_id,COUNT(*) reviews,ROUND(AVG(rv.rating),2) rating"
                + " FROM product_reviews rv JOIN page_rows p ON p.product_id=rv.product_id WHERE rv.deleted_at IS NULL"
                + ReviewVisibility.PUBLISHED + liveMember("rv.member_id", filter.includeDemo) + " GROUP BY rv.product_id)"
                + " SELECT page_rows.*,COALESCE(w.members,0) current_wish,COALESCE(c.members,0) current_cart,"
                + " COALESCE(r.reviews,0) reviews,r.rating FROM page_rows"
                + " LEFT JOIN wish_now w USING(product_id) LEFT JOIN cart_now c USING(product_id)"
                + " LEFT JOIN review_now r USING(product_id) ORDER BY " + filter.sort.order + ",product_id");
        try (PreparedStatement statement = query.prepare(conn); ResultSet result = statement.executeQuery()) {
            List<Row> rows = new ArrayList<>();
            while (result.next()) rows.add(new Row(result));
            return rows;
        }
    }

    public static final class Day {
        public final LocalDate date;
        public final long views, wishAdds, wishRemoves, cartAdds, cartRemoves, units, revenue;

        Day(LocalDate date, long views, long wishAdds, long wishRemoves, long cartAdds, long cartRemoves, long units, long revenue) {
            this.date = date;
            this.views = views;
            this.wishAdds = wishAdds;
            this.wishRemoves = wishRemoves;
            this.cartAdds = cartAdds;
            this.cartRemoves = cartRemoves;
            this.units = units;
            this.revenue = revenue;
        }
    }

    List<Day> trend(Connection conn, AdminProductPerformanceController.Filter filter, Coverage coverage)
            throws SQLException {
        Query query = scope(filter.products, "");
        sales(query, filter, "DATE(v.approved_at)");
        behavior(query, filter, coverage, "DATE(e.occurred_at + INTERVAL 9 HOUR)");
        query.append(" SELECT dates.bucket,COALESCE(a.views,0) views,COALESCE(a.wish_add,0) wish_add,"
                + "COALESCE(a.wish_remove,0) wish_remove,COALESCE(a.cart_add,0) cart_add,COALESCE(a.cart_remove,0) cart_remove,"
                + " COALESCE(s.units,0) units,COALESCE(s.revenue,0) revenue"
                + " FROM (SELECT bucket FROM acts UNION SELECT bucket FROM sales) dates"
                + " LEFT JOIN acts a USING(bucket) LEFT JOIN sales s USING(bucket) ORDER BY dates.bucket");
        try (PreparedStatement statement = query.prepare(conn); ResultSet rows = statement.executeQuery()) {
            List<Day> trend = new ArrayList<>();
            LocalDate next = rows.next() ? rows.getObject("bucket", LocalDate.class) : null;
            for (LocalDate day = filter.start; day.isBefore(filter.endExclusive); day = day.plusDays(1)) {
                if (day.equals(next)) {
                    trend.add(new Day(day, rows.getLong("views"), rows.getLong("wish_add"), rows.getLong("wish_remove"),
                            rows.getLong("cart_add"), rows.getLong("cart_remove"),
                            rows.getLong("units"), rows.getLong("revenue")));
                    next = rows.next() ? rows.getObject("bucket", LocalDate.class) : null;
                } else {
                    trend.add(new Day(day, 0, 0, 0, 0, 0, 0, 0));
                }
            }
            return trend;
        }
    }

    static final class Query {
        final StringBuilder sql;
        final List<Object> args = new ArrayList<>();

        Query(String initial) {
            sql = new StringBuilder(initial);
        }

        Query append(String part, Object... params) {
            sql.append(part);
            args.addAll(List.of(params));
            return this;
        }

        PreparedStatement prepare(Connection conn) throws SQLException {
            PreparedStatement statement = conn.prepareStatement(sql.toString());
            try {
                for (int index = 0; index < args.size(); index++) statement.setObject(index + 1, args.get(index));
                return statement;
            } catch (SQLException error) {
                try { statement.close(); }
                catch (SQLException closeError) { error.addSuppressed(closeError); }
                throw error;
            }
        }
    }
}
