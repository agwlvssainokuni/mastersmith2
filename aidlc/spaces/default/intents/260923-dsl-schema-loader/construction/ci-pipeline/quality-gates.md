# 品質の関門（quality-gates）

Intent `260923-dsl-schema-loader`（dsl-schema-loader）の関門の一覧。合否の基準、それを実行するコマンド・タスク、証拠の出る場所と、**Build and Test が記録した検査の一覧と CI の段との対応**を記録する（project.md の Deployment「CI が必要な検査を実行しているかの確認は、Build and Test が記録した検査の一覧と、CI の段との対応づけで判断する」）。本書は既にある実装（`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/vitest.config.ts`・`.pre-commit-config.yaml`・`.github/workflows/ci.yml`）の記録であり、新しい関門を足すものではない。

## 0. 関門の3つの場所

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（Gitleaks・Spotless・Prettier） | **止める**（コミットが失敗する）。各自が `pre-commit install` を1回実行していることが前提 |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段）。colima が動いていること。統合の前とリリースの前は `./gradlew e2eTest` も | **止める。これが統合の関門**（team.md の Way of Working、project.md の Mandated）。対象DB のテストが SKIPPED の状態では統合しない |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合そのものは止めない（統合の後に動くため）。失敗したら次へ進む前に直す |

## 1. 関門の基準（team.md・project.md から）

| 関門 | 合否の基準 | 決まりの出どころ | 実行するタスク（段） |
|---|---|---|---|
| フォーマット | 差分があれば失敗。Java は palantir-java-format（インデント4、1行120文字）、画面は Prettier。`vendor/` は対象外 | team.md の Code Style | `verifyFormat`（1） |
| リンタ | error があれば失敗（oxlint の correctness・react・jsx-a11y・typescript、ESLint の react-hooks、`react/no-danger`・`no-eval` 系、Stylelint） | team.md の Code Style | `verifyLint`（2） |
| ライセンスヘッダー | Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）が無い・形が違えば失敗 | team.md の Code Style、project.md の Mandated | `verifyLicense`（3）と `verifyFormat`（1 の Spotless の `licenseHeader`） |
| ビルド | Java のコンパイル・`tsc --noEmit`（`strict`、`any` の禁止）・Vite のビルドのエラーで失敗 | team.md の Deployment・Code Style | `verifyBuild`（4） |
| 全テスト | 単体・結合・画面のテストが1件でも失敗したら失敗。対象DB のテストは CI では飛ばさない | team.md の Testing Posture・Way of Working、project.md の Mandated | `verifyUnitTest`（5）・`verifyIntegrationTest`（6） |
| カバレッジ | 行 **80%** 未満、または分岐 **70%** 未満で失敗。バックエンドは全体の合計と、新しいパッケージごと | team.md の Testing Posture | `verifyCoverage`（7） |
| 秘密情報 | Gitleaks が1件でも検出したら失敗（履歴全体）。コミットの前と CI の両方で実行 | team.md の Code Style、project.md の Mandated | pre-commit と `gitleaksScan`（8） |
| Java の静的解析 | SpotBugs＋FindSecBugs の **priority 1** の指摘、または**パターン名が `SQL_` で始まる**指摘が1件でもあれば失敗。priority 2・3 は警告 | team.md の Code Style | `spotbugsGate`（8） |
| 依存の脆弱性 | 重大度 **High 以上**（CVSS 7.0 以上）で失敗。npm の devDependencies は警告、ただし成果物を作る道具（`config/npm-build-tools.txt`）の High 以上と `MAL-` は止める | team.md の Code Style・Deployment、project.md の Mandated | `osvScan`（8） |
| 成果物 | WAR を作れない、または WAR の中の JSON Schema が正本と違えば失敗。初回の読み込みの量は 500KB を超えても警告だけ | team.md の Deployment、U2 の NFR 設計 | `verifyArtifact`（9） |

## 2. `./gradlew verify` の段の詳細

