package daiso.product.controller;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

final class AdminProductFilter {
    final String keyword;
    final String productId;
    final String category;
    final String status;
    final Stock stock;
    final Integer stockQuantity;
    final Sort sort;
    final boolean onlineInventory;

    private AdminProductFilter(String keyword, String category, String status, Stock stock,
            Integer stockQuantity, Sort sort) {
        this(keyword, "", category, status, stock, stockQuantity, sort, false);
    }

    private AdminProductFilter(String keyword, String productId, String category, String status, Stock stock,
            Integer stockQuantity, Sort sort, boolean onlineInventory) {
        this.onlineInventory = onlineInventory;
        this.keyword = keyword;
        this.productId = productId;
        this.category = category;
        this.status = status;
        this.stock = stock;
        this.stockQuantity = stockQuantity;
        this.sort = sort;
    }

    static AdminProductFilter read(HttpServletRequest request, Map<String, String> statuses) {
        return read(request, statuses, false);
    }

    static AdminProductFilter readSuggestions(HttpServletRequest request, Map<String, String> statuses) {
        return read(request, statuses, true);
    }

    private static AdminProductFilter read(HttpServletRequest request, Map<String, String> statuses, boolean suggestions) {
        String status = ProductQueries.text(request, "filterStatus", 20);
        if (!status.isEmpty() && !statuses.containsKey(status)) throw new IllegalArgumentException();
        String rawStock = ProductQueries.text(request, "filterStock", 20);
        Stock stock = rawStock.isEmpty() ? Stock.ALL : Stock.valueOf(rawStock);
        if (stock.name().startsWith("ONLINE_")) throw new IllegalArgumentException();
        Integer quantity = stock.needsQuantity ? stockQuantity(request) : null;
        String sort = suggestions ? "" : ProductQueries.text(request, "sort", 20);
        if ("SHORTAGE".equals(sort)) throw new IllegalArgumentException();
        return new AdminProductFilter(Normalizer.normalize(ProductQueries.text(request, "query", 120), Normalizer.Form.NFC),
                suggestions ? "" : ProductQueries.text(request, "selectedProductId", 100),
                ProductQueries.text(request, "filterCategory", 100), status,
                stock, quantity, sort.isEmpty() ? Sort.NEWEST : Sort.valueOf(sort), false);
    }

    static AdminProductFilter forInventory(String keyword, String productId, String category, Stock stock, String sort) {
        Sort order = "shortage".equals(sort) ? Sort.SHORTAGE : "low".equals(sort) ? Sort.STOCK_ASC : Sort.NAME;
        return new AdminProductFilter(Normalizer.normalize(keyword, Normalizer.Form.NFC), productId, category, "", stock, null, order, true);
    }

    static AdminProductFilter performanceScope(String category, String productId) {
        return new AdminProductFilter("", productId, category, "", Stock.ALL, null, Sort.NEWEST, false);
    }

    static AdminProductFilter defaults() {
        return new AdminProductFilter("", "", "", Stock.ALL, null, Sort.NEWEST);
    }

    Map<String, String> parameters(int page) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("query", keyword);
        if (!productId.isEmpty()) values.put("selectedProductId", productId);
        values.put("filterCategory", category);
        values.put("filterStatus", status);
        values.put("filterStock", stock == Stock.ALL ? "" : stock.name());
        values.put("stockQuantity", stockQuantity == null ? "" : stockQuantity.toString());
        values.put("sort", sort == Sort.NEWEST ? "" : sort.name());
        values.put("page", page > 1 ? Integer.toString(page) : "");
        return values;
    }

    static Map<String, String> stockOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Stock option : Stock.values()) {
            if (!option.name().startsWith("ONLINE_")) options.put(option.name(), option.label);
        }
        return options;
    }

    private static int stockQuantity(HttpServletRequest request) {
        try {
            Integer quantity = ProductQueries.integer(request, "stockQuantity", null, 0, Integer.MAX_VALUE);
            if (quantity != null) return quantity;
        } catch (IllegalArgumentException ignored) {
            // Use the same message for missing and invalid quantities.
        }
        throw new IllegalArgumentException("재고 기준 수량은 0 이상의 정수로 입력해 주세요.");
    }

    enum Stock {
        ALL("재고 전체", "", false),
        IN_STOCK("재고 있음", " AND p.stock_quantity > 0", false),
        EMPTY("재고 없음", " AND p.stock_quantity = 0", false),
        AT_LEAST("입력 수량 이상", " AND p.stock_quantity >= ?", true),
        AT_MOST("입력 수량 이하", " AND p.stock_quantity <= ?", true),
        NORMAL("정상 (6개 이상)", " AND p.stock_quantity >= 6", false),
        LOW("부족 (1~5개)", " AND p.stock_quantity BETWEEN 1 AND 5", false),
        ONLINE_NORMAL("온라인 재고 정상", " AND " + OnlineStockState.NORMAL.condition, false),
        ONLINE_LOW("온라인 재고 부족", " AND " + OnlineStockState.LOW.condition, false),
        ONLINE_EMPTY("온라인 품절", " AND " + OnlineStockState.SOLD_OUT.condition, false),
        ONLINE_INACTIVE("온라인 집계 제외", " AND " + OnlineStockState.INACTIVE.condition, false);

        final String label;
        final String predicate;
        final boolean needsQuantity;

        Stock(String label, String predicate, boolean needsQuantity) {
            this.label = label;
            this.predicate = predicate;
            this.needsQuantity = needsQuantity;
        }
    }

    enum Sort {
        NEWEST("p.created_at DESC"),
        UPDATED("p.updated_at DESC"),
        SALES("COALESCE(admin_sales.units, 0) DESC"),
        REVIEWS("COALESCE(review_stats.review_count, 0) DESC"),
        NAME("p.name"),
        STOCK_ASC("p.stock_quantity"),
        STOCK_DESC("p.stock_quantity DESC"),
        SHORTAGE("CASE WHEN " + OnlineStockState.INACTIVE.condition
                + " THEN 1 ELSE 0 END, CAST(p.stock_quantity AS SIGNED) - p.low_stock_threshold, p.stock_quantity"),
        PRICE_ASC("p.price"),
        PRICE_DESC("p.price DESC");

        final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }
}
