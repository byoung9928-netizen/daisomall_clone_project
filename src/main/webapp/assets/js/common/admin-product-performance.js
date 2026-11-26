(() => {
    const root = document.querySelector('[data-admin-query="product-performance"]');
    if (!root) return;

    const observers = [];
    const hiddenColumns = new Set();
    const colors = ['#2563eb', '#0d9488', '#8b5cf6', '#b45309', '#475569', '#aab4c5'];
    const seriesColors = { revenue: colors[0], previousRevenue: colors[0], units: colors[1], views: colors[0],
        wish: colors[1], cart: colors[2], unwish: colors[3], uncart: colors[4] };
    let showZero = false;
    let categoryAscending = false;
    let detailRequest;
    let detailDialog;
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
        const bars = keys[0] === 'units';
        const hourly = days[0].closest('table').dataset.hourly === 'true';
        const centered = bars || hourly;
        const firstFuture = days.findIndex(day => day.dataset.state === 'future');
        const firstCovered = days.findIndex(day => day.dataset.views !== undefined);
        const series = keys.map(key => days.map(day => day.dataset[key] === undefined ? null : Number(day.dataset[key])));
        const unit = keys[0] === 'revenue' ? '원' : keys[0] === 'units' ? '개' : '건';
        const range = graph.querySelector('[data-chart-range]');
        range.textContent = days[0].closest('table').dataset.trendPeriod;
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
        canvas.setAttribute('aria-label', graph.querySelector('figcaption').textContent + ' 그래프. 방향키로 ' + (hourly ? '시간대별' : '날짜별') + ' 수치를 확인할 수 있습니다.');
        const legend = document.createElement('div');
        legend.className = 'performance-chart-legend';
        const shown = keys.map(key => key !== 'unwish' && key !== 'uncart');
        keys.forEach((key, index) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = seriesLabels[key];
            button.style.setProperty('--series-color', seriesColors[key]);
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
        if (!bars) range.before(legend);

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
            left = Math.max(context.measureText(axis(minimum)).width,
                context.measureText(axis(minimum + 3 * step)).width) + 12;
            const right = width - 20;
            chartWidth = Math.max(1, right - left);
            const bottom = height - 22;
            const boundary = index => left + chartWidth * index / days.length;
            const x = centered ? index => boundary(index + .5)
                : index => days.length === 1 ? left + chartWidth / 2 : left + chartWidth * index / (days.length - 1);
            const futureEdge = firstFuture < 0 ? right : boundary(firstFuture);
            if (keys[0] === 'views') {
                const edge = firstCovered < 0 ? futureEdge : firstCovered === 0 ? left
                    : hourly ? boundary(firstCovered) : (x(firstCovered - 1) + x(firstCovered)) / 2;
                context.fillStyle = '#eef0f3';
                context.fillRect(left, 12, edge - left, bottom - 12);
            }
            if (firstFuture >= 0) {
                context.fillStyle = '#f6f7f9';
                context.fillRect(futureEdge, 12, right - futureEdge, bottom - 12);
                context.beginPath();
                context.strokeStyle = '#a3adb9';
                context.setLineDash([3, 3]);
                context.moveTo(futureEdge, 12);
                context.lineTo(futureEdge, bottom);
                context.stroke();
                context.setLineDash([]);
            }
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
            const limit = Math.max(2, Math.floor(chartWidth / 54) + 1);
            if (hourly) {
                const hours = days.length;
                const hourStep = [1, 2, 3, 4, 6, 8, 12, 24].find(step => hours / step + 1 <= limit);
                for (let index = 0; index <= hours; index += hourStep) {
                    const label = index === hours ? '24:00' : days[index].dataset.tick;
                    context.fillText(label, boundary(index), bottom + 12);
                }
            } else {
                const tickCount = Math.min(days.length, limit);
                for (let tick = 0; tick < tickCount; tick++) {
                    const index = tickCount === 1 ? 0 : Math.round(tick * (days.length - 1) / (tickCount - 1));
                    context.fillText(days[index].dataset.tick, x(index), bottom + 12);
                }
            }
            for (const [index, values] of series.entries()) {
                if (!shown[index]) continue;
                context.strokeStyle = context.fillStyle = seriesColors[keys[index]];
                if (keys[index] === 'units') {
                    const bar = Math.max(1, Math.min(30, chartWidth / days.length * .65));
                    values.forEach((value, day) => {
                        if (value !== null) context.fillRect(x(day) - bar / 2, y(value), bar, y(0) - y(value));
                    });
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
            const bucket = days[active].dataset;
            if (bucket.state === 'future' || (keys[0] === 'views' && bucket.views === undefined)) {
                const reason = bucket.state === 'future' ? '집계 전' : '행동 미수집';
                tooltip.textContent = bucket.date + ' · ' + reason
                    + (bucket.state === 'collecting' ? ' · 집계 진행 중' : '');
                return;
            }
            const values = series.flatMap((values, index) => {
                if (!shown[index]) return [];
                const date = keys[index] === 'previousRevenue' ? ' (' + days[active].dataset.previousDate + ')' : '';
                return [seriesLabels[keys[index]] + date + ' '
                    + (values[active] === null ? '수집 기록 없음' : number.format(values[active]) + unit)];
            });
            tooltip.textContent = [bucket.date, ...values, ...(bucket.state === 'collecting' ? ['집계 진행 중'] : [])].join(' · ');
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
            const index = centered ? Math.floor(offset * days.length / chartWidth)
                : Math.round(offset * (days.length - 1) / chartWidth);
            select(Math.max(0, Math.min(days.length - 1, index)));
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

    function prepareCategories() {
        const section = root.querySelector('.performance-category-sales');
        if (!section) return;
        const rows = [...section.querySelectorAll('[data-category-revenue]')].map(row => ({
            row, value: Number(row.dataset.categoryRevenue),
            label: row.querySelector('[data-category-label]').textContent.trim(), link: row.querySelector('a')
        }));
        const option = section.querySelector('[data-category-show-zero]');
        const sort = section.querySelector('[data-category-sort]');
        const empty = section.querySelector('[data-category-empty]');
        option.parentElement.hidden = false;
        sort.hidden = false;
        section.querySelector('[data-category-sort-label]').hidden = true;

        function updateTable() {
            option.checked = showZero;
            sort.textContent = '순상품 매출 ' + (categoryAscending ? '↑' : '↓');
            sort.closest('th').setAttribute('aria-sort', categoryAscending ? 'ascending' : 'descending');
            const sorted = [...rows].sort((a, b) => (categoryAscending ? a.value - b.value : b.value - a.value));
            for (const item of sorted) {
                item.row.hidden = !showZero && item.value === 0;
                empty.before(item.row);
            }
            empty.hidden = rows.length === 0 || rows.some(item => !item.row.hidden);
        }
        option.addEventListener('change', () => { showZero = option.checked; updateTable(); });
        sort.addEventListener('click', () => { categoryAscending = !categoryAscending; updateTable(); });
        updateTable();

        const figure = section.querySelector('[data-performance-donut]');
        if (!figure) return;
        const positive = rows.filter(item => item.value > 0);
        const total = Number(figure.dataset.total);
        const segments = positive.slice(0, 5);
        if (positive.length > 5) segments.push({ label: '기타', value: positive.slice(5).reduce((sum, item) => sum + item.value, 0) });
        const ring = document.createElement('div');
        ring.className = 'performance-donut-ring';
        ring.setAttribute('aria-hidden', 'true');
        const center = document.createElement('div');
        center.className = 'performance-donut-center';
        ring.append(center);
        const legend = document.createElement('ul');
        let offset = 0;
        for (const [index, item] of segments.entries()) {
            item.color = colors[index];
            item.start = offset;
            offset += item.value / total * 100;
            item.end = offset;
            const label = document.createElement('li');
            const content = item.link ? item.link.cloneNode(false) : document.createElement('span');
            content.textContent = item.label + ' · ' + number.format(item.value) + '원 · ' + (item.value / total * 100).toFixed(2) + '%';
            label.style.setProperty('--series-color', item.color);
            label.append(content);
            label.addEventListener('pointerenter', () => highlight(item));
            label.addEventListener('pointerleave', () => highlight(null));
            content.addEventListener('focus', () => highlight(item));
            content.addEventListener('blur', () => highlight(null));
            legend.append(label);
        }

        function highlight(item) {
            ring.style.background = 'conic-gradient(' + segments.map(part =>
                (item && part !== item ? '#e2e5e9' : part.color) + ' ' + part.start + '% ' + part.end + '%').join(',') + ')';
            center.textContent = item ? item.label + ' · ' + number.format(item.value) + '원'
                : '전체 순상품 매출 · ' + number.format(total) + '원';
            ring.classList.toggle('is-linked', Boolean(item?.link));
        }

        function segment(event) {
            const box = ring.getBoundingClientRect();
            const x = event.clientX - box.left - box.width / 2;
            const y = event.clientY - box.top - box.height / 2;
            const radius = Math.hypot(x, y);
            if (radius < box.width * .30 || radius > box.width / 2) return null;
            const percent = (Math.atan2(x, -y) / (2 * Math.PI) * 100 + 100) % 100;
            return segments.find(item => percent >= item.start && percent < item.end);
        }
        ring.addEventListener('pointermove', event => highlight(segment(event)));
        ring.addEventListener('pointerleave', () => highlight(null));
        ring.addEventListener('click', event => segment(event)?.link?.click());
        figure.append(ring, legend);
        highlight(null);
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

    function markUnavailable(container) {
        const selector = '[data-conversion], [data-metric], [data-product-conversion] strong, .performance-detail-stats dd';
        for (const value of container.querySelectorAll(selector)) {
            if (value.textContent.trim() === '—') value.classList.add('performance-unavailable');
        }
    }

    function setup() {
        markUnavailable(root);
        for (const observer of observers.splice(0)) observer.disconnect();
        scrollEvents?.abort();
        scrollEvents = new AbortController();
        prepareFilters();
        prepareTable(scrollEvents.signal);
        prepareCategories();
        root.querySelector("[data-performance-page-size]")?.addEventListener("change", event => event.target.form.requestSubmit());
        const days = [...root.querySelectorAll('[data-performance-days] tbody tr')];
        if (!days.length) return;
        for (const graph of root.querySelectorAll('[data-performance-chart]')) prepareGraph(graph, days);
    }

    function detailTabs(dialog) {
        const list = dialog.querySelector('.performance-detail-tabs');
        if (!list) return;
        const tabs = [...list.querySelectorAll('[data-detail-tab]')];
        const panes = [...dialog.querySelectorAll('[data-detail-section]')];
        list.setAttribute('role', 'tablist');
        function show(target) {
            tabs.forEach((tab, index) => {
                tab.id = 'performance-inner-tab-' + index;
                tab.setAttribute('role', 'tab');
                tab.setAttribute('aria-selected', String(index === target));
                tab.setAttribute('aria-controls', panes[index].id);
                tab.tabIndex = index === target ? 0 : -1;
                panes[index].setAttribute('role', 'tabpanel');
                panes[index].setAttribute('aria-labelledby', tab.id);
                panes[index].hidden = index !== target;
            });
            dialog.querySelector('.performance-detail-body').scrollTop = 0;
        }
        tabs.forEach((tab, index) => tab.addEventListener('click', event => {
            event.preventDefault();
            show(index);
        }));
        list.addEventListener('keydown', event => {
            const index = tabs.indexOf(event.target);
            if (index < 0) return;
            const keys = { ArrowLeft: (index + tabs.length - 1) % tabs.length,
                ArrowRight: (index + 1) % tabs.length, Home: 0, End: tabs.length - 1 };
            if (!(event.key in keys)) return;
            event.preventDefault();
            show(keys[event.key]);
            tabs[keys[event.key]].focus();
        });
        show(0);
    }

    function wireDetail(dialog, opener) {
        dialog.addEventListener('click', event => {
            if (!event.target.closest('[data-performance-detail-close]') || !opener) return;
            event.preventDefault();
            dialog.close();
        });
        dialog.addEventListener('close', () => {
            detailRequest?.abort();
            detailRequest = null;
            detailDialog = null;
            if (opener) {
                dialog.remove();
                opener.focus({ preventScroll: true });
            } else {
                const close = dialog.querySelector('[data-performance-detail-close]');
                if (close) location.assign(close.href);
            }
        }, { once: true });
    }

    function showDetail(link) {
        const url = new URL(link.href);
        url.searchParams.set('fragment', 'detail');
        url.hash = '';
        const dialog = document.createElement('dialog');
        dialog.className = 'performance-detail-dialog';
        dialog.setAttribute('aria-labelledby', 'performance-detail-title');
        const status = document.createElement('div');
        status.className = 'performance-detail-content';
        status.innerHTML = '<header class="performance-detail-header"><h2 id="performance-detail-title">상품 상세 분석</h2></header>'
            + '<div class="performance-detail-body"><p role="status"></p></div>'
            + '<footer class="performance-detail-footer"><button class="button secondary" type="button" hidden>다시 시도</button>'
            + '<button class="button primary" type="button" data-performance-detail-close>닫기</button></footer>';
        const hint = status.querySelector('[role=status]');
        const retry = status.querySelector('button[hidden]');
        dialog.append(status);
        document.body.append(dialog);
        detailDialog = dialog;
        wireDetail(dialog, link);
        dialog.showModal();

        async function load() {
            detailRequest?.abort();
            const request = new AbortController();
            detailRequest = request;
            retry.hidden = true;
            hint.textContent = '상품 상세 분석을 불러오는 중입니다.';
            try {
                const response = await fetch(url, { signal: request.signal, redirect: 'error', cache: 'no-store' });
                if (!response.ok) throw new Error(String(response.status));
                const page = new DOMParser().parseFromString(await response.text(), 'text/html');
                const content = page.querySelector('[data-performance-detail-content]');
                if (!content) throw new Error('INVALID');
                if (detailRequest !== request || !dialog.open) return;
                dialog.replaceChildren(content);
                detailTabs(dialog);
                markUnavailable(dialog);
                dialog.querySelector('[data-performance-detail-close]')?.focus({ preventScroll: true });
            } catch (failure) {
                if (detailRequest !== request || !dialog.open) return;
                hint.textContent = failure.message === '403' ? '관리자 권한을 확인해 주세요.'
                    : failure.message === '404' ? '현재 조건에서 상품을 찾을 수 없습니다.'
                    : '상품 상세 분석을 불러오지 못했습니다.';
                retry.hidden = false;
            }
        }
        retry.addEventListener('click', load);
        load();
    }

    root.addEventListener('click', event => {
        const link = event.target.closest('[data-performance-detail-link]');
        if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.shiftKey
                || event.altKey || event.metaKey || typeof window.HTMLDialogElement?.prototype.showModal !== 'function') return;
        event.preventDefault();
        if (!detailDialog) showDetail(link);
    });

    const initial = root.querySelector('[data-performance-initial-dialog]');
    if (initial && typeof window.HTMLDialogElement?.prototype.showModal === 'function') {
        initial.removeAttribute('open');
        detailTabs(initial);
        markUnavailable(initial);
        wireDetail(initial, null);
        detailDialog = initial;
        initial.showModal();
    }

    document.addEventListener('pointerover', event => {
        const info = event.target.closest('.performance-popover');
        if (event.pointerType === 'mouse' && info) info.open = true;
    });
    document.addEventListener('pointerout', event => {
        const info = event.target.closest('.performance-popover');
        if (info && !info.contains(event.relatedTarget) && !info.hasAttribute('data-pinned')) info.open = false;
    });
    document.addEventListener('click', event => {
        const summary = event.target.closest('.performance-popover summary');
        for (const open of document.querySelectorAll('.performance-popover[open]')) {
            if (open.contains(event.target)) continue;
            open.open = false;
            open.removeAttribute('data-pinned');
        }
        if (!summary) return;
        event.preventDefault();
        const info = summary.parentElement;
        info.open = info.toggleAttribute('data-pinned');
    });
    document.addEventListener('keydown', event => {
        const info = event.target.closest('.performance-popover[open]');
        if (event.key === 'Escape' && info) {
            event.preventDefault();
            info.open = false;
            info.removeAttribute('data-pinned');
        }
    });

    document.addEventListener('admin-query-render', setup);
    setup();
})();
