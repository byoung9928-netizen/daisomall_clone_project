(() => {
    'use strict';

    const reviewList = document.querySelector('[data-my-review-list]');
    let listRequest;

    async function loadList(pageUrl = new URL(location.href), push = false) {
        const listUrl = new URL(reviewList.dataset.reviewListUrl, location.href);
        const error = document.querySelector('[data-review-list-error]');
        const scroll = window.scrollY;
        for (const [key, parameter] of Object.entries({
            tab: 'reviewTab', type: 'reviewType', sort: 'reviewSort', page: 'reviewPage'
        })) {
            const value = pageUrl.searchParams.get(parameter);
            if (value) listUrl.searchParams.set(key, value);
        }
        listRequest?.abort();
        const request = new AbortController();
        listRequest = request;
        reviewList.setAttribute('aria-busy', 'true');
        error.hidden = true;
        try {
            const response = await fetch(listUrl, { signal: request.signal, redirect: 'error', cache: 'no-store' });
            if (!response.ok) throw new Error(response.status === 401 ? 'LOGIN' : 'LIST');
            const html = new DOMParser().parseFromString(await response.text(), 'text/html');
            const result = html.querySelector('[data-review-list-result]');
            if (!result || !/^[1-9]\d*$/.test(result.dataset.page)) throw new Error('LIST');
            if (listRequest !== request) return false;
            if (result.dataset.page === '1') pageUrl.searchParams.delete('reviewPage');
            else pageUrl.searchParams.set('reviewPage', result.dataset.page);
            if (pageUrl.href !== location.href) {
                if (push) history.pushState(null, '', pageUrl);
                else history.replaceState(history.state, '', pageUrl);
            }
            reviewList.replaceChildren(result);
            if (window.scrollY !== scroll) window.scrollTo({ top: scroll, behavior: 'instant' });
            return true;
        } catch (failure) {
            if (listRequest !== request) return false;
            error.textContent = failure.message === 'LOGIN' ? '로그인한 뒤 리뷰 목록을 확인해 주세요.'
                : '리뷰 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
            error.hidden = false;
            if (!reviewList.querySelector('[data-review-list-result]')) reviewList.replaceChildren();
            return false;
        } finally {
            if (listRequest === request) reviewList.removeAttribute('aria-busy');
        }
    }

    function ordinaryClick(event) {
        return !event.defaultPrevented && event.button === 0 && !event.ctrlKey
            && !event.metaKey && !event.shiftKey && !event.altKey;
    }

    if (reviewList) {
        reviewList.addEventListener('click', async event => {
            const link = event.target.closest('[data-review-list-link]');
            if (!link || !ordinaryClick(event)) return;
            event.preventDefault();
            const label = link.textContent.trim().replace(/\s+/g, ' ');
            if (await loadList(new URL(link.href), true)) {
                const target = [...reviewList.querySelectorAll('[data-review-list-link]')]
                    .find(item => item.textContent.trim().replace(/\s+/g, ' ') === label);
                (target || reviewList.querySelector('.my-review-tabs [aria-current]'))?.focus({ preventScroll: true });
            }
        });
        window.addEventListener('popstate', () => loadList());
        loadList();
    }

    if (typeof HTMLDialogElement === 'undefined' || typeof HTMLDialogElement.prototype.showModal !== 'function') return;

    const messages = {
        create: ['등록', '리뷰가 정상적으로 등록되었습니다.'],
        update: ['수정', '리뷰가 정상적으로 수정되었습니다.'],
        delete: ['삭제', '리뷰가 정상적으로 삭제되었습니다.']
    };
    const orderLinks = [...document.querySelectorAll('[data-order-review]')];
    const standalone = document.querySelector('[data-review-dialog]');
    let opening = false;
    if (!standalone && !reviewList && orderLinks.length === 0) return;

    const notice = document.createElement('dialog');
    notice.className = 'review-dialog review-dialog--prompt';
    notice.setAttribute('aria-labelledby', 'review-prompt-title');
    notice.setAttribute('aria-describedby', 'review-prompt-description');
    notice.innerHTML = '<form method="dialog" class="review-dialog__form">'
        + '<header class="review-dialog__header"><h2 id="review-prompt-title"></h2></header>'
        + '<div class="review-dialog__body"><p id="review-prompt-description"></p></div>'
        + '<footer class="review-dialog__footer"><button class="button secondary" value="cancel"></button>'
        + '<button class="button review-dialog__danger" value="delete">삭제</button></footer></form>';
    document.body.append(notice);

    function prompt(title, description, confirmDelete = false) {
        notice.querySelector('h2').textContent = title;
        notice.querySelector('p').textContent = description;
        const cancel = notice.querySelector('[value="cancel"]');
        const confirm = notice.querySelector('[value="delete"]');
        cancel.textContent = confirmDelete ? '취소' : '확인';
        confirm.hidden = !confirmDelete;
        notice.returnValue = '';
        notice.showModal();
        cancel.focus({ preventScroll: true });
        return new Promise(resolve => notice.addEventListener('close',
            () => resolve(notice.returnValue === 'delete'), { once: true }));
    }

    function editorUrl(link) {
        return new URL(link.dataset.reviewUrl || link.href, location.href);
    }

    function errorText(response, body) {
        if (response.headers.get('Content-Type')?.startsWith('text/plain') && body.trim()) return body.trim();
        if (response.status === 401) return '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.';
        if (response.status === 413) return '사진 첨부 용량을 확인해 주세요.';
        return '';
    }

    async function refreshOrders(productId) {
        let complete = true;
        await Promise.all(orderLinks.filter(link => link.dataset.orderReview === productId).map(async link => {
            try {
                const url = editorUrl(link);
                url.pathname += '/order-state';
                const response = await fetch(url, { redirect: 'error', cache: 'no-store' });
                const state = (await response.text()).trim();
                if (!response.ok || !['EDIT', 'WRITE', 'NONE'].includes(state)) throw new Error();
                if (state === 'NONE') {
                    link.removeAttribute('href');
                    link.setAttribute('aria-disabled', 'true');
                    link.tabIndex = -1;
                } else {
                    link.href = link.dataset.reviewUrl;
                    link.removeAttribute('aria-disabled');
                    link.removeAttribute('tabindex');
                }
                link.textContent = { EDIT: '리뷰 수정/삭제', WRITE: '리뷰 작성', NONE: '리뷰 작성 불가' }[state];
            } catch {
                complete = false;
            }
        }));
        return complete;
    }

    function bind(dialog, opener) {
        const orderLink = opener.matches('[data-order-review]') ? opener : null;
        const fromList = opener.matches('[data-my-review-open]');
        const dynamic = orderLink || fromList;
        const productId = opener.closest('[data-review-item]')?.dataset.reviewItem;
        const close = dialog.querySelector('[data-review-close]');
        const remove = dialog.querySelector('[data-review-delete]');
        const form = dialog.querySelector('form');
        const save = form.querySelector('[type="submit"]');
        let busy = false;
        let completing = false;
        close.hidden = false;
        if (remove) remove.hidden = false;
        close.addEventListener('click', () => dialog.close());
        dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
        dialog.addEventListener('close', () => {
            if (dynamic) dialog.remove();
            if (!completing) opener.focus({ preventScroll: true });
        });

        async function submit(action) {
            if (busy) return;
            const [label, success] = messages[action];
            const url = new URL(form.action);
            let body = new FormData(form);
            if (action === 'delete') {
                url.pathname = url.pathname.replace(/\/update$/, '/delete');
                body = new URLSearchParams([...body].filter(([key]) =>
                    ['id', 'csrf', 'from', 'orderNo', 'reviewId', 'version'].includes(key)));
            } else if (form.enctype !== 'multipart/form-data') {
                body = new URLSearchParams(body);
            }
            const buttons = [save, close, remove].filter(Boolean);
            busy = true;
            buttons.forEach(button => { button.disabled = true; });
            form.setAttribute('aria-busy', 'true');
            let saved = false;
            let detail = '';
            let returnUrl;
            let refreshed = true;
            try {
                const response = await fetch(url, { method: 'POST', body, redirect: 'error',
                    headers: { 'X-Review-Request': 'editor' } });
                const result = await response.text();
                saved = response.ok && result.trim() === 'SAVED';
                if (saved) {
                    returnUrl = response.headers.get('X-Review-Return');
                    if (orderLink) refreshed = await refreshOrders(orderLink.dataset.orderReview);
                    if (fromList) refreshed = await loadList();
                } else {
                    detail = errorText(response, result);
                }
            } catch {
                // A missing response does not confirm that the operation succeeded.
            } finally {
                busy = false;
                buttons.forEach(button => { button.disabled = false; });
                form.removeAttribute('aria-busy');
            }

            if (!saved) {
                await prompt('리뷰 ' + label + ' 실패', detail || '리뷰 ' + label + '에 실패했습니다. 다시 시도해 주세요.');
                (action === 'delete' ? remove : save).focus({ preventScroll: true });
                return;
            }

            completing = true;
            dialog.close();
            await prompt('리뷰 ' + label + ' 완료', refreshed ? success
                : success + (fromList ? ' 최신 목록을 확인하려면 리뷰 화면을 다시 불러와 주세요.'
                    : ' 버튼 상태를 확인하려면 주문내역을 다시 불러와 주세요.'));
            if (dynamic) {
                const row = fromList && [...reviewList.querySelectorAll('[data-review-item]')]
                    .find(item => item.dataset.reviewItem === productId);
                const focus = opener.isConnected ? opener : row?.querySelector('.my-review-edit')
                    || reviewList.querySelector('.my-review-tabs [aria-current]') || document.getElementById('my-review-title');
                focus?.focus({ preventScroll: true });
            }
            else if (returnUrl && new URL(returnUrl, location.href).origin === location.origin) location.assign(returnUrl);
            else location.reload();
        }

        form.addEventListener('submit', event => {
            event.preventDefault();
            submit(remove ? 'update' : 'create');
        });
        remove?.addEventListener('click', async () => {
            if (busy) return;
            if (await prompt('리뷰를 삭제하시겠습니까?', '삭제한 리뷰는 다시 복구할 수 없습니다.', true)) {
                submit('delete');
            } else {
                remove.focus({ preventScroll: true });
            }
        });

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
        if (dynamic) dialog.addEventListener('close', () => {
            if (!dialog.isConnected) previewUrls.forEach(url => URL.revokeObjectURL(url));
        });
    }

    if (standalone) {
        const opener = document.querySelector('[data-review-open]');
        standalone.removeAttribute('open');
        opener.hidden = false;
        bind(standalone, opener);
        opener.addEventListener('click', () => standalone.showModal());
        standalone.showModal();
    }

    for (const link of orderLinks) link.dataset.reviewUrl = link.href;
    document.addEventListener('click', async event => {
        const link = event.target.closest('[data-order-review], [data-my-review-open]');
        if (!link || !ordinaryClick(event)) return;
        event.preventDefault();
        if (link.getAttribute('aria-disabled') === 'true' || opening || notice.open
                || document.querySelector('[data-review-dialog]:modal')) return;
        opening = true;
        link.setAttribute('aria-busy', 'true');
        const url = editorUrl(link);
        url.searchParams.set('fragment', 'editor');
        try {
            const response = await fetch(url, { redirect: 'error', cache: 'no-store' });
            const body = await response.text();
            if (!response.ok) {
                await prompt('리뷰 화면을 열 수 없습니다', errorText(response, body) || '잠시 후 다시 시도해 주세요.');
                return;
            }
            const html = new DOMParser().parseFromString(body, 'text/html');
            const dialog = html.querySelector('[data-review-dialog]');
            if (!dialog) throw new Error();
            document.body.append(dialog);
            bind(dialog, link);
            const stars = Number(link.dataset.reviewStarsValue);
            if (stars >= 1 && stars <= 5 && !dialog.querySelector('[name="reviewId"]')) {
                const rating = dialog.querySelector('[name="rating"][value="' + stars + '"]');
                if (rating) {
                    rating.checked = true;
                    rating.dispatchEvent(new Event('change', { bubbles: true }));
                }
            }
            dialog.showModal();
        } catch {
            await prompt('리뷰 화면을 열 수 없습니다', '잠시 후 다시 시도해 주세요.');
        } finally {
            opening = false;
            link.removeAttribute('aria-busy');
        }
    });
})();
