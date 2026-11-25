<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.util.Collections,daiso.cart.db.CartBean,daiso.product.controller.ProductView" %>
<%
    String orderContext = request.getContextPath();
    List<CartBean> orderItems = (List<CartBean>) request.getAttribute("orderItems");
    if (orderItems == null) orderItems = Collections.emptyList();
    Map<String,String> delivery = (Map<String,String>) request.getAttribute("deliveryAddress");
    if (delivery == null) delivery = Collections.emptyMap();
    String draftRecipient = delivery.getOrDefault("recipient", "");
    if (draftRecipient.isEmpty() && session.getAttribute("name") instanceof String) {
        draftRecipient = (String) session.getAttribute("name");
    }
    String orderCsrf = (String) request.getAttribute("orderCsrf");
    String savedMessage = request.getAttribute("deliveryMessage") instanceof String ? (String) request.getAttribute("deliveryMessage") : "";
    java.text.NumberFormat orderMoney = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
    long orderSubtotal = request.getAttribute("orderSubtotal") instanceof Long ? (Long) request.getAttribute("orderSubtotal") : 0;
    long orderShipping = request.getAttribute("orderShipping") instanceof Long ? (Long) request.getAttribute("orderShipping") : 0;
    long orderTotal = orderSubtotal + orderShipping;
    boolean hasDelivery = !delivery.getOrDefault("address1", "").isEmpty();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <style>
    .order-screen.container{width:calc(100% - 48px);max-width:1280px;margin:0 auto;padding:40px 0 90px;box-sizing:border-box;color:#161d24}
    .order-screen *, .delivery-dialog *{box-sizing:border-box}
    .order-screen .order-title{font-size:38px;font-weight:800;line-height:1.3;margin:0 0 40px}
    .order-columns{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:28px;align-items:start}
    .order-main{min-width:0}.order-block{margin:0 0 76px}.order-section-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:28px}
    .order-section-heading h2{font-size:26px;font-weight:800;margin:0;line-height:1.4}.order-screen .order-small-button,.delivery-dialog .order-small-button{border:1px solid #e1e4e9;background:#fff;color:#17212a;min-height:48px;padding:0 18px;font-size:14px;font-weight:700;cursor:pointer}
    .order-screen .order-register{min-height:28px;padding:3px 12px;font-size:12px;font-weight:400}.order-address-box{border-top:1px solid #e1e4e9;border-bottom:1px solid #e1e4e9;padding:20px;font-size:14px;line-height:1.8}
    .order-address-box p{margin:3px 0}.order-address-name{display:flex;align-items:center;gap:10px}.order-address-name strong{font-size:17px}.order-address-tag{border:1px solid #e1e4e9;padding:0 8px;font-size:12px;color:#637184}.order-address-phone{color:#637184}
    .delivery-request{width:min(460px,100%);margin-top:18px;position:relative}.delivery-request select{width:100%;min-height:48px;border:1px solid #151d24;background:#fff;padding:0 40px 0 14px;font-size:14px;color:#152333;border-radius:2px}
    .delivery-request input{width:100%;height:48px;margin-top:10px;border:1px solid #e1e4e9;padding:0 14px;font-size:14px}.delivery-request label{display:block;font-size:13px;margin:0 0 8px;color:#637184}.order-screen [hidden],.delivery-dialog [hidden]{display:none!important}
    .order-delivery-date{font-size:13px;color:#607083}.order-product-row{display:flex;gap:16px;align-items:center;padding:0 0 24px;margin-bottom:16px;border-bottom:1px solid #edf0f3}.order-product-row img{width:80px;height:108px;object-fit:contain;background:#f6f6f6;flex-shrink:0}.order-product-name{font-size:14px;line-height:1.6;margin:4px 0;color:#19222c;text-decoration:none}.order-product-price{font-size:20px;font-weight:800}.order-product-quantity{font-size:14px;font-weight:400}.order-screen .order-note{padding:18px 20px;background:#f6f7f9;color:#6c7c8f;font-size:12px;line-height:1.8}.order-note strong{display:block;margin-bottom:6px}.order-note p{margin:2px 0}
    .coupon-row{display:flex;gap:12px}.coupon-row input{min-width:0;flex:1;height:48px;border:1px solid #e1e4e9;padding:0 18px;background:#fff;color:#85909f;font-size:14px}.coupon-row button{width:120px}.order-discount-total{color:#e60023;font-size:16px}.order-point-row{display:grid;grid-template-columns:280px minmax(0,1fr) 120px;gap:12px;align-items:start;margin:0 0 24px;padding-left:20px}.order-point-row>label{font-size:14px;padding-top:14px}.order-point-input{position:relative}.order-point-input input{width:100%;height:48px;border:1px solid #e1e4e9;background:#f6f7f9;padding:0 32px 0 16px;color:#a6aeb8;font-size:14px}.order-point-input>span{position:absolute;right:16px;top:15px;font-size:14px;color:#697688}.order-point-balance{font-size:12px;color:#6c7c8f;margin:10px 12px 0}.order-screen button:disabled{cursor:default;background:#eceef2;color:#b7bec8;border-color:#eceef2}.gift-register{margin-left:312px;border-top:1px solid #e1e4e9;padding-top:16px;display:flex;align-items:center;gap:12px;font-size:12px;color:#607083}.order-screen .gift-register button{min-height:28px;font-size:12px;padding:0 10px}
    .order-remember{display:flex;align-items:center;gap:6px;font-size:12px;color:#4e5e72}.order-screen input[type=checkbox]{appearance:auto;width:18px;height:18px;accent-color:#171e24;margin:0}.order-required{color:#ed0025}.payment-methods{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.order-screen .payment-method{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;min-height:104px;border:1px solid #e1e4e9;border-radius:0;background:#fff;color:#152333;font-size:14px;position:relative;padding:12px 6px}.order-screen .payment-method:disabled{background:#fff;color:#627083;border-color:#e1e4e9}.payment-method[aria-pressed=true]{border:2px solid #171e24!important}.payment-wordmark{display:block;font-size:18px;font-weight:800;line-height:1.2}.payment-wordmark.toss{color:#0064ff}.payment-wordmark.shinhan{color:#0866ff}.payment-wordmark.samsung{color:#00479c;font-size:13px}.payment-wordmark.naver{color:#00c73c}.payment-wordmark.kakao{background:#ffdf00;border-radius:20px;padding:3px 9px;color:#171b1f;font-size:14px}.payment-wordmark.payco{color:#ef173a}.payment-wordmark.nh{color:#0475dc}.payment-wordmark.lotte{color:#df143b}.payment-wordmark.culture{font-size:12px}.payment-badge{position:absolute;top:-11px;left:12px;background:#171e24;color:#fff;border-radius:12px;padding:3px 10px;font-size:10px}.payment-help{color:#68778a;font-size:13px;line-height:1.8;margin:20px 0}.payment-detail{border:1px solid #e1e4e9;padding:18px 20px;margin-top:20px;font-size:14px;line-height:1.8}.payment-detail p{margin:0}.order-agreements{border-top:1px solid #e1e4e9;padding-top:24px}.order-agreements label{display:flex;align-items:flex-start;gap:10px;font-size:14px;line-height:1.7}.order-agreements input{flex-shrink:0;margin-top:3px!important}
    .order-summary{border:1px solid #e1e4e9;padding:24px;position:sticky;top:24px;background:#fff}.order-summary-line{display:flex;justify-content:space-between;gap:12px;font-size:14px;margin:0 0 14px}.order-summary-line.sub{color:#6a788b}.order-summary-total{border-top:1px solid #e1e4e9;border-bottom:1px solid #e1e4e9;margin-top:22px;padding:20px 0;display:flex;justify-content:space-between;align-items:center;font-weight:700;font-size:16px}.order-summary-total strong{color:#e60023;font-size:22px}.order-summary details{font-size:13px;line-height:1.8;color:#4e5968;padding:20px 0;border-bottom:1px solid #e1e4e9}.order-summary summary{cursor:pointer;list-style:none;text-align:center}.order-summary summary::after{content:'⌄';display:block;margin-top:7px;font-size:20px}.order-summary details[open] summary::after{content:'⌃'}.order-summary details p{font-size:12px;color:#637184}.order-pay-button{width:100%;min-height:48px;margin-top:24px;border:0;background:#171e24;color:#fff;font-size:16px;font-weight:700;cursor:pointer}.order-pay-button:disabled{opacity:.6;cursor:not-allowed}.order-pay-note{font-size:12px;color:#7b8795;text-align:center;margin:10px 0 0}.order-empty{padding:80px 0;text-align:center}.order-empty a{display:inline-block;padding:14px 24px;background:#171e24;color:#fff;text-decoration:none;margin-top:18px}.order-feedback{color:#c00020;font-size:14px;line-height:1.6}
    .delivery-dialog{width:520px;max-width:calc(100% - 32px);max-height:calc(100dvh - 64px);padding:0;border:0;border-radius:2px;color:#15202c;background:white}.delivery-dialog::backdrop{background:rgba(0,0,0,.73)}.delivery-dialog-frame{display:flex;flex-direction:column;max-height:calc(100dvh - 64px)}.delivery-dialog-header{display:flex;justify-content:space-between;align-items:center;padding:24px 24px 20px;flex-shrink:0}.delivery-dialog-header h2{margin:0;font-size:20px;font-weight:800}.delivery-close{width:28px;height:28px;border:0;background:none;color:#17212a;font-size:28px;line-height:1;cursor:pointer}.delivery-form-scroll{padding:4px 24px 8px;overflow-y:auto;min-height:0}.delivery-field{margin:0 0 30px}.delivery-field>label{display:block;font-size:18px;font-weight:700;margin-bottom:12px}.delivery-field input{width:100%;min-height:48px;border:1px solid #e1e4e9;background:#fff;border-radius:0;padding:12px 20px;font-size:14px;color:#17212a}.delivery-field input::placeholder{color:#b5bdc7}.delivery-field input[readonly]{background:#f6f7f9}.delivery-field input[aria-invalid=true]{border-color:#ed0025}.delivery-field .field-error{color:#ed0025;font-size:14px;margin:8px 0 0;line-height:1.5}.delivery-address-search{display:flex;gap:8px;margin-bottom:16px}.delivery-address-search input{flex:1;min-width:0}.delivery-address-search button{flex-shrink:0}.delivery-address-line{margin-bottom:16px}.delivery-dialog-footer{padding:20px 24px 24px;flex-shrink:0}.delivery-save{width:100%;height:48px;background:#171e24;border:0;color:white;font-size:14px;font-weight:700;cursor:pointer}.delivery-save:disabled{opacity:.5}.delivery-dialog :focus-visible,.order-screen :focus-visible{outline:2px solid #2a66bd;outline-offset:3px}.coupon-empty{text-align:center;padding:40px 0;color:#718092}
    @media(max-width:1100px){.order-point-row{grid-template-columns:170px minmax(0,1fr) 100px}.gift-register{margin-left:202px}.payment-methods{gap:10px}.payment-wordmark{font-size:16px}}
    @media(max-width:800px){.order-screen.container{width:calc(100% - 32px);padding-top:28px}.order-screen .order-title{font-size:28px;margin-bottom:32px}.order-columns{grid-template-columns:1fr}.order-summary{position:static}.order-block{margin-bottom:46px}.order-section-heading h2{font-size:22px}.order-point-row{grid-template-columns:1fr 90px;padding:0;gap:10px}.order-point-row>label{grid-column:1/-1;padding-top:0}.gift-register{margin-left:0}.payment-methods{grid-template-columns:repeat(2,minmax(0,1fr))}.order-section-heading{flex-wrap:wrap}.delivery-dialog{max-height:calc(100dvh - 32px)}.delivery-dialog-frame{max-height:calc(100dvh - 32px)}.delivery-dialog-header{padding:20px}.delivery-form-scroll{padding:4px 20px 8px}.delivery-dialog-footer{padding:16px 20px 20px}.delivery-field>label{font-size:16px}.order-remember{font-size:11px}}
    </style>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <main id="main-content" class="container shop-page order-screen" data-page="checkout" tabindex="-1">
    <h1 class="order-title">택배배송 주문서</h1>
    <div id="order-selected-items" hidden>
    <% for (CartBean item : orderItems) { %>
        <input type="hidden"
               name="cartItemId"
               value="<%= item.getCartItemId() %>">
    <% } %>
</div>
    <% if (orderItems.isEmpty()) { %>
        <div class="order-empty"><p>주문할 상품이 없습니다.</p><a href="<%= orderContext %>/app/cart">장바구니로</a></div>
    <% } else { %>
    <p id="order-feedback" class="order-feedback" role="alert">${orderError}</p>
    <div class="order-columns">
      <div class="order-main">
        <section class="order-block" aria-labelledby="delivery-heading">
          <div class="order-section-heading"><h2 id="delivery-heading">배송지</h2><button class="order-small-button order-register" type="button" id="open-delivery">⌖ <%= hasDelivery ? "변경" : "등록" %></button></div>
          <div class="order-address-box" id="delivery-display">
          <% if (!hasDelivery) { %><p>배송지를 등록해 주세요.</p><% } else { %>
            <p class="order-address-name"><strong><%= ProductView.escape(delivery.get("recipient")) %></strong><span class="order-address-tag"><%= ProductView.escape(delivery.get("addressName")) %></span></p>
            <p class="order-address-phone"><%= ProductView.escape(delivery.get("phone")) %></p>
            <p>[<%= ProductView.escape(delivery.get("postcode")) %>] <%= ProductView.escape(delivery.get("address1")) %> <%= ProductView.escape(delivery.get("address2")) %></p>
          <% } %>
          </div>
          <div class="delivery-request" id="delivery-request" data-saved-message="<%= ProductView.escape(savedMessage) %>" <%= hasDelivery ? "" : "hidden" %>>
            <label for="delivery-message-choice">배송 요청사항</label>
            <select id="delivery-message-choice">
              <option value="">배송 요청사항 선택</option><option>직접 받을게요 (부재 시 문 앞)</option><option>배송 전 연락 바랍니다</option><option>부재 시 경비실에 맡겨주세요</option><option>부재 시 문 앞에 놓아주세요</option><option>부재 시 택배함에 넣어주세요</option><option value="custom">직접 입력</option>
            </select>
            <input id="delivery-message-custom" aria-label="배송 요청사항 직접 입력" maxlength="500" placeholder="배송 요청사항을 입력해 주세요" hidden>
          </div>
        </section>
        <section class="order-block" aria-labelledby="order-items-heading">
          <div class="order-section-heading"><h2 id="order-items-heading">주문상품 <%= orderItems.size() %></h2><span class="order-delivery-date">택배배송</span></div>
          <% for (CartBean line : orderItems) {
            String orderImage = line.getImageUrl();
            if (orderImage == null || orderImage.isBlank()) orderImage = "assets/images/mock-product-0.svg";
            String orderImageSrc = orderImage.startsWith("https://") ? orderImage : orderContext + "/" + orderImage;
          %>
          <article class="order-product-row">
            <img src="<%= ProductView.escape(orderImageSrc) %>" alt="<%= ProductView.escape(line.getProductName()) %>">
            <div><div class="order-product-price"><%= orderMoney.format(line.getItemTotal()) %>원<span class="order-product-quantity"> / <%= line.getQuantity() %>개</span></div><a class="order-product-name" href="<%= orderContext %>/product.jsp?id=<%= java.net.URLEncoder.encode(line.getProductId(), "UTF-8") %>"><%= ProductView.escape(line.getProductName()) %></a>
            <% if (!line.isPurchasable()) { %><p class="order-feedback">상품 판매 상태 또는 재고를 확인해 주세요.</p><% } %>
            </div>
          </article>
          <% } %>
          <div class="order-note"><strong>확인해 주세요</strong><p>· 주문 시점에 따라 상품의 판매 상태와 재고가 달라질 수 있습니다.</p><p>· 상품 금액 30,000원 이상 구매 시 무료배송입니다.</p><p>· 주문 전 상품과 배송 정보를 확인해 주세요.</p></div>
        </section>
        
        <section class="order-block" aria-labelledby="coupon-heading">
          <div class="order-section-heading"><h2 id="coupon-heading">쿠폰 적용 <span>0</span></h2></div>
          <div class="coupon-row"><input aria-label="선택된 쿠폰" value="쿠폰을 선택해주세요" readonly><button class="order-small-button" type="button" id="open-coupon">선택</button></div>
        </section>
        <section class="order-block" aria-labelledby="point-heading">
          <div class="order-section-heading"><h2 id="point-heading">포인트 · 상품권</h2><span class="order-discount-total">0원</span></div>
          <div class="order-point-row">
    <label for="daiso-points">다이소 포인트 ⓘ</label>
    <div class="order-point-input">
        <input id="daiso-points" type="number" min="0" step="1" value="0"
               max="<%= Math.min(orderSubtotal,
                   ((Number) request.getAttribute("daisoPointBalance")).longValue()) %>">
        <span>P</span>
        <p class="order-point-balance">
            보유 <%= orderMoney.format(request.getAttribute("daisoPointBalance")) %>P
        </p>
    </div>
    <button id="use-all-daiso-points" class="order-small-button" type="button">
        전액사용
    </button>
</div>

<div class="order-point-row">
    <label for="mall-points">다이소몰 포인트 ⓘ</label>
    <div class="order-point-input">
        <input id="mall-points" value="0" disabled>
        <span>P</span>
        <p class="order-point-balance">보유 0P</p>
    </div>
    <button class="order-small-button" type="button" disabled>전액사용</button>
</div>

<div class="order-point-row">
    <label for="gift-balance">다이소 상품권 ⓘ</label>
    <div class="order-point-input">
        <input id="gift-balance" placeholder="보유 금액을 조회해 주세요" disabled>
        <p class="order-point-balance">보유 -원</p>
    </div>
    <button class="order-small-button" type="button" data-order-unavailable>
        조회하기
    </button>
</div>
          <div class="gift-register"><button class="order-small-button" type="button" data-order-unavailable>상품권 등록</button><span>상품권을 등록하고 편하게 쇼핑하세요.</span></div>
        </section>
        <section class="order-block" aria-labelledby="payment-heading">
          <div class="order-section-heading"><h2 id="payment-heading">결제수단<span class="order-required">*</span></h2><label class="order-remember"><input id="remember-payment" type="checkbox"> 이 결제 수단을 다음에도 사용</label></div>
          <div class="payment-methods" role="group" aria-label="결제수단 선택">
            <button class="payment-method" type="button" disabled><span class="payment-wordmark toss">●</span>토스 계좌이체</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark shinhan">SOL</span>신한 슈퍼SOL</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark samsung">SAMSUNG<br>pay</span>삼성페이</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark naver">N pay</span>네이버페이</button>
            <button class="payment-method" type="button" id="payment-kakao" aria-pressed="true"><span class="payment-wordmark kakao">● pay</span>카카오페이</button>
            <button class="payment-method" type="button" id="payment-toss-pay" aria-pressed="false"><span class="payment-wordmark toss" aria-hidden="true">toss pay</span>토스페이</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark payco">PAYCO</span>PAYCO</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark nh">NH pay</span>NH pay</button>
            <button class="payment-method" type="button" disabled><span class="payment-badge">다이소 삼성카드 혜택</span><span class="payment-wordmark samsung">삼성카드</span>삼성카드</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark lotte">롯데카드</span>롯데카드</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark" aria-hidden="true">▤</span>신용카드</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark">₩</span>가상계좌 입금</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark culture">CULTURE LAND</span>컬쳐랜드상품권</button>
            <button class="payment-method" type="button" disabled><span class="payment-wordmark">▱</span>금융 법인카드(무기명)</button>
          </div>
          <div class="payment-detail"><p><strong id="selected-payment-label">카카오페이</strong></p></div>
          <p id="selected-payment-help" class="payment-help" aria-live="polite">카카오페이로 결제할 수 있습니다.</p>
          <div class="order-agreements"><label><input id="order-consent" type="checkbox"><span>주문 상품과 배송 정보를 확인했으며, 주문 및 결제 진행에 동의합니다.</span></label></div>
        </section>
      </div>
      <aside class="order-summary" aria-label="결제 금액">
        <div class="order-summary-line"><span>총 상품 금액</span><span><%= orderMoney.format(orderSubtotal) %>원</span></div>
        <div class="order-summary-line"><span>총 배송비</span><span><%= orderMoney.format(orderShipping) %>원</span></div>
        <div class="order-summary-line sub"><span>└ 기본 배송비</span><span><%= orderMoney.format(orderShipping) %>원</span></div>
        <div class="order-summary-total"><span>결제 예상 금액</span><strong><%= orderMoney.format(orderTotal) %>원</strong></div>
        <details><summary>주문 정보 확인 후, 결제에 동의합니다.</summary><p>주문 상품, 수량, 결제 금액 및 배송지를 확인해 주세요. 결제 진행 시 상품 상태와 재고를 다시 확인합니다.</p></details>
        <button type="button" class="order-pay-button" id="order-pay"><%= orderMoney.format(orderTotal) %>원 결제하기</button>
        
      </aside>
    </div>
    <% } %>
    </main>
    <dialog class="delivery-dialog" id="delivery-dialog" aria-labelledby="delivery-dialog-title">
      <form id="delivery-form" class="delivery-dialog-frame" action="<%= orderContext %>/app/order/address" method="post" novalidate>
        <div class="delivery-dialog-header"><h2 id="delivery-dialog-title">배송지 등록</h2><button class="delivery-close" type="button" data-close-dialog aria-label="배송지 등록 닫기">×</button></div>
        <div class="delivery-form-scroll">
          <input type="hidden" name="csrfToken" value="<%= ProductView.escape(orderCsrf) %>">
          <div class="delivery-field"><label for="delivery-name">배송지명 <span class="order-required">*</span></label>
          <input id="delivery-name" name="addressName" maxlength="50" placeholder="배송지명을 입력해 주세요" value="<%= ProductView.escape(delivery.get("addressName")) %>" required><p class="field-error" hidden></p></div>
          <div class="delivery-field"><label for="delivery-recipient">수령인 이름 <span class="order-required">*</span></label>
          <input id="delivery-recipient" name="recipient" maxlength="100" placeholder="수령인 이름을 입력해 주세요" value="<%= ProductView.escape(draftRecipient) %>" autocomplete="name" required><p class="field-error" hidden></p></div>
          <div class="delivery-field"><label for="delivery-phone">수령인 휴대폰번호 <span class="order-required">*</span></label>
          <input id="delivery-phone" name="phone" type="tel" maxlength="30" placeholder="휴대폰번호를 입력해 주세요" value="<%= ProductView.escape(delivery.get("phone")) %>" autocomplete="tel" required><p class="field-error" hidden></p></div>
          <div class="delivery-field"><label for="delivery-postcode">주소 <span class="order-required">*</span></label>
          <div class="delivery-address-search"><input id="delivery-postcode" name="postcode" maxlength="5" inputmode="numeric" pattern="[0-9]{5}" aria-label="우편번호" value="<%= ProductView.escape(delivery.get("postcode")) %>" required>
          <button id="find-address" class="order-small-button" type="button">주소 찾기</button></div><input id="delivery-address1" class="delivery-address-line" name="address1" maxlength="255" aria-label="기본주소" value="<%= ProductView.escape(delivery.get("address1")) %>" required>
          <input id="delivery-address2" name="address2" maxlength="255" placeholder="상세주소를 입력해 주세요" aria-label="상세주소" value="<%= ProductView.escape(delivery.get("address2")) %>"><p class="field-error" hidden></p></div>
          <p id="delivery-feedback" class="order-feedback" role="alert"></p>
        </div>
        <div class="delivery-dialog-footer"><button class="delivery-save" type="submit">저장</button></div>
      </form>
    </dialog>
    <dialog class="delivery-dialog" id="coupon-dialog" aria-labelledby="coupon-dialog-title"><div class="delivery-dialog-frame"><div class="delivery-dialog-header"><h2 id="coupon-dialog-title">쿠폰 선택</h2><button type="button" class="delivery-close" data-close-dialog aria-label="쿠폰 선택 닫기">×</button></div><div class="coupon-empty">사용 가능한 쿠폰이 없습니다.</div><div class="delivery-dialog-footer"><button class="delivery-save" type="button" data-close-dialog>확인</button></div></div></dialog>
    <%@ include file="../common/footer.jspf" %>
    
    <!--카카오 api  --><script src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js" defer></script>
    <%if (request.getAttribute("tossClientKey")!= null){ %>
    <!-- toss api --><script src = "https://js.tosspayments.com/v2/standard" defer></script>
    <%} %>
    <script>
    document.addEventListener('DOMContentLoaded', function () {
        const openDelivery = document.getElementById('open-delivery');
        if (!openDelivery) return;
        const dialog = document.getElementById('delivery-dialog');
        const form = document.getElementById('delivery-form');
        const feedback = document.getElementById('delivery-feedback');
        const messageChoice = document.getElementById('delivery-message-choice');
        const customMessage = document.getElementById('delivery-message-custom');
        let addressSaved = <%= hasDelivery %>;
        const showNotice = function (text) { window.Daiso.ui.notice(text); };
        const savedMessage = document.getElementById('delivery-request').dataset.savedMessage;
        const preset = Array.from(messageChoice.options).find(function (option) { return option.value !== 'custom' && option.value === savedMessage; });
        if (preset) { messageChoice.value = savedMessage; }
        else if (savedMessage) { messageChoice.value = 'custom'; customMessage.value = savedMessage; customMessage.hidden = false; }
        let messageQueue = Promise.resolve();
        function saveMessage() {
            const data = new URLSearchParams();
            data.set('csrfToken',form.elements.csrfToken.value);
            data.set('deliveryMessage',messageChoice.value === 'custom' ? customMessage.value.trim() : messageChoice.value);
            messageQueue = messageQueue.then(async function () {
                try {
                    const response = await fetch('<%= orderContext %>/app/order/delivery-message',{method:'POST',body:data});
                    if (!response.ok) throw new Error('배송 요청사항 저장에 실패했습니다.');
                    const result = (await response.text()).trim();
                    if (result !== 'OK') throw new Error(result);
                } catch (error) { showNotice(error.message); }
            });
        }
        openDelivery.addEventListener('click', function () { dialog.showModal(); });
        const remember = document.getElementById('remember-payment');
        const kakaoPayment = document.getElementById('payment-kakao');
        const tossPay = document.getElementById('payment-toss-pay');
        const paymentLabel = document.getElementById('selected-payment-label');
        const paymentHelp = document.getElementById('selected-payment-help');
        const payButton = document.getElementById('order-pay');
     // 사용 포인트가 바뀌면 할인 금액, 결제 예상 금액, 결제 버튼을 함께 갱신한다.
        const pointInput = document.getElementById('daiso-points');
        const useAllPoints = document.getElementById('use-all-daiso-points');
        const originalTotal = <%= orderTotal %>;
        const maxPoints = Number(pointInput.max);
        const won = new Intl.NumberFormat('ko-KR');

        function updatePointAmount() {
            const entered = pointInput.valueAsNumber;
            const points = Number.isFinite(entered)
                ? Math.min(maxPoints, Math.max(0, Math.floor(entered)))
                : 0;

            pointInput.value = String(points);
            const payable = originalTotal - points;

            document.querySelector('#point-heading + .order-discount-total').textContent =
                '-' + won.format(points) + '원';
            document.querySelector('.order-summary-total strong').textContent =
                won.format(payable) + '원';
            payButton.textContent = won.format(payable) + '원 결제하기';
        }

        pointInput.addEventListener('input', updatePointAmount);
        useAllPoints.addEventListener('click', function () {
            pointInput.value = String(maxPoints);
            updatePointAmount();
        });
        let selectedPayment = 'kakao';

        function selectPayment(method) {
            if (selectedPayment === method || payButton.disabled) return;
            selectedPayment = method;
            const isTossPay = method === 'toss-pay';

            kakaoPayment.setAttribute('aria-pressed', String(!isTossPay));
            tossPay.setAttribute('aria-pressed', String(isTossPay));
            paymentLabel.textContent = isTossPay ? '토스페이' : '카카오페이';
            paymentHelp.textContent = isTossPay
                ? '토스페이 결제창 연결을 준비 중입니다.'
                : '카카오페이로 결제할 수 있습니다.';
            remember.disabled = isTossPay;
        }

        kakaoPayment.addEventListener('click', () => selectPayment('kakao'));
        tossPay.addEventListener('click', () => selectPayment('toss-pay'));
        try { remember.checked = localStorage.getItem('daiso.order.rememberKakao') === 'true'; } catch (ignored) { /* 저장소 차단 시 기본값 사용 */ }
        remember.addEventListener('change',function () { try { if (remember.checked) localStorage.setItem('daiso.order.rememberKakao','true'); else localStorage.removeItem('daiso.order.rememberKakao'); } catch (ignored) { /* 화면 선택은 유지 */ } });
        document.querySelectorAll('[data-close-dialog]').forEach(function (button) { button.addEventListener('click', function () { button.closest('dialog').close(); }); });
        //document.getElementById('open-coupon').addEventListener('click', function () { document.getElementById('coupon-dialog').showModal(); });
        document.querySelectorAll('[data-order-unavailable]').forEach(function (button) { button.addEventListener('click', function () { showNotice('상품권 기능은 준비 중입니다.'); }); });
        messageChoice.addEventListener('change', function () { customMessage.hidden = messageChoice.value !== 'custom'; if (!customMessage.hidden) customMessage.focus(); saveMessage(); });
        customMessage.addEventListener('change',saveMessage);
        document.getElementById('find-address').addEventListener('click', function () {
            const Postcode = window.kakao && window.kakao.Postcode || window.daum && window.daum.Postcode;
            if (!Postcode) { feedback.textContent = '주소 검색을 불러오지 못했습니다. 우편번호와 주소를 직접 입력해 주세요.'; return; }
            new Postcode({oncomplete: function (data) {
                form.elements.postcode.value = data.zonecode;
                form.elements.address1.value = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress;
                form.elements.address2.focus();
            }}).open();
        });
        const errors = {addressName:'배송지명을 입력해 주세요.',recipient:'수령인 이름을 입력해 주세요.',phone:'휴대폰번호를 확인해 주세요.',postcode:'5자리 우편번호를 입력해 주세요.',address1:'기본주소를 입력해 주세요.'};
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            feedback.textContent = '';
            form.querySelectorAll('.field-error').forEach(function (node) { node.hidden = true; });
            form.querySelectorAll('[aria-invalid]').forEach(function (node) { node.removeAttribute('aria-invalid'); });
            let firstInvalid = null;
            Object.keys(errors).forEach(function (name) {
                const field = form.elements[name];
                const text = field.value.trim();
                const invalid = !text || !field.checkValidity() || (name === 'phone' && !/^01[016789][0-9]{7,8}$/.test(text.replace(/[\s-]/g,'')));
                if (invalid) { field.setAttribute('aria-invalid','true'); const error = field.closest('.delivery-field').querySelector('.field-error'); error.textContent = errors[name]; error.hidden = false; if (!firstInvalid) firstInvalid = field; }
            });
            if (firstInvalid) { firstInvalid.focus(); return; }
            const save = form.querySelector('[type=submit]');
            if (save.disabled) return;
            save.disabled = true;
            try {
                const response = await fetch(form.action, {method:'POST',body:new URLSearchParams(new FormData(form))});
                if (!response.ok) throw new Error('배송지 저장 요청에 실패했습니다.');
                const result = (await response.text()).trim();
                if (result !== 'OK') throw new Error(result);
                const display = document.getElementById('delivery-display');
                display.replaceChildren();
                const title = document.createElement('p'); title.className = 'order-address-name';
                const name = document.createElement('strong'); name.textContent = form.elements.recipient.value.trim();
                const tag = document.createElement('span'); tag.className = 'order-address-tag'; tag.textContent = form.elements.addressName.value.trim(); title.append(name,tag);
                const phone = document.createElement('p'); phone.className = 'order-address-phone'; phone.textContent = form.elements.phone.value.trim();
                const address = document.createElement('p'); address.textContent = '[' + form.elements.postcode.value.trim() + '] ' + form.elements.address1.value.trim() + ' ' + form.elements.address2.value.trim();
                display.append(title,phone,address);
                document.getElementById('delivery-request').hidden = false;
                openDelivery.textContent = '⌖ 변경'; addressSaved = true; dialog.close(); showNotice('배송지가 저장되었습니다.');
            } catch (error) { feedback.textContent = error.message; } finally { save.disabled = false; }
        });
        //택배 주문서 결제버튼:선택한 결제수단으로 요청
        payButton.addEventListener('click', async function () {
            if (payButton.disabled) return;

            if (!addressSaved) {
                showNotice('배송지를 등록해 주세요.');
                dialog.showModal();
                return;
            }

            const consent = document.getElementById('order-consent');
            if (!consent.checked) {
                showNotice('주문 및 결제 진행에 동의해 주세요.');
                consent.focus();
                return;
            }

            const isTossPay = selectedPayment === 'toss-pay';
            if (isTossPay && typeof window.TossPayments !== 'function') {
                showNotice('토스페이 테스트 키와 SDK 설정을 확인해 주세요.');
                return;
            }

            payButton.disabled = true;

            try {
                await messageQueue;

                const data = new URLSearchParams();
                data.set('csrfToken', form.elements.csrfToken.value);
                data.set('consent', 'true');
                document.querySelectorAll('#order-selected-items input[name="cartItemId"]')
                    .forEach(input => data.append('cartItemId', input.value));
                const usedPoints = Number(pointInput.value);
                const expectedAmount = originalTotal - usedPoints;

                if (expectedAmount <= 0) {
                    throw new Error('현재 결제창에서는 결제할 금액이 1원 이상이어야 합니다.');
                }

                data.set('usedPoints', String(usedPoints));
                data.set('expectedAmount', String(expectedAmount));
                const contextPath = '<%= orderContext %>';
                const path = isTossPay ? '/app/payment/toss/ready' : '/app/payment/kakao/ready';
                const response = await fetch(contextPath + path, { method: 'POST', body: data });

                if (!response.ok) throw new Error('결제 준비 요청에 실패했습니다.');

                const result = await response.json();
                if (result.message) throw new Error(result.message);

                if (isTossPay) {
                    const payment = window.TossPayments(result.clientKey)
                        .payment({ customerKey: result.customerKey });
                    const callbackBase = location.origin + contextPath + '/app/payment/toss';

                    await payment.requestPayment({
                        method: 'CARD',
                        amount: { currency: 'KRW', value: result.amount },
                        orderId: result.orderId,
                        orderName: result.orderName,
                        successUrl: callbackBase + '/preview-success',
                        failUrl: callbackBase + '/preview-fail',
                        card: { flowMode: 'DIRECT', easyPay: 'TOSSPAY' }
                    });

                    payButton.disabled = false;
                    return;
                }

                if (!result.redirectUrl) throw new Error('결제창 주소를 받지 못했습니다.');
                window.location.assign(result.redirectUrl);
            } catch (error) {
                showNotice(error.message);
                payButton.disabled = false;
            }
        });
    });
    </script>
</body>
</html>
