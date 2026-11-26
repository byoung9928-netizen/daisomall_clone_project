<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReview,daiso.product.dto.ProductReviewPage,daiso.product.dto.ReviewQuestion,daiso.product.dto.ReviewExtras.Answer" %>
<%
    String context = request.getContextPath();
    String base = ProductView.escape(context + "/app/products/reviews");
    ProductDTO product = (ProductDTO) request.getAttribute("reviewProduct");
    ProductReviewPage reviews = (ProductReviewPage) request.getAttribute("reviewPage");
    ProductReview own = reviews.ownReview;
    boolean editing = own != null;
    String id = product.getProductId();
    String token = ProductView.escape(request.getAttribute("reviewToken"));
    String reviewOrderNo = (String) request.getAttribute("reviewOrderNo");
    boolean fromMyPage = Boolean.TRUE.equals(request.getAttribute("reviewFromMyPage"));
    boolean reviewFragment = "editor".equals(request.getParameter("fragment"));
%>
<dialog class="review-dialog" aria-labelledby="review-editor" data-review-dialog <%= reviewFragment ? "" : "open" %>>
    <form method="post" class="review-dialog__form"
          enctype="<%= reviews.extras.available ? "multipart/form-data" : "application/x-www-form-urlencoded" %>"
          action="<%= base + (editing ? "/update" : "/create") %>">
        <header class="review-dialog__header">
            <h2 id="review-editor"><%= editing ? "내 리뷰 수정/삭제" : "리뷰 작성" %></h2>
            <button type="button" class="review-dialog__close" data-review-close hidden aria-label="리뷰 창 닫기">×</button>
        </header>
        <div class="review-dialog__body">
            <input type="hidden" name="id" value="<%= ProductView.escape(id) %>">
            <input type="hidden" name="csrf" value="<%= token %>">
            <% if (reviewOrderNo != null) { %>
                <input type="hidden" name="from" value="order">
                <input type="hidden" name="orderNo" value="<%= ProductView.escape(reviewOrderNo) %>">
            <% } else if (fromMyPage) { %><input type="hidden" name="from" value="my-reviews"><% } %>
            <% if (editing) { %>
                <input type="hidden" name="reviewId" value="<%= own.id %>">
                <input type="hidden" name="version" value="<%= own.version %>">
            <% } %>
            <div class="review-dialog__product">
                <img src="<%= ProductView.image(context, product) %>" alt="" width="68" height="68">
                <span><%= ProductView.escape(product.getName()) %></span>
            </div>
            <fieldset class="review-dialog__section">
                <legend>상품에 얼마나 만족하시나요?</legend>
                <div class="review-dialog__rating" data-review-stars>
                    <% for (int stars = 1; stars <= 5; stars++) { %>
                        <label>
                            <input type="radio" name="rating" value="<%= stars %>"
                                <%= editing && own.rating == stars ? "checked" : "" %> required>
                            <span aria-hidden="true">★</span>
                            <span class="review-dialog__sr"><%= stars %>점</span>
                        </label>
                    <% } %>
                </div>
                <output data-review-rating aria-live="polite"><%= editing ? own.rating + "점" : "별점을 선택해 주세요." %></output>
            </fieldset>
            <% if (reviews.extras.available) { %>
                <input type="hidden" name="reviewExtras" value="1">
                <% for (ReviewQuestion question : reviews.extras.questions) {
                    int selected = 0;
                    if (editing) {
                        for (Answer answer : reviews.extras.answers(own.id)) {
                            if (answer.key.equals(question.key)) selected = answer.value;
                        }
                    }
                %>
                    <fieldset class="review-dialog__section review-dialog__question">
                        <legend><%= ProductView.escape(question.label) %> <small>(선택)</small></legend>
                        <div class="review-dialog__options">
                            <% for (int index = 0; index < question.options.size(); index++) { %>
                                <label>
                                    <input type="radio" name="answer_<%= ProductView.escape(question.key) %>"
                                           value="<%= index + 1 %>" <%= selected == index + 1 ? "checked" : "" %>>
                                    <span><%= ProductView.escape(question.options.get(index)) %></span>
                                </label>
                            <% } %>
                        </div>
                        <button type="button" class="review-dialog__reset" data-review-clear hidden>선택 해제</button>
                    </fieldset>
                <% } %>
            <% } %>
            <div class="review-dialog__section">
                <label for="review-content">어떤 점이 좋았거나 아쉬웠나요?</label>
                <textarea id="review-content" name="content" rows="5" maxlength="2000"
                          placeholder="상품을 사용해 본 경험을 적어 주세요."
                          aria-describedby="review-count" required><%= editing ? ProductView.escape(own.content) : "" %></textarea>
                <p id="review-count" class="review-dialog__count"><span data-review-count>0</span> / 2,000자</p>
            </div>
            <% if (reviews.extras.available) { %>
                <div class="review-dialog__section review-dialog__attachments">
                    <label for="review-photos">사진 첨부 <span data-review-photo-count></span></label>
                    <input id="review-photos" type="file" name="photos" accept="image/jpeg,image/png"
                           aria-describedby="review-photo-guide" multiple>
                    <p id="review-photo-guide">JPG·PNG 최대 10장, 장당 2MB, 한 번에 총 5MB까지 첨부할 수 있어요.</p>
                    <p class="review-dialog__photo-error" data-review-photo-error role="alert"></p>
                    <div class="review-dialog__previews" data-review-previews></div>
                    <% if (editing && !reviews.extras.photos(own.id).isEmpty()) { %>
                        <div class="review-dialog__previews">
                            <% for (long photoId : reviews.extras.photos(own.id)) { %>
                                <label>
                                    <img src="<%= ProductView.escape(context + "/private-product-review-photo?id=" + photoId) %>" alt="첨부된 리뷰 사진">
                                    <input type="checkbox" name="removePhoto" value="<%= photoId %>"> 삭제
                                </label>
                            <% } %>
                        </div>
                    <% } %>
                </div>
            <% } %>
        </div>
        <footer class="review-dialog__footer <%= editing ? "review-dialog__footer--editing" : "" %>">
            <button type="submit" class="button primary <%= editing ? "review-dialog__save" : "" %>"><%= editing ? "수정 저장" : "등록하기" %></button>
            <% if (editing) { %>
                <button type="button" class="button review-dialog__danger" data-review-delete hidden>리뷰 삭제</button>
            <% } %>
        </footer>
    </form>
</dialog>
