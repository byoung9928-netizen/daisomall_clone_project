package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.ProductReviewDAO.Sort;
import daiso.product.service.ProductReviewService;
import daiso.product.service.ProductReviewService.Result;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class ProductReviewController {
    private static final String ROUTE = "/products/reviews";
    private static final String VIEW = "product/reviews";
    private static final String TOKEN = "productReviewCsrf";
    private static final String TOKEN_OWNER = "productReviewCsrfOwner";
    private final ProductReviewService service = new ProductReviewService();
    private final ProductDAO products = new ProductDAO();

    @DaisoGetMapping(ROUTE)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        String productId;
        Sort sort;
        int page;
        try {
            productId = productId(request);
            String key = request.getParameter("sort");
            sort = key == null ? Sort.NEWEST : sort(key);
            String rawPage = request.getParameter("page");
            page = rawPage == null ? 1 : Integer.parseInt(rawPage);
            if (page < 1) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            return error(request, response, 400, "상품 또는 조회 조건을 확인해 주세요.");
        }
        try {
            ProductDTO product;
            try (Connection connection = ProductQueries.connect()) {
                product = products.findVisibleById(connection, productId);
                connection.commit();
            }
            if (product == null) return error(request, response, 404, "상품을 찾을 수 없습니다.");
            request.setAttribute("reviewProduct", product);
            HttpSession session = request.getSession(false);
            String member = memberId(session);
            request.setAttribute("reviewSignedIn", member != null);
            request.setAttribute("reviewSort", sort);
            request.setAttribute("reviewPage", service.page(product.getProductId(), member, sort, page));
            if (member != null) request.setAttribute("reviewToken", token(session, member));
            return VIEW;
        } catch (SQLException | NamingException failure) {
            return unavailable(request, response, failure);
        }
    }

    @DaisoPostMapping(ROUTE + "/create")
    public String create(HttpServletRequest request, HttpServletResponse response) {
        return change(request, response, Action.CREATE);
    }

    @DaisoPostMapping(ROUTE + "/update")
    public String update(HttpServletRequest request, HttpServletResponse response) {
        return change(request, response, Action.UPDATE);
    }

    @DaisoPostMapping(ROUTE + "/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) {
        return change(request, response, Action.DELETE);
    }

    private String change(HttpServletRequest request, HttpServletResponse response, Action action) {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String member = memberId(session);
        if (member == null) return error(request, response, 401, "로그인이 필요합니다.");
        try {
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (!validToken(request, session, member)) {
                return error(request, response, 403, "보안 확인에 실패했습니다. 리뷰 화면을 다시 열어 주세요.");
            }
            String productId = productId(request);
            request.setAttribute("reviewReturnId", productId);
            long id = action == Action.CREATE ? 0 : Long.parseLong(request.getParameter("reviewId"));
            long version = action == Action.CREATE ? 0 : Long.parseLong(request.getParameter("version"));
            if (version < 0 || (action != Action.CREATE && id < 1)) throw new IllegalArgumentException();
            int rating = action == Action.DELETE ? 0 : Integer.parseInt(request.getParameter("rating"));
            String content = request.getParameter("content");
            Result result;
            switch (action) {
                case CREATE:
                    result = service.create(productId, member, rating, content);
                    break;
                case UPDATE:
                    result = service.update(productId, member, id, version, rating, content);
                    break;
                default:
                    result = service.delete(productId, member, id, version);
            }
            switch (result) {
                case SAVED:
                    return "redirect:/products/detail?id="
                            + URLEncoder.encode(productId, StandardCharsets.UTF_8) + "#product-reviews";
                case PURCHASE_REQUIRED:
                    return error(request, response, 403, "취소되지 않은 결제 완료 구매가 있어야 작성할 수 있습니다.");
                case DUPLICATE:
                    return error(request, response, 409, "이미 이 상품에 작성한 리뷰가 있습니다.");
                default:
                    return error(request, response, 409, "수정하거나 삭제할 수 없는 리뷰입니다. 화면을 다시 열어 주세요.");
            }
        } catch (IllegalArgumentException failure) {
            return error(request, response, 400, "입력값을 확인해 주세요. 별점은 1~5점, 내용은 1~2,000자입니다.");
        } catch (SQLException | NamingException | java.io.UnsupportedEncodingException failure) {
            return unavailable(request, response, failure);
        }
    }

    private enum Action { CREATE, UPDATE, DELETE }

    private static Sort sort(String key) {
        for (Sort value : Sort.values()) {
            if (value.key.equals(key)) return value;
        }
        throw new IllegalArgumentException();
    }

    private static String productId(HttpServletRequest request) {
        String id = request.getParameter("id");
        if (id == null || id.isBlank() || id.codePointCount(0, id.length()) > 100
                || !id.equals(id.strip()) || id.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException();
        }
        return id;
    }

    private static String memberId(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("member_id");
        return value instanceof String && !((String) value).isBlank() ? (String) value : null;
    }

    private static String token(HttpSession session, String member) {
        synchronized (session) {
            Object value = session.getAttribute(TOKEN);
            if (!(value instanceof String) || !member.equals(session.getAttribute(TOKEN_OWNER))) {
                value = UUID.randomUUID().toString();
                session.setAttribute(TOKEN, value);
                session.setAttribute(TOKEN_OWNER, member);
            }
            return (String) value;
        }
    }

    private static boolean validToken(HttpServletRequest request, HttpSession session, String member) {
        String[] values = request.getParameterValues("csrf");
        Object expected = session.getAttribute(TOKEN);
        return expected instanceof String && member.equals(session.getAttribute(TOKEN_OWNER))
                && values != null && values.length == 1 && values[0] != null && values[0].length() == 36
                && MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.UTF_8),
                        values[0].getBytes(StandardCharsets.UTF_8));
    }

    private static String unavailable(HttpServletRequest request, HttpServletResponse response, Exception failure) {
        if (failure instanceof SQLException && ((SQLException) failure).getErrorCode() == 1146) {
            return error(request, response, 503, "리뷰 저장소가 아직 준비되지 않았습니다.");
        }
        request.getServletContext().log("상품 리뷰 처리 실패", failure);
        return error(request, response, 503, "리뷰를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static String error(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("reviewError", message);
        return VIEW;
    }
}
