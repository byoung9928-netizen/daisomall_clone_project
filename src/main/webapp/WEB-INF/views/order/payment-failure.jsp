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
        <main id="main-content" class="container shop-page" data-page="payment-failure" tabindex="-1">
        <h1 class="page-title">
            모의 결제에 실패했습니다
        </h1>
        <p class="order-status">
        </p>
        <p class="order-number">
        </p>
        <p class="source-note">
        </p>
        <div class="checkout-layout">
            <section class="order-detail">
                <h2>
                    배송 정보
                </h2>
                <p data-recipient>
                </p>
                <p data-address>
                </p>
                <p data-memo>
                </p>
            </section>
            <%@ include file="../common/order-totals.jspf" %>
        </div>
        <a class="button primary" data-order-next>
        </a>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { link, button, empty } = app.ui;
        app.ready.then(function (repository) {
            const order = repository.getOrder(new URLSearchParams(location.search).get("id"));
            if (!order) { root.replaceChildren(); empty(root, "현재 회원의 주문을 찾을 수 없습니다."); root.append(link("주문 내역", "orders.jsp", {}, "button")); return; }
            const failed = order.status === "PAYMENT_FAILED";
            root.querySelector("h1").textContent = failed ? "모의 결제에 실패했습니다" : "주문이 완료되었습니다";
            root.querySelector(".order-status").textContent = app.ui.orderStatus(order.status);
            root.querySelector(".order-number").textContent = order.id;
            root.querySelector(".source-note").textContent = failed ? "결제 실패로 장바구니와 Mock 재고를 유지했습니다. 다시 주문할 수 있습니다." : "실제 결제나 배송이 발생하지 않는 체험 주문입니다.";
            const detail = root.querySelector(".order-detail");
            detail.prepend(app.ui.itemList(order.items));
            detail.querySelector("[data-recipient]").textContent = order.recipient + " · " + order.phone;
            detail.querySelector("[data-address]").textContent = "(" + order.postcode + ") " + order.address;
            detail.querySelector("[data-memo]").textContent = order.memo;
            app.ui.updateTotals(root.querySelector(".totals-panel"), order.totals);
            const next = root.querySelector("[data-order-next]");
            next.textContent = failed ? "장바구니에서 다시 시도" : "주문 내역 보기";
            next.href = app.url(failed ? "cart.jsp" : "orders.jsp");
        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
