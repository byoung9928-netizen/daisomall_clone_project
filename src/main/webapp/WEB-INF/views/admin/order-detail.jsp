<%@ page contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Collections" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="daiso.order.db.OrderBean" %>
<%@ page import="daiso.cart.db.CartBean" %>
<%@ page import="daiso.product.controller.ProductView" %>

<%
OrderBean order =
    (OrderBean) request.getAttribute("adminOrder");

Object error = request.getAttribute("adminOrderError");

List<CartBean> items =
    order == null || order.getItems() == null
        ? Collections.emptyList()
        : order.getItems();

NumberFormat money = NumberFormat.getIntegerInstance();

DateTimeFormatter dateFormat =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
%>

<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">

<a class="skip-link" href="#admin-content">
    관리자 본문 바로가기
</a>

<%@ include file="../common/admin-header.jspf" %>

<div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>

    <main id="admin-content"
          class="admin-content"
          data-admin-page="orders"
          tabindex="-1">

        <h1 class="page-title">주문 상세</h1>

        <p>
            <a class="button secondary"
               href="${pageContext.request.contextPath}/app/admin/orders">
                주문 목록으로
            </a>
        </p>

        <% if (error != null || order == null) { %>

            <p class="error-message" role="alert">
                <%= error == null
                    ? "주문정보를 찾을 수 없습니다."
                    : ProductView.escape(error) %>
            </p>

        <% } else { %>

            <section class="admin-panel">
                <h2>주문정보</h2>

                <table class="admin-table">
                    <caption class="visually-hidden">
                        주문 기본정보
                    </caption>
                    <tbody>
                        <tr>
                            <th scope="row">주문번호</th>
                            <td><%= ProductView.escape(
                                order.getOrderNo()) %></td>
                        </tr>
                        <tr>
                            <th scope="row">회원번호</th>
                            <td><%= ProductView.escape(
                                order.getMemberId()) %></td>
                        </tr>
                        <tr>
                            <th scope="row">주문일시</th>
                            <td>
                                <%= order.getCreatedAt() == null
                                    ? "-"
                                    : order.getCreatedAt()
                                        .toLocalDateTime()
                                        .format(dateFormat) %>
                            </td>
                        </tr>
                        <tr>
                            <th scope="row">주문 상태</th>
                            <td><%= ProductView.escape(
                                order.getStatus()) %></td>
                        </tr>
                        <tr>
                            <th scope="row">결제 상태</th>
                            <td>
                                <%= order.getPaymentStatus() == null
                                    ? "결제 기록 없음"
                                    : ProductView.escape(
                                        order.getPaymentStatus()) %>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </section>

            <section class="admin-panel">
                <h2>주문상품</h2>

                <div class="admin-table-scroll"
                     tabindex="0"
                     role="region"
                     aria-label="주문상품 목록">

                    <table class="admin-table">
                        <caption class="visually-hidden">
                            주문 당시 상품·가격·수량
                        </caption>
                        <thead>
                            <tr>
                                <th scope="col">상품번호</th>
                                <th scope="col">상품명</th>
                                <th scope="col">단가</th>
                                <th scope="col">수량</th>
                                <th scope="col">상품 합계</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% if (items.isEmpty()) { %>
                                <tr>
                                    <td colspan="5">
                                        주문상품 정보가 없습니다.
                                    </td>
                                </tr>
                            <% } else {
                                for (CartBean item : items) {
                            %>
                                <tr>
                                    <td><%= ProductView.escape(
                                        item.getProductId()) %></td>
                                    <td><%= ProductView.escape(
                                        item.getProductName()) %></td>
                                    <td>
                                        <%= money.format(
                                            item.getPrice()) %>원
                                    </td>
                                    <td><%= item.getQuantity() %>개</td>
                                    <td>
                                        <%= money.format(
                                            item.getItemTotal()) %>원
                                    </td>
                                </tr>
                            <% }
                            } %>
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="admin-panel">
                <h2>배송정보</h2>

                <table class="admin-table">
                    <caption class="visually-hidden">
                        주문 배송정보
                    </caption>
                    <tbody>
                        <tr>
                            <th scope="row">받는 사람</th>
                            <td><%= ProductView.escape(
                                order.getRecipient()) %></td>
                        </tr>
                        <tr>
                            <th scope="row">연락처</th>
                            <td><%= ProductView.escape(
                                order.getPhone()) %></td>
                        </tr>
                        <tr>
                            <th scope="row">우편번호</th>
                            <td><%= ProductView.escape(
                                order.getPostcode()) %></td>
                        </tr>
                        <tr>
                            <th scope="row">주소</th>
                            <td>
                                <%= ProductView.escape(
                                    order.getAddress1()) %>
                                <%= ProductView.escape(
                                    order.getAddress2()) %>
                            </td>
                        </tr>
                        <tr>
                            <th scope="row">배송 요청사항</th>
                            <td>
                                <%= order.getDeliveryMessage() == null
                                    || order.getDeliveryMessage().isBlank()
                                    ? "없음"
                                    : ProductView.escape(
                                        order.getDeliveryMessage()) %>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </section>
			<section class="admin-panel">
				    <h2>배송조회</h2>
				
				    <p>
				        택배사:
				        <%= order.getCarrierId() == null
				            ? "배정 전"
				            : ProductView.escape(order.getCarrierId()) %>
				    </p>
				
				    <p>
				        운송장:
				        <%= order.getTrackingNumber() == null
				            ? "배정 전"
				            : ProductView.escape(order.getTrackingNumber()) %>
				    </p>
				
				    <form id="admin-tracking-form"
				          method="post"
				          action="${pageContext.request.contextPath}/app/admin/orders/tracking">
				
				        <input type="hidden" name="orderNo"
				               value="<%= ProductView.escape(
				                   order.getOrderNo()) %>">
				
				        <input type="hidden" name="csrfToken"
				               value="<%= ProductView.escape(
				                   request.getAttribute("adminOrderCsrf")) %>">
				
				        <button class="button primary" type="submit">
				            현재 배송 상태 조회
				        </button>
				    </form>
				
				    <p id="admin-tracking-result"
				       role="status"
				       aria-live="polite"></p>
			</section>
            <section class="admin-panel">
                <h2>주문 금액</h2>
				
                <table class="admin-table">
                    <caption class="visually-hidden">
                        주문 시 금액 내역
                    </caption>
                    <tbody>
                        <tr>
                            <th scope="row">상품 금액</th>
                            <td>
                                <%= money.format(
                                    order.getSubtotalAmount()) %>원
                            </td>
                        </tr>
                        <tr>
                            <th scope="row">배송비</th>
                            <td>
                                <%= money.format(
                                    order.getShippingFee()) %>원
                            </td>
                        </tr>
                        <tr>
                            <th scope="row">총 주문 금액</th>
                            <td>
                                <strong>
                                    <%= money.format(
                                        order.getTotalAmount()) %>원
                                </strong>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </section>

        <% } %>
    </main>
