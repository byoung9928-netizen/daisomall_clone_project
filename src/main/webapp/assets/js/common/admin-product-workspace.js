(() => {
    const root = document.querySelector('[data-admin-query]');
    if (!root) return;
    const content = root.querySelector('[data-admin-query-content]');
    const error = root.querySelector('[data-admin-query-error]');
    const retry = root.querySelector('[data-admin-query-retry]');
    let failedUrl;
    retry?.addEventListener('click', () => { if (failedUrl) navigate(failedUrl); });
    const route = new URL(root.dataset.adminQueryRoute, location.origin);
    let committed = new URL(location.href);
    const initialEntry = committed.href;
    let pending;
    let dirtyForm;
    const pendingPreviews = new WeakMap();

    root.addEventListener('input', event => {
        const form = event.target.closest('[data-admin-draft]');
        if (form) dirtyForm = form;
    });
    function hasBatchDraft() {
        return window.DaisoAdminBatchSelection?.hasSelection()
            && !root.querySelector('[data-batch-form][data-native-submit], #product-bulk[data-native-submit]');
    }

    function hasDraft() {
        return dirtyForm?.isConnected || hasBatchDraft();
    }

    window.addEventListener('beforeunload', event => {
        if (!hasDraft()) return;
        event.preventDefault();
        event.returnValue = '';
    });

    function confirmDiscard() {
        return window.confirm('이동하면 현재 선택과 저장하지 않은 입력이 사라집니다. 이동할까요?');
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

    function workspacePath(url) {
        return url.pathname.replace(/;jsessionid=[^/;]*/gi, '');
    }

    function isWorkspace(url) {
        return url.origin === route.origin && workspacePath(url) === route.pathname;
    }

    function canonicalEntry(entry) {
        if (isWorkspace(entry)) return entry;
        const target = new URL(route);
        target.search = entry.search;
        target.searchParams.delete('view');
        target.hash = entry.hash;
        return target;
    }

    async function fetchWorkspace(url, signal) {
        if (!isWorkspace(url)) throw new Error();
        const response = await fetch(url, { signal, cache: 'no-store' });
        if (response.status === 403 || response.status === 401) throw new Error('auth');
        if (response.status === 400) throw new Error('filters');
        if (!response.ok) throw new Error('server');
        const destination = new URL(response.url);
        if (!isWorkspace(destination)) {
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
        if (retry) retry.hidden = true;
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

    root.addEventListener('admin-batch-input-changed', event => pendingPreviews.get(event.target)?.abort());

    root.addEventListener('admin-batch-request-preview', async event => {
        const form = event.target;
        const { opener, showError } = event.detail;
        if (pendingPreviews.has(form) || content.hasAttribute('aria-busy')) return;
        const attempt = new AbortController();
        pendingPreviews.set(form, attempt);
        opener.disabled = true;
        try {
            const response = await fetch(form.action, {
                method: 'POST', body: new URLSearchParams(new FormData(form)),
                cache: 'no-store', signal: attempt.signal
            });
            const html = new DOMParser().parseFromString(await response.text(), 'text/html');
            if (!form.isConnected || attempt.signal.aborted) return;
            const preview = html.querySelector('[data-batch-preview]');
            if (response.ok && preview) {
                openBatchPreview({ ...event.detail, preview });
            } else {
                const failure = html.querySelector('[data-batch-server-error]');
                showError(failure?.dataset.field, failure?.textContent.trim()
                    || (response.status === 403 ? '관리자 로그인과 요청 확인 정보를 확인해 주세요.'
                        : '미리보기를 불러오지 못했습니다. 다시 시도해 주세요.'));
            }
        } catch {
            if (form.isConnected && !attempt.signal.aborted) {
                showError(null, '미리보기에 연결하지 못했습니다. 다시 시도해 주세요.');
            }
        } finally {
            pendingPreviews.delete(form);
            opener.disabled = false;
        }
    });

    function openBatchPreview({ dialog, preview, opener, noticeId, refreshLabel }) {
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
            if (!attempted) {
                const body = new URLSearchParams(new FormData(confirmation));
                body.set('bulkAction', 'cancel');
                fetch(confirmation.action, { method: 'POST', body, keepalive: true }).catch(() => {});
            }
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
                    let url = canonicalEntry(committed);
                    try {
                        const target = new URL(result.listUrl, location.origin);
                        if (isWorkspace(target)) url = target;
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
    }

    async function navigate(url, restore = false, filterForm) {
        if (url.href === pending?.url.href || (!pending && url.href === committed.href)) return;
        const hasEditorDraft = dirtyForm?.isConnected;
        if (hasEditorDraft && !confirmDiscard()) {
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
            if (!hasEditorDraft && hasBatchDraft() && window.DaisoAdminBatchSelection.changesStore(next) && !confirmDiscard()) {
                if (restore) history.replaceState(null, '', committed);
                filterForm?.reset();
                return;
            }
            destination.hash = url.hash;

            if (!restore) history.pushState(null, '', destination);
            else if (destination.href !== location.href) history.replaceState(null, '', destination);
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
        } catch (failure) {
            if (pending !== request) return;
            const messages = {
                auth: '관리자 로그인이 만료되었거나 조회 권한이 없습니다. 다시 로그인해 주세요.',
                filters: '검색 조건을 확인해 주세요. 현재 목록은 유지됩니다.'
            };
            error.textContent = messages[failure.message] || '목록을 불러오지 못했습니다. 기존 결과를 유지합니다.';
            error.hidden = false;
            if (retry) { failedUrl = url; retry.hidden = false; }
            if (restore) history.replaceState(null, '', committed);
        } finally {
            if (pending === request) {
                pending = undefined;
                content.removeAttribute('aria-busy');
            }
        }
    }

    root.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
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
            if (form.hasAttribute('data-admin-explicit-submit')) return;
            if (form.elements.historyPeriod?.value === 'CUSTOM'
                    && (!form.elements.historyStart.value || !form.elements.historyEnd.value)) return;
            form.requestSubmit();
        }
    });
    root.addEventListener('click', event => {
        if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.altKey || event.shiftKey || event.metaKey) return;
        const link = event.target.closest('a:is([data-admin-query-link], [data-category-nav], [data-stock-state], .admin-product-pagination a)');
        if (!link || !isWorkspace(new URL(link.href))) return;
        event.preventDefault();
        navigate(new URL(link.href));
    });
    window.addEventListener('popstate', () => {
        const entry = new URL(location.href);
        navigate(entry.href === initialEntry ? canonicalEntry(entry) : entry, true);
    });
    window.addEventListener('pageshow', () => {
        root.querySelectorAll('[data-admin-submitting]').forEach(form => delete form.dataset.adminSubmitting);
        root.querySelectorAll('[data-native-submit]').forEach(form => delete form.dataset.nativeSubmit);
    });
})();
