# 設定のずれ（drift-report）

動いている環境を読み取りだけで確かめ、記録した設計の値と1つずつ比べました（Q2: A、2026-10-15 11:39〜11:45（02:39〜02:45Z））。パスは、断りが無ければ `aidlc/spaces/default/intents/261004-role-menu/` を省いたリポジトリのルートからの相対パスで書きます。

- **正とする記録**: 最後の配備し直しの記録 `operation/performance-validation/redeploy-log.md` の 4節です。その前の記録（`operation/deployment-pipeline/cd-config.md`・`operation/environment-provisioning/environment-inventory.md`・`operation/deployment-execution/deployment-log.md`・`health-check-report.md`）の値は「その時点の値」として並べ、配備し直しによるイメージ・WAR・戻し先のタグの違いはずれに数えません。
- 配備したアプリは止めず、要求も送っていません。`docker exec` で WAR の SHA-256 と PID 1 のコマンド行の件数だけを読みました。
- `.env`・`.env.targetdb` は開いていません。権限と更新の時刻、項目の名前ごとの件数（`.env.example` の名前ごとに `grep -c "^NAME="`）だけを見ました。起動のログの初期管理者のメールアドレスの値の件数は、値を表示せずに数えました。
- 内部DB の版・利用者・ロールと割り当て・監査の数は、配備し直しの直前の複写の数え（`redeploy-log.md` 3節）を正とします（Q2: A）。

## 1. 比べた結果

### 1.1 アプリのコンテナ

| 項目 | 正とする記録の値（`redeploy-log.md` 4節ほか） | 実際 | 判定 | その時点の値（前の記録） |
|---|---|---|---|---|
| 配備したイメージ | `mastersmith:local` ＝ `sha256:e0a5825d257c1b1f…` | 同じ | ずれ無し | 初回 `e41a5b02fd0b`（`deployment-log.md` 8節）、S8 の直し `3247c5bdab79`（11.4、`health-check-report.md` 1節）。`environment-inventory.md` 2節は配備の前の `fefeafbd299b` |
| イメージの中の WAR | SHA-256 `4bf6e68c…741febd3` | `4bf6e68ca1d996b47b419023590a8034c0656fe4cce26f07d0d809fa741febd3`。PC の `backend/build/libs/mastersmith.war`（2026-10-15 04:53 作成）も同じ | ずれ無し | 初回 `517c847b…`（`environment-inventory.md` 5節）、S8 の直し `f5293bed…`（`health-check-report.md` 2節） |
| 状態 | healthy、再起動 0、OOMKilled false | healthy、再起動 0、`OOMKilled=false`。08:51:07（23:51:07Z）に起動（Performance Validation の後の `docker compose start app` のまま） | ずれ無し | — |
| 上限 | メモリ 2g・CPU 4・`restart: no` | `docker inspect` は `2147483648`・`4000000000`・`no`。`docker compose config`（`.env` を読み込んだ結果）は `2147483648`・`4` | ずれ無し | 同じ（`environment-inventory.md` 2節） |
| JVM の設定 | PID 1 に `-XX:+ExitOnOutOfMemoryError` 1 件・`-XX:MaxRAMPercentage=50.0` 1 件 | 同じ | ずれ無し | 配備の前の版には `ExitOnOutOfMemoryError` が無い（`environment-inventory.md` 3節） |
| 起動の入口 | `java -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1 ${MASTERSMITH_JAVA_OPTIONS:-} -jar /app/mastersmith.war` | 同じ値（`sh -c "set -f; exec java …"` で包んだ形） | ずれ無し（書き方の違いだけ） | — |
| 利用者・特権・番号 | `10001:10001`、特権なし、`127.0.0.1:8080` | 同じ | ずれ無し | 同じ |
| ヘルスチェック | `/dev/tcp` で `/actuator/health` の 200。間隔 30 秒・時間切れ 5 秒・3回・猶予 40 秒・始めの間隔 2 秒 | 同じ | ずれ無し | 同じ（`environment-inventory.md` 3節） |
| ボリューム | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 同じ。77.82kB | ずれ無し | 81.92kB（配備の前、`environment-inventory.md` 7節） |
| ログ | json-file、10m × 3 | 同じ | ずれ無し | 同じ |
| 環境変数（名前だけ） | `.env` の 16 項目と `TZ`、イメージの6つ（`PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL`） | 同じ 23 の名前。`docker compose config` の app の環境の項目は 17 | ずれ無し | 同じ |

