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

    <main id="main-content" class="container shop-page" data-page="mypage" tabindex="-1">
        <section class="member-panel">
            <h1 class="page-title">마이페이지</h1>
            <p class="member-greeting">${memberName}님, 반갑습니다.</p>
            <p>${memberEmail}</p>

            <form method="post"
                  action="${pageContext.request.contextPath}/app/member/logout">
                <button type="submit" class="button secondary">로그아웃</button>
            </form>
        </section>
    </main>

    <%@ include file="../common/footer.jspf" %>
</body>
</html>