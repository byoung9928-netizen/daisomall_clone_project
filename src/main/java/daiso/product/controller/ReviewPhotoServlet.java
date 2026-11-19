package daiso.product.controller;

import daiso.product.service.ProductReviewService;
import java.io.IOException;
import java.sql.SQLException;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/product-review-photo")
public final class ReviewPhotoServlet extends HttpServlet {
    private final ProductReviewService reviews = new ProductReviewService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id < 1) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            byte[] photo = reviews.photo(id);
            if (photo == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("image/jpeg");
            response.setContentLength(photo.length);
            response.getOutputStream().write(photo);
        } catch (SQLException | NamingException error) {
            throw new ServletException("리뷰 사진 조회 실패", error);
        }
    }
}
