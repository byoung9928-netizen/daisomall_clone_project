(() => {
    const root = document.querySelector('.store-manager-inventory');
    if (!root) return;

    const selected = new Map();
    const currentStore = root.querySelector('[data-store-view-id]').value;
    const scrollKey = 'store-inventory-threshold:' + location.pathname;
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
        const button = root.querySelector('[data-threshold-bulk]');
        const clear = root.querySelector('[data-store-selection-clear]');
        if (count) count.textContent = message || '선택 ' + selected.size + '개';
        if (button) button.disabled = selected.size === 0;
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
        const limited = thresholdForm()?.dataset.batchLimit;
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
        form.querySelectorAll('[data-threshold-after]').forEach(cell => cell.textContent = value);
    }

    function openThreshold(items) {
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
            row.insertCell().textContent = item.threshold;
            row.insertCell().setAttribute('data-threshold-after', '');
        });
        if (items.length === 1) form.elements.threshold.value = items[0].threshold;
        thresholdEstimate(form);
        form.closest('dialog').showModal();
        form.elements.threshold.focus();
    }

    const adjustmentLabels = { RECEIVE: '추가할 수량', DAMAGE: '차감할 수량', COUNT: '최종 재고 수량' };

    function adjustmentEstimate(dialog) {
        const before = Number(dialog.dataset.quantity);
        const type = dialog.querySelector('[data-adjust-type]').value;
        const input = dialog.querySelector('[data-adjust-amount]');
        const reason = dialog.querySelector('[data-adjust-reason]').value.trim();
        const result = dialog.querySelector('[data-adjust-estimate]');
        input.min = type === 'COUNT' ? '0' : '1';
        dialog.querySelector('[data-adjust-label]').textContent = adjustmentLabels[type];
        if (!input.value || !input.validity.valid) {
            result.textContent = '허용 범위의 정수 수량을 입력해 주세요.';
            return;
        }
        const amount = Number(input.value);
        const after = type === 'COUNT' ? amount : before + (type === 'DAMAGE' ? -amount : amount);
        if (after < 0 || after > 2147483647) {
            result.textContent = '변경 후 재고가 허용 범위를 벗어납니다.';
            return;
        }
        const delta = after - before;
        result.textContent = '변경량 ' + (delta > 0 ? '+' : '') + delta + '개 · 예상 재고 ' + after + '개'
            + (delta === 0 ? ' · 수량이 그대로입니다.' : '') + (reason ? '' : ' · 사유를 입력해 주세요.');
    }

    root.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        if (button.matches('[data-store-dialog-close]')) button.closest('dialog').close();
        else if (button.matches('[data-store-selection-clear]')) {
            selected.clear();
            syncSelection();
        } else if (button.matches('[data-threshold-one]')) openThreshold([rowData(button)]);
        else if (button.matches('[data-threshold-bulk]')) openThreshold([...selected.values()]);
        else if (button.matches('[data-adjust-preview]')) {
            const dialog = root.querySelector('#store-adjust-preview');
            const info = rowData(button);
            dialog.dataset.quantity = info.quantity;
            dialog.querySelector('[data-adjust-product]').textContent = info.name;
            dialog.querySelector('[data-adjust-current]').textContent = info.quantity;
            dialog.querySelector('[data-adjust-type]').value = 'RECEIVE';
            dialog.querySelector('[data-adjust-amount]').value = '';
            dialog.querySelector('[data-adjust-reason]').value = '';
            adjustmentEstimate(dialog);
            dialog.showModal();
            dialog.querySelector('[data-adjust-amount]').focus();
        }
    });

    root.addEventListener('input', event => {
        const dialog = event.target.closest('#store-adjust-preview');
        if (dialog) adjustmentEstimate(dialog);
        const form = event.target.closest('[data-store-threshold-form]');
        if (!form) return;
        if (event.target === form.elements.threshold) thresholdEstimate(form);
        if (event.target === form.elements.reason) form.elements.reason.setCustomValidity('');
    });

    root.addEventListener('submit', event => {
        if (event.defaultPrevented || !event.target.matches('[data-store-threshold-form]')) return;
        const reason = event.target.elements.reason;
        if (!reason.value.trim()) {
            event.preventDefault();
            reason.setCustomValidity('변경 사유를 입력해 주세요.');
            reason.reportValidity();
            delete event.target.dataset.adminSubmitting;
            return;
        }
        try {
            const table = root.querySelector('.store-inventory-table');
            sessionStorage.setItem(scrollKey, JSON.stringify({
                x: window.scrollX, y: window.scrollY, left: table.scrollLeft, top: table.scrollTop
            }));
        } catch { /* The inventory remains usable without session storage. */ }
    });

    document.addEventListener('admin-query-render', () => {
        if (root.querySelector('[data-store-view-id]').value !== currentStore) {
            selected.clear();
            location.reload();
            return;
        }
        syncSelection();
    });

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
