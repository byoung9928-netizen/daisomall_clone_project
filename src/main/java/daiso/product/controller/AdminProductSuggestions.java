package daiso.product.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final class AdminProductSuggestions {
    @FunctionalInterface
    interface Lookup {
        void load(Connection connection, JsonArray items) throws SQLException;
    }

    static String respond(HttpServletRequest request, HttpServletResponse response, Lookup lookup) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JsonArray items = new JsonArray();
        if (AdminProductController.adminSession(request) == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } else {
            try (Connection connection = ProductQueries.connect()) {
                lookup.load(connection, items);
                connection.commit();
            } catch (IllegalArgumentException error) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } catch (SQLException | NamingException error) {
                request.getServletContext().log("관리자 상품 자동완성 조회 실패", error);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        }
        response.getWriter().write(response.getStatus() == HttpServletResponse.SC_OK ? items.toString() : "[]");
        response.flushBuffer();
        return null;
    }

    static void add(JsonArray items, String context, String id, String name, String code, String image,
            int price, int stock, String stockStatus) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("name", name);
        item.addProperty("code", code);
        item.addProperty("price", price);
        item.addProperty("stock", stock);
        item.addProperty("stockStatus", stockStatus);
        item.addProperty("image", ProductView.imageUrl(context, image));
        items.add(item);
    }

    private AdminProductSuggestions() {}
}
