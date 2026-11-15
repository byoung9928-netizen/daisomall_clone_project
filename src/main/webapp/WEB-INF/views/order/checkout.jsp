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
        <main id="main-content" class="container shop-page" data-page="checkout" tabindex="-1">
        <h1 class="page-title">
            주문서
        </h1>
        <p class="source-note">
            실제 결제가 발생하지 않는 모의 주문입니다. 테스트용 배송 정보를 입력해 주세요.
        </p>
        <div class="checkout-layout">
            <form id="checkoutForm" class="checkout-form" method="post">
                <h2>
                    배송 정보
                </h2>
                <label class="field">
                    받는 분
                    <input type="text" name="recipient" required maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    연락처
                    <input type="tel" name="phone" required maxlength="120" autocomplete="tel">
                </label>
                <label class="field">
                    우편번호 (5자리)
                    <input type="text" name="postcode" required pattern="[0-9]{5}" maxlength="5" autocomplete="off">
                </label>
                <label class="field">
                    주소
                    <input type="text" name="address" required maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    배송 요청사항 (선택)
                    <input type="text" name="memo" maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    모의 결제 결과 선택
                    <select name="outcome">
                        <option value="success">
                            성공
                        </option>
                        <option value="failure">
                            실패
                        </option>
                    </select>
                </label>
                <p class="error-message" role="alert">
                </p>
                <button type="submit" class="button primary">
                    모의 결제하기
                </button>
            </form>
            <%@ include file="../common/order-totals.jspf" %>
        </div>
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


        const items = repository.getCart();
        if (!items.length) {
            root.querySelector(".source-note").remove();
            root.querySelector(".checkout-layout").remove();
            empty(root, "주문할 상품이 없습니다.");
            root.append(link("장바구니로", "cart.jsp", {}, "button"));
            return;
        }

        const layout = root.querySelector(".checkout-layout");
        const form = root.querySelector(".checkout-form");
        const member = repository.getSession();
        form.elements.recipient.value = member ? member.name : "";
        form.elements.phone.value = member ? member.phone : "";
        const error = form.querySelector(".error-message");
        const submit = form.querySelector('[type="submit"]');
        const token = crypto.randomUUID();
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            submit.disabled = true;
            try {
                const order = repository.placeOrder({ ...Object.fromEntries(new FormData(form)), token });
                location.href = app.url(order.status === "PAYMENT_FAILED" ? "payment-failure.jsp" : "payment-success.jsp", { id: order.id });
            } catch (cause) {
                error.textContent = cause.message;
                submit.disabled = false;
            }
        });
        const totals = root.querySelector(".totals-panel");
        app.ui.updateTotals(totals, repository.cartTotals());
        totals.prepend(app.ui.itemList(items.map((line) => ({ ...line.product, productId: line.productId, quantity: line.quantity, lineTotal: line.lineTotal }))));


        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
