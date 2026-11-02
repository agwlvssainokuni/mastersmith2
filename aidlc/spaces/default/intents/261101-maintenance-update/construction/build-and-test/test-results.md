# テストの結果 — Build and Test（Intent 261101-maintenance-update）

時刻は UTC。件数とカバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` の実測だけを書く。秘密の値・行の値・メールアドレス・JWT は書かない。目標ごとの最終の判定は `build-and-test-summary.md` の `## Target Verification Matrix`。生の記録は `build/perf-results/bt-mu/`（git の対象外）にあり、参照の場面の summary json だけを伏せて `evidence/` に置いた。

## 1. ビルドの状態

| 項目 | 結果 |
|---|---|
| 確かめたソース | `develop` の `45a5872a`（= `origin/develop`）。ブランチは作らず、コードは変えていない |
| ビルド | **成功**（`verify` の中の型の検査・WAR を含む） |
| WAR | `backend/build/libs/mastersmith.war`（SHA-256 の先頭 `fe3382c51f62d33e`）。試験のイメージ `mastersmith:perf-maintenance-update`（`sha256:0adebf1b4cb9…`）の中の WAR も同じ SHA-256 |

## 2. 1コマンドの検査（verify）

| 項目 | 値 |
|---|---|
| コマンド | `./gradlew --stop` の後、`caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 付き） |
| 窓 | 06:11:54Z〜06:39:29Z |
| 終了コード | 0（`BUILD SUCCESSFUL in 27m 34s`） |
| 単体テスト（backend） | **2,809 件**、失敗 0・エラー 0・SKIPPED 0 |
| 結合テスト（backend） | **1,663 件**、失敗 0・エラー 0・SKIPPED 10（DB の種類ごとの前からの `assumeTrue`。コンテナが無いための飛ばしは 0） |
| 画面のテスト（Vitest） | **238 ファイル・2,312 件**、失敗 0 |
| 安全の検査 | `spotbugsGate`・`gitleaksScan`・`perfEvidenceScan`・`osvScan` が通過（verify の成功に含まれる） |
| 眠り | 流す前に `PreventUserIdleSystemSleep` 1。窓の後の `pmset -g log` の最後の `Sleep`・`DarkWake` は 05:34Z・05:51Z で、窓の前。流し直しは無い |
| 電源 | 始めは電池（100%）、verify の後は AC（`charged`）だった |

前の Intent（261029 の Build and Test: 単体 2,807・結合 1,654・画面 2,312）からの差は、T3 の単体テスト +2 と P1 の `*DeepPageSortIT`（3種類 × 3 件）+9 で、計画と合う。

## 3. 画面からの一連の操作（E2E、実際の Chromium）

| 項目 | 値 |
|---|---|
| コマンド | `caffeinate -dims ./gradlew e2eTest`（見本の PG は ③ の上乗せのまま `127.0.0.1:15432`、Mailpit は動いていた） |
| 窓 | 06:39:48Z〜06:54:32Z |
| 結果 | **277 passed・unexpected 0・flaky 0・skipped 0**（875.6 秒 = 14.6 分）。`BUILD SUCCESSFUL in 14m 43s` |
| axe | axe の検査のファイル（050〜080・100・120・130・160〜195）はすべて通過＝違反 0 件。axe-core は `frontend/package-lock.json` で `4.14.0` の1つ |
| 眠り | 窓に `Sleep`・`DarkWake` 無し |

報告の json（`frontend/test-results/e2e-results.json`、約 7.5 MB）の検索: `eyJ` 0・`Authorization` 0・`refreshToken` 0・`postgresql://` 0。資格情報の環境変数（`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`・`MASTERSMITH_TARGET_DB_PASSWORD`・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD`）は名前の一覧の中に1回ずつだけで値は無い。`jdbc:` の 1 件は E2E の一時の内部DB（H2 のファイル）の起動の引数。

時間 14.6 分は前の Intent（14.0 分）とほぼ同じ。

### 3.1 見本の PG のポートを閉じた（③ の依頼者の決定）

