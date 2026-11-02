# Build and Test のまとめ（Intent 261101-maintenance-update）

scope bugfix・深さ Minimal・Test Strategy Minimal。`develop` の最後の形（`45a5872a` = `origin/develop`）で、clean 付きの verify・E2E・負荷の試験（FR10）を流し、この Intent のコミットの CI を試行ごとに確かめた。コードは変えていない。詳細は `test-results.md`。

## 結論

- **verify**: 成功（27分34秒）。単体 2,809・結合 1,663（SKIPPED 10 は DB の種類ごとの前からの `assumeTrue`）・画面 238 ファイル 2,312 件、失敗 0。カバレッジは backend 全体 行 97.6%・分岐 91.3%、frontend 全体 行 95.16%・分岐 90.40%（伏せ字の道具 94.23%・90.65%）。下限を下回るのは `packagesJudgedByTotal` の `common.health`（行 79.2%）だけ（前からの扱い）。
- **E2E**: 277 passed（14.6 分、実際の Chromium）、axe の違反 0 件、報告に秘密の形 0 件。終えた後に見本の PG を上乗せ無しで作り直し、`127.0.0.1:15432` を閉じた（版とダイジェストは `compose.yaml` と一致）。
- **負荷の試験**: 判定の回（PostgreSQL 2回目・MySQL 1回目・MariaDB 2回目）で、全場面が線と `checks` を通過。プールの時間切れ 0・503 0、アプリの `memory.peak` 38.4〜47.6%、対象DB の `anon` 15.5%・90.4%・84.4%（`OOMKilled` false・`oom_kill` 0）、`dslMixed` の `memory.peak` 0.9077（線 0.95）・`anon` 0.6935。**Not Met は 0 件**。PostgreSQL と MariaDB の1回目は、暖めの道具（`SELECT *` を道具で受ける形）が対象DB のコンテナの上限に当たって線（`OOMKilled`・`oom_kill`）を割ったため、原因を再現で確かめてから暖めを集計の形に替えて流し直した。線と物差しは変えていない。
- **配備したアプリ**: 06:55:51Z〜10:26:40Z（約 3時間31分）止め、戻した後は healthy・`/actuator/health` 200・イメージ ID `sha256:d8a467b60f6f…` は止める前と同じ。
- **レビューの R-02**（verify と E2E の数字が記録を信じる形）: `develop` の最後の形で clean 付きの verify と E2E を流し直し、上の実測で応えた。
- **CI**: この Intent の6つの run はすべて1回目で成功。検査の job の最大 37分05秒（42 分の線の内）。CI の Java は 25.0.5+8 で Dockerfile の JRE と同じ。
- **記録**: `evidence/` の参照の場面の json 18 ファイルは伏せ字の道具を通し、`--scan`・`gitleaks dir`・`eyJ` の検索がどれも 0 件。

## テストの種類と生成した手順書

| 種類 | 手順書 | 流した場 |
|---|---|---|
| ビルド・1コマンドの検査（単体・結合・画面・カバレッジ・安全の検査） | `build-instructions.md` | Node（Vitest は jsdom）・JVM・Testcontainers |
| 結合（対象DB 3種類）・E2E | `integration-test-instructions.md` | Testcontainers・実際の Chromium（Playwright） |
| 性能（k6・`dslMixed`） | `performance-test-instructions.md` | 使い捨ての環境（colima の VM） |
| 安全 | `security-test-instructions.md` | verify の中・記録の検索 |

Test Strategy は Minimal だが、要件 FR10 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・安全の手順書も作った（`project.md` の学びと同じ扱い）。

## カバレッジの期待

