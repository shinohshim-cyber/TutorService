# AI 기반 개인 맞춤형 학습도우미 — TutorService

학생·선생님의 실시간 1:1 채팅과 학생 전용 AI 학습 상담을 통합한다. AI 상담은 단기·장기 기억, Context Engineering, pgvector 검색과 Neo4j GraphRAG를 단계적으로 도입한다.

현재 상태: **설계 초안 작성 완료, Java 21 선택·로컬 설치 완료, 나머지 구현은 사용자 검토 대기**. 애플리케이션·컨테이너·기능 테스트는 아직 구현하지 않았다. 문서 작성 기준일은 2026-11-27(Asia/Seoul)이다.

## 디렉터리 조사 결과

작업 위치는 `D:\Projects\aisource\TutorService`다. 최초 조사에서 숨김 항목을 포함한 항목 수는 0이었다. 기존 코드, Git 메타데이터, 빌드 파일, 의존성 잠금 파일 및 상위 AGENTS.md는 발견되지 않았다. 기존 파일 수정·삭제 없이 설계 문서만 새로 작성했다.

## 로컬 환경 실측

| 항목 | 확인 결과 | 조치/판정 |
| --- | --- | --- |
| Java / javac | Temurin 21.0.13.1+2 / javac 21.0.13.1, JAVA_HOME=`C:\Program Files\Eclipse Adoptium\jdk-21\` | 사용자 지시에 따라 설치·시스템 JAVA_HOME/PATH 설정 완료. `--release 21` 컴파일·실행 통과 |
| Python | `C:\Python312\python.exe`, 3.12.6 | 3.12 계열 사용 가능. 최신 보안 패치 적용 검토 |
| Python Launcher | py는 있으나 등록된 Python 없음 | `python` 경로를 명시해 가상환경 생성 예정 |
| pip | 26.3 | 프로젝트 가상환경은 아직 없음 |
| Node.js / npm | 24.22.1 / 11.19.1 | 프런트엔드 후보 기준 충족, 실제 설치·빌드 미검증 |
| Gradle | PATH에서 발견되지 않음 | 전역 설치 대신 검증된 Wrapper 도입 예정 |
| Docker / Compose | PATH와 기본 Docker 설치 경로에서 미확인 | 설치 여부·엔진 기동 확인 필요. 컨테이너 검증 불가 |
| Git | 2.56.0.windows.2 | 실행 가능, 저장소 초기화는 미실시 |
| Ollama | 0.40.18, 모델 목록·show 조회 성공 | 실제 생성·임베딩 품질/속도는 미검증 |
| WSL | `wsl --status`가 E_ACCESSDENIED 반환 | 현재 실행 권한에서 확인 불가, 미설치로 단정하지 않음 |
| RAM / GPU | CIM 조회 접근 거부 | 하드웨어 규모 미확정, 모델 동시성 확정 불가 |

Java 최초 조사값은 17.0.12였다. 이후 사용자 요청으로 공식 winget 패키지 `EclipseAdoptium.Temurin.21.JDK` 버전 `21.0.13.102`(JDK 21.0.13.1+2)를 설치했다. 갱신된 시스템 환경변수를 읽은 검증 프로세스에서 java/javac 모두 새 경로로 해석되고 Java 21 smoke 검사가 통과했다. 기존에 실행 중인 Codex·IDE·터미널은 이전 환경변수를 유지할 수 있으므로 재시작 후 사용한다. 기존 JDK 17은 삭제하지 않았다.

Ollama 로컬 모델: bge-m3, qwen3-vl:4b, embeddinggemma, gemma4:e2b, gemma2. `gemma4:e2b`의 show 결과는 컨텍스트 상한 131072, `bge-m3`는 상한 8192·임베딩 차원 1024다. 이는 메타데이터이며 실제 실행 num_ctx/가용 메모리·성능 보장은 아니다. 변경 가능한 태그 대신 모델 digest를 고정할 예정이다.

## 기술 조합 제안

아래는 **초기 후보**다. 최신판을 무조건 선택하지 않으며 1단계에서 지원 기간·보안 공지·실제 resolver/빌드/통합 테스트 후 정확한 패치 버전과 이미지 digest를 확정한다.

| 영역 | 제안 | 의존성·검증 항목 |
| --- | --- | --- |
| JVM | JDK 21 + Spring Boot 4.0.x + Gradle 8.14.x | Boot 4의 Spring Framework 7 / Security 7 / Jakarta EE 11 계열. BOM 사용, Jackson·JWT·STOMP 통합 검증 |
| Frontend | Node 24 + React 19.x + TypeScript + Vite | React/TS/Vite 패치 버전과 peer dependencies는 npm resolver 후 고정. STOMP client 도입 |
| AI API | Python 3.12 + FastAPI + Pydantic 2 + Uvicorn | FastAPI/Starlette/AnyIO 호환 세트로 lock. async HTTP와 worker lifecycle 분리 |
| AI Agent | LangChain / LangGraph + langchain-ollama + langgraph-checkpoint-redis | 개별 최신 버전을 섞지 않고 공통 core/checkpoint 제약을 resolve하고 저장·재개 API 확인 |
| ORM | Spring Data JPA / SQLAlchemy 2 + psycopg 3 | Java는 업무 원본, Python은 제한된 RAG/기억 데이터 접근. DDL은 Flyway 단일 소유 |
| PostgreSQL | 17.x + pgvector 0.8.7 후보 | Flyway core + PostgreSQL 모듈 + JDBC driver. 확장 포함 이미지·인덱스 검증 |
| Redis | 8.x(JSON·Query Engine 포함 배포판) | Checkpointer 모듈 필수. 인증/큐 Redis와 checkpoint Redis는 인스턴스 분리 권장 |
| Neo4j | 지원 중인 2026.x 서버 후보 + Python driver 6.x | 정확한 서버 패치·에디션·라이선스와 driver 조합은 10단계 검증 |
| LLM / embedding | 현재 Ollama, gemma4:e2b / bge-m3 우선 평가 | 한국어 튜터 평가 후 확정. embedding 1024 차원은 실제 API 출력으로 재확인 |
| Infra | Docker Desktop + Compose v2, Linux containers | Windows/WSL 권한 및 메모리 확인. DB는 외부 포트 비공개 원칙 |

Spring Boot 4.0 공식 요구사항은 Java 17 이상과 Gradle 8.14 이상(8.x) 또는 9.x를 명시한다. Java 21에서 Gradle 실행은 8.5부터 지원된다. 따라서 제안 조합은 문서상 교집합에 있다. Boot 3.5는 대안이지만 새 프로젝트는 4.0을 우선 평가한다. [Boot 요구사항](https://docs.spring.io/spring-boot/4.0/system-requirements.html), [Gradle 호환표](https://docs.gradle.org/current/userguide/compatibility.html).

Vite 공식 가이드의 Node 최소 기준은 20.19+ 또는 22.12+다. 현재 Node 24는 기준을 충족하지만 lock 생성과 빌드 검증이 필요하다. [Vite 가이드](https://vite.dev/guide/).

Redis Checkpointer는 Redis JSON과 Query Engine이 필요하다. Python 3.12는 공식 지원 범위에 포함된다. TTL 옵션 단위는 분이다. 완료 시점 기준 정책은 별도 lifecycle 계층으로 구현한다. [Redis 요구사항](https://redis.io/docs/latest/integrate/langgraph/), [TTL 옵션](https://redis-developer.github.io/langgraph-redis/v0.6.0/user_guide/ttl.html).

그 외 검토 근거: [FastAPI 릴리스](https://fastapi.tiangolo.com/release-notes/), [LangChain 설치](https://github.com/langchain-ai/docs/blob/main/src/oss/langchain/install.mdx), [Redis checkpointer 패키지](https://pypi.org/project/langgraph-checkpoint-redis/), [pgvector](https://github.com/pgvector/pgvector), [Flyway PostgreSQL 모듈](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database), [Neo4j 드라이버](https://neo4j.com/docs/python-manual/current/install/), [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md).

## 설계 문서

| 문서 | 내용 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 개발·검증 규칙 |
| [PRD](docs/PRD.md) | 범위, 요구사항, 수용 기준 |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | 경계, 데이터 흐름, 장애 처리 |
| [DATABASE](docs/DATABASE.md) | 데이터 모델과 제약 |
| [API_SPEC](docs/API_SPEC.md) | REST·STOMP·내부 계약 |
| [CHAT_DESIGN](docs/CHAT_DESIGN.md) | 일반 채팅 전달·읽음·권한 |
| [AI_AGENT_DESIGN](docs/AI_AGENT_DESIGN.md) | 작업 상태·순서·취소·저장 |
| [MEMORY_POLICY](docs/MEMORY_POLICY.md) | TTL·추출·정정·삭제 |
| [CONTEXT_ENGINEERING](docs/CONTEXT_ENGINEERING.md) | 압축·요약·예산 |
| [RAG_DESIGN](docs/RAG_DESIGN.md) | 문서 파이프라인·검색 범위·그래프 |
| [SECURITY](docs/SECURITY.md) | 인증·인가·개인정보·AI 보안 |
| [TASKS](docs/TASKS.md) | 구현 순서, 승인·완료 게이트 |
| [TEST_PLAN](docs/TEST_PLAN.md) | 기능·경합·장애·보안 검증 |

## 다음 작업

Java 21 준비는 완료했다. 나머지 1단계는 사용자 확인 후 Docker 환경 준비·의존성 버전 고정·최소 골격·Compose·헬스체크·Flyway·CI 구성을 진행한다. 현재 실행할 애플리케이션이나 기능 테스트 명령은 없다. 각 단계의 성공·실패 근거를 TASKS에 기록한다.
