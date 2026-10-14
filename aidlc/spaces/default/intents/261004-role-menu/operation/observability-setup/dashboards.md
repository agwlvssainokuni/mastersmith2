# ダッシュボード（dashboards）

前の Intent の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/dashboards.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。ダッシュボードはファイル（`docker/monitoring/dashboards/mastersmith-overview.json`）でリポジトリに置き、画面からは変えません（`aidlc/spaces/default/memory/project.md` の Deployment）。決定は `observability-setup-questions.md`（Q1〜Q6 すべて A、確認済みの要約）です。

## 1. 今回の差（ファイルの変更。未コミット）

`docker/monitoring/dashboards/mastersmith-overview.json` を次のとおり変えました（版 3 → 4、パネル 34 → 38）。式は、書く前に起動して名前・ラベル・`le` を確かめ（2節）、書いた後にすべて流しました（3節）。

| # | 変更 | 中身 | 出典 |
|---|---|---|---|
| 1 | 行「ロール・グループ・メニュー（Intent 261004-role-menu）」（id 35）と3パネルを足した | 下の表 | group・role・navigation の `monitoring-design.md` 5節（group の Q1: A） |
| 2 | 「仮の目標の一覧」（id 3）に2行を足した | 「ロール・グループ・メニューの API の 95 パーセンタイル: 1 秒以内（判定は k6。下の区画は目安）」「権限の YAML の確かめ・適用: 確かめ 15 秒以内・適用 30 秒以内（k6 で判定。パネルでは見ない）」 | Q6: A（設計の「区画1つだけ」との差。5節） |
| 3 | 「稼働」（id 2）と「仮の目標の一覧」（id 3）の高さを 6 → 8 にし、その下のパネルを 2 ずつ下げた | 2行を足した一覧が切れずに見えるようにするため。式は変えていない | #2 に伴う配置だけの変更 |

区画の3パネルが対象にする `uri` の正規表現（3つとも同じ）：

```
/api/admin/(groups|roles|role-transfer)(/.*)?|/api/admin/users/[^/]+/roles|/api/me/(work-role|navigation|table-access|permissions/.*)
```

- 利用者の管理の API（`/api/admin/users`・`/api/admin/users/{userId}/…` の `/roles` 以外）と、`/api/me/preferences` などほかの `/api/me` の API は含みません（role の `monitoring-design.md` 5節）。実機の `uri` の値 30 種類（直近 30 分）に当てて、対象の 24 種類（2節）だけが当たることを確かめました。当たらなかった 6 種類は `/actuator/health`・`/api/admin/dsl/apply`・`/api/admin/dsl/preview`・`/api/admin/users`・`/api/auth/login`・`UNKNOWN` です。

| id | パネル | 式 | 目安の線 |
|---|---|---|---|
| 36 | API ごとの要求の数（1 時間） | `sum by (method, uri, status) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"<上の正規表現>"}[1h]))` | なし |
| 37 | API ごとの時間（95 パーセンタイル） | `histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri=~"<上の正規表現>"}[5m])))` | 1000 ms（黄の線） |
| 38 | API ごとの 5xx の件数（1 時間） | `sum by (method, uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"<上の正規表現>",status=~"5.."}[1h])) or on() vector(0)` | なし |

- パネル 37 の説明に、次の3点を書きました。
  - アプリの指標 `http.server.requests` のバケットで測ること。「招待と登録」の行のトレースの値 `traces_spanmetrics_latency` とは元の指標が違う。
  - 判定は k6 で行い、線は目安であること。
  - 5000 ms を超える要求（権限の YAML の確かめ・適用）は `+Inf` に入り、このパネルでは判定しないこと。
- パネル 36 の説明に、認可の入口の 403 は `uri="UNKNOWN"` に入りこの区画に出ないこと、置き場の 403 は業務の結果であることを書きました（2節の分かったこと）。
- Grafana は、ファイルの変更を起動し直さずに読み込みました。`/api/dashboards/uid/mastersmith-overview` でパネル 38 と新しい行の題を確かめています。

## 2. 確かめ方と分かったこと（Q1: A・Q2: A）

使い捨ての環境（`docker/perf/compose.yaml`）は次の形で起動しました。
- イメージ: 配備と同じ `mastersmith:local`（`sha256:3247c5bdab79…`）
- project `mastersmith-perf`・番号 18080・メモリの上限 1g
- 仮の署名鍵・仮の初期管理者・試験用の利用者 3 名（`obs-user01〜03@example.test`、管理者でない）
- 対象DB と Mailpit の profile は付けない

