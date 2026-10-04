# T1（FR4.2）接続プールの上限を下げた負荷の試験の結果

- 実施: 2026-10-03T23:33Z〜23:49Z（UTC）。k6 は 23:36:32Z〜23:42:02Z（5 分 30 秒。setup を含む）、警報の読み取りは 23:47:31Z まで
- 対象: `develop` の `a1b41e7`（WAR は `./gradlew :backend:bootWar` が UP-TO-DATE と判定したもの）。イメージ `mastersmith:user-admin-followup`（`sha256:860fe66a…`）
- 手順: `perf/README.md` の手順 0・1・1''・2'' と節「接続プールの上限を下げて流す」（a〜e）、4''・5''。台本の全体を `caffeinate -i` で包んだ
- 合否の基準: 計画（`construction/code-generation/code-generation-plan.md`）の 7.1 節（依頼者の決定 D2: A）
- 配備したアプリ（プロジェクト `mastersmith` の `app`）: オーケストレーターが 23:32:27Z に止めた状態のまま触れていない（Q3: A）。起動し直しはオーケストレーターが行う。`mailpit`・`targetdb-postgres` にも触れていない

## 1. 判定

| 基準 | 判定 | 根拠（実測） |
|---|---|---|
| (a) 時間切れの累計 1 以上、または待ちの最大 1,000 ms 以上 | **Met** | `hikaricp.connections.timeout` の累計 0 → **662**（k6 の終わり）。`hikaricp.connections.acquire` の MAX は最大 **5,049.8 ms**（`baseUnit` は `milliseconds`。外部エクスポートを有効にしたため） |
| (b) 警報3件が `Alerting` になる（決まりは変えない） | **Met** | `ms-audit-fail`・`ms-error-logs`・`ms-pool-pending` の3件とも、題の状態が `firing`（警報の実体の状態は `Alerting`）になった。時刻の並びは 3 節 |
| (c) BUSY の L3 と L4 が traceId で 1 対 1 | **Unverified** | 409 `USER_ADMIN_BUSY` は **0 件**（L3 0 件・L4 0 件）。計画 7.1 のとおり、(c) のためだけに場面を変えて流し直していない（依頼者の判断が要る） |

(b) が鳴ったため、上限 2・同時 20・8 分の流し直しは行っていない。

## 2. 負荷の形と環境

