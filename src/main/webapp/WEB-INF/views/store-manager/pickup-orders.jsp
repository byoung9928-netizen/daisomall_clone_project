<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map" %>
<%@ page import="daiso.product.controller.ProductView" %>
<%
    List<Map<String, Object>> orders =
            (List<Map<String, Object>>) request.getAttribute("pickupOrders");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">
    <main class="admin-content">
        <h1 class="page-title">
            <%= ProductView.escape(request.getAttribute("storeManagerName")) %> 픽업 주문
        </h1>

        <nav aria-label="매장 관리자 메뉴">
            <a class="button secondary"
               href="${pageContext.request.contextPath}/app/store-manager/inventory">매장 재고</a>
            <a class="button secondary" aria-current="page"
               href="${pageContext.request.contextPath}/app/store-manager/pickup-orders">픽업 주문</a>
        </nav>

        <p>최근 결제 완료 주문 · 최대 50건</p>
        <div class="admin-table-scroll">
            <table class="admin-table">
                <thead>
                    <tr>
                        <th scope="col">주문번호</th>
                        <th scope="col">수령인</th>
                        <th scope="col">준비할 상품</th>
                        <th scope="col">결제 일시</th>
                        <th scope="col">결제 상태</th>
                        <th scope="col">픽업 진행 상태</th>
                    </tr>
                </thead>
                <tbody>
                    <% if (orders.isEmpty()) { %>
                        <tr><td colspan="6">결제 완료된 픽업 주문이 없습니다.</td></tr>
                    <% } else {
                        for (Map<String, Object> order : orders) { %>
                        <tr>
                            <td><%= ProductView.escape(order.get("orderNo")) %></td>
                            <td><%= ProductView.escape(order.get("recipient")) %></td>

<td>
    <% List<?> items = (List<?>) order.get("items");
       if (items == null || items.isEmpty()) { %>
        준비할 상품 정보가 없습니다.
    <% } else { %>
        <ul>
            <% for (Object item : items) { %>
                <li><%= ProductView.escape(item) %></li>
            <% } %>
        </ul>
    <% } %>
</td>

<td><%= ProductView.escape(order.get("approvedAt")) %></td>
                            <td>결제 완료</td>
						<td>
    <% String pickupStatus = (String) order.get("pickupStatus"); %>
    <%= "PICKED_UP".equals(pickupStatus) ? "수령 완료"
            : "READY".equals(pickupStatus) ? "수령 대기" : "준비 대기" %>

    <% if (pickupStatus == null || "READY".equals(pickupStatus)) { %>
        <form method="post"
              action="${pageContext.request.contextPath}/app/store-manager/pickup-orders/status">
            <input type="hidden" name="csrf"
                   value="<%= ProductView.escape(request.getAttribute("storeManagerCsrf")) %>">
            <input type="hidden" name="orderNo"
                   value="<%= ProductView.escape(order.get("orderNo")) %>">
            <button type="submit" class="button secondary" name="action"
                    value="<%= pickupStatus == null ? "ready" : "complete" %>">
                <%= pickupStatus == null ? "준비 완료" : "수령 완료" %>
            </button>
        </form>
    <% } %>
</td>
					</tr>
                    <%  }
                    } %>
                </tbody>
            </table>
        </div>
    </main>
</body>
</html>