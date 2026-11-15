(() => {
    "use strict";
    const app = window.Daiso;
    // 온라인 재고와 매장 재고 화면은 같은 검색·표·편집 동작을 사용합니다.
    app.admin.bindInventory = function (root, repository) {
        const section = root.dataset.adminPage;
        const { button } = app.ui;
        const { field, selectField, formFeedback, addRow, openEditor } = app.admin;
        const { form, input } = app.admin.searchBox(root);
        const stockTable = app.admin.table(root);
        const editor = root.querySelector(".admin-panel");
        const count = root.querySelector("[data-count]");
        let limit = 30;
        const more = root.querySelector("[data-more]");
        more.addEventListener("click", () => {
            limit += 30;
            render();
        });
        function edit(product) {
            const editForm = openEditor(editor, product.name + " 재고 수정");
            const locations = section === "inventory" ? [["online", "온라인"]] : repository.getStores().map((store) => [store.id, store.name]);
            const select = selectField(editForm, "재고 위치", "location", locations);
            const quantity = field(editForm, "재고 수량", "quantity", "number", section === "inventory" ? product.onlineStock : product.storeStocks[select.value]);
            quantity.min = 0;
            quantity.max = 999999;
            select.addEventListener("change", () => {
                const current = repository.getProduct(product.id);
                quantity.value = select.value === "online" ? current.onlineStock : current.storeStocks[select.value];
            });
            formFeedback(editForm, (values) => {
                repository.updateInventory(product.id, values.location, values.quantity);
                render();
            }, "재고 저장");
            editor.scrollIntoView({ block: "start" });
        }
        function render() {
            const query = input.value.trim().toLowerCase();
            const products = repository.getProducts().filter((product) => (product.id + " " + product.name).toLowerCase().includes(query));
            count.textContent = products.length + "개 상품";
            stockTable.body.replaceChildren();
            products.slice(0, limit).forEach((product) => addRow(stockTable.body, [product.id, product.name, product.onlineStock, product.storeStocks.seoul, product.storeStocks.busan, product.storeStocks.jeju, button("재고 수정", () => edit(product), "text-button")]));
            more.hidden = products.length <= limit;
        }
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            limit = 30;
            render();
        });
        render();
    };
})();
