<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductConversionDAO.Metrics,java.util.Locale,java.util.Map,daiso.product.controller.ProductView,daiso.product.controller.ProductPerformanceDAO.Row,daiso.product.controller.ProductPerformanceDAO.Day,daiso.product.controller.AdminProductPerformanceController,daiso.product.controller.AdminProductPerformanceController.Page,daiso.product.controller.AdminProductPerformanceController.Period,daiso.product.controller.AdminProductPerformanceController.Sort,daiso.product.controller.AdminProductPerformanceController.Tab" %>
<%!
    private static String number(long value) { return String.format(Locale.KOREA, "%,d", value); }
    private static String behavior(boolean available, long value) { return available ? number(value) : "—"; }
    private static String percentage(long numerator, long denominator) {
        return denominator == 0 ? "분석 불가" : Metrics.percent(numerator, denominator).toPlainString() + "%";
    }
    private static String fraction(long numerator, long denominator, String unit) {
        return denominator == 0 ? "분모 없음" : number(numerator) + unit + " / " + number(denominator) + unit;
    }
    private static String average(long total, int peers) {
        return peers == 0 ? "—" : String.format(Locale.KOREA, "%,.1f", (double) total / peers);
    }
%>
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
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-product-performance.css?v=performance-tabs-1">
    <script src="<%= context %>/assets/js/common/admin-product-workspace.js?v=batch-selection-1" defer></script>
    <script src="<%= context %>/assets/js/common/admin-product-suggestions.js?v=inventory-suggest-1" defer></script>
    <script src="<%= context %>/assets/js/common/admin-product-performance.js?v=performance-tabs-1" defer></script>
