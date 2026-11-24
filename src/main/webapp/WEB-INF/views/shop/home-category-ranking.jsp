<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.controller.ProductDAO.HomeRankCategory" %>
<%
    String context = request.getContextPath();
    List<HomeRankCategory> categories = (List<HomeRankCategory>) request.getAttribute("homeRankCategories");
    List<ProductDTO> carouselProducts = (List<ProductDTO>) request.getAttribute("homeRankProducts");
    String carouselId = "home-ranking-cards";
    String carouselLabel = "랭킹 상품";
    boolean carouselRanked = true;
    request.setAttribute("browseCatalog", true);
%>
<% if (categories != null) { %>
<div data-ranking-content>
    <% if (categories.isEmpty()) { %>
        <p class="empty-state">표시할 카테고리 랭킹이 없습니다.</p>
    <% } else { %>
        <div class="home-ranking-tabs" role="tablist" aria-label="인기 카테고리">
            <% int tabIndex = 0;
               for (HomeRankCategory category : categories) {
                   boolean active = tabIndex == 0; %>
                <button type="button" id="home-rank-tab-<%= tabIndex++ %>" role="tab" aria-controls="home-ranking-panel"
                        aria-selected="<%= active %>" tabindex="<%= active ? 0 : -1 %>"
                        data-rank-category="<%= ProductView.escape(category.id) %>"><%= ProductView.escape(category.name) %></button>
            <% } %>
        </div>
        <div id="home-ranking-panel" role="tabpanel" aria-labelledby="home-rank-tab-0" tabindex="0">
    <% } %>
<% } %>
<% if (carouselProducts != null) { %>
    <div data-ranking-products>
        <% if (Boolean.TRUE.equals(request.getAttribute("cardPickupLookupError"))) { %>
            <p class="card-pickup-note" role="status">매장픽업 가능 여부를 확인하지 못했습니다.</p>
        <% } %>
        <%@ include file="home-product-carousel.jspf" %>
    </div>
<% } %>
<% if (categories != null) { %>
    <% if (!categories.isEmpty()) { %></div><% } %>
</div>
<% } %>
