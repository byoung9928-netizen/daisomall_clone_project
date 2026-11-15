(() => {
    "use strict";

    const app = window.Daiso;
    function bind(node, text, className) {
        if (text !== undefined) node.textContent = text;
        if (className !== undefined) node.className = className;
        return node;
    }

    function view(name, text, className) {
        const template = document.getElementById("view-" + name);
        if (!(template instanceof HTMLTemplateElement)) {
            throw new Error("Missing JSP view template: " + name);
        }
        return bind(template.content.firstElementChild.cloneNode(true), text, className);
    }

    function part(parent, name, text, className) {
        const node = parent.querySelector('[data-view-part="' + name + '"]');
        if (!node) throw new Error("Missing JSP view part: " + name);
        return bind(node, text, className);
    }

    function link(label, file, params, className) {
        const node = app.ui.view("shop-ui-a", label, className);
        node.href = app.url(file, params);
        return node;
    }

    function button(label, action, className = "button") {
        const node = app.ui.view("shop-ui-button", label, className);
        node.addEventListener("click", action);
        return node;
    }

    let noticeTimer;
    function notice(message) {
        const output = document.querySelector("#common-feature-status");
        if (output) {
            output.textContent = message;
            clearTimeout(noticeTimer);
            noticeTimer = setTimeout(() => {
                output.textContent = "";
            }, 5000);
        }
    }

    function productCard(product, { details = false } = {}) {
        const card = view(details ? "product-card-linked" : "product-card");
        card.dataset.productId = product.id;
        const cover = card.querySelector(".product-cover");
        const image = cover.querySelector("img");
        if (details) cover.href = app.url("product.jsp", { id: product.id });
        image.src = app.url(product.image);
        image.alt = product.name + " (자체 제작 Mock 이미지)";
        const unavailable = product.status !== "AVAILABLE" || product.onlineStock < 1;
        if (unavailable) {
            cover.append(app.ui.view("shop-ui-product-status", product.status === "SCHEDULED" ? "판매 예정" : "품절"));
        }
        card.querySelector(".product-price").textContent = money(product.price);
        const name = card.querySelector(".product-name");
        name.textContent = product.name;
        if (details) name.href = app.url("product.jsp", { id: product.id });
        const sourceLabels = { REFERENCE: "관찰 표본", GENERATED: "생성 샘플", ADMIN_EDITED: "수정 샘플" };
        card.querySelector("[data-source-badge]").textContent = sourceLabels[product.source.kind] || "Mock 상품";
        card.querySelector(".product-rating").textContent = "★ " + product.rating.toFixed(1) + " (" + product.reviewCount + ") · Mock";
        return card;
    }

    function money(value) {
        return new Intl.NumberFormat("ko-KR").format(value) + "원";
    }

    function empty(root, message) {
        root.append(app.ui.view("shop-ui-empty-state", message));
    }

    function error(root, cause) {
        root.replaceChildren(app.ui.view("shop-ui-page-title"), app.ui.view("shop-ui-error-message", cause.message));
        root.append(button("다시 시도", () => location.reload()), button("Mock 데이터 초기화", () => {
            if (window.confirm("이 브라우저의 Mock 변경과 주문을 모두 초기화할까요?")) {
                if (app.repository) {
                    app.repository.reset();
                } else {
                    localStorage.removeItem(app.storageKey);
                    sessionStorage.removeItem("daisomall.mock.session");
                }
                location.reload();
            }
        }, "button secondary"));
    }

    app.ui = { view, part, link, button, notice, productCard, money, empty, error };
})();
