<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.time.format.DateTimeFormatter,daiso.product.controller.ProductView,daiso.product.controller.StoreInventoryController,daiso.product.controller.StoreInventoryController.Page,daiso.product.controller.StoreInventoryController.Preview,daiso.product.dao.StoreInventoryDAO.Change,daiso.product.dao.StoreInventoryDAO.Assortment,daiso.product.dao.StoreInventoryDAO.AssortmentFilter,daiso.product.dao.StoreInventoryDAO.State,daiso.product.dao.StoreInventoryDAO.Location,daiso.product.dao.StoreInventoryDAO.Stock,daiso.product.dao.StoreInventoryDAO.History" %>
<%
    if (!Boolean.TRUE.equals(request.getAttribute("storeInventoryReady"))) {
        request.getRequestDispatcher(StoreInventoryController.ROUTE).forward(request, response);
        return;
    }
    Page inventoryPage = (Page) request.getAttribute("storeInventoryPage");
    String storeRoute = request.getContextPath() + StoreInventoryController.ROUTE;
    Object storeError = request.getAttribute("storeInventoryError");
    Object storeSuccess = request.getAttribute("storeInventorySaved");
    Object savedProduct = request.getAttribute("storeInventorySavedProduct");
    Map<?, ?> storeDraft = request.getAttribute("storeDraft") instanceof Map<?, ?>
            ? (Map<?, ?>) request.getAttribute("storeDraft") : Map.of();
    Object storeFieldError = request.getAttribute("storeFieldError");
    boolean editingStock = inventoryPage != null && inventoryPage.editing != null;
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-product-workspace.js" defer></script>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1" data-admin-query="stores">
            <h1 class="page-title">매장별 재고 관리</h1>
            <p class="error-message" role="alert" data-admin-query-error hidden></p>
            <div data-admin-query-content>
            <% if (storeSuccess != null) { %>
                <p id="store-save-result" class="inventory-success" role="status"><%= ProductView.escape(storeSuccess) %></p>
            <% } %>
            <p>오프라인 매장 재고입니다. 매장 미취급 상품은 목록에 표시되지 않으며, 취급 상품의 재고가 0개면 품절로 표시됩니다.</p>
            <% if (storeError != null && !editingStock) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(storeError) %></p>
            <% } %>
            <% if (storeError != null && !editingStock && !storeDraft.isEmpty()) {
                   boolean rowVisible = inventoryPage != null && inventoryPage.inventory.stream().anyMatch(stock ->
                           stock.id.equals(storeDraft.get("product"))
                           && (inventoryPage.showingAssortment
                               ? !stock.carried && (stock.version < 0 ? Assortment.REGISTER : Assortment.RESUME)
                                       .name().equals(storeDraft.get("operation"))
                               : stock.quantity == 0 && Assortment.UNLIST.name().equals(storeDraft.get("operation")))); %>
                <% if (!rowVisible || "adjust".equals(storeDraft.get("kind"))) { %>
                    <details class="store-assortment" open>
                        <summary>저장하지 못한 입력값</summary>
                        <% if ("threshold".equals(storeDraft.get("kind"))) { %>
                            <p>입력한 부족 기준 · <%= ProductView.escape(storeDraft.get("threshold")) %></p>
                        <% } else { %>
                            <p>변경 유형 <%= ProductView.escape(storeDraft.get("change")) %><%= ProductView.escape(storeDraft.get("operation")) %></p>
                            <% if (storeDraft.get("amount") != null) { %><p>입력 수량 <%= ProductView.escape(storeDraft.get("amount")) %></p><% } %>
                            <p>변경 사유 · <%= ProductView.escape(storeDraft.get("reason")) %></p>
                        <% } %>
                    </details>
                <% } %>
            <% } %>
            <% if (inventoryPage != null) { %>
                <nav class="store-admin-links" aria-label="매장 지역">
                    <% for (String region : List.of("", "서울", "부산")) { %>
                        <a class="button secondary" data-admin-query-link href="<%= inventoryPage.regionUrl(region) %>"
                           <%= region.equals(inventoryPage.city) ? "aria-current=\"page\"" : "" %>><%= region.isEmpty() ? "전체 지역" : region %></a>
                    <% } %>
                </nav>
                <form class="admin-search store-admin-filters" data-admin-query-form data-store-filters method="get" action="<%= ProductView.escape(storeRoute) %>">
                    <input type="hidden" name="city" value="<%= ProductView.escape(inventoryPage.city) %>">
                    <% if (inventoryPage.showingHistory) { %><input type="hidden" name="mode" value="history"><% } %>
                    <% if (inventoryPage.showingAssortment) { %><input type="hidden" name="mode" value="assortment"><% } %>
                    <label class="field">매장명 검색
                        <input name="storeQuery" type="search" maxlength="40" value="<%= ProductView.escape(inventoryPage.filters.get("storeQuery")) %>">
                    </label>
                    <label class="field">매장
                        <select name="store" required>
                            <% for (Location location : inventoryPage.locations) {
                                   if (!inventoryPage.city.isEmpty() && !location.city.equals(inventoryPage.city)) continue;
                                   String storeTerm = inventoryPage.filters.getOrDefault("storeQuery", "");
                                   if (!location.id.equals(inventoryPage.store)
                                           && !location.name.toLowerCase(java.util.Locale.ROOT).contains(storeTerm.toLowerCase(java.util.Locale.ROOT))) continue; %>
                                <option value="<%= ProductView.escape(location.id) %>" <%= location.id.equals(inventoryPage.store) ? "selected" : "" %>><%= ProductView.escape(location.name) %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">상품명·품번
                        <input name="q" type="search" maxlength="100" value="<%= ProductView.escape(inventoryPage.keyword) %>">
                    </label>
                    <% if (!inventoryPage.showingHistory && !inventoryPage.showingAssortment) { %>
                        <label class="field">재고 상태
                            <select name="state">
                                <% for (State option : State.values()) { %>
                                    <option value="<%= option.name() %>" <%= option == inventoryPage.state ? "selected" : "" %>><%= option.label %></option>
                                <% } %>
                            </select>
                        </label>
                    <% } %>
                    <% if (inventoryPage.showingAssortment) { %>
                        <label class="field">취급 상태
                            <select name="assortment">
                                <% for (AssortmentFilter scope : AssortmentFilter.values()) { %>
                                    <option value="<%= scope.name() %>" <%= scope == inventoryPage.assortmentFilter ? "selected" : "" %>><%= scope.label %></option>
                                <% } %>
                            </select>
                        </label>
                    <% } %>
                    <% if (!inventoryPage.showingHistory && !inventoryPage.showingAssortment) { %>
                        <label class="field">정렬
                            <select name="sort">
                                <% for (daiso.product.dao.StoreInventoryDAO.Sort sort : daiso.product.dao.StoreInventoryDAO.Sort.values()) {
                                       if (sort == daiso.product.dao.StoreInventoryDAO.Sort.REGION) continue; %>
                                    <option value="<%= sort.name() %>" <%= sort.name().equals(inventoryPage.filters.get("sort")) ? "selected" : "" %>><%= sort.label %></option>
                                <% } %>
                            </select>
                        </label>
                    <% } %>
                    <% if (inventoryPage.showingHistory) { %>
                        <label class="field">이력 기간 <select name="historyPeriod">
                            <% for (daiso.product.controller.AdminReviewController.Period period : daiso.product.controller.AdminReviewController.Period.values()) { %>
                                <option value="<%= period.name() %>" <%= period.name().equals(inventoryPage.filters.get("historyPeriod")) ? "selected" : "" %>><%= period.label %></option>
                            <% } %>
                        </select></label>
                        <label class="field">시작일 <input type="date" name="historyStart" value="<%= ProductView.escape(inventoryPage.filters.get("historyStart")) %>"></label>
                        <label class="field">종료일 <input type="date" name="historyEnd" value="<%= ProductView.escape(inventoryPage.filters.get("historyEnd")) %>"></label>
                        <label class="field">변경 유형 <select name="historyType">
                            <% for (String[] type : new String[][] {{"", "전체"}, {"INITIAL", "초기 등록"}, {"RECEIVE", "입고"}, {"ISSUE", "출고"},
                                    {"DAMAGE", "파손"}, {"RETURN", "반품 입고"}, {"COUNT", "재고 조사"}, {"THRESHOLD", "부족 기준"},
                                    {"TRANSFER", "매장 이동"}, {"REGISTER", "취급 등록"}, {"RESUME","취급 재개"}, {"UNLIST","취급 해제"}}) { %>
                                <option value="<%= type[0] %>" <%= type[0].equals(inventoryPage.filters.get("historyType")) ? "selected" : "" %>><%= type[1] %></option>
                            <% } %>
                        </select></label>
                        <label class="field">관리자 ID <input name="historyActor" maxlength="100" value="<%= ProductView.escape(inventoryPage.filters.get("historyActor")) %>"></label>
                        <label class="field">이동 번호 <input name="historyReference" maxlength="36" value="<%= ProductView.escape(inventoryPage.filters.get("historyReference")) %>"></label>
                        <label class="field">이력 정렬 <select name="historyOrder"><option value="">최신순</option>
                            <option value="oldest" <%= "oldest".equals(inventoryPage.filters.get("historyOrder")) ? "selected" : "" %>>오래된순</option></select></label>
                    <% } %>
                    <div class="inventory-filter-actions">
                        <button class="button primary" type="submit">검색</button>
                        <a class="button secondary" href="<%= inventoryPage.resetUrl() %>" data-admin-query-link>초기화</a>
                    </div>
                </form>
                <%@ include file="store-inventory-compare.jspf" %>
                <% if (inventoryPage.selected == null) { %>
                    <p class="empty-state">표시할 매장이 없습니다.</p>
                <% } else { %>
                    <h2><%= ProductView.escape(inventoryPage.selected.name) %></h2>
                    <div class="store-metrics" aria-label="매장 재고 요약">
                        <div><span>취급 상품</span><strong><%= inventoryPage.summary.kinds %>종</strong></div>
                        <div><span>총 재고</span><strong><%= inventoryPage.summary.units %>개</strong></div>
                        <a data-stock-state="LOW" href="<%= inventoryPage.summaryUrl(State.LOW) %>"><span>재고 부족</span><strong><%= inventoryPage.summary.low %>종</strong></a>
                        <a data-stock-state="SOLD_OUT" href="<%= inventoryPage.summaryUrl(State.SOLD_OUT) %>"><span>품절 (0개)</span><strong><%= inventoryPage.summary.soldOut %>종</strong></a>
                    </div>
                    <nav class="store-admin-links" aria-label="매장 관리 화면">
                        <a class="button secondary" data-admin-query-link href="<%= inventoryPage.inventoryUrl %>" <%= !inventoryPage.showingHistory && !inventoryPage.showingAssortment ? "aria-current=\"page\"" : "" %>>취급 상품</a>
                        <a class="button secondary" data-admin-query-link href="<%= inventoryPage.assortmentUrl %>" <%= inventoryPage.showingAssortment ? "aria-current=\"page\"" : "" %>>취급 상품 등록·재개</a>
                        <a class="button secondary" data-admin-query-link href="<%= inventoryPage.historyUrl %>" <%= inventoryPage.showingHistory ? "aria-current=\"page\"" : "" %>>재고 변경 이력</a>
                    </nav>
                    <% if (inventoryPage.editing != null) {
                           Stock stock = inventoryPage.editing;
                           Preview preview = inventoryPage.preview; %>
                        <dialog id="store-adjustment" class="store-adjustment store-drawer" open aria-labelledby="store-adjustment-title"
                                data-row-anchor="<%= Page.rowAnchor(stock.id) %>">
                            <header class="store-drawer-heading">
                                <h3 id="store-adjustment-title" tabindex="-1">재고·기준 조정</h3>
                                <a class="button secondary" href="<%= inventoryPage.closeDrawerUrl(stock.id) %>" data-drawer-close>닫기</a>
                            </header>
                            <p><%= ProductView.escape(inventoryPage.selected.name) %> · <strong><%= ProductView.escape(stock.name) %></strong></p>
                            <% if (storeError != null) { %>
                                <p class="error-message" id="store-adjustment-error" role="alert"><%= ProductView.escape(storeError) %></p>
                            <% } %>
                            <form method="post" data-admin-draft data-stock-editor data-store-write data-before="<%= stock.quantity %>" action="<%= ProductView.escape(storeRoute) %>/adjust">
                                <% for (Map.Entry<String, String> field : inventoryPage.filters.entrySet()) { %>
                                    <input type="hidden" name="<%= field.getKey() %>" value="<%= ProductView.escape(field.getValue()) %>">
                                <% } %>
                                <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("storeInventoryCsrf")) %>">
                                <input type="hidden" name="product" value="<%= ProductView.escape(stock.id) %>">
                                <input type="hidden" name="version" value="<%= stock.version %>">
                                <% if (preview == null) { %>
                                    <p>현재 매장 재고 <strong><%= stock.quantity %>개</strong></p>
                                    <div class="store-adjustment-fields">
                                        <label class="field">변경 유형
                                            <select name="change" required <%= "change".equals(storeFieldError) ? "aria-invalid=\"true\" aria-describedby=\"store-adjustment-error\"" : "" %>>
                                                <% for (Change change : Change.values()) { %>
                                                    <option value="<%= change.name() %>" <%= change.name().equals(storeDraft.get("change")) ? "selected" : "" %>><%= change.label %></option>
                                                <% } %>
                                            </select>
                                        </label>
                                        <label class="field"><span data-amount-label>조정 수량</span>
                                            <input name="amount" type="number" inputmode="numeric" min="0" max="2147483647" step="1" required
                                                   value="<%= ProductView.escape(storeDraft.get("amount")) %>"
                                                   aria-describedby="store-amount-hint<%= "amount".equals(storeFieldError) ? " store-adjustment-error" : "" %>"
                                                   <%= "amount".equals(storeFieldError) ? "aria-invalid=\"true\"" : "" %>>
                                        </label>
                                    </div>
                                    <p id="store-amount-hint">재고 조사는 최종 수량, 그 외에는 증감 수량을 입력하세요.</p>
                                    <dl class="store-adjustment-summary" data-live-stock hidden>
                                        <div><dt>변경 전</dt><dd><%= stock.quantity %>개</dd></div>
                                        <div><dt>변경량</dt><dd data-stock-delta>—</dd></div>
                                        <div><dt>변경 후</dt><dd data-stock-after>—</dd></div>
                                    </dl>
                                    <p data-stock-invalid class="error-message" role="status" hidden></p>
                                    <label class="field">변경 사유
                                        <textarea name="reason" maxlength="255" rows="2" required <%= "reason".equals(storeFieldError) ? "aria-invalid=\"true\" aria-describedby=\"store-adjustment-error\"" : "" %>><%= ProductView.escape(storeDraft.get("reason")) %></textarea>
                                    </label>
                                    <div class="store-admin-links">
                                        <button class="button primary" type="submit" name="action" value="preview">변경 전후 확인</button>
                                        <a class="button secondary" href="<%= inventoryPage.closeDrawerUrl(stock.id) %>" data-drawer-close>취소</a>
                                    </div>
                                <% } else { %>
                                    <input type="hidden" name="change" value="<%= preview.adjustment.change.name() %>">
                                    <input type="hidden" name="amount" value="<%= preview.adjustment.amount %>">
                                    <input type="hidden" name="reason" value="<%= ProductView.escape(preview.adjustment.reason) %>">
                                    <dl class="store-adjustment-summary">
                                        <div><dt>변경 유형</dt><dd><%= preview.adjustment.change.label %></dd></div>
                                        <div><dt>변경 전</dt><dd><%= stock.quantity %>개</dd></div>
                                        <div><dt>변경량</dt><dd><%= preview.after > stock.quantity ? "+" : "" %><%= preview.after - stock.quantity %>개</dd></div>
                                        <div><dt>변경 후</dt><dd><strong><%= preview.after %>개</strong></dd></div>
                                    </dl>
                                    <p>변경 사유 · <%= ProductView.escape(preview.adjustment.reason) %></p>
                                    <div class="store-admin-links">
                                        <button class="button primary" type="submit" name="action" value="save">재고 반영</button>
                                        <button class="button secondary" type="submit" name="action" value="revise">다시 작성</button>
                                    </div>
                                <% } %>
                            </form>
                            <% if (preview == null) { %>
                                <section class="store-threshold" aria-labelledby="store-threshold-heading">
                                    <h4 id="store-threshold-heading">부족 기준</h4>
                                    <% if (inventoryPage.thresholdsEnabled) { %>
                                        <form method="post" data-store-write data-store-threshold
                                              action="<%= ProductView.escape(storeRoute) %>/threshold">
                                            <% for (Map.Entry<String, String> field : inventoryPage.filters.entrySet()) { %>
                                                <input type="hidden" name="<%= field.getKey() %>" value="<%= ProductView.escape(field.getValue()) %>">
                                            <% } %>
                                            <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("storeInventoryCsrf")) %>">
                                            <input type="hidden" name="product" value="<%= ProductView.escape(stock.id) %>">
                                            <input type="hidden" name="version" value="<%= stock.version %>">
                                            <label class="field">새 부족 기준 (개 이하)
                                                <input name="threshold" type="number" inputmode="numeric" min="1" max="2147483647" step="1" required
                                                       value="<%= ProductView.escape(storeDraft.containsKey("threshold") ? storeDraft.get("threshold") : stock.threshold) %>"
                                                       <%= "threshold".equals(storeFieldError) ? "aria-invalid=\"true\" aria-describedby=\"store-adjustment-error\"" : "" %>>
                                            </label>
                                            <dl class="store-adjustment-summary">
                                                <div><dt>변경 전</dt><dd><%= stock.threshold %>개 이하</dd></div>
                                                <div data-threshold-preview hidden><dt>변경 후</dt><dd><output data-threshold-after></output></dd></div>
                                            </dl>
                                            <button class="button secondary" type="submit">부족 기준 저장</button>
                                        </form>
                                    <% } else { %>
                                        <p>현재 기준은 <%= stock.threshold %>개 이하입니다. 마이그레이션 적용 후 변경할 수 있습니다.</p>
                                    <% } %>
                                </section>
                            <% } %>
                        </dialog>
                    <% } %>
                    <p class="store-stock-hint">조회 결과 <%= inventoryPage.total %>건</p>
                    <% if (inventoryPage.showingHistory) {
                           DateTimeFormatter historyDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); %>
                        <p class="inventory-range">전체 <%= inventoryPage.total %>건
                            <% if (!inventoryPage.history.isEmpty()) { %> · <%= (inventoryPage.page - 1) * 20 + 1 %>–<%= (inventoryPage.page - 1) * 20 + inventoryPage.history.size() %>번째 표시<% } %></p>
                        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="매장 재고 변경 이력 표">
                            <table class="admin-table">
                                <thead><tr>
                                    <th scope="col">일시</th><th scope="col">상품</th><th scope="col">유형</th>
                                    <th scope="col">변경 전 → 후</th><th scope="col">사유</th><th scope="col">관리자</th>
                                </tr></thead>
                                <tbody>
                                    <% for (History entry : inventoryPage.history) { %>
                                        <tr>
                                            <td><time datetime="<%= entry.date %>"><%= historyDate.format(entry.date) %></time></td>
                                            <td><%= ProductView.escape(entry.name) %><br><small><%= ProductView.escape(entry.code.isEmpty() ? entry.reference : entry.code) %></small></td>
                                            <td><%= entry.transferId == null ? entry.type : "매장 이동 · " + entry.type %>
                                                <% if (entry.transferId != null) { %><br><small><%= ProductView.escape(entry.transferId) %></small><% } %></td>
                                            <td><% if (entry.beforeThreshold != null) { %>
                                                    <%= entry.beforeThreshold %>개 이하 → <%= entry.afterThreshold %>개 이하
                                                <% } else { %>
                                                    <%= entry.before %>개 → <%= entry.after %>개 (<%= (long) entry.after - entry.before %>)
                                                <% } %></td>
                                            <td><%= ProductView.escape(entry.reason) %></td>
                                            <td><%= entry.administrator == null ? "초기 데이터" : ProductView.escape(entry.administrator) %></td>
                                        </tr>
                                    <% } %>
                                    <% if (inventoryPage.history.isEmpty()) { %>
                                        <tr><td colspan="6">조건에 맞는 변경 이력이 없습니다.</td></tr>
                                    <% } %>
                                </tbody>
                            </table>
                        </div>
                    <% } else { %>
                        <% if (!inventoryPage.showingAssortment) { %><p class="store-stock-hint">재고가 0개인 상품은 취급 해제할 수 있어요.</p><% } %>
                        <p class="inventory-range">전체 <strong><%= inventoryPage.total %></strong>종
                            <% if (!inventoryPage.inventory.isEmpty()) { %> · <%= (inventoryPage.page - 1) * 20 + 1 %>–<%= (inventoryPage.page - 1) * 20 + inventoryPage.inventory.size() %>번째 표시<% } %></p>
                        <div id="store-inventory-table" tabindex="-1"></div>
                        <% if (!inventoryPage.showingAssortment) {
                    boolean storeBatch = true;
                       Map<String, String> batchFilters = inventoryPage.filters;
                       String batchCsrf = String.valueOf(request.getAttribute("storeInventoryCsrf")); %>
                <%@ include file="inventory-batch-form.jspf" %>
                <% } %>
                <div class="admin-table-scroll" tabindex="0" role="region" aria-label="매장 취급 상품 표">
                            <table class="admin-table store-stock-table">
                                <thead><tr>
                                    <% if (!inventoryPage.showingAssortment) { %><th scope="col">선택·개별값</th><% } %><th scope="col">상품</th><th scope="col">매장 재고</th>
                                    <th scope="col">상태</th>
                                    <% if (!inventoryPage.showingAssortment) { %><th scope="col">부족 기준</th><% } %>
                                    <th scope="col">관리</th>
                                </tr></thead>
                                <tbody>
                                    <% for (Stock stock : inventoryPage.inventory) {
                                           State state = stock.state();
                                           boolean activeList = !inventoryPage.showingAssortment;
                                           Assortment action = activeList ? Assortment.UNLIST
                                                   : stock.version < 0 ? Assortment.REGISTER : Assortment.RESUME;
                                           boolean editingAssortment = stock.id.equals(storeDraft.get("product"))
                                                   && action.name().equals(storeDraft.get("operation"));
                                           boolean confirmUnlist = stock.id.equals(request.getAttribute("storeUnlistConfirmation"));
                                           boolean savedRow = stock.id.equals(savedProduct); %>
                                        <tr id="<%= Page.rowAnchor(stock.id) %>" tabindex="-1"<%= savedRow ? " data-saved-row aria-describedby=\"store-save-result\"" : "" %>>
                                            <% if (activeList) { %><td class="admin-batch-choice"><input type="checkbox" name="batchProduct" form="inventory-batch-form"
                    value="<%= ProductView.escape(stock.id) %>" aria-label="<%= ProductView.escape(stock.name) %> 선택">
                <input name="amount.<%= ProductView.escape(stock.id) %>" form="inventory-batch-form" type="number" min="1"
                       placeholder="공통값 사용" aria-label="<%= ProductView.escape(stock.name) %> 개별 수량 또는 기준"></td><% } %>
                <td><div class="store-stock-product">
                                                <img src="<%= ProductView.image(inventoryPage.context, stock.image) %>" width="48" height="52" loading="lazy" alt="">
                                                <div><strong><%= ProductView.escape(stock.name) %></strong><small>품번 <%= ProductView.escape(stock.code) %></small>
                                                    <% if (activeList && state != State.NORMAL) { %>
                                                        <small>정상까지 최소 <%= (long) stock.threshold + 1 - stock.quantity %>개 입고</small>
                                                    <% } %></div>
                                            </div></td>
                                            <td><%= stock.carried || stock.version >= 0 ? stock.quantity + "개" : "—" %></td>
                                            <td>
                                                <% if (activeList) { %>
                                                    <span class="store-stock-badge" data-stock-state="<%= state.name() %>"><%= state.label %></span>
                                                <% } else { %>
                                                    <%= stock.carried ? "취급 중" : stock.version < 0 ? "미등록" : "취급 종료" %>
                                                <% } %>
                                            </td>
                                            <% if (activeList) { %><td><%= stock.threshold %>개 이하</td><% } %>
                                            <td>
                                                <div class="store-stock-actions">
                                                    <% if (activeList) { %>
                                                        <a class="button secondary" href="<%= inventoryPage.editUrl(stock.id) %>#store-adjustment"
                                                           data-open-store-drawer aria-label="<%= ProductView.escape(stock.name) %> 재고·기준 조정">재고·기준 조정</a>
                                                    <% } %>
                                                    <% if (activeList ? stock.quantity == 0 : !stock.carried) { %>
                                                        <details class="store-assortment" <%= editingAssortment ? "open" : "" %>>
                                                            <summary><%= action.label %></summary>
                                                            <form class="store-assortment-form" data-store-write method="post" action="<%= ProductView.escape(storeRoute) %>/assortment">
                                                                <% for (Map.Entry<String, String> field : inventoryPage.filters.entrySet()) { %>
                                                                    <input type="hidden" name="<%= field.getKey() %>" value="<%= ProductView.escape(field.getValue()) %>">
                                                                <% } %>
                                                                <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("storeInventoryCsrf")) %>">
                                                                <input type="hidden" name="product" value="<%= ProductView.escape(stock.id) %>">
                                                                <input type="hidden" name="version" value="<%= confirmUnlist ? request.getAttribute("storeUnlistVersion") : stock.version %>">
                                                                <input type="hidden" name="operation" value="<%= action.name() %>">
                                                                <% if (confirmUnlist) { %>
                                                                    <input type="hidden" name="unlistToken" value="<%= ProductView.escape(request.getAttribute("storeUnlistToken")) %>">
                                                                    <p tabindex="-1" data-unlist-confirm><strong><%= ProductView.escape(stock.name) %></strong>의 취급을 종료할까요? 고객 화면에 미취급으로 표시됩니다.</p>
                                                                    <p>변경 사유 · <%= ProductView.escape(storeDraft.get("reason")) %></p>
                                                                    <input type="hidden" name="reason" value="<%= ProductView.escape(storeDraft.get("reason")) %>">
                                                                <% } else { %>
                                                                <% if (action == Assortment.UNLIST) { %>
                                                                    <p>현재 재고 0개</p>
                                                                <% } else { %>
                                                                    <% if (action == Assortment.RESUME) { %>
                                                                        <p>해제 상태에서 남은 수량은 <%= stock.quantity %>개입니다. 재개 시 실제 수량을 입력하세요.</p>
                                                                    <% } %>
                                                                    <label class="field">초기 재고
                                                                        <input type="number" name="amount" min="0" max="2147483647" step="1" required value="<%= editingAssortment ? ProductView.escape(storeDraft.get("amount")) : "" %>">
                                                                    </label>
                                                                <% } %>
                                                                <label class="field">변경 사유
                                                                    <textarea name="reason" rows="2" maxlength="255" required><%= editingAssortment ? ProductView.escape(storeDraft.get("reason")) : "" %></textarea>
                                                                </label>
                                                                <% } %>
                                                                <% if (action == Assortment.UNLIST) { %>
                                                                    <button class="button <%= confirmUnlist ? "inventory-danger" : "secondary" %>" type="submit" name="action" value="<%= confirmUnlist ? "save" : "preview" %>"><%= confirmUnlist ? "취급 해제 확정" : "해제 내용 확인" %></button>
                                                                    <% if (confirmUnlist) { %><a class="button secondary" href="<%= inventoryPage.closeDrawerUrl(stock.id) %>" data-drawer-close>취소</a><% } %>
                                                                <% } else { %>
                                                                    <button class="button primary" type="submit" name="action" value="save"><%= action.label %></button>
                                                                <% } %>
                                                            </form>
                                                        </details>
                                                    <% } %>
                                                </div>
                                            </td>
                                        </tr>
                                    <% } %>
                                    <% if (inventoryPage.inventory.isEmpty()) { %>
                                        <tr><td colspan="<%= inventoryPage.showingAssortment ? 4 : 6 %>">조건에 맞는 상품이 없습니다.</td></tr>
                                    <% } %>
                                </tbody>
                            </table>
                        </div>
                    <% } %>
                    <% if (inventoryPage.total > 0) {
                           InventoryPagination inventoryPagination = inventoryPage.pagination; %>
                        <%@ include file="inventory-pagination.jspf" %>
                    <% } %>
                <% } %>
            <% } %>
            </div>
        </main>
    </div>
</body>
</html>
