# 配備の方式（deployment-strategy）

方式は前の Intent と同じ**入れ替え（止めてから新しい版で起動する）**である（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 1節）。アプリのコンテナは1台で、組み込みの H2 のファイルを2つのプロセスから開けないため、ブルー/グリーン・カナリア・順次の入れ替えは採らない。見本の対象DB（PostgreSQL）はアプリの外の DB で、アプリと一緒に起動するだけで入れ替えの対象ではない。本書は今回の配備の手順を書く。決定は `deployment-pipeline-questions.md`、構成は `cd-config.md` にある。

## 1. 変えないもの

- 配備は依頼者の承認のうえで手で行う。自動の配備は持たない。
- 配備の前の関門は、統合の前の関門と同じ `./gradlew verify`。
- **ボリュームを消す操作（`docker compose down -v` など）はしない。** 内部DB の監査ログも一緒に消える。
- `.env` の値（秘密情報を含む）は、どの手順でも表示しない。AI が行う確かめは、項目を数えること（`grep -c '^KEY=.' .env`）だけにする。

## 2. 配備の手順（手で行う）

| 順 | 手順 | コマンド | 通る条件 |
|---|---|---|---|
| 1 | 作業ツリーにアプリのソースの未コミットの変更が無いことを確かめる | `git status --porcelain` | アプリのソースに変更が無い（ワークフローの記録と監査ログのディレクトリは除いて判断する。`project.md` の Deployment） |
| 2 | 配備する版のハッシュを控える | `git rev-parse --short HEAD` | 値を控えた（`037a33b` 以降の `develop` の先頭） |
| 3 | 検査を通して WAR を作る | `./gradlew verify` | 成功し、`backend/build/libs/mastersmith.war` がある。コンテナの実行環境が無いときの警告（対象DB のテストを飛ばした）が出ていない |
| 4 | 依頼者が `.env` に9項目を入れる（`cd-config.md` 3節。値は AI に見せない） | 依頼者が編集する | — |
| 5 | 入ったことを数で確かめる（値は表示しない） | `for k in MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD MASTERSMITH_TARGET_DB_TYPE MASTERSMITH_TARGET_DB_HOST MASTERSMITH_TARGET_DB_PORT MASTERSMITH_TARGET_DB_DATABASE MASTERSMITH_TARGET_DB_SCHEMA MASTERSMITH_TARGET_DB_USERNAME MASTERSMITH_TARGET_DB_PASSWORD; do echo "$k $(grep -c "^$k=." .env)"; done` | 9項目がすべて 1。あわせて `grep -c '^MASTERSMITH_DB_URL=' .env` が 0（既定の `;DEFRAG_ALWAYS=TRUE` が効く） |
| 6 | 起動するサービスを確かめる（値は表示しない） | `docker compose --profile targetdb-postgres config --services` | `app` と `targetdb-postgres` がある。上限は起動の後に手順 13 で確かめる |
| 7 | 今動いている版のイメージに戻し先のタグを付ける（Q3: A） | `docker tag mastersmith:local mastersmith:pre-dsl` | `docker image inspect mastersmith:pre-dsl --format '{{.Id}}'` が `sha256:8441534a745f…`（`10742a3` のイメージ）と同じ |
| 8 | 動いているアプリを止め、内部DB のボリュームをホームの下へ複写する（Q2: A） | 下の「手順 8 のコマンド」 | `~/.mastersmith-backup/mastersmith-data-<日時>-before-dsl.tgz` ができた。置き場の権限は 700 |
| 9 | アプリのイメージを作り直し、アプリと見本の対象DB を起動する（F2: A） | `docker compose --profile targetdb-postgres up -d --build` | エラーなく終わる。`mastersmith:local` が新しいイメージになり、`mastersmith:pre-dsl` は元の ID のまま |
| 10 | 健全になるまで待つ | `docker compose --profile targetdb-postgres ps` | `app` が `healthy`（最長で約 2 分）、`targetdb-postgres` が `Up` |
| 11 | 見本の対象DB が作られたことを確かめる | `docker compose logs targetdb-postgres` | 初期化の台本（`01-sample-schema.sql`・`02-reader-account.sh`）が誤りなく走り、`database system is ready to accept connections` が出ている。`ERROR` と「READER_PASSWORD が空です」の案内が無い |
| 12 | 内部DB のスキーマが V6 まで進んだことを確かめる | `docker compose logs app` を Flyway のログで絞る | V5・V6 の2つを当て、版が 6 になったことが出ている（例: `Successfully applied 2 migrations ... now at version v6`） |
| 13 | 上限を確かめる | `docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'`、`docker inspect mastersmith-targetdb-postgres-1 --format '{{.HostConfig.Memory}}'` | アプリが `2147483648 4000000000`、見本の DB が `536870912` |
| 14 | 手でスモークテストを行う | 3節 | すべての項目が通る |
| 15 | 配備の完了 | — | 手順 2 のハッシュが、いま動いている版の記録になる |

手順 8 のコマンド（README の「内部DBのバックアップと戻し方」の置き場をホームの下に替えたもの）:

