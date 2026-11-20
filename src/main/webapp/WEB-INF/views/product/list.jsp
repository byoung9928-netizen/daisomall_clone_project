<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    Integer catalogStatus = (Integer) request.getAttribute("catalogErrorStatus");
    if (catalogStatus != null) response.setStatus(catalogStatus);
    boolean lowStockPage = Boolean.TRUE.equals(request.getAttribute("stockWatch"));
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-wishlist.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-wishlist.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-collections.css">
    <% if (lowStockPage) { %>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/stock-watch.css">
        <script src="${pageContext.request.contextPath}/assets/js/common/stock-watch.js" defer></script>
    <% } %>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <div class="wishlist-tools<%= lowStockPage ? " stock-watch-wishlist" : "" %>" data-wishlist-config
         data-cart-url="${pageContext.request.contextPath}/app/cart/add"
         data-base-url="${pageContext.request.contextPath}/app/products/wishlist"
         data-login-url="${pageContext.request.contextPath}/app/member/login"
         data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
         data-logged-in="<%= Boolean.TRUE.equals(request.getAttribute("wishlistLoggedIn")) %>">
        <p class="wishlist-feedback" data-wishlist-feedback role="status" aria-live="polite"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
    </div>
    <%@ include file="catalog.jspf" %>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
