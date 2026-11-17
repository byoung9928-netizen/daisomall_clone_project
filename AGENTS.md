# 다이소몰 클론 개발 지침

이 문서는 저장소 전체의 코드 작성과 수정에 적용한다. 작업 전 실제 소스, 빌드 설정, 실행 환경을 확인한다. 초기 README의 Mock 전용 설명과 현재 소스가 다를 때는 현재 소스와 설정을 기준으로 삼는다.

## 기술 스택과 호환성

| 영역 | 현재 기준 |
| --- | --- |
| Java | Java SE 11 대상. 확인한 로컬 JDK는 Eclipse Temurin 11.0.32.1 |
| 웹 서버 | Eclipse Dynamic Web Project 4.0과 WTP. 로컬 Apache Tomcat 9.0.123 |
| 서버 API | Servlet 4.0 (`javax.servlet`), JSP 2.3, EL 3.0. `WEB-INF/lib/servlet-api.jar`의 Tomcat 번들 버전은 9.0.123 |
| DB | MySQL 8.0 (`daisomall`). 로컬 설치 파일은 8.0.46. JDBC 4.2, MySQL Connector/J 8.0.28, JNDI DataSource `java:comp/env/jdbc/mysqlDB` |
| 추가 JAR | `cos.jar`가 포함되어 있으며 정확한 버전은 확인되지 않음 |
| 화면 | JSP/JSPF, HTML, CSS, Vanilla JavaScript. SUITE와 Pretendard 웹폰트를 로컬에서 제공 |
| 빌드·검증 | Eclipse/WTP에서 빌드와 배포를 수행한다. Maven·Gradle·npm 설정은 없다. `tools/`의 일부 검증은 Node.js와 별도 Playwright 설치를 사용한다 |

- 현재 Java, Tomcat, MySQL 버전과 `javax.servlet` 패키지에 맞는 안정적인 문법과 공식 권장 API를 우선한다. Java 11 이후 기능, 미리 보기 기능, 다른 Servlet 버전 전용 API는 사용하지 않는다.
- 신규 코드에 deprecated API와 구식 패턴을 도입하지 않는다. JavaScript는 `const`를 기본으로 쓰고 재할당이 필요한 변수에 `let`을 사용한다. `var`는 사용하지 않는다. 브라우저 API는 지원 범위를 확인하고, IDE 표시 문제는 검사기 설정과 실제 실행 결과를 확인해 해결한다.
- 기존 기술 스택, 라이브러리 버전, 빌드 방식 변경이나 새 라이브러리·프레임워크 도입이 필요하면 이유, 호환성, 영향 범위를 먼저 제안하고 사용자 승인 후 적용한다.

## 폴더 구조와 MVC

| 경로 | 역할 |
| --- | --- |
| `src/main/java/daiso/common/mvc/` | `/app/*` 프런트 컨트롤러, 요청 매핑, 핸들러, JSP 뷰 결정 |
| `src/main/java/daiso/` | `product`, `member`, `cart`, `oreder` 등 기능별 서버 코드. `oreder`는 현재 디렉터리 이름 |
| `src/main/webapp/` | 공개 JSP 진입점과 배포할 웹 자원 |
| `src/main/webapp/WEB-INF/views/` | 기능별 JSP 뷰와 공통 JSPF 조각 |
| `src/main/webapp/WEB-INF/lib/` | 프로젝트에 포함된 JAR |
| `src/main/webapp/assets/` | CSS, JavaScript, 이미지, 폰트, 기존 Mock JSON |
| `tools/`, `src/main/webapp/tests/` | 검증·보조 스크립트와 브라우저 테스트 페이지 |
| `docs/`, `.settings/` | 프로젝트 문서와 Eclipse 설정 |

