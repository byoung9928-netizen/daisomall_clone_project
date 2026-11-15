# 초보자 친화형 JSP 리팩터링 — 2026-11-15

## 작업과 원본 보존

- 기준 브랜치·커밋: `refactor/jsp-view-structure`, `bffb78c21875fabf5d332b8d8ba4ceaa5647ad9f`.
- 신규 브랜치: `refactor/beginner-friendly-jsp`.
- 독립 Worktree: `D:\2.Workspace\2026\daiso\daisomall\.cache\worktrees\beginner-friendly-jsp`.
- 원본 작업공간의 미커밋 `member.jspf`는 수정·삭제·이동하지 않았다. 별도 커밋이나 Stash도 만들지 않았다.
- 원본 파일 SHA-256: `E4AA1E714BAD0F14B39D32B1AC6AF289887CD3DFEE4BF47E4C4528075EDC1305`.
- 모든 소스 수정은 신규 Worktree 안에서 진행했다. `dev`, `main`, 기존 작업 브랜치에 커밋하거나 병합하지 않는다.

## 구조 변경

로그인부터 회원 → 상품 → 장바구니·주문 → 홈·공통 → 관리자 순서로 실제 View JSP를 분리했다. 각 화면의 HTML과 전용 스크립트는 해당 JSP에서 확인한다. 공통 스크립트는 `defer`로 먼저 로드하고 페이지 스크립트는 `DOMContentLoaded` 및 `Daiso.ready` 이후 이벤트와 데이터를 연결한다.

| 대상 | 새 위치 |
| --- | --- |
| 회원 4개 화면 | `WEB-INF/views/member/` |
| 목록·검색·상세 | `WEB-INF/views/product/` |
| 장바구니·주문서·주문 내역·상세·결제 결과 | `WEB-INF/views/order/` |
| 홈 | `WEB-INF/views/shop/home.jsp` |
| 관리자 7개 화면 | `WEB-INF/views/admin/`의 화면별 JSP |
| 공통 헤더·메뉴·푸터·금액 HTML | `WEB-INF/views/common/*.jspf` |
| 반복 사용 동작 | `assets/js/common/` |

`WEB-INF/views/templates`, `Daiso.ui.view()`·`part()`와 기존 전체 페이지 렌더러를 제거했다. 정적 화면을 생성하는 새 프레임워크나 템플릿 엔진은 만들지 않았다. 브라우저 Mock 데이터 개수에 따라 반복되는 상품 카드·장바구니 행·주문 항목·표 행만 작은 DOM 코드로 생성한다. 사용자 입력은 `textContent`·`value`로 바인딩한다.

기존 공개 JSP와 `pageName`·`activeNavigation`·`adminSection` 요청 속성을 유지한다. 기존 Forward 경로의 `shop/page.jsp`, `admin/layout.jsp`는 허용된 화면으로 분기한다. 검색은 최초 Forward URI를 확인해 검색 JSP로 연결한다. 관리자 `view`, 상품·주문의 `id`, 검색·정렬·필터·다음 화면 쿼리는 유지한다.

회원·주문서 폼에 POST 제출 방식을 명시했지만 현재는 기존 Mock 제출 이벤트가 동작한다. 없는 API를 호출하지 않는다. Java 커스텀 MVC, 업무 Java 코드, CSS, seed JSON, Mock Repository와 저장 형식은 변경하지 않았다.

## 단계별·최종 검증

Java 11 / Tomcat 9.0.123 / Chrome headless에서 수행했다. 기존 서버를 건드리지 않고 변경 후 서버는 18189, 기준 커밋에서 별도 추출한 원본은 18190 포트를 사용했다. 원본의 미커밋 파일을 기준 서버에 복사하지 않았다.

