# 監視の設計 — U4 role

この文書は、承認済みの `observability-design.md`（NFR 設計）を、手元の監視の上でどう見るかを表にしたものです。手元の監視は `grafana/otel-lgtm` で、compose の profile を見たいときだけ起動する形です。

group の Q1 A にそろえて、次のように扱います。

- 独自の指標と新しい警報は足さない。
- group が足すダッシュボードの区画（この Intent の管理の API）に、role の `uri` を足す。区画を書いて式を確かめるのは Observability Setup で、B4〜B6 は `docker/monitoring/` に触れない。
- 配備先が決まるまでは、警報の通知の先を作らない（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A・Q2: A、まとめの確認は Looks correct。監視は group の Q1 A にそろえる）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/construction/role/` の下）:
  - `nfr-design/observability-design.md`（1〜4節）
  - `nfr-design/performance-design.md`（2節・3節）
  - `nfr-design/scalability-design.md`（2節）
  - `nfr-design/reliability-design.md`（2.6）
  - `nfr-requirements/observability-requirements.md`（NFR5.1〜NFR5.6）
- group の `monitoring-design.md`（5節の区画）
- 既存のもの（正とする。読むだけ）:
  - `docker/monitoring/provisioning/alerting/mastersmith.yaml`（16 件）
  - `docker/monitoring/dashboards/mastersmith-overview.json`
  - `application.yaml`（`slo` の `http.server.requests`: 100・250・500・1000・2000・5000 ms）

## 1. Metrics & KPIs

指標の名前は、手元の監視（Prometheus）での名前です。実際の名前・ラベル・`le` のバケット・単位は、Observability Setup で起動して確かめます（`project.md` の学び）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| role の API の要求の数（`http_server_requests_milliseconds_count`、`method`・`uri`・`status` ごと） | Spring Boot の `http.server.requests`（既存）。`uri` は道の型 | なし（見るだけ） | 使われ方と、409（`ROLE_BUSY`・`GROUP_BUSY`・名前の重なりなど）・404 の傾向を見る |
| role の API の p95 | 同上（`slo` のバケット） | 目安の線 1000 ms。import の確かめ・適用は別の線（15 秒・30 秒は 5000 ms のバケットを超え `+Inf` に入るため、パネルでは目安にしない） | SLO の基準の値を見る。判定は k6（3節） |
| role の API の 5xx の数 | 同上（`status=~"5.."`） | なし（割合は既存の全体の警報が拾う） | 想定外の失敗（包み直した例外）を API ごとに見る |
| 接続プール（`hikaricp_connections_pending`・`timeout`・`acquire`） | HikariCP（既存） | 既存の `ms-pool-pending` のまま | 書き込みの2本使いと import の適用（数秒、2本）を見る |
| JVM のヒープ（`jvm_memory_used_bytes{area="heap"}`） | 既存 | 既存の `ms-heap` のまま | 確かめと適用・書き出し・DSL の投入の重なり（受け入れた制約）を見る（`security-design.md` 7節） |
| `ROLE_BUSY` の WARN の件数 | アプリのログ（Loki）。`lockKind`（`ROLE_ROW`・`ROLE_NAME_KEY`・`ROLE_ASSIGNMENT_KEY`・`WORK_ROLE_SELECTION_KEY`・`GROUP_ROW`・`ROLE_TRANSFER_SLOT`） | なし（見るだけ） | 待ちの上限切れと、確かめ・適用の重なりの多さをログの問い合わせで数える（`observability-design.md` 3節） |

## 2. Alerts

新しい警報は足しません。role の失敗を拾う既存の警報と、拾わないものは次のとおりです（式は `mastersmith.yaml` で確かめた）。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-5xx-ratio`（5xx の割合の増加） | `uri` で絞らない全体の 5xx の割合。role の API と作業ロールの切り替えを含む | 既存のとおり | 手元の Grafana の画面だけ（通知の先は作らない） |
| `ms-error-logs`（ERROR のログの増加） | 想定外の失敗の ERROR（包み直した例外のクラスの名前・SQLState・制約の名前）を数える | 既存のとおり | 同上 |
| `ms-audit-fail`・`ms-audit-slow` | role の監査の記録も既存の `AuditEventListener` を通る | 既存のとおり | 同上 |
| `ms-pool-pending`（コネクションプールの待ち） | `max(hikaricp_connections_pending)` | 既存のとおり（式の見直しは配備先が決まるまでの申し送りのまま） | 同上 |
| `ms-heap`（JVM のメモリの不足の兆し） | ヒープの使用率 | 既存のとおり | 同上 |
| `ms-forbidden`（管理画面への拒否の増加） | 全体の 403 の件数。role の管理の API の 403 を含む | 既存のとおり | 同上 |
| （拾わない）role の API の p95 | 既存の p95 の警報は `uri` を1つの道に固定しており、role の API を含まない | — | 足さない。承認済みの `observability-design.md` 4節が受け入れた制約と同じで、警報を足すかは配備先が決まったときに決める |
| （拾わない）`ROLE_BUSY`・業務の 409・404 | 警報にしない | — | 1節のログの問い合わせと 5節の区画で見る |

