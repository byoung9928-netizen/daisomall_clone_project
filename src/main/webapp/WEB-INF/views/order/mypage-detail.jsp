<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page
	import="java.util.Map,java.util.List,java.util.Collections,daiso.cart.db.CartBean,daiso.product.controller.ProductView"%>
<%
String myContext = request.getContextPath();
Map<String, Object> myOrder = (Map<String, Object>) request.getAttribute("myOrder");
List<Map<String, Object>> myOrders = (List<Map<String, Object>>) request.getAttribute("myOrders");
if (myOrders == null)
	myOrders = Collections.emptyList();
Map<String, String> myAddress = myOrder != null && myOrder.get("address") instanceof Map
		? (Map<String, String>) myOrder.get("address")
		: Collections.emptyMap();
List<CartBean> myItems = myOrder != null ? (List<CartBean>) myOrder.get("items") : Collections.emptyList();
java.text.NumberFormat myMoney = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
<%@ include file="../common/head.jspf"%>
<style>
.my-screen.container {
	width: calc(100% - 48px);
	max-width: 1280px;
	padding: 32px 0 80px;
	margin: 0 auto;
	box-sizing: border-box;
	color: #17212a
}

.my-screen *, .my-receipt * {
	box-sizing: border-box
}

.my-screen button, .my-screen input {
	font-family: inherit
}

.my-screen [hidden] {
	display: none !important
}

.account-overview {
	display: grid;
	grid-template-columns: 195px minmax(0, 1fr);
	gap: 20px;
	margin-bottom: 16px
}

.my-profile {
	border: 1px solid #e0e5eb;
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	min-height: 260px;
	padding: 24px 12px
}

.my-avatar {
	width: 80px;
	height: 80px;
	border: 1px solid #ccd1d8;
	border-radius: 50%;
	background: #f0f1f3;
	display: grid;
	place-items: center;
	position: relative;
	margin-bottom: 18px
}

.my-avatar svg {
	width: 52px;
	height: 52px;
	fill: #fff
}

.my-avatar span {
	position: absolute;
	right: -8px;
	top: -8px;
	display: grid;
	place-items: center;
	width: 36px;
	height: 36px;
	border: 1px solid #e0e5eb;
	background: #fff;
	border-radius: 50%;
	font-size: 15px;
	color: #66768a
}

.my-member-tag {
	border-radius: 20px;
	background: #17212a;
	color: #fff;
	font-size: 10px;
	padding: 3px 8px;
	margin-bottom: 8px
}

.my-member-name {
	font-size: 20px;
	font-weight: 800;
	line-height: 1.5
}

.my-account-right {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 12px
}

.my-usage {
	border: 1px solid #e0e5eb;
	flex: 1
}

.my-usage-title {
	background: #eceef2;
	padding: 20px 32px;
	font-size: 16px;
	font-weight: 700
}

.my-stats {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 16px;
	padding: 28px 32px
}

.my-stat-label {
	font-size: 13px;
	color: #7a889b;
	margin-bottom: 5px
}

.my-stat-value {
	font-size: 22px;
	font-weight: 800;
	color: #081b31
}

.my-stat-value small {
	font-size: 14px;
	font-weight: 400
}

.my-points {
	border: 1px solid #e0e5eb;
	display: grid;
	grid-template-columns: 1fr 1fr;
	padding: 20px 32px;
	gap: 32px
}

.my-points>div {
	display: flex;
	justify-content: space-between;
	align-items: center;
	gap: 16px;
	font-size: 13px;
	color: #6f8095
}

.my-points>div+div {
	border-left: 1px solid #e0e5eb;
	padding-left: 32px
}

.my-points strong {
	font-size: 22px;
	color: #10253b
}

