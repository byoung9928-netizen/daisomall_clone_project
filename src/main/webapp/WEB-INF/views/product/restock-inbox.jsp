<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.dao.ProductRestockDAO.Entry,daiso.product.controller.ProductView" %>
<% boolean partial = Boolean.TRUE.equals(request.getAttribute("restockPartial"));
   List<Entry> entries = (List<Entry>) request.getAttribute("restockEntries");
   String context = request.getContextPath(); %>
<% if (!partial) { %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>다이소몰 재입고 알림</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/my-product-activity.css">
</head>
<body><main class="product-activity-section product-restock" style="max-width:900px;margin:32px auto;padding:16px">
    <h1>다이소몰 재입고 알림</h1>
    <p><a href="<%= context %>/app/order/mypage?productActivity=restock">마이페이지 입고 알림</a></p>
    <% if (request.getAttribute("restockNotice") != null) { %>
        <p role="status"><%= ProductView.escape(request.getAttribute("restockNotice")) %></p>
    <% } %>
<% } %>
<div data-restock-notices>
    <% if (Boolean.TRUE.equals(request.getAttribute("restockLoadError"))) { %>
        <p role="alert">재입고 알림을 불러오지 못했습니다.</p>
    <% } else if (entries == null) { %>
        <div class="product-restock-empty"><p>아직 알림 신청을 받지 않습니다.</p></div>
    <% } else if (entries.isEmpty()) { %>
        <div class="product-restock-empty"><p>신청 내역이 없습니다.</p></div>
    <% } else { %>
        <p>최근 신청 50건까지 표시합니다.</p>
        <ul>
            <% for (Entry entry : entries) { %>
                <li>
                    <a href="<%= ProductView.url(context, "/app/products/detail", Map.of("id", entry.id)) %>"><%= ProductView.escape(entry.name) %></a>
                    <strong><%= entry.notified == null ? "알림 대기" : "재입고 알림" %></strong>
                    <% java.time.LocalDateTime date = entry.notified == null ? entry.requested : entry.notified; %>
                    <time datetime="<%= date %>"><%= date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) %></time>
                </li>
            <% } %>
        </ul>
        <p>알림을 받은 뒤 상품이 다시 품절될 수 있습니다.</p>
    <% } %>
</div>
<% if (!partial) { %></main></body></html><% } %>
