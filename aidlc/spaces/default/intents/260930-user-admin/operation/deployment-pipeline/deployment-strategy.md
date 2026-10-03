# 配備の仕方（deployment-strategy）

Intent 260930-user-admin の配備の仕方と、配備の後の確かめです。流れの全体は `cd-config.md`、戻しは `rollback-runbook.md` にあります。

## 1. 方式

| 項目 | 値 |
|---|---|
| 方式 | 1台の置き換え（Recreate）。青緑・カナリア・段階の昇格は無い（配備先が開発者の PC 上のコンテナ1つのため。U3〜U5 の `infrastructure-specification.md` の 1節） |
| 止まる時間 | 新しいイメージでの起動（Flyway の V9 を含む）の間。前の Intent と同じく1分ほどの見込み。バックアップを取らないため（Q1: B）、配備の前の停止は無い |
| 機能の切り替え（feature flag） | 使わない。利用者の管理の画面と API は、管理者だけが使える既存の `/api/admin/**` の決まりに乗る |
| 環境の昇格 | 無い（検証環境・本番環境は配備先が決まってから。`project.md` の Deployment） |
| 承認 | 依頼者（`team.md` の Deployment。本番配備の承認者は依頼者） |

## 2. 入れ替えの手順（`cd-config.md` の 3節の順 3・4）

```bash
# 順 3: 戻し先のタグ（今動いている版）
docker tag mastersmith:local mastersmith:pre-user-admin
docker image inspect mastersmith:pre-user-admin --format '{{.Id}}'    # sha256:c77c1bb247f9… であること

# 順 4: イメージを作り直し、app だけを入れ替える（依頼者の承認の後）
docker compose --profile targetdb-postgres up -d --build app
docker compose --profile targetdb-postgres ps app                     # app が healthy になるまで待つ（最長で約 2 分）
docker image inspect mastersmith:local --format '{{.Id}} {{.Created}}' # 配備したイメージの ID を記録する
```

- `app` だけを指定する。見本の対象DB（`targetdb-postgres`）と Mailpit は入れ替えない。
- 起動のときに Flyway が V9 を当てる（`validate-on-migrate: true`、失敗したら起動を止める）。V9 が失敗して healthy にならないときは 5節の中止の条件に当たる。
- 画面は `http://localhost:8080/` で開く（`127.0.0.1` では Origin の確かめが合わず 403。README の「コンテナでの起動と確認」）。

## 3. 配備の後の確かめ（スモークテスト、Q4: A）

健全性（`/actuator/health` が UP）と、下のすべてが通るまで、配備の完了としません（`team.md` の Deployment）。

- パスワードの要る操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けます（`project.md` の Corrections）。
- 起動のログは、値を表示せず、ロガーとキーの名前と件数だけを見ます（`project.md` の Corrections）。
- 利用者の一覧の応答と画面にはメールアドレス・氏名が出ます。AI は値を記録・報告に写しません。依頼者の確かめの結果も、件数と印の有無だけを記録します。
- **管理の操作はしません**（5つの操作・氏名と言語の変更・招待）。監査に残る要求はログインの `LOGIN_SUCCEEDED` だけです（送る前に依頼者に伝えます）。利用者の一覧を見ることは監査に残りません（`AuditEventType` に一覧の種類は無い。README の「利用者の管理の API」）。

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| S1 | 健全性と起動のログ | AI | `app` が healthy になる。ERROR が 0 件。WARN はロガーと件数だけを見て、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）のほかに無い |
| S2 | Flyway が V9 を当てたこと | AI | Flyway のロガー（`org.flywaydb` で始まる）のログに、版 9 への移行（`to version "9`）と、版 9 になったこと（`now at version v9`）がそれぞれ1件ある。件数だけを数え、メッセージは表示しない（下のコマンド） |
| S3 | ★ログイン | 依頼者（AI は S5 で裏付け） | 依頼者が初期管理者でログインし、ホームが開ける。ログインの照合は停止の列を含む利用者の行を読むため、V9 の後の内部DB で既存の利用者が使えることの裏付けになる |
| S4 | 利用者の管理の画面の一覧 | 依頼者 | サイドバーの「利用者の管理」（`/admin/users`）から一覧（「利用者の一覧」）が開ける。自分の行に「あなた」の印がある。「状態」の列に「利用停止」の印の利用者が 0 人（V9 で既存の利用者が有効のままであることの裏付け）。依頼者は件数（全体の件数と「利用停止」の件数）と印の有無だけを伝える |
| S5 | 監査の記録 | AI | README の「監査ログの確かめ方」のとおり、アプリを止めて内部DB を `~/.mastersmith-backup/`（権限 700）へ複写し、アプリを起動し直してから、複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて、配備の時刻以降の `audit_events` を種類と結果ごとに数える。`LOGIN_SUCCEEDED` が1件以上あり、利用者の管理の種類（`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`USER_SUSPENDED`・`USER_RESUMED`・`LOGIN_FAILURES_RESET`）が 0 件。アプリを止める間（数秒）は依頼者に伝えてから行う |

S1・S2 のコマンド（値を表示しない形）:

```bash
# S1: レベルごとの件数と、WARN・ERROR のロガーと件数だけ
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | .level' | sort | uniq -c
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.level=="WARN" or .level=="ERROR") | .logger' | sort | uniq -c

