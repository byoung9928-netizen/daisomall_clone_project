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

import daiso.cart.db.CartBean;

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
	public List<OrderBean> getOrderList(String keyword, int page, boolean awaitingShipment)
	        throws SQLException {
        if (page < 1) {
			throw new IllegalArgumentException("페이지 번호를 확인해 주세요.");
		}
        return queryOrders(keyword, awaitingShipment, (page - 1L) * 50);
    }

    // ===== 엑셀용: 화면과 조회 조건을 공유하고 페이지 제한만 제외 =====
    public List<OrderBean> getOrdersForExport(String keyword, boolean awaitingShipment) throws SQLException {
        return queryOrders(keyword, awaitingShipment, null);
    }

    private List<OrderBean> queryOrders(String keyword, boolean awaitingShipment, Long offset) throws SQLException {
				
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
			            + "OR LOCATE(?, o.order_no) > 0 "//locate는 문자열 안에 검색어가 있는지 찾는 mysql함수
			            + "OR LOCATE(?, o.member_id) > 0 "//검색어가 들어있으면 위치가 1이상으로 나오기때문에 조건이 참
			            + "OR EXISTS ("
			            + "SELECT 1 FROM order_items search_item "//
			            + "WHERE search_item.order_id = o.order_id "//이 주문의 상품 중 검색어가 들어간 상품이 하나라도 있는가?
			            + "AND LOCATE(?, search_item.product_name) > 0"//
			            + ")) "
						+ "AND (? = 0 OR (p.status = 'APPROVED' " 
			            + "AND o.status IN ('PAID', 'PREPARING') "
						+ "AND o.canceled_at IS NULL)) "
			            + "ORDER BY o.created_at DESC, o.order_id DESC "
			            + (offset == null ? "" : "LIMIT 51 OFFSET ?");
		List<OrderBean> orders = new ArrayList<>();
		
		try(
				Connection con = ds.getConnection();
				PreparedStatement pstmt = con.prepareStatement(sql))
		{
			pstmt.setString(1, query);//빈 검색어인지 확인
			pstmt.setString(2, query);//주문번호 검색
			pstmt.setString(3, query);//회원번호 검색
			pstmt.setString(4, query);//상품명 검색
			pstmt.setBoolean(5, awaitingShipment);
			if (offset != null) {
                pstmt.setLong(6, offset);
            }
			
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
	// 주문번호로 주문 기본정보와 주문상품 조회
	public OrderBean getOrderDetail(String orderNo)
	        throws SQLException {

	    String orderSql =
	        "SELECT o.*, p.status AS payment_status "
	        + "FROM orders o "
	        + "LEFT JOIN payment_attempts p "
	        + "ON p.payment_id = ("
	        + "SELECT MAX(p2.payment_id) "
	        + "FROM payment_attempts p2 "
	        + "WHERE p2.order_id = o.order_id"
	        + ") "
	        + "WHERE o.order_no = ?";

	    try (Connection con = ds.getConnection()) {
	        OrderBean order = new OrderBean();

	        // 주문·배송지·금액·결제 상태 조회
	        try (PreparedStatement pstmt =
	                con.prepareStatement(orderSql)) {

	            pstmt.setString(1, orderNo);

	            try (ResultSet rs = pstmt.executeQuery()) {
	                if (!rs.next()) {
	                    return null;
	                }

	                order.setOrderId(rs.getLong("order_id"));
	                order.setOrderNo(rs.getString("order_no"));
	                order.setMemberId(rs.getString("member_id"));
	                order.setStatus(rs.getString("status"));
	                order.setPaymentStatus(
	                    rs.getString("payment_status")
	                );
	                order.setCreatedAt(
	                    rs.getTimestamp("created_at")
	                );

	                order.setRecipient(rs.getString("recipient"));
	                order.setPhone(rs.getString("phone"));
	                order.setPostcode(rs.getString("postcode"));
	                order.setAddress1(rs.getString("address1"));
	                order.setAddress2(rs.getString("address2"));
	                order.setDeliveryMessage(
	                    rs.getString("delivery_message")
	                );
	                order.setCarrierId(rs.getString("carrier_id"));
	                order.setTrackingNumber(rs.getString("tracking_number"));

	                order.setSubtotalAmount(
	                    rs.getInt("subtotal_amount")
	                );
	                order.setShippingFee(rs.getInt("shipping_fee"));
	                order.setTotalAmount(rs.getInt("total_amount"));
	            }
	        }

	        String itemSql =
	            "SELECT product_id, product_name, product_image, "
	            + "unit_price, quantity "
	            + "FROM order_items "
	            + "WHERE order_id = ? "
	            + "ORDER BY order_item_id";

	        List<CartBean> items = new ArrayList<>();

	        // 현재 상품정보가 아니라 주문 당시 저장한 정보 사용
	        try (PreparedStatement pstmt =
	                con.prepareStatement(itemSql)) {

	            pstmt.setLong(1, order.getOrderId());

	            try (ResultSet rs = pstmt.executeQuery()) {
	                while (rs.next()) {
	                    CartBean item = new CartBean();

	                    item.setProductId(
	                        rs.getString("product_id")
	                    );
	                    item.setProductName(
	                        rs.getString("product_name")
	                    );
	                    item.setImageUrl(
	                        rs.getString("product_image")
	                    );
	                    item.setPrice(rs.getInt("unit_price"));
	                    item.setQuantity(rs.getInt("quantity"));

	                    items.add(item);
	                }
	            }
	        }

	        order.setItems(items);

	        return order;
	    }
  }
	
	public List<OrderBean> getOrderListByMemberId(String memberId)
	        throws SQLException {

	    if (memberId == null || memberId.trim().isEmpty()) {
	        throw new IllegalArgumentException("회원 정보를 확인해 주세요.");
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
	                    + "WHERE o.member_id = ? "
	                    + "ORDER BY o.created_at DESC, o.order_id DESC "
	                    + "LIMIT 10";

	    List<OrderBean> orders = new ArrayList<>();

	    try (Connection connection = ds.getConnection();
	         PreparedStatement statement = connection.prepareStatement(sql)) {

	        statement.setString(1, memberId.trim());

	        try (ResultSet result = statement.executeQuery()) {
	            while (result.next()) {
	                OrderBean order = new OrderBean();
	                order.setOrderId(result.getLong("order_id"));
	                order.setOrderNo(result.getString("order_no"));
	                order.setMemberId(result.getString("member_id"));
	                order.setStatus(result.getString("status"));
	                order.setTotalAmount(result.getInt("total_amount"));
	                order.setCreatedAt(result.getTimestamp("created_at"));
	                order.setPaymentStatus(result.getString("payment_status"));

	                String productName = result.getString("first_product_name");
	                int productCount = result.getInt("product_count");

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
	
	public void updateDeliveryStatus(
	        String orderNo,
	        String carrierId,
	        String trackingNumber,
	        String nextStatus) throws SQLException {

	    if (!"SHIPPED".equals(nextStatus)
	            && !"DELIVERED".equals(nextStatus)) {
	        throw new IllegalArgumentException(
	            "지원하지 않는 배송 상태입니다."
	        );
	    }

	    String sql =
	        "UPDATE orders o "
	        + "JOIN payment_attempts p "
	        + "ON p.payment_id = ("
	        + "SELECT MAX(p2.payment_id) "
	        + "FROM payment_attempts p2 "
	        + "WHERE p2.order_id = o.order_id"
	        + ") "
	        + "SET o.status = ? "
	        + "WHERE o.order_no = ? "
	        + "AND o.carrier_id = ? "
	        + "AND o.tracking_number = ? "
	        + "AND p.status = 'APPROVED' "
	        + "AND o.status IN ('PAID', 'PREPARING', 'SHIPPED') "
	        + "AND (? = 'DELIVERED' "
	        + "OR o.status IN ('PAID', 'PREPARING'))";

	    try (Connection con = ds.getConnection()) {
	        boolean originalAutoCommit = con.getAutoCommit();

	        con.setReadOnly(false);
	        con.setAutoCommit(false);

	        try {
	            try (PreparedStatement pstmt =
	                    con.prepareStatement(sql)) {

	                pstmt.setString(1, nextStatus);
	                pstmt.setString(2, orderNo);
	                pstmt.setString(3, carrierId);
	                pstmt.setString(4, trackingNumber);
	                pstmt.setString(5, nextStatus);

	               int updatedRows= pstmt.executeUpdate();
	               if(updatedRows == 1 && "SHIPPED".equals(nextStatus)) {
	            	   enqueueShippingEmail(con,orderNo);
	               }
	            }

	            con.commit();

	        } catch (SQLException | RuntimeException e) {
	            con.rollback();
	            throw e;

	        } finally {
	            con.setAutoCommit(originalAutoCommit);
	        }
	    }
	}
	
	//배송중으로 변경된 주문의 회원 이메일과 운송장 정보를 발송대기열에 저장
	private void enqueueShippingEmail(Connection con, String orderNo)throws SQLException{
		String sql=
				"INSERT INTO order_email_outbox "
				+"(order_id, event_type, recipient_email, subject, body) "
				+"SELECT o.order_id, 'SHIPPED' , m.email, '주문하신 상품이 발송되었습니다.', "
				+"		CONCAT('주문번호: ', o.order_no, CHAR(10), "
				+"             '택배사: ', o.carrier_id, CHAR(10), "
				+"			   '운송장 번호: ', o.tracking_number) "
				+ "FROM orders o "
		        + "JOIN members m ON m.member_id = o.member_id "
		        + "WHERE o.order_no = ? "
		        + "  AND o.fulfillment_type = 'DELIVERY' "
		        + "  AND o.status = 'SHIPPED' "
		        + "  AND NULLIF(TRIM(m.email), '') IS NOT NULL "
		        + "ON DUPLICATE KEY UPDATE email_id = order_email_outbox.email_id";
		
		try (PreparedStatement pstmt = con.prepareStatement(sql)){
			pstmt.setString(1, orderNo);
			pstmt.executeUpdate();
		}
				
	}
			}
		

