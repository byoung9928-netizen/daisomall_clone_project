(() => {
    const detail = document.querySelector(".pd-product-detail");
    if (!detail) return;

    const gallery = detail.querySelector("[data-gallery]");
    const thumbnails = Array.from(gallery.querySelectorAll("[data-gallery-thumb]"));
    if (thumbnails.length > 1) {
        const main = gallery.querySelector("[data-gallery-main]");
        const status = gallery.querySelector("[data-gallery-status]");
        let current = 0;
        const showImage = (index) => {
            thumbnails[current].setAttribute("aria-pressed", "false");
            current = (index + thumbnails.length) % thumbnails.length;
            const selected = thumbnails[current];
            selected.setAttribute("aria-pressed", "true");
            main.src = selected.querySelector("img").src;
            status.textContent = selected.getAttribute("aria-label");
        };
        thumbnails.forEach((button, index) => button.addEventListener("click", () => showImage(index)));
        gallery.querySelectorAll("[data-gallery-step]").forEach((button) => {
            button.addEventListener("click", () => showImage(current + Number(button.dataset.galleryStep)));
        });
    }

    const delivery = detail.querySelector("[data-delivery]");
    const pickup = detail.querySelector("[data-pickup]");
    const cart = detail.querySelector("#cartAddForm");
    const cartButton = cart.querySelector("[data-add-cart]");
    const quantity = cart.elements.quantity;
    const total = detail.querySelector("[data-total]");
    const quantityHint = detail.querySelector("[data-quantity-hint]");
    const steppers = Array.from(cart.querySelectorAll("[data-quantity-step]"));
    const deliveryLimit = Number(detail.dataset.deliveryLimit);
    const price = Number(detail.dataset.unitPrice);
    const money = new Intl.NumberFormat("ko-KR");

    let pickupMode = false;
    let selectedStore = null;
    let deliveryQuantity = 1;
    let pickupQuantity = 1;
    let saving = false;

    const maximum = () => pickupMode ? Math.min(99, Number(selectedStore?.dataset.quantity || 0)) : deliveryLimit;
    const validQuantity = (limit) => limit > 0 && quantity.value !== "" && quantity.validity.valid;
    const updateQuantity = () => {
        const limit = maximum();
        const valid = validQuantity(limit);
        const count = valid ? quantity.valueAsNumber : 0;
        total.textContent = money.format(price * count) + "원";
        quantityHint.textContent = limit < 1 ? ""
            : valid ? "최대 " + limit + "개" : "1~" + limit + "개 사이의 수량을 입력해 주세요.";
        steppers[0].disabled = !valid || count <= 1;
        steppers[1].disabled = !valid || count >= limit;
        cartButton.disabled = pickupMode || !valid || saving;
        cartButton.textContent = pickupMode ? "픽업 담기 준비 중" : "장바구니 담기";
    };
    const restoreQuantity = () => {
        const limit = maximum();
        quantity.max = Math.max(1, limit);
        quantity.disabled = limit < 1;
        quantity.value = limit > 0 ? Math.min(limit, pickupMode ? pickupQuantity : deliveryQuantity) : "";
        updateQuantity();
    };

    quantity.addEventListener("input", updateQuantity);
    quantity.addEventListener("change", () => {
        if (!validQuantity(maximum())) return;
        if (pickupMode) pickupQuantity = quantity.valueAsNumber;
        else deliveryQuantity = quantity.valueAsNumber;
    });
    steppers.forEach((button) => {
        button.addEventListener("click", () => {
            quantity.value = quantity.valueAsNumber + Number(button.dataset.quantityStep);
            quantity.dispatchEvent(new Event("change"));
            updateQuantity();
        });
    });

    if (pickup) {
        const dialog = detail.querySelector("[data-pickup-dialog]");
        const search = dialog.querySelector("[data-store-search]");
        const stores = Array.from(dialog.querySelectorAll("[name=pickupStore]"));
        const confirm = dialog.querySelector("[data-confirm-store]");
        const empty = dialog.querySelector("[data-no-stores]");
        const pickupInfo = detail.querySelector("[data-pickup-info]");
        const deliveryInfo = detail.querySelector("[data-delivery-info]");
        const pickupNote = detail.querySelector("[data-pickup-note]");
        const summary = detail.querySelector("[data-store-summary]");
        const changeStore = detail.querySelector("[data-open-stores]");
        let opener = null;

        const setMode = (nextPickupMode) => {
            pickupMode = nextPickupMode;
            delivery.setAttribute("aria-pressed", String(!pickupMode));
            pickup.setAttribute("aria-pressed", String(pickupMode));
            deliveryInfo.hidden = pickupMode;
            pickupInfo.hidden = !pickupMode;
            pickupNote.hidden = !pickupMode;
            restoreQuantity();
        };

        const searchStores = () => {
            const keyword = search.value.trim().toLocaleLowerCase("ko");
            let matches = 0;
            stores.forEach((radio) => {
                const searchable = (radio.dataset.name + " " + radio.dataset.city).toLocaleLowerCase("ko");
                radio.closest("label").hidden = !searchable.includes(keyword);
                radio.disabled = radio.closest("label").hidden;
                if (!radio.disabled) matches++;
            });
            empty.hidden = matches > 0;
            confirm.disabled = !stores.some((radio) => radio.checked && !radio.disabled);
        };
        const openStores = (button) => {
            opener = button;
            search.value = "";
            stores.forEach((radio) => { radio.checked = radio === selectedStore; });
            searchStores();
            dialog.showModal();
        };

        delivery.addEventListener("click", () => setMode(false));
        pickup.addEventListener("click", () => {
            setMode(true);
            openStores(pickup);
        });
        changeStore.addEventListener("click", () => openStores(changeStore));
        search.addEventListener("input", searchStores);
        dialog.addEventListener("change", searchStores);
        dialog.querySelector("[data-close-stores]").addEventListener("click", () => dialog.close());
        dialog.addEventListener("close", () => opener?.focus());
        confirm.addEventListener("click", () => {
            const store = stores.find((radio) => radio.checked && !radio.disabled);
            if (!store) return;
            selectedStore = store;
            summary.textContent = store.dataset.name + " · " + store.dataset.city + " · 재고 " + store.dataset.quantity + "개";
            changeStore.textContent = "매장 변경";
            restoreQuantity();
            dialog.close();
        });
    }

    restoreQuantity();
    cart.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (pickupMode || saving || !validQuantity(deliveryLimit)) return;
        saving = true;
        updateQuantity();
        try {
            const response = await fetch(cart.action, {
                method: "POST",
                body: new URLSearchParams(new FormData(cart))
            });
            if (!response.ok) throw new Error("장바구니 저장 중 오류가 발생했습니다.");
            const result = (await response.text()).trim();
            window.Daiso.ui.notice(result === "OK" ? "장바구니에 담았습니다." : result);
        } catch (error) {
            window.Daiso.ui.notice(error.message);
        } finally {
            saving = false;
            updateQuantity();
        }
    });
})();