| 段 | タスク | 失敗の条件 | 実行するもの | 証拠の出る場所 |
|---|---|---|---|---|
| 0 準備 | `verifyPrepare` | 道具が無い・Node.js が 24 でない、`npm ci` の失敗、サブモジュールの追跡されるファイルが変わっている | `checkToolchain`・`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`frontendInstall` | Gradle の出力（道具が無いときは入れ方を示して失敗し、黙って飛ばさない） |
| 1 フォーマット | `verifyFormat` | 差分がある | `spotlessCheck`・`:backend:spotlessCheck`・`frontendFormatCheck` | Gradle の出力、Spotless の差分 |
| 2 リンタ | `verifyLint` | error がある | `frontendLint`（oxlint・ESLint）・`frontendLintCss`（Stylelint） | npm の出力 |
| 3 ライセンスヘッダー | `verifyLicense` | ヘッダーが無い・形が違う | `frontendLicenseCheck` | npm の出力 |
| 4 ビルド | `verifyBuild` | コンパイル・型・ビルドの誤り | `:backend:compileJava`・`:backend:compileTestJava`・`frontendTypecheck`・`frontendBuild` | `frontend/dist/`、Gradle の出力 |
| 5 単体テスト | `verifyUnitTest` | 1件でも失敗 | `:backend:test`（`*Test`。JUnit 5・jqwik・ArchUnit。`ExtensionOrderArchitectureTest`・`H2DefragOnCloseTest` を含む）・`frontendTest`（Vitest・Testing Library・vitest-axe・fast-check） | `backend/build/test-results/test/`、`backend/build/reports/tests/test/`、Vitest の出力 |
| 6 結合テスト | `verifyIntegrationTest` | 1件でも失敗。**CI（`CI=true`）では Docker に届かなければ対象DB のテストが失敗する** | `:backend:integrationTest`（`*IT`。組み込みの H2 と、Testcontainers の MySQL 8.4.11・MariaDB 11.8.9・PostgreSQL 18.6） | `backend/build/test-results/integrationTest/` |
| 7 カバレッジ | `verifyCoverage` | 3節の下限を下回る | `:backend:jacocoTestReport`・`:backend:jacocoTestCoverageVerification`・`frontendCoverage` | `backend/build/reports/jacoco/test/jacocoTestReport.xml`、`frontend/coverage/` |
| 8 安全の検査 | `verifySecurity` | 5節・4節の止める条件、Gitleaks の検出 | `:backend:spotbugsGate`・`osvScan`・`gitleaksScan` | `backend/build/reports/spotbugs/main.xml`、`build/reports/osv-scanner/osv.json`、Gitleaks の出力（値は伏せる） |
| 9 成果物と量 | `verifyArtifact` | WAR を作れない、JSON Schema の複写が正本と違う | `:backend:bootWar`・`:backend:verifyDslSchemaInWar`・`frontendBundleSize` | `backend/build/libs/mastersmith.war`、npm の出力（KB 数） |

テストの JVM のヒープの上限は 1g（`backend/build.gradle.kts`。この Intent の U4 で 512MB から上げた）。前の段が失敗したら後ろの段は実行しない。

## 3. カバレッジの下限

| 範囲 | 値 | 実行するもの | 備考 |
|---|---|---|---|
| バックエンドの全体 | 行 80% 以上・分岐 70% 以上 | `jacocoTestCoverageVerification` の1つ目の `rule` | 単体（`test.exec`）と結合（`integrationTest.exec`）を合わせて測る |
| バックエンドの新しいパッケージごと | 行 80% 以上・分岐 70% 以上 | 同じタスクの2つ目の `rule`（`element = "PACKAGE"`、`excludes = packagesJudgedByTotal`） | この Intent で入れた。既存の 22 パッケージは一覧で外して全体の合計で判定し、一覧に無い新しいパッケージは自動で対象になる。一覧は増やさない（team.md の Testing Posture。U1 の実測で `audit.service`・`common.health`・`auth.repository` が単独で下回ったため） |
| フロントエンドの全体 | 行 80% 以上・分岐 70% 以上 | `frontend/vitest.config.ts` の `coverage.thresholds`（`lines: 80`・`branches: 70`、`provider: 'v8'`） | — |

計測から外すのは、起動クラスと設定値だけのクラス（`MastersmithApplication*`・`**/*Properties.class`・`**/*Properties$*.class`）、画面の `src/main.tsx`・`*.d.ts`・テストのファイルに限る。**除外を後から増やして実質的に下限を下げない**。この Intent で計測の除外は増えていない（Build and Test の G-COV-EXCL）。

## 4. 依存関係の脆弱性の判定（OSV-Scanner、8 の段）

対象の lockfile は3つ: `backend/gradle.lockfile`、`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json`。いずれかが無ければ失敗させる。

| 区分 | 判定 | 統合を止めるか |
|---|---|---|
| Gradle（バックエンド）の依存 | 重大度 High 以上（CVSS 7.0 以上）で失敗 | **止める** |
| npm の実行時の依存（`dependencies`。推移依存と `vendor/make-you-chic-ui` の実行時の依存を含む） | High 以上で失敗 | **止める** |
| npm の開発用の依存（`devDependencies`） | 警告だけ | 止めない |
| npm の開発用のうち成果物を作る道具（`config/npm-build-tools.txt`。Vite・Rollup・esbuild・TypeScript など） | High 以上で失敗 | **止める** |
| 悪意のあるパッケージ（OSV の ID か別名が `MAL-` で始まる） | 重大度によらず失敗 | **止める** |
| 重大度が分からないもの | 警告（上の条件に当たるものは上の行で判定） | 止めない |

lockfile で見つからないものは、黙って飛ばさず実行時の依存として扱う（安全側）。

### 手元の `osvScan` が飛ばされる場合（Q3: B）

- `osvScan` の入力は3つの lockfile と `config/npm-build-tools.txt` である。これらが変わらなければ、**手元の `./gradlew verify` では Gradle が `osvScan` を UP-TO-DATE として飛ばす**（Build and Test で2回そうなった）。そのため、lockfile を変えていない間に新しく公開された脆弱性は、手元の統合の前の関門では見つからないことがある。
- 依頼者の決定（この段の Q3: B）により、この扱いは**今のまま**とする。タスクの設定は変えない。
- **CI では毎回まっさらな環境で走るため、`osvScan` は必ず実行される**。統合の後の CI が、新しく公開された脆弱性を見つける場所になる。
- 手元で走らせ直したいときは `./gradlew osvScan --rerun` を使う（Build and Test の C13。件数を報告するときもこれで実測する）。

## 5. Java の静的解析（SpotBugs＋FindSecBugs、8 の段）

| 項目 | 値 |
|---|---|
| 設定 | `effort = MAX`、`reportLevel = LOW`（低い確度まで報告に出す）、`ignoreFailures = true`（報告の生成では失敗させない）、除外は `backend/config/spotbugs-exclude.xml`（この Intent で変えていない） |
| 関門 | `spotbugsGate` が `backend/build/reports/spotbugs/main.xml` を読み、**priority 1** の指摘、または**パターン名が `SQL_` で始まる**指摘（priority によらず）が1件でもあれば失敗させる。`SQL_` の関門はこの Intent の U1 で入れた |
| それ以外 | priority 2・3 は警告として表示するだけ |
| 対象 | `spotbugsMain` のみ。`spotbugsTest` は無効（テストのコードは対象外） |
| 誤検知 | 除外の設定に理由を書いて外す（team.md の Code Style） |

Build and Test の実測（C12）: priority 1 は 0 件、`SQL_` は 0 件、priority 2 が 66 件・priority 3 が 43 件の警告。

## 6. Build and Test が記録した検査と CI の段との対応

Build and Test が走らせたコマンド（`aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md` の1節）と、統合の関門の目標（`build-and-test-summary.md` の Target Verification Matrix の A）を、CI の段に対応づけた。

### 6.1 コマンドの一覧との対応

| # | Build and Test のコマンド | 何を確かめたか | CI での実行 | 対応する CI の段 |
|---|---|---|---|---|
| C12（C1・C2） | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 統合の前の関門の全体（0〜9 の段）、件数とカバレッジ | **実行する** | ジョブ `verify` の 6 の段「1コマンドの検査を実行する」（`./gradlew verify`）。CI はまっさらな環境のため `cleanTest`・`cleanIntegrationTest` を付けなくてもテストは必ず走る |
| C13 | `./gradlew osvScan --rerun` | 依存の脆弱性の走査のやり直し | **実行する** | `verify` の 8 の段の `osvScan`（CI では UP-TO-DATE にならない。4節） |
| C14（C4・C5） | `DOCKER_HOST=unix:///nonexistent/docker.sock env -u CI ./gradlew … integrationTest --tests 'cherry.mastersmith.targetdb.*' --tests 'cherry.mastersmith.dslmanage.*'` | 手元でコンテナの実行環境に届かないときに警告を出して SKIPPED になること（NFR12.3） | **実行しない（意図して外す）** | CI では `CI=true` のため逆の道（失敗させる）を通る。SKIPPED の道は手元だけの振る舞い。判定の関数は `ContainerRuntimeCheckTest`、登録の順は `ExtensionOrderArchitectureTest` が 5 の段で毎回確かめる。代わりの実行の場は7節 |
| C3（と 5節の再実行） | `./gradlew e2eTest` | ビルドした WAR での E2E（010〜040 の4本、6 件、`workers: 1`） | **実行しない（意図して外す）** | 7節。統合の前とリリースの前に手元で実行する（Q1: A） |
| C6・C15 | `./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl… .` | 性能の測定用のイメージ | 実行しない | `bootWar` は 9 の段で実行する。イメージの作成は性能の測定と配備のためで、CI の範囲の外（7節） |
| C7〜C10・C16〜C18 | `./perf/dsl-timing.sh …`（使い捨ての環境） | 1回ずつの時間・画面の時間・英語の表示・保存の量 | **実行しない（意図して外す）** | 7節 |
| C11 | `k6 inspect` と `dslLight`・`dslCycle` の短い実行 | k6 の台本が動くことの確かめ（判定に使わない） | **実行しない（意図して外す）** | 7節 |

