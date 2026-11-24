package daiso.order.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Restores online stock within the caller's locked order transaction. */
final class OnlineStockRestoration {
    enum Reason {
        ORDER_CANCEL("주문 취소"), ORDER_REFUND("반품·환불");

        final String label;

        Reason(String label) {
            this.label = label;
        }
    }

    private OnlineStockRestoration() { }

    static void requireSchema(Connection connection) throws SQLException {
        // Check both ALTER statements before reserving the order and calling the payment gateway.
        String sql = "SELECT o.stock_restored_at, ch.order_no FROM orders o "
                + "CROSS JOIN product_admin_changes ch LIMIT 0";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.executeQuery().close();
        } catch (SQLException error) {
            if (error.getErrorCode() == 1054 || error.getErrorCode() == 1146) {
                throw new IllegalArgumentException("재고 복구 마이그레이션 적용 후 다시 시도해 주세요.", error);
            }
            throw error;
        }
    }

    static void restore(Connection connection, long orderId, Reason reason) throws SQLException {
        String orderNo;
        String lock = "SELECT order_no, stock_restored_at FROM orders "
                + "WHERE order_id = ? AND stock_deducted = TRUE FOR UPDATE";
        try (PreparedStatement query = connection.prepareStatement(lock)) {
            query.setLong(1, orderId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("재고를 차감한 주문을 찾을 수 없습니다.");
                if (row.getTimestamp("stock_restored_at") != null) return;
                orderNo = row.getString("order_no");
            }
        }

        // Lock products in the same order used by payment approval.
        String items = "SELECT product_id, SUM(quantity) AS quantity, MIN(quantity) AS min_quantity "
                + "FROM order_items WHERE order_id = ? GROUP BY product_id ORDER BY product_id";
        String product = "SELECT stock_quantity FROM products WHERE product_id = ? FOR UPDATE";
        String update = "UPDATE products SET stock_quantity = ? WHERE product_id = ?";
        String audit = "INSERT INTO product_admin_changes "
                + "(product_id, product_code, product_name, admin_member_id, action, before_stock, after_stock, "
                + "stock_operation, stock_reason, order_no) "
                + "SELECT product_id, COALESCE(product_code, ''), name, NULL, 'STOCK_RESTORE', ?, ?, ?, ?, ? "
                + "FROM products WHERE product_id = ?";

        try (PreparedStatement purchased = connection.prepareStatement(items);
             PreparedStatement stock = connection.prepareStatement(product);
             PreparedStatement save = connection.prepareStatement(update);
             PreparedStatement history = connection.prepareStatement(audit)) {
            purchased.setLong(1, orderId);
            try (ResultSet rows = purchased.executeQuery()) {
                if (!rows.next()) throw new SQLException("복구할 주문 상품이 없습니다.");
                do {
                    String productId = rows.getString("product_id");
                    long quantity = rows.getLong("quantity");
                    if (rows.getInt("min_quantity") <= 0 || quantity > Integer.MAX_VALUE) {
                        throw new SQLException("복구할 상품 수량이 유효하지 않습니다.");
                    }
                    stock.setString(1, productId);
                    int before;
                    try (ResultSet current = stock.executeQuery()) {
                        if (!current.next()) throw new SQLException("복구할 상품을 찾을 수 없습니다.");
                        before = current.getInt("stock_quantity");
                    }
                    if (before < 0 || quantity > Integer.MAX_VALUE - (long) before) {
                        throw new SQLException("복구 후 재고가 허용 범위를 벗어납니다.");
                    }
                    int after = before + (int) quantity;
                    save.setInt(1, after);
                    save.setString(2, productId);
                    if (save.executeUpdate() != 1) throw new SQLException("온라인 재고를 복구하지 못했습니다.");

                    history.setInt(1, before);
                    history.setInt(2, after);
                    history.setString(3, reason.name());
                    history.setString(4, reason.label);
                    history.setString(5, orderNo);
                    history.setString(6, productId);
                    if (history.executeUpdate() != 1) throw new SQLException("재고 복구 이력을 저장하지 못했습니다.");
                } while (rows.next());
            }
        }

        try (PreparedStatement mark = connection.prepareStatement("UPDATE orders SET stock_restored_at = CURRENT_TIMESTAMP(6) "
                + "WHERE order_id = ? AND stock_deducted = TRUE AND stock_restored_at IS NULL")) {
            mark.setLong(1, orderId);
            if (mark.executeUpdate() != 1) throw new SQLException("재고 복구 완료 시각을 저장하지 못했습니다.");
        }
    }
}