- 상품 조회는 기존 경로를 유지한다. 공개 JSP에서 `/app/*`로 요청을 전달하고, 컨트롤러가 DAO·DTO로 조회한 데이터를 요청 속성에 담아 `/WEB-INF/views/`의 JSP에서 렌더링한다. 상품 자동완성은 기존 `/api/products/*` 엔드포인트를 활용한다.
- 컨트롤러는 요청 검증과 흐름을 담당하고, DAO는 SQL과 결과 매핑을 담당한다. 화면 표시를 위한 값을 컨트롤러에서 준비하고 JSP에서는 표시를 담당한다. JSP와 브라우저 코드에 JDBC·SQL이나 서버 업무 로직을 넣지 않는다.
- 기존 MVC 계층, 라우팅 방식 또는 뷰 전달 구조를 변경해야 하면 변경 이유와 영향을 설명하고 사용자 승인 후 작업한다.
- 저장소의 원본을 수정하고 Eclipse에서 배포한다. `build/classes`와 Eclipse `wtpwebapps`는 빌드·배포 결과물이다. 기존 컨트롤러, DAO, JSPF, CSS와 JS를 먼저 살펴 공통 동작을 재사용하고, 변경 범위에 필요한 만큼만 정리한다.

## 화면, 데이터, 오류 처리

- 의미에 맞는 HTML을 사용한다. 여러 열의 상품 목록과 페이지 영역에는 CSS Grid, 한 방향의 버튼·텍스트 정렬에는 Flexbox를 우선 검토한다. 작은 화면과 넓은 화면에서 줄바꿈과 스크롤을 확인한다.
- 키보드 이동, 초점 표시, 폼 레이블, 이미지 대체 텍스트, 색 대비를 확인한다. 동적으로 바뀌는 결과와 오류는 사용자가 인지할 수 있게 표시하고 WCAG 2.2 AA를 기준으로 점검한다.
- 서버에서 입력값, 권한, 상태 변경 조건을 검사한다. SQL 값은 `PreparedStatement`에 바인딩하고 동적 정렬 열처럼 바인딩할 수 없는 항목은 허용 목록으로 제한한다. HTML 출력은 출력 위치에 맞춰 이스케이프한다. 상태 변경 요청에는 인증·인가와 CSRF 방어를 고려한다. 자격 증명과 개인정보는 코드, 로그, 응답에 노출하지 않는다. Mock 저장소를 실제 인증이나 권한 확인에 사용하지 않는다.
- JDBC 자원은 `try-with-resources`로 닫고 함께 성공해야 하는 DB 변경은 하나의 트랜잭션으로 처리한다. 검색과 목록은 DB에서 범위를 제한하며 반복 조회와 불필요한 전송을 줄인다. 오류는 사용자에게 처리 가능한 메시지로 표시하고 서버에는 진단에 필요한 내용만 기록한다.

## 검증과 참고 문서

- 변경 후 가능한 빌드와 관련 테스트를 실행한다. Java는 Java 11 대상으로 Eclipse 빌드 또는 `javac --release 11` 컴파일을 확인한다. JSP 변경은 Tomcat 9에서 실제 페이지가 렌더링되는지 확인하고, 외부 JavaScript는 `node --check <파일>`로 문법을 검사한다.
- 프로젝트 루트에서 `node tools/verify-jsp-structure.cjs`를 실행할 수 있다. 기존 카테고리 데이터용 `<template id="product-category-data">`도 이 검사가 감지하므로 실패하면 실제 원인을 확인한다. 브라우저 테스트는 서버와 Playwright가 준비되고 테스트가 현재 구현을 대상으로 할 때 실행한다. 기존 Mock 기반 테스트와 DB 기반 상품 검증의 전제는 확인한다.
- 변경된 기능의 정상 동작과 관련 경계 조건을 확인한다. 상품 변경에서는 목록·검색·카테고리·상세·페이지 이동 중 영향받는 흐름을 검증한다. 실행한 검사, 결과, 환경상 실행하지 못한 검사를 작업 결과에 기록한다.
- 오래된 예제보다 사용 중인 버전에 맞는 공식 문서와 표준을 우선한다. [Java 11](https://docs.oracle.com/en/java/javase/11/) · [Tomcat 9 및 Servlet/JSP](https://tomcat.apache.org/tomcat-9.0-doc/index.html) · [MySQL 8.0](https://dev.mysql.com/doc/refman/8.0/en/) · [MDN 웹 기술 문서](https://developer.mozilla.org/en-US/docs/Web/JavaScript) · [WCAG 2.2](https://www.w3.org/TR/WCAG22/)
