<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page import="daiso.member.controller.MemberProfileInfo"%>
<%@ page import="daiso.member.controller.AdminMemberConsentInfo"%>
<%@ page
	import="java.util.Map,java.util.List,java.util.Collections,daiso.cart.db.CartBean,daiso.product.controller.ProductView"%>
<%@ page import="java.util.Set"%>
<%
String myContext = request.getContextPath();
String productActivity = request.getParameter("productActivity");
if (productActivity == null || !Set.of("wishlist", "recent", "frequent", "restock", "reviews").contains(productActivity)
        || "profile".equals(request.getAttribute("myPageSection"))) {
    productActivity = "";
}
Set<String> linkedSocialProviders =
(Set<String>) request.getAttribute("linkedSocialProviders");

if (linkedSocialProviders == null) {
linkedSocialProviders = Collections.emptySet();
}

boolean kakaoLinked = linkedSocialProviders.contains("KAKAO");
boolean naverLinked = linkedSocialProviders.contains("NAVER");
boolean googleLinked = linkedSocialProviders.contains("GOOGLE");
Map<String, Object> myOrder = (Map<String, Object>) request.getAttribute("myOrder");
Map<String, Boolean> orderReviewStates = (Map<String, Boolean>) request.getAttribute("orderReviewStates");
if (orderReviewStates == null)
    orderReviewStates = Collections.emptyMap();
List<Map<String, Object>> myOrders = (List<Map<String, Object>>) request.getAttribute("myOrders");
if (myOrders == null)
	myOrders = Collections.emptyList();
Map<String, String> myAddress = myOrder != null && myOrder.get("address") instanceof Map
		? (Map<String, String>) myOrder.get("address")
		: Collections.emptyMap();
Object profileAddressValue = request.getAttribute("profileDeliveryAddress");
Map<String, String> profileDeliveryAddress = profileAddressValue instanceof Map
		? (Map<String, String>) profileAddressValue
		: Collections.emptyMap();
List<Map<String, String>> profileDeliveryAddresses =
(List<Map<String, String>>) request.getAttribute("profileDeliveryAddresses");

if (profileDeliveryAddresses == null) {
profileDeliveryAddresses = Collections.emptyList();
}

String[] deliveryFields = {
"addressName", "recipient", "phone", "postcode", "address1", "address2"
};
Object pickupStoreValue = request.getAttribute("profilePickupStore");
Map<String, String> profilePickupStore = pickupStoreValue instanceof Map
		? (Map<String, String>) pickupStoreValue
		: Collections.emptyMap();

String orderCsrfToken = String.valueOf(request.getAttribute("orderCsrf"));
Object consentCsrfValue =
request.getAttribute("memberConsentCsrf");
String memberConsentCsrf =
consentCsrfValue instanceof String
        ? (String) consentCsrfValue
        : "";

AdminMemberConsentInfo profileConsentSettings =
(AdminMemberConsentInfo) request.getAttribute(
        "profileConsentSettings"
);
String profileConsentError =
(String) request.getAttribute("profileConsentError");

String deliveryName = profileDeliveryAddress.getOrDefault("recipient", "");
String maskedDeliveryName = deliveryName;
if (deliveryName.length() == 2) {
	maskedDeliveryName = deliveryName.substring(0, 1) + "*";
} else if (deliveryName.length() > 2) {
	maskedDeliveryName = deliveryName.substring(0, 1) + "*".repeat(deliveryName.length() - 2)
	+ deliveryName.substring(deliveryName.length() - 1);
}

String deliveryPhone = profileDeliveryAddress.getOrDefault("phone", "");
String deliveryPhoneDigits = deliveryPhone.replaceAll("\\D", "");
String maskedDeliveryPhone = "****";
if (deliveryPhoneDigits.length() == 11) {
	maskedDeliveryPhone = deliveryPhoneDigits.substring(0, 3) + "-" + deliveryPhoneDigits.substring(3, 7) + "-****";
} else if (deliveryPhoneDigits.length() == 10) {
	maskedDeliveryPhone = deliveryPhoneDigits.substring(0, 3) + "-***-****";
}

String deliveryPostcode = profileDeliveryAddress.getOrDefault("postcode", "");
if (deliveryPostcode.length() == 5) {
	deliveryPostcode = deliveryPostcode.substring(0, 3) + "**";
}

String deliveryAddress1 = profileDeliveryAddress.getOrDefault("address1", "").replaceAll("\\d", "*");
String deliveryAddress2 = profileDeliveryAddress.getOrDefault("address2", "").isEmpty() ? "" : "상세주소 비공개";
List<CartBean> myItems = myOrder != null ? (List<CartBean>) myOrder.get("items") : Collections.emptyList();
java.text.NumberFormat myMoney = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
<%@ include file="../common/head.jspf"%>
<link rel="stylesheet"
	href="${pageContext.request.contextPath}/assets/css/my-product-activity.css">
<% if (Set.of("wishlist", "recent", "frequent").contains(productActivity)) { %>
<link rel="stylesheet"
	href="${pageContext.request.contextPath}/assets/css/product-wishlist.css">
<script defer
	src="${pageContext.request.contextPath}/assets/js/common/product-wishlist.js"></script>
<% } %>
<script defer
	src="${pageContext.request.contextPath}/assets/js/common/my-product-activity.js"></script>
	<script defer
        src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"></script>
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
	line-height: 1.5;
	color: inherit;
	text-decoration: none;
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

.my-item-actions {
	display: flex;
	flex-wrap: wrap;
	justify-content: flex-end;
	align-items: center;
	gap: 8px;
}

.my-item .my-review-action {
	display: inline-flex;
	align-items: center;
	min-height: 36px;
	border: 1px solid #8390a0;
	border-radius: 24px;
	padding: 6px 14px;
	color: #192638;
	background: #fff;
	font-size: 13px;
	font-weight: 650;
	text-decoration: none;
	white-space: nowrap;
}

.my-item .my-review-action:hover { background: #f4f5f7; }
.my-item .my-review-action:focus-visible { outline: 2px solid #365f8c; outline-offset: 2px; }

@media (max-width: 580px) {
	.my-item-actions { flex-direction: column; align-items: flex-end; gap: 5px; }
	.my-item .my-review-action { padding-inline: 8px; font-size: 11px; }
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

.my-profile-action {
	display: block;
	width: calc(100% - 195px);
	min-height: 42px;
	margin: 24px 0 48px 195px;
	border: 0;
	background: #17212a;
	color: #fff;
	font-weight: 700;
	cursor: pointer;
}

#profileVerifyDialog {
	width: min(420px, calc(100% - 32px));
	border: 0;
	padding: 28px;
}

#profileVerifyDialog::backdrop {
	background: rgba(0, 0, 0, .7);
}

#profileVerifyDialog input:not([type="button"]) {
	display: block;
	width: 100%;
	height: 42px;
	margin: 10px 0 18px;
	border: 1px solid #ccd1d8;
	padding: 0 10px;
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

.my-home-shortcuts a, .my-home-shortcuts button {
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

.my-home-shortcuts a:last-child, .my-home-shortcuts button:last-child {
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

@media ( max-width : 700px) {
	.my-home-shortcuts {
		grid-template-columns: repeat(4, minmax(0, 1fr));
		row-gap: 12px;
	}
}

.delivery-pickup-dialog {
	width: min(496px, calc(100vw - 32px));
	height: min(750px, calc(100vh - 40px));
	max-width: none;
	max-height: none;
	margin: auto;
	padding: 0;
	border: 0;
	color: #111;
	box-sizing: border-box;
	overflow-y: auto;
}

.delivery-pickup-dialog::backdrop {
	background: rgba(0, 0, 0, 0.76);
}

.delivery-pickup-header {
	height: 72px;
	padding: 0 24px;
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.delivery-pickup-header h2 {
	margin: 0;
	font-size: 20px;
	font-weight: 700;
}

#closeDeliveryPickupDialog {
	border: 0;
	padding: 0;
	background: transparent;
	color: #17212a;
	font-size: 30px;
	line-height: 1;
	cursor: pointer;
}

.delivery-pickup-tabs {
	display: grid;
	grid-template-columns: 1fr 1fr;
	height: 44px;
	border-bottom: 1px solid #e2e5e9;
}

.delivery-pickup-tab {
	border: 0;
	border-bottom: 2px solid transparent;
	background: #fff;
	color: #65758a;
	font-size: 14px;
	cursor: pointer;
}

.delivery-pickup-tab.is-active {
	border-bottom-color: #17212a;
	color: #111;
	font-weight: 700;
}

#deliveryManagementPanel, #pickupManagementPanel {
	padding: 24px;
}

#deliveryManagementPanel>p:first-of-type {
	margin: 0;
	padding: 18px 20px 10px;
	border: 1px solid #e2e5e9;
	font-size: 16px;
}

#deliveryManagementPanel>p:nth-of-type(2) {
	margin: 0;
	padding: 8px 20px 18px;
	border: 1px solid #e2e5e9;
	border-top: 0;
	line-height: 1.6;
}

#deliveryManagementPanel>button, #deliveryAddressForm button,
	#pickupStoreForm button {
	min-height: 42px;
	border: 1px solid #e2e5e9;
	background: #fff;
	color: #17212a;
	cursor: pointer;
}

#deliveryManagementPanel>button {
	width: 100%;
	margin-bottom: 22px;
	font-weight: 700;
}

#deliveryAddressForm {
	margin-top: 20px;
}

#deliveryAddressForm label {
	display: block;
	margin: 14px 0;
	color: #65758a;
	font-size: 13px;
}

#deliveryAddressForm input {
	display: block;
	width: 100%;
	height: 40px;
	margin-top: 6px;
	padding: 0 10px;
	border: 1px solid #d8dde4;
	box-sizing: border-box;
}

#pickupStoreForm label {
	display: block;
	padding: 20px;
	border: 1px solid #e2e5e9;
	line-height: 1.7;
}

#pickupStoreForm small {
	display: block;
	color: #65758a;
}

#deliveryManagementPanel>.delivery-count {
	margin: 0 0 26px;
	padding: 0;
	border: 0;
	font-size: 14px;
}

.delivery-address-card {
	position: relative;
	margin-bottom: 16px;
	padding: 20px;
	border: 1px solid #e2e5e9;
}

.delivery-address-card h3 {
	margin: 0 32px 12px 0;
	font-size: 16px;
}

