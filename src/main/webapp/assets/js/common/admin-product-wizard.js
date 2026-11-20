(() => {
    const form = document.querySelector('[data-code-check]');
    if (!form) return;

    const dialog = form.closest('dialog');
    const fields = form.elements;
    const inputStep = form.querySelector('[data-product-input-step]');
    const preview = form.querySelector('[data-product-preview-step]');
    const previewButton = form.querySelector('[data-product-preview]');
    const cover = form.querySelector('[data-product-image-preview]');
    const coverInPreview = preview.querySelector('[data-preview-image]');
    const list = form.querySelector('[data-gallery-list]');
    const message = form.querySelector('[data-gallery-message]');
    const official = form.querySelector('[data-gallery-official]');
    const guide = preview.querySelector('[data-preview-gallery]');
    const format = new Intl.NumberFormat('ko-KR');
    const values = new Map(Array.from(preview.querySelectorAll('[data-preview-value]'),
        element => [element.dataset.previewValue, element]));
    const files = fields.detailImages;
    const maxBytes = 5 * 1024 * 1024;
    const selection = Array.from(form.querySelector('[data-gallery-saved]').content.querySelectorAll('img'),
        image => ({ key: 's' + image.dataset.imageId, source: image.src }));
    let inputScroll = 0;

    function syncFiles() {
        if (typeof DataTransfer !== 'function') return;
        const transfer = new DataTransfer();
        for (const image of selection) if (image.file) transfer.items.add(image.file);
        files.files = transfer.files;
    }

    function imageElement(src, alt) {
        const image = document.createElement('img');
        image.src = src;
        image.alt = alt;
        image.decoding = 'async';
        return image;
    }

    function action(text, name, position, disabled, run) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'button secondary';
        button.textContent = text;
        button.setAttribute('aria-label', position + '번 상세 이미지 ' + name);
        button.disabled = disabled;
        button.addEventListener('click', run);
        return button;
    }

    function drawGallery(focusIndex) {
        list.replaceChildren();
        selection.forEach((image, index) => {
            const row = document.createElement('li');
            const controls = document.createElement('div');
            controls.className = 'admin-gallery-actions';
            const move = (offset) => {
                [selection[index], selection[index + offset]] = [selection[index + offset], selection[index]];
                syncFiles();
                drawGallery(index + offset);
            };
            controls.append(
                action('↑', '앞으로 이동', index + 1, index === 0, () => move(-1)),
                action('↓', '뒤로 이동', index + 1, index === selection.length - 1, () => move(1)),
                action('제거', '제거', index + 1, false, () => {
                    if (image.file) URL.revokeObjectURL(image.source);
                    selection.splice(index, 1);
                    syncFiles();
                    drawGallery(Math.min(index, selection.length - 1));
                })
            );
            row.append(imageElement(image.source, (index + 1) + '번 상세 이미지'), controls);
            list.append(row);
        });
        if (focusIndex !== undefined) {
            (list.children[focusIndex]?.querySelector('button:not(:disabled)') || files).focus();
        }
    }

    files.addEventListener('change', () => {
        message.textContent = '';
        const batch = Array.from(files.files);
        if (typeof DataTransfer !== 'function') {
            files.value = '';
            message.textContent = '상세 이미지 첨부와 순서 변경을 지원하는 브라우저에서 다시 시도해 주세요.';
            return;
        }
        if (selection.length + batch.length > 10 || batch.some(file =>
            file.size === 0 || !['image/jpeg', 'image/png'].includes(file.type))) {
            message.textContent = '상세 이미지는 JPG·PNG 형식으로 최대 10장까지 추가할 수 있습니다.';
        } else {
            for (const file of batch) selection.push({ file, source: URL.createObjectURL(file) });
        }
        syncFiles();
        drawGallery();
    });

    const set = (key, text) => { values.get(key).textContent = text; };
    function renderPreview() {
        const quantity = fields.stock.valueAsNumber;
        const status = fields.status.value === 'DISCONTINUED' ? '판매 종료'
            : fields.status.value === 'SCHEDULED' ? '판매 예정'
            : fields.status.value === 'AVAILABLE' && quantity > 0 ? '구매 가능' : '품절';
        const money = format.format(fields.price.valueAsNumber) + '원';
        set('brand', fields.brand.value.trim());
        set('name', fields.name.value.trim());
        set('price', money);
        set('code', '품번 ' + fields.code.value.trim());
        set('stock', status + ' · 온라인 재고 ' + quantity + '개');
        set('total', '합계 ' + money);
        set('description', fields.description.value.trim());
        values.get('description').hidden = !fields.description.value.trim();
        coverInPreview.src = cover.hidden ? coverInPreview.dataset.fallbackImage : cover.src;

        const officialMatches = official.dataset.code === fields.code.value.trim();
        const officialImages = officialMatches ? Array.from(official.content.querySelectorAll('img')) : [];
        const sources = selection.length > 0 ? selection.map(image => image.source)
            : officialImages.map(image => image.src);
        const facts = preview.querySelector('[data-preview-facts]');
        facts.replaceChildren();
        if (officialMatches) {
            official.content.querySelectorAll('dl, p').forEach(node => facts.append(node.cloneNode(true)));
        }
        const first = preview.querySelector('[data-preview-first]');
        first.parentElement.hidden = sources.length === 0;
        if (sources.length > 0) first.src = sources[0]; else first.removeAttribute('src');
        preview.querySelector('[data-preview-photos]').replaceChildren(...sources.map(
            (source, index) => imageElement(source, (index + 1) + '번 상세 이미지')));
        guide.open = false;
        guide.hidden = sources.length === 0 && !facts.hasChildNodes();
        preview.querySelector('[data-preview-empty]').hidden = !guide.hidden;
    }

    function back() {
        preview.hidden = true;
        inputStep.hidden = false;
        dialog.classList.remove('is-preview');
        dialog.setAttribute('aria-labelledby', 'product-editor-title');
        fields.previewConfirmed.value = '0';
        previewButton.focus({ preventScroll: true });
        dialog.scrollTop = inputScroll;
    }

    function filesWithinLimit() {
        const coverSize = fields.imageFile.files[0]?.size || 0;
        return selection.reduce((sum, image) => sum + (image.file?.size || 0), coverSize) <= maxBytes;
    }

    function showPreview() {
        if (!form.reportValidity()) return;
        if (!filesWithinLimit()) {
            message.textContent = '새로 첨부하는 대표·상세 이미지의 합계를 5MB 이하로 줄여 주세요.';
            files.focus();
            return;
        }
        message.textContent = '';
        inputScroll = dialog.scrollTop;
        renderPreview();
        inputStep.hidden = true;
        preview.hidden = false;
        dialog.classList.add('is-preview');
        dialog.setAttribute('aria-labelledby', 'product-preview-title');
        preview.querySelector('h2').focus({ preventScroll: true });
        dialog.scrollTop = 0;
    }

    coverInPreview.addEventListener('error', () => {
        if (coverInPreview.getAttribute('src') !== coverInPreview.dataset.fallbackImage) {
            coverInPreview.src = coverInPreview.dataset.fallbackImage;
        }
    });
    form.noValidate = true;
    previewButton.addEventListener('click', showPreview);
    preview.querySelector('[data-product-back]').addEventListener('click', back);
    dialog.addEventListener('cancel', event => {
        if (!preview.hidden) {
            event.preventDefault();
            event.stopImmediatePropagation();
            back();
        }
    }, { capture: true });
    form.addEventListener('submit', event => {
        if (preview.hidden) {
            event.preventDefault();
            showPreview();
            return;
        }
        if (!form.checkValidity() || !filesWithinLimit()) {
            event.preventDefault();
            back();
            showPreview();
            return;
        }
        let next = 0;
        fields.detailImageOrder.value = selection.map(image => image.file ? 'n' + next++ : image.key).join(',');
        fields.previewConfirmed.value = '1';
    });

    window.addEventListener('pagehide', () => {
        for (const image of selection) if (image.file) URL.revokeObjectURL(image.source);
    }, { once: true });
    drawGallery();
})();
