<%@ page contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Collections" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="daiso.order.db.OrderBean" %>
<%@ page import="daiso.product.controller.ProductView" %>

<%
List<OrderBean> orders =
    (List<OrderBean>) request.getAttribute("adminOrders");

if (orders == null) {
    orders = Collections.emptyList();
}

NumberFormat money = NumberFormat.getIntegerInstance();

DateTimeFormatter dateFormat =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

Object query = request.getAttribute("adminOrderQuery");
Object error = request.getAttribute("adminOrderError");
int orderPage = 
	request.getAttribute("adminOrderPage") instanceof Number
		?((Number) request.getAttribute("adminOrderPage")).intValue()
		:1;
boolean hasNext = Boolean.TRUE.equals(request.getAttribute("adminOrderHasNext"));
%>

<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">

<a class="skip-link" href="#admin-content">
    관리자 본문 바로가기
</a>

<%@ include file="../common/admin-header.jspf" %>

<div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>

    <main id="admin-content"
          class="admin-content"
          data-admin-page="orders"
          tabindex="-1">

        <h1 class="page-title">주문 관리</h1>

        <form class="admin-search"
              method="get"
              action="${pageContext.request.contextPath}/app/admin/orders">

            <label class="field">
                <span>주문번호·회원번호·상품명 검색</span>
                <input type="search"
                       name="query"
                       maxlength="120"
                       value="<%= ProductView.escape(query) %>">
            </label>

            <button class="button" type="submit">검색</button>
        </form>

        <% if (error != null) { %>
            <p class="error-message" role="alert">
                <%= ProductView.escape(error) %>
            </p>
        <% } else { %>

            <p class="admin-count" role="status">
                <%=orderPage %>페이지 · 현재<%=orders.size() %>건
            </p>

            <div class="admin-table-scroll"
                 tabindex="0"
                 role="region"
                 aria-label="주문 목록">

                <table class="admin-table">
                    <caption class="visually-hidden">주문 목록</caption>

                    <thead>
                        <tr>
                            <th scope="col">주문번호</th>
                            <th scope="col">상품명</th>
                            <th scope="col">회원번호</th>
                            <th scope="col">주문일시</th>
                            <th scope="col">결제금액</th>
                            <th scope="col">주문 상태</th>
                            <th scope="col">결제 상태</th>
                        </tr>
                    </thead>

                    <tbody>
                        <% if (orders.isEmpty()) { %>
                            <tr>
                                <td colspan="7">
                                    조회된 주문이 없습니다.
                                </td>
                            </tr>
                        <% } else {
                            for (OrderBean order : orders) {
                        %>
                            <tr>
                                <td><a href="${pageContext.request.contextPath}/app/admin/orders/detail?orderNo=<%=java.net.URLEncoder.encode(order.getOrderNo(),"UTF-8")%>">
                                <%= ProductView.escape(
                                    order.getOrderNo()) %></a></td>
                                <td><%= ProductView.escape(
                                	order.getProductSummary()) %></td>

                                <td><%= ProductView.escape(
                                    order.getMemberId()) %></td>

                                <td>
                                    <%= order.getCreatedAt() == null
                                        ? "-"
                                        : order.getCreatedAt()
                                            .toLocalDateTime()
                                            .format(dateFormat) %>
                                </td>

                                <td>
                                    <%= money.format(
                                        order.getTotalAmount()) %>원
                                </td>

                                <td><%= ProductView.escape(
                                    order.getStatus()) %></td>

                                <td>
                                    <%= order.getPaymentStatus() == null
                                        ? "결제 기록 없음"
                                        : ProductView.escape(
                                            order.getPaymentStatus()) %>
                                </td>
                            </tr>
                        <% }
                        } %>
                    </tbody>
                </table>
            </div>
            <nav aria-label="주문 목록 페이지"
            	style="dispaly:flex;justify-content:center; align-items:center; gap:16px; margin-top:24px;">
            	<%if(orderPage>1){ %>
            		<form method="get"
            				action="${pageContext.request.contextPath}/app/admin/orders">
            				<input type="hidden" name="query"
            						value="<%=ProductView.escape(query) %>">
            				<input type="hidden" name="page"
            						value="<%=orderPage -1 %>">
            				<button class="button secondary" type="submit">
            					이전
            				</button>
            		</form>
            		<%} %>
            		<span aria-current="page"><%=orderPage %>페이지</span>
            		<% if(hasNext){ %>
            		<form method="get">
            			action="${pageContext.request.contextPath }/app/admin/orders"
            			<input type="hidden" name="query"
            				value="<%=ProductView.escape(query) %>">
            				
            			<input type="hidden" name="page"
            				value="<%=orderPage+1L %>">
            			<button class ="button secondary" type="submit">
            				다음
            			</button>
            		</form>
            		<%} %>
            	</nav>
        <% } %>
    </main>
</div>

</body>
</html>