</div>
<script>
(function () {
    const form =
        document.getElementById("admin-tracking-form");

    if (!form) return;

    const resultBox =
        document.getElementById("admin-tracking-result");

    const submitButton =
        form.querySelector('button[type="submit"]');

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        if (submitButton.disabled) return;

        submitButton.disabled = true;
        resultBox.textContent = "현재 배송 상태를 조회하고 있습니다.";

        try {
            const data =
                new URLSearchParams(new FormData(form));

            const response = await fetch(form.action, {
                method: "POST",
                body: data
            });

            if (!response.ok) {
                throw new Error("배송조회 요청에 실패했습니다.");
            }

            const result = await response.json();

            if (!result.success) {
                resultBox.textContent =
                    result.message || "배송정보를 조회하지 못했습니다.";
                return;
            }

            const tracking = result.tracking;
            const lastEvent = tracking.lastEvent;
            const status = lastEvent.status;
            const statusLabels = {
            	    INFORMATION_RECEIVED: "배송 준비중",
            	    AT_PICKUP: "상품 인수중",
            	    IN_TRANSIT: "배송중",
            	    OUT_FOR_DELIVERY: "배송 출발",
            	    DELIVERED: "배송완료",
            	    ATTEMPT_FAIL: "배송 시도 실패",
            	    ERROR: "배송 오류",
            	    UNKNOWN: "배송 상태 확인중"
            	};

            	const statusText =
            	    statusLabels[status.code] || "배송 상태 확인중";
            resultBox.textContent = [
                "운송장: " + tracking.trackingNumber,
                "현재 상태: " +statusText,
                lastEvent.time,
                lastEvent.description
            ].filter(Boolean).join(" / ");

        } catch (error) {
            resultBox.textContent = error.message;

        } finally {
            submitButton.disabled = false;
        }
    });
})();
</script>

</body>
</html>