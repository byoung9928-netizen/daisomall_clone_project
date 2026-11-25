package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class AdminStockTrends {
    private AdminStockTrends() { }

    static Trend load(Connection connection, String product, String store, int days)
            throws SQLException {
        boolean offline = !store.isEmpty();
        boolean fulfillment = AdminInventoryBatchController.hasColumn(connection, "orders", "fulfillment_type");
        boolean salesReady = !offline || (fulfillment
                && AdminInventoryBatchController.hasColumn(connection, "orders", "pickup_store_id"));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate from = today.minusDays(days - 1L);
        LocalDate end = today.plusDays(1);
        String changes = offline
                ? "SELECT DATE(changed_at) AS day, after_quantity - before_quantity AS delta "
                    + "FROM store_inventory_history WHERE store_id = ? AND product_reference = ? "
                    + "AND change_type IN ('RECEIVE','ISSUE','DAMAGE','RETURN','COUNT') "
                : "SELECT DATE(changed_at) AS day, after_stock - before_stock AS delta "
                    + "FROM product_admin_changes WHERE product_id = ? AND before_stock <> after_stock ";
        String sales = " UNION ALL SELECT DATE(s.approved_at), 0, 0, SUM(s.quantity) FROM "
                + "(SELECT MIN(p.approved_at) AS approved_at, MAX(i.quantity) AS quantity FROM order_items i "
                + "JOIN orders o ON o.order_id = i.order_id JOIN payment_attempts p ON p.order_id = o.order_id "
                + "WHERE i.product_id = ? AND o.stock_deducted = TRUE AND o.canceled_at IS NULL "
                + "AND p.status IN ('APPROVED','REFUNDING') "
                + (offline ? "AND o.fulfillment_type = 'PICKUP' AND o.pickup_store_id = ? "
                    : fulfillment ? "AND o.fulfillment_type = 'DELIVERY' " : "")
                + "AND p.approved_at >= ? AND p.approved_at < ? GROUP BY i.order_id) s GROUP BY DATE(s.approved_at)";
        String sql = "WITH movements AS (" + changes + "AND changed_at >= ? AND changed_at < ?) "
                + "SELECT day, SUM(received) AS received, SUM(issued) AS issued, SUM(sold) AS sold FROM ("
                + "SELECT day, SUM(GREATEST(delta, 0)) AS received, SUM(GREATEST(-delta, 0)) AS issued, 0 AS sold "
                + "FROM movements WHERE delta <> 0 GROUP BY day" + (salesReady ? sales : "")
                + ") series GROUP BY day ORDER BY day";
        List<Day> series = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            int pos = 1;
            if (offline) query.setString(pos++, store);
            query.setString(pos++, product);
            query.setObject(pos++, from.atStartOfDay());
            query.setObject(pos++, end.atStartOfDay());
            if (salesReady) {
                query.setString(pos++, product);
                if (offline) query.setString(pos++, store);
                query.setObject(pos++, from.atStartOfDay());
                query.setObject(pos, end.atStartOfDay());
            }
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) series.add(new Day(row));
            }
        }
        return new Trend(series, from, today, salesReady);
    }

    public static final class Trend {
        public final List<Day> series;
        public final LocalDate from;
        public final LocalDate through;
        public final boolean salesReady;
        public final long maximum;

        private Trend(List<Day> series, LocalDate from, LocalDate through, boolean salesReady) {
            this.series = List.copyOf(series);
            this.from = from;
            this.through = through;
            this.salesReady = salesReady;
            this.maximum = Math.max(1, series.stream().mapToLong(i ->
                    Math.max(i.sold, Math.max(i.received, i.issued))).max().orElse(0));
        }
    }

    public static final class Day {
        public final LocalDate date;
        public final long received;
        public final long issued;
        public final long sold;

        private Day(ResultSet row) throws SQLException {
            date = row.getObject("day", LocalDate.class);
            received = row.getLong("received");
            issued = row.getLong("issued");
            sold = row.getLong("sold");
        }
    }
}
