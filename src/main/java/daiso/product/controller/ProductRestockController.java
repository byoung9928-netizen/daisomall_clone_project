package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.ProductRestockDAO;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class ProductRestockController {
    private static final String PATH = "/products/restock";
    private static final String NOTICE = "productRestockNotice";
    private static final ProductRestockDAO REQUESTS = new ProductRestockDAO();
    private final ProductDAO products = new ProductDAO();

    static boolean eligible(ProductDTO product) {
        return product.getStockQuantity() == 0 && ProductView.pickupEligible(product);
    }

    static void prepare(HttpServletRequest request, Connection connection, ProductDTO product) {
        if (!eligible(product)) return;
        try {
            request.setAttribute("restockRequestAvailable", REQUESTS.available(connection));
        } catch (SQLException error) {
            request.getServletContext().log("재입고 알림 신청 기능 확인 실패", error);
        }
    }

    @DaisoPostMapping(PATH)
    public String subscribe(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String member = ProductActivityController.member(session);
        if (member == null || !WishlistController.validToken(request, member)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        try {
            request.setCharacterEncoding("UTF-8");
            String id = ProductQueries.text(request, "productId", 100);
            try (Connection connection = ProductQueries.connect()) {
                try {
                    ProductDTO locked = products.findOnlineProduct(connection, id, true);
                    if (locked == null || !eligible(locked) || products.findVisibleById(connection, id) == null) {
                        connection.rollback();
                        response.sendError(HttpServletResponse.SC_CONFLICT, "현재 재입고 알림을 신청할 수 없는 상품입니다.");
                        return null;
                    }
                    if (!REQUESTS.available(connection)) {
                        connection.rollback();
                        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                        return null;
                    }
                    REQUESTS.subscribe(connection, member, id);
                    connection.commit();
                } catch (SQLException error) {
                    connection.rollback();
                    throw error;
                }
            }
            session.setAttribute(NOTICE, "재입고 알림을 신청했습니다.");
            return "redirect:" + PATH;
        } catch (IllegalArgumentException error) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("재입고 알림 신청 실패", error);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        return null;
    }

    @DaisoGetMapping(PATH)
    public String inbox(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String member = ProductActivityController.member(session);
        if (member == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        boolean partial = "1".equals(request.getParameter("partial"));
        request.setAttribute("restockPartial", partial);
        try (Connection connection = ProductQueries.connect()) {
            if (REQUESTS.available(connection)) {
                request.setAttribute("restockEntries", REQUESTS.inbox(connection, member));
            }
            connection.commit();
            if (!partial) {
                synchronized (session) {
                    request.setAttribute("restockNotice", session.getAttribute(NOTICE));
                    session.removeAttribute(NOTICE);
                }
            }
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("재입고 알림 목록 조회 실패", error);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("restockLoadError", true);
        }
        return "product/restock-inbox";
    }
}
