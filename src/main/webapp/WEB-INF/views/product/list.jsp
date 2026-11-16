<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    Integer catalogStatus = (Integer) request.getAttribute("catalogErrorStatus");
    if (catalogStatus != null) response.setStatus(catalogStatus);
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
    <script src="${pageContext.request.contextPath}/assets/js/common/product-api.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>
    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>
    <%@ include file="catalog.jspf" %>
    <%@ include file="../common/footer.jspf" %>
</body>
</html>
