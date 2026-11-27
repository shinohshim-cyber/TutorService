# 일반 채팅 설계

일반 채팅 모듈은 학생·선생님 1:1 대화만 담당한다. AI 세션·Agent state·추출 작업과 테이블 및 이벤트 유형을 공유하지 않는다.

## 방 생성과 접근

활성 teacher_student_relation을 확인한 당사자만 방을 생성한다. 같은 활성 참여자 쌍의 방 생성 경합은 DB unique 제약으로 수렴한다. GET/POST/읽음·답변 연결/WS SEND/SUBSCRIBE/fanout에서 현재 관계와 방 참여를 확인한다. 관계 해제 시 신규 접근을 차단하고 기존 WS 연결의 권한도 회수한다. 해제 후 과거 대화 열람 허용은 별도 정책이 확정될 때까지 거절한다.

## 메시지 확정과 전달

1. REST와 STOMP는 동일 ChatService를 호출한다. 인증 principal에서 sender를 결정하고 body 크기·공백·replyTo 범위를 검증한다.
2. 방 행 잠금 아래 roomSeq를 할당한다. `(roomId,senderId,clientMessageId)`와 requestHash로 중복·충돌을 판별한다.
3. 메시지와 Outbox 이벤트를 하나의 DB 트랜잭션으로 저장한다. 커밋 실패 시 발행하지 않는다.
4. Relay가 이벤트를 발행한다. 발행 후 장애·중복 이벤트는 eventId/messageId로 소비·클라이언트에서 중복 제거한다.
5. 수신자의 현재 권한을 확인해 user queue로 전달하고, 오프라인이면 notifications 원본을 남긴다. 푸시 연결 여부가 DB 저장 성공 여부에 영향을 주지 않는다.

클라이언트는 전송 중·확정·실패를 구분한다. 재시도 시 같은 clientMessageId를 사용한다. 송신자도 서버가 반환한 정규 메시지로 optimistic UI를 치환한다. 네트워크 단절 후 재전송에도 원본은 1건이어야 한다.

## 조회·읽음·오프라인

메시지 cursor는 roomId와 roomSeq를 묶고 변조·타 방 사용을 검증한다. 이전 조회는 beforeSeq 내림차순으로 가져와 UI 표시 순서로 정렬한다. 누락 복구는 afterSeq 오름차순이다. offset pagination을 사용하지 않는다.
읽음은 `max(current,lastReadSeq)`로 갱신하고 해당 방의 실제 최대 seq보다 큰 값은 거절한다. unread는 읽음 이후 상대방 메시지 수이며 본인 메시지는 제외한다. 메시지 전달 완료를 읽음으로 간주하지 않는다. 다중 탭에서도 읽음 위치가 후퇴하지 않는다.
재접속 시 방 목록·unread·cursor 이후 메시지를 DB에서 동기화한다. 알림은 힌트이며 오프라인 복구의 원본은 PostgreSQL이다. 이벤트 전달 실패와 메시지 저장 실패를 분리해 표시한다.

## 질문 답변 연결

replyToId는 동일 방에 존재하는 메시지만 허용한다. 복합 FK와 서비스 검사를 함께 사용한다. 연결된 메시지가 나중에 정책상 삭제되면 본문 대신 삭제 표시를 반환한다. 다른 학생 방의 ID는 존재 여부를 노출하지 않고 거절한다.

## Presence와 알림

접속별 TTL 60초·heartbeat 20초는 초기 후보다. 여러 연결 중 하나라도 살아 있으면 online이며 비정상 종료는 TTL로 정리한다. 상태 조회는 담당 범위에 한정한다. AI 작업의 계속 실행 여부와 presence를 연계하지 않는다. 알림은 recipientId+eventId로 멱등 저장하며 읽음 처리는 수신자 본인만 가능하다.

## 검증 게이트

커밋 실패 시 무전달, 동일 키 동시 전송 1건, 서로 다른 payload 충돌, 타 방 reply 차단, 관계 해제 직후 WS 차단, 오프라인 cursor 복구, 다중 탭 읽음 단조성을 통과해야 3단계를 완료한다. 일반 채팅이 AI worker·memory·vector로 유출되지 않는 경계 테스트를 포함한다.
