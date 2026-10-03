# 異常の検出（anomaly-config）

前の Intent の記録（`aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/anomaly-config.md` と、それが正とする `260922-auth-audit-base` の記録）を正とします。機械学習による異常の検出は置かず、固定の閾値の警報とダッシュボード・問い合わせで見ます。配備先が決まったときに見直します。

## 1. 今回の差

- 利用者の管理（一覧・氏名と言語の変更・5つの操作）について、異常の検出も新しい警報も置きません（決まっていること、U3 の NFR5.10）。
- 気づきたいことと見る場所は次のとおりです。

| 気づきたいこと | 見る場所 |
|---|---|
| 一覧・操作が遅い、誤りが多い | Explore の N1（道・状態コードごとの数）・N2（道ごとの p95）・N7（1000 ms 以内の割合）（`dashboards.md` 1節） |
| 409 の急な増え（同時の操作の重なり・画面の古い表示からの操作） | N3（道ごとの 409）と L1（code ごと）。BUSY は L3、排他の口は L4（`log-queries.md` 2節） |
| 最後の管理者の保護で拒否された操作、権限を失った管理者の操作 | 監査の `failure_reason` が `LAST_ACTIVE_ADMIN`・`NOT_ADMIN` の行（`log-queries.md` 3節）。L1 の `USER_ADMIN_LAST_ADMIN` と業務の層の `ACCESS_DENIED` |
| 管理者でない利用者による管理の API の試し | `ms-forbidden`（直近 1 時間に 403 が 20 件を超える）。入口の 403 は `uri="UNKNOWN"` に入るため、道は監査の `ACCESS_DENIED` の `request_path` で見る |
| 監査の書き込みの失敗・遅れ（U3 の種類だけ） | L5・L6（`auditEventType` で絞る）。数が多いときは `ms-audit-fail`・`ms-audit-slow` |
| 接続プールが尽きかけている | `ms-pool-pending`（待ちが 1 分続いたときだけ）。短い枯渇は監査の書き込みの失敗の ERROR（L6）。時間切れの累計を見る式への見直しは申し送り（`alarms.md` 2.1） |

- 警報が鳴ることの確かめは、`ms-forbidden` だけをこの段で行いました。`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` は performance-validation、`ms-5xx-ratio`・`ms-audit-slow` は Unverified です（`alarms.md` 2節）。
- 新しく現れた数の系列の最初の 1 件が `increase` に数えられないため、件数の少ない異常はダッシュボードと Explore で 0 や小さい値に見えることがあります。少ない件数は Loki の問い合わせか監査の記録で数えます。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q2: A・Q3: A）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/anomaly-config.md`
- U3 の `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（2・7節）・`infrastructure-specification.md`、`construction/u3-user-admin-api/nfr-design/reliability-design.md`・`security-design.md`・`performance-design.md`
- この段の `dashboards.md`・`alarms.md`・`log-queries.md`

## Assumptions & Open Questions

None.
