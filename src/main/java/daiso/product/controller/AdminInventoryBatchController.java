package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.Stock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class AdminInventoryBatchController {
    private static final String KEY = AdminInventoryBatchController.class.getName();
    private static final String VIEW = "admin/inventory-batch";
    private static final String PREVIEW_KEY = KEY + ".previews";
    private static final int PREVIEW_LIMIT = 8;
    static final String SAVED_PARAMETER = "batchSaved";
    private final ProductDAO products = new ProductDAO();
    private final AdminProductChanges audit = new AdminProductChanges();

    @DaisoPostMapping("/admin/inventory/batch")
    public String online(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return batch(request, response, true);
    }

    @DaisoPostMapping("/admin/stores/batch")
    public String stores(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return batch(request, response, false);
    }

    private String batch(HttpServletRequest request, HttpServletResponse response, boolean online)
            throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "private, no-store");
        response.addHeader("Vary", "Accept");
        boolean json = AdminBatchResponse.wantsJson(request);
        request.setAttribute("adminSection", online ? "inventory" : "stores");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            String message = "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.";
            return json ? AdminBatchResponse.failed(response, 403, message) : problem(request, response, 403, message);
        }
        String route = online ? "/admin/inventory" : "/admin/stores";
        String manager = session.getAttribute("member_id").toString();
        request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, Map.of()));
        try {
            Map<String, String> filters = returnFilters(request, online);
            request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, filters));
            String token = ProductQueries.text(request, "receipt", 36);
            if (token.isEmpty()) {
                if (json) return AdminBatchResponse.failed(response, 400, "미리보기를 만든 뒤 변경을 확정해 주세요.");
                Plan plan = preview(request, online, filters);
                rememberPreview(session, plan);
                request.setAttribute("batchPreview", plan);
                request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, plan.filters));
                request.setAttribute("batchAction", ProductView.escape(request.getContextPath() + "/app" + route + "/batch"));
                request.setAttribute("batchCsrf", AdminProductController.token(session));
                return VIEW;
            }
            Plan plan;
            synchronized (session) {
                PreviewPlans previews = (PreviewPlans) session.getAttribute(PREVIEW_KEY);
                plan = previews == null ? null : previews.byReceipt.get(token);
                if (plan == null || plan.online != online || plan.expires < System.currentTimeMillis()) {
                    throw new IllegalArgumentException("미리보기가 만료되었거나 이미 처리되었습니다. 최신 재고를 확인하고 다시 미리보기를 만들어 주세요.");
                }
                previews.byReceipt.remove(token);
            }
            request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, plan.filters));
            int changed = commit(plan, manager);
            String message = "재고 일괄 작업을 완료했습니다. " + changed + "개 재고 행을 변경했습니다.";
            if (json) {
                String listUrl = ProductView.rawUrl(request.getContextPath() + request.getServletPath(), route, plan.filters);
                return AdminBatchResponse.saved(response, message, listUrl);
            }
            synchronized (session) {
                PreviewPlans previews = (PreviewPlans) session.getAttribute(PREVIEW_KEY);
                if (previews.notices.size() >= PREVIEW_LIMIT) {
                    previews.notices.remove(previews.notices.keySet().iterator().next());
                }
                previews.notices.put(plan.receipt, new Notice(route, message));
            }
            return "redirect:" + ProductView.rawUrl("", route,
                    ProductView.with(plan.filters, SAVED_PARAMETER, plan.receipt)) + "#inventory-batch-result";
        } catch (InvalidInput error) {
            request.setAttribute("batchErrorField", error.field);
            return json ? AdminBatchResponse.failed(response, 400, error.getMessage())
                    : problem(request, response, 400, error.getMessage());
        } catch (IllegalArgumentException error) {
            return json ? AdminBatchResponse.failed(response, 400, error.getMessage())
                    : problem(request, response, 400, error.getMessage());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("재고 일괄 작업 실패", error);
            String message = "재고와 변경 이력을 저장하지 못했습니다. 스키마와 재고 상태를 확인해 주세요.";
            return json ? AdminBatchResponse.failed(response, 503, message) : problem(request, response, 503, message);
        }
    }

    private static void rememberPreview(HttpSession session, Plan plan) {
        synchronized (session) {
            PreviewPlans previews = (PreviewPlans) session.getAttribute(PREVIEW_KEY);
            if (previews == null) {
                previews = new PreviewPlans();
                session.setAttribute(PREVIEW_KEY, previews);
            }
            previews.byReceipt.values().removeIf(saved -> saved.expires < System.currentTimeMillis());
            if (previews.byReceipt.size() >= PREVIEW_LIMIT) {
                previews.byReceipt.remove(previews.byReceipt.keySet().iterator().next());
            }
            previews.byReceipt.put(plan.receipt, plan);
        }
    }

    private static final class PreviewPlans {
        private final Map<String, Plan> byReceipt = new LinkedHashMap<>();
        private final Map<String, Notice> notices = new LinkedHashMap<>();
    }

    private static final class Notice {
        private final String route;
        private final String message;

        private Notice(String route, String message) {
            this.route = route;
            this.message = message;
        }
    }

    private static Map<String, String> returnFilters(HttpServletRequest request, boolean online) {
        Map<String, String> filters = new LinkedHashMap<>();
        List<String> names = online ? List.of("q", "category", "state", "sort", "page")
                : List.of("q", "state", "sort", "store", "city", "page", "storeQuery",
                        "compareQ", "compareProduct", "compareCity", "compareSort", "trendDays");
        for (String name : names) filters.put(name, ProductQueries.text(request, name, 120));
        return filters;
    }

    private Plan preview(HttpServletRequest request, boolean online, Map<String, String> filters)
            throws SQLException, NamingException {
        Operation operation;
        try {
            operation = Operation.valueOf(ProductQueries.text(request, "operation", 12));
        } catch (IllegalArgumentException error) {
            throw new InvalidInput("operation", "변경 유형을 선택해 주세요.");
        }
        if (online && operation == Operation.TRANSFER) {
            throw new InvalidInput("operation", "온라인 재고는 매장 간 이동 대상이 아닙니다.");
        }
        String reason;
        try {
            reason = ProductQueries.text(request, "reason", 255);
        } catch (IllegalArgumentException error) {
            throw new InvalidInput("reason", "변경 사유는 255자 이내로 입력해 주세요.");
        }
        if (reason.isEmpty()) throw new InvalidInput("reason", "변경 사유를 입력해 주세요.");
        String[] values = request.getParameterValues("batchProduct");
        if (values == null || values.length == 0 || values.length > 20) {
            throw new InvalidInput("batchProduct", "현재 페이지에서 상품을 1~20개 선택해 주세요.");
        }
        TreeSet<String> ids = new TreeSet<>();
        for (String id : values) {
            if (id.isBlank() || id.length() > 100 || !id.equals(id.strip())
                    || id.codePoints().anyMatch(Character::isISOControl) || !ids.add(id)) {
                throw new InvalidInput("batchProduct", "선택한 상품을 확인해 주세요.");
            }
        }
        String store = filters.getOrDefault("store", "");
        String destination = operation == Operation.TRANSFER ? ProductQueries.text(request, "destination", 100) : "";
        if (operation == Operation.TRANSFER && ids.size() != 1) {
            throw new InvalidInput("batchProduct", "매장 간 이동은 상품 하나만 선택해 주세요.");
        }
        if (operation == Operation.TRANSFER && (destination.isEmpty() || store.equals(destination))) {
            throw new InvalidInput("destination", "출고 매장과 다른 입고 매장을 선택해 주세요.");
        }
        if (!online && store.isEmpty()) throw new InvalidInput("batchProduct", "대상 매장을 다시 선택해 주세요.");
        String mode = ProductQueries.text(request, "amountMode", 12);
        boolean individual = "INDIVIDUAL".equals(mode);
        if ((!mode.isEmpty() && !"SHARED".equals(mode) && !individual)
                || (individual && operation == Operation.TRANSFER)) {
            throw new InvalidInput("amountMode", "수량 입력 방식을 확인해 주세요.");
        }
        Integer shared = individual ? null : amount(request, "amount", operation);
        List<Line> lines = new ArrayList<>();
        Map<String, String> storeNames = new LinkedHashMap<>();
        try (Connection connection = ProductQueries.connect()) {
            StoreInventoryDAO stores = online ? null : StoreInventoryDAO.forInventory(connection);
            if (!online && operation == Operation.THRESHOLD && !stores.thresholdsEnabled()) {
                throw new IllegalArgumentException("매장 재고 부족 기준 SQL을 먼저 적용해 주세요.");
            }
            if (online && operation == Operation.THRESHOLD && !hasColumn(connection, "product_admin_changes", "threshold_reason")) {
                throw new IllegalArgumentException("온라인 부족 기준 이력 SQL을 먼저 적용해 주세요.");
            }
            if (operation == Operation.TRANSFER && !hasColumn(connection, "store_inventory_history", "transfer_id")) {
                throw new IllegalArgumentException("매장 간 재고 이동 SQL을 먼저 적용해 주세요.");
            }
            if (stores != null) {
                for (StoreInventoryDAO.Location location : stores.locations(connection)) {
                    storeNames.put(location.id, location.name);
                }
                if (operation == Operation.TRANSFER && !storeNames.containsKey(destination)) {
                    throw new InvalidInput("destination", "입고 매장을 다시 선택해 주세요.");
                }
            }
            for (String id : ids) {
                String field = individual ? "amount." + id : "amount";
                int amount = individual ? amount(request, field, operation) : shared;
                if (online) {
                    ProductDTO item = products.findOnlineProduct(connection, id, false);
                    if (item == null || !id.equals(item.getProductId())) throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
                    int before = operation == Operation.THRESHOLD ? item.getLowStockThreshold() : item.getStockQuantity();
                    lines.add(new Line(id, "", item.getName(), before, checkedAfter(operation, before, amount, field), 0));
                } else {
                    lines.add(storeLine(connection, stores, store, id, operation, amount, field, "batchProduct"));
                    if (operation == Operation.TRANSFER) {
                        lines.add(storeLine(connection, stores, destination, id, Operation.RECEIVE, amount,
                                field, "destination"));
                    }
                }
            }
            connection.commit();
        }
        return new Plan(online, operation, reason, filters, lines, storeNames);
    }

    private static int amount(HttpServletRequest request, String field, Operation operation) {
        Integer amount;
        try {
            amount = ProductQueries.integer(request, field, null, 1, Integer.MAX_VALUE);
        } catch (IllegalArgumentException error) {
            throw new InvalidInput(field, operation.amountLabel + "은 1~2,147,483,647 사이의 정수로 입력해 주세요.");
        }
        if (amount == null) throw new InvalidInput(field, operation.amountLabel + "을 입력해 주세요.");
        return amount;
    }

    private static int checkedAfter(Operation operation, int before, int amount, String field) {
        if ((operation == Operation.ISSUE || operation == Operation.TRANSFER) && amount > before) {
            throw new InvalidInput(field, "현재 재고보다 많은 수량을 출고할 수 없습니다.");
        }
        try {
            return operation.after(before, amount);
        } catch (IllegalArgumentException error) {
            throw new InvalidInput(field, error.getMessage());
        }
    }

    private static Line storeLine(Connection connection, StoreInventoryDAO stores, String store, String id,
            Operation operation, int amount, String quantityField, String storeField) throws SQLException {
        Stock item = stores.find(connection, store, id, false);
        if (item == null || !item.carried || !id.equals(item.id)) {
            throw new InvalidInput(storeField, "매장에서 취급하지 않는 상품입니다. 먼저 취급 등록을 완료해 주세요.");
        }
        int before = operation == Operation.THRESHOLD ? item.threshold : item.quantity;
        return new Line(id, store, item.name, before, checkedAfter(operation, before, amount, quantityField), item.version);
    }

    private int commit(Plan plan, String manager) throws SQLException, NamingException {
        try (Connection conn = ProductQueries.connect()) {
            try {
                StoreInventoryDAO stores = plan.online ? null : StoreInventoryDAO.forInventory(conn);
                if (!plan.online) {
                    TreeSet<String> locations = new TreeSet<>();
                    for (Line line : plan.lines) locations.add(line.store);
                    for (String store : locations) {
                        if (!stores.lockStore(conn, store, "")) throw new IllegalArgumentException("매장을 찾을 수 없습니다.");
                    }
                }
                int changed = 0;
                for (Line line : plan.lines) {
                    if (plan.online) changed += writeOnline(conn, plan, line, manager);
                    else changed += writeStore(conn, stores, plan, line, manager);
                }
                conn.commit();
                return changed;
            } catch (SQLException | RuntimeException failure) {
                try { conn.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    private int writeOnline(Connection conn, Plan plan, Line line, String manager) throws SQLException {
        ProductDTO item = products.findOnlineProduct(conn, line.id, true);
        boolean threshold = plan.operation == Operation.THRESHOLD;
        if (item == null || line.before != (threshold ? item.getLowStockThreshold() : item.getStockQuantity())) {
            throw new IllegalArgumentException("미리보기 이후 재고 또는 기준이 바뀌었습니다. 다시 확인해 주세요.");
        }
        if (line.before == line.after) return 0;
        products.updateOnlineInventory(conn, line.id, line.after, threshold);
        if (threshold) audit.recordThreshold(conn, manager, item, line.after, plan.reason);
        else audit.recordStock(conn, manager, item, products.findOnlineProduct(conn, line.id, false),
                plan.operation.name(), plan.reason);
        return 1;
    }

    private static int writeStore(Connection conn, StoreInventoryDAO stores, Plan plan, Line line, String manager)
            throws SQLException {
        Stock stock = stores.find(conn, line.store, line.id, true);
        if (stock == null || !stock.carried || stock.version != line.version) {
            throw new IllegalArgumentException("미리보기 이후 매장 재고가 바뀌었습니다. 다시 확인해 주세요.");
        }
        if (line.before == line.after) return 0;
        if (plan.operation == Operation.THRESHOLD) {
            stores.updateThreshold(conn, line.store, stock, line.after, manager, plan.reason);
        } else {
            Change change = line.after > line.before ? Change.RECEIVE : Change.ISSUE;
            stores.update(conn, line.store, stock, line.after, change, plan.reason, manager,
                    plan.operation == Operation.TRANSFER ? plan.receipt : null);
        }
        return 1;
    }

    static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (var query = conn.prepareStatement(sql)) {
            query.setString(1, table);
            query.setString(2, column);
            try (var rows = query.executeQuery()) { return rows.next(); }
        }
    }

    static String takeNotice(HttpServletRequest request, HttpSession session, String route) {
        String receipt = savedReceipt(request);
        if (receipt == null) return null;
        synchronized (session) {
            PreviewPlans previews = (PreviewPlans) session.getAttribute(PREVIEW_KEY);
            Notice notice = previews == null ? null : previews.notices.get(receipt);
            if (notice == null || !route.equals(notice.route)) return null;
            previews.notices.remove(receipt);
            return notice.message;
        }
    }

    static String savedReceipt(HttpServletRequest request) {
        String receipt = request.getParameter(SAVED_PARAMETER);
        return receipt != null && receipt.matches("[0-9a-f-]{36}") ? receipt : null;
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int code, String message) {
        response.setStatus(code);
        request.setAttribute("batchError", message);
        return VIEW;
    }

    private static final class InvalidInput extends IllegalArgumentException {
        private final String field;

        private InvalidInput(String field, String message) {
            super(message);
            this.field = field;
        }
    }

    private enum Operation {
        RECEIVE("일괄 입고", "입고 수량"), ISSUE("일괄 출고", "출고 수량"),
        THRESHOLD("부족 기준 변경", "새 부족 기준"), TRANSFER("매장 간 재고 이동", "이동 수량");

        private final String label;
        private final String amountLabel;

        Operation(String label, String amountLabel) {
            this.label = label;
            this.amountLabel = amountLabel;
        }

        int after(int before, int amount) {
            if (this == THRESHOLD) return amount;
            return (this == RECEIVE ? Change.RECEIVE : Change.ISSUE).after(before, amount);
        }
    }

    public static final class Plan {
        public final String receipt = UUID.randomUUID().toString();
        public final List<Line> lines;
        public final String reason;
        public final String label;
        public final long receivedUnits;
        public final long issuedUnits;
        public final Map<String, String> storeNames;
        public final boolean threshold;
        public final boolean transfer;
        private final long expires = System.currentTimeMillis() + 600_000;
        private final boolean online;
        private final Operation operation;
        private final Map<String, String> filters;

        Plan(boolean online, Operation operation, String reason, Map<String, String> filters, List<Line> lines,
                Map<String, String> storeNames) {
            this.lines = List.copyOf(lines);
            this.reason = reason;
            this.online = online;
            this.operation = operation;
            this.filters = Map.copyOf(filters);
            this.threshold = operation == Operation.THRESHOLD;
            this.transfer = operation == Operation.TRANSFER;
            this.label = operation.label;
            this.storeNames = Map.copyOf(storeNames);
            this.receivedUnits = lines.stream().mapToLong(line -> Math.max(0L, (long) line.after - line.before)).sum();
            this.issuedUnits = lines.stream().mapToLong(line -> Math.max(0L, (long) line.before - line.after)).sum();
        }
    }

    public static final class Line {
        public final String store;
        public final String name;
        public final int before;
        public final int after;
        private final String id;
        private final long version;

        Line(String id, String store, String name, int before, int after, long version) {
            this.id = id;
            this.store = store;
            this.name = name;
            this.before = before;
            this.after = after;
            this.version = version;
        }
    }
}