```bash
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-before-dsl.tgz -C /data .
ls -l ~/.mastersmith-backup/     # ファイルができたこと（中身は開かない）
```

- 手順 8 では、止めたアプリを起動し直さない（手順 9 で新しい版を起動する）。止まっている間（数十秒〜2分）に届いた要求は失敗する。
- 置き場を `mktemp -d` の一時ディレクトリにしない。colima の VM から見えず、複写が空振りする（`project.md` の Corrections）。
- 手順 9 は `--profile targetdb-postgres` を必ず付ける。付けないと見本の対象DB が起動しない。アプリは起動のときに対象DB に接続しないため、起動の順序（`depends_on`）は要らない。
- 手順 11 で、パスワードが空のまま初めて起動して見本の DB が作れなかったときは、`.env` を直し、`rollback-runbook.md` 6節で見本の DB だけを作り直す（アプリは戻さない）。
- 手順 12 で使う Flyway のログの文言は、Deployment Execution で実際のログの項目を見て確かめる（1行1件の JSON の `message` にある見込み）。

## 3. スモークテスト

前の Intent（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 3節）の7項目に、DSL の管理画面と見本の対象DB からの生成を足す。

| 項目 | 確かめ方 | 合格の基準 | 行う人 |
|---|---|---|---|
| 健全性 | `docker compose --profile targetdb-postgres ps`、または `curl -s http://localhost:8080/actuator/health` | `healthy`／`{"status":"UP"}` | AI |
| ログイン画面 | ブラウザで `http://localhost:8080/` | ログイン画面が表示される | 依頼者 |
| ログイン | 初期管理者のメールアドレスとパスワードでログイン | ホームが表示される | 依頼者 |
| 管理者向け領域 | サイドバーの「管理」を開く | 管理者向け領域が表示される | 依頼者 |
| DSL の管理画面を開く | サイドバーの「DSL」を開く（`/admin/dsl`） | 画面が表示され、エラーの知らせが出ない | 依頼者 |
| 今の状態の取得 | 画面の上部の「今の状態」（`GET /api/admin/dsl/status`） | 表示される（初めての配備のため、適用中の版とプレビューはどちらも無い表示になる見込み） | 依頼者 |
| 見本の対象DB からの既定の DSL の生成 | 「スキーマを読み込む」→ 確かめる表示で進める（`POST /api/admin/dsl/preview/generate`） | 成功し、プレビューのタブに見本のスキーマ `sales` のテーブル（`customers`・`orders`・`order_items`・記号を含む名前の表）とビュー（`customer_orders`）が並ぶ。「未設定」（`TARGET_DB_UNCONFIGURED`）・「接続できない」（`TARGET_DB_UNAVAILABLE`）にならない | 依頼者 |
| ログアウト | ユーザーメニューのログアウト | ログイン画面に戻る | 依頼者 |
| ログ | `docker compose logs app` | ERROR が無い。1行1件の JSON のまま。生成の INFO（`DSL の操作を終えました`、`dsl.operation` が `generate`、`dsl.outcome` が `success`）が1件ある。対象DB の設定の WARN（項目の名前だけの WARN）が無い | AI |
| ログに接続情報が無い | アプリのログの中の、接続先 `targetdb-postgres` とユーザー名 `mastersmith_reader` の数を数える（コマンドは表の下） | どちらも 0（接続先・ユーザー名をログに出さない。`project.md` の Forbidden） | AI |
| 監査イベント | README の「監査ログの確かめ方」の手順で内部DB を複写し、複写したファイルを読み取りで開く（置き場は手順 8 と同じホームの下） | スモークテストの `LOGIN_SUCCEEDED`・`DSL_GENERATED`（`actor_user_id` あり、`dsl_source` が `GENERATED`）・`LOGGED_OUT` の3件がある | AI |

ログに接続情報が無いことの確かめのコマンド:

```bash
docker compose logs app | grep -c -e targetdb-postgres -e mastersmith_reader    # 0 であること
```

- ログインなど、パスワードが要る操作は依頼者が行う。AI は監査イベントとログで裏付け、個人に関する値（メールアドレス・接続元IP）は表示せず、値の有無だけを確かめる（`project.md` の Corrections）。
- **スモークテストの要求（ログイン・生成・ログアウト）は監査ログに残り、追記だけで消せない。** 送る前に依頼者に伝える（`project.md` の Corrections）。
- 監査イベントの確かめではアプリを止める（止めると H2 のファイルが詰め直される。約 1 秒）。止める前に依頼者に伝え、確かめた後は `docker compose start app` で起動し直して `healthy` を確かめる。
- 生成で置いたプレビューは残してよい（適用はスモークテストに含めない）。破棄するときは画面の「破棄する」で行い、監査に `DSL_PREVIEW_DISCARDED` が1件足されることを依頼者に伝える。
- 照合（プレビューの表示の中の対象DB との食い違いの確かめ）は、生成の直後のプレビューで警告が 0 件になる見込み。警告が出ても配備の合否にはしない（記録して依頼者に伝える）。

## 4. 中止して戻す条件

