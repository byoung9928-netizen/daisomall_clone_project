package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.StoreInventoryDAO.Location;

@DaisoController
public final class StoreFinderController {
    public static final String LOCATIONS = "/app/stores/locations";
    public static final String PRODUCTS = "/app/stores/products";
    private static final String VIEW = "product/store-finder";
    private static final int PAGE_SIZE = 12;
    private final StoreInventoryDAO stores = new StoreInventoryDAO();
    private final ProductDAO products = new ProductDAO();

    @DaisoGetMapping("/stores/locations")
    public String locations(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("locationFinder", true);
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 100);
            List<Location> matches = matches(stores.locations(connection), keyword);
            request.setAttribute("storeFinder", new StoreFinderView(request.getContextPath(), keyword, matches));
            connection.commit();
        } catch (IllegalArgumentException error) {
            return message(request, response, 400, "검색어를 100자 이내로 입력해 주세요.");
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
        return VIEW;
    }

    @DaisoGetMapping("/stores/products")
    public String products(HttpServletRequest request, HttpServletResponse response) {
        try (Connection connection = ProductQueries.connect()) {
            String keyword = ProductQueries.text(request, "q", 100);
            String storeKeyword = ProductQueries.text(request, "storeQuery", 100);
            String productId = ProductQueries.text(request, "product", 100);
            String storeId = ProductQueries.text(request, "store", 40);
            String picker = ProductQueries.text(request, "picker", 10);
            int page = ProductQueries.integer(request, "page", 1, 1, 1000);
            if (!List.of("", "product", "store").contains(picker)) throw new IllegalArgumentException();
            ProductDTO product = productId.isEmpty() ? null : products.findVisibleById(connection, productId);
            if (!productId.isEmpty() && product == null) {
                return message(request, response, 404, "현재 조회할 수 없는 상품입니다.");
            }

            List<Location> locations = storeId.isEmpty() && !"store".equals(picker)
                    ? List.of() : stores.locations(connection);
            Location selected = locations.stream().filter(store -> store.id.equals(storeId)).findFirst().orElse(null);
            if (!storeId.isEmpty() && selected == null) {
                return message(request, response, 404, "현재 조회할 수 없는 매장입니다.");
            }
            List<Location> storeResults = "store".equals(picker) ? matches(locations, storeKeyword) : List.of();
            CatalogPage productResults = null;
            if ("product".equals(picker) && !keyword.isEmpty()) {
                CatalogFilter filter = new CatalogFilter();
                filter.keyword = keyword;
                filter.limit = PAGE_SIZE;
                filter.offset = (page - 1) * PAGE_SIZE;
                productResults = products.findCatalog(connection, filter);
            }
            Integer quantity = product == null || selected == null
                    ? null : stores.quantity(connection, selected.id, product.getProductId());
            request.setAttribute("storeFinder", new StoreFinderView(request.getContextPath(), keyword, storeKeyword,
                    picker, product, selected, quantity, storeResults, productResults, page, PAGE_SIZE));
            connection.commit();
        } catch (IllegalArgumentException error) {
            return message(request, response, 400, "검색 조건을 다시 확인해 주세요.");
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
        return VIEW;
    }

    private static List<Location> matches(List<Location> locations, String keyword) {
        String[] terms = keyword.toLowerCase(Locale.ROOT).split("\\s+");
        return locations.stream().filter(store -> {
            String name = (store.name + " " + store.city + " " + store.id).toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (!name.contains(term)) return false;
            }
            return true;
        }).collect(Collectors.toUnmodifiableList());
    }

    private static String unavailable(HttpServletRequest request, HttpServletResponse response, Exception error) {
        request.getServletContext().log("고객 매장 조회 실패", error);
        return message(request, response, 500, "매장 정보를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static String message(HttpServletRequest request, HttpServletResponse response, int status, String text) {
        response.setStatus(status);
        request.setAttribute("storeFinderError", text);
        return VIEW;
    }
}
