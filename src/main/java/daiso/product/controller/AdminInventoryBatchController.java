package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.Stock;
import java.io.UnsupportedEncodingException;
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
    private final ProductDAO products = new ProductDAO();
    private final AdminProductChanges audit = new AdminProductChanges();

    @DaisoPostMapping("/admin/inventory/batch")
    public String online(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        return batch(request, response, true);
    }

    @DaisoPostMapping("/admin/stores/batch")
    public String stores(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        return batch(request, response, false);
    }

    private String batch(HttpServletRequest request, HttpServletResponse response, boolean online)
            throws UnsupportedEncodingException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            return problem(request, response, 403, "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.");
        }
        String route = online ? "/admin/inventory" : "/admin/stores";
        String manager = session.getAttribute("member_id").toString();
        request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, Map.of()));
        try {
            String token = ProductQueries.text(request, "receipt", 36);
            if (token.isEmpty()) {
                Plan plan = preview(request, online);
                synchronized (session) { session.setAttribute(KEY, plan); }
                request.setAttribute("batchPreview", plan);
                request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, plan.filters));
                request.setAttribute("batchAction", ProductView.escape(request.getContextPath() + "/app" + route + "/batch"));
                request.setAttribute("batchCsrf", AdminProductController.token(session));
                return VIEW;
            }
            Plan plan;
            synchronized (session) {
                Object value = session.getAttribute(KEY);
                if (!(value instanceof Plan) || !token.equals(((Plan) value).receipt)
                        || ((Plan) value).online != online || ((Plan) value).expires < System.currentTimeMillis()) {
                    throw new IllegalArgumentException("미리보기가 만료되었거나 이미 처리되었습니다.");
                }
                plan = (Plan) value;
                session.removeAttribute(KEY);
            }
            request.setAttribute("batchBack", ProductView.url(request.getContextPath(), "/app" + route, plan.filters));
            int changed = commit(plan, manager);
            session.setAttribute(KEY + route, "재고 일괄 작업을 완료했습니다. " + changed + "개 재고 행을 변경했습니다.");
            return "redirect:" + ProductView.rawUrl("", route, plan.filters) + "#inventory-batch";
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, error.getMessage());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("재고 일괄 작업 실패", error);
            return problem(request, response, 503, "재고와 변경 이력을 저장하지 못했습니다. 스키마와 재고 상태를 확인해 주세요.");
        }
    }

    private Plan preview(HttpServletRequest request, boolean online) throws SQLException, NamingException {
        Operation operation = Operation.valueOf(ProductQueries.text(request, "operation", 12));
        if (online && operation == Operation.TRANSFER) throw new IllegalArgumentException("온라인 재고는 매장 간 이동 대상이 아닙니다.");
        String reason = ProductQueries.text(request, "reason", 255);
        if (reason.isEmpty()) throw new IllegalArgumentException("변경 사유를 입력해 주세요.");
        String[] values = request.getParameterValues("batchProduct");
        if (values == null || values.length == 0 || values.length > 20) {
            throw new IllegalArgumentException("현재 페이지에서 상품을 1~20개 선택해 주세요.");
        }
        TreeSet<String> ids = new TreeSet<>();
        for (String id : values) {
            if (id.isBlank() || id.length() > 100 || !id.equals(id.strip())
                    || id.codePoints().anyMatch(Character::isISOControl) || !ids.add(id)) {
                throw new IllegalArgumentException("선택한 상품을 확인해 주세요.");
            }
        }
        Map<String, String> filters = new LinkedHashMap<>();
        for (String name : List.of("q", "category", "state", "sort", "store", "city", "page", "storeQuery")) {
            filters.put(name, ProductQueries.text(request, name, 120));
        }
        String store = filters.get("store");
        String destination = operation == Operation.TRANSFER ? ProductQueries.text(request, "destination", 100) : "";
        if (operation == Operation.TRANSFER && (ids.size() != 1 || destination.isEmpty() || store.equals(destination))) {
            throw new IllegalArgumentException("이동 상품 하나와 출고 매장과 다른 입고 매장을 선택해 주세요.");
        }
        if (!online && store.isEmpty()) throw new IllegalArgumentException("대상 매장을 다시 선택해 주세요.");
        Integer shared = ProductQueries.integer(request, "amount", null, 1, Integer.MAX_VALUE);
        List<Line> lines = new ArrayList<>();
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
            for (String id : ids) {
                Integer amount = ProductQueries.integer(request, "amount." + id, shared, 1, Integer.MAX_VALUE);
                if (amount == null) throw new IllegalArgumentException("공통 수량 또는 상품별 수량을 입력해 주세요.");
                if (online) {
                    ProductDTO item = products.findOnlineProduct(connection, id, false);
                    if (item == null || !id.equals(item.getProductId())) throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
                    int before = operation == Operation.THRESHOLD ? item.getLowStockThreshold() : item.getStockQuantity();
                    lines.add(new Line(id, "", item.getName(), before, operation.after(before, amount), 0));
                } else {
                    lines.add(storeLine(connection, stores, store, id, operation, amount));
                    if (operation == Operation.TRANSFER) {
                        lines.add(storeLine(connection, stores, destination, id, Operation.RECEIVE, amount));
                    }
                }
            }
            connection.commit();
        }
        return new Plan(online, operation, reason, filters, lines);
    }

    private static Line storeLine(Connection connection, StoreInventoryDAO stores, String store, String id,
            Operation operation, int amount) throws SQLException {
        Stock item = stores.find(connection, store, id, false);
        if (item == null || !item.carried || !id.equals(item.id)) {
            throw new IllegalArgumentException("매장에서 취급하지 않는 상품입니다. 먼저 취급 등록을 완료해 주세요.");
        }
        int before = operation == Operation.THRESHOLD ? item.threshold : item.quantity;
        return new Line(id, store, item.name, before, operation.after(before, amount), item.version);
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

    static Object takeNotice(HttpSession session, String route) {
        synchronized (session) {
            Object saved = session.getAttribute(KEY + route);
            session.removeAttribute(KEY + route);
            return saved;
        }
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int code, String message) {
        response.setStatus(code);
        request.setAttribute("batchError", message);
        return VIEW;
    }

    private enum Operation {
        RECEIVE, ISSUE, THRESHOLD, TRANSFER;

        int after(int before, int amount) {
            if (this == THRESHOLD) return amount;
            return (this == RECEIVE ? Change.RECEIVE : Change.ISSUE).after(before, amount);
        }
    }

    public static final class Plan {
        public final String receipt = UUID.randomUUID().toString();
        public final List<Line> lines;
        public final String reason;
        public final long changedUnits;
        public final boolean threshold;
        public final boolean transfer;
        private final long expires = System.currentTimeMillis() + 600_000;
        private final boolean online;
        private final Operation operation;
        private final Map<String, String> filters;

        Plan(boolean online, Operation operation, String reason, Map<String, String> filters, List<Line> lines) {
            this.lines = List.copyOf(lines);
            this.reason = reason;
            this.online = online;
            this.operation = operation;
            this.filters = Map.copyOf(filters);
            this.threshold = operation == Operation.THRESHOLD;
            this.transfer = operation == Operation.TRANSFER;
            this.changedUnits = lines.stream().mapToLong(line -> (long) line.after - line.before).sum();
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
