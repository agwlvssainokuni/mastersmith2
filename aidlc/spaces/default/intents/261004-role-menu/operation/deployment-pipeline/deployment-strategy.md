# 配備の仕方（deployment-strategy）

Intent 261004-role-menu の配備の仕方と、配備の後の確かめです。流れの全体は `cd-config.md`、戻しと戻しの練習は `rollback-runbook.md` にあります。前の Intent の `deployment-strategy.md`（`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/deployment-strategy.md`。以下「前の手順」）を正とし、差だけを書きます。

## 1. 方式

| 項目 | 値 |
|---|---|
| 方式 | 1台の置き換え（Recreate）。青緑・カナリア・段階の昇格は無い（配備先が開発者の PC 上のコンテナ1つのため） |
| 止まる時間 | 入れ替えの前の内部DB の複写から、新しいイメージで healthy になるまで（移行 V10〜V12 を含む）と、配備の後の複写（約 10 秒） |
| 機能の切り替え（feature flag） | 使わない。業務のメニューと権限は、版 2 の DSL の適用とロールの割り当てで効く |
| 環境の昇格 | 無い（検証環境・本番環境は配備先が決まってから） |
| 承認 | 依頼者 |

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 2〜6）

```bash
# 順 2: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-role-menu
docker image inspect mastersmith:pre-role-menu --format '{{.Id}}'   # sha256:fefeafbd299b… であること

# 順 4 の前: 監査の数え始めの時刻 T0（UTC）を控える。依頼者が DSL の画面で版 1 のプレビューがあれば破棄する
date -u +%Y-%m-%dT%H:%M:%SZ

# 順 5: 依頼者の承認の後、アプリを止めて内部DB を複写する（配備の前のバックアップ）
docker compose stop app
#   README の「内部DBのバックアップと戻し方」の形で ~/.mastersmith-backup/mastersmith-data-<日時>-pre-role-menu.tgz を作る。
#   複写を ACCESS_MODE_DATA=r で開き、cd-config.md の 4節の4つの数だけを読む（値は表示しない）

# 順 6: WAR とイメージを作り直し、app だけを入れ替える（起動で V10〜V12 が当たる）
docker compose --profile targetdb-postgres up -d --build app
docker compose --profile targetdb-postgres ps app                              # app が healthy になるまで待つ
docker image inspect mastersmith:local --format '{{.Id}} {{.Created}}'          # 配備したイメージの ID を記録する
```

- `app` だけを指定する。見本の対象DB と Mailpit は入れ替えない。
- 画面は `http://localhost:8080/` で開く（`127.0.0.1` では Origin の確かめが合わず 403）。開いているタブは入れ替えの後に読み込み直す。
- 救済の見込み（`cd-config.md` の 3節の順 3 と 4節）で救済が働く見込みなら、順 6 の前に依頼者の了承を得る。

## 3. 配備の後の確かめ（スモークテスト、Q5: A）

健全性と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。

- パスワードの要る操作（ログイン）と画面の操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けます（`project.md` の Corrections）。
- 起動のログは、値を表示せず、レベル・ロガー・メッセージ・キーの名前と件数だけを見ます。
- 監査に残る操作（ログイン、DSL の生成・適用、ロール・グループの操作）は、行う前に依頼者に伝え、下の 3.2 の期待の件数をその場で合わせます。依頼者が予定に無い操作をしたときは、その場で何をしたかを伺い、期待の件数を直してから数えます（`project.md` の学び）。
- 確かめ用のロール・グループの名前は、個人に関する値を含まない見本の名前（例「スモーク確認」）にします。

