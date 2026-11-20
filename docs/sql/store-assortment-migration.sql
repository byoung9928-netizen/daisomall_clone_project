-- MySQL 8.0, run once after store-inventory-schema.sql with daisomall selected.
-- Back up the target database and apply before publishing the new administrator views.
-- The two ALTER statements commit individually. If only one succeeds, inspect SHOW CREATE TABLE
-- and run only the remaining statement after fixing the cause. No seed rerun is needed.
ALTER TABLE store_inventory_history
    DROP CHECK chk_store_inventory_history_actor,
    MODIFY COLUMN change_type
        ENUM('INITIAL','RECEIVE','ISSUE','DAMAGE','RETURN','COUNT','REGISTER','RESUME','UNLIST') NOT NULL,
    ADD CONSTRAINT chk_store_inventory_history_actor_v2 CHECK (
        (change_type = 'INITIAL' AND administrator_id IS NULL) OR
        (change_type <> 'INITIAL' AND administrator_id IS NOT NULL)
    );

ALTER TABLE store_inventory
    ADD COLUMN is_carried TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_store_inventory_carried CHECK (
        is_carried IN (0, 1) AND (is_carried = 1 OR quantity = 0)
    ),
    ADD KEY idx_store_inventory_carried_qty (store_id, is_carried, quantity, product_id);