.my-club-banner {
	height: 72px;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 20px;
	background: #08090b;
	color: #fff;
	margin-bottom: 60px;
	font-size: 19px;
	background-image: radial-gradient(circle at 17% 20%, #b0b6c2 0 1px, transparent 2px),
		radial-gradient(circle at 78% 62%, #777e89 0 1px, transparent 2px)
}

.my-club-banner strong {
	font-size: 25px;
	color: #d1d4dc
}

.my-club-banner button {
	background: transparent;
	border: 1px solid #c1c6d0;
	color: #fff;
	font-size: 12px;
	padding: 4px 12px;
	cursor: pointer
}

.my-layout {
	display: grid;
	grid-template-columns: 195px minmax(0, 1fr);
	gap: 20px;
	align-items: start
}

.my-sidebar {
	border: 1px solid #dfe4eb;
	padding: 22px 20px
}

.my-menu-group {
	border-bottom: 1px solid #dfe4eb;
	padding-bottom: 20px;
	margin-bottom: 26px
}

.my-menu-group:last-child {
	border: 0;
	margin: 0;
	padding: 0
}

.my-menu-group h2 {
	font-size: 16px;
	font-weight: 800;
	margin: 0 0 16px
}

.my-menu-group h2 small {
	font-size: 10px;
	font-weight: 400;
	color: #758396;
	margin-left: 5px
}

.my-menu-group a, .my-menu-group button {
	display: block;
	width: 100%;
	padding: 8px 0;
	border: 0;
	text-align: left;
	background: none;
	color: #52677f;
	text-decoration: none;
	font-size: 13px;
	line-height: 1.5;
	cursor: pointer
}

.my-menu-group .my-current {
	color: #17212a;
	font-weight: 700
}

.my-content {
	min-width: 0
}

.my-order-heading {
	display: flex;
	justify-content: space-between;
	align-items: center;
	gap: 16px;
	margin: 0 0 18px
}

.my-order-heading h1 {
	font-size: 28px;
	margin: 0;
	font-weight: 800;
	color: #738298
}

.my-delivery-tag {
	font-size: 11px;
	font-weight: 400;
	margin-left: 12px;
	background: #f3f5f7;
	padding: 4px 7px;
	color: #69778a;
	vertical-align: middle
}

.my-order-number {
	font-size: 11px;
	color: #6d7e94;
	overflow-wrap: anywhere;
	text-align: right
}

.my-status-panel {
	background: #f6f7f9;
	padding: 20px 24px 18px;
	margin-bottom: 24px
}

.my-status-panel h2 {
	font-size: 18px;
	margin: 0 0 24px;
	font-weight: 800
}

.my-status-steps {
	list-style: none;
	display: grid;
	grid-template-columns: repeat(5, minmax(0, 1fr));
	padding: 0;
	margin: 0
}

.my-status-steps li {
	position: relative;
	text-align: center;
	color: #8a98a9;
	font-size: 12px;
	padding-top: 26px;
	line-height: 1.5
}

.my-status-steps li::before {
	content: '';
	width: 18px;
	height: 18px;
	border: 2px solid #c8cfd8;
	background: #f6f7f9;
	border-radius: 50%;
	position: absolute;
	top: 0;
	left: calc(50% - 9px);
	z-index: 1
}

.my-status-steps li::after {
	content: '';
	height: 1px;
	background: #c8cfd8;
	position: absolute;
	top: 8px;
	left: -50%;
	width: 100%
}

.my-status-steps li:first-child::after {
	display: none
}

.my-status-steps li[aria-current=step] {
	color: #17212a;
	font-weight: 700
}

.my-status-steps li[aria-current=step]::before {
	content: '✓';
	color: white;
	font-size: 12px;
	border-color: #455564;
	background: #455564;
	display: grid;
	place-items: center
}

.my-status-steps li:first-child {
	text-align: left
}

.my-status-steps li:first-child::before {
	left: 12px
}

.my-status-steps li:nth-child(2)::after {
	left: calc(-100% + 20px);
	width: calc(150% - 20px)
}

.my-item {
	display: flex;
	align-items: center;
	gap: 16px;
	padding: 0 0 24px;
	margin-bottom: 24px
}

.my-item img {
	width: 80px;
	height: 108px;
	object-fit: contain;
	background: #f6f6f6;
	flex-shrink: 0
}

.my-item-info {
	flex: 1;
	min-width: 0
}

.my-item-info strong {
	font-size: 20px
}

.my-item-info strong small {
	font-size: 14px;
	font-weight: 400
}

.my-item-info a {
	display: block;
	color: #17212a;
	text-decoration: none;
	font-size: 13px;
	line-height: 1.7;
	margin-top: 4px
}

.my-add-cart {
	border: 1px solid #dfe4eb;
	background: #fff;
	color: #53677f;
	border-radius: 24px;
	padding: 8px 16px;
	cursor: pointer;
	font-size: 13px
}

.my-order-actions {
	display: flex;
	justify-content: center;
	gap: 12px;
	margin: 30px 0 72px
}

.my-screen .my-outline-button {
	width: 260px;
	min-height: 40px;
	border: 1px solid #dfe4eb;
	background: #fff;
	color: #20334a;
	font-size: 13px;
	cursor: pointer
}

.my-screen button:disabled {
	cursor: default;
	color: #9ca7b5;
	background: #fafbfc
}

.my-section {
	margin-bottom: 76px
}

.my-section-heading {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 30px;
	gap: 16px
}

.my-section-heading h2 {
	font-size: 27px;
	font-weight: 800;
	margin: 0
}

.my-change-button {
	border: 1px solid #dfe4eb;
	padding: 5px 12px;
	font-size: 12px;
	background: #fff;
	color: #6c7e93
}

.my-data-table {
	width: 100%;
	border-collapse: collapse;
	border-top: 1px solid #dfe4eb;
	font-size: 14px;
	text-align: left
}

.my-data-table th, .my-data-table td {
	border-bottom: 1px solid #dfe4eb;
	padding: 18px 20px;
	line-height: 1.6;
	vertical-align: top
}

.my-data-table th {
	width: 195px;
	font-weight: 400;
	color: #667b94
}

.my-data-table td {
	overflow-wrap: anywhere
}

.my-data-table .money {
	text-align: right;
	white-space: nowrap
}

.my-data-table .tinted {
	background: #f6f7f9
}

.my-data-table .tinted th {
	font-weight: 700;
	color: #17212a
}

.my-data-table .red {
	color: #e60023;
	font-weight: 800
}

.my-data-table .muted th, .my-data-table .muted td {
	color: #8a98a9
}

.my-payment-label {
	display: inline-block;
	padding: 2px 6px;
	background: #6c7e93;
	color: #fff;
	font-size: 10px;
	margin-left: 10px
}

.my-receipt-button-wrap {
	text-align: center;
	margin-top: 28px
}

.my-empty {
	padding: 70px 20px;
	text-align: center;
	background: #f7f8fa;
	line-height: 1.8
}

.my-empty a {
	display: inline-block;
	margin-top: 18px;
	padding: 12px 20px;
	background: #17212a;
	color: #fff;
	text-decoration: none
}

.my-order-switch {
	display: flex;
	justify-content: flex-end;
	gap: 12px;
	align-items: center;
	font-size: 13px;
	margin-bottom: 24px;
	color: #667b94
}

.my-order-switch select {
	height: 40px;
	max-width: 100%;
	border: 1px solid #dfe4eb;
	background: #fff;
	padding: 0 12px;
	font-size: 13px
}

.my-logout {
	margin-top: 24px
}

.my-logout button {
	border: 0;
	background: none;
	color: #667b94;
	font-size: 12px;
	text-decoration: underline;
	cursor: pointer
}

.my-receipt {
	width: 640px;
	max-width: calc(100% - 32px);
	max-height: calc(100dvh - 48px);
	padding: 24px;
	border: 0;
	color: #17212a
}

.my-receipt::backdrop {
	background: rgba(0, 0, 0, .65)
}

.my-receipt-head {
	display: flex;
	justify-content: space-between;
	align-items: center;
	gap: 16px
}

.my-receipt-head h2 {
	font-size: 24px;
	margin: 0
}

.my-receipt-close {
	border: 0;
	background: none;
	font-size: 28px;
	cursor: pointer
}

.my-receipt p {
	font-size: 13px;
	color: #667b94;
	overflow-wrap: anywhere
}

.my-receipt table {
	width: 100%;
	border-collapse: collapse;
	margin-top: 24px;
	font-size: 13px
}

.my-receipt th, .my-receipt td {
	text-align: left;
	padding: 12px 8px;
	border-bottom: 1px solid #e1e5eb
}

.my-receipt-total {
	text-align: right !important;
	font-weight: 700;
	font-size: 18px !important;
	color: #17212a !important
}

.my-screen :focus-visible, .my-receipt :focus-visible {
	outline: 2px solid #2867b7;
	outline-offset: 3px
}

@media ( max-width :1000px) {
	.account-overview, .my-layout {
		grid-template-columns: 160px minmax(0, 1fr)
	}
	.my-stats {
		padding: 24px 20px;
		gap: 10px
	}
	.my-points {
		padding: 18px 20px;
		gap: 16px
	}
	.my-points>div+div {
		padding-left: 16px
	}
	.my-usage-title {
		padding: 18px 20px
	}
	.my-stat-value {
		font-size: 19px
	}
	.my-sidebar {
		padding: 20px 14px
	}
	.my-data-table th {
		width: 145px
	}
}

@media ( max-width :700px) {
	.my-screen.container {
		width: calc(100% - 32px);
		padding-top: 24px
	}
	.account-overview {
		grid-template-columns: 105px minmax(0, 1fr);
		gap: 10px
	}
	.my-profile {
		padding: 16px 8px;
		min-height: 240px
	}
	.my-avatar {
		width: 60px;
		height: 60px
	}
	.my-avatar svg {
		width: 42px
	}
	.my-avatar span {
		width: 25px;
		height: 25px;
		right: -3px;
		font-size: 12px
	}
	.my-member-name {
		font-size: 16px
	}
	.my-usage-title {
		font-size: 12px;
		padding: 12px
	}
	.my-stats {
		grid-template-columns: 1fr 1fr;
		gap: 14px;
		padding: 14px 12px
	}
	.my-stat-label {
		font-size: 11px
	}
	.my-stat-value {
		font-size: 17px
	}
	.my-points {
		padding: 12px;
		gap: 10px;
		grid-template-columns: 1fr
	}
	.my-points>div+div {
		border: 0;
		padding: 0
	}
	.my-points strong {
		font-size: 16px
	}
	.my-points>div {
		font-size: 11px
	}
	.my-club-banner {
		gap: 8px;
		font-size: 11px;
		height: 60px;
		margin-bottom: 28px
	}
	.my-club-banner strong {
		font-size: 17px
	}
	.my-club-banner button {
		font-size: 10px;
		padding: 3px 6px
	}
	.my-layout {
		grid-template-columns: 1fr
	}
	.my-sidebar {
		padding: 12px;
		display: flex;
		overflow-x: auto;
		gap: 18px
	}
	.my-menu-group {
		border: 0;
		padding: 0;
		margin: 0;
		min-width: 115px;
		flex-shrink: 0
	}
	.my-menu-group h2 {
		font-size: 13px;
		margin-bottom: 8px
	}
	.my-menu-group a, .my-menu-group button {
		font-size: 12px;
		padding: 5px 0
	}
	.my-order-heading {
		align-items: flex-start;
		flex-direction: column;
		gap: 8px;
		margin-top: 12px
	}
	.my-order-heading h1 {
		font-size: 24px
	}
	.my-order-number {
		text-align: left;
		font-size: 10px
	}
	.my-status-panel {
		padding: 18px 12px
	}
	.my-status-steps li {
		font-size: 10px
	}
	.my-status-steps li:first-child::before {
		left: 8px
	}
	.my-status-steps li:nth-child(2)::after {
		left: calc(-100% + 16px);
		width: calc(150% - 16px)
	}
	.my-item {
		gap: 10px
	}
	.my-item img {
		width: 65px;
		height: 85px
	}
	.my-item-info strong {
		font-size: 17px
	}
	.my-add-cart {
		font-size: 11px;
		padding: 6px 10px
	}
	.my-data-table th {
		width: 90px
	}
	.my-data-table th, .my-data-table td {
		padding: 14px 10px;
		font-size: 12px
	}
	.my-section-heading h2 {
		font-size: 23px
	}
	.my-section {
		margin-bottom: 48px
	}
	.my-order-actions {
		margin-bottom: 48px
	}
	.my-screen .my-outline-button {
		width: auto;
		flex: 1;
		max-width: 260px
	}
	.my-logout {
		display: none
	}
	.my-order-switch {
		flex-wrap: wrap;
		justify-content: flex-start
	}
}
.my-home-shortcuts {
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    margin-bottom: 12px;
    padding: 14px 6px;
    border: 1px solid #dfe4eb;
    background: #fff;
}

.my-home-shortcuts a,
.my-home-shortcuts button {
    display: grid;
    justify-items: center;
    gap: 7px;
    padding: 4px;
    border: 0;
    border-right: 1px solid #edf0f2;
    background: #fff;
    color: #52677f;
    font: inherit;
    font-size: 12px;
    text-decoration: none;
    cursor: pointer;
}

.my-home-shortcuts a:last-child,
.my-home-shortcuts button:last-child {
    border-right: 0;
}

.my-home-shortcuts span {
    color: #17212a;
    font-size: 21px;
    line-height: 1;
}

.my-home-shortcuts button:disabled {
    color: #52677f;
    cursor: default;
}

.my-home-notice {
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: center;
    gap: 10px;
    margin-bottom: 34px;
    padding: 12px 16px;
    border: 1px solid #dfe4eb;
    color: #667b94;
    font-size: 12px;
}

.my-home-notice strong {
    color: #17212a;
}

.my-home-notice b {
    color: #17212a;
    font-size: 20px;
}

.my-home-products {
    margin-top: 38px;
}

.my-home-product-grid {
    display: grid;
    gap: 12px;
}

.my-home-wish-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
}

.my-home-recent-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
}

