<%@ page contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,java.util.List,java.util.Collections" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="daiso.cart.db.CartBean" %>
<%@ page import="daiso.product.controller.ProductView" %>

<%
Map<String, Object> returnOrder =
    (Map<String, Object>) request.getAttribute("returnOrder");

Object returnError = request.getAttribute("returnError");

List<CartBean> returnItems = returnOrder == null
    ? Collections.emptyList()
    : (List<CartBean>) returnOrder.get("items");

if (returnItems == null) {
    returnItems = Collections.emptyList();
}

NumberFormat returnMoney = NumberFormat.getIntegerInstance();
%>

<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>

    <style>
        .return-request {
            padding-top: 40px;
            padding-bottom: 60px;
        }

        .return-request section {
            margin-top: 32px;
        }

        .return-request table {
            width: 100%;
            border-collapse: collapse;
        }

        .return-request th,
        .return-request td {
            padding: 16px;
            border-bottom: 1px solid #e0e5eb;
            text-align: left;
        }

        .return-request th {
            background: #f6f7f9;
        }

        .return-request .return-table-scroll {
            overflow-x: auto;
        }

        .return-request form {
            max-width: 700px;
            display: grid;
            gap: 20px;
        }

        .return-request textarea {
            min-height: 120px;
            resize: vertical;
        }

        .return-request .return-total {
            font-size: 22px;
            font-weight: 700;
            color: #ed0025;
        }

        .return-request .return-actions {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
        }
    </style>
</head>

<body>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/navigation.jspf" %>

<main id="main-content"
      class="container shop-page return-request">

    <h1 class="page-title">반품 신청</h1>

    <% if (returnError != null || returnOrder == null) { %>

        <p class="error-message" role="alert">
            <%= returnError == null
                ? "반품 신청할 주문을 찾을 수 없습니다."
                : ProductView.escape(returnError) %>
        </p>

        <a class="button secondary"
           href="${pageContext.request.contextPath}/app/order/mypage">
            주문내역으로
        </a>

    <% } else { %>

        <p>
            주문번호:
            <%= ProductView.escape(returnOrder.get("orderNo")) %>
        </p>

        <section aria-labelledby="return-items-title">
            <h2 id="return-items-title">반품 상품</h2>

            <div class="return-table-scroll"
                 tabindex="0"
                 role="region"
                 aria-label="반품 상품 목록">

                <table>
                    <caption class="visually-hidden">
                        전체 반품 신청 상품
                    </caption>

                    <thead>
                        <tr>
                            <th scope="col">상품명</th>
                            <th scope="col">수량</th>
                            <th scope="col">상품 금액</th>
                        </tr>
                    </thead>

                    <tbody>
                        <% for (CartBean item : returnItems) { %>
                            <tr>
                                <td><%= ProductView.escape(
                                    item.getProductName()) %></td>
                                <td><%= item.getQuantity() %>개</td>
                                <td>
                                    <%= returnMoney.format(
                                        item.getItemTotal()) %>원
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            </div>
        </section>

        <section aria-labelledby="return-amount-title">
            <h2 id="return-amount-title">환불 예정 금액</h2>

            <p class="return-total">
                <%= returnMoney.format(returnOrder.get("total")) %>원
            </p>

            <p>
                주문 전체 반품 기준입니다.
                반품 신청 후 관리자 승인 시 모의환불이 진행됩니다.
            </p>
        </section>

        <section aria-labelledby="return-reason-title">
            <h2 id="return-reason-title">반품 사유</h2>

            <form id="return-request-form"
                  method="post"
                  action="${pageContext.request.contextPath}/app/order/return/request">

                <input type="hidden" name="orderNo"
                       value="<%= ProductView.escape(
                           returnOrder.get("orderNo")) %>">

                <input type="hidden" name="csrfToken"
                       value="<%= ProductView.escape(
                           request.getAttribute("returnCsrf")) %>">

                <label class="field">
                    <span>반품 사유</span>
                    <select name="reason" required>
                        <option value="">사유를 선택해 주세요</option>
                        <option value="상품불량">상품불량</option>
                        <option value="오배송">오배송</option>
                        <option value="단순변심">단순변심</option>
                        <option value="기타">기타</option>
                    </select>
                </label>

                <label class="field">
                    <span>상세 사유 — 기타 선택 시 필수</span>
                    <textarea name="reasonDetail"
                              maxlength="500"
                              placeholder="상세 사유를 입력해 주세요"></textarea>
                </label>

                <p id="return-request-message"
                   role="status"
                   aria-live="polite"></p>

                <div class="return-actions">
                    <a class="button secondary"
                       href="${pageContext.request.contextPath}/app/order/mypage">
                        주문내역으로
                    </a>

                    <button class="button primary" type="submit">
                        반품 신청
                    </button>
                </div>
            </form>
        </section>

    <% } %>
</main>

<%@ include file="../common/footer.jspf" %>

<script>
(function () {
    const form = document.getElementById("return-request-form");

    if (!form) return;

    const reason = form.elements.reason;
    const detail = form.elements.reasonDetail;
    const button = form.querySelector('button[type="submit"]');
    const message = document.getElementById("return-request-message");

    let submitted = false;

    reason.addEventListener("change", function () {
        detail.required = reason.value === "기타";
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        if (button.disabled || submitted) return;

        if (reason.value === "기타" && !detail.value.trim()) {
            message.textContent = "기타 반품 사유를 입력해 주세요.";
            detail.focus();
            return;
        }

        if (!window.confirm("이 주문의 전체 상품을 반품 신청할까요?")) {
            return;
        }

        button.disabled = true;
        message.textContent = "반품 신청을 저장하고 있습니다.";

        try {
            const data = new URLSearchParams(new FormData(form));

            const response = await fetch(form.action, {
                method: "POST",
                body: data
            });

            if (!response.ok) {
                throw new Error("반품 신청 요청에 실패했습니다.");
            }

            const result = (await response.text()).trim();

            if (result !== "OK") {
                message.textContent = result;
                return;
            }

            submitted = true;
            button.textContent = "신청 완료";
            reason.disabled = true;
            detail.disabled = true;

            message.textContent =
                "반품 신청이 접수되었습니다. "
                + "관리자 승인 후 모의환불이 진행됩니다.";

        } catch (error) {
            message.textContent = error.message;

        } finally {
            button.disabled = submitted;
        }
    });
})();
</script>
</body>
</html>