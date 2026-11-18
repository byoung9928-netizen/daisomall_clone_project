<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductView" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
	<style>
.signup-async-control {
    display: flex;
    gap: 8px;
    align-items: stretch;
}

.signup-async-control input {
    flex: 1;
    min-width: 0;
}

.member-form .signup-async-control .button {
    width: auto;
    min-width: 100px;
    white-space: nowrap;
}

.signup-field-status {
    min-height: 1em;
    margin: 0;
    font-size: 13px;
    font-weight: 400;
}

.signup-field-status.is-error {
    color: #e60012;
}

.signup-field-status.is-success {
    color: #19664b;
}
</style>
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
            <form id="signupForm" class="member-form" method="post"
      action="${pageContext.request.contextPath}/app/member/signup">

    <div class="field">
        <label for="signupName">이름 <span class="required-mark">*</span></label>
        <input id="signupName" name="name" type="text" required maxlength="100"
               value="<%= ProductView.escape(request.getAttribute("name")) %>">
        <p id="nameStatus" class="signup-field-status" aria-live="polite"></p>
    </div>

    <div class="field">
        <label for="signupPhone">휴대폰 <span class="required-mark">*</span></label>
        <input id="signupPhone" name="phone" type="tel" required maxlength="30"
               autocomplete="tel" placeholder="휴대폰 번호를 입력해 주세요"
               value="<%= ProductView.escape(request.getAttribute("phone")) %>">
        <p id="phoneStatus" class="signup-field-status" aria-live="polite"></p>
    </div>

    <div class="field">
        <label for="signupBirthDate">생년월일 <span class="required-mark">*</span></label>
        <input id="signupBirthDate" name="birthDate" type="text" required
               minlength="8" maxlength="8" inputmode="numeric"
               pattern="[0-9]{8}" placeholder="예: 19970318"
               value="<%= ProductView.escape(request.getAttribute("birthDate")) %>">
        <p id="birthDateStatus" class="signup-field-status" aria-live="polite"></p>
    </div>

    <div class="field">
        <label for="signupGender">성별 (선택)</label>
        <select id="signupGender" name="gender">
            <option value="">선택 안 함</option>
            <option value="MALE">남자</option>
            <option value="FEMALE">여자</option>
        </select>
    </div>

    <div class="field">
        <label for="signupLoginId">아이디 <span class="required-mark">*</span></label>
        <div class="signup-async-control">
            <input id="signupLoginId" name="loginId" type="text" required
                   minlength="4" maxlength="50" autocomplete="username"
                   placeholder="영문 소문자, 숫자, ., _, - 사용"
                   value="<%= ProductView.escape(request.getAttribute("loginId")) %>">
            <button id="checkLoginId" type="button" class="button secondary">
                중복 확인
            </button>
        </div>
        <p id="loginIdStatus" class="signup-field-status" aria-live="polite"></p>
    </div>

    <div class="field">
        <label for="signupPassword">비밀번호 <span class="required-mark">*</span></label>
        <input id="signupPassword" name="password" type="password" required
               minlength="8" maxlength="16" autocomplete="new-password"
               placeholder="영문, 숫자, 특수문자 포함 8~16자">
        <p id="passwordStatus" class="signup-field-status" aria-live="polite"></p>
    </div>

    <div class="field">
        <label for="confirmPassword">비밀번호 확인 <span class="required-mark">*</span></label>
        <input id="confirmPassword" name="confirmPassword" type="password"
               required minlength="8" maxlength="16" autocomplete="new-password">
        <p id="confirmPasswordStatus" class="signup-field-status"
           aria-live="polite"></p>
    </div>

    <div class="field">
        <label for="signupEmail">이메일 <span class="required-mark">*</span></label>
        <div class="signup-async-control">
            <input id="signupEmail" name="email" type="email" required
                   maxlength="120" autocomplete="email"
                   placeholder="이메일을 입력해 주세요"
                   value="<%= ProductView.escape(request.getAttribute("email")) %>">
            <button id="checkEmail" type="button" class="button secondary">
                중복 확인
            </button>
        </div>
        <p id="emailStatus" class="signup-field-status" aria-live="polite"></p>
    </div>

    <p class="error-message" role="alert">${signupError}</p>

    <button type="submit" class="button primary">
        회원가입
    </button>
</form>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
    <script>
