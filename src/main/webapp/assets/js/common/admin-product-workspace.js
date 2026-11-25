(() => {
    const root = document.querySelector('[data-admin-query]');
    if (!root) return;
    const content = root.querySelector('[data-admin-query-content]');
    const error = root.querySelector('[data-admin-query-error]');
    let committed = new URL(location.href);
    let pending;
    let dirtyForm;

    root.addEventListener('input', event => {
        const form = event.target.closest('[data-admin-draft]');
        if (form) dirtyForm = form;
    });
    window.addEventListener('beforeunload', event => {
        if (!dirtyForm?.isConnected) return;
        event.preventDefault();
        event.returnValue = '';
    });

    function canLeave() {
        return !dirtyForm?.isConnected || window.confirm('저장하지 않은 입력이 있습니다. 이동할까요?');
    }

    async function navigate(url, restore = false) {
        if (url.href === pending?.url.href || (!pending && url.href === committed.href)) return;
        if (!canLeave()) {
            if (restore) history.replaceState(null, '', committed);
            return;
        }
        pending?.controller.abort();
        const request = { url, controller: new AbortController() };
        pending = request;
        content.setAttribute('aria-busy', 'true');
        error.hidden = true;
        try {
            const response = await fetch(url, { signal: request.controller.signal, cache: 'no-store' });
            const destination = new URL(response.url);
            if (!response.ok || destination.origin !== committed.origin || destination.pathname !== committed.pathname) {
                throw new Error();
            }
            destination.hash = url.hash;
            const html = new DOMParser().parseFromString(await response.text(), 'text/html');
            if (pending !== request) return;
            const next = html.querySelector('[data-admin-query-content]');
            if (!next || html.querySelector('[data-admin-query]')?.dataset.adminQuery !== root.dataset.adminQuery) throw new Error();

            const scrollLeft = content.querySelector('.admin-table-scroll')?.scrollLeft || 0;
            content.replaceChildren(...next.childNodes);
            const table = content.querySelector('.admin-table-scroll');
            if (table) table.scrollLeft = scrollLeft;
            committed = destination;
            dirtyForm = undefined;
            if (!restore) history.pushState(null, '', destination);
            else if (destination.href !== url.href) history.replaceState(null, '', destination);
            document.dispatchEvent(new Event('admin-query-render'));
            if (url.hash) {
                let id = url.hash.slice(1);
                try { id = decodeURIComponent(id); } catch { /* Keep malformed fragment literal. */ }
                const anchor = document.getElementById(id);
                if (anchor) {
                    anchor.setAttribute('tabindex', '-1');
                    anchor.focus({ preventScroll: true });
                    anchor.scrollIntoView({ block: 'nearest' });
                }
            }
        } catch {
            if (pending !== request) return;
            error.textContent = '목록을 불러오지 못했습니다. 기존 결과를 유지합니다.';
            error.hidden = false;
            if (restore) history.replaceState(null, '', committed);
        } finally {
            if (pending === request) {
                pending = undefined;
                content.removeAttribute('aria-busy');
            }
        }
    }

    root.addEventListener('submit', event => {
        const form = event.target;
        if (form.matches('[data-admin-query-form]')) {
            event.preventDefault();
            const url = new URL(form.action);
            url.search = new URLSearchParams(new FormData(form));
            navigate(url);
        } else if (!event.defaultPrevented && form.method === 'post') {
            if (form.dataset.adminSubmitting === 'true') {
                event.preventDefault();
                return;
            }
            if (dirtyForm === form) dirtyForm = undefined;
            form.dataset.adminSubmitting = 'true';
        }
    });
    root.addEventListener('change', event => {
        if (event.target.matches('[data-admin-query-form] select, [data-admin-query-form] input[type="date"]')) {
            const form = event.target.form;
            if (form.elements.historyPeriod?.value === 'CUSTOM'
                    && (!form.elements.historyStart.value || !form.elements.historyEnd.value)) return;
            form.requestSubmit();
        }
    });
    root.addEventListener('click', event => {
        if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.altKey || event.shiftKey || event.metaKey) return;
        const link = event.target.closest('a:is([data-admin-query-link], [data-category-nav], [data-stock-state], .admin-product-pagination a)');
        if (!link || link.origin !== location.origin || link.pathname !== committed.pathname) return;
        event.preventDefault();
        navigate(new URL(link.href));
    });
    window.addEventListener('popstate', () => navigate(new URL(location.href), true));
    window.addEventListener('pageshow', () => {
        root.querySelectorAll('[data-admin-submitting]').forEach(form => delete form.dataset.adminSubmitting);
    });
})();
