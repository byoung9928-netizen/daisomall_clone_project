-- MySQL 8. Apply after members and product_reviews exist, including on a fresh installation.
-- Adds an empty table; existing accounts and reviews are untouched.
CREATE TABLE IF NOT EXISTS product_review_reactions (
    review_id BIGINT NOT NULL,
    member_id VARCHAR(100) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    reaction_type ENUM('HELPFUL', 'PURCHASE_INTEREST', 'QUESTION_RESOLVED') NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (review_id, reaction_type, member_id),
    KEY ix_review_reaction_member (member_id),
    CONSTRAINT fk_review_reaction_review FOREIGN KEY (review_id)
        REFERENCES product_reviews (review_id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_reaction_member FOREIGN KEY (member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