</head>
<body class="admin-body product-admin-ux">
<a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
<%@ include file="../common/admin-header.jspf" %>
<div class="admin-layout">
    <%@ include file="product-admin-menu.jspf" %>
    <main id="admin-content" class="admin-content" tabindex="-1" data-admin-query="product-performance" data-admin-query-route="<%= route %>">
        <h1 class="page-title">상품 성과 분석</h1>
        <p class="error-message" role="alert" data-admin-query-error hidden></p>
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
            <form class="admin-panel performance-filters" data-admin-query-form data-performance-filters method="get" action="<%= route %>">
                <input type="hidden" name="tab" value="<%= performance.tab.key %>">
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
                <% if (performance.tab != Tab.PRODUCTS) { %>
                <p class="performance-scope"><strong>적용된 상품 조건</strong> <%= ProductView.escape(performance.appliedProductFilters) %></p>
                <% } %>
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
                </div>
                <div class="performance-actions">
                    <button type="submit" class="button primary">조회</button>
                    <a class="button secondary" data-admin-query-link href="<%= route %>?tab=<%= performance.tab.key %>">초기화</a>
                    <a class="button secondary" href="<%= performance.csv() %>">CSV 다운로드</a>
                </div>
            </form>

            <% if (performance.tab != Tab.PRODUCTS) { %>
            <p class="performance-period">
                <strong><%= performance.start %> ~ <%= performance.end %></strong> (한국 시간)
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
                <% if (performance.tab == Tab.OVERVIEW) { %>
                <p>판매는 결제 승인일 기준이며, 완료된 전액 취소·환불은 원래 승인일의 수량과 상품 매출에서 제외합니다.</p>
                <% } else { %>
                <p>첫 조회 또는 첫 장바구니 등록부터 7일간 관찰합니다. 조회·장바구니·구매의 발생 순서를 확인하고 회원을 중복 없이 셉니다.</p>
                <p>결제 전환에는 취소·환불된 승인도 포함합니다. 순구매에는 완료된 취소·환불을 제외합니다.
                    주문 지표는 주문 생성일 기준이며, 시연 주문은 앞선 조회 또는 장바구니 기록이 연결된 경우에 집계합니다.</p>
                <% } %>
            </details>
            <% } %>
            <% if (performance.tab == Tab.OVERVIEW) { %>
            <h2 class="performance-group-title">기간 행동</h2>
            <div class="store-metrics performance-metrics" aria-label="기간 성과 요약">
                <div><span>상품 조회</span><strong data-metric="views"><%= behavior(performance.hasBehavior, performance.summary.views) %></strong></div>
                <div><span>찜 등록</span><strong data-metric="wish"><%= behavior(performance.hasBehavior, performance.summary.wishAdds) %></strong></div>
                <div><span>장바구니 등록</span><strong data-metric="cart"><%= behavior(performance.hasBehavior, performance.summary.cartAdds) %></strong></div>
            </div>
            <h2 class="performance-group-title">기간 판매</h2>
            <div class="store-metrics performance-metrics" aria-label="기간 판매 요약">
                <div><span>순판매 수량</span><strong data-metric="units"><%= number(performance.summary.units) %>개</strong></div>
                <div><span>순상품 매출</span><strong data-metric="revenue"><%= number(performance.summary.revenue) %>원</strong></div>
                <div><span>미판매 상품</span><strong data-metric="unsold"><%= number(performance.summary.unsold) %>종</strong></div>
            </div>

            <section class="admin-panel performance-trend" aria-labelledby="performance-trend-title">
                <h2 id="performance-trend-title">일별 행동·판매 추이</h2>
                <div class="performance-charts">
                    <% if (performance.hasBehavior) { %>
                    <figure data-performance-chart="views"><figcaption>상품 조회 (건)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <figure data-performance-chart="wish,unwish"><figcaption>찜 등록·취소 (건)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <figure data-performance-chart="cart,uncart"><figcaption>장바구니 등록·삭제 (건)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <% } %>
                    <figure data-performance-chart="units"><figcaption>순판매 수량 (개)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                    <figure data-performance-chart="revenue"><figcaption>순상품 매출 (원)</figcaption><canvas aria-hidden="true"></canvas><small data-chart-range></small></figure>
                </div>
                <details class="performance-trend-data">
                    <summary>일별 데이터 표 보기</summary>
                    <div class="admin-table-scroll" tabindex="0" role="region" aria-label="일별 성과">
                        <table class="admin-table" data-performance-days>
                            <thead><tr><th scope="col">일자</th><th scope="col">조회</th><th scope="col">찜 등록</th><th scope="col">찜 취소</th>
                                <th scope="col">장바구니 등록</th><th scope="col">장바구니 삭제</th><th scope="col">순판매 수량</th><th scope="col">순상품 매출</th></tr></thead>
                            <tbody>
                                <% for (Day day : performance.trend) {
                                    boolean covered = performance.firstBehaviorDay != null && !day.date.isBefore(performance.firstBehaviorDay); %>
                                <tr data-date="<%= day.date %>"
                                    <% if (covered) { %>data-views="<%= day.views %>" data-wish="<%= day.wishAdds %>" data-unwish="<%= day.wishRemoves %>"
                                    data-cart="<%= day.cartAdds %>" data-uncart="<%= day.cartRemoves %>"<% } %>
                                    data-units="<%= day.units %>" data-revenue="<%= day.revenue %>">
                                    <th scope="row"><%= day.date %></th><td><%= behavior(covered, day.views) %></td>
                                    <td><%= behavior(covered, day.wishAdds) %></td><td><%= behavior(covered, day.wishRemoves) %></td>
                                    <td><%= behavior(covered, day.cartAdds) %></td><td><%= behavior(covered, day.cartRemoves) %></td>
                                    <td><%= number(day.units) %>개</td><td><%= number(day.revenue) %>원</td>
                                </tr>
                                <% } %>
                            </tbody>
                        </table>
                    </div>
                </details>
            </section>

            <% } else if (performance.tab == Tab.CONVERSION) { %>
                <%@ include file="product-conversion.jspf" %>
            <% } else { %>
            <section id="performance-results" aria-labelledby="performance-results-title">
                <h2 id="performance-results-title">상품별 성과</h2>
                <p role="status">상품 <%= number(performance.summary.products) %>종
                <% if (performance.first > 0) { %> · <%= performance.first %>–<%= performance.last %>번째 표시 <% } %></p>
                <p class="performance-key">기간 지표는 선택 기간 기준이며, 현재 상태와 활성 리뷰는 조회 시점 기준입니다. —는 수집 기록이 없는 기간을 표시합니다.</p>
                <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품별 성과 표">
                    <table class="admin-table performance-rows">
                        <thead><tr><th scope="col">상품</th><th scope="col">기간 조회</th><th scope="col">기간 찜</th><th scope="col">기간 장바구니</th>
                            <th scope="col">기간 구매·판매</th><th scope="col">조회 대비 결제 전환율</th><th scope="col">현재 찜·장바구니</th><th scope="col">활성 리뷰</th>
                            <th scope="col">현재 온라인 재고</th><th scope="col">관리</th></tr></thead>
                        <tbody>
                            <% for (Row item : performance.rows) { %>
                            <tr data-performance-product="<%= ProductView.escape(item.id) %>">
                                <th scope="row">
                                    <div class="performance-product">
                                        <img src="<%= ProductView.image(context, item.imageUrl) %>" alt="" width="40" height="40" loading="lazy">
                                        <span><strong><%= ProductView.escape(item.name) %></strong>
                                            <small>품번 <%= ProductView.escape(item.code) %></small>
                                            <small><%= ProductView.escape(performance.categories.getOrDefault(item.category, item.category)) %> ·
                                                <%= ProductView.escape(performance.statuses.getOrDefault(item.status, item.status)) %></small></span>
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
                                    <small><%= fraction(item.conversion.viewPaid, item.conversion.viewed, "명") %></small></td>
                                <td>찜 <%= number(item.currentWish) %>명<small>장바구니 <%= number(item.currentCart) %>명</small></td>
                                <td><%= item.rating == null ? "—" : item.rating %><small><%= number(item.reviews) %>건</small></td>
                                <td><%= number(item.stock) %>개</td>
                                <td><a id="<%= performance.rowAnchor(item.id) %>" class="button secondary" data-admin-query-link href="<%= performance.detail(item.id) %>">상세</a></td>
                            </tr>
                            <% } if (performance.rows.isEmpty()) { %><tr><td colspan="10">조건에 맞는 상품이 없습니다.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <% if (performance.pagination.pages > 1) { %>
                    <nav class="admin-product-pagination" aria-label="상품 성과 페이지">
                        <% if (performance.pagination.previous != null) { %><a href="<%= performance.pagination.previous %>">이전</a><% } %>
                        <% for (Map.Entry<Integer, String> link : performance.pagination.links.entrySet()) {
                            if (link.getKey() == performance.pagination.page) { %>
                            <strong aria-current="page"><%= link.getKey() %></strong>
                            <% } else { %><a href="<%= link.getValue() %>"><%= link.getKey() %></a><% } } %>
                        <% if (performance.pagination.next != null) { %><a href="<%= performance.pagination.next %>">다음</a><% } %>
                    </nav>
                <% } %>
            </section>
            <% if (!performance.detailId.isEmpty()) { %>
            <section id="product-performance-detail" class="admin-panel performance-detail" aria-labelledby="performance-detail-title">
                <% if (performance.selected == null) { %>
                    <h2 id="performance-detail-title">상품 상세 분석</h2>
                    <p>현재 조회 조건에서 해당 상품을 찾을 수 없습니다.</p>
                <% } else {
                    Row item = performance.selected;
                    int peers = performance.peers.products; %>
                    <h2 id="performance-detail-title"><%= ProductView.escape(item.name) %> 상세 분석</h2>
                    <p>품번 <%= ProductView.escape(item.code) %> · 동일 카테고리의 다른 상품 <%= peers %>종과 비교 (미판매 포함)</p>
                    <% if (performance.hasBehavior && item.views < AdminProductPerformanceController.LOW_VIEW_SAMPLE) { %>
                        <p class="performance-notice">이 상품의 조회 표본이 적습니다 (10건 미만).</p>
                    <% } %>
                    <% if (peers == 0) { %><p>비교할 동일 카테고리 상품이 없습니다.</p><% } %>
                    <div class="admin-table-scroll" tabindex="0" role="region" aria-label="비교 지표">
                    <table class="admin-table performance-comparison">
                        <thead><tr><th scope="col">기간 지표</th><th scope="col">이 상품</th><th scope="col">카테고리 평균</th></tr></thead>
                        <tbody>
                            <tr><th scope="row">조회</th><td><%= behavior(performance.hasBehavior, item.views) %></td><td><%= performance.hasBehavior ? average(performance.peers.views, peers) : "—" %></td></tr>
                            <tr><th scope="row">찜 등록</th><td><%= behavior(performance.hasBehavior, item.wishAdds) %></td><td><%= performance.hasBehavior ? average(performance.peers.wishAdds, peers) : "—" %></td></tr>
                            <tr><th scope="row">장바구니 등록</th><td><%= behavior(performance.hasBehavior, item.cartAdds) %></td><td><%= performance.hasBehavior ? average(performance.peers.cartAdds, peers) : "—" %></td></tr>
                            <tr><th scope="row">순판매 수량</th><td><%= number(item.units) %></td><td><%= average(performance.peers.units, peers) %></td></tr>
                            <tr><th scope="row">순상품 매출 (원)</th><td><%= number(item.revenue) %></td><td><%= average(performance.peers.revenue, peers) %></td></tr>
                        </tbody>
                    </table>
                    </div>
                    <% Metrics itemFlow = item.conversion;
                       Metrics peerFlow = performance.peerConversion; %>
                    <h3>구매 행동 및 카테고리 비교</h3>
                    <p>조회 대비 결제 전환율 <strong><%= percentage(itemFlow.viewPaid, itemFlow.viewed) %></strong>
                        · 카테고리 가중 평균 <strong><%= percentage(peerFlow.viewPaid, peerFlow.viewed) %></strong>
                        <small>(이 상품 제외 · 조회 회원 표본 수로 가중)</small></p>
                    <dl class="performance-detail-stats">
                        <div><dt>조회 대비 순구매율</dt><dd><%= percentage(itemFlow.viewNet, itemFlow.viewed) %></dd></div>
                        <div><dt>조회 후 찜·장바구니 등록률</dt><dd><%= percentage(itemFlow.wished, itemFlow.viewed) %> · <%= percentage(itemFlow.cartAfterView, itemFlow.viewed) %></dd></div>
                        <div><dt>직접·장바구니 경유 결제</dt><dd><%= itemFlow.viewed == 0 ? "—" : number(itemFlow.direct) + "명 · " + number(itemFlow.viaCart) + "명" %></dd></div>
                        <div><dt>장바구니 대비 결제 전환율</dt><dd><%= percentage(itemFlow.cartPaid, itemFlow.cart) %></dd></div>
                        <div><dt>장바구니 최종 미구매율</dt><dd><%= percentage(itemFlow.lostCart, itemFlow.matureCart) %></dd></div>
                        <div><dt>주문 대비 결제 완료·실패율</dt><dd><%= percentage(itemFlow.paid, itemFlow.orders) %> · <%= percentage(itemFlow.failed, itemFlow.orders) %></dd></div>
                        <div><dt>승인 후 취소·환불율</dt><dd><%= percentage(itemFlow.cancelled, itemFlow.paid) %> · <%= percentage(itemFlow.refunded, itemFlow.paid) %></dd></div>
                    </dl>
                    <dl class="performance-detail-stats">
                        <div><dt>기간 고유 조회 회원</dt><dd><%= behavior(performance.hasBehavior, item.viewers) %></dd></div>
                        <div><dt>기간 찜 취소</dt><dd><%= behavior(performance.hasBehavior, item.wishRemoves) %></dd></div>
                        <div><dt>기간 장바구니 삭제</dt><dd><%= behavior(performance.hasBehavior, item.cartRemoves) %></dd></div>
                        <div><dt>기간 유효 구매 회원</dt><dd><%= number(item.buyers) %>명</dd></div>
                        <div><dt>현재 찜 회원</dt><dd><%= number(item.currentWish) %>명</dd></div>
                        <div><dt>현재 장바구니 회원</dt><dd><%= number(item.currentCart) %>명</dd></div>
                        <div><dt>활성 리뷰 평점·건수</dt><dd><%= item.rating == null ? "—" : item.rating %> · <%= number(item.reviews) %>건</dd></div>
                        <div><dt>현재 온라인 재고</dt><dd><%= number(item.stock) %>개</dd></div>
                    </dl>
                    <nav class="performance-actions" aria-label="관련 관리자 화면">
                        <a class="button secondary" href="<%= ProductView.url(context, "/app/admin/products", Map.of("edit", item.id)) %>#product-editor">상품 관리</a>
                        <a class="button secondary" href="<%= ProductView.url(context, "/app/admin/inventory", Map.of("selectedProductId", item.id, "q", item.code)) %>">온라인 재고</a>
                        <a class="button secondary" href="<%= ProductView.url(context, "/app/admin/products/reviews", Map.of("query", item.code)) %>">리뷰 관리</a>
                    </nav>
                <% } %>
                <a class="button secondary" data-admin-query-link href="<%= performance.detail("") %>">상세 닫기</a>
            </section>
            <% } %>

            <% } } %>
        </div>
    </main>
</div>
</body>
</html>
