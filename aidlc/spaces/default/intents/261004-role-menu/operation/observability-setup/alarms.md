# 警報（alarms）

前の Intent の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/alarms.md` と、それが正とする前々回までの記録）を正とし、今回の差だけを書きます。警報はファイル（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、16 件）でリポジトリに置きます。通知の先（contact point）は、配備先が決まるまで置きません。手元の Grafana の警報の一覧で見るだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

- **この Intent では警報を足しても変えてもいません**。ファイルも変えていません。根拠は次の3つです。
  - 決まっていること
  - group・role・navigation の `monitoring-design.md` 2節
  - Q4: A（`ms-forbidden` の式を変えない）
- 新しい API の p95 の警報は足していません。判定は performance-validation の k6 で、ダッシュボードの区画の p95 のパネルで目安を見ます（`dashboards.md`）。
- `ROLE_BUSY`・`GROUP_BUSY`・業務の 409・404 は警報にしません。見方は `log-queries.md` 2節です。

## 2. 「拾う」と書いた警報の確かめ（Q3: A・Q5: A）

使い捨ての環境から送った要求で確かめました（送り方は `dashboards.md` 2節）。

| 警報（しきい値、重さ） | この段の確かめ | 結果 | 判定 |
|---|---|---|---|
| `ms-forbidden` 管理画面への拒否の増加（直近 1 時間の 403 が 20 件を超える、`for: 0s`、中） | 入口の 403（管理者でない利用者からのグループ・ロールの一覧）26 件と、置き場の 403 13 件を送った | **鳴った**。Grafana の評価で `Alerting`、activeAt は 2026-10-15 01:33:30 JST。01:36 の式の値は 39.5。内訳は `UNKNOWN` 27.7・`/api/me/table-access` 13.8（`dashboards.md` の R2）。置き場の 403（業務の結果）も数に入ることを確かめた（2.2） | 確かめた |
| `ms-app-absent` アプリの停止（指標が 5 分届かない、`for: 5m`、高） | 確かめの最後に使い捨てのアプリを止め（`docker compose … stop app`、01:37:10）、警報の状態を 15 秒ごとに記録した（Q5: A） | **鳴った**。`Pending` が 01:42:30、`Alerting` が 01:47:30。指標の最後の点は 01:37:10（止めるときの最後の送信）で、**止めてから鳴るまで 10 分 20 秒**（2.1） | 確かめた |
| `ms-5xx-ratio` 5xx の割合の増加（0.01 を超える状態が 5 分、中） | 式の分母に区画の API の要求が入ることを R3 で確かめた | R3 = 0.845（直近 15 分の要求の 84.5% が区画の API）。分子の 5xx は 0（区画の経路で 5xx を起こす手が、本番のコードを変えずには無い） | 数に入ることは確かめた。**鳴ることは Unverified**（持ち主 feedback-optimization） |
| `ms-pool-pending` コネクションプールの待ち（0 を超える状態が 1 分、低） | この段では負荷をかけない。式はプール全体の値で `uri` を持たないため、group・role の書き込みの接続も含む | 式は `success`・値 0。待ちの最大 0・時間切れの累計 0・上限 30（R6） | **持ち主を performance-validation へ移す**（`groupPoolLimit`・`rolePoolLimit` の間に lgtm を動かす） |
| `ms-error-logs` ERROR のログの増加（5 分に 5 件を超える、中） | 同上 | 式は `success`・値 0。この段の ERROR は、確かめの手順の上の送信の失敗の1件だけ（`log-queries.md` 1節） | **持ち主を performance-validation へ移す** |
| `ms-audit-fail`・`ms-audit-slow` 監査の書き込みの失敗・遅れ | `AuditEventListener` はこの Intent の種類にも同じ処理でログを出す。式は種類で絞らない。この Intent の種類に絞る L7・L8 を流した | 警報の式・L7・L8 とも `success`・結果なし | `ms-audit-fail` は**持ち主を performance-validation へ移す**。`ms-audit-slow` は**鳴ることは Unverified** |
| `ms-heap` JVM のメモリの不足の兆し（0.85 を超える状態が 5 分、低） | この段では負荷をかけない | 式は `success`・最大 0.280（上限 1g、直近 15 分） | 急に尽きる場合は鳴らない見込み（2.1）。尽きる確かめは performance-validation の `roleTransferLarge` |

- 前の Intent では、`ms-app-absent` は「アプリが動いているときに結果が空」しか確かめていませんでした。**鳴ることを確かめたのはこの段が初めて**です。
- 警報の評価は、16 件すべて `health: ok` でした。片付けの前（01:47:51）の状態は `firing` 2 件（`ms-forbidden`・`ms-app-absent`）・`inactive` 14 件です。

### 2.1 ヒープが尽きて JVM が止まったとき（Q5: A）

- `-XX:+ExitOnOutOfMemoryError` で JVM が止まると、`restart: "no"` のため起き直しません。止まった後、指標が届かなくなってから `ms-app-absent` が鳴るまでの流れは次のとおりです。
  1. Prometheus の見に行く幅（5 分）の間は、最後の点が見え続ける。
  2. その後 `absent(...)` が 1 を返し、`Pending` になる（今回は止めてから 5 分 20 秒）。
  3. `for: 5m` の後に `Alerting` になる（今回は 10 分 20 秒）。
- 今回は `docker compose stop`（SIGTERM、終了コード 143）で止めたため、止める直前に最後の指標が送られました。JVM が自分で止まるときは最後の送信が無く、最後の点は直前の周期（最大 60 秒前）になりえます。その分、鳴るまでは 9〜10 分ほどになる見込みです（`Unverified`）。
- `ms-heap` は 5 分続いたときだけ鳴るため、急に尽きる場合は鳴りません。止まった理由の見方は `log-queries.md` 4節です。
- **配備したアプリの外部エクスポートは有効にしていない**ため、今の配備したアプリが止まっても、手元の監視の警報は鳴りません。止まったことに気づくのは画面の表示です。配備先が決まったときに、外部エクスポートと通知の先とあわせて見直します。

### 2.2 申し送り（配備先が決まったとき）

- **`ms-forbidden` と置き場の 403**（Q4: A）: 式は変えていません。置き場の 403 は「見られないテーブル」という業務の結果で、監査に残りません。それでも `ms-forbidden` の数に入り、今回の確かめでは置き場の 403 だけで 13 件でした（しきい値 20 の 65%）。
  - 依頼者1人の今の使い方では、20 件に届くことはまれな見込みです。
  - 警報の注記（「監査ログで利用者と接続元IPを確かめる」）は置き場の 403 に当たらないため、見分け方を `log-queries.md` 3節に書きました。
  - 式から `uri="/api/me/table-access"` を外すか分けるかは、配備先が決まり利用者が増えたときに決めます。
- **`ms-pool-pending` の式の見直し**: 前の Intent からの申し送りのままです（時間切れの累計 `hikaricp_connections_timeout_total` の増加を見る式。候補は `sum(increase(hikaricp_connections_timeout_total{service_name="mastersmith"}[5m])) > 0`。警報には入れていない）。この Intent の group・role の書き込みの2本使いと、権限の YAML の適用の2本も、この警報に頼ります。
- **区画の p95 の警報**: 足していません。既存の p95 の警報3件は `uri` を1つの道に固定しているため、新しい API を拾いません（基盤の設計の差、Q1: A）。配備先が決まったときに、区画の API の p95 の警報を足すかを決めます。

## 3. すべての警報の式の確かめ（2026-10-15 01:36:12 JST）

警報の式 16 件を Prometheus・Loki の API で1つずつ流し、16 件すべて `success` でした。

| 警報 | 取り出し先 | 結果 | 値 |
|---|---|---|---|
| ms-app-absent | Prometheus | success | （結果なし。アプリが動いているときは空が正しい。止めた後に鳴ることは 2節） |
| ms-5xx-ratio | Prometheus | success | 0 |
| ms-error-logs | Prometheus | success | 0 |
| ms-audit-fail | Loki | success | （結果なし） |
| ms-login-p95 | Prometheus | success | 487.5（しきい値 1000 の内側） |
| ms-refresh-p95 | Prometheus | success | （結果なし。送っていない） |
| ms-check-p95 | Prometheus | success | （結果なし。送っていない） |
| ms-pool-pending | Prometheus | success | 0 |
| ms-heap | Prometheus | success | 0.245 |
| ms-forbidden | Prometheus | success | 39.5 |
| ms-lock | Loki | success | （結果なし） |
| ms-rejected | Loki | success | （結果なし） |
| ms-origin | Loki | success | （結果なし） |
| ms-audit-slow | Loki | success | （結果なし） |
| ms-cleanup-fail | Loki | success | （結果なし） |
| ms-bcrypt | Loki | success | （結果なし） |

## 4. Build and Test から持ち越した Unverified

U3-NFR5.4 は **Met（差つき）**、U4-NFR5.4 は **Met** と判定しました。理由は `slo-config.md` 3節です。警報が鳴ることのうち、残るものは2節の表のとおりです。
- performance-validation へ移すもの: `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`
- Unverified のもの: `ms-5xx-ratio`・`ms-audit-slow`

## 5. 上流との差

| ID | 上流 | 上流の記載 | この段の記録 | 理由と扱い |
|---|---|---|---|---|
| O-D1 | group の `observability-design.md` 4節、U3-NFR5.4 | 既存の決まり（5xx の率・p95）が `uri` でグループの API にも効く | 5xx の率は効く。p95 は効かず、区画のパネルで見る | 基盤の設計 group の `monitoring-design.md` 7節の差（Q1: A）を実機で確かめた。承認済みの文書は書き換えない |
| O-D2 | navigation の `monitoring-design.md` 2節 | `ms-forbidden` は置き場の 403 を含む。「多くても障害とは限らない」 | そのとおり数えることを確かめ、確かめの中で鳴った。式は変えない | Q4: A。見分け方を `log-queries.md` 3節に書いた |

## 6. 確かめの後の片付け（2026-10-15 01:47:51〜01:47:56 JST）

確かめの結果（式・警報・監査の件数・`ms-app-absent` の時間）を見て記録した後に片付けました。

1. lgtm を使い捨ての環境の網から外した（`docker network disconnect mastersmith-perf_default mastersmith-lgtm-1`）。lgtm の網は元の `mastersmith_default` だけに戻った。
2. 使い捨ての環境を消した（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v`）。コンテナ・ボリューム `mastersmith-perf_perf-data`・網 `mastersmith-perf_default` は、どれも残っていない。
3. lgtm を止めた（`docker compose --profile monitoring stop lgtm`、`Exited (0)`）。元の状態（止まっている）に戻した。
   - lgtm のボリューム `mastersmith_mastersmith-monitoring` は、前の Intent からのものを消さずに残した。
   - この段で使い捨ての環境から送った指標・ログ・トレースが、配備したアプリと同じ `service_name="mastersmith"` で残っています（利用者の値は含まない。`log-queries.md` 1節・`tracing-config.md` 1節の漏えいの確かめ）。
   - 次に lgtm を起動したとき、この段の 403 で `ms-forbidden` が、止めたアプリで `ms-app-absent` が、少しの間 `Alerting` で見えることがあります。
