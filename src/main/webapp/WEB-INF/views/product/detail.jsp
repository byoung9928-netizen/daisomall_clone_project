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
        <main id="main-content" class="container shop-page" data-page="product" tabindex="-1">
        <nav class="breadcrumbs" aria-label="현재 위치">
        </nav>
        <div class="product-detail-layout">
            <img class="detail-image">
            <section class="detail-info">
                <p class="eyebrow" data-product="brand">
                </p>
                <h1 class="page-title" data-product="name">
                </h1>
                <p class="detail-price" data-product="price">
                </p>
                <p data-product="rating">
                </p>
                <p class="source-note" data-product="source">
                </p>
                <p data-product="id">
                </p>
                <p class="stock-status" data-product="stock">
                </p>
                <div class="delivery-summary">
                    <strong>
                        택배배송
                    </strong>
                    <p>
                        배송비 3,000원 · 상품 30,000원 이상 무료
                    </p>
                </div>
                <details class="store-stock">
                    <summary>
                        체험 매장 재고 보기
                    </summary>
                </details>
                <label class="field">
                    수량
                    <input type="number" name="quantity" min="1" value="1">
                </label>
                <p class="detail-total">
                </p>
                <button type="button" class="button primary" data-add-cart>
                    장바구니 담기
                </button>
            </section>
        </div>
        <section class="product-description">
            <h2>
                상품 설명
            </h2>
            <p data-product-description>
            </p>
            <details>
                <summary>
                    상품 정보 및 반품 안내
                </summary>
                <p>
                    본 화면은 학습용 Mock입니다. 제조·인증·반품 정책은 실제 구매 정보가 아니며 백엔드 연동 전 별도 확정합니다.
                </p>
            </details>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { element, link, button, money, empty, notice } = app.ui;
        app.ready.then(function (repository) {

        const product = repository.getProduct(new URLSearchParams(location.search).get("id"));
        if (!product) {
            root.replaceChildren();
            empty(root, "상품을 찾을 수 없습니다.");
            root.append(link("상품 목록으로", "list.jsp", {}, "button"));
            return;
        }
        root.querySelector(".breadcrumbs").replaceWith(app.ui.breadcrumbs(repository.getCategories(), product.categoryId));
        const layout = root.querySelector(".product-detail-layout");
        const image = layout.querySelector(".detail-image");
        image.src = app.url(product.image);
        image.alt = product.name + " 자체 제작 Mock 이미지";
        const info = layout.querySelector(".detail-info");
        const status = product.status === "SCHEDULED" ? "판매 예정" : product.status === "SOLD_OUT" || !product.onlineStock ? "품절" : "구매 가능";
        const values = { brand: product.brand, name: product.name, price: money(product.price), rating: "★ " + product.rating + " · 리뷰 " + product.reviewCount + " (Mock)", source: product.source.note, id: "품번 " + product.id, stock: status + " · 온라인 Mock 재고 " + product.onlineStock + "개" };
        Object.entries(values).forEach(([key, value]) => { info.querySelector('[data-product="' + key + '"]').textContent = value; });
        const stores = info.querySelector(".store-stock");
        repository.getStores().forEach(store => stores.append(element("p", "", store.name + " · " + (product.storeStocks[store.id] || 0) + "개 (Mock)")));
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
        const description = root.querySelector(".product-description");
        description.querySelector("[data-product-description]").textContent = product.description;

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
