(() => {
    const cleanups = new WeakMap();
    const selector = "[data-featured-carousel]";

    function initialize(carousel) {
        if (cleanups.has(carousel)) return;
        const viewport = carousel.querySelector(".featured-viewport");
        const track = carousel.querySelector(".featured-track");
        const cards = Array.from(track.children);
        const prev = carousel.querySelector("[data-featured-prev]");
        const next = carousel.querySelector("[data-featured-next]");
        const status = carousel.querySelector("[data-featured-status]");
        const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
        const ranked = carousel.hasAttribute("data-ranked-carousel");

        let windowSize = 0;
        let page = 0;
        let stops = [0];
        let moving = false;
        let timer;
        let touchStart;

        function updateControls() {
            next.hidden = stops.length < 2;
            next.setAttribute("aria-disabled", String(page === stops.length - 1));
            if (page === 0 && document.activeElement === prev) next.focus({ preventScroll: true });
            prev.hidden = page === 0;
        }

        function updateFocus() {
            const first = stops[page];
            const activeCard = document.activeElement?.closest(".product-card");
            const activeIndex = cards.findIndex(card => card.contains(activeCard));
            if (activeIndex !== -1 && (activeIndex < first || activeIndex >= first + windowSize)) {
                (next.hidden ? prev : next).focus({ preventScroll: true });
            }
            cards.forEach((card, index) => { card.inert = index < first || index >= first + windowSize; });
        }

        function placeTrack() {
            const distance = cards[stops[page]].offsetLeft - cards[0].offsetLeft;
            track.style.transform = "translateX(" + -distance + "px)";
        }

        function finishMove() {
            if (!moving) return;
            moving = false;
            clearTimeout(timer);
            carousel.classList.remove("is-sliding");
            updateFocus();
            const first = stops[page];
            status.textContent = (first + 1) + "–" + Math.min(first + windowSize, cards.length)
                + "번째 상품, 전체 " + cards.length + "개";
        }

        function move(direction) {
            const target = page + direction;
            if (moving || target < 0 || target >= stops.length) return;
            page = target;
            moving = true;
            if (!motion.matches) carousel.classList.add("is-sliding");
            updateControls();
            placeTrack();
            if (motion.matches) {
                finishMove();
            } else {
                timer = window.setTimeout(finishMove, 410);
            }
        }

        function resize() {
            if (moving) finishMove();
            const first = stops[page];
            const columns = Number(getComputedStyle(carousel).getPropertyValue("--featured-visible"));
            windowSize = ranked ? columns * 2 : columns;
            stops = [];
            if (ranked) {
                cards.forEach((card, index) => {
                    const withinPage = index % windowSize;
                    card.style.gridColumnStart = Math.floor(index / windowSize) * columns + (withinPage % columns) + 1;
                    card.style.gridRowStart = Math.floor(withinPage / columns) + 1;
                    if (withinPage === 0) stops.push(index);
                });
            } else {
                const last = Math.max(0, cards.length - columns);
                for (let index = 0; index < last; index += Math.max(1, columns - 1)) stops.push(index);
                stops.push(last);
            }
            const later = stops.findIndex(index => index > first);
            page = later === -1 ? stops.length - 1 : later - 1;
            carousel.style.setProperty("--featured-arrow-top", cards[0].querySelector(".product-cover").offsetHeight / 2 + "px");
            updateControls();
            placeTrack();
            updateFocus();
        }

        prev.addEventListener("click", () => move(-1));
        next.addEventListener("click", () => move(1));
        track.addEventListener("transitionend", event => {
            if (event.target === track && event.propertyName === "transform") finishMove();
        });
        carousel.addEventListener("keydown", event => {
            if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
            if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
                event.preventDefault();
                move(event.key === "ArrowLeft" ? -1 : 1);
            }
        });
        viewport.addEventListener("touchstart", event => {
            touchStart = event.touches.length === 1 ? event.touches[0] : null;
        }, { passive: true });
        viewport.addEventListener("touchcancel", () => { touchStart = null; });
        viewport.addEventListener("touchend", event => {
            if (!touchStart) return;
            const start = touchStart;
            touchStart = null;
            if (event.touches.length) return;
            const end = event.changedTouches[0];
            const x = end.clientX - start.clientX;
            const y = end.clientY - start.clientY;
            if (Math.abs(x) < 48 || Math.abs(x) <= Math.abs(y) * 1.3) return;
            if (event.cancelable) event.preventDefault();
            move(x > 0 ? -1 : 1);
        });

        carousel.classList.add("is-ready");
        resize();
        const observer = new ResizeObserver(resize);
        observer.observe(viewport);
        cleanups.set(carousel, () => {
            observer.disconnect();
            clearTimeout(timer);
        });
    }

    window.Daiso.ui.initProductCarousels = root => root.querySelectorAll(selector).forEach(initialize);
    window.Daiso.ui.disposeProductCarousels = root => {
        root.querySelectorAll(selector).forEach(carousel => cleanups.get(carousel)?.());
    };
    window.Daiso.ui.initProductCarousels(document);
})();
