# 設定のずれ（drift-report）

配備先が開発者の PC 上のコンテナのため、AWS Config のずれの検出を、`docker inspect`・環境変数の名前・`.env` の更新の時刻と、記録した値との1つずつの比べに読み替えました（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。読み取りだけで確かめ、配備したアプリは止めず、要求も送っていません。内部DB の状態は配備の後の複写の数え（`operation/deployment-execution/deployment-log.md` 2.2節、`smoke-test-results.md` 2節）を正とします。値（`.env` の値・個人に関する値・行の値）は書きません。

- 比べた記録: `operation/deployment-execution/deployment-log.md`・`health-check-report.md`、`operation/environment-provisioning/environment-inventory.md`（「配備の後」の列）・`validation-report.md`、`operation/deployment-pipeline/cd-config.md`。
- 確かめた時刻: 2026-11-01 12:00〜12:15 ごろ UTC（片付けの前）。片付けと X2・X3 の確かめは 12:25 ごろ UTC（3節）。

## 1. 比べた結果

### 1.1 アプリのコンテナ

| 項目 | 記録 | 今 | 判定 |
|---|---|---|---|
| イメージ | `mastersmith:local` = `sha256:d8a467b60f6f…`（`deployment-log.md` 4節） | 同じ | 一致 |
| WAR の SHA-256（イメージの中） | `4cd5c416…008222f` | 同じ（`docker run --rm --entrypoint sha256sum mastersmith:local /app/mastersmith.war`） | 一致 |
| 上限 | メモリ 2GiB・CPU 4、`restart: no` | 同じ（`HostConfig.Memory` 2,147,483,648、`NanoCpus` 4,000,000,000） | 一致 |
| ボリューム | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 同じ | 一致 |
| ヘルスチェック | compose の bash の `/dev/tcp` で `GET /actuator/health` の 200 | 今 healthy | 一致 |
| 環境変数（名前だけ） | 配備の後 19 項目（`environment-inventory.md` 3節） | `.env` 由来の 18 項目と `TZ` の 19 項目（イメージの6項目を除く）。参照の2つの設定は 0 件（既定で動く） | 一致 |
| 起動 | 配備は 2026-11-01 05:01Z。Performance Validation の後に起こし直す | 2026-11-01T11:58:44Z に `docker compose start app` で起こし直した。再起動 0 回、OOMKilled なし | 記録どおり（`operation/performance-validation/test-results.md` 1節） |

### 1.2 起動のログ（件数とロガーの名前だけ）

- 起こし直した後のログ: INFO 37・WARN 2（Flyway の H2 の版・`BeanPostProcessorChecker` の各1行）・ERROR 0、JSON でない行 0。
- `Migrating schema` は 0 件で、内部DB のスキーマは版 13 のまま（この Intent は移行なし。`deployment-log.md` と同じ）。

### 1.3 設定のファイル

| ファイル | 記録 | 今 | 判定 |
|---|---|---|---|
| `.env` | この Intent では変えない（`deployment-log.md` 4節「変わらないもの」） | 更新の時刻が 2026-11-01 08:43:55Z。項目の名前は 18 で、増減は無い（3節） | **ずれ**（X2。値の変化は未確認） |
| `.env.targetdb` | 変えない | 更新の時刻 2026-10-21 08:48 JST のまま。項目の名前は 3 | 一致 |
| `compose.yaml`・`Dockerfile` | 配備に効く差は無い（`cd-config.md`） | 配備の後の `aidlc/` の外の変更は、ダッシュボードの JSON 1 つだけ（`git diff --stat main develop`） | 一致（Observability Setup の記録どおり） |

### 1.4 ほかのコンテナ・戻し先・バックアップ・リポジトリ

