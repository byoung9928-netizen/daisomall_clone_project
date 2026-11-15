(() => {
    "use strict";

    const app = window.Daiso;
    app.features = { ...app.features, catalog: true };
    const { link, productCard, money, empty, notice } = app.ui;
    document.addEventListener("category:select", (event) => {
        if (event.detail.isLeaf || event.detail.navigate) {
            location.href = app.url("list.jsp", { category: event.detail.id });
        }
    });
    document.querySelectorAll("[data-navigation-key]").forEach((item) => {
        const key = item.dataset.navigationKey;
        const choices = { new: "newest", ranking: "recommended", rating: "rating", exhibitions: "recommended", "open-run": "newest", season: "newest", "selling-out": "recommended", beauty: "recommended", health: "recommended" };
        if (choices[key]) {
            item.removeAttribute("data-unavailable");
            item.addEventListener("click", () => {
                location.href = app.url("list.jsp", { sort: choices[key], category: key === "beauty" ? "CTGR_01050" : undefined, collection: key });
            });
        }
    });

    function pathToCategory(categories, id) {
        const path = [];
        let category = categories.find((item) => item.id === id);
        while (category) {
            path.unshift(category);
            category = categories.find((item) => item.id === category.parentId);
        }
        return path;
    }

    function breadcrumbs(categories, categoryId) {
        const nav = app.ui.view("catalog-breadcrumbs");
        nav.append(link("홈", ""));
        pathToCategory(categories, categoryId).forEach((category) => nav.append(app.ui.view("catalog-span"), link(category.name, "list.jsp", { category: category.id })));
        return nav;
    }

    function renderCatalog(root, repository) {
        const params = new URLSearchParams(location.search);
        const categoryId = params.get("category");
        const query = params.get("q") || "";
        const categories = repository.getVisibleCategories();
        const category = categories.find((item) => item.id === categoryId);
        if (categoryId && !category) {
            empty(root, "존재하지 않는 카테고리입니다.");
            root.append(link("전체 상품 보기", "list.jsp", {}, "button"));
            return;
        }
        root.append(breadcrumbs(categories, categoryId), app.ui.view("catalog-page-title", query ? "‘" + query + "’ 검색 결과" : category ? category.name : "전체 상품"));
        const chips = app.ui.view("catalog-category-chips");
        categories.filter((item) => item.parentId === (categoryId || null)).sort((left, right) => left.displayOrder - right.displayOrder).forEach((child) => chips.append(link(child.name, "list.jsp", { category: child.id }, "category-chip")));
        root.append(chips);
        const layout = app.ui.view("catalog-layout");
        const filter = layout.querySelector("form");
        const price = filter.elements.maxPrice;
        const stock = filter.elements.available;
        const sort = layout.querySelector('[name="sort"]');
        const count = layout.querySelector(".result-count");
        const grid = layout.querySelector(".catalog-grid");
        const more = layout.querySelector(".load-more");
        price.value = params.get("maxPrice") || "";
        stock.checked = params.get("available") === "true";
        sort.value = params.get("sort") || "recommended";
        if (!sort.value) sort.value = "recommended";
        more.addEventListener("click", () => {
            visible += 30;
            update(false);
        });
        let visible = 30;
        function update(reset = true) {
            if (reset) {
                visible = 30;
            }
            const products = repository.queryProducts({ categoryId, query, sort: sort.value, maxPrice: price.value || Infinity, availableOnly: stock.checked });
            count.textContent = "전체 " + products.length + "개";
            grid.replaceChildren();
            products.slice(0, visible).forEach((product) => grid.append(productCard(product, { details: true })));
            if (!products.length) {
                empty(grid, "조건에 맞는 상품이 없습니다. 필터나 검색어를 바꿔 보세요.");
            }
            more.hidden = visible >= products.length;
            const next = new URL(location.href);
            [["sort", sort.value], ["maxPrice", price.value], ["available", stock.checked ? "true" : ""]].forEach(([key, value]) => {
                if (value) {
                    next.searchParams.set(key, value);
                } else {
                    next.searchParams.delete(key);
                }
            });
            history.replaceState(null, "", next);
        }
        [price, stock, sort].forEach((control) => control.addEventListener("change", () => update()));
        filter.addEventListener("submit", (event) => event.preventDefault());
        filter.querySelector("[data-reset-filter]").addEventListener("click", () => {
            price.value = "";
            stock.checked = false;
            sort.value = "recommended";
            update();
        });
        root.append(layout);
        update();
    }

    function renderProduct(root, repository) {
        const product = repository.getProduct(new URLSearchParams(location.search).get("id"));
        if (!product) {
            empty(root, "상품을 찾을 수 없습니다.");
            root.append(link("상품 목록으로", "list.jsp", {}, "button"));
            return;
        }
        root.append(breadcrumbs(repository.getCategories(), product.categoryId));
        const layout = app.ui.view("product-detail");
        const image = layout.querySelector(".detail-image");
        image.src = app.url(product.image);
        image.alt = product.name + " 자체 제작 Mock 이미지";
        const info = layout.querySelector(".detail-info");
        const status = product.status === "SCHEDULED" ? "판매 예정" : product.status === "SOLD_OUT" || !product.onlineStock ? "품절" : "구매 가능";
        const values = { brand: product.brand, name: product.name, price: money(product.price), rating: "★ " + product.rating + " · 리뷰 " + product.reviewCount + " (Mock)", source: product.source.note, id: "품번 " + product.id, stock: status + " · 온라인 Mock 재고 " + product.onlineStock + "개" };
        Object.entries(values).forEach(([key, value]) => { info.querySelector('[data-product="' + key + '"]').textContent = value; });
        const stores = info.querySelector(".store-stock");
        repository.getStores().forEach(store => stores.append(app.ui.view("catalog-p-4", store.name + " · " + (product.storeStocks[store.id] || 0) + "개 (Mock)")));
        const quantity = info.querySelector('[name="quantity"]');
        quantity.max = String(Math.min(99, product.onlineStock));
        const total = info.querySelector(".detail-total");
        total.textContent = "합계 " + money(product.price);
        quantity.addEventListener("input", () => {
            total.textContent = "합계 " + money(product.price * Math.max(0, Number(quantity.value)));
        });
        const add = info.querySelector("[data-add-cart]");
        add.addEventListener("click", () => {
            if (!quantity.checkValidity()) {
                quantity.reportValidity();
                return;
            }
            if (app.repository.addCart) {
                try {
                    app.repository.addCart(product.id, Number(quantity.value));
                    notice("장바구니에 담았습니다.");
                } catch (error) {
                    notice(error.message);
                }
            } else {
                notice("장바구니 기능은 다음 단계에서 연결됩니다.");
            }
        });
        add.disabled = status !== "구매 가능";
        root.append(layout);
        const description = app.ui.view("product-description");
        description.querySelector("[data-product-description]").textContent = product.description;
        root.append(description);
    }

    const root = document.querySelector('[data-page="catalog"], [data-page="product"]');
    if (root) {
        app.ready.then((repository) => {
            root.replaceChildren();
            if (root.dataset.page === "catalog") {
                renderCatalog(root, repository);
            } else {
                renderProduct(root, repository);
            }
        }).catch((error) => app.ui.error(root, error));
    }
})();
