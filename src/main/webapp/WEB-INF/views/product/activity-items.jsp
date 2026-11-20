<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.time.LocalDate,java.util.LinkedHashMap,java.util.ArrayList,java.util.List,java.util.Map,java.util.Set,daiso.product.controller.ProductView,daiso.product.dto.ProductActivityItem" %>
<%
    List<ProductActivityItem> items = (List<ProductActivityItem>) request.getAttribute("activityItems");
    String kind = (String) request.getAttribute("activityKind");
    String context = request.getContextPath();
    boolean summary = Boolean.TRUE.equals(request.getAttribute("activitySummary"));
    boolean recent = "recent".equals(kind);
    boolean wishlist = "wishlist".equals(kind);
    boolean selectable = !summary && (recent || wishlist);
    boolean wishlistReady = request.getAttribute("wishlistError") == null;
    Set<String> selected = (Set<String>) request.getAttribute("wishlistSelected");
    Map<String, LocalDate> recentDates = (Map<String, LocalDate>) request.getAttribute("activityRecentDates");
    String previous = (String) request.getAttribute("activityPrevious");
    String next = (String) request.getAttribute("activityNext");
    Map<LocalDate, List<ProductActivityItem>> groups = new LinkedHashMap<>();
    if (items != null) {
        for (ProductActivityItem item : items) {
            LocalDate date = !summary && recent && recentDates != null
                    ? recentDates.get(item.product.getProductId()) : null;
            groups.computeIfAbsent(date, ignored -> new ArrayList<>()).add(item);
        }
    }
%>
<div data-product-activity-result>
    <% if (items != null && items.isEmpty()) {
        String empty = wishlist ? "찜한 상품이 없습니다."
                : recent ? "이번 로그인에서 본 상품이 없습니다." : "결제 완료된 구매 상품이 없습니다.";
    %>
        <p class="product-activity-empty"><%= empty %></p>
    <% } else if (items != null) { %>
        <div class="<%= summary ? "" : "product-history-results" %>"
             <% if (!summary) { %> data-wishlist-config
             data-base-url="<%= ProductView.escape(context + "/app/products/wishlist") %>"
             data-login-url="<%= ProductView.escape(context + "/app/member/login") %>"
             data-logged-in="true" data-wishlist-page="<%= wishlist %>"
             data-csrf="<%= ProductView.escape(request.getAttribute("wishlistCsrf")) %>"
             data-history-remove="<%= ProductView.escape(context + "/app/products/my-activity/remove") %>"
             data-history-kind="<%= ProductView.escape(kind) %>"
             <% } %>>
            <% if (selectable) { %>
                <div class="product-history-toolbar">
                    <label><input type="checkbox" data-history-select-all <%= wishlist && !wishlistReady ? "disabled" : "" %>> 전체 선택</label>
                    <div><output data-history-count aria-live="polite">0개 선택</output>
                        <button class="button secondary" type="button" data-history-remove-selected disabled>선택 삭제</button>
                    </div>
                </div>
            <% } %>
            <% if (!summary) { %>
                <p class="product-history-feedback" data-wishlist-feedback role="status"><%= ProductView.escape(request.getAttribute("wishlistError")) %></p>
            <% } %>
            <% for (Map.Entry<LocalDate, List<ProductActivityItem>> group : groups.entrySet()) { %>
                <% if (group.getKey() != null) { %>
                    <h3 class="product-history-date"><time datetime="<%= group.getKey() %>"><%= group.getKey() %></time></h3>
                <% } %>
                <ul class="product-activity-grid <%= summary && wishlist ? "product-activity-grid--wishlist" : "" %>">
                    <% for (ProductActivityItem entry : group.getValue()) {
                        String id = entry.product.getProductId();
                        String name = ProductView.escape(entry.product.getName());
                        String url = ProductView.url(context, "/product.jsp", Map.of("id", id));
                        boolean wished = selected != null && selected.contains(id);
                    %>
                        <li>
                            <% if (selectable) { %>
                                <label class="product-history-select"><input type="checkbox" data-history-select
                                    value="<%= ProductView.escape(id) %>" <%= wishlist && !wishlistReady ? "disabled" : "" %>>
                                    <span class="visually-hidden"><%= name %> 선택</span>
                                </label>
                            <% } %>
                            <div class="product-history-card">
                                <a class="product-activity-card" href="<%= url %>">
                                    <img src="<%= ProductView.image(context, entry.product) %>" alt="" loading="lazy" decoding="async">
                                    <strong><%= ProductView.money(entry.product.getPrice()) %></strong>
                                    <span><%= name %></span>
                                    <small><%= ProductView.status(entry.product) %><%= entry.purchases > 0 ? " · 구매 " + entry.purchases + "회" : "" %></small>
                                </a>
                                <% if (!summary) { %>
                                    <button type="button" class="wishlist-button<%= wished ? " is-selected" : "" %>"
                                            data-wishlist-product="<%= ProductView.escape(id) %>" data-wishlist-name="<%= name %>"
                                            aria-pressed="<%= wished %>" aria-label="<%= name %> <%= wished ? "찜 해제" : "찜 등록" %>"
                                            <%= wishlistReady ? "" : "disabled" %>>
                                        <span aria-hidden="true" class="product-history-heart">♥</span>
                                        <span class="visually-hidden" data-wishlist-label><%= wished ? "찜 해제" : "찜" %></span>
                                    </button>
                                    <% if (entry.product.getReviewCount() > 0) { %>
                                        <a class="product-history-rating" href="<%= url %>#product-reviews">
                                            ★ <%= String.format(java.util.Locale.ROOT, "%.1f", entry.product.getRating()) %>
                                            <span>리뷰 <%= entry.product.getReviewCount() %>개</span>
                                        </a>
                                    <% } %>
                                <% } %>
                            </div>
                        </li>
                    <% } %>
                </ul>
            <% } %>
        </div>
    <% } %>
    <% if (previous != null || next != null) { %>
        <nav class="product-activity-pagination" aria-label="상품 활동 페이지">
            <% if (previous != null) { %><a class="button secondary" rel="prev" href="<%= previous %>">이전</a><% } %>
            <% if (next != null) { %><a class="button secondary" rel="next" href="<%= next %>">다음</a><% } %>
        </nav>
    <% } %>
</div>
