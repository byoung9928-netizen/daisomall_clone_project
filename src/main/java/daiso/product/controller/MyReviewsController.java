package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.dao.ProductReviewDAO;
import daiso.product.dto.MyReviewItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class MyReviewsController {
    static final String RETURN_SOURCE = "my-reviews";
    private static final String ROUTE = "/order/mypage?productActivity=reviews";
    private static final String VIEW = "product/my-review-items";
    private static final int PAGE_SIZE = 15;
    private final ProductReviewDAO reviews = new ProductReviewDAO();

    @DaisoGetMapping("/products/reviews/mine")
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        Object identity = session == null ? null : session.getAttribute("member_id");
        if (!(identity instanceof String) || ((String) identity).isBlank()) {
            response.setStatus(401);
            return VIEW;
        }
        boolean written = "written".equals(request.getParameter("tab"));
        boolean oldest = "oldest".equals(request.getParameter("sort"));
        String rawType = request.getParameter("type");
        String type = rawType == null ? "" : rawType;
        int requestedPage;
        try {
            if (!List.of("", "DELIVERY", "PICKUP").contains(type)) throw new IllegalArgumentException();
            requestedPage = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
        } catch (IllegalArgumentException error) {
            response.setStatus(400);
            return VIEW;
        }

        try (Connection connection = ProductQueries.connect()) {
            String member = (String) identity;
            Map<String, Integer> available = reviews.mineCounts(connection, member, false);
            Map<String, Integer> myWritten = reviews.mineCounts(connection, member, true);
            int availableTotal = total(available);
            int writtenTotal = total(myWritten);
            Map<String, Integer> activeCounts = written ? myWritten : available;
            int activeTotal = written ? writtenTotal : availableTotal;
            int filtered = type.isEmpty() ? activeTotal : activeCounts.getOrDefault(type, 0);
            int lastPage = Math.max(1, (filtered - 1) / PAGE_SIZE + 1);
            int page = Math.min(requestedPage, lastPage);
            List<MyReviewItem> items = reviews.mine(connection, member, written, type, oldest,
                    PAGE_SIZE, (page - 1) * PAGE_SIZE);
            request.setAttribute("myReviewItems", items);
            request.setAttribute("myReviewWritten", written);
            request.setAttribute("myReviewOldest", oldest);
            request.setAttribute("myReviewType", type);
            request.setAttribute("myReviewPage", page);
            request.setAttribute("myReviewAvailableTotal", availableTotal);
            request.setAttribute("myReviewWrittenTotal", writtenTotal);
            request.setAttribute("myReviewActiveTotal", activeTotal);
            request.setAttribute("myReviewTypeCounts", activeCounts);
            if (page > 1) request.setAttribute("myReviewPrevious", pageLink(request, written, oldest, type, page - 1));
            if (page < lastPage) request.setAttribute("myReviewNext", pageLink(request, written, oldest, type, page + 1));
            connection.commit();
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("마이페이지 리뷰 조회 실패", error);
            response.setStatus(503);
        }
        return VIEW;
    }

    private static int total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    static String returnPath(boolean written) {
        return ROUTE + (written ? "&reviewTab=written" : "");
    }

    private static String pageLink(HttpServletRequest request, boolean written, boolean oldest, String type, int page) {
        return ProductView.url(request.getContextPath(), "/app/order/mypage", Map.of(
                "productActivity", "reviews", "reviewTab", written ? "written" : "",
                "reviewSort", oldest ? "oldest" : "", "reviewType", type, "reviewPage", Integer.toString(page)));
    }
}
