# 設定のずれ（drift-report）

動いている環境を読み取りだけで確かめ、記録した設計の値と1つずつ比べました（Q2: A、2026-10-03 23:49〜23:55 JST）。

- 配備したアプリは止めず、要求も送っていません。
- `.env`・`.env.targetdb` は開いていません。権限と更新の時刻、項目の名前ごとの件数（`.env.example` の名前ごとに `grep -c "^NAME="`）だけを見ました。値は書きません。
- 内部DB の V9・停止中の利用者・監査の件数は、配備の記録（`operation/deployment-execution/deployment-log.md`・`smoke-test-results.md`）の値を正とします（Q2: A）。

## 1. 比べた結果

### 1.1 アプリのコンテナ

| 項目 | 設計・記録の値 | 実際 | 判定 | 出どころ |
|---|---|---|---|---|
| 配備したイメージ | `mastersmith:local` = `sha256:f386b55f16a8…` | 同じ | ずれ無し | `deployment-log.md` 1節 |
| 状態 | healthy、再起動 0 回、OOMKilled なし | healthy、再起動 0 回、`OOMKilled=false`。23:32:43 JST に起動（Performance Validation の後に起動し直したまま） | ずれ無し | `health-check-report.md`、`operation/performance-validation/test-results.md` 1節 |
| メモリの上限（compose の既定） | `${MASTERSMITH_CONTAINER_MEMORY:-2g}` | `compose.yaml` の既定は 2g のまま | ずれ無し | `environment-inventory.md` 2節、`cd-config.md` 1節 |
| CPU の上限（compose の既定） | `${MASTERSMITH_CONTAINER_CPUS:-4}` | `compose.yaml` の既定は 4 のまま | ずれ無し | 同上 |
| この PC の `.env` の上限の項目 | 2つとも1件ずつある（値は見ていない） | `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` が1件ずつ | ずれ無し | `environment-inventory.md` 4節 |
| 効いている上限 | メモリ 2GiB・CPU 4 | `docker inspect` は `2147483648`・`4000000000`。`docker compose config`（`.env` を読み込んだ結果）は `2147483648`・`4` | ずれ無し | `environment-inventory.md` 2節。下の注 |
| 再起動の方針・利用者・特権 | `restart: "no"`、`10001:10001`、特権なし | 同じ | ずれ無し | `environment-inventory.md` 2節 |
| PC に開く番号 | `127.0.0.1:8080` | 同じ | ずれ無し | `environment-inventory.md` 2節・7節 |
| ヘルスチェック | `/dev/tcp` で `/actuator/health` の 200。間隔 30 秒・時間切れ 5 秒・3回・猶予 40 秒・始めの間隔 2 秒 | 同じ | ずれ無し | `environment-inventory.md` 3節 |
| 起動の入口 | `MaxRAMPercentage=50.0`・`user.timezone=Asia/Tokyo`・`h2.compactThreads=1`・`${MASTERSMITH_JAVA_OPTIONS:-}` | 同じ値（実際は `sh -c "set -f; exec java …"` で包んだ形） | ずれ無し（書き方の違いだけ） | `environment-inventory.md` 3節 |
| ボリューム | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 同じ | ずれ無し | `environment-inventory.md` 3節 |
| ログ | json-file、10m × 3 | 同じ | ずれ無し | `environment-inventory.md` 3節 |
| 環境変数（名前だけ） | `.env` の 16 項目と `TZ`、イメージの6つ | 同じ名前の組（`docker compose config` の app の環境の項目も 17） | ずれ無し | `environment-inventory.md` 3節・4節 |

- 注: `environment-inventory.md` の Assumptions は「`.env` の上限の値は既定と同じと見ている」としていました。`docker compose config` は `.env` を読み込んだ結果を返すため、効いている値が既定と同じ（2GiB・4）であることは、この段で確かめられました。`.env` の値そのものは見ていません。

### 1.2 起動のログ（版・件数・キーの名前だけ）

