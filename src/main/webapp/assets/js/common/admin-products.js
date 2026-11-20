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
        const focus = location.hash === '#product-status' ? '#product-status' : '#product-editor-title';
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
        setupDetailPreview(form, preview);
    }

    function setupDetailPreview(form, imagePreview) {
        const detail = form.querySelector('[data-admin-product-preview]');
        if (!detail) return;
        const image = detail.querySelector('[data-preview-image]');
        const fallback = image.dataset.fallbackImage;
        const priceFormat = new Intl.NumberFormat('ko-KR');
        const displayedValues = new Map(Array.from(detail.querySelectorAll('[data-preview-value]'),
            element => [element.dataset.previewValue, element]));
        const set = (name, value) => { displayedValues.get(name).textContent = value; };

        function update() {
            if (!detail.open) return;
            const fields = form.elements;
            const quantity = fields.stock.valueAsNumber;
            const status = fields.status.value === 'SCHEDULED' ? '판매 예정'
                : fields.status.value === 'AVAILABLE' && quantity > 0 ? '구매 가능' : '품절';
            set('brand', fields.brand.value.trim());
            set('name', fields.name.value.trim() || '상품명');
            set('price', Number.isFinite(fields.price.valueAsNumber) ? priceFormat.format(fields.price.valueAsNumber) + '원' : '—');
            set('code', '품번 ' + (fields.code.value.trim() || '—'));
            set('stock', status + ' · 온라인 재고 ' + (Number.isFinite(quantity) ? quantity : '—') + '개');
            set('description', fields.description.value.trim() || '등록한 상품 설명이 없습니다.');
            const source = imagePreview.hidden ? fallback : imagePreview.src;
            if (image.getAttribute('src') !== source) image.src = source;
        }

        image.addEventListener('error', () => {
            if (image.getAttribute('src') !== fallback) image.src = fallback;
        });
        detail.addEventListener('toggle', update);
        form.addEventListener('input', update);
        form.addEventListener('change', update);
        imagePreview.addEventListener('load', update);
        imagePreview.addEventListener('error', update);
    }

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
    if (bulk) {
        // 상품 체크박스는 표 안에 있으며 form 속성으로 일괄 변경 폼에 연결된다.
        const checkboxes = Array.from(bulk.elements).filter(input => input.name === 'selectedProduct');
        const selectAll = document.querySelector('[data-bulk-select-all]');
        const heading = document.querySelector('[data-bulk-select-heading]');
        const count = bulk.querySelector('[data-bulk-count]');
        const submit = bulk.querySelector('button[type="submit"]');
        const confirmDialog = document.querySelector('#product-bulk-confirm');
        const selectedCount = () => checkboxes.filter(input => input.checked).length;
        const updateSelection = () => {
            const total = selectedCount();
            count.textContent = total + '개 선택';
            selectAll.checked = total === checkboxes.length;
            selectAll.indeterminate = total > 0 && total < checkboxes.length;
            submit.disabled = total === 0;
        };
        selectAll.hidden = false;
        heading.hidden = true;
        selectAll.addEventListener('change', () => {
            checkboxes.forEach(input => { input.checked = selectAll.checked; });
            updateSelection();
        });
        checkboxes.forEach(input => input.addEventListener('change', updateSelection));
        updateSelection();

        let confirmed = false;
        bulk.addEventListener('submit', (event) => {
            if (confirmed) {
                confirmed = false;
                return;
            }
            const status = bulk.elements.targetStatus.selectedOptions[0].textContent;
            const message = selectedCount() + '개 상품의 판매 상태를 ‘' + status + '’(으)로 변경할까요?';
            if (typeof confirmDialog.showModal !== 'function') {
                if (!window.confirm(message)) event.preventDefault();
                return;
            }
            event.preventDefault();
            confirmDialog.querySelector('[data-bulk-message]').textContent = message;
            confirmDialog.returnValue = '';
            confirmDialog.showModal();
        });
        confirmDialog.addEventListener('close', () => {
            if (confirmDialog.returnValue === 'apply') {
                confirmed = true;
                bulk.requestSubmit(submit);
            }
        });
    }

    const dialog = document.querySelector('#product-delete-dialog');
    if (typeof dialog?.showModal !== 'function') return;

    const name = dialog.querySelector('[data-delete-product-name]');
    const id = dialog.querySelector('input[name="id"]');
    dialog.querySelector('[data-delete-cancel]').addEventListener('click', () => dialog.close());
    document.querySelector('[data-admin-page="products"]').addEventListener('click', (event) => {
        const link = event.target.closest('[data-delete-id]');
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        id.value = link.dataset.deleteId;
        name.textContent = link.dataset.deleteName;
        dialog.showModal();
    });
})();
