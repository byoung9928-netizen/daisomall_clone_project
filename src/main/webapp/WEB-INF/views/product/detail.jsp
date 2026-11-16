<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductDAO.Category,daiso.product.controller.ProductView" %>
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
               List<Category> trail = (List<Category>) request.getAttribute("categoryPath");
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
                    <label class="field">
                        수량
                        <input type="number" name="quantity" min="1" max="<%= Math.max(1, Math.min(99, product.getStockQuantity())) %>" value="1" <%= ProductView.available(product) ? "" : "disabled" %>>
                    </label>
                    <p class="detail-total">합계 <%= ProductView.money(product.getPrice()) %></p>
                    <button type="button" class="button primary" data-add-cart <%= ProductView.available(product) ? "" : "disabled" %>>장바구니 담기</button>
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
<<<<<<< HEAD
               <form id="cartAddForm" action="${pageContext.request.contextPath}/app/cart/add"	method="post">

   		 	<input type="hidden" name="productId">

    		<label class="field">
       		 수량
        	<input type="number" name="quantity"  min="1" max="99" value="1" required>
   			 </label>

    		<p class="detail-total"></p>

    		<button type="submit" class="button primary" data-add-cart>장바구니 담기</button>

			</form>
=======
>>>>>>> branch 'product' of https://github.com/byoung9928-netizen/daisomall_clone_project.git
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
<<<<<<< HEAD
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { element, link, button, money, empty, notice } = app.ui;
        app.ready.then(function (repository) {

        const product = repository.getProduct(new URLSearchParams(location.search).get("id"));
        if (!product) {
            root.replaceChildren();
            empty(root, "상품을 찾을 수 없습니다.");
            root.append(link("상품 목록으로", "list.jsp", {}, "button"));
            return;
        }
        root.querySelector(".breadcrumbs").replaceWith(app.ui.breadcrumbs(repository.getCategories(), product.categoryId));
        const layout = root.querySelector(".product-detail-layout");
        const image = layout.querySelector(".detail-image");
        image.src = app.url(product.image);
        image.alt = product.name + " 자체 제작 Mock 이미지";
        const info = layout.querySelector(".detail-info");
        const status = product.status === "SCHEDULED" ? "판매 예정" : product.status === "SOLD_OUT" || !product.onlineStock ? "품절" : "구매 가능";
        const values = { brand: product.brand, name: product.name, price: money(product.price), rating: "★ " + product.rating + " · 리뷰 " + product.reviewCount + " (Mock)", source: product.source.note, id: "품번 " + product.id, stock: status + " · 온라인 Mock 재고 " + product.onlineStock + "개" };
        Object.entries(values).forEach(([key, value]) => { info.querySelector('[data-product="' + key + '"]').textContent = value; });
        const stores = info.querySelector(".store-stock");
        repository.getStores().forEach(store => stores.append(element("p", "", store.name + " · " + (product.storeStocks[store.id] || 0) + "개 (Mock)")));
        const quantity = info.querySelector('[name="quantity"]');
        quantity.max = String(Math.min(99, product.onlineStock));
        const total = info.querySelector(".detail-total");
        total.textContent = "합계 " + money(product.price);
        quantity.addEventListener("input", () => {
            total.textContent = "합계 " + money(product.price * Math.max(0, Number(quantity.value)));
        });
        const cartForm = info.querySelector("#cartAddForm");

        cartForm.elements.productId.value = product.id;

        const add = info.querySelector("[data-add-cart]");
        add.disabled = status !== "구매 가능";
        const description = root.querySelector(".product-description");
        description.querySelector("[data-product-description]").textContent = product.description;

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
=======
    <% if (product != null) { %>
        <script>
        document.addEventListener("DOMContentLoaded", () => {
            const app = window.Daiso;
            const layout = document.querySelector(".product-detail-layout");
            const quantity = layout.querySelector('[name="quantity"]');
            const total = layout.querySelector(".detail-total");
            const add = layout.querySelector("[data-add-cart]");
            quantity.addEventListener("input", () => {
                total.textContent = "합계 " + app.ui.money(Number(layout.dataset.unitPrice) * Math.max(0, Number(quantity.value)));
            });
            add.addEventListener("click", async () => {
                if (!quantity.reportValidity()) return;
                try {
                    const repository = await app.ready;
                    repository.addCart(layout.dataset.productId, Number(quantity.value));
                    app.ui.notice("장바구니에 담았습니다.");
                } catch (error) {
                    app.ui.notice(error.message);
                }
            });
        });
        </script>
    <% } %>
>>>>>>> branch 'product' of https://github.com/byoung9928-netizen/daisomall_clone_project.git
</body>
</html>
