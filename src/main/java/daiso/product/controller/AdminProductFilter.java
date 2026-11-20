package daiso.product.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

final class AdminProductFilter {
    final String keyword;
    final String category;
    final String status;
    final Stock stock;
    final Integer stockQuantity;
    final Sort sort;

    private AdminProductFilter(String keyword, String category, String status, Stock stock,
            Integer stockQuantity, Sort sort) {
        this.keyword = keyword;
        this.category = category;
        this.status = status;
        this.stock = stock;
        this.stockQuantity = stockQuantity;
        this.sort = sort;
    }

    static AdminProductFilter read(HttpServletRequest request, Map<String, String> statuses) {
        String status = ProductQueries.text(request, "filterStatus", 20);
        if (!status.isEmpty() && !statuses.containsKey(status)) throw new IllegalArgumentException();
        String rawStock = ProductQueries.text(request, "filterStock", 20);
        Stock stock = rawStock.isEmpty() ? Stock.ALL : Stock.valueOf(rawStock);
        Integer quantity = stock.needsQuantity ? stockQuantity(request) : null;
        String sort = ProductQueries.text(request, "sort", 20);
        return new AdminProductFilter(ProductQueries.text(request, "query", 120),
                ProductQueries.text(request, "filterCategory", 100), status,
                stock, quantity, sort.isEmpty() ? Sort.NEWEST : Sort.valueOf(sort));
    }

    static AdminProductFilter forInventory(String keyword, Stock stock) {
        return new AdminProductFilter(keyword, "", "", stock, null, Sort.NAME);
    }

    static AdminProductFilter defaults() {
        return new AdminProductFilter("", "", "", Stock.ALL, null, Sort.NEWEST);
    }

    Map<String, String> parameters(int page) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("query", keyword);
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
        for (Stock option : Stock.values()) options.put(option.name(), option.label);
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
        LOW("부족 (1~5개)", " AND p.stock_quantity BETWEEN 1 AND 5", false);

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
        NAME("p.name"),
        STOCK_ASC("p.stock_quantity"),
        STOCK_DESC("p.stock_quantity DESC"),
        PRICE_ASC("p.price"),
        PRICE_DESC("p.price DESC");

        final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }
}
