# 보안 및 개인정보 설계

이 문서는 구현 요구사항이며 법률 검토 완료를 의미하지 않는다. 실제 운영 전 대상 연령·기관 역할·처리 근거·원본 및 백업 보관 기간·정보주체 권리 절차를 확정한다. 개발 fixture는 가상 학생 데이터만 사용한다.

## 인증·토큰

2단계 구체 계약과 운영자 선생님 초대 절차는 [AUTH_DESIGN](AUTH_DESIGN.md)을 따른다. JWT/Refresh/회원·관계 상태 검사는 Spring 외부 API에서 공통 적용한다. WS 종료는 3단계 WS 구현 시 연동하며 현재 WS 기능이 있는 것으로 계산하지 않는다.

- 비밀번호는 Spring BCrypt로 저장한다. 초기 cost 12 후보를 하드웨어에서 측정한다. BCrypt의 72바이트 제한을 UTF-8 기준으로 검증하고 조용한 절단을 금지한다. 최소 길이·로그인 rate limit·일반화된 오류를 적용한다.
- Access JWT 초기 수명 15분, refresh family 절대 수명 14일은 후보다. JWT 서명 알고리즘 allowlist, iss/aud/exp/nbf/sub/jti/kid를 검증한다. 키는 환경변수 또는 배포 secret 주입으로 관리하며 코드·이미지·로그에 넣지 않는다.
- Refresh는 256-bit 이상 CSPRNG opaque 값이다. Redis에는 원문 대신 별도 환경 secret을 쓰는 HMAC-SHA256 digest와 family/jti/user/만료/소모 상태만 저장한다.
- Rotation은 Lua 등 원자 연산으로 구 토큰 소비 확인·상태 변경·신 토큰 등록을 함께 처리한다. 이미 소비된 토큰 재사용은 해당 family 전체 폐기와 보안 기록을 남긴다. replay 감지를 위해 사용된 digest도 family 만료까지 보존한다.
- 프런트엔드는 refresh를 Secure/HttpOnly/SameSite cookie, access token을 메모리에 유지한다. cookie 경로는 auth endpoint로 제한한다. refresh/logout은 Origin allowlist와 CSRF token을 함께 검증한다. CORS wildcard+credentials를 금지한다.
- 다중 탭 refresh는 client single-flight로 조정한다. 모호한 동시 재사용을 무조건 성공시키는 grace window를 두지 않는다. 응답 유실 시 재로그인이 필요할 수 있음을 UI에서 처리한다.
- logout/탈퇴/중요 권한 회수는 Redis family/session 상태를 폐기하고 WS를 종료한다. Access JWT의 즉시 회수가 필요한 경로는 session version/deny 상태를 검사한다. Redis 검증 불가 시 refresh·보호 경로는 fail closed다.

## 인가

학생·선생님 역할과 담당 관계를 모두 확인한다. 선생님 가입 요청만으로 실제 역할을 부여하지 않는다. 담당 관계는 검증된 초대·수락 기반이다. 기억·AI 대화·학습 취약점은 학생 본인에게만 공개한다. 선생님 접근은 별도 근거와 명시 권한 기능을 설계하기 전 거절한다.
REST path, WS SEND/SUBSCRIBE, 이벤트 fanout, 내부 job claim, DB 검색, graph 탐색에 같은 범위 검증을 적용한다. 존재하지만 비접근인 ID는 404로 처리해 열거를 줄인다. 삭제·정지·관계 해제 상태를 캐시 무효화와 연결 회수에 반영한다. cursor와 replyTo도 권한 대상이다.

## WebSocket·내부 통신

TLS, handshake Origin allowlist, STOMP CONNECT JWT, frame 최대 크기·SEND rate limit·heartbeat·연결 수 제한을 설정한다. 토큰 query 전달과 client 임의 sender/user destination 지정을 금지한다. 만료 시 연결을 종료한다. broker destination을 명시 allowlist로 제한한다.
FastAPI/Redis/PostgreSQL/Neo4j/Ollama는 외부 공개하지 않는다. 내부 JWT는 외부와 다른 audience/scope/서명 키를 사용한다. Docker private network는 인증을 대신하지 않는다. DB/Redis 최소 권한·서비스별 계정·내부 인증·필요 시 TLS를 적용한다.

## AI 및 RAG 보안

프롬프트·자료·기억은 신뢰할 수 없는 데이터다. prompt injection으로 지침이나 검색 필터를 변경할 수 없게 server scope를 고정한다. 파라미터화 SQL/Cypher와 고정 tool schema만 사용한다. 모델 출력은 HTML sanitize 및 안전한 Markdown 렌더링을 거친다. 임의 파일·shell·네트워크 도구를 제공하지 않는다.
체크포인트 serializer는 임의 코드 실행 가능한 객체 복원을 허용하지 않으며 라이브러리 보안 공지·safe serializer 지원을 확인한다. 사용자 입력을 checkpoint namespace/filter key로 직접 쓰지 않는다. 요약·추출 후보는 schema, 출처, generation, memory_epoch 검증 후만 게시한다.
자료 업로드는 MIME/시그니처·크기·파서 timeout·압축 폭탄·악성 파일·경로 traversal을 검증한다. 저장 경로는 서버 발급 ID이며 이름을 실행 경로로 사용하지 않는다.

## 개인정보·삭제·운영

대화·기억·학생 식별자·token을 로그·trace payload에 기록하지 않는다. 감사 로그에는 행위 종류, 주체의 최소 식별자, 대상 ID, 결과·시각만 기록하며 접근을 제한한다. 기억은 처리 목적에 필요한 정보만 추출하고 민감정보는 제외한다.
2단계에서는 중복 email의 DB 오류 DETAIL에 식별자가 포함되는 것을 확인해 Hibernate JDBC 오류/바인딩 로그를 비활성화하고 PostgreSQL 오류 verbosity=terse, 오류 SQL/파라미터 출력 제한을 적용한다. API는 공통 오류로 응답한다. 실제 중복 가입 후 backend/PostgreSQL 로그에 해당 fixture 식별자·비밀번호·초대 원문·토큰이 없는지 통합 테스트한다. Refresh replay는 비밀정보 없이 보안 이벤트만 기록한다.
삭제는 DB tombstone으로 즉시 차단하고 Redis checkpoint·staging·vector·Neo4j·캐시·출처 요약을 정리한다. worker 재시도·오프라인 결과·백업 복원으로 데이터가 부활하지 않게 ledger를 적용한다. backup 물리 만료와 처리 의무는 운영 전에 확정한다.
의존성은 BOM/lock/digest로 고정하고 SBOM·secret scan·npm audit/pip audit/Java 취약점 점검을 CI에 구성한다. 스캔 결과의 오탐·예외는 근거·기한을 기록한다. 출시 차단 항목은 테스트 실패, 권한 누출, 취소·삭제 후 재생성, 미해결 critical 취약점이다.
