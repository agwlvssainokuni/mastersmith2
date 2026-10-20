# Deployment Pipeline の質問（Intent 261016-business-data-views）

この Intent（業務データの一覧・詳細・作成・更新・削除の画面と、持ち越しの直し。U1〜U5、Bolt B1〜B7）の配備の流れを決めるための質問です。前の Intent（`261015-deps-ui-carryover`・`261004-role-menu`）の配備の手順と、既にある `Dockerfile`・`compose.yaml`・README を正とし、今回の差で判断が分かれる点だけを尋ねます（`project.md` の Deployment の学び）。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。方式は1台の置き換え。本番配備の承認者は依頼者 | `team.md`・`project.md` の Deployment |
| 元の手順 | 前の Intent の手順（`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/` と、その元の `261004-role-menu` の同じ段）を正とし、今回の差だけを書く | `project.md` の Deployment の学び |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`sha256:f103aee1702a…`、前の Intent で配備した `main` の先頭 `6dd9bb0`、アプリのコードは `776738e`）で healthy。Mailpit（v1.31.5）と見本の対象DB（`postgres:18.6`、ボリュームは 2026-09-25 作成）も動いている。見本の PG は PC に番号を開けていない（E2E の上乗せの番号は閉じている）。colima の VM は CPU 4・メモリ 6GiB（2026-10-21 に読み取りだけで確かめた） | `docker ps`・`docker inspect`・`colima list`、前の Intent の `deployment-execution/deployment-log.md` |
| 配備する版 | `develop` の先頭（`7f2575c`）。`main` からの差は 39 コミットで、`aidlc/` の外を変えたのは 13 コミット（B2 `ea23bf3`、B3 の 5 つ `e34c260`〜`efa145d`、B1 `2360768`、B4 `04b0589`、B5 `1152202`、CI の直し `b7291d3`、B6 `a031ba1`、B7 `d220447`、Build and Test の直し `22de65f`）。アプリのコードは `d220447` と同じ（`22de65f` はテスト・`vitest.config.ts`・README だけ）。`main` は `develop` の祖先 | `git log main..develop`、`git diff d220447 develop -- . ':!aidlc'` |
| 変わるもの | 業務データの機能（`businessdata` の 113 ファイル、`targetdb`・`audit`・`role` の一部）、画面（`frontend/src` の 140 ファイル）、make-you-chic-ui の固定先 `b0a1e79`、axe-core 4.14.0、内部DB の移行 V13、`application.yaml`（一覧・詳細・書き込みの時間の上限、書き込みのアカウントとプールの設定）、`.env.example`・`.env.targetdb.example`、見本の初期化の台本 `03-writer-account.sh`（3種類）。`Dockerfile`・`compose.yaml` は変わらない | `git diff --name-only main develop` |
| 内部DB の移行 V13 | `audit_events` に空を許す3列（`target_schema`・`target_table`・`work_role_id`）を足すだけ。既存の表と行は変えない。前進のみで後方互換 | `backend/src/main/resources/db/migration/V13__u2_business_data_audit_columns.sql`、U2 の `infrastructure-design/infrastructure-specification.md` |
| 前の版と新しい監査の行 | 新しい版は監査に `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED`、結果 `UNKNOWN`、失敗の理由 `BUSINESS_*` を書く。前の版（`main`）の `backend/src/main` で `AuditEventRepository` を呼ぶのは `AuditEventRecorder` の `save` だけで、監査の行を読む経路は無い（2026-10-21 に `git grep` で読み取りだけで確かめた）。そのため、前の版に戻しても新しい値の行で失敗しない見込み（確かめ方は Q3） | `git grep`（`main` の `AuditEventRepository`・`AuditEventRecorder`） |
| `.env` に足す項目 | 書き込みの2項目 `MASTERSMITH_TARGET_DB_WRITE_USERNAME`（値は `mastersmith_writer`）・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD`。今の `.env` には無い（項目の名前だけを数えた。値は開いていない）。時間の上限（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL`・`_WRITE`、既定 5s）と書き込みのプールの上限（`MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE`、既定 5）は既定のままとし、足さない。片方だけ・両方無しのときは、起動と読み取りは続き、書き込みの API は 409 `BUSINESS_WRITE_NOT_CONFIGURED` になる（この2項目が書き込みの機能を入れ切りする実質の切り替えで、ほかの機能の切り替えの仕組みは持たない） | `.env.example`、`application.yaml`、README の「環境変数」の表 |
| `.env.targetdb` に足す項目 | `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`。今の `.env.targetdb` には無い（名前だけを数えた） | `.env.targetdb.example` |
| 見本の PG に要る作業 | 書き込みのアカウント `mastersmith_writer` は今の見本の PG に無い（`pg_roles` の件数 0）。初期化の台本 `03-writer-account.sh` は空のボリュームでだけ動くため、U2 の基盤の設計の Q4: A のとおり、ボリュームを残したまま README の「既にある見本の DB に書き込みのアカウントを足す（U2）」の手順で足す: (1) `.env` をリポジトリの外（`$HOME/.mastersmith-env-before-writer`、権限 600）へ複写、(2) 乱数のパスワードを表示せずに `.env.targetdb` と `.env` に足す、(3) 見本の PG のコンテナだけを作り直して新しい変数を渡す（データは残る）、(4) `docker compose exec` で台本を流す、(5) ロールの件数・付与・TEMP が無いことを件数で確かめる、(6) アプリを作り直す。`TEMPORARY` の取り上げは B6 で済んでいる（`has_database_privilege('public','business','TEMP')` が `f`） | U2 の `infrastructure-design/infrastructure-design-questions.md` Q4: A、`infrastructure-specification.md`、README、`docker exec` での件数の確かめ（値は出していない） |
| WAR | `Dockerfile` は手元の WAR を写すだけのため、配備の前に `./gradlew :backend:bootWar` で作り直し（最新の扱いで作り直されないときは `--rerun-tasks` などで作り直す）、SHA-256 と中身（`businessdata` のクラス、V13、画面のビルド）を確かめる | `project.md` の Deployment の学び、Build and Test の `memory-peak-measurement.md`（`bootWar` が作り直されなかった記録） |
| 配備の前の検査 | verify・CI・E2E は Build and Test と CI Pipeline の結果を正とし、流し直さない。E2E の全件 256 件は B7 の後に通っており、その後のアプリのコードの変更は無い（`team.md` の「リリースの前に E2E」はこれで満たす）。CI は `31d434c` まで確かめ済み（`05a9b37` の1回目の失敗は `ci-pipeline/ci-config.md` 6.2 に記録）。未 push の `7f2575c` は `aidlc/` だけ | `construction/build-and-test/build-and-test-summary.md`・`test-results.md`、`construction/ci-pipeline/ci-config.md` |
| 配備の前の k6 | 流さない。業務データの負荷の目標（Unverified 12 件）は Performance Validation が判定する（Build and Test の Q3: A）。`project.md` の「同じソースのイメージで試験済みのときだけ省く」との差を `cd-config.md` に記録する（前の Intent `261004-role-menu` の Q4: A と同じ扱い）。`dslMixed` の `memory.peak` は Build and Test で Met（0.873・0.870）。`RETENTION_TIME=0` は配備物に取り込まない（後の Intent で決める） | `build-and-test-questions.md` Q3、`memory-peak-measurement.md` |
| 戻し先のタグ | 入れ替えの前に、今のイメージに `mastersmith:pre-business-data-views` を付け、ID が `f103aee1702a` で始まることを確かめる | README の「戻し方」、前の手順 |
| 配備の前の内部DB の複写 | スキーマが変わる（V13）ため省かない。入れ替えの前にアプリを止めて `~/.mastersmith-backup/`（権限 700）へ複写し、配備の前のバックアップにする。`mktemp -d` は使わない | README の「内部DBのバックアップと戻し方」、`project.md` の学び |
| 戻しの基本の形 | イメージだけで戻す（V13 は残り、前の版は足した列を読まない）。`.env` の書き込みの2行・見本の PG の `mastersmith_writer`・`TEMPORARY` の取り上げは残す（前の版は使わない）。配備の前の複写を戻すのは、データが壊れたとき・移行が途中で失敗したときだけ（その後のデータと監査の行を失う）。見本の PG に書いた行は、どちらの戻しでも戻らない | U2 の基盤の設計 Q4: A、`team.md` の Deployment |
| 救済の見込み | 前の手順のとおり、入れ替えの前の複写で初期管理者の停止と印の有無を数え、依頼者に画面でパスワードを変えたかを尋ねる | 前の手順の順 4・5 |
| 監査に残る操作の扱い | スモークテストで監査に残るもの（ログイン・ロールの操作・作業ロールの切り替え・業務データの作成・更新・削除）は、行う前に依頼者に伝え、期待の件数をその場で合わせてから、配備の後の複写で種類と結果ごとに数える。値（メールアドレス・パスワード・業務データの値・主キーの値）は表示しない | `project.md` の Deployment・Corrections の学び、`AuditEventType`（2026-10-21 にソースで確かめた） |
| アプリが止まったとき | 前の手順のとおり（`ExitOnOutOfMemoryError`、`restart: "no"`、`docker compose start app`） | 前の手順の `rollback-runbook.md` 4節 |
| 入れ替えの後の画面 | 画面が大きく変わる（業務データの画面、行の操作の名前、axe-core）ため、入れ替えの後に開いているタブを読み込み直す | 前の手順 |
| `main` への取り込み | スモークテストが通った後、取り込みの前に前の取り込み（`6dd9bb0`）の後のすべてのコミットの CI を試行ごとに確かめ、依頼者の承認を得て `main` を fast-forward で取り込む。タグは付けない。`origin` への push は依頼者が行う | `team.md` の Deployment・Way of Working、`project.md` の学び |

