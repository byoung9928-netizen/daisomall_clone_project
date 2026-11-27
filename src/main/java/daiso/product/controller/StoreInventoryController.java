package daiso.product.controller;

import com.google.gson.JsonObject;
import daiso.order.db.PickupStockTransaction;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import daiso.admin.report.AdminReport;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Assortment;
import daiso.product.dao.StoreInventoryDAO.AssortmentFilter;
import daiso.product.dao.StoreInventoryDAO.Change;
import daiso.product.dao.StoreInventoryDAO.History;
import daiso.product.dao.StoreInventoryDAO.Location;
import daiso.product.dao.StoreInventoryDAO.InventoryFilter;
import daiso.product.dao.StoreInventoryDAO.State;
import daiso.product.dao.StoreInventoryDAO.Stock;
import daiso.product.dao.StoreInventoryDAO.Summary;

@DaisoController
public final class StoreInventoryController {
    public static final String ROUTE = "/app/admin/stores";
    private static final String MAPPING = "/admin/stores";
    private static final String VIEW = "admin/stores";
    private static final String SAVED = StoreInventoryController.class.getName() + ".saved";
    private static final String ADJUST_PREVIEWS = StoreInventoryController.class.getName() + ".adjustPreviews";
    private static final int ADJUST_PREVIEW_LIMIT = 8;
    private static final String UNLIST = StoreInventoryController.class.getName() + ".unlist";
    private static final int PAGE_SIZE = 20;
    private static final Set<String> CITIES = Set.of("", "서울", "부산");

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

    @DaisoGetMapping(MAPPING + "/suggestions")
    public String suggestions(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        return AdminProductSuggestions.respond(request, response, (connection, items) -> {
            Filters filters = new Filters(request);
            if (filters.history || filters.assortment || filters.keyword.isEmpty()) return;
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            Location store = filters.selectStore(dao.locations(connection));
            if (store == null) return;
            for (StoreInventoryDAO.StockSuggestion match : dao.suggestInventory(connection, filters.inventory(store.id, false))) {
                Stock stock = match.stock;
                AdminProductSuggestions.add(items, request.getContextPath(), stock.id, stock.name, stock.code,
                        stock.image, match.price, stock.quantity, stock.state().label);
            }
        });
    }

