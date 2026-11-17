(() => {
    const carousel = document.querySelector('[data-home-carousel]');
    if (!carousel) return;

    const slides = Array.from(carousel.querySelectorAll('[data-home-slide]'));
    const controls = carousel.querySelector('.home-carousel-controls');
    const counter = carousel.querySelector('[data-home-counter]');
    const announcement = carousel.querySelector('[data-home-announcement]');
    const pause = carousel.querySelector('[data-home-pause]');
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let current = 0;
    let paused = motion.matches;

    function updatePause() {
        pause.textContent = paused ? '▶' : 'Ⅱ';
        pause.setAttribute('aria-label', paused ? '배너 자동 재생' : '배너 자동 재생 정지');
    }

    function move(direction, announce) {
        slides[current].hidden = true;
        current = (current + direction + slides.length) % slides.length;
        slides[current].hidden = false;
        counter.textContent = `${current + 1} / ${slides.length}`;
        if (announce) {
            announcement.textContent = `${current + 1}/${slides.length} ${slides[current].querySelector('h2').innerText}`;
        }
    }

    carousel.querySelector('[data-home-previous]').addEventListener('click', () => move(-1, true));
    carousel.querySelector('[data-home-next]').addEventListener('click', () => move(1, true));
    pause.addEventListener('click', () => {
        paused = !paused;
        updatePause();
    });
    motion.addEventListener('change', (event) => {
        if (event.matches) {
            paused = true;
            updatePause();
        }
    });
    window.setInterval(() => {
        if (!paused && !document.hidden && !carousel.matches(':hover, :focus-within')) {
            move(1, false);
        }
    }, 6500);
    updatePause();
    controls.hidden = false;
})();