| 項目 | 設計・記録の値 | 実際 | 判定 | 出どころ |
|---|---|---|---|---|
| スキーマの版 | 配備で V8 → V9 | 1回目の起動（21:55 JST）で `Current version … 8` → `now at version v9`。2回目（22:04、S5 の後）と3回目（23:32、Performance Validation の後）は `Successfully validated 9 migrations`・`Current version … 9`・`is up to date` | ずれ無し | `smoke-test-results.md` S2、`deployment-log.md` 2節 |
| ログの形 | すべて JSON | 129 行すべて JSON。キーは `level`・`logger`・`message`・`thread`・`timestamp`（ほかは行による） | ずれ無し | `smoke-test-results.md` S2 |
| ERROR | 0 件 | 0 件 | ずれ無し | 同上 |
| WARN | 起動ごとに2件（Flyway の H2 の版の注意・BeanPostProcessorChecker） | 7 件 = 3回の起動 × 2件 ＋ `GlobalExceptionHandler` の1件（21:59 JST、401 `AUTHENTICATION_FAILED`） | ずれ無し。401 の1件は S5 の `LOGIN_FAILED` 1件（依頼者の操作）に当たる | `smoke-test-results.md` S2・S5 |
| 初期管理者の INFO | キー `maskedEmail` があり、`email` は無い | 3回の起動とも、キーは `level`・`logger`・`maskedEmail`・`message`・`thread`・`timestamp` | ずれ無し | `smoke-test-results.md` S2 |

### 1.3 設定のファイル

| 項目 | 設計・記録の値 | 実際 | 判定 | 出どころ |
|---|---|---|---|---|
| `.env` の権限・管理・更新の時刻 | 600、Git の管理外、最後の更新 2026-09-30 23:48 | 600、管理外、2026-09-30 23:48:31（この Intent の間に変わっていない） | ずれ無し | `environment-inventory.md` 4節、`cd-config.md` 2節（`.env` は変えない） |
| `.env` の有効な項目 | 16 項目、どれも1件ずつ | 16 件。初期管理者2・署名鍵1・コンテナの上限2・ベース URL 1・メール3・対象DB 7 が1件ずつ。ほかの `.env.example` の名前は0件 | ずれ無し | `environment-inventory.md` 4節 |
| `.env` に無い項目 | `MASTERSMITH_DB_MAXIMUM_POOL_SIZE`（既定 30）・`MASTERSMITH_JAVA_OPTIONS`・`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD` | 4つとも0件 | ずれ無し | 同上 |
| 外部エクスポートの2行 | コメントのまま（配備したアプリは外部エクスポートを無効） | `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`・`_ENDPOINT` は有効 0・コメント 1 件ずつ | ずれ無し | 前の Intent の Observability Setup の決定、この Intent の `slo-config.md`（使い捨ての環境だけ有効にした） |
| `.env.targetdb` | 600、管理外、2項目 | 600、管理外、2件。アプリの環境の名前に入っていない | ずれ無し | `environment-inventory.md` 4節 |
| `compose.yaml` の profile | `observability`・`monitoring`・`mail`・`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb` | 同じ6つ。`docker compose config --quiet` が通る | ずれ無し | `environment-inventory.md` 4節 |
| ほかのサービスの上限 | lgtm 1536m・Mailpit 256m・見本の PostgreSQL 512m | 同じ（ほかに MySQL 768m・MariaDB 512m） | ずれ無し | `environment-inventory.md` 2節 |

### 1.4 ほかのコンテナ・戻し先・バックアップ・リポジトリ

