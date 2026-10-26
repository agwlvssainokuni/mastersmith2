# テストの結果（261025-deps-perf-carryover、前半）

**Collaborator:** aidlc-quality-agent

`develop` の先頭 `5bebcab`（B1〜B4 統合済み）で実測した前半の結果。負荷の試験は次の依頼で流し、ここでは「負荷の試験（次の依頼）」と書く。コードは変えていない。値（資格情報・接続先・行の値・メールアドレス）は記録していない。Target Verification Matrix の全行は `build-and-test-summary.md` 4節。

## 1. ビルドの状態

| 項目 | 結果 |
|---|---|
| コマンド | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した。PATH に `$HOME/.volta/bin`） |
| 結果 | BUILD SUCCESSFUL in 22m 5s（40 タスク中 20 実行、20 up-to-date） |
| 時間 | 1,325 秒。CI の上限 60 分の 36.8%、その 7 割（約 42 分）の 52.6%。42 分の線は超えていない |
| 眠りの確かめ | 窓 08:32〜08:54（JST）。`pmset -g log` の `Sleep`・`DarkWake`・`Wake` の最後の記録は 08:15:13・08:26:36 で窓の前。窓の中に無い。実行の前に `PreventUserIdleSystemSleep` が 1 |
| 作業ツリー | `aidlc/` の外に変更なし（`git status`） |

## 2. テストの件数

| 種類 | 総数 | 失敗 | エラー | SKIPPED |
|---|---|---|---|---|
| バックエンドの単体（`XxxTest`・構造の検査） | 2,612 | 0 | 0 | 0 |
| バックエンドの結合（`XxxIT`、対象DB 3 種類のコンテナ） | 1,375 | 0 | 0 | 10 |
| 画面（Vitest） | 1,977（226 ファイル） | 0 | — | — |
| E2E（Playwright 1.64.1） | 256 | 0（unexpected 0） | — | 0（flaky 0） |

- 結合の SKIPPED 10 は B3・B4 の実測（10）と同じ件数で、他の DB 種別向けの `assumeTrue`（colima の 2 変数を渡して対象DB のテストは実行された）。
- 単体・結合は `backend/build/test-results/{test,integrationTest}/*.xml` の `tests`・`failures`・`errors`・`skipped` の合計。前の B3・B4 の件数（2,612・1,375・1,977）と一致した。
- 個別の確かめ（すべて失敗 0）: `ColumnValueCodecTest` 48、`JdbcTargetReadAccessTest` 11、`BusinessDataTimeoutIT` 9、`BusinessDataWriteTimeoutIT` 4、`BusinessDataUnknownNamesIT` 6、`NavigationApiIT` 7、`BusinessDataBoundaryArchitectureTest` 8、`TargetDbPropertiesTest` 7、`TargetDbStartupIT` 7、`*ConnectionUsageIT` 計 20、`*SecretLeakIT` 計 42、`OtlpLogExportIT` 4、`ExternalExportIT` 1。

## 3. カバレッジ

- バックエンド（`jacocoTestReport.xml`）: 全体 **行 97.7%・分岐 91.4%**。71 パッケージのうち下限未満は `common.health`（行 79.2%・分岐 100%）だけで、`packagesJudgedByTotal` の 7 パッケージの 1 つ（全体の合計で判定。この Intent は手を入れていない）。手が入ったパッケージ（行/分岐）: `targetdb.config` 100.0/99.1、`targetdb.domain` 100.0/97.2、`targetdb.service` 89.1/75.4（最小）、`dslmanage.generate` 99.4/100.0、`businessdata.repository` 96.4/89.0、`businessdata.domain` 97.7/90.2、`businessdata.web` 97.6/77.9、`navigation.service` 100/100、`navigation.domain` 100/92.6、`navigation.web` 92.0/100。除外は増やしていない。
- 画面（`frontend/coverage/coverage-summary.json`）: 全体 **行 95.06%・分岐 89.82%**（文 94.91%・関数 96.22%）。B4 の記録（分岐 89.77%）との差は丸めと測りの揺れの範囲。

## 4. 依存関係の脆弱性（`osvScan --rerun`）とリリースの前の確かめ（FR3.10）

- `./gradlew osvScan --rerun`: BUILD SUCCESSFUL in 3s。`OSV-Scanner: 失敗の条件に当たるもの 0 件、警告 12 件`（開発用の依存: `frontend/package-lock.json` と `vendor/make-you-chic-ui/package-lock.json` の `fast-uri` 3.1.8・`zod`・`@vitest/mocker`）。B4 の記録（警告 12 件）と同じ。
- `grep -rIl 'axe-core\|axe\.min' frontend/dist` → 出力なし（終わりの値 1）。`unzip -l backend/build/libs/mastersmith.war | grep -i axe` → 出力なし（終わりの値 1）。WAR の SHA-256 は 80b19c53…（`verify` が作り直したもの）。

