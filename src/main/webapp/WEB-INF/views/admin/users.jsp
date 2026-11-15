<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin.css">
<script src="${pageContext.request.contextPath}/assets/js/common/admin-ui.js" defer></script>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>
    <div class="admin-layout">
    <%@ include file="../common/admin-navigation.jspf" %>
        <main id="admin-content" class="admin-content" data-admin-page="users" tabindex="-1">
        <p class="admin-eyebrow">
            MOCK COMMERCE OPERATIONS
        </p>
        <h1 class="page-title">
            사용자 관리
        </h1>
        <form class="admin-search">
            <label class="field">
                <span>
                    회원 이름 또는 이메일 검색
                </span>
                <input name="query" type="text" maxlength="120" autocomplete="off">
            </label>
            <button class="button" type="submit">
                검색
            </button>
        </form>
        <section class="admin-panel" hidden>
            <h2 hidden>
            </h2>
            <form class="admin-editor-form" hidden>
                <label class="field">
                    회원 상태
                    <select name="status">
                    </select>
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    회원 상태 저장
                </button>
            </form>
        </section>
        <div class="admin-table-scroll" tabindex="0" role="region" aria-label="사용자 관리 표 (가로 스크롤 가능)">
            <table class="admin-table">
                <caption class="visually-hidden">
                    사용자 관리
                </caption>
                <thead>
                    <tr>
                        <th scope="col">
                            회원 ID
                        </th>
                        <th scope="col">
                            이름
                        </th>
                        <th scope="col">
                            이메일
                        </th>
                        <th scope="col">
                            유형
                        </th>
                        <th scope="col">
                            상태
                        </th>
                        <th scope="col">
                            관리
                        </th>
                    </tr>
                </thead>
                <tbody>
                </tbody>
            </table>
        </div>
    </main>
    </div>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("admin-content");
        const { link, button } = app.ui;
        const { field, selectField, formFeedback, addRow, openEditor } = app.admin;
        app.ready.then(function (repository) {
            if (!app.admin.requireAdmin(root, repository)) return;
            const section = "users";

        const { form, input } = app.admin.searchBox(root);
        const userTable = app.admin.table(root);
        const editor = root.querySelector(".admin-panel");
        function edit(member) {
            const editForm = openEditor(editor, member.name + " 상태 관리");
            selectField(editForm, "회원 상태", "status", [["ACTIVE", "활성"], ["SUSPENDED", "이용 중지"]], member.status);
            formFeedback(editForm, (values) => {
                repository.updateMemberStatus(member.id, values.status);
                render();
            }, "회원 상태 저장");
            editor.scrollIntoView({ block: "start" });
        }
        function render() {
            const query = input.value.trim().toLowerCase();
            userTable.body.replaceChildren();
            repository.getMembers().filter((member) => (member.name + " " + member.email).toLowerCase().includes(query)).forEach((member) => {
                const editButton = button("상태 수정", () => edit(member), "text-button");
                editButton.disabled = member.id === repository.getSession().id;
                addRow(userTable.body, [member.id, member.name, member.email, member.role, member.status, editButton]);
            });
        }
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            render();
        });
        render();

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
