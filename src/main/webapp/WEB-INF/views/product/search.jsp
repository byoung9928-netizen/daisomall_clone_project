<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>

</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
        <main id="main-content" class="container shop-page" data-page="catalog" tabindex="-1">
        <nav class="breadcrumbs" aria-label="현재 위치">
        </nav>
        <h1 class="page-title">
            검색 결과
        </h1>
        <nav class="category-chips" aria-label="하위 카테고리">
        </nav>
        <div class="catalog-layout">
            <form class="catalog-filter">
                <h2>
                    상품 필터
                </h2>
                <label class="field">
                    최대 가격
                    <select name="maxPrice">
                        <option value="">
                            전체 가격
                        </option>
                        <option value="1000">
                            1,000원 이하
                        </option>
                        <option value="3000">
                            3,000원 이하
                        </option>
                        <option value="5000">
                            5,000원 이하
                        </option>
                    </select>
                </label>
                <label class="check-field">
                    <input type="checkbox" name="available">
                    <span>
                        구매 가능한 상품만
                    </span>
                </label>
                <button type="button" class="text-button" data-reset-filter>
                    필터 초기화
                </button>
            </form>
            <section class="catalog-products">
                <div class="catalog-toolbar">
                    <p class="result-count">
                    </p>
                    <label class="sort-label">
                        정렬
                        <select name="sort">
                            <option value="recommended">
                                추천순
                            </option>
                            <option value="newest">
                                신상품순
                            </option>
                            <option value="priceAsc">
                                가격낮은순
                            </option>
                            <option value="priceDesc">
                                가격높은순
                            </option>
                            <option value="rating">
                                별점순
                            </option>
                        </select>
                    </label>
                </div>
                <div class="product-grid catalog-grid">
                </div>
                <button type="button" class="button secondary load-more">
                    상품 더 보기
                </button>
            </section>
        </div>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { link, button } = app.ui;
        app.ready.then(function (repository) {
            app.ui.bindCatalog(root, repository);
        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
