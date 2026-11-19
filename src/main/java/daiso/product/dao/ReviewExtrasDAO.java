package daiso.product.dao;

import daiso.product.dto.ReviewExtras.Answer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReviewExtrasDAO {
    public boolean available(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name IN ('product_review_answers', 'product_review_photos')";
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            return rows.next() && rows.getInt(1) == 2;
        }
    }

    public List<String> categoryPath(Connection connection, String productId) throws SQLException {
        String sql = "WITH RECURSIVE path AS ("
                + "SELECT c.category_id, c.parent_id, 0 AS distance FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id WHERE p.product_id = ? "
                + "UNION ALL SELECT c.category_id, c.parent_id, path.distance + 1 FROM path "
                + "JOIN categories c ON c.category_id = path.parent_id) "
                + "SELECT category_id FROM path ORDER BY distance";
        List<String> categories = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, productId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) categories.add(rows.getString(1));
            }
        }
        return categories;
    }

    public Map<Long, List<Answer>> answers(Connection connection, List<Long> reviewIds) throws SQLException {
        if (reviewIds.isEmpty()) return Map.of();
        String sql = "SELECT review_id, question_key, question_label, answer_value, answer_label "
                + "FROM product_review_answers WHERE review_id IN (" + placeholders(reviewIds) + ") "
                + "ORDER BY review_id, display_order";
        Map<Long, List<Answer>> result = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bind(query, reviewIds);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.computeIfAbsent(rows.getLong(1), id -> new ArrayList<>())
                        .add(new Answer(rows.getString(2), rows.getString(3), rows.getInt(4), rows.getString(5)));
            }
        }
        return result;
    }

    public Map<Long, List<Long>> photos(Connection connection, List<Long> reviewIds) throws SQLException {
        if (reviewIds.isEmpty()) return Map.of();
        String sql = "SELECT review_id, photo_id FROM product_review_photos WHERE review_id IN ("
                + placeholders(reviewIds) + ") ORDER BY review_id, photo_id";
        Map<Long, List<Long>> result = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bind(query, reviewIds);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.computeIfAbsent(rows.getLong(1), id -> new ArrayList<>()).add(rows.getLong(2));
            }
        }
        return result;
    }

    public byte[] visiblePhoto(Connection connection, long photoId) throws SQLException {
        String sql = "WITH RECURSIVE visible AS ("
                + "SELECT category_id FROM categories WHERE parent_id IS NULL AND is_visible = 1 "
                + "UNION ALL SELECT c.category_id FROM categories c JOIN visible v "
                + "ON c.parent_id = v.category_id WHERE c.is_visible = 1) "
                + "SELECT ph.photo_bytes FROM product_review_photos ph "
                + "JOIN product_reviews r ON r.review_id = ph.review_id AND r.deleted_at IS NULL "
                + "JOIN products p ON p.product_id = r.product_id "
                + "JOIN visible v ON v.category_id = p.category_id WHERE ph.photo_id = ?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, photoId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getBytes(1) : null;
            }
        }
    }

    public void replaceAnswers(Connection connection, long reviewId, List<Answer> answers) throws SQLException {
        executeByReview(connection, "DELETE FROM product_review_answers WHERE review_id = ?", reviewId);
        if (answers.isEmpty()) return;
        String sql = "INSERT INTO product_review_answers "
                + "(review_id, question_key, question_label, answer_value, answer_label, display_order) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            int index = 0;
            for (Answer answer : answers) {
                query.setLong(1, reviewId);
                query.setString(2, answer.key);
                query.setString(3, answer.question);
                query.setInt(4, answer.value);
                query.setString(5, answer.label);
                query.setInt(6, index++);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    public void removePhotos(Connection connection, long reviewId, List<Long> photoIds) throws SQLException {
        if (photoIds.isEmpty()) return;
        String sql = "DELETE FROM product_review_photos WHERE photo_id IN (" + placeholders(photoIds) + ") AND review_id = ?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bind(query, photoIds);
            query.setLong(photoIds.size() + 1, reviewId);
            query.executeUpdate();
        }
    }

    public void removeAllPhotos(Connection connection, long reviewId) throws SQLException {
        executeByReview(connection, "DELETE FROM product_review_photos WHERE review_id = ?", reviewId);
    }

    public void addPhotos(Connection connection, long reviewId, List<byte[]> photos) throws SQLException {
        if (photos.isEmpty()) return;
        try (PreparedStatement query = connection.prepareStatement(
                "INSERT INTO product_review_photos (review_id, photo_bytes) VALUES (?, ?)")) {
            for (byte[] photo : photos) {
                query.setLong(1, reviewId);
                query.setBytes(2, photo);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    private static void executeByReview(Connection connection, String sql, long reviewId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, reviewId);
            query.executeUpdate();
        }
    }

    private static String placeholders(List<Long> ids) {
        return String.join(", ", Collections.nCopies(ids.size(), "?"));
    }

    private static void bind(PreparedStatement query, List<Long> ids) throws SQLException {
        for (int index = 0; index < ids.size(); index++) query.setLong(index + 1, ids.get(index));
    }
}
