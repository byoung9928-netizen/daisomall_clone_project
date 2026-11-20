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
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1">
            <h1 class="page-title">온라인 재고 관리</h1>
            <p>온라인 판매 재고입니다. 재고 수정은 상품 관리에서 저장되며 기존 상품 변경 이력에 남습니다.</p>
            <% if (request.getAttribute("onlineInventoryError") != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("onlineInventoryError")) %></p>
            <% } %>
            <% if (online != null) { %>
                <form class="admin-search store-admin-filters" method="get" action="<%= ProductView.escape(inventoryContext + OnlineInventoryController.ROUTE) %>">
                    <label class="field">상품명·품번
                        <input name="q" type="search" maxlength="120" value="<%= ProductView.escape(online.keyword) %>">
                    </label>
                    <label class="field">재고 상태
                        <select name="state">
                            <% for (State state : State.values()) { %>
                                <option value="<%= state.name() %>" <%= state == online.state ? "selected" : "" %>><%= state.label %></option>
                            <% } %>
                        </select>
                    </label>
                    <button class="button primary" type="submit">조회</button>
                </form>
                <p>검색 결과 <strong><%= online.total %></strong>종</p>
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
                                           href="<%= ProductView.url(inventoryContext, "/app/admin/products", Map.of("edit", item.getProductId())) %>">상품 관리에서 수정</a></td>
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
