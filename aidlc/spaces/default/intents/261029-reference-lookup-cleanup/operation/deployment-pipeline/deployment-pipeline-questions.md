# Deployment Pipeline の質問（Intent 261029-reference-lookup-cleanup）

この Intent（他テーブル参照の REFERENCE・LOOKUP の読み取り・保存前の確かめ・画面、perf の記録の伏せ字の道具、コンテナと perf の文書の整理）の配備の流れを決めるための質問です。前の Intent（`261027-perf-targets-review`）の配備の手順（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）と、既にある `Dockerfile`・`compose.yaml`・README を正とし、今回の差で判断が分かれる点だけを尋ねます（`project.md` の Deployment の学び）。各単位の基盤の設計（`construction/u*/infrastructure-design/cicd-pipeline.md`）が「Deployment Pipeline で決める」と残した点もここで決めます。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。本番配備の承認者は依頼者。1つのコンテナを入れ替える形（ブルーグリーン・カナリアは使わない） | `team.md`・`project.md` の Deployment、U1 `cicd-pipeline.md` 3節 |
| 元の手順 | 前の Intent `261027-perf-targets-review` の手順を正とし、今回の差だけを書く | `project.md` の Deployment の学び |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`sha256:e0ede6ddd5c4…`、10-27 に作成）で healthy。見本の PG（`mastersmith-targetdb-postgres-1`、`postgres:18.6`）と Mailpit（`v1.32.0`）が動き、lgtm（`0.38.0`）は止まっている | `docker ps -a`・`docker inspect`（読み取り） |
| 配備する版 | `develop` の先頭 `23519159`。`main`（`d797ef74`）から 64 コミット先。`aidlc/`・`.claude/` の外の差は、`backend/src/main`（`businessdata` の参照の読み取り・保存前の確かめ、`targetdb` の目的 `REFERENCE` と2つの設定）、`frontend/src`（参照の部品）、`frontend/package.json`・lockfile（i18next・React など）、`vendor/make-you-chic-ui` の固定先（`ecb07e9` → `cab4207`）、`build.gradle.kts`（perf の道具の検査）、`perf/`、`README.md`・`.env.example`（コメントだけ）、E2E（180・190・195） | `git diff --stat main..develop` |
| 内部DB の移行 | **無い**（`backend/src/main/resources/db` の差 0。Flyway は V13 のまま） | `git diff main..develop --stat -- backend/src/main/resources/db` |
| `.env` | **変えない**。足した2つの設定 `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`（既定 5s）・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（既定 10s）は既定で動く。`.env.example` にはコメントアウトの2行と説明だけを足した。`.env` は開かず、2つの項目の有無だけを数える | U1 `cicd-pipeline.md` 3節 順 1（Q1: A） |
| `compose.yaml`・`Dockerfile` | アプリの分の変更は無い（`Dockerfile` は手元の WAR を写すだけ）。Mailpit・lgtm は U5 で `compose.yaml` の版にそろえ済み | `git diff main..develop`、U5 の記録 |
| 監査の種類 | `AuditEventType` に**新しい種類は無い**。保存前の参照の確かめの拒否は既存の `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED` の `FAILURE`（理由 `BUSINESS_REFERENCE_NOT_FOUND`。`BusinessDataAuditReason.of`）に残る。参照先の読み取り（`POST /api/tables/definition` の `CREATE`・`UPDATE`、`POST /api/tables/reference/rows`）は監査に残らない | `audit/domain/AuditEventType.java`・`businessdata/domain/BusinessDataAuditReason.java`（読み取り） |
| WAR | `develop` の先頭から作り直し（`./gradlew :backend:bootWar`）、SHA-256 を記録し、WAR の中の `application.yaml` に2つの鍵と既定があることを確かめる。手元の WAR（`4cd5c416…`、11-01 12:26 に Tooltip の直しのブランチで作成）はそのまま使わない | U1 `cicd-pipeline.md` 3節 順 3、`project.md` の学び（WAR の確かめ） |
| 入れ替え | `docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID の一致を確かめ、食い違えば `--force-recreate --no-deps` で作り直す。入れ替えの前に今のイメージ（`e0ede6ddd5c4`）に戻し先のタグ `mastersmith:pre-reference-lookup-cleanup` を付ける | `project.md` の学び、前の Intent と同じ |
| 起動の確かめ（R-01） | healthy と `/actuator/health` の 200 に加え、起動のログに `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` の点検の WARN が 0 件であること（項目名で数え、値は出さない）と、既存の業務データの一覧が 200 で返ることを**必ず**確かめる（2つの設定の誤りは対象DB 全体を使えなくし、ヘルスチェックの 200 では見分けられないため）。救済の WARN 0 件・ERROR 0 件・Flyway の `Migrating schema` 0 件も数える | U1 `infrastructure-specification.md` R-01、`cicd-pipeline.md` 3節 順 5・順 6 |
| 戻し | **イメージだけ**（`mastersmith:pre-reference-lookup-cleanup` で `app` を作り直す）。`.env` は変えない。U1・U2・U3 は同じ WAR とイメージに入るため必ず一緒に戻る（U3 だけ・サーバー側だけの戻しは無い）。戻すと、作成・更新の画面の参照のカラムは前の形（文字の入力・選択）に戻り、保存前の参照の確かめも無くなる（外部キーの無い参照先へ参照先に無い値を保存できる前の状態）。2つの設定の誤り（1 秒未満）のときは、イメージは替えず `.env` の該当の行を値を表示せずに消して作り直す。戻しの練習は、移行と `.env` の変更が無いため行わない | U1・U2・U3 `cicd-pipeline.md` 4節・5節 |
| 配備の前の検査 | verify・CI は Build and Test と CI Pipeline の結果を正とし流し直さない。固定先の更新（`cb23466b`）の後に、短命のブランチで verify と E2E 277 件が通り、`cb23466b` の CI は1回目で成功。その後のコミット（`1e8e42bb`・`23519159`）は `aidlc/` だけ | `ci-config.md` 6.4節 |
| 見本の PG | E2E の口の重ね合わせ（`docker/e2e/compose.targetdb-port.yaml`、`127.0.0.1:15432` だけ）のまま動かす。配備したアプリは compose の網で届くため、配備に影響しない | `docker port`（読み取り）、Build and Test の記録 |
| `main` への取り込み | 依頼者の承認を得て、`develop` から fast-forward で行う（Deployment Execution）。タグは付けない。取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる（`23519159` の CI は今は実行中）。`git push` は依頼者が行う | `team.md`、`project.md` の学び |
| 秘密・個人に関する値 | `.env` の値、初期管理者のメールアドレス、業務データの行の値を表示しない。ログと複写は件数・キーの名前だけで見る。内部DB の複写を取るときはリポジトリの外（`~/.mastersmith-backup/`、置き場 700・ファイル 600） | `project.md` の Forbidden と学び |

## Q1. スモークテストの中身（参照の項目の確かめ）

各単位の基盤の設計は、参照の読み取り・保存前の確かめ・画面をスモークテストでどこまで確かめるか、見本の対象DB に参照の項目を持つ表を足すかを、この段に残しました。

調べた事実:

- 見本の PG には外部キー `sales.orders.customer_id → sales.customers` があり、既定の DSL の生成（`DslTreeBuilder`）は1カラムの外部キーを `select`・選択肢の元 `REFERENCE` にする。適用中の DSL が既定の生成のものなら、`orders` の作成・更新の画面で `customer_id` が参照の選択になる（見本の PG の表を足す必要は無い）。
- LOOKUP（参照ピッカー）は既定の生成では作られず、DSL を手で書いて投入・適用しないと出ない。LOOKUP の流れと axe は E2E 190・195 で確かめ済み。
- 保存前の確かめの 409（参照先に無い値）は、画面の選択からは作れない（選べるのは参照先にある値だけ）。結合テストと E2E で確かめ済み。
- 初期管理者には、前の Intent の配備の時点で、グループ経由のロールの割り当て3つと作業ロールの保存があった（`project.md` の学び）。参照の選択肢は、作業ロールが参照先（`customers`）を読めるときだけ返る。

**推奨: A。** 理由: 既にある外部キーと既定の DSL で、参照の読み取り（選択肢）・保存前の確かめ（成功の経路）・画面の部品を、見本の PG と適用中の DSL を変えずに1回の流れで確かめられます。B は DSL の投入と適用を2回行い（監査に `DSL_SUBMITTED`・`DSL_APPLIED` が各2件残り、適用中の DSL の履歴も増える）、E2E 190・195 で確かめ済みの LOOKUP を重ねて見るだけになります。C は R-01 の確かめだけで、この Intent の中心の機能を配備した版で一度も触れません。

A の流れ（前の Intent と同じ「乙」だけを作る形）:

| 順 | 項目 | 誰が | 確かめ |
|---|---|---|---|
| S1 | healthy・起動のログ | AI | 「決まっていること」の起動の確かめ（R-01 の WARN 0 件を含む） |
| S2 | 既存の業務データの一覧 | 依頼者 | 今の作業ロールで `customers` の一覧が開く（200）。R-01 の必須の確かめ |
| S3 | ★ロール「スモーク確認乙」 | 依頼者 | 作成し、権限の設定で `sales.orders` に主権限 `FULL` と補助権限（作成・削除）、`sales.customers` に `READ` を付けて保存（1回） |
| S4 | ★割り当てと切り替え | 依頼者 | 自分（初期管理者）に乙を割り当て、作業ロールを乙に切り替える |
| S5 | ★参照の選択肢（作成） | 依頼者 | `orders` の「作成」で `customer_id` が選択になり、見本の顧客2件のラベルが文字として出る。架空の値（`order_id` 9001・今日の日付・`total` 0）と1件目の顧客で保存し、詳細に顧客のラベルが出る |
| S6 | ★参照の変更（更新） | 依頼者 | 「更新」で `customer_id` を2件目の顧客に変えて保存し、詳細に変えたラベルが出る（値が変わった参照だけを確かめる経路） |
| S7 | ★削除 | 依頼者 | 作った行を消し、一覧に無い（見本の PG に残さない） |
| S8 | ★片付け | 依頼者 | 作業ロールを元のロールへ戻し、乙の割り当てを外して乙を消す |
| S9 | 見本の PG と監査の確かめ | AI | `sales.orders` の行の数が S2 の前と同じ（件数だけ）。配備の後の内部DB の複写（Q3）で T0 以降の監査を種類と結果ごとに数える |

A の監査の期待（★の操作を行う前に依頼者に伝え、予定に無い操作をしたときはその場で聞いて合わせる）: `ROLE_CREATED` 1・`ROLE_PERMISSION_CHANGED` 1（保存した回数）・`ROLE_ASSIGNED` 1・`WORK_ROLE_SWITCHED` 2（元 → 乙 → 元）・`BUSINESS_ROW_CREATED` 1（`SUCCESS`）・`BUSINESS_ROW_UPDATED` 1（`SUCCESS`）・`BUSINESS_ROW_DELETED` 1（`SUCCESS`）・`ROLE_UNASSIGNED` 1・`ROLE_DELETED` 1・`LOGIN_SUCCEEDED` はログインした回数（0 もありうる）。`FAILURE`・`UNKNOWN` 0、`BUSINESS_ROW_*` の `work_role_id` は空でない。参照の読み取りは監査に残らない。

前提（[assumption]）: 適用中の DSL に `sales.orders`・`sales.customers` があり、`orders.customer_id` が `REFERENCE` の選択である。S5 で選択にならないときは、その場で止めて、S5〜S7 を `customers` の作成・更新・削除（前の Intent の形）に置き換えるかを伺う（DSL は変えない）。

A. 既定の DSL の参照（`orders.customer_id`）で、乙を作って作成・更新・削除の流れを確かめる（上の S1〜S9）。409 と LOOKUP は結合テストと E2E で確かめ済みとし、差を `cd-config.md` に記録する
B. A に加えて、`orders.customer_id` を LOOKUP にした DSL を投入・適用してピッカーを確かめ、終わったら元の DSL を投入・適用して戻す（監査に `DSL_SUBMITTED`・`DSL_APPLIED` が各2件増える）
C. 参照の操作は行わない。S1・S2（R-01 の確かめ）と業務データの一覧・詳細の表示だけにする（ロールは作らない。監査に残るのはログインだけ）
X. Other (please specify)

[Answer]: A

## Q2. 配備の前の k6

Build and Test は、参照の6場面の試し走り（1 VU・10 秒）と種の確かめまでで、判定の回（10 VU・3 分、合否の回 2 回）は Performance Validation（この Intent の後の段）に回しました（Build and Test の Q3: A）。`project.md` の学びは「Build and Test で配備と同じソースを負荷の試験済みのときだけ配備の前の k6 を省く」で、今回は当たりません。前の Intent `261016-business-data-views` も同じ状況で流さず Performance Validation に任せ、差を `cd-config.md` に記録しました。

**推奨: B。** 理由: 判定の回は3種類の対象DB で配備したアプリを数時間止める重さがあり、Performance Validation と同じ回を二度流すことになります。移行と `.env` の変更が無く、戻しはイメージだけで約 1〜2 分で済むため、配備の後に問題が出ても戻しで足ります。試し走りでは18個の summary json がそろい、終了コードはすべて 0 でした。

A. 配備の前に PostgreSQL だけで参照の6場面の判定の回（10 VU・3 分）を使い捨ての環境で流し、満たしてから入れ替える（配備したアプリを約 30〜40 分止める）
B. 流さず Performance Validation に任せる。`project.md` の「試験済みのときだけ省く」との差を `cd-config.md` に記録し、配備の後に問題が出たらイメージで戻す
X. Other (please specify)

[Answer]: B

## Q3. 内部DB の複写

移行が無いため、配備の前の複写は戻しに要りません（戻しはイメージだけ）。一方、Q1 で A・B を選ぶと監査の件数を数えるために配備の後の複写が要ります（アプリを止めて複写し、読み取りで開く。前の Intent までの形）。配備の前の複写は、初期管理者のロールの割り当てと作業ロールの保存の数（S3〜S8 の前提）と T0 の基準を値を出さずに数えるのにも使えます。

**推奨: A。** 理由: 配備の前の停止は入れ替えの停止に重ねられるため止まる時間はほぼ増えず、スモークテストの前提（割り当て・作業ロール）を画面に頼らず数えられ、念のためのバックアップにもなります。配備の後の複写は監査の確かめとバックアップを兼ねます。Q1 が C なら監査の確かめの複写も要らないため、C を選んでください。

A. 配備の前（入れ替えの停止の間）と配備の後（スモークテストの後）の2回、複写を取る。前は前提と T0 の数え、後は監査の確かめに使い、どちらもバックアップを兼ねる（ファイル 600）。戻しの練習はしない
B. 配備の後の1回だけ取る（監査の確かめとバックアップ）。前提は画面で依頼者に確かめてもらう
C. 複写を取らない（Q1 が C のとき）
X. Other (please specify)

[Answer]: A

## Q4. 試験用タグ `mastersmith:reference-lookup-cleanup` の片付け

Build and Test の試し走りで作ったイメージ（`eab1676f26de`、`df79634c` の WAR。後の Tooltip の直し `cb23466b` を含まない）です。Performance Validation は配備したイメージで流すため、このイメージは使いません。どのコンテナも使っていません。

**推奨: A。** 理由: 配備する版と中身が違い、残すと Performance Validation で取り違えるおそれがあります。PC のディスクの使用は高いままです（前の Intent までの記録）。

A. 配備の段の終わり（Deployment Execution の後）に `docker rmi mastersmith:reference-lookup-cleanup` で消す（実行の直前にどのコンテナも使っていないことを確かめる）
B. 残す
X. Other (please specify)

[Answer]: A
