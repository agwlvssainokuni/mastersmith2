# ビルドとテストの要約（build-and-test-summary）

Intent `260923-dsl-schema-loader`（dsl-schema-loader）の全5単位（U1 対象DB、U2 DSL の定義、U3 既定の DSL の生成、U4 DSL の管理、U5 DSL の管理画面）を通した、ビルド・テストの状態と、測るべき品質の目標（107 件）の検証の表。Loop-back 1（Code Generation で直した後、コミット `8961cb2`）の結果で更新した。

- Scope: classic、Depth: Standard、Test Strategy: **Standard**（`aidlc/spaces/default/intents/260923-dsl-schema-loader/aidlc-state.md`）
- 実行した日: 2026-09-24（Loop-back 1 の後の実行も同じ日。今の判定はコミット `8961cb2` による）
- 実行の環境: macOS（darwin 25.5.0）、JDK 25、Node.js 24、Gradle 9.7.1、colima の VM（CPU 4・メモリ 6GiB）
- 依頼者の決定: `build-and-test-questions.md` の Q1〜Q9 と Consolidated Summary Confirmation（Looks correct）

## 1. 全体の状態

| 区分 | 結果 |
|---|---|
| 1コマンドの検査（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`） | **成功**。Loop-back 1 の後（`8961cb2`）6分17秒。最初の実行は `39f9aeb` で 4分21秒、E2E と `perf/` の変更の後で 4分14秒 |
| バックエンドの単体テスト（`*Test`） | **716 件すべて成功**（失敗 0、飛ばし 0。Loop-back 1 で `ExtensionOrderArchitectureTest` 3 件・`H2DefragOnCloseTest` 3 件を足した） |
| バックエンドの結合テスト（`*IT`、対象DB 3種類を含む） | **375 件すべて成功**（失敗 0、飛ばし 0） |
| 画面のテスト（Vitest） | **313 件すべて成功**（47 ファイル） |
| E2E（Playwright、`./gradlew e2eTest`） | **6 件すべて成功**（4 ファイル、1 worker、010 → 040 の順、19.0 秒） |
| バックエンドのカバレッジ | 行 **98.1%**（3884/3960）・分岐 **94.1%**（1349/1433）。新しいパッケージ 13 個もすべて下限以上（最低は `dslmanage.web` の分岐 86.7%） |
| 画面のカバレッジ | 行 **97.86%**（961/982）・分岐 **93.6%**（585/625） |
| 秘密情報の検出（Gitleaks） | 0 件（143 コミット） |
| Java の静的解析（SpotBugs＋FindSecBugs） | priority 1 は 0 件、`SQL_` は 0 件。priority 2・3 の警告のみ（統合は止めない） |
| 依存関係の脆弱性（OSV-Scanner） | `./gradlew osvScan --rerun` で走査し直し、脆弱性 0（`backend/gradle.lockfile` 244・`frontend/package-lock.json` 395・`vendor/make-you-chic-ui/package-lock.json` 404 パッケージ） |
| 初回の読み込みの JavaScript | 111.4 KB（gzip） |
| colima の VM のメモリの使用量（`verify` の間） | 最大 約 1,424MiB（配備したアプリを動かしたまま） |
| 性能（1回ずつの時間と保存の量、上限 CPU 4・メモリ 2g） | 14 件（画面の時間・英語の表示・保存の量の2行を含む）を測り、13 件が Met、U4-STORAGE-RUN（動いている間の H2 のファイルの大きさ）が **Not Met** |
| コンテナの実行環境が無いときの動き（NFR12.3） | Loop-back 1 で直した。129 件・失敗 0・飛ばし 57（10 クラスすべてに警告）、BUILD SUCCESSFUL で **Met** |
| Target Verification Matrix | **Met 93・Not Met 1・Unverified 13**（107 件。U4-STORAGE を2行に分けた） |

## 2. 作った手順書（テストの種類の一覧）

| 手順書 | 種類 | 作った理由 |
|---|---|---|
| `build-instructions.md` | ビルド | ステージの成果物。コンテナの実行環境（colima と `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`）が必須になった |
| `integration-test-instructions.md` | 結合テスト・E2E | Standard が求める。単位をまたぐ境界（C1〜C8）と E2E（010〜040、`workers: 1`） |
| `performance-test-instructions.md` | 性能 | U2〜U5 が測れる時間と資源の目標を持つため。Q2: B で Performance Validation への引き継ぎを含む |
| `security-test-instructions.md` | 安全（静的解析・秘密情報・脆弱性・ライセンス・振る舞い） | DSL の信頼できない入力・対象DB の接続情報・管理者だけの API の3つの危険の面を持つため |
| `test-results.md` | 実行の記録 | ステージの成果物 |
| `cross-unit-traceability.md` | 単位をまたぐ要件の網羅（判定 Pass） | 既にあるもの（この段では書き換えない） |

単体テストは各単位の `code-generation/` の記録が受け持つ（この段では作り直さない）。

## 3. 単位ごとのカバレッジ

C2 の JaCoCo の報告を、単位のパッケージごとに合わせた値（C12（`8961cb2`）は本番のコードが同じで、全体の値も同じ）。テストの件数は `test-results.md` 2.2。

| 単位 | 範囲 | 行 | 分岐 | テスト（単体・結合・画面・E2E） |
|---|---|---|---|---|
| U1 対象DB | `targetdb.*`（4 パッケージ） | 98.5%（393/399） | 96.1%（174/181） | 83・50・—・— |
| U2 DSL の定義 | `dsl.*`（4 パッケージ） | 98.6%（849/861） | 93.4%（396/424） | 89・2・—・— |
| U3 既定の DSL の生成 | `dslmanage.generate` | 99.3%（303/305） | 100%（145/145） | 75・9・—・— |
| U4 DSL の管理 | `dslmanage.domain`・`repository`・`service`・`web` | 100%（841/841） | 95.8%（184/192） | 62・39・—・— |
| U5 DSL の管理画面 | `frontend/src/features/dsl/`（と `features/dsl/api`） | 96.98%（`features/dsl`）・98.36%（`api`） | 92.28%・97.82% | —・31（`DslAccessControlIT`）・16 ファイル・1 |

- 既存のパッケージで単独で下限を下回るのは `auth.repository`（分岐 50.0%、1/2）と `common.health`（行 79.2%、42/53）。`packagesJudgedByTotal` で全体の合計で判定する（Q9: A）。
- カバレッジの除外は増えていない（G-COV-EXCL）。

## Target Verification Matrix

対象は、この段の Step 1 で集めた測るべき品質の目標 106 件に、Loop-back 1 で U4-STORAGE を「動いている間の最大」（U4-STORAGE-RUN）と「起動し直した後」（U4-STORAGE-RESTART）の2行に分けた 1 件を足した 107 件（統合の関門と成果物 19、性能と資源 13、単位のテストで確かめる非機能 61、画面の時間と E2E 4、手での確認 7、後の段が持つもの 3）。判定は `Met`（満たす）・`Not Met`（満たさない）・`Unverified`（この段では確かめず、持ち主の段か人が持つ）の3つだけで、`Pending` は残さない。

表記:

- Source の単位の文書は `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/` の下（例: `u1-target-db/nfr-requirements/tech-stack-decisions.md`）。`req` は `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/requirements-analysis/requirements.md` の 3章、`a11y` は `aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/refined-mockups/accessibility-checklist.md`。`team.md`・`project.md` は `aidlc/spaces/default/memory/` の下。
- Evidence の「C1・C2」は `test-results.md` 1節の最初の2回の `verify`、「C12」は Loop-back 1 の後（`8961cb2`）の `verify` で、3回とも成功。単位のテストは C12 でも同じく成功している。性能の結果は `build/perf-results/` の下。

### A. 統合の関門と成果物（19 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| G-FORMAT | team.md の Code Style・Deployment | フォーマットの違反 0 件（palantir-java-format・Prettier、`vendor/` は対象外） | 違反 0 件（`verifyFormat` 成功） | C1・C2 の `verifyFormat` | build-and-test | Met |
| G-LINT | team.md の Code Style・`u5-dsl-admin-ui/nfr-requirements/security-requirements.md` NFR3.9 | リンタの error 0 件（oxlint・ESLint・Stylelint、`react/no-danger` を含む） | error 0 件 | C1・C2 の `verifyLint` | build-and-test | Met |
| G-LICENSE | team.md の Code Style・project.md の Mandated | すべてのソースに Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni） | 違反 0 件 | C1・C2 の `verifyLicense` と `verifyFormat`（Spotless の licenseHeader） | build-and-test | Met |
| G-BUILD | team.md の Deployment | Java のコンパイル・`tsc --noEmit`・Vite のビルドが成功 | 成功 | C1・C2 の `verifyBuild` | build-and-test | Met |
| G-TEST | team.md・project.md の Testing Posture | 単体・結合・画面のテストがすべて成功（失敗 0・飛ばし 0） | 単体 716・結合 375・画面 313、失敗 0・飛ばし 0（C12、`8961cb2`） | `backend/build/test-results/`・`test-results.md` 2.2 | build-and-test | Met |
| NFR11-BE | req の NFR11・team.md の Testing Posture | バックエンド全体で行 80% 以上・分岐 70% 以上 | 行 98.1%（3884/3960）・分岐 94.1%（1349/1433） | `backend/build/reports/jacoco/test/jacocoTestReport.xml`・`test-results.md` 2.3 | build-and-test | Met |
| NFR11-BE-PKG | team.md の Testing Posture・`u1-target-db/code-generation/code-summary.md` 4節 | 新しいパッケージ 13 個がそれぞれ行 80%・分岐 70% 以上。既存の 22 個は全体の合計で判定 | 13 個すべて以上（最低 `dslmanage.web` の分岐 86.7%）。既存で単独で下回るのは `auth.repository`（分岐 50.0%）・`common.health`（行 79.2%）で、全体の合計で判定（Q9: A） | 同上（`jacocoTestCoverageVerification` 成功） | build-and-test | Met |
| NFR11-FE | req の NFR11・team.md の Testing Posture | 画面全体で行 80%・分岐 70% 以上 | 行 97.86%（961/982）・分岐 93.6%（585/625） | `frontend/coverage/`・`test-results.md` 2.3 | build-and-test | Met |
| G-COV-EXCL | team.md の Testing Posture | カバレッジの除外が既存のもの（起動クラス・設定値だけのクラス・自動生成・`vendor/`）から増えていない | 増えていない。`backend/build.gradle.kts` の差は依存の除外と `packagesJudgedByTotal` だけ | `backend/build.gradle.kts`・`frontend/vitest.config.ts` | build-and-test | Met |
| NFR6.3 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・req の NFR6・team.md の Code Style | SpotBugs＋FindSecBugs の priority 1 の指摘 0 件、`SQL_` で始まる指摘 0 件 | priority 1 は 0 件・`SQL_` は 0 件（priority 2 が 66 件・priority 3 が 43 件は警告） | C1・C2 の `spotbugsGate`・`backend/build/reports/spotbugs/main.xml` | build-and-test | Met |
| G-OSV | team.md の Code Style・Deployment・project.md の Mandated | 依存の脆弱性で統合を止めるもの 0 件（`vendor/make-you-chic-ui` の lockfile を含む） | `./gradlew osvScan --rerun` で走査し直し（UP-TO-DATE ではない）、3つの lockfile の 244・395・404 パッケージで脆弱性 0 | `build/reports/osv-scanner/osv.json`・`test-results.md` 2.4（C13） | build-and-test | Met |
| G-GITLEAKS | team.md の Code Style・project.md の Mandated | 秘密情報の検出 0 件 | 0 件（143 コミット） | C12 の `gitleaksScan` | build-and-test | Met |
| G-ARTIFACT | team.md の Deployment・`u2-dsl-definition/nfr-design/security-design.md` 6節 | `dist` を同梱した実行可能 WAR、WAR の中の JSON Schema が正本と一致、初回の読み込みの量が上限の内 | WAR ができ、`verifyDslSchemaInWar` 成功、111.4 KB（gzip、目安 500KB） | C1・C2 の `verifyArtifact`・`test-results.md` 2.5 | build-and-test | Met |
| U1-LICENSE-DOCS | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 1.1節・`u1-target-db/code-generation/code-summary.md` 6節 | WAR に第三者のライセンスの文書がある | MariaDB Connector/J の LGPL 2.1 の文書が `WEB-INF/classes/META-INF/third-party-licenses/` にある。MySQL Connector/J の jar に `LICENSE`、PostgreSQL JDBC の jar に `META-INF/LICENSE` | `backend/build/libs/mastersmith.war` の中身の一覧・`test-results.md` 2.5 | build-and-test | Met |
| NFR12.1 | req の NFR12・`u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節・project.md の Mandated | 版とダイジェストを固定した MySQL 8.4.11・MariaDB 11.8.9・PostgreSQL 18.6 のコンテナで `verify` の中で毎回実行し、飛ばし 0 件 | 3種類とも実行、結合テストの飛ばし 0 件（2回とも） | `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`・`backend/build/test-results/integrationTest/` | build-and-test | Met |
| NFR12.2 | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節・team.md の Testing Posture | 表を作るテストはクラスごとに名前の重ならないスキーマを作って消し、実行順に依存しない | 3種類の IT が `TargetDbTestDatabase` の仕組みで独立して成功（2回とも同じ結果） | `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbTestDatabase.java`・C1・C2 | build-and-test | Met |
| NFR12.3 | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節・team.md の Way of Working | コンテナの実行環境が無いときは警告を出して対象DB のテストだけ飛ばし、SKIPPED として出す | Loop-back 1 で拡張の登録の順を直した後、Docker に届かない状態で 129 件・失敗 0・飛ばし 57、BUILD SUCCESSFUL（27 秒）。飛ばした 10 クラスすべての出力に警告がある。構造の検査 `ExtensionOrderArchitectureTest` が再発を防ぐ（違反 0）。直す前は4クラスが `initializationError` で失敗していた | `test-results.md` 2.8・3.1（C14）・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ExtensionOrderArchitectureTest.java` | build-and-test | Met |
| TP-TDB-PLACE | team.md の Testing Posture・`u1-target-db/nfr-requirements/tech-stack-decisions.md` NFR12.1 | `verify` の時間と VM のメモリを実測し、対象DB の結合テストの置き場を決める材料を出す | 4分21秒・4分14秒・6分17秒（`8961cb2`）、VM のメモリ最大 約 1,424MiB（6GiB 中）。依頼者の決定で3種類とも `verify` の中で毎回（Q1: A） | `test-results.md` 2.7・`build-and-test-questions.md` Q1 | build-and-test | Met |
| G-E2E | team.md の Testing Posture・README の E2E の節 | E2E が CSP 違反やスクリプトのエラーなしに通る | 4 ファイル・6 件すべて成功（1 worker、010 → 040、19.0 秒）。DSL の管理の流れ（040）を足した（Q5: B） | `./gradlew e2eTest` の出力・`test-results.md` 2.6 | build-and-test | Met |

### B. 性能と資源（13 件）

条件: 使い捨ての環境、上限 CPU 4・メモリ 2g（Q4: A。U4 の NFR1.12 の条件 1g との差は明記）、対象DB は 100 テーブル × 100 カラム。1回ずつの時間は `39f9aeb`、保存の量は `8961cb2` で測った。大きさの MB は 10^6 バイト（`summary.md` の表は 2^20 バイトで割った値）。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1.6 | req の NFR1・`u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` 2節・`u4-dsl-management/nfr-requirements/performance-requirements.md` 1節・AC1.2.3 | 既定の DSL の生成が 100 × 100 で 30 秒以内（3種類とも） | 1.54〜2.64 秒（3種類） | `build/perf-results/dsl-run1/summary.md` | build-and-test | Met |
| NFR1.7 | `u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` 2節・`u3-default-dsl-generation/nfr-design/security-design.md` 5節 | 内訳: 読み取り 15 秒・組み立てと書き出し 5 秒・検証 3 秒・保存と応答 2 秒 | 読み取り 25〜296ms・組み立てと書き出し 426〜767ms・検証 419〜832ms・残り 約 0.8 秒以下。すべて内 | 同上（生成の内訳の表） | build-and-test | Met |
| NFR1.4 | `u2-dsl-definition/nfr-requirements/tech-stack-decisions.md` 2節 | 想定の規模の既定の DSL の読み込み・検証・モデル作りが 3 秒以内 | 419〜832ms（6,146,161〜6,383,161 バイト、コメントあり。要件の試算 約 5.3MB より大きい） | 同上（`validateMillis`） | build-and-test | Met |
| NFR1.5 | `u2-dsl-definition/nfr-requirements/tech-stack-decisions.md` 2節・`u2-dsl-definition/nfr-requirements/security-requirements.md` 4節 | 10MB の DSL の読み込みと検証が 10 秒以内（誤りの有無によらず）。メモリを測る | 0.82〜1.14 秒（正しいもの・誤りが多数・末尾に1件）。区間のヒープの最大 1,237MB、OOMKilled なし | 同上 | build-and-test | Met |
| U2-PATTERN-COMPILE | `u2-dsl-definition/nfr-design/security-design.md` 4節・`u2-dsl-definition/code-generation/code-summary.md` | 1,000 文字の重い正規表現の組み立てが短く終わり、実行器が埋まり続けない | 重い DSL（`pattern` 2,000 個）の投入 0.34〜0.44 秒、直後の普通の投入 0.228 秒（前後 0.21〜0.29 秒）、打ち切りと拒否のログ 0 件 | `build/perf-results/dsl-run4-extra/summary.md` | build-and-test | Met |
| NFR1.8 | req の NFR1・`u4-dsl-management/nfr-requirements/performance-requirements.md` 1節・`u4-dsl-management/nfr-design/performance-design.md` 1節・AC3.2.5 | 表示（照合を含む）・投入・戻しが 10 秒以内（3種類、10MB を含む） | 表示 0.045〜0.200 秒・投入 0.57〜1.04 秒・戻し 0.54〜1.00 秒。応答しない対象DB では決定 B により最悪 23〜28 秒を許す（目標は正常なときだけ） | `build/perf-results/dsl-run1/summary.md` | build-and-test | Met |
| NFR1.10 | `u4-dsl-management/nfr-requirements/performance-requirements.md` 1節 | 今の状態・履歴・破棄・適用の 95% が 1 秒以内（履歴 20 件など） | 未測定（Q2: B）。k6 の場面 `dslLight`・`dslCycle` を足し、動くことだけ確かめた（条件をそろえていないため判定に使わない） | `perf/k6/scenarios.js`・`build/perf-results/dsl-k6-smoke/`・`performance-test-instructions.md` 3節 | performance-validation | Unverified |
| NFR1.11 | `u4-dsl-management/nfr-requirements/performance-requirements.md` 1節・`u4-dsl-management/nfr-design/performance-design.md` 3節 | プレビュー中と適用中のダウンロードが 10MB でも 2 秒以内 | 0.032〜0.038 秒 | `build/perf-results/dsl-run1/summary.md` | build-and-test | Met |
| NFR1.12 | `u4-dsl-management/nfr-requirements/performance-requirements.md` 2節・`u4-dsl-management/nfr-design/performance-design.md` 4節 | 10MB の DSL の処理が、1g のコンテナで、ログインと同時でも失敗しない | 未測定（Q2: B）。参考: 2g の1回ずつの測定で `memory.peak` 1,732〜1,869MB、OOMKilled なし。k6 の場面 `dslMixed` を足した | `perf/k6/scenarios.js`・`performance-test-instructions.md` 3節 | performance-validation | Unverified |
| NFR2.5 | `u3-default-dsl-generation/nfr-requirements/security-requirements.md` 2節・`u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` 3.4節 | 100 × 100 で生成した DSL が 10MB の内。上限を超える写しでは失敗し、部分的な DSL を返さない | 6,146,161〜6,383,161 バイト。失敗の道は `TargetSchemaDslGeneratorTest` が成功 | `build/perf-results/dsl-run1/summary.md`・`backend/src/test/java/cherry/mastersmith/dslmanage/generate/TargetSchemaDslGeneratorTest.java` | build-and-test | Met |
| U4-STORAGE-RUN | `u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` 3.4節・`u4-dsl-management/nfr-requirements/scalability-requirements.md` 1節 | 動いている間の最大の状態（プレビュー1件と履歴 20 件がすべて 10MB）で、H2 のファイルが最大約 210MB、コンテナのメモリの上限の内 | `8961cb2`（`DEFRAG_ALWAYS=TRUE`）でも、21 回で 267.4MB、プレビューを置いて 278.2MB（2回とも同じ）、40 回で 483.1MB。1回に約 10.78MB 増え、履歴 20 件の後も頭打ちなし。`memory.peak` は上限 2g に張り付く（ページキャッシュ込み）、`anon` の最大 1,959〜1,986MB（上限の約 92%）、OOMKilled なし | `build/perf-results/dsl-lb1-storage21-defrag/`・`dsl-lb1-storage21-defrag-2/`・`dsl-lb1-storage40-defrag/`・`test-results.md` 3.2 | build-and-test | Not Met |
| U4-STORAGE-RESTART | 同上 | 止めて起動し直した後、H2 のファイルが生きているデータの量に戻り、データが無事 | `DEFRAG_ALWAYS=TRUE` で 15.9MB（21 回・40 回とも）。なしでは 278.2MB のまま。止めるのに 0.55〜1.00 秒、ExitCode 143（SIGTERM で正常終了）、OOMKilled なし、healthy まで約 8.7 秒。適用中の DSL・プレビューの SHA-256・履歴 20 件の版と `dslHash` が前後で一致、戻しで 20/20 一致。ただし試験の DSL は gzip で 10MB から 0.29MB に縮むデータで、圧縮の効かない本文では約 210MB＋α に近づくと推定（実測ではない） | `build/perf-results/dsl-lb1-storage21-defrag/`・`dsl-lb1-storage21-defrag-2/`・`dsl-lb1-storage40-defrag/`・`dsl-lb1-storage21-nodefrag/`・`test-results.md` 3.2 | build-and-test | Met |
| U4-POOL | `u4-dsl-management/nfr-design/logical-components.md` 3節・`u4-dsl-management/code-generation/code-summary.md` 7節・project.md の Corrections | 適用などの操作で内部DB のプール（上限 30）をログインと共有しても尽きない | 未測定（Q2: B）。`dslMixed` の実行で Hikari の値を記録する手順を `perf/README.md` に書いた | `perf/README.md`・`performance-test-instructions.md` 3節 | performance-validation | Unverified |

### C. 単位のテストで確かめる非機能（61 件）

いずれも C1・C2 の `verify` の中で成功した（失敗 0・飛ばし 0）。Actual は「そのテストが目標をそのとおり確かめていること」を突き合わせた結果。

#### U1 対象DB（16 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1.1 | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節・`u1-target-db/nfr-design/security-design.md` 1節・5節 | 対象DB の接続の待ちの上限 3 秒（生成・照合とも） | 3 秒の設定をテストが確かめて成功（U4 の要件の「5 秒」は古い記述） | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfigTest.java`・C1・C2 | build-and-test | Met |
| NFR1.2 | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節・`u1-target-db/nfr-design/security-design.md` 2節 | 問い合わせ1回の待ちの上限 生成 20 秒・照合 5 秒（4回とも） | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/service/TargetSchemaReaderTest.java`・C1・C2 | build-and-test | Met |
| NFR1.3 | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節・`u1-target-db/nfr-design/security-design.md` 2節 | メタデータの読み取りは 4 回の問い合わせで、テーブルの数に比例しない | 3種類の DB で成功（時間の部分は NFR1.7 で 25〜296ms） | `backend/src/test/java/cherry/mastersmith/targetdb/repository/MysqlSchemaQueriesIT.java`・`MariadbSchemaQueriesIT.java`・`PostgresSchemaQueriesIT.java`・C1・C2 | build-and-test | Met |
| NFR4.1 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・req の NFR4 | 接続情報は設定だけから受け取り、`web` 層から設定の型を使わない | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java`・C1・C2 | build-and-test | Met |
| NFR4.2 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・AC6.1.4 | パスワードは設定の型の文字列化と TRACE を含むログで伏せ字 | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbSecretLeakIT.java`・C1・C2 | build-and-test | Met |
| NFR4.3 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・AC6.1.5 | 設定の欠け・不正は項目名だけを WARN 1件、値は出さない | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`・C1・C2 | build-and-test | Met |
| NFR4.4 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・`u1-target-db/nfr-design/security-design.md` 3節 | 失敗の結果とログに JDBC の例外の文言・接続先・ユーザー名を含めない | 3種類の DB で成功（3つのドライバーのログは OFF） | `backend/src/test/java/cherry/mastersmith/targetdb/service/MysqlTargetSchemaReaderIT.java`・`MariadbTargetSchemaReaderIT.java`・`PostgresTargetSchemaReaderIT.java`・C1・C2 | build-and-test | Met |
| NFR4.5 | `u1-target-db/nfr-requirements/security-requirements.md` 2節 | 資格情報を含む JDBC の URL を組み立てない | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfigTest.java`・C1・C2 | build-and-test | Met |
| NFR4.6 | `u1-target-db/nfr-requirements/security-requirements.md` 2節 | 接続は読み取り専用で、書き込み・DDL のコードが無い | 3種類の DB で成功 | `backend/src/test/java/cherry/mastersmith/targetdb/service/MysqlTargetSchemaReaderIT.java`・`MariadbTargetSchemaReaderIT.java`・`PostgresTargetSchemaReaderIT.java`・C1・C2 | build-and-test | Met |
| NFR6.1 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・req の NFR6 | 任意の文字列の識別子が引用符の外に出ない | 性質ベースのテストが成功 | `backend/src/test/java/cherry/mastersmith/targetdb/domain/SqlIdentifiersTest.java`・C1・C2 | build-and-test | Met |
| NFR6.2 | `u1-target-db/nfr-requirements/security-requirements.md` 2節 | 値（スキーマ名）はプレースホルダーで渡し、文は固定の文字列 | 4つの問い合わせは固定の文字列の定数を `prepareStatement` に渡し、スキーマ名は `setString` で渡している。SpotBugs の `SQL_` 0 件（traceability.json は本番のソースを指す。Q7: A で記録のみ） | `backend/src/main/java/cherry/mastersmith/targetdb/repository/SchemaRows.java`・`MysqlSchemaQueries.java`・`PostgresSchemaQueries.java`・C1・C2 の `spotbugsGate` | build-and-test | Met |
| NFR6.4 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・AC1.1.9 | 引用符・空白・セミコロンを含む名前でも読み取りが成功し、ほかのテーブルとデータは変わらない | 3種類の DB で成功 | `backend/src/test/java/cherry/mastersmith/targetdb/repository/MysqlSchemaQueriesIT.java`・`MariadbSchemaQueriesIT.java`・`PostgresSchemaQueriesIT.java`・C1・C2 | build-and-test | Met |
| NFR7.1 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・req の NFR7 | 対象DB の設定が無い・欠け・種類が対応外でもアプリは起動する | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`・C1・C2 | build-and-test | Met |
| NFR7.2 | `u1-target-db/nfr-requirements/security-requirements.md` 2節 | 起動時に対象DB に接続しない | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`・C1・C2 | build-and-test | Met |
| NFR7.3 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・AC6.1.2 | ヘルスチェックは内部DB だけで判断し、対象DB を止めても 200 | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`・C1・C2 | build-and-test | Met |
| NFR7.4 | `u1-target-db/nfr-requirements/security-requirements.md` 2節・ADR-006 | 対象DB の接続は既定の候補にならず、ログイン・監査・Flyway・JPA・ヘルスチェックは内部DB を使う | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDbStartupIT.java`・C1・C2 | build-and-test | Met |
| NFR7.5〜NFR7.7 | `u1-target-db/nfr-requirements/tech-stack-decisions.md` 2節 | プール 最大 5・最小 0・使わない接続は 60 秒で閉じる | 成功 | `backend/src/test/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfigTest.java`・C1・C2 | build-and-test | Met |

#### U2 DSL の定義（13 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR2.1 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（大きさ） | 本文ちょうど 10MB は受け付け、1 バイト超えは読まずに `SIZE_LIMIT` | 成功（承認済みの要件 NFR2 の 5MB は U3 の決定で 10MB） | `backend/src/test/java/cherry/mastersmith/dsl/service/DefaultDslReaderTest.java`・C1・C2 | build-and-test | Met |
| NFR2.2 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（入れ子の深さ） | 深さ 50 は受け付け、51 は `DEPTH_LIMIT` | 成功 | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`・C1・C2 | build-and-test | Met |
| NFR2.3 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（別名） | 別名 100 は受け付け、101 は `ALIAS_LIMIT` | 成功 | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`・C1・C2 | build-and-test | Met |
| NFR2.4 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（別名の展開の爆発） | 展開後の節 1,000,000 を超えたら打ち切り、爆発する DSL を 1 秒以内に `ALIAS_LIMIT` | 成功 | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`・C1・C2 | build-and-test | Met |
| NFR3.1 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（タグ） | タグはすべて `FORBIDDEN_TAG` で、型が作られない | 6 種類のタグで成功（記録用のクラスが 0 件） | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`・C1・C2 | build-and-test | Met |
| NFR3.2 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（重複キー） | 重複キーは2回目の位置つきで `DUPLICATE_KEY`、上書きしない | 成功（同じテーブル・カラムの二重の定義も `DUPLICATE_KEY`） | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserTest.java`・C1・C2 | build-and-test | Met |
| NFR3.3 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・team.md（`$ref`） | 外部の `$ref` を取りに行かない（受け口への要求 0 件） | 成功 | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`・C1・C2 | build-and-test | Met |
| NFR3.4 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・req の NFR3 | 画面を通さず API を直接呼んでも本文をすべてサーバー側で検証する | U4 の結合テストで成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`・C1・C2 | build-and-test | Met |
| NFR3.5 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節 | SnakeYAML の安全な読み込みだけを使い、`dsl` から Jackson の YAML の読み込みを使わない | 成功（`jackson-dataformat-yaml` を依存から外した） | `backend/src/test/java/cherry/mastersmith/dsl/DslBoundaryArchitectureTest.java`・`backend/build.gradle.kts`・C1・C2 | build-and-test | Met |
| NFR3.6 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節 | `pattern` は 1,000 文字まで、組み立ては 100 ミリ秒で打ち切り、値に当てはめない | 成功（実時間は U2-PATTERN-COMPILE） | `backend/src/test/java/cherry/mastersmith/dsl/validate/PatternCheckerTest.java`・C1・C2 | build-and-test | Met |
| NFR5.1 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・req の NFR5 | 部品の例外の文言が誤りの一覧に現れない | 性質ベースのテストほかが成功 | `backend/src/test/java/cherry/mastersmith/dsl/parse/SafeYamlParserPropertyTest.java`・C1・C2 | build-and-test | Met |
| NFR5.2 | `u2-dsl-definition/nfr-requirements/security-requirements.md` 2節・AC2.2.6 | 誤りに埋める値は場所と値の先頭 100 文字まで、知らない項目の値は埋めない | 成功 | `backend/src/test/java/cherry/mastersmith/dsl/validate/DslSchemaValidatorTest.java`・C1・C2 | build-and-test | Met |
| U2-SCHEMA-PUB | `u2-dsl-definition/nfr-design/security-design.md` 6節・`u2-dsl-definition/nfr-requirements/tech-stack-decisions.md` 4節 | `GET /dsl/dsl-schema-v1.json` がログインなしで取れ、`nosniff` が付く | 成功（`SecurityConfig` は変えていない） | `backend/src/test/java/cherry/mastersmith/dsl/DslSchemaPublicationIT.java`・C1・C2 | build-and-test | Met |

