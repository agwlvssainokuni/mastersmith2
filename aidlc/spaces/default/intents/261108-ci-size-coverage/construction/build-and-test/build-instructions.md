# ビルドの手順（Intent 261108-ci-size-coverage）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 前提

- JDK 25・Node 26・git（README のとおり）。アプリのビルドの手順は変えていない。
- 規模を数えるには cloc が要る（手元は `brew install cloc`。2026-11-08 の確かめは 2.10。CI は `apt-get install cloc` で 2.06）。
- colima の PC で全件の `verify` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`caffeinate -dims` で包む（`project.md` の Testing Posture）。

## 2. ビルドと検査

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3
```

- この Intent はアプリのソースと `verify` の段を変えないため、ビルドの成果物（WAR）の中身は変わらない。

## 3. 報告の台本

```bash
node scripts/program-size.mjs                    # 規模（標準出力に Markdown）
node scripts/program-size.mjs --base <commit>    # 前の回との差の列つき
node scripts/ci-summary.mjs --base <commit>      # CI の要約と同じもの（GITHUB_STEP_SUMMARY が無ければ標準出力）
```

- 要約の台本は、全件の `verify` の後の報告（JaCoCo の XML・JUnit の XML・`frontend/coverage/coverage-summary.json`）を読む。報告が無い欄は「報告なし」と警告になり、終了コードは 0。

## 4. うまくいかないとき

- 規模の欄に「cloc が無い」と出る: cloc を入れる。
- 差の列が「—」: 比べる元が渡されていない・0 が並ぶ値・履歴に無いコミット。理由が1行出る。
- `build/program-size-base` が残っているとき: `git worktree prune` の後に消す（台本は毎回消すが、途中で止めたとき用）。
