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
        <main id="admin-content" class="admin-content" data-admin-page="orders" tabindex="-1">
        <p class="admin-eyebrow">
            MOCK COMMERCE OPERATIONS
        </p>
        <h1 class="page-title">
            주문 관리
        </h1>
        <% if (request.getParameter("id") != null && !request.getParameter("id").isEmpty()) { %>
        <section class="admin-panel">
            <h2>
            </h2>
            <p data-order-member>
            </p>
            <p class="admin-order-status">
            </p>
            <p data-order-address>
            </p>
            <p data-order-phone>
            </p>
            <ul class="order-items">
            </ul>
            <p data-order-total>
            </p>
            <form class="admin-editor-form" hidden>
                <label class="field">
                    변경할 주문 상태
                    <select name="status">
                    </select>
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    주문 상태 저장
                </button>
            </form>
            <p data-no-transition hidden>
                현재 상태에서 변경 가능한 다음 상태가 없습니다.
            </p>
        </section>
        <a class="button secondary" data-order-back href="${pageContext.request.contextPath}/admin/?view=orders">
            주문 목록으로
        </a>
        <% } else { %>
        <form class="admin-search">
            <label class="field">
                <span>
                    주문 ID 또는 회원 ID 검색
                </span>
                <input name="query" type="text" maxlength="120" autocomplete="off">
            </label>
            <button class="button" type="submit">
                검색
            </button>
        </form>
        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="주문 관리 표 (가로 스크롤 가능)">
            <table class="admin-table">
                <caption class="visually-hidden">
                    주문 관리
                </caption>
                <thead>
                    <tr>
                        <th scope="col">
                            주문 ID
                        </th>
                        <th scope="col">
                            회원 ID
                        </th>
                        <th scope="col">
                            주문 일시
                        </th>
                        <th scope="col">
                            금액
                        </th>
                        <th scope="col">
                            상태
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
        <% } %>
    </main>
    </div>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("admin-content");
        const { element, link, button, money, empty } = app.ui;
        const { field, selectField, formFeedback, addRow, openEditor } = app.admin;
        app.ready.then(function (repository) {
            if (!app.admin.requireAdmin(root, repository)) return;

        const params = new URLSearchParams(location.search);
        const id = params.get("id");
        const orders = repository.getAllOrders();
        if (id) {
            const order = orders.find((item) => item.id === id);
            if (!order) {
                root.querySelector(".admin-panel").remove(); root.querySelector("[data-order-back]").remove();
                empty(root, "주문을 찾을 수 없습니다.");
                return;
            }
            const panel = root.querySelector(".admin-panel");
            panel.querySelector("h2").textContent = order.id;
            panel.querySelector("[data-order-member]").textContent = "회원 ID: " + order.memberId;
            panel.querySelector(".admin-order-status").textContent = app.ui.orderStatus(order.status);
            panel.querySelector("[data-order-address]").textContent = order.recipient + " / " + order.address;
            panel.querySelector("[data-order-phone]").textContent = order.phone;
            const items = panel.querySelector(".order-items");
            order.items.forEach(item => items.append(element("li", "", item.name + " · " + item.quantity + "개 · " + money(item.lineTotal))));
            panel.querySelector("[data-order-total]").textContent = "결제 합계 " + money(order.totals.total);
            const transitions = repository.orderTransitions(order.status);
            if (transitions.length) {
                const form = panel.querySelector("form");
                form.hidden = false;
                selectField(form, "변경할 주문 상태", "status", transitions.map((status) => [status, app.ui.orderStatus(status)]));
                formFeedback(form, (values) => {
                    repository.updateOrderStatus(order.id, values.status);
                    location.reload();
                }, "주문 상태 저장");
            } else {
                panel.querySelector("[data-no-transition]").hidden = false;
            }
            return;
        }
        const { form, input } = app.admin.searchBox(root);
        const orderTable = app.admin.table(root);
        function render() {
            orderTable.body.replaceChildren();
            const query = input.value.trim().toLowerCase();
            orders.filter((order) => (order.id + " " + order.memberId).toLowerCase().includes(query)).forEach((order) => addRow(orderTable.body, [order.id, order.memberId, new Date(order.createdAt).toLocaleString("ko-KR"), money(order.totals.total), app.ui.orderStatus(order.status), link("상세", "admin/", { view: "orders", id: order.id }, "text-button")]));
        }
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            render();
        });
        if (!orders.length) {
            empty(root, "체험 주문이 없습니다. 고객 화면에서 모의 주문을 만들어 보세요.");
        }
        render();

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
