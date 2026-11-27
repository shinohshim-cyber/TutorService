# TutorService 개발 지침

이 저장소는 학생·선생님 일반 채팅과 학생 전용 AI 학습 상담을 개발한다. 설명과 작업 보고는 한국어로 작성한다.

## 작업 절차

- 구현 전 README.md와 해당 기능의 docs 문서를 읽는다. 요구사항·계약 변경은 설계 문서에 함께 반영한다.
- 현재는 설계 검토 단계다. 사용자가 구현 진행을 확인하기 전에는 애플리케이션 코드, 빌드 설정, 인프라를 생성하거나 설치하지 않는다.
- docs/TASKS.md의 1~12단계를 순서대로 진행한다. 한 번에 전체 프로젝트를 구현하지 않는다.
- 각 단계에서 구현 내용, 변경 파일, 실행한 테스트와 결과, 미해결 문제, 다음 작업을 보고한다. 실패·미실행 검증을 통과로 기록하지 않는다.
- 기존 파일을 읽지 않고 덮어쓰거나 삭제하지 않는다. 비밀정보와 실제 학생 대화를 커밋하지 않는다.

## Git 및 Jira 작업

- 기본 브랜치는 `develop`이다. 자세한 작업 절차는 CONTRIBUTING.md를 따른다.
- Jira 프로젝트는 `TUT`, 보드 ID는 `3`이다: https://gad03302.atlassian.net/jira/software/projects/TUT/boards/3
- 실제 Jira 키를 포함한 `feature/TUT-번호-설명`, `fix/TUT-번호-설명`, `docs/TUT-번호-설명`, `chore/TUT-번호-설명` 브랜치에서 작업하고 PR base를 develop으로 지정한다.
- 커밋 메시지와 PR 제목·본문에 실제 TUT 이슈 키를 연결한다. 이슈 키를 임의로 만들지 않는다.
- 상태는 `할 일 → 진행 중 → 체크 → 완료`다. 체크는 본인의 diff·테스트·보안 점검 단계이며 develop 병합과 필수 검증 이후에 완료 처리한다.
- 현재 1인 개발이므로 필수 타인 승인은 0명이다. PR 경유·리뷰 대화 해결·강제 push 및 브랜치 삭제 차단은 유지한다. 팀원 참여 시 승인·Code Owner 정책을 다시 확인한다.
- GitHub for Atlassian 자동 연결 및 Jira 자동 상태 전이는 실제 검증 전까지 완료로 보고하지 않는다.

## 구조 및 책임

- Java 기준은 사용자 확인된 **JDK 21**이다. 향후 Gradle toolchain, 컴파일·테스트, 컨테이너 런타임도 Java 21로 맞춘다. 로컬 설치 확인 버전은 Temurin 21.0.13.1+2다.
- 계획 디렉터리: frontend/, backend/, ai-service/, infra/, docs/. 현재 코드 디렉터리는 아직 없다.
- backend는 common, identity, relationship, notification, chat, aichat 모듈로 구분한다. 일반 채팅과 AI 채팅의 Entity/Repository/Service를 분리한다.
- Controller는 요청 변환·검증, Service는 권한·유스케이스·트랜잭션, Repository는 영속화, Entity는 불변조건, DTO는 전송 계약을 담당한다.
- 외부 HTTP/WS 진입점은 Spring Boot다. FastAPI, Redis, Neo4j, Ollama는 내부 전용이다.
- PostgreSQL DDL은 backend의 Flyway만 소유한다. JPA ddl-auto=validate, SQLAlchemy create_all 금지. Neo4j/Redis 인덱스 변경은 별도 버전 스크립트로 관리한다.

## 반드시 지킬 불변조건

- DB 커밋 이전에 영구 메시지를 전달하지 않는다. 일반 메시지·완성된 AI 답변은 Outbox로 전달한다. 스트리밍 조각은 임시 미리보기로만 표시한다.
- 전달은 at-least-once, 영구 저장은 unique key와 원자적 조건 검사로 멱등 처리한다. exactly-once 전달을 주장하지 않는다.
- 동일 AI thread는 DB 순번·thread lease·fencing token으로 직렬화한다. Redis consumer group만으로 직렬화를 가정하지 않는다.
- 작업 등록과 원본 메시지 저장은 같은 DB 트랜잭션에 Outbox까지 기록한다.
- 취소·삭제와 최종 저장은 같은 session/job 행 잠금 순서로 경합을 해결한다. 오래된 실행 시도는 결과·체크포인트·기억을 게시할 수 없다.
- 학생 범위는 인증 주체로 결정한다. 클라이언트 studentId/threadId 및 LLM의 필터를 신뢰하지 않는다.
- 일반 채팅은 기본적으로 AI 입력·기억 추출·RAG 수집 대상에서 제외한다.
- Refresh Token은 원문 대신 HMAC digest를 저장하고 Rotation을 원자적으로 처리한다. BCrypt 입력의 바이트 제한을 검증한다.

## 검증 및 실행

실행 가능한 명령은 1단계 구축 후 README에 실제 확인 결과와 함께 추가한다. 현재 빌드·테스트 명령은 존재하지 않는다.
이후 backend는 Gradle Wrapper, frontend는 npm lockfile, ai-service는 해시가 포함된 의존성 잠금 파일을 사용한다. Docker 기반 통합 테스트는 실제 PostgreSQL/Redis/Neo4j 조합으로 수행한다.
검증 기준은 docs/TEST_PLAN.md를 따른다. 기능별 보안·경합·장애 테스트가 실패하면 완료 처리하지 않는다.