.my-home-product {
    min-width: 0;
    font-size: 12px;
}

.my-home-product-image {
    position: relative;
    aspect-ratio: 1 / 1.15;
    overflow: hidden;
    margin-bottom: 7px;
    background: #f1f2f4;
}

.my-home-product-image img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.my-home-product-image span {
    position: absolute;
    right: 8px;
    bottom: 8px;
    color: #e60023;
    font-size: 20px;
}

.my-home-product strong,
.my-home-product small {
    display: block;
    margin-top: 5px;
}

.my-home-product small {
    color: #7a889b;
}

@media (max-width: 700px) {
    .my-home-shortcuts {
        grid-template-columns: repeat(4, minmax(0, 1fr));
        row-gap: 12px;
    }

    .my-home-recent-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }
}
</style>
</head>
<body>
	<a class="skip-link" href="#main-content">본문 바로가기</a>
	<%@ include file="../common/header.jspf"%>
	<%@ include file="../common/navigation.jspf"%>
	<main id="main-content" class="container shop-page my-screen"
		data-page="mypage" tabindex="-1">
		<h1 class="visually-hidden">마이페이지 주문 상세</h1>
		<section class="account-overview" aria-label="회원 이용 현황">
			<div class="my-profile">
				<div class="my-avatar">
					<svg viewBox="0 0 48 48" aria-hidden="true">
						<circle cx="24" cy="15" r="9" />
						<path d="M7 43v-5a17 17 0 0 1 34 0v5z" /></svg>
					<span>0</span>
				</div>
				<span class="my-member-tag">개인</span><strong class="my-member-name"><%=ProductView.escape(request.getAttribute("memberName"))%>님
					›</strong>
			</div>
			<div class="my-account-right">
				<div class="my-usage">
					<div class="my-usage-title"><%=ProductView.escape(request.getAttribute("myPageYear"))%>년
						나의 다이소몰 이용현황 ⓘ
					</div>
					<div class="my-stats">
						<div>
							<div class="my-stat-label">구매 횟수</div>
							<div class="my-stat-value"><%=myMoney.format(request.getAttribute("myPagePurchaseCount"))%><small>회</small>
							</div>
						</div>
						<div>
							<div class="my-stat-label">구매 금액</div>
							<div class="my-stat-value"><%=myMoney.format(request.getAttribute("myPagePurchaseAmount"))%><small>원</small>
							</div>
						</div>
						<div>
							<div class="my-stat-label">리뷰 수</div>
							<div class="my-stat-value">-</div>
						</div>
						<div>
							<div class="my-stat-label">리뷰 랭킹</div>
							<div class="my-stat-value">-</div>
						</div>
					</div>
				</div>
				<div class="my-points">
					<div>
						<span>다이소 포인트</span><strong>- P</strong>
					</div>
					<div>
						<span>다이소몰 포인트</span><strong>- P</strong>
					</div>
				</div>
			</div>
		</section>
		<div class="my-club-banner">
			<span><strong>신상클럽</strong> 신상 소식 가장 먼저 받아보세요.</span>
			<button type="button" data-my-unavailable>무료구독</button>
		</div>
		<div class="my-layout">
			<aside class="my-sidebar" aria-label="마이페이지 메뉴">
				<section class="my-menu-group">
					<h2>
						주문 관리 <small>최근 1년</small>
					</h2>
					<a class="my-current" href="<%=myContext%>/app/order/mypage">주문내역</a>
					<button type="button" data-my-unavailable>취소/교환/반품 내역</button>
				</section>
				<section class="my-menu-group">
					<h2>나의 혜택</h2>
					<button type="button" data-my-unavailable>신상클럽</button>
					<button type="button" data-my-unavailable>다이소 포인트</button>
					<button type="button" data-my-unavailable>다이소몰 포인트</button>
					<button type="button" data-my-unavailable>쿠폰</button>
					<button type="button" data-my-unavailable>다이소 상품권</button>
				</section>
				<section class="my-menu-group">
					<h2>나의 활동</h2>
					<button type="button" data-my-unavailable>찜</button>
					<button type="button" data-my-unavailable>리뷰</button>
					<button type="button" data-my-unavailable>최근 본</button>
					<button type="button" data-my-unavailable>자주 산</button>
					<button type="button" data-my-unavailable>이벤트 참여내역</button>
					<button type="button" data-my-unavailable>상품 입고 알림</button>
				</section>
				<section class="my-menu-group">
					<h2>나의 정보</h2>
					<button type="button" data-my-unavailable>회원정보</button>
					<button type="button" data-my-unavailable>프로필</button>
					<button type="button" data-my-unavailable>배송지/픽업매장</button>
					<button type="button" data-my-unavailable>환불계좌</button>
					<button type="button" data-my-unavailable>알림 목록</button>
				</section>
				<section class="my-menu-group">
					<h2>고객센터</h2>
					<button type="button" data-my-unavailable>공지사항</button>
					<button type="button" data-my-unavailable>FAQ</button>
					<button type="button" data-my-unavailable>1:1 문의내역</button>
					<button type="button" data-my-unavailable>1:1 문의</button>
					<form class="my-logout" method="post"
						action="<%=myContext%>/app/member/logout">
						<button type="submit">로그아웃</button>
					</form>
				</section>
			</aside>
			<div class="my-content">
				<nav class="my-home-shortcuts" aria-label="마이페이지 바로가기">
					<a href="<%=myContext%>/app/order/mypage"><span>▤</span>주문내역</a>
					<button type="button" data-my-unavailable>
						<span>♡</span>찜
					</button>
					<button type="button" data-my-unavailable>
						<span>▧</span>리뷰
					</button>
					<button type="button" data-my-unavailable>
						<span>◷</span>최근 본
					</button>
					<button type="button" data-my-unavailable>
						<span>％</span>쿠폰
					</button>
					<button type="button" data-my-unavailable>
						<span>▣</span>상품권
					</button>
					<button type="button" data-my-unavailable>
						<span>▦</span>멤버십
					</button>
				</nav>

				<div class="my-home-notice">
					<strong>공지</strong> <span>마이페이지 기능을 순차적으로 연결하고 있습니다.</span> <b
						aria-hidden="true">›</b>
				</div>
				<%
				if (myOrder == null) {
				%>
				<div class="my-empty">
					<p><%=request.getAttribute("myOrderError") == null
		? "표시할 주문내역이 없습니다."
		: ProductView.escape(request.getAttribute("myOrderError"))%></p>
					<a href="<%=myContext%>/list.jsp">쇼핑하러 가기</a>
				</div>
				<%
				} else {
				%>
				<%
				if (myOrders.size() > 1) {
				%><label class="my-order-switch">주문 선택 <select
					id="my-order-switch">
						<%
						for (Map<String, Object> option : myOrders) {
						%><option value="<%=ProductView.escape(option.get("orderNo"))%>"
							<%=option.get("orderNo").equals(myOrder.get("orderNo")) ? "selected" : ""%>><%=ProductView.escape(option.get("paidAt"))%>
							·
							<%=myMoney.format(option.get("total"))%>원
						</option>
						<%
						}
						%>
				</select>
				</label>
				<%
				}
				%>
				<div class="my-order-heading">
					<h1><%=ProductView.escape(myOrder.get("date"))%><span
							class="my-delivery-tag">택배</span>
					</h1>
					<span class="my-order-number">주문번호 <%=ProductView.escape(myOrder.get("orderNo"))%></span>
				</div>
				<section class="my-status-panel" aria-label="주문 처리 상태">
					<h2>결제완료</h2>
					<ol class="my-status-steps">
						<li aria-current="step">결제완료</li>
						<li>상품준비중</li>
						<li>배송준비중</li>
						<li>배송중</li>
						<li>배송완료</li>
					</ol>
				</section>
				<%
				for (CartBean item : myItems) {
					String myImage = item.getImageUrl();
					if (myImage == null || myImage.isBlank())
						myImage = "assets/images/mock-product-0.svg";
					if (!myImage.startsWith("https://"))
						myImage = myContext + "/" + myImage;
				%>
				<article class="my-item">
					<img src="<%=ProductView.escape(myImage)%>" alt="">
					<div class="my-item-info">
						<strong><%=myMoney.format(item.getItemTotal())%>원<small>
								/ <%=item.getQuantity()%>개
						</small></strong><a
							href="<%=myContext%>/product.jsp?id=<%=java.net.URLEncoder.encode(item.getProductId(), "UTF-8")%>"><%=ProductView.escape(item.getProductName())%></a>
					</div>
					<button class="my-add-cart" type="button"
						data-my-add="<%=ProductView.escape(item.getProductId())%>">♧
						담기</button>
				</article>
				<%
				}
				%>
				<div class="my-order-actions">
					<button class="my-outline-button" type="button" disabled
						title="주문 취소 API 연결이 필요합니다">주문취소</button>
					<button class="my-outline-button" type="button" data-my-unavailable>문의하기</button>
				</div>
				<section class="my-section" aria-labelledby="my-delivery-title">
					<div class="my-section-heading">
						<h2 id="my-delivery-title">배송정보</h2>
						<button class="my-change-button" type="button" disabled
							title="결제 후 배송지 변경 기능은 준비 중입니다">⇄ 변경</button>
					</div>
					<table class="my-data-table">
						<caption class="visually-hidden">주문 배송정보</caption>
						<tbody>
							<tr>
								<th scope="row">수령 방법</th>
								<td>택배배송</td>
							</tr>
							<tr>
								<th scope="row">배송지</th>
								<td><%=ProductView.escape(myAddress.get("addressName"))%></td>
							</tr>
							<tr>
								<th scope="row">받는분</th>
								<td><%=ProductView.escape(myAddress.get("recipient"))%></td>
							</tr>
							<tr>
								<th scope="row">연락처</th>
								<td><%=ProductView.escape(myAddress.get("phone"))%></td>
							</tr>
							<tr>
								<th scope="row">주소</th>
								<td><%=ProductView.escape(myAddress.get("address1"))%> <%=ProductView.escape(myAddress.get("address2"))%></td>
							</tr>
							<tr>
								<th scope="row">배송 요청사항</th>
								<td><%=myOrder.get("message") == null || myOrder.get("message").toString().isBlank()
		? "없음"
		: ProductView.escape(myOrder.get("message"))%></td>
							</tr>
						</tbody>
					</table>
				</section>
				<section class="my-section" aria-labelledby="my-total-title">
					<div class="my-section-heading">
						<h2 id="my-total-title">최종 결제 금액</h2>
					</div>
					<table class="my-data-table">
						<caption class="visually-hidden">주문 결제 내역</caption>
						<tbody>
							<tr class="tinted">
								<th scope="row">총 주문 금액</th>
								<td class="money red"><%=myMoney.format(myOrder.get("total"))%>원</td>
							</tr>
							<tr>
								<th scope="row">총 상품 금액</th>
								<td class="money"><%=myMoney.format(myOrder.get("subtotal"))%>원</td>
							</tr>
							<tr>
								<th scope="row">총 배송비</th>
								<td class="money"><%=myMoney.format(myOrder.get("shipping"))%>원</td>
							</tr>
							<tr class="muted">
								<th scope="row">기본 배송비</th>
								<td class="money"><%=myMoney.format(myOrder.get("shipping"))%>원</td>
							</tr>
							<tr class="tinted">
								<th scope="row">카카오페이<span class="my-payment-label">결제완료</span></th>
								<td class="money"><%=myMoney.format(myOrder.get("total"))%>원</td>
							</tr>
							<tr>
								<th scope="row">결제수단</th>
								<td>카카오페이</td>
							</tr>
							<tr>
								<th scope="row">결제일시</th>
								<td><%=ProductView.escape(myOrder.get("paidAt"))%></td>
							</tr>
						</tbody>
					</table>
					<div class="my-receipt-button-wrap">
						<button class="my-outline-button" id="open-my-receipt"
							type="button">거래명세서</button>
					</div>
				</section>
				<dialog class="my-receipt" id="my-receipt"
					aria-labelledby="my-receipt-title">
				<div class="my-receipt-head">
					<h2 id="my-receipt-title">거래명세서</h2>
					<button class="my-receipt-close" id="close-my-receipt"
						type="button" aria-label="거래명세서 닫기">×</button>
				</div>
				<p>카카오페이 모의결제 내역</p>
				<p>
					주문번호
					<%=ProductView.escape(myOrder.get("orderNo"))%></p>
				<p>
					결제일시
					<%=ProductView.escape(myOrder.get("paidAt"))%></p>
				<table>
					<thead>
						<tr>
							<th scope="col">상품명</th>
							<th scope="col">수량</th>
							<th scope="col">금액</th>
						</tr>
					</thead>
					<tbody>
						<%
						for (CartBean item : myItems) {
						%><tr>
							<td><%=ProductView.escape(item.getProductName())%></td>
							<td><%=item.getQuantity()%></td>
							<td><%=myMoney.format(item.getItemTotal())%>원</td>
						</tr>
						<%
						}
						%>
					</tbody>
				</table>
				<p class="my-receipt-total">
					배송비
					<%=myMoney.format(myOrder.get("shipping"))%>원
				</p>
				<p class="my-receipt-total">
					총 결제 금액
					<%=myMoney.format(myOrder.get("total"))%>원
				</p>
				</dialog>
				<%
				}
				%>

				<section class="my-section my-home-products">
					<div class="my-section-heading">
						<h2>찜한 상품 ›</h2>
						<button type="button" disabled>전체보기</button>
					</div>

					<div class="my-home-product-grid my-home-wish-grid">
						<%
						for (int i = 0; i < 3; i++) {
						%>
						<article class="my-home-product">
							<div class="my-home-product-image">
								<img
									src="<%=myContext%>/assets/images/mock-product-<%=i%>.svg"
									alt="샘플 상품 이미지"> <span aria-hidden="true">♡</span>
							</div>
							<strong>샘플 상품</strong> <small>찜 목록 연결 준비 중</small>
						</article>
						<%
						}
						%>
					</div>
				</section>

				<section class="my-section my-home-products">
					<div class="my-section-heading">
						<h2>최근 본 상품 ›</h2>
						<button type="button" disabled>전체보기</button>
					</div>

					<div class="my-home-product-grid my-home-recent-grid">
						<%
						for (int i = 0; i < 5; i++) {
						%>
						<article class="my-home-product">
							<div class="my-home-product-image">
								<img src="<%=myContext%>/assets/images/mock-product-<%=i%>.svg"
									alt="샘플 상품 이미지"> <span aria-hidden="true">♡</span>
							</div>
							<strong>샘플 상품</strong> <small>최근 본 상품 연결 준비 중</small>
						</article>
						<%
						}
						%>
					</div>
				</section>
			</div>
		</div>
		
	</main>
	<%@ include file="../common/footer.jspf"%>
	<script>
