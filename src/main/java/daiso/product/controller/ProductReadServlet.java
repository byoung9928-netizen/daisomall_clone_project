package daiso.product.controller;

import daiso.product.dao.StoreInventoryDAO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpSession;
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
    private static final int RECENT_PAGE_SIZE = 10;
    private static final String RECENT_TOKEN = "product.recentQuickToken";
    private final StoreInventoryDAO stores = new StoreInventoryDAO();
    private final ProductDAO dao = new ProductDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getPathInfo();
        if ("/search-scope".equals(path)) {
            writeJson(response, jsonString(ProductView.searchHistoryKey(request.getSession(false))));
            return;
        }
        if ("/suggestions".equals(path) || "/recent".equals(path)) {
            try {
                if ("/recent".equals(path)) recent(request, response);
                else suggestions(request, response);
            } catch (IllegalArgumentException error) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            } catch (SQLException | NamingException error) {
                request.getServletContext().log("상품 검색어 또는 최근 본 상품 조회 실패", error);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
            return;
        }
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
                    String products = page.products.stream().map(product -> productJson(request.getContextPath(), product))
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
                    json = productJson(request.getContextPath(), product);
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

    private void suggestions(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, NamingException {
        String term = ProductQueries.text(request, "q", 100);
        String context = request.getContextPath();
        try (Connection connection = ProductQueries.connect()) {
            String json = dao.searchSuggestions(connection, term).stream().map(match ->
                    "{\"term\":" + jsonString(match.term)
                    + ",\"category\":" + jsonString(match.categoryId)
                    + ",\"image\":" + jsonString(match.image == null ? null : ProductView.imageUrl(context, match.image))
                    + "}").collect(Collectors.joining(",", "[", "]"));
            connection.commit();
            writeJson(response, json);
        }
    }

    private void recent(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, NamingException {
        boolean summary = "1".equals(request.getParameter("summary"));
        int offset = ProductQueries.integer(request, "offset", 0, 0, 50);
        HttpSession session = request.getSession(false);
        String member = ProductActivityController.member(session);
        List<String> ids = List.copyOf(ProductActivityController.snapshot(session, member).keySet());
        if (ids.isEmpty()) {
            writeJson(response, "{\"items\":[],\"more\":false}");
            return;
        }
        int limit = summary ? 1 : RECENT_PAGE_SIZE;
        String context = request.getContextPath();
        try (Connection connection = ProductQueries.connect()) {
            List<ProductDTO> page = dao.recentQuickPage(connection, ids, limit + 1, offset);
            List<ProductDTO> shown = page.subList(0, Math.min(limit, page.size()));
            List<String> shownIds = shown.stream().map(ProductDTO::getProductId).collect(Collectors.toList());
            Set<String> pickupIds = Set.of();
            if (!summary && !shownIds.isEmpty()) {
                try {
                    pickupIds = stores.pickupProductIds(connection, shownIds);
                } catch (SQLException error) {
                    request.getServletContext().log("최근 본 상품의 매장 재고 조회 실패", error);
                }
            }
            var facts = summary ? java.util.Map.<String, ProductDAO.CardFacts>of()
                    : dao.cardFacts(connection, shownIds, false);
            Set<String> pickup = pickupIds;
            String items = shown.stream().map(product -> {
                var fact = facts.get(product.getProductId());
                return "{\"id\":" + jsonString(product.getProductId()) + ",\"name\":" + jsonString(product.getName())
                        + ",\"image\":" + jsonString(ProductView.imageUrl(context, product.getImage()))
                        + ",\"price\":" + product.getPrice() + ",\"delivery\":" + ProductView.available(product)
                        + ",\"pickup\":" + (ProductView.pickupEligible(product) && pickup.contains(product.getProductId()))
                        + ",\"soldOut\":" + ProductView.temporarilySoldOut(product)
                        + ",\"newProduct\":" + (fact != null && fact.newProduct) + "}";
            }).collect(Collectors.joining(",", "[", "]"));
            connection.commit();
            String token = summary ? null : recentToken(session, member);
            writeJson(response, "{\"items\":" + items + ",\"more\":" + (page.size() > limit)
                    + ",\"token\":" + jsonString(token) + "}");
        }
    }

    private static String recentToken(HttpSession session, String member) {
        synchronized (session) {
            if (!Objects.equals(member, ProductActivityController.member(session))) return null;
            Object existing = session.getAttribute(RECENT_TOKEN);
            if (existing instanceof RecentToken && Objects.equals(member, ((RecentToken) existing).member)) {
                return ((RecentToken) existing).value;
            }
            String value = UUID.randomUUID().toString();
            session.setAttribute(RECENT_TOKEN, new RecentToken(member, value));
            return value;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        if (!"/recent".equals(request.getPathInfo())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        String[] submitted = request.getParameterValues("csrf");
        if (session == null || submitted == null || submitted.length != 1 || submitted[0].length() != 36) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        synchronized (session) {
            String member = ProductActivityController.member(session);
            Object allowed = session.getAttribute(RECENT_TOKEN);
            if (!(allowed instanceof RecentToken) || !Objects.equals(member, ((RecentToken) allowed).member)
                    || !MessageDigest.isEqual(((RecentToken) allowed).value.getBytes(StandardCharsets.UTF_8),
                            submitted[0].getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            try {
                String product = ProductQueries.text(request, "productId", 100);
                String action = ProductQueries.text(request, "action", 10);
                if ("clear".equals(action) && product.isEmpty()) {
                    ProductActivityController.removeDisplayed(session, member, Set.of());
                } else if ("remove".equals(action) && !product.isEmpty()
                        && ProductActivityController.snapshot(session, member).containsKey(product)) {
                    ProductActivityController.removeDisplayed(session, member, Set.of(product));
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } catch (IllegalArgumentException error) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }

    private static void writeJson(HttpServletResponse response, String body) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "private, no-store");
        response.getWriter().write(body);
    }

    private static final class RecentToken implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final String member;
        final String value;

        RecentToken(String member, String value) {
            this.member = member;
            this.value = value;
        }
    }

    private static String productJson(String context, ProductDTO product) {
        return "{\"id\":" + jsonString(product.getProductId())
                + ",\"code\":" + jsonString(product.productCode)
                + ",\"name\":" + jsonString(product.getName())
                + ",\"price\":" + product.getPrice()
                + ",\"onlineStock\":" + product.getStockQuantity()
                + ",\"categoryId\":" + jsonString(product.categoryId)
                + ",\"brand\":" + jsonString(product.brand)
                + ",\"status\":" + jsonString(product.status)
                + ",\"image\":" + jsonString(product.image)
                + ",\"imageUrl\":" + jsonString(ProductView.imageUrl(context, product.getImage()))
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