document.addEventListener("DOMContentLoaded", function () {
    const form = document.getElementById("signupForm");
    const contextPath = "${pageContext.request.contextPath}";

    const nameInput = document.getElementById("signupName");
    const phoneInput = document.getElementById("signupPhone");
    const birthDateInput = document.getElementById("signupBirthDate");
    const loginIdInput = document.getElementById("signupLoginId");
    const passwordInput = document.getElementById("signupPassword");
    const confirmPasswordInput = document.getElementById("confirmPassword");
    const emailInput = document.getElementById("signupEmail");

    let checkedLoginId = "";
    let checkedEmail = "";

    function showStatus(id, message, success) {
        const element = document.getElementById(id);
        element.textContent = message;
        element.classList.toggle("is-success", success);
        element.classList.toggle("is-error", Boolean(message) && !success);
    }

    function validateName() {
        const valid = nameInput.value.trim().length > 0;
        showStatus("nameStatus", valid ? "입력 완료" : "이름을 입력해 주세요.", valid);
        return valid;
    }

    function validatePhone() {
        const digits = phoneInput.value.replace(/\D/g, "");
        const valid = digits.length >= 10 && digits.length <= 11;
        showStatus(
            "phoneStatus",
            valid ? "입력 완료" : "휴대폰 번호를 확인해 주세요.",
            valid
        );
        return valid;
    }

    function validateBirthDate() {
        const value = birthDateInput.value.trim();

        if (!/^\d{8}$/.test(value)) {
            showStatus("birthDateStatus", "생년월일을 숫자 8자리로 입력해 주세요.", false);
            return false;
        }

        const year = Number(value.substring(0, 4));
        const month = Number(value.substring(4, 6));
        const day = Number(value.substring(6, 8));
        const date = new Date(year, month - 1, day);

        const valid =
            date.getFullYear() === year
            && date.getMonth() === month - 1
            && date.getDate() === day
            && date <= new Date();

        showStatus(
            "birthDateStatus",
            valid ? "입력 완료" : "생년월일을 확인해 주세요.",
            valid
        );
        return valid;
    }

    function validatePassword() {
        const valid =
            /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,16}$/
                .test(passwordInput.value);

        showStatus(
            "passwordStatus",
            valid
                ? "사용할 수 있는 비밀번호입니다."
                : "영문, 숫자, 특수문자를 포함해 8~16자로 입력해 주세요.",
            valid
        );
        return valid;
    }

    function validateConfirmPassword() {
        const valid =
            confirmPasswordInput.value.length > 0
            && confirmPasswordInput.value === passwordInput.value;

        showStatus(
            "confirmPasswordStatus",
            valid ? "비밀번호가 일치합니다." : "비밀번호가 일치하지 않습니다.",
            valid
        );
        return valid;
    }

    async function checkLoginId() {
        const value = loginIdInput.value.trim().toLowerCase();

        if (!/^[a-z0-9._-]{4,50}$/.test(value)) {
            checkedLoginId = "";
            showStatus(
                "loginIdStatus",
                "아이디는 영문 소문자·숫자·., _, -로 4~50자 입력해 주세요.",
                false
            );
            return false;
        }

        showStatus("loginIdStatus", "아이디를 확인하고 있습니다.", false);

        try {
            const url = contextPath
                + "/app/member/signup/check-login-id?loginId="
                + encodeURIComponent(value);
            const response = await fetch(url, { cache: "no-store" });
            const result = (await response.text()).trim();

            if (!response.ok) {
                throw new Error("확인 요청 실패");
            }

            if (value !== loginIdInput.value.trim().toLowerCase()) {
                return false;
            }

            if (result === "available") {
                checkedLoginId = value;
                showStatus("loginIdStatus", "사용할 수 있는 아이디입니다.", true);
                return true;
            }

            checkedLoginId = "";
            showStatus(
                "loginIdStatus",
                result === "duplicate"
                    ? "이미 사용 중인 아이디입니다."
                    : "아이디 형식을 확인해 주세요.",
                false
            );
            return false;
        } catch (error) {
            checkedLoginId = "";
            showStatus("loginIdStatus", "중복 확인에 실패했습니다. 다시 시도해 주세요.", false);
            return false;
        }
    }

    async function checkEmail() {
        const value = emailInput.value.trim().toLowerCase();

        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
            checkedEmail = "";
            showStatus("emailStatus", "이메일 형식을 확인해 주세요.", false);
            return false;
        }

        showStatus("emailStatus", "이메일을 확인하고 있습니다.", false);

        try {
            const url = contextPath
                + "/app/member/signup/check-email?email="
                + encodeURIComponent(value);
            const response = await fetch(url, { cache: "no-store" });
            const result = (await response.text()).trim();

            if (!response.ok) {
                throw new Error("확인 요청 실패");
            }

            if (value !== emailInput.value.trim().toLowerCase()) {
                return false;
            }

            if (result === "available") {
                checkedEmail = value;
                showStatus("emailStatus", "사용할 수 있는 이메일입니다.", true);
                return true;
            }

            checkedEmail = "";
            showStatus(
                "emailStatus",
                result === "duplicate"
                    ? "이미 가입된 이메일입니다."
                    : "이메일 형식을 확인해 주세요.",
                false
            );
            return false;
        } catch (error) {
            checkedEmail = "";
            showStatus("emailStatus", "중복 확인에 실패했습니다. 다시 시도해 주세요.", false);
            return false;
        }
    }

    nameInput.addEventListener("blur", validateName);
    phoneInput.addEventListener("blur", validatePhone);
    birthDateInput.addEventListener("blur", validateBirthDate);
    passwordInput.addEventListener("input", function () {
        validatePassword();
        if (confirmPasswordInput.value) {
            validateConfirmPassword();
        }
    });
    confirmPasswordInput.addEventListener("input", validateConfirmPassword);

    loginIdInput.addEventListener("input", function () {
        checkedLoginId = "";
        showStatus("loginIdStatus", "", false);
    });
    loginIdInput.addEventListener("blur", checkLoginId);
    document.getElementById("checkLoginId")
        .addEventListener("click", checkLoginId);

    emailInput.addEventListener("input", function () {
        checkedEmail = "";
        showStatus("emailStatus", "", false);
    });
    emailInput.addEventListener("blur", checkEmail);
    document.getElementById("checkEmail")
        .addEventListener("click", checkEmail);

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        const localFieldsValid =
            validateName()
            && validatePhone()
            && validateBirthDate()
            && validatePassword()
            && validateConfirmPassword();

        if (!localFieldsValid || !form.reportValidity()) {
            return;
        }

        const loginId = loginIdInput.value.trim().toLowerCase();
        const email = emailInput.value.trim().toLowerCase();

        const loginIdAvailable =
            checkedLoginId === loginId || await checkLoginId();
        const emailAvailable =
            checkedEmail === email || await checkEmail();

        if (!loginIdAvailable || !emailAvailable) {
            return;
        }

        HTMLFormElement.prototype.submit.call(form);
    });
});
</script>
</body>
</html>
