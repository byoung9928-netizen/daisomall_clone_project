(function () {
    "use strict";

    const app = window.Daiso;
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

    const input = document.querySelector("#search-query");
    if (!input) {
        return;
    }
    const suggestions = document.createElement("datalist");
    suggestions.id = "product-suggestions";
    input.setAttribute("list", suggestions.id);
    input.after(suggestions);
    let request;
    let timer;

    input.addEventListener("input", function () {
        clearTimeout(timer);
        if (request) {
            request.abort();
        }
        suggestions.replaceChildren();

        const query = input.value.trim();
        if (query.length < 2) {
            return;
        }
        timer = setTimeout(function () {
            request = new AbortController();
            const signal = request.signal;

            fetch(app.url("api/products", { q: query, limit: 6 }), { signal: signal })
                .then(function (response) {
                    return response.ok ? response.json() : null;
                }).then(function (data) {
                    if (!data || signal.aborted) {
                        return;
                    }
                    const names = Object.create(null);
                    data.items.forEach(function (item) {
                        if (!names[item.name]) {
                            names[item.name] = true;
                            const option = document.createElement("option");
                            option.value = item.name;
                            suggestions.appendChild(option);
                        }
                    });
                }).catch(function () {
                    // A suggestion request never prevents submitting the search form.
                });
        }, 200);
    });
})();