E2E の後に `docker compose --profile targetdb-postgres up -d --no-deps targetdb-postgres` で上乗せ無しで作り直した（app は作り直していない）。ポートは `5432/tcp` だけで `127.0.0.1:15432` は閉じた（TCP で届かない）。`docker inspect` のイメージは `postgres:18.6@sha256:3a4264520e12…` で、`compose.yaml` の記述とダイジェストまで一致。ボリュームは残した。

## 4. カバレッジ

### 4.1 バックエンド（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）

| 範囲 | 行 | 分岐 |
|---|---|---|
| 全体 | 97.6% | 91.3% |
| `businessdata.domain` | 98.2% | 91.5% |
| `businessdata.service`（T3） | 95.8% | 89.1% |
| `businessdata.repository`（P1） | 96.3% | 88.5% |
| `businessdata.web` | 97.3% | 79.9% |

下限（行 80%・分岐 70%）を下回るパッケージは `common.health`（行 79.2%）だけで、`packagesJudgedByTotal` の一覧にあり全体の合計で判定する（前からの扱い。この Intent は `src/main` に触れていない）。パッケージごとの下限はビルドが確かめて通過した。

### 4.2 画面（`frontend/coverage/coverage-summary.json`）

| 範囲 | 行 | 分岐 |
|---|---|---|
| 全体 | 95.16% | 90.40%（文 95.02%・関数 96.05%） |
| `perf/redact-k6-evidence.mjs`（T1 で対象に入れた） | 94.23% | 90.65% |

新しい機能のフォルダは無い。

## 5. 負荷の試験（FR10.1〜FR10.4）

### 5.1 条件

| 項目 | 値 |
|---|---|
| 配備したアプリを止めた時刻 | **06:55:51Z**（`docker compose stop app`。止める前は `sha256:d8a467b60f6f…`・healthy・RestartCount 0・OOMKilled false） |
| 戻した時刻 | 10:26:31Z に `docker compose start app`、10:26:40Z に healthy・`/actuator/health` 200 |
| 止めていた時間 | **約 3 時間 31 分**（06:55:51Z〜10:26:40Z） |
| 戻した後 | イメージ ID `sha256:d8a467b60f6f…`（`mastersmith:local`、止める前と同じ）・RestartCount 0・OOMKilled false。起動の WARN 2（Flyway の版の案内・Bean の後処理の案内）、ERROR 0 |
| 試験のイメージ | `mastersmith:perf-maintenance-update`（手元の WAR から作った。`mastersmith:local` と `mastersmith:pre-reference-lookup-cleanup` は変えていない・消していない。タグは残した） |
| 線の照らし | 始めに場面ごとに `k6 inspect --include-system-env-vars`（19 場面）で閾値を読み、計画の表と一致。`./perf/k6/thresholds-test.sh` も全件 ok（`build/bt-k6-inspect.txt`） |
| アプリ・k6・対象DB | CPU 4・メモリ 2g。k6 `grafana/k6:2.3.0`（`perf/versions.env` の固定）。各場面 10 VU・3 分（`bizDelete` 100 回、`bizCreateLarge` 10 VU × 3 回）。対象DB は README の業務データの試験の値（PG `shared_buffers` 256MB・768m、MySQL 1280M・2688m、MariaDB 896M・1408m） |
| 読み取りのプール | 業務データの読み取りの場面は既定の上限 10（`hikaricp.connections.max` 10.0。Intent 261027 の測り直しと同じ）。参照・書き込みの場面は上限 5 にしてアプリを作り直し、暖めの後に 5.0 を確かめてから流した（Intent 261029 と同じ） |
| 種 | どの回も `biz_item` 100,000 行・目印の語の当たり 909・最長 2,000,000 字（MySQL・MariaDB は `utf8mb4`） |
| 表の大きさ / プール | PG 212,762,624 B（79.3%）、MySQL 1,017,905,152 B（75.8%）、MariaDB 747,372,544 B（79.5%）。どれも 80% の内で、前の Intent と同じ値 |
| 眠り | 全体を1つの `caffeinate -dims` で包んだ（駆動の台本ごと）。06:55Z〜10:27Z に `Sleep`・`DarkWake` は 0 件。電源は AC |

### 5.2 1回目に起きたこと（環境の不合格と2回目）

