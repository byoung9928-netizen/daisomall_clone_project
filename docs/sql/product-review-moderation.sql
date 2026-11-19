-- Run before deploying the review moderation code to this development database.
-- Review content and attachments stay in the original review tables.
CREATE TABLE product_review_moderation (
    review_id BIGINT NOT NULL,
    moderation_state VARCHAR(12) NOT NULL,
    admin_member_id VARCHAR(100) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (review_id),
    CONSTRAINT fk_product_review_moderation_review FOREIGN KEY (review_id)
        REFERENCES product_reviews (review_id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_review_moderation_state
        CHECK (moderation_state IN ('PUBLISHED', 'HIDDEN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
