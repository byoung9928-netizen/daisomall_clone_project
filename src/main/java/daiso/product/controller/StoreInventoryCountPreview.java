package daiso.product.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** A consistent, read-only view of matching pickup allocations for an inventory count preview. */
final class StoreInventoryCountPreview {
    private static final Set<String> IN_FLIGHT = Set.of("APPROVING", "CANCELLING", "REFUNDING");
    private static final String ORDERS =
            "SELECT o.status, COALESCE(o.pickup_status, '') AS pickup_status, o.stock_deducted, "
            + "o.stock_restored_at IS NOT NULL AS restored, o.canceled_at IS NOT NULL AS cancelled, "
            + "COALESCE(p.status, '') AS payment_status, p.amount = o.total_amount AS matching_amount, "
            + "COALESCE(r.status, '') AS return_status, SUM(i.quantity) AS units, MIN(i.quantity) AS min_units, "
            + "o.order_id, MIN(i.order_item_id) AS item_id, COUNT(*) AS item_count, p.payment_id, p.amount, "
            + "o.total_amount, p.approved_at, r.return_id, r.processed_at, o.stock_restored_at, o.canceled_at "
            + "FROM order_items i JOIN orders o ON o.order_id = i.order_id "
            + "LEFT JOIN payment_attempts p ON p.payment_id = ("
            + "SELECT MAX(p2.payment_id) FROM payment_attempts p2 WHERE p2.order_id = o.order_id) "
            + "LEFT JOIN order_returns r ON r.order_id = o.order_id "
            + "WHERE i.product_id = ? AND o.fulfillment_type = 'PICKUP' AND o.pickup_store_id = ? "
            + "GROUP BY o.order_id, o.status, o.pickup_status, o.stock_deducted, o.stock_restored_at, "
            + "o.canceled_at, p.status, p.amount, o.total_amount, r.status, p.payment_id, p.approved_at, "
            + "r.return_id, r.processed_at ORDER BY o.order_id";

    private StoreInventoryCountPreview() {}

    /** An unresolvable order prevents a numeric result for the entire store/product pair. */
    static long allocated(Connection connection, String store, String product) throws SQLException {
        return inspect(connection, store, product, false).allocated;
    }

    static Snapshot snapshot(Connection connection, String store, String product) throws SQLException {
        return inspect(connection, store, product, true);
    }

    private static Snapshot inspect(Connection connection, String store, String product, boolean snapshot)
            throws SQLException {
        MessageDigest digest = snapshot ? digest() : null;
        try (PreparedStatement statement = connection.prepareStatement(ORDERS)) {
            statement.setString(1, product);
            statement.setString(2, store);
            try (ResultSet rows = statement.executeQuery()) {
                long allocated = 0;
                while (rows.next()) {
                    if (rows.getLong("item_count") != 1 || rows.getInt("min_units") <= 0) throw new UncertainPickup();
                    if (digest != null) {
                        for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                            String value = rows.getString(column);
                            String field = value == null ? "-" : value.length() + ":" + value;
                            digest.update(field.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    if (isAllocated(rows)) {
                        long units = rows.getLong("units");
                        if (rows.getInt("min_units") <= 0 || units > Integer.MAX_VALUE - allocated) {
                            throw new UncertainPickup();
                        }
                        allocated += units;
                    }
                }
                return new Snapshot((int) allocated, digest == null ? ""
                        : java.util.Base64.getEncoder().encodeToString(digest.digest()));
            }
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    static final class Snapshot {
        final int allocated;
        final String fingerprint;

        Snapshot(int allocated, String fingerprint) {
            this.allocated = allocated;
            this.fingerprint = fingerprint;
        }
    }

    private static boolean isAllocated(ResultSet order) throws SQLException {
        String state = order.getString("status");
        String pickup = order.getString("pickup_status");
        String payment = order.getString("payment_status");
        String returned = order.getString("return_status");
        boolean deducted = order.getBoolean("stock_deducted");
        boolean restored = order.getBoolean("restored");
        boolean cancelled = order.getBoolean("cancelled");

        if (IN_FLIGHT.contains(state) || IN_FLIGHT.contains(payment) || IN_FLIGHT.contains(returned)
                || "REQUESTED".equals(returned) || (!pickup.isEmpty() && !Set.of("READY", "PICKED_UP").contains(pickup))) {
            throw new UncertainPickup();
        }
        switch (state) {
            case "PAID":
                if (deducted && !restored && !cancelled && "APPROVED".equals(payment)
                        && order.getBoolean("matching_amount") && (returned.isEmpty() || "REJECTED".equals(returned))) {
                    return !"PICKED_UP".equals(pickup);
                }
                break;
            case "PENDING":
                if (!deducted && !restored && !cancelled && pickup.isEmpty() && returned.isEmpty()
                        && Set.of("", "PENDING", "FAILED").contains(payment)) return false;
                break;
            case "CANCELLED":
                if (cancelled && !"PICKED_UP".equals(pickup) && (returned.isEmpty() || "REJECTED".equals(returned))
                        && ((deducted && restored && "CANCELLED".equals(payment) && order.getBoolean("matching_amount"))
                            || (!deducted && !restored && pickup.isEmpty() && Set.of("", "CANCELLED", "FAILED").contains(payment)))) {
                    return false;
                }
                break;
            case "REFUNDED":
                if (cancelled && deducted == restored && "REFUNDED".equals(returned)
                        && "CANCELLED".equals(payment) && order.getBoolean("matching_amount")) return false;
                break;
            default:
                break;
        }
        throw new UncertainPickup();
    }

    static final class UncertainPickup extends IllegalStateException {
        UncertainPickup() {
            super("확인이 필요한 픽업 주문이 있어 할당 수량을 계산할 수 없습니다.");
        }
    }
}

