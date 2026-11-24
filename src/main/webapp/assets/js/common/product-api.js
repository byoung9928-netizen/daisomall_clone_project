(function () {
    "use strict";

    const filter = document.querySelector("#catalog-filter");
    if (filter && !filter.hasAttribute("data-browse-filters")) {
        ["sort", "maxPrice", "status", "available"].forEach(function (name) {
            const field = filter.elements.namedItem(name);
            if (field) {
                field.addEventListener("change", function () {
                    filter.requestSubmit();
                });
            }
        });
    }

    const catalog = document.querySelector(".catalog-products");
    if (catalog) {
        catalog.addEventListener("click", function (event) {
            const next = event.target.closest(".load-more[rel=next]");
            if (!next || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
                return;
            }
            event.preventDefault();
            if (next.getAttribute("aria-busy") === "true") {
                return;
            }
            next.setAttribute("aria-busy", "true");

            fetch(next.href).then(function (response) {
                if (!response.ok) {
                    throw new Error("상품 목록을 불러오지 못했습니다.");
                }
                return response.text();
            }).then(function (page) {
                const documentPage = new DOMParser().parseFromString(page, "text/html");
                const nextGrid = documentPage.querySelector(".catalog-grid");
                const pagination = documentPage.querySelector("nav[aria-label='상품 목록 페이지']");
                if (!nextGrid || !pagination) {
                    throw new Error("상품 목록을 찾을 수 없습니다.");
                }
                const grid = catalog.querySelector(".catalog-grid");
                while (nextGrid.firstElementChild) {
                    grid.appendChild(nextGrid.firstElementChild);
                }
                const pickupNotice = catalog.querySelector("[data-card-pickup-error]");
                if (pickupNotice && documentPage.querySelector("[data-card-pickup-error]:not([hidden])")) {
                    pickupNotice.hidden = false;
                }
                next.closest("nav").replaceWith(pagination);
            }).catch(function () {
                location.assign(next.href);
            });
        });
    }
})();
