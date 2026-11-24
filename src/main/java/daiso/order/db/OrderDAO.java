package daiso.order.db;
import java.util.List;
import java.util.ArrayList;
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
		public long createOrder(OrderBean order) throws SQLException {

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

		        calculatedSubtotal += item.getItemTotal();
		    }

		    if (calculatedSubtotal != order.getSubtotalAmount()
		            || order.getShippingFee() < 0
		            || (long) order.getSubtotalAmount()
		                + order.getShippingFee() != order.getTotalAmount()) {

		        throw new IllegalArgumentException(
		            "주문 금액이 일치하지 않습니다."
		        );
		    }

		    String orderSql =
		        "INSERT INTO orders "
		        + "(order_no, member_id, checkout_token, status, "
		        + "recipient, phone, postcode, address1, address2, "
		        + "delivery_message, subtotal_amount, shipping_fee, "
		        + "total_amount) "
		        + "VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
		public void savePaymentReady(
		        long orderId, String tid, int amount)
		        throws SQLException {

		    if (tid == null || tid.isBlank()) {
		        throw new IllegalArgumentException(
		            "카카오페이 결제번호가 없습니다."
		        );
		    }

		    String sql =
		        "INSERT INTO payment_attempts "
		        + "(order_id, method, status, amount, tid) "
		        + "SELECT order_id, 'KAKAOPAY', 'PENDING', "
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

		                pstmt.setString(1, tid);
		                pstmt.setLong(2, orderId);
		                pstmt.setInt(3, amount);

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
		public List<Map<String, Object>> getMyOrders(String memberId)
		        throws SQLException {

		    String sql =
		        "SELECT o.order_id, o.order_no, o.status, "
		        + "o.total_amount, o.created_at, "
		        + "p.approved_at "
		        + "FROM orders o "
		        + "LEFT JOIN payment_attempts p "
		        + "ON p.payment_id = ("
		        + "SELECT MAX(p2.payment_id) "
		        + "FROM payment_attempts p2 "
		        + "WHERE p2.order_id = o.order_id"
		        + ") "
		        + "WHERE o.member_id = ? "
		        + "AND o.hidden_at IS NULL "
		        + "ORDER BY o.created_at DESC, o.order_id DESC "
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

		                orders.add(order);
		            }
		        }
		    }

		    return orders;
		}
		
		public Map<String, Object> getMyOrderDetail(
		        String memberId, String orderNo) throws SQLException {

				String sql =
				    "SELECT o.*, p.status AS payment_status, "
				    + "p.approved_at, r.status AS return_status, "
				    + "r.admin_note AS return_admin_note "
				    + "FROM orders o "
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
		        String memberId, String orderNo) throws SQLException {

		    String selectSql =
		        "SELECT o.order_id, o.total_amount, o.stock_deducted, "
		        + "o.status AS order_status, "
		        + "p.payment_id, p.tid, p.amount, "
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

		                    if (!"PAID".equals(rs.getString("order_status"))
		                            || !"APPROVED".equals(
		                                rs.getString("payment_status"))) {

		                        throw new IllegalArgumentException(
		                            "이미 처리 중이거나 취소 가능한 주문이 아닙니다."
		                        );
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


