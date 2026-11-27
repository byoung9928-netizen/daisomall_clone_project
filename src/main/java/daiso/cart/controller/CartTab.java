package daiso.cart.controller;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class CartTab {
    private CartTab() {}

    private static String cookieName(String memberId) {
        return "cartTab_" + UUID.nameUUIDFromBytes(memberId.getBytes(StandardCharsets.UTF_8));
    }

    public static void remember(HttpServletRequest request, HttpServletResponse response,
            String memberId, boolean pickup) {
        Cookie cookie = new Cookie(cookieName(memberId), pickup ? "pickup" : "delivery");
        String context = request.getContextPath();
        cookie.setPath(context.isEmpty() ? "/" : context);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(60 * 60 * 24 * 365);
        response.addCookie(cookie);
    }

    static boolean openPickup(HttpServletRequest request, String memberId, boolean fallback) {
        String tab = request.getParameter("tab");
        if ("pickup".equals(tab)) return true;
        if ("delivery".equals(tab)) return false;

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String name = cookieName(memberId);
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    if ("pickup".equals(cookie.getValue())) return true;
                    if ("delivery".equals(cookie.getValue())) return false;
                }
            }
        }
        return fallback;
    }
}
