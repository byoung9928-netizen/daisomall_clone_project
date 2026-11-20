package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.AdminProductFilter.Stock;
import daiso.product.dao.StoreInventoryDAO.State;

@DaisoController
public final class OnlineInventoryController {
    public static final String ROUTE = "/app/admin/inventory";
    private static final String VIEW = "admin/inventory";
    private static final int PAGE_SIZE = 20;
    private static final Map<State, Stock> STOCK = Map.of(State.ALL, Stock.ALL, State.NORMAL, Stock.NORMAL,
            State.LOW, Stock.LOW, State.SOLD_OUT, Stock.EMPTY);
    private final ProductDAO dao = new ProductDAO();

    @DaisoGetMapping("/admin/inventory")
    public String list(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("onlineInventoryReady", true);
        request.setAttribute("adminSection", "inventory");
        if (AdminProductController.adminSession(request) == null) {
            return problem(request, response, 403, "관리자 권한이 필요합니다.");
        }
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 120);
            String rawState = ProductQueries.text(request, "state", 20);
            State state = rawState.isEmpty() ? State.ALL : State.valueOf(rawState);
            int page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            AdminProductFilter filter = AdminProductFilter.forInventory(keyword, STOCK.get(state));
            ProductDAO.CatalogPage result = dao.findAdminProducts(connection, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            int pages = Math.max(1, (result.total + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page > pages) {
                page = pages;
                result = dao.findAdminProducts(connection, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            }
            request.setAttribute("onlineInventoryPage", new Page(request.getContextPath(), result, keyword, state, page, pages));
            connection.commit();
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "조회 조건을 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("온라인 재고 조회 실패", error);
            return problem(request, response, 500, "온라인 재고를 조회할 수 없습니다.");
        }
        return VIEW;
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("onlineInventoryError", message);
        return VIEW;
    }

    public static final class Page {
        public final List<ProductDTO> products;
        public final int total;
        public final String keyword;
        public final State state;
        public final int page;
        public final int pages;
        public final String previous;
        public final String next;

        private Page(String context, ProductDAO.CatalogPage result, String keyword, State state, int page, int pages) {
            products = List.copyOf(result.products);
            total = result.total;
            this.keyword = keyword;
            this.state = state;
            this.page = page;
            this.pages = pages;
            previous = page > 1 ? pageUrl(context, page - 1) : null;
            next = page < pages ? pageUrl(context, page + 1) : null;
        }

        private String pageUrl(String context, int target) {
            return ProductView.url(context, ROUTE, Map.of("q", keyword, "state", state.name(), "page", Integer.toString(target)));
        }
    }
}