| 種類 | 1回目 | 原因の確かめ | 扱い |
|---|---|---|---|
| PostgreSQL（06:56Z〜07:47Z） | 全場面が閾値と `checks` を通ったが、片付けの前の対象DB の状態が **`OOMKilled=true`・`oom_kill 1`**（サーバーは動いたまま）。場面の間の `anon` の最大は 119.5 MiB（15.6%） | 暖めの問い合わせ（`psql -c "SELECT * FROM bizperf.biz_item"`。psql が結果の全体を受ける）が 1 秒で終わっていた。新しい対象DB で同じ問い合わせだけを流して再現: psql が終了コード 137、`memory.events` の `oom_kill` 0→1、`OOMKilled=true`、サーバーは動いたまま（`build/perf-results/bt-mu/postgres-repro/repro.txt`）。強制終了されたのは暖めの道具の psql で、試験の場面ではない | 線（`OOMKilled` false・`oom_kill` 0）を割ったため、暖めを結果1行の集計の形に替えて環境を作り直し、全場面を2回目に流した |
| MySQL（07:47Z〜08:35Z） | 全場面が通過。`OOMKilled` false・`oom_kill` 0 | 暖めの `SELECT *` で `anon` は 1,981.5 MiB まで上がったが上限（2,688 MiB）の内。`anon` の最大 2,428.9 MiB は `bizCreateLarge` の終わり（08:35:05Z）で、暖めの分ではない | 1回目を判定の回とした |
| MariaDB（08:35Z〜08:39Z） | `bizSearchFirst` の 600 回がすべて借りる待ち 3 秒の時間切れ（時間切れの累計の増分 600、k6 の終了コード 99）、以後の場面は setup で止まった（107） | 対象DB のコンテナが暖めの問い合わせ（`mariadb` の道具で `SELECT *`）の最中の **08:35:55Z に OOM で止まっていた**（`OOMKilled=true`・終了コード 137・`anon` 884→1,282 MiB）。アプリの WARN は `BUSINESS_TARGET_DB_UNAVAILABLE` 693 件と HikariCP の接続の失敗 4 件、ERROR 0 | 駆動を止めて片付け、暖めを集計の形にして作り直し、全場面を2回目に流した |

- PostgreSQL・MariaDB は、線を割ったのが場面ではなく対象DB の状態（環境の全体）だったため、その場面だけでなく全場面を流し直した（FR10.1 の「線を割った場面だけ2回目」との差。`build-and-test-summary.md` の計画との差）。
- 2回目はどちらも全場面が通過し、対象DB は `OOMKilled` false・`oom_kill` 0。3回目は流していない。
- 1回目の PostgreSQL の値は判定に使わず、5.3 に参考として並べる。

### 5.3 場面ごとの値（判定の回。`iteration_duration` の p95・最大 ms、`checks`（合格/失敗）、`http_req_duration{name}` の p95、繰り返し）

どの場面も k6 の終了コード 0（閾値を通過）、`checks` の失敗 0。

