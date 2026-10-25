# 設定のずれ（drift-report）

配備先が開発者の PC 上のコンテナのため、AWS Config のずれの検出を、`docker inspect`・`docker compose config`（値は出さない）・`.env` の更新の時刻と、記録した値との1つずつの比べに読み替えました（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。Q2: A のとおり、読み取りだけで確かめ、配備したアプリは止めず、要求も送っていません（`/actuator/health` を除く）。内部DB の状態は配備の後の複写の数え（`operation/deployment-execution/deployment-log.md` 5節）を正とします。

- 比べた記録: `operation/deployment-execution/deployment-log.md`・`operation/environment-provisioning/environment-inventory.md`（「配備の後」の列）・`operation/deployment-pipeline/cd-config.md`。
- 確かめた時刻: 2026-10-25 10:10 ごろ UTC（片付けの前）。片付けの後の状態は3節です。

## 1. 比べた結果

### 1.1 アプリのコンテナ

| 項目 | 記録 | 今 | 判定 |
|---|---|---|---|
| イメージ | `mastersmith:local` = `4cec01e79026`（`deployment-log.md` 1節） | 同じ | 一致 |
| WAR の SHA-256（イメージの中） | `6426111d…bfe041f4` | 同じ（`docker run --rm --entrypoint sha256sum mastersmith:local /app/mastersmith.war`） | 一致 |
| 上限 | メモリ 2GiB・CPU 4、`restart: no` | 同じ（`HostConfig.Memory` 2,147,483,648、`NanoCpus` 4,000,000,000） | 一致 |
| ボリューム | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 同じ | 一致 |
| ヘルスチェック | compose の bash の `/dev/tcp` で `GET /actuator/health` の 200 | 同じ。今 healthy | 一致 |
| 環境変数（名前だけ） | 配備の後 19 項目（`environment-inventory.md` 3節。書き込みの2項目を含む） | `.env` 由来の 18 項目と `TZ` の 19 項目（イメージの6項目を除く）。`MASTERSMITH_JAVA_OPTIONS` は無い | 一致 |
| 起動 | 配備は 2026-10-20 23:49Z。Performance Validation の後に起こし直す | 2026-10-25T09:59:21Z に `docker compose start app` で起こし直した。再起動 0 回、OOMKilled なし | 記録どおり（`operation/performance-validation/test-results.md` 1節） |

### 1.2 起動のログ（件数とキーの名前だけ）

- 起こし直した後のログ: INFO 37・WARN 2・ERROR 0。
- コンテナの作成（配備の入れ替え）のときのログに、Flyway の「Migrating schema」「Successfully applied 1 migration」が1回ずつあります（V13、`deployment-log.md` 1節と合う）。起こし直した後は当てる移行はありません。

### 1.3 設定のファイル

| ファイル | 記録 | 今 | 判定 |
|---|---|---|---|
| `.env` | Deployment Execution で書き込みの2項目を足した | 更新の時刻 2026-10-21 08:48 JST（配備の時刻と合う）。その後の更新なし | 一致 |
| `.env.targetdb` | 見本の書き込みのパスワードを足した | 同じ時刻。その後の更新なし | 一致 |
| `compose.yaml`・`Dockerfile` | 配備に効く差は無い（`cd-config.md` 1節） | 配備の後の `aidlc/` の外の変更は、ダッシュボード・警報の決まり・テストの3ファイルだけ（`git diff --stat c099a2f develop`） | 一致（警報の決まりの追加は Observability Setup の記録どおり） |
| `docker compose config` | アプリ 2g・CPU 4、見本の PG 512m（profile `targetdb-postgres`） | 同じ | 一致 |

### 1.4 ほかのコンテナ・戻し先・バックアップ・リポジトリ

| 項目 | 記録 | 今 | 判定 |
|---|---|---|---|
| 見本の PG・Mailpit | `postgres:18.6`・`axllent/mailpit:v1.31.5`（ダイジェスト固定） | 同じ。Up | 一致 |
| 手元の監視のコンテナ | `environment-inventory.md` 2節は `grafana/otel-lgtm:0.34.0`（Exited） | 同じ 0.34.0 のまま。**`compose.yaml` の固定先は `0.36.0`**（前の Intent 261015-deps-ui-carryover で上げた後に、コンテナを作り直していない） | **ずれ**（記録と今は一致するが、`compose.yaml` と違う）。次に起動するときに `docker compose --profile monitoring up -d lgtm` で作り直せば揃う。直していない |
| 戻し先 | `mastersmith:pre-business-data-views` = `f103aee1702a` | 同じ | 一致 |
| バックアップ | 入れ替えの前と配備の後の複写（600） | 同じ。`~/.mastersmith-env-before-writer` は Incident Response で消した（記録どおり） | 一致。ほかに9月の `.env` の複写が残っていた（3節で消した） |
| `main` と配備した版 | Deployment Execution で `main` を `c099a2f` へ進めた（`deployment-log.md` 7節） | `main` の固定先は make-you-chic-ui `ecb07e9`。配備した WAR は前の固定先 `b0a1e79` で組んだ。手元の WAR は `ac9c58d4…` で配備したものと違う | **ずれ**（2節の 1）。Q4: A で記録し、次の配備で揃える |
| サブモジュール | make-you-chic-ui `ecb07e9`、java-mustache-processor `8d44c36` | 同じ | 一致 |

## 2. 記録どうしの食い違い・記録と環境の食い違い（直さず記録したもの）

承認済みの記録は書き換えていません（`project.md` の学び）。

