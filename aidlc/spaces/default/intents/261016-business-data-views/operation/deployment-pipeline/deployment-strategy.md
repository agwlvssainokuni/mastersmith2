# 配備の仕方（deployment-strategy）

Intent 261016-business-data-views の配備の仕方です。前の手順（`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/deployment-strategy.md` と、その元の `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/deployment-strategy.md`）からの差を中心に書きます。流れの全体と順の番号は `cd-config.md` の 3節です。

## 1. 方式

- 入れ替え（recreate）。開発者の PC 上の1つのコンテナで、アプリを止めて新しいイメージで起動し直す。無停止の入れ替え・段階的な切り替え（blue/green・canary）はしない（配備先が決まるまでの扱い。切り替えの割合と中止の条件の代わりに、4節の中止の条件を置く）。
- 内部DB の移行は V13（`audit_events` に空を許す3列。前進のみ・後方互換）。入れ替えの起動で当たる。
- 同じ停止の中で、見本の PG に書き込みのアカウントを足し、`.env` に書き込みの2項目を足す（Q1: A）。アプリの作り直しは入れ替えの1回だけ。
- アプリが止まるのは、入れ替えの前の複写から healthy まで（約 3〜4 分の見込み）と、配備の後の複写（約 10 秒）。
- 書き込みの機能は、`.env` の書き込みの2項目で入れ切りする（両方あるときだけ使う）。ほかの機能の切り替えの仕組みは持たない。

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 1〜9）

停止の間の順は「止める → 内部DB を複写 → `.env` を外へ複写 → 書き込みの2項目と `.env.targetdb` を足す → 見本の PG を作り直して台本を流し、件数で確かめる → イメージ → 入れ替え → health → 救済の見込み → 監査の件数」です。WAR は停止の前に作り直します（停止を短くするため）。

```bash
# 順 1: 関門
git status --short -- . ':!aidlc'                          # 空であること
git diff --stat d220447 HEAD -- . ':!aidlc'                # 22de65f の4ファイルだけであること

# 順 2: WAR の作り直しと確かめ（停止の前）
./gradlew :backend:bootWar --rerun-tasks
shasum -a 256 backend/build/libs/mastersmith.war           # 記録する
unzip -l backend/build/libs/mastersmith.war | grep -E 'businessdata/web/BusinessDataWriteController|businessdata/repository/|V13__u2_business_data_audit_columns' | head
unzip -l backend/build/libs/mastersmith.war | grep -c 'static/assets/'   # 画面のビルドがあること（件数だけ）

# 順 3: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-business-data-views
docker image inspect mastersmith:pre-business-data-views --format '{{.Id}}'   # sha256:f103aee1702a… であること

# 順 4: 救済の見込みを依頼者に尋ねる（前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したか）

# 順 5: 依頼者の承認の後、T0（UTC）を控え、アプリを止めて内部DB を複写する（cd-config.md の 4節。ここでは start しない）

# 順 6・順 7: cd-config.md の 3.2 のコマンド（順 6 は流す前に依頼者に見せて承認を得る）

# 順 8: 順 2 の WAR からイメージを作り直し、app だけを入れ替える（新しい .env を読む）
docker compose --profile targetdb-postgres up -d --build app

# 順 9: healthy を待ち、健全性を確かめる
docker compose --profile targetdb-postgres ps app          # healthy であること
docker compose --profile targetdb-postgres ps              # targetdb-postgres が Up、PORTS が空、mailpit が healthy
```

- 順 8 の `--build` は、順 2 の WAR を写してイメージ `mastersmith:local` を作り直す（WAR は作り直さない。`project.md` の学び）。順 3 のタグを付ける前に順 8 を行わない。
- 順 9 の後に、開いているタブを読み込み直す。

## 3. 配備の後の確かめ（スモークテスト、Q4: A）

健全性と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。

