<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="head.jspf" %>
</head>
<body class="admin-body store-manager-body">
    <%@ include file="layout.jspf" %>
    <main class="admin-content" id="store-manager-content" tabindex="-1">
        <h1 class="page-title"><%= ProductView.escape(request.getAttribute("storeManagerTitle")) %></h1>
        <section class="admin-panel" aria-labelledby="store-manager-planned">
            <h2 id="store-manager-planned">개발 예정</h2>
            <p>현재 이용할 수 있는 기능은 재고 관리, 재고 변경 이력, 픽업 주문 관리입니다.</p>
            <a class="button secondary" href="<%= storeManagerBase %>dashboard">대시보드로 이동</a>
        </section>
    </main>
    </div>
</body>
</html>