| 場面 | 線 | PG 2回目 | MySQL 1回目 | MariaDB 2回目 | PG 1回目（参考） |
|---|---|---|---|---|---|
| `bizDefinition` | < 1,000 | 7.64（最大 44.69、461,351/0、http 6.10） | —（PG だけ） | — | 9.02 |
| `bizSearchFirst` | < 1,000 | 65.45（255.08、41,758/0、51.27） | 381.74（781.91、6,707/0、373.31） | 273.55（519.78、9,155/0、268.74） | 86.15 |
| `bizSearchContains` | < 3,000 | 615.93（1,102.11、3,597/0、610.25） | 1,470.31（1,623.89、1,440/0、1,465.42） | **2,613.94**（3,013.86、799/0、2,608.02） | 766.32 |
| `bizSearchLast` | < 2,000 | 76.75（121.21、33,571/0、65.97） | 440.08（829.80、5,345/0、435.97） | 1,200.36（1,343.38、1,788/0、1,195.98） | 117.76 |
| `bizSearchLastSorted` | < 2,000 | 218.38（284.90、11,513/0、212.20） | 512.24（734.09、4,415/0、506.57） | 1,515.89（1,621.21、1,392/0、1,511.29） | 317.30 |
| `bizDetail` | < 1,000 | 64.11（130.48、38,532/0、63.96） | 4.61（26.90、693,351/0、4.48） | 3.96（16.86、831,558/0、3.85） | 110.77 |
| `bizSearchWide`（記録だけ） | — | 236.10（432.01、11,111/0） | 215.21（685.44、12,139/0） | 176.56（308.99、15,104/0） | 367.93 |
| `refDefinition200` | < 1,000 | 14.42（55.50、209,919/0） | 9.13（45.29、350,959/0） | 9.12（41.79、348,755/0） | 30.38 |
| `refDefinition201` | < 1,000 | 11.73（34.49、248,130/0） | 6.20（66.07、524,173/0） | 5.91（20.01、534,187/0） | 14.54 |
| `refPickerEquals` | < 1,000 | 69.05（161.70、31,730/0） | 3.96（16.87、916,671/0） | 3.74（18.07、974,693/0） | 108.23 |
| `refLabel` | < 1,000 | 66.60（133.80、31,911/0） | 4.00（23.55、916,407/0） | 3.86（33.63、964,648/0） | 100.84 |
| `refPickerContains` | < 3,000 | 521.23（793.73、4,447/0） | 1,244.77（1,768.26、1,695/0） | 1,290.33（1,874.63、1,608/0） | 621.63 |
| `refCreateChecked` | < 1,000 | 12.84（70.32、219,816/0） | 11.00（123.72、251,542/0） | 8.74（56.43、335,219/0） | 18.27 |
| `bizCreate` | < 1,000 | 7.33（93.47、435,940/0） | 12.24（121.95、246,091/0） | 8.08（98.29、401,668/0） | 10.80 |
| `bizUpdate` | < 1,000 | 5.28（45.01、638,102/0） | 7.82（118.90、372,633/0） | 5.56（31.21、603,894/0） | 7.39 |
| `bizDelete` | < 1,000 | 8.60（13.95、100/0） | 6.60（10.53、100/0） | 14.19（16.70、100/0） | 10.18 |
| `bizWritePoolLimit`（409 を合格） | < 1,000 | 8.10（32.14、400,990/0） | 9.35（36.72、336,357/0） | 8.21（55.44、394,616/0） | 12.81 |
| `bizCreateLarge`（記録だけ） | — | 532.85（587.14、30/0） | 851.33（947.38、30/0） | 488.01（556.70、30/0） | 624.72 |

- 線に最も近いのは MariaDB の `bizSearchContains`（2,613.94 ms、線 3,000 ms まで 386 ms）。前の Intent 261027 の 2,555.1 / 2,743.5 ms と同じ水準。
- `bizWritePoolLimit` の 409 の数は、アプリのログの `BUSINESS_REFERENCE_NOT_FOUND` の WARN の件数とちょうど一致した（PG 400,990・MySQL 336,357・MariaDB 394,616）。

### 5.4 プール

- 読み取りのプール `mastersmith-target-db`: 判定の回のすべての場面で、待ちの時間切れの累計は 0 → 0（増分 0）。
- 書き込みのプール `mastersmith-target-db-write`（上限 5）: `refCreateChecked` と書き込みの4場面のすべてで累計 0 → 0。
- 503 は 0 件（どの場面も `checks` の失敗 0、ログの WARN に 503 の code 0）。
- 借りる待ちの最大（`hikaricp.connections.acquire` の `MAX`、秒。場面の後に読んだ値で記録だけ）: 読み取りは `refPickerContains` が PG 0.596・MySQL 1.230・MariaDB 1.300 で最も大きく、ほかは 0.11 以下。書き込みは最大 0.113（MySQL）。借りる待ちの上限 3 秒には届いていない。

### 5.5 メモリ

アプリ（上限 2g = 2,147,483,648 B。線は `memory.peak` / `memory.max` < 0.9。作り直した直後から測った）:

