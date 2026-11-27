<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.History" %>
<%
    List<History> history = (List<History>) request.getAttribute("storeManagerHistory");
    String historyProduct = (String) request.getAttribute("storeManagerHistoryProduct");
    DateTimeFormatter historyDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css">
</head>
<body class="admin-body store-manager-body">
    <%@ include file="layout.jspf" %>
    <main class="admin-content" id="store-manager-content" tabindex="-1">
        <h1 class="page-title">재고 변경 이력</h1>
        <% if (!historyProduct.isEmpty()) { %>
            <p>상품 ID <strong><%= ProductView.escape(historyProduct) %></strong>
                <a class="button secondary" href="<%= storeManagerBase %>history">전체 이력 보기</a></p>
        <% } %>
    <div class="admin-table-scroll">
        <table class="admin-table">
            <thead>
                <tr>
                    <th scope="col">일시</th>
                    <th scope="col">상품</th>
                    <th scope="col">유형</th>
                    <th scope="col">변경 전 → 후</th>
                    <th scope="col">사유</th>
                    <th scope="col">처리자</th>
                </tr>
            </thead>
            <tbody>
                <% if (history.isEmpty()) { %>
                    <tr><td colspan="6">재고 변경 이력이 없습니다.</td></tr>
                <% } else {
                    for (History entry : history) { %>
                        <tr>
                            <td><%= historyDate.format(entry.date) %></td>
                            <td><%= ProductView.escape(entry.name) %></td>
                            <td><%= ProductView.escape(entry.type) %></td>
                            <td>
                                <% if (entry.beforeThreshold != null) { %>
                                    <%= entry.beforeThreshold %>개 이하 → <%= entry.afterThreshold %>개 이하
                                <% } else { %>
                                    <%= entry.before %>개 → <%= entry.after %>개
                                <% } %>
                            </td>
                            <td><%= ProductView.escape(entry.reason) %></td>
                            <td><%= entry.administrator == null
                                    ? "초기 데이터" : ProductView.escape(entry.administrator) %></td>
                        </tr>
                    <% }
                } %>
            </tbody>
        </table>
    </div>

        <% daiso.product.controller.InventoryPagination inventoryPagination =
                (daiso.product.controller.InventoryPagination) request.getAttribute("storeManagerHistoryPagination");
           request.setAttribute("adminPaginationLabel", "재고 변경 이력 페이지"); %>
        <%@ include file="../admin/inventory-pagination.jspf" %>
    </main>
    </div>
</body>
</html>
