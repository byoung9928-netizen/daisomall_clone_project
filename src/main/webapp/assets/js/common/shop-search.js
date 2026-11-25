(() => {
    const form = document.querySelector('[data-shop-search]');
    if (!form) return;
    const input = form.querySelector('#search-query');
    const panel = form.querySelector('[data-search-panel]');
    const clear = form.querySelector('[data-search-clear]');
    let key = 'daiso.search.' + form.dataset.historyKey;
    let restoringScope = false;
    const cache = new Map();
    const composition = window.DaisoSearchComposition.bind(input, refresh);
    const normalize = value => window.DaisoSearchComposition.normalize(value).toLocaleLowerCase('ko');
    let history = [];
    let timer;
    let pending;
    let activeQuery;
    let entries = [];
    let selected = -1;
    function readHistory() {
        try {
            const stored = JSON.parse(localStorage.getItem(key) || '[]');
            return Array.isArray(stored) ? stored.filter(item => item && typeof item.term === 'string').slice(0, 10) : [];
        } catch {
            return [];
        }
    }
    history = readHistory();

    function saveHistory() {
        try { localStorage.setItem(key, JSON.stringify(history)); } catch { /* Keep this page's history. */ }
    }

    function remember(item) {
        if (restoringScope) return;
        history = [item, ...history.filter(previous => normalize(previous.term) !== normalize(item.term))].slice(0, 10);
        saveHistory();
    }

    function node(tag, text, className) {
        const element = document.createElement(tag);
        if (text) element.textContent = text;
        if (className) element.className = className;
        return element;
    }

    function searchUrl(item) {
        const url = new URL(form.action);
        const collection = form.elements.namedItem('collection');
        if (collection) url.searchParams.set('collection', collection.value);
        url.searchParams.set(item.category ? 'category' : 'q', item.category || item.term);
        return url.href;
    }

    function option(item, query) {
        const link = node('a', '', 'shop-search-term');
        link.href = searchUrl(item);
        link.addEventListener('click', () => remember({ term: item.term, category: item.category }));
        const position = query ? item.term.toLocaleLowerCase('ko').indexOf(query.toLocaleLowerCase('ko')) : -1;
        if (item.image && !query) {
            const image = node('img');
            image.src = item.image;
            image.alt = '';
            link.append(image);
        }
        if (position < 0) link.append(document.createTextNode(item.term));
        else link.append(document.createTextNode(item.term.slice(0, position)),
            node('strong', item.term.slice(position, position + query.length)),
            document.createTextNode(item.term.slice(position + query.length)));
        entries.push(link);
        return link;
    }

    function draw(query, results, status) {
        panel.replaceChildren();
        entries = [];
        selected = -1;
        input.removeAttribute('aria-activedescendant');
        panel.setAttribute('aria-label', query ? '상품 검색어 자동완성' : '추천 및 최근 검색어');
        const list = node('div', '', query ? 'shop-search-matches' : 'shop-search-tags');
        list.id = 'shop-search-options';
        if (query) list.setAttribute('role', 'listbox');
        input.setAttribute('aria-expanded', String(!panel.hidden && Boolean(query)));
        if (!query && history.length) {
            const heading = node('div', '', 'shop-search-heading');
            const erase = node('button', '전체 삭제');
            erase.type = 'button';
            erase.addEventListener('click', () => {
                history = [];
                saveHistory();
                draw('', results, status);
                input.focus();
            });
            heading.append(node('h2', '최근 검색어'), erase);
            panel.append(heading);
            const recent = node('div', '', 'shop-search-tags');
            history.forEach(item => {
                const tag = node('div', '', 'shop-search-tag');
                const remove = node('button', '×');
                remove.type = 'button';
                remove.setAttribute('aria-label', item.term + ' 삭제');
                remove.addEventListener('click', () => {
                    history = history.filter(previous => previous !== item);
                    saveHistory();
                    draw('', results, status);
                    input.focus();
                });
                tag.append(option(item, ''), remove);
                recent.append(tag);
            });
            panel.append(recent);
        }
        if (!query) panel.append(node('h2', '추천 검색어'));
        results.forEach((item, index) => {
            const row = node('div', '', query ? 'shop-search-match' : 'shop-search-tag');
            const link = option(item, query);
            row.append(link);
            if (query) {
                row.id = 'shop-search-option-' + index;
                row.setAttribute('role', 'option');
                row.setAttribute('aria-selected', 'false');
                const fill = node('button', '↖');
                fill.type = 'button';
                fill.setAttribute('aria-label', item.term + ' 입력');
                fill.addEventListener('click', () => {
                    input.value = item.term;
                    input.focus();
                    refresh();
                });
                row.append(fill);
            }
            list.append(row);
        });
        panel.append(list);
        const announcement = node('p', status || (query && results.length ? results.length + '개 검색어' : ''), status ? 'shop-search-status' : 'visually-hidden');
        announcement.setAttribute('role', 'status');
        panel.append(announcement);
        if (!query) {
            const close = node('button', '닫기', 'shop-search-close');
            close.type = 'button';
            close.addEventListener('click', () => {
                input.focus();
                shut();
            });
            panel.append(close);
        }
    }

    function stop() {
        clearTimeout(timer);
        pending?.abort();
        pending = undefined;
        activeQuery = undefined;
    }

    function shut() {
        composition.cancelUpdate();
        stop();
        panel.hidden = true;
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
    }

    function refresh() {
        clear.hidden = !input.value;
        const query = input.value.trim().normalize('NFC');
        if (!panel.hidden && activeQuery === query) return;
        stop();
        panel.hidden = false;
        const cached = cache.get(query);
        const combine = results => {
            if (!query) return results;
            const terms = new Map(results.map(item => [normalize(item.term), item]));
            const keyword = normalize(query);
            history.forEach(item => {
                const term = normalize(item.term);
                if (term.includes(keyword) && !terms.has(term)) terms.set(term, item);
            });
            const rank = item => normalize(item.term) === keyword ? 0 : normalize(item.term).startsWith(keyword) ? 1 : 2;
            return [...terms.values()].sort((a, b) => rank(a) - rank(b)).slice(0, 10);
        };
        const render = results => {
            const items = combine(results);
            draw(query, items, items.length ? '' : query ? '추천할 검색어가 없습니다.' : '추천 검색어가 없습니다.');
        };
        if (cached && Date.now() - cached.time < 30000) {
            render(cached.items);
            return;
        }
        draw(query, [], '검색어를 불러오는 중입니다.');
        activeQuery = query;
        timer = setTimeout(async () => {
            const request = new AbortController();
            pending = request;
            try {
                const url = new URL(form.dataset.suggestUrl, location.origin);
                if (query) url.searchParams.set('q', query);
                const response = await fetch(url, { signal: request.signal });
                if (!response.ok) throw new Error();
                const items = await response.json();
                if (pending !== request || panel.hidden || input.value.trim().normalize('NFC') !== query) return;
                if (cache.size >= 30) cache.delete(cache.keys().next().value);
                cache.set(query, { time: Date.now(), items });
                render(items);
            } catch {
                if (pending === request && !panel.hidden && input.value.trim().normalize('NFC') === query) {
                    draw(query, combine([]), '추천 검색어를 불러오지 못했습니다. 검색은 계속할 수 있습니다.');
                }
            } finally {
                if (pending === request) {
                    pending = undefined;
                    activeQuery = undefined;
                }
            }
        }, query ? 250 : 0);
    }

    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-controls', 'shop-search-options');
    input.setAttribute('aria-expanded', 'false');
    input.setAttribute('aria-autocomplete', 'list');
    clear.hidden = !input.value;
    input.addEventListener('focus', refresh);
    input.addEventListener('click', () => { if (panel.hidden) refresh(); });
    input.addEventListener('input', refresh);
    clear.addEventListener('click', () => { input.value = ''; input.focus(); refresh(); });
    form.addEventListener('submit', event => {
        if (composition.blocksSubmit()) {
            event.preventDefault();
            return;
        }
        const term = input.value.trim().normalize('NFC');
        if (term) remember({ term });
    });
    form.addEventListener('focusout', event => {
        if (event.relatedTarget && !form.contains(event.relatedTarget)) shut();
    });
    document.addEventListener('pointerdown', event => { if (!form.contains(event.target)) shut(); });
    window.addEventListener('pagehide', shut);
    window.addEventListener('pageshow', async event => {
        if (!event.persisted) return;
        restoringScope = true;
        history = [];
        try {
            const response = await fetch(form.dataset.searchScopeUrl);
            if (!response.ok) throw new Error();
            key = 'daiso.search.' + await response.json();
            history = readHistory();
            restoringScope = false;
        } catch {
            // Keep history hidden until the next page load if the account is unknown.
        }
    });
    form.addEventListener('keydown', event => {
        if (event.target === input && composition.ignoreKey(event)) return;
        if (panel.hidden) return;
        if (event.key === 'Escape') {
            event.preventDefault();
            input.focus();
            shut();
        }
        if (event.target !== input || !entries.length) return;
        if (event.key === 'Enter' && selected >= 0) {
            event.preventDefault();
            entries[selected].click();
        }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            entries[selected]?.parentElement.setAttribute('aria-selected', 'false');
            const step = event.key === 'ArrowDown' ? 1 : -1;
            selected = (selected < 0 && step < 0 ? entries.length - 1 : (selected + step + entries.length) % entries.length);
            const row = entries[selected].parentElement;
            row.setAttribute('aria-selected', 'true');
            if (row.id) input.setAttribute('aria-activedescendant', row.id);
            row.scrollIntoView({ block: 'nearest' });
        }
    });
})();
