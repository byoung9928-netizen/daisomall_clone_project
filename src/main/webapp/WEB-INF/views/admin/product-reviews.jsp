<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate,java.time.format.DateTimeFormatter,java.text.NumberFormat,java.util.List,java.util.Map,daiso.product.controller.ProductView,daiso.product.controller.InventoryPagination,daiso.product.controller.AdminReviewController.Period,daiso.product.dao.AdminReviewDAO.Filter,daiso.product.dao.AdminReviewDAO.State,daiso.product.dao.AdminReviewDAO.DateField,daiso.product.dao.AdminReviewDAO.ReportFilter,daiso.product.dao.AdminReviewDAO.Sort,daiso.product.dao.AdminReviewDAO.Moderation,daiso.product.dao.AdminReviewDAO.Page,daiso.product.dao.AdminReviewDAO.Row,daiso.product.dto.ReviewExtras.Answer,daiso.product.dao.ReviewFeedbackDAO.ReportCounts,daiso.product.dao.ReviewFeedbackDAO.ReportEntry,daiso.product.dto.ReviewFeedback.Reason" %>
<%
    request.setAttribute("adminSection", "product-reviews");
    String context = request.getContextPath();
    String route = context + "/app/admin/products/reviews";
    Page results = (Page) request.getAttribute("adminReviews");
    Filter filter = (Filter) request.getAttribute("adminReviewFilter");
    Period reviewPeriod = (Period) request.getAttribute("adminReviewPeriod");
    LocalDate reviewToday = (LocalDate) request.getAttribute("adminReviewToday");
    String errorMessage = (String) request.getAttribute("adminReviewError");
    Map<String, String> reportViewBase = (Map<String, String>) request.getAttribute("adminReportViewBase");
    Map<ReportFilter, String> cards = (Map<ReportFilter, String>) request.getAttribute("adminReviewCards");
    NumberFormat counts = NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
    DateTimeFormatter dates = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-reviews.css?v=2">
    <script defer src="${pageContext.request.contextPath}/assets/js/common/admin-reviews.js?v=2"></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content admin-review-page" tabindex="-1">
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
                <nav class="store-metrics admin-review-metrics" aria-label="리뷰 및 신고 요약">
                    <% for (ReportFilter bucket : ReportFilter.values()) {
                           boolean canFilter = results.reportsAvailable || bucket == ReportFilter.ALL;
                           String cardValue = canFilter ? counts.format(results.summary.get(bucket))
                                   + ("리뷰".equals(bucket.unit) ? "개" : "건") : "—";
                    %>
                        <% if (canFilter) { %>
                            <a href="<%= cards.get(bucket) %>" data-review-metric="<%= bucket.name() %>"
                               <%= filter.reports == bucket ? "aria-current=\"true\"" : "" %>>
                                <span><%= bucket.label %></span><strong><%= cardValue %></strong>
                            </a>
                        <% } else { %>
                            <div data-review-metric="<%= bucket.name() %>">
                                <span><%= bucket.label %></span><strong aria-label="집계할 수 없음"><%= cardValue %></strong>
                            </div>
                        <% } %>
                    <% } %>
                </nav>
                <p class="admin-review-scope">요약은 검색 조건과 조회 기간을 기준으로 집계하며, 처리 완료에는 기각 건도 포함됩니다. 표의 신고 건수와 신고 정렬은 리뷰별 전체 신고 이력을 기준으로 합니다.</p>

                <form id="admin-review-filters" class="admin-search admin-review-filters" action="<%= ProductView.escape(route) %>" method="get">
                    <div class="admin-review-fields">
                        <label class="field">리뷰 검색
                            <input type="search" name="query" maxlength="120" placeholder="상품명, 품번, 작성자 또는 리뷰 내용"
                                   value="<%= ProductView.escape(filter.keyword) %>">
                        </label>
                        <label class="field">별점
                            <select name="rating">
                                <option value="">전체 별점</option>
                                <% for (int score = 5; score >= 1; score--) { %>
                                    <option value="<%= score %>" <%= score == filter.rating ? "selected" : "" %>><%= score %>점</option>
                                <% } %>
                            </select>
                        </label>
                        <label class="field">상태
                            <select name="state">
                                <option value="ALL" <%= filter.state == State.ALL ? "selected" : "" %>>전체 상태</option>
                                <option value="ACTIVE" <%= filter.state == State.ACTIVE ? "selected" : "" %>>게시 중</option>
                                <option value="HIDDEN" <%= filter.state == State.HIDDEN ? "selected" : "" %>>숨김</option>
                                <option value="REMOVED" <%= filter.state == State.REMOVED ? "selected" : "" %>>관리자 삭제</option>
                                <option value="DELETED" <%= filter.state == State.DELETED ? "selected" : "" %>>작성자 삭제</option>
                            </select>
                        </label>
                        <% if (results.reportsAvailable) { %>
                            <label class="field">신고
                                <select name="reports">
                                    <% for (ReportFilter value : ReportFilter.values()) { %>
                                        <option value="<%= value.name() %>" <%= value == filter.reports ? "selected" : "" %>><%= value.label %></option>
                                    <% } %>
                                </select>
                            </label>
                        <% } %>
                    </div>
                    <fieldset class="admin-review-period">
                        <legend>조회 기간</legend>
                        <div class="admin-review-dates">
                            <label class="field">조회 기준
                                <select name="dateField">
                                    <% for (DateField value : DateField.values()) {
                                           if (value != DateField.REVIEW && !results.reportsAvailable && value != filter.dateField) continue; %>
                                        <option value="<%= value.name() %>" <%= value == filter.dateField ? "selected" : "" %>><%= value.label %></option>
                                    <% } %>
                                </select>
                            </label>
                            <div class="admin-review-presets" role="group" aria-label="빠른 기간 선택">
                                <% for (Period value : Period.values()) {
                                       LocalDate preset = value.start(reviewToday); %>
                                    <label>
                                        <input type="radio" name="period" value="<%= value.name() %>" <%= value == reviewPeriod ? "checked" : "" %>
                                               data-start="<%= ProductView.escape(preset) %>" data-end="<%= preset == null ? "" : reviewToday %>">
                                        <span><%= value.label %></span>
                                    </label>
                                <% } %>
                            </div>
                            <label class="field">시작일
                                <input type="date" name="start" min="1000-01-01" max="9999-12-30"
                                       value="<%= filter.start == null ? "" : filter.start.toLocalDate() %>">
                            </label>
                            <label class="field">종료일
                                <input type="date" name="end" min="1000-01-01" max="9999-12-30"
                                       value="<%= filter.endExclusive == null ? "" : filter.endExclusive.toLocalDate().minusDays(1) %>">
                            </label>
                        </div>
                    </fieldset>
                    <div class="admin-product-actions">
                        <button class="button primary" type="submit">검색</button>
                        <a class="button secondary" href="<%= ProductView.escape(route) %>">초기화</a>
                    </div>
                </form>

                <section id="review-results" tabindex="-1" aria-label="리뷰 검색 결과"
                         <%= Boolean.TRUE.equals(request.getAttribute("adminMissingDetail")) ? "data-review-fallback" : "" %>>
                    <div class="admin-review-list-heading">
                        <p class="admin-count" role="status">검색 결과 <strong><%= counts.format(results.total) %>개</strong></p>
                        <div class="admin-review-sort">
                            <label for="admin-review-sort">정렬</label>
                            <select id="admin-review-sort" name="sort" form="admin-review-filters">
                                <% for (Sort sort : Sort.values()) { %>
                                    <option value="<%= sort.name() %>" <%= sort == filter.sort ? "selected" : "" %>><%= sort.label %></option>
                                <% } %>
                            </select>
                            <noscript><button type="submit" class="button secondary" form="admin-review-filters">적용</button></noscript>
                        </div>
                    </div>
                    <% if (results.items.isEmpty()) { %>
                        <p class="admin-panel">조건에 맞는 리뷰가 없습니다.</p>
                    <% } else { %>
                        <div class="admin-table-scroll">
                            <table class="admin-table admin-review-table">
                                <caption class="visually-hidden">관리자 리뷰 목록. 신고 건수는 리뷰별 전체 이력 기준입니다.</caption>
                                <thead><tr>
                                    <th scope="col">상품명·품번</th><th scope="col">작성자</th><th scope="col">별점</th>
                                    <th scope="col">리뷰 내용</th><th scope="col">작성일</th><th scope="col">리뷰 상태</th>
                                    <th scope="col">전체 신고</th><th scope="col">미처리 신고</th><th scope="col">관리</th>
                                </tr></thead>
                                <tbody>
                                <% for (Row item : results.items) {
                                       String statusLabel = item.deleted ? "작성자 삭제" : item.moderation == Moderation.HIDDEN ? "숨김"
                                               : item.moderation == Moderation.DELETED ? "관리자 삭제" : "게시 중";
                                       ReportCounts reportCounts = results.reports.getOrDefault(item.id, ReportCounts.EMPTY);
                                       boolean detailOpen = Long.valueOf(item.id).equals(request.getAttribute("adminSelectedDetail"));
                                       String reviewAnchor = "review-" + item.id;
                                       String detailUrl = ProductView.url(context, "/app/admin/products/reviews",
                                               ProductView.with(reportViewBase, "detail", Long.toString(item.id)));
                                %>
                                    <tr id="<%= reviewAnchor %>">
                                        <th scope="row"><strong><%= ProductView.escape(item.productName) %></strong>
                                            <small><%= ProductView.escape(item.productCode) %></small></th>
                                        <td><%= ProductView.escape(item.loginId) %></td>
                                        <td><%= item.rating %>점</td>
                                        <td><span class="admin-review-preview"><%= ProductView.escape(item.content) %></span></td>
                                        <td><time datetime="<%= item.createdAt %>"><%= item.createdAt.format(dates) %></time></td>
                                        <td><%= statusLabel %></td>
                                        <td><%= results.reportsAvailable ? counts.format(reportCounts.total) + "건" : "—" %></td>
                                        <td class="<%= reportCounts.pending > 0 ? "admin-review-pending" : "" %>">
                                            <%= results.reportsAvailable ? counts.format(reportCounts.pending) + "건" : "—" %></td>
                                        <td class="admin-review-links">
                                            <% if (detailOpen) { %>
                                                <a class="text-button" href="<%= ProductView.url(context, "/app/admin/products/reviews", reportViewBase) %>#<%= reviewAnchor %>">닫기</a>
                                            <% } else { %>
                                                <a class="text-button" href="<%= detailUrl %>#<%= reviewAnchor %>">상세 보기</a>
                                            <% } %>
                                            <% if (reportCounts.total > 0) { %>
                                                <a class="text-button" href="<%= detailUrl %>#<%= reviewAnchor %>-reports">신고 확인</a>
                                            <% } %>
                                        </td>
                                    </tr>
                                    <% if (detailOpen) { %>
                                        <tr class="admin-review-detail"><td colspan="9">
                                            <h2>리뷰 상세</h2>
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
                                            <% if (reportCounts.total > 0) {
                                                   Map<Reason, Long> reasons = (Map<Reason, Long>) request.getAttribute("adminReportReasons");
                                                   List<ReportEntry> reports = (List<ReportEntry>) request.getAttribute("adminReportEntries");
                                            %>
                                                <section id="<%= reviewAnchor %>-reports" aria-labelledby="<%= reviewAnchor %>-title">
                                                    <h2 id="<%= reviewAnchor %>-title">신고 내역 · <%= reportCounts.total %>건</h2>
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
                                            <% if (!item.deleted || reportCounts.pending > 0) { %>
                                                <form class="admin-product-actions" method="post" data-admin-review-action
                                                      action="<%= request.getAttribute("adminReviewModerate") %>">
                                                    <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("adminReviewCsrf")) %>">
                                                    <input type="hidden" name="reviewId" value="<%= item.id %>">
                                                    <input type="hidden" name="version" value="<%= item.version %>">
                                                    <% if (reportCounts.pending > 0) { %>
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
                                        </td></tr>
                                    <% } %>
                                <% } %>
                                </tbody>
                            </table>
                        </div>
                    <% } %>
                    <% if (results.pages > 1) {
                           InventoryPagination inventoryPagination = (InventoryPagination) request.getAttribute("adminReviewPagination");
                           request.setAttribute("adminPaginationLabel", "리뷰 목록 페이지"); %>
                        <%@ include file="inventory-pagination.jspf" %>
                    <% } %>
                </section>
            <% } %>
        </main>
    </div>
</body>
</html>
