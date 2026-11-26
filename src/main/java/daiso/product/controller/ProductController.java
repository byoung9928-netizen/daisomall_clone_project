package daiso.product.controller;

import daiso.product.service.ProductBehaviorRecorder;
import daiso.product.service.ProductBehaviorRecorder.Type;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductDAO.CatalogFilter;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.controller.ProductDAO.Category;
import daiso.product.controller.ProductDAO.HomeRankCategory;
import daiso.product.dao.ProductReviewDAO.Sort;
import daiso.product.dao.StoreInventoryDAO;
import daiso.product.dao.RecommendationActivityDAO;
import daiso.product.dao.RecommendationActivityDAO.Kind;
import daiso.product.dao.RecommendationActivityDAO.Signal;
import daiso.product.service.RecommendationService;
import daiso.product.service.RecommendationService.Interest;
import daiso.product.service.RecommendationService.Diagnostic;
import daiso.product.service.ProductReviewService;

@DaisoController
public class ProductController {
    private static final int PAGE_SIZE = 30;
    private static final int HOME_FEATURED_LIMIT = 21;
    private static final int POPULAR_POOL = 84;
    private static final int CANDIDATES_PER_ROOT = 48;
    private static final int ACTION_LIMIT = 120;
    private static final int VIEW_LIMIT = 50;
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int HOME_RANK_CATEGORY_LIMIT = 10;
    private static final int HOME_RANK_PRODUCT_LIMIT = 24;
    private static final Map<String, String> COLLECTIONS = Map.ofEntries(
            Map.entry("new", "신상발견"), Map.entry("ranking", "랭킹"), Map.entry("rating", "별점 5점"),
            Map.entry("selling-out", "품절임박"), Map.entry("open-run", "오픈런"), Map.entry("brand", "브랜드 상품"),
            Map.entry("store", "매장 상품 찾기"), Map.entry("exhibitions", "기획전"), Map.entry("exhibition", "기획전"),
            Map.entry("beauty", "뷰티"), Map.entry("health", "건강식품"));

    private final ProductDAO dao = new ProductDAO();
    private final ProductCatalogFacets catalogFacets = new ProductCatalogFacets();
    private final OfficialProductDetails officialDetails = new OfficialProductDetails();
    private final ProductDetailImages detailImages = new ProductDetailImages();
    private final StoreInventoryDAO storeInventory = new StoreInventoryDAO();
    private final RecommendationActivityDAO recommendationActivity = new RecommendationActivityDAO();
    private final RecommendationService recommendations = new RecommendationService();
    private final ProductReviewService reviews = new ProductReviewService();

    @DaisoGetMapping("/products/home")
    public String home(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        try (Connection conn = ProductQueries.connect()) {
            List<Category> categories = dao.findVisibleCategories(conn);
            request.setAttribute("productCategories", categories);
            request.setAttribute("activeNavigation", "home");
            List<ProductDTO> newest = dao.findCatalog(conn, selection("newest", "", HOME_FEATURED_LIMIT)).products;
            request.setAttribute("newProducts", newest);
            prepareHomeRecommendations(request, response, conn, categories, newest);
            conn.commit();
        }
        return "shop/home";
    }

    private void prepareHomeRecommendations(HttpServletRequest request, HttpServletResponse response, Connection conn,
            List<Category> categories, List<ProductDTO> newest) throws SQLException {
        HttpSession session = request.getSession(false);
        String member = ProductActivityController.member(session);
        if (member != null && session.getAttribute("name") instanceof String) {
            String name = ((String) session.getAttribute("name")).strip();
            if (!name.isEmpty() && name.length() <= 100 && name.codePoints().noneMatch(Character::isISOControl)) {
                request.setAttribute("homeRecommendationTitle", name + "님을 위한 오늘 추천상품");
            }
        }

        Map<String, String> featured = rankHomeRecommendations(request, conn, categories, newest, session, member, null);
        request.setAttribute("featuredProducts", dao.recommendationCards(conn, List.copyOf(featured.keySet())));
        request.setAttribute("recommendationReasons", featured);
        List<String> wishlistIds = new ArrayList<>(featured.keySet());
        newest.forEach(product -> wishlistIds.add(product.getProductId()));
        prepareCardFacts(request, conn, wishlistIds);
        WishlistController.prepare(request, response, conn, wishlistIds);
    }

