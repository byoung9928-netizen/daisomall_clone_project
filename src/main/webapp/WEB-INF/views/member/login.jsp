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
                가입한 이메일과 비밀번호를 입력해 로그인합니다.
            </p>
            <form id="loginForm" class="member-form" method="post" action="${pageContext.request.contextPath}/app/member/login">
                <label class="field">
                    이메일
                    <input name="email" type="email" required maxlength="120" autocomplete="email">
                </label>
                <label class="field">
                    비밀번호 (4자 이상)
                    <input name="password" type="password" required minlength="4" maxlength="120" autocomplete="current-password">
                </label>
                <p class="error-message" role="alert">${loginError}
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    로그인
                </button>
            </form>
            <a class="text-button" href="${pageContext.request.contextPath}/app/member/signup">
                회원가입
            </a>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
