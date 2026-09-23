# ビルド手順（build-instructions）

本Intent（auth-audit-foundation、U1〜U4）のソースを、開発者の PC で取得してからビルドし、成果物（画面を同梱した実行可能 WAR）を得るまでの手順。統合の前の関門である1コマンドの検査（`./gradlew verify`）の前提もここでそろえる。

すべてのコマンドはリポジトリのルート（`mastersmith2/`）で実行する。

## 1. 前提の道具

| 道具 | 版 | 入れ方の例（macOS） | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `brew install --cask temurin@25` または SDKMAN の `sdk install java 25.0.4-tem` | ビルド・テスト |
| Node.js・npm | 24 | `brew install node@24` または `nvm install 24` | 画面のビルド・テスト |
| Gitleaks | 8.30.1 | `brew install gitleaks` | 秘密情報の検出（`./gradlew verify` の 8 の段） |
| OSV-Scanner | 2.6.0 | `brew install osv-scanner` | 依存関係の脆弱性の検査（同 8 の段） |
| Python と pre-commit | pre-commit 4.x | `brew install pre-commit` | コミットの前の検査 |
| Docker（Docker Desktop または colima） | Compose v2 | `brew install colima docker docker-compose` | コンテナで動かすとき（ビルドとテストには不要） |
| Playwright の Chromium | `@playwright/test` と同じ版 | `(cd frontend && npx playwright install chromium)` | ビルドした WAR での画面の確認（`./gradlew e2eTest`） |

- Gradle は Wrapper（`./gradlew`）で入るため、別に入れる必要はない。
- `./gradlew verify` は、Node.js の版が 24 でない、Gitleaks・OSV-Scanner が見つからないときに、入れ方を示して失敗する。検査を黙って飛ばさない。
- **本Intentのテストはコンテナの実行環境を必要としない**。内部DB（H2）は本番と同じ組み込み・ファイル保存で動かすため、Testcontainers は使わない（team.md の Testing Posture の読み替え）。Docker が要るのは `compose.yaml` での起動確認だけである。

## 2. 取得と依存関係の用意

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

依存関係は `./gradlew verify` の 0 の段（`verifyPrepare`）が自動で用意するため、通常は手で入れる必要はない。個別に動かしたいときだけ次を実行する。

