# ログと監査ログの見方（log-queries）

前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/log-queries.md`、`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/log-queries.md`）を正とし、今回の差だけを書きます。

## 1. Loki に届く今回のログ（使い捨ての環境で確かめた）

外部エクスポートを有効にした使い捨ての環境から送らせ、直近のログ 34 行を集めました（Q5: C）。今回の Intent のログは、次のキー（Loki の属性）で届きました。

| ロガー | 本文 | レベル | キー |
|---|---|---|---|
| `SmtpMailSender` | メールを送信しました | INFO | `templateId`・`language` |
| `SmtpMailSender` | メールを送信できませんでした | WARN | `templateId`・`language`・`failureKind`（例 `TIMEOUT`）・`exceptionType` |
| `InvitationMailDispatcher` | 招待メールを送れませんでした。一覧から送り直せます | INFO | `invitationId`・`operation`（例 `INVITE`）・`failureKind` |
| `GlobalExceptionHandler` | 要求をエラー応答に変換しました | WARN | `code`・`status`・`exceptionType` |
| `InitialAdminInitializer` | 初期管理者を作成しました／既にいるため、作成しませんでした | INFO | `email`（値は `[REDACTED]`） |

- 招待・送り直し・取り消し・登録の完了・パスワードの変更・プリファレンスの保存の成功は、アプリのログを出しません（監査の記録に残る）。
- **伏せ字と漏えい**: 34 行の本文と属性に、メールアドレス（`@example.test`・`@example.com`）・招待のトークン・招待の URL（`/register`）・`Bearer` の値は、どれも 0 件でした。`email` の値は2件とも `[REDACTED]` でした。
- 送信の失敗のログの `failureKind` は大文字（`TIMEOUT`）、指標のタグ `mail_failure_kind` は小文字（`timeout`）です。問い合わせを書くときは合わせてください。

## 2. Explore（Loki）で使う問い合わせ

| 見たいもの | LogQL |
|---|---|
| 送信の失敗（種類ごと、1 日） | `sum by (failureKind) (count_over_time({service_name="mastersmith"} \|= "メールを送信できませんでした" [1d]))` |
| 招待のメールを送れなかった招待 | `{service_name="mastersmith"} \|= "招待メールを送れませんでした"`（`invitationId` で一覧の招待と突き合わせる） |
| 招待の定期の削除の失敗（N3、警報なし） | `{service_name="mastersmith"} \|= "保存期間を過ぎた招待の削除に失敗しました"` |
| 招待の定期の削除の結果 | `{service_name="mastersmith"} \|= "保存期間を過ぎた招待を削除しました"` |
| 登録の完了・パスワードの変更で拒否された要求の内訳 | `sum by (code) (count_over_time({service_name="mastersmith"} \|= "要求をエラー応答に変換しました" [1d]))` |
| 見た目の設定の警告（U8、0 が期待値） | `{service_name="mastersmith"} \|= "見た目の設定に許されない値が指定されたため、既定の値を使います"` |

- 定期の削除のログの本文は `backend/src/main/java/cherry/mastersmith/invitation/service/InvitationCleanupJob.java` と同じです。削除は毎日 3 時 45 分に動くため、この段では実際に出ることを確かめていません（式だけ）。

## 3. 総当たりの疑いの問い合わせ（R1、Q3: A）

- 疑いがあるときだけ（`ms-lock`・`ms-rejected` の警報や、ログイン・登録の失敗の増加に気づいたとき）、依頼者が README の「監査ログの確かめ方」の手順（アプリを数秒止めて内部DB を複写）で流します。定期には流しません。
- README の2つの問い合わせ（誤ったトークンの登録の完了の数え方と、今のパスワードの誤りの数え方）を、使い捨ての環境の内部DB の複写（`ACCESS_MODE_DATA=r`）で実際に流しました。列の名前（`failure_reason`・`source_ip`・`actor_user_id`・`result`・`event_type`・`occurred_at`）はどれも正しく、次の値が返りました（送り元の IP と利用者の ID は数だけを見た）。
  - 登録の完了の失敗: `INVITATION_ALREADY_USED` が 1 件（送り元 1）。
  - 今のパスワードの誤り: 利用者 1 名で 2 件。
- 同じ複写の監査の内訳: `INVITATION_ISSUED` 4・`INVITATION_RESENT` 1・`INVITATION_CANCELLED` 1・`REGISTRATION_COMPLETED` 1・`REGISTRATION_FAILED`（`INVITATION_ALREADY_USED`）1・`PASSWORD_CHANGED` 成功 2・失敗（`CURRENT_PASSWORD_MISMATCH`）2・`LOGIN_SUCCEEDED` 5。
- 入力の誤り（400 `VALIDATION_FAILED`）の登録の完了は、監査に残りませんでした（設計どおり、登録の失敗はトークンで招待を引いた後の拒否だけを残す）。リンクの確かめの拒否（404）も残りません。そのため、形の正しくないトークンの総当たりは監査の問い合わせでは見えず、ダッシュボードの「API ごとの要求の数」（`/api/registration/verify` の 404）で見ます。

## Sources

- `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/observability-setup-questions.md`（Q3: A・Q5: C、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/log-queries.md`
- `README.md`（「監査ログの確かめ方」「利用者のプリファレンスとパスワードの変更（U2）」「外部エクスポートの確かめ方」）
- `backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java`、`backend/src/main/java/cherry/mastersmith/invitation/service/InvitationCleanupJob.java`
- `construction/u2-user-preferences/nfr-design/observability-design.md`、`construction/u3-invitation/infrastructure-design/monitoring-design.md`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:3100/loki/api/v1/query_range?...`、`java -cp h2-2.4.240.jar org.h2.tools.RunScript -url "jdbc:h2:file:...;ACCESS_MODE_DATA=r"`（使い捨ての環境の複写）

## Assumptions & Open Questions

None.
