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
import java.util.SortedSet;
import java.util.TreeSet;

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
import daiso.product.service.ProductImageStorage;
import daiso.product.controller.ProductDAO.CatalogPage;
import daiso.product.controller.AdminProductChanges.Action;
import daiso.product.controller.AdminProductChanges.HistoryPage;

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
    private final ProductDetailImages detailImages = new ProductDetailImages();
    private final OfficialProductDetails officialDetails = new OfficialProductDetails();
    private final AdminProductChanges changes = new AdminProductChanges();

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
        Map<String, String> form = new LinkedHashMap<>();
        ProductImageStorage imageStorage = new ProductImageStorage(request.getServletContext());
        List<String> uploadedImages = new java.util.ArrayList<>();
        boolean saved = false;
        try {
            java.util.Collection<javax.servlet.http.Part> parts = request.getContentType() != null
                    && request.getContentType().startsWith("multipart/form-data") ? request.getParts() : List.of();
            if (!validToken(request, session)) return denied(request, response, "상품 관리 화면을 새로 연 후 다시 시도해 주세요.");
            form = productForm(request);
            if (!"1".equals(request.getParameter("previewConfirmed"))) {
                throw new IllegalArgumentException("고객 상세 미리보기를 확인한 뒤 저장해 주세요.");
            }
            String imageOrder = ProductQueries.text(request, "detailImageOrder", 250);
            String imageBaseline = ProductQueries.text(request, "detailImageBaseline", 210);
            List<javax.servlet.http.Part> covers = imageFiles(parts, "imageFile");
            List<javax.servlet.http.Part> details = imageFiles(parts, "detailImages");
            if (covers.size() > 1 || details.size() > ProductDetailImages.LIMIT
                    || java.util.stream.Stream.concat(covers.stream(), details.stream())
                            .mapToLong(javax.servlet.http.Part::getSize).sum() > ProductImageStorage.MAX_BYTES) {
                throw new IllegalArgumentException("상세 이미지 최대 10장, 새로 첨부하는 사진은 합계 5MB까지 저장할 수 있습니다.");
            }
            boolean create = form.get("id").isEmpty();
            Map<String, String> fields = new LinkedHashMap<>(form);
            if (!covers.isEmpty()) fields.put("image", "");
            ProductDTO product = product(fields);
            if (!covers.isEmpty()) {
                String path = imageStorage.save(covers.get(0));
                uploadedImages.add(path);
                product.setImage(path);
            }
            List<String> detailUploads = new java.util.ArrayList<>();
            for (javax.servlet.http.Part detail : details) {
                String path = imageStorage.save(detail);
                uploadedImages.add(path);
                detailUploads.add(path);
            }
            saved = write(conn -> {
                ProductDTO before = create ? null : dao.lockAdminProduct(conn, product.getProductId());
                if (!create && before == null) return false;
                Map<Long, String> currentImages = create ? Map.of() : detailImages.list(conn, product.getProductId());
                if (!ProductDetailImages.baseline(currentImages).equals(imageBaseline)) {
                    throw new IllegalArgumentException("다른 작업에서 상세 이미지가 변경되었습니다. 현재 목록을 다시 확인해 주세요.");
                }
                List<String> orderedImages = ProductDetailImages.order(imageOrder, currentImages, detailUploads);
                if (!dao.saveAdminProduct(conn, product, create)) return false;
                if (!List.copyOf(currentImages.values()).equals(orderedImages)) {
                    detailImages.replace(conn, product.getProductId(), orderedImages);
                }
                changes.record(conn, administrator(session), create ? Action.CREATE : Action.UPDATE, before, product);
                return true;
            });
            if (!saved) return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST,
                    "상품과 최하위 카테고리를 확인해 주세요.", form);
            session.setAttribute(NOTICE, create ? "상품을 등록했습니다." : "상품을 수정했습니다.");
            return redirectToList(request);
        } catch (IllegalStateException error) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            request.setAttribute("adminProductBlocked", "사진은 5MB 이하의 JPG 또는 PNG 파일을 선택해 주세요.");
            return VIEW;
        } catch (javax.servlet.ServletException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("adminProductBlocked", "사진 업로드 요청을 확인해 주세요.");
            return VIEW;
        } catch (java.io.IOException error) {
            return unavailable(request, response, error);
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, error.getMessage(), form);
        } catch (SQLException error) {
            if (error.getErrorCode() == 1062) {
                return problem(request, response, session, HttpServletResponse.SC_CONFLICT, "이미 사용 중인 품번입니다.", form);
            }
            return unavailable(request, response, error);
        } catch (NamingException error) {
            return unavailable(request, response, error);
        } finally {
            if (!saved) {
                for (String path : uploadedImages) {
                    try {
                        imageStorage.discard(path);
                    } catch (java.io.IOException error) {
                        request.getServletContext().log("저장되지 않은 상품 사진 정리 실패", error);
                    }
                }
            }
        }
    }

    private static List<javax.servlet.http.Part> imageFiles(java.util.Collection<javax.servlet.http.Part> parts, String name) {
        return parts.stream().filter(part -> name.equals(part.getName()) && part.getSubmittedFileName() != null
                && !part.getSubmittedFileName().isBlank()).collect(java.util.stream.Collectors.toList());
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
            boolean deleted = write(conn -> {
                ProductDTO before = dao.lockAdminProduct(conn, id);
                if (before == null || !dao.deleteAdminProduct(conn, id)) return false;
                changes.record(conn, administrator(session), Action.DELETE, before, null);
                return true;
            });
            if (!deleted) return problem(request, response, session, HttpServletResponse.SC_NOT_FOUND,
                    "상품을 찾을 수 없습니다.", null);
            session.setAttribute(NOTICE, "상품을 삭제했습니다.");
            return redirectToList(request);
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, "삭제할 상품을 확인해 주세요.", null);
        } catch (SQLException error) {
            if (error.getErrorCode() == 1451) {
                request.setAttribute("adminProductRelatedId", request.getParameter("id").strip());
                return problem(request, response, session, HttpServletResponse.SC_CONFLICT,
                        "주문·장바구니·찜·리뷰에 연결된 상품은 삭제할 수 없습니다. 판매 상태를 변경해 주세요.", null);
            }
            return unavailable(request, response, error);
        } catch (NamingException error) {
            return unavailable(request, response, error);
        }
    }

    @DaisoPostMapping(ROUTE + "/bulk-status")
    public String bulkStatus(HttpServletRequest request, HttpServletResponse response)
            throws java.io.UnsupportedEncodingException {
        HttpSession session = adminSession(request);
        if (session == null) return denied(request, response, "관리자 계정으로 로그인해 주세요.");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (!validToken(request, session)) return denied(request, response, "상품 관리 화면을 새로 연 후 다시 시도해 주세요.");
        try {
            String status = ProductQueries.text(request, "targetStatus", 20);
            if (!STATUSES.containsKey(status)) throw new IllegalArgumentException("변경할 판매 상태를 선택해 주세요.");
            SortedSet<String> ids = selectedIds(request.getParameterValues("selectedProduct"));
            int updated = write(conn -> {
                int count = 0;
                for (String id : ids) {
                    ProductDTO before = dao.lockAdminProduct(conn, id);
                    if (before == null || !before.getProductId().equals(id)) {
                        throw new IllegalArgumentException("선택 목록에 삭제됐거나 찾을 수 없는 상품이 있습니다.");
                    }
                    if (status.equals(before.getStatus())) continue;
                    dao.updateAdminStatus(conn, id, status);
                    ProductDTO after = dao.lockAdminProduct(conn, id);
                    changes.record(conn, administrator(session), Action.BULK_STATUS, before, after);
                    count++;
                }
                return count;
            });
            session.setAttribute(NOTICE, ids.size() + "개 선택 · " + updated + "개 상품의 판매 상태를 변경했습니다.");
            return redirectToList(request);
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, error.getMessage(), null);
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
    }

    private static SortedSet<String> selectedIds(String[] values) {
        if (values == null || values.length == 0 || values.length > PAGE_SIZE) {
            throw new IllegalArgumentException("현재 페이지의 상품을 1~30개 선택해 주세요.");
        }
        SortedSet<String> ids = new TreeSet<>();
        for (String id : values) {
            if (id.isBlank() || id.codePointCount(0, id.length()) > 100
                    || !id.equals(id.strip()) || id.codePoints().anyMatch(Character::isISOControl) || !ids.add(id)) {
                throw new IllegalArgumentException("선택한 상품을 확인해 주세요.");
            }
        }
        return ids;
    }

    private String show(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Map<String, String> form) {
        AdminProductFilter filter = AdminProductFilter.defaults();
        String editId = "";
        String deleteId = "";
        String historyId = "";
        Long historyBefore = null;
        int page = 1;
        boolean history = "1".equals(request.getParameter("history"));
        try {
            filter = AdminProductFilter.read(request, STATUSES);
            page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
            editId = ProductQueries.text(request, "edit", 100);
            deleteId = ProductQueries.text(request, "delete", 100);
            if (history) {
                historyId = ProductQueries.text(request, "historyId", 100);
                String before = ProductQueries.text(request, "beforeChange", 19);
                if (!before.isEmpty()) {
                    historyBefore = Long.valueOf(before);
                    if (historyBefore < 1) throw new IllegalArgumentException();
                }
            }
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("adminProductError", "검색 조건을 확인해 주세요.");
        }
        try (Connection conn = ProductQueries.connect()) {
            CatalogPage result = dao.findAdminProducts(conn, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
            int pages = Math.max(1, (result.total + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page > pages) {
                page = pages;
                result = dao.findAdminProducts(conn, filter, PAGE_SIZE, (page - 1) * PAGE_SIZE);
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
                String productId = form.getOrDefault("id", "");
                Map<Long, String> images = productId.isEmpty() ? Map.of() : detailImages.list(conn, productId);
                request.setAttribute("adminDetailImages", images);
                request.setAttribute("adminDetailImageBaseline", ProductDetailImages.baseline(images));
                if (!productId.isEmpty()) {
                    ProductDTO current = dao.findById(conn, productId);
                    if (current != null) {
                        request.setAttribute("adminOfficialCode", current.getProductCode());
                        request.setAttribute("adminOfficialDetail", officialDetails.find(request.getServletContext(), current));
                    }
                }
                request.setAttribute("productCategories", dao.findAdminCategories(conn, true));
                request.setAttribute("adminMinPrice", MIN_PRICE);
                request.setAttribute("adminMaxPrice", MAX_PRICE);
                request.setAttribute("adminPriceRange", PRICE_RANGE);
            }
            request.setAttribute("adminCategoryFilters", dao.findAdminCategories(conn, false));
            request.setAttribute("adminStockFilters", AdminProductFilter.stockOptions());
            String context = request.getContextPath();
            String url = "/app" + ROUTE;
            Map<String, String> filters = filter.parameters(page);
            request.setAttribute("adminProductFilters", filters);
            AdminProductFilter.Sort nextPriceSort = filter.sort == AdminProductFilter.Sort.PRICE_ASC
                    ? AdminProductFilter.Sort.PRICE_DESC : AdminProductFilter.Sort.PRICE_ASC;
            request.setAttribute("adminPriceSortLink", ProductView.url(context, url,
                    ProductView.with(filter.parameters(1), "sort", nextPriceSort.name())));
            Map<String, String> historyFilters = ProductView.with(filters, "history", "1");
            request.setAttribute("adminProductHistory", ProductView.url(context, url, historyFilters));
            request.setAttribute("adminProductSave", ProductView.url(context, url + "/save", filters));
            request.setAttribute("adminProductDelete", ProductView.url(context, url + "/delete", filters));
            request.setAttribute("adminProductBulk", ProductView.url(context, url + "/bulk-status", filters));
            if (history) {
                HistoryPage entries = changes.list(conn, historyId, historyBefore);
                Map<String, String> scopedHistory = ProductView.with(historyFilters, "historyId", historyId);
                request.setAttribute("productHistory", entries.entries);
                request.setAttribute("productHistoryId", historyId);
                if (entries.next != null) request.setAttribute("productHistoryOlder", ProductView.url(context, url,
                        ProductView.with(scopedHistory, "beforeChange", entries.next.toString())));
            }
            String relatedId = (String) request.getAttribute("adminProductRelatedId");
            if (relatedId != null) {
                request.setAttribute("adminProductStatusLink", ProductView.url(context, url,
                        ProductView.with(filters, "edit", relatedId)));
            }
            conn.commit();
            Map<ProductDTO, Map<String, String>> rows = new LinkedHashMap<>();
            for (ProductDTO product : result.products) {
                rows.put(product, Map.of(
                        "edit", ProductView.url(context, url, ProductView.with(filters, "edit", product.getProductId())),
                        "delete", ProductView.url(context, url, ProductView.with(filters, "delete", product.getProductId())),
                        "history", ProductView.url(context, url, ProductView.with(historyFilters, "historyId", product.getProductId()))));
            }
            request.setAttribute("productRows", rows);
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

    static HttpSession adminSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("member_id") != null && "ADMIN".equals(session.getAttribute("role"))
                ? session : null;
    }

    static String token(HttpSession session) {
        synchronized (session) {
            String value = (String) session.getAttribute(CSRF);
            if (value == null) {
                value = UUID.randomUUID().toString();
                session.setAttribute(CSRF, value);
            }
            return value;
        }
    }

    static boolean validToken(HttpServletRequest request, HttpSession session) {
        String expected = (String) session.getAttribute(CSRF);
        String submitted = request.getParameter("csrf");
        return expected != null && submitted != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
    }

    private static String redirectToList(HttpServletRequest request) {
        try {
            AdminProductFilter filter = AdminProductFilter.read(request, STATUSES);
            int page = ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE);
            return "redirect:" + ProductView.rawUrl("", ROUTE, filter.parameters(page));
        } catch (IllegalArgumentException ignored) {
            return "redirect:" + ROUTE;
        }
    }

    private static String administrator(HttpSession session) {
        return session.getAttribute("member_id").toString();
    }

    private static <T> T write(AdminWrite<T> action) throws SQLException, NamingException {
        DataSource source = InitialContext.doLookup("java:comp/env/jdbc/mysqlDB");
        try (Connection conn = source.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        }
    }

    @FunctionalInterface
    private interface AdminWrite<T> {
        T run(Connection conn) throws SQLException;
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
        if (ProductImageStorage.isImagePath(value)) return true;
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
