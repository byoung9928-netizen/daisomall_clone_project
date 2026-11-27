(() => {
    const root = document.querySelector('.store-manager-inventory');
    if (!root) return;

    const selected = new Map();
    const format = new Intl.NumberFormat('ko-KR');
    const content = root.querySelector('[data-admin-query-content]');
    const queryStatus = root.querySelector('[data-store-query-status]');
    const triggers = new WeakMap();
    let frame = 0;

    new MutationObserver(() => {
        queryStatus.textContent = content.hasAttribute('aria-busy') ? (root.dataset.loadingLabel || '재고를 조회하는 중입니다.') : '';
    }).observe(content, { attributes: true, attributeFilter: ['aria-busy'] });

    function updateTableHeader() {
        if (frame) return;
        frame = requestAnimationFrame(() => {
            frame = 0;
            const region = root.querySelector('.store-inventory-table');
            const head = region?.querySelector('thead');
            if (!head) return;
            const bounds = region.getBoundingClientRect();
            const height = head.offsetHeight;
            const offset = Math.max(0, Math.min(-bounds.top, bounds.height - height * 2));
            region.style.setProperty('--store-table-head-offset', offset + 'px');
        });
    }
    window.addEventListener('scroll', updateTableHeader, { passive: true });
    window.addEventListener('resize', updateTableHeader);

    root.addEventListener('error', event => {
        const image = event.target;
        if (image instanceof HTMLImageElement && image.matches('.store-inventory-thumb, .admin-product-suggest-button img')
                && image.getAttribute('src') !== root.dataset.placeholder) {
            image.src = root.dataset.placeholder;
        }
    }, true);

    function openDialog(form, trigger, field) {
        const dialog = form.closest('dialog');
        triggers.set(dialog, trigger);
        dialog.showModal();
        field.focus({ preventScroll: true });
    }
    let countRequest;
    let countSnapshot;
    const currentStore = root.querySelector('[data-store-view-id]').value;
    const scrollKey = 'store-inventory-save:' + location.pathname;
    const thresholdForm = () => root.querySelector('[data-store-threshold-form]');
    const choices = () => [...root.querySelectorAll('[data-store-select]')];
    const rowData = checkbox => checkbox.closest('[data-store-stock]').dataset;

    function syncSelection(message = '') {
        const controls = choices();
        controls.forEach(control => {
            const info = rowData(control);
            control.checked = selected.has(info.productId);
            if (control.checked) selected.set(info.productId, info);
        });
        const count = root.querySelector('[data-store-selection-count]');
        const buttons = root.querySelectorAll('[data-threshold-bulk], [data-receive-bulk], [data-assortment-bulk]');
        const clear = root.querySelector('[data-store-selection-clear]');
        if (count) count.textContent = message || '선택 ' + selected.size + '개';
        buttons.forEach(button => button.disabled = selected.size === 0 || (button.hasAttribute('data-assortment-bulk')
            && [...selected.values()].some(info => info.operation !== button.dataset.assortmentBulk)));
        if (clear) clear.hidden = selected.size === 0;
        const all = root.querySelector('[data-store-select-page]');
        if (all) {
            const checked = controls.filter(input => input.checked).length;
            all.checked = controls.length > 0 && checked === controls.length;
            all.indeterminate = checked > 0 && !all.checked;
        }
    }

    root.addEventListener('change', event => {
        const control = event.target;
        const limited = thresholdForm()?.dataset.batchLimit || root.dataset.batchLimit;
        if (!limited) return;
        const limit = Number(limited);
        if (control.matches('[data-store-select]')) {
            const info = rowData(control);
            if (!control.checked) selected.delete(info.productId);
            else if (selected.size < limit) selected.set(info.productId, info);
            else {
                syncSelection('상품은 최대 ' + limit + '개까지 선택할 수 있습니다.');
                return;
            }
            syncSelection();
        } else if (control.matches('[data-store-select-page]')) {
            const current = choices().map(rowData);
            if (!control.checked) current.forEach(info => selected.delete(info.productId));
            else for (const info of current) {
                if (selected.size < limit) selected.set(info.productId, info);
            }
            const truncated = control.checked && current.some(info => !selected.has(info.productId));
            syncSelection(truncated ? '상품은 최대 ' + limit + '개까지 선택할 수 있습니다.' : '');
        }
    });

    root.addEventListener('change', event => {
        if (!event.target.matches('[data-adjust-type]')) return;
        const dialog = event.target.closest('dialog');
        if (event.target.value === 'COUNT') void loadCountPreview(dialog);
        else {
            clearCountPreview();
            adjustmentEstimate(dialog);
        }
    });

    root.addEventListener('close', event => {
        const dialog = event.target;
        if (dialog.open) return;
        if (dialog.id === 'store-adjust-preview') clearCountPreview();
        const trigger = triggers.get(dialog);
        if (trigger?.isConnected) trigger.focus({ preventScroll: true });
    }, true);

    function hiddenField(name, value) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        return input;
    }

    function thresholdEstimate(form) {
        const input = form.elements.threshold;
        const value = input.value && input.validity.valid ? input.value : '—';
        form.querySelectorAll('[data-threshold-after]').forEach(cell => cell.textContent = value === '—' ? value : format.format(value) + '개');
    }

    function openThreshold(items, trigger) {
        const form = thresholdForm();
        if (!form || !items.length) return;
        form.reset();
        form.elements.reason.setCustomValidity('');
        form.querySelector('[data-threshold-count]').textContent = items.length;
        const fields = form.querySelector('[data-threshold-items]');
        const preview = form.querySelector('[data-threshold-preview]');
        fields.replaceChildren();
        preview.replaceChildren();
        items.forEach(item => {
            fields.append(hiddenField('item', item.productId), hiddenField('version', item.version));
            const row = preview.insertRow();
            row.insertCell().textContent = item.name;
            row.insertCell().textContent = format.format(item.threshold) + '개';
            row.insertCell().setAttribute('data-threshold-after', '');
        });
        if (items.length === 1) form.elements.threshold.value = items[0].threshold;
        thresholdEstimate(form);
        openDialog(form, trigger, form.elements.threshold);
    }

    function batchEstimate(form) {
        let total = 0;
        let complete = true;
        const limit = Number(form.dataset.batchLimit);
        for (const input of form.querySelectorAll('input[name="amount"]')) {
            const afterCell = input.closest('tr').querySelector('[data-receive-after]');
            const amount = Number(input.value);
            const after = Number(input.dataset.quantity) + amount;
            const entered = !!input.value && input.validity.valid;
            const valid = entered && after <= 2147483647;
            const error = input.closest('td').querySelector('.store-amount-error');
            error.textContent = input.value && !valid ? '허용 범위의 정수를 확인해 주세요.' : '';
            input.setAttribute('aria-invalid', error.textContent ? 'true' : 'false');
            afterCell.textContent = valid ? format.format(after) + '개' : '—';
            complete = complete && valid;
            if (valid) total += amount;
        }
        const count = form.querySelectorAll('input[name="amount"]').length;
        form.querySelector('[data-receive-total]').textContent = complete
            ? '총 입고 ' + format.format(total) + '개' : '상품별 입고 수량을 확인해 주세요.';
        form.querySelector('button[type="submit"]').disabled = form.dataset.saveEnabled !== 'true'
            || !complete || count === 0 || count > limit || !form.elements.reason.value.trim();
    }

    function openReceive(items, trigger) {
        const form = root.querySelector('[data-batch-receive-form]');
        if (!form || !items.length) return;
        form.reset();
        form.elements.reason.setCustomValidity('');
        form.querySelector('[data-receive-count]').textContent = items.length;
        const preview = form.querySelector('[data-receive-preview]');
        preview.replaceChildren();
        items.forEach(item => {
            const row = preview.insertRow();
            const product = row.insertCell();
            const detail = document.createElement('div');
            detail.className = 'store-batch-product';
            const image = document.createElement('img');
            image.src = item.image;
            image.alt = '';
            image.width = image.height = 44;
            image.className = 'store-inventory-thumb';
            const description = document.createElement('span');
            description.textContent = item.name + ' · ' + item.code;
            detail.append(image, description);
            product.append(detail);
            row.insertCell().textContent = format.format(item.quantity) + '개';

            const amount = document.createElement('input');
            amount.type = 'number';
            amount.name = 'amount';
            amount.min = '1';
            amount.max = '2147483647';
            amount.required = true;
            amount.dataset.quantity = item.quantity;
            amount.setAttribute('aria-label', item.name + ' 입고 수량');
            const error = document.createElement('small');
            error.id = 'store-receive-error-' + row.rowIndex;
            error.className = 'store-amount-error';
            amount.setAttribute('aria-describedby', error.id);
            row.insertCell().append(hiddenField('item', item.productId), amount, error);
            row.insertCell().setAttribute('data-receive-after', '');
        });
        batchEstimate(form);
        openDialog(form, trigger, form.querySelector('input[name="amount"]'));
    }

    const adjustmentLabels = { RECEIVE: '추가할 수량', DAMAGE: '차감할 수량', COUNT: '실제 보유 수량' };
    const countTimeFormat = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul', dateStyle: 'short', timeStyle: 'medium'
    });

    function clearCountPreview() {
        countRequest?.abort();
        countRequest = undefined;
        countSnapshot = undefined;
    }

    async function loadCountPreview(dialog) {
        clearCountPreview();
        const form = dialog.querySelector('[data-quantity-form]');
        const status = form.querySelector('[data-count-status]');
        form.elements.countBaseline.value = '';
        if (form.dataset.countSave === 'true') form.elements.amount.value = '';
        const request = new AbortController();
        countRequest = request;
        status.textContent = '미수령 픽업 할당 수량을 확인하는 중입니다.';
        adjustmentEstimate(dialog);
        const url = new URL(form.dataset.countUrl, location.origin);
        url.searchParams.set('item', form.elements.namedItem('item').value);
        try {
            const response = await fetch(url, { signal: request.signal, cache: 'no-store' });
            const result = await response.json();
            if (countRequest !== request || !dialog.isConnected) return;
            if (response.ok && result.store !== currentStore) {
                status.textContent = '소속 매장이 변경되었습니다. 재고 목록을 다시 열어 주세요.';
            } else {
                if (response.ok) {
                    countSnapshot = result;
                    form.elements.countBaseline.value = result.baseline || '';
                }
                status.textContent = response.ok ? '' : result.message || '할당 수량을 확인할 수 없습니다.';
            }
        } catch {
            if (countRequest !== request || !dialog.isConnected) return;
            status.textContent = '할당 수량을 확인할 수 없습니다. 다시 조회해 주세요.';
        }
        adjustmentEstimate(dialog);
    }

    function adjustmentEstimate(dialog) {
        const form = dialog.querySelector('[data-quantity-form]');
        const type = form.elements.change.value;
        const isCount = type === 'COUNT';
        const before = isCount ? countSnapshot?.available : Number(dialog.dataset.quantity);
        const input = form.elements.amount;
        const reason = form.elements.reason.value.trim();
        const result = form.querySelector('[data-adjust-estimate]');
        const save = form.querySelector('button[type="submit"]');
        save.disabled = true;
        input.min = isCount ? '0' : '1';
        input.disabled = isCount && form.dataset.countSave === 'true' && !countSnapshot?.baseline;
        form.querySelector('[data-adjust-label]').textContent = adjustmentLabels[type];
        form.querySelector('[data-adjust-current]').textContent = before == null ? '—' : format.format(before);
        input.removeAttribute('aria-invalid');
        form.querySelector('[data-count-details]').hidden = !isCount;
        if (isCount) {
            form.querySelector('[data-count-allocated]').textContent = countSnapshot ? format.format(countSnapshot.allocated) + '개' : '—';
            form.querySelector('[data-count-physical]').textContent = countSnapshot ? format.format(countSnapshot.estimatedPhysical) + '개' : '—';
            form.querySelector('[data-count-checked]').textContent = countSnapshot
                ? countTimeFormat.format(new Date(countSnapshot.checkedAt)) + ' (한국시간)' : '—';
            if (!countSnapshot) {
                result.textContent = '';
                return;
            }
        }
        if (!input.value || !input.validity.valid || !Number.isInteger(Number(input.value))
                || (isCount && !/^[0-9]{1,10}$/.test(input.value))) {
            if (input.value) input.setAttribute('aria-invalid', 'true');
            result.textContent = '허용 범위의 정수 수량을 입력해 주세요.';
            return;
        }
        const amount = Number(input.value);
        const after = isCount ? amount - countSnapshot.allocated : before + (type === 'DAMAGE' ? -amount : amount);
        if (isCount && after < 0) {
            input.setAttribute('aria-invalid', 'true');
            result.textContent = '실제 보유 수량은 미수령 픽업 할당보다 적을 수 없습니다.';
            return;
        }
        if (after < 0 || after > 2147483647) {
            input.setAttribute('aria-invalid', 'true');
            result.textContent = type === 'DAMAGE' && after < 0
                ? '파손 수량은 현재 판매 가능 재고를 초과할 수 없습니다.' : '변경 후 재고가 허용 범위를 벗어납니다.';
            return;
        }
        const delta = after - before;
        result.textContent = '변경량 ' + (delta > 0 ? '+' : '') + format.format(delta) + '개 · '
            + (isCount ? '예상 판매 가능 재고 ' : '예상 재고 ') + format.format(after) + '개'
            + (delta === 0 ? ' · 수량이 그대로입니다.' : '') + (reason ? '' : ' · 사유를 입력해 주세요.');
        save.disabled = !reason || (isCount
            ? form.dataset.countSave !== 'true' || !countSnapshot.baseline
            : form.dataset.saveEnabled !== 'true' || delta === 0);
    }

    root.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        if (button.matches('[data-store-dialog-close]')) button.closest('dialog').close();
        else if (button.matches('[data-count-refresh]')) void loadCountPreview(button.closest('dialog'));
        else if (button.matches('[data-store-selection-clear]')) {
            selected.clear();
            syncSelection();
        } else if (button.matches('[data-assortment-one], [data-assortment-bulk]')) {
            const rows = button.hasAttribute('data-assortment-one') ? [rowData(button)] : [...selected.values()];
            root.dispatchEvent(new CustomEvent('store-assortment-open', { detail: { rows, button } }));
        } else if (button.matches('[data-threshold-one]')) openThreshold([rowData(button)], button);
        else if (button.matches('[data-threshold-bulk]')) openThreshold([...selected.values()], button);
        else if (button.matches('[data-receive-bulk]')) openReceive([...selected.values()], button);
        else if (button.matches('[data-adjust-preview]')) {
            const dialog = root.querySelector('#store-adjust-preview');
            const info = rowData(button);
            const form = dialog.querySelector('[data-quantity-form]');
            clearCountPreview();
            form.reset();
            form.elements.reason.setCustomValidity('');
            form.elements.namedItem('item').value = info.productId;
            dialog.dataset.quantity = info.quantity;
            dialog.querySelector('[data-adjust-product]').textContent = info.name;
            dialog.querySelector('[data-adjust-code]').textContent = [info.code, info.productId].filter(Boolean).join(' · ');
            dialog.querySelector('[data-adjust-image]').src = info.image;
            adjustmentEstimate(dialog);
            openDialog(form, button, dialog.querySelector('[data-adjust-amount]'));
        }
    });

    root.addEventListener('input', event => {
        const form = event.target.closest('[data-store-save-form]');
        if (!form) return;
        if (event.target === form.elements.reason) form.elements.reason.setCustomValidity('');
        if (form.matches('[data-store-threshold-form]') && event.target === form.elements.threshold) thresholdEstimate(form);
        if (form.matches('[data-quantity-form]')) adjustmentEstimate(form.closest('dialog'));
        if (form.matches('[data-batch-receive-form]')) batchEstimate(form);
    });

    root.addEventListener('submit', event => {
        if (event.defaultPrevented || !event.target.matches('[data-store-save-form]')) return;
        const submit = event.target.querySelector('button[type="submit"]');
        if (event.target.dataset.storeSaving === 'true') {
            event.preventDefault();
            return;
        }
        if (submit.disabled) {
            event.preventDefault();
            delete event.target.dataset.adminSubmitting;
            return;
        }
        const reason = event.target.elements.reason;
        if (!reason.value.trim()) {
            event.preventDefault();
            reason.setCustomValidity('변경 사유를 입력해 주세요.');
            reason.reportValidity();
            delete event.target.dataset.adminSubmitting;
            return;
        }
        event.target.dataset.storeSaving = 'true';
        event.target.setAttribute('aria-busy', 'true');
        submit.dataset.storeInitialLabel = submit.textContent;
        submit.textContent = '저장 중…';
        submit.disabled = true;
        try {
            const table = root.querySelector('.store-inventory-table');
            sessionStorage.setItem(scrollKey, JSON.stringify({
                x: window.scrollX, y: window.scrollY, left: table.scrollLeft, top: table.scrollTop
            }));
        } catch { /* The inventory remains usable without session storage. */ }
    });

    document.addEventListener('admin-query-render', () => {
        clearCountPreview();
        if (root.querySelector('[data-store-view-id]').value !== currentStore) {
            selected.clear();
            location.reload();
            return;
        }
        syncSelection();
        updateTableHeader();
    });
    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        root.querySelectorAll('[data-store-saving="true"]').forEach(form => {
            const submit = form.querySelector('button[type="submit"]');
            delete form.dataset.storeSaving;
            delete form.dataset.adminSubmitting;
            form.removeAttribute('aria-busy');
            submit.textContent = submit.dataset.storeInitialLabel;
            submit.disabled = false;
            if (form.matches('[data-quantity-form]')) adjustmentEstimate(form.closest('dialog'));
            else if (form.matches('[data-batch-receive-form]')) batchEstimate(form);
        });
        updateTableHeader();
    });
    updateTableHeader();

    if (root.querySelector('[data-store-inventory-result]')) {
        try {
            const position = JSON.parse(sessionStorage.getItem(scrollKey));
            sessionStorage.removeItem(scrollKey);
            if (position) requestAnimationFrame(() => {
                root.querySelector('.store-inventory-table').scrollTo(position.left, position.top);
                window.scrollTo(position.x, position.y);
            });
        } catch { /* The inventory remains usable without session storage. */ }
    }
})();
