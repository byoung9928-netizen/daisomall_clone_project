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

    <main id="main-content" class="container shop-page" tabindex="-1">
        <section class="member-panel">
            <h1 class="page-title">아이디 찾기</h1>

            <p class="source-note">
                가입할 때 입력한 이름과 전화번호를 입력해 주세요.
            </p>

            <form class="member-form" method="post"
                  action="${pageContext.request.contextPath}/app/member/find-id">
                <label class="field">
                    이름
                    <input name="name" type="text" required maxlength="50">
                </label>

                <label class="field">
                    전화번호
                    <input name="phone" type="tel" required maxlength="30">
                </label>

                <p class="error-message" role="alert">${findIdError}</p>
                <p class="form-status" role="status">${findIdResult}</p>

                <button type="submit" class="button primary">아이디 찾기</button>
            </form>

            <a class="text-button"
               href="${pageContext.request.contextPath}/app/member/login">
                로그인으로 돌아가기
            </a>
        </section>
    </main>

    <%@ include file="../common/footer.jspf" %>
</body>
</html>