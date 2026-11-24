package daiso.order.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.JsonObject;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.order.db.ReturnDAO;
import daiso.order.payment.kakaoPayClient;

@DaisoController
public class AdminReturnController {

    @DaisoGetMapping("/admin/returns")
    public String returnList(
            HttpServletRequest request,
            HttpServletResponse response) {
    	
    	if(!isAdmin(request.getSession(false))) {
    		return forbidden(response);
    	}
        request.setAttribute("adminSection", "returns");
        boolean pendingOnly = "pending".equals(request.getParameter("filter"));
        request.setAttribute("adminReturnsPendingOnly", pendingOnly);
        request.setAttribute(
            "adminReturns", Collections.emptyList()
        );

        try {
            ReturnDAO dao = new ReturnDAO();

            List<Map<String, Object>> returns =
                dao.getReturnList(pendingOnly);

            // DB 상태값을 화면 표시용 한글로 변환
            for (Map<String, Object> item : returns) {
                item.put(
                    "statusLabel",
                    statusLabel((String) item.get("status"))
                );
            }

            request.setAttribute("adminReturns", returns);
            HttpSession session = request.getSession();
            
            if(session.getAttribute("adminReturnCsrf")==null) {
            	session.setAttribute(
            			"adminReturnCsrf",
            			UUID.randomUUID().toString());
            }
            request.setAttribute("adminReturnCsrf", session.getAttribute("adminReturnCsrf"));

        } catch (SQLException e) {
            request.getServletContext().log(
                "관리자 반품 목록 조회 실패", e
            );

            response.setStatus(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            request.setAttribute(
                "adminReturnError",
                "반품 신청 목록을 불러오지 못했습니다."
            );
        }

        return "admin/returns";
    }

    private String statusLabel(String status) {

        if (status == null) {
            return "상태 확인 필요";
        }

        switch (status) {
            case "REQUESTED":
                return "반품접수";
            case "APPROVED":
                return "반품승인";
            case "RECEIVED":
                return "회수완료";
            case "REFUNDING":
                return "환불처리중";
            case "REFUNDED":
                return "환불완료";
            case "REJECTED":
                return "반품반려";
            default:
                return "상태 확인 필요";
        }
    }
    
    @DaisoPostMapping("/admin/returns/approve")
    public String approve(
            HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession session = request.getSession(false);

        if (!isAdmin(session)) {
            return forbidden(response);
        }

        Object savedToken = session.getAttribute("adminReturnCsrf");

        if (!(savedToken instanceof String)
                || !savedToken.equals(request.getParameter("csrfToken"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute(
                "adminReturnError",
                "반품 목록을 새로 열고 다시 시도해 주세요."
            );
            return "admin/returns";
        }

        long returnId;

        try {
            returnId = Long.parseLong(request.getParameter("returnId"));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute(
                "adminReturnError",
                "반품 신청 번호를 확인해 주세요."
            );
            return "admin/returns";
        }

        // 승인 대상과 금액은 DB에서 가져오고, 중복 요청을 차단
        kakaoPayClient client;
        ReturnDAO dao;
        Map<String, Object> target;

        try {
            client = new kakaoPayClient();
            dao = new ReturnDAO();
            target = dao.beginRefund(returnId);
        } catch (IllegalArgumentException e) {
            request.setAttribute("adminReturnError", e.getMessage());
            return "admin/returns";
        } catch (SQLException | IllegalStateException e) {
            request.getServletContext().log("반품 환불 시작 실패", e);
            request.setAttribute(
                "adminReturnError",
                "환불을 시작하지 못했습니다. 주문 상태와 서버 설정을 확인해 주세요."
            );
            return "admin/returns";
        }

        String tid = (String) target.get("tid");
        int amount = ((Number) target.get("amount")).intValue();

        try {
            JsonObject result = client.cancelPayment(tid, amount);

            // 해당 결제가 전액 취소됐는지 확인
            boolean fullyCanceled =
                kakaoPayClient.CID.equals(result.get("cid").getAsString())
                && tid.equals(result.get("tid").getAsString())
                && target.get("orderNo").equals(
                    result.get("partner_order_id").getAsString())
                && target.get("memberId").equals(
                    result.get("partner_user_id").getAsString())
                && "CANCEL_PAYMENT".equals(result.get("status").getAsString())
                && result.getAsJsonObject("amount")
                    .get("total").getAsInt() == amount
                && result.getAsJsonObject("approved_cancel_amount")
                    .get("total").getAsInt() == amount
                && result.getAsJsonObject("canceled_amount")
                    .get("total").getAsInt() == amount;

            if (!fullyCanceled) {
                request.setAttribute(
                    "adminReturnError",
                    "전액 환불을 확인하지 못했습니다. 카카오페이 결제 상태를 확인해 주세요."
                );
                return "admin/returns";
            }

            Timestamp refundedAt = Timestamp.valueOf(
                LocalDateTime.parse(
                    result.get("canceled_at").getAsString(),
                    DateTimeFormatter.ISO_DATE_TIME
                )
            );

            dao.completeRefund(returnId, tid, amount, refundedAt);

            return "redirect:/admin/returns";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | SQLException | RuntimeException e) {
            request.getServletContext().log("반품 환불 결과 확인 필요", e);
        }

        request.setAttribute(
            "adminReturnError",
            "환불 결과 확인이 필요합니다. 재요청 전에 카카오페이 결제 상태를 확인해 주세요."
        );
        return "admin/returns";
    }

    private boolean isAdmin(HttpSession session) {
        return session != null
            && session.getAttribute("member_id") != null
            && "ADMIN".equals(session.getAttribute("role"));
    }

    private String forbidden(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return "redirect:/member/login";
    }
    
    @DaisoPostMapping("/admin/returns/reject")
    public String reject(
            HttpServletRequest request,
            HttpServletResponse response) {

        HttpSession session = request.getSession(false);

        if (!isAdmin(session)) {
            return forbidden(response);
        }

        Object savedToken = session.getAttribute("adminReturnCsrf");

        if (!(savedToken instanceof String)
                || !savedToken.equals(request.getParameter("csrfToken"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute(
                "adminReturnError", "반품 목록을 새로 열어 주세요."
            );
            return "admin/returns";
        }

        long returnId;

        try {
            returnId = Long.parseLong(request.getParameter("returnId"));
            if (returnId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute(
                "adminReturnError", "반품 신청 번호를 확인해 주세요."
            );
            return "admin/returns";
        }

        String note = request.getParameter("adminNote");
        note = note == null ? "" : note.trim();

        if (note.isBlank() || note.length() > 500) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute(
                "adminReturnError", "반려 사유를 500자 이내로 입력해 주세요."
            );
            return "admin/returns";
        }

        try {
            if (new ReturnDAO().rejectReturn(returnId, note)) {
                return "redirect:/admin/returns";
            }

            response.setStatus(HttpServletResponse.SC_CONFLICT);
            request.setAttribute(
                "adminReturnError", "이미 처리됐거나 반려할 수 없는 신청입니다."
            );

        } catch (SQLException e) {
            request.getServletContext().log("반품 반려 저장 실패", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute(
                "adminReturnError", "반려 처리 중 오류가 발생했습니다."
            );
        }

        return "admin/returns";
    }
}
