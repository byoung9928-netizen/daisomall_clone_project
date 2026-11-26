package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.product.controller.ProductConversionDAO.Metrics;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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

    static final class Filter {
        final AdminProductFilter products;
        final Period period;
        final Sort sort;
        final boolean includeDemo;
        final LocalDate start, endExclusive;
        final int page;
        final String detail;

        Filter(HttpServletRequest request) {
            products = AdminProductFilter.read(request, AdminProductController.STATUSES);
            String requestedPeriod = ProductQueries.text(request, "period", 15);
            period = requestedPeriod.isEmpty() ? Period.WEEK : Period.valueOf(requestedPeriod);
            String requestedSort = ProductQueries.text(request, "performanceSort", 15);
            sort = requestedSort.isEmpty() ? Sort.REVENUE : Sort.valueOf(requestedSort);
            String demo = ProductQueries.text(request, "demo", 10);
            if (!List.of("", "exclude", "include").contains(demo)) throw new IllegalArgumentException();
            includeDemo = "include".equals(demo);
            page = ProductQueries.integer(request, "page", 1, 1, 1_000_000);
            detail = ProductQueries.text(request, "detail", 100);
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

        LocalDateTime utcStart() { return start.atStartOfDay(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
        LocalDateTime utcEnd() { return endExclusive.atStartOfDay(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }

        Map<String, String> params() {
            Map<String, String> result = new LinkedHashMap<>(products.parameters(1));
            result.put("period", period.name());
            result.put("start", period == Period.CUSTOM ? start.toString() : "");
            result.put("end", period == Period.CUSTOM ? endExclusive.minusDays(1).toString() : "");
            result.put("performanceSort", sort.name());
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
            ProductPerformanceDAO.Summary total = performance.summary(conn, filter, filter.products, coverage, "");
            int pages = Math.max(1, (total.products + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = Math.min(filter.page, pages);
            List<ProductPerformanceDAO.Row> rows = performance.rows(conn, filter, filter.products, coverage,
                    PAGE_SIZE, (page - 1) * PAGE_SIZE, "");
            ProductPerformanceDAO.Row selected = null;
            ProductPerformanceDAO.Summary peers = null;
            Metrics peerConversion = null;
            Metrics conversion = ProductConversionDAO.summary(conn, ProductPerformanceDAO.scope(filter.products, ""),
                    filter, coverage, false);
            if (!filter.detail.isEmpty()) {
                selected = rows.stream().filter(row -> row.id.equals(filter.detail)).findFirst().orElse(null);
                if (selected == null) {
                    selected = performance.rows(conn, filter, filter.products, coverage, 1, 0, filter.detail)
                            .stream().findFirst().orElse(null);
                }
                if (selected != null) {
                    AdminProductFilter peerProducts = AdminProductFilter.performanceScope(selected.category, "");
                    peers = performance.summary(conn, filter, peerProducts, coverage, selected.id);
                    peerConversion = ProductConversionDAO.summary(conn, ProductPerformanceDAO.scope(peerProducts, selected.id),
                            filter, coverage, true);
                }
            }
            request.setAttribute("productPerformance", new Page(request.getContextPath(), filter, coverage, total,
                    products.findAdminCategories(conn, false), rows, performance.trend(conn, filter, coverage),
                    selected, peers, conversion, peerConversion, page, pages));
            conn.commit();
            return VIEW;
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "검색 조건과 조회 기간을 확인해 주세요. 직접 지정은 최대 366일입니다.");
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("상품 성과 분석 조회 실패", error);
            return problem(request, response, 503, "상품 성과 데이터를 조회하지 못했습니다.");
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
        public final Sort sort;
        public final boolean includeDemo, behaviorReady, hasBehavior;
        public final LocalDate start, end, firstBehaviorDay;
        public final ProductPerformanceDAO.Summary summary, peers;
        public final ProductPerformanceDAO.Row selected;
        public final Metrics conversion, peerConversion;
        public final List<ProductPerformanceDAO.Row> rows;
        public final List<ProductPerformanceDAO.Day> trend;
        public final InventoryPagination pagination;
        public final String detailId;
        public final int first, last;

        Page(String context, Filter filter, ProductPerformanceDAO.Coverage coverage, ProductPerformanceDAO.Summary summary,
                Map<String, String> categories, List<ProductPerformanceDAO.Row> rows, List<ProductPerformanceDAO.Day> trend,
                ProductPerformanceDAO.Row selected, ProductPerformanceDAO.Summary peers,
                Metrics conversion, Metrics peerConversion, int page, int pages) {
            this.context = context;
            this.params = filter.params();
            this.categories = categories;
            this.period = filter.period;
            this.sort = filter.sort;
            this.includeDemo = filter.includeDemo;
            this.start = filter.start;
            this.end = filter.endExclusive.minusDays(1);
            this.behaviorReady = coverage.ready;
            this.hasBehavior = hasBehavior(coverage, filter.endExclusive);
            this.firstBehaviorDay = coverage.firstDay;
            this.summary = summary;
            this.rows = rows;
            this.trend = trend;
            this.selected = selected;
            this.conversion = conversion;
            this.peerConversion = peerConversion;
            this.peers = peers;
            this.detailId = filter.detail;
            first = rows.isEmpty() ? 0 : (page - 1) * PAGE_SIZE + 1;
            last = rows.isEmpty() ? 0 : first + rows.size() - 1;
            pagination = new InventoryPagination(page, pages, target -> ProductView.url(context, ROUTE,
                    ProductView.with(params, "page", Integer.toString(target))));
        }

        public String csv() { return ProductView.url(context, ROUTE + "/csv", params); }
        public String detail(String productId) {
            Map<String, String> link = ProductView.with(params, "page", Integer.toString(pagination.page));
            return ProductView.url(context, ROUTE, ProductView.with(link, "detail", productId))
                    + (productId.isEmpty() ? "#performance-results" : "#product-performance-detail");
        }
    }
}
