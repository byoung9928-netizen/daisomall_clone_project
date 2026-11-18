package daiso.product.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import daiso.product.service.ProductImageStorage;

@WebServlet("/product-images/*")
public final class ProductImageServlet extends HttpServlet {
    // 상품에 연결된 업로드 사진 제공
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        String imagePath = path == null ? "" : "product-images" + path;
        if (!ProductImageStorage.isImagePath(imagePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path file = new ProductImageStorage(getServletContext()).resolve(imagePath);
        if (!Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(imagePath.endsWith(".png") ? "image/png" : "image/jpeg");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
    }
}