| 項目 | 記録 | 今 | 判定 |
|---|---|---|---|
| 見本の PG | `postgres:18.6`（ダイジェスト `5a5a84b1…`） | 同じ。Up。`compose.yaml` 147 行目と一致 | 一致 |
| Mailpit | `axllent/mailpit:v1.32.0`（ダイジェスト `0790692b…`） | 同じ。Up・healthy。`compose.yaml` 121 行目と一致 | 一致 |
| 手元の監視のコンテナ | `grafana/otel-lgtm:0.38.0`（ダイジェスト `75cdf892…`、FR6.2 で確かめた） | 同じ。Exited。`compose.yaml` 83 行目と一致 | 一致（前の Intent のずれ 0.34.0 は FR6.1 で直った） |
| lgtm の古いイメージ | U5 の NFR8.83 は `0.34.0` を戻し先として残す。Environment Provisioning の時点では `0.34.0` と古いタグ無しの2つ（`57b5ab1c`・`57c04cbd`）があった（`validation-report.md` N3） | 3つとも残っていない | **ずれ**（X1） |
| 戻し先 | `mastersmith:pre-reference-lookup-cleanup` = `e0ede6ddd5c4` | 同じ | 一致 |
| バックアップ | 入れ替えの前と配備の後の複写（600） | 同じ。複写は全部で 22 件、どれも 600。`~/.mastersmith-env-*` は 0 件（Environment Provisioning の Q1: A のとおり） | 一致 |
| 数える道具 | `~/.mastersmith-tools/`（700）の道具は 700・600 | `Cols.java`・`SinceCount.java` の2つが 644 だった | **ずれ**（X3。3節で直した） |
| `main` と配備した版 | Deployment Execution で `main` を `be6e61c5` へ進めた | `main` = `origin/main` = `be6e61c5`。配備した WAR は `5458e3eb` から作り、アプリと部品のソースは `main` と同じ | 一致（前の Intent の A4 の食い違いは無くなった） |
| `develop` | — | `develop` = `origin/develop` = `96011fe0`。`main` より 9 コミット先（`aidlc/` の外はダッシュボードの JSON だけ） | 記録どおり（Q4: A で取り込む） |
| サブモジュール | make-you-chic-ui `cab4207`、java-mustache-processor `8d44c36` | 同じ | 一致 |

## 2. 記録どうしの食い違い・記録と環境の食い違い（直さず記録したもの）

承認済みの記録は書き換えていません（`project.md` の学び）。