### 6.2 統合の関門の目標との対応（Target Verification Matrix の A、19 件）

| Target ID | Build and Test の判定 | CI の段（`verify` の中） |
|---|---|---|
| G-FORMAT | Met | 1 `verifyFormat` |
| G-LINT | Met | 2 `verifyLint` |
| G-LICENSE | Met | 3 `verifyLicense` と 1 `verifyFormat` |
| G-BUILD | Met | 4 `verifyBuild` |
| G-TEST | Met | 5 `verifyUnitTest`・6 `verifyIntegrationTest` |
| NFR11-BE・NFR11-BE-PKG・NFR11-FE | Met | 7 `verifyCoverage` |
| G-COV-EXCL | Met | 7 の設定（`backend/build.gradle.kts`・`frontend/vitest.config.ts`）。除外が増えないことは設定の点検で確かめる（CI は設定のとおりに測る） |
| NFR6.3 | Met | 8 `spotbugsGate` |
| G-OSV | Met | 8 `osvScan` |
| G-GITLEAKS | Met | 8 `gitleaksScan`（CI は `fetch-depth: 0` で履歴全体） |
| G-ARTIFACT | Met | 9 `verifyArtifact`（`bootWar`・`verifyDslSchemaInWar`・`frontendBundleSize`）と、CI の 7 の段の WAR の保存 |
| U1-LICENSE-DOCS | Met | 9 `bootWar` が WAR に同梱する。文書の有無そのものは Build and Test の WAR の中身の点検で確かめた（CI の関門ではない） |
| NFR12.1 | Met | 6 `verifyIntegrationTest`（3種類をダイジェスト固定で毎回。CI では飛ばさない） |
| NFR12.2 | Met | 6 `verifyIntegrationTest` |
| NFR12.3 | Met | 5 `verifyUnitTest` の `ExtensionOrderArchitectureTest`・`ContainerRuntimeCheckTest`。SKIPPED の道は手元だけ（6.1 の C14） |
| TP-TDB-PLACE | Met | 決定（3種類とも `verify` の中で毎回）の結果として 6 の段で実行 |
| G-E2E | Met | **CI の外**（7節） |

