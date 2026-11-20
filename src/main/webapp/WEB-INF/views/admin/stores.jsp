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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1">
            <h1 class="page-title">매장별 재고 관리</h1>
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
                        <p>변경 유형 <%= ProductView.escape(storeDraft.get("change")) %><%= ProductView.escape(storeDraft.get("operation")) %></p>
                        <% if (storeDraft.get("amount") != null) { %><p>입력 수량 <%= ProductView.escape(storeDraft.get("amount")) %></p><% } %>
                        <p>변경 사유 · <%= ProductView.escape(storeDraft.get("reason")) %></p>
                    </details>
                <% } %>
            <% } %>
            <% if (inventoryPage != null) { %>
                <nav class="store-admin-links" aria-label="매장 지역">
                    <% for (String region : List.of("", "서울", "부산")) { %>
                        <a class="button secondary" href="<%= inventoryPage.regionUrl(region) %>"
                           <%= region.equals(inventoryPage.city) ? "aria-current=\"page\"" : "" %>><%= region.isEmpty() ? "전체 지역" : region %></a>
                    <% } %>
                </nav>
                <form class="admin-search store-admin-filters" data-store-filters method="get" action="<%= ProductView.escape(storeRoute) %>">
                    <input type="hidden" name="city" value="<%= ProductView.escape(inventoryPage.city) %>">
                    <% if (inventoryPage.showingHistory) { %><input type="hidden" name="mode" value="history"><% } %>
                    <% if (inventoryPage.showingAssortment) { %><input type="hidden" name="mode" value="assortment"><% } %>
                    <label class="field">매장
                        <select name="store" required>
                            <% for (Location location : inventoryPage.locations) {
                                   if (!inventoryPage.city.isEmpty() && !location.city.equals(inventoryPage.city)) continue; %>
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
                    <div class="inventory-filter-actions">
                        <button class="button primary" type="submit">검색</button>
                        <a class="button secondary" href="<%= inventoryPage.resetUrl() %>">초기화</a>
                    </div>
                </form>
                <% if (inventoryPage.selected == null) { %>
                    <p class="empty-state">표시할 매장이 없습니다.</p>
                <% } else { %>
                    <h2><%= ProductView.escape(inventoryPage.selected.name) %></h2>
                    <div class="store-metrics" aria-label="매장 재고 요약">
                        <div><span>취급 상품</span><strong><%= inventoryPage.summary.kinds %>종</strong></div>
                        <div><span>총 재고</span><strong><%= inventoryPage.summary.units %>개</strong></div>
                        <a data-stock-state="LOW" href="<%= inventoryPage.summaryUrl(State.LOW) %>"><span>재고 부족 (1~5개)</span><strong><%= inventoryPage.summary.low %>종</strong></a>
                        <a data-stock-state="SOLD_OUT" href="<%= inventoryPage.summaryUrl(State.SOLD_OUT) %>"><span>품절 (0개)</span><strong><%= inventoryPage.summary.soldOut %>종</strong></a>
                    </div>
                    <nav class="store-admin-links" aria-label="매장 관리 화면">
                        <a class="button secondary" href="<%= inventoryPage.inventoryUrl %>" <%= !inventoryPage.showingHistory && !inventoryPage.showingAssortment ? "aria-current=\"page\"" : "" %>>취급 상품</a>
                        <a class="button secondary" href="<%= inventoryPage.assortmentUrl %>" <%= inventoryPage.showingAssortment ? "aria-current=\"page\"" : "" %>>취급 상품 등록·재개</a>
                        <a class="button secondary" href="<%= inventoryPage.historyUrl %>" <%= inventoryPage.showingHistory ? "aria-current=\"page\"" : "" %>>재고 변경 이력</a>
                    </nav>
                    <% if (inventoryPage.editing != null) {
                           Stock stock = inventoryPage.editing;
                           Preview preview = inventoryPage.preview; %>
                        <dialog id="store-adjustment" class="store-adjustment store-drawer" open aria-labelledby="store-adjustment-title"
                                data-row-anchor="<%= Page.rowAnchor(stock.id) %>">
                            <header class="store-drawer-heading">
                                <h3 id="store-adjustment-title" tabindex="-1">재고 조정</h3>
                                <a class="button secondary" href="<%= inventoryPage.closeDrawerUrl(stock.id) %>" data-drawer-close>닫기</a>
                            </header>
                            <p><%= ProductView.escape(inventoryPage.selected.name) %> · <strong><%= ProductView.escape(stock.name) %></strong></p>
                            <% if (storeError != null) { %>
                                <p class="error-message" id="store-adjustment-error" role="alert"><%= ProductView.escape(storeError) %></p>
                            <% } %>
                            <form method="post" data-stock-editor data-store-write data-before="<%= stock.quantity %>" action="<%= ProductView.escape(storeRoute) %>/adjust">
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
                        </dialog>
                    <% } %>
                    <% if (inventoryPage.showingHistory) {
                           DateTimeFormatter historyDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); %>
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
                                            <td><%= entry.type %></td>
                                            <td><%= entry.before %> → <%= entry.after %></td>
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
                        <div id="store-inventory-table" tabindex="-1"></div>
                        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="매장 취급 상품 표">
                            <table class="admin-table store-stock-table">
                                <thead><tr>
                                    <th scope="col">상품</th><th scope="col">매장 재고</th>
                                    <th scope="col">상태</th><th scope="col">관리</th>
                                </tr></thead>
                                <tbody>
                                    <% for (Stock stock : inventoryPage.inventory) {
                                           State state = State.ofQuantity(stock.quantity);
                                           boolean activeList = !inventoryPage.showingAssortment;
                                           Assortment action = activeList ? Assortment.UNLIST
                                                   : stock.version < 0 ? Assortment.REGISTER : Assortment.RESUME;
                                           boolean editingAssortment = stock.id.equals(storeDraft.get("product"))
                                                   && action.name().equals(storeDraft.get("operation"));
                                           boolean confirmUnlist = stock.id.equals(request.getAttribute("storeUnlistConfirmation"));
                                           boolean savedRow = stock.id.equals(savedProduct); %>
                                        <tr id="<%= Page.rowAnchor(stock.id) %>" tabindex="-1"<%= savedRow ? " data-saved-row aria-describedby=\"store-save-result\"" : "" %>>
                                            <td><div class="store-stock-product">
                                                <img src="<%= ProductView.image(inventoryPage.context, stock.image) %>" width="48" height="52" loading="lazy" alt="">
                                                <div><strong><%= ProductView.escape(stock.name) %></strong><small>품번 <%= ProductView.escape(stock.code) %></small></div>
                                            </div></td>
                                            <td><%= stock.carried || stock.version >= 0 ? stock.quantity + "개" : "—" %></td>
                                            <td>
                                                <% if (activeList) { %>
                                                    <span class="store-stock-badge" data-stock-state="<%= state.name() %>"><%= state.label %></span>
                                                <% } else { %>
                                                    <%= stock.carried ? "취급 중" : stock.version < 0 ? "미등록" : "취급 종료" %>
                                                <% } %>
                                            </td>
                                            <td>
                                                <div class="store-stock-actions">
                                                    <% if (activeList) { %>
                                                        <a class="button secondary" href="<%= inventoryPage.editUrl(stock.id) %>#store-adjustment"
                                                           data-open-store-drawer aria-label="<%= ProductView.escape(stock.name) %> 재고 조정">재고 조정</a>
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
                                        <tr><td colspan="4">조건에 맞는 상품이 없습니다.</td></tr>
                                    <% } %>
                                </tbody>
                            </table>
                        </div>
                    <% } %>
                    <% if (inventoryPage.previous != null || inventoryPage.next != null) { %>
                        <nav class="admin-product-pagination" aria-label="매장 재고 페이지">
                            <% if (inventoryPage.previous != null) { %><a class="button secondary" rel="prev" href="<%= inventoryPage.previous %>">이전</a><% } %>
                            <span aria-current="page"><%= inventoryPage.page %> 페이지</span>
                            <% if (inventoryPage.next != null) { %><a class="button secondary" rel="next" href="<%= inventoryPage.next %>">다음</a><% } %>
                        </nav>
                    <% } %>
                <% } %>
            <% } %>
        </main>
    </div>
</body>
</html>
