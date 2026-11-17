package daiso.product.controller;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.controller.ProductDAO.Category;

public final class ProductCatalogView {
    private static final List<Integer> STANDARD_PRICES = List.of(1000, 3000, 5000);
    private static final List<Map.Entry<String, String>> SORT_OPTIONS = List.of(
            Map.entry("recommended", "추천순"), Map.entry("newest", "신상품순"),
            Map.entry("priceAsc", "가격낮은순"), Map.entry("priceDesc", "가격높은순"),
            Map.entry("rating", "별점순"), Map.entry("reviews", "리뷰많은순"));

    public final String title;
    public final boolean storeSearch;
    public final List<ProductDTO> products;
    public final Map<String, String> categoryLinks;
    public final Map<String, String> hiddenFilters;
    public final String filterAction;
    public final String resetUrl;
    public final String brand;
    public final boolean availableOnly;
    public final List<Option> prices;
    public final List<Option> statuses;
    public final List<Option> sorts;
    public final String resultCount;
    public final boolean showPagination;
    public final String pageLabel;
    public final String previousUrl;
    public final String nextUrl;

    ProductCatalogView(String context, String route, String title, String collection, CatalogFilter filter,
            List<Category> categories, CatalogPage result, int page, int pages) {
        this.title = title;
        storeSearch = "store".equals(collection);
        products = List.copyOf(result.products);
        brand = filter.brand;
        availableOnly = filter.available;
        prices = priceOptions(filter.maxPrice);
        statuses = List.of(new Option("", "전체 상태", filter.status.isEmpty()),
                new Option("AVAILABLE", "판매 중", "AVAILABLE".equals(filter.status)),
                new Option("SOLD_OUT", "품절", "SOLD_OUT".equals(filter.status)),
                new Option("SCHEDULED", "판매 예정", "SCHEDULED".equals(filter.status)));
        sorts = SORT_OPTIONS.stream()
                .map(entry -> new Option(entry.getKey(), entry.getValue(), entry.getKey().equals(filter.sort)))
                .collect(Collectors.toUnmodifiableList());

        Map<String, String> baseParams = new LinkedHashMap<>();
        baseParams.put("q", filter.keyword);
        baseParams.put("category", filter.category);
        baseParams.put("collection", collection);
        filterAction = ProductView.url(context, route, Map.of());
        resetUrl = ProductView.url(context, route, baseParams);

        Map<String, String> hidden = new LinkedHashMap<>(baseParams);
        hidden.values().removeIf(String::isEmpty);
        hiddenFilters = Collections.unmodifiableMap(hidden);

        Map<String, String> params = new LinkedHashMap<>(baseParams);
        params.put("brand", filter.brand);
        params.put("status", filter.status);
        params.put("sort", filter.sort);
        params.put("maxPrice", filter.maxPrice == null ? "" : filter.maxPrice.toString());
        params.put("available", filter.available ? "true" : "");

        String parentId = filter.category.isEmpty() ? null : filter.category;
        Map<String, String> links = new LinkedHashMap<>();
        for (Category category : categories) {
            if (Objects.equals(category.parentId, parentId)) {
                String href = ProductView.url(context, route, ProductView.with(params, "category", category.id));
                links.put(href, category.name);
            }
        }
        categoryLinks = Collections.unmodifiableMap(links);

        resultCount = NumberFormat.getIntegerInstance(Locale.KOREA).format(result.total);
        showPagination = pages > 1;
        pageLabel = page + " / " + pages;
        previousUrl = page > 1 ? pageUrl(context, route, params, page - 1) : null;
        nextUrl = page < pages ? pageUrl(context, route, params, page + 1) : null;
    }

    private static List<Option> priceOptions(Integer selectedPrice) {
        List<Option> options = new ArrayList<>();
        options.add(new Option("", "전체 가격", selectedPrice == null));
        for (Integer price : STANDARD_PRICES) {
            options.add(new Option(price.toString(), ProductView.money(price) + " 이하", price.equals(selectedPrice)));
        }
        if (selectedPrice != null && !STANDARD_PRICES.contains(selectedPrice)) {
            options.add(new Option(selectedPrice.toString(), ProductView.money(selectedPrice) + " 이하", true));
        }
        return List.copyOf(options);
    }

    private static String pageUrl(String context, String route, Map<String, String> filters, int page) {
        return ProductView.url(context, route, ProductView.with(filters, "page", Integer.toString(page)));
    }

    public static final class Option {
        public final String value;
        public final String label;
        public final boolean selected;

        private Option(String value, String label, boolean selected) {
            this.value = value;
            this.label = label;
            this.selected = selected;
        }
    }
}
