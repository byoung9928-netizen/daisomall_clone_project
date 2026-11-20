<%@ page contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.util.Collections" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="daiso.product.controller.ProductView" %>

<%
List<Map<String, Object>> returnList =
    (List<Map<String, Object>>) request.getAttribute("adminReturns");

if (returnList == null) {
    returnList = Collections.emptyList();
}

Object returnError = request.getAttribute("adminReturnError");
NumberFormat refundMoney = NumberFormat.getIntegerInstance();
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
          data-admin-page="returns"
          tabindex="-1">

        <h1 class="page-title">반품·환불 관리</h1>

        <% if (returnError != null) { %>

            <p class="error-message" role="alert">
                <%= ProductView.escape(returnError) %>
            </p>

        <% } else { %>

            <p class="admin-count">
                최근 반품 신청 <%= returnList.size() %>건
                · 최대 50건 표시
            </p>

            <div class="admin-table-scroll"
                 tabindex="0"
                 role="region"
                 aria-label="반품 신청 목록">

                <table class="admin-table">
                    <caption class="visually-hidden">
                        반품 신청 및 환불 처리 내역
                    </caption>

                    <thead>
                        <tr>
                            <th scope="col">주문번호</th>
                            <th scope="col">상품명</th>
                            <th scope="col">회원번호</th>
                            <th scope="col">반품 사유</th>
                            <th scope="col">환불 예정 금액</th>
                            <th scope="col">처리 상태</th>
                            <th scope="col">신청일시</th>
                            <th scope="col">관리</th>
                        </tr>
                    </thead>

                    <tbody>
                        <% if (returnList.isEmpty()) { %>
                            <tr>
                                <td colspan="8">
                                    반품 신청 내역이 없습니다.
                                </td>
                            </tr>

                        <% } else {
                            for (Map<String, Object> item : returnList) {
                        %>
                            <tr>
                                <td>
                                    <a href="${pageContext.request.contextPath}/app/admin/orders/detail?orderNo=<%= java.net.URLEncoder.encode(
                                        (String) item.get("orderNo"), "UTF-8") %>">
                                        <%= ProductView.escape(
                                            item.get("orderNo")) %>
                                    </a>
                                </td>

                                <td><%= ProductView.escape(
                                    item.get("productSummary")) %></td>

                                <td><%= ProductView.escape(
                                    item.get("memberId")) %></td>

                                <td>
                                    <%= ProductView.escape(
                                        item.get("reason")) %>
                                    <p><%= ProductView.escape(
                                        item.get("reasonDetail")) %></p>
                                </td>

                                <td>
                                    <%= refundMoney.format(
                                        item.get("refundAmount")) %>원
                                </td>

                                <td>
                                    <%= ProductView.escape(
                                        item.get("statusLabel")) %>

                                    <% if (item.get("adminNote") != null) { %>
                                        <p><%= ProductView.escape(
                                            item.get("adminNote")) %></p>
                                    <% } %>
                                </td>

                                <td><%= ProductView.escape(
                                    item.get("requestedAt")) %></td>

                                <td>
                                    <% if ("REQUESTED".equals(
                                            item.get("status"))) { %>

                                        <form method="post"
                                              action="${pageContext.request.contextPath}/app/admin/returns/approve"
                                              onsubmit="return window.confirm('반품을 승인하고 모의환불을 진행할까요?');">

                                            <input type="hidden" name="returnId"
                                                   value="<%= item.get("returnId") %>">

                                            <input type="hidden" name="csrfToken"
                                                   value="<%= ProductView.escape(
                                                       request.getAttribute("adminReturnCsrf")) %>">

                                            <button class="button primary"
                                                    type="submit">
                                                승인 및 환불
                                            </button>
                                        </form>

                                        <form method="post"
                                              action="${pageContext.request.contextPath}/app/admin/returns/reject"
                                              style="margin-top:12px;">

                                            <input type="hidden" name="returnId"
                                                   value="<%= item.get("returnId") %>">

                                            <input type="hidden" name="csrfToken"
                                                   value="<%= ProductView.escape(
                                                       request.getAttribute("adminReturnCsrf")) %>">

                                            <label class="field">
                                                <span>반려 사유</span>
                                                <input type="text"
                                                       name="adminNote"
                                                       maxlength="500"
                                                       required>
                                            </label>

                                            <button class="button secondary"
                                                    type="submit">
                                                반려
                                            </button>
                                        </form>

                                    <% } else { %>
                                        —
                                    <% } %>
                                </td>
                            </tr>
                        <% }
                        } %>
                    </tbody>
                </table>
            </div>

        <% } %>
    </main>
</div>

</body>
</html>