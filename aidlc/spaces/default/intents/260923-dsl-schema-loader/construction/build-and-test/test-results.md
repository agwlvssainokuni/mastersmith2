# 実行の記録（test-results）

Intent `260923-dsl-schema-loader` の Build and Test で実際に走らせたコマンドと、その結果。**ここに書く数字はすべて実測**であり、上流の文書の記述を写したものではない（project.md の Testing Posture）。

- 実行した日: 2026-09-24（Loop-back 1 の後の実行も同じ日）
- 実行の環境: macOS（darwin 25.5.0）、JDK 25（Temurin）、Node.js 24、Gradle Wrapper 9.7.1、colima の VM（CPU 4・メモリ 6GiB）、Chromium 153（Playwright）。配備したアプリ（プロジェクト `mastersmith`）は動かしたまま
- 対象のソース:
  - 最初の実行（C1〜C11）: コミット `39f9aeb` と、その上に E2E の名前の見直しと `040-dsl-admin.e2e.ts`、`frontend/playwright.config.ts`（`workers: 1`）、`perf/` の台本、README、`docker/perf/compose.yaml` を足した状態（後に `fb08de1` としてコミット。アプリのソースは同じ）
  - **Loop-back 1 の後の実行（C12〜C18）: コミット `8961cb2`**。Code Generation に戻って、対象DB のテストの拡張の登録の順を直して構造の検査を足し（NFR12.3）、内部DB の既定の接続先に `DEFRAG_ALWAYS=TRUE` を足した（U4-STORAGE）。いきさつは下の `## Loop-Back Log`
- **今の判定は `8961cb2` の結果による**。最初の実行の結果は、`8961cb2` で変わらない部分（E2E・1回ずつの時間）の根拠と、直す前の記録として残す。

## 1. 走らせたコマンドの一覧

### 1.1 Loop-back 1 の後（`8961cb2`）

| # | コマンド | 目的 | 結果 |
|---|---|---|---|
| C12 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 統合の前の関門、件数とカバレッジの実測 | **成功**（6分17秒） |
| C13 | `./gradlew osvScan --rerun` | 依存関係の脆弱性の走査のやり直し（UP-TO-DATE で飛ばさない） | **成功**（脆弱性 0） |
| C14 | `DOCKER_HOST=unix:///nonexistent/docker.sock env -u CI ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*' --tests 'cherry.mastersmith.dslmanage.*'` | コンテナの実行環境が無いときの動き（NFR12.3）。colima は止めない | **成功**（27 秒。129 件: 失敗 0・飛ばし 57） |
| C15 | `./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl-lb1 .` | 保存の量の測り直し用のイメージ（配備のタグ `local` は上書きしない） | 成功（`sha256:593656c3…`） |
| C16 | `MASTERSMITH_IMAGE_TAG=perf-dsl-lb1 ./perf/dsl-timing.sh --storage postgres`（2回） | 保存の量（21 回＋プレビュー、`DEFRAG_ALWAYS=TRUE` の既定） | 完了（`build/perf-results/dsl-lb1-storage21-defrag/`・`dsl-lb1-storage21-defrag-2/`） |
| C17 | C16 を `STORAGE_ROUNDS=40` で | 20 件を超えた後の増え方 | 完了（`build/perf-results/dsl-lb1-storage40-defrag/`） |
| C18 | C16 を `PERF_DB_URL=jdbc:h2:file:/app/data/mastersmith` で | 比べるため `DEFRAG_ALWAYS` なしで | 完了（`build/perf-results/dsl-lb1-storage21-nodefrag/`） |

### 1.2 最初の実行（`39f9aeb` と E2E・`perf/` の変更）

