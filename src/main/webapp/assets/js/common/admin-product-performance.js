(() => {
    const root = document.querySelector('[data-admin-query="product-performance"]');
    if (!root) return;

    const observers = [];
    const hiddenColumns = new Set();
    const colors = ['#2563eb', '#0d9488', '#8b5cf6', '#b45309', '#475569'];
    let scrollEvents;
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
        uncart: '장바구니 삭제', units: '순판매 수량', revenue: '현재 순상품 매출', previousRevenue: '직전 순상품 매출'
    };

    function prepareGraph(graph, days) {
        const canvas = graph.querySelector('canvas');
        const context = canvas.getContext('2d');
        if (!context) return;

        const keys = graph.dataset.performanceChart.split(',');
        const series = keys.map(key => days.map(day => day.dataset[key] === undefined ? null : Number(day.dataset[key])));
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
        const shown = keys.map(key => key !== 'unwish' && key !== 'uncart');
        keys.forEach((key, index) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = seriesLabels[key];
            button.style.setProperty('--series-color', colors[index]);
            button.dataset.dashed = String(index % 2 === 1);
            button.setAttribute('aria-pressed', String(shown[index]));
            button.addEventListener('click', () => {
                shown[index] = !shown[index];
                button.setAttribute('aria-pressed', String(shown[index]));
                updateTooltip();
                draw();
            });
            legend.append(button);
        });
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
            const points = series.filter((_, index) => shown[index]).flat().filter(value => value !== null);
            const minimum = Math.min(0, ...points);
            const step = Math.max(1, Math.ceil((Math.max(0, ...points) - minimum) / 3));
            const axis = value => number.format(value) + unit;
            left = context.measureText(axis(minimum + 3 * step)).width + 8;
            const right = width - 19;
            chartWidth = Math.max(1, right - left);
            const bottom = height - 22;
            const x = index => days.length === 1 ? left + chartWidth / 2 : left + chartWidth * index / (days.length - 1);
            const y = value => bottom - (bottom - 12) * (value - minimum) / (3 * step);
            context.textAlign = 'right';
            context.textBaseline = 'middle';
            for (let i = 0; i <= 3; i++) {
                context.strokeStyle = '#dfe2e7';
                context.fillStyle = '#46515e';
                context.fillText(axis(minimum + i * step), left - 5, y(minimum + i * step));
                context.beginPath();
                context.moveTo(left, y(minimum + i * step));
                context.lineTo(right, y(minimum + i * step));
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
                if (!shown[index]) continue;
                context.strokeStyle = context.fillStyle = colors[index];
                if (keys[index] === 'units') {
                    const bar = Math.max(1, Math.min(30, chartWidth / days.length * .65));
                    values.forEach((value, day) => context.fillRect(x(day) - bar / 2, y(value), bar, y(0) - y(value)));
                    continue;
                }
                context.lineWidth = 2;
                context.setLineDash(index % 2 ? [4, 3] : []);
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

        function updateTooltip() {
            tooltip.hidden = active < 0;
            if (active < 0) return;
            const values = series.flatMap((values, index) => {
                if (!shown[index]) return [];
                const date = keys[index] === 'previousRevenue' ? ' (' + days[active].dataset.previousDate + ')' : '';
                return [seriesLabels[keys[index]] + date + ' '
                    + (values[active] === null ? '수집 기록 없음' : number.format(values[active]) + unit)];
            });
            tooltip.textContent = [days[active].dataset.date, ...values].join(' · ');
        }

        function select(index) {
            if (index === active) return;
            active = index;
            updateTooltip();
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

    function prepareDonut() {
        const figure = root.querySelector('[data-performance-donut]');
        if (!figure) return;
        const rows = [...root.querySelectorAll('[data-category-revenue]')].map(row => ({
            value: Number(row.dataset.categoryRevenue),
            label: row.querySelector('[data-category-label]').textContent.trim()
        })).filter(row => row.value > 0);
        const total = rows.reduce((sum, row) => sum + row.value, 0);
        const segments = rows.slice(0, 5);
        if (rows.length > 5) segments.push({ label: '기타', value: rows.slice(5).reduce((sum, row) => sum + row.value, 0) });
        const ring = document.createElement('div');
        ring.className = 'performance-donut-ring';
        ring.setAttribute('aria-hidden', 'true');
        const legend = document.createElement('ul');
        let offset = 0;
        const stops = segments.map((row, index) => {
            const color = colors[index] || '#aab4c5';
            const start = offset;
            offset += row.value / total * 100;
            const label = document.createElement('li');
            label.textContent = row.label + ' · ' + number.format(row.value) + '원';
            label.style.setProperty('--series-color', color);
            legend.append(label);
            return color + ' ' + start + '% ' + offset + '%';
        });
        ring.style.background = 'conic-gradient(' + stops.join(',') + ')';
        figure.append(ring, legend);
    }

    function prepareTable(signal) {
        const scroll = root.querySelector('.performance-table-scroll');
        if (!scroll) return;
        const table = scroll.querySelector('table');
        const settings = root.querySelector('.performance-columns');
        const sticky = document.createElement('div');
        sticky.className = 'performance-sticky-head';
        sticky.setAttribute('aria-hidden', 'true');
        sticky.hidden = true;
        const mirror = document.createElement('table');
        mirror.className = table.className;
        sticky.append(mirror);
        scroll.before(sticky);

        function pin() {
            const box = scroll.getBoundingClientRect();
            sticky.hidden = box.top >= 0 || box.bottom <= table.tHead.offsetHeight;
            if (sticky.hidden) return;
            sticky.style.left = box.left + 'px';
            sticky.style.width = box.width + 'px';
            mirror.style.width = table.offsetWidth + 'px';
            const real = [...table.tHead.querySelectorAll('th')];
            [...mirror.tHead.querySelectorAll('th')].forEach((cell, index) => {
                cell.style.width = real[index].getBoundingClientRect().width + 'px';
            });
            sticky.scrollLeft = scroll.scrollLeft;
        }

        function apply() {
            for (const input of settings.querySelectorAll('[data-performance-column]')) {
                const index = Number(input.dataset.performanceColumn);
                input.checked = !hiddenColumns.has(index);
                table.querySelector('[data-performance-header="' + index + '"]').hidden = !input.checked;
                for (const row of table.querySelectorAll('[data-performance-product]')) row.cells[index - 1].hidden = !input.checked;
            }
            const groups = [...table.querySelectorAll('[scope="colgroup"]')];
            for (const [index, cells] of [[2, 3, 4], [5, 6], [7, 8, 9]].entries()) {
                const count = cells.filter(col => !hiddenColumns.has(col)).length;
                groups[index].hidden = count === 0;
                groups[index].colSpan = count || 1;
            }
            const header = table.tHead.cloneNode(true);
            for (const link of header.querySelectorAll('a')) link.tabIndex = -1;
            mirror.replaceChildren(header);
            pin();
        }

        settings.addEventListener('change', event => {
            const index = Number(event.target.dataset.performanceColumn);
            if (!index) return;
            if (event.target.checked) hiddenColumns.delete(index);
            else hiddenColumns.add(index);
            apply();
        });
        settings.querySelector('[data-performance-show-all]').addEventListener('click', () => {
            hiddenColumns.clear();
            apply();
        });
        window.addEventListener('scroll', pin, { signal, passive: true });
        window.addEventListener('resize', pin, { signal });
        scroll.addEventListener('scroll', pin, { signal, passive: true });
        apply();
    }

    function setup() {
        for (const observer of observers.splice(0)) observer.disconnect();
        scrollEvents?.abort();
        scrollEvents = new AbortController();
        prepareFilters();
        prepareTable(scrollEvents.signal);
        prepareDonut();
        const days = [...root.querySelectorAll('[data-performance-days] tbody tr')];
        if (!days.length) return;
        for (const graph of root.querySelectorAll('[data-performance-chart]')) prepareGraph(graph, days);
    }

    document.addEventListener('admin-query-render', setup);
    setup();
})();
