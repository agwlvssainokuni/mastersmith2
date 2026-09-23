# 目標（slo-config）

Intent `260922-auth-audit-base`（auth-audit-foundation）のサービスの目標（SLO）と指標（SLI）。入力は、U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`（monitoring-design）と `infrastructure-specification.md`（infrastructure-specification）、`construction/<単位>/nfr-design/performance-design.md`（performance-design）・`security-design.md`（security-design）・`reliability-design.md`（reliability-design）である。決定は `observability-setup-questions.md`（Q1〜Q7）にある。

## 1. 位置づけ

承認済みの設計（U1〜U4 の monitoring-design 3章）は、目標の多くを「配備先が決まったときに決める（候補を示す）」としている。本段では、依頼者の決定（確認済みの要約）により、**候補を「仮の目標」として手元のダッシュボードに出す**。正式な値は配備先が決まったときに決める。

## 2. 仮の目標

| SLI | 測り方 | 仮の目標 | 期間 | 由来 | 手元での見え方 |
|---|---|---|---|---|---|
| 稼働 | 5xx でない応答の割合（`http_server_requests_milliseconds_count`） | 99.5% | 30 日 | U1 の monitoring-design 3章の候補 | ダッシュボードの「稼働（30 日）」。見ていた時間の中だけの値 |
| ログインの API の応答時間 | 95 パーセンタイル（`uri="/api/auth/login"`） | 1 秒以内（同時 10 件） | 30 日 | U2 の NFR1.1 | 同「ログインの API」 |
| トークンの更新の API の応答時間 | 95 パーセンタイル（`/api/auth/session/refresh`） | 1 秒以内（同時 10 件） | 30 日 | U2 の NFR1.2 | 同「トークンの更新の API」 |
| 確認用 API の応答時間 | 95 パーセンタイル（`/api/admin/check`） | 300 ミリ秒以内（同時 10 件、成功・403 とも） | 30 日 | U3 の NFR1.1 | 成功の分だけ（403 は `uri="UNKNOWN"` になるため。`dashboards.md` 3節） |
| 監査イベント1件の書き込みの時間 | 95 パーセンタイル | 50 ミリ秒以内（同時 10 件） | 試験の間 | U4 の NFR1.1 | 指標が無い（U4 は計器を作らない）。Performance Validation で測る |
| ヘルスチェックの UP の割合 | — | 配備先が決まったときに決める | 30 日 | U1 の monitoring-design 3章 | ヘルスの結果は指標に無いため、手元では出さない |
| 監査の書き込みの成功の割合 | 失敗の ERROR の件数 | 候補: 失敗 0 件 | 30 日 | U4 の monitoring-design 3章 | 警報 ms-audit-fail |

## 3. 誤差の予算の扱い

手元の監視は見たいときだけ動くため、30 日の予算の消費を連続して測れない。予算の消費に応じた判断（配備を止めるなど）と、消費の速さの警報は、配備先が決まって常時の収集ができてから決める。

## 4. この段が持ち主の未検証の目標の判定（Q3）

| ID | 目標 | 確かめ方 | 結果 | 判定 |
|---|---|---|---|---|
| U2-NFR10.7 | ログインの失敗の割合を、運用で見る指標の候補とし、監査ログから数える | `log-queries.md` 3節の問い合わせを、今の配備の監査ログの複写で実行した | 2026-09-23（日本時間）: 失敗 2 件 ÷ 試行 6 件 = 33.3%（配備の確認の中の入力の誤り） | **Met**（数え方を実際のデータで示せた） |
| U3-NFR10.5 | 管理画面へのアクセス拒否の件数（ACCESS_DENIED）を、運用で見る指標の候補とする | 同上 | 21 件（うち 20 件は本段で監視の確認のために送った未ログインの `/api/admin/check`） | **Met** |
| U4-NFR10.5 | 監査の書き込みの失敗の件数（アプリのログの ERROR）を、運用で見る指標の候補とする | `log-queries.md` 1節の検索と、警報 ms-audit-fail | ログの該当は 0 件。警報の決まりは評価でエラーなし | **Met** |
| U4-NFR1.4 | 監査イベントの量を無期限に保存しても、当面の配備先で扱える | 今の量: 31 件、ボリューム 69.63kB | 1年分の増え方の実測が無い | **Unverified**（`log-queries.md` 4節の月1回の確認で見る） |
| U1-NFR1.9 | 内部DBのファイルの増え方を見積もる（1年 約 180MB） | 同上 | 同上 | **Unverified**（同上。持ち主の食い違い（performance-validation か本段か）は、本段で月1回の確認に入れたことで扱いを決めた） |

目標を緩めて「満たした」ことにはしていない（project.md の Testing Posture）。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/observability-setup-questions.md`（Q1〜Q7、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `compose.yaml`、`docker/monitoring/`、`README.md` の「手元の監視（Grafana）」
- 2026-09-23 12:43〜12:57 に手元で実行した `docker compose`・Grafana の API・Prometheus と Loki の問い合わせ・H2 の Shell の出力

## Assumptions & Open Questions

- 仮の目標の正式な値は、配備先が決まったときに依頼者が決める。
