# 처음 맡는 다이소몰 프론트엔드

화면을 고치려면 먼저 **그 화면의 View JSP**를 여세요. 위쪽은 실제 HTML, 아래쪽 `<script>`는 그 화면의 동작입니다. 공통 디자인은 기존 CSS를 사용합니다.

## 1. JSP·JSPF·JS·Java의 차이

| 종류 | 역할 |
| --- | --- |
| JSP | Tomcat이 읽어서 HTML로 보내는 페이지. 로그인 폼·상품 상세 등 |
| JSPF | 여러 JSP에 포함하는 HTML 조각. 헤더·메뉴·푸터 등 |
| JS | 브라우저에서 버튼 클릭과 데이터 갱신을 처리 |
| Java | 서버에서 요청을 받고 업무 처리·데이터 조회·저장을 담당 |
| CSS | 색상·글자·간격·반응형 배치 |

JSP 하단의 JavaScript도 브라우저에서 실행합니다. Java처럼 MySQL에 직접 연결하지 않습니다.

## 2. 폴더와 담당 파일

아래 경로는 `src/main/webapp/` 기준입니다.

| 화면 | 실제 View JSP |
| --- | --- |
| 홈 | `WEB-INF/views/shop/home.jsp` |
| 로그인·가입 | `WEB-INF/views/member/login.jsp`, `signup.jsp` |
| 마이페이지·회원정보 | `WEB-INF/views/member/mypage.jsp`, `profile.jsp` |
| 상품 목록·검색·상세 | `WEB-INF/views/product/list.jsp`, `search.jsp`, `detail.jsp` |
| 장바구니·주문서 | `WEB-INF/views/order/cart.jsp`, `checkout.jsp` |
| 주문 내역·상세 | `WEB-INF/views/order/orders.jsp`, `detail.jsp` |
| 결제 결과 | `WEB-INF/views/order/payment-success.jsp`, `payment-failure.jsp` |
| 관리자 | `WEB-INF/views/admin/`의 `dashboard`, `categories`, `products`, `inventory`, `stores`, `orders`, `users.jsp` |

공개 JSP는 기존 주소를 유지하는 진입점입니다. `/login.jsp`는 실제 View로 Forward합니다. Forward는 주소창을 바꾸지 않고 서버 안에서 처리를 넘깁니다. `shop/page.jsp`와 `admin/layout.jsp`는 허용된 View로 연결하는 분기 파일입니다. `WEB-INF` 안의 JSP를 브라우저 주소로 직접 열지 않습니다.

| 공통 위치 | 역할 |
| --- | --- |
| `WEB-INF/views/common/head.jspf` | CSS·공통 JS 로딩 순서 |
| `common/header.jspf`, `navigation.jspf`, `footer.jspf` | 고객 공통 HTML |
| `common/admin-header.jspf`, `admin-navigation.jspf` | 관리자 공통 HTML |
| `common/order-totals.jspf` | 장바구니·주문서·상세의 금액 HTML |
| `assets/js/data/mock-repository.js` | 현재 Mock 조회·저장·검증 |
| `assets/js/common/shop-ui.js` | 금액·알림·오류·반복 상품 카드 |
| `assets/js/common/shop-events.js` | 공통 메뉴 이동·로그아웃 |
| `assets/js/common/navigation.js` | 카테고리·키보드·모바일 탐색 |
| `assets/js/common/catalog.js` | 목록·검색 공통 필터·정렬·더 보기 |
| `assets/js/common/order-ui.js` | 주문 항목·금액 갱신 |
| `assets/js/common/admin-ui.js` | 관리자 공통 표·입력·저장 처리 |
| `assets/js/common/admin-inventory.js` | 온라인·매장 재고 공통 동작 |
| `assets/mock/`, `assets/css/` | 초기 JSON / 기존 디자인 |

페이지 전용 스크립트는 `DOMContentLoaded`를 기다립니다. 그전에 `head.jspf`의 공통 `defer` JS가 순서대로 실행됩니다. `Daiso.ready`는 Mock 데이터 준비를 기다리는 Promise입니다. 따라서 각 페이지는 데이터 준비 후 기존 HTML에 값과 이벤트를 연결합니다.

## 3. 로그인 수정하기

1. `member/login.jsp`를 엽니다. 위쪽 `loginForm`이 실제 폼입니다.
2. 문구·입력란·버튼은 HTML에서 수정합니다. `name="email"`, `name="password"`는 제출 데이터 이름입니다.
3. 하단 `submit` 이벤트의 `repository.login(...)`이 현재 Mock 로그인입니다.
4. 가입은 `signup.jsp`의 `repository.register(...)`, 정보 수정은 `profile.jsp`의 `repository.saveProfile(...)`입니다.

폼의 `method="post"`는 향후 서버 제출에 사용할 수 있습니다. 현재는 JS가 `preventDefault()`로 기본 제출을 멈추고 Mock을 실행합니다. 실제 로그인 Servlet을 만든 뒤 `action`과 제출 처리를 함께 연결하세요. 없는 서버 주소로 먼저 변경하지 않습니다.

## 4. 상품 조회 수정하기

HTML은 `product/list.jsp`, `search.jsp`, `detail.jsp`에서 확인합니다. 목록·검색의 공통 동작은 `common/catalog.js`의 `repository.queryProducts(...)`입니다. 상세 JSP는 URL의 `id`로 `repository.getProduct(...)`를 조회하고 수량·합계·장바구니 버튼을 연결합니다.

