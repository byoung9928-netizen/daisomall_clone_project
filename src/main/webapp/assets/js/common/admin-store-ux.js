(() => {
    const menuToggle = document.querySelector('[data-product-admin-menu]');
    const menu = menuToggle?.nextElementSibling;
    if (menu?.classList.contains('admin-sidebar')) {
        menu.id = 'product-admin-sidebar';
        const current = menu.querySelector('[aria-current="page"]');
        menuToggle.querySelector('[data-menu-current]').textContent = current?.textContent.trim() || '';
        menuToggle.setAttribute('aria-controls', menu.id);
        menuToggle.addEventListener('click', () => {
            const expanded = menuToggle.getAttribute('aria-expanded') === 'false';
            menuToggle.setAttribute('aria-expanded', String(expanded));
            menuToggle.querySelector('[data-menu-action]').textContent = expanded ? '관리 메뉴 닫기' : '관리 메뉴 열기';
        });
        menuToggle.hidden = false;
    }

    document.addEventListener('submit', event => {
        const form = event.target.closest('[data-store-write]');
        if (!form) return;
        if (form.dataset.submitting === 'true') {
            event.preventDefault();
            return;
        }
        form.dataset.submitting = 'true';
        if (event.submitter) event.submitter.setAttribute('aria-disabled', 'true');
    });
    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        document.querySelectorAll('[data-store-write][data-submitting]').forEach(form => {
            delete form.dataset.submitting;
            form.querySelector('[aria-disabled="true"]')?.removeAttribute('aria-disabled');
        });
    });

    const filters = document.querySelector('[data-store-filters]');
    if (filters) {
        filters.querySelectorAll('select').forEach(field => {
            field.addEventListener('change', () => filters.requestSubmit());
        });
    }

    const confirmation = document.querySelector('[data-unlist-confirm]');
    if (confirmation) confirmation.focus();

    const inventory = document.getElementById('store-inventory-table');
    const returnedRow = document.querySelector('[data-saved-row]');
    const drawer = document.querySelector('.store-drawer');
    if (drawer) {
        const row = document.getElementById(drawer.dataset.rowAnchor);
        (row || inventory)?.scrollIntoView({ block: 'center' });
        openDrawer(drawer);
    } else if (returnedRow) {
        returnedRow.scrollIntoView({ block: 'center' });
        returnedRow.focus({ preventScroll: true });
    } else if (location.hash === '#store-inventory-table' || location.hash.startsWith('#store-row-')) {
        const target = document.getElementById(location.hash.slice(1)) || inventory;
        target?.focus();
    }

    let opening = false;
    document.addEventListener('click', async event => {
        const opener = event.target.closest('[data-open-store-drawer]');
        if (!opener || !window.HTMLDialogElement?.prototype.showModal
                || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        if (opening || document.querySelector('.store-drawer[open]')) return;
        opening = true;
        opener.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(opener.href, { cache: 'no-store', credentials: 'same-origin' });
            if (!response.ok || response.redirected) throw new Error('Store adjustment page unavailable');
            const source = new DOMParser().parseFromString(await response.text(), 'text/html').querySelector('.store-drawer');
            if (!source || !inventory) throw new Error('Store adjustment dialog unavailable');
            const panel = document.importNode(source, true);
            inventory.before(panel);
            openDrawer(panel, opener);
        } catch {
            location.assign(opener.href);
        } finally {
            opening = false;
            opener.removeAttribute('aria-busy');
        }
    });

    function openDrawer(panel, opener) {
        if (typeof panel.showModal === 'function') {
            panel.removeAttribute('open');
            panel.showModal();
            const closeLink = panel.querySelector('[data-drawer-close]');
            const close = () => {
                if (!opener) {
                    location.assign(closeLink.href);
                    return;
                }
                panel.close();
                panel.remove();
                opener.focus({ preventScroll: true });
            };
            panel.addEventListener('cancel', event => {
                event.preventDefault();
                close();
            });
            if (opener) {
                panel.querySelectorAll('[data-drawer-close]').forEach(link => {
                    link.addEventListener('click', event => {
                        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
                        event.preventDefault();
                        close();
                    });
                });
            }
            (panel.querySelector('[aria-invalid="true"]') || panel.querySelector('h3')).focus();
        }
        bindCalculation(panel);
    }

    function bindCalculation(panel) {
        const form = panel.querySelector('[data-stock-editor]');
        const summary = form.querySelector('[data-live-stock]');
        if (!summary) return;

        const amount = form.elements.namedItem('amount');
        const change = form.elements.namedItem('change');
        const before = Number(form.dataset.before);
        const delta = summary.querySelector('[data-stock-delta]');
        const after = summary.querySelector('[data-stock-after]');
        const error = form.querySelector('[data-stock-invalid]');
        const label = form.querySelector('[data-amount-label]');
        const hint = form.querySelector('#store-amount-hint');
        summary.hidden = false;

        function refresh() {
            const count = change.value === 'COUNT';
            const out = change.value === 'ISSUE' || change.value === 'DAMAGE';
            label.textContent = count ? '최종 실사 수량' : out ? '차감 수량' : '추가 수량';
            hint.textContent = count ? '실제로 센 재고를 입력하세요.' : out ? '현재 재고에서 뺄 수량을 입력하세요.' : '현재 재고에 더할 수량을 입력하세요.';
            amount.min = count ? '0' : '1';

            const value = amount.valueAsNumber;
            const difference = count ? value - before : out ? -value : value;
            const next = before + difference;
            const outOfRange = Number.isInteger(value) && (next < 0 || next > 2147483647);
            const noChange = Number.isInteger(value) && next === before;
            amount.setCustomValidity('');
            const valid = amount.validity.valid && !outOfRange && !noChange;
            delta.textContent = valid ? (difference > 0 ? '+' : '') + difference + '개' : '—';
            after.textContent = valid ? next + '개' : '—';
            const message = outOfRange ? '변경 후 재고는 0개 이상이어야 하며 최대 허용 수량을 넘을 수 없습니다.'
                : noChange ? '현재와 다른 수량을 입력하세요.' : '';
            amount.setCustomValidity(message);
            if (error.textContent !== message) error.textContent = message;
            error.hidden = !message;
        }

        amount.addEventListener('input', refresh);
        change.addEventListener('change', refresh);
        refresh();
    }
})();
