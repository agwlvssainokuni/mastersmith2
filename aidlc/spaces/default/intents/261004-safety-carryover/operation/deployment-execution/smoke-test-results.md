# スモークテストの結果（smoke-test-results）

`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/deployment-strategy.md` の 3節（S1〜S4、Q2: A）の結果です。値（メールアドレス・パスワード）は表示せず、レベル・ロガー・キーの名前・件数だけを見ました。上流の配備の手順は `cd-config.md`、Build and Test の結果は `aidlc/spaces/default/intents/261004-safety-carryover/construction/build-and-test/test-results.md`。

## 結果

| # | 確かめ | 結果 | 判定 |
|---|---|---|---|
| S1 | 健全性と起動のログ | healthy、`/actuator/health` は `{"status":"UP"}`。INFO 36・WARN 3・ERROR 0。WARN のロガーは `InitialAdminInitializer`（救済、見込みどおり）1・`org.flywaydb…Database`（H2 の版の案内、前から）1・`PostProcessorRegistrationDelegate$BeanPostProcessorChecker`（前から）1 | 合格 |
| S2 | 初期管理者の起動のログ | WARN「初期管理者を救済しました」1 行、条件 `SUSPENDED`（見込みどおり）。キーは `timestamp`・`level`・`logger`・`thread`・`message`・`maskedEmail`・`conditions`（`email` は無い）。ログ全体で `.env` の初期管理者のメールアドレスそのものを含む行は 0 件。`Migrating schema` 0 件。起動 6.269 秒 | 合格 |
| S3 | ★ログイン | 依頼者が初期管理者（`.env` のパスワード）でログインし、ホームが開けた。ほかの操作はしていない | 合格 |
| S4 | 監査の記録（配備の開始 04:41:00Z 以降） | `INITIAL_ADMIN_RESCUED` 1 行（結果 `SUCCESS`、接続元 `system`、`rejection_kind` が `SUSPENDED`、操作した人は空、`entered_email` は空、対象の利用者あり）。`LOGIN_SUCCEEDED` 1 行。`INITIAL_ADMIN_CREATED` 0。ほかの種類 0 | 合格 |

## 追加で確かめたこと

- 配備の後の複写のためにアプリを起動し直した2回目の起動では、救済は働かず、INFO「初期管理者は既にいるため、作成しませんでした」が出た（救済の後は条件に当たらない。要件 FR1.3）。2回目の起動の WARN は前からの案内の2件だけ。
- 有効な管理者（印あり・停止していない）は、配備の前の 1 人から 2 人になった（救済で初期管理者が戻った）。

## 判定

S1〜S4 のすべてが合格。配備を完了とした（`team.md` の Deployment）。
