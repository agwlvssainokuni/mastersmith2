# 異常の検出（anomaly-config）

前の Intent の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/anomaly-config.md` と、それが正とする前々回までの記録）を正とします。機械学習による異常の検出は置かず、固定の閾値の警報とダッシュボード・問い合わせで見ます。

## 1. 手元では異常の検出を行わない理由（前の Intent と同じ）

| 理由 | 中身 |
|---|---|
| 監視を常に動かしていない | 手元の監視（lgtm）は、見たいときだけ compose の profile で起動します（`aidlc/spaces/default/memory/project.md` の Deployment）。そのため、異常の検出の元になる平常の値の連なりがたまりません |
| 利用者が1人 | 配備したアプリを使うのは依頼者だけで、要求の数が少なく、平常の幅を学べません |
| 通知の先が無い | 配備先が決まるまで警報の通知の先を置かないため、検出しても知らせる先が無い |
| 設計で決めた | 独自の指標は足さない。新しい警報は、実測で既存の警報が拾えなかったときに限りこの段で決める（U1・U2 の `monitoring-design.md` 2節）。今回は1つだけ足した（`alarms.md` 1節） |

## 2. 気づきたいことと見る場所（この Intent の差）

| 気づきたいこと | 見る場所 |
|---|---|
| 対象DB に届かない・答えない（業務データの 503） | 警報 `ms-biz-target-db`（5 分の窓に 503 が1件でもあれば鳴る）。区画の 503 の件数（パネル 42）とプールの時間切れ（パネル 44・45）。原因は `log-queries.md` 2節の L1・L2 の `reason` と、`docker compose ps targetdb-postgres` |
| 業務データの API が遅い | 区画の p95（パネル 41。目安の線 1000 ms）。100 ms 未満は 95 前後に見えるため、細かい変化は見えない。判定は k6 |
| 業務の 409 の急な増え（参照先が無い・一意の重なり・定義が変わった など） | 区画の 409 の件数（パネル 43）と、code ごとの件数（`log-queries.md` の L4） |
| 業務データの 403 の増え（権限の無いテーブル・カラム・操作） | 区画の要求の数（パネル 40）の `status="403"` と `ms-forbidden`。業務データの 403 は監査に残らないため、ログとトレースで追う（`alarms.md` 4節） |
| 対象DB のプールが詰まりかけている | プールのパネルの時間切れの系列。待ちの系列と `ms-pool-pending` は 60 秒ごとの点のため、短い詰まりを取りこぼす |
| 監査の書き込みの失敗・遅れ（業務データの出来事） | `ms-audit-fail`・`ms-audit-slow`（出来事の種類で絞らない。鳴ることは `Unverified`） |
| 内部DB の増え（監査の行） | 配備ごとに `mastersmith.mv.db` の大きさと `audit_events` の件数を記録する。1件 約 100 B が基準の値（`slo-config.md` 3節） |
| 手元の監視の受け手が止まった | lgtm の `OOMKilled` と、アプリの送信の失敗の ERROR（`ms-error-logs` の数に入りうる。`alarms.md` 5節） |

- 新しく現れた数の系列の最初の点は `increase` に数えられないため、件数の少ない異常（対象DB が止まった直後の 503 など）は、最初の 1〜2 分は 0 に見えることがあります。今回の確かめでは、`ms-biz-target-db` が鳴るまでに、止めてから 2分48秒かかりました（`alarms.md` 2節）。

## 3. 配備先が決まったときの測り方

1. 監視を常に動かし、配備したアプリの外部エクスポートを有効にする（`slo-config.md` 2節の「配備先が決まったときの測り方」）。受け手の大きさは、トレースの量の見込みから決める（`tracing-config.md` 3節）。
2. 2〜4 週間、区画の要求の数・p95・403・409・503 の平常の幅を取り、固定の閾値を見直す。`ms-forbidden` の題・注記・しきい値（業務データの 403 を分けるか）もここで決める（`alarms.md` 4節）。
3. 配備先の監視の仕組みに異常の検出（季節性のある要求の数など）があれば、そのときに候補にする。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/observability-setup-questions.md`（決まっていること、Q3: A・Q4: A・F1: A・F2: A）
- この段の `measurement-notes.md`・`dashboards.md`・`alarms.md`・`log-queries.md`・`slo-config.md`・`tracing-config.md`
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/anomaly-config.md`
- 基盤の設計: `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（1・2節）・`construction/u2-businessdata-write/infrastructure-design/monitoring-design.md`（1・2節）

## Assumptions & Open Questions

None.
