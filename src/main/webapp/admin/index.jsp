<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
javax.servlet.http.HttpSession adminSession = request.getSession(false);

if (adminSession == null
        || adminSession.getAttribute("member_id") == null
        || !"ADMIN".equalsIgnoreCase(
                String.valueOf(adminSession.getAttribute("role")))) {
    response.sendRedirect(
            request.getContextPath() + "/app/member/login");
    return;
}

    String adminSection = request.getParameter("view");
    if (adminSection == null) {
        adminSection = "dashboard";
    }
    if (!adminSection.matches("dashboard|categories|products|inventory|stores|orders|users")) {
        response.sendError(404);
        return;
    }
    if ("products".equals(adminSection)) {
        request.getRequestDispatcher("/app/admin/products").forward(request, response);
        return;
    }
    request.setAttribute("adminSection", adminSection);
%>
<jsp:forward page="/WEB-INF/views/admin/layout.jsp" />
