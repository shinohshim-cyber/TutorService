# AI 기반 개인 맞춤형 학습도우미 — TutorService

학생·선생님의 실시간 1:1 채팅과 학생 전용 AI 학습 상담을 통합한다. AI 상담은 단기·장기 기억, Context Engineering, pgvector 검색과 Neo4j GraphRAG를 단계적으로 도입한다.

현재 상태: **1단계 개발 환경 구성 및 로컬 검증 완료**. Java21·Docker Desktop과 세 서비스 최소 골격·의존성 잠금·Compose·Flyway·CI를 구성했고 7개 컨테이너의 실제 통합 검증을 통과했다. 회원·채팅·AI Agent 기능은 이후 단계다. 최종 원격 CI·PR 병합 이력은 [TUT-2](https://gad03302.atlassian.net/browse/TUT-2)에서 추적한다. 기준일은 2026-11-27(Asia/Seoul)이며 실행법·제약·검증 결과는 [LOCAL_SETUP](docs/LOCAL_SETUP.md)에 기록한다.

## 로컬 실행

Docker Desktop을 시작하고 저장소 루트의 새 PowerShell 터미널에서 실행한다.

```powershell
./scripts/Initialize-Environment.ps1
docker compose --env-file .env -f infra/compose.yml up -d --build --wait --wait-timeout 240
python scripts/verify-environment.py
```

화면은 `http://127.0.0.1:5173`, Spring API는 `http://127.0.0.1:18080`이다. `.env`에 생성되는 실제 비밀정보는 커밋·공유하지 않는다. 상세한 개발·중지 명령과 버전 정책은 LOCAL_SETUP을 따른다.

## GitHub 및 Jira

기본 브랜치는 **develop**이며 [GitHub 저장소](https://github.com/shinohshim-cyber/TutorService)와 [Jira TUT 보드](https://gad03302.atlassian.net/jira/software/projects/TUT/boards/3)를 사용한다. 실제 TUT 이슈 키를 포함한 작업 브랜치에서 변경하고 PR 자기 점검 후 develop으로 병합한다. 현재 1인 개발 기준으로 타인 승인은 필수가 아니다. PR 경유·강제 push 및 develop 삭제 차단은 유지한다.

Jira는 `할 일 → 진행 중 → 체크 → 완료` 순서로 관리한다. 브랜치·커밋·PR 규칙과 자기 점검·완료 기준은 [CONTRIBUTING.md](CONTRIBUTING.md)를 따른다. [협업 환경 구성 TUT-1](https://gad03302.atlassian.net/browse/TUT-1)에서 설정과 검증 결과를 추적한다.

## 디렉터리 조사 결과

작업 위치는 `D:\Projects\aisource\TutorService`다. 최초 조사에서 숨김 항목을 포함한 항목 수는 0이었다. 기존 코드, Git 메타데이터, 빌드 파일, 의존성 잠금 파일 및 상위 AGENTS.md는 발견되지 않았다. 기존 파일 수정·삭제 없이 설계 문서만 새로 작성했다.

## 로컬 환경 실측

| 항목 | 확인 결과 | 조치/판정 |
| --- | --- | --- |
| Java / javac | Temurin 21.0.13.1+2 / javac 21.0.13.1, JAVA_HOME=`C:\Program Files\Eclipse Adoptium\jdk-21\` | 사용자 지시에 따라 설치·시스템 JAVA_HOME/PATH 설정 완료. `--release 21` 컴파일·실행 통과 |
| Python | `C:\Python312\python.exe`, 3.12.6 | 3.12 계열 사용 가능. 최신 보안 패치 적용 검토 |
| Python Launcher | py는 있으나 등록된 Python 없음 | `python` 경로를 명시해 가상환경 생성 예정 |
| pip | 26.3 | 프로젝트 가상환경은 아직 없음 |
| Node.js / npm | 24.22.1 / 11.19.1 | 의존성 설치·typecheck·production build 통과 |
| Gradle | 프로젝트 Wrapper8.14.3 | 배포 SHA256·의존성 잠금/해시 및 Java21 빌드 검증 |
| Docker / Compose | Desktop4.100.1 / Engine29.9.3 / Compose5.7.1 | Linux 엔진·hello-world·D 드라이브 가상 디스크 검증 통과 |
| Git | 2.56.0.windows.2 | 사용자 제공 저장소 origin 연결 확인, 기본 브랜치 develop |
| Ollama | 0.40.18, 모델 목록·show 조회 성공 | 실제 생성·임베딩 품질/속도는 미검증 |
| WSL | 권한 승인 후 조회 성공: 3.0.5.0, Ubuntu-24.04, WSL2 | 기존 배포판 유지, 별도 Ubuntu 설치 없음 |
| RAM / GPU | 약 15.9 GiB RAM, Intel HD Graphics 4600 | 하드웨어 조회 완료, 실제 모델 성능·동시성은 미측정 |

Java 최초 조사값은 17.0.12였다. 이후 사용자 요청으로 공식 winget 패키지 `EclipseAdoptium.Temurin.21.JDK` 버전 `21.0.13.102`(JDK 21.0.13.1+2)를 설치했다. 갱신된 시스템 환경변수를 읽은 검증 프로세스에서 java/javac 모두 새 경로로 해석되고 Java 21 smoke 검사가 통과했다. 기존에 실행 중인 Codex·IDE·터미널은 이전 환경변수를 유지할 수 있으므로 재시작 후 사용한다. 기존 JDK 17은 삭제하지 않았다.

Ollama 로컬 모델: bge-m3, qwen3-vl:4b, embeddinggemma, gemma4:e2b, gemma2. `gemma4:e2b`의 show 결과는 컨텍스트 상한 131072, `bge-m3`는 상한 8192·임베딩 차원 1024다. 이는 메타데이터이며 실제 실행 num_ctx/가용 메모리·성능 보장은 아니다. 변경 가능한 태그 대신 모델 digest를 고정할 예정이다.

## 기술 조합 제안

아래는 1단계에서 검증·고정한 조합과 후속 단계의 후보를 구분한 것이다. 실제 버전은 lockfile과 이미지 digest를 기준으로 재현하며 기능 추가 시 호환성·보안·통합 테스트를 다시 확인한다.

| 영역 | 제안 | 의존성·검증 항목 |
| --- | --- | --- |
| JVM | JDK21 + Spring Boot4.0.10 + Gradle8.14.3 | BOM·lock·SHA256 검증. Security/JWT·STOMP는 후속 단계 |
| Frontend | Node24.22.1 + React19.3.1 + TypeScript7.0.2 + Vite8.4.0 | npm lock·typecheck/build 확인. STOMP는 3단계 |
| AI API | Python3.12 + FastAPI0.162.7 + Pydantic2.14.4 + Uvicorn0.54.0 | prod/dev 해시 잠금 파일, 내부 health/readiness 테스트 |
| AI Agent | LangChain / LangGraph + langchain-ollama + langgraph-checkpoint-redis | 개별 최신 버전을 섞지 않고 공통 core/checkpoint 제약을 resolve하고 저장·재개 API 확인 |
| ORM | Spring Data JPA / SQLAlchemy2.1.9 + psycopg3.3.9 | Runtime DB 계정 분리·Flyway validate, Python memory/rag 범위 |
| PostgreSQL | 17 계열 + pgvector0.8.7 digest 고정 | Flyway baseline·재기동 중복 방지·권한 분리 통과 |
| Redis | Redis8 digest 고정, 인증/큐·checkpoint 별도 인스턴스 | checkpoint의 JSON·Query Engine 명시 로드·동작 검증 |
| Neo4j | 5.26 LTS community 이미지를 digest 고정 | 실제 GraphRAG·Python driver 연결은 10단계 검증 |
| LLM / embedding | 현재 Ollama, gemma4:e2b / bge-m3 우선 평가 | 한국어 튜터 평가 후 확정. embedding 1024 차원은 실제 API 출력으로 재확인 |
| Infra | Docker Desktop + Compose 플러그인, Linux containers | 실제 Compose5.7.1 사용. DB·AI 서비스 호스트 포트 비공개 |

Spring Boot 4.0 공식 요구사항은 Java 17 이상과 Gradle 8.14 이상(8.x) 또는 9.x를 명시한다. Java 21에서 Gradle 실행은 8.5부터 지원된다. 따라서 제안 조합은 문서상 교집합에 있다. Boot 3.5는 대안이지만 새 프로젝트는 4.0을 우선 평가한다. [Boot 요구사항](https://docs.spring.io/spring-boot/4.0/system-requirements.html), [Gradle 호환표](https://docs.gradle.org/current/userguide/compatibility.html).

Vite 공식 가이드의 Node 최소 기준은 20.19+ 또는 22.12+다. 현재 Node24에서 lock 생성과 typecheck/build를 검증했다. [Vite 가이드](https://vite.dev/guide/).

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

로컬 통합 검증은 통과했으며 원격 CI와 develop 병합 결과는 TUT-2에 연결한다. 다음 기능 단계는 회원관리·인증이며, 현재 환경 준비를 그 기능의 완료로 계산하지 않는다.
