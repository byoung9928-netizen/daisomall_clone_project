<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,java.text.NumberFormat,java.util.Locale,daiso.product.controller.ProductView" %>
<%
    Map<?, ?> receipt = (Map<?, ?>) request.getAttribute("pickupCompleted");
    NumberFormat won = NumberFormat.getIntegerInstance(Locale.KOREA);
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <title>매장픽업 주문완료</title>
    <%@ include file="../common/head.jspf" %>
    <style>
        .pickup-receipt { max-width: 800px; margin: 48px auto 88px; padding: 0 20px; color: #171e24; }
        .pickup-receipt h1 { margin: 0 0 40px; font-size: 36px; }
        .pickup-receipt h2 { margin: 0 0 12px; font-size: 22px; }
        .pickup-receipt .receipt-status { display: flex; align-items: center; gap: 10px; margin-bottom: 32px; }
        .pickup-receipt dl { margin: 0; border-top: 1px solid #171e24; }
        .pickup-receipt dl div { display: flex; justify-content: space-between; gap: 20px;
            padding: 16px 0; border-bottom: 1px solid #e5e8eb; }
        .pickup-receipt dd { margin: 0; text-align: right; overflow-wrap: anywhere; }
        .pickup-receipt .receipt-total { font-weight: 700; }
        .pickup-receipt .receipt-total dd { color: #df0011; }
        .pickup-receipt .receipt-link { display: block; max-width: 320px; margin: 32px auto 0;
            padding: 14px; background: #171e24; color: white; text-decoration: none;
            text-align: center; font-weight: 700; border-radius: 0; }
    </style>
</head>
<body>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/navigation.jspf" %>

<main class="pickup-receipt">
    <h1>주문완료</h1>

    <div class="receipt-status">
        <img src="<%= ctx %>/assets/images/home/service-pickup.svg"
             width="32" height="32" alt="">
        <strong>매장픽업 주문이 완료되었습니다.</strong>
    </div>

    <h2>주문 정보</h2>
    <dl>
        <div><dt>주문번호</dt><dd><%= ProductView.escape(receipt.get("orderNo")) %></dd></div>
        <div><dt>픽업매장</dt><dd><%= ProductView.escape(receipt.get("storeName")) %></dd></div>
        <div><dt>수령인</dt><dd><%= ProductView.escape(receipt.get("recipient")) %></dd></div>
		<div><dt>결제수단</dt><dd><%= ProductView.escape(receipt.get("paymentMethod")) %></dd></div>        
		<div><dt>총 상품 금액</dt><dd><%= won.format(receipt.get("subtotal")) %>원</dd></div>
        <div><dt>픽업 수수료</dt><dd><%= won.format(receipt.get("pickupFee")) %>원</dd></div>
        <div class="receipt-total"><dt>결제 금액</dt><dd><%= won.format(receipt.get("total")) %>원</dd></div>
    </dl>

    <a class="receipt-link" href="<%= ctx %>/app/order/mypage">주문 내역 보기</a>
</main>

<%@ include file="../common/footer.jspf" %>
</body>
</html>