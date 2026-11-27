# 개발 및 Jira 작업 절차

이 프로젝트는 현재 1인 개발 기준으로 운영한다. 변경은 `develop`에서 분기한 작업 브랜치에서 작성하고 PR 자기 점검 후 병합한다. 팀원이 참여하면 별도 확인을 거쳐 필수 승인 수와 Code Owner를 조정한다.

## 작업 위치

- 저장소: [TutorService](https://github.com/shinohshim-cyber/TutorService)
- 기본 브랜치: `develop`
- Jira: [TUT board](https://gad03302.atlassian.net/jira/software/projects/TUT/boards/3)
- WBS 및 일정: [공유 Drive 일정·관리](https://drive.google.com/drive/folders/1_onAMXT710L7tihM0wrbi9fR-02dR-_e)
- 협업 환경 구성 이슈: [TUT-1](https://gad03302.atlassian.net/browse/TUT-1)

기존 `master` 이력은 유지한다. 실제 릴리즈·배포 브랜치 정책은 배포 단계에서 결정한다. GitHub의 작업 문의 경로는 Jira로 안내한다.

## 브랜치·커밋·PR 규칙

Jira에 작업 목적·수용 기준·검증 방법을 기록하고 실제 발급된 `TUT-번호`를 사용한다. WBS 항목은 구현할 범위를 정할 때 Jira 작업으로 나누고 원래 WBS ID를 본문에 연결한다. 담당자·기한은 실제 합의된 값을 기록한다.

작업 브랜치는 `feature/TUT-번호-설명`, `fix/TUT-번호-설명`, `docs/TUT-번호-설명`, `chore/TUT-번호-설명`을 사용한다. 아래는 실제 협업 환경 구성 이슈의 예시다.

```powershell
git clone https://github.com/shinohshim-cyber/TutorService.git
Set-Location TutorService
git switch develop
git pull --ff-only origin develop
git switch -c chore/TUT-1-collaboration
```

기존 작업 폴더에서는 변경 사항을 먼저 확인하고 `git fetch origin` 후 `develop`을 갱신한다. 로컬 `develop`이 아직 없으면 `git switch --track -c develop origin/develop`을 사용한다. 이미 작업 중인 브랜치를 덮어쓰거나 다른 작업자의 변경을 되돌리지 않는다.

커밋 메시지는 `TUT-1 docs: 1인 개발 및 Jira 작업 절차 정리`처럼 이슈 키와 변경 내용을 포함한다. PR 제목은 `[TUT-1] develop 기반 작업 절차 구성`처럼 작성하고 **base를 `develop`**으로 지정한다. 본문에는 Jira 링크, 변경 결과, 실제 검증 결과와 미해결 문제를 적는다. GitHub의 PR 템플릿을 사용한다.

## Jira 상태 흐름

| 상태 | 진입 기준 | 다음 작업 |
| --- | --- | --- |
| 할 일 | 목적·범위·완료 기준을 기록한 작업 | 선행 조건 확인 및 작업 브랜치 생성 |
| 진행 중 | 실제 구현·문서 작성 시작 | 구현, 적절한 테스트, 문서 갱신 |
| 체크 | PR을 열고 변경·검증 근거를 준비 | 본인이 diff·테스트 결과·민감정보·범위 점검 |
| 완료 | 필요한 검증 통과, 미해결 차단 문제 해소, `develop` 병합 확인 | Jira에 최종 결과와 PR 링크 기록 |

체크 중 결함이 발견되면 진행 중으로 되돌려 수정한다. 실패한 테스트를 완료로 취급하지 않는다. 테스트가 적용되지 않는 문서 변경은 실행하지 않은 기능 테스트 대신 실제 문서·링크·diff 검증을 기록한다. PR 생성만으로 완료 처리하지 않는다.

현재 `체크` 상태는 Jira API의 status category에서 `new`로 확인되었다. 보드에서는 위 절차로 사용하되, 진행률 보고에서 검토 작업이 할 일로 집계될 수 있다. category를 진행 중으로 변경할지는 별도 보드 관리 결정으로 둔다.

## develop 보호 설정

2026-11-27 기준 `develop-protection` ruleset은 다음과 같이 적용한다.

| 항목 | 설정 |
| --- | --- |
| 적용 브랜치 | `refs/heads/develop` |
| 병합 방식 | PR 경유 필수 |
| 필수 타인 승인 | **0명** |
| Code Owner 승인 / 마지막 push 타인 승인 | 필수 아님 |
| 리뷰 대화 | 해결 후 병합 |
| 강제 push / 브랜치 삭제 | 차단 |
| 우회 권한 | 등록 없음 |

PR 작성자는 자기 PR을 Approve할 수 없다. 1인 개발에서는 승인 수 0명으로 자기 점검 후 직접 병합한다. 승인 요구를 없애도 PR 경유 및 강제 push·삭제 차단은 유지한다. 존재하지 않는 CI job을 필수 검사로 지정하지 않는다. 기능 CI를 구축한 뒤 실제 성공 이력이 있는 검사 이름을 확인해 적용한다.

병합 전 최신 `develop`과의 충돌을 해결하고 필요한 검증을 다시 수행한다. 이슈 키를 유지하는 Squash merge를 기본 작업 관례로 권장하며 저장소는 merge·squash·rebase 방식을 모두 허용한다. 작업 브랜치 정리는 PR 병합을 확인한 뒤 수행한다.

## 자기 점검 항목

- 변경 범위가 Jira 작업과 일치하고 필요한 설계 문서를 갱신했는가?
- diff에 비밀정보·실제 학생 대화·로컬 산출물·의존성 폴더가 없는가?
- 해당 변경에 필요한 검증을 실제 실행했으며 실패·미실행 결과를 정확히 기록했는가?
- 일반/AI 채팅 분리, DB 커밋 후 전달, 권한 범위, 멱등성·취소·삭제 정책을 지키는가?
- PR base가 develop이고 이슈 키·설명·완료 조건이 올바른가?

## Jira 개발정보 자동 연결

브랜치·커밋·PR에 이슈 키를 포함하는 규칙과 Jira/PR 본문 링크를 적용한다. Jira 개발 패널에 자동 표시하려면 **GitHub for Atlassian 앱**을 해당 사이트·저장소에 연결하고 실제 PR이 표시되는지 확인해야 한다. Codex용 GitHub 연결과 이 앱 연결은 별개다. 자동 연결 상태와 워크플로 자동 전이는 현재 미검증이므로 Jira 상태는 수동으로 관리한다.

근거: [GitHub PR 승인 제약](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/approving-a-pull-request-with-required-reviews), [Jira와 GitHub 개발정보 연결](https://support.atlassian.com/jira-cloud-administration/docs/use-the-github-for-jira-app/).
