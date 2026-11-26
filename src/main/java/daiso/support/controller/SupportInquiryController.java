package daiso.support.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import daiso.order.controller.OrderMyPageController;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.support.db.SupportInquiryDAO;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import daiso.product.controller.ReviewPhotoUpload;

@DaisoController
public class SupportInquiryController {

    private final SupportInquiryDAO dao = new SupportInquiryDAO();

    private static final java.util.Set<String> INQUIRY_TYPES = java.util.Set.of(
            "다이소몰|주문·배송|배송 일정",
            "다이소몰|주문·배송|주문 취소",
            "다이소몰|반품·환불|반품 신청",
            "다이소몰|반품·환불|환불 진행",
            "매장|매장픽업|픽업 준비",
            "매장|매장픽업|상품 수령",
            "매장|매장 이용|재고 문의",
            "매장|매장 이용|매장 정보"
    );
    
    
    @DaisoGetMapping("/support/inquiries")
    public String inquiries(HttpServletRequest request, HttpServletResponse response)
            throws SQLException {
        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");
        if (!(memberId instanceof String)) {
            return "redirect:/member/login";
        }
        
        response.setHeader("Cache-Control", "private, no-store");
        boolean historyView = "GET".equals(request.getMethod())
                && "history".equals(request.getParameter("view"));
        request.setAttribute("inquiryHistoryView", historyView);

        if (historyView) {
            try {
                request.setAttribute("myInquiries", dao.findByMemberId((String) memberId));
            } catch (SQLException | NamingException error) {
                request.getServletContext().log("문의 목록 조회 실패", error);
                response.setStatus(503);
                request.setAttribute("inquiryLoadError", "문의내역을 불러오지 못했어요.");
            }
        } else {
            Object token = session.getAttribute("supportInquiryCsrf");
            if (token == null) {
                token = UUID.randomUUID().toString();
                session.setAttribute("supportInquiryCsrf", token);
            }
            request.setAttribute("supportInquiryCsrf", token);
            try {
                request.setAttribute("inquiryStores", dao.findStores());
            } catch (SQLException | NamingException error) {
                request.getServletContext().log("문의용 매장 목록 조회 실패", error);
                request.setAttribute("inquiryStoresError", "매장 목록을 불러오지 못했어요.");
            }
        }

        String view = new OrderMyPageController().mypage(request);
        request.setAttribute("myPageSection", "support");
        return view;
    }

    @DaisoPostMapping("/support/inquiries")
    public String createInquiry(HttpServletRequest request, HttpServletResponse response)
    		throws IOException, SQLException {
    	request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");
        if (!(memberId instanceof String)) {
        	return "redirect:/member/login";
        }
        
        Part photoPart = null;
        String contentType = request.getContentType();

        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            try {
                photoPart = request.getPart("inquiryPhoto");
            } catch (IOException | ServletException | IllegalStateException error) {
                response.setStatus(400);
                request.setAttribute("inquiryError", "2MB 이하의 JPG 또는 PNG 사진을 선택해 주세요.");
                return inquiries(request, response);
            }
        }

        Object expected = session.getAttribute("supportInquiryCsrf");
        if (!(expected instanceof String) || !expected.equals(request.getParameter("csrfToken"))) {
            response.sendError(403, "문의 화면을 다시 열고 등록해 주세요.");
            return null;
        }

        String title = java.util.Objects.toString(request.getParameter("title"), "").strip();
        String content = java.util.Objects.toString(request.getParameter("content"), "").strip();

        request.setAttribute("draftTitle", title);
        request.setAttribute("draftContent", content);
        
        String channel = request.getParameter("channel");
        String category = request.getParameter("category");
        String subcategory = request.getParameter("subcategory");

        if (!INQUIRY_TYPES.contains(channel + "|" + category + "|" + subcategory)) {
            response.setStatus(400);
            request.setAttribute("inquiryError", "문의채널과 문의유형, 상세유형을 확인해 주세요.");
            return inquiries(request, response);
        }

        if (title.isEmpty() || title.length() > 120 || content.isEmpty() || content.length() > 2000) {
            response.setStatus(400);
            request.setAttribute("inquiryError", "제목은 120자, 내용은 2,000자 이내로 입력해 주세요.");
            return inquiries(request, response);
        }

        String storeId = "매장".equals(channel) ? request.getParameter("storeId") : null;
        String productId = "매장".equals(channel) ? request.getParameter("productId") : null;

        storeId = storeId == null || storeId.isBlank() ? null : storeId.strip();
        productId = productId == null || productId.isBlank() ? null : productId.strip();

        if ((storeId != null && storeId.length() > 40)
                || (productId != null && productId.length() > 100)) {
            response.setStatus(400);
            request.setAttribute("inquiryError", "매장이나 상품을 다시 선택해 주세요.");
            return inquiries(request, response);
        }
        
        byte[] photo = null;
        if (photoPart != null && photoPart.getSubmittedFileName() != null
                && !photoPart.getSubmittedFileName().isBlank()) {
            try {
                photo = ReviewPhotoUpload.normalize(photoPart);
            } catch (IOException | IllegalArgumentException error) {
                response.setStatus(400);
                request.setAttribute("inquiryError", "2MB 이하의 정상적인 JPG 또는 PNG 사진을 선택해 주세요.");
                return inquiries(request, response);
            }
        }

        try {
        	dao.insert((String) memberId, title, content, channel, category, subcategory,
        	        storeId, productId, photo);
            return "redirect:/support/inquiries?view=history&created=1";
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("문의 등록 실패", error);
            response.setStatus(503);
            request.setAttribute("inquiryError", "문의를 저장하지 못했어요. 다시 시도해 주세요.");
            return inquiries(request, response);
        }
    }
    @DaisoGetMapping("/support/inquiries/photo")
    public String inquiryPhoto(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setHeader("Cache-Control", "private, no-store");

        HttpSession session = request.getSession(false);
        Object memberId = session == null ? null : session.getAttribute("member_id");
        if (!(memberId instanceof String)) {
            response.sendError(404);
            return null;
        }

        long inquiryId;
        try {
            inquiryId = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException error) {
            response.sendError(404);
            return null;
        }

        try {
            byte[] photo = dao.findPhoto(inquiryId, (String) memberId);
            if (photo == null) {
                response.sendError(404);
                return null;
            }

            response.setContentType("image/jpeg");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(photo.length);
            response.getOutputStream().write(photo);
            response.flushBuffer();
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("문의 사진 조회 실패", error);
            response.sendError(503);
            return null;
        }
    }
}