| # | コマンド | 目的 | 結果 |
|---|---|---|---|
| C1 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`39f9aeb`） | 統合の前の関門、件数とカバレッジの実測。あわせて VM のメモリを 10 秒ごとに記録 | **成功**（4分21秒） |
| C2 | C1 と同じ（E2E と `perf/` の変更の後） | 変更の後の関門 | **成功**（4分14秒） |
| C3 | `./gradlew e2eTest` | ビルドした WAR での E2E（4ファイル） | **成功**（6 件、19.0 秒） |
| C4 | Docker の接続先（`DOCKER_HOST`）を存在しないソケットに向けて `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` | コンテナの実行環境が無いときの動き（NFR12.3、Q6: B） | **失敗**（35 件: 成功 6・飛ばし 26・失敗 3）。Loop-back 1 の理由（3.1） |
| C5 | C4 と同じ向け先で、`DslTargetDbIT` と `dslmanage.generate` の IT に絞った `:backend:integrationTest` | 同上（U3・U4 の対象DB のテスト） | **失敗**（10 件: 飛ばし 9・失敗 1） |
| C6 | `./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl .` | 性能の測定用のイメージ | 成功 |
| C7 | `MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres mysql mariadb` | 1回ずつの時間（3種類の対象DB、100 × 100） | 成功（`build/perf-results/dsl-run1/`） |
| C8 | `MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --ui postgres` | 画面の時間 | 成功（`build/perf-results/dsl-run2-ui/`） |
| C9 | `MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --lang --pattern --storage postgres`（2回） | 英語の表示・重い正規表現・保存の量 | 1回目は英語の表示の台本が時間切れ（`dsl-run3-extra`、3.3）。2回目で完了（`dsl-run4-extra`） |
| C10 | C9 の `--storage` を `STORAGE_ROUNDS=40` で | 保存の量の増え方の再現 | 完了（`build/perf-results/dsl-run5-storage40/`） |
| C11 | `k6 inspect`（全場面）と、`dslLight`・`dslCycle` を 10 秒ずつ（`grafana/k6:2.3.0`） | Performance Validation に渡す k6 の台本が動くことの確かめ（判定には使わない） | 成功（`build/perf-results/dsl-k6-smoke/`） |

- C4・C5・C14 の後は Docker の接続先を元に戻した。C1・C2・C12 は対象DB のテストを飛ばさずに（飛ばし 0）通っている。
- 性能の測定（C6〜C11・C15〜C18）は、すべて使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`、番号 18080）に対して行い、終わったら `down -v` と一時ディレクトリの削除で片付けた。配備したアプリ・その内部DB・監査ログには要求を送っていない。
- `8961cb2` の変更は、テストのコード（拡張の登録の順・構造の検査・詰め直しのテスト）と内部DB の既定の接続先（`backend/src/main/resources/application.yaml`・`.env.example`・README）だけである。E2E（C3）と1回ずつの時間（C7〜C9）は `8961cb2` では流し直していない（4節）。

## 2. 結果の詳細

### 2.1 ビルド（C12。最初の実行は C1・C2）

```
BUILD SUCCESSFUL in 6m 17s   （C12、8961cb2）
BUILD SUCCESSFUL in 4m 21s   （C1、39f9aeb）
BUILD SUCCESSFUL in 4m 14s   （C2）
```

`verify` の 10 の段（0 準備〜9 成果物）がすべて通った。フォーマット・リンタ・ライセンスヘッダー・ビルド（Java のコンパイル、`tsc --noEmit`、Vite のビルド）の違反・誤りは 0 件。

### 2.2 テストの件数（C12）

| 種類 | テストのクラス・ファイル | 合計 | 成功 | 失敗 | 飛ばし |
|---|---|---|---|---|---|
| バックエンドの単体テスト（`*Test`） | 99 | **716** | 716 | **0** | 0 |
| バックエンドの結合テスト（`*IT`、対象DB 3種類を含む） | 73（入れ子のクラスを含む） | **375** | 375 | **0** | **0** |
| 画面のテスト（Vitest） | 47 | **313** | 313 | **0** | 0 |
| E2E（Playwright、C3） | 4 | **6** | 6 | **0** | 0 |
| **合計** | — | **1,410** | **1,410** | **0** | **0** |

- 単体テストが C1・C2 の 710 件から 716 件に増えたのは、Loop-back 1 で足した `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ExtensionOrderArchitectureTest.java`（3 件。拡張の登録の順の構造の検査）と `backend/src/test/java/cherry/mastersmith/config/H2DefragOnCloseTest.java`（3 件。終了時の詰め直しと既定の接続先）である。
- E2E の件数は C3（最初の実行）のもの。

単位ごとの内訳（バックエンドはパッケージで割り当て。`targetdb` は U1、`dsl` は U2、`dslmanage.generate` は U3、ほかの `dslmanage` は U4、`dslmanage.web.DslAccessControlIT` は U5 の一括の確かめ）。

| 単位 | 単体テスト | 結合テスト | 画面 | E2E |
|---|---|---|---|---|
| U1 対象DB | 83（10 クラス。`ExtensionOrderArchitectureTest` を含む） | 50（8 クラス。3種類の DB） | — | — |
| U2 DSL の定義 | 89（11 クラス） | 2（`DslSchemaPublicationIT`） | — | — |
| U3 既定の DSL の生成 | 75（5 クラス） | 9（3種類の DB の生成） | — | — |
| U4 DSL の管理 | 62（8 クラス） | 39（6 クラス） | — | — |
| U5 DSL の管理画面 | — | 31（`DslAccessControlIT`） | `src/features/dsl/` の 16 ファイルと `src/shared/api-client/` の足したテスト | 1（`040-dsl-admin.e2e.ts`） |
| 前の Intent までの部品 | 407（65 クラス。`config` の `H2DefragOnCloseTest` を含む） | 244（54 クラス） | 残り | 5 |

- Vitest の出力に jsdom の `Not implemented: HTMLCanvasElement's getContext()` が出る。jsdom に canvas が無いことの通知で、テストの結果には影響しない。
- 結合テストの終わりに OTLP の送り先に届かない WARN が出るのは、外部エクスポートを有効にする既存のテスト（`ExternalExportIT`）の想定どおりの出力である。

