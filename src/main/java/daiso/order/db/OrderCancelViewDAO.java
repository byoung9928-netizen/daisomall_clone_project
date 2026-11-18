package daiso.order.db;

import java.sql.*;
import java.util.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import daiso.cart.db.CartBean;

// 취소 화면에 표시할 DB 정보만 조회합니다. 결제 취소나 데이터 변경은 하지 않습니다.
public class OrderCancelViewDAO {
    private final DataSource ds;
    public OrderCancelViewDAO() {
        try { ds = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB"); }
        catch (NamingException e) { throw new IllegalStateException("주문 DB 연결 설정을 확인해 주세요.", e); }
    }
    public Map<String,Object> findSummary(String memberId, int year) throws SQLException {
        String sql="SELECT COUNT(*) AS purchase_count,COALESCE(SUM(total_amount),0) AS purchase_amount "
            + "FROM orders WHERE member_id=? AND status='PAID' AND created_at>=? AND created_at<?";
        try(Connection con=ds.getConnection();PreparedStatement st=con.prepareStatement(sql)) {
            st.setString(1,memberId);st.setTimestamp(2,Timestamp.valueOf(year+"-01-01 00:00:00"));
            st.setTimestamp(3,Timestamp.valueOf((year+1)+"-01-01 00:00:00"));
            try(ResultSet rs=st.executeQuery()) {
                rs.next();Map<String,Object> summary=new HashMap<>();
                summary.put("count",rs.getLong("purchase_count"));summary.put("amount",rs.getLong("purchase_amount"));return summary;
            }
        }
    }
    public Map<String,Object> findOrder(String memberId, String orderNo) throws SQLException {
        String sql = "SELECT o.order_id,o.order_no,o.status,o.subtotal_amount,o.shipping_fee,o.total_amount,"
            + "p.status AS payment_status FROM orders o JOIN payment_attempts p ON p.order_id=o.order_id "
            + "WHERE o.member_id=? AND o.order_no=? ORDER BY p.payment_id DESC LIMIT 1";
        try (Connection con = ds.getConnection()) {
            Map<String,Object> order = new HashMap<>();
            try (PreparedStatement st = con.prepareStatement(sql)) {
                st.setString(1,memberId); st.setString(2,orderNo);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) return null;
                    order.put("orderId",rs.getLong("order_id")); order.put("orderNo",rs.getString("order_no"));
                    order.put("status",rs.getString("status")); order.put("paymentStatus",rs.getString("payment_status"));
                    order.put("subtotal",rs.getInt("subtotal_amount")); order.put("shipping",rs.getInt("shipping_fee"));
                    order.put("total",rs.getInt("total_amount"));
                }
            }
            List<CartBean> items = new ArrayList<>();
            String itemSql = "SELECT product_id,product_name,product_image,unit_price,quantity FROM order_items WHERE order_id=? ORDER BY order_item_id";
            try (PreparedStatement st = con.prepareStatement(itemSql)) {
                st.setLong(1,(Long)order.get("orderId"));
                try (ResultSet rs = st.executeQuery()) {
                    while(rs.next()) {
                        CartBean item=new CartBean(); item.setProductId(rs.getString("product_id"));
                        item.setProductName(rs.getString("product_name")); item.setImageUrl(rs.getString("product_image"));
                        item.setPrice(rs.getInt("unit_price")); item.setQuantity(rs.getInt("quantity")); items.add(item);
                    }
                }
            }
            order.put("items",items); return order;
        }
    }
}
