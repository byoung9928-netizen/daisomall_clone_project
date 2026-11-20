package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import daiso.product.dao.AdminCategoryDAO.Row;

public final class AdminCategoryHistoryDAO {
    public enum Action {
        CREATE("신규 등록"), NAME("이름 변경"), VISIBILITY("노출 변경"), ORDER("표시 순서 변경"), DELETE("카테고리 삭제");

        public final String label;
        Action(String label) { this.label = label; }
    }

    public boolean available(Connection connection) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = 'category_admin_changes'";
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            return rows.next();
        }
    }

    public boolean supportsDeletion(Connection connection) throws SQLException {
        String sql = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'category_admin_changes' AND COLUMN_NAME = 'operation'";
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            return rows.next() && rows.getString(1).contains("'DELETE'");
        }
    }

    public int highestIssuedId(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(category_id, 6) AS UNSIGNED)), 0) "
                + "FROM category_admin_changes WHERE category_id REGEXP '^CTGR_[0-9]{5}$'";
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    public void record(Connection connection, Row category, String actor, Action action, String before, String after)
            throws SQLException {
        record(connection, category.id, category.name, actor, action, before, after);
    }

    public void record(Connection connection, String id, String name, String actor, Action action,
            String before, String after) throws SQLException {
        String sql = "INSERT INTO category_admin_changes "
                + "(category_id, category_name, administrator_id, operation, before_value, after_value) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, id);
            query.setString(2, name);
            query.setString(3, actor);
            query.setString(4, action.name());
            query.setString(5, before);
            query.setString(6, after);
            query.executeUpdate();
        }
    }

    public List<Entry> list(Connection connection, String id, int limit, int offset) throws SQLException {
        String sql = "SELECT operation, before_value, after_value, administrator_id, changed_at "
                + "FROM category_admin_changes WHERE category_id = ? ORDER BY history_id DESC LIMIT ? OFFSET ?";
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, id);
            query.setInt(2, limit);
            query.setInt(3, offset);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) entries.add(new Entry(rows));
            }
        }
        return entries;
    }

    public static final class Entry {
        public final String action;
        public final String before;
        public final String after;
        public final String actor;
        public final LocalDateTime time;

        private Entry(ResultSet row) throws SQLException {
            action = Action.valueOf(row.getString("operation")).label;
            before = row.getString("before_value");
            after = row.getString("after_value");
            actor = row.getString("administrator_id");
            time = row.getObject("changed_at", LocalDateTime.class);
        }
    }
}
