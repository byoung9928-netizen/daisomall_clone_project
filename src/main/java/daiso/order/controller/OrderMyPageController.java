package daiso.order.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.google.gson.JsonObject;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.member.controller.MemberProfileInfo;
import daiso.member.controller.MemberService;
import daiso.order.db.AdminOrderDAO;
import daiso.order.db.OrderDAO;
import daiso.order.delivery.DeliveryTrackerClient;
import daiso.order.delivery.DemoTrackingNumber;
import daiso.order.payment.kakaoPayClient;
import daiso.product.service.ProductReviewService;
import daiso.order.db.DeliveryAddressDAO;
import daiso.product.dao.StoreInventoryDAO.Location;

@DaisoController
public class OrderMyPageController {
    private final MemberService memberService = new MemberService();
    private final ProductReviewService reviews = new ProductReviewService();

    @DaisoGetMapping("/order/mypage")
    public String mypage(HttpServletRequest request) throws SQLException {
        HttpSession session = request.getSession(false);
        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "redirect:/member/login";
        }
        if (session.getAttribute("memberConsentCsrf") == null) {
            session.setAttribute(
                    "memberConsentCsrf",
                    UUID.randomUUID().toString()
            );
        }

        request.setAttribute(
                "memberConsentCsrf",
                session.getAttribute("memberConsentCsrf")
        );
        // 주문내역 삭제 요청을 확인하기 위한 토큰
        if (session.getAttribute("myOrderDeleteCsrf") == null) {
            session.setAttribute(
                "myOrderDeleteCsrf",
                UUID.randomUUID().toString()
            );
        }

        request.setAttribute(
            "myOrderDeleteCsrf",
            session.getAttribute("myOrderDeleteCsrf")
        );
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
                selected.put("returnStatusLabel", returnStatusLabel((String)selected.get("returnStatus")));
                if ("PAID".equals(selected.get("status")) && !"profile".equals(request.getParameter("section"))
                        && (request.getParameter("productActivity") == null || request.getParameter("productActivity").isBlank())) {
                    try {
                        request.setAttribute("orderReviewStates", reviews.orderReviewStates(memberId, orderNo));
                    } catch (SQLException | NamingException error) {
                        request.getServletContext().log("주문내역 리뷰 상태 조회 실패", error);
                    }
                }
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
		    try {
		        List<Map<String, String>> addresses =
		                new DeliveryAddressDAO().findByMemberId(memberId);

		        request.setAttribute("profileDeliveryAddresses", addresses);

		        // 세션에 현재 배송지가 없으면 가장 최근 주소를 회원정보 요약에 표시한다.
		        if (!(savedAddress instanceof Map) && !addresses.isEmpty()) {
		            request.setAttribute("profileDeliveryAddress", addresses.get(0));
		        }
		    } catch (SQLException | javax.naming.NamingException e) {
		        request.getServletContext().log("회원 배송지 목록 조회 실패", e);
		        request.setAttribute("profileDeliveryAddressLoadFailed", true);
		    }
		    try {
		        List<Location> stores = PickupStores.findAll();
		        request.setAttribute("profilePickupStores", stores);

		        Object selectedStoreId = session.getAttribute("orderPickupStoreId");
		        for (Location store : stores) {
		            if (store.id.equals(selectedStoreId)) {
		                request.setAttribute("profilePickupStore", Map.of(
		                        "storeId", store.id,
		                        "storeName", store.name,
		                        "city", store.city
		                ));
		                break;
		            }
		        }
		    } catch (SQLException | javax.naming.NamingException e) {
		        request.getServletContext().log("픽업매장 목록 조회 실패", e);
		        request.setAttribute("profilePickupLoadFailed", true);
		    }

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
            try {
                request.setAttribute(
                        "profileConsentSettings",
                        memberService.findAdminMemberConsent(memberId)
                );
            } catch (Exception consentError) {
                request.getServletContext().log(
                        "회원 동의 설정 조회 실패",
                        consentError
                );
                request.setAttribute(
                        "profileConsentError",
                        "동의 설정 정보를 불러오지 못했습니다."
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
            case"REFUNDING":return"환불처리중";
            case"REFUNDED": return"환불완료";
            default:
                return "상태 확인 필요";
        }
    }
    @DaisoPostMapping("/order/profile/consent")
    @DaisoResponseBody
    public String saveMemberConsentSettings(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        String memberId = session == null
                ? null
                : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "로그인 후 이용해 주세요.";
        }

        Object savedToken = session.getAttribute("memberConsentCsrf");
        String submittedToken = request.getParameter("csrfToken");

        if (!(savedToken instanceof String)
                || !savedToken.equals(submittedToken)) {
            return "화면을 새로고침한 뒤 다시 시도해 주세요.";
        }

        String selectedRetention =
                request.getParameter("profileRetention");
        String retentionPeriod;

