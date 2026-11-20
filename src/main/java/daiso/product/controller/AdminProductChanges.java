package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
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

    HistoryPage list(Connection connection, String productId, Long cursor) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ch.*, COALESCE(m.login_id, ch.admin_member_id) AS actor "
                + "FROM product_admin_changes ch LEFT JOIN members m ON m.member_id = ch.admin_member_id WHERE 1 = 1");
        if (!productId.isEmpty()) sql.append(" AND ch.product_id = ?");
        if (cursor != null) sql.append(" AND ch.change_id < ?");
        sql.append(" ORDER BY ch.change_id DESC LIMIT ?");
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (!productId.isEmpty()) query.setString(index++, productId);
            if (cursor != null) query.setLong(index++, cursor);
            query.setInt(index, PAGE_SIZE + 1);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) entries.add(new Entry(rows));
            }
        }
        Long next = null;
        if (entries.size() > PAGE_SIZE) {
            entries.remove(PAGE_SIZE);
            next = entries.get(PAGE_SIZE - 1).id;
        }
        return new HistoryPage(entries, next);
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
        final Long next;
        HistoryPage(List<Entry> entries, Long next) {
            this.entries = List.copyOf(entries);
            this.next = next;
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

        Entry(ResultSet rows) throws SQLException {
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
            stockOperation = "STOCK_ADJUST".equals(action) ? rows.getString("stock_operation") : null;
            stockReason = "STOCK_ADJUST".equals(action) ? rows.getString("stock_reason") : null;
        }

        public boolean priceChanged() { return !Objects.equals(beforePrice, afterPrice); }
        public boolean stockChanged() { return !Objects.equals(beforeStock, afterStock); }
        public boolean statusChanged() { return !Objects.equals(beforeStatus, afterStatus); }
    }
}
