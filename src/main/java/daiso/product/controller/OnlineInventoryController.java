package daiso.product.controller;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.controller.AdminProductFilter.Stock;
import daiso.product.dao.StoreInventoryDAO.Change;

@DaisoController
public final class OnlineInventoryController {
    public static final String ROUTE = "/app/admin/inventory";
    static final String MAPPING = "/admin/inventory";
    private static final String VIEW = "admin/inventory";
    private static final String SAVED = OnlineInventoryController.class.getName() + ".saved";
    private static final String PREVIEW = OnlineInventoryController.class.getName() + ".preview";
    private static final int PAGE_SIZE = 20;
    private static final Map<OnlineStockState, Stock> STOCK = Map.of(
            OnlineStockState.ALL, Stock.ALL, OnlineStockState.NORMAL, Stock.ONLINE_NORMAL,
            OnlineStockState.LOW, Stock.ONLINE_LOW, OnlineStockState.SOLD_OUT, Stock.ONLINE_EMPTY,
            OnlineStockState.INACTIVE, Stock.ONLINE_INACTIVE);
    private final ProductDAO dao = new ProductDAO();
    private final AdminProductChanges changes = new AdminProductChanges();

    @DaisoGetMapping(MAPPING)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) {
            return problem(request, response, 403, "관리자 권한이 필요합니다.");
        }
        return show(request, response, session);
    }

    @DaisoPostMapping(MAPPING + "/change")
    public String change(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            return problem(request, response, 403, "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.");
        }

        Map<String, String> draft = new LinkedHashMap<>();
        for (String field : List.of("operation", "amount", "reason", "threshold")) {
            String value = request.getParameter(field);
            if (value != null) draft.put(field, value.substring(0, Math.min(value.length(), 1000)));
        }
        request.setAttribute("onlineDraft", draft);
        try {
            String id = ProductQueries.text(request, "edit", 100);
            String action = ProductQueries.text(request, "action", 15);
            if (id.isEmpty()) throw new IllegalArgumentException("상품을 다시 선택해 주세요.");
            if ("revise".equals(action)) return show(request, response, session);
            boolean threshold = "threshold".equals(action);
            boolean save = "save".equals(action);
            if (!threshold && !save && !"preview".equals(action)) throw new IllegalArgumentException("재고 작업을 확인해 주세요.");
            Map<String, String> filters = parameters(ProductQueries.text(request, "q", 120),
                    ProductQueries.text(request, "category", 100), readState(request),
                    ProductQueries.integer(request, "page", 1, 1, 1_000_000), readSort(request, "sort"));
            int expected = requiredNumber(request, "expected", threshold ? 1 : 0);
            int amount = requiredNumber(request, threshold ? "threshold" : "amount", threshold ? 1 : 0);
            Change operation = threshold ? null : Change.valueOf(ProductQueries.text(request, "operation", 12));
            String reason = threshold ? "" : ProductQueries.text(request, "reason", 255);
            if (!threshold && reason.isEmpty()) throw new IllegalArgumentException("변경 사유를 입력해 주세요.");

            try (Connection connection = ProductQueries.connect()) {
                try {
                    ProductDTO before = dao.findOnlineProduct(connection, id, threshold || save);
                    if (before == null) throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
                    int current = threshold ? before.getLowStockThreshold() : before.getStockQuantity();
                    if (current != expected) throw new IllegalArgumentException("다른 작업에서 수량 또는 기준이 변경됐습니다. 다시 확인해 주세요.");
                    int after = threshold ? amount : operation.after(current, amount);
                    if (threshold) {
                        if (current != after) dao.updateOnlineInventory(connection, id, after, true);
                    } else if (save) {
                        String token = ProductQueries.text(request, "confirmation", 36);
                        if (token.isEmpty() || !consumePreview(session, previewKey(id, expected, operation, amount, reason, token))) {
                            throw new IllegalArgumentException("저장 전 변경 내용을 다시 확인해 주세요.");
                        }
                        dao.updateOnlineInventory(connection, id, after, false);
                        ProductDTO updated = dao.findOnlineProduct(connection, id, false);
                        changes.recordStock(connection, session.getAttribute("member_id").toString(), before, updated,
                                operation.name(), reason);
                    } else {
                        String token = UUID.randomUUID().toString();
                        session.setAttribute(PREVIEW, previewKey(id, expected, operation, amount, reason, token));
                        request.setAttribute("onlinePreview", new Preview(current, after, operation, amount, reason, token));
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException error) {
                    try { connection.rollback(); }
                    catch (SQLException rollback) { error.addSuppressed(rollback); }
                    throw error;
                }
            }
            if (save || threshold) {
                return "redirect:" + ProductView.rawUrl("", MAPPING, ProductView.with(filters, "saved",
                        markSaved(session, threshold ? "상품의 재고 부족 기준을 저장했습니다." : "온라인 재고가 수정되었습니다.")));
            }
        } catch (IllegalArgumentException error) {
            response.setStatus(400);
            request.setAttribute("onlineInventoryError", error.getMessage());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("온라인 재고 저장 실패", error);
            response.setStatus(500);
            request.setAttribute("onlineInventoryError", "변경 내용을 저장하지 못했습니다. 설정과 이력을 확인한 뒤 다시 시도해 주세요.");
        }
        return show(request, response, session);
    }

    private static int requiredNumber(HttpServletRequest request, String name, int min) {
        Integer value = ProductQueries.integer(request, name, null, min, Integer.MAX_VALUE);
        if (value == null) throw new IllegalArgumentException("유효한 수량 또는 기준을 입력해 주세요.");
        return value;
    }

    private static boolean consumePreview(HttpSession session, Map<String, String> preview) {
        synchronized (session) {
            if (!preview.equals(session.getAttribute(PREVIEW))) return false;
            session.removeAttribute(PREVIEW);
            return true;
        }
    }

    private static Map<String, String> previewKey(String id, int expected, Change operation, int amount,
            String reason, String token) {
        return Map.of("id", id, "expected", Integer.toString(expected), "operation", operation.name(),
                "amount", Integer.toString(amount), "reason", reason, "token", token);
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 120);
            OnlineStockState state = readState(request);
            int page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            String category = ProductQueries.text(request, "category", 100);
            Map<String, String> categories = dao.findAdminCategories(connection, false);
            if (!category.isEmpty() && !categories.containsKey(category)) throw new IllegalArgumentException();
            String sort = readSort(request, "sort");
            AdminProductFilter filter = AdminProductFilter.forInventory(keyword, category, STOCK.get(state), sort);
            ProductDAO.CatalogPage result = dao.findAdminProducts(connection, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            int pages = Math.max(1, (result.total + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page > pages) {
                page = pages;
                result = dao.findAdminProducts(connection, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            }
            request.setAttribute("onlineInventoryPage",
                    new Page(request.getContextPath(), result, keyword, category, categories, state, page, pages, sort,
                            dao.onlineSummary(connection)));
            request.setAttribute("onlineStockCsrf", AdminProductController.token(session));
            String edit = ProductQueries.text(request, "edit", 100);
            if (!edit.isEmpty()) {
                ProductDTO product = dao.findOnlineProduct(connection, edit, false);
                if (product == null) request.setAttribute("onlineInventoryError", "상품을 찾을 수 없습니다.");
                request.setAttribute("onlineStockProduct", product);
            }
            String trendId = ProductQueries.text(request, "trendId", 100);
            if (!trendId.isEmpty()) {
                int days = ProductQueries.integer(request, "trendDays", 30, 1, 90);
                ProductDTO product = dao.findOnlineProduct(connection, trendId, false);
                if (product != null) {
                    request.setAttribute("stockTrendProduct", product);
                    request.setAttribute("stockTrend", AdminStockTrends.load(connection, trendId, "", days));
                }
            }
            connection.commit();
            request.setAttribute("inventoryBatchNotice", AdminInventoryBatchController.takeNotice(request, session, MAPPING));
            synchronized (session) {
                String receipt = request.getParameter("saved");
                Object value = session.getAttribute(SAVED);
                if (receipt != null && value instanceof Map<?, ?>
                        && receipt.equals(((Map<?, ?>) value).get("receipt"))) {
                    session.removeAttribute(SAVED);
                    response.setHeader("Cache-Control", "no-store");
                    request.setAttribute("onlineInventorySaved", ((Map<?, ?>) value).get("message"));
                }
            }
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "조회 조건을 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("온라인 재고 조회 실패", error);
            boolean pending = error instanceof SQLException && ((SQLException) error).getErrorCode() == 1054;
            return problem(request, response, pending ? 503 : 500,
                    pending ? "온라인 재고 마이그레이션 적용 후 다시 시도해 주세요." : "온라인 재고를 조회할 수 없습니다.");
        }
        return VIEW;
    }

    private static void initialize(HttpServletRequest request) {
        request.setAttribute("onlineInventoryReady", true);
        request.setAttribute("adminSection", "inventory");
    }

    // ===== 온라인 재고 엑셀은 화면과 같은 검색 조건을 해석 =====
    static AdminProductFilter readInventoryFilter(HttpServletRequest request, ProductDAO dao, Connection con)
            throws SQLException {
        String keyword = ProductQueries.text(request, "q", 120);
        String category = ProductQueries.text(request, "category", 100);
        if (!category.isEmpty() && !dao.findAdminCategories(con, false).containsKey(category)) {
            throw new IllegalArgumentException("카테고리를 확인해 주세요.");
        }
        return AdminProductFilter.forInventory(keyword, category, STOCK.get(readState(request)), readSort(request, "sort"));
    }

    private static OnlineStockState readState(HttpServletRequest request) {
        String value = ProductQueries.text(request, "state", 20);
        return value.isEmpty() ? OnlineStockState.ALL : OnlineStockState.valueOf(value);
    }

    private static String readSort(HttpServletRequest request, String name) {
        String value = ProductQueries.text(request, name, 12);
        if (!List.of("", "low", "shortage").contains(value)) throw new IllegalArgumentException("정렬 조건을 확인해 주세요.");
        return value;
    }

    static String markSaved(HttpSession session) {
        return markSaved(session, "온라인 재고가 수정되었습니다.");
    }

    private static String markSaved(HttpSession session, String message) {
        String receipt = UUID.randomUUID().toString();
        synchronized (session) {
            session.setAttribute(SAVED, Map.of("receipt", receipt, "message", message));
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
            OnlineStockState state = OnlineStockState.valueOf(ProductQueries.text(request, "inventoryState", 20));
            int page = ProductQueries.integer(request, "inventoryPage", 1, 1, 1_000_000);
            return parameters(keyword, category, state, page, readSort(request, "inventorySort"));
        } catch (IllegalArgumentException error) {
            return Map.of();
        }
    }

    private static Map<String, String> parameters(String keyword, String category, OnlineStockState state, int page, String sort) {
        return Map.of("q", keyword, "category", category, "state", state.name(),
                "page", Integer.toString(page), "sort", sort);
    }

    public static String operationLabel(Change operation) {
        return operation == Change.DAMAGE ? "파손·손실" : operation.label;
    }

    public static final class Preview {
        public final int before;
        public final int after;
        public final Change operation;
        public final int amount;
        public final String reason;
        public final String token;

        private Preview(int before, int after, Change operation, int amount, String reason, String token) {
            this.before = before;
            this.after = after;
            this.operation = operation;
            this.amount = amount;
            this.reason = reason;
            this.token = token;
        }
    }

    public static final class Page {
        public final List<ProductDTO> products;
        public final int total;
        public final String keyword;
        public final String category;
        public final Map<String, String> categories;
        public final OnlineStockState state;
        public final Map<OnlineStockState, Long> summary;
        public final Map<String, String> filters;
        public final String resetEditor;
        public final int page;
        public final int pages;
        public final String sort;
        public final int first;
        public final int last;
        public final InventoryPagination pagination;

        private Page(String context, ProductDAO.CatalogPage result, String keyword, String category,
                Map<String, String> categories, OnlineStockState state, int page, int pages, String sort,
                Map<OnlineStockState, Long> summary) {
            products = List.copyOf(result.products);
            total = result.total;
            this.keyword = keyword;
            this.category = category;
            this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
            this.state = state;
            this.page = page;
            this.pages = pages;
            this.sort = sort;
            this.summary = summary;
            filters = parameters(keyword, category, state, page, sort);
            resetEditor = ProductView.url(context, ROUTE, filters);
            first = products.isEmpty() ? 0 : (page - 1) * PAGE_SIZE + 1;
            last = products.isEmpty() ? 0 : first + products.size() - 1;
            pagination = new InventoryPagination(page, pages, target -> pageUrl(context, target));
        }

        private String pageUrl(String context, int target) {
            return ProductView.url(context, ROUTE, parameters(keyword, category, state, target, sort));
        }

        public String editUrl(String context, String productId) {
            return ProductView.url(context, ROUTE, ProductView.with(filters, "edit", productId));
        }

        public String summaryUrl(String context, OnlineStockState scope) {
            return ProductView.url(context, ROUTE, parameters("", "", scope, 1, sort));
        }
    }
}
