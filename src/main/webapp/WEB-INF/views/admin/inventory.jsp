<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css">
<script src="${pageContext.request.contextPath}/assets/js/common/admin-ui.js" defer></script>
<script src="${pageContext.request.contextPath}/assets/js/common/admin-inventory.js" defer></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" data-admin-page="inventory" tabindex="-1">
        <p class="admin-eyebrow">
            MOCK COMMERCE OPERATIONS
        </p>
        <h1 class="page-title">
            온라인 재고 관리
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
        <section class="admin-panel" hidden>
            <h2 hidden>
            </h2>
            <form class="admin-editor-form" hidden>
                <label class="field">
                    재고 위치
                    <select name="location">
                    </select>
                </label>
                <label class="field">
                    재고 수량
                    <input name="quantity" type="number" required maxlength="120" autocomplete="off">
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    재고 저장
                </button>
            </form>
        </section>
        <p data-count>
        </p>
        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="온라인 재고 관리 표 (가로 스크롤 가능)">
            <table class="admin-table">
                <caption class="visually-hidden">
                    온라인 재고 관리
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
                            온라인
                        </th>
                        <th scope="col">
                            서울
                        </th>
                        <th scope="col">
                            부산
                        </th>
                        <th scope="col">
                            제주
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
            재고 더 보기
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
        app.ready.then(function (repository) {
            if (!app.admin.requireAdmin(root, repository)) return;
            app.admin.bindInventory(root, repository);
        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
