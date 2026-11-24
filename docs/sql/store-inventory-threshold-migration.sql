-- MySQL 8.0. For an existing database with store-assortment-migration.sql already applied.
-- New installations use store-inventory-schema.sql, which already contains this structure.
-- Select the application's database and back up store_inventory and store_inventory_history first.
-- Verify the baseline with SHOW CREATE TABLE on both tables. Run the two ALTERs in order, once.
-- Existing rows retain quantity, is_carried and version. Their new threshold defaults to 5.
-- Existing history rows retain their original type and have NULL threshold values.
-- Each ALTER commits independently. If interrupted, inspect SHOW CREATE TABLE and execute
-- only the missing ALTER after resolving the error. No data seeding is required.

ALTER TABLE store_inventory
    ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 5,
    ADD CONSTRAINT chk_store_inventory_threshold CHECK (low_stock_threshold >= 1);

ALTER TABLE store_inventory_history
    DROP CHECK chk_store_inventory_history_actor_v2,
    MODIFY COLUMN change_type ENUM('INITIAL','RECEIVE','ISSUE','DAMAGE','RETURN','COUNT','REGISTER','RESUME','UNLIST','THRESHOLD') NOT NULL,
    ADD COLUMN before_threshold INT DEFAULT NULL,
    ADD COLUMN after_threshold INT DEFAULT NULL,
    ADD CONSTRAINT chk_store_inventory_history_actor_v3 CHECK (
        (change_type = 'INITIAL' AND administrator_id IS NULL) OR
        (change_type <> 'INITIAL' AND administrator_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_store_inventory_history_threshold CHECK (
        (change_type = 'THRESHOLD' AND before_threshold IS NOT NULL AND after_threshold IS NOT NULL
            AND before_threshold >= 1 AND after_threshold >= 1 AND before_threshold <> after_threshold
            AND before_quantity = after_quantity)
        OR (change_type <> 'THRESHOLD' AND before_threshold IS NULL AND after_threshold IS NULL)
    );
