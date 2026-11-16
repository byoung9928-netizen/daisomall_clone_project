<% request.setAttribute("activeNavigation", "home"); %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>

</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <% if ("success".equals(request.getParameter("logout"))) { %>
<script>
document.addEventListener("DOMContentLoaded", function () {
    var toast = document.getElementById("common-feature-status");

    if (!toast) {
        return;
    }

    toast.textContent = "로그아웃 되었습니다.";
    toast.classList.add("logout-toast");

    window.setTimeout(function () {
        toast.textContent = "";
        toast.classList.remove("logout-toast");
    }, 3000);

    window.history.replaceState(null, "", location.pathname);
});
</script>
<% } %>
    <%@ include file="../common/navigation.jspf" %>
        <main id="main-content" class="container storefront" data-page="home" tabindex="-1">
        <section class="promotion-grid" aria-label="이번 주 기획전">
            <div class="hero-carousel" aria-roledescription="캐러셀" aria-label="메인 기획전">
                <div class="hero-slide">
                    <p class="hero-eyebrow">
                    </p>
                    <h1 class="hero-title">
                    </h1>
                    <p class="hero-description">
                    </p>
                    <img class="hero-illustration" alt="">
                </div>
                <div class="carousel-controls">
                    <span class="carousel-counter">
                    </span>
                    <button type="button" class="carousel-control" data-carousel-previous>
                        이전
                    </button>
                    <button type="button" class="carousel-control" data-carousel-next>
                        다음
                    </button>
                    <button type="button" class="carousel-control" data-carousel-pause>
                    </button>
                </div>
            </div>
            <a class="promotion-tile tile-0" href="${pageContext.request.contextPath}/list.jsp?collection=exhibition&amp;sort=rating">
                <span class="eyebrow">
                    DAILY SELECTION
                </span>
                <h2>
                    겨울 생활 준비
                </h2>
                <span class="tile-caption">
                    생활 속 작은 발견 →
                </span>
                <img alt="" src="${pageContext.request.contextPath}/assets/images/mock-product-2.svg">
            </a>
            <a class="promotion-tile tile-1" href="${pageContext.request.contextPath}/list.jsp?collection=exhibition&amp;sort=newest">
                <span class="eyebrow">
                    DAILY SELECTION
                </span>
                <h2>
                    나를 위한 뷰티 루틴
                </h2>
                <span class="tile-caption">
                    생활 속 작은 발견 →
                </span>
                <img alt="" src="${pageContext.request.contextPath}/assets/images/mock-product-3.svg">
            </a>
            <a class="promotion-tile tile-2" href="${pageContext.request.contextPath}/list.jsp?collection=exhibition&amp;sort=rating">
                <span class="eyebrow">
                    DAILY SELECTION
                </span>
                <h2>
                    한 칸의 정리 습관
                </h2>
                <span class="tile-caption">
                    생활 속 작은 발견 →
                </span>
                <img alt="" src="${pageContext.request.contextPath}/assets/images/mock-product-4.svg">
            </a>
            <a class="promotion-tile tile-3" href="${pageContext.request.contextPath}/list.jsp?collection=exhibition&amp;sort=newest">
                <span class="eyebrow">
                    DAILY SELECTION
                </span>
                <h2>
                    오늘도 즐거운 문구 생활
                </h2>
                <span class="tile-caption">
                    생활 속 작은 발견 →
                </span>
                <img alt="" src="${pageContext.request.contextPath}/assets/images/mock-product-5.svg">
            </a>
        </section>
        <nav class="category-shortcuts" aria-label="주요 카테고리 바로가기">
        </nav>
        <div class="campaign-strip">
            <strong>
                필요한 순간, 가까운 생활
            </strong>
            <span>
                자체 제작 이미지와 Mock 상품으로 구성한 학습용 쇼핑몰입니다.
            </span>
        </div>
        <section class="product-section" id="featured">
            <p class="section-eyebrow">
                EVERYDAY PICKS
            </p>
            <h2 class="section-title">
                오늘의 추천 상품
            </h2>
            <div class="product-grid">
            </div>
        </section>
        <section class="product-section">
            <p class="section-eyebrow">
                EVERYDAY PICKS
            </p>
            <h2 class="section-title">
                일상에 더하는 작은 발견
            </h2>
            <div class="product-grid">
            </div>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { element, link, button, productCard } = app.ui;
        app.ready.then(function (repository) {
        const promotions = root.querySelector(".promotion-grid");
        const hero = promotions.querySelector(".hero-carousel");
        const slide = hero.querySelector(".hero-slide");
        const slides = [
            { title: "따뜻한 일상을\n함께 준비해요", subtitle: "WINTER TOGETHER", color: "winter", description: "차곡차곡 채우는 나만의 겨울 생활" },
            { title: "작은 정리로\n달라지는 우리 집", subtitle: "LIVING ESSENTIALS", color: "living", description: "공간을 가볍게, 일상을 편안하게" },
            { title: "매일의 새로운\n발견을 만나보세요", subtitle: "EVERYDAY DISCOVERY", color: "beauty", description: "생활에 더하는 작고 기분 좋은 변화" },
        ];
        let current = 0;
        let paused = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        let hovering = false;
        const counter = hero.querySelector(".carousel-counter");
        const pause = hero.querySelector("[data-carousel-pause]");
        pause.addEventListener("click", () => {
            paused = !paused;
            updatePause();
        });
        function updatePause() {
            pause.textContent = paused ? "재생" : "정지";
            pause.setAttribute("aria-label", paused ? "배너 자동 재생" : "배너 자동 재생 정지");
        }
        function renderSlide() {
            const data = slides[current];
            slide.className = "hero-slide " + data.color;
            slide.querySelector(".hero-eyebrow").textContent = data.subtitle;
            slide.querySelector(".hero-title").textContent = data.title;
            slide.querySelector(".hero-description").textContent = data.description;
            slide.querySelector("img").src = app.url("assets/images/mock-product-" + current + ".svg");
            counter.textContent = current + 1 + " / " + slides.length;
        }
        function move(direction) {
            current = (current + direction + slides.length) % slides.length;
            renderSlide();
        }
        hero.querySelector("[data-carousel-previous]").addEventListener("click", () => move(-1));
        hero.querySelector("[data-carousel-next]").addEventListener("click", () => move(1));
        hero.addEventListener("pointerenter", () => {
            hovering = true;
        });
        hero.addEventListener("pointerleave", () => {
            hovering = false;
        });
        const timer = setInterval(() => {
            if (!paused && !hovering && !document.hidden && !hero.contains(document.activeElement)) {
                move(1);
            }
        }, 6000);
        window.addEventListener("pagehide", () => clearInterval(timer), { once: true });
        updatePause();
        renderSlide();
        const shortcuts = root.querySelector(".category-shortcuts");
        repository.getVisibleCategories().filter((category) => category.level === 1).sort((left, right) => left.displayOrder - right.displayOrder).slice(0, 10).forEach((category, index) => {
            const shortcut = element("a", "category-shortcut");
            const icon = element("span", "shortcut-icon");
            const name = element("span");
            name.dataset.categoryName = "";
            shortcut.append(icon, name);
            shortcut.href = app.url("list.jsp", { category: category.id });
            shortcut.dataset.categoryId = category.id;
            shortcut.querySelector(".shortcut-icon").textContent = ["₩", "✦", "⌂", "○", "▤", "✎"][index % 6];
            shortcut.querySelector("[data-category-name]").textContent = category.name;
            shortcuts.append(shortcut);
        });
        root.querySelectorAll(".product-section").forEach((section, index) => {
            const grid = section.querySelector(".product-grid");
            repository.queryProducts().slice(index * 6, index * 6 + 6).forEach((product) => grid.append(productCard(product, { details: true })));
        });

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
