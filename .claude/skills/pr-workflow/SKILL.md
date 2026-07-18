---
name: pr-workflow
description: Team-style PR workflow for landing any change to main — branch, commit, push, PR, review, merge. Use whenever changes need to land in the repository (feature, fix, docs, chore), when the user says "PR로 올려", "반영해", or when uncommitted changes exist that should reach main.
---

# PR 기반 팀 워크플로

이 저장소는 1인 프로젝트지만 **팀 프로젝트처럼 운영**한다: main 직접 push 금지,
모든 변경은 브랜치 → PR → CI → 리뷰 → 머지 경로를 거친다.

## 0. 전제 확인

- 현재 브랜치가 main이고 작업 중 변경이 있다면, 커밋 전에 반드시 브랜치를 만든다.
- **원격이 로컬보다 앞서 있을 수 있다** (dependabot 머지 등). 반드시 시작 시:
  ```sh
  git fetch origin
  git merge --ff-only origin/main   # main에서. 실패하면 로컬 커밋이 있다는 뜻 — 브랜치로 옮길 것
  ```

## 1. 브랜치 생성

`<type>/<slug>` 형식, origin/main 기준:

| type | 용도 |
|------|------|
| feature | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 불변 구조 개선 |
| test | 테스트만 추가/수정 |
| docs | 문서 |
| chore | 빌드/설정/저장소 정비 |

```sh
git switch -c feature/cart-stock-deduction origin/main
```

## 2. 구현 + 커밋

- 커밋 메시지: **verb-first 영어, prefix 없음** (예: "Add product stock and enforce it on cart quantities"). `feat:`/`fix:` 금지 (CLAUDE.md 규약).
- 논리 단위로 커밋을 쪼갠다 — 이 프로젝트는 커밋 히스토리로 아키텍처 의사결정을 문서화하는 것이 목표다.
- 소스 변경이 있으면 push 전에 테스트를 돌린다:
  ```sh
  cmd.exe /c gradlew.bat test    # WSL에서. 리눅스/CI에선 ./gradlew test
  ```

## 3. Push

- **WSL 쪽에는 GitHub push 자격증명이 없다** (gh 미설치, HTTPS 헬퍼 없음, SSH 키는 OCI 배포용뿐).
- 순서대로 시도:
  1. GitHub MCP 도구: `create_branch` → `push_files`(논리 커밋 단위로 나눠 호출) → 이후 `git fetch` 후 로컬 브랜치를 원격에 맞춘다.
  2. MCP가 없으면 사용자에게 Windows 쪽(IntelliJ/터미널)에서 push하도록 요청한다.

## 4. PR 생성

- `.github/pull_request_template.md` 구조를 따른다 (요약 / 변경사항 / 검증 / 체크리스트).
- 제목은 커밋과 같은 스타일: verb-first, prefix 없음.
- base는 main. GitHub MCP `create_pull_request` 사용.
- 팀 리뷰 시뮬레이션: 가능하면 `request_copilot_review`로 Copilot 리뷰를 요청한다.

## 5. CI + 리뷰

- CI(`ci.yml`)의 **test + prod-boot-check(MySQL 8.4) 둘 다 통과해야 머지 가능**.
- 머지 전에 `/code-review`(또는 self-review)로 diff를 점검하고, 발견된 문제는 같은 브랜치에 추가 커밋으로 수정한다.

## 6. 머지 + 후처리

- **머지 방식: "Rebase and merge" 우선.** squash는 커밋 히스토리 문서화 목표를 해치므로 브랜치 커밋이 지저분할 때만 사용. merge commit은 dependabot처럼 자동 PR에만.
- 머지 후:
  ```sh
  git switch main && git fetch origin && git merge --ff-only origin/main
  git branch -d <branch>            # 원격 브랜치는 GitHub에서 삭제
  ```
- 개선 백로그 항목을 처리한 경우 `docs/IMPROVEMENTS.md` 처리 현황과 README 진행 상황을 갱신한다 (같은 PR에 포함하는 것이 원칙).

## 함정 (이 저장소에서 실제로 겪은 것)

- **gradlew.bat phantom diff**: EOL 정규화 이전 blob 때문이었고 `.gitattributes` + `git add --renormalize .`로 해결됨. 전체 파일이 바뀐 것처럼 보이는 diff는 먼저 `git diff --ignore-cr-at-eol`로 EOL 노이즈인지 확인할 것.
- `.omc/`, `.claude/settings.local.json` 등 로컬 상태는 커밋하지 않는다 (.gitignore 참조).
