package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RecommendationActivityDAO {
    public enum Kind { VIEW, WISHLIST, CART, PURCHASE }

    public static final class Signal {
        public final String productId;
        public final Kind kind;
        public final LocalDate date;

        public Signal(String productId, Kind kind, LocalDate date) {
            this.productId = productId;
            this.kind = kind;
            this.date = date;
        }
    }

    public List<Signal> findActions(Connection connection, String member, LocalDate since, int limit)
            throws SQLException {
        String sql = "SELECT product_id, activity, MAX(activity_on) AS activity_on FROM ("
                + "SELECT product_id, 'WISHLIST' AS activity, DATE(created_at) AS activity_on "
                + "FROM product_wishlists WHERE member_id = ? AND created_at >= ? "
                + "UNION ALL SELECT product_id, 'CART', DATE(COALESCE(updated_at, created_at)) "
                + "FROM cart_items WHERE member_id = ? AND quantity > 0 "
                + "AND COALESCE(updated_at, created_at) >= ? "
                + "UNION ALL SELECT product_id, 'CART', NULL FROM pickup_cart_items "
                + "WHERE member_id = ? AND quantity > 0 "
                + "UNION ALL SELECT i.product_id, 'PURCHASE', DATE(MAX(p.approved_at)) "
                + "FROM orders o JOIN payment_attempts p ON p.order_id = o.order_id "
                + "JOIN order_items i ON i.order_id = o.order_id "
                + "WHERE o.member_id = ? AND " + ApprovedPurchase.CONDITION
                + " AND p.approved_at >= ? GROUP BY i.product_id) actions "
                + "GROUP BY product_id, activity ORDER BY activity_on DESC, product_id, activity LIMIT ?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, member);
            query.setObject(2, since);
            query.setString(3, member);
            query.setObject(4, since);
            query.setString(5, member);
            query.setString(6, member);
            query.setObject(7, since);
            query.setInt(8, limit);
            return read(query);
        }
    }

    public List<Signal> findViews(Connection connection, String member, LocalDate since, int limit)
            throws SQLException {
        String sql = "SELECT product_id, 'VIEW' AS activity, last_viewed_on AS activity_on FROM product_view_history "
                + "WHERE member_id = ? AND last_viewed_on >= ? ORDER BY last_viewed_on DESC, product_id LIMIT ?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, member);
            query.setObject(2, since);
            query.setInt(3, limit);
            return read(query);
        }
    }

    public void recordView(Connection connection, String member, String product, LocalDate day) throws SQLException {
        String sql = "INSERT INTO product_view_history (member_id, product_id, last_viewed_on) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE last_viewed_on = GREATEST(last_viewed_on, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, member);
            query.setString(2, product);
            query.setObject(3, day);
            query.setObject(4, day);
            query.executeUpdate();
        }
    }

    public static boolean viewTableMissing(SQLException error) {
        return error.getErrorCode() == 1146 && "42S02".equals(error.getSQLState());
    }

    private static List<Signal> read(PreparedStatement query) throws SQLException {
        List<Signal> result = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) result.add(new Signal(rows.getString("product_id"),
                    Kind.valueOf(rows.getString("activity")), rows.getObject("activity_on", LocalDate.class)));
        }
        return result;
    }
}
