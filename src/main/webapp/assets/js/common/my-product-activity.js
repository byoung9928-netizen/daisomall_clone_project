(() => {
    function bindHistorySelection(panel) {
        const config = panel.querySelector('[data-history-remove]');
        const all = panel.querySelector('[data-history-select-all]');
        const remove = panel.querySelector('[data-history-remove-selected]');
        if (!config || !all || !remove) return;
        const boxes = Array.from(config.querySelectorAll('[data-history-select]:not(:disabled)'));
        const label = config.querySelector('[data-history-count]');
        const feedback = config.querySelector('[data-wishlist-feedback]');
        let pending = false;
        const chosen = () => boxes.filter(box => box.checked);
        const update = () => {
            const count = chosen().length;
            all.checked = boxes.length > 0 && count === boxes.length;
            all.indeterminate = count > 0 && count < boxes.length;
            all.disabled = pending || boxes.length === 0;
            remove.disabled = pending || count === 0;
            label.textContent = count + '개 선택';
        };
        all.addEventListener('change', () => {
            for (const box of boxes) box.checked = all.checked;
            update();
        });
        for (const box of boxes) box.addEventListener('change', update);
        remove.addEventListener('click', async () => {
            const items = chosen();
            if (pending || !items.length || !window.confirm('선택한 상품 ' + items.length + '개를 목록에서 삭제할까요?')) return;
            pending = true;
            update();
            try {
                const body = new URLSearchParams({ kind: config.dataset.historyKind, csrf: config.dataset.csrf });
                for (const item of items) body.append('productId', item.value);
                const response = await fetch(config.dataset.historyRemove, { method: 'POST', credentials: 'same-origin', body });
                if (!response.ok || (await response.text()).trim() !== 'REMOVED') throw new Error(String(response.status));
                const next = new URL(location.href);
                next.searchParams.delete('activityPage');
                location.assign(next);
            } catch (error) {
                feedback.textContent = error.message === '401' ? '로그인한 뒤 다시 시도해 주세요.' : '선택한 상품을 삭제하지 못했습니다.';
                pending = false;
                update();
            }
        });
        update();
    }

    document.querySelectorAll('[data-product-activity]').forEach(async panel => {
        try {
            const response = await fetch(panel.dataset.url, { credentials: 'same-origin' });
            if (!response.ok) throw new Error(String(response.status));
            const markup = new DOMParser().parseFromString(await response.text(), 'text/html');
            const result = markup.querySelector('[data-product-activity-result]');
            if (!result) throw new Error('Invalid activity response');
            panel.replaceChildren(...result.childNodes);
            bindHistorySelection(panel);
        } catch (error) {
            const hint = document.createElement('p');
            hint.className = 'product-activity-empty';
            hint.setAttribute('role', 'status');
            hint.textContent = error.message === '401' ? '로그인한 뒤 다시 확인해 주세요.'
                : '상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
            panel.replaceChildren(hint);
        } finally {
            panel.removeAttribute('aria-busy');
        }
    });
})();
