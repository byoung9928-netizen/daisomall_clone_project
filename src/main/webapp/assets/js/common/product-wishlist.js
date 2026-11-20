(() => {
    "use strict";

    const configFor = button => button.closest('[data-wishlist-config]')
        || document.querySelector('[data-wishlist-config]');
    const pending = new Set();

    function notice(config, message) {
        config.querySelector("[data-wishlist-feedback]").textContent = message;
    }

    function login(config) {
        if (window.confirm("로그인이 필요한 기능입니다. 로그인 화면으로 이동할까요?")) {
            location.assign(config.dataset.loginUrl);
        }
    }

    function buttonsFor(productId) {
        return Array.from(document.querySelectorAll("[data-wishlist-product]"))
            .filter(button => button.dataset.wishlistProduct === productId);
    }

    // 이벤트 위임으로 더 보기로 추가된 상품 카드도 처리
    document.addEventListener("click", async event => {
        const button = event.target.closest("[data-wishlist-product]");
        if (!button || button.disabled) return;
        const config = configFor(button);
        if (!config) return;
        event.preventDefault();
        if (config.dataset.loggedIn !== "true") {
            login(config);
            return;
        }

        const productId = button.dataset.wishlistProduct;
        if (pending.has(productId)) return;
        const selected = button.getAttribute("aria-pressed") !== "true";
        pending.add(productId);
        buttonsFor(productId).forEach(control => {
            control.disabled = true;
            control.setAttribute("aria-busy", "true");
        });

        try {
            const response = await fetch(config.dataset.baseUrl + (selected ? "/add" : "/remove"), {
                method: "POST",
                credentials: "same-origin",
                cache: "no-store",
                body: new URLSearchParams({ productId, csrf: config.dataset.csrf })
            });
            const code = (await response.text()).trim();
            if (response.status === 401) {
                notice(config, "로그인이 만료되었습니다. 다시 로그인해 주세요.");
                login(config);
                return;
            }
            if (response.status === 403) {
                notice(config, "보안 확인에 실패했습니다. 화면을 새로고침한 후 다시 시도해 주세요.");
                return;
            }
            if (code === "STORAGE_NOT_READY") {
                notice(config, "찜 DB 테이블이 아직 적용되지 않아 사용할 수 없습니다.");
                return;
            }
            if (!response.ok || code !== (selected ? "ADDED" : "REMOVED")) {
                notice(config, response.status === 400
                    ? "상품 정보를 확인해 주세요."
                    : "저장 결과를 확인하지 못했습니다. 새로고침 후 찜 상태를 확인해 주세요.");
                return;
            }

            // 서버의 커밋 성공 응답을 받은 경우에만 모든 동일 상품 버튼 갱신
            buttonsFor(productId).forEach(control => {
                control.setAttribute("aria-pressed", String(selected));
                control.classList.toggle("is-selected", selected);
                control.setAttribute("aria-label",
                    control.dataset.wishlistName + (selected ? " 관심상품 해제" : " 관심상품 등록"));
                control.querySelector("[data-wishlist-label]").textContent = selected ? "찜 해제" : "찜";
            });
            notice(config, selected ? "관심상품에 등록했습니다." : "관심상품에서 해제했습니다.");
            if (!selected && config.dataset.wishlistPage === "true") location.reload();
        } catch (error) {
            notice(config, "저장 결과를 확인하지 못했습니다. 새로고침 후 찜 상태를 확인해 주세요.");
        } finally {
            pending.delete(productId);
            buttonsFor(productId).forEach(control => {
                control.disabled = false;
                control.removeAttribute("aria-busy");
            });
        }
    });

    // 뒤로 가기로 복원된 화면은 현재 로그인 회원의 DB 상태로 다시 조회
    window.addEventListener("pageshow", event => {
        if (event.persisted && document.querySelector("[data-wishlist-config]")) location.reload();
    });
})();
