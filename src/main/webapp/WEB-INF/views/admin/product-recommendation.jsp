<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductView,daiso.product.service.RecommendationService.*" %>
<% Diagnostic diagnostic = (Diagnostic) request.getAttribute("recommendationDiagnostic");
   java.text.NumberFormat score = java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA);
   score.setMaximumFractionDigits(3);
   String contextPath = request.getContextPath(); %>
<!DOCTYPE html>
<html lang="ko">
<head><%@ include file="../common/admin-head.jspf" %></head>
<body class="admin-body">
<%@ include file="../common/admin-header.jspf" %>
<div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
    <main id="admin-content" class="admin-content">
        <h1 class="page-title">시연 회원 추천 분석</h1>
        <p>시연 회원의 저장된 행동 기록으로 현재 홈페이지 추천을 계산합니다.</p>
        <p><a href="<%= contextPath %>/app/admin/products">상품 관리로 돌아가기</a></p>
        <form method="get" class="admin-search" action="<%= contextPath %>/app/admin/products/recommendation">
            <label>시연 회원 ID <input name="member" placeholder="DEMO_RANK_V1_M0001"
                    pattern="DEMO_RANK_V1_M[0-9]{4}" maxlength="18" required></label>
            <button type="submit" class="button primary">분석</button>
        </form>
        <% if (request.getAttribute("analysisError") != null) { %>
            <p role="alert"><%= ProductView.escape(request.getAttribute("analysisError")) %></p>
        <% } %>
        <% if (diagnostic != null) { %>
            <h2>추천에 사용한 행동 점수</h2>
            <% if (diagnostic.signals.isEmpty()) { %><p>현재 추천 기간에 해당하는 행동 기록이 없습니다.</p><% } else { %>
                <div class="admin-table-scroll" tabindex="0" aria-label="행동 점수" role="region">
                    <table class="admin-table"><thead><tr><th>상품 ID</th><th>행동</th><th>최근 활동일</th>
                        <th>기본 점수</th><th>시간 가중치</th><th>반영 점수</th></tr></thead><tbody>
                        <% for (SignalScore entry : diagnostic.signals) { %>
                            <tr><td><%= ProductView.escape(entry.id) %></td><td><%= entry.kind %></td>
                                <td><%= entry.date == null ? "기록 없음" : entry.date %></td><td><%= entry.base %></td>
                                <td><%= score.format(entry.weight) %></td><td><%= score.format(entry.score) %></td></tr>
                        <% } %>
                    </tbody></table>
                </div>
            <% } %>
            <h2>관심 카테고리 분포</h2>
            <% if (diagnostic.categories.isEmpty()) { %><p>집계할 관심 카테고리가 없습니다.</p><% } else { %>
                <ul><% for (java.util.Map.Entry<String, Double> category : diagnostic.categories.entrySet()) { %>
                    <li><%= ProductView.escape(category.getKey()) %> <strong><%= score.format(category.getValue()) %></strong></li>
                <% } %></ul>
            <% } %>
            <h2>추천 후보 및 결과</h2>
            <% if (diagnostic.candidates.isEmpty()) { %><p>추천 가능한 상품이 없습니다.</p><% } else {
                   java.util.List<String> ranking = java.util.List.copyOf(diagnostic.result.keySet()); %>
                <div class="admin-table-scroll" tabindex="0" aria-label="추천 후보 및 결과" role="region">
                    <table class="admin-table"><thead><tr><th>상품</th><th>최종 점수</th><th>추천 순위</th><th>추천 이유</th></tr></thead><tbody>
                        <% for (Candidate entry : diagnostic.candidates) { %>
                            <tr><td><%= ProductView.escape(entry.name) %> (<%= ProductView.escape(entry.id) %>)</td>
                                <td><%= score.format(entry.score) %></td>
                                <td><%= entry.recommended ? ranking.indexOf(entry.id) + 1 : "후보" %></td>
                                <td><%= entry.reason.isEmpty() ? "—" : ProductView.escape(entry.reason) %></td></tr>
                        <% } %>
                    </tbody></table>
                </div>
            <% } %>
        <% } %>
    </main>
</div>
</body></html>
