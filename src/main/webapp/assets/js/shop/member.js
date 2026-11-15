(() => {
    "use strict";

    const app = window.Daiso;
    const { link, button } = app.ui;
    const root = document.querySelector("[data-page]");

    function field(form, label, name, type = "text", value = "") {
        const wrapper = app.ui.view("member-field", label);
        const input = app.ui.view("member-input");
        input.name = name;
        input.type = type;
        input.value = value;
        input.maxLength = name === "name" ? 50 : 120;
        input.autocomplete = { email: "email", name: "name", phone: "tel", password: "current-password" }[name] || "off";
        if (name === "password") {
            input.minLength = 4;
        }
        wrapper.append(input);
        form.append(wrapper);
        return input;
    }

    app.ui.field = field;

    app.ready.then((repository) => {
        function refreshHeader() {
            const member = repository.getSession();
            const login = document.querySelector('[data-member-route="login"]');
            if (login) {
                const label = login.querySelector(".account-label");
                label.textContent = member ? "로그아웃" : "로그인·회원";
                login.title = member ? member.name + " · 로그아웃" : "로그인·회원";
            }
        }
        document.querySelectorAll("[data-member-route]").forEach((control) => {
            control.removeAttribute("data-unavailable");
            control.addEventListener("click", () => {
                if (control.dataset.memberRoute === "login" && repository.getSession()) {
                    repository.logout();
                    location.href = app.url("");
                } else {
                    location.href = app.url(control.dataset.memberRoute + ".jsp");
                }
            });
        });
        window.addEventListener("mock:session", refreshHeader);
        refreshHeader();
        if (!root || !["login", "signup", "mypage", "profile"].includes(root.dataset.page)) {
            return;
        }
        root.replaceChildren();
        const page = root.dataset.page;
        const member = repository.getSession();
        const panel = app.ui.view("member-member-panel");
        root.append(panel);
        const titles = { login: "로그인", signup: "회원가입", mypage: "마이페이지", profile: "회원정보" };
        panel.append(app.ui.view("member-page-title", titles[page]));
        if (page === "mypage") {
            if (!member) {
                panel.append(app.ui.view("member-p"), link("로그인", "login.jsp", {}, "button primary"));
                return;
            }
            panel.append(app.ui.view("member-member-greeting", member.name + "님, 반갑습니다."), app.ui.view("member-p-2", member.email));
            const actions = app.ui.view("member-action-stack");
            actions.append(link("회원정보 수정", "profile.jsp", {}, "button secondary"));
            const orders = button("주문 내역", () => {
                if (app.features.orders) {
                    location.href = app.url("orders.jsp");
                } else {
                    app.ui.notice("주문 내역은 v0.6에서 연결됩니다.");
                }
            }, "button secondary");
            actions.append(orders, button("로그아웃", () => {
                repository.logout();
                location.href = app.url("login.jsp");
            }));
            if (member.role === "ADMIN") {
                actions.append(link("관리자 화면", "admin/", {}, "button secondary"));
            }
            panel.append(actions);
            return;
        }
        if (page === "profile" && !member) {
            panel.append(app.ui.view("member-p-3"), link("로그인", "login.jsp", {}, "button"));
            return;
        }
        panel.append(app.ui.view("member-source-note"));
        const form = app.ui.view("member-form-" + page);
        const error = form.querySelector(".error-message");
        const status = form.querySelector(".form-status");
        if (page === "profile") {
            form.querySelector("[data-member-email]").textContent = "이메일: " + member.email;
        }
        if (page !== "login" && member) {
            form.elements.name.value = member.name;
            form.elements.phone.value = member.phone;
        }
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            const values = Object.fromEntries(new FormData(form));
            error.textContent = "";
            try {
                if (page === "profile") {
                    repository.saveProfile(values);
                    status.textContent = "회원정보를 저장했습니다.";
                } else {
                    if (page === "signup") {
                        repository.register(values);
                    }
                    repository.login(values.email, values.password);
                    const next = new URLSearchParams(location.search).get("next");
                    location.href = app.url(next === "admin" && app.features.admin ? "admin/" : next === "checkout" && app.features.orders ? "checkout.jsp" : "mypage.jsp");
                }
            } catch (cause) {
                error.textContent = cause.message;
            }
        });
        panel.append(form);
        if (page === "login") {
            panel.append(link("회원가입", "signup.jsp", {}, "text-button"));
            panel.append(app.ui.view("member-p-5"));
            panel.append(button("체험 회원으로 로그인", () => {
                form.elements.email.value = "demo@example.test";
                form.elements.password.value = "demo";
                form.requestSubmit();
            }, "button secondary"));
            panel.append(button("체험 관리자로 로그인", () => {
                form.elements.email.value = "admin@example.test";
                form.elements.password.value = "demo";
                form.requestSubmit();
            }, "button secondary"));
        }
    }).catch((error) => {
        if (root) {
            app.ui.error(root, error);
        }
    });
})();
