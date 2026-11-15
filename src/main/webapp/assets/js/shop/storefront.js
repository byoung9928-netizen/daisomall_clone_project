(() => {
    "use strict";

    const root = document.querySelector('[data-page="home"]');
    if (!root) {
        return;
    }
    const app = window.Daiso;
    const { productCard } = app.ui;

    app.ready.then((repository) => {
        root.replaceChildren();
        const promotions = app.ui.view("storefront-promotion-grid");
        const hero = app.ui.part(promotions, "storefront-hero-carousel");
        const slide = app.ui.part(hero, "storefront-hero-slide");
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
        ["겨울 생활 준비", "나를 위한 뷰티 루틴", "한 칸의 정리 습관", "오늘도 즐거운 문구 생활"].forEach((title, index) => {
            const tile = app.ui.view("promotion-tile", undefined, "promotion-tile tile-" + index);
            tile.href = app.url("list.jsp", { collection: "exhibition", sort: index % 2 ? "newest" : "rating" });
            tile.querySelector("h2").textContent = title;
            tile.querySelector("img").src = app.url("assets/images/mock-product-" + (index + 2) % 6 + ".svg");
            promotions.append(tile);
        });
        root.append(promotions);
        const shortcuts = app.ui.view("storefront-category-shortcuts");
        repository.getVisibleCategories().filter((category) => category.level === 1).sort((left, right) => left.displayOrder - right.displayOrder).slice(0, 10).forEach((category, index) => {
            const shortcut = app.ui.view("category-shortcut");
            shortcut.href = app.url("list.jsp", { category: category.id });
            shortcut.dataset.categoryId = category.id;
            shortcut.querySelector(".shortcut-icon").textContent = ["₩", "✦", "⌂", "○", "▤", "✎"][index % 6];
            shortcut.querySelector("[data-category-name]").textContent = category.name;
            shortcuts.append(shortcut);
        });
        root.append(shortcuts);
        const strip = app.ui.view("storefront-campaign-strip");
        root.append(strip);
        ["오늘의 추천 상품", "일상에 더하는 작은 발견"].forEach((title, index) => {
            const section = app.ui.view("storefront-product-section");
            if (index === 0) {
                section.id = "featured";
            }
            section.querySelector(".section-title").textContent = title;
            const grid = section.querySelector(".product-grid");
            repository.queryProducts().slice(index * 6, index * 6 + 6).forEach((product) => grid.append(productCard(product, { details: true })));
            root.append(section);
        });
    }).catch((error) => app.ui.error(root, error));
})();
