package daiso.product.dao;

import daiso.product.dto.ProductReview;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public final class ProductReviewDAO {
    private static final String ACTIVE = " FROM product_reviews WHERE product_id = ? AND deleted_at IS NULL";
    private static final String FIELDS = "SELECT review_id, rating, content, created_at, version";
    private static final String PURCHASE = "SELECT i.order_item_id FROM order_items i "
            + "JOIN orders o ON o.order_id = i.order_id "
            + "JOIN payment_attempts p ON p.order_id = o.order_id "
            + "WHERE o.member_id = ? AND i.product_id = ? "
            + "AND o.status = 'PAID' AND o.canceled_at IS NULL "
            + "AND p.method = 'KAKAOPAY' AND p.status = 'APPROVED' "
            + "AND p.tid IS NOT NULL AND p.tid <> '' AND p.approved_at IS NOT NULL "
            + "AND p.amount = o.total_amount "
            + "ORDER BY p.approved_at DESC, i.order_item_id DESC LIMIT 1";

    public enum Sort {
        NEWEST("newest", "created_at DESC, review_id DESC"),
        HIGHEST("highest", "rating DESC, created_at DESC, review_id DESC"),
        LOWEST("lowest", "rating ASC, created_at DESC, review_id DESC");

        public final String key;
        private final String sql;

        Sort(String key, String sql) {
            this.key = key;
            this.sql = sql;
        }
    }

    public static final class Summary {
        public final long count;
        public final BigDecimal average;

        private Summary(long count, BigDecimal average) {
            this.count = count;
            this.average = average;
        }
    }

    public Summary summarize(Connection connection, String productId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*), AVG(rating)" + ACTIVE)) {
            query.setString(1, productId);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return new Summary(rows.getLong(1), rows.getBigDecimal(2));
            }
        }
    }

    public List<ProductReview> list(Connection connection, String productId, String memberId,
            Sort sort, int limit, long offset) throws SQLException {
        String sql = FIELDS + ", (member_id = ?) AS owned" + ACTIVE
                + " ORDER BY " + sort.sql + " LIMIT ? OFFSET ?";
        List<ProductReview> items = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, memberId);
            query.setString(2, productId);
            query.setInt(3, limit);
            query.setLong(4, offset);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) items.add(review(rows, rows.getBoolean("owned")));
            }
        }
        return items;
    }

    public ProductReview findOwn(Connection connection, String productId, String memberId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(FIELDS + ACTIVE + " AND member_id = ?")) {
            query.setString(1, productId);
            query.setString(2, memberId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? review(rows, true) : null;
            }
        }
    }

    // 저장 시 승인 근거 행을 커밋까지 잠가 확인과 등록 사이의 상태 변경을 막는다.
    public OptionalLong findPaidItem(Connection connection, String memberId, String productId, boolean lock)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(PURCHASE + (lock ? " FOR SHARE" : ""))) {
            query.setString(1, memberId);
            query.setString(2, productId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? OptionalLong.of(rows.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    public void insert(Connection connection, String productId, String memberId, long orderItemId,
            int rating, String content) throws SQLException {
        String sql = "INSERT INTO product_reviews (product_id, member_id, order_item_id, rating, content) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, productId);
            query.setString(2, memberId);
            query.setLong(3, orderItemId);
            query.setInt(4, rating);
            query.setString(5, content);
            query.executeUpdate();
        }
    }

    // 원래 주문상품 연결은 수정하지 않는다. 버전 조건으로 오래된 화면의 덮어쓰기를 막는다.
    public boolean changeOwn(Connection connection, String productId, String memberId, long reviewId,
            long version, boolean deleting, int rating, String content) throws SQLException {
        String changes = deleting ? "deleted_at = CURRENT_TIMESTAMP(6)" : "rating = ?, content = ?";
        String sql = "UPDATE product_reviews SET " + changes + ", version = version + 1 "
                + "WHERE review_id = ? AND product_id = ? AND member_id = ? AND version = ? "
                + "AND deleted_at IS NULL";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            int n = 1;
            if (!deleting) {
                query.setInt(n++, rating);
                query.setString(n++, content);
            }
            query.setLong(n++, reviewId);
            query.setString(n++, productId);
            query.setString(n++, memberId);
            query.setLong(n, version);
            return query.executeUpdate() == 1;
        }
    }

    private static ProductReview review(ResultSet rows, boolean owner) throws SQLException {
        return new ProductReview(rows.getLong("review_id"), rows.getInt("rating"), rows.getString("content"),
                rows.getObject("created_at", LocalDateTime.class), owner, rows.getLong("version"));
    }
}
