-- DaisoMall 시연 데이터 생성에 사용한 JDBC PreparedStatement 쿼리
-- ? 값은 tools/DemoRankingSeed.java가 대상 DB의 상품과 생성 계획을 기준으로 바인딩한다.
-- 실행 순서: 회원 → 주문 → 결제 승인 → 주문상품 → 리뷰 → 숨김 검증 표본.
-- 적용은 생성기를 사용한다. 생성기는 대상 DB, 중복, 외래키와 트랜잭션을 확인한다.
-- DEMO-TID는 학습용 가상 식별자다. 외부 결제 차단을 배포한 개발 DB에서만 적용한다.
-- 실제 회원 정보, 로그인 비밀번호, DB 접속 정보는 이 문서에 포함되지 않는다.

-- 1. 회원
-- 파라미터: 회원 ID, 로그인 ID, 시연 이메일, PBKDF2 해시, 이름, 가입일
INSERT INTO members
    (member_id, login_id, email, password_hash, name, role, status, created_at)
VALUES (?, ?, ?, ?, ?, 'MEMBER', 'ACTIVE', ?);

-- 2. 결제 완료 상태의 시연 주문
-- 파라미터: 주문번호, 회원 ID, 시연 체크아웃 토큰, 상품 합계, 결제 총액, 주문 시각
INSERT INTO orders
    (order_no, member_id, checkout_token, status, recipient, phone, postcode, address1,
     subtotal_amount, shipping_fee, total_amount, created_at, stock_deducted)
VALUES (?, ?, ?, 'PAID', '시연 전용', '000-0000-0000', '00000', '가상 시연 주소', ?, 0, ?, ?, 0);

-- 3. 시연용 승인 기록
-- 파라미터: 결제 시도 시각, 승인 시각, 주문번호
INSERT INTO payment_attempts
    (order_id, method, status, amount, attempted_at, approved_at, tid)
SELECT order_id, 'KAKAOPAY', 'APPROVED', total_amount, ?, ?, CONCAT('DEMO-TID-', order_no)
FROM orders
WHERE order_no = ?;

-- 4. 주문상품
-- 파라미터: 상품 ID, 상품명, 상품 이미지, 단가, 수량, 상품 금액, 주문번호
INSERT INTO order_items
    (order_id, product_id, product_name, product_image, unit_price, quantity, item_total)
SELECT order_id, ?, ?, ?, ?, ?, ?
FROM orders
WHERE order_no = ?;

-- 5. 구매 인증 리뷰
-- 파라미터: 별점, [시연용] 리뷰 본문, 작성 시각, 수정 시각, 삭제 시각 또는 NULL, 주문번호, 상품 ID
INSERT INTO product_reviews
    (member_id, product_id, order_item_id, rating, content, created_at, updated_at, deleted_at)
SELECT o.member_id, i.product_id, i.order_item_id, ?, ?, ?, ?, ?
FROM orders o
JOIN order_items i USING (order_id)
WHERE o.order_no = ? AND i.product_id = ?;

-- 6. 공개 집계에서 제외할 숨김 리뷰 표본
-- 파라미터: 시연 회원 ID, 표본 상품 ID
INSERT INTO product_review_moderation
    (review_id, moderation_state, admin_member_id, changed_at)
SELECT review_id, 'HIDDEN', 'DEMO_RANK_V1_SYSTEM', TIMESTAMPADD(MINUTE, 1, created_at)
FROM product_reviews
WHERE member_id = ? AND product_id = ?;

-- 생성 트랜잭션의 구매 인증 건수 검증
-- 파라미터: 첫 번째 시연 회원 ID, 적용 규모의 마지막 시연 회원 ID
SELECT COUNT(DISTINCT i.order_item_id)
FROM product_reviews rv
JOIN order_items i USING (order_item_id)
JOIN orders o USING (order_id)
JOIN payment_attempts p USING (order_id)
WHERE rv.member_id = o.member_id
  AND rv.product_id = i.product_id
  AND o.status = 'PAID'
  AND o.canceled_at IS NULL
  AND p.status = 'APPROVED'
  AND p.method = 'KAKAOPAY'
  AND p.tid = CONCAT('DEMO-TID-', o.order_no)
  AND p.amount = o.total_amount
  AND o.created_at <= p.approved_at
  AND p.approved_at < rv.created_at
  AND o.member_id REGEXP '^DEMO_RANK_V1_M[0-9]{4}$'
  AND o.member_id BETWEEN ? AND ?
  AND o.checkout_token = CONCAT('DEMO-CHECKOUT-', o.order_no);
