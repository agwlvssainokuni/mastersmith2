# 異常の検知（anomaly-config）

Intent `260922-auth-audit-base`（auth-audit-foundation）の異常の検知の設定。入力は、U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`（monitoring-design）と `infrastructure-specification.md`（infrastructure-specification）、`construction/<単位>/nfr-design/performance-design.md`（performance-design）・`security-design.md`（security-design）・`reliability-design.md`（reliability-design）である。決定は `observability-setup-questions.md`（Q1〜Q7）にある。

## 1. 決定

**自動の異常の検知（過去の値から基準線を学び、外れを知らせる仕組み）は使わない。** 決まった値のしきい値（`alarms.md`）だけで見る（確認済みの要約）。

| 案 | 採否 | 理由 |
|---|---|---|
| 決まった値のしきい値 | 採る | 承認済みの設計（U1〜U4 の monitoring-design 2章）が値の候補を持つ。量が少なくても働く |
| 基準線による異常の検知 | 採らない | 手元の監視は見たいときだけ動き、量も開発者の操作だけのため、基準線を作れるだけの連続したデータが無い |

## 2. 代わりに見るもの

- 急な失敗: 5xx の割合、ERROR の件数、監査の書き込みの失敗（`alarms.md`）。
- 攻撃の兆し: ロックの多発、403 の多発、正規化されていないパスの拒否、送り元の不一致（`alarms.md`）。
- ゆっくりした変化: 記録の量（月1回、`log-queries.md` 4節）、ログインの失敗の割合（月1回、`log-queries.md` 3節）。

## 3. 配備先が決まったときに見直すもの

常時の収集ができ、利用者の要求が流れるようになったら、応答時間と失敗の割合について、基準線による検知を足すかを決める。そのときは、しきい値（急な失敗）と基準線（ゆっくりした外れ）を組み合わせる。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/observability-setup-questions.md`（Q1〜Q7、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `compose.yaml`、`docker/monitoring/`、`README.md` の「手元の監視（Grafana）」
- 2026-09-23 12:43〜12:57 に手元で実行した `docker compose`・Grafana の API・Prometheus と Loki の問い合わせ・H2 の Shell の出力

## Assumptions & Open Questions

None.
