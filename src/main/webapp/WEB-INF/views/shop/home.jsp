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
         data-login-url="${pageContext.request.contextPath}/app/member/login"
         data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
         data-logged-in="<%= Boolean.TRUE.equals(request.getAttribute("wishlistLoggedIn")) %>">
        <a class="button secondary" href="${pageContext.request.contextPath}/app/products/wishlist">♡ 관심상품 보기</a>
        <p class="wishlist-feedback" data-wishlist-feedback role="status" aria-live="polite"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
    </div>
    <main id="main-content" class="container storefront" data-page="home" tabindex="-1">
        <%@ include file="home-promotions.jspf" %>
        <section class="product-section" id="featured">
            <p class="section-eyebrow">
                EVERYDAY PICKS
            </p>
            <h2 class="section-title">
                오늘의 추천 상품
            </h2>
            <div class="product-grid">
                <% for (ProductDTO item : (List<ProductDTO>) request.getAttribute("featuredProducts")) { %>
                    <%@ include file="../product/card.jspf" %>
                <% } %>
            </div>
            <a class="button secondary" href="<%= ProductView.url(context, "/list.jsp", Map.of("sort", "recommended", "collection", "ranking")) %>">상품 더 보기</a>
        </section>
        <section class="product-section" id="new-products">
            <p class="section-eyebrow">
                EVERYDAY PICKS
            </p>
            <h2 class="section-title">
                신상발견
            </h2>
            <div class="product-grid">
                <% for (ProductDTO item : (List<ProductDTO>) request.getAttribute("newProducts")) { %>
                    <%@ include file="../product/card.jspf" %>
                <% } %>
            </div>
            <a class="button secondary" href="<%= ProductView.url(context, "/list.jsp", Map.of("sort", "newest", "collection", "new")) %>">상품 더 보기</a>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
