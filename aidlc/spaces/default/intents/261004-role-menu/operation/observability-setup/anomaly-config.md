# 異常の検出（anomaly-config）

前の Intent の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/anomaly-config.md` と、それが正とする前々回までの記録）を正とします。機械学習による異常の検出は置かず、固定の閾値の警報とダッシュボード・問い合わせで見ます。

## 1. 手元では異常の検出を行わない理由

| 理由 | 中身 |
|---|---|
| 監視を常に動かしていない | 手元の監視（lgtm）は、見たいときだけ compose の profile で起動します（`aidlc/spaces/default/memory/project.md` の Deployment）。そのため、異常の検出の元になる平常の値の連なりがたまりません |
| 利用者が1人 | 配備したアプリを使うのは依頼者だけで、要求の数が少なく、平常の幅を学べません |
| 通知の先が無い | 配備先が決まるまで警報の通知の先を置かないため、検出しても知らせる先が無い |
| 設計で決めた | 独自の指標と新しい警報は足さない（group・role・navigation の `monitoring-design.md` 2節） |

## 2. 気づきたいことと見る場所（この Intent の差）

| 気づきたいこと | 見る場所 |
|---|---|
| ロール・グループ・メニューの API が遅い、誤りが多い | 区画のパネル 36（道・状態コードごとの数）・37（道ごとの p95）・38（5xx）と、R4（1000 ms 以内の割合）（`dashboards.md`） |
| 409 の急な増え（名前の重なり・同時の操作の重なり・変える点の無い権限の YAML） | L1（code ごと）。BUSY は L5、排他の口は L3・L4（`log-queries.md` 2節） |
| 置き場の 403 の増え（権限の無いテーブルを開く、開いたままの画面で作業ロールを替える） | R2（403 の `uri` ごと）と L9。業務の結果で、障害とは限らない（`log-queries.md` 3節） |
| 管理者でない利用者による管理の API の試し | `ms-forbidden`（直近 1 時間に 403 が 20 件を超える）。入口の 403 は `uri="UNKNOWN"` で、道は監査の `ACCESS_DENIED` の `request_path` で見る |
| 監査の書き込みの失敗・遅れ（この Intent の種類） | L7・L8（`auditEventType` で絞る）。数が多いときは `ms-audit-fail`・`ms-audit-slow` |
| 接続プールが尽きかけている（書き込みの2本使い、権限の YAML の適用の2本） | `ms-pool-pending`（待ちが 1 分続いたときだけ）。時間切れの累計を見る式への見直しは申し送り（`alarms.md` 2.2） |
| ヒープが尽きて JVM が止まった（`ExitOnOutOfMemoryError`） | `ms-app-absent`（鳴るまでの時間は `alarms.md` 2.1）。止まった理由は `log-queries.md` 4節の手順で見る |
| 権限の YAML の確かめ・適用が長い | 5000 ms を超えるとバケットの外（`+Inf`）のため、p95 のパネルでは見えない。`http_server_requests_max_milliseconds` の道ごとの最大（`dashboards.md` の R5）で見る |

- 新しく現れた数の系列の最初の 1 件は `increase` に数えられないため、件数の少ない異常（権限の YAML の適用など）は、ダッシュボードと Explore で 0 や小さい値に見えることがあります。少ない件数は Loki の問い合わせか監査の記録で数えます。

## 3. 配備先が決まったときの測り方

1. 監視を常に動かし、配備したアプリの外部エクスポートを有効にする（`slo-config.md` 2節の「配備先が決まったときの測り方」）。
2. 2〜4 週間、区画の要求の数・p95・403 の内訳・ヒープの使用率の平常の幅を取り、固定の閾値を見直す。区画の p95 の警報を足すか、`ms-forbidden` から置き場の 403 を分けるかもここで決める（`alarms.md` 2.2）。
3. 配備先の監視の仕組みに異常の検出（季節性のある要求の数など）があれば、そのときに候補にする。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q2: A・Q4: A・Q5: A）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/anomaly-config.md`
- 基盤の設計: `construction/group/infrastructure-design/monitoring-design.md`（1・2節）・`infrastructure-specification.md`、`construction/role/infrastructure-design/monitoring-design.md`（1・2節）・`infrastructure-specification.md`、`construction/navigation/infrastructure-design/monitoring-design.md`（1・2節）・`infrastructure-specification.md`
- NFR の設計: `construction/group/nfr-design/reliability-design.md`・`security-design.md`・`performance-design.md`、`construction/role/nfr-design/` と `construction/navigation/nfr-design/` の同じ文書
- この段の `dashboards.md`・`alarms.md`・`log-queries.md`・`slo-config.md`

## Assumptions & Open Questions

None.
