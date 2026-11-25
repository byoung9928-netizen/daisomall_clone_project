<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="daiso.product.controller.ProductView" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.Stock" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.History" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%
    List<Stock> stocks = (List<Stock>) request.getAttribute("storeManagerStock");
    int currentPage = (Integer) request.getAttribute("storeManagerPage");
    long totalPages = (Long) request.getAttribute("storeManagerPages");
    boolean showHistory = Boolean.TRUE.equals(request.getAttribute("storeManagerShowHistory"));
    List<History> history = (List<History>) request.getAttribute("storeManagerHistory");
    String pageLink = showHistory ? "?mode=history&amp;page=" : "?page=";
    DateTimeFormatter historyDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">
    <main class="admin-content">
        <h1 class="page-title">
            <%= ProductView.escape(request.getAttribute("storeManagerName")) %> 재고 관리
        </h1>

        <form method="post" action="${pageContext.request.contextPath}/app/member/logout">
            <button type="submit" class="button secondary">로그아웃</button>
        </form>
        <a class="button secondary"
   href="${pageContext.request.contextPath}/app/store-manager/pickup-orders">픽업 주문</a>
        
        <nav aria-label="매장 재고 메뉴">
    <a class="button secondary"
       href="${pageContext.request.contextPath}/app/store-manager/inventory">재고 목록</a>
    <a class="button secondary" href="?mode=history">변경 이력</a>
</nav>

<% if (showHistory) { %>
    <h2>재고 변경 이력</h2>
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
<% } else { %>

        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th scope="col">상품명</th>
                        <th scope="col">품번</th>
                        <th scope="col">현재 수량</th>
                        <th scope="col">관리</th>
                    </tr>
                </thead>
                <tbody>
                    <% if (stocks.isEmpty()) { %>
                        <tr><td colspan="4">조회할 재고가 없습니다.</td></tr>
                    <% } else {
                        for (Stock stock : stocks) { %>
                        <tr>
                            <td><%= ProductView.escape(stock.name) %></td>
                            <td><%= ProductView.escape(stock.code) %></td>
                            <td><%= stock.quantity %>개</td>
                            <td>
    <details>
        <summary>재고 조정</summary>
        <form method="post"
              action="${pageContext.request.contextPath}/app/store-manager/inventory/adjust">
            <input type="hidden" name="csrf"
                   value="<%= ProductView.escape(request.getAttribute("storeManagerCsrf")) %>">
            <input type="hidden" name="viewStoreId"
                   value="<%= ProductView.escape(request.getAttribute("storeManagerId")) %>">
            <input type="hidden" name="productId"
                   value="<%= ProductView.escape(stock.id) %>">
            <input type="hidden" name="version" value="<%= stock.version %>">
            <input type="hidden" name="page" value="<%= currentPage %>">

            <label>변경 유형
                <select name="change">
                    <option value="RECEIVE">입고</option>
                    <option value="DAMAGE">파손</option>
                    <option value="COUNT">재고 조사</option>
                </select>
            </label>

            <label>수량
                <input type="number" name="amount" min="0" max="2147483647" required>
            </label>
            <p>입고·파손은 증감 수량, 재고 조사는 최종 수량을 입력하세요.</p>

            <label>변경 사유
                <input type="text" name="reason" maxlength="255" required>
            </label>

            <button type="submit" class="button secondary">저장</button>
        </form>
    </details>
</td>
                        </tr>
                    <%  }
                    } %>
                </tbody>
            </table>
        </div>
<% } %>
        <nav aria-label="재고 목록 페이지">
            <% if (currentPage > 1) { %>
                <a class="button secondary" href="<%= pageLink %><%= currentPage - 1 %>">이전</a>
            <% } %>
            <span><%= currentPage %> / <%= totalPages %> 페이지</span>
            <% if (currentPage < totalPages) { %>
                <a class="button secondary" href="<%= pageLink %><%= currentPage + 1 %>">다음</a>
            <% } %>
        </nav>
    </main>
</body>
</html>