.delivery-address-delete {
	position: absolute;
	top: 14px;
	right: 16px;
	color: #17212a;
	font-size: 24px;
}

.delivery-address-badges {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-bottom: 10px;
}

.delivery-address-badges span {
	padding: 4px 7px;
	background: #f0f1f3;
	font-size: 11px;
}

.delivery-address-card .delivery-address-text, .delivery-address-card .delivery-address-person,
	.delivery-address-card .delivery-address-request {
	margin: 8px 0;
	line-height: 1.6;
	overflow-wrap: anywhere;
}

.delivery-address-person, .delivery-address-request {
	color: #65758a;
}

.delivery-address-edit {
	min-height: 26px !important;
	margin-top: 6px;
	padding: 2px 8px;
	font-size: 12px;
}

.delivery-address-notice {
	padding: 12px 16px;
	background: #f5f6f7;
	color: #65758a;
	font-size: 12px;
}

.delivery-address-notice p {
	margin: 3px 0;
}

.delivery-empty-message {
	padding: 20px 0;
}

.member-login-title {
	margin-top: 42px;
}

.member-login-info {
	border-top: 1px solid #e2e5e9;
}

.member-login-row {
	display: grid;
	grid-template-columns: 218px minmax(0, 1fr);
	align-items: center;
	min-height: 54px;
	padding: 12px 0;
}

.member-login-label {
	color: #667b94;
}

.member-login-value {
	display: flex;
	align-items: center;
	gap: 12px;
}

.member-login-eye {
	border: 0;
	padding: 0;
	background: transparent;
	font-size: 20px;
	cursor: pointer;
}

.member-login-password {
	width: 197px;
	min-height: 30px;
	border: 1px solid #e2e5e9;
	background: #fff;
	cursor: pointer;
}

.member-email-change-button {
	margin-left: auto;
	width: 197px;
	min-height: 30px;
	border: 1px solid #e2e5e9;
	background: #fff;
	cursor: pointer;
}

.member-email-editor {
	border-top: 1px solid #e2e5e9;
	padding: 20px 0;
}

.member-email-edit-content {
	min-width: 0;
	width: 100%;
}

.member-email-inputs {
	display: grid;
	grid-template-columns: minmax(120px, 1fr) auto minmax(150px, 1fr) auto;
	align-items: center;
	gap: 12px;
}

.member-email-inputs input, .member-email-inputs select {
	width: 100%;
	min-width: 0;
	height: 48px;
	padding: 0 12px;
	border: 1px solid #ccd1d8;
	background: #fff;
	font: inherit;
}

.member-email-inputs button {
	min-width: 150px;
	height: 48px;
	border: 1px solid #dfe4eb;
	background: #fff;
	font-weight: 700;
	cursor: pointer;
}

.member-email-edit-row .member-login-label em {
	color: #e60023;
	font-style: normal;
}

.member-email-message {
	min-height: 22px;
	margin: 8px 0;
	color: #e60023;
	font-size: 13px;
}

.member-email-save-button {
	width: 100%;
	min-height: 48px;
	border: 0;
	background: #17212a;
	color: #fff;
	font-weight: 700;
	cursor: pointer;
}

.member-email-save-button:disabled {
	background: #e7e9ec;
	color: #aeb5bd;
	cursor: default;
}

@media ( max-width : 700px) {
	.member-email-change-button {
		width: auto;
		padding: 0 12px;
	}
	.member-email-inputs {
		grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
		gap: 8px;
	}
	.member-email-inputs button {
		grid-column: 1/-1;
		width: 100%;
	}
	.member-email-edit-row {
		grid-template-columns: 1fr;
		gap: 10px;
	}
}

.member-social-row {
	align-items: start;
	padding-top: 24px;
}

.member-kakao-link {
	display: flex;
	align-items: center;
	gap: 8px;
	min-height: 40px;
}

.member-kakao-link button {
	border: 0;
	padding: 0;
	background: transparent;
	text-decoration: underline;
	cursor: pointer;
}

.member-kakao-icon {
	display: inline-grid;
	width: 20px;
	height: 20px;
	place-items: center;
	border-radius: 50%;
	background: #fee500;
	color: #111;
	font-size: 10px;
}

.member-other-social {
	display: block;
	width: 100%;
	margin-top: 10px;
	padding: 16px 0;
	border: 0;
	border-top: 1px solid #e2e5e9;
	background: #fff;
	text-align: left;
	cursor: pointer;
}

.member-social-buttons {
	display: flex;
	gap: 8px;
}

.member-social-buttons button {
	width: 240px;
	min-height: 40px;
	cursor: pointer;
}

.member-naver-button {
	border: 0;
	background: #03c75a;
	color: #fff;
	font-weight: 700;
}

.member-apple-button {
	border: 1px solid #111;
	background: #fff;
	color: #111;
	font-weight: 700;
}

.member-login-social {
	width: 100%;
}

.member-linked-social-list {
	display: flex;
	flex-direction: column;
	gap: 8px;
}

.member-linked-social, .member-kakao-link {
	display: flex;
	align-items: center;
	gap: 8px;
	min-height: 32px;
	color: #111;
}

.member-linked-social button, .member-kakao-link button {
	border: 0;
	padding: 0;
	background: transparent;
	color: #111;
	text-decoration: underline;
	cursor: pointer;
}

.member-linked-icon {
	display: inline-grid;
	width: 20px;
	height: 20px;
	place-items: center;
	border-radius: 50%;
	font-size: 12px;
	font-weight: 700;
}

.member-linked-icon-naver {
	background: #03c75a;
	color: #fff;
}

.member-linked-icon-google {
	background: #f1f3f4;
	color: #4285f4;
}

.member-social-empty {
	margin: 0;
	min-height: 32px;
	color: #667b94;
	font-size: 13px;
}

.member-social-help {
	color: #667b94;
	font-size: 13px;
}

.member-social-buttons {
	flex-wrap: wrap;
}

.member-social-buttons button {
	width: 275px;
	min-height: 40px;
}

.member-kakao-button {
	border: 0;
	background: #fee500;
	color: #111;
	font-weight: 700;
}

.member-google-button {
	border: 1px solid #111;
	background: #fff;
	color: #111;
	font-weight: 700;
}

@media ( max-width : 700px) {
	.member-social-buttons button {
		width: 100%;
	}
}

@media ( max-width : 700px) {
	.member-login-row {
		grid-template-columns: 95px minmax(0, 1fr);
	}
	.member-social-buttons button {
		width: 50%;
	}
}

.my-screen .member-consent-section {
	margin-top: 60px;
	color: #17212a;
}

.my-screen .member-consent-section h3 {
	margin: 0 0 28px;
	font-size: 27px;
	font-weight: 800;
}

.my-screen .member-consent-section .member-consent-row {
	display: grid;
	grid-template-columns: 218px minmax(0, 1fr);
	margin-bottom: 30px;
}

.my-screen .member-consent-label {
	padding-top: 4px;
	color: #667b94;
}

.my-screen .member-consent-content {
	min-width: 0;
}

.my-screen .member-consent-title, .my-screen .member-consent-check,
	.my-screen .member-consent-options, .my-screen .member-retention-options
	{
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 12px;
}

.my-screen .member-consent-title {
	margin-bottom: 12px;
}

.my-screen .member-consent-title button, .my-screen .member-consent-check button,
	.my-screen .member-consent-footer button {
	border: 0;
	padding: 0;
	background: transparent;
	color: #17212a;
	text-decoration: underline;
	cursor: pointer;
	font: inherit;
}

.my-screen .member-consent-options label, .my-screen .member-retention-options label
	{
	display: inline-flex;
	align-items: center;
	gap: 7px;
}

.my-screen .member-consent-section input[type="checkbox"], .my-screen .member-consent-section input[type="radio"]
	{
	width: 20px;
	height: 20px;
	margin: 0;
	accent-color: #17212a;
}

.my-screen .member-consent-muted {
	color: #9ca7b5;
}

.my-screen .member-consent-check {
	margin-bottom: 14px;
}

.my-screen .member-consent-notice {
	margin: 28px 0 0;
	padding: 14px 18px 14px 30px;
	background: #f6f7f9;
	color: #667b94;
	font-size: 12px;
	line-height: 1.6;
}

.my-screen .member-retention-content {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20px;
}

.my-screen .member-retention-options {
	gap: 22px;
}

.my-screen .member-retention-save {
    width: 197px;
    min-height: 30px;
    border: 0;
    background: #17212a;
    color: #fff;
    cursor: pointer;
}
.my-screen .member-consent-footer {
	display: flex;
	justify-content: space-between;
	margin-top: 78px;
	padding-top: 18px;
	border-top: 1px solid #e2e5e9;
}

@media ( max-width : 700px) {
	.my-screen .member-consent-section h3 {
		font-size: 23px;
	}
	.my-screen .member-consent-section .member-consent-row {
		grid-template-columns: 1fr;
		gap: 10px;
	}
	.my-screen .member-retention-content {
		align-items: flex-start;
		flex-direction: column;
	}
	.my-screen .member-retention-save {
		width: 100%;
	}
}

.profile-password-dialog {
	width: min(440px, calc(100% - 32px));
	border: 0;
	padding: 28px;
	color: #17212a;
}

.profile-password-dialog::backdrop {
	background: rgba(0, 0, 0, .65);
}

.profile-password-dialog h2 {
	margin: 0 0 24px;
	font-size: 22px;
}

.profile-password-dialog label {
	display: block;
	margin-bottom: 16px;
	font-size: 14px;
}

.profile-password-dialog input {
	display: block;
	width: 100%;
	height: 44px;
	margin-top: 8px;
	padding: 0 12px;
	border: 1px solid #ccd1d8;
	box-sizing: border-box;
}

.profile-password-hint {
	margin: -8px 0 16px;
	color: #667b94;
	font-size: 12px;
}

.profile-password-message {
	min-height: 20px;
	color: #e60023;
	font-size: 13px;
}

.profile-password-actions {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
	margin-top: 20px;
}

.profile-password-actions button {
	min-width: 100px;
	height: 40px;
	border: 1px solid #dfe4eb;
	background: #fff;
	cursor: pointer;
}

.profile-password-actions button[type="submit"] {
	border-color: #17212a;
	background: #17212a;
	color: #fff;
}
#demoRefundDialog {
    width: min(520px, calc(100vw - 32px));
    height: auto;
    max-height: calc(100vh - 32px);
    border-radius: 16px;
}

