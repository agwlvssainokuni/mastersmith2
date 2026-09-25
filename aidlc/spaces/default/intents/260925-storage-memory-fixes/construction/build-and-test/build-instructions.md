# ビルドの手順（build-instructions）

この Intent（260925-storage-memory-fixes）の変更は、設定（`application.yaml`・`Dockerfile`・`backend/build.gradle.kts`）・テスト・JMX の道具・文書だけで、ビルドの手順そのものは変わらない。

## 1. 前提

- JDK 25（Gradle の toolchain）、Node.js（`frontend/` の `npm ci` は Gradle から呼ぶ）、Docker（colima、VM は CPU 4・メモリ 6GiB）。
- colima の PC では、どのコマンドの前にも次を渡す（渡さないと対象DB のテストが SKIPPED になる。project.md の学び）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 2. ビルドと統合の前の関門

```bash
# 1コマンドの検査（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジの下限・Gitleaks・SpotBugs・OSV-Scanner）
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
# E2E（verify と CI の外。代わりの実行の場はこの段）
./gradlew e2eTest
# 実行可能 WAR とイメージ（配備のタグ local は上書きしない）
./gradlew :backend:bootWar && docker build -t mastersmith:storage-memory-fix .
MASTERSMITH_IMAGE_TAG=storage-memory-fix ./docker/check-container-limits.sh
```

## 3. よくある問題

- 対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する: 上の2つの環境変数を渡していない。
- 件数とカバレッジを報告するときは、テストのタスクが UP-TO-DATE で飛ばされないよう `:backend:cleanTest :backend:cleanIntegrationTest` を付ける。
- `check-container-limits.sh` を `local` のイメージ（最大ヒープ 75% のまま）に流すと、既定の割合の2件が NG になる（新しいイメージで 50% を確かめる）。

## Sources

- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（Testing Contract・D1〜D9・Build and Test に引き継ぐこと）
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
- 生の結果（コミットしない）: `build/perf-results/bt/`
