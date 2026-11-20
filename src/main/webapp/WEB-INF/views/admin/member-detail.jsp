<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Collections,java.text.NumberFormat,java.time.format.DateTimeFormatter,daiso.member.controller.AdminMemberDetailInfo,daiso.member.controller.AdminMemberConsentInfo,daiso.order.db.OrderBean,daiso.product.controller.ProductView" %>
<%
    request.setAttribute("adminSection", "users");

    String context = request.getContextPath();
    String errorMessage =
            (String) request.getAttribute("adminMemberDetailError");
    AdminMemberDetailInfo member =
            (AdminMemberDetailInfo) request.getAttribute("adminMemberDetail");
    List<OrderBean> memberOrders =
            (List<OrderBean>) request.getAttribute("adminMemberOrders");
    if (memberOrders == null) {
        memberOrders = Collections.emptyList();
    }

    String ordersError =
            (String) request.getAttribute("adminMemberOrdersError");
    java.util.Set<String> socialProviders =
            (java.util.Set<String>) request.getAttribute(
                    "adminMemberSocialProviders"
            );
    if (socialProviders == null) {
        socialProviders = Collections.emptySet();
    }

    String socialError =
            (String) request.getAttribute("adminMemberSocialError");
    AdminMemberConsentInfo consent =
            (AdminMemberConsentInfo) request.getAttribute(
                    "adminMemberConsent"
            );
    String consentError =
            (String) request.getAttribute("adminMemberConsentError");

    String retentionPeriodLabel = "-";
    String consentUpdatedAt = "-";

    if (consent != null) {
        String retentionPeriod = consent.getRetentionPeriod();

        if ("WITHDRAWAL".equals(retentionPeriod)) {
            retentionPeriodLabel = "회원 탈퇴 시까지";
        } else if ("5_YEARS".equals(retentionPeriod)) {
            retentionPeriodLabel = "5년";
        } else if ("3_YEARS".equals(retentionPeriod)) {
            retentionPeriodLabel = "3년";
        }

        if (consent.getUpdatedAt() != null) {
            consentUpdatedAt =
                    new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm")
                            .format(consent.getUpdatedAt());
        }
    }
    NumberFormat money = NumberFormat.getIntegerInstance();
    DateTimeFormatter orderDateFormat =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    String birthDate = "-";
    String createdAt = "-";
    String genderLabel = "-";
    String roleLabel = "-";
    String statusLabel = "-";

    if (member != null) {
        if (member.getBirthDate() != null) {
            birthDate = member.getBirthDate().toString();
        }

        if (member.getCreatedAt() != null) {
            createdAt = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm")
                    .format(member.getCreatedAt());
        }

        if ("MALE".equalsIgnoreCase(member.getGender())) {
            genderLabel = "남성";
        } else if ("FEMALE".equalsIgnoreCase(member.getGender())) {
            genderLabel = "여성";
        } else if (member.getGender() != null
                && !member.getGender().trim().isEmpty()) {
            genderLabel = member.getGender();
        }

        roleLabel = "ADMIN".equalsIgnoreCase(member.getRole())
                ? "관리자" : "회원";

        statusLabel = "ACTIVE".equalsIgnoreCase(member.getStatus())
                ? "활성"
                : "SUSPENDED".equalsIgnoreCase(member.getStatus())
                        ? "이용 중지" : member.getStatus();
    }
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/admin-head.jspf" %>
</head>
<body class="admin-body">
    <a class="skip-link" href="#admin-content">관리자 본문 바로가기</a>
    <%@ include file="../common/admin-header.jspf" %>

    <div class="admin-layout">
        <%@ include file="../common/admin-navigation.jspf" %>

        <main id="admin-content" class="admin-content" tabindex="-1">
            <p class="admin-eyebrow">MEMBER MANAGEMENT</p>
            <h1 class="page-title">회원 상세정보</h1>

            <% if (errorMessage != null) { %>
                <p class="error-message" role="alert">
                    <%= ProductView.escape(errorMessage) %>
                </p>
            <% } %>

            <% if (member != null) { %>
                <div class="admin-table-scroll"
                     role="region"
                     aria-label="회원 상세정보">
                    <table class="admin-table">
                        <caption class="visually-hidden">회원 상세정보</caption>
                        <tbody>
                            <tr>
                                <th scope="row">회원 ID</th>
                                <td><%= ProductView.escape(member.getMemberId()) %></td>
                            </tr>
                            <tr>
                                <th scope="row">로그인 ID</th>
                                <td><%= ProductView.escape(member.getLoginId()) %></td>
                            </tr>
                            <tr>
                                <th scope="row">이름</th>
                                <td><%= ProductView.escape(member.getName()) %></td>
                            </tr>
                            <tr>
                                <th scope="row">이메일</th>
                                <td><%= ProductView.escape(member.getEmail()) %></td>
                            </tr>
                            <tr>
                                <th scope="row">전화번호</th>
                                <td><%= ProductView.escape(member.getPhone()) %></td>
                            </tr>
                            <tr>
                                <th scope="row">생년월일</th>
                                <td><%= ProductView.escape(birthDate) %></td>
                            </tr>
                            <tr>
                                <th scope="row">성별</th>
                                <td><%= ProductView.escape(genderLabel) %></td>
                            </tr>
                            <tr>
                                <th scope="row">회원 유형</th>
                                <td><%= ProductView.escape(roleLabel) %></td>
                            </tr>
                            <tr>
                                <th scope="row">계정 상태</th>
                                <td><%= ProductView.escape(statusLabel) %></td>
                            </tr>
                            <tr>
                                <th scope="row">가입일</th>
                                <td><%= ProductView.escape(createdAt) %></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                                <section class="admin-panel"
                         aria-labelledby="member-orders-heading">
                    <h2 id="member-orders-heading">최근 주문 내역</h2>

                    <% if (ordersError != null) { %>
                        <p class="error-message" role="alert">
                            <%= ProductView.escape(ordersError) %>
                        </p>
                    <% } else { %>
                        <p>최근 주문 최대 10건 · 조회 결과 <%= memberOrders.size() %>건</p>

                        <div class="admin-table-scroll"
                             tabindex="0"
                             role="region"
                             aria-label="회원 최근 주문 목록">
                            <table class="admin-table">
                                <caption class="visually-hidden">
                                    회원 최근 주문 내역
                                </caption>
                                <thead>
                                    <tr>
                                        <th scope="col">주문번호</th>
                                        <th scope="col">상품</th>
                                        <th scope="col">주문일시</th>
                                        <th scope="col">결제금액</th>
                                        <th scope="col">주문 상태</th>
                                        <th scope="col">결제 상태</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <% if (memberOrders.isEmpty()) { %>
                                        <tr>
                                            <td colspan="6">주문 내역이 없습니다.</td>
                                        </tr>
                                    <% } else {
                                        for (OrderBean order : memberOrders) {
                                            String orderStatus =
                                                    order.getStatus() == null
                                                            ? "-"
                                                            : order.getStatus();
                                            if ("PENDING".equals(orderStatus)) {
                                                orderStatus = "결제 대기";
                                            } else if ("PAID".equals(orderStatus)) {
                                                orderStatus = "결제 완료";
                                            } else if ("CANCELLED".equals(orderStatus)) {
                                                orderStatus = "취소";
                                            }

                                            String paymentStatus =
                                                    order.getPaymentStatus() == null
                                                            ? "결제 기록 없음"
                                                            : order.getPaymentStatus();
                                            if ("APPROVED".equals(paymentStatus)) {
                                                paymentStatus = "결제 완료";
                                            } else if ("READY".equals(paymentStatus)) {
                                                paymentStatus = "결제 대기";
                                            } else if ("CANCELLED".equals(paymentStatus)) {
                                                paymentStatus = "결제 취소";
                                            } else if ("FAILED".equals(paymentStatus)) {
                                                paymentStatus = "결제 실패";
                                            }

                                            String orderDate =
                                                    order.getCreatedAt() == null
                                                            ? "-"
                                                            : order.getCreatedAt()
                                                                    .toLocalDateTime()
                                                                    .format(orderDateFormat);
                                    %>
                                        <tr>
                                            <td><%= ProductView.escape(order.getOrderNo()) %></td>
                                            <td><%= ProductView.escape(order.getProductSummary()) %></td>
                                            <td><%= ProductView.escape(orderDate) %></td>
                                            <td><%= money.format(order.getTotalAmount()) %>원</td>
                                            <td><%= ProductView.escape(orderStatus) %></td>
                                            <td><%= ProductView.escape(paymentStatus) %></td>
                                        </tr>
                                    <%  }
                                       } %>
                                </tbody>
                            </table>
                        </div>
                    <% } %>
                </section>
                                <section class="admin-panel"
                         aria-labelledby="member-social-heading">
                    <h2 id="member-social-heading">SNS 연동 상태</h2>

                    <% if (socialError != null) { %>
                        <p class="error-message" role="alert">
                            <%= ProductView.escape(socialError) %>
                        </p>
                    <% } else { %>
                        <ul>
                            <%
                                String[][] socialOptions = {
                                    {"KAKAO", "카카오"},
                                    {"NAVER", "네이버"},
                                    {"GOOGLE", "구글"}
                                };

                                for (String[] option : socialOptions) {
                                    boolean linked =
                                            socialProviders.contains(option[0]);
                            %>
                                <li>
                                    <%= option[1] %>:
                                    <%= linked ? "연동됨" : "미연동" %>
                                </li>
                            <% } %>
                        </ul>
                    <% } %>
                </section>
                                <section class="admin-panel"
                         aria-labelledby="member-consent-heading">
                    <h2 id="member-consent-heading">수신 동의 및 개인정보 설정</h2>

                    <% if (consentError != null) { %>
                        <p class="error-message" role="alert">
                            <%= ProductView.escape(consentError) %>
                        </p>
                    <% } else if (consent == null) { %>
                        <p>저장된 동의 설정이 없습니다.</p>
                    <% } else { %>
                        <div class="admin-table-scroll"
                             role="region"
                             aria-label="회원 수신 동의 및 개인정보 설정">
                            <table class="admin-table">
                                <caption class="visually-hidden">
                                    회원 수신 동의 및 개인정보 설정
                                </caption>
                                <tbody>
                                    <tr>
                                        <th scope="row">마케팅 문자 수신</th>
                                        <td><%= consent.isMarketingSms() ? "동의" : "미동의" %></td>
                                    </tr>
                                    <tr>
                                        <th scope="row">마케팅 이메일 수신</th>
                                        <td><%= consent.isMarketingEmail() ? "동의" : "미동의" %></td>
                                    </tr>
                                    <tr>
                                        <th scope="row">마케팅 개인정보 활용</th>
                                        <td><%= consent.isMarketingPersonalInfo() ? "동의" : "미동의" %></td>
                                    </tr>
                                    <tr>
                                        <th scope="row">위치기반 서비스 약관</th>
                                        <td><%= consent.isLocationTerms() ? "동의" : "미동의" %></td>
                                    </tr>
                                    <tr>
                                        <th scope="row">개인정보 보유 기간</th>
                                        <td><%= ProductView.escape(retentionPeriodLabel) %></td>
                                    </tr>
                                    <tr>
                                        <th scope="row">최종 수정일</th>
                                        <td><%= ProductView.escape(consentUpdatedAt) %></td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    <% } %>
                </section>
            <% } %>

            <p>
                <a class="button secondary"
                   href="<%= ProductView.escape(context + "/app/admin/users") %>">
                    회원 목록으로
                </a>
            </p>
        </main>
    </div>
</body>
</html>