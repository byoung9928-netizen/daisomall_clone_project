(() => {
    const root = document.querySelector('.store-manager-assortment');
    if (!root) return;

    const names = { REGISTER: '취급 등록', UNLIST: '취급 종료', RESUME: '재취급' };
    const saleStates = { AVAILABLE: '판매 중', SOLD_OUT: '온라인 품절', SCHEDULED: '판매 예정', DISCONTINUED: '판매 종료' };
    const number = new Intl.NumberFormat('ko-KR');
    const items = new Map();
    let opener;
    let token = '';
    let busy = false;

    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text) node.textContent = text;
        if (className) node.className = className;
        return node;
    }

    function field(name, value, label, min) {
        const input = element('input');
        input.name = name;
        input.value = value;
        if (!label) {
            input.type = 'hidden';
            return input;
        }
        input.type = 'number';
        input.required = true;
        input.min = min;
        input.max = 2147483647;
        const wrapper = element('label', label, 'field');
        wrapper.append(input);
        return wrapper;
    }

    root.addEventListener('store-assortment-open', event => {
        const form = root.querySelector('[data-assortment-form]');
        const { rows, button } = event.detail;
        const operation = rows[0]?.operation;
        if (!operation || busy) return;
        opener = button;
        token = '';
        items.clear();
        form.reset();
        form.elements.operation.value = operation;
        const title = names[operation];
        form.querySelector('[data-assortment-title]').textContent = rows.length === 1 ? title : '일괄 ' + title;
        form.querySelector('[data-assortment-intro]').textContent = rows.length + '종 · '
            + (operation === 'UNLIST' ? '상품별 종료 가능 여부를 미리 확인하세요.'
                : '설정할 재고와 부족 기준을 확인한 뒤 미리보기를 실행하세요.');
        form.querySelector('[data-assortment-error]').hidden = true;
        form.querySelector('button[type=submit]').textContent = '미리보기';
        const container = form.querySelector('[data-assortment-items]');
        container.replaceChildren();

        for (const row of rows) {
            const item = element('section', '', 'store-assortment-item');
            const image = element('img', '', 'store-inventory-thumb');
            image.src = row.image;
            image.alt = '';
            image.width = 44;
            image.height = 44;
            const detail = element('div');
            detail.append(element('strong', row.name), element('small', [row.code, row.productId, saleStates[row.sale] || row.sale]
                .filter(Boolean).join(' · ')));
            const heading = element('div', '', 'store-assortment-item-heading');
            heading.append(image, detail);
            item.append(heading, field('item', row.productId), field('version', row.version));
            const hasStock = Number(row.version) >= 0;
            const quantity = Number(row.quantity);
            const preserve = operation === 'RESUME' && quantity > 0;
            item.append(element('p', hasStock ? '현재 ' + number.format(quantity) + '개 · 부족 기준 ' + row.threshold + '개'
                : '매장 재고 해당 없음', 'inventory-scope'));
            if (preserve) item.append(element('p', '복구된 재고 ' + number.format(quantity) + '개를 보존합니다.', 'store-recovered'));
            const inputs = element('div', '', 'store-assortment-inputs');
            inputs.append(field('quantity', operation === 'RESUME' ? quantity : 0,
                operation === 'UNLIST' || preserve ? '' : '초기 재고', 0));
            inputs.append(field('threshold', row.threshold,
                operation === 'UNLIST' || form.dataset.thresholds !== 'true' ? '' : '부족 기준', 1));
            const result = element('p');
            result.hidden = true;
            item.append(inputs, result);
            items.set(row.productId, result);
            container.append(item);
        }
        form.closest('dialog').showModal();
        (container.querySelector('input[type=number]') || form.elements.reason).focus({ preventScroll: true });
    });

    function resetPreview(form) {
        token = '';
        for (const row of items.values()) row.hidden = true;
        form.querySelector('button[type=submit]').textContent = '미리보기';
        form.querySelector('[data-assortment-error]').hidden = true;
    }

    root.addEventListener('input', event => {
        const form = event.target.closest('[data-assortment-form]');
        if (form && !busy) resetPreview(form);
    });
    root.addEventListener('close', event => {
        if (event.target.id === 'store-assortment-edit' && opener?.isConnected) opener.focus({ preventScroll: true });
    }, true);
    root.addEventListener('cancel', event => {
        if (busy && event.target.id === 'store-assortment-edit') event.preventDefault();
    }, true);

    root.addEventListener('submit', async event => {
        const form = event.target;
        if (!form.matches('[data-assortment-form]')) return;
        event.preventDefault();
        if (busy) return;
        const submit = form.querySelector('button[type=submit]');
        const alert = form.querySelector('[data-assortment-error]');
        if (!form.elements.reason.value.trim()) {
            alert.hidden = false;
            alert.textContent = '변경 사유를 입력해 주세요.';
            form.elements.reason.focus();
            return;
        }
        const body = new URLSearchParams(new FormData(form));
        const saving = Boolean(token);
        if (saving) body.set('previewToken', token);
        token = '';
        alert.hidden = true;
        const controls = [...form.querySelectorAll('button, input:not([type=hidden])')].filter(control => !control.disabled);
        for (const control of controls) control.disabled = true;
        busy = true;
        form.setAttribute('aria-busy', 'true');
        submit.textContent = saving ? '저장 중…' : '확인 중…';
        let navigating = false;
        try {
            const response = await fetch(saving ? form.dataset.save : form.dataset.preview, {
                method: 'POST', body, credentials: 'same-origin', headers: { Accept: 'application/json' }
            });
            const data = await response.json();
            for (const entry of data.items || []) {
                const result = items.get(entry.id);
                if (!result) continue;
                result.hidden = false;
                result.className = entry.message ? 'error-message' : 'inventory-scope';
                result.textContent = entry.message || (form.elements.operation.value === 'UNLIST' ? '종료 가능 · 예정 재고 0개'
                    : '예정 재고 ' + number.format(entry.afterQuantity) + '개 · 부족 기준 ' + number.format(entry.afterThreshold) + '개');
            }
            if (!response.ok) throw new Error(data.message || '요청을 처리하지 못했습니다.');
            if (saving) {
                const target = new URL(data.url, location.href);
                if (target.origin !== location.origin || target.pathname !== location.pathname) throw new Error();
                try {
                    const table = root.querySelector('.store-inventory-table');
                    sessionStorage.setItem('store-inventory-save:' + location.pathname, JSON.stringify({
                        x: window.scrollX, y: window.scrollY, left: table.scrollLeft, top: table.scrollTop
                    }));
                } catch { /* Scroll restoration is optional. */ }
                location.assign(target.href);
                navigating = true;
            } else {
                token = typeof data.token === 'string' ? data.token : '';
                if (!token) throw new Error('처리할 수 없는 상품이 있습니다. 상품별 안내를 확인해 주세요.');
            }
        } catch (error) {
            alert.hidden = false;
            alert.textContent = error instanceof SyntaxError || error instanceof TypeError || !error.message
                ? saving ? '저장 결과를 확인하지 못했습니다. 목록과 이력을 확인해 주세요.' : '미리보기를 불러오지 못했습니다.'
                : error.message;
        } finally {
            if (!navigating) {
                busy = false;
                form.removeAttribute('aria-busy');
                for (const control of controls) control.disabled = false;
                submit.textContent = token ? '변경 확정' : '미리보기';
                submit.focus({ preventScroll: true });
            }
        }
    });
})();
