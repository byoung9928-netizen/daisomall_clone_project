package daiso.product.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.controller.ProductDAO.Category;
import daiso.product.dao.ProductReviewDAO.Sort;
import daiso.product.service.ProductReviewService;

@DaisoController
public class ProductController {
    private static final int PAGE_SIZE = 30;
    private static final Map<String, String> COLLECTIONS = Map.of(
            "new", "신상발견", "ranking", "랭킹", "rating", "별점 5점",
            "selling-out", "품절임박", "open-run", "판매 예정", "brand", "브랜드 상품",
            "store", "매장 상품 찾기", "exhibitions", "기획전", "exhibition", "기획전");

    private final ProductDAO dao = new ProductDAO();
    private final OfficialProductDetails officialDetails = new OfficialProductDetails();
    private final ProductDetailImages detailImages = new ProductDetailImages();
    private final ProductReviewService reviews = new ProductReviewService();

    @DaisoGetMapping("/products/home")
    public String home(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        try (Connection conn = ProductQueries.connect()) {
            request.setAttribute("productCategories", dao.findVisibleCategories(conn));
            request.setAttribute("activeNavigation", "home");
            List<ProductDTO> featured = dao.findCatalog(conn, selection("recommended", "", 6)).products;
            List<ProductDTO> newest = dao.findCatalog(conn, selection("newest", "", 6)).products;
            request.setAttribute("featuredProducts", featured);
            request.setAttribute("newProducts", newest);
            // 메인에 표시된 상품의 찜 상태를 한 번에 조회
            List<String> wishlistIds = new ArrayList<>();
            for (ProductDTO item : featured) wishlistIds.add(item.getProductId());
            for (ProductDTO item : newest) wishlistIds.add(item.getProductId());
            WishlistController.prepare(request, response, conn, wishlistIds);
            conn.commit();
        }
        return "shop/home";
    }

    @DaisoGetMapping("/products")
    public String products(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        return catalog(request, response, "list");
    }

    @DaisoGetMapping("/products/search")
    public String search(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        return catalog(request, response, "search");
    }

    @DaisoGetMapping("/products/page")
    public String page(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        return detail(request, response);
    }

    @DaisoGetMapping("/products/move")
    public String move() {
        return "redirect:/products";
    }

    @DaisoGetMapping("/products/detail")
    public String detail(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        String id = request.getParameter("id");
        if (id == null || id.isBlank() || id.length() > 100) {
            return "product/detail";
        }
        try (Connection conn = ProductQueries.connect()) {
            ProductDTO product = dao.findVisibleById(conn, id.strip());
            if (product != null) {
                List<Category> categories = dao.findVisibleCategories(conn);
                List<Category> path = categoryPath(categories, product.getCategoryId());
                request.setAttribute("product", product);
                request.setAttribute("productDetailImages", List.copyOf(detailImages.list(conn, product.getProductId()).values()));
                request.setAttribute("productCategories", categories);
                request.setAttribute("categoryPath", path);
                Category group = path.get(Math.max(0, path.size() - 2));
                CatalogPage related = dao.findCatalog(conn, selection("recommended", group.id, 7));
                List<ProductDTO> recommendations = new ArrayList<>();
                for (ProductDTO item : related.products) {
                    if (!item.getProductId().equals(product.getProductId()) && recommendations.size() < 6) {
                        recommendations.add(item);
                    }
                }
                request.setAttribute("relatedProducts", recommendations);
                // 상세와 추천 카드가 같은 찜 상태를 사용
                List<String> wishlistIds = new ArrayList<>();
                wishlistIds.add(product.getProductId());
                for (ProductDTO item : recommendations) wishlistIds.add(item.getProductId());
                WishlistController.prepare(request, response, conn, wishlistIds);
            }
            conn.commit();
        }
        ProductDTO product = (ProductDTO) request.getAttribute("product");
        if (product != null) {
            request.setAttribute("officialProductDetails", officialDetails.find(request.getServletContext(), product));
            prepareDetailReviews(request, product.getProductId());
        }
        return "product/detail";
    }

