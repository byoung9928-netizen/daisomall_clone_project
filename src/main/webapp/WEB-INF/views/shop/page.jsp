<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String shopPage = String.valueOf(request.getAttribute("pageName"));
    String originalUri = (String) request.getAttribute("javax.servlet.forward.request_uri");
    if (originalUri == null) originalUri = request.getRequestURI();
    String viewPath;
    switch (shopPage) {
        case "login": case "signup": case "mypage": case "profile": viewPath = "member/" + shopPage + ".jsp"; break;
        case "catalog": viewPath = "product/" + (originalUri.endsWith("/search.jsp") ? "search" : "list") + ".jsp"; break;
        case "product": viewPath = "product/detail.jsp"; break;
        case "cart": case "checkout": case "orders": case "payment-success": case "payment-failure": viewPath = "order/" + shopPage + ".jsp"; break;
        case "order": viewPath = "order/detail.jsp"; break;
        default: response.sendError(404); return;
    }
%>
<jsp:forward page='<%= "/WEB-INF/views/" + viewPath %>' />