以上により、Build and Test が記録した build と test のコマンドのうち、統合の関門に当たるもの（C12・C13）は、CI が同じ `./gradlew verify` でそのまま実行する。CI の外に置くものは、すべて7節に代わりの実行の場を書いた。

## 7. 意図して CI の外に置く検査と、代わりの実行の場

| 検査 | CI の外に置く理由 | 代わりの実行の場 | 持ち主 |
|---|---|---|---|
| **E2E**（`./gradlew e2eTest`、010〜040 の4本） | ブラウザー（Playwright の Chromium）と起動した WAR が要り、前の Intent から CI と `verify` の外に置いている（この段の Q1: A） | **統合の前とリリースの前に手元で実行**（README の E2E の節）。`8961cb2` の WAR で 6 件すべて成功（18.7 秒） | 依頼者と AI（統合・リリースの手順） |
| **性能の測定**（`perf/dsl-timing.sh`・k6、`perf/README.md`） | 配備とは別の使い捨ての環境（仮の鍵・仮の利用者、終わったら消す）と、資源の上限をそろえた条件が要る（project.md の Testing Posture） | 手元の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`） | Build and Test（済み）、Performance Validation（NFR1.10・NFR1.12・U4-POOL） |
| **コンテナに届かないときの確かめ**（NFR12.3 の SKIPPED の道） | CI では `CI=true` のため失敗させるのが正しい振る舞いで、SKIPPED の道は通らない | 手元で `DOCKER_HOST` を存在しないソケットに向けて `integrationTest` を実行（Build and Test の C14）。再発は CI でも走る `ExtensionOrderArchitectureTest` が防ぐ | 手元（変更したときに再実行） |
| **手でのアクセシビリティの確認**（A11Y-O1・O3・O4・P6・SR・U5・DESIGN の7件） | キーボードだけの操作、拡大、VoiceOver など人の確かめが要る。部品ごとの自動の検査（vitest-axe、NFR10.1）は CI の 5 の段で走る | 依頼者の手元のブラウザー（Build and Test の Q6: B） | 依頼者（リリースの前） |

## 8. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○（コミットが失敗する） | — |
| 0 準備／1 フォーマット／2 リンタ／3 ライセンスヘッダー／4 ビルド | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら。手元で対象DB のテストが SKIPPED なら統合しない） | 手元の SKIPPED は警告を出す（統合はしない） |
| 7 カバレッジの下限 | ○（全体と新しいパッケージごとに、行 80%・分岐 70%） | — |
| 8 Gitleaks | ○ | — |
| 8 SpotBugs | ○（priority 1 と `SQL_`） | priority 2・3 |
| 8 OSV-Scanner | ○（4節の「止める」の行） | npm の開発用（道具と `MAL-` を除く）、重大度が不明 |
| 9 WAR の生成と JSON Schema の一致 | ○ | — |
| 9 初回の読み込みの JavaScript の量 | × | 500KB を超えたら警告 |
| E2E（`./gradlew e2eTest`） | 手元で統合の前とリリースの前に実行し、失敗したら統合しない | —（`verify` と CI の外） |

## 9. CI（統合の後）に固有の関門

CI は `./gradlew verify` をそのまま呼ぶため、2〜5節の関門はすべて CI でも効く。CI に固有のものは次の3つ。

| 関門 | 合否の基準 | 実行するもの | 証拠 |
|---|---|---|---|
| 道具の真正性 | Gitleaks・OSV-Scanner のダウンロードの SHA-256 が合わなければ失敗（`set -euo pipefail` と `sha256sum -c -`） | `ci.yml` の「Gitleaks と OSV-Scanner を入れる」の段 | ワークフローの実行の記録 |
| 対象DB のテストを飛ばさない | Docker に届かなければ対象DB のテストが失敗する（`CI=true`） | `ContainerRuntimeCheck` | テストの結果（`integrationTest`） |
| 成果物の存在 | WAR が見つからなければ失敗（`if-no-files-found: error`） | `actions/upload-artifact` | 成果物 `mastersmith-<コミットのハッシュ>`（30 日保存） |

CI での実行の結果（G-CI）は `ci-config.md` の8節に記録する（依頼者のプッシュの後）。

## Sources

- `build.gradle.kts`（`verifyStages`・`verify`・`osvScan` の入力と判定・`gitleaksScan`・`e2eTest`）
- `backend/build.gradle.kts`（テストの JVM のヒープ、`test`・`integrationTest`、JaCoCo の下限と `packagesJudgedByTotal`・除外、`spotbugsGate`、`verifyDslSchemaInWar`）
- `frontend/vitest.config.ts`（カバレッジの下限と除外）、`frontend/playwright.config.ts`、`frontend/e2e/`
- `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`・`ExtensionOrderArchitectureTest.java`・`TargetDbImages.java`
- `config/npm-build-tools.txt`、`backend/config/spotbugs-exclude.xml`、`.pre-commit-config.yaml`、`.github/workflows/ci.yml`
- `README.md`（`verify` の段の一覧、依存の脆弱性の判定、E2E の節、対象DB の節）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md`（1節のコマンドの一覧と実測）・`build-and-test-summary.md`（Target Verification Matrix）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/ci-pipeline/ci-pipeline-questions.md`（Q1〜Q3）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/ci-pipeline/quality-gates.md`（書き方の見本）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment・Code Style）、`project.md`（Deployment・Testing Posture・Mandated）

## Assumptions & Open Questions

None.
