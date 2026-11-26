# 전환 분석 시연 데이터

로컬 MySQL의 별도 개발 DB `daisomall_demo` 또는 `daisomall_demo_*`에서만 실행한다. 대상에는 2-A 행동 기록 테이블과 기존 시연 회원·주문이 있어야 한다. 실행 전에 DB를 백업한다. 주문·결제·리뷰·재고와 실제 찜·장바구니 상태는 이 도구로 변경하지 않는다.

PowerShell에서 Java 11과 함께 실행한다. `DAISO_BEHAVIOR_SEED_PASSWORD`는 로컬 DB 계정의 비밀번호를 현재 셸에 설정한다.

```powershell
$driver = (Get-ChildItem src/main/webapp/WEB-INF/lib/mysql-connector*.jar | Select-Object -First 1).FullName
$build = Join-Path $env:TEMP 'daiso-behavior-seed-build'
New-Item -ItemType Directory -Force $build | Out-Null
javac --release 11 -encoding UTF-8 -d $build tools/DemoBehaviorSeed.java
java -cp "$build;$driver" DemoBehaviorSeed preview daisomall_demo root
java -cp "$build;$driver" DemoBehaviorSeed apply daisomall_demo root
```

`preview`는 읽기만 한다. `apply`는 기존 승인 주문에서 조회 후 직접 결제와 장바구니 경유 결제 시나리오를 고르고, 행동 시간을 해당 주문 생성보다 앞에 배치한다. 실제 시연 결제 실패 주문이 있는 경우에만 실패 시나리오를 연결한다. 해당 상품을 주문한 적 없는 시연 회원·상품 조합에서 조회만, 찜 등록, 7일 관찰이 끝난 장바구니 미구매 시나리오를 만든다. 관리자에서 시연 데이터를 포함하고 최근 30일을 선택하면 시연용 미구매를 확인할 수 있다. 구매·실패가 더 오래됐으면 기존 주문 생성일을 포함하는 기간으로 조회한다.

적용 시 개발 DB에만 생성 이력 테이블 `product_behavior_demo_seed`를 만든다. 생성 이벤트와 이력 연결은 같은 트랜잭션으로 저장하며 실패 시 롤백한다. 재실행 시 이미 만든 이벤트는 건너뛴다. `cleanup`은 이 도구가 생성한 이벤트만 지운다. 시연 계정이 브라우저에서 직접 발생시킨 행동은 유지된다.

필요할 때만 시연 생성분을 정리한다.

```powershell
java -cp "$build;$driver" DemoBehaviorSeed cleanup daisomall_demo root
Remove-Item Env:DAISO_BEHAVIOR_SEED_PASSWORD
```
