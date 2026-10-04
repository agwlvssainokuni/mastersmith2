# 配備の仕方（deployment-strategy）

Intent 261003-user-admin-followup の配備の仕方と、配備の後の確かめです。流れの全体は `cd-config.md`、戻しは `rollback-runbook.md` にあります。前の Intent の `deployment-strategy.md`（以下「前の手順」）を正とし、差だけを書きます。

## 1. 方式

| 項目 | 値 |
|---|---|
| 方式 | 1台の置き換え（Recreate）。青緑・カナリア・段階の昇格は無い（配備先が開発者の PC 上のコンテナ1つのため） |
| 止まる時間 | 新しいイメージでの起動の間（前の Intent では入れ替えの開始から healthy まで約 17 秒）。スキーマの変更は無い |
| 機能の切り替え（feature flag） | 使わない |
| 環境の昇格 | 無い（検証環境・本番環境は配備先が決まってから） |
| 承認 | 依頼者 |

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 2・3）

```bash
# 順 2: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-user-admin-followup
docker image inspect mastersmith:pre-user-admin-followup --format '{{.Id}}'   # sha256:f386b55f16a8… であること

# 順 3: WAR とイメージを作り直し、app だけを入れ替える（依頼者の承認の後、Q1: A）
docker compose --profile targetdb-postgres up -d --build app
docker compose --profile targetdb-postgres ps app                              # app が healthy になるまで待つ
docker image inspect mastersmith:local --format '{{.Id}} {{.Created}}'          # 配備したイメージの ID を記録する
```

- `app` だけを指定する。見本の対象DB と Mailpit は入れ替えない。
- 画面は `http://localhost:8080/` で開く（`127.0.0.1` では Origin の確かめが合わず 403）。

## 3. 配備の後の確かめ（スモークテスト、Q2: A）

健全性と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment、要件の確かめの指摘 R-02）。

- パスワードの要る操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けます。
- 起動のログは、値を表示せず、レベル・ロガー・キーの名前と件数だけを見ます。
- 利用者の一覧の画面にはメールアドレス・氏名が出ます。AI は値を記録・報告に写しません。依頼者の確かめの結果も、件数と見た目の結果だけを記録します。
- **管理の操作は確定させません**。確かめの表示は「やめる」で閉じ、氏名と言語の入力は送らずに閉じます。監査に残る要求は、ログインの `LOGIN_SUCCEEDED` だけです（送る前に依頼者に伝えます）。依頼者が画面で監査に残る操作をしたときは、その場で伺い、S6 の期待の件数を合わせます（`project.md` の学び 2026-10-03）。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy になり、`/actuator/health` が 200・UP。ERROR が 0 件。WARN はロガーと件数だけを見て、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）のほかに無い |
| S2 | 起動のログに秘密・メールアドレスが無い | AI | 初期管理者の INFO にキー `maskedEmail` があり、キー `email` が無い（キーの有無だけを見る）。Flyway は版 9 のまま（`Successfully validated` があり、新しい移行が無い。件数だけを数える） |
| S3 | ★ログイン | 依頼者（AI は S6 で裏付け） | 依頼者が初期管理者でログインし、ホームが開ける |
| S4 | 利用者の管理の一覧とメニュー（K2） | 依頼者 | 「利用者の管理」から一覧が開ける。いちばん下の行の右端の「操作」を開き、メニューの右端と下端が欠けずに画面の中に出る。広い幅と、ブラウザの幅を狭めた状態の両方で見る。依頼者は「欠けた・欠けない」だけを伝える |
| S5 | 閉じた後のフォーカス（K1） | 依頼者 | 行の「操作」をキーボードで開き、「管理者の印を付ける」などの確かめの表示を開いて「やめる」（または Escape）で閉じる。閉じた後、フォーカスの枠がその行の「操作」のボタンにある（次に Tab を押すと、その行の次の要素へ進む）。氏名と言語の入力の表示も、開いて閉じるだけで同じことを見る。**どちらも確定させない** |
| S6 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/`（権限 700）へ複写し、アプリを起動し直してから、複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて、配備の時刻以降の `audit_events` を種類と結果ごとに数える。`LOGIN_SUCCEEDED` が1件以上あり、利用者の管理の種類（`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`）が 0 件（氏名と言語の変更は監査に残らない。`AuditEventType` に種類が無いことを確かめた）。アプリを止める間（数秒）は依頼者に伝えてから行う。複写は配備の後のバックアップを兼ねる |

S1・S2 のコマンド（値を表示しない形。前の手順の S1 と同じ）:

```bash
# S1: レベルごとの件数と、WARN・ERROR のロガーと件数だけ
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .logger' | sort | uniq -c

# S2: 初期管理者の INFO のキーの有無（値は表示しない）と、Flyway の移行の件数
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | test("InitialAdmin")) | keys_unsorted | join(",")' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'Migrating schema'   # 0 であること
```

- S2 の初期管理者のロガーの名前は前の Intent の記録（`InitialAdminInitializer`）に合わせた。0 件のときは、まずロガーの名前と件数だけを数え直す。
- S4・S5 は E2E 110・120（153 件のうち）で確かめ済みのものを、配備したイメージと実際のブラウザで見直すもの（Q2: A）。
- S6 の監査の数えでは、配備の前のブラウザに残っていたセッションのログアウト（`LOGGED_OUT`）が1件混じることがある。その他の種類が出たら、何が残ったかを依頼者に伺う。

## 4. 中止の条件

次のどれかが起きたら配備を中止し、`rollback-runbook.md` に従ってイメージだけを戻します。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない | 戻し先のイメージで起動し直す |
| S1〜S6 のどれかが通らず、その場で原因が設定だけと分からない | 同上 |
| ERROR のログが出続ける、ログに秘密・メールアドレスそのものが出る | 同上 |
| S4 でメニューが欠ける、S5 でフォーカスが戻らない | 中止して原因を調べる。E2E では通っているため、ブラウザの違い・幅の違いを確かめ、戻すかどうかは依頼者が決める（戻すと、前の Intent の版の同じ不具合に戻るだけで、データへの影響は無い） |

## 5. 監視

- 手元の監視（lgtm、profile `monitoring`）は、この段では起動しない。この Intent は指標・警報・ダッシュボードを変えていない（警報3件が鳴ることは Build and Test の T1 で確かめた）。
- 配備の直後は、`docker compose logs app`（値を表示しない形）と監査の記録（S6）で確かめる。

## Sources

- 前の手順: `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/deployment-strategy.md`、`operation/deployment-execution/smoke-test-results.md`・`deployment-log.md`
- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`（Q1・Q2、まとめの確認）
- `construction/code-generation/code-summary.md`（E2E 110・120 の確かめ）
- `README.md`（コンテナでの起動と確認、監査ログの確かめ方）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] S2 の Flyway の文言（`Successfully validated`・`Migrating schema`）は、前の Intent の配備の起動のログと同じ形を前提にしている。文言が違うときはロガーと件数で判定し直す。
