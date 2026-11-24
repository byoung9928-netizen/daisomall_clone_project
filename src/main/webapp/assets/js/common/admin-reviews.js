(() => {
    const filters = document.querySelector('#admin-review-filters');
    if (filters) {
        const { start, end, period, sort } = filters.elements;
        const validateDates = () => {
            const custom = period.value === 'CUSTOM';
            start.required = end.required = custom;
            end.setCustomValidity(custom && start.value && end.value && start.value > end.value
                ? '종료일은 시작일 이후여야 합니다.' : '');
        };
        filters.querySelectorAll('[name="period"]').forEach(preset => {
            preset.addEventListener('change', () => {
                if (preset.value !== 'CUSTOM') {
                    start.value = preset.dataset.start;
                    end.value = preset.dataset.end;
                }
                validateDates();
            });
        });
        [start, end].forEach(date => date.addEventListener('input', () => {
            period.value = 'CUSTOM';
            validateDates();
        }));
        sort.addEventListener('change', () => filters.requestSubmit());
        validateDates();
    }

    if (document.querySelector('[data-review-fallback]') && /^#review-\d+$/.test(location.hash)) {
        document.querySelector('#review-results').scrollIntoView();
    }

    document.querySelectorAll('[data-admin-review-action]').forEach(form => {
        form.addEventListener('submit', event => {
            const message = event.submitter?.dataset.confirm;
            if (message && !window.confirm(message)) event.preventDefault();
        });
    });
})();
