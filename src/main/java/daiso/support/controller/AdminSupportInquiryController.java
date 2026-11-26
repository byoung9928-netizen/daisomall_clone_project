package daiso.support.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.support.db.SupportInquiryDAO;

@DaisoController
public class AdminSupportInquiryController {
    private static final String CSRF_KEY = "adminInquiryCsrf";
    private final SupportInquiryDAO dao = new SupportInquiryDAO();

    @DaisoGetMapping("/admin/inquiries")
    public String inquiries(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = adminSession(request);
        if (session == null) {
            response.sendError(403);
            return null;
        }

        response.setHeader("Cache-Control", "private, no-store");
        request.setAttribute("adminSection", "inquiries");

        Object token = session.getAttribute(CSRF_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(CSRF_KEY, token);
        }
        request.setAttribute(CSRF_KEY, token);

        try {
            request.setAttribute("adminInquiries", dao.findForAdmin());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 문의 목록 조회 실패", error);
            response.setStatus(503);
            request.setAttribute("adminInquiryError", "문의 목록을 불러오지 못했습니다.");
        }

        return "admin/inquiries";
    }

    @DaisoPostMapping("/admin/inquiries/answer")
    public String answer(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = adminSession(request);
        if (session == null) {
            response.sendError(403);
            return null;
        }

        Object expected = session.getAttribute(CSRF_KEY);
        if (!(expected instanceof String) || !expected.equals(request.getParameter("csrfToken"))) {
            response.sendError(403, "문의 관리 화면을 다시 열어 주세요.");
            return null;
        }

        long inquiryId;
        try {
            inquiryId = Long.parseLong(request.getParameter("inquiryId"));
        } catch (NumberFormatException error) {
            response.sendError(400, "문의 번호를 확인해 주세요.");
            return null;
        }

        String answer = Objects.toString(request.getParameter("answer"), "").strip();
        if (inquiryId < 1 || answer.isEmpty() || answer.length() > 2000) {
            response.sendError(400, "문의 번호와 답변 내용을 확인해 주세요.");
            return null;
        }

        try {
            if (!dao.saveAnswer(inquiryId, answer)) {
                response.sendError(409, "이미 답변했거나 찾을 수 없는 문의입니다.");
                return null;
            }
            return "redirect:/admin/inquiries?answered=1";
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 문의 답변 저장 실패", error);
            response.sendError(503, "답변을 저장하지 못했습니다.");
            return null;
        }
    }

    private static HttpSession adminSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("member_id") == null
                || !"ADMIN".equals(session.getAttribute("role"))) {
            return null;
        }
        return session;
    }
}