    private void prepareDetailReviews(HttpServletRequest request, String productId) {
        String requestedSort = request.getParameter("reviewSort");
        Sort sort = "highest".equals(requestedSort) ? Sort.HIGHEST
                : "lowest".equals(requestedSort) ? Sort.LOWEST : Sort.NEWEST;
        int page = 1;
        String rawPage = request.getParameter("reviewPage");
        if (rawPage != null) {
            try {
                page = Math.max(1, Integer.parseInt(rawPage));
            } catch (NumberFormatException ignored) {
                // 잘못된 리뷰 페이지 값은 첫 페이지로 표시한다.
            }
        }
        request.setAttribute("detailReviewSort", sort.key);
        try {
            // 상품 조회 트랜잭션과 분리해 리뷰 오류에도 상세 화면을 표시한다.
            request.setAttribute("detailReviews", reviews.page(productId, null, sort, page));
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("상품 상세 리뷰 조회 실패", error);
        }
    }

    private String catalog(HttpServletRequest request, HttpServletResponse response, String view) throws SQLException, NamingException {
        String collection = request.getParameter("collection");
        collection = collection == null ? "" : collection;
        String title = COLLECTIONS.get(collection);
        CatalogFilter filter;
        int page;
        try {
            filter = ProductQueries.read(request);
            page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
        } catch (IllegalArgumentException e) {
            request.setAttribute("catalogError", "상품 조회 조건을 확인해 주세요.");
            request.setAttribute("catalogErrorStatus", 400);
            return "product/" + view;
        }

        if (ProductQueries.text(request, "sort", 20).isEmpty()) {
            if ("new".equals(collection) || "open-run".equals(collection)) filter.sort = "newest";
            if ("rating".equals(collection)) filter.sort = "rating";
        }
        if ("open-run".equals(collection) && filter.status.isEmpty()) filter.status = "SCHEDULED";
        filter.lowStock = "selling-out".equals(collection);
        filter.topRated = "rating".equals(collection);
        filter.offset = (page - 1) * PAGE_SIZE;
        filter.limit = PAGE_SIZE;

        try (Connection conn = ProductQueries.connect()) {
            List<Category> categories = dao.findVisibleCategories(conn);
            List<Category> path = categoryPath(categories, filter.category);
            request.setAttribute("productCategories", categories);
            if (!filter.category.isEmpty() && path.isEmpty()) {
                request.setAttribute("catalogError", "존재하지 않는 카테고리입니다.");
                request.setAttribute("catalogErrorStatus", 404);
            } else {
                CatalogPage result = dao.findCatalog(conn, filter);
                int pages = Math.max(1, (result.total + PAGE_SIZE - 1) / PAGE_SIZE);
                if (page > pages) {
                    page = pages;
                    filter.offset = (page - 1) * PAGE_SIZE;
                    result = dao.findCatalog(conn, filter);
                }
                if (title == null) {
                    title = path.isEmpty() ? "전체 상품" : path.get(path.size() - 1).name;
                    if (!filter.keyword.isEmpty()) {
                        title = "‘" + filter.keyword + "’ 검색 결과";
                    }
                }
                // 현재 페이지의 찜 상태를 상품별 반복 조회 없이 준비
                List<String> wishlistIds = new ArrayList<>();
                for (ProductDTO item : result.products) wishlistIds.add(item.getProductId());
                WishlistController.prepare(request, response, conn, wishlistIds);
                request.setAttribute("activeNavigation", collection);
                request.setAttribute("categoryPath", path);
                request.setAttribute("catalog", new ProductCatalogView(request.getContextPath(), "/" + view + ".jsp",
                        title, collection, filter, categories, result, page, pages));
            }
            conn.commit();
        }
        return "product/" + view;
    }

    private static CatalogFilter selection(String sort, String category, int limit) {
        CatalogFilter filter = new CatalogFilter();
        filter.sort = sort;
        filter.category = category;
        filter.limit = limit;
        return filter;
    }

    private static List<Category> categoryPath(List<Category> categories, String id) {
        List<Category> path = new ArrayList<>();
        Map<String, Category> byId = new LinkedHashMap<>();
        for (Category category : categories) {
            byId.put(category.id, category);
        }
        for (Category category = byId.get(id); category != null; category = byId.get(category.parentId)) {
            path.add(category);
        }
        Collections.reverse(path);
        return path;
    }
}
