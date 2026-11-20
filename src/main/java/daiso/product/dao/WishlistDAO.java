package daiso.product.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import daiso.product.controller.ProductDTO;
import daiso.product.dto.WishlistItem;

/** SQL의 회원 조건은 항상 서버에서 확인한 회원 ID로 바인딩합니다. */
public final class WishlistDAO {
    // 상품별 반복 조회 대신 현재 화면의 찜 상태를 한 번에 조회
    public Set<String> findSelected(Connection connection, String memberId, Collection<String> productIds)
            throws SQLException {
        if (productIds.isEmpty()) return Set.of();
        String placeholders = String.join(",", Collections.nCopies(productIds.size(), "?"));
        String sql = "SELECT product_id FROM product_wishlists WHERE member_id = ? AND product_id IN ("
                + placeholders + ")";
        Set<String> selected = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId);
            int index = 2;
            for (String productId : productIds) statement.setString(index++, productId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) selected.add(rows.getString("product_id"));
            }
        }
        return Set.copyOf(selected);
    }

    // 등록 중 상품이 삭제되지 않도록 같은 트랜잭션에서 상품 행 확인
    public boolean lockProduct(Connection connection, String productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id FROM products WHERE product_id = ? FOR SHARE")) {
            statement.setString(1, productId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    // 동일한 등록 요청은 기존 찜 시각을 바꾸지 않음
    public void add(Connection connection, String memberId, String productId) throws SQLException {
        String sql = "INSERT INTO product_wishlists (member_id, product_id) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE member_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId);
            statement.setString(2, productId);
            statement.setString(3, memberId);
            statement.executeUpdate();
        }
    }

    // 이미 해제된 상품에 같은 요청을 보내도 해제 상태 유지
    public void remove(Connection connection, String memberId, String productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM product_wishlists WHERE member_id = ? AND product_id = ?")) {
            statement.setString(1, memberId);
            statement.setString(2, productId);
            statement.executeUpdate();
        }
    }

    public void removeSelected(Connection connection, String memberId, Collection<String> productIds) throws SQLException {
        if (productIds.isEmpty()) return;
        String sql = "DELETE FROM product_wishlists WHERE member_id = ? AND product_id IN ("
                + String.join(",", Collections.nCopies(productIds.size(), "?")) + ")";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, memberId);
            int index = 2;
            for (String productId : productIds) query.setString(index++, productId);
            query.executeUpdate();
        }
    }

    // 품절 및 숨겨진 카테고리 상품도 찜 목록에는 유지
    public int count(Connection connection, String memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM product_wishlists WHERE member_id = ?")) {
            statement.setString(1, memberId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    public List<WishlistItem> findPage(Connection connection, String memberId, int limit, int offset)
            throws SQLException {
        String sql = "SELECT p.product_id, p.name, p.price, p.stock_quantity, p.status, "
                + "p.image_url, p.product_code, w.created_at FROM product_wishlists w "
                + "JOIN products p ON p.product_id = w.product_id WHERE w.member_id = ? "
                + "ORDER BY w.created_at DESC, w.product_id DESC LIMIT ? OFFSET ?";
        List<WishlistItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, memberId);
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ProductDTO product = new ProductDTO();
                    product.setProductId(rows.getString("product_id"));
                    product.setName(rows.getString("name"));
                    product.setPrice(rows.getInt("price"));
                    product.setStockQuantity(rows.getInt("stock_quantity"));
                    product.setStatus(rows.getString("status"));
                    product.setImage(rows.getString("image_url"));
                    product.setProductCode(rows.getString("product_code"));
                    items.add(new WishlistItem(product, rows.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return items;
    }
}