## 3. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| role の API の応答時間（k6 の `iteration_duration{scenario:…}`、操作が2つ以上の場面は `op` のタグごと） | p95 < 1000 ms、`checks` の率 = 1 | Performance Validation の場面ごと（10 VU・3 分）。`Unverified` から始め、Performance Validation の値で判定する |
| import の確かめ・適用（`roleTransferLarge`、上限ちょうど 10 MiB） | 確かめ 15 秒以内・適用 30 秒以内（`iteration_duration` の最大） | Performance Validation の1回。データの用意は Q2: A（`cicd-pipeline.md` 5.3） |
| `snapshotFor` | 1回 300 ms 以内 | 結合テストと Performance Validation の `myPermissionsTree` で見る |
| 接続プールの合否の回（`rolePoolLimit`、上限 11・書き込み 5 VU・2 分） | 時間切れの累計 0、500 が 0 件、`hikaricp.connections.acquire` の最大 20 ms 未満 | Performance Validation の1回。上限 10・20 VU の回は記録だけ（`scalability-design.md` 2.2） |
| 手元の監視の role の API の p95（`http_server_requests`） | 目安 1000 ms（判定に使わない） | 手元の監視を常に動かしていない間は `Unverified`。配備の直後・監視の確かめ・負荷の試験・振り返りの時点の値を基準の値として並べる（`project.md` の学び） |

`acquire` の単位（group の読み直しの R-04 の手当て）は次のとおりです。

| 読む所 | 名前 | 単位 | 合否での扱い |
|---|---|---|---|
| 使い捨てのアプリの `/actuator/metrics/hikaricp.connections.acquire`（外部エクスポートは無効） | `MAX` の統計 | 秒（Micrometer のタイマーの `baseUnit: seconds`） | 20 ms は 0.020 と比べ、記録には単位を添える |
| 手元の監視（OTLP で送った系列） | Observability Setup で起動して確かめる（`_milliseconds_` などの付き方を含む） | 確かめた単位 | 合否には使わない（見るだけ） |

SLO の値は緩めません。測れない段では `Unverified` とし、持ち主の段（performance-validation・observability-setup）を明記して引き継ぎます。

## 4. Logs & Tracing

| 項目 | 扱い |
|---|---|
| アプリのログ | 既存の構造化ログ。足すのは `ROLE_BUSY` の WARN だけ（`lockKind` と例外のクラスの名前、トレースID。名前・ID 以外の中身と例外の文は出さない）。業務のログは出さない（監査が記録する） |
| 漏えいの守り | `org.hibernate.orm.jdbc.error: OFF` を変えない。B6 で `org.springframework.jdbc.core.StatementCreatorUtils: OFF` を足す（JDBC のバッチの引数の値を TRACE に出さない）。`RoleSecretLeakIT`（TRACE を `org.springframework.jdbc` にも広げる）・`RoleBusyLogTraceIT` が `verify` で毎回守る |
| ログの収集・トレース | 変えない（手元の監視の profile を起動したときだけ OTLP で送る。外部エクスポートは既定で無効）。role の要求にも既存のトレースIDが付く |
| 監査 | 内部DB の `audit_events`（既存の仕組み）。種類 8 つと、足す失敗の理由（機能設計の BR11）。残さないもの（入力の誤り・`ROLE_BUSY`・`GROUP_BUSY`・読み取り・import の確かめ・書き出しなど）は `observability-design.md` 1節のとおり |

## 5. ダッシュボードの区画（group の Q1 A にそろえる）

group が `docker/monitoring/dashboards/mastersmith-overview.json` に足す区画「ロール・グループ・メニュー（Intent 261004-role-menu）」の3つのパネル（API ごとの要求の数・p95・5xx の件数）の `uri` の条件に、role の道を足します。持ち主は Observability Setup です。

| 足す `uri` の道の型 | 中身 |
|---|---|
| `/api/admin/roles`・`/api/admin/roles/{roleId}`・`/api/admin/roles/{roleId}/permissions…`・`/api/admin/roles/{roleId}/assignments…` | ロールの管理・権限の設定と木・割り当て |
| `/api/admin/role-transfer/export`・`/check`・`/apply` | 書き出し・確かめ・適用 |
| `/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles` | グループ・利用者のロールの読み取り |
| `/api/me/work-role`・`/api/me/permissions/schemas…` | 作業ロール・自分の権限 |

- 式の形は group の `monitoring-design.md` 5節と同じです。`uri=~"..."` の正規表現に上の道を足します（例 `/api/admin/(groups|roles|role-transfer|users/[^/]+/roles).*|/api/me/(work-role|permissions/.*)`）。`/api/admin/users/{userId}` の下の利用者の管理の API を誤って含めないよう、道の型を名指しで足します。
- p95 のパネルの目安の線は 1000 ms です。確かめと適用（15 秒・30 秒）はバケットの上限 5000 ms を超えるため、パネルの説明に「`+Inf` に入る長い要求の p95 はこのパネルで判定しない（k6 で判定）」と書きます。
- Observability Setup での確かめは group と同じです（`project.md` の学び）。
  1. `+Inf` 以外の `le` があることを確かめる。
  2. 要求を指標の送信の周期（1 分）を複数またいで送ってから、すべての式を流す。
  3. NaN を、要求が無いためと決めつけない。
  4. ロールの操作は監査に残るため、送る前に依頼者に伝える（使い捨ての環境なら配備した内部DB を汚さない）。

## 6. 運用の手順への引き継ぎ

- `ROLE_BUSY`（特に `ROLE_TRANSFER_SLOT`）が続くときの見方と、確かめ・適用の時のヒープの見方を、README の「警報と対応の手順」に足すかは incident-response の段で決めます。この段では、見るもの（1節と 5節）だけを書きます。

## 7. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `observability-design.md` 4節 | 新しい警報は足さない。p95 の警報が無いことは受け入れた制約（ダッシュボードには触れていない） | 警報は足さない（同じ）。group の区画に role の `uri` を足す | group の Q1 A にそろえた（この段のまとめの確認）。食い違いではなく、見る手段を足しただけ |
| `scalability-design.md` 2.2 | `acquire` の最大が 20 ms 未満（単位の読み方は書いていない） | `/actuator/metrics` の `MAX` を秒で読み、0.020 と比べる | group の読み直しの R-04 の手当て。合否の値は変えていない |
