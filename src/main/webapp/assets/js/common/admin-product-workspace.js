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
    function hasDraft() {
        return dirtyForm?.isConnected || (root.querySelector('[data-batch-product]:checked, input[form="product-bulk"]:checked')
            && !root.querySelector('[data-batch-form][data-native-submit], #product-bulk[data-native-submit]'));
    }

    window.addEventListener('beforeunload', event => {
        if (!hasDraft()) return;
        event.preventDefault();
        event.returnValue = '';
    });

    function canLeave() {
        return !hasDraft() || window.confirm('이동하면 현재 선택과 저장하지 않은 입력이 사라집니다. 이동할까요?');
    }

    function workspaceTable() {
        return content.querySelector('.admin-table-scroll:not(dialog .admin-table-scroll)');
    }

    function render(next, destination) {
        const scrollLeft = workspaceTable()?.scrollLeft || 0;
        content.replaceChildren(...next.childNodes);
        const table = workspaceTable();
        if (table) table.scrollLeft = scrollLeft;
        committed = destination;
        dirtyForm = undefined;
        document.dispatchEvent(new Event('admin-query-render'));
    }

    async function fetchWorkspace(url, signal) {
        const response = await fetch(url, { signal, cache: 'no-store' });
        const destination = new URL(response.url);
        if (!response.ok || destination.origin !== committed.origin || destination.pathname !== committed.pathname) {
            throw new Error();
        }
        const html = new DOMParser().parseFromString(await response.text(), 'text/html');
        const next = html.querySelector('[data-admin-query-content]');
        if (!next || html.querySelector('[data-admin-query]')?.dataset.adminQuery !== root.dataset.adminQuery) throw new Error();
        return { next, destination };
    }

    function showBatchNotice(id, message) {
        let notice = document.getElementById(id);
        if (!notice) {
            notice = document.createElement('p');
            notice.id = id;
            notice.className = 'inventory-success';
            notice.setAttribute('role', 'status');
            content.prepend(notice);
        }
        notice.textContent = message;
    }

    function workspaceScroll() {
        const table = workspaceTable();
        return { x: window.scrollX, y: window.scrollY, left: table?.scrollLeft || 0, top: table?.scrollTop || 0 };
    }

    function restoreWorkspaceScroll(position) {
        workspaceTable()?.scrollTo({ left: position.left, top: position.top, behavior: 'instant' });
        window.scrollTo({ left: position.x, top: position.y, behavior: 'instant' });
    }

    async function refreshAfterBatch(url, noticeId, message) {
        pending?.controller.abort();
        const request = { url, controller: new AbortController() };
        pending = request;
        content.setAttribute('aria-busy', 'true');
        error.hidden = true;
        try {
            const { next, destination } = await fetchWorkspace(url, request.controller.signal);
            if (pending !== request) return;
            const position = workspaceScroll();
            history.replaceState(null, '', destination);
            render(next, destination);
            showBatchNotice(noticeId, message);
            restoreWorkspaceScroll(position);
        } catch {
            if (pending !== request) return;
            const retry = document.createElement('button');
            retry.type = 'button';
            retry.className = 'button secondary';
            retry.textContent = '목록 다시 불러오기';
            retry.addEventListener('click', () => refreshAfterBatch(url, noticeId, message));
            error.replaceChildren('변경은 저장됐지만 최신 목록을 불러오지 못했습니다. ', retry);
            error.hidden = false;
        } finally {
            if (pending === request) {
                pending = undefined;
                content.removeAttribute('aria-busy');
            }
        }
    }

    root.addEventListener('admin-batch-preview', event => {
        const { dialog, preview, opener, noticeId, refreshLabel, discard } = event.detail;
        dialog.replaceChildren(document.importNode(preview, true));
        const close = dialog.querySelector('[data-batch-close]');
        const revise = dialog.querySelector('[data-batch-revise]');
        const confirmation = dialog.querySelector('[data-batch-confirm]');
        const warning = dialog.querySelector('[data-batch-confirm-error]');
        let saving = false;
        let attempted = false;
        const preventDismiss = event => { if (saving) event.preventDefault(); };
        dialog.addEventListener('cancel', preventDismiss);
        dialog.addEventListener('close', () => {
            dialog.removeEventListener('cancel', preventDismiss);
            if (!attempted && discard) discard(confirmation);
            dialog.replaceChildren();
            if (opener.isConnected) opener.focus({ preventScroll: true });
        }, { once: true });
        close.hidden = false;
        close.addEventListener('click', () => dialog.close());
        revise.addEventListener('click', event => {
            event.preventDefault();
            if (!saving) dialog.close();
        });
        confirmation.addEventListener('submit', async event => {
            event.preventDefault();
            const accept = confirmation.querySelector('button[type="submit"]');
            if (attempted) return;
            saving = attempted = true;
            accept.disabled = close.disabled = true;
            revise.setAttribute('aria-disabled', 'true');
            try {
                const response = await fetch(confirmation.action, {
                    method: 'POST',
                    headers: { Accept: 'application/json' },
                    body: new URLSearchParams(new FormData(confirmation)),
                    cache: 'no-store',
                    redirect: 'error'
                });
                if (!response.headers.get('Content-Type')?.toLowerCase().startsWith('application/json')) throw new Error();
                const result = await response.json();
                if (response.ok && result.success === true && typeof result.message === 'string') {
                    let url = committed;
                    try {
                        const target = new URL(result.listUrl, location.origin);
                        if (target.origin === committed.origin && target.pathname === committed.pathname) url = target;
                    } catch { /* Keep the current list URL. */ }
                    const position = workspaceScroll();
                    dialog.close();
                    opener.form.dispatchEvent(new Event('admin-batch-reset'));
                    showBatchNotice(noticeId, result.message);
                    restoreWorkspaceScroll(position);
                    void refreshAfterBatch(url, noticeId, result.message);
                    return;
                }
                warning.textContent = result.success === false && result.message
                    ? result.message : '저장 결과를 확인할 수 없습니다. 목록에서 최신 값을 확인해 주세요.';
            } catch {
                warning.textContent = '확정 응답을 확인하지 못했습니다. 목록에서 최신 값을 확인해 주세요.';
            } finally {
                saving = false;
                close.disabled = false;
                revise.removeAttribute('aria-disabled');
            }
            warning.hidden = false;
            warning.focus();
            const refreshLink = document.createElement('a');
            refreshLink.className = 'button secondary';
            refreshLink.href = revise.href;
            refreshLink.textContent = refreshLabel;
            confirmation.prepend(refreshLink);
        });
        dialog.showModal();
        dialog.querySelector('h1').focus();
    });

    async function navigate(url, restore = false, filterForm) {
        if (url.href === pending?.url.href || (!pending && url.href === committed.href)) return;
        if (!canLeave()) {
            if (restore) history.replaceState(null, '', committed);
            filterForm?.reset();
            return;
        }
        pending?.controller.abort();
        const request = { url, controller: new AbortController() };
        pending = request;
        content.setAttribute('aria-busy', 'true');
        error.hidden = true;
        try {
            const { next, destination } = await fetchWorkspace(url, request.controller.signal);
            if (pending !== request) return;
            destination.hash = url.hash;

            if (!restore) history.pushState(null, '', destination);
            else if (destination.href !== url.href) history.replaceState(null, '', destination);
            render(next, destination);
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
            navigate(url, false, form);
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
        root.querySelectorAll('[data-native-submit]').forEach(form => delete form.dataset.nativeSubmit);
    });
})();
