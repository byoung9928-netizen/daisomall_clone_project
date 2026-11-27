package daiso.member.controller;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;
import daiso.support.db.SupportInquiryDAO;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.product.controller.GeminiStockQuestionClient;

@DaisoController
public class AdminChatController {
    private static final Gson JSON = new Gson();
    private final AdminDashboardDAO dao = new AdminDashboardDAO();
    private final SupportInquiryDAO inquiryDao = new SupportInquiryDAO();

    @DaisoPostMapping("/admin/chat")
    @DaisoResponseBody
    public String chat(HttpServletRequest request, HttpServletResponse response)
            throws UnsupportedEncodingException {
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("member_id") == null
                || !"ADMIN".equals(session.getAttribute("role"))
                || !"admin-chat".equals(request.getHeader("X-Daiso-Request"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "";
        }

        String question = Objects.toString(request.getParameter("question"), "").strip();
        if (question.isEmpty() || question.length() > 200) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "";
        }

        boolean returns = question.contains("반품");
        boolean soldOut = question.contains("품절");
        // replaceAll("\\s+", "") -> 공백을 지워 "발송 대기" 와 "발송대기" 둘다 알아보게함.
        String inquiryQuestion = question.replaceAll("\\s+", "");
        
        boolean awaitingShipment = inquiryQuestion.contains("발송대기");
        boolean pendingInquiry = inquiryQuestion.contains("미답변")
                || inquiryQuestion.contains("답변대기문의")
                || inquiryQuestion.contains("답변안한문의");

        int matched = (returns ? 1 : 0)
                + (soldOut ? 1 : 0)
                + (awaitingShipment ? 1 : 0)
                + (pendingInquiry ? 1 : 0);

        if (matched == 0) {
            return answerGeneralQuestion(question, request);
        }

        if (matched > 1) {
            return JSON.toJson(new Reply(
                    "관리 항목은 하나씩 물어봐 주세요.",
                    null, null));
        }

        String context = request.getContextPath();
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.KOREA);
        try {
        	if (pendingInquiry) {
        	    long count = inquiryDao.countPending();

        	    return JSON.toJson(new Reply(
        	            "미답변 1:1 문의는 " + number.format(count) + "건입니다.",
        	            context + "/app/admin/inquiries",
        	            "1:1 문의 관리 열기"));
        	}
        	if (awaitingShipment) {
        	    long count = dao.countAwaitingShipment();

        	    return JSON.toJson(new Reply("발송 대기 주문은 " + number.format(count) + "건입니다.",
        	            context + "/app/admin/orders?filter=awaiting-shipment",
        	            "발송 대기 주문 보기"));
        	            
        	}
            if (returns) {
                long count = dao.countPendingReturns();
                return JSON.toJson(new Reply("반품 승인 대기는 " + number.format(count) + "건입니다.",
                        context + "/app/admin/returns?filter=pending", "승인 대기 요청 보기"));
            }

            long count = dao.countOnlineSoldOut();
            return JSON.toJson(new Reply("온라인 품절 상품은 " + number.format(count) + "종입니다.",
                    context + "/app/admin/inventory?state=SOLD_OUT", "품절 상품 보기"));
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리 도우미 집계 조회 실패", error);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "";
        }
    }
    
    private String answerGeneralQuestion(String question, HttpServletRequest request) {
        try {
            String answer = new GeminiStockQuestionClient().answerGeneral(question);

            if (!answer.isBlank()) {
                return JSON.toJson(new Reply(answer, null, null));
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            request.getServletContext().log("관리 도우미 일반 답변 생성 실패", error);
        }

        return JSON.toJson(new Reply(
                "일반 질문에 답하지 못했어요. 잠시 후 다시 시도해 주세요.",
                null, null));
    }

    private static final class Reply {
        private final String message;
        private final String href;
        private final String label;

        private Reply(String message, String href, String label) {
            this.message = message;
            this.href = href;
            this.label = label;
        }
    }
}