外部エクスポートは使い捨てのアプリにだけ、ホームの下の一時の環境ファイル（権限 600）で有効にしました。lgtm（profile `monitoring`）を使い捨ての環境の網に別名 `lgtm` でつなぎ、送らせています。**配備したアプリ・Mailpit・見本の対象DB は止めず、変えず、要求も送っていません**。眠りを防ぐため `caffeinate -dims` を張り、`pmset -g assertions` で効いていることを確かめました。

1. 用意（2026-10-15 01:29〜01:31 JST）
   - 確かめの要求（ログインの後にグループの一覧と作成 `obs-probe`、利用者の一覧）を数件送った。
   - 版 2 の DSL（スキーマ `OBS`、テーブル `T1`・`T2`、2段のメニュー）を貼り付けで投入・適用した。
   - ロールを3つ作った。`obs-role-a` は T1 READ・T2 NONE、`obs-role-b` は T1 READ、`obs-rdup` は名前の重なりの 409 用。
   - `obs-user01` に a・b を割り当てた。グループ `obs-base`（メンバー `obs-user02`）に a を割り当てた。名前の重なり用のグループ `obs-dup` を作った。
2. 権限の YAML の書き出し・確かめ・適用を各1回（01:31:13〜01:31:23）
   - 書き出した YAML をそのまま適用すると **409 `ROLE_NO_CHANGE`** になり、監査に `ROLE_TRANSFER_APPLIED` の `FAILURE`/`NO_CHANGE` が1行残った。
   - `obs-rdup` に T1 READ を足した YAML を確かめ直して適用し、200 を得た。
3. 区画のすべての道に、成功と代表の失敗を 1 回分として、13 回（01:30:51〜01:35:25、回の間は約 20 秒）送った。指標の送信の周期（60 秒）を5つまたいでいる。1回分の中身は次のとおり。
   - グループの一覧・作成・1件・名前の変更・メンバーの追加と外し・グループのロール・削除、存在しない ID（404）、名前の重なり（409）
   - ロールの一覧・作成・1件・名前の変更・権限の木（schemas・tables・columns）・保存・割り当ての一覧・利用者とグループへの割り当てと外し・利用者のロール・削除、404、409
   - `obs-user01` の作業ロールの読み取りと切り替え（a と b を交互）・自分の権限の木（3つ）・業務のメニュー・置き場（T1 は 200、T2 は 403、引数なしは 400）
   - 管理者でない `obs-user01` からのグループとロールの一覧（入口の 403）
4. 送った要求はログインを除いて 515 件で、状態コードは 200 が 215・201 が 32・204 が 163・400 が 13・403 が 39・404 が 26・409 が 27 件でした。見込みと違ったのは、2 の1件目の適用（409 `ROLE_NO_CHANGE`）だけです。

分かったこと：

- **式の名前は `_milliseconds_`** でした（`http_server_requests_milliseconds_count`・`_bucket`・`_sum`・`max_milliseconds`）。承認済みの `observability-design.md`（navigation 4節など）が書く `http_server_requests_seconds_bucket` は、手元の監視には無い名前です。基盤の設計 navigation の `monitoring-design.md` 7節の差のとおり、区画は `_milliseconds_` で書きました。
- **`le` は 100・250・500・1000・2000・5000・`+Inf`**。p95 の式を書く前に、`/api/me/navigation` の系列などで `+Inf` 以外のバケットがあることを確かめました。
- **`uri` の値は道の型**で、ID と問い合わせの部分（`schema=`・`table=`）を含みません。区画の正規表現に当たった 24 種類は次のとおりです。
  - `/api/admin/groups`・`/api/admin/groups/{groupId}`・`…/members`・`…/members/{userId}`・`…/roles`
  - `/api/admin/roles`・`/api/admin/roles/{roleId}`・`…/assignments`・`…/assignments/users/{userId}`・`…/assignments/groups/{groupId}`・`…/permissions`・`…/permissions/schemas`・`…/permissions/tables`・`…/permissions/columns`
  - `/api/admin/role-transfer/export`・`/check`・`/apply`
  - `/api/admin/users/{userId}/roles`
  - `/api/me/work-role`・`/api/me/permissions/schemas`・`/tables`・`/columns`・`/api/me/navigation`・`/api/me/table-access`

  ほかのラベルは `method`・`status`・`outcome`・`exception="none"`・`error="none"` です。
