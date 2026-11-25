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
    private static final String BULK = AdminProductController.class.getName() + ".bulk";
    private static final String NOTICE = "adminProductNotice";
    private static final int PAGE_SIZE = 30;
    private static final int PAGE_LINKS = 6;
    private static final int MIN_PRICE = 1000;
    private static final int MAX_PRICE = 5000;
    private static final String PRICE_RANGE = "가격은 1,000원 이상 5,000원 이하로 입력해 주세요.";
    static final Map<String, String> STATUSES = Map.of("AVAILABLE", "판매 중", "SOLD_OUT", "품절",
            "SCHEDULED", "판매 예정", "DISCONTINUED", "판매 종료");
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
        synchronized (session) {
            BulkSession state = (BulkSession) session.getAttribute(BULK + ".drafts");
            String receipt = request.getParameter("productBulkSaved");
            if (state != null && receipt != null && receipt.length() == 36) {
                request.setAttribute("adminBulkNotice", state.notices.remove(receipt));
                response.setHeader("Cache-Control", "private, no-store");
            }
        }
        return show(request, response, session, null);
    }

    @DaisoGetMapping(ROUTE + "/csv")
    public String csv(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        if (adminSession(request) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        StringBuilder csv = new StringBuilder("\ufeff");
        try (Connection conn = ProductQueries.connect()) {
            AdminProductFilter filters = AdminProductFilter.read(request, STATUSES);
            Map<String, String> categories = dao.findAdminCategories(conn, false);
            csvRow(csv, "상품 ID", "품번", "상품명", "브랜드", "카테고리", "가격", "판매 상태",
                    "온라인 재고", "등록일", "수정일");
            for (ProductDTO product : dao.findAdminProductsForExport(conn, filters)) {
                csvRow(csv, product.getProductId(), product.getProductCode(), product.getName(), product.getBrand(),
                        categories.getOrDefault(product.getCategoryId(), product.getCategoryId()), product.getPrice(),
                        STATUSES.getOrDefault(product.getStatus(), product.getStatus()), product.getStockQuantity(),
                        product.adminCreatedAt, product.adminUpdatedAt);
            }
            conn.commit();
        } catch (IllegalArgumentException error) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        } catch (NamingException | SQLException error) {
            request.getServletContext().log("상품 CSV 조회 실패", error);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return null;
        }
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=products.csv");
        response.setHeader("Cache-Control", "private, no-store");
        response.getWriter().write(csv.toString());
        response.flushBuffer();
        return null;
    }

    private static void csvRow(StringBuilder output, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i != 0) output.append(',');
            String value = cells[i] == null ? "" : cells[i].toString();
            String leading = value.stripLeading();
            if (cells[i] instanceof String && (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0
                    || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n"))) {
                value = "'" + value;
            }
            output.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        output.append("\r\n");
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
                if (!dao.lockLeafCategory(conn, product.getCategoryId())) return false;
                ProductDTO before = create ? null : dao.lockAdminProduct(conn, product.getProductId());
                if (!create && before == null) return false;
                verifyDiscontinuation(request, product.getStatus(), before);
                if (!create) product.setStockQuantity(before.getStockQuantity());
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
            Map<String, String> inventoryReturn = create ? Map.of() : OnlineInventoryController.returnParameters(request);
            if (!inventoryReturn.isEmpty()) {
                String receipt = OnlineInventoryController.markSaved(session);
                return "redirect:" + ProductView.rawUrl("", OnlineInventoryController.MAPPING,
                        ProductView.with(inventoryReturn, "saved", receipt));
            }
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
            return saveFailure(request, response, session, error, form);
        } catch (IllegalArgumentException error) {
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, error.getMessage(), form);
        } catch (SQLException error) {
            if (error.getErrorCode() == 1062) {
                return problem(request, response, session, HttpServletResponse.SC_CONFLICT, "이미 사용 중인 품번입니다.", form);
            }
            return saveFailure(request, response, session, error, form);
        } catch (NamingException error) {
            return saveFailure(request, response, session, error, form);
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
    public String bulkStatus(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.addHeader("Vary", "Accept");
        boolean json = AdminBatchResponse.wantsJson(request);
        HttpSession session = adminSession(request);
        if (session == null) {
            String message = "관리자 계정으로 로그인해 주세요.";
            return json ? AdminBatchResponse.failed(response, 403, message) : denied(request, response, message);
        }
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (!validToken(request, session)) {
            String message = "상품 관리 화면을 새로 연 후 다시 시도해 주세요.";
            return json ? AdminBatchResponse.failed(response, 403, message) : denied(request, response, message);
        }
        try {
            String receipt = ProductQueries.text(request, "bulkReceipt", 36);
            if ("cancel".equals(request.getParameter("bulkAction"))) {
                synchronized (session) {
                    bulkSession(session).previews.remove(receipt);
                }
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                response.flushBuffer();
                return null;
            }
            if (receipt.isEmpty()) {
                if (json) return AdminBatchResponse.failed(response, 400, "미리보기를 만든 뒤 변경을 확정해 주세요.");
                BulkPreview preview = previewBulk(request);
                synchronized (session) {
                    Map<String, BulkPreview> previews = bulkSession(session).previews;
                    previews.values().removeIf(saved -> saved.expires < System.currentTimeMillis());
                    rememberBulk(previews, preview.receipt, preview);
                }
                request.removeAttribute("adminBulkErrorField");
                request.setAttribute("productBulkPreview", preview);
                return show(request, response, session, null);
            }
            BulkPreview preview;
            synchronized (session) {
                preview = bulkSession(session).previews.remove(receipt);
            }
            if (preview == null || preview.expires < System.currentTimeMillis()) {
                throw new IllegalArgumentException("미리보기가 만료되었거나 이미 처리되었습니다. 다시 미리보기를 만들어 주세요.");
            }
            int updated = write(conn -> applyBulk(conn, request, administrator(session), preview));
            String message = updated == 0 ? "선택한 상품이 모두 이미 같은 값입니다."
                    : "선택한 상품 " + updated + "개의 " + preview.fieldLabel
                            + ("PRICE".equals(preview.type) ? "을" : "를") + " 변경했습니다.";
            if (json) {
                String listUrl = ProductView.rawUrl(request.getContextPath() + request.getServletPath(), ROUTE, preview.filters);
                return AdminBatchResponse.saved(response, message, listUrl);
            }
            synchronized (session) {
                rememberBulk(bulkSession(session).notices, receipt, message);
            }
            return "redirect:" + ProductView.rawUrl("", ROUTE,
                    ProductView.with(preview.filters, "productBulkSaved", receipt)) + "#product-bulk-result";
        } catch (IllegalArgumentException error) {
            if (json) return AdminBatchResponse.failed(response, 400, error.getMessage());
            request.setAttribute("adminBulkError", true);
            return problem(request, response, session, HttpServletResponse.SC_BAD_REQUEST, error.getMessage(), null);
        } catch (SQLException | NamingException error) {
            if (json) {
                request.getServletContext().log("상품 일괄 변경 실패", error);
                return AdminBatchResponse.failed(response, 503, "상품과 변경 이력을 저장하지 못했습니다. 최신 상품 목록을 확인해 주세요.");
            }
            return unavailable(request, response, error);
        }
    }

    private static BulkSession bulkSession(HttpSession session) {
        BulkSession state = (BulkSession) session.getAttribute(BULK + ".drafts");
        if (state == null) {
            state = new BulkSession();
            session.setAttribute(BULK + ".drafts", state);
        }
        return state;
    }

    private static <T> void rememberBulk(Map<String, T> values, String receipt, T value) {
        if (values.size() >= 8) values.remove(values.keySet().iterator().next());
        values.put(receipt, value);
    }

    private static final class BulkSession {
        private final Map<String, BulkPreview> previews = new LinkedHashMap<>();
        private final Map<String, String> notices = new LinkedHashMap<>();
    }

    private BulkPreview previewBulk(HttpServletRequest request) throws SQLException, NamingException {
        String type = ProductQueries.text(request, "bulkType", 12);
        request.setAttribute("adminBulkErrorField", "bulkType");
        String target;
        switch (type) {
            case "STATUS":
                request.setAttribute("adminBulkErrorField", "targetStatus");
                target = ProductQueries.text(request, "targetStatus", 20);
                if (!STATUSES.containsKey(target)) throw new IllegalArgumentException("변경할 판매 상태를 선택해 주세요.");
                break;
            case "PRICE":
                request.setAttribute("adminBulkErrorField", "targetPrice");
                String submitted = ProductQueries.text(request, "targetPrice", 10);
                if (submitted.isEmpty()) throw new IllegalArgumentException("변경할 가격을 입력해 주세요.");
                int price = number(submitted, "가격");
                if (price < MIN_PRICE || price > MAX_PRICE) throw new IllegalArgumentException(PRICE_RANGE);
                target = Integer.toString(price);
                break;
            case "CATEGORY":
                request.setAttribute("adminBulkErrorField", "targetCategory");
                target = ProductQueries.text(request, "targetCategory", 100);
                break;
            default: throw new IllegalArgumentException("일괄 변경 항목을 확인해 주세요.");
        }
        request.setAttribute("adminBulkErrorField", "selectedProduct");
        SortedSet<String> ids = selectedIds(request.getParameterValues("selectedProduct"));
        request.removeAttribute("adminBulkErrorField");
        Map<String, String> returnFilters = AdminProductFilter.read(request, STATUSES).parameters(
                ProductQueries.integer(request, "page", 1, 1, Integer.MAX_VALUE / PAGE_SIZE));
        try (Connection conn = ProductQueries.connect()) {
            Map<String, String> categories = "CATEGORY".equals(type) ? dao.findAdminCategories(conn, true) : Map.of();
            if ("CATEGORY".equals(type)) {
                if (!changes.categoryAuditReady(conn)) throw new IllegalArgumentException("카테고리 변경 이력 SQL을 확인해 주세요.");
                if (!categories.containsKey(target)) {
                    request.setAttribute("adminBulkErrorField", "targetCategory");
                    throw new IllegalArgumentException("변경할 최하위 카테고리를 선택해 주세요.");
                }
            }
            List<BulkItem> items = new java.util.ArrayList<>();
            for (String id : ids) {
                ProductDTO product = dao.findById(conn, id);
                if (product == null || !id.equals(product.getProductId())) {
                    throw new IllegalArgumentException("선택 목록에 찾을 수 없는 상품이 있습니다.");
                }
                if ("STATUS".equals(type)) {
                    request.setAttribute("adminBulkErrorField", "targetStatus");
                    verifyDiscontinuation(request, target, product);
                    request.removeAttribute("adminBulkErrorField");
                }
                String old = bulkValue(type, product);
                items.add(new BulkItem(id, product.getName(), product.getProductCode(), old, bulkLabel(type, old, categories),
                        !target.equals(old)));
            }
            conn.commit();
            return new BulkPreview(type, target, bulkLabel(type, target, categories), items, returnFilters);
        }
    }

    private int applyBulk(Connection conn, HttpServletRequest request, String administrator, BulkPreview preview)
            throws SQLException {
        if ("CATEGORY".equals(preview.type) && !dao.lockLeafCategory(conn, preview.target)) {
            throw new IllegalArgumentException("대상은 유효한 최하위 카테고리여야 합니다.");
        }
        int updated = 0;
        for (BulkItem item : preview.items) {
            ProductDTO before = dao.lockAdminProduct(conn, item.id);
            if (before == null || !item.previous.equals(bulkValue(preview.type, before))) {
                throw new IllegalArgumentException("미리보기 이후 상품이 변경되었습니다. 다시 조회해 주세요.");
            }
            if (item.previous.equals(preview.target)) continue;
            switch (preview.type) {
                case "STATUS":
                    verifyDiscontinuation(request, preview.target, before);
                    dao.updateAdminStatus(conn, item.id, preview.target);
                    changes.record(conn, administrator, Action.BULK_STATUS, before, dao.lockAdminProduct(conn, item.id));
                    break;
                case "PRICE":
                    dao.updateAdminPrice(conn, item.id, Integer.parseInt(preview.target));
                    changes.record(conn, administrator, Action.UPDATE, before, dao.lockAdminProduct(conn, item.id));
                    break;
                case "CATEGORY":
                    dao.updateAdminCategory(conn, item.id, preview.target);
                    changes.recordCategory(conn, administrator, before, preview.target);
                    break;
                default: throw new IllegalArgumentException("일괄 변경 항목을 확인해 주세요.");
            }
            updated++;
        }
        return updated;
    }

    private static String bulkValue(String type, ProductDTO product) {
        if ("PRICE".equals(type)) return Integer.toString(product.getPrice());
        return "CATEGORY".equals(type) ? product.getCategoryId() : product.getStatus();
    }

    private static String bulkLabel(String type, String value, Map<String, String> categories) {
        if ("PRICE".equals(type)) return ProductView.money(Integer.parseInt(value));
        return ("STATUS".equals(type) ? STATUSES : categories).getOrDefault(value, value);
    }

    public static final class BulkPreview {
        public final String receipt = UUID.randomUUID().toString();
        public final String targetLabel;
        public final String fieldLabel;
        public final List<BulkItem> items;
        private final Map<String, String> filters;
        private final long expires = System.currentTimeMillis() + 600_000;
        private final String type;
        private final String target;

        BulkPreview(String type, String target, String targetLabel, List<BulkItem> items,
                Map<String, String> filters) {
            this.type = type;
            this.target = target;
            this.targetLabel = targetLabel;
            this.fieldLabel = "PRICE".equals(type) ? "가격" : "CATEGORY".equals(type) ? "카테고리" : "판매 상태";
            this.items = List.copyOf(items);
            this.filters = Map.copyOf(filters);
        }
    }

    public static final class BulkItem {
        public final String name;
        public final String previousLabel;
        public final String code;
        public final boolean changed;
        private final String id;
        private final String previous;

        BulkItem(String id, String name, String code, String previous, String previousLabel, boolean changed) {
            this.id = id;
            this.name = name;
            this.previous = previous;
            this.previousLabel = previousLabel;
            this.code = code;
            this.changed = changed;
        }
    }

    private static boolean discontinuationEnabled(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getServletContext().getInitParameter("daiso.product.discontinuationEnabled"));
    }

    private static void verifyDiscontinuation(HttpServletRequest request, String requested, ProductDTO current) {
        if ("DISCONTINUED".equals(requested) && (current == null || !"DISCONTINUED".equals(current.getStatus()))
                && !discontinuationEnabled(request)) {
            throw new IllegalArgumentException("결제 승인 단계의 판매 상태 검증이 준비될 때까지 판매 종료 전환은 보류됩니다.");
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
            if (changes.categoryAuditReady(conn)) request.setAttribute("adminBulkCategories", dao.findAdminCategories(conn, true));
            request.setAttribute("adminStockFilters", AdminProductFilter.stockOptions());
            String context = request.getContextPath();
            String url = "/app" + ROUTE;
            Map<String, String> filters = filter.parameters(page);
            request.setAttribute("adminProductFilters", filters);
            request.setAttribute("adminProductStatusSummary", dao.adminStatusSummary(conn, filter));
            request.setAttribute("adminProductCsv", ProductView.url(context, url + "/csv", filter.parameters(1)));
            request.setAttribute("adminProductFirst", result.total == 0 ? 0 : (page - 1) * PAGE_SIZE + 1);
            request.setAttribute("adminProductLast", (page - 1) * PAGE_SIZE + result.products.size());

            AdminProductFilter.Sort nextPriceSort = filter.sort == AdminProductFilter.Sort.PRICE_ASC
                    ? AdminProductFilter.Sort.PRICE_DESC : AdminProductFilter.Sort.PRICE_ASC;
            request.setAttribute("adminPriceSortLink", ProductView.url(context, url,
                    ProductView.with(filter.parameters(1), "sort", nextPriceSort.name())));
            Map<String, String> historyFilters = ProductView.with(filters, "history", "1");
            request.setAttribute("adminProductHistory", ProductView.url(context, url, historyFilters));
            Map<String, String> saveFilters = new LinkedHashMap<>(filters);
            Map<String, String> inventoryReturn = form != null && !form.getOrDefault("id", "").isEmpty()
                    ? OnlineInventoryController.returnParameters(request) : Map.of();
            if (!inventoryReturn.isEmpty()) {
                saveFilters.put("fromInventory", "1");
                saveFilters.put("inventoryQuery", inventoryReturn.get("q"));
                saveFilters.put("inventoryCategory", inventoryReturn.get("category"));
                saveFilters.put("inventoryState", inventoryReturn.get("state"));
                saveFilters.put("inventoryPage", inventoryReturn.get("page"));
                saveFilters.put("inventorySort", inventoryReturn.get("sort"));
            }
            request.setAttribute("adminProductSave", ProductView.url(context, url + "/save", saveFilters));
            request.setAttribute("adminProductDelete", ProductView.url(context, url + "/delete", filters));
            request.setAttribute("adminProductBulk", ProductView.url(context, url + "/bulk-status", filters));
            if (history) {
                Map<String, String> historyQuery = new LinkedHashMap<>();
                for (String key : List.of("historyQuery", "historyActor", "historyReference", "historyType", "historyPeriod",
                        "historyStart", "historyEnd", "historyOrder")) {
                    historyQuery.put(key, ProductQueries.text(request, key, 120));
                }
                HistoryPage entries = changes.list(conn, historyId, historyBefore, historyQuery,
                        ProductQueries.integer(request, "historyPage", 1, 1, 1_000_000));
                Map<String, String> scopedHistory = ProductView.with(historyFilters, "historyId", historyId);
                scopedHistory.putAll(historyQuery);
                if (historyBefore != null) scopedHistory.put("beforeChange", historyBefore.toString());
                request.setAttribute("productHistory", entries.entries);
                request.setAttribute("productHistoryId", historyId);
                request.setAttribute("productHistoryQuery", historyQuery);
                request.setAttribute("productHistoryBase", ProductView.with(historyFilters, "historyId", historyId));
                request.setAttribute("productHistoryPagination", new InventoryPagination(entries.page, entries.pages,
                        number -> ProductView.url(context, url,
                                ProductView.with(scopedHistory, "historyPage", Integer.toString(number))) + "#product-history"));
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
            request.setAttribute("adminProductList", inventoryReturn.isEmpty() ? ProductView.url(context, url, filters)
                    : ProductView.url(context, OnlineInventoryController.ROUTE, inventoryReturn));
            request.setAttribute("productStatuses", STATUSES);
            request.setAttribute("productDiscontinuationEnabled", discontinuationEnabled(request));
            request.setAttribute(CSRF, token(session));
            return VIEW;
        } catch (IllegalArgumentException error) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("adminProductError", "이력 검색 조건을 확인해 주세요.");
            return VIEW;
        } catch (SQLException | NamingException error) {
            return unavailable(request, response, error);
        }
    }

    private String saveFailure(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Exception error, Map<String, String> form) {
        if (!form.getOrDefault("id", "").isEmpty() && !OnlineInventoryController.returnParameters(request).isEmpty()) {
            request.getServletContext().log("온라인 재고 저장 실패", error);
            return problem(request, response, session, 500,
                    "온라인 재고를 저장하지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.", form);
        }
        return unavailable(request, response, error);
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