    private Map<String, String> rankHomeRecommendations(HttpServletRequest request, Connection conn,
            List<Category> categories, List<ProductDTO> newest, HttpSession session, String member,
            Diagnostic diagnostic) throws SQLException {
        List<ProductDTO> popular = dao.recommendationCandidates(conn, null, POPULAR_POOL);
        List<Interest> interests = member == null ? List.of() : homeInterests(request, conn, session, member, diagnostic);
        List<ProductDTO> seeds = new ArrayList<>();
        List<String> seedIds = interests.stream().map(interest -> interest.id).collect(Collectors.toList());
        for (var item : dao.findRecentlyViewed(conn, seedIds, seedIds.size(), 0)) seeds.add(item.product);
        List<ProductDTO> candidates = dao.recommendationCandidates(conn,
                recommendations.scope(interests, seeds, categories, diagnostic), CANDIDATES_PER_ROOT);

        var ids = new LinkedHashSet<String>();
        for (List<ProductDTO> group : List.of(popular, candidates, newest)) {
            for (ProductDTO product : group) ids.add(product.getProductId());
        }
        Set<String> pickup = prepareCardPickup(request, conn, List.copyOf(ids));
        return recommendations.select(member != null, interests, seeds, candidates, popular, newest,
                categories, pickup, HOME_FEATURED_LIMIT, diagnostic);
    }