    @DaisoGetMapping(MAPPING + "/count-preview")
    public String countPreview(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) return StoreStocktake.respond(response, new JsonObject(), 403);
        JsonObject result = new JsonObject();
        int status = 200;
        try (Connection connection = ProductQueries.connect()) {
            Filters filters = new Filters(request);
            if (filters.store.isEmpty() || filters.product.isEmpty()) throw new IllegalArgumentException();
            if (!StoreStocktake.enabled(connection)) throw new StoreStocktake.Conflict("실사 저장 전환이 아직 활성화되지 않았습니다.");
            String member = session.getAttribute("member_id").toString();
            StoreStocktake.requireAdministrator(connection, member, false);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            Location selected = filters.selectStore(dao.locations(connection));
            Stock stock = selected == null ? null : dao.find(connection, selected.id, filters.product, false);
            if (stock == null || !stock.carried || !stock.id.equals(filters.product)) {
                throw new StoreStocktake.Conflict("취급 중인 매장 상품을 다시 선택해 주세요.");
            }
            StoreStocktake.Baseline baseline = StoreStocktake.issue(connection, session, member, "ADMIN", selected.id, stock);
            StoreStocktake.present(result, selected.id, stock, baseline.orders.allocated, baseline);
            connection.commit();
        } catch (StoreStocktake.Conflict | StoreInventoryCountPreview.UncertainPickup error) {
            status = 409;
            result.addProperty("message", error.getMessage());
        } catch (IllegalArgumentException error) {
            status = 400;
            result.addProperty("message", "실사할 매장과 상품을 다시 선택해 주세요.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 실사 기준 조회 실패", error);
            status = 503;
            result.addProperty("message", "현재 픽업 할당 수량을 조회할 수 없습니다.");
        }
        return StoreStocktake.respond(response, result, status);
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
            if (filters.store.isEmpty() || filters.product.isEmpty() || filters.history || filters.assortment) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            return blocked(request, response, 400, "점포와 상품을 다시 선택해 주세요.");
        }

        rememberDraft(request, filters.product, "adjust");
        if ("revise".equals(request.getParameter("action"))) {
            return show(request, response, session, filters, null);
        }
        boolean confirm = "save".equals(request.getParameter("action"));
        if (!confirm && !"preview".equals(request.getParameter("action"))) {
            return problem(request, response, session, filters, 400, "미리보기 또는 반영을 선택해 주세요.");
        }

        Adjustment adjustment;
        Preview confirmed = null;
        try {
            if (confirm) confirmed = takeAdjustment(session, filters, ProductQueries.text(request, "adjustReceipt", 36));
            adjustment = confirmed == null ? Adjustment.read(request) : confirmed.adjustment;
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, filters, confirm ? 409 : 400, error.getMessage());
        }

        Preview preview = null;
        try (Connection connection = ProductQueries.connect()) {
            boolean count = adjustment.change == Change.COUNT;
            if (count && confirm) StoreStocktake.beginCurrentRead(connection);
            boolean stocktake = count && StoreStocktake.enabled(connection);
            if (count && !stocktake && !adjustment.countBaseline.isEmpty()) {
                throw new StoreStocktake.Conflict("실사 저장 전환이 활성화되지 않았습니다. 수량을 다시 확인해 주세요.");
            }
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            try {
                if (confirm && !dao.lockStore(connection, filters.store, filters.city)) {
                    throw new IllegalArgumentException("선택한 지역의 매장을 다시 골라 주세요.");
                }
                Stock before = dao.find(connection, filters.store, filters.product, confirm);
                if (before == null || !filters.product.equals(before.id) || !before.carried
                        || before.version != adjustment.version || (confirmed != null && before.quantity != confirmed.stock.quantity)) {
                    connection.rollback();
                    return problem(request, response, session, filters, 409,
                            "미리보기 이후 재고가 변경되었거나 취급이 종료됐습니다. 최신 재고로 미리보기를 다시 만들어 주세요.");
                }
                request.setAttribute("storeFieldError", "amount");
                String member = session.getAttribute("member_id").toString();
                StoreStocktake.Baseline baseline = null;
                int after;
                if (stocktake) {
                    StoreStocktake.requireAdministrator(connection, member, confirm);
                    baseline = StoreStocktake.find(session, adjustment.countBaseline, member, "ADMIN",
                            filters.store, filters.product, confirm);
                    after = StoreStocktake.compare(connection, baseline, before,
                            StoreStocktake.revision(connection, filters.store), adjustment.amount);
                } else {
                    after = adjustment.change.after(before.quantity, adjustment.amount);
                }
                request.removeAttribute("storeFieldError");
                if (!confirm) {
                    preview = new Preview(filters.store, before, after, adjustment, baseline);
                } else if (baseline != null) {
                    StoreStocktake.save(connection, dao, baseline, before, adjustment.amount, after, adjustment.reason);
                } else {
                    dao.update(connection, filters.store, before, after, adjustment.change, adjustment.reason, member);
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
        } catch (StoreStocktake.Conflict | StoreInventoryCountPreview.UncertainPickup error) {
            return problem(request, response, session, filters, 409, error.getMessage());
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, filters, 400, error.getMessage());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 재고 조정 실패", error);
            return problem(request, response, session, filters, 500,
                    "재고를 저장하지 못했습니다. 입력한 내용을 확인한 뒤 다시 시도해 주세요.");
        }

        if (confirm) {
            return savedRedirect(session, filters, confirmed != null && confirmed.after == confirmed.stock.quantity
                    ? "변경된 판매 가능 재고가 없습니다." : "매장 재고를 조정했습니다.");
        }
        rememberAdjustment(session, preview);
        return show(request, response, session, filters, preview);
    }

    @SuppressWarnings("unchecked")
    private static void rememberAdjustment(HttpSession session, Preview preview) {
        synchronized (session) {
            Map<String, Preview> previews = (Map<String, Preview>) session.getAttribute(ADJUST_PREVIEWS);
            if (previews == null) {
                previews = new LinkedHashMap<>();
                session.setAttribute(ADJUST_PREVIEWS, previews);
            }
            if (previews.size() >= ADJUST_PREVIEW_LIMIT) previews.remove(previews.keySet().iterator().next());
            previews.put(preview.receipt, preview);
        }
    }

    @SuppressWarnings("unchecked")
    private static Preview takeAdjustment(HttpSession session, Filters filters, String receipt) {
        synchronized (session) {
            Map<String, Preview> previews = (Map<String, Preview>) session.getAttribute(ADJUST_PREVIEWS);
            Preview preview = previews == null ? null : previews.get(receipt);
            if (preview == null || !preview.store.equals(filters.store) || !preview.stock.id.equals(filters.product)
                    || preview.expires < System.currentTimeMillis()) {
                throw new IllegalArgumentException("미리보기가 만료되었거나 이미 처리되었습니다. 최신 재고로 미리보기를 다시 만들어 주세요.");
            }
            return previews.remove(receipt);
        }
    }

    @DaisoPostMapping(MAPPING + "/threshold")
    public String saveThreshold(HttpServletRequest request, HttpServletResponse response)
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
            if (filters.store.isEmpty() || filters.product.isEmpty() || filters.history || filters.assortment) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            return blocked(request, response, 400, "점포와 상품을 다시 선택해 주세요.");
        }

        rememberDraft(request, filters.product, "threshold");
        int threshold;
        long version;
        try {
            request.setAttribute("storeFieldError", "threshold");
            Integer entered = ProductQueries.integer(request, "threshold", null, 1, Integer.MAX_VALUE);
            if (entered == null) throw new IllegalArgumentException();
            threshold = entered;
            request.removeAttribute("storeFieldError");
            version = Long.parseLong(ProductQueries.text(request, "version", 19));
            if (version < 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, filters, 400, "1개 이상의 부족 기준을 입력하고 상품을 확인해 주세요.");
        }

        try (Connection connection = ProductQueries.connect()) {
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            if (!dao.thresholdsEnabled()) {
                return problem(request, response, session, filters, 503, "매장 재고 부족 기준 마이그레이션을 먼저 적용해 주세요.");
            }
            try {
                if (!dao.lockStore(connection, filters.store, filters.city)) {
                    throw new IllegalArgumentException("선택한 지역의 매장을 다시 골라 주세요.");
                }
                Stock before = dao.find(connection, filters.store, filters.product, true);
                if (before == null || !before.carried || before.version != version) {
                    connection.rollback();
                    return problem(request, response, session, filters, 409,
                            "재고 또는 부족 기준이 바뀌었거나 취급이 종료됐습니다. 현재 정보를 다시 확인해 주세요.");
                }
                if (before.threshold == threshold) {
                    request.setAttribute("storeFieldError", "threshold");
                    throw new IllegalArgumentException("새 부족 기준이 현재 기준과 같습니다.");
                }
                dao.updateThreshold(connection, filters.store, before, threshold,
                        session.getAttribute("member_id").toString());
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
            request.getServletContext().log("매장 재고 부족 기준 저장 실패", error);
            return problem(request, response, session, filters, 500, "부족 기준을 저장하지 못했습니다. 다시 시도해 주세요.");
        }
        return savedRedirect(session, filters, "매장 재고 부족 기준이 저장되었습니다.");
    }

    @DaisoPostMapping(MAPPING + "/assortment")
    public String changeAssortment(HttpServletRequest request, HttpServletResponse response)
            throws UnsupportedEncodingException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        initialize(request);
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null || !AdminProductController.validToken(request, session)) {
            return blocked(request, response, 403, "관리자 권한 또는 요청 확인 정보가 유효하지 않습니다.");
        }

        Filters filters;
        Assortment action;
        long expectedVersion;
        int quantity;
        String reason;
        try {
            filters = new Filters(request);
            rememberDraft(request, filters.product, "assortment");
            action = Assortment.valueOf(ProductQueries.text(request, "operation", 12));
            expectedVersion = Long.parseLong(ProductQueries.text(request, "version", 19));
            reason = ProductQueries.text(request, "reason", 255);
            Integer entered = action == Assortment.UNLIST ? Integer.valueOf(0)
                    : ProductQueries.integer(request, "amount", null, 0, Integer.MAX_VALUE);
            if (filters.store.isEmpty() || filters.product.isEmpty() || filters.history || reason.isEmpty()
                    || entered == null || (action == Assortment.REGISTER ? expectedVersion != -1 : expectedVersion < 0)) {
                throw new IllegalArgumentException();
            }
            quantity = entered;
        } catch (IllegalArgumentException error) {
            return assortmentInputProblem(request, response, session);
        }

        boolean confirm = "save".equals(request.getParameter("action"));
        if (!confirm && (action != Assortment.UNLIST || !"preview".equals(request.getParameter("action")))) {
            return problem(request, response, session, filters, 400, "취급 해제 확인 단계를 다시 진행해 주세요.");
        }
        if (confirm && action == Assortment.UNLIST
                && !validUnlist(request, session, filters, expectedVersion, reason)) {
            return problem(request, response, session, filters, 409, "취급 해제 내용을 다시 확인해 주세요.");
        }
        try (Connection connection = ProductQueries.connect()) {
            if (confirm && action == Assortment.UNLIST) StoreStocktake.beginCurrentRead(connection);
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            try {
                if (confirm && !dao.lockStore(connection, filters.store, filters.city)) {
                    throw new IllegalArgumentException("선택한 지역의 매장을 다시 골라 주세요.");
                }
                Stock before = dao.find(connection, filters.store, filters.product, confirm);
                if (action == Assortment.REGISTER && before == null) {
                    before = dao.findProduct(connection, filters.product);
                }
                validateAssortment(before, action, expectedVersion);
                if (confirm && action == Assortment.UNLIST && PickupStockTransaction.enabled(connection)) {
                    StoreStocktake.requireAdministrator(connection, session.getAttribute("member_id").toString(), true);
                    if (StoreInventoryCountPreview.allocated(connection, filters.store, before.id) > 0) {
                        throw new StoreStocktake.Conflict("미수령 픽업 할당이 있는 상품은 취급을 종료할 수 없습니다.");
                    }
                }
                if (confirm) {
                    dao.changeAssortment(connection, filters.store, before, quantity, action, reason,
                            session.getAttribute("member_id").toString());
                } else {
                    String token = UUID.randomUUID().toString();
                    session.setAttribute(UNLIST, unlistKey(filters, expectedVersion, reason, token));
                    request.setAttribute("storeUnlistConfirmation", before.id);
                    request.setAttribute("storeUnlistToken", token);
                    request.setAttribute("storeUnlistVersion", expectedVersion);
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
        } catch (IllegalArgumentException | StoreInventoryCountPreview.UncertainPickup error) {
            return problem(request, response, session, filters, 409, error.getMessage());
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("매장 취급 상태 저장 실패", error);
            return problem(request, response, session, filters, 500,
                    "취급 상태를 저장하지 못했습니다. 입력한 내용을 확인한 뒤 다시 시도해 주세요.");
        }

        if (!confirm) return show(request, response, session, filters, null);
        return savedRedirect(session, filters, action.label + "을 완료했습니다.");
    }

    private static String savedRedirect(HttpSession session, Filters filters, String message) {
        String receipt = UUID.randomUUID().toString();
        synchronized (session) {
            session.setAttribute(SAVED, Map.of("receipt", receipt, "store", filters.store,
                    "product", filters.product, "message", message));
        }
        String target = ProductView.rawUrl("", MAPPING, ProductView.with(filters.parameters(), "saved", receipt));
        return "redirect:" + target + "#store-inventory-table";
    }

    private static void showSaved(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            String store, List<Stock> stock) {
        synchronized (session) {
            Object value = session.getAttribute(SAVED);
            if (!(value instanceof Map<?, ?>)) return;
            Map<?, ?> receipt = (Map<?, ?>) value;
            if (!receipt.get("receipt").equals(request.getParameter("saved")) || !receipt.get("store").equals(store)) return;
            session.removeAttribute(SAVED);
            response.setHeader("Cache-Control", "no-store");
            request.setAttribute("storeInventorySaved", receipt.get("message"));
            if (stock.stream().limit(PAGE_SIZE).anyMatch(item -> item.id.equals(receipt.get("product")))) {
                request.setAttribute("storeInventorySavedProduct", receipt.get("product"));
            }
        }
    }

    private static boolean validUnlist(HttpServletRequest request, HttpSession session, Filters filters,
            long version, String reason) {
        String token = request.getParameter("unlistToken");
        return token != null && token.length() == 36 && unlistKey(filters, version, reason, token).equals(session.getAttribute(UNLIST));
    }

    private static Map<String, String> unlistKey(Filters filters, long version, String reason, String token) {
        return Map.of("store", filters.store, "product", filters.product, "version", Long.toString(version),
                "reason", reason, "token", token);
    }

    private String assortmentInputProblem(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        try {
            return problem(request, response, session, new Filters(request), 400,
                    "매장, 상품, 수량과 변경 사유를 확인해 주세요.");
        } catch (IllegalArgumentException error) {
            return blocked(request, response, 400, "조회 조건을 다시 확인해 주세요.");
        }
    }

    private static void rememberDraft(HttpServletRequest request, String product, String kind) {
        Map<String, String> draft = new LinkedHashMap<>();
        draft.put("product", product);
        draft.put("kind", kind);
        for (String field : List.of("change", "operation", "amount", "reason", "threshold")) {
            String value = request.getParameter(field);
            if (value != null) draft.put(field, value.substring(0, Math.min(value.length(), 1000)));
        }
        request.setAttribute("storeDraft", draft);
    }

    private static void validateAssortment(Stock before, Assortment action, long expectedVersion) {
        if (before == null || before.version != expectedVersion || before.carried != (action == Assortment.UNLIST)) {
            throw new IllegalArgumentException("상품의 취급 상태가 바뀌었습니다. 목록을 확인한 후 다시 시도해 주세요.");
        }
        if (before.quantity != 0) {
            throw new IllegalArgumentException("재고를 0개로 처리한 뒤 취급 상태를 변경해 주세요.");
        }
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Filters filters, Preview preview) {
        try (Connection connection = ProductQueries.connect()) {
            StoreInventoryDAO dao = StoreInventoryDAO.forInventory(connection);
            List<Location> locations = dao.locations(connection);
            request.setAttribute("inventoryBatchLocations", locations);
            if (!filters.compareQuery.isEmpty()) {
                request.setAttribute("storeCompareCandidates", dao.compareCandidates(connection, filters.compareQuery));
            }
            if (!filters.compareProduct.isEmpty()) {
                List<StoreInventoryDAO.ComparedStock> full = dao.compare(connection, filters.compareProduct, "", filters.compareSort);
                request.setAttribute("storeCompareTotal", full.size());
                request.setAttribute("storeCompareCarried", full.stream().filter(row -> row.quantity != null).count());
                request.setAttribute("storeCompareAvailable", full.stream().filter(row -> row.quantity != null && row.quantity > 0).count());
                request.setAttribute("storeCompareUnits", full.stream().filter(row -> row.quantity != null).mapToLong(row -> row.quantity).sum());
                request.setAttribute("storeCompareRows", full.stream().filter(row -> filters.compareCity.isEmpty()
                        || filters.compareCity.equals(row.city)).collect(java.util.stream.Collectors.toList()));
            }
            Location selected;
            try {
                selected = filters.selectStore(locations);
            } catch (IllegalArgumentException error) {
                return blocked(request, response, 400, "선택한 지역의 매장을 다시 골라 주세요.");
            }
            String selectedId = selected == null ? "" : selected.id;
            if (selected != null && !filters.compareProduct.isEmpty()) {
                int days = ProductQueries.integer(request, "trendDays", 30, 1, 90);
                request.setAttribute("stockTrend", AdminStockTrends.load(connection, filters.compareProduct, selectedId, days));
            }
            if (preview != null && !preview.stock.id.equals(filters.product)) {
                throw new IllegalStateException("Preview stock does not match the selection");
            }
            InventoryFilter inventoryFilter = filters.inventory(selectedId, true);
            long total = selected == null ? 0
                    : filters.history ? dao.countHistory(connection, selectedId, filters.keyword, filters.historyFilter)
                    : filters.assortment ? dao.countAssortment(connection, selectedId, filters.keyword, filters.assortmentFilter)
                    : dao.countInventory(connection, inventoryFilter);
            int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = Math.min(filters.page, pages);
            if (page != filters.page && "GET".equals(request.getMethod())) {
                Map<String, String> params = filters.parameters();
                params.put("store", selectedId);
                params.put("page", Integer.toString(page));
                params.put("product", filters.product);
                String receipt = request.getParameter("saved");
                boolean saved = receipt != null && receipt.matches("[0-9a-f-]{36}");
                if (saved) params.put("saved", receipt);
                String batchReceipt = AdminInventoryBatchController.savedReceipt(request);
                if (batchReceipt != null) params.put(AdminInventoryBatchController.SAVED_PARAMETER, batchReceipt);
                connection.commit();
                String anchor = batchReceipt != null ? "#inventory-batch-result" : saved ? "#store-inventory-table" : "";
                return "redirect:" + ProductView.rawUrl("", MAPPING, params) + anchor;
            }
            int offset = (page - 1) * PAGE_SIZE;
            Summary summary = selected == null ? null : dao.summary(connection, selectedId);
            List<Stock> inventory = List.of();
            if (selected != null && !filters.history) {
                inventory = filters.assortment
                        ? dao.assortment(connection, selectedId, filters.keyword, filters.assortmentFilter, PAGE_SIZE, offset)
                        : dao.inventory(connection, inventoryFilter, filters.sort, PAGE_SIZE, offset);
            }
            List<History> history = selected == null || !filters.history ? List.of()
                    : dao.history(connection, selectedId, filters.keyword, filters.historyFilter, PAGE_SIZE, offset);
            Stock editing = selected == null || filters.history || filters.assortment || filters.product.isEmpty()
                    ? null : preview == null ? dao.find(connection, selectedId, filters.product, false) : preview.stock;
            if (editing != null && !editing.carried) editing = null;
            Map<String, String> categories = filters.history || filters.assortment ? Map.of()
                    : new ProductDAO().findAdminCategories(connection, false);
            request.setAttribute("storeInventoryPage", new Page(request.getContextPath(), filters, locations, selected,
                    categories,
                    inventory, history, editing, preview, summary, page, pages, total, dao.thresholdsEnabled()));
            request.setAttribute("storeInventoryCsrf", AdminProductController.token(session));
            if (editing != null) request.setAttribute("storeStocktakeEnabled", StoreStocktake.enabled(connection));
            connection.commit();
            showSaved(request, response, session, selectedId, inventory);
            request.setAttribute("inventoryBatchNotice", AdminInventoryBatchController.takeNotice(request, session, MAPPING));
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
        if (error instanceof SQLException && ((SQLException) error).getErrorCode() == 1054
                && "42S22".equals(((SQLException) error).getSQLState())) {
            return blocked(request, response, 503, "매장 취급 상태 마이그레이션을 확인한 뒤 다시 시도해 주세요.");
        }
        request.getServletContext().log("매장 재고 처리 실패", error);
        return blocked(request, response, 500, "매장 재고를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
 // ===== 매장별 재고 엑셀: 선택한 매장의 전체 검색 결과 =====
    public static AdminReport exportInventory(HttpServletRequest request)
            throws SQLException, NamingException {
        Filters filters = new Filters(request);
        AdminReport report = new AdminReport(
                "매장별 재고",
                List.of("매장명", "상품 ID", "품번", "상품명", "재고 수량", "부족 기준", "재고 상태"),
                "재고 상태별 상품 수",
                "재고 상태",
                "상품 수",
                new LinkedHashMap<>()
        );

        try (Connection connection = ProductQueries.connect()) {
            StoreInventoryDAO inventoryDao = StoreInventoryDAO.forInventory(connection);
            Location selected = filters.selectStore(inventoryDao.locations(connection));
            if (selected == null) return report;

            // 화면과 같은 재고 조회를 사용하고 첫 행부터 전체 결과를 가져온다.
            List<Stock> rows = inventoryDao.inventory(connection, filters.inventory(selected.id, true),
                    filters.sort, Integer.MAX_VALUE, 0);

            for (Stock stock : rows) {
                String state = stock.state().label;
                report.rows.add(new Object[] {
                        selected.name, stock.id, stock.code,
                        stock.name, stock.quantity, stock.threshold, state
                });
                report.chart.merge(state, 1L, Long::sum);
            }

            connection.commit();
        }

        return report;
    }

    private static final class Filters {
        final String city;
        final String store;
        final String keyword;
        final String category;
        final String selectedProductId;
        final String product;
        final State state;
        final int page;
        final boolean history;
        final boolean assortment;
        final AssortmentFilter assortmentFilter;
        final String storeQuery;
        final StoreInventoryDAO.Sort sort;
        final String compareQuery;
        final String compareProduct;
        final String compareCity;
        final StoreInventoryDAO.Sort compareSort;
        final String historyPeriod;
        final StoreInventoryDAO.HistoryFilter historyFilter;

        Filters(HttpServletRequest request) {
            city = ProductQueries.text(request, "city", 20);
            if (!CITIES.contains(city)) throw new IllegalArgumentException("매장 지역");
            store = ProductQueries.text(request, "store", 40);
            keyword = java.text.Normalizer.normalize(ProductQueries.text(request, "q", 100), java.text.Normalizer.Form.NFC);
            category = ProductQueries.text(request, "category", 100);
            selectedProductId = ProductQueries.text(request, "selectedProductId", 100);
            product = ProductQueries.text(request, "product", 100);
            String stateValue = ProductQueries.text(request, "state", 20);
            state = stateValue.isEmpty() ? State.ALL : State.valueOf(stateValue);
            page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            storeQuery = ProductQueries.text(request, "storeQuery", 40);
            sort = readSort(request, "sort", StoreInventoryDAO.Sort.NAME);
            compareQuery = ProductQueries.text(request, "compareQ", 100);
            compareProduct = ProductQueries.text(request, "compareProduct", 100);
            compareCity = ProductQueries.text(request, "compareCity", 20);
            if (!CITIES.contains(compareCity)) throw new IllegalArgumentException("매장 비교 지역");
            compareSort = readSort(request, "compareSort", StoreInventoryDAO.Sort.REGION);
            String mode = request.getParameter("mode");
            history = "history".equals(mode);
            assortment = "assortment".equals(mode);
            historyPeriod = history ? ProductQueries.text(request, "historyPeriod", 10) : "";
            AdminReviewController.Period period = historyPeriod.isEmpty() ? AdminReviewController.Period.ALL
                    : AdminReviewController.Period.valueOf(historyPeriod);
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            java.time.LocalDate start = period == AdminReviewController.Period.CUSTOM
                    ? historyDate(request, "historyStart") : period.start(today);
            java.time.LocalDate end = period == AdminReviewController.Period.CUSTOM ? historyDate(request, "historyEnd") : today;
            if (start != null && start.isAfter(end)) throw new IllegalArgumentException("이력 날짜");
            String type = history ? ProductQueries.text(request, "historyType", 14) : "";
            if (!StoreInventoryDAO.HistoryFilter.TYPES.contains(type)) throw new IllegalArgumentException("이력 유형");
            historyFilter = new StoreInventoryDAO.HistoryFilter(start == null ? null : start.atStartOfDay(),
                    start == null ? null : end.plusDays(1).atStartOfDay(), type,
                    history ? ProductQueries.text(request, "historyActor", 100) : "",
                    history ? ProductQueries.text(request, "historyReference", 36) : "",
                    history && "oldest".equals(request.getParameter("historyOrder")));
            String scope = assortment ? ProductQueries.text(request, "assortment", 15) : "";
            assortmentFilter = scope.isEmpty() ? AssortmentFilter.UNREGISTERED : AssortmentFilter.valueOf(scope);
        }

        Location selectStore(List<Location> locations) {
            Location selected = locations.stream()
                    .filter(location -> city.isEmpty() || location.city.equals(city))
                    .filter(location -> store.isEmpty() || location.id.equals(store))
                    .findFirst().orElse(null);
            if (selected == null && !store.isEmpty()) throw new IllegalArgumentException();
            return selected;
        }

        InventoryFilter inventory(String storeId, boolean exact) {
            return new InventoryFilter(storeId, keyword, category, exact ? selectedProductId : "", state);
        }

        private static java.time.LocalDate historyDate(HttpServletRequest request, String name) {
            try {
                java.time.LocalDate value = java.time.LocalDate.parse(ProductQueries.text(request, name, 10));
                if (value.getYear() < 1000 || value.isAfter(java.time.LocalDate.of(9999, 12, 30))) {
                    throw new IllegalArgumentException("이력 날짜");
                }
                return value;
            } catch (java.time.format.DateTimeParseException error) {
                throw new IllegalArgumentException("이력 날짜", error);
            }
        }

        private static StoreInventoryDAO.Sort readSort(HttpServletRequest request, String name, StoreInventoryDAO.Sort fallback) {
            String value = ProductQueries.text(request, name, 12);
            return value.isEmpty() ? fallback : StoreInventoryDAO.Sort.valueOf(value);
        }

        Map<String, String> parameters() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("city", city);
            params.put("store", store);
            params.put("q", keyword);
            if (!history && !assortment) {
                params.put("category", category);
                params.put("selectedProductId", selectedProductId);
            }
            params.put("storeQuery", storeQuery);
            params.put("sort", sort.name());
            params.put("compareQ", compareQuery);
            params.put("compareProduct", compareProduct);
            params.put("compareCity", compareCity);
            params.put("compareSort", compareSort.name());
            if (history) {
                params.put("historyPeriod", historyPeriod);
                params.put("historyStart", historyFilter.start == null ? "" : historyFilter.start.toLocalDate().toString());
                params.put("historyEnd", historyFilter.end == null ? "" : historyFilter.end.toLocalDate().minusDays(1).toString());
                params.put("historyType", historyFilter.type);
                params.put("historyActor", historyFilter.actor);
                params.put("historyReference", historyFilter.reference);
                params.put("historyOrder", historyFilter.oldest ? "oldest" : "");
            }
            params.put("state", state == State.ALL || history || assortment ? "" : state.name());
            params.put("mode", history ? "history" : assortment ? "assortment" : "");
            params.put("assortment", assortment ? assortmentFilter.name() : "");
            params.put("page", page > 1 ? Integer.toString(page) : "");
            return params;
        }
    }

    public static final class Adjustment {
        public final Change change;
        public final int amount;
        public final long version;
        public final String reason;
        private final String countBaseline;

        private Adjustment(Change change, int amount, long version, String reason, String countBaseline) {
            this.change = change;
            this.amount = amount;
            this.version = version;
            this.reason = reason;
            this.countBaseline = countBaseline;
        }

        private static Adjustment read(HttpServletRequest request) {
            try {
                request.setAttribute("storeFieldError", "change");
                Change change = Change.valueOf(ProductQueries.text(request, "change", 12));
                request.setAttribute("storeFieldError", "amount");
                Integer amount = ProductQueries.integer(request, "amount", null, 0, Integer.MAX_VALUE);
                if (amount == null) throw new IllegalArgumentException();
                request.removeAttribute("storeFieldError");
                long version = Long.parseLong(ProductQueries.text(request, "version", 19));
                if (version < 0) throw new IllegalArgumentException();
                request.setAttribute("storeFieldError", "reason");
                String reason = ProductQueries.text(request, "reason", 255);
                if (reason.isEmpty()) throw new IllegalArgumentException();
                request.removeAttribute("storeFieldError");
                String baseline = ProductQueries.text(request, "countBaseline", 36);
                if (change == Change.COUNT && !baseline.isEmpty() && !request.getParameter("amount").matches("[0-9]{1,10}")) {
                    throw new IllegalArgumentException();
                }
                return new Adjustment(change, amount, version, reason, baseline);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("변경 유형, 수량, 사유를 확인해 주세요.", error);
            }
        }
    }

    public static final class Preview {
        public final String receipt = UUID.randomUUID().toString();
        private final long expires = System.currentTimeMillis() + 600_000;
        private final String store;
        public final Stock stock;
        public final int after;
        public final Adjustment adjustment;
        public final Integer allocated;
        public final String countStarted;

        private Preview(String store, Stock stock, int after, Adjustment adjustment, StoreStocktake.Baseline baseline) {
            this.store = store;
            this.stock = stock;
            this.after = after;
            this.adjustment = adjustment;
            this.allocated = baseline == null ? null : baseline.orders.allocated;
            this.countStarted = baseline == null ? null : baseline.started.format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
        public final boolean showingAssortment;
        public final AssortmentFilter assortmentFilter;
        public final Summary summary;
        public final List<Location> locations;
        public final Location selected;
        public final List<Stock> inventory;
        public final List<History> history;
        public final Stock editing;
        public final Preview preview;
        public final Map<String, String> filters;
        public final long total;
        public final boolean thresholdsEnabled;
        public final InventoryPagination pagination;
        public final String inventoryUrl;
        public final String historyUrl;
        public final String assortmentUrl;
        public final Map<String, String> categories;

        private Page(String context, Filters query, List<Location> locations, Location selected,
                Map<String, String> categories, List<Stock> inventory, List<History> history, Stock editing, Preview preview, Summary summary,
                int page, int pages, long total, boolean thresholdsEnabled) {
            this.context = context;
            this.city = query.city;
            this.store = selected == null ? "" : selected.id;
            this.keyword = query.keyword;
            this.state = query.state;
            this.page = page;
            this.total = total;
            this.thresholdsEnabled = thresholdsEnabled;
            this.showingHistory = query.history;
            this.showingAssortment = query.assortment;
            this.assortmentFilter = query.assortmentFilter;
            this.summary = summary;
            this.locations = List.copyOf(locations);
            this.selected = selected;
            this.categories = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(categories));
            this.inventory = List.copyOf(inventory);
            this.history = List.copyOf(history);
            this.editing = editing;
            this.preview = preview;
            Map<String, String> params = query.parameters();
            params.put("store", store);
            params.put("page", page > 1 ? Integer.toString(page) : "");
            filters = Map.copyOf(params);
            pagination = new InventoryPagination(page, pages, target -> url("page", Integer.toString(target)));
            params.remove("page");
            params.remove("assortment");
            inventoryUrl = ProductView.url(context, ROUTE, ProductView.with(params, "mode", ""));
            params.remove("state");
            params.remove("category");
            params.remove("selectedProductId");
            historyUrl = ProductView.url(context, ROUTE, ProductView.with(params, "mode", "history"));
            params.remove("q");
            assortmentUrl = ProductView.url(context, ROUTE, ProductView.with(params, "mode", "assortment"));
        }

        public String editUrl(String id) { return url("product", id); }
        public String closeEditorUrl() { return ProductView.url(context, ROUTE, filters); }

        public String closeDrawerUrl(String id) {
            boolean rowVisible = inventory.stream().anyMatch(item -> item.id.equals(id));
            return closeEditorUrl() + "#" + (rowVisible ? rowAnchor(id) : "store-inventory-table");
        }

        public static String rowAnchor(String id) {
            return "store-row-" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(id.getBytes(StandardCharsets.UTF_8));
        }
        public String resetUrl() {
            return ProductView.url(context, ROUTE, Map.of("city", city, "store", store,
                    "mode", showingHistory ? "history" : showingAssortment ? "assortment" : ""));
        }

        public String regionUrl(String region) {
            return ProductView.url(context, ROUTE, Map.of("city", region, "mode", showingHistory ? "history" : showingAssortment ? "assortment" : ""));
        }

        public String summaryUrl(State state) {
            return ProductView.url(context, ROUTE,
                    Map.of("city", city, "store", store, "state", state == State.ALL ? "" : state.name()));
        }

        private String url(String key, String value) {
            return ProductView.url(context, ROUTE, ProductView.with(filters, key, value));
        }

    }
}
