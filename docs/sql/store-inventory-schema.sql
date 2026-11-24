-- Personal development database. Verify the products key and take a backup before applying.
-- Final schema for new installations. Older databases use the migration scripts instead.
CREATE TABLE IF NOT EXISTS store_locations (
    store_id VARCHAR(40) NOT NULL,
    store_name VARCHAR(100) NOT NULL,
    city VARCHAR(20) NOT NULL,
    PRIMARY KEY (store_id),
    KEY idx_store_locations_city (city, store_name, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS store_inventory (
    store_id VARCHAR(40) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    is_carried TINYINT NOT NULL DEFAULT 1,
    low_stock_threshold INT NOT NULL DEFAULT 5,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (store_id, product_id),
    KEY idx_store_inventory_product (product_id),
    KEY idx_store_inventory_quantity (store_id, quantity, product_id),
    KEY idx_store_inventory_carried_qty (store_id, is_carried, quantity, product_id),
    CONSTRAINT chk_store_inventory_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_store_inventory_threshold CHECK (low_stock_threshold >= 1),
    CONSTRAINT chk_store_inventory_carried CHECK (
        is_carried IN (0, 1) AND (is_carried = 1 OR quantity = 0)
    ),
    CONSTRAINT fk_store_inventory_location FOREIGN KEY (store_id)
        REFERENCES store_locations (store_id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_inventory_product FOREIGN KEY (product_id)
        REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS store_inventory_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    store_id VARCHAR(40) NOT NULL,
    product_id VARCHAR(100) DEFAULT NULL,
    product_reference VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    change_type ENUM('INITIAL','RECEIVE','ISSUE','DAMAGE','RETURN','COUNT','REGISTER','RESUME','UNLIST','THRESHOLD') NOT NULL,
    before_quantity INT NOT NULL,
    after_quantity INT NOT NULL,
    before_threshold INT DEFAULT NULL,
    after_threshold INT DEFAULT NULL,
    reason VARCHAR(255) NOT NULL,
    administrator_id VARCHAR(100) DEFAULT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (history_id),
    KEY idx_store_inventory_history_date (store_id, changed_at DESC, history_id DESC),
    KEY idx_store_inventory_history_product (product_id),
    CONSTRAINT chk_store_inventory_history_quantities CHECK (before_quantity >= 0 AND after_quantity >= 0),
    CONSTRAINT chk_store_inventory_history_threshold CHECK (
        (change_type = 'THRESHOLD' AND before_threshold IS NOT NULL AND after_threshold IS NOT NULL
            AND before_threshold >= 1 AND after_threshold >= 1 AND before_threshold <> after_threshold
            AND before_quantity = after_quantity)
        OR (change_type <> 'THRESHOLD' AND before_threshold IS NULL AND after_threshold IS NULL)
    ),
    CONSTRAINT chk_store_inventory_history_actor_v3 CHECK (
        (change_type = 'INITIAL' AND administrator_id IS NULL) OR
        (change_type <> 'INITIAL' AND administrator_id IS NOT NULL)
    ),
    CONSTRAINT fk_store_inventory_history_store FOREIGN KEY (store_id)
        REFERENCES store_locations (store_id) ON DELETE RESTRICT,
    CONSTRAINT fk_store_inventory_history_product FOREIGN KEY (product_id)
        REFERENCES products (product_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
