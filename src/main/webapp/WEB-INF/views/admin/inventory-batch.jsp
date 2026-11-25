<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductView,daiso.product.controller.AdminInventoryBatchController.Plan,daiso.product.controller.AdminInventoryBatchController.Line" %>
<!doctype html>
<html lang="ko">
<head><%@ include file="../common/admin-head.jspf" %></head>
<body class="admin-body">
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="product-admin-menu.jspf" %>
        <main id="admin-content" class="admin-content">
            <h1 class="page-title">재고 작업 미리보기</h1>
            <% if (request.getAttribute("batchError") != null) { %>
                <p class="error-message" role="alert"><%= ProductView.escape(request.getAttribute("batchError")) %></p>
            <% } %>
            <% Plan batch = (Plan) request.getAttribute("batchPreview");
               if (batch != null) { %>
                <p><strong><%= batch.transfer ? 1 : batch.lines.size() %>개 상품</strong> · <%= ProductView.escape(batch.reason) %></p>
                <% if (!batch.threshold) { %><p>전체 재고 증감 <strong><%= batch.changedUnits %>개</strong></p><% } %>
                <% if (batch.transfer) { %><p>이동 번호 <%= batch.receipt %></p><% } %>
                <div class="admin-table-scroll"><table class="admin-table">
                    <thead><tr><th>상품</th><th>매장</th><th>변경 전</th><th>변경량</th><th>변경 후</th></tr></thead>
                    <tbody><% for (Line line : batch.lines) { %>
                        <tr><td><%= ProductView.escape(line.name) %></td>
                            <td><%= line.store.isEmpty() ? "온라인" : ProductView.escape(line.store) %></td>
                            <td><%= line.before %></td><td><%= (long) line.after - line.before %></td><td><%= line.after %></td></tr>
                    <% } %></tbody>
                </table></div>
                <form method="post" action="<%= request.getAttribute("batchAction") %>" data-store-write>
                    <input type="hidden" name="csrf" value="<%= ProductView.escape(request.getAttribute("batchCsrf")) %>">
                    <input type="hidden" name="receipt" value="<%= batch.receipt %>">
                    <button type="submit" class="button primary">재고 변경 확정</button>
                </form>
            <% } %>
            <% if (request.getAttribute("batchBack") != null) { %>
                <p><a class="button secondary" href="<%= request.getAttribute("batchBack") %>">재고 목록으로</a></p>
            <% } %>
        </main>
    </div>
</body>
</html>
