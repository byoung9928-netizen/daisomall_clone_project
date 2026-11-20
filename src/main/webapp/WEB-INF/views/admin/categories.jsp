<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.Map,java.time.format.DateTimeFormatter,daiso.product.controller.AdminCategoryController,daiso.product.controller.AdminCategoryModel.Page,daiso.product.controller.AdminCategoryModel.TreeItem,daiso.product.controller.AdminCategoryModel.Node,daiso.product.controller.ProductView,daiso.product.dao.AdminCategoryHistoryDAO.Entry" %>
<%
    if (!Boolean.TRUE.equals(request.getAttribute("categoryReady"))) {
        request.getRequestDispatcher(AdminCategoryController.ROUTE).forward(request, response);
        return;
    }
    String categoryRoute = ProductView.escape(request.getContextPath() + AdminCategoryController.ROUTE);
    String csrf = ProductView.escape(request.getAttribute("categoryCsrf"));
    Page categoryPage = (Page) request.getAttribute("categoryPage");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-categories.css">
    <script src="${pageContext.request.contextPath}/assets/js/common/admin-categories.js" defer></script>
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main class="admin-content category-admin" id="admin-content" tabindex="-1">
            <h1 class="page-title">카테고리 관리</h1>
            <% if (request.getAttribute("categorySaved") != null) { %>
                <p class="inventory-success" role="status" data-category-saved><%= ProductView.escape(request.getAttribute("categorySaved")) %></p>
            <% } %>
            <% if (request.getAttribute("categoryError") != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("categoryError")) %></p>
            <% } %>
            <% if (categoryPage != null) { %>
                <% if (!categoryPage.historyReady) { %>
                    <p class="category-guide" role="status">이력 테이블이 없어 현재 조회만 가능합니다. 카테고리 이력 SQL을 적용하면 변경을 사용할 수 있습니다.</p>
                <% } %>
                <div class="category-workspace">
                    <section class="category-browser" aria-labelledby="category-browser-heading">
                        <div class="category-section-heading">
                            <h2 id="category-browser-heading">카테고리 트리</h2>
                            <div class="category-actions">
                                <a class="button primary" href="<%= categoryPage.newUrl %>#category-detail" data-category-nav>카테고리 등록</a>
                                <a class="button secondary" href="<%= categoryPage.allHistoryUrl %>#category-detail" data-category-nav>전체 카테고리 변경 이력</a>
                            </div>
                        </div>
                        <form class="category-search" method="get" action="<%= categoryRoute %>" role="search" aria-label="카테고리 검색">
                            <label class="visually-hidden" for="category-search">카테고리명 또는 ID</label>
                            <input id="category-search" name="q" type="search" maxlength="120" placeholder="카테고리명·ID"
                                   value="<%= ProductView.escape(categoryPage.keyword) %>">
                            <button class="button primary" type="submit">검색</button>
                            <% if (!categoryPage.keyword.isEmpty()) { %><a href="<%= categoryRoute %>">초기화</a><% } %>
                        </form>
                        <p class="category-tree-note">상품 수는 하위 분류를 포함합니다.</p>
                        <% if (categoryPage.tree.isEmpty()) { %><p class="empty-state">조건에 맞는 카테고리가 없습니다.</p><% } %>
                        <ul class="category-tree" data-category-tree>
                            <% int openLevels = 0;
                               for (TreeItem item : categoryPage.tree) {
                                   Node node = item.node;
                                   boolean active = node == categoryPage.selected;
                                   while (openLevels >= node.row.level) { %></ul></details></li><% openLevels--; } %>
                                <li>
                                    <div class="category-tree-row">
                                        <div class="category-tree-links">
                                            <a class="category-tree-select" href="<%= categoryPage.editUrl(node.row.id) %>#category-detail" data-category-nav data-category-id="<%= ProductView.escape(node.row.id) %>"
                                               <%= active ? "aria-current=\"true\"" : "" %>>
                                                <strong><%= ProductView.escape(node.row.name) %></strong>
                                                <span class="category-tree-count"><%= node.subtreeProducts %>개</span>
                                            </a>
                                            <% if (categoryPage.historyReady && node.canParent()) { %>
                                                <a class="category-tree-add" href="<%= categoryPage.childUrl(node.row.id) %>#category-detail"
                                                   aria-label="<%= ProductView.escape(node.row.name) %>에 하위 카테고리 추가" data-category-nav>+</a>
                                            <% } %>
                                        </div>
                                        <% if (!node.effectiveVisible) { %>
                                            <span class="category-visibility"><span aria-hidden="true">◌</span> <%= node.row.visible ? "상위 비노출" : "비노출" %></span>
                                        <% } %>
                                        <% if (!categoryPage.keyword.isEmpty()) { %><small><%= ProductView.escape(node.path) %></small><% } %>
                                    </div>
                                    <% if (item.expandable) {
                                           openLevels = node.row.level; %>
                                        <details data-category-branch="<%= ProductView.escape(node.row.id) %>" <%= item.open ? "open" : "" %>>
                                            <summary>하위 분류<span class="visually-hidden"> · <%= ProductView.escape(node.row.name) %></span></summary>
                                            <ul>
                                    <% } else { %>
                                        </li>
                                    <% } %>
                            <% } while (openLevels > 0) { %></ul></details></li><% openLevels--; } %>
                        </ul>
                    </section>
                    <section class="category-detail" id="category-detail" data-category-panel
                             data-selected="<%= categoryPage.selected == null ? "" : ProductView.escape(categoryPage.selected.row.id) %>"
                             aria-labelledby="category-detail-heading">
                        <% if (request.getAttribute("categoryAllHistory") != null) { %>
                            <%@ include file="category-all-history.jspf" %>
                        <% } else if (categoryPage.form == null) { %>
                            <h2 id="category-detail-heading" tabindex="-1">카테고리 상세</h2>
                            <p>트리에서 카테고리를 선택하세요.</p>
                        <% } else {
                               Node selected = categoryPage.selected;
                               Map<String, String> form = categoryPage.form;
                               String keyword = ProductView.escape(categoryPage.keyword);
                               Object deleteToken = request.getAttribute("categoryDeleteToken");
                               DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); %>
                            <div class="category-section-heading">
                                <h2 id="category-detail-heading" tabindex="-1"><%= categoryPage.create ? "카테고리 등록" : selected == null ? "카테고리 수정" : ProductView.escape(selected.row.name) %></h2>
                                <% if (!categoryPage.create && categoryPage.productsUrl != null) { %>
                                    <a class="text-button category-products-link" href="<%= categoryPage.productsUrl %>">이 분류 상품 관리 <span aria-hidden="true">↗</span></a>
                                <% } %>
                            </div>
                            <% if (selected != null && !categoryPage.create) { %>
                                <dl class="category-facts">
                                    <div><dt>ID</dt><dd><%= ProductView.escape(selected.row.id) %></dd></div>
                                    <div><dt>상위 경로</dt><dd><%= selected.parent == null ? "없음" : ProductView.escape(selected.parent.path) %></dd></div>
                                    <div><dt>분류 단계</dt><dd><%= selected.row.level %>단계</dd></div>
                                    <div><dt>표시 순서</dt><dd><%= selected.row.order %></dd></div>
                                    <div><dt>직접 등록 상품</dt><dd><%= selected.row.products %>개</dd></div>
                                    <div><dt>하위 포함 상품</dt><dd><%= selected.subtreeProducts %>개</dd></div>
                                    <div><dt>노출 설정</dt><dd><%= selected.row.visible ? "노출" : "비노출" %></dd></div>
                                    <div><dt>고객 화면</dt><dd><%= selected.effectiveVisible ? "표시" : selected.row.visible ? "상위 비노출" : "숨김" %></dd></div>
                                </dl>
                                <% if (categoryPage.latest != null) { %>
                                    <p class="category-tree-note">마지막 변경 · <%= ProductView.escape(categoryPage.latest.action) %> ·
                                        <time datetime="<%= categoryPage.latest.time %>"><%= format.format(categoryPage.latest.time) %></time></p>
                                <% } %>
                                <% if (categoryPage.impact == null && deleteToken == null) { %>
                                    <div class="category-actions">
                                        <form class="category-actions" method="post" action="<%= categoryRoute %>/save">
                                            <input type="hidden" name="csrf" value="<%= csrf %>">
                                            <input type="hidden" name="q" value="<%= keyword %>">
                                            <input type="hidden" name="id" value="<%= ProductView.escape(selected.row.id) %>">
                                            <button class="button secondary" type="submit" name="intent" value="up" <%= categoryPage.historyReady && categoryPage.canUp ? "" : "disabled" %>>↑ 위로</button>
                                            <button class="button secondary" type="submit" name="intent" value="down" <%= categoryPage.historyReady && categoryPage.canDown ? "" : "disabled" %>>↓ 아래로</button>
                                        </form>
                                        <% if (categoryPage.childUrl != null) { %>
                                            <a class="button secondary" href="<%= categoryPage.childUrl %>#category-detail" data-category-nav>하위 카테고리 추가</a>
                                        <% } %>
                                    </div>
                                <% } %>
                            <% } %>
                            <% if (deleteToken == null && (categoryPage.create || selected != null)) { %>
                                <form class="category-form" method="post" action="<%= categoryRoute %>/save">
                                    <% if (!categoryPage.create && categoryPage.impact == null) { %><h3 class="category-form-title">카테고리 정보 수정</h3><% } %>
                                    <input type="hidden" name="csrf" value="<%= csrf %>">
                                    <input type="hidden" name="q" value="<%= keyword %>">
                                    <input type="hidden" name="create" value="<%= categoryPage.create ? "1" : "0" %>">
                                    <% if (!categoryPage.create) { %>
                                        <input type="hidden" name="id" value="<%= ProductView.escape(selected.row.id) %>">
                                        <input type="hidden" name="revision" value="<%= ProductView.escape(categoryPage.revision) %>">
                                    <% } %>
                                    <% if (categoryPage.impact != null) { %>
                                        <input type="hidden" name="name" value="<%= ProductView.escape(form.get("name")) %>">
                                        <input type="hidden" name="visible" value="0">
                                        <input type="hidden" name="confirmation" value="<%= ProductView.escape(categoryPage.confirmToken) %>">
                                        <section class="category-confirm" aria-labelledby="category-confirm-title">
                                            <h3 id="category-confirm-title">비노출 영향 확인</h3>
                                            <p><strong><%= ProductView.escape(selected.path) %></strong>을 숨기면 현재 고객에게 보이는 하위 분류 <strong><%= categoryPage.impact.descendants %>개</strong>와 상품 <strong><%= categoryPage.impact.products %>개</strong>가 고객 메뉴·검색·상세에서 제외됩니다.</p>
                                            <div class="category-actions">
                                                <button type="submit" name="intent" value="confirm" class="button inventory-danger">비노출 확정</button>
                                                <a class="button secondary" href="<%= categoryPage.resetUrl %>#category-detail">취소</a>
                                            </div>
                                        </section>
                                    <% } else { %>
                                        <% if (categoryPage.create) { %>
                                            <label class="field">상위 카테고리
                                                <select name="parent">
                                                    <option value="">없음 (대분류)</option>
                                                    <% for (Node parent : categoryPage.parents) { %>
                                                        <option value="<%= ProductView.escape(parent.row.id) %>" <%= parent.row.id.equals(form.get("parent")) ? "selected" : "" %>><%= ProductView.escape(parent.path) %></option>
                                                    <% } %>
                                                </select>
                                            </label>
                                            <p class="category-tree-note">ID는 서버가 발급하며, 같은 상위 분류의 마지막에 추가됩니다.</p>
                                        <% } %>
                                        <label class="field">카테고리명
                                            <input name="name" maxlength="120" required value="<%= ProductView.escape(form.get("name")) %>">
                                        </label>
                                        <label class="field">고객 메뉴 노출
                                            <select id="category-visibility" name="visible" <%= selected != null && selected.parent != null && !selected.parent.effectiveVisible ? "aria-describedby=\"category-parent-hidden\"" : "" %>>
                                                <option value="1" <%= "1".equals(form.get("visible")) ? "selected" : "" %>>노출</option>
                                                <option value="0" <%= "0".equals(form.get("visible")) ? "selected" : "" %>>비노출</option>
                                            </select>
                                        </label>
                                        <% if (selected != null && selected.parent != null && !selected.parent.effectiveVisible && !categoryPage.create) { %>
                                            <p id="category-parent-hidden" class="category-guide">상위 분류가 비노출 상태라 이 분류만 노출해도 고객에게 보이지 않습니다.</p>
                                        <% } else if (categoryPage.create && selected != null && !selected.effectiveVisible) { %>
                                            <p class="category-guide">지정된 상위 분류가 고객에게 보이지 않습니다.</p>
                                        <% } %>
                                        <div class="category-actions">
                                            <button type="submit" name="intent" value="save" class="button primary" <%= categoryPage.historyReady ? "" : "disabled" %>><%= categoryPage.create ? "카테고리 등록" : "카테고리 정보 저장" %></button>
                                            <% if (categoryPage.create) { %><a class="button secondary" href="<%= categoryPage.resetUrl %>#category-detail" data-category-nav>취소</a><% } %>
                                        </div>
                                    <% } %>
                                </form>
                            <% } %>
                            <% if (!categoryPage.create && selected != null && categoryPage.impact == null) { %>
                                <section class="category-history" aria-labelledby="category-delete-heading">
                                    <h3 id="category-delete-heading"><%= deleteToken == null ? "카테고리 삭제" : "카테고리 삭제 확인" %></h3>
                                    <% if (deleteToken != null) { %>
                                        <div class="category-confirm">
                                            <p><strong><%= ProductView.escape(selected.path) %></strong>을 삭제합니다. 하위 분류 0개, 연결 상품 0개이며 변경 이력은 보존됩니다.</p>
                                            <form class="category-actions" method="post" action="<%= categoryRoute %>/delete">
                                                <input type="hidden" name="csrf" value="<%= csrf %>">
                                                <input type="hidden" name="q" value="<%= keyword %>">
                                                <input type="hidden" name="id" value="<%= ProductView.escape(selected.row.id) %>">
                                                <input type="hidden" name="confirmation" value="<%= ProductView.escape(deleteToken) %>">
                                                <button class="button inventory-danger" type="submit" name="intent" value="confirm">카테고리 삭제 확정</button>
                                                <a class="button secondary" href="<%= categoryPage.resetUrl %>#category-detail" data-category-nav>취소</a>
                                            </form>
                                        </div>
                                    <% } else if (selected.hasChildren() || selected.row.products != 0) { %>
                                        <p class="category-tree-note">하위 분류나 연결 상품이 있는 카테고리는 삭제할 수 없습니다. 고객 메뉴에서 숨기려면 <a href="#category-visibility">비노출 설정</a>을 이용하세요.</p>
                                    <% } else if (!categoryPage.deleteReady) { %>
                                        <p class="category-tree-note">삭제 이력 마이그레이션을 적용하면 빈 카테고리를 삭제할 수 있습니다.</p>
                                    <% } else { %>
                                        <form method="post" action="<%= categoryRoute %>/delete">
                                            <input type="hidden" name="csrf" value="<%= csrf %>">
                                            <input type="hidden" name="q" value="<%= keyword %>">
                                            <input type="hidden" name="id" value="<%= ProductView.escape(selected.row.id) %>">
                                            <button class="button secondary inventory-danger-outline" type="submit" name="intent" value="preview">삭제 내용 확인</button>
                                        </form>
                                    <% } %>
                                </section>
                            <% } %>
                            <% if (!categoryPage.create && selected != null && categoryPage.historyReady) { %>
                                <section class="category-history" aria-labelledby="category-history-heading">
                                    <h3 id="category-history-heading">변경 이력</h3>
                                    <% if (categoryPage.history.isEmpty()) { %><p>표시할 변경 이력이 없습니다.</p><% } %>
                                    <ul>
                                        <% for (Entry entry : categoryPage.history) { %>
                                            <li><strong><%= ProductView.escape(entry.action) %></strong>
                                                <span><%= entry.before == null ? "—" : ProductView.escape(entry.before) %> → <%= ProductView.escape(entry.after) %></span>
                                                <small><time datetime="<%= entry.time %>"><%= format.format(entry.time) %></time> · 관리자 <%= ProductView.escape(entry.actor) %></small>
                                            </li>
                                        <% } %>
                                    </ul>
                                    <nav class="category-actions" aria-label="카테고리 변경 이력 페이지">
                                        <% if (categoryPage.previous != null) { %><a class="button secondary" href="<%= categoryPage.previous %>#category-history-heading" data-category-nav>최근 기록</a><% } %>
                                        <% if (categoryPage.next != null) { %><a class="button secondary" href="<%= categoryPage.next %>#category-history-heading" data-category-nav>이전 기록</a><% } %>
                                    </nav>
                                </section>
                            <% } %>
                        <% } %>
                    </section>
                </div>
                <p class="visually-hidden" role="status" data-category-status></p>
            <% } %>
        </main>
    </div>
</body>
</html>
