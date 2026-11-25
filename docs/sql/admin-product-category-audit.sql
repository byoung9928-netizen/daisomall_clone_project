-- MySQL 8. Apply after product-admin-changes.sql. No existing rows are updated.
-- Optional for the new bulk category workflow; other product operations keep their current schema.
ALTER TABLE product_admin_changes
    ADD COLUMN before_category_id VARCHAR(100) NULL,
    ADD COLUMN after_category_id VARCHAR(100) NULL;
