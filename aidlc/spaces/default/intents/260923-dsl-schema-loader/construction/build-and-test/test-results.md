# 実行の記録（test-results）

Intent `260923-dsl-schema-loader` の Build and Test で実際に走らせたコマンドと、その結果。**ここに書く数字はすべて実測**であり、上流の文書の記述を写したものではない（project.md の Testing Posture）。

- 実行した日: 2026-09-24
- 実行の環境: macOS（darwin 25.5.0）、JDK 25（Temurin）、Node.js 24、Gradle Wrapper 9.7.1、colima の VM（CPU 4・メモリ 6GiB）、Chromium 153（Playwright）。配備したアプリ（プロジェクト `mastersmith`）は動かしたまま
- 対象のソース: 1回目はコミット `39f9aeb`。2回目は、その上に E2E の名前の見直しと `040-dsl-admin.e2e.ts`、`frontend/playwright.config.ts`（`workers: 1`）、`perf/` の台本、README、`docker/perf/compose.yaml` を足した未コミットの状態（アプリのソースは同じ）

## 1. 走らせたコマンドの一覧

| # | コマンド | 目的 | 結果 |
|---|---|---|---|
| C1 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`39f9aeb`） | 統合の前の関門、件数とカバレッジの実測。あわせて VM のメモリを 10 秒ごとに記録 | **成功**（4分21秒） |
| C2 | C1 と同じ（E2E と `perf/` の変更の後） | 変更の後の関門 | **成功**（4分14秒） |
| C3 | `./gradlew e2eTest` | ビルドした WAR での E2E（4ファイル） | **成功**（6 件、19.0 秒） |
| C4 | Docker の接続先（`DOCKER_HOST`）を存在しないソケットに向けて `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` | コンテナの実行環境が無いときの動き（NFR12.3、Q6: B）。colima は止めない（配備したアプリを止めないため） | **失敗**（35 件: 成功 6・飛ばし 26・失敗 3） |
| C5 | C4 と同じ向け先で、`DslTargetDbIT` と `dslmanage.generate` の IT に絞った `:backend:integrationTest` | 同上（U3・U4 の対象DB のテスト） | **失敗**（10 件: 飛ばし 9・失敗 1） |
| C6 | `./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl .` | 性能の測定用のイメージ（配備のタグ `local` は上書きしない） | 成功 |
| C7 | `MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres mysql mariadb` | 1回ずつの時間（3種類の対象DB、100 × 100） | 成功（`build/perf-results/dsl-run1/`） |
| C8 | `MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --ui postgres` | 画面の時間 | 成功（`build/perf-results/dsl-run2-ui/`） |
| C9 | `MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --lang --pattern --storage postgres`（2回） | 英語の表示・重い正規表現・保存の量 | 1回目は英語の表示の台本が時間切れ（`dsl-run3-extra`、3.3）。台本を直した2回目で完了（`dsl-run4-extra`） |
| C10 | C9 の `--storage` を `STORAGE_ROUNDS=40` で | 保存の量の増え方の再現（20 件を超えた後） | 完了（`build/perf-results/dsl-run5-storage40/`） |
| C11 | `k6 inspect`（全場面）と、`dslLight`・`dslCycle` を 10 秒ずつ（`grafana/k6:2.3.0`） | Performance Validation に渡す k6 の台本が動くことの確かめ（判定には使わない） | 成功（`build/perf-results/dsl-k6-smoke/`） |

