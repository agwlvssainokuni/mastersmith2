# 品質の関門（quality-gates）

Intent `260922-auth-audit-base`（auth-audit-foundation）の関門の一覧。合否の基準と、それを実行する実際のコマンド・タスク、証拠の出る場所を1行ずつ記録する。本書は**すでに存在する実装**（`build.gradle.kts`・`backend/build.gradle.kts`・`.pre-commit-config.yaml`・`.github/workflows/ci.yml`）の記録であり、新しい関門を足すものではない。

## 0. 関門の3つの場所

| 場所 | 実行するもの | 統合を止めるか |
|---|---|---|
| コミットの直前 | pre-commit のフック（3項目） | **止める**（コミットが失敗する）。ただし各自が `pre-commit install` を1回実行していることが前提 |
| **統合の前（手元）** | `./gradlew verify`（0〜9 の段） | **止める。これが統合の関門**（team.md の Way of Working、project.md の Mandated） |
| 統合の後（CI） | GitHub Actions が同じ `./gradlew verify` を実行 | 統合そのものは止めない（統合の後に動くため）。失敗したら、次の Bolt に進む前に直す |

`./gradlew verify` は、前の段が失敗したら後ろの段を実行しない（`build.gradle.kts` の `verifyStages` が `mustRunAfter` で順番を固定し、`verify` が全段に依存する）。段だけを単独で実行することもできる（例: `./gradlew verifyFormat`）。

## 1. コミットの直前（`.pre-commit-config.yaml`）

| 関門 | 合否の基準 | 実行するもの | 証拠 |
|---|---|---|---|
| 秘密情報の検出 | コミットする差分に秘密情報が見つかったら失敗 | `gitleaks`（`repo: https://github.com/gitleaks/gitleaks`、`rev: v8.30.1` で版を固定） | `pre-commit` の出力（`pre-commit run --all-files` で再現できる） |
| Java・Gradle のフォーマットとライセンスヘッダー | Spotless の確認に差分があれば失敗 | `./gradlew --quiet spotlessCheck :backend:spotlessCheck`（対象 `\.(java\|kts)$`） | 同上 |
| 画面のフォーマット | Prettier の確認に差分があれば失敗 | `npx --no-install prettier --check --ignore-unknown …`（対象 `^frontend/`、`package-lock.json` を除く） | 同上 |

pre-push のフックは置かない（統合の前に `./gradlew verify` を手で実行する）。

## 2. `./gradlew verify` の段（統合の前の関門）

| 段 | タスク | 合否の基準（失敗の条件） | 実行するもの（中身） | 証拠の出る場所 |
|---|---|---|---|---|
| 0 準備 | `verifyPrepare` | 道具が無い／版が違う、`npm ci` の失敗、**サブモジュールの追跡されるファイルが変わっている**ときは失敗 | `checkToolchain`（`node`・`npm`・`git` の存在と Node.js が `v24.` で始まること）、`vendorInstall`（`npm ci`）、`vendorBuild`（`npm run build`）、`vendorUnchanged`（`git status --porcelain` が空）、`frontendInstall`（`npm ci`） | Gradle の出力。道具が無いときは入れ方を示して失敗する（黙って飛ばさない） |
| 1 フォーマット | `verifyFormat` | 差分があれば失敗 | `spotlessCheck`（ルートの `*.gradle.kts`）、`:backend:spotlessCheck`（palantir-java-format ＋ `licenseHeader`）、`frontendFormatCheck`（`npm run format:check` = `prettier --check .`） | Gradle の出力、Spotless の差分の表示 |
| 2 リンタ | `verifyLint` | error があれば失敗 | `frontendLint`（`npm run lint` = `oxlint . && eslint .`）、`frontendLintCss`（`npm run lint:css` = `stylelint "src/**/*.css"`） | Gradle・npm の出力 |
| 3 ライセンスヘッダー | `verifyLicense` | Apache-2.0 のヘッダーが無い・形が違えば失敗 | `frontendLicenseCheck`（`npm run license:check` = `node scripts/check-license-header.mjs`）。**Java と Gradle の Kotlin DSL は 1 の段の Spotless の `licenseHeader` が同時に確かめる** | npm の出力 |
| 4 ビルド | `verifyBuild` | コンパイル・型検査・ビルドのエラーで失敗 | `:backend:compileJava`、`:backend:compileTestJava`、`frontendTypecheck`（`tsc --noEmit`）、`frontendBuild`（`vite build`） | `frontend/dist/`、Gradle の出力 |
| 5 単体テスト | `verifyUnitTest` | 1件でも失敗したら失敗 | `:backend:test`（`*Test` に絞る。JUnit 5・jqwik・ArchUnit）、`frontendTest`（`vitest run`） | `backend/build/test-results/test/`、`backend/build/reports/tests/test/`、Vitest の出力 |
| 6 結合テスト | `verifyIntegrationTest` | 1件でも失敗したら失敗 | `:backend:integrationTest`（`*IT` に絞る。Spring Boot Test ＋ **組み込みの H2**。コンテナは不要） | `backend/build/test-results/integrationTest/` |
| 7 カバレッジの下限 | `verifyCoverage` | **行 80% 未満、または分岐 70% 未満**で失敗（3節） | `:backend:jacocoTestReport`、`:backend:jacocoTestCoverageVerification`、`frontendCoverage`（`vitest run --coverage`） | `backend/build/reports/jacoco/test/jacocoTestReport.xml`（と html）、`frontend/coverage/` |
| 8 安全の検査 | `verifySecurity` | SpotBugs の重大度 High が1件でもあれば失敗（5節）、OSV-Scanner が止める条件に当たれば失敗（4節）、Gitleaks が秘密情報を検出したら失敗 | `:backend:spotbugsGate`、`osvScan`、`gitleaksScan`（`gitleaks git --redact --no-banner --config .gitleaks.toml --exit-code 1 .`。**リポジトリの履歴全体**） | `backend/build/reports/spotbugs/main.xml`（と html）、`build/reports/osv-scanner/osv.json`、Gitleaks の出力（値は伏せられる） |
| 9 成果物と量の確認 | `verifyArtifact` | **WAR を作れなければ失敗**。読み込みの量は 500KB を超えても**警告だけで失敗しない** | `:backend:bootWar`（`frontend/dist` を同梱した実行可能 WAR）、`frontendBundleSize`（`node scripts/check-bundle-size.mjs`） | `backend/build/libs/mastersmith.war`、npm の出力（合計の KB 数） |