## 5. E2E の全件（FR1.4・FR4.7）

- 前提: Mailpit は動作中。見本の PostgreSQL を `docker/e2e/compose.targetdb-port.yaml` の上乗せで 127.0.0.1:15432 に開けた（作り直し。配備したアプリは止めない）。終わった後、上乗せ無しで作り直してポートを閉じた（`5432/tcp` のみ）。
- 結果: `BUILD SUCCESSFUL in 13m 11s`（792 秒）。`expected 256`・`unexpected 0`・`flaky 0`・`skipped 0`。窓 08:56〜09:09 に `Sleep`・`DarkWake` なし。
- **報告 json の秘密の確かめ**（文字列検索の件数。値は表示していない）: `jdbc:postgresql`・`jdbc:mysql`・`jdbc:mariadb`・`mastersmith_e2e_`・`MASTERSMITH_SAMPLE_TARGETDB`・`:15432`・`Bearer ey`・`eyJhbGci` は各 0 件。`MASTERSMITH_TARGET_DB` は 4 件で、報告の部品の確かめ対象の「名前」と「kind」の行だけ（値なし）。報告の部品（`playwright-secret-check-reporter.ts`）も通った（BUILD SUCCESSFUL）。
- 落ちたものは無く、切り分け（Playwright 1.63.0 に戻す → B2 の他の npm → B3 の C5）は不要だった。

### 5.1 画面の時間（U3-NFR1.7a〜e、E2E 185、各 5 回の中央値）

| ID | 判定 | 線（中央値） | 今回の中央値（最大） | 前回（B6） | 判定 |
|---|---|---|---|---|---|
| U3-NFR1.7a | `listShown100` | 1.0 秒 | 69.1 ms（107.1） | 70.1 ms | Met |
| U3-NFR1.7b | `listShown20` | 0.5 秒 | 48.3 ms（74.0） | 50.1 ms | Met |
| U3-NFR1.7c | `detailFocused` | 0.5 秒 | 62.4 ms（63.4） | 45.2 ms | Met |
| U3-NFR1.7d | `formShown100` | 0.5 秒 | 50.9 ms（52.8） | 32.8 ms | Met |
| U3-NFR1.7e | `listShown100Back` | 1.0 秒 | 179.9 ms（197.1） | 179.0 ms | Met |

- 5 回すべて測れた（`note` なし）。「塗りまで」の中央値: 7a 184.2・7b 71.9・7c 78.4・7d 64.3・7e 226.8 ms（参考。判定に使わない）。
- 7c・7d は前回より約 17〜18 ms 長いが、線（500 ms）の約 10〜12%。今回は B2 の依存の更新（Playwright・vitest・jsdom など）と B3 の画面の変更の後で、原因の切り分けは行っていない（線は満たしているため、見立てなし）。

## 6. 単体・結合テストの JVM のヒープ（P7、FR4.8）

`verify` の出力とテストの XML に `OutOfMemoryError`・`GC overhead` は 0 件（`grep -c` 0、`backend/build/test-results/` の検索で該当ファイル 0）。単体（1g）・結合（1536m）とも尽きていない。境界テストの読み込みを共有する直しは不要。

## 7. 各目標の判定（前半の時点。最終は `build-and-test-summary.md` 4節と 5節）

| 判定 | 件数 | 目標 |
|---|---|---|
| Met | 18 | BT-VERIFY・BT-VERIFY-TIME・BT-COV-BE・BT-COV-FE・NFR2-OSV・FR3.10・FR1.4-E2E・FR2.3-APPENDER・FR3.1-3.7-TESTS・FR4.4-CONNUSAGE・NFR2-LEAK・FR4.8-P7・U3-NFR1.7a〜e・FR3.4-COMPACT |
| Not Met | 0 | （前半では無し） |
| Unverified | 1 | FR5.1-5.3（Deployment Execution が持つ） |
| Pending（途中） | 24 | 負荷の試験（次の依頼）: U1-NFR1.1・1.3・3.2・2.4・2.4-LIMIT・2.1・2.5b、U2-NFR1.4・1.5・3.4・2.7・2.8・2.9、FR4.2-OOM・FR4.2-ANON、FR4.5-PEAK・FR4.5-RET0、FR4.6-SEED、FR4.8-P5・P6、FR3.8、NFR1-NOLOOSEN。依頼者の push の後: FR1.1-PR・BT-CI |

- 負荷の試験の行は、次の依頼で Met・Not Met・Unverified のいずれかにし、`Pending` を残さない。前半では Not Met の目標を緩めていない。

## 8. 気づいた点・懸念

