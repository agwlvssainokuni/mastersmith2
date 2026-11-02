# 結合の試験の手順 — Build and Test（Intent 261101-maintenance-update）

Test Strategy は Minimal で、新しい結合テストは P1 の再現のテスト（`*DeepPageSortIT`、3種類の対象DB）だけ。ほかは既存の結合テストと E2E の全件で、版を上げた後も振る舞いが変わらないことを確かめる。

## 1. 結合テスト（`verify` の中）

```bash
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- 対象DB は版を固定したイメージ（`TargetDbImages`: postgres 18.6・mysql 26.10.0・mariadb 13.0.2、この Intent で組み直しのダイジェストにそろえた）で Testcontainers が起動する。テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。
- P1 だけを流す: `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*DeepPageSortIT'`（前の2つの環境変数付き）。
- 合格: 失敗 0。SKIPPED は DB の種類ごとの前からの `assumeTrue` の 10 件だけ。

## 2. E2E（実際の Chromium、Playwright）

```bash
# 見本の PostgreSQL を E2E の間だけ 127.0.0.1:15432 に開ける（docs/verification.md）
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d --no-deps targetdb-postgres
caffeinate -dims ./gradlew e2eTest
# 流し終えたら上乗せ無しで作り直してポートを閉じる（app は作り直さない）
docker compose --profile targetdb-postgres up -d --no-deps targetdb-postgres
docker inspect mastersmith-targetdb-postgres-1 --format '{{.Config.Image}}'   # compose.yaml の版とダイジェストに一致すること
```

- 前提: Mailpit（`127.0.0.1:8025`）と見本の PG が動いていること。`./gradlew e2eTest` が始める前に確かめる。
- 合格: 全件 passed（unexpected・flaky 0）。axe の検査（050〜080・100・120・130・160〜195）は違反 0 件で通る。axe-core は `frontend/package-lock.json` で1つの版（4.14.0）。
- 報告（`frontend/test-results/e2e-results.json`）を文字列で検索し、`eyJ`・`Authorization`・`refreshToken`・`postgresql://` が 0 件、資格情報の環境変数は名前だけで値が無いことを確かめる。

## Sources

- `docs/verification.md`（1コマンドの検査・E2E・E2E の見本の PostgreSQL）
- `construction/code-generation/code-summary.md`・`unit-test-instructions.md`

## Assumptions & Open Questions

None.
