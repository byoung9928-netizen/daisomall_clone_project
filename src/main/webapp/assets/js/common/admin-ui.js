(() => {
    "use strict";
    const app = window.Daiso;
    const { element } = app.ui;
    // 아래 함수들은 관리자 여러 화면에서 같은 표·입력·저장 처리를 재사용합니다.
    function field(form, label, name, type = "text", value = "") {
        const input = form.elements[name];
        input.value = value;
        return input;
    }
    function selectField(form, label, name, options, value) {
        const select = form.elements[name];
        select.replaceChildren();
        options.forEach(([id, text]) => { const option = element("option", "", text); option.value = id; select.append(option); });
        if (value !== undefined) select.value = value;
        return select;
    }
    function formFeedback(form, action, label = "저장") {
        const error = form.querySelector(".error-message");
        const status = form.querySelector(".form-status");
        error.textContent = ""; status.textContent = "";
        form.querySelector('[type="submit"]').textContent = label;
        form.onsubmit = event => {
            event.preventDefault(); error.textContent = ""; status.textContent = "";
            try { action(Object.fromEntries(new FormData(form))); status.textContent = "저장했습니다. 고객 화면의 새 조회에 반영됩니다."; }
            catch (cause) { error.textContent = cause.message; }
        };
        return status;
    }
    function table(root) { const wrapper = root.querySelector(".admin-table-scroll"); return { wrapper, body: wrapper.querySelector("tbody") }; }
    function searchBox(root) { const form = root.querySelector(".admin-search"); return { form, input: form.elements.query }; }
    function addRow(body, values) {
        const row = element("tr");
        values.forEach(value => { const cell = element("td"); if (value instanceof Node) cell.append(value); else cell.textContent = value; row.append(cell); });
        body.append(row);
    }
    function openEditor(editor, title) {
        editor.hidden = false;
        const hint = editor.querySelector("[data-editor-hint]"); if (hint) hint.remove();
        const heading = editor.querySelector("h2"); heading.hidden = false; heading.textContent = title;
        const form = editor.querySelector("form"); form.hidden = false;
        return form;
    }
    function requireAdmin(root, repository) {
        document.querySelectorAll("[data-admin-link]").forEach(link => { if (link.dataset.adminLink === root.dataset.adminPage) link.setAttribute("aria-current", "page"); });
        try { repository.requireAdmin(); return true; }
        catch (error) {
            [...root.children].slice(2).forEach(node => node.remove());
            root.append(element("p", "source-note", "관리자 체험 로그인이 필요합니다. 이 화면의 권한 검사는 실제 보안 인증이 아닙니다."), app.ui.link("관리자 로그인", "login.jsp", { next: "admin" }, "button primary"));
            return false;
        }
    }
    app.admin = { field, selectField, formFeedback, table, searchBox, addRow, openEditor, requireAdmin };
})();
