<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,java.text.NumberFormat,java.util.Locale,daiso.product.controller.ProductView" %>
<%@ page import="daiso.product.controller.StoreManagerAssortmentController,daiso.product.controller.StoreManagerInventoryController" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.AssortmentItem,daiso.product.dao.StoreInventoryDAO.Stock" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.ManagerAssortment,daiso.product.dao.StoreInventoryDAO.ManagerSort" %>
<%
    StoreManagerAssortmentController.Page model =
            (StoreManagerAssortmentController.Page) request.getAttribute("assortment");
    String path = request.getContextPath();
    String actionUrl = ProductView.escape(path + StoreManagerAssortmentController.ROUTE);
    NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.KOREA);
    ManagerAssortment[] cards = {ManagerAssortment.ALL, ManagerAssortment.ACTIVE, ManagerAssortment.NEW, ManagerAssortment.ENDED};
    String[] cardIcons = {"▦", "✓", "+", "×"};
    String[] tones = {"ALL", "NORMAL", "LOW", "SOLD_OUT"};
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/store-manager-inventory.css?v=polish-1">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/store-manager-assortment.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-workspace.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-suggestions.js?v=assortment-1" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/store-manager-inventory.js?v=assortment-1" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/store-manager-assortment.js" defer></script>
</head>
<body class="admin-body store-manager-body">
    <%@ include file="layout.jspf" %>
    <main class="admin-content store-manager-inventory store-manager-assortment" id="store-manager-content" tabindex="-1"
          data-admin-query="store-manager-assortment" data-admin-query-route="<%= actionUrl %>"
          data-batch-limit="<%= StoreManagerAssortmentController.LIMIT %>"
          data-loading-label="취급 상품을 조회하는 중입니다."
          data-placeholder="<%= ProductView.image(path, (String) null) %>">
        <h1 class="page-title">취급 상품 관리</h1>
        <p class="inventory-scope">배정된 매장의 상품을 등록·종료하거나 다시 취급합니다.</p>
        <p class="error-message" role="alert" data-admin-query-error hidden></p>
        <p class="store-ui-loading" role="status" data-store-query-status></p>
        <div data-admin-query-content>
            <input type="hidden" data-store-view-id value="<%= ProductView.escape(request.getAttribute("storeManagerId")) %>">
            <% if (model.notice != null) { %>
                <p class="success-message" role="status" data-store-inventory-result><%= ProductView.escape(model.notice) %></p>
            <% } %>
            <section class="admin-stats store-inventory-cards" aria-label="취급 현황">
                <% for (int i = 0; i < cards.length; i++) { ManagerAssortment card = cards[i]; %>
                    <a class="admin-stat" data-stock-kind="<%= tones[i] %>" data-admin-query-link href="<%= model.filter(card) %>"
                       <%= card == model.state ? "aria-current=\"true\"" : "" %>>
                        <span class="store-card-label"><span class="store-card-icon" aria-hidden="true"><%= cardIcons[i] %></span><%= card.label %></span>
                        <span class="store-card-value"><strong><%= numbers.format(model.totals.get(card)) %></strong><span>종</span></span>
                    </a>
                <% } %>
            </section>
            <p class="inventory-scope">현황은 검색어와 카테고리를 반영합니다. 카드 선택으로 취급 상태를 좁힐 수 있습니다.</p>
            <% if (model.recovered > 0) { %>
                <p class="store-assortment-warning">
                    취급 종료 상태에서 재고가 복구된 상품이 <strong><%= numbers.format(model.recovered) %>종</strong> 있습니다.
                    <a href="<%= model.filter(ManagerAssortment.RECOVERED) %>" data-admin-query-link>복구 재고 보기</a>
                </p>
            <% } %>
            <% if (!model.safePickup) { %>
                <p class="inventory-scope">현재 환경에서는 픽업 재고 보호가 활성화되기 전까지 취급 종료를 저장할 수 없습니다.</p>
            <% } %>
            <form class="admin-search store-inventory-filters" data-admin-query-form data-admin-explicit-submit method="get" action="<%= actionUrl %>">
                <div class="field store-search-main admin-product-suggest" data-admin-suggestions="<%= actionUrl %>/suggestions"
                     data-suggest-filters="category state" data-suggest-stock-label="매장 재고">
                    <label for="store-assortment-query">상품명·품번·브랜드·상품 ID</label>
                    <input id="store-assortment-query" type="search" name="q" maxlength="120" autocomplete="off"
                           value="<%= ProductView.escape(model.params.get("q")) %>">
                    <input type="hidden" name="selectedProductId" value="<%= ProductView.escape(model.params.get("selectedProductId")) %>">
                    <div class="admin-product-suggest-panel" data-admin-suggest-panel hidden>
                        <p data-admin-suggest-status role="status" hidden></p>
                        <ul id="store-assortment-suggestions" role="listbox" aria-label="추천 상품"></ul>
                    </div>
                </div>
                <label class="field">카테고리
                    <select name="category">
                        <option value="">전체 카테고리</option>
                        <% for (Map.Entry<String, String> category : model.categories.entrySet()) { %>
                            <option value="<%= ProductView.escape(category.getKey()) %>"
                              <%= category.getKey().equals(model.params.get("category")) ? "selected" : "" %>><%= ProductView.escape(category.getValue()) %></option>
                        <% } %>
                    </select>
                </label>
                <label class="field">취급 상태
                    <select name="state">
                        <% for (ManagerAssortment state : ManagerAssortment.values()) { %>
                            <option value="<%= state.name() %>" <%= state == model.state ? "selected" : "" %>><%= state.label %></option>
                        <% } %>
                    </select>
                </label>
                <label class="field">정렬
                    <select name="sort">
                        <% for (ManagerSort sort : ManagerSort.values()) { %>
                            <option value="<%= sort.name() %>" <%= sort == model.sort ? "selected" : "" %>><%= sort.label %> <%= sort.descending ? "내림차순" : "오름차순" %></option>
                        <% } %>
                    </select>
                </label>
                <label class="field">페이지당
                    <select name="size">
                        <% for (int size : new int[]{20, 50, 100}) { %>
                            <option value="<%= size %>" <%= Integer.toString(size).equals(model.params.get("size")) ? "selected" : "" %>><%= size %>개</option>
                        <% } %>
                    </select>
                </label>
                <div class="store-inventory-actions store-search-actions">
                    <button class="button primary" type="submit">검색</button>
                    <a class="button secondary" href="<%= actionUrl %>" data-admin-query-link>초기화</a>
                </div>
            </form>

            <div class="store-inventory-toolbar">
                <p class="admin-count">검색 결과 <strong><%= numbers.format(model.total) %>종</strong>
                    <% if (model.total > 0) { %> · <%= numbers.format(model.first) %>–<%= numbers.format(model.last) %>번째<% } %>
                </p>
                <div class="store-inventory-actions">
                    <span data-store-selection-count role="status">선택 0개</span>
                    <button type="button" class="button secondary" data-store-selection-clear hidden>선택 해제</button>
                    <button type="button" class="button secondary" data-assortment-bulk="REGISTER" disabled>일괄 등록</button>
                    <button type="button" class="button secondary" data-assortment-bulk="UNLIST" disabled>일괄 종료</button>
                    <a class="button secondary" href="<%= model.csv() %>">검색 결과 CSV</a>
                </div>
            </div>
            <p class="inventory-scope">같은 작업을 할 상품끼리 선택해 주세요. 한 번에 최대 <%= StoreManagerAssortmentController.LIMIT %>종을 처리할 수 있습니다.</p>
            <div class="store-inventory-table" role="region" tabindex="0" aria-label="취급 상품 목록">
                <table class="admin-table">
                    <colgroup>
                        <col class="store-col-select"><col class="store-col-image"><col>
                        <col class="store-col-code"><col class="store-col-category">
                        <col class="store-col-number" span="3"><col class="store-col-state">
                        <col class="store-col-time"><col class="store-assortment-actions-col">
                    </colgroup>
                    <thead><tr>
                        <th class="admin-batch-choice" scope="col"><input type="checkbox" data-store-select-page aria-label="현재 페이지 상품 선택"></th>
                        <th scope="col">이미지</th><th scope="col">상품</th><th scope="col">품번</th>
                        <th scope="col">카테고리</th><th scope="col">판매 가격</th><th scope="col">매장 재고</th>
                        <th scope="col">부족 기준</th><th scope="col">취급 상태</th><th scope="col">최근 취급 변경</th>
                        <th scope="col">관리</th>
                    </tr></thead>
                    <tbody>
                    <% if (model.rows.isEmpty()) { %>
                        <tr><td colspan="11" class="store-empty-row">조건에 맞는 상품이 없습니다. 검색 조건을 바꾸거나 <a data-admin-query-link href="<%= actionUrl %>">초기화</a>해 주세요.</td></tr>
                    <% }
                    for (AssortmentItem item : model.rows) {
                        Stock stock = item.stock;
                        ManagerAssortment state = item.state();
                        String operation = state == ManagerAssortment.NEW ? "REGISTER" : state == ManagerAssortment.ACTIVE ? "UNLIST" : "RESUME";
                        boolean editable = state == ManagerAssortment.ACTIVE || item.eligible;
                        String actionName = state == ManagerAssortment.NEW ? "취급 등록" : state == ManagerAssortment.ACTIVE ? "취급 종료" : "재취급";
                        String tone = state == ManagerAssortment.ACTIVE ? "NORMAL" : state == ManagerAssortment.NEW ? "ALL" : "LOW";
                    %>
                        <tr data-store-stock data-operation="<%= operation %>" data-product-id="<%= ProductView.escape(stock.id) %>"
                            data-version="<%= stock.version %>" data-name="<%= ProductView.escape(stock.name) %>"
                            data-code="<%= ProductView.escape(stock.code) %>" data-image="<%= ProductView.image(path, stock.image) %>"
                            data-sale="<%= ProductView.escape(item.saleStatus) %>"
                            data-quantity="<%= stock.quantity %>" data-threshold="<%= stock.threshold %>">
                            <td class="admin-batch-choice"><% if (editable && state != ManagerAssortment.ENDED) { %>
                                <input type="checkbox" data-store-select aria-label="<%= ProductView.escape(stock.name) %> 선택">
                            <% } %></td>
                            <td><img class="store-inventory-thumb" src="<%= ProductView.image(path, stock.image) %>" alt="" width="44" height="44" loading="lazy"></td>
                            <td class="store-item-name"><strong title="<%= ProductView.escape(stock.name) %>"><%= ProductView.escape(stock.name) %></strong>
                                <small>ID <%= ProductView.escape(stock.id) %><% if (item.brand != null && !item.brand.isBlank()) { %> · <%= ProductView.escape(item.brand) %><% } %></small>
                            </td>
                            <td><%= ProductView.escape(stock.code) %></td>
                            <td><%= ProductView.escape(model.categories.get(item.categoryId)) %></td>
                            <td class="inventory-number"><%= numbers.format(item.price) %>원</td>
                            <td class="inventory-number"><%= state == ManagerAssortment.NEW ? "해당 없음" : numbers.format(stock.quantity) + "개" %></td>
                            <td class="inventory-number"><%= state == ManagerAssortment.NEW ? "해당 없음" : numbers.format(stock.threshold) + "개" %></td>
                            <td><span class="store-inventory-state" data-stock-kind="<%= tone %>"><%= state.label %></span>
                                <% if (state == ManagerAssortment.ENDED && stock.quantity > 0) { %><small class="store-recovered">복구 재고</small><% } %>
                            </td>
                            <td><%= item.lastChange == null ? "기록 없음" : StoreManagerInventoryController.recordedTime(item.lastChange) %></td>
                            <td class="store-stock-management"><div class="store-stock-controls">
                                <% if (editable) { %>
                                    <button type="button" class="button <%= state == ManagerAssortment.ACTIVE ? "secondary" : "primary" %>" data-assortment-one><%= actionName %></button>
                                <% } else if (state == ManagerAssortment.ENDED) { %><small>재취급 불가</small><% } %>
                                <% if (item.eligible) { %><a class="button secondary" href="<%= ProductView.url(path, "/app/products/detail", Map.of("id", stock.id)) %>">상품 상세</a><% } %>
                                <% if (state == ManagerAssortment.ACTIVE) { %><a class="button secondary" href="<%= ProductView.url(path, StoreManagerInventoryController.ROUTE, Map.of("selectedProductId", stock.id)) %>">재고 관리</a><% } %>
                                <% if (state != ManagerAssortment.NEW) { %><a class="button secondary" href="<%= ProductView.url(path, "/app/store-manager/history", Map.of("product", stock.id)) %>">변경 이력</a><% } %>
                            </div></td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
            <% daiso.product.controller.InventoryPagination inventoryPagination = model.pagination;
               request.setAttribute("adminPaginationBoundaryControls", Boolean.TRUE); %>
            <%@ include file="../admin/inventory-pagination.jspf" %>

            <dialog class="store-inventory-dialog" id="store-assortment-edit" data-store-batch aria-labelledby="store-assortment-title">
                <form method="post" class="store-dialog-form" data-assortment-form data-preview="<%= actionUrl %>/preview"
                      data-save="<%= actionUrl %>/save" data-thresholds="<%= model.thresholds %>">
                    <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("storeManagerCsrf")) %>">
                    <input type="hidden" name="operation">
                    <% for (Map.Entry<String, String> entry : model.params.entrySet()) { %>
                        <input type="hidden" name="<%= entry.getKey() %>" value="<%= ProductView.escape(entry.getValue()) %>">
                    <% } %>
                    <header class="store-inventory-dialog-header">
                        <h2 id="store-assortment-title" data-assortment-title></h2>
                        <button type="button" class="button secondary" data-store-dialog-close aria-label="닫기">닫기</button>
                    </header>
                    <div class="store-dialog-content">
                        <p class="inventory-scope" data-assortment-intro></p>
                        <div class="store-dialog-items" data-assortment-items></div>
                        <label class="field">변경 사유
                            <input type="text" name="reason" required maxlength="255">
                        </label>
                        <p class="error-message" role="alert" data-assortment-error hidden></p>
                    </div>
                    <footer class="store-dialog-footer">
                        <button class="button secondary" type="button" data-store-dialog-close>취소</button>
                        <button class="button primary" type="submit">미리보기</button>
                    </footer>
                </form>
            </dialog>
        </div>
    </main>
    </div>
</body>
</html>