document.addEventListener('DOMContentLoaded',function(){
 const notice=function(text){window.Daiso.ui.notice(text);};
 document.querySelectorAll('[data-my-unavailable]').forEach(function(button){button.addEventListener('click',function(){notice('해당 기능은 준비 중입니다.');});});
 const chooser=document.getElementById('my-order-switch');
 if(chooser)chooser.addEventListener('change',function(){const url=new URL(location.href);url.searchParams.set('orderNo',chooser.value);location.assign(url.href);});
 const open=document.getElementById('open-my-receipt');
 if(open){const dialog=document.getElementById('my-receipt');open.addEventListener('click',function(){dialog.showModal();});document.getElementById('close-my-receipt').addEventListener('click',function(){dialog.close();});}
 document.querySelectorAll('[data-my-add]').forEach(function(button){button.addEventListener('click',async function(){if(button.disabled)return;button.disabled=true;try{const data=new URLSearchParams();data.set('productId',button.dataset.myAdd);data.set('quantity','1');const response=await fetch('<%=myContext%>/app/cart/add',{method:'POST',body:data});if(!response.ok)throw new Error('장바구니 담기에 실패했습니다.');const result=(await response.text()).trim();notice(result==='OK'?'장바구니에 1개 담았습니다.':result);}catch(error){notice(error.message);}finally{button.disabled=false;}});});
});
</script>
</body>
</html>
