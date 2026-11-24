<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView" %>
<%
    String context = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-wishlist.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-wishlist.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/home.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/home-carousel.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/featured-carousel.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/home-category-ranking.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/stock-watch.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <% if ("success".equals(request.getParameter("logout"))) { %>
<script>
document.addEventListener("DOMContentLoaded", function () {
    var toast = document.getElementById("common-feature-status");

    if (!toast) {
        return;
    }

    toast.textContent = "로그아웃 되었습니다.";
    toast.classList.add("logout-toast");

    window.setTimeout(function () {
        toast.textContent = "";
        toast.classList.remove("logout-toast");
    }, 3000);

    window.history.replaceState(null, "", location.pathname);
});
</script>
<% } %>
    <%@ include file="../common/navigation.jspf" %>
    <div class="wishlist-tools" data-wishlist-config
         data-base-url="${pageContext.request.contextPath}/app/products/wishlist"
         data-cart-url="${pageContext.request.contextPath}/app/cart/add"
         data-cart-preview-url="${pageContext.request.contextPath}/app/products/cart/preview"
         data-cart-view-url="${pageContext.request.contextPath}/app/cart"
         data-login-url="${pageContext.request.contextPath}/app/member/login"
         data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
         data-logged-in="<%= Boolean.TRUE.equals(request.getAttribute("wishlistLoggedIn")) %>">
        <p class="wishlist-feedback" data-wishlist-feedback role="status" aria-live="polite"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
    </div>
    <main id="main-content" class="container storefront" data-page="home" tabindex="-1">
        <%@ include file="home-promotions.jspf" %>
        <% if (Boolean.TRUE.equals(request.getAttribute("cardPickupLookupError"))) { %>
            <p class="card-pickup-note" role="status">매장픽업 가능 여부를 확인하지 못했습니다.</p>
        <% } %>
        <section class="product-section" id="featured" aria-labelledby="featured-title">
            <p class="section-eyebrow">
                EVERYDAY PICKS
            </p>
            <h2 class="section-title" id="featured-title"><%= request.getAttribute("homeRecommendationTitle") == null
                    ? "오늘의 추천 상품" : ProductView.escape(request.getAttribute("homeRecommendationTitle")) %></h2>
            <% {
                List<ProductDTO> carouselProducts = (List<ProductDTO>) request.getAttribute("featuredProducts");
                String carouselId = "featured-products";
                String carouselLabel = "추천 상품";
                boolean carouselRanked = false;
                request.setAttribute("browseCatalog", true);
            %>
                <%@ include file="home-product-carousel.jspf" %>
            <% request.removeAttribute("browseCatalog");
               request.removeAttribute("recommendationReasons");
               } %>
        </section>
        <section class="product-section" id="new-products" aria-labelledby="new-products-title">
            <p class="section-eyebrow">EVERYDAY PICKS</p>
            <h2 class="section-title" id="new-products-title">신상발견</h2>
            <% {
                List<ProductDTO> carouselProducts = (List<ProductDTO>) request.getAttribute("newProducts");
                String carouselId = "new-product-cards";
                String carouselLabel = "신상품";
                boolean carouselRanked = false;
            %>
                <%@ include file="home-product-carousel.jspf" %>
            <% } %>
        </section>
        <section class="product-section" id="home-category-ranking" aria-labelledby="home-ranking-title"
                 data-home-ranking data-source="${pageContext.request.contextPath}/app/products/home/category-rankings">
            <h2 class="section-title" id="home-ranking-title">카테고리 랭킹</h2>
            <p class="home-ranking-status" role="status" data-ranking-status></p>
            <button class="button secondary" type="button" data-ranking-retry hidden>다시 불러오기</button>
            <div data-ranking-content></div>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
