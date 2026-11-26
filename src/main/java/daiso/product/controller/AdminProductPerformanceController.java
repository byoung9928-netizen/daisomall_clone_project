package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductConversionDAO.Metrics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@DaisoController
public final class AdminProductPerformanceController {
    private static final String MAPPING = "/admin/products/performance";
    private static final String ROUTE = "/app" + MAPPING;
    private static final String VIEW = "admin/product-performance";
    private static final int PAGE_SIZE = 20;
    public static final int LOW_VIEW_SAMPLE = 10;
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ProductPerformanceDAO performance = new ProductPerformanceDAO();
    private final ProductDAO products = new ProductDAO();

    public enum Tab {
        OVERVIEW("overview", "종합 성과"), CONVERSION("conversion", "구매 전환 분석"), POPULAR("popular", "인기 상품 분석"), PRODUCTS("products", "상품별 성과");

        public final String key, label;

        Tab(String key, String label) {
            this.key = key;
            this.label = label;
        }

        static Tab read(String key, String detail) {
            for (Tab tab : values()) {
                if (tab.key.equals(key)) return tab;
            }
            return key.isEmpty() && !detail.isEmpty() ? PRODUCTS : OVERVIEW;
        }
    }

    public enum Period {
        TODAY("오늘", 1), WEEK("최근 7일", 7), MONTH("최근 30일", 30), QUARTER("최근 90일", 90), CUSTOM("직접 지정", 0);
        public final String label;
        final int days;

        Period(String label, int days) {
            this.label = label;
            this.days = days;
        }
    }

    public enum Sort {
        REVENUE("순상품 매출 높은순", "revenue DESC"), UNITS("순판매 수량 많은순", "units DESC"),
        VIEW("조회 많은순", "views DESC"), WISH("찜 등록 많은순", "wish_add DESC"),
        CART("장바구니 등록 많은순", "cart_add DESC"), BUYERS("구매 회원 많은순", "buyers DESC"),
        NAME("상품명순", "name ASC"), STOCK("온라인 재고 적은순", "stock_quantity ASC"),
        CONVERSION_HIGH("조회 대비 결제 전환율 높은순", "(f_view<" + LOW_VIEW_SAMPLE
                + "), f_view_paid/NULLIF(f_view,0) DESC"),
        CONVERSION_LOW("조회 대비 결제 전환율 낮은순", "(f_view<" + LOW_VIEW_SAMPLE
                + "), f_view_paid/NULLIF(f_view,0) ASC");

        public final String label;
        final String order;

        Sort(String label, String order) {
            this.label = label;
            this.order = order;
        }
    }

    public enum Rank {
        REVENUE("순상품 매출", "revenue", "원"), UNITS("순판매 수량", "units", "개"),
        VIEW("조회 수", "views", "건"), WISH("찜 등록 수", "wish_add", "건"),
        CART("장바구니 등록 수", "cart_add", "건");

        public final String label, unit;
        final String column;

        Rank(String label, String column, String unit) {
            this.label = label;
            this.column = column;
            this.unit = unit;
        }

        boolean behavior() { return this == VIEW || this == WISH || this == CART; }
    }

    public enum Quick {
        ALL("전체 상품"), SOLD("판매 발생"), UNSOLD("미판매"), TOP_VIEW("조회 상위"),
        LOW_CONVERSION("전환율 낮음"), LOW_STOCK("재고 부족"), EMPTY_STOCK("품절");

        public final String label;
        Quick(String label) { this.label = label; }
    }

    static final class Filter {
        final AdminProductFilter products;
        final Period period;
        final Sort sort;
        final boolean includeDemo;
        final LocalDate start, endExclusive;
        final int page, pageSize, rankingLimit, minimumViews, maxConversion;
        final Rank rank;
        final Quick quick;
        final boolean fromPopular;
        final String detail;
        final Tab tab;