- 画面の時間 7c・7d は前回より長い（上記）。線は満たす。
- 見本の PostgreSQL を E2E のために作り直したため、配備したアプリの業務データの読み取りが数秒失敗した可能性がある（README の注意どおり。確かめていない）。作り直し後のコンテナは起動しているが、`healthy` の表示は `docker ps` の `Up` のみ（見本の PG にヘルスチェックの表示は無い）。
- `verify` は 1 回目で通ったため、不安定なテストの扱い（`team.md`）は発動していない。CI の結果は依頼者の push の後に試行ごとに確かめる。
- 負荷の試験を流す前の確認事項（次の依頼）: (1) k6 は手元に無く、`perf/versions.env` の `K6_IMAGE` を `docker run` で使う。(2) 試験で使う WAR は、`verify` が作り直した今の WAR（SHA-256 80b19c53…）を Dockerfile で写して作る。配備したイメージ（`mastersmith:local`）の WAR とは異なる見込み（配備は FR5.2 で `main` の先頭から作り直す）。(3) `RETENTION_TIME=0` の諮りと P6 の `navMenu` への替えは、承認の場で扱う。

## 9. 負荷の試験（後半）

`mastersmith:carryover`（WAR の SHA-256 80b19c53…、イメージ ID 009a757de120…。配備した `mastersmith:local`（WAR 6426111d…）は変えていない）を、配備したアプリ（`mastersmith-app-1`）を `2026-10-26T00:20:49Z` に止めて使い捨ての環境で流す。読み取りのプールは既定の **10**（`MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` を渡さない既定で動かし、`hikaricp.connections.max` が 10 であることを MariaDB の回で確かめた）。元の数値の表は `evidence/<種類>-scenarios.tsv`（`iteration_duration{scenario:…}` の p95・最大・件数、`checks` の失敗、非期待の応答、プールの待ちの時間切れの増分、対象DB の `anon` の最大）。値（資格情報・接続先・行の値）は記録していない。

### 9.1 PostgreSQL（`shared_buffers` 256MB・メモリ 768m。2B' 確認: 行 100,000・目印の行 909・最長 2,000,000 字、`biz_pair` 1,000・`biz_wide` 200・`biz_ref` 100）

- 表の大きさ（データ + 索引）212,566,016 B = 共有バッファーの 79.2%（80% 以内）。
- 読み取り（10 VU・3 分、合否の回 2 回）: `iteration_duration` の p95 は `bizDefinition` 6.4・6.1、`bizSearchFirst` 53.2・54.7、`bizSearchContains` 543.0・552.1、`bizSearchLast` 68.6・71.3、`bizSearchLastSorted` 201.2・214.2、`bizDetail` 78.1・62.1 ms（線 1000 ms）。`checks` の失敗 0、読み取りのプール（`pool="mastersmith-target-db"`）の待ちの時間切れの増分 0、503 は 0（`checks` が 200 を要求し失敗 0）。
- 上限に届く形（`bizSearchContains` 12 VU・3 分）: p95 743.7 ms・最大 1,367.3 ms、待ちの最大 310.6 ms、時間切れの増分 0、`checks` の失敗 0。（1 回目は 10 人の利用者では 12 VU を賄えず rc=107 で止まったため、利用者 `perf-biz-11`・`12` を足して `bizPrepare` を流し直し、書き込みの場面の後に流した。流す順が読み取りの先という決まりとの差。`biz_item` は書き込みの場面で変わらない）。
- `bizSearchWide`: アプリの `memory.peak` 927.7・909.6 MiB（上限 2g の 45.3%・44.4%）、アプリの `anon` の最大 906.1・888.8 MiB。
- 書き込み（10 VU・3 分、2 回）: p95 は `bizCreate` 7.5・7.6、`bizUpdate` 5.2・5.3、`bizDelete` 20.9・7.6、`bizWritePoolLimit` 8.4・9.3 ms。`checks` の失敗 0、書き込みのプールの時間切れの増分 0。`bizCreateLarge`: p95 503.6・571.9 ms（記録だけ）、アプリの `memory.peak` 647.1・662.7 MiB（30.8%・32.4%）、`anon` の最大 626.0・650.6 MiB。
- 対象DB: OOMKilled false、`oom_kill` 0（全場面）。`anon` の最大 111.8 MiB（上限 768 MiB の 14.6%、85% の線の内）。
- 監査（`BUSINESS_%`）: 作成成功 899,599・作成失敗（参照先なし）768,757・更新 1,337,801・削除 200（合計 3,006,836 行、内部DB のファイル 324,321,280 B）。WARN は参照先なし（409）の想定内のもの 768,757 行と `ROLE_NO_CHANGE` 115 行、起動ごとの 2 行。ERROR 0、`OutOfMemoryError` 0、ログに利用者のメールアドレスと行の値 0 件。（最後の `bizCreateLarge` の 2 回目のアプリのログは、`perfu up --force-recreate` 前に保存し損ねたため無い。他の JVM のログは全て数えた。）

