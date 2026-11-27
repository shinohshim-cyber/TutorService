# API 계약 초안

외부 prefix `/api/v1`, 내부 prefix `/internal/v1`. 외부는 Spring Boot만 노출한다. JSON은 camelCase, 시간은 UTC ISO-8601, ID는 UUID다. 계약은 구현 단계에서 OpenAPI 및 STOMP schema로 검증한다.

## 공통 계약

Access JWT는 `Authorization: Bearer`로 전달한다. pagination은 `limit` 기본 30/최대 100과 불투명 cursor를 사용한다. 목록 응답은 `{items,nextCursor,hasMore}`다. 에러는 `{code,message,traceId,details}`이며 내부 스택·계정 존재 정보는 숨긴다. 400 입력 오류, 401 인증, 403 역할 제한, 404 비접근 리소스, 409 상태·동일 키 payload 충돌, 413 과대 입력, 429 제한, 503 의존 서비스 장애를 구분한다.

같은 clientMessageId와 같은 정규화 payload는 기존 결과를 반환한다. 다른 payload는 409다. 권한·취소·삭제 검사는 멱등 재응답에도 수행한다. studentId는 클라이언트 입력으로 소유권을 결정하지 않는다.

## REST

공개 환경 endpoint는 `GET /api/v1/system/status`와 `GET /actuator/health`다. 전자는 `{service:"TutorService",status:"UP",phase:"environment"}`, 후자는 상세 정보 없는 health 상태를 반환한다. 2단계는 아래 auth/users/relationships를 구현한다. 채팅·접속 상태·알림·기억/RAG는 이후 단계다. 내부 AI 서비스는 호스트 포트를 공개하지 않고 `/internal/v1/health/live`, `/internal/v1/health/ready`만 제공한다. ready는 실제 DB 연결 실패 시 비밀정보 없이 503을 반환한다.

| Method / path | 입력·결과 | 권한/조건 |
| --- | --- | --- |
| GET /auth/csrf | → {token,headerName}, XSRF-TOKEN cookie | 변경 요청 전 조회 |
| POST /auth/signup | email,password,name,requestedRole?,teacherInvitationToken? → user, 201 | 학생 기본, 선생님은 검증된 단회 가입 초대 필요 |
| POST /auth/login | email,password → accessToken,expiresIn + refresh cookie | 실패 제한 |
| POST /auth/refresh | HttpOnly refresh cookie → 새 access/refresh | Origin/CSRF 확인, 원자 Rotation |
| POST /auth/logout | 현재 family 폐기 → 204 | cookie 제거, WS 종료 |
| GET/PATCH /users/me | 본인 프로필 → user | 역할 변경 금지 |
| DELETE /users/me | {password} 재인증 → 202 {deletionId} | 회원 익명화·관계 회수·모든 세션 즉시 차단 |
| POST /relationships/invitations | recipientId → invitationId | 검증된 선생님, 임의 담당 지정 금지 |
| GET /relationships/invitations | limit?,cursor? → {items,nextCursor,hasMore} | 본인이 보낸/받은 유효한 대기 초대 |
| POST /relationships/invitations/{id}/accept | 승인 → relation | 초대받은 학생 |
| GET /relationships | limit?,cursor? → {items,nextCursor,hasMore} | 본인 활성 담당 관계 |
| DELETE /relationships/{id} | 관계 해제 → 204 | 당사자, 방 접근 회수 |
| GET /presence?userIds=... | 온라인 여부 | 담당 관계 범위, 최대 50 |
| GET /notifications | cursor 목록 | 수신자 본인 |
| PATCH /notifications/{id}/read | 읽음 → 204 | 본인 |
| POST /chat/rooms | relationId → room, 201/기존 200 | 활성 관계 당사자 |
| GET /chat/rooms | 마지막 메시지·unreadCount | 본인·현재 접근 가능 |
| GET /chat/rooms/{id}/messages | beforeCursor 또는 afterCursor → 목록 | 같은 요청에 두 cursor 불가 |
| POST /chat/rooms/{id}/messages | clientMessageId,body,replyToId? → message | STOMP SEND와 같은 서비스 호출 |
| PATCH /chat/rooms/{id}/read | lastReadSeq → read position | 실제 방 상한 이하, 단조 증가 |
| POST /ai/sessions | title? → sessionId,threadId,status | 학생 본인, thread 서버 발급 |
| GET /ai/sessions | 세션 목록 | 학생 본인 |
| GET /ai/sessions/{id}/messages | cursor → 완성 원본 목록 | 학생 본인 |
| POST /ai/sessions/{id}/messages | clientMessageId,body → 202 {messageId,jobId,status} | OPEN만, 질문/job/outbox 원자 등록 |
| GET /ai/jobs/{id} | status,attempt,answerMessageId?,errorCode? | 세션 소유 학생 |
| POST /ai/jobs/{id}/cancel | → terminal job | 이미 SUCCEEDED이면 기존 상태, CANCELLED이면 멱등 |
| POST /ai/sessions/{id}/close | → CLOSED | 새 질문 금지, 활성 작업은 유지 |
| DELETE /ai/sessions/{id} | → 202 deletionId | tombstone·세대 증가 즉시 적용 |
| GET /ai/memories | type?,cursor → 기억·출처·만료 | 학생 본인 |
| PATCH /ai/memories/{id} | value,revision → 새 revision | 학생 본인, stale revision 409 |
| DELETE /ai/memories/{id} | → 204 | 본인, 재추출 suppression 등록 |
| POST /learning/documents | 자료·scope → 202 ingestionId | 학생은 본인 자료, 공용 승인은 운영 절차 |
| GET/DELETE /learning/documents/{id} | 처리 상태 / 삭제 | 소유자·명시적 권한 |
| GET /learning/analysis | 개념별 관찰·출처·불확실성 | 학생 본인, 선생님 자동 공개 없음 |

