# 配備の記録（deployment-log）

Intent 260925-user-management（U1〜U8）の配備の記録です（2026-09-29、日本時間）。手順は `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`、決定は `deployment-execution-questions.md`（Q1: A・Q2: A）です。秘密の値と個人に関する値は書きません。

## 1. 配備した版と戻し先

| 項目 | 値 |
|---|---|
| 配備した版 | develop の `83b572b` |
| 新しいイメージ | `mastersmith:local`（`sha256:9e5243a30b77…`） |
| 戻し先 | `mastersmith:pre-user-management`（`sha256:305fddf4aa93…`）。前の Intent（260925-storage-memory-fixes）の `deployment-log.md` の新しいイメージと同じ ID で、戻し先として正しいことを確かめた |
| 配備の前のバックアップ | `~/.mastersmith-backup/mastersmith-data-202609290030-before-user-management.tgz`（15,001 B） |
| 配備の前の `.env` の複写 | `~/.mastersmith-env-before-user-management`（権限 600、中身は表示していない） |

## 2. 手順と時刻

| # | 手順 | 時刻 | 結果 |
|---|---|---|---|
| 1 | 未コミットの変更の確かめ（アプリのソース。記録と監査ログは除く） | — | 無し。配備する版は `83b572b` |
| 2 | 関門：`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST` などを渡して） | （7分18秒） | `BUILD SUCCESSFUL in 7m 18s`。単体 1,234・結合 562 件、失敗 0・飛ばし 0 |
| 3 | 関門：Mailpit を起動した `./gradlew e2eTest` | （3分18秒、00:28 より前に終了） | `BUILD SUCCESSFUL in 3m 18s`。90 件通過。json の報告にトークン・招待の URL は 0 件 |
| 4 | Mailpit を止めて消す（Q5: A） | 00:28 ごろ | E2E のメールを消した |
| 5 | 戻し用のタグ `docker tag mastersmith:local mastersmith:pre-user-management` | 00:28 ごろ | `sha256:305fddf4…` |
| 6 | ★アプリを止め、内部DB をバックアップ（依頼者の承認：1〜3 をまとめて承認） | 00:30:33〜00:30:42 | 1 のバックアップを作った |
| 7 | ★`.env` を中身を表示せずに複写し、4行を足す | 00:31 | 足す前は4項目とも 0 件、足した後は 1 件ずつ（項目の名前だけで数えた）。足した値は `MASTERSMITH_WEB_BASE_URL=http://localhost:8080`・`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`・`MASTERSMITH_MAIL_FROM=noreply@example.com`（Q2: A）。`.env` の権限は 600 のまま |
| 8 | ★`docker compose --profile targetdb-postgres up -d --build --wait` | 00:31:47〜00:31:59 | healthy。Flyway が V7・V8 を当てた（6 → 8） |
| 9 | 配備の後の確かめ S1〜S12 | 00:32〜00:43 ごろ | すべて通った（`smoke-test-results.md`） |
| 10 | 配備の後のバックアップ（戻しの練習と監査の確かめに使う） | 00:43:41〜00:43:48 | アプリを約7秒止めた |
| 11 | 戻しの練習（1つ前の版を 18080 で、10 の複写で起動） | 00:44〜00:50 ごろ | 通った（`health-check-report.md` の 3 節） |
| 12 | 片付け | 00:51 ごろ | 練習の環境（コンテナ・ボリューム・網）、練習用の `.env` の複写、確かめ用の複写、10 のバックアップを消した。Mailpit を止めて消した |

- アプリが止まっていたのは、6〜8 の約1分半と、10 の約7秒です。
- 配備の前の k6 は流していません（Deployment Pipeline の Q2: B。performance-validation で行う）。
- CI（`66fe981`）の失敗は既知として扱いました（Deployment Pipeline の Q1: A）。

## 3. 起動のログ（S1）

- Flyway：`Successfully validated 8 migrations`、`Current version of schema "PUBLIC": 6`、V7（u2 user preferences）と V8（u3 invitation）を当てて v8 になった。
- メール：設定の状態 `CONFIGURED`、暗号化 `NONE`、テンプレート2件（`invitation_en`・`invitation_ja`）。
- 招待：`enabled: true`、`unavailableReasons` は空。
- 見た目の設定の WARN は無し。
- ERROR 0 件。WARN は2件で、Spring の Bean の案内と、Flyway の H2 の版の案内（どちらも前の版から出ているもの）。
- 初期管理者の INFO「既にいるため、作成しませんでした」に、メールアドレスのキーが出ている。これは前からある動きで、U2 で据え置きを受け入れ済み（`build-and-test-summary.md` の U2-NFR2.1）。確かめのときに、その値（予約されたドメインのアドレス）を作業の画面に一度表示してしまった。以後は件数だけを見た。

## 4. 決まりとの差（記録）

| 決まり | 今回 |
|---|---|
| team.md の「CI が失敗したら次に進む前に直す」 | CI の失敗（2つの時間切れ）を既知として配備した（Deployment Pipeline の Q1: A、次の Intent で直す） |
| project.md の「配備の前の k6 は、試験済みのときだけ省く」 | Build and Test で負荷をかけていないが、配備の前の k6 は流していない（Deployment Pipeline の Q2: B） |
| project.md の Corrections「個人に関する値は表示せず、値の有無だけを確かめる」 | 初期管理者の既存のログの1行を確かめたとき、そのメールアドレスを作業の画面に一度表示した（記録には書いていない） |

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `operation/environment-provisioning/environment-inventory.md`
- `construction/build-and-test/test-results.md`
- `operation/deployment-execution/deployment-execution-questions.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-execution/deployment-log.md`（戻し先のイメージの ID）
- この段で実行したコマンドの出力（`docker`・`./gradlew`・`curl`）

## Assumptions & Open Questions

None.