- パスワードの要る操作（ログイン）と画面の操作は依頼者が行い、AI はアプリのログ・監査の記録・見本の PG の件数で裏付けます（`project.md` の Corrections）。
- 起動のログは、値を表示せず、レベル・ロガー・メッセージ・キーの名前と件数だけを見ます。
- 監査に残る操作（ログイン、ロールの作成・権限の保存・割り当て、作業ロールの切り替え、業務データの作成・更新・削除）は、行う前に依頼者に伝え、下の 3.2 の期待の件数をその場で合わせます。依頼者が予定に無い操作をしたときは、その場で何をしたかを伺い、期待の件数を直してから数えます（`project.md` の学び）。
- 確かめ用のロールの名前は、個人に関する値を含まない見本の名前（「スモーク確認甲」「スモーク確認乙」）にします。作る業務データの行も架空の値だけにします（例: `customer_id` 9001、`name` 「スモーク確認」、`email` `smoke@example.com`、`note` 空）。
- 切り替えの前提: 作業ロールは、割り当てられたロールが1つだと自動で選ばれて切り替えを確かめられないため、ロールを2つ作って両方を割り当てます（`project.md` の学び）。割り当ての後、作業ロールの保存が無ければ ID の最も小さいロール（先に作った甲）が自動で選ばれ、これは監査に残りません（前の Intent の S7）。`cd-config.md` の 4節で、初期管理者に前からロールの割り当てや作業ロールの保存があると分かったときは、自動で選ばれるロールと期待の件数をスモークテストの前に合わせ直します。

### 3.1 確かめの一覧

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy、`/actuator/health` が 200・UP。ERROR 0 件。WARN はロガーと件数だけを見て、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）と、救済の見込みどおりの救済の WARN のほかに無い。書き込みの設定の WARN（項目の名前 `MASTERSMITH_TARGET_DB_WRITE_` を含む行）が 0 件。ログの中の `.env` の初期管理者のメールアドレスの値の件数が 0（値は表示しない） |
| S2 | 移行と JVM の設定 | AI | Flyway の `Migrating schema` が 1 件（版 13）で、現在の版が 13。`ExitOnOutOfMemoryError` と `MaxRAMPercentage=50.0` が PID 1 のコマンド行に1件ずつ。初期管理者の INFO・WARN にキー `maskedEmail` があり、キー `email` が無い |
| S3 | 見本の PG | AI | `cd-config.md` の 3.2 の順 7 の確かめが `1\|t\|t\|t\|f\|f\|f`。`sales.customers` の行の数を控える（見込み 2。値は表示しない） |
| S4 | ★ログイン | 依頼者 | 初期管理者でログインし（ログイン中ならタブを読み込み直し）、ホームが開ける |
| S5 | ★ロールと権限の設定 | 依頼者 | 「ロール」で「スモーク確認甲」「スモーク確認乙」をこの順に作る。「権限の設定」で、甲には `sales.orders` に読み取り（`READ`）、乙には `sales.customers` に主権限 `FULL` と補助権限（作成・削除）、ビュー `sales.customer_orders` に読み取り（`READ`）を付けて保存する（保存はロールごとに1回、計2回） |
| S6 | ★割り当て | 依頼者 | 各ロールの「割り当て」のタブで、自分（初期管理者）に甲・乙の両方を割り当てる |
| S7 | ★作業ロールの切り替えと業務のメニュー | 依頼者 | 画面を読み込み直し、トップバーの「作業ロール」が甲（自動で選ばれる）で、業務の区画に `orders` の項目だけが出る。作業ロールを乙に切り替えると（Toast で知らせる）、業務の区画が `customers` と `customer_orders` の項目に変わる |
| S8 | ★一覧と詳細 | 依頼者 | `customers` の一覧（見出しは表示名）で、検索（例: `name` の部分一致）・並べ替え（列の見出し）・1ページの件数の選択（20・50・100）が働き、ページ送りの表示が出る（行が少なく1ページのため、次のページへは移れない。複数ページの送りは E2E 180 で確かめ済み）。行のリンクか「操作」の「詳細」で詳細が開き、詳細から戻ると一覧の状態（検索の条件・並べ替え）が保たれる |
| S9 | ★作成・更新・削除 | 依頼者 | `customers` の一覧から「作成」で架空の値の行を1件作り、詳細が開く。「更新」で `note` を変えて保存し、詳細に変えた値が出る。「削除」で確かめの表示を経て消し、一覧に戻って行が無い（作った行を見本の PG に残さない） |
| S10 | ★ビュー | 依頼者 | `customer_orders` の一覧が開き、行の「操作」に更新・削除が出ず、「作成」のボタンも出ない（ビューの決まり。サーバー側の拒否は結合テストで確かめ済み） |
| S11 | ★片付けの判断 | 依頼者 | 作った2つのロールを残すか消すかを決める。消すときは、配備の後の複写（S13）の前に、2つのロールの割り当てを外す → 2つのロールを消す（作業ロールの保存も一緒に消える）の順で行う |
| S12 | 見本の PG の片付けの確かめ | AI | `sales.customers` の行の数が S3 で控えた数と同じ（`docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc "SELECT COUNT(*) FROM sales.customers"`。値は出さず件数だけ） |
| S13 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/mastersmith-data-<日時>-after-business-data-views.tgz` へ複写し、すぐ起動し直す（約 10 秒。依頼者に伝えてから）。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数え、3.2 の表と比べる。業務データの行は `target_schema`・`target_table`・`work_role_id` が空でない件数も数える（値は表示しない）。依頼者の「済んだ」の答えの後も、この複写でロールの行の数と監査を数えてから片付けを判定する（`project.md` の学び）。この複写は配備の後のバックアップと戻しの練習の元を兼ねる |

S1・S2 のコマンド（値を表示しない形）:

```bash
# S1: レベルごとの件数と、WARN・ERROR のロガーとメッセージと件数だけ
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .level + " " + .logger + " " + .message' | sort | uniq -c
docker compose logs --no-log-prefix app | grep -c 'MASTERSMITH_TARGET_DB_WRITE_'        # 0 であること

