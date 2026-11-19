package daiso.product.dao;

public final class ReviewVisibility {
    // 공개 리뷰를 조회할 때 product_reviews 테이블의 SQL 별칭은 rv를 사용한다.
    public static final String PUBLISHED = " AND NOT EXISTS (SELECT 1 FROM product_review_moderation rm "
            + "WHERE rm.review_id = rv.review_id AND rm.moderation_state IN ('HIDDEN', 'DELETED'))";

    private ReviewVisibility() { }
}