- 단계별 브라우저 검증: 로그인 → 마이페이지 → 정보 저장 → 목록 필터 → 상세 → 장바구니 담기 → 주문서 진입 통과.
- `node tools/verify-jsp-structure.cjs`: 외부·JSP 내부 스크립트 29개 구문 및 상대 include 경로 검사 통과. 템플릿 복제 시스템 잔존 없음.
- 실제 Tomcat 요청으로 고객·관리자 JSP 컴파일 확인.
- `node tools/verify-commerce.cjs 7`: 1440·1280·390px 전부 통과. 캐러셀·카테고리·상품·회원·장바구니·결제·주문·관리자 기능 확인. 콘솔 오류·가로 넘침 없음.
- 추가 Mock 흐름: 비회원 장바구니 병합·주문, 회원별 주문 구분, 음수 수량 거절, 관리자 접근 제한, seed 오류·카테고리 재시도·페이지 재시도 통과.
- `node tools/compare-jsp-views.cjs`: 동일 저장 데이터로 22개 화면 × 3개 폭, **66건 불일치 0건**.
- `node tools/compare-jsp-views.cjs --edit`: 분류·상품·온라인 재고·매장 재고·회원 편집 폼을 연 상태, **15건 불일치 0건**.

화면 비교는 텍스트, 표시 중인 주요 요소의 위치·크기·폼 값, 화면 영역 PNG를 비교한다. 정적 JSP 안에서 `hidden`으로 준비한 폼은 보일 때 비교한다. 비교에서 발견한 체험 로그인 버튼 간격과 빈 관리자 편집 영역 표시는 원래 모습으로 수정했다. JSP Forward 속성의 따옴표 구문도 실제 컴파일 단계에서 수정했다.

기존 자동 도구가 재생성한 과거 캡처는 원래 내용으로 복원한다. 실제 Eclipse Publish·실물 모바일·Safari/Firefox·스크린리더·실제 DB/API/결제는 검증 범위에 포함하지 않았다. `verify-frontend.cjs`는 같은 commerce 도구를 호출하는 래퍼이므로 중복 실행하지 않았다.

## 다시 검증하기

```powershell
$env:PLAYWRIGHT_MODULE = 'C:\path\to\node_modules\playwright'
$env:FRONTEND_BASE_URL = 'http://127.0.0.1:18189/daisomall/'
$env:BASELINE_BASE_URL = 'http://127.0.0.1:18190/daisomall/'
node tools/verify-jsp-structure.cjs
node tools/verify-commerce.cjs 7
node tools/compare-jsp-views.cjs
node tools/compare-jsp-views.cjs --edit
```

`PreviewServer.java`는 두 번째 인자로 포트를 받을 수 있다. 생략하면 기존 18089를 유지한다. Playwright·Tomcat은 외부 설치를 사용하며 프로젝트에 npm·Maven·Gradle 의존성을 추가하지 않는다.

## 인수인계

[초보자 인수인계 문서](../guides/frontend-handover.md)에 파일 종류·담당 화면·로그인·상품·주문 수정 위치, Mock과 DB의 차이, 향후 Controller → Service → JDBC → MySQL 연결, 작은 기능 추가 연습, Eclipse 실행 및 다음 구현 과제를 작성했다.

## Git 결과와 남은 항목

신규 Worktree에서 요청한 메시지 `refactor: simplify frontend structure for beginner JSP development`로 로컬 커밋을 생성했다. 원본 Worktree의 미커밋 변경은 포함하지 않았다.

**GitHub 업로드는 미완료다.** `git push -u origin refactor/beginner-friendly-jsp`가 인증 단계에서 `User cancelled dialog` 및 `could not read Username`으로 실패했다. 연결된 GitHub 앱도 Git tree 쓰기 요청을 `403 Resource not accessible by integration`으로 거절했다. 원격 브랜치를 강제 변경하거나 자격 증명을 임의로 바꾸지 않았다.

Git 인증 또는 GitHub 앱의 저장소 쓰기 권한이 준비되면 이 Worktree에서 다음 명령을 실행한다. `dev`·`main`·기존 작업 브랜치에는 병합하지 않는다.

```powershell
git push -u origin refactor/beginner-friendly-jsp
```
