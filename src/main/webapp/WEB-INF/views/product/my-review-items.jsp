<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.time.format.DateTimeFormatter,daiso.product.controller.ProductView,daiso.product.dto.MyReviewItem" %>
<%
    String context = request.getContextPath();
    List<MyReviewItem> items = (List<MyReviewItem>) request.getAttribute("myReviewItems");
    boolean written = Boolean.TRUE.equals(request.getAttribute("myReviewWritten"));
    Map<Long, List<Long>> photos = (Map<Long, List<Long>>) request.getAttribute("myReviewPhotos");
    if (photos == null) photos = Map.of();
    String previous = (String) request.getAttribute("myReviewPrevious");
    String next = (String) request.getAttribute("myReviewNext");
    DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy.MM.dd");
%>
<div data-product-activity-result>
    <% if (items != null && items.isEmpty()) { %>
        <p class="product-activity-empty"><%= written ? "작성한 리뷰가 없습니다." : "작성할 수 있는 리뷰가 없습니다." %></p>
    <% } else if (items != null) { %>
        <ul class="my-review-list">
            <% for (MyReviewItem item : items) {
                String id = item.product.getProductId();
                String productUrl = ProductView.url(context, "/product.jsp", Map.of("id", id));
                String editorUrl = ProductView.url(context, "/app/products/reviews", Map.of("id", id, "from", "my-reviews"));
            %>
                <li class="my-review-card">
                    <div class="my-review-card-top">
                        <div class="my-review-product">
                            <img src="<%= ProductView.image(context, item.product) %>" alt="" width="72" height="72" loading="lazy">
                            <div>
                                <% if (item.visible) { %>
                                    <a href="<%= productUrl %>"><strong><%= ProductView.escape(item.product.getName()) %></strong></a>
                                <% } else { %>
                                    <strong><%= ProductView.escape(item.product.getName()) %></strong>
                                <% } %>
                                <p><%= written ? "작성일" : "결제일" %> <time datetime="<%= item.date %>"><%= item.date.format(date) %></time></p>
                            </div>
                        </div>
                        <% if (item.visible) { %>
                            <a class="button <%= written ? "secondary" : "primary" %>" href="<%= editorUrl %>"><%= written ? "수정·삭제" : "리뷰 작성" %></a>
                        <% } else { %>
                            <span class="my-review-badge">상품 페이지 이용 불가</span>
                        <% } %>
                    </div>
                    <% if (written) { %>
                        <div class="my-review-meta">
                            <span class="my-review-rating" aria-label="별점 <%= item.rating %>점">
                                <span aria-hidden="true"><%= "★".repeat(item.rating) %></span> <%= item.rating %>점
                            </span>
                            <% if (!"PUBLISHED".equals(item.state)) { %>
                                <span class="my-review-badge"><%= "HIDDEN".equals(item.state) ? "관리자 숨김" : "관리자 삭제" %></span>
                            <% } %>
                        </div>
                        <p class="my-review-body"><%= ProductView.escape(item.content) %></p>
                        <% List<Long> attached = photos.getOrDefault(item.reviewId, List.of());
                           if (!attached.isEmpty()) { %>
                            <div class="my-review-images">
                                <% int number = 1;
                                   for (long photo : attached) {
                                    String src = ProductView.url(context, "/private-product-review-photo", Map.of("id", Long.toString(photo)));
                                %>
                                    <a href="<%= src %>"><img src="<%= src %>" alt="리뷰 사진 <%= number++ %> 크게 보기"
                                                              width="76" height="76" loading="lazy"></a>
                                <% } %>
                            </div>
                        <% } %>
                    <% } %>
                </li>
            <% } %>
        </ul>
    <% } %>
    <% if (previous != null || next != null) { %>
        <nav class="product-activity-pagination" aria-label="리뷰 목록 페이지">
            <% if (previous != null) { %><a class="button secondary" rel="prev" href="<%= previous %>">이전</a><% } %>
            <% if (next != null) { %><a class="button secondary" rel="next" href="<%= next %>">다음</a><% } %>
        </nav>
    <% } %>
</div>
