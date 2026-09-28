# 異常の検出（anomaly-config）

前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/anomaly-config.md`）を正とします。機械学習による異常の検出は置かず、固定の閾値の警報とダッシュボードで見ます。配備先が決まったときに見直します。

## 1. 今回の差

- 招待・登録・メールの送信・プリファレンス・パスワードの変更・見た目の設定について、異常の検出は置きません。
- 見るときの手がかりは次のとおりです。

| 気づきたいこと | 見る場所 |
|---|---|
| 形の正しくないトークンの総当たり（R1） | ダッシュボードの「API ごとの要求の数」の `/api/registration/verify`・`/api/registration/complete` の急な増え。監査には残らない（`log-queries.md` 3節） |
| 使い終えた・取り消した招待のトークンの総当たり（R1） | README の登録の完了の失敗の問い合わせ（疑いがあるときだけ、Q3: A） |
| 今のパスワードの総当たり（R1） | README のパスワードの誤りの問い合わせ（疑いがあるときだけ、Q3: A） |
| 受け手（SMTP）の不調 | ダッシュボードの「失敗の種類ごとの数」と「送信の時間」。招待の一覧の FAILED |
| 招待の定期の削除の失敗（N3） | Explore（Loki）の問い合わせ（`log-queries.md` 2節）。警報なし（`alarms.md` 1節） |

- 新しく現れた数の系列の最初の 1 件が `increase` に数えられないため、件数の少ない異常（1〜2 件の送信の失敗など）はダッシュボードで 0 に見えることがあります（`dashboards.md` 2節）。少ない件数を確かめたいときは、Loki の問い合わせで数えます。

## Sources

- `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/observability-setup-questions.md`（Q2: D・Q3: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/anomaly-config.md`
- `construction/u2-user-preferences/infrastructure-design/monitoring-design.md`、`construction/u3-invitation/infrastructure-design/monitoring-design.md`（R1・N3）
- `operation/observability-setup/dashboards.md`・`alarms.md`・`log-queries.md`（この段）

## Assumptions & Open Questions

None.
