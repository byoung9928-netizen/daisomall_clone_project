<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" tabindex="-1">
            <h1 class="page-title">운영 대시보드</h1>

            <% if (Boolean.TRUE.equals(request.getAttribute("dashboardLoadFailed"))) { %>
                <p class="error-message" role="alert">
                    대시보드 통계를 불러오지 못했습니다. 잠시 후 새로고침해 주세요.
                </p>
            <% } else { %>
                <div class="admin-stats">
                    <section class="admin-stat">
                        <h2>상품</h2>
                        <strong>${dashboardProductCount}개</strong>
                    </section>
                    <section class="admin-stat">
                        <h2>카테고리</h2>
                        <strong>${dashboardCategoryCount}개</strong>
                    </section>
                    <section class="admin-stat">
                        <h2>전체 주문</h2>
                        <strong>${dashboardOrderCount}건</strong>
                    </section>
                    <section class="admin-stat">
                        <h2>결제된 주문 금액</h2>
                        <strong>${dashboardPaidAmount}원</strong>
                    </section>
                </div>
                <section class="admin-panel" aria-labelledby="dashboard-tasks-title">
    <h2 id="dashboard-tasks-title">처리할 일</h2>
    <div class="admin-stats">
        <div class="admin-stat">
            <h3>발송 대기 주문</h3>
            <strong>${dashboardAwaitingShipmentCount}건</strong>
						<p>
							<a
								href="${pageContext.request.contextPath}/app/admin/orders?filter=awaiting-shipment">
								발송 대기 주문 보기 </a>
						</p>
					</div>
        <div class="admin-stat">
            <h3>온라인 품절 상품</h3>
            <strong>${dashboardOnlineSoldOutCount}종</strong>
            <p><a href="${pageContext.request.contextPath}/app/admin/inventory?state=SOLD_OUT">
                품절 상품 보기
            </a></p>
        </div>
    </div>
</section>
            <% } %>
        </main>
    </div>
</body>
</html>