単位の分割の無い bugfix のため、全体の下限（行 80%・分岐 70%。backend はパッケージごとにも）だけを当てる。この Intent は下限・除外・`packagesJudgedByTotal`・ArchUnit の境界テスト・SpotBugs の除外・Flyway の移行を変えていない（`3675b2f7^..HEAD` の差で 0。`frontend/vitest.config.ts` は T1 の `include`・`allowExternal` の追加だけで `thresholds` は同じ）。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| BT-VERIFY | NFR3・`team.md` | clean 付きの `verify` が成功、テストの失敗 0 | 終了コード 0、失敗 0、SKIPPED 10（前からの `assumeTrue`） | `test-results.md` 2節 | build-and-test | Met |
| BT-COV-BE | NFR1・`team.md` | backend 全体とパッケージごと 行 80%・分岐 70% | 全体 97.6%・91.3%、下回るのは一覧の `common.health` だけ | `test-results.md` 4.1 | build-and-test | Met |
| BT-COV-FE | NFR1・FR9.2 | frontend 全体 行 80%・分岐 70%（伏せ字の道具を含む） | 95.16%・90.40%（道具 94.23%・90.65%） | `test-results.md` 4.2 | build-and-test | Met |
| BT-E2E | 計画の引き継ぎ 1・FR4.6 | E2E の全件の通過、axe の違反 0 件、axe-core は lockfile で1つ | 277 passed・unexpected 0・flaky 0、axe の検査はすべて通過、axe-core 4.14.0 の1つ（実際の Chromium） | `test-results.md` 3節 | build-and-test | Met |
| BT-E2E-REPORT | `team.md`・`project.md` | 報告に JWT・Authorization・資格情報の値が無い | `eyJ` 0・`Authorization` 0・`refreshToken` 0・`postgresql://` 0、資格情報は名前だけ | `test-results.md` 3節 | build-and-test | Met |
| BT-PG-PORT | ③ の依頼者の決定・FR7.3 | E2E の後に見本の PG のポートを閉じ、版とダイジェストが `compose.yaml` と一致 | `5432/tcp` だけ、`postgres:18.6@sha256:3a4264520e12…` で一致 | `test-results.md` 3.1 | build-and-test | Met |
| BT-JAVA-PATCH | 計画 Step 6 | CI の Java のパッチが Dockerfile の JRE と同じ | CI 25.0.5+8・JRE 25.0.5_8 | `test-results.md` 7節 | build-and-test | Met |
| BT-CI-BEFORE-MERGE | Testing Contract（CI の実行） | CI で選んだテストを流す | この計画は CI Pipeline を流さない（`aidlc-state.md` で ci-pipeline は Skip） | — | — | N/A（the plan runs no CI Pipeline） |
| NFR5-VERIFY-TIME | NFR5 | verify の時間を記録 | 27分34秒 | `test-results.md` 6節 | build-and-test | Met |
| NFR5-CI-TIME | NFR5・`team.md` | CI の検査の job が 42 分以内 | 最大 37分05秒（6 run、すべて1回目で成功） | `test-results.md` 7節 | build-and-test | Met |
| NFR2-OSV | NFR2 | OSV-Scanner で High 以上 0 | `osvScan` 通過（Moderate 3件は持ち越し） | `test-results.md` 2節 | build-and-test | Met |
| NFR4-COMPAT | NFR4 | Flyway の移行・API の形を変えない | 移行の差 0。P1 は一覧の並びの式、T3 は WARN のキーだけ。E2E 全件通過 | `3675b2f7^..HEAD` の差・`test-results.md` 3節 | build-and-test | Met |
| FR10.2-LINES | FR10.2 | 線と物差しが計画の表と同じ | 19 場面の `k6 inspect` と `thresholds-test.sh` が一致 | `build/bt-k6-inspect.txt`・`test-results.md` 5.1 | build-and-test | Met |
| K6-READ-1000 | FR10.1 | `bizDefinition`・`bizSearchFirst`・`bizDetail` の p95 < 1,000 ms（3種類） | 最大 381.74 ms（MySQL `bizSearchFirst`） | `test-results.md` 5.3 | build-and-test | Met |
| K6-CONTAINS-3000 | FR10.1 | `bizSearchContains` の p95 < 3,000 ms | 最大 2,613.94 ms（MariaDB） | `test-results.md` 5.3 | build-and-test | Met |
| K6-LAST-2000 | FR10.1 | `bizSearchLast`・`bizSearchLastSorted` の p95 < 2,000 ms | 最大 1,515.89 ms（MariaDB `bizSearchLastSorted`） | `test-results.md` 5.3 | build-and-test | Met |
| K6-WRITE-1000 | FR10.1 | `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` の p95 < 1,000 ms | 最大 14.19 ms（MariaDB `bizDelete`） | `test-results.md` 5.3 | build-and-test | Met |
| K6-REF-1000 | FR10.1 | 参照の5場面（`refPickerContains` を除く）と `refCreateChecked` の p95 < 1,000 ms | 最大 69.05 ms（PG `refPickerEquals`） | `test-results.md` 5.3・`evidence/` | build-and-test | Met |
| K6-REFCONTAINS-3000 | FR10.1 | `refPickerContains` の p95 < 3,000 ms | 最大 1,290.33 ms（MariaDB） | `test-results.md` 5.3・`evidence/` | build-and-test | Met |
| K6-CHECKS | FR10.1 | すべての場面で `checks` の率 1 | 判定の回の全場面で失敗 0 | `test-results.md` 5.3 | build-and-test | Met |
| K6-POOL | FR10.1 | 待ちの時間切れの増分 0・503 0 件 | 読み取り・書き込みのプールとも増分 0、503 0 | `test-results.md` 5.4 | build-and-test | Met |
| MEM-WIDE | FR10.1 | `bizSearchWide` のアプリの `memory.peak` < 90% | 44.0%・47.6%・45.9% | `test-results.md` 5.5 | build-and-test | Met |
| MEM-LARGE | FR10.1 | `bizCreateLarge` のアプリの `memory.peak` < 90% | 38.4%・42.6%・40.7% | `test-results.md` 5.5 | build-and-test | Met |
| TDB-ANON | FR10.1 | 対象DB の `anon` の最大 ≤ 92% | PG 15.5%・MySQL 90.4%・MariaDB 84.4% | `test-results.md` 5.5 | build-and-test | Met |
| TDB-OOM | FR10.1 | 対象DB の `OOMKilled` false・`oom_kill` 0 | 判定の回の3種類とも false・0（1回目の PG・MariaDB は暖めの道具で割り、流し直した） | `test-results.md` 5.2・5.5 | build-and-test | Met |
| DSL-PEAK | FR10.1 | `dslMixed` の `memory.peak` / `memory.max` < 0.95（前の線 0.9 も並べる） | 0.9077（前の線 0.9 は超え） | `test-results.md` 5.6 | build-and-test | Met |
| DSL-ANON | FR10.1 | `dslMixed` の `anon` の最大 ≤ 85% | 69.35% | `test-results.md` 5.6 | build-and-test | Met |
| FR10.1-COMPARE | FR10.1 | 前の Intent（261029・261027）の値と並べる | 並べた（261016 の値も） | `test-results.md` 5.7 | build-and-test | Met |
| FR10.3-REDACT | FR10.3・`project.md` の Forbidden | 記録の json に認証の値が無い | 18 ファイル、`--scan` 0・`gitleaks dir` 0・`eyJ` 0 | `test-results.md` 5.9 | build-and-test | Met |
| FR10.4-RERUN | FR10.4 | 線を割ったら2回目、2回目も割れたら Not Met のまま | PG・MariaDB の1回目が対象DB の状態で割れ、2回目で通過。2回目で割れたものは無い | `test-results.md` 5.2 | build-and-test | Met |
| PERF-LOG-LEAK | NFR2・`project.md` | 使い捨てのアプリのログと k6 の出力に利用者の名前・行の値・`eyJ` が無い | すべて 0 件 | `test-results.md` 5.8・5.6 | build-and-test | Met |
| DEPLOYED-RESTORE | 依頼の手順 3 | 配備したアプリが戻り、イメージ ID が変わらない | healthy・200・`sha256:d8a467b60f6f…` | `test-results.md` 5.1 | build-and-test | Met |
| FR11.1 | FR11.1 | 配備・起動確認・スモークテスト・戻し方 | まだ行っていない | — | deployment-pipeline・deployment-execution | Unverified |
| FR11.2 | FR11.2 | 古いイメージの片付け（承認の後） | まだ行っていない | — | deployment-execution | Unverified |
| FR11.3 | FR11.3 | `main` への取り込みの前の CI の確かめと fast-forward | まだ行っていない | — | deployment-execution | Unverified |

