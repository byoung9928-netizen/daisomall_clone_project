package daiso.product.view;

import daiso.product.dto.ProductDTO;
import daiso.product.dto.ProductPage;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AdminProductPageModel {
    // 페이지 번호를 여섯 개씩 표시
    private static final int PAGE_LINKS = 6;
    public final Map<String, Object> attributes = new LinkedHashMap<>();

    // 관리자 목록의 상품별 링크와 페이지 번호 준비
    public AdminProductPageModel(String context, String query, int page, int pages, ProductPage result) {
        String url = "/app/admin/products";
        Map<String, String> filters = Map.of("query", query, "page", Integer.toString(page));
        Map<ProductDTO, Map<String, String>> rows = new LinkedHashMap<>();
        for (ProductDTO product : result.products) {
            rows.put(product, Map.of(
                    "edit", ProductDisplay.url(context, url, ProductDisplay.with(filters, "edit", product.getProductId())),
                    "delete", ProductDisplay.url(context, url, ProductDisplay.with(filters, "delete", product.getProductId()))));
        }
        attributes.put("productRows", rows);
        attributes.put("adminQuery", query);
        attributes.put("adminProductCount", result.total);
        Map<Integer, String> pageLinks = new LinkedHashMap<>();
        int firstPage = ((page - 1) / PAGE_LINKS) * PAGE_LINKS + 1;
        for (int number = firstPage; number <= Math.min(firstPage + PAGE_LINKS - 1, pages); number++) {
            pageLinks.put(number, ProductDisplay.url(context, url, ProductDisplay.with(filters, "page", Integer.toString(number))));
        }
        attributes.put("adminProductPage", page);
        attributes.put("adminProductPageLinks", pageLinks);
        attributes.put("adminProductPrev", page > 1 ? ProductDisplay.url(context, url,
                ProductDisplay.with(filters, "page", Integer.toString(page - 1))) : null);
        attributes.put("adminProductNext", page < pages ? ProductDisplay.url(context, url,
                ProductDisplay.with(filters, "page", Integer.toString(page + 1))) : null);
        attributes.put("adminProductNew", ProductDisplay.url(context, url, ProductDisplay.with(filters, "new", "1")));
        attributes.put("adminProductList", ProductDisplay.url(context, url, filters));
    }

    // 기존 상품 정보를 입력 폼에 표시할 값으로 변환
    public static Map<String, String> formFor(ProductDTO product) {
        return Map.of("id", product.getProductId(), "code", product.getProductCode(), "name", product.getName(),
                "brand", product.getBrand(), "category", product.getCategoryId(), "price", Integer.toString(product.getPrice()),
                "stock", Integer.toString(product.getStockQuantity()), "status", product.getStatus(),
                "image", product.getImage() == null ? "" : product.getImage(),
                "description", product.getDescription() == null ? "" : product.getDescription());
    }
}
