package daiso.product.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

public final class InventoryPagination {
    public final int page;
    public final int pages;
    public final String previous;
    public final String next;
    public final Map<Integer, String> links;

    InventoryPagination(int page, int pages, IntFunction<String> url) {
        this.page = page;
        this.pages = pages;
        previous = page > 1 ? url.apply(page - 1) : null;
        next = page < pages ? url.apply(page + 1) : null;
        Map<Integer, String> window = new LinkedHashMap<>();
        if (pages <= 7) {
            for (int index = 1; index <= pages; index++) window.put(index, url.apply(index));
        } else {
            int start = Math.max(1, Math.min(page - 2, pages - 4));
            window.put(1, url.apply(1));
            for (int index = start; index < start + 5; index++) window.put(index, url.apply(index));
            window.put(pages, url.apply(pages));
        }
        links = Collections.unmodifiableMap(window);
    }
}
