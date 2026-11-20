package daiso.order.controller;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.order.db.OrderDAO;
import daiso.order.db.ReturnDAO;

@DaisoController
public class ReturnController {

    // 반품 신청 화면
    @DaisoGetMapping("/order/return/form")
    public String form(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "redirect:/member/login";
        }

        String orderNo = request.getParameter("orderNo");

        if (orderNo == null || orderNo.isBlank()
                || orderNo.length() > 100) {
            return "redirect:/order/mypage";
        }

        try {
            // 본인 주문만 조회
            Map<String, Object> order =
                new OrderDAO().getMyOrderDetail(memberId, orderNo);

            if (order == null) {
                request.setAttribute(
                    "returnError",
                    "본인의 주문을 찾을 수 없습니다."
                );

            } else if (!"DELIVERED".equals(order.get("status"))
                    || !"APPROVED".equals(
                        order.get("paymentStatus"))) {

                request.setAttribute(
                    "returnError",
                    "배송완료된 결제승인 주문만 반품 신청할 수 있습니다."
                );

            } else {
                request.setAttribute("returnOrder", order);

                if (session.getAttribute("returnCsrf") == null) {
                    session.setAttribute(
                        "returnCsrf",
                        UUID.randomUUID().toString()
                    );
                }

                request.setAttribute(
                    "returnCsrf",
                    session.getAttribute("returnCsrf")
                );
            }

        } catch (SQLException e) {
            request.getServletContext().log(
                "반품 신청 대상 주문 조회 실패", e
            );

            request.setAttribute(
                "returnError",
                "주문정보를 불러오지 못했습니다."
            );
        }

        return "order/return-form";
    }

    // 반품 신청 저장
    @DaisoPostMapping("/order/return/request")
    @DaisoResponseBody
    public String requestReturn(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        String memberId = session == null
            ? null : (String) session.getAttribute("member_id");

        if (memberId == null || memberId.isBlank()) {
            return "로그인 후 이용해 주세요.";
        }

        Object savedToken = session.getAttribute("returnCsrf");

        if (!(savedToken instanceof String)
                || !savedToken.equals(
                    request.getParameter("csrfToken"))) {
            return "반품 신청 화면을 새로 열어 주세요.";
        }

        String orderNo = request.getParameter("orderNo");
        String reason = request.getParameter("reason");
        String reasonDetail = request.getParameter("reasonDetail");

        Set<String> allowedReasons = Set.of(
            "상품불량", "오배송", "단순변심", "기타"
        );

        if (reason == null || !allowedReasons.contains(reason)) {
            return "반품 사유를 선택해 주세요.";
        }

        try {
            boolean saved = new ReturnDAO().requestReturn(
                memberId, orderNo, reason, reasonDetail
            );

            return saved
                ? "OK"
                : "반품 신청 가능한 본인 주문을 찾을 수 없습니다.";

        } catch (IllegalArgumentException e) {
            return e.getMessage();

        } catch (SQLException e) {
            request.getServletContext().log(
                "반품 신청 저장 실패", e
            );

            return "반품 신청 저장 중 DB 오류가 발생했습니다.";
        }
    }
    
    
}