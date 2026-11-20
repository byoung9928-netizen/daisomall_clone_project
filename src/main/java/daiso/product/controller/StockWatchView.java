package daiso.product.controller;

import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.Category;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StockWatchView {
    static final String ROUTE = "/app/products/selling-out";
    public final String total;
    public final String checkedAt;
    public final String checkedAtIso;
    public final boolean newest;
    public final String popularUrl;
    public final String newestUrl;
    public final String allUrl;
    public final Map<String, String> categories;
    public final List<ProductDTO> ranked;

    StockWatchView(String context, CatalogFilter filter, Map<String, Integer> counts,
            List<Category> topCategories, List<ProductDTO> ranked) {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.KOREA);
        total = number.format(counts.values().stream().mapToInt(Integer::intValue).sum());
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
        checkedAt = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
        checkedAtIso = now.toOffsetDateTime().toString();
        newest = "new".equals(filter.watchRank);
        this.ranked = List.copyOf(ranked);

        Map<String, String> state = new LinkedHashMap<>();
        state.put("category", filter.category);
        state.put("brand", filter.brand);
        state.put("q", filter.keyword);
        state.put("sort", filter.sort);
        state.put("available", filter.available ? "true" : "");
        state.put("recent", filter.recentOnly ? "true" : "");
        popularUrl = ProductView.url(context, ROUTE, ProductView.with(state, "rank", "popular"));
        newestUrl = ProductView.url(context, ROUTE, ProductView.with(state, "rank", "new"));
        state.put("rank", filter.watchRank);
        allUrl = ProductView.url(context, ROUTE, ProductView.with(state, "category", ""));
        Map<String, String> categoriesWithCounts = new LinkedHashMap<>();
        for (Category category : topCategories) {
            int count = counts.getOrDefault(category.id, 0);
            if (category.parentId == null && count > 0) {
                String url = ProductView.url(context, ROUTE, ProductView.with(state, "category", category.id));
                categoriesWithCounts.put(url, category.name + " " + number.format(count) + "개");
            }
        }
        categories = Collections.unmodifiableMap(categoriesWithCounts);
    }
}
