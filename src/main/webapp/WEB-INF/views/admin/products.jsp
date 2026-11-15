<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css">
<script src="${pageContext.request.contextPath}/assets/js/common/admin-ui.js" defer></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" data-admin-page="products" tabindex="-1">
        <p class="admin-eyebrow">
            MOCK COMMERCE OPERATIONS
        </p>
        <h1 class="page-title">
            상품 관리
        </h1>
        <form class="admin-search">
            <label class="field">
                <span>
                    이름 또는 ID 검색
                </span>
                <input name="query" type="text" maxlength="120" autocomplete="off">
            </label>
            <button class="button" type="submit">
                검색
            </button>
        </form>
        <button type="button" class="button primary" data-create-product>
            상품 등록
        </button>
        <section class="admin-panel" id="product-editor" hidden>
            <h2 hidden>
            </h2>
            <form class="admin-editor-form" hidden>
                <label class="field">
                    상품 설명
                    <textarea name="description" maxlength="2000" rows="4">
                    </textarea>
                </label>
                <label class="field">
                    상품명
                    <input name="name" type="text" required maxlength="50" autocomplete="name">
                </label>
                <label class="field">
                    브랜드
                    <input name="brand" type="text" required maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    카테고리 (최하위)
                    <select name="categoryId">
                    </select>
                </label>
                <label class="field">
                    가격 (원)
                    <input name="price" type="number" required maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    온라인 재고
                    <input name="onlineStock" type="number" required maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    판매 상태
                    <select name="status">
                    </select>
                </label>
                <label class="field">
                    Mock 이미지
                    <select name="image">
                    </select>
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    상품 저장
                </button>
            </form>
        </section>
        <p class="admin-count" data-count>
        </p>
        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품 관리 표 (가로 스크롤 가능)">
            <table class="admin-table">
                <caption class="visually-hidden">
                    상품 관리
                </caption>
                <thead>
                    <tr>
                        <th scope="col">
                            상품 ID
                        </th>
                        <th scope="col">
                            상품명
                        </th>
                        <th scope="col">
                            가격
                        </th>
                        <th scope="col">
                            상태
                        </th>
                        <th scope="col">
                            온라인 재고
                        </th>
                        <th scope="col">
                            관리
                        </th>
                    </tr>
                </thead>
                <tbody>
                </tbody>
            </table>
        </div>
        <button type="button" class="button secondary" data-more>
            상품 더 보기
        </button>
    </main>
    </div>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("admin-content");
        const { link, button, money } = app.ui;
        const { field, selectField, formFeedback, addRow, openEditor } = app.admin;
        app.ready.then(function (repository) {
            if (!app.admin.requireAdmin(root, repository)) return;
            const section = "products";

        const editor = root.querySelector(".admin-panel");
        editor.id = "product-editor";
        const count = root.querySelector("[data-count]");
        const { form, input } = app.admin.searchBox(root);
        const productTable = app.admin.table(root);
        let limit = 30;
        const more = root.querySelector("[data-more]");
        more.addEventListener("click", () => {
            limit += 30;
            render();
        });
        function edit(product) {
            const editForm = openEditor(editor, product ? "상품 수정 · " + product.id : "상품 등록");
            field(editForm, "상품명", "name", "text", product ? product.name : "");
            field(editForm, "브랜드", "brand", "text", product ? product.brand : "샘플 생활연구소");
            const categories = repository.getCategories();
            const options = categories.filter((category) => category.isLeaf).map((category) => {
                const parent = categories.find((node) => node.id === category.parentId);
                const grandparent = parent && categories.find((node) => node.id === parent.parentId);
                return [category.id, [grandparent && grandparent.name, parent && parent.name, category.name].filter(Boolean).join(" › ")];
            });
            selectField(editForm, "카테고리 (최하위)", "categoryId", options, product ? product.categoryId : undefined);
            const price = field(editForm, "가격 (원)", "price", "number", product ? product.price : 1000);
            price.min = 1;
            price.max = 10000000;
            const stock = field(editForm, "온라인 재고", "onlineStock", "number", product ? product.onlineStock : 10);
            stock.min = 0;
            stock.max = 999999;
            selectField(editForm, "판매 상태", "status", [["AVAILABLE", "판매 중"], ["SOLD_OUT", "품절"], ["SCHEDULED", "판매 예정"]], product ? product.status : "AVAILABLE");
            selectField(editForm, "Mock 이미지", "image", Array.from({ length: 6 }, (_, index) => ["assets/images/mock-product-" + index + ".svg", "샘플 이미지 " + (index + 1)]), product ? product.image : undefined);
            const description = editForm.elements.description;
            description.rows = 4;
            description.value = product ? product.description : "관리자에서 만든 체험용 상품입니다.";
            formFeedback(editForm, (values) => {
                const saved = repository.saveProduct(product ? product.id : null, values);
                product = saved;
                render();
            }, "상품 저장");
            editor.scrollIntoView({ block: "start" });
        }
        function render() {
            const query = input.value.trim().toLowerCase();
            const products = repository.getProducts().filter((product) => (product.name + " " + product.id).toLowerCase().includes(query));
            count.textContent = products.length + "개 상품";
            productTable.body.replaceChildren();
            products.slice(0, limit).forEach((product) => addRow(productTable.body, [product.id, product.name, money(product.price), product.status, product.onlineStock, button("수정", () => edit(product), "text-button")]));
            more.hidden = products.length <= limit;
        }
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            limit = 30;
            render();
        });
        root.querySelector("[data-create-product]").addEventListener("click", () => edit(null));
        render();

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
