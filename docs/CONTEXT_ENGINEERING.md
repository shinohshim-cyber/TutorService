# Context Engineering

초기 정책은 누적 대화 토큰 약 4,000에서 압축, 최근 메시지 최대 10개 유지다. 모델 context 한도·한국어 tokenization·실제 실행 num_ctx가 우선이며 4,000을 모든 모델의 안전 한도로 가정하지 않는다.

## 구성 순서와 예산

System/안전 규칙 → 학생의 명시적 설정 → 출처가 표시된 장기 기억 → rolling summary → 권한 검증된 검색 근거 → 최근 대화/현재 질문으로 구성한다. 검색 문서와 기억은 instruction이 아닌 인용 데이터로 구획한다. 신뢰 수준과 출처를 유지하고 일반 채팅은 포함하지 않는다.

`inputBudget = min(modelContextLimit, runtimeNumCtx) - outputReserve - safetyMargin`.
초기 출력 reserve 1,024, safety margin 512를 후보로 두되 thinking/tool schema 토큰도 포함한다. 시스템 지침과 현재 질문 예산을 먼저 확보한 뒤 요약·검색·기억을 배분한다. 모델 설정이 작으면 기준 4,000 도달 전에도 압축한다.
가능하면 모델별 tokenizer로 계산한다. 정확한 tokenizer를 사용할 수 없으면 보수적 estimator와 실행 usage를 비교·보정한다. 한글 문자열 길이/4 같은 고정식만으로 안전성을 보장하지 않는다. 입력이 단독으로 한도를 초과하면 분할 요청 또는 413을 반환한다.

## 압축 및 rolling summary

1. 이전 승인 summary와 아직 요약하지 않은 원본 범위를 선택한다.
2. 학습 목표, 설명한 개념, 학생의 오개념, 해결/미해결 질문, 명시적 정정을 구조화해 요약한다.
3. source message range, generation, summarizerVersion, token count를 저장한다. 새 요약은 이전 요약의 출처 lineage를 보존한다.
4. 요약 품질·예산을 검증하고 새 candidate state를 만든다. 원본 메시지는 압축으로 삭제하지 않는다.
5. 결과 수락된 checkpoint만 다음 작업에서 읽는다. 압축 이벤트는 장기 기억 추출을 예약하되 요약 그 자체를 확정 기억으로 취급하지 않는다.

최근 10개는 최대 유지 목표다. tool call/result가 도입되면 한 쌍을 자르지 않고 메시지 단위를 묶는다. 예산 부족 시 검색 청크 수·장기 기억 수를 먼저 줄이고, 오래된 최근 메시지를 추가 요약한다. 현재 질문과 시스템 제약은 유지한다. 모두 불가능하면 명시 오류로 종료하며 provider의 묵시적 truncate에 의존하지 않는다.

## Sliding window 및 복구

window는 현재 질문 포함 최근 최대 10개 메시지다. summary와 window의 중복 범위를 추적해 반복 주입을 막는다. summary 실패 시 제한된 window로 예산을 맞출 수 있으면 진행하고, 불가능하면 재시도 가능한 오류로 종료한다.
checkpoint TTL 만료는 단기 기억 망각이다. 모든 과거 원본을 자동으로 재주입하지 않는다. 학생이 요청한 과거 맥락은 별도 권한 검증된 제한 조회로 참조할 수 있으며 검색 출처를 표시한다. 새 state 초기화 시 장기 기억 조회에도 수정·삭제 epoch를 적용한다.

## 정정·삭제 일관성

요약에 인용된 기억 revision·source 메시지 목록을 보존한다. 기억 정정/삭제와 세션 삭제 시 해당 summary/head를 무효화한다. 새 작업은 삭제된 내용이 들어간 checkpoint를 재사용하지 않는다. 이미 실행 중인 작업은 memory_epoch가 바뀌면 결과·파생 게시 전에 재검증하고 필요한 경우 재시도한다.

## 평가

긴 한국어 대화, 초장문 한 메시지, 질문 정정, 상충하는 선호, 10개 경계, tool pair, RAG 과다 입력, 작은 num_ctx, thinking 모델을 테스트한다. 토큰 상한 초과 0건, 삭제 내용 재등장 0건을 필수로 두고 요약 충실도·미해결 질문 보존·답변 품질·압축 비용을 fixture로 비교한다. 원본·요약 전문을 관측 로그에 수집하지 않는다.
