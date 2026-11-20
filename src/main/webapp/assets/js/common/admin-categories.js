(() => {
    const tree = document.querySelector('[data-category-tree]');
    let panel = document.querySelector('[data-category-panel]');
    if (!tree || !panel) return;

    const status = document.querySelector('[data-category-status]');
    const branches = [...tree.querySelectorAll('[data-category-branch]')];
    const links = [...tree.querySelectorAll('[data-category-id]')];
    const storageKey = 'daisomall.admin.categories.open';
    const search = document.querySelector('#category-search').value;
    const open = new Set();
    let pending;

    try {
        const saved = JSON.parse(sessionStorage.getItem(storageKey) || '[]');
        if (Array.isArray(saved)) saved.forEach(id => open.add(id));
    } catch {
        // The tree works with native disclosure controls when storage is unavailable.
    }

    branches.forEach(branch => {
        const id = branch.dataset.categoryBranch;
        if (open.has(id)) branch.open = true;
        branch.addEventListener('toggle', () => {
            if (branch.open) open.add(id);
            else open.delete(id);
            try { sessionStorage.setItem(storageKey, JSON.stringify([...open])); } catch {}
        });
    });

    function highlight() {
        links.forEach(link => {
            const active = link.dataset.categoryId === panel.dataset.selected;
            if (!active) {
                link.removeAttribute('aria-current');
                return;
            }
            link.setAttribute('aria-current', 'true');
            for (let branch = link.closest('details'); branch; branch = branch.parentElement.closest('details')) {
                branch.open = true;
            }
        });
    }

    async function navigate(url, push) {
        pending?.abort();
        const controller = new AbortController();
        pending = controller;
        panel.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', signal: controller.signal });
            if (!response.ok || response.redirected) throw new Error('Category panel unavailable');
            const source = new DOMParser().parseFromString(await response.text(), 'text/html').querySelector('[data-category-panel]');
            if (!source) throw new Error('Category panel missing');
            if (controller.signal.aborted) return;
            const next = document.importNode(source, true);
            panel.replaceWith(next);
            panel = next;
            document.querySelector('[data-category-saved]')?.remove();
            if (push) history.pushState(null, '', url);
            highlight();
            const heading = panel.querySelector(url.hash === '#category-history-heading'
                ? '#category-history-heading' : '#category-detail-heading');
            if (heading) {
                heading.setAttribute('tabindex', '-1');
                heading.focus();
                status.textContent = heading.textContent.trim();
            }
        } catch (error) {
            if (error.name !== 'AbortError') location.assign(url.href);
        } finally {
            if (pending === controller) panel.removeAttribute('aria-busy');
        }
    }

    document.querySelector('.category-workspace').addEventListener('click', event => {
        const link = event.target.closest('[data-category-nav]');
        if (!link || event.button !== 0 || event.ctrlKey || event.shiftKey || event.altKey || event.metaKey) return;
        const url = new URL(link.href);
        if (url.origin !== location.origin) return;
        event.preventDefault();
        navigate(url, true);
    });

    window.addEventListener('popstate', () => {
        const url = new URL(location.href);
        if ((url.searchParams.get('q') || '') === search) navigate(url, false);
        else location.reload();
    });
    highlight();
})();
