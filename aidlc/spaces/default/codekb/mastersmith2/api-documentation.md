# API（mastersmith2）

同じオリジンで配信する SPA から呼ぶ前提である（CORS の設定は無い）。深さ Minimal のため、今回の Intent に関わる API（警報の式と運用の記録の誤りの対象）だけを書いた。ほかの API（DSL の管理・プリファレンス・パスワードの変更・見た目の設定・問題の種類の説明など）は数えていない。アクセスの決まりの全体は前回の記録（`config/SecurityConfig.java` の差し込み口の順）から変わっていないかを今回は確かめていない。

## 今回の Intent に関わる外部の API（HTTP）

| メソッド・パス | 実装 | アクセス | 今回の関わり |
|---|---|---|---|
| `POST /api/auth/login` | `auth/web/AuthController` | 公開 | 警報 `ms-login-p95` の対象（K-6） |
| `POST /api/auth/session/refresh` | 同上 | 公開・Origin の一致 | 警報 `ms-refresh-p95` の対象（K-6） |
| `GET /api/admin/check` | `access/web/AdminCheckController` | 管理者のみ | 警報 `ms-check-p95` の対象（K-6） |
| `POST /api/admin/invitations` | `invitation/web/InvitationAdminController` | 管理者のみ | 送信が失敗しても 201 で `sendResult: FAILED` を返す（K-8） |
| `POST /api/admin/invitations/{invitationId}/resend` | 同上 | 管理者のみ | 送信が失敗しても 200 で `sendResult: FAILED` を返す（K-8） |
| `POST /api/registration/verify`・`POST /api/registration/complete` | `invitation/web/RegistrationController` | 公開（差し込み口 order 310 で2つの POST だけ） | 拒否は 404 `REGISTRATION_LINK_INVALID`。完了の拒否は監査に `REGISTRATION_FAILED` が残る（K-8） |
| `GET /actuator/health` | Actuator（Web に公開するのは health だけ、詳細なし） | 公開 | 変わらない。指標の窓口（`/actuator/metrics`）は公開していない |

招待・送り直しの応答の形は README の「招待と登録の完了（U3）」による（コントローラーは今回読んでいない）。

## 指標（Micrometer → OTLP）

| 名前 | 作る場所 | 分布（バケット） | 使う側 |
|---|---|---|---|
| `http.server.requests` | Spring MVC の観測（Spring Boot の既定） | 無い | 警報 `ms-5xx-ratio`・`ms-forbidden`（件数）、`ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`（バケット） |
| `mastersmith.mail.send` | `mail/service/SmtpMailSender`（`Observation`） | 無い | ダッシュボードの送信の行。95 パーセンタイルはトレースの `traces_spanmetrics_latency_bucket` で代用 |
| `mastersmith.dsl.operation` | `dslmanage/service/DslOperationMetrics` | あり（`publishPercentileHistogram()`） | ダッシュボードの DSL の行 |

送信は `management.otlp.metrics.export`（`step: 60s`、`${mastersmith.observability.export.enabled}` で有効、既定は無効）。流れと K-6 の本文は `architecture.md` の Interaction Diagrams 1。

## K-8 承認済みの運用の記録の誤り

前の Intent（`260925-user-management`）の運用の記録（`aidlc/spaces/default/intents/260925-user-management/operation/` の `alarms.md`・`log-queries.md`・`incident-response/runbooks.md` の RB-17）に、コードと合わない記述がある。`feedback-optimization/feedback-loop.md` 38 行が同じ誤りを「README や手順書の側で正しい形にそろえる」と書いている。

確かめた事実:

- **登録の完了の拒否は監査に残る。** 形の誤ったトークンでも `RegistrationService.complete` が `RegistrationFailedEvent` を出し、`audit` が `REGISTRATION_FAILED` を記録する（`architecture.md` の Interaction Diagrams 2）。したがって「形の誤ったトークンの拒否は監査に残らない」という記述は、登録の完了については誤り。README の「API のアクセス制御（U3）」の終わり（716 行付近、「誤ったトークンで完了を呼ぶたびに監査に `REGISTRATION_FAILED` が1行増えます」）はコードと合っている。リンクの確かめ（`/verify`）の拒否が監査に残るかは、今回読んでいない。
- **招待メールの送信の失敗は、既存の警報に当たらない。** 招待は 201・送り直しは 200（`sendResult: FAILED`）で返り、ログは WARN と INFO（README 837 行付近）。5xx の割合（`ms-5xx-ratio`）と ERROR のログ（`ms-error-logs`）の式はどちらも拾わない。README は「警報に当たらない」ことまでは書いていない。`runbooks.md` 196 行は「メールの送信の失敗…には、警報がありません」と書いており、これは合っている（誤りがあるのは `alarms.md` の側、前の Intent の `drift-report.md` 2節の判定による。`alarms.md` の本文は今回読んでいない）。

帰結（決める点）: 誤りのある文書は承認済みの記録のため、`project.md` の決まり（確定済みの成果物は書き換えず、差を明記して README などを直す）が当たる。手順書の置き場がリポジトリの中に無い（K-9、`code-quality-assessment.md`）ため、「手順書で正す」の置き場は要件で決める必要がある。
