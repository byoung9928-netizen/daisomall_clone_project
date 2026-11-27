<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,daiso.cart.db.CartBean,daiso.member.controller.MemberProfileInfo,daiso.product.controller.ProductView" %>
<%
    List<CartBean> pickupOrderItems = (List<CartBean>) request.getAttribute("pickupOrderItems");
    MemberProfileInfo pickupBuyer = (MemberProfileInfo) request.getAttribute("pickupBuyer");
    java.text.NumberFormat pickupMoney = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
    String pickupName = pickupBuyer == null ? "" : pickupBuyer.getName();
    String pickupPhone = pickupBuyer == null ? "" : pickupBuyer.getPhone();
    String pickupEmail = pickupBuyer == null ? "" : pickupBuyer.getEmail();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <title>매장픽업 주문서</title>
    <%@ include file="../common/head.jspf" %>
    <style>
        .pickup-checkout {
            --pickup-accent: var(--color-ink);
            --pickup-border: var(--color-line);
            --pickup-surface: var(--color-soft);
            --pickup-muted: var(--color-muted);
            --pickup-label-width: clamp(120px, 21%, 192px);
            padding-block: clamp(28px, 4vw, 48px) 88px;
            font-size: 16px;
        }
        .pickup-checkout-heading {
            display: flex;
            align-items: baseline;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 12px 24px;
            margin-bottom: clamp(32px, 4vw, 48px);
        }
        .pickup-checkout-heading .page-title {
            margin: 0;
            font-size: clamp(30px, 3vw, 40px);
            line-height: 1.2;
            text-align: start;
        }
        .pickup-back, .pickup-profile-link {
            color: var(--pickup-accent);
            text-decoration: underline;
            text-underline-offset: 3px;
        }
        .pickup-checkout-grid {
            display: grid;
            grid-template-columns: minmax(0, 3.1fr) minmax(272px, 1fr);
            align-items: start;
            gap: clamp(24px, 2.5vw, 36px);
        }
        .pickup-checkout-content {
            min-width: 0;
            display: grid;
            gap: clamp(44px, 5vw, 72px);
        }
        .pickup-checkout-content section { min-width: 0; }
        .pickup-checkout-content h2 {
            margin: 0 0 24px;
            font-size: clamp(22px, 2.3vw, 30px);
            line-height: 1.25;
            font-weight: 750;
        }
        .pickup-section-heading {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 10px 18px;
            flex-wrap: wrap;
            margin-bottom: 24px;
        }
        .pickup-section-heading h2 { margin: 0; }
        .pickup-details {
            margin: 0;
            border-top: 1px solid var(--pickup-border);
        }
        .pickup-details > div, .pickup-fields > label {
            display: grid;
            grid-template-columns: var(--pickup-label-width) minmax(0, 1fr);
            align-items: center;
            gap: 12px;
            padding-inline: 16px;
        }
        .pickup-details > div {
            min-height: 54px;
            padding-block: 12px;
            border-bottom: 1px solid var(--pickup-border);
        }
        .pickup-details dt, .pickup-fields > label span { color: var(--pickup-accent); }
        .pickup-details dd { margin: 0; overflow-wrap: anywhere; }
        .pickup-copy { display: inline-flex; gap: 8px; align-items: center; }
        .pickup-copy input { width: 18px; height: 18px; margin: 0; accent-color: var(--pickup-accent); }
        .pickup-fields { display: grid; gap: 18px; }
        .pickup-fields > label span { font-weight: 650; }
        .pickup-fields input {
            width: 100%;
            min-height: 48px;
            padding: 11px 16px;
            border: 1px solid var(--pickup-border);
            border-radius: 7px;
            background: #fff;
        }
        .pickup-fields input:disabled { color: var(--pickup-muted); background: var(--pickup-surface); }
        .pickup-store-info {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 16px;
            border: 1px solid var(--pickup-border);
            border-radius: 10px;
        }
        .pickup-store-info img { flex: 0 0 20px; object-fit: contain; }
        .pickup-store-info strong { font-size: 17px; overflow-wrap: anywhere; }
        .pickup-guide { margin-top: 28px; }
        .pickup-guide h3 { margin: 0 0 12px; font-size: 20px; }
        .pickup-note {
            margin: 12px 0 0;
            padding: 10px 16px;
            background: var(--pickup-surface);
            border-radius: 7px;
            color: var(--pickup-muted);
            font-size: 13px;
            line-height: 1.55;
        }
        .pickup-item-count { color: var(--pickup-muted); font-size: .75em; font-weight: 650; }
        .pickup-order-items { list-style: none; padding: 0; margin: 0; }
        .pickup-order-items li {
            display: grid;
            grid-template-columns: 80px minmax(0, 1fr);
            align-items: center;
            gap: 18px;
            padding-block: 16px;
            border-bottom: 1px solid var(--pickup-border);
        }
        .pickup-order-items li:first-child { padding-top: 0; }
        .pickup-order-items img {
            width: 80px;
            height: 106px;
            object-fit: contain;
            background: var(--pickup-surface);
            border-radius: 8px;
        }
        .pickup-item-price { margin: 0 0 4px; }
        .pickup-item-price strong { font-size: 18px; }
        .pickup-item-quantity { margin-inline-start: 8px; color: var(--pickup-muted); }
        .pickup-item-name { display: block; overflow-wrap: anywhere; }

        .pickup-checkout-total {
            position: sticky;
            top: 24px;
            padding: 24px 22px 20px;
            border: 1px solid var(--pickup-border);
            border-radius: 0;
            background: #fff;
            box-shadow: 0 4px 16px rgb(0 0 0 / 6%);
        }
        .pickup-checkout-total dl { margin: 0; }
        .pickup-checkout-total dl > div {
            display: flex;
            flex-wrap: wrap;
            justify-content: space-between;
            align-items: baseline;
            gap: 4px 12px;
        }
        .pickup-checkout-total dl > div + div { margin-top: 10px; }
        .pickup-checkout-total dd { margin: 0; margin-inline-start: auto; text-align: right; }
        .pickup-checkout-total .pickup-final {
            margin-top: 16px;
            padding-block: 14px;
            border-block: 1px solid var(--pickup-border);
            font-size: 18px;
            font-weight: 700;
        }
        .pickup-final dd { color: var(--color-red); font-size: 22px; }
        .pickup-order-review {
            padding-block: 3px;
            border-bottom: 1px solid var(--pickup-border);
        }
        .pickup-order-review summary {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            padding-block: 13px;
            color: var(--pickup-accent);
            font-weight: 650;
            list-style: none;
            cursor: pointer;
        }
        .pickup-order-review summary::-webkit-details-marker { display: none; }
        .pickup-order-review summary::after {
            content: "";
            width: 8px;
            height: 8px;
            margin-inline-end: 3px;
            border: solid currentColor;
            border-width: 0 2px 2px 0;
            transform: translateY(-2px) rotate(45deg);
        }
        .pickup-order-review[open] summary::after { transform: translateY(2px) rotate(225deg); }
        .pickup-order-review summary:focus-visible { outline: 2px solid var(--pickup-accent); outline-offset: 2px; }
        .pickup-order-preview {
            list-style: none;
            margin: 0 0 10px;
            padding: 10px 12px;
            border-radius: 7px;
            background: var(--pickup-surface);
            color: var(--pickup-muted);
            font-size: 13px;
            overflow-wrap: anywhere;
        }
        .pickup-order-preview li + li { margin-top: 4px; }
        .pickup-checkout-total .pickup-payment-status { margin: 10px 0 0; color: var(--pickup-muted); font-size: 13px; }

        .pickup-checkout-actions {
            display: flex;
            justify-content: center;
            border-top: 1px solid var(--pickup-border);
            padding-top: 24px;
        }
        .pickup-checkout-actions .button { width: min(100%, 360px); border-radius: 0; }
        .pickup-checkout .pickup-pay-button {
            min-height: 48px;
            border-color: #414141;
            background: #414141;
            color: #fff;
            font-weight: 700;
        }

        .pickup-checkout .pickup-checkout-total .button {
            width: 100%;
            margin-top: 16px;
            border-radius: 0;
        }
        .pickup-method-grid {
            list-style: none;
            padding: 0;
            margin: 0;
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 14px;
        }
        .pickup-method {
            min-width: 0;
            min-height: 112px;
            display: grid;
            grid-template-rows: 36px auto;
            justify-items: center;
            align-content: center;
            align-items: center;
            gap: 7px;
            padding: 14px 8px;
            border: 1px solid var(--pickup-border);
            border-radius: 0;
            background: #fff;
            text-align: center;
        }
        .pickup-method-option {
            position: relative;
            min-width: 0;
            display: grid;
        }
        .pickup-method-option label { cursor: pointer; }
        .pickup-method-option input:focus-visible + .pickup-method {
            outline: 2px solid var(--pickup-accent);
            outline-offset: 2px;
        }
        .pickup-method-option input:checked + .pickup-method {
            border-color: var(--pickup-accent);
            box-shadow: inset 0 0 0 1px var(--pickup-accent);
            background: var(--pickup-surface);
        }
        .pickup-method-mark {
            display: inline-grid;
            place-items: center;
            max-width: 100%;
            color: var(--pickup-accent);
            font: 800 clamp(12px, 1.3vw, 16px)/1.2 system-ui, sans-serif;
        }
        .pickup-method-mark--kakao { padding: 4px 9px; border-radius: 999px; background: #ffdf00; color: #171e24; }
        .pickup-method-mark--sol { padding: 5px 7px; border-radius: 7px; background: #1457ed; color: #fff; }
        .pickup-method-mark--toss { color: #135ec4; }
        .pickup-method-mark--samsung { color: #143c99; }
        .pickup-method-mark--naver { color: #087941; }
        .pickup-method-mark--payco { color: #b51932; }
        .pickup-method-mark--nh { color: #075da1; }
        .pickup-method-mark--lotte { color: #9a2348; }
        .pickup-method-mark--credit, .pickup-method-mark--bank { font-size: 20px; }
        .pickup-method-name { line-height: 1.35; overflow-wrap: anywhere; }
        .pickup-method-option input:checked + .pickup-method .pickup-method-name { font-weight: 750; }

        @media (min-width: 841px) and (max-width: 1060px) {
            .pickup-method-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
        }
        @media (max-width: 840px) {
            .pickup-checkout { padding-bottom: 48px; }
            .pickup-checkout-grid { grid-template-columns: minmax(0, 1fr); }
            .pickup-checkout-total { position: static; }
            .pickup-checkout-actions { display: none; }
        }
        @media (max-width: 620px) {
            .pickup-checkout { --pickup-label-width: 78px; font-size: 15px; }
            .pickup-details > div, .pickup-fields > label { padding-inline: 4px; }
            .pickup-fields { gap: 12px; }
            .pickup-order-items li { grid-template-columns: 64px minmax(0, 1fr); gap: 12px; }
            .pickup-order-items img { width: 64px; height: 84px; }
            .pickup-method-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
            .pickup-method { min-height: 96px; }
        }

    .order-point-row{display:grid;grid-template-columns:280px minmax(0,1fr) 120px;gap:12px;align-items:start;margin:0 0 24px;padding-left:20px}.order-point-row>label{font-size:14px;padding-top:14px}.order-point-input{position:relative}.order-point-input input{width:100%;height:48px;border:1px solid #e1e4e9;background:#f6f7f9;padding:0 32px 0 16px;color:#a6aeb8;font-size:14px}.order-point-input>span{position:absolute;right:16px;top:15px;font-size:14px;color:#697688}.order-point-balance{font-size:12px;color:#6c7c8f;margin:10px 12px 0}
.pickup-point-heading{display:flex;justify-content:space-between;align-items:baseline}.pickup-point-heading span{color:#e60023}.order-point-row button{border:1px solid #e1e4e9;background:#fff;min-height:48px;font-size:14px;font-weight:700;cursor:pointer}@media(max-width:1100px){.order-point-row{grid-template-columns:170px minmax(0,1fr) 100px}}@media(max-width:800px){.order-point-row{grid-template-columns:minmax(0,1fr) 90px;padding:0}.order-point-row label{grid-column:1/-1}}
</style>
</head>
<body>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <main class="container pickup-checkout" id="main-content">
        <div class="pickup-checkout-heading">
            <h1 class="page-title">매장픽업 주문서</h1>
            <a class="pickup-back" href="${pageContext.request.contextPath}/app/cart?tab=pickup">픽업 장바구니로 돌아가기</a>
        </div>
        <% if (pickupOrderItems == null) { %>
            <p role="alert"><%= ProductView.escape(request.getAttribute("pickupOrderError")) %></p>
        <% } else { %>
            <%-- ===== 픽업 주문서 내용. 결제 연결 전에는 주문 정보를 확인한다. ===== --%>
            <%
                long pickupPaymentAmount = ((Number) request.getAttribute("pickupSubtotal")).longValue()
                        + ((Number) request.getAttribute("pickupFee")).intValue();
                String pickupPaymentLabel = pickupMoney.format(pickupPaymentAmount) + "원";
            %>
            <div class="pickup-checkout-grid">
                <div class="pickup-checkout-content">
                    <section aria-labelledby="pickup-buyer-title">
                        <h2 id="pickup-buyer-title">주문자</h2>
                        <dl class="pickup-details">
                            <div><dt>주문인</dt><dd><%= ProductView.escape(pickupName) %></dd></div>
                            <div><dt>휴대폰</dt><dd><%= ProductView.escape(pickupPhone) %></dd></div>
                            <div><dt>이메일</dt><dd><%= ProductView.escape(pickupEmail) %></dd></div>
                        </dl>
                        <p class="pickup-note"><a class="pickup-profile-link" href="${pageContext.request.contextPath}/app/member/profile">회원정보에서 주문자 정보 수정</a></p>
                    </section>
                    <section aria-labelledby="pickup-recipient-title">
                        <div class="pickup-section-heading">
                            <h2 id="pickup-recipient-title">수령인</h2>
                            <label class="pickup-copy"><input type="checkbox" id="pickup-same-buyer" checked> 주문자 정보와 동일</label>
                        </div>
                        <div class="pickup-fields">
                            <label><span>이름</span><input id="pickup-recipient-name" name="recipient" autocomplete="name"
                                value="<%= ProductView.escape(pickupName) %>" disabled></label>
                            <label><span>휴대폰</span><input id="pickup-recipient-phone" name="phone" type="tel" autocomplete="tel"
                                value="<%= ProductView.escape(pickupPhone) %>" disabled></label>
                        </div>
                    </section>
                    <section aria-labelledby="pickup-store-title">
                        <h2 id="pickup-store-title">픽업매장 정보</h2>
                        <div class="pickup-store-info">
                            <img src="${pageContext.request.contextPath}/assets/images/home/service-pickup.svg"
                                 alt="" width="20" height="20">
                            <strong><%= ProductView.escape(request.getAttribute("pickupStoreName")) %></strong>
                        </div>
                        <div class="pickup-guide">
                            <h3>픽업 안내</h3>
                            <dl class="pickup-details">
                                <div><dt>수령 장소</dt><dd><%= ProductView.escape(request.getAttribute("pickupStoreName")) %></dd></div>
                                <div><dt>수령 방법</dt><dd>매장 방문 수령</dd></div>
                            </dl>
                            <p class="pickup-note">방문할 매장과 수령인 연락처를 확인해 주세요.</p>
                        </div>
                    </section>
                    
                    <section aria-labelledby="pickup-order-items-title">
                        <h2 id="pickup-order-items-title">주문상품 <span class="pickup-item-count"><%= pickupOrderItems.size() %>개</span></h2>
                        <ul class="pickup-order-items">
                        <% for (CartBean pickupItem : pickupOrderItems) { %>
                            <li>
                                <img src="<%= ProductView.image(request.getContextPath(), pickupItem.getImageUrl()) %>" alt="" width="80" height="106">
                                <div>
                                    <p class="pickup-item-price">
                                        <strong><%= pickupMoney.format(pickupItem.getItemTotal()) %>원</strong>
                                        <span class="pickup-item-quantity"><%= pickupItem.getQuantity() %>개</span>
                                    </p>
                                    <span class="pickup-item-name"><%= ProductView.escape(pickupItem.getProductName()) %></span>
                                </div>
                            </li>
                        <% } %>
                        </ul>
                    </section>
                    <section aria-labelledby="pickup-point-title">
<div class="pickup-point-heading"><h2 id="pickup-point-title">다이소 포인트</h2><span id="pickup-discount">0원</span></div>
<div class="order-point-row">
    <label for="daiso-points">다이소 포인트 ⓘ</label>
    <div class="order-point-input">
        <input id="daiso-points" type="number" min="0" step="1" value="0"
               max="<%= Math.min(((Number) request.getAttribute("pickupSubtotal")).longValue(),
                   ((Number) request.getAttribute("pickupPointBalance")).longValue()) %>">
        <span>P</span>
        <p class="order-point-balance">
            보유 <%= pickupMoney.format(request.getAttribute("pickupPointBalance")) %>P
        </p>
    </div>
    <button id="use-all-daiso-points" class="order-small-button" type="button">
        전액사용
    </button>
</div>
</section>
<section aria-labelledby="pickup-payment-method-title">
                        <h2 id="pickup-payment-method-title">결제수단</h2>
                        
                        <%-- ===== 결제수단 타일 ===== --%>
                        <ul class="pickup-method-grid" aria-label="결제수단">
                            <li class="pickup-method-option">
                                <input class="visually-hidden" id="pickup-kakao-pay" type="radio" name="pickup-payment-method" value="kakao">
                                <label class="pickup-method" for="pickup-kakao-pay">
                                    <span class="pickup-method-mark pickup-method-mark--kakao" aria-hidden="true">● pay</span>
                                    <span class="pickup-method-name">카카오페이</span>
                                </label>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--toss" aria-hidden="true">toss</span>
                                <span class="pickup-method-name">토스 계좌이체</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--sol" aria-hidden="true">SOL</span>
                                <span class="pickup-method-name">신한 슈퍼SOL</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--samsung" aria-hidden="true">Samsung Pay</span>
                                <span class="pickup-method-name">삼성페이</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--naver" aria-hidden="true">N pay</span>
                                <span class="pickup-method-name">네이버페이</span>
                            </li>
                            <li class="pickup-method-option">
                                <input class="visually-hidden" id="pickup-toss-pay" type="radio" name="pickup-payment-method" value="toss-pay">
                                <label class="pickup-method" for="pickup-toss-pay">
                                    <span class="pickup-method-mark pickup-method-mark--toss" aria-hidden="true">toss pay</span>
                                    <span class="pickup-method-name">토스페이</span>
                                </label>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--payco" aria-hidden="true">PAYCO</span>
                                <span class="pickup-method-name">PAYCO</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--nh" aria-hidden="true">NH pay</span>
                                <span class="pickup-method-name">NH pay</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--samsung" aria-hidden="true">SAMSUNG</span>
                                <span class="pickup-method-name">삼성카드</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--lotte" aria-hidden="true">LOTTE</span>
                                <span class="pickup-method-name">롯데카드</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--credit" aria-hidden="true">▤</span>
                                <span class="pickup-method-name">신용카드</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--bank" aria-hidden="true">₩</span>
                                <span class="pickup-method-name">가상계좌 입금</span>
                            </li>
                            <li class="pickup-method">
                                <span class="pickup-method-mark pickup-method-mark--credit" aria-hidden="true">▣</span>
                                <span class="pickup-method-name">공용 법인카드</span>
                            </li>
                        </ul>
                        <p class="pickup-note" id="pickup-payment-note" aria-live="polite">결제수단을 선택해 주세요.</p>
                    </section>
                    <div class="order-agreements"><label><input id="order-consent" type="checkbox"><span>주문 상품과 배송 정보를 확인했으며, 주문 및 결제 진행에 동의합니다.</span></label></div>
                    
                    <%-- ===== 주문서 하단 결제 버튼 ===== --%>
                    <div class="pickup-checkout-actions">
                        <button class="button primary pickup-pay-button" type="button" data-pickup-pay>
                            <%= pickupPaymentLabel %> 결제하기
                        </button>
                    </div>
                </div>
                <aside class="pickup-checkout-total" aria-label="결제 요약">
                    <dl>
                        <div><dt>총 상품 금액</dt><dd><%= pickupMoney.format(request.getAttribute("pickupSubtotal")) %>원</dd></div>
                        <div><dt>픽업 수수료</dt><dd><%= pickupMoney.format(request.getAttribute("pickupFee")) %>원</dd></div>
                        <div class="pickup-final">
                            <dt>결제 예상 금액</dt>
                            <dd><%= pickupPaymentLabel %></dd>
                        </div>
                    </dl>
                    <details class="pickup-order-review">
                        <summary>주문 정보 확인</summary>
                        <ul class="pickup-order-preview">
                            <li>픽업매장 <strong><%= ProductView.escape(request.getAttribute("pickupStoreName")) %></strong></li>
                            <li>주문상품 <strong><%= pickupOrderItems.size() %>종</strong></li>
                        </ul>
                    </details>
                    <button class="button primary pickup-pay-button" type="button" aria-describedby="pickup-payment-status" data-pickup-pay>
                        <%= pickupPaymentLabel %> 결제하기
                    </button>
                    <p id="pickup-payment-status" class="pickup-payment-status">
                        픽업 결제 준비 중<br>
                        매장 재고는 주문서 조회 시점 기준이에요.
                    </p>
                </aside>
            </div>
            <%--토스페이 결제 준비 요청에 보낼 매장과 선택 상품 --%>
            <form id="pickup-toss-order" hidden
      data-client-key="<%= ProductView.escape(request.getAttribute("tossClientKey")) %>">
            <%--같은 주문서에 결제버튼을 두번눌러도 같은 요청번호를 사용 --%>
            <input type="hidden" name="checkoutToken" value="<%=java.util.UUID.randomUUID() %>">
            <input type="hidden" name="csrfToken"
            				value="<%=ProductView.escape(request.getAttribute("orderCsrf")) %>">
            <input type="hidden" name="storeId"
            				value="<%=ProductView.escape(pickupOrderItems.get(0).getStoreId()) %>">
            <input type="hidden" name="expectedAmount" value="<%=pickupPaymentAmount %>">
            
            <%for(CartBean item : pickupOrderItems){ %>
            	<input type="hidden" name="productId"
            				value="<%=ProductView.escape(item.getProductId()) %>">
            <%} %>
            
            </form>
            <%--매장 픽업 토스페이 결제창 sdk로드 --%>
            <% if(request.getAttribute("tossClientKey")!=null){ %>
            	<script src="https://js.tosspayments.com/v2/standard" defer></script>
            	<%} %>
            <script>
                (() => {
                    // 사용 포인트와 픽업 결제 예정 금액을 함께 갱신한다.
                    const points = document.getElementById("daiso-points");
                    const expected = document.querySelector('#pickup-toss-order [name="expectedAmount"]');
                    const original = <%= pickupPaymentAmount %>;
                    const money = new Intl.NumberFormat("ko-KR");
                    function updatePoints() {
                        const value = Math.min(Number(points.max), Math.max(0, Math.floor(points.valueAsNumber || 0)));
                        points.value = String(value);
                        expected.value = String(original - value);
                        const label = money.format(original - value) + "원";
                        document.getElementById("pickup-discount").textContent = value ? "-" + money.format(value) + "원" : "0원";
                        document.querySelector(".pickup-final dd").textContent = label;
                        document.querySelectorAll("[data-pickup-pay]").forEach(button => button.textContent = label + " 결제하기");
                    }
                    points.addEventListener("input", updatePoints);
                    document.getElementById("use-all-daiso-points").addEventListener("click", () => {
                        points.value = points.max;
                        updatePoints();
                    });
                    window.addEventListener("pageshow", updatePoints);
                    const paymentOptions = Array.from(document.querySelectorAll('input[name="pickup-payment-method"]'));
                    const paymentNames = { kakao: "카카오페이", "toss-pay": "토스페이" };
                    const paymentNote = document.getElementById("pickup-payment-note");
					//현재 수령인 정보를 포함한 픽업 결제 요청 만들기
					function buildPickupTossRequest(){
						const form=document.getElementById("pickup-toss-order");
						const body = new URLSearchParams(new FormData(form));
						
						body.set("usedPoints", points.value);
                        body.set("recipient", document.getElementById("pickup-recipient-name").value.trim());
						body.set("phone", document.getElementById("pickup-recipient-phone").value.trim());
						return body;
						
					}
                    function selectedPaymentName() {
                        const selected = paymentOptions.find(option => option.checked);
                        return selected ? paymentNames[selected.value] : "";
                    }

                    function updatePaymentNote() {
                        const name = selectedPaymentName();
                        paymentNote.textContent = name
                            ? "선택한 결제수단 · " + name
                            : "결제수단을 선택해 주세요.";
                    }

                    paymentOptions.forEach(option => option.addEventListener("change", updatePaymentNote));
                    window.addEventListener("pageshow", updatePaymentNote);
                    updatePaymentNote();

                    document.querySelectorAll("[data-pickup-pay]").forEach(button => {
                        button.addEventListener("click", async() => {
                        	const consent = document.getElementById('order-consent');
                            if (!consent.checked) {
                                window.Daiso.ui.notice('주문 및 결제 진행에 동의해 주세요.');
                                consent.focus();
                                return;
                            }
                        	const name = selectedPaymentName();
                            if (!name) {
                                window.Daiso.ui.notice("결제수단을 선택해 주세요.");
                                paymentOptions[0].focus();
                                return;
                            }
                            
                            const isKakao = document.getElementById("pickup-kakao-pay").checked;

                            try {
                            	// ===== 픽업 주문 저장 전에 토스 테스트 결제창 설정 확인 =====
                            	const clientKey = document.getElementById("pickup-toss-order").dataset.clientKey;
                            	if (!isKakao && (!clientKey || !clientKey.startsWith("test_ck_")
                            	        || typeof window.TossPayments !== "function")) {
                            	    window.Daiso.ui.notice("토스페이 테스트 키와 SDK 설정을 확인해 주세요.");
                            	    return;
                            	}
                            	// ===== 선택한 결제수단과 픽업 주문을 함께 전송 =====
                            	const body = buildPickupTossRequest();
                            	body.set("paymentMethod", isKakao ? "KAKAOPAY" : "TOSSPAY");

                            	const response = await fetch(
                            	    "<%= request.getContextPath() %>/app/payment/toss/pickup/check",
                            	    { method: "POST", body }
                            	);
                                if (!response.ok) {
                                    throw new Error("픽업 주문 확인 실패");
                                }

                                const result = await response.json();
                                if (result.message) {
                                    window.Daiso.ui.notice(result.message);
                                    return;
                                }
                                if (isKakao) {
                                    if (!result.redirectUrl) {
                                        window.Daiso.ui.notice("카카오페이 결제창 주소를 받지 못했습니다.");
                                        return;
                                    }
                                    window.location.assign(result.redirectUrl);
                                    return;
                                }

                             // ===== 서버에 저장한 픽업 주문번호와 금액으로 토스페이 결제창 열기 =====
                                const sdk = window.TossPayments(clientKey);
                                const payment = sdk.payment({ customerKey: window.TossPayments.ANONYMOUS });
                                const callbackBase = window.location.origin
                                        + "<%= request.getContextPath() %>/app/payment/toss";

                                await payment.requestPayment({
                                    method: "CARD",
                                    amount: { currency: "KRW", value: result.amount },
                                    orderId: result.orderId,
                                    orderName: "매장픽업 주문",
                                    successUrl: callbackBase + "/pickup/auth-result",
                                    failUrl: callbackBase + "/preview-fail",
                                    card: { flowMode: "DIRECT", easyPay: "TOSSPAY" }
                                });
                                
                            } catch (e) {
                                window.Daiso.ui.notice("픽업 주문을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.");
                            }
                        });
                    });


                    const same = document.getElementById("pickup-same-buyer");
                    const fields = ["pickup-recipient-name", "pickup-recipient-phone"]
                        .map(id => document.getElementById(id));

                    const syncRecipient = () => {
                        fields.forEach(field => {
                            field.disabled = same.checked;
                            if (same.checked) field.value = field.defaultValue;
                        });
                    };

                    same.addEventListener("change", () => {
                        syncRecipient();
                        if (!same.checked) {
                            fields.forEach(field => { field.value = ""; });
                            fields[0].focus();
                        }
                    });
                    window.addEventListener("pageshow", syncRecipient);
                    syncRecipient();
                })();
            </script>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
