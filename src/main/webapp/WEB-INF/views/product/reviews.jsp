<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReview,daiso.product.dto.ProductReviewPage,daiso.product.dto.ReviewQuestion,daiso.product.dto.ReviewExtras.Answer" %>
<%
    String context = request.getContextPath();
    String route = "/app/products/reviews";
    ProductDTO product = (ProductDTO) request.getAttribute("reviewProduct");
    ProductReviewPage reviews = (ProductReviewPage) request.getAttribute("reviewPage");
    String id = product == null ? (String) request.getAttribute("reviewReturnId") : product.getProductId();
    boolean fromMyPage = Boolean.TRUE.equals(request.getAttribute("reviewFromMyPage"));
    String reviewOrderNo = (String) request.getAttribute("reviewOrderNo");
    String reviewHref = id == null ? null : ProductView.url(context, route,
            Map.of("id", id, "from", fromMyPage ? "my-reviews" : ""));
    String token = ProductView.escape(request.getAttribute("reviewToken"));
    String base = ProductView.escape(context + route);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-reviews.css?v=delivery-review">
    <script defer src="${pageContext.request.contextPath}/assets/js/common/product-review-editor.js?v=my-review-list"></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <main id="main-content" class="container shop-page" tabindex="-1">
        <h1 class="page-title">구매 인증 리뷰 작성·관리</h1>
        <% if (reviewOrderNo != null) { %>
            <p><a href="<%= ProductView.url(context, "/app/order/mypage", Map.of("orderNo", reviewOrderNo)) %>">주문내역으로 돌아가기</a></p>
        <% } else if (fromMyPage) { %>
            <p><a href="<%= ProductView.url(context, "/app/order/mypage",
                    Map.of("productActivity", "reviews", "reviewTab", reviews != null && reviews.ownReview != null ? "written" : "")) %>">내 리뷰 목록으로 돌아가기</a></p>
        <% } else if (product != null) { %>
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
                <button type="button" class="button primary review-dialog-trigger" data-review-open hidden>
                    <%= editing ? "내 리뷰 수정/삭제" : "리뷰 작성" %>
                </button>
                <jsp:include page="review-editor.jsp" />
                <% if (editing) { %>
                    <noscript><details class="review-dialog-delete">
                        <summary>내 리뷰 삭제</summary>
                        <p>삭제한 리뷰는 목록과 별점 집계에서 제외되며 첨부 사진도 삭제됩니다.</p>
                        <form method="post" action="<%= base %>/delete">
                            <input type="hidden" name="id" value="<%= ProductView.escape(id) %>">
                            <input type="hidden" name="csrf" value="<%= token %>">
                            <% if (reviewOrderNo != null) { %>
                                <input type="hidden" name="from" value="order">
                                <input type="hidden" name="orderNo" value="<%= ProductView.escape(reviewOrderNo) %>">
                            <% } else if (fromMyPage) { %><input type="hidden" name="from" value="my-reviews"><% } %>
                            <input type="hidden" name="reviewId" value="<%= own.id %>">
                            <input type="hidden" name="version" value="<%= own.version %>">
                            <button type="submit" class="button secondary">리뷰 삭제 확인</button>
                        </form>
                    </details></noscript>
                <% } %>
            <% } else if (Boolean.TRUE.equals(request.getAttribute("reviewSignedIn"))) { %>
                <p>택배는 배송 완료 후, 매장픽업은 유효한 결제 완료 후 리뷰를 작성할 수 있습니다.</p>
            <% } else { %>
                <p><a href="<%= ProductView.escape(context + "/app/member/login") %>">로그인</a>하고 구매한 상품의 리뷰를 작성할 수 있습니다.</p>
            <% } %>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
