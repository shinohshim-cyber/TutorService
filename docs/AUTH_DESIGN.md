# 2단계 회원·인증 구현 계약

TUT-5, WBS 02.01~02.08. 학생 가입, 검증된 선생님 가입, 로그인/JWT/refresh, 프로필·탈퇴, 담당 학생 초대·수락·해제와 최소 화면을 구현한다. 일반/AI 채팅과 WS는 3단계 이후다.

## 회원과 선생님 검증

email은 trim/소문자로 정규화하고 DB unique로 중복 경합을 막는다. 비밀번호는 12자 이상, UTF-8 72바이트 이하이며 BCrypt cost12로 저장한다. 이름은 1~80자이며 제어문자를 거절한다. 일반 가입은 STUDENT다. TEACHER는 운영자가 외부 검증 후 `scripts/create-teacher-invitation.py`로 발급한 email 귀속·24시간 유효·단회 토큰을 요구한다. 운영자 HTTP 승격 API나 기본 선생님 계정은 없다. 초대 원문은 운영자가 전달할 때만 확인하고 DB에는 SHA256 digest와 검증자 기록을 저장한다.

프로필 PATCH는 name만 허용한다. role 등 알 수 없는 JSON 속성도 400으로 거절한다. 탈퇴는 비밀번호 재확인 후 DB status=DELETED, version 증가, 식별정보/비밀번호 제거, 담당 관계 해제 및 deletionId 기록을 한 트랜잭션으로 처리한다. 아직 대화 데이터가 없으므로 후속 삭제 worker는 이 단계에서 만들지 않는다. 앞으로 대화/기억 추가 시 삭제 ledger·정리 작업과 연동한다.

## JWT와 세션

HS256 전용, 환경 secret 32바이트 이상, kid/iss/aud/sub/jti/iat/nbf/exp/sid/version을 검증한다. Access15분, family 절대14일이다. 현재 키로 서명하고 명시한 이전 kid/키도 검증하는 교체 경로를 둔다. 과거 키는 최대 access 수명 이후 제거한다. 모든 보호 요청은 DB 회원 상태/버전과 Redis family를 확인한다. Redis 장애는 503으로 거절한다. family 폐기로 로그아웃과 replay 이후 JWT도 즉시 거절한다.

Refresh는 서버 발급 family UUID와 CSPRNG256비트로 구성한다. Redis family hash 하나에 user/version/절대 만료와 HMAC-SHA256 digest별 ACTIVE/USED 상태만 보관한다. Lua가 구 digest 소비와 새 digest 등록을 원자 처리한다. USED 재사용은 family 전체를 폐기한다. TTL은 절대 만료를 연장하지 않는다. 알 수 없는 digest는 유효 토큰으로 간주하지 않는다. 원문·비밀번호·JWT는 로그에 남기지 않는다.

## 브라우저와 요청 보호

Refresh는 HttpOnly/SameSite=Strict, 경로 `/api/v1/auth` cookie다. 운영은 Secure=true가 기본이며 HTTP loopback 개발 Compose에서만 false로 명시한다. Access는 메모리에만 유지한다. CSRF는 Spring CookieCsrfTokenRepository와 `GET /api/v1/auth/csrf` + `X-XSRF-TOKEN`으로 검증한다. 모든 변경 요청은 허용된 Origin도 요구한다. CORS는 지정한 로컬 Origin만 허용한다. 응답은 no-store이며 에러는 code/message/traceId/details 형식이다.

클라이언트 refresh는 탭 내부 single-flight와 Web Locks로 탭 간 직렬화한다. 성공한 access는 BroadcastChannel로 같은 Origin의 탭에 전달하고 logout도 공유한다. 모호한 중복 소비를 성공시키는 grace는 없다. 응답 유실/replay 감지 시 다시 로그인한다. 로그인은 Redis 기반 IP/정규화 email HMAC key 고정창 제한을 적용한다. X-Forwarded-For는 신뢰하지 않는다. 단일 gateway 뒤에서는 IP 한도를 공유하며 운영 reverse proxy 신뢰 설정은 별도 검토한다.

## 담당 관계

검증된 TEACHER만 활성 STUDENT UUID를 대상으로 24시간 단회 초대를 생성한다. 수락은 해당 학생만 가능하고 초대 행 잠금과 DB partial unique로 중복 관계를 방지한다. 당사자만 목록/해제를 사용할 수 있다. 다른 사용자의 초대/관계는 404로 숨긴다. 모든 사용자의 활성 여부를 다시 검사하며 탈퇴는 관계를 해제한다. WS 종료는 WS 구현 시 이 상태 검사에 연결한다.

회원은 JPA Entity/Repository를 사용한다. 원자 초대 소비·삭제·관계 SQL은 전용 Repository의 파라미터화 JDBC로 실행하며 같은 Spring 트랜잭션/DB 연결에 참여한다. Service가 정책과 트랜잭션을 소유하고 Controller는 DTO만 반환한다. 목록은 기본30/최대100, UUID 오름차순 cursor와 `{items,nextCursor,hasMore}` 계약을 사용하며 커서의 ID 자체를 접근 권한으로 신뢰하지 않는다.

## 검증

JUnit으로 비밀번호 경계·JWT 서명/필수 claim/키 교체를 검사한다. 실제 Compose PostgreSQL/Redis에서 HTTP 가입·역할 위조·단회 초대·프로필·refresh 경합/replay·logout·탈퇴·CSRF/Origin·속도 제한을 검증한다. Redis 정지/재시작은 finally 복구하고 보호/refresh 경로의 fail-closed를 확인한다. 테스트는 매번 UUID 가상 계정을 만들고 해당 fixture만 정리한다. CI 필수 build-and-integration에 같은 검증을 포함한다.