FR11.1〜FR11.3 は配備の段の作業で、この計画の後の段（Deployment Pipeline・Deployment Execution、どちらも EXECUTE）が持ち主。

## 既知の振る舞いの変化（コード生成のレビューの R-01）

P1（FR9.1）の直しで、業務データの一覧の並び（`RowSqlBuilder.orderBy`）を参照先と同じ `RowDialect.sortExpression` にそろえ、文字の型のカラムで並べ替えるときは**先頭 100 字で切った値**で並べる。利用者から見える変化:

- 先頭 100 字が同じ値どうしは、101 字目以降ではなく**主キーの順**で並ぶ（主キーで並びを決める既存の決まりによる）。
- **主キーの無いテーブルとビュー**では、先頭 100 字が同じ値どうしの**順が約束されない**（もともと主キーが無いと順は約束されない）。
- 内側と外側の問い合わせは同じ並びの句を作るため、ページの重なりと抜けは出ない（レビューの R-01 の確かめ）。
- MySQL で長い値（約 8,000 字を超える）のカラムで並べ替えた一覧が 1038（Out of sort memory）で 500 になることは無くなった（`*DeepPageSortIT`、3種類の DB で9件通過）。

## 持ち越し

| 項目 | 内容 | 取り込む時期 |
|---|---|---|
| typescript 7 | 7.0.2 を判定基準 3（上流の未対応: `@typescript-eslint/*` 8.72.1 の peer が `<6.1.0`）で見送り、`.github/dependabot.yml` の `ignore` に書いた | typescript-eslint が 7 を支援したとき |
| A2 | make-you-chic-ui の lockfile の `braces` 3.0.3 は上流でも直っていない（開発時だけの依存、警告の扱い） | 上流が直したとき |
| A3 | 依頼（5）（1ページの件数の選択・横のスクロールの領域）の口が上流 `cae5fed` に無く、自前の `ScrollRegion`・`PageSizeSelect` を置き換えていない | 上流が公開したとき |
| OSV の Moderate 3件 | `io.opentelemetry:opentelemetry-api` 1.62.0・`org.mariadb.jdbc:mariadb-java-client` 3.5.10（どちらも Spring Boot の管理の版）、frontend の `minimatch` 10.2.6（開発時だけ）。統合を止めない重大度 | Spring Boot が管理の版を上げたとき、typescript-eslint が範囲を広げたとき |
| 線に近い値 | MariaDB の `bizSearchContains` 2,613.94 ms（線 3,000 ms）、MySQL の対象DB の `anon` 90.4%（線 92%）、`dslMixed` の `memory.peak` 0.9077（線 0.95） | 業務データの読み取りや対象DB の設定を変える Intent で測り直す |
| 暖めの手順 | `perf/README.md` は暖めを「表を1回全部読んで暖める」とだけ書き、形を決めていない。`SELECT *` を道具で受ける形は対象DB のコンテナの上限に当たる（PG は psql の強制終了、MariaDB はサーバーの停止） | README に「結果を1行にする集計の形」を書き足すかを後の Intent で決める |