| 項目 | 設計・記録の値 | 実際 | 判定 | 出どころ |
|---|---|---|---|---|
| Mailpit | 動いている（配備では触らない） | Up・healthy（3日前から） | ずれ無し | `cd-config.md` 1節 |
| 見本の PostgreSQL | 動いている（触らない） | Up（4日前から） | ずれ無し | 同上 |
| 手元の監視 | 見たいときだけ起動 | Exited（Performance Validation の後に止めた） | ずれ無し | `operation/observability-setup/dashboards.md`、`test-results.md` 1節 |
| 戻し先 | `mastersmith:pre-user-admin` = `c77c1bb247f9…` | 同じ | ずれ無し | `deployment-log.md` 1節 |
| 配備の後のバックアップ | `~/.mastersmith-backup/mastersmith-data-202610032204-after-user-admin.tgz`（権限 600） | 600、22,176 B で残る。置き場は 700 | ずれ無し | `deployment-log.md` 2節 |
| ボリューム | 64 個。`mastersmith` の3つ | 64 個（名前の無いもの 61）。`mastersmith` の3つ（内部DB 69.63kB・見本の DB 65.77MB・監視 796.5MB） | ずれ無し | `environment-inventory.md` 6節 |
| アプリのソース | 配備した版は `cc28d1f`（アプリのソースは `b126bdc` と同じ） | `develop` の先頭 `95a8de8` との差は `aidlc/` の外で0行。`aidlc/` の外に未コミットの変更は無い | ずれ無し（イメージの作り直しは要らない） | `deployment-log.md` 1節 |
| `main` | `cc28d1f` へ fast-forward、push は依頼者 | `main`・`origin/main` とも `cc28d1f` | ずれ無し | `deployment-log.md` 2節 |
| `develop` の push | — | `origin/develop` より 6 コミット先（記録だけ） | 依頼者の push 待ち（`feedback-loop.md` 5節） | — |
| サブモジュール | make-you-chic-ui `3d9521a`、java-mustache-processor `8d44c36`（0.1.0） | 同じ | ずれ無し | `cd-config.md` 2節 |
| Dependabot の知らせ | — | 開いているもの 0 件 | — | `gh pr list` |
| 手元のイメージ | 46 個（タグ付きの一覧 46 行）。結合テストの MySQL・MariaDB のダイジェスト固定のイメージがある | **22 個・7.094GB。MySQL・MariaDB のイメージが無い**（`mastersmith` の 11 個のタグ・Mailpit・見本の PostgreSQL は残る） | **ずれ（原因は確かめていない）**。2節の1行目 | `environment-inventory.md` 5.2節・5.3節、`validation-report.md` |

## 2. 記録どうしの食い違い・記録と環境の食い違い（直さず記録したもの）

承認済みの記録は書き換えていません。直すかどうかは依頼者が決めます（`project.md` の Change Control）。

| 食い違い | 根拠 | 影響と扱いの案 |
|---|---|---|
| **手元のイメージの数**: Environment Provisioning（21:36 ごろ）の記録は「片付けの後 46 個・21.43GB、タグ付きの一覧 46 行」で、結合テスト用の MySQL（`ade067ae…`）・MariaDB（`d4fdec05…`）のイメージがあるとしている。この段では 22 個・7.094GB で、MySQL・MariaDB のイメージが無い | この Intent の記録に残る片付けは、`validation-report.md` 4節の `docker image prune -f`・`docker builder prune -f` の1回だけ。`docker events` には直近の消した記録が残っていなかった。この質問の記録（23:41）の時点で、すでに 22 個だった | 配備したアプリには影響しない。次の `./gradlew verify` の対象DB の結合テストで、ダイジェストで取り直す（ネットワークが要る）。誰がいつ消したかを依頼者に確かめたい。記録の側は直さない案 |
| **監査ログの追記を消した手順の名前**: `construction/code-generation/gate-decisions.md` 6節は「B5 の squash の手順（`git restore --source=HEAD -- aidlc/`）で消した」と書く。`project.md` の学び（2026-10-03）は「U3 の小さな直しの統合で消した」と書く | git の履歴では、B5 は `develop` を `afd69c3` へ fast-forward で統合した（`335aba6` の件名）。`f82f186` の後の squash は U3 の直し `b126bdc`（`fix/260930-user-admin-audit-fields`）だけ | `project.md` の学びと git の履歴が合う。`gate-decisions.md` の「B5 の」は、U3 の直しの統合の誤記か、B5 と同じ手順の意味と読める。直すかは依頼者が決める。`feedback-loop.md` 4節は「U3 の直しの統合」で書いた |
| **古いタグのイメージの数**: この段の質問の記録の H2 は「10 個」 | `mastersmith` のタグは 11 個。10 は `local` を除いた数（今の戻し先 `pre-user-admin` を含む） | `cost-analysis.md` 4節に数え方を書いた。片付けの候補は 8〜9 個 |
| **起動の入口の書き方**: `environment-inventory.md` 3節は `java … -jar /app/mastersmith.war` と書く | 実際の ENTRYPOINT は `sh -c "set -f; exec java … \"$@\" -jar …"` で包んだ形 | 値（JVM の設定）は同じで、ずれではない。直さない案 |
| **前の Intent までのバックアップの権限**: この Intent の配備の後のバックアップは 600 | 前の Intent までの `.tgz` 5 件は 644（置き場は 700 で、ほかの利用者からは読めない） | この Intent の記録との食い違いではない。権限を 600 にそろえるかは、バックアップの保存の期間と一緒に依頼者が決める（`feedback-loop.md` の D6） |