| 場面 | PG 2回目 | MySQL | MariaDB 2回目 | PG 1回目（参考） |
|---|---|---|---|---|
| `bizSearchWide` の `memory.peak` | 944,607,232 B（**44.0%**） | 1,022,316,544 B（**47.6%**） | 984,743,936 B（**45.9%**） | 917,491,712 B（42.7%） |
| 同 `anon` の最大 | 882.0 MiB | 941.8 MiB | 919.9 MiB | 859.3 MiB |
| `bizCreateLarge` の `memory.peak` | 824,619,008 B（**38.4%**） | 913,846,272 B（**42.6%**） | 873,066,496 B（**40.7%**） | 728,936,448 B（33.9%） |
| 同 `anon` の最大 | 777.1 MiB | 847.9 MiB | 720.9 MiB | 675.2 MiB |

どれも `memory.events` の `max` 0・`oom_kill` 0。

対象DB（線は `anon` の最大 / `memory.max` ≤ 92%・`OOMKilled` false・`oom_kill` 0。回の全体の1秒ごとの見本）:

| 種類 | 上限 | `anon` の最大 | 比 | `memory.events` の `max` | OOMKilled・`oom_kill` |
|---|---|---|---|---|---|
| PostgreSQL 2回目 | 768 MiB | 119.1 MiB | **15.5%** | 16,753 | false・0 |
| MySQL 1回目 | 2,688 MiB | 2,428.9 MiB | **90.4%** | 6,683 | false・0 |
| MariaDB 2回目 | 1,408 MiB | 1,187.7 MiB | **84.4%** | 0 | false・0 |
| （参考）PostgreSQL 1回目 | 768 MiB | 119.5 MiB | 15.6% | 12,056 | **true・1**（暖めの psql。5.2） |
| （参考）MariaDB 1回目 | 1,408 MiB | 1,282.4 MiB（止まる直前） | 91.1% | — | **true**（暖めの最中に止まった。5.2） |

`memory.events` の `max` は上限で回収した回数で、OOM ではない。MySQL の 90.4% は線（92%）の内だが余裕は 1.6 ポイント。

### 5.6 `dslMixed`（既定の環境、`perf/dsl-mixed-memory.sh` で1回）

| 項目 | 値 | 線 | 判定 |
|---|---|---|---|
| `memory.peak` / `memory.max`（`judge`） | 1,949,310,976 / 2,147,483,648 = **0.9077** | < 0.95 | Met |
| 前の線（`judge_090`） | 0.9077 | < 0.90 | 並べて記録（not-under-0.9） |
| `anon` の最大 / `memory.max`（`anon_max_ratio`） | **0.6935** | ≤ 0.85 | Met |
| peak の時の内訳 | `anon` 1,489,207,296 B・`file` 436,973,568 B（回収できるページキャッシュ） | — | — |
| k6 | 終了コード 0（`checks` の失敗 0）、ログインの利用者の行 10・重なり 0・後置きの欠け 0 | — | — |
| 状態 | OOMKilled false・終了コード 0。WARN 2（起動の案内）・ERROR 0・OutOfMemoryError 0。`RETENTION_TIME` 45000（既定） | — | — |
| 窓 | k6 10:21:57Z〜10:24:58Z | — | — |

秘密の検索（`build/perf-results/bt-mu/dsl-k6` の全体）: 仮の管理者のパスワード 0・署名鍵 0・試験用の利用者のパスワード 0・`password` などと接続先の URL 0・`eyJ` 0。

### 5.7 前の Intent の値との比べ（`iteration_duration` の p95 ms）

