# スモークテストの結果（smoke-test-results）

配備した版（`83b572b`、イメージ `sha256:9e5243a30b77…`）の配備の後の確かめです（2026-09-29 00:32〜00:43）。項目は `operation/deployment-pipeline/deployment-strategy.md` の 3 節の S1〜S12 です。

- 画面の操作は、依頼者が `http://localhost:8080/` で行いました（依頼者の報告は「終わった。問題なし。」）。
- AI は、アプリのログと監査の記録で裏付けました。
- 監査の記録は、配備の後に取った複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて数えました。
- 個人に関する値は、表示していません。

**判定: すべて通過**

| # | 確かめ | 行った人 | 結果 | 証拠 |
|---|---|---|---|---|
| S1 | 起動のログ | AI | 通過 | V7・V8 を当てた。メール `CONFIGURED`・`NONE`・テンプレート2件。招待 `enabled: true`。ERROR 0（`deployment-log.md` の 3 節） |
| S2 | ログイン画面 | AI と依頼者 | 通過 | `curl` で `<html lang="ja">`・200。言語の切り替えを依頼者が画面で確かめた |
| S3 | 見た目の設定の API | AI | 通過 | 未認証の `GET /api/appearance` が 200 で `{"brandColor":"blue","fontFamily":"sans"}` の2項目 |
| S4 | `/register`（フラグメントなし） | AI と依頼者 | 通過 | `curl` で 200・`text/html`。「このリンクは使えません」を依頼者が画面で確かめた |
| S5 | ログインと管理 | 依頼者 | 通過 | ホーム・「管理」が開けた。`LOGIN_SUCCEEDED` がある |
| S6 | 招待の一覧 | 依頼者 | 通過 | サイドバーの「利用者の招待」から開けた |
| S7 | 招待から登録の完了まで | 依頼者 | 通過 | Mailpit を起動し直して招待を出し、Mailpit の画面でメールを見て（U1-NFR11.1 の目視）、登録を終えてログインした。`INVITATION_ISSUED` 1・`REGISTRATION_COMPLETED` 1。招待は1件で完了済み。利用者は2人になった |
| S8 | プリファレンスの保存 | 依頼者 | 通過 | 表示の設定を変えて保存し、元に戻した（監査の記録の種類は無い） |
| S9 | パスワードの変更 | 依頼者 | 通過 | S7 の利用者のパスワードを変え、新しいパスワードでログインできた。`PASSWORD_CHANGED` 1 |
| S10 | ログアウト | 依頼者 | 通過 | ログイン画面に戻った。`LOGGED_OUT` がある |
| S11 | DSL | 依頼者 | 通過 | 今の状態が表示された |
| S12 | ログの漏れ | AI | 通過 | アプリのログ 54 行のうち、`/register#token=` 0 件。`@example.com` は1件で、初期管理者の既存の INFO（受け入れ済み）の1行だけ。それを除くと 0 件 |

## 監査の記録の件数（配備の後、2026-09-28T15:31Z 以降）

| 記録 | 結果 | 件数 |
|---|---|---|
| `LOGIN_SUCCEEDED` | SUCCESS | 5 |
| `LOGGED_OUT` | SUCCESS | 5 |
| `INVITATION_ISSUED` | SUCCESS | 1 |
| `REGISTRATION_COMPLETED` | SUCCESS | 1 |
| `PASSWORD_CHANGED` | SUCCESS | 1 |
| `LOGIN_FAILED` | FAILURE（`PASSWORD_MISMATCH`） | 1 |

`LOGIN_FAILED` の1件は、確かめの中のパスワードの入力の誤りとみています（依頼者の報告は問題なし）。

## アプリのログの WARN（確かめの間）

| 種類 | 件数 | 見立て |
|---|---|---|
| `REFRESH_FAILED`（401） | 3 | ログインの前・ログアウトの後のトークンの更新（想定どおり） |
| `DSL_PREVIEW_NOT_FOUND`（404） | 8 | DSL の画面でプレビューが無いとき（想定どおり） |
| `AUTHENTICATION_FAILED`（401） | 1 | 上の `LOGIN_FAILED` と同じ |
| 起動のときの案内 | 2 | Spring の Bean の案内・Flyway の H2 の版の案内 |

ERROR は0件でした。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（3 節）・`cd-config.md`
- `operation/environment-provisioning/environment-inventory.md`
- `construction/build-and-test/test-results.md`（配備と同じソースでの E2E）
- アプリのログ（`docker compose logs app`）と、配備の後の複写の `audit_events`・`invitations`・`users` の件数

## Assumptions & Open Questions

None.
