<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView" %>
<%
    String context = request.getContextPath();
    ProductDTO product = (ProductDTO) request.getAttribute("product");
    if (product == null) response.setStatus(404);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
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
                    <p data-product="rating">★ <%= String.format(java.util.Locale.ROOT, "%.1f", product.getRating()) %> · 리뷰 <%= product.getReviewCount() %></p>
                    <% if (product.getSourceNote() != null && !product.getSourceNote().isBlank()) { %>
                        <p class="source-note" data-product="source"><%= ProductView.escape(product.getSourceNote()) %></p>
                    <% } %>
                    <p data-product="id">품번 <%= ProductView.escape(product.getProductCode()) %></p>
                    <p class="stock-status" data-product="stock"><%= ProductView.status(product) %> · 온라인 재고 <%= product.getStockQuantity() %>개</p>
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
                        <input type="number" name="quantity" min="1" max="<%= Math.max(1, Math.min(99, product.getStockQuantity())) %>" value="1" <%= ProductView.available(product) ? "" : "disabled" %>>
                    </label>
                    <p class="detail-total">합계 <%= ProductView.money(product.getPrice()) %></p>
                    <!-- 담기 버튼을 누르면 submit 이벤트가 발생 -->
                    <button type="submit" class="button primary" data-add-cart <%= ProductView.available(product) ? "" : "disabled" %>>장바구니 담기</button>
                    </form>
                    
                </section>
            </div>
            <section class="product-description">
                <h2>상품 설명</h2>
                <p data-product-description><%= product.getDescription() == null || product.getDescription().isBlank()
                    ? "등록된 상품 설명이 없습니다." : ProductView.escape(product.getDescription()) %></p>
                <details>
                    <summary>상품 정보 및 반품 안내</summary>
                    <p>제조·인증·반품 정책은 이 학습용 상품 데이터에 포함되어 있지 않습니다.</p>
                </details>
            </section>
            <% if (!related.isEmpty()) { %>
                <section class="product-section" id="related-products">
                    <h2 class="section-title">함께 둘러볼 상품</h2>
                    <div class="product-grid">
                        <% for (ProductDTO item : related) { %>
                            <%@ include file="card.jspf" %>
                        <% } %>
                    </div>
                </section>
            <% }
           } %>
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