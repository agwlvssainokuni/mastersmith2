# ビルドの手順（Intent 261004-safety-carryover）

この Intent の変更（`develop` の `b084c07`）をビルドし、検査を流す手順。上流は `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`。

## 前提

- Java 25（Temurin）、Node.js と npm（`frontend/`）、コンテナの実行環境（colima。VM は CPU 4・メモリ 6GiB）。
- 対象DB の結合テストのため、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（`project.md` の学び）。渡さないと対象DB のテストが SKIPPED になる。
- `.env` は開かない。ビルドと検査には要らない。

## 1コマンドの検査

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- フォーマット・リンタ・ライセンスヘッダー・ビルドと型の検査・単体と結合のテスト・フロントエンドのテスト・カバレッジの下限・Gitleaks・SpotBugs の関門・WAR の確かめを含む（`team.md` の Way of Working）。
- OSV-Scanner は入力が変わらないと `UP-TO-DATE` で飛ぶことがある。実際に走らせたいときは `./gradlew osvScan --rerun` を別に流す（Code Generation の Step 16 で確かめた）。

## アプリのイメージ

```bash
./gradlew :backend:bootWar && docker build -t mastersmith:safety-carryover .
```

- `Dockerfile` の `FROM` はダイジェスト付き（`eclipse-temurin:25.0.4_7-jre-noble@sha256:b573af9e…`、arm64・amd64 を含む index）。
- 配備に使うタグ `local` は、負荷の試験のために上書きしない（`perf/README.md` の手順 0）。

## よくあるつまずき

- 対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で落ちる → colima を起動し、上の2つの環境変数を渡して流し直す。
- `FilterExceptionErrorLogIT` が落ちる → 同じ JVM で複数の Tomcat が起動するとエンジンの名前が `Tomcat-3` のように番号付きになる。テストは実際の名前に合わせてロガーのレベルを当てる形にしてある（Code Generation の G2: A）。
