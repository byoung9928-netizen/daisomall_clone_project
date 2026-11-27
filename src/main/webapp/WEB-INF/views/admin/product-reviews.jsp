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
    boolean fragment = Boolean.TRUE.equals(request.getAttribute("adminReviewFragment"));
    String startDate = filter == null || filter.start == null ? "" : filter.start.toLocalDate().toString();
    String endDate = filter == null || filter.endExclusive == null ? "" : filter.endExclusive.toLocalDate().minusDays(1).toString();
    String errorMessage = (String) request.getAttribute("adminReviewError");
    Map<String, String> reportViewBase = (Map<String, String>) request.getAttribute("adminReportViewBase");
    Map<ReportFilter, String> cards = (Map<ReportFilter, String>) request.getAttribute("adminReviewCards");
    NumberFormat counts = NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
    DateTimeFormatter dates = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
%>
<% if (!fragment) { %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-reviews.css?v=5">
    <script defer src="${pageContext.request.contextPath}/assets/js/common/admin-reviews.js?v=5"></script>
</head>
<body class="admin-body product-admin-ux">
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
            <% } %>
            <% if (results != null) { %>
                <% if (!fragment && !results.reportsAvailable) { %>
                    <p class="form-status">신고 기능을 사용하려면 리뷰 신고 테이블 마이그레이션을 적용해 주세요.</p>
                <% } %>
                <nav class="store-metrics admin-review-metrics" data-review-metrics aria-label="리뷰 및 신고 요약">
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
                <% if (!fragment) { %>
                <p class="admin-review-scope">요약은 검색 조건과 조회 기간을 기준으로 집계하며, 처리 완료에는 기각 건도 포함됩니다. 표의 신고 건수와 신고 정렬은 리뷰별 전체 신고 이력을 기준으로 합니다.</p>

                <form id="admin-review-filters" class="admin-review-filters" action="<%= ProductView.escape(route) %>" method="get">
                    <h2 class="admin-review-group-title">리뷰 검색 및 필터</h2>
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
                        <% if (results.reportsAvailable) { %>
                            <label class="field">신고 사유 <select name="reason"><option value="">전체</option>
                                <% for (Reason reason : Reason.values()) { %>
                                    <option value="<%= reason.name() %>" <%= reason.name().equals(filter.reason) ? "selected" : "" %>><%= reason.label %></option>
                                <% } %>
                            </select></label>
                        <% } %>
                        <% if (results.photosAvailable) { %>
                            <label class="field">사진 <select name="withPhoto"><option value="">전체</option><option value="1" <%= filter.photo ? "selected" : "" %>>사진이 있는 리뷰</option></select></label>
                        <% } %>
                        <% if (results.reactionsAvailable) { %>
                            <label class="field">반응 <select name="withReaction"><option value="">전체</option><option value="1" <%= filter.reaction ? "selected" : "" %>>반응이 있는 리뷰</option></select></label>
                        <% } %>
                    </div>
                    <fieldset class="admin-review-period">
                        <legend class="visually-hidden">조회 기간</legend>
                        <h2 class="admin-review-group-title">조회 기간</h2>
                        <div class="admin-review-dates">
                            <label class="field">조회 기준
                                <select name="dateField">
                                    <% for (DateField value : DateField.values()) {
                                           if (value != DateField.REVIEW && !results.reportsAvailable && value != filter.dateField) continue; %>
                                        <option value="<%= value.name() %>" <%= value == filter.dateField ? "selected" : "" %>><%= value.label %></option>
                                    <% } %>
                                </select>
                            </label>
                            <div class="admin-review-preset-field">
                                <span id="admin-review-period-label">빠른 기간</span>
                                <div class="admin-review-presets" role="group" aria-labelledby="admin-review-period-label">
                                <% for (Period value : Period.values()) {
                                       LocalDate preset = value.start(reviewToday); %>
                                    <label>
                                        <input type="radio" name="period" value="<%= value.name() %>" <%= value == reviewPeriod ? "checked" : "" %>
                                               data-start="<%= ProductView.escape(preset) %>" data-end="<%= preset == null ? "" : reviewToday %>">
                                        <span><%= value.label %></span>
                                    </label>
                                <% } %>
                                </div>
                            </div>
                            <label class="field">시작일
                                <input type="date" name="start" min="1000-01-01" max="9999-12-30"
                                       value="<%= startDate %>">
                            </label>
                            <label class="field">종료일
                                <input type="date" name="end" min="1000-01-01" max="9999-12-30"
                                       value="<%= endDate %>">
                            </label>
                        </div>
                    </fieldset>
                    <div class="admin-product-actions">
                        <button class="button primary" type="submit">검색</button>
                        <a class="button secondary" href="<%= ProductView.escape(route) %>" data-review-reset>초기화</a>
                    </div>
                </form>
                <section class="admin-review-analysis" aria-labelledby="review-analysis-title">
                    <h2 id="review-analysis-title" class="admin-review-group-title">상품별 리뷰 분석</h2>
                    <form id="admin-review-analysis" method="get" action="<%= ProductView.escape(route) %>">
                        <% for (Map.Entry<String, String> entry : reportViewBase.entrySet()) {
                               if ("analysisProduct".equals(entry.getKey())) continue; %>
                            <input type="hidden" name="<%= ProductView.escape(entry.getKey()) %>" value="<%= ProductView.escape(entry.getValue()) %>">
                        <% } %>
                        <label class="field">상품 ID 또는 품번
                            <input name="analysisProduct" type="search" maxlength="100" required
                                   value="<%= ProductView.escape(filter.analysisProduct) %>" placeholder="상품 ID 또는 품번">
                        </label>
                        <button class="button secondary" type="submit">분석하기</button>
                        <p class="error-message" role="alert" data-review-analysis-error hidden></p>
                    </form>
                <% } %>
                    <div data-review-analysis-result aria-live="polite">
                    <% daiso.product.dao.AdminReviewDAO.Analysis analysis = (daiso.product.dao.AdminReviewDAO.Analysis) request.getAttribute("adminReviewAnalysis");
                       if (analysis != null) { %>
                        <section class="admin-panel">
                            <h2><%= ProductView.escape(analysis.name) %> 리뷰 분석</h2>
                            <p>공개 리뷰 <%= counts.format(analysis.total) %>개 · 평균 <%= analysis.average == null ? "—" : analysis.average.setScale(1, java.math.RoundingMode.HALF_UP) %>점
                                · 사진 리뷰 <%= results.photosAvailable ? counts.format(analysis.photos) + "개" : "—" %>
                                · 반응 <%= results.reactionsAvailable ? counts.format(analysis.reactions) + "건" : "—" %></p>
                            <ul class="admin-review-stars">
                                <% for (int n = 5; n > 0; n--) { %><li><%= n %>점 <strong><%= counts.format(analysis.stars[n - 1]) %>개</strong></li><% } %>
                            </ul>
                            <% if (results.reportsAvailable) { %><p>관리자 신고 집계 · 신고된 리뷰 <%= counts.format(analysis.reported) %>개 · 미처리 <%= counts.format(analysis.pending) %>건</p><% } %>
                        </section>
                    <% } else if (!filter.analysisProduct.isEmpty()) { %>
                        <p class="admin-panel">분석할 상품 ID 또는 품번을 찾을 수 없습니다.</p>
                    <% } %>
                    </div>
                <% if (!fragment) { %></section><% } %>

                <section id="review-results" tabindex="-1" aria-label="리뷰 검색 결과"
                         data-review-url="<%= request.getAttribute("adminReviewUrl") %>"
                         data-start="<%= startDate %>" data-end="<%= endDate %>"
                         <%= Boolean.TRUE.equals(request.getAttribute("adminMissingDetail")) ? "data-review-fallback" : "" %>>
                    <div class="admin-review-list-heading">
                        <p class="admin-count" data-review-count role="status">검색 결과 <strong><%= counts.format(results.total) %>개</strong></p>
                        <% if (!fragment) { %>
                        <div class="admin-review-sort">
                            <label for="admin-review-sort">정렬</label>
                            <select id="admin-review-sort" name="sort" form="admin-review-filters">
                                <% for (Sort sort : Sort.values()) {
                                       if (sort == Sort.MOST_REACTED && !results.reactionsAvailable && sort != filter.sort) continue; %>
                                    <option value="<%= sort.name() %>" <%= sort == filter.sort ? "selected" : "" %>><%= sort.label %></option>
                                <% } %>
                            </select>
                            <noscript><button type="submit" class="button secondary" form="admin-review-filters">적용</button></noscript>
                        </div>
                        <% } %>
                    </div>
                    <% if (!fragment) { %>
                        <p class="admin-review-message" data-review-loading role="status" hidden>리뷰를 불러오는 중입니다.</p>
                        <p class="admin-review-message admin-review-error" data-review-error role="alert" hidden></p>
                    <% } %>
                    <div data-review-list>
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
                                            <% List<daiso.product.dao.AdminReviewDAO.Decision> reviewDecisions =
                                                   (List<daiso.product.dao.AdminReviewDAO.Decision>) request.getAttribute("adminReviewDecisions");
                                               if (reviewDecisions != null && !reviewDecisions.isEmpty()) { %>
                                                <h3>최근 관리자 처리 이력</h3>
                                                <ul><% for (daiso.product.dao.AdminReviewDAO.Decision entry : reviewDecisions) { %>
                                                    <li><time datetime="<%= entry.date %>"><%= entry.date.format(dates) %></time>
                                                        · <%= ProductView.escape(entry.manager) %>
                                                        · <%= "DISMISS".equals(entry.action) ? "신고 기각" : "PUBLISHED".equals(entry.action) ? "복구" : "HIDDEN".equals(entry.action) ? "숨김" : "삭제" %>
                                                        · <%= ProductView.escape(entry.before) %> → <%= ProductView.escape(entry.after) %>
                                                        · <%= ProductView.escape(entry.reason) %></li>
                                                <% } %></ul>
                                            <% } %>
                                            <% if (!item.deleted || reportCounts.pending > 0) { %>
                                                <form class="admin-product-actions" method="post" data-admin-review-action
                                                      action="<%= request.getAttribute("adminReviewModerate") %>">
                                                    <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("adminReviewCsrf")) %>">
                                                    <input type="hidden" name="reviewId" value="<%= item.id %>">
                                                    <input type="hidden" name="version" value="<%= item.version %>">
                                                    <% if (Boolean.TRUE.equals(request.getAttribute("adminReviewAuditReady"))) { %>
                                                        <label class="field">처리 사유
                                                            <input name="moderationReason" maxlength="255" required>
                                                        </label>
                                                    <% } else { %>
                                                        <p>처리 사유를 기록하려면 관리자 리뷰 처리 이력 SQL을 적용하세요.</p>
                                                    <% } %>
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
                    </div>
                </section>
            <% } %>
            <% if (!fragment) { %>
        </main>
    </div>
</body>
</html>
<% } %>
