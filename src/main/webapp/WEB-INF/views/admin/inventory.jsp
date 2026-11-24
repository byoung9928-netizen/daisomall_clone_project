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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1">
            <h1 class="page-title">온라인 재고 관리</h1>
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
                <form class="admin-search store-admin-filters" method="get" action="<%= ProductView.escape(inventoryContext + OnlineInventoryController.ROUTE) %>">
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
                        <a class="button secondary" href="<%= ProductView.escape(inventoryContext + OnlineInventoryController.ROUTE) %>">초기화</a>
                    </div>
                </form>
                <p class="inventory-range" role="status">전체 <strong><%= online.total %></strong>종
                    <% if (online.total > 0) { %><span> · <%= online.first %>–<%= online.last %>번째 상품 표시</span><% } %></p>
                <div class="admin-table-scroll" tabindex="0" role="region" aria-label="온라인 재고 상품 표">
                    <table class="admin-table store-stock-table">
                        <thead><tr>
                            <th scope="col">상품</th><th scope="col">온라인 재고</th><th scope="col">재고 상태</th><th scope="col">부족 기준</th><th scope="col">관리</th>
                        </tr></thead>
                        <tbody>
                            <% for (ProductDTO item : online.products) {
                                   OnlineStockState state = OnlineStockState.of(item); %>
                                <tr>
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
                                    <td><%= item.getLowStockThreshold() %>개 이하</td>
                                    <td><div class="store-stock-actions">
                                        <a class="button secondary" href="<%= online.editUrl(inventoryContext, item.getProductId()) %>#online-stock-editor">재고·기준 조정</a>
                                        <a class="text-button" href="<%= ProductView.url(inventoryContext, "/app/admin/products",
                                                Map.of("history", "1", "historyId", item.getProductId())) %>#product-history">변경 이력</a>
                                    </div></td>
                                </tr>
                            <% } %>
                            <% if (online.products.isEmpty()) { %><tr><td colspan="5">조건에 맞는 상품이 없습니다.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <% if (online.total > 0) {
                       InventoryPagination inventoryPagination = online.pagination; %>
                    <%@ include file="inventory-pagination.jspf" %>
                <% } %>
            <% } %>
        </main>
    </div>
</body>
</html>
