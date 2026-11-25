package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class AdminCategoryDAO {
    public long recentSales(Connection connection, String id, java.time.LocalDate through) throws SQLException {
        String sql = "WITH RECURSIVE branch AS (SELECT category_id FROM categories WHERE category_id = ? "
                + "UNION ALL SELECT c.category_id FROM categories c JOIN branch b ON c.parent_id = b.category_id) "
                + "SELECT COALESCE(SUM(i.quantity), 0) FROM branch b JOIN products pr ON pr.category_id = b.category_id "
                + "JOIN order_items i ON i.product_id = pr.product_id JOIN orders o ON o.order_id = i.order_id "
                + "WHERE o.canceled_at IS NULL AND EXISTS (SELECT 1 FROM payment_attempts p WHERE p.order_id = o.order_id "
                + "AND p.status IN ('APPROVED','REFUNDING') AND p.approved_at >= ? AND p.approved_at < ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, id);
            query.setObject(2, through.minusDays(29).atStartOfDay());
            query.setObject(3, through.plusDays(1).atStartOfDay());
            try (ResultSet row = query.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    private static final String FIELDS = "c.category_id, c.parent_id, c.name, c.level, c.display_order, c.is_leaf, c.is_visible";

    public List<Row> list(Connection connection) throws SQLException {
        String sql = "SELECT " + FIELDS + ", COALESCE(p.direct_products, 0) AS direct_products FROM categories c "
                + "LEFT JOIN (SELECT category_id, COUNT(*) AS direct_products FROM products GROUP BY category_id) p "
                + "ON c.category_id = p.category_id";
        return read(connection, sql);
    }

    // Use a consistent lock order for category edits, root creation and sibling reordering.
    public List<Row> lockTree(Connection connection) throws SQLException {
        return read(connection, "SELECT " + FIELDS + ", 0 AS direct_products FROM categories c "
                + "ORDER BY c.category_id FOR UPDATE");
    }

    private static List<Row> read(Connection connection, String sql) throws SQLException {
        List<Row> list = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            while (rows.next()) list.add(new Row(rows));
        }
        return list;
    }

    public void update(Connection connection, Row current, String name, boolean visible) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "UPDATE categories SET name = ?, is_visible = ? WHERE category_id = ?")) {
            query.setString(1, name);
            query.setBoolean(2, visible);
            query.setString(3, current.id);
            query.executeUpdate();
        }
    }

    public void updateOrders(Connection connection, Map<String, Integer> positions) throws SQLException {
        if (positions.isEmpty()) return;
        try (PreparedStatement query = connection.prepareStatement("UPDATE categories SET display_order=? WHERE category_id=?")) {
            for (Map.Entry<String, Integer> position : positions.entrySet()) {
                query.setInt(1, position.getValue());
                query.setString(2, position.getKey());
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    public boolean hasProducts(Connection connection, String id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT product_id FROM products WHERE category_id = ? LIMIT 1 FOR UPDATE")) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    public void delete(Connection connection, Row category) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("DELETE FROM categories WHERE category_id = ?")) {
            query.setString(1, category.id);
            if (query.executeUpdate() != 1) throw new SQLException("Category delete failed");
        }
    }

    public void updateLeaf(Connection connection, Row category, boolean leaf) throws SQLException {
        if (category.leaf == leaf) return;
        try (PreparedStatement query = connection.prepareStatement("UPDATE categories SET is_leaf= ? WHERE category_id= ?")) {
            query.setBoolean(1, leaf);
            query.setString(2, category.id);
            if (query.executeUpdate() != 1) throw new SQLException("Category leaf update failed");
        }
    }

    public void insert(Connection connection, String id, Row parent, String name, int displayOrder,
            boolean visible) throws SQLException {
        int level = parent == null ? 1 : parent.level + 1;
        if (level > 3) throw new IllegalArgumentException("카테고리는 최대 3단계까지 등록할 수 있습니다.");
        if (parent != null && parent.leaf) {
            if (hasProducts(connection, parent.id)) {
                throw new IllegalArgumentException("상품이 연결된 최하위 카테고리에는 하위 카테고리를 추가할 수 없습니다.");
            }
            updateLeaf(connection, parent, false);
        }
        try (PreparedStatement query = connection.prepareStatement(
                "INSERT INTO categories (category_id, parent_id, name, level, display_order, is_leaf, is_visible) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?)")) {
            query.setString(1, id);
            query.setString(2, parent == null ? null : parent.id);
            query.setString(3, name);
            query.setInt(4, level);
            query.setInt(5, displayOrder);
            query.setBoolean(6, visible);
            query.executeUpdate();
        }
    }

    public List<String> affectedProducts(Connection connection, List<String> categories) throws SQLException {
        if (categories.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(categories.size(), "?"));
        String sql = "SELECT product_id, category_id FROM products "
                + "WHERE category_id IN (" + placeholders + ") "
                + "ORDER BY product_id FOR SHARE";
        List<String> products = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            for (int index = 0; index < categories.size(); index++) query.setString(index + 1, categories.get(index));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString("product_id");
                    products.add(id.length() + ":" + id + rows.getString("category_id"));
                }
            }
        }
        return products;
    }

    public static final class Row {
        public final String id;
        public final String parent;
        public final String name;
        public final int level;
        public final int order;
        public final boolean leaf;
        public final boolean visible;
        public final long products;

        private Row(ResultSet result) throws SQLException {
            id = result.getString("category_id");
            parent = result.getString("parent_id");
            name = result.getString("name");
            level = result.getInt("level");
            order = result.getInt("display_order");
            leaf = result.getBoolean("is_leaf");
            visible = result.getBoolean("is_visible");
            products = result.getLong("direct_products");
        }
    }
}
