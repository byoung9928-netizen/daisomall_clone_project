<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<% String shopGuide = (String) request.getAttribute("shopGuide"); %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/shop-guide.css">
</head>
<body>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>

    <main class="shop-guide">
        <nav class="container shop-guide-nav" aria-label="쇼핑 안내">
            <a href="${pageContext.request.contextPath}/app/guides/delivery"
               <%= "delivery".equals(shopGuide) ? "aria-current=\"page\"" : "" %>>다이소배송</a>
            <a href="${pageContext.request.contextPath}/app/guides/events"
               <%= "events".equals(shopGuide) ? "aria-current=\"page\"" : "" %>>이벤트</a>
            <a href="${pageContext.request.contextPath}/app/guides/partner-card"
               <%= "partner-card".equals(shopGuide) ? "aria-current=\"page\"" : "" %>>제휴카드</a>
        </nav>

        <% if ("delivery".equals(shopGuide)) { %>
            <div class="container">
                <header class="shop-guide-hero shop-guide-delivery">
                    <div>
                        <h1>다이소배송</h1>
                        <p>집에서 받거나 가까운 매장에서 찾아가세요.</p>
                    </div>
                    <img src="${pageContext.request.contextPath}/assets/images/home/quick-delivery.png"
                         alt="" width="104" height="104">
                </header>

                <section aria-labelledby="shop-guide-methods">
                    <h2 id="shop-guide-methods">배송 방법 한눈에 보기</h2>
                    <div class="shop-guide-grid">
                        <article class="shop-guide-item">
                            <h3>택배배송</h3>
                            <p>상품 합계 3만원 미만은 배송비 3천원, 3만원 이상은 무료예요.</p>
                            <a class="shop-guide-link"
                               href="${pageContext.request.contextPath}/app/products">상품 둘러보기</a>
                        </article>
                        <article class="shop-guide-item">
                            <h3>매장픽업</h3>
                            <p>픽업 가능한 상품을 주문하고 선택한 매장에서 받으세요. 픽업 수수료는 200원이에요.</p>
                            <a class="shop-guide-link"
                               href="${pageContext.request.contextPath}/app/stores/products">매장 상품 찾기</a>
                        </article>
                    </div>
                </section>

                <section aria-labelledby="shop-guide-pickup">
                    <h2 id="shop-guide-pickup">매장픽업 이용 순서</h2>
                    <ol class="shop-guide-steps">
                        <li>상품과 수령 매장을 고르세요.</li>
                        <li>수령인 정보를 확인하고 주문하세요.</li>
                        <li>준비가 끝나면 선택한 매장에서 받아 가세요.</li>
                    </ol>
                    <a class="shop-guide-link"
                       href="${pageContext.request.contextPath}/app/support/faq">배송·픽업 FAQ 보기</a>
                </section>
            </div>

        <% } else if ("events".equals(shopGuide)) { %>
            <div class="container">
                <header class="shop-guide-hero shop-guide-events">
                    <div>
                        <h1>이벤트</h1>
                        <p>시즌 테마별로 상품을 둘러보세요.</p>
                    </div>
                    <img src="${pageContext.request.contextPath}/assets/images/home/quick-event.png"
                         alt="" width="104" height="104">
                </header>

                <section aria-labelledby="shop-guide-themes">
                    <h2 id="shop-guide-themes">시즌 기획전</h2>
                    <div class="shop-guide-grid shop-guide-grid-three">
                        <a class="shop-guide-tile"
                           href="${pageContext.request.contextPath}/search.jsp?q=크리스마스">
                            <img src="${pageContext.request.contextPath}/assets/images/home/quick-christmas.png"
                                 alt="" width="104" height="104">
                            <h3>크리스마스 마켓</h3>
                            <p>트리 장식과 선물용품 찾아보기</p>
                        </a>
                        <a class="shop-guide-tile"
                           href="${pageContext.request.contextPath}/search.jsp?q=겨울">
                            <img src="${pageContext.request.contextPath}/assets/images/home/quick-winter.png"
                                 alt="" width="104" height="104">
                            <h3>겨울 시즌템</h3>
                            <p>추운 날에 필요한 생활용품 찾아보기</p>
                        </a>
                        <a class="shop-guide-tile"
                           href="${pageContext.request.contextPath}/list.jsp?collection=new">
                            <img src="${pageContext.request.contextPath}/assets/images/home/quick-new-club.png"
                                 alt="" width="104" height="104">
                            <h3>신상클럽</h3>
                            <p>새로 들어온 상품 둘러보기</p>
                        </a>
                    </div>
                </section>
            </div>

        <% } else { %>
            <div class="container">
                <header class="shop-guide-hero shop-guide-partner">
                    <div>
                        <h1>제휴카드 안내</h1>
                        <p>카드 혜택을 살펴볼 때 확인해야 할 조건을 모았어요.</p>
                    </div>
                    <img src="${pageContext.request.contextPath}/assets/images/home/quick-card.png"
                         alt="" width="104" height="104">
                </header>

                <section aria-labelledby="shop-guide-card-terms">
                    <h2 id="shop-guide-card-terms">혜택 확인 항목</h2>
                    <dl class="shop-guide-terms">
                        <div>
                            <dt>적용 대상</dt>
                            <dd>온라인 주문과 오프라인 매장 결제 중 어디에 적용되는지 확인하세요.</dd>
                        </div>
                        <div>
                            <dt>할인 조건</dt>
                            <dd>최소 결제 금액, 전월 이용 실적, 할인 한도를 확인하세요.</dd>
                        </div>
                        <div>
                            <dt>비용과 기간</dt>
                            <dd>연회비와 행사 기간은 카드사 공식 상품 안내에서 확인하세요.</dd>
                        </div>
                    </dl>
                    <a class="shop-guide-link"
                       href="${pageContext.request.contextPath}/app/guides/events">시즌 기획전 보기</a>
                </section>
            </div>
        <% } %>
    </main>

    <%@ include file="../common/footer.jspf" %>
</body>
</html>