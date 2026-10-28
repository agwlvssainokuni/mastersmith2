# ビルドの手順（Intent 261027-perf-targets-review）

## 前提

- Java 25、Node.js（`frontend/` と `vendor/make-you-chic-ui` の lockfile どおり）、Docker（colima）が動いていること。
- colima の PC では次をシェルに渡す（渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## ビルドと全検査（統合の前の関門）

```bash
# テストの件数・カバレッジを報告するときは cleanTest を付けて実測する（UP-TO-DATE で飛ばされるため）
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジの下限（行 80%・分岐 70%）・秘密情報の検出・静的解析・依存関係の脆弱性検査（OSV-Scanner）が通ること。
- 流した後に `pmset -g log | grep -E ' (Sleep|DarkWake) '` で眠りが無かったかを確かめる。入っていたら結果を使わずに流し直す。

## この Intent の追加の確かめ

```bash
bash perf/k6/thresholds-test.sh          # 緩めた p95 の線（k6 inspect。Docker が必要）
bash perf/dsl-mixed-memory-test.sh       # memory.peak の判定（0.95 と前の線 0.9 の併記）
bash perf/lib/perf-db-url-test.sh        # 既存
```

## E2E（Intent の最後に1回。`team.md`）

```bash
cd frontend && npm ci && cd ..           # lockfile どおり（スクリプトは動かさない）
caffeinate -dims ./gradlew e2eTest       # 見本の対象DB（PostgreSQL）がコンテナで動いていること
```

## つまずきやすい点

- 配備したアプリを止めた状態（測り直しの間）は、見本の PostgreSQL と Mailpit が動いていること。止まっていると `e2eTest` が始める前に失敗して手順を示す。
- 測り直し（性能）と E2E・`verify` は VM のメモリを取り合うため、同時に流さない。
