-- MySQL 8.0: select daisomall after backing up the database.
-- Requires category-admin-history.sql; skip that file if category_admin_changes already exists.
-- Extend the ENUM by appending DELETE so existing values and stored audit rows are preserved.
-- The audit table has no category FK, so its records survive a category deletion.
-- Run once before enabling deletion. On error inspect SHOW CREATE TABLE category_admin_changes.
ALTER TABLE category_admin_changes
    MODIFY COLUMN operation ENUM('CREATE','NAME','VISIBILITY','ORDER','DELETE') NOT NULL;
