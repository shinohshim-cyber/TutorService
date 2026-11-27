# 단계별 구현 계획 및 현재 상태

2026-11-27 기준: 설계 문서 작성 후 사용자 승인 범위에서 **1단계 구성과 로컬 검증을 완료**했다. Docker Desktop/WSL2, Java21, 세 서비스 골격, 의존성·이미지 잠금, Compose, Flyway, CI를 구성했다. 최종 원격 CI·develop 병합 여부와 결과는 [TUT-2](https://gad03302.atlassian.net/browse/TUT-2)에서 추적한다. 작업 브랜치는 `chore/TUT-2-development-environment`다. 업무 기능은 다음 단계이며 전체를 한 번에 구현하지 않는다.

별도 협업 설정 작업은 [TUT-1](https://gad03302.atlassian.net/browse/TUT-1)에서 관리한다. GitHub 기본 브랜치 develop 및 보호 ruleset을 확인했으며 1인 개발에 맞게 필수 타인 승인 0명, PR 경유·리뷰 대화 해결·강제 push/삭제 차단을 적용했다. Jira 흐름은 `할 일 → 진행 중 → 체크 → 완료`이며 CONTRIBUTING.md와 PR 템플릿으로 작업·검증·병합 절차를 정의한다. 이 협업 설정은 애플리케이션 1단계 완료를 의미하지 않는다.

## 실행 계획

| 단계 | 산출물/구현 | 완료 게이트 | 선행·주요 문제 |
| --- | --- | --- | --- |
| 1 공통 환경 | 구성·로컬 검증 완료: Docker·JDK21·세 서비스 골격·Compose·Flyway·CI·lock/digest | typecheck/build·Python2개·7개 healthy·DB/Redis/Neo4j·재기동 검증 통과 | 최종 원격 CI·병합 이력은 TUT-2 및 PR 확인 |
| 2 회원·인증 | users/roles/초대 관계, BCrypt, JWT, Redis refresh rotation, 회원 API | role 상승 차단, refresh 경합/replay/family 폐기, 탈퇴·인가 테스트 | 1 완료, 선생님 검증·초대 bootstrap 방안 |
| 3 일반 채팅 | rooms/messages/read, WS, presence/알림, Outbox relay, cursor | DB 저장 후 전달, 중복·rollback·오프라인·읽음·WS 인가 통과 | 2, 단일 Spring replica 기준 |
| 4 AI 기본 | 학생 session/thread, LangGraph+Ollama, 원본 이력·내부 API, 최소 학습 상담 | 한국어 가상 질의 성공, scope·모델 오류·timeout 검증 | 3, 생성 모델 평가. 동기 실행은 개발 검증용으로 한정 |
| 5 AI 비동기 | jobs/outbox/Streams worker, seq/lease/fence, 취소·close/delete, preview/final | crash/reclaim/retry/DLQ·순서·취소 경합·세션 종료 저장·삭제 거절 통과 | 4, DB 상태·fence 권위 확립 |
| 6 단기 기억 | Async Redis Saver, namespace adapter, context head, pin/TTL/cleanup | 재시작·재개·24h 기준·활성 pin·stale 쓰기 및 삭제 후 부활 차단 | 5, Redis JSON/Query·라이브러리 쓰기 경로 검증 |
| 7 Context | rolling summary/window/토큰 예산·압축 이벤트 | 4,000 기준·최근10개·초장문·작은 num_ctx·요약 실패 테스트 | 6, 모델별 tokenizer 정확도 |
| 8 장기 기억 | 추출/통합/조회/정정·epoch·suppression·감쇠·만료·삭제 전파 | 유형 TTL·본인 범위·출처·정정 우선·재추출/재생성 차단 | 7, 개인정보·보관 검토 |
| 9 Vector RAG | 수집·정제·chunk·embedding·pgvector·인용·자료 ACL | 임베딩 차원/버전·filtered recall·삭제·검색 실패 및 누출 테스트 | 8, 자료 사용 권한·한국어 fixture |
| 10 GraphRAG | 개념·선행 관계·개인 observation·Neo4j 투영·결합 검색 | traversal scope·projection 지연·삭제 전파·vector 대비 평가 | 9, Cypher template·서버/driver 조합 |
| 11 통합 UI·분석 | 역할별 화면·작업 상태·읽음·기억 관리·근거/취약점 | Playwright 핵심 여정·접근성·재접속·권한 UI | 각 단계의 최소 UI를 최종 통합 |
| 12 통합·보안 | 장애 주입·동시성·권한·성능·백업 복구·SBOM·운영 문서 | TEST_PLAN 필수 항목 통과·미해결 위험 승인/해소 | 1~11, 실제 데이터 운영 전 정책 확정 |

## 설계 결정 초안

| 항목 | 초기 제안 | 재검토 시점 |
| --- | --- | --- |
| Spring baseline | Boot4.0.10 + JDK21 + Gradle8.14.3, BOM·lock·SHA256 | 1단계 Windows/Linux 빌드 검증 |
| 모델 | 현재 설치 gemma4:e2b/bge-m3 우선 평가 | 4·9단계 품질·메모리 측정 |
| 순서 보장 | DB seq + lease + monotonic fence | 5단계 장애·동시성 시험 |
| checkpoint | attempt 격리 + accepted head + write guard | 6단계 library API 검증 |
| 전달 | DB Outbox + at-least-once + unique | 3·5단계 중복·장애 시험 |
| 원본 보관 | 미확정, 장기 기억 90~365일과 별개 | 실제 데이터 운영 전 |
| 역할·관계 bootstrap | 승인된 선생님 초대 + 학생 수락 | 2단계 시작 전 구체화 |

## 1단계 구현 범위 및 검증

JDK21·로컬 Docker Desktop을 준비하고 버전·의존성을 고정했으며 최소 3개 서비스 골격·Compose·Flyway baseline·환경 검증·CI를 구성했다. 로컬 전체 통합 검증이 통과했다. 실행 명령과 제약은 LOCAL_SETUP.md를 따른다. 회원·채팅 기능은 아직 구현하지 않았다. PR의 실제 CI 성공과 develop 병합을 확인한 뒤 Jira를 완료 처리한다.

## 2단계 구현 및 로컬 검증

사용자는 1단계 완료 후 2단계 진행을 확인했다. 브랜치 `feature/TUT-29-member-authentication`, 에픽 TUT-5, WBS02.01~02.08/TUT-29~36에서 진행한다. 학생/검증된 선생님 가입, 프로필·탈퇴, JWT와 Redis HMAC Refresh 원자 Rotation/replay 폐기, CSRF/Origin/속도·입력 제한, 담당 관계 초대·수락·해제, React 인증 화면을 구현했다. 검증 결과는 TEST_PLAN의2단계 기록을 따른다. 원격 CI 통과 및 develop 병합 후 Jira를 완료로 처리한다. 채팅·WS는 포함하지 않는다.

다음은 3단계 일반 채팅 시스템이다. 실제 활성 담당 관계의 인가·DB 커밋 후 Outbox 전달·멱등 메시지·읽음/오프라인·REST/STOMP 권한 검증을 구현하고 테스트한다.

## 단계 보고 양식

1. 구현 내용과 충족 요구사항 ID
2. 추가/변경 파일 목록
3. 실제 실행한 검증 명령, 환경, 통과/실패/미실행 결과
4. 미해결 문제·제약과 사용자 판단이 필요한 항목
5. 다음 단계의 구체적인 작업과 완료 기준

## 현재 검증 기록

- 디렉터리: 초기 항목 0개. 기존 파일 손상 없음.
- 도구: JDK21 컴파일·실행, Gradle8.14.3 Wrapper, Docker Desktop4.100.1/Engine29.9.3/Compose5.7.1 및 hello-world 통과. 기존 Ubuntu24.04/WSL2와 하드웨어 조회 완료. Docker 가상 디스크는 D 드라이브에 생성됐다.
- 의존성: npm lock, Python prod/dev SHA256 lock, Gradle BOM/lock/verification metadata, 이미지8종 digest 및 CI Actions SHA를 고정하고 빌드·기동 검증했다.
- 문서: 필수 14개와 실제 14개 일치, UTF-8·빈 파일·제목·코드 블록·로컬 상대 링크 자동 점검 통과(exit 0). 정책·상태 전이·삭제·scope·잠금 순서의 문서 교차 검토 완료. TEST_PLAN에 검증 범위를 기록했다.
- 로컬 검증: frontend typecheck/build, backend build(단위 테스트 소스 없음), AI 테스트2개, 실제7서비스 health·포트·API·DB역할·pgvector·RedisJSON/검색·Neo4j·Flyway 재기동 중복 방지 통과. 원격 CI 실행 결과는 PR/TUT-2 참조. 회원·채팅·AI Agent 테스트는 해당 기능 미구현으로 미실행이다.