### 3.1 確かめの一覧

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy、`/actuator/health` が 200・UP。ERROR は、版 1 の適用中の DSL があったときの「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」1件だけ（無かったときは 0 件。`cd-config.md` の 4節）。WARN はロガーと件数だけを見て、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）と、救済の見込みどおりの救済の WARN のほかに無い |
| S2 | 移行と JVM の設定 | AI | Flyway の `Migrating schema` が 3 件（版 10・11・12）で、現在の版が 12。初期管理者の INFO・WARN にキー `maskedEmail` があり、キー `email` が無い。`ExitOnOutOfMemoryError` と `MaxRAMPercentage=50.0` が PID 1 のコマンド行に1件ずつ（`cd-config.md` の 6節） |
| S3 | ★ログイン | 依頼者 | 初期管理者でログインし（ログイン中ならタブを読み込み直し）、ホームが開ける。サイドバーに「ホーム」「業務」「管理」の区画があり、「管理」にロール・グループ・権限の受け渡しがある |
| S4 | ★DSL の移し替え（`cd-config.md` の 5節） | 依頼者 | DSL の画面に「適用中の DSL は使われていません」の注意が出ている（版 1 があったとき）。「スキーマを読み込む」で版 2 を生成し、プレビューに検証の通過・スキーマ・メニューの木が出て、適用でき、注意が消える |
| S5 | ★ロールと権限の設定 | 依頼者 | 「ロール」で確かめ用のロールを2つ（ロール甲・ロール乙）作る。「権限の設定」の木でスキーマ → テーブルを開き、ロール甲にはテーブル A、ロール乙には別のテーブル B に読み取り（`READ`）を付けて保存する（保存はロールごとに1回、計2回） |
| S6 | ★割り当て | 依頼者 | 各ロールの「割り当て」のタブで、自分（初期管理者）にロール甲・ロール乙の両方を割り当てる |
| S7 | ★作業ロールの切り替えと業務のメニュー | 依頼者 | 画面を読み込み直し、トップバーの「作業ロール」がロール甲（ID の最も小さいロールが自動で選ばれる）で、業務の区画にテーブル A の項目だけが出て、選ぶと `/tables?schema=…&table=…` の準備中の画面が表示名つきで開く。次に作業ロールの一覧からロール乙に切り替え（Toast で知らせる）、業務の区画がテーブル B の項目だけに変わり、テーブル A の道を直接開くと「この画面を表示する権限がありません」になる |
| S8 | ★自分の権限 | 依頼者 | 「自分の権限を見る」（`/me/permissions`）で、ロール乙ではテーブル B が読み取り・テーブル A が権限なしで出る。ロール甲に切り替え直したときの表示は見ない（切り替え直すと `WORK_ROLE_SWITCHED` がもう1件増えるため。見るときは期待の件数を足す） |
| S9 | ★グループと利用者の詳細 | 依頼者 | 「グループ」で確かめ用のグループを1つ作り、自分をメンバーに足す。利用者の管理の画面の自分の行の「ロールを見る」に、確かめ用のロール2つと出どころ（直接の割り当て）が出る |
| S10 | ★片付けの判断 | 依頼者 | 作ったロール2つとグループを残すか消すかを決める。消すときは、配備の後の複写（S11）の前に、2つのロールの割り当てを外す → 2つのロールを消す（作業ロールの保存も一緒に消える）、メンバーを外す → グループを消す、の順で行う |
| S11 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/mastersmith-data-<日時>-after-role-menu.tgz` へ複写し、すぐ起動し直す（約 10 秒。依頼者に伝えてから）。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数え、3.2 の表と比べる。この複写は配備の後のバックアップと戻しの練習の元を兼ねる |

S1・S2 のコマンド（値を表示しない形）:

```bash
# S1: レベルごとの件数と、WARN・ERROR のロガーとメッセージと件数だけ
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .level + " " + .logger + " " + .message' | sort | uniq -c