| 場面 | 種類 | 今回 | 前の値（出どころ） |
|---|---|---|---|
| `bizSearchContains` | MySQL | 1,470.31 | 1,433.2 / 1,252.4（261027、上限 10） |
| `bizSearchContains` | MariaDB | 2,613.94 | 2,555.1 / 2,743.5（261027） |
| `bizSearchLast` | MySQL | 440.08 | 403.9 / 410.7（261027） |
| `bizSearchLast` | MariaDB | 1,200.36 | 1,399.1 / 1,392.9（261027） |
| `bizSearchLastSorted` | MySQL | 512.24 | 494.3 / 532.1（261027） |
| `bizSearchLastSorted` | MariaDB | 1,515.89 | 1,831.1 / 1,721.8（261027） |
| `bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail` | PostgreSQL | 7.64・65.45・615.93・76.75・218.38・64.11 | 8.68・65.78・674.14・86.93・237.18・91.37（261016 の1回目、上限 5） |
| `bizSearchFirst`・`bizDetail` | MySQL | 381.74・4.61 | 274.30・7.50（261016、上限 5） |
| `bizSearchFirst`・`bizDetail` | MariaDB | 273.55・3.96 | 649.70・10.45（261016、上限 5） |
| `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`・`refCreateChecked` | PostgreSQL | 14.42・11.73・69.05・66.60・521.23・12.84 | 11.34・9.46・82.41・84.31・496.26・14.77（261029 の1回目、上限 5） |
| 同上 | MySQL | 9.13・6.20・3.96・4.00・1,244.77・11.00 | 11.67・6.37・5.25・4.25・1,337.34・12.59（261029） |
| 同上 | MariaDB | 9.12・5.91・3.74・3.86・1,290.33・8.74 | 8.77・6.16・3.85・4.01・1,463.77・9.40（261029） |
| `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` | PostgreSQL | 7.33・5.28・8.60・8.10 | 6.95・4.66・15.87・8.37（261029） |
| 同上 | MySQL | 12.24・7.82・6.60・9.35 | 12.18・8.21・9.14・10.59（261029） |
| 同上 | MariaDB | 8.08・5.56・14.19・8.21 | 6.18・4.60・6.32・6.89（261029） |
| `bizSearchWide`・`bizCreateLarge` の `memory.peak` | PG・MySQL・MariaDB | 44.0%・47.6%・45.9% ／ 38.4%・42.6%・40.7% | 43.8%・44.5%・45.8% ／ 33.4%・44.5%・36.6%（261029 の1回目） |
| 対象DB の `anon` の最大 | PG・MySQL・MariaDB | 15.5%・90.4%・84.4% | 13.9%・75.2%・81.8%（261029 の1回目）、MySQL 71.54%・MariaDB 69.57%（261027、読み取りだけ） |
| `dslMixed` の `memory.peak` | — | 0.9077 | 0.9053 / 0.8862 / 0.8840（261027） |
| `dslMixed` の `anon_max_ratio` | — | 0.6935 | 0.6965（261027） |

- どの場面も前の値と同じ桁の内で、線を割った場面は無い。退行の判定ではなく並べた記録（流す順・回の数・表の状態が前の Intent と完全には同じでないため）。
- MySQL の対象DB の `anon` 90.4% は前の Intent（261029 の 75.2%・78.5%）より高い。最大は `bizCreateLarge` の終わりで、書き込みの場面の後。線（92%）の内。
- 業務データの読み取りの場面の上限が 261016（上限 5）と違う（今回は 10）ことに注意。

### 5.8 使い捨てのアプリのログ（片付けの前に数えた。件数と名前だけ）

| 回 | 利用者の名前 | 行の値 | `eyJ` | WARN | ERROR | OutOfMemoryError |
|---|---|---|---|---|---|---|
| PG 2回目 | 0 | 0 | 0 | 401,113（`BUSINESS_REFERENCE_NOT_FOUND` 400,990・`ROLE_NO_CHANGE` 113・起動の案内 10） | 0 | 0 |
| MySQL | 0 | 0 | 0 | 336,480（`BUSINESS_REFERENCE_NOT_FOUND` 336,357・`ROLE_NO_CHANGE` 113・起動の案内 10） | 0 | 0 |
| MariaDB 2回目 | 0 | 0 | 0 | 394,739（`BUSINESS_REFERENCE_NOT_FOUND` 394,616・`ROLE_NO_CHANGE` 113・起動の案内 10） | 0 | 0 |

- `BUSINESS_REFERENCE_NOT_FOUND` は `bizWritePoolLimit` の期待の答え（409）、`ROLE_NO_CHANGE` は setup が前の場面のロール・割り当てを受け入れる 409（前の Intent と同じ読み方）。起動の案内は Flyway の版・Bean の後処理（起動のたびに1組、5回の起動で 10 行）。
- k6 の出力（記録に置かない）: 試験用の利用者の名前 0・`eyJ` 0（どの回も）。
- 片付け: どの回も `down -v` と一時ディレクトリの削除の後、`mastersmith-perf_*` のボリューム 0・一時ディレクトリ 0・コンテナ 0 を確かめた。

