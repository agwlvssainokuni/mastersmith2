# トレース（tracing-config）

前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/tracing-config.md`）を正とします。トレースの設定は変えていません。

## 1. 今回の確かめ（使い捨ての環境、Q5: C）

- Tempo に、今回の6本の API（`http post /api/admin/invitations`・`http get /api/admin/invitations`・`.../{invitationId}/resend`・`.../{invitationId}/cancel`・`http post /api/registration/verify`・`http post /api/registration/complete`）と、`/api/me/password`・`/api/me/preferences`・`/api/appearance` のトレースが届きました。スパンの名前は道の型（`{invitationId}`）で、ID を含みません。
- メールの送信は、要求のトレースの中のスパン `mastersmith.mail.send` として届き、属性に `mail.template`・`mail.language`・`mail.outcome`・`mail.failure.kind` が付きます。
- 要求のスパンの属性 `http.url` は道だけで、問い合わせの部分を含みません。招待の ID は `http.url`（例 `/api/admin/invitations/3/cancel`）に出ますが、利用者を表す値ではありません。
- **漏えい**: ヘルスチェック以外のトレース 50 件（約 268KB）で、メールアドレス・招待のトークン・招待の URL（`/register`）・JWT の形の値・新しいパスワードの項目は、どれも 0 件でした（`Bearer` の一致は、フィルターの名前 `BearerTokenAuthenticationFilter` だけ）。
- lgtm はトレースから時間の指標 `traces_spanmetrics_latency_bucket`（ラベル `service`・`span_name`・`span_kind`・`status_code`）を作ります。今回のダッシュボードの時間のパネルはこれを使いました（`dashboards.md` 2節）。送信のスパンは、送信が失敗しても `status_code` が `STATUS_CODE_OK` のままです。

## 2. 招待1件の追い方

1. Grafana の Explore（Loki）で `{service_name="mastersmith"} |= "招待メールを送れませんでした"` を開く。
2. 行の `trace_id` から Tempo のトレースを開き、`mastersmith.mail.send` のスパンの `mail.failure.kind` と時間を見る。
3. 行の `invitationId` で、招待の一覧の画面（管理者）の行と突き合わせ、送り直す。

## 3. 外部エクスポートの切り替え（Q4: A）

- 配備した `.env` の2行（`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318`）は、消さずに行頭の `# ` の有無で切り替えます。この段の確かめの間だけ有効にし、終わったらコメントに戻して lgtm を止め、アプリを起動し直します（README の「手元の監視（Grafana）」）。
- `.env` は開かず、項目の名前の件数だけで確かめます（有効 0 件・コメント 1 件 → 有効 1 件・コメント 0 件）。

## Sources

- `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/observability-setup-questions.md`（Q4: A・Q5: C、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/tracing-config.md`
- `README.md`（「外部エクスポートの確かめ方」「手元の監視（Grafana）」）
- `backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java`、`construction/u1-mail/nfr-design/logical-components.md`
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:3200/api/search?...`・`localhost:3200/api/traces/<id>`、`grep -c` による `.env` の項目の件数

## Assumptions & Open Questions

None.
