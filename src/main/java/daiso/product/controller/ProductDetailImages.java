package daiso.product.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProductDetailImages {
    static final int LIMIT = 10;

    Map<Long, String> list(Connection connection, String productId) throws SQLException {
        Map<Long, String> images = new LinkedHashMap<>();
        String sql = "SELECT image_id, image_path FROM product_detail_images "
                + "WHERE product_id = ? ORDER BY display_order";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, productId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) images.put(rows.getLong("image_id"), rows.getString("image_path"));
            }
        }
        return images;
    }

    void replace(Connection connection, String productId, List<String> images) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("DELETE FROM product_detail_images WHERE product_id = ?")) {
            query.setString(1, productId);
            query.executeUpdate();
        }
        if (images.isEmpty()) return;
        try (PreparedStatement query = connection.prepareStatement(
                "INSERT INTO product_detail_images (product_id, display_order, image_path) VALUES (?, ?, ?)")) {
            int position = 0;
            for (String image : images) {
                query.setString(1, productId);
                query.setInt(2, ++position);
                query.setString(3, image);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    static String baseline(Map<Long, String> images) {
        return images.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    static List<String> order(String value, Map<Long, String> existing, List<String> uploads) {
        if (value.length() > 250) throw invalid();
        String[] keys = value.isEmpty() ? new String[0] : value.split(",", -1);
        if (keys.length > LIMIT) throw invalid();
        Map<String, String> available = new LinkedHashMap<>();
        existing.forEach((id, path) -> available.put("s" + id, path));
        for (int index = 0; index < uploads.size(); index++) available.put("n" + index, uploads.get(index));
        List<String> ordered = new java.util.ArrayList<>();
        for (String key : keys) {
            String path = available.remove(key);
            if (path == null) throw invalid();
            ordered.add(path);
        }
        if (available.keySet().stream().anyMatch(key -> key.startsWith("n"))) throw invalid();
        return ordered;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("상세 이미지 선택이나 표시 순서를 확인해 주세요.");
    }
}
