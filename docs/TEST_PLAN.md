# 테스트 및 완료 기준

현재는 설계 단계다. 아래 테스트는 계획이며 실제 기능 검증 완료를 뜻하지 않는다. 가상 데이터 fixture를 사용하고 비동기 경합은 sleep에 의존하지 않는 barrier·제어 clock·장애 hook으로 재현한다.

## 도구·계층

- Backend: JUnit, Spring Boot Test, Testcontainers(PostgreSQL+pgvector, Redis JSON/Query 지원 이미지), 실제 STOMP client. H2로 DB lock·constraint·vector 동작을 대체하지 않는다.
- AI: pytest, async 테스트, 실제 Redis Saver와 Ollama 어댑터 smoke. 단위 테스트는 결정적 fake model, 모델 품질 테스트는 고정 digest·설정·fixture를 사용한다.
- Frontend: TypeScript typecheck, 필요한 상태 로직 테스트, Playwright 핵심 사용자 여정.
- 계약/보안: OpenAPI/이벤트 schema 검증, 역할·소유권 matrix, 의존성/secret scan.
- 통합/성능: 실제 Compose 네트워크, worker/relay 강제 종료·네트워크 단절·재기동, 부하 생성 도구.

## 필수 시나리오

| ID | 시나리오 | 합격 조건 | 단계 |
| --- | --- | --- | --- |
| ENV-01 | 3서비스 build·부팅·health, Flyway 빈 DB/재실행 | 설정 재현 가능, 자동 DDL 없음, 정확 버전 기록 | 1 |
| AUTH-01 | 가입·로그인·BCrypt·바이트 제한·역할 위조 | 해시 저장, 72바이트 초과 절단 없음, 승격 거절 | 2 |
| AUTH-02 | refresh 동시 rotation/replay/만료/logout/Redis 장애 | 한 번만 소비, family 폐기, 원문 미저장, 장애 시 거절 | 2 |
| AUTH-03 | JWT alg/iss/aud/exp/키 교체·CSRF/Origin | 잘못된 token·요청 거절, 정상 흐름 유지 | 2 |
| CHAT-01 | 같은 clientMessageId 동시 저장·payload 충돌 | 원본 1건, 다른 payload 409 | 3 |
| CHAT-02 | DB rollback, relay publish 후 crash, Redis 단절 | rollback 무전달, 중복 수렴, Outbox 회복 후 전달 | 3 |
| CHAT-03 | 타 방 조회/reply/read/WS 구독·관계 해제 | 데이터 노출 없음, 기존 연결도 차단 | 3 |
| CHAT-04 | cursor 경계·오프라인·다중 탭·읽음 역전 | 누락 없이 복구, 읽음 단조, unread 정확 | 3 |
| COM-01 | presence TTL/다중 연결·알림 중복 | 정상 offline 수렴, 알림 1건, AI job 영향 없음 | 3 |
| AI-01 | 기본 한국어 상담·서버 발급 thread·타 학생 접근 | 정상 답변·이력, scope 위조 거절 | 4 |
| JOB-01 | 원본/job/outbox 트랜잭션 중 장애 | 셋 모두 저장 또는 모두 rollback | 5 |
| JOB-02 | 여러 worker 동일 thread/다른 thread | 동일 thread 유효 실행 1개·seq 순서, 다른 thread 독립 | 5 |
| JOB-03 | XREAD 후 crash, pending reclaim, stale fence | 재시도 회복, stale 결과·preview 게시 없음 | 5 |
| JOB-04 | 결과 DB 커밋 후 응답 유실·재제출 | 답변 1건·동일 hash 기존 결과·다른 hash 충돌 | 5 |
| JOB-05 | cancel/result 두 커밋 순서·queued 취소 | 먼저 커밋한 terminal 유지, 취소 이후 저장 0건 | 5 |
| JOB-06 | UI 이탈·CLOSED·DELETING 상태에서 완료 | 이탈/종료 답변 저장, 삭제 답변 거절 | 5 |
| JOB-07 | deadline·backoff·DLQ·trim·Redis 유실 | 무한 선행 대기 없음, DB reconciliation, 삭제 job 부활 없음 | 5 |
| STREAM-01 | 중복·순서 역전·reconnect·느린 구독 | preview 임시 표시, final은 DB 확정 뒤, 복구 가능 | 5 |
| MEM-01 | 완료+24h/읽기/활성 job/완료-enqueue 경합 | 읽기는 TTL 연장 없음, 활성 만료 없음, terminal 후 정확 TTL | 6 |
| MEM-02 | saver 관련 키·latest pointer·pending writes | pin/expire/delete 전 키 집합 처리 검증 | 6 |
| MEM-03 | 삭제 직후 stale saver write·Redis 복구 | 쓰기 관문 차단, head 게시 없음, 최종 잔존 없음 | 6 |
| CTX-01 | 4,000 token·최근10·초장문·tool pair·작은 num_ctx | 입력+출력+여유 한도 준수, 필요한 현재 질문 유지 | 7 |
| CTX-02 | rolling summary 오류·모순·정정·삭제 lineage | 정보 보존 fixture 충족, 삭제 내용 재등장 없음 | 7 |
| LONG-01 | 압축/close 추출 중복·통합·90~365일 | 출처/유형/만료 정확, 중복 후보 수렴 | 8 |
| LONG-02 | 추출 중 기억 수정/삭제·epoch 경합 | 명시 정정 우선, stale commit·재추출 차단 | 8 |
| LONG-03 | 세션/계정 삭제·복수 출처·기억이 든 타 세션 요약 | 잔존 근거 재평가, cache/head 무효화, 모든 파생 삭제 추적 | 8 |
| RAG-01 | 수집 중복·재임베딩·차원 변경·중간 삭제 | 모델 버전 혼합 없음, 삭제 revision 승격 금지 | 9 |
| RAG-02 | 타 학생 자료/회수된 grant/ANN ACL | 허용 자료만 검색, filtered recall 측정 | 9 |
| RAG-03 | prompt injection·가짜 인용·자료 없음 | 권한·도구 범위 유지, 유효 출처만 인용 | 9 |
| GRAPH-01 | 교과 관계·2hop/범위·학생 경유 탐색 | 제한 준수·타 학생 관찰 노출 0건 | 10 |
| GRAPH-02 | 이벤트 중복·projection 지연·Neo4j 장애·삭제 | 멱등 투영, DB deny 우선, 안전한 fallback | 10 |
| UI-01 | 학생/선생님 로그인→일반/AI 채팅→기억 수정/삭제 | 역할별 여정·키보드 접근·에러·재접속 정상 | 11 |
| SYS-01 | 서비스 재시작·백업 복원·삭제 ledger 재적용 | 새 게시 전 tombstone 복구·삭제 데이터 부활 없음 | 12 |
| SYS-02 | 전체 권한 matrix·민감 로그·dependency scan | 누출 없음, 필수 취약점 해소 | 12 |
| PERF-01 | 동시50명·초기 AI worker1·긴 대화 | PRD 후보 목표 측정, 병목/한계 보고 | 12 |