### 5.9 記録（evidence/）に置いたもの

- 判定の回の参照の6場面の summary json: 3種類 × 6 = **18 ファイル**（`postgres-r2-*`・`mysql-r1-*`・`mariadb-r2-*`、約 79 KB）。`node perf/redact-k6-evidence.mjs <入力> <evidence の下>` の JSON の口でだけ置いた。
- 置いた後の確かめ: `--scan` は調べたファイル 18・検出 0・入力の誤り 0（終了コード 0）、`gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1` は終了コード 0（no leaks found）、`eyJ` を含むファイル 0。

## 6. 時間

| 回 | verify | E2E |
|---|---|---|
| ①〜⑤（Code Generation） | 27m39s・26m32s・25m55s・26m19s・26m31s | — |
| **この段（`45a5872a`）** | **27m34s** | **14.6 分** |

## 7. CI（この Intent のコミット、試行ごと）

`gh run list --workflow ci.yml` と `gh run view <run> --json attempt,jobs` で確かめた。時間は検査の job（`./gradlew verify`）の始めから終わり。

| コミット | run | 試行 | 結果 | 検査の job |
|---|---|---|---|---|
| `45a5872a` | 41383567811 | 1 | success | 29分32秒（04:04:56Z〜04:34:28Z） |
| `b7385263`（⑤） | 41383301492 | 1 | success | 36分17秒 |
| `5950d786`（④） | 41358192731 | 1 | success | 35分05秒 |
| `a29f6dbf`（③） | 41350580891 | 1 | success | 31分33秒 |
| `064be35a`（②） | 41329447669 | 1 | success | 34分26秒 |
| `6cbf5cfb`（①の記録。`3675b2f7`・`28dd694b` を含む push） | 41318603493 | 1 | success | 37分05秒 |

- `3675b2f7`・`28dd694b` は単独の run を持たず、まとめて push された先頭の `6cbf5cfb` の run で確かめた。
- すべて1回目で成功。再実行は無い。検査の job の最大 37分05秒は 42 分の線の内。
- CI の `actions/setup-java` が入れた Java は `25.0.5+8`（`45a5872a` の run のログ）で、Dockerfile の JRE `25.0.5_8` と同じ。Node は v26.13.0。
- Dependabot の PR #83・#84・#85 は 2026-11-01T21:11〜21:12Z に CLOSED（開いている Dependabot の PR は 0）。

## 8. 失敗と未確かめ

- コマンドの失敗: 最終の回には無い。途中の失敗は負荷の試験の1回目の PostgreSQL（対象DB の `oom_kill`）と MariaDB（対象DB の停止）で、どちらも暖めの道具が原因と確かめ、環境を作り直した2回目で通った（5.2）。
- この段の持ち主で確かめていない目標: 無い。
- 後の段が持ち主: FR11.1〜FR11.3（配備・片付け・`main` への取り込み）は Deployment Pipeline・Deployment Execution。

## Sources

- 実行の出力: `build/bt-verify.log`・`build/bt-e2e.log`・`build/bt-k6-inspect.txt`・`build/perf-results/bt-mu/`（git の対象外）
- `backend/build/reports/jacoco/test/jacocoTestReport.xml`・`frontend/coverage/coverage-summary.json`・`frontend/test-results/e2e-results.json`
- 前の値: `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/test-results.md`、`aidlc/spaces/default/intents/261027-perf-targets-review/construction/build-and-test/evidence/remeasure-results.md`、`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/performance-validation/test-results.md`・`construction/build-and-test/test-results.md`
- `gh run list`・`gh run view`・`gh pr list` の出力

## Assumptions & Open Questions

- [assumption] PostgreSQL の1回目の `oom_kill 1` は、場面の間の `anon` の小ささと再現の結果から、暖めの psql の強制終了と読んだ（1回目の対象DB のログは片付けで消えており、その回の中では確かめていない）。
- [assumption] 1回目の暖めの形（`SELECT *` を道具で受ける）は、前の Intent 261027 の MySQL・MariaDB の暖めと同じ形で、そのときは OOM が出ていない。今回 MariaDB が止まった差（版・ダイジェストの組み直し・表の状態など）は調べていない。
