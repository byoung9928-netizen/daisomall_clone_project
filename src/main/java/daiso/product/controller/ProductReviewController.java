package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.ProductReviewDAO.Sort;
import daiso.product.dto.ReviewExtras.Submission;
import daiso.product.service.ProductReviewService;
import daiso.product.service.ProductReviewService.Result;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

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
        request.setAttribute("reviewFromMyPage", MyReviewsController.RETURN_SOURCE.equals(request.getParameter("from")));
        String productId;
        String orderNo;
        Sort sort;
        int page;
        try {
            productId = productId(request);
            orderNo = orderSource(request);
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
            if (orderNo != null && (member == null || !service.orderReviewStates(member, orderNo).containsKey(productId))) {
                return error(request, response, 403, "리뷰를 작성할 수 있는 결제 완료 주문을 확인해 주세요.");
            }
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
            Collection<Part> parts = List.of();
            String type = request.getContentType();
            if (action != Action.DELETE && type != null && type.startsWith("multipart/form-data")) {
                try {
                    parts = request.getParts();
                } catch (IllegalStateException limit) {
                    return error(request, response, 413, "사진은 한 번에 총 5MB까지 첨부할 수 있습니다.");
                } catch (ServletException malformed) {
                    return error(request, response, 400, "사진 첨부 요청을 읽을 수 없습니다.");
                }
            }
            if (!validToken(request, session, member)) {
                return error(request, response, 403, "보안 확인에 실패했습니다. 리뷰 화면을 다시 열어 주세요.");
            }
            String orderNo = orderSource(request);
            boolean fromMyPage = MyReviewsController.RETURN_SOURCE.equals(request.getParameter("from"));
            request.setAttribute("reviewFromMyPage", fromMyPage);
            String productId = productId(request);
            request.setAttribute("reviewReturnId", productId);
            long id = action == Action.CREATE ? 0 : Long.parseLong(request.getParameter("reviewId"));
            long version = action == Action.CREATE ? 0 : Long.parseLong(request.getParameter("version"));
            if (version < 0 || (action != Action.CREATE && id < 1)) throw new IllegalArgumentException();
            int rating = action == Action.DELETE ? 0 : Integer.parseInt(request.getParameter("rating"));
            String content = request.getParameter("content");
            Submission submission = action == Action.DELETE ? null : submission(request, parts, action);
            Result result;
            switch (action) {
                case CREATE:
                    result = service.create(productId, member, rating, content, submission, orderNo);
                    break;
                case UPDATE:
                    result = service.update(productId, member, id, version, rating, content, submission);
                    break;
                default:
                    result = service.delete(productId, member, id, version);
            }
            switch (result) {
                case SAVED:
                    if (orderNo != null) {
                        return "redirect:/order/mypage?orderNo="
                                + URLEncoder.encode(orderNo, StandardCharsets.UTF_8) + "#order-product-" + idAnchor(productId);
                    }
                    if (fromMyPage) return "redirect:" + MyReviewsController.returnPath(action != Action.DELETE);
                    return "redirect:/products/detail?id="
                            + URLEncoder.encode(productId, StandardCharsets.UTF_8) + "#product-reviews";
                case PURCHASE_REQUIRED:
                    return error(request, response, 403, "취소되지 않은 결제 완료 구매가 있어야 작성할 수 있습니다.");
                case DUPLICATE:
                    return error(request, response, 409, "이미 이 상품에 작성한 리뷰가 있습니다.");
                case EXTRAS_UNAVAILABLE:
                    return error(request, response, 503, "리뷰 답변과 사진을 저장할 수 없습니다. 화면을 다시 열어 주세요.");
                default:
                    return error(request, response, 409, "수정하거나 삭제할 수 없는 리뷰입니다. 화면을 다시 열어 주세요.");
            }
        } catch (NumberFormatException failure) {
            return error(request, response, 400, "별점과 리뷰 정보를 확인해 주세요.");
        } catch (IllegalArgumentException failure) {
            return error(request, response, 400, failure.getMessage() == null
                    ? "상품과 리뷰 입력값을 확인해 주세요." : failure.getMessage());
        } catch (SQLException | NamingException | IOException failure) {
            return unavailable(request, response, failure);
        }
    }

    private static Submission submission(HttpServletRequest request, Collection<Part> parts, Action action)
            throws IOException {
        String[] enabled = request.getParameterValues("reviewExtras");
        if (enabled == null && parts.isEmpty()) return null;
        if (enabled == null || enabled.length != 1 || !"1".equals(enabled[0])) throw new IllegalArgumentException();

        Map<String, Integer> answers = new HashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (!entry.getKey().startsWith("answer_")) continue;
            String[] values = entry.getValue();
            if (answers.size() >= 2 || values.length != 1) throw new IllegalArgumentException();
            if (!values[0].isEmpty()) answers.put(entry.getKey().substring("answer_".length()), Integer.parseInt(values[0]));
        }

        Set<Long> removals = new LinkedHashSet<>();
        String[] requested = request.getParameterValues("removePhoto");
        if (requested != null) {
            if (action != Action.UPDATE || requested.length > Submission.MAX_PHOTOS) throw new IllegalArgumentException();
            for (String value : requested) {
                long id = Long.parseLong(value);
                if (id < 1 || !removals.add(id)) throw new IllegalArgumentException();
            }
        }
        return new Submission(answers, ReviewPhotoUpload.read(parts, Submission.MAX_PHOTOS), List.copyOf(removals));
    }

    private enum Action { CREATE, UPDATE, DELETE }

    private static Sort sort(String key) {
        for (Sort value : Sort.values()) {
            if (value.key.equals(key)) return value;
        }
        throw new IllegalArgumentException();
    }

    private static String orderSource(HttpServletRequest request) {
        if (!"order".equals(request.getParameter("from"))) return null;
        String orderNo = request.getParameter("orderNo");
        if (orderNo == null || !orderNo.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("주문번호를 확인해 주세요.");
        }
        request.setAttribute("reviewOrderNo", orderNo);
        return orderNo;
    }

    private static String idAnchor(String productId) {
        return URLEncoder.encode(productId, StandardCharsets.UTF_8).replace("+", "%20");
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
