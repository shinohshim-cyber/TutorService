# 데이터베이스 설계

상태: 업무 논리 모델 초안. 아래 업무 테이블의 물리 마이그레이션은 구현 단계별로 작성한다. 1단계의 `V1__platform_baseline.sql`은 pgvector 확장, common/identity/chat/aichat/memory/rag schema 및 권한만 구성하며 회원·채팅 테이블은 아직 생성하지 않는다. PostgreSQL은 업무·게시 상태의 단일 원본이다. PK는 서버 생성 UUID, 시간은 timestamptz를 사용한다.

## PostgreSQL 엔티티

| 테이블 | 주요 열 | 제약/인덱스 |
| --- | --- | --- |
| users | id, normalized_email, password_hash, role, status, created_at | email UNIQUE, role STUDENT/TEACHER, 삭제 계정 로그인 금지 |
| teacher_student_relations | id, teacher_id, student_id, status, verified_by, accepted_at | 활성 쌍 partial UNIQUE, 역할 교차 검증 |
| relationship_invitations | id, sender_id, recipient_id, digest, expires_at, consumed_at | digest UNIQUE, 단회 사용, 승인된 선생님만 발급 |
| notifications | id, recipient_id, type, reference_id, read_at, source_event_id | (recipient_id, source_event_id) UNIQUE |
| chat_rooms | id, teacher_id, student_id, relation_id, next_seq, status | 활성 참여자 쌍 UNIQUE |
| chat_members | room_id, user_id, last_read_seq | (room_id,user_id) PK, 읽음 단조 증가 |
| chat_messages | id, room_id, sender_id, client_message_id, request_hash, room_seq, reply_to_id, body, created_at, deleted_at | (room_id,sender_id,client_message_id) UNIQUE, (room_id,room_seq) UNIQUE, (room_id,id) UNIQUE, reply는 동일 room 복합 FK |
| ai_sessions | id, student_id, thread_id, status, generation, next_job_seq, next_message_seq, last_completed_at, deleted_at | thread_id UNIQUE, 상태 OPEN/CLOSED/DELETING/DELETED |
| ai_messages | id, session_id, message_seq, role, body, job_id, client_message_id, request_hash, created_at | (session_id,message_seq) UNIQUE, assistant job_id partial UNIQUE, user (session_id,client_message_id) partial UNIQUE |
| ai_jobs | id, session_id, seq, type, status, generation, context_memory_epoch, attempt, fence, lease_until, deadline_at, result_hash, error_code | (session_id,seq) UNIQUE, (session_id) active lease partial UNIQUE, 질문당 generation job UNIQUE |
| ai_thread_leases | session_id, current_job_id, fence, lease_until | session_id PK, fence 단조 증가 |
| ai_context_heads | session_id, generation, committed_attempt_ns, checkpoint_id, version | session_id PK, 결과 수락 때 CAS 게시 |
| outbox_events | id, aggregate_id, type, payload, status, lease_until, available_at, attempts, published_at | 미발행 (available_at,id) 인덱스, 개인정보 payload 최소화 |
| processed_events | consumer, event_id, processed_at | (consumer,event_id) PK, 재전달 기간 이상 보관 |
| long_term_memories | id, student_id, type, value, confidence, salience, status, revision, memory_epoch, valid_from, expires_at, deleted_at | (student_id,status,expires_at) 인덱스, 낙관적 잠금 revision |
| memory_sources | memory_id, message_id, session_id, generation, source_revision | 출처 추적, 삭제 source 재참조 금지 |
| student_memory_controls | student_id, memory_epoch, updated_at | 수정·삭제 시 epoch 증가, 오래된 extraction commit 거절 |
| memory_suppressions | id, student_id, source_id, fingerprint, reason, created_at | 삭제·정정 사실 재추출 차단용 최소 메타데이터 |
| documents | id, owner_id, scope, status, revision, checksum, storage_ref | scope PUBLIC_APPROVED/STUDENT_PRIVATE, 권한 검증된 자료만 검색 |
| document_grants | document_id, student_id, revoked_at | 문서별 허용 범위, 회수 시 검색 즉시 차단 |
| document_chunks | id, document_id, revision, ordinal, text, embedding, embedding_model, embedding_version | (document_id,revision,ordinal) UNIQUE, vector(1024) 초기 후보 |
| student_concept_observations | id, student_id, concept_id, evidence_id, confidence, revision, expires_at | 그래프 투영의 원본·검증·삭제 추적 |
| cleanup_jobs | id, subject_type, subject_id, generation, status, attempts, next_retry_at | checkpoint/memory/vector/graph 정리 재시도 |
| deletion_tombstones | subject_type, subject_id, invalid_generation, deleted_at | 삭제 차단 원본, 재시도·백업 복원보다 오래 유지 |

