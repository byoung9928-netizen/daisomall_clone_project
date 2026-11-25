(() => {
    const root = document.querySelector('[data-admin-query]');
    if (!root || !root.querySelector('[data-admin-suggestions]')) return;

    const { bind, normalize } = window.DaisoSearchComposition;
    const money = new Intl.NumberFormat('ko-KR');
    let dispose = () => {};
    let reopenAfterRender = false;

    function setup() {
        dispose();
        const wrapper = root.querySelector('[data-admin-suggestions]');
        if (!wrapper) return;
        const form = wrapper.closest('form');
        const input = wrapper.querySelector('input[type="search"]');
        const filterNames = wrapper.dataset.suggestFilters.split(' ');
        const exact = form.elements.selectedProductId;
        const panel = wrapper.querySelector('[data-admin-suggest-panel]');
        const status = wrapper.querySelector('[data-admin-suggest-status]');
        const list = panel.querySelector('[role="listbox"]');
        const listeners = new AbortController();
        const options = { signal: listeners.signal };
        let pending;
        let timer;
        let activeKey;
        let entries = [];
        let selected = -1;
        let filterSubmitting = false;
        const composition = bind(input, refresh, options);

        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-controls', list.id);
        input.setAttribute('aria-expanded', 'false');

        function stop() {
            clearTimeout(timer);
            pending?.abort();
            pending = undefined;
            activeKey = undefined;
        }

        function close() {
            stop();
            composition.cancelUpdate();
            panel.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
            selected = -1;
        }

        function suggestUrl() {
            const keyword = normalize(input.value);
            const quantity = form.elements.stockQuantity;
            const stock = form.elements.filterStock?.value;
            if (!keyword || (['AT_LEAST', 'AT_MOST'].includes(stock) && (!quantity.value || !quantity.validity.valid))) return null;
            const url = new URL(wrapper.dataset.adminSuggestions, location.origin);
            url.searchParams.set(input.name, keyword);
            for (const name of filterNames) {
                const field = form.elements.namedItem(name);
                if (field && !field.disabled && field.value) url.searchParams.set(name, field.value);
            }
            return url;
        }

        function make(tag, text, css) {
            const element = document.createElement(tag);
            if (css) element.className = css;
            if (text) element.textContent = text;
            return element;
        }

        function choose(item) {
            input.value = item.code || item.id;
            exact.value = item.id;
            reopenAfterRender = false;
            close();
            form.requestSubmit();
        }

        function draw(items, message) {
            selected = -1;
            input.removeAttribute('aria-activedescendant');
            status.textContent = message;
            status.hidden = !message;
            entries = items.map((item, index) => {
                const row = make('li', '', 'admin-product-suggest-option');
                const button = make('button', '', 'admin-product-suggest-button');
                row.id = list.id + '-' + index;
                row.setAttribute('role', 'option');
                row.setAttribute('aria-selected', 'false');
                button.type = 'button';
                button.tabIndex = -1;
                const image = make('img');
                image.alt = '';
                image.src = item.image;
                image.width = image.height = 40;
                const detail = make('span', '', 'admin-product-suggest-detail');
                detail.append(make('strong', item.name), make('small', '품번 ' + item.code + ' · ' + money.format(item.price) + '원'),
                    make('small', wrapper.dataset.suggestStockLabel + ' ' + money.format(item.stock) + '개 · ' + item.stockStatus));
                button.append(image, detail);
                button.addEventListener('mousedown', event => event.preventDefault());
                button.addEventListener('click', () => choose(item));
                row.append(button);
                return row;
            });
            list.replaceChildren(...entries);
        }

        function refresh() {
            const url = suggestUrl();
            if (!url) {
                close();
                return;
            }
            if (!panel.hidden && activeKey === url.href) return;
            stop();
            activeKey = url.href;
            panel.hidden = false;
            input.setAttribute('aria-expanded', 'true');
            draw([], '추천 상품을 불러오는 중입니다.');
            timer = setTimeout(async () => {
                const request = new AbortController();
                pending = request;
                const isCurrent = () => pending === request && wrapper.isConnected && !panel.hidden && suggestUrl()?.href === url.href;
                try {
                    const response = await fetch(url, { signal: request.signal, cache: 'no-store' });
                    if (!response.ok) throw new Error();
                    const items = await response.json();
                    if (isCurrent()) draw(items, items.length ? '' : '일치하는 상품이 없습니다.');
                } catch {
                    if (isCurrent()) {
                        activeKey = undefined;
                        draw([], '추천 상품을 불러오지 못했습니다.');
                    }
                } finally {
                    if (pending === request) pending = undefined;
                }
            }, 250);
        }

        input.addEventListener('focus', refresh, options);
        input.addEventListener('click', () => { if (panel.hidden) refresh(); }, options);
        input.addEventListener('input', () => {
            exact.value = '';
            refresh();
        }, options);
        input.addEventListener('keydown', event => {
            if (composition.ignoreKey(event) || panel.hidden) return;
            if (event.key === 'Escape') {
                event.preventDefault();
                reopenAfterRender = false;
                close();
            }
            if (!entries.length) return;
            if (event.key === 'Enter' && selected >= 0) {
                event.preventDefault();
                entries[selected].querySelector('button').click();
            }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                entries[selected]?.setAttribute('aria-selected', 'false');
                const step = event.key === 'ArrowDown' ? 1 : -1;
                selected = selected < 0 ? (step > 0 ? 0 : entries.length - 1)
                    : (selected + step + entries.length) % entries.length;
                const row = entries[selected];
                row.setAttribute('aria-selected', 'true');
                input.setAttribute('aria-activedescendant', row.id);
                row.scrollIntoView({ block: 'nearest' });
            }
        }, options);
        form.addEventListener('change', event => {
            if (!filterNames.includes(event.target.name)) return;
            filterSubmitting = event.target.tagName === 'SELECT';
            refresh();
            queueMicrotask(() => { filterSubmitting = false; });
        }, options);
        form.addEventListener('submit', event => {
            if (composition.blocksSubmit()) {
                event.preventDefault();
                return;
            }
            reopenAfterRender = filterSubmitting && !panel.hidden;
            if (!reopenAfterRender) close();
        }, options);
        form.addEventListener('focusout', event => {
            if (event.relatedTarget && !form.contains(event.relatedTarget)) close();
        }, options);
        document.addEventListener('pointerdown', event => {
            if (!form.contains(event.target)) {
                reopenAfterRender = false;
                close();
            }
        }, options);
        window.addEventListener('pagehide', close, options);
        dispose = () => {
            close();
            listeners.abort();
        };
        if (reopenAfterRender) {
            reopenAfterRender = false;
            refresh();
        }
    }

    document.addEventListener('admin-query-render', setup);
    setup();
})();
