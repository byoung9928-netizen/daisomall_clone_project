<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="daiso.cart.db.CartBean" %>

<%
    List<CartBean> cartList =
        (List<CartBean>) request.getAttribute("cartList");

	long subtotal = 0;

	if (cartList != null) {
    	for (CartBean cart : cartList) {
        	subtotal += cart.getItemTotal();
   		}	
	}

	long shippingFee =
    	subtotal > 0 && subtotal < 30000 ? 3000 : 0;

	long totalAmount = subtotal + shippingFee;
%>
<!DOCTYPE html>
<html lang="ko">
<head>
<%@ include file="../common/head.jspf" %>
<style>
.cart-screen{padding-top:40px;padding-bottom:80px}.cart-screen .page-title{margin:0 0 36px;font-size:32px;font-weight:800}.cart-board{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:28px;align-items:start}.cart-delivery-tab{height:49px;border:1px solid #171e24;background:#171e24;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:16px}.cart-delivery-tab svg{width:22px;height:22px;margin-right:9px;fill:currentColor}.cart-empty{min-height:400px;display:flex;align-items:center;justify-content:center;flex-direction:column;text-align:center}.cart-empty-icon{border:3px solid #c7cbd1;border-radius:50%;width:68px;height:68px;display:grid;place-items:center;color:#c7cbd1;font-size:46px;font-weight:500;line-height:1;margin-bottom:26px}.cart-empty p{color:#6b7785;margin:0 0 28px}.cart-shop-button,.cart-order-button{display:flex;align-items:center;justify-content:center;min-height:48px;background:#171e24;color:white;text-decoration:none;font-weight:700;border:0;padding:12px 20px}.cart-payment{border:1px solid #e2e5e9;padding:24px;background:#fff}.cart-address-title{display:flex;justify-content:space-between;align-items:center;gap:10px}.cart-address-title h2{font-size:17px;margin:0}.cart-address-link{border:1px solid #e2e5e9;padding:5px 10px;font-size:12px;color:#394552;text-decoration:none}.cart-address-message{font-size:14px;color:#6b7785;margin:18px 0}.cart-price-lines{border-top:1px solid #e2e5e9;padding:17px 0}.cart-price-line{display:flex;justify-content:space-between;gap:12px;font-size:14px;margin:0 0 12px}.cart-price-line:last-child{margin-bottom:0}.cart-base-shipping{color:#778291}.cart-grand-total{border-top:1px solid #e2e5e9;border-bottom:1px solid #e2e5e9;padding:19px 0;display:flex;justify-content:space-between;align-items:center;font-weight:700}.cart-grand-total strong{color:#e60020;font-size:20px}.cart-order-button{width:100%;margin-top:16px;box-sizing:border-box}.cart-order-button:disabled{background:#e9ebef;color:#b9bec6;cursor:default}.cart-policy{list-style:none;padding:0;margin:16px 0 0;font-size:12px;color:#748090;line-height:1.7}.cart-policy li{padding-left:10px;position:relative;margin-bottom:8px}.cart-policy li:before{content:'·';position:absolute;left:0}.cart-screen .cart-row{display:grid;grid-template-columns:88px minmax(0,1fr) auto;gap:20px;align-items:center;border-bottom:1px solid #e2e5e9;padding:24px 0}.cart-screen .cart-row img{width:88px;height:88px;object-fit:contain}.cart-product-info p{margin:0 0 8px}.cart-product-info a{color:inherit;text-decoration:none}.cart-quantity{font-size:14px;color:#748090}.cart-item-price{text-align:right;white-space:nowrap}.cart-item-price small{display:block;margin-top:8px;font-weight:400;color:#748090}.cart-screen .error-message:empty{display:none}@media(max-width:800px){.cart-board{grid-template-columns:1fr}.cart-screen{padding-top:24px}.cart-screen .page-title{font-size:26px;margin-bottom:24px}.cart-empty{min-height:320px}.cart-payment{width:100%;box-sizing:border-box}.cart-screen .cart-row{gap:12px;grid-template-columns:64px minmax(0,1fr) auto}.cart-screen .cart-row img{width:64px;height:64px}}
/* 장바구니 본문 비율: 최대 1280px, 상품 영역 / 간격 28px / 결제 패널 300px */
.cart-screen.container{width:calc(100% - 48px);max-width:1280px;margin-left:auto;margin-right:auto;box-sizing:border-box;padding-left:0;padding-right:0;padding-top:40px}
.cart-screen .page-title{font-size:38px;line-height:1.3;margin-bottom:40px}
.cart-board{grid-template-columns:minmax(0,1fr) 300px;gap:28px}
.cart-delivery-tab{box-sizing:border-box;height:48px}
.cart-empty{min-height:420px;box-sizing:border-box;padding-top:18px}
.cart-payment{box-sizing:border-box;width:300px;padding:24px;min-height:468px}
.cart-price-lines{padding:20px 0}
.cart-grand-total{padding:18px 0}
@media(max-width:800px){.cart-screen.container{width:calc(100% - 32px);padding-top:24px}.cart-screen .page-title{font-size:28px;margin-bottom:28px}.cart-board{grid-template-columns:minmax(0,1fr)}.cart-payment{width:100%;min-height:0}.cart-empty{min-height:340px;padding-top:0}}
.cart-shipping-progress{border:1px solid #e2e5e9;margin-top:24px;padding:24px}.cart-progress-top,.cart-progress-caption{display:flex;justify-content:space-between;align-items:center;gap:12px;font-size:14px}.cart-progress-top{color:#718092}.cart-progress-top a{color:inherit;text-decoration:none}.cart-progress-caption{margin-top:12px}.cart-progress-caption span{color:#718092}.cart-shipping-progress progress{display:block;width:100%;height:8px;border:0;border-radius:8px;overflow:hidden;margin-top:16px;appearance:none;background:#e9ecf2}.cart-shipping-progress progress::-webkit-progress-bar{background:#e9ecf2;border-radius:8px}.cart-shipping-progress progress::-webkit-progress-value{background:linear-gradient(90deg,#ffb1bb,#e60020);border-radius:8px}.cart-shipping-progress progress::-moz-progress-bar{background:#e60020}.cart-selection-toolbar{display:flex;justify-content:space-between;align-items:center;min-height:74px;font-size:13px}.cart-selection-toolbar label{display:flex;align-items:center;gap:7px}.cart-screen input[type=checkbox]{appearance:auto;width:20px;height:20px;accent-color:#171e24;margin:0;flex-shrink:0}.cart-selection-toolbar button{border:0;background:none;color:#8b949f;font-size:13px}.cart-product-table{width:100%;border-collapse:collapse;table-layout:fixed;border-top:2px solid #171e24}.cart-col-check{width:32px}.cart-col-price{width:130px}.cart-col-quantity{width:130px}.cart-col-total{width:120px}.cart-col-delete{width:34px}.cart-product-table th{height:54px;background:#f5f6f8;border-bottom:1px solid #dfe3e8;font-size:14px;text-align:center}.cart-product-table td{padding:24px 8px;vertical-align:middle}.cart-product-table .cart-check-cell{padding-left:4px;vertical-align:top}.cart-table-product{display:flex;align-items:center;gap:16px;text-decoration:none;color:#171e24;font-size:13px;line-height:1.6}.cart-table-product img{width:80px;height:108px;object-fit:contain;background:#f5f5f5;flex-shrink:0}.cart-table-money{text-align:center;font-size:17px;font-weight:700;white-space:nowrap}.cart-stepper{display:flex;align-items:center;justify-content:space-between;border:1px solid #e2e5e9;width:118px;height:36px;box-sizing:border-box;margin:auto;font-size:14px;font-weight:700}.cart-stepper button{border:0;background:none;width:34px;height:34px;font-size:18px;color:#a0a7b0;padding:0}.cart-remove{border:0;background:none;font-size:28px;color:#9ca5af;padding:0;line-height:1}.cart-cautions{background:#f6f7f9;border-radius:3px;padding:18px 20px;color:#758395;font-size:12px;line-height:1.7}.cart-cautions p{margin:4px 0 0}.cart-table-wrap{overflow-x:auto}@media(max-width:1100px){.cart-col-price{width:85px}.cart-col-quantity{width:105px}.cart-col-total{width:90px}.cart-stepper{width:96px}.cart-table-product{gap:8px}.cart-table-product img{width:60px;height:80px}.cart-table-money{font-size:14px}}@media(max-width:800px){.cart-product-table{min-width:620px}.cart-shipping-progress{padding:18px}.cart-progress-caption{font-size:12px}}
</style>
</head>
<body>
<a class="skip-link" href="#main-content">본문 바로가기</a>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/navigation.jspf" %>
<% boolean hasCartItems = cartList != null && !cartList.isEmpty();
   java.text.NumberFormat cartMoney = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
   String cartContext = request.getContextPath(); %>
<main id="main-content" class="container shop-page cart-screen" data-page="cart" tabindex="-1">
<h1 class="page-title">장바구니</h1>
<p class="error-message" role="alert">${cartError}</p>
<div class="cart-board">
<section aria-label="택배배송 상품">
<div class="cart-delivery-tab"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 5h12v10h1V8h4l3 4v6h-2a3 3 0 0 1-6 0H9a3 3 0 0 1-6 0H2V5zm15 5v3h3l-2-3h-1zM6 16a1 1 0 1 0 0 2 1 1 0 0 0 0-2zm11 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2z"/></svg>택배배송</div>
<% if (!hasCartItems) { %>
<div class="cart-empty">
<span class="cart-empty-icon" aria-hidden="true">!</span>
<p>장바구니에 담긴 상품이 없습니다.</p>
<a class="cart-shop-button" href="<%= cartContext %>/list.jsp">쇼핑하러 가기</a>
</div>
<% } else { %>
<div class="cart-shipping-progress">
<div class="cart-progress-top"><span>택배배송</span><a href="<%= cartContext %>/list.jsp">＋ 상품 더 담기</a></div>
<progress max="30000" value="<%= Math.min(subtotal, 30000) %>" aria-label="무료배송 기준 달성 금액"></progress>
<div class="cart-progress-caption"><strong><%= subtotal < 30000 ? cartMoney.format(30000 - subtotal) + "원 더 담으면! 무료배송" : "무료배송 금액을 달성했어요" %></strong><span>30,000원</span></div>
</div>
<div class="cart-selection-toolbar"><label><input id="cart-select-all" type="checkbox" checked> 전체 선택 <span id="cart-selected-count"><%= cartList.size() %>/<%= cartList.size() %>개</span></label><button id="cart-delete-selected" type="button">선택삭제</button></div>
<div class="cart-table-wrap">
<table class="cart-product-table">
<colgroup><col class="cart-col-check"><col><col class="cart-col-price"><col class="cart-col-quantity"><col class="cart-col-total"><col class="cart-col-delete"></colgroup>
<thead><tr><th colspan="2" scope="col">상품정보</th><th scope="col">상품금액</th><th scope="col">수량</th><th scope="col">총 금액</th><th scope="col"><span class="visually-hidden">삭제</span></th></tr></thead>
<tbody>
<% for (CartBean cart : cartList) {
String cartImage = cart.getImageUrl();
if (cartImage == null || cartImage.isBlank()) cartImage = "assets/images/mock-product-0.svg";
String cartImageSrc = cartImage.startsWith("https://") ? cartImage : cartContext + "/" + cartImage;
String cartProductName = cart.getProductName() == null ? "상품" : cart.getProductName();
%>
<tr data-cart-amount="<%= cart.getItemTotal() %>">
<td class="cart-check-cell"><input class="cart-item-check" type="checkbox" value="<%=cart.getCartItemId() %>" checked aria-label="상품 선택"></td>
<td><a class="cart-table-product" href="<%= cartContext %>/product.jsp?id=<%= java.net.URLEncoder.encode(cart.getProductId(), "UTF-8") %>"><img src="<%= daiso.product.controller.ProductView.escape(cartImageSrc) %>" alt=""><span><%= daiso.product.controller.ProductView.escape(cartProductName) %></span></a></td>
<td class="cart-table-money"><%= cartMoney.format(cart.getPrice()) %>원</td>
<td><div class="cart-stepper" data-cart-item-id="<%=cart.getCartItemId() %>">
	<button type="button" data-quantity-change="-1" aria-label="수량 줄이기" <%=cart.getQuantity()<=1?"disabled":"" %>>−</button>
	<span><%= cart.getQuantity() %></span>
	<button type="button" data-quantity-change="1" aria-label="수량 늘리기"<%=cart.getQuantity()>=99 ||cart.getQuantity()>=cart.getStockQuantity()||!"AVAILABLE".equals(cart.getProductStatus()) ? "disabled" : "" %>>＋</button></div></td>
<td class="cart-table-money"><%= cartMoney.format(cart.getItemTotal()) %>원</td>
<td><button class="cart-remove" type="button" data-cart-delete="<%=cart.getCartItemId() %>" aria-label="상품 삭제" >×</button></td>
</tr>
<% } %>
</tbody></table></div>
<div class="cart-cautions"><strong>유의사항</strong><p>· 상품의 판매 상태와 재고에 따라 주문 가능 여부가 달라질 수 있습니다.</p><p>· 상품 금액 30,000원 이상 구매 시 무료배송입니다.</p></div>
<% } %>
</section>
<aside class="cart-payment" aria-label="배송지 및 결제 예상 금액">
<div class="cart-address-title"><h2>배송지</h2><a class="cart-address-link" href="<%= cartContext %>/app/order/form">⌖ 등록</a></div>
<p class="cart-address-message">배송지를 등록해 주세요.</p>
<div class="cart-price-lines">
<div class="cart-price-line"><span>총 상품 금액</span><span><%= cartMoney.format(subtotal) %>원</span></div>
<div class="cart-price-line"><span>총 배송비</span><span><%= cartMoney.format(shippingFee) %>원</span></div>
<div class="cart-price-line cart-base-shipping"><span>└ 기본 배송비</span><span><%= cartMoney.format(shippingFee) %>원</span></div>
</div>
<div class="cart-grand-total"><span>결제 예상 금액</span><strong><%= cartMoney.format(totalAmount) %>원</strong></div>
<% if (hasCartItems) { %>
<a class="cart-order-button" href="<%= cartContext %>/app/order/form">주문하기</a>
<% } else { %>
<button class="cart-order-button" type="button" disabled>주문하기</button>
<% } %>
<ul class="cart-policy"><li>[상품준비중] 상태일 경우에만 주문 취소 가능합니다.</li><li>[마이페이지 &gt; 주문내역]에서 직접 취소하실 수 있습니다.</li></ul>
</aside>
</div>
</main>
<%@ include file="../common/footer.jspf" %>
<script>
document.addEventListener('DOMContentLoaded', function () {
 const all = document.getElementById('cart-select-all');
 if (!all) return;
 const checks = Array.from(document.querySelectorAll('.cart-item-check'));
 const count = document.getElementById('cart-selected-count');
 const order = document.querySelector('a.cart-order-button');
 function syncSelection() {
  const selected = checks.filter(function(c){return c.checked;}).length;
  all.checked = selected === checks.length;
  all.indeterminate = selected > 0 && selected < checks.length;
  count.textContent = selected + '/' + checks.length + '개';
  if (order) {order.setAttribute('aria-disabled', selected === checks.length ? 'false' : 'true'); order.style.opacity = selected === checks.length ? '1' : '.45'; order.title = selected === checks.length ? '' : '현재 주문서는 전체 상품 주문만 지원합니다.';}
 }
 all.addEventListener('change', function(){checks.forEach(function(c){c.checked=all.checked;});syncSelection();});
 checks.forEach(function(c){c.addEventListener('change',syncSelection);});
 if(order) order.addEventListener('click',function(e){if(order.getAttribute('aria-disabled')==='true'){e.preventDefault();alert('현재는 전체 상품을 선택한 뒤 주문할 수 있습니다.');}});
 syncSelection();
});
//상품 +- 수정
document.addEventListener("DOMContentLoaded", function(){
	let updating = false;
	document.querySelectorAll("[data-quantity-change]")
		.forEach(function (button){
			button.addEventListener("click", async function (){
				if(updating) return;
				updating = true;
				 const stepper = button.closest(".cart-stepper");
				 
				 const data = new URLSearchParams();
				 data.set("cartItemId",stepper.dataset.cartItemId);
				 data.set("change",button.dataset.quantityChange);
				 
				 try{
					 const response = await fetch(
							 "${pageContext.request.contextPath}/app/cart/update",
							 { method:"POST", body: data}
							 );
					 if(!response.ok){
						 throw new Error("수량 변경 요청에 실패했습니다.");
					 }
					 const result = (await response.text()).trim();
					 if(result === "OK"){
						 location.reload();
						 return;
					 }
					 window.Daiso.ui.notice(result);
				 }catch (error){
					 window.Daiso.ui.notice(error.message);
				 }
				 updating=false;
			});
		});
});
//상품 삭제
document.addEventListener("DOMContentLoaded", function () {
    let deleting = false;

    document.querySelectorAll("[data-cart-delete]")
        .forEach(function (button) {
            button.addEventListener("click", async function () {
                if (deleting) return;
                if (!confirm("이 상품을 장바구니에서 삭제할까요?")) return;

                deleting = true;

                const data = new URLSearchParams();
                data.set("cartItemId", button.dataset.cartDelete);

                try {
                    const response = await fetch(
                        "${pageContext.request.contextPath}/app/cart/delete",
                        {
                            method: "POST",
                            body: data
                        }
                    );

                    if (!response.ok) {
                        throw new Error("삭제 요청에 실패했습니다.");
                    }

                    const result = (await response.text()).trim();

                    if (result === "OK") {
                        location.reload();
                        return;
                    }

                    window.Daiso.ui.notice(result);

                } catch (error) {
                    window.Daiso.ui.notice(error.message);
                }

                deleting = false;
            });
        });
});
//선택삭제
document.addEventListener("DOMContentLoaded", function () {
    const deleteButton =
        document.getElementById("cart-delete-selected");

    if (!deleteButton) return;

    let deleting = false;

    deleteButton.addEventListener("click", async function () {
        if (deleting) return;

        const selected = document.querySelectorAll(
            ".cart-item-check:checked"
        );

        if (selected.length === 0) {
            window.Daiso.ui.notice("삭제할 상품을 선택해 주세요.");
            return;
        }

        if (!confirm(
            "선택한 상품 " + selected.length + "개를 삭제할까요?"
        )) return;

        deleting = true;
        deleteButton.disabled = true;

        const data = new URLSearchParams();

        selected.forEach(function (checkbox) {
            data.append("cartItemId", checkbox.value);
        });

        try {
            const response = await fetch(
                "${pageContext.request.contextPath}/app/cart/delete-selected",
                {
                    method: "POST",
                    body: data
                }
            );

            if (!response.ok) {
                throw new Error("선택삭제 요청에 실패했습니다.");
            }

            const result = (await response.text()).trim();

            if (result === "OK") {
                location.reload();
                return;
            }

            window.Daiso.ui.notice(result);

        } catch (error) {
            window.Daiso.ui.notice(error.message);
        }

        deleting = false;
        deleteButton.disabled = false;
    });
});
</script>

</body>
</html>