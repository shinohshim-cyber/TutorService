# 2단계 회원관리·인증 결과

기준일 2026-11-27. 에픽 [TUT-5](https://gad03302.atlassian.net/browse/TUT-5), WBS02.01~02.08/TUT-29~36. Java21을 유지하며 일반/AI 채팅은 이번 변경에 포함하지 않았다. 원격 CI·최종 병합 이력은 해당 Jira와 PR에서 확인한다.

## 구현 내용

- 회원 가입/로그인/프로필/탈퇴: 정규화 email UNIQUE, BCrypt cost12·12자/UTF-8 72바이트 정책, 역할 임의 상승 거절, 탈퇴 익명화·모든 session version 차단.
- JWT: HS256 고정·iss/aud/exp/nbf/sub/jti/kid/sid/version 검증, 현재/이전 키 교체 경로,15분 Access.
- Refresh: CSPRNG opaque 원문을 Redis에 저장하지 않고 HMAC digest만 보관. Lua 원자 Rotation·USED 재사용 시 family 폐기, 절대14일 TTL 고정, 로그아웃 즉시 Access 회수.
- Origin allowlist·CSRF·고정창 로그인 제한·16KB JSON 제한과 공통 오류. Redis 장애는503으로 fail closed.
- 운영자 email 귀속 단회 선생님 가입 초대, 검증된 선생님→학생 담당 초대·수락·해제. 행 잠금/활성 쌍 unique/본인 범위 cursor 목록.
- React 가입·로그인·프로필·관계·탈퇴 화면. Access는 메모리, Refresh는 HttpOnly cookie, 다중 탭 refresh 직렬화와 로그아웃 공유.

## 변경 파일

| 영역 | 주요 파일 |
| --- | --- |
| Backend | backend/src/main/java/com/tutorservice/identity/**, relationship/**, common/web/** |
| DB | backend/src/main/resources/db/migration/V2__identity_and_relationships.sql |
| 의존성/설정 | backend/build.gradle, gradle.lockfile, gradle/verification-metadata.xml, application.yml |
| Frontend | frontend/src/api.ts, main.tsx, style.css, nginx.conf |
| 실행/검증 | infra/compose.yml, .env.example, scripts/Initialize-Environment.ps1, create-teacher-invitation.py, verify-auth.py, verify-environment.py |
| CI | .github/workflows/environment.yml의 build-and-integration에 인증 통합/Redis 장애 검증 추가 |
| 설계 | AGENTS/README, AUTH_DESIGN, API_SPEC, DATABASE, SECURITY, LOCAL_SETUP, TASKS, TEST_PLAN |

## 실제 검증

Java21 build와 JUnit4개, frontend typecheck/production build 통과. 실제 Compose PG/Redis 통합10개 검증 그룹에서 정상/거절·초대 만료/재사용·프로필 역할 위조·cursor/입력 경계·Refresh barrier 경합200/401·family 폐기·만료/logout·탈퇴·rate limit·Redis 정지/finally 복구·로그 비노출을 통과했다. 데이터는 UUID 가상 fixture만 사용하고 해당 생성 데이터만 정리했다.

Chrome headless에서 가입→로그인→이름 수정→새로고침 refresh→다중 탭 logout→재로그인→탈퇴를 통과했다. HttpOnly/SameSite/path와 Web Storage 비사용, JavaScript 런타임 오류 없음, 데스크톱1360px/모바일390px 가독성·가로 넘침을 확인했다. 브라우저 E2E는 로컬 수행이며 CI에는 아직 포함하지 않았다.

7개 서비스 health·비공개 포트·DB 역할·pgvector·checkpoint Redis JSON/Search·Neo4j·backend 재기동 시 Flyway V1/V2 중복 방지도 검증했다. 로컬 실제 비밀정보8개 소스 비포함과 문서 인코딩/링크를 점검했다.

## 제약 및 다음 작업

2단계 필수 기능의 로컬 검증 차단 문제는 없다. 운영자의 선생님 신원 확인은 외부 절차이며 이 시스템이 자동 판별하지 않는다. HTTP loopback의 Secure=false cookie 설정은 운영 TLS 환경에서 바꿔야 한다. 개인정보/백업 보관·운영 키 교체 실행·부하·전체 취약점 검증은12단계 운영 게이트다.

다음은 3단계 일반 채팅: 활성 관계 기반 방·메시지/읽음·REST/STOMP 인가, 커밋 후 Outbox 전달, 중복/rollback/오프라인/관계 회수 검증이다. WS가 아직 없어 연결 종료 검증을 이 단계의 완료로 주장하지 않는다.
