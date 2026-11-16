(() => {
    "use strict";

    const app = window.Daiso;
    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function link(label, file, params, className) {
        const node = element("a", className, label);
        node.href = app.url(file, params);
        return node;
    }

    function button(label, action, className = "button") {
        const node = element("button", className, label);
        node.type = "button";
        node.addEventListener("click", action);
        return node;
    }

    let noticeTimer;
    function notice(message) {
        const output = document.querySelector("#common-feature-status");
        if (output) {
            output.textContent = message;
            clearTimeout(noticeTimer);
            noticeTimer = setTimeout(() => {
                output.textContent = "";
            }, 5000);
        }
    }

    function money(value) {
        return new Intl.NumberFormat("ko-KR").format(value) + "원";
    }

    function empty(root, message) {
        root.append(element("p", "empty-state", message));
    }

    function error(root, cause) {
        root.replaceChildren(element("h1", "page-title", "화면을 표시하지 못했습니다"), element("p", "error-message", cause.message));
        root.append(button("다시 시도", () => location.reload()), button("Mock 데이터 초기화", () => {
            if (window.confirm("이 브라우저의 Mock 변경과 주문을 모두 초기화할까요?")) {
                if (app.repository) {
                    app.repository.reset();
                } else {
                    localStorage.removeItem(app.storageKey);
                    sessionStorage.removeItem("daisomall.mock.session");
                }
                location.reload();
            }
        }, "button secondary"));
    }

    app.ui = { element, link, button, notice, money, empty, error };
})();
