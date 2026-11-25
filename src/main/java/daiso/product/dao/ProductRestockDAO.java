package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ProductRestockDAO {
    public boolean available(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT request_id FROM product_restock_requests LIMIT 0")) {
            statement.executeQuery().close();
            return true;
        } catch (SQLException error) {
            if (error.getErrorCode() == 1146) return false;
            throw error;
        }
    }

    public void subscribe(Connection connection, String member, String product) throws SQLException {
        String sql = "INSERT INTO product_restock_requests (member_id, product_id) SELECT ?, ? "
                + "WHERE NOT EXISTS (SELECT 1 FROM product_restock_requests "
                + "WHERE product_id = ? AND member_id = ? AND pending_slot = 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, member);
            statement.setString(2, product);
            statement.setString(3, product);
            statement.setString(4, member);
            statement.executeUpdate();
        }
    }

    public List<Entry> inbox(Connection connection, String member) throws SQLException {
        String sql = "SELECT p.product_id, p.name, r.requested_at, r.notified_at "
                + "FROM product_restock_requests r JOIN products p ON p.product_id = r.product_id "
                + "WHERE r.member_id = ? ORDER BY r.request_id DESC LIMIT 50";
        List<Entry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, member);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    result.add(new Entry(row.getString(1), row.getString(2),
                            row.getObject(3, LocalDateTime.class), row.getObject(4, LocalDateTime.class)));
                }
            }
        }
        return result;
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final LocalDateTime requested;
        public final LocalDateTime notified;

        private Entry(String id, String name, LocalDateTime requested, LocalDateTime notified) {
            this.id = id;
            this.name = name;
            this.requested = requested;
            this.notified = notified;
        }
    }
}