#### U3 既定の DSL の生成（4 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR4.7 | `u3-default-dsl-generation/nfr-requirements/security-requirements.md` 2節 | 任意の文字列の名前とコメントで生成した DSL が検証を通り、読み直した値が同じ。同じ写しから同じバイト列 | 性質ベースのテストが成功（失敗時の種は jqwik の報告に出る） | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslGenerationPropertyTest.java`・C1・C2 | build-and-test | Met |
| NFR4.8 | `u3-default-dsl-generation/nfr-requirements/security-requirements.md` 2節・`u3-default-dsl-generation/nfr-design/security-design.md` 3節 | コメントの制御文字を取り除き、長さは切り詰めない | 成功（traceability.json は本番のソースを指す。Q7: A で記録のみ） | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslYamlWriterTest.java`・`DslTreeBuilderTest.java`・C1・C2 | build-and-test | Met |
| NFR4.9 | `u3-default-dsl-generation/nfr-requirements/security-requirements.md` 2節・AC3.4.4 | 生成した DSL・結果・ログに接続先・ユーザー名・パスワードが無い | 成功（3種類の実際の DB からの生成でも確かめた） | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/TargetSchemaDslGeneratorTest.java`・`DefaultDslGeneratorPostgresIT.java` ほか2つ・C1・C2 | build-and-test | Met |
| NFR9.2 | `u3-default-dsl-generation/nfr-requirements/tech-stack-decisions.md` 2節・req の NFR9 | 表示名 ja はコメント（無ければ物理名）、en は物理名 | 成功（traceability.json は本番のソースを指す。Q7: A で記録のみ） | `backend/src/test/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilderTest.java`・C1・C2 | build-and-test | Met |

