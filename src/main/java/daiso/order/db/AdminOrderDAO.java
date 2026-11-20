package daiso.order.db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class AdminOrderDAO {
	private final DataSource ds;
	
	public AdminOrderDAO() {
		try {
			ds = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
		}catch (NamingException e) {
			throw new IllegalStateException("주문 DB 연결설정을 확인해 주세요." ,e);
		}
	}
	//전체 회원의 최근 주문 50건 조회
	//검색어가 있으면 주문 번호 또는 회원번호로 검색
	public List<OrderBean> getOrderList(String keyword)
				throws SQLException{
		String query = keyword == null ? "" : keyword.trim();
		
		if(query.length() >120) {
			throw new IllegalArgumentException("검색어는 120자 이내로 입력해 주세요");
		}
		String sql = 
				"SELECT o.order_id, o.order_no, o.member_id, "
			            + "o.status, o.total_amount, o.created_at, "
			            + "p.status AS payment_status, "
			            + "(SELECT oi.product_name FROM order_items oi "
			            + "WHERE oi.order_id = o.order_id "
			            + "ORDER BY oi.order_item_id LIMIT 1) AS first_product_name, "
			            + "(SELECT COUNT(*) FROM order_items oi2 "
			            + "WHERE oi2.order_id = o.order_id) AS product_count "
			            + "FROM orders o "
			            + "LEFT JOIN payment_attempts p "
			            + "ON p.payment_id = ("
			            + "SELECT MAX(p2.payment_id) "
			            + "FROM payment_attempts p2 "
			            + "WHERE p2.order_id = o.order_id"
			            + ") "
			            + "WHERE (? = '' "
			            + "OR LOCATE(?, o.order_no) > 0 "
			            + "OR LOCATE(?, o.member_id) > 0 "
			            + "OR EXISTS ("
			            + "SELECT 1 FROM order_items search_item "
			            + "WHERE search_item.order_id = o.order_id "
			            + "AND LOCATE(?, search_item.product_name) > 0"
			            + ")) "
			            + "ORDER BY o.created_at DESC, o.order_id DESC "
			            + "LIMIT 50";
		List<OrderBean> orders = new ArrayList<>();
		
		try(
				Connection con = ds.getConnection();
				PreparedStatement pstmt = con.prepareStatement(sql))
		{
			pstmt.setString(1, query);
			pstmt.setString(2, query);
			pstmt.setString(3, query);
			pstmt.setString(4, query);
			
			try (ResultSet rs=pstmt.executeQuery()){
				while(rs.next()) {
					OrderBean order =new OrderBean();
					 order.setOrderId(rs.getLong("order_id"));
	                    order.setOrderNo(rs.getString("order_no"));
	                    order.setMemberId(rs.getString("member_id"));
	                    order.setStatus(rs.getString("status"));
	                    order.setTotalAmount(
	                        rs.getInt("total_amount")
	                    );
	                    order.setCreatedAt(
	                        rs.getTimestamp("created_at")
	                    );
	                    order.setPaymentStatus(
	                        rs.getString("payment_status")
	                    );
	                    
	                    String productName = rs.getString("first_product_name");
	                    int productCount = rs.getInt("product_count");

	                    String summary = productName == null
	                        ? "상품정보 없음" : productName;

	                    if (productCount > 1) {
	                        summary += " 외 " + (productCount - 1) + "종";
	                    }

	                    order.setProductSummary(summary);
	                    orders.add(order);
	                }
	            }
	        }

	        return orders;
				}
			}
		