반복 상품 카드만 `shop-ui.js`의 `productCard(...)`가 만듭니다. 페이지 전체 HTML을 JavaScript로 만들지 않습니다.

## 5. 장바구니·주문 수정하기

- `order/cart.jsp`: `getCart`, `setCartQuantity`, `removeCart`와 수량·삭제 이벤트.
- `order/checkout.jsp`: 배송 폼과 `placeOrder` 제출 이벤트.
- `order/orders.jsp`: 현재 회원 또는 비회원의 주문 목록.
- `order/detail.jsp`, 결제 결과 JSP: 주문 상태·배송 정보·금액 표시.
- `common/order-totals.jspf`: 반복되는 금액 HTML.

상품 수가 바뀌는 장바구니 행·주문 항목·관리자 표 행만 작은 DOM 코드로 반복합니다. 사용자 이름·주소·상품명은 `textContent` 또는 입력란의 `value`로 출력합니다. 사용자 입력을 `innerHTML`에 넣지 마세요.

## 6. 향후 서버 연결 흐름

```text
JSP의 HTML Form 또는 JavaScript
    ↓ HTTP 요청
Servlet / Controller: 입력을 받고 응답 또는 View 선택
    ↓
Service: 가입·재고·주문 같은 업무 규칙
    ↓
Repository: JDBC로 SQL 실행
    ↓
MySQL: 실제 데이터 저장
```

Controller가 `request.setAttribute(...)`로 전달한 데이터를 JSP에서 EL로 출력할 수 있습니다. 필요한 부분만 JS의 `fetch`로 조회할 수도 있습니다. 서버를 먼저 구현한 뒤 현재 `repository` 호출을 하나씩 교체하세요.

`src/main/java/daiso/common`의 기존 MVC와 `member/controller`, `product/controller`는 변경하지 않았습니다. View 이름 규칙은 `common/mvc/JspViewResolver.java`에서 확인합니다. 실제 연결 시 Controller의 반환 View 이름을 해당 JSP 경로에 맞춰 확인해야 합니다. 이번 작업은 API·Service·JDBC 업무 코드를 추가하지 않습니다.

## 7. Mock과 실제 DB의 차이

현재 JSON은 초기 데이터입니다. 변경은 같은 접속 주소의 `localStorage["daisomall.mock.v1"]`, 로그인 ID는 `sessionStorage["daisomall.mock.session"]`에 저장합니다. 기존 저장 형식은 유지했습니다. 다른 주소·브라우저와는 공유되지 않습니다.

사용자가 브라우저 값을 바꿀 수 있으므로 실제 인증·권한·재고 보장이 아닙니다. 비밀번호를 실제로 검증하지 않고 결제·배송도 발생하지 않습니다. 실제 서비스에서는 서버가 인증·권한·재고·금액을 확인하고 DB에 저장해야 합니다.

## 8. 기능 추가 연습: 상품 카드에 품번 표시

이번 변경에 새 기능을 추가하지는 않았습니다. 아래는 나중에 해 볼 예시입니다.

1. `shop-ui.js`의 `productCard`에서 `element("p", "", product.id)`를 카드에 추가합니다.
2. 이 함수는 홈·목록에서 재사용하므로 두 화면을 모두 확인합니다.
3. 화면을 1440·1280·390px로 열어 배치가 깨지지 않는지 확인합니다.
4. 서버 상품 조회를 연결할 때 응답에 `id`가 있는지도 확인합니다.

## 9. Eclipse / Tomcat 실행

1. Java 11과 Tomcat 9를 설치합니다.
2. Eclipse의 **File → Import → General → Existing Projects into Workspace**로 수정할 Worktree를 가져옵니다. 원본 폴더와 혼동하지 마세요. 같은 프로젝트 이름이 이미 있으면 별도 Eclipse workspace를 사용합니다.
3. JavaSE-11과 Apache Tomcat v9.0 Runtime을 설정합니다. Maven·Gradle로 변환하지 않습니다.
4. Servers에서 프로젝트를 추가하고 실행합니다. 기본 접속은 `http://localhost:8080/daisomall/`입니다. 실제 포트·context root는 서버 설정을 확인합니다.
5. 수정이 보이지 않으면 Publish 상태와 콘솔을 확인한 뒤 새로고침합니다.

체험 로그인 버튼을 사용하고 실제 개인정보·비밀번호를 입력하지 않습니다. 자동 검증은 `tools/verify-jsp-structure.cjs`, `verify-commerce.cjs`, `compare-jsp-views.cjs`를 사용합니다. 외부 Playwright 경로는 `PLAYWRIGHT_MODULE`로 지정합니다. 별도 포트는 `FRONTEND_BASE_URL`, 비교할 원본은 `BASELINE_BASE_URL`로 지정합니다. 화면 비교는 변경 전·후 서버가 모두 필요합니다.

## 10. 팀이 다음에 구현할 기능

1. 회원: 실제 가입·로그인·로그아웃, 비밀번호 해시, 서버 세션·권한.
2. 상품: DB 조회·검색·카테고리·페이지 나누기, 관리자 변경 저장.
3. 주문: 회원별 장바구니·주문, 서버 금액 계산, 재고와 주문을 함께 처리하는 트랜잭션.
4. 공통: JDBC 연결·설정 보관, 서버 입력 검증·오류 응답.
5. 결제: 서버 주문 처리 후 테스트 결제 연동. 현재 모의 결제와 구분.

세 사람은 회원 / 상품·관리 / 장바구니·주문으로 나눠 맡을 수 있습니다. 공통 JS·CSS를 고칠 때는 영향을 받는 화면을 함께 확인하세요.
