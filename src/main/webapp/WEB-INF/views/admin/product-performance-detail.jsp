<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.AdminProductPerformanceController,daiso.product.controller.AdminProductPerformanceController.Page,daiso.product.controller.ProductPerformanceDAO.Row,daiso.product.controller.ProductView" %>
<%@ include file="product-performance-format.jspf" %>
<%
    Page performance = (Page) request.getAttribute("productPerformance");
    Row item = performance.selected;
    String context = request.getContextPath();
%>
<%!
    private static String comparison(long current, long total, int count, boolean collected) {
        if (!collected || count == 0) return "비교 불가";
        double average = (double) total / count;
        if (Double.compare(current, average) == 0) return "평균과 동일";
        if (total == 0) return "평균 0 · 비율 비교 불가";
        if (total < 0) return String.format(Locale.KOREA, "평균과 %+.2f 차이", current - average);
        return String.format(Locale.KOREA, "평균 대비 %+.2f%%", (current - average) / average * 100);
    }
%>
<div data-performance-detail-content class="performance-detail-content">
    <% if (item == null) { %>
    <header class="performance-detail-header"><h2 id="performance-detail-title">상품 상세 분석</h2></header>
    <div class="performance-detail-body"><p>현재 조건에서 상품을 찾을 수 없습니다.</p></div>
    <footer class="performance-detail-footer"><a class="button secondary" data-performance-detail-close href="<%= performance.detail("") %>">닫기</a></footer>
    <% } else {
       int peers = performance.peers.products;
       Metrics flow = item.conversion;
       Metrics peer = performance.peerConversion;
       String categoryPath = performance.categories.getOrDefault(item.category, item.category);
    %>
    <header class="performance-detail-header">
        <img alt="" width="58" height="58" src="<%= ProductView.image(context, item.imageUrl) %>">
        <div><h2 id="performance-detail-title"><%= ProductView.escape(item.name) %></h2>
            <p>품번 <%= ProductView.escape(item.code) %> · <%= ProductView.escape(categoryPath) %></p>
            <p><%= performance.start %> ~ <%= performance.end %> (한국 시간) · 시연 데이터 <%= performance.includeDemo ? "포함" : "제외" %></p></div>
        <a class="button secondary performance-detail-x" data-performance-detail-close href="<%= performance.detail("") %>" aria-label="상품 상세 닫기">×</a>
    </header>
    <div class="performance-detail-body">
        <nav class="performance-tabs performance-detail-tabs" aria-label="상품 상세 분석">
            <a href="#performance-detail-results" data-detail-tab>성과 및 비교</a>
            <a href="#performance-detail-conversion" data-detail-tab>구매 전환</a>
            <a href="#performance-detail-current" data-detail-tab>현재 상태</a>
        </nav>
        <section id="performance-detail-results" data-detail-section>
            <h3>성과 및 비교</h3>
            <dl class="performance-detail-stats">
                <div><dt>순상품 매출</dt><dd><%= number(item.revenue) %>원</dd></div>
                <div><dt>순판매 수량</dt><dd><%= number(item.units) %>개</dd></div>
                <div><dt>고유 구매 회원</dt><dd><%= number(item.buyers) %>명</dd></div>
                <div><dt>상품 조회</dt><dd><%= behavior(performance.hasBehavior, item.views) %></dd></div>
                <div><dt>고유 조회 회원</dt><dd><%= behavior(performance.hasBehavior, item.viewers) %></dd></div>
                <div><dt>찜 등록</dt><dd><%= behavior(performance.hasBehavior, item.wishAdds) %></dd></div>
                <div><dt>찜 취소</dt><dd><%= behavior(performance.hasBehavior, item.wishRemoves) %></dd></div>
                <div><dt>장바구니 등록</dt><dd><%= behavior(performance.hasBehavior, item.cartAdds) %></dd></div>
                <div><dt>장바구니 삭제</dt><dd><%= behavior(performance.hasBehavior, item.cartRemoves) %></dd></div>
            </dl>
            <% if (!performance.hasBehavior) { %><p class="performance-notice">행동 미수집</p>
            <% } else if (item.views < AdminProductPerformanceController.LOW_VIEW_SAMPLE) { %><p class="performance-notice">조회 표본 부족 · 조회 10건 미만</p><% } %>
            <h3>동일 카테고리 평균</h3>
            <p><%= ProductView.escape(categoryPath) %> · <%= number(peers) %>종 (이 상품 제외, 미판매 포함)</p>
            <div class="performance-peer-grid">
            <% String[] labels = {"순상품 매출 (원)", "순판매 수량 (개)", "조회 (건)", "찜 등록 (건)", "장바구니 등록 (건)"};
               long[] values = {item.revenue, item.units, item.views, item.wishAdds, item.cartAdds};
               long[] sums = {performance.peers.revenue, performance.peers.units, performance.peers.views,
                   performance.peers.wishAdds, performance.peers.cartAdds};
               for (int i = 0; i < labels.length; i++) {
                   boolean collected = i < 2 || performance.hasBehavior;
                   double mean = peers == 0 ? 0 : (double) sums[i] / peers;
                   double maximum = Math.max(mean, values[i]);
            %>
                <div><h4><%= labels[i] %></h4>
                    <p>이 상품 <%= behavior(collected, values[i]) %> · 평균 <%= collected ? average(sums[i], peers) : "—" %></p>
                    <% if (collected && peers > 0 && maximum > 0 && mean >= 0 && values[i] >= 0) { %>
                    <meter min="0" max="<%= maximum %>" value="<%= values[i] %>" aria-label="이 상품"></meter>
                    <meter class="performance-paid" min="0" max="<%= maximum %>" value="<%= mean %>" aria-label="카테고리 평균"></meter>
                    <% } %>
                    <small><%= comparison(values[i], sums[i], peers, collected) %></small>
                </div>
            <% } %>
            </div>
        </section>
        <section id="performance-detail-conversion" data-detail-section>
            <p>카테고리 가중 평균 전환율 <strong><%= percentage(peer.viewPaid, peer.viewed) %></strong> ·
                <%= fraction(peer.viewPaid, peer.viewed, "명") %> (이 상품 제외)</p>
            <% if (flow.viewed > 0 && peer.viewed > 0) { %><p>카테고리 평균과의 차이
                <%= String.format(Locale.KOREA, "%+.2f%%p", 100d * flow.viewPaid / flow.viewed - 100d * peer.viewPaid / peer.viewed) %></p><% } %>
            <%@ include file="product-conversion.jspf" %>
        </section>
        <section id="performance-detail-current" data-detail-section>
            <h3>현재 상태</h3>
            <p>조회 시점 기준</p>
            <dl class="performance-detail-stats">
                <div><dt>온라인 재고</dt><dd><%= number(item.stock) %>개</dd></div>
                <div><dt>찜 회원</dt><dd><%= number(item.currentWish) %>명</dd></div>
                <div><dt>장바구니 회원</dt><dd><%= number(item.currentCart) %>명</dd></div>
                <div><dt>활성 리뷰</dt><dd><%= number(item.reviews) %>건</dd></div>
                <div><dt>평균 별점</dt><dd><%= item.rating == null ? "—" : item.rating %></dd></div>
            </dl>
        </section>
    </div>
    <footer class="performance-detail-footer">
        <a class="button secondary" href="<%= ProductView.url(context, "/app/admin/products", java.util.Map.of("edit", item.id)) %>#product-editor">상품 관리</a>
        <a class="button secondary" href="<%= ProductView.url(context, "/app/admin/inventory", java.util.Map.of("selectedProductId", item.id, "q", item.code)) %>">온라인 재고</a>
        <a class="button secondary" href="<%= ProductView.url(context, "/app/admin/products/reviews", java.util.Map.of("query", item.code)) %>">리뷰 관리</a>
        <a class="button primary" data-performance-detail-close href="<%= performance.detail("") %>">닫기</a>
    </footer>
    <% } %>
</div>
