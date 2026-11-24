package daiso.order.controller;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.JsonObject;

import daiso.order.delivery.DemoTrackingNumber;
import daiso.order.payment.TossPayClient;
import daiso.cart.db.CartBean;
import daiso.cart.db.CartDAO;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.order.db.OrderBean;
import daiso.order.db.OrderDAO;

@DaisoController
public class TossPayController {
	//==토스페이 결제 준비: 선택 상품 재고 금액 확인
    @DaisoPostMapping("/payment/toss/ready")
    @DaisoResponseBody
    public String ready(HttpServletRequest request, HttpServletResponse response) throws SQLException {
        response.setHeader("Cache-Control", "no-store");
        String clientKey = System.getenv("TOSS_PAYMENTS_CLIENT_KEY");
        String secretKey = System.getenv("TOSS_PAYMENTS_SECRET_KEY");
        if (clientKey == null || !clientKey.startsWith("test_ck")
                || secretKey == null || !secretKey.startsWith("test_sk")) {
            return error("토스페이 테스트 키 설정을 확인해 주세요.");
        }

        HttpSession session = request.getSession(false);
        Object member = session == null ? null : session.getAttribute("member_id");
        if (!(member instanceof String) || ((String) member).isBlank()) {
            return error("로그인 후 이용해 주세요.");
        }
        String memberId = (String) member;

        Object csrf = session.getAttribute("orderCsrf");
        if (!(csrf instanceof String) || !csrf.equals(request.getParameter("csrfToken"))) {
            return error("주문서를 새로고침해 주세요.");
        }
        if (!"true".equals(request.getParameter("consent"))
                || !(session.getAttribute("orderDeliveryAddress") instanceof Map)) {
            return error("배송지와 주문 동의를 확인해 주세요.");
        }

        String[] values = request.getParameterValues("cartItemId");
        if (values == null || values.length == 0) {
            return error("주문할 상품을 선택해 주세요.");
        }

        Set<Long> remaining = new HashSet<>();
        try {
            for (String value : values) {
                long id = Long.parseLong(value);
                if (id <= 0) return error("장바구니 상품을 확인해 주세요.");
                remaining.add(id);
            }
        } catch (NumberFormatException e) {
            return error("장바구니 상품을 확인해 주세요.");
        }

        List<CartBean> items = new ArrayList<>();
        for (CartBean item : new CartDAO().getCartList(memberId)) {
            if (remaining.remove(item.getCartItemId())) items.add(item);
        }
        if (!remaining.isEmpty() || items.isEmpty()
                || items.stream().anyMatch(item -> !item.isPurchasable())) {
            return error("선택한 상품이나 재고가 변경됐어요. 장바구니를 확인해 주세요.");
        }

        long subtotal = items.stream().mapToLong(CartBean::getItemTotal).sum();
        long amount = subtotal + (subtotal > 0 && subtotal < 30000 ? 3000 : 0);
        if (amount <= 0 || amount > Integer.MAX_VALUE
                || !Long.toString(amount).equals(request.getParameter("expectedAmount"))) {
            return error("결제 금액이 변경됐어요. 주문서를 새로고침해 주세요.");
        }
       

        String orderId = UUID.randomUUID().toString();
        // ===== 토스페이 인증 요청 전 택배 주문과 상품을 결제 대기로 저장 =====
        Map<?, ?> address = (Map<?, ?>) session.getAttribute("orderDeliveryAddress");

        OrderBean order = new OrderBean();
        order.setOrderNo(orderId);
        order.setCheckoutToken(orderId);
        order.setMemberId(memberId);
        order.setRecipient((String) address.get("recipient"));
        order.setPhone((String) address.get("phone"));
        order.setPostcode((String) address.get("postcode"));
        order.setAddress1((String) address.get("address1"));
        order.setAddress2((String) address.get("address2"));
        order.setDeliveryMessage((String) session.getAttribute("orderDeliveryMessage"));
        order.setSubtotalAmount(Math.toIntExact(subtotal));
        order.setShippingFee(Math.toIntExact(amount - subtotal));
        order.setTotalAmount(Math.toIntExact(amount));
        order.setItems(items);

        long dbOrderId = new OrderDAO().createOrder(order);
        String customerKey;
        synchronized (session) {
            customerKey = (String) session.getAttribute("tossCustomerKey");
            if (customerKey == null) {
                customerKey = UUID.randomUUID().toString();
                session.setAttribute("tossCustomerKey", customerKey);
            }
         // ===== 결제완료 화면에 표시할 택배 주문 정보 보관 =====
            Map<String, Object> draft = new java.util.HashMap<>();
            draft.put("memberId", memberId);
            draft.put("orderId", orderId);
            draft.put("dbOrderId", dbOrderId);
            draft.put("amount", Long.toString(amount));
            draft.put("items", items);
            draft.put("address", new java.util.HashMap<>(address));
            draft.put("message", order.getDeliveryMessage());
            draft.put("subtotal", subtotal);
            draft.put("shipping", amount - subtotal);
            session.setAttribute("tossPreview", draft);
        }

        JsonObject result = new JsonObject();
        result.addProperty("clientKey", clientKey);
        result.addProperty("customerKey", customerKey);
        result.addProperty("orderId", orderId);
        result.addProperty("orderName", "택배배송 주문 " + items.size() + "종");
        result.addProperty("amount", amount);
        return result.toString();
    }
    //토프세이 테스트 인증 결과 확인
    @DaisoGetMapping("/payment/toss/preview-success")
    @DaisoResponseBody
    public String previewSuccess(HttpServletRequest request, HttpServletResponse response)
    			throws SQLException{
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        Object saved = session == null ? null : session.getAttribute("tossPreview");
        String paymentKey = request.getParameter("paymentKey");
        if (!(saved instanceof Map) || paymentKey == null || paymentKey.isBlank()) {
            return "테스트 주문을 확인할 수 없습니다.";
        }

        Map<?, ?> draft = (Map<?, ?>) saved;
        if (!draft.get("memberId").equals(session.getAttribute("member_id"))
                || !draft.get("orderId").equals(request.getParameter("orderId"))
                || !draft.get("amount").equals(request.getParameter("amount"))) {
            return "테스트 주문 정보가 일치하지 않습니다.";
        }
     // ===== 택배배송 토스페이 승인 요청 및 주문 완료 처리 =====
        String memberId = (String) draft.get("memberId");
        String orderNo = (String) draft.get("orderId");
        long dbOrderId = ((Number) draft.get("dbOrderId")).longValue();
        int amount = Integer.parseInt((String) draft.get("amount"));
        OrderDAO orderDAO = new OrderDAO();

        synchronized (session) {
            if (Boolean.TRUE.equals(session.getAttribute("tossStarted:" + orderNo))) {
                return "이미 승인 요청을 보낸 주문입니다. 주문 내역을 확인해 주세요.";
            }

            orderDAO.savePaymentReady(dbOrderId, paymentKey, amount, "TOSSPAY");
            if (!orderDAO.beginPaymentApproval(memberId, orderNo, paymentKey)) {
                return "승인을 시작할 수 없습니다. 주문 상태를 확인해 주세요.";
            }
            session.setAttribute("tossStarted:" + orderNo, Boolean.TRUE);
        }

        try {
            JsonObject payment = new TossPayClient().confirm(paymentKey, orderNo, amount);

            if (!"DONE".equals(payment.get("status").getAsString())
                    || !paymentKey.equals(payment.get("paymentKey").getAsString())
                    || !orderNo.equals(payment.get("orderId").getAsString())
                    || amount != payment.get("totalAmount").getAsInt()) {
                return "토스 승인 결과와 주문 정보가 일치하지 않습니다. 주문 상태를 확인해 주세요.";
            }

            Timestamp approvedAt = Timestamp.from(
                    OffsetDateTime.parse(payment.get("approvedAt").getAsString()).toInstant());

            orderDAO.approvePayment(memberId, dbOrderId, paymentKey, amount,
                    approvedAt, DemoTrackingNumber.create());

         // ===== 토스페이 승인 및 주문 저장 완료 후 장바구니로 이동 =====
         // ===== 승인이 저장된 주문을 기존 결제완료 화면에 표시 =====
            Map<String, Object> completed = new java.util.HashMap<>();
            completed.put("orderNo", orderNo);
            completed.put("items", draft.get("items"));
            completed.put("address", draft.get("address"));
            completed.put("message", draft.get("message"));
            completed.put("subtotal", draft.get("subtotal"));
            completed.put("shipping", draft.get("shipping"));
            completed.put("total", amount);
            completed.put("paymentMethod", "토스페이");
            completed.put("paidAt", OffsetDateTime.parse(payment.get("approvedAt").getAsString())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            request.setAttribute("completedOrder", completed);
            request.getRequestDispatcher("/WEB-INF/views/order/payment-result.jsp")
                    .forward(request, response);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "승인 결과 확인이 중단됐습니다. 다시 결제하기 전에 주문 상태를 확인해 주세요.";
        } catch (Exception e) {
            request.getServletContext().log("토스페이 승인 결과 확인 실패. 주문번호=" + orderNo, e);
            return "결제 결과를 확인하지 못했습니다. 다시 결제하기 전에 주문 상태를 확인해 주세요.";
        }
    }

    @DaisoGetMapping("/payment/toss/preview-fail")
    @DaisoResponseBody
    public String previewFail() {
        return "토스페이 테스트 인증이 취소됐거나 실패했습니다.";
    }
    //  토스페이 매장픽업 결제 전 수령인·매장 재고·결제 금액 검증 
    @DaisoPostMapping("/payment/toss/pickup/check")
    @DaisoResponseBody
    public String checkPickup(HttpServletRequest request, HttpServletResponse response)
            throws SQLException {
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        Object member = session == null ? null : session.getAttribute("member_id");
        if (!(member instanceof String) || ((String) member).isBlank()) {
            return error("로그인 후 이용해 주세요.");
        }

        Object csrf = session.getAttribute("orderCsrf");
        if (!(csrf instanceof String) || !csrf.equals(request.getParameter("csrfToken"))) {
            return error("주문서를 새로고침해 주세요.");
        }

        String recipient = request.getParameter("recipient");
        String phone = request.getParameter("phone");
        if (recipient == null || recipient.isBlank() || phone == null
                || !phone.replaceAll("[\\s-]", "").matches("01[0-9]{8,9}")) {
            return error("수령인 이름과 휴대폰 번호를 확인해 주세요.");
        }

        String storeId = request.getParameter("storeId");
        String[] productIds = request.getParameterValues("productId");
        if (storeId == null || storeId.isBlank() || storeId.length() > 40
                || productIds == null || productIds.length == 0 || productIds.length > 100) {
            return error("픽업 매장과 상품을 다시 선택해 주세요.");
        }

        Set<String> remaining = new HashSet<>();
        for (String id : productIds) {
            if (id == null || id.isBlank() || id.length() > 100 || !remaining.add(id)) {
                return error("선택한 픽업 상품을 확인해 주세요.");
            }
        }
        List<CartBean> selectedItems = new ArrayList<>();
        long subtotal = 0;
        for (CartBean item : new CartDAO().getPickupCartList((String) member)) {
            if (!storeId.equals(item.getStoreId()) || !remaining.remove(item.getProductId())) {
                continue;
            }
            if (item.getQuantity() < 1 || item.getQuantity() > item.getStockQuantity()) {
                return error("매장 재고가 변경됐어요. 장바구니에서 수량을 확인해 주세요.");
            }
            selectedItems.add(item);
            subtotal += item.getItemTotal();
        }

        if (!remaining.isEmpty()) {
            return error("픽업 장바구니가 변경됐어요. 상품을 다시 선택해 주세요.");
        }

        long amount = subtotal + 200;
        if (subtotal <= 0 || amount > Integer.MAX_VALUE
                || !Long.toString(amount).equals(request.getParameter("expectedAmount"))) {
            return error("픽업 결제 금액이 변경됐어요. 주문서를 새로고침해 주세요.");
        }

     // ===== 검증된 매장픽업 주문과 주문상품을 결제 대기 상태로 저장 =====
        String checkoutToken = request.getParameter("checkoutToken");
        if (checkoutToken == null
                || !checkoutToken.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return error("주문서를 새로고침한 뒤 다시 시도해 주세요.");
        }

        String orderNo = UUID.randomUUID().toString();
        OrderBean order = new OrderBean();
        order.setOrderNo(orderNo);
        order.setCheckoutToken(checkoutToken);
        order.setMemberId((String) member);
        order.setRecipient(recipient.trim());
        order.setPhone(phone.trim());
        order.setSubtotalAmount(Math.toIntExact(subtotal));
        order.setShippingFee(0);
        order.setTotalAmount(Math.toIntExact(amount));
        order.setItems(selectedItems);

     // ===== 선택한 픽업 결제수단 확인 후 주문 저장 =====
        String method = request.getParameter("paymentMethod");
        if (method == null) method = "TOSSPAY";
        if (!"TOSSPAY".equals(method) && !"KAKAOPAY".equals(method)) {
            return error("결제수단을 다시 선택해 주세요.");
        }

        OrderDAO orderDAO = new OrderDAO();
        long dbOrderId;
        try {
            dbOrderId = orderDAO.createPickupOrder(order, storeId);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                return error("이미 접수된 주문 요청입니다. 주문 내역을 확인해 주세요.");
            }
            throw e;
        }
     // ===== 카카오 픽업 결제 준비 및 승인에 사용할 거래번호 보관 =====
        if ("KAKAOPAY".equals(method)) {
            int totalAmount = Math.toIntExact(amount);
            int quantity = selectedItems.stream().mapToInt(CartBean::getQuantity).sum();
            String baseUrl = "http://localhost:8080" + request.getContextPath()
                    + "/app/payment/kakao";

            try {
                daiso.order.payment.kakaoPayClient client = new daiso.order.payment.kakaoPayClient();
                JsonObject ready = client.ready(
                        orderNo, (String) member, "매장픽업 주문", quantity, totalAmount,
                        baseUrl + "/approve?orderNo=" + orderNo,
                        baseUrl + "/cancel?orderNo=" + orderNo,
                        baseUrl + "/fail?orderNo=" + orderNo);

                String tid = ready.get("tid").getAsString();
                String redirectUrl = ready.get("next_redirect_pc_url").getAsString();
                orderDAO.savePaymentReady(dbOrderId, tid, totalAmount, "KAKAOPAY");

                Map<String, Object> pending = new java.util.HashMap<>();
                pending.put("orderId", dbOrderId);
                pending.put("orderNo", orderNo);
                pending.put("memberId", member);
                pending.put("tid", tid);
                pending.put("totalAmount", totalAmount);
                pending.put("fulfillmentType", "PICKUP");
                session.setAttribute("kakaoPending:" + orderNo, pending);

                JsonObject result = new JsonObject();
                result.addProperty("redirectUrl", redirectUrl);
                return result.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("카카오페이 결제 준비 요청이 중단되었습니다.");
            } catch (java.io.IOException e) {
                request.getServletContext().log("픽업 카카오페이 준비 실패. 주문번호=" + orderNo, e);
                return error("카카오페이 결제창을 열지 못했습니다.");
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("orderId", orderNo);
        result.addProperty("amount", amount);
        return result.toString();
    }
    // 토스 인증 결과를 픽업 주문과 대조하고 승인 완료 처리
    @DaisoGetMapping("/payment/toss/pickup/auth-result")
    @DaisoResponseBody
 
    public String pickupAuthResult(HttpServletRequest request, HttpServletResponse response)
            throws SQLException {
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        Object member = session == null ? null : session.getAttribute("member_id");
        if (!(member instanceof String) || ((String) member).isBlank()) {
            return "로그인 후 픽업 주문 내역을 확인해 주세요.";
        }

        String memberId = (String) member;
        String orderNo = request.getParameter("orderId");
        String paymentKey = request.getParameter("paymentKey");
        int amount;
        try {
            amount = Integer.parseInt(request.getParameter("amount"));
            UUID.fromString(orderNo == null ? "" : orderNo);
        } catch (IllegalArgumentException e) {
            return "픽업 주문번호와 금액을 확인해 주세요.";
        }

        OrderDAO dao = new OrderDAO();
        long orderId;
        try {
            orderId = dao.beginPickupTossApproval(memberId, orderNo, paymentKey, amount);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return e.getMessage();
        }

        try {
            JsonObject payment = new TossPayClient().confirm(paymentKey, orderNo, amount);
            if (!"DONE".equals(payment.get("status").getAsString())
                    || !orderNo.equals(payment.get("orderId").getAsString())
                    || !paymentKey.equals(payment.get("paymentKey").getAsString())
                    || amount != payment.get("totalAmount").getAsInt()) {
                throw new IllegalStateException("토스 승인 결과와 픽업 주문이 일치하지 않습니다.");
            }

            Timestamp approvedAt = Timestamp.from(
                    OffsetDateTime.parse(payment.get("approvedAt").getAsString()).toInstant());
            dao.completePickupTossApproval(memberId, orderId, paymentKey, amount, approvedAt);
            response.sendRedirect(request.getContextPath() + "/app/order/pickup/complete?orderNo="
                    + java.net.URLEncoder.encode(orderNo, java.nio.charset.StandardCharsets.UTF_8));
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            request.getServletContext().log("픽업 승인 요청 중단. 주문번호=" + orderNo, e);
        } catch (Exception e) {
            request.getServletContext().log("픽업 승인 결과 확인 실패. 주문번호=" + orderNo, e);
        }
        return "픽업 결제 결과를 확인 중입니다. 다시 결제하기 전에 주문 상태를 확인해 주세요.";
    }
    // 결제 재요청 없이 매장픽업 완료 화면 표시
    @DaisoGetMapping("/order/pickup/complete")
    public String pickupComplete(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, java.io.IOException {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        Object member = session == null ? null : session.getAttribute("member_id");
        if (!(member instanceof String) || ((String) member).isBlank()) {
            return "redirect:/member/login";
        }

        Map<String, Object> completed = new OrderDAO().findPaidPickupOrder(
                (String) member, request.getParameter("orderNo"));

        if (completed == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "픽업 주문을 찾을 수 없습니다.");
            return null;
        }

        request.setAttribute("pickupCompleted", completed);
        return "order/pickup-complete";
    }
    
    private String error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("message", message);
        return result.toString();
    }
}