### 2.3 カバレッジ（C12。C1・C2 と同じ値）

バックエンド（JaCoCo、単体＋結合、`backend/build/reports/jacoco/test/jacocoTestReport.xml`）

| 範囲 | 行 | 分岐 | 下限 |
|---|---|---|---|
| **全体** | **98.1%**（3884/3960） | **94.1%**（1349/1433） | 行 80%・分岐 70% |

新しいパッケージ（13 個、パッケージごとにも下限を当てる。値は C2 の報告から読み取ったもの。C12 は本番のコードが同じで、全体の値も同じ）

| 単位 | パッケージ | 行 | 分岐 |
|---|---|---|---|
| U1 | `targetdb.config` | 99.2%（125/126） | 98.5%（66/67） |
| U1 | `targetdb.domain` | 100%（120/120） | 96.9%（62/64） |
| U1 | `targetdb.repository` | 96.0%（119/124） | 87.5%（28/32） |
| U1 | `targetdb.service` | 100%（29/29） | 100%（18/18） |
| U2 | `dsl.domain` | 100%（236/236） | 94.8%（110/116） |
| U2 | `dsl.parse` | 98.7%（220/223） | 92.1%（117/127） |
| U2 | `dsl.service` | 98.5%（130/132） | 100%（52/52） |
| U2 | `dsl.validate` | 97.4%（263/270） | 90.7%（117/129） |
| U3 | `dslmanage.generate` | 99.3%（303/305） | 100%（145/145） |
| U4 | `dslmanage.domain` | 100%（130/130） | 92.3%（24/26） |
| U4 | `dslmanage.repository` | 100%（74/74） | 100%（4/4） |
| U4 | `dslmanage.service` | 100%（506/506） | 98.5%（130/132） |
| U4 | `dslmanage.web` | 100%（131/131） | **86.7%**（26/30。最低） |

既存のパッケージ（`packagesJudgedByTotal`、全体の合計で判定）のうち単独で下限を下回るもの

| パッケージ | 行 | 分岐 | 扱い |
|---|---|---|---|
| `auth.repository` | 93.9%（31/33） | **50.0%**（1/2） | Q9: A で今のまま |
| `common.health` | **79.2%**（42/53） | 100%（2/2） | Q9: A で今のまま |

- U1 の実測で単独で下回っていた `audit.service` は、行 80.9%（76/94）・分岐 83.3%（10/12）で下限を上回った。
- カバレッジの除外は増えていない。`backend/build.gradle.kts` のこの Intent での差は、依存の除外（`jackson-dataformat-yaml` など）と `packagesJudgedByTotal` だけで、計測の除外（起動クラス・設定値だけのクラス・自動生成・`vendor/`）は変わっていない。

画面（`@vitest/coverage-v8`）

| 範囲 | 文 | 分岐 | 関数 | 行 |
|---|---|---|---|---|
| **全体** | 97.76%（1004/1027） | **93.6%**（585/625） | 98.1%（311/317） | **97.86%**（961/982） |
| `features/dsl` | 97.1% | 92.28% | 98.62% | 96.98% |
| `features/dsl/api` | 98.43% | 97.82% | 100% | 98.36% |
| `shared/api-client` | 98.61% | 97.56% | 100% | 98.59% |

### 2.4 安全の検査（C12 の 8 の段と C13）

| 検査 | 結果 |
|---|---|
| Gitleaks | 143 コミットを走査、`no leaks found` |
| SpotBugs＋FindSecBugs（`spotbugsGate`） | priority 1 は 0 件、パターン名が `SQL_` で始まるものは 0 件。priority 2 が 66 件・priority 3 が 43 件の警告（統合は止めない） |
| OSV-Scanner（C13、`./gradlew osvScan --rerun`） | 走査し直した（UP-TO-DATE ではない）。`backend/gradle.lockfile` 244・`frontend/package-lock.json` 395・`vendor/make-you-chic-ui/package-lock.json` 404 パッケージ、脆弱性 0（失敗の条件 0 件・警告 0 件、`build/reports/osv-scanner/osv.json`） |