## Q1. 書き込みのアカウントと `.env` の書き足しを、いつ行うか

決まっている手順（README の「既にある見本の DB に書き込みのアカウントを足す」）は、見本の PG のコンテナの作り直しとアプリの作り直しを含みます。入れ替えとの順を決めます。

A. 入れ替えの前の停止（内部DB の複写のためにアプリを止めている間）に行う。順は「アプリを止める → 内部DB を複写 → `.env` を外へ複写 → パスワードを `.env.targetdb` と `.env` に足す → 見本の PG のコンテナを作り直して台本を流し、件数で確かめる → 新しいイメージでアプリを入れ替える」。アプリの作り直しが1回で済み、入れ替えの直後から書き込みを確かめられる
B. 先に新しい版を書き込みの設定なしで入れ替え、読み取りの画面と 409 `BUSINESS_WRITE_NOT_CONFIGURED` を確かめた後に、README の手順で足してアプリをもう一度作り直す。新しい版と書き込みの設定の問題を切り分けやすいが、アプリの作り直しが2回になり、止まる時間が増える
C. 今回の配備では書き込みの設定を入れない（書き込みは 409 のまま）。書き込みはスモークテストで確かめられず、後で別に足す
X. Other (please specify)

推奨は A です。止まる時間が最も短く、見本の PG のコンテナを作り直す間にアプリが見本の PG を読みに行くことも無くなります。代わりに、入れ替えの後に問題が出たときは、新しい版と書き込みの設定のどちらが原因かを起動のログ（書き込みの設定の WARN の有無）で見分けます。