### 9.2 MariaDB（バッファープール 896M・メモリ 1408m。`--default-character-set=utf8mb4` で種を入れ、2B' 確認: 行 100,000・目印の行 909・最長 2,000,000 字）

- 表の大きさ（データ + 索引）747,274,240 B = プールの 79.5%（80% 以内）。実際のプール 939,524,096 B、コンテナの上限 1,476,395,008 B（1408 MiB）。
- 読み取り（10 VU・3 分、2 回）: p95 は `bizSearchFirst` 327.2・318.7、`bizSearchContains` **2,443.0・2,508.3**、`bizSearchLast` **1,191.2・1,191.3**、`bizSearchLastSorted` **1,549.6・1,535.3**、`bizDetail` 4.7・4.2 ms（線 1000 ms）。太字の 3 場面は線を超えた（k6 の終了コード 99）。`checks` の失敗 0、読み取りのプールの待ちの時間切れの増分 0（全場面）、503 は 0（前回は `bizSearchContains` で 503 が 1・2 件、時間切れ 1・2）。前回 p95 は `bizSearchContains` 4,698.2・4,708.5、`bizSearchLast` 2,026.8・2,417.1、`bizSearchLastSorted` 2,964.3・2,212.3、`bizSearchFirst` 650・603 ms。
- 上限に届く形（`bizSearchContains` 12 VU・3 分、`BIZ_USER_COUNT=12`）: p95 3,249.7 ms・最大 4,341.0 ms、プールの待ちの最大 1,296.0 ms、時間切れの増分 0、503 0、`checks` の失敗 0（k6 の終了コード 99 は p95 の線）。
- `bizSearchWide`: アプリの `memory.peak` 952.6・946.7 MiB（44.4%・44.1%）、アプリの `anon` の最大 929.3・927.7 MiB。
- 書き込み（10 VU・3 分、2 回）: p95 は `bizCreate` 8.5・7.8、`bizUpdate` 5.3・5.7、`bizDelete` 7.6・4.0、`bizWritePoolLimit` 8.3・8.8 ms。`checks` の失敗 0、書き込みのプールの時間切れの増分 0。`bizCreateLarge`: p95 744.7・662.8 ms（記録だけ）、アプリの `memory.peak` 724.9・878.5 MiB（33.8%・40.9%）、`anon` の最大 660.1・808.9 MiB。
- 対象DB: OOMKilled false、`oom_kill` 0（全場面）。`anon` の最大 **1,234.3 MiB（上限 1,408 MiB の 87.7%）**。85% の線を超えた（`bizCreateLarge` 2 回目。前回 1,197.0 MiB = 85.01%）。合格の条件（OOMKilled false かつ `oom_kill` 0）では Met。超えた値は依頼者に諮る。
- 監査: 作成成功 819,407・作成失敗（参照先なし）772,933・更新 1,237,026・削除 200（合計 2,829,991 行、内部DB 305,565,696 B）。WARN は参照先なし（409）の想定内のもの約 772,900 行・`ROLE_NO_CHANGE` 数十行・起動ごとの 2 行。ERROR 0、`OutOfMemoryError` 0、ログに利用者のメールアドレスと行の値 0 件。

### 9.3 MySQL（バッファープール 1280M・メモリ 2688m。`--default-character-set=utf8mb4` で種を入れ、2B' 確認: 行 100,000・目印の行 909・最長 2,000,000 字）