### 2.5 成果物（C12 の 9 の段）

| 確かめ | 結果 |
|---|---|
| 実行可能 WAR（`bootWar`） | `backend/build/libs/mastersmith.war`（画面の `dist` を同梱） |
| WAR の中の JSON Schema（`verifyDslSchemaInWar`） | 成功（`WEB-INF/classes/static/dsl/dsl-schema-v1.json` が正本と一致） |
| 初回の読み込みの JavaScript（`frontendBundleSize`） | **111.4 KB（gzip）**（`assets/index-Cyfbom38.js`）。目安 500KB 以内 |
| 第三者のライセンスの文書 | MariaDB Connector/J の LGPL 2.1 の文書が `WEB-INF/classes/META-INF/third-party-licenses/` にある。MySQL Connector/J の jar に `LICENSE`、PostgreSQL JDBC の jar に `META-INF/LICENSE` がある |

### 2.6 E2E（C3、最初の実行）

```
Running 6 tests using 1 worker
  ✓ 1 e2e/010-skeleton.e2e.ts   shows the login layout at / with a CSP header and without violations or script errors (887ms)
  ✓ 2 e2e/010-skeleton.e2e.ts   opening a screen URL directly also shows the login layout (753ms)
  ✓ 3 e2e/020-auth.e2e.ts       logs in as the initial administrator, keeps the session over a reload and logs out (1.8s)
  ✓ 4 e2e/020-auth.e2e.ts       shows one message for a wrong password and stays on the login screen (1.1s)
  ✓ 5 e2e/030-admin-access.e2e.ts  logs in, opens the administration area from the sidebar and logs out (1.7s)
  ✓ 6 e2e/040-dsl-admin.e2e.ts  logs in, submits a pasted DSL, applies it after confirmation and logs out (6.0s)
  6 passed (19.0s)
```

- 1 worker で、010 → 020 → 030 → 040 の番号の順に実行された（Q5: B）。
- 040 は、プレビューが無いときの読み込みの 404（`DSL_PREVIEW_NOT_FOUND`）を想定どおりの表示として集計から外している。CSP 違反とスクリプトのエラーは 0 件。

### 2.7 対象DB の結合テストの置き場（TP-TDB-PLACE）

| 項目 | 実測 |
|---|---|
| `verify` 全体の時間 | 4分21秒（C1）・4分14秒（C2）・6分17秒（C12） |
| 結合テストの時間（JUnit の報告の和、C2） | 約 192 秒 |
| colima の VM のメモリの使用量（C1 の間、10 秒ごと） | 最小 872MiB・最大 **1,424MiB**（空き 4,485MiB 以上）。配備したアプリを動かしたまま |

依頼者の決定（Q1: A）により、対象DB の結合テストは今のまま3種類とも `./gradlew verify` の中で毎回実行する（CI も同じ）。

### 2.8 コンテナの実行環境が無いときの動き（C14、NFR12.3）

| 実行 | 合計 | 失敗 | 飛ばし（SKIPPED） | 結果 |
|---|---|---|---|---|
| C14（`targetdb.*`・`dslmanage.*`、`8961cb2`） | 129 | **0** | 57（10 クラス） | **BUILD SUCCESSFUL**（27 秒） |

- 飛ばした 10 クラス: `MysqlSchemaQueriesIT`・`MariadbSchemaQueriesIT`・`PostgresSchemaQueriesIT`・`MysqlTargetSchemaReaderIT`・`MariadbTargetSchemaReaderIT`・`PostgresTargetSchemaReaderIT`・`DefaultDslGeneratorMysqlIT`・`DefaultDslGeneratorMariadbIT`・`DefaultDslGeneratorPostgresIT`・`DslTargetDbIT`。
- 10 クラスすべての出力（テスト結果の XML）に、警告「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない（colima を起動してやり直す）」がある。黙って飛ばしていない。
- 再発は構造の検査 `ExtensionOrderArchitectureTest`（C12 で違反 0）が防ぐ。
- **NFR12.3 は Met**。直す前（C4・C5）は4クラスが `initializationError` で失敗していた（3.1）。

### 2.9 性能

1回ずつの時間（C7〜C9、`39f9aeb`）と保存の量（C16〜C18、`8961cb2`）。結果の一覧と判定は `performance-test-instructions.md` 2.2。上限はいずれも CPU 4・メモリ 2g。

