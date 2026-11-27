package daiso.member.controller;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import daiso.order.db.AdminOrderDAO;
import daiso.order.db.OrderBean;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import java.util.Map;
import daiso.product.controller.InventoryPagination;
import daiso.product.controller.ProductView;

@DaisoController
public final class AdminMemberController {
    private static final String PATH = "/admin/users";
    private static final int MEMBERS_PER_PAGE = 30;
    private static final String VIEW = "admin/users";
    private static final String DETAIL_VIEW = "admin/member-detail";
    private static final String CSRF_KEY = "adminMemberCsrf";
    private static final String NOTICE_KEY = "adminMembersNotice";

    private final MemberService memberService = new MemberService();

    @DaisoGetMapping(PATH)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");

        HttpSession session = adminSession(request);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("adminMembersError", "관리자 계정으로 로그인해 주세요.");
            return VIEW;
        }

        Object notice = session.getAttribute(NOTICE_KEY);
        if (notice != null) {
            request.setAttribute("adminMembersNotice", notice);
            session.removeAttribute(NOTICE_KEY);
        }

        request.setAttribute("adminMemberCsrf", csrfToken(session));

        String query = request.getParameter("query");
        String status = request.getParameter("status");

        request.setAttribute("adminMemberQuery", query == null ? "" : query.trim());
        request.setAttribute(
                "adminMemberStatus",
                status == null || status.trim().isEmpty()
                        ? "ALL"
                        : status.trim().toUpperCase(java.util.Locale.ROOT)
        );

        try {
        	long requestedPage;
        	try {
        	    requestedPage = Long.parseLong(request.getParameter("page"));
        	} catch (NumberFormatException e) {
        	    requestedPage = 1;
        	}

        	long totalMembers = memberService.countAdminMembers(query, status);
        	long totalPages = totalMembers == 0
        	        ? 1
        	        : (totalMembers - 1) / MEMBERS_PER_PAGE + 1;
        	long currentPage = Math.min(Math.max(requestedPage, 1), totalPages);
        	long offset = (currentPage - 1) * MEMBERS_PER_PAGE;

        	List<AdminMemberInfo> members =
        	        memberService.findAdminMembers(query, status, MEMBERS_PER_PAGE, offset);

        	request.setAttribute("adminMembers", members);
        	request.setAttribute("adminMemberTotal", totalMembers);
        	request.setAttribute("adminMemberPage", currentPage);
        	request.setAttribute("adminMemberTotalPages", totalPages);
        	String pageQuery = (String) request.getAttribute("adminMemberQuery");
        	String pageStatus = (String) request.getAttribute("adminMemberStatus");

        	request.setAttribute("adminMemberPagination",
        	        new InventoryPagination(
        	                Math.toIntExact(currentPage),
        	                Math.toIntExact(totalPages),
        	                target -> ProductView.url(request.getContextPath(), "/app" + PATH,
        	                        Map.of("query", pageQuery, "status", pageStatus,
        	                                "page", Integer.toString(target)))));
            return VIEW;
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute(
                    "adminMembersError",
                    "검색어 또는 회원 상태를 확인해 주세요."
            );
            return VIEW;
        } catch (Exception error) {
            request.getServletContext().log("관리자 회원 목록 조회 실패", error);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute(
                    "adminMembersError",
                    "회원 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
            );
            return VIEW;
        }
    }
    
    @DaisoGetMapping(PATH + "/detail")
    public String detail(
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "private, no-store");

        HttpSession session = adminSession(request);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute(
                    "adminMemberDetailError",
                    "관리자 계정으로 로그인해 주세요."
            );
            return DETAIL_VIEW;
        }

        try {
            String memberId = request.getParameter("memberId");
            AdminMemberDetailInfo member =
                    memberService.findAdminMemberDetail(memberId);

            if (member == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                request.setAttribute(
                        "adminMemberDetailError",
                        "회원을 찾을 수 없습니다."
                );
                return DETAIL_VIEW;
            }

            request.setAttribute("adminMemberDetail", member);

            try {
                List<OrderBean> orders =
                        new AdminOrderDAO().getOrderListByMemberId(memberId);
                request.setAttribute("adminMemberOrders", orders);
            } catch (Exception orderError) {
                request.getServletContext().log(
                        "관리자 회원 주문 내역 조회 실패",
                        orderError
                );
                request.setAttribute(
                        "adminMemberOrdersError",
                        "주문 내역을 불러오지 못했습니다."
                );
            }
            
            try {
                request.setAttribute(
                        "adminMemberSocialProviders",
                        memberService.findLinkedSocialProviders(memberId)
                );
            } catch (Exception socialError) {
                request.getServletContext().log(
                        "관리자 회원 SNS 연동 조회 실패",
                        socialError
                );
                request.setAttribute(
                        "adminMemberSocialError",
                        "SNS 연동 정보를 불러오지 못했습니다."
                );
            }
            
            try {
                request.setAttribute(
                        "adminMemberConsent",
                        memberService.findAdminMemberConsent(memberId)
                );
            } catch (Exception consentError) {
                request.getServletContext().log(
                        "관리자 회원 동의 설정 조회 실패",
                        consentError
                );
                request.setAttribute(
                        "adminMemberConsentError",
                        "동의 설정 정보를 불러오지 못했습니다."
                );
            }

            return DETAIL_VIEW;

        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute(
                    "adminMemberDetailError",
                    error.getMessage()
            );
            return DETAIL_VIEW;

        } catch (Exception error) {
            request.getServletContext().log("관리자 회원 상세 조회 실패", error);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute(
                    "adminMemberDetailError",
                    "회원 정보를 불러오지 못했습니다."
            );
            return DETAIL_VIEW;
        }
    }

    @DaisoPostMapping(PATH + "/status")
    public String updateStatus(
            HttpServletRequest request,
            HttpServletResponse response)
            throws UnsupportedEncodingException {

        response.setHeader("Cache-Control", "private, no-store");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        HttpSession session = adminSession(request);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("adminMembersError", "관리자 계정으로 로그인해 주세요.");
            return VIEW;
        }

        String query = request.getParameter("query");
        String filterStatus = request.getParameter("filterStatus");

        if (!validCsrf(request, session)) {
            session.setAttribute(
                    NOTICE_KEY,
                    "보안 확인에 실패했습니다. 목록을 새로고침한 뒤 다시 시도해 주세요."
            );
            return listRedirect(query, filterStatus);
        }

        session.removeAttribute(CSRF_KEY);

        try {
            boolean changed = memberService.updateAdminMemberStatus(
                    String.valueOf(session.getAttribute("member_id")),
                    request.getParameter("memberId"),
                    request.getParameter("newStatus")
            );

            session.setAttribute(
                    NOTICE_KEY,
                    changed
                            ? "회원 상태를 변경했습니다."
                            : "상태가 바뀌지 않았습니다. 관리자 계정이거나 이미 같은 상태일 수 있습니다."
            );
        } catch (IllegalArgumentException error) {
            session.setAttribute(NOTICE_KEY, error.getMessage());
        } catch (Exception error) {
            request.getServletContext().log("관리자 회원 상태 변경 실패", error);
            session.setAttribute(
                    NOTICE_KEY,
                    "회원 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return listRedirect(query, filterStatus);
    }

    private static HttpSession adminSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null
                || session.getAttribute("member_id") == null
                || !"ADMIN".equalsIgnoreCase(
                        String.valueOf(session.getAttribute("role")))) {
            return null;
        }
        return session;
    }

    private static String csrfToken(HttpSession session) {
        Object existing = session.getAttribute(CSRF_KEY);
        if (existing instanceof String) {
            return (String) existing;
        }

        String token = UUID.randomUUID().toString();
        session.setAttribute(CSRF_KEY, token);
        return token;
    }

    private static boolean validCsrf(
            HttpServletRequest request,
            HttpSession session) {

        String[] submitted = request.getParameterValues("csrf");
        Object expectedValue = session.getAttribute(CSRF_KEY);

        if (submitted == null
                || submitted.length != 1
                || !(expectedValue instanceof String)) {
            return false;
        }

        byte[] expected = ((String) expectedValue)
                .getBytes(StandardCharsets.UTF_8);
        byte[] actual = submitted[0].getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, actual);
    }

    private static String listRedirect(String query, String status)
            throws UnsupportedEncodingException {

        String safeQuery = query == null ? "" : query;
        String safeStatus = status == null || status.trim().isEmpty()
                ? "ALL"
                : status.trim();

        return "redirect:" + PATH
                + "?query=" + URLEncoder.encode(safeQuery, "UTF-8")
                + "&status=" + URLEncoder.encode(safeStatus, "UTF-8");
    }
}