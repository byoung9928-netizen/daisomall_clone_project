-- MySQL 8. Apply after members and product_reviews exist, including on a fresh installation.
-- The application also uses the existing product-review-moderation.sql.
CREATE TABLE IF NOT EXISTS product_review_reports (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    reporter_member_id VARCHAR(100) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    reason_code ENUM('OFF_TOPIC', 'ABUSE', 'ADVERTISING', 'PERSONAL_INFORMATION', 'SPAM',
                     'SEXUAL_CONTENT', 'MALWARE', 'COPYRIGHT', 'OTHER') NOT NULL,
    status ENUM('PENDING', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    processed_by VARCHAR(100) COLLATE utf8mb4_0900_ai_ci NULL,
    PRIMARY KEY (report_id),
    UNIQUE KEY uq_review_report_member (review_id, reporter_member_id),
    KEY ix_review_report_status (review_id, status),
    KEY ix_review_report_reporter (reporter_member_id),
    KEY ix_review_report_processor (processed_by),
    CONSTRAINT fk_review_report_review FOREIGN KEY (review_id)
        REFERENCES product_reviews (review_id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_report_reporter FOREIGN KEY (reporter_member_id)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_report_processor FOREIGN KEY (processed_by)
        REFERENCES members (member_id) ON DELETE RESTRICT,
    CONSTRAINT ck_review_report_processed CHECK (
        (status = 'PENDING' AND processed_at IS NULL AND processed_by IS NULL)
        OR (status IN ('RESOLVED', 'DISMISSED') AND processed_at IS NOT NULL AND processed_by IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