| Target ID | 実測 | 判定 |
|---|---|---|
| NFR1.6 | 生成 1.54〜2.64 秒（3種類） | Met |
| NFR1.7 | 読み取り 25〜296ms・組み立てと書き出し 426〜767ms・検証 419〜832ms | Met |
| NFR1.4 | 検証 419〜832ms（6,146,161〜6,383,161 バイト） | Met |
| NFR2.5 | 6,146,161〜6,383,161 バイト（10MB の内） | Met |
| NFR1.5 | 10MB の投入 0.82〜1.14 秒、区間のヒープの最大 1,237MB | Met |
| NFR1.8 | 表示 0.045〜0.200 秒・投入 0.57〜1.04 秒・戻し 0.54〜1.00 秒 | Met |
| NFR1.11 | 0.032〜0.038 秒 | Met |
| NFR1.18 | 今の状態 0.045〜0.113 秒、プレビュー（照合を含む）0.22〜0.29 秒 | Met |
| NFR1.19 | 25ms | Met |
| NFR1.20 | 553〜664ms | Met |
| U2-PATTERN-COMPILE | 重い投入 0.34〜0.44 秒、直後の普通の投入 0.228 秒、打ち切りのログ 0 件 | Met |
| U5-LANG-E2E | `pass: true` | Met |
| U4-STORAGE-RUN | 動いている間の H2 のファイル: 21 回で 267.4MB、プレビューを置いて 278.2MB（2回とも同じ）、40 回で 483.1MB。1回に約 10.78MB 増え、頭打ちなし | **Not Met** |
| U4-STORAGE-RESTART | 止めて起動し直した後: 15.9MB（21 回・40 回とも）。止めるのに 0.55〜1.00 秒、ExitCode 143、データは前後で一致 | Met |

k6 の動作の確かめ（C11。条件をそろえていないため判定に使わない）: `dslLight` は 61,407 要求で失敗 0、今の状態 p95 1.1ms・履歴 p95 1.07ms。`dslCycle` は 49 要求で失敗 0、適用 p95 4.71ms・破棄 p95 10.15ms。

大きさの単位: この記録の MB は 10^6 バイト。`build/perf-results/*/summary.md` の保存の量の表は 2^20 バイトで割った値で、例えば 278.2MB は表の 265.3、483.1MB は表の 460.7、15.9MB は表の 15.2 にあたる。

## 3. 失敗と、Loop-back 1 で直したこと

### 3.1 NFR12.3: コンテナの実行環境が無いときに4クラスが失敗した（直した。今は Met）

**直す前の症状**（C4・C5）

```
MariadbTargetSchemaReaderIT > initializationError FAILED
    java.util.NoSuchElementException
        at java.base/java.util.ArrayDeque.removeLast(ArrayDeque.java:370)
        at org.springframework.boot.test.system.OutputCapture.pop(OutputCapture.java:81)
        at org.springframework.boot.test.system.OutputCaptureExtension.afterAll(OutputCaptureExtension.java:106)
        at java.base/java.util.ArrayList.forEach(ArrayList.java:1604)
（MysqlTargetSchemaReaderIT・PostgresTargetSchemaReaderIT・DslTargetDbIT も同じ）
```

C4 は 35 件（成功 6・飛ばし 26・失敗 3）、C5 は 10 件（飛ばし 9・失敗 1）で、`integrationTest` のタスクが失敗した。警告と SKIPPED は出ていた。

**診断**

- 4クラスは拡張を `@ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})` の順で登録していた（`AbstractTargetSchemaReaderIT` と `DslTargetDbIT`）。
- JUnit 5 は前処理を登録の順に、後処理を逆の順に呼ぶ。`ContainerRuntimeCheck.beforeAll` が `TestAbortedException` を投げると `OutputCaptureExtension.beforeAll` は呼ばれないが、`afterAll` は呼ばれるため、`OutputCapture.pop` が空の待ち行列で `NoSuchElementException` を出し、クラスの中断が失敗に変わっていた。
- `ContainerRuntimeCheck` だけを登録したクラスは正しく SKIPPED になっていた。単体テストの `ContainerRuntimeCheckTest` は判定の関数だけを確かめており、ほかの拡張との組み合わせを確かめていなかった。

**直したこと**（`8961cb2`、Code Generation に戻して）

1. 2か所の登録の順を `@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})` に入れ替えた。
2. 構造の検査 `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ExtensionOrderArchitectureTest.java`（3 件）を足した。両方の拡張を登録するテストのクラスすべてで `OutputCaptureExtension` が先であることを確かめ、違反があればクラスと順を示して失敗する（再発の防止と、不具合を再現するテスト。project.md の Mandated）。

