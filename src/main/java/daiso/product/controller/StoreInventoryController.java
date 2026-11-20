package daiso.product.controller;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.History;
import daiso.product.dao.StoreInventoryDAO.Location;
import daiso.product.dao.StoreInventoryDAO.State;
import daiso.product.dao.StoreInventoryDAO.Stock;

@DaisoController
public final class StoreInventoryController {
    public static final String ROUTE = "/app/admin/stores";
    private static final String MAPPING = "/admin/stores";
    private static final String VIEW = "admin/stores";
    private static final String SAVED = StoreInventoryController.class.getName() + ".saved";
    private static final int PAGE_SIZE = 20;
    private static final Set<String> CITIES = Set.of("", "서울", "부산");
    private final StoreInventoryDAO dao = new StoreInventoryDAO();

    @DaisoGetMapping(MAPPING)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) return blocked(request, response, 403, "관리자 권한이 필요합니다.");
        try {
            return show(request, response, session, new Filters(request), null);
        } catch (IllegalArgumentException error) {
            return blocked(request, response, 400, "조회 조건을 확인해 주세요.");
        }
    }

    @DaisoPostMapping(MAPPING + "/adjust")
    public String adjust(HttpServletRequest request, HttpServletResponse response)
            throws UnsupportedEncodingException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            return blocked(request, response, 403, "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.");
        }

        Filters filters;
        try {
            filters = new Filters(request);
            if (filters.store.isEmpty() || filters.product.isEmpty() || filters.history) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            return blocked(request, response, 400, "점포와 상품을 다시 선택해 주세요.");
        }

        Adjustment adjustment;
        try {
            adjustment = Adjustment.read(request);
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, filters, 400, error.getMessage());
        }

        boolean confirm = "save".equals(request.getParameter("action"));
        if (!confirm && !"preview".equals(request.getParameter("action"))) {
            return problem(request, response, session, filters, 400, "미리보기 또는 반영을 선택해 주세요.");
        }

        Preview preview = null;
        try (Connection connection = ProductQueries.connect()) {
            try {
                Stock before = dao.find(connection, filters.store, filters.product, confirm);
                if (before == null || before.version != adjustment.version) {
                    connection.rollback();
                    return problem(request, response, session, filters, 409,
                            "재고가 변경됐거나 취급이 종료됐습니다. 현재 재고를 다시 확인해 주세요.");
                }
                int after = adjustment.change.after(before.quantity, adjustment.amount);
                if (confirm) {
                    dao.update(connection, filters.store, before, after, adjustment.change,
                            adjustment.reason, session.getAttribute("member_id").toString());
                } else {
                    preview = new Preview(before, after, adjustment);
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, filters, 400, error.getMessage());
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }

        if (confirm) {
            session.setAttribute(SAVED, filters.store);
            return "redirect:" + ProductView.rawUrl("", MAPPING, filters.parameters());
        }
        return show(request, response, session, filters, preview);
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Filters filters, Preview preview) {
        try (Connection connection = ProductQueries.connect()) {
            List<Location> locations = dao.locations(connection);
            Location selected = locations.stream().filter(location -> location.id.equals(filters.store)
                    && (filters.city.isEmpty() || location.city.equals(filters.city))).findFirst().orElse(null);
            if (!filters.store.isEmpty() && selected == null) {
                return blocked(request, response, 400, "선택한 지역의 매장을 다시 골라 주세요.");
            }
            if (selected == null) {
                selected = locations.stream().filter(location -> filters.city.isEmpty() || filters.city.equals(location.city))
                        .findFirst().orElse(null);
            }
            String selectedId = selected == null ? "" : selected.id;
            if (preview != null && !preview.stock.id.equals(filters.product)) {
                throw new IllegalStateException("Preview stock does not match the selection");
            }
            List<Stock> inventory = selected == null || filters.history ? List.of()
                    : dao.inventory(connection, selectedId, filters.keyword, filters.state, PAGE_SIZE + 1, filters.offset());
            List<History> history = selected == null || !filters.history ? List.of()
                    : dao.history(connection, selectedId, filters.keyword, PAGE_SIZE + 1, filters.offset());
            Stock editing = selected == null || filters.history || filters.product.isEmpty() ? null
                    : preview == null ? dao.find(connection, selectedId, filters.product, false) : preview.stock;
            request.setAttribute("storeInventoryPage", new Page(request.getContextPath(), filters, locations, selected,
                    inventory, history, editing, preview));
            request.setAttribute("storeInventoryCsrf", AdminProductController.token(session));
            if (selectedId.equals(session.getAttribute(SAVED))) {
                request.setAttribute("storeInventorySaved", true);
                session.removeAttribute(SAVED);
            }
            connection.commit();
            return VIEW;
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
    }

    private String problem(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Filters filters, int status, String message) {
        response.setStatus(status);
        request.setAttribute("storeInventoryError", message);
        return show(request, response, session, filters, null);
    }

    private static void initialize(HttpServletRequest request) {
        request.setAttribute("storeInventoryReady", true);
        request.setAttribute("adminSection", "stores");
    }

    private static String blocked(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("storeInventoryError", message);
        return VIEW;
    }

    private static String unavailable(HttpServletRequest request, HttpServletResponse response, Exception error) {
        request.getServletContext().log("매장 재고 처리 실패", error);
        return blocked(request, response, 500, "매장 재고를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static final class Filters {
        final String city;
        final String store;
        final String keyword;
        final String product;
        final State state;
        final int page;
        final boolean history;

        Filters(HttpServletRequest request) {
            city = ProductQueries.text(request, "city", 20);
            if (!CITIES.contains(city)) throw new IllegalArgumentException("매장 지역");
            store = ProductQueries.text(request, "store", 40);
            keyword = ProductQueries.text(request, "q", 100);
            product = ProductQueries.text(request, "product", 100);
            String stateValue = ProductQueries.text(request, "state", 20);
            state = stateValue.isEmpty() ? State.ALL : State.valueOf(stateValue);
            page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            history = "history".equals(request.getParameter("mode"));
        }

        int offset() { return (page - 1) * PAGE_SIZE; }

        Map<String, String> parameters() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("city", city);
            params.put("store", store);
            params.put("q", keyword);
            params.put("state", state == State.ALL || history ? "" : state.name());
            params.put("mode", history ? "history" : "");
            params.put("page", page > 1 ? Integer.toString(page) : "");
            return params;
        }
    }

    public static final class Adjustment {
        public final Change change;
        public final int amount;
        public final long version;
        public final String reason;

        private Adjustment(Change change, int amount, long version, String reason) {
            this.change = change;
            this.amount = amount;
            this.version = version;
            this.reason = reason;
        }

        private static Adjustment read(HttpServletRequest request) {
            try {
                Change change = Change.valueOf(ProductQueries.text(request, "change", 12));
                Integer amount = ProductQueries.integer(request, "amount", null, 0, Integer.MAX_VALUE);
                long version = Long.parseLong(ProductQueries.text(request, "version", 19));
                String reason = ProductQueries.text(request, "reason", 255);
                if (amount == null || version < 0 || reason.isEmpty()) throw new IllegalArgumentException();
                return new Adjustment(change, amount, version, reason);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("변경 유형, 수량, 사유를 확인해 주세요.", error);
            }
        }
    }

    public static final class Preview {
        public final Stock stock;
        public final int after;
        public final Adjustment adjustment;

        private Preview(Stock stock, int after, Adjustment adjustment) {
            this.stock = stock;
            this.after = after;
            this.adjustment = adjustment;
        }
    }

    public static final class Page {
        public final String context;
        public final String city;
        public final String store;
        public final String keyword;
        public final State state;
        public final int page;
        public final boolean showingHistory;
        public final List<Location> locations;
        public final Location selected;
        public final List<Stock> inventory;
        public final List<History> history;
        public final Stock editing;
        public final Preview preview;
        public final Map<String, String> filters;
        public final String previous;
        public final String next;
        public final String inventoryUrl;
        public final String historyUrl;

        private Page(String context, Filters query, List<Location> locations, Location selected,
                List<Stock> inventory, List<History> history, Stock editing, Preview preview) {
            this.context = context;
            this.city = query.city;
            this.store = selected == null ? "" : selected.id;
            this.keyword = query.keyword;
            this.state = query.state;
            this.page = query.page;
            this.showingHistory = query.history;
            this.locations = List.copyOf(locations);
            this.selected = selected;
            this.inventory = firstPage(inventory);
            this.history = firstPage(history);
            this.editing = editing;
            this.preview = preview;
            Map<String, String> params = query.parameters();
            params.put("store", store);
            filters = Map.copyOf(params);
            previous = page > 1 ? url("page", Integer.toString(page - 1)) : null;
            next = Math.max(inventory.size(), history.size()) > PAGE_SIZE ? url("page", Integer.toString(page + 1)) : null;
            params.remove("page");
            inventoryUrl = ProductView.url(context, ROUTE, ProductView.with(params, "mode", ""));
            params.remove("state");
            historyUrl = ProductView.url(context, ROUTE, ProductView.with(params, "mode", "history"));
        }

        public String editUrl(String id) { return url("product", id); }

        public String regionUrl(String region) {
            return ProductView.url(context, ROUTE, Map.of("city", region, "mode", showingHistory ? "history" : ""));
        }

        private String url(String key, String value) {
            return ProductView.url(context, ROUTE, ProductView.with(filters, key, value));
        }

        private static <T> List<T> firstPage(List<T> rows) {
            return List.copyOf(rows.subList(0, Math.min(rows.size(), PAGE_SIZE)));
        }
    }
}
