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
                <div class="member-options">
   				 <label class="check-field">
        			<input type="checkbox" name="autoLogin" id="autoLogin">
        		<span>자동 로그인</span>
    			</label>

   				 <label class="check-field">
       				 <input type="checkbox" id="saveEmail">
       			 <span>아이디 저장</span>
   				 </label>
			</div>
                <p class="error-message" role="alert">${loginError}
                </p>
                <p class="form-status" role="status">${loginNotice}
                </p>
                <button type="submit" class="button primary">
                    로그인
                </button>
            </form>            
         		<div class="member-links">
    				<a class="text-button"
       					href="${pageContext.request.contextPath}/app/member/signup">
       				 <strong>회원가입</strong>
    				</a>

    			<span class="link-divider">|</span>

    				<a class="text-button"
       					href="${pageContext.request.contextPath}/app/member/find-id">
        				아이디 찾기
    				</a>
    			<span class="link-divider">|</span>
				
				<a class="text-button"
  					 href="${pageContext.request.contextPath}/app/member/find-password">
    			비밀번호 찾기
				</a>
				</div>
				<div class="social-login">
    <p>SNS 간편 로그인</p>

    <div class="social-login-buttons">
        <button type="button"
                id="kakao-login-btn"
                class="social-login-button kakao"
                aria-label="카카오 로그인"
                title="카카오 로그인">
            <svg viewBox="0 0 48 48" aria-hidden="true">
                <path d="M24 7C12.4 7 3 14.5 3 23.7c0 5.8 3.9 10.9 9.8 13.8l-2.4 7.2c-.2.5.4.9.9.6l8.8-5.1c1.3.2 2.6.3 3.9.3 11.6 0 21-7.5 21-16.8S35.6 7 24 7z"
                      fill="#191919"/>
            </svg>
        </button>
    </div>
</div>
				<button type="button" id="adminDemoLogin"
        class="button secondary"
        style="width: 100%; margin-top: 20px;">
    관리자 체험 로그인 (Mock)
</button>

<p class="source-note">
    관리자 화면 확인용 체험 로그인입니다.
</p>

<p id="adminDemoError" class="error-message" role="alert"></p>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
<script>
document.addEventListener("DOMContentLoaded", function () {
    var form = document.getElementById("loginForm");
    var emailInput = form.elements.email;
    var saveEmail = document.getElementById("saveEmail");
    var storageKey = "daisomall.savedLoginEmail";

    var savedEmail = localStorage.getItem(storageKey);

    if (savedEmail) {
        emailInput.value = savedEmail;
        saveEmail.checked = true;
    }

    form.addEventListener("submit", function () {
        if (saveEmail.checked) {
            localStorage.setItem(storageKey, emailInput.value.trim());
        } else {
            localStorage.removeItem(storageKey);
        }
    });
});
</script>
<script>
document.addEventListener("DOMContentLoaded", function () {
    var button = document.getElementById("adminDemoLogin");
    var errorMessage = document.getElementById("adminDemoError");

    window.Daiso.ready.then(function (repository) {
        button.addEventListener("click", function () {
            errorMessage.textContent = "";

            try {
                repository.login("admin@example.test", "demo1234");
                location.href = window.Daiso.url(
                    "admin/",
                    { view: "dashboard" }
                );
            } catch (error) {
                errorMessage.textContent = error.message;
            }
        });
    });
});
</script>
<script src="https://t1.kakaocdn.net/kakao_js_sdk/2.8.1/kakao.min.js"
        crossorigin="anonymous"></script>

<script>
    Kakao.init('4283b243159f2b259f575f88bcf68632');

    document.getElementById("kakao-login-btn").addEventListener("click", function () {
    	Kakao.Auth.authorize({
    	    redirectUri: "http://localhost:8080/daisomall/app/member/oauth/kakao/callback",
    	    scope: "account_email"
    	});
    });
</script>
</body>
</html>