[Answer]: A

## Q2. パスワードの生成と `.env`・`.env.targetdb` への書き足しを、誰が行うか

README の手順のコマンドは、乱数のパスワードをシェルの変数に入れてファイルの末尾に足すだけで、値を画面に出しません。

A. AI が README の手順のコマンドをそのまま流す。`.env` と `.env.targetdb` は開かず、書き足した後は項目の名前と値が空でないことだけを数えて確かめる。依頼者は流す前にコマンドを確かめて承認する
B. 依頼者が README の手順のコマンドを自分の端末で流す。AI は、その後に項目の名前と件数だけで確かめる
X. Other (please specify)

推奨は A です。前の Intent（`260924-followup-fixes`）でも AI が値を表示せずに `.env` の行を扱い、流す前の承認で依頼者が中身を確かめられます。代わりに、秘密の値を作る操作を AI が行います。依頼者が自分で行いたい場合は B を選んでください。

[Answer]: A

## Q3. 戻しの練習（V13 の後の内部DB で前の版が動くか）を行うか

U2 の基盤の設計は「前の版は V13 を無視して起動する。戻しの確かめは Deployment の段で行う」としています。前の版に監査の行を読む経路が無いことはソースで確かめました（決まっていることの表）。練習の環境での操作は複写の中の監査にだけ残り、配備した内部DB は汚れません。

