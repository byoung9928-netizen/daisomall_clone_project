-- MySQL 8. Apply to the existing store inventory history after the store threshold migration.
-- NULL keeps existing adjustments unchanged. One row per store in any transfer.
ALTER TABLE store_inventory_history
    ADD COLUMN transfer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uq_store_inventory_transfer (transfer_id, store_id);
