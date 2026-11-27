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
        if (!form || event.defaultPrevented) return;
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
            const table = document.getElementById('store-inventory-table');
            if (!source || !table) throw new Error('Store adjustment dialog unavailable');
            const panel = document.importNode(source, true);
            table.before(panel);
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
        const thresholdForm = panel.querySelector('[data-store-threshold]');
        if (thresholdForm) {
            const input = thresholdForm.elements.namedItem('threshold');
            const output = thresholdForm.querySelector('[data-threshold-after]');
            const updateThreshold = () => {
                output.textContent = input.validity.valid ? input.valueAsNumber + '개 이하' : '—';
            };
            input.addEventListener('input', updateThreshold);
            updateThreshold();
            thresholdForm.querySelector('[data-threshold-preview]').hidden = false;
        }

        const form = panel.querySelector('[data-stock-editor]');
        const summary = form.querySelector('[data-live-stock]');
        if (!summary) return;

        const amount = form.elements.namedItem('amount');
        const change = form.elements.namedItem('change') || form.elements.namedItem('operation');
        const listedQuantity = Number(form.dataset.before);
        const details = form.querySelector('[data-count-details]');
        const previewButton = form.querySelector('button[value="preview"]');
        let countRequest;
        let baseline;

        async function loadStocktake() {
            countRequest?.abort();
            const request = new AbortController();
            countRequest = request;
            baseline = undefined;
            form.elements.countBaseline.value = '';
            amount.value = '';
            const status = details.querySelector('[data-count-status]');
            status.textContent = '미수령 픽업 할당 수량을 확인하는 중입니다.';
            refresh();
            const url = new URL(form.dataset.countUrl, location.origin);
            for (const name of ['city', 'store', 'product']) url.searchParams.set(name, form.elements[name].value);
            try {
                const response = await fetch(url, { signal: request.signal, cache: 'no-store' });
                const result = await response.json();
                if (countRequest !== request || !form.isConnected) return;
                if (response.ok && result.baseline && result.store === form.elements.store.value
                        && result.item === form.elements.product.value) {
                    baseline = result;
                    form.elements.countBaseline.value = result.baseline;
                    form.elements.version.value = result.version;
                    status.textContent = '';
                } else {
                    status.textContent = result.message || '실사 기준을 확인하지 못했습니다. 다시 조회해 주세요.';
                }
            } catch {
                if (countRequest !== request || !form.isConnected) return;
                status.textContent = '실사 기준을 확인하지 못했습니다. 다시 조회해 주세요.';
            }
            refresh();
        }
        const delta = summary.querySelector('[data-stock-delta]');
        const after = summary.querySelector('[data-stock-after]');
        const error = form.querySelector('[data-stock-invalid]');
        const label = form.querySelector('[data-amount-label]');
        const hint = form.querySelector('#store-amount-hint');
        summary.hidden = false;

        function refresh() {
            const count = change.value === 'COUNT';
            const stocktake = !!details && count;
            const before = stocktake ? baseline?.available : listedQuantity;
            const out = change.value === 'ISSUE' || change.value === 'DAMAGE';
            label.textContent = stocktake ? '실제 보유 수량' : count ? '최종 실사 수량' : out ? '차감 수량' : '추가 수량';
            if (details) {
                details.hidden = !stocktake;
                details.querySelector('[data-count-allocated]').textContent = baseline ? baseline.allocated + '개' : '—';
                details.querySelector('[data-count-physical]').textContent = baseline ? baseline.estimatedPhysical + '개' : '—';
                details.querySelector('[data-count-checked]').textContent = baseline ? new Date(baseline.checkedAt)
                    .toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }) + ' (한국시간)' : '—';
                form.querySelector('[data-count-available]').textContent = before == null ? '—' : before + '개';
                if (previewButton) previewButton.disabled = stocktake && !baseline;
                amount.disabled = stocktake && !baseline;
            }
            hint.textContent = count ? '실제로 센 재고를 입력하세요.' : out ? '현재 재고에서 뺄 수량을 입력하세요.' : '현재 재고에 더할 수량을 입력하세요.';
            amount.min = count ? '0' : '1';

            const value = amount.valueAsNumber;
            const difference = count ? value - (stocktake ? baseline?.allocated : 0) - before : out ? -value : value;
            const next = before + difference;
            const outOfRange = Number.isInteger(value) && (next < 0 || next > 2147483647);
            const noChange = !stocktake && Number.isInteger(value) && next === before;
            const invalidPhysical = stocktake && amount.value !== '' && !/^[0-9]{1,10}$/.test(amount.value);
            amount.setCustomValidity('');
            const valid = Number.isInteger(next) && amount.validity.valid && !outOfRange && !noChange && !invalidPhysical;
            delta.textContent = valid ? (difference > 0 ? '+' : '') + difference + '개' : '—';
            after.textContent = valid ? next + '개' : '—';
            const message = invalidPhysical ? '실제 보유 수량을 정수로 입력해 주세요.'
                : stocktake && next < 0 ? '실제 보유 수량은 미수령 픽업 할당보다 적을 수 없습니다.'
                : outOfRange ? '변경 후 재고는 0개 이상이어야 하며 최대 허용 수량을 넘을 수 없습니다.'
                : noChange ? '현재와 다른 수량을 입력하세요.' : '';
            amount.setCustomValidity(message);
            if (error.textContent !== message) error.textContent = message;
            error.hidden = !message;
        }

        amount.addEventListener('input', refresh);
        change.addEventListener('change', () => {
            if (details && change.value === 'COUNT') {
                void loadStocktake();
                return;
            }
            countRequest?.abort();
            countRequest = undefined;
            baseline = undefined;
            if (details) {
                form.elements.countBaseline.value = '';
                form.elements.version.value = form.elements.version.defaultValue;
            }
            refresh();
        });
        if (details) details.querySelector('[data-count-refresh]').addEventListener('click', loadStocktake);
        if (details && change.value === 'COUNT') void loadStocktake();
        else refresh();
    }
})();
