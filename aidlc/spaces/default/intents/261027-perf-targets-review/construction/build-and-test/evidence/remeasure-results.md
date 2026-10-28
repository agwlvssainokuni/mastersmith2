# 測り直しの結果（Not Met だった場面。Intent 261027-perf-targets-review の Build and Test）

担当: 品質。測った日: 2026-10-28（UTC）。生の記録は同じ `evidence/` に置いた（下の「記録の置き場」）。

## 1. 条件

| 項目 | 値 |
|---|---|
| コミット | b342649（develop 系の作業ブランチ `fix/261027-perf-targets-review`） |
| 試験のイメージ | `mastersmith:perf-targets-review`（配備したイメージ `mastersmith:local`、ID `sha256:3075065a2653…` に付けたタグ。`mastersmith:local` は動かしていない） |
| イメージの中の WAR の SHA-256 | `ff50498b88a97ac122ac39336ed946908389b1ddcacb14665e8412deead6f9f0` |
| 手元の `backend/build/libs/mastersmith.war` の SHA-256 | `ff50498b88a97ac122ac39336ed946908389b1ddcacb14665e8412deead6f9f0`（**イメージの中と同じ**。前の Intent で見つかった食い違いは今回は無い） |
| VM / コンテナの上限 | colima VM は CPU 4・メモリ 6GiB。アプリは CPU 4・メモリ 2g（配備と同じ） |
| k6 | `grafana/k6:2.3.0`（`perf/versions.env` の固定の値）。10 VU・3 分（`BIZ_DURATION` 既定） |
| 対象DB | MySQL 26.10.0（バッファープール 1280M = 1,342,177,280 B、`mem_limit` 2688m = 2,818,572,288 B）、MariaDB 13.0.2（896M = 939,524,096 B、1408m = 1,476,395,008 B）。README の「業務データの試験の値」のとおり |
| 種 | `make-biz-seed.sh` の既定（`biz_item` 100,000 行・`biz_pair` 1,000 行・`biz_wide` 200 行）。MySQL・MariaDB とも `--default-character-set=utf8mb4` で投入（約 12 秒） |
| 種の確かめ（表の大きさを測る前） | MySQL: 行 100000・目印の語の当たり 909・最長 2,000,000 字。MariaDB も同じ確かめを通過（`*-seed-check.txt`） |
| 表の大きさ（データ+索引）/ プール | MySQL 1,017,806,848 B / 1,342,177,280 B = **75.83%**。MariaDB 747,274,240 B / 939,524,096 B = **79.54%**（どちらも 80% 以内。前の Intent と同じ値） |
| 暖め | 種と `bizPrepare` の後に `SELECT * FROM bizperf.biz_item` を1回流した |
| 回数 | 場面ごとに 2 回（r1・r2）を続けて流した。`dslMixed` は 3 回（下記） |
| 流した場面 | MySQL・MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted` のみ（Q2: A）。読み取りだけで、書き込みの場面は流していない |
| 判定の物差し | `iteration_duration{scenario:<名前>}` の p95（台本の `thresholdsFor` は新しい線。k6 の終了コードは新しい線の判定） |
| 眠り | 窓（2026-10-28T14:46:27Z〜15:41:48Z、JST 23:46〜00:41）に `Sleep`・`DarkWake`・`Wake` の記録は無い（最後は JST 23:34:02。`pmset-log.txt`）。`caffeinate -dims` で包み、始めに `PreventUserIdleSystemSleep` が 1 であることを確かめた。VM と手元の時計のずれは `dslMixed` の `clock.tsv` で 0 秒 |

## 2. 場面 × DB × 回の p95（ミリ秒）

新しい線: `bizSearchContains` < 3000、`bizSearchLast`・`bizSearchLastSorted` < 2000。前の線: すべて < 1000。各行とも `checks` の失敗 0・`http_req_failed` 0・k6 の終了コード 0（新しい線で通過）。

| 場面 | DB | r1 | r2 | 悪い側 | 新しい線 | 前の線 | 前回（261025-deps-perf-carryover）の値 |
|---|---|---:|---:|---:|---|---|---|
| bizSearchContains | MySQL | 1433.2 | 1252.4 | 1433.2 | Met | Not Met | 1786.8 / 1848.0 |
| bizSearchLast | MySQL | 403.9 | 410.7 | 410.7 | Met | Met | （前回は MySQL の記録なし。下の注） |
| bizSearchLastSorted | MySQL | 494.3 | 532.1 | 532.1 | Met | Met | （同上） |
| bizSearchContains | MariaDB | 2555.1 | 2743.5 | 2743.5 | Met | Not Met | 2443.0 / 2508.3 |
| bizSearchLast | MariaDB | 1399.1 | 1392.9 | 1399.1 | Met | Not Met | 1191.2 / 1191.3 |
| bizSearchLastSorted | MariaDB | 1831.1 | 1721.8 | 1831.1 | Met | Not Met | 1549.6 / 1535.3 |

- 注: 前回として依頼文に挙がった値は MariaDB の3場面と MySQL の Contains だけで、MySQL の Last・LastSorted の前回値は今回の指示に無いため並べていない。
- MariaDB の Contains（悪い側 2743.5 ms）と LastSorted（1831.1 ms、線の 2000 ms まで 169 ms）は新しい線に近い。MariaDB の3場面は前回より遅い値（+約 150〜300 ms）になった。MySQL の Contains は前回より速い（1252〜1433 ms）。この差の原因は調べていない。
- 対象DB のプール（`mastersmith-target-db`）の待ちの時間切れの累計の増分は全 12 回で 0.0。借りるまでの待ちの最大（生の値。`baseUnit` は記録していない）は最大でも 0.22（MySQL の最初の読み）。アプリの 503 の指標は MySQL・MariaDB とも現れなかった（`*-collect.txt`）。

## 3. `dslMixed` の `memory.peak`（アプリのコンテナ、上限 2g = 2,147,483,648 B）

既定の接続先（`perf/dsl-mixed-memory.sh`、`dsl-timing.sh postgres` の環境、内部DB の `RETENTION_TIME` は 45000）。1・2 回目の差が 1.91 ポイント（≥ 1 ポイント）のため、決まりどおり 3 回目を足した。

| 回 | peak_ratio | judge（新しい線 0.95） | judge_090（前の線 0.90） | anon_max_ratio（85% の線） | k6 | OOMKilled |
|---|---:|---|---|---:|---|---|
| default-1 | 0.9053 | under-0.95 | not-under-0.9 | 0.6914 | 終了コード 0・checks 失敗 0 | false |
| default-2 | 0.8862 | under-0.95 | under-0.9 | 0.6877 | 同上 | false |
| default-3 | 0.8840 | under-0.95 | under-0.9 | 0.6965 | 同上 | false |
| **3 回の最悪値** | **0.9053** | **Met** | **Not Met**（1 回が 0.90 以上） | 0.6965（**Met**、85% 以下） | | |

- 前回: 94.4% / 93.1%（261025-deps-perf-carryover）。今回は 88.4〜90.5%。値が下がった原因は調べていない（回ごとの差も 1.9 ポイントあった）。
- peak の時の内訳は `anon` 約 1.48 GB・`file`（回収できるページキャッシュ）約 0.38〜0.44 GB で、前回の記録の読みと同じ傾向（`dslmixed-summaries.txt`）。
- ログ: 各回 WARN 2 行（起動時の Flyway の H2 の版・Bean の後処理の案内）、ERROR 0、OutOfMemoryError 0。

## 4. 対象DB の `anon` の最大（今回の範囲は読み取りの3場面だけ）

1 秒ごとにコンテナの `memory.stat` の `anon` を記録した（MySQL 1,073 点、MariaDB 1,069 点。`*-target-memory.tsv`）。

| DB | `anon` の最大 | `memory.max` | 割合 | OOMKilled | `memory.events` の oom_kill | 新しい線（≤ 92%） | 前の線（85%、超えたら諮る） | 前回 |
|---|---:|---:|---:|---|---:|---|---|---|
| MySQL | 1922.9 MiB | 2688 MiB | **71.54%** | false | 0 | Met | Met | 90.3% |
| MariaDB | 979.6 MiB | 1408 MiB | **69.57%** | false | 0 | Met | Met | 85.01% |

- 場面別（r1/r2）の最大は MySQL: Contains 1523.6/1520.1、Last 1914.9/1919.1、LastSorted 1922.9/1921.7 MiB、MariaDB: Contains 946.5/946.3、Last 978.1/979.6、LastSorted 979.5/979.6 MiB（割合は `*-collect.txt`）。
- **前回との比較の注意**: 前回の 90.3%・85.01% は、書き込みの場面（`bizCreate` など）を含む測りの最大。前回の読み取りの場面だけの記録（`bizSearch*`、MySQL）は 1,959.8〜1,965.9 MiB（約 72.9〜73.1%）で、今回の 1,520〜1,923 MiB（56.6〜71.5%）と同じ水準。今回は書き込みを流していないため、書き込みを含めた最大は今回の測りでは確かめていない。
- 対象DB の `memory.peak`（ページキャッシュを含む）は MySQL 2,258,214,912 B、MariaDB 1,086,423,040 B。判定の物差し（`anon`）ではない。

## 5. 結果のまとめ（新旧の線）

| 目標 | 新しい線 | 前の線 |
|---|---|---|
| MySQL bizSearchContains p95 | Met（1433.2 < 3000） | Not Met（> 1000） |
| MySQL bizSearchLast p95 | Met | Met |
| MySQL bizSearchLastSorted p95 | Met | Met |
| MariaDB bizSearchContains p95 | Met（2743.5 < 3000） | Not Met |
| MariaDB bizSearchLast p95 | Met（1399.1 < 2000） | Not Met |
| MariaDB bizSearchLastSorted p95 | Met（1831.1 < 2000） | Not Met |
| dslMixed memory.peak 比（3 回の最悪） | Met（0.9053 < 0.95） | Not Met（0.9053 ≥ 0.90） |
| dslMixed anon_max_ratio | Met（0.6965 ≤ 0.85） | 同左 |
| 対象DB の anon（読み取りの範囲） | Met（MySQL 71.54%・MariaDB 69.57% ≤ 92%） | Met（≤ 85%） |
| OOMKilled / oom_kill / OutOfMemoryError | すべて false / 0 / 0 | 同左 |

## 6. WARN・ERROR・漏えい

- 使い捨てのアプリのログ（各 DB の全場面の後）: 試験用の利用者名 0 件、行の値（目印の語など）0 件、OutOfMemoryError 0 件、WARN 4 行（起動ごと 2 行 × 2 回の起動。上記の Flyway と Bean の案内）、ERROR 0 行。
- k6 の出力に試験用の利用者名を含む行は全場面で 0。k6 の出力ファイルと資格情報の環境ファイルは記録に置いていない。

## 7. 停止の時間と片付け

- 配備したアプリ（`mastersmith-app-1`）の停止: **2026-10-28T14:46:27Z 〜 15:41:48Z（3,321 秒、55 分 21 秒）**。内訳は測定 約 54 分と後述のやり直し約 1 分。
- 使い捨ての環境は README の手順で片付けた（`down -v`、一時ディレクトリの削除。`mastersmith-perf_*` のボリューム 0、`~/.mastersmith-perf-*` 0）。`mastersmith` 側のコンテナ・ボリューム、mailpit、見本の PostgreSQL（`mastersmith-targetdb-postgres-1`）、イメージ `mastersmith:local` は変えていない。タグ `mastersmith:perf-targets-review`（`mastersmith:local` と同じ ID）は残してある。
- 起動し直し後の確認: `mastersmith-app-1` は `mastersmith:local`、メモリ 2 GiB・CPU 4、healthy。

## 8. 計画との差・つまずき

1. **配備したアプリを一度、誤ったタグで起動した**: 駆動スクリプトが `MASTERSMITH_IMAGE_TAG=perf-targets-review` を export したまま `docker compose up -d app` を実行したため、`mastersmith-app-1` がタグ `mastersmith:perf-targets-review`（ID は local と同じ）で作り直された（15:40:46Z）。気づいて、タグを渡さない環境で `docker compose up -d app` をやり直し、`mastersmith:local` で healthy を確かめた（15:41:48Z）。中身（イメージ ID）は同じで、アプリのコード・設定は変わっていない。README の手順の最後（`docker compose up -d --wait`）は、試験用のタグの変数を `unset` してから行う必要がある（README の不具合ではなく、駆動スクリプトの誤り）。
2. 3 回目の `dslMixed` は決まり（2 回の差 ≥ 1 ポイント）で足した。
3. 見込み 3〜5 時間に対し、実際は約 55 分で終わった（種の投入が約 12 秒、各場面 3 分 × 12 回 + `dslMixed` 3 回）。
4. 暖めは `SELECT * FROM bizperf.biz_item` を1回流す形（README は「表を1回全部読んで暖める」とだけ書く）。台本・README・ソースは変えていない。
5. 場面の並びは、場面ごとに r1・r2 を続けて流す形（前回の記録と同じ）。MySQL → MariaDB → `dslMixed` の順。
6. 対象DB の待ちの最大（`hikaricp.connections.acquire` の MAX）の `baseUnit` は記録していない（値は生の数値）。時間切れの累計（COUNT）の増分は単位に依らず 0。
7. 前回の MySQL の Last・LastSorted の値は指示に無く、並べていない。書き込みの場面は流していないため、対象DB の `anon` は読み取りの範囲の値である。

## 9. 記録の置き場（`evidence/`）

- `conditions.txt`（WAR の SHA-256・イメージ ID・停止と起動の時刻）、`pmset-log.txt`
- `{mysql,mariadb}-scenarios.tsv`（p95・最大・checks・待ちの時間切れの増分・開始と終了の epoch）、`*-hikari-oom.tsv`（前後の Hikari の値・OOMKilled・`memory.events`）、`*-collect.txt`（表の大きさ・設定の実際の値・ログの件数・場面別の `anon` の最大）、`*-seed-check.txt`、`*-target-memory.tsv`（1 秒ごとの `anon`）、`*-times.tsv`、`biz-<DB>-<場面>-r<n>.json`（k6 の summary json）
- `memory-default/default-{1,2,3}/`（`summary.txt`・`memory.tsv`・`peak.txt`・`clock.tsv`・`k6.txt`・`dslMixed.json` など）、`dslmixed-summaries.txt`
