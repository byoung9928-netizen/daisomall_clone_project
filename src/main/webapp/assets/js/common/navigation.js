(function () {
    "use strict";

    function loadCategories(url) {
        const model = document.querySelector("#product-category-data");
        if (model) {
            const nodes = model.content.querySelectorAll("[data-id]");
            const categories = [];
            for (let i = 0; i < nodes.length; i += 1) {
                const node = nodes[i];
                const parentId = node.dataset.parentId || null;
                categories.push({
                    id: node.dataset.id,
                    parentId: parentId,
                    name: node.textContent,
                    level: Number(node.dataset.level),
                    displayOrder: Number(node.dataset.displayOrder)
                });
            }
            return categories;
        }
        return fetch(url, { credentials: "same-origin" }).then(function (response) {
            if (!response.ok) {
                throw new Error("Category response: " + response.status);
            }
            return response.json();
        }).then(function (payload) {
            if (!Array.isArray(payload)) {
                throw new Error("Invalid category payload");
            }
            const ids = Object.create(null);
            payload.forEach(function (item) {
                if (typeof item.id !== "string" || ids[item.id]
                        || typeof item.name !== "string" || [1, 2, 3].indexOf(item.level) === -1
                        || !Number.isInteger(item.displayOrder)) {
                    throw new Error("Invalid category node");
                }
                ids[item.id] = item;
            });
            payload.forEach(function (item) {
                const parent = ids[item.parentId];
                if (item.level === 1 ? item.parentId !== null : !parent || parent.level !== item.level - 1) {
                    throw new Error("Invalid category parent");
                }
            });
            return payload;
        });
    }

    function initializeCategories() {
        const toggle = document.querySelector("#category-toggle");
        const panel = document.querySelector("#category-panel");
        const lists = ["category-roots", "category-children", "category-leaves"]
            .map(function (id) { return document.getElementById(id); });
        const loadStatus = document.querySelector("#category-load-status");
        const retry = document.querySelector("#category-retry");
        const catalogUrl = panel && panel.dataset.catalogUrl;
        const close = document.querySelector("#category-close");
        if (!toggle || !catalogUrl || !loadStatus || !retry || !close || lists.some(function (list) { return !list; })) {
            return;
        }
        const expanded = [null, null];
        let categories = [];
        let loading = false;

        function childrenOf(parentId) {
            return categories.filter(function (item) {
                return item.parentId === parentId;
            }).sort(function (left, right) {
                return left.displayOrder - right.displayOrder;
            });
        }

        function setOpen(open, restoreFocus) {
            panel.hidden = !open;
            toggle.setAttribute("aria-expanded", String(open));
            if (restoreFocus) {
                toggle.focus();
            }
            if (open && categories.length === 0 && !loading) {
                initialize();
            }
        }

        function renderLevel(level, parentId) {
            const list = lists[level - 1];
            list.replaceChildren();
            const items = childrenOf(parentId);
            items.forEach(function (item) {
                const row = document.createElement("li");
                row.className = "category-row";

                const link = document.createElement("a");
                link.className = "category-item";
                const url = new URL(catalogUrl, window.location.href);
                url.searchParams.set("category", item.id);
                link.href = url.href;
                link.textContent = item.name;
                row.append(link);

                if (level < lists.length && childrenOf(item.id).length > 0) {
                    const disclosure = document.createElement("button");
                    disclosure.type = "button";
                    disclosure.className = "category-disclosure";
                    disclosure.setAttribute("aria-label", item.name + " 하위 분류");
                    disclosure.setAttribute("aria-controls", lists[level].id);
                    disclosure.setAttribute("aria-expanded", "false");
                    disclosure.addEventListener("click", function () {
                        browse(level, expanded[level - 1] === item.id ? null : item.id);
                    });
                    row.append(disclosure);
                }
                if (level < lists.length) {
                    link.addEventListener("pointerenter", function (event) {
                        if (event.pointerType === "mouse" && window.matchMedia("(any-hover: hover)").matches) {
                            browse(level, row.querySelector(".category-disclosure") ? item.id : null);
                        }
                    });
                }
                row.dataset.id = item.id;
                list.append(row);
            });
            if (items.length === 0) {
                showEmpty(list, "하위 분류가 없습니다.");
            }
        }

        function showEmpty(list, message) {
            list.replaceChildren();
            const row = document.createElement("li");
            row.className = "category-empty";
            row.textContent = message;
            list.append(row);
        }

        function browse(level, id) {
            const index = level - 1;
            if (expanded[index] === id) {
                return;
            }
            expanded[index] = id;
            lists[index].querySelectorAll(".category-row").forEach(function (row) {
                const open = row.dataset.id === id;
                row.classList.toggle("is-expanded", open);
                const disclosure = row.querySelector(".category-disclosure");
                if (disclosure) {
                    disclosure.setAttribute("aria-expanded", String(open));
                }
            });
            if (id) {
                renderLevel(level + 1, id);
            } else {
                showEmpty(lists[level], level === 1 ? "대분류를 선택해 주세요." : "중분류를 선택해 주세요.");
            }
            if (level === 1) {
                expanded[1] = null;
                showEmpty(lists[2], "중분류를 선택해 주세요.");
            }
        }

        function initialize() {
            loading = true;
            loadStatus.hidden = false;
            loadStatus.textContent = "카테고리를 불러오는 중입니다.";
            retry.hidden = true;
            panel.setAttribute("aria-busy", "true");

            Promise.resolve().then(function () {
                return loadCategories(panel.dataset.source);
            }).then(function (loaded) {
                categories = loaded;
                renderLevel(1, null);
                showEmpty(lists[1], "대분류를 선택해 주세요.");
                showEmpty(lists[2], "중분류를 선택해 주세요.");
                loadStatus.textContent = "전체 카테고리를 불러왔습니다.";
                loadStatus.classList.add("visually-hidden");
            }).catch(function () {
                loadStatus.classList.remove("visually-hidden");
                loadStatus.textContent = "카테고리를 불러오지 못했습니다. 다시 시도해 주세요.";
                retry.hidden = false;
            }).then(function () {
                loading = false;
                panel.removeAttribute("aria-busy");
            });
        }

        toggle.addEventListener("click", function () { setOpen(panel.hidden); });
        close.addEventListener("click", function () { setOpen(false, true); });
        retry.addEventListener("click", initialize);
        document.addEventListener("click", function (event) {
            if (!panel.hidden && !panel.contains(event.target) && !toggle.contains(event.target)) {
                setOpen(false, panel.contains(document.activeElement));
            }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && !panel.hidden) {
                event.preventDefault();
                setOpen(false, true);
            }
        });
        panel.addEventListener("keydown", function (event) {
            const row = event.target.closest(".category-row");
            if (!row) {
                return;
            }
            const level = lists.indexOf(row.parentElement);
            const links = Array.from(lists[level].querySelectorAll(".category-item"));
            const index = links.indexOf(row.querySelector(".category-item"));
            let target;
            if (event.key === "ArrowDown") {
                target = links[(index + 1) % links.length];
            } else if (event.key === "ArrowUp") {
                target = links[(index + links.length - 1) % links.length];
            } else if (event.key === "Home") {
                target = links[0];
            } else if (event.key === "End") {
                target = links[links.length - 1];
            } else if (event.key === "ArrowRight" && row.querySelector(".category-disclosure")) {
                browse(level + 1, row.dataset.id);
                target = lists[level + 1].querySelector(".category-item");
            } else if (event.key === "ArrowLeft" && level > 0) {
                target = lists[level - 1].querySelector(".is-expanded .category-item");
            }
            if (target) {
                event.preventDefault();
                target.focus();
            }
        });

    }

    initializeCategories();

    document.querySelectorAll("[data-active-navigation]").forEach(function (navigation) {
        navigation.querySelectorAll("[data-navigation-key]").forEach(function (item) {
            const active = item.dataset.navigationKey === navigation.dataset.activeNavigation;
            item.classList.toggle("current", active);
            if (active) {
                item.setAttribute("aria-current", "page");
            } else {
                item.removeAttribute("aria-current");
            }
        });
    });

    let noticeTimer;
    function announceUnavailable(label) {
        const featureStatus = document.querySelector("#common-feature-status");
        if (featureStatus) {
            featureStatus.textContent = label.trim() + " 기능은 준비 중입니다. 현재는 공통 레이아웃 미리보기입니다.";
            clearTimeout(noticeTimer);
            noticeTimer = setTimeout(function () {
                featureStatus.textContent = "";
            }, 5000);
        }
    }

    document.querySelectorAll("[data-unavailable]").forEach(function (button) {
        button.addEventListener("click", function () {
            if (button.hasAttribute("data-unavailable")) {
                announceUnavailable(button.textContent);
            }
        });
    });
})();
