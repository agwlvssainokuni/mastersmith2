# ビルドの手順（Intent 261110-deps-forks-review）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 前提

- JDK 25（Temurin 25.0.5）・Node 26（26.13.0）・git・gh（README のとおり）。Gradle は Wrapper（9.9.1）を使う。
- colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す（`docs/target-db.md`）。長い実行は `caffeinate -dims` で包み、流した後に `pmset -g log` で眠りが無かったかを確かめる。
- E2E の前に、Mailpit（`docker compose --profile mail up -d mailpit`）と、E2E 用のポートを開けた見本の PostgreSQL（`docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres`）を起動する。流し終えたら、見本の PostgreSQL はポートを外して作り直し、どちらも止める。

## 2. ビルドと検査

```bash
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3
caffeinate -dims ./gradlew e2eTest
```

- この Intent で、検査の入口（`verify`・`verifyQuick`・`e2eTest`）の形は変えていない。
- E2E で起動する WAR の JVM に `-Duser.timezone=Asia/Tokyo` を足した（`frontend/playwright.config.ts`。FR2.5）。タイムゾーンを決める4か所（Dockerfile・compose の `TZ`・テストの JVM・E2E の起動）が同じ値になった。
- CI は `./gradlew verify -PitForks="$IT_FORKS"` を流す。既定の `IT_FORKS_DEFAULT` は 3 のままで、この段の比べの結果で確定した（`.github/workflows/ci.yml`・`docs/verification.md`）。手動の実行は `gh workflow run ci.yml --ref <ブランチ> -f it_forks=<N>`（1〜8）。

## 3. 手元の監視の確かめ（上げたイメージ）

```bash
docker compose --profile monitoring --profile observability up -d lgtm otel-collector
curl -s http://127.0.0.1:3000/api/health
docker compose --profile monitoring --profile observability stop lgtm otel-collector
```

## 4. うまくいかないとき

- 対象DB の結合テストが SKIPPED になる: colima が動いているか、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡したかを確かめる。
- CI の検査の job が長い: 手順ごとの時間（`gh api repos/{owner}/{repo}/actions/runs/<id>/jobs`）を見る。この段では、報告用の手順 `規模を数える道具（cloc）を入れる` の `apt-get` が 21分24秒かかった回があった（`test-results.md` 3.2節）。この手順は `continue-on-error: true` のため、遅れても CI は失敗にならない。