| # | 食い違い | 正とするもの | 扱い |
|---|---|---|---|
| 1 | `main` の先頭（固定先 `ecb07e9`）と配備した WAR（`b0a1e79` で組んだ）。差は make-you-chic-ui の `package.json`・`package-lock.json`（Vite など組む道具の版）だけで、アプリと部品のソースは同じ | 配備したものは `deployment-log.md` 1節の WAR | Q4: A。次の配備で WAR を作り直して揃える（`feedback-loop.md` の A4） |
| 2 | 手元の監視のコンテナのイメージ 0.34.0 と `compose.yaml` の 0.36.0 | `compose.yaml` | 次に起動するときに作り直す |
| 3 | Build and Test の記録（`build-and-test-summary.md` 5節・`test-results.md` 4節・`quality-gates.md` 6節）は `logoutIntent.test.tsx` を「不安定と確かめられていない」とするが、CI（`9292432`）で二度目に落ち、Observability Setup の段の途中で直した（`bb73817`） | `operation/observability-setup/memory.md` | 記録だけ |
| 4 | `operation/environment-provisioning/validation-report.md` の N2 は前の Intent の複写の権限を「直していない・諮る」とするが、同じ段の `memory.md` は依頼者の決定で 600 に直したとする。今はすべて 600 | `memory.md` と今の状態 | 記録だけ |
| 5 | U2 の `reliability-design.md` 2節は書き込みの文の時間切れを TIMEOUT とするが、実装は確定の段だけが TIMEOUT で、書き込みの文の段は UNAVAILABLE | 実装と `operation/incident-response/runbooks.md` の RB-37 | 記録だけ（Incident Response で記録済み） |
| 6 | `rollback-runbook.md` 3.1 の書き込みだけを止める手（`.env` の複写を戻す）と、Incident Response の RB-44（2行を消す） | RB-44 | 記録だけ（Incident Response で記録済み） |
| 7 | 監査の行の大きさの見積もり（約 0.5 KB）と実測（約 100〜108 B） | 実測 | 記録だけ |
| 8 | 負荷の試験の対象DB の試験の値（README・基盤の設計 2.1）と、実際の表の大きさ（MySQL は 1,017.8 MB） | 実測と `load-test-plan.md` 1.2 | 第2の束で直す（`feedback-loop.md` の G1・B5） |
| 9 | U3 R-09 の既知の制約が段の `memory.md` にあり、U3 の `generation-notes.md` 11.5 の既知の制約に無い | `build-and-test-summary.md` 5節 | 記録だけ |
| 10 | 05a9b37 の CI の1回目の失敗が Build and Test と Code Generation の記録に無い | `construction/ci-pipeline/ci-config.md` 6.2 | 記録だけ（CI Pipeline で記録済み） |

## 3. この段で環境を変えたこと（Q3: A、依頼者の承認済み）

- `mastersmith` のタグ 8 個を外し、使われていないビルドのキャッシュ（1.462GB）を消し、9月の `.env` の複写を中身を開かずに消しました（`cost-analysis.md` 5.1）。
- 片付けの後: `mastersmith` のタグは `local`・`pre-business-data-views` の2つ。名前の無いイメージ 0 個。配備したアプリは healthy・200 のまま。
- 片付けは、この段の決まり（環境を変えない）との差です。

## 4. 結論

- 配備したアプリ・設定のファイル・見本の対象DB・戻し先は、配備の記録と一致しました。
- ずれは2つです: `main` の先頭と配備した WAR の組む道具の版（次の配備で揃える）と、手元の監視のコンテナのイメージ（次に起動するときに作り直す）。どちらもアプリの振る舞いには関わらない見込みです。
- 記録どうしの食い違いは8つで、どれも承認済みの記録を書き換えずに、正とするものを決めて記録しました。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-optimization-questions.md`（Q2: A・Q3: A・Q4: A）
- `operation/deployment-execution/deployment-log.md`（1節・5節・7節）
- `operation/environment-provisioning/environment-inventory.md`（2節〜7節）・`validation-report.md`（N2）・`memory.md`
- `operation/deployment-pipeline/cd-config.md`（1節）・`rollback-runbook.md`（3.1）
- `operation/observability-setup/alarms.md`・`dashboards.md`・`memory.md`
- `operation/incident-response/incident-plan.md`・`runbooks.md`（RB-37・RB-44）
- `operation/performance-validation/test-results.md`（1節）・`load-test-plan.md`（1.1・1.2）
- `construction/build-and-test/build-and-test-summary.md`（5節）・`test-results.md`、`construction/ci-pipeline/quality-gates.md`・`ci-config.md`
- この段のコマンドの出力: `docker inspect mastersmith-app-1`（環境変数は名前だけ）・`docker inspect mastersmith-lgtm-1`・`docker run --rm --entrypoint sha256sum mastersmith:local /app/mastersmith.war`・`docker compose --profile targetdb-postgres config`（上限と profile だけ）・`docker logs mastersmith-app-1`（レベルの件数と Flyway の文だけ）・`ls -l .env .env.targetdb`（時刻だけ）・`ls -l ~/.mastersmith-backup/`・`git rev-parse`・`git diff --stat c099a2f develop`・`git submodule status`・`grep -n image: compose.yaml`

## Assumptions & Open Questions

- 内部DB の中身は数え直していません（Q2: A）。配備の後に内部DB を変える操作をしていない前提です。
- 組む道具の版の差が画面の振る舞いに関わらないことは、Vite の直しの後の verify（画面のテスト 1975 件）と E2E 256 件の通過で見ていますが、配備した WAR そのもので確かめたのではありません。
