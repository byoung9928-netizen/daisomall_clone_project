<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,daiso.product.controller.AdminCategoryController,daiso.product.controller.AdminCategoryController.Page,daiso.product.controller.AdminCategoryController.Group,daiso.product.controller.AdminCategoryController.Node,daiso.product.controller.ProductView" %>
<%
    if (!Boolean.TRUE.equals(request.getAttribute("categoryReady"))) {
        request.getRequestDispatcher(AdminCategoryController.ROUTE).forward(request, response);
        return;
    }
    String categoryRoute = ProductView.escape(request.getContextPath() + AdminCategoryController.ROUTE);
    Page categoryPage = (Page) request.getAttribute("categoryPage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main class="admin-content store-admin" id="admin-content" tabindex="-1">
            <h1 class="page-title">카테고리 관리</h1>
            <% if (Boolean.TRUE.equals(request.getAttribute("categorySaved"))) { %>
                <p class="inventory-success" role="status">카테고리를 저장했습니다.</p>
            <% } %>
            <% if (request.getAttribute("categoryError") != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("categoryError")) %></p>
            <% } %>
            <% if (categoryPage != null) { %>
                <form class="admin-search store-admin-filters" method="get" action="<%= categoryRoute %>">
                    <label class="field">카테고리명·ID
                        <input name="q" type="search" maxlength="120" value="<%= ProductView.escape(categoryPage.keyword) %>">
                    </label>
                    <div class="inventory-filter-actions">
                        <button class="button primary" type="submit">검색</button>
                        <a class="button secondary" href="<%= categoryRoute %>">초기화</a>
                        <a class="button secondary" href="<%= categoryPage.newUrl %>#category-editor">카테고리 등록</a>
                    </div>
                </form>
                <% if (categoryPage.form != null) {
                       Map<String, String> categoryForm = categoryPage.form; %>
                    <section class="admin-panel admin-category-editor" id="category-editor" aria-labelledby="category-editor-title">
                        <h2 id="category-editor-title"><%= categoryPage.create ? "카테고리 등록" : "카테고리 수정" %></h2>
                        <% if (categoryPage.selected != null && !categoryPage.create) { %>
                            <p><strong><%= ProductView.escape(categoryPage.selected.path) %></strong> · <%= categoryPage.selected.row.level %>단계</p>
                            <p id="category-visibility-impact">비노출 시 이 분류와 하위 <%= categoryPage.selected.descendants %>개 분류가 고객 탐색에서 제외돼요.
                                이 범위의 상품 <%= categoryPage.selected.subtreeProducts %>개는 고객 검색·상세에서 숨겨져요.</p>
                        <% } %>
                        <form method="post" action="<%= categoryRoute %>/save" class="admin-editor-form">
                            <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("categoryCsrf")) %>">
                            <input type="hidden" name="q" value="<%= ProductView.escape(categoryPage.keyword) %>">
                            <input type="hidden" name="create" value="<%= categoryPage.create ? "1" : "0" %>">
                            <% if (categoryPage.create) { %>
                                <label class="field">카테고리 ID
                                    <input name="id" maxlength="100" required pattern="[A-Za-z0-9_\-]+" value="<%= ProductView.escape(categoryForm.get("id")) %>">
                                </label>
                                <label class="field">상위 카테고리
                                    <select name="parent">
                                        <option value="">없음 (대분류)</option>
                                        <% for (Node parent : categoryPage.parents) { %>
                                            <option value="<%= ProductView.escape(parent.row.id) %>" <%= parent.row.id.equals(categoryForm.get("parent")) ? "selected" : "" %>><%= ProductView.escape(parent.path) %></option>
                                        <% } %>
                                    </select>
                                </label>
                            <% } else { %>
                                <input type="hidden" name="id" value="<%= ProductView.escape(categoryForm.get("id")) %>">
                            <% } %>
                            <label class="field">카테고리명
                                <input name="name" maxlength="120" required value="<%= ProductView.escape(categoryForm.get("name")) %>">
                            </label>
                            <label class="field">표시 순서
                                <input name="order" type="number" min="0" max="2147483647" step="1" required value="<%= ProductView.escape(categoryForm.get("order")) %>">
                            </label>
                            <label class="field">고객 메뉴 노출
                                <select name="visible" <%= categoryPage.selected != null && !categoryPage.create ? "aria-describedby=\"category-visibility-impact\"" : "" %>>
                                    <option value="1" <%= "1".equals(categoryForm.get("visible")) ? "selected" : "" %>>노출</option>
                                    <option value="0" <%= "0".equals(categoryForm.get("visible")) ? "selected" : "" %>>비노출</option>
                                </select>
                            </label>
                            <div class="inventory-filter-actions">
                                <button type="submit" class="button primary">저장</button>
                                <a class="button secondary" href="<%= categoryPage.resetUrl %>">취소</a>
                            </div>
                        </form>
                    </section>
                <% } %>
                <% for (Group group : categoryPage.groups) { %>
                    <details class="admin-category-group" <%= categoryPage.keyword.isEmpty() ? "" : "open" %>>
                        <summary><strong><%= ProductView.escape(group.root.row.name) %></strong> · 표시 순서 <%= group.root.row.order %></summary>
                        <div class="admin-table-scroll" role="region" tabindex="0" aria-label="<%= ProductView.escape(group.root.row.name) %> 카테고리 표">
                            <table class="admin-table store-stock-table">
                                <thead><tr>
                                    <th scope="col">분류명·ID</th><th scope="col">표시 순서</th><th scope="col">노출 상태</th>
                                    <th scope="col">직접 등록 상품</th><th scope="col">관리</th>
                                </tr></thead>
                                <tbody>
                                    <% for (Node node : group.rows) { %>
                                        <tr>
                                            <td><div class="admin-category-name" data-level="<%= node.row.level %>">
                                                <a href="<%= categoryPage.editUrl(node.row.id) %>#category-editor"><%= ProductView.escape(node.row.name) %></a>
                                                <small><%= ProductView.escape(node.row.id) %> · <%= node.row.level %>단계</small>
                                            </div></td>
                                            <td><%= node.row.order %></td>
                                            <td><%= !node.row.visible ? "비노출" : node.effectiveVisible ? "노출" : "상위 비노출" %></td>
                                            <td><%= node.row.products %>개</td>
                                            <td><a class="button secondary" href="<%= categoryPage.editUrl(node.row.id) %>#category-editor">수정</a></td>
                                        </tr>
                                    <% } %>
                                </tbody>
                            </table>
                        </div>
                    </details>
                <% } %>
                <% if (categoryPage.groups.isEmpty()) { %><p class="empty-state">조건에 맞는 카테고리가 없습니다.</p><% } %>
            <% } %>
        </main>
    </div>
</body>
</html>
