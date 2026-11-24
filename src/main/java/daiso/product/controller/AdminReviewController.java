package daiso.product.controller;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.product.dao.AdminReviewDAO;
import daiso.product.dao.AdminReviewDAO.Page;
import daiso.product.dao.AdminReviewDAO.Moderation;
import daiso.product.dao.AdminReviewDAO.State;
import daiso.product.dao.AdminReviewDAO.ReportFilter;
import daiso.product.dao.ReviewFeedbackDAO;
import daiso.product.dao.ReviewFeedbackDAO.ReportCounts;
import java.util.stream.Collectors;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@DaisoController
public final class AdminReviewController {
    private static final String PATH = "/admin/products/reviews";
    private static final String VIEW = "admin/product-reviews";
    private static final String NOTICE = "adminReviewNotice";
    private final AdminReviewDAO reviews = new AdminReviewDAO();
    private final ReviewFeedbackDAO feedback = new ReviewFeedbackDAO();

    @DaisoGetMapping(PATH)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        HttpSession session = AdminProductController.adminSession(request);
        if (session == null) {
            return problem(request, response, 403, "관리자 계정으로 로그인해 주세요.");
        }
        String keyword;
        int rating;
        int requestedPage;
        State state;
        ReportFilter reportFilter;
        long selectedReport;
        try {
            keyword = ProductQueries.text(request, "query", 120);
            rating = ProductQueries.integer(request, "rating", 0, 0, 5);
            requestedPage = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE);
            String rawState = ProductQueries.text(request, "state", 12);
            state = rawState.isEmpty() ? State.ALL : State.valueOf(rawState);
            reportFilter = reportFilter(request);
            String selected = ProductQueries.text(request, "reportReview", 19);
            selectedReport = selected.isEmpty() ? 0 : Long.parseLong(selected);
            if (!selected.isEmpty() && selectedReport <= 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "리뷰 검색 조건을 확인해 주세요.");
        }
        try (Connection connection = ProductQueries.connect()) {
            Page results = reviews.list(connection, keyword, rating, state, reportFilter, requestedPage);
            if (results.reports.getOrDefault(selectedReport, ReportCounts.EMPTY).total > 0) {
                var entries = feedback.reportEntries(connection, selectedReport);
                request.setAttribute("adminSelectedReport", selectedReport);
                request.setAttribute("adminReportEntries", entries);
                request.setAttribute("adminReportReasons", entries.stream().collect(
                        Collectors.groupingBy(entry -> entry.reason, Collectors.counting())));
            }
            connection.commit();
            request.setAttribute("adminReviews", results);
            request.setAttribute("adminReviewQuery", keyword);
            request.setAttribute("adminReviewRating", rating);
            request.setAttribute("adminReviewState", state.name());
            request.setAttribute("adminReportFilter", reportFilter.name());

            request.setAttribute("adminReviewCsrf", AdminProductController.token(session));
            request.setAttribute(NOTICE, session.getAttribute(NOTICE));
            session.removeAttribute(NOTICE);
            Map<String, String> filters = filters(keyword, rating, state, reportFilter);
            String context = request.getContextPath();
            request.setAttribute("adminReportViewBase", ProductView.with(filters, "page", Integer.toString(results.number)));
            request.setAttribute("adminReviewModerate", ProductView.url(context, "/app" + PATH + "/moderate",
                    ProductView.with(filters, "page", Integer.toString(results.number))));
            if (results.number > 1) {
                request.setAttribute("adminReviewPrevious", pageLink(context, filters, results.number - 1));
            }
            if (results.number < results.pages && results.number < Integer.MAX_VALUE) {
                request.setAttribute("adminReviewNext", pageLink(context, filters, results.number + 1));
            }
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
        Map<String, String> returnFilters;
        try {
            id = Long.parseLong(ProductQueries.text(request, "reviewId", 19));
            version = Long.parseLong(ProductQueries.text(request, "version", 19));
            if (id < 1 || version < 0) throw new IllegalArgumentException();
            String action = ProductQueries.text(request, "moderation", 12);
            dismiss = "DISMISS".equals(action);
            next = dismiss ? null : Moderation.valueOf(action);
            String keyword = ProductQueries.text(request, "query", 120);
            int rating = ProductQueries.integer(request, "rating", 0, 0, 5);
            int page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE);
            String rawState = ProductQueries.text(request, "state", 12);
            State state = rawState.isEmpty() ? State.ALL : State.valueOf(rawState);
            returnFilters = ProductView.with(filters(keyword, rating, state, reportFilter(request)), "page", Integer.toString(page));
        } catch (IllegalArgumentException error) {
            return problem(request, response, 400, "리뷰와 변경할 상태를 확인해 주세요.");
        }

        try (Connection connection = ProductQueries.connect()) {
            try {
                String admin = session.getAttribute("member_id").toString();
                boolean changed = dismiss ? reviews.dismissReports(connection, id, version, admin)
                        : reviews.moderate(connection, id, version, admin, next);
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
            return "redirect:" + ProductView.rawUrl("", PATH, returnFilters);
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("관리자 리뷰 상태 변경 실패", error);
            return problem(request, response, 503, "리뷰 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static ReportFilter reportFilter(HttpServletRequest request) {
        String value = ProductQueries.text(request, "reports", 12);
        return value.isEmpty() ? ReportFilter.ALL : ReportFilter.valueOf(value);
    }

    private static Map<String, String> filters(String keyword, int rating, State state, ReportFilter reportFilter) {
        return Map.of("query", keyword, "rating", rating == 0 ? "" : Integer.toString(rating),
                "state", state == State.ALL ? "" : state.name(),
                "reports", reportFilter == ReportFilter.ALL ? "" : reportFilter.name());
    }

    private static String pageLink(String context, Map<String, String> filters, int number) {
        return ProductView.url(context, "/app" + PATH, ProductView.with(filters, "page", Integer.toString(number)));
    }

    private static String problem(HttpServletRequest request, HttpServletResponse response, int status, String message) {
        response.setStatus(status);
        request.setAttribute("adminReviewError", message);
        return VIEW;
    }
}
