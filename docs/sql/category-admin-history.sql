-- DaisoMall MySQL 8.0. Run once with daisomall selected after the existing category schema.
-- Back up structure and data with MySQL Workbench or:
-- mysqldump --single-transaction --no-tablespaces --databases daisomall > daisomall-before-category-history.sql
-- Keep the backup outside Git. This file only creates an audit table. It does not backfill or update categories.
-- On failure, inspect SHOW CREATE TABLE category_admin_changes and resolve the cause before retrying.
-- Audit identifiers are snapshots. Existing category and member tables need no changes.
CREATE TABLE category_admin_changes (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    category_id VARCHAR(100) NOT NULL,
    category_name VARCHAR(120) NOT NULL,
    administrator_id VARCHAR(100) NOT NULL,
    operation ENUM('CREATE','NAME','VISIBILITY','ORDER') NOT NULL,
    before_value VARCHAR(255) DEFAULT NULL,
    after_value VARCHAR(255) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (history_id),
    KEY idx_category_admin_changes_category (category_id, history_id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
