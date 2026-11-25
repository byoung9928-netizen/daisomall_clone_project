package daiso.admin.report;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public final class AdminSalesDAO {
    private static final String PAYMENT_STATUSES = "('APPROVED', 'REFUNDING')";

    public static final class Analysis {
        public final long[] current = new long[4];
        public final long[] previous = new long[4];
        public final List<Sale> days = new ArrayList<>();
        public final List<Sale> categories = new ArrayList<>();
        public LocalDate priorFrom;
        public LocalDate priorThrough;

        public long dayMaximum(boolean amount) {
            return days.stream().mapToLong(row -> amount ? row.amount : row.units).max().orElse(1);
        }
    }

    public static final class Sale {
        public final String label;
        public final long units;
        public final long amount;

        private Sale(ResultSet row) throws SQLException {
            label = row.getString("label");
            units = row.getLong("units");
            amount = row.getLong("amount");
        }
    }

    Analysis analyze(LocalDate from, LocalDate through) throws SQLException {
        Analysis result = new Analysis();
        long days = ChronoUnit.DAYS.between(from, through) + 1;
        LocalDate priorFrom = from.minusDays(days);
        boolean compare = priorFrom.getYear() >= 1000;
        if (compare) {
            result.priorFrom = priorFrom;
            result.priorThrough = from.minusDays(1);
        }
        String sql = "WITH RECURSIVE bounds AS (SELECT CAST(? AS DATETIME) AS prior_start, "
                + "CAST(? AS DATETIME) AS current_start, CAST(? AS DATETIME) AS current_end), "
                + "paid AS (SELECT o.order_id, MAX(o.total_amount) AS payment_total, "
                + "MIN(p.approved_at) AS approved_at, p.approved_at >= b.current_start AS current_period "
                + "FROM bounds b JOIN payment_attempts p ON p.approved_at >= b.prior_start AND p.approved_at < b.current_end "
                + "JOIN orders o ON o.order_id = p.order_id WHERE o.canceled_at IS NULL AND p.status IN "
                + PAYMENT_STATUSES + " GROUP BY o.order_id, current_period), "
                + "details AS (SELECT p.current_period, DATE(p.approved_at) AS day, i.product_id, "
                + "SUM(i.quantity) AS units, SUM(i.item_total) AS amount "
                + "FROM paid p JOIN order_items i ON i.order_id = p.order_id GROUP BY p.current_period, day, i.product_id), "
                + "category_roots AS (SELECT category_id, name AS root_name FROM categories WHERE parent_id IS NULL "
                + "UNION ALL SELECT c.category_id, r.root_name FROM categories c JOIN category_roots r ON c.parent_id = r.category_id), "
                + "totals AS (SELECT current_period, SUM(units) AS units, SUM(amount) AS amount FROM details GROUP BY current_period), "
                + "payment_totals AS (SELECT current_period, COUNT(*) AS orders, SUM(payment_total) AS payments "
                + "FROM paid GROUP BY current_period), "
                + "category_products AS (SELECT product_id, SUM(units) AS units, SUM(amount) AS amount "
                + "FROM details WHERE current_period GROUP BY product_id) "
                + "SELECT 'TOTAL' AS kind, p.current_period, '' AS label, COALESCE(t.units, 0) AS units, "
                + "COALESCE(t.amount, 0) AS amount, p.orders, p.payments "
                + "FROM payment_totals p LEFT JOIN totals t ON t.current_period = p.current_period "
                + "UNION ALL SELECT 'DAY', TRUE, CAST(day AS CHAR), SUM(units), SUM(amount), 0, 0 "
                + "FROM details WHERE current_period GROUP BY day "
                + "UNION ALL SELECT 'CATEGORY', TRUE, COALESCE(cr.root_name, '미분류'), SUM(d.units), SUM(d.amount), 0, 0 "
                + "FROM category_products d LEFT JOIN products p ON p.product_id = d.product_id "
                + "LEFT JOIN category_roots cr ON cr.category_id = p.category_id "
                + "GROUP BY COALESCE(cr.root_name, '미분류')";
        try (Connection con = dataSource().getConnection(); PreparedStatement query = con.prepareStatement(sql)) {
            query.setTimestamp(1, Timestamp.valueOf((compare ? priorFrom : from).atStartOfDay()));
            query.setTimestamp(2, Timestamp.valueOf(from.atStartOfDay()));
            query.setTimestamp(3, Timestamp.valueOf(through.plusDays(1).atStartOfDay()));
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    switch (row.getString("kind")) {
                        case "DAY": result.days.add(new Sale(row)); break;
                        case "CATEGORY": result.categories.add(new Sale(row)); break;
                        default:
                            long[] total = row.getBoolean("current_period") ? result.current : result.previous;
                            total[0] = row.getLong("amount");
                            total[1] = row.getLong("units");
                            total[2] = row.getLong("orders");
                            total[3] = row.getLong("payments");
                    }
                }
            }
        }
        result.days.sort(java.util.Comparator.comparing(row -> row.label));
        result.categories.sort(java.util.Comparator.comparingLong((Sale row) -> row.units).reversed()
                .thenComparing(row -> row.label));
        return result;
    }

    // ===== 판매순위 집계 =====
    // 결제 승인일 기준. 결제대기·취소·환불완료 주문과 배송비는 집계에서 제외한다.
    List<Object[]> list(LocalDate from, LocalDate through) throws SQLException {
        String sql = "SELECT oi.product_id, COALESCE(MAX(pr.name), MAX(oi.product_name)) AS product_name, "
                + "SUM(oi.quantity) AS units, SUM(oi.item_total) AS sales "
                + "FROM order_items oi JOIN orders o ON oi.order_id = o.order_id "
                + "LEFT JOIN products pr ON pr.product_id = oi.product_id "
                + "WHERE o.canceled_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM payment_attempts p WHERE p.order_id = o.order_id "
                + "AND p.status IN " + PAYMENT_STATUSES + " AND p.approved_at >= ? AND p.approved_at < ?) "
                + "GROUP BY oi.product_id ORDER BY units DESC, sales DESC, oi.product_id";
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = dataSource().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(through.plusDays(1).atStartOfDay()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[] {rs.getString("product_id"), rs.getString("product_name"),
                            rs.getLong("units"), rs.getLong("sales")});
                }
            }
        }
        return rows;
    }

    private DataSource dataSource() throws SQLException {
        try {
            return InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        } catch (NamingException e) {
            throw new SQLException("판매순위 DB 연결 설정을 확인해 주세요.", e);
        }
    }
}
