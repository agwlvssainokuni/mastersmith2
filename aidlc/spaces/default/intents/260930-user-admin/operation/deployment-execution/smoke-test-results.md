# スモークテストの結果（260930-user-admin）

承認済みの `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/deployment-strategy.md` の S1〜S5。値（メールアドレス・氏名など）は表示せず、キーの名前と件数だけで確かめた（`project.md` の Corrections）。

| ID | 確かめ | 結果 | 判定 |
|---|---|---|---|
| S1 | healthy と `/actuator/health` | コンテナは healthy。`/actuator/health` は 200・`{"status":"UP"}`。mailpit・targetdb-postgres も動いたまま | 合格 |
| S2 | 起動のログ | ログ 40 行はすべて JSON。ERROR 0 件。WARN 2 件（Spring の BeanPostProcessorChecker と、Flyway の「H2 2.4.240 は検証済みの版より新しい」の注意。前の版と同じ）。Flyway は 9 つの移行を検証し、`Current version of schema "PUBLIC": 8` から `Migrating schema "PUBLIC" to version "9 - u1 user suspension"`、`now at version v9`。初期管理者の INFO にキー `maskedEmail` があり、`email` のキーは無い（値は表示していない） | 合格 |
| S3 | 依頼者のログイン | 依頼者が初期管理者でログインできた | 合格 |
| S4 | 利用者の管理の一覧 | 一覧が出た（依頼者）。**行の「操作」のメニューを開くと、メニューの右側が画面の外にはみ出して欠ける**（不具合）。依頼者の決定 B で既知の不具合として後の Intent で直す | 合格（既知の不具合つき） |
| S5 | 監査の記録 | アプリを約 10 秒止めて内部DB を複写し、複写を読み取り（`ACCESS_MODE_DATA=r`）で開いて数えた（展開した複写は数えた後に消した）。配備の時刻 2026-10-03T12:54:56Z 以降: `LOGIN_SUCCEEDED` SUCCESS 6・`LOGIN_FAILED` FAILURE 1・`LOGGED_OUT` SUCCESS 5・`USER_ADMIN_GRANTED` SUCCESS 2・`USER_ADMIN_REVOKED` SUCCESS 1・`USER_SUSPENDED` SUCCESS 1・`USER_RESUMED` SUCCESS 1。`flyway_schema_history` の最後は版 9・成功。利用者 2 人、停止中の利用者 0 人 | 合格（予定との差あり） |

## 予定との差

- S5 の予定は「監査に残るのは `LOGIN_SUCCEEDED` だけ、利用者の管理の種類は 0 件」だった。実際には管理の操作が5件記録されていた。依頼者が画面で行った操作である（「自分が行った」）。5つの操作の種類が監査に記録されることの、実際の配備での裏付けにもなった（操作した人・対象の利用者の ID は見ていない）。
- S4 の不具合は、`deployment-log.md` の3節のとおり。

## V9 の事後の裏付け（U1-NFR10.1）

- 起動のログで Flyway が V8 → V9 を当て、`flyway_schema_history` の最後が版 9・成功であることを確かめた（S2・S5）。
- 既存の初期管理者でログインでき（S3）、利用者の一覧（`users.suspended` を読む API）が出た（S4）。停止中の利用者は 0 人で、既存の利用者は有効のまま（V9 の既定 `FALSE`）。
- 判定: U1-NFR10.1 は Met（配備した環境で確かめた）。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/build-and-test-summary.md`（U1-NFR10.1 の行）
- `README.md`（監査ログの確かめ方）

## Assumptions & Open Questions

None.
