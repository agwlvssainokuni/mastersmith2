# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。深さ Minimal のため、警報の式と運用の記録に関わる API だけを書いた。ほかの API（DSL の管理・プリファレンス・パスワードの変更・見た目の設定・問題の種類の説明など）は数えていない。今回の Intent は API の中身を読んでおらず、下の表の実装の列は前回（`e68f54d`）の記録である。

## 警報と運用の記録に関わる外部の API（HTTP）

| メソッド・パス | 実装 | アクセス | 関わり |
|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController` | 公開 | 警報 `ms-login-p95`（しきい値 1000 ms）の対象 |
| `POST /api/auth/session/refresh` | 同上 | 公開・Origin の一致 | 警報 `ms-refresh-p95`（しきい値 1000 ms）の対象 |
| `GET /api/admin/check` | `access/web/AdminCheckController` | 管理者のみ | 警報 `ms-check-p95`（しきい値 300 ms、境界に無い。K-15）の対象 |
| `POST /api/admin/invitations` | `invitation/web/InvitationAdminController` | 管理者のみ | 送信が失敗しても 201 で `sendResult: FAILED` を返す（前回の K-8） |
| `POST /api/admin/invitations/{invitationId}/resend` | 同上 | 管理者のみ | 送信が失敗しても 200 で `sendResult: FAILED` を返す（前回の K-8） |
| `POST /api/registration/verify`・`POST /api/registration/complete` | `invitation/web/RegistrationController` | 公開（差し込み口 order 310 で2つの POST だけ） | 拒否は 404 `REGISTRATION_LINK_INVALID`。完了の拒否は監査に `REGISTRATION_FAILED` が残る（前回の K-8） |
| `GET /actuator/health` | Actuator（Web に公開するのは health だけ、詳細なし） | 公開 | 変わらない。指標の窓口（`/actuator/metrics`）は公開していない |

## 指標（Micrometer → OTLP）

| 名前 | 作る場所 | 分布（バケット） | 使う側 |
|---|---|---|---|
| `http.server.requests` | Spring MVC の観測（Spring Boot の既定） | `slo` の境界 100・250・500・1000・2000・5000 ms と `+Inf`（`346c719` で足された） | 警報 `ms-5xx-ratio`・`ms-forbidden`（件数）、`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`（バケット） |
| `mastersmith.mail.send` | `mail/service/SmtpMailSender`（`Observation`） | `slo` の境界（上と同じものと 10000 ms） | ダッシュボードの送信の行 |
| `mastersmith.dsl.operation` | `dslmanage/service/DslOperationMetrics` | あり（`publishPercentileHistogram()`） | ダッシュボードの DSL の行 |

送信は `management.otlp.metrics.export`（`step: 60s`、既定は無効）。流れと K-15 の本文は `architecture.md` の Interaction Diagrams 1。

## 起動時の処理（API ではないがログの出口）

| 処理 | 実装 | 出すもの |
|---|---|---|
| 初期管理者の作成 | `user/service/InitialAdminInitializer` | INFO のログ3か所（キー `email` の値を含む）、設定が無い・正しくないときの WARN（値なし）。K-11 の本文は `component-inventory.md` の `user` |

## 前回の K-8 承認済みの運用の記録の誤り

この節は前回（`e68f54d`）の記録で、今回は読み直していない。K-8 は `7c2fea4`（README の画面・監視・既知の制約を直し「警報と対応の手順」の節を足す）で扱われた（件名による）。

前の Intent（`260925-user-management`）の運用の記録（`alarms.md`・`log-queries.md`・`runbooks.md` の RB-17）に、コードと合わない記述があった。

- **登録の完了の拒否は監査に残る。** 形の誤ったトークンでも `RegistrationService.complete` が `RegistrationFailedEvent` を出し、`audit` が `REGISTRATION_FAILED` を記録する（`architecture.md` の Interaction Diagrams 2）。「形の誤ったトークンの拒否は監査に残らない」という記述は、登録の完了については誤り。リンクの確かめ（`/verify`）の拒否が監査に残るかは、読んでいない。
- **招待メールの送信の失敗は、既存の警報に当たらない。** 招待は 201・送り直しは 200（`sendResult: FAILED`）で返り、ログは WARN と INFO。5xx の割合（`ms-5xx-ratio`）と ERROR のログ（`ms-error-logs`）の式はどちらも拾わない。

帰結: 誤りのある文書は承認済みの記録のため書き換えず、README の側で正しい形にそろえる（`project.md` の Way of Working）。