#demoRefundForm {
    display: grid;
    gap: 14px;
}

#demoRefundForm p {
    margin: 0;
}

#demoRefundForm select,
#demoRefundForm input[name="accountNumber"],
#demoRefundForm input[name="holderName"] {
    display: block;
    width: 100%;
    box-sizing: border-box;
    min-height: 44px;
    margin-top: 6px;
    padding: 8px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    font: inherit;
}

#demoRefundForm .refund-holder-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: end;
    gap: 8px;
}

#demoRefundForm .refund-actions {
    display: flex;
    gap: 8px;
    padding-top: 14px;
    border-top: 1px solid #e1e5eb;
}

#demoRefundForm .refund-actions button {
    flex: 1;
}

#demoRefundForm button {
    min-height: 44px;
    padding: 8px 12px;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    background: #fff;
    font: inherit;
    cursor: pointer;
}

#demoRefundForm button[type="reset"] {
    justify-self: start;
}

#demoRefundForm button[value="save"] {
    background: #25354a;
    color: #fff;
}

#demoRefundForm button:disabled {
    background: #e2e8f0;
    color: #475569;
    cursor: default;
}

@media (max-width: 420px) {
    #demoRefundForm .refund-holder-row {
        grid-template-columns: 1fr;
    }
}
#notificationNotice {
    width: min(420px, calc(100% - 32px));
    box-sizing: border-box;
    margin: auto;
    padding: 24px;
    border: 1px solid #e1e5eb;
    border-radius: 14px;
}

#notificationNotice::backdrop {
    background: rgba(15, 23, 42, 0.62);
}

#notificationNotice h2 {
    margin: 0 0 12px;
    font-size: 20px;
}

#notificationNotice p {
    margin: 0 0 20px;
    color: #475569;
}

#notificationNotice form {
    display: flex;
    justify-content: flex-end;
}

