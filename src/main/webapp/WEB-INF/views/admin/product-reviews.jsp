<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.format.DateTimeFormatter,java.util.List,java.util.Map,daiso.product.controller.ProductView,daiso.product.dao.AdminReviewDAO.Moderation,daiso.product.dao.AdminReviewDAO.Page,daiso.product.dao.AdminReviewDAO.Row,daiso.product.dto.ReviewExtras.Answer" %>
<%
    request.setAttribute("adminSection", "product-reviews");
    String context = request.getContextPath();
    String route = context + "/app/admin/products/reviews";
    Page results = (Page) request.getAttribute("adminReviews");
    String state = (String) request.getAttribute("adminReviewState");
    int rating = (Integer) (request.getAttribute("adminReviewRating") == null ? 0 : request.getAttribute("adminReviewRating"));
    String errorMessage = (String) request.getAttribute("adminReviewError");
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
                    %>
                        <details class="admin-panel admin-review-card">
                            <summary>
                                <strong><%= ProductView.escape(item.productName) %></strong>
                                <span> · <%= item.rating %>점 · <%= statusLabel %></span>
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
                            <% if (!item.deleted) { %>
                                <form class="admin-product-actions" method="post" data-admin-review-action
                                      action="<%= request.getAttribute("adminReviewModerate") %>">
                                    <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("adminReviewCsrf")) %>">
                                    <input type="hidden" name="reviewId" value="<%= item.id %>">
                                    <input type="hidden" name="version" value="<%= item.version %>">
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
