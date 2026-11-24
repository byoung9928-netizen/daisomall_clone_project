package daiso.order.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import daiso.order.payment.kakaoPayClient;

public class ReturnDAO {

    private final DataSource ds;
    //본인의 배송완료 결제승인주문만 가능
    public ReturnDAO() {
        try {
            ds = InitialContext.doLookup(
                "java:comp/env/jdbc/mysqlDB"
            );
        } catch (NamingException e) {
            throw new IllegalStateException(
                "반품 DB 연결 설정을 확인해 주세요.", e
            );
        }
    }

    // 배송완료된 본인 주문의 전체 반품 신청
    public boolean requestReturn(
            String memberId,
            String orderNo,
            String reason,
            String reasonDetail) throws SQLException {

        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException(
                "로그인 후 이용해 주세요."
            );
        }

        if (orderNo == null || orderNo.isBlank()
                || orderNo.length() > 100) {
            throw new IllegalArgumentException(
                "주문번호를 확인해 주세요."
            );
        }

        if (reason == null || reason.isBlank()
                || reason.length() > 100) {
            throw new IllegalArgumentException(
                "반품 사유를 확인해 주세요."
            );
        }

        reasonDetail =
            reasonDetail == null ? "" : reasonDetail.trim();

        if (reasonDetail.length() > 500) {
            throw new IllegalArgumentException(
                "상세 사유는 500자 이내로 입력해 주세요."
            );
        }

        if ("기타".equals(reason.trim())
                && reasonDetail.isBlank()) {
            throw new IllegalArgumentException(
                "기타 반품 사유를 입력해 주세요."
            );
        }

        // 환불 예정 금액은 입력값이 아닌 DB 주문 금액 사용
        String sql =
            "INSERT INTO order_returns "
            + "(order_id, reason, reason_detail, status, refund_amount) "
            + "SELECT o.order_id, ?, ?, 'REQUESTED', o.total_amount "
            + "FROM orders o "
            + "JOIN payment_attempts p "
            + "ON p.payment_id = ("
            + "SELECT MAX(p2.payment_id) "
            + "FROM payment_attempts p2 "
            + "WHERE p2.order_id = o.order_id"
            + ") "
            + "WHERE o.member_id = ? "
            + "AND o.order_no = ? "
            + "AND o.status = 'DELIVERED' "
            + "AND p.status = 'APPROVED' "
            + "AND o.total_amount = p.amount "
            + "AND o.total_amount > 0";