#notificationNotice button {
    min-height: 44px;
    padding: 0 24px;
    border: 0;
    border-radius: 8px;
    background: #17212a;
    color: #fff;
    font-weight: 700;
    cursor: pointer;
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
				<span class="my-member-tag">개인</span><a class="my-member-name"
					href="<%=myContext%>/app/member/profile"> <%=ProductView.escape(request.getAttribute("memberName"))%>님
					›
				</a>
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
					<a class="<%= productActivity.isEmpty() ? "my-current" : "" %>"
						href="<%=myContext%>/app/order/mypage">주문내역</a>
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
					<a href="<%=myContext%>/app/order/mypage?productActivity=wishlist"
						<%= "wishlist".equals(productActivity) ? "class=\"my-current\" aria-current=\"page\"" : "" %>>찜</a>
					<a href="<%=myContext%>/app/order/mypage?productActivity=reviews"
						<%= "reviews".equals(productActivity) ? "class=\"my-current\" aria-current=\"page\"" : "" %>>리뷰</a>
					<a href="<%=myContext%>/app/order/mypage?productActivity=recent"
						<%= "recent".equals(productActivity) ? "class=\"my-current\" aria-current=\"page\"" : "" %>>최근
						본</a> <a
						href="<%=myContext%>/app/order/mypage?productActivity=frequent"
						<%= "frequent".equals(productActivity) ? "class=\"my-current\" aria-current=\"page\"" : "" %>>자주
						산</a>
					<button type="button" data-my-unavailable>이벤트 참여내역</button>
					<a href="<%=myContext%>/app/order/mypage?productActivity=restock"
						<%= "restock".equals(productActivity) ? "class=\"my-current\" aria-current=\"page\"" : "" %>>상품
						입고 알림</a>
				</section>
				<section class="my-menu-group">
					<h2>나의 정보</h2>
					<a
						class="<%="profile".equals(request.getAttribute("myPageSection")) ? "my-current" : ""%>"
						href="<%=myContext%>/app/member/profile">회원정보</a>
					<button type="button" data-my-unavailable>프로필</button>
					<a href="<%=myContext%>/app/order/mypage?section=profile&amp;manage=delivery">배송지/픽업매장</a>						
					<a href="<%=myContext%>/app/order/mypage?section=profile&amp;manage=refund">환불계좌</a>				    
					<button type="button" id="openNotificationNotice" aria-haspopup="dialog">알림목록</button>
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
			<dialog id="notificationNotice"
				aria-labelledby="notificationNoticeTitle"
				aria-describedby="notificationNoticeText">
			<h2 id="notificationNoticeTitle">알림 목록 안내</h2>
			<p id="notificationNoticeText">현재 웹에서는 알림 목록을 확인할 수 없습니다.</p>
			<form method="dialog">
				<button type="submit" autofocus>확인</button>
			</form>
			</dialog>
			<div class="my-content">
				<%
				if ("reviews".equals(productActivity)) {
                    %>
				<%@ include file="../product/my-reviews.jspf"%>
				<%
                } else if (!productActivity.isEmpty()) {
                    %>
				<%@ include file="../product/my-activity.jspf"%>
				<%
                } else if ("profile".equals(request.getAttribute("myPageSection"))) {
					MemberProfileInfo profile = (MemberProfileInfo) request.getAttribute("profile");

					String genderText = "선택 안 함";
					if (profile != null && "MALE".equals(profile.getGender())) {
						genderText = "남자";
					} else if (profile != null && "FEMALE".equals(profile.getGender())) {
						genderText = "여자";
					}
					String profileName = profile == null || profile.getName() == null ? "" : profile.getName().trim();

					String maskedName = profileName;
					if (profileName.length() == 1) {
						maskedName = "*";
					} else if (profileName.length() == 2) {
						maskedName = profileName.substring(0, 1) + "*";
					} else if (profileName.length() > 2) {
						maskedName = profileName.substring(0, 1) + "*".repeat(profileName.length() - 2)
						+ profileName.substring(profileName.length() - 1);
					}

					String phoneDigits = profile == null || profile.getPhone() == null ? "" : profile.getPhone().replaceAll("\\D", "");

					String maskedPhone = "미입력";
					if (phoneDigits.length() == 11) {
						maskedPhone = phoneDigits.substring(0, 3) + "-" + phoneDigits.substring(3, 7) + "-****";
					} else if (phoneDigits.length() == 10) {
						maskedPhone = phoneDigits.substring(0, 3) + "-***-****";
					}

					String maskedBirthDate = profile != null && profile.getBirthDate() != null ? "****년 **월 **일" : "미입력";
				%>

				<section class="my-section">
					<div class="my-section-heading">
						<h2>회원정보</h2>
					</div>

					<%
					if (request.getAttribute("profileError") != null) {
					%>
					<p class="error-message" role="alert">
						<%=ProductView.escape(request.getAttribute("profileError"))%>
					</p>
					<%
					} else if (profile != null) {
					%>
					<h3>기본 정보</h3>

					<%
					if (Boolean.TRUE.equals(request.getAttribute("profileEditMode"))) {
					%>
					<form id="basicProfileForm">
						<table class="my-data-table">
							<tbody>
								<tr>
									<th scope="row"><label for="profile-name">이름</label></th>
									<td><input id="profile-name" name="name" maxlength="100"
										value="<%=ProductView.escape(profile.getName())%>" required>
									</td>
								</tr>
								<tr>
									<th scope="row"><label for="profile-phone">휴대폰</label></th>
									<td><input id="profile-phone" name="phone" type="tel"
										maxlength="30" readonly
										value="<%=ProductView.escape(profile.getPhone())%>" required>
									</td>
								</tr>
								<tr>
									<th scope="row"><label for="profile-birth-date">생년월일</label></th>
									<td><input id="profile-birth-date" name="birthDate"
										type="date"
										value="<%=profile.getBirthDate() == null ? "" : profile.getBirthDate().toString()%>">
									</td>
								</tr>
								<tr>
									<th scope="row">성별</th>
									<td><label> <input type="radio" name="gender"
											value="MALE"
											<%="MALE".equals(profile.getGender()) ? "checked" : ""%>>
											남자
									</label> <label> <input type="radio" name="gender"
											value="FEMALE"
											<%="FEMALE".equals(profile.getGender()) ? "checked" : ""%>>
											여자
									</label></td>
								</tr>
							</tbody>
						</table>

						<p id="profileUpdateMessage" role="status"></p>
						<button type="submit" class="my-profile-action">저장</button>
					</form>

					<%
					} else {
					%>
					<table class="my-data-table">
						<tbody>
							<tr>
								<th scope="row">이름</th>
								<td><%=ProductView.escape(maskedName)%></td>
							</tr>
							<tr>
								<th scope="row">휴대폰</th>
								<td><%=ProductView.escape(maskedPhone)%></td>
							</tr>
							<tr>
								<th scope="row">생년월일</th>
								<td><%=ProductView.escape(maskedBirthDate)%></td>
							</tr>
							<tr>
								<th scope="row">성별(선택)</th>
								<td><%=ProductView.escape(genderText)%></td>
							</tr>
						</tbody>
					</table>

					<button type="button" id="openProfileVerification"
						class="my-profile-action">기본정보 확인 및 변경</button>

					<dialog id="profileVerifyDialog"
						aria-labelledby="profileVerifyTitle">
					<h2 id="profileVerifyTitle">본인인증</h2>
					<p>등록된 휴대폰 번호로 인증번호를 받으세요.</p>
					<button type="button" id="sendProfileCode">인증번호 받기</button>
					<label for="profileVerifyCode">인증번호</label> <input
						id="profileVerifyCode" inputmode="numeric" maxlength="6"
						autocomplete="one-time-code">
					<p id="profileVerifyMessage" role="alert"></p>
					<button type="button" id="closeProfileVerify">취소</button>
					<button type="button" id="submitProfileVerify">인증 확인</button>
					</dialog>
					<%
					}
					%>
					<h3>배송/픽업 정보</h3>

					<table class="my-data-table">
						<tbody>
							<tr>
								<th scope="row">배송지</th>
								<td>
									<%
									if (!profileDeliveryAddress.isEmpty()) {
									%> <strong> <%=ProductView.escape(profileDeliveryAddress.get("addressName"))%>
										(<%=ProductView.escape(maskedDeliveryName)%>)
								</strong><br> [<%=ProductView.escape(deliveryPostcode)%>] <%=ProductView.escape(deliveryAddress1)%>
									<%=ProductView.escape(deliveryAddress2)%><br> <%=ProductView.escape(maskedDeliveryPhone)%>
									<%
									} else {
									%> 등록된 배송지가 없습니다. <%
									}
									%>
								</td>
							</tr>
							<tr>
								<th scope="row">픽업매장</th>
								<td>
									<%
									if (!profilePickupStore.isEmpty()) {
									%> <strong> <%=ProductView.escape(profilePickupStore.get("storeName"))%>
								</strong><br> <%=ProductView.escape(profilePickupStore.get("city"))%>
									<%
									} else {
									%> 선택된 픽업매장이 없습니다. <%
									}
									%>
								</td>
							</tr>
						</tbody>
					</table>

					<button type="button" id="openDeliveryPickupManager"
						class="my-profile-action">배송지/픽업매장 관리</button>

					<dialog id="deliveryPickupDialog" class="delivery-pickup-dialog">
					<div class="delivery-pickup-header">
						<h2>배송지/픽업매장 관리</h2>
						<button type="button" id="closeDeliveryPickupDialog"
							aria-label="닫기">×</button>
					</div>

					<div class="delivery-pickup-tabs" role="tablist">
						<button type="button" class="delivery-pickup-tab is-active"
							data-delivery-tab="delivery" aria-selected="true">배송지 관리
						</button>
						<button type="button" class="delivery-pickup-tab"
							data-delivery-tab="pickup" aria-selected="false">픽업매장 관리
						</button>
					</div>

					<section id="deliveryManagementPanel">
						<button type="button" class="delivery-add-button"
							data-add-delivery>
							<span aria-hidden="true">⊕</span> 새 배송지 추가
						</button>

						<p class="delivery-count">
							전체
							<%=profileDeliveryAddresses.size()%>개
						</p>

						<%
						if (!profileDeliveryAddresses.isEmpty()) {
						    for (Map<String, String> address : profileDeliveryAddresses) {
						%>
						<article class="delivery-address-card">
							<span class="delivery-address-delete" aria-hidden="true">×</span>

							<h3>
								[<%=ProductView.escape(address.get("addressName"))%>]
							</h3>

							<div class="delivery-address-badges">
								<span>택배배송가능</span> <span>오늘배송가능</span> <span>대량주문가능</span>
							</div>

							<p class="delivery-address-text">
								(<%=ProductView.escape(address.get("postcode"))%>)
								<%=ProductView.escape(address.get("address1"))%>
								<%=ProductView.escape(address.get("address2"))%>
							</p>

							<p class="delivery-address-person">
								<%=ProductView.escape(address.get("recipient"))%>
								&nbsp;
								<%=ProductView.escape(address.get("phone"))%>
							</p>

							<p class="delivery-address-request">[요청사항] 요청사항 없음</p>

							<button type="button" class="delivery-address-edit"
								data-edit-delivery
								data-address-id="<%=ProductView.escape(address.get("id"))%>"
								<%for (int i = 0; i < deliveryFields.length; i++) {%>
								data-field-<%=i%>="<%=ProductView.escape(address.get(deliveryFields[i]))%>"
        						<%}%>>수정</button>
						</article>
						<%} 
						} else {
						%>
						<p class="delivery-empty-message">
							<%=Boolean.TRUE.equals(request.getAttribute("profileDeliveryAddressLoadFailed"))
		? "배송지 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
		: "등록된 배송지가 없습니다."%>
						</p>
						<%
						}
						%>

						<div class="delivery-address-notice">
							<p>• 배송지에 따라 배송 유형이 달라질 수 있습니다.</p>
							<p>• 배송지는 로그인 세션에 저장됩니다.</p>
						</div>
 
						<form id="deliveryAddressForm" hidden>
							<input type="hidden" name="addressAction"> <input type="hidden"
								name="addressId"> <input type="hidden" name="csrfToken"
								value="<%=ProductView.escape(orderCsrfToken)%>"> <label>배송지명
							<input name="addressName" maxlength="50"
								value="<%=ProductView.escape(profileDeliveryAddress.get("addressName"))%>"
								required>
							</label> <label>받는 분 <input name="recipient" maxlength="100"
								value="<%=ProductView.escape(profileDeliveryAddress.get("recipient"))%>"
								required>
							</label> <label>휴대폰 <input name="phone" maxlength="30"
								value="<%=ProductView.escape(profileDeliveryAddress.get("phone"))%>"
								required>
							</label>
							<button type="button" id="findProfileAddress">주소 검색</button> 
							<label>우편번호 <input name="postcode" maxlength="5"
								value="<%=ProductView.escape(profileDeliveryAddress.get("postcode"))%>"
								required>
							</label>
							 <label>기본 주소 <input name="address1" maxlength="255"
								value="<%=ProductView.escape(profileDeliveryAddress.get("address1"))%>"
								required>
							</label> <label>상세 주소 <input name="address2" maxlength="255"
								value="<%=ProductView.escape(profileDeliveryAddress.get("address2"))%>">
							</label>

							<p id="deliveryAddressMessage" role="alert"></p>
							<button type="submit">저장</button>
						</form>
					</section>

					<section id="pickupManagementPanel" hidden>
    <%
    List<daiso.product.dao.StoreInventoryDAO.Location> pickupStores =
            (List<daiso.product.dao.StoreInventoryDAO.Location>)
                    request.getAttribute("profilePickupStores");
    %>

    <% if (Boolean.TRUE.equals(request.getAttribute("profilePickupLoadFailed"))) { %>
        <p role="alert">픽업매장 목록을 불러오지 못했습니다.</p>
    <% } else if (pickupStores == null || pickupStores.isEmpty()) { %>
        <p>등록된 픽업매장이 없습니다.</p>
    <% } else { %>
        <p>픽업매장을 선택해 주세요.</p>
        <form id="pickupStoreForm">
            <input type="hidden" name="csrfToken"
                   value="<%=ProductView.escape(orderCsrfToken)%>">

            <% for (daiso.product.dao.StoreInventoryDAO.Location store : pickupStores) { %>
                <label>
                    <input type="radio" name="storeId"
                           value="<%=ProductView.escape(store.id)%>"
                           <%=store.id.equals(profilePickupStore.get("storeId"))
                                   ? "checked" : ""%> required>
                    <%=ProductView.escape(store.name)%>
                    <small><%=ProductView.escape(store.city)%></small>
                </label>
            <% } %>

            <p id="pickupStoreMessage" role="alert"></p>
            <button type="submit">픽업매장 선택</button>
        </form>
    <% } %>
</section>
					</dialog>
					<%
String demoRefundBank = (String) request.getAttribute("demoRefundBank");
%>

<dialog id="demoRefundDialog" class="delivery-pickup-dialog"
        aria-labelledby="demoRefundTitle">
    <div class="delivery-pickup-header">
        <h2 id="demoRefundTitle">환불계좌 관리</h2>
        <form method="dialog">
            <button type="submit" aria-label="닫기">×</button>
        </form>
    </div>

    <div style="padding: 1.25rem;">
        <p>가상의 계좌번호를 숫자 5~20자리로 입력해 주세요.
    예금주는 로그인한 계정의 회원 이름을 입력해 주세요.
    이 화면은 회원 이름 일치 여부를 확인하는 시연용입니다.</p>

        <strong>
    <%=ProductView.escape(demoRefundBank)%> ••••
    <%=ProductView.escape((String) request.getAttribute("demoRefundLastFour"))%>
</strong>

        <form id="demoRefundForm" method="post"
              action="<%=myContext%>/app/order/profile/demo-refund-account">
            <input type="hidden" name="csrfToken"
                   value="<%=ProductView.escape(orderCsrfToken)%>">

            <p>
                <label>은행
                    <select name="bank" required>
                        <option value="">은행을 선택해 주세요</option>
                        <% for (String bank : List.of("국민은행", "신한은행", "우리은행")) { %>
                            <option value="<%=bank%>"
                                    <%=bank.equals(demoRefundBank) ? "selected" : ""%>>
                                <%=bank%>
                            </option>
                        <% } %>
                    </select>
                </label>
            </p>

            <p>
                <label>계좌번호
                    <input name="accountNumber" inputmode="numeric"
                           pattern="[0-9]{5,20}" maxlength="20" required>
                </label>
            </p>

							<div class="refund-holder-row">
								<label>예금주 <input name="holderName" maxlength="20"
									required>
								</label>
								<button type="submit" value="verify">시연 계좌 확인</button>
							</div>

							<button type="reset">입력 초기화</button>

							<p>
                <label>
                    <input type="checkbox" name="consent" value="yes" required>
                    시연 데이터 이용에 동의합니다. 로그인 세션 동안만 보관됩니다.
                </label>
            </p>

							<p id="demoRefundMessage" role="status"></p>

							<div class="refund-actions">
								<%
								if (demoRefundBank != null) {
								%>
								<button type="submit" value="delete" formnovalidate>환불계좌
									삭제</button>
								<%
								}
								%>
								<button type="submit" value="save" disabled>저장</button>
							</div>
						</form>
    </div>
</dialog>
					<%
					String profileLoginId = profile.getLoginId() == null ? "" : profile.getLoginId();

					String maskedProfileLoginId = profileLoginId.length() > 3
							? profileLoginId.substring(0, 3) + "*".repeat(profileLoginId.length() - 3)
							: profileLoginId;
					String profileEmail =
					        profile.getEmail() == null ? "" : profile.getEmail().trim();

					String maskedProfileEmail = profileEmail;
					int emailAtIndex = profileEmail.indexOf("@");

					if (emailAtIndex > 0) {
					    String emailId = profileEmail.substring(0, emailAtIndex);
					    String emailDomain = profileEmail.substring(emailAtIndex);

					    int visibleLength = Math.min(2, emailId.length());

					    maskedProfileEmail =
					            emailId.substring(0, visibleLength)
					            + "********"
					            + emailDomain;
					}
					%>

					<h3 class="member-login-title">로그인 정보</h3>

					<div class="member-login-info">
						<div class="member-login-row">
							<div class="member-login-label">아이디</div>
							<div class="member-login-value">
								<span id="profileLoginIdText"
									data-masked="<%=ProductView.escape(maskedProfileLoginId)%>"
									data-full="<%=ProductView.escape(profileLoginId)%>"> <%=ProductView.escape(maskedProfileLoginId)%>
								</span>
								<button type="button" id="toggleProfileLoginId"
									class="member-login-eye" aria-label="아이디 보기">◎</button>
							</div>
						</div>

						<div class="member-login-row">
							<div class="member-login-label">비밀번호</div>
							<div class="member-login-value">
								<button type="button" class="member-login-password"
									id="openProfilePasswordDialog">비밀번호 변경</button>
							</div>
						</div>

						<div class="member-login-row member-social-row">
							<div class="member-login-label">
								간편 로그인 연결 <span class="member-social-help">ⓘ</span>
							</div>

							<div class="member-login-social">
								<div class="member-linked-social-list">
									<%
									if (kakaoLinked) {
									%>
									<div class="member-kakao-link">
										<span class="member-kakao-icon" aria-hidden="true">●</span> <strong>카카오
											계정 연동</strong>
										<button type="button" data-social-unlink="KAKAO">해제</button>
									</div>
									<%
									}
									%>

									<%
									if (naverLinked) {
									%>
									<div class="member-linked-social">
										<span class="member-linked-icon member-linked-icon-naver"
											aria-hidden="true">N</span> <strong>네이버 계정 연동</strong>
										<button type="button" data-social-unlink="NAVER">해제</button>
									</div>
									<%
									}
									%>

									<%
									if (googleLinked) {
									%>
									<div class="member-linked-social">
										<span class="member-linked-icon member-linked-icon-google"
											aria-hidden="true">G</span> <strong>구글 계정 연동</strong>
										<button type="button" data-social-unlink="GOOGLE">해제</button>
									</div>
									<%
									}
									%>

									<%
									if (!kakaoLinked && !naverLinked && !googleLinked) {
									%>
									<p class="member-social-empty">연동된 계정이 없습니다.</p>
									<%
									}
									%>
								</div>

								<button type="button" id="toggleOtherSocial"
									class="member-other-social" aria-expanded="true">
									다른 SNS로 연동하기 <span aria-hidden="true">⌃</span>
								</button>

								<div id="otherSocialButtons" class="member-social-buttons">
									<%
									if (!kakaoLinked) {
									%>
									<button type="button" class="member-kakao-button"
										id="linkKakaoAccount">● 카카오 계정</button>
									<%
									}
									%>

									<%
									if (!naverLinked) {
									%>
									<button type="button" class="member-naver-button"
										id="linkNaverAccount">N 네이버 계정</button>
									<%
									}
									%>

									<%
									if (!googleLinked) {
									%>
									<button type="button" class="member-google-button"
										id="linkGoogleAccount">G 구글 계정</button>
									<%
									}
									%>
								</div>
							</div>
						</div>
					</div>
					<h3 class="member-login-title">이메일</h3>

					<div class="member-login-info">
						<div class="member-login-row">
							<div class="member-login-label">이메일</div>
							<div class="member-login-value">
								<span id="profileEmailText"
									data-masked="<%=ProductView.escape(maskedProfileEmail)%>"
									data-full="<%=ProductView.escape(profileEmail)%>"><%=ProductView.escape(maskedProfileEmail)%></span>

								<button type="button" id="toggleProfileEmail"
									class="member-login-eye" aria-label="이메일 보기">◎</button>

								<button type="button" id="openProfileEmailEditor"
									class="member-email-change-button">이메일 변경</button>
							</div>
						</div>

						<div id="profileEmailEditor" class="member-email-editor" hidden>
							<div class="member-login-row member-email-edit-row">
								<div class="member-login-label">
									이메일 <em>*</em>
								</div>

								<div class="member-email-edit-content">
									<div class="member-email-inputs">
										<input type="text" id="profileEmailLocal" maxlength="100"
											placeholder="아이디" aria-label="이메일 아이디"> <span
											aria-hidden="true">@</span> <select id="profileEmailDomain"
											aria-label="이메일 도메인">
											<option value="">선택</option>
											<option value="naver.com">naver.com</option>
											<option value="gmail.com">gmail.com</option>
											<option value="daum.net">daum.net</option>
											<option value="hanmail.net">hanmail.net</option>
											<option value="nate.com">nate.com</option>
											<option value="custom">직접 입력</option>
										</select> <input type="text" id="profileEmailCustomDomain"
											maxlength="100" placeholder="도메인 입력"
											aria-label="이메일 도메인 직접 입력" hidden>

										<button type="button" id="checkProfileEmailButton">
											중복확인</button>
									</div>

									<p id="profileEmailMessage" class="member-email-message"
										role="status" aria-live="polite"></p>

									<button type="button" id="saveProfileEmailButton"
										class="member-email-save-button" disabled>이메일 변경 저장</button>
								</div>
							</div>
						</div>
					</div>
					<section class="member-consent-section"
						aria-labelledby="member-consent-title">

						<h3 id="member-consent-title">알림 수신 및 개인정보 동의 설정</h3>
						<form id="memberConsentForm"
							action="<%=ProductView.escape(myContext + "/app/order/profile/consent")%>"
							method="post">
							<input type="hidden" name="csrfToken"
								value="<%=ProductView.escape(memberConsentCsrf)%>">

							<%
							if (profileConsentError != null) {
							%>
							<p class="error-message" role="alert">
								<%=ProductView.escape(profileConsentError)%>
							</p>
							<%
							} else if (profileConsentSettings == null) {
							%>
							<p>저장된 동의 설정이 없습니다.</p>
							<%
							}
							%>

							<div class="member-consent-row">
								<div class="member-consent-label">쇼핑 이벤트/혜택 알림</div>

								<div class="member-consent-content">
									<div class="member-consent-title">
										<strong>광고성 정보 수신 동의 (선택)</strong>
										<button type="button" data-my-unavailable>전문보기</button>
									</div>

									<div class="member-consent-options">
										<label> <input type="checkbox" name="marketingSms"
											value="true"
											<%=profileConsentSettings != null && profileConsentSettings.isMarketingSms() ? "checked" : ""%>>
											<span>SMS로 받기</span>
										</label> <label> <input type="checkbox"
											name="marketingEmail" value="true"
											<%=profileConsentSettings != null && profileConsentSettings.isMarketingEmail() ? "checked" : ""%>>
											<span>이메일로 받기</span>
										</label> <label class="member-consent-muted"> <input
											type="checkbox" disabled> <span>앱 푸시로 받기</span>
										</label>
									</div>

									<ul class="member-consent-notice">
										<li>주문/배송 등 정보성 알림은 광고성 수신 동의 여부와 관계없이 발송됩니다.</li>
										<li>광고성 정보를 수신하실 매체를 한 개 이상 선택 시 광고성 정보 수신에 동의 처리됩니다.</li>
										<li>앱 푸시 알림은 다이소몰 앱에서 설정 가능합니다.</li>
									</ul>
								</div>
							</div>

							<div class="member-consent-row">
								<div class="member-consent-label">선택 동의</div>

								<div class="member-consent-content">
									<label class="member-consent-check"> <input type="checkbox"
										name="marketingPersonalInfo" value="true"
										<%=profileConsentSettings != null && profileConsentSettings.isMarketingPersonalInfo() ? "checked" : ""%>> <span>마케팅 활용 목적의 개인정보 수집 및 이용
											동의 (선택)</span>
										<button type="button" data-my-unavailable>전문보기</button>
									</label> <label class="member-consent-check"> <input
										type="checkbox" name="locationTerms" value="true"
										<%=profileConsentSettings != null && profileConsentSettings.isLocationTerms() ? "checked" : ""%>> <span>위치기반 서비스 이용약관 (선택)</span>
										<button type="button" data-my-unavailable>전문보기</button>
									</label>

									<ul class="member-consent-notice">
										<li>선택 동의 항목은 언제든 동의 내역을 변경하실 수 있습니다. 동의 철회 시 일부 서비스 이용에
											제한이 있을 수 있으나, 다이소몰 이용에 불이익은 없습니다.</li>
									</ul>
								</div>
							</div>

							<div class="member-consent-row member-retention-row">
								<div class="member-consent-label">개인정보 보관기간</div>

								<div class="member-consent-content member-retention-content">
									<div class="member-retention-options">
										<label> <input type="radio" name="profileRetention"
											value="withdrawal"
											<%=profileConsentSettings == null || "WITHDRAWAL".equals(profileConsentSettings.getRetentionPeriod()) ? "checked"
		: ""%>>
											<span>탈퇴 시까지</span>
										</label> <label> <input type="radio" name="profileRetention"
											value="5years"
											<%=profileConsentSettings != null && "5_YEARS".equals(profileConsentSettings.getRetentionPeriod()) ? "checked"
		: ""%>>
											<span>5년</span>
										</label> <label> <input type="radio" name="profileRetention"
											value="3years"
											<%=profileConsentSettings != null && "3_YEARS".equals(profileConsentSettings.getRetentionPeriod()) ? "checked"
		: ""%>>
											<span>3년</span>
										</label>
									</div>

									<p id="memberConsentMessage" role="status" aria-live="polite"></p>
									<button type="submit" class="member-retention-save">
										동의 설정 저장</button>
								</div>
							</div>

							<div class="member-consent-footer">
								<button type="button" data-my-unavailable>회원탈퇴</button>
								<button type="button" data-my-unavailable>개인정보 이용현황</button>
							</div>
							</form>
					</section>
					<dialog id="profilePasswordDialog" class="profile-password-dialog">
					<h2>비밀번호 변경</h2>

					<form id="profilePasswordForm">
						<label> 현재 비밀번호 <input type="password"
							name="currentPassword" autocomplete="current-password" required>
						</label> <label> 새 비밀번호 <input type="password" name="newPassword"
							autocomplete="new-password" minlength="8" maxlength="16" required>
						</label>

						<p class="profile-password-hint">영문, 숫자, 특수문자를 포함해 8~16자로 입력해
							주세요.</p>

						<label> 새 비밀번호 확인 <input type="password"
							name="confirmPassword" autocomplete="new-password" required>
						</label>

						<p id="profilePasswordMessage" class="profile-password-message"
							role="status" aria-live="polite"></p>

						<div class="profile-password-actions">
							<button type="button" id="closeProfilePasswordDialog">취소</button>
							<button type="submit" id="saveProfilePasswordButton">변경하기</button>
						</div>
					</form>
					</dialog>
					<%
					}
					%>


				</section>

				<%
				} else {
				%>
				<nav class="my-home-shortcuts" aria-label="마이페이지 바로가기">
					<a href="<%=myContext%>/app/order/mypage"><span>▤</span>주문내역</a> <a
						href="<%=myContext%>/app/order/mypage?productActivity=wishlist"><span>♡</span>찜</a>
					<a href="<%=myContext%>/app/order/mypage?productActivity=reviews"><span>▧</span>리뷰</a>
					<a href="<%=myContext%>/app/order/mypage?productActivity=recent"><span>◷</span>최근
						본</a>
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
				<%
				// DB에서 조회한 주문 상태
				String myStatus = String.valueOf(myOrder.get("status"));
				String myStatusLabel = String.valueOf(myOrder.get("statusLabel"));

				// 현재 주문 단계. 결제 대기·취소·실패는 진행 단계가 없음
				int myCurrentStep = -1;

				if ("PAID".equals(myStatus)) {
					myCurrentStep = 0;
				} else if ("PREPARING".equals(myStatus)) {
					myCurrentStep = 1;
				} else if ("SHIPPED".equals(myStatus)) {
					myCurrentStep = 3;
				} else if ("DELIVERED".equals(myStatus)) {
					myCurrentStep = 4;
				}
				%>

				<section class="my-status-panel" aria-label="주문 처리 상태">
					<h2><%=ProductView.escape(myStatusLabel)%></h2>

					<%
					if (myCurrentStep >= 0) {
					%>
					<ol class="my-status-steps">
						<li <%=myCurrentStep == 0 ? "aria-current=\"step\"" : ""%>>
							결제완료</li>
						<li <%=myCurrentStep == 1 ? "aria-current=\"step\"" : ""%>>
							상품준비중</li>
						<li <%=myCurrentStep == 2 ? "aria-current=\"step\"" : ""%>>
							배송준비중</li>
						<li <%=myCurrentStep == 3 ? "aria-current=\"step\"" : ""%>>
							배송중</li>
						<li <%=myCurrentStep == 4 ? "aria-current=\"step\"" : ""%>>
							배송완료</li>
					</ol>
					<form id="my-order-tracking-form"
					      method="post"
					      action="<%= myContext %>/app/order/tracking">
					
					    <input type="hidden" name="orderNo"
					           value="<%= ProductView.escape(
					               myOrder.get("orderNo")) %>">
					
					    <input type="hidden" name="csrfToken"
					           value="<%= ProductView.escape(
					               request.getAttribute("myOrderDeleteCsrf")) %>">
					
					    <button class="my-outline-button" type="submit">
					        현재 배송 상태 조회
					    </button>
					</form>
					
					<p id="my-order-tracking-result"
					   role="status"
					   aria-live="polite"></p>
					<%
					}
					%>
					<% if (myOrder.get("returnStatus") != null) { %>
					    <p>
					        반품 처리 상태
					        <strong><%= ProductView.escape(
					            myOrder.get("returnStatusLabel")) %></strong>
					    </p>
					
					    <% if ("REJECTED".equals(myOrder.get("returnStatus"))
					            && myOrder.get("returnAdminNote") != null) { %>
					        <p>반려 사유 <%= ProductView.escape(
					            myOrder.get("returnAdminNote")) %></p>
					    <% } %>
					<% } %>
				</section>
				<%
				for (CartBean item : myItems) {
					String myImage = item.getImageUrl();
					if (myImage == null || myImage.isBlank())
						myImage = "assets/images/mock-product-0.svg";
					if (!myImage.startsWith("https://"))
						myImage = myContext + "/" + myImage;
				%>
				<article class="my-item" id="order-product-<%= ProductView.escape(item.getProductId()) %>">
					<img src="<%=ProductView.escape(myImage)%>" alt="">
					<div class="my-item-info">
						<strong><%=myMoney.format(item.getItemTotal())%>원<small>
								/ <%=item.getQuantity()%>개
						</small></strong><a
							href="<%=myContext%>/product.jsp?id=<%=java.net.URLEncoder.encode(item.getProductId(), "UTF-8")%>"><%=ProductView.escape(item.getProductName())%></a>
					</div>
					<div class="my-item-actions">
						<% Boolean reviewWritten = orderReviewStates.get(item.getProductId());
						   if (reviewWritten != null) { %>
							<a class="my-review-action"
							   href="<%= ProductView.url(myContext, "/app/products/reviews",
							        Map.of("id", item.getProductId(), "from", "order", "orderNo", (String) myOrder.get("orderNo"))) %>"><%= reviewWritten ? "리뷰 수정" : "리뷰 작성" %></a>
						<% } %>
						<button class="my-add-cart" type="button"
							data-my-add="<%=ProductView.escape(item.getProductId())%>">♧
							담기</button>
					</div>
				</article>
				<%
				}
				%>
				<div class="my-order-actions">
					<% if ("PENDING".equals(myOrder.get("status"))) { %>
					<button class="my-outline-button" type="button"
						id="delete-pending-order"
						data-order-no="<%= ProductView.escape(myOrder.get("orderNo")) %>"
						data-csrf="<%= ProductView.escape(request.getAttribute("myOrderDeleteCsrf")) %>">
						결제대기 주문 삭제</button>
					<% } else if ("PAID".equals(myOrder.get("status"))
							&& "APPROVED".equals(myOrder.get("paymentStatus"))) { %>
					<a class="my-outline-button"
						style="display: inline-flex; align-items: center; justify-content: center; text-decoration: none;"
						href="<%= myContext %>/app/order/cancel/form?orderNo=<%= java.net.URLEncoder.encode(
							   (String) myOrder.get("orderNo"), "UTF-8") %>">
						주문취소 </a>
					<% } %>
						<% if ("DELIVERED".equals(myOrder.get("status"))
        					&& "APPROVED".equals(myOrder.get("paymentStatus"))
        					&& myOrder.get("returnStatus") == null) { %>
					
					    <a class="my-outline-button"
					       style="display:inline-flex;align-items:center;justify-content:center;text-decoration:none;"
					       href="<%= myContext %>/app/order/return/form?orderNo=<%= java.net.URLEncoder.encode(
					           (String) myOrder.get("orderNo"), "UTF-8") %>">
					        반품 신청
					    </a>
					
					<% } %>
					
					<button class="my-outline-button" type="button" data-my-unavailable>
						문의하기</button>
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
								<%
								// DB의 결제 상태를 화면에 표시할 문구로 변환
								String myPaymentStatus = String.valueOf(myOrder.get("paymentStatus"));

								String myPaymentLabel;

								switch (myPaymentStatus) {
									case "APPROVED" :
										myPaymentLabel = "결제완료";
										break;

									case "PENDING" :
										myPaymentLabel = "결제대기";
										break;

									case "CANCELLED" :
										myPaymentLabel = "취소완료";
										break;

									case "FAILED" :
										myPaymentLabel = "결제실패";
										break;

									default :
										myPaymentLabel = "결제 상태 확인 필요";
										break;
								}
								%>

								<th scope="row">카카오페이 <span class="my-payment-label">
										<%=ProductView.escape(myPaymentLabel)%>
								</span>
								</th>
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

				<%@ include file="../product/my-activity.jspf"%>
				<%
				}
				%>
			</div>
		</div>

	</main>
	<%@ include file="../common/footer.jspf"%>
	<script>