일반 채팅을 AI memory/vector/graph에 넣지 않는 테스트는 3·8·9·10단계의 공통 게이트다. 개인정보 처리 근거를 충족하지 않은 데이터로 이 테스트를 대체하지 않는다.

## 완료 판정과 증거

기능별 긍정·거절·경합·장애 테스트 중 해당되는 항목을 통과해야 한다. 실패 항목은 원인/재현 명령/수정/재실행 결과를 기록하고 완료 상태를 보류한다. 의존성 미설치·Docker 부재·권한 문제는 미실행으로 표시한다. 가상 모델 단위 통과를 실제 Ollama 통합 성공으로 표시하지 않는다.
성능 결과에는 CPU/GPU/RAM, 모델 digest/num_ctx, DB/Redis/Neo4j 버전, 동시성·샘플 수·p50/p95/오류율을 기록한다. 테스트 데이터와 로그에서 대화 전문·인증정보를 제거한다. CI 산출물과 로컬 검증 결과를 단계 보고에 연결한다.

## 설계 단계 실제 검증 기록

2026-11-27: Python 표준 라이브러리로 필수 14개 Markdown 파일과 실제 파일 수 일치, UTF-8 읽기, 비어 있지 않은 본문·제목, 코드 블록 균형, 로컬 상대 링크 대상 존재를 자동 점검했다. **PASS, exit 0**. 예기치 않은 추가 파일은 없었다. 문서 간 세션 종료/삭제·작업 상태·scope·세대/fence·memory epoch·잠금 순서와 요구사항의 단계 매핑을 교차 검토했다. 외부 URL의 장기 유효성이나 실제 의존성 설치 성공을 이 점검으로 보장하지 않는다.

