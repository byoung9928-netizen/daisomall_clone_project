package daiso.order.db;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.sql.Statement;
import java.security.SecureRandom;
import java.util.Base64;
import daiso.cart.db.CartBean;
import daiso.order.payment.kakaoPayClient;

public class OrderDAO {
		//db연결을 빌려주는 객체
		private final DataSource ds;
		
		public OrderDAO() {
			try {
				//기존 context.xml에 설정한 db연결 사용
				ds = InitialContext.doLookup(
						"java:comp/env/jdbc/mysqlDB"
				);
			}catch(NamingException e) {
				//연결 설정을 찾지못하면 DAO생성을 중단
				throw new IllegalStateException(
					"주문 DB연결 설정을 찾을 수 없습니다.", e
					);
					
			}
		}
		// ===== 택배·픽업 주문을 같은 트랜잭션으로 저장 =====
		public long createOrder(OrderBean order) throws SQLException {
		    return insertOrder(order, null, 0);
		}

		public long createPickupOrder(OrderBean order, String storeId) throws SQLException {
		    if (storeId == null || storeId.isBlank() || storeId.length() > 40) {
		        throw new IllegalArgumentException("픽업 매장을 확인해 주세요.");
		    }
		    if (order.getShippingFee() != 0) {
		        throw new IllegalArgumentException("픽업 주문의 배송비는 0원이어야 합니다.");
		    }
		    return insertOrder(order, storeId, 200);
		}

		private long insertOrder(OrderBean order, String storeId, int pickupFee)
		        throws SQLException {

		    // 주문할 상품이 있는지 확인
		    if (order.getItems() == null || order.getItems().isEmpty()) {
		        throw new IllegalArgumentException(
		            "주문할 상품이 없습니다."
		        );
		    }

		    // 상품별 합계와 주문 총액이 일치하는지 확인
		    long calculatedSubtotal = 0;

		    for (CartBean item : order.getItems()) {
		        if (item.getQuantity() < 1 || item.getQuantity() > 99
		                || item.getPrice() < 0) {
		            throw new IllegalArgumentException(
		                "상품 가격과 수량을 확인해 주세요."
		            );
		        }
		        if (storeId != null && !storeId.equals(item.getStoreId())) {
		            throw new IllegalArgumentException("선택한 픽업 매장과 상품의 매장이 다릅니다.");
		        }
		        calculatedSubtotal += item.getItemTotal();
		    }

		 // 포인트는 상품 금액까지만 사용하고, 배송비와 픽업 수수료는 별도로 더한다.
		    int usedPoints = order.getUsedPoints();

		    if (usedPoints < 0 || usedPoints > calculatedSubtotal) {
		        throw new IllegalArgumentException("사용 포인트가 상품 금액 범위를 벗어났습니다.");
		    }

		    long calculatedTotal =
		            calculatedSubtotal - usedPoints + order.getShippingFee() + pickupFee;

		    if (calculatedSubtotal != order.getSubtotalAmount()
		            || order.getShippingFee() < 0
		            || calculatedTotal != order.getTotalAmount()) {
		        throw new IllegalArgumentException("주문 금액이 일치하지 않습니다.");
		    }

		    String orderSql =
		            "INSERT INTO orders "
		            + "(order_no, member_id, checkout_token, status, "
		            + "recipient, phone, postcode, address1, address2, "
		            + "delivery_message, subtotal_amount, shipping_fee, total_amount, "
		            + "fulfillment_type, pickup_store_id, pickup_fee, used_points) "
		            + "VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		    String itemSql =
		        "INSERT INTO order_items "
		        + "(order_id, product_id, product_name, product_image, "
		        + "unit_price, quantity, item_total) "
		        + "VALUES (?, ?, ?, ?, ?, ?, ?)";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            long orderId;
		            if( usedPoints>0) {
		            	checkAvailablePoints(con,order.getMemberId(),usedPoints);
		            }

		            // 주문 기본정보 저장
		            try (PreparedStatement pstmt = con.prepareStatement(
		                    orderSql, Statement.RETURN_GENERATED_KEYS)) {

		                pstmt.setString(1, order.getOrderNo());
		                pstmt.setString(2, order.getMemberId());
		                pstmt.setString(3, order.getCheckoutToken());
		                pstmt.setString(4, order.getRecipient());
		                pstmt.setString(5, order.getPhone());
		                pstmt.setString(6, order.getPostcode());
		                pstmt.setString(7, order.getAddress1());
		                pstmt.setString(8, order.getAddress2());
		                pstmt.setString(9, order.getDeliveryMessage());
		                pstmt.setInt(10, order.getSubtotalAmount());
		                pstmt.setInt(11, order.getShippingFee());
		                pstmt.setInt(12, order.getTotalAmount());
		                pstmt.setString(13, storeId == null ? "DELIVERY" : "PICKUP");
		                pstmt.setString(14, storeId);
		                pstmt.setInt(15, pickupFee);
		                pstmt.setInt(16, usedPoints);

		                pstmt.executeUpdate();

		                // MySQL이 자동 생성한 order_id 가져오기
		                try (ResultSet rs = pstmt.getGeneratedKeys()) {
		                    if (!rs.next()) {
		                        throw new SQLException(
		                            "생성된 주문번호를 가져오지 못했습니다."
		                        );
		                    }

		                    orderId = rs.getLong(1);
		                }
		            }
		            // 포인트를 사용한 주문은 사용액을 음수로 기록한다.
		            if (usedPoints > 0) {
		                String useSql =
		                        "INSERT INTO daiso_point_history "
		                        + "(member_id, order_id, point_type, point_amount) "
		                        + "VALUES (?, ?, 'USE', ?)";

		                try (PreparedStatement pstmt = con.prepareStatement(useSql)) {
		                    pstmt.setString(1, order.getMemberId());
		                    pstmt.setLong(2, orderId);
		                    pstmt.setInt(3, -usedPoints);
		                    pstmt.executeUpdate();
		                }
		            }
		            // 주문에 포함된 상품들을 저장
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(itemSql)) {

		                for (CartBean item : order.getItems()) {
		                    pstmt.setLong(1, orderId);
		                    pstmt.setString(2, item.getProductId());
		                    pstmt.setString(3, item.getProductName());
		                    pstmt.setString(4, item.getImageUrl());
		                    pstmt.setInt(5, item.getPrice());
		                    pstmt.setInt(6, item.getQuantity());
		                    pstmt.setInt(
		                        7, Math.toIntExact(item.getItemTotal())
		                    );

		                    pstmt.executeUpdate();
		                }
		            }

		            // 주문과 상품 저장이 모두 성공하면 확정
		            con.commit();

