(() => {
    "use strict";

    const app = window.Daiso;
    app.features = { ...app.features, orders: true };
    const { link, button, money, empty } = app.ui;
    const labels = { PAID: "결제 완료", PAYMENT_FAILED: "결제 실패", PREPARING: "배송 준비", SHIPPED: "배송 중", DELIVERED: "배송 완료", CANCELLED: "주문 취소" };
    app.ui.orderStatus = (status) => labels[status] || status;
    const root = document.querySelector("[data-page]");

    function totalsPanel(totals) {
        const panel = app.ui.view("order-totals");
        ["subtotal", "shipping", "total"].forEach(key => {
            panel.querySelector('[data-total="' + key + '"]').textContent = money(totals[key]);
        });
        return panel;
    }

    function itemList(items) {
        const list = app.ui.view("orders-order-items");
        items.forEach((item) => {
            const row = app.ui.view("order-item");
            const product = row.querySelector("a");
            product.textContent = item.name;
            product.href = app.url("product.jsp", { id: item.productId });
            row.querySelector("span").textContent = item.quantity + "개 · " + money(item.lineTotal);
            list.append(row);
        });
        return list;
    }

    function renderCart(repository) {
        root.replaceChildren(app.ui.view("orders-page-title"));
        const items = repository.getCart();
        if (!items.length) {
            empty(root, "장바구니가 비어 있습니다.");
            root.append(link("상품 둘러보기", "list.jsp", {}, "button primary"));
            return;
        }
        const layout = app.ui.view("orders-checkout-layout");
        const list = app.ui.view("orders-cart-list");
        const error = app.ui.view("orders-error-message");
        items.forEach((line) => {
            const row = app.ui.view("orders-cart-row");
            row.dataset.productId = line.productId;
            if (line.product) {
                const image = app.ui.view("orders-img");
                image.src = app.url(line.product.image);
                image.alt = "";
                const info = app.ui.view("orders-cart-product-info");
                info.append(link(line.product.name, "product.jsp", { id: line.productId }), app.ui.view("orders-p", money(line.product.price)));
                if (!line.purchasable) {
                    info.append(app.ui.view("orders-error-message-2"));
                }
                row.append(image, info);
            } else {
                row.append(app.ui.view("orders-p-2"));
            }
            const quantityLabel = app.ui.view("orders-field");
            const quantity = app.ui.part(quantityLabel, "orders-input");
            quantity.value = line.quantity;
            quantity.addEventListener("change", () => {
                try {
                    repository.setCartQuantity(line.productId, Number(quantity.value));
                    renderCart(repository);
                } catch (cause) {
                    error.textContent = cause.message;
                    quantity.value = line.quantity;
                }
            });
            row.append(quantityLabel, app.ui.view("orders-strong-2", money(line.lineTotal)), button("삭제", () => {
                repository.removeCart(line.productId);
                renderCart(repository);
            }, "text-button"));
            list.append(row);
        });
        list.append(error);
        const totals = totalsPanel(repository.cartTotals());
        if (items.every((line) => line.purchasable)) {
            totals.append(link("주문하기", "checkout.jsp", {}, "button primary"));
        } else {
            totals.append(app.ui.view("orders-error-message-3"));
        }
        layout.append(list, totals);
        root.append(layout);
    }

    function renderCheckout(repository) {
        root.append(app.ui.view("orders-page-title-2"));
        const items = repository.getCart();
        if (!items.length) {
            empty(root, "주문할 상품이 없습니다.");
            root.append(link("장바구니로", "cart.jsp", {}, "button"));
            return;
        }
        root.append(app.ui.view("orders-source-note"));
        const layout = app.ui.view("orders-checkout-layout-2");
        const form = app.ui.view("checkout-form");
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
        const totals = totalsPanel(repository.cartTotals());
        totals.prepend(itemList(items.map((line) => ({ ...line.product, productId: line.productId, quantity: line.quantity, lineTotal: line.lineTotal }))));
        layout.append(form, totals);
        root.append(layout);
    }

    function renderOrders(repository) {
        root.append(app.ui.view("orders-page-title-3"));
        if (!repository.getSession()) {
            root.append(app.ui.view("orders-source-note-2"));
        }
        const orders = repository.getOrders();
        if (!orders.length) {
            empty(root, "주문 내역이 없습니다.");
        }
        orders.forEach((order) => {
            const row = app.ui.view("orders-order-summary");
            row.append(app.ui.view("orders-p-3", new Date(order.createdAt).toLocaleString("ko-KR")), app.ui.view("orders-h2-3", labels[order.status]), itemList(order.items), app.ui.view("orders-strong-3", money(order.totals.total)), link("주문 상세", "order.jsp", { id: order.id }, "button secondary"));
            root.append(row);
        });
    }

    function renderOrder(repository, page) {
        const order = repository.getOrder(new URLSearchParams(location.search).get("id"));
        if (!order) {
            empty(root, "현재 회원의 주문을 찾을 수 없습니다.");
            root.append(link("주문 내역", "orders.jsp", {}, "button"));
            return;
        }
        const failed = order.status === "PAYMENT_FAILED";
        const title = page === "order" ? "주문 상세" : failed ? "모의 결제에 실패했습니다" : "주문이 완료되었습니다";
        root.append(app.ui.view("orders-page-title-4", title), app.ui.view("orders-order-status", labels[order.status]), app.ui.view("orders-order-number", order.id));
        root.append(app.ui.view("orders-source-note-3", failed ? "결제 실패로 장바구니와 Mock 재고를 유지했습니다. 다시 주문할 수 있습니다." : "실제 결제나 배송이 발생하지 않는 체험 주문입니다."));
        const layout = app.ui.view("orders-checkout-layout-3");
        const detail = app.ui.view("orders-order-detail");
        detail.append(itemList(order.items), app.ui.view("orders-h2-4"), app.ui.view("orders-p-4", order.recipient + " · " + order.phone), app.ui.view("orders-p-5", "(" + order.postcode + ") " + order.address), app.ui.view("orders-p-6", order.memo));
        layout.append(detail, totalsPanel(order.totals));
        root.append(layout, link(failed ? "장바구니에서 다시 시도" : "주문 내역 보기", failed ? "cart.jsp" : "orders.jsp", {}, "button primary"));
    }

    app.ready.then((repository) => {
        document.querySelectorAll('[data-shopping-route="cart"]').forEach((control) => {
            control.removeAttribute("data-unavailable");
            control.addEventListener("click", () => {
                location.href = app.url("cart.jsp");
            });
        });
        document.querySelectorAll('[data-shopping-route="orders"]').forEach((control) => {
            control.removeAttribute("data-unavailable");
            control.addEventListener("click", () => {
                location.href = app.url("orders.jsp");
            });
        });
        if (!root || !["cart", "checkout", "payment-success", "payment-failure", "orders", "order"].includes(root.dataset.page)) {
            return;
        }
        root.replaceChildren();
        const page = root.dataset.page;
        if (page === "cart") {
            renderCart(repository);
        } else if (page === "checkout") {
            renderCheckout(repository);
        } else if (page === "orders") {
            renderOrders(repository);
        } else {
            renderOrder(repository, page);
        }
    }).catch((error) => {
        if (root) {
            app.ui.error(root, error);
        }
    });
})();
