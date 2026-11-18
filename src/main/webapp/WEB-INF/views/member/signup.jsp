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
            <form id="signupForm" class="member-form" method="post"
      action="${pageContext.request.contextPath}/app/member/signup">

    <label class="field">
    <span>이름 <span class="required-mark">*</span></span>
    <input name="name" type="text" required maxlength="100">
</label>

    <label class="field">
      <span>  휴대폰 <span class="required-mark">*</span></span>
        <input name="phone" type="tel" required maxlength="30"
               autocomplete="tel" placeholder="휴대폰 번호를 입력해 주세요">
    </label>

    <label class="field">
      <span>생년월일 <span class="required-mark">*</span></span>
        <input name="birthDate" type="text"
       required minlength="8" maxlength="8"
       inputmode="numeric" pattern="[0-9]{8}"
       placeholder="생년월일 8자리 (예: 19970318)"
       autocomplete="bday">
    </label>

    <label class="field">
        성별 (선택)
        <select name="gender">
            <option value="">선택 안 함</option>
            <option value="MALE">남자</option>
            <option value="FEMALE">여자</option>
        </select>
    </label>

    <label class="field">
     <span>   아이디 <span class="required-mark">*</span></span>
        <input name="loginId" type="text" required minlength="4"
               maxlength="50" autocomplete="username"
               placeholder="아이디를 입력해 주세요">
    </label>

    <label class="field">
       <span> 비밀번호 (8~16자) <span class="required-mark">*</span></span>
        <input name="password" type="password" required minlength="8"
               maxlength="16" autocomplete="new-password">
    </label>

    <label class="field">
      <span>  비밀번호 확인 <span class="required-mark">*</span></span>
        <input name="confirmPassword" type="password" required minlength="8"
               maxlength="16" autocomplete="new-password">
    </label>

    <label class="field">
       <span> 이메일 <span class="required-mark">*</span></span>
        <input name="email" type="email" required maxlength="120"
               autocomplete="email" placeholder="이메일을 입력해 주세요">
    </label>

    <p class="error-message" role="alert">${signupError}</p>

    <button type="submit" class="button primary">
        회원가입
    </button>
</form>
        </section>
    </main>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