**直した後**: C14 で 129 件・失敗 0・飛ばし 57、BUILD SUCCESSFUL。C12 の構造の検査も違反 0（2.8）。

### 3.2 U4-STORAGE: 内部DB（H2）のファイルが増え続けた（起動し直した後は直った。動いている間は Not Met のまま）

**直す前の症状**（C9 の2回目 `dsl-run4-extra`、C10 `dsl-run5-storage40`、`39f9aeb`）

- 投入→適用の1回ごとに H2 のファイル（`/app/data/mastersmith.mv.db`）が約 10.3MiB 増え、21 回＋プレビューで 282.0MiB（295.7MB）、40 回＋プレビューで 460.7MiB（483.1MB）。履歴 20 件の後も増え続け、**起動し直しても縮まなかった**。`dsl-run3-extra` でも同じ傾向（256.1MiB）。
- `memory.peak` が上限 2g ちょうど（2,048MiB）に達した。OOMKilled なし。

**診断**: 内部DB の接続先 `jdbc:h2:file:./data/mastersmith` が終了時の詰め直しを指定していなかった。H2 のファイルは、消した行の場所を動いている間に詰め直さず、`SHUTDOWN COMPACT`・`SHUTDOWN DEFRAG`（または `DEFRAG_ALWAYS`）で閉じないと小さくならない（Loop-Back Log の診断）。

**直したこと**（`8961cb2`）: 内部DB の既定の接続先を `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` にした（`backend/src/main/resources/application.yaml`・README の環境変数の表・`.env.example`）。`backend/src/test/java/cherry/mastersmith/config/H2DefragOnCloseTest.java`（3 件）で、付けたときは閉じた後にファイルが生きているデータの量まで縮み、付けないときは開き直しても縮まないこと、既定の接続先に付いていることを確かめる。測る台本（`perf/dsl-timing.sh`）と手順（`perf/README.md`）に、止めるのにかかる時間・終わり方・データの無事の確かめと、接続先を比べる `PERF_DB_URL` を足した。

**直した後**（C16〜C18、`8961cb2`、PostgreSQL の使い捨ての環境、上限 CPU 4・メモリ 2g）

| 実行 | 21 回目 | プレビューを置いた後（最大） | 起動し直した後 | 止めるのにかかった時間 | データの比べ |
|---|---|---|---|---|---|
| `dsl-lb1-storage21-defrag`（`DEFRAG_ALWAYS` あり） | 267.4MB | 278.2MB | **15.9MB** | 0.88 秒 | 一致 |
| `dsl-lb1-storage21-defrag-2`（同じ、2回目） | 267.4MB | 278.2MB | **15.9MB** | 0.93 秒 | 一致、戻し 20/20 一致 |
| `dsl-lb1-storage40-defrag`（40 回） | 267.4MB | 483.1MB（40 回＋プレビュー） | **15.9MB** | 1.00 秒 | 一致、戻し 20/20 一致 |
| `dsl-lb1-storage21-nodefrag`（`DEFRAG_ALWAYS` なし） | 267.4MB | 278.2MB | 278.2MB（縮まない） | 0.55 秒 | 一致、戻し 20/20 一致 |

- **動いている間（U4-STORAGE-RUN）**: 1回に約 10.78MB 増え、履歴が 20 件で止まった後も頭打ちにならない（40 回で 483.1MB）。`DEFRAG_ALWAYS` は閉じるときだけ効くため、動いている間の増え方は直す前と同じ。期待（プレビュー1件と履歴 20 件がすべて 10MB の最大の状態で約 210MB）を超えるため **Not Met**。
- **起動し直した後（U4-STORAGE-RESTART）**: `DEFRAG_ALWAYS` ありでは 21 回・40 回とも 15.9MB に縮んだ。なしでは 278.2MB のまま。止めるのに 0.55〜1.00 秒（`stop_grace_period` 45 秒の内）、終わり方は ExitCode 143（SIGTERM で正常に終了）、OOMKilled なし、起動から healthy まで約 8.7 秒。止める前後で、適用中の DSL とプレビューの SHA-256、プレビューの識別、履歴 20 件の版と `dslHash` が一致し、履歴のすべての版の戻しで本文の SHA-256 が `dslHash` と 20/20 一致した。**Met**。
- **ただし**: 試験の 10MB の DSL は gzip で 10MB から約 0.29MB に縮む（繰り返しの多い）データである。圧縮の効かない本文では、詰め直した後も生きているデータの量（約 210MB＋α）に近づくと推定している（**実測ではない**）。
- **メモリ**: 動いている間は `memory.peak` が上限 2g に張り付く（ページキャッシュ込み）。プロセスのメモリ（`anon`）の最大は 1,959〜1,986MB（上限の約 92%）。OOMKilled なし。

