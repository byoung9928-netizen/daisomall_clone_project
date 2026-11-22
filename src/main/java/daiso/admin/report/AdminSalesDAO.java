package daiso.admin.report;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

final class AdminSalesDAO {
    // ===== 판매순위 집계 =====
    // 결제 승인일 기준. 결제대기·취소·환불완료 주문과 배송비는 집계에서 제외한다.
    List<Object[]> list(LocalDate from, LocalDate through) throws SQLException {
        String sql = "SELECT oi.product_id, COALESCE(MAX(pr.name), MAX(oi.product_name)) AS product_name, "
                + "SUM(oi.quantity) AS units, SUM(oi.item_total) AS sales "
                + "FROM order_items oi JOIN orders o ON oi.order_id = o.order_id "
                + "LEFT JOIN products pr ON pr.product_id = oi.product_id "
                + "WHERE o.canceled_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM payment_attempts p WHERE p.order_id = o.order_id "
                + "AND p.status IN ('APPROVED', 'REFUNDING') AND p.approved_at >= ? AND p.approved_at < ?) "
                + "GROUP BY oi.product_id ORDER BY units DESC, sales DESC, oi.product_id";
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = dataSource().getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(through.plusDays(1).atStartOfDay()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[] {rs.getString("product_id"), rs.getString("product_name"),
                            rs.getLong("units"), rs.getLong("sales")});
                }
            }
        }
        return rows;
    }

    private DataSource dataSource() throws SQLException {
        try {
            return InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        } catch (NamingException e) {
            throw new SQLException("판매순위 DB 연결 설정을 확인해 주세요.", e);
        }
    }
}