#### U4 DSL の管理（19 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1.9 | `u4-dsl-management/nfr-requirements/performance-requirements.md` 1節 | 照合は接続 3 秒・問い合わせ 5 秒で打ち切り、応答しない対象DB でも警告 `TARGET_UNAVAILABLE` つきで表示を続ける | 成功（テストの中の応答しない相手で、接続の段で 10 秒の内に返る。問い合わせの段の本物の遅延は再現していない） | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`・C1・C2 | build-and-test | Met |
| NFR1.13 | `u4-dsl-management/nfr-requirements/scalability-requirements.md` 2節 | 生成・投入・戻し・表示はアプリ全体で同時に1つ、重なりは待たずに 503 `DSL_BUSY` | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`・C1・C2 | build-and-test | Met |
| NFR1.14 | `u4-dsl-management/nfr-requirements/scalability-requirements.md` 2節 | `DSL_BUSY` で断った要求は状態を変えず、監査の出来事を出さない | 成功 | 同上 | build-and-test | Met |
| NFR1.15 | `u4-dsl-management/nfr-requirements/scalability-requirements.md` 2節 | 軽い処理は制限しない。同時の適用は1件だけ成功 | 成功 | 同上 | build-and-test | Met |
| NFR1.16 | `u4-dsl-management/nfr-requirements/observability-requirements.md` 1節・`u4-dsl-management/nfr-design/observability-design.md` 1節 | 指標 `mastersmith.dsl.operation`（タグ `operation`・`outcome` は決まった語だけ）が操作の終わりに1回 | 成功（画面での確かめは OBS-DASH） | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslLifecycleTest.java`・C1・C2 | build-and-test | Met |
| NFR1.17 | `u4-dsl-management/nfr-requirements/observability-requirements.md` 2節 | 操作ごとに INFO 1件（キーと値）、本文・接続先・パスワード・部品の文言を出さない | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`・C1・C2 | build-and-test | Met |
| NFR2.6 | `u4-dsl-management/nfr-requirements/security-requirements.md` 2節・`u4-dsl-management/nfr-design/security-design.md` 1節 | 投入の API だけ 10MB（ちょうどは受け付け、1 バイト超えは 413 `DSL_TOO_LARGE`）、ほかは 1MB。ログインしていない大きな要求は 401 | 成功（既存の `ExposureIT` 2件・`SecurityHeadersIT` 1件は決定 C で直していない） | 同上 | build-and-test | Met |
| NFR3.7 | `u4-dsl-management/nfr-requirements/security-requirements.md` 2節・team.md（認可） | DSL の操作の API は管理者だけ（未認証 401・管理者でない 403） | 代表の API で成功（10 本の一括は U5-AUTHZ） | 同上 | build-and-test | Met |
| NFR3.8 | `u4-dsl-management/nfr-requirements/security-requirements.md` 2節 | ダウンロードは `Content-Disposition: attachment`・`application/yaml`・`nosniff` | 成功 | 同上 | build-and-test | Met |
| NFR4.10 | `u4-dsl-management/nfr-requirements/security-requirements.md` 2節・AC1.1.7・AC3.4.4・AC6.3.2 | 応答・ダウンロード・ログ・監査に接続先・ユーザー名・パスワードが無い | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`・C1・C2 | build-and-test | Met |
| NFR5.3 | `u4-dsl-management/nfr-requirements/observability-requirements.md` 2節・team.md の Code Style | 想定内の失敗（413・409・422・503）は WARN 以下でスタックトレースなし、500 は ERROR でスタックトレース付き、境界で1回 | 成功 | `backend/src/test/java/cherry/mastersmith/common/error/web/BusinessExceptionPropertiesTest.java`・C1・C2 | build-and-test | Met |
| NFR5.4 | `u4-dsl-management/nfr-requirements/security-requirements.md` 2節・req の NFR5・AC2.2.8・team.md（拒否の応答） | エラー応答は Problem Details と `code`、部品の文言・スタックトレースを載せない。誤りの一覧は表示言語の文言 | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAdminApiIT.java`・C1・C2 | build-and-test | Met |
| NFR5.5 | `u4-dsl-management/nfr-requirements/security-requirements.md` 2節 | `DSL_BUSY` を 503 の1つの状態コードで登録し、起動時の重複の検査を通る | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`・C1・C2 | build-and-test | Met |
| NFR7.8 | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 1節・req の NFR7 | 対象DB が止まっていても表示・今の状態・履歴・適用・破棄・ダウンロードは使える | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslTargetDbIT.java`・C1・C2 | build-and-test | Met |
| NFR8.1 | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 1節・req の NFR8・AC4.2.4 | 適用は1つのトランザクションで、途中の失敗で全部を巻き戻し、差し替えと監査は確定の後だけ | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslConcurrencyIT.java`・C1・C2 | build-and-test | Met |
| NFR8.2 | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 1節・AC4.2.3 | 同じプレビューの同時の適用は1件だけ成功、履歴は1件だけ増える | 成功 | 同上 | build-and-test | Met |
| NFR8.3 | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 1節 | プレビューを置く操作が重なってもプレビューの行は1つで、後に確定した方が残る | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/repository/DslManageRepositoryIT.java`・C1・C2 | build-and-test | Met |
| NFR8.4 | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 1節 | 起動時に適用中の DSL を読めなくても ERROR 1件で「無い」として起動を続ける | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/service/DslStartupIT.java`・C1・C2 | build-and-test | Met |
| NFR8.5 | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 1節・AC6.3.3 | 監査の書き込みに失敗しても DSL の操作は成功し、アプリのログに1件 | 成功 | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAuditWriteFailureIT.java`・C1・C2 | build-and-test | Met |

#### U5 DSL の管理画面（8 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1.21 | `u5-dsl-admin-ui/nfr-requirements/performance-requirements.md` | 誤りが 100 件を超えても先頭 100 件だけを描き、総数は数字で示す | 成功 | `frontend/src/features/dsl/DslErrorList.test.tsx`・C1・C2 | build-and-test | Met |
| NFR3.9 | `u5-dsl-admin-ui/nfr-requirements/security-requirements.md` 2節・req の NFR3 | サーバーから来る文字列は文字として描き、`<script>` を含む表示名が文字のまま出る | 成功、リンタの `react/no-danger` の違反 0 件 | `frontend/src/features/dsl/DslPreviewPanel.test.tsx`・C1・C2 の `verifyLint` | build-and-test | Met |
| NFR3.10 | `u5-dsl-admin-ui/nfr-requirements/security-requirements.md` 2節 | アクセストークンは ApiClient だけが扱う。ダウンロードの一時的な URL はすぐに捨てる | 成功 | `frontend/src/features/dsl/api/saveFile.test.ts`・`frontend/src/shared/api-client/apiClient.download.test.ts`・C1・C2 | build-and-test | Met |
| NFR3.11 | `u5-dsl-admin-ui/nfr-requirements/security-requirements.md` 2節 | JSON Schema のリンクは同じオリジンの `/dsl/dsl-schema-v1.json` だけ | 成功 | `frontend/src/features/dsl/DslSubmitForm.test.tsx`・C1・C2 | build-and-test | Met |
| NFR5.6 | `u5-dsl-admin-ui/nfr-requirements/security-requirements.md` 2節 | 失敗の文言は `code` から選び、`detail`・内部の文言・接続先を出さない。`DSL_BUSY`・`DSL_TOO_LARGE` の文言を持つ | 成功 | `frontend/src/features/dsl/DslAdminPage.test.tsx`・C1・C2 | build-and-test | Met |
| NFR9.1 | `u5-dsl-admin-ui/nfr-requirements/security-requirements.md` 2節・req の NFR9 | 画面の文言は ja・en を対で持ち、欠けを止める。物理名・識別は訳さない | 成功（make-you-chic-ui の閉じるボタンの名前は英語の表示でも「閉じる」のまま。`vendor` を変えられない） | `frontend/src/features/dsl/registration.test.tsx`・C1・C2 | build-and-test | Met |
| NFR10.1 | `u5-dsl-admin-ui/nfr-requirements/security-requirements.md` 2節・req の NFR10・AC6.2.6・team.md | 画面の部品ごとに vitest-axe で違反 0 件 | 成功（手で確かめる項目は E） | `frontend/src/features/dsl/` の各 `*.test.tsx`・C1・C2 | build-and-test | Met |
| U5-AUTHZ | team.md（認可）・U5 の機能設計 BR8.1・AC6.2.1〜AC6.2.3 | 10 本の API それぞれで 未認証 401・管理者でない 403（監査の行が1件増える）・管理者の結果。401・403 で状態が変わらない | 31 件すべて成功（設計の「11 本」は契約 C6 と実装に合わせて 10 本） | `backend/src/test/java/cherry/mastersmith/dslmanage/web/DslAccessControlIT.java`・C1・C2 | build-and-test | Met |

### D. 画面の時間と E2E（4 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1.18 | `u5-dsl-admin-ui/nfr-requirements/performance-requirements.md`・`u5-dsl-admin-ui/nfr-design/performance-design.md` | 画面を開いて今の状態とプレビューが出るまで 3 秒以内（照合を除く）、照合を含めて 11 秒以内 | 今の状態 0.045〜0.113 秒、プレビュー（照合を含む）0.22〜0.29 秒 | `build/perf-results/dsl-run2-ui/postgres/ui-timing.json` | build-and-test | Met |
| NFR1.19 | 同上 | 違いの表の行（100 カラム）を開いてから 0.5 秒以内 | 25ms（開いた行のカラムだけを描くことは `DslDiffTable.test.tsx` で成功） | 同上・`frontend/src/features/dsl/DslDiffTable.test.tsx` | build-and-test | Met |
| NFR1.20 | 同上 | 10MB のファイルを選んでから送り始めるまで 2 秒以内。`File.size` で読む前に判定 | 553〜664ms（判定は `submitInput.test.ts` で成功） | 同上・`frontend/src/features/dsl/submitInput.test.ts` | build-and-test | Met |
| U5-LANG-E2E | `u5-dsl-admin-ui/code-generation/code-summary.md` 6節 | 英語の表示で、サーバーが返す誤り・警告の `message` も英語になる | 英語のロケールの Chromium で、照合の警告と 422 の誤りの一覧の両方で日本語の文字が無く、画面にそのまま出る（`pass: true`） | `build/perf-results/dsl-run4-extra/postgres/ui-lang.json` | build-and-test | Met |

### E. 手での確認（アクセシビリティ、7 件）

依頼者の決定（Q6: B）により、依頼者が後で手元のブラウザーで確かめる。この段では確かめていない。部品ごとの自動の検査（vitest-axe、NFR10.1）と部品ごとのキーボードの操作（user-event のテスト）は Met。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| A11Y-O1 | a11y の O1・5節の手順 2 | すべての操作をキーボードだけで最後まで行える | 未確認（Q6: B） | `build-and-test-questions.md` Q6 | 依頼者の手での確認（持ち主の段なし） | Unverified |
| A11Y-O3 | a11y の O3 | Tab の順が見た目の順 | 未確認（Q6: B） | 同上 | 依頼者の手での確認（持ち主の段なし） | Unverified |
| A11Y-O4 | a11y の O4 | フォーカスが見える | 未確認（Q6: B） | 同上 | 依頼者の手での確認（持ち主の段なし） | Unverified |
| A11Y-P6 | a11y の P6・5節の手順 4 | 200% の拡大と 767px 以下の幅で、横に動くのは表だけで文字が欠けず操作できる | 未確認（Q6: B） | 同上 | 依頼者の手での確認（持ち主の段なし） | Unverified |
| A11Y-SR | a11y の 5節の手順 3・R1・R4 | VoiceOver で処理中・成功・失敗・誤りの件数・確かめる表示が伝わる | 未確認（Q6: B）。Modal の本文が `aria-describedby` で結べていない点もここで確かめる | 同上 | 依頼者の手での確認（持ち主の段なし） | Unverified |
| A11Y-U5 | a11y の U5 | 成功で求めていない画面の切り替えを起こさない | 未確認（Q6: B） | 同上 | 依頼者の手での確認（持ち主の段なし） | Unverified |
| A11Y-DESIGN | a11y の O7・U6・R5 | 時間の制限が無い、同じ働きの部品は同じ文言・位置、ネイティブの要素を優先 | 未確認（Q6: B）。a11y の U2 の案内「5MB まで」は実装では「10MB まで」（決定どおり） | 同上 | 依頼者の手での確認（持ち主の段なし） | Unverified |

### F. 後の段が持つもの（3 件）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| OBS-DASH | `u4-dsl-management/nfr-requirements/observability-requirements.md` 3節・`u4-dsl-management/nfr-design/observability-design.md` 3節・`u4-dsl-management/code-generation/code-summary.md` 7節・project.md の Corrections | ダッシュボードの行「DSL の操作」の式がすべて値を返し、ログ（Loki）で `dsl.operation` のキーで絞れる | 未確認（Q8: B）。式3つは Code Generation で使い捨ての監視のコンテナで確かめ済み | `build-and-test-questions.md` Q8 | observability-setup | Unverified |
| G-CI | team.md の Way of Working・Deployment | GitHub Actions で同じ `verify` が通る（テストの JVM のヒープ 1g、対象DB のコンテナを含む） | 未確認（push は依頼者が行う。統合の後に確かめる） | team.md の Way of Working | ci-pipeline | Unverified |
| U4-MIGRATION | `u4-dsl-management/nfr-requirements/reliability-requirements.md` 3節・`u4-dsl-management/nfr-design/reliability-design.md` 4節・team.md の Deployment | Flyway の V5・V6 は前進のみで、1つ前の版のアプリが移行後の内部DB で動く | 未確認。点検では V5 は表 2つの作成、V6 は NULL を許す列 4つの追加だけで、削除・変更は無い | `backend/src/main/resources/db/migration/V5__u4_dsl_management.sql`・`V6__u4_dsl_audit_columns.sql` | deployment-execution | Unverified |

### 判定のまとめ

| 判定 | 件数 | 内訳 |
|---|---|---|
| **Met** | **93** | 統合の関門と成果物 19、性能と資源 9、単位のテストで確かめる非機能 61、画面の時間と E2E 4 |
| **Not Met** | **1** | U4-STORAGE-RUN（動いている間は H2 のファイルが増え続け、メモリが上限に張り付く） |
| **Unverified** | **13** | performance-validation 3（NFR1.10・NFR1.12・U4-POOL）、observability-setup 1（OBS-DASH）、ci-pipeline 1（G-CI）、deployment-execution 1（U4-MIGRATION）、依頼者の手での確認 7（A11Y-*） |
| 合計 | 107 | — |

- `Unverified` のうち後の段が持つ6件は、実行の計画で **EXECUTE** の段（`performance-validation`・`observability-setup`・`ci-pipeline`・`deployment-execution`）が持つ（`aidlc-state.md`）。アクセシビリティの手での確認 7 件は持ち主の段が無く、依頼者が行う。
- Loop-back 1 で NFR12.3 は Met になり、U4-STORAGE は起動し直した後（RESTART）が Met、動いている間（RUN）が Not Met のまま残った。`Not Met` が 1 件あるため、この段は「すべて満たした」とはならない。さらに直すか既知の制約として先へ進むかは依頼者の判断が要る（`test-results.md` 3.2）。

## 4. 準備の度合い

| 区分 | 状態 | 根拠 |
|---|---|---|
| ビルドできる（build-ready） | **Yes** | `./gradlew verify` が `8961cb2` で通り、画面と JSON Schema を同梱した実行可能 WAR ができる |
| テストできる（test-ready） | **No** | テストはすべて緑でコマンド1つずつで再現でき、コンテナの実行環境が無いときの動き（NFR12.3）も直ったが、U4-STORAGE-RUN が Not Met |
| 配備できる（deployment-ready） | **No** | U4-STORAGE-RUN（動いている間は内部DB のファイルが増え続け、メモリが上限に張り付く）が未解決。NFR1.12（1g のコンテナ）と U4-POOL が未確認 |

## 5. 既知の制約と残り

1. **NFR12.3**（Loop-back 1 で直した。Met）: 4クラスの拡張の登録の順（`ContainerRuntimeCheck` → `OutputCaptureExtension`）のため、Docker に届かないときに中断が失敗に変わっていた。順を入れ替え、構造の検査 `ExtensionOrderArchitectureTest` で再発を防ぐ。
2. **U4-STORAGE-RUN**（Not Met）: 内部DB の既定の接続先に `DEFRAG_ALWAYS=TRUE` を足したことで、止めて起動し直した後は 15.9MB に戻る（U4-STORAGE-RESTART は Met）。しかし動いている間は、10MB の DSL の投入→適用の1回ごとに約 10.78MB 増え、履歴 20 件の後も頭打ちにならない（21 回＋プレビューで 278.2MB、40 回で 483.1MB。期待は最大約 210MB）。`memory.peak` は上限 2g に張り付き、`anon` は最大 1,959〜1,986MB（上限の約 92%）。止めない限り増え続けるため、動いている間の詰め直しか本文の持ち方の見直しが要る。また、試験の DSL はよく縮むデータで、圧縮の効かない本文では起動し直した後も約 210MB＋α に近づくと推定している（実測ではない）。
3. **Performance Validation への引き継ぎ**: NFR1.10（95 パーセンタイル）、NFR1.12（1g のコンテナでの 10MB とログインの重ね）、U4-POOL（プールの余裕）。k6 の場面 `dslLight`・`dslCycle`・`dslMixed` と手順は `perf/README.md`。2g でもメモリが上限に近いため、1g の NFR1.12 は最初に確かめる。
4. **古い記述との差**（目標は緩めていない）: 既定の DSL は約 5.3MB（試算）・約 6.7MB（U3 の記録）ではなく 6.15〜6.38MB（コメントあり）。U4 の要件の「生成は接続 5 秒」は実装では 3 秒。決定 B により照合は応答しない対象DB で最悪 23〜28 秒。
5. **traceability.json の食い違い**: U1〜U3 の一部（NFR1.1・NFR1.2・NFR6.2・NFR6.3・NFR4.8・NFR9.2・NFR12.1〜NFR12.3）が本番のソースや設定を指している。Q7: A により直さず `cross-unit-traceability.md` に記録した。この表ではテストに読み替えて判定した。
6. **アクセシビリティの手での確認 7 件**は依頼者が後で行う。未確認の前提は「部品ごとの自動の検査とキーボードの操作のテストが緑なら、流れ全体でも操作できる」ことで、次の機会は依頼者の手元での確認（リリースの前）。
7. **監視・CI・移行の戻し**は後の段（observability-setup・ci-pipeline・deployment-execution）で確かめる。
8. **E2E と1回ずつの時間**は最初の実行（`39f9aeb`）の結果で、`8961cb2` では流し直していない（本番の変更は内部DB の接続先だけ）。E2E は統合の前に流し直す。
9. **SpotBugs の priority 2・3 の警告**（109 件）は統合を止めない基準の内だが、前の Intent から増えている。
10. **E2E は CI の外**に置く（統合の前とリリースの前に手で実行）。040 は対象DB を設定しないため、照合の中身は結合テストが受け持つ。


## 受け入れた失敗（依頼者の判断、2026-09-25）

U4-STORAGE-RUN（動いている間の内部DB のファイルの最大）は Not Met のまま、依頼者が「Accept failure」を選んだ（Loop-backs used: 1/3）。README に既知の制約として記録した。詳細は `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md` の 5節。直した後の WAR で E2E を流し直し、6 件すべて成功した。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/aidlc-state.md`（Scope・Depth・Test Strategy・実行の計画）
- `build/perf-results/dsl-lb1-storage21-defrag/`・`build/perf-results/dsl-lb1-storage21-defrag-2/`・`build/perf-results/dsl-lb1-storage40-defrag/`・`build/perf-results/dsl-lb1-storage21-nodefrag/`（Loop-back 1 の後の保存の量）
- `backend/src/main/resources/application.yaml`（`DEFRAG_ALWAYS=TRUE`）・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ExtensionOrderArchitectureTest.java`・`backend/src/test/java/cherry/mastersmith/config/H2DefragOnCloseTest.java`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`（Q1〜Q9 と Consolidated Summary Confirmation）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/cross-unit-traceability.md`（要件の網羅、判定 Pass）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md`・`performance-test-instructions.md`・`security-test-instructions.md`・`integration-test-instructions.md`・`build-instructions.md`
- 目標の一覧の下書き（この段の Step 1 で各単位の `nfr-requirements/`・`nfr-design/`・`code-generation/`、`inception/requirements-analysis/requirements.md`、`inception/refined-mockups/accessibility-checklist.md` から集めた 106 件。リポジトリの外の一時ファイル）
- `backend/build/test-results/`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`build/reports/osv-scanner/osv.json`・`frontend/coverage/`
- `build/perf-results/dsl-run1/summary.md`・`build/perf-results/dsl-run2-ui/`・`build/perf-results/dsl-run3-extra/`・`build/perf-results/dsl-run4-extra/summary.md`・`build/perf-results/dsl-run5-storage40/`・`build/perf-results/dsl-k6-smoke/`
- `backend/src/test/java/cherry/mastersmith/`・`frontend/src/`・`frontend/e2e/`（テストのクラスの実在の確認）、`backend/src/main/java/cherry/mastersmith/targetdb/repository/`（NFR6.2 の点検）、`backend/src/main/resources/db/migration/`（U4-MIGRATION の点検）、`backend/build.gradle.kts`
- `perf/README.md`・`README.md`
- `aidlc/spaces/default/memory/org.md`・`aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`・`aidlc/spaces/default/memory/phases/construction.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/build-and-test-summary.md`（書き方の見本）

## Assumptions & Open Questions

- U4-STORAGE-RUN の Not Met を、さらに直すか（動いている間の詰め直し・本文の持ち方の見直し）、既知の制約として記録して先へ進むかは、依頼者の判断が要る。
- 圧縮の効かない本文での、起動し直した後の大きさ（約 210MB＋α の推定）は実測していない。
- アクセシビリティの手での確認 7 件の持ち主の段は無い。依頼者がいつ行うかは決まっていない。