개별 AI 메시지 수정·삭제는 초기 API에서 제공하지 않는다. 정정은 새 메시지 또는 기억 수정, 대화 제거는 세션 삭제로 처리한다. 추후 도입 시 해당 메시지에서 파생된 요약·체크포인트·기억·그래프까지 무효화해야 한다.

## 2단계 회원·인증 응답 상세

2단계 응답 상세: user는 `{id,email,name,role}`, login/refresh는 `{accessToken,expiresIn:900,user}`다. 로그인·가입은 공통 오류로 처리하며 이름 PATCH는 `{name}`만 허용한다. 모든 변경 요청은 허용 Origin과 GET /auth/csrf로 받은 `X-XSRF-TOKEN`을 함께 요구한다. TUTOR_REFRESH cookie는 HttpOnly/SameSite=Strict, `/api/v1/auth` 경로이며 서버 family 만료는 최초 발급 후14일로 고정한다. 운영 Secure=true, HTTP loopback Compose만 false다. 관계 초대 응답은 `{id,senderId,recipientId,expiresAt}`, 수락/관계는 `{id,teacherId,studentId,status,acceptedAt}`다. 관계/초대 목록은 UUID 오름차순의 불투명 cursor를 사용한다. 입력 정책·선생님 검증은 [AUTH_DESIGN](AUTH_DESIGN.md)을 따른다.

## STOMP

WebSocket endpoint `/ws`. 브라우저는 STOMP CONNECT header에 access token을 넣는다. URL query에 token을 넣지 않는다. CONNECT에서 JWT 검증, SEND/SUBSCRIBE 시 리소스 권한 재검사, 만료 시 연결 종료·재연결한다. 허용 Origin만 handshake 가능하다.

| 구분 | destination | payload |
| --- | --- | --- |
| SEND | /app/chat/rooms/{id}/messages | REST와 같은 clientMessageId,body,replyToId |
| SEND | /app/chat/rooms/{id}/read | lastReadSeq |
| SUBSCRIBE | /user/queue/events | 본인 메시지·읽음·알림·AI 상태·preview |
| SUBSCRIBE | /user/queue/errors | requestId,code,message |

초기에는 사용자 전용 queue로만 전송하여 임의 방 topic 구독을 막는다. generic `/topic/**`, `/queue/**` 구독과 broker prefix로의 클라이언트 SEND는 거절한다. 사용자 queue도 principal과 현재 리소스 권한을 기준으로 라우팅한다. 구독 후 관계 해제 시 연결/구독을 정리하고 fanout 시 재검사한다.

이벤트 envelope: `{eventId,type,resourceId,occurredAt,version,payload}`. 일반 메시지 payload는 `{id,roomId,roomSeq,senderId,body,replyToId,createdAt}`. AI preview는 `{jobId,attempt,seq,delta,provisional:true}`, final은 `{jobId,messageId,messageSeq,status:"SUCCEEDED"}`다. final 수신 시 DB 메시지를 기준으로 임시 조각을 치환한다. STOMP RECEIPT는 DB 저장 확인으로 취급하지 않는다.

## 내부 서비스·큐 계약

서비스 인증은 별도 짧은 수명 JWT(aud/iss/scope 확인) 또는 mTLS다. 외부 access token을 내부 권한으로 재사용하지 않는다.

| Endpoint / 대상 | 의미 |
| --- | --- |
| POST Spring /internal/v1/ai/jobs/{id}/claim | 정규 작업 입력·generation·contextMemoryEpoch·attempt·fence·lease·서버 소유 student scope 반환. 선행 작업 미종료 409 |
| POST Spring /internal/v1/ai/jobs/{id}/heartbeat | fence/attempt 조건부 lease 갱신, 취소·삭제 신호 반환 |
| POST Spring /internal/v1/ai/jobs/{id}/result | generation,contextMemoryEpoch,attempt,fence,resultHash,body,candidateCheckpoint → 조건부 원자 수락 |
| POST Spring /internal/v1/ai/jobs/{id}/failure | 재시도 가능/불가능 오류, 상태 조건부 전이 |
| POST Spring /internal/v1/ai/memories/commit | extraction source,epoch,revision,출처·범위 검증 후 게시 |
| POST FastAPI /internal/v1/ai/control | jobId,generation,controlId,cancel/delete 신호. DB 상태가 최종 권위 |
| POST FastAPI /internal/v1/ai/cleanup | cleanupId,subject,generation → 멱등 정리 상태 |
| GET FastAPI /internal/v1/health/ready | 설정·모델·저장소 readiness, 비밀정보 제외 |

Streams job event는 `{schemaVersion:1,eventId,jobId}` 중심이며 본문·JWT·기억은 넣지 않는다. duplicate event도 claim에서 상태 확인한다. 동일 result key+hash 재제출은 기존 결과, 다른 hash는 409. 폐기된 fence·취소·삭제 결과는 게시하지 않고 discarded 원인만 기록한다. preview 수신도 job 상태·generation을 확인한다. 내부 payload는 크기 제한·schema 검증·서비스 scope를 적용한다.
