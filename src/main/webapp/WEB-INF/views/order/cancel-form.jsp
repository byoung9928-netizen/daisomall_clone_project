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


<%
String cancelContext=request.getContextPath();
Map<String,Object> cancelOrder=(Map<String,Object>)request.getAttribute("cancelOrder");
java.text.NumberFormat cancelMoney=java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
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
<style>
.cancel-screen.container{width:calc(100% - 48px);max-width:1060px;margin:0 auto;padding:40px 0 70px;box-sizing:border-box;color:#14212e}.cancel-screen *,.cancel-confirm *{box-sizing:border-box}.cancel-screen .cancel-title{font-size:32px;margin:0 0 36px;font-weight:800}.cancel-item{display:flex;gap:16px;align-items:center;border-top:1px solid #e0e5eb;padding:24px 0 16px}.cancel-item img{width:80px;height:108px;object-fit:contain;background:#f6f6f6}.cancel-item strong{font-size:20px}.cancel-item small{font-size:14px;font-weight:400}.cancel-item p{margin:5px 0 0;font-size:14px;line-height:1.6}.cancel-info{background:#f6f7f9;padding:12px 20px;color:#73849a;font-size:12px;line-height:1.8;border-radius:4px}.cancel-info p{margin:0}.cancel-block{margin-top:72px}.cancel-block h2{font-size:28px;font-weight:800;margin:0 0 32px}.cancel-reason-wrap{position:relative}.cancel-reason-button{height:48px;width:100%;background:#fff;border:1px solid #dfe4eb;text-align:left;padding:0 16px;color:#152b43;display:flex;align-items:center;justify-content:space-between;cursor:pointer;font-size:14px}.cancel-reason-button[aria-expanded=true]{border:2px solid #171e24}.cancel-options{position:absolute;top:47px;left:0;right:0;background:#fff;border:1px solid #dfe4eb;box-shadow:0 6px 14px #17212a12;list-style:none;padding:0;margin:0;z-index:3}.cancel-options button{display:block;width:100%;text-align:left;padding:15px 16px;border:0;border-bottom:1px solid #e0e5eb;background:#fff;color:#152b43;font-size:14px;cursor:pointer}.cancel-options li:last-child button{border-bottom:0}.cancel-options button:hover,.cancel-options button:focus-visible{background:#f5f6f8}.cancel-other{display:block;width:100%;min-height:90px;margin-top:14px;padding:14px;border:1px solid #dfe4eb;resize:vertical;font-family:inherit;font-size:14px}.cancel-screen [hidden]{display:none!important}.cancel-refund-table{width:100%;border-collapse:collapse;border-top:1px solid #dfe4eb;font-size:14px}.cancel-refund-table th,.cancel-refund-table td{padding:18px 20px;border-bottom:1px solid #dfe4eb;line-height:1.5}.cancel-refund-table th{text-align:left;font-weight:400}.cancel-refund-table td{text-align:right}.cancel-refund-table .tinted{background:#f6f7f9;font-weight:700}.cancel-refund-table .tinted th{font-weight:700}.cancel-refund-table .red{color:#ed0025;font-weight:800}.cancel-refund-table .muted{color:#7b8ca2}.cancel-refund-table .muted th{padding-left:40px}.cancel-refund-note{background:#f6f7f9;padding:22px 24px;margin-top:24px;border-radius:6px;font-size:12px;line-height:1.8;color:#708199}.cancel-actions{border-top:1px solid #e0e5eb;display:flex;justify-content:center;gap:8px;margin-top:24px;padding-top:72px}.cancel-actions a,.cancel-actions button{width:320px;min-height:48px;display:flex;justify-content:center;align-items:center;border:1px solid #dfe4eb;background:#fff;text-decoration:none;color:#15283d;font-size:14px;font-weight:700;cursor:pointer}.cancel-actions button{border-color:#17212a}.cancel-error{font-size:14px;color:#d00020;line-height:1.7}.cancel-confirm{width:500px;max-width:calc(100% - 32px);padding:24px;border:0;border-radius:2px;color:#17212a}.cancel-confirm::backdrop{background:#0009}.cancel-confirm-head{display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:36px}.cancel-confirm-head h2{font-size:20px;margin:0}.cancel-confirm-close{border:0;background:none;font-size:28px;cursor:pointer}.cancel-restore-label{display:flex;align-items:center;gap:8px;font-size:14px}.cancel-restore-label input{appearance:auto;accent-color:#17212a;width:20px;height:20px;margin:0}.cancel-confirm-actions{display:flex;gap:8px;margin-top:32px}.cancel-confirm-actions button{flex:1;min-height:48px;border:1px solid #dfe4eb;background:#fff;font-weight:700;color:#17212a;cursor:pointer}.cancel-confirm-actions .primary{background:#17212a;color:#fff;border-color:#17212a}.cancel-screen :focus-visible,.cancel-confirm :focus-visible{outline:2px solid #2867b7;outline-offset:3px}@media(max-width:700px){.cancel-screen.container{width:calc(100% - 32px);padding-top:24px}.cancel-screen .cancel-title{font-size:26px}.cancel-block{margin-top:44px}.cancel-block h2{font-size:23px;margin-bottom:24px}.cancel-actions{padding-top:36px}.cancel-actions a,.cancel-actions button{width:auto;flex:1}.cancel-refund-table th,.cancel-refund-table td{padding:16px 12px}.cancel-info{padding:14px}.cancel-confirm{padding:22px}.cancel-item img{width:65px;height:88px}}

.my-content .cancel-screen{width:100%;max-width:none;padding:0;margin:0}.my-content .cancel-screen .cancel-title{font-size:28px;margin-top:0}</style></head>
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
			<div class="my-content"><div class="cancel-screen">
<h1 class="cancel-title">주문취소</h1>
<% if(cancelOrder==null){ %><p class="cancel-error" role="alert"><%= ProductView.escape(request.getAttribute("cancelPageError")) %></p><a href="<%= cancelContext %>/app/order/mypage">주문내역으로</a>
<% }else{ for(CartBean item:(List<CartBean>)cancelOrder.get("items")){ String img=item.getImageUrl();if(img==null||img.isBlank())img="assets/images/mock-product-0.svg";if(!img.startsWith("https://"))img=cancelContext+"/"+img; %>
<article class="cancel-item"><img src="<%= ProductView.escape(img) %>" alt=""><div><strong><%= cancelMoney.format(item.getItemTotal()) %>원<small> / <%= item.getQuantity() %>개</small></strong><p><%= ProductView.escape(item.getProductName()) %></p></div></article>
<% } %>
<div class="cancel-info"><p>· 배송 준비가 시작된 주문은 취소가 불가합니다.</p><p>· 취소가 불가한 경우에는 배송완료 후 반품으로 신청해 주세요.</p></div>
<section class="cancel-block"><h2>취소 사유</h2><div class="cancel-reason-wrap"><button id="cancel-reason-toggle" class="cancel-reason-button" type="button" aria-expanded="false" aria-controls="cancel-reasons"><span id="cancel-reason-text">취소 사유를 선택해주세요.</span><span aria-hidden="true" id="cancel-reason-arrow">⌄</span></button><ul class="cancel-options" id="cancel-reasons" hidden>
<% String[] reasons={"주소를 잘못 입력함","결제수단 변경을 원함","재주문을 위해 기존 주문을 취소함","상품이 필요 없게 됨(단순변심)","배송이 늦음","기타"};for(String reason:reasons){ %><li><button type="button" data-cancel-reason="<%= ProductView.escape(reason) %>"><%= ProductView.escape(reason) %></button></li><% } %></ul></div><textarea class="cancel-other" id="cancel-other" maxlength="500" placeholder="취소 사유를 입력해 주세요" aria-label="기타 취소 사유" hidden></textarea><p id="cancel-reason-error" class="cancel-error" role="alert"></p></section>
<section class="cancel-block"><h2>환불 예정 금액</h2><table class="cancel-refund-table"><caption class="visually-hidden">전체 주문 취소 시 환불 예정 금액</caption><tbody><tr class="tinted"><th scope="row">총 환불 금액</th><td class="red"><%= cancelMoney.format(cancelOrder.get("total")) %>원</td></tr><tr><th scope="row">총 상품 금액</th><td><%= cancelMoney.format(cancelOrder.get("subtotal")) %>원</td></tr><tr><th scope="row">총 배송비</th><td><%= cancelMoney.format(cancelOrder.get("shipping")) %>원</td></tr><tr class="muted"><th scope="row">기본 배송비</th><td><%= cancelMoney.format(cancelOrder.get("shipping")) %>원</td></tr><tr class="tinted"><th scope="row">카카오페이 환불</th><td><%= cancelMoney.format(cancelOrder.get("total")) %>원</td></tr></tbody></table><div class="cancel-refund-note">ⓘ 취소 완료 후 결제수단에 따라 환불됩니다. 정확한 반영 시점은 결제수단별로 다를 수 있습니다.</div></section>
<div class="cancel-actions"><a href="<%= cancelContext %>/app/order/mypage?orderNo=<%= ProductView.escape(cancelOrder.get("orderNo")) %>">이전</a><button id="cancel-apply" type="button">취소 신청하기</button></div>
<dialog class="cancel-confirm" id="cancel-confirm" aria-labelledby="cancel-confirm-title"><div class="cancel-confirm-head"><h2 id="cancel-confirm-title">취소 신청하시겠습니까?</h2><button id="cancel-confirm-close" class="cancel-confirm-close" type="button" aria-label="닫기">×</button></div><label class="cancel-restore-label"><input type="checkbox" id="cancel-restore-cart" checked>장바구니에 다시 담기</label><p id="cancel-submit-error" class="cancel-error" role="alert"></p><div class="cancel-confirm-actions"><button id="cancel-confirm-back" type="button">이전</button><button class="primary" id="cancel-confirm-submit" type="button">확인</button></div></dialog>
<input type="hidden" id="cancel-order-no" value="<%= ProductView.escape(cancelOrder.get("orderNo")) %>"><input type="hidden" id="cancel-csrf" value="<%= ProductView.escape(request.getAttribute("cancelCsrf")) %>">
<% } %></div></div>
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
<script>
document.addEventListener('DOMContentLoaded',function(){
 const toggle=document.getElementById('cancel-reason-toggle');if(!toggle)return;
 const options=document.getElementById('cancel-reasons'),other=document.getElementById('cancel-other'),error=document.getElementById('cancel-reason-error'),dialog=document.getElementById('cancel-confirm');
 let reason='';
 function closeReasons(){options.hidden=true;toggle.setAttribute('aria-expanded','false');document.getElementById('cancel-reason-arrow').textContent='⌄';}
 toggle.addEventListener('click',function(){const show=options.hidden;options.hidden=!show;toggle.setAttribute('aria-expanded',String(show));document.getElementById('cancel-reason-arrow').textContent=show?'⌃':'⌄';});
 options.querySelectorAll('button').forEach(function(button){button.addEventListener('click',function(){reason=button.dataset.cancelReason;document.getElementById('cancel-reason-text').textContent=reason;closeReasons();other.hidden=reason!=='기타';error.textContent='';if(!other.hidden)other.focus();else toggle.focus();});});
 document.addEventListener('click',function(event){if(!event.target.closest('.cancel-reason-wrap'))closeReasons();});
 document.addEventListener('keydown',function(event){if(event.key==='Escape')closeReasons();});
 document.getElementById('cancel-apply').addEventListener('click',function(){if(!reason){error.textContent='취소 사유를 선택해 주세요.';toggle.focus();return;}if(reason==='기타'&&!other.value.trim()){error.textContent='기타 취소 사유를 입력해 주세요.';other.focus();return;}error.textContent='';document.getElementById('cancel-submit-error').textContent='';dialog.showModal();});
 document.getElementById('cancel-confirm-close').addEventListener('click',function(){dialog.close();});document.getElementById('cancel-confirm-back').addEventListener('click',function(){dialog.close();});
 const submitCancel =
	    document.getElementById('cancel-confirm-submit');

	submitCancel.addEventListener('click', async function () {
	    if (submitCancel.disabled) return;

	    const submitError =
	        document.getElementById('cancel-submit-error');

	    submitError.textContent = '';

	    if (!reason) {
	        submitError.textContent = '취소 사유를 선택해 주세요.';
	        return;
	    }

	    if (reason === '기타' && !other.value.trim()) {
	        submitError.textContent = '기타 취소 사유를 입력해 주세요.';
	        return;
	    }

	    const orderNo =
	        document.getElementById('cancel-order-no').value;

	    const data = new URLSearchParams();

	    data.set('orderNo', orderNo);
	    data.set(
	        'csrfToken',
	        document.getElementById('cancel-csrf').value
	    );
	    data.set('reason', reason);
	    data.set(
	        'reasonDetail',
	        reason === '기타' ? other.value.trim() : ''
	    );
	    data.set(
	        'restoreCart',
	        String(document.getElementById('cancel-restore-cart').checked)
	    );

	    submitCancel.disabled = true;
	    submitCancel.textContent = '처리 중…';

	    try {
	        const response = await fetch(
	            '<%= cancelContext %>/app/order/cancel/request',
	            {
	                method: 'POST',
	                body: data
	            }
	        );

	        if (!response.ok) {
	            throw new Error(
	                '취소 처리 결과를 확인하지 못했습니다. '
	                + '주문내역을 확인해 주세요.'
	            );
	        }

	        const result = (await response.text()).trim();

	        if (result !== 'OK') {
	            submitError.textContent = result;
	            return;
	        }

	        window.location.assign(
	            '<%= cancelContext %>/app/order/cancel/complete'
	            + '?orderNo=' + encodeURIComponent(orderNo)
	        );

	    } catch (error) {
	        submitError.textContent = error.message;

	    } finally {
	        submitCancel.disabled = false;
	        submitCancel.textContent = '확인';
	    }
	});
 });

</script>
</body>
</html>