前の Intent（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md` 4節）と同じ条件（約 2 分で `healthy` にならない、起動が失敗する、スモークテストが通らない）に当たったら、`rollback-runbook.md` で戻す。今回の変更に固有の扱いは次のとおり。

| 条件 | 見方 | 扱い |
|---|---|---|
| Flyway の V5・V6 が失敗して起動が止まる | `docker compose logs app` | `rollback-runbook.md` 4節（データも戻す）。失敗した移行の記録が残った内部DB では、古い版も Flyway の確かめで起動できない見込みのため |
| 起動のログに ERROR（`適用中の DSL を読めないため…`）が出る | `docker compose logs app` | 初めての配備では適用の履歴が無いため出ない見込み。出たら記録し、依頼者と戻すかを決める |
| 見本の対象DB が起動しない・作れない（パスワードが空など） | 手順 10・11 | アプリは戻さない。`.env` を直し、`rollback-runbook.md` 6節で見本の DB だけを作り直す |
| 生成が `TARGET_DB_UNCONFIGURED`・`TARGET_DB_UNAVAILABLE` になる | 3節、`docker compose logs app` の WARN（項目の名前、原因の種類と SQLState だけ） | 設定の誤りとして `.env` を直し、`docker compose up -d app` でアプリのコンテナを作り直す（版は戻さない）。直らなければ依頼者と戻すかを決める |
| 生成以外の新しい機能だけに不具合がある（ログイン・管理は動く） | 3節 | 依頼者が戻すかを決める（戻すと DSL の管理が使えなくなる） |

設定の誤り（`.env` の値など）が原因とはっきりしている場合は、版を戻さずに設定を直して手順 9 からやり直してよい（前回の構成と同じ）。

## 5. 運用の注意（既知の制約 U4-STORAGE-RUN）

- 動いている間は、DSL の投入と適用のたびに内部DB のファイルが本文の大きさの分（10MB の DSL なら約 10.8MB）ずつ増え、頭打ちにならない。止めると `;DEFRAG_ALWAYS=TRUE` で詰め直され、起動し直した後は小さくなる（Build and Test の実測で約 16MB。止めるのにかかる時間は約 1 秒）。
- 大きな DSL の投入と適用を何度も重ねたときは、ディスクの空きを確かめ、`docker compose restart app` などでアプリを起動し直す（README の「DSL の管理の API（U4）」）。見本の対象DB は起動し直さなくてよい。
- 依頼者が Build and Test で受け入れた制約で、配備を止める条件にはしない。動いている間のメモリ（`anon` が上限 2g の約 92% に達した実測）とあわせて、performance-validation の段で確かめる（NFR1.12・U4-POOL と同じ段）。

## 6. DB のスキーマ

- 内部DB は Flyway の V5・V6 が起動のときに当たる。どちらも表と NULL を許す列を足すだけで、前進のみ・後方互換の決まり（team.md の Deployment）を守る。
- 戻すときにスキーマを元に戻す手順は持たない。古い版が V5・V6 の当たった内部DB で起動できること（U4-MIGRATION）は Deployment Execution で確かめる（`rollback-runbook.md` 3節）。
- 見本の対象DB のスキーマ `sales` は初めて起動したときに作られ、アプリは読み取りだけで変えない。

## 7. 引き継ぐもの

| 事項 | 持ち主の段 |
|---|---|
| 見本の対象DB の起動の前提（VM の空き、ボリュームが無い状態からの初めての作成、`.env` の9項目）を実際の環境で確かめる | environment-provisioning |
| 本書の手順での配備、V5・V6 の確かめ、スモークテスト、U4-MIGRATION の確かめ | deployment-execution |
| 動いている間のメモリと内部DB のファイルの増え方（U4-STORAGE-RUN）、NFR1.10・NFR1.12・U4-POOL | performance-validation |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1〜Q3、F1・F2、要約）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260923-colima-spec-up/operation/deployment-pipeline/deployment-strategy.md`、`aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/infrastructure-design/cicd-pipeline.md`（2節、スモークテスト）、`aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u1-target-db/infrastructure-design/cicd-pipeline.md`（3節）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-summary.md`（U4-STORAGE-RUN・U4-STORAGE-RESTART・U4-MIGRATION）
- `compose.yaml`、`Dockerfile`、`README.md`（「コンテナでの起動と確認」「内部DBのバックアップと戻し方」「手元で試す対象DB（compose の profile）」「DSL の管理の API（U4）」「DSL の管理画面（U5）」「監査ログの確かめ方」）、`docker/targetdb/postgres/01-sample-schema.sql`・`02-reader-account.sh`

## Assumptions & Open Questions

- [assumption] 手順 12 の Flyway のログの文言と、手順 11 の PostgreSQL の起動のログの文言は、ライブラリとイメージの既定の出力からの見込みで、この環境では確かめていない。Deployment Execution で実際のログを見て確かめる。
- [assumption] スモークテストの「今の状態」は、初めての配備で適用中の版とプレビューが無い表示になる見込み。内部DB に DSL の記録が無いことはボリュームを開いて確かめていない（今の版 `10742a3` には DSL の表が無いため、無い見込み）。