**影響**: 10MB の DSL の投入と適用を重ねる使い方では、アプリを止めるまで内部DB のファイルが増え続け、コンテナのボリュームを圧迫する（止めて起動し直せば生きているデータの量に戻る）。監査ログと同じファイルのため、ファイルを消して直すことはできない（README の「消してはいけない操作」）。

**残りの選べる手**（依頼者の判断が要る）: 動いている間の詰め直し（例: 適用の後に詰め直す操作を入れる）、履歴の本文の持ち方の見直し（別の表・圧縮など）、または既知の制約として記録して後の Intent で直す（目標は緩めない）。

### 3.3 在ステージの台本の手直し（`dsl-run3-extra`）

- `perf/ui/dsl-ui-lang.mjs` が、画面の要素の待ちで時間切れ（`locator.waitFor: Timeout 60000ms exceeded`、`pass: false`、手順は1つも記録されず）になった。画面は開いたときにプレビューの表示（重い処理）を読みに行き、読み終わる前に投入すると 503 `DSL_BUSY` になるため、台本の待ち合わせを直した（最初の読み込みが終わるのを待ってから投入する。`perf/README.md` の注に記載）。
- 直した台本の2回目（`dsl-run4-extra`）で `pass: true`。アプリのコードは変えていない。

## 4. 既知の観察事項

- **`8961cb2` で流し直していないもの**: E2E（C3）と1回ずつの時間（C7〜C9）。`8961cb2` の本番の変更は内部DB の接続先に `DEFRAG_ALWAYS=TRUE` を足したこと（閉じるときだけ効く）だけで、C16〜C18 の同じ台本の生成・投入・表示・戻しの測定も通っている。E2E は統合の前に `./gradlew e2eTest` で流し直す。
- **古い記述との差**（目標は緩めずに実測で判定した。`performance-test-instructions.md` 5節）: 既定の DSL は約 5.3MB（試算）・約 6.7MB（U3 の記録）ではなく 6.15〜6.38MB（コメントあり）。U4 の要件の「生成は接続 5 秒」は実装では 3 秒。決定 B により照合は応答しない対象DB で最悪 23〜28 秒。
- **k6 の結果は判定に使わない**: C11 は台本が動くことの確かめで、NFR1.10 の条件（履歴 20 件・想定の規模の DSL が適用中とプレビュー中）をそろえていない。
- **性能の測定の資源の条件**: 上限 2g で測った（Q4: A）。NFR1.12 の条件 1g とは違う。保存の量の測定でも `anon` が上限の約 92% に達しており、1g では余裕が無い見込みが高い。
- **SpotBugs の警告**: priority 2・3 の 109 件は統合を止めない基準の内で、前の Intent から数が増えている（この Intent の `record` の一覧の受け渡しや管理の API の口など）。


## 5. 受け入れた失敗（依頼者の判断）

- **対象**: U4-STORAGE-RUN（動いている間の内部DB のファイルの最大）。Not Met のまま。
- **判断**: Loop-back 1 の後の halt-and-ask（Loop-backs used: 1/3）で、依頼者が「Accept failure」を選んだ（2026-09-25）。示した直し方の候補は、A. H2 の保存の設定（`RETENTION_TIME`・`AUTO_COMPACT_FILL_RATE` など）で動いている間に空きを再利用させる、B. DSL の本文を圧縮して保存する、C. A と B の両方、D. 既知の制約として受け入れる。
- **記録**: README の「DSL の管理」の節に「既知の制約（動いている間の内部DB のファイルの大きさ）」を足した（投入と適用のたびに本文の大きさの分ずつ増え、止めると詰め直される。大きな DSL の投入と適用を重ねたら起動し直す）。承認済みの要件の文書は書き換えない（project.md の決まり）。
- **目標は緩めていない**: 期待「最大約 210MB が H2 のファイルとコンテナの上限の内に収まる」は、動いている間は満たさない（21 回で約 278MB、40 回で約 483MB）。起動し直した後は満たす（U4-STORAGE-RESTART は Met）。
- **残る危険**: 起動し直さずに大きな DSL の投入と適用を重ねると、ディスクの使用量が増え続ける。コンテナのメモリ（プロセス分）は上限 2g の約 92% で、止まってはいない。
- **その後の確かめ**: 直した後の WAR（8961cb2）で `./gradlew e2eTest` を流し直し、6 件すべて成功（18.7 秒、1 worker、010→020→030→040）。

