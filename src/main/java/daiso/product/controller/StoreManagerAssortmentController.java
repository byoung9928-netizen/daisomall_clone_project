package daiso.product.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.order.db.PickupStockTransaction;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Assortment;
import daiso.product.dao.StoreInventoryDAO.AssortmentItem;
import daiso.product.dao.StoreInventoryDAO.InventoryFilter;
import daiso.product.dao.StoreInventoryDAO.ManagerAssortment;
import daiso.product.dao.StoreInventoryDAO.ManagerSort;
import daiso.product.dao.StoreInventoryDAO.State;
import daiso.product.dao.StoreInventoryDAO.Stock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class StoreManagerAssortmentController {
    private static final String MAPPING = "/store-manager/assortment";
    public static final String ROUTE = "/app" + MAPPING;
    public static final int LIMIT = 20;
    private static final String SESSION = StoreManagerAssortmentController.class.getName();
    private static final long PREVIEW_TTL = 600_000;

    static String list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String member = StoreManagerAccess.memberId(session);
        if (member == null) return "redirect:/member/login";

        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, member, false);
            if (manager == null) {
                response.sendError(403, "배정된 매장이 있는 관리자만 이용할 수 있습니다.");
                return null;
            }
            Filters filters = new Filters(request);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            Map<ManagerAssortment, Long> totals = dao.managerAssortmentSummary(connection, filters.scope(manager.storeId, false));
            long total = totals.get(filters.state);
            int pages = Math.toIntExact(Math.max(1, (total + filters.size - 1) / filters.size));
            int page = Math.min(filters.page, pages);
            long offset = (page - 1L) * filters.size;
            List<AssortmentItem> rows = dao.managerAssortment(connection, filters.scope(manager.storeId, false),
                    filters.state, filters.sort, filters.size, offset, false);
            Map<String, String> categories = new ProductDAO().findAdminCategories(connection, false);
            boolean safePickup = PickupStockTransaction.enabled(connection);
            long recovered = dao.countRecoveredUncarried(connection, manager.storeId);
            connection.commit();

            StoreManagerController.showManager(request, manager, "assortment");
            request.setAttribute("storeManagerTitle", "취급 상품 관리");
            request.setAttribute("storeManagerId", manager.storeId);
            request.setAttribute("storeManagerCsrf", AdminProductController.token(session));
            request.setAttribute("assortment", new Page(request.getContextPath(), filters, page, pages, totals,
                    rows, categories, offset, recovered, safePickup, dao.thresholdsEnabled(),
                    notice(session, ProductQueries.text(request, "receipt", 36), member, manager.storeId)));
            return "store-manager/assortment";
        } catch (IllegalArgumentException error) {
            response.sendError(400, "검색 조건을 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 취급 상품 조회 실패", error);
            response.sendError(503, "취급 상품을 조회하지 못했습니다.");
        }
        return null;
    }

    @DaisoGetMapping(MAPPING + "/suggestions")
    public String suggestions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonArray matches = new JsonArray();
        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, StoreManagerAccess.memberId(request), false);
            if (manager == null) return json(response, 403, matches.toString());
            Filters filters = new Filters(request);
            if (!filters.keyword.isEmpty()) {
                StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
                for (AssortmentItem item : dao.managerAssortment(connection, filters.scope(manager.storeId, true),
                        filters.state, filters.sort, 10, 0, true)) {
                    Stock stock = item.stock;
                    AdminProductSuggestions.add(matches, request.getContextPath(), stock.id, stock.name, stock.code,
                            stock.image, item.price, stock.version < 0 ? null : stock.quantity, item.state().label);
                }
            }
            connection.commit();
            return json(response, 200, matches.toString());
        } catch (IllegalArgumentException error) {
            return json(response, 400, "[]");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 취급 상품 자동완성 실패", error);
            return json(response, 503, "[]");
        }
    }

    @DaisoGetMapping(MAPPING + "/csv")
    public String csv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        StringBuilder output = new StringBuilder("\ufeff");
        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, StoreManagerAccess.memberId(request), false);
            if (manager == null) {
                response.sendError(403);
                return null;
            }
            Filters filters = new Filters(request);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            InventoryFilter scope = filters.scope(manager.storeId, false);
            Map<String, String> categories = new ProductDAO().findAdminCategories(connection, false);
            AdminProductController.csvRow(output, "상품 ID", "품번", "상품명", "브랜드", "카테고리", "판매 가격",
                    "매장 재고", "부족 기준", "취급 상태", "최근 취급 변경");
            final int chunkSize = 500;
            for (long offset = 0; ; offset += chunkSize) {
                List<AssortmentItem> rows = dao.managerAssortment(connection, scope, filters.state, filters.sort,
                        chunkSize, offset, false);
                for (AssortmentItem item : rows) {
                    Stock stock = item.stock;
                    AdminProductController.csvRow(output, stock.id, stock.code, stock.name, item.brand,
                            categories.get(item.categoryId), item.price, stock.version < 0 ? "" : stock.quantity,
                            stock.version < 0 ? "" : stock.threshold, item.state().label,
                            StoreManagerInventoryController.recordedTime(item.lastChange));
                }
                if (rows.size() < chunkSize) break;
            }
            connection.commit();
        } catch (IllegalArgumentException error) {
            response.sendError(400, "검색 조건을 확인해 주세요.");
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 취급 상품 CSV 실패", error);
            response.sendError(503, "CSV를 만들지 못했습니다.");
            return null;
        }
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=store-assortment.csv");
        response.getWriter().write(output.toString());
        response.flushBuffer();
        return null;
    }

    @DaisoPostMapping(MAPPING + "/preview")
    public String preview(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return change(request, response, false);
    }

    @DaisoPostMapping(MAPPING + "/save")
    public String save(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return change(request, response, true);
    }

    private String change(HttpServletRequest request, HttpServletResponse response, boolean save) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        String member = StoreManagerAccess.memberId(session);
        if (member == null || !AdminProductController.validToken(request, session)) {
            return problem(response, 403, "매장 관리자 권한과 화면을 다시 확인해 주세요.");
        }
        try (Connection connection = ProductQueries.connect()) {
            try {
                Mutation input = new Mutation(request);
                Filters filters = new Filters(request);
                if (save) StoreStocktake.beginCurrentRead(connection);
                boolean safePickup = PickupStockTransaction.enabled(connection);
                StoreManagerAccess manager = save ? StoreManagerAccess.findForWrite(connection, member)
                        : StoreManagerAccess.find(connection, member, false);
                if (manager == null) {
                    connection.rollback();
                    return problem(response, 403, "현재 매장 배정을 확인해 주세요.");
                }
                StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
                if (!safePickup && save && !dao.lockStore(connection, manager.storeId, "")) {
                    throw new Conflict("현재 매장 배정을 확인해 주세요.");
                }
                String revision = safePickup && input.action == Assortment.UNLIST
                        ? StoreStocktake.revision(connection, manager.storeId) : "";
                if (save) consume(session, request, member, manager.storeId, input, revision);

                JsonArray results = new JsonArray();
                Map<String, Stock> validated = new LinkedHashMap<>();
                for (String id : dao.assortmentProductOrder(connection, input.items.keySet())) {
                    if (save) dao.findProduct(connection, id); // Protect the global sale status while committing.
                    AssortmentItem item = dao.managerAssortmentItem(connection, manager.storeId, id);
                    Stock locked = save && item != null && item.stock.version >= 0
                            ? dao.find(connection, manager.storeId, id, true) : null;
                    Stock before = locked == null && item != null ? item.stock : locked;
                    Line line = input.items.get(id);
                    String error = validate(connection, manager.storeId, item, before, input.action, line,
                            dao.thresholdsEnabled(), safePickup);
                    if (error == null) validated.put(id, before);
                    JsonObject result = new JsonObject();
                    result.addProperty("id", id);
                    result.addProperty("message", error);
                    if (item != null && before != null) {
                        result.addProperty("quantity", before.version < 0 ? null : before.quantity);
                        result.addProperty("threshold", before.version < 0 ? null : before.threshold);
                        result.addProperty("afterQuantity", afterQuantity(input.action, before, line));
                        result.addProperty("afterThreshold", input.action == Assortment.UNLIST ? before.threshold : line.threshold);
                    }
                    results.add(result);
                }
                JsonObject payload = new JsonObject();
                payload.add("items", results);
                if (!save) {
                    connection.commit();
                    if (validated.size() == input.items.size()) {
                        String token = UUID.randomUUID().toString();
                        synchronized (session) {
                            remember(session).previews.put(token, new Pending(member, manager.storeId, input, revision));
                            trim(remember(session).previews);
                        }
                        payload.addProperty("token", token);
                    }
                    return json(response, 200, payload.toString());
                }
                if (validated.size() != input.items.size()) {
                    connection.rollback();
                    payload.addProperty("message", "조건이 바뀐 상품이 있습니다. 최신 상태를 확인해 주세요.");
                    return json(response, 409, payload.toString());
                }
                for (Map.Entry<String, Stock> entry : validated.entrySet()) {
                    Stock before = entry.getValue();
                    Line line = input.items.get(entry.getKey());
                    Integer threshold = input.action != Assortment.UNLIST && dao.thresholdsEnabled() ? line.threshold : null;
                    dao.changeAssortment(connection, manager.storeId, before, afterQuantity(input.action, before, line),
                            input.action, input.reason, member, threshold);
                }
                if (safePickup) PickupStockTransaction.advance(connection, manager.storeId);
                connection.commit();
                String receipt = UUID.randomUUID().toString();
                synchronized (session) {
                    SessionState state = remember(session);
                    state.notices.put(receipt, new String[]{member, manager.storeId,
                            validated.size() + "개 상품의 " + input.action.label + " 작업을 완료했습니다."});
                    trim(state.notices);
                }
                payload.addProperty("url", ProductView.rawUrl(request.getContextPath(), ROUTE,
                        ProductView.with(filters.params(filters.page), "receipt", receipt)));
                return json(response, 200, payload.toString());
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } catch (Conflict error) {
            return problem(response, 409, error.getMessage());
        } catch (IllegalArgumentException error) {
            return problem(response, 400, "상품, 수량, 부족 기준 및 변경 사유를 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 취급 상품 변경 실패", error);
            return problem(response, 503, "변경 결과를 확인하지 못했습니다. 목록과 이력을 확인한 뒤 다시 시도해 주세요.");
        }
    }

    private static String validate(Connection connection, String store, AssortmentItem item, Stock before,
            Assortment action, Line line, boolean thresholds, boolean safePickup) throws SQLException {
        if (before == null || before.version != line.version || before.carried != (action == Assortment.UNLIST)
                || (action == Assortment.REGISTER) != (before.version < 0)) return "취급 또는 재고 상태가 변경되었습니다.";
        if (action == Assortment.UNLIST) {
            if (before.quantity != 0) return "남은 재고를 0개로 처리한 뒤 종료해 주세요.";
            if (!safePickup) return "현재 환경의 픽업 재고 보호가 활성화된 후에 종료할 수 있습니다.";
            try {
                if (StoreInventoryCountPreview.allocated(connection, store, before.id) > 0) return "미수령 픽업 할당이 있습니다.";
            } catch (StoreInventoryCountPreview.UncertainPickup error) {
                return error.getMessage();
            }
        } else {
            if (!item.eligible) return "공개된 판매 가능 상품만 취급할 수 있습니다.";
            if (!thresholds && line.threshold != StoreInventoryDAO.DEFAULT_THRESHOLD) return "현재 기본 부족 기준만 사용할 수 있습니다.";
            if (action == Assortment.RESUME && before.quantity > 0) {
                if (!safePickup) return "복구 재고를 보존하려면 픽업 재고 보호가 활성화되어야 합니다.";
                if (before.quantity != line.quantity) return "복구 재고 수량은 변경할 수 없습니다.";
            }
        }
        return null;
    }

    private static int afterQuantity(Assortment action, Stock before, Line line) {
        return action == Assortment.UNLIST ? 0 : action == Assortment.RESUME && before.quantity > 0
                ? before.quantity : line.quantity;
    }

    private static void consume(HttpSession session, HttpServletRequest request, String member, String store,
            Mutation input, String revision) {
        Pending pending;
        synchronized (session) {
            pending = remember(session).previews.remove(ProductQueries.text(request, "previewToken", 36));
        }
        if (pending == null || pending.expires < System.currentTimeMillis() || !member.equals(pending.member)
                || !store.equals(pending.store) || !revision.equals(pending.revision) || input.action != pending.input.action
                || !input.reason.equals(pending.input.reason) || !input.items.equals(pending.input.items)) {
            throw new Conflict("미리보기가 변경되었거나 만료되었습니다. 최신 상태로 다시 미리보기해 주세요.");
        }
    }

    private static String json(HttpServletResponse response, int status, String value) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setStatus(status);
        response.getWriter().write(value);
        response.flushBuffer();
        return null;
    }

    private static String problem(HttpServletResponse response, int status, String message) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("message", message);
        return json(response, status, result.toString());
    }

    private static SessionState remember(HttpSession session) {
        SessionState state = (SessionState) session.getAttribute(SESSION);
        if (state == null) {
            state = new SessionState();
            session.setAttribute(SESSION, state);
        }
        return state;
    }

    private static void trim(Map<String, ?> map) {
        if (map.size() > 12) map.remove(map.keySet().iterator().next());
    }

    private static String notice(HttpSession session, String receipt, String member, String store) {
        if (receipt.isEmpty()) return null;
        synchronized (session) {
            String[] notice = remember(session).notices.remove(receipt);
            return notice != null && member.equals(notice[0]) && store.equals(notice[1]) ? notice[2] : null;
        }
    }

    private static final class SessionState {
        final Map<String, Pending> previews = new LinkedHashMap<>();
        final Map<String, String[]> notices = new LinkedHashMap<>();
    }

    private static final class Pending {
        final String member, store, revision;
        final Mutation input;
        final long expires = System.currentTimeMillis() + PREVIEW_TTL;

        Pending(String member, String store, Mutation input, String revision) {
            this.member = member;
            this.store = store;
            this.input = input;
            this.revision = revision;
        }
    }

    private static final class Conflict extends IllegalArgumentException {
        Conflict(String message) { super(message); }
    }

    private static final class Line {
        final long version;
        final int quantity, threshold;

        Line(String version, String quantity, String threshold) {
            this.version = Long.parseLong(version);
            this.quantity = Integer.parseInt(quantity);
            this.threshold = Integer.parseInt(threshold);
            if (this.version < -1 || this.quantity < 0 || this.threshold < 1) throw new IllegalArgumentException();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Line)) return false;
            Line line = (Line) other;
            return version == line.version && quantity == line.quantity && threshold == line.threshold;
        }

        @Override public int hashCode() { return java.util.Objects.hash(version, quantity, threshold); }
    }

    private static final class Mutation {
        final Assortment action;
        final String reason;
        final Map<String, Line> items = new LinkedHashMap<>();

        Mutation(HttpServletRequest request) {
            action = Assortment.valueOf(ProductQueries.text(request, "operation", 12));
            reason = ProductQueries.text(request, "reason", 255);
            String[] ids = request.getParameterValues("item");
            String[] versions = request.getParameterValues("version");
            String[] quantities = request.getParameterValues("quantity");
            String[] thresholds = request.getParameterValues("threshold");
            if (reason.isBlank() || ids == null || ids.length == 0 || ids.length > LIMIT
                    || action == Assortment.RESUME && ids.length != 1 || versions == null || quantities == null
                    || thresholds == null || versions.length != ids.length || quantities.length != ids.length
                    || thresholds.length != ids.length) throw new IllegalArgumentException();
            for (int i = 0; i < ids.length; i++) {
                if (ids[i].isBlank() || ids[i].length() > 100
                        || items.putIfAbsent(ids[i], new Line(versions[i], quantities[i], thresholds[i])) != null) {
                    throw new IllegalArgumentException();
                }
            }
        }
    }

    private static final class Filters {
        final String keyword, category, selected;
        final ManagerAssortment state;
        final ManagerSort sort;
        final int page, size;

        Filters(HttpServletRequest request) {
            keyword = Normalizer.normalize(ProductQueries.text(request, "q", 120), Normalizer.Form.NFC);
            category = ProductQueries.text(request, "category", 100);
            selected = ProductQueries.text(request, "selectedProductId", 100);
            String stateValue = ProductQueries.text(request, "state", 16);
            state = stateValue.isEmpty() ? ManagerAssortment.ALL : ManagerAssortment.valueOf(stateValue);
            String sortValue = ProductQueries.text(request, "sort", 20);
            sort = sortValue.isEmpty() ? ManagerSort.NAME : ManagerSort.valueOf(sortValue);
            page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE);
            size = ProductQueries.integer(request, "size", 20, 20, 100);
            if (!Set.of(20, 50, 100).contains(size)) throw new IllegalArgumentException();
        }

        InventoryFilter scope(String store, boolean suggestion) {
            return new InventoryFilter(store, keyword, category, suggestion ? "" : selected, State.ALL, null, null);
        }

        Map<String, String> params(int targetPage) {
            return Map.of("q", keyword, "category", category, "selectedProductId", selected, "state", state.name(),
                    "sort", sort.name(), "size", Integer.toString(size), "page", Integer.toString(targetPage));
        }
    }

    public static final class Page {
        public final Map<String, String> params, categories;
        public final Map<ManagerAssortment, Long> totals;
        public final List<AssortmentItem> rows;
        public final ManagerAssortment state;
        public final ManagerSort sort;
        public final boolean safePickup, thresholds;
        public final String notice;
        public final long total, first, last, recovered;
        public final InventoryPagination pagination;
        private final String context;

        Page(String context, Filters filters, int page, int pages, Map<ManagerAssortment, Long> totals,
                List<AssortmentItem> rows, Map<String, String> categories, long offset, long recovered,
                boolean safePickup, boolean thresholds, String notice) {
            this.context = context;
            params = filters.params(page);
            this.categories = categories;
            this.totals = totals;
            this.rows = rows;
            state = filters.state;
            sort = filters.sort;
            total = totals.get(state);
            first = rows.isEmpty() ? 0 : offset + 1;
            last = offset + rows.size();
            this.notice = notice;
            this.recovered = recovered;
            this.safePickup = safePickup;
            this.thresholds = thresholds;
            pagination = new InventoryPagination(page, pages, target -> ProductView.url(context, ROUTE, filters.params(target)));
        }

        public String filter(ManagerAssortment target) {
            Map<String, String> next = target == ManagerAssortment.RECOVERED ? Map.of("state", target.name())
                    : ProductView.with(ProductView.with(params, "page", "1"), "state", target.name());
            return ProductView.url(context, ROUTE, next);
        }

        public String csv() { return ProductView.url(context, ROUTE + "/csv", params); }
    }
}
