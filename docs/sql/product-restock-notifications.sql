-- MySQL 8. Back up the schema and inspect SHOW TRIGGERS LIKE 'products' before applying.
-- Both DDL statements must succeed before enabling the feature. Creation neither changes
-- existing stock nor notifies users about past restocks. DDL auto-commits.
-- If trigger creation fails, inspect the error and install it before deploying the feature.
-- Rollback after approval: remove the trigger first. Keep subscription rows for review.
CREATE TABLE product_restock_requests (
    request_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notified_at DATETIME(6) NULL,
    pending_slot TINYINT AS (IF(notified_at IS NULL, 1, NULL)) STORED,
    UNIQUE KEY uq_restock_pending (product_id, member_id, pending_slot),
    KEY ix_restock_trigger (product_id, notified_at),
    KEY ix_restock_inbox (member_id, request_id DESC),
    CONSTRAINT fk_restock_member FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE,
    CONSTRAINT fk_restock_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER //
CREATE TRIGGER notify_product_restock AFTER UPDATE ON products
FOR EACH ROW
BEGIN
    IF NEW.status = 'AVAILABLE' AND NEW.stock_quantity > 0
       AND (OLD.status <> 'AVAILABLE' OR OLD.stock_quantity <= 0) THEN
        UPDATE product_restock_requests
        SET notified_at = CURRENT_TIMESTAMP(6)
        WHERE product_id = NEW.product_id AND notified_at IS NULL;
    END IF;
END//
DELIMITER ;
