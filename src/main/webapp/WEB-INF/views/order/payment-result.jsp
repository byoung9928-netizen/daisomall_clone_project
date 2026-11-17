<%@ page contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>

<!DOCTYPE html>
<html lang="ko">
<head>
    <%@ include file="../common/head.jspf" %>
</head>
<body>
    <a class="skip-link" href="#main-content">본문 바로가기</a>

    <%@ include file="../common/header.jspf" %>
    <%@ include file="../common/navigation.jspf" %>

    <main id="main-content"
          class="container shop-page"
          tabindex="-1">

        <h1 class="page-title">모의결제 결과</h1>

        <p role="status">
            <%= daiso.product.controller.ProductView.escape(
                request.getAttribute("paymentMessage")
            ) %>
        </p>

        <a class="button primary"
           href="${pageContext.request.contextPath}/app/cart">
            장바구니로
        </a>

        <a class="button"
           href="${pageContext.request.contextPath}/">
            홈으로
        </a>
    </main>

    <%@ include file="../common/footer.jspf" %>
</body>
</html>