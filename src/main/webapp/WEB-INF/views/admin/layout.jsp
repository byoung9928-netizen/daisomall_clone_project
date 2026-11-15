<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String section = String.valueOf(request.getAttribute("adminSection"));
    if (!section.matches("dashboard|categories|products|inventory|stores|orders|users")) { response.sendError(404); return; }
    String adminView = "/WEB-INF/views/admin/" + section + ".jsp";
%>
<jsp:forward page="<%= adminView %>" />