        Filter(HttpServletRequest request) {
            products = AdminProductFilter.read(request, AdminProductController.STATUSES);
            String requestedPeriod = ProductQueries.text(request, "period", 15);
            period = requestedPeriod.isEmpty() ? Period.WEEK : Period.valueOf(requestedPeriod);
            String requestedSort = ProductQueries.text(request, "performanceSort", 15);
            String requestedQuick = ProductQueries.text(request, "quick", 20);
            quick = requestedQuick.isEmpty() ? Quick.ALL : Quick.valueOf(requestedQuick);
            sort = requestedSort.isEmpty() ? (quick == Quick.TOP_VIEW ? Sort.VIEW : Sort.REVENUE) : Sort.valueOf(requestedSort);
            String requestedRank = ProductQueries.text(request, "rank", 12);
            rank = requestedRank.isEmpty() ? Rank.REVENUE : Rank.valueOf(requestedRank);
            rankingLimit = readChoice(request, "rankingLimit", 5, List.of(5, 10));
            pageSize = readChoice(request, "pageSize", PAGE_SIZE, List.of(20, 50, 100));
            minimumViews = readChoice(request, "minimumViews", 10, List.of(10, 30, 50, 100));
            maxConversion = readChoice(request, "maxConversion", 5, List.of(1, 3, 5, 10));
            fromPopular = "popular".equals(request.getParameter("back"));
            String demo = ProductQueries.text(request, "demo", 10);
            if (!List.of("", "exclude", "include").contains(demo)) throw new IllegalArgumentException();
            includeDemo = "include".equals(demo);
            page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            String requestedDetail = ProductQueries.text(request, "detail", 100);
            tab = Tab.read(ProductQueries.text(request, "tab", 20), requestedDetail);
            detail = tab == Tab.PRODUCTS ? requestedDetail : "";
            LocalDate today = LocalDate.now(SEOUL);
            try {
                LocalDate end = period == Period.CUSTOM ? LocalDate.parse(ProductQueries.text(request, "end", 10)) : today;
                start = period == Period.CUSTOM ? LocalDate.parse(ProductQueries.text(request, "start", 10))
                        : today.minusDays(period.days - 1L);
                if (start.getYear() < 2000 || end.isAfter(today) || start.isAfter(end)
                        || ChronoUnit.DAYS.between(start, end) >= 366) throw new IllegalArgumentException();
                endExclusive = end.plusDays(1);
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException("조회 기간이 올바르지 않습니다.", error);
            }
        }

        private static int readChoice(HttpServletRequest request, String name, int fallback, List<Integer> allowed) {
            int value = ProductQueries.integer(request, name, fallback, 1, 100);
            if (!allowed.contains(value)) throw new IllegalArgumentException();
            return value;
        }

        Filter(Filter current) {
            products = current.products;
            period = Period.CUSTOM;
            sort = current.sort;
            includeDemo = current.includeDemo;
            endExclusive = current.start;
            start = endExclusive.minusDays(ChronoUnit.DAYS.between(current.start, current.endExclusive));
            page = current.page;
            pageSize = current.pageSize;
            rankingLimit = current.rankingLimit;
            minimumViews = current.minimumViews;
            maxConversion = current.maxConversion;
            detail = "";
            tab = Tab.OVERVIEW;
            rank = current.rank;
            quick = Quick.ALL;
            fromPopular = false;
        }

        Quick activeQuick() { return tab == Tab.PRODUCTS ? quick : Quick.ALL; }

