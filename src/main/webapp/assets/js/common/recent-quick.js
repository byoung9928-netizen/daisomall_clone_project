(() => {
    const root = document.querySelector('[data-recent-quick]');
    if (!root) return;
    const trigger = root.querySelector('[data-recent-trigger]');
    const panel = root.querySelector('[data-recent-panel]');
    const close = root.querySelector('[data-recent-close]');
    const scroll = root.querySelector('[data-recent-scroll]');
    const list = root.querySelector('[data-recent-list]');
    const moreButton = root.querySelector('[data-recent-more]');
    const clear = root.querySelector('[data-recent-clear]');
    const status = root.querySelector('[data-recent-status]');
    const money = new Intl.NumberFormat('ko-KR');
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
    let items = [];
    let representative;
    let token;
    let more = false;
    let opened = false;
    let busy = false;
    let loaded = false;
    let version = 0;
    let hideTimer;
    let summaryRequest;

    function element(tag, css, label) {
        const node = document.createElement(tag);
        if (css) node.className = css;
        if (label) node.textContent = label;
        return node;
    }

    function card(item) {
        const row = element('li', 'recent-quick-row');
        row.dataset.product = item.id;
        const url = new URL(root.dataset.productUrl, location.origin);
        url.searchParams.set('id', item.id);
        const cover = element('a', 'recent-quick-cover');
        cover.href = url.href;
        cover.tabIndex = -1;
        const image = element('img');
        image.src = item.image;
        image.alt = item.name;
        image.loading = 'lazy';
        cover.append(image);
        if (item.soldOut) cover.append(element('span', 'recent-quick-sold-out', item.pickup ? '택배 품절' : '일시품절'));
        const details = element('div', 'recent-quick-details');
        const price = element('div', 'recent-quick-price');
        price.append(element('strong', '', money.format(item.price) + '원'));
        if (item.newProduct) price.append(element('span', 'product-new', 'NEW'));
        const name = element('a', 'recent-quick-name', item.name);
        name.href = url.href;
        const badges = element('div', 'recent-quick-badges');
        if (item.delivery) badges.append(element('span', 'badge', '택배'));
        if (item.pickup) badges.append(element('span', 'badge', '픽업'));
        details.append(price, name, badges);
        const remove = element('button', 'recent-quick-remove', '×');
        remove.type = 'button';
        remove.dataset.removeRecent = item.id;
        remove.setAttribute('aria-label', item.name + ' 최근 본 상품에서 삭제');
        row.append(cover, details, remove);
        return row;
    }

    function setRepresentative(item) {
        representative = item;
        clearTimeout(hideTimer);
        if (item) {
            root.hidden = false;
            trigger.querySelector('img').src = item.image;
        } else if (!opened && !root.hidden) {
            hideTimer = setTimeout(() => {
                if (!opened && !representative) root.hidden = true;
            }, reducedMotion.matches ? 0 : 210);
        }
    }

    function syncControls() {
        moreButton.hidden = !more;
        moreButton.disabled = busy;
        clear.disabled = busy || items.length === 0;
        list.setAttribute('aria-busy', String(busy));
        list.querySelectorAll('[data-remove-recent]').forEach(button => { button.disabled = busy; });
    }

    function emptyMessage() {
        if (items.length === 0 && loaded) {
            list.replaceChildren(element('li', 'recent-quick-empty', '최근 본 상품이 없습니다.'));
        }
    }

    async function summary() {
        summaryRequest?.abort();
        const request = new AbortController();
        summaryRequest = request;
        try {
            const response = await fetch(root.dataset.recentUrl + '?summary=1', { signal: request.signal });
            if (!response.ok) throw new Error();
            const data = await response.json();
            if (!request.signal.aborted && !opened && !loaded) setRepresentative(data.items[0]);
        } catch { /* The shopper can continue using the current page. */ }
    }

    async function loadPage() {
        if (busy) return;
        busy = true;
        const current = version;
        const offset = items.length;
        syncControls();
        status.textContent = '상품을 불러오는 중입니다.';
        try {
            const response = await fetch(root.dataset.recentUrl + '?offset=' + offset);
            if (!response.ok) throw new Error();
            const data = await response.json();
            if (current !== version) return;
            if (!offset) list.replaceChildren();
            const seen = new Set(items.map(item => item.id));
            const next = data.items.filter(item => !seen.has(item.id));
            items.push(...next);
            list.append(...next.map(card));
            more = data.more && next.length > 0;
            token = data.token;
            loaded = true;
            setRepresentative(items[0]);
            emptyMessage();
            status.textContent = '';
        } catch {
            if (current === version) {
                status.textContent = '최근 본 상품을 불러오지 못했습니다.';
                more = true;
                moreButton.textContent = '다시 시도';
            }
        } finally {
            if (current === version) {
                busy = false;
                syncControls();
            }
        }
    }

    function show() {
        opened = true;
        root.classList.add('is-open');
        panel.inert = false;
        trigger.setAttribute('aria-expanded', 'true');
        close.focus({ preventScroll: true });
        if (!loaded) loadPage();
    }

    function hide(restoreFocus) {
        if (!opened) return;
        opened = false;
        trigger.setAttribute('aria-expanded', 'false');
        if (restoreFocus) {
            const target = representative ? trigger : document.querySelector('#main-content');
            target?.focus({ preventScroll: true });
        } else if (panel.contains(document.activeElement)) {
            document.activeElement.blur();
        }
        panel.inert = true;
        root.classList.remove('is-open');
        setRepresentative(representative);
    }

    async function remove(id, button) {
        if (busy || !token) return;
        if (!id && !window.confirm('최근 본 상품을 모두 삭제할까요?')) return;
        busy = true;
        const current = version;
        syncControls();
        const body = new URLSearchParams({ csrf: token, action: id ? 'remove' : 'clear' });
        if (id) body.set('productId', id);
        let nextFocus = clear;
        let refresh = false;
        try {
            const response = await fetch(root.dataset.recentUrl, { method: 'POST', body });
            if (!response.ok) throw new Error();
            if (current !== version) return;
            if (id) {
                const row = button.closest('li');
                const rows = [...list.children];
                const positions = rows.map(node => node.getBoundingClientRect().top);
                nextFocus = (row.nextElementSibling || row.previousElementSibling)?.querySelector('button') || clear;
                row.remove();
                if (!reducedMotion.matches) rows.forEach((node, index) => {
                    if (!node.isConnected) return;
                    const delta = positions[index] - node.getBoundingClientRect().top;
                    if (delta) node.animate([{ transform: 'translateY(' + delta + 'px)' }, { transform: 'translateY(0)' }],
                        { duration: 165, easing: 'ease-out' });
                });
                items = items.filter(item => item.id !== id);
                refresh = more && !items.length;
            } else {
                items = [];
                more = false;
            }
            emptyMessage();
            if (!refresh) setRepresentative(items[0]);
            status.textContent = id ? '상품을 삭제했습니다.' : '최근 본 상품을 모두 삭제했습니다.';
        } catch {
            if (current === version) {
                status.textContent = '삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.';
                nextFocus = button;
            }
        } finally {
            if (current === version) {
                busy = false;
                syncControls();
                if (opened) (nextFocus.disabled ? close : nextFocus).focus({ preventScroll: true });
                if (refresh) loadPage();
            }
        }
    }

    trigger.addEventListener('click', () => opened ? hide(true) : show());
    close.addEventListener('click', () => hide(true));
    clear.addEventListener('click', () => remove(null, clear));
    moreButton.addEventListener('click', () => {
        moreButton.textContent = '상품 더 보기';
        loadPage();
    });
    list.addEventListener('click', event => {
        const button = event.target.closest('[data-remove-recent]');
        if (button) remove(button.dataset.removeRecent, button);
    });
    document.addEventListener('pointerdown', event => {
        if (opened && !root.contains(event.target)) hide(false);
    });
    document.addEventListener('keydown', event => {
        if (opened && event.key === 'Escape' && !event.defaultPrevented && !document.querySelector('dialog[open]')) {
            event.preventDefault();
            hide(true);
        }
    });
    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        version++;
        hide(false);
        setRepresentative(undefined);
        root.hidden = true;
        items = [];
        loaded = more = busy = false;
        token = undefined;
        list.replaceChildren();
        syncControls();
        summary();
    });
    if (root.dataset.hasRecent === 'true') summary();
})();
