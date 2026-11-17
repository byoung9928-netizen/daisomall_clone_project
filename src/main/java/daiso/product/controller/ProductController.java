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

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.controller.ProductDAO.Category;

@DaisoController
public class ProductController {
    private static final int PAGE_SIZE = 30;
    private static final Map<String, String> COLLECTIONS = Map.of(
            "new", "신상발견", "ranking", "랭킹", "rating", "별점 5점",
            "selling-out", "품절임박", "open-run", "판매 예정", "brand", "브랜드 상품",
            "store", "매장 상품 찾기", "exhibitions", "기획전", "exhibition", "기획전");

    private final ProductDAO dao = new ProductDAO();

    @DaisoGetMapping("/products/home")
    public String home(HttpServletRequest request) throws SQLException, NamingException {
        try (Connection conn = ProductQueries.connect()) {
            request.setAttribute("productCategories", dao.findVisibleCategories(conn));
            request.setAttribute("activeNavigation", "home");
            request.setAttribute("featuredProducts", dao.findCatalog(conn, selection("recommended", "", 6)).products);
            request.setAttribute("newProducts", dao.findCatalog(conn, selection("newest", "", 6)).products);
            conn.commit();
        }
        return "shop/home";
    }

    @DaisoGetMapping("/products")
    public String products(HttpServletRequest request) throws SQLException, NamingException {
        return catalog(request, "list");
    }

    @DaisoGetMapping("/products/search")
    public String search(HttpServletRequest request) throws SQLException, NamingException {
        return catalog(request, "search");
    }

    @DaisoGetMapping("/products/page")
    public String page(HttpServletRequest request) throws SQLException, NamingException {
        return detail(request);
    }

    @DaisoGetMapping("/products/move")
    public String move() {
        return "redirect:/products";
    }

    @DaisoGetMapping("/products/detail")
    public String detail(HttpServletRequest request) throws SQLException, NamingException {
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
            }
            conn.commit();
        }
        return "product/detail";
    }

    private String catalog(HttpServletRequest request, String view) throws SQLException, NamingException {
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
