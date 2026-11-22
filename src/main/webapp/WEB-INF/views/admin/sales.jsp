<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,daiso.product.controller.ProductView" %>
<%
    List<Object[]> salesRows = (List<Object[]>) request.getAttribute("salesRows");
    Object salesError = request.getAttribute("salesError");
    java.text.NumberFormat salesNumbers = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">
<a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
<%@ include file="../common/admin-header.jspf" %>
<div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
    <main id="admin-content" class="admin-content" tabindex="-1">
        <h1 class="page-title">판매순위</h1>
        <%-- ===== 결제 승인일 기준으로 판매순위를 조회 ===== --%>
        <form class="admin-search" method="get" action="${pageContext.request.contextPath}/app/admin/sales">
            <label>시작일 <input type="date" name="from" required
                value="<%= ProductView.escape(request.getAttribute("salesFrom")) %>"></label>
            <label>종료일 <input type="date" name="through" required
                value="<%= ProductView.escape(request.getAttribute("salesThrough")) %>"></label>
            <button type="submit" class="button primary">조회</button>
        </form>
        <% if (salesError != null) { %>
            <p role="alert"><%= ProductView.escape(salesError) %></p>
        <% } else if (salesRows == null || salesRows.isEmpty()) { %>
            <p>선택한 기간에 판매된 상품이 없습니다.</p>
        <% } else { %>
            <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품 판매순위 표">
                <table class="admin-table">
                    <thead><tr><th scope="col">순위</th><th scope="col">상품 ID</th><th scope="col">상품명</th>
                        <th scope="col">판매수량</th><th scope="col">상품 매출</th></tr></thead>
                    <tbody>
                    <% for (Object[] sale : salesRows) { %>
                        <tr>
                            <td><%= sale[0] %></td>
                            <td><%= ProductView.escape(sale[1]) %></td>
                            <td><%= ProductView.escape(sale[2]) %></td>
                            <td><%= salesNumbers.format(sale[3]) %>개</td>
                            <td><%= salesNumbers.format(sale[4]) %>원</td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        <% } %>
    </main>
</div>
</body>
</html>