- C4・C5 の後は Docker の接続先を元に戻した。C1・C2 は対象DB のテストを飛ばさずに（飛ばし 0）通っている。
- 性能の測定（C6〜C11）は、すべて使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`、番号 18080）に対して行い、終わったら `down -v` と一時ディレクトリの削除で片付けた。配備したアプリ・その内部DB・監査ログには要求を送っていない。

## 2. 結果の詳細

### 2.1 ビルド（C1・C2）

```
BUILD SUCCESSFUL in 4m 21s   （C1、2026-09-24T12:30:06Z〜12:34:28Z）
BUILD SUCCESSFUL in 4m 14s   （C2）
```

`verify` の 10 の段（0 準備〜9 成果物）がすべて通った。フォーマット・リンタ・ライセンスヘッダー・ビルド（Java のコンパイル、`tsc --noEmit`、Vite のビルド）の違反・誤りは 0 件。

### 2.2 テストの件数（C1・C2 で同じ結果）

| 種類 | テストのクラス・ファイル | 合計 | 成功 | 失敗 | 飛ばし |
|---|---|---|---|---|---|
| バックエンドの単体テスト（`*Test`） | 97 | **710** | 710 | **0** | 0 |
| バックエンドの結合テスト（`*IT`、対象DB 3種類を含む） | 73（入れ子のクラスを含む） | **375** | 375 | **0** | **0** |
| 画面のテスト（Vitest） | 47 | **313** | 313 | **0** | 0 |
| E2E（Playwright、C3） | 4 | **6** | 6 | **0** | 0 |
| **合計** | — | **1,404** | **1,404** | **0** | **0** |

単位ごとの内訳（バックエンドはパッケージで割り当て。`targetdb` は U1、`dsl` は U2、`dslmanage.generate` は U3、ほかの `dslmanage` は U4、`dslmanage.web.DslAccessControlIT` は U5 の一括の確かめ）。

| 単位 | 単体テスト | 結合テスト | 画面 | E2E |
|---|---|---|---|---|
| U1 対象DB | 80（9 クラス） | 50（8 クラス。3種類の DB） | — | — |
| U2 DSL の定義 | 89（11 クラス） | 2（`DslSchemaPublicationIT`） | — | — |
| U3 既定の DSL の生成 | 75（5 クラス） | 9（3種類の DB の生成） | — | — |
| U4 DSL の管理 | 62（8 クラス） | 39（6 クラス） | — | — |
| U5 DSL の管理画面 | — | 31（`DslAccessControlIT`） | `src/features/dsl/` の 16 ファイルと `src/shared/api-client/` の足したテスト | 1（`040-dsl-admin.e2e.ts`） |
| 前の Intent までの部品 | 404（64 クラス） | 244（54 クラス） | 残り | 5 |

- テストの時間の合計（JUnit の報告の和）は単体 約 75 秒・結合 約 192 秒。
- Vitest の出力に jsdom の `Not implemented: HTMLCanvasElement's getContext()` が 25 回出る。jsdom に canvas が無いことの通知で、テストの結果には影響しない。
- 結合テストの終わりに OTLP の送り先に届かない WARN が出るのは、外部エクスポートを有効にする既存のテスト（`ExternalExportIT`）の想定どおりの出力である。

### 2.3 カバレッジ（C2 が生成した報告から読み取った実測）

バックエンド（JaCoCo、単体＋結合、`backend/build/reports/jacoco/test/jacocoTestReport.xml`）

| 範囲 | 行 | 分岐 | 下限 |
|---|---|---|---|
| **全体** | **98.1%**（3884/3960） | **94.1%**（1349/1433） | 行 80%・分岐 70% |

新しいパッケージ（13 個、パッケージごとにも下限を当てる）

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

- U1 の実測で単独で下回っていた `audit.service` は、今回 行 80.9%（76/94）・分岐 83.3%（10/12）で下限を上回った。
- カバレッジの除外は増えていない。`backend/build.gradle.kts` のこの Intent での差は、依存の除外（`jackson-dataformat-yaml` など）と `packagesJudgedByTotal` だけで、計測の除外（起動クラス・設定値だけのクラス・自動生成・`vendor/`）は変わっていない。

画面（`@vitest/coverage-v8`）

| 範囲 | 文 | 分岐 | 関数 | 行 |
|---|---|---|---|---|
| **全体** | 97.76%（1004/1027） | **93.6%**（585/625） | 98.1%（311/317） | **97.86%**（961/982） |
| `features/dsl` | 97.1% | 92.28% | 98.62% | 96.98% |
| `features/dsl/api` | 98.43% | 97.82% | 100% | 98.36% |
| `shared/api-client` | 98.61% | 97.56% | 100% | 98.59% |

### 2.4 安全の検査（C1・C2 の 8 の段）

| 検査 | 結果 |
|---|---|
| Gitleaks | 141 コミット・約 15.65MB を走査、`no leaks found` |
| SpotBugs＋FindSecBugs（`spotbugsGate`） | priority 1 は 0 件、パターン名が `SQL_` で始まるものは 0 件。priority 2 が 66 件・priority 3 が 43 件の警告（統合は止めない） |
| OSV-Scanner（`osvScan`） | 2回とも UP-TO-DATE（lockfile が変わっていない）。使った結果（`build/reports/osv-scanner/osv.json`、2026-09-24 13:34）は該当 0 件 |

### 2.5 成果物（C1・C2 の 9 の段）

