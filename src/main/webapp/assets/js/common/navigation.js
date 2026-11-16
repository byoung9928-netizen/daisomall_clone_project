(function () {
    "use strict";

    function loadCategories(url) {
        var model = document.querySelector("#product-category-data");
        if (model) {
            var nodes = model.content.querySelectorAll("[data-id]");
            var categories = [];
            var parents = Object.create(null);
            for (var i = 0; i < nodes.length; i += 1) {
                var node = nodes[i];
                var parentId = node.dataset.parentId || null;
                categories.push({
                    id: node.dataset.id,
                    parentId: parentId,
                    name: node.textContent,
                    level: Number(node.dataset.level),
                    displayOrder: Number(node.dataset.displayOrder)
                });
                if (parentId !== null) {
                    parents[parentId] = true;
                }
            }
            categories.forEach(function (category) {
                category.isLeaf = !parents[category.id];
            });
            return categories;
        }
        if (window.Daiso && window.Daiso.repository) {
            return window.Daiso.repository.getVisibleCategories();
        }
        return fetch(url, { credentials: "same-origin" }).then(function (response) {
            if (!response.ok) {
                throw new Error("Category response: " + response.status);
            }
            return response.json();
        }).then(function (payload) {
            if (!Array.isArray(payload.categories) || payload.categories.length === 0) {
                throw new Error("Invalid category payload");
            }
            var ids = Object.create(null);
            payload.categories.forEach(function (item) {
                if (typeof item.id !== "string" || ids[item.id]
                        || typeof item.name !== "string" || [1, 2, 3].indexOf(item.level) === -1
                        || !Number.isInteger(item.displayOrder)) {
                    throw new Error("Invalid category node");
                }
                ids[item.id] = item;
            });
            payload.categories.forEach(function (item) {
                var parent = ids[item.parentId];
                if (item.level === 1 ? item.parentId !== null : !parent || parent.level !== item.level - 1) {
                    throw new Error("Invalid category parent");
                }
            });
            return payload.categories;
        });
    }

    function initializeCategories() {
        var toggle = document.querySelector("#category-toggle");
        var panel = document.querySelector("#category-panel");
        var lists = ["category-roots", "category-children", "category-leaves"]
            .map(function (id) { return document.getElementById(id); });
        var loadStatus = document.querySelector("#category-load-status");
        var retry = document.querySelector("#category-retry");
        var selection = document.querySelector("#common-category-selection");
        var close = document.querySelector("#category-close");
        if (!toggle || !panel || !loadStatus || !retry || !close || lists.some(function (list) { return !list; })) {
            return;
        }
        var selected = [null, null, null];
        var categories = [];
        var loading = false;

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
            var list = lists[level - 1];
            list.replaceChildren();
            var items = childrenOf(parentId);
            items.forEach(function (item) {
                var row = document.createElement("li");
                var button = document.createElement("button");
                button.type = "button";
                button.className = "category-item";
                button.dataset.id = item.id;
                var label = document.createElement("span");
                label.className = "category-item-label";
                label.textContent = item.name;
                button.append(label);
                button.setAttribute("aria-pressed", String(selected[level - 1] === item.id));
                if (childrenOf(item.id).length > 0) {
                    button.setAttribute("aria-controls", lists[level].id);
                    var arrow = document.createElement("span");
                    arrow.className = "category-item-arrow";
                    arrow.textContent = "›";
                    arrow.setAttribute("aria-hidden", "true");
                    button.append(arrow);
                }
                button.addEventListener("pointerenter", function (event) {
                    if (item.level < 3 && event.pointerType === "mouse"
                            && window.matchMedia("(any-hover: hover)").matches) {
                        browse(item);
                    }
                });
                button.addEventListener("click", function () {
                    browse(item);
                    confirmSelection(item);
                });
                row.append(button);
                list.append(row);
            });
            if (items.length === 0) {
                showEmpty(list, "하위 분류가 없습니다.");
            }
        }

        function showEmpty(list, message) {
            list.replaceChildren();
            var row = document.createElement("li");
            row.className = "category-empty";
            row.textContent = message;
            list.append(row);
        }

        function browse(item) {
            if (selected[item.level - 1] === item.id) {
                return;
            }
            selected[item.level - 1] = item.id;
            for (var index = item.level; index < selected.length; index += 1) {
                selected[index] = null;
            }
            // Keep the activated element mounted so keyboard focus remains stable.
            lists[item.level - 1].querySelectorAll("button").forEach(function (button) {
                button.setAttribute("aria-pressed", String(button.dataset.id === item.id));
            });
            if (item.level < 3) {
                renderLevel(item.level + 1, item.id);
            }
            if (item.level === 1) {
                showEmpty(lists[2], "중분류를 선택해 주세요.");
            }
        }

        function findCategory(id) {
            return categories.find(function (category) { return category.id === id; });
        }

        function confirmSelection(item) {
            var path = selected.filter(Boolean).map(function (id) {
                return findCategory(id).name;
            });
            if (selection) {
                selection.textContent = "선택한 카테고리: " + path.join(" › ")
                    + (childrenOf(item.id).length === 0 ? " · 상품 목록을 엽니다." : "");
            }
            panel.dispatchEvent(new CustomEvent("category:select", {
                bubbles: true,
                detail: { id: item.id, path: selected.filter(Boolean), isLeaf: item.isLeaf },
            }));
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
            var button = event.target.closest(".category-item");
            if (!button) {
                return;
            }
            var level = lists.findIndex(function (list) { return list.contains(button); });
            var buttons = Array.from(lists[level].querySelectorAll("button"));
            var index = buttons.indexOf(button);
            var target;
            if (event.key === "ArrowDown") {
                target = buttons[(index + 1) % buttons.length];
            } else if (event.key === "ArrowUp") {
                target = buttons[(index + buttons.length - 1) % buttons.length];
            } else if (event.key === "Home") {
                target = buttons[0];
            } else if (event.key === "End") {
                target = buttons[buttons.length - 1];
            } else if (event.key === "ArrowRight" && level < 2) {
                var item = findCategory(button.dataset.id);
                browse(item);
                target = lists[level + 1].querySelector("button");
            } else if (event.key === "ArrowLeft" && level > 0) {
                target = lists[level - 1].querySelector('[aria-pressed="true"]');
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
            var active = item.dataset.navigationKey === navigation.dataset.activeNavigation;
            item.classList.toggle("current", active);
            if (active) {
                item.setAttribute("aria-current", "page");
            } else {
                item.removeAttribute("aria-current");
            }
        });
    });

    var noticeTimer;
    function announceUnavailable(label) {
        var featureStatus = document.querySelector("#common-feature-status");
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
