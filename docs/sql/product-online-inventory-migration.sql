-- DaisoMall MySQL 8.0. Apply once after product-admin-changes.sql, with daisomall selected.
-- Back up the full database with Workbench Data Export (structure and data)
-- or mysqldump --single-transaction --no-tablespaces --databases daisomall > daisomall-before-online-inventory.sql.
-- Verify the dump completed and keep it outside Git. Avoid concurrent DDL during export.
-- products.status is VARCHAR(20) with no status CHECK, so DISCONTINUED needs no schema change.
-- Extend history first, then add the threshold. Each ALTER commits individually.
-- Existing stock, statuses, historical rows, and foreign keys are retained. Legacy audit metadata stays NULL.
-- On error, stop and inspect SHOW CREATE TABLE for both tables. Resolve the cause and apply only missing statements.
-- To revert the deployment, retain the additive columns and back up any new records before considering a schema rollback.
ALTER TABLE product_admin_changes
    DROP CHECK chk_product_admin_changes_action,
    ADD COLUMN stock_operation VARCHAR(20) NULL,
    ADD COLUMN stock_reason VARCHAR(255) NULL,
    ADD CONSTRAINT chk_product_admin_changes_action_v2
        CHECK (action IN ('CREATE', 'UPDATE', 'BULK_STATUS', 'DELETE', 'STOCK_ADJUST')),
    ADD CONSTRAINT chk_product_admin_stock_metadata CHECK (
        (action <> 'STOCK_ADJUST' AND stock_operation IS NULL AND stock_reason IS NULL)
        OR (action = 'STOCK_ADJUST' AND stock_operation IS NOT NULL
            AND stock_operation IN ('RECEIVE','ISSUE','DAMAGE','RETURN','COUNT')
            AND stock_reason IS NOT NULL AND CHAR_LENGTH(TRIM(stock_reason)) > 0
            AND before_stock IS NOT NULL AND after_stock IS NOT NULL AND before_stock <> after_stock)
    );

ALTER TABLE products
    ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 5,
    ADD CONSTRAINT chk_products_low_stock_threshold CHECK (low_stock_threshold >= 1);
