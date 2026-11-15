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
        <main id="main-content" class="container shop-page" data-page="cart" tabindex="-1">
        <h1 class="page-title">
            장바구니
        </h1>
        <p class="empty-state" data-empty-cart hidden>
            장바구니가 비어 있습니다.
        </p>
        <a class="button primary" data-browse hidden href="${pageContext.request.contextPath}/list.jsp">
            상품 둘러보기
        </a>
        <div class="checkout-layout">
            <div class="cart-list">
            </div>
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
        const { element, link, button, money, empty } = app.ui;
        app.ready.then(function (repository) {
            const layout = root.querySelector(".checkout-layout");
            const list = root.querySelector(".cart-list");
            const summary = root.querySelector(".totals-panel");
            function renderCart() {
                const items = repository.getCart();
                list.replaceChildren();
                summary.querySelectorAll("a, .error-message").forEach(node => node.remove());
                layout.hidden = !items.length;
                root.querySelector("[data-empty-cart]").hidden = !!items.length;
                root.querySelector("[data-browse]").hidden = !!items.length;
                if (!items.length) return;
                const error = element("p", "error-message"); error.setAttribute("role", "alert");
                // 장바구니 상품 수와 값은 브라우저 Mock 데이터에 따라 달라집니다.
                items.forEach(line => {
                    const row = element("article", "cart-row"); row.dataset.productId = line.productId;
                    if (line.product) {
                        const image = element("img"); image.src = app.url(line.product.image); image.alt = "";
                        const info = element("div", "cart-product-info");
                        info.append(link(line.product.name, "product.jsp", { id: line.productId }), element("p", "", money(line.product.price)));
                        if (!line.purchasable) info.append(element("p", "error-message", "판매 상태 또는 재고를 확인해 주세요."));
                        row.append(image, info);
                    } else row.append(element("p", "", "삭제되었거나 존재하지 않는 상품"));
                    const label = element("label", "field", "수량");
                    const quantity = element("input"); quantity.type = "number"; quantity.name = "quantity"; quantity.min = "1"; quantity.max = "99"; quantity.value = line.quantity;
                    quantity.addEventListener("change", () => {
                        try { repository.setCartQuantity(line.productId, Number(quantity.value)); renderCart(); }
                        catch (cause) { error.textContent = cause.message; quantity.value = line.quantity; }
                    });
                    label.append(quantity);
                    row.append(label, element("strong", "", money(line.lineTotal)), button("삭제", () => { repository.removeCart(line.productId); renderCart(); }, "text-button"));
                    list.append(row);
                });
                list.append(error);
                app.ui.updateTotals(summary, repository.cartTotals());
                if (items.every(line => line.purchasable)) summary.append(link("주문하기", "checkout.jsp", {}, "button primary"));
                else summary.append(element("p", "error-message", "모든 상품의 상태와 재고를 확인한 후 주문할 수 있습니다."));
            }
            renderCart();
        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
