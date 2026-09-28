# 配備の戦略（deployment-strategy）

Intent 260925-user-management の配備の仕方と、配備の後の確かめです。流れの全体は `cd-config.md`、戻しは `rollback-runbook.md` にあります。

## 1. 方式

| 項目 | 値 |
|---|---|
| 方式 | 1台の置き換え（Recreate）。青緑・カナリア・段階の昇格は無い（配備先が開発者の PC 上のコンテナ1つのため） |
| 止まる時間 | バックアップの間（アプリを止める）と、新しい版の起動（Flyway の V7・V8 を含む）の間。止めている間に出た招待は FAILED で確定し、起動の後に一覧から送り直せる（U3 の `infrastructure-specification.md`） |
| 機能の切り替え（feature flag） | 使わない。招待はベース URL と SMTP の設定がそろったときだけ使える（揃わなければ一覧の API が理由を返す） |
| 環境の昇格 | 無い（検証環境・本番環境は配備先が決まってから。project.md の Deployment） |
| 承認 | 依頼者（team.md の Deployment。本番配備の承認者は依頼者） |

## 2. 配備の前の関門（Q1: A）

| 確かめ | 通る条件 |
|---|---|
| 未コミットの変更 | アプリのソースに無い（ワークフローの記録と監査ログのディレクトリは除く。project.md の Deployment） |
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | `BUILD SUCCESSFUL`、対象DB のテストの飛ばし 0 件 |
| `./gradlew e2eTest`（Mailpit を起動） | すべて通る。json の報告にトークン・招待の URL・初期管理者のメールアドレスが無い |
| CI | 参考として記録する。`66fe981` の既知の失敗（2つの時間切れ）は次の Intent で直す |
| 配備の前の k6 | 流さない（Q2: B）。performance-validation に任せる |

## 3. 配備の後の確かめ（スモークテスト）

健全性（`/actuator/health` が UP）と、下のすべてが通るまで、配備の完了としません（team.md の Deployment）。
- パスワードの要る操作は依頼者が行います。AI は、アプリのログと監査の記録で裏付けます。
- 個人に関する値は表示しません（project.md の Corrections）。
- ★の付いた操作は、配備した内部DB と監査に行が残ります（消す操作はありません）。送る前に、依頼者に伝えます。

| # | 確かめ | 行う人 | 通る条件 | 出典 |
|---|---|---|---|---|
| S1 | 起動のログ | AI | Flyway の V7・V8 が当たった。メールの設定の状態 `CONFIGURED`・方式 `NONE`・テンプレートの件数。招待の INFO が enabled。ベース URL の形の誤りと、見た目の設定の WARN が無い。ERROR が無い | U1・U3・U8 の `cicd-pipeline.md`、U3 の `monitoring-design.md` |
| S2 | ログイン画面 | AI | `http://localhost:8080/` でログイン画面が出る。`<html lang>` が既定の ja。言語の切り替えがある | U4 の `cicd-pipeline.md` |
| S3 | 見た目の設定の API（Q4: A） | AI | 未認証の `GET /api/appearance` が 200 で、`brandColor`・`fontFamily` の2項目 | U8 の `cicd-pipeline.md` |
| S4 | `/register`（フラグメントなし） | AI | 「このリンクは使えません」の表示が出る。API を呼ばず、内部DB・監査を変えない | U6 の `cicd-pipeline.md` |
| S5 | ★ログインと管理 | 依頼者（AI は監査で裏付け） | 初期管理者でログインし、ホーム・「管理」が開ける。`LOGIN_SUCCEEDED` が記録される | README の配備の確認 |
| S6 | 招待の一覧（U5） | 依頼者 | サイドバーの「利用者の招待」から一覧が開ける | U5 の `cicd-pipeline.md` |
| S7 | ★招待から登録の完了まで（Q4: B） | 依頼者（AI は監査とログで裏付け） | Mailpit を `docker compose --profile mail up -d mailpit` で起動し直す。依頼者が画面から予約されたドメイン（例 `@example.com`）の宛先に招待を出す。Mailpit の画面（`http://127.0.0.1:8025`）でメールを見て（U1-NFR11.1 の目視）、リンクから登録を終え、その利用者でログインする。`INVITATION_ISSUED`・`REGISTRATION_COMPLETED`・`LOGIN_SUCCEEDED` が記録される。利用者1人と監査の記録が残る | U3 の `cicd-pipeline.md`・`infrastructure-specification.md` |
| S8 | ★プリファレンスの保存（Q4: C） | 依頼者 | 表示の設定を変えて保存し、画面に反映される。元に戻して保存する。プリファレンスの保存には監査の記録の種類が無いため、監査には残らない（Q4 の C の説明の「監査が残る」は誤りだった。内部DB の設定の値だけが変わり、元に戻す） | U7 の `cicd-pipeline.md`、`AuditEventType` |
| S9 | ★パスワードの変更（Q4: D） | 依頼者（AI は監査で裏付け） | S7 で作った利用者のパスワードを変え、新しいパスワードでログインできる。`PASSWORD_CHANGED` が記録される。初期管理者のパスワードは変えない | U2・U7 の `cicd-pipeline.md` |
| S10 | ★ログアウト | 依頼者 | ログイン画面に戻る。`LOGGED_OUT` が記録される | README の配備の確認 |
| S11 | DSL（見本の対象DB） | 依頼者 | サイドバーの「DSL」で今の状態が表示される（前の Intent から続く確かめ。スキーマの読み込みは今回は行わない） | README の配備の確認 |
| S12 | ログの漏れ | AI | `docker compose logs app` の中の `/register#token=` と `@example.com` の件数が 0（件数だけを見る） | U3 の `cicd-pipeline.md` |

確かめが終わったら、Mailpit を止めて消します（`docker compose stop mailpit && docker compose rm -f mailpit`。Q5: A）。

## 4. 中止の条件

次のどれかが起きたら配備を中止し、`rollback-runbook.md` に従って戻します。

| 条件 | 戻し方 |
|---|---|
| 起動が healthy にならない、Flyway の V7・V8 が失敗した | 第二の手（バックアップの展開）と `.env` の複写の戻し（中止のため、Q3: A） |
| スモークテストの S1〜S12 のどれかが通らず、その場で原因が設定だけと分からない | 第一の手（イメージだけ）。データが壊れていれば第二の手 |
| ERROR のログが出続ける、ログに秘密・メールアドレスが出る | 第一の手 |

## 5. 監視

- 手元の監視（Grafana、profile `monitoring`）は、配備の後の observability-setup で扱います。依頼者の決定で、Explore を使えるようにする設定（`GF_USERS_VIEWERS_CAN_EDIT`）と、ログの外部エクスポートの `.env` の2行も、その段で行います。
- 配備の直後は、`docker compose logs app` と監査の記録（README の「監査ログの確かめ方」）で確かめます。

## Sources

- `construction/ci-pipeline/ci-config.md`・`construction/ci-pipeline/quality-gates.md`
- 各単位の `construction/u*/infrastructure-design/cicd-pipeline.md`・`infrastructure-specification.md`（U3 は `monitoring-design.md` も）
- `operation/deployment-pipeline/deployment-pipeline-questions.md`
- `README.md`（コンテナでの起動と確認、監査ログの確かめ方、手元でメールを見る）
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

None.
