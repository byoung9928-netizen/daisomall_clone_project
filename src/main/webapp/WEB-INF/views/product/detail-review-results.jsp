<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="daiso.product.controller.ProductDTO,daiso.product.controller.ProductView,daiso.product.dto.ProductReviewPage,java.util.Map" %>
<%
    String context = request.getContextPath();
    ProductDTO product = (ProductDTO) request.getAttribute("product");
    ProductReviewPage detailReviews = (ProductReviewPage) request.getAttribute("detailReviews");
    String selectedReviewSort = (String) request.getAttribute("detailReviewSort");
    if (product != null && detailReviews != null) {
%>
    <%@ include file="detail-review-results.jspf" %>
<% } %>
