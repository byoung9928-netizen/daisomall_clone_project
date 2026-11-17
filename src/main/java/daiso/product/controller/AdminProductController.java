package daiso.product.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import daiso.common.annotation.DaisoController;
import daiso.common.annotation.DaisoGetMapping;
import daiso.common.annotation.DaisoPostMapping;
import daiso.common.annotation.DaisoResponseBody;
import daiso.product.controller.ProductDAO.CatalogPage;

@DaisoController
public final class AdminProductController {
    private static final String ROUTE = "/admin/products";
    private static final String VIEW = "admin/products";
    private static final String CSRF = "adminProductCsrf";
    private static final String NOTICE = "adminProductNotice";
    private static final int PAGE_SIZE = 30;
    private static final int PAGE_LINKS = 6;
    private static final int MIN_PRICE = 1000;
    private static final int MAX_PRICE = 5000;
    private static final String PRICE_RANGE = "가격은 1,000원 이상 5,000원 이하로 입력해 주세요.";
    private static final Map<String, String> STATUSES = Map.of("AVAILABLE", "판매 중", "SOLD_OUT", "품절",
            "SCHEDULED", "판매 예정");
    private final ProductDAO dao = new ProductDAO();

    @DaisoGetMapping(ROUTE)
    public String list(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = adminSession(request);
        if (session == null) return denied(request, response, "관리자 계정으로 로그인해 주세요.");

        Object notice = session.getAttribute(NOTICE);
        session.removeAttribute(NOTICE);
        request.setAttribute(NOTICE, notice);
        return show(request, response, session, null);
    }

    @DaisoGetMapping(ROUTE + "/check-code")
    @DaisoResponseBody
    public String checkCode(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (adminSession(request) == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "";
        }
        try {
            String code = ProductQueries.text(request, "code", 100);
            String id = ProductQueries.text(request, "id", 100);
            if (code.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return "";
            }
            try (Connection conn = ProductQueries.connect()) {
                boolean exists = dao.adminProductCodeExists(conn, code, id);
                conn.commit();
                return exists ? "duplicate" : "available";
            }
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "";
        } catch (SQLException | NamingException error) {
            request.getServletContext().log("상품 품번 확인 실패", error);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return "";
        }
    }