### 1.2 起動のログ（版・件数・キーの名前だけ）

| 項目 | 正とする記録の値（`redeploy-log.md` 4節・5節） | 実際 | 判定 |
|---|---|---|---|
| スキーマの版 | 12。新しい移行なし | 07:03 と 08:51 の2回の起動とも `Successfully validated 12 migrations`・`Current version of schema "PUBLIC": 12`・`is up to date. No migration necessary.` | ずれ無し |
| ログの形と件数 | 07:03 の起動で 39 行（すべて JSON）・INFO 36・WARN 2・ERROR 0 | コンテナのログ全体は 83 行（すべて JSON）・INFO 79・WARN 4・ERROR 0。内訳は 07:03 の起動 39 行、07:07 の停止の案内 5 行（INFO）、08:51 の起動 39 行（INFO 37・WARN 2） | ずれ無し |
| WARN | 起動ごとに2件（Flyway の H2 の版の案内・Spring の Bean の案内） | 4 件 ＝ 2回の起動 × 2件 | ずれ無し |
| 救済・初期管理者の INFO | 救済の WARN 0。初期管理者の INFO はキー `maskedEmail` あり・`email` 無し | 08:51 の起動の INFO のキーは `level`・`logger`・`maskedEmail`・`message`・`thread`・`timestamp`。救済の WARN 0 | ずれ無し |
| メールアドレスの漏れ | `.env` の初期管理者のメールアドレスの値の件数 0 | 0 | ずれ無し |
| 配備し直しの後の操作 | 依頼者は配備したアプリの画面を触っていない見込み | 07:03 の起動の後と 08:51 の起動の後に、起動・停止の案内のほかの行は無い（業務の WARN・ERROR が 0） | 見込みと合う（監査の数は確かめていない。2節） |

### 1.3 設定のファイル

