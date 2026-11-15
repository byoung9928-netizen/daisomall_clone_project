(() => {
    "use strict";
    const { element, link, money } = window.Daiso.ui;
    // 주문서·주문 내역·주문 상세에서 반복되는 상품 목록만 만듭니다.
    function itemList(items) {
        const list = element("ul", "order-items");
        items.forEach(item => {
            const row = element("li");
            row.append(link(item.name, "product.jsp", { id: item.productId }), element("span", "", item.quantity + "개 · " + money(item.lineTotal)));
            list.append(row);
        });
        return list;
    }
    function updateTotals(panel, totals) {
        ["subtotal", "shipping", "total"].forEach(key => {
            panel.querySelector('[data-total="' + key + '"]').textContent = money(totals[key]);
        });
    }
    window.Daiso.ui.itemList = itemList;
    window.Daiso.ui.updateTotals = updateTotals;
})();
