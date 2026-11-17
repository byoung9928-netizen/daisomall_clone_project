package daiso.member.filter;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import daiso.member.controller.MemberLoginInfo;
import daiso.member.controller.MemberService;

public class RememberMeFilter implements Filter {

    private static final String REMEMBER_COOKIE = "DAISO_REMEMBER";

    private final MemberService memberService = new MemberService();

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        HttpSession session = httpRequest.getSession(false);

        // 이미 로그인한 세션이 없을 때만 자동 로그인을 확인합니다.
        if (session == null || session.getAttribute("member_id") == null) {
            String token = findCookieValue(httpRequest);

            if (token != null) {
                try {
                    MemberLoginInfo member =
                            memberService.findMemberByRememberToken(token);

                    if (member == null) {
                        // 만료되거나 유효하지 않은 토큰은 지웁니다.
                        memberService.deleteRememberToken(token);
                        expireCookie(httpRequest, httpResponse);
                    } else {
                        HttpSession restoredSession =
                                httpRequest.getSession(true);

                        httpRequest.changeSessionId();

                        restoredSession.setAttribute(
                                "member_id", member.getMemberId());
                        restoredSession.setAttribute(
                                "email", member.getEmail());
                        restoredSession.setAttribute(
                                "name", member.getName());
                        restoredSession.setAttribute(
                                "role", member.getRole());
                    }
                } catch (Exception e) {
                    throw new ServletException(
                            "자동 로그인 확인 중 오류가 발생했습니다.", e);
                }
            }
        }

        chain.doFilter(httpRequest, httpResponse);
    }

    private String findCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (REMEMBER_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private void expireCookie(
            HttpServletRequest request,
            HttpServletResponse response) {

        Cookie cookie = new Cookie(REMEMBER_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());

        String contextPath = request.getContextPath();
        cookie.setPath(
                contextPath == null || contextPath.isEmpty()
                        ? "/"
                        : contextPath + "/");

        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}