## 3. 結論

- 動いている配備したアプリ・設定のファイル・戻し先・バックアップに、記録した設計の値とのずれはありません。
- 1つだけ、手元のイメージが Environment Provisioning の記録より減り、結合テスト用の MySQL・MariaDB のイメージが無くなっています。配備したアプリには影響しませんが、原因が分からないため、承認の場で依頼者に確かめます。
- 記録どうしの食い違いは2節のとおりです。どれも承認済みの記録は書き換えず、扱いを依頼者に諮ります。

### 依頼者の決定（2026-10-03、承認の場の前）

- 手元のイメージが減ったのは、依頼者が片付けたもの（「自分が片付けた」）。原因は分かったため、ずれではなく記録として残す。次の `./gradlew verify` の前に、MySQL・MariaDB のイメージを取り直すネットワークが要る。
- `construction/code-generation/gate-decisions.md` 6節の「B5 の squash」の表現は書き換えず、この文書の2節の記録だけにする（正しくは U3 の小さな直し `b126bdc` の squash）。
- 片付け（H1〜H3）と古いバックアップの権限（644）は、いまは決めず後で判断する。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（Q2: A、確認済みの要約）
- `operation/deployment-execution/deployment-log.md`・`health-check-report.md`・`smoke-test-results.md`
- `operation/environment-provisioning/environment-inventory.md`・`validation-report.md`
- `operation/deployment-pipeline/cd-config.md`
- `operation/observability-setup/slo-config.md`・`dashboards.md`・`alarms.md`
- `operation/performance-validation/test-results.md`
- `construction/code-generation/gate-decisions.md`（6節）、`aidlc/spaces/default/memory/project.md`（Change Control の学び）
- この段で実行したコマンドの出力（2026-10-03 23:49〜23:55 JST）: `colima list`・`docker ps -a`・`docker inspect`（値の出ない項目と環境変数の名前だけ）・`docker stats --no-stream`・`docker compose config --format json` を jq で上限・profile・イメージ・番号・環境の項目の数だけに絞ったもの・`docker compose config --quiet`・`stat`（`.env`・`.env.targetdb`）・`grep -c`（`.env.example` の名前ごと）・`git check-ignore`・`docker logs` の行数・レベルの件数・Flyway の版・キーの名前・`docker images`・`docker image inspect`・`docker system df`（`-v`）・`docker volume ls`・`docker events`・`ls -l ~/.mastersmith-backup`・`git rev-parse`・`git diff cc28d1f HEAD -- . ':!aidlc'`・`git status --porcelain`・`git submodule status`・`git log`・`gh pr list`

## Assumptions & Open Questions

- 手元のイメージが減った原因は、依頼者の片付けと分かった（3節の依頼者の決定）。
