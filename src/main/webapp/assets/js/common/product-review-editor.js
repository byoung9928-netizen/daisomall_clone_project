(() => {
    'use strict';

    const dialog = document.querySelector('[data-review-dialog]');
    if (!dialog) return;

    const openButton = document.querySelector('[data-review-open]');
    const closeButton = dialog.querySelector('[data-review-close]');
    if (typeof dialog.showModal === 'function') {
        const open = () => {
            dialog.showModal();
            closeButton.focus();
        };
        dialog.removeAttribute('open');
        openButton.hidden = false;
        closeButton.hidden = false;
        openButton.addEventListener('click', open);
        closeButton.addEventListener('click', () => dialog.close());
        dialog.addEventListener('close', () => openButton.focus());
        open();
    }

    const stars = dialog.querySelector('[data-review-stars]');
    const rating = dialog.querySelector('[data-review-rating]');
    const updateStars = () => {
        const checked = stars.querySelector('input:checked');
        const value = checked ? Number(checked.value) : 0;
        stars.querySelectorAll('input').forEach(input => {
            input.closest('label').classList.toggle('is-filled', Number(input.value) <= value);
        });
        rating.textContent = value ? value + '점' : '별점을 선택해 주세요.';
    };
    stars.addEventListener('change', updateStars);
    updateStars();

    const content = dialog.querySelector('#review-content');
    const counter = dialog.querySelector('[data-review-count]');
    const updateCount = () => { counter.textContent = Array.from(content.value).length; };
    content.addEventListener('input', updateCount);
    updateCount();

    dialog.querySelectorAll('[data-review-clear]').forEach(button => {
        button.hidden = false;
        button.addEventListener('click', () => {
            const selected = button.closest('fieldset').querySelector('input:checked');
            if (selected) {
                selected.checked = false;
                selected.focus();
            }
        });
    });

    const photos = dialog.querySelector('#review-photos');
    if (!photos) return;

    const form = photos.form;
    const previews = dialog.querySelector('[data-review-previews]');
    const photoCount = dialog.querySelector('[data-review-photo-count]');
    const photoError = dialog.querySelector('[data-review-photo-error]');
    const existing = Array.from(form.querySelectorAll('[name="removePhoto"]'));
    const maximum = 10;
    const megabyte = 1024 * 1024;
    const replaceFiles = typeof DataTransfer === 'function';
    let selected = [];
    let previewUrls = [];

    const check = () => {
        const total = existing.filter(input => !input.checked).length + selected.length;
        photoCount.textContent = '(' + total + '/' + maximum + ')';
        let message = '';
        if (total > maximum) {
            message = '사진은 최대 10장까지 저장할 수 있어요.';
        } else if (selected.some(file => file.size === 0 || file.size > 2 * megabyte)) {
            message = '사진 한 장의 크기는 2MB 이하여야 해요.';
        } else if (selected.reduce((size, file) => size + file.size, 0) > 5 * megabyte) {
            message = '새로 첨부하는 사진의 합계는 5MB 이하여야 해요.';
        }
        photoError.textContent = message;
        photos.setCustomValidity(message);
    };

    const syncFileInput = () => {
        const transfer = new DataTransfer();
        selected.forEach(file => transfer.items.add(file));
        photos.files = transfer.files;
    };

    const render = () => {
        previewUrls.forEach(url => URL.revokeObjectURL(url));
        previewUrls = [];
        previews.replaceChildren();
        selected.forEach((file, index) => {
            const item = document.createElement('div');
            const image = document.createElement('img');
            const url = URL.createObjectURL(file);
            previewUrls.push(url);
            image.src = url;
            image.alt = '첨부 예정 사진 ' + (index + 1);
            item.append(image);
            if (replaceFiles) {
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.textContent = '제거';
                remove.setAttribute('aria-label', (index + 1) + '번 사진 제거');
                remove.addEventListener('click', () => {
                    selected.splice(index, 1);
                    syncFileInput();
                    render();
                    (previews.querySelector('button') || photos).focus();
                });
                item.append(remove);
            }
            previews.append(item);
        });
        check();
    };

    photos.addEventListener('change', () => {
        const files = Array.from(photos.files);
        if (replaceFiles) {
            for (const file of files) {
                if (!selected.some(photo => photo.name === file.name && photo.size === file.size
                        && photo.lastModified === file.lastModified)) selected.push(file);
            }
            syncFileInput();
        } else {
            selected = files;
        }
        render();
    });
    existing.forEach(input => input.addEventListener('change', check));
    check();
})();
