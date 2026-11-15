# 이전 JSP 템플릿 구조 기록

> 이 문서는 이전 리팩터링의 구조 기록입니다. 현재 브랜치는 아래 템플릿 복제 시스템을 제거했습니다. 현재 파일 위치·실행 순서·수정 방법은 [초보자 인수인계 문서](guides/frontend-handover.md)를 확인하세요.

정적 마크업은 `src/main/webapp/WEB-INF/views`가 소유합니다. 기존 공개 JSP 진입 경로, CSS, Mock Repository와 저장 형식은 유지합니다.

- `shop/home.jsp`, `shop/page.jsp`, `admin/layout.jsp`: 페이지 문서와 공통 레이아웃.
- `shop/common/*.jspf`: 헤더·내비게이션·푸터.
- `templates/all.jspf`: 고객·관리자·공통 레이아웃 검증 페이지가 공유하는 템플릿 목록.
- `templates/storefront.jspf`: 캐러셀, 기획전, 카테고리 바로가기와 추천 전시.
- `templates/catalog.jspf`: 목록 필터·정렬과 상품 상세.
- `templates/member.jspf`: 로그인·가입·회원정보 폼과 회원 상태별 표시.
- `templates/orders.jspf`: 장바구니, 주문서, 금액 요약과 주문 표시.
- `templates/admin.jspf`: 관리자 표, 검색, 편집 및 데이터별 표시.
- `templates/shop-ui.jspf`, `templates/navigation.jspf`: 상품 카드, 공통 상태 표시와 카테고리 행.

`<template>`은 브라우저가 표시하거나 폼으로 처리하지 않는 JSPF 마크업입니다. 데이터가 브라우저 Mock Repository에 있으므로 JavaScript는 필요한 템플릿을 복제해 데이터를 채우고, 데이터 수에 따라 반복 항목을 추가하거나 상태에 맞는 뷰를 선택합니다. 서버 데이터 조회나 새 인증 계층은 도입하지 않습니다.

`Daiso.ui.view(name, text?, className?)`은 `view-{name}` 템플릿의 루트 요소를 복제합니다. `part(parent, name, text?, className?)`은 복제한 레이아웃 안의 `data-view-part`를 찾습니다. 없는 템플릿이나 바인딩 지점은 오류로 처리합니다. 사용자 입력과 Mock 텍스트는 `textContent`/폼 `value`로만 바인딩합니다. HTML 문자열 조립이나 `innerHTML`을 사용하지 않습니다.

새 화면을 추가할 때 구조·고정 문구·폼 속성은 JSP/JSPF에 작성하고, JavaScript에는 조회·검증·이벤트·동적 값 및 상태 갱신을 작성합니다. 기존 `tools/build-pages.cjs`는 공개 진입 JSP만 생성하며 이 템플릿들은 덮어쓰지 않습니다.

## 검증

Java 11 / Tomcat 9에서 `tools/PreviewServer.java`로 18089 포트의 검증 서버를 실행하고, 설치된 Playwright 모듈을 지정합니다. npm 의존성을 추가할 필요는 없습니다.

```powershell
$env:PLAYWRIGHT_MODULE = 'C:\path\to\node_modules\playwright'
node tools/verify-commerce.cjs 7
node tools/verify-frontend.cjs
```

변경 전 소스를 별도 임시 디렉터리에 복사하고 그 검증 서버만 18090 포트로 실행하면, 다음 명령으로 동일 저장 데이터에 대한 22개 화면 × 3개 폭(1440·1280·390px)의 텍스트, 주요 요소의 위치·크기·폼 값 및 화면 PNG를 비교할 수 있습니다. 비교 도구는 불일치 캡처를 OS 임시 디렉터리에 저장하며 불일치가 있으면 실패합니다. 특정 화면만 다시 비교하려면 경로를 인자로 전달합니다(예: `node tools/compare-jsp-views.cjs signup.jsp`).

```powershell
node tools/compare-jsp-views.cjs
```
