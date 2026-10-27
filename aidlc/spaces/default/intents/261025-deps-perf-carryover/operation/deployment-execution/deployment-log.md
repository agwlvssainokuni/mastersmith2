# 配備の記録（deployment-log）

Intent 261025-deps-perf-carryover の配備の配備の実施記録です。手順は Deployment Pipeline の `cd-config.md`（流れの順 1〜14）に従いました。

## 1. 概要

| 項目 | 値 |
|---|---|
| 配備先 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`） |
| 配備した版 | `develop` の `9bf6b70`（アプリのコード。以降のコミットは `aidlc/` だけ） |
| WAR の SHA-256 | `ff50498b88a97ac122ac39336ed946908389b1ddcacb14665e8412deead6f9f0`（`spring-boot-4.1.2.jar` を含む） |
| 新しいイメージ | `mastersmith:local` = `sha256:3075065a2653…` |
| 戻し先 | `mastersmith:pre-deps-perf-carryover` = `sha256:4cec01e79026…`（前の Intent の版。WAR は `6426111d…`） |
| 内部DB | 変更なし（Flyway は V13 のまま、`Migrating schema` 0 件） |
| `.env` | 変更なし（`MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` の項目なし。読み取りのプールは WAR の既定 10） |
| 結果 | 成功（healthy、スモークテスト S1〜S9 通過） |

## 2. 実施の順

| 順 | 内容 | 結果 |
|---|---|---|
| 1 | 関門: アプリのソースに未コミットの変更なし、`9bf6b70` との差が空、AC 電源 100% | 通過 |
| 2 | WAR の作り直し（`bootWar`、55 秒） | 成功。画面 assets 92、`TableAccessResponse` 0 件、読み取りのプール既定 `:10}` を確認 |
| 3 | 戻し先のタグ `mastersmith:pre-deps-perf-carryover` を付与 | `sha256:4cec01e79026…` |
| 5〜8 | T0=`2026-10-26T23:45:02Z` にアプリを停止 → 見本の PG を上乗せ無しで作り直し（`127.0.0.1:15432` を閉じる）→ `docker compose --profile targetdb-postgres up -d --build app` | 約 `23:45:21Z` に healthy、`/actuator/health` 200。アプリの停止は約 20 秒 |
| S1〜S8 | スモークテスト（`smoke-test-results.md`） | 通過 |
| S9 | 2026-10-27T15:52:08Z に停止（約 10 秒）、内部DB を複写（`~/.mastersmith-backup/mastersmith-data-202610280052-after-deps-perf-carryover.tgz`、権限 600）、起動し直し 15:52:18Z に healthy | 完了 |

順 4（救済の見込み）は依頼者の答え Q1: A（何もしていない）で、順 9 以降は下記のとおりです。

## 3. 見本の PG の作り直し

- 上乗せ無しで作り直し、確認 `1|t|t|t|f|f|f`、`sales.customers` は 2 行、公開は `5432/tcp` のみ（`127.0.0.1:15432` は閉じた）。
- ボリュームは残るため、データは引き継いだ。

## 4. 差（承認済みの記録との違い）

| 項目 | 差 | 扱い |
|---|---|---|
| Mailpit | 配備の時点では v1.31.5 のまま（`compose.yaml` は v1.32.0） | Deployment Pipeline の Q2: A のとおり、配備では作り直さなかった。承認の場の Request Changes（「Mailpit, otel-lgtm(0.38.0へ)」）を受け、`docker compose --profile mail up -d --no-deps --force-recreate mailpit` で v1.32.0 に作り直した（受けたメールは無く、Messages 0）。`/api/v1/info` が v1.32.0、healthy |
| otel-lgtm | 配備の時点では 0.34.0 のコンテナが停止中（`compose.yaml` は 0.37.0） | 同じ Request Changes を受け、Dependabot #79 と同じ 0.38.0（index ダイジェスト `sha256:75cdf892…`）に作業ブランチ `fix/261025-deps-perf-carryover-b6` で固定先をそろえ（`compose.yaml`・`perf/README.md`・`.github/dependabot.yml` のコメント）、`docker compose --profile monitoring up -d --no-deps --force-recreate lgtm` で作り直した。healthy、Grafana の `/api/health` 200。起動したままにしてある（メモリ使用は約 0.8GiB / 上限 1.5GiB） |
| 配備した WAR との関係 | 上の 2 つは配備したアプリの WAR・イメージに入らない | アプリは作り直しておらず、healthy のまま。`compose.yaml` の otel-lgtm の版の変更は `develop` に B6（`0e3e6ee`、squash）として統合済みで、`main` の取り込みに含める |
| B6 の verify | `./gradlew verify osvScan` が BUILD SUCCESSFUL（3分43秒） | 変更が `compose.yaml`・`perf/README.md`・`.github/dependabot.yml` のコメントだけで、バックエンドのテスト・結合テスト・JaCoCo・`osvScan` は入力が変わらず UP-TO-DATE で飛んだ。バックエンドの確かめは B5 の全件の結果と `9bf6b70` の CI に頼る（`cleanTest` 付きの再実行はしていない） |
| WAR の SHA-256 | 配備した WAR（`ff50498b…`）は負荷の試験の WAR（`80b19c53…`）と違う | B5（vite・react-router の更新）で画面の成果物が違うため。バックエンドのコードは同じ |
| 配備の前の k6 | 流していない | `cd-config.md` 3.3 に記録済み（Build and Test の結果を正とする） |

## 5. 戻し方

- 戻していない。必要なときは `rollback-runbook.md` 2.1 のとおり、`MASTERSMITH_IMAGE_TAG=pre-deps-perf-carryover` と `--no-build` を付けて戻す（戻し先のイメージは残してある）。
- 戻しの練習は行っていない（スキーマと `.env` の変更が無いため。`cd-config.md` の 4節・5節）。

## 6. `main` への取り込み

未実施。この段の承認の後、取り込みの前の CI の試行ごとの確かめと依頼者の承認を経て行い、結果は `develop` だけのコミットで追記する。
