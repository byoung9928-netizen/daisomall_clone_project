package daiso.product.controller;

import daiso.product.dao.ReviewVisibility;
import daiso.product.dto.ProductActivityItem;
import daiso.product.service.RecommendationService.Scope;
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
        return queryAdminProducts(conn, filter, limit, offset);
    }

    List<AdminSuggestion> findAdminSuggestions(Connection conn, AdminProductFilter filter) throws SQLException {
        if (filter.keyword.isEmpty()) return List.of();
        AdminScope scope = adminScope(filter, true);
        String sql = scope.prefix + "SELECT p.product_id, p.name, p.product_code, p.price, p.stock_quantity, p.image_url"
                + (filter.onlineInventory ? ", p.status, p.low_stock_threshold" : "") + scope.from + scope.where
                + " ORDER BY CASE WHEN p.product_code = ? OR p.product_id = ? OR p.name = ? OR p.brand = ? THEN 0 "
                + "WHEN LOCATE(?, p.name) = 1 OR LOCATE(?, p.product_code) = 1 "
                + "OR LOCATE(?, p.product_id) = 1 OR LOCATE(?, p.brand) = 1 THEN 1 ELSE 2 END, "
                + "CHAR_LENGTH(p.name), p.name, p.product_id LIMIT 10";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, scope.parameters);
            for (int i = 1; i <= 8; i++) query.setString(scope.parameters.size() + i, filter.keyword);
            try (ResultSet rows = query.executeQuery()) {
                List<AdminSuggestion> matches = new ArrayList<>();
                while (rows.next()) matches.add(new AdminSuggestion(rows, filter.onlineInventory));
                return matches;
            }
        }
    }

    static final class AdminSuggestion {
        final String id;
        final String name;
        final String code;
        final String image;
        final int price;
        final int stock;
        final String stockStatus;

        private AdminSuggestion(ResultSet row, boolean online) throws SQLException {
            id = row.getString("product_id");
            name = row.getString("name");
            code = row.getString("product_code");
            image = row.getString("image_url");
            price = row.getInt("price");
            stock = row.getInt("stock_quantity");
            stockStatus = online ? OnlineStockState.of(row.getString("status"), stock,
                    row.getInt("low_stock_threshold")).label : "";
        }
    }

    // ===== 엑셀에서도 같은 상품·재고 필터와 정렬을 사용 =====
    List<ProductDTO> findAdminProductsForExport(Connection conn, AdminProductFilter filter) throws SQLException {
        return queryAdminProducts(conn, filter, null, 0).products;
    }

    private CatalogPage queryAdminProducts(Connection conn, AdminProductFilter filter, Integer limit, int offset)
            throws SQLException {
        AdminScope where = adminScope(filter, true);
        String prefix = where.prefix;
        String scope = where.from + where.where;
        List<Object> terms = where.parameters;
        int total = 0;
        if (limit != null) {
            try (PreparedStatement query = conn.prepareStatement(prefix + "SELECT COUNT(*)" + scope)) {
                bind(query, terms);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    total = rows.getInt(1);
                }
            }
        }
        String columns = ADMIN_COLUMNS + (filter.onlineInventory ? ", p.low_stock_threshold" : "")
                + (limit == null ? ", p.created_at, p.updated_at" : "");
        String sortingJoin = filter.sort == AdminProductFilter.Sort.REVIEWS ? ACTIVE_REVIEW_STATS
                : filter.sort == AdminProductFilter.Sort.SALES ? ADMIN_SALES : "";
        String pagination = limit == null ? "" : " LIMIT ? OFFSET ?";
        try (PreparedStatement query = conn.prepareStatement(prefix + "SELECT " + columns + where.from + sortingJoin + where.where
                + " ORDER BY " + filter.sort.orderBy + ", p.product_id" + pagination)) {
            bind(query, terms);
            if (limit != null) {
                query.setInt(terms.size() + 1, limit);
                query.setInt(terms.size() + 2, offset);
            }
            List<ProductDTO> products = readProducts(query, filter.onlineInventory, limit == null);
            return new CatalogPage(products, limit == null ? products.size() : total);
        }
    }

    private static final String ADMIN_SALES = " LEFT JOIN (SELECT i.product_id, SUM(i.quantity) AS units "
            + "FROM order_items i JOIN orders o ON o.order_id = i.order_id WHERE o.status = 'PAID' "
            + "AND o.canceled_at IS NULL AND EXISTS (SELECT 1 FROM payment_attempts a WHERE a.order_id = o.order_id "
            + "AND a.status = 'APPROVED' AND a.tid <> '' AND a.approved_at IS NOT NULL AND a.amount = o.total_amount) "
            + "GROUP BY i.product_id) admin_sales ON admin_sales.product_id = p.product_id";

    Map<String, Long> adminStatusSummary(Connection conn, AdminProductFilter filter) throws SQLException {
        AdminScope scope = adminScope(filter, false);
        Map<String, Long> totals = new LinkedHashMap<>();
        try (PreparedStatement query = conn.prepareStatement(scope.prefix + "SELECT p.status, COUNT(*)"
                + scope.from + scope.where + " GROUP BY p.status")) {
            bind(query, scope.parameters);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) totals.put(rows.getString(1), rows.getLong(2));
            }
        }
        totals.put("", totals.values().stream().mapToLong(Long::longValue).sum());
        return totals;
    }

    static AdminScope adminScope(AdminProductFilter filter, boolean includeStatus) {
        String prefix = "";
        StringBuilder from = new StringBuilder(" FROM products p");
        StringBuilder scope = new StringBuilder();
        List<Object> terms = new ArrayList<>();
        if (!filter.category.isEmpty()) {
            prefix = "WITH RECURSIVE admin_selected AS (SELECT category_id FROM categories WHERE category_id = ? "
                    + "UNION ALL SELECT c.category_id FROM categories c JOIN admin_selected s ON c.parent_id = s.category_id) ";
            from.append(" JOIN admin_selected s ON s.category_id = p.category_id");
            terms.add(filter.category);
        }
        scope.append(" WHERE 1 = 1");
        if (!filter.productId.isEmpty()) {
            scope.append(" AND p.product_id = ?");
            terms.add(filter.productId);
        }
        if (!filter.keyword.isEmpty()) {
            scope.append(" AND (LOCATE(?, p.name) > 0 OR LOCATE(?, p.product_code) > 0 "
                    + "OR LOCATE(?, p.product_id) > 0 OR LOCATE(?, p.brand) > 0)");
            terms.addAll(Collections.nCopies(4, filter.keyword));
        }
        if (includeStatus && !filter.status.isEmpty()) {
            scope.append(" AND p.status = ?");
            terms.add(filter.status);
        }
        scope.append(filter.stock.predicate);
        if (filter.stockQuantity != null) terms.add(filter.stockQuantity);
        return new AdminScope(prefix, from.toString(), scope.toString(), terms);
    }

    static final class AdminScope {
        final String prefix;
        final String from;
        final String where;
        final List<Object> parameters;

        AdminScope(String prefix, String from, String where, List<Object> parameters) {
            this.prefix = prefix;
            this.from = from;
            this.where = where;
            this.parameters = parameters;
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

    void updateAdminPrice(Connection conn, String id, int price) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement("UPDATE products SET price = ? WHERE product_id = ?")) {
            query.setInt(1, price);
            query.setString(2, id);
            query.executeUpdate();
        }
    }

    void updateAdminCategory(Connection conn, String id, String category) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "UPDATE products SET category_id = ? WHERE product_id = ?")) {
            query.setString(1, category);
            query.setString(2, id);
            query.executeUpdate();
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
        loadRatings(conn, items.stream().map(item -> item.product).collect(java.util.stream.Collectors.toList()));
    }

    private void loadRatings(Connection conn, List<ProductDTO> items) throws SQLException {
        if (items.isEmpty()) return;
        Map<String, ProductDTO> products = new LinkedHashMap<>();
        for (ProductDTO item : items) products.put(item.getProductId(), item);
        String placeholders = String.join(",", Collections.nCopies(products.size(), "?"));
        String sql = "SELECT rv.product_id, AVG(rv.rating) AS average_rating, COUNT(*) AS review_count "
                + "FROM product_reviews rv WHERE rv.deleted_at IS NULL AND rv.product_id IN (" + placeholders + ")"
                + ReviewVisibility.PUBLISHED + " GROUP BY rv.product_id";
        for (ProductDTO product : products.values()) {
            product.setRating(0);
            product.setReviewCount(0);
        }
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, new ArrayList<>(products.keySet()));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    ProductDTO product = products.get(rows.getString("product_id"));
                    product.setRating(rows.getDouble("average_rating"));
                    product.setReviewCount(rows.getInt("review_count"));
                }
            }
        }
    }

    List<ProductDTO> recommendationCandidates(Connection conn, Scope scope, int limit) throws SQLException {
        String fields = "SELECT p.product_id, p.category_id, p.brand, p.price, p.status, p.stock_quantity";
        String filter = " WHERE p.status IN ('AVAILABLE','SOLD_OUT')";
        String sql;
        List<Object> args = new ArrayList<>();
        if (scope == null) {
            sql = VISIBLE_CATEGORIES + " " + fields + " FROM products p JOIN visible_categories v "
                    + "ON v.category_id = p.category_id" + filter + " ORDER BY " + sortSql("recommended")
                    + ", p.product_id LIMIT ?";
        } else {
            if (scope.roots.isEmpty()) return List.of();
            String priority = "CASE WHEN p.category_id IN (" + markers(scope.leaves.size()) + ") THEN 0 ";
            args.addAll(scope.leaves);
            if (!scope.parents.isEmpty()) {
                priority += "WHEN v.parent_id IN (" + markers(scope.parents.size()) + ") THEN 1 ";
                args.addAll(scope.parents);
            }
            String root = "COALESCE(parent.parent_id, v.parent_id, v.category_id)";
            sql = VISIBLE_CATEGORIES + ", shortlist AS (SELECT p.product_id, ROW_NUMBER() OVER (PARTITION BY "
                    + root + " ORDER BY " + priority + "ELSE 2 END, p.sales DESC, p.product_id) AS position "
                    + "FROM products p JOIN visible_categories v ON v.category_id = p.category_id "
                    + "LEFT JOIN visible_categories parent ON parent.category_id = v.parent_id" + filter
                    + " AND " + root + " IN (" + markers(scope.roots.size()) + ")) "
                    + fields + " FROM shortlist s JOIN products p ON p.product_id = s.product_id "
                    + "WHERE s.position <= ? ORDER BY s.position, p.product_id";
            args.addAll(scope.roots);
        }
        args.add(limit);
        List<ProductDTO> result = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, args);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    ProductDTO item = new ProductDTO();
                    item.setProductId(row.getString("product_id"));
                    item.setCategoryId(row.getString("category_id"));
                    item.setBrand(row.getString("brand"));
                    item.setPrice(row.getInt("price"));
                    item.setStatus(row.getString("status"));
                    item.setStockQuantity(row.getInt("stock_quantity"));
                    result.add(item);
                }
            }
        }
        return result;
    }

    List<ProductDTO> recommendationCards(Connection connection, List<String> ids) throws SQLException {
        List<ProductDTO> cards = new ArrayList<>();
        for (ProductActivityItem item : findRecentlyViewed(connection, ids, ids.size(), 0)) cards.add(item.product);
        loadRatings(connection, cards);
        return cards;
    }

    private static String markers(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    List<ProductDTO> recentQuickPage(Connection conn, List<String> ids, int limit, int offset) throws SQLException {
        if (ids.isEmpty() || offset >= ids.size()) return List.of();
        String placeholders = markers(ids.size());
        String sql = VISIBLE_CATEGORIES + " SELECT " + ADMIN_COLUMNS + " FROM products p "
                + "JOIN visible_categories v ON p.category_id = v.category_id "
                + "WHERE p.status <> 'DISCONTINUED' AND p.product_id IN (" + placeholders + ") "
                + "ORDER BY FIELD(p.product_id, " + placeholders + ") LIMIT ? OFFSET ?";
        List<Object> parameters = new ArrayList<>(ids);
        parameters.addAll(ids);
        parameters.add(limit);
        parameters.add(offset);
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, parameters);
            return readProducts(query);
        }
    }

    Map<String, CardFacts> cardFacts(Connection conn, List<String> ids, boolean includePurchases) throws SQLException {
        if (ids.isEmpty()) return Map.of();
        String sales = includePurchases ? " LEFT JOIN (SELECT i.product_id, SUM(i.quantity) AS units "
                + "FROM order_items i JOIN approved_orders a ON a.order_id = i.order_id "
                + "WHERE i.product_id IN (" + markers(ids.size()) + ") GROUP BY i.product_id) sales "
                + "ON sales.product_id = p.product_id" : "";
        String sql = (includePurchases ? "WITH " + APPROVED_ORDER_CTE : "")
                + " SELECT p.product_id, p.created_at >= CURRENT_TIMESTAMP - INTERVAL 14 DAY "
                + "AND p.created_at <= CURRENT_TIMESTAMP AND NOT EXISTS (SELECT 1 FROM products batch "
                + "WHERE batch.created_at >= CURRENT_TIMESTAMP - INTERVAL 15 DAY "
                + "AND DATE(batch.created_at) = DATE(p.created_at) LIMIT 1 OFFSET 99) AS is_new, "
                + (includePurchases ? "COALESCE(sales.units, 0)" : "0") + " AS units FROM products p"
                + sales + " WHERE p.product_id IN (" + markers(ids.size()) + ")";
        List<Object> parameters = new ArrayList<>();
        if (includePurchases) {
            parameters.add(-30);
            parameters.addAll(ids);
        }
        parameters.addAll(ids);
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, parameters);
            try (ResultSet results = query.executeQuery()) {
                Map<String, CardFacts> facts = new LinkedHashMap<>();
                while (results.next()) {
                    facts.put(results.getString("product_id"),
                            new CardFacts(results.getBoolean("is_new"), results.getLong("units")));
                }
                return facts;
            }
        }
    }

    List<SearchSuggestion> searchSuggestions(Connection conn, String term) throws SQLException {
        List<Object> parameters = new ArrayList<>();
        String sql;
        if (term.isEmpty()) {
            sql = VISIBLE_CATEGORIES + APPROVED_ORDERS
                    + ", picks AS (SELECT p.name AS term, p.image_url, p.product_id, p.review_count, "
                    + "COALESCE(sales_rank.units, 0) AS units, "
                    + "ROW_NUMBER() OVER (PARTITION BY p.category_id ORDER BY COALESCE(sales_rank.units, 0) DESC, "
                    + "p.review_count DESC, p.product_id) AS category_rank "
                    + "FROM products p JOIN visible_categories v ON v.category_id = p.category_id"
                    + salesRankJoin(false, false)
                    + " WHERE p.status = 'AVAILABLE' AND p.stock_quantity > 0) "
                    + "SELECT term, NULL AS category_id, MAX(image_url) AS image_url FROM picks "
                    + "WHERE category_rank = 1 GROUP BY term ORDER BY MAX(units) DESC, MAX(review_count) DESC, term LIMIT 6";
            parameters.add(-30);
        } else {
            sql = VISIBLE_CATEGORIES
                    + ", public_products AS (SELECT p.name, p.brand, p.category_id, p.review_count FROM products p "
                    + "JOIN visible_categories v ON v.category_id = p.category_id WHERE p.status <> 'DISCONTINUED'), "
                    + "populated_categories AS (SELECT DISTINCT category_id FROM public_products "
                    + "UNION DISTINCT SELECT v.parent_id FROM visible_categories v "
                    + "JOIN populated_categories c ON c.category_id = v.category_id WHERE v.parent_id IS NOT NULL), "
                    + "terms AS (SELECT name AS term, NULL AS category_id, review_count AS popularity FROM public_products "
                    + "WHERE LOCATE(?, name) > 0 UNION ALL SELECT brand, NULL, review_count FROM public_products "
                    + "WHERE LOCATE(?, brand) > 0 UNION ALL SELECT v.name, v.category_id, 0 FROM visible_categories v "
                    + "JOIN populated_categories c ON c.category_id = v.category_id WHERE LOCATE(?, v.name) > 0) "
                    + "SELECT term, CASE WHEN COUNT(category_id) = COUNT(*) AND COUNT(DISTINCT category_id) = 1 "
                    + "THEN MAX(category_id) END AS category_id, NULL AS image_url FROM terms "
                    + "GROUP BY term HAVING COUNT(category_id) <> COUNT(*) OR COUNT(DISTINCT category_id) = 1 "
                    + "ORDER BY CASE WHEN term = ? THEN 0 WHEN LOCATE(?, term) = 1 THEN 1 ELSE 2 END, "
                    + "MAX(popularity) DESC, CHAR_LENGTH(term), term LIMIT 10";
            parameters.addAll(Collections.nCopies(5, term));
        }
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, parameters);
            try (ResultSet results = query.executeQuery()) {
                List<SearchSuggestion> matches = new ArrayList<>();
                while (results.next()) {
                    matches.add(new SearchSuggestion(results.getString("term"), results.getString("category_id"),
                            results.getString("image_url")));
                }
                return matches;
            }
        }
    }

    public static final class CardFacts {
        public final boolean newProduct;
        public final long units;

        CardFacts(boolean newProduct, long units) {
            this.newProduct = newProduct;
            this.units = units;
        }
    }

    static final class SearchSuggestion {
        final String term;
        final String categoryId;
        final String image;

        SearchSuggestion(String term, String categoryId, String image) {
            this.term = term;
            this.categoryId = categoryId;
            this.image = image;
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
        List<Object> categoryParameters = new ArrayList<>();
        String categories = "visible_categories";
        String categorySql = VISIBLE_CATEGORIES;
        if (!filter.category.isEmpty()) {
            categorySql += SCOPED_CATEGORIES;
            categories = "selected_categories";
            categoryParameters.add(filter.category);
        }
        String fromProducts = " FROM products p JOIN " + categories + " c ON p.category_id = c.category_id";
        boolean rankedSales = filter.salesDays > 0;
        boolean sortBySales = "sales".equals(filter.sort);
        boolean sortByWishlists = "wishlists".equals(filter.sort);
        boolean includeSales = rankedSales || sortBySales;
        boolean includeWishlists = filter.wishlistRank || sortByWishlists;
        boolean reviewFilter = filter.withReviews || filter.topRated || filter.ratingBand != null || filter.minimumRating != null;
        boolean reviewStatsNeeded = reviewFilter || "rating".equals(filter.sort) || "reviews".equals(filter.sort);
        // Match ranked IDs to their products before joining the recursive category set.
        String joinOrder = rankedSales ? "/*+ JOIN_PREFIX(sales_rank, p) */ " : "";
        String salesJoin = includeSales ? salesRankJoin(rankedSales, filter.salesGrowth) : "";
        String wishlistJoin = includeWishlists ? (filter.wishlistRank ? " JOIN " : " LEFT JOIN ")
                + "(SELECT product_id, COUNT(*) AS saves FROM product_wishlists GROUP BY product_id) "
                + "wishlist_rank ON wishlist_rank.product_id = p.product_id" : "";

        List<Object> parameters = new ArrayList<>();
        StringBuilder scope = new StringBuilder(" WHERE p.status <> 'DISCONTINUED'");
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
        if (filter.facets.matchingCodes != null) {
            scope.append(" AND p.product_code IN (SELECT matched.product_code FROM "
                    + "JSON_TABLE(?, '$[*]' COLUMNS (product_code VARCHAR(100) PATH '$')) AS matched)");
            parameters.add(filter.facets.matchingCodes);
        }
        if (filter.minimumRating != null) {
            scope.append(" AND ").append(REVIEW_AVERAGE).append(" >= ?");
            parameters.add(filter.minimumRating);
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

        List<Object> countParameters = new ArrayList<>(categoryParameters);
        if (rankedSales) countParameters.add(-filter.salesDays);
        countParameters.addAll(parameters);
        String countSql = categorySql + (rankedSales ? APPROVED_ORDERS : "") + " SELECT " + joinOrder + "COUNT(*)"
                + fromProducts + (reviewFilter ? ACTIVE_REVIEW_STATS : "") + (rankedSales ? salesJoin : "") + scope;
        if (filter.wishlistRank) {
            countSql += " AND EXISTS (SELECT 1 FROM product_wishlists w WHERE w.product_id = p.product_id)";
        }
        int total;
        try (PreparedStatement query = conn.prepareStatement(countSql)) {
            bind(query, countParameters);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                total = result.getInt(1);
            }
        }

        List<Object> listParameters = new ArrayList<>(categoryParameters);
        if (includeSales) listParameters.add(rankedSales ? -filter.salesDays : -30);
        listParameters.addAll(parameters);
        String order = sortSql(filter.sort);
        if (includeSales) order = "COALESCE(sales_rank.units, 0) DESC, " + order;
        if (includeWishlists) order = "COALESCE(wishlist_rank.saves, 0) DESC, " + order;
        if (!rankedSales && !filter.wishlistRank && "recommended".equals(filter.sort) && !filter.keyword.isEmpty()) {
            order = "CASE WHEN p.product_code = ? THEN 0 ELSE 1 END, " + order;
            listParameters.add(filter.keyword);
        }
        listParameters.add(filter.limit);
        listParameters.add(filter.offset);

        String columns = reviewStatsNeeded ? PUBLIC_COLUMNS : PRODUCT_FIELDS + ", 0 AS rating, 0 AS review_count";
        String listSql = categorySql + (includeSales ? APPROVED_ORDERS : "") + " SELECT " + joinOrder + columns
                + fromProducts + (reviewStatsNeeded ? ACTIVE_REVIEW_STATS : "") + salesJoin + wishlistJoin + scope
                + " ORDER BY " + order + ", p.product_id LIMIT ? OFFSET ?";
        List<ProductDTO> products;
        try (PreparedStatement query = conn.prepareStatement(listSql)) {
            bind(query, listParameters);
            products = readProducts(query);
        }
        if (!reviewStatsNeeded) loadRatings(conn, products);
        return new CatalogPage(products, total);
    }

    private static final String HOME_RANK_SCOPE = " WHERE p.status IN ('AVAILABLE','SOLD_OUT')";
    private static final int HOME_RANK_DAYS = 30;
    static final int HOME_RANK_MINIMUM = 12;

    List<HomeRankCategory> findHomeRankCategories(Connection conn) throws SQLException {
        String members = ", category_members AS (SELECT category_id AS ranking_id, category_id FROM visible_categories "
                + "WHERE level IN (1,2) UNION ALL SELECT m.ranking_id, v.category_id FROM category_members m "
                + "JOIN visible_categories v ON v.parent_id = m.category_id)";
        String sql = VISIBLE_CATEGORIES + APPROVED_ORDERS + members
                + " SELECT v.category_id, v.name, COALESCE(v.parent_id, v.category_id) AS root_id, "
                + "COUNT(*) AS eligible_products, SUM(COALESCE(sales_rank.units, 0)) AS approved_units, "
                + "SUM(" + REVIEW_COUNT + ") AS public_reviews"
                + " FROM category_members m JOIN visible_categories v ON v.category_id = m.ranking_id"
                + " JOIN products p ON p.category_id = m.category_id"
                + salesRankJoin(false, false) + ACTIVE_REVIEW_STATS + HOME_RANK_SCOPE
                + " GROUP BY v.category_id, v.name, v.parent_id HAVING COUNT(*) >= ?"
                + " ORDER BY (approved_units > 0) DESC, (eligible_products >= 20) DESC, approved_units DESC, public_reviews DESC, v.category_id";
        List<HomeRankCategory> result = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            query.setInt(1, -HOME_RANK_DAYS);
            query.setInt(2, HOME_RANK_MINIMUM);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new HomeRankCategory(rows.getString("category_id"), rows.getString("name"),
                            rows.getString("root_id"), rows.getInt("eligible_products"), rows.getLong("approved_units")));
                }
            }
        }
        return result;
    }

    List<ProductDTO> findHomeRankProducts(Connection conn, String category, int limit) throws SQLException {
        String sql = VISIBLE_CATEGORIES + SCOPED_CATEGORIES + APPROVED_ORDERS
                + " SELECT " + PUBLIC_COLUMNS + " FROM products p JOIN selected_categories c ON p.category_id = c.category_id"
                + salesRankJoin(false, false) + ACTIVE_REVIEW_STATS + HOME_RANK_SCOPE
                + " AND EXISTS (SELECT 1 FROM visible_categories WHERE category_id = ? AND level IN (1,2))"
                + " ORDER BY COALESCE(sales_rank.units, 0) DESC, " + REVIEW_COUNT + " DESC, p.product_id LIMIT ?";
        try (PreparedStatement query = conn.prepareStatement(sql)) {
            bind(query, List.of(category, -HOME_RANK_DAYS, category, limit));
            return readProducts(query);
        }
    }

    public static final class HomeRankCategory {
        public final String id;
        public final String name;
        final String rootId;
        public final int products;
        public final long units;

        private HomeRankCategory(String id, String name, String rootId, int products, long units) {
            this.id = id;
            this.name = name;
            this.rootId = rootId;
            this.products = products;
            this.units = units;
        }
    }

    private static final String APPROVED_ORDER_CTE = "approved_orders AS (SELECT o.order_id, MAX(pa.approved_at) AS approved_at "
            + "FROM orders o JOIN payment_attempts pa ON pa.order_id = o.order_id "
            + "WHERE o.status = 'PAID' AND o.canceled_at IS NULL AND pa.status = 'APPROVED' "
            + "AND pa.tid <> '' AND pa.amount = o.total_amount "
            + "AND pa.approved_at >= TIMESTAMPADD(DAY, ?, CURRENT_TIMESTAMP) GROUP BY o.order_id)";

    private static final String APPROVED_ORDERS = ", " + APPROVED_ORDER_CTE;

    private static String salesRankJoin(boolean required, boolean growth) {
        String units = growth
                ? "SUM(CASE WHEN a.approved_at >= CURRENT_TIMESTAMP - INTERVAL 1 DAY THEN i.quantity ELSE -i.quantity END)"
                : "SUM(i.quantity)";
        return (required ? " JOIN " : " LEFT JOIN ")
                + "(SELECT i.product_id, " + units
                + " AS units FROM approved_orders a JOIN order_items i ON i.order_id = a.order_id "
                + "GROUP BY i.product_id HAVING units > 0) sales_rank ON sales_rank.product_id = p.product_id";
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
        return readProducts(query, onlineInventory, false);
    }

    private static List<ProductDTO> readProducts(PreparedStatement query, boolean onlineInventory, boolean export)
            throws SQLException {
        List<ProductDTO> products = new ArrayList<>();
        try (ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                ProductDTO product = readProduct(rows);
                if (export) {
                    product.adminCreatedAt = rows.getObject("created_at", java.time.LocalDateTime.class);
                    product.adminUpdatedAt = rows.getObject("updated_at", java.time.LocalDateTime.class);
                }
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
        Integer minimumRating;
        ProductCatalogFacets.Selection facets = ProductCatalogFacets.Selection.EMPTY;
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
