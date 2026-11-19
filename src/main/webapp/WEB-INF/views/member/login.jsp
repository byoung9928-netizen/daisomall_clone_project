<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<%@ include file="../common/head.jspf"%>

</head>
<body>
	<a class="skip-link" href="#main-content">본문 바로가기</a>
	<%@ include file="../common/header.jspf"%>
	<% if ("success".equals(request.getParameter("signup"))) { %>
<script>
document.addEventListener("DOMContentLoaded", function () {
    var toast = document.getElementById("common-feature-status");

    if (!toast) {
        return;
    }

    toast.textContent = "회원가입되었습니다.";
    toast.classList.add("logout-toast");

    window.setTimeout(function () {
        toast.textContent = "";
        toast.classList.remove("logout-toast");
    }, 3000);

    window.history.replaceState(null, "", location.pathname);
});
</script>
<% } %>
	<%@ include file="../common/navigation.jspf"%>
	<main id="main-content" class="container shop-page" data-page="login"
		tabindex="-1">
		<section class="member-panel">
			<h1 class="page-title">로그인</h1>
			<form id="loginForm" class="member-form" method="post"
				action="${pageContext.request.contextPath}/app/member/login">
				<label class="field"> 아이디 <input name="loginId" type="text"
					required maxlength="120" autocomplete="username"
					placeholder="아이디를 입력해 주세요">
				</label>
				<label class="field"> 비밀번호 <input name="password" placeholder="비밀번호를 입력해 주세요"
					type="password" required maxlength="120"
					autocomplete="current-password">
				</label>
				<div class="member-options">
					<label class="check-field"> <input type="checkbox"
						name="autoLogin" id="autoLogin"> <span>자동 로그인</span>
					</label> <label class="check-field"> <input type="checkbox"
						id="saveEmail"> <span>아이디 저장</span>
					</label>
				</div>
				<p class="error-message" role="alert">${loginError}</p>
				<p class="form-status" role="status">${loginNotice}</p>
				<button type="submit" class="button primary">로그인</button>
			</form>
			<div class="member-links">
				<a class="text-button"
					href="${pageContext.request.contextPath}/app/member/signup"> <strong>회원가입</strong>
				</a> <span class="link-divider">|</span> <a class="text-button"
					href="${pageContext.request.contextPath}/app/member/find-id">
					아이디 찾기 </a> <span class="link-divider">|</span> <a class="text-button"
					href="${pageContext.request.contextPath}/app/member/find-password">
					비밀번호 찾기 </a>
			</div>
			<div class="social-login">
				<p>SNS 간편 로그인</p>

				<div class="social-login-buttons">
					<button type="button" id="kakao-login-btn"
						class="social-login-button kakao" aria-label="카카오 로그인"
						title="카카오 로그인">
						<svg viewBox="0 0 48 48" aria-hidden="true">
                <path
								d="M24 7C12.4 7 3 14.5 3 23.7c0 5.8 3.9 10.9 9.8 13.8l-2.4 7.2c-.2.5.4.9.9.6l8.8-5.1c1.3.2 2.6.3 3.9.3 11.6 0 21-7.5 21-16.8S35.6 7 24 7z"
								fill="#191919" />
            </svg>
					</button>
					<form action="https://nid.naver.com/oauth2.0/authorize"
						method="get">
						<input type="hidden" name="response_type" value="code"> <input
							type="hidden" name="client_id" value="${naverClientId}">
						<input type="hidden" name="redirect_uri"
							value="http://localhost:8080/daisomall/app/member/oauth/naver/callback">
						<input type="hidden" name="state" value="${naverOAuthState}">

						<button type="submit" class="social-login-button"
							aria-label="네이버 로그인" title="네이버 로그인"
							style="background: #03c75a; color: white; font-size: 30px; font-weight: bold;">
							N</button>
					</form>
					<button type="button"
        class="gsi-material-button"
        aria-label="Google 로그인"
        onclick="window.location.href='${pageContext.request.contextPath}/app/member/oauth/google/start'">
						<div class="gsi-material-button-state"></div>
						<div class="gsi-material-button-content-wrapper">
							<div class="gsi-material-button-icon">
								<svg version="1.1" xmlns="http://www.w3.org/2000/svg"
									viewBox="0 0 48 48" xmlns:xlink="http://www.w3.org/1999/xlink"
									style="display: block;">
        <path fill="#EA4335"
										d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"></path>
        <path fill="#4285F4"
										d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"></path>
        <path fill="#FBBC05"
										d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"></path>
        <path fill="#34A853"
										d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"></path>
        <path fill="none" d="M0 0h48v48H0z"></path>
      </svg>
							</div>
							<span style="display: none;">Sign in with Google</span>
						</div>
					</button>
				</div>
			</div>
			<button type="button" id="adminDemoLogin" class="button secondary"
				style="width: 100%; margin-top: 20px;">관리자 체험 로그인 (Mock)</button>

			<p class="source-note">관리자 화면 확인용 체험 로그인입니다.</p>

			<p id="adminDemoError" class="error-message" role="alert"></p>
		</section>
	</main>
	<%@ include file="../common/footer.jspf"%>
	<script>
		document.addEventListener("DOMContentLoaded", function() {
			var form = document.getElementById("loginForm");
			var loginIdInput = form.elements.loginId;
			var saveEmail = document.getElementById("saveEmail");
			var storageKey = "daisomall.savedLoginEmail";

			var savedEmail = localStorage.getItem(storageKey);

			if (savedEmail) {
				emailInput.value = savedEmail;
				saveEmail.checked = true;
			}

			form.addEventListener("submit", function() {
				if (saveEmail.checked) {
					localStorage.setItem(storageKey, emailInput.value.trim());
				} else {
					localStorage.removeItem(storageKey);
				}
			});
		});
	</script>
	<script>
		document.addEventListener("DOMContentLoaded", function() {
			var button = document.getElementById("adminDemoLogin");
			var errorMessage = document.getElementById("adminDemoError");

			window.Daiso.ready.then(function(repository) {
				button.addEventListener("click", function() {
					errorMessage.textContent = "";

					try {
						repository.login("admin@example.test", "demo1234");
						location.href = window.Daiso.url("admin/", {
							view : "dashboard"
						});
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

		document
				.getElementById("kakao-login-btn")
				.addEventListener(
						"click",
						function() {
							Kakao.Auth
									.authorize({
										redirectUri : "http://localhost:8080/daisomall/app/member/oauth/kakao/callback",
										scope : "account_email"
									});
						});
	</script>
</body>
</html>