## 計画との差

1. **PostgreSQL と MariaDB は全場面を2回流した**: FR10.1 は「線を割った場面だけ2回目」だが、割れたのが場面ではなく対象DB の状態（暖めの道具による `oom_kill`・停止）で、回の全体が同じ条件にならないため、環境を作り直して全場面を流し直した。判定は2回目で行い、1回目の PG は参考として並べた。MariaDB の1回目は対象DB が止まった後の場面を流さずに止めた。
2. **暖めの形を回の途中で替えた**: 1回目は `SELECT *` を道具で受ける形（前の Intent 261027 の MySQL・MariaDB と同じ形）、2回目の PG・MariaDB は結果を1行にする集計の形（前の Intent 261029 と同じ考え方）。MySQL は1回目のまま（OOM なし、`anon` の最大は暖めの後の `bizCreateLarge`）。
3. **業務データの読み取りの場面は読み取りのプールの既定の上限 10** で流した（`perf/README.md` の「業務データの場面」と前の Intent 261027 と同じ）。参照・書き込みの場面は上限 5（261029 と同じ）。Intent 261016（上限 5）の値と並べるときは上限が違う。
4. **`dslMixed` は既定の環境の1回だけ**（FR10.1 の「1回ずつ」による。`perf/README.md` の「既定の2回と試しの2回」は流していない）。線を割らなかったため2回目は流していない。
5. **電源**: verify は電池（100%）で始めた（途中で AC に戻った）。`caffeinate -dims` で包み、`pmset -g log` で窓に眠りが無いことを確かめた。
6. 配備したアプリを止めた時間は約 3時間31分（2回目の流し直しを含む）。

## 準備の状態

| 区分 | 状態 |
|---|---|
| build-ready | はい（verify 成功、WAR とイメージを作れる） |
| test-ready | はい（単体・結合・画面・E2E・負荷の試験・CI がすべて通過、Not Met 0） |
| deployment-ready | 配備の段の作業（FR11）が残る。配備するソースの負荷の試験は済み |

段の失敗の判定（段の定義の Failure predicate）: コマンドの失敗は最終の回に無く、Not Met は 0 件。Unverified は FR11.1〜FR11.3 の3行で、どれも後の段（Deployment Pipeline・Deployment Execution）が持ち主。

## Sources

- `test-results.md`・`build-instructions.md`・`integration-test-instructions.md`・`performance-test-instructions.md`・`security-test-instructions.md`（この段）
- 要件 `inception/requirements-analysis/requirements.md`（FR1〜FR11・NFR1〜NFR5）
- `construction/code-generation/code-generation-plan.md`（Testing Contract・「Build and Test に引き継ぐこと」）・`code-summary.md`・`unit-test-instructions.md`・`traceability.json`・`reviews/review-01.md`（R-01）
- `aidlc-state.md`（Stages to Execute・Skip）
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] PostgreSQL の1回目の `oom_kill 1` は、場面の間の `anon` の小ささと、同じ問い合わせだけを流した再現（psql の終了コード 137・`oom_kill` 0→1）から、暖めの psql の強制終了と読んだ。
- 暖めの手順を `perf/README.md` に書き足すか（持ち越しの表）は、承認の場で依頼者に確かめる。
