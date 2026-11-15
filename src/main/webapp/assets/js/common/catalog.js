(() => {
    "use strict";
    const app = window.Daiso;
    const { element, link, empty, productCard } = app.ui;
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
        const nav = element("nav", "breadcrumbs");
        nav.setAttribute("aria-label", "현재 위치");
        nav.append(link("홈", ""));
        pathToCategory(categories, categoryId).forEach(category => nav.append(element("span", "", "›"), link(category.name, "list.jsp", { category: category.id })));
        return nav;
    }
    // 목록과 검색 화면에서 같은 필터·정렬·더 보기 동작을 재사용합니다.
    function bindCatalog(root, repository) {
        const params = new URLSearchParams(location.search);
        const categoryId = params.get("category");
        const query = params.get("q") || "";
        const categories = repository.getVisibleCategories();
        const category = categories.find((item) => item.id === categoryId);
        if (categoryId && !category) {
            root.replaceChildren();
            empty(root, "존재하지 않는 카테고리입니다.");
            root.append(link("전체 상품 보기", "list.jsp", {}, "button"));
            return;
        }
        root.querySelector(".breadcrumbs").replaceWith(breadcrumbs(categories, categoryId));
        root.querySelector("h1").textContent = query ? "‘" + query + "’ 검색 결과" : category ? category.name : "전체 상품";
        const chips = root.querySelector(".category-chips");
        categories.filter((item) => item.parentId === (categoryId || null)).sort((left, right) => left.displayOrder - right.displayOrder).forEach((child) => chips.append(link(child.name, "list.jsp", { category: child.id }, "category-chip")));
        const layout = root.querySelector(".catalog-layout");
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
        update();
     }
    app.ui.breadcrumbs = breadcrumbs;
    app.ui.bindCatalog = bindCatalog;
})();
