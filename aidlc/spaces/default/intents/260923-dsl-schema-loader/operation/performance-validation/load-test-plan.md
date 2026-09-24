# 負荷の試験の計画（load-test-plan）

Intent `260923-dsl-schema-loader` の性能の目標のうち、Build and Test から引き継いだ3件（NFR1.10・NFR1.12・U4-POOL）を、負荷をかけて確かめる計画。決定は `performance-validation-questions.md`（Q1〜Q4 すべて A、確認済みの要約）。

## 1. 確かめる目標

| ID | 目標 | 出典 |
|---|---|---|
| NFR1.10 | 今の状態・履歴・破棄・適用の 95% が 1 秒以内（想定の規模の DSL が適用中・プレビュー中、履歴 20 件） | `construction/u4-dsl-management/nfr-requirements/performance-requirements.md` 1節、`construction/u4-dsl-management/nfr-design/performance-design.md` 3節 |
| NFR1.12 | 10MB の DSL を1つ処理するとき、1g のコンテナ（ヒープ 75%）で、ほかの利用（ログイン）と同時でも失敗しない。足りなければコンテナの上限の既定の見直しを依頼者に諮る | 同 `performance-requirements.md` 2節、`performance-design.md` 4節 |
| U4-POOL | 適用などの操作で、内部DB のプール（上限 30）をログインと共有しても尽きない | `construction/u4-dsl-management/nfr-design/logical-components.md` 3節、`construction/build-and-test/build-and-test-summary.md` の B 表 |

前提とする規模は `construction/u4-dsl-management/nfr-requirements/scalability-requirements.md`（アプリ1つ、管理者が数名、DSL の操作は手動でまれ、DSL は最大 10MB、履歴 20 件）と `construction/u4-dsl-management/nfr-design/scalability-design.md`（重い処理は同時に1つ、重なりは 503 `DSL_BUSY`）。軽い API はこの制限を受けない。

## 2. 環境

| 項目 | 値 |
|---|---|
| 置き場 | 配備した環境とは別の使い捨ての環境（compose のプロジェクト `mastersmith-perf`、`docker/perf/compose.yaml`）。仮の署名鍵・仮の管理者・試験用の利用者 10 名。秘密の値はホームの下の一時ディレクトリ（権限 700）に置き、表示せず、終わったら消す（project.md の Testing Posture） |
| 作り方 | `KEEP=1 MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres`（1回ずつの時間の測定を行ったうえで環境を残す。生成した想定の規模の DSL と 10MB の DSL が結果の置き場にできる） |
| イメージ | 配備している `mastersmith:local`（`sha256:1585bd4ef3dd…`）に試験用のタグ `perf-dsl` を付けたもの。配備の版 `7bc1b68` から試験の時点の `bab11e8` まで、`backend`・`frontend`・`Dockerfile`・`docker/perf`・`perf` に変更は無い |
| 対象DB | PostgreSQL の1種類（Q3: A。軽い API は対象DB を使わず、3種類の時間は Build and Test で測り済み） |
| アプリの上限 | CPU 4。メモリは NFR1.10 が 2g、NFR1.12・U4-POOL が 1g と 2g（Q1: A） |
| VM | colima（CPU 4・メモリ 6GiB・ディスク 100GiB） |
| 道具 | k6 `grafana/k6:2.3.0`、台本 `perf/k6/scenarios.js` |
| 配備したアプリ | k6 を流す間は止め（`docker compose stop app`、見本の PostgreSQL は動かしたまま）、終わったら `docker compose start app` で起動し直して healthy を確かめる（Q4: A） |

## 3. 場面

| 順 | 場面 | 条件 | 見るもの | 目標 |
|---|---|---|---|---|
| 1 | `dslCycle`（投入 → 適用 → 投入 → 破棄 → 履歴） | 1人、180 秒（25 周以上。履歴が上限の 20 件に達する）、想定の規模の生成した DSL、上限 2g | `http_req_duration{name:dslApply}`・`{name:dslDiscard}` の 95 パーセンタイル、失敗 | NFR1.10（適用・破棄） |
| 2 | `dslLight`（今の状態 → 履歴） | 同時 5、120 秒、上限 2g。先に想定の規模の DSL を適用中・プレビュー中にし、履歴 20 件 | `{name:dslStatus}`・`{name:dslHistory}` の 95 パーセンタイル、失敗 | NFR1.10（今の状態・履歴） |
| 3 | `dslMixed`（重い側: 10MB の投入 → プレビューの表示。軽い側: 別々の利用者のログイン） | 重い側1人＋ログイン同時 10、120 秒。上限 2g と 1g のそれぞれで、アプリのコンテナを作り直してから流す | 場面ごとの `checks` の率、要求の失敗、OOMKilled、コンテナのメモリ（`memory.peak`・`memory.stat` の `anon`・`memory.events`）、Hikari（使用中・待ち・待ちの時間切れの累計・借りるまでの待ちの最大）、ログの ERROR | NFR1.12・U4-POOL |

- 場面 1・2 の閾値は台本に書いてある（`p(95)<1000`）。場面 3 の閾値は `checks{scenario:dslHeavy}`・`checks{scenario:logins}` の `rate==1`。
- Hikari の値は、使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡して `/actuator/metrics/hikaricp.*` を読む（配備したアプリの公開の範囲は変えない）。コンテナのメモリは cgroup のファイルを読む。どちらも約 2 秒ごとに記録する。
- 試験用の利用者 10 名は、README の手順（`perf/README.md` の手順 2）で内部DB に直接入れる。

## 4. 判定

- `Met`（目標を満たす）・`Not Met`（満たさない）・`Unverified`（確かめられない）のどれかにする。目標は緩めない。
- NFR1.12 は、要件の条件の 1g で判定する。1g で失敗したら `Not Met` とし、要件にある「コンテナの上限の既定の見直し」を承認の場で依頼者に諮る（Q1: A）。2g の結果は参考として並べる。

## 5. 片付け

- `docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v`、一時ディレクトリの削除、試験用のタグ `perf-dsl` の削除。
- 配備したアプリを `docker compose start app` で起動し、healthy とヘルスチェックの応答を確かめる。
- 結果の生データは `build/perf-results/pv-setup/`・`build/perf-results/pv/` に置く（リポジトリの管理の外）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/performance-validation-questions.md`（Q1〜Q4、確認済みの要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/performance-design.md`・`scalability-design.md`・`logical-components.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`・`performance-test-instructions.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/dashboards.md`（DSL の操作の行。この段の試験は使い捨ての環境で行い、手元の監視は使わない）
- `perf/README.md`・`perf/dsl-timing.sh`・`perf/k6/scenarios.js`・`docker/perf/compose.yaml`

## Assumptions & Open Questions

None.