| 確かめ | 結果 |
|---|---|
| 実行可能 WAR（`bootWar`） | `backend/build/libs/mastersmith.war`（画面の `dist` を同梱） |
| WAR の中の JSON Schema（`verifyDslSchemaInWar`） | 成功（`WEB-INF/classes/static/dsl/dsl-schema-v1.json` が正本と一致） |
| 初回の読み込みの JavaScript（`frontendBundleSize`） | **111.4 KB（gzip）**（`assets/index-Cyfbom38.js`）。目安 500KB 以内 |
| 第三者のライセンスの文書 | MariaDB Connector/J の LGPL 2.1 の文書が `WEB-INF/classes/META-INF/third-party-licenses/` にある。MySQL Connector/J の jar に `LICENSE`、PostgreSQL JDBC の jar に `META-INF/LICENSE` がある |

### 2.6 E2E（C3）

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
| `verify` 全体の時間 | 4分21秒（C1）・4分14秒（C2） |
| 結合テストの時間（JUnit の報告の和） | 約 192 秒 |
| colima の VM のメモリの使用量（C1 の間、10 秒ごと） | 最小 872MiB・最大 **1,424MiB**（空き 4,485MiB 以上）。配備したアプリを動かしたまま |

依頼者の決定（Q1: A）により、対象DB の結合テストは今のまま3種類とも `./gradlew verify` の中で毎回実行する（CI も同じ）。

### 2.8 コンテナの実行環境が無いときの動き（C4・C5、NFR12.3）

| 実行 | 合計 | 成功 | 飛ばし（SKIPPED） | 失敗 |
|---|---|---|---|---|
| C4（`targetdb.*`） | 35 | 6 | 26（`MysqlSchemaQueriesIT`・`MariadbSchemaQueriesIT`・`PostgresSchemaQueriesIT`） | 3（`MysqlTargetSchemaReaderIT`・`MariadbTargetSchemaReaderIT`・`PostgresTargetSchemaReaderIT` の `initializationError`） |
| C5（`DslTargetDbIT`・`dslmanage.generate` の IT） | 10 | 0 | 9（`DefaultDslGeneratorMysqlIT`・`DefaultDslGeneratorMariadbIT`・`DefaultDslGeneratorPostgresIT`） | 1（`DslTargetDbIT` の `initializationError`） |

- 警告「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない（colima を起動してやり直す）」は出た（WARN と標準エラー）。飛ばしたテストは SKIPPED として出力に出た（黙って飛ばしていない）。
- しかし4クラスが失敗になり、`integrationTest` のタスクが失敗した。**NFR12.3 は Not Met**（3.1）。
- C4 の成功 6 件は、コンテナを使わない U1 の結合テスト（`TargetDbStartupIT`・`TargetDbSecretLeakIT`）である。

### 2.9 性能（C7〜C11）

結果の一覧と判定は `performance-test-instructions.md` 2.2。要点は次のとおり（上限 CPU 4・メモリ 2g）。

| Target ID | 実測 | 判定 |
|---|---|---|
| NFR1.6 | 生成 1.54〜2.64 秒（3種類） | Met |
| NFR1.7 | 読み取り 25〜296ms・組み立てと書き出し 426〜767ms・検証 419〜832ms | Met |
| NFR1.4 | 検証 419〜832ms（6,146,161〜6,383,161 バイト） | Met |
| NFR2.5 | 6,146,161〜6,383,161 バイト（10MB の内） | Met |
| NFR1.5 | 10MB の投入 0.82〜1.14 秒、区間のヒープの最大 1,237MB | Met |
| NFR1.8 | 表示 0.045〜0.200 秒・投入 0.57〜1.04 秒・戻し 0.54〜1.00 秒 | Met |
| NFR1.11 | 0.032〜0.038 秒 | Met |
| NFR1.18 | 0.10〜0.29 秒（今の状態とプレビュー、照合を含む） | Met |
| NFR1.19 | 25ms | Met |
| NFR1.20 | 553〜664ms | Met |
| U2-PATTERN-COMPILE | 重い投入 0.34〜0.44 秒、直後の普通の投入 0.228 秒、打ち切りのログ 0 件 | Met |
| U5-LANG-E2E | `pass: true` | Met |
| U4-STORAGE | H2 のファイル 282MB（21 回＋プレビュー）、40 回で 461MB。`memory.peak` 2,048MB | **Not Met** |

k6 の動作の確かめ（C11。条件をそろえていないため判定に使わない）: `dslLight` は 61,407 要求で失敗 0、今の状態 p95 1.1ms・履歴 p95 1.07ms。`dslCycle` は 49 要求で失敗 0、適用 p95 4.71ms・破棄 p95 10.15ms。

## 3. 失敗の詳細

### 3.1 NFR12.3: コンテナの実行環境が無いときに4クラスが失敗する

**症状**（C4・C5）

