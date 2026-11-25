package daiso.order.controller;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import daiso.cart.db.CartBean;
import daiso.cart.db.CartDAO;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.order.db.OrderCancelViewDAO;
import daiso.order.db.OrderDAO;
import daiso.order.payment.kakaoPayClient;
import daiso.order.payment.TossPayClient;
@DaisoController
public class OrderCancelPageController {
    @DaisoGetMapping("/order/cancel/form")
    public String form(HttpServletRequest request) throws SQLException {
        HttpSession session=request.getSession(false);
        String memberId=session==null?null:(String)session.getAttribute("member_id");
        if(memberId==null||memberId.isBlank()) 
        	return "redirect:/member/login";
        String orderNo=request.getParameter("orderNo");
        
        if(orderNo==null||orderNo.isBlank()||orderNo.length()>100) 
        	return "redirect:/order/mypage";
        
        Map<String,Object> order=new OrderCancelViewDAO().findOrder(memberId,orderNo);
        
        if(order==null) 
        	request.setAttribute("cancelPageError","본인의 주문을 찾을 수 없습니다.");
        else if(!"PAID".equals(order.get("status"))||!"APPROVED".equals(order.get("paymentStatus"))) {
            request.setAttribute("cancelPageError","현재 취소 신청 가능한 결제완료 주문이 아닙니다.");
        } else request.setAttribute("cancelOrder",order);
        
        if(session.getAttribute("cancelCsrf")==null)session.setAttribute("cancelCsrf",UUID.randomUUID().toString());
        request.setAttribute("cancelCsrf",session.getAttribute("cancelCsrf"));
        prepareMemberSummary(request,session,memberId);
        return "order/cancel-form";
    }
    @DaisoGetMapping("/order/cancel/complete")
    public String complete(HttpServletRequest request) throws SQLException {
        HttpSession session=request.getSession(false);
        String memberId=session==null?null:(String)session.getAttribute("member_id");
        
        if(memberId==null||memberId.isBlank())
        	return "redirect:/member/login";
        String orderNo=request.getParameter("orderNo");
        Map<String,Object> order=orderNo==null||orderNo.isBlank()?null:new OrderCancelViewDAO().findOrder(memberId,orderNo);
        if(order==null||!"CANCELLED".equals(order.get("status"))||!"CANCELLED".equals(order.get("paymentStatus"))) {
            request.setAttribute("cancelCompleteError","취소 완료된 주문을 확인할 수 없습니다.");
        }
        if (order != null
                && "CANCELLED".equals(order.get("status"))
                && "CANCELLED".equals(order.get("paymentStatus"))) {

            String messageKey = "cancelCartMessage:" + orderNo;

            request.setAttribute(
                "cancelCartMessage",
                session.getAttribute(messageKey)
            );

            session.removeAttribute(messageKey);
        }
        prepareMemberSummary(request,session,memberId);
        return "order/cancel-complete";
    }
    private void prepareMemberSummary(HttpServletRequest request,HttpSession session,String memberId) throws SQLException {
        int year=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).getYear();
        Map<String,Object> summary=new OrderCancelViewDAO().findSummary(memberId,year);
        request.setAttribute("memberName",session.getAttribute("name"));
        request.setAttribute("memberEmail",session.getAttribute("email"));
        request.setAttribute("myPageYear",Integer.toString(year));
        request.setAttribute("myPagePurchaseCount",summary.get("count"));
        request.setAttribute("myPagePurchaseAmount",summary.get("amount"));
        request.setAttribute("activeNavigation","");
    }
    @DaisoPostMapping("/order/cancel/request")
    @DaisoResponseBody
    public String requestCancellation(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "로그인 후 이용해 주세요.";
        }

        // 정상적인 취소 화면에서 보낸 요청인지 확인
        Object savedToken = session.getAttribute("cancelCsrf");
        String csrfToken = request.getParameter("csrfToken");

        if (!(savedToken instanceof String)
                || !savedToken.equals(csrfToken)) {
            return "취소 화면을 새로 열고 다시 시도해 주세요.";
        }

        String orderNo = request.getParameter("orderNo");
        String reason = request.getParameter("reason");
        String reasonDetail = request.getParameter("reasonDetail");
        String restoreCartValue = request.getParameter("restoreCart");

        if (orderNo == null || orderNo.isBlank()
                || orderNo.length() > 100) {
            return "주문번호를 확인해 주세요.";
        }

        // 화면에서 제공한 사유만 허용
        boolean validReason =
            "주소를 잘못 입력함".equals(reason)
            || "결제수단 변경을 원함".equals(reason)
            || "재주문을 위해 기존 주문을 취소함".equals(reason)
            || "상품이 필요 없게 됨(단순변심)".equals(reason)
            || "배송이 늦음".equals(reason)
            || "기타".equals(reason);

        if (!validReason) {
            return "취소 사유를 선택해 주세요.";
        }

        if (reasonDetail == null) {
            reasonDetail = "";
        }

        reasonDetail = reasonDetail.trim();

        if (reasonDetail.length() > 500) {
            return "취소 상세 사유는 500자 이내로 입력해 주세요.";
        }

        if ("기타".equals(reason) && reasonDetail.isBlank()) {
            return "기타 취소 사유를 입력해 주세요.";
        }

        if (!"true".equals(restoreCartValue)
                && !"false".equals(restoreCartValue)) {
            return "장바구니 재담기 선택을 확인해 주세요.";
        }

        boolean restoreCart = Boolean.parseBoolean(restoreCartValue);

        try {
            // 본인의 주문만 조회
            Map<String, Object> order =
                new OrderCancelViewDAO().findOrder(memberId, orderNo);

            if (order == null) {
                return "본인의 주문을 찾을 수 없습니다.";
            }

            if (!"PAID".equals(order.get("status"))
                    || !"APPROVED".equals(order.get("paymentStatus"))) {
                return "이미 처리 중이거나 취소 가능한 주문이 아닙니다.";
            }

            
            String paymentMethod = (String)order.get("paymentMethod");
            if(!"KAKAOPAY".equals(paymentMethod) && !"TOSSPAY".equals(paymentMethod)) {
            	return "현재 이 결제수단은 온라인 주문취소를 지원하지 않습니다.";
            }
            return executePaidCancellation(
            	    memberId, orderNo, paymentMethod, reason, restoreCart, session
            	);

           

        } catch (SQLException e) {
            return "주문정보 조회 중 오류가 발생했습니다.";
        }
    }
    private String executePaidCancellation(
            String memberId,
            String orderNo,
            String paymentMethod,
            String reason,
            boolean restoreCart,
            HttpSession session) {

        try {
            // 키 설정 오류가 있으면 DB 변경 전에 중단
            
            boolean toss ="TOSSPAY".equals(paymentMethod);
            if(toss) {
            	String secretKey = System.getenv("TOSS_PAYMENTS_SECRET_KEY");
            	if(secretKey == null || !secretKey.startsWith("test_sk")) {
            		return "토스페이 테스트 시크릿 키를 확인해 주세요.";
            	}
            }
            kakaoPayClient client = toss? null : new kakaoPayClient();
            OrderDAO dao = new OrderDAO();

            // 본인 주문·결제 상태 확인 후 중복 취소 차단
            Map<String, Object> target =
                dao.beginPaidOrderCancellation(memberId, orderNo,paymentMethod);

            long orderId =
                ((Number) target.get("orderId")).longValue();

            long paymentId =
                ((Number) target.get("paymentId")).longValue();

            String tid = (String) target.get("tid");
            int amount = ((Number) target.get("total")).intValue();

            try {
                Timestamp canceledAt;

                // ===== 결제수단별 취소 요청 및 응답 확인 =====
                if (toss) {
                    JsonObject payment = new TossPayClient().cancel(
                            tid, amount, reason, "order-cancel-" + paymentId);
                    canceledAt = verifyTossCancellation(payment, tid, orderNo, amount);
                } else {
                    JsonObject result = client.cancelPayment(tid, amount);

                    boolean matches = kakaoPayClient.CID.equals(result.get("cid").getAsString())
                            && tid.equals(result.get("tid").getAsString())
                            && orderNo.equals(result.get("partner_order_id").getAsString())
                            && memberId.equals(result.get("partner_user_id").getAsString())
                            && "CANCEL_PAYMENT".equals(result.get("status").getAsString())
                            && result.getAsJsonObject("amount").get("total").getAsInt() == amount
                            && result.getAsJsonObject("approved_cancel_amount").get("total").getAsInt() == amount
                            && result.getAsJsonObject("canceled_amount").get("total").getAsInt() == amount
                            && result.getAsJsonObject("cancel_available_amount").get("total").getAsInt() == 0;

                    if (!matches) {
                        return "취소 응답이 주문정보와 일치하지 않습니다. "
                                + "재요청하지 말고 결제 상태를 확인해 주세요.";
                    }

                    canceledAt = Timestamp.valueOf(LocalDateTime.parse(
                            result.get("canceled_at").getAsString(),
                            DateTimeFormatter.ISO_DATE_TIME));
                }

                // ===== 취소 확인 후 주문 상태와 재고를 한 번만 반영 =====
                dao.completePaidOrderCancellation(
                        memberId, orderId, paymentId, tid, amount, canceledAt);

             // 환불 DB 저장이 끝난 뒤에만 장바구니에 다시 담기
                if (restoreCart) {
                    String message = restoreCancelledCart(memberId, orderNo);

                    session.setAttribute(
                        "cancelCartMessage:" + orderNo,
                        message
                    );
                }
                return "OK";

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return "취소 요청이 중단되어 결과 확인이 필요합니다. "
                    + "다시 취소 요청하지 마세요.";

            } catch (IOException e) {
                return "카카오페이 취소 결과 확인이 필요합니다. "
                    + "다시 취소 요청하지 마세요.";

            } catch (SQLException e) {
                session.getServletContext().log("카카오페이 취소 완료 후 DB 반영 실패", e);
                return "카카오페이 취소 결과를 DB에 저장하지 못했습니다. "
                    + "재요청하지 말고 결제 상태를 확인해 주세요.";

            } catch (RuntimeException e) {
                return "취소 응답을 확인하지 못했습니다. "
                    + "재요청하지 말고 결제 상태를 확인해 주세요.";
            }

        } catch (IllegalArgumentException e) {
            return e.getMessage();

        } catch (SQLException e) {
            return "취소 처리 시작 중 DB 오류가 발생했습니다. "
                + "주문 상태를 확인해 주세요.";

        } catch (RuntimeException e) {
            return "결제 취소 설정을 확인해 주세요.";
        }
    }
    
    //토츠페이먼츠 전액취소결과 확인
    private Timestamp verifyTossCancellation(
    		JsonObject payment, String paymentKey, String orderNo, int amount)
    		throws IOException{
    	if(!paymentKey.equals(payment.get("paymentKey").getAsString())
    			|| !orderNo.equals(payment.get("orderId").getAsString())
    			|| amount != payment.get("totalAmount").getAsInt()
    			|| !"CANCELED".equals(payment.get("status").getAsString())
    			|| payment.get("balanceAmount").getAsInt() != 0 ){
    		throw new IOException("토스 취소 결과가 주문정보와 일치하지않습니다.");
    		
    	}
    	if(payment.has("cancels")&& payment.get("cancels").isJsonArray()) {
    		for(JsonElement entry : payment.getAsJsonArray("cancels")) {
    			JsonObject cancel = entry.getAsJsonObject();
    			if("DONE".equals(cancel.get("cancelStatus").getAsString())
    					&& cancel.get("cancelAmount").getAsInt()==amount) {
    						return Timestamp.from(
    								OffsetDateTime.parse(cancel.get("canceledAt").getAsString()).toInstant());
    								
    					}
    			}
    		}
    	throw new IOException("토스 전액 취소 기록을 확인하지 못했습니다.");
    	}
    
    
    private String restoreCancelledCart(
            String memberId, String orderNo) {

        try {
            Map<String, Object> order =
                new OrderCancelViewDAO().findOrder(memberId, orderNo);

            if (order == null
                    || !"CANCELLED".equals(order.get("status"))
                    || !"CANCELLED".equals(order.get("paymentStatus"))) {
                return "장바구니 재담기에 필요한 주문을 확인하지 못했습니다.";
            }

            List<CartBean> items =
                (List<CartBean>) order.get("items");

            if (items == null || items.isEmpty()) {
                return "다시 담을 상품을 찾지 못했습니다.";
            }

            CartDAO cartDAO = new CartDAO();
            boolean pickup = Boolean.TRUE.equals(order.get("pickup"));
            int addedCount = 0;
            int failedCount = 0;

            for (CartBean item : items) {
                try {
                    boolean added = true;
                    if (pickup) {
                        cartDAO.addPickupCart(memberId, (String) order.get("pickupStoreId"),
                            item.getProductId(), item.getQuantity());
                    } else {
                        added = cartDAO.addCart(memberId, item.getProductId(), item.getQuantity());
                    }

                    if (added) {
                        addedCount++;
                    } else {
                        failedCount++;
                    }

                } catch (SQLException | RuntimeException e) {
                    failedCount++;
                }
            }

            if (failedCount > 0) {
                return "결제 취소는 완료되었습니다. "
                    + addedCount + "개 품목을 다시 담았으며, "
                    + failedCount + "개 품목은 재고·판매 상태 또는 "
                    + "저장 오류로 담지 못했습니다.";
            }

            return addedCount + "개 품목을 장바구니에 다시 담았습니다.";

        } catch (SQLException | RuntimeException e) {
            return "결제 취소는 완료되었지만 "
                + "장바구니 재담기는 완료하지 못했습니다.";
        }
    }
}
