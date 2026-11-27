# 로컬 개발 환경 준비

기준일: 2026-11-27. 작업 추적: [TUT-2](https://gad03302.atlassian.net/browse/TUT-2). 작업 브랜치는 `chore/TUT-2-development-environment`다.

## 확정한 설치 방식

Windows에 Docker Desktop을 설치하고 WSL2 백엔드를 사용한다. 기존 `Ubuntu-24.04` 배포판은 유지한다. 사용자 최종 선택에 따라 Ubuntu 내부에 별도 Docker Engine을 설치하지 않는다.

컨테이너 이미지·볼륨의 디스크는 `D:\Projects\aisource\TutorService\.local-data\docker-desktop`에 두도록 설치 옵션을 지정했다. 이 경로는 Git에서 제외한다. 설치 후 실제 적용 경로를 확인해야 하며 설치 옵션 지정만으로 적용 완료로 간주하지 않는다.

## 실제 확인한 환경

| 항목 | 결과 |
| --- | --- |
| Windows | Windows 10 Pro 22H2, build 19045.6466 |
| Java | Temurin JDK 21.0.13.1+2, 컴파일·실행 검사 통과 |
| WSL | 3.0.5.0, Linux kernel 6.18.58.1-1 |
| Ubuntu | Ubuntu-24.04, WSL version 2, 조회 당시 Stopped |
| CPU | Intel Core i7-4770 3.40GHz |
| 메모리 | 17,097,129,984 bytes, 약 15.9 GiB |
| GPU | Intel HD Graphics 4600, AdapterRAM 1 GiB 보고 |
| 하이퍼바이저 | HypervisorPresent=true |
| CPU 가상화/SLAT CIM 값 | false/false 보고. 기존 WSL2와 하이퍼바이저가 있으므로 이 값만으로 BIOS 가상화 비활성으로 단정하지 않음 |
| C 드라이브 여유 | 조회 당시 약 9.7 GiB |
| D 드라이브 여유 | 조회 당시 약 429.5 GiB |

Windows 일반 실행 계정은 OS 관리자 토큰을 사용하지 않는다. 프로그램 설치 시 별도 Windows 권한 승인이 필요할 수 있다. 자동 재부팅은 수행하지 않는다. 운영체제의 지원·보안 업데이트 상태는 Docker의 숫자 버전 최소 조건과 별도로 점검한다.

## Docker 설치 진행 기록

공식 winget 패키지 `Docker.DockerDesktop` 버전 `4.100.1`을 선택했다. 설치 관리자 SHA256은 `98b24fd6f56313899953ca4d2ed05645d69963b598143b75c6585fa4f7f639b2`이며 winget의 해시 검증을 통과했다.

실행한 설치 옵션:

```powershell
winget install --id Docker.DockerDesktop --exact --version 4.100.1 --source winget --silent --accept-source-agreements --accept-package-agreements --disable-interactivity --override 'install --user --quiet --accept-license --backend=wsl-2 --wsl-default-data-root=D:\Projects\aisource\TutorService\.local-data\docker-desktop'
```

설치 종료 코드 0을 확인했다. 설치 경로는 `C:\Users\human-06\AppData\Local\Programs\DockerDesktop`이며 Docker Desktop 4.100.1, Engine 29.9.3, Compose v5.7.1이 확인되었다. `docker desktop status`는 running, `docker info`의 OSType은 linux다. `docker run --rm hello-world`는 정상 메시지와 종료 코드 0을 반환했다.

실제 가상 디스크 `disk/docker_data.vhdx`, `main/ext4.vhdx`가 지정한 D 드라이브 경로에 생성되었다. 설치 직전의 터미널에는 Docker 자격 증명 도구 경로가 없어 첫 이미지 다운로드가 실패했지만, 작업 프로세스 PATH에 Docker resources/bin을 추가한 뒤 동일 검증에 성공했다. 새 터미널을 열면 설치 시 갱신된 PATH를 사용한다. 이는 Docker 준비 완료이며 전체 1단계 검증과는 구분한다.

## Docker 검증 결과

1. 설치 경로·버전·Engine·Compose 확인 통과
2. WSL2 Linux 엔진 응답 및 running 상태 확인 통과
3. D 드라이브 가상 디스크 생성 확인 통과
4. hello-world 다운로드·컨테이너 실행·자동 제거 확인 통과
5. 기존 Ubuntu를 삭제·재설치·이동하지 않았으며 Ubuntu 내부 Docker Engine 설치 명령은 실행하지 않음

## 프로젝트 실행

저장소 루트에서 새 PowerShell 터미널을 사용한다. Java 21, Node 24.22.1, Python 3.12와 Docker Desktop이 필요하다. 로컬 secret은 아래 스크립트가 생성하며 기존 `.env`는 덮어쓰지 않는다. `.env`와 `.local-data`를 Git·Drive에 업로드하지 않는다.

```powershell
./scripts/Initialize-Environment.ps1
docker compose --env-file .env -f infra/compose.yml up -d --build --wait --wait-timeout 240
python scripts/verify-environment.py
```

Frontend는 `http://127.0.0.1:5173`, Spring API는 `http://127.0.0.1:18080`이다. 이 호스트의 8080 포트 바인딩이 Windows에서 거절되어 API 호스트 포트만 18080으로 변경했다. 컨테이너 내부 Spring 포트는 8080을 유지한다. FastAPI·PostgreSQL·Redis·Neo4j는 호스트 포트를 게시하지 않는다. frontend의 `/api`는 Spring으로만 프록시한다. 회원·일반 채팅·AI 상담은 아직 구현하지 않았으며 현재 화면은 환경 연결 확인용이다.

중지는 아래 명령을 사용한다. 볼륨과 DB 데이터를 제거하는 옵션은 포함하지 않는다.

```powershell
docker compose --env-file .env -f infra/compose.yml down
```

개별 개발 검증:

```powershell
npm --prefix frontend ci
npm --prefix frontend run build
./backend/gradlew.bat --no-daemon -p backend build
python -m venv ai-service/.venv
./ai-service/.venv/Scripts/python.exe -m pip install --require-hashes -r ai-service/requirements-dev.lock
Push-Location ai-service
./.venv/Scripts/python.exe -m pytest -q
Pop-Location
```

Java 테스트 소스는 아직 없으며 backend build의 `test NO-SOURCE`를 단위 테스트 통과로 계산하지 않는다. backend의 실제 DB 초기화·기동·재기동은 환경 통합 검증에서 확인한다.

## 확정한 의존성과 경계

| 영역 | 잠금/검증 |
| --- | --- |
| frontend | React 19.3.1, TypeScript 7.0.2, Vite 8.4.0, npm package-lock.json |
| backend | Java21, Boot4.0.10 BOM, Gradle8.14.3 Wrapper SHA256, gradle.lockfile 및 dependency verification SHA256 |
| AI API | Python3.12, FastAPI0.162.7, Uvicorn0.54.0, SQLAlchemy2.1.9, psycopg3.3.9, Pydantic2.14.4, requirements.lock의 해시 |
| AI 테스트 | pytest9.2.0, Starlette가 요구하는 httpx2로 맞춰 기존 httpx 폐기 경고 해소. requirements-dev.lock 별도 |
| 컨테이너 | infra/images.lock.json과 Dockerfile/Compose의 digest 고정 |
| CI | .github/workflows/environment.yml의 공식 Actions commit SHA 고정 |

Neo4j는 초기 후보 2026 계열 대신 이번 환경에서는 지원되는 5.26 LTS community 이미지를 선택하고 digest로 고정했다. 실제 GraphRAG 및 driver 연결은 10단계 범위다. LangChain/LangGraph/Checkpointer 의존성은 4~6단계에서 추가하며 현재 Agent가 구현된 것으로 보고하지 않는다.

PostgreSQL의 로컬 부트스트랩/migration 계정은 확장 설치를 위한 관리자다. Spring 일반 연결은 비관리자 `tutor_app`, Python 연결은 비관리자 `tutor_ai`를 사용한다. Flyway만 schema DDL을 수행하며 worker는 memory/rag schema에만 접근한다. 운영 배포에서는 migration을 별도 job/계정 경로로 분리한 뒤 실제 학생 데이터를 사용한다.

실제 로컬 검증은 모두 통과했다: Docker hello-world, frontend typecheck/build, backend Windows/Linux 이미지 빌드, Python 테스트 2개(경고 없음), 7개 healthy 서비스, 비공개 포트, API, pgvector·Flyway·DB 역할, checkpoint Redis JSON/Query Engine, Neo4j 인증 및 Spring 재기동 후 migration 중복 방지. 원격 CI·최종 develop 병합 이력은 TUT-2와 해당 PR에서 확인한다.

Redis의 배포 이미지에 모듈 파일이 포함되어 있어도 기본 `redis-server` 명령만으로 JSON/검색이 활성화되지는 않았다. `infra/redis/start.sh`에서 checkpoint 인스턴스에 필요한 rejson·redisearch만 명시적으로 로드하고 실제 JSON.SET/GET과 FT._LIST를 검증했다. 인증·큐 Redis는 core 인스턴스로 분리한다.

해결한 호환 문제: Vite CSS 타입 선언 추가, Linux에서 추가로 읽는 JUnit BOM module 4개의 공식 Maven Central/Plugin Portal 해시 대조·등록, Starlette의 httpx2 테스트 의존성 정합성, Neo4j 환경변수의 설정 오인 제거, Windows 8080 바인딩 제한에 따른 호스트 API 포트 변경. 각 변경 이후 해당 빌드·테스트 또는 전체 통합 검증을 다시 통과했다.

통합 검증 스크립트는 실제 컨테이너의 healthy 상태·비공개 포트·API·pgvector·Flyway·DB 역할·Redis JSON/Query Engine·Neo4j 인증 및 Spring 재기동 후 migration 중복 방지를 확인한다.

설치 기준: [Docker Desktop Windows 공식 설치 문서](https://docs.docker.com/desktop/setup/install/windows-install/).
