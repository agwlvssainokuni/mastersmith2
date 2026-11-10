# スモークテストの結果（smoke-test-results）— Intent 261108-ci-checks-release

`deployment-strategy.md` の S2〜S5 の結果です（S1 は `health-check-report.md`）。時刻は UTC（2026-11-10）。すべて通過し、配備は完了しました。

## 1. 結果

| # | 確かめ | 行った人 | 結果 |
|---|---|---|---|
| S2 | 未認証の要求とログ（14:55:03〜） | AI | (1) `/` 200（`text/html`）、`/favicon.svg` 200（`image/svg+xml`）、`/favicon.ico` 200（`image/x-icon`）。(2) `/api/me/work-role` が 401 の Problem Details、`code` は `AUTHENTICATION_REQUIRED`。(3) WAR の中の画面の JavaScript 41 個をすべて配信から取り、WAR の中と同じ中身で 200。入口から読む `index-FLDWXX1R.js`・`useTranslation-CkzyOXKK.js` に `dsl.status.at` が 0 件。(4) この間のアプリのログに ERROR 0・5xx 0。通過 |
| S3 | ★ログインと、コードを消した画面（Chrome） | 依頼者 | 依頼者の答え「A, login 1, logout 1」。(a) DSL の状態、(b) 業務データの一覧のページの件数の切り替えとページ送り、(c) 役割の一覧・権限の木・割り当て、(d) グループの一覧、(e) 役割の引き継ぎ、(f) 利用者の管理の一覧、(g) 表示のタイムゾーンの選択肢を開き、崩れや文言の鍵の表示は無かった。ログイン 1 回・ログアウト 1 回、予定に無い操作なし。通過 |
| S4 | 画面の操作の間のログ（14:55:03〜14:59:53） | AI | 4 行すべて JSON。ERROR 0・5xx 0。WARN 2 はどちらも `GlobalExceptionHandler` の想定内の 4xx: `REFRESH_FAILED` 401（ログインの前の画面の読み込みで、リフレッシュトークンがまだ無いとき）と `DSL_PREVIEW_NOT_FOUND` 404（DSL の状態の画面で、プレビューが 0 件のとき。2.1 の数えでプレビュー 0）。ほかは対象DB の接続のプールの起動の INFO 2。通過 |
| S5 | 監査と状態の確かめ | AI | 配備の後の複写を台本 A3 で数えた（`deployment-log.md` の 2.2）。T0 の後の監査は `LOGIN_SUCCEEDED` `SUCCESS` 1・`LOGGED_OUT` `SUCCESS` 1、ほか 0。全体 340 → 342。スキーマの版 14、初期管理者・利用者・DSL・プレビューの数は配備の前と同じ。`build/count-db-copy/` に `work-*` は残っていない。通過 |

## 2. 監査の期待との照らし（`deployment-strategy.md` の 3.1）

| 種類 | 結果 | 期待 | 実際 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 1（依頼者のログインの回数） | 1 |
| `LOGGED_OUT` | `SUCCESS` | 1（依頼者のログアウトの回数） | 1 |
| ほかのすべての種類と結果 | — | 0 | 0 |

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（S2〜S5・3.1）
- 質問と答え: `deployment-execution-questions.md`（Q3）
- `curl`（未認証の要求）、`unzip -p`・`cmp`（配信と WAR の比べ）、`docker logs`（レベル・ロガー・`code`・`status` だけを数えた）、`node scripts/count-db-copy.mjs`
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`
- Build and Test: `construction/build-and-test/test-results.md`（E2E 298 件）

## Assumptions & Open Questions

None.
