# 負荷の試験の結果（k6）— Build and Test（Intent 261102-timezone-ui-polish）

担当: 品質。測った日: 2026-11-04（UTC）。PostgreSQL の回は日本時間 11-04 23:28〜11-05 00:51（1〜4 節）、MySQL・MariaDB の回は依頼者の決定で追加し、日本時間 11-05 03:43〜05:35 に流した（5 節）。手順は `../performance-test-instructions.md`。生の summary json は同じ `evidence/` に置いた（伏せ字の道具を通したもの。下の 7 節）。

## 1. 条件

| 項目 | 値 |
|---|---|
| コミット | `2a6e05f75b0a5f30ef91097b0da3b56a1a95909a`（作業ブランチ `fix/261102-timezone-ui-polish-bt1` の HEAD） |
| WAR | `backend/build/libs/mastersmith.war`。SHA-256 `e2563954b6db12021e471f8fdfbecf1a388ea58e6767bfdec09856fbb645f42b`。測る前に `./gradlew :backend:bootWar` を流し、`bootWar` を含むすべてのタスクが今の入力に対して UP-TO-DATE であることと、WAR の中の画面の資産の名前が今の `frontend/dist`（22:17 のビルド）と同じであることを確かめた（WAR のファイルの日時は 09:29 のまま。中身は HEAD のもの） |
| 試験のイメージ | `mastersmith:perf-tz-bt`（ID `sha256:3a3fd38094b8…`）。**イメージの中の WAR の SHA-256 は手元の WAR と同じ** `e2563954…` |
| 配備したアプリ | `mastersmith-app-1`（`mastersmith:local`、ID `sha256:ffd2b8380460…`）。測る間だけ止めた（2026-11-04T14:28:05Z〜15:51:53Z、約 83 分 48 秒）。起動し直した後に healthy で、イメージ ID は止める前と同じ |
| VM / 上限 | colima は CPU 4・メモリ 6GiB（docker から見える量 6,198,571,008 B）。使い捨てのアプリは CPU 4・メモリ 2g（配備と同じ）。k6 は同じ VM（上限なし） |
| k6 | `grafana/k6:2.3.0`（`perf/versions.env` の固定の値）。どの場面も 10 VU。設定の場面は 1 分（`DURATION` の既定）、ほかは 3 分（`BIZ_DURATION`・`ROLE_DURATION` の既定） |
| 対象DB | 使い捨ての PostgreSQL 18.6（`shared_buffers` 256MB、メモリの上限 805,306,368 B = 768m。README の業務データの試験の値） |
| 種 | `make-biz-seed.sh postgres` の既定。`biz_item` 100,000 行・目印の語の当たり 909・最長 2,000,000 字（アプリの起動の前に確かめた） |
| 表の大きさ / 共有バッファー | 合計 212,762,624 B（`biz_item` 205,578,240 + 索引 2,260,992 ほか）/ 268,435,456 B = **79.3%**（80% の内。前の Intent と同じ値） |
| 暖め | 集計の形（`SELECT COUNT(*), SUM(LENGTH(t::text)) FROM bizperf.biz_item t`）を1回（前の Intent の教訓。`SELECT *` は psql が OOM になる） |
| 読み取りのプール | 業務データの読み取りの場面は既定の上限 10。参照の場面の前に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE=5` を足してアプリを作り直し、暖めの後に `hikaricp.connections.max` が **5.0** であることを確かめてから流した |
| 眠り | 駆動の全体を1つの `caffeinate -dims` で包んだ。流す前の `pmset -g assertions` で `PreventSystemSleep` 1・`PreventUserIdleSystemSleep` 1（caffeinate の主張あり）。流した後の `pmset -g log` で、窓（日本時間 23:28〜00:51）に `Sleep`・`DarkWake`・`Wake` は無い（最後の記録は 22:05 の DarkWake → FullWake で、窓の前） |
| 回数 | 場面ごとに 2 回（r1・r2）を続けて流し、悪い側の値で判定した |

## 2. 場面 × 回の値（PostgreSQL。ミリ秒）

判定の値は `iteration_duration{scenario:<場面>}` の p95（1回の繰り返しに要求1つ）。`http_req_duration` は並べて記録するだけ（設定の3場面だけは、台本の判定の閾値が `http_req_duration{name:<場面>}` のため、両方を並べる。下の注）。`checks` は合格 / 失敗の件数。k6 の終了コードはすべて 0（閾値を通過）。

| 場面 | 出どころ | 線 | r1 p95 | r2 p95 | 悪い側 | 最大（r1 / r2） | http p95（r1 / r2） | checks（r1 / r2、失敗） | 判定 |
|---|---|---|---:|---:|---:|---|---|---|---|
| `preferencesGet` | U2 NFR2.1.3 | < 1,000 | 1.94 | 1.71 | 1.94 | 1,315.59 / 1,229.42 | 1.85 / 1.63 | 667,721 / 718,554、0 | Met |
| `preferencesSave` | U2 NFR2.1.3 | < 1,000 | 2.63 | 2.29 | 2.63 | 1,235.94 / 1,182.86 | 2.50 / 2.16 | 493,065 / 526,255、0 | Met |
| `preferencesInvalid` | U2 NFR2.1.3 | < 1,000 | 6.76 | 6.58 | 6.76 | 1,342.10 / 1,193.39 | 4.46 / 4.08 | 394,416 / 410,492、0 | Met |
| `preferencesSaveTimeZone`（op `set`） | U2 NFR2.1.4 | < 1,000 | 4.65 | 4.29 | 4.65 | 912.53 / 878.35 | — | （場面の合計で下の行） | Met |
| `preferencesSaveTimeZone`（op `clear`） | U2 NFR2.1.4 | < 1,000 | 2.86 | 2.69 | 2.86 | 100.95 / 10.38 | — | 同上 | Met |
| `preferencesSaveTimeZone`（op `invalid`） | U2 NFR2.1.4 | < 1,000 | 3.69 | 3.41 | 3.69 | 31.08 / 12.35 | — | 同上 | Met |
| `preferencesSaveTimeZone`（`name` の成功 / 拒否） | U2 NFR2.1.4 | < 1,000 | 2.89 / 3.51 | 2.73 / 3.24 | 2.89 / 3.51 | 98.89・30.80 / 12.46・12.24 | （この行が http） | 699,718 / 733,284、0 | Met |
| `navMenu` | U5 NFR2.1.2 | < 1,000 | 159.74 | 156.24 | 159.74 | 278.52 / 228.24 | 156.92 / 153.37 | 16,262 / 16,504、0 | Met |
| `navMenuBaseline` | U5 NFR2.1.2 | < 1,000 | 135.33 | 136.31 | 136.31 | 213.93 / 230.17 | 135.12 / 136.09 | 19,191 / 18,959、0 | Met |
| `bizDefinition` | U4 NFR2.1.2 | < 1,000 | 5.90 | 6.45 | 6.45 | 51.48 / 25.87 | 4.85 / 5.30 | 611,948 / 543,284、0 | Met |
| `bizSearchFirst` | U4 NFR2.1.2・U3 NFR2.1.5 | < 1,000 | 61.66 | 58.24 | 61.66 | 222.90 / 123.89 | 47.78 / 44.67 | 44,945 / 47,341、0 | Met |
| `bizDetail` | U4 NFR2.1.2・U3 NFR2.1.5 | < 1,000 | 65.94 | 65.58 | 65.94 | 237.48 / 111.46 | 65.82 / 65.47 | 38,596 / 38,118、0 | Met |
| `bizSearchContains` | U4 NFR2.1.2・U3 NFR2.1.5 | < 3,000 | 613.47 | 591.45 | 613.47 | 1,021.25 / 713.31 | 606.72 / 586.65 | 3,678 / 3,738、0 | Met |
| `bizSearchLast` | U4 NFR2.1.2・U3 NFR2.1.5 | < 2,000 | 75.67 | 77.31 | 77.31 | 158.70 / 157.69 | 64.97 / 66.23 | 34,225 / 33,473、0 | Met |
| `bizSearchLastSorted` | U4 NFR2.1.2・U3 NFR2.1.5 | < 2,000 | 228.62 | 221.60 | 228.62 | 386.63 / 300.57 | 222.89 / 215.43 | 11,131 / 11,344、0 | Met |
| `refDefinition200`（上限 5） | U4 NFR2.1.2（参照の選択肢） | < 1,000 | 15.28 | 14.04 | 15.28 | 67.27 / 45.21 | 14.89 / 13.72 | 202,443 / 209,035、0 | Met |
| `refPickerEquals`（上限 5） | U4 NFR2.1.2（ピッカー） | < 1,000 | 69.09 | 68.42 | 69.09 | 163.01 / 120.50 | 68.90 / 68.30 | 32,023 / 31,995、0 | Met |
| `refLabel`（上限 5） | U4 NFR2.1.2（ラベルの1件読み） | < 1,000 | 67.45 | 67.27 | 67.45 | 131.45 / 136.08 | 67.29 / 67.15 | 32,181 / 32,094、0 | Met |
| `refPickerContains`（上限 5） | U4 NFR2.1.2（ピッカー） | < 3,000 | 514.64 | 526.74 | 526.74 | 769.19 / 760.68 | 514.40 / 526.52 | 4,427 / 4,369、0 | Met |

注:

- 設定の3場面（`preferencesGet`・`preferencesSave`・`preferencesInvalid`）は、台本に `iteration_duration{scenario:<場面>}` の閾値が無く summary json に場面ごとの値が出ないため、場面を1つだけ流した回の全体の `iteration_duration` を判定の値とした。全体の値には `setup()`（10 人のログイン）の1件が入るため、最大の 1.2〜1.3 秒はその1件と見られる（p95 には効かない。台本の判定の閾値 `http_req_duration{name:<場面>}` の p95 も 1.63〜4.46 ms で線の内）。
- `preferencesSaveTimeZone` の op `set` の最大 878〜913 ms も、各 VU の最初の回と見られる（p95 は 4.3〜4.7 ms）。原因は調べていない。
- `preferencesInvalid` の `http_req_failed` 197,208 / 205,246 件と `preferencesSaveTimeZone` の 116,616 / 122,209 件は、期待の 400（`VALIDATION_FAILED`）で、`checks` はすべて合格。アプリのログの `VALIDATION_FAILED` の WARN 641,279 件は、この4回の 400 の合計（402,454 + 238,825 = 641,279）とちょうど一致した。
- 参照の場面の `http_req_failed` 5〜14 件は setup の 409 `ROLE_NO_CHANGE`（前の回のロール・割り当てを受け入れる）。アプリのログの `ROLE_NO_CHANGE` の WARN は 103 件（5 + 14 × 7）で一致した。

## 3. プール・メモリ・状態

- 対象DB の読み取りのプール（`pool=mastersmith-target-db`）の待ちの時間切れの累計（`hikaricp.connections.timeout` の COUNT）: 全 32 回の前後で 0.0 → 0.0（増分 0）。503 は 0 件（`checks` の失敗 0）。
- 借りる待ちの最大（`hikaricp.connections.acquire` の MAX、秒。各回の後に読んだ値で記録だけ）: 業務データの場面で最大 0.019（`bizSearchLastSorted`）、参照の場面で最大 0.602（`refPickerContains` r1。前の Intent 261101 の PG は 0.596）。借りる待ちの上限 3 秒には届いていない。
- 片付けの前の状態: 使い捨てのアプリ running・`OOMKilled=false`・`memory.peak` 1,277,755,392 B（上限 2g の 59.5%。記録だけ）。対象DB の PostgreSQL running・`OOMKilled=false`・`memory.events` の `oom` 0・`oom_kill` 0・`max` 161（上限での回収の回数で、OOM ではない）。

## 4. 前の値との並べ（U3 NFR2.1.5 の前後、U4・U5・U2 の参考）

前は、この Intent の直前の版（Intent 261101-maintenance-update の Build and Test。同じ手順・同じ PG の設定、1回）。悪化の目安として並べるもので、前の値を線にしない。

| 場面 | 今回（悪い側） | 前（261101 の PG 2回目） | 差 |
|---|---:|---:|---:|
| `bizDefinition` | 6.45 | 7.64 | −1.19 |
| `bizSearchFirst` | 61.66 | 65.45 | −3.79 |
| `bizDetail` | 65.94 | 64.11 | +1.83 |
| `bizSearchContains` | 613.47 | 615.93 | −2.46 |
| `bizSearchLast` | 77.31 | 76.75 | +0.56 |
| `bizSearchLastSorted` | 228.62 | 218.38 | +10.24 |
| `refDefinition200` | 15.28 | 14.42 | +0.86 |
| `refPickerEquals` | 69.09 | 69.05 | +0.04 |
| `refLabel` | 67.45 | 66.60 | +0.85 |
| `refPickerContains` | 526.74 | 521.23 | +5.51 |

- 業務データ・参照の PostgreSQL の値は、どれも前の版と同じ水準（差は最大で +10.24 ms、+4.7%）。U3・U4 の変更で悪化した様子は見えない。
- `navMenu` 159.74・`navMenuBaseline` 136.31 ms は、前（Intent 261025 の `navMenu` 187.1・159.3 ms、Intent 261004 の `navMenu` 181.1・`navMenuBaseline` 229.2 ms）と同じ水準か、それより速い。
- 設定の3場面は前（Intent 260925 の Performance Validation、`http_req_duration{name}` の p95: Get 3.65・Save 3.87・Invalid 5.95 ms）と同じ水準（今回は 1.63〜4.46 ms）。条件（内部DB の膨らみ方・測った版）が違うため参考。

## 5. MySQL・MariaDB の回（U3 NFR2.1.5・U4 NFR2.1.2。依頼者の決定で追加）

最初の報告では時間の上限のため Unverified としたが、依頼者の決定で同じ版・同じ手順で追加して流した。

### 5.1 条件

| 項目 | 値 |
|---|---|
| 版 | 1 節と同じ（コミット `2a6e05f7`、WAR の SHA-256 `e2563954…`、イメージ `mastersmith:perf-tz-bt`。流す前に、手元の WAR とイメージの中の WAR の SHA-256 が同じことを確かめ直した） |
| 配備したアプリ | 2026-11-04T18:43:02Z に止め、20:36:01Z に戻した（約 1 時間 53 分）。試験のタグの変数を渡さずに `docker compose start app` で戻し、healthy、イメージ ID は `mastersmith:local`（`sha256:ffd2b838…`）と一致 |
| 対象DB | MySQL 26.10.0（バッファープール 1,342,177,280 B = 1280M、メモリの上限 2,818,572,288 B = 2688m）、MariaDB 13.0.2（939,524,096 B = 896M、1,476,395,008 B = 1408m）。README の業務データの試験の値 |
| 種と確かめ | `make-biz-seed.sh mysql`・`mariadb` の既定を `--default-character-set=utf8mb4` で投入。どちらも行 100,000・目印の語の当たり 909・最長 2,000,000 字。表ごとの行の数（`biz_code200` 200・`biz_code201` 201・`biz_refcreate` 0）も種の出力で確かめた |
| 表の大きさ / バッファー | MySQL 1,017,905,152 B / 1,342,177,280 B = **75.8%**、MariaDB 747,372,544 B / 939,524,096 B = **79.5%**（どちらも 80% の内。前の Intent と同じ値） |
| 暖め | 集計の形（`SELECT COUNT(*), SUM(LENGTH(CONCAT_WS('', id, c_001, …, c_099))) FROM bizperf.biz_item`）を1回 |
| 流し方 | 種類ごとに使い捨ての環境を作り直し（MySQL → MariaDB）、業務データの5場面（プールの上限 10）→ アプリを作り直して上限 5 を確かめ（どちらも `hikaricp.connections.max` が 5.0）→ 参照の4場面。場面ごとに r1・r2 を続けて流し、悪い側で判定。`bizDefinition` は対象DB に接続しないため PostgreSQL の回だけ（README のとおり）で、この回には入れていない |
| 眠り | 2種類の全体を1つの `caffeinate -dims` で包んだ。流す前に `PreventSystemSleep` 1・`PreventUserIdleDisplaySleep` 1。流した後の `pmset -g log` で、窓（日本時間 03:43:08〜05:35:26）に `Sleep`・`DarkWake`・`Wake` は 0 件（最後の記録は始める前の 03:41:19 の DarkWake → FullWake） |
| 窓 | MySQL 18:43:08Z〜19:39:19Z、MariaDB 19:39:20Z〜20:35:25Z（UTC） |

### 5.2 場面 × 回の値（ミリ秒。判定は `iteration_duration{scenario:<場面>}` の p95）

`checks` の失敗はすべて 0、k6 の終了コードはすべて 0。参照の場面の `http_req_failed` 5〜14 件は setup の 409 `ROLE_NO_CHANGE`（2 節の注と同じ）。

| 場面 | 線 | MySQL r1 | MySQL r2 | MySQL 悪い側 | MariaDB r1 | MariaDB r2 | MariaDB 悪い側 | 判定 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `bizSearchFirst` | < 1,000 | 239.42 | 222.06 | 239.42 | 334.75 | 313.40 | 334.75 | Met・Met |
| `bizDetail` | < 1,000 | 3.44 | 3.71 | 3.71 | 3.80 | 3.94 | 3.94 | Met・Met |
| `bizSearchContains` | < 3,000 | 1,270.97 | 1,281.27 | 1,281.27 | 2,573.13 | 2,545.33 | 2,573.13 | Met・Met |
| `bizSearchLast` | < 2,000 | 372.73 | 380.49 | 380.49 | 1,238.74 | 1,183.10 | 1,238.74 | Met・Met |
| `bizSearchLastSorted` | < 2,000 | 463.85 | 478.93 | 478.93 | 1,458.16 | 1,291.78 | 1,458.16 | Met・Met |
| `refDefinition200`（上限 5） | < 1,000 | 8.70 | 8.36 | 8.70 | 9.18 | 8.73 | 9.18 | Met・Met |
| `refPickerEquals`（上限 5） | < 1,000 | 3.79 | 3.81 | 3.81 | 3.46 | 3.44 | 3.46 | Met・Met |
| `refLabel`（上限 5） | < 1,000 | 3.77 | 3.76 | 3.77 | 3.40 | 3.39 | 3.40 | Met・Met |
| `refPickerContains`（上限 5） | < 3,000 | 1,175.62 | 1,175.06 | 1,175.62 | 1,206.30 | 1,223.73 | 1,223.73 | Met・Met |

最大（r1 / r2）と `checks` の合格の件数（r1 / r2）:

| 場面 | MySQL 最大 | MySQL checks | MariaDB 最大 | MariaDB checks |
|---|---|---|---|---|
| `bizSearchFirst` | 911.63 / 314.11 | 10,281 / 10,876 | 562.36 / 485.68 | 7,537 / 7,861 |
| `bizDetail` | 36.18 / 16.02 | 933,210 / 868,852 | 20.25 / 18.28 | 869,734 / 835,045 |
| `bizSearchContains` | 1,336.81 / 1,396.74 | 1,673 / 1,655 | 2,849.22 / 2,753.79 | 818 / 824 |
| `bizSearchLast` | 480.46 / 507.91 | 6,112 / 6,026 | 1,370.73 / 1,300.92 | 1,735 / 1,809 |
| `bizSearchLastSorted` | 580.42 / 675.45 | 4,905 / 4,772 | 1,596.63 / 1,403.08 | 1,478 / 1,652 |
| `refDefinition200` | 64.93 / 26.64 | 368,181 / 372,364 | 35.97 / 32.16 | 357,014 / 367,445 |
| `refPickerEquals` | 18.29 / 17.94 | 969,119 / 967,441 | 19.53 / 23.29 | 1,070,859 / 1,055,651 |
| `refLabel` | 16.41 / 17.75 | 978,823 / 979,304 | 18.02 / 16.56 | 1,070,881 / 1,071,619 |
| `refPickerContains` | 2,988.84 / 1,810.29 | 1,795 / 1,796 | 1,825.87 / 2,077.23 | 1,739 / 1,733 |

- 線に最も近いのは MariaDB の `bizSearchContains`（2,573.13 ms、線 3,000 ms まで 427 ms）。前の Intent と同じ水準。

### 5.3 前の値との並べ（U3 NFR2.1.5。悪い側の p95 ms）

前は Intent 261101-maintenance-update の Build and Test（MySQL 1回目・MariaDB 2回目、各1回）と、Intent 261027-perf-targets-review の測り直し（r1 / r2）。差は今回 − 261101。

| 場面 | DB | 今回 | 261101 | 差 | 261027 |
|---|---|---:|---:|---:|---|
| `bizSearchFirst` | MySQL | 239.42 | 381.74 | −142.32 | — |
| `bizDetail` | MySQL | 3.71 | 4.61 | −0.90 | — |
| `bizSearchContains` | MySQL | 1,281.27 | 1,470.31 | −189.04 | 1,433.2 / 1,252.4 |
| `bizSearchLast` | MySQL | 380.49 | 440.08 | −59.59 | 403.9 / 410.7 |
| `bizSearchLastSorted` | MySQL | 478.93 | 512.24 | −33.31 | 494.3 / 532.1 |
| `refDefinition200` | MySQL | 8.70 | 9.13 | −0.43 | — |
| `refPickerEquals` | MySQL | 3.81 | 3.96 | −0.15 | — |
| `refLabel` | MySQL | 3.77 | 4.00 | −0.23 | — |
| `refPickerContains` | MySQL | 1,175.62 | 1,244.77 | −69.15 | — |
| `bizSearchFirst` | MariaDB | 334.75 | 273.55 | **+61.20** | — |
| `bizDetail` | MariaDB | 3.94 | 3.96 | −0.02 | — |
| `bizSearchContains` | MariaDB | 2,573.13 | 2,613.94 | −40.81 | 2,555.1 / 2,743.5 |
| `bizSearchLast` | MariaDB | 1,238.74 | 1,200.36 | **+38.38** | 1,399.1 / 1,392.9 |
| `bizSearchLastSorted` | MariaDB | 1,458.16 | 1,515.89 | −57.73 | 1,831.1 / 1,721.8 |
| `refDefinition200` | MariaDB | 9.18 | 9.12 | +0.06 | — |
| `refPickerEquals` | MariaDB | 3.46 | 3.74 | −0.28 | — |
| `refLabel` | MariaDB | 3.40 | 3.86 | −0.46 | — |
| `refPickerContains` | MariaDB | 1,223.73 | 1,290.33 | −66.60 | — |

- MySQL はすべての場面で前（261101）と同じか速い。
- MariaDB の `bizSearchFirst` は前（261101）より +61.20 ms（+22.4%）遅い。ただし、それより前の Intent 261016 の値（649.70 ms、プールの上限 5）よりは速く、線（1,000 ms）まで 665 ms ある。`bizSearchLast` の +38.38 ms（+3.2%）は、261027 の 1,399.1 / 1,392.9 ms より速い。どちらも原因は調べていない。前の版の回数が1回であることも差の読みに効く。
- U3 の変更で悪化したと判断できる差は見当たらない（線を割った場面は無い）。

### 5.4 プール・メモリ・状態

- 対象DB の読み取りのプールの待ちの時間切れの累計: MySQL・MariaDB の全 36 回の前後で 0.0 → 0.0（増分 0）。503 は 0 件。
- 借りる待ちの最大（`hikaricp.connections.acquire` の MAX、秒。各回の後に読んだ値で記録だけ）: 業務データの場面は最大 0.028（MySQL）・0.018（MariaDB）。参照の場面は `refPickerContains` が最大で、MySQL r1 **2.557**・r2 1.226、MariaDB r1 1.191・r2 1.585。MySQL の r1 は、借りる待ちの上限 3 秒（時間切れで 503）に近い値になった（前の Intent 261101 の MySQL は 1.230）。時間切れは起きておらず、合否には効かないが、上限 5 のプールでの部分一致のピッカーの余裕は小さい。
- 対象DB の `anon` の最大（2 秒ごとの記録。業務データと参照の読み取りの範囲）: MySQL 2,072,821,760 B（1,976.8 MiB）/ 2,688 MiB = **73.5%**、MariaDB 1,040,101,376 B（991.9 MiB）/ 1,408 MiB = **70.4%**。どちらも 92% の線の内（記録の範囲は読み取りだけで、書き込みの場面は流していない）。
- 片付けの前の状態: 両方の回とも、使い捨てのアプリと対象DB は running・`OOMKilled=false`。対象DB の `memory.events` は `oom` 0・`oom_kill` 0・`max` 0。アプリの `memory.peak` は MySQL 810,598,400 B（37.7%）・MariaDB 771,948,544 B（35.9%）（記録だけ）。

## 6. 測っていないもの

| 対象 | 理由 | 持ち越し |
|---|---|---|
| `refDefinition201`・`refCreateChecked`・書き込みの場面・`bizSearchWide`・`bizCreateLarge` | この Intent の NFR の ID（U2 NFR2.1.3・NFR2.1.4、U5 NFR2.1.2、U4 NFR2.1.2、U3 NFR2.1.5）の範囲の外として流していない（依頼の「範囲を広げすぎない」） | 要るときは指揮役が判断する |

## 7. 伏せ字と秘密の確かめ

- 置いたもの: k6 の summary json 68 件（PostgreSQL の回の `biz-*`・`ref-*`・`nav-*`・`pref-*` の 32 件と、`mysql-*`・`mariadb-*` の業務データと参照の 36 件。各 r1・r2）。準備の場面（`bizPrepare`・`rolePrepare`・`navPrepare`・暖め）の json と k6 の標準出力は置いていない。
- 伏せ字の道具（`perf/redact-k6-evidence.mjs`、JSON の口）: 元の json の JWT の形の文字列は PostgreSQL の回 240 件・MySQL と MariaDB の回 360 件で、すべて `setup_data` の下にあり、`[REDACTED-SETUP]` に置き換わった（240 件・360 件。`[REDACTED-JWT]` は 0 件）。置いた後の `evidence/` の JWT の形の文字列（先頭が `e`・`y`・`J` の3文字のもの）は **0** 件（json と、この md を含むすべてのファイル）。
- `node perf/redact-k6-evidence.mjs --scan evidence/`: 69 ファイル、検出 0 件（終了コード 0）。
- `gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 evidence/`: no leaks found（終了コード 0）。
- PostgreSQL の回の使い捨てのアプリのログ（参照の前に作り直す前の分 103 行と、その後の分 641,431 行）: 試験用の利用者の名前 0・行の値（目印の語など）0・JWT の形の文字列 0・ERROR 0・OutOfMemoryError 0。WARN は起動の案内（Flyway の H2 の版・Bean の後処理）が起動ごとに2行（3回の起動で 6 行）と、2 節の注の `VALIDATION_FAILED` 641,279・`ROLE_NO_CHANGE` 103 だけ。
- MySQL・MariaDB の回の使い捨てのアプリのログ（どちらも、作り直す前の分 103〜104 行と、その後の分 144 行）: 試験用の利用者の名前 0・行の値 0・JWT の形の文字列 0・ERROR 0・OutOfMemoryError 0。WARN は起動の案内 6 行（3回の起動）と `ROLE_NO_CHANGE` 103（setup の 409 の件数 5 + 14 × 7 と一致）だけ。ログは駆動が種類ごとの `down -v` の前にリポジトリの外の一時の置き場へ残し、片付けの後にそこから数えた（数えた後に一時の置き場ごと消した）。
- k6 の出力に試験用の利用者の名前を含むものは、どの回も 0 件。