document.addEventListener('DOMContentLoaded',function(){
 const notice=function(text){window.Daiso.ui.notice(text);};
 document.querySelectorAll('[data-my-unavailable]').forEach(function(button){button.addEventListener('click',function(){notice('해당 기능은 준비 중입니다.');});});
 const notificationNotice = document.getElementById('notificationNotice');
 document.getElementById('openNotificationNotice').addEventListener('click', function () {
     notificationNotice.showModal();
 });
 const chooser=document.getElementById('my-order-switch');
 if(chooser)chooser.addEventListener('change',function(){const url=new URL(location.href);url.searchParams.set('orderNo',chooser.value);location.assign(url.href);});
 const open=document.getElementById('open-my-receipt');
 if(open){const dialog=document.getElementById('my-receipt');open.addEventListener('click',function(){dialog.showModal();});document.getElementById('close-my-receipt').addEventListener('click',function(){dialog.close();});}
 document.querySelectorAll('[data-my-add]').forEach(function(button){button.addEventListener('click',async function(){if(button.disabled)return;button.disabled=true;try{const data=new URLSearchParams();data.set('productId',button.dataset.myAdd);data.set('quantity','1');const response=await fetch('<%=myContext%>/app/cart/add',{method:'POST',body:data});if(!response.ok)throw new Error('장바구니 담기에 실패했습니다.');const result=(await response.text()).trim();notice(result==='OK'?'장바구니에 1개 담았습니다.':result);}catch(error){notice(error.message);}finally{button.disabled=false;}});});
 const verifyDialog = document.getElementById('profileVerifyDialog');
 const openVerifyButton = document.getElementById('openProfileVerification');
 const closeVerifyButton = document.getElementById('closeProfileVerify');
 const verifyButton = document.getElementById('submitProfileVerify');
 const verifyCode = document.getElementById('profileVerifyCode');
 const verifyMessage = document.getElementById('profileVerifyMessage');
 const profileForm = document.getElementById('basicProfileForm');
 const profileCsrf = '<%= ProductView.escape(orderCsrfToken) %>';
 const sendProfileCodeButton = document.getElementById('sendProfileCode');

 if (sendProfileCodeButton) {
     sendProfileCodeButton.addEventListener('click', async function () {
         sendProfileCodeButton.disabled = true;
         verifyMessage.textContent = '인증번호를 요청하는 중입니다.';

         const data = new URLSearchParams();
         data.set('csrfToken', profileCsrf);

         try {
             const response = await fetch(
                 '<%=myContext%>/app/member/profile/send-code',
                 { method: 'POST', body: data }
             );
             if (!response.ok) throw new Error();

             const result = (await response.text()).trim();
             verifyMessage.textContent = result === 'OK'
                     ? '인증번호를 요청했습니다. 받은 문자를 확인해 주세요.'
                     : result;
         } catch (error) {
             verifyMessage.textContent = '문자 요청 중 오류가 발생했습니다.';
         } finally {
             sendProfileCodeButton.disabled = false;
         }
     });
 }

 if (openVerifyButton && verifyDialog) {
   openVerifyButton.addEventListener('click', function () {
     verifyMessage.textContent = '';
     verifyCode.value = '';
     verifyDialog.showModal();
     verifyCode.focus();
   });

   closeVerifyButton.addEventListener('click', function () {
     verifyDialog.close();
   });

   verifyButton.addEventListener('click', async function () {
     const data = new URLSearchParams();
     data.set('code', verifyCode.value.trim());
     data.set('csrfToken', profileCsrf);
     
     try {
       const response = await fetch(
         '<%=myContext%>/app/member/profile/verify-code',
         { method: 'POST', body: data }
       );
       const result = (await response.text()).trim();

       if (result === 'OK') {
    	    location.reload();
    	} else {
    	    verifyMessage.textContent = response.ok
    	            ? result
    	            : '인증 요청 중 오류가 발생했습니다.';
    	}
     } catch (error) {
       verifyMessage.textContent = '인증 요청 중 오류가 발생했습니다.';
     }
   });
 }

 if (profileForm) {
   profileForm.addEventListener('submit', async function (event) {
     event.preventDefault();

     const data = new URLSearchParams(new FormData(profileForm));
     data.set('csrfToken', profileCsrf);
     
     try {
       const response = await fetch(
         '<%=myContext%>/app/member/profile/update',
         { method: 'POST', body: data }
       );
       const result = (await response.text()).trim();

       if (result === 'OK') {
         location.reload();
       } else {
         document.getElementById('profileUpdateMessage').textContent = result;
       }
     } catch (error) {
       document.getElementById('profileUpdateMessage').textContent =
         '회원정보 저장 중 오류가 발생했습니다.';
     }
   });
 }
 const passwordDialog =
	  document.getElementById('profilePasswordDialog');
	const openPasswordButton =
	  document.getElementById('openProfilePasswordDialog');
	const closePasswordButton =
	  document.getElementById('closeProfilePasswordDialog');
	const passwordForm =
	  document.getElementById('profilePasswordForm');
	const passwordMessage =
	  document.getElementById('profilePasswordMessage');
	const savePasswordButton =
	  document.getElementById('saveProfilePasswordButton');

	if (
	  passwordDialog &&
	  openPasswordButton &&
	  closePasswordButton &&
	  passwordForm &&
	  passwordMessage &&
	  savePasswordButton
	) {
	  openPasswordButton.addEventListener('click', function () {
	    passwordForm.reset();
	    passwordMessage.textContent = '';
	    passwordDialog.showModal();
	  });

	  closePasswordButton.addEventListener('click', function () {
	    passwordDialog.close();
	  });

	  passwordForm.addEventListener('submit', async function (event) {
	    event.preventDefault();

	    const data = new URLSearchParams(new FormData(passwordForm));
	    savePasswordButton.disabled = true;
	    passwordMessage.textContent = '비밀번호를 변경하고 있습니다.';

	    try {
	      const response = await fetch(
	        '<%=myContext%>/app/member/profile/password',
	        { method: 'POST', body: data }
	      );

	      const result = (await response.text()).trim();

	      if (result === 'OK') {
	        passwordForm.reset();
	        passwordMessage.textContent = '비밀번호가 변경되었습니다.';
	      } else if (result === 'LOGIN_REQUIRED') {
	        passwordMessage.textContent = '로그인 상태를 확인해 주세요.';
	      } else {
	        passwordMessage.textContent = result;
	      }
	    } catch (error) {
	      passwordMessage.textContent = '비밀번호 변경 중 오류가 발생했습니다.';
	    } finally {
	      savePasswordButton.disabled = false;
	    }
	  });
	}
 const loginIdText = document.getElementById('profileLoginIdText');
 const toggleLoginId = document.getElementById('toggleProfileLoginId');
 const emailText = document.getElementById('profileEmailText');
 const toggleEmail = document.getElementById('toggleProfileEmail');

 if (emailText && toggleEmail) {
   toggleEmail.addEventListener('click', function () {
     const isMasked =
       emailText.textContent.trim() === emailText.dataset.masked;

     emailText.textContent = isMasked
       ? emailText.dataset.full
       : emailText.dataset.masked;

     toggleEmail.setAttribute(
       'aria-label',
       isMasked ? '이메일 숨기기' : '이메일 보기'
     );
   });
 }
 
 const openEmailEditor = document.getElementById('openProfileEmailEditor');
 const emailEditor = document.getElementById('profileEmailEditor');
 const emailLocalInput = document.getElementById('profileEmailLocal');
 const emailDomainSelect = document.getElementById('profileEmailDomain');
 const emailCustomDomain = document.getElementById('profileEmailCustomDomain');
 const checkEmailButton = document.getElementById('checkProfileEmailButton');
 const saveEmailButton = document.getElementById('saveProfileEmailButton');
 const emailMessage = document.getElementById('profileEmailMessage');
 
 if (
		  openEmailEditor &&
		  emailEditor &&
		  emailText &&
		  emailLocalInput &&
		  emailDomainSelect &&
		  emailCustomDomain &&
		  checkEmailButton &&
		  saveEmailButton &&
		  emailMessage
		) {

 let checkedEmail = '';

 function getNewEmail() {
   const local = emailLocalInput.value.trim();
   const domain = emailDomainSelect.value === 'custom'
     ? emailCustomDomain.value.trim()
     : emailDomainSelect.value;

   if (!local || !domain) {
     return '';
   }

   return local + '@' + domain;
 }

 function resetEmailCheck() {
   checkedEmail = '';
   saveEmailButton.disabled = true;
   emailMessage.textContent = '';
 }

 if (openEmailEditor && emailEditor) {
   openEmailEditor.addEventListener('click', function () {
     emailEditor.hidden = false;

     const currentEmail = emailText.dataset.full || '';
     const atIndex = currentEmail.lastIndexOf('@');

     if (atIndex > 0) {
       emailLocalInput.value = currentEmail.substring(0, atIndex);

       const currentDomain = currentEmail.substring(atIndex + 1);
       const domainOption = Array.from(emailDomainSelect.options)
         .find(function (option) {
           return option.value === currentDomain;
         });

       if (domainOption) {
         emailDomainSelect.value = currentDomain;
         emailCustomDomain.hidden = true;
       } else {
         emailDomainSelect.value = 'custom';
         emailCustomDomain.hidden = false;
         emailCustomDomain.value = currentDomain;
       }
     }

     emailLocalInput.focus();
   });
 }

 emailDomainSelect.addEventListener('change', function () {
   const isCustom = emailDomainSelect.value === 'custom';
   emailCustomDomain.hidden = !isCustom;
   resetEmailCheck();

   if (isCustom) {
     emailCustomDomain.focus();
   }
 });

 [emailLocalInput, emailCustomDomain].forEach(function (input) {
   input.addEventListener('input', resetEmailCheck);
 });

 checkEmailButton.addEventListener('click', async function () {
   const email = getNewEmail();

   if (!email) {
     emailMessage.textContent = '이메일을 입력해 주세요.';
     resetEmailCheck();
     return;
   }

   const data = new URLSearchParams();
   data.set('email', email);

   checkEmailButton.disabled = true;
   emailMessage.textContent = '중복 확인 중입니다.';

   try {
     const response = await fetch(
       '<%=myContext%>/app/member/profile/email/check',
       { method: 'POST', body: data }
     );
     const result = (await response.text()).trim();

     if (result === 'available') {
       checkedEmail = email;
       saveEmailButton.disabled = false;
       emailMessage.textContent = '사용할 수 있는 이메일입니다.';
       emailMessage.style.color = '#16834a';
     } else if (result === 'duplicate') {
       resetEmailCheck();
       emailMessage.textContent = '이미 사용 중인 이메일입니다.';
     } else if (result === 'LOGIN_REQUIRED') {
       resetEmailCheck();
       emailMessage.textContent = '로그인 상태를 확인해 주세요.';
     } else {
       resetEmailCheck();
       emailMessage.textContent = '이메일 형식을 확인해 주세요.';
     }
   } catch (error) {
     resetEmailCheck();
     emailMessage.textContent = '중복 확인 중 오류가 발생했습니다.';
   } finally {
     checkEmailButton.disabled = false;
   }
 });

 saveEmailButton.addEventListener('click', async function () {
   const email = getNewEmail();

   if (!email || email !== checkedEmail) {
     resetEmailCheck();
     emailMessage.textContent = '이메일 중복확인을 다시 해주세요.';
     return;
   }

   const data = new URLSearchParams();
   data.set('email', email);

   saveEmailButton.disabled = true;

   try {
     const response = await fetch(
       '<%=myContext%>/app/member/profile/email',
       { method: 'POST', body: data }
     );
     const result = (await response.text()).trim();

     if (result === 'OK') {
       location.reload();
     } else {
       emailMessage.textContent = result;
       saveEmailButton.disabled = false;
     }
   } catch (error) {
     emailMessage.textContent = '이메일 저장 중 오류가 발생했습니다.';
     saveEmailButton.disabled = false;
   }
 });
 }

 if (loginIdText && toggleLoginId) {
   toggleLoginId.addEventListener('click', function () {
     const isMasked =
       loginIdText.textContent.trim() === loginIdText.dataset.masked;

     loginIdText.textContent = isMasked
       ? loginIdText.dataset.full
       : loginIdText.dataset.masked;

     toggleLoginId.setAttribute(
       'aria-label',
       isMasked ? '아이디 숨기기' : '아이디 보기'
     );
   });
 }
 const linkGoogleButton = document.getElementById('linkGoogleAccount');

 if (linkGoogleButton) {
   linkGoogleButton.addEventListener('click', function () {
     location.href =
       '<%=myContext%>/app/member/oauth/google/start?mode=link';
   });
 }
 const linkNaverButton = document.getElementById('linkNaverAccount');

 if (linkNaverButton) {
   linkNaverButton.addEventListener('click', function () {
     location.href =
       '<%=myContext%>/app/member/oauth/naver/start?mode=link';
   });
 }
 const linkKakaoButton = document.getElementById('linkKakaoAccount');

 if (linkKakaoButton) {
   linkKakaoButton.addEventListener('click', function () {
     location.href =
       '<%=myContext%>/app/member/oauth/kakao/start';
   });
 }
 const toggleOtherSocial = document.getElementById('toggleOtherSocial');
 document.querySelectorAll('[data-social-unlink]').forEach(function (button) {
	  button.addEventListener('click', async function () {
	    const provider = button.dataset.socialUnlink;

	    if (!confirm(provider + ' 계정 연동을 해제할까요?')) {
	      return;
	    }

	    const data = new URLSearchParams();
	    data.set('provider', provider);
	    button.disabled = true;

	    try {
	      const response = await fetch(
	        '<%=myContext%>/app/member/profile/social/unlink',
	        { method: 'POST', body: data }
	      );

	      const result = (await response.text()).trim();

	      if (result === 'OK') {
	        alert('SNS 연동이 해제되었습니다.');
	        location.reload();
	      } else {
	        alert(result);
	      }
	    } catch (error) {
	      alert('SNS 연동 해제 중 오류가 발생했습니다.');
	    } finally {
	      button.disabled = false;
	    }
	  });
	});
 const otherSocialButtons = document.getElementById('otherSocialButtons');

 if (toggleOtherSocial && otherSocialButtons) {
   toggleOtherSocial.addEventListener('click', function () {
     const isOpen = toggleOtherSocial.getAttribute('aria-expanded') === 'true';

     toggleOtherSocial.setAttribute('aria-expanded', String(!isOpen));
     otherSocialButtons.hidden = isOpen;
   });
 }
});
const manager = document.getElementById('deliveryPickupDialog');
const openManager = document.getElementById('openDeliveryPickupManager');
const closeManager = document.getElementById('closeDeliveryPickupDialog');
const deliveryPanel = document.getElementById('deliveryManagementPanel');
const pickupPanel = document.getElementById('pickupManagementPanel');
const deliveryForm = document.getElementById('deliveryAddressForm');
const pickupForm = document.getElementById('pickupStoreForm');

