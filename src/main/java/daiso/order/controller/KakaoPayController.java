package daiso.order.controller;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.google.gson.JsonObject;

import daiso.cart.db.CartBean;
import daiso.cart.db.CartDAO;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.order.db.OrderBean;
import daiso.order.db.OrderDAO;
import daiso.order.delivery.DemoTrackingNumber;
import daiso.order.payment.kakaoPayClient;

@DaisoController
public class KakaoPayController {

    @DaisoPostMapping("/payment/kakao/ready")
    @DaisoResponseBody
    public String ready(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return error("로그인 후 이용해 주세요.");
        }

        Object csrf = session.getAttribute("orderCsrf");

        if (!(csrf instanceof String)
                || !csrf.equals(request.getParameter("csrfToken"))) {
            return error("주문서를 새로고침해 주세요.");
        }

        if (!"true".equals(request.getParameter("consent"))) {
            return error("주문 및 결제에 동의해 주세요.");
        }

        if (!(session.getAttribute("orderDeliveryAddress")
                instanceof Map)) {
            return error("배송지를 등록해 주세요.");
        }

        try {
        	String[] selectedValues =
        		    request.getParameterValues("cartItemId");

        		if (selectedValues == null || selectedValues.length == 0) {
        		    return error("주문할 상품을 선택해 주세요.");
        		}

        		Set<Long> selectedIds = new HashSet<>();

        		try {
        		    for (String value : selectedValues) {
        		        long id = Long.parseLong(value);

        		        if (id <= 0) {
        		            return error("장바구니 번호를 확인해 주세요.");
        		        }

        		        selectedIds.add(id);
        		    }
        		} catch (NumberFormatException e) {
        		    return error("장바구니 번호를 확인해 주세요.");
        		}

        		// 본인의 현재 DB 장바구니 조회
        		CartDAO dao = new CartDAO();
        		List<CartBean> cartItems = dao.getCartList(memberId);

        		// 전달받은 선택 상품만 결제 대상으로 구성
        		List<CartBean> items = new ArrayList<>();

        		for (CartBean item : cartItems) {
        		    if (selectedIds.contains(item.getCartItemId())) {
        		        items.add(item);
        		    }
        		}

        		if (items.size() != selectedIds.size()) {
        		    return error(
        		        "선택한 상품이 변경되었습니다. 장바구니에서 다시 선택해 주세요."
        		    );
        		}

            long subtotal = 0;
            long quantity = 0;

            for (CartBean item : items) {
                if (!item.isPurchasable()) {
                    return error(
                        "상품 판매 상태와 재고를 확인해 주세요."
                    );
                }

                subtotal += item.getItemTotal();
                quantity += item.getQuantity();
            }

            long shipping = subtotal < 30000 ? 3000 : 0;

            int totalAmount =
                Math.toIntExact(subtotal + shipping);

            int totalQuantity = Math.toIntExact(quantity);

            String orderNo = UUID.randomUUID().toString();

            String itemName = items.get(0).getProductName();

            if (items.size() > 1) {
                itemName += " 외 " + (items.size() - 1) + "종";
            }

            String baseUrl =
                "http://localhost:8080"
                + request.getContextPath()
                + "/app/payment/kakao";
            
         // 세션에 저장된 배송지 가져오기
            Map<String, String> address = new HashMap<>(
                (Map<String, String>)
                session.getAttribute("orderDeliveryAddress")
            );

            // DAO에 전달할 주문정보 구성
            OrderBean order = new OrderBean();

            order.setOrderNo(orderNo);
            order.setMemberId(memberId);
            order.setCheckoutToken(orderNo);

            order.setRecipient(address.get("recipient"));
            order.setPhone(address.get("phone"));
            order.setPostcode(address.get("postcode"));
            order.setAddress1(address.get("address1"));
            order.setAddress2(address.get("address2"));

            order.setDeliveryMessage(
                (String) session.getAttribute("orderDeliveryMessage")
            );

            order.setSubtotalAmount(Math.toIntExact(subtotal));
            order.setShippingFee(Math.toIntExact(shipping));
            order.setTotalAmount(totalAmount);
            order.setItems(items);

            // orders·order_items에 결제 대기 상태로 저장
            OrderDAO orderDAO = new OrderDAO();
            long orderId = orderDAO.createOrder(order);      
            
            kakaoPayClient client = new kakaoPayClient();

            JsonObject result = client.ready(
                orderNo,
                memberId,
                itemName,
                totalQuantity,
                totalAmount,
                baseUrl + "/approve?orderNo=" + orderNo,
                baseUrl + "/cancel?orderNo=" + orderNo,
                baseUrl + "/fail?orderNo=" + orderNo
            );
            //카카오 페이 결제번호와 결제 대기정보를 db에 저장
            orderDAO.savePaymentReady(orderId,result.get("tid").getAsString(), totalAmount);

            // 승인 단계에서 다시 사용할 정보를 서버에 보관
            Map<String, Object> pending = new HashMap<>();
            pending.put("orderId", orderId);

            pending.put("orderNo", orderNo);
            pending.put("memberId", memberId);
            pending.put("tid", result.get("tid").getAsString());
            pending.put("totalAmount", totalAmount);
            pending.put("items", items);

            pending.put(
                "deliveryAddress",
                new HashMap<>(
                    (Map<String, String>)
                    session.getAttribute("orderDeliveryAddress")
                )
            );

            pending.put(
                "deliveryMessage",
                session.getAttribute("orderDeliveryMessage")
            );

            session.setAttribute("kakaoPending:" + orderNo, pending);

            JsonObject response = new JsonObject();

            response.addProperty(
                "redirectUrl",
                result.get("next_redirect_pc_url").getAsString()
            );

            return response.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("결제 준비 요청이 중단되었습니다.");

        } catch (Exception e) {
            System.err.println(
                "카카오페이 결제 준비 실패: "
                + e.getClass().getSimpleName()
                +" / "+e.getMessage()
            );

            return error("결제 준비에 실패했습니다.");
        }
    }

    private String error(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("message", message);
        return response.toString();
    }
    @DaisoGetMapping("/payment/kakao/approve")
    public String approve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "redirect:/member/login";
        }

        String orderNo = request.getParameter("orderNo");
        String pgToken = request.getParameter("pg_token");

        if (orderNo == null || orderNo.isBlank()
                || pgToken == null || pgToken.isBlank()) {

            request.setAttribute("paymentMessage",
                "결제 승인 정보를 확인할 수 없습니다.");

            return paymentResult(request);
        }

        Object value =
            session.getAttribute("kakaoPending:" + orderNo);

        if (!(value instanceof Map)) {
            request.setAttribute("paymentMessage",
                "결제 준비 정보를 찾을 수 없습니다.");

            return paymentResult(request);
        }

        Map<String, Object> pending = (Map<String, Object>) value;

        synchronized (pending) {
            if (!memberId.equals(pending.get("memberId"))) {
                request.setAttribute("paymentMessage",
                    "본인의 결제만 처리할 수 있습니다.");

                return paymentResult(request);
            }

            // 새로고침 등으로 같은 승인을 다시 요청하지 않도록 확인
            if (pending.get("status") != null) {
                request.setAttribute("paymentMessage",
                    "APPROVED".equals(pending.get("status"))
                        ? "이미 모의결제 승인이 완료된 건입니다."
                        : "이미 처리 중이거나 결과 확인이 필요한 결제입니다.");

                return paymentResult(request);
            }

            pending.put("status", "APPROVING");
        }

        try {
            String tid = (String) pending.get("tid");
            OrderDAO approvalDAO = new OrderDAO();

            boolean started = approvalDAO.beginPaymentApproval(
                memberId, orderNo, tid
            );

            if (!started) {
                synchronized (pending) {
                    pending.put("status", "CHECK_REQUIRED");
                }

                request.setAttribute(
                    "paymentMessage",
                    "취소되었거나 이미 처리 중인 주문입니다. "
                    + "새로운 승인 요청을 보내지 않았습니다."
                );

                return paymentResult(request);
            }

            kakaoPayClient client = new kakaoPayClient();

            JsonObject result = client.approve(
                tid,
                orderNo,
                memberId,
                pgToken
            );

            // 승인 응답을 보관해 결과 확인에 사용
            pending.put("approvalResult", result.toString());

            boolean matches =
                kakaoPayClient.CID.equals(
                    result.get("cid").getAsString())
                && tid.equals(result.get("tid").getAsString())
                && orderNo.equals(
                    result.get("partner_order_id").getAsString())
                && memberId.equals(
                    result.get("partner_user_id").getAsString())
                && ((Integer) pending.get("totalAmount")).intValue()
                    == result.getAsJsonObject("amount")
                        .get("total").getAsInt();
            //카카오 승인 응답과 주문정보가 일치할 때만 db 반영
            if(matches) {
            	Object savedOrderId = pending.get("orderId");
            	
            	if(!(savedOrderId instanceof Number)) {
            		throw new IllegalStateException(
            				"DB 주문번호가 없습니다."
            				);
            	}
            	long orderId = ((Number) savedOrderId).longValue();
            	int approvedAmount = result.getAsJsonObject("amount").get("total").getAsInt();
            	
            	Timestamp approvedAt=Timestamp.valueOf(
            			LocalDateTime.parse(result.get("approved_at").getAsString()
            					)
            			);
            	OrderDAO orderDAO= new OrderDAO();
            	
            	//결제승인이 확인된 주문에 가상운송장 번호 생성
            	String trackingNumber = DemoTrackingNumber.create();
            	
            	orderDAO.approvePayment(memberId,orderId,tid,approvedAmount,approvedAt,trackingNumber);
            }
            synchronized (pending) {
                pending.put("status",
                    matches ? "APPROVED" : "CHECK_REQUIRED");
            }

            request.setAttribute("paymentMessage",
                matches
                    ? "카카오페이 모의결제 승인이 완료되었습니다."
                    : "승인 응답이 주문 정보와 달라 확인이 필요합니다.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            synchronized (pending) {
                pending.put("status", "CHECK_REQUIRED");
            }

            request.setAttribute("paymentMessage",
                "승인 요청이 중단되었습니다. 결제 결과 확인이 필요합니다.");

        } catch (Exception e) {
            synchronized (pending) {
                pending.put("status", "CHECK_REQUIRED");
            }

            System.err.println(
                "카카오페이 승인 처리 확인 필요: "
                + e.getClass().getSimpleName()
            );

            request.setAttribute("paymentMessage",
                "승인 처리 결과를 확인하지 못했습니다. "
                + "같은 결제를 다시 진행하지 마세요.");
        }

        return paymentResult(request);
    }
    @DaisoGetMapping("/payment/kakao/cancel")
    public String cancel(HttpServletRequest request) {
        return finishPayment(request, "CANCELLED");
    }

    @DaisoGetMapping("/payment/kakao/fail")
    public String fail(HttpServletRequest request) {
        return finishPayment(request, "FAILED");
    }

    private String finishPayment(
            HttpServletRequest request, String status) {

        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "redirect:/member/login";
        }

        String orderNo = request.getParameter("orderNo");

        Object value = orderNo == null
            ? null : session.getAttribute("kakaoPending:" + orderNo);

        if (!(value instanceof Map)) {
            request.setAttribute(
                "paymentMessage",
                "결제 준비 정보를 찾을 수 없습니다."
            );

            return paymentResult(request);
        }

        Map<String, Object> pending = (Map<String, Object>) value;

        synchronized (pending) {
            if (!memberId.equals(pending.get("memberId"))) {
                request.setAttribute(
                    "paymentMessage",
                    "본인의 결제만 처리할 수 있습니다."
                );

            } else if (pending.get("status") != null) {
                // 이미 승인됐거나 처리 중인 상태를 덮어쓰지 않음
                request.setAttribute(
                    "paymentMessage",
                    "이미 처리된 요청입니다. 결제 결과를 확인해 주세요."
                );

            } else {
                pending.put("status", status);

                request.setAttribute(
                    "paymentMessage",
                    "CANCELLED".equals(status)
                        ? "결제를 취소했습니다."
                        : "결제 인증을 완료하지 못했습니다."
                );
            }
        }

        return paymentResult(request);
    }
    // 결과 화면에 표시할 값만 준비합니다. 결제 승인이나 DB 저장을 다시 실행하지 않습니다.
    private String paymentResult(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String orderNo = request.getParameter("orderNo");
        Object memberId = session == null ? null : session.getAttribute("member_id");
        Object stored = session == null || orderNo == null ? null
            : session.getAttribute("kakaoPending:" + orderNo);

        if (stored instanceof Map && memberId instanceof String) {
            Map<String, Object> pending = (Map<String, Object>) stored;
            synchronized (pending) {
                if (memberId.equals(pending.get("memberId"))
                        && "APPROVED".equals(pending.get("status"))) {
                    try {
                        JsonObject approval = com.google.gson.JsonParser.parseString(
                            (String) pending.get("approvalResult")
                        ).getAsJsonObject();
                        List<CartBean> items = (List<CartBean>) pending.get("items");
                        long subtotal = 0;
                        for (CartBean item : items) subtotal += item.getItemTotal();
                        long total = ((Number) pending.get("totalAmount")).longValue();
                        Map<String, Object> completed = new HashMap<>();
                        completed.put("orderNo", pending.get("orderNo"));
                        completed.put("items", items);
                        completed.put("address", pending.get("deliveryAddress"));
                        completed.put("message", pending.get("deliveryMessage"));
                        completed.put("subtotal", subtotal);
                        completed.put("shipping", total - subtotal);
                        completed.put("total", total);
                        completed.put("paymentMethod", "카카오페이");
                        String paidAt = approval.has("approved_at")
                            ? approval.get("approved_at").getAsString() : "";
                        try {
                            paidAt = java.time.LocalDateTime.parse(paidAt).format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            );
                        } catch (java.time.format.DateTimeParseException ignored) {
                            // 원본 승인 시각을 표시합니다.
                        }
                        completed.put("paidAt", paidAt);
                        request.setAttribute("completedOrder", completed);
                    } catch (RuntimeException e) {
                        request.setAttribute("paymentMessage",
                            "모의결제는 승인되었지만 상세 정보를 표시하지 못했습니다. 같은 결제를 다시 진행하지 마세요.");
                    }
                }
            }
        }
        return "order/payment-result";
    }

}