- **認可の入口で拒否した 403**（管理者でない利用者からの管理の API）は、前の Intent と同じく `uri="UNKNOWN"` に入り、区画には出ません。**置き場の 403**（`/api/me/table-access`）は業務の層で返すため、道の型の系列に入ります。
- 区画の系列は 39 組（method・uri・status）でした。権限の YAML の3本は1回ずつしか送っていないため、`increase` は 0 に見えます（新しい系列の最初の 1 件は `increase` に数えられない。`project.md` の学び）。少ない件数は監査とログで数えます（`log-queries.md`）。
- **使い捨てのアプリの最初の ERROR 1件**は、lgtm を網につなぐ前にアプリが起動し、ログの送信が `UnknownHostException: lgtm` で失敗したものでした（`io.opentelemetry.exporter.internal.http.HttpExporter`）。前の Intent と同じ手順の上の出来事で、アプリの不具合ではありません。次からは、網につないでからアプリを起動し直します（`tracing-config.md` 3節）。

## 3. すべての式の確かめ（2026-10-15 01:36:12 JST）

次の 43 件を、lgtm の Prometheus・Loki の API で1つずつ流し、**43 件すべて `success`** でした（書き方の誤り・名前の違いなし）。
- ダッシュボードの式 27 件（新しい3件を含む）
- 警報の式 16 件（`alarms.md` 3節）

ほかに、見方の問い合わせ R1〜R6（下）と `log-queries.md` の L1〜L9 も、すべて `success` でした。

| パネル | 取り出し先 | 結果 | 値 |
|---|---|---|---|
| 2 稼働（30 日） | Prometheus | success | 1 |
| 5 要求の数（1 分あたり） | Prometheus | success | 105.7 |
| 6 5xx の割合（5 分） | Prometheus | success | 0 |
| 7 ERROR のログ（5 分あたり） | Prometheus | success | 0 |
| 9 ログインの API | Prometheus | success | 487.5 |
| 10・11 トークンの更新・確認用 API | Prometheus | success | （結果なし。送っていない） |
| 13 コネクションプールの待ち | Prometheus | success | 0 |
| 14 JVM のヒープの使用率 | Prometheus | success | 0.245 |
| 15 403 の件数（1 時間） | Prometheus | success | 39.4 |
| 17〜21 監査の失敗・ロック・正規化されていないパス・送り元の不一致・監査の遅れ | Loki | success（5件とも） | （結果なし） |
| 24・25 DSL の操作の件数 | Prometheus | success | 0（投入・適用の1回ずつは新しい系列の最初の 1 件） |
| 26 DSL の操作の時間 | Prometheus | success | NaN（直近 5 分に要求なし） |
| 28・29・32・34 招待と登録・メール | Prometheus | success | （結果なし。送っていない） |
| 30・33 招待の 5xx・メールの失敗 | Prometheus | success | 0 |
| **36 区画の要求の数** | Prometheus | success | 39 系列。成功の道は 13〜14、利用者とグループへの割り当て（POST `…/assignments` 204）は 28.1、権限の YAML の3本は 0（R1） |
| **37 区画の時間（p95）** | Prometheus | success | 要求のあった道は 95 ms（すべて `le="100"` の中で、0〜100 ms の補間の値）。権限の YAML の3本は NaN（直近 5 分に要求なし） |
| **38 区画の 5xx** | Prometheus | success | 0 |

見方の問い合わせ（Explore で使う。どれも上の正規表現を `U` と書く）：

| ID | 見たいもの | PromQL | 確かめた値（直近 15 分） |
|---|---|---|---|
| R1 | 区画の系列の数 | `count(sum by (method, uri, status) (http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"U"}))` | 39 |
| R2 | 403 の内訳（uri ごと、1 時間） | `sum by (uri) (increase(http_server_requests_milliseconds_count{service_name="mastersmith",status="403"}[1h]))` | `UNKNOWN` 27.7・`/api/me/table-access` 13.8 |
| R3 | 区画の要求が全体に占める割合（`ms-5xx-ratio` の分母） | `sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"U"}[15m])) / sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith"}[15m]))` | 0.845 |
| R4 | 区画の 1000 ms 以内の割合 | `sum(increase(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri=~"U",le="1000"}[15m])) / sum(increase(http_server_requests_milliseconds_count{service_name="mastersmith",uri=~"U"}[15m]))` | 1（`le="100"` でも 1） |
| R5 | 道ごとの最大（15 分） | `max by (uri) (max_over_time(http_server_requests_max_milliseconds{service_name="mastersmith",uri=~"U"}[15m]))` | 最も長いのは権限の保存の 28.5 ms、次がグループの一覧の 19.9 ms |
| R6 | 接続プール | `max_over_time(hikaricp_connections_pending{service_name="mastersmith"}[15m])`・`sum(hikaricp_connections_timeout_total{service_name="mastersmith"})` | 待ちの最大 0・時間切れの累計 0・上限 30 |

