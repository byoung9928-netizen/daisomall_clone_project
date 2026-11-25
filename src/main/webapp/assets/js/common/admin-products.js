(() => {
    const editor = document.querySelector('#product-editor');
    if (typeof editor?.showModal === 'function') {
        editor.removeAttribute('open');
        editor.showModal();
        const close = editor.querySelector('.product-editor-close');
        editor.addEventListener('cancel', (event) => {
            event.preventDefault();
            location.assign(close.href);
        });
        const fieldTargets = ['#product-status', '#product-stock'];
        const focus = fieldTargets.includes(location.hash) ? location.hash : '#product-editor-title';
        editor.querySelector(focus).focus();
    }

    const form = document.querySelector('[data-code-check]');
    if (form) {
        const fileInput = form.elements.imageFile;
        const imagePath = form.elements.image;
        const preview = form.querySelector('[data-product-image-preview]');
        const imageFeedback = form.querySelector('#product-image-feedback');
        let previewUrl;
        const imageHint = 'JPG·PNG, 최대 5MB, 가로·세로 각각 최대 4,096픽셀. 파일을 선택하면 기존 사진을 교체합니다.';

        function imageError(message) {
            fileInput.setCustomValidity(message);
            fileInput.setAttribute('aria-invalid', String(Boolean(message)));
            imageFeedback.classList.toggle('is-error', Boolean(message));
            imageFeedback.textContent = message || imageHint;
        }

        function updatePreview() {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            previewUrl = null;
            imageError('');
            preview.hidden = true;
            preview.removeAttribute('src');
            const file = fileInput.files[0];
            if (file) {
                if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024 || file.size === 0) {
                    imageError('사진은 5MB 이하의 JPG 또는 PNG 파일을 선택해 주세요.');
                    return;
                }
                previewUrl = URL.createObjectURL(file);
                preview.src = previewUrl;
                preview.hidden = false;
                imageFeedback.textContent = `${file.name} 선택됨 · 저장하면 이 사진으로 변경합니다.`;
            } else {
                const value = imagePath.value.trim();
                if (value.startsWith('assets/images/') || value.startsWith('product-images/')) {
                    preview.src = new URL(value, new URL(preview.dataset.imageBase, location.origin)).href;
                    preview.hidden = false;
                } else {
                    try {
                        const url = new URL(value);
                        if (url.protocol === 'https:') { preview.src = url.href; preview.hidden = false; }
                    } catch { /* 이미지 경로가 입력되면 미리보기를 표시한다. */ }
                }
            }
        }
        preview.addEventListener('load', () => {
            if (fileInput.files.length && (preview.naturalWidth > 4096 || preview.naturalHeight > 4096)) {
                imageError('사진의 가로와 세로는 각각 4,096픽셀 이하여야 합니다.');
            }
        });
        preview.addEventListener('error', () => {
            if (fileInput.files.length) imageError('사진을 읽을 수 없습니다. JPG 또는 PNG 파일을 다시 선택해 주세요.');
            preview.hidden = true;
        });
        fileInput.addEventListener('change', updatePreview);
        imagePath.addEventListener('input', () => { if (!fileInput.files.length) updatePreview(); });
        window.addEventListener('pagehide', () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, { once: true });
        updatePreview();

        const code = form.elements.code;
        const feedback = document.querySelector('#product-code-feedback');
        const id = form.elements.id.value;
        let timer;
        let check;

        function showCodeFeedback(message, invalid = false) {
            code.setCustomValidity(invalid ? message : '');
            code.setAttribute('aria-invalid', String(invalid));
            feedback.classList.toggle('is-error', invalid);
            feedback.textContent = message;
        }

        code.addEventListener('input', () => {
            window.clearTimeout(timer);
            check?.abort();
            const value = code.value.trim();
            showCodeFeedback(value ? '품번을 확인 중입니다.' : '');
            if (!value) return;

            timer = window.setTimeout(async () => {
                check = new AbortController();
                const { signal } = check;
                const url = new URL(form.dataset.codeCheck, location.href);
                url.search = new URLSearchParams({ code: value, id });
                try {
                    const response = await fetch(url, { signal });
                    const result = response.ok && await response.text();
                    if (signal.aborted) return;
                    if (result === 'duplicate') {
                        showCodeFeedback('동일한 품번의 상품이 이미 있습니다.', true);
                    } else if (result === 'available') {
                        showCodeFeedback('사용할 수 있는 품번입니다.');
                    } else {
                        showCodeFeedback('지금은 확인할 수 없습니다. 저장할 때 다시 확인합니다.');
                    }
                } catch (error) {
                    if (!signal.aborted) showCodeFeedback('지금은 확인할 수 없습니다. 저장할 때 다시 확인합니다.');
                }
            }, 300);
        });

        const price = form.elements.price;
        const priceFeedback = document.querySelector('#product-price-feedback');
        function updatePriceFeedback() {
            const invalid = price.validity.rangeUnderflow || price.validity.rangeOverflow
                || price.validity.stepMismatch || price.validity.badInput;
            price.setAttribute('aria-invalid', String(invalid));
            priceFeedback.classList.toggle('is-error', invalid);
        }
        price.addEventListener('input', updatePriceFeedback);
        price.addEventListener('invalid', updatePriceFeedback);
        updatePriceFeedback();
    }

    function bindBulk(form) {
        const root = form.closest('[data-admin-query]');
        const checkboxes = [...form.elements].filter(input => input.name === 'selectedProduct');
        const selectAll = root.querySelector('[data-bulk-select-all]');
        const clear = form.querySelector('[data-batch-clear]');
        const status = form.querySelector('[data-product-bulk-error]');
        const opener = form.querySelector('button[type="submit"]');
        const dialog = root.querySelector('#product-bulk-dialog');
        const type = form.elements.bulkType;
        const selection = window.DaisoAdminBatchSelection.bind({
            form, panel: form, boxes: checkboxes, selectAll, onChange: edited
        });
        const saved = selection.draft;
        for (const [name, value] of Object.entries(saved)) form.elements.namedItem(name).value = value;
        const targets = { STATUS: 'targetStatus', PRICE: 'targetPrice', CATEGORY: 'targetCategory' };
        let invalidField;
        form.noValidate = true;
        clear.hidden = false;
        root.querySelector('[data-bulk-select-heading]').hidden = checkboxes.length > 0;

        function updateType() {
            for (const field of form.querySelectorAll('[data-bulk-field]')) {
                field.hidden = field.dataset.bulkField !== type.value;
                field.querySelector('select, input').disabled = field.hidden;
            }
        }

        function clearError() {
            invalidField?.removeAttribute('aria-invalid');
            invalidField?.removeAttribute('aria-errormessage');
            form.querySelector('.batch-field-error')?.remove();
            status.hidden = true;
            invalidField = undefined;
        }

        function showError(name, message) {
            clearError();
            const field = name === 'selectedProduct' ? selectAll : form.elements.namedItem(name);
            if (field?.closest('.field')) {
                const error = document.createElement('span');
                error.id = 'product-bulk-field-error';
                error.className = 'batch-field-error';
                error.setAttribute('role', 'alert');
                error.textContent = message;
                field.closest('.field').append(error);
                field.setAttribute('aria-invalid', 'true');
                field.setAttribute('aria-errormessage', error.id);
                invalidField = field;
            } else {
                status.hidden = false;
                status.textContent = message;
            }
            (field || status).focus();
        }

        function edited() {
            form.dispatchEvent(new Event('admin-batch-input-changed', { bubbles: true }));
            clearError();
        }

        function resetSelection() {
            for (const name of Object.keys(saved)) delete saved[name];
            form.reset();
            selection.clear();
            updateType();
        }
        clear.addEventListener('click', () => {
            resetSelection();
            (selectAll.hidden ? root.querySelector('[data-admin-query-form] input[type="search"]') : selectAll)?.focus();
        });
        form.addEventListener('admin-batch-reset', resetSelection);
        form.addEventListener('input', event => {
            if (event.target.name === 'targetPrice') saved.targetPrice = event.target.value;
            if (event.target.form === form) edited();
        });
        form.addEventListener('change', event => {
            if (event.target.tagName !== 'SELECT') return;
            saved[event.target.name] = event.target.value;
            edited();
            updateType();
        });

        function validate() {
            if (!selection.size()) return ['selectedProduct', '변경할 상품을 선택해 주세요.'];
            const name = targets[type.value];
            if (!name) return ['bulkType', '일괄 변경 항목을 선택해 주세요.'];
            const field = form.elements.namedItem(name);
            if (name === 'targetPrice') {
                if (!field.value && !field.validity.badInput) return [name, '변경할 가격을 입력해 주세요.'];
                if (!Number.isInteger(field.valueAsNumber) || !field.validity.valid) {
                    return [name, '1,000원부터 5,000원까지 정수로 입력해 주세요.'];
                }
            } else if (!field.value || field.selectedOptions[0]?.disabled) {
                return [name, name === 'targetStatus' ? '변경할 판매 상태를 선택해 주세요.' : '변경할 카테고리를 선택해 주세요.'];
            }
            return null;
        }

        form.addEventListener('submit', event => {
            clearError();
            const issue = validate();
            if (issue) {
                event.preventDefault();
                showError(...issue);
                return;
            }
            if (typeof dialog.showModal !== 'function') {
                form.dataset.nativeSubmit = 'true';
                return;
            }
            event.preventDefault();
            form.dispatchEvent(new CustomEvent('admin-batch-request-preview', {
                bubbles: true,
                detail: { dialog, opener, showError, noticeId: 'product-bulk-result', refreshLabel: '최신 상품 목록 보기' }
            }));
        });
        updateType();
    }

    function bindList() {
        const stockFilter = document.querySelector('[name="filterStock"]');
        if (stockFilter) {
            const quantity = stockFilter.form.elements.stockQuantity;
            const quantityField = quantity.closest('[data-stock-quantity]');
            const updateStockFilter = () => {
                const needsQuantity = stockFilter.value === 'AT_LEAST' || stockFilter.value === 'AT_MOST';
                quantityField.hidden = !needsQuantity;
                quantity.disabled = !needsQuantity;
                quantity.required = needsQuantity;
            };
            stockFilter.addEventListener('change', updateStockFilter);
            updateStockFilter();
        }

        const bulk = document.querySelector('#product-bulk');
        if (bulk) bindBulk(bulk);

    }
    bindList();
    document.addEventListener('admin-query-render', bindList);
    document.querySelector('[data-admin-page="products"]').addEventListener('click', (event) => {
        const dialog = document.querySelector('#product-delete-dialog');
        if (typeof dialog?.showModal !== 'function') return;
        const name = dialog.querySelector('[data-delete-product-name]');
        const id = dialog.querySelector('input[name="id"]');
        if (event.target.closest('[data-delete-cancel]') && dialog.open) {
            dialog.close();
            return;
        }
        const link = event.target.closest('[data-delete-id]');
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        id.value = link.dataset.deleteId;
        name.textContent = link.dataset.deleteName;
        dialog.showModal();
    });
})();
