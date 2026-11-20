package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.AdminProductFilter.Stock;
import daiso.product.dao.StoreInventoryDAO.State;

@DaisoController
public final class OnlineInventoryController {
    public static final String ROUTE = "/app/admin/inventory";
    static final String MAPPING = "/admin/inventory";
    private static final String VIEW = "admin/inventory";
    private static final String SAVED = OnlineInventoryController.class.getName() + ".saved";
    private static final int PAGE_SIZE = 20;
    private static final Map<State, Stock> STOCK = Map.of(State.ALL, Stock.ALL, State.NORMAL, Stock.NORMAL,
            State.LOW, Stock.LOW, State.SOLD_OUT, Stock.EMPTY);
    private final ProductDAO dao = new ProductDAO();

    @DaisoGetMapping(MAPPING)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("onlineInventoryReady", true);
        request.setAttribute("adminSection", "inventory");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) {
            return problem(request, response, 403, "관리자 권한이 필요합니다.");
        }
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 120);
            String rawState = ProductQueries.text(request, "state", 20);
            State state = rawState.isEmpty() ? State.ALL : State.valueOf(rawState);
            int page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            String category = ProductQueries.text(request, "category", 100);
            Map<String, String> categories = dao.findAdminCategories(connection, false);
            if (!category.isEmpty() && !categories.containsKey(category)) throw new IllegalArgumentException();
            boolean lowFirst = "low".equals(request.getParameter("sort"));
            AdminProductFilter filter = AdminProductFilter.forInventory(keyword, category, STOCK.get(state), lowFirst);
            ProductDAO.CatalogPage result = dao.findAdminProducts(connection, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            int pages = Math.max(1, (result.total + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page > pages) {
                page = pages;
                result = dao.findAdminProducts(connection, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            }
            request.setAttribute("onlineInventoryPage",
                    new Page(request.getContextPath(), result, keyword, category, categories, state, page, pages, lowFirst));
            connection.commit();
            synchronized (session) {
                String receipt = request.getParameter("saved");
                if (receipt != null && receipt.equals(session.getAttribute(SAVED))) {
                    session.removeAttribute(SAVED);
                    response.setHeader("Cache-Control", "no-store");
                    request.setAttribute("onlineInventorySaved", true);
                }
            }
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "조회 조건을 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("온라인 재고 조회 실패", error);
            return problem(request, response, 500, "온라인 재고를 조회할 수 없습니다.");
        }
        return VIEW;
    }

    static String markSaved(HttpSession session) {
        String receipt = UUID.randomUUID().toString();
        synchronized (session) {
            session.setAttribute(SAVED, receipt);
        }
        return receipt;
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("onlineInventoryError", message);
        return VIEW;
    }

    // Rebuild the destination from whitelisted fields. No caller-supplied URL is accepted.
    static Map<String, String> returnParameters(HttpServletRequest request) {
        if (!"1".equals(request.getParameter("fromInventory"))) return Map.of();
        try {
            String keyword = ProductQueries.text(request, "inventoryQuery", 120);
            String category = ProductQueries.text(request, "inventoryCategory", 100);
            State state = State.valueOf(ProductQueries.text(request, "inventoryState", 20));
            int page = ProductQueries.integer(request, "inventoryPage", 1, 1, 1_000_000);
            boolean lowFirst = "low".equals(request.getParameter("inventorySort"));
            return parameters(keyword, category, state, page, lowFirst);
        } catch (IllegalArgumentException error) {
            return Map.of();
        }
    }

    private static Map<String, String> parameters(String keyword, String category, State state, int page, boolean lowFirst) {
        return Map.of("q", keyword, "category", category, "state", state.name(),
                "page", Integer.toString(page), "sort", lowFirst ? "low" : "");
    }

    public static final class Page {
        public final List<ProductDTO> products;
        public final int total;
        public final String keyword;
        public final String category;
        public final Map<String, String> categories;
        public final State state;
        public final int page;
        public final int pages;
        public final boolean lowFirst;
        public final int first;
        public final int last;
        public final String previous;
        public final String next;

        private Page(String context, ProductDAO.CatalogPage result, String keyword, String category,
                Map<String, String> categories, State state, int page, int pages, boolean lowFirst) {
            products = List.copyOf(result.products);
            total = result.total;
            this.keyword = keyword;
            this.category = category;
            this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
            this.state = state;
            this.page = page;
            this.pages = pages;
            this.lowFirst = lowFirst;
            first = products.isEmpty() ? 0 : (page - 1) * PAGE_SIZE + 1;
            last = products.isEmpty() ? 0 : first + products.size() - 1;
            previous = page > 1 ? pageUrl(context, page - 1) : null;
            next = page < pages ? pageUrl(context, page + 1) : null;
        }

        private String pageUrl(String context, int target) {
            return ProductView.url(context, ROUTE, parameters(keyword, category, state, target, lowFirst));
        }

        public String editUrl(String context, String productId) {
            return ProductView.url(context, "/app/admin/products", Map.of(
                    "edit", productId, "fromInventory", "1", "inventoryQuery", keyword,
                    "inventoryCategory", category, "inventoryState", state.name(),
                    "inventoryPage", Integer.toString(page), "inventorySort", lowFirst ? "low" : ""));
        }
    }
}
