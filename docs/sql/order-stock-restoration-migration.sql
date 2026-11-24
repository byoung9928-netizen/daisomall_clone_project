-- MySQL 8.0. Back up the target database and select the application's schema first.
-- Requires the existing orders.stock_deducted column and product-online-inventory-migration.sql.
-- Inspect SHOW CREATE TABLE for orders and product_admin_changes before applying.
-- Apply both ALTER statements before enabling this application version.
-- ALTER statements commit separately. If interrupted, apply only the missing ALTER.
-- Existing rows and stock are unchanged. Historical orders are not restored by this script.
-- For application rollback, retain the additive columns and all audit rows.

ALTER TABLE product_admin_changes
    DROP CHECK chk_product_admin_changes_action_v2,
    DROP CHECK chk_product_admin_stock_metadata,
    MODIFY COLUMN admin_member_id VARCHAR(100) NULL,
    ADD COLUMN order_no VARCHAR(100) NULL,
    ADD CONSTRAINT chk_product_admin_changes_action_v3
        CHECK (action IN ('CREATE','UPDATE','BULK_STATUS','DELETE','STOCK_ADJUST','STOCK_RESTORE')),
    ADD CONSTRAINT chk_product_admin_stock_metadata_v2 CHECK (
        (action NOT IN ('STOCK_ADJUST','STOCK_RESTORE') AND admin_member_id IS NOT NULL
            AND order_no IS NULL AND stock_operation IS NULL AND stock_reason IS NULL)
        OR (action = 'STOCK_ADJUST' AND admin_member_id IS NOT NULL AND order_no IS NULL
            AND stock_operation IS NOT NULL AND stock_operation IN ('RECEIVE','ISSUE','DAMAGE','RETURN','COUNT')
            AND stock_reason IS NOT NULL AND CHAR_LENGTH(TRIM(stock_reason)) > 0
            AND before_stock IS NOT NULL AND after_stock IS NOT NULL AND before_stock <> after_stock)
        OR (action = 'STOCK_RESTORE' AND admin_member_id IS NULL
            AND order_no IS NOT NULL AND CHAR_LENGTH(TRIM(order_no)) > 0
            AND stock_operation IS NOT NULL AND stock_operation IN ('ORDER_CANCEL','ORDER_REFUND')
            AND stock_reason IS NOT NULL AND CHAR_LENGTH(TRIM(stock_reason)) > 0
            AND before_stock IS NOT NULL AND before_stock >= 0 AND after_stock IS NOT NULL AND after_stock > before_stock)
    );

ALTER TABLE orders
    ADD COLUMN stock_restored_at DATETIME(6) NULL;
