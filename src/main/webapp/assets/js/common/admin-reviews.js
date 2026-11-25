(() => {
    const filters = document.querySelector('#admin-review-filters');
    if (!filters) return;

    const results = document.querySelector('#review-results');
    const metrics = document.querySelector('[data-review-metrics]');
    const count = results.querySelector('[data-review-count]');
    const list = results.querySelector('[data-review-list]');
    const loading = results.querySelector('[data-review-loading]');
    const error = results.querySelector('[data-review-error]');
    const analysisForm = document.querySelector('#admin-review-analysis');
    const analysisProduct = analysisForm.elements.analysisProduct;
    const analysisResult = document.querySelector('[data-review-analysis-result]');
    const analysisError = analysisForm.querySelector('[data-review-analysis-error]');
    const { query, start, end, period } = filters.elements;
    const defaults = { query: '', rating: '', state: 'ALL', reports: 'ALL',
        dateField: 'REVIEW', period: 'ALL', start: '', end: '', sort: 'NEWEST', page: '1', detail: '', reason: '', withPhoto: '', withReaction: '', analysisProduct: '' };
    let committed = new URL(results.dataset.reviewUrl, location.origin);
    let committedDates = results.dataset;
    let pending;

    function requestKey(url) {
        const custom = url.searchParams.get('period') === 'CUSTOM';
        return JSON.stringify(Object.keys(defaults).map(name => {
            if (!custom && (name === 'start' || name === 'end')) return '';
            const value = url.searchParams.get(name) || defaults[name];
            return name === 'query' ? value.trim() : value;
        }));
    }

    function validateDates() {
        const custom = period.value === 'CUSTOM';
        start.required = end.required = custom;
        end.setCustomValidity(custom && start.value && end.value && start.value > end.value
            ? '종료일은 시작일 이후여야 합니다.' : '');
    }

    function syncFields(preserveKeyword) {
        for (const name of ['query', 'rating', 'state', 'reports', 'dateField', 'period', 'sort', 'reason', 'withPhoto', 'withReaction']) {
            if (name === 'query' && preserveKeyword) continue;
            const field = filters.elements.namedItem(name);
            if (field) field.value = committed.searchParams.get(name) || defaults[name];
        }
        if (!preserveKeyword) analysisProduct.value = committed.searchParams.get('analysisProduct') || '';
        start.value = committedDates.start;
        end.value = committedDates.end;
        validateDates();
    }

    function formUrl(useKeyword) {
        const url = new URL(filters.action);
        const values = new FormData(filters);
        const activeUrl = pending?.url || committed;
        values.set('query', useKeyword ? query.value.trim() : activeUrl.searchParams.get('query') || '');
        values.set('analysisProduct', activeUrl.searchParams.get('analysisProduct') || '');
        if (period.value !== 'CUSTOM') {
            values.delete('start');
            values.delete('end');
        }
        url.search = new URLSearchParams(values);
        return url;
    }

    function stop() {
        if (!pending) return;
        pending.controller.abort();
        clearTimeout(pending.timer);
        pending = undefined;
        loading.hidden = true;
        list.removeAttribute('aria-busy');
        list.classList.remove('is-loading');
    }

    function moveToResults(url, scroll) {
        if (scroll === 'page') {
            results.focus({ preventScroll: true });
            results.scrollIntoView({
                behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
            });
        } else if (scroll === 'detail') {
            const anchor = document.getElementById(url.hash.slice(1)) || results;
            anchor.scrollIntoView();
        }
    }

    async function load(url, { scroll, fromHistory = false, preserveKeyword = true, errorElement = error } = {}) {
        analysisError.hidden = true;
        const key = requestKey(url);
        if (pending?.key === key) return;
        if (requestKey(committed) === key) {
            stop();
            error.hidden = true;
            syncFields(preserveKeyword);
            if (fromHistory) committed.hash = url.hash;
            if (scroll === 'detail' && url.hash !== committed.hash) {
                committed.hash = url.hash;
                history.pushState(null, '', committed);
            }
            moveToResults(url, scroll);
            return;
        }

        stop();
        const controller = new AbortController();
        const request = { key, url, controller, timer: setTimeout(() => { loading.hidden = false; }, 180) };
        pending = request;
        error.hidden = true;
        list.setAttribute('aria-busy', 'true');
        list.classList.add('is-loading');

        try {
            const response = await fetch(url, {
                signal: controller.signal,
                headers: { 'X-Review-Fragment': '1' }
            });
            if (!response.ok) {
                throw new Error(response.status === 403
                    ? '관리자 로그인이 필요합니다. 페이지를 다시 열고 로그인해 주세요.'
                    : '리뷰를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
            }
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            if (pending !== request) return;
            const nextMetrics = page.querySelector('[data-review-metrics]');
            const nextAnalysis = page.querySelector('[data-review-analysis-result]');
            const nextResults = page.querySelector('#review-results');
            const nextCount = nextResults?.querySelector('[data-review-count]');
            const nextList = nextResults?.querySelector('[data-review-list]');
            if (!nextMetrics || !nextAnalysis || !nextCount || !nextList || !nextResults.dataset.reviewUrl) throw new Error();

            const currentY = window.scrollY;
            const tableX = list.querySelector('.admin-table-scroll')?.scrollLeft || 0;
            const focusedCard = metrics.contains(document.activeElement)
                ? document.activeElement.dataset.reviewMetric : null;
            const nextUrl = new URL(nextResults.dataset.reviewUrl, location.origin);
            nextUrl.hash = url.hash;
            committed = nextUrl;
            committedDates = nextResults.dataset;
            if (fromHistory) history.replaceState(null, '', committed);
            else history.pushState(null, '', committed);
            metrics.replaceChildren(...nextMetrics.childNodes);
            count.replaceChildren(...nextCount.childNodes);
            list.replaceChildren(...nextList.childNodes);
            analysisResult.replaceChildren(...nextAnalysis.childNodes);
            const table = list.querySelector('.admin-table-scroll');
            if (table) table.scrollLeft = tableX;
            syncFields(preserveKeyword);
            if (focusedCard) {
                Array.from(metrics.querySelectorAll('a[data-review-metric]'))
                    .find(card => card.dataset.reviewMetric === focusedCard)?.focus({ preventScroll: true });
            }
            if (scroll) moveToResults(url, scroll);
            else if (!fromHistory) window.scrollTo({ top: currentY, behavior: 'instant' });
        } catch (failure) {
            if (pending !== request) return;
            errorElement.textContent = failure.message.startsWith('관리자') ? failure.message
                : '리뷰를 불러오지 못했습니다. 현재 목록을 유지합니다. 다시 시도해 주세요.';
            errorElement.hidden = false;
            syncFields(preserveKeyword);
            if (fromHistory) history.replaceState(null, '', committed);
        } finally {
            clearTimeout(request.timer);
            if (pending === request) {
                pending = undefined;
                loading.hidden = true;
                list.removeAttribute('aria-busy');
                list.classList.remove('is-loading');
            }
        }
    }

    function applyFilters(useKeyword = false) {
        validateDates();
        if (filters.reportValidity()) load(formUrl(useKeyword));
        else stop();
    }

    filters.addEventListener('change', event => {
        const field = event.target;
        if (field.name === 'period') {
            if (field.value === 'CUSTOM') {
                stop();
                validateDates();
                return;
            }
            start.value = field.dataset.start;
            end.value = field.dataset.end;
        }
        if (['period', 'start', 'end', 'rating', 'state', 'reports', 'dateField', 'reason', 'withPhoto', 'withReaction'].includes(field.name)) {
            applyFilters();
        }
    });
    [start, end].forEach(date => date.addEventListener('input', () => {
        stop();
        period.value = 'CUSTOM';
        validateDates();
    }));
    filters.elements.sort.addEventListener('change', () => applyFilters());

    document.addEventListener('submit', event => {
        if (event.target === filters) {
            event.preventDefault();
            applyFilters(true);
        } else if (event.target === analysisForm) {
            event.preventDefault();
            const value = analysisProduct.value.trim();
            if (!value) {
                analysisProduct.focus();
                return;
            }
            analysisError.hidden = true;
            const url = new URL(pending?.url || committed);
            url.searchParams.set('analysisProduct', value);
            load(url, { errorElement: analysisError });
        } else if (event.target.matches('[data-admin-review-action]')) {
            const message = event.submitter?.dataset.confirm;
            if (message && !window.confirm(message)) event.preventDefault();
        }
    });

    document.addEventListener('click', event => {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        const link = event.target.closest('a');
        if (!link) return;
        const url = new URL(link.href);
        if (url.origin !== location.origin || url.pathname !== committed.pathname) return;
        if (link.matches('[data-review-reset]')) {
            event.preventDefault();
            load(url, { preserveKeyword: false });
        } else if (metrics.contains(link)) {
            event.preventDefault();
            validateDates();
            if (!filters.reportValidity()) return;
            const bucket = link.dataset.reviewMetric;
            if (filters.elements.reports) filters.elements.reports.value = bucket;
            if (bucket === 'PENDING' || bucket === 'REPORTED') {
                filters.elements.sort.value = url.searchParams.get('sort');
            }
            load(formUrl(false));
        } else if (list.contains(link) && link.closest('.admin-product-pagination, .admin-review-links')) {
            event.preventDefault();
            load(url, { scroll: link.closest('.admin-product-pagination') ? 'page' : 'detail' });
        }
    });

    window.addEventListener('popstate', () => load(new URL(location.href), {
        fromHistory: true, preserveKeyword: false
    }));

    if (results.hasAttribute('data-review-fallback') && /^#review-\d+$/.test(location.hash)) results.scrollIntoView();
})();
