(() => {
    const root = document.querySelector('[data-admin-query="inventory"], [data-admin-query="stores"]');
    if (!root) return;

    const maxAmount = 2147483647;
    const operations = {
        RECEIVE: ['입고 수량', '변경 사유', '선택한 상품의 재고에 입력한 수량을 더합니다.'],
        ISSUE: ['출고 수량', '변경 사유', '선택한 상품의 재고에서 입력한 수량을 뺍니다.'],
        THRESHOLD: ['새 부족 기준', '변경 사유', '선택한 상품의 재고 부족 기준을 바꿉니다.'],
        TRANSFER: ['이동 수량', '이동 사유', '한 상품의 재고를 출고 매장에서 입고 매장으로 이동합니다.']
    };

    function setup() {
        const form = root.querySelector('[data-batch-form]');
        const selectAll = root.querySelector('[data-batch-select-all]');
        if (!form || !selectAll || form.dataset.batchReady) return;
        form.dataset.batchReady = 'true';

        const panel = form.closest('[data-batch-panel]');
        const boxes = [...root.querySelectorAll('[data-batch-product]')];
        const clear = panel.querySelector('[data-batch-clear]');
        const modeField = panel.querySelector('[data-batch-mode]');
        const sharedField = panel.querySelector('[data-batch-shared]');
        const individualFields = panel.querySelector('[data-batch-individual]');
        const sourceField = panel.querySelector('[data-batch-source]');
        const destinationField = panel.querySelector('[data-batch-destination]');
        const status = panel.querySelector('[data-batch-error]');
        const dialog = panel.querySelector('[data-batch-dialog]');
        const previewButton = form.querySelector('button[type="submit"]');
        const { operation, amountMode, amount, reason, destination } = form.elements;
        const selection = window.DaisoAdminBatchSelection.bind({
            form, panel, boxes, selectAll, onChange: () => { changed(); refresh(); }
        });
        const saved = selection.draft;
        const drafts = saved.amounts ??= new Map();
        if (saved.operation) operation.value = saved.operation;
        if (saved.mode) amountMode.value = saved.mode;
        reason.value = saved.reason || '';
        if (destination) destination.value = saved.destination || '';
        let currentOperation = operation.value;

        const draft = () => {
            if (!drafts.has(currentOperation)) drafts.set(currentOperation, { shared: '', individual: new Map() });
            return drafts.get(currentOperation);
        };
        amount.value = draft().shared;

        form.noValidate = true;
        clear.hidden = false;
        selectAll.hidden = boxes.length === 0;

        function clearError() {
            panel.querySelectorAll('.batch-field-error').forEach(error => error.remove());
            [selectAll, ...form.querySelectorAll('[aria-invalid="true"]')].forEach(field => {
                field.removeAttribute('aria-invalid');
                field.removeAttribute('aria-errormessage');
            });
            status.textContent = '';
            status.hidden = true;
        }

        function showError(field, message) {
            clearError();
            const container = field?.closest('.field');
            if (container) {
                const error = document.createElement('span');
                error.id = 'inventory-batch-field-error';
                error.className = 'batch-field-error';
                error.setAttribute('role', 'alert');
                error.textContent = message;
                container.append(error);
                field.setAttribute('aria-invalid', 'true');
                field.setAttribute('aria-errormessage', error.id);
            } else {
                status.textContent = message;
                status.hidden = false;
            }
            (field || status).focus({ preventScroll: true });
        }

        function refresh() {
            const selected = selection.values();
            const transfer = operation.value === 'TRANSFER';
            const individual = !transfer && amountMode.value === 'INDIVIDUAL';
            const [amountLabel, reasonLabel, hint] = operations[operation.value];
            modeField.hidden = transfer;
            amountMode.disabled = transfer;
            sharedField.hidden = individual;
            amount.disabled = individual;
            if (destination) {
                sourceField.hidden = !transfer;
                destinationField.hidden = !transfer;
                destination.disabled = !transfer;
            }
            panel.querySelector('[data-batch-amount-label]').textContent = amountLabel;
            panel.querySelector('[data-batch-reason-label]').textContent = reasonLabel;
            panel.querySelector('[data-batch-hint]').textContent = hint;
            individualFields.hidden = !individual;
            individualFields.replaceChildren();

            if (!individual) return;
            for (const product of selected) {
                const line = document.createElement('div');
                line.className = 'inventory-batch-item';
                const item = document.createElement('div');
                const name = document.createElement('strong');
                name.textContent = product.name;
                const stock = document.createElement('small');
                stock.textContent = (selection.isVisible(product.id) ? '현재 재고 ' : '마지막 조회 재고 ') + product.stock + '개';
                if (operation.value === 'THRESHOLD') stock.textContent += ' · 기존 부족 기준 ' + product.threshold + '개';
                item.append(name, stock);
                const label = document.createElement('label');
                label.className = 'field';
                label.textContent = amountLabel;
                const input = document.createElement('input');
                input.type = 'number';
                input.min = '1';
                input.max = String(maxAmount);
                input.step = '1';
                input.inputMode = 'numeric';
                input.required = true;
                input.name = 'amount.' + product.id;
                input.value = draft().individual.get(product.id) || '';
                input.setAttribute('aria-label', product.name + ' ' + amountLabel);
                input.addEventListener('input', () => draft().individual.set(product.id, input.value));
                label.append(input);
                line.append(item, label);
                individualFields.append(line);
            }
        }

        function changed() {
            form.dispatchEvent(new Event('admin-batch-input-changed', { bubbles: true }));
            clearError();
        }

        function resetSelection() {
            drafts.clear();
            delete saved.operation;
            delete saved.mode;
            delete saved.reason;
            delete saved.destination;
            form.reset();
            currentOperation = operation.value;
            selection.clear();
        }
        clear.addEventListener('click', () => {
            resetSelection();
            (selectAll.hidden ? root.querySelector('[data-admin-query-form] input[type="search"]') : selectAll)?.focus();
        });
        form.addEventListener('admin-batch-reset', resetSelection);
        form.addEventListener('input', changed);
        amount.addEventListener('input', () => { draft().shared = amount.value; });
        reason.addEventListener('input', () => { saved.reason = reason.value; });
        destination?.addEventListener('change', () => { saved.destination = destination.value; changed(); });
        operation.addEventListener('change', () => {
            currentOperation = saved.operation = operation.value;
            amount.value = draft().shared;
            changed();
            refresh();
        });
        amountMode.addEventListener('change', () => {
            saved.mode = amountMode.value;
            changed();
            refresh();
        });

        function validate() {
            const selected = selection.values();
            const type = operation.value;
            const label = operations[type][0];
            if (!selected.length || selected.length > selection.limit) {
                return [selectAll, '전체 페이지에서 상품을 1~' + selection.limit + '개 선택해 주세요.'];
            }
            if (type === 'TRANSFER') {
                if (selected.length !== 1) return [selectAll, '매장 간 이동은 상품 하나만 선택해 주세요.'];
                if (!destination.value || destination.value === form.elements.store.value) {
                    return [destination, '출고 매장과 다른 입고 매장을 선택해 주세요.'];
                }
            }
            if (!reason.value.trim()) return [reason, operations[type][1] + '를 입력해 주세요.'];
            for (const product of selected) {
                const input = amount.disabled ? form.elements.namedItem('amount.' + product.id) : amount;
                const value = input.valueAsNumber;
                if (!input.value && !input.validity.badInput) return [input, label + '을 입력해 주세요.'];
                if (!Number.isInteger(value) || value < 1 || value > maxAmount) {
                    return [input, '1~2,147,483,647 사이의 정수를 입력해 주세요.'];
                }
                if (!selection.isVisible(product.id)) continue;
                if ((type === 'ISSUE' || type === 'TRANSFER') && value > Number(product.stock)) {
                    return [input, product.name + '의 현재 재고보다 많은 수량을 출고할 수 없습니다.'];
                }
                if (type === 'RECEIVE' && value + Number(product.stock) > maxAmount) {
                    return [input, '입고 후 재고가 허용 범위를 벗어납니다.'];
                }
            }
            return null;
        }

        form.addEventListener('submit', event => {
            const error = validate();
            if (error) {
                event.preventDefault();
                showError(...error);
                return;
            }
            if (typeof dialog.showModal !== 'function') {
                form.dataset.nativeSubmit = 'true';
                return;
            }
            event.preventDefault();
            clearError();
            form.dispatchEvent(new CustomEvent('admin-batch-request-preview', {
                bubbles: true,
                detail: { dialog, opener: previewButton, noticeId: 'inventory-batch-result',
                    refreshLabel: '최신 재고 보기',
                    showError: (name, message) => showError(
                        name === 'batchProduct' ? selectAll : form.elements.namedItem(name), message) }
            }));
        });
        refresh();
    }

    document.addEventListener('admin-query-render', setup);
    setup();
})();
