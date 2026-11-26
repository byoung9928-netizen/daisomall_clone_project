<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductConversionDAO.Metrics,java.util.Locale,java.util.Map,daiso.product.controller.ProductView,daiso.product.controller.ProductPerformanceDAO.Row,daiso.product.controller.ProductPerformanceDAO.TrendBucket,daiso.product.controller.AdminProductPerformanceController,daiso.product.controller.AdminProductPerformanceController.Page,daiso.product.controller.AdminProductPerformanceController.Period,daiso.product.controller.AdminProductPerformanceController.Sort,daiso.product.controller.AdminProductPerformanceController.Tab,daiso.product.controller.AdminProductPerformanceController.Quick,daiso.product.controller.AdminProductPerformanceController.Rank,daiso.product.controller.ProductPerformanceDAO.Ranked,daiso.product.controller.ProductPerformanceDAO.CategorySales" %>
<%@ include file="product-performance-format.jspf" %>
<%
    Page performance = (Page) request.getAttribute("productPerformance");
    String route = request.getContextPath() + "/app/admin/products/performance";
    String context = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-product-workspace.css?v=batch-selection-2">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-stores.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-product-performance.css?v=performance-hourly-1">
    <script src="<%= context %>/assets/js/common/admin-product-workspace.js?v=performance-hourly-1" defer></script>
    <script src="<%= context %>/assets/js/common/admin-product-suggestions.js?v=inventory-suggest-1" defer></script>
    <script src="<%= context %>/assets/js/common/admin-product-performance.js?v=performance-hourly-1" defer></script>
