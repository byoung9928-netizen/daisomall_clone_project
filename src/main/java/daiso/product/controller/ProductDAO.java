package daiso.product.controller;

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
    private static final String PRODUCT_FIELDS = "p.product_id, p.name, p.price, p.stock_quantity, "
            + "p.category_id, p.product_code, p.brand, p.status, p.image_url, p.description, "
            + "p.source_kind, p.source_note";
    private static final String ADMIN_COLUMNS = PRODUCT_FIELDS + ", p.rating, p.review_count";
    private static final String REVIEW_AVERAGE = "COALESCE(review_stats.average_rating, 0)";
    private static final String REVIEW_COUNT = "COALESCE(review_stats.review_count, 0)";
    private static final String PUBLIC_COLUMNS = PRODUCT_FIELDS
            + ", " + REVIEW_AVERAGE + " AS rating, " + REVIEW_COUNT + " AS review_count";
    // 공개 상품 조회는 논리 삭제되지 않은 구매 리뷰만 한 번에 집계한다.
    private static final String ACTIVE_REVIEW_STATS =
            " LEFT JOIN (SELECT product_id, AVG(rating) AS average_rating, COUNT(*) AS review_count "
            + "FROM product_reviews WHERE deleted_at IS NULL GROUP BY product_id) review_stats "
            + "ON review_stats.product_id = p.product_id";
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

    CatalogPage findAdminProducts(Connection conn, String keyword, int limit, int offset) throws SQLException {
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
        try (PreparedStatement query = conn.prepareStatement("SELECT " + ADMIN_COLUMNS + scope
                + " ORDER BY p.created_at DESC, p.product_id LIMIT ? OFFSET ?")) {
            bind(query, terms);
            query.setInt(5, limit);
            query.setInt(6, offset);
            return new CatalogPage(readProducts(query), total);
        }
    }

    Map<String, String> findAdminCategories(Connection conn) throws SQLException {
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

    boolean adminProductCodeExists(Connection conn, String code, String excludedId) throws SQLException {
        String sql = "SELECT 1 FROM products WHERE product_code = ? AND product_id <> ?";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, code);
            query.setString(2, excludedId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    boolean saveAdminProduct(Connection conn, ProductDTO product, boolean create) throws SQLException {
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

    boolean deleteAdminProduct(Connection conn, String id) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            query.setString(1, id);
            return query.executeUpdate() == 1;
        }
    }

    public List<ProductDTO> findAll(Connection conn) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT " + PUBLIC_COLUMNS + " FROM products p" + ACTIVE_REVIEW_STATS)) {
            return readProducts(query);
        }
    }

    public ProductDTO findById(Connection conn, String productId) throws SQLException {
        return findById(conn, productId, false);
    }

    ProductDTO findVisibleById(Connection conn, String productId) throws SQLException {
        return findById(conn, productId, true);
    }

    private ProductDTO findById(Connection conn, String productId, boolean visibleOnly) throws SQLException {
        String sql = visibleOnly
                ? VISIBLE_CATEGORIES + " SELECT " + PUBLIC_COLUMNS + " FROM products p "
                        + "JOIN visible_categories v ON p.category_id = v.category_id"
                        + ACTIVE_REVIEW_STATS + " WHERE p.product_id = ?"
                : "SELECT " + ADMIN_COLUMNS + " FROM products p WHERE p.product_id = ?";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, productId);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? readProduct(row) : null;
            }
        }
    }

    public List<ProductDTO> searchProducts(Connection conn, String keyword) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT " + PUBLIC_COLUMNS + " FROM products p" + ACTIVE_REVIEW_STATS + " WHERE p.name LIKE ?")) {
            query.setString(1, "%" + keyword + "%");
            return readProducts(query);
        }
    }

    List<Category> findVisibleCategories(Connection conn) throws SQLException {
        String sql = VISIBLE_CATEGORIES + " SELECT category_id, parent_id, name, level, display_order "
                + "FROM visible_categories ORDER BY level, display_order, category_id";
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                categories.add(new Category(rows.getString("category_id"), rows.getString("parent_id"),
                        rows.getString("name"), rows.getInt("level"), rows.getInt("display_order")));
            }
        }
        return categories;
    }

    CatalogPage findCatalog(Connection conn, CatalogFilter filter) throws SQLException {
        List<Object> parameters = new ArrayList<>();
        String categories = "visible_categories";
        String sql = VISIBLE_CATEGORIES;
        if (!filter.category.isEmpty()) {
            sql += SCOPED_CATEGORIES;
            categories = "selected_categories";
            parameters.add(filter.category);
        }
        StringBuilder scope = new StringBuilder(" FROM products p JOIN " + categories
                + " c ON p.category_id = c.category_id" + ACTIVE_REVIEW_STATS + " WHERE 1 = 1");
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
            scope.append(" AND " + REVIEW_AVERAGE + " = 5 AND " + REVIEW_COUNT + " > 0");
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
                sql + " SELECT " + PUBLIC_COLUMNS + scope + " ORDER BY " + order + ", p.product_id LIMIT ? OFFSET ?")) {
            bind(query, parameters);
            return new CatalogPage(readProducts(query), total);
        }
    }

    private static String sortSql(String sort) {
        switch (sort) {
            case "newest":
                return "p.created_at DESC";
            case "priceAsc":
                return "p.price ASC";
            case "priceDesc":
                return "p.price DESC";
            case "rating":
                return REVIEW_AVERAGE + " DESC, " + REVIEW_COUNT + " DESC";
            case "reviews":
                return REVIEW_COUNT + " DESC";
            default:
                return "p.sales DESC";
        }
    }

    private static void bind(PreparedStatement query, List<?> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            query.setObject(index + 1, parameters.get(index));
        }
    }

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
        product.categoryId = row.getString("category_id");
        product.productCode = row.getString("product_code");
        product.brand = row.getString("brand");
        product.status = row.getString("status");
        product.image = row.getString("image_url");
        product.description = row.getString("description");
        product.rating = row.getDouble("rating");
        product.reviewCount = row.getInt("review_count");
        product.sourceKind = row.getString("source_kind");
        product.sourceNote = row.getString("source_note");
        return product;
    }

    static final class CatalogFilter {
        String category = "";
        String keyword = "";
        String brand = "";
        String status = "";
        String sort = "recommended";
        Integer maxPrice;
        boolean available;
        boolean lowStock;
        boolean topRated;
        int limit;
        int offset;
    }

    static final class CatalogPage {
        final List<ProductDTO> products;
        final int total;

        CatalogPage(List<ProductDTO> products, int total) {
            this.products = products;
            this.total = total;
        }
    }

    public static final class Category {
        public final String id;
        public final String parentId;
        public final String name;
        public final int level;
        public final int displayOrder;

        Category(String id, String parentId, String name, int level, int displayOrder) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.level = level;
            this.displayOrder = displayOrder;
        }
    }
}