if (manager && openManager) {
  openManager.addEventListener('click', function () {
    manager.showModal();
  });

  closeManager.addEventListener('click', function () {
    manager.close();
  });
}

document.querySelectorAll('[data-delivery-tab]').forEach(function (tab) {
	  tab.addEventListener('click', function () {
	    const showDelivery = tab.dataset.deliveryTab === 'delivery';

	    deliveryPanel.hidden = !showDelivery;
	    pickupPanel.hidden = showDelivery;

	    document.querySelectorAll('[data-delivery-tab]').forEach(function (item) {
	      const isActive = item === tab;
	      item.classList.toggle('is-active', isActive);
	      item.setAttribute('aria-selected', String(isActive));
	    });
	  });
	});
	
const manageMode = new URLSearchParams(window.location.search).get('manage');

if (manager && (manageMode === 'delivery' || manageMode === 'pickup')) {
    manager.querySelector('[data-delivery-tab="' + manageMode + '"]').click();
    manager.showModal();
}

const refundDialog = document.getElementById('demoRefundDialog');
const refundForm = document.getElementById('demoRefundForm');

if (refundDialog && refundForm && manageMode === 'refund') {
    refundDialog.showModal();

    const message = document.getElementById('demoRefundMessage');
    const saveButton = refundForm.querySelector('button[value="save"]');
    const controls = refundForm.querySelectorAll('input, select, button');

    refundForm.addEventListener('input', function () {
        saveButton.disabled = true;
        message.textContent = '';
    });
    
    refundForm.addEventListener('reset', function () {
        saveButton.disabled = true;
        message.textContent = '';
    });

    refundForm.addEventListener('submit', async function (event) {
        event.preventDefault();

        const action = event.submitter ? event.submitter.value : 'verify';
        const data = new URLSearchParams(new FormData(refundForm));
        data.set('action', action);

        controls.forEach(function (control) {
            control.disabled = true;
        });

        let verified = false;
        try {
            const response = await fetch(refundForm.action, {
                method: 'POST',
                body: data
            });
            if (!response.ok) {
                throw new Error('환불계좌 요청 실패');
            }

            const result = (await response.text()).trim();
            if (result !== 'OK') {
                message.textContent = result;
                return;
            }

            if (action === 'verify') {
                verified = true;
                message.textContent = '시연용 계좌 확인이 완료되었습니다.';
            } else {
                window.location.reload();
            }
        } catch (error) {
            message.textContent = '처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            controls.forEach(function (control) {
                control.disabled = false;
            });
            saveButton.disabled = !verified;
        }
    });
}

	if (deliveryForm) {
		  const fields = ['addressName', 'recipient', 'phone',
		                  'postcode', 'address1', 'address2'];

		  document.querySelectorAll('[data-add-delivery], [data-edit-delivery]')
		    .forEach(function (button) {
		      button.addEventListener('click', function () {
		        deliveryForm.reset();

		        const editing = button.hasAttribute('data-edit-delivery');
		        deliveryForm.elements.addressAction.value = editing ? 'edit' : 'add';
		        deliveryForm.elements.addressId.value =
		          editing ? button.dataset.addressId : '';

		        fields.forEach(function (field, index) {
		          deliveryForm.elements.namedItem(field).value =
		            editing ? button.getAttribute('data-field-' + index) : '';
		        });

		        document.getElementById('deliveryAddressMessage').textContent = '';
		        deliveryForm.hidden = false;
		        deliveryForm.elements.addressName.focus();
		      });
		    });
		}

