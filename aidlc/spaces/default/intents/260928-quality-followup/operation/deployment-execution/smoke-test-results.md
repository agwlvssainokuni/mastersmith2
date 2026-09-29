# スモークテストの結果（smoke-test-results）

`aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/deployment-strategy.md` の 3節の S1〜S8 の結果です（2026-09-29）。パスワードの要る操作は依頼者が行い、AI はアプリのログと監査の記録で裏付けました。個人に関する値は表示していません（ロガー・項目の名前・件数だけを見た）。監査の記録は、アプリを数秒止めて取った内部DB の複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて数えました。

| # | 確かめ | 行う人 | 結果 | 根拠 |
|---|---|---|---|---|
| S1 | 起動のログ | AI | **通過** | 21:59:33 に healthy（11 秒）。ERROR 0 件。WARN は2件で、Spring の Bean の案内と Flyway の H2 の版の案内（前の版から出ている既知のもの）。見た目の設定の WARN は無し |
| S2 | 見本の対象DB | AI | **通過** | `mastersmith-targetdb-postgres-1` が `postgres:18.6@sha256:5a5a84b19854…` で running |
| S3 | ログイン画面と画面の版 | AI | **通過** | `http://localhost:8080/` が 200、`<html lang="ja">`。配信される CSS（`assets/index-D-GXKkcp.css`）に `--color-primary-text`・`--color-primary-subtle-text`・`--color-danger-text`・`--color-success-text` が含まれる（make-you-chic-ui の `310e1ec` の文字用の色） |
| S4 | ★ログインと管理 | 依頼者 | **通過** | 依頼者がホームと「管理」を開けることを確かめた。監査に `LOGIN_SUCCEEDED`（22:00:39） |
| S5 | ★見た目（コントラスト） | 依頼者 | **行わなかった** | ブランドカラーは画面ではなく設定ファイルで決める項目で、手順書の誤りだった。コントラストは Build and Test の E2E（`construction/build-and-test/test-results.md` の 2節、ブランドカラーとテーマのすべての組）で確かめ済み |
| S6 | ★DSL（見本の対象DB の読み取り） | 依頼者 | **通過（差あり）** | 画面を開いたときプレビューは無かった（`DSL_PREVIEW_NOT_FOUND` 404 が2件、22:00:42・22:01:30）。見本の対象DB から既定の DSL を生成（`DSL_GENERATED` 22:01:34、`dsl.durationMs` 254）。その後、破棄ではなく適用された（`DSL_APPLIED` 22:01:51）。依頼者の決定（G1: A）でそのまま使う |
| S7 | ★ログアウト | 依頼者 | **通過** | 1回目: 22:00:39 のトークンが 22:06:07 に無効化され、`LOGGED_OUT`（22:06:07）。確かめ直し（G2: A）: `LOGIN_SUCCEEDED` 22:06:52、トークンの無効化と `LOGGED_OUT` 22:06:54 |
| S8 | ログの漏れ | AI | **差あり** | `/register#token=` は 0 件。`@example.com` は1件で、初期管理者の作成の INFO のログ（`InitialAdminInitializer` の `email` の項目）。前の Intent から出ている既存の動きで、今回の変更によるものではない。`project.md` の Forbidden に反するため、次の Intent の候補として記録した（`deployment-log.md` の 4・5節） |

## ほかに見た応答

- 22:00:33 の `REFRESH_FAILED`（401）: 配備の前のセッションの Cookie でトークンの更新を試みた応答（配備で新しいセッションになったため）。ふつうの動き。

## 判定（まとめの確かめ直しの後に保存）

S5 を除く確かめはすべて通過し、中止の条件（`deployment-strategy.md` の 4節）に当たるものは無かった。S5 は手順書の誤りで行わず、E2E の結果で代えた。S6 の適用と S8 の既存のログは、差として記録した。
