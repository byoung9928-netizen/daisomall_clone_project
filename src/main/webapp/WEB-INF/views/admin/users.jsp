<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,daiso.member.controller.AdminMemberInfo,daiso.product.controller.ProductView" %>
<%
    request.setAttribute("adminSection", "users");

    String context = request.getContextPath();
    String route = context + "/app/admin/users";

    List<AdminMemberInfo> members =
            (List<AdminMemberInfo>) request.getAttribute("adminMembers");
    if (members == null) {
        members = List.of();
    }

    String query = (String) request.getAttribute("adminMemberQuery");
    if (query == null) {
        query = "";
    }

    String status = (String) request.getAttribute("adminMemberStatus");
    if (status == null) {
        status = "ALL";
    }
    
    Long memberTotal = (Long) request.getAttribute("adminMemberTotal");
    Long memberPageValue = (Long) request.getAttribute("adminMemberPage");
    Long memberPageCount = (Long) request.getAttribute("adminMemberTotalPages");
    long memberPage = memberPageValue == null ? 1 : memberPageValue;

    String memberPageUrl = route
            + "?query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
            + "&status=" + java.net.URLEncoder.encode(status, java.nio.charset.StandardCharsets.UTF_8)
            + "&page=";

    String errorMessage = (String) request.getAttribute("adminMembersError");
    String noticeMessage = (String) request.getAttribute("adminMembersNotice");
    String csrfToken = (String) request.getAttribute("adminMemberCsrf");
    String actingAdminId = session.getAttribute("member_id") == null
            ? "" : String.valueOf(session.getAttribute("member_id"));
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
            <h1 class="page-title">회원 관리</h1>

            <% if (errorMessage != null) { %>
                <p class="error-message" role="alert">
                    <%= ProductView.escape(errorMessage) %>
                </p>
            <% } %>

			<%
			if (noticeMessage != null) {
			%>
			<p class="success-message" role="status">
				<%=ProductView.escape(noticeMessage)%>
			</p>
			<%
			}
			%>

			<form class="admin-search"
                  action="<%= ProductView.escape(route) %>"
                  method="get">
                <label class="field">
                    <span>회원 ID, 로그인 ID, 이름 또는 이메일 검색</span>
                    <input type="search"
                           name="query"
                           maxlength="120"
                           value="<%= ProductView.escape(query) %>"
                           placeholder="검색어 입력">
                </label>

                <label class="field">
                    <span>회원 상태</span>
                    <select name="status">
                        <option value="ALL" <%= "ALL".equals(status) ? "selected" : "" %>>
                            전체 상태
                        </option>
                        <option value="ACTIVE" <%= "ACTIVE".equals(status) ? "selected" : "" %>>
                            활성
                        </option>
                        <option value="SUSPENDED" <%= "SUSPENDED".equals(status) ? "selected" : "" %>>
                            이용 중지
                        </option>
                    </select>
                </label>

                <button class="button" type="submit">검색</button>
                <a class="button secondary"
                   href="<%= ProductView.escape(route) %>">초기화</a>
            </form>

            <% if (memberTotal != null) { %>
    <p class="admin-count" role="status">
        검색 결과 <%= memberTotal %>명
        · <%= memberPage %>/<%= memberPageCount %>페이지
    </p>
<% } %>

            <div class="admin-table-scroll"
                 tabindex="0"
                 role="region"
                 aria-label="회원 목록 표 (가로 스크롤 가능)">
                <table class="admin-table">
                    <caption class="visually-hidden">회원 목록</caption>
                    <thead>
                        <tr>
                            <th scope="col">회원 ID</th>
                            <th scope="col">로그인 ID</th>
                            <th scope="col">이름</th>
                            <th scope="col">이메일</th>
                            <th scope="col">유형</th>
                            <th scope="col">상태</th>
                            <th scope="col">가입일</th>
                            <th scope="col">관리</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (members.isEmpty()) { %>
                            <tr>
                                <td colspan="8">조건에 맞는 회원이 없습니다.</td>
                            </tr>
                        <% } else {
                            for (AdminMemberInfo member : members) {
                                String roleLabel =
                                        "ADMIN".equalsIgnoreCase(member.getRole())
                                                ? "관리자" : "회원";
                                String statusLabel =
                                        "ACTIVE".equalsIgnoreCase(member.getStatus())
                                                ? "활성"
                                                : "SUSPENDED".equalsIgnoreCase(member.getStatus())
                                                        ? "이용 중지"
                                                        		: "WITHDRAWN".equalsIgnoreCase(member.getStatus())
                                                                ? "탈퇴"
                                                                : member.getStatus();
                                String createdAt =
                                        member.getCreatedAt() == null
                                                ? "-"
                                                : new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm")
                                                        .format(member.getCreatedAt());
                        %>
                            <tr>
                                <td><%= ProductView.escape(member.getMemberId()) %></td>
                                <td><%= ProductView.escape(member.getLoginId()) %></td>
							<td><a class="text-button"
								href="<%=ProductView.escape(
		context + "/app/admin/users/detail?memberId=" + java.net.URLEncoder.encode(member.getMemberId(), "UTF-8"))%>">
									<%=ProductView.escape(member.getName())%>
							</a></td>
							<td><%=ProductView.escape(member.getEmail())%></td>
                                <td><%= ProductView.escape(roleLabel) %></td>
                                <td><%= ProductView.escape(statusLabel) %></td>
                                <td><%= ProductView.escape(createdAt) %></td>
							<td>
								<%
								boolean isAdmin = "ADMIN".equalsIgnoreCase(member.getRole());
									boolean isSelf = member.getMemberId() != null && member.getMemberId().equals(actingAdminId);
									boolean isActive = "ACTIVE".equalsIgnoreCase(member.getStatus());
									boolean isSuspended = "SUSPENDED".equalsIgnoreCase(member.getStatus());

									if (!isAdmin && !isSelf && (isActive || isSuspended)) {
										String nextStatus = isActive ? "SUSPENDED" : "ACTIVE";
										String buttonLabel = isActive ? "이용 중지" : "활성화";
								%>
								<form method="post"
									action="<%=ProductView.escape(context + "/app/admin/users/status")%>">
									<input type="hidden" name="csrf"
										value="<%=ProductView.escape(csrfToken)%>"> <input
										type="hidden" name="memberId"
										value="<%=ProductView.escape(member.getMemberId())%>">
									<input type="hidden" name="query"
										value="<%=ProductView.escape(query)%>"> <input
										type="hidden" name="filterStatus"
										value="<%=ProductView.escape(status)%>"> <input
										type="hidden" name="newStatus" value="<%=nextStatus%>">
									<button class="button secondary" type="submit">
										<%=buttonLabel%>
									</button>
								</form> <%
 } else {
 %> - <%
 }
 %>
							</td>
						</tr>
                        <%
                        }
                        }
                        %>
                    </tbody>
                </table>
            </div>
            <% if (memberPageCount != null && memberPageCount > 1) { %>
    <nav class="admin-product-pagination" aria-label="회원 목록 페이지">
        <% if (memberPage > 1) { %>
            <a class="button secondary"
               href="<%=ProductView.escape(memberPageUrl + (memberPage - 1))%>">이전</a>
        <% } %>

        <% for (long p = Math.max(1, memberPage - 2);
                p <= Math.min(memberPageCount, memberPage + 2); p++) { %>
            <% if (p == memberPage) { %>
                <span class="admin-product-page-number" aria-current="page"><%=p%></span>
            <% } else { %>
                <a class="admin-product-page-number"
                   href="<%=ProductView.escape(memberPageUrl + p)%>"><%=p%></a>
            <% } %>
        <% } %>

        <% if (memberPage < memberPageCount) { %>
            <a class="button secondary"
               href="<%=ProductView.escape(memberPageUrl + (memberPage + 1))%>">다음</a>
        <% } %>
    </nav>
<% } %>
        </main>
    </div>
</body>
</html>