    @DaisoGetMapping("/admin/products/recommendation")
    public String recommendationAnalysis(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, NamingException, java.io.IOException {
        response.setHeader("Cache-Control", "private, no-store");
        if (!recommendationDiagnosticsEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (AdminProductController.adminSession(request) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        request.setAttribute("adminSection", "products");
        String member = request.getParameter("member");
        if (member == null || member.isBlank()) return "admin/product-recommendation";
        if (!member.matches("DEMO_RANK_V1_M[0-9]{4}")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("analysisError", "시연 회원 ID를 확인해 주세요.");
            return "admin/product-recommendation";
        }
        try (Connection conn = ProductQueries.connect()) {
            if (!recommendationActivity.fixtureMember(conn, member)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                request.setAttribute("analysisError", "등록된 시연 회원을 찾을 수 없습니다.");
                return "admin/product-recommendation";
            }
            Diagnostic diagnostic = new Diagnostic();
            List<ProductDTO> newest = dao.findCatalog(conn, selection("newest", "", HOME_FEATURED_LIMIT)).products;
            rankHomeRecommendations(request, conn, dao.findVisibleCategories(conn), newest, null, member, diagnostic);
            request.setAttribute("recommendationDiagnostic", diagnostic);
            conn.commit();
        }
        return "admin/product-recommendation";
    }

    public static boolean recommendationDiagnosticsEnabled() {
        return Boolean.getBoolean("daisomall.dev.recommendations");
    }

    private List<Interest> homeInterests(HttpServletRequest request, Connection conn, HttpSession session, String member,
            Diagnostic diagnostic) {
        LocalDate today = LocalDate.now(STORE_ZONE);
        LocalDate since = today.minusDays(RecommendationService.HISTORY_DAYS);
        List<Signal> signals = new ArrayList<>();
        ProductActivityController.recommendationSnapshot(session, member).forEach((id, date) -> signals.add(new Signal(id, Kind.VIEW, date)));
        try {
            signals.addAll(recommendationActivity.findActions(conn, member, since, ACTION_LIMIT));
        } catch (SQLException error) {
            request.getServletContext().log("추천 행동 조회 실패", error);
        }
        try {
            signals.addAll(recommendationActivity.findViews(conn, member, since, VIEW_LIMIT));
        } catch (SQLException error) {
            if (!RecommendationActivityDAO.viewTableMissing(error)) {
                request.getServletContext().log("추천 조회 이력 확인 실패", error);
            }
        }
        return recommendations.interests(signals, today, diagnostic);
    }

    @DaisoGetMapping("/products/home/category-rankings")
    public String homeCategoryRankings(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, NamingException {
        response.setHeader("Cache-Control", "private, no-store");
        String category;
        try {
            category = ProductQueries.text(request, "category", 100);
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "shop/home-category-ranking";
        }
        try (Connection conn = ProductQueries.connect()) {
            if (category.isEmpty()) {
                List<HomeRankCategory> categories = homeRankCategories(dao.findHomeRankCategories(conn));
                request.setAttribute("homeRankCategories", categories);
                if (!categories.isEmpty()) category = categories.get(0).id;
            }
            if (!category.isEmpty()) {
                List<ProductDTO> products = dao.findHomeRankProducts(conn, category, HOME_RANK_PRODUCT_LIMIT);
                if (products.size() < ProductDAO.HOME_RANK_MINIMUM) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return "shop/home-category-ranking";
                }
                request.setAttribute("homeRankProducts", products);
                List<String> ids = products.stream().map(ProductDTO::getProductId).collect(Collectors.toList());
                prepareCardPickup(request, conn, ids);
                prepareCardFacts(request, conn, ids);
                WishlistController.prepare(request, response, conn, ids);
            }
            conn.commit();
        }
        return "shop/home-category-ranking";
    }

    private static List<HomeRankCategory> homeRankCategories(List<HomeRankCategory> candidates) {
        Set<String> represented = new LinkedHashSet<>();
        List<HomeRankCategory> selected = new ArrayList<>();
        for (HomeRankCategory candidate : candidates) {
            if (represented.add(candidate.rootId)) selected.add(candidate);
            if (selected.size() == HOME_RANK_CATEGORY_LIMIT) return selected;
        }
        // Prefer a new product family first, then fill the remaining tabs in popularity order.
        for (HomeRankCategory candidate : candidates) {
            if (!selected.contains(candidate)) selected.add(candidate);
            if (selected.size() == HOME_RANK_CATEGORY_LIMIT) break;
        }
        return selected;
    }

    @DaisoGetMapping("/products")
    public String products(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        return catalog(request, response, "list");
    }

    @DaisoGetMapping("/products/selling-out")
    public String sellingOut(HttpServletRequest request, HttpServletResponse response) throws SQLException, NamingException {
        request.setAttribute("stockWatch", true);
        request.setAttribute("activeNavigation", "selling-out");
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
        response.setHeader("Cache-Control", "private, no-store");
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
                List<String> uploadedImages = List.copyOf(detailImages.list(conn, product.getProductId()).values());
                OfficialProductDetails.Entry officialDetail = officialDetails.find(request.getServletContext(), product);
                request.setAttribute("productDetailImages", uploadedImages);
                request.setAttribute("officialProductDetails", officialDetail);
                request.setAttribute("productGalleryImages", galleryImages(product, uploadedImages, officialDetail));
                preparePickupStores(request, conn, product);
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
                for (ProductDTO item : recommendations) wishlistIds.add(item.getProductId());
                prepareCardPickup(request, conn, wishlistIds);
                prepareCardFacts(request, conn, wishlistIds);
                wishlistIds.add(product.getProductId());
                WishlistController.prepare(request, response, conn, wishlistIds);
                ProductRestockController.prepare(request, conn, product);
            }
            conn.commit();
        }
        ProductDTO product = (ProductDTO) request.getAttribute("product");
        if (product != null) {
            HttpSession session = request.getSession();
            String member = ProductActivityController.member(session);
            LocalDate viewedOn = ProductActivityController.remember(session, member, product.getProductId());
            if (member != null && viewedOn != null) recordView(request, member, product.getProductId(), viewedOn);
            prepareDetailReviews(request, product.getProductId(), positiveReviewPage(request.getParameter("photoPage")));
            ProductBehaviorRecorder.record(request, Type.VIEW, product.getProductId());
        }
        return "product/detail";
    }

    private static List<String> galleryImages(ProductDTO product, List<String> uploadedImages,
            OfficialProductDetails.Entry officialDetail) {
        LinkedHashSet<String> images = new LinkedHashSet<>();
        if (product.getImage() != null && !product.getImage().isBlank()) images.add(product.getImage());
        images.addAll(uploadedImages);
        if (officialDetail != null) images.addAll(officialDetail.getRepresentativePhotos());
        if (images.isEmpty()) images.add("assets/images/mock-product-0.svg");
        return List.copyOf(images);
    }

    private void preparePickupStores(HttpServletRequest request, Connection connection, ProductDTO product) {
        if (!ProductView.pickupEligible(product)) return;
        try {
            request.setAttribute("pickupStores", storeInventory.pickupStores(connection, product.getProductId()));
        } catch (SQLException error) {
            request.getServletContext().log("상품 상세 픽업 재고 조회 실패", error);
            request.setAttribute("pickupLookupError", true);
        }
    }

    private void prepareCardFacts(HttpServletRequest request, Connection connection, List<String> productIds) {
        try {
            request.setAttribute("cardFacts", dao.cardFacts(connection, productIds, true));
        } catch (SQLException error) {
            request.getServletContext().log("상품 카드 구매 정보 조회 실패", error);
        }
    }

    private Set<String> prepareCardPickup(HttpServletRequest request, Connection connection, List<String> productIds) {
        try {
            Set<String> pickup = storeInventory.pickupProductIds(connection, productIds);
            request.setAttribute("cardPickupIds", pickup);
            return pickup;
        } catch (SQLException error) {
            request.getServletContext().log("상품 카드 픽업 재고 조회 실패", error);
            request.setAttribute("cardPickupLookupError", true);
            return Set.of();
        }
    }

    private void recordView(HttpServletRequest request, String member, String product, LocalDate day) {
        try (Connection conn = ProductQueries.connect()) {
            try {
                recommendationActivity.recordView(conn, member, product, day);
                conn.commit();
            } catch (SQLException error) {
                conn.rollback();
                throw error;
            }
        } catch (SQLException | NamingException error) {
            if (!(error instanceof SQLException) || !RecommendationActivityDAO.viewTableMissing((SQLException) error)) {
                request.getServletContext().log("상품 조회 이력 저장 실패", error);
            }
        }
    }

    @DaisoGetMapping("/products/detail/reviews")
    public String detailReviewResults(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, NamingException {
        response.setHeader("Cache-Control", "private, no-store");
        String id = request.getParameter("id");
        String sort = request.getParameter("reviewSort");
        if (id == null || id.isBlank() || id.length() > 100
                || (sort != null && !List.of("newest", "highest", "lowest").contains(sort))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            ProductDTO product;
            try (Connection connection = ProductQueries.connect()) {
                product = dao.findVisibleById(connection, id.strip());
                connection.commit();
            }
            if (product == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                request.setAttribute("product", product);
                if (!prepareDetailReviews(request, product.getProductId(), 0)) {
                    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                }
            }
        }
        return "product/detail-review-results";
    }

    private boolean prepareDetailReviews(HttpServletRequest request, String productId, int photoPage) {
        String requestedSort = request.getParameter("reviewSort");
        Sort sort = "highest".equals(requestedSort) ? Sort.HIGHEST
                : "lowest".equals(requestedSort) ? Sort.LOWEST : Sort.NEWEST;
        int page = positiveReviewPage(request.getParameter("reviewPage"));
        request.setAttribute("detailReviewSort", sort.key);
        try {
            // 상품 조회 트랜잭션과 분리해 리뷰 오류에도 상세 화면을 표시한다.
            HttpSession session = request.getSession(false);
            String member = ProductReviewController.memberId(session);
            request.setAttribute("detailReviews", reviews.publicPage(productId, member, sort, page, photoPage));
            if (member != null) request.setAttribute("detailReviewCsrf", ProductReviewController.token(session, member));
            return true;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("상품 상세 리뷰 조회 실패", error);
            return false;
        }
    }

    private static int positiveReviewPage(String rawPage) {
        if (rawPage == null) return 1;
        try {
            return Math.max(1, Integer.parseInt(rawPage));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String catalog(HttpServletRequest request, HttpServletResponse response, String view) throws SQLException, NamingException {
        boolean stockWatch = Boolean.TRUE.equals(request.getAttribute("stockWatch"));
        String collection = stockWatch ? "selling-out" : request.getParameter("collection");
        collection = collection == null ? "" : collection;
        String title = COLLECTIONS.get(collection);
        ProductCollection collectionPage = ProductCollection.forKey(collection);
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
            if (stockWatch || "new".equals(collection) || "open-run".equals(collection)) filter.sort = "newest";
            if ("rating".equals(collection)) filter.sort = "rating";
        }
        filter.lowStock = !stockWatch && "selling-out".equals(collection);
        filter.stockWatch = stockWatch;
        if (stockWatch) {
            if (!List.of("newest", "stockDesc", "recommended").contains(filter.sort)) filter.sort = "newest";
            filter.status = "";
            filter.maxPrice = null;
            filter.recentOnly = "true".equals(request.getParameter("recent"));
            filter.watchRank = "new".equals(request.getParameter("rank")) ? "new" : "popular";
        }
        filter.topRated = false;
        if (collectionPage != null) collectionPage.configure(request, filter);
        filter.offset = (page - 1) * PAGE_SIZE;
        filter.limit = PAGE_SIZE;

        try (Connection conn = ProductQueries.connect()) {
            List<Category> categories = dao.findVisibleCategories(conn);
            List<Category> path = categoryPath(categories, filter.category);
            if (collectionPage != null && !collectionPage.root.isEmpty()
                    && path.stream().noneMatch(category -> category.id.equals(collectionPage.root))) {
                filter.category = collectionPage.root;
                path = categoryPath(categories, filter.category);
            }
            request.setAttribute("productCategories", categories);
            if (!filter.category.isEmpty() && path.isEmpty()) {
                request.setAttribute("catalogError", "존재하지 않는 카테고리입니다.");
                request.setAttribute("catalogErrorStatus", 404);
            } else {
                if (collection.isEmpty() && !stockWatch) {
                    filter.facets = catalogFacets.select(request.getServletContext(), filter.category, request);
                }
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
                if (stockWatch) {
                    Map<String, Integer> counts = dao.stockWatchCounts(conn);
                    CatalogFilter rank = selection("new".equals(filter.watchRank) ? "newest" : "recommended", "", 6);
                    rank.lowStock = true;
                    List<ProductDTO> ranked = dao.findCatalog(conn, rank).products;
                    for (ProductDTO item : ranked) wishlistIds.add(item.getProductId());
                    request.setAttribute("stockWatchHero", new StockWatchView(request.getContextPath(), filter, counts,
                            categories, ranked));
                }
                prepareCardPickup(request, conn, wishlistIds);
                prepareCardFacts(request, conn, wishlistIds);
                WishlistController.prepare(request, response, conn, wishlistIds);
                request.setAttribute("activeNavigation", collectionPage == null ? collection : collectionPage.key);
                request.setAttribute("categoryPath", path);
                String route = stockWatch ? StockWatchView.ROUTE
                        : collectionPage != null ? "/app/products" : "/" + view + ".jsp";
                request.setAttribute("catalog", new ProductCatalogView(request.getContextPath(), route,
                        title, collection, filter, categories, path, result, page, pages));
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
