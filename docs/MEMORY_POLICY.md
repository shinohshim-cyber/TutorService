# AI 기억 정책

일반 채팅 원본에는 이 정책을 적용하지 않는다. AI 원본 메시지, 단기 state, 요약, 장기 기억, 검색 임베딩, 그래프 관찰은 별도 수명과 출처를 갖는다. 원본 보관 정책을 장기 기억 보관 기간으로 대신하지 않는다.

## 단기 기억 수명

- thread의 최근 대화·요약·실행 상태는 Redis Checkpointer에 저장한다.
- 활성 QUEUED/RUNNING/RETRY_WAIT 작업이 있는 thread는 필요한 head와 checkpoint 관련 키를 pin하여 만료시키지 않는다. 무한 pin은 job deadline·stale lease reconciler로 해제한다.
- 활성 작업이 모두 terminal이 된 최종 처리 완료 시점에 `expiresAt = lastCompletedAt + 24h`를 설정한다. 실패·취소 처리도 종료 시점으로 기록하되 취소된 후보 state는 게시하지 않는다.
- 읽기·UI 재접속은 만료 시점을 연장하지 않는다. 새 작업 수락은 기존 head가 유효할 때 pin하고, head가 이미 만료되면 최소 context에서 시작한다.
- Saver 기본 TTL은 분 단위로 24h=1440분이지만, 매 checkpoint 쓰기/읽기 기준 TTL을 완료 기준 정책으로 그대로 사용할 수 없다. adapter에서 관련 키 전체 pin/expire를 관리한다.
- 만료 직전 enqueue·완료·취소 경합은 thread lifecycle 잠금과 Redis 원자 연산으로 처리한다. 새 active job의 pin을 이전 완료 callback이 덮어쓰지 못하도록 generation/fence를 검사한다.

근거: [Redis Checkpointer TTL](https://redis-developer.github.io/langgraph-redis/v0.6.0/user_guide/ttl.html). 관련 checkpoint·write·pointer 키의 처리 범위는 고정된 라이브러리 버전으로 테스트한다.

## 장기 기억 유형

| 유형 | 예시 | 초기 최대 보관 | 갱신 |
| --- | --- | --- | --- |
| 학습 목표 | 이번 학기 함수 이해 | 180일 | 학생의 명시적 목표 변경 우선 |
| 학습 선호 | 짧은 예제 선호 | 365일 | 본인 수정 우선 |
| 취약 개념 관찰 | 분수 통분 오류 반복 | 90일 | 근거·신뢰도·관찰 시점 함께 저장 |
| 학습 진도/성취 관찰 | 일차방정식 연습 완료 | 180일 | 검증된 최근 근거로 통합 |

민감정보·연락처·비밀번호·주민식별정보·일반 채팅·근거 없는 성격/건강 추론은 추출 대상에서 제외한다. 기억에는 type, confidence, source IDs, created/updated/lastAccessed, expiresAt, revision을 기록한다. 조회만으로 보관 기한을 늘리지 않는다. 검증된 새 근거로 갱신할 때 해당 유형 최대 기간을 다시 적용하고 변경 이력을 남긴다.

## 추출·통합·조회·정정

압축 또는 session close에서 source revision 범위를 가진 멱등 extraction job을 등록한다. close 시 진행 중 답변이 있으면 그 답변 종료 후 최종 추출을 실행한다. `(student,session,generation,sourceRange,extractorVersion)`로 중복 예약을 막는다. 추출 실패는 재시도하며 사용자 답변 저장을 되돌리지 않는다.
모델은 후보를 staging에 생성한다. 게시 관문은 학생·출처 존재·삭제 tombstone·session generation·student memory_epoch를 검증한다. 유사 후보는 유형+정규화 fingerprint로 통합하며 모순을 조용히 덮어쓰지 않는다. 학생의 명시적 정정이 자동 추론보다 우선한다.
조회는 본인 범위의 active·미만료·유효 출처만 반환한다. relevance/recency/confidence/salience를 평가하고 상위 소수만 context에 넣는다. `decayedScore = salience * exp(-lambda * ageDays)` 형태로 유형별 감쇠를 평가한다. 낮은 점수는 검색에서 제외할 수 있지만 자동 영구 삭제 기준과 분리한다. 만료·명시 삭제는 즉시 검색 차단 후 물리 정리한다.
PATCH/DELETE는 memory_epoch와 revision을 증가시켜 오래된 추출·통합 결과를 거절한다. 삭제/정정 source와 fingerprint는 suppression으로 남겨 같은 원문에서 기억이 되살아나는 것을 막는다. 사용자 수정 값은 다시 읽은 자동 추론으로 덮어쓰지 않는다.

## 취소·삭제의 파생 데이터 처리

| 사건 | 즉시 효과 | 후속 처리 |
| --- | --- | --- |
| 작업 취소 | 해당 attempt 게시·checkpoint head 승격 금지 | 임시 namespace·preview·staging 삭제. 이전 정상 기억은 유지 |
| 세션 종료 | 새 질문 차단 | 활성 답변 저장 후 추출·완료 TTL |
| 세션 삭제 | generation 증가·tombstone·조회/게시 차단 | 원본, checkpoint, 요약, 출처 기억, 임베딩, 그래프 관찰 정리 |
| 기억 수정/삭제 | epoch 증가·검색 차단/수정 | 오래된 추출 거절, 요약 및 검색 캐시 무효화 |
| 계정 탈퇴 | 인증 폐기·모든 작업 차단 | 모든 저장소 정리·백업 복원 시 재삭제 ledger 적용 |

복수 출처 기억은 삭제된 출처를 제거한 후 남은 근거로 재평가한다. 근거가 불충분하면 비활성화한다. 삭제 내용이 이미 들어간 다른 세션 요약도 lineage를 통해 무효화하고 허용 원본만으로 재작성한다. 기존 checkpoint에 잘못된 기억이 남지 않도록 head와 context cache도 폐기한다.

## 운영 게이트

청소 작업은 PostgreSQL cleanup ledger에 단계별 완료를 기록한다. Redis/Neo4j 장애에도 논리 차단은 유지하고 재시도한다. 삭제 API의 202는 삭제 접수이며 모든 매체의 물리 삭제 완료를 뜻하지 않는다. 물리 정리 목표는 정상 서비스에서 24h 이내 후보로 두고 백업 보관·법적 의무·대상 연령에 맞춰 출시 전 확정한다. 보관·권한 정책 미확정 상태에서는 실제 학생 데이터로 출시하지 않는다.
