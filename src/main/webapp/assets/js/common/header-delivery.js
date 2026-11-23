(() => {
    const menu = document.getElementById('header-delivery-menu');
    if (!menu) return;

    const trigger = document.getElementById('header-delivery-toggle');
    const panel = document.getElementById('header-delivery-panel');
    const buttons = [...panel.querySelectorAll('[data-mode]')];
    const addressRow = panel.querySelector('[data-address-row]');
    const pickupRow = panel.querySelector('[data-pickup-row]');
    const status = panel.querySelector('[data-delivery-status]');
    const loginLink = panel.querySelector('[data-delivery-login]');

    let mode = 'parcel';
    let pinned = false;
    let summary = null;
    let loading = false;

    function showRows() {
        addressRow.hidden = !summary || mode === 'pickup';
        pickupRow.hidden = !summary || (mode !== 'pickup' && mode !== 'bulk');
    }

    async function loadSummary() {
        if (summary || loading) return;

        loading = true;
        status.hidden = false;
        status.textContent = '배송지와 픽업매장을 불러오는 중입니다.';
        loginLink.hidden = true;

        try {
            const response = await fetch(menu.dataset.summaryUrl);
            if (response.status === 401) {
                status.textContent = '로그인하면 등록한 배송지와 매장을 볼 수 있습니다.';
                loginLink.hidden = false;
                return;
            }
            if (!response.ok) throw new Error('배송·픽업 정보 조회 실패');

            summary = await response.json();
            panel.querySelector('[data-address-text]').textContent =
                summary.address || '등록한 배송지가 없습니다.';
            panel.querySelector('[data-pickup-text]').textContent =
                summary.pickupStore || '선택한 픽업매장이 없습니다.';
            status.hidden = true;
            showRows();
        } catch (error) {
            status.textContent = '배송·픽업 정보를 불러오지 못했습니다.';
        } finally {
            loading = false;
        }
    }

    function setOpen(open) {
        panel.hidden = !open;
        trigger.setAttribute('aria-expanded', String(open));
        if (open) loadSummary();
    }

    menu.addEventListener('pointerenter', event => {
        if (event.pointerType === 'mouse') setOpen(true);
    });

    menu.addEventListener('pointerleave', () => {
        if (!pinned && !panel.contains(document.activeElement)) setOpen(false);
    });

    trigger.addEventListener('click', () => {
        pinned = !pinned;
        setOpen(pinned);
    });

    document.addEventListener('click', event => {
        if (!menu.contains(event.target)) {
            pinned = false;
            setOpen(false);
        }
    });

    menu.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !panel.hidden) {
            event.preventDefault();
            pinned = false;
            setOpen(false);
            trigger.focus();
        }
    });

    buttons.forEach(button => {
        button.addEventListener('click', () => {
            mode = button.dataset.mode;
            buttons.forEach(item => {
                item.setAttribute('aria-pressed', String(item === button));
            });
            showRows();
        });
    });
})();