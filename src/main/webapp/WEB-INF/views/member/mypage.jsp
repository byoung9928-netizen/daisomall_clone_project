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
            <h1 class="page-title">
                마이페이지
            </h1>
            <p class="member-greeting">
            </p>
            <p data-member-email>
            </p>
            <div class="action-stack">
                <a class="button secondary" href="${pageContext.request.contextPath}/profile.jsp">
                    회원정보 수정
                </a>
                <button type="button" class="button secondary" data-orders>
                    주문 내역
                </button>
                <button type="button" class="button" data-logout>
                    로그아웃
                </button>
                <a class="button secondary" data-admin href="${pageContext.request.contextPath}/admin/">
                    관리자 화면
                </a>
            </div>
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
            const panel = root.querySelector("section");
            const member = repository.getSession();
            if (!member) { panel.querySelector(".member-greeting").remove(); panel.querySelector("[data-member-email]").remove(); panel.querySelector(".action-stack").remove(); panel.append(element("p", "", "로그인 후 나의 쇼핑 내역을 확인하세요."), link("로그인", "login.jsp", {}, "button primary")); return; }
            panel.querySelector(".member-greeting").textContent = member.name + "님, 반갑습니다.";
            panel.querySelector("[data-member-email]").textContent = member.email;
            if (member.role !== "ADMIN") panel.querySelector("[data-admin]").remove();
            panel.querySelector("[data-orders]").addEventListener("click", () => { location.href = app.url("orders.jsp"); });
            panel.querySelector("[data-logout]").addEventListener("click", () => { repository.logout(); location.href = app.url("login.jsp"); });
        }).catch(function (error) { app.ui.error(root, error); });
    });
    </script>
</body>
</html>
