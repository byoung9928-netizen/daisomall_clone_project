package daiso.product.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import daiso.product.controller.ProductDAO.CatalogFilter;

enum ProductCollection {
    NEW("new", "", "최근 30일에 등록된 상품을 만나보세요.",
            "feed", "피드", "all", "신상품 모아보기"),
    OPEN_RUN("open-run", "", "판매 예정으로 등록된 상품을 미리 확인하세요."),
    EXHIBITIONS("exhibitions", "", "테마별 추천 상품을 둘러보세요.",
            "category", "카테고리", "interest", "찜 많은 상품", "sales", "최근 구매 인기"),
    RANKING("ranking", "", "클론 쇼핑몰의 결제 승인 수량과 공개 리뷰를 기준으로 집계합니다.",
            "daily", "판매 · 최근 24시간", "weekly", "판매 · 최근 7일", "rising", "판매 · 급상승",
            "new", "신상", "reviews", "리뷰"),
    RATING("rating", "", "공개된 구매 리뷰의 평균 별점을 기준으로 모았습니다.",
            "5.0", "5점", "4.9", "4.9점", "4.8", "4.8점", "4.7", "4.7점", "satisfaction", "만족한 순간"),
    BEAUTY("beauty", "CTGR_01050", "스킨케어부터 뷰티 소품까지 카테고리별로 찾아보세요.",
            "all", "전체 상품", "new", "뷰티 신상"),
    HEALTH("health", "CTGR_01020", "건강식품을 종류별로 찾아보세요.",
            "all", "전체 상품", "new", "건강식품 신상");

    final String key;
    final String root;
    final String description;
    final List<Map.Entry<String, String>> modes;

    ProductCollection(String key, String root, String description, String... options) {
        this.key = key;
        this.root = root;
        this.description = description;
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (int i = 0; i < options.length; i += 2) {
            entries.add(Map.entry(options[i], options[i + 1]));
        }
        modes = List.copyOf(entries);
    }

    static ProductCollection forKey(String key) {
        if ("exhibition".equals(key)) return EXHIBITIONS;
        for (ProductCollection section : values()) {
            if (section.key.equals(key)) return section;
        }
        return null;
    }

    void configure(HttpServletRequest request, CatalogFilter filter) {
        String mode = request.getParameter("mode");
        if (!modes.isEmpty() && modes.stream().noneMatch(option -> option.getKey().equals(mode))) {
            filter.collectionMode = modes.get(0).getKey();
        } else if (mode != null && !modes.isEmpty()) {
            filter.collectionMode = mode;
        }
        String selected = filter.collectionMode;
        filter.publicSale = this != OPEN_RUN;
        switch (this) {
            case NEW:
                filter.recentOnly = true;
                filter.sort = "newest";
                break;
            case OPEN_RUN:
                filter.status = "SCHEDULED";
                filter.available = false;
                filter.sort = "newest";
                break;
            case EXHIBITIONS:
                filter.wishlistRank = "interest".equals(selected);
                filter.salesDays = "sales".equals(selected) ? 7 : 0;
                break;
            case RANKING:
                if ("reviews".equals(selected)) {
                    filter.withReviews = true;
                    filter.sort = "reviews";
                } else if ("new".equals(selected)) {
                    filter.recentOnly = true;
                    filter.sort = "newest";
                } else {
                    filter.salesGrowth = "rising".equals(selected);
                    filter.salesDays = filter.salesGrowth ? 2 : "weekly".equals(selected) ? 7 : 1;
                    filter.sort = "newest";
                }
                break;
            case RATING:
                filter.satisfactoryReview = "satisfaction".equals(selected);
                if (!filter.satisfactoryReview) filter.ratingBand = new BigDecimal(selected);
                filter.sort = "rating";
                break;
            case BEAUTY:
            case HEALTH:
                if (filter.category.isEmpty()) filter.category = root;
                if ("new".equals(selected)) {
                    filter.recentOnly = true;
                    filter.sort = "newest";
                }
                break;
            default:
                throw new IllegalStateException("Unknown product collection");
        }
        if (this != OPEN_RUN && "SCHEDULED".equals(filter.status)) filter.status = "";
    }
}