4. ホームの下の一時の置き場は、中身ごと消しました。置いていたのは、仮の署名鍵・仮の管理者と試験用の利用者のパスワード・内部DB の複写・送った要求の記録・台本・H2 の道具・DSL と権限の YAML の見本です。リポジトリの `build/` に一時のファイルは残っていません。
5. `docker ps -a` で、配備した環境が元どおり動いていることを確かめた。
   - `mastersmith-app-1`（Up・healthy）・`mastersmith-mailpit-1`（Up・healthy）・`mastersmith-targetdb-postgres-1`（Up）
   - 配備したアプリには、この段の間に何も送っていない。止めても作り直してもいない。
6. 眠りを防ぐ `caffeinate -dims` を止めた。`pmset -g log` で、確かめの間（01:26〜01:48）に Sleep・DarkWake が無かったことを確かめた。

- この段の間のメモリ: 01:38 の時点で、lgtm 1.23GiB／1.5GiB・配備したアプリ 469MiB／2GiB・Mailpit 41MiB・見本の対象DB 56MiB。VM の空き（available）は 4.0GB でした。lgtm は上限の 82% に達しており、ダッシュボードを長く開くとさらに増えうります（前の Intent で 900m の上限で止められた記録あり）。
- リポジトリのファイルで変えたのは `docker/monitoring/dashboards/mastersmith-overview.json` だけ（未コミット、`dashboards.md` 1節）です。`.env` は開いていません。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/observability-setup-questions.md`（Q3: A・Q4: A・Q5: A、確認済みの要約）
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/observability-setup/alarms.md`
- 基盤の設計: `construction/group/infrastructure-design/monitoring-design.md`（2・7節）・`infrastructure-specification.md`、`construction/role/infrastructure-design/monitoring-design.md`（2・7節）・`infrastructure-specification.md`、`construction/navigation/infrastructure-design/monitoring-design.md`（2・7節）・`infrastructure-specification.md`
- NFR の設計: `construction/group/nfr-design/observability-design.md`・`reliability-design.md`・`performance-design.md`・`security-design.md`、`construction/role/nfr-design/` と `construction/navigation/nfr-design/` の同じ文書
- `construction/build-and-test/build-and-test-summary.md`（U3-NFR5.4・U4-NFR5.4）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`、`compose.yaml`（`lgtm`・`restart: "no"`）、`docker/perf/compose.yaml`、`Dockerfile`（`ExitOnOutOfMemoryError`）、`README.md`
- 実行したコマンドの出力:
  - `docker exec mastersmith-lgtm-1 curl -s localhost:9090/api/v1/query?...`・`localhost:3100/loki/api/v1/query?...`
  - `curl -s http://127.0.0.1:3000/api/prometheus/grafana/api/v1/rules`（15 秒ごとの記録）
  - `docker compose -p mastersmith-perf -f docker/perf/compose.yaml stop app`・`down -v`、`docker network disconnect …`、`docker compose --profile monitoring stop lgtm`
  - `docker ps -a`・`docker stats --no-stream`、`pmset -g assertions`・`pmset -g log`

## Assumptions & Open Questions

- JVM が自分で止まったときに `ms-app-absent` が鳴るまでの時間（9〜10 分の見込み）は、`docker compose stop` で止めた測りからの推定です（`Unverified`）。
