# スモークテストの結果（261003-user-admin-followup）

承認済みの `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/deployment-strategy.md` の S1〜S6。値（メールアドレス・氏名など）は表示せず、キーの名前と件数だけで確かめた。

| ID | 確かめ | 結果 | 判定 |
|---|---|---|---|
| S1 | 健全性と起動のログ | healthy、`/actuator/health` は 200・UP。ログ 39 行はすべて JSON（JSON でない行 0）。INFO 37・WARN 2・ERROR 0。WARN は Flyway（`Database`、H2 の版の案内）と Spring の `BeanPostProcessorChecker` の各1件で、前の版と同じ | 合格 |
| S2 | 秘密・メールアドレスと移行 | 初期管理者の INFO のキーは `timestamp,level,logger,thread,message,maskedEmail`（`email` のキーは無い、値は表示していない）。Flyway の `Successfully validated` 1 件・`Migrating schema` 0 件（V9 のまま） | 合格 |
| S3 | ★ログイン | 依頼者が初期管理者でログインでき、ホームが開けた | 合格 |
| S4 | 右端の行の「操作」のメニュー（K2） | 依頼者: 広い幅と狭い幅の両方で、メニューは欠けずに画面の中に出た | 合格 |
| S5 | 閉じた後のフォーカス（K1） | 依頼者: 確かめの表示も、氏名と言語の入力の表示も、閉じた後にフォーカスが行の「操作」に戻った | 合格 |
| S6 | 監査の記録 | 配備の時刻 2026-10-04T00:15:09Z 以降: `LOGIN_SUCCEEDED` SUCCESS 1・`LOGGED_OUT` SUCCESS 1・`USER_ADMIN_REVOKED` SUCCESS 1・`USER_ADMIN_GRANTED` SUCCESS 1・`USER_SUSPENDED` SUCCESS 1・`USER_RESUMED` SUCCESS 1。停止中の利用者 0 人 | 合格（予定との差あり） |

## 予定との差

- S6 の予定は「利用者の管理の種類が 0 件」だった。依頼者が S4・S5 の間に印の付け外しと停止・再開を確定させた（その場で伺った答え）ため、期待を4件に合わせて数え、一致した。`LOGGED_OUT` の1件は、配備の前のブラウザのセッションのログアウトの見込み（前の手順の注のとおり）。
- 要件の FR1・FR2・FR3 の画面の直しを、配備したイメージと実際のブラウザで見直した（S4・S5。FR3 の言語の欄は見ていない）。

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-execution/deployment-log.md`
- `README.md`（監査ログの確かめ方）

## Assumptions & Open Questions

None.