## 3. カバレッジの下限

| 項目 | 値 | 実行するもの | 備考 |
|---|---|---|---|
| バックエンド | 行 **80%** 以上、分岐 **70%** 以上 | `jacocoTestCoverageVerification`（`counter = "LINE"` / `minimum = 0.80`、`counter = "BRANCH"` / `minimum = 0.70`） | 単体（`test.exec`）と結合（`integrationTest.exec`）の実行記録を**合わせて**測る |
| フロントエンド | 行 **80%** 以上、分岐 **70%** 以上 | `frontend/vitest.config.ts` の `coverage.thresholds`（`lines: 80`、`branches: 70`。`provider: 'v8'`） | — |

- **下限は成果物の全体（bundle）に対して定義している**。パッケージ単位・ファイル単位の下限は定義していない。したがって、一部のパッケージが 80% を下回っても、全体が下限を満たせばこの関門は通る（Build and Test の実測では `common/health` 79.2%、`audit/service` 77.2% が該当し、全体は行 96.14%・分岐 91.45%）。
- 計測から外すのは、アプリの起動クラスと設定値だけのクラスに限る（`coverageExclusions` = `MastersmithApplication*`、`**/*Properties.class`、`**/*Properties$*.class`）。フロントエンドは `src/main.tsx`・`*.d.ts`・テストのファイルを外す。**除外を後から増やして実質的に下限を下げない**（team.md の Testing Posture）。

## 4. 依存関係の脆弱性の判定（OSV-Scanner、8 の段）

検査の対象の lockfile は3つ: `backend/gradle.lockfile`、`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json`。いずれかが無ければ失敗させる。実装は `build.gradle.kts` の `osvScan` タスクが報告（JSON）を読んで判定する。

| 区分 | 判定 | 統合を止めるか |
|---|---|---|
| Gradle（バックエンド）の依存関係 | 重大度 High 以上（CVSS **7.0 以上**）で失敗 | **止める** |
| npm の**実行時**の依存関係（`dependencies`。推移依存と `vendor/make-you-chic-ui` の実行時の依存を含む） | High 以上で失敗 | **止める** |
| npm の**開発用**の依存関係（`devDependencies`） | 原則は**警告だけ** | 止めない |
| npm の開発用のうち、**成果物を作る道具**（`config/npm-build-tools.txt` に列挙。Vite・rolldown・esbuild・rollup・postcss・lightningcss・TypeScript・`@vitejs/plugin-react`・`vite-plugin-dts` など。末尾 `*` は前方一致） | High 以上で失敗 | **止める** |
| **悪意のあるパッケージ**（OSV の ID または別名が `MAL-` で始まるもの） | 重大度によらず失敗 | **止める**（実行時・開発用を問わない） |
| 重大度が分からないもの | 警告（上の条件に当たるものは上の行で判定する） | 止めない |