방별 순번은 방 행 잠금으로 할당하여 커밋 순서를 고정한다. AI seq도 session 행 잠금으로 할당한다. 단순 시각 정렬·UUID 정렬로 메시지 순서를 추정하지 않는다. 본문 크기 제한, FK, NOT NULL, CHECK 상태 제약을 마이그레이션에서 구체화한다.

## 저장소별 소유권

Spring이 users/chat/ai_jobs/ai_messages/lease/context head/outbox의 유일한 writer다. Python SQLAlchemy는 허용된 기억·RAG 조회와 staging만 담당한다. 기억 게시·정정·삭제는 Spring의 조건부 commit 관문을 거친다. Python DB 계정에는 일반 채팅 테이블 읽기 권한을 주지 않는다. schema owner, Spring, worker, migration 계정을 분리한다.

Flyway가 전체 PostgreSQL 스키마를 관리한다. JPA와 SQLAlchemy는 생성·자동 변경을 하지 않는다. pgvector 확장 설치 권한은 migration 계정에 한정한다. 버전 파일은 적용 후 수정하지 않고 전진 마이그레이션을 추가한다. 로컬 초기화와 운영 데이터 변경을 구분하고 데이터 유실 롤백 대신 복구·보상 계획을 작성한다.

로컬 서버 초기 부트스트랩은 `infra/postgres/001-create-roles.sh`가 runtime 로그인 계정만 생성한다. 업무 schema·확장·객체 권한과 이후 테이블 변경은 backend Flyway migration에서 관리한다. Spring 일반 datasource는 tutor_app, Python은 tutor_ai이며 migration용 관리자 연결과 분리한다.

## Redis

| 용도 | 키/정책 |
| --- | --- |
| Refresh | auth:refresh:{family}:{jti} → digest, userId, status, expiry. 절대 만료 14일 후보, Rotation해도 family 수명은 무한 연장하지 않음 |
| Presence | presence:{user}:{connection} → heartbeat, TTL 60초 후보. 20초 heartbeat, 하나라도 활성일 때 online |
| Queue | tutor:jobs + consumer group, pending reclaim, DLQ, result/notification 이벤트 |
| Checkpoint | 라이브러리 키는 adapter가 관리, thread+generation+attempt namespace 사용. raw 사용자 ID를 키로 받지 않음 |
| Preview | job/attempt별 stream, seq, 제한된 길이·TTL. DB 원본 아님 |

큐·인증 Redis는 AOF/noeviction으로 운영한다. Checkpoint 전용 Redis는 JSON/검색 기능과 TTL/pin을 검증한다. Refresh family Lua script와 checkpoint cleanup에 필요한 최소 ACL만 부여한다.

## Neo4j

Concept(id, curriculumVersion), DocumentChunk(id, revision), Student(id), Observation(id, studentId, evidenceId, revision)를 정의한다. PREREQUISITE_OF, EXPLAINS, HAS_OBSERVATION, ABOUT 관계를 사용한다. Concept 식별자와 Observation 식별자는 UNIQUE constraint를 둔다. 학생별 관찰은 원본 observation 이벤트의 멱등 투영으로 생성하며 일반 채팅에서 추출하지 않는다. 학생 노드 삭제·출처 삭제는 해당 파생 노드/관계까지 정리한다.

## 보관 및 인덱스

장기 기억 90~365일은 MEMORY_POLICY를 따른다. 원본 메시지·계정·자료·백업 보관 기간은 아직 미확정이며 출시 전 결정한다. 임의로 영구 보관하지 않는다. 삭제 요청은 먼저 조회·생성 경로를 차단하고 비동기 물리 정리를 추적한다. 백업은 만료·복원 재삭제 정책을 적용한다.
pgvector HNSW는 데이터 규모와 ACL 필터 후 recall을 측정한 뒤 사용한다. 초기 소규모는 exact cosine 검색으로 시작한다. embedding 모델·차원 변경은 새 열/테이블·재임베딩·전환 마이그레이션으로 처리한다.
