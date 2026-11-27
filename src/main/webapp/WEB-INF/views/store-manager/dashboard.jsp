<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.OffsetDateTime,java.time.format.DateTimeFormatter" %>
<%
    OffsetDateTime checkedAt = (OffsetDateTime) request.getAttribute("storeManagerCheckedAt");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="head.jspf" %>
</head>
<body class="admin-body store-manager-body">
    <%@ include file="layout.jspf" %>
    <main class="admin-content" id="store-manager-content" tabindex="-1">
        <h1 class="page-title">매장 대시보드</h1>
        <section class="admin-panel" aria-labelledby="store-manager-overview">
            <div class="store-manager-overview">
                <div>
                    <h2 id="store-manager-overview"><%= ProductView.escape(request.getAttribute("storeManagerName")) %></h2>
                    <p>조회 시각 <time datetime="<%= checkedAt %>"><%= checkedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) %></time> (한국 시간)</p>
                </div>
                <a class="button secondary" href="<%= storeManagerBase %>dashboard">새로고침</a>
            </div>
            <p>재고 및 픽업 현황 요약은 준비 중입니다.</p>
            <div class="store-manager-quick-links">
                <a class="button secondary" href="<%= storeManagerBase %>inventory">재고 관리</a>
                <a class="button secondary" href="<%= storeManagerBase %>pickup-orders">픽업 주문 관리</a>
            </div>
        </section>
    </main>
    </div>
</body>
</html>