実行時か開発用かは、lockfile の各項目の `dev`・`devOptional` の印から作る表で判定する。**lockfile で見つからないものは、黙って飛ばさず実行時の依存関係として扱う**（安全側）。判定の結果は「失敗の条件に当たるもの N 件、警告 M 件」として出力され、失敗のときは該当を並べて例外にする。

## 5. Java の静的解析（SpotBugs ＋ FindSecBugs、8 の段）

| 項目 | 値 |
|---|---|
| 設定 | `effort = MAX`、`reportLevel = LOW`（**低い確度のものまで報告に出す**）、`ignoreFailures = true`（報告の生成では失敗させない）、除外は `config/spotbugs-exclude.xml` |
| 関門 | `spotbugsGate` が `backend/build/reports/spotbugs/main.xml` を読み、`priority == "1"`（**重大度 High**）の指摘が1件でもあれば失敗させる |
| それ未満 | `priority` 2・3 は**警告として表示するだけ**（統合は止めない） |
| 対象 | `spotbugsMain` のみ。`spotbugsTest` は無効にしており、テストのコードは関門の対象外 |

Build and Test の実測では priority 1 が 0 件、priority 2 が 29 件・priority 3 が 23 件（合計 52 件の警告）。

## 6. 止めるもの・止めないものの整理

| 関門 | 統合を止める | 警告にとどめる |
|---|---|---|
| pre-commit（Gitleaks・フォーマット） | ○（コミットが失敗する） | — |
| 0 準備 | ○ | — |
| 1 フォーマット／2 リンタ／3 ライセンスヘッダー／4 ビルド | ○ | — |
| 5 単体テスト／6 結合テスト | ○（1件でも失敗したら） | — |
| 7 カバレッジの下限 | ○（全体で行 80%・分岐 70% を下回ったら） | パッケージ単位の下限は定義していない |
| 8 Gitleaks | ○ | — |
| 8 SpotBugs | ○（priority 1 のみ） | priority 2・3 |
| 8 OSV-Scanner | ○（4節の「止める」の行） | npm の開発用（道具と `MAL-` を除く）、重大度不明 |
| 9 WAR の生成 | ○ | — |
| 9 初回の読み込みの JavaScript の量 | ×（**警告だけ**） | 500KB を超えたら警告（`Performance Validation で見直してください`） |
| E2E（`./gradlew e2eTest`） | `verify` と CI の外。統合の前とリリースの前に手で実行する | — |

## 7. CI（統合の後）が実行するもの

CI は `./gradlew verify` を**そのまま**呼ぶため、2〜6節の関門はすべてそのまま CI でも効く。CI 固有の関門は次の2つだけ。

| 関門 | 合否の基準 | 実行するもの | 証拠 |
|---|---|---|---|
| 道具の真正性 | Gitleaks・OSV-Scanner のダウンロードの SHA-256 が合わなければ失敗（`set -euo pipefail` と `sha256sum -c -`） | `.github/workflows/ci.yml` の「Gitleaks と OSV-Scanner を入れる」の段 | ワークフローの実行の記録 |
| 成果物の存在 | WAR が見つからなければ失敗（`if-no-files-found: error`） | `actions/upload-artifact` | 成果物 `mastersmith-<コミットのハッシュ>`（30 日保存） |

## 8. 本段の質問の要約との差（記録）

`ci-pipeline-questions.md` 3節の表は、9 の段の失敗の基準を「生成の失敗、上限（500KB）超過」と書いている。**実装では 500KB の超過は警告だけで、統合も CI も止めない**（`frontend/scripts/check-bundle-size.mjs` の「目安の 500KB を超えたら警告を出す（統合は止めない。NFR1.5）」）。承認済みの設計（`u1-app-skeleton/infrastructure-design/cicd-pipeline.md` 2節「量の超過は失敗にしない」）も同じであり、要約の1行だけが強く書かれていた。本書は実装と承認済みの設計に合わせて記録する。

## Sources

- `build.gradle.kts`（`verifyStages`・`verify`・`osvScan`・`gitleaksScan`・`e2eTest`）
- `backend/build.gradle.kts`（`test`・`integrationTest`・JaCoCo の下限と除外・Spotless・`spotbugsGate`）
- `frontend/vitest.config.ts`（カバレッジの下限と除外）、`frontend/package.json`（npm のスクリプト）、`frontend/scripts/check-bundle-size.mjs`
- `config/npm-build-tools.txt`（成果物を作る道具の一覧）、`.pre-commit-config.yaml`、`.github/workflows/ci.yml`
- `README.md`（段の一覧、依存関係の脆弱性の判定）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/test-results.md`・`build-and-test-summary.md`（実測）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment・Code Style）、`project.md`（Mandated）

## Assumptions & Open Questions

None.
