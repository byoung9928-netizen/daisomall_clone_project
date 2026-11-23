<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReviewPage,java.math.RoundingMode,daiso.product.dao.StoreInventoryDAO.PickupStore" %>
<%
    String context = request.getContextPath();
    ProductDTO product = (ProductDTO) request.getAttribute("product");
    ProductReviewPage detailReviews = (ProductReviewPage) request.getAttribute("detailReviews");
    String detailAverage = detailReviews == null || detailReviews.average == null ? ""
            : detailReviews.average.setScale(1, RoundingMode.HALF_UP).toPlainString();
    if (product == null) response.setStatus(404);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-wishlist.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-wishlist.js" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-reviews.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-detail-description.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-detail.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/product-detail.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <div class="wishlist-tools" data-wishlist-config
         data-base-url="${pageContext.request.contextPath}/app/products/wishlist"
         data-login-url="${pageContext.request.contextPath}/app/member/login"
         data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
         data-logged-in="<%= Boolean.TRUE.equals(request.getAttribute("wishlistLoggedIn")) %>">
        <p class="wishlist-feedback" data-wishlist-feedback role="status" aria-live="polite"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
    </div>
    <main id="main-content" class="container shop-page" data-page="product" tabindex="-1">
        <% if (product == null) { %>
            <h1 class="page-title">상품을 찾을 수 없습니다.</h1>
            <a class="button" href="<%= ProductView.url(context, "/list.jsp", Map.of()) %>">상품 목록으로</a>
        <% } else {
               List<ProductDTO> related = (List<ProductDTO>) request.getAttribute("relatedProducts"); %>
            <%@ include file="breadcrumbs.jspf" %>
            <%
                List<String> gallery = (List<String>) request.getAttribute("productGalleryImages");
                List<PickupStore> pickupStores = (List<PickupStore>) request.getAttribute("pickupStores");
                boolean hasPickup = pickupStores != null && !pickupStores.isEmpty();
                int deliveryLimit = ProductView.available(product) ? Math.min(99, product.getStockQuantity()) : 0;
                boolean wishlistDetailSelected = request.getAttribute("wishlistSelected") instanceof java.util.Set<?>
                        && ((java.util.Set<?>) request.getAttribute("wishlistSelected")).contains(product.getProductId());
            %>
            <div class="product-detail-layout pd-product-detail" data-unit-price="<%= product.getPrice() %>"
                 data-delivery-limit="<%= deliveryLimit %>">
                <section class="pd-gallery" aria-label="상품 이미지" data-gallery>
                    <div class="pd-gallery-stage">
                        <img class="detail-image" data-gallery-main src="<%= ProductView.image(context, gallery.get(0)) %>"
                             alt="<%= ProductView.escape(product.getName()) %>" referrerpolicy="no-referrer">
                        <% if (gallery.size() > 1) { %>
                            <button type="button" class="pd-gallery-back" data-gallery-step="-1" aria-label="이전 이미지">‹</button>
                            <button type="button" class="pd-gallery-next" data-gallery-step="1" aria-label="다음 이미지">›</button>
                        <% } %>
                    </div>
                    <% if (gallery.size() > 1) { %>
                        <div class="pd-gallery-thumbs" aria-label="상품 썸네일">
                            <% for (int index = 0; index < gallery.size(); index++) { %>
                                <button type="button" data-gallery-thumb aria-pressed="<%= index == 0 %>"
                                        aria-label="상품 이미지 <%= index + 1 %> / <%= gallery.size() %>">
                                    <img src="<%= ProductView.image(context, gallery.get(index)) %>" alt=""
                                         loading="lazy" referrerpolicy="no-referrer">
                                </button>
                            <% } %>
                        </div>
                        <p class="pd-sr-only" data-gallery-status role="status"></p>
                    <% } %>
                </section>
                <section class="detail-info" aria-labelledby="pd-product-name">
                    <p class="detail-price" data-product="price"><%= ProductView.money(product.getPrice()) %></p>
                    <h1 id="pd-product-name" class="page-title" data-product="name"><%= ProductView.escape(product.getName()) %></h1>
                    <p data-product="rating"><a class="pd-review-jump" href="#product-reviews"><%= detailAverage.isEmpty() ? "" : "★ " + detailAverage + " · " %>리뷰<%= detailReviews == null ? "" : " " + detailReviews.total + "개" %></a></p>
                    <p class="pd-product-code" data-product="id">품번 <%= ProductView.escape(product.getProductCode()) %></p>
                    <p class="stock-status" data-product="stock">온라인 <%= ProductView.status(product) %> · 재고 <%= product.getStockQuantity() %>개</p>
                    <% if ("DISCONTINUED".equals(product.getStatus())) { %>
                        <p class="source-note">판매 종료된 상품입니다.</p>
                    <% } %>
                    <% if (product.getSourceNote() != null && !product.getSourceNote().isBlank()) { %>
                        <p class="source-note" data-product="source"><%= ProductView.escape(product.getSourceNote()) %></p>
                    <% } %>

                    <section class="pd-fulfillment" aria-labelledby="pd-fulfillment-title">
                        <h2 id="pd-fulfillment-title">배송방법</h2>
                        <div class="pd-methods">
                            <button type="button" data-delivery aria-pressed="true">
                                <strong>택배배송</strong>
                                <span><%= deliveryLimit > 0 ? "온라인 주문 가능" : "온라인 주문 불가" %></span>
                            </button>
                            <% if (hasPickup) { %>
                                <button type="button" data-pickup aria-pressed="false" aria-haspopup="dialog">
                                    <strong>매장픽업</strong>
                                    <span>재고 있는 매장 <%= pickupStores.size() %>곳</span>
                                </button>
                            <% } %>
                        </div>
                        <div class="pd-method-info" data-delivery-info>
                            <p><%= deliveryLimit > 0 ? "배송비 3,000원 · 상품 30,000원 이상 무료" : "현재 택배배송으로 주문할 수 없습니다." %></p>
                        </div>
                        <% if (hasPickup) { %>
                            <div class="pd-method-info" data-pickup-info hidden>
                                <div class="pd-store-selection">
                                    <strong data-store-summary aria-live="polite">픽업할 매장을 선택해 주세요.</strong>
                                    <button type="button" class="pd-quiet-button" data-open-stores aria-haspopup="dialog">매장 선택</button>
                                </div>
                                <p>매장 재고는 조회 시점 기준이며 선택해도 예약되지 않습니다.</p>
                            </div>
                        <% } %>
                        <% if (Boolean.TRUE.equals(request.getAttribute("pickupLookupError"))) { %>
                            <p class="pd-pickup-error" role="status">매장 재고를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.</p>
                        <% } %>
                        <a class="pd-store-link" href="<%= ProductView.url(context, "/app/stores/products", Map.of("product", product.getProductId())) %>">매장 상품 찾기</a>
                    </section>

                    <form id="cartAddForm" method="post" action="${pageContext.request.contextPath}/app/cart/add">
                        <input type="hidden" name="productId" value="<%= ProductView.escape(product.getProductId()) %>">
                        <div class="pd-order-quantity">
                            <label for="pd-quantity">수량</label>
                            <div class="pd-stepper">
                                <button type="button" data-quantity-step="-1" aria-label="수량 줄이기" disabled>−</button>
                                <input id="pd-quantity" type="number" name="quantity" aria-describedby="pd-quantity-hint" min="1" max="<%= Math.max(1, deliveryLimit) %>"
                                       value="1" <%= deliveryLimit > 0 ? "" : "disabled" %>>
                                <button type="button" data-quantity-step="1" aria-label="수량 늘리기" <%= deliveryLimit > 1 ? "" : "disabled" %>>+</button>
                            </div>
                        </div>
                        <p id="pd-quantity-hint" class="pd-quantity-hint" data-quantity-hint><%= deliveryLimit > 0 ? "최대 " + deliveryLimit + "개" : "" %></p>
                        <p class="detail-total" aria-live="polite"><span>상품 합계</span>
                            <strong data-total><%= ProductView.money(deliveryLimit > 0 ? product.getPrice() : 0) %></strong>
                        </p>
                        <div class="pd-order-actions">
                        <button type="button" class="wishlist-button<%= wishlistDetailSelected ? " is-selected" : "" %>"
                                data-wishlist-product="<%= ProductView.escape(product.getProductId()) %>"
                                data-wishlist-name="<%= ProductView.escape(product.getName()) %>"
                                aria-pressed="<%= wishlistDetailSelected %>"
                                aria-label="<%= ProductView.escape(product.getName()) %> <%= request.getAttribute("wishlistError") != null ? "찜 상태 확인 불가" : (wishlistDetailSelected ? "관심상품 해제" : "관심상품 등록") %>"
                                <%= request.getAttribute("wishlistError") != null ? "disabled" : "" %>>
                            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21 3.5 12.5C-2 6.5 6 0 12 6c6-6 14 0.5 8.5 6.5Z"/></svg>
                            <span data-wishlist-label><%= wishlistDetailSelected ? "찜 해제" : "찜" %></span>
                        </button>
                            <button type="submit" class="pd-add-cart" data-add-cart <%= deliveryLimit > 0 ? "" : "disabled" %>>장바구니 담기</button>
                        </div>
                        <p class="pd-pickup-note" data-pickup-note hidden>픽업 장바구니와 주문은 준비 중입니다.</p>
                    </form>
                </section>
                <% if (hasPickup) { %>
                    <dialog class="pd-pickup-dialog" data-pickup-dialog aria-labelledby="pd-pickup-title">
                        <div class="pd-dialog-content">
                            <header class="pd-dialog-header">
                                <h2 id="pd-pickup-title">픽업매장 선택</h2>
                                <button type="button" class="pd-quiet-button" data-close-stores>닫기</button>
                            </header>
                            <label class="pd-store-search" for="pd-store-keyword">매장명·지역 검색
                                <input id="pd-store-keyword" type="search" maxlength="100" placeholder="예: 서울, 해운대" data-store-search autofocus>
                            </label>
                            <div class="pd-store-list" role="group" aria-label="픽업 가능한 매장">
                                <% for (PickupStore store : pickupStores) { %>
                                    <label class="pd-store-option">
                                        <input type="radio" name="pickupStore" value="<%= ProductView.escape(store.location.id) %>"
                                               data-name="<%= ProductView.escape(store.location.name) %>"
                                               data-city="<%= ProductView.escape(store.location.city) %>" data-quantity="<%= store.quantity %>">
                                        <span><strong><%= ProductView.escape(store.location.name) %></strong>
                                            <span><%= ProductView.escape(store.location.city) %></span></span>
                                        <span class="pd-store-stock">재고 <%= store.quantity %>개</span>
                                    </label>
                                <% } %>
                                <p data-no-stores role="status" hidden>검색 조건에 맞는 픽업 매장이 없습니다.</p>
                            </div>
                            <button type="button" class="pd-add-cart" data-confirm-store disabled>선택 완료</button>
                        </div>
                    </dialog>
                <% } %>
            </div>
            <nav class="pd-detail-links" aria-label="상품 상세 바로가기">
                <a href="#product-description">상품 설명</a>
                <a href="#product-reviews">상품 리뷰<%= detailReviews == null ? "" : " " + detailReviews.total + "개" %></a>
            </nav>
            <%@ include file="official-description.jspf" %>
            <% if (!related.isEmpty()) { %>
                <section class="product-section" id="related-products">
                    <h2 class="section-title">함께 둘러볼 상품</h2>
                    <div class="product-grid">
                        <% for (ProductDTO item : related) { %>
                            <%@ include file="card.jspf" %>
                        <% } %>
                    </div>
                </section>
            <% } %>
            <%@ include file="detail-reviews.jspf" %>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>