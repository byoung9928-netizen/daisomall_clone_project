package daiso.product.service;

import daiso.product.dao.ProductConnectionFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Analytics writes are isolated from the original customer action. */
public final class ProductBehaviorRecorder {
    public enum Type { VIEW, WISHLIST_ADD, WISHLIST_REMOVE, CART_ADD, CART_REMOVE }

    private static final String INSERT = "INSERT INTO product_behavior_events "
            + "(member_id, product_id, event_type, occurred_at) ";
    private static final String LOCK_NAME = "SHA2(JSON_ARRAY(DATABASE(), 'product-behavior-view', ?, ?), 256)";

    private ProductBehaviorRecorder() {}

    public static void record(HttpServletRequest request, Type type, String productId) {
        record(request, type, List.of(productId));
    }

    public static void record(HttpServletRequest request, Type type, Collection<String> productIds) {
        if (productIds.isEmpty()) return;
        try {
            HttpSession session = request.getSession(false);
            if (session == null || !"MEMBER".equalsIgnoreCase(String.valueOf(session.getAttribute("role")))) return;
            Object id = session.getAttribute("member_id");
            if (!(id instanceof String) || ((String) id).isBlank()) return;
            try (Connection conn = ProductConnectionFactory.openWriteConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (type == Type.VIEW) {
                        recordView(conn, (String) id, productIds.iterator().next());
                    } else {
                        recordChanges(conn, (String) id, type, productIds);
                        conn.commit();
                    }
                } catch (SQLException | RuntimeException error) {
                    try { conn.rollback(); }
                    catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                    throw error;
                }
            }
        } catch (SQLException | NamingException | RuntimeException error) {
            if (!(error instanceof SQLException) || ((SQLException) error).getErrorCode() != 1146) {
                request.getServletContext().log("상품 행동 기록 저장 실패 (" + type + ")", error);
            }
        }
    }

    private static void recordChanges(Connection conn, String memberId, Type type, Collection<String> productIds)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(INSERT + "VALUES (?, ?, ?, UTC_TIMESTAMP(6))")) {
            statement.setQueryTimeout(2);
            for (String productId : productIds) {
                statement.setString(1, memberId);
                statement.setString(2, productId);
                statement.setString(3, type.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void recordView(Connection conn, String memberId, String productId) throws SQLException {
        // Serialize the cutoff check for this pair, including requests from different sessions.
        if (!viewLock(conn, "GET_LOCK(" + LOCK_NAME + ", 0)", memberId, productId)) return;
        try {
            String sql = INSERT + "SELECT ?, ?, 'VIEW', UTC_TIMESTAMP(6) WHERE NOT EXISTS ("
                    + "SELECT 1 FROM product_behavior_events WHERE member_id = ? AND product_id = ? "
                    + "AND event_type = 'VIEW' AND occurred_at > UTC_TIMESTAMP(6) - INTERVAL 30 MINUTE)";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setQueryTimeout(2);
                statement.setString(1, memberId);
                statement.setString(2, productId);
                statement.setString(3, memberId);
                statement.setString(4, productId);
                statement.executeUpdate();
            }
            conn.commit();
        } finally {
            try {
                viewLock(conn, "RELEASE_LOCK(" + LOCK_NAME + ")", memberId, productId);
            } catch (SQLException error) {
                // A pooled connection must not retain this session-level lock.
                try { conn.abort(Runnable::run); }
                catch (SQLException abortError) { error.addSuppressed(abortError); }
                throw error;
            }
        }
    }

    private static boolean viewLock(Connection conn, String expression, String memberId, String productId)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("SELECT " + expression)) {
            statement.setQueryTimeout(2);
            statement.setString(1, memberId);
            statement.setString(2, productId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }
}