```
MariadbTargetSchemaReaderIT > initializationError FAILED
    java.util.NoSuchElementException
        at java.base/java.util.ArrayDeque.removeLast(ArrayDeque.java:370)
        at org.springframework.boot.test.system.OutputCapture.pop(OutputCapture.java:81)
        at org.springframework.boot.test.system.OutputCaptureExtension.afterAll(OutputCaptureExtension.java:106)
        at java.base/java.util.ArrayList.forEach(ArrayList.java:1604)
（MysqlTargetSchemaReaderIT・PostgresTargetSchemaReaderIT・DslTargetDbIT も同じ）
```

**診断**

- 失敗する4クラスは、拡張を `@ExtendWith({ContainerRuntimeCheck.class, OutputCaptureExtension.class})` の順で登録している（`backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java` の 75 行、`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java` の 70 行）。
- `ContainerRuntimeCheck.beforeAll` が `TestAbortedException` を投げると、後に登録した `OutputCaptureExtension.beforeAll`（出力の捕まえ口を積む）は呼ばれない。一方で `afterAll` は登録したすべての拡張で呼ばれるため、`OutputCapture.pop` が空の `ArrayDeque` から取り出そうとして `NoSuchElementException` になり、クラスの中断が失敗に変わる。
- `ContainerRuntimeCheck` だけを登録したクラス（`AbstractSchemaQueriesIT`・`AbstractDefaultDslGeneratorIT` の子）は正しく SKIPPED になった。単体テストの `ContainerRuntimeCheckTest` は判定の関数（`decide`）だけを確かめており、ほかの拡張との組み合わせは確かめていない。
- 影響: 開発中にコンテナの実行環境が無いと、警告は出るが検査全体が「失敗」になり、「警告を出して対象DB のテストだけを飛ばす」という team.md の Way of Working の決まりどおりにならない。コンテナの実行環境がある通常の `verify` と CI（`CI=true` では飛ばさず失敗させる）には影響しない。

**直す候補**（テストのコードだけ。この段では直していない）

1. 4クラスの登録の順を `@ExtendWith({OutputCaptureExtension.class, ContainerRuntimeCheck.class})` にする（出力の捕まえ口を先に積み、中断の後の `afterAll` で正しく外す）。
2. 不具合を再現するテストを足す（project.md の Mandated）。例: JUnit の `EngineTestKit` で、Docker に届かない状態を与えた入れ子のテストのクラスを実行し、失敗ではなく中断（aborted）になることを確かめる。
3. 見積もり: 作業 約 30 分、費用なし、危険は低い（本番のコードに触れない）。

### 3.2 U4-STORAGE: 内部DB（H2）のファイルが履歴の上限の後も増え続ける

**症状**（C9 の2回目 `dsl-run4-extra`）

| 時点 | H2 のファイル（MB） | 履歴 | `memory.current`（MB） | `memory.peak`（MB） | `anon`（MB） |
|---|---|---|---|---|---|
| 始め | 55.8 | 2 | 837.2 | 1,730.4 | 775.0 |
| 10 回目 | 158.7 | 12 | 2,023.0 | 2,027.1 | 1,852.7 |
| 18 回目（履歴 20 件に到達） | 240.9 | 20 | 2,044.4 | 2,048.2 | 1,888.9 |
| 21 回目 | 271.8 | 20 | 2,044.3 | 2,048.2 | 1,901.2 |
| 最後にプレビュー1件 | **282.0** | 20 | 2,044.3 | **2,048.2** | 1,903.4 |
| 起動し直した後 | **282.0** | 20 | 1,061.8 | 1,066.1 | 1,055.8 |

- 投入→適用の1回ごとに、H2 のファイル（`/app/data/mastersmith.mv.db`）が約 10.3MB 増えた。履歴が 20 件に達して古い版が消えるようになった後も同じ割合で増え続け、起動し直しても縮まなかった。
- 期待（`u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` 3.4節・`u4-dsl-management/nfr-requirements/scalability-requirements.md` 1節）は、プレビュー1件と履歴 20 件がすべて 10MB の最大の状態で約 210MB。
- 再現: `dsl-run3-extra`（21 回＋プレビューで 256.1MB）、`dsl-run5-storage40`（40 回＋プレビューで **460.7MB**、履歴は 20 件のまま）でも同じ傾向だった（project.md の Testing Posture の決まりどおり、やり直して再現を確かめた）。
- コンテナのメモリ: 12 回目以後、`memory.peak` が上限 2g ちょうど（2,048MB）に達し、`memory.current` も 2,044MB 前後に張り付いた。プロセスのメモリ（`anon`）は約 1,900MB、ページキャッシュ（`file`）は 130〜170MB。3回とも OOMKilled なし（`state.txt` は `OOMKilled=false ExitCode=0 Status=running`）、応答の時間も 0.98〜1.24 秒のまま悪くならなかった。

