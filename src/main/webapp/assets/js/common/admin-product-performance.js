(() => {
    const root = document.querySelector('[data-admin-query="product-performance"]');
    if (!root) return;

    const observers = [];
    const number = new Intl.NumberFormat('ko-KR');

    function prepareFilters() {
        const form = root.querySelector('[data-performance-filters]');
        if (!form) return;
        const { period, start, end } = form.elements;

        function syncDates() {
            const custom = period.value === 'CUSTOM';
            for (const field of [start, end]) {
                field.disabled = !custom;
                field.required = custom;
                field.closest('[data-performance-date]').hidden = !custom;
            }
            start.max = end.value;
            end.min = start.value;
        }

        form.addEventListener('change', event => {
            syncDates();
            if (period.value !== 'CUSTOM') return;
            if (event.target === period) {
                event.stopPropagation();
                start.focus();
            } else if (event.target === start || event.target === end) {
                if (!form.checkValidity()) event.stopPropagation();
            }
        });
        syncDates();
    }

    const seriesLabels = {
        views: '조회', wish: '찜 등록', unwish: '찜 취소', cart: '장바구니 등록',
        uncart: '장바구니 삭제', units: '순판매 수량', revenue: '순상품 매출'
    };

    function prepareGraph(graph, days) {
        const canvas = graph.querySelector('canvas');
        const context = canvas.getContext('2d');
        if (!context) return;

        const keys = graph.dataset.performanceChart.split(',');
        const series = keys.map(key => days.map(day => day.dataset[key] === undefined ? null : Number(day.dataset[key])));
        const peak = Math.max(0, ...series.flat());
        const step = Math.max(1, Math.ceil(peak / 3));
        const unit = keys[0] === 'revenue' ? '원' : keys[0] === 'units' ? '개' : '건';
        const range = graph.querySelector('[data-chart-range]');
        range.textContent = days.length === 1 ? days[0].dataset.date
            : days[0].dataset.date + ' ~ ' + days[days.length - 1].dataset.date;
        const plot = document.createElement('div');
        plot.className = 'performance-chart-plot';
        canvas.before(plot);
        plot.append(canvas);
        const tooltip = document.createElement('div');
        tooltip.className = 'performance-chart-tooltip';
        tooltip.setAttribute('aria-live', 'polite');
        tooltip.hidden = true;
        plot.append(tooltip);
        canvas.tabIndex = 0;
        canvas.removeAttribute('aria-hidden');
        canvas.setAttribute('aria-label', graph.querySelector('figcaption').textContent + ' 그래프. 방향키로 날짜별 수치를 확인할 수 있습니다.');
        const legend = document.createElement('div');
        legend.className = 'performance-chart-legend';
        for (const key of keys) {
            const label = document.createElement('span');
            label.textContent = seriesLabels[key];
            legend.append(label);
        }
        range.before(legend);

        let active = -1;
        let left = 0;
        let chartWidth = 0;

        function draw() {
            const width = canvas.clientWidth;
            const height = canvas.clientHeight;
            if (width < 60 || !height) return;
            const ratio = window.devicePixelRatio || 1;
            canvas.width = Math.round(width * ratio);
            canvas.height = Math.round(height * ratio);
            context.scale(ratio, ratio);
            context.font = '11px sans-serif';
            const axis = value => number.format(value) + unit;
            left = context.measureText(axis(3 * step)).width + 8;
            const right = width - 19;
            chartWidth = Math.max(1, right - left);
            const bottom = height - 22;
            const x = index => days.length === 1 ? left + chartWidth / 2 : left + chartWidth * index / (days.length - 1);
            const y = value => bottom - (bottom - 12) * value / (3 * step);
            context.textAlign = 'right';
            context.textBaseline = 'middle';
            for (let i = 0; i <= 3; i++) {
                context.strokeStyle = '#dfe2e7';
                context.fillStyle = '#46515e';
                context.fillText(axis(i * step), left - 5, y(i * step));
                context.beginPath();
                context.moveTo(left, y(i * step));
                context.lineTo(right, y(i * step));
                context.stroke();
            }
            context.textAlign = 'center';
            const tickCount = Math.min(days.length, Math.max(2, Math.floor(chartWidth / 54) + 1));
            for (let tick = 0; tick < tickCount; tick++) {
                const index = tickCount === 1 ? 0 : Math.round(tick * (days.length - 1) / (tickCount - 1));
                const label = days[index].dataset.date.slice(5).replace('-', '.');
                context.fillText(label, x(index), bottom + 12);
            }
            for (const [index, values] of series.entries()) {
                context.strokeStyle = context.fillStyle = index === 0 ? '#116c71' : '#943b63';
                context.lineWidth = 2;
                context.setLineDash(index === 0 ? [] : [4, 3]);
                context.beginPath();
                let connected = false;
                for (const [day, value] of values.entries()) {
                    if (value === null) {
                        connected = false;
                        continue;
                    }
                    if (connected) context.lineTo(x(day), y(value));
                    else context.moveTo(x(day), y(value));
                    connected = true;
                }
                context.stroke();
                context.setLineDash([]);
                for (const [day, value] of values.entries()) {
                    const isolated = values[day - 1] == null && values[day + 1] == null;
                    if (value === null || (day !== active && !isolated)) continue;
                    context.beginPath();
                    context.arc(x(day), y(value), 3, 0, Math.PI * 2);
                    context.fill();
                }
            }
        }

        function select(index) {
            if (index === active) return;
            active = index;
            tooltip.hidden = index < 0;
            if (index >= 0) {
                const values = series.map((values, row) => seriesLabels[keys[row]] + ' '
                    + (values[index] === null ? '수집 기록 없음' : number.format(values[index]) + unit));
                tooltip.textContent = [days[index].dataset.date, ...values].join(' · ');
            }
            draw();
        }

        function point(event) {
            if (!chartWidth) return;
            const offset = event.clientX - canvas.getBoundingClientRect().left - left;
            select(Math.max(0, Math.min(days.length - 1, Math.round(offset * (days.length - 1) / chartWidth))));
        }

        canvas.addEventListener('pointermove', event => { if (event.pointerType !== 'touch') point(event); });
        canvas.addEventListener('pointerdown', point);
        canvas.addEventListener('pointerleave', event => {
            if (event.pointerType !== 'touch' && document.activeElement !== canvas) select(-1);
        });
        canvas.addEventListener('blur', () => select(-1));
        canvas.addEventListener('keydown', event => {
            if (event.key === 'Escape') select(-1);
            if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
            event.preventDefault();
            select(Math.max(0, Math.min(days.length - 1, active < 0 ? 0
                : active + (event.key === 'ArrowLeft' ? -1 : 1))));
        });
        const observer = new ResizeObserver(draw);
        observers.push(observer);
        observer.observe(canvas);
        draw();
    }

    function setup() {
        for (const observer of observers.splice(0)) observer.disconnect();
        prepareFilters();
        const days = [...root.querySelectorAll('[data-performance-days] tbody tr')];
        if (!days.length) return;
        for (const graph of root.querySelectorAll('[data-performance-chart]')) prepareGraph(graph, days);
    }

    document.addEventListener('admin-query-render', setup);
    setup();
})();