- 表の大きさ（データ + 索引）1,017,806,848 B = プール 1,342,177,280 B の 75.8%。コンテナの上限 2,818,572,288 B（2688 MiB）。
- **無効にした回（PC の眠り）**: `bizSearchLast` の 1 回目（`2026-10-26T02:45:41Z` から）の間に、PC がバッテリー 1% で低電力スリープに入り（`pmset -g log`: 11:47:53 JST に Low Power Sleep、12:59:01 JST に Hibernate から復帰。電源に接続後）、約 74 分止まった（k6 の要求が 149,217 件失敗、アプリのログに `HikariPool` の「clock leap detected（45 秒）」）。この 1 回は使わない。ほかの回はこの窓の外（`pmset` の `Sleep`・`DarkWake` なし）。無効の回は、同じ MySQL を作り直して種を入れ直し（目印の行 909・最長 2,000,000 字、表 1,017,806,848 B）、`bizPrepare` の後に `bizSearchLast` だけを 2 回流し直した（`rR1`・`rR2`、読み取りの後で書き込みの前の状態）。
- 読み取り（10 VU・3 分、2 回）: p95 は `bizSearchFirst` 315.4・317.8、`bizSearchContains` **1,786.8・1,848.0**、`bizSearchLast` 627.5（元の 2 回目）・878.8・486.7（作り直し後の 2 回）、`bizSearchLastSorted` 534.8・501.2、`bizDetail` 6.1・5.7 ms（線 1000 ms）。`bizSearchContains` だけが線を超えた。`checks` の失敗 0、読み取りのプールの待ちの時間切れの増分 0、503 は 0（前回は `bizSearchContains` で時間切れ 37・503 が 37 件）。前回 p95 は `bizSearchContains` 1,627.0・5,513.4、`bizSearchFirst`・`bizSearchLast` は 3 回目で 302.1・733.9 ms。
- 上限に届く形（`bizSearchContains` 12 VU・3 分）: p95 2,264.6 ms・最大 3,039.0 ms、プールの待ちの最大 1,092.8 ms、時間切れの増分 0、503 0、`checks` の失敗 0。
- `bizSearchWide`: アプリの `memory.peak` 933.5・902.5 MiB（45.6%・44.1%）、`anon` の最大 915.0・892.3 MiB。
- 書き込み（10 VU・3 分、2 回）: p95 は `bizCreate` 17.2・15.8、`bizUpdate` 10.5・10.5、`bizDelete` 15.0・6.1、`bizWritePoolLimit` 13.4・11.4 ms。`checks` の失敗 0、書き込みのプールの時間切れの増分 0。`bizCreateLarge`: p95 1,145.5・1,033.2 ms（記録だけ）、アプリの `memory.peak` 906.2・916.1 MiB（44.2%・44.7%）、`anon` の最大 887.5・861.4 MiB。
- 対象DB: OOMKilled false、`oom_kill` 0（全場面）。`anon` の最大 **2,454.1 MiB（上限 2,688 MiB の 91.3%）**。85% の線を超えた（`bizCreateLarge` 2 回目。前回 2,426.9 MiB = 90.3%）。合格の条件（OOMKilled false かつ `oom_kill` 0）では Met。超えた値は依頼者に諮る。
- 監査: 作成成功 379,296・作成失敗（参照先なし）518,266・更新 563,839・削除 200（合計 1,462,026 行、内部DB 158,216,192 B）。WARN は参照先なし（409）の想定内のもの 518,266 行・`ROLE_NO_CHANGE` 117 行・起動ごとの 2 行・眠りの `HikariPool` 1 行。ERROR 0、`OutOfMemoryError` 0、ログに利用者のメールアドレスと行の値 0 件。
- 監査の行 1 行あたり: PostgreSQL 107.9 B・MariaDB 108.0 B・MySQL 108.2 B（内部DB のファイル ÷ 全行。見積もり 約 0.5 KB より小さく、前回 約 108 B と同じ）。

### 9.4 `dslMixed` の `memory.peak`（FR4.5。使い捨てのアプリ 2g・最大ヒープ 50%、10 VU・3 分、10 MiB の DSL。回ごとにアプリのコンテナを作り直して前の最大を消した）

| 回 | `memory.peak` ÷ 2g | 判定（< 0.90） | `anon` の最大 ÷ 2g | peak の時の `file`（ページキャッシュ） | 内部DB のファイルの伸び | WARN/ERROR/OOM |
|---|---|---|---|---|---|---|
| 既定 1（`default-1`） | **0.9099** | 超過 | 0.6861 | 457.0 MB | 456.3 MB | 2/0/0 |
| 既定 2（`default-2`） | 0.8774 | 満たす | 0.6905 | 305.7 MB | 383.2 MB | 2/0/0 |
| 既定 3（`default-3`、計画に無い追加） | **0.9010** | 超過 | 0.69 | 339.6 MB | 427.1 MB | 2/0/0 |
| 試し 1（`retention-1`、`RETENTION_TIME=0`） | 0.7084 | 満たす | 0.6804 | 35.0 MB（peak の時）・最大 64.5 MB | 64.4 MB | 2/0/0 |
| 試し 2（`retention-2`） | 0.7194 | 満たす | 0.6911 | 35.9 MB（peak の時）・最大 78.5 MB | 78.4 MB | 2/0/0 |

