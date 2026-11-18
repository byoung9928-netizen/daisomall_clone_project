package daiso.product.dao;

import daiso.product.dto.ProductCategory;
import daiso.product.dto.ProductDTO;
import daiso.product.dto.ProductFilter;
import daiso.product.dto.ProductPage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductDAO {
    // 필드와 기본 설정
    private static final String COLUMNS = "p.product_id, p.name, p.price, p.stock_quantity, "
            + "p.category_id, p.product_code, p.brand, p.status, p.image_url, p.description, p.rating, "
            + "p.review_count, p.source_kind, p.source_note";
    private static final String VISIBLE_CATEGORIES =
            "WITH RECURSIVE visible_categories AS ("
            + "SELECT category_id, parent_id, name, level, display_order FROM categories "
            + "WHERE parent_id IS NULL AND is_visible = 1 "
            + "UNION ALL SELECT c.category_id, c.parent_id, c.name, c.level, c.display_order "
            + "FROM categories c JOIN visible_categories v ON c.parent_id = v.category_id "
            + "WHERE c.is_visible = 1)";
    private static final String SCOPED_CATEGORIES =
            ", selected_categories AS (SELECT category_id FROM visible_categories WHERE category_id = ? "
            + "UNION ALL SELECT v.category_id FROM visible_categories v "
            + "JOIN selected_categories s ON v.parent_id = s.category_id)";

    // 고객 상품 목록과 검색 조회
    public List<ProductDTO> findAll(Connection conn) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT " + COLUMNS + " FROM products p")) {
            return readProducts(query);
        }
    }

    public List<ProductDTO> searchProducts(Connection conn, String keyword) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT " + COLUMNS + " FROM products p WHERE p.name LIKE ?")) {
            query.setString(1, "%" + keyword + "%");
            return readProducts(query);
        }
    }

    public ProductPage findCatalog(Connection conn, ProductFilter filter) throws SQLException {
        List<Object> parameters = new ArrayList<>();
        String categories = "visible_categories";
        String sql = VISIBLE_CATEGORIES;
        if (!filter.category.isEmpty()) {
            sql += SCOPED_CATEGORIES;
            categories = "selected_categories";
            parameters.add(filter.category);
        }
        StringBuilder scope = new StringBuilder(" FROM products p JOIN " + categories
                + " c ON p.category_id = c.category_id WHERE 1 = 1");
        if (!filter.keyword.isEmpty()) {
            scope.append(" AND (LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0"
                    + " OR LOCATE(?, p.brand) > 0)");
            parameters.addAll(Collections.nCopies(3, filter.keyword));
        }
        if (!filter.brand.isEmpty()) {
            scope.append(" AND LOCATE(?, p.brand) > 0");
            parameters.add(filter.brand);
        }
        if (filter.maxPrice != null) {
            scope.append(" AND p.price <= ?");
            parameters.add(filter.maxPrice);
        }
        if (!filter.status.isEmpty()) {
            scope.append(" AND p.status = ?");
            parameters.add(filter.status);
        }
        if (filter.available || filter.lowStock) {
            scope.append(" AND p.status = 'AVAILABLE' AND p.stock_quantity > 0");
        }
        if (filter.lowStock) {
            scope.append(" AND p.stock_quantity <= 5");
        }
        if (filter.topRated) {
            scope.append(" AND p.rating = 5 AND p.review_count > 0");
        }

        int total;
        try (PreparedStatement query = conn.prepareStatement(sql + " SELECT COUNT(*)" + scope)) {
            bind(query, parameters);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                total = result.getInt(1);
            }
        }

        String order = sortSql(filter.sort);
        if ("recommended".equals(filter.sort) && !filter.keyword.isEmpty()) {
            order = "CASE WHEN p.product_code = ? THEN 0 ELSE 1 END, " + order;
            parameters.add(filter.keyword);
        }
        parameters.add(filter.limit);
        parameters.add(filter.offset);
        try (PreparedStatement query = conn.prepareStatement(
                sql + " SELECT " + COLUMNS + scope + " ORDER BY " + order + ", p.product_id LIMIT ? OFFSET ?")) {
            bind(query, parameters);
            return new ProductPage(readProducts(query), total);
        }
    }

    // 상품 ID로 상세 조회
    public ProductDTO findById(Connection conn, String productId) throws SQLException {
        return findById(conn, productId, false);
    }

    private ProductDTO findById(Connection conn, String productId, boolean visibleOnly) throws SQLException {
        String sql = visibleOnly
                ? VISIBLE_CATEGORIES + " SELECT " + COLUMNS + " FROM products p "
                        + "JOIN visible_categories v ON p.category_id = v.category_id WHERE p.product_id = ?"
                : "SELECT " + COLUMNS + " FROM products p WHERE p.product_id = ?";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, productId);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? readProduct(row) : null;
            }
        }
    }

    public ProductDTO findVisibleById(Connection conn, String productId) throws SQLException {
        return findById(conn, productId, true);
    }

    // 고객 화면에서 사용할 카테고리 조회
    public List<ProductCategory> findVisibleCategories(Connection conn) throws SQLException {
        String sql = VISIBLE_CATEGORIES + " SELECT category_id, parent_id, name, level, display_order "
                + "FROM visible_categories ORDER BY level, display_order, category_id";
        List<ProductCategory> categories = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                categories.add(new ProductCategory(rows.getString("category_id"), rows.getString("parent_id"),
                        rows.getString("name"), rows.getInt("level"), rows.getInt("display_order")));
            }
        }
        return categories;
    }

    // 관리자 상품 목록과 입력용 카테고리 조회
    public ProductPage findAdminProducts(Connection conn, String keyword, int limit, int offset) throws SQLException {
        String scope = " FROM products p WHERE LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0 "
                + "OR LOCATE(?, p.product_id) > 0 OR LOCATE(?, p.brand) > 0";
        List<String> terms = List.of(keyword, keyword, keyword, keyword);
        int total;
        try (PreparedStatement query = conn.prepareStatement("SELECT COUNT(*)" + scope)) {
            bind(query, terms);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                total = rows.getInt(1);
            }
        }
        try (PreparedStatement query = conn.prepareStatement("SELECT " + COLUMNS + scope
                + " ORDER BY p.created_at DESC, p.product_id LIMIT ? OFFSET ?")) {
            bind(query, terms);
            query.setInt(5, limit);
            query.setInt(6, offset);
            return new ProductPage(readProducts(query), total);
        }
    }

    public Map<String, String> findAdminCategories(Connection conn) throws SQLException {
        String sql = "SELECT c.category_id, CONCAT_WS(' › ', g.name, p.name, c.name) AS path "
                + "FROM categories c LEFT JOIN categories p ON c.parent_id = p.category_id "
                + "LEFT JOIN categories g ON p.parent_id = g.category_id "
                + "WHERE c.is_leaf = 1 ORDER BY path, c.category_id";
        Map<String, String> categories = new LinkedHashMap<>();
        try (PreparedStatement query = conn.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                categories.put(rows.getString("category_id"), rows.getString("path"));
            }
        }
        return categories;
    }

    // 관리자 품번 중복 확인과 상품 등록 및 수정
    public boolean adminProductCodeExists(Connection conn, String code, String excludedId) throws SQLException {
        String sql = "SELECT 1 FROM products WHERE product_code = ? AND product_id <> ?";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, code);
            query.setString(2, excludedId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    public boolean saveAdminProduct(Connection conn, ProductDTO product, boolean create) throws SQLException {
        String sql = create
                ? "INSERT INTO products (product_code, name, brand, price, stock_quantity, status, image_url, "
                        + "description, category_id, product_id) SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? "
                        + "FROM categories WHERE category_id = ? AND is_leaf = 1"
                : "UPDATE products SET product_code = ?, name = ?, brand = ?, price = ?, stock_quantity = ?, "
                        + "status = ?, image_url = ?, description = ?, category_id = ? WHERE product_id = ? "
                        + "AND EXISTS (SELECT 1 FROM categories WHERE category_id = ? AND is_leaf = 1)";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, product.getProductCode());
            query.setString(2, product.getName());
            query.setString(3, product.getBrand());
            query.setInt(4, product.getPrice());
            query.setInt(5, product.getStockQuantity());
            query.setString(6, product.getStatus());
            query.setString(7, product.getImage());
            query.setString(8, product.getDescription());
            query.setString(9, product.getCategoryId());
            query.setString(10, product.getProductId());
            query.setString(11, product.getCategoryId());
            return query.executeUpdate() == 1;
        }
    }

    // 관리자 상품 삭제
    public boolean deleteAdminProduct(Connection conn, String id) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            query.setString(1, id);
            return query.executeUpdate() == 1;
        }
    }

    // 정렬 SQL 선택과 조회 파라미터 바인딩
    private static String sortSql(String sort) {
        switch (sort) {
            case "newest":
                return "p.created_at DESC";
            case "priceAsc":
                return "p.price ASC";
            case "priceDesc":
                return "p.price DESC";
            case "rating":
                return "p.rating DESC, p.review_count DESC";
            case "reviews":
                return "p.review_count DESC";
            default:
                return "p.sales DESC";
        }
    }

    private static void bind(PreparedStatement query, List<?> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            query.setObject(index + 1, parameters.get(index));
        }
    }

    // DB 조회 결과를 상품 객체로 변환
    private static List<ProductDTO> readProducts(PreparedStatement query) throws SQLException {
        List<ProductDTO> products = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                products.add(readProduct(rows));
            }
        }
        return products;
    }

    private static ProductDTO readProduct(ResultSet row) throws SQLException {
        ProductDTO product = new ProductDTO();
        product.setProductId(row.getString("product_id"));
        product.setName(row.getString("name"));
        product.setPrice(row.getInt("price"));
        product.setStockQuantity(row.getInt("stock_quantity"));
        product.setCategoryId(row.getString("category_id"));
        product.setProductCode(row.getString("product_code"));
        product.setBrand(row.getString("brand"));
        product.setStatus(row.getString("status"));
        product.setImage(row.getString("image_url"));
        product.setDescription(row.getString("description"));
        product.setRating(row.getDouble("rating"));
        product.setReviewCount(row.getInt("review_count"));
        product.setSourceKind(row.getString("source_kind"));
        product.setSourceNote(row.getString("source_note"));
        return product;
    }

}
