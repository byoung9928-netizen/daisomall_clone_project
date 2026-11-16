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
        <main id="main-content" class="container shop-page" data-page="signup" tabindex="-1">
        <section class="member-panel">
            <h1 class="page-title">
                회원가입
            </h1>
            <p class="source-note">
                학습용 회원가입 화면입니다. 입력한 정보는 프로젝트 데이터베이스에 저장됩니다.
            </p>
            <form id="signupForm" class="member-form" method="post" action="${pageContext.request.contextPath}/app/member/signup">
                <label class="field">
                    이메일
                    <input name="email" type="email" required maxlength="120" autocomplete="email">
                </label>
                <label class="field">
                    임의 비밀번호 (4자 이상)
                    <input name="password" type="password" required minlength="4" maxlength="120" autocomplete="new-password">
                </label>
                <label class="field">
                    이름
                    <input name="name" type="text" required maxlength="50" autocomplete="name">
                </label>
                <label class="field">
                    전화번호 (예: 010-0000-0000)
                    <input name="phone" type="tel" required maxlength="120" autocomplete="tel">
                </label>
                <p class="error-message" role="alert">${signupError}
                </p>
                <p class="form-status" role="status">
                </p>
                <button type="submit" class="button primary">
                    회원가입
                </button>
            </form>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