- 前回: 既定 0.8731・0.8696、試し 0.7400・0.7175。`RETENTION_TIME` は既定の回 45000、試しの回 0 を、アプリを止めた後に内部DB を読み取りで開いて確認した（`settings.txt`）。
- **FR4.5-PEAK は Not Met**（計画の 2 回のうち 1 回が 0.90 以上。追加の 3 回目も超過）。物差しは `memory.peak` のまま（`anon` に替えていない）。`anon` の最大は 0.686〜0.691 で前回（0.690〜0.699）と同じで、peak を押し上げたのは回収できるページキャッシュの量（内部DB のファイルの伸びが前回の約 371〜381 MB に対し、今回の 456・427 MB の回で大きい）。原因の確定はしていない（見立て。環境が dsl-timing の準備の後で内部DB のファイルが前回より膨らんだ状態だったことに依る可能性）。
- 秘密の検索（今回の `OUT_DIR` 対象）: 署名鍵・仮の管理者のパスワード・試験用の利用者のパスワード・`jdbc:`・password の語は各 0 件。
- 注: 前回の既定の回の記録が `build/perf-results/dsl-k6/memory/` に残っていたため、`dsl-k6-memory-prev-intent` に移してから流した（ファイルの移動のみ）。

### 9.5 `RETENTION_TIME=0` を配備物に入れるかの材料（決めない）

- 測定結果: `memory.peak` ÷ 2g は 0.7084・0.7194（既定の回 0.8774〜0.9099 より 0.16〜0.20 低い）。理由は、内部DB のファイルの伸び（約 64〜78 MB、既定の回は約 383〜456 MB）とページキャッシュ（`file`）が小さくなること。`anon` は変わらない。
- 壊れうる点（見立て。**この段では書き込みの途中で止める確かめは行っていない**）: `RETENTION_TIME` は H2（MVStore）が、書き込みの後に古い版（チャンク）をどれだけの間残すかの設定で、0 にすると古い版をすぐ解放して再利用する。書き込みの途中でプロセスが止まったり電源が落ちたりしたとき、H2 は直前の確定した版へ戻ることで復旧するが、古い版が早く再利用されると戻れる版が少なくなり、ファイルが壊れる危険が増える（H2 は復旧できないとき起動に失敗しうる）。この Intent の配備はイメージだけの戻しで内部DB の複写を取る手順があるが、書き込みの途中で止まったときの影響は測っていない。配備物に入れるなら、止める確かめ（`kill -9`・電源断の模擬）を先に行うこと、効果（ページキャッシュを減らす）と引き換えの危険を依頼者が判断することが要る。前回（261016）と同じ材料で、今回の値も同じ向き。

### 9.6 `dslRoleOverlap`（P5）と `navMenu`（P6）

使い捨ての環境（対象DB なし、アプリ 2g）で、`rolePrepare` → `dslRoleOverlap` ×2 → `navPrepare` → `navMenu` ×2（`DSL_FILE` は 10 MiB の DSL、`ROLE_YAML_FILE` は上限ちょうど 5,242,880 バイトの権限の YAML）。

- `dslRoleOverlap`（60 秒）: 1 回目 `checks{dslHeavy}` 94/94・`checks{roleChecks}` 50/50、`iteration_duration{scenario:roleChecks}` の最大 4,167.3 ms（中央値 1,113.5 ms）、2 回目 58/58・30/30、最大 2,703.3 ms（中央値 2,126.6 ms）。線 < 15 秒（`checks` の率 1）を満たす。重なる間の失敗した要求 0（`http_req_failed` 0）。アプリのログは WARN 4（起動の 2 行×2 回の起動）・ERROR 0・`OutOfMemoryError` 0、試験用の利用者名 0 件。アプリの OOMKilled false・`oom_kill` 0（`memory.peak` 1.96〜2.00 GB = 2g の 91〜93%、重い DSL とヒープの影響。参考）。
- `navMenu`（10 VU・3 分）: p95 187.1 ms・159.3 ms（最大 343.1・209.3 ms、14,475・16,082 回、`checks` 100%）。前回の置き場の p95（`261004-role-menu`）は `navMenu` 181.1 ms で同じ水準。`tableAccess*`（35.1・31.1 ms）は B3 で道を消したため測り直せない。
- **締める案の提案（目標の数値は変えていない）**: 線は 1,000 ms のまま、実測の p95 は 159〜187 ms（線の約 1/5〜1/6）、最大でも 343 ms。締めるなら (a) `navMenu` の p95 を 500 ms 未満（実測の約 2.7 倍の余裕）とし、判定は合否の回 2 回の両方で満たすこと、(b) 同じ権限の解決を通る `myPermissionsTree`・`workRoleSwitch` の場面も同じ線にそろえること、(c) 置き場の問い合わせの目標（「約 30 分の 1」）は道を消したため廃止し、新しい線は `navMenu` に統一すること。締めるかは依頼者が要件で明示に決めたときだけ（`team.md`）。

### 9.7 手元の監視の式の値（FR3.8、`monitorLight`）

