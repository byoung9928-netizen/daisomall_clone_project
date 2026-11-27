package daiso.order.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Store-first locking shared by pickup state transitions and manager stocktakes. */
public final class PickupStockTransaction {
    private static final String PICKUP_ORDER =
            "SELECT o.order_id, o.pickup_store_id FROM orders o WHERE o.fulfillment_type = 'PICKUP' ";
    private static final String COLUMNS =
            "SELECT s.pickup_revision, h.actor_type, h.order_id, h.order_no, h.order_item_id, "
            + "h.pickup_event_slot, h.counted_physical, h.counted_pickup_units "
            + "FROM store_locations s CROSS JOIN store_inventory_history h LIMIT 0";

    public enum Movement {
        PICKUP_PAYMENT("픽업 결제 차감", -1),
        PICKUP_CANCEL("픽업 주문 취소 복구", 1),
        PICKUP_REFUND("픽업 환불 복구", 1);

        private final String label;
        private final int direction;

        Movement(String label, int direction) {
            this.label = label;
            this.direction = direction;
        }
    }

    private PickupStockTransaction() { }

    public static boolean enabled(Connection connection) throws SQLException {
        if (!Boolean.getBoolean("daisomall.pickupInventoryDelta")) return false;
        return hasHistorySchema(connection) && hasCarriedConstraint(connection);
    }

