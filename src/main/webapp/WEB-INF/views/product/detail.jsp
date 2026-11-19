<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReviewPage,java.math.RoundingMode" %>
<%
    String context = request.getContextPath();
    ProductDTO product = (ProductDTO) request.getAttribute("product");
    ProductReviewPage detailReviews = (ProductReviewPage) request.getAttribute("detailReviews");
    String detailAverage = detailReviews == null || detailReviews.average == null ? ""
            : detailReviews.average.setScale(1, RoundingMode.HALF_UP).toPlainString();
    if (product == null) response.setStatus(404);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-wishlist.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-wishlist.js" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-reviews.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-detail-description.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <div class="wishlist-tools" data-wishlist-config
         data-base-url="${pageContext.request.contextPath}/app/products/wishlist"
         data-login-url="${pageContext.request.contextPath}/app/member/login"
         data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
         data-logged-in="<%= Boolean.TRUE.equals(request.getAttribute("wishlistLoggedIn")) %>">
        <a class="button secondary" href="${pageContext.request.contextPath}/app/products/wishlist">♡ 관심상품 보기</a>
        <p class="wishlist-feedback" data-wishlist-feedback role="status" aria-live="polite"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
    </div>
    <main id="main-content" class="container shop-page" data-page="product" tabindex="-1">
        <% if (product == null) { %>
            <h1 class="page-title">상품을 찾을 수 없습니다.</h1>
            <a class="button" href="<%= ProductView.url(context, "/list.jsp", Map.of()) %>">상품 목록으로</a>
        <% } else {
               List<ProductDTO> related = (List<ProductDTO>) request.getAttribute("relatedProducts"); %>
            <%@ include file="breadcrumbs.jspf" %>
            <div class="product-detail-layout" data-product-id="<%= ProductView.escape(product.getProductId()) %>" data-unit-price="<%= product.getPrice() %>">
                <img class="detail-image" src="<%= ProductView.image(context, product) %>" alt="<%= ProductView.escape(product.getName()) %>">
                <section class="detail-info">
                    <p class="eyebrow" data-product="brand"><%= ProductView.escape(product.getBrand()) %></p>
                    <h1 class="page-title" data-product="name"><%= ProductView.escape(product.getName()) %></h1>
                    <p class="detail-price" data-product="price"><%= ProductView.money(product.getPrice()) %></p>
                    <p data-product="rating"><a class="pd-review-jump" href="#product-reviews"><%= detailAverage.isEmpty() ? "" : "★ " + detailAverage + " · " %>리뷰<%= detailReviews == null ? "" : " " + detailReviews.total + "개" %></a></p>
                    <% if (product.getSourceNote() != null && !product.getSourceNote().isBlank()) { %>
                        <p class="source-note" data-product="source"><%= ProductView.escape(product.getSourceNote()) %></p>
                    <% } %>
                    <p data-product="id">품번 <%= ProductView.escape(product.getProductCode()) %></p>
                    <p class="stock-status" data-product="stock"><%= ProductView.status(product) %> · 온라인 재고 <%= product.getStockQuantity() %>개</p>
                    <% boolean wishlistDetailSelected = request.getAttribute("wishlistSelected") instanceof java.util.Set<?>
                            && ((java.util.Set<?>) request.getAttribute("wishlistSelected")).contains(product.getProductId()); %>
    <button type="button" class="wishlist-button<%= wishlistDetailSelected ? " is-selected" : "" %>"
            data-wishlist-product="<%= ProductView.escape(product.getProductId()) %>"
            data-wishlist-name="<%= ProductView.escape(product.getName()) %>"
            aria-pressed="<%= wishlistDetailSelected %>"
            aria-label="<%= ProductView.escape(product.getName()) %> <%= request.getAttribute("wishlistError") != null ? "찜 상태 확인 불가" : (wishlistDetailSelected ? "관심상품 해제" : "관심상품 등록") %>"
            <%= request.getAttribute("wishlistError") != null ? "disabled" : "" %>>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21 3.5 12.5C-2 6.5 6 0 12 6c6-6 14 0.5 8.5 6.5Z"/></svg>
        <span data-wishlist-label><%= wishlistDetailSelected ? "찜 해제" : "찜" %></span>
    </button>
                    <div class="delivery-summary">
                        <strong>택배배송</strong>
                        <p>배송비 3,000원 · 상품 30,000원 이상 무료</p>
                    </div>
                    <a class="button secondary" href="<%= ProductView.url(context, "/search.jsp", Map.of("collection", "store", "q", product.getProductCode())) %>">매장 상품 찾기</a>
                    
                    <!-- 1. 장바구니 요청 시작 -->
                    <form id="cartAddForm" method="post" action="${pageContext.request.contextPath}/app/cart/add">
                    <!-- input 안 서버로 보낼 값 -->
                    <!-- 여기 name(productId, quantity)은 자바에서 값을 꺼내 쓰는 이름  -->
                        <input type="hidden" name="productId" value="<%= ProductView.escape(product.getProductId()) %>">
                    <label class="field">
                        수량
                        <input type="number" name="quantity" min="1" max="<%= Math.max(1, Math.min(99, product.getStockQuantity())) %>" 
                        		value="1" <%= ProductView.available(product) ? "" : "disabled" %>>
                    </label>
                    <p class="detail-total">합계 <%= ProductView.money(product.getPrice()) %></p>
                    <!-- 담기 버튼을 누르면 submit 이벤트가 발생 -->
                    <button type="submit" class="button primary" data-add-cart <%= ProductView.available(product) ? "" : "disabled" %>>장바구니 담기</button>
                    </form>
                    
                </section>
            </div>
            <nav class="pd-detail-links" aria-label="상품 상세 바로가기">
                <a href="#product-description">상품 설명</a>
                <a href="#product-reviews">상품 리뷰<%= detailReviews == null ? "" : " " + detailReviews.total + "개" %></a>
            </nav>
            <%@ include file="official-description.jspf" %>
            <% if (!related.isEmpty()) { %>
                <section class="product-section" id="related-products">
                    <h2 class="section-title">함께 둘러볼 상품</h2>
                    <div class="product-grid">
                        <% for (ProductDTO item : related) { %>
                            <%@ include file="card.jspf" %>
                        <% } %>
                    </div>
                </section>
            <% } %>
            <%@ include file="detail-reviews.jspf" %>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <% if (product != null) { %>
    <script>
    document.addEventListener("DOMContentLoaded", function () {
        const app = window.Daiso;
        const layout = document.querySelector(".product-detail-layout");
        const quantity = layout.querySelector('[name="quantity"]');
        const total = layout.querySelector(".detail-total");
        quantity.addEventListener("input", function () {
            total.textContent = "합계 " + app.ui.money(
                Number(layout.dataset.unitPrice) * Math.max(0, Number(quantity.value))
            );
        });
        const cartForm = document.getElementById("cartAddForm");
        const cartButton = cartForm.querySelector('[type="submit"]');
        //장바구니 담기 버튼을 눌렀을때 submit을 아래 javaScript가 받음
        cartForm.addEventListener("submit", async function (event) {
            event.preventDefault();
            if (cartButton.disabled) return;
            cartButton.disabled = true;
            try {
                const response = await fetch(cartForm.action, {
                    method: "POST",
                    body: new URLSearchParams(new FormData(cartForm))
                });
                if (!response.ok) throw new Error("장바구니 저장 중 오류가 발생했습니다.");
                const result = (await response.text()).trim();
                app.ui.notice(result === "OK" ? "장바구니에 담았습니다." : result);
            } catch (error) {
                app.ui.notice(error.message);
            } finally {
                cartButton.disabled = false;
            }
        });
    });
    </script>
    <% } %>
</body>
</html>