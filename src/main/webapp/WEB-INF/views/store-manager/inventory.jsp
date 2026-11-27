<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.StoreManagerInventoryController" %>
<%@ page import="daiso.product.controller.StoreManagerInventoryController.Page" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.Stock,daiso.product.dao.StoreInventoryDAO.InventorySnapshot" %>
<%@ page import="daiso.product.dao.StoreInventoryDAO.State,daiso.product.dao.StoreInventoryDAO.ManagerSort" %>
<%
    Page model = (Page) request.getAttribute("storeInventoryPage");
    String path = request.getContextPath();
    String inventoryUrl = daiso.product.controller.ProductView.escape(path + StoreManagerInventoryController.ROUTE);
    State[] cardStates = {State.ALL, State.NORMAL, State.LOW, State.SOLD_OUT};
    String[] cardLabels = {"취급 상품", "정상 재고", "재고 부족", "품절 상품"};
    java.text.NumberFormat numbers = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
    boolean filtered = model.state != State.ALL || java.util.stream.Stream.of("q", "selectedProductId", "category", "minimum", "maximum")
            .anyMatch(key -> !model.params.getOrDefault(key, "").isEmpty());
    String[] cardIcons = {"▦", "✓", "!", "×"};
    long[] cardTotals = {model.summary.kinds, model.summary.kinds - model.summary.low - model.summary.soldOut,
            model.summary.low, model.summary.soldOut};
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/store-manager-inventory.css?v=polish-1">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-workspace.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-suggestions.js" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/common/store-manager-inventory.js?v=polish-1" defer></script>
</head>
<body class="admin-body store-manager-body">
    <%@ include file="layout.jspf" %>
    <main class="admin-content store-manager-inventory" id="store-manager-content" tabindex="-1"
          data-placeholder="<%= ProductView.image(path, (String) null) %>"
          data-admin-query="store-manager-inventory" data-admin-query-route="<%= inventoryUrl %>">
        <h1 class="page-title">재고 관리</h1>
        <p class="error-message" role="alert" data-admin-query-error hidden></p>
        <p class="store-ui-loading" role="status" data-store-query-status></p>
        <div data-admin-query-content>
            <input type="hidden" data-store-view-id value="<%= ProductView.escape(request.getAttribute("storeManagerId")) %>">
            <% if (model.notice != null) { %>
                <p class="<%= model.notice.error ? "error-message" : "success-message" %>" role="<%= model.notice.error ? "alert" : "status" %>"
                   data-store-inventory-result><%= ProductView.escape(model.notice.message) %></p>
            <% } %>
            <% if (model.recoveredUncarried > 0) { %>
                <p class="store-ui-warning" role="status">취급 종료 상품 <%= model.recoveredUncarried %>종에 남아 있는 재고가 있습니다. 재취급 또는 재고 정리가 필요합니다.</p>
            <% } %>
            <section aria-label="소속 매장 전체 재고 지표">
                <p class="inventory-scope">소속 매장의 전체 취급 상품 기준 · 검색 조건과 무관</p>
                <div class="admin-stats store-inventory-cards">
                    <% for (int i = 0; i < cardStates.length; i++) { %>
                        <a class="admin-stat" href="<%= model.filter("state", cardStates[i].name()) %>"
                           data-stock-state data-stock-kind="<%= cardStates[i].name() %>"
                           <%= model.state == cardStates[i] ? "aria-current=\"true\"" : "" %>>
                            <span class="store-card-label"><span class="store-card-icon" aria-hidden="true"><%= cardIcons[i] %></span><%= cardLabels[i] %></span>
                            <span class="store-card-value"><strong><%= numbers.format(cardTotals[i]) %></strong><span>종</span></span>
                        </a>
                    <% } %>
                </div>
            </section>

            <form class="admin-search store-inventory-filters" data-admin-query-form data-admin-explicit-submit
                  method="get" action="<%= inventoryUrl %>">
                <div class="field store-search-main admin-product-suggest" data-admin-suggestions="<%= inventoryUrl %>/suggestions"
                     data-suggest-filters="category state minimum maximum" data-suggest-stock-label="매장 재고">
                    <label for="store-inventory-query">상품명·품번·브랜드·상품 ID</label>
                    <input id="store-inventory-query" type="search" name="q" maxlength="120" autocomplete="off"
                           value="<%= ProductView.escape(model.params.get("q")) %>">
                    <input type="hidden" name="selectedProductId" value="<%= ProductView.escape(model.params.get("selectedProductId")) %>">
                    <div class="admin-product-suggest-panel" data-admin-suggest-panel hidden>
                        <p data-admin-suggest-status role="status" hidden></p>
                        <ul id="store-inventory-suggestions" role="listbox" aria-label="추천 상품"></ul>
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
                <label class="field">재고 상태
                    <select name="state">
                        <% for (State state : State.values()) { %>
                            <option value="<%= state.name() %>" <%= state == model.state ? "selected" : "" %>><%= state.label %></option>
                        <% } %>
                    </select>
                </label>
                <fieldset class="store-quantity-range">
                    <legend>재고 수량 범위</legend>
                    <label><span class="visually-hidden">최소 수량</span>
                        <input type="number" name="minimum" min="0" max="2147483647" placeholder="최소" value="<%= model.params.get("minimum") %>">
                    </label>
                    <span aria-hidden="true">–</span>
                    <label><span class="visually-hidden">최대 수량</span>
                        <input type="number" name="maximum" min="0" max="2147483647" placeholder="최대" value="<%= model.params.get("maximum") %>">
                    </label>
                </fieldset>
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
                    <a class="button secondary" href="<%= inventoryUrl %>" data-admin-query-link>초기화</a>
                </div>
            </form>

            <div class="store-inventory-toolbar">
                <p class="admin-count">검색 결과 <strong><%= numbers.format(model.total) %>종</strong>
                    <% if (model.total > 0) { %> · <%= numbers.format(model.first) %>–<%= numbers.format(model.last) %>번째<% } %>
                    <% if (filtered) { %><span class="store-active-filter">필터 적용 중</span><% } %>
                </p>
                <div class="store-inventory-actions">
                    <% if (!model.token.isEmpty()) { %>
                        <button type="button" class="button secondary" data-threshold-bulk disabled>선택 부족 기준 변경</button>
                        <button type="button" class="button secondary" data-receive-bulk disabled><%= StoreManagerInventoryController.quantityWritesEnabled() ? "일괄 입고" : "일괄 입고 미리보기" %></button>
                        <span data-store-selection-count role="status">선택 0개</span>
                        <button type="button" class="button secondary" data-store-selection-clear hidden>선택 해제</button>
                    <% } %>
                    <a class="button secondary" href="<%= model.csv() %>">검색 결과 CSV</a>
                </div>
            </div>
            <p class="inventory-scope">최근 기록된 수량 변경은 재고 이력에 남은 시각입니다. 픽업 차감·복구는 누락될 수 있습니다.</p>
            <% if (model.token.isEmpty()) { %><p class="inventory-scope">현재 DB에서 부족 기준 설정을 사용할 수 없습니다.</p><% } %>
            <div class="admin-table-scroll store-inventory-table" tabindex="0" role="region" aria-label="매장 재고 목록">
                <table class="admin-table">
                    <colgroup><col class="store-col-select"><col class="store-col-image"><col><col class="store-col-code">
                        <col class="store-col-category"><col class="store-col-number"><col class="store-col-number">
                        <col class="store-col-state"><col class="store-col-time"><col class="store-col-actions"></colgroup>
                    <thead><tr>
                        <th scope="col" class="admin-batch-choice">
                            <% if (!model.token.isEmpty() && !model.rows.isEmpty()) { %>
                                <input type="checkbox" data-store-select-page aria-label="현재 페이지 상품 선택">
                            <% } else { %>선택<% } %>
                        </th>
                        <th scope="col">상품 이미지</th>
                        <% ManagerSort[] sortable = {ManagerSort.NAME, ManagerSort.CODE, ManagerSort.QUANTITY, ManagerSort.THRESHOLD};
                           for (int i = 0; i < sortable.length; i++) {
                               ManagerSort sort = sortable[i];
                               ManagerSort active = model.sort == sort || model.sort == sort.reverse() ? model.sort : null;
                               ManagerSort next = active == sort ? sort.reverse() : sort;
                               if (i == 2) { %><th scope="col">카테고리</th><% } %>
                            <th scope="col" <%= i >= 2 ? "class=\"inventory-number\"" : "" %>
                                <%= active == null ? "" : "aria-sort=\"" + (active.descending ? "descending" : "ascending") + "\"" %>>
                                <a href="<%= model.filter("sort", next.name()) %>" data-admin-query-link
                                   aria-label="<%= sort.label %> <%= next.descending ? "내림차순" : "오름차순" %> 정렬">
                                    <%= sort.label %> <span aria-hidden="true"><%= active == null ? "↕" : active.descending ? "↓" : "↑" %></span>
                                </a>
                            </th>
                        <% } %>
                        <th scope="col">상태</th>
                        <th scope="col">최근 기록된 변경</th>
                        <th scope="col">관리</th>
                    </tr></thead>
                    <tbody>
                    <% if (model.rows.isEmpty()) { %>
                        <tr><td colspan="10" class="store-empty-row"><strong>검색 조건에 맞는 취급 상품이 없습니다.</strong>
                            <span>검색어와 필터를 확인해 주세요.</span>
                            <a href="<%= inventoryUrl %>" data-admin-query-link>검색 조건 초기화</a></td></tr>
                    <% } else {
                        for (InventorySnapshot row : model.rows) {
                            Stock stock = row.stock;
                            String historyUrl = ProductView.url(path, "/app/store-manager/history", Map.of("product", stock.id)); %>
                            <tr data-store-stock data-product-id="<%= ProductView.escape(stock.id) %>"
                                data-name="<%= ProductView.escape(stock.name) %>" data-version="<%= stock.version %>"
                                data-threshold="<%= stock.threshold %>" data-quantity="<%= stock.quantity %>"
                                data-code="<%= ProductView.escape(stock.code) %>" data-image="<%= ProductView.image(path, stock.image) %>">
                                <td class="admin-batch-choice"><% if (!model.token.isEmpty()) { %>
                                    <input type="checkbox" data-store-select aria-label="<%= ProductView.escape(stock.name) %> 선택">
                                <% } %></td>
                                <td><img class="store-inventory-thumb" src="<%= ProductView.image(path, stock.image) %>" alt="" width="44" height="44" loading="lazy"></td>
                                <td class="store-item-name"><strong title="<%= ProductView.escape(stock.name) %>"><%= ProductView.escape(stock.name) %></strong>
                                    <small>ID <%= ProductView.escape(stock.id) %></small></td>
                                <td><%= ProductView.escape(stock.code) %></td>
                                <td><%= ProductView.escape(model.categories.get(row.categoryId)) %></td>
                                <td class="inventory-number"><%= numbers.format(stock.quantity) %>개</td>
                                <td class="inventory-number"><%= numbers.format(stock.threshold) %>개</td>
                                <td><span class="store-inventory-state" data-stock-kind="<%= stock.state().name() %>"><%= stock.state().label %></span></td>
                                <td><%= row.lastQuantityChange == null ? "기록 없음" : StoreManagerInventoryController.recordedTime(row.lastQuantityChange) %></td>
                                <td class="store-stock-management"><div class="store-stock-controls">
                                    <button type="button" class="button primary" data-adjust-preview><%= StoreManagerInventoryController.quantityWritesEnabled() ? "재고 조정" : "조정 미리보기" %></button>
                                    <% if (!model.token.isEmpty()) { %><button type="button" class="button secondary" data-threshold-one>부족 기준 수정</button><% } %>
                                    <a class="button secondary" href="<%= historyUrl %>">변경 이력</a>
                                </div></td>
                            </tr>
                    <%  }
                    } %>
                    </tbody>
                </table>
            </div>
            <% daiso.product.controller.InventoryPagination inventoryPagination = model.pagination;
               request.setAttribute("adminPaginationBoundaryControls", Boolean.TRUE); %>
            <%@ include file="../admin/inventory-pagination.jspf" %>
            <%@ include file="inventory-dialogs.jspf" %>
        </div>
    </main>
    </div>
</body>
</html>
