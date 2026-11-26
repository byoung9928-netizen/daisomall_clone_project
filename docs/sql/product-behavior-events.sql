-- MySQL 8.0. Select and back up the intended DaisoMall schema before applying.
-- Verify that members.member_id and products.product_id use VARCHAR(100) and utf8mb4_0900_ai_ci.
-- This table starts empty. occurred_at uses UTC. Existing data and recommendation tables are unchanged.
-- Deleting a member or a product also deletes its behavior records.
CREATE TABLE IF NOT EXISTS product_behavior_events (
    event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    event_type ENUM('VIEW', 'WISHLIST_ADD', 'WISHLIST_REMOVE', 'CART_ADD', 'CART_REMOVE') NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    KEY idx_behavior_time (occurred_at, event_type),
    KEY idx_behavior_product_time (product_id, occurred_at, event_type),
    KEY idx_behavior_member_time (member_id, occurred_at, event_type),
    KEY idx_behavior_view_window (member_id, product_id, event_type, occurred_at),
    CONSTRAINT fk_behavior_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_behavior_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