최초 설계 조사 시점에는 애플리케이션 단위·통합·성능·보안 테스트가 미실행이었다. 문서 점검으로 이를 대신하지 않는다. 이후 1단계에서 실제 수행한 검증은 아래에 별도로 기록한다.

같은 날 Java 환경 후속 검증: 사용자 요청으로 Temurin JDK 21.0.13.1+2 설치 및 시스템 JAVA_HOME/PATH 설정 완료. 갱신된 환경에서 `java -version`, `javac -version`은 모두 21.0.13.1을 반환하고 실행 경로는 `C:\Program Files\Eclipse Adoptium\jdk-21\bin`이었다. 임시 디렉터리에서 `javac --release 21`로 컴파일 후 `java`로 실행해 `Runtime.version().feature() == 21` 검사를 통과(exit 0)했다. 임시 소스·class 파일은 정리했다. 당시 Docker 및 나머지 1단계는 미완료였으며 아래 검증으로 구분한다.

## 1단계 실제 검증 기록

2026-11-27, TUT-2. 검증 데이터는 환경 확인용이며 실제 학생 데이터는 사용하지 않았다.

| 검증 | 결과 |
| --- | --- |
| Docker Engine·Compose 및 hello-world | PASS, Linux 엔진·D 드라이브 가상 디스크 확인 |
| `npm --prefix frontend run build` | PASS, TypeScript 검사 및 Vite production build |
| `backend/gradlew.bat --no-daemon -p backend build` | PASS, Java21. test NO-SOURCE이므로 Java 단위 테스트 통과로 계산하지 않음 |
| `.venv/Scripts/python.exe -m pytest -q` (ai-service 디렉터리) | PASS, 2개. DB 장애에도 liveness 유지·readiness503 및 오류 정보 보호, API 문서 비공개 |
| `docker compose --env-file .env -f infra/compose.yml up -d --build --wait` | PASS, Linux 이미지3개 빌드 및 서비스7개 healthy |
| `python scripts/verify-environment.py` | PASS, 전체 서비스·비공개 포트·React/Spring/FastAPI·pgvector·Flyway·DB계정·Redis JSON/검색·Neo4j 인증 |
| Spring 재기동 후 동일 환경 검증 | PASS, Flyway 성공 이력1건 유지·재적용/중복 생성 없음 |
| 소스·구성 점검 | PASS, 생성한 비밀정보6개와 로컬 런타임 데이터가 소스에 없음. 이미지 digest8종 일치·문서 링크/인코딩 점검 |

실제 빌드 중 발견한 CSS 타입 선언·Linux module 검증 메타데이터·Starlette 테스트 의존성·Neo4j 설정 이름·Windows8080 바인딩·Redis 모듈 로딩 문제를 수정한 뒤 해당 검증을 다시 통과했다. 실패한 중간 결과를 성공으로 기록하지 않았다. 공통 환경 원격 CI는 `.github/workflows/environment.yml`에서 같은 빌드·테스트·통합 검증을 수행하며, 실제 원격 결과와 develop 병합 이력은 TUT-2 및 해당 PR의 Checks를 근거로 확인한다.

회원·인증·메시지·Agent·기억·RAG·성능·운영 보안 테스트는 후속 단계 범위로 미실행이다.