        try (Connection con = ds.getConnection()) {
            boolean originalAutoCommit = con.getAutoCommit();

            con.setReadOnly(false);
            con.setAutoCommit(false);

            try {
                int inserted;

                try (PreparedStatement pstmt =
                        con.prepareStatement(sql)) {

                    pstmt.setString(1, reason.trim());
                    pstmt.setString(2, reasonDetail);
                    pstmt.setString(3, memberId);
                    pstmt.setString(4, orderNo);

                    inserted = pstmt.executeUpdate();
                }

                con.commit();

                return inserted == 1;

            } catch (SQLException e) {
                con.rollback();

                // order_id의 UNIQUE 제약으로 중복 신청 방지
                if (e.getErrorCode() == 1062) {
                    throw new IllegalArgumentException(
                        "이미 반품 신청한 주문입니다.", e
                    );
                }

                throw e;

            } catch (RuntimeException e) {
                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(originalAutoCommit);
            }
        }
    }
    
    // 전체 회원의 최근 반품 신청 50건 조회
    public List<Map<String, Object>> getReturnList() throws SQLException {
        return getReturnList(false);
    }


    // ===== 엑셀용: 화면과 같은 반품 조회에서 50건 제한만 제외 =====
    public List<Map<String, Object>> getReturnList(boolean pendingOnly)
            throws SQLException {
        return queryReturns(true, pendingOnly);
    }

    public List<Map<String, Object>> getReturnsForExport() throws SQLException {
        return queryReturns(false, false);
    }
    
    private List<Map<String, Object>> queryReturns(boolean limited, boolean pendingOnly)
            throws SQLException {
        String sql =
            "SELECT r.*, o.order_no, o.member_id, "
            + "(SELECT oi.product_name FROM order_items oi "
            + "WHERE oi.order_id = o.order_id "
            + "ORDER BY oi.order_item_id LIMIT 1) AS first_product_name, "
            + "(SELECT COUNT(*) FROM order_items oi2 "
            + "WHERE oi2.order_id = o.order_id) AS product_count "
            + "FROM order_returns r "
            + "JOIN orders o ON o.order_id = r.order_id "
            + (pendingOnly
                    ? "WHERE r.status = 'REQUESTED' ORDER BY r.requested_at ASC, r.return_id ASC "
                    : "ORDER BY r.requested_at DESC, r.return_id DESC ")
            + (limited ? "LIMIT 50" : "");

        List<Map<String, Object>> returns = new ArrayList<>();

        try (
            Connection con = ds.getConnection();
            PreparedStatement pstmt = con.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();

                item.put("returnId", rs.getLong("return_id"));
                item.put("orderId", rs.getLong("order_id"));
                item.put("orderNo", rs.getString("order_no"));
                item.put("memberId", rs.getString("member_id"));

                item.put("reason", rs.getString("reason"));
                item.put("reasonDetail", rs.getString("reason_detail"));
                item.put("status", rs.getString("status"));
                item.put("refundAmount", rs.getInt("refund_amount"));
                item.put("adminNote", rs.getString("admin_note"));

                item.put(
                    "requestedAt", rs.getTimestamp("requested_at")
                );
                item.put(
                    "processedAt", rs.getTimestamp("processed_at")
                );

                String productName =
                    rs.getString("first_product_name");

                int productCount = rs.getInt("product_count");

                String summary = productName == null
                    ? "상품정보 없음" : productName;

                if (productCount > 1) {
                    summary += " 외 " + (productCount - 1) + "종";
                }

                item.put("productSummary", summary);

                returns.add(item);
            }
        }

        return returns;
    }
    
    //반품, 주문, 결제정보 검증, 중복환불막기위해 환불 처리중으로 변경
    public Map<String, Object> beginRefund(long returnId)
            throws SQLException {

        if (returnId <= 0) {
            throw new IllegalArgumentException(
                "반품 신청 번호를 확인해 주세요."
            );
        }

        String selectSql =
            "SELECT r.status AS return_status, r.refund_amount, "
            + "o.order_id, o.order_no, o.member_id, o.stock_deducted, "
            + "o.status AS order_status, o.total_amount, "
            + "p.payment_id, p.tid, p.amount, "
            + "p.status AS payment_status "
            + "FROM order_returns r "
            + "JOIN orders o ON o.order_id = r.order_id "
            + "JOIN payment_attempts p "
            + "ON p.payment_id = ("
            + "SELECT MAX(p2.payment_id) "
            + "FROM payment_attempts p2 "
            + "WHERE p2.order_id = o.order_id"
            + ") "
            + "WHERE r.return_id = ? FOR UPDATE";

        String updateSql =
            "UPDATE order_returns r "
            + "JOIN orders o ON o.order_id = r.order_id "
            + "JOIN payment_attempts p ON p.order_id = o.order_id "
            + "SET r.status = 'REFUNDING', "
            + "o.status = 'REFUNDING', "
            + "p.status = 'REFUNDING' "
            + "WHERE r.return_id = ? "
            + "AND p.payment_id = ? "
            + "AND r.status = 'REQUESTED' "
            + "AND o.status = 'DELIVERED' "
            + "AND p.status = 'APPROVED'";

        try (Connection con = ds.getConnection()) {
            boolean originalAutoCommit = con.getAutoCommit();

            con.setReadOnly(false);
            con.setAutoCommit(false);

            try {
                Map<String, Object> target = new HashMap<>();
                boolean restoreStock;

                // 반품·주문·결제 정보를 잠그고 확인
                try (PreparedStatement pstmt =
                        con.prepareStatement(selectSql)) {

                    pstmt.setLong(1, returnId);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                "반품 신청과 결제정보를 찾을 수 없습니다."
                            );
                        }

                        boolean canRefund =
                            "REQUESTED".equals(
                                rs.getString("return_status"))
                            && "DELIVERED".equals(
                                rs.getString("order_status"))
                            && "APPROVED".equals(
                                rs.getString("payment_status"));

                        if (!canRefund) {
                            throw new IllegalArgumentException(
                                "이미 처리 중이거나 환불 가능한 상태가 아닙니다."
                            );
                        }

                        int amount = rs.getInt("refund_amount");
                        String tid = rs.getString("tid");
                        kakaoPayClient.requireGatewayIdentifier(tid);
                        kakaoPayClient.requireGatewayIdentifier(rs.getString("order_no"));

                        if (amount <= 0
                                || amount != rs.getInt("total_amount")
                                || amount != rs.getInt("amount")
                                || tid == null || tid.isBlank()) {

                            throw new IllegalArgumentException(
                                "환불 금액과 결제 번호를 확인해 주세요."
                            );
                        }

                        restoreStock = rs.getBoolean("stock_deducted");
                        target.put("returnId", returnId);
                        target.put("orderId", rs.getLong("order_id"));
                        target.put("orderNo", rs.getString("order_no"));
                        target.put("memberId", rs.getString("member_id"));
                        target.put("paymentId", rs.getLong("payment_id"));
                        target.put("tid", tid);
                        target.put("amount", amount);
                    }
                }

                if (restoreStock) OnlineStockRestoration.requireSchema(con);

                // 같은 신청에 대한 추가 환불 요청 차단
                try (PreparedStatement pstmt =
                        con.prepareStatement(updateSql)) {

                    pstmt.setLong(1, returnId);
                    pstmt.setLong(
                        2, ((Number) target.get("paymentId")).longValue()
                    );

                    if (pstmt.executeUpdate() <= 0) {
                        throw new SQLException(
                            "환불처리중 상태로 변경하지 못했습니다."
                        );
                    }
                }

                con.commit();

                return target;

            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(originalAutoCommit);
            }
        }
    }
    
    // 카카오페이 취소 성공 확인 후 반품·주문·결제 상태를 함께 확정
    public void completeRefund(
            long returnId,
            String tid,
            int amount,
            Timestamp refundedAt) throws SQLException {

        if (returnId <= 0 || tid == null || tid.isBlank()
                || amount <= 0 || refundedAt == null) {
            throw new IllegalArgumentException(
                "환불 완료 정보를 확인해 주세요."
            );
        }

        String checkSql =
            "SELECT r.status AS return_status, r.refund_amount, "
            + "o.status AS order_status, o.order_id, o.total_amount, o.stock_deducted, "
            + "p.status AS payment_status, p.amount "
            + "FROM order_returns r "
            + "JOIN orders o ON o.order_id = r.order_id "
            + "JOIN payment_attempts p ON p.order_id = o.order_id "
            + "WHERE r.return_id = ? AND p.tid = ? "
            + "FOR UPDATE";

        String updateSql =
            "UPDATE order_returns r "
            + "JOIN orders o ON o.order_id = r.order_id "
            + "JOIN payment_attempts p ON p.order_id = o.order_id "
            + "SET r.status = 'REFUNDED', r.processed_at = ?, "
            + "o.status = 'REFUNDED', o.canceled_at = ?, "
            + "o.hidden_at = NULL, "
            + "p.status = 'CANCELLED' "
            + "WHERE r.return_id = ? AND p.tid = ? "
            + "AND r.status = 'REFUNDING' "
            + "AND o.status = 'REFUNDING' "
            + "AND p.status = 'REFUNDING'";

        try (Connection con = ds.getConnection()) {
            boolean originalAutoCommit = con.getAutoCommit();

            con.setReadOnly(false);
            con.setAutoCommit(false);

            try {
                long orderId;
                boolean restoreStock;
                try (PreparedStatement pstmt =
                        con.prepareStatement(checkSql)) {

                    pstmt.setLong(1, returnId);
                    pstmt.setString(2, tid);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                "환불 대상 정보를 찾을 수 없습니다."
                            );
                        }

                        if (amount != rs.getInt("refund_amount")
                                || amount != rs.getInt("total_amount")
                                || amount != rs.getInt("amount")) {
                            throw new SQLException(
                                "환불 금액이 일치하지 않습니다."
                            );
                        }

                        orderId = rs.getLong("order_id");
                        restoreStock = rs.getBoolean("stock_deducted");
                        String returnStatus =
                            rs.getString("return_status");

                        String orderStatus =
                            rs.getString("order_status");

                        String paymentStatus =
                            rs.getString("payment_status");

                        // 이미 저장된 결과는 중복 변경하지 않음
                        if ("REFUNDED".equals(returnStatus)
                                && "REFUNDED".equals(orderStatus)
                                && "CANCELLED".equals(paymentStatus)) {
                            con.commit();
                            return;
                        }

                        if (!"REFUNDING".equals(returnStatus)
                                || !"REFUNDING".equals(orderStatus)
                                || !"REFUNDING".equals(paymentStatus)) {
                            throw new SQLException(
                                "환불완료로 변경할 수 없는 상태입니다."
                            );
                        }
                    }
                }

                if (restoreStock) {
                    OnlineStockRestoration.restore(con, orderId, OnlineStockRestoration.Reason.ORDER_REFUND);
                }

                try (PreparedStatement pstmt =
                        con.prepareStatement(updateSql)) {

                    pstmt.setTimestamp(1, refundedAt);
                    pstmt.setTimestamp(2, refundedAt);
                    pstmt.setLong(3, returnId);
                    pstmt.setString(4, tid);

                    if (pstmt.executeUpdate() <= 0) {
                        throw new SQLException(
                            "환불완료 상태를 저장하지 못했습니다."
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
    
    // 접수 상태인 반품만 반려하고 사유를 저장
    public boolean rejectReturn(long returnId, String adminNote)
            throws SQLException {

        String sql =
            "UPDATE order_returns "
            + "SET status = 'REJECTED', admin_note = ?, "
            + "processed_at = CURRENT_TIMESTAMP "
            + "WHERE return_id = ? AND status = 'REQUESTED'";

        try (Connection con = ds.getConnection()) {
            boolean originalAutoCommit = con.getAutoCommit();

            con.setReadOnly(false);
            con.setAutoCommit(false);

            try (PreparedStatement pstmt = con.prepareStatement(sql)) {
                pstmt.setString(1, adminNote);
                pstmt.setLong(2, returnId);

                boolean rejected = pstmt.executeUpdate() == 1;
                con.commit();
                return rejected;

            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(originalAutoCommit);
            }
        }
    }
}