</head>
<body class="admin-body product-admin-ux">
<a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
<%@ include file="../common/admin-header.jspf" %>
<div class="admin-layout">
    <%@ include file="product-admin-menu.jspf" %>
    <main id="admin-content" class="admin-content" tabindex="-1" data-admin-query="product-performance" data-admin-query-route="<%= route %>">
        <h1 class="page-title">상품 성과 분석</h1>
        <p class="error-message" role="alert" data-admin-query-error hidden></p>
        <button class="button secondary" type="button" data-admin-query-retry hidden>다시 시도</button>
        <div data-admin-query-content>
            <% if (performance == null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("productPerformanceError")) %></p>
                <a href="<%= route %>" class="button secondary">기본 조건으로 조회</a>
            <% } else { %>
            <nav class="performance-tabs" aria-label="상품 성과 분석 화면">
                <% for (Tab tab : Tab.values()) { %>
                <a id="performance-tab-<%= tab.key %>" data-admin-query-link href="<%= performance.tabUrl(tab) %>#performance-tab-<%= tab.key %>" <%= tab == performance.tab ? "aria-current=\"page\"" : "" %>><%= tab.label %></a>
                <% } %>
            </nav>
            <form class="admin-panel performance-filters" data-admin-query-form id="performance-query-form" data-admin-explicit-submit data-performance-filters method="get" action="<%= route %>">
                <input type="hidden" name="tab" value="<%= performance.tab.key %>">
                <input type="hidden" name="quick" value="<%= performance.quick.name() %>">
                <input type="hidden" name="rank" value="<%= performance.rank.name() %>">
                <input type="hidden" name="rankingLimit" value="<%= performance.rankingLimit %>">
                <input type="hidden" name="filterStock" value="<%= ProductView.escape(performance.params.get("filterStock")) %>">
                <input type="hidden" name="stockQuantity" value="<%= ProductView.escape(performance.params.get("stockQuantity")) %>">
                <div class="performance-common-fields">
                    <label class="field">시연 데이터
                        <select name="demo"><option value="exclude">제외</option><option value="include" <%= performance.includeDemo ? "selected" : "" %>>포함</option></select>
                    </label>
                    <label class="field">분석 기간
                        <select name="period">
                            <% for (Period period : Period.values()) { %>
                            <option value="<%= period.name() %>" <%= period == performance.period ? "selected" : "" %>><%= period.label %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field" data-performance-date>시작일
                        <input type="date" name="start" value="<%= performance.start %>" max="<%= performance.end %>">
                    </label>
                    <label class="field" data-performance-date>종료일
                        <input type="date" name="end" value="<%= performance.end %>">
                    </label>
                </div>
                <div class="performance-actions">
                    <button type="submit" class="button primary">조회</button>
                    <a class="button secondary" data-admin-query-link href="<%= route %>?tab=<%= performance.tab.key %>">초기화</a>
                    <a class="button secondary" href="<%= performance.csv() %>">CSV 다운로드</a>
                </div>
                <div class="performance-product-fields" <%= performance.tab == Tab.PRODUCTS ? "" : "hidden" %>>
                    <div class="field admin-product-suggest" data-admin-suggestions="<%= context %>/app/admin/products/suggestions"
                         data-suggest-filters="filterCategory filterStatus filterStock stockQuantity" data-suggest-stock-label="온라인 재고">
                        <label for="performance-query">상품 검색</label>
                        <input id="performance-query" name="query" type="search" maxlength="120" autocomplete="off"
                               placeholder="상품명, 품번, 브랜드 또는 ID" value="<%= ProductView.escape(performance.params.get("query")) %>">
                        <input type="hidden" name="selectedProductId" value="<%= ProductView.escape(performance.params.get("selectedProductId")) %>">
                        <div class="admin-product-suggest-panel" data-admin-suggest-panel hidden>
                            <p data-admin-suggest-status role="status" hidden></p>
                            <ul id="performance-suggest-list" role="listbox" aria-label="추천 상품"></ul>
                        </div>
                    </div>
                    <label class="field">카테고리
                        <select name="filterCategory">
                            <option value="">전체 카테고리</option>
                            <% for (Map.Entry<String, String> category : performance.categories.entrySet()) { %>
                            <option value="<%= ProductView.escape(category.getKey()) %>" <%= category.getKey().equals(performance.params.get("filterCategory")) ? "selected" : "" %>><%= ProductView.escape(category.getValue()) %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">판매 상태
                        <select name="filterStatus">
                            <option value="">전체 상태</option>
                            <% for (Map.Entry<String, String> status : performance.statuses.entrySet()) { %>
                            <option value="<%= status.getKey() %>" <%= status.getKey().equals(performance.params.get("filterStatus")) ? "selected" : "" %>><%= status.getValue() %></option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">상품 정렬
                        <select name="performanceSort">
                            <% for (Sort sort : Sort.values()) { %>
                            <option value="<%= sort.name() %>" <%= sort == performance.sort ? "selected" : "" %>><%= sort.label %></option>
                            <% } %>
                        </select>
                    </label>
                    <% if (performance.tab != Tab.PRODUCTS) { %>
                    <input type="hidden" name="pageSize" value="<%= performance.pageSize %>">
                    <% } %>
                    <% if (performance.quick == Quick.LOW_CONVERSION) { %>
                    <label class="field">최소 고유 조회 회원
                        <select name="minimumViews">
                            <% for (int size : new int[]{10, 30, 50, 100}) { %>
                            <option value="<%= size %>" <%= size == performance.minimumViews ? "selected" : "" %>><%= size %>명 이상</option>
                            <% } %>
                        </select>
                    </label>
                    <label class="field">결제 전환율
                        <select name="maxConversion">
                            <% for (int threshold : new int[]{1, 3, 5, 10}) { %>
                            <option value="<%= threshold %>" <%= threshold == performance.maxConversion ? "selected" : "" %>><%= threshold %>% 미만</option>
                            <% } %>
                        </select>
                    </label>
                    <% } else { %>
                    <input type="hidden" name="minimumViews" value="<%= performance.minimumViews %>">
                    <input type="hidden" name="maxConversion" value="<%= performance.maxConversion %>">
                    <% } %>
                </div>
            </form>

            <p class="performance-period">
                <strong><%= performance.start %> ~ <%= performance.end %></strong> (한국 시간) ·
                <%= ProductView.escape(performance.appliedProductFilters) %>
                <% if (performance.firstBehaviorDay != null) { %> · 행동 수집 첫 기록일 <%= performance.firstBehaviorDay %><% } %>
            </p>
            <% if (!performance.hasBehavior) { %>
            <p class="performance-notice"><%= !performance.behaviorReady ? "행동 수집 테이블이 필요합니다."
                : performance.firstBehaviorDay == null ? "아직 수집된 행동 기록이 없습니다." : "행동 수집 시작 전 기간입니다." %></p>
            <% } else if (performance.start.isBefore(performance.firstBehaviorDay)) { %>
            <p class="performance-notice">행동 수치는 첫 기록일부터 제공됩니다.</p>
            <% } %>
            <details class="performance-guide">
                <summary>집계 기준</summary>
                <% if (performance.tab != Tab.CONVERSION) { %>
                <p>판매는 결제 승인일 기준이며, 완료된 전액 취소·환불은 원래 승인일의 수량과 상품 매출에서 제외합니다.</p>
                <% } else { %>
                <p>첫 조회 또는 첫 장바구니 등록부터 7일간 관찰합니다. 조회·장바구니·구매의 발생 순서를 확인하고 회원을 중복 없이 셉니다.</p>
                <p>결제 전환에는 취소·환불된 승인도 포함합니다. 순구매에는 완료된 취소·환불을 제외합니다.
                    주문 지표는 주문 생성일 기준이며, 시연 주문은 앞선 조회 또는 장바구니 기록이 연결된 경우에 집계합니다.</p>
                <% } %>
            </details>
            <% if (performance.tab == Tab.OVERVIEW) { %>
            <%@ include file="product-performance-overview.jspf" %>

            <section class="admin-panel performance-trend" aria-labelledby="performance-trend-title">
                <h2 id="performance-trend-title"><%= performance.hourly ? "시간대별" : "일별" %> 행동·판매 추이</h2>
                <div class="performance-charts">
                    <figure data-performance-chart="revenue,previousRevenue"><figcaption>순상품 매출 (원)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <figure data-performance-chart="units"><figcaption>순판매 수량 (개)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <% if (performance.hasBehavior || performance.hourly) { %>
                    <figure data-performance-chart="views,wish,cart,unwish,uncart"><figcaption>고객 행동 (건)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <% } %>
                </div>
                <% if (performance.hourly) { %>
                <p class="performance-key">행동 기록 수집 전과 아직 지나지 않은 시간은 ‘—’로 표시합니다.</p>
                <% } %>
                <details class="performance-trend-data">
                    <summary><%= performance.hourly ? "시간대별" : "일별" %> 데이터 표 보기</summary>
                    <div class="admin-table-scroll" tabindex="0" role="region" aria-label="<%= performance.hourly ? "시간대별 성과" : "일별 성과" %>">
                        <table class="admin-table" data-performance-days data-hourly="<%= performance.hourly %>" data-trend-period="<%= performance.hourly ? performance.start + " · 00:00–24:00 (한국 시간)" : performance.start + " ~ " + performance.end %>">
                            <thead><tr><th scope="col"><%= performance.hourly ? "시간 (한국 시간)" : "일자" %></th><th scope="col">조회</th><th scope="col">찜 등록</th><th scope="col">찜 취소</th>
                                <th scope="col">장바구니 등록</th><th scope="col">장바구니 삭제</th><th scope="col">순판매 수량</th><th scope="col">순상품 매출</th><th scope="col">직전 기간 일자·매출</th></tr></thead>
                            <tbody>
                                <% for (int dayIndex = 0; dayIndex < performance.trend.size(); dayIndex++) {
                                    TrendBucket day = performance.trend.get(dayIndex);
                                    TrendBucket earlier = performance.previousTrend.get(dayIndex); %>
                                <tr data-date="<%= day.detail %>" data-tick="<%= day.tick %>"
                                    data-state="<%= !day.available ? "future" : day.collecting ? "collecting" : "complete" %>"
                                    <% if (day.covered) { %>data-views="<%= day.views %>" data-wish="<%= day.wishAdds %>"
                                    data-unwish="<%= day.wishRemoves %>" data-cart="<%= day.cartAdds %>" data-uncart="<%= day.cartRemoves %>"<% } %>
                                    <% if (day.available) { %>data-units="<%= day.units %>" data-revenue="<%= day.revenue %>"<% } %>
                                    <% if (earlier.available) { %>data-previous-revenue="<%= earlier.revenue %>"<% } %>
                                    data-previous-date="<%= earlier.detail %>">
                                    <th scope="row"><%= day.label %>
                                        <% if (!day.available) { %><small> · 집계 전</small>
                                        <% } else if (day.collecting) { %><small> · 집계 진행 중</small><% } %>
                                    </th>
                                    <td><%= behavior(day.covered, day.views) %></td><td><%= behavior(day.covered, day.wishAdds) %></td>
                                    <td><%= behavior(day.covered, day.wishRemoves) %></td><td><%= behavior(day.covered, day.cartAdds) %></td>
                                    <td><%= behavior(day.covered, day.cartRemoves) %></td>
                                    <td><%= day.available ? number(day.units) + "개" : "—" %></td>
                                    <td><%= day.available ? number(day.revenue) + "원" : "—" %></td>
                                    <td><%= earlier.available ? earlier.detail + " · " + number(earlier.revenue) + "원" : "—" %></td>
                                </tr>
                                <% } %>
                            </tbody>
                        </table>
                    </div>
                </details>
            </section>

            <% } else if (performance.tab == Tab.CONVERSION) { %>
                <% Metrics flow = performance.conversion; %>
                <%@ include file="product-conversion.jspf" %>
            <% } else if (performance.tab == Tab.POPULAR) { %>
                <%@ include file="product-performance-popular.jspf" %>
            <% } else { %>
            <nav class="performance-quick" aria-label="빠른 분석 필터">
                <% for (Quick item : Quick.values()) { %>
                <a data-admin-query-link href="<%= performance.quickUrl(item) %>" <%= item == performance.quick ? "aria-current=\"page\"" : "" %>><%= item.label %></a>
                <% } %>
            </nav>
            <section id="performance-results" aria-labelledby="performance-results-title">
                <h2 id="performance-results-title">상품별 성과</h2>
                <div class="performance-table-tools">
                <p role="status">상품 <%= number(performance.totalProducts) %>종
                <% if (performance.first > 0) { %> · <%= performance.first %>–<%= performance.last %>번째 표시 <% } %></p>
                <div class="performance-table-options">
                    <label class="field">페이지당 상품
                        <select name="pageSize" form="performance-query-form" data-performance-page-size>
                            <% for (int size : new int[]{20, 50, 100}) { %>
                            <option value="<%= size %>" <%= size == performance.pageSize ? "selected" : "" %>><%= size %>개</option>
                            <% } %>
                        </select>
                    </label>
                <details class="performance-columns">
                    <summary class="button secondary">표시 항목</summary>
                    <div class="performance-actions">
                        <% String[] columnNames = {"조회", "찜", "장바구니", "구매·판매", "결제 전환율", "현재 찜·장바구니", "활성 리뷰", "현재 재고"};
                           for (int col = 0; col < columnNames.length; col++) { %>
                        <label><input type="checkbox" data-performance-column="<%= col + 2 %>" checked> <%= columnNames[col] %></label>
                        <% } %>
                        <button class="button secondary" type="button" data-performance-show-all>전체 표시 · 기본값 복원</button>
                    </div>
                </details>
                </div></div>
                <p class="performance-key">기간 지표는 선택 기간 기준이며, 현재 상태와 활성 리뷰는 조회 시점 기준입니다.</p>
                <div class="admin-table-scroll performance-table-scroll" tabindex="0" role="region" aria-label="상품별 성과 표">
                    <table class="admin-table performance-rows">
                        <thead>
                        <tr class="performance-column-groups"><th scope="col" rowspan="2" <%= performance.sortAttribute(Sort.NAME) %>><a data-admin-query-link href="<%= performance.sortUrl(performance.nextSort(Sort.NAME)) %>">상품 정보</a></th>
                            <th scope="colgroup" colspan="3">고객 행동 · 선택 기간</th>
                            <th scope="colgroup" colspan="2">판매 성과 · 선택 기간</th>
                            <th scope="colgroup" colspan="3">현재 상태 · 조회 시점</th><th scope="col" rowspan="2">관리</th></tr>
                        <tr>
                            <% String[] headings = {"조회", "찜", "장바구니", "구매·판매", "결제 전환율", "찜·장바구니", "활성 리뷰", "온라인 재고"};
                               Sort salesSort = performance.sort == Sort.UNITS || performance.sort == Sort.UNITS_ASC ? Sort.UNITS
                                   : performance.sort == Sort.BUYERS || performance.sort == Sort.BUYERS_ASC ? Sort.BUYERS : Sort.REVENUE;
                               if (salesSort == Sort.UNITS) headings[3] = "순판매 수량";
                               if (salesSort == Sort.BUYERS) headings[3] = "구매 회원";
                               Sort[] sorts = {Sort.VIEW, Sort.WISH, Sort.CART, salesSort, Sort.CONVERSION_HIGH, null, null, Sort.STOCK};
                               for (int col = 0; col < headings.length; col++) {
                                   Sort sort = sorts[col]; %>
                                <th scope="col" data-performance-header="<%= col + 2 %>" <%= sort == null ? "" : performance.sortAttribute(sort) %>>
                                    <% if (sort != null) { %><a data-admin-query-link href="<%= performance.sortUrl(performance.nextSort(sort)) %>"><%= headings[col] %></a>
                                    <% } else { %><%= headings[col] %><% } %>
                                </th>
                            <% } %>
                        </tr></thead>
                        <tbody>
                            <% for (Row item : performance.rows) { %>
                            <tr data-performance-product="<%= ProductView.escape(item.id) %>">
                                <th scope="row">
                                    <div class="performance-product">
                                        <img src="<%= ProductView.image(context, item.imageUrl) %>" alt="" width="46" height="46" loading="lazy">
                                        <span><strong><%= ProductView.escape(item.name) %></strong>
                                            <small>품번 <%= ProductView.escape(item.code) %></small>
                                            <small><%= ProductView.escape(performance.categories.getOrDefault(item.category, item.category)) %></small>
                                            <small class="performance-product-status"><%= ProductView.escape(performance.statuses.getOrDefault(item.status, item.status)) %></small></span>
                                    </div>
                                </th>
                                <td>조회 <%= behavior(performance.hasBehavior, item.views) %><small>고유 회원 <%= behavior(performance.hasBehavior, item.viewers) %></small></td>
                                <td>등록 <%= behavior(performance.hasBehavior, item.wishAdds) %><small>취소 <%= behavior(performance.hasBehavior, item.wishRemoves) %></small></td>
                                <td>등록 <%= behavior(performance.hasBehavior, item.cartAdds) %><small>삭제 <%= behavior(performance.hasBehavior, item.cartRemoves) %></small></td>
                                <td><strong><%= number(item.revenue) %>원</strong><small>순판매 <%= number(item.units) %>개 · 구매 <%= number(item.buyers) %>명</small></td>
                                <td data-product-conversion><strong><%= percentage(item.conversion.viewPaid, item.conversion.viewed) %></strong>
                                    <% if (item.conversion.viewed > 0 && item.conversion.viewed < AdminProductPerformanceController.LOW_VIEW_SAMPLE) { %>
                                        <small class="conversion-small-sample">표본 부족</small>
                                    <% } %>
                                    <small><%= performance.hasBehavior ? fraction(item.conversion.viewPaid, item.conversion.viewed, "명") : "행동 미수집" %></small></td>
                                <td>찜 <%= number(item.currentWish) %>명<small>장바구니 <%= number(item.currentCart) %>명</small></td>
                                <td><%= item.rating == null ? "—" : item.rating %><small><%= number(item.reviews) %>건</small></td>
                                <td><%= number(item.stock) %>개</td>
                                <td><a id="<%= performance.rowAnchor(item.id) %>" class="button secondary" data-performance-detail-link href="<%= performance.detail(item.id) %>">상세</a></td>
                            </tr>
                            <% } if (performance.rows.isEmpty()) { %><tr><td colspan="10">조건에 맞는 상품이 없습니다. <a data-admin-query-link href="<%= route %>?tab=products">필터 초기화</a></td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <% if (performance.totalProducts > 0) {
                   InventoryPagination inventoryPagination = performance.pagination;
                   request.setAttribute("adminPaginationLabel", "상품 성과 페이지");
                   request.setAttribute("adminPaginationBoundaryControls", true); %>
                    <%@ include file="inventory-pagination.jspf" %>
                <% } %>
            </section>
            <% if (!performance.detailId.isEmpty()) { %>
            <dialog class="performance-detail-dialog" aria-labelledby="performance-detail-title" data-performance-initial-dialog open>
                <jsp:include page="product-performance-detail.jsp" />
            </dialog>

            <% } %>

            <% } } %>
        </div>
    </main>
</div>
</body>
</html>
