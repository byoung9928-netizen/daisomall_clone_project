(() => {
    "use strict";

    const ui = window.Daiso.ui;
    if (ui.showToast) return;

    const duration = 2500;
    const limit = 4;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const stack = document.createElement("div");
    stack.className = "shop-toast-stack";
    const announcements = document.createElement("div");
    announcements.className = "visually-hidden";
    announcements.setAttribute("aria-live", "polite");
    announcements.setAttribute("aria-relevant", "additions");
    document.body.append(stack, announcements);

    const visible = [];
    const queued = [];

    function layout(change) {
        const positions = visible.map(entry => [entry, entry.row.getBoundingClientRect().top]);
        change();
        for (const [entry, top] of positions) {
            if (!entry.row.isConnected) continue;
            entry.slide?.cancel();
            const distance = top - entry.row.getBoundingClientRect().top;
            if (distance && !reducedMotion.matches) {
                entry.slide = entry.row.animate(
                    [{ transform: "translateY(" + distance + "px)" }, { transform: "translateY(0)" }],
                    { duration: 170, easing: "ease-out" }
                );
            }
        }
    }

    function syncTimer(entry) {
        const paused = entry.hovered || entry.focused || document.hidden;
        if (paused && entry.timer !== null) {
            clearTimeout(entry.timer);
            entry.timer = null;
            entry.remaining = Math.max(0, entry.deadline - performance.now());
        } else if (!paused && entry.timer === null && !entry.leaving) {
            entry.deadline = performance.now() + entry.remaining;
            entry.timer = setTimeout(() => dismiss(entry), entry.remaining);
        }
    }

    function dismiss(entry) {
        if (entry.leaving) return;
        entry.leaving = true;
        clearTimeout(entry.timer);
        entry.timer = null;
        const finish = () => {
            const hadFocus = entry.row.contains(document.activeElement);
            layout(() => {
                entry.slide?.cancel();
                entry.row.remove();
                entry.announcement.remove();
                visible.splice(visible.indexOf(entry), 1);
            });
            if (hadFocus) {
                const link = stack.querySelector(".shop-toast a");
                (link || entry.origin)?.focus({ preventScroll: true });
            }
            flush();
        };
        if (reducedMotion.matches) {
            finish();
        } else {
            const styles = getComputedStyle(entry.toast);
            const start = { opacity: styles.opacity, transform: styles.transform };
            entry.entrance?.cancel();
            entry.toast.animate([start, { opacity: 0, transform: "translateY(8px)" }],
                { duration: 220, easing: "ease-in", fill: "forwards" }).finished.then(finish);
        }
    }

    function flush() {
        while (queued.length && visible.length < limit) {
            const options = queued.shift();
            const row = document.createElement("div");
            const toast = document.createElement("div");
            toast.className = "shop-toast";
            const message = document.createElement("span");
            message.textContent = options.message;
            toast.append(message);
            if (options.actionText && options.actionUrl) {
                const link = document.createElement("a");
                link.href = options.actionUrl;
                link.textContent = options.actionText;
                toast.append(link);
            }
            row.append(toast);
            const announcement = document.createElement("span");
            announcement.textContent = options.message;
            const entry = {
                row, toast, announcement, origin: options.origin,
                remaining: duration, timer: null, hovered: false, focused: false, leaving: false
            };
            row.addEventListener("pointerenter", event => {
                if (event.pointerType !== "mouse") return;
                entry.hovered = true;
                syncTimer(entry);
            });
            row.addEventListener("pointerleave", event => {
                if (event.pointerType !== "mouse") return;
                entry.hovered = false;
                syncTimer(entry);
            });
            row.addEventListener("focusin", () => {
                entry.focused = true;
                syncTimer(entry);
            });
            row.addEventListener("focusout", event => {
                entry.focused = row.contains(event.relatedTarget);
                syncTimer(entry);
            });

            layout(() => stack.append(row));
            visible.push(entry);
            announcements.append(announcement);
            if (!reducedMotion.matches) {
                entry.entrance = toast.animate(
                    [{ opacity: 0, transform: "translateY(10px)" }, { opacity: 1, transform: "translateY(0)" }],
                    { duration: 180, easing: "ease-out" }
                );
            }
            syncTimer(entry);
        }
        if (queued.length && !visible.some(entry => entry.leaving)) dismiss(visible[0]);
    }

    document.addEventListener("visibilitychange", () => visible.forEach(syncTimer));
    ui.showToast = ({ message, actionText, actionUrl, origin = document.activeElement }) => {
        queued.push({ message, actionText, actionUrl, origin });
        flush();
    };
})();
