-- MySQL 8. Apply after the product reviews tables. No existing review rows are changed.
CREATE TABLE product_review_admin_decisions (
    decision_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    admin_member_id VARCHAR(100) NOT NULL,
    action ENUM('PUBLISHED','HIDDEN','DELETED','DISMISS') NOT NULL,
    before_state VARCHAR(20) NOT NULL,
    after_state VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_review_decisions_review (review_id, decision_id),
    CONSTRAINT fk_review_decisions_review FOREIGN KEY (review_id)
        REFERENCES product_reviews(review_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
