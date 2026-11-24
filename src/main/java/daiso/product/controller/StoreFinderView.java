package daiso.product.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.dao.StoreInventoryDAO.Location;
import daiso.product.dao.StoreInventoryDAO.State;
import daiso.product.dao.StoreInventoryDAO.StockAvailability;

public final class StoreFinderView {
    private static final Map<String, String> AREA_BOUNDS = Map.of(
            "SEOUL_GANGNAM", "127.019,37.490,127.038,37.506",
            "SEOUL_HONGDAE", "126.913,37.548,126.933,37.564",
            "BUSAN_SEOMYEON", "129.048,35.149,129.067,35.166",
            "BUSAN_HAEUNDAE", "129.150,35.152,129.172,35.170");

    public final String keyword;
    public final String storeKeyword;
    public final String picker;
    public final ProductDTO product;
    public final Store selected;
    public final List<Store> stores;
    public final List<ProductDTO> products;
    public final int total;
    public final boolean checked;
    public final String stockLabel;
    public final String stockState;
    public final String closeUrl;
    public final String previous;
    public final String next;

    StoreFinderView(String context, String keyword, List<Location> stores) {
        this(context, keyword, "", "", null, null, null, stores, null, 1, 1);
    }

    StoreFinderView(String context, String keyword, String storeKeyword, String picker, ProductDTO product,
            Location selected, StockAvailability availability, List<Location> locations, CatalogPage result, int page, int size) {
        this.keyword = keyword;
        this.storeKeyword = storeKeyword;
        this.picker = picker;
        this.product = product;
        this.selected = selected == null ? null : new Store(context, selected);
        this.stores = locations.stream().map(store -> new Store(context, store)).collect(Collectors.toUnmodifiableList());
        products = result == null ? List.of() : List.copyOf(result.products);
        total = result == null ? 0 : result.total;
        checked = product != null && selected != null;
        if (availability == null) {
            stockLabel = "이 매장에서 취급하지 않는 상품입니다.";
            stockState = "not-carried";
        } else {
            State state = availability.state;
            stockLabel = (state == State.NORMAL ? "재고 있음" : state == State.SOLD_OUT ? "품절" : state.label)
                    + " · " + availability.quantity + "개";
            stockState = state == State.SOLD_OUT ? "sold-out" : state == State.LOW ? "low" : "available";
        }

        Map<String, String> params = Map.of("q", keyword, "product", product == null ? "" : product.getProductId(),
                "store", selected == null ? "" : selected.id);
        closeUrl = ProductView.url(context, StoreFinderController.PRODUCTS, params);
        Map<String, String> pageParams = ProductView.with(params, "picker", "product");
        previous = page > 1 && result != null ? pageUrl(context, pageParams, page - 1) : null;
        next = (long) page * size < total ? pageUrl(context, pageParams, page + 1) : null;
    }

    private static String pageUrl(String context, Map<String, String> params, int page) {
        return ProductView.url(context, StoreFinderController.PRODUCTS, ProductView.with(params, "page", Integer.toString(page)));
    }

    public static final class Store {
        public final String id;
        public final String name;
        public final String city;
        public final String mapUrl;
        public final String mapBounds;
        public final String stockUrl;
        public final String locationUrl;

        private Store(String context, Location location) {
            id = location.id;
            name = location.name;
            city = location.city;
            String bounds = AREA_BOUNDS.get(location.id);
            mapBounds = bounds == null ? "" : bounds;
            mapUrl = bounds == null ? "" : ProductView.url("", "https://www.openstreetmap.org/export/embed.html",
                    Map.of("bbox", bounds, "layer", "mapnik"));
            stockUrl = ProductView.url(context, StoreFinderController.PRODUCTS, Map.of("store", id));
            locationUrl = ProductView.url(context, StoreFinderController.LOCATIONS, Map.of("q", name));
        }
    }
}
