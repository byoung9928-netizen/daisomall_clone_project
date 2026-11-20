(() => {
    const config = document.querySelector('[data-cart-url][data-wishlist-config]');
    if (!config) return;
    const feedback = config.querySelector('[data-wishlist-feedback]');

    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-stock-watch-add]');
        if (!button || button.disabled) return;
        if (config.dataset.loggedIn !== 'true') {
            if (window.confirm('로그인하고 장바구니에 담을까요?')) location.assign(config.dataset.loginUrl);
            return;
        }

        button.disabled = true;
        try {
            const response = await fetch(config.dataset.cartUrl, {
                method: 'POST',
                credentials: 'same-origin',
                body: new URLSearchParams({ productId: button.dataset.stockWatchAdd, quantity: '1' })
            });
            const message = (await response.text()).trim();
            if (!response.ok) throw new Error('장바구니에 담지 못했습니다.');
            feedback.textContent = message === 'OK' ? '장바구니에 1개 담았습니다.' : message;
            if (message === 'OK') button.textContent = '담았어요';
        } catch {
            feedback.textContent = '장바구니에 담지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            button.disabled = false;
        }
    });
})();