        LocalDateTime utcStart() { return start.atStartOfDay(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
        LocalDateTime utcEnd() { return endExclusive.atStartOfDay(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }

        Map<String, String> params() {
            Map<String, String> result = new LinkedHashMap<>(products.parameters(1));
            result.put("period", period.name());
            result.put("start", period == Period.CUSTOM ? start.toString() : "");
            result.put("end", period == Period.CUSTOM ? endExclusive.minusDays(1).toString() : "");
            result.put("performanceSort", sort.name());
            result.put("quick", quick == Quick.ALL ? "" : quick.name());
            result.put("pageSize", pageSize == PAGE_SIZE ? "" : Integer.toString(pageSize));
            result.put("rank", rank == Rank.REVENUE ? "" : rank.name());
            result.put("rankingLimit", rankingLimit == 5 ? "" : Integer.toString(rankingLimit));
            result.put("minimumViews", minimumViews == 10 ? "" : Integer.toString(minimumViews));
            result.put("maxConversion", maxConversion == 5 ? "" : Integer.toString(maxConversion));
            result.put("demo", includeDemo ? "include" : "exclude");
            return result;
        }
    }

    @DaisoGetMapping(MAPPING)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        request.setAttribute("adminSection", "product-performance");
        if (AdminProductController.adminSession(request) == null) {
            return problem(request, response, 403, "관리자 계정으로 로그인해 주세요.");
        }
        try (Connection conn = ProductQueries.connect()) {
            Filter filter = new Filter(request);
            ProductPerformanceDAO.Coverage coverage = performance.coverage(conn);
            Page page = new Page(request.getContextPath(), filter, coverage, products.findAdminCategories(conn, false));
            switch (filter.tab) {
                case OVERVIEW:
                    Filter previous = new Filter(filter);
                    page.summary = performance.summary(conn, filter, filter.products, coverage, "");
                    page.previous = performance.summary(conn, previous, filter.products, coverage, "");
                    page.previousStart = previous.start;
                    page.comparableBehavior = coverage.firstDay != null && !previous.start.isBefore(coverage.firstDay);
                    page.trend = performance.trend(conn, filter, coverage);
                    page.previousTrend = performance.trend(conn, previous, new ProductPerformanceDAO.Coverage(false, null));
                    page.ranking = performance.ranking(conn, filter, coverage, Rank.REVENUE, 3);
                    break;
                case CONVERSION:
                    page.conversion = ProductConversionDAO.summary(conn, ProductPerformanceDAO.scope(filter.products, ""),
                            filter, coverage, false);
                    break;
                case POPULAR:
                    if (!filter.rank.behavior() || page.hasBehavior) {
                        page.ranking = performance.ranking(conn, filter, coverage, filter.rank, filter.rankingLimit);
                    }
                    page.categorySales = performance.categorySales(conn, filter);
                    break;
                case PRODUCTS:
                    loadProducts(conn, filter, coverage, page);
                    break;
            }
            request.setAttribute("productPerformance", page);
            conn.commit();
            return VIEW;
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "검색 조건과 조회 기간을 확인해 주세요. 직접 지정은 최대 366일입니다.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("상품 성과 분석 조회 실패", error);
            return problem(request, response, 503, "상품 성과 데이터를 조회하지 못했습니다.");
        }
    }

    private void loadProducts(Connection conn, Filter filter, ProductPerformanceDAO.Coverage coverage, Page page)
            throws SQLException {
        page.totalProducts = performance.count(conn, filter, coverage);
        int pages = Math.max(1, (page.totalProducts - 1) / filter.pageSize + 1);
        int current = Math.min(filter.page, pages);
        page.pagination = new InventoryPagination(current, pages, target -> page.listUrl(target));
        page.rows = performance.rows(conn, filter, filter.products, coverage,
                filter.pageSize, (current - 1) * filter.pageSize, "");
        page.first = page.rows.isEmpty() ? 0 : (current - 1) * filter.pageSize + 1;
        page.last = page.rows.isEmpty() ? 0 : page.first + page.rows.size() - 1;
        if (filter.detail.isEmpty()) return;
        page.selected = page.rows.stream().filter(row -> row.id.equals(filter.detail)).findFirst().orElse(null);
        if (page.selected == null) {
            page.selected = performance.rows(conn, filter, filter.products, coverage, 1, 0, filter.detail)
                    .stream().findFirst().orElse(null);
        }
        if (page.selected != null) {
            AdminProductFilter peerScope = AdminProductFilter.performanceScope(page.selected.category, "");
            page.peers = performance.summary(conn, filter, peerScope, coverage, page.selected.id);
            page.peerConversion = ProductConversionDAO.summary(conn, ProductPerformanceDAO.scope(peerScope, page.selected.id),
                    filter, coverage, true);
        }
    }

    @DaisoGetMapping(MAPPING + "/csv")
    public String csv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        if (AdminProductController.adminSession(request) == null) {
            response.sendError(403);
            return null;
        }
        StringBuilder output = new StringBuilder("\ufeff");
        try (Connection conn = ProductQueries.connect()) {
            Filter filter = new Filter(request);
            ProductPerformanceDAO.Coverage coverage = performance.coverage(conn);
            if (filter.tab == Tab.POPULAR) {
                AdminProductController.csvRow(output, "순위", "상품 ID", "품번", "상품명", "지표", "수치", "단위",
                        "범위", "시작일(KST)", "종료일(KST)", "시연 데이터");
                int position = 0;
                for (ProductPerformanceDAO.Ranked row : performance.ranking(conn, filter, coverage, filter.rank, filter.rankingLimit)) {
                    AdminProductController.csvRow(output, ++position, row.id, row.code, row.name, filter.rank.label,
                            row.value, filter.rank.unit, "TOP " + filter.rankingLimit, filter.start,
                            filter.endExclusive.minusDays(1), filter.includeDemo ? "포함" : "제외");
                }
            } else {
                AdminProductController.csvRow(output, "상품 ID", "품번", "상품명", "카테고리", "판매 상태", "시작일(KST)",
                        "종료일(KST)", "시연 데이터", "행동 첫 기록일(KST)", "기간 조회", "고유 조회 회원", "기간 찜 등록",
                        "기간 찜 취소", "현재 찜 회원", "기간 장바구니 등록", "기간 장바구니 삭제", "현재 장바구니 회원",
                        "기간 유효 구매 회원", "기간 순판매 수량", "기간 순상품 매출(원)", "활성 리뷰 수", "활성 평균 평점", "현재 온라인 재고",
                        "관찰 기간(일)", "조회 코호트 회원", "조회 후 찜 회원", "조회 후 찜 비율(%)",
                        "조회 후 장바구니 회원", "조회 후 장바구니 비율(%)", "조회 후 승인 회원", "조회 대비 결제 전환율(%)",
                        "조회 후 순구매 회원", "조회 대비 순구매율(%)", "직접 결제 회원", "장바구니 경유 결제 회원",
                        "장바구니 코호트 회원", "장바구니 후 승인 회원", "장바구니 대비 결제 전환율(%)",
                        "관찰 완료 장바구니 회원", "관찰 완료 미구매 회원", "최종 미구매율(%)",
                        "주문", "결제 승인 주문", "주문 대비 승인율(%)", "미승인 실패 주문", "결제 실패율(%)",
                        "승인 후 취소 주문", "취소율(%)", "승인 후 환불 주문", "환불율(%)", "조회 표본 상태");
                Map<String, String> categories = products.findAdminCategories(conn, false);
                boolean hasBehavior = hasBehavior(coverage, filter.endExclusive);
                for (ProductPerformanceDAO.Row row : performance.rows(conn, filter, filter.products, coverage, 0, 0, "")) {
                    Metrics flow = row.conversion;
                    AdminProductController.csvRow(output, row.id, row.code, row.name,
                            categories.getOrDefault(row.category, row.category), AdminProductController.STATUSES.getOrDefault(row.status, row.status),
                            filter.start, filter.endExclusive.minusDays(1), filter.includeDemo ? "포함" : "제외",
                            coverage.firstDay, hasBehavior ? row.views : null, hasBehavior ? row.viewers : null,
                            hasBehavior ? row.wishAdds : null, hasBehavior ? row.wishRemoves : null, row.currentWish,
                            hasBehavior ? row.cartAdds : null, hasBehavior ? row.cartRemoves : null, row.currentCart, row.buyers,
                            row.units, row.revenue, row.reviews, row.rating, row.stock,
                            ProductConversionDAO.DAYS, hasBehavior ? flow.viewed : null, hasBehavior ? flow.wished : null,
                            Metrics.percent(flow.wished, flow.viewed), hasBehavior ? flow.cartAfterView : null,
                            Metrics.percent(flow.cartAfterView, flow.viewed), hasBehavior ? flow.viewPaid : null,
                            Metrics.percent(flow.viewPaid, flow.viewed), hasBehavior ? flow.viewNet : null,
                            Metrics.percent(flow.viewNet, flow.viewed), hasBehavior ? flow.direct : null, hasBehavior ? flow.viaCart : null,
                            hasBehavior ? flow.cart : null, hasBehavior ? flow.cartPaid : null, Metrics.percent(flow.cartPaid, flow.cart),
                            hasBehavior ? flow.matureCart : null, hasBehavior ? flow.lostCart : null, Metrics.percent(flow.lostCart, flow.matureCart),
                            flow.orders, flow.paid, Metrics.percent(flow.paid, flow.orders), flow.failed, Metrics.percent(flow.failed, flow.orders),
                            flow.cancelled, Metrics.percent(flow.cancelled, flow.paid), flow.refunded, Metrics.percent(flow.refunded, flow.paid),
                            flow.viewed == 0 ? "분석 불가" : flow.viewed < LOW_VIEW_SAMPLE ? "표본 부족" : "집계 가능");
                }
            }
            conn.commit();
        } catch (IllegalArgumentException error) {
            response.sendError(400);
            return null;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("상품 성과 CSV 조회 실패", error);
            response.sendError(503);
            return null;
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=product-performance.csv");
        response.getWriter().write(output.toString());
        response.flushBuffer();
        return null;
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("productPerformanceError", message);
        return VIEW;
    }

    private static boolean hasBehavior(ProductPerformanceDAO.Coverage coverage, LocalDate endExclusive) {
        return coverage.firstDay != null && coverage.firstDay.isBefore(endExclusive);
    }

    public static final class Page {
        private final String context;
        public final Map<String, String> params, categories;
        public final Map<String, String> statuses = AdminProductController.STATUSES;
        public final Period period;
        public final Tab tab;
        public final String appliedProductFilters;
        public final Sort sort;
        public final boolean includeDemo, behaviorReady, hasBehavior;
        public final LocalDate start, end, firstBehaviorDay;
        public ProductPerformanceDAO.Summary summary, previous, peers;
        public ProductPerformanceDAO.Row selected;
        public Metrics conversion, peerConversion;
        public List<ProductPerformanceDAO.Row> rows = List.of();
        public List<ProductPerformanceDAO.Day> trend = List.of(), previousTrend = List.of();
        public List<ProductPerformanceDAO.Ranked> ranking = List.of();
        public List<ProductPerformanceDAO.CategorySales> categorySales = List.of();
        public InventoryPagination pagination;
        public LocalDate previousStart;
        public boolean comparableBehavior;
        public final boolean fromPopular, partialDay;
        public final String detailId;
        public final Rank rank;
        public final Quick quick;
        public final int rankingLimit, pageSize, minimumViews, maxConversion;
        private final int requestedPage;
        public int first, last, totalProducts;

        Page(String context, Filter filter, ProductPerformanceDAO.Coverage coverage, Map<String, String> categories) {
            this.context = context;
            this.params = filter.params();
            this.categories = categories;
            this.period = filter.period;
            this.tab = filter.tab;
            List<String> labels = new ArrayList<>();
            addLabel(labels, "검색어", params.get("query"));
            addLabel(labels, "선택 상품 ID", params.getOrDefault("selectedProductId", ""));
            String category = params.get("filterCategory");
            addLabel(labels, "카테고리", categories.getOrDefault(category, category));
            addLabel(labels, "판매 상태", statuses.getOrDefault(params.get("filterStatus"), ""));
            if (!params.get("filterStock").isEmpty()) {
                addLabel(labels, "온라인 재고", filter.products.stock.label
                        + (filter.products.stockQuantity == null ? "" : " " + filter.products.stockQuantity + "개"));
            }
            appliedProductFilters = labels.isEmpty() ? "전체 상품" : String.join(" · ", labels);
            this.sort = filter.sort;
            this.includeDemo = filter.includeDemo;
            this.start = filter.start;
            this.end = filter.endExclusive.minusDays(1);
            this.behaviorReady = coverage.ready;
            this.hasBehavior = hasBehavior(coverage, filter.endExclusive);
            this.firstBehaviorDay = coverage.firstDay;
            this.detailId = filter.detail;
            this.rank = filter.rank;
            this.quick = filter.quick;
            this.rankingLimit = filter.rankingLimit;
            this.pageSize = filter.pageSize;
            this.minimumViews = filter.minimumViews;
            this.maxConversion = filter.maxConversion;
            this.requestedPage = filter.page;
            this.fromPopular = filter.fromPopular;
            this.partialDay = end.equals(LocalDate.now(SEOUL));
        }

        public String csv() { return ProductView.url(context, ROUTE + "/csv", tabParams(tab)); }

        public String listUrl(int page) {
            return ProductView.url(context, ROUTE, ProductView.with(tabParams(Tab.PRODUCTS), "page", Integer.toString(page)));
        }

        public String quickUrl(Quick value) {
            Map<String, String> link = ProductView.with(tabParams(Tab.PRODUCTS), "quick", value.name());
            if (value == Quick.TOP_VIEW) link.put("performanceSort", Sort.VIEW.name());
            return ProductView.url(context, ROUTE, link);
        }

        public String rankUrl(Rank metric) {
            return ProductView.url(context, ROUTE, ProductView.with(tabParams(Tab.POPULAR), "rank", metric.name()));
        }

        public String rankLimitUrl(int limit) {
            return ProductView.url(context, ROUTE, ProductView.with(tabParams(Tab.POPULAR), "rankingLimit", Integer.toString(limit)));
        }

        public String categoryUrl(String root) {
            Map<String, String> link = ProductView.with(tabParams(Tab.PRODUCTS), "filterCategory", root);
            link.remove("quick");
            return ProductView.url(context, ROUTE, link);
        }

        public String sortUrl(Sort value) {
            return ProductView.url(context, ROUTE, ProductView.with(tabParams(Tab.PRODUCTS), "performanceSort", value.name()));
        }

        private static void addLabel(List<String> labels, String label, String value) {
            if (!value.isEmpty()) labels.add(label + " " + value);
        }

        private Map<String, String> tabParams(Tab target) {
            return ProductView.with(params, "tab", target.key);
        }

        public String tabUrl(Tab target) {
            return ProductView.url(context, ROUTE,
                    ProductView.with(tabParams(target), "page", Integer.toString(pagination == null ? requestedPage : pagination.page)));
        }

        public String rowAnchor(String productId) {
            return "performance-row-" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(productId.getBytes(StandardCharsets.UTF_8));
        }

        public String detail(String productId) {
            String url = tabUrl(Tab.PRODUCTS);
            if (!productId.isEmpty()) {
                Map<String, String> link = ProductView.with(tabParams(Tab.PRODUCTS), "page",
                        Integer.toString(pagination == null ? requestedPage : pagination.page));
                if (tab == Tab.POPULAR) link.put("back", "popular");
                return ProductView.url(context, ROUTE, ProductView.with(link, "detail", productId))
                        + "#performance-detail-title";
            }
            if (fromPopular) return tabUrl(Tab.POPULAR) + "#performance-tab-popular";
            boolean onPage = rows.stream().anyMatch(row -> row.id.equals(detailId));
            return url + "#" + (onPage ? rowAnchor(detailId) : "performance-results-title");
        }
    }
}
