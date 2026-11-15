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
        <main id="admin-content" class="admin-content" data-admin-page="categories" tabindex="-1">
        <p class="admin-eyebrow">
            MOCK COMMERCE OPERATIONS
        </p>
        <h1 class="page-title">
            카테고리 관리
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
        <section class="admin-panel" id="category-editor">
            <p data-editor-hint>
                목록에서 분류를 선택하세요. ID·상위 관계·계층은 변경하지 않습니다. 같은 순서가 있으면 서로 교환합니다.
            </p>
            <h2 hidden>
            </h2>
            <form class="admin-editor-form" hidden>
                <label class="field">
                    분류명
                    <input name="name" type="text" required maxlength="50" autocomplete="name">
                </label>
                <label class="field">
                    표시 순서
                    <input name="displayOrder" type="number" required maxlength="120" autocomplete="off">
                </label>
                <label class="field">
                    메뉴 노출
                    <select name="visible">
                    </select>
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    분류 저장
                </button>
            </form>
        </section>
        <p class="admin-count" data-count>
        </p>
        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="카테고리 관리 표 (가로 스크롤 가능)">
            <table class="admin-table">
                <caption class="visually-hidden">
                    카테고리 관리
                </caption>
                <thead>
                    <tr>
                        <th scope="col">
                            ID
                        </th>
                        <th scope="col">
                            이름
                        </th>
                        <th scope="col">
                            단계
                        </th>
                        <th scope="col">
                            상위 ID
                        </th>
                        <th scope="col">
                            순서
                        </th>
                        <th scope="col">
                            노출
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
            카테고리 더 보기
        </button>
    </main>
    </div>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("admin-content");
        const { link, button } = app.ui;
        const { field, selectField, formFeedback, addRow, openEditor } = app.admin;
        app.ready.then(function (repository) {
            if (!app.admin.requireAdmin(root, repository)) return;
            const section = "categories";

        const editor = root.querySelector(".admin-panel");
        editor.id = "category-editor";
        const { form, input } = app.admin.searchBox(root);
        const categoryTable = app.admin.table(root);
        let limit = 40;
        const more = root.querySelector("[data-more]");
        more.addEventListener("click", () => {
            limit += 40;
            render();
        });
        const count = root.querySelector("[data-count]");
        function edit(category) {
            const editForm = openEditor(editor, "분류 수정 · " + category.id);
            field(editForm, "분류명", "name", "text", category.name);
            const order = field(editForm, "표시 순서", "displayOrder", "number", category.displayOrder);
            order.min = 1;
            order.max = 999;
            selectField(editForm, "메뉴 노출", "visible", [["true", "노출"], ["false", "숨김"]], category.visible === false ? "false" : "true");
            formFeedback(editForm, (values) => {
                repository.saveCategory(category.id, { ...values, visible: values.visible === "true" });
                render();
            }, "분류 저장");
            editor.scrollIntoView({ block: "start" });
        }
        function render() {
            const query = input.value.trim().toLowerCase();
            const categories = repository.getCategories().filter((category) => (category.name + " " + category.id).toLowerCase().includes(query));
            count.textContent = categories.length + "개 분류";
            categoryTable.body.replaceChildren();
            categories.slice(0, limit).forEach((category) => addRow(categoryTable.body, [category.id, category.name, category.level, category.parentId || "—", category.displayOrder, category.visible === false ? "숨김" : "노출", button("수정", () => edit(category), "text-button")]));
            more.hidden = categories.length <= limit;
        }
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            limit = 40;
            render();
        });
        render();

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
