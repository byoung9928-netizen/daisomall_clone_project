package daiso.product.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Collectors;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/api/products/*")
public class ProductReadServlet extends HttpServlet {
    private final ProductDAO dao = new ProductDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getPathInfo();
        boolean catalog = path == null || "/".equals(path);
        if (!catalog && !"/detail".equals(path) && !"/categories".equals(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            String id = ProductQueries.text(request, "id", 100);
            ProductDAO.CatalogFilter filter = catalog ? ProductQueries.read(request) : null;
            if ("/detail".equals(path) && id.isEmpty()) {
                throw new IllegalArgumentException("Missing product ID");
            }
            String json;
            try (Connection conn = ProductQueries.connect()) {
                if (catalog) {
                    ProductDAO.CatalogPage page = dao.findCatalog(conn, filter);
                    String products = page.products.stream().map(ProductReadServlet::productJson)
                            .collect(Collectors.joining(",", "[", "]"));
                    json = "{\"items\":" + products + ",\"total\":" + page.total + "}";
                } else if ("/categories".equals(path)) {
                    json = dao.findVisibleCategories(conn).stream().map(ProductReadServlet::categoryJson)
                            .collect(Collectors.joining(",", "[", "]"));
                } else {
                    ProductDTO product = dao.findVisibleById(conn, id);
                    if (product == null) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    json = productJson(product);
                }
                conn.commit();
            }
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(json);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException | NamingException e) {
            throw new ServletException("상품 조회에 실패했습니다.", e);
        }
    }

    private static String productJson(ProductDTO product) {
        return "{\"id\":" + jsonString(product.getProductId())
                + ",\"code\":" + jsonString(product.productCode)
                + ",\"name\":" + jsonString(product.getName())
                + ",\"price\":" + product.getPrice()
                + ",\"onlineStock\":" + product.getStockQuantity()
                + ",\"categoryId\":" + jsonString(product.categoryId)
                + ",\"brand\":" + jsonString(product.brand)
                + ",\"status\":" + jsonString(product.status)
                + ",\"image\":" + jsonString(product.image)
                + ",\"description\":" + jsonString(product.description)
                + ",\"rating\":" + product.rating
                + ",\"reviewCount\":" + product.reviewCount
                + ",\"sourceKind\":" + jsonString(product.sourceKind)
                + ",\"sourceNote\":" + jsonString(product.sourceNote) + "}";
    }

    private static String categoryJson(ProductDAO.Category category) {
        return "{\"id\":" + jsonString(category.id) + ",\"parentId\":" + jsonString(category.parentId)
                + ",\"name\":" + jsonString(category.name) + ",\"level\":" + category.level
                + ",\"displayOrder\":" + category.displayOrder + "}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' || character == '\\') {
                json.append('\\').append(character);
            } else if (character < 0x20) {
                json.append(String.format("\\u%04x", (int) character));
            } else {
                json.append(character);
            }
        }
        return json.append('"').toString();
    }
}
