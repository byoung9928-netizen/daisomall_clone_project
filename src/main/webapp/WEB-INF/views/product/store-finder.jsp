<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductView,daiso.product.controller.ProductDTO,daiso.product.controller.StoreFinderController,daiso.product.controller.StoreFinderView,daiso.product.controller.StoreFinderView.Store" %>
<%
    String context = request.getContextPath();
    String locationRoute = ProductView.escape(context + StoreFinderController.LOCATIONS);
    String productRoute = ProductView.escape(context + StoreFinderController.PRODUCTS);
    boolean locationPage = Boolean.TRUE.equals(request.getAttribute("locationFinder"));
    StoreFinderView finder = (StoreFinderView) request.getAttribute("storeFinder");
    String pageTitle = locationPage ? "매장 위치 찾기" : "매장 상품 찾기";
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/store-finder.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/store-finder.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <main id="main-content" class="container shop-page store-finder" tabindex="-1">
        <div class="store-finder-heading">
            <h1 class="page-title"><%= pageTitle %></h1>
            <nav class="store-finder-tabs" aria-label="매장 조회">
                <a href="<%= locationRoute %>" <%= locationPage ? "aria-current=\"page\"" : "" %>>매장 위치 찾기</a>
                <a href="<%= productRoute %>" <%= !locationPage ? "aria-current=\"page\"" : "" %>>매장 상품 찾기</a>
            </nav>
        </div>
        <% if (request.getAttribute("storeFinderError") != null) { %>
            <p class="store-finder-notice" role="alert"><%= ProductView.escape(request.getAttribute("storeFinderError")) %></p>
        <% } else if (finder != null && locationPage) { %>
            <p class="store-finder-notice">학습용 가상 매장입니다. 지도는 지점이 속한 주변 지역을 보여줍니다.</p>
            <form class="store-finder-search" method="get" action="<%= locationRoute %>" role="search" aria-label="지역·매장 검색">
                <label class="visually-hidden" for="store-location-query">지역 또는 매장명</label>
                <input id="store-location-query" name="q" type="search" maxlength="100" placeholder="지역 또는 매장명 (예: 서울, 서면)"
                       value="<%= ProductView.escape(finder.keyword) %>">
                <button type="submit">매장 검색</button>
            </form>
            <p class="store-finder-count">매장 <strong><%= finder.stores.size() %></strong>곳</p>
            <div class="store-location-results">
                <% for (Store store : finder.stores) { %>
                    <article class="store-location-card">
                        <% if (!store.mapUrl.isEmpty()) { %>
				<div data-naver-store-map data-client-id="${naverMapsClientId}"
					data-bounds="<%= ProductView.escape(store.mapBounds) %>"
					role="region"
					aria-label="<%= ProductView.escape(store.name) %> 주변 지역 지도"
					style="height: 15rem;" hidden></div>
				<iframe src="<%=store.mapUrl%>" title="<%= ProductView.escape(store.name) %> 주변 지역 지도"
                                    loading="lazy" referrerpolicy="no-referrer"></iframe>
                        <% } %>
                        <div class="store-location-info">
                            <p><%= ProductView.escape(store.city) %> · 가상 매장</p>
                            <h2><%= ProductView.escape(store.name) %></h2>
                            <a class="store-finder-action" href="<%= store.stockUrl %>">이 매장 재고 찾기</a>
                        </div>
                    </article>
                <% } %>
            </div>
            <% if (finder.stores.isEmpty()) { %>
                <p class="store-finder-empty">검색 조건에 맞는 매장이 없습니다.</p>
            <% } %>
        <% } else if (finder != null) {
               String selectedProduct = finder.product == null ? "" : finder.product.getProductId();
               String selectedStore = finder.selected == null ? "" : finder.selected.id; %>
            <p class="store-finder-notice">학습용 가상 매장의 재고입니다. 매장 재고는 온라인 판매 재고와 별도로 표시됩니다.</p>
            <div class="store-finder-steps">
                <section aria-labelledby="stock-product-title">
                    <h2 id="stock-product-title">상품 고르기</h2>
                    <form class="store-finder-search" method="get" action="<%= productRoute %>" role="search" aria-label="매장 상품 검색">
                        <input type="hidden" name="picker" value="product">
                        <input type="hidden" name="product" value="<%= ProductView.escape(selectedProduct) %>">
                        <input type="hidden" name="store" value="<%= ProductView.escape(selectedStore) %>">
                        <label class="visually-hidden" for="store-product-query">상품명 또는 품번</label>
                        <input id="store-product-query" name="q" type="search" maxlength="100" placeholder="상품명이나 품번을 입력하세요"
                               value="<%= ProductView.escape(finder.keyword) %>">
                        <button type="submit">상품 검색</button>
                    </form>
                    <% if (finder.product != null) { %>
                        <article class="store-finder-selection">
                            <img src="<%= ProductView.image(context, finder.product) %>" alt="" width="72" height="88">
                            <div>
                                <strong><%= ProductView.money(finder.product.getPrice()) %></strong>
                                <a href="<%= ProductView.url(context, "/product.jsp", Map.of("id", selectedProduct)) %>"><%= ProductView.escape(finder.product.getName()) %></a>
                                <p>품번 <%= ProductView.escape(finder.product.getProductCode()) %></p>
                            </div>
                        </article>
                    <% } %>
                </section>
                <section aria-labelledby="stock-store-title">
                    <h2 id="stock-store-title">매장 재고 확인</h2>
                    <% if (finder.product == null) { %>
                        <p class="store-finder-empty">재고를 확인할 상품을 먼저 선택해 주세요.</p>
                    <% } else { %>
                        <form class="store-finder-search" method="get" action="<%= productRoute %>" role="search" aria-label="재고를 확인할 매장 검색">
                            <input type="hidden" name="picker" value="store">
                            <input type="hidden" name="q" value="<%= ProductView.escape(finder.keyword) %>">
                            <input type="hidden" name="product" value="<%= ProductView.escape(selectedProduct) %>">
                            <input type="hidden" name="store" value="<%= ProductView.escape(selectedStore) %>">
                            <label class="visually-hidden" for="store-stock-query">지역 또는 매장명</label>
                            <input id="store-stock-query" name="storeQuery" type="search" maxlength="100" placeholder="지역 또는 매장명 (비워 두면 전체)"
                                   value="<%= ProductView.escape(finder.storeKeyword) %>">
                            <button type="submit">매장 검색</button>
                        </form>
                    <% } %>
                    <% if (finder.checked) { %>
                        <article class="store-finder-selection store-stock-result" aria-label="매장 재고 조회 결과">
                            <div>
                                <h3><%= ProductView.escape(finder.selected.name) %></h3>
                                <p class="store-stock-status" data-stock-state="<%= finder.stockState %>"><%= finder.stockLabel %></p>
                            </div>
                            <a class="store-finder-action" href="<%= finder.selected.locationUrl %>">매장 위치</a>
                        </article>
                    <% } else if (finder.selected != null) { %>
                        <p class="store-finder-empty">선택한 매장 · <%= ProductView.escape(finder.selected.name) %></p>
                    <% } %>
                </section>
            </div>
            <% if ("product".equals(finder.picker) || ("store".equals(finder.picker) && finder.product != null)) {
                   boolean productPicker = "product".equals(finder.picker);
                   String pickerTitle = productPicker ? "상품명·품번 검색" : "매장 검색"; %>
                <dialog class="store-picker" data-store-picker open aria-labelledby="store-picker-title">
                    <header class="store-picker-heading">
                        <h2 id="store-picker-title"><%= pickerTitle %></h2>
                        <a href="<%= finder.closeUrl %>" aria-label="검색 창 닫기" data-picker-close>닫기</a>
                    </header>
                    <form class="store-finder-search" action="<%= productRoute %>" method="get" role="search" aria-label="<%= pickerTitle %>">
                        <input type="hidden" name="picker" value="<%= finder.picker %>">
                        <input type="hidden" name="product" value="<%= ProductView.escape(selectedProduct) %>">
                        <input type="hidden" name="store" value="<%= ProductView.escape(selectedStore) %>">
                        <% if (!productPicker) { %><input type="hidden" name="q" value="<%= ProductView.escape(finder.keyword) %>"><% } %>
                        <label class="visually-hidden" for="store-picker-query"><%= pickerTitle %></label>
                        <input id="store-picker-query" type="search" name="<%= productPicker ? "q" : "storeQuery" %>" maxlength="100"
                               value="<%= ProductView.escape(productPicker ? finder.keyword : finder.storeKeyword) %>" autofocus>
                        <button type="submit">검색</button>
                    </form>
                    <form class="store-picker-select" action="<%= productRoute %>" method="get">
                        <input type="hidden" name="q" value="<%= ProductView.escape(finder.keyword) %>">
                        <% if (productPicker) { %>
                            <input type="hidden" name="store" value="<%= ProductView.escape(selectedStore) %>">
                            <p class="store-finder-count">검색 결과 <strong><%= finder.total %></strong>개</p>
                            <div class="store-picker-choices">
                                <% for (ProductDTO choice : finder.products) { %>
                                    <label class="store-picker-option">
                                        <input type="radio" name="product" value="<%= ProductView.escape(choice.getProductId()) %>" required>
                                        <img src="<%= ProductView.image(context, choice) %>" alt="" width="64" height="72" loading="lazy">
                                        <span><strong><%= ProductView.money(choice.getPrice()) %></strong><span><%= ProductView.escape(choice.getName()) %></span><small>품번 <%= ProductView.escape(choice.getProductCode()) %></small></span>
                                    </label>
                                <% } %>
                            </div>
                            <% if (finder.products.isEmpty()) { %>
                                <p class="store-finder-empty"><%= finder.keyword.isEmpty() ? "상품명이나 품번을 입력해 주세요." : "이 페이지에 표시할 상품이 없습니다." %></p>
                            <% } %>
                            <% if (finder.previous != null || finder.next != null) { %>
                                <nav class="store-picker-pages" aria-label="상품 검색 페이지">
                                    <% if (finder.previous != null) { %><a href="<%= finder.previous %>" rel="prev">이전 결과</a><% } %>
                                    <% if (finder.next != null) { %><a href="<%= finder.next %>" rel="next">다음 결과</a><% } %>
                                </nav>
                            <% } %>
                        <% } else { %>
                            <input type="hidden" name="product" value="<%= ProductView.escape(selectedProduct) %>">
                            <input type="hidden" name="storeQuery" value="<%= ProductView.escape(finder.storeKeyword) %>">
                            <div class="store-picker-choices">
                                <% for (Store store : finder.stores) { %>
                                    <label class="store-picker-option">
                                        <input type="radio" name="store" value="<%= ProductView.escape(store.id) %>" required>
                                        <span><strong><%= ProductView.escape(store.name) %></strong><small><%= ProductView.escape(store.city) %></small></span>
                                    </label>
                                <% } %>
                            </div>
                            <% if (finder.stores.isEmpty()) { %><p class="store-finder-empty">검색 조건에 맞는 매장이 없습니다.</p><% } %>
                        <% } %>
                        <button class="store-picker-confirm" type="submit" <%= (productPicker ? finder.products.isEmpty() : finder.stores.isEmpty()) ? "disabled" : "" %>><%= productPicker ? "상품 선택" : "매장 선택" %></button>
                    </form>
                </dialog>
            <% } %>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
