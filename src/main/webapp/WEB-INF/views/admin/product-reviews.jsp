<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.format.DateTimeFormatter,java.util.List,java.util.Map,daiso.product.controller.ProductView,daiso.product.dao.AdminReviewDAO.Moderation,daiso.product.dao.AdminReviewDAO.Page,daiso.product.dao.AdminReviewDAO.Row,daiso.product.dto.ReviewExtras.Answer,daiso.product.dao.ReviewFeedbackDAO.ReportCounts,daiso.product.dao.ReviewFeedbackDAO.ReportEntry,daiso.product.dto.ReviewFeedback.Reason" %>
<%
    request.setAttribute("adminSection", "product-reviews");
    String context = request.getContextPath();
    String route = context + "/app/admin/products/reviews";
    Page results = (Page) request.getAttribute("adminReviews");
    String state = (String) request.getAttribute("adminReviewState");
    int rating = (Integer) (request.getAttribute("adminReviewRating") == null ? 0 : request.getAttribute("adminReviewRating"));
    String errorMessage = (String) request.getAttribute("adminReviewError");
    String reportFilter = (String) request.getAttribute("adminReportFilter");
    Map<String, String> reportViewBase = (Map<String, String>) request.getAttribute("adminReportViewBase");
    DateTimeFormatter dates = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <script defer src="${pageContext.request.contextPath}/assets/js/common/admin-reviews.js"></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" tabindex="-1">
            <h1 class="page-title">리뷰 관리</h1>
            <% if (errorMessage != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(errorMessage) %></p>
                <% if (response.getStatus() == 403) { %>
                    <a class="button secondary" href="<%= ProductView.escape(context + "/app/member/login") %>">로그인 화면</a>
                <% } else { %>
                    <a class="button secondary" href="<%= ProductView.escape(route) %>">리뷰 목록으로</a>
                <% } %>
            <% } %>
            <% if (request.getAttribute("adminReviewNotice") != null) { %>
                <p class="form-status" role="status"><%= ProductView.escape(request.getAttribute("adminReviewNotice")) %></p>
            <% } %>
            <% if (results != null) { %>
                <% if (!results.reportsAvailable) { %>
                    <p class="form-status">신고 기능을 사용하려면 리뷰 신고 테이블 마이그레이션을 적용해 주세요.</p>
                <% } %>
                <div class="admin-product-toolbar">
                <form class="admin-product-filters" action="<%= ProductView.escape(route) %>" method="get">
                    <label class="field admin-product-filter-row">
                        <span>리뷰 검색</span>
                        <input type="search" name="query" maxlength="120" placeholder="상품명, 품번, 작성자 또는 리뷰 내용"
                               value="<%= ProductView.escape(request.getAttribute("adminReviewQuery")) %>">
                    </label>
                    <label class="field admin-product-filter-row">
                        <span>별점</span>
                        <select name="rating">
                            <option value="">전체 별점</option>
                            <% for (int score = 5; score >= 1; score--) { %>
                                <option value="<%= score %>" <%= score == rating ? "selected" : "" %>><%= score %>점</option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field admin-product-filter-row">
                        <span>상태</span>
                        <select name="state">
                            <option value="">전체 상태</option>
                            <option value="ACTIVE" <%= "ACTIVE".equals(state) ? "selected" : "" %>>게시 중</option>
                            <option value="HIDDEN" <%= "HIDDEN".equals(state) ? "selected" : "" %>>숨김</option>
                            <option value="REMOVED" <%= "REMOVED".equals(state) ? "selected" : "" %>>관리자 삭제</option>
                            <option value="DELETED" <%= "DELETED".equals(state) ? "selected" : "" %>>작성자 삭제</option>
                        </select>
                    </label>
                    <% if (results.reportsAvailable) { %>
                        <label class="field admin-product-filter-row">
                            <span>신고</span>
                            <select name="reports">
                                <option value="">전체 리뷰</option>
                                <option value="REPORTED" <%= "REPORTED".equals(reportFilter) ? "selected" : "" %>>신고된 리뷰</option>
                                <option value="PENDING" <%= "PENDING".equals(reportFilter) ? "selected" : "" %>>미처리 신고가 있는 리뷰</option>
                            </select>
                        </label>
                    <% } %>
                    <div class="admin-product-actions admin-product-filter-actions">
                        <button class="button secondary" type="submit">검색</button>
                        <a class="text-button" href="<%= ProductView.escape(route) %>">초기화</a>
                    </div>
                </form>
                </div>

                <p class="admin-count" role="status"><%= results.total %>개 리뷰</p>
                <div class="admin-review-results">
                    <% if (results.items.isEmpty()) { %><p>조건에 맞는 리뷰가 없습니다.</p><% } %>
                    <% for (Row item : results.items) {
                        String statusLabel = item.deleted ? "작성자 삭제" : item.moderation == Moderation.HIDDEN ? "숨김"
                                : item.moderation == Moderation.DELETED ? "관리자 삭제" : "게시 중";
                        ReportCounts reportCounts = results.reports.getOrDefault(item.id, ReportCounts.EMPTY);
                        boolean reportOpen = Long.valueOf(item.id).equals(request.getAttribute("adminSelectedReport"));
                        String reportAnchor = "review-reports-" + item.id;
                    %>
                        <details class="admin-panel admin-review-card" id="<%= reportAnchor %>" <%= reportOpen ? "open" : "" %>>
                            <summary>
                                <strong><%= ProductView.escape(item.productName) %></strong>
                                <span> · <%= item.rating %>점 · <%= statusLabel %></span>
                                <% if (results.reportsAvailable) { %>
                                    <span> · 신고 <%= reportCounts.total %>건 · 미처리 <%= reportCounts.pending %>건</span>
                                <% } %>
                            </summary>
                            <p><%= ProductView.escape(item.productCode) %> · <%= ProductView.escape(item.loginId) %> ·
                                <time datetime="<%= item.createdAt %>"><%= item.createdAt.format(dates) %></time></p>
                            <% if (item.moderatedAt != null) { %>
                                <p>관리자 처리 <time datetime="<%= item.moderatedAt %>"><%= item.moderatedAt.format(dates) %></time></p>
                            <% } %>
                            <p class="admin-review-text"><%= ProductView.escape(item.content) %></p>
                            <% List<Answer> answers = results.answers.getOrDefault(item.id, List.of());
                               if (!answers.isEmpty()) { %>
                                <dl>
                                    <% for (Answer answer : answers) { %>
                                        <dt><%= ProductView.escape(answer.question) %></dt>
                                        <dd><%= ProductView.escape(answer.label) %></dd>
                                    <% } %>
                                </dl>
                            <% } %>
                            <% List<Long> photos = results.photos.getOrDefault(item.id, List.of());
                               if (!item.deleted && !photos.isEmpty()) { %>
                                <div class="admin-review-photos">
                                    <% int number = 1;
                                       for (long photo : photos) {
                                           String photoLink = ProductView.escape(context + "/private-product-review-photo?id=" + photo);
                                    %>
                                        <a href="<%= photoLink %>"><img src="<%= photoLink %>" alt="첨부 사진 <%= number++ %> 크게 보기"
                                                                       loading="lazy" width="96" height="96"></a>
                                    <% } %>
                                </div>
                            <% } %>
                            <p><a class="text-button" href="<%= ProductView.url(context, "/product.jsp", Map.of("id", item.productId)) %>#product-reviews">고객 상품 리뷰 보기</a></p>
                            <% if (reportCounts.total > 0 && !reportOpen) { %>
                                <p><a class="button secondary" href="<%= ProductView.url(context, "/app/admin/products/reviews",
                                    ProductView.with(reportViewBase, "reportReview", Long.toString(item.id))) %>#<%= reportAnchor %>">신고 확인</a></p>
                            <% } %>
                            <% if (reportOpen) {
                                   Map<Reason, Long> reasons = (Map<Reason, Long>) request.getAttribute("adminReportReasons");
                                   List<ReportEntry> reports = (List<ReportEntry>) request.getAttribute("adminReportEntries");
                            %>
                                <section aria-labelledby="<%= reportAnchor %>-title">
                                    <h2 id="<%= reportAnchor %>-title">신고 내역 · <%= reportCounts.total %>건</h2>
                                    <ul>
                                        <% for (Reason reason : Reason.values()) {
                                               if (!reasons.containsKey(reason)) continue; %>
                                            <li><%= reason.label %> <%= reasons.get(reason) %>건</li>
                                        <% } %>
                                    </ul>
                                    <ul>
                                        <% for (ReportEntry entry : reports) { %>
                                            <li><%= entry.reason.label %> · <%= entry.status.label %> · 접수
                                                <time datetime="<%= entry.received %>"><%= entry.received.format(dates) %></time>
                                                <% if (entry.processed != null) { %>
                                                    · 처리 <time datetime="<%= entry.processed %>"><%= entry.processed.format(dates) %></time>
                                                <% } %>
                                            </li>
                                        <% } %>
                                    </ul>
                                </section>
                            <% } %>
                            <% if (!item.deleted || (reportOpen && reportCounts.pending > 0)) { %>
                                <form class="admin-product-actions" method="post" data-admin-review-action
                                      action="<%= request.getAttribute("adminReviewModerate") %>">
                                    <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("adminReviewCsrf")) %>">
                                    <input type="hidden" name="reviewId" value="<%= item.id %>">
                                    <input type="hidden" name="version" value="<%= item.version %>">
                                    <% if (reportOpen && reportCounts.pending > 0) { %>
                                        <button class="button secondary" name="moderation" value="DISMISS"
                                                data-confirm="리뷰 상태를 유지하고 미처리 신고를 기각할까요?">리뷰 유지 · 신고 기각</button>
                                    <% } %>
                                    <% if (item.canChangeTo(Moderation.HIDDEN)) { %>
                                        <button class="button secondary" name="moderation" value="HIDDEN" data-confirm="이 리뷰를 고객 화면에서 숨길까요?">숨김</button>
                                    <% } %>
                                    <% if (item.canChangeTo(Moderation.DELETED)) { %>
                                        <button class="button secondary" name="moderation" value="DELETED" data-confirm="이 리뷰를 관리자 삭제 처리할까요? 이후 복구할 수 있습니다.">삭제</button>
                                    <% } %>
                                    <% if (item.canChangeTo(Moderation.PUBLISHED)) { %>
                                        <button class="button primary" name="moderation" value="PUBLISHED" data-confirm="이 리뷰를 고객 화면에 다시 게시할까요?">복구</button>
                                    <% } %>
                                </form>
                            <% } %>
                        </details>
                    <% } %>
                </div>
                <% if (results.pages > 1) { %>
                    <nav class="admin-product-pagination" aria-label="리뷰 목록 페이지">
                        <% if (request.getAttribute("adminReviewPrevious") != null) { %>
                            <a class="button secondary" rel="prev" href="<%= request.getAttribute("adminReviewPrevious") %>">이전 페이지</a>
                        <% } %>
                        <span aria-current="page"><%= results.number %> / <%= results.pages %></span>
                        <% if (request.getAttribute("adminReviewNext") != null) { %>
                            <a class="button secondary" rel="next" href="<%= request.getAttribute("adminReviewNext") %>">다음 페이지</a>
                        <% } %>
                    </nav>
                <% } %>
            <% } %>
        </main>
    </div>
</body>
</html>
