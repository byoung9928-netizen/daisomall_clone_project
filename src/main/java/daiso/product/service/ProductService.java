package daiso.product.service;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.naming.NamingException;
import daiso.product.dao.ProductConnectionFactory;
import daiso.product.dao.ProductDAO;
import daiso.product.dto.ProductDTO;

public final class ProductService {
    // 상품 입력에 적용하는 공통 규칙
    public static final int MIN_PRICE = 1000;
    public static final int MAX_PRICE = 5000;
    public static final String PRICE_RANGE = "가격은 1,000원 이상 5,000원 이하로 입력해 주세요.";
    public static final Map<String, String> STATUSES = Map.of("AVAILABLE", "판매 중", "SOLD_OUT", "품절",
            "SCHEDULED", "판매 예정");
    private final ProductDAO dao = new ProductDAO();

    // 품번 중복 조회
    public boolean codeExists(String code, String id) throws SQLException, NamingException {
        try (Connection conn = ProductConnectionFactory.openQueryConnection()) {
            boolean exists = dao.adminProductCodeExists(conn, code, id);
            conn.commit();
            return exists;
        }
    }

    // 상품 입력 검증과 등록 및 수정 트랜잭션
    public boolean save(Map<String, String> form) throws SQLException, NamingException {
        boolean create = form.get("id").isEmpty();
        ProductDTO product = product(form);
        try (Connection conn = ProductConnectionFactory.openWriteConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!dao.saveAdminProduct(conn, product, create)) {
                    throw new IllegalArgumentException("상품과 최하위 카테고리를 확인해 주세요.");
                }
                conn.commit();
                return create;
            } catch (SQLException | RuntimeException error) {
                rollback(conn, error);
                throw error;
            }
        }
    }

    // 상품 삭제 트랜잭션
    public boolean delete(String id) throws SQLException, NamingException {
        try (Connection conn = ProductConnectionFactory.openWriteConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean deleted = dao.deleteAdminProduct(conn, id);
                conn.commit();
                return deleted;
            } catch (SQLException | RuntimeException error) {
                rollback(conn, error);
                throw error;
            }
        }
    }

    // 실패한 변경을 취소하고 최초 오류 보존
    private static void rollback(Connection conn, Exception error) {
        try { conn.rollback(); }
        catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
    }

    // 입력 데이터 변환과 필드별 검증
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
