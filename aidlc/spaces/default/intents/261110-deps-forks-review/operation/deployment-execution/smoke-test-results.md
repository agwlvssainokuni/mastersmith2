# スモークテストの結果（smoke-test-results）— Intent 261110-deps-forks-review

`deployment-strategy.md` の S2〜S5 の結果です（S1 は `health-check-report.md`）。時刻は UTC（2026-11-12）。すべて通過し、配備は完了しました。

## 1. 結果

| # | 確かめ | 行った人 | 結果 |
|---|---|---|---|
| S2 | 未認証の要求とログ（20:31:34〜） | AI | (1) `/`・`/favicon.svg`・`/favicon.ico` が 200。(2) `/api/me` が 401、`Content-Type: application/problem+json`、`code` は `AUTHENTICATION_REQUIRED`。項目は `code`・`detail`・`instance`・`status`・`title`・`traceId`・`type` で、前の版と同じ形（Jackson 3.1.8）。(3) 入口の HTML が読む JavaScript（`index-D55ECMtF.js`・`useTranslation-DTk7Nmf5.js`）がどちらも WAR の中にある（WAR に無いもの 0 件）。(4) この間のアプリのログに ERROR 0・5xx 0。通過 |
| S3 | ★ログインと、見るだけの画面（Chrome、日本語と英語） | 依頼者 | 依頼者の答え「A, login 1, logout 1」。(a) ホーム、(b) DSL の状態、(c) 業務データの一覧と検索の欄（日時の列のヒント、形の合わない日時での誤りの文言）、(d) 役割の一覧、(e) 利用者の管理の一覧を、日本語と英語で見て、崩れや文言の鍵の表示は無かった。ログイン 1 回・ログアウト 1 回、予定に無い操作なし。通過 |
| S4 | 画面の操作の間のログ（20:31:34〜22:15:01） | AI | 6 行すべて JSON。ERROR 0・5xx 0。WARN 4 はどれも `GlobalExceptionHandler` の想定内の 4xx: `REFRESH_FAILED` 401 が 1（ログインの前の画面の読み込みで、リフレッシュトークンがまだ無いとき）と `DSL_PREVIEW_NOT_FOUND` 404 が 3（DSL の状態の画面を開いたとき。プレビューは 0 件。日本語と英語で開いた分）。前の配備の S4 と同じ種類。ほかは INFO 2。通過 |
| S5 | 監査と状態の確かめ | AI | 配備の後の複写を台本 A3 で数えた（`deployment-log.md` の 2.2）。T0 の後の監査は `LOGIN_SUCCEEDED` `SUCCESS` 1・`LOGGED_OUT` `SUCCESS` 1、ほか 0。全体 342 → 344。スキーマの版 14、初期管理者・利用者・DSL・プレビューの数は配備の前と同じ。`build/count-db-copy/` に `work-*` は残っていない。通過 |

## 2. 監査の期待との照らし（`deployment-strategy.md` の 3.1）

| 種類 | 結果 | 期待 | 実際 |
|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 1（依頼者のログインの回数） | 1 |
| `LOGGED_OUT` | `SUCCESS` | 1（依頼者のログアウトの回数） | 1 |
| ほかのすべての種類と結果 | — | 0 | 0 |

- 業務データの検索（誤りの文言を出した操作を含む）と、表示の設定の言語の切り替えは、監査に残らなかった（期待どおり）。

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（S2〜S5・3.1）
- 質問と答え: `deployment-execution-questions.md`（Q4）
- `curl`（未認証の要求）、`unzip -Z1`（配信と WAR の比べ）、`docker logs`（レベル・ロガー・`code`・`status` だけを数えた）、`node scripts/count-db-copy.mjs`
- `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`
- Build and Test: `construction/build-and-test/test-results.md`（E2E 298 件）

## Assumptions & Open Questions

None.