| 項目 | 記録の値 | 実際 | 判定 | 出どころ |
|---|---|---|---|---|
| `.env` の権限・管理・更新の時刻 | 600、Git の管理外、最後の更新 2026-09-30 23:48。この Intent で変えない | 600、管理外（`git check-ignore`）、2026-09-30 23:48:31 | ずれ無し | `environment-inventory.md` 4節、`cd-config.md` 1節 |
| `.env` の有効な項目 | 16 項目、どれも1件ずつ | 16 件。初期管理者2・署名鍵1・コンテナの上限2・ベース URL 1・メール3・対象DB 7 が1件ずつ。ほかの `.env.example` の名前は0件 | ずれ無し | `environment-inventory.md` 4節 |
| `.env` に無い項目 | `MASTERSMITH_DB_PASSWORD`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`・`MASTERSMITH_JAVA_OPTIONS`・`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD` | 5つとも0件 | ずれ無し | 同上 |
| 外部エクスポートの2行 | コメントのまま（配備したアプリは外部エクスポートを無効） | `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`・`_ENDPOINT` は有効 0・コメント 1 件ずつ | ずれ無し | `operation/observability-setup/alarms.md` 2.1 |
| `.env.targetdb` | 600、管理外、2項目、2026-09-25 から変えない | 600、2件、2026-09-25 11:29:25。アプリの環境の名前に入っていない | ずれ無し | `environment-inventory.md` 4節 |
| `compose.yaml` の profile | `observability`・`monitoring`・`mail`・`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb` | 同じ6つ。`docker compose config --quiet` が通る | ずれ無し | `environment-inventory.md` 4節 |
| `compose.yaml` と `Dockerfile` | 最後の変更は配備の前の `005730a` | `git log` の最後の変更は `005730a`（2026-10-14 01:39 JST） | ずれ無し | `cd-config.md` 1節 |

### 1.4 ほかのコンテナ・戻し先・バックアップ・リポジトリ

| 項目 | 記録の値 | 実際 | 判定 | 出どころ |
|---|---|---|---|---|
| Mailpit・見本の PostgreSQL | 動いたまま（配備では触らない） | どちらも Up（7日前から。Mailpit は healthy） | ずれ無し | `redeploy-log.md` 4節 |
| 手元の監視 | 見たいときだけ起動 | Exited（約 10 時間前に止めた） | ずれ無し | `alarms.md` 6節、`operation/performance-validation/test-results.md` 1節 |
| 戻し先のタグ | `role-menu-before-dslfix` ＝ `3247c5bdab79`、`role-menu-before-s8fix` ＝ `e41a5b02fd0b`、`pre-role-menu` ＝ `fefeafbd299b` | 3つとも同じ ID で残る | ずれ無し | `redeploy-log.md` 4節 |
| `mastersmith` のほかのタグ | 前の Intent までの 16 個（`deployment-log.md` 14節の 19 個から `local`・`pre-role-menu`・`role-menu-before-s8fix` を除いたもの） | 0 個（この段の片付けで外した。3節） | ずれではない（依頼者の承認を得た変更） | この段の片付け |
| 名前の無いイメージ | 0 個 | 0 個 | ずれ無し | `deployment-log.md` 14節 |
| バックアップ | `~/.mastersmith-backup/`（700）。この Intent の分は `…202610140820-pre-role-menu`・`…202610141654-after-role-menu`・`…202610142211-after-role-menu`・`…202610142218-after-role-menu-final`・`…202610150703-pre-dslfix`（どれも 600） | 5つとも残る。置き場の 16 個はすべて 600（`count-db.sh` は 700） | ずれ無し | `health-check-report.md` 3節、`redeploy-log.md` 3節 |
| DSL の画面の直しの配備の**後**のバックアップ | — | **無い**（2節の4行目） | 記録として残す | — |
| ボリューム | 64 個。`mastersmith` の3つ | 64 個。`mastersmith` の3つ（内部DB 77.82kB・見本の DB 65.77MB・監視 825.8MB） | ずれ無し | `environment-inventory.md` 6.3・7節 |
| アプリのソース | 配備した版の画面の資源は `954028c` の `frontend/` と同じ（`redeploy-log.md` 2.2） | `develop` の先頭 `b2c4559` と `954028c` の差は `aidlc/` の外で0行。`aidlc/` の外に未コミットの変更は無い | ずれ無し | `redeploy-log.md` 2節 |
| `main` | `2161ce0`（Deployment Execution の順 10） | `main`・`origin/main` とも `2161ce0`。`develop` は `main` より 9 コミット先で、`aidlc/` の外の差は 15 ファイル。**配備した版は `main` に入っていない** | 記録として残す（Q4: A で取り込む。`feedback-loop.md` 7節） | `feedback-optimization-questions.md` の冒頭 |
| `develop` の push | — | `develop`・`origin/develop` とも `b2c4559`。CI（run 38671241833）はこの段の確かめの時点で実行中 | — | `gh run list` |
| サブモジュール | make-you-chic-ui `5bf1ffe`、java-mustache-processor `8d44c36`（0.1.0） | 同じ | ずれ無し | `redeploy-log.md` 2.1 |

## 2. 記録どうしの食い違い・記録と環境の食い違い（直さず記録したもの）

承認済みの記録は書き換えていません。直すかどうかは依頼者が決めます（`project.md` の Change Control）。

| 食い違い | 根拠 | 影響と扱いの案 |
|---|---|---|
| **名前の無いボリュームの数**: この段の質問の記録の F1 は「57 個（約 9.3GB）すべてに compose の project の印が無い」と書く | この段の読みでは、ボリューム 64 個のうち、名前の無い（64 桁の ID の）ものは 50 個で、どれも compose の project の印が無い。使われていないボリューム（`dangling=true`）は 61 個で、ほかのプロジェクト（`devenv` 6・`mastermeister-devenv` 3・`sqlapp2` 2）の名前付き 11 個を含む。57 の数え方は確かめられなかった | 扱い（残して依頼者が判断する、F1: A）は変わらない。回収できる量 9.262GB は同じ。質問の記録は書き換えない |
| **ログの件数の範囲**: 質問の記録の冒頭は「起動の後のアプリのログは INFO 79・WARN 4・ERROR 0」と書く | コンテナを作り直さずに起動し直したため、コンテナのログには 07:03 の起動と 08:51 の起動の2回分が入っている。08:51 の起動の後だけでは INFO 37・WARN 2・ERROR 0 | 件数は合う。範囲の書き方の違いだけで、直さない案 |
| **戻し先の一覧**: `health-check-report.md` 4節と `operation/incident-response/runbooks.md` の RB-35 の戻し先は `pre-role-menu`・`role-menu-before-s8fix` の2つ。`escalation-matrix.md` の RB-35 は `pre-role-menu` だけ | `redeploy-log.md` 4節で `role-menu-before-dslfix` が足された。DSL の画面の直しだけを戻すときの戻し先はこれ（`redeploy-log.md` 6節） | 配備し直しによる記録の更新。古い記録は書き換えない。次の Intent の Incident Response で手順書の戻し先をそろえる |
| **前の手順書の戻し先のタグ**: `runbooks.md` の古くなった箇所の表は、元の手順書の RB-10・RB-20・RB-26 の戻し先を `mastersmith:pre-dsl`・`pre-user-management`・`pre-user-admin` と並べる（今の戻し先は `pre-role-menu` と書いたうえで） | この段の片付けで、この3つのタグは外した（3節） | 今の戻し先の書き方は正しく、手順への影響は無い。前の Intent の手順書を開いたときに、そのタグがもう無いことに注意する |
| **配備の後のバックアップ**: `incident-plan.md` 5節は、配備の後（最終）の複写を `…202610142218-after-role-menu-final.tgz` とする | DSL の画面の直しの配備の後の複写は無い。最も新しい複写は入れ替えの直前の `…202610150703-pre-dslfix.tgz`（`redeploy-log.md` 3節の数えでは前回と同じ状態）。その後、配備したアプリのログに業務の行は無く、依頼者が画面を触っていない見込みのため、内部DB の中身は `pre-dslfix` と同じ見込み（監査の数は確かめていない） | RPO の上の影響は小さい見込み。次に配備したアプリを止めるとき（次の Intent の配備の前など）に複写を取る。Q2: A のとおり、この段では止めない |
| **ダッシュボードのファイルの状態**: `operation/observability-setup/dashboards.md` 1節と `alarms.md` 6節は、`docker/monitoring/dashboards/mastersmith-overview.json` の変更を「未コミット」と書く | その後 `c0f1995` でコミットされた。`redeploy-log.md` 2.3 で、指揮役の見込みに無い差として見つかった（WAR とイメージには入らない） | 記録の時点の違いで、ずれではない。直さない案 |
| **配備する版の記録**: `cd-config.md` 1節は配備する版を `005730a` とする | その後 `a16cd68`（S8 の直し）と `3f685c0`（DSL の画面の直し）を配備し直した。どちらもスキーマの変更が無く、`compose.yaml`・`Dockerfile`・`.env` は変えていない | 配備し直しによる記録の更新。古い記録は書き換えない |

## 3. この段で環境を変えたこと（Q3: B・F1: A）

この段の決まりは「コード・設定・環境を変えない」です（前の Intent と同じ。質問の記録の冒頭）。依頼者の答え（Q3: B・F1: A）と承認により、次の片付けだけを行いました。これは決まりとの差として記録します。

- 2026-10-15 11:36:55〜11:36:57（02:36:55〜02:36:57Z）に、`docker image rm` で `mastersmith` の古いタグ 16 個を外し、`docker builder prune -f` でビルドのキャッシュ 102.4MB を回収しました（一覧と前後の量は `cost-analysis.md` 5.1）。
- 配備した `local`、戻し先の `role-menu-before-dslfix`・`role-menu-before-s8fix`・`pre-role-menu` は残しました。
- 名前の無いボリュームと手元の監視のボリュームは残しました。
- 配備したアプリ・Mailpit・見本の対象DB は止めていません。片付けの後も配備したアプリは healthy です（1.1）。
- 影響: 前の Intent までの版へは、イメージでは戻せなくなりました。この Intent の戻しの手順（`operation/deployment-pipeline/rollback-runbook.md` の 2節・3節、`redeploy-log.md` 6節）が使う戻し先は残っているため、手順には影響しません。

## 4. 結論

- 動いている配備したアプリ・設定のファイル・戻し先・バックアップに、最後の配備し直しの記録とのずれはありません。
- イメージ・WAR・戻し先の違いは、配備し直し2回による記録の更新です（1.1 の「その時点の値」）。
- 記録として残すことは次のとおりです。
  - DSL の画面の直しの配備の後のバックアップが無いこと
  - 配備した版が `main` に入っていないこと（Q4: A で取り込む）
  - この段で片付けたこと（3節）
- 記録どうしの食い違いは2節のとおりです。どれも承認済みの記録は書き換えず、扱いを依頼者に諮ります。

## 5. 記録のコミット b2c4559 の CI の1回目の失敗（この段で扱った）

- CI の run 38671241833 の1回目（2026-10-15T02:44Z）が、`./gradlew verify` の結合テスト `SuspendWhileLoginIT`（a login of the user being suspended passes without waiting and its token is revoked by the suspension）の1件で失敗した（1165 件のうち 1 件）。b2c4559 は `aidlc/` の下の記録だけの変更で、アプリとテストのソースは 954028c（CI 成功）と同じ。
- 例外は、テストの待ち合わせの部品 `backend/src/test/java/cherry/mastersmith/useradmin/testsupport/TestUserAdminBarrier.java` の `waitingForUserRows()` が `INFORMATION_SCHEMA.SESSIONS` を読む問い合わせで、H2 の内部の `NullPointerException`（`this.transaction` が null、エラーコード 50000）。
- 見立て（未検証）: ほかのセッションのトランザクションが終わる瞬間に H2 がそのセッションの情報を読み、H2 の内部で競合が起きた。本番のコードの経路ではなく、テストの部品の問い合わせで起きた。
- 依頼者の決定（A: 時間を区切って再現→再実行）で、手元で 15 分を上限に `./gradlew :backend:integrationTest --tests '*useradmin*' --rerun` を繰り返した（2026-10-15T02:51:42Z〜03:07:42Z、`caffeinate -dims`）。11 回（useradmin の結合テスト 13 クラス）すべて通り、再現しなかった。その間の `pmset -g log` に Sleep・DarkWake は無かった。
- 依頼者が失敗したジョブを流し直し、2回目の試行は成功した（`gh run view 38671241833` で attempt 2・success）。`team.md` の決まりどおり、不安定と確かめられていない扱いで進める。同じテストが二度目に落ちたら、原因を直すまで次へ進まない（直し方の候補: 待ち合わせの問い合わせが H2 の内部の誤りで失敗したら「まだ待っていない」として問い直す形。テストだけの変更）。

## Sources

- `operation/feedback-optimization/feedback-optimization-questions.md`（冒頭の読み取りの結果、Q2: A・Q3: B・F1: A・Q4: A、確認済みの要約）
- `operation/performance-validation/redeploy-log.md`（2節〜6節）・`test-results.md`（1節）・`fix-notes.md`
- `operation/deployment-execution/deployment-log.md`（8節・11節・14節）・`health-check-report.md`・`smoke-test-results.md`
- `operation/environment-provisioning/environment-inventory.md`（2節〜7節）
- `operation/deployment-pipeline/cd-config.md`（1節）・`rollback-runbook.md`
- `operation/observability-setup/dashboards.md`（1節）・`alarms.md`（2.1・6節）
- `operation/incident-response/incident-plan.md`（5節）
- この段の片付けの記録（2026-10-15 02:36:55〜02:36:57 UTC、依頼者の承認済み）
- この段で実行したコマンドの出力（2026-10-15 02:39〜02:45 UTC）: `colima list`・`docker ps -a`・`docker inspect mastersmith-app-1`（値の出ない項目と環境変数の名前だけ）・`docker exec mastersmith-app-1`（`sha256sum /app/mastersmith.war`、`/proc/1/cmdline` の `-XX:` の件数）・`docker stats --no-stream`・`docker compose config --format json` を `jq` で上限・イメージ・環境の項目の数だけに絞ったもの・`docker compose config --quiet`・`docker compose config --profiles`・`stat`（`.env`・`.env.targetdb`）・`grep -c`（`.env.example` の名前ごと）・`git check-ignore`・`docker logs` と `jq`（行数・レベル・時刻・ロガー・Flyway の文・キーの名前・件数だけ）・`docker image ls mastersmith`・`docker images`・`docker system df`（`-v`）・`docker volume ls`・`docker volume inspect`（compose の project の印だけ）・`ls -l ~/.mastersmith-backup`・`shasum -a 256`（PC の WAR）・`git rev-parse`・`git rev-list --count main..develop`・`git diff --stat`（`aidlc/` の外）・`git status --porcelain`・`git submodule status`・`git log`（`compose.yaml`・`Dockerfile`・`.env.example`）・`gh run list --branch develop`

## Assumptions & Open Questions

- [assumption] DSL の画面の直しの配備の後の内部DB の中身は、入れ替えの直前の複写 `pre-dslfix` と同じと見ている（配備したアプリのログに業務の行が無いことからの見込み。監査の数は数えていない）。次にアプリを止めるときに複写を取って確かめる。
- 質問の記録の F1 の「57 個」の数え方は確かめられなかった（2節）。
