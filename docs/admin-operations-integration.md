# 관리자 운영 기능 적용과 팀 연동

## 팀 배포와 적용 순서

각 팀원은 MySQL Workbench에서 프로젝트가 사용하는 DB를 선택하고 SELECT DATABASE()로 대상 스키마를 확인한다. 먼저 구조와 데이터를 백업한다. 예를 들어 비밀번호를 명령에 적지 않고 `mysqldump --single-transaction --routines --triggers -p DB_NAME --result-file=before-admin-operations.sql`로 백업한다. 백업은 팀에 전달할 배포 파일과 분리한다.

공유 대상은 이 문서와 아래 마이그레이션 SQL이다. 각 DB에서 이미 적용한 단계는 건너뛴다. 이 기능에는 시연 회원·주문·리뷰 INSERT나 매장 재고 seed가 필요하지 않다.

| 순서 | SQL 파일 | 실행 전 확인 |
| --- | --- | --- |
| 1 | [store-inventory-threshold-migration.sql](sql/store-inventory-threshold-migration.sql) | 기존 DB에 `store_inventory.low_stock_threshold`와 매장 기준 이력이 없을 때 적용한다. 새 DB를 [store-inventory-schema.sql](sql/store-inventory-schema.sql)로 생성했다면 이 단계는 이미 포함되어 있다. 기존 매장 기준은 기본값 5로 추가된다. |
| 2 | [admin-product-category-audit.sql](sql/admin-product-category-audit.sql) | [product-admin-changes.sql](sql/product-admin-changes.sql)에 포함된 상품 이력 테이블이 있어야 한다. |
| 3 | [admin-online-threshold-audit.sql](sql/admin-online-threshold-audit.sql) | [product-online-inventory-migration.sql](sql/product-online-inventory-migration.sql)의 온라인 부족 기준 및 재고 이력 구조가 적용되어 있어야 한다. |
| 4 | [admin-store-transfer.sql](sql/admin-store-transfer.sql) | 매장 기준 구조와 `store_inventory_history`를 확인한다. |
| 5 | [admin-review-decisions.sql](sql/admin-review-decisions.sql) | 기존 `product_reviews` 테이블과 리뷰 관리 스키마를 확인한다. |
| 6 | [product-restock-notifications.sql](sql/product-restock-notifications.sql) | 공유 DB에서는 운영팀과 주문 담당자가 검토한 뒤 실행한다. SQL 편집기에서 `DELIMITER`를 포함한 파일 전체를 실행한다. |

적용 후 `SHOW CREATE TABLE product_admin_changes`, `SHOW CREATE TABLE store_inventory_history`, `SHOW CREATE TABLE product_review_admin_decisions`, `SHOW CREATE TABLE product_restock_requests`로 구조를 확인한다. `SHOW TRIGGERS LIKE 'products'`에서 `notify_product_restock`까지 확인한 뒤 같은 버전의 애플리케이션을 배포한다.

MySQL DDL은 단계마다 자동 커밋된다. 일부 문장에서 실패하면 기존에 생성된 컬럼·테이블·트리거를 확인하고 실패한 문장부터 적용한다. 재입고 기능을 되돌려야 하면 DBA가 트리거부터 중지하고 신청 테이블 데이터를 보존한다. [order-stock-restoration-migration.sql](sql/order-stock-restoration-migration.sql)은 주문 취소·환불 재고 복구용 별도 마이그레이션이다.

추천 분석은 개발 JVM에서만 `-Ddaisomall.dev.recommendations=true`로 활성화한다. 관리자 세션에 더해 시연 데이터 생성기가 기록하는 회원 ID, 로그인 ID, 도메인, 역할의 일치를 검사한다. 조회 대상 회원의 세션 임시 기록 대신 DB에 저장된 이벤트로 분석한다.

## 운영 대시보드

현재 화면은 `/app/admin/dashboard`이며 `UserController`와 `AdminDashboardDAO`가 담당한다. 대시보드 담당자는 기존 권한 검증을 통과한 요청에서 아래 집계와 내부 링크를 사용한다.

| 업무 | 조회 계약 | 이동 경로 |
| --- | --- | --- |
| 온라인 재고 부족 / 온라인 품절 | 상품별 부족 기준을 적용하고 판매 예정·종료를 제외하는 온라인 재고 화면 기준 | `/app/admin/inventory?state=LOW`, `/app/admin/inventory?state=SOLD_OUT` |
| 매장 부족 | 매장별 취급 중 상품의 재고가 1 이상이고 매장별 부족 기준 이하. 여러 매장을 합산하면 단위는 매장·상품 관계 | `/app/admin/stores?store=STORE_ID&state=LOW` |
| 미처리 리뷰 신고 | 리뷰 수 대신 처리 대기 신고 건수 | `/app/admin/products/reviews?reports=PENDING&sort=MOST_PENDING` |
| 발송 대기 / 반품 처리 대기 | 기존 주문·반품 담당 DAO의 대기 조건 | `/app/admin/orders?filter=awaiting-shipment`, `/app/admin/returns?filter=pending` |
| 당일 매출 / 판매량 | 한국 시간의 결제 승인일과 판매순위 집계 기준. 상품 매출과 주문 총결제액을 각각 표기 | `/app/admin/sales?salesPeriod=TODAY` |

