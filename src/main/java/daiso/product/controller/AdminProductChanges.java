package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AdminProductChanges {
    private static final int PAGE_SIZE = 30;
    enum Action { CREATE, UPDATE, BULK_STATUS, DELETE, STOCK_ADJUST }

    void recordStock(Connection connection, String administrator, ProductDTO before, ProductDTO after,
            String operation, String reason) throws SQLException {
        record(connection, administrator, Action.STOCK_ADJUST, before, after, operation, reason);
    }

    void record(Connection connection, String administrator, Action action, ProductDTO before, ProductDTO after)
            throws SQLException {
        record(connection, administrator, action, before, after, null, null);
    }

    private void record(Connection connection, String administrator, Action action, ProductDTO before, ProductDTO after,
            String operation, String reason) throws SQLException {
        if (action == Action.UPDATE && before.getPrice() == after.getPrice()
                && before.getStockQuantity() == after.getStockQuantity()
                && Objects.equals(before.getStatus(), after.getStatus())) return;

        ProductDTO product = after == null ? before : after;
        String sql = "INSERT INTO product_admin_changes (product_id, product_code, product_name, admin_member_id, "
                + "action, before_price, after_price, before_stock, after_stock, before_status, after_status"
                + (operation == null ? ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        : ", stock_operation, stock_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, product.getProductId());
            query.setString(2, product.getProductCode());
            query.setString(3, product.getName());
            query.setString(4, administrator);
            query.setString(5, action.name());
            setSnapshot(query, 6, before);
            setSnapshot(query, 7, after);
            if (operation != null) {
                query.setString(12, operation);
                query.setString(13, reason);
            }
            query.executeUpdate();
        }
    }

    boolean categoryAuditReady(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'product_admin_changes' AND COLUMN_NAME IN ('before_category_id','after_category_id')";
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet row = query.executeQuery()) {
            return row.next() && row.getInt(1) == 2;
        }
    }

    void recordCategory(Connection connection, String manager, ProductDTO before, String category) throws SQLException {
        String sql = "INSERT INTO product_admin_changes (product_id, product_code, product_name, admin_member_id, "
                + "action, before_category_id, after_category_id) VALUES (?, ?, ?, ?, 'UPDATE', ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, before.getProductId());
            query.setString(2, before.getProductCode());
            query.setString(3, before.getName());
            query.setString(4, manager);
            query.setString(5, before.getCategoryId());
            query.setString(6, category);
            query.executeUpdate();
        }
    }

    void recordThreshold(Connection connection, String manager, ProductDTO product, int after, String reason)
            throws SQLException {
        String sql = "INSERT INTO product_admin_changes (product_id, product_code, product_name, admin_member_id, "
                + "action, before_threshold, after_threshold, threshold_reason) VALUES (?, ?, ?, ?, 'UPDATE', ?, ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, product.getProductId());
            query.setString(2, product.getProductCode());
            query.setString(3, product.getName());
            query.setString(4, manager);
            query.setInt(5, product.getLowStockThreshold());
            query.setInt(6, after);
            query.setString(7, reason);
            query.executeUpdate();
        }
    }

    HistoryPage list(Connection connection, String productId, Long cursor, Map<String, String> filters, int requestedPage)
            throws SQLException {
        String scope = " FROM product_admin_changes ch LEFT JOIN members m ON m.member_id = ch.admin_member_id WHERE 1 = 1";
        List<Object> parameters = new ArrayList<>();
        if (!productId.isEmpty()) { scope += " AND ch.product_id = ?"; parameters.add(productId); }
        if (cursor != null) { scope += " AND ch.change_id < ?"; parameters.add(cursor); }
        String keyword = filters.get("historyQuery");
        if (!keyword.isEmpty()) {
            scope += " AND (LOCATE(?, ch.product_name) > 0 OR LOCATE(?, ch.product_code) > 0)";
            parameters.add(keyword);
            parameters.add(keyword);
        }
        String actor = filters.get("historyActor");
        if (!actor.isEmpty()) {
            scope += " AND (ch.admin_member_id = ? OR m.login_id = ?)";
            parameters.add(actor);
            parameters.add(actor);
        }
        boolean thresholdReady = AdminInventoryBatchController.hasColumn(connection, "product_admin_changes", "threshold_reason");
        String reference = filters.get("historyReference");
        if (!reference.isEmpty()) {
            if (AdminInventoryBatchController.hasColumn(connection, "product_admin_changes", "order_no")) {
                scope += " AND ch.order_no = ?";
                parameters.add(reference);
            } else scope += " AND FALSE";
        }
        String type = filters.get("historyType");
        if ("THRESHOLD".equals(type)) scope += thresholdReady ? " AND ch.before_threshold IS NOT NULL" : " AND FALSE";
        else if (!type.isEmpty()) {
            scope += " AND ch.stock_operation = ?";
            parameters.add(type);
        }
        String periodValue = filters.get("historyPeriod");
        AdminReviewController.Period period = periodValue.isEmpty() ? AdminReviewController.Period.ALL
                : AdminReviewController.Period.valueOf(periodValue);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate start = period == AdminReviewController.Period.CUSTOM ? date(filters.get("historyStart")) : period.start(today);
        LocalDate end = period == AdminReviewController.Period.CUSTOM ? date(filters.get("historyEnd")) : today;
        if (start != null) {
            if (start.isAfter(end)) throw new IllegalArgumentException("시작일이 종료일보다 늦습니다.");
            scope += " AND ch.changed_at >= ? AND ch.changed_at < ?";
            parameters.add(start.atStartOfDay());
            parameters.add(end.plusDays(1).atStartOfDay());
        }
        int pages;
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*)" + scope)) {
            bind(query, parameters);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                pages = (int) Math.max(1, (rows.getLong(1) + PAGE_SIZE - 1) / PAGE_SIZE);
            }
        }
        int page = Math.min(requestedPage, pages);
        boolean categoryReady = categoryAuditReady(connection);
        List<Entry> entries = new ArrayList<>();
        boolean oldest = "oldest".equals(filters.get("historyOrder"));
        String order = oldest ? " ASC" : " DESC";
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ch.*, COALESCE(m.login_id, ch.admin_member_id) AS actor" + scope
                + " ORDER BY ch.changed_at" + order + ", ch.change_id" + order + " LIMIT ? OFFSET ?")) {
            bind(query, parameters);
            query.setInt(parameters.size() + 1, PAGE_SIZE);
            query.setLong(parameters.size() + 2, (page - 1L) * PAGE_SIZE);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) entries.add(new Entry(rows, categoryReady, thresholdReady));
            }
        }
        return new HistoryPage(entries, page, pages);
    }

    private static LocalDate date(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1000 || date.isAfter(LocalDate.of(9999, 12, 30))) throw new IllegalArgumentException("날짜 범위를 확인해 주세요.");
            return date;
        } catch (java.time.format.DateTimeParseException error) {
            throw new IllegalArgumentException("날짜 입력을 확인해 주세요.", error);
        }
    }

    private static void bind(PreparedStatement query, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) query.setObject(i + 1, parameters.get(i));
    }

    private static void setSnapshot(PreparedStatement query, int index, ProductDTO product) throws SQLException {
        if (product == null) {
            query.setNull(index, Types.INTEGER);
            query.setNull(index + 2, Types.INTEGER);
            query.setNull(index + 4, Types.VARCHAR);
        } else {
            query.setInt(index, product.getPrice());
            query.setInt(index + 2, product.getStockQuantity());
            query.setString(index + 4, product.getStatus());
        }
    }

    public static final class HistoryPage {
        public final List<Entry> entries;
        final int page;
        final int pages;

        private HistoryPage(List<Entry> entries, int page, int pages) {
            this.entries = List.copyOf(entries);
            this.page = page;
            this.pages = pages;
        }
    }

    public static final class Entry {
        final long id;
        public final LocalDateTime at;
        public final String productCode;
        public final String productName;
        public final String actor;
        public final String action;
        public final Integer beforePrice;
        public final Integer afterPrice;
        public final Integer beforeStock;
        public final Integer afterStock;
        public final String beforeStatus;
        public final String afterStatus;
        public final String stockOperation;
        public final String stockReason;
        public final String orderNo;

        public final String beforeCategory;
        public final String afterCategory;

        public final Integer beforeThreshold;
        public final Integer afterThreshold;
        public final String thresholdReason;

        Entry(ResultSet rows, boolean categoryReady, boolean thresholdReady) throws SQLException {
            beforeThreshold = thresholdReady ? rows.getObject("before_threshold", Integer.class) : null;
            afterThreshold = thresholdReady ? rows.getObject("after_threshold", Integer.class) : null;
            thresholdReason = thresholdReady ? rows.getString("threshold_reason") : null;
            beforeCategory = categoryReady ? rows.getString("before_category_id") : null;
            afterCategory = categoryReady ? rows.getString("after_category_id") : null;
            id = rows.getLong("change_id");
            at = rows.getObject("changed_at", LocalDateTime.class);
            productCode = rows.getString("product_code");
            productName = rows.getString("product_name");
            actor = rows.getString("actor");
            action = rows.getString("action");
            beforePrice = rows.getObject("before_price", Integer.class);
            afterPrice = rows.getObject("after_price", Integer.class);
            beforeStock = rows.getObject("before_stock", Integer.class);
            afterStock = rows.getObject("after_stock", Integer.class);
            beforeStatus = rows.getString("before_status");
            afterStatus = rows.getString("after_status");
            boolean automatic = "STOCK_RESTORE".equals(action);
            boolean stockHistory = automatic || "STOCK_ADJUST".equals(action);
            stockOperation = stockHistory ? rows.getString("stock_operation") : null;
            stockReason = stockHistory ? rows.getString("stock_reason") : null;
            orderNo = automatic ? rows.getString("order_no") : null;
        }

        public boolean priceChanged() { return !Objects.equals(beforePrice, afterPrice); }
        public boolean stockChanged() { return !Objects.equals(beforeStock, afterStock); }
        public boolean statusChanged() { return !Objects.equals(beforeStatus, afterStatus); }
    }
}
