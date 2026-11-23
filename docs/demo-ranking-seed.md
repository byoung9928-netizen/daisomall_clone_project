# 시연용 구매·리뷰 데이터 실행 절차

생성기는 Java 11과 프로젝트의 MySQL Connector/J를 사용한다. 별도 `daisomall_demo`와 지정한 루프백 학습용 DB에서 시연 데이터를 적용할 수 있다. 명시적 승인 인수가 필요하다. 학습용 DB에 적용한 데이터의 주문과 TID에는 시연용 식별자가 유지되며, 쇼핑몰 랭킹에는 반영된다. 매출 집계가 필요한 환경에서는 가상 기록을 실거래와 구분하는 집계 조건을 사용한다.

## 적용 전 체크리스트

1. 원본 `daisomall`을 저장소 바깥에 백업한다. MySQL Workbench의 Data Export 또는 `mysqldump --single-transaction`을 사용하고 파일 크기와 종료 결과를 확인한다.
2. 별도 개발 인스턴스 또는 개인 로컬 MySQL에 빈 `daisomall_demo`를 준비한다. 원본의 구조를 **Structure Only**로 복사한 다음, 데이터는 **categories와 products만** 복사한다. 카테고리와 상품 ID를 보존한다. 원본과 시연 DB에서 아래 검증 SQL을 각각 실행해 카탈로그 건수를 비교한다. 기존 회원·주문·결제·리뷰·재고 데이터를 그 DB로 가져오지 않는다.
3. 시연용 DB 계정은 `daisomall_demo`만 접근하게 한다. 관리자 화면 검사에 필요한 별도 시연 관리자 계정이 있다면 이 DB에서만 사용한다.
4. UI를 검증할 때는 별도 로컬 Tomcat 배포 복사본을 만든다. 그 복사본의 JNDI `jdbc/mysqlDB`가 시연 DB만 읽게 설정하고, 외부로 나가는 연결을 차단한 환경에서 구동한다. 새 Tomcat 프로세스에 `KAKAOPAY_SECRET_KEY`를 전달하지 않는다. 현재 결제 클라이언트는 이 값이 없으면 요청 구성 전 생성자에서 중단한다. 배포본과 연결을 확인한 뒤 조회·로그인·리뷰 화면을 사용한다. 실제 승인·취소·환불 기능을 시험하려면 별도의 결제 검증 환경을 사용한다.
5. DB 세션 시간과 애플리케이션 시간대가 일치하는지 확인한다. 확인한 개인 로컬 DB의 오프셋은 `+09:00`이다.

## 미리보기와 단계별 적용

프로젝트 루트의 PowerShell에서 실행한다. Java 11의 `java`가 PATH에 없으면 설치 경로의 실행 파일을 사용한다. DB 비밀번호는 콘솔에 표시되지 않고 입력된다. 자동 환경에는 `DEMO_DB_PASSWORD` 환경 변수를 사용하고 종료 후 지운다.

```powershell
$lib = 'src/main/webapp/WEB-INF/lib/*'
$original = 'jdbc:mysql://127.0.0.1:3306/daisomall'
$demo = 'jdbc:mysql://127.0.0.1:3306/daisomall_demo'
$dbUser = 'YOUR_LOCAL_DEMO_DB_USER'

java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java preview 100 $original YOUR_LOCAL_READ_USER
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java preview 1500 $demo $dbUser

# 승인 후 1단계
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java apply 100 $demo $dbUser --approve-isolated-write

# 1단계에서 SQL·로그인·리뷰·랭킹을 검증한 후 2단계
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java apply 1500 $demo $dbUser --approve-isolated-write
```

| 규모 | 회원 | 주문 | 승인 기록 | 구매 상품 관계 | 리뷰 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1단계 | 100 | 300 | 300 | 1,000 | 1,000 |
| 전체 누적 | 1,500 | 4,500 | 4,500 | 15,000 | 15,000 |

- 첫 5개 로그인 ID는 `demorank0001`부터 `demorank0005`까지다. 1단계에서 시연 전용 비밀번호를 입력한다. 기존 프로젝트 형식인 PBKDF2-HMAC-SHA256, 120,000회 해시를 저장한다. 다른 회원의 비밀번호는 임의로 만든 접근 불가 값이다. 일반 역할은 실제 가입 코드와 같은 `MEMBER`다. 이메일은 `example.invalid`를 사용하고, 이름은 시연용이며 별도 닉네임 컬럼은 현재 스키마에 없다.
- 승인 기록은 구매 인증 조건에 맞추되 TID와 주문 번호가 시연 접두어를 사용한다. 상품 및 매장 재고, 포인트, 쿠폰은 생성 대상에 포함되지 않는다. 리뷰에는 `[시연용]` 표시가 있다. 승인된 리뷰용 사진을 아직 제공받지 않았으므로 사진 없이 생성한다.
- 숨김 1개와 소프트 삭제 1개를 포함한다. 전체 15,000개를 적용하면 시연용 공개 리뷰는 14,998개다. 숨김 행의 변경 주체는 테스트용 시스템 식별자로 기록한다. 계정·상품별 리뷰와 주문 연결은 생성 전후에 검증한다.
- 2단계는 1단계 100명이 존재해야 시작한다. 동일 카탈로그에서는 상품 선택, 별점, 시간대 배분을 고정된 규칙으로 재현한다. 첫 적용의 DB 시각을 첫 회원의 가입일에서 복원하므로 재실행이나 확장 시 기존 기록을 덮어쓰지 않는다. 최근 24시간·7일 구간은 DB 현재 시각으로 움직인다. 첫 적용 직후가 랭킹 비교에 적합하다. 시연을 갱신하려면 원래 DB와 구분된 환경에서 정리 후 두 단계로 다시 적용한다.
- 오류가 발생하면 일괄 롤백한다. 생성기가 기존 시연 행의 수량·소유 관계 이상을 발견하면 추가 생성을 멈춘다. 기존 앱과 동시에 해당 시연 데이터를 수정하지 않은 상태에서 실행한다.