| # | 食い違い | 正とするもの | 扱い |
|---|---|---|---|
| 1 | X1: lgtm の `0.34.0` と古いタグ無しの lgtm 2つが、記録の無いまま消えている。U5 の NFR8.83 は `0.34.0` を戻し先として残すとした。いつ・何で消えたかは `docker events` に残っておらず確かめられない（VM の中のディスクの使用は Environment Provisioning の 40G から今 30G に減っており、約 10GB の lgtm のイメージが消えたことと合う） | 今の状態と `compose.yaml`（`0.38.0` のダイジェスト） | Q3: A。記録だけにする。lgtm を `0.34.0` に戻すときは、上流に残るタグとダイジェストで pull し直す（固定のダイジェストで取り直す）。lgtm は手元の監視で、アプリの戻しには関わらない |
| 2 | X2: `.env` の更新の時刻 2026-11-01 08:43:55Z（Performance Validation で配備したアプリを止めた約 2 分後）に当たる記録がどの段にも無い | 3節の確かめ | Q3: A。3節のとおり項目の名前を確かめ、記録する |
| 3 | X3: `~/.mastersmith-tools/` の2ファイルが 644 | 前の Intent の決まり（複写と道具は 600・700） | Q3: A。600 に直した（3節） |
| 4 | `deployment-strategy.md` の S1 は WARN を `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` で探すと書くが、ログに出るのは Spring の項目の名前 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total` | `deployment-log.md` 3節の 1、`operation/incident-response/runbooks.md`（123 行目・RB-51） | 記録だけ（T5）。次の配備の手順は Spring の名前で書く |
| 5 | AC3.1.7（U2 → U3）と AC3.4.5（U1 → U3）が U3 の `traceability.json` に無く、追跡の連鎖が途切れる。実装とテストは U3 にある | `construction/build-and-test/build-and-test-summary.md` 5節、`verification/phase-check-construction.md` 3節 | 記録だけ（T4。Build and Test の承認の場で受け入れ済み） |
| 6 | `validation-report.md` の N3 は回収できるイメージを 16GB とするが、今は 5.419GB（片付けの前） | 今の状態（差の多くは X1） | 記録だけ |
| 7 | HikariCP の WARN のスタックトレースに対象DB のホスト名が出る。`project.md` の Forbidden（対象DB の接続先をログに含めない）との差 | `operation/observability-setup/log-queries.md` 3節の依頼者の決定 C | 既知の差として記録だけ（直さない・次の Intent の項目にもしない）。ユーザー名・パスワード・資格情報を含む JDBC の URL は出ていない。配備先が決まってログの置き場と見る人が変わるときに見直す（`incident-plan.md` 7節） |
| 8 | 指揮役の連絡では `a3099f8` は単独の CI の run を持たないとされたが、`gh run list` には run 41177060211 がある | `gh` の結果 | 記録だけ（`build-and-test-summary.md` 5節に記録済み） |

## 3. この段で環境を変えたこと・確かめたこと（Q2: A・Q3: A、依頼者の承認済み、2026-11-01 12:25 ごろ UTC、指揮役が実行）

- **片付け（Q2: A）**: `mastersmith` のタグ 5 個（`perf-reference-lookup-cleanup`・`pre-perf-targets-review`・`carryover`・`pre-deps-perf-carryover`・`pre-business-data-views`）を、使うコンテナが 0 であることを確かめてから外し、使われていないビルドのキャッシュ（514.5MB）を消しました（`cost-analysis.md` 5.1）。残る `mastersmith` のタグは `local`（`d8a467b60f6f`）・`pre-reference-lookup-cleanup`（`e0ede6ddd5c4`）。配備したアプリはイメージ `d8a467b6…` のまま healthy です。
- **X3**: `Cols.java`・`SinceCount.java` を 600 にしました。置き場のほかのファイルは 600 か 700 です。
- **X2**: 依頼者に心当たりを尋ね、「心当たりは無い」との答えでした。承認を得て、値を表示せずに項目の名前だけを数えました。
  - `.env` の項目の名前は 18、`.env.targetdb` は 3。
  - `.env` の項目の名前の集合と、動いているアプリのコンテナの環境変数の名前を比べ、`.env` の 18 項目はすべてコンテナにあり、コンテナにだけあるのはイメージ由来の `JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL`・`PATH` と `TZ` だけでした（項目の増減は無い）。
  - **値が変わったかは確かめていません**（値を開かないため）。動いているアプリは 08:43:55Z の後に作り直していないため、今の動作は配備のときの値で動いています。**次にアプリを作り直す配備の前に、依頼者が `.env` の中身を確かめます**（`feedback-loop.md` 7節）。
- これらは、この段の決まり（環境を変えない）との差です。

## 4. 結論

- 配備したアプリ（イメージ・WAR・上限・ボリューム・環境変数の名前）・見本の対象DB・Mailpit・手元の監視のコンテナ・戻し先・バックアップ・`main` とサブモジュールは、配備の記録と `compose.yaml` に一致しました。
- ずれは3つです: X1（lgtm の古いイメージが記録の無いまま消えた。記録だけ）、X2（`.env` の更新の時刻。項目の増減は無く、値の変化は未確認。次の配備の前に依頼者が確かめる）、X3（道具の権限。600 に直した）。
- 記録どうしの食い違いと既知の差は 2節の 4〜8 で、どれも承認済みの記録を書き換えずに、正とするものを決めて記録しました。

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/feedback-optimization/feedback-optimization-questions.md`（Q2: A・Q3: A・Q4: A と X2 の追加の確認）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-execution/deployment-log.md`（2節〜4節）・`health-check-report.md`・`smoke-test-results.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/environment-provisioning/environment-inventory.md`（1節〜6節）・`validation-report.md`（N3）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`（S1）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/dashboards.md`・`alarms.md`・`slo-config.md`・`log-queries.md`（3節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/incident-response/incident-plan.md`（7節）・`runbooks.md`（RB-51）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/performance-validation/test-results.md`（1節・9節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/build-and-test/build-and-test-summary.md`（5節）、`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/verification/phase-check-construction.md`
- この段のコマンドの出力: `docker inspect`（4つのコンテナ。環境変数は名前だけ）・`docker run --rm --entrypoint sha256sum mastersmith:local /app/mastersmith.war`・`docker logs`（レベルとロガーの件数、`Migrating schema` の件数だけ）・`docker images`・`docker events`（イメージ）・`stat`（`.env`・`.env.targetdb` の時刻と権限だけ）・`ls -l ~/.mastersmith-backup/ ~/.mastersmith-tools/`・`colima ssh -- df -h /`・`git rev-parse`・`git diff --stat main develop -- . ':!aidlc'`・`git submodule status`・`grep -n image: compose.yaml`、指揮役が実行した片付けと名前の件数の比べの結果

## Assumptions & Open Questions

- 内部DB の中身は数え直していません。配備の後に内部DB を変える操作は、Performance Validation（使い捨ての環境）を含めてしていない前提です。
- X2 の `.env` の値が変わったかは確かめていません。次にアプリを作り直す前に依頼者が確かめます。
- X1 の削除がいつ・何で起きたかは確かめられていません（指揮役がこの会話と Performance Validation の担当の操作の記録を検索し、lgtm のイメージを消した操作は見つかりませんでした）。