    @DaisoPostMapping(ROUTE + "/save")
    public String save(HttpServletRequest request, HttpServletResponse response) throws java.io.UnsupportedEncodingException {
        HttpSession session = adminSession(request);
        if (session == null) return denied(request, response, "관리자 계정으로 로그인해 주세요.");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (!validToken(request, session)) return denied(request, response, "상품 관리 화면을 새로 연 후 다시 시도해 주세요.");

        Map<String, String> form = productForm(request);
        try {
            boolean create = form.get("id").isEmpty();
            ProductDTO product = product(form);
            try (Connection conn = writableConnection()) {
                if (!dao.saveAdminProduct(conn, product, create)) {
                    return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST,
                            "상품과 최하위 카테고리를 확인해 주세요.", form);
                }
            }
            session.setAttribute(NOTICE, create ? "상품을 등록했습니다." : "상품을 수정했습니다.");
            return "redirect:" + ROUTE;
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, error.getMessage(), form);
        } catch (SQLException error) {
            if (error.getErrorCode() == 1062) {
                return problem(request, response, session, HttpServletResponse.SC_CONFLICT, "이미 사용 중인 품번입니다.", form);
            }
            return unavailable(request, response, error);
        } catch (NamingException error) {
            return unavailable(request, response, error);
        }
    }

    @DaisoPostMapping(ROUTE + "/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) throws java.io.UnsupportedEncodingException {
        HttpSession session = adminSession(request);
        if (session == null) return denied(request, response, "관리자 계정으로 로그인해 주세요.");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (!validToken(request, session)) return denied(request, response, "상품 관리 화면을 새로 연 후 다시 시도해 주세요.");
        try {
            String id = ProductQueries.text(request, "id", 100);
            if (id.isEmpty()) throw new IllegalArgumentException();
            try (Connection conn = writableConnection()) {
                if (!dao.deleteAdminProduct(conn, id)) {
                    return problem(request, response, session, HttpServletResponse.SC_NOT_FOUND, "상품을 찾을 수 없습니다.", null);
                }
            }
            session.setAttribute(NOTICE, "상품을 삭제했습니다.");
            return "redirect:" + ROUTE;
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, "삭제할 상품을 확인해 주세요.", null);
        } catch (SQLException error) {
            if (error.getErrorCode() == 1451) {
                return problem(request, response, session, HttpServletResponse.SC_CONFLICT,
                        "장바구니나 주문에 연결된 상품은 삭제할 수 없습니다. 판매 상태를 변경할 수 있습니다.", null);
            }
            return unavailable(request, response, error);
        } catch (NamingException error) {
            return unavailable(request, response, error);
        }
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Map<String, String> form) {
        String query = "";
        String editId = "";
        String deleteId = "";
        int page = 1;
        try {
            query = ProductQueries.text(request, "query", 120);
            page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
            editId = ProductQueries.text(request, "edit", 100);
            deleteId = ProductQueries.text(request, "delete", 100);
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("adminProductError", "검색 조건을 확인해 주세요.");
        }
        try (Connection conn = ProductQueries.connect()) {
            CatalogPage result = dao.findAdminProducts(conn, query, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            int pages = Math.max(1, (result.total + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page > pages) {
                page = pages;
                result = dao.findAdminProducts(conn, query, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            }
            if (!editId.isEmpty() || !deleteId.isEmpty()) {
                ProductDTO selected = dao.findById(conn, editId.isEmpty() ? deleteId : editId);
                if (selected == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    request.setAttribute("adminProductError", "상품을 찾을 수 없습니다.");
                } else if (!editId.isEmpty()) {
                    form = formFor(selected);
                } else {
                    request.setAttribute("deleteProduct", selected);
                }
            } else if ("1".equals(request.getParameter("new")) && form == null) {
                form = Map.of("status", "AVAILABLE", "price", Integer.toString(MIN_PRICE), "stock", "0");
            }
            if (form != null) {
                request.setAttribute("productForm", form);
                request.setAttribute("productCategories", dao.findAdminCategories(conn));
                request.setAttribute("adminMinPrice", MIN_PRICE);
                request.setAttribute("adminMaxPrice", MAX_PRICE);
                request.setAttribute("adminPriceRange", PRICE_RANGE);
            }
            conn.commit();
            String context = request.getContextPath();
            String url = "/app" + ROUTE;
            Map<String, String> filters = Map.of("query", query, "page", Integer.toString(page));
            Map<ProductDTO, Map<String, String>> rows = new LinkedHashMap<>();
            for (ProductDTO product : result.products) {
                rows.put(product, Map.of(
                        "edit", ProductView.url(context, url, ProductView.with(filters, "edit", product.getProductId())),
                        "delete", ProductView.url(context, url, ProductView.with(filters, "delete", product.getProductId()))));
            }
            request.setAttribute("productRows", rows);
            request.setAttribute("adminQuery", query);
            request.setAttribute("adminProductCount", result.total);
            Map<Integer, String> pageLinks = new LinkedHashMap<>();
            int firstPage = ((page - 1) / PAGE_LINKS) * PAGE_LINKS + 1;
            for (int number = firstPage; number <= Math.min(firstPage + PAGE_LINKS - 1, pages); number++) {
                pageLinks.put(number, ProductView.url(context, url, ProductView.with(filters, "page", Integer.toString(number))));
            }
            request.setAttribute("adminProductPage", page);
            request.setAttribute("adminProductPageLinks", pageLinks);
            request.setAttribute("adminProductPrev", page > 1 ? ProductView.url(context, url,
                    ProductView.with(filters, "page", Integer.toString(page - 1))) : null);
            request.setAttribute("adminProductNext", page < pages ? ProductView.url(context, url,
                    ProductView.with(filters, "page", Integer.toString(page + 1))) : null);
            request.setAttribute("adminProductNew", ProductView.url(context, url, ProductView.with(filters, "new", "1")));
            request.setAttribute("adminProductList", ProductView.url(context, url, filters));
            request.setAttribute("productStatuses", STATUSES);
            request.setAttribute(CSRF, token(session));
            return VIEW;
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
    }

    private String problem(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            int status, String message, Map<String, String> form) {
        response.setStatus(status);
        request.setAttribute("adminProductError", message);
        return show(request, response, session, form);
    }

    private static String denied(HttpServletRequest request, HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("adminProductBlocked", message);
        return VIEW;
    }

    private static String unavailable(HttpServletRequest request, HttpServletResponse response, Exception error) {
        request.getServletContext().log("관리자 상품 처리 실패", error);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        request.setAttribute("adminProductBlocked", "상품을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        return VIEW;
    }

    private static HttpSession adminSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("member_id") != null && "ADMIN".equals(session.getAttribute("role"))
                ? session : null;
    }

    private static String token(HttpSession session) {
        synchronized (session) {
            String value = (String) session.getAttribute(CSRF);
            if (value == null) {
                value = UUID.randomUUID().toString();
                session.setAttribute(CSRF, value);
            }
            return value;
        }
    }

    private static boolean validToken(HttpServletRequest request, HttpSession session) {
        String expected = (String) session.getAttribute(CSRF);
        String submitted = request.getParameter("csrf");
        return expected != null && submitted != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
    }

    private static Connection writableConnection() throws SQLException, NamingException {
        DataSource source = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        return source.getConnection();
    }

    private static Map<String, String> productForm(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : List.of("id", "code", "name", "brand", "category", "price", "stock", "status", "image", "description")) {
            String value = request.getParameter(field);
            values.put(field, value == null ? "" : value.strip());
        }
        return values;
    }

    private static Map<String, String> formFor(ProductDTO product) {
        return Map.of("id", product.getProductId(), "code", product.getProductCode(), "name", product.getName(),
                "brand", product.getBrand(), "category", product.getCategoryId(), "price", Integer.toString(product.getPrice()),
                "stock", Integer.toString(product.getStockQuantity()), "status", product.getStatus(),
                "image", product.getImage() == null ? "" : product.getImage(),
                "description", product.getDescription() == null ? "" : product.getDescription());
    }

    private static ProductDTO product(Map<String, String> form) {
        ProductDTO product = new ProductDTO();
        String id = field(form, "id", 100, false);
        product.setProductId(id.isEmpty() ? "ADMIN-" + UUID.randomUUID() : id);
        product.setProductCode(field(form, "code", 100, true));
        product.setName(field(form, "name", 255, true));
        product.setBrand(field(form, "brand", 100, false));
        product.setCategoryId(field(form, "category", 100, true));
        int price;
        try {
            price = Integer.parseInt(form.get("price"));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(PRICE_RANGE);
        }
        if (price < MIN_PRICE || price > MAX_PRICE) throw new IllegalArgumentException(PRICE_RANGE);
        product.setPrice(price);
        product.setStockQuantity(number(form.get("stock"), "온라인 재고"));
        String status = form.get("status");
        if (!STATUSES.containsKey(status)) throw new IllegalArgumentException("판매 상태를 선택해 주세요.");
        product.setStatus(status);
        String image = field(form, "image", 1000, false);
        if (!image.isEmpty() && !validImage(image)) {
            throw new IllegalArgumentException("이미지는 HTTPS URL 또는 assets/images/ 아래 경로를 입력해 주세요.");
        }
        product.setImage(image.isEmpty() ? null : image);
        product.setDescription(field(form, "description", 2000, false));
        return product;
    }

    private static String field(Map<String, String> form, String name, int maxLength, boolean required) {
        String value = form.get(name);
        if ((required && value.isEmpty()) || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException("필수 항목과 입력 길이를 확인해 주세요.");
        }
        return value;
    }

    private static int number(String input, String label) {
        try {
            int value = Integer.parseInt(input);
            if (value >= 0) return value;
        } catch (NumberFormatException ignored) {
            // Use the same validation message for missing, malformed and out-of-range values.
        }
        throw new IllegalArgumentException(label + "은 0 이상의 정수로 입력해 주세요.");
    }

    private static boolean validImage(String value) {
        try {
            URI image = URI.create(value);
            if ("https".equalsIgnoreCase(image.getScheme()) && image.getHost() != null && image.getUserInfo() == null) {
                return true;
            }
            return image.getScheme() == null && image.getRawAuthority() == null && image.getPath() != null
                    && image.getPath().startsWith("assets/images/") && !image.getPath().contains("\\")
                    && !List.of(image.getPath().split("/")).contains("..");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