**診断**

- ファイルの増え方は「書いた量」に比例し、「持っている量」（プレビュー1件＋履歴 20 件）に比例しない。H2 の保存の仕組み（MVStore）が、削除した版の空き領域を再利用・詰め直ししていないと見られる。**これは未確認の仮説**で、H2 の設定（詰め直しの条件・書き込みの頻度）と、適用で履歴へ写す `INSERT ... SELECT` の書き方との関係を確かめていない。
- メモリが上限に張り付く原因（ヒープの大きさ・H2 のキャッシュ・ページキャッシュの内訳）も確かめていない。NMT の測定は Performance Validation の NFR1.12 で行う。

**影響**

- 10MB の DSL を何度も適用する使い方では、内部DB のファイルが際限なく大きくなり、コンテナのボリュームを圧迫する。監査ログと同じファイルのため、ファイルを消して直すことはできない（README の「消してはいけない操作」）。
- 想定の規模（約 6MB の DSL を時々適用）では増え方はゆるやかだが、上限は無い。

**選べる手**（依頼者の判断が要る。この段では直していない）

- H2 の詰め直し（例: 適用・履歴の削除の後や起動時に詰め直す設定・操作）を入れ、ファイルが約 210MB の内に収まることを `perf/dsl-timing.sh --storage` で確かめる。
- 履歴の本文の持ち方を見直す（例: 大きな本文を別の表や圧縮で持つ）。
- 目標は緩めずに、既知の制約として記録して後の Intent で直す。

### 3.3 在ステージの台本の手直し（`dsl-run3-extra`）

- `perf/ui/dsl-ui-lang.mjs` が、画面の要素の待ちで時間切れ（`locator.waitFor: Timeout 60000ms exceeded`、`pass: false`、手順は1つも記録されず）になった。画面は開いたときにプレビューの表示（重い処理）を読みに行き、読み終わる前に投入すると 503 `DSL_BUSY` になるため、台本の待ち合わせを直した（最初の読み込みが終わるのを待ってから投入する。`perf/README.md` の注に記載）。
- 直した台本の2回目（`dsl-run4-extra`）で `pass: true`。アプリのコードは変えていない。

## 4. 既知の観察事項

- **古い記述との差**（目標は緩めずに実測で判定した。`performance-test-instructions.md` 5節）: 既定の DSL は約 5.3MB（試算）・約 6.7MB（U3 の記録）ではなく 6.15〜6.38MB（コメントあり）。U4 の要件の「生成は接続 5 秒」は実装では 3 秒。決定 B により照合は応答しない対象DB で最悪 23〜28 秒。
- **k6 の結果は判定に使わない**: C11 は台本が動くことの確かめで、NFR1.10 の条件（履歴 20 件・想定の規模の DSL が適用中とプレビュー中）をそろえていない。
- **性能の測定の資源の条件**: 上限 2g で測った（Q4: A）。NFR1.12 の条件 1g とは違う。
- **SpotBugs の警告**: priority 2・3 の 109 件は統合を止めない基準の内で、前の Intent から数が増えている（この Intent の `record` の一覧の受け渡しや管理の API の口など）。


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

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の2回の出力（1回目は `39f9aeb`、2回目は変更の後）、`./gradlew e2eTest` の出力、コンテナの実行環境が無いときの2回の出力、VM のメモリの記録（いずれもセッションの一時ファイル）
- `backend/build/test-results/test/`・`backend/build/test-results/integrationTest/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`build/reports/osv-scanner/osv.json`
- `build/perf-results/dsl-run1/summary.md`・`build/perf-results/dsl-run2-ui/`・`build/perf-results/dsl-run3-extra/`・`build/perf-results/dsl-run4-extra/summary.md`・`build/perf-results/dsl-run5-storage40/`・`build/perf-results/dsl-k6-smoke/`
- `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`・`backend/src/test/java/cherry/mastersmith/targetdb/service/AbstractTargetSchemaReaderIT.java`・`backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`
- `perf/README.md`・`README.md`・`frontend/playwright.config.ts`・`frontend/e2e/040-dsl-admin.e2e.ts`・`backend/build.gradle.kts`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`
- `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- NFR12.3 と U4-STORAGE の2件の Not Met をどう扱うか（この段で直すか、既知の制約として記録して先へ進むか）は依頼者の判断が要る。
- U4-STORAGE の原因は未確認の仮説である（3.2）。
