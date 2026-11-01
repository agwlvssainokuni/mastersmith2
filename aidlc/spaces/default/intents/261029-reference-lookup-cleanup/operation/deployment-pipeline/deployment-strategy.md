# 配備の方式とスモークテスト（deployment-strategy）

Intent 261029-reference-lookup-cleanup。流れは `cd-config.md` の 3節。前の Intent（`261027-perf-targets-review`）の方式と同じで、差は起動の確かめに2つの新しい設定の点検を足したこと（U1 の R-01）と、スモークテストで参照の項目を確かめること（Q1: A）。

## 1. 方式

- **1 台の置き換え**（recreate）。`docker compose --profile targetdb-postgres up -d --build app` でアプリだけを入れ替え、イメージ ID の一致を `docker inspect` で確かめる（食い違えば `--force-recreate --no-deps app`）。ブルー・グリーンやカナリアは、PC 上の 1 台のコンテナでは意味がないため使わない。
- 環境の段（dev → staging → prod）は持たない。本番配備の承認者は依頼者。承認は、アプリが止まる順 5〜7 の前に 1 回（`cd-config.md`）。
- 内部DB のスキーマ変更は無い（V13 のまま、前進のみ・後方互換）。機能フラグは使わない（参照の機能は DSL の `REFERENCE`・`LOOKUP` の定義があるときだけ働き、定義が無い表の動きは前と同じ）。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき。通るまで完了とはみなさない。

## 2. 起動の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | healthy と起動のログ | AI | `mastersmith-app-1` が healthy。`/actuator/health` が 200（compose と同じ bash の `/dev/tcp` で待つ）。メモリの上限 2 GiB・CPU 4。コンテナのイメージ ID が `mastersmith:local` の ID と一致。起動のログ（JSON として読み、ロガーと項目の名前と件数だけを出す）で、ERROR 0 件・Flyway の `Migrating schema` 0 件で版 13・救済の WARN 0 件・**点検の WARN のうち項目名 `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` を含むものが 0 件**（U1 の R-01）。WARN はほかに前の版から出ているもの（Flyway の H2 の版・Bean の後処理の案内）だけ。初期管理者の INFO にメールアドレスそのものが出ていない |

- S1 の点検の WARN が 0 件でも、2つの設定の誤りは対象DB 全体を使えなくし、ヘルスチェックの 200 では見分けられない。そのため S2 で既存の業務データの一覧が 200 で返ることを**必ず**確かめる（U1 の `cicd-pipeline.md` 3節 順 5・順 6）。

## 3. スモークテスト（Q1: A）

既定の DSL の参照（`sales.orders.customer_id → sales.customers`、`REFERENCE` の選択）で、参照の読み取り（選択肢とラベル）・保存前の確かめ（成功の経路）・画面の部品を確かめる。確かめ用のロールは「スモーク確認乙」だけを作る（前の Intent の配備で、初期管理者にグループ経由の割り当てと作業ロールの保存があったため。`project.md` の学び）。

値（メールアドレス・パスワード・業務データの値・主キーの値・顧客のラベル）は記録に書かない。パスワードが要る操作と画面の操作は依頼者が行い、AI はアプリのログ・監査の記録・見本の PG の件数で裏付ける。★の操作は監査に残るため、行う前に 3.1 の期待の件数を依頼者に伝える。

### 3.1 手順

