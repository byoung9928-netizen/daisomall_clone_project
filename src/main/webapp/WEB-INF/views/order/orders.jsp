<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>

</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
        <main id="main-content" class="container shop-page" data-page="orders" tabindex="-1">
        <h1 class="page-title">
            주문 내역
        </h1>
        <p class="source-note" hidden>
            현재 브라우저의 비회원 체험 주문만 표시합니다.
        </p>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { element, link, button, money, empty } = app.ui;
        app.ready.then(function (repository) {


        if (!repository.getSession()) {
            root.querySelector(".source-note").hidden = false;
        }
        const orders = repository.getOrders();
        if (!orders.length) {
            empty(root, "주문 내역이 없습니다.");
        }
        orders.forEach((order) => {
            const row = element("article", "order-summary");
            row.append(element("p", "", new Date(order.createdAt).toLocaleString("ko-KR")), element("h2", "", app.ui.orderStatus(order.status)), app.ui.itemList(order.items), element("strong", "", money(order.totals.total)), link("주문 상세", "order.jsp", { id: order.id }, "button secondary"));
            root.append(row);
        });

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
