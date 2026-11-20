<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.OnlineInventoryController,daiso.product.controller.OnlineInventoryController.Page,daiso.product.controller.ProductView,daiso.product.controller.ProductDTO,daiso.product.dao.StoreInventoryDAO.State" %>
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
            <% if (Boolean.TRUE.equals(request.getAttribute("onlineInventorySaved"))) { %>
                <p class="inventory-success" role="status">온라인 재고가 수정되었습니다.</p>
            <% } %>
            <p>온라인 판매 재고입니다. 재고 수정은 상품 관리에서 저장되며 기존 상품 변경 이력에 남습니다.</p>
            <% if (request.getAttribute("onlineInventoryError") != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("onlineInventoryError")) %></p>
            <% } %>
            <% if (online != null) { %>
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
                            <% for (State state : State.values()) { %>
                                <option value="<%= state.name() %>" <%= state == online.state ? "selected" : "" %>><%= state.label %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">정렬
                        <select name="sort">
                            <option value="">상품명순</option>
                            <option value="low" <%= online.lowFirst ? "selected" : "" %>>재고 적은 순</option>
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
                            <th scope="col">상품</th><th scope="col">온라인 재고</th><th scope="col">재고 상태</th><th scope="col">관리</th>
                        </tr></thead>
                        <tbody>
                            <% for (ProductDTO item : online.products) {
                                   State state = State.ofQuantity(item.getStockQuantity()); %>
                                <tr>
                                    <td><div class="store-stock-product">
                                        <img src="<%= ProductView.image(inventoryContext, item) %>" width="48" height="52" loading="lazy" alt="">
                                        <div><strong><%= ProductView.escape(item.getName()) %></strong><small>품번 <%= ProductView.escape(item.getProductCode()) %></small></div>
                                    </div></td>
                                    <td><%= item.getStockQuantity() %>개</td>
                                    <td><span class="store-stock-badge" data-stock-state="<%= state.name() %>"><%= state.label %></span></td>
                                    <td><a class="button secondary"
                                           href="<%= online.editUrl(inventoryContext, item.getProductId()) %>#product-stock">재고 수정</a></td>
                                </tr>
                            <% } %>
                            <% if (online.products.isEmpty()) { %><tr><td colspan="4">조건에 맞는 상품이 없습니다.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <% if (online.previous != null || online.next != null) { %>
                    <nav class="admin-product-pagination" aria-label="온라인 재고 페이지">
                        <% if (online.previous != null) { %><a class="button secondary" rel="prev" href="<%= online.previous %>">이전</a><% } %>
                        <span aria-current="page"><%= online.page %> / <%= online.pages %></span>
                        <% if (online.next != null) { %><a class="button secondary" rel="next" href="<%= online.next %>">다음</a><% } %>
                    </nav>
                <% } %>
            <% } %>
        </main>
    </div>
</body>
</html>