前提: `cd-config.md` の 4.1 の数えで、適用中の DSL の最新の本文に `orders`・`customer_id`・`REFERENCE` が現れ、初期管理者の割り当て（直接 0・グループ経由 3）と作業ロールの保存（1）が前のまま。違うときは `cd-config.md` の順 8 で合わせ直してから始める（3.3）。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S2 | 既存の業務データの一覧（R-01 の必須の確かめ） | 依頼者 | 今の作業ロールで業務のメニューから `customers` の一覧が開き（エラー表示が無い）、1 行の詳細が開ける。AI はアプリのログで同じ時刻の `POST /api/tables/list` 相当の要求に 5xx・ERROR が無いことを確かめる。あわせて AI が `sales.orders` の行の数を控える（`docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc "SELECT COUNT(*) FROM sales.orders"`。件数だけ） |
| S3 | ★ロール「スモーク確認乙」 | 依頼者 | 「ロール」で乙を作る。「権限の設定」で `sales.orders` に主権限 `FULL` と補助権限（作成・削除）、`sales.customers` に読み取り（`READ`）を付けて保存する（保存は1回） |
| S4 | ★割り当てと切り替え | 依頼者 | 乙の「割り当て」のタブで自分（初期管理者）に乙を割り当てる。画面を読み込み直し、トップバーの「作業ロール」を乙に切り替える（Toast で知らせる）。業務の区画に `orders` と `customers` の項目が出る |
| S5 | ★参照の選択肢と作成 | 依頼者 | `orders` の一覧の「作成」で、`customer_id` が選択の部品になり、見本の顧客 2 件のラベルが文字として出る（HTML として描かれない・エラー表示が無い）。架空の値（`order_id` 9001・`ordered_on` 今日・`total` 0・`is_paid` 偽）と1件目の顧客で保存し、詳細が開き `customer_id` に顧客のラベルが出る |
| S6 | ★参照の変更と更新 | 依頼者 | 詳細から「更新」を開き、`customer_id` の選択に今の顧客が選ばれた状態で出る。2件目の顧客に変えて保存し、詳細に変えた顧客のラベルが出る（値が変わった参照だけを確かめる経路） |
| S7 | ★削除 | 依頼者 | 詳細から「削除」で確かめの表示を経て消し、一覧に戻って行が無い（作った行を見本の PG に残さない） |
| S8 | ★片付け | 依頼者 | 作業ロールを元のロールへ切り替え、乙の割り当てを外し、乙を消す（この順。乙の作業ロールの保存は元へ戻した時点で残らない） |
| S9 | 見本の PG と監査の確かめ | AI | `sales.orders` の行の数が S2 で控えた数と同じ（件数だけ）。配備の後の複写（`cd-config.md` の順 10）を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数えて 3.2 と比べる。`roles` の行の数・初期管理者の割り当て（直接・グループ経由）・作業ロールの保存の有無が `cd-config.md` 4.1 の数と同じ（確かめ用のロールが残っていない）。依頼者が「済んだ」と答えた後も、複写で状態を数えてから判定する（`project.md` の学び） |

- 依頼者が画面で触れるときは、監査に残る操作（管理の操作・ログインの失敗・保存の押し直しなど）をしたかをその場で聞き、期待の件数を合わせてから数える（`project.md` の学び。前の Intent では保存を2回押して `ROLE_PERMISSION_CHANGED` が 2 になった）。
- S5 の「選択の部品になる」は、作業ロールが参照先（`customers`）を読めるときだけ選択肢が返るため、S3 で `customers` に `READ` を付けることが前提。

### 3.2 監査の期待（`AuditEventType` で確かめた種類だけ）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` の種類と、`BusinessDataAuditReason.of` の写しで確かめた。参照先の読み取り（作成・更新の定義の取得、`POST /api/tables/reference/rows`）と業務データの一覧・詳細の読み取りは、どの種類にも当たらず監査に残らない。

| 種類 | 結果 | 期待の件数 | どの操作 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 依頼者がログインした回数（0 もありうる。入れ替えの後もリフレッシュトークンで続けば 0） | S2 の前 |
| `ROLE_CREATED` | `SUCCESS` | 1 | S3 |
| `ROLE_PERMISSION_CHANGED` | `SUCCESS` | 1（保存を押した回数） | S3 |
| `ROLE_ASSIGNED` | `SUCCESS` | 1 | S4 |
| `WORK_ROLE_SWITCHED` | `SUCCESS` | 2（元 → 乙、乙 → 元） | S4・S8 |
| `BUSINESS_ROW_CREATED` | `SUCCESS` | 1 | S5 |
| `BUSINESS_ROW_UPDATED` | `SUCCESS` | 1 | S6 |
| `BUSINESS_ROW_DELETED` | `SUCCESS` | 1 | S7 |
| `ROLE_UNASSIGNED` | `SUCCESS` | 1 | S8 |
| `ROLE_DELETED` | `SUCCESS` | 1 | S8 |
| `BUSINESS_ROW_*` の `FAILURE`（`BUSINESS_REFERENCE_NOT_FOUND` を含む） | — | 0 | 予定に無い |
| 結果 `FAILURE`・`UNKNOWN` のすべて | — | 0 | 予定に無い |
| `INITIAL_ADMIN_RESCUED`・`DSL_*`・`ROLE_RENAMED`・`ROLE_TRANSFER_APPLIED`・`GROUP_*`・`USER_*`・`INVITATION_*`・`REGISTRATION_*`・`PASSWORD_CHANGED`・`ACCESS_DENIED`・`LOGIN_FAILED`・`LOGIN_FAILURES_RESET` | — | 0（依頼者がログアウトしたときは `LOGGED_OUT` を足す） | 予定に無い |

