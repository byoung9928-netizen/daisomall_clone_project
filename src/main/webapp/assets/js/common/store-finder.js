(() => {
    const picker = document.querySelector('[data-store-picker]');
    if (!picker || typeof picker.showModal !== 'function') return;

    picker.removeAttribute('open');
    picker.showModal();

    picker.addEventListener('cancel', event => {
        event.preventDefault();
        location.assign(picker.querySelector('[data-picker-close]').href);
    });
})();