- lgtm（`grafana/otel-lgtm:0.37.0@sha256:57b5ab1c…`、メモリ 1536m、ポート 127.0.0.1:13000）を使い捨ての環境の網につないだ後にアプリを起動した（先に起動したときのログ送信の ERROR を避ける）。アプリは外部エクスポート有効、対象DB は PostgreSQL（`bizPrepare` 済み）。
- `monitorLight`（1 VU・5 分・`MONITOR_BIZ=1`、繰り返しごとに 1〜2 秒の待ち）: `checks` 201/201（率 1）。lgtm は止まらず（再起動 0・OOMKilled false、使用メモリ 604 MiB/1.5 GiB、ログに out of memory/killed 0 件）。アプリの WARN 2（起動）・ERROR 0・利用者名 0 件。
- 式の値: 警報の決まり 17 件の式と画面（ダッシュボード）の 37 件の式の計 54 件を Prometheus/Loki に問い合わせ、すべて誤りなく返った（`error` 0）。27 件で値が返り（5xx の割合・ERROR のログ・ログイン・確認用 API・コネクションプールの待ち・JVM のヒープ・403・API ごとの要求の数と時間・業務データの 503/409・対象DB の読み取りのプール 3 式）、27 件は空（ロック・書き込みの失敗・正規化されていないパスの拒否・送り元の不一致・更新の API・メール・書き込みのプール・操作ごとの時間など、この場面が起こさない出来事の式）。一覧は `evidence/monitor/monitor-eval.tsv`。
- lgtm のイメージ（約 3.4 GB）は、この段で `docker pull` した（以前は 0.34.0 と別のダイジェストだけがあった）。

### 9.8 まとめ・後片付け・依頼者に諮ること

