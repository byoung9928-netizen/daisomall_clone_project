<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dao.StoreInventoryDAO.State" %>
<%
    request.setAttribute("adminSection", "products");
    String blocked = (String) request.getAttribute("adminProductBlocked");
    String csrf = (String) request.getAttribute("adminProductCsrf");
    Map<String, String> productForm = (Map<String, String>) request.getAttribute("productForm");
    Map<String, String> categories = (Map<String, String>) request.getAttribute("productCategories");
    Map<String, String> statuses = (Map<String, String>) request.getAttribute("productStatuses");
    Map<ProductDTO, Map<String, String>> productRows =
            (Map<ProductDTO, Map<String, String>>) request.getAttribute("productRows");
    ProductDTO deletion = (ProductDTO) request.getAttribute("deleteProduct");
    Map<Integer, String> pageLinks = (Map<Integer, String>) request.getAttribute("adminProductPageLinks");
    Integer currentPage = (Integer) request.getAttribute("adminProductPage");
    Map<String, String> filters = (Map<String, String>) request.getAttribute("adminProductFilters");
    Map<String, String> categoryFilters = (Map<String, String>) request.getAttribute("adminCategoryFilters");
    Map<String, String> stockFilters = (Map<String, String>) request.getAttribute("adminStockFilters");
    String activeSort = filters == null ? "" : filters.get("sort");
    boolean priceAscending = "PRICE_ASC".equals(activeSort);
    boolean priceDescending = "PRICE_DESC".equals(activeSort);
    java.util.List<String> statusKeys = java.util.List.of("AVAILABLE", "SOLD_OUT", "SCHEDULED", "DISCONTINUED");
    boolean discontinuationEnabled = Boolean.TRUE.equals(request.getAttribute("productDiscontinuationEnabled"));
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css?v=batch-selection-2">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-workspace.js?v=batch-selection-1" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-batch-selection.js?v=batch-selection-2" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-detail-description.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-products.js?v=batch-selection-1" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-suggestions.js?v=inventory-suggest-1" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-wizard.js" defer></script>
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content" data-admin-page="products" tabindex="-1" data-admin-query="products" data-admin-query-route="${pageContext.request.contextPath}/app/admin/products">
            <h1 class="page-title">상품 관리</h1>
        <% if (daiso.product.controller.ProductController.recommendationDiagnosticsEnabled()) { %>
            <p><a class="button secondary" href="${pageContext.request.contextPath}/app/admin/products/recommendation">시연 회원 추천 분석</a></p>
        <% } %>
            <p class="error-message" role="alert" data-admin-query-error hidden></p>
            <div data-admin-query-content>
            <% if (filters != null && !filters.getOrDefault("filterCategory", "").isEmpty()) { %>
                <p><a href="<%= ProductView.url(request.getContextPath(), "/app/admin/categories",
                        Map.of("edit", filters.get("filterCategory"))) %>#category-detail">선택한 카테고리 관리로 돌아가기</a></p>
            <% } %>
            <% if (!discontinuationEnabled && blocked == null) { %>
                <p class="source-note">판매 종료 전환은 결제 승인 단계의 상품 상태 확인이 준비될 때까지 보류됩니다.</p>
            <% } %>
            <% if (blocked != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(blocked) %></p>
                <% if (response.getStatus() == 403) { %>
                    <a class="button secondary" href="${pageContext.request.contextPath}/app/member/login">로그인 화면</a>
                <% } %>
            <% } else { %>
                <% if (productForm == null && request.getAttribute("adminProductError") != null) { %>
                    <p class="error-message" role="alert"
                       <%= Boolean.TRUE.equals(request.getAttribute("adminBulkError")) ? "data-batch-server-error" : "" %>
                       data-field="<%= ProductView.escape(request.getAttribute("adminBulkErrorField")) %>"><%= ProductView.escape(request.getAttribute("adminProductError")) %></p>
                    <% if (request.getAttribute("adminProductStatusLink") != null) { %>
                        <p><a class="button secondary" href="<%= request.getAttribute("adminProductStatusLink") %>#product-status">이 상품의 판매 상태 변경</a></p>
                    <% } %>
                <% } %>
                <% if (request.getAttribute("adminProductNotice") != null) { %>
                    <p class="form-status" role="status"><%= ProductView.escape(request.getAttribute("adminProductNotice")) %></p>
                <% } %>
                <% if (request.getAttribute("adminBulkNotice") != null) { %>
                    <p class="inventory-success" id="product-bulk-result" role="status" tabindex="-1"><%= ProductView.escape(request.getAttribute("adminBulkNotice")) %></p>
                <% } %>
                <% Map<String, Long> statusSummary = (Map<String, Long>) request.getAttribute("adminProductStatusSummary"); %>
                    <nav class="admin-product-actions admin-product-toolbar-actions" aria-label="상품 관리 작업">
                        <a class="button secondary" href="<%= request.getAttribute("adminProductCsv") %>">검색 결과 CSV</a>
                        <a class="button secondary" href="<%= request.getAttribute("adminProductHistory") %>#product-history">전체 변경 이력</a>
                        <a class="button primary" href="<%= request.getAttribute("adminProductNew") %>#product-editor">상품 등록</a>
                    </nav>
                <nav class="store-metrics" aria-label="검색 범위의 상품 판매 상태">
                    <% for (String metric : java.util.List.of("", "AVAILABLE", "SOLD_OUT", "SCHEDULED", "DISCONTINUED")) {
                           String metricUrl = ProductView.url(request.getContextPath(), "/app/admin/products",
                                   ProductView.with(ProductView.with(filters, "filterStatus", metric), "page", "")); %>
                        <a data-admin-query-link href="<%= metricUrl %>" <%= metric.equals(filters.get("filterStatus")) ? "aria-current=\"page\"" : "" %>>
                            <span><%= metric.isEmpty() ? "전체 상품" : statuses.get(metric) %></span>
                            <strong><%= statusSummary.getOrDefault(metric, 0L) %>종</strong>
                        </a>
                    <% } %>
                </nav>
                <div class="admin-product-toolbar">
                    <form class="admin-product-filters" data-admin-query-form method="get" action="${pageContext.request.contextPath}/app/admin/products">
                        <div class="field admin-product-filter-row admin-product-suggest"
                             data-suggest-filters="filterCategory filterStatus filterStock stockQuantity" data-suggest-stock-label="온라인 재고" data-admin-suggestions="${pageContext.request.contextPath}/app/admin/products/suggestions">
                            <label for="admin-product-query">상품 검색</label>
                            <input id="admin-product-query" name="query" type="search" maxlength="120" autocomplete="off"
                                   placeholder="상품명, 품번, 브랜드 또는 ID" value="<%= ProductView.escape(filters.get("query")) %>">
                            <input type="hidden" name="selectedProductId" value="<%= ProductView.escape(filters.get("selectedProductId")) %>">
                            <div class="admin-product-suggest-panel" data-admin-suggest-panel hidden>
                                <p data-admin-suggest-status role="status" hidden></p>
                                <ul id="admin-product-suggest-list" role="listbox" aria-label="추천 상품"></ul>
                            </div>
                        </div>
                        <label class="field admin-product-filter-row">
                            <span>카테고리</span>
                            <select name="filterCategory">
                                <option value="">전체 카테고리</option>
                                <% for (Map.Entry<String, String> option : categoryFilters.entrySet()) { %>
                                    <option value="<%= ProductView.escape(option.getKey()) %>" <%= option.getKey().equals(filters.get("filterCategory")) ? "selected" : "" %>><%= ProductView.escape(option.getValue()) %></option>
                                <% } %>
                            </select>
                        </label>
                        <label class="field admin-product-filter-row">
                            <span>판매 상태</span>
                            <select name="filterStatus">
                                <option value="">상태 전체</option>
                                <% for (String status : statusKeys) { %>
                                    <option value="<%= status %>" <%= status.equals(filters.get("filterStatus")) ? "selected" : "" %>><%= statuses.get(status) %></option>
                                <% } %>
                            </select>
                        </label>
                        <div class="field admin-product-filter-row">
                            <label for="admin-stock-filter">온라인 재고</label>
                            <div class="admin-product-stock-controls">
                                <select id="admin-stock-filter" name="filterStock">
                                    <% for (Map.Entry<String, String> option : stockFilters.entrySet()) { %>
                                        <option value="<%= option.getKey() %>" <%= option.getKey().equals(filters.get("filterStock")) ? "selected" : "" %>><%= option.getValue() %></option>
                                    <% } %>
                                </select>
                                <label class="admin-product-stock-quantity" data-stock-quantity>
                                    <span class="visually-hidden">기준 수량</span>
                                    <input name="stockQuantity" type="number" inputmode="numeric" min="0" max="2147483647" step="1"
                                           placeholder="수량" value="<%= ProductView.escape(filters.get("stockQuantity")) %>">
                                </label>
                            </div>
                        </div>
                        <label class="field admin-product-filter-row"><span>상품 정렬</span>
                            <select name="sort">
                                <% for (String[] option : new String[][] {
                                    {"NEWEST", "최신 등록순"}, {"UPDATED", "최근 수정순"}, {"NAME", "상품명순"},
                                    {"PRICE_DESC", "가격 높은순"}, {"PRICE_ASC", "가격 낮은순"}, {"STOCK_DESC", "재고 많은순"},
                                    {"STOCK_ASC", "재고 적은순"}, {"SALES", "판매량 높은순"}, {"REVIEWS", "리뷰 많은순"}
                                }) { %>
                                    <option value="<%= option[0] %>" <%= option[0].equals(activeSort) ? "selected" : "" %>><%= option[1] %></option>
                                <% } %>
                            </select>
                        </label>
                        <div class="admin-product-actions admin-product-filter-actions inventory-filter-actions">
                            <button class="button primary" type="submit">검색</button>
                            <a class="button secondary" href="${pageContext.request.contextPath}/app/admin/products" data-admin-query-link>초기화</a>
                        </div>
                    </form>

                </div>

                <%@ include file="product-history.jspf" %>

                <% if (productForm != null) { %>
                    <dialog class="product-editor-dialog" id="product-editor" open aria-labelledby="product-editor-title">
                        <div class="product-editor-heading">
                            <h2 id="product-editor-title" tabindex="-1"><%= productForm.getOrDefault("id", "").isEmpty() ? "상품 등록" : "상품 수정" %></h2>
                            <a class="product-editor-close" href="<%= request.getAttribute("adminProductList") %>" aria-label="상품 입력 창 닫기">×</a>
                        </div>
                        <% if (request.getAttribute("adminProductError") != null) { %>
                            <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("adminProductError")) %></p>
                        <% } %>
                        <form class="admin-editor-form" method="post" enctype="multipart/form-data" action="<%= request.getAttribute("adminProductSave") %>"
                              data-admin-draft data-code-check="${pageContext.request.contextPath}/app/admin/products/check-code">
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                            <input type="hidden" name="id" value="<%= ProductView.escape(productForm.get("id")) %>">
                            <input type="hidden" name="previewConfirmed" value="0">
                            <input type="hidden" name="detailImageOrder">
                            <input type="hidden" name="detailImageBaseline" value="<%= ProductView.escape(request.getAttribute("adminDetailImageBaseline")) %>">
                            <div data-product-input-step>
                                <label class="field">상품명
                                <input name="name" required maxlength="255" value="<%= ProductView.escape(productForm.get("name")) %>">
                                </label>
                                <label class="field">품번
                                <input name="code" required maxlength="100" aria-describedby="product-code-feedback"
                                       value="<%= ProductView.escape(productForm.get("code")) %>">
                                </label>
                                <p class="product-field-feedback" id="product-code-feedback" role="status"></p>
                                <label class="field">브랜드
                                <input name="brand" maxlength="100" value="<%= ProductView.escape(productForm.get("brand")) %>">
                                </label>
                                <label class="field">카테고리 (최하위)
                                <select name="category" required>
                                    <option value="">카테고리를 선택해 주세요</option>
                                    <% for (Map.Entry<String, String> category : categories.entrySet()) { %>
                                        <option value="<%= ProductView.escape(category.getKey()) %>" <%= category.getKey().equals(productForm.get("category")) ? "selected" : "" %>><%= ProductView.escape(category.getValue()) %></option>
                                    <% } %>
                                </select>
                                </label>
                                <label class="field">가격 (원)
                                <input name="price" type="number" min="${adminMinPrice}" max="${adminMaxPrice}" step="1" required
                                       aria-describedby="product-price-feedback" value="<%= ProductView.escape(productForm.get("price")) %>">
                                </label>
                                <p class="product-field-feedback" id="product-price-feedback" role="status">${adminPriceRange}</p>
                                <label class="field">온라인 재고
                                <input id="product-stock" name="stock" type="number" min="0" max="2147483647" required value="<%= ProductView.escape(productForm.get("stock")) %>"
                                       <%= productForm.getOrDefault("id", "").isEmpty() ? "" : "readonly" %>>
                                </label>
                                <% if (!productForm.getOrDefault("id", "").isEmpty()) { %>
                                    <a href="<%= ProductView.url(request.getContextPath(), "/app/admin/inventory",
                                            Map.of("edit", productForm.get("id"))) %>#online-stock-editor">온라인 재고 조정으로 이동</a>
                                <% } %>
                                <label class="field">판매 상태
                                <select id="product-status" name="status" required>
                                    <% for (String status : statusKeys) { %>
                                        <option value="<%= status %>" <%= status.equals(productForm.get("status")) ? "selected" : "" %>
                                                <%= !discontinuationEnabled && "DISCONTINUED".equals(status) && !status.equals(productForm.get("status")) ? "disabled" : "" %>><%= statuses.get(status) %></option>
                                    <% } %>
                                </select>
                                </label>
                                <label class="field">대표 이미지 파일
                                <input name="imageFile" type="file" accept="image/jpeg,image/png" aria-describedby="product-image-feedback">
                                </label>
                                <p id="product-image-feedback" class="product-field-feedback" role="status">JPG·PNG, 최대 5MB, 가로·세로 각각 최대 4,096픽셀. 파일을 선택하면 기존 사진을 교체합니다.</p>
                                <img class="product-image-preview" data-product-image-preview data-image-base="${pageContext.request.contextPath}/"
                                 alt="선택한 상품 대표 이미지 미리보기" hidden>
                                <label class="field">이미지 경로 또는 HTTPS URL (직접 입력)
                                <input name="image" maxlength="1000" placeholder="assets/images/…" value="<%= ProductView.escape(productForm.get("image")) %>">
                                </label>
                                <label class="field">상품 설명
                                <textarea name="description" maxlength="2000" rows="4"><%= ProductView.escape(productForm.get("description")) %></textarea>
                                </label>
                                <%
                                Map<Long, String> savedDetailImages = (Map<Long, String>) request.getAttribute("adminDetailImages");
                                daiso.product.controller.OfficialProductDetails.Entry savedOfficialDetail =
                                        (daiso.product.controller.OfficialProductDetails.Entry) request.getAttribute("adminOfficialDetail");
                                %>
                                <fieldset class="admin-gallery">
                                <legend>상품 상세 이미지</legend>
                                <label class="field">상세 이미지 선택
                                    <input type="file" name="detailImages" accept="image/jpeg,image/png" multiple
                                           aria-describedby="admin-gallery-help admin-gallery-message">
                                </label>
                                <p id="admin-gallery-help">최대 10장 · JPG, PNG · 새로 선택한 대표·상세 이미지 합계 5MB 이하</p>
                                <p id="admin-gallery-message" role="status" data-gallery-message></p>
                                <ol class="admin-gallery-list" data-gallery-list></ol>
                                <template data-gallery-saved>
                                    <% for (Map.Entry<Long, String> image : savedDetailImages.entrySet()) { %>
                                        <img data-image-id="<%= image.getKey() %>"
                                             src="<%= ProductView.escape(request.getContextPath() + "/" + image.getValue()) %>" alt="">
                                    <% } %>
                                </template>
                                <template data-gallery-official data-code="<%= ProductView.escape(request.getAttribute("adminOfficialCode")) %>">
                                    <% if (savedOfficialDetail != null) {
                                        java.util.List<String> images = savedOfficialDetail.getDetailPhotos();
                                        if (images.isEmpty()) images = savedOfficialDetail.getRepresentativePhotos();
                                        for (String url : images) { %>
                                            <img src="<%= ProductView.escape(url) %>" alt="">
                                        <% } %>
                                        <dl class="pd-official__facts">
                                            <div><dt>공식 품번</dt><dd><%= ProductView.escape(savedOfficialDetail.getSku()) %></dd></div>
                                            <% for (Map.Entry<String, String> fact : savedOfficialDetail.getFacts().entrySet()) { %>
                                                <div><dt><%= ProductView.escape(fact.getKey()) %></dt><dd><%= ProductView.escape(fact.getValue()) %></dd></div>
                                            <% } %>
                                        </dl>
                                        <% if (!savedOfficialDetail.getSource().isEmpty()) { %>
                                            <p>공식 상품 정보 확인 (새 창)</p>
                                        <% } %>
                                    <% } %>
                                </template>
                                </fieldset>
                                <div class="product-editor-actions">
                                <a class="button secondary" href="<%= request.getAttribute("adminProductList") %>">취소</a>
                                <button type="button" class="button primary" data-product-preview>미리보기</button>
                                </div>
                            </div>
                            <section class="admin-product-preview" data-product-preview-step hidden aria-labelledby="product-preview-title">
                                <h2 id="product-preview-title" tabindex="-1">고객 상세 미리보기</h2>
                                <div class="product-detail-layout admin-product-preview-layout">
                                    <img class="detail-image" data-preview-image
                                         data-fallback-image="${pageContext.request.contextPath}/assets/images/mock-product-0.svg"
                                         src="${pageContext.request.contextPath}/assets/images/mock-product-0.svg" alt="">
                                    <div class="detail-info">
                                        <p class="eyebrow" data-preview-value="brand"></p>
                                        <h3 data-preview-value="name">상품명</h3>
                                        <p class="detail-price" data-preview-value="price"></p>
                                        <p data-preview-value="code"></p>
                                        <p class="stock-status" data-preview-value="stock"></p>
                                        <button type="button" class="button secondary" disabled>♡ 찜</button>
                                        <div class="delivery-summary"><strong>택배배송</strong><p>배송비 3,000원 · 상품 30,000원 이상 무료</p></div>
                                        <button type="button" class="button secondary" disabled>매장 상품 찾기</button>
                                        <label class="field">수량 <input type="number" value="1" disabled></label>
                                        <p class="detail-total" data-preview-value="total"></p>
                                        <button type="button" class="button primary" disabled>장바구니 담기</button>
                                    </div>
                                </div>
                                <section class="pd-description pd-official">
                                    <h3>상품 설명</h3>
                                    <p class="pd-product-description" data-preview-value="description" hidden></p>
                                    <details class="pd-official__expand" data-preview-gallery hidden>
                                        <summary>
                                            <span class="pd-official__preview"><img data-preview-first alt=""></span>
                                            <span class="pd-official__toggle">
                                                <span class="pd-official__more">상품 설명 더보기</span>
                                                <span class="pd-official__less">상품 설명 접기</span>
                                            </span>
                                        </summary>
                                        <div class="pd-official__content">
                                            <div class="pd-official__photos" data-preview-photos></div>
                                            <div data-preview-facts></div>
                                        </div>
                                    </details>
                                    <p class="pd-official__empty" data-preview-empty>등록된 상세 이미지가 없습니다.</p>
                                </section>
                                <div class="product-editor-actions admin-preview-actions">
                                    <button type="button" class="button secondary" data-product-back>이전</button>
                                    <button type="submit" class="button primary"><%= productForm.getOrDefault("id", "").isEmpty() ? "등록" : "수정 저장" %></button>
                                </div>
                            </section>
                        </form>
                    </dialog>
                <% } %>

                <% if (deletion != null) { %>
                    <section class="admin-panel" id="delete-product">
                        <h2>상품 삭제</h2>
                        <p>‘<%= ProductView.escape(deletion.getName()) %>’ 상품을 삭제할까요?</p>
                        <form method="post" action="<%= request.getAttribute("adminProductDelete") %>">
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                            <input type="hidden" name="id" value="<%= ProductView.escape(deletion.getProductId()) %>">
                            <button class="button primary" type="submit">삭제 확인</button>
                            <a class="button secondary" href="<%= request.getAttribute("adminProductList") %>">취소</a>
                        </form>
                    </section>
                <% } %>

                <% daiso.product.controller.AdminProductController.BulkPreview bulkPreview =
                       (daiso.product.controller.AdminProductController.BulkPreview) request.getAttribute("productBulkPreview");
                   Map<String, String> bulkCategories = (Map<String, String>) request.getAttribute("adminBulkCategories"); %>
                <% if (bulkPreview != null) { %>
                    <section class="admin-panel inventory-batch-preview" data-batch-preview>
                        <header class="inventory-batch-heading">
                            <h1 class="page-title" id="product-bulk-title" tabindex="-1">상품 <%= bulkPreview.fieldLabel %> 일괄 변경</h1>
                            <button class="button secondary" type="button" data-batch-close hidden aria-label="미리보기 닫기">×</button>
                        </header>
                        <div class="batch-preview-summary">
                            <p>선택한 상품 <strong><%= bulkPreview.items.size() %>개</strong> · 변경 항목 <strong><%= bulkPreview.fieldLabel %></strong></p>
                        </div>
                        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품별 변경 전후 비교">
                            <table class="admin-table">
                                <thead><tr><th scope="col">상품명·품번</th><th scope="col">기존 값</th><th scope="col">변경 후 값</th></tr></thead>
                                <tbody>
                                    <% for (daiso.product.controller.AdminProductController.BulkItem item : bulkPreview.items) { %>
                                        <tr>
                                            <td><%= ProductView.escape(item.name) %><small>품번 <%= ProductView.escape(item.code) %></small></td>
                                            <td><%= ProductView.escape(item.previousLabel) %></td>
                                            <td><% if (item.changed) { %><strong class="batch-preview-change"><%= ProductView.escape(bulkPreview.targetLabel) %></strong>
                                                <% } else { %><%= ProductView.escape(bulkPreview.targetLabel) %><small>변경 없음</small><% } %></td>
                                        </tr>
                                    <% } %>
                                </tbody>
                            </table>
                        </div>
                        <p class="error-message" role="alert" data-batch-confirm-error hidden tabindex="-1"></p>
                        <form method="post" action="<%= request.getAttribute("adminProductBulk") %>" class="inventory-batch-confirm" data-batch-confirm>
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                            <input type="hidden" name="bulkReceipt" value="<%= bulkPreview.receipt %>">
                            <a class="button secondary" href="<%= request.getAttribute("adminProductList") %>" data-batch-revise>다시 작성</a>
                            <button class="button primary" type="submit">변경 확정</button>
                        </form>
                    </section>
                <% } %>
                <div class="admin-product-results-bar">
                    <p class="admin-count" role="status"><%= request.getAttribute("adminProductCount") %>개 상품
                        <% if (!productRows.isEmpty()) { %> · <%= request.getAttribute("adminProductFirst") %>–<%= request.getAttribute("adminProductLast") %>번째 표시<% } %></p>
                </div>
                        <form class="admin-panel inventory-batch-panel product-bulk-panel" id="product-bulk" data-batch-selection-name="selectedProduct"
                              data-batch-limit="<%= daiso.product.controller.AdminProductController.BULK_LIMIT %>" method="post" action="<%= request.getAttribute("adminProductBulk") %>">
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                            <header class="inventory-batch-heading">
                                <h3>상품 일괄 변경</h3>
                                <output data-batch-count aria-live="polite">현재 페이지 상품 선택</output>
                                <button class="button secondary" type="button" data-batch-clear hidden>전체 선택 해제</button>
                            </header>
                            <%@ include file="batch-selection.jspf" %>
                            <div class="inventory-batch-fields">
                            <label class="field"><span>일괄 변경 항목</span>
                                <select name="bulkType">
                                    <option value="">변경 항목 선택</option>
                                    <option value="STATUS">판매 상태</option>
                                    <option value="PRICE">가격</option>
                                    <% if (bulkCategories != null) { %><option value="CATEGORY">카테고리</option><% } %>
                                </select>
                            </label>
                            <label class="field admin-product-bulk-status" data-bulk-field="STATUS">
                                <span>변경할 판매 상태</span>
                                <select name="targetStatus">
                                    <option value="">변경할 상태 선택</option>
                                    <% for (String status : statusKeys) { %>
                                        <option value="<%= status %>" <%= !discontinuationEnabled && "DISCONTINUED".equals(status) ? "disabled" : "" %>><%= statuses.get(status) %></option>
                                    <% } %>
                                </select>
                            </label>
                            <label class="field" data-bulk-field="PRICE"><span>변경할 가격 (원)</span>
                                <input name="targetPrice" type="number" min="1000" max="5000" step="1">
                            </label>
                            <% if (bulkCategories != null) { %>
                                <label class="field" data-bulk-field="CATEGORY"><span>새 카테고리</span>
                                    <select name="targetCategory">
                                        <option value="">최하위 카테고리 선택</option>
                                        <% for (Map.Entry<String, String> option : bulkCategories.entrySet()) { %>
                                            <option value="<%= ProductView.escape(option.getKey()) %>"><%= ProductView.escape(option.getValue()) %></option>
                                        <% } %>
                                    </select>
                                </label>
                            <% } %>
                            </div>
                            <p class="error-message" role="alert" data-product-bulk-error hidden tabindex="-1"></p>
                            <button class="button primary" type="submit">미리보기</button>
                        </form>
                        <dialog class="inventory-batch-dialog product-bulk-dialog" id="product-bulk-dialog" aria-labelledby="product-bulk-title"></dialog>
                <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품 관리 표 (가로 스크롤 가능)">
                    <table class="admin-table store-stock-table">
                        <caption class="visually-hidden">상품 관리</caption>
                        <thead><tr>
                            <th scope="col"><span data-bulk-select-heading>선택</span><input type="checkbox" data-bulk-select-all hidden aria-label="현재 페이지 상품 모두 선택"></th>
                            <th scope="col">상품 ID</th><th scope="col">상품명·품번</th><th scope="col">카테고리</th>
                            <th scope="col"<%= priceAscending ? " aria-sort=\"ascending\"" : priceDescending ? " aria-sort=\"descending\"" : "" %>>
                                <a class="admin-price-sort" href="<%= request.getAttribute("adminPriceSortLink") %>" data-admin-query-link
                                   aria-label="가격 <%= priceAscending ? "내림차순" : "오름차순" %>으로 정렬">
                                    가격
                                    <span class="admin-price-sort-arrows" aria-hidden="true">
                                        <span class="<%= priceAscending ? "is-active" : "" %>">▲</span>
                                        <span class="<%= priceDescending ? "is-active" : "" %>">▼</span>
                                    </span>
                                </a>
                            </th>
                            <th scope="col">판매 상태</th><th scope="col">온라인 재고</th><th scope="col">관리</th>
                        </tr></thead>
                        <tbody>
                            <% for (Map.Entry<ProductDTO, Map<String, String>> row : productRows.entrySet()) {
                                   ProductDTO item = row.getKey(); %>
                                <tr>
                                    <td><input type="checkbox" name="selectedProduct" form="product-bulk"
                                               data-name="<%= ProductView.escape(item.getName()) %>" data-code="<%= ProductView.escape(item.getProductCode()) %>"
                                               value="<%= ProductView.escape(item.getProductId()) %>"
                                               aria-label="<%= ProductView.escape(item.getName()) %> 선택"></td>
                                    <td><%= ProductView.escape(item.getProductId()) %></td>
                                    <td><div class="store-stock-product">
                                        <% String detailUrl = ProductView.url(request.getContextPath(), "/product.jsp", Map.of("id", item.getProductId())); %>
                                        <a href="<%= detailUrl %>" tabindex="-1" aria-hidden="true"><img src="<%= ProductView.image(request.getContextPath(), item) %>" width="44" height="48" decoding="async" loading="lazy" alt=""></a>
                                        <div><a href="<%= detailUrl %>" class="admin-workspace-ellipsis"><strong><%= ProductView.escape(item.getName()) %></strong></a><small>품번 <%= ProductView.escape(item.getProductCode()) %></small></div>
                                    </div></td>
                                    <td><%= ProductView.escape(categoryFilters.getOrDefault(item.getCategoryId(), item.getCategoryId())) %></td>
                                    <td><%= ProductView.money(item.getPrice()) %></td>
                                    <td><%= ProductView.escape(statuses.getOrDefault(item.getStatus(), item.getStatus())) %></td>
                                    <% State quantityState = State.ofQuantity(item.getStockQuantity()); %>
                                    <td><span class="store-stock-badge" data-stock-state="<%= quantityState.name() %>"><%= quantityState.label %></span>
                                        <strong class="inventory-stock-number"><%= item.getStockQuantity() %>개</strong></td>
                                    <td><div class="store-stock-actions">
                                        <a class="button secondary" href="<%= row.getValue().get("edit") %>#product-editor" aria-label="<%= ProductView.escape(item.getName()) %> 수정">수정</a>
                                        <a class="button secondary inventory-danger-outline" href="<%= row.getValue().get("delete") %>#delete-product"
                                           data-delete-id="<%= ProductView.escape(item.getProductId()) %>"
                                           data-delete-name="<%= ProductView.escape(item.getName()) %>"
                                           aria-label="<%= ProductView.escape(item.getName()) %> 삭제">삭제</a>
                                        <a class="text-button" href="<%= row.getValue().get("history") %>#product-history"
                                           aria-label="<%= ProductView.escape(item.getName()) %> 변경 이력">이력</a>
                                    </div></td>
                                </tr>
                            <% } %>
                            <% if (productRows.isEmpty()) { %><tr><td colspan="8">조건에 맞는 상품이 없습니다.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <nav class="admin-product-pagination" aria-label="상품 관리 페이지">
                    <% if (request.getAttribute("adminProductPrev") != null) { %>
                        <a class="button secondary" rel="prev" href="<%= request.getAttribute("adminProductPrev") %>">이전 페이지</a>
                    <% } %>
                    <div class="admin-product-page-numbers">
                        <% for (Map.Entry<Integer, String> link : pageLinks.entrySet()) { %>
                            <% if (link.getKey().equals(currentPage)) { %>
                                <span class="admin-product-page-number" aria-current="page"><%= link.getKey() %></span>
                            <% } else { %>
                                <a class="admin-product-page-number" href="<%= link.getValue() %>"
                                   aria-label="<%= link.getKey() %>페이지"><%= link.getKey() %></a>
                            <% } %>
                        <% } %>
                    </div>
                    <% if (request.getAttribute("adminProductNext") != null) { %>
                        <a class="button secondary" rel="next" href="<%= request.getAttribute("adminProductNext") %>">다음 페이지</a>
                    <% } %>
                </nav>
                <dialog class="product-delete-dialog" id="product-delete-dialog" aria-labelledby="product-delete-title"
                        aria-describedby="product-delete-message">
                    <h2 id="product-delete-title">상품을 삭제할까요?</h2>
                    <p id="product-delete-message"><strong data-delete-product-name></strong> 상품을 삭제하면 되돌릴 수 없습니다.</p>
                    <form method="post" action="<%= request.getAttribute("adminProductDelete") %>">
                        <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                        <input type="hidden" name="id">
                        <button class="button secondary" type="button" data-delete-cancel autofocus>취소</button>
                        <button class="button primary" type="submit">삭제하기</button>
                    </form>
                </dialog>
            <% } %>
            </div>
        </main>
    </div>
</body>
</html>
