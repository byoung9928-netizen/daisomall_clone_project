(() => {
    const config = document.querySelector('[data-cart-url][data-wishlist-config]');
    if (!config) return;
    const feedback = config.querySelector('[data-wishlist-feedback]');

    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-stock-watch-add], [data-catalog-add], [data-catalog-preview]');
        if (!button || button.disabled) return;
        if (config.dataset.loggedIn !== 'true') {
            if (window.confirm('로그인하고 장바구니에 담을까요?')) location.assign(config.dataset.loginUrl);
            return;
        }

        button.disabled = true;
        try {
            const preview = button.dataset.catalogPreview;
            const body = preview
                ? new URLSearchParams({ productId: preview, csrf: config.dataset.csrf })
                : new URLSearchParams({ productId: button.dataset.catalogAdd || button.dataset.stockWatchAdd, quantity: '1' });
            const response = await fetch(preview ? config.dataset.cartPreviewUrl : config.dataset.cartUrl, {
                method: 'POST',
                credentials: 'same-origin',
                body
            });
            const message = (await response.text()).trim();
            if (!response.ok && !preview) throw new Error('장바구니에 담지 못했습니다.');
            if (!response.ok || message !== 'OK') {
                feedback.textContent = message || '장바구니에 담지 못했습니다.';
            } else if (preview) {
                const cartLink = document.createElement('a');
                cartLink.href = config.dataset.cartViewUrl;
                cartLink.textContent = '장바구니 보기';
                feedback.replaceChildren('품절 상품을 미리 담았습니다. ', cartLink);
                button.textContent = '미리 담았어요';
            } else {
                feedback.textContent = '장바구니에 1개 담았습니다.';
                button.textContent = '담았어요';
            }
        } catch {
            feedback.textContent = '장바구니에 담지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            button.disabled = false;
        }
    });
})();
