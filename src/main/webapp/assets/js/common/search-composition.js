(() => {
    const normalize = value => value.trim().normalize('NFC');

    function bind(input, refresh, options) {
        let composing = false;
        let imeEnter = false;
        let timer;
        const cancelUpdate = () => clearTimeout(timer);
        const refreshFocused = () => {
            if (document.activeElement === input) refresh();
        };
        input.addEventListener('compositionstart', () => { composing = true; }, options);
        input.addEventListener('compositionupdate', () => {
            // compositionupdate can arrive before the browser changes the input value.
            cancelUpdate();
            timer = setTimeout(refreshFocused, 0);
        }, options);
        input.addEventListener('compositionend', () => {
            cancelUpdate();
            composing = false;
            refreshFocused();
        }, options);
        input.addEventListener('keyup', event => { if (event.key === 'Enter') imeEnter = false; }, options);
        options?.signal?.addEventListener('abort', cancelUpdate, { once: true });

        return {
            cancelUpdate,
            blocksSubmit: () => composing || imeEnter,
            ignoreKey(event) {
                if (composing || event.isComposing || event.keyCode === 229) {
                    if (event.key === 'Enter') imeEnter = true;
                    return true;
                }
                if (event.key !== 'Enter') return false;
                if (imeEnter && event.repeat) return true;
                imeEnter = false;
                return false;
            }
        };
    }

    window.DaisoSearchComposition = { bind, normalize };
})();