# S2: Flyway の版 9 への移行と、版 9 になったことの件数（それぞれ 1 と出ること）
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'to version "9'
docker compose logs --no-log-prefix app | jq -R -r 'fromjson? | select(.logger | startswith("org.flywaydb")) | .message' | grep -c 'now at version v9'
```

- `up -d --build app` はコンテナを作り直すため、`docker compose logs app` は新しいコンテナの起動からのログだけになる。
- S2 のメッセージの文言は Flyway の版に左右される。0 件のときは、まずロガーの名前と件数（`.logger` だけ）を数え直して文言の違いを確かめ、値を表示しない形で判定する。
- S5 の監査の数えでは、配備の前のブラウザに残っていたセッションのログアウト（`LOGGED_OUT`）が1件混じることがある（前の Intent の S5 の見立て）。その他の種類が出たら、何が残ったかを依頼者に伝える。
- S5 の複写は確かめのための一時の写しで、配備の前のバックアップの代わりにはならない（Q1: B）。複写にはメールアドレスと接続元IPが残るため、権限 700 の置き場から動かさない。

## 4. V9 の事後の裏付け（Deployment Execution で確かめる）

U1 の `cicd-pipeline.md` 6節と Build and Test の引き継ぎ（U1-NFR10.1）が、Deployment Execution に求める確かめです。上のスモークテストで次のとおり裏付け、結果を Deployment Execution の記録に残します。

| 確かめること | 裏付け | 判定 |
|---|---|---|
| Flyway が V9 を当て、起動が止まらなかった | S1（healthy）・S2（版 9 への移行と版 9） | 両方が通れば当てた |
| 既存の利用者が V9 の後の内部DB で使える | S3（既存の初期管理者でログインでき、ホームが開ける）・S5（`LOGIN_SUCCEEDED`） | 両方が通れば使える |
| 既存の利用者が有効（`suspended` が false）のまま | S4（一覧の「利用停止」が 0 人。一覧の API は停止の列を含む利用者の行を読む） | 0 人なら有効のまま |

- U1 の `cicd-pipeline.md` 6節は「利用者の読み取り（`/api/me` など）」と書いています。Q4: A の範囲では、利用者の行の読み取りを一覧の API（S4）とログインの照合（S3）で裏付けます。
- 1つ前の版が V9 の後の内部DB で動くこと（U1-NFR10.2）は、この確かめに含みません（`cd-config.md` の 5節）。

## 5. 中止の条件

次のどれかが起きたら配備を中止し、`rollback-runbook.md` に従ってイメージだけを戻します。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない、Flyway の V9 が失敗した | 戻し先のイメージで起動し直す。V9 は1文の列の追加で、失敗したときは版 8 のまま残る見込み（H2 は DDL を巻き戻せないため、版と列の状態は戻した後の起動のログで確かめる） |
| S1〜S5 のどれかが通らず、その場で原因が設定だけと分からない | 戻し先のイメージで起動し直す |
| ERROR のログが出続ける、ログに秘密・メールアドレスそのものが出る | 同上 |
| S4 で「利用停止」の利用者が1人以上いる | 中止して原因を調べる（V9 の既定 false に反するため）。戻すかどうかは依頼者が決める（戻すと停止が効かない。`rollback-runbook.md` の 3節） |

## 6. 監視

- 手元の監視（lgtm、profile `monitoring`）は今動いていない。この段では起動しない（指標・警報・ダッシュボードはこの Intent で足していない。U3 の `monitoring-design.md`）。監視の確かめは Observability Setup の段で行う。
- 配備の直後は、`docker compose logs app`（値を表示しない形）と監査の記録（S5）で確かめる。

## Sources

- `cd-config.md`・`rollback-runbook.md`・`deployment-pipeline-questions.md`（Q1・Q3・Q4・まとめの確認）
- `construction/ci-pipeline/ci-config.md`・`construction/ci-pipeline/quality-gates.md`
- `construction/u1-user-suspension/infrastructure-design/cicd-pipeline.md`（6節）
- `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（1節・8節）・`cicd-pipeline.md`（6節）
- `construction/u4-admin-forbidden-ui/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（6節）
- `construction/u5-user-admin-ui/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（5節の順 8）
- `construction/build-and-test/build-and-test-summary.md`（U1-NFR10.1 の引き継ぎ）
- `README.md`（コンテナでの起動と確認、監査ログの確かめ方、利用者の管理の API、スキーマの変更（Flyway））
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`（一覧の種類が無いこと）、`backend/src/main/resources/logback-spring.xml`（ログの項目の名前 `level`・`logger`・`message`）、`frontend/src/features/useradmin/messages.ts`・`registration.ts`（画面の文言と URL）
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/deployment-strategy.md`・`operation/deployment-execution/smoke-test-results.md`

## Assumptions & Open Questions

- [assumption] S2 は、Flyway が版 9 への移行と結果を INFO で出すことを前提にしている（前の Intent の V7・V8 の配備で同じ形の INFO を記録した）。文言が違うときは 3節の注のとおりロガーと件数で判定し直す。
