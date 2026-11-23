-- Read-only comparisons. On the original local database, baseline reports can run before setup.
-- For reproducible rolling-window checks, run immediately after the first fixture apply.
SELECT DATABASE() AS database_name, NOW() AS db_now, UTC_TIMESTAMP() AS utc_now,
       TIMEDIFF(NOW(),UTC_TIMESTAMP()) AS utc_offset,
       (SELECT COUNT(*) FROM products) AS catalog_products, (SELECT COUNT(*) FROM categories) AS categories,
       (SELECT COUNT(*) FROM members WHERE member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$') AS demo_members,
       (SELECT COUNT(*) FROM orders WHERE order_no REGEXP '^DEMO_RANK_V1_M[0-9]{4}_O[1-3]$') AS demo_orders,
       (SELECT COUNT(*) FROM payment_attempts WHERE tid REGEXP '^DEMO-TID-DEMO_RANK_V1_M[0-9]{4}_O[1-3]$') AS demo_payments,
       (SELECT COUNT(*) FROM order_items i JOIN orders o USING(order_id)
        WHERE o.order_no REGEXP '^DEMO_RANK_V1_M[0-9]{4}_O[1-3]$') AS demo_order_items,
       (SELECT COUNT(*) FROM product_reviews WHERE member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$') AS demo_reviews;

-- Compare these snapshots across the same database before and after seeding.
SELECT COUNT(*) AS products, SUM(stock_quantity) AS online_units,
       BIT_XOR(CAST(CRC32(CONCAT_WS('|',product_id,stock_quantity)) AS UNSIGNED)) AS online_signature
FROM products;
SELECT COUNT(*) AS store_rows, COALESCE(SUM(quantity),0) AS offline_units,
       BIT_XOR(CAST(CRC32(CONCAT_WS('|',store_id,product_id,is_carried,quantity)) AS UNSIGNED)) AS offline_signature
FROM store_inventory;

-- The three expected problem counts are zero on a freshly applied fixture.
SELECT
    (SELECT COUNT(*) FROM (
        SELECT o.member_id,i.product_id FROM order_items i JOIN orders o USING(order_id)
        WHERE o.order_no REGEXP '^DEMO_RANK_V1_M[0-9]{4}_O[1-3]$'
        GROUP BY o.member_id,i.product_id HAVING COUNT(*) > 1
    ) AS dup) AS duplicate_purchases,
    (SELECT COUNT(*) FROM (
        SELECT member_id,product_id FROM product_reviews
        WHERE deleted_at IS NULL AND member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'
        GROUP BY member_id,product_id HAVING COUNT(*) > 1
    ) AS dup) AS duplicate_active_reviews,
    (SELECT COUNT(*) FROM product_reviews r
      JOIN order_items i ON i.order_item_id=r.order_item_id JOIN orders o ON o.order_id=i.order_id
      WHERE r.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'
        AND (r.member_id<>o.member_id OR r.product_id<>i.product_id OR o.status<>'PAID' OR o.canceled_at IS NOT NULL
          OR NOT EXISTS (SELECT 1 FROM payment_attempts a WHERE a.order_id=o.order_id
            AND a.method='KAKAOPAY' AND a.status='APPROVED' AND a.tid=CONCAT('DEMO-TID-',o.order_no)
            AND a.amount=o.total_amount AND o.created_at<=a.approved_at AND a.approved_at<r.created_at)))
      AS invalid_review_purchases;

SELECT r.rating, COUNT(*) AS physical_reviews,
       SUM(r.deleted_at IS NULL AND COALESCE(m.moderation_state,'PUBLISHED')='PUBLISHED') AS public_reviews
FROM product_reviews r LEFT JOIN product_review_moderation m USING(review_id)
WHERE r.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'
GROUP BY r.rating ORDER BY r.rating;

SELECT CASE WHEN r.deleted_at IS NOT NULL THEN 'SOFT_DELETED'
            ELSE COALESCE(m.moderation_state,'PUBLISHED') END AS visibility, COUNT(*) AS reviews
FROM product_reviews r LEFT JOIN product_review_moderation m USING(review_id)
WHERE r.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'
GROUP BY visibility ORDER BY visibility;

SELECT COUNT(DISTINCT product_id) AS covered_products, COUNT(DISTINCT content) AS distinct_public_texts
FROM product_reviews r WHERE r.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$' AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM product_review_moderation m WHERE m.review_id=r.review_id
                AND m.moderation_state IN ('HIDDEN','DELETED'));

-- Mirror storefront SQL. Sales approval conditions intentionally match ProductDAO, which does not
-- currently constrain method here. Review purchase authorization is stricter (see query above).
WITH RECURSIVE visible AS (
    SELECT category_id FROM categories WHERE parent_id IS NULL AND is_visible=1
    UNION ALL SELECT c.category_id FROM categories c JOIN visible v ON c.parent_id=v.category_id WHERE c.is_visible=1
), products_visible AS (
    SELECT p.product_id,p.name,p.created_at FROM products p JOIN visible v USING(category_id)
    WHERE p.status NOT IN ('SCHEDULED','DISCONTINUED')
), approvals AS (
    SELECT o.order_id,MAX(a.approved_at) AS approved_at
    FROM orders o JOIN payment_attempts a USING(order_id)
    WHERE o.status='PAID' AND o.canceled_at IS NULL AND a.status='APPROVED'
      AND a.tid<>'' AND a.amount=o.total_amount AND a.approved_at>=NOW()-INTERVAL 7 DAY
    GROUP BY o.order_id
), units AS (
    SELECT i.product_id, SUM(i.quantity) AS weekly_units,
      SUM(CASE WHEN a.approved_at>=NOW()-INTERVAL 1 DAY THEN i.quantity ELSE 0 END) AS daily_units,
      SUM(CASE WHEN a.approved_at>=NOW()-INTERVAL 1 DAY THEN i.quantity
               WHEN a.approved_at>=NOW()-INTERVAL 2 DAY THEN -i.quantity ELSE 0 END) AS rising_units
    FROM approvals a JOIN order_items i USING(order_id) GROUP BY i.product_id
), stats AS (
    SELECT r.product_id,COUNT(*) AS review_count,AVG(r.rating) AS avg_rating
    FROM product_reviews r WHERE r.deleted_at IS NULL
      AND NOT EXISTS (SELECT 1 FROM product_review_moderation m WHERE m.review_id=r.review_id
                      AND m.moderation_state IN ('HIDDEN','DELETED'))
    GROUP BY r.product_id
), sales_modes AS (
    SELECT 'daily' AS mode,product_id,daily_units AS score FROM units WHERE daily_units>0
    UNION ALL SELECT 'weekly',product_id,weekly_units FROM units WHERE weekly_units>0
    UNION ALL SELECT 'rising',product_id,rising_units FROM units WHERE rising_units>0
), leaderboard AS (
    SELECT s.mode,p.product_id,p.name,s.score,
           ROW_NUMBER() OVER (PARTITION BY s.mode ORDER BY s.score DESC,p.created_at DESC,p.product_id) AS position_no
    FROM sales_modes s JOIN products_visible p USING(product_id)
    UNION ALL
    SELECT 'reviews',p.product_id,p.name,s.review_count,
           ROW_NUMBER() OVER (ORDER BY s.review_count DESC,p.product_id)
    FROM stats s JOIN products_visible p USING(product_id)
    UNION ALL
    SELECT 'rating',p.product_id,p.name,s.avg_rating,
           ROW_NUMBER() OVER (ORDER BY s.avg_rating DESC,s.review_count DESC,p.product_id)
    FROM stats s JOIN products_visible p USING(product_id)
), bands AS (
    SELECT p.product_id, CASE WHEN s.avg_rating=5 THEN '5.0'
        WHEN ROUND(s.avg_rating,1) IN (4.7,4.8,4.9) THEN CAST(ROUND(s.avg_rating,1) AS CHAR)
        WHEN s.review_count IS NULL THEN 'unreviewed' ELSE 'other' END AS band
    FROM products_visible p LEFT JOIN stats s USING(product_id)
)
SELECT mode, position_no, product_id, name, score FROM leaderboard WHERE position_no<=10
UNION ALL
SELECT CONCAT('count ',band),0,NULL,NULL,COUNT(*) FROM bands GROUP BY band
ORDER BY mode,position_no;

-- Pick a reviewed product in Workbench and use the same visibility and tie-breakers as the detail page.
SET @review_product = (
    SELECT r.product_id FROM product_reviews r
    WHERE r.deleted_at IS NULL AND NOT EXISTS (SELECT 1 FROM product_review_moderation m
        WHERE m.review_id=r.review_id AND m.moderation_state IN ('HIDDEN','DELETED'))
    GROUP BY r.product_id ORDER BY COUNT(*) DESC,r.product_id LIMIT 1
);
SELECT r.product_id,COUNT(*) AS public_reviews,AVG(r.rating) AS exact_average,ROUND(AVG(r.rating),1) AS card_average,
       SUM(r.rating=1) AS r1,SUM(r.rating=2) AS r2,SUM(r.rating=3) AS r3,SUM(r.rating=4) AS r4,SUM(r.rating=5) AS r5
FROM product_reviews r
WHERE r.product_id=@review_product AND r.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM product_review_moderation m WHERE m.review_id=r.review_id
                  AND m.moderation_state IN ('HIDDEN','DELETED'))
GROUP BY r.product_id;

WITH public_reviews AS (
    SELECT r.review_id,r.rating,r.created_at FROM product_reviews r WHERE r.product_id=@review_product
      AND r.deleted_at IS NULL AND NOT EXISTS (SELECT 1 FROM product_review_moderation m
        WHERE m.review_id=r.review_id AND m.moderation_state IN ('HIDDEN','DELETED'))
), ordered AS (
    SELECT *, ROW_NUMBER() OVER (ORDER BY created_at DESC,review_id DESC) AS newest,
        ROW_NUMBER() OVER (ORDER BY rating DESC,created_at DESC,review_id DESC) AS highest,
        ROW_NUMBER() OVER (ORDER BY rating ASC,created_at DESC,review_id DESC) AS lowest
    FROM public_reviews
)
SELECT * FROM ordered WHERE LEAST(newest,highest,lowest)<=10 ORDER BY newest;

EXPLAIN SELECT r.review_id,r.rating,r.created_at
FROM product_reviews r WHERE r.product_id=@review_product AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM product_review_moderation m WHERE m.review_id=r.review_id
                AND m.moderation_state IN ('HIDDEN','DELETED'))
ORDER BY r.created_at DESC,r.review_id DESC LIMIT 10;