		            return orderId;

		        } catch (SQLException | RuntimeException e) {
		            // 하나라도 실패하면 주문·상품 저장 모두 취소
		            con.rollback();
		            throw e;

		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		// ===== 결제 대기 기록 저장 =====
		public void savePaymentReady(long orderId, String tid, int amount)
		        throws SQLException {
		    savePaymentReady(orderId, tid, amount, "KAKAOPAY");
		}

		public void savePaymentReady(long orderId, String tid, int amount, String method)
		        throws SQLException {

		    if (!"KAKAOPAY".equals(method) && !"TOSSPAY".equals(method)) {
		        throw new IllegalArgumentException("지원하지 않는 결제수단입니다.");
		    }

		    if (tid == null || tid.isBlank()) {
		        throw new IllegalArgumentException("결제 식별자가 없습니다.");
		    }

		    String sql =
		        "INSERT INTO payment_attempts "
		        + "(order_id, method, status, amount, tid) "
		        + "SELECT order_id, ?, 'PENDING', "
		        + "total_amount, ? "
		        + "FROM orders "
		        + "WHERE order_id = ? "
		        + "AND total_amount = ? "
		        + "AND status = 'PENDING'";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(sql)) {

		                pstmt.setString(1, method);
		                pstmt.setString(2, tid);
		                pstmt.setLong(3, orderId);
		                pstmt.setInt(4, amount);

		                int result = pstmt.executeUpdate();

		                if (result != 1) {
		                    throw new SQLException(
		                        "결제 대기 주문과 금액을 확인할 수 없습니다."
		                    );
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
		
		// 같은 회원의 주문 두 건이 같은 포인트를 동시에 사용하지 않도록 잔액 확인 전에 잠근다.
		private void checkAvailablePoints(Connection con, String memberId, int usedPoints)
		        throws SQLException {
		    String lockSql = "SELECT member_id FROM members WHERE member_id = ? FOR UPDATE";

		    try (PreparedStatement pstmt = con.prepareStatement(lockSql)) {
		        pstmt.setString(1, memberId);

		        try (ResultSet rs = pstmt.executeQuery()) {
		            if (!rs.next()) {
		                throw new IllegalArgumentException("포인트를 사용할 회원을 찾을 수 없습니다.");
		            }
		        }
		    }

		    String balanceSql =
		            "SELECT COALESCE(SUM(point_amount), 0) "
		            + "FROM daiso_point_history WHERE member_id = ?";

		    try (PreparedStatement pstmt = con.prepareStatement(balanceSql)) {
		        pstmt.setString(1, memberId);

		        try (ResultSet rs = pstmt.executeQuery()) {
		            rs.next();
		            if (rs.getLong(1) < usedPoints) {
		                throw new IllegalArgumentException("보유 포인트가 부족합니다.");
		            }
		        }
		    }
		}
		
		// 다이소 포인트 구매 적립: 배송비와 픽업 수수료를 제외한 상품 금액 1,000원당 1P
		private void earnDaisoPoints(Connection con, long orderId) throws SQLException {
		    String sql =
		            "INSERT INTO daiso_point_history "
		            + "(member_id, order_id, point_type, point_amount) "
		            + "SELECT member_id, order_id, 'EARN', subtotal_amount DIV 1000 "
		            + "FROM orders "
		            + "WHERE order_id = ? AND status = 'PAID' AND subtotal_amount >= 1000";

		    try (PreparedStatement pstmt = con.prepareStatement(sql)) {
		        pstmt.setLong(1, orderId);
		        pstmt.executeUpdate();
		    }
		}
		
		// 주문 취소: 이 주문에서 실제 사용했던 포인트를 회원에게 돌려준다.
		private void restoreUsedPoints(Connection con, long orderId) throws SQLException {
		    String sql =
		            "INSERT INTO daiso_point_history "
		            + "(member_id, order_id, point_type, point_amount) "
		            + "SELECT member_id, order_id, 'RESTORE', -point_amount "
		            + "FROM daiso_point_history "
		            + "WHERE order_id = ? AND point_type = 'USE'";

		    try (PreparedStatement pstmt = con.prepareStatement(sql)) {
		        pstmt.setLong(1, orderId);
		        pstmt.executeUpdate();
		    }
		}
		
		private void reverseDaisoPoints(Connection con, long orderId) throws SQLException{
			String sql = 
					"INSERT INTO daiso_point_history "
					+"(member_id, order_id, point_type,point_amount) "
					+"SELECT member_id, order_id, 'REVERSE', -point_amount "
					+"FROM daiso_point_history "
					+"WHERE order_id = ? AND point_type= 'EARN'";
			try (PreparedStatement pstmt = con.prepareStatement(sql)){
				pstmt.setLong(1, orderId);
				pstmt.executeUpdate();
			}
		}
		// 다이소 포인트 잔액 조회: 로그인한 회원의 적립과 회수 내역을 합산한다.
		public long getDaisoPointBalance(String memberId) throws SQLException {
		    String sql =
		            "SELECT COALESCE(SUM(point_amount), 0) "
		            + "FROM daiso_point_history "
		            + "WHERE member_id = ?";

		    try (Connection con = ds.getConnection();
		         PreparedStatement pstmt = con.prepareStatement(sql)) {
		        pstmt.setString(1, memberId);

		        try (ResultSet rs = pstmt.executeQuery()) {
		            rs.next();
		            return rs.getLong(1);
		        }
		    }
		}
		//마이페이지:로그인한 회원의 최근 변동 50건을 최신순으로 조회
		public List<Map<String,Object>>getMyPointHistory(String memberId) throws SQLException{
			String sql =
					"SELECT h.created_at, o.order_no, h.point_type, h.point_amount "
					+"FROM daiso_point_history h "
					+"JOIN orders o ON o.order_id = h.order_id "
					+"WHERE h.member_id = ? "
					+"ORDER BY h.created_at DESC, h.point_id DESC "
					+"LIMIT 50";
			List<Map<String,Object>> history=new ArrayList<>();
			
			try(Connection con=ds.getConnection();
					PreparedStatement pstmt= con.prepareStatement(sql)){
				pstmt.setString(1, memberId);
				
				try(ResultSet rs = pstmt.executeQuery()){
					while(rs.next()) {
						Map<String,Object>entry = new HashMap<>();
						entry.put("createdAt", rs.getTimestamp("created_at"));
		                entry.put("orderNo", rs.getString("order_no"));
		                entry.put("pointType", rs.getString("point_type"));
		                entry.put("pointAmount", rs.getInt("point_amount"));
		                history.add(entry);
					}
				}
			}
			return history;		
		}
		
		public void approvePayment(
		
				String memberId,
		        long orderId,
		        String tid,
		        int amount,
		        Timestamp approvedAt,
		        String trackingNumber) throws SQLException {
			if(trackingNumber == null || trackingNumber.isBlank()||trackingNumber.length()>200) {
				throw new IllegalArgumentException(
						"자동 배정할 운송장 번호가 없습니다.");
			}
		    if (approvedAt == null) {
		        throw new IllegalArgumentException(
		            "결제 승인 시각이 없습니다."
		        );
		    }

		    String checkSql =
		        "SELECT o.status AS order_status, o.total_amount, "
		        + "p.status AS payment_status, p.amount "
		        + "FROM orders o "
		        + "JOIN payment_attempts p ON p.order_id = o.order_id "
		        + "WHERE o.order_id = ? "
		        + "AND o.member_id = ? "
		        + "AND p.tid = ? FOR UPDATE";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            String orderStatus;
		            String paymentStatus;

		            // 처리 중에는 같은 결제 건을 다른 요청이 변경하지 못하게 잠금
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(checkSql)) {

		                pstmt.setLong(1, orderId);
		                pstmt.setString(2, memberId);
		                pstmt.setString(3, tid);

		                try (ResultSet rs = pstmt.executeQuery()) {
		                    if (!rs.next()) {
		                        throw new SQLException(
		                            "주문과 결제정보를 찾을 수 없습니다."
		                        );
		                    }

		                    if (rs.getInt("total_amount") != amount
		                            || rs.getInt("amount") != amount) {
		                        throw new SQLException(
		                            "승인 금액과 주문 금액이 다릅니다."
		                        );
		                    }

		                    orderStatus = rs.getString("order_status");
		                    paymentStatus = rs.getString("payment_status");
		                }
		            }

		            // 이미 반영된 결제라면 중복 변경하지 않음
		            if ("PAID".equals(orderStatus)
		                    && "APPROVED".equals(paymentStatus)) {
		                con.commit();
		                return;
		            }

		            if (!"APPROVING".equals(orderStatus)
		                    || !"APPROVING".equals(paymentStatus)) {
		                throw new SQLException(
		                    "승인 처리할 수 없는 주문 상태입니다."
		                );
		            }
		           // 주문에 저장된 상품별 구매 수량을 조회한다.
		            String orderedItemsSql =
		                "SELECT product_id, SUM(quantity) AS ordered_quantity "
		                + "FROM order_items WHERE order_id = ? "
		                + "GROUP BY product_id ORDER BY product_id";

		            String deductStockSql =
		                "UPDATE products "
		                + "SET stock_quantity = stock_quantity - ? "
		                + "WHERE product_id = ? AND stock_quantity >= ?";

		            try (PreparedStatement itemsStmt = con.prepareStatement(orderedItemsSql);
		                 PreparedStatement stockStmt = con.prepareStatement(deductStockSql)) {

		                itemsStmt.setLong(1, orderId);

		                try (ResultSet rs = itemsStmt.executeQuery()) {
		                    if (!rs.next()) {
		                        throw new SQLException("주문 상품을 찾을 수 없습니다.");
		                    }

		                    do {
		                        String productId = rs.getString("product_id");
		                        long quantity = rs.getLong("ordered_quantity");

		                        if (quantity <= 0) {
		                            throw new SQLException("주문 상품의 수량을 확인해 주세요.");
		                        }

		                        stockStmt.setLong(1, quantity);
		                        stockStmt.setString(2, productId);
		                        stockStmt.setLong(3, quantity);

		                        if (stockStmt.executeUpdate() != 1) {
		                            throw new SQLException("상품 재고가 부족합니다. 상품번호: " + productId);
		                        }
		                    } while (rs.next());
		                }
		            }

		            String paymentSql =
		                "UPDATE payment_attempts "
		                + "SET status = 'APPROVED', approved_at = ? "
		                + "WHERE order_id = ? AND tid = ? "
		                + "AND status = 'APPROVING'";

		            try (PreparedStatement pstmt =
		                    con.prepareStatement(paymentSql)) {

		                pstmt.setTimestamp(1, approvedAt);
		                pstmt.setLong(2, orderId);
		                pstmt.setString(3, tid);

		                if (pstmt.executeUpdate() != 1) {
		                    throw new SQLException(
		                        "결제 승인정보를 변경하지 못했습니다."
		                    );
		                }
		            }

		            String orderSql =
		            		 "UPDATE orders "
		            		    + "SET status = 'PAID', hidden_at = NULL, stock_deducted = TRUE, "
		            			+ "carrier_id = 'dev.track.dummy', "
		            			+ "tracking_number = ? "
		            			+ "WHERE order_id = ? AND member_id = ? "
		            			+ "AND status = 'APPROVING'";
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(orderSql)) {

		                pstmt.setString(1, trackingNumber);
		                pstmt.setLong(2, orderId);
		                pstmt.setString(3, memberId);

		                if (pstmt.executeUpdate() != 1) {
		                    throw new SQLException(
		                        "주문 상태를 변경하지 못했습니다."
		                    );
		                }
		            }
		            earnDaisoPoints(con,orderId);
		            String deleteCartSql =
		            	    "DELETE c FROM cart_items c "
		            	    + "JOIN order_items oi "
		            	    + "ON oi.product_id = c.product_id "
		            	    + "JOIN orders o ON o.order_id = oi.order_id "
		            	    + "WHERE o.order_id = ? "
		            	    + "AND o.member_id = ? "
		            	    + "AND c.member_id = ? "
		            	    + "AND o.status = 'PAID' "
		            	    + "AND c.quantity = oi.quantity";
		            
		            try(PreparedStatement pstmt = con.prepareStatement(deleteCartSql)){
		            	pstmt.setLong(1, orderId);
		            	pstmt.setString(2, memberId);
		            	pstmt.setString(3,  memberId);
		            	
		            	pstmt.executeUpdate();
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
		
		public String findLatestChatStatus(String memberId, boolean returnQuestion)
		        throws SQLException {
		    String sql;

		    if (returnQuestion) {
		        sql = "SELECT r.status FROM order_returns r "
		                + "JOIN orders o ON o.order_id = r.order_id "
		                + "WHERE o.member_id = ? AND o.hidden_at IS NULL "
		                + "ORDER BY r.requested_at DESC, r.return_id DESC LIMIT 1";
		    } else {
		        sql = "SELECT o.status FROM orders o "
		                + "WHERE o.member_id = ? AND o.hidden_at IS NULL "
		                + "ORDER BY o.created_at DESC, o.order_id DESC LIMIT 1";
		    }

		    try (Connection con = ds.getConnection();
		         PreparedStatement statement = con.prepareStatement(sql)) {
		        statement.setString(1, memberId);

		        try (ResultSet result = statement.executeQuery()) {
		            return result.next() ? result.getString("status") : null;
		        }
		    }
		}
		
		public List<Map<String, Object>> getMyOrders(String memberId)
		        throws SQLException {

		    return getMyOrders(memberId, null);
        }

        public List<Map<String, Object>> getMyOrders(String memberId, Boolean claims)
                throws SQLException {
            // 취소 주문과 반품 신청 주문은 주문 목록을 자르기 전에 구분한다.
            String claimCondition = "(o.status IN ('CANCELLED', 'CANCELLING') OR EXISTS "
                    + "(SELECT 1 FROM order_returns r WHERE r.order_id = o.order_id))";
            String filter = claims == null ? "" : (claims ? "AND " : "AND NOT ") + claimCondition + " ";
            String claimDate = "COALESCE(o.canceled_at, "
                    + "(SELECT MAX(r.requested_at) FROM order_returns r WHERE r.order_id = o.order_id))";
            boolean claimHistory = Boolean.TRUE.equals(claims);
            String dateColumn = claimHistory ? claimDate : "NULL";
            String orderBy = claimHistory ? "claim_at DESC, o.order_id DESC" : "o.order_id DESC";
            String sql =
		        "SELECT o.order_id, o.order_no, o.status, "
		        + "o.total_amount, o.created_at, "
		        + "p.approved_at, " + dateColumn + " AS claim_at "
		        + "FROM orders o "
		        + "LEFT JOIN payment_attempts p "
		        + "ON p.payment_id = ("
		        + "SELECT MAX(p2.payment_id) "
		        + "FROM payment_attempts p2 "
		        + "WHERE p2.order_id = o.order_id"
		        + ") "
		        + "WHERE o.member_id = ? "
		        + "AND o.hidden_at IS NULL "
                + filter
		        + "ORDER BY " + orderBy + " "
		        + "LIMIT 50";

		    List<Map<String, Object>> orders = new ArrayList<>();

		    try (
		        Connection con = ds.getConnection();
		        PreparedStatement pstmt = con.prepareStatement(sql)
		    ) {
		        pstmt.setString(1, memberId);

		        try (ResultSet rs = pstmt.executeQuery()) {
		            while (rs.next()) {
		                Map<String, Object> order = new HashMap<>();

		                order.put("orderId", rs.getLong("order_id"));
		                order.put("orderNo", rs.getString("order_no"));
		                order.put("status", rs.getString("status"));
		                order.put("total", rs.getInt("total_amount"));
		                order.put("createdAt", rs.getTimestamp("created_at"));
		                order.put("approvedAt", rs.getTimestamp("approved_at"));
                        order.put("claimAt", rs.getTimestamp("claim_at"));

		                orders.add(order);
		            }
		        }
		    }

		    return orders;
		}
		
		public Map<String, Object> getMyOrderDetail(
		        String memberId, String orderNo) throws SQLException {

				String sql =
				    "SELECT o.*, s.store_name, s.city, p.method AS pay_method, p.status AS payment_status, "
				    + "p.approved_at, COALESCE(o.canceled_at, r.requested_at) AS claim_at, r.status AS return_status, "
				    + "r.admin_note AS return_admin_note "
				    + "FROM orders o "
				    + "LEFT JOIN store_locations s ON s.store_id = o.pickup_store_id "
                    + "LEFT JOIN payment_attempts p ON p.payment_id = ("
				    + "SELECT MAX(p2.payment_id) "
				    + "FROM payment_attempts p2 "
				    + "WHERE p2.order_id = o.order_id"
				    + ") "
				    + "LEFT JOIN order_returns r ON r.order_id = o.order_id "
				    + "WHERE o.member_id = ? AND o.order_no = ? "
				    + "AND o.hidden_at IS NULL";
		       

		    try (Connection con = ds.getConnection()) {
		        Map<String, Object> order = new HashMap<>();
		        long orderId;

		        // 회원번호까지 함께 확인해 본인의 주문만 조회
		        try (PreparedStatement pstmt =
		                con.prepareStatement(sql)) {

		            pstmt.setString(1, memberId);
		            pstmt.setString(2, orderNo);

		            try (ResultSet rs = pstmt.executeQuery()) {
		                if (!rs.next()) {
		                    return null;
		                }

		                orderId = rs.getLong("order_id");

		                order.put("orderId", orderId);
		                order.put("orderNo", rs.getString("order_no"));
		                order.put("status", rs.getString("status"));
		                order.put("fulfillmentType", rs.getString("fulfillment_type"));
		                order.put("pickupStatus", rs.getString("pickup_status"));
                        order.put("pickupStore", rs.getString("store_name") != null ? rs.getString("store_name") : rs.getString("pickup_store_id"));
                        order.put("pickupCity", rs.getString("city"));
                        order.put("pickupFee", rs.getInt("pickup_fee"));
                        order.put("payMethod", rs.getString("pay_method"));
                        order.put("carrierId", rs.getString("carrier_id"));
		                order.put("trackingNumber", rs.getString("tracking_number"));
		                order.put("paymentStatus", rs.getString("payment_status"));
		                order.put("returnStatus", rs.getString("return_status"));
		                order.put("returnAdminNote", rs.getString("return_admin_note"));
		                order.put("subtotal", rs.getInt("subtotal_amount"));
		                order.put("shipping", rs.getInt("shipping_fee"));
		                order.put("total", rs.getInt("total_amount"));
		                order.put("message", rs.getString("delivery_message"));

		                // 배송정보
		                Map<String, String> address = new HashMap<>();

		                address.put(
		                    "addressName", rs.getString("recipient")
		                );
		                address.put("recipient", rs.getString("recipient"));
		                address.put("phone", rs.getString("phone"));
		                address.put("postcode", rs.getString("postcode"));
		                address.put("address1", rs.getString("address1"));
		                address.put("address2", rs.getString("address2"));

		                order.put("address", address);

		                // 화면에 표시할 주문 날짜와 결제 시각
		                Timestamp claimAt = rs.getTimestamp("claim_at");
                        if (claimAt != null) {
                            order.put("claimDate", claimAt.toLocalDateTime().toLocalDate().toString());
                        }

                        Timestamp createdAt =
		                    rs.getTimestamp("created_at");

		                Timestamp approvedAt =
		                    rs.getTimestamp("approved_at");

		                order.put(
		                    "date",
		                    createdAt.toLocalDateTime()
		                        .toLocalDate().toString()
		                );

		                order.put(
		                    "paidAt",
		                    approvedAt == null
		                        ? "-"
		                        : approvedAt.toLocalDateTime().format(
		                            java.time.format.DateTimeFormatter
		                                .ofPattern("yyyy-MM-dd HH:mm")
		                        )
		                );
		            }
		        }

		        // 주문 당시 저장한 상품정보 조회
		        String itemSql =
		            "SELECT product_id, product_name, product_image, "
		            + "unit_price, quantity "
		            + "FROM order_items "
		            + "WHERE order_id = ? "
		            + "ORDER BY order_item_id";

		        List<CartBean> items = new ArrayList<>();

		        try (PreparedStatement pstmt =
		                con.prepareStatement(itemSql)) {

		            pstmt.setLong(1, orderId);

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

		        order.put("items", items);

		        return order;
		    }
		}
		
		// 삭제 요청 전에 본인의 결제대기 주문인지 확인
		public Map<String, Object> getPendingOrder(
		        String memberId, String orderNo) throws SQLException {

		    String sql =
		        "SELECT o.order_id, o.order_no, o.total_amount, "
		        + "p.payment_id, p.tid, p.status AS payment_status "
		        + "FROM orders o "
		        + "LEFT JOIN payment_attempts p "
		        + "ON p.payment_id = ("
		        + "SELECT MAX(p2.payment_id) "
		        + "FROM payment_attempts p2 "
		        + "WHERE p2.order_id = o.order_id"
		        + ") "
		        + "WHERE o.member_id = ? "
		        + "AND o.order_no = ? "
		        + "AND o.status = 'PENDING' "
		        + "AND o.hidden_at IS NULL";

		    try (
		        Connection con = ds.getConnection();
		        PreparedStatement pstmt = con.prepareStatement(sql)
		    ) {
		        pstmt.setString(1, memberId);
		        pstmt.setString(2, orderNo);

		        try (ResultSet rs = pstmt.executeQuery()) {
		            if (!rs.next()) {
		                return null;
		            }

		            Map<String, Object> order = new HashMap<>();

		            order.put("orderId", rs.getLong("order_id"));
		            order.put("orderNo", rs.getString("order_no"));
		            order.put("total", rs.getInt("total_amount"));

		            // 결제 준비 기록이 없으면 null
		            order.put("paymentId", rs.getObject("payment_id"));
		            order.put("tid", rs.getString("tid"));
		            order.put(
		                "paymentStatus",
		                rs.getString("payment_status")
		            );

		            return order;
		        }
		    }
		}
		
		// 포인트를 사용하고 30분 이상 결제 대기 중인 주문을 100건씩 조회한다.
		public List<Long> findExpiredPointOrderIds(long afterOrderId) throws SQLException {
		    String sql =
		            "SELECT o.order_id FROM orders o "
		            + "WHERE o.order_id > ? AND o.status = 'PENDING' "
		            + "AND o.used_points > 0 "
		            + "AND o.created_at <= CURRENT_TIMESTAMP - INTERVAL 30 MINUTE "
		            + "AND NOT EXISTS ("
		            + "    SELECT 1 FROM payment_attempts p "
		            + "    WHERE p.order_id = o.order_id AND p.status <> 'PENDING'"
		            + ") "
		            + "ORDER BY o.order_id LIMIT 100";

		    List<Long> orderIds = new ArrayList<>();

		    try (Connection con = ds.getConnection();
		         PreparedStatement pstmt = con.prepareStatement(sql)) {
		        pstmt.setLong(1, afterOrderId);

		        try (ResultSet rs = pstmt.executeQuery()) {
		            while (rs.next()) {
		                orderIds.add(rs.getLong("order_id"));
		            }
		        }
		    }
		    return orderIds;
		}
		
		// 30분 이상 결제 대기 중인 포인트 사용 주문을 취소하고 사용 포인트를 반환한다.
		public boolean expirePendingPointOrder(long orderId) throws SQLException {
		    String expireOrderSql =
		            "UPDATE orders o "
		            + "SET o.status = 'CANCELLED', o.canceled_at = CURRENT_TIMESTAMP, "
		            + "o.hidden_at = CURRENT_TIMESTAMP "
		            + "WHERE o.order_id = ? AND o.status = 'PENDING' "
		            + "AND o.used_points > 0 "
		            + "AND o.created_at <= CURRENT_TIMESTAMP - INTERVAL 30 MINUTE "
		            + "AND NOT EXISTS ("
		            + "    SELECT 1 FROM payment_attempts p "
		            + "    WHERE p.order_id = o.order_id AND p.status <> 'PENDING'"
		            + ")";

		    String cancelAttemptsSql =
		            "UPDATE payment_attempts SET status = 'CANCELLED' "
		            + "WHERE order_id = ? AND status = 'PENDING'";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();
		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            int updated;
		            try (PreparedStatement pstmt = con.prepareStatement(expireOrderSql)) {
		                pstmt.setLong(1, orderId);
		                updated = pstmt.executeUpdate();
		            }

		            if (updated == 0) {
		                con.commit();
		                return false;
		            }

		            try (PreparedStatement pstmt = con.prepareStatement(cancelAttemptsSql)) {
		                pstmt.setLong(1, orderId);
		                pstmt.executeUpdate();
		            }

		            restoreUsedPoints(con, orderId);
		            con.commit();
		            return true;
		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;
		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		// 결제대기 주문을 취소하고 주문내역에서 제외
		public boolean cancelPendingOrder(
		        String memberId,
		        String orderNo,
		        String tid) throws SQLException {

		    String sql =
		        "UPDATE orders o "
		        + "JOIN payment_attempts p ON p.order_id = o.order_id "
		        + "SET o.status = 'CANCELLED', "
		        + "o.canceled_at = CURRENT_TIMESTAMP, "
		        + "o.hidden_at = CURRENT_TIMESTAMP, "
		        + "p.status = 'CANCELLED' "
		        + "WHERE o.member_id = ? "
		        + "AND o.order_no = ? "
		        + "AND p.tid = ? "
		        + "AND o.status = 'PENDING' "
		        + "AND p.status = 'PENDING'";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            int updated;

		            try (PreparedStatement pstmt =
		                    con.prepareStatement(sql)) {

		                pstmt.setString(1, memberId);
		                pstmt.setString(2, orderNo);
		                pstmt.setString(3, tid);

		                updated = pstmt.executeUpdate();
		            }
		         // 대기 주문이 이번 요청에서 실제로 취소된 경우에만 사용 포인트를 반환한다.
		            if (updated > 0) {
		                String findOrderSql =
		                        "SELECT order_id FROM orders WHERE member_id = ? AND order_no = ?";

		                try (PreparedStatement pstmt = con.prepareStatement(findOrderSql)) {
		                    pstmt.setString(1, memberId);
		                    pstmt.setString(2, orderNo);

		                    try (ResultSet rs = pstmt.executeQuery()) {
		                        if (!rs.next()) {
		                            throw new SQLException("포인트를 반환할 취소 주문을 찾을 수 없습니다.");
		                        }
		                        restoreUsedPoints(con, rs.getLong("order_id"));
		                    }
		                }
		            }
		            con.commit();

		            // 두 테이블을 변경하므로 1이 아닌 양수인지 검사
		            return updated > 0;

		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;

		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		// 대기 주문을 승인 처리 중으로 변경
		
		//로그인 회원의 픽업 주문을 검증하고 토스 승인 요청을 한 번만 시작
		public long beginPickupTossApproval(String memberId, String orderNo,
		                                    String paymentKey, int amount) throws SQLException {
		    String findOrderSql =
		            "SELECT order_id, total_amount, status FROM orders "
		            + "WHERE member_id = ? AND order_no = ? "
		            + "AND fulfillment_type = 'PICKUP' AND pickup_store_id IS NOT NULL "
		            + "FOR UPDATE";

		    String startOrderSql =
		            "UPDATE orders SET status = 'APPROVING' "
		            + "WHERE order_id = ? AND status = 'PENDING'";

		    String insertAttemptSql =
		            "INSERT INTO payment_attempts (order_id, method, status, amount, tid) "
		            + "VALUES (?, 'TOSSPAY', 'APPROVING', ?, ?)";

		    if (paymentKey == null || paymentKey.isBlank() || amount <= 0) {
		        throw new IllegalArgumentException("픽업 결제 인증 정보를 확인해 주세요.");
		    }

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();
		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            long orderId;
		            try (PreparedStatement stmt = con.prepareStatement(findOrderSql)) {
		                stmt.setString(1, memberId);
		                stmt.setString(2, orderNo);

		                try (ResultSet rs = stmt.executeQuery()) {
		                    if (!rs.next() || rs.getInt("total_amount") != amount) {
		                        throw new IllegalArgumentException("픽업 주문번호와 결제 금액을 확인해 주세요.");
		                    }
		                    if (!"PENDING".equals(rs.getString("status"))) {
		                        throw new IllegalStateException("이미 처리 중이거나 완료된 픽업 주문입니다.");
		                    }
		                    orderId = rs.getLong("order_id");
		                }
		            }

		            try (PreparedStatement stmt = con.prepareStatement(startOrderSql)) {
		                stmt.setLong(1, orderId);
		                if (stmt.executeUpdate() != 1) {
		                    throw new SQLException("픽업 주문을 승인 대기로 변경하지 못했습니다.");
		                }
		            }

		            try (PreparedStatement stmt = con.prepareStatement(insertAttemptSql)) {
		                stmt.setLong(1, orderId);
		                stmt.setInt(2, amount);
		                stmt.setString(3, paymentKey);
		                if (stmt.executeUpdate() != 1) {
		                    throw new SQLException("픽업 결제 시도를 저장하지 못했습니다.");
		                }
		            }

		            con.commit();
		            return orderId;
		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;
		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		
		//픽업준비가 끝난 주문의 회원 이메일과 수령매장을 발송 대기열에 저장
		private void enqueuePickupReadyEmail(Connection connection, String orderNo) 
			throws SQLException{
			String sql = 
					"INSERT INTO order_email_outbox "
				+"(order_id , event_type, recipient_email, subject, body) "
				+"SELECT o.order_id, 'PICKUP_READY' , m.email, "
				+"		'주문하신 상품의 픽업 준비가 완료되었습니다.',"
				+"		CONCAT('픽업 매장: ',s.store_name ,CHAR(10), "
				+"			   '주문 번호: ',o.order_no, CHAR(10), "
				+"			   '매장에 방문해 직원에게 주문번호를 보여주세요.') "
				+"FROM orders o "
				+"JOIN members m ON m.member_id = o.member_id "
				+"JOIN store_locations s ON s.store_id = o.pickup_store_id "
				+"WHERE o.order_no = ? "
				+"  AND o.fulfillment_type = 'PICKUP' "
				+"  AND o.pickup_status = 'READY' "
				+"  AND NULLIF(TRIM(m.email), '') IS NOT NULL "
				+"ON DUPLICATE KEY UPDATE email_id = order_email_outbox.email_id";
			
			try (PreparedStatement statement = connection.prepareStatement(sql)){
				statement.setString(1, orderNo);
				statement.executeUpdate();
			}
		}
		private static final SecureRandom PICKUP_TOKEN_RANDOM = new SecureRandom();
		
		//준비 완료된 주문에 QR로 사용할 무작위 코드와 만료 시각을 저장한다.
		private void issuePickupToken(Connection connection, String storeId, String orderNo)
				throws SQLException{
			byte[]randomBytes = new byte[32];
			PICKUP_TOKEN_RANDOM.nextBytes(randomBytes);
			String token = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(randomBytes);
			
			String sql = "UPDATE orders "
					+"SET pickup_token = ?, "
					+" 	  pickup_token_expires_at = DATE_ADD(NOW(), INTERVAL 3 DAY) "
					+"WHERE order_no = ? AND pickup_store_id = ? "
					+"AND pickup_status = 'READY' AND pickup_token IS NULL";
			
			try (PreparedStatement statement = connection.prepareStatement(sql)){
				statement.setString(1, token);
				statement.setString(2, orderNo);
				statement.setString(3, storeId);
				if(statement.executeUpdate() != 1) {
					throw new SQLException("픽업 QR코드를 발급하지 못했습니다.");
				}
			}
		}
		public boolean advancePickupStatus(Connection connection, String storeId,
		        
				String orderNo, boolean markPickedUp,String pickupToken) throws SQLException {
			
		    String sql = "UPDATE orders o SET o.pickup_status = ? "
		            + "WHERE o.order_no = ? AND o.pickup_store_id = ? "
		            + "AND o.fulfillment_type = 'PICKUP' AND o.status = 'PAID' "
		            + "AND o.canceled_at IS NULL AND o.pickup_status <=> ? "
		            + "AND EXISTS ("
		            + "    SELECT 1 FROM payment_attempts p "
		            + "    WHERE p.order_id = o.order_id AND p.status = 'APPROVED' "
		            + "    AND p.payment_id = ("
		            + "        SELECT MAX(p2.payment_id) FROM payment_attempts p2 "
		            + "        WHERE p2.order_id = o.order_id"
		            + "    )"
		            + ")"
		            +(markPickedUp
		            		?"AND o.pickup_token=? AND o.pickup_token_expires_at>NOW()"
		            		:"");
		            

		    try (PreparedStatement statement = connection.prepareStatement(sql)) {
		        statement.setString(1, markPickedUp ? "PICKED_UP" : "READY");
		        statement.setString(2, orderNo);
		        statement.setString(3, storeId);
		        if (markPickedUp) {
		            statement.setString(4, "READY");
		            statement.setString(5, pickupToken);
		        } else {
		            statement.setNull(4, java.sql.Types.VARCHAR);
		        }
		       boolean changed = statement.executeUpdate()==1;
		       if(changed && ! markPickedUp) {
		    	   issuePickupToken(connection, storeId, orderNo);
		    	   enqueuePickupReadyEmail(connection,orderNo);
		       }
		       return changed;
		    }
		}
		
		//담당 매장에서 아직 수령할수 있는 QR의 주문번호를 찾는다.
		public String findReadyPickupOrderNoByToken(
				Connection connection, String storeId,String pickupToken)
				throws SQLException{
			if(pickupToken == null || ! pickupToken.matches("[A-Za-z0-9_-]{43}")) {
				return null;
			}
			String sql = "SELECT o.order_no FROM orders o "
					+"WHERE o.pickup_token = ? AND o.pickup_store_id = ? "
					+"AND o.fulfillment_type = 'PICKUP' AND o.status = 'PAID' "
					+"AND o.pickup_status = 'READY' AND o.canceled_at IS NULL "
		            +"AND o.pickup_token_expires_at > NOW()";
			
			try (PreparedStatement statement=connection.prepareStatement(sql)){
				statement.setString(1, pickupToken);
				statement.setString(2, storeId);
				
				try(ResultSet result= statement.executeQuery()){
					return result.next() ? result.getString("order_no"):null;
				}
			}
		}
		
		public List<Map<String, Object>> findPaidPickupOrders(
		        Connection connection, String storeId) throws SQLException {
		    String sql = "WITH recent_pickups AS ("
		            + "    SELECT o.order_id, o.order_no, o.recipient,  o.pickup_status, p.approved_at "
		            + "    FROM orders o "
		            + "    JOIN payment_attempts p ON p.payment_id = ("
		            + "        SELECT MAX(p2.payment_id) FROM payment_attempts p2 "
		            + "        WHERE p2.order_id = o.order_id"
		            + "    ) "
		            + "    WHERE o.fulfillment_type = 'PICKUP' AND o.pickup_store_id = ? "
		            + "    AND o.status = 'PAID' AND p.status = 'APPROVED' "
		            + "    AND o.canceled_at IS NULL "
		            + "    ORDER BY p.approved_at DESC, o.order_id DESC LIMIT 50"
		            + ") "
		            + "SELECT p.order_id, p.order_no, p.recipient, p.pickup_status, p.approved_at, "
		            + "       i.product_name, i.quantity "
		            + "FROM recent_pickups p "
		            + "LEFT JOIN order_items i ON i.order_id = p.order_id "
		            + "ORDER BY p.approved_at DESC, p.order_id DESC, i.order_item_id ASC";

		    Map<Long, Map<String, Object>> orders = new java.util.LinkedHashMap<>();
		    Map<Long, List<String>> itemsByOrder = new HashMap<>();

		    try (PreparedStatement statement = connection.prepareStatement(sql)) {
		        statement.setString(1, storeId);

		        try (ResultSet result = statement.executeQuery()) {
		            while (result.next()) {
		                long orderId = result.getLong("order_id");
		                List<String> items = itemsByOrder.get(orderId);

		                if (items == null) {
		                    items = new ArrayList<>();
		                    itemsByOrder.put(orderId, items);

		                    Map<String, Object> order = new HashMap<>();
		                    order.put("orderNo", result.getString("order_no"));
		                    order.put("recipient", result.getString("recipient"));
		                    order.put("approvedAt", result.getTimestamp("approved_at"));
		                    order.put("pickupStatus", result.getString("pickup_status"));
		                    order.put("items", items);
		                    orders.put(orderId, order);
		                }

		                String productName = result.getString("product_name");
		                if (productName != null) {
		                    items.add(productName + " × " + result.getInt("quantity") + "개");
		                }
		            }
		        }
		    }

		    return new ArrayList<>(orders.values());
		}
		
		// 로그인 회원의 결제 완료된 매장픽업 주문 조회
		public Map<String, Object> findPaidPickupOrder(String memberId, String orderNo)
		        throws SQLException {
			String sql =
			        "SELECT o.order_no, o.recipient, o.subtotal_amount, o.pickup_fee, "
			        + "o.total_amount, p.method, "
			        + "COALESCE(s.store_name, o.pickup_store_id) AS store_name "
			        + "FROM orders o "
			        + "LEFT JOIN store_locations s ON s.store_id = o.pickup_store_id "
			        + "JOIN payment_attempts p ON p.order_id = o.order_id "
			        + "AND p.status = 'APPROVED' AND p.amount = o.total_amount "
			        + "AND p.method IN ('TOSSPAY', 'KAKAOPAY') "
			        + "WHERE o.member_id = ? AND o.order_no = ? "
			        + "AND o.fulfillment_type = 'PICKUP' AND o.status = 'PAID'";

		    try (Connection con = ds.getConnection();
		         PreparedStatement stmt = con.prepareStatement(sql)) {
		        stmt.setString(1, memberId);
		        stmt.setString(2, orderNo);

		        try (ResultSet rs = stmt.executeQuery()) {
		            if (!rs.next()) return null;

		            Map<String, Object> result = new HashMap<>();
		            result.put("orderNo", rs.getString("order_no"));
		            result.put("recipient", rs.getString("recipient"));
		            result.put("storeName", rs.getString("store_name"));
		            result.put("subtotal", rs.getInt("subtotal_amount"));
		            result.put("pickupFee", rs.getInt("pickup_fee"));
		            result.put("total", rs.getInt("total_amount"));
		            result.put("paymentMethod",
		                    "KAKAOPAY".equals(rs.getString("method")) ? "카카오페이" : "토스페이");
		            return result;
		        }
		    }
		}
		//토스 승인 결과, 선택 매장 재고, 픽업 장바구니를 한 트랜잭션에 반영
		// 토스페이와 카카오페이의 픽업 승인 결과를 매장 재고에 반영
		public void completePickupTossApproval(String memberId, long orderId,
		        String paymentKey, int amount, Timestamp approvedAt) throws SQLException {
		    completePickupApproval(memberId, orderId, paymentKey, amount, approvedAt, "TOSSPAY");
		}

		public void completePickupApproval(String memberId, long orderId,
		        String paymentKey, int amount, Timestamp approvedAt, String method) throws SQLException {
		    if (!"TOSSPAY".equals(method) && !"KAKAOPAY".equals(method)) {
		        throw new IllegalArgumentException("지원하지 않는 픽업 결제수단입니다.");
		    }


		    String lockSql =
		            "SELECT o.pickup_store_id, o.status AS order_status, p.status AS pay_status "
		            + "FROM orders o JOIN payment_attempts p ON p.order_id = o.order_id "
		            + "WHERE o.order_id = ? AND o.member_id = ? AND o.fulfillment_type = 'PICKUP' "
		            + "AND o.total_amount = ? AND p.amount = ? AND p.method = ? "
		            + "AND p.tid = ? FOR UPDATE";

		    // order_items.product_id and store_inventory.product_id share the products PK collation.
		    // Read the sorted item keys first, then lock each inventory row through its composite PK.
		    String itemsSql =
		            "SELECT product_id, SUM(quantity) AS qty FROM order_items "
		            + "WHERE order_id = ? GROUP BY product_id ORDER BY product_id";

		    String stockSql =
		            "UPDATE store_inventory SET quantity = quantity - ?, version = version + 1 "
		            + "WHERE store_id = ? AND product_id = ? AND is_carried = 1 AND quantity >= ?";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();
		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            String storeId;
		            try (PreparedStatement stmt = con.prepareStatement(lockSql)) {
		                stmt.setLong(1, orderId);
		                stmt.setString(2, memberId);
		                stmt.setInt(3, amount);
		                stmt.setInt(4, amount);
		                stmt.setString(5, method);
		                stmt.setString(6, paymentKey);

		                try (ResultSet rs = stmt.executeQuery()) {
		                    if (!rs.next()) throw new SQLException("일치하는 픽업 결제가 없습니다.");
		                    if ("PAID".equals(rs.getString("order_status"))
		                            && "APPROVED".equals(rs.getString("pay_status"))) {
		                        con.commit();
		                        return;
		                    }
		                    if (!"APPROVING".equals(rs.getString("order_status"))
		                            || !"APPROVING".equals(rs.getString("pay_status"))) {
		                        throw new SQLException("승인 처리 중인 픽업 주문이 아닙니다.");
		                    }
		                    storeId = rs.getString("pickup_store_id");
		                }
		            }

		            try (PreparedStatement items = con.prepareStatement(itemsSql);
		                 PreparedStatement stock = con.prepareStatement(stockSql)) {
		                items.setLong(1, orderId);
		                try (ResultSet rs = items.executeQuery()) {
		                    if (!rs.next()) throw new SQLException("픽업 주문상품이 없습니다.");
		                    do {
		                        long qty = rs.getLong("qty");
		                        if (qty <= 0) throw new SQLException("픽업 주문 수량이 잘못됐습니다.");
		                        stock.setLong(1, qty);
		                        stock.setString(2, storeId);
		                        stock.setString(3, rs.getString("product_id"));
		                        stock.setLong(4, qty);
		                        if (stock.executeUpdate() != 1) {
		                            throw new SQLException("선택 매장의 픽업 재고가 부족합니다.");
		                        }
		                    } while (rs.next());
		                }
		            }

		            try (PreparedStatement pay = con.prepareStatement(
		                    "UPDATE payment_attempts SET status = 'APPROVED', approved_at = ? "
		                    		+ "WHERE order_id = ? AND tid = ? AND method = ? AND status = 'APPROVING'");
		                 PreparedStatement order = con.prepareStatement(
		                    "UPDATE orders SET status = 'PAID', stock_deducted = TRUE "
		                    + "WHERE order_id = ? AND status = 'APPROVING'");
		                 PreparedStatement cart = con.prepareStatement(
		                    "DELETE c FROM pickup_cart_items c JOIN order_items i "
		                    + "ON i.order_id = ? AND i.product_id = c.product_id "
		                    + "WHERE c.member_id = ? AND c.store_id = ? AND c.quantity = i.quantity")) {
		                pay.setTimestamp(1, approvedAt);
		                pay.setLong(2, orderId);
		                pay.setString(3, paymentKey);
		                pay.setString(4, method);
		                
		                order.setLong(1, orderId);
		                if (pay.executeUpdate() != 1 || order.executeUpdate() != 1) {
		                    throw new SQLException("픽업 결제 완료 상태를 저장하지 못했습니다.");
		                }

		                cart.setLong(1, orderId);
		                cart.setString(2, memberId);
		                cart.setString(3, storeId);
		                cart.executeUpdate();
		            }
		            earnDaisoPoints(con, orderId);
		            con.commit();
		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;
		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		// 취소가 먼저 끝났다면 변경하지 못하므로 승인 요청을 보내지 않음
		public boolean beginPaymentApproval(
		        String memberId,
		        String orderNo,
		        String tid) throws SQLException {

		    String sql =
		        "UPDATE orders o "
		        + "JOIN payment_attempts p ON p.order_id = o.order_id "
		        + "SET o.status = 'APPROVING', "
		        + "p.status = 'APPROVING' "
		        + "WHERE o.member_id = ? "
		        + "AND o.order_no = ? "
		        + "AND p.tid = ? "
		        + "AND o.status = 'PENDING' "
		        + "AND p.status = 'PENDING'";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            int updated;

		            try (PreparedStatement pstmt =
		                    con.prepareStatement(sql)) {

		                pstmt.setString(1, memberId);
		                pstmt.setString(2, orderNo);
		                pstmt.setString(3, tid);

		                updated = pstmt.executeUpdate();
		            }

		            con.commit();
		            return updated > 0;

		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;

		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		public Map<String, Object> beginPaidOrderCancellation(
		        String memberId, String orderNo,String expectedMethod) throws SQLException {

		    String selectSql =
		        "SELECT o.order_id, o.total_amount, o.stock_deducted, "
		        + "o.status AS order_status, "
		        + "o.pickup_status, "
		        + "p.payment_id, p.tid, p.amount, p.method, "
		        + "p.status AS payment_status "
		        + "FROM orders o "
		        + "JOIN payment_attempts p ON p.order_id = o.order_id "
		        + "WHERE o.member_id = ? AND o.order_no = ? "
		        + "ORDER BY p.payment_id DESC "
		        + "LIMIT 1 FOR UPDATE";

		    String updateSql =
		        "UPDATE orders o "
		        + "JOIN payment_attempts p ON p.order_id = o.order_id "
		        + "SET o.status = 'CANCELLING', "
		        + "p.status = 'CANCELLING' "
		        + "WHERE o.order_id = ? "
		        + "AND o.member_id = ? "
		        + "AND p.payment_id = ? "
		        + "AND o.status = 'PAID' "
		        + "AND p.status = 'APPROVED'";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            Map<String, Object> order = new HashMap<>();
		            boolean restoreStock;

		            // 주문과 결제 행을 잠그고 본인 주문·상태 확인
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(selectSql)) {

		                pstmt.setString(1, memberId);
		                pstmt.setString(2, orderNo);

		                try (ResultSet rs = pstmt.executeQuery()) {
		                    if (!rs.next()) {
		                        throw new IllegalArgumentException(
		                            "본인의 주문을 찾을 수 없습니다."
		                        );
		                    }
		                    
		                    if ("PICKED_UP".equals(rs.getString("pickup_status"))) {
		                        throw new IllegalArgumentException("수령이 끝난 픽업 주문은 취소할 수 없습니다.");
		                    }

		                    if (!"PAID".equals(rs.getString("order_status"))
		                            || !"APPROVED".equals(
		                                rs.getString("payment_status"))) {

		                        throw new IllegalArgumentException(
		                            "이미 처리 중이거나 취소 가능한 주문이 아닙니다."
		                        );
		                    }
		                    //잠근 결제기록의 결제수단 확인
		                    if(!expectedMethod.equals(rs.getString("method"))) {
		                    	throw new IllegalArgumentException("주문의 결제수단을 다시 확인해 주세요.");
		                    }
		                    String tid = rs.getString("tid");
		                    kakaoPayClient.requireGatewayIdentifier(tid);
		                    kakaoPayClient.requireGatewayIdentifier(orderNo);
		                    int total = rs.getInt("total_amount");

		                    if (tid == null || tid.isBlank()
		                            || total <= 0
		                            || total != rs.getInt("amount")) {

		                        throw new IllegalArgumentException(
		                            "결제 번호와 금액을 확인해 주세요."
		                        );
		                    }

		                    restoreStock = rs.getBoolean("stock_deducted");
		                    order.put("orderId", rs.getLong("order_id"));
		                    order.put("paymentId", rs.getLong("payment_id"));
		                    order.put("tid", tid);
		                    order.put("total", total);
		                }
		            }

		            if (restoreStock) OnlineStockRestoration.requireSchema(con);

		            // 확인한 결제만 취소 처리 중으로 변경
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(updateSql)) {

		                pstmt.setLong(
		                    1, ((Number) order.get("orderId")).longValue()
		                );
		                pstmt.setString(2, memberId);
		                pstmt.setLong(
		                    3, ((Number) order.get("paymentId")).longValue()
		                );

		                if (pstmt.executeUpdate() <= 0) {
		                    throw new SQLException(
		                        "취소 처리 중 상태로 변경하지 못했습니다."
		                    );
		                }
		            }

		            con.commit();

		            return order;

		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;

		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		// 카카오페이 취소 성공을 확인한 뒤에만 호출
		public void completePaidOrderCancellation(
		        String memberId,
		        long orderId,
		        long paymentId,
		        String tid,
		        int amount,
		        Timestamp canceledAt) throws SQLException {

		    if (canceledAt == null) {
		        throw new IllegalArgumentException(
		            "결제 취소 시각이 없습니다."
		        );
		    }

		    String checkSql =
		        "SELECT o.status AS order_status, o.total_amount, o.stock_deducted, "
		        + "p.status AS payment_status, p.amount "
		        + "FROM orders o "
		        + "JOIN payment_attempts p ON p.order_id = o.order_id "
		        + "WHERE o.member_id = ? "
		        + "AND o.order_id = ? "
		        + "AND p.payment_id = ? "
		        + "AND p.tid = ? FOR UPDATE";

		    try (Connection con = ds.getConnection()) {
		        boolean originalAutoCommit = con.getAutoCommit();

		        con.setReadOnly(false);
		        con.setAutoCommit(false);

		        try {
		            boolean restoreStock;
		            try (PreparedStatement pstmt =
		                    con.prepareStatement(checkSql)) {

		                pstmt.setString(1, memberId);
		                pstmt.setLong(2, orderId);
		                pstmt.setLong(3, paymentId);
		                pstmt.setString(4, tid);

		                try (ResultSet rs = pstmt.executeQuery()) {
		                    if (!rs.next()) {
		                        throw new SQLException(
		                            "취소할 주문과 결제정보를 찾을 수 없습니다."
		                        );
		                    }

		                    if (amount <= 0
		                            || rs.getInt("total_amount") != amount
		                            || rs.getInt("amount") != amount) {
		                        throw new SQLException(
		                            "취소 금액과 주문 금액이 다릅니다."
		                        );
		                    }

		                    restoreStock = rs.getBoolean("stock_deducted");
		                    String orderStatus =
		                        rs.getString("order_status");

		                    String paymentStatus =
		                        rs.getString("payment_status");

		                    // 이미 반영한 취소 결과는 중복 변경하지 않음
		                    if ("CANCELLED".equals(orderStatus)
		                            && "CANCELLED".equals(paymentStatus)) {
		                        con.commit();
		                        return;
		                    }

		                    if (!"CANCELLING".equals(orderStatus)
		                            || !"CANCELLING".equals(paymentStatus)) {
		                        throw new SQLException(
		                            "취소완료로 변경할 수 없는 상태입니다."
		                        );
		                    }
		                }
		            }

		            if (restoreStock) {
		                OnlineStockRestoration.restore(con, orderId, OnlineStockRestoration.Reason.ORDER_CANCEL);
		            }

		            String paymentSql =
		                "UPDATE payment_attempts "
		                + "SET status = 'CANCELLED' "
		                + "WHERE payment_id = ? AND order_id = ? "
		                + "AND tid = ? AND status = 'CANCELLING'";

		            try (PreparedStatement pstmt =
		                    con.prepareStatement(paymentSql)) {

		                pstmt.setLong(1, paymentId);
		                pstmt.setLong(2, orderId);
		                pstmt.setString(3, tid);

		                if (pstmt.executeUpdate() != 1) {
		                    throw new SQLException(
		                        "결제 취소 상태를 저장하지 못했습니다."
		                    );
		                }
		            }

		            String orderSql =
		                "UPDATE orders "
		                + "SET status = 'CANCELLED', canceled_at = ?, "
		                + "hidden_at = NULL "
		                + "WHERE order_id = ? AND member_id = ? "
		                + "AND status = 'CANCELLING'";

		            try (PreparedStatement pstmt =
		                    con.prepareStatement(orderSql)) {

		                pstmt.setTimestamp(1, canceledAt);
		                pstmt.setLong(2, orderId);
		                pstmt.setString(3, memberId);

		                if (pstmt.executeUpdate() != 1) {
		                    throw new SQLException(
		                        "주문 취소 상태를 저장하지 못했습니다."
		                    );
		                }
		            }
		            reverseDaisoPoints(con,orderId);//적립한 포인트 회수
		            restoreUsedPoints(con,orderId);//결제할 때 사용했던 포인트 반환
		            con.commit();

		        } catch (SQLException | RuntimeException e) {
		            con.rollback();
		            throw e;

		        } finally {
		            con.setAutoCommit(originalAutoCommit);
		        }
		    }
		}
		
}


