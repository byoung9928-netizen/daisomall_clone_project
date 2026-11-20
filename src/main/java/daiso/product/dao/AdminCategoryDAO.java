package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class AdminCategoryDAO {
    public List<Row> list(Connection connection) throws SQLException {
        String sql = "SELECT c.category_id, c.parent_id, c.name, c.level, c.display_order, c.is_leaf, c.is_visible, "
                + "COALESCE(p.direct_products, 0) AS direct_products FROM categories c "
                + "LEFT JOIN (SELECT category_id, COUNT(*) AS direct_products FROM products GROUP BY category_id) p "
                + "ON c.category_id = p.category_id ORDER BY c.level, c.display_order, c.category_id";
        List<Row> list = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            while (rows.next()) list.add(new Row(rows, rows.getLong("direct_products")));
        }
        return list;
    }

    public void update(Connection connection, String id, String name, int displayOrder, boolean visible) throws SQLException {
        lock(connection, id);
        try (PreparedStatement query = connection.prepareStatement(
                "UPDATE categories SET name = ?, display_order = ?, is_visible = ? WHERE category_id = ?")) {
            query.setString(1, name);
            query.setInt(2, displayOrder);
            query.setBoolean(3, visible);
            query.setString(4, id);
            query.executeUpdate();
        }
    }

    public void insert(Connection connection, String id, String parentId, String name, int displayOrder,
            boolean visible) throws SQLException {
        int level = 1;
        if (parentId != null) {
            Row parent = lock(connection, parentId);
            if (parent.level >= 3) throw new IllegalArgumentException("카테고리는 최대 3단계까지 등록할 수 있습니다.");
            level = parent.level + 1;
            if (parent.leaf) {
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT product_id FROM products WHERE category_id = ? LIMIT 1 FOR UPDATE")) {
                    query.setString(1, parent.id);
                    try (ResultSet rows = query.executeQuery()) {
                        if (rows.next()) throw new IllegalArgumentException("상품이 연결된 최하위 카테고리에는 하위 카테고리를 추가할 수 없습니다.");
                    }
                }
                try (PreparedStatement query = connection.prepareStatement("UPDATE categories SET is_leaf = 0 WHERE category_id = ?")) {
                    query.setString(1, parent.id);
                    query.executeUpdate();
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "INSERT INTO categories (category_id, parent_id, name, level, display_order, is_leaf, is_visible) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?)")) {
            query.setString(1, id);
            query.setString(2, parentId);
            query.setString(3, name);
            query.setInt(4, level);
            query.setInt(5, displayOrder);
            query.setBoolean(6, visible);
            query.executeUpdate();
        }
    }

    private static Row lock(Connection connection, String id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT category_id, parent_id, name, level, display_order, is_leaf, is_visible "
                + "FROM categories WHERE category_id = ? FOR UPDATE")) {
            query.setString(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !id.equals(rows.getString("category_id"))) {
                    throw new IllegalArgumentException("선택한 카테고리를 찾을 수 없습니다.");
                }
                return new Row(rows, 0);
            }
        }
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

        private Row(ResultSet result, long products) throws SQLException {
            id = result.getString("category_id");
            parent = result.getString("parent_id");
            name = result.getString("name");
            level = result.getInt("level");
            order = result.getInt("display_order");
            leaf = result.getBoolean("is_leaf");
            visible = result.getBoolean("is_visible");
            this.products = products;
        }
    }
}
