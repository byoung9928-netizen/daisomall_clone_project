<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.time.format.DateTimeFormatter,daiso.product.controller.ProductView,daiso.product.controller.StoreInventoryController,daiso.product.controller.StoreInventoryController.Page,daiso.product.controller.StoreInventoryController.Preview,daiso.product.dao.StoreInventoryDAO.Change,daiso.product.dao.StoreInventoryDAO.State,daiso.product.dao.StoreInventoryDAO.Location,daiso.product.dao.StoreInventoryDAO.Stock,daiso.product.dao.StoreInventoryDAO.History" %>
<%
    if (!Boolean.TRUE.equals(request.getAttribute("storeInventoryReady"))) {
        request.getRequestDispatcher(StoreInventoryController.ROUTE).forward(request, response);
        return;
    }
    Page inventoryPage = (Page) request.getAttribute("storeInventoryPage");
    String storeRoute = request.getContextPath() + StoreInventoryController.ROUTE;
    Object storeError = request.getAttribute("storeInventoryError");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1">
            <h1 class="page-title">매장별 재고 관리</h1>
            <p>오프라인 매장 재고입니다. 매장 미취급 상품은 목록에 표시되지 않으며, 취급 상품의 재고가 0개면 품절로 표시됩니다.</p>
            <% if (storeError != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(storeError) %></p>
            <% } %>
            <% if (Boolean.TRUE.equals(request.getAttribute("storeInventorySaved"))) { %>
                <p class="form-status" role="status">매장 재고와 변경 이력을 저장했습니다.</p>
            <% } %>
            <% if (inventoryPage != null) { %>
                <nav class="store-admin-links" aria-label="매장 지역">
                    <% for (String region : List.of("", "서울", "부산")) { %>
                        <a class="button secondary" href="<%= inventoryPage.regionUrl(region) %>"
                           <%= region.equals(inventoryPage.city) ? "aria-current=\"page\"" : "" %>><%= region.isEmpty() ? "전체 지역" : region %></a>
                    <% } %>
                </nav>
                <form class="admin-search store-admin-filters" method="get" action="<%= ProductView.escape(storeRoute) %>">
                    <input type="hidden" name="city" value="<%= ProductView.escape(inventoryPage.city) %>">
                    <% if (inventoryPage.showingHistory) { %><input type="hidden" name="mode" value="history"><% } %>
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
                    <% if (!inventoryPage.showingHistory) { %>
                        <label class="field">재고 상태
                            <select name="state">
                                <% for (State option : State.values()) { %>
                                    <option value="<%= option.name() %>" <%= option == inventoryPage.state ? "selected" : "" %>><%= option.label %></option>
                                <% } %>
                            </select>
                        </label>
                    <% } %>
                    <button class="button primary" type="submit">조회</button>
                </form>
                <% if (inventoryPage.selected == null) { %>
                    <p class="empty-state">표시할 매장이 없습니다.</p>
                <% } else { %>
                    <h2><%= ProductView.escape(inventoryPage.selected.name) %></h2>
                    <nav class="store-admin-links" aria-label="매장 관리 화면">
                        <a class="button secondary" href="<%= inventoryPage.inventoryUrl %>" <%= !inventoryPage.showingHistory ? "aria-current=\"page\"" : "" %>>취급 상품</a>
                        <a class="button secondary" href="<%= inventoryPage.historyUrl %>" <%= inventoryPage.showingHistory ? "aria-current=\"page\"" : "" %>>재고 변경 이력</a>
                    </nav>
                    <% if (inventoryPage.editing != null) {
                           Stock stock = inventoryPage.editing;
                           Preview preview = inventoryPage.preview; %>
                        <section id="store-adjustment" class="admin-panel store-adjustment" aria-labelledby="store-adjustment-title">
                            <h3 id="store-adjustment-title">재고 조정 · <%= ProductView.escape(stock.name) %></h3>
                            <form method="post" action="<%= ProductView.escape(storeRoute) %>/adjust">
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
                                            <select name="change" required>
                                                <% for (Change change : Change.values()) { %>
                                                    <option value="<%= change.name() %>"><%= change.label %></option>
                                                <% } %>
                                            </select>
                                        </label>
                                        <label class="field">수량
                                            <input name="amount" type="number" min="0" max="2147483647" step="1" required aria-describedby="store-amount-hint">
                                        </label>
                                    </div>
                                    <p id="store-amount-hint">재고 조사는 실사한 최종 수량을 입력하세요. 그 외에는 늘리거나 줄일 수량을 입력하세요. 반품 입고는 판매 가능한 반품만 반영합니다.</p>
                                    <label class="field">변경 사유
                                        <textarea name="reason" maxlength="255" rows="2" required></textarea>
                                    </label>
                                    <div class="store-admin-links">
                                        <button class="button primary" type="submit" name="action" value="preview">변경 전후 확인</button>
                                        <a class="button secondary" href="<%= inventoryPage.inventoryUrl %>">취소</a>
                                    </div>
                                <% } else { %>
                                    <input type="hidden" name="change" value="<%= preview.adjustment.change.name() %>">
                                    <input type="hidden" name="amount" value="<%= preview.adjustment.amount %>">
                                    <input type="hidden" name="reason" value="<%= ProductView.escape(preview.adjustment.reason) %>">
                                    <dl class="store-adjustment-summary">
                                        <div><dt>변경 유형</dt><dd><%= preview.adjustment.change.label %></dd></div>
                                        <div><dt>변경 전</dt><dd><%= stock.quantity %>개</dd></div>
                                        <div><dt>변경 후</dt><dd><strong><%= preview.after %>개</strong></dd></div>
                                    </dl>
                                    <p>변경 사유 · <%= ProductView.escape(preview.adjustment.reason) %></p>
                                    <div class="store-admin-links">
                                        <button class="button primary" type="submit" name="action" value="save">재고 반영</button>
                                        <a class="button secondary" href="<%= inventoryPage.editUrl(stock.id) %>#store-adjustment">다시 작성</a>
                                    </div>
                                <% } %>
                            </form>
                        </section>
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
                        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="매장 취급 상품 표">
                            <table class="admin-table">
                                <thead><tr>
                                    <th scope="col">품번</th><th scope="col">상품명</th><th scope="col">매장 재고</th>
                                    <th scope="col">재고 상태</th><th scope="col">관리</th>
                                </tr></thead>
                                <tbody>
                                    <% for (Stock stock : inventoryPage.inventory) { %>
                                        <tr>
                                            <td><%= ProductView.escape(stock.code) %></td>
                                            <td><%= ProductView.escape(stock.name) %></td>
                                            <td><%= stock.quantity %>개</td>
                                            <td><%= stock.stateLabel() %></td>
                                            <td><a class="button secondary" href="<%= inventoryPage.editUrl(stock.id) %>#store-adjustment"
                                                   aria-label="<%= ProductView.escape(stock.name) %> 재고 조정">재고 조정</a></td>
                                        </tr>
                                    <% } %>
                                    <% if (inventoryPage.inventory.isEmpty()) { %>
                                        <tr><td colspan="5">조건에 맞는 취급 상품이 없습니다.</td></tr>
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
