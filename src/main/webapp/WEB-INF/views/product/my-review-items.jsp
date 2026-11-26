<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.time.format.DateTimeFormatter,daiso.product.controller.ProductView,daiso.product.dto.MyReviewItem" %>
<%
    String context = request.getContextPath();
    List<MyReviewItem> items = (List<MyReviewItem>) request.getAttribute("myReviewItems");
    if (items == null) return;
    boolean written = Boolean.TRUE.equals(request.getAttribute("myReviewWritten"));
    boolean oldest = Boolean.TRUE.equals(request.getAttribute("myReviewOldest"));
    String type = (String) request.getAttribute("myReviewType");
    Map<String, Integer> counts = (Map<String, Integer>) request.getAttribute("myReviewTypeCounts");
    String route = "/app/order/mypage";
    Map<String, String> base = Map.of("productActivity", "reviews", "reviewTab", written ? "written" : "",
            "reviewSort", oldest ? "oldest" : "", "reviewType", type);
    String previous = (String) request.getAttribute("myReviewPrevious");
    String next = (String) request.getAttribute("myReviewNext");
    DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    Map<String, String> types = Map.of("", "전체", "DELIVERY", "택배배송", "PICKUP", "매장 픽업");
%>
<div data-review-list-result data-page="<%= request.getAttribute("myReviewPage") %>">
    <nav class="my-review-tabs" aria-label="내 리뷰 목록">
        <% for (boolean target : new boolean[]{ false, true }) { %>
        <a data-review-list-link href="<%= ProductView.url(context, route, ProductView.with(base, "reviewTab", target ? "written" : "")) %>"
           <%= written == target ? "aria-current=\"page\"" : "" %>>
            <%= target ? "내가 작성한 리뷰" : "작성 가능한 리뷰" %>
            <span class="my-review-count"><%= request.getAttribute(target ? "myReviewWrittenTotal" : "myReviewAvailableTotal") %></span>
        </a>
        <% } %>
    </nav>
    <p class="my-review-guide"><%= written ? "작성한 리뷰를 수정하거나 삭제할 수 있어요."
            : "택배배송 상품은 배송 완료 후 리뷰를 작성할 수 있어요." %></p>
    <div class="my-review-toolbar">
        <nav class="my-review-types" aria-label="구매 유형">
            <% for (String key : List.of("", "DELIVERY", "PICKUP")) { %>
            <a data-review-list-link href="<%= ProductView.url(context, route, ProductView.with(base, "reviewType", key)) %>"
               <%= type.equals(key) ? "aria-current=\"page\"" : "" %>><%= types.get(key) %>
                <strong><%= key.isEmpty() ? request.getAttribute("myReviewActiveTotal") : counts.getOrDefault(key, 0) %></strong></a>
            <% } %>
        </nav>
        <nav class="my-review-sorts" aria-label="리뷰 정렬">
            <% for (boolean target : new boolean[]{ false, true }) { %>
            <a data-review-list-link href="<%= ProductView.url(context, route, ProductView.with(base, "reviewSort", target ? "oldest" : "")) %>"
               <%= oldest == target ? "aria-current=\"page\"" : "" %>><%= target ? "오래된순" : "최신순" %></a>
            <% } %>
        </nav>
    </div>
    <% if (items.isEmpty()) { %>
        <div class="my-review-empty" role="status">
            <span aria-hidden="true">◇</span>
            <strong><%= type.isEmpty()
                ? (written ? "아직 작성한 리뷰가 없어요." : "작성 가능한 리뷰가 없어요.")
                : types.get(type) + (written ? "으로 구매한 상품에 작성한 리뷰가 없어요." : " 상품에 작성 가능한 리뷰가 없어요.") %></strong>
            <% if (type.isEmpty() && written) { %><p>상품을 사용한 경험을 남겨보세요.</p><% } %>
        </div>
    <% } else { %>
        <ul class="my-review-list">
            <% for (MyReviewItem item : items) {
                String id = item.product.getProductId();
                String productUrl = ProductView.url(context, "/product.jsp", Map.of("id", id));
                String editorUrl = ProductView.url(context, "/app/products/reviews", Map.of("id", id, "from", "my-reviews"));
            %>
            <li class="my-review-card" data-review-item="<%= ProductView.escape(id) %>">
                <div class="my-review-card-top">
                    <div class="my-review-product">
                        <img src="<%= ProductView.image(context, item.product) %>" alt="" width="68" height="68" loading="lazy">
                        <div>
                            <% if (item.visible) { %>
                                <a href="<%= productUrl %>"><strong><%= ProductView.escape(item.product.getName()) %></strong></a>
                            <% } else { %>
                                <strong><%= ProductView.escape(item.product.getName()) %></strong>
                            <% } %>
                            <p><%= types.getOrDefault(item.purchaseType, "구매 유형 미확인") %> · <%= written ? "작성일" : "결제일" %>
                                <time datetime="<%= item.date %>"><%= item.date.format(date) %></time></p>
                        </div>
                    </div>
                    <div class="my-review-actions">
                    <% if (!written) { %>
                        <div class="my-review-stars" role="group" aria-label="별점을 선택하여 리뷰 작성">
                            <% for (int stars = 1; stars <= 5; stars++) { %>
                            <a href="<%= editorUrl %>" data-my-review-open data-review-stars-value="<%= stars %>"
                               aria-label="<%= stars %>점으로 리뷰 작성"><span aria-hidden="true">★</span></a>
                            <% } %>
                        </div>
                        <a class="my-review-edit" href="<%= editorUrl %>" data-my-review-open>리뷰 작성 <span aria-hidden="true">›</span></a>
                    <% } else { %>
                        <a class="my-review-edit" href="<%= editorUrl %>" data-my-review-open><span aria-hidden="true">✎</span> 수정/삭제</a>
                    <% } %>
                    </div>
                </div>
                <% if (written) { %>
                    <div class="my-review-meta">
                        <span class="my-review-rating" aria-label="별점 <%= item.rating %>점">★ <%= item.rating %>점</span>
                        <% if (item.refunded) { %><span class="my-review-badge">환불 구매자</span><% } %>
                        <% if (!"PUBLISHED".equals(item.state)) { %>
                            <span class="my-review-badge"><%= "HIDDEN".equals(item.state) ? "관리자 숨김" : "관리자 삭제" %></span>
                        <% } %>
                    </div>
                    <p class="my-review-body"><%= ProductView.escape(item.content) %></p>
                <% } %>
            </li>
            <% } %>
        </ul>
    <% } %>
    <% if (previous != null || next != null) { %>
        <nav class="product-activity-pagination" aria-label="리뷰 페이지">
            <% if (previous != null) { %><a data-review-list-link href="<%= previous %>">이전</a><% } %>
            <span aria-current="page"><%= request.getAttribute("myReviewPage") %></span>
            <% if (next != null) { %><a data-review-list-link href="<%= next %>">다음</a><% } %>
        </nav>
    <% } %>
</div>