    public static boolean hasHistorySchema(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(COLUMNS);
             ResultSet unused = query.executeQuery()) {
            return true;
        } catch (SQLException error) {
            if (error.getErrorCode() == 1054) return false;
            throw error;
        }
    }

    private static boolean hasCarriedConstraint(Connection connection) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND ((TABLE_NAME = 'store_inventory' AND CONSTRAINT_NAME = 'chk_store_inventory_carried_v2') "
                + "OR (TABLE_NAME = 'store_inventory_history' AND CONSTRAINT_NAME IN ("
                + "'chk_store_inventory_history_actor_v4','chk_store_inventory_history_count_snapshot',"
                + "'uq_store_inventory_pickup_event'))) AND ENFORCED = 'YES'";
        try (PreparedStatement query = connection.prepareStatement(sql);
             ResultSet rows = query.executeQuery()) {
            int count = 0;
            while (rows.next()) count++;
            return count == 4;
        }
    }

    public static String lockStore(Connection connection, String storeId) throws SQLException {
        if (connection.getAutoCommit()) throw new SQLException("픽업 매장 잠금에 트랜잭션이 필요합니다.");
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT CAST(pickup_revision AS CHAR) FROM store_locations WHERE store_id = ? FOR UPDATE")) {
            query.setString(1, storeId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("픽업 매장을 찾을 수 없습니다.");
                return row.getString(1);
            }
        }
    }

    public static String lockIfEnabled(Connection connection, String storeId) throws SQLException {
        return storeId == null || !enabled(connection) ? null : lockStore(connection, storeId);
    }

    static String lockOrder(Connection connection, long orderId) throws SQLException {
        return lockOrder(connection, "AND o.order_id = ?", orderId);
    }

    static String lockOrder(Connection connection, String memberId, String orderNo) throws SQLException {
        return lockOrder(connection, "AND o.member_id = ? AND o.order_no = ?", memberId, orderNo);
    }

    static String lockReturn(Connection connection, long returnId) throws SQLException {
        return lockOrder(connection, "AND o.order_id = (SELECT order_id FROM order_returns WHERE return_id = ?)", returnId);
    }

    private static String lockOrder(Connection connection, String condition, Object... keys) throws SQLException {
        if (!enabled(connection)) return null;
        long orderId;
        String storeId;
        try (PreparedStatement query = connection.prepareStatement(PICKUP_ORDER + condition)) {
            for (int index = 0; index < keys.length; index++) query.setObject(index + 1, keys[index]);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                orderId = row.getLong("order_id");
                storeId = row.getString("pickup_store_id");
            }
        }
        lockStore(connection, storeId);
        try (PreparedStatement query = connection.prepareStatement(
                PICKUP_ORDER + "AND o.order_id = ? AND o.pickup_store_id = ? FOR UPDATE")) {
            query.setLong(1, orderId);
            query.setString(2, storeId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("픽업 주문의 매장 정보가 변경되었습니다.");
            }
        }
        return storeId;
    }

    public static void advance(Connection connection, String storeId) throws SQLException {
        if (storeId == null) return;
        try (PreparedStatement save = connection.prepareStatement(
                "UPDATE store_locations SET pickup_revision = pickup_revision + 1 WHERE store_id = ?")) {
            save.setString(1, storeId);
            if (save.executeUpdate() != 1) throw new SQLException("픽업 매장 상태를 갱신하지 못했습니다.");
        }
    }

    static void adjustItems(Connection connection, long orderId, String storeId, Movement movement)
            throws SQLException {
        // Order/product identifiers use the same collation as the inventory composite key.
        String items = "SELECT o.order_no, i.order_item_id, i.product_id, i.quantity "
                + "FROM orders o JOIN order_items i ON i.order_id = o.order_id "
                + "WHERE o.order_id = ? AND o.fulfillment_type = 'PICKUP' AND o.pickup_store_id = ? "
                + "ORDER BY i.product_id";
        String stock = "SELECT i.quantity, i.is_carried, p.name, COALESCE(p.product_code, '') AS product_code "
                + "FROM store_inventory i JOIN products p ON p.product_id = i.product_id "
                + "WHERE i.store_id = ? AND i.product_id = ? FOR UPDATE OF i";
        String save = "UPDATE store_inventory SET quantity = ?, version = version + 1 "
                + "WHERE store_id = ? AND product_id = ?";
        String audit = "INSERT INTO store_inventory_history (store_id, product_id, product_reference, product_name, "
                + "product_code, change_type, before_quantity, after_quantity, reason, order_id, order_no, order_item_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement orderItems = connection.prepareStatement(items);
             PreparedStatement inventory = connection.prepareStatement(stock);
             PreparedStatement update = connection.prepareStatement(save);
             PreparedStatement history = connection.prepareStatement(audit)) {
            orderItems.setLong(1, orderId);
            orderItems.setString(2, storeId);
            try (ResultSet rows = orderItems.executeQuery()) {
                if (!rows.next()) throw new SQLException("픽업 주문상품이 없습니다.");
                do {
                    String productId = rows.getString("product_id");
                    int units = rows.getInt("quantity");
                    if (units <= 0) throw new SQLException("픽업 주문 수량이 잘못됐습니다.");
                    inventory.setString(1, storeId);
                    inventory.setString(2, productId);
                    try (ResultSet current = inventory.executeQuery()) {
                        if (!current.next()) throw new SQLException("픽업 주문의 매장 재고가 없습니다.");
                        int before = current.getInt("quantity");
                        long next = before + (long) units * movement.direction;
                        if ((movement == Movement.PICKUP_PAYMENT && !current.getBoolean("is_carried"))
                                || next < 0 || next > Integer.MAX_VALUE) {
                            throw new SQLException("픽업 매장 재고를 변경할 수 없습니다.");
                        }
                        int after = (int) next;
                        update.setInt(1, after);
                        update.setString(2, storeId);
                        update.setString(3, productId);
                        if (update.executeUpdate() != 1) throw new SQLException("픽업 재고 변경에 실패했습니다.");

                        history.setString(1, storeId);
                        history.setString(2, productId);
                        history.setString(3, productId);
                        history.setString(4, current.getString("name"));
                        history.setString(5, current.getString("product_code"));
                        history.setString(6, movement.name());
                        history.setInt(7, before);
                        history.setInt(8, after);
                        history.setString(9, movement.label);
                        history.setLong(10, orderId);
                        history.setString(11, rows.getString("order_no"));
                        history.setLong(12, rows.getLong("order_item_id"));
                        if (history.executeUpdate() != 1) throw new SQLException("픽업 재고 이력 저장에 실패했습니다.");
                    }
                } while (rows.next());
            }
        }
    }
}

