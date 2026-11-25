package daiso.product.controller;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final class AdminBatchResponse {
    private AdminBatchResponse() {}

    static boolean wantsJson(HttpServletRequest request) {
        return "application/json".equalsIgnoreCase(request.getHeader("Accept"));
    }

    static String saved(HttpServletResponse response, String message, String listUrl) throws IOException {
        return write(response, HttpServletResponse.SC_OK, message, listUrl);
    }

    static String failed(HttpServletResponse response, int status, String message) throws IOException {
        return write(response, status, message, null);
    }

    private static String write(HttpServletResponse response, int status, String message, String listUrl)
            throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("success", listUrl != null);
        result.addProperty("message", message);
        if (listUrl != null) result.addProperty("listUrl", listUrl);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.toString());
        response.flushBuffer();
        return null;
    }
}
