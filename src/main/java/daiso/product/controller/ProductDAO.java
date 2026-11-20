package daiso.product.controller;

import daiso.product.dao.ReviewVisibility;
import daiso.product.dto.ProductActivityItem;
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
            + "FROM product_reviews rv WHERE rv.deleted_at IS NULL" + ReviewVisibility.PUBLISHED
            + " GROUP BY product_id) review_stats "
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

    CatalogPage findAdminProducts(Connection conn, AdminProductFilter filter, int limit, int offset) throws SQLException {
        String prefix = "";
        StringBuilder scope = new StringBuilder(" FROM products p");
        List<Object> terms = new ArrayList<>();
        if (!filter.category.isEmpty()) {
            prefix = "WITH RECURSIVE admin_selected AS (SELECT category_id FROM categories WHERE category_id = ? "
                    + "UNION ALL SELECT c.category_id FROM categories c JOIN admin_selected s ON c.parent_id = s.category_id) ";
            scope.append(" JOIN admin_selected s ON s.category_id = p.category_id");
            terms.add(filter.category);
        }
        scope.append(" WHERE 1 = 1");
        if (!filter.keyword.isEmpty()) {
            scope.append(" AND (LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0 "
                    + "OR LOCATE(?, p.product_id) > 0 OR LOCATE(?, p.brand) > 0)");
            terms.addAll(Collections.nCopies(4, filter.keyword));
        }
        if (!filter.status.isEmpty()) {
            scope.append(" AND p.status = ?");
            terms.add(filter.status);
        }
        scope.append(filter.stock.predicate);
        if (filter.stockQuantity != null) terms.add(filter.stockQuantity);
        int total;
        try (PreparedStatement query = conn.prepareStatement(prefix + "SELECT COUNT(*)" + scope)) {
            bind(query, terms);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                total = rows.getInt(1);
            }
        }
        String columns = ADMIN_COLUMNS + (filter.onlineInventory ? ", p.low_stock_threshold" : "");
        try (PreparedStatement query = conn.prepareStatement(prefix + "SELECT " + columns + scope
                + " ORDER BY " + filter.sort.orderBy + ", p.product_id LIMIT ? OFFSET ?")) {
            bind(query, terms);
            query.setInt(terms.size() + 1, limit);
            query.setInt(terms.size() + 2, offset);
            return new CatalogPage(readProducts(query, filter.onlineInventory), total);
        }
    }

    Map<OnlineStockState, Long> onlineSummary(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, "
                + aggregate(OnlineStockState.NORMAL) + ", " + aggregate(OnlineStockState.LOW) + ", "
                + aggregate(OnlineStockState.SOLD_OUT) + ", " + aggregate(OnlineStockState.INACTIVE) + " FROM products p";
        try (PreparedStatement query = conn.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            rows.next();
            return Map.of(OnlineStockState.ALL, rows.getLong("total"), OnlineStockState.NORMAL, rows.getLong("NORMAL"),
                    OnlineStockState.LOW, rows.getLong("LOW"), OnlineStockState.SOLD_OUT, rows.getLong("SOLD_OUT"),
                    OnlineStockState.INACTIVE, rows.getLong("INACTIVE"));
        }
    }

    private static String aggregate(OnlineStockState state) {
        return "COUNT(CASE WHEN " + state.condition + " THEN 1 END) AS `" + state.name() + "`";
    }

    ProductDTO findOnlineProduct(Connection conn, String id, boolean lock) throws SQLException {
        String sql = "SELECT " + ADMIN_COLUMNS + ", p.low_stock_threshold FROM products p WHERE p.product_id = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, id);
            List<ProductDTO> matches = readProducts(query, true);
            return matches.isEmpty() ? null : matches.get(0);
        }
    }

    void updateOnlineInventory(Connection conn, String id, int quantity, boolean threshold) throws SQLException {
        String column = threshold ? "low_stock_threshold" : "stock_quantity";
        try (PreparedStatement query = conn.prepareStatement("UPDATE products SET " + column + " = ? WHERE product_id = ?")) {
            query.setInt(1, quantity);
            query.setString(2, id);
            if (query.executeUpdate() != 1) throw new SQLException("Online inventory update failed");
        }
    }

    Map<String, String> findAdminCategories(Connection conn, boolean leavesOnly) throws SQLException {
        String sql = "SELECT c.category_id, CONCAT_WS(' › ', g.name, p.name, c.name) AS path "
                + "FROM categories c LEFT JOIN categories p ON c.parent_id = p.category_id "
                + "LEFT JOIN categories g ON p.parent_id = g.category_id "
                + (leavesOnly ? "WHERE c.is_leaf = 1 " : "") + "ORDER BY path, c.category_id";
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

    boolean lockLeafCategory(Connection conn, String id) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT is_leaf FROM categories WHERE category_id = ? FOR SHARE")) {
            statement.setString(1, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && row.getBoolean("is_leaf");
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

    ProductDTO lockAdminProduct(Connection conn, String id) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT " + ADMIN_COLUMNS + " FROM products p WHERE p.product_id = ? FOR UPDATE")) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? readProduct(row) : null;
            }
        }
    }

    void updateAdminStatus(Connection conn, String id, String status) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("UPDATE products SET status = ? WHERE product_id = ?")) {
            query.setString(1, status);
            query.setString(2, id);
            if (query.executeUpdate() != 1) throw new SQLException("상품 판매 상태 저장 실패");
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

    void loadActivityRatings(Connection conn, List<ProductActivityItem> items) throws SQLException {
        if (items.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(items.size(), "?"));
        Map<String, ProductDTO> products = new LinkedHashMap<>();
        for (ProductActivityItem item : items) products.put(item.product.getProductId(), item.product);
        String sql = "SELECT rv.product_id, AVG(rv.rating) AS average_rating, COUNT(*) AS review_count "
                + "FROM product_reviews rv WHERE rv.deleted_at IS NULL AND rv.product_id IN (" + placeholders + ")"
                + ReviewVisibility.PUBLISHED + " GROUP BY rv.product_id";
        for (ProductDTO product : products.values()) {
            product.setRating(0);
            product.setReviewCount(0);
        }
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, items.stream().map(item -> item.product.getProductId()).collect(java.util.stream.Collectors.toList()));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    ProductDTO product = products.get(rows.getString("product_id"));
                    product.setRating(rows.getDouble("average_rating"));
                    product.setReviewCount(rows.getInt("review_count"));
                }
            }
        }
    }

    List<ProductActivityItem> findRecentlyViewed(Connection conn, List<String> ids, int limit, int offset) throws SQLException {
        if (ids.isEmpty() || offset >= ids.size()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = VISIBLE_CATEGORIES + " SELECT " + ADMIN_COLUMNS + " FROM products p "
                + "JOIN visible_categories v ON p.category_id = v.category_id WHERE p.product_id IN (" + placeholders + ")";
        Map<String, ProductDTO> products = new LinkedHashMap<>();
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, ids);
            for (ProductDTO product : readProducts(query)) products.put(product.getProductId(), product);
        }
        List<ProductActivityItem> items = new ArrayList<>();
        int seen = 0;
        for (String id : ids) {
            ProductDTO product = products.get(id);
            if (product != null && seen++ >= offset) items.add(new ProductActivityItem(product, 0));
            if (items.size() == limit) break;
        }
        return items;
    }

    List<ProductActivityItem> findFrequentlyPurchased(Connection conn, String member, int limit, int offset)
            throws SQLException {
        String sql = VISIBLE_CATEGORIES
                + ", member_purchases AS (SELECT i.product_id, COUNT(DISTINCT o.order_id) AS purchase_count, "
                + "MAX(o.created_at) AS last_purchase FROM order_items i JOIN orders o ON o.order_id = i.order_id "
                + "WHERE o.member_id = ? AND o.status = 'PAID' AND o.canceled_at IS NULL "
                + "AND EXISTS (SELECT 1 FROM payment_attempts pa WHERE pa.order_id = o.order_id "
                + "AND pa.method = 'KAKAOPAY' AND pa.status = 'APPROVED' AND pa.approved_at IS NOT NULL "
                + "AND pa.tid IS NOT NULL AND pa.tid <> '' AND pa.amount = o.total_amount) GROUP BY i.product_id) "
                + "SELECT " + ADMIN_COLUMNS + ", mp.purchase_count FROM member_purchases mp "
                + "JOIN products p ON p.product_id = mp.product_id "
                + "JOIN visible_categories v ON p.category_id = v.category_id "
                + "ORDER BY mp.purchase_count DESC, mp.last_purchase DESC, p.product_id LIMIT ? OFFSET ?";
        List<ProductActivityItem> items = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setString(1, member);
            query.setInt(2, limit);
            query.setInt(3, offset);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) items.add(new ProductActivityItem(readProduct(rows), rows.getLong("purchase_count")));
            }
        }
        return items;
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

    private static final String STOCK_WATCH_CONDITION = "p.stock_quantity BETWEEN 0 AND 5 "
            + "AND p.status IN ('AVAILABLE', 'SOLD_OUT')";

    Map<String, Integer> stockWatchCounts(Connection conn) throws SQLException {
        String sql = "WITH RECURSIVE roots AS (SELECT category_id, category_id AS root_id FROM categories "
                + "WHERE parent_id IS NULL AND is_visible = 1 UNION ALL "
                + "SELECT c.category_id, r.root_id FROM categories c JOIN roots r ON c.parent_id = r.category_id "
                + "WHERE c.is_visible = 1) SELECT r.root_id, COUNT(*) AS stock_count "
                + "FROM products p JOIN roots r ON r.category_id = p.category_id WHERE "
                + STOCK_WATCH_CONDITION + " GROUP BY r.root_id";
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (PreparedStatement query = conn.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            while (rows.next()) counts.put(rows.getString("root_id"), rows.getInt("stock_count"));
        }
        return counts;
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
                + " c ON p.category_id = c.category_id" + ACTIVE_REVIEW_STATS);
        if (filter.salesDays > 0) {
            sql += ", approved_orders AS (SELECT o.order_id, MAX(pa.approved_at) AS approved_at "
                    + "FROM orders o JOIN payment_attempts pa ON pa.order_id = o.order_id "
                    + "WHERE o.status = 'PAID' AND o.canceled_at IS NULL AND pa.status = 'APPROVED' "
                    + "AND pa.tid <> '' AND pa.amount = o.total_amount "
                    + "AND pa.approved_at >= TIMESTAMPADD(DAY, ?, CURRENT_TIMESTAMP) GROUP BY o.order_id)";
            String units = filter.salesGrowth
                    ? "SUM(CASE WHEN a.approved_at >= CURRENT_TIMESTAMP - INTERVAL 1 DAY "
                        + "THEN i.quantity ELSE -i.quantity END)"
                    : "SUM(i.quantity)";
            scope.append(" JOIN (SELECT i.product_id, ").append(units)
                    .append(" AS units FROM approved_orders a JOIN order_items i ON i.order_id = a.order_id "
                    + "GROUP BY i.product_id HAVING units > 0) sales_rank ON sales_rank.product_id = p.product_id");
            parameters.add(-filter.salesDays);
        }
        if (filter.wishlistRank) {
            scope.append(" JOIN (SELECT product_id, COUNT(*) AS saves FROM product_wishlists GROUP BY product_id) "
                    + "wishlist_rank ON wishlist_rank.product_id = p.product_id");
        }
        scope.append(" WHERE p.status <> 'DISCONTINUED'");
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
        if (filter.stockWatch) scope.append(" AND ").append(STOCK_WATCH_CONDITION);
        if (filter.recentOnly) scope.append(" AND p.created_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY");
        if (filter.withReviews || filter.topRated || filter.ratingBand != null) {
            scope.append(" AND ").append(REVIEW_COUNT).append(" > 0");
        }
        if (filter.topRated) scope.append(" AND ").append(REVIEW_AVERAGE).append(" = 5");
        if (filter.ratingBand != null) {
            String score = filter.ratingBand.compareTo(java.math.BigDecimal.valueOf(5)) == 0
                    ? REVIEW_AVERAGE : "ROUND(" + REVIEW_AVERAGE + ", 1)";
            scope.append(" AND ").append(score).append(" = ?");
            parameters.add(filter.ratingBand);
        }
        if (filter.satisfactoryReview) {
            scope.append(" AND EXISTS (SELECT 1 FROM product_reviews rv JOIN product_review_answers ra "
                    + "ON ra.review_id = rv.review_id WHERE rv.product_id = p.product_id AND ra.answer_value = 3 "
                    + "AND rv.deleted_at IS NULL").append(ReviewVisibility.PUBLISHED).append(')');
        }
        if (filter.publicSale) scope.append(" AND p.status <> 'SCHEDULED'");

        int total;
        try (PreparedStatement query = conn.prepareStatement(sql + " SELECT COUNT(*)" + scope)) {
            bind(query, parameters);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                total = result.getInt(1);
            }
        }

        String order = sortSql(filter.sort);
        if (filter.salesDays > 0) order = "sales_rank.units DESC, " + order;
        if (filter.wishlistRank) order = "wishlist_rank.saves DESC, " + order;
        if (filter.salesDays == 0 && !filter.wishlistRank && "recommended".equals(filter.sort)
                && !filter.keyword.isEmpty()) {
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
            case "stockDesc":
                return "p.stock_quantity DESC";
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
        return readProducts(query, false);
    }

    private static List<ProductDTO> readProducts(PreparedStatement query, boolean onlineInventory) throws SQLException {
        List<ProductDTO> products = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                ProductDTO product = readProduct(rows);
                if (onlineInventory) product.setLowStockThreshold(rows.getInt("low_stock_threshold"));
                products.add(product);
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
        boolean stockWatch;
        boolean recentOnly;
        String watchRank = "";
        boolean topRated;
        boolean publicSale;
        boolean withReviews;
        boolean wishlistRank;
        boolean salesGrowth;
        boolean satisfactoryReview;
        int salesDays;
        java.math.BigDecimal ratingBand;
        String collectionMode = "";
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
