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
        <main id="main-content" class="container shop-page" data-page="login" tabindex="-1">
        <section class="member-panel">
            <h1 class="page-title">
                로그인
            </h1>
            <p class="source-note">
                학습용 Mock 회원 화면입니다. 실제 개인정보를 입력하지 마세요. 비밀번호는 검증·저장하지 않습니다.
            </p>
            <form id="loginForm" class="member-form" method="post">
                <label class="field">
                    이메일
                    <input name="email" type="email" required maxlength="120" autocomplete="email">
                </label>
                <label class="field">
                    임의 비밀번호 (4자 이상)
                    <input name="password" type="password" required minlength="4" maxlength="120" autocomplete="current-password">
                </label>
                <p class="error-message" role="alert">
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    로그인
                </button>
            </form>
            <a class="text-button" href="${pageContext.request.contextPath}/signup.jsp">
                회원가입
            </a>
            <p>
                체험 계정: demo@example.test / 임의 비밀번호 4자 이상
            </p>
            <button type="button" class="button secondary" data-demo-email="demo@example.test">
                체험 회원으로 로그인
            </button><button type="button" class="button secondary" data-demo-email="admin@example.test">
                체험 관리자로 로그인
            </button>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
    // 공통 defer 스크립트 실행 후, 이 페이지의 HTML에 Mock 데이터와 이벤트를 연결합니다.
    document.addEventListener("DOMContentLoaded", function () {
        "use strict";
        const app = window.Daiso;
        const root = document.getElementById("main-content");
        const { link, button } = app.ui;
        app.ready.then(function (repository) {
            const form = root.querySelector(".member-form");
            const error = form.querySelector(".error-message");
            const status = form.querySelector(".form-status");
            const member = repository.getSession();
            form.addEventListener("submit", function (event) {
                event.preventDefault();
                const values = Object.fromEntries(new FormData(form));
                error.textContent = "";
                try {
                    repository.login(values.email, values.password);
                    const next = new URLSearchParams(location.search).get("next");
                    location.href = app.url(next === "admin" ? "admin/" : next === "checkout" ? "checkout.jsp" : "mypage.jsp");
                } catch (cause) { error.textContent = cause.message; }
            });
            root.querySelectorAll("[data-demo-email]").forEach(function (control) {
                control.addEventListener("click", function () { form.elements.email.value = control.dataset.demoEmail; form.elements.password.value = "demo"; form.requestSubmit(); });
            });

        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
