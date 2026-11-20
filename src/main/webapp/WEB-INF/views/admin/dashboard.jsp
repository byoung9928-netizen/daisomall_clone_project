<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
<script src="${pageContext.request.contextPath}/assets/js/common/admin-ui.js" defer></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" data-admin-page="dashboard" tabindex="-1">
        <p class="admin-eyebrow">
            MOCK COMMERCE OPERATIONS
        </p>
        <h1 class="page-title">
            운영 대시보드
        </h1>
        <div class="admin-stats">
            <section class="admin-stat">
                <h2>
                    상품
                </h2>
                <strong>
                </strong>
            </section>
            <section class="admin-stat">
                <h2>
                    카테고리
                </h2>
                <strong>
                </strong>
            </section>
            <section class="admin-stat">
                <h2>
                    주문
                </h2>
                <strong>
                </strong>
            </section>
            <section class="admin-stat">
                <h2>
                    결제된 주문 금액
                </h2>
                <strong>
                </strong>
            </section>
        </div>
        <section class="admin-panel">
            <h2>
                작업 안내
            </h2>
            <p>
                같은 브라우저·같은 접속 주소의 Mock 데이터만 변경됩니다. 다른 탭의 고객 화면은 새로고침하면 최신 값을 조회합니다.
            </p>
            <p>
                원본 JSON은 수정하지 않습니다. 초기화하면 생성 상품·회원·장바구니·주문·관리 변경을 모두 제거합니다.
            </p>
            <button type="button" class="button secondary" data-reset-mock>
                Mock 데이터 전체 초기화
            </button>
        </section>
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

        const orders = repository.getAllOrders();
        const products = repository.getProducts();
        const grid = root.querySelector(".admin-stats");
        [["상품", products.length + "개"], ["카테고리", repository.getCategories().length + "개"], ["주문", orders.length + "건"], ["결제된 주문 금액", money(orders.filter((order) => !["PAYMENT_FAILED", "CANCELLED"].includes(order.status)).reduce((sum, order) => sum + order.totals.total, 0))]].forEach(([label, value], index) => {
            grid.children[index].querySelector("strong").textContent = value;
        });
        const reset = root.querySelector("[data-reset-mock]");
        reset.addEventListener("click", () => {
            if (window.confirm("이 브라우저의 체험 데이터와 주문을 모두 초기화할까요?")) {
                repository.reset();
                location.href = app.url("login.jsp", { next: "admin" });
            }
        });

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
