(() => {
    const panel = document.querySelector("[data-review-endpoint]");
    if (!panel) return;
    let results = panel.querySelector("[data-review-results]");
    if (!results) return;

    const product = results.dataset.reviewProduct;
    const sortLinks = Array.from(panel.querySelectorAll("[data-review-sort-link]"));
    const allowedSorts = new Set(sortLinks.map(link => link.dataset.reviewSortLink));
    const status = panel.querySelector("[data-review-status]");
    let pending = null;

    const reviewState = url => {
        const sort = url.searchParams.get("reviewSort");
        const page = Number(url.searchParams.get("reviewPage"));
        return {
            sort: allowedSorts.has(sort) ? sort : "newest",
            page: Number.isInteger(page) && page > 0 && page <= 2147483647 ? page : 1
        };
    };

    const load = async (sort, page, scrollToResults, updateHistory) => {
        pending?.abort();
        pending = null;
        if (results.dataset.reviewSort === sort && Number(results.dataset.reviewPage) === page) {
            results.removeAttribute("aria-busy");
            status.textContent = "";
            return;
        }

        const request = new AbortController();
        pending = request;
        results.setAttribute("aria-busy", "true");
        status.textContent = "리뷰를 불러오는 중입니다.";
        const endpoint = new URL(panel.dataset.reviewEndpoint, window.location.origin);
        endpoint.searchParams.set("id", product);
        endpoint.searchParams.set("reviewSort", sort);
        endpoint.searchParams.set("reviewPage", String(page));

        try {
            const response = await fetch(endpoint, { signal: request.signal, headers: { Accept: "text/html" } });
            if (!response.ok) throw new Error("Review results unavailable");
            const html = new DOMParser().parseFromString(await response.text(), "text/html");
            if (pending !== request) return;
            const next = html.querySelector("[data-review-results]");
            if (!next || next.dataset.reviewProduct !== product || next.dataset.reviewSort !== sort) {
                throw new Error("Unexpected review results");
            }

            results.replaceWith(next);
            results = next;
            sortLinks.forEach(link => {
                if (link.dataset.reviewSortLink === sort) link.setAttribute("aria-current", "true");
                else link.removeAttribute("aria-current");
            });
            panel.querySelectorAll(".pd-reviews__gallery-nav a").forEach(link => {
                const target = new URL(link.href);
                target.searchParams.set("reviewSort", sort);
                target.searchParams.set("reviewPage", next.dataset.reviewPage);
                link.href = target.href;
            });
            if (updateHistory) {
                const current = new URL(window.location.href);
                current.searchParams.set("reviewSort", sort);
                current.searchParams.set("reviewPage", next.dataset.reviewPage);
                history.pushState(null, "", current);
            }
            if (scrollToResults) {
                next.focus({ preventScroll: true });
                next.scrollIntoView({ block: "start" });
            }
            status.textContent = "";
        } catch (error) {
            if (pending === request && error.name !== "AbortError") {
                status.textContent = "리뷰를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        } finally {
            if (pending === request) {
                pending = null;
                results.removeAttribute("aria-busy");
            }
        }
    };

    panel.addEventListener("click", event => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        const link = event.target.closest("[data-review-sort-link], [data-review-page-link]");
        if (!link) return;

        event.preventDefault();
        const pageLink = link.hasAttribute("data-review-page-link");
        const sort = pageLink ? results.dataset.reviewSort : link.dataset.reviewSortLink;
        const page = pageLink ? Number(link.dataset.reviewPageLink) : 1;
        load(sort, page, pageLink, true);
    });

    window.addEventListener("popstate", () => {
        const url = new URL(window.location.href);
        if (url.searchParams.get("id") !== product) return;
        const state = reviewState(url);
        load(state.sort, state.page, false, false);
    });
})();
