(() => {
    const storageKey = 'daisomall.admin.categories.open';
    const open = new Set();
    try {
        const saved = JSON.parse(sessionStorage.getItem(storageKey) || '[]');
        if (Array.isArray(saved)) saved.forEach(id => open.add(id));
    } catch { /* Native details remain usable without storage. */ }

    function bindTree() {
        const tree = document.querySelector('[data-category-tree]');
        if (!tree) return;
        const controls = document.querySelector('[data-category-expand-controls]');
        controls.hidden = false;
        controls.addEventListener('click', event => {
            const button = event.target.closest('[data-category-expand]');
            if (!button) return;
            tree.querySelectorAll('[data-category-branch]').forEach(branch => {
                branch.open = button.dataset.categoryExpand === 'true';
            });
        });
        tree.querySelectorAll('[data-category-branch]').forEach(branch => {
            const id = branch.dataset.categoryBranch;
            if (open.has(id)) branch.open = true;
            branch.addEventListener('toggle', () => {
                if (branch.open) open.add(id);
                else open.delete(id);
                try { sessionStorage.setItem(storageKey, JSON.stringify([...open])); } catch {}
            });
        });
    }
    bindTree();
    document.addEventListener('admin-query-render', bindTree);
})();
