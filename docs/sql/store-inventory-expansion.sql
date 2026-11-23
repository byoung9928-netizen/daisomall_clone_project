-- MySQL 8.0. Run as a whole in a fresh connection to the personal development daisomall database.
-- Preview is the default. Only after approval and backup, execute the SET below in the same connection
-- immediately before running this file. The flag is consumed once.
-- SET @apply_store_inventory_expansion = 1;
-- Backup before apply (save outside the repository and verify the exit status and dump contents):
-- mysqldump --host=127.0.0.1 --user=YOUR_USER --password --single-transaction --no-tablespaces --result-file=PATH_OUTSIDE_REPO/daisomall-before-store-expansion.sql daisomall
-- Use a fail-fast client (mysql batch without --force). In a GUI, stop on any error and ROLLBACK
-- in the same connection, or close it. After commit, restore from a verified backup if needed.

SET @exp_apply = IF(COALESCE(@apply_store_inventory_expansion, 0) = 1 AND DATABASE() = 'daisomall', 1, 0);
SET @apply_store_inventory_expansion = 0;
SET @exp_stock_rows = -1, @exp_history_rows = -1;

DROP TEMPORARY TABLE IF EXISTS tmp_store_expansion_targets, tmp_store_expansion_totals, tmp_store_expansion_plan;
CREATE TEMPORARY TABLE tmp_store_expansion_targets (
    store_id VARCHAR(40) NOT NULL PRIMARY KEY,
    store_no INT NOT NULL,
    target_count INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TEMPORARY TABLE tmp_store_expansion_totals (
    store_id VARCHAR(40) NOT NULL PRIMARY KEY,
    store_no INT NOT NULL,
    target_count INT NOT NULL,
    store_exists TINYINT NOT NULL,
    carried_count INT NOT NULL,
    ended_count INT NOT NULL,
    needed INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TEMPORARY TABLE tmp_store_expansion_plan (
    store_id VARCHAR(40) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    root_id VARCHAR(100) NOT NULL,
    category_id VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    quantity INT NOT NULL CHECK (quantity BETWEEN 0 AND 50),
    is_common TINYINT NOT NULL,
    PRIMARY KEY (store_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tmp_store_expansion_targets (store_id, store_no, target_count) VALUES
    ('SEOUL_GANGNAM', 0, 1200),
    ('SEOUL_HONGDAE', 1, 1000),
    ('BUSAN_SEOMYEON', 2, 900),
    ('BUSAN_HAEUNDAE', 3, 800);

START TRANSACTION;

-- The administrator also locks the location before stock or assortment changes.
-- Preview takes no location locks; apply serializes the four locations in PK order.
SELECT store_id FROM store_locations FORCE INDEX (PRIMARY)
WHERE @exp_apply = 1 AND store_id IN ('BUSAN_HAEUNDAE', 'BUSAN_SEOMYEON', 'SEOUL_GANGNAM', 'SEOUL_HONGDAE')
ORDER BY store_id FOR UPDATE;

INSERT INTO tmp_store_expansion_totals (store_id, store_no, target_count, store_exists, carried_count, ended_count, needed)
SELECT t.store_id, t.store_no, t.target_count, l.store_id IS NOT NULL,
       COUNT(CASE WHEN i.is_carried = 1 THEN 1 END),
       COUNT(CASE WHEN i.is_carried = 0 THEN 1 END),
       GREATEST(t.target_count - COUNT(CASE WHEN i.is_carried = 1 THEN 1 END), 0)
FROM tmp_store_expansion_targets t
LEFT JOIN store_locations l ON l.store_id = t.store_id
LEFT JOIN store_inventory i ON i.store_id = l.store_id
GROUP BY t.store_id, t.store_no, t.target_count, l.store_id;

INSERT INTO tmp_store_expansion_plan
    (store_id, product_id, root_id, category_id, product_name, product_code, quantity, is_common)
WITH RECURSIVE visible_categories AS (
    SELECT category_id, category_id AS root_id
    FROM categories WHERE parent_id IS NULL AND is_visible = 1
    UNION ALL
    SELECT c.category_id, v.root_id
    FROM categories c JOIN visible_categories v ON v.category_id = c.parent_id
    WHERE c.is_visible = 1
), paid_units AS (
    SELECT oi.product_id, SUM(oi.quantity) AS units
    FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
    WHERE o.status = 'PAID' AND o.canceled_at IS NULL
      AND EXISTS (
        SELECT 1 FROM payment_attempts a WHERE a.order_id = o.order_id
          AND a.method = 'KAKAOPAY' AND a.status = 'APPROVED' AND a.approved_at IS NOT NULL
          AND a.tid IS NOT NULL AND a.tid <> '' AND a.amount = o.total_amount
      )
    GROUP BY oi.product_id
), eligible AS (
    -- Match customer detail visibility and its AVAILABLE / online SOLD_OUT pickup policy.
    SELECT p.product_id, p.category_id, v.root_id, p.name, COALESCE(p.product_code, '') AS product_code,
           COALESCE(u.units, 0) AS paid_units,
           ROW_NUMBER() OVER (PARTITION BY v.root_id ORDER BY COALESCE(u.units, 0) DESC,
               CRC32(CONCAT('common|', p.product_id)), p.product_id) AS common_rank,
           COUNT(*) OVER (PARTITION BY v.root_id) AS root_size,
           MOD(CRC32(CONCAT('home|', p.product_id)), 4) AS home_store,
           MOD(CRC32(CONCAT('reach|', p.product_id)), 100) AS reach_bucket
    FROM products p
    JOIN visible_categories v ON v.category_id = p.category_id
    LEFT JOIN paid_units u ON u.product_id = p.product_id
    WHERE p.status IN ('AVAILABLE', 'SOLD_OUT')
), candidates AS (
    SELECT t.store_id, t.needed, e.*,
           e.common_rank <= LEAST(6, GREATEST(1, CEIL(e.root_size * 0.02))) AS is_common,
           CASE WHEN MOD(t.store_no + 4 - e.home_store, 4) <
                   CASE WHEN e.reach_bucket < 65 THEN 1 WHEN e.reach_bucket < 92 THEN 2 ELSE 3 END
                THEN 0 ELSE 1 END AS fallback_store,
           CRC32(CONCAT('assortment|', t.store_id, '|', e.product_id)) AS store_order
    FROM tmp_store_expansion_totals t
    JOIN eligible e ON t.store_exists = 1 AND t.needed > 0
    LEFT JOIN store_inventory i ON i.store_id = t.store_id AND i.product_id = e.product_id
    WHERE i.product_id IS NULL
), category_rounds AS (
    -- Take one assortment round per leaf before revisiting it; native store assignments win ties.
    SELECT c.*, ROW_NUMBER() OVER (PARTITION BY store_id, category_id, is_common
        ORDER BY fallback_store, store_order, product_id) AS category_round
    FROM candidates c
), store_ranked AS (
    SELECT c.*, ROW_NUMBER() OVER (PARTITION BY store_id
        ORDER BY is_common DESC, category_round, fallback_store, store_order, product_id) AS store_rank
    FROM category_rounds c
), stock_bands AS (
    SELECT s.*, ROW_NUMBER() OVER (PARTITION BY store_id
        ORDER BY is_common, CRC32(CONCAT('band|', store_id, '|', product_id)), product_id) AS band_rank,
        COUNT(*) OVER (PARTITION BY store_id) AS batch_size,
        CRC32(CONCAT('quantity|', store_id, '|', product_id)) AS quantity_seed
    FROM store_ranked s WHERE store_rank <= needed
)
SELECT store_id, product_id, root_id, category_id, name, product_code,
       CASE WHEN band_rank <= ROUND(batch_size * 0.10) THEN 0
            WHEN band_rank <= ROUND(batch_size * 0.10) + ROUND(batch_size * 0.20) THEN 1 + MOD(quantity_seed, 5)
            WHEN is_common = 1 THEN 18 + MOD(quantity_seed, 33)
            ELSE 6 + MOD(quantity_seed, 39)
       END,
       is_common
FROM stock_bands;

-- Preview totals. Check target shortages and the sold-out/low/normal bands before approval.
SELECT t.store_id, t.store_exists, t.carried_count, t.ended_count, t.target_count, t.needed,
       COUNT(p.product_id) AS planned, t.needed - COUNT(p.product_id) AS shortfall,
       COUNT(CASE WHEN p.quantity = 0 THEN 1 END) AS planned_sold_out,
       COUNT(CASE WHEN p.quantity BETWEEN 1 AND 5 THEN 1 END) AS planned_low,
       COUNT(CASE WHEN p.quantity >= 6 THEN 1 END) AS planned_normal,
       COALESCE(SUM(p.quantity), 0) AS added_units
FROM tmp_store_expansion_totals t LEFT JOIN tmp_store_expansion_plan p ON p.store_id = t.store_id
GROUP BY t.store_id, t.store_exists, t.carried_count, t.ended_count, t.target_count, t.needed
ORDER BY t.store_id;

SELECT p.store_id, c.name AS root_category, COUNT(*) AS planned,
       COUNT(DISTINCT p.category_id) AS categories_covered
FROM tmp_store_expansion_plan p JOIN categories c ON c.category_id = p.root_id
GROUP BY p.store_id, c.name ORDER BY p.store_id, c.name;

SELECT store_count, COUNT(*) AS products
FROM (
    SELECT product_id, COUNT(*) AS store_count FROM tmp_store_expansion_plan GROUP BY product_id
) AS coverage GROUP BY store_count ORDER BY store_count;

-- Product-level preview. An eligible pickup location is a planned row with quantity > 0.
SELECT store_id, product_id, product_code, product_name, quantity, is_common
FROM tmp_store_expansion_plan ORDER BY store_id, product_id;

SELECT COUNT(*) INTO @exp_missing_stores
FROM tmp_store_expansion_totals WHERE store_exists = 0;
SELECT COALESCE(SUM(needed), 0) INTO @exp_needed FROM tmp_store_expansion_totals;
SELECT COUNT(*) INTO @exp_planned FROM tmp_store_expansion_plan;
SELECT COUNT(*) INTO @exp_conflicts
FROM tmp_store_expansion_plan p
LEFT JOIN store_locations l ON l.store_id = p.store_id
LEFT JOIN products item ON item.product_id = p.product_id
LEFT JOIN store_inventory i ON i.store_id = p.store_id AND i.product_id = p.product_id
WHERE l.store_id IS NULL OR item.product_id IS NULL OR i.product_id IS NOT NULL;

SET @exp_ready = @@SESSION.foreign_key_checks = 1 AND @exp_missing_stores = 0
    AND @exp_needed = @exp_planned AND @exp_conflicts = 0;
SELECT @exp_missing_stores AS missing_stores, @exp_conflicts AS conflicts,
       @@SESSION.foreign_key_checks AS foreign_key_checks, @exp_ready AS plan_ready;

-- APPLY: permanent tables are touched only when the opt-in flag and plan checks pass.
-- Use plain INSERT so a concurrent duplicate or constraint violation fails instead of overwriting.
INSERT INTO store_inventory (store_id, product_id, quantity, is_carried)
SELECT store_id, product_id, quantity, 1
FROM tmp_store_expansion_plan
WHERE @exp_apply = 1 AND @exp_ready = 1
ORDER BY store_id, product_id;
SET @exp_stock_rows = ROW_COUNT();

INSERT INTO store_inventory_history
    (store_id, product_id, product_reference, product_name, product_code, change_type,
     before_quantity, after_quantity, reason, administrator_id)
SELECT store_id, product_id, product_id, product_name, product_code, 'INITIAL', 0, quantity,
       '포트폴리오 시연용 오프라인 매장 취급 상품 확장', NULL
FROM tmp_store_expansion_plan
WHERE @exp_apply = 1 AND @exp_ready = 1 AND @exp_stock_rows = @exp_planned
ORDER BY store_id, product_id;
SET @exp_history_rows = ROW_COUNT();

SET @exp_commit = COALESCE(@exp_apply = 1 AND @exp_ready = 1
    AND @exp_stock_rows = @exp_planned AND @exp_history_rows = @exp_planned, 0);
SET @exp_finish_sql = IF(@exp_commit = 1, 'COMMIT', 'ROLLBACK');
PREPARE store_expansion_finish FROM @exp_finish_sql;
EXECUTE store_expansion_finish;
DEALLOCATE PREPARE store_expansion_finish;

SELECT CASE WHEN @exp_apply = 0 THEN 'PREVIEW'
            WHEN @exp_commit = 0 THEN 'ROLLED_BACK'
            WHEN @exp_planned = 0 THEN 'ALREADY_AT_TARGET'
            ELSE 'COMMITTED' END AS outcome,
       IF(@exp_commit = 1, @exp_stock_rows, 0) AS inventory_inserted,
       IF(@exp_commit = 1, @exp_history_rows, 0) AS history_inserted;

DROP TEMPORARY TABLE tmp_store_expansion_plan, tmp_store_expansion_totals, tmp_store_expansion_targets;