# S2: Flyway の移行の件数、初期管理者の行のキーの有無（値は表示しない）
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'Migrating schema'   # 1 であること
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | test("InitialAdmin")) | .level + " " + .message + " " + (keys_unsorted | join(","))' | sort | uniq -c
docker exec mastersmith-app-1 cat /proc/1/cmdline | tr '\0' '\n' | grep -cE 'ExitOnOutOfMemoryError|MaxRAMPercentage=50.0'   # 2 であること
```

### 3.2 監査の期待の件数（T0 以降、`AuditEventType` で確かめた種類）

`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` の種類で数えます（2026-10-21 にソースで確かめた）。数えるのは種類（`event_type`）と結果（`result`）と、業務データの行の3列が空でない件数だけで、利用者・対象・主キーの値は表示しません。期待の件数は操作の前に依頼者と合わせ、依頼者が予定と違う操作をしたときはその場で直します。業務データの読み取り（一覧・詳細・定義）と 403 の拒否は監査に残りません。

| 種類 | 期待の件数（結果は `SUCCESS`） | 出どころ |
|---|---|---|
| `LOGIN_SUCCEEDED` | 依頼者がログインした回数（ログイン中のまま読み込み直したときは数えない） | S4 |
| `ROLE_CREATED` | 2 | S5 |
| `ROLE_PERMISSION_CHANGED` | 2（保存した回数） | S5 |
| `ROLE_ASSIGNED` | 2 | S6 |
| `WORK_ROLE_SWITCHED` | 1（甲 → 乙。最初の甲は自動で選ばれ、記録されない） | S7 |
| `BUSINESS_ROW_CREATED` | 1（`target_schema`・`target_table`・`work_role_id` が空でない） | S9 |
| `BUSINESS_ROW_UPDATED` | 1（同上） | S9 |
| `BUSINESS_ROW_DELETED` | 1（同上） | S9 |
| `ROLE_UNASSIGNED`・`ROLE_DELETED` | 消すとき各 2、残すとき各 0 | S11 |
| `INITIAL_ADMIN_RESCUED` | 救済の見込みどおり（働かない見込みなら 0） | 順 8 |
| `INITIAL_ADMIN_CREATED` | 0 | 順 8 |
| `DSL_GENERATED`・`DSL_APPLIED` | 0（`cd-config.md` の 4節で適用中の DSL に見本のテーブルが無く、生成し直したときは各 1） | 4節の手当て |
| `DSL_SUBMITTED`・`DSL_SUBMISSION_REJECTED`・`DSL_PREVIEW_DISCARDED`・`ROLE_RENAMED`・`ROLE_TRANSFER_APPLIED`・`GROUP_*`・`USER_*`・`INVITATION_*`・`ACCESS_DENIED`・`LOGIN_FAILED`・`LOGGED_OUT` | 0（依頼者がログアウトしたときは `LOGGED_OUT` を足す） | 予定に無い操作 |
| 上のどの種類でも、結果が `FAILURE` か `UNKNOWN` の行 | 0 | 業務の拒否（409 など）と確定の時間切れが無いこと |

- 「残す」を選ぶと、確かめ用のロール2つは本物のデータとして残り、乙の権限で初期管理者が `sales.customers` を書けるままになります。残すときは、そのことを Deployment Execution の記録に書きます。
- `BUSINESS_ROW_*` の行の `work_role_id` は乙の ID になる見込みです（値は表示せず、空でないことだけを数える）。

## 4. 合否の基準と中止の条件

| 条件 | 扱い |
|---|---|
| 順 7 の見本の PG の確かめが期待と違う | 入れ替え（順 8）に進まず依頼者に諮る。前の版のまま `docker compose --profile targetdb-postgres start app` で起動し直せる |
| healthy にならない、Flyway が V13 で失敗する | `rollback-runbook.md`。V13 の途中の失敗は第二の手（配備の前の複写を戻す） |
| S1 で ERROR が出る・出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、書き込みの設定の WARN が出る | 原因が設定だけと分かれば直して入れ替え直す。分からなければ `rollback-runbook.md` の 2節で戻すかを依頼者に諮る |
| S4〜S10 のどれかが通らず、その場で原因が操作の誤りや設定だけと分からない | 同上 |
| S12 で行の数が戻っていない | 依頼者に画面で残った行を消してもらう（`BUSINESS_ROW_DELETED` を期待に足す） |
| S13 で期待と違う件数が出て、依頼者の操作で説明できない | 合否を決める前に依頼者に諮る |

## 5. 監視

- 手元の監視（otel-lgtm）は常に動かしていない。配備の確かめは S1〜S13 のログ・監査・件数で行い、業務データの API の指標・警報の確かめは Observability Setup が持つ（Build and Test の Unverified 4 件）。
- 負荷の下の性能は Performance Validation が持つ（`cd-config.md` の 3.3）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/deployment-strategy.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/deployment-strategy.md`（S5〜S11 と 3.2）
- `cd-config.md`・`deployment-pipeline-questions.md`（Q1・Q4）
- この Intent の記録: `construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`、`construction/build-and-test/build-and-test-summary.md`
- リポジトリ: `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`・`AuditResult.java`、`backend/src/main/java/cherry/mastersmith/role/domain/MainPermission.java`・`PermissionValues.java`、README（「業務データの画面」「監査ログの確かめ方」「E2E」の 180 の説明）、見本の PG の `sales` の表の形（2026-10-21 に `information_schema` で読み取りだけ。行の値は読んでいない）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 初期管理者には、前の Intent の片付けの後、ロールの割り当てと作業ロールの保存が無い。`cd-config.md` の 4節で数え、違えば 3.2 を合わせ直す。
- [assumption] 適用中の DSL に `sales.customers`（主キー `customer_id`）・`sales.orders`・`sales.customer_orders` が含まれる。無ければ `cd-config.md` の 4節の手当て（既定の DSL の生成と適用）を行う。
- 見本の `customers` は2行のため、S8 では複数ページの送りを確かめられない（E2E 180 の 25 件で確かめ済み）。
