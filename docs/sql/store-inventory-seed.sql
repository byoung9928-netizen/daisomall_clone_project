-- Run once after the schema, in one connection. A rerun preserves existing inventory and history.
DROP TEMPORARY TABLE IF EXISTS store_inventory_seed_plan;
CREATE TEMPORARY TABLE store_inventory_seed_plan (
    store_id VARCHAR(40) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (store_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

START TRANSACTION;

INSERT INTO store_locations (store_id, store_name, city) VALUES
    ('SEOUL_GANGNAM', '서울 강남점', '서울'),
    ('SEOUL_HONGDAE', '서울 홍대점', '서울'),
    ('BUSAN_SEOMYEON', '부산 서면점', '부산'),
    ('BUSAN_HAEUNDAE', '부산 해운대점', '부산')
ON DUPLICATE KEY UPDATE store_id = store_locations.store_id;

-- Lock the locations in the same order so two seed runs cannot plan the same new rows.
SELECT store_id FROM store_locations
WHERE store_id IN ('SEOUL_GANGNAM','SEOUL_HONGDAE','BUSAN_SEOMYEON','BUSAN_HAEUNDAE')
ORDER BY store_id FOR UPDATE;

INSERT INTO store_inventory_seed_plan (store_id, product_id, quantity)
WITH targets AS (
    SELECT 'SEOUL_GANGNAM' AS store_id, 1 AS store_number, 25 AS target_count, 0 AS start_offset
    UNION ALL SELECT 'SEOUL_HONGDAE', 2, 20, 20
    UNION ALL SELECT 'BUSAN_SEOMYEON', 3, 22, 35
    UNION ALL SELECT 'BUSAN_HAEUNDAE', 4, 18, 52
), ranked AS (
    SELECT product_id,
        ROW_NUMBER() OVER (ORDER BY COALESCE(sales, 0) DESC, product_id) AS row_no,
        COUNT(*) OVER () AS total
    FROM products
    WHERE source_kind = 'REFERENCE' AND status IN ('AVAILABLE', 'SOLD_OUT')
), chosen AS (
    SELECT t.store_id, t.store_number,
        CASE WHEN slot.row_no <= 5 THEN slot.row_no
             ELSE 6 + MOD(t.start_offset + slot.row_no - 6, NULLIF(slot.total - 5, 0))
        END AS product_row
    FROM targets t JOIN ranked slot ON slot.row_no <= t.target_count
)
SELECT c.store_id, p.product_id,
    CASE WHEN p.row_no <= 5 THEN MOD(p.row_no + c.store_number - 2, 5)
         ELSE MOD(p.row_no * 19 + c.store_number * 11, 51)
    END
FROM chosen c JOIN ranked p ON p.row_no = c.product_row
LEFT JOIN store_inventory i ON i.store_id = c.store_id AND i.product_id = p.product_id
WHERE i.product_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM store_inventory_history h
                  WHERE h.store_id = c.store_id AND h.change_type = 'INITIAL');

INSERT INTO store_inventory (store_id, product_id, quantity)
SELECT store_id, product_id, quantity FROM store_inventory_seed_plan ORDER BY store_id, product_id;

INSERT INTO store_inventory_history
    (store_id, product_id, product_reference, product_name, product_code, change_type,
     before_quantity, after_quantity, reason, administrator_id)
SELECT s.store_id, s.product_id, s.product_id, p.name, COALESCE(p.product_code, ''), 'INITIAL',
       0, s.quantity, '학습용 매장 초기 재고', NULL
FROM store_inventory_seed_plan s JOIN products p ON p.product_id = s.product_id
ORDER BY s.store_id, s.product_id;

COMMIT;
DROP TEMPORARY TABLE store_inventory_seed_plan;

SELECT l.store_id, l.store_name, COUNT(i.product_id) AS product_types,
       COALESCE(SUM(i.quantity), 0) AS total_quantity,
       COUNT(CASE WHEN i.quantity = 0 THEN 1 END) AS sold_out_types,
       COUNT(CASE WHEN i.quantity BETWEEN 1 AND 5 THEN 1 END) AS low_stock_types
FROM store_locations l LEFT JOIN store_inventory i ON i.store_id = l.store_id
WHERE l.store_id IN ('SEOUL_GANGNAM','SEOUL_HONGDAE','BUSAN_SEOMYEON','BUSAN_HAEUNDAE')
GROUP BY l.store_id, l.store_name ORDER BY l.store_id;
