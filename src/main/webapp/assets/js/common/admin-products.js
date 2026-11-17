(() => {
    const form = document.querySelector('[data-code-check]');
    if (form) {
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