- **配備したアプリを止めた時間**: `2026-10-26T00:20:49Z` に停止、`05:41` 頃に `docker compose start app`、`05:42:41Z` に healthy を確認（`mastersmith:local`、イメージ ID `4cec01e79026…` のまま、`/actuator/health` が UP）。約 5 時間 21 分（見込み 4〜5 時間より約 20 分長い。PC の眠りの 74 分の間も止まっていた時間に入る）。
- **PC の眠り**: MySQL の 2 場面目（`bizSearchLast` の 1 回目）の最中に、PC が**バッテリー 1% で低電力スリープに入り**（`11:47:53 JST`）、**AC 電源を接続した `12:59:01 JST` に復帰**した。この回は無効にして流し直した。それ以前は電池駆動（残量 86%→1%）で長い試験を流していた。以後は AC 電源のままで、窓の中に `Sleep`・`DarkWake` は無かった（`pmset -g log`）。
- **後片付け**: 使い捨ての環境（`mastersmith-perf` のアプリ・対象DB 3 種類・lgtm・ボリューム）はすべて、結果を確かめた後に消した（`mastersmith-perf_` のボリューム 0）。一時ディレクトリ（`~/.mastersmith-perf-*`、資格情報の環境ファイルを含む）は削除した。試験用イメージ `mastersmith:carryover`（ID `009a757de120…`）と `mastersmith:local` は残した。配備した見本の PostgreSQL（`mastersmith-targetdb-postgres-1`）と Mailpit は触っていない。`.env` は開いていない。
- **依頼者に諮ること**:
  1. 対象DB の `anon` の最大が 85% の線を超えた（MariaDB 87.7%・MySQL 91.3%。前回 85.01%・90.3%）。OOMKilled false・`oom_kill` 0 のため合格の条件で Met とし、プール 10 は採れる。余裕（メモリの上限）を上げるか。
  2. `RETENTION_TIME=0` を配備物に入れるか（9.4・9.5。入れるなら先に書き込みの途中で止める確かめを行う）。
  3. `dslMixed` の `memory.peak` が Not Met（0.9099・0.8774・0.9010）になった扱い（前回は Met。回収できるページキャッシュと内部DB のファイルの伸びに依る。物差しは変えていない）。
  4. Not Met 4 件（MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`、MySQL の `bizSearchContains`）。プール 10 で時間切れ・503 は 0 になった。p95 は MariaDB の部分一致 2.4〜2.5 秒と深い OFFSET 1.19 秒、MySQL の部分一致 1.8 秒が線を超える（部分一致は索引が効かず、10 VU が CPU 4 を取り合う）。直し方（件数を「多数」と出す・索引の案内・VU/CPU の見直しなど）は後の Intent。
  5. P6 の締める案（9.6）。
  6. 依頼者の push の後に出た新しい Dependabot の PR 3 件（#76 mailpit v1.32.0・#77 vite 8.3.5・#78 react-router 8.5.0）の扱い。
- 気づいた点: (a) `bizSearchContains` の 12 VU は、`perf-biz` の利用者が 10 人では賄えず（`VUS` が `BIZ_USER_COUNT` 以下という台本の確かめ）、README の手順 3B は利用者を 10 人しか入れない。12 VU の場面を流すには 12 人と `-e BIZ_USER_COUNT=12` が要る（README の直しの候補）。(b) PostgreSQL の書き込みの後に流した 12 VU の回は、読み取りの先という決まりとの差（`biz_item` は書き込みで変わらないため、読み取りの値への影響は無い見立て）。(c) PostgreSQL の最後の `bizCreateLarge` の 2 回目のアプリのログを保存し損ねた（その他の JVM のログは全て数えた）。

## 10. 後半の後の依頼者の決定と B5（Dependabot の新しい 3 件）

### 10.1 依頼者の決定（負荷の試験の結果を受けて）

| 論点 | 決定 | 記録 |
|---|---|---|
| 対象DB の `anon` が 85% の線を超えた（MariaDB 87.7%・MySQL 91.3%）、`dslMixed` の `memory.peak` が Not Met | 線を緩めず持ち越す。`anon` は合格の条件（OOMKilled false かつ `oom_kill` 0）で Met、`memory.peak` は Not Met のまま。上限の引き上げと原因の確定は次の Intent | 4 節の Matrix の FR4.2-ANON・U2-NFR2.8・FR4.5-PEAK |
| `RETENTION_TIME=0` を配備物に入れるか | 入れない。書き込みの途中で止める確かめを先に行ってから、次の Intent で決める | FR4.5-RET0（記録の目標）。9.5 |
| P6 の締める案 | 提案のまま、次の Intent で決める。目標の数値は変えない | FR4.8-P6。9.6 |
| Dependabot の新しい 3 件（#76〜#78） | この Intent で取り込む（B5） | 10.2 |

### 10.2 B5: 新しい Dependabot の更新 3 件の取り込み

- 内容: `axllent/mailpit` v1.31.6 → v1.32.0（index のダイジェスト `sha256:0790692b…` で固定。`compose.yaml`・`docker/perf/compose.yaml`・`README.md` を同じ版にそろえ、`.github/dependabot.yml` のコメントを更新）、`vite` 8.3.4 → 8.3.5、`react-router` 8.4.0 → 8.5.0（`frontend/package.json`・`package-lock.json`。`hasInstallScript` の項目は増えていない）。作業ブランチ `fix/261025-deps-perf-carryover-b5` から `develop` へ squash で統合（`9bf6b70`、依頼者のコミットの承認あり）。
- `:backend:cleanTest :backend:cleanIntegrationTest verify osvScan --rerun`: BUILD SUCCESSFUL in 23m 6s。`osvScan` は失敗の条件に当たるもの 0 件（警告は開発用の依存 `fast-uri` など）。SKIPPED は他の DB 種別向けの 10 件のまま。実行の窓に `Sleep`・`DarkWake` なし。
- E2E の全件: **256 件通過**（12.9 分、BUILD SUCCESSFUL in 12m 59s）。報告 json に `jdbc:`・password の語なし。
  - 1 回目の起動は 2 秒で失敗した。見本の PostgreSQL が 127.0.0.1:15432 を開いていなかったため（前の依頼の後、上乗せ無しで作り直されていた）。依頼者の了承を得て、`docker/e2e/compose.targetdb-port.yaml` の上乗せで見本の PG を作り直し（ボリュームは引き継ぎ。配備したアプリは healthy のまま）、流し直して通った。テストの失敗ではない。
  - 見本の PG は、上乗せのポート公開付きのまま残っている（127.0.0.1:15432）。
- 範囲: vite・react-router の更新は画面の本体に触れるため E2E を流した（`team.md` の決まりどおり）。mailpit の更新は E2E の Mailpit 受け手には使っておらず（動いているコンテナは v1.31.5 のまま。次の配備で作り直される）、`docker compose config` は通った。
- 新しく出た #79（`grafana/otel-lgtm` 0.37.0 → 0.38.0）は、この Intent では取り込まず、次の Intent の候補とする。

### 10.3 Dependabot の PR と CI（push の後）

- Dependabot の PR: #54〜#78（25 件）はすべて CLOSED で、マージ済みは 0 件（`gh pr list --state all`）。#76〜#78 は `9bf6b70` の push の直後に Dependabot が自動で閉じた。開いているのは新しい #79（`grafana/otel-lgtm` 0.37.0 → 0.38.0）だけで、次の Intent の候補とする。
- CI: `b5a7f3a`（run 40245315982）は 1 回目の試行で success（検査の job 28 分 39 秒）。B5 を含む先頭 `9bf6b70`（run 40352740558）も **1 回目の試行で success**、検査の job `./gradlew verify` は 15:47:17Z〜16:17:50Z = **30 分 33 秒**（42 分の線の内、60 分の 50.9%）。どちらも再実行なし。
