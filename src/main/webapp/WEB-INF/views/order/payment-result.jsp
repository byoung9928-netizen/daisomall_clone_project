<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,java.util.List,java.util.Collections,daiso.cart.db.CartBean,daiso.product.controller.ProductView" %>
<%
    String completeContext = request.getContextPath();
    Map<String,Object> completed = (Map<String,Object>) request.getAttribute("completedOrder");
    boolean completedSuccessfully = completed != null;
    Map<String,String> completedAddress = completedSuccessfully && completed.get("address") instanceof Map
        ? (Map<String,String>) completed.get("address") : Collections.emptyMap();
    List<CartBean> completedItems = completedSuccessfully && completed.get("items") instanceof List
        ? (List<CartBean>) completed.get("items") : Collections.emptyList();
    java.text.NumberFormat completedMoney = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <style>
    .complete-screen.container{width:calc(100% - 48px);max-width:1280px;box-sizing:border-box;margin:0 auto;padding:40px 0 80px;color:#17212a}
    .complete-screen *,.complete-dialog *{box-sizing:border-box}.complete-screen [hidden]{display:none!important}
    .complete-screen .complete-title{font-size:38px;line-height:1.3;font-weight:800;margin:0 0 40px}
    .complete-hero{text-align:center;margin:0 0 72px}.complete-arrival{font-size:26px;font-weight:800;line-height:1.5;margin:0 0 46px}
    .complete-shipping{display:flex;justify-content:center;align-items:center;gap:12px;font-size:22px;font-weight:700;color:#3b4650;margin-bottom:62px}.complete-shipping svg{width:52px;height:52px;fill:currentColor}
    .complete-notice{max-width:920px;background:#f6f7f9;text-align:left;margin:0 auto 32px;padding:18px 20px;color:#6d7d90;font-size:12px;line-height:1.8}.complete-notice strong{display:block;margin-bottom:6px}.complete-notice p{margin:2px 0}
    .complete-actions{display:flex;gap:12px;justify-content:center}.complete-screen .complete-button{display:flex;align-items:center;justify-content:center;min-height:48px;width:320px;padding:12px 20px;border:1px solid #dfe4ea;color:#17212a;background:#fff;font-size:14px;font-weight:700;text-decoration:none;cursor:pointer;border-radius:0}.complete-screen .complete-button.primary{background:#171e24;color:#fff;border-color:#171e24}
    .complete-section{margin-bottom:72px}.complete-section-header{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:30px}.complete-section-header h2{font-size:28px;line-height:1.4;font-weight:800;margin:0}.complete-screen .complete-address-change{font-size:12px;min-height:28px;border:1px solid #e0e5eb;background:#fff;padding:3px 12px;color:#7b8795;cursor:default}
    .complete-table{width:100%;border-collapse:collapse;border-top:1px solid #e1e5eb;text-align:left;font-size:14px}.complete-table th,.complete-table td{padding:18px 20px;border-bottom:1px solid #e1e5eb;line-height:1.6;vertical-align:top}.complete-table th{width:195px;font-weight:400;color:#52657b}.complete-table td{overflow-wrap:anywhere}.complete-table .complete-money{text-align:right;white-space:nowrap}.complete-table .complete-sub th,.complete-table .complete-sub td{color:#8390a2}.complete-table .complete-total{background:#f6f7f9;font-weight:700}.complete-table .complete-total th{font-weight:700;color:#17212a}.complete-table .complete-total td{color:#e60023;font-weight:800}.complete-pay-heading{font-size:22px;font-weight:700;margin:30px 0 24px}.complete-status{display:inline-block;background:#657488;color:#fff;font-size:11px;padding:1px 7px;margin-left:12px;vertical-align:middle}.complete-error{padding:60px 0;text-align:center}.complete-error p{line-height:1.8;margin:0 0 30px}
    .complete-dialog{border:0;border-radius:2px;padding:0;width:680px;max-width:calc(100% - 32px);max-height:calc(100dvh - 48px);color:#17212a}.complete-dialog::backdrop{background:rgba(0,0,0,.65)}.complete-dialog-head{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:24px;border-bottom:1px solid #e1e5eb}.complete-dialog-head h2{margin:0;font-size:22px;font-weight:800}.complete-close{border:0;background:none;color:#17212a;font-size:28px;cursor:pointer;width:30px;height:30px;line-height:1}.complete-dialog-body{padding:24px;overflow-y:auto;max-height:calc(100dvh - 150px)}.complete-order-no{font-size:13px;line-height:1.8;color:#64748a;margin:0 0 24px;overflow-wrap:anywhere}.complete-item{display:flex;gap:16px;align-items:center;padding:18px 0;border-top:1px solid #e1e5eb}.complete-item img{width:76px;height:90px;object-fit:contain;background:#f7f7f7;flex-shrink:0}.complete-item p{margin:5px 0;font-size:14px;line-height:1.6}.complete-item strong{font-size:17px}.complete-item small{font-size:13px;font-weight:400;color:#657488}.complete-dialog-total{border-top:1px solid #e1e5eb;padding-top:20px;text-align:right;font-size:17px;font-weight:700;color:#e60023}.complete-screen :focus-visible,.complete-dialog :focus-visible{outline:2px solid #2a66bd;outline-offset:3px}
    @media(max-width:800px){.complete-screen.container{width:calc(100% - 32px);padding-top:28px}.complete-screen .complete-title{font-size:28px;margin-bottom:32px}.complete-arrival{font-size:22px;margin-bottom:28px}.complete-shipping{font-size:18px;margin-bottom:36px}.complete-notice{font-size:12px}.complete-actions{gap:10px}.complete-screen .complete-button{width:auto;flex:1;max-width:320px;font-size:13px;padding:12px}.complete-hero,.complete-section{margin-bottom:48px}.complete-section-header h2{font-size:23px}.complete-section-header{margin-bottom:20px}.complete-table th{width:105px}.complete-table th,.complete-table td{padding:15px 10px;font-size:13px}.complete-pay-heading{font-size:20px}.complete-status{display:block;width:fit-content;margin:8px 0 0}}
    </style>
</head>
<body>
<a class="skip-link" href="#main-content">본문 바로가기</a>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/navigation.jspf" %>
<main id="main-content" class="container shop-page complete-screen" tabindex="-1">
<% if (completedSuccessfully) { %>
    <h1 class="complete-title">주문완료</h1>
    <section class="complete-hero" aria-label="모의결제 완료 안내">
        <p class="complete-arrival" role="status">주문이 완료되었습니다.</p>
        <div class="complete-shipping"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 5h12v10h1V8h4l3 4v6h-2a3 3 0 0 1-6 0H9a3 3 0 0 1-6 0H2V5zm15 5v3h3l-2-3h-1zM6 16a1 1 0 1 0 0 2 1 1 0 0 0 0-2zm11 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2z"/></svg><span>택배배송</span></div>
        <div class="complete-notice"><strong>확인해 주세요</strong><p>· 카카오페이 모의결제 승인 결과입니다.</p><p>· 배송 예정일은 배송 일정이 확정된 후 안내됩니다.</p></div>
        <div class="complete-actions"><button class="complete-button" id="open-complete-detail" type="button">주문 상세보기</button><a class="complete-button primary" href="<%= completeContext %>/list.jsp">쇼핑 계속하기</a></div>
    </section>
    <section class="complete-section" aria-labelledby="complete-delivery-heading">
        <div class="complete-section-header"><h2 id="complete-delivery-heading">배송정보</h2><button class="complete-address-change" type="button" disabled title="결제 후 배송지 변경 기능은 아직 연결되지 않았습니다">⇄ 변경</button></div>
        <table class="complete-table"><caption class="visually-hidden">주문 배송정보</caption><tbody>
            <tr><th scope="row">수령 방법</th><td>택배배송</td></tr>
            <tr><th scope="row">배송지</th><td><%= ProductView.escape(completedAddress.get("addressName")) %></td></tr>
            <tr><th scope="row">받는분</th><td><%= ProductView.escape(completedAddress.get("recipient")) %></td></tr>
            <tr><th scope="row">연락처</th><td><%= ProductView.escape(completedAddress.get("phone")) %></td></tr>
            <tr><th scope="row">주소</th><td>[<%= ProductView.escape(completedAddress.get("postcode")) %>] <%= ProductView.escape(completedAddress.get("address1")) %> <%= ProductView.escape(completedAddress.get("address2")) %></td></tr>
            <tr><th scope="row">배송 요청사항</th><td><%= completed.get("message") == null || completed.get("message").toString().isBlank() ? "없음" : ProductView.escape(completed.get("message")) %></td></tr>
        </tbody></table>
    </section>
    <section class="complete-section" aria-labelledby="complete-price-heading">
        <div class="complete-section-header"><h2 id="complete-price-heading">결제 금액</h2></div>
        <table class="complete-table"><caption class="visually-hidden">승인된 주문 금액</caption><tbody>
            <tr><th scope="row">총 상품 금액</th><td class="complete-money"><%= completedMoney.format(completed.get("subtotal")) %>원</td></tr>
            <tr><th scope="row">총 배송비</th><td class="complete-money"><%= completedMoney.format(completed.get("shipping")) %>원</td></tr>
            <tr class="complete-sub"><th scope="row">기본 배송비</th><td class="complete-money"><%= completedMoney.format(completed.get("shipping")) %>원</td></tr>
            <tr class="complete-total"><th scope="row">총 결제 금액</th><td class="complete-money"><%= completedMoney.format(completed.get("total")) %>원</td></tr>
        </tbody></table>
        <h3 class="complete-pay-heading">결제수단</h3>
        <table class="complete-table"><caption class="visually-hidden">결제수단 및 승인 시각</caption><tbody>
            <tr><th scope="row">결제수단</th><td><%= ProductView.escape(completed.get("paymentMethod")) %></td></tr>
            <tr><th scope="row">결제 시간</th><td><%= ProductView.escape(completed.get("paidAt")) %><span class="complete-status">결제완료</span></td></tr>
        </tbody></table>
    </section>
    <dialog class="complete-dialog" id="complete-detail-dialog" aria-labelledby="complete-detail-title">
        <div class="complete-dialog-head"><h2 id="complete-detail-title">주문 상세</h2><button class="complete-close" id="close-complete-detail" type="button" aria-label="주문 상세 닫기">×</button></div>
        <div class="complete-dialog-body"><p class="complete-order-no">주문번호 <%= ProductView.escape(completed.get("orderNo")) %></p>
        <% for (CartBean item : completedItems) {
            String completedImage = item.getImageUrl();
            if (completedImage == null || completedImage.isBlank()) completedImage = "assets/images/mock-product-0.svg";
            if (!completedImage.startsWith("https://")) completedImage = completeContext + "/" + completedImage;
        %>
            <article class="complete-item"><img src="<%= ProductView.escape(completedImage) %>" alt=""><div><p><%= ProductView.escape(item.getProductName()) %></p><strong><%= completedMoney.format(item.getItemTotal()) %>원 <small>/ <%= item.getQuantity() %>개</small></strong></div></article>
        <% } %>
        <p class="complete-dialog-total">총 결제 금액 <%= completedMoney.format(completed.get("total")) %>원</p></div>
    </dialog>
<% } else { %>
    <h1 class="complete-title">모의결제 결과</h1>
    <div class="complete-error"><p role="status"><%= request.getAttribute("paymentMessage") == null ? "표시할 결제 정보가 없습니다." : ProductView.escape(request.getAttribute("paymentMessage")) %></p><div class="complete-actions"><a class="complete-button" href="<%= completeContext %>/app/cart">장바구니로</a><a class="complete-button primary" href="<%= completeContext %>/">홈으로</a></div></div>
<% } %>
</main>
<%@ include file="../common/footer.jspf" %>
<script>
document.addEventListener('DOMContentLoaded',function(){
    const open = document.getElementById('open-complete-detail');
    if (!open) return;
    const dialog = document.getElementById('complete-detail-dialog');
    open.addEventListener('click',function(){dialog.showModal();});
    document.getElementById('close-complete-detail').addEventListener('click',function(){dialog.close();});
});
</script>
</body>
</html>
