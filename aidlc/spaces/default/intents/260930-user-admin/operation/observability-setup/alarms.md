# 警報（alarms）

前の Intent の記録（`aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/alarms.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。警報はファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、16 件）でリポジトリに置きます。通知の先（contact point）は、配備先が決まるまで置きません（手元の Grafana の警報の一覧で見るだけ。`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

- **この Intent では警報を足していません**。ファイルも変えていません（決まっていること、U3 の NFR5.10・`monitoring-design.md` 2節）。
- 409（`USER_ADMIN_BUSY`・`USER_ADMIN_LAST_ADMIN`・`USER_ADMIN_NO_CHANGE` など）は警報にしません。7つの API の p95 の警報も足しません（目標の判定は performance-validation の k6）。
- 利用者の管理の API の失敗は、既存の警報が「続けて起きる・数の多い異常」のときにだけ拾います。1件・数件の失敗は、監査の記録とアプリのログ（`log-queries.md`）で見ます。

## 2. 「拾う」と書いた警報の確かめ（Q2: A）

使い捨ての環境から送った要求で確かめました（送り方は `dashboards.md` 2節）。

| 警報（しきい値、重さ） | この段の確かめ | 結果 | 判定 |
|---|---|---|---|
| `ms-forbidden` 管理画面への拒否の増加（直近 1 時間の 403 が 20 を超える、`for: 0s`、中） | 管理者でない利用者から一覧を呼んで 403 を送った（合わせて 184 件）。業務の層の 403（`NOT_ADMIN`）と入口の 403（`uri="UNKNOWN"`）の両方が式に入ることを確かめた | **鳴った**。Grafana の警報の評価で `Alerting`、activeAt は 2026-10-03T13:23:30Z（22:23:30 JST）。22:29:38 の式の値は 249.694 で、内訳は `UNKNOWN` 約 215・grant-admin 約 35（`dashboards.md` の N5） | 確かめた |
| `ms-5xx-ratio` 5xx の割合の増加（5xx の割合が 0.01 を超える状態が 5 分、中） | 式の分母（すべての要求の毎秒の数）に7つの API の要求が入ることを、N6 で確かめた | N6 = 0.764（直近 5 分の要求の 76.4% が7つの API）。分子の 5xx は 0（U3 の経路で 5xx を起こす手が本番のコードを変えずには無い） | 数に入ることは確かめた。**鳴ることは Unverified** |
| `ms-audit-slow` 監査の書き込みの遅れ（直近 1 日に 2 件を超える、低） | 遅れのログ「監査イベントの記録に時間がかかりました」は、`AuditEventListener` が5つの操作の監査（`onUserAdminAuditEvent`）にも同じ処理で出し、キー `auditEventType` を持つ（ソースで確かめた）。式は種類で絞らないため U3 の監査も数える。U3 の種類に絞る問い合わせ L5 を流した | 警報の式・L5 とも `success`・結果なし（200 ms を超えた書き込みが無かった） | 数に入ることは確かめた。**鳴ることは Unverified** |
| `ms-pool-pending` コネクションプールの待ち（待ちの数が 0 を超える状態が 1 分、低） | この段では負荷をかけない | 式は `success`・値 0。この段の間の待ちの最大 0、借りるまでの待ちの最大 0.708 ms、時間切れの累計 0 | **持ち主を performance-validation へ移す** |
| `ms-error-logs` ERROR のログの増加（5 分に 5 件を超える、中） | 同上 | 式は `success`・値 0（この段の間の ERROR は 0 件） | **持ち主を performance-validation へ移す** |
| `ms-audit-fail` 監査の書き込みの失敗（直近 1 時間に 1 件でも、中） | 同上。U3 の種類に絞る問い合わせ L6 を流した | 式・L6 とも `success`・結果なし | **持ち主を performance-validation へ移す** |

- **持ち主を移したもの**: `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` が U3 の失敗で鳴ることは、performance-validation の上限 10 の (B) の場面（`perf/README.md` の「利用者の管理の場面」、`userAdminOps` を `VUS=10`、使い捨てのアプリの上限 10）で、手元の監視を起動したまま確かめます。この場面は2本目の接続の待ちを起こし、欠けた監査と ERROR を出す見込みの場面です。手元の監視（lgtm 1.5GB）を負荷の環境と同時に動かすため、VM のメモリ（6GiB）に注意します（README の「コンテナの資源の上限」）。
- **Unverified のもの**: `ms-5xx-ratio`・`ms-audit-slow` が U3 の失敗で鳴ること。U3 の経路で 5xx と 200 ms を超える監査の書き込みを、本番のコードを変えずに起こす手が無いためです。持ち主は feedback-optimization（配備先が決まったときに見直す）とします。
- 入口の 403 は `uri="UNKNOWN"` に入るため、`ms-forbidden` が鳴ったときにどの道への 403 かは指標では分かりません。アプリのログの WARN「権限が足りないため要求を拒否しました」と監査の `ACCESS_DENIED` の行（要求の道 `request_path` を持つ）で見ます（`log-queries.md` 1節・3節）。
- `ms-lock`（ロックの多発）は、失敗回数を戻す操作では増えません。今回はログインを1回ずつしか失敗させていないため、ロックは起きていません（Loki の「アカウントをロックしました」は結果なし）。

### 2.1 `ms-pool-pending` の式の見直し（申し送り）

