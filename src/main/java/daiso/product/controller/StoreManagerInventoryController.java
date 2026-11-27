package daiso.product.controller;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.InventoryFilter;
import daiso.product.dao.StoreInventoryDAO.InventorySnapshot;
import daiso.product.dao.StoreInventoryDAO.ManagerSort;
import daiso.product.dao.StoreInventoryDAO.State;
import daiso.product.dao.StoreInventoryDAO.Stock;
import daiso.product.dao.StoreInventoryDAO.StockSuggestion;
import daiso.product.dao.StoreInventoryDAO.Summary;

@DaisoController
public final class StoreManagerInventoryController {
    public static final String ROUTE = "/app/store-manager/inventory";
    private static final String MAPPING = "/store-manager/inventory";
    private static final String VIEW = "store-manager/inventory";
    private static final String SESSION_KEY = StoreManagerInventoryController.class.getName();
    private static final DateTimeFormatter RECORDED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SESSION_LIMIT = 24;

    static String list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String memberId = StoreManagerAccess.memberId(session);
        if (memberId == null) return "redirect:/member/login";

        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
            if (manager == null) return denied(response);
            Filters filters = new Filters(request);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            InventoryFilter scope = filters.forStore(manager.storeId, false);
            long total = dao.countInventory(connection, scope);
            int pages = Math.toIntExact(Math.max(1, (total + filters.size - 1) / filters.size));
            int page = Math.min(filters.page, pages);
            List<InventorySnapshot> rows = dao.inventorySnapshots(connection, scope, filters.sort,
                    filters.size, (page - 1L) * filters.size);
            Map<String, String> categories = new ProductDAO().findAdminCategories(connection, false);
            Summary summary = dao.summary(connection, manager.storeId);
            connection.commit();

