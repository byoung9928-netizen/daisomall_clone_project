(() => {
    const section = document.querySelector("[data-home-ranking]");
    if (!section) return;
    const content = section.querySelector("[data-ranking-content]");
    const status = section.querySelector("[data-ranking-status]");
    const retry = section.querySelector("[data-ranking-retry]");
    let requestedTab = null;
    let pending = null;

    async function load(tab) {
        pending?.abort();
        const controller = new AbortController();
        pending = controller;
        requestedTab = tab;
        const panel = content.querySelector("[role=tabpanel]");
        const target = panel || content;
        target.setAttribute("aria-busy", "true");
        retry.hidden = true;
        status.textContent = "카테고리 랭킹을 불러오는 중입니다.";

        const url = new URL(section.dataset.source, location.href);
        if (tab) url.searchParams.set("category", tab.dataset.rankCategory);
        try {
            const response = await fetch(url, { credentials: "same-origin", signal: controller.signal });
            if (!response.ok) throw new Error("Ranking response " + response.status);
            const doc = new DOMParser().parseFromString(await response.text(), "text/html");
            if (pending !== controller) return;
            const result = doc.querySelector(tab ? "[data-ranking-products]" : "[data-ranking-content]");
            if (!result) throw new Error("Ranking markup missing");

            window.Daiso.ui.disposeProductCarousels(target);
            if (tab) target.replaceChildren(result);
            else target.replaceChildren(...result.childNodes);
            window.Daiso.ui.initProductCarousels(target);
            if (tab) {
                content.querySelectorAll("[role=tab]").forEach(button => {
                    button.setAttribute("aria-selected", String(button === tab));
                    button.tabIndex = button === tab ? 0 : -1;
                });
                panel.setAttribute("aria-labelledby", tab.id);
                status.textContent = tab.textContent.trim() + " 랭킹을 표시했습니다.";
            } else {
                status.textContent = "";
            }
        } catch (error) {
            if (pending !== controller) return;
            status.textContent = "카테고리 랭킹을 불러오지 못했습니다.";
            retry.hidden = false;
        } finally {
            if (pending === controller) {
                target.removeAttribute("aria-busy");
                pending = null;
            }
        }
    }

    section.addEventListener("click", event => {
        const tab = event.target.closest("[data-rank-category]");
        if (tab) void load(tab);
    });
    retry.addEventListener("click", () => void load(requestedTab));
    content.addEventListener("keydown", event => {
        const tab = event.target.closest("[role=tab]");
        if (!tab) return;
        const tabs = Array.from(tab.parentElement.querySelectorAll("[role=tab]"));
        const current = tabs.indexOf(tab);
        let index;
        switch (event.key) {
            case "ArrowRight": index = (current + 1) % tabs.length; break;
            case "ArrowLeft": index = (current + tabs.length - 1) % tabs.length; break;
            case "Home": index = 0; break;
            case "End": index = tabs.length - 1; break;
            default: return;
        }
        event.preventDefault();
        tabs.forEach((button, slot) => { button.tabIndex = slot === index ? 0 : -1; });
        tabs[index].focus();
    });
    void load(null);
})();