`ms-pool-pending` の式（`max(hikaricp_connections_pending{service_name="mastersmith"})` が 0 を超える状態が 1 分）は、借りる待ちが 5 秒で時間切れになるため、短い枯渇では鳴りません。5つの操作は確定の後の監査で2本目の接続を借りるため（U3 の NFR6.1）、接続の枯渇の気づきとしてこの警報に頼ります。**時間切れの累計（`hikaricp_connections_timeout_total`）の増加を見る式に見直すことは、この段では行わず、配備先が決まったときの申し送りのまま**とします（`construction/code-generation/gate-decisions.md` 4節、U3 の NFR5.10）。この段で、手元の監視に `hikaricp_connections_timeout_total`（値 0）と `hikaricp_connections_acquire_max_milliseconds` が届いていることを確かめました。見直すときは、例えば `sum(increase(hikaricp_connections_timeout_total{service_name="mastersmith"}[5m])) > 0` の形が候補です（この式は警報に入れておらず、書いただけです）。

## 3. すべての警報の式の確かめ（2026-10-03 22:29:34）

警報の式 16 件を Prometheus・Loki の API で1つずつ流し、16 件すべて `success` でした。Grafana の警報の評価（`/api/prometheus/grafana/api/v1/rules`）は、16 件すべて `health: ok` で、`firing` 1 件（`ms-forbidden`）・`inactive` 15 件でした（22:30 頃）。

| 警報 | 取り出し先 | 結果 | 値 |
|---|---|---|---|
| ms-app-absent | Prometheus | success | （結果なし。アプリが動いているときは空が正しい） |
| ms-5xx-ratio | Prometheus | success | 0 |
| ms-error-logs | Prometheus | success | 0 |
| ms-audit-fail | Loki | success | （結果なし） |
| ms-login-p95 | Prometheus | success | 487.5（しきい値 1000 の内側） |
| ms-refresh-p95 | Prometheus | success | （結果なし） |
| ms-check-p95 | Prometheus | success | （結果なし） |
| ms-pool-pending | Prometheus | success | 0 |
| ms-heap | Prometheus | success | 0.159 |
| ms-forbidden | Prometheus | success | 249.694 |
| ms-lock | Loki | success | （結果なし） |
| ms-rejected | Loki | success | （結果なし） |
| ms-origin | Loki | success | （結果なし） |
| ms-audit-slow | Loki | success | （結果なし） |
| ms-cleanup-fail | Loki | success | （結果なし） |
| ms-bcrypt | Loki | success | （結果なし） |

- 前の Intent で「バケットが無いため値を出さない」と記録した `ms-login-p95` は、値を返すようになっていました（Intent 260928-quality-followup の FR4 でバケットを足した）。

## 4. 上流との差

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| O-D1 | U3 の `monitoring-design.md` 8節、NFR5.10 | 「拾う」と書いた警報が、しきい値を超えたときに鳴ることまでこの段で確かめる | 鳴ることを確かめたのは `ms-forbidden` だけ。3件は performance-validation へ持ち主を移し、2件は Unverified | Q2: A の決定。承認済みの文書は書き換えない |
| O-D2 | U3 の `monitoring-design.md` 2節・7節 | `ms-forbidden` は U3 の確かめ直しの 403 と認可の入口の 403 を含むすべての 403 を数える | そのとおり数えることを確かめた。ただし入口の 403 の `uri` は `UNKNOWN` で、道では分けられない | 設計の想定に無かった値のため、見方を `log-queries.md` に記録した |

## 5. 確かめの後の片付け（2026-10-03 22:33:47〜22:33:53）

確かめの結果（式・警報・監査の件数）を見た後に片付けました。

1. lgtm を使い捨ての環境の網から外した（`docker network disconnect mastersmith-perf_default mastersmith-lgtm-1`）。lgtm の網は元の `mastersmith_default` だけに戻った。
2. 使い捨ての環境を消した（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v`）。コンテナ 0・ボリューム 0（`mastersmith-perf_perf-data` を削除）・網 0（`mastersmith-perf_default` を削除）。
3. lgtm を止めた（`docker compose --profile monitoring stop lgtm`、`Exited (0)`）。元の状態（止まっている）に戻した。lgtm のボリューム `mastersmith_mastersmith-monitoring` は残し、この段で使い捨ての環境から送った指標・ログ・トレースが、配備したアプリと同じ `service_name="mastersmith"` で残っています（利用者の値は含まない。1節の漏えいの確かめ）。
4. ホームの下の一時の置き場（仮の署名鍵・仮の管理者と試験用の利用者のパスワード・内部DB の複写・台本・H2 の道具）を消した。
5. `docker compose -p mastersmith ps` で、配備したアプリ `mastersmith-app-1`（Up・healthy）・`mastersmith-mailpit-1`（Up・healthy）・`mastersmith-targetdb-postgres-1`（Up）が元どおり動いていることを確かめた。配備したアプリには、この段の間に何も送っておらず、止めても作り直してもいない。

- リポジトリのファイル（`docker/monitoring/` を含む）とコードは変えていません。`.env` は開いていません。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/observability-setup-questions.md`（Q2: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260925-user-management/operation/observability-setup/alarms.md`
- U3 の `construction/u3-user-admin-api/infrastructure-design/monitoring-design.md`（2・7・8節）・`infrastructure-specification.md`、`construction/u3-user-admin-api/nfr-design/reliability-design.md`・`performance-design.md`・`security-design.md`、`construction/u3-user-admin-api/nfr-requirements/observability-requirements.md`（NFR5.10）、U4 の `construction/u4-admin-forbidden-ui/infrastructure-design/monitoring-design.md`（2節）
- `construction/code-generation/gate-decisions.md`（4節、`ms-pool-pending` の申し送り）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`、`perf/README.md`（利用者の管理の場面）、`README.md`（手元の監視・コンテナの資源の上限）
- `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（遅れ・失敗のログ）
- 実行したコマンドの出力: `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`（すべての式を1つずつ実行）、`curl -s http://127.0.0.1:3000/api/prometheus/grafana/api/v1/rules`

## Assumptions & Open Questions

None.