A. 配備とスモークテストの後に行う（前の Intent `261004-role-menu` の Q3: A と同じ形）。アプリを止めて配備の後の複写を取り（配備の後のバックアップと監査の数えを兼ねる。約 10 秒の停止）、すぐ起動し直す。複写を一時のボリュームに展開し、前の版のイメージ（`mastersmith:pre-business-data-views`）を別の名前のコンテナ・別の番号（例 18080）・`.env` の複写で起動して、次を確かめる。終わったら練習の環境と一時のボリュームを消す
  1. `/actuator/health` の 200 を待つ（`running` を健全と読まない）。Flyway が V13 を無視して起動し、ERROR が 0 件
  2. 依頼者がログインでき、ホームと管理の画面が開ける（スモークテストで残った `BUSINESS_ROW_*`・結果の値と新しい3列のある監査の行がある複写で、起動・ログイン・監査の書き込みが失敗しない）
  3. `.env` の書き込みの2行があっても、前の版の起動のログに誤りが出ない
B. 練習は行わず、ソースの確かめ（前の版は監査の行を読まない）と、前の Intent で前の版が未来の移行（V10〜V12）を無視して起動したことの実績で足りるとし、未確認のまま残る前提と次に確かめる機会を `rollback-runbook.md` に記録する
C. 入れ替えの前に、配備の前の複写に新しい版を一時の環境で起動して V13 を当て、その複写で前の版を起動して A の 1〜3 を確かめてから入れ替える。入れ替えの前に戻せると分かるが、実際の業務データの監査の行での確かめにはならない
X. Other (please specify)

推奨は A です。`team.md` の「1つ前の版のアプリが動く後方互換」を、配備した後の実際の状態（業務データの監査の行を含む）で確かめられ、配備の後の複写1回の停止にまとめられます。代わりに、練習で問題が見つかったときは新しい版が動いている状態から戻し方を考えることになります（前進のみの移行のため、急いで戻す必要は無い見込み）。

[Answer]: A

## Q4. 配備の後のスモークテストで、業務データをどこまで確かめるか

健全性と起動のログ（ERROR 0、V13 の1件が当たった、書き込みの設定の WARN が無い、秘密・メールアドレスそのもの・業務データの値がログに無い）、依頼者のログイン、監査の `LOGIN_SUCCEEDED` は、どの選択肢でも行います。業務データの画面を開くには、適用中の DSL に見本の `sales` のテーブルがあり、利用者の作業ロールにそのテーブルの権限があることが要ります。入れ替えの前の複写で、DSL・ロール・割り当ての行の数を数えて前提を確かめます（中身は表示しない）。依頼者が画面で行う操作です。

