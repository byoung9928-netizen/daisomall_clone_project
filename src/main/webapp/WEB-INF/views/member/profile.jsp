<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.member.controller.MemberProfileInfo,daiso.product.controller.ProductView" %>
<%
String profileContext = request.getContextPath();
MemberProfileInfo profile =
        (MemberProfileInfo) request.getAttribute("profile");

String genderText = "선택 안 함";
if (profile != null && "MALE".equals(profile.getGender())) {
    genderText = "남자";
} else if (profile != null && "FEMALE".equals(profile.getGender())) {
    genderText = "여자";
}
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <style>
        .profile-panel {
            width: calc(100% - 40px);
            max-width: 960px;
            margin: 36px auto 80px;
        }

        .profile-info {
            margin-top: 28px;
            border-top: 1px solid #dfe3e8;
        }

        .profile-info > div {
            display: grid;
            grid-template-columns: 190px minmax(0, 1fr);
        }

        .profile-info dt,
        .profile-info dd {
            margin: 0;
            padding: 18px 20px;
            border-bottom: 1px solid #dfe3e8;
        }

        .profile-info dt {
            background: #f6f7f8;
            font-weight: 700;
        }

        .profile-actions {
            margin-top: 28px;
            text-align: right;
        }

        @media (max-width: 600px) {
            .profile-info > div {
                grid-template-columns: 120px minmax(0, 1fr);
            }

            .profile-info dt,
            .profile-info dd {
                padding: 14px 10px;
            }
        }
    </style>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>

    <main id="main-content" class="container shop-page" tabindex="-1">
        <section class="profile-panel">
            <h1 class="page-title">회원정보</h1>

            <% if (request.getAttribute("profileError") != null) { %>
                <p class="error-message" role="alert">
                    <%= ProductView.escape(request.getAttribute("profileError")) %>
                </p>
            <% } else if (profile != null) { %>
                <p class="source-note">
                    회원가입 때 입력한 회원정보입니다.
                </p>

                <dl class="profile-info">
                    <div>
                        <dt>이름</dt>
                        <dd><%= ProductView.escape(profile.getName()) %></dd>
                    </div>
                    <div>
                        <dt>휴대폰</dt>
                        <dd><%= ProductView.escape(profile.getPhone()) %></dd>
                    </div>
                    <div>
                        <dt>생년월일</dt>
                        <dd>
                            <%= profile.getBirthDate() == null
                                    ? "미입력"
                                    : ProductView.escape(profile.getBirthDate()) %>
                        </dd>
                    </div>
                    <div>
                        <dt>성별</dt>
                        <dd><%= ProductView.escape(genderText) %></dd>
                    </div>
                    <div>
                        <dt>아이디</dt>
                        <dd><%= ProductView.escape(profile.getLoginId()) %></dd>
                    </div>
                    <div>
                        <dt>이메일</dt>
                        <dd><%= ProductView.escape(profile.getEmail()) %></dd>
                    </div>
                </dl>

                <div class="profile-actions">
                    <a class="button secondary"
                       href="<%= profileContext %>/app/order/mypage">
                        마이페이지로 돌아가기
                    </a>
                </div>
            <% } %>
        </section>
    </main>

    <%@ include file="../common/footer.jspf" %>
</body>
</html>