package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Resolves the current store assignment within the caller's transaction. */
final class StoreManagerAccess {
    private static final String ASSIGNMENT_SQL =
            "SELECT s.store_id, s.store_name, m.login_id "
            + "FROM store_manager_assignments a "
            + "JOIN members m ON m.member_id = a.member_id "
            + "JOIN store_locations s ON s.store_id = a.store_id "
            + "WHERE a.member_id = ? "
            + "AND m.role = 'STORE_MANAGER' AND m.status = 'ACTIVE'";

    final String storeId;
    final String storeName;
    final String loginId;

    private StoreManagerAccess(String storeId, String storeName, String loginId) {
        this.storeId = storeId;
        this.storeName = storeName;
        this.loginId = loginId;
    }

    static String memberId(HttpServletRequest request) {
        return memberId(request.getSession(false));
    }

    static String memberId(HttpSession session) {
        Object memberId = session == null ? null : session.getAttribute("member_id");
        return memberId instanceof String && !((String) memberId).isBlank() ? (String) memberId : null;
    }

    static StoreManagerAccess find(Connection connection, String memberId, boolean lock) throws SQLException {
        if (memberId == null || memberId.isBlank()) {
            return null;
        }
        String sql = lock ? ASSIGNMENT_SQL + " FOR UPDATE" : ASSIGNMENT_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new StoreManagerAccess(row.getString("store_id"), row.getString("store_name"), row.getString("login_id"))
                        : null;
            }
        }
    }
}
