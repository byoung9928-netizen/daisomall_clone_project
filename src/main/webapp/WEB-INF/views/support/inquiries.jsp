<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductView" %>
<%
List<Map<String, Object>> inquiries =
        (List<Map<String, Object>>) request.getAttribute("myInquiries");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <style>
        .inquiry-page { max-width: 760px; margin: 32px auto; }
        .inquiry-page section { margin: 24px 0; }
        .inquiry-page form { display: grid; gap: 10px; }
        .inquiry-page input, .inquiry-page textarea {
            width: 100%;
            padding: 10px;
            border: 1px solid #ccd1d8;
            font: inherit;
        }
        .inquiry-page form button { justify-self: start; padding: 8px 16px; }
        .inquiry-page details { padding: 12px 0; border-bottom: 1px solid #e0e5eb; }
        .inquiry-page summary { cursor: pointer; }
        .inquiry-text { white-space: pre-wrap; overflow-wrap: anywhere; }
    </style>
</head>
<body>
<%@ include file="../common/header.jspf" %>

<main class="container inquiry-page">
    <p><a href="${pageContext.request.contextPath}/app/order/mypage">마이페이지</a></p>
    <% boolean historyView = Boolean.TRUE.equals(request.getAttribute("inquiryHistoryView")); %>
<h1><%= historyView ? "1:1 문의내역" : "1:1 문의" %></h1>
<% if (!historyView) { %>

    <section id="inquiry-form" aria-labelledby="inquiry-form-title">
        <h2 id="inquiry-form-title">문의 작성</h2>
        <% if (request.getAttribute("inquiryError") != null) { %>
            <p role="alert"><%= ProductView.escape(request.getAttribute("inquiryError")) %></p>
        <% } %>

        <form method="post" action="${pageContext.request.contextPath}/app/support/inquiries">
            <input type="hidden" name="csrfToken"
                   value="<%= ProductView.escape(request.getAttribute("supportInquiryCsrf")) %>">

            <label for="inquiry-title">제목</label>
            <input id="inquiry-title" name="title" maxlength="120" required
                   value="<%= ProductView.escape(request.getAttribute("draftTitle")) %>">

            <label for="inquiry-content">내용</label>
            <textarea id="inquiry-content" name="content" rows="6"
                      maxlength="2000" required><%= ProductView.escape(request.getAttribute("draftContent")) %></textarea>

            <button type="submit">문의 등록</button>
        </form>
    </section>
    
    <% } else { %>

    <section id="inquiry-history" aria-labelledby="inquiry-history-title">
        <h2 id="inquiry-history-title">내 문의내역</h2>
        <% if ("1".equals(request.getParameter("created"))) { %>
            <p role="status">문의가 접수됐어요.</p>
        <% } %>

        <% if (request.getAttribute("inquiryLoadError") != null) { %>
            <p role="alert"><%= ProductView.escape(request.getAttribute("inquiryLoadError")) %></p>
        <% } else if (inquiries == null || inquiries.isEmpty()) { %>
            <p>등록한 문의가 없어요.</p>
        <% } else {
            for (Map<String, Object> inquiry : inquiries) { %>
                <details>
                    <summary>
                        <%= ProductView.escape(inquiry.get("title")) %>
                        · <%= "ANSWERED".equals(inquiry.get("status")) ? "답변 완료" : "답변 대기" %>
                    </summary>
                    <p><%= ProductView.escape(inquiry.get("createdAt")) %></p>
                    <p class="inquiry-text"><%= ProductView.escape(inquiry.get("content")) %></p>
                    <% if ("ANSWERED".equals(inquiry.get("status"))) { %>
                        <h3>관리자 답변</h3>
                        <p class="inquiry-text"><%= ProductView.escape(inquiry.get("answer")) %></p>
                    <% } %>
                </details>
            <% }
        } %>
    </section>
    <% } %>
</main>
</body>
</html>