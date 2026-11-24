<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.WishlistItem,daiso.product.dto.WishlistPage" %>
<%
    String context = request.getContextPath();
    WishlistPage wishlistPage = (WishlistPage) request.getAttribute("wishlistPage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-wishlist.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-wishlist.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <div class="wishlist-tools" data-wishlist-config data-wishlist-page="true"
         data-base-url="${pageContext.request.contextPath}/app/products/wishlist"
         data-login-url="${pageContext.request.contextPath}/app/member/login"
         data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
         data-logged-in="<%= Boolean.TRUE.equals(request.getAttribute("wishlistLoggedIn")) %>">
        <p class="wishlist-feedback" data-wishlist-feedback role="status" aria-live="polite"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
    </div>

    <main id="main-content" class="container shop-page" tabindex="-1">
        <h1 class="page-title">관심상품</h1>
        <% if (Boolean.TRUE.equals(request.getAttribute("wishlistLoginRequired"))) { %>
            <p class="empty-state">로그인 후 관심상품을 확인할 수 있습니다.</p>
            <a class="button primary" href="${pageContext.request.contextPath}/app/member/login">로그인하러 가기</a>
        <% } else if (wishlistPage != null) { %>
            <div data-wishlist-results>
                <p class="result-count">전체 <%= wishlistPage.getTotal() %>개 · 최근 찜한 순</p>
                <% if (wishlistPage.getItems().isEmpty()) { %>
                    <p class="empty-state">아직 관심상품이 없습니다. 상품의 하트를 눌러 등록해 보세요.</p>
                    <a class="button secondary" href="${pageContext.request.contextPath}/list.jsp">상품 둘러보기</a>
                <% } else { %>
                    <div class="product-grid">
                        <% for (WishlistItem entry : wishlistPage.getItems()) {
                               ProductDTO item = entry.getProduct(); %>
                            <article class="product-card">
                                <a class="product-cover" href="<%= ProductView.url(context, "/product.jsp", Map.of("id", item.getProductId())) %>">
                                    <img src="<%= ProductView.image(context, item) %>" alt="<%= ProductView.escape(item.getName()) %>" loading="lazy">
                                    <% if (!ProductView.available(item)) { %>
                                        <span class="product-status"><%= ProductView.status(item) %></span>
                                    <% } %>
                                </a>
                                <button type="button" class="wishlist-button is-selected"
                                        data-wishlist-product="<%= ProductView.escape(item.getProductId()) %>"
                                        data-wishlist-name="<%= ProductView.escape(item.getName()) %>"
                                        aria-pressed="true" aria-label="<%= ProductView.escape(item.getName()) %> 관심상품 해제">
                                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21 3.5 12.5C-2 6.5 6 0 12 6c6-6 14 0.5 8.5 6.5Z"/></svg>
                                    <span data-wishlist-label>찜 해제</span>
                                </button>
                                <p class="product-price"><%= ProductView.money(item.getPrice()) %></p>
                                <a class="product-name" href="<%= ProductView.url(context, "/product.jsp", Map.of("id", item.getProductId())) %>"><%= ProductView.escape(item.getName()) %></a>
                                <p class="wishlist-stock"><%= ProductView.status(item) %> · 현재 재고 <%= item.getStockQuantity() %>개</p>
                            </article>
                        <% } %>
                    </div>
                    <nav class="wishlist-pagination" aria-label="관심상품 페이지">
                        <% if (wishlistPage.getPage() > 1) { %>
                            <a class="button secondary" rel="prev" href="<%= ProductView.url(context, "/app/products/wishlist", Map.of("page", Integer.toString(wishlistPage.getPage() - 1))) %>">이전</a>
                        <% }
                           for (int number = Math.max(1, wishlistPage.getPage() - 2);
                                   number <= Math.min(wishlistPage.getTotalPages(), wishlistPage.getPage() + 2); number++) { %>
                            <a class="button secondary" <%= number == wishlistPage.getPage() ? "aria-current=\"page\"" : "" %>
                               href="<%= ProductView.url(context, "/app/products/wishlist", Map.of("page", Integer.toString(number))) %>"><%= number %></a>
                        <% }
                           if (wishlistPage.getPage() < wishlistPage.getTotalPages()) { %>
                            <a class="button secondary" rel="next" href="<%= ProductView.url(context, "/app/products/wishlist", Map.of("page", Integer.toString(wishlistPage.getPage() + 1))) %>">다음</a>
                        <% } %>
                    </nav>
                <% } %>
            </div>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
