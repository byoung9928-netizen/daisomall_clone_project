<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.OnlineInventoryController,daiso.product.controller.OnlineInventoryController.Page,daiso.product.controller.ProductView,daiso.product.controller.ProductDTO,daiso.product.controller.OnlineStockState" %>
<%
    if (!Boolean.TRUE.equals(request.getAttribute("onlineInventoryReady"))) {
        request.getRequestDispatcher(OnlineInventoryController.ROUTE).forward(request, response);
        return;
    }
    String inventoryContext = request.getContextPath();
    Page online = (Page) request.getAttribute("onlineInventoryPage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-workspace.js?v=batch-json-1" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-inventory-batch.js?v=batch-json-1" defer></script>
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1" data-admin-query="inventory">
            <h1 class="page-title">온라인 재고 관리</h1>
            <p class="error-message" role="alert" data-admin-query-error hidden></p>
            <div data-admin-query-content>
            <% if (request.getAttribute("inventoryBatchNotice") != null) { %>
                <p id="inventory-batch-result" class="inventory-success" role="status" tabindex="-1"><%= ProductView.escape(request.getAttribute("inventoryBatchNotice")) %></p>
            <% } %>
            <% if (request.getAttribute("onlineInventorySaved") != null) { %>
                <p class="inventory-success" role="status"><%= ProductView.escape(request.getAttribute("onlineInventorySaved")) %></p>
            <% } %>
            <p>온라인 판매 재고입니다. 수동 조정은 기존 상품 변경 이력에 기록됩니다. 판매 종료·판매 예정 상품은 발주 집계에서 제외합니다.</p>
            <% if (request.getAttribute("onlineInventoryError") != null && request.getAttribute("onlineStockProduct") == null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("onlineInventoryError")) %></p>
            <% } %>
            <% if (online != null) { %>
                <div class="store-metrics" aria-label="전체 온라인 상품 재고 요약">
                    <% for (OnlineStockState bucket : OnlineStockState.values()) { %>
                        <a href="<%= online.summaryUrl(inventoryContext, bucket) %>" data-stock-state="<%= bucket.name() %>">
                            <span><%= bucket == OnlineStockState.ALL ? "전체 상품" : bucket.label %></span>
                            <strong><%= online.summary.get(bucket) %>종</strong>
                        </a>
                    <% } %>
                </div>
                <%@ include file="online-stock-editor.jspf" %>
                <% ProductDTO trendProduct = (ProductDTO) request.getAttribute("stockTrendProduct");
                   if (trendProduct != null) { %>
                    <section class="admin-panel" id="online-stock-trend">
                        <h2><%= ProductView.escape(trendProduct.getName()) %> 재고 및 판매량</h2>
                        <form class="inventory-batch-form" data-admin-query-form method="get" action="<%= inventoryContext + OnlineInventoryController.ROUTE %>#online-stock-trend">
                            <% for (Map.Entry<String, String> filterEntry : online.filters.entrySet()) { %>
                                <input type="hidden" name="<%= filterEntry.getKey() %>" value="<%= ProductView.escape(filterEntry.getValue()) %>">
                            <% } %>
                            <input type="hidden" name="trendId" value="<%= ProductView.escape(trendProduct.getProductId()) %>">
                            <label class="field">조회 기간
                                <select name="trendDays">
                                    <% for (String days : java.util.List.of("7", "30", "90")) { %>
                                        <option value="<%= days %>" <%= days.equals(request.getParameter("trendDays")) || ("30".equals(days) && request.getParameter("trendDays") == null) ? "selected" : "" %>>최근 <%= days %>일</option>
                                    <% } %>
                                </select>
                            </label>
                            <button class="button secondary" type="submit">조회</button>
                        </form>
                        <%@ include file="stock-trend.jspf" %>
                    </section>
                <% } %>
                <form class="admin-search store-admin-filters" data-admin-query-form method="get" action="<%= ProductView.escape(inventoryContext + OnlineInventoryController.ROUTE) %>">
                    <label class="field">상품명·품번
                        <input name="q" type="search" maxlength="120" value="<%= ProductView.escape(online.keyword) %>">
                    </label>
                    <label class="field">카테고리
                        <select name="category">
                            <option value="">전체 카테고리</option>
                            <% for (Map.Entry<String, String> category : online.categories.entrySet()) { %>
                                <option value="<%= ProductView.escape(category.getKey()) %>" <%= category.getKey().equals(online.category) ? "selected" : "" %>><%= ProductView.escape(category.getValue()) %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">재고 상태
                        <select name="state">
                            <% for (OnlineStockState state : OnlineStockState.values()) { %>
                                <option value="<%= state.name() %>" <%= state == online.state ? "selected" : "" %>><%= state.label %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">정렬
                        <select name="sort">
                            <option value="">상품명순</option>
                            <option value="low" <%= "low".equals(online.sort) ? "selected" : "" %>>재고 적은 순</option>
                            <option value="shortage" <%= "shortage".equals(online.sort) ? "selected" : "" %>>부족순 (기준 대비)</option>
                        </select>
                    </label>
                    <div class="inventory-filter-actions">
                        <button class="button primary" type="submit">검색</button>
                        <a class="button secondary" href="<%= ProductView.escape(inventoryContext + OnlineInventoryController.ROUTE) %>" data-admin-query-link>초기화</a>
                    </div>
                </form>
                <p class="inventory-range" role="status">전체 <strong><%= online.total %></strong>종
                    <% if (online.total > 0) { %><span> · <%= online.first %>–<%= online.last %>번째 상품 표시</span><% } %></p>
                <% boolean storeBatch = false;
                       String batchStoreName = "";
                       Map<String, String> batchFilters = online.filters;
                       String batchCsrf = String.valueOf(request.getAttribute("onlineStockCsrf")); %>
                <%@ include file="inventory-batch-form.jspf" %>
                <div class="admin-table-scroll" tabindex="0" role="region" aria-label="온라인 재고 상품 표">
                    <table class="admin-table store-stock-table">
                        <thead><tr>
                            <th scope="col" class="admin-batch-choice">
                            <span>선택</span>
                            <input type="checkbox" data-batch-select-all hidden aria-label="현재 페이지 상품 전체 선택">
                        </th><th scope="col">상품</th><th scope="col">온라인 재고</th><th scope="col">재고 상태</th><th scope="col">부족 기준</th><th scope="col">관리</th>
                        </tr></thead>
                        <tbody>
                            <% for (ProductDTO item : online.products) {
                                   OnlineStockState state = OnlineStockState.of(item); %>
                                <tr>
                                    <td class="admin-batch-choice"><input type="checkbox" name="batchProduct" form="inventory-batch-form" data-batch-product
                    value="<%= ProductView.escape(item.getProductId()) %>" aria-label="<%= ProductView.escape(item.getName()) %> 선택" data-name="<%= ProductView.escape(item.getName()) %>" data-stock="<%= item.getStockQuantity() %>" data-threshold="<%= item.getLowStockThreshold() %>"></td>
                <td><div class="store-stock-product">
                                        <img src="<%= ProductView.image(inventoryContext, item) %>" width="48" height="52" loading="lazy" alt="">
                                        <div><strong><%= ProductView.escape(item.getName()) %></strong><small>품번 <%= ProductView.escape(item.getProductCode()) %></small></div>
                                    </div></td>
                                    <td><%= item.getStockQuantity() %>개</td>
                                    <td><span class="store-stock-badge" data-stock-state="<%= state.name() %>"><%= state.label %></span>
                                        <% if (state == OnlineStockState.INACTIVE || "SOLD_OUT".equals(item.getStatus())) { %>
                                            <small class="inventory-stock-number">판매 상태 · <%= "SOLD_OUT".equals(item.getStatus()) ? "품절" : ProductView.status(item) %></small>
                                        <% } %>
                                    </td>
                                    <td><%= item.getLowStockThreshold() %>개 이하
                                        <% if (state == OnlineStockState.LOW || state == OnlineStockState.SOLD_OUT) { %>
                                            <small class="inventory-stock-number">최소 입고 <%= (long) item.getLowStockThreshold() + 1 - item.getStockQuantity() %>개</small>
                                        <% } %>
                                    </td>
                                    <td><div class="store-stock-actions">
                                        <a class="button secondary" href="<%= online.editUrl(inventoryContext, item.getProductId()) %>#online-stock-editor">재고·기준 조정</a>
                                        <a class="text-button" data-admin-query-link href="<%= ProductView.url(inventoryContext, OnlineInventoryController.ROUTE,
                                                ProductView.with(online.filters, "trendId", item.getProductId())) %>#online-stock-trend">재고 분석</a>
                                        <a class="text-button" href="<%= ProductView.url(inventoryContext, "/app/admin/products",
                                                Map.of("history", "1", "historyId", item.getProductId())) %>#product-history">변경 이력</a>
                                    </div></td>
                                </tr>
                            <% } %>
                            <% if (online.products.isEmpty()) { %><tr><td colspan="6">조건에 맞는 상품이 없습니다.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <% if (online.total > 0) {
                       InventoryPagination inventoryPagination = online.pagination; %>
                    <%@ include file="inventory-pagination.jspf" %>
                <% } %>
            <% } %>
            </div>
        </main>
    </div>
</body>
</html>
