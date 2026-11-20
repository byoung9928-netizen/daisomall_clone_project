package daiso.product.dao;

import daiso.product.controller.ProductDTO;
import daiso.product.dto.MyReviewItem;
import daiso.product.dto.ProductReview;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public final class ProductReviewDAO {
    private static final String ACTIVE = " FROM product_reviews rv WHERE rv.product_id = ? AND rv.deleted_at IS NULL";
    // 리뷰 작성 당시 연결한 주문상품의 전액 환불 완료 여부를 조회한다.
    private static final String FIELDS = "SELECT review_id, rating, content, created_at, version, "
            + "EXISTS (SELECT 1 FROM order_items i JOIN orders o ON o.order_id = i.order_id "
            + "JOIN payment_attempts p ON p.order_id = o.order_id "
            + "WHERE i.order_item_id = rv.order_item_id "
            + "AND i.product_id = rv.product_id AND o.member_id = rv.member_id "
            + "AND o.status = 'CANCELLED' AND o.canceled_at IS NOT NULL "
            + "AND p.method = 'KAKAOPAY' AND p.status = 'CANCELLED' AND p.approved_at IS NOT NULL "
            + "AND p.tid IS NOT NULL AND p.tid <> '' AND p.amount = o.total_amount) AS refunded";
    private static final String APPROVED_PURCHASE = "o.status = 'PAID' AND o.canceled_at IS NULL "
            + "AND p.method = 'KAKAOPAY' AND p.status = 'APPROVED' "
            + "AND p.tid IS NOT NULL AND p.tid <> '' AND p.approved_at IS NOT NULL AND p.amount = o.total_amount";
    private static final String PURCHASE = "SELECT i.order_item_id FROM order_items i "
            + "JOIN orders o ON o.order_id = i.order_id "
            + "JOIN payment_attempts p ON p.order_id = o.order_id "
            + "WHERE o.member_id = ? AND i.product_id = ? "
            + "AND " + APPROVED_PURCHASE + " ORDER BY p.approved_at DESC, i.order_item_id DESC LIMIT 1";

    private static final String VISIBLE = "WITH RECURSIVE visible AS ("
            + "SELECT category_id FROM categories WHERE parent_id IS NULL AND is_visible = 1 "
            + "UNION ALL SELECT c.category_id FROM categories c JOIN visible v "
            + "ON c.parent_id = v.category_id WHERE c.is_visible = 1) ";
    private static final String MY_PRODUCT = "pr.product_id, pr.name, pr.image_url, pr.product_code, ";

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
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*), AVG(rating)" + ACTIVE + ReviewVisibility.PUBLISHED)) {
            query.setString(1, productId);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return new Summary(rows.getLong(1), rows.getBigDecimal(2));
            }
        }
    }

    public List<ProductReview> list(Connection connection, String productId, String memberId,
            Sort sort, int limit, long offset) throws SQLException {
        String sql = FIELDS + ", (member_id = ?) AS owned" + ACTIVE + ReviewVisibility.PUBLISHED
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

    public List<MyReviewItem> mine(Connection connection, String member, boolean written, boolean oldest,
            int limit, int offset) throws SQLException {
        String sql;
        if (written) {
            sql = VISIBLE + "SELECT " + MY_PRODUCT + "r.review_id, r.rating, r.content, r.created_at AS event_at, "
                    + "COALESCE(rm.moderation_state, 'PUBLISHED') AS moderation_state, v.category_id IS NOT NULL AS visible "
                    + "FROM product_reviews r JOIN products pr ON pr.product_id = r.product_id "
                    + "LEFT JOIN visible v ON pr.category_id = v.category_id "
                    + "LEFT JOIN product_review_moderation rm ON rm.review_id = r.review_id "
                    + "WHERE r.member_id = ? AND r.deleted_at IS NULL";
        } else {
            sql = VISIBLE + ", eligible AS (SELECT i.product_id, MAX(p.approved_at) AS event_at "
                    + "FROM order_items i JOIN orders o ON o.order_id = i.order_id "
                    + "JOIN payment_attempts p ON p.order_id = o.order_id WHERE o.member_id = ? AND " + APPROVED_PURCHASE
                    + " AND NOT EXISTS(SELECT 1 FROM product_reviews r WHERE r.member_id = o.member_id "
                    + "AND r.product_id = i.product_id AND r.deleted_at IS NULL) GROUP BY i.product_id) "
                    + "SELECT " + MY_PRODUCT + "0 AS review_id, 0 AS rating, NULL AS content, e.event_at, "
                    + "NULL AS moderation_state, TRUE AS visible FROM eligible e JOIN products pr ON pr.product_id = e.product_id "
                    + "JOIN visible v ON pr.category_id = v.category_id";
        }
        sql += " ORDER BY event_at " + (oldest ? "ASC" : "DESC") + ", pr.product_id LIMIT ? OFFSET ?";
        List<MyReviewItem> items = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, member);
            query.setInt(2, limit);
            query.setInt(3, offset);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    ProductDTO product = new ProductDTO();
                    product.setProductId(rows.getString("product_id"));
                    product.setName(rows.getString("name"));
                    product.setProductCode(rows.getString("product_code"));
                    product.setImage(rows.getString("image_url"));
                    items.add(new MyReviewItem(product, rows.getObject("event_at", LocalDateTime.class),
                            rows.getLong("review_id"), rows.getInt("rating"), rows.getString("content"),
                            rows.getString("moderation_state"), rows.getBoolean("visible")));
                }
            }
        }
        return items;
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

    public long insert(Connection connection, String productId, String memberId, long orderItemId,
            int rating, String content) throws SQLException {
        String sql = "INSERT INTO product_reviews (product_id, member_id, order_item_id, rating, content) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement query = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            query.setString(1, productId);
            query.setString(2, memberId);
            query.setLong(3, orderItemId);
            query.setInt(4, rating);
            query.setString(5, content);
            query.executeUpdate();
            try (ResultSet keys = query.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
                throw new SQLException("저장한 리뷰 ID를 확인할 수 없습니다.");
            }
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
                rows.getObject("created_at", LocalDateTime.class), owner, rows.getLong("version"),
                rows.getBoolean("refunded"));
    }
}