- `increase` の値が整数でないのは、Prometheus が時間の幅の端を補って数えるためです。件数の正は監査の記録とログの件数です（`log-queries.md`）。

## 4. 監査との突き合わせ

使い捨ての環境の内部DB の監査の件数は、送った要求から見込んだ件数とすべて一致しました（`log-queries.md` 3節）。

## 5. 上流との差（承認済みの文書は書き換えない）

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| D-D1 | group・role・navigation の `monitoring-design.md` 5節 | 「足すのは区画を1つだけ」 | 区画に加えて「仮の目標の一覧」に2行を足し、パネル 2・3 の高さと以下の配置を変えた | Q6: A。警報もしきい値も変えない記録だけの差 |
| D-D2 | navigation の `observability-design.md` 4節 | 式の名前 `http_server_requests_seconds_bucket` | 実機の名前は `_milliseconds_`。区画は `_milliseconds_` で書いた | navigation の `monitoring-design.md` 7節の差を実機で確かめた |
| D-D3 | role の `monitoring-design.md` 5節の正規表現の例 | `/api/admin/(groups\|roles\|role-transfer\|users/[^/]+/roles).*\|/api/me/(work-role\|permissions/.*)` に navigation の2本を足す | 2節の正規表現（`(/.*)?` で一覧の道を含め、`/api/admin/users/[^/]+/roles` を別の枝にした） | 例の形では `/api/admin/users/{userId}/roles` の後ろに何でも当たりうるため、道を名指しする設計の意図に合わせて締めた。当たる道は設計の表と同じ |

## 6. 基準の値と片付け

基準の値は `slo-config.md` 2節に並べました。片付けは `alarms.md` 6節に書きました。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/observability-setup-questions.md`（Q1〜Q6、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/dashboards.md`
- 基盤の設計: `construction/group/infrastructure-design/monitoring-design.md`（5・7節）・`infrastructure-specification.md`、`construction/role/infrastructure-design/monitoring-design.md`（5・7節）・`infrastructure-specification.md`、`construction/navigation/infrastructure-design/monitoring-design.md`（5・7節）・`infrastructure-specification.md`、`construction/app-frame-ui/infrastructure-design/monitoring-design.md`・`construction/role-admin-ui/infrastructure-design/monitoring-design.md`（3節）
- NFR の設計: `construction/group/nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`・`observability-design.md`、`construction/role/nfr-design/` と `construction/navigation/nfr-design/` の同じ文書
- `docker/monitoring/dashboards/mastersmith-overview.json`・`docker/monitoring/provisioning/dashboards/mastersmith.yaml`、`compose.yaml`（`lgtm`）、`docker/perf/compose.yaml`、`perf/README.md`、`backend/src/main/resources/application.yaml`（`management.metrics.distribution.slo`・`management.otlp.metrics.export.step`）
- `backend/src/main/java/cherry/mastersmith/group/web/GroupAdminController.java`・`role/web/*Controller.java`・`role/web/RoleTransferPaths.java`・`navigation/web/NavigationController.java`（道の確かめ）
- 実行したコマンドの出力:
  - `docker compose --profile monitoring up -d --wait lgtm`
  - `docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait`
  - `docker network connect --alias lgtm mastersmith-perf_default mastersmith-lgtm-1`
  - `curl`（使い捨てのアプリへの要求。結果はホームの下の一時の置き場の記録で数えた）
  - `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（式を1つずつ実行）
  - `curl -s http://127.0.0.1:3000/api/dashboards/uid/mastersmith-overview`

## Assumptions & Open Questions

- 確かめた値は、負荷をかけない使い捨ての環境（上限 1g）の値で、目標の判定には使いません（判定は performance-validation の k6）。
- D-D3 の正規表現の締め方は承認の場で確かめます。
