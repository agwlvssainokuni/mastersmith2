# 健全性の確かめ（health-check-report）— Intent 261110-deps-forks-review

`deployment-strategy.md` の S1 の結果です。時刻は UTC（2026-11-12）。

## 1. S1（配備の直後、20:30:57〜20:31:13）

| # | 確かめ | 通る条件 | 結果 |
|---|---|---|---|
| 1 | 健全性 | healthy・`/actuator/health` が 200 | 20:31:13 に healthy。コンテナの中から bash の `/dev/tcp` で `/actuator/health` が `HTTP/1.1 200`。メモリの上限 2 GiB（2147483648）・CPU 4 |
| 2 | イメージと WAR | コンテナのイメージ ID が `mastersmith:local` と一致し、戻し先と違う。中の WAR の SHA-256 が順 2 と同じ | `sha256:e4eb8ce7de01…` で一致（戻し先 `0d4b6e8866a9` と違う）。`/app/mastersmith.war` の SHA-256 `53abadc0…f3f4`（順 2 と同じ） |
| 3 | 部品の版 | WAR の `WEB-INF/lib` に networknt 3.0.9・Jackson 3.1.8・itu 1.17.0 があり、前の版が無い | 順 2 の WAR（コンテナの中と同じ SHA-256）の `unzip -Z1` で `json-schema-validator-3.0.9.jar`・`jackson-core-3.1.8.jar`・`jackson-databind-3.1.8.jar`・`itu-1.17.0.jar` だけ。前の版（3.0.6・3.1.7・1.14.0）は無い。コンテナには `unzip` が無いため、SHA-256 の一致で同じ WAR と確かめた |
| 4 | 移行 | `Migrating schema` 0 件・版 14 のまま | Flyway: `Successfully validated 14 migrations`、`Schema "PUBLIC" is up to date. No migration necessary.`。`Migrating schema` 0 件 |
| 5 | 起動のログ | ERROR 0・救済の WARN 0・WARN は前からのものだけ・メールアドレスそのものが出ない | 39 行すべて JSON。INFO 37・WARN 2・ERROR 0（適用中の DSL の読み直しの誤りも無い）。WARN は `PostProcessorRegistrationDelegate$BeanPostProcessorChecker`（Bean の後処理の案内）1 と `org.flywaydb.core.internal.database.base.Database`（H2 の版の案内）1 で、前の版から出ているもの。救済の WARN 0（`rescue` を含む行 0）。初期管理者の INFO にキー `maskedEmail` があり、ログの中のメールアドレスの形の文字列は 0 件（値は表示していない） |

## 2. 起動し直しの後（順 11、22:15:25〜22:15:36）

| 確かめ | 結果 |
|---|---|
| 健全性 | 22:15:36 に healthy（止めていたのは約 11 秒） |
| イメージ | `sha256:e4eb8ce7de01…` で一致 |
| 起動のログ | INFO 37・WARN 2（1節と同じ2件）・ERROR 0 |
| 台本 A1 | `mastersmith-app-1` が `mastersmith:local`、見本の PG（18.6）・Mailpit（v1.33.1）のダイジェストが `compose.yaml` と一致。食い違い 0 件 |

## 3. 戻しの練習

行っていない。移行と `.env` の変更が無く、戻しはイメージだけのため（`cd-config.md` の 5節、`project.md` の Deployment の 2026-11-07 の決まり）。戻し先 `mastersmith:pre-deps-forks-review`（`0d4b6e8866a9`）は、前の配備（261108-ci-checks-release）で healthy とスモークテストを通った版そのもので、同じ内部DB（版 14）で動いていた。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（S1）・`cd-config.md`
- `docker inspect`・`docker exec`（`/actuator/health`・`sha256sum`）・`docker logs`（レベル・ロガー・項目の名前と件数だけを数えた）・`unzip -Z1`・`shasum -a 256`・`node scripts/ops-readonly-check.mjs`
- Build and Test: `construction/build-and-test/test-results.md`

## Assumptions & Open Questions

None.
