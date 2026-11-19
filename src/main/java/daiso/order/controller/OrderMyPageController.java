package daiso.order.controller;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.member.controller.MemberProfileInfo;
import daiso.member.controller.MemberService;
import daiso.order.db.OrderDAO;

@DaisoController
public class OrderMyPageController {
    private final MemberService memberService = new MemberService();

    @DaisoGetMapping("/order/mypage")
    public String mypage(HttpServletRequest request) throws SQLException {
        HttpSession session = request.getSession(false);
        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "redirect:/member/login";
        }

        OrderDAO dao = new OrderDAO();

        // DB에서 본인의 최근 주문 목록 조회
        List<Map<String, Object>> orders =
            dao.getMyOrders(memberId);

        DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 기존 주문 선택 목록에서 사용하는 표시값 준비
        for (Map<String, Object> order : orders) {
            Timestamp createdAt =
                (Timestamp) order.get("createdAt");

            Timestamp approvedAt =
                (Timestamp) order.get("approvedAt");

            Timestamp displayTime =
                approvedAt == null ? createdAt : approvedAt;

            order.put(
                "paidAt",
                displayTime.toLocalDateTime().format(formatter)
            );
        }

        String orderNo = request.getParameter("orderNo");

        // 별도 선택이 없으면 가장 최근 주문 표시
        if ((orderNo == null || orderNo.isBlank())
                && !orders.isEmpty()) {
            orderNo = (String) orders.get(0).get("orderNo");
        }

        Map<String, Object> selected = null;

        if (orderNo != null && !orderNo.isBlank()) {
            selected = dao.getMyOrderDetail(memberId, orderNo);

            if (selected == null) {
                request.setAttribute(
                    "myOrderError",
                    "현재 회원의 주문을 찾을 수 없습니다."
                );
            } else {
                selected.put(
                    "statusLabel",
                    statusLabel((String) selected.get("status"))
                );
            }
        }

		
        // 현재 목록에 포함된 올해 결제 완료 주문 집계
        String year = Integer.toString(
            LocalDate.now(ZoneId.of("Asia/Seoul")).getYear()
        );

        long purchaseCount = 0;
        long purchaseAmount = 0;

        for (Map<String, Object> order : orders) {
            Timestamp approvedAt =
                (Timestamp) order.get("approvedAt");

            String status = (String) order.get("status");

            boolean paid =
                "PAID".equals(status)
                || "PREPARING".equals(status)
                || "SHIPPED".equals(status)
                || "DELIVERED".equals(status);

            if (paid && approvedAt != null
                    && approvedAt.toLocalDateTime()
                        .getYear() == Integer.parseInt(year)) {

                purchaseCount++;
                purchaseAmount +=
                    ((Number) order.get("total")).longValue();
            }
        }

        request.setAttribute("myPageYear", year);
        request.setAttribute("myPagePurchaseCount", purchaseCount);
        request.setAttribute("myPagePurchaseAmount", purchaseAmount);

        request.setAttribute("myOrders", orders);
        request.setAttribute("myOrder", selected);

        request.setAttribute(
            "memberName", session.getAttribute("name")
        );
        request.setAttribute(
            "memberEmail", session.getAttribute("email")
        );

        request.setAttribute("activeNavigation", "");


        boolean profileView = "profile".equals(request.getParameter("section"));
        if (profileView) {
		    request.setAttribute("myPageSection", "profile");
		    
		    Object savedAddress = session.getAttribute("orderDeliveryAddress");
		    Map<String, String> profileDeliveryAddress;

		    if (savedAddress instanceof Map) {
		        profileDeliveryAddress = (Map<String, String>) savedAddress;
		    } else if (selected != null && selected.get("address") instanceof Map) {
		        profileDeliveryAddress =
		                (Map<String, String>) selected.get("address");
		    } else {
		        profileDeliveryAddress = Collections.emptyMap();
		    }

		    request.setAttribute("profileDeliveryAddress", profileDeliveryAddress);
		    request.setAttribute(
		            "profilePickupStore",
		            session.getAttribute("orderPickupStore")
		    );

		    Object csrfToken = session.getAttribute("orderCsrf");
		    if (!(csrfToken instanceof String)) {
		        csrfToken = java.util.UUID.randomUUID().toString();
		        session.setAttribute("orderCsrf", csrfToken);
		    }
		    request.setAttribute("orderCsrf", csrfToken);

		    try {
		    	request.setAttribute(
		    	        "linkedSocialProviders",
		    	        memberService.findLinkedSocialProviders(memberId)
		    	);
		        MemberProfileInfo profile =
		                memberService.findProfileByMemberId(memberId);

		        if (profile == null) {
		            request.setAttribute(
		                    "profileError",
		                    "회원 정보를 찾을 수 없습니다."
		            );
		        } else {
		            request.setAttribute("profile", profile);
		            Object verifiedMemberId =
		                    session.getAttribute("profileVerificationMemberId");
		            Object expiresAt =
		                    session.getAttribute("profileVerificationExpiresAt");

		            boolean profileEditMode =
		                    memberId.equals(verifiedMemberId)
		                    && expiresAt instanceof Long
		                    && ((Long) expiresAt) >= System.currentTimeMillis();

		            request.setAttribute("profileEditMode", profileEditMode);
		        }
		    } catch (Exception e) {
		        request.getServletContext().log("회원정보 조회 실패", e);
		        request.setAttribute(
		                "profileError",
		                "회원정보를 불러오지 못했습니다."
		        );
		    }
		}
		return "order/mypage-detail";
    }

    private String statusLabel(String status) {
        if (status == null) return "상태 확인 필요";

        switch (status) {
            case "PENDING":
                return "결제대기";
            case "PAID":
                return "결제완료";
            case "PREPARING":
                return "상품준비중";
            case "SHIPPED":
                return "배송중";
            case "DELIVERED":
                return "배송완료";
            case "CANCELLED":
                return "주문취소";
            case "FAILED":
            case "PAYMENT_FAILED":
                return "결제실패";
            default:
                return "상태 확인 필요";
        }
    }
}
