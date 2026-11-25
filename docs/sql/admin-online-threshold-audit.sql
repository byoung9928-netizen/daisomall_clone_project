-- MySQL 8. Apply to product_admin_changes after the online inventory migration.
-- Existing audit rows stay unchanged. Required for bulk threshold edits.
ALTER TABLE product_admin_changes
    ADD COLUMN before_threshold INT NULL,
    ADD COLUMN after_threshold INT NULL,
    ADD COLUMN threshold_reason VARCHAR(255) NULL;
