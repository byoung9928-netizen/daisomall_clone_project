<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>

</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
        <main id="main-content" class="container shop-page" data-page="profile" tabindex="-1">
        <section class="member-panel">
            <h1 class="page-title">
                회원정보
            </h1>
            <p class="source-note">
                학습용 Mock 회원 화면입니다. 실제 개인정보를 입력하지 마세요. 비밀번호는 검증·저장하지 않습니다.
            </p>
            <form id="profileForm" class="member-form" method="post">
                <p data-member-email>
                </p>
                <label class="field">
                    이름
                    <input name="name" type="text" required maxlength="50" autocomplete="name">
                </label>
                <label class="field">
                    전화번호 (예: 010-0000-0000)
                    <input name="phone" type="tel" required maxlength="120" autocomplete="tel">
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    회원정보 저장
                </button>
            </form>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { element, link, button } = app.ui;
        app.ready.then(function (repository) {
            const form = root.querySelector(".member-form");
            const error = form.querySelector(".error-message");
            const status = form.querySelector(".form-status");
            const member = repository.getSession();
            if (!member) { root.querySelector(".source-note").remove(); form.remove(); root.querySelector("section").append(element("p", "", "Mock 로그인이 필요합니다."), link("로그인", "login.jsp", {}, "button")); return; }
            form.querySelector("[data-member-email]").textContent = "이메일: " + member.email;
            if (member) { form.elements.name.value = member.name; form.elements.phone.value = member.phone; }
            form.addEventListener("submit", function (event) {
                event.preventDefault();
                const values = Object.fromEntries(new FormData(form));
                error.textContent = "";
                try {
                    repository.saveProfile(values);
                    status.textContent = "회원정보를 저장했습니다.";
                } catch (cause) { error.textContent = cause.message; }
            });

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