- 使い捨ての環境（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml`）。上限 CPU 4・メモリ 2g（`MASTERSMITH_CONTAINER_CPUS=4`・`MASTERSMITH_CONTAINER_MEMORY=2g`）
- `app.env` に足した行（秘密ではない値）: `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318`。起動し直した後の `hikaricp.connections.max` は 4 だった
- 手元の監視: `grafana/otel-lgtm:0.34.0` を `mastersmith-perf-lgtm`（別名 `lgtm`、`127.0.0.1:13000`、`--memory 1536m`、ボリュームなし）で起動し、`docker/monitoring/provisioning/alerting/mastersmith.yaml` を読み取りでそのまま読み込ませた。始める前に 16 件の警報の決まりが `inactive`・`ok` で読み込まれ、Prometheus で `max(hikaricp_connections_pending{service_name="mastersmith"})` が値を返すことを確かめた
- k6: `grafana/k6:2.3.0`、`SCENARIO=userAdminPoolLimit`・`VUS=12`・`DURATION=5m`・`PERF_UA_COUNT=20`（4 節の差 2）
- 試験用の利用者: 手順 2'' のとおり（`perf-ua-0001`〜`1000`、`perf-uasw-*` とトークンの行）。ただし `perf-uaop`・`perf-uat`・`perf-uapf` は 01〜20（4 節の差 2）。メールアドレスは予約のドメイン（`@example.test`）だけ。資格情報は一時ディレクトリ（権限 700）の中で乱数で作り、表示していない

実行したコマンド（要点。一時ディレクトリの場所は `$D`）:

```bash
./gradlew :backend:bootWar && docker build -t mastersmith:user-admin-followup .
export MASTERSMITH_PERF_ENV_FILE="$D/app.env" MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g MASTERSMITH_IMAGE_TAG=user-admin-followup
# 手順 2''（SYSTEM_RANGE(1, 20) の差だけ）→ a（3行）→ b（lgtm の起動と perfu up -d --wait --force-recreate app）
caffeinate -i zsh "$D/ua-limit.sh" "$D" limit 12 5m   # README の c の台本に、指標と docker stats の 30 秒ごとの記録を足したもの
```

## 3. 警報の状態の並び（30 秒ごと、UTC）

| 時刻 | ERROR のログの増加（ms-error-logs） | 監査の書き込みの失敗（ms-audit-fail） | コネクションプールの待ち（ms-pool-pending） |
|---|---|---|---|
| 23:36:33 | inactive | inactive | inactive |
| 23:37:06 | inactive | **firing** | inactive |
| 23:37:39 | inactive | firing | inactive |
| 23:38:12 | **firing** | firing | pending |
| 23:38:45 | firing | firing | pending |
| 23:39:17 | firing | firing | **firing** |
| 23:39:50〜23:42:35 | firing | firing | firing |
| 23:43:08〜23:46:25 | firing | firing | inactive |
| 23:46:58 | inactive | firing | inactive |

- 警報の実体の `activeAt`: `ms-audit-fail` は 23:36:50Z から `Alerting`（1 時間の窓のため、読み終わりの 23:47:45Z もまだ `Alerting`）。`ms-pool-pending` は 23:42:50Z に `Normal` へ、`ms-error-logs` は 23:46:50Z に `Normal` へ戻った
- 3件のほかに `監査の書き込みの遅れ` も 23:37:50Z から `Alerting` になった（今回の判定の対象外。記録だけ）
- 全 60 行は `build/perf-results/limit-alerts.txt`

## 4. README との差（どう流したか）

1. **イメージのタグ**: 手順 0 の `docker compose build app` はタグ `mastersmith:local` を上書きする。配備したアプリのイメージ `mastersmith:local`（`sha256:f386b55f…`、2026-10-03 12:54Z に作成）は `a1b41e7` より前のソースで、上書きすると後の `docker compose up` で配備したアプリが作り直されうる。配備したアプリに触れないため、別のタグ `mastersmith:user-admin-followup` で作り、`MASTERSMITH_IMAGE_TAG` で使った（`mastersmith:local` は変わっていないことを確かめた）。このタグのイメージは消さずに残している
2. **利用者の数（README のままでは流せない）**: 台本の `setupUserAdmin` は `VUS > PERF_UA_COUNT`（既定 10）で始める前に止まる。手順 2'' は `perf-uaop`・`perf-uat` を 10 名しか入れないため、節の `VUS=12` はそのままでは動かない。手順 2'' の `SYSTEM_RANGE(1, 10)`（uaop・uat・uapf の行）を `SYSTEM_RANGE(1, 20)` にし（流し直しの `VUS=20` にも足りる数）、k6 に `-e PERF_UA_COUNT=20` を渡した。README の直しが要る
3. **警報の状態の名前**: Grafana の `api/prometheus/grafana/api/v1/rules` の題の `state` は `inactive`・`pending`・`firing` で、`Alerting` は警報の実体（`alerts[].state`）の名前だった。README の `grep -c 'Alerting'` は題の状態を書いたファイルでは 0 件になる。`firing` を `Alerting` と読み、読み終わりの時点で実体の状態が `Alerting` であることも確かめた。README の直しが要る
4. **待ちの最大の読み方**: `hikaricp.connections.acquire` の MAX は直近の時間の窓の最大で、時間が経つと下がる。README の c の台本が読む「終わりの後 5 分」の値は MAX **0.22 ms** に戻っており、(a) を判定できない。30 秒ごとに `timeout`・`acquire`・`pending` を記録する行を台本に足し（`limit-metrics-timeline.txt`）、その最大と k6 の終わりの時点の値（`limit-end-*.json`）で判定した。README の直しが要る
5. 警報の記録の時刻を UTC にした（README はローカルの時刻）。k6 の出力をファイル（`limit-k6.log`）に残し、`docker stats` を 30 秒ごとに記録した
6. 手順 d の数え方は L3 の traceId が L4 に含まれるかだけを見るため、L4 の件数が traceId ごとにちょうど1件かも数える形に広げた（今回は BUSY が 0 件で、どちらも 0）
7. 片付けの順: 判定に使う値（指標・警報・ログの件数・監査の件数）はすべて `build/perf-results/` に残すか読み取った後に環境を消したが、この文書は片付けの後に書いた

## 5. 実測の値

### k6（`build/perf-results/limit.json`・`limit-k6.log`）

| 項目 | 値 |
|---|---|
| 操作の応答 204 / 409 / 500 / その他 | **124 / 128 / 422 / 0** |
| 要求の数（setup と操作する管理者のログインを含む） | 734 |
| 回の数（4つの操作の組） | 200 |
| 応答時間（全体） | 平均 5,095 ms・中央 5,037 ms・p95 5,745 ms・最大 14,865 ms |
| 操作する管理者のログインし直しの 500（その回は打ち切り） | 34 件 |

- 409 の内訳（アプリのログの L3 の `code`）: `USER_ADMIN_NO_CHANGE` 70・`USER_ADMIN_TARGET_SUSPENDED` 58・`USER_ADMIN_BUSY` **0**。組の途中の操作が 500 で失敗し、対象の状態が戻らないまま次の操作に進んだための 409 と読める
- 閾値は置かない場面のため、p95 は判定に使わない

### 接続プール（`/actuator/metrics`、`baseUnit` は `milliseconds`）

| 時点 | timeout の累計 | acquire の COUNT | acquire の MAX |
|---|---|---|---|
| k6 の前（23:36:32Z） | 0 | 8 | 0.196 ms |
| 23:37:06Z | 51 | 206 | 5,041.3 ms |
| k6 の終わり（23:42:02Z） | **662** | 1,529 | **5,049.8 ms** |
| 終わりの後 5 分（23:47:31Z） | 662 | 1,562 | 0.225 ms |

- `hikaricp.connections.pending` の最大は 13（30 秒ごとの値）

### アプリのログ（JSON、値は出さず件数だけ）

- 水準: INFO 38・WARN 188・ERROR 901。JSON でない行 0
- ERROR の内訳: `想定外のエラーが起きました` 456（k6 の 500 の 422 とログインの 500 の 34 の和と一致）・Tomcat の `dispatcherServlet` のロガーの `Servlet.service() … threw exception` 239・`監査イベントの記録に失敗しました` 206（例外の種類は `CannotCreateTransactionException`）
- WARN の内訳: `要求をエラー応答に変換しました` 128（409 の数と一致）・`監査イベントの記録に時間がかかりました` 42・`内部DBの確認が制限時間内に終わりませんでした` 16 ほか起動の2件
- Tomcat のロガーの ERROR 239 件は、例外のログを境界で1回だけ出す決まり（team.md の Code Style）と重なる出力の可能性がある。今回は原因を確かめていない（オーケストレーターに確認を委ねる）

### 監査の件数（手順 5''、アプリを止めて H2 の道具で読み取りだけ）

| 種類 | SUCCESS | FAILURE（理由ごと） |
|---|---|---|
| USER_ADMIN_GRANTED | 7 | TARGET_SUSPENDED 3 |
| USER_ADMIN_REVOKED | 5 | NO_CHANGE 6・TARGET_SUSPENDED 7 |
| USER_SUSPENDED | 13 | NO_CHANGE 5 |
| USER_RESUMED | 7 | NO_CHANGE 9 |
| LOGIN_SUCCEEDED | 9 | — |

- 4つの操作の SUCCESS の合計 32 と k6 の 204 の 124 の差 **92** は、監査の記録の失敗の ERROR のうち `failureReason` の無いもの（成功の監査）92 件と一致する。FAILURE の合計 30 と 409 の 128 の差 98 は、`failureReason` のある失敗の ERROR 98 件と一致する。ログインの成功の監査 9 件と、`LOGIN_SUCCEEDED` の失敗の ERROR 16 件の和 25 は、操作する管理者のログインの成功の数に当たる
- 監査の欠けは、確定の後に2本目の接続を借りられずに時間切れになったためで、この場面が起こそうとした状態（計画 7.1 の「鳴る筋道」）のとおり。欠けた分はすべて ERROR のログに出ている

### 秘密の漏えいの件数（手順 4''）

| 確かめ | 件数 |
|---|---|
| アプリのログの `perf-ua` | **16** |
| アプリのログの `MVStoreException` | 0 |
| アプリのログの `password`・`Bearer `・`eyJ` | 0 |
| k6 の出力（`limit-k6.log`）の `@example` | 0 |

- `perf-ua` の 16 件は、すべて `監査イベントの記録に失敗しました`（`auditEventType=LOGIN_SUCCEEDED`）の ERROR のキー `enteredEmail` だった（操作する管理者のログインの成功の監査が時間切れで書けなかった分）。project.md の Corrections（2026-09-29）で「手で記録を補うための既知の例外としてメールアドレスそのものを載せる」と決めたキーで、新しい漏えいの経路ではない。ただし README の手順 4'' は「どれも 0」としており、監査の失敗が起きる場面では 0 にならない。外部エクスポートを有効にしたため、同じ行は使い捨ての Loki にも送られた（環境ごと消した）。README の書き方を直すかは依頼者の判断が要る
- k6 の結果の JSON（`limit.json`）の `setup_data.userAdminIds` には、試験用の利用者 12 名のメールアドレス（`@example.test`）が利用者 ID の鍵として入っている。予約のドメインの仮の値で、`build/` は git の対象外。トークン・パスワードは含まれない

## 6. メモリの余裕

- colima の VM: CPU 4・メモリ 5.77GiB（`docker info` で 6,197,366,784 バイト）
- 30 秒ごとの `docker stats` の最大: 使い捨てのアプリ **571.1MiB / 2GiB**・手元の監視 657.3MiB / 1.5GiB・k6 16.8MiB・`mastersmith` の mailpit 約 15MiB・targetdb-postgres 約 26MiB。合計でも約 1.3GiB で、VM に十分な余裕があった
- 使い捨てのアプリは止まらなかった（`OOMKilled=false`・起動し直し 0 回、5'' で止めるまで `running`）
- 配備したアプリ（上限 2g）は止めたままだったため、並べて動かした場合の値は測っていない

## 7. 片付け

- `docker rm -f mastersmith-perf-lgtm`、`docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v`（ボリューム `mastersmith-perf_perf-data` とネットワーク `mastersmith-perf_default` を消した）、一時ディレクトリを削除
- README の最後の `docker compose up -d --wait` は行っていない（配備したアプリの起動し直しはオーケストレーターが行う）。片付けの後の時点（23:48:56Z）で `mastersmith-app-1` は `Exited (143)` のまま
- 残したもの: `build/perf-results/limit*`（k6 の結果・k6 の出力・警報の並び・指標の並び・メモリの並び・前後の指標の JSON。git の対象外）と、イメージ `mastersmith:user-admin-followup`

## 8. 依頼者に確かめたいこと

1. (c) は BUSY が 0 件で `Unverified`。このまま持ち越すか（計画 7.1 のとおり、場面を変える流し直しは依頼者の判断）
2. README の直し（4 節の 2・3・4。利用者の数と `PERF_UA_COUNT`、`firing` と `Alerting` の名前、待ちの最大を試験の間に読むこと）をどの段で行うか
3. 手順 4'' の `perf-ua` 16 件（既知の例外 `enteredEmail`）を README でどう扱うか
4. Tomcat のロガーの ERROR 239 件（`Servlet.service() … threw exception`）の原因を確かめるか

## 9. 依頼者の決定と README の直し（2026-10-04）

依頼者の決定:

- (c) は `Unverified` のまま次の Intent へ持ち越す（記録だけ）
- Tomcat のロガーの ERROR 239 件（`Servlet.service() … threw exception`）の原因の確かめは、次の Intent へ持ち越す（記録だけ）
- `perf/README.md` はこの Intent で直す（文書だけ。k6 の台本とコードは変えない）

`perf/README.md` に行った直し（4 節の差 1〜6 と 5 節の手順 4'' の件数を反映。台本 `perf/k6/scenarios.js` は変えていない）:

1. 共通の手順 0・1（8〜23 行付近）: イメージを `docker compose build app` ではなく Intent ごとのタグ（`docker build -t mastersmith:<Intent の名前> .`）で作り、手順 1 の `export` に `MASTERSMITH_IMAGE_TAG` を足した。配備したアプリのタグ `local` を上書きしないため
2. 手順 2''（274〜290 行付近）: `perf-uaop`・`perf-uat`・`perf-uapf` を 01〜20 にし（`SYSTEM_RANGE(1, 20)`）、`VUS=12`・流し直しの `VUS=20` では k6 に `-e PERF_UA_COUNT=20` を渡すことを書いた
3. 手順 4''（347〜369 行付近）: `perf-ua` の件数は、`監査イベントの記録に失敗しました` の ERROR のキー `enteredEmail`（既知の例外）の行の数と一致し、それ以外の行では 0 であることを見る、とし、両方を数えるコマンドを足した
4. 節「接続プールの上限を下げて流す」（384〜495 行付近）:
   - 題の `state` は `inactive`・`pending`・`firing` で、`firing` を `Alerting` と読むこと。台本は実体の `state`・`activeAt` も記録し、`grep -c 'Alerting'` を題ごとの `firing` の回数の数え方に替えた
   - `acquire` の `MAX` が時間とともに下がるため、30 秒ごとの記録と k6 の終わりの直後の値で (a) を判定する書き方と、その計算のコマンドを足した
   - 利用者の数と `PERF_UA_COUNT=20`、鳴らなかったときの扱い（流し直しは台本の引数で上限 2・`VUS=20`・`8m`）を書いた
   - 台本 `ua-limit.sh` を、この試験で実際に使った形（引数・時刻は UTC・k6 の出力をファイルへ・メモリの記録）にそろえた
   - 手順 d の L3・L4 を「L4 をちょうど1件持つ L3」の件数で数える形に広げた

## 10. Build and Test の承認の場の決定（2026-10-04）

依頼者が Approve を選んだ。次の扱いを受け入れた。

- FR4.2-c は Unverified のまま、次の Intent へ持ち越す。
- FR4.2 は、確定済みの `construction/code-generation/traceability.json` を直さず、この段で確かめたことを `cross-unit-traceability.md` に記録する。
- `README.md` の「取得と準備」の節の古い固定先（`077f5b4`。今は `e82b651`）は、Deployment Pipeline の段で直す。
