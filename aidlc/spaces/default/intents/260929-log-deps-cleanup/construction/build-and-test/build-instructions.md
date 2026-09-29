# ビルドの手順（260929-log-deps-cleanup）

この Intent で変えたビルドの前提と、この段で使ったコマンドを書く。ビルドの仕組みそのものは前の Intent から変わっていない（入口は `./gradlew verify` の1つ。README の「検査」の節）。

## 前提

- JDK 25（temurin）、Node 24（`frontend/package.json` の `engines.node` は `>=24 <25`）。
- コンテナの実行環境 colima が動いていること（対象DB の結合テストを Testcontainers で起動する）。
- サブモジュール: `vendor/make-you-chic-ui` は `077f5b48ce84cd020ecec2d925836a085f9d9e11`、`vendor/java-mustache-processor` は `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`git submodule update --init` で取る）。
- この Intent で変わった版: spotless 8.10.3、spotbugs の Gradle プラグイン 6.5.12（`gradle/libs.versions.toml`。プラグインは lockfile に載らない）、`@types/node` 26.6.3（`frontend/package.json`・`package-lock.json`）、otel-collector 0.162.0（`compose.yaml`）。

## 環境変数（colima の PC）

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する（`project.md` の学び）。

## ビルドと検査

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- `:backend:cleanTest :backend:cleanIntegrationTest` を付けるのは、テストが UP-TO-DATE で飛ばされず、件数とカバレッジを実測するため（`project.md` の Testing Posture）。
- 依存の入れ直しは lockfile どおり（`cd frontend && npm ci`）。

## ビルドの確かめ

- `BUILD SUCCESSFUL` と、単体・結合テストの SKIPPED が 0 であること（`backend/build/test-results/` の XML）。
- spotbugs のプラグインの版: `./gradlew -q :backend:buildEnvironment | grep spotbugs` で 6.5.12。

## うまくいかないとき

- 対象DB のテストが SKIPPED: 上の環境変数と `colima status` を確かめる。
- frontend のテストが CI だけで落ちる: 画面の操作の直後に表示の設定を同期で確かめていないかを見る（この Intent で直した3件の形。`test-results.md` の 3節）。
