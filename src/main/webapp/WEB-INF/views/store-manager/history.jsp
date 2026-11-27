<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.History" %>
<%
    List<History> history = (List<History>) request.getAttribute("storeManagerHistory");
    String historyProduct = (String) request.getAttribute("storeManagerHistoryProduct");
    String historyType = (String) request.getAttribute("storeManagerHistoryType");
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
        <form method="get" action="<%= storeManagerBase %>history">
            <% if (!historyProduct.isEmpty()) { %>
                <input type="hidden" name="product" value="<%= ProductView.escape(historyProduct) %>">
            <% } %>
            <label class="field">변경 유형
                <select name="historyType">
                    <% for (String[] option : new String[][]{{"", "전체"}, {"INITIAL", "초기 등록"}, {"RECEIVE", "입고"},
                            {"ISSUE", "출고"}, {"DAMAGE", "파손"}, {"RETURN", "반품 입고"}, {"COUNT", "실사"},
                            {"THRESHOLD", "부족 기준"}, {"REGISTER", "취급 등록"}, {"RESUME", "취급 재개"},
                            {"UNLIST", "취급 종료"}, {"TRANSFER", "매장 이동"}, {"PICKUP_PAYMENT", "픽업 결제 차감"},
                            {"PICKUP_CANCEL", "픽업 주문 취소 복구"}, {"PICKUP_REFUND", "픽업 환불 복구"}}) { %>
                        <option value="<%= option[0] %>" <%= option[0].equals(historyType) ? "selected" : "" %>><%= option[1] %></option>
                    <% } %>
                </select>
            </label>
            <button class="button secondary" type="submit">조회</button>
        </form>
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
                            <td><%= ProductView.escape(entry.type) %>
                                <% if (entry.orderNo != null) { %><br><small>주문번호 <%= ProductView.escape(entry.orderNo) %></small><% } %></td>
                            <td>
                                <% if (entry.beforeThreshold != null) { %>
                                    <%= entry.beforeThreshold %>개 이하 → <%= entry.afterThreshold %>개 이하
                                <% } else { %>
                                    <%= entry.before %>개 → <%= entry.after %>개
                                <% } %>
                            </td>
                            <td><%= ProductView.escape(entry.reason) %></td>
                            <td><%= "SYSTEM".equals(entry.actorType) ? "시스템" : entry.administrator == null
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
