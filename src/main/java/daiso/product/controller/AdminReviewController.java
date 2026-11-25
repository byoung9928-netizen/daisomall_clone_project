package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.AdminReviewDAO;
import daiso.product.dao.AdminReviewDAO.DateField;
import daiso.product.dao.AdminReviewDAO.Filter;
import daiso.product.dao.AdminReviewDAO.Moderation;
import daiso.product.dao.AdminReviewDAO.Page;
import daiso.product.dao.AdminReviewDAO.ReportFilter;
import daiso.product.dao.AdminReviewDAO.Sort;
import daiso.product.dao.AdminReviewDAO.State;
import daiso.product.dao.ReviewFeedbackDAO;
import daiso.product.dao.ReviewFeedbackDAO.ReportCounts;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class AdminReviewController {
    private static final String PATH = "/admin/products/reviews";
    private static final String VIEW = "admin/product-reviews";
    private static final String DECISIONS = "product_review_admin_decisions";
    private static final String NOTICE = "adminReviewNotice";
    private static final String RESULTS_ANCHOR = "#review-results";
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 30);

    public enum Period {
        ALL("전체"), TODAY("오늘"), WEEK("최근 7일"), MONTH("최근 1개월"), CUSTOM("직접 지정");

        public final String label;

        Period(String label) { this.label = label; }

        public LocalDate start(LocalDate today) {
            if (this == TODAY) return today;
            if (this == WEEK) return today.minusDays(6);
            if (this == MONTH) return today.minusMonths(1);
            return null;
        }
    }
    private final AdminReviewDAO reviews = new AdminReviewDAO();
    private final ReviewFeedbackDAO feedback = new ReviewFeedbackDAO();

    @DaisoGetMapping(PATH)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        boolean fragment = "1".equals(request.getHeader("X-Review-Fragment"));
        response.setHeader("Vary", "X-Review-Fragment");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) {
            return problem(request, response, 403, "관리자 계정으로 로그인해 주세요.");
        }
        LocalDate today = LocalDate.now(STORE_ZONE);
        Filter filter;
        Period period;
        int requestedPage;
        long selectedReview;
        try {
            period = choice(request, "period", Period.ALL);
            filter = readFilter(request, period, today);
            requestedPage = page(request);
            String selected = ProductQueries.text(request, "detail", 19);
            if (selected.isEmpty()) selected = ProductQueries.text(request, "reportReview", 19);
            selectedReview = selected.isEmpty() ? 0 : Long.parseLong(selected);
            if (!selected.isEmpty() && selectedReview <= 0) throw new IllegalArgumentException();
        } catch (DateTimeParseException error) {
            return problem(request, response, 400, "유효한 시작일과 종료일을 입력해 주세요.");
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "리뷰 검색 조건과 기간을 확인해 주세요. 시작일은 종료일보다 늦을 수 없습니다.");
        }
        try (Connection connection = ProductQueries.connect()) {
            Page results = reviews.list(connection, filter, requestedPage, selectedReview);
            boolean detailVisible = results.items.stream().anyMatch(row -> row.id == selectedReview);
            if (detailVisible && results.reports.getOrDefault(selectedReview, ReportCounts.EMPTY).total > 0) {
                var entries = feedback.reportEntries(connection, selectedReview);
                request.setAttribute("adminReportEntries", entries);
                request.setAttribute("adminReportReasons", entries.stream().collect(
                        Collectors.groupingBy(entry -> entry.reason, Collectors.counting())));
            }
            boolean auditReady = reviews.tableReady(connection, DECISIONS);
            request.setAttribute("adminReviewAuditReady", auditReady);
            if (detailVisible && auditReady) request.setAttribute("adminReviewDecisions", reviews.decisions(connection, selectedReview));
            if (!filter.analysisProduct.isEmpty()) {
                request.setAttribute("adminReviewAnalysis", reviews.analysis(connection, filter.analysisProduct, results));
            }
            connection.commit();
            request.setAttribute("adminReviews", results);
            request.setAttribute("adminReviewFilter", filter);
            request.setAttribute("adminReviewPeriod", period);
            request.setAttribute("adminReviewToday", today);
            request.setAttribute("adminSelectedDetail", detailVisible ? selectedReview : 0L);
            request.setAttribute("adminMissingDetail", selectedReview != 0 && !detailVisible);
            request.setAttribute("adminReviewCsrf", AdminProductController.token(session));
            request.setAttribute("adminReviewFragment", fragment);
            if (!fragment) {
                request.setAttribute(NOTICE, session.getAttribute(NOTICE));
                session.removeAttribute(NOTICE);
            }

            Map<String, String> filters = filters(filter, period);
            Map<String, String> current = ProductView.with(filters, "page", Integer.toString(results.number));
            String context = request.getContextPath();
            request.setAttribute("adminReportViewBase", current);
            request.setAttribute("adminReviewUrl", ProductView.url(context, "/app" + PATH,
                    detailVisible ? ProductView.with(current, "detail", Long.toString(selectedReview)) : current));
            request.setAttribute("adminReviewModerate", ProductView.url(context, "/app" + PATH + "/moderate", current));
            request.setAttribute("adminReviewPagination", new InventoryPagination(results.number, results.pages,
                    number -> ProductView.url(context, "/app" + PATH,
                            ProductView.with(filters, "page", Integer.toString(number))) + RESULTS_ANCHOR));
            Map<ReportFilter, String> cards = new EnumMap<>(ReportFilter.class);
            for (ReportFilter report : ReportFilter.values()) {
                Sort sort = report == ReportFilter.PENDING ? Sort.MOST_PENDING
                        : report == ReportFilter.REPORTED ? Sort.MOST_REPORTED : filter.sort;
                Map<String, String> target = ProductView.with(filters, "reports", report.name());
                cards.put(report, ProductView.url(context, "/app" + PATH, ProductView.with(target, "sort", sort.name()))
                        + RESULTS_ANCHOR);
            }
            request.setAttribute("adminReviewCards", cards);
            return VIEW;
        } catch (NamingException | SQLException error) {
            request.getServletContext().log("관리자 리뷰 목록 조회 실패", error);
            return problem(request, response, 503, "리뷰를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @DaisoPostMapping(PATH + "/moderate")
    public String moderate(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) return problem(request, response, 403, "관리자 계정으로 로그인해 주세요.");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String[] tokens = request.getParameterValues("csrf");
        if (tokens == null || tokens.length != 1 || !AdminProductController.validToken(request, session)) {
            return problem(request, response, 403, "보안 확인에 실패했습니다. 리뷰 관리 화면을 다시 열어 주세요.");
        }

        long id;
        long version;
        Moderation next;
        boolean dismiss;
        String reason;
        Map<String, String> returnFilters;
        try {
            id = Long.parseLong(ProductQueries.text(request, "reviewId", 19));
            version = Long.parseLong(ProductQueries.text(request, "version", 19));
            if (id < 1 || version < 0) throw new IllegalArgumentException();
            String action = ProductQueries.text(request, "moderation", 12);
            dismiss = "DISMISS".equals(action);
            reason = ProductQueries.text(request, "moderationReason", 255);
            next = dismiss ? null : Moderation.valueOf(action);
            Period period = choice(request, "period", Period.ALL);
            Filter filter = readFilter(request, period, LocalDate.now(STORE_ZONE));
            returnFilters = ProductView.with(filters(filter, period), "page", Integer.toString(page(request)));
            returnFilters = ProductView.with(returnFilters, "detail", Long.toString(id));
        } catch (DateTimeParseException | IllegalArgumentException error) {
            return problem(request, response, 400, "리뷰와 변경할 상태를 확인해 주세요.");
        }

        try (Connection connection = ProductQueries.connect()) {
            try {
                String admin = session.getAttribute("member_id").toString();
                boolean auditReady = reviews.tableReady(connection, DECISIONS);
                if (auditReady && reason.isEmpty()) {
                    return problem(request, response, 400, "처리 사유를 입력해 주세요.");
                }
                String before = auditReady ? reviews.lockDecisionState(connection, id) : "";
                boolean changed = dismiss ? reviews.dismissReports(connection, id, version, admin)
                        : reviews.moderate(connection, id, version, admin, next);
                if (changed && auditReady) reviews.recordDecision(connection, id, admin, dismiss ? "DISMISS" : next.name(),
                        before, dismiss ? before : next.name(), reason);
                connection.commit();
                if (!changed) return problem(request, response, 409,
                        "이미 처리됐거나 리뷰 상태가 바뀌었습니다. 목록을 다시 열어 주세요.");
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
            String notice = dismiss ? "미처리 신고를 기각했습니다."
                    : next == Moderation.PUBLISHED ? "리뷰를 복구했습니다."
                    : next == Moderation.HIDDEN ? "리뷰를 숨겼습니다." : "리뷰를 관리자 삭제 처리했습니다.";
            session.setAttribute(NOTICE, notice);
            return "redirect:" + ProductView.rawUrl("", PATH, returnFilters) + "#review-" + id;
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 리뷰 상태 변경 실패", error);
            return problem(request, response, 503, "리뷰 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static Filter readFilter(HttpServletRequest request, Period period, LocalDate today) {
        LocalDate start = period == Period.CUSTOM ? date(request, "start") : period.start(today);
        LocalDate end = period == Period.CUSTOM ? date(request, "end") : today;
        if (start != null && start.isAfter(end)) throw new IllegalArgumentException();
        String reason = ProductQueries.text(request, "reason", 24);
        if (!reason.isEmpty()) daiso.product.dto.ReviewFeedback.Reason.valueOf(reason);
        return new Filter(ProductQueries.text(request, "query", 120),
                ProductQueries.integer(request, "rating", 0, 0, 5), choice(request, "state", State.ALL),
                choice(request, "reports", ReportFilter.ALL), choice(request, "dateField", DateField.REVIEW),
                start == null ? null : start.atStartOfDay(), start == null ? null : end.plusDays(1).atStartOfDay(),
                choice(request, "sort", Sort.NEWEST), reason,
                "1".equals(request.getParameter("withPhoto")), "1".equals(request.getParameter("withReaction")),
                ProductQueries.text(request, "analysisProduct", 100));
    }

    private static LocalDate date(HttpServletRequest request, String name) {
        LocalDate value = LocalDate.parse(ProductQueries.text(request, name, 10));
        if (value.isBefore(MIN_DATE) || value.isAfter(MAX_DATE)) throw new IllegalArgumentException();
        return value;
    }

    private static int page(HttpServletRequest request) {
        return ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE);
    }

    private static <E extends Enum<E>> E choice(HttpServletRequest request, String name, E fallback) {
        String value = ProductQueries.text(request, name, 20);
        return value.isEmpty() ? fallback : Enum.valueOf(fallback.getDeclaringClass(), value);
    }

    private static Map<String, String> filters(Filter filter, Period period) {
        boolean custom = period == Period.CUSTOM;
        Map<String, String> values = new java.util.LinkedHashMap<>(Map.of("query", filter.keyword, "rating", filter.rating == 0 ? "" : Integer.toString(filter.rating),
                "state", filter.state.name(), "reports", filter.reports.name(), "dateField", filter.dateField.name(),
                "period", period.name(), "start", custom ? filter.start.toLocalDate().toString() : "",
                "end", custom ? filter.endExclusive.toLocalDate().minusDays(1).toString() : "",
                "sort", filter.sort.name()));
        values.put("reason", filter.reason);
        values.put("withPhoto", filter.photo ? "1" : "");
        values.put("withReaction", filter.reaction ? "1" : "");
        values.put("analysisProduct", filter.analysisProduct);
        return values;
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("adminReviewError", message);
        return VIEW;
    }
}
