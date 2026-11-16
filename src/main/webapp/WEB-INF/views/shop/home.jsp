<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,daiso.product.controller.ProductDTO,daiso.product.controller.ProductDAO.Category,daiso.product.controller.ProductView" %>
<%
    String context = request.getContextPath();
    List<Category> categories = (List<Category>) request.getAttribute("productCategories");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>

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
            <% String[] icons = {"₩", "✦", "⌂", "○", "▤", "✎"};
               int shortcutIndex = 0;
               for (Category category : categories) {
                   if (category.level == 1 && shortcutIndex < 10) { %>
                <a class="category-shortcut" data-category-id="<%= ProductView.escape(category.id) %>" href="<%= ProductView.url(context, "/list.jsp", Map.of("category", category.id)) %>">
                    <span class="shortcut-icon" aria-hidden="true"><%= icons[shortcutIndex++ % icons.length] %></span>
                    <span data-category-name><%= ProductView.escape(category.name) %></span>
                </a>
            <%     }
               } %>
        </nav>
        <div class="campaign-strip">
            <strong>
                필요한 순간, 가까운 생활
            </strong>
            <span>
                프로젝트 DB에 등록된 상품을 둘러보세요.
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
                <% for (ProductDTO item : (List<ProductDTO>) request.getAttribute("featuredProducts")) { %>
                    <%@ include file="../product/card.jspf" %>
                <% } %>
            </div>
            <a class="button secondary" href="<%= ProductView.url(context, "/list.jsp", Map.of("sort", "recommended", "collection", "ranking")) %>">상품 더 보기</a>
        </section>
        <section class="product-section" id="new-products">
            <p class="section-eyebrow">
                EVERYDAY PICKS
            </p>
            <h2 class="section-title">
                신상발견
            </h2>
            <div class="product-grid">
                <% for (ProductDTO item : (List<ProductDTO>) request.getAttribute("newProducts")) { %>
                    <%@ include file="../product/card.jspf" %>
                <% } %>
            </div>
            <a class="button secondary" href="<%= ProductView.url(context, "/list.jsp", Map.of("sort", "newest", "collection", "new")) %>">상품 더 보기</a>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
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
    });
    </script>
</body>
</html>