## Loop-Back Log

### Loop-back 1 — 2026-09-24T13:58:42Z

- **Decision**: 依頼者の選択「Retry with fix」（依頼者の言葉: 「Code Generation に戻って両方直す」）。
- **Diagnosis**:
  1. NFR12.3: `AbstractTargetSchemaReaderIT`（MySQL・MariaDB・PostgreSQL の3クラス）と `DslTargetDbIT` は `@ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})` の順で拡張を登録している。コンテナの実行環境に届かないと `ContainerRuntimeCheck.beforeAll` がテストを飛ばす例外を投げ、`OutputCaptureExtension.beforeAll` が呼ばれないまま `afterAll` の `OutputCapture.pop` が空の待ち行列で `NoSuchElementException` を出し、クラスが `initializationError` で失敗する。
  2. U4-STORAGE: 内部DB（H2）の接続先 `jdbc:h2:file:./data/mastersmith` は終了時の詰め直しを指定していない。10MB の DSL の適用を重ねると、古い履歴が消えても H2 のファイルが毎回約 10.3MB 増え（21 回で 282MB、40 回で 461MB）、起動し直しても縮まない。H2 のファイルは `SHUTDOWN COMPACT`・`SHUTDOWN DEFRAG` を実行しないと小さくならない（依頼者の知見）。コンテナの `memory.peak` も上限 2,048MB に達した。
- **Root-cause stage**: code-generation（1 は U1・U4 のテストのコード、2 は U4 で内部DB に 10MB の本文を保存する形にしたときの接続先の設定）。
- **Planned fix**:
  1. 2か所の登録の順を `{OutputCaptureExtension.class, ContainerRuntimeCheck.class}` に入れ替える。再現のテスト（JUnit の `EngineTestKit` で、2つの拡張を付けたクラスを「Docker に届かない」状態で走らせ、失敗ではなく飛ばされたになること）を同じ変更に含める。
  2. 内部DB の既定の接続先に `;DEFRAG_ALWAYS=TRUE` を足す（`application.yaml`・README の環境変数の表・`.env.example`、コンテナの値も）。終了時の詰め直しで起動し直した後に縮むこと、動いている間の増え方、止めるのにかかる時間（`stop_grace_period: 45s` の内）とデータの無事を `perf/dsl-timing.sh --storage` で測り直す。動いている間の増え方が頭打ちにならなければ、その結果を持って依頼者に諮る。
- **Estimated impact**: 1 は作業 約30分・費用なし・危険は低い（テストのコードだけ）。2 は作業 1〜2時間（多くは測り直し）・費用なし・危険は中（止め方とデータの扱いに関わる）。

## Sources

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の3回の出力（`39f9aeb`、E2E と `perf/` の変更の後、`8961cb2`）、`./gradlew osvScan --rerun` の出力、`./gradlew e2eTest` の出力、コンテナの実行環境が無いときの3回の出力、VM のメモリの記録（いずれもセッションの一時ファイル）
- `backend/build/test-results/test/`・`backend/build/test-results/integrationTest/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`build/reports/osv-scanner/osv.json`
- `build/perf-results/dsl-run1/summary.md`・`build/perf-results/dsl-run2-ui/`・`build/perf-results/dsl-run3-extra/`・`build/perf-results/dsl-run4-extra/summary.md`・`build/perf-results/dsl-run5-storage40/`・`build/perf-results/dsl-k6-smoke/`
- `build/perf-results/dsl-lb1-storage21-defrag/`・`build/perf-results/dsl-lb1-storage21-defrag-2/`・`build/perf-results/dsl-lb1-storage40-defrag/`・`build/perf-results/dsl-lb1-storage21-nodefrag/`（`summary.md`・`postgres/stop-state.txt`・`postgres/state.txt`）
- `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ExtensionOrderArchitectureTest.java`・`backend/src/test/java/cherry/mastersmith/config/H2DefragOnCloseTest.java`・`backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java`・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`
- `backend/src/main/resources/application.yaml`・`perf/README.md`・`README.md`・`frontend/playwright.config.ts`・`frontend/e2e/040-dsl-admin.e2e.ts`・`backend/build.gradle.kts`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- U4-STORAGE-RUN（動いている間の増え方）の Not Met をどう扱うか（動いている間の詰め直しなどでさらに直すか、既知の制約として記録して先へ進むか）は依頼者の判断が要る。
- 圧縮の効かない本文での、詰め直した後の大きさ（約 210MB＋α の推定）は実測していない。
- E2E（C3）は `8961cb2` で流し直していない。
