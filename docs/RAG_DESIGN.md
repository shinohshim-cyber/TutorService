# Vector RAG 및 GraphRAG

9단계 Vector RAG 이후 10단계 GraphRAG를 추가한다. 초기부터 모든 검색 경로의 학생 scope와 자료 출처를 검증한다. 일반 채팅은 수집 대상이 아니다.

## 문서 수집 및 인덱싱

자료는 학생 본인 업로드 또는 권한이 확인된 승인 자료만 수집한다. 공용 게시 권한은 별도 운영 승인으로 부여한다. 임의 URL 다운로드는 초기 제외하며 추후 도입 시 SSRF·저작권·접근 권한 검증을 설계한다.
자료 상태는 UPLOADED → PROCESSING → READY 또는 FAILED/DELETING이다. checksum+revision+embeddingVersion으로 멱등 처리한다. 파일 형식·크기·본문 길이·악성 콘텐츠를 확인하고 파서를 격리한다. 1차 텍스트/PDF를 대상으로 하되 표·수식·스캔 OCR 지원은 품질 평가 후 확대한다.
페이지·제목·절 구조를 보존해 500 token/overlap 80 token을 초기 청킹 후보로 둔다. embedding tokenizer 길이를 확인하고 provider의 자동 절단 대신 명시 분할한다. 청크에는 documentId/revision/page/ordinal/owner/scope/embedding model digest를 남긴다.
bge-m3를 우선 평가하며 메타데이터 차원은 1024다. 실제 API 응답 차원을 확인해 vector(1024)를 확정한다. 모델 변경은 동일 열의 혼합 벡터 대신 새 버전 인덱스를 만들고 재임베딩 완료 후 전환한다. 생성 모델 임베딩 차원을 검색 모델 차원으로 혼동하지 않는다.

## Vector 검색

서버가 claim으로 받은 student scope를 SQL bind parameter로 설정한다. READY·현재 revision·미삭제 문서 중 승인 공용 또는 학생 본인/유효 grant만 검색한다. ANN 상위 결과를 먼저 가져온 후 권한 필터만 하는 방식은 사용하지 않는다. 권한을 쿼리 단계에서 강제하고 반환 직전에도 재검증한다. scope 정보가 없으면 거절한다.
초기는 exact cosine top-k=5, 중복 청크 병합, 출처 포함을 후보로 둔다. 데이터 증가 시 HNSW와 iterative scan의 filtered recall을 측정한다. reranking은 한국어 fixture로 효과·지연을 확인한 뒤 도입한다. 근거가 부족하면 추정과 자료 근거를 구분하고 없는 출처를 만들지 않는다.

## Graph 모델과 구축

Concept 사이 PREREQUISITE_OF는 교과 과정 기반으로 검증한다. DocumentChunk EXPLAINS Concept 관계는 출처와 추출기 버전을 기록한다. 학생별 Observation은 student_concept_observations를 원본으로 하는 멱등 투영이다. Student HAS_OBSERVATION Observation ABOUT Concept 형태로 공용 개념과 개인 근거를 분리한다.
LLM 추출은 후보만 만들고 허용 concept ID·관계 유형·순환·출처를 검증한다. 취약점은 단일 틀린 답으로 확정하지 않고 confidence와 evidence를 표시한다. 개인 관계에는 studentId·revision·expiresAt·source를 포함한다.
Neo4j constraint/index는 버전 스크립트와 schema ledger로 관리한다. PostgreSQL Flyway 소유권과 구분하며 Python 시작 시 무제한 자동 변경하지 않는다.

## GraphRAG 조회 및 결합

1. 질문에서 허용 개념 사전 기반으로 seed concept를 찾는다.
2. 고정 Cypher template와 파라미터로 본인 observation 및 공용 개념을 최대 2 hop/후보 50개 범위에서 탐색한다.
3. 선행 개념·관련 자료 후보를 Vector 결과와 합치고 출처 ID로 중복 제거한다.
4. PostgreSQL에서 문서 ACL·출처 현재 revision·삭제 여부를 다시 검증한다. 오래된 그래프 투영은 답변 근거에서 제외한다.
5. context 예산 내 근거만 전달하고 학생의 취약점은 불확실한 관찰로 표현한다.

LLM이 생성한 임의 Cypher·노드 label·property를 실행하지 않는다. Student 노드의 이웃을 시작으로 다른 학생에게 이동하는 탐색을 허용하지 않는다. Neo4j projection 지연 중에도 DB의 deny/tombstone을 우선한다.

## 삭제·실패·평가

문서 삭제/권한 회수는 DB에서 즉시 차단하고 chunk/vector/graph projection을 cleanup ledger로 정리한다. 학생·세션·기억 삭제 시 관련 observation과 인용 근거도 정리한다. 인덱싱 중 삭제된 revision이 READY로 승격되지 않게 generation/revision 조건을 검사한다.
Neo4j만 실패하면 vector-only로 처리 가능하며 그 사실을 상태에 남긴다. 권한 검증 실패는 일반 검색으로 우회하지 않는다. 모든 근거가 없으면 일반 학습 설명으로 제한하고 자료 인용을 생략한다.
한국어 개념 질문·동음이의어·교과 버전·학습 취약점 fixture로 recall@5, citation precision, 근거 일치도, p95 지연, graph 추가 효과를 측정한다. 타 학생 데이터 검색·출처 위조·삭제 revision 반환은 0건이어야 한다.

설치·버전 검토 근거: [pgvector 공식 저장소](https://github.com/pgvector/pgvector), [Neo4j Python driver 호환성](https://neo4j.com/docs/python-manual/current/install/).
