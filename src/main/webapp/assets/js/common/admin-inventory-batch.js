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
        const count = panel.querySelector('[data-batch-count]');
        const modeField = panel.querySelector('[data-batch-mode]');
        const sharedField = panel.querySelector('[data-batch-shared]');
        const individualFields = panel.querySelector('[data-batch-individual]');
        const sourceField = panel.querySelector('[data-batch-source]');
        const destinationField = panel.querySelector('[data-batch-destination]');
        const status = panel.querySelector('[data-batch-error]');
        const dialog = panel.querySelector('[data-batch-dialog]');
        const previewButton = form.querySelector('button[type="submit"]');
        const { operation, amountMode, amount, reason, destination } = form.elements;
        const drafts = new Map();
        let pendingPreview;
        let saving = false;
        let currentOperation = operation.value;

        const checked = () => boxes.filter(box => box.checked);
        const draft = () => {
            if (!drafts.has(currentOperation)) drafts.set(currentOperation, { shared: '', individual: new Map() });
            return drafts.get(currentOperation);
        };

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
            (field || status).focus();
        }

        function refresh() {
            const selected = checked();
            const transfer = operation.value === 'TRANSFER';
            const individual = !transfer && amountMode.value === 'INDIVIDUAL';
            const [amountLabel, reasonLabel, hint] = operations[operation.value];
            panel.hidden = selected.length === 0;
            count.textContent = '상품 ' + selected.length + '개 선택';
            selectAll.checked = boxes.length > 0 && selected.length === boxes.length;
            selectAll.indeterminate = selected.length > 0 && selected.length < boxes.length;
            for (const box of boxes) box.closest('tr').toggleAttribute('data-batch-selected', box.checked);

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
            for (const box of selected) {
                const line = document.createElement('div');
                line.className = 'inventory-batch-item';
                const item = document.createElement('div');
                const name = document.createElement('strong');
                name.textContent = box.dataset.name;
                const stock = document.createElement('small');
                stock.textContent = '현재 재고 ' + box.dataset.stock + '개';
                if (operation.value === 'THRESHOLD') stock.textContent += ' · 기존 부족 기준 ' + box.dataset.threshold + '개';
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
                input.name = 'amount.' + box.value;
                input.value = draft().individual.get(box.value) || '';
                input.setAttribute('aria-label', box.dataset.name + ' ' + amountLabel);
                input.addEventListener('input', () => draft().individual.set(box.value, input.value));
                label.append(input);
                line.append(item, label);
                individualFields.append(line);
            }
        }

        function changed() {
            pendingPreview?.abort();
            clearError();
        }

        for (const box of boxes) box.addEventListener('change', () => {
            changed();
            if (checked().length > 20) box.checked = false;
            refresh();
        });
        selectAll.addEventListener('change', () => {
            const select = selectAll.checked;
            boxes.forEach((box, index) => { box.checked = select && index < 20; });
            changed();
            refresh();
        });
        clear.addEventListener('click', () => {
            boxes.forEach(box => { box.checked = false; });
            drafts.clear();
            form.reset();
            currentOperation = operation.value;
            changed();
            refresh();
            selectAll.focus();
        });
        form.addEventListener('input', changed);
        amount.addEventListener('input', () => { draft().shared = amount.value; });
        operation.addEventListener('change', () => {
            currentOperation = operation.value;
            amount.value = draft().shared;
            changed();
            refresh();
        });
        amountMode.addEventListener('change', () => {
            changed();
            refresh();
        });

        function validate() {
            const selected = checked();
            const type = operation.value;
            const label = operations[type][0];
            if (!selected.length || selected.length > 20) return [selectAll, '현재 페이지에서 상품을 1~20개 선택해 주세요.'];
            if (type === 'TRANSFER') {
                if (selected.length !== 1) return [selectAll, '매장 간 이동은 상품 하나만 선택해 주세요.'];
                if (!destination.value || destination.value === form.elements.store.value) {
                    return [destination, '출고 매장과 다른 입고 매장을 선택해 주세요.'];
                }
            }
            if (!reason.value.trim()) return [reason, operations[type][1] + '를 입력해 주세요.'];
            for (const box of selected) {
                const input = amount.disabled ? form.elements.namedItem('amount.' + box.value) : amount;
                const value = input.valueAsNumber;
                if (!input.value && !input.validity.badInput) return [input, label + '을 입력해 주세요.'];
                if (!Number.isInteger(value) || value < 1 || value > maxAmount) {
                    return [input, '1~2,147,483,647 사이의 정수를 입력해 주세요.'];
                }
                if ((type === 'ISSUE' || type === 'TRANSFER') && value > Number(box.dataset.stock)) {
                    return [input, box.dataset.name + '의 현재 재고보다 많은 수량을 출고할 수 없습니다.'];
                }
                if (type === 'RECEIVE' && value + Number(box.dataset.stock) > maxAmount) {
                    return [input, '입고 후 재고가 허용 범위를 벗어납니다.'];
                }
            }
            return null;
        }

        function nativePreview() {
            form.dataset.nativeSubmit = 'true';
            form.requestSubmit(previewButton);
        }

        dialog.addEventListener('cancel', event => {
            if (saving) event.preventDefault();
        });
        dialog.addEventListener('close', () => {
            dialog.replaceChildren();
            if (previewButton.isConnected) previewButton.focus({ preventScroll: true });
        });

        function showPreview(preview) {
            dialog.replaceChildren(document.importNode(preview, true));
            const close = dialog.querySelector('[data-batch-close]');
            const revise = dialog.querySelector('[data-batch-revise]');
            const confirmation = dialog.querySelector('[data-batch-confirm]');
            close.hidden = false;
            close.addEventListener('click', () => dialog.close());
            revise.addEventListener('click', event => {
                event.preventDefault();
                if (!saving) dialog.close();
            });
            confirmation.addEventListener('submit', async event => {
                event.preventDefault();
                const accept = event.submitter || confirmation.querySelector('button[type="submit"]');
                if (saving || accept.disabled) return;
                saving = true;
                accept.disabled = true;
                close.disabled = true;
                revise.setAttribute('aria-disabled', 'true');
                const warning = dialog.querySelector('[data-batch-confirm-error]');
                try {
                    const response = await fetch(confirmation.action, {
                        method: 'POST', body: new URLSearchParams(new FormData(confirmation)), cache: 'no-store'
                    });
                    const html = new DOMParser().parseFromString(await response.text(), 'text/html');
                    const next = html.querySelector('[data-admin-query-content]');
                    const destinationUrl = new URL(response.url);
                    if (response.ok && response.redirected && destinationUrl.origin === location.origin
                            && destinationUrl.pathname === location.pathname
                            && html.querySelector('[data-admin-query]')?.dataset.adminQuery === root.dataset.adminQuery
                            && next?.querySelector('#inventory-batch-result')) {
                        destinationUrl.searchParams.delete('batchSaved');
                        dialog.close();
                        root.dispatchEvent(new CustomEvent('admin-inventory-batch-saved', {
                            detail: { next, destination: destinationUrl }
                        }));
                        return;
                    }
                    warning.textContent = html.querySelector('[data-batch-server-error]')?.textContent.trim()
                        || '저장 결과를 확인하지 못했습니다. 목록의 최신 재고를 확인해 주세요.';
                } catch {
                    warning.textContent = '연결이 끊겨 저장 결과를 확인할 수 없습니다. 최신 재고를 확인해 주세요.';
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
                refreshLink.textContent = '최신 재고 보기';
                confirmation.prepend(refreshLink);
            });
            dialog.showModal();
            dialog.querySelector('h1').focus();
        }

        form.addEventListener('submit', async event => {
            if (form.dataset.nativeSubmit) return;
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
            if (pendingPreview || root.querySelector('[data-admin-query-content][aria-busy="true"]')) return;
            clearError();
            const controller = new AbortController();
            pendingPreview = controller;
            previewButton.disabled = true;
            let fallback = false;
            try {
                const response = await fetch(form.action, {
                    method: 'POST', body: new URLSearchParams(new FormData(form)),
                    cache: 'no-store', signal: controller.signal
                });
                const html = new DOMParser().parseFromString(await response.text(), 'text/html');
                if (!form.isConnected || controller.signal.aborted) return;
                const error = html.querySelector('[data-batch-server-error]');
                const preview = html.querySelector('[data-batch-preview]');
                if (error) {
                    const name = error.dataset.field;
                    const field = name === 'batchProduct' ? selectAll : form.elements.namedItem(name);
                    showError(field, error.textContent.trim());
                } else if (response.ok && preview) {
                    showPreview(preview);
                } else {
                    fallback = true;
                }
            } catch {
                fallback = !controller.signal.aborted;
            } finally {
                pendingPreview = undefined;
                previewButton.disabled = false;
                if (fallback && form.isConnected) nativePreview();
            }
        });
        refresh();
    }

    document.addEventListener('admin-query-render', setup);
    setup();
})();
