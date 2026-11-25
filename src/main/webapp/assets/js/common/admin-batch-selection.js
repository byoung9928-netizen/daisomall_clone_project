(() => {
    const root = document.querySelector('[data-admin-query]');
    if (!root) return;

    const readStore = content => content.querySelector('[data-admin-batch-store]')?.value || '';
    let store = readStore(root);
    let items = new Map();
    let draft = {};
    let expanded = false;

    function syncStore() {
        const next = readStore(root);
        if (next === store) return;
        store = next;
        items = new Map();
        draft = {};
        expanded = false;
    }
    document.addEventListener('admin-query-render', syncStore);

    function bind({ form, panel, boxes, selectAll, onChange }) {
        const limit = Number(form.dataset.batchLimit);
        const fieldName = form.dataset.batchSelectionName;
        const count = panel.querySelector('[data-batch-count]');
        const details = panel.querySelector('[data-batch-selection-view]');
        details.hidden = false;
        const list = panel.querySelector('[data-batch-selection-list]');
        const notice = panel.querySelector('[data-batch-selection-notice]');
        const extra = document.createElement('div');
        extra.hidden = true;
        form.append(extra);
        const visible = new Map(boxes.map(box => [box.value, box]));
        const currentItems = items;
        const key = id => JSON.stringify([store, id]);
        const select = box => currentItems.set(key(box.value), {
            id: box.value, name: box.dataset.name, code: box.dataset.code,
            stock: box.dataset.stock, threshold: box.dataset.threshold
        });

        function refresh() {
            for (const box of boxes) {
                box.checked = currentItems.has(key(box.value));
                if (box.checked) select(box);
                box.closest('tr').toggleAttribute('data-batch-selected', box.checked);
            }
            const visibleCount = boxes.filter(box => box.checked).length;
            selectAll.hidden = boxes.length === 0;
            selectAll.checked = visibleCount > 0 && visibleCount === boxes.length;
            selectAll.indeterminate = visibleCount > 0 && visibleCount < boxes.length;
            panel.hidden = currentItems.size === 0;
            count.textContent = '전체 페이지에서 상품 ' + currentItems.size + '개 선택';
            details.open = expanded;
            list.replaceChildren();
            extra.replaceChildren();
            for (const product of currentItems.values()) {
                const row = document.createElement('li');
                const label = document.createElement('span');
                label.textContent = product.name + (product.code ? ' · 품번 ' + product.code : '');
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'button secondary';
                remove.textContent = '선택 해제';
                remove.setAttribute('aria-label', product.name + ' 선택 해제');
                remove.addEventListener('click', () => {
                    const position = Array.from(list.children).indexOf(row);
                    currentItems.delete(key(product.id));
                    update();
                    const next = list.children[Math.min(position, list.children.length - 1)];
                    (next?.querySelector('button') || (selectAll.hidden
                        ? root.querySelector('[data-admin-query-form] input[type="search"]') : selectAll))?.focus();
                });
                row.append(label, remove);
                list.append(row);

                if (!visible.has(product.id)) {
                    const input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = fieldName;
                    input.value = product.id;
                    extra.append(input);
                }
            }
        }

        function update(limited = false) {
            notice.hidden = !limited;
            notice.textContent = limited ? '전체 페이지에서 최대 ' + limit + '개까지 선택할 수 있습니다.' : '';
            refresh();
            onChange();
        }

        for (const box of boxes) box.addEventListener('change', () => {
            if (!box.checked) currentItems.delete(key(box.value));
            else if (currentItems.size >= limit) {
                update(true);
                return;
            } else select(box);
            update();
        });
        selectAll.addEventListener('change', () => {
            const selecting = selectAll.checked;
            let limited = false;
            for (const box of boxes) {
                if (!selecting) currentItems.delete(key(box.value));
                else if (currentItems.has(key(box.value)) || currentItems.size < limit) select(box);
                else limited = true;
            }
            update(limited);
        });
        details.addEventListener('toggle', () => { if (details.isConnected) expanded = details.open; });
        refresh();
        panel.setAttribute('data-batch-managed', '');

        return {
            draft,
            values: () => [...currentItems.values()],
            size: () => currentItems.size,
            isVisible: id => visible.has(id),
            limit,
            clear() {
                currentItems.clear();
                expanded = false;
                update();
            }
        };
    }

    window.DaisoAdminBatchSelection = {
        bind,
        hasSelection: () => items.size > 0,
        changesStore: content => root.dataset.adminQuery === 'stores' && readStore(content) !== store
    };
})();