if (deliveryForm) {
	  const findAddressButton = document.getElementById('findProfileAddress');

	  findAddressButton.addEventListener('click', function () {
	    const message = document.getElementById('deliveryAddressMessage');
	    const Postcode = window.kakao && window.kakao.Postcode;

	    if (!Postcode) {
	      message.textContent = '주소 검색을 불러오지 못했습니다. 직접 입력해 주세요.';
	      return;
	    }

	    message.textContent = '';

	    new Postcode({
	      oncomplete: function (data) {
	        const address = data.userSelectedType === 'R'
	          ? data.roadAddress
	          : data.jibunAddress;

	        if (deliveryForm.elements.address1.value !== address) {
	          deliveryForm.elements.address2.value = '';
	        }

	        deliveryForm.elements.postcode.value = data.zonecode;
	        deliveryForm.elements.address1.value = address;
	        deliveryForm.elements.address2.focus();
	      }
	    }).open({ q: deliveryForm.elements.postcode.value.trim() });
	  });
  deliveryForm.addEventListener('submit', async function (event) {
    event.preventDefault();

    const data = new URLSearchParams(new FormData(deliveryForm));

    try {
      const response = await fetch(
        '<%=myContext%>/app/order/address',
        { method: 'POST', body: data }
      );
      const result = (await response.text()).trim();

      if (result === 'OK') {
        location.reload();
      } else {
        document.getElementById('deliveryAddressMessage').textContent = result;
      }
    } catch (error) {
      document.getElementById('deliveryAddressMessage').textContent =
        '배송지 저장 중 오류가 발생했습니다.';
    }
  });
}