대시보드는 전체 목록을 반복 조회하지 않고 업무별 집계를 일괄 조회한다. 현재 주문·반품 탭의 필터 정의를 그대로 사용한다.

## 매장 관리자 권한

- 회원 담당자와 `STORE_MANAGER` 역할 및 `member_store_assignments(member_id, store_id)` 복합 키 매핑을 협의한다. 복수 매장 배정을 허용하고 권한 회수 즉시 다음 요청에 반영한다.
- 총괄 관리자는 전체 매장과 상품을 관리한다. 매장 관리자는 배정 매장의 목록·상세·검색·비교·내보내기·이력 및 재고 변경만 허용한다.
- 허용 매장 ID는 검증된 세션의 회원 ID와 DB 매핑에서 얻는다. GET과 POST, 직접 URL, 배치·취급 변경, CSV까지 동일한 서버 검사를 적용한다. 이동은 양쪽 매장 모두 배정되어야 하며 미리보기와 확정에서 각각 권한을 검증한다. 처리 이력에는 실제 수행자를 남긴다.
- 역할을 단순히 기존 `ADMIN` 허용 목록에 더하면 주문·회원·상품 화면도 노출될 수 있다. 인증 필터와 매장별 권한 서비스의 인터페이스부터 협의한다.

## 매장픽업 주문과 자동 이력

- 현재 픽업 장바구니, `/app/order/pickup/form`, Toss 승인, `OrderDAO.completePickupApproval` 계열 승인 경로를 사용한다. `OrderDAO`의 승인 트랜잭션은 `pickup_store_id`를 참조해 취급 중인 매장 재고를 차감하고 `stock_deducted`를 설정한다.
- 픽업 주문의 준비·수령 상태, 처리 담당자 권한 및 전환 조건을 주문 담당자와 확정한다. 저장 시 서버는 주문의 매장과 담당자 배정, 현재 상태를 다시 확인한다.
- 복구는 `fulfillment_type`으로 구분한다. 택배는 온라인 복구, 픽업은 주문에 기록된 매장의 재고만 복구한다. 현재 `OnlineStockRestoration`은 `stock_deducted`를 근거로 `products.stock_quantity`를 변경하므로 픽업 주문에서 이 경로에 도달하는지 우선 점검한다. 픽업 승인·취소·환불과 재고 이동은 같은 잠금 순서 및 중복 복구 키를 사용하도록 맞춘다.
- 자동 출고 이력은 주문 확정 트랜잭션에서 함께 기록한다. 상품별 수량, 주문번호, 매장과 처리 전후 수량을 기록하며 같은 주문을 재처리해도 이력이 두 번 남지 않게 한다. 현재 픽업 판매 그래프는 재고가 차감된 매장 결제 주문을 읽기 전용으로 집계하고 수동 재고 이동과 분리한다.
- PG 호출은 재고 트랜잭션과 분리한다. PG 결과가 성공했지만 DB 작업이 실패하면 정산 담당자가 같은 주문 키로 재처리할 수 있어야 한다. 테스트에는 주문 완료, 승인 재전송, 취소·환불 재전송, 시연 주문과 수동 작업을 포함한다.

## 온라인 재입고 알림

- 기존 마이페이지의 상품 입고 알림 탭에 신청·알림 목록을 연결한다. 공통 회원 알림함의 기존 안내 모달과 회원 인증은 유지한다. 공통 알림 배지를 추가하려면 회원 담당자와 미확인 알림 및 읽음 상태 계약을 합의한다.
- `products.stock_quantity`가 변경되어 실제 구매 가능한 상태가 되면 트리거가 대기 신청을 같은 DB 트랜잭션에 기록한다. 수동 입고, 취소·환불 복구 및 이후 추가되는 온라인 재고 경로가 동일한 DB 규칙을 따른다. 오프라인 재고, 실패 후 롤백된 작업, 판매 예정·판매 종료 상태에서는 알림을 생성하지 않는다.
- 트리거 실행 계정과 권한, 상품별 동시 신청량, 결제 및 재고 처리에 미치는 잠금·장애 영향을 운영 배포 전에 확인한다. 신청과 신규 알림 조회는 상품 ID와 로그인 세션의 회원 ID를 사용한다.
