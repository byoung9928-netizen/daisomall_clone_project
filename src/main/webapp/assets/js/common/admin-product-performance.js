(() => {
    const root = document.querySelector('[data-admin-query="product-performance"]');
    if (!root) return;

    let observer;
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

    function draw(graph, days) {
        const canvas = graph.querySelector('canvas');
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        if (!width || !height) return;
        const ratio = window.devicePixelRatio || 1;
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        const context = canvas.getContext('2d');
        if (!context) return;
        context.scale(ratio, ratio);

        const keys = graph.dataset.performanceChart.split(',');
        const counts = keys.map(key => days.map(day => day.dataset[key] === undefined ? null : Number(day.dataset[key])));
        const peak = counts.reduce((max, series) => series.reduce((largest, value) => Math.max(largest, value ?? 0), max), 0);
        const padding = 6;
        const chartWidth = width - padding * 2;
        const chartHeight = height - padding * 2;
        const bottom = height - padding;
        context.strokeStyle = '#dfe2e7';
        for (const fraction of [0, .5, 1]) {
            context.beginPath();
            context.moveTo(padding, bottom - chartHeight * fraction);
            context.lineTo(width - padding, bottom - chartHeight * fraction);
            context.stroke();
        }

        const colors = ['#146d74', '#9b385c'];
        for (const [index, series] of counts.entries()) {
            context.strokeStyle = context.fillStyle = colors[index];
            context.setLineDash(index === 0 ? [] : [4, 3]);
            context.lineWidth = 2;
            context.beginPath();
            let points = 0;
            let x;
            let y;
            for (const [day, value] of series.entries()) {
                if (value === null) continue;
                x = days.length === 1 ? width / 2 : padding + chartWidth * day / (days.length - 1);
                y = bottom - chartHeight * value / Math.max(1, peak);
                if (points++ === 0) context.moveTo(x, y);
                else context.lineTo(x, y);
            }
            context.stroke();
            if (points === 1) {
                context.beginPath();
                context.arc(x, y, 3, 0, Math.PI * 2);
                context.fill();
            }
        }
        const first = days[0].dataset.date.slice(5);
        const last = days[days.length - 1].dataset.date.slice(5);
        const legend = keys.length === 2 ? ' · 실선 등록 / 점선 ' + (keys[0] === 'wish' ? '취소' : '삭제') : '';
        graph.querySelector('[data-chart-range]').textContent = (first === last ? first : first + ' ~ ' + last)
            + ' · 최대 ' + number.format(peak) + legend;
    }

    function setup() {
        observer?.disconnect();
        prepareFilters();
        const days = [...root.querySelectorAll('[data-performance-days] tbody tr')];
        if (!days.length) return;
        const graphs = root.querySelectorAll('[data-performance-chart]');
        observer = new ResizeObserver(entries => {
            for (const { target } of entries) draw(target, days);
        });
        for (const graph of graphs) observer.observe(graph);
    }

    document.addEventListener('admin-query-render', setup);
    setup();
})();