```bash
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

- 依存関係は lockfile（npm）とバージョンカタログ（Gradle、`settings-gradle.lockfile`）で版を固定している。CI も同じく lockfile どおりに入れる（`npm ci`）。
- `vendor/make-you-chic-ui` のビルドで作られるのは、サブモジュールの `.gitignore` が無視する `node_modules/` と `dist/` だけで、追跡されるファイルは変わらない。`verifyPrepare` の `vendorUnchanged` がそれを確かめる。
- サブモジュールの中身はこのリポジトリから変更しない（project.md の Forbidden）。

## 3. 環境の設定

ビルドとテストだけなら環境変数は要らない。**起動する**ときだけ次を用意する。

```bash
cp .env.example .env
# .env を開き、値を入れる（.env は Git 管理外。コミットしない）
```

| 変数 | 用途 | 既定 |
|---|---|---|
| `MASTERSMITH_DB_PASSWORD` | 内部DB（H2）のパスワード（秘密情報） | なし（起動時に必要） |
| `MASTERSMITH_AUTH_SIGNING_KEY` | アクセストークンの署名鍵。256 ビット以上。無い・短いと起動を止める | なし（起動時に必要） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_*` | 初期管理者のメールアドレスとパスワード | なし（無ければ作らずに WARN を出して起動を続ける） |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` | 外部エクスポート（OTLP） | `false`（既定で無効） |

秘密情報はソースコード・設定ファイルに直接書かない。見本は値を空にした `.env.example` に置く（project.md の Forbidden）。

## 4. ビルドのコマンド

| 目的 | コマンド | 出力 |
|---|---|---|
| 画面のビルド | `(cd frontend && npm run build)` | `frontend/dist/` |
| バックエンドのコンパイル | `./gradlew :backend:compileJava :backend:compileTestJava` | `backend/build/classes/` |
| 成果物（画面を同梱した実行可能 WAR） | `./gradlew :backend:bootWar` | `backend/build/libs/mastersmith.war` |
| 統合の前の関門（ビルドを含む全検査） | `./gradlew verify` | 下表のとおり |

`./gradlew verify` は次の順に実行し、1つでも失敗したら後ろの段は実行しない。CI（GitHub Actions）も同じタスクを呼ぶ。

| 順 | 段（タスク） | 内容 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui の `npm ci`・ビルド、サブモジュールが変わっていないことの確認、画面の `npm ci` |
| 1 | `verifyFormat` | Spotless（palantir-java-format、ライセンスヘッダーを含む）、Prettier |
| 2 | `verifyLint` | oxlint、ESLint、Stylelint |
| 3 | `verifyLicense` | 画面のファイルのライセンスヘッダー（Java と Gradle の Kotlin DSL は 1 の段の Spotless が確かめる） |
| 4 | `verifyBuild` | Java のコンパイル、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit（`*Test`）、Vitest |
| 6 | `verifyIntegrationTest` | Spring と組み込みの H2 を起動するテスト（`*IT`） |
| 7 | `verifyCoverage` | JaCoCo・`@vitest/coverage-v8`。**行 80%・分岐 70%** を下回ったら失敗 |
| 8 | `verifySecurity` | SpotBugs＋FindSecBugs、OSV-Scanner、Gitleaks（重大度 High 以上で失敗） |
| 9 | `verifyArtifact` | 画面を同梱した実行可能 WAR、初回の読み込みの JavaScript の量 |

各段だけを実行することもできる（例: `./gradlew verifyFormat`）。

E2E（Playwright）は `verify` に入れていない。別のタスクで動かす。

```bash
(cd frontend && npx playwright install chromium)   # 各自1回
./gradlew e2eTest
```

## 5. ビルドの確認

| 確かめること | 方法 | 期待 |
|---|---|---|
| 1コマンドの検査が通る | `./gradlew verify` | `BUILD SUCCESSFUL` |
| 成果物ができている | `ls -l backend/build/libs/mastersmith.war` | ファイルがある |
| 画面が WAR に同梱されている | `unzip -l backend/build/libs/mastersmith.war \| grep -c 'BOOT-INF/classes/static/'` | 1件以上 |
| 起動できる | `java -jar backend/build/libs/mastersmith.war` のあと `curl -s localhost:8080/actuator/health` | `{"status":"UP"}` |
| コンテナで起動できる | `docker compose up --build -d` のあと `curl -s localhost:8080/actuator/health` | `{"status":"UP"}` |
| 初回の読み込みの JavaScript の量 | `verify` の 9 の段の出力 | 500KB（gzip）以内 |

報告の出る場所。

- テスト: `backend/build/reports/tests/test/`、`backend/build/reports/tests/integrationTest/`
- カバレッジ: `backend/build/reports/jacoco/test/html/index.html`、`backend/build/reports/jacoco/test/jacocoTestReport.xml`、`frontend/coverage/`
- SpotBugs: `backend/build/reports/spotbugs/`
- OSV-Scanner: `build/reports/osv-scanner/osv.json`
- E2E: `frontend/playwright-report/`

## 6. うまくいかないときの手当て

| 症状 | 原因 | 手当て |
|---|---|---|
| `checkToolchain` が失敗する | Node.js が 24 でない、Gitleaks・OSV-Scanner が無い | 出力に示された入れ方で用意する。検査を飛ばす設定は作らない |
| `vendorUnchanged` が失敗する | `vendor/make-you-chic-ui` の追跡されるファイルを変えた | サブモジュールの変更を取り消す。変更は make-you-chic-ui のリポジトリ側で行う |
| 画面のビルドで React が二重に読み込まれる | `resolve.dedupe` の設定漏れ | `frontend/vite.config.ts` の `resolve.dedupe` に `react`・`react-dom` があることを確かめる |
| `verifyFormat` が失敗する | 整形していない | `./gradlew spotlessApply :backend:spotlessApply` と `(cd frontend && npm run format)` を実行する |
| `verifyLicense` が失敗する | 新しいファイルにライセンスヘッダーが無い | 先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を入れる |
| `verifyCoverage` が失敗する | 行 80%・分岐 70% を下回った | **テストを足して満たす**。下限を下げる・計測の除外を増やすことはしない（org.md・team.md） |
| `gitleaksScan` が失敗する | 秘密情報をコミットした | 値を環境変数に移し、履歴からも取り除く |
| `osvScan` が失敗する | 実行時に使う依存関係に重大度 High 以上の脆弱性 | 版を上げる。開発時にだけ使うものは警告にとどまる（team.md の Deployment） |
| `./gradlew e2eTest` がブラウザの無いことで失敗する | Chromium が入っていない | `(cd frontend && npx playwright install chromium)` |
| 結合テストが 404 で散発的に失敗する | 調査中の一過性の失敗（U4 の code-summary.md 6章） | そのときのログを保存して報告する。詳しくは `test-results.md` の「既知の観察事項」 |

## Sources

- `README.md`（前提の道具、取得と準備、1コマンドの検査、環境変数）
- `build.gradle.kts`（`verifyStages`、`verify`、`e2eTest` の定義）、`backend/build.gradle.kts`（JaCoCo の下限）
- `aidlc/spaces/default/memory/team.md`（Way of Working、Testing Posture、Deployment、Code Style）
- `aidlc/spaces/default/memory/project.md`（Way of Working の `./gradlew verify` の学び、Forbidden、Mandated）
- 各単位の `construction/<unit>/code-generation/unit-test-instructions.md` 2.1 節（前提の用意）
- `construction/u4-audit-log/code-generation/code-summary.md` 6章（結合テストの一過性の失敗）

## Assumptions & Open Questions

None.
