-- Apply once to the DaisoMall development database.
-- Product and administrator identifiers are snapshots so audit records survive deletion.
CREATE TABLE product_admin_changes (
    change_id BIGINT NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    admin_member_id VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    before_price INT NULL,
    after_price INT NULL,
    before_stock INT NULL,
    after_stock INT NULL,
    before_status VARCHAR(20) NULL,
    after_status VARCHAR(20) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (change_id),
    KEY idx_product_admin_changes_product (product_id, change_id),
    CONSTRAINT chk_product_admin_changes_action
        CHECK (action IN ('CREATE', 'UPDATE', 'BULK_STATUS', 'DELETE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
