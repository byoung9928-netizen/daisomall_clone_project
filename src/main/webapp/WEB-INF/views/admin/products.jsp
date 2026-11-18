<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductView" %>
<%
    request.setAttribute("adminSection", "products");
    String blocked = (String) request.getAttribute("adminProductBlocked");
    String csrf = (String) request.getAttribute("adminProductCsrf");
    Map<String, String> productForm = (Map<String, String>) request.getAttribute("productForm");
    Map<String, String> categories = (Map<String, String>) request.getAttribute("productCategories");
    Map<String, String> statuses = (Map<String, String>) request.getAttribute("productStatuses");
    Map<ProductDTO, Map<String, String>> productRows =
            (Map<ProductDTO, Map<String, String>>) request.getAttribute("productRows");
    ProductDTO deletion = (ProductDTO) request.getAttribute("deleteProduct");
    Map<Integer, String> pageLinks = (Map<Integer, String>) request.getAttribute("adminProductPageLinks");
    Integer currentPage = (Integer) request.getAttribute("adminProductPage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-products.js" defer></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" data-admin-page="products" tabindex="-1">
            <h1 class="page-title">상품 관리</h1>
            <% if (blocked != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(blocked) %></p>
                <% if (response.getStatus() == 403) { %>
                    <a class="button secondary" href="${pageContext.request.contextPath}/app/member/login">로그인 화면</a>
                <% } %>
            <% } else { %>
                <% if (productForm == null && request.getAttribute("adminProductError") != null) { %>
                    <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("adminProductError")) %></p>
                <% } %>
                <% if (request.getAttribute("adminProductNotice") != null) { %>
                    <p class="form-status" role="status"><%= ProductView.escape(request.getAttribute("adminProductNotice")) %></p>
                <% } %>
                <div class="admin-product-toolbar">
                    <form class="admin-search" method="get" action="${pageContext.request.contextPath}/app/admin/products">
                        <label class="field">상품명, 품번, 브랜드 또는 ID 검색
                            <input name="query" type="search" maxlength="120" value="<%= ProductView.escape(request.getAttribute("adminQuery")) %>">
                        </label>
                        <button class="button secondary" type="submit">검색</button>
                    </form>
                    <a class="button primary" href="<%= request.getAttribute("adminProductNew") %>#product-editor">상품 등록</a>
                </div>

                <% if (productForm != null) { %>
                    <dialog class="product-editor-dialog" id="product-editor" open aria-labelledby="product-editor-title">
                        <div class="product-editor-heading">
                            <h2 id="product-editor-title" tabindex="-1"><%= productForm.getOrDefault("id", "").isEmpty() ? "상품 등록" : "상품 수정" %></h2>
                            <a class="product-editor-close" href="<%= request.getAttribute("adminProductList") %>" aria-label="상품 입력 창 닫기">×</a>
                        </div>
                        <% if (request.getAttribute("adminProductError") != null) { %>
                            <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("adminProductError")) %></p>
                        <% } %>
                        <form class="admin-editor-form" method="post" enctype="multipart/form-data" action="${pageContext.request.contextPath}/app/admin/products/save"
                              data-code-check="${pageContext.request.contextPath}/app/admin/products/check-code">
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                            <input type="hidden" name="id" value="<%= ProductView.escape(productForm.get("id")) %>">
                            <label class="field">상품명
                                <input name="name" required maxlength="255" value="<%= ProductView.escape(productForm.get("name")) %>">
                            </label>
                            <label class="field">품번
                                <input name="code" required maxlength="100" aria-describedby="product-code-feedback"
                                       value="<%= ProductView.escape(productForm.get("code")) %>">
                            </label>
                            <p class="product-field-feedback" id="product-code-feedback" role="status"></p>
                            <label class="field">브랜드
                                <input name="brand" maxlength="100" value="<%= ProductView.escape(productForm.get("brand")) %>">
                            </label>
                            <label class="field">카테고리 (최하위)
                                <select name="category" required>
                                    <option value="">카테고리를 선택해 주세요</option>
                                    <% for (Map.Entry<String, String> category : categories.entrySet()) { %>
                                        <option value="<%= ProductView.escape(category.getKey()) %>" <%= category.getKey().equals(productForm.get("category")) ? "selected" : "" %>><%= ProductView.escape(category.getValue()) %></option>
                                    <% } %>
                                </select>
                            </label>
                            <label class="field">가격 (원)
                                <input name="price" type="number" min="${adminMinPrice}" max="${adminMaxPrice}" step="1" required
                                       aria-describedby="product-price-feedback" value="<%= ProductView.escape(productForm.get("price")) %>">
                            </label>
                            <p class="product-field-feedback" id="product-price-feedback" role="status">${adminPriceRange}</p>
                            <label class="field">온라인 재고
                                <input name="stock" type="number" min="0" max="2147483647" required value="<%= ProductView.escape(productForm.get("stock")) %>">
                            </label>
                            <label class="field">판매 상태
                                <select name="status" required>
                                    <% for (String status : java.util.List.of("AVAILABLE", "SOLD_OUT", "SCHEDULED")) { %>
                                        <option value="<%= status %>" <%= status.equals(productForm.get("status")) ? "selected" : "" %>><%= statuses.get(status) %></option>
                                    <% } %>
                                </select>
                            </label>
                            <label class="field">대표 이미지 파일
                                <input name="imageFile" type="file" accept="image/jpeg,image/png" aria-describedby="product-image-feedback">
                            </label>
                            <p id="product-image-feedback" class="product-field-feedback" role="status">JPG·PNG, 최대 5MB, 가로·세로 각각 최대 4,096픽셀. 파일을 선택하면 기존 사진을 교체합니다.</p>
                            <img class="product-image-preview" data-product-image-preview data-image-base="${pageContext.request.contextPath}/"
                                 alt="선택한 상품 대표 이미지 미리보기" hidden>
                            <label class="field">이미지 경로 또는 HTTPS URL (직접 입력)
                                <input name="image" maxlength="1000" placeholder="assets/images/…" value="<%= ProductView.escape(productForm.get("image")) %>">
                            </label>
                            <label class="field">상품 설명
                                <textarea name="description" maxlength="2000" rows="4"><%= ProductView.escape(productForm.get("description")) %></textarea>
                            </label>
                            <div class="product-editor-actions">
                                <a class="button secondary" href="<%= request.getAttribute("adminProductList") %>">취소</a>
                                <button type="submit" class="button primary">상품 저장</button>
                            </div>
                        </form>
                    </dialog>
                <% } %>

                <% if (deletion != null) { %>
                    <section class="admin-panel" id="delete-product">
                        <h2>상품 삭제</h2>
                        <p>‘<%= ProductView.escape(deletion.getName()) %>’ 상품을 삭제할까요?</p>
                        <form method="post" action="${pageContext.request.contextPath}/app/admin/products/delete">
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                            <input type="hidden" name="id" value="<%= ProductView.escape(deletion.getProductId()) %>">
                            <button class="button primary" type="submit">삭제 확인</button>
                            <a class="button secondary" href="<%= request.getAttribute("adminProductList") %>">취소</a>
                        </form>
                    </section>
                <% } %>

                <p class="admin-count" role="status"><%= request.getAttribute("adminProductCount") %>개 상품</p>
                <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품 관리 표 (가로 스크롤 가능)">
                    <table class="admin-table">
                        <caption class="visually-hidden">상품 관리</caption>
                        <thead><tr>
                            <th scope="col">상품 ID</th><th scope="col">품번</th><th scope="col">상품명</th>
                            <th scope="col">가격</th><th scope="col">판매 상태</th><th scope="col">온라인 재고</th><th scope="col">관리</th>
                        </tr></thead>
                        <tbody>
                            <% for (Map.Entry<ProductDTO, Map<String, String>> row : productRows.entrySet()) {
                                   ProductDTO item = row.getKey(); %>
                                <tr>
                                    <td><%= ProductView.escape(item.getProductId()) %></td>
                                    <td><%= ProductView.escape(item.getProductCode()) %></td>
                                    <td><%= ProductView.escape(item.getName()) %></td>
                                    <td><%= ProductView.money(item.getPrice()) %></td>
                                    <td><%= ProductView.escape(statuses.getOrDefault(item.getStatus(), item.getStatus())) %></td>
                                    <td><%= item.getStockQuantity() %></td>
                                    <td>
                                        <a class="text-button" href="<%= row.getValue().get("edit") %>#product-editor" aria-label="<%= ProductView.escape(item.getName()) %> 수정">수정</a>
                                        <a class="text-button" href="<%= row.getValue().get("delete") %>#delete-product"
                                           data-delete-id="<%= ProductView.escape(item.getProductId()) %>"
                                           data-delete-name="<%= ProductView.escape(item.getName()) %>"
                                           aria-label="<%= ProductView.escape(item.getName()) %> 삭제">삭제</a>
                                    </td>
                                </tr>
                            <% } %>
                            <% if (productRows.isEmpty()) { %><tr><td colspan="7">조건에 맞는 상품이 없습니다.</td></tr><% } %>
                        </tbody>
                    </table>
                </div>
                <nav class="admin-product-pagination" aria-label="상품 관리 페이지">
                    <% if (request.getAttribute("adminProductPrev") != null) { %>
                        <a class="button secondary" rel="prev" href="<%= request.getAttribute("adminProductPrev") %>">이전 페이지</a>
                    <% } %>
                    <div class="admin-product-page-numbers">
                        <% for (Map.Entry<Integer, String> link : pageLinks.entrySet()) { %>
                            <% if (link.getKey().equals(currentPage)) { %>
                                <span class="admin-product-page-number" aria-current="page"><%= link.getKey() %></span>
                            <% } else { %>
                                <a class="admin-product-page-number" href="<%= link.getValue() %>"
                                   aria-label="<%= link.getKey() %>페이지"><%= link.getKey() %></a>
                            <% } %>
                        <% } %>
                    </div>
                    <% if (request.getAttribute("adminProductNext") != null) { %>
                        <a class="button secondary" rel="next" href="<%= request.getAttribute("adminProductNext") %>">다음 페이지</a>
                    <% } %>
                </nav>
                <dialog class="product-delete-dialog" id="product-delete-dialog" aria-labelledby="product-delete-title"
                        aria-describedby="product-delete-message">
                    <h2 id="product-delete-title">상품을 삭제할까요?</h2>
                    <p id="product-delete-message"><strong data-delete-product-name></strong> 상품을 삭제하면 되돌릴 수 없습니다.</p>
                    <form method="post" action="${pageContext.request.contextPath}/app/admin/products/delete">
                        <input type="hidden" name="csrf" value="<%= ProductView.escape(csrf) %>">
                        <input type="hidden" name="id">
                        <button class="button secondary" type="button" data-delete-cancel autofocus>취소</button>
                        <button class="button primary" type="submit">삭제하기</button>
                    </form>
                </dialog>
            <% } %>
        </main>
    </div>
</body>
</html>
