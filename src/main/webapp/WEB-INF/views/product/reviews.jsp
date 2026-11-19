<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReview,daiso.product.dto.ProductReviewPage" %>
<%
    String context = request.getContextPath();
    String route = "/app/products/reviews";
    ProductDTO product = (ProductDTO) request.getAttribute("reviewProduct");
    ProductReviewPage reviews = (ProductReviewPage) request.getAttribute("reviewPage");
    String id = product == null ? (String) request.getAttribute("reviewReturnId") : product.getProductId();
    String reviewHref = id == null ? null : ProductView.url(context, route, Map.of("id", id));
    String token = ProductView.escape(request.getAttribute("reviewToken"));
    String base = ProductView.escape(context + route);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-reviews.css">
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <main id="main-content" class="container shop-page" tabindex="-1">
        <h1 class="page-title">구매 인증 리뷰 작성·관리</h1>
        <% if (product != null) { %>
            <p><a href="<%= ProductView.url(context, "/product.jsp", Map.of("id", id)) %>#product-reviews"><%= ProductView.escape(product.getName()) %> 상품 리뷰로 돌아가기</a></p>
        <% } else if (reviewHref != null) { %>
            <p><a href="<%= reviewHref %>">리뷰 작성·관리로 돌아가기</a></p>
        <% } else { %>
            <p><a href="<%= ProductView.escape(context + "/list.jsp") %>">상품 목록으로 돌아가기</a></p>
        <% } %>
        <% if (request.getAttribute("reviewError") != null) { %>
            <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("reviewError")) %></p>
        <% } %>
        <% if (reviews != null) { %>
            <% if (reviews.canWrite || reviews.ownReview != null) {
                ProductReview own = reviews.ownReview;
                boolean editing = own != null;
            %>
                <section class="pd-review-editor" aria-labelledby="review-editor">
                    <h2 id="review-editor"><%= editing ? "내 리뷰 수정" : "리뷰 작성" %></h2>
                    <form method="post" action="<%= base + (editing ? "/update" : "/create") %>">
                        <input type="hidden" name="id" value="<%= ProductView.escape(id) %>">
                        <input type="hidden" name="csrf" value="<%= token %>">
                        <% if (editing) { %>
                            <input type="hidden" name="reviewId" value="<%= own.id %>">
                            <input type="hidden" name="version" value="<%= own.version %>">
                        <% } %>
                        <label class="field">별점
                            <select name="rating" required>
                                <% for (int stars = 1; stars <= 5; stars++) { %>
                                    <option value="<%= stars %>" <%= stars == (editing ? own.rating : 5) ? "selected" : "" %>><%= stars %>점</option>
                                <% } %>
                            </select>
                        </label>
                        <label class="field">리뷰 내용
                            <textarea name="content" rows="5" maxlength="2000" required><%= editing ? ProductView.escape(own.content) : "" %></textarea>
                        </label>
                        <button type="submit" class="button primary"><%= editing ? "수정 저장" : "리뷰 등록" %></button>
                    </form>
                    <% if (editing) { %>
                        <details>
                            <summary>내 리뷰 삭제</summary>
                            <p>삭제하면 리뷰 목록과 별점 집계에서 제외됩니다.</p>
                            <form method="post" action="<%= base %>/delete">
                                <input type="hidden" name="id" value="<%= ProductView.escape(id) %>">
                                <input type="hidden" name="csrf" value="<%= token %>">
                                <input type="hidden" name="reviewId" value="<%= own.id %>">
                                <input type="hidden" name="version" value="<%= own.version %>">
                                <button type="submit" class="button secondary">리뷰 삭제 확인</button>
                            </form>
                        </details>
                    <% } %>
                </section>
            <% } else if (Boolean.TRUE.equals(request.getAttribute("reviewSignedIn"))) { %>
                <p>취소되지 않은 결제 완료 구매가 있어야 작성할 수 있습니다.</p>
            <% } else { %>
                <p><a href="<%= ProductView.escape(context + "/app/member/login") %>">로그인</a>하고 구매한 상품의 리뷰를 작성할 수 있습니다.</p>
            <% } %>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