A. 依頼者が画面で、確かめ用のロールを2つ作り（作業ロールが自動で選ばれず、切り替えを確かめられるようにするため。`project.md` の学び）、一方に `sales.customers` の主権限 FULL と補助権限（作成・削除）を付けて両方を自分に割り当て、作業ロールを切り替える。業務のメニューから一覧を開き、検索・並べ替え・ページ送り・詳細を確かめ、架空の値の行を1件作成→更新→削除する（見本の PG に行を残さない）。ビュー `customer_orders` では行の更新・削除の操作が出ないことも見る。AI は、操作の前に合わせた期待の件数（`ROLE_*`・`ROLE_ASSIGNED`・`WORK_ROLE_SWITCHED`・`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED` の成功が各1件など）と、配備の後の複写の件数を比べる。作ったロールを残すか消すかは依頼者が決める
B. 読み取りだけを確かめる（既存のロールと作業ロールで一覧・詳細を開く。前提が無ければ A と同じロールの準備だけ行う）。書き込みは、定義の API の `writeConfigured` が `true` であることと、画面の作成のボタンが出ることだけで確かめ、行は書かない。監査に業務データの行は残らない
C. A に加えて、失敗の場合も1件ずつ確かめる（一意の値の重なり 409、権限の無いテーブルへ直接移ったときの 403 の表示）。失敗の監査（`BUSINESS_DUPLICATE_VALUE` など）が残る
X. Other (please specify)

推奨は A です。権限の設定から作業ロール・業務のメニュー・一覧・詳細・書き込み・監査の新しい3列までを、配備した環境で1回通して確かめられ、Q3 の練習の複写に業務データの監査の行が入ります。代わりに、監査に確かめの行が残ります。失敗の場合は結合テストと E2E で確かめ済みのため、A では入れません。

[Answer]: A

## Q5. 配備の記録と `main` への取り込みの順

前の Intent では、依頼者の決定で、配備の記録を `develop` にコミットしてから `main` を fast-forward しました（記録も `main` に入り、取り込みと push の結果は `main` の後の `develop` だけのコミットで追記）。

A. 前の Intent と同じく、配備の記録（Deployment Execution の記録）をコミットしてから `main` を `develop` の先頭へ fast-forward する。取り込みと push の結果は、その後の `develop` だけのコミットで追記する
B. 先に今の `develop` の先頭（この段とスモークテストの前の記録まで）で `main` を fast-forward し、配備の記録は `develop` にだけ積む。`main` の先頭は配備の確かめの前の記録になる
X. Other (please specify)

推奨は A です。`main` の先頭に配備の記録まで入り、どのコミットをリリースしたかを `main` の先頭と記録で一度に追えます。どちらでもアプリのコードは `d220447` と同じです。

[Answer]: A


## Sources

- 段の定義: `.claude/aidlc-common/stages/operation/deployment-pipeline.md`
- 前の手順: `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/`（`deployment-pipeline-questions.md`・`cd-config.md`・`rollback-runbook.md`・`deployment-strategy.md`）と `operation/deployment-execution/deployment-log.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/deployment-pipeline-questions.md`
- この Intent の基盤の設計: `construction/u2-businessdata-write/infrastructure-design/infrastructure-design-questions.md`（Q4: A）・`infrastructure-specification.md`・`cicd-pipeline.md` 6節、`construction/u1-businessdata-read/infrastructure-design/cicd-pipeline.md` 6節、`construction/u3-businessdata-screens/infrastructure-design/cicd-pipeline.md` 5節
- この Intent の Build and Test と CI: `construction/build-and-test/build-and-test-summary.md`・`build-and-test-questions.md`（Q1〜Q4）・`memory-peak-measurement.md`・`test-results.md`、`construction/ci-pipeline/ci-config.md`
- リポジトリ（読み取りだけ）: `Dockerfile`、`compose.yaml`（`targetdb-postgres`・`restart: "no"`）、`.env.example`・`.env.targetdb.example`、`backend/src/main/resources/application.yaml`、`backend/src/main/resources/db/migration/V13__u2_business_data_audit_columns.sql`、`docker/targetdb/postgres/03-writer-account.sh`、`docker/e2e/compose.targetdb-port.yaml`、`backend/src/main/java/cherry/mastersmith/audit/domain/`（`AuditEventType`・`AuditResult`・`AuditFailureReason`）、README（「コンテナでの起動と確認」「戻し方」「内部DBのバックアップと戻し方」「既にある見本の DB に書き込みのアカウントを足す（U2）」「環境変数」）、`git log main..develop`・`git diff main develop`・`git grep`（`main` の `AuditEventRepository`）
- 実行環境（読み取りだけ、2026-10-21）: `docker ps`、`docker images`、`docker inspect`（`mastersmith-app-1`・`mastersmith-targetdb-postgres-1`）、`docker exec` で見本の PG の件数（`mastersmith_writer` 0、`mastersmith_reader` 1、PUBLIC の TEMP `f`、E2E の一時のロールとスキーマ 0）、`colima list`、`.env`・`.env.targetdb` の項目の名前（値は開いていない）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment）、`aidlc/spaces/default/memory/project.md`（Deployment・Change Control・Corrections の学び）

