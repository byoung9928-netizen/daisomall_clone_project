(() => {
    const filters = document.querySelector('[data-store-filters]');
    if (filters) {
        filters.querySelectorAll('select').forEach(field => {
            field.addEventListener('change', () => filters.requestSubmit());
        });
    }

    const confirmation = document.querySelector('[data-unlist-confirm]');
    if (confirmation) confirmation.focus();

    const drawer = document.querySelector('.store-drawer');
    if (!drawer) {
        if (location.hash === '#store-inventory-table') document.getElementById('store-inventory-table')?.focus();
        return;
    }
    if (typeof drawer.showModal === 'function') {
        drawer.removeAttribute('open');
        drawer.showModal();
        drawer.addEventListener('cancel', event => {
            event.preventDefault();
            location.assign(drawer.querySelector('[data-drawer-close]').href);
        });
        (drawer.querySelector('[aria-invalid="true"]') || drawer.querySelector('h3')).focus();
    }

    const form = drawer.querySelector('[data-stock-editor]');
    const summary = form.querySelector('[data-live-stock]');
    if (!summary) return;

    const amount = form.elements.namedItem('amount');
    const change = form.elements.namedItem('change');
    const before = Number(form.dataset.before);
    const delta = summary.querySelector('[data-stock-delta]');
    const after = summary.querySelector('[data-stock-after]');
    const error = form.querySelector('[data-stock-invalid]');
    const label = form.querySelector('[data-amount-label]');
    const hint = form.querySelector('#store-amount-hint');
    summary.hidden = false;

    function refresh() {
        const count = change.value === 'COUNT';
        const out = change.value === 'ISSUE' || change.value === 'DAMAGE';
        label.textContent = count ? '최종 실사 수량' : out ? '차감 수량' : '추가 수량';
        hint.textContent = count ? '실제로 센 재고를 입력하세요.' : out ? '현재 재고에서 뺄 수량을 입력하세요.' : '현재 재고에 더할 수량을 입력하세요.';
        amount.min = count ? '0' : '1';

        const value = amount.valueAsNumber;
        const difference = count ? value - before : out ? -value : value;
        const next = before + difference;
        const outOfRange = Number.isInteger(value) && (next < 0 || next > 2147483647);
        const noChange = Number.isInteger(value) && next === before;
        amount.setCustomValidity('');
        const valid = amount.validity.valid && !outOfRange && !noChange;
        delta.textContent = valid ? (difference > 0 ? '+' : '') + difference + '개' : '—';
        after.textContent = valid ? next + '개' : '—';
        const message = outOfRange ? '변경 후 재고는 0개 이상이어야 하며 최대 허용 수량을 넘을 수 없습니다.'
            : noChange ? '현재와 다른 수량을 입력하세요.' : '';
        amount.setCustomValidity(message);
        if (error.textContent !== message) error.textContent = message;
        error.hidden = !message;
    }

    amount.addEventListener('input', refresh);
    change.addEventListener('change', refresh);
    refresh();
})();
