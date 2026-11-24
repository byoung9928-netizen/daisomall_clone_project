-- MySQL 8.0. Select the intended DaisoMall schema and back it up before applying.
-- Check that members.member_id and products.product_id match VARCHAR(100) / utf8mb4_0900_ai_ci.
-- CREATE adds only this table. The two foreign keys remove a view when its member or product is deleted.
-- A single row per member and product stores the most recent Korean calendar day.
CREATE TABLE IF NOT EXISTS product_view_history (
    member_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    last_viewed_on DATE NOT NULL,
    PRIMARY KEY (member_id, product_id),
    KEY idx_product_view_member_date (member_id, last_viewed_on DESC, product_id),
    KEY idx_product_view_product (product_id),
    CONSTRAINT fk_product_view_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE CASCADE,
    CONSTRAINT fk_product_view_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