## Consolidated Summary Confirmation

**cd-config**
- 前の Intent（261015-deps-ui-carryover・261004-role-menu）の配備の手順を正とし、この Intent の差だけを書く。配備の対象は `develop` の先頭（アプリのコードは d220447 と同じ）で、`Dockerfile`・`compose.yaml` は変わらない。WAR を作り直して SHA-256 と中身を確かめ、`mastersmith:local` を作る。配備の前の k6 は流さず Performance Validation に任せる（project.md の「試験済みのときだけ省く」との差を記録する）。B7 の後の E2E 256 件をリリースの前の E2E とする。
- 入れ替えの停止の間に、内部DB を複写し（バックアップを兼ねる）、`.env` を外へ複写してから書き込みの2項目（`MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD`）と `.env.targetdb` の書き込みのパスワードを足す。見本の PG のコンテナを作り直して README の「既にある見本の DB に書き込みのアカウントを足す」の台本を流し、件数で確かめてから入れ替える（Q1: A）。パスワードの生成と書き足しは AI が README のコマンドで行い、値は表示しない。流す前にコマンドを依頼者に見せて承認を得る（Q2: A）。
- `main` への取り込みは、配備の記録をコミットしてから `develop` を fast-forward する。タグは付けない。取り込みと push の結果は `develop` だけに追記する（Q5: A）。

**deployment-strategy**
- 入れ替えは停止して作り直す1台の入れ替え（前の Intent と同じ）。移行は V13（監査の3列、空を許す、前進のみ、後方互換）。救済の見込みと、監査の件数の合わせ方を手順に入れる。
- スモークテスト（依頼者が画面で行う）: ロールを2つ作り、一方に業務データのテーブルの FULL と作成・削除を付けて両方を自分に割り当て、作業ロールを切り替える。一覧・検索・並べ替え・ページ送り・詳細、架空の値の行の作成→更新→削除（行を残さない）、ビューで更新・削除の操作が出ないこと。AI は監査の件数を期待の件数と比べ、片付けは配備の後の複写で数えてから判定する（Q4: A）。

**rollback-runbook**
- 戻しはイメージだけ（前の版 `f103aee1702a` を `mastersmith:pre-business-data-views` として残す）。V13 の列・`.env` の書き込みの2項目・書き込みのアカウント・TEMPORARY の取り上げは戻しても残す。見本の PG に書いた行は戻らない。内部DB を元に戻す必要があるときは入れ替えの前の複写を戻し、その後のデータと監査の行を失うことを書く。
- 戻しの練習を、配備とスモークテストの後に、配備の後の複写で前の版を別のコンテナ・番号で起動して行う（health 200・ERROR 0・ログインと画面・`.env` の書き込みの2項目があっても誤りが出ない）（Q3: A）。

Does this all look correct before I generate the deployment pipeline artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