            StoreManagerController.showManager(request, manager, "inventory");
            request.setAttribute("storeManagerId", manager.storeId);
            request.setAttribute("storeManagerCsrf", AdminProductController.token(session));
            String token = "";
            Notice notice;
            synchronized (session) {
                SessionState state = state(session);
                if (dao.thresholdsEnabled()) {
                    token = UUID.randomUUID().toString();
                    remember(state.tokens, token, manager.storeId);
                }
                String receipt = request.getParameter("receipt");
                notice = state.notices.get(receipt);
                if (notice != null && notice.store.equals(manager.storeId)) state.notices.remove(receipt);
                else notice = null;
            }
            request.setAttribute("storeInventoryPage", new Page(request.getContextPath(), filters, page,
                    pages, total, categories, summary, rows, token, notice));
            return VIEW;
        } catch (IllegalArgumentException error) {
            response.sendError(400, "조회 조건을 확인해 주세요. 최소 수량은 최대 수량 이하여야 합니다.");
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 재고 목록 조회 실패", error);
            response.sendError(503, "매장 재고 목록을 불러오지 못했습니다.");
            return null;
        }
    }

    @DaisoGetMapping(MAPPING + "/suggestions")
    public String suggestions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String memberId = StoreManagerAccess.memberId(request);
        JsonArray items = new JsonArray();
        if (memberId == null) {
            response.setStatus(403);
        } else {
            try (Connection connection = ProductQueries.connect()) {
                StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
                if (manager == null) {
                    response.setStatus(403);
                } else {
                    Filters filters = new Filters(request);
                    StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
                    for (StockSuggestion match : dao.suggestInventory(connection, filters.forStore(manager.storeId, true))) {
                        Stock stock = match.stock;
                        AdminProductSuggestions.add(items, request.getContextPath(), stock.id, stock.name, stock.code,
                                stock.image, match.price, stock.quantity, stock.state().label);
                    }
                    connection.commit();
                }
            } catch (IllegalArgumentException error) {
                response.setStatus(400);
            } catch (SQLException | NamingException error) {
                request.getServletContext().log("매장 상품 자동완성 조회 실패", error);
                response.setStatus(503);
            }
        }
        response.getWriter().write(response.getStatus() == 200 ? items.toString() : "[]");
        response.flushBuffer();
        return null;
    }

    @DaisoGetMapping(MAPPING + "/csv")
    public String csv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        String memberId = StoreManagerAccess.memberId(request);
        if (memberId == null) return denied(response);

        StringBuilder output = new StringBuilder("\ufeff");
        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, false);
            if (manager == null) return denied(response);

            Filters filters = new Filters(request);
            InventoryFilter scope = filters.forStore(manager.storeId, false);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            Map<String, String> categories = new ProductDAO().findAdminCategories(connection, false);
            AdminProductController.csvRow(output, "상품 ID", "품번", "상품명", "카테고리", "현재 재고",
                    "부족 기준", "재고 상태", "최근 기록된 수량 변경 시각");
            int chunkSize = 500;
            for (long offset = 0; ; offset += chunkSize) {
                List<InventorySnapshot> rows = dao.inventorySnapshots(connection, scope, filters.sort, chunkSize, offset);
                for (InventorySnapshot row : rows) {
                    Stock stock = row.stock;
                    AdminProductController.csvRow(output, stock.id, stock.code, stock.name,
                            categories.get(row.categoryId), stock.quantity, stock.threshold, stock.state().label,
                            recordedTime(row.lastQuantityChange));
                }
                if (rows.size() < chunkSize) break;
            }
            connection.commit();
        } catch (IllegalArgumentException error) {
            response.sendError(400, "CSV 조회 조건을 확인해 주세요.");
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 재고 CSV 조회 실패", error);
            response.sendError(503, "매장 재고 CSV를 만들지 못했습니다.");
            return null;
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=store-inventory.csv");
        response.getWriter().write(output.toString());
        response.flushBuffer();
        return null;
    }

    private enum Save { THRESHOLD, ADJUST, RECEIVE_BATCH }

    // Enable relative quantity submissions after the approved MySQL concurrency tests.
    private static final Set<Change> VERIFIED_RELATIVE_WRITES = Set.of();

    public static boolean quantityWritesEnabled() {
        return VERIFIED_RELATIVE_WRITES.containsAll(Set.of(Change.RECEIVE, Change.DAMAGE));
    }

    @DaisoPostMapping(MAPPING + "/threshold")
    public String threshold(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return save(request, response, Save.THRESHOLD);
    }

    @DaisoPostMapping(MAPPING + "/adjust")
    public String adjust(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return save(request, response, Save.ADJUST);
    }

    @DaisoPostMapping(MAPPING + "/receive-batch")
    public String receiveBatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return save(request, response, Save.RECEIVE_BATCH);
    }

    private String save(HttpServletRequest request, HttpServletResponse response, Save operation) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = request.getSession(false);
        String memberId = StoreManagerAccess.memberId(session);
        if (memberId == null || !AdminProductController.validToken(request, session)) return denied(response);

        Filters filters;
        Change change;
        try {
            filters = new Filters(request);
            change = quantityChange(request, operation);
        } catch (IllegalArgumentException error) {
            response.sendError(400, "조회 조건 또는 재고 변경 유형을 확인해 주세요.");
            return null;
        }

        boolean allowed = operation == Save.THRESHOLD
                || change != Change.COUNT && quantityWritesEnabled();
        String store = null;
        Notice notice;
        try (Connection connection = ProductQueries.connect()) {
            StoreManagerAccess manager = StoreManagerAccess.find(connection, memberId, allowed);
            if (manager == null) {
                connection.rollback();
                return denied(response);
            }
            store = manager.storeId;
            if (!allowed) {
                connection.rollback();
                response.sendError(409, change == Change.COUNT
                        ? "실사 저장은 픽업 결제와의 동시성 검증을 마친 뒤 사용할 수 있습니다."
                        : "픽업 결제와 통합 관리자 재고 변경 시 수량이 어긋날 수 있어 수량 저장을 사용할 수 없습니다.");
                return null;
            }

            try {
                consumeToken(session, ProductQueries.text(request, "formToken", 36), store);
                String reason = ProductQueries.text(request, "reason", 255);
                if (reason.isEmpty()) throw new IllegalArgumentException("변경 사유를 입력해 주세요.");

                StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
                String message;
                if (operation == Save.THRESHOLD) {
                    Integer threshold = ProductQueries.integer(request, "threshold", null, 1, Integer.MAX_VALUE);
                    if (threshold == null) throw new IllegalArgumentException("부족 기준을 입력해 주세요.");
                    if (!dao.thresholdsEnabled()) throw new IllegalArgumentException("현재 DB에서 부족 기준 변경을 사용할 수 없습니다.");
                    Map<String, Long> selected = selection(request.getParameterValues("item"), request.getParameterValues("version"));
                    int updated = updateThresholds(connection, dao, store, selected, threshold, reason, memberId);
                    message = updated == 0 ? "변경할 부족 기준이 없습니다." : updated + "개 상품의 부족 기준을 변경했습니다.";
                } else {
                    Map<String, Integer> amounts = quantities(request, operation == Save.ADJUST);
                    StoreManagerQuantityChange.apply(connection, dao, store, change, amounts, reason, memberId);
                    message = amounts.size() + "개 상품의 재고를 변경했습니다. 변경 이력에서 실제 수량을 확인할 수 있습니다.";
                }
                connection.commit();
                notice = new Notice(store, message, false);
            } catch (IllegalArgumentException | SQLException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof SQLException) throw (SQLException) failure;
                notice = new Notice(store, failure.getMessage(), true);
            }
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 재고 저장 실패", error);
            if (store == null) {
                response.sendError(503, "재고를 저장하지 못했습니다.");
                return null;
            }
            notice = new Notice(store, "재고를 저장하지 못했습니다. 현재 목록과 변경 이력을 확인한 뒤 다시 시도해 주세요.", true);
        }

        String receipt = UUID.randomUUID().toString();
        synchronized (session) {
            remember(state(session).notices, receipt, notice);
        }
        return "redirect:" + ProductView.rawUrl("", MAPPING,
                ProductView.with(filters.params(filters.page), "receipt", receipt));
    }

    private static Change quantityChange(HttpServletRequest request, Save operation) {
        if (operation == Save.THRESHOLD) return null;
        if (operation == Save.RECEIVE_BATCH) return Change.RECEIVE;
        Change change = Change.valueOf(ProductQueries.text(request, "change", 12));
        if (!Set.of(Change.RECEIVE, Change.DAMAGE, Change.COUNT).contains(change)) throw new IllegalArgumentException();
        return change;
    }

    private static void consumeToken(HttpSession session, String token, String store) {
        synchronized (session) {
            Map<String, String> tokens = state(session).tokens;
            if (!store.equals(tokens.get(token))) {
                throw new IllegalArgumentException("이미 처리됐거나 매장이 변경된 요청입니다. 목록에서 다시 확인해 주세요.");
            }
            tokens.remove(token);
        }
    }

    private static Map<String, Integer> quantities(HttpServletRequest request, boolean single) {
        String[] ids = request.getParameterValues("item");
        String[] inputs = request.getParameterValues("amount");
        int limit = single ? 1 : AdminInventoryBatchController.BATCH_LIMIT;
        if (ids == null || ids.length == 0 || ids.length > limit || inputs == null || inputs.length != ids.length) {
            throw new IllegalArgumentException("상품을 1~" + limit + "개 선택해 주세요.");
        }

        Map<String, Integer> amounts = new TreeMap<>();
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            if (!validProductId(id) || !inputs[i].matches("[0-9]{1,10}")) {
                throw new IllegalArgumentException("상품과 변경 수량을 확인해 주세요.");
            }
            long amount = Long.parseLong(inputs[i]);
            if (amount < 1 || amount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("변경 수량은 1개 이상의 정수여야 합니다.");
            }
            if (amounts.putIfAbsent(id, (int) amount) != null) {
                throw new IllegalArgumentException("중복 선택된 상품이 있습니다.");
            }
        }
        return amounts;
    }

    private static boolean validProductId(String id) {
        return !id.isBlank() && id.length() <= 100 && id.equals(id.strip());
    }

    static int updateThresholds(Connection connection, StoreInventoryDAO dao, String store,
            Map<String, Long> selection, int threshold, String reason, String memberId) throws SQLException {
        List<Stock> changes = new ArrayList<>();
        for (Map.Entry<String, Long> item : selection.entrySet()) {
            Stock stock = dao.find(connection, store, item.getKey(), true);
            if (stock == null || !item.getKey().equals(stock.id) || !stock.carried || stock.version != item.getValue()) {
                throw new IllegalArgumentException("재고 상태가 바뀌었거나 취급하지 않는 상품이 포함됐습니다. 목록에서 다시 선택해 주세요.");
            }
            if (stock.threshold != threshold) changes.add(stock);
        }
        for (Stock stock : changes) dao.updateThreshold(connection, store, stock, threshold, memberId, reason);
        return changes.size();
    }

    private static Map<String, Long> selection(String[] ids, String[] versions) {
        if (ids == null || ids.length == 0 || ids.length > AdminInventoryBatchController.BATCH_LIMIT
                || versions == null || versions.length != ids.length) {
            throw new IllegalArgumentException("상품을 1~" + AdminInventoryBatchController.BATCH_LIMIT + "개 선택해 주세요.");
        }
        Map<String, Long> result = new TreeMap<>();
        for (int i = 0; i < ids.length; i++) {
            if (!validProductId(ids[i])
                    || !versions[i].matches("[0-9]{1,19}")) throw new IllegalArgumentException("상품 정보를 확인해 주세요.");
            long version;
            try { version = Long.parseLong(versions[i]); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("상품 정보를 확인해 주세요."); }
            if (result.putIfAbsent(ids[i], version) != null) throw new IllegalArgumentException("중복 선택된 상품이 있습니다.");
        }
        return result;
    }

    private static String denied(HttpServletResponse response) throws IOException {
        response.sendError(403, "배정된 매장이 있는 관리자만 이용할 수 있습니다.");
        return null;
    }

    private static SessionState state(HttpSession session) {
        SessionState state = (SessionState) session.getAttribute(SESSION_KEY);
        if (state == null) {
            state = new SessionState();
            session.setAttribute(SESSION_KEY, state);
        }
        return state;
    }

    private static <T> void remember(Map<String, T> entries, String key, T value) {
        if (entries.size() >= SESSION_LIMIT) entries.remove(entries.keySet().iterator().next());
        entries.put(key, value);
    }

    private static final class SessionState {
        final Map<String, String> tokens = new LinkedHashMap<>();
        final Map<String, Notice> notices = new LinkedHashMap<>();
    }

    public static final class Notice {
        private final String store;
        public final String message;
        public final boolean error;
        private Notice(String store, String message, boolean error) {
            this.store = store;
            this.message = message;
            this.error = error;
        }
    }

    private static final class Filters {
        final String keyword, category, selected;
        final State state;
        final ManagerSort sort;
        final Integer minimum, maximum;
        final int page, size;

        Filters(HttpServletRequest request) {
            keyword = Normalizer.normalize(ProductQueries.text(request, "q", 120), Normalizer.Form.NFC);
            category = ProductQueries.text(request, "category", 100);
            selected = ProductQueries.text(request, "selectedProductId", 100);
            String stateValue = ProductQueries.text(request, "state", 16);
            state = stateValue.isEmpty() ? State.ALL : State.valueOf(stateValue);
            String sortValue = ProductQueries.text(request, "sort", 20);
            sort = sortValue.isEmpty() ? ManagerSort.NAME : ManagerSort.valueOf(sortValue);
            minimum = ProductQueries.integer(request, "minimum", null, 0, Integer.MAX_VALUE);
            maximum = ProductQueries.integer(request, "maximum", null, 0, Integer.MAX_VALUE);
            size = ProductQueries.integer(request, "size", 20, 20, 100);
            page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE);
            if (!Set.of(20, 50, 100).contains(size) || minimum != null && maximum != null && minimum > maximum) {
                throw new IllegalArgumentException("조회 범위");
            }
        }

        InventoryFilter forStore(String store, boolean suggestions) {
            return new InventoryFilter(store, keyword, category, suggestions ? "" : selected, state, minimum, maximum);
        }

        Map<String, String> params(int targetPage) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("q", keyword);
            params.put("category", category);
            params.put("selectedProductId", selected);
            params.put("state", state.name());
            params.put("sort", sort.name());
            params.put("minimum", minimum == null ? "" : minimum.toString());
            params.put("maximum", maximum == null ? "" : maximum.toString());
            params.put("size", Integer.toString(size));
            params.put("page", Integer.toString(targetPage));
            return params;
        }
    }

    public static final class Page {
        private final String context;
        public final Map<String, String> params, categories;
        public final List<InventorySnapshot> rows;
        public final Summary summary;
        public final long total, first, last;
        public final String token;
        public final Notice notice;
        public final InventoryPagination pagination;
        public final ManagerSort sort;
        public final State state;

        private Page(String context, Filters filters, int page, int pages, long total, Map<String, String> categories,
                Summary summary, List<InventorySnapshot> rows, String token, Notice notice) {
            this.context = context;
            params = filters.params(page);
            this.categories = categories;
            this.rows = rows;
            this.summary = summary;
            this.total = total;
            this.token = token;
            this.notice = notice;
            this.sort = filters.sort;
            this.state = filters.state;
            first = total == 0 ? 0 : (page - 1L) * filters.size + 1;
            last = total == 0 ? 0 : first + rows.size() - 1;
            pagination = new InventoryPagination(page, pages, target -> ProductView.url(context, ROUTE, filters.params(target)));
        }

        public String filter(String key, String value) {
            return ProductView.url(context, ROUTE, ProductView.with(ProductView.with(params, key, value), "page", "1"));
        }

        public String csv() { return ProductView.url(context, ROUTE + "/csv", params); }
    }

    public static String recordedTime(LocalDateTime value) {
        return value == null ? "" : RECORDED_TIME.format(value);
    }
}
