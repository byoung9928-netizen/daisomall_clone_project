(function () {
    "use strict";

    var app = window.Daiso;
    var filter = document.querySelector("#catalog-filter");
    if (filter) {
        ["sort", "maxPrice", "status", "available"].forEach(function (name) {
            var field = filter.elements.namedItem(name);
            if (field) {
                field.addEventListener("change", function () {
                    filter.requestSubmit();
                });
            }
        });
    }

    var catalog = document.querySelector(".catalog-products");
    if (catalog) {
        catalog.addEventListener("click", function (event) {
            var next = event.target.closest(".load-more[rel=next]");
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
                var documentPage = new DOMParser().parseFromString(page, "text/html");
                var nextGrid = documentPage.querySelector(".catalog-grid");
                var pagination = documentPage.querySelector("nav[aria-label='상품 목록 페이지']");
                if (!nextGrid || !pagination) {
                    throw new Error("상품 목록을 찾을 수 없습니다.");
                }
                var grid = catalog.querySelector(".catalog-grid");
                while (nextGrid.firstElementChild) {
                    grid.appendChild(nextGrid.firstElementChild);
                }
                next.closest("nav").replaceWith(pagination);
            }).catch(function () {
                location.assign(next.href);
            });
        });
    }

    var input = document.querySelector("#search-query");
    if (!input) {
        return;
    }
    var suggestions = document.createElement("datalist");
    suggestions.id = "product-suggestions";
    input.setAttribute("list", suggestions.id);
    input.after(suggestions);
    var request;
    var timer;

    input.addEventListener("input", function () {
        clearTimeout(timer);
        if (request) {
            request.abort();
        }
        suggestions.replaceChildren();

        var query = input.value.trim();
        if (query.length < 2) {
            return;
        }
        timer = setTimeout(function () {
            request = new AbortController();
            var signal = request.signal;

            fetch(app.url("api/products", { q: query, limit: 6 }), { signal: signal })
                .then(function (response) {
                    return response.ok ? response.json() : null;
                }).then(function (data) {
                    if (!data || signal.aborted) {
                        return;
                    }
                    var names = Object.create(null);
                    data.items.forEach(function (item) {
                        if (!names[item.name]) {
                            names[item.name] = true;
                            var option = document.createElement("option");
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
