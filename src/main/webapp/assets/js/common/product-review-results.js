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
    const reactions = new Map();
    const report = panel.querySelector("[data-report-dialog]");
    const complete = panel.querySelector("[data-report-complete]");
    let reportOrigin;
    let reportSaving = false;

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
            await Promise.allSettled(reactions.values());
            if (pending !== request) return;
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

    const login = () => {
        if (window.confirm("로그인이 필요한 기능입니다. 로그인 화면으로 이동할까요?")) {
            location.assign(panel.dataset.feedbackLogin);
        }
    };

    const showError = (element, message) => {
        element.hidden = !message;
        element.textContent = message;
    };

    const feedbackMessage = code => {
        if (code === "ALREADY_REPORTED") return "이미 신고한 리뷰입니다.";
        if (code === "CSRF") return "보안 확인이 만료되었습니다. 페이지를 다시 열어 주세요.";
        if (code === "UNAVAILABLE_REVIEW") return "이 리뷰에는 더 이상 반응하거나 신고할 수 없습니다.";
        return "처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    };

    const sendFeedback = async (url, params) => {
        const response = await fetch(url, { method: "POST", body: new URLSearchParams(params) });
        const body = await response.text();
        if (!response.ok) throw new Error(body);
        return body;
    };

    const react = async button => {
        if (pending || button.disabled) return;
        const row = button.closest("[data-feedback-review]");
        const reviewId = row.dataset.feedbackReview;
        const reaction = button.dataset.reviewReaction;
        const key = reviewId + "/" + reaction;
        if (reactions.has(key)) return;
        const csrf = results.dataset.feedbackCsrf;
        if (!csrf) return login();

        const error = row.querySelector("[data-feedback-error]");
        showError(error, "");
        button.setAttribute("aria-busy", "true");
        const operation = sendFeedback(panel.dataset.reactionUrl, {
            csrf, reviewId, reaction, selected: String(button.getAttribute("aria-pressed") !== "true")
        });
        reactions.set(key, operation);
        try {
            const tally = JSON.parse(await operation);
            button.setAttribute("aria-pressed", String(tally.selected));
            const count = button.querySelector("[data-reaction-count]");
            count.textContent = tally.count || "";
            count.hidden = tally.count === 0;
        } catch (failure) {
            if (failure.message === "LOGIN") login();
            else showError(error, feedbackMessage(failure.message));
        } finally {
            reactions.delete(key);
            button.removeAttribute("aria-busy");
        }
    };

    const restoreReportFocus = () => {
        const target = reportOrigin?.isConnected ? reportOrigin : results;
        target.focus({ preventScroll: true });
    };

    if (report && complete) {
        const form = report.querySelector("[data-report-form]");
        const submit = form.querySelector("[type=submit]");
        const error = report.querySelector("[data-report-error]");
        report.querySelectorAll("[data-report-close]").forEach(button => {
            button.addEventListener("click", () => { if (!reportSaving) report.close(); });
        });
        report.addEventListener("cancel", event => { if (reportSaving) event.preventDefault(); });
        report.addEventListener("close", () => { if (!complete.open) restoreReportFocus(); });
        complete.addEventListener("close", restoreReportFocus);
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (reportSaving) return;
            reportSaving = true;
            submit.disabled = true;
            showError(error, "");
            try {
                const outcome = await sendFeedback(panel.dataset.reportUrl, {
                    csrf: results.dataset.feedbackCsrf,
                    reviewId: reportOrigin.closest("[data-feedback-review]").dataset.feedbackReview,
                    reason: new FormData(form).get("reason")
                });
                if (outcome !== "REPORTED") throw new Error();
                report.close();
                complete.showModal();
            } catch (failure) {
                if (failure.message === "LOGIN") {
                    report.close();
                    login();
                } else {
                    showError(error, feedbackMessage(failure.message));
                }
            } finally {
                reportSaving = false;
                submit.disabled = false;
            }
        });
        panel.addEventListener("click", event => {
            const button = event.target.closest("[data-review-report]");
            if (!button || pending) return;
            if (!results.dataset.feedbackCsrf) return login();
            reportOrigin = button;
            form.reset();
            showError(error, "");
            report.showModal();
            form.querySelector("input:checked").focus();
        });
    }

    panel.addEventListener("click", event => {
        const button = event.target.closest("[data-review-reaction]");
        if (button) react(button);
    });

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
