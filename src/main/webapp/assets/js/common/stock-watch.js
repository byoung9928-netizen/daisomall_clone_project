(() => {
    const config = document.querySelector('[data-cart-url][data-wishlist-config]');
    if (!config) return;
    const feedback = config.querySelector('[data-wishlist-feedback]');
    const pending = new Set();

    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-stock-watch-add], [data-catalog-add], [data-catalog-preview]');
        if (!button || button.disabled) return;
        if (config.dataset.loggedIn !== 'true') {
            if (window.confirm('로그인하고 장바구니에 담을까요?')) location.assign(config.dataset.loginUrl);
            return;
        }

        const preview = button.dataset.catalogPreview;
        const id = preview || button.dataset.catalogAdd || button.dataset.stockWatchAdd;
        if (pending.has(id)) return;
        pending.add(id);
        button.disabled = true;
        try {
            const body = preview
                ? new URLSearchParams({ productId: preview, csrf: config.dataset.csrf })
                : new URLSearchParams({ productId: id, quantity: '1' });
            const response = await fetch(preview ? config.dataset.cartPreviewUrl : config.dataset.cartUrl, {
                method: 'POST',
                credentials: 'same-origin',
                body
            });
            const message = (await response.text()).trim();
            if (response.redirected || (!response.ok && !preview)) throw new Error('장바구니에 담지 못했습니다.');
            if (!response.ok || message !== 'OK') {
                feedback.textContent = message || '장바구니에 담지 못했습니다.';
            } else {
                feedback.textContent = '';
                window.Daiso.ui.showToast({
                    message: preview ? '품절 상품을 미리 담았습니다.' : '택배배송 장바구니에 담았습니다.',
                    actionText: '바로가기',
                    actionUrl: config.dataset.cartViewUrl,
                    origin: button
                });
                button.textContent = preview ? '미리 담았어요' : '담았어요';
            }
        } catch {
            feedback.textContent = '장바구니에 담지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            pending.delete(id);
            button.disabled = false;
        }
    });
})();
