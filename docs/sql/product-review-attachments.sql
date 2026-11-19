-- Apply to the personal DaisoMall database after explicit approval.
CREATE TABLE product_review_answers (
    review_id BIGINT NOT NULL,
    question_key VARCHAR(40) NOT NULL,
    question_label VARCHAR(100) NOT NULL,
    answer_value TINYINT UNSIGNED NOT NULL,
    answer_label VARCHAR(50) NOT NULL,
    display_order TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (review_id, question_key),
    CONSTRAINT fk_product_review_answers_review FOREIGN KEY (review_id)
        REFERENCES product_reviews (review_id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_review_answer_value CHECK (answer_value BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_review_photos (
    photo_id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    photo_bytes MEDIUMBLOB NOT NULL,
    PRIMARY KEY (photo_id),
    KEY idx_product_review_photos_review (review_id, photo_id),
    CONSTRAINT fk_product_review_photos_review FOREIGN KEY (review_id)
        REFERENCES product_reviews (review_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