        if ("withdrawal".equals(selectedRetention)) {
            retentionPeriod = "WITHDRAWAL";
        } else if ("5years".equals(selectedRetention)) {
            retentionPeriod = "5_YEARS";
        } else if ("3years".equals(selectedRetention)) {
            retentionPeriod = "3_YEARS";
        } else {
            return "개인정보 보관 기간을 선택해 주세요.";
        }

        try {
            memberService.saveMemberConsentSettings(
                    memberId,
                    Boolean.parseBoolean(request.getParameter("marketingSms")),
                    Boolean.parseBoolean(request.getParameter("marketingEmail")),
                    Boolean.parseBoolean(request.getParameter("marketingPersonalInfo")),
                    Boolean.parseBoolean(request.getParameter("locationTerms")),
                    retentionPeriod
            );
            return "OK";
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        } catch (Exception error) {
            request.getServletContext().log(
                    "회원 동의 설정 저장 실패",
                    error
            );
            return "동의 설정을 저장하지 못했습니다. 다시 시도해 주세요.";
        }
    }
    @DaisoPostMapping("/order/pending/delete")
    @DaisoResponseBody
    public String deletePendingOrder(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "로그인 후 이용해 주세요.";
        }

        // 다른 곳에서 임의로 보낸 요청인지 검사
        Object savedToken =
            session.getAttribute("myOrderDeleteCsrf");

        String csrfToken = request.getParameter("csrfToken");

        if (!(savedToken instanceof String)
                || !savedToken.equals(csrfToken)) {
            return "화면을 새로고침한 후 다시 시도해 주세요.";
        }

        String orderNo = request.getParameter("orderNo");

        if (orderNo == null || orderNo.isBlank()
                || orderNo.length() > 100) {
            return "주문번호를 확인해 주세요.";
        }

        try {
            OrderDAO dao = new OrderDAO();

            Map<String, Object> order =
                dao.getPendingOrder(memberId, orderNo);

            if (order == null) {
                return "삭제할 결제대기 주문을 찾을 수 없습니다.";
            }

            String tid = (String) order.get("tid");

            if (tid == null || tid.isBlank()) {
                return "결제 번호가 없어 상태 확인이 필요합니다.";
            }

            if (!"PENDING".equals(order.get("paymentStatus"))) {
                return "결제 상태가 변경되었습니다. 새로고침해 주세요.";
            }

            // DB 상태뿐 아니라 카카오페이의 실제 상태도 조회
            JsonObject payment =
                new kakaoPayClient().getPayment(tid);

            boolean matches =
                kakaoPayClient.CID.equals(
                    payment.get("cid").getAsString())
                && tid.equals(
                    payment.get("tid").getAsString())
                && orderNo.equals(
                    payment.get("partner_order_id").getAsString())
                && memberId.equals(
                    payment.get("partner_user_id").getAsString())
                && ((Number) order.get("total")).intValue()
                    == payment.getAsJsonObject("amount")
                        .get("total").getAsInt();

            if (!matches) {
                return "조회한 결제정보가 주문과 일치하지 않습니다.";
            }

            String status = payment.get("status").getAsString();

            if ("SUCCESS_PAYMENT".equals(status)) {

                if (!payment.has("approved_at")
                        || payment.get("approved_at").isJsonNull()) {
                    return "승인 시각을 확인할 수 없어 복구하지 않았습니다.";
                }

                Timestamp approvedAt = Timestamp.valueOf(
                    java.time.LocalDateTime.parse(
                        payment.get("approved_at").getAsString(),
                        DateTimeFormatter.ISO_DATE_TIME
                    )
                );

                long orderId =
                    ((Number) order.get("orderId")).longValue();

                int amount = payment.getAsJsonObject("amount")
                    .get("total").getAsInt();

                // 다른 승인·취소 요청과 겹치지 않도록 먼저 상태 변경
                boolean started = dao.beginPaymentApproval(
                    memberId, orderNo, tid
                );

                if (!started) {
                    return "주문 상태가 변경되었습니다. 새로고침해 주세요.";
                }

                // 이미 받은 승인 결과를 DB에 반영
                // 카카오페이에 승인을 다시 요청하는 것이 아님
                dao.approvePayment(
                    memberId, orderId, tid, amount, approvedAt, DemoTrackingNumber.create()
                );

                return "이미 결제된 주문이라 결제완료로 복구했습니다. "
                    + "화면을 새로고침해 주세요.";
            }

            if ("PART_CANCEL_PAYMENT".equals(status)
                    || "CANCEL_PAYMENT".equals(status)) {
                return "취소 이력이 있는 결제입니다. "
                    + "결제정보 확인이 필요합니다.";
            }

            // 결제 중단 또는 승인 실패가 확인된 경우만 숨김 처리
         // 아직 결제되지 않은 주문만 취소 가능
            boolean canCancel =
                "READY".equals(status)
                || "SEND_TMS".equals(status)
                || "OPEN_PAYMENT".equals(status)
                || "SELECT_METHOD".equals(status)
                || "ARS_WAITING".equals(status)
                || "AUTH_PASSWORD".equals(status)
                || "FAIL_AUTH_PASSWORD".equals(status)
                || "QUIT_PAYMENT".equals(status)
                || "FAIL_PAYMENT".equals(status);

            if (!canCancel) {
                return "취소 가능한 결제대기 상태가 아닙니다.";
            }

            boolean cancelled =
            	    dao.cancelPendingOrder(memberId, orderNo, tid);

            	return cancelled
            	    ? "OK"
            	    : "이미 승인이 시작되었거나 주문 상태가 변경되었습니다. "
            	        + "새로고침해 주세요.";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "결제 상태 조회가 중단되었습니다. 삭제하지 않았습니다.";

        } catch (IOException e) {
            return "카카오페이 상태 조회에 실패했습니다. 삭제하지 않았습니다.";

        } catch (SQLException e) {
            return "주문내역 삭제 처리 중 DB 오류가 발생했습니다.";

        } catch (RuntimeException e) {
            return "결제정보를 확인하지 못했습니다. 삭제하지 않았습니다.";
        }
    }
    @DaisoPostMapping("/order/tracking")
    @DaisoResponseBody
    public String tracking(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "로그인 후 이용해 주세요.";
        }

        // 마이페이지에서 이미 발급한 요청 확인 토큰 사용
        Object savedToken =
            session.getAttribute("myOrderDeleteCsrf");

        if (!(savedToken instanceof String)
                || !savedToken.equals(
                    request.getParameter("csrfToken"))) {
            return "마이페이지를 새로고침한 뒤 다시 시도해 주세요.";
        }

        String orderNo = request.getParameter("orderNo");

        if (orderNo == null || orderNo.isBlank()
                || orderNo.length() > 100) {
            return "주문번호를 확인해 주세요.";
        }

        try {
            OrderDAO dao = new OrderDAO();

            // 회원번호까지 함께 검사하므로 본인 주문만 조회됨
            Map<String, Object> order =
                dao.getMyOrderDetail(memberId, orderNo);

            if (order == null) {
                return "본인의 주문을 찾을 수 없습니다.";
            }

            String status = (String) order.get("status");

            boolean canTrack =
                "PAID".equals(status)
                || "PREPARING".equals(status)
                || "SHIPPED".equals(status)
                || "DELIVERED".equals(status);

            if (!canTrack
                    || !"APPROVED".equals(order.get("paymentStatus"))) {
                return "현재 배송조회가 가능한 주문이 아닙니다.";
            }

            String carrierId = (String) order.get("carrierId");
            String trackingNumber =
                (String) order.get("trackingNumber");

            if (carrierId == null || carrierId.isBlank()
                    || trackingNumber == null
                    || trackingNumber.isBlank()) {
                return "아직 운송장이 배정되지 않았습니다.";
            }

            DeliveryTrackerClient client =
                new DeliveryTrackerClient();

            JsonObject tracking =
                client.track(carrierId, trackingNumber);

            String deliveryCode = tracking
                .getAsJsonObject("lastEvent")
                .getAsJsonObject("status")
                .get("code").getAsString();

            String nextStatus;

            if ("DELIVERED".equals(deliveryCode)) {
                nextStatus = "DELIVERED";

            } else if ("IN_TRANSIT".equals(deliveryCode)
                    || "OUT_FOR_DELIVERY".equals(deliveryCode)) {
                nextStatus = "SHIPPED";

            } else {
                return "아직 배송중·배송완료 상태가 확인되지 않았습니다.";
            }

            // 관리자 배송조회와 같은 DB 갱신 메서드 재사용
            new AdminOrderDAO().updateDeliveryStatus(
                orderNo, carrierId, trackingNumber, nextStatus
            );

            return "OK";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "배송조회 요청이 중단되었습니다.";

        } catch (IOException e) {
            return e.getMessage();

        } catch (SQLException e) {
            request.getServletContext().log(
                "고객 주문 배송조회 실패", e
            );
            return "배송정보 조회·저장 중 DB 오류가 발생했습니다.";

        } catch (RuntimeException e) {
            return "배송조회 설정 또는 응답을 확인해 주세요.";
        }
    }
    private String returnStatusLabel(String status) {
        if (status == null) return "";

        switch (status) {
            case "REQUESTED": return "반품접수";
            case "APPROVED": return "반품승인";
            case "RECEIVED": return "회수완료";
            case "REFUNDING": return "환불처리중";
            case "REFUNDED": return "환불완료";
            case "REJECTED": return "반품반려";
            default: return "반품 상태 확인 필요";
        }
    }
}
