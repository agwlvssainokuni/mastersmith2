# ビルドの手順（Intent 261107-verify-quick-local）

入力: Code Generation の `aidlc/spaces/default/intents/261107-verify-quick-local/construction/code-generation/code-generation-plan.md`（作りの要点と手順）・`unit-test-instructions.md`・`code-summary.md`。

## 前提

- 道具: JDK 25・Node 26・npm・git（`./gradlew` の 0 の段 `checkToolchain` が確かめる）、Gitleaks・OSV-Scanner（README の前提の道具の表）、コンテナの実行環境 colima（対象DB の結合テスト）。
- サブモジュール: `git submodule update --init --recursive`（`vendor/make-you-chic-ui`・`vendor/java-mustache-processor`）。
- colima の PC では、シェルに次を渡す（`docs/target-db.md`）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- 長い実行は `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる（`project.md` の Testing Posture）。

## ビルドと検査の入口

| 入口 | 使う場面 | コマンド |
|---|---|---|
| 全件の `verify` | Build and Test・リリースの前・`packagesJudgedByTotal` のパッケージに手を入れる Bolt・Dependabot の取り込み、CI | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` |
| 並べた全件の `verify` | 手元だけ（CI は使わない） | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=<N>` |
| 統合の前の `verifyQuick` | 上の場面以外の Bolt の統合の前 | `./gradlew verifyQuick`（関わる結合テストを流すときは `-PitTests=<形>`） |
| タスクの一覧の確かめ | 検査の入口を変えたとき | `node scripts/verify-graph-check.mjs` |
| E2E | Intent の最後（この段）とリリースの前 | `./gradlew e2eTest`（前提は `docs/verification.md` の E2E の節） |

成果物の WAR は `backend/build/libs/mastersmith.war`（どの入口でも 9 の段で作られる）。

## ビルドの確かめ

- `verify`・`verifyQuick` の終わりに `BUILD SUCCESSFUL` が出ること。
- `verify` では 7 の段の `jacocoTestCoverageVerification`（バックエンドの全体とパッケージごとの下限）と `frontendCoverage`（画面の下限）が通ること。
- `verifyQuick` では 7 の段に「単体テストだけの参考値」の行が出ること（判定はしない）。

## よくある詰まり

- 対象DB のテストが SKIPPED になる: colima が止まっているか、上の2つの環境変数が渡っていない。`verify` ではパッケージごとの下限で失敗し、`-PitTests` を付けた `verifyQuick` では飛ばさずに失敗する。
- `-PitTests`・`-PitForks` の値の誤り: 設定の段階で理由を示して失敗する（空の値、0、整数でない値、`verify` と `-PitTests` の組み合わせ）。
- テストのタスクが UP-TO-DATE で飛ばされる: 件数やカバレッジを記録するときは `:backend:cleanTest :backend:cleanIntegrationTest` を付ける。