- `BUSINESS_ROW_*` の3行の `target_schema`・`target_table`・`work_role_id` は空でない（値は表示せず、空でないことだけを数える。`work_role_id` は乙の ID の見込み）。

### 3.3 前提が違ったときの合わせ直し（`cd-config.md` の順 8）

| 違い | 合わせ直し |
|---|---|
| 適用中の DSL の最新の本文に `REFERENCE` が無い（`orders.customer_id` が参照でない） | DSL は変えない。依頼者に諮り、S3 の権限を `sales.customers` に `FULL` と補助権限（作成・削除）にして、S5〜S7 を `customers` の作成・更新・削除（前の Intent の形）に置き換えるかを決める。参照の画面の確かめは E2E 190・195 で確かめ済みとし、差を Deployment Execution の記録に書く |
| 初期管理者の割り当てや作業ロールの保存が前と違う（例: 割り当てが乙だけになり作業ロールが自動で選ばれる） | 作業ロールの自動の選び方（割り当てられたロールが1つだと自動で選ばれ、監査に残らない）を踏まえて、S4・S8 の切り替えと `WORK_ROLE_SWITCHED` の期待を依頼者と合わせ直す（`project.md` の学び） |
| `roles` の行の数が前と違う・乙の名前が既にある | 確かめ用のロールの名前を依頼者と決め直す |

## 4. 通らなかったとき

- 通らない点があれば、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。S1 で2つの設定の点検の WARN が出た・S2 の一覧が 503 になったときは、まず `rollback-runbook.md` の 3節（`.env` の該当の行を消す）を検討する。
- S5・S6 で参照の選択肢が出ない・保存が 409・503 になるときは、アプリのログの `code` と `failureReason`（値は出さない）を読み、作業ロールの権限（`customers` の `READ`）と適用中の DSL を確かめてから戻すかを諮る。
- 戻すと決めるまでは、状態（行の数・監査）を変える操作をしない。S5 で作った行が残ったまま戻すときは、戻す前に依頼者が画面で消すか、残すかを決めて記録する（戻した版でも一覧・削除はできる）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-pipeline/deployment-strategy.md`、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/deployment-strategy.md`（乙・作業ロールの切り替え・監査の期待の形）、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-execution/smoke-test-results.md`（合わせ直しの結果）
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`
- 上流: `construction/u1-reference-read/infrastructure-design/cicd-pipeline.md`（3節 順 5・順 6、R-01）、`construction/u2-reference-write-check/infrastructure-design/cicd-pipeline.md`（3節 順 6）、`construction/u3-reference-screens/infrastructure-design/cicd-pipeline.md`（5節）
- リポジトリ: `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataAuditReason.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`、`docker/targetdb/postgres/01-sample-schema.sql`、`compose.yaml`、README
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 初期管理者でログインする操作と画面の操作は依頼者が行う（パスワードを AI が扱わない）。
- [assumption] 既定の DSL の生成は `orders.customer_id` の表示のカラムを参照先の文字のカラムから選ぶ。S5 で出るラベルの中身は記録しない。
- [assumption] 3.1 の前提（適用中の DSL・割り当て・作業ロール）は `cd-config.md` の 4.1 で数えて裏付ける。