# S2: Flyway の移行の件数、初期管理者の行のキーの有無（値は表示しない）
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'Migrating schema'   # 3 であること
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | test("InitialAdmin")) | .level + " " + .message + " " + (keys_unsorted | join(","))' | sort | uniq -c
```

### 3.2 監査の期待の件数（T0 以降、`AuditEventType` で確かめた種類）

数えるのは種類（`event_type`）と結果（`result`）だけで、利用者・対象の値は表示しません。期待の件数は、操作の前に依頼者と合わせ、依頼者が予定と違う操作をしたときはその場で直します。

| 種類 | 期待の件数（結果は成功） | 出どころ |
|---|---|---|
| `DSL_PREVIEW_DISCARDED` | 0 または 1（順 4 で版 1 のプレビューがあり破棄したとき 1） | 順 4（前の版） |
| `LOGIN_SUCCEEDED` | 順 4 と S3 で依頼者がログインした回数（ログイン中のまま読み込み直したときは数えない） | 順 4・S3 |
| `DSL_GENERATED` | 1 | S4 |
| `DSL_APPLIED` | 1 | S4 |
| `ROLE_CREATED` | 2 | S5 |
| `ROLE_PERMISSION_CHANGED` | 2（保存した回数） | S5 |
| `ROLE_ASSIGNED` | 2 | S6 |
| `GROUP_CREATED` | 1 | S9 |
| `GROUP_MEMBER_ADDED` | 1 | S9 |
| `WORK_ROLE_SWITCHED` | 1（ロール甲 → ロール乙。最初のロール甲は自動で選ばれ、記録されない） | S7 |
| `ROLE_UNASSIGNED`・`ROLE_DELETED` | 消すとき各 2、残すとき各 0 | S10 |
| `GROUP_MEMBER_REMOVED`・`GROUP_DELETED` | 消すとき各 1、残すとき各 0 | S10 |
| `INITIAL_ADMIN_RESCUED` | 救済の見込みどおり（働かない見込みなら 0） | 順 6 |
| `INITIAL_ADMIN_CREATED` | 0 | 順 6 |
| `DSL_SUBMITTED`・`DSL_SUBMISSION_REJECTED`・`ROLE_TRANSFER_APPLIED`・`ROLE_RENAMED`・`GROUP_RENAMED`・`ACCESS_DENIED`・`LOGIN_FAILED` | 0 | 予定に無い操作 |
| 上のどの種類でも、結果が失敗の行 | 0 | 業務の拒否（409 など）が無いこと |

- 「残す」を選ぶと、上の消す操作は 0 件で、確かめ用のロール2つとグループは本物のデータとして残ります（業務データの画面はまだ無いため、残しても権限で見えるのは業務のメニューと準備中の画面だけ）。
- 確かめの読み取り（一覧・詳細・木・自分の権限・業務のメニュー・置き場の問い合わせ）は監査に残りません（README）。

## 4. 合否の基準と中止の条件

- **合格**: S1〜S11 がすべて通ったとき。合格の後に、戻しの練習（`rollback-runbook.md` の 6節）へ進む。
- **中止**: 次のどれかが起きたら配備を中止し、依頼者に諮ってから `rollback-runbook.md` に従って戻します。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない、Flyway の移行が失敗する | 移行が途中で失敗したときは、配備の前の複写を戻してから戻し先のイメージで起動する（`rollback-runbook.md` の 3節）。そうでなければイメージだけを戻す（2節） |
| S1 で想定の1件のほかに ERROR が出る・出続ける、ログに秘密・メールアドレスそのものが出る | イメージだけを戻す（2節） |
| S4〜S9 のどれかが通らず、その場で原因が操作の誤りや設定だけと分からない | 同上（戻す前に版 2 のプレビューがあれば破棄する） |
| S11 で期待と違う件数が出て、依頼者の操作で説明できない | 合否を決める前に依頼者に諮る |
| アプリが止まった（`ExitOnOutOfMemoryError`） | `rollback-runbook.md` の 7節 |

## 5. 監視

- 手元の監視（lgtm、profile `monitoring`）は、この段では起動しない。新しい警報とダッシュボードの区画の確かめは Observability Setup の段（`U3-NFR5.4`・`U4-NFR5.4`）。
- 配備の直後は、`docker compose logs app`（値を表示しない形）と監査の記録（S11）で確かめる。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/deployment-strategy.md`
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`（Q2・Q5・Q6）
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`・`AuditEventFactory.java`、`backend/src/main/java/cherry/mastersmith/role/service/WorkRoleService.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslStartupLoader.java`（読むだけ）
- README（DSL の管理の API・DSL の操作の監査、グループの管理の API、ロールと権限の API、業務のメニューの API、ロール・グループ・権限の受け渡しの画面、骨組みの画面、監査ログの確かめ方）
- `construction/role/infrastructure-design/infrastructure-specification.md`（4節）、`construction/group/infrastructure-design/infrastructure-specification.md`（4節）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 権限の設定の保存1回につき `ROLE_PERMISSION_CHANGED` は1件（README の「成功した変える操作を残す」と `AuditEventFactory` の対応から読んだ。実際の件数で確かめる）。
- [assumption] ロールを割り当てた直後は、保存した作業ロールが無いため ID の最も小さいロール（ロール甲）が `PUT /api/me/work-role` を送らずに選ばれ、`WORK_ROLE_SWITCHED` は出ない。出るのは S7 のロール乙への切り替えの1件だけ（README の「作業ロールの決め方」、`WorkRoleService`）。
- 確かめ用のロールを2つにし、作業ロールの切り替えも確かめることは、依頼者の答え（確かめたい点 1: A）で決めた。
