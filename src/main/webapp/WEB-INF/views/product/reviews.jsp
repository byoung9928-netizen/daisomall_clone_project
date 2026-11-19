<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReview,daiso.product.dto.ProductReviewPage,daiso.product.dto.ReviewQuestion,daiso.product.dto.ReviewExtras.Answer" %>
<%
    String context = request.getContextPath();
    String route = "/app/products/reviews";
    ProductDTO product = (ProductDTO) request.getAttribute("reviewProduct");
    ProductReviewPage reviews = (ProductReviewPage) request.getAttribute("reviewPage");
    String id = product == null ? (String) request.getAttribute("reviewReturnId") : product.getProductId();
    String reviewHref = id == null ? null : ProductView.url(context, route, Map.of("id", id));
    String token = ProductView.escape(request.getAttribute("reviewToken"));
    String base = ProductView.escape(context + route);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/product-reviews.css">
    <script defer src="${pageContext.request.contextPath}/assets/js/common/product-review-editor.js"></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <main id="main-content" class="container shop-page" tabindex="-1">
        <h1 class="page-title">구매 인증 리뷰 작성·관리</h1>
        <% if (product != null) { %>
            <p><a href="<%= ProductView.url(context, "/product.jsp", Map.of("id", id)) %>#product-reviews"><%= ProductView.escape(product.getName()) %> 상품 리뷰로 돌아가기</a></p>
        <% } else if (reviewHref != null) { %>
            <p><a href="<%= reviewHref %>">리뷰 작성·관리로 돌아가기</a></p>
        <% } else { %>
            <p><a href="<%= ProductView.escape(context + "/list.jsp") %>">상품 목록으로 돌아가기</a></p>
        <% } %>
        <% if (request.getAttribute("reviewError") != null) { %>
            <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("reviewError")) %></p>
        <% } %>
        <% if (reviews != null) { %>
            <% if (reviews.canWrite || reviews.ownReview != null) {
                ProductReview own = reviews.ownReview;
                boolean editing = own != null;
            %>
                <button type="button" class="button primary review-dialog-trigger" data-review-open hidden>
                    <%= editing ? "내 리뷰 수정" : "리뷰 작성" %>
                </button>
                <dialog class="review-dialog" aria-labelledby="review-editor" data-review-dialog open>
                    <form method="post" class="review-dialog__form"
                          enctype="<%= reviews.extras.available ? "multipart/form-data" : "application/x-www-form-urlencoded" %>"
                          action="<%= base + (editing ? "/update" : "/create") %>">
                        <header class="review-dialog__header">
                            <h2 id="review-editor"><%= editing ? "내 리뷰 수정" : "리뷰 작성" %></h2>
                            <button type="button" class="review-dialog__close" data-review-close hidden aria-label="리뷰 창 닫기">×</button>
                        </header>
                        <div class="review-dialog__body">
                            <input type="hidden" name="id" value="<%= ProductView.escape(id) %>">
                            <input type="hidden" name="csrf" value="<%= token %>">
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
                        <footer class="review-dialog__footer">
                            <button type="submit" class="button primary"><%= editing ? "수정 저장" : "등록하기" %></button>
                        </footer>
                    </form>
                </dialog>
                <% if (editing) { %>
                    <details class="review-dialog-delete">
                        <summary>내 리뷰 삭제</summary>
                        <p>삭제한 리뷰는 목록과 별점 집계에서 제외되며 첨부 사진도 삭제됩니다.</p>
                        <form method="post" action="<%= base %>/delete">
                            <input type="hidden" name="id" value="<%= ProductView.escape(id) %>">
                            <input type="hidden" name="csrf" value="<%= token %>">
                            <input type="hidden" name="reviewId" value="<%= own.id %>">
                            <input type="hidden" name="version" value="<%= own.version %>">
                            <button type="submit" class="button secondary">리뷰 삭제 확인</button>
                        </form>
                    </details>
                <% } %>
            <% } else if (Boolean.TRUE.equals(request.getAttribute("reviewSignedIn"))) { %>
                <p>취소되지 않은 결제 완료 구매가 있어야 작성할 수 있습니다.</p>
            <% } else { %>
                <p><a href="<%= ProductView.escape(context + "/app/member/login") %>">로그인</a>하고 구매한 상품의 리뷰를 작성할 수 있습니다.</p>
            <% } %>
        <% } %>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
