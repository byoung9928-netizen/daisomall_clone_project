package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductDAO {
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

    public List<ProductDTO> findAll(Connection conn) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("SELECT " + COLUMNS + " FROM products p")) {
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

    public List<ProductDTO> searchProducts(Connection conn, String keyword) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT " + COLUMNS + " FROM products p WHERE p.name LIKE ?")) {
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
                return "p.rating DESC, p.review_count DESC";
            case "reviews":
                return "p.review_count DESC";
            default:
                return "p.sales DESC";
        }
    }

    private static void bind(PreparedStatement query, List<Object> parameters) throws SQLException {
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
