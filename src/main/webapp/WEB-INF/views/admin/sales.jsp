<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,daiso.product.controller.ProductView,daiso.admin.report.AdminSalesDAO.Analysis,daiso.admin.report.AdminSalesDAO.Sale,daiso.product.controller.AdminReviewController.Period" %>
<%
    List<Object[]> salesRows = (List<Object[]>) request.getAttribute("salesRows");
    Object salesError = request.getAttribute("salesError");
    java.text.NumberFormat salesNumbers = java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
    <style>
        .sales-metrics {display:grid;grid-template-columns:repeat(auto-fit,minmax(175px,1fr));gap:12px;margin:16px 0}
        .sales-metrics>div,.sales-graph {background:#fff;border:1px solid #e4d8d8;border-radius:8px;padding:14px}
        .sales-metrics dt,.sales-metrics small,.sales-caption {color:#545961}
        .sales-metrics dd {margin:4px 0;font-size:1.4rem;font-weight:700}
        .sales-analysis-grid {display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,320px),1fr));gap:12px;margin:16px 0}
        .sales-graph ol {list-style:none;padding:0;margin:0;max-height:320px;overflow:auto}
        .sales-graph li {display:grid;grid-template-columns:minmax(80px,1fr) minmax(40px,2fr) auto;align-items:center;gap:8px;margin:7px 0;font-size:.875rem}
        .sales-graph meter {width:100%;accent-color:#a61232}
        .sales-shortcuts {display:flex;flex-wrap:wrap;gap:8px;margin:12px 0}
    </style>
</head>
<body class="admin-body">
<a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
<%@ include file="../common/admin-header.jspf" %>
<div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
    <main id="admin-content" class="admin-content" tabindex="-1">
        <h1 class="page-title">판매순위</h1>
        <nav class="sales-shortcuts" aria-label="빠른 판매 조회 기간">
            <% for (Period preset : List.of(Period.TODAY, Period.WEEK, Period.MONTH)) { %>
                <a class="button <%= preset.name().equals(request.getParameter("salesPeriod")) ? "primary" : "secondary" %>"
                   href="${pageContext.request.contextPath}/app/admin/sales?salesPeriod=<%= preset.name() %>"
                   <%= preset.name().equals(request.getParameter("salesPeriod")) ? "aria-current=\"page\"" : "" %>><%= preset.label %></a>
            <% } %>
        </nav>
        <form class="admin-search" method="get" action="${pageContext.request.contextPath}/app/admin/sales">
            <label>시작일 <input type="date" name="from" required
                value="<%= ProductView.escape(request.getAttribute("salesFrom")) %>"></label>
            <label>종료일 <input type="date" name="through" required
                value="<%= ProductView.escape(request.getAttribute("salesThrough")) %>"></label>
            <button type="submit" class="button primary">조회</button>
        </form>
        <%
            Analysis analytics = (Analysis) request.getAttribute("salesAnalytics");
            if (analytics != null) {
                String[] labels = {"상품 매출", "판매 수량", "결제 완료 주문", "주문 총결제액"};
                String[] units = {"원", "개", "건", "원"};
                java.text.NumberFormat change = java.text.NumberFormat.getPercentInstance(java.util.Locale.KOREA);
                change.setMaximumFractionDigits(1);
        %>
            <p class="sales-caption">결제 승인일 기준으로 집계합니다. 취소·환불 완료 건은 제외합니다. 주문 총결제액에는 배송비 등 주문별 비용이 포함됩니다.</p>
            <% if (analytics.priorFrom != null) { %>
                <p class="sales-caption">이전 동일 기간 <%= analytics.priorFrom %> ~ <%= analytics.priorThrough %></p>
            <% } %>
            <dl class="sales-metrics">
                <% for (int i = 0; i < labels.length; i++) { %>
                    <div><dt><%= labels[i] %></dt><dd><%= salesNumbers.format(analytics.current[i]) %><%= units[i] %></dd>
                        <% if (analytics.previous[i] > 0) { %>
                            <small>이전 대비 <%= analytics.current[i] > analytics.previous[i] ? "+" : "" %><%= change.format((analytics.current[i] - analytics.previous[i]) / (double) analytics.previous[i]) %></small>
                        <% } else if (analytics.priorFrom != null) { %><small>이전 기간 실적 없음</small><% } %>
                    </div>
                <% } %>
            </dl>
            <div class="sales-analysis-grid">
                <% for (boolean amount : new boolean[]{true, false}) { %>
                    <section class="sales-graph"><h2><%= amount ? "일별 상품 매출" : "일별 판매량" %></h2>
                        <% if (analytics.days.isEmpty()) { %><p>판매 기록이 없습니다.</p><% } else { %>
                            <ol>
                            <% long maximum = Math.max(1, analytics.dayMaximum(amount));
                               for (Sale day : analytics.days) { long value = amount ? day.amount : day.units; %>
                                <li><time datetime="<%= day.label %>"><%= day.label %></time>
                                    <meter aria-label="<%= day.label %>" min="0" max="<%= maximum %>" value="<%= value %>"></meter>
                                    <span><%= salesNumbers.format(value) %><%= amount ? "원" : "개" %></span></li>
                            <% } %>
                            </ol>
                        <% } %>
                    </section>
                <% } %>
                <section class="sales-graph"><h2>대분류별 판매량</h2>
                    <% if (analytics.categories.isEmpty()) { %><p>판매 기록이 없습니다.</p><% } else { %>
                        <ol>
                        <% for (Sale group : analytics.categories) { %>
                            <li><span><%= ProductView.escape(group.label) %></span>
                                <meter aria-label="<%= ProductView.escape(group.label) %>" min="0" max="<%= Math.max(1, analytics.categories.get(0).units) %>" value="<%= group.units %>"></meter>
                                <span><%= salesNumbers.format(group.units) %>개</span></li>
                        <% } %>
                        </ol>
                    <% } %>
                </section>
            </div>
        <% } %>
        <% if (salesError != null) { %>
            <p role="alert"><%= ProductView.escape(salesError) %></p>
        <% } else if (salesRows == null || salesRows.isEmpty()) { %>
            <p>선택한 기간에 판매된 상품이 없습니다.</p>
        <% } else { %>
            <div class="admin-table-scroll" tabindex="0" role="region" aria-label="상품 판매순위 표">
                <table class="admin-table">
                    <thead><tr><th scope="col">순위</th><th scope="col">상품 ID</th><th scope="col">상품명</th>
                        <th scope="col">판매수량</th><th scope="col">상품 매출</th></tr></thead>
                    <tbody>
                    <% for (Object[] sale : salesRows) { %>
                        <tr>
                            <td><%= sale[0] %></td>
                            <td><%= ProductView.escape(sale[1]) %></td>
                            <td><%= ProductView.escape(sale[2]) %></td>
                            <td><%= salesNumbers.format(sale[3]) %>개</td>
                            <td><%= salesNumbers.format(sale[4]) %>원</td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        <% } %>
    </main>
</div>
</body>
</html>
