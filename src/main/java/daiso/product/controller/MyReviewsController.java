package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.dao.ProductReviewDAO;
import daiso.product.dao.ReviewExtrasDAO;
import daiso.product.dto.MyReviewItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final ReviewExtrasDAO extras = new ReviewExtrasDAO();

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
        int page;
        try {
            page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
        } catch (IllegalArgumentException error) {
            response.setStatus(400);
            return VIEW;
        }

        try (Connection connection = ProductQueries.connect()) {
            List<MyReviewItem> results = reviews.mine(connection, (String) identity, written, oldest,
                    PAGE_SIZE + 1, (page - 1) * PAGE_SIZE);
            List<MyReviewItem> items = results.subList(0, Math.min(PAGE_SIZE, results.size()));
            request.setAttribute("myReviewItems", items);
            request.setAttribute("myReviewWritten", written);
            if (written && !items.isEmpty() && extras.available(connection)) {
                List<Long> ids = items.stream().map(item -> item.reviewId).collect(Collectors.toList());
                request.setAttribute("myReviewPhotos", extras.photos(connection, ids));
            }
            if (page > 1) request.setAttribute("myReviewPrevious", pageLink(request, written, oldest, page - 1));
            if (results.size() > PAGE_SIZE && page < Integer.MAX_VALUE / PAGE_SIZE) {
                request.setAttribute("myReviewNext", pageLink(request, written, oldest, page + 1));
            }
            connection.commit();
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("마이페이지 리뷰 조회 실패", error);
            response.setStatus(503);
        }
        return VIEW;
    }

    static String returnPath(boolean written) {
        return ROUTE + (written ? "&reviewTab=written" : "");
    }

    private static String pageLink(HttpServletRequest request, boolean written, boolean oldest, int page) {
        return ProductView.url(request.getContextPath(), "/app/order/mypage", Map.of(
                "productActivity", "reviews", "reviewTab", written ? "written" : "",
                "reviewSort", oldest ? "oldest" : "", "reviewPage", Integer.toString(page)));
    }
}
