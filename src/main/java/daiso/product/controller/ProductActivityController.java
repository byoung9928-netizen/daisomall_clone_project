package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.product.dao.WishlistDAO;
import daiso.product.dto.ProductActivityItem;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class ProductActivityController {
    private static final String VIEW = "product/activity-items";
    private static final String SESSION_KEY = "product.recentViews";
    private static final int MAX_RECENT = 50;
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PAGE_SIZE = 20;
    private final ProductDAO products = new ProductDAO();
    private final WishlistDAO wishlist = new WishlistDAO();

    @DaisoGetMapping("/products/my-activity")
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String member = member(session);
        if (member == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return VIEW;
        }
        String kind;
        int page;
        boolean summary = "1".equals(request.getParameter("summary"));
        try {
            kind = ProductQueries.text(request, "kind", 12);
            page = summary ? 1 : ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
            if (!List.of("wishlist", "recent", "frequent").contains(kind)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return VIEW;
        }

        int limit = summary ? ("wishlist".equals(kind) ? 3 : 5) : PAGE_SIZE;
        int offset = (page - 1) * limit;
        List<ProductActivityItem> items;
        try (Connection connection = ProductQueries.connect()) {
            switch (kind) {
                case "wishlist":
                    items = new ArrayList<>();
                    for (var entry : wishlist.findPage(connection, member, limit + 1, offset)) {
                        items.add(new ProductActivityItem(entry.getProduct(), 0));
                    }
                    break;
                case "recent":
                    Map<String, LocalDate> recent = snapshot(session, member);
                    request.setAttribute("activityRecentDates", recent);
                    items = products.findRecentlyViewed(connection, List.copyOf(recent.keySet()), limit + 1, offset);
                    break;
                default:
                    items = products.findFrequentlyPurchased(connection, member, limit + 1, offset);
            }
            if (!summary) {
                List<String> ids = items.stream().map(item -> item.product.getProductId())
                        .collect(java.util.stream.Collectors.toList());
                WishlistController.prepare(request, response, connection, ids);
                products.loadActivityRatings(connection, items);
            }
            connection.commit();
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("마이페이지 상품 조회 실패", error);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return VIEW;
        }
        request.setAttribute("activityItems", items.subList(0, Math.min(limit, items.size())));
        request.setAttribute("activityKind", kind);
        request.setAttribute("activitySummary", summary);
        if (!summary && page > 1) request.setAttribute("activityPrevious", pageLink(request, kind, page - 1));
        if (!summary && page < Integer.MAX_VALUE / PAGE_SIZE && items.size() > limit) {
            request.setAttribute("activityNext", pageLink(request, kind, page + 1));
        }
        return VIEW;
    }

    static LocalDate remember(HttpSession session, String member, String productId) {
        if (member == null) return null;
        LocalDate today = LocalDate.now(STORE_ZONE);
        synchronized (session) {
            Map<String, LocalDate> previous = snapshot(session, member);
            Map<String, LocalDate> items = new LinkedHashMap<>();
            items.put(productId, today);
            previous.forEach((id, date) -> {
                if (items.size() < MAX_RECENT) items.putIfAbsent(id, date);
            });
            session.setAttribute(SESSION_KEY, new RecentViews(member, items));
            return today.equals(previous.get(productId)) ? null : today;
        }
    }

    @DaisoPostMapping("/products/my-activity/remove")
    @DaisoResponseBody
    public String remove(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String member = member(session);
        if (member == null) return failure(response, 401);
        try {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (!WishlistController.validToken(request, member)) return failure(response, 403);
            String kind = request.getParameter("kind");
            if (!"recent".equals(kind) && !"wishlist".equals(kind)) return failure(response, 400);
            String[] submitted = request.getParameterValues("productId");
            if (submitted == null || submitted.length == 0 || submitted.length > PAGE_SIZE) return failure(response, 400);
            var ids = new LinkedHashSet<String>();
            for (String id : submitted) {
                if (id.isBlank() || id.length() > 100 || !id.equals(id.strip())
                        || id.codePoints().anyMatch(Character::isISOControl)) return failure(response, 400);
                ids.add(id);
            }
            if ("recent".equals(kind)) {
                synchronized (session) {
                    Map<String, LocalDate> items = new LinkedHashMap<>(snapshot(session, member));
                    ids.forEach(items::remove);
                    session.setAttribute(SESSION_KEY, new RecentViews(member, items));
                }
            } else {
                try (Connection connection = ProductQueries.connect()) {
                    try {
                        wishlist.removeSelected(connection, member, ids);
                        connection.commit();
                    } catch (SQLException error) {
                        connection.rollback();
                        throw error;
                    }
                }
            }
            return "REMOVED";
        } catch (UnsupportedEncodingException | SQLException | NamingException error) {
            request.getServletContext().log("상품 활동 삭제 실패", error);
            return failure(response, 503);
        }
    }

    private static String failure(HttpServletResponse response, int status) {
        response.setStatus(status);
        return "ERROR";
    }

    static Map<String, LocalDate> snapshot(HttpSession session, String member) {
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof RecentViews && member.equals(((RecentViews) value).member)
                ? ((RecentViews) value).items : Map.of();
    }

    static String member(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("member_id");
        return value instanceof String && !((String) value).isBlank() ? (String) value : null;
    }

    private static String pageLink(HttpServletRequest request, String kind, int page) {
        return ProductView.url(request.getContextPath(), "/app/order/mypage",
                Map.of("productActivity", kind, "activityPage", Integer.toString(page)));
    }

    private static final class RecentViews implements java.io.Serializable {
        private static final long serialVersionUID = 2L;
        final String member;
        final Map<String, LocalDate> items;

        RecentViews(String member, Map<String, LocalDate> items) {
            this.member = member;
            this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        }
    }
}