if (pickupForm) {
  pickupForm.addEventListener('submit', async function (event) {
    event.preventDefault();

    const data = new URLSearchParams(new FormData(pickupForm));

    try {
      const response = await fetch(
        '<%=myContext%>/app/order/pickup-store',
        { method: 'POST', body: data }
      );
      const result = (await response.text()).trim();

      if (result === 'OK') {
        location.reload();
      } else {
        document.getElementById('pickupStoreMessage').textContent = result;
      }
    } catch (error) {
      document.getElementById('pickupStoreMessage').textContent =
        '픽업매장 저장 중 오류가 발생했습니다.';
    }
  });
}

(function () {
    const deleteButton =
        document.getElementById("delete-pending-order");

    if (!deleteButton) return;

    deleteButton.addEventListener("click", async function () {
        if (deleteButton.disabled) return;

        if (!window.confirm(
                "결제 상태를 확인한 뒤 주문내역에서 삭제할까요?")) {
            return;
        }

        deleteButton.disabled = true;

        try {
            const data = new URLSearchParams();

            data.set("orderNo", deleteButton.dataset.orderNo);
            data.set("csrfToken", deleteButton.dataset.csrf);

            const response = await fetch(
                "<%= myContext %>/app/order/pending/delete",
                {
                    method: "POST",
                    body: data
                }
            );

            if (!response.ok) {
                throw new Error("삭제 요청 처리에 실패했습니다.");
            }

            const result = (await response.text()).trim();

            if (result !== "OK") {
                window.alert(result);
                return;
            }

            // 삭제한 주문번호를 주소에서 빼고 목록으로 이동
            window.location.assign(
                "<%= myContext %>/app/order/mypage"
            );

        } catch (error) {
            window.alert(error.message);

        } finally {
            deleteButton.disabled = false;
        }
    });
})();
const memberConsentForm =
    document.getElementById('memberConsentForm');
const memberConsentMessage =
    document.getElementById('memberConsentMessage');
const memberConsentSaveButton =
    memberConsentForm
        ? memberConsentForm.querySelector('.member-retention-save')
        : null;

if (memberConsentForm && memberConsentMessage && memberConsentSaveButton) {
    memberConsentForm.addEventListener('submit', async function (event) {
        event.preventDefault();

        memberConsentSaveButton.disabled = true;
        memberConsentMessage.textContent = '저장 중입니다.';

        const data = new URLSearchParams(new FormData(memberConsentForm));

        try {
            const response = await fetch(memberConsentForm.action, {
                method: 'POST',
                body: data
            });
            const result = (await response.text()).trim();

            if (result === 'OK') {
                memberConsentMessage.textContent = '';

                var toast = document.getElementById('common-feature-status');
                if (toast) {
                    toast.textContent = '동의 설정을 저장했습니다.';
                    toast.classList.add('logout-toast');

                    window.setTimeout(function () {
                        toast.textContent = '';
                        toast.classList.remove('logout-toast');
                    }, 3000);
                }
            } else {
                memberConsentMessage.textContent = result;
            }
        } catch (error) {
            memberConsentMessage.textContent =
                '동의 설정 저장 중 오류가 발생했습니다.';
        } finally {
            memberConsentSaveButton.disabled = false;
        }
    });
}
</script>
<script>
(function () {
    const form =
        document.getElementById("my-order-tracking-form");

    if (!form) return;

    const button =
        form.querySelector('button[type="submit"]');

    const resultBox =
        document.getElementById("my-order-tracking-result");

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        if (button.disabled) return;

        button.disabled = true;
        resultBox.textContent = "배송 상태를 확인하고 있습니다.";

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

            const result = (await response.text()).trim();

            if (result !== "OK") {
                resultBox.textContent = result;
                return;
            }

            // 갱신된 DB 상태로 주문 진행 단계를 다시 표시
            window.location.reload();

        } catch (error) {
            resultBox.textContent = error.message;

        } finally {
            button.disabled = false;
        }
    });
})();
</script>
</body>
</html>