## 학습용 본서버 적용

본서버 스키마의 실제 이름을 승인 인수와 같게 지정한다. 대상이 로컬 학습용 DB인지 확인하고 백업한 뒤, 시연 식별자를 외부 카카오페이 요청 전에 차단하는 최신 주문·결제 코드를 해당 앱에 먼저 배포한다. 일반 주문이 있는 DB에서는 시연 접두어와 충돌하는 행을 검사하고, 정상 행은 보존한다. 시연 DB가 같은 카탈로그를 사용하더라도 본서버 상품 ID를 읽어 대상에서 구매를 생성한다. 카카오페이 API는 생성기에서 호출하지 않는다.

```powershell
$learning = 'jdbc:mysql://127.0.0.1:3306/daisomall'
$learningUser = 'YOUR_LOCAL_LEARNING_DB_USER'
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java preview 1500 $learning $learningUser
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java apply 100 $learning $learningUser --approve-learning-write=daisomall
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java apply 1500 $learning $learningUser --approve-learning-write=daisomall
```

본서버에서 `cleanup`을 실행할 수 없다. 되돌리기 전에 적용 직후 추가된 학습 기록과 백업을 비교한다. 본서버의 기존 작업까지 영향을 줄 수 있으므로 백업 전체 덮어쓰기는 별도 승인과 작업 중지가 필요하다.

## DB와 화면 비교

1. `demo-ranking-verify.sql`을 적용 전과 각 단계 직후에 실행한다. 회원·주문·승인·구매 관계·리뷰 건수와 원본 온라인·오프라인 재고 체크섬을 비교한다. 중복 구매, 중복 활성 리뷰, 구매 인증 불일치는 0건이어야 한다.
2. SQL의 `daily`, `weekly`, `rising`, `reviews` 상위 10개를 각각 `/app/products?collection=ranking&mode=daily` 등의 화면과 비교한다. 급상승은 최근 하루 수량에서 이전 하루 수량을 뺀 양수만 표시한다. 동점은 판매 랭킹에서 등록 시각 내림차순과 상품 ID, 리뷰 랭킹에서 상품 ID를 사용한다. 2페이지와 신규 데이터가 없는 DB의 빈 화면도 확인한다.
3. 평점 순서는 `/app/products?sort=rating`의 리뷰가 있는 상품과 비교한다. `/app/products?collection=rating&mode=5.0`은 정확한 평균 5.0, 다른 별점 구간은 소수 첫째 자리 반올림이다. `4.9`, `4.8`, `4.7`도 비교한다. 리뷰가 없는 상품, 판매 종료 또는 비노출 카테고리는 각 화면의 조건에 맞게 제외되는지 확인한다.
4. 상세 페이지에서 SQL 평균과 리뷰 수, 최신순·높은순·낮은순, 다음 페이지를 비교한다. 관리자 화면에서 숨김·삭제 표본의 상태와 공개 집계 제외를 확인한다. `demorank0001` 계정으로 구매 목록 및 본인 리뷰 수정도 시험한다. 순위 상위 상품은 생성기의 미리보기에 출력된다.
5. 검증 SQL의 실행 계획과 브라우저 네트워크 시간을 비교한다. 실제 15,000건을 적용한 뒤 목록·랭킹·별점 정렬·리뷰 상세·관리자 리뷰 검색의 응답 시간을 측정한다. 추천순의 `products.sales`는 별도 값이며, 생성기는 이를 바꾸지 않는다. 현재 판매 랭킹 쿼리는 승인 상태와 TID, 결제액을 보지만 결제수단은 제한하지 않는다. 구매 인증은 KAKAOPAY를 요구한다. 다른 결제수단 데이터가 추가될 때 두 집계의 차이를 검토한다.

## 정리

시연 DB에서만 다음을 실행한다. 계정 ID·로그인 ID·이메일·일반 권한과 시연 주문 번호·토큰·TID 및 구매 연결을 확인한다. 리뷰 부가정보부터 결제·주문·계정 순으로 정리하며, 기존 관리자는 대상에 포함되지 않는다. 테스트하는 동안 별도의 주문이나 찜 같은 연관 데이터를 만들었다면 외래키 또는 사전 점검에 걸려 전체 롤백될 수 있다. 그 경우 연관 데이터를 확인하거나 시연 스키마를 새로 만들어 진행한다.

```powershell
java -Dfile.encoding=UTF-8 --class-path $lib tools/DemoRankingSeed.java cleanup 1500 $demo $dbUser --approve-isolated-write
```
