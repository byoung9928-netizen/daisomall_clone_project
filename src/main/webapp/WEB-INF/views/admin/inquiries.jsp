<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductView" %>
<%
List<Map<String, Object>> inquiries =
        (List<Map<String, Object>>) request.getAttribute("adminInquiries");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <style>
        .admin-inquiry { margin: 12px 0; }
        .admin-inquiry summary { cursor: pointer; }
        .admin-inquiry-text { white-space: pre-wrap; overflow-wrap: anywhere; }
        .admin-inquiry form { display: grid; gap: 8px; }
        .admin-inquiry textarea { width: 100%; padding: 8px; font: inherit; }
        .admin-inquiry form button { justify-self: start; }
    </style>
</head>
<body class="admin-body product-admin-ux">
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>

        <main id="admin-content" class="admin-content">
            <h1 class="page-title">1:1 문의 관리</h1>

            <% if ("1".equals(request.getParameter("answered"))) { %>
                <p role="status">답변을 저장했습니다.</p>
            <% } %>

            <% if (request.getAttribute("adminInquiryError") != null) { %>
                <p role="alert"><%= ProductView.escape(request.getAttribute("adminInquiryError")) %></p>
            <% } else if (inquiries == null || inquiries.isEmpty()) { %>
                <p>접수된 문의가 없습니다.</p>
            <% } else {
                for (Map<String, Object> inquiry : inquiries) {
                    long inquiryId = (Long) inquiry.get("inquiryId");
                    boolean pending = "PENDING".equals(inquiry.get("status"));
            %>
                <details class="admin-panel admin-inquiry">
                    <summary>
                        <%= ProductView.escape(inquiry.get("title")) %>
                        · <%= ProductView.escape(inquiry.get("loginId")) %>
                        · <%= pending ? "답변 대기" : "답변 완료" %>
                    </summary>

                    <p><%= ProductView.escape(inquiry.get("createdAt")) %></p>
				<%
				if (inquiry.get("channel") != null) {
				%>
				<p>
					<strong>문의 분류</strong>
					<%=ProductView.escape(inquiry.get("channel"))%>
					›
					<%=ProductView.escape(inquiry.get("category"))%>
					›
					<%=ProductView.escape(inquiry.get("subcategory"))%>
				</p>
				<%
				}
				%>

				<%
				if (inquiry.get("storeName") != null) {
				%>
				<p>
					<strong>매장</strong>
					<%=ProductView.escape(inquiry.get("storeName"))%></p>
				<%
				}
				%>

				<%
				if (inquiry.get("productName") != null) {
				%>
				<p>
					<strong>상품</strong>
					<%=ProductView.escape(inquiry.get("productName"))%></p>
				<% } %>
				<p class="admin-inquiry-text"><%= ProductView.escape(inquiry.get("content")) %></p>
				<%
				if (Boolean.TRUE.equals(inquiry.get("hasPhoto"))) {
				%>
				<p>
					<a
						href="${pageContext.request.contextPath}/app/support/inquiries/photo?id=<%= inquiryId %>"
						target="_blank" rel="noopener">첨부 사진 보기</a>
				</p>
				<%
				}
				%>

				<% if (pending) { %>
                        <form method="post"
                              action="${pageContext.request.contextPath}/app/admin/inquiries/answer">
                            <input type="hidden" name="csrfToken"
                                   value="<%= ProductView.escape(request.getAttribute("adminInquiryCsrf")) %>">
                            <input type="hidden" name="inquiryId" value="<%= inquiryId %>">
                            <label for="answer-<%= inquiryId %>">답변</label>
                            <textarea id="answer-<%= inquiryId %>" name="answer"
                                      rows="4" maxlength="2000" required></textarea>
                            <button type="submit" class="button primary">답변 저장</button>
                        </form>
                    <% } else { %>
                        <h2>등록한 답변</h2>
                        <p class="admin-inquiry-text"><%= ProductView.escape(inquiry.get("answer")) %></p>
                    <% } %>
                </details>
            <%  }
            } %>
        </main>
    </div>
</body>
</html>