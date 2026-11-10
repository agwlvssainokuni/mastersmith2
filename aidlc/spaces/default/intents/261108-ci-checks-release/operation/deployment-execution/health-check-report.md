# 健全性の確かめ（health-check-report）— Intent 261108-ci-checks-release

`deployment-strategy.md` の S1 の結果です。時刻は UTC（2026-11-10）。

## 1. S1（配備の直後、14:54:22〜14:54:32）

| # | 確かめ | 通る条件 | 結果 |
|---|---|---|---|
| 1 | 健全性 | healthy・`/actuator/health` が 200 | 14:54:32 に healthy。コンテナの中から bash の `/dev/tcp` で `/actuator/health` が `HTTP/1.1 200`。メモリの上限 2 GiB（2147483648）・CPU 4 |
| 2 | イメージと WAR | コンテナのイメージ ID が `mastersmith:local` と一致し、戻し先と違う。中の WAR の SHA-256 が順 2 と同じ | `sha256:0d4b6e8866a9…` で一致（戻し先 `4ee5fb45593e` と違う）。`/app/mastersmith.war` の SHA-256 `3805cffb…c9f3` |
| 3 | 移行 | `Migrating schema` 0 件・版 14 のまま | Flyway: 14 件の移行の検証に成功、`Current version of schema "PUBLIC": 14`、`Schema "PUBLIC" is up to date. No migration necessary.`。`Migrating schema` 0 件 |
| 4 | 起動のログ | ERROR 0・救済の WARN 0・WARN は前からのものだけ・メールアドレスそのものが出ない | 39 行すべて JSON。INFO 37・WARN 2・ERROR 0。WARN は `PostProcessorRegistrationDelegate$BeanPostProcessorChecker`（Bean の後処理の案内）1 と `org.flywaydb.core.internal.database.base.Database`（H2 の版の案内）1 で、前の版から出ているもの。救済の WARN 0。初期管理者の INFO にキー `maskedEmail` が 1 件、`.env` の初期管理者のメールアドレスの値がログに 0 件（値は表示していない） |

## 2. 起動し直しの後（順 11、15:00:10〜15:00:19）

| 確かめ | 結果 |
|---|---|
| 健全性 | 15:00:19 に healthy |
| イメージ | `sha256:0d4b6e8866a9…` で一致 |
| 起動のログ | INFO 37・WARN 2（1節と同じ2件）・ERROR 0 |
| 台本 A1 | `mastersmith-app-1` が `mastersmith:local`、見本の PG・Mailpit のダイジェストが `compose.yaml` と一致。食い違い 0 件 |

## 3. 戻しの練習

行っていない。移行と `.env` の変更が無く、戻しはイメージだけのため（`cd-config.md` の 5節、`project.md` の Deployment の 2026-11-07 の決まり）。戻し先 `mastersmith:pre-ci-checks-release`（`4ee5fb45593e`）は、前の配備（261104）で healthy とスモークテストを通った版そのもので、同じ内部DB（版 14）で動いていた。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（S1）・`cd-config.md`
- `docker inspect`・`docker exec`（`/actuator/health`・`sha256sum`）・`docker logs`（ロガーと項目の名前と件数だけを数えた）・`node scripts/ops-readonly-check.mjs`
- Build and Test: `construction/build-and-test/test-results.md`

## Assumptions & Open Questions

None.
