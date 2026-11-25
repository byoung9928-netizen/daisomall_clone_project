<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductView,daiso.product.controller.AdminInventoryBatchController.Plan,daiso.product.controller.AdminInventoryBatchController.Line" %>
<!doctype html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-product-workspace.css?v=batch-preview-2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-stores.css">
</head>
<body class="admin-body product-admin-ux">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content store-admin" tabindex="-1">
            <% Plan batch = (Plan) request.getAttribute("batchPreview"); %>
            <% if (batch != null) { %>
                <section class="admin-panel inventory-batch-preview" data-batch-preview>
                    <header class="inventory-batch-heading">
                        <h1 id="batch-preview-title" class="page-title" tabindex="-1"><%= batch.label %> 미리보기</h1>
                        <button type="button" class="button secondary" data-batch-close aria-label="미리보기 닫기" hidden>×</button>
                    </header>
                    <div class="batch-preview-summary">
                    <p><strong>선택 상품 <%= batch.transfer ? 1 : batch.lines.size() %>개</strong></p>
                    <p><%= batch.transfer ? "이동 사유" : "변경 사유" %> · <%= ProductView.escape(batch.reason) %></p>
                    <% if (!batch.threshold) { %>
                        <p>전체 입고 <strong><%= batch.receivedUnits %>개</strong> · 전체 출고 <strong><%= batch.issuedUnits %>개</strong></p>
                    <% } %>
                    </div>
                    <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품별 변경 계획">
                        <table class="admin-table">
                            <thead><tr><th scope="col">상품명·품번</th><th scope="col">재고 위치</th>
                                <th scope="col"><%= batch.threshold ? "기존 부족 기준" : "변경 전 재고" %></th>
                                <th scope="col"><%= batch.threshold ? "기준 변경량" : "재고 변경량" %></th>
                                <th scope="col"><%= batch.threshold ? "새 부족 기준" : "변경 후 재고" %></th></tr></thead>
                            <tbody><% for (Line line : batch.lines) { long delta = (long) line.after - line.before; %>
                                <tr><td><%= ProductView.escape(line.name) %><small>품번 <%= ProductView.escape(line.code) %></small></td>
                                    <td><%= line.store.isEmpty() ? "온라인" : ProductView.escape(batch.storeNames.get(line.store)) %>
                                        <%= batch.transfer ? (delta < 0 ? " · 출고" : " · 입고") : "" %></td>
                                    <td><%= line.before %>개</td><td><%= delta > 0 ? "+" : "" %><%= delta %>개</td>
                                    <td><% if (delta != 0) { %><strong class="batch-preview-change"><%= line.after %>개</strong>
                                        <% } else { %><%= line.after %>개 <small>변경 없음</small><% } %></td></tr>
                            <% } %></tbody>
                        </table>
                    </div>
                    <p class="error-message" role="alert" data-batch-confirm-error hidden tabindex="-1"></p>
                    <form method="post" class="inventory-batch-confirm" action="<%= request.getAttribute("batchAction") %>" data-store-write data-batch-confirm>
                        <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("batchCsrf")) %>">
                        <input type="hidden" name="receipt" value="<%= batch.receipt %>">
                        <a class="button secondary" href="<%= request.getAttribute("batchBack") %>#inventory-batch" data-batch-revise>다시 작성</a>
                        <button type="submit" class="button primary">변경 확정</button>
                    </form>
                </section>
            <% } else { %>
                <h1 class="page-title">재고 일괄 작업</h1>
                <% if (request.getAttribute("batchError") != null) { %>
                    <p class="error-message" role="alert" data-batch-server-error
                       data-field="<%= ProductView.escape(request.getAttribute("batchErrorField")) %>"><%= ProductView.escape(request.getAttribute("batchError")) %></p>
                <% } %>
                <% if (request.getAttribute("batchBack") != null) { %>
                    <p><a class="button secondary" href="<%= request.getAttribute("batchBack") %>">재고 목록으로</a></p>
                <% } %>
            <% } %>
        </main>
    </div>
</body>
</html>
