# ビルド手順（build-instructions）

Intent `260923-dsl-schema-loader`（U1 対象DB・U2 DSL の定義・U3 既定の DSL の生成・U4 DSL の管理・U5 DSL の管理画面）のソースを開発者の PC で取得し、統合の前の関門である1コマンドの検査（`./gradlew verify`）を通して、成果物（画面を同梱した実行可能 WAR）を得るまでの手順。

前の Intent（`260922-auth-audit-base`）の手順との大きな違いは、**対象DB の結合テストのためにコンテナの実行環境（colima）が必須になった**ことである（team.md の Way of Working・Testing Posture、Q1: A）。

すべてのコマンドはリポジトリのルート（`mastersmith2/`）で実行する。

## 1. 前提の道具

| 道具 | 版 | 入れ方の例（macOS） | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `brew install --cask temurin@25` または SDKMAN の `sdk install java 25.0.4-tem` | ビルド・テスト |
| Node.js・npm | 24 | `brew install node@24` または `nvm install 24` | 画面のビルド・テスト |
| Gitleaks | 8.30.1 | `brew install gitleaks` | 秘密情報の検出（`verifySecurity`） |
| OSV-Scanner | 2.6.0 | `brew install osv-scanner` | 依存関係の脆弱性の検査（`verifySecurity`） |
| Python と pre-commit | pre-commit 4.x | `brew install pre-commit` | コミットの前の検査 |
| colima と Docker CLI | Compose v2 | `brew install colima docker docker-compose` | **対象DB の結合テスト（`verifyIntegrationTest`）に必須**。コンテナでの起動・性能の測定にも使う |
| Playwright の Chromium | `@playwright/test` と同じ版 | `(cd frontend && npx playwright install chromium)` | E2E（`./gradlew e2eTest`）と画面の時間の測定（`perf/dsl-timing.sh --ui`） |
| k6 | コンテナのイメージ `grafana/k6:2.3.0` | 入れる必要なし（`docker run` で使う） | 性能の試験（Performance Validation。`perf/README.md`） |

- Gradle は Wrapper（`./gradlew`）で入るため、別に入れる必要はない。
- `./gradlew verify` は、Node.js の版が 24 でない、Gitleaks・OSV-Scanner が見つからないときに、入れ方を示して失敗する（`checkToolchain`）。検査を黙って飛ばさない。
- 内部DB（H2）を使うテストは、本番と同じ組み込みの H2 で動く（コンテナ不要）。コンテナを使うのは、対象DB（MySQL・MariaDB・PostgreSQL）の結合テストだけである（team.md の Testing Posture の読み替え）。

## 2. 取得と依存関係の用意

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

依存関係は `./gradlew verify` の 0 の段（`verifyPrepare`）が自動で用意する。個別に動かしたいときだけ次を実行する。

```bash
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

- 依存関係は lockfile（`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`）と `backend/gradle.lockfile` で版を固定している。CI も lockfile どおりに入れる（`npm ci`）。
- この Intent で足した依存（JDBC ドライバー3つ（MySQL Connector/J・MariaDB Connector/J・PostgreSQL JDBC）、SnakeYAML、networknt の JSON Schema の検証の部品 3.0.6 など）も lockfile で固定済み。Testcontainers と jqwik はテストだけで使う。
- `vendor/make-you-chic-ui` の中身はこのリポジトリから変更しない（project.md の Forbidden）。`verifyPrepare` の `vendorUnchanged` が、追跡されるファイルが変わっていないことを確かめる。

## 3. 環境の設定

### 3.1 コンテナの実行環境（ビルドとテストに必要）

colima の VM を CPU 4・メモリ 6GiB で起動する（README の「コンテナの資源の上限」）。

```bash
colima start --cpu 4 --memory 6
colima list          # CPUS が 4、MEMORY が 6GiB
```

Testcontainers は Docker の context を読まないため、Docker の接続先を環境変数で渡す（シェルの設定ファイルに書いておく）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
# 後片付けのコンテナ（Ryuk）が VM の中の Docker の接続口を使うため
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- 対象DB のイメージは、版とダイジェストを固定したもの（MySQL 8.4.11・MariaDB 11.8.9・PostgreSQL 18.6。正本は `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`）を使う。初回はイメージの取得に時間がかかる。
- Docker に届かないとき、開発中（環境変数 `CI` が無い）は、警告を出して対象DB のテストだけを中断（SKIPPED）にする設計である。**この状態では統合しない**。ただし、今の実装では4つのテストのクラスが中断ではなく失敗になる（6節と `test-results.md` 3.1）。CI（`CI=true`）では Docker に届かなければ失敗させる。
- 実測（2026-09-24）: `verify` の間の VM のメモリの使用量は最大 約 1,424MiB（配備したアプリを動かしたまま）で、6GiB の VM に余裕がある。

### 3.2 起動するときだけ要るもの

ビルドとテストだけなら環境変数は要らない。**起動する**ときは次を用意する。

```bash
cp .env.example .env
# .env を開き、値を入れる（.env は Git 管理外。コミットしない）
```

| 変数 | 用途 | 既定 |
|---|---|---|
| `MASTERSMITH_DB_PASSWORD` | 内部DB（H2）のパスワード（秘密情報） | なし（起動時に必要） |
| `MASTERSMITH_AUTH_SIGNING_KEY` | アクセストークンの署名鍵。256 ビット以上 | なし（起動時に必要） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_*` | 初期管理者のメールアドレスとパスワード | なし |
| `MASTERSMITH_TARGET_DB_*` | 対象DB の種類・接続先・ユーザー名・パスワード・スキーマ名（`mastersmith.target-db.*`） | なし（無くてもアプリは起動し、生成だけが「設定が無い」で失敗する） |
| `MASTERSMITH_CONTAINER_MEMORY` | アプリのコンテナのメモリの上限 | `1g`（CPU 4・6GiB の VM では `2g` にする） |

対象DB の接続情報は設定だけから受け取り、画面・API・DSL から受け取らない（project.md の Forbidden）。秘密情報はソースコード・設定ファイルに直接書かない。

## 4. ビルドのコマンド

| 目的 | コマンド | 出力 |
|---|---|---|
| 画面のビルド | `(cd frontend && npm run build)` | `frontend/dist/` |
| バックエンドのコンパイル | `./gradlew :backend:compileJava :backend:compileTestJava` | `backend/build/classes/` |
| 成果物（画面を同梱した実行可能 WAR） | `./gradlew :backend:bootWar` | `backend/build/libs/mastersmith.war` |
| 統合の前の関門（ビルドを含む全検査） | `./gradlew verify` | 下の表のとおり |
| 件数とカバレッジを実測で報告するとき | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | テストのタスクを UP-TO-DATE で飛ばさない（project.md の Testing Posture） |

`./gradlew verify` は次の順に実行し、1つでも失敗したら後ろの段は実行しない。CI（GitHub Actions）も同じタスクを呼ぶ。

| 順 | 段（タスク） | 内容 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui の `npm ci`・ビルド、サブモジュールが変わっていないことの確認、画面の `npm ci` |
| 1 | `verifyFormat` | Spotless（palantir-java-format、Java と Kotlin DSL のライセンスヘッダーを含む）、Prettier |
| 2 | `verifyLint` | oxlint（`react/no-danger` などのセキュリティ系のルールを含む）、ESLint、Stylelint |
| 3 | `verifyLicense` | 画面のファイルのライセンスヘッダー |
| 4 | `verifyBuild` | Java のコンパイル、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit（`*Test`）、Vitest |
| 6 | `verifyIntegrationTest` | Spring と組み込みの H2 を起動するテスト、対象DB 3種類のコンテナを使うテスト（`*IT`） |
| 7 | `verifyCoverage` | JaCoCo・`@vitest/coverage-v8`。行 80%・分岐 70% を下回ったら失敗。バックエンドは全体の合計に加え、新しいパッケージごとにも同じ下限（既存の 22 パッケージは `backend/build.gradle.kts` の `packagesJudgedByTotal` で全体の合計で判定） |
| 8 | `verifySecurity` | SpotBugs＋FindSecBugs（`spotbugsGate`。priority 1 と、パターン名が `SQL_` で始まる指摘は priority によらず失敗）、OSV-Scanner（`osvScan`）、Gitleaks（`gitleaksScan`、履歴全体） |
| 9 | `verifyArtifact` | 実行可能 WAR（`bootWar`）、WAR の中の JSON Schema が正本と一致すること（`verifyDslSchemaInWar`）、初回の読み込みの JavaScript の量（`frontendBundleSize`） |

- テストの JVM のヒープは 1g（`backend/build.gradle.kts` の `maxHeapSize`）。10MB の DSL を扱うテストでヒープが尽きたため U4 で上げた（project.md の Testing Posture の記録）。
- 各段だけを実行することもできる（例: `./gradlew verifyFormat`）。
- E2E（Playwright）は `verify` と CI に入れていない。統合の前とリリースの前に手で実行する（`integration-test-instructions.md` 2.3）。

```bash
(cd frontend && npx playwright install chromium)   # 初回と Playwright の更新のとき
./gradlew e2eTest
```

## 5. ビルドの確認

| 確かめること | 方法 | 期待 |
|---|---|---|
| 1コマンドの検査が通る | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | `BUILD SUCCESSFUL`（実測 4分14秒〜4分21秒） |
| 対象DB のテストが飛ばされていない | `backend/build/test-results/integrationTest/` の報告 | 飛ばし 0 件（実測 結合 375 件・飛ばし 0） |
| 成果物ができている | `ls -l backend/build/libs/mastersmith.war` | ファイルがある |
| JSON Schema が WAR に入っている | `verifyArtifact` の `verifyDslSchemaInWar` | 成功（`WEB-INF/classes/static/dsl/dsl-schema-v1.json` が正本と一致） |
| 初回の読み込みの JavaScript の量 | `verifyArtifact` の出力 | 500KB（gzip）以内（実測 111.4KB） |
| コンテナで起動できる | `docker compose up --build -d` のあと `curl -s localhost:8080/actuator/health` | `{"status":"UP"}` |

WAR の中の第三者のライセンスの文書は次で確かめられる（MariaDB Connector/J の LGPL 2.1 の文書が `WEB-INF/classes/META-INF/third-party-licenses/` にある）。

```bash
unzip -l backend/build/libs/mastersmith.war 'WEB-INF/classes/META-INF/third-party-licenses/*'
```

報告の出る場所。

- テスト: `backend/build/reports/tests/test/`、`backend/build/reports/tests/integrationTest/`、`backend/build/test-results/`
- カバレッジ: `backend/build/reports/jacoco/test/jacocoTestReport.xml`（と `html/`）、`frontend/coverage/`
- SpotBugs: `backend/build/reports/spotbugs/main.xml`
- OSV-Scanner: `build/reports/osv-scanner/osv.json`
- E2E: `frontend/playwright-report/`

## 6. うまくいかないときの手当て

| 症状 | 原因 | 手当て |
|---|---|---|
| `checkToolchain` が失敗する | Node.js が 24 でない、Gitleaks・OSV-Scanner が無い | 出力に示された入れ方で用意する。検査を飛ばす設定は作らない |
| 対象DB のテストが「コンテナの実行環境が無いため対象DB のテストを飛ばした」の警告で SKIPPED になる | colima が止まっている、`DOCKER_HOST` が無い・違う | `colima start` で起動し、3.1 の環境変数を確かめてから `verify` をやり直す。**飛ばした状態では統合しない** |
| 同じ状態で `MysqlTargetSchemaReaderIT` などが `initializationError`（`NoSuchElementException`、`OutputCapture.pop`）で失敗する | 既知の不具合（NFR12.3 の Not Met）。拡張の登録の順のため、中断のときに `OutputCaptureExtension` の後片付けが失敗する | 上と同じく colima を起動してやり直す。直し方の候補は `test-results.md` 3.1 |
| CI で対象DB のテストが失敗する | CI（`CI=true`）では Docker に届かなければ飛ばさずに失敗させる設計 | CI の実行環境に Docker があることを確かめる |
| テストの件数が前回と同じまま・時間が極端に短い | テストのタスクが UP-TO-DATE で飛ばされた | `:backend:cleanTest :backend:cleanIntegrationTest` を付けてやり直す |
| テストで `OutOfMemoryError` | テストの JVM のヒープの不足 | `backend/build.gradle.kts` の `maxHeapSize`（1g）を確かめる。下げない |
| `vendorUnchanged` が失敗する | `vendor/make-you-chic-ui` の追跡されるファイルを変えた | 変更を取り消す。変更は make-you-chic-ui のリポジトリ側で行う |
| `verifyFormat` が失敗する | 整形していない | `./gradlew spotlessApply :backend:spotlessApply` と `(cd frontend && npm run format)` |
| `verifyLicense` が失敗する | 新しいファイルにライセンスヘッダーが無い | 先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を入れる |
| `verifyCoverage` が失敗する | 行 80%・分岐 70% を下回った（全体か新しいパッケージ） | **テストを足して満たす**。下限を下げる・計測の除外を増やす・`packagesJudgedByTotal` に新しいパッケージを足すことはしない（team.md の Testing Posture） |
| `spotbugsGate` が失敗する | priority 1 の指摘か `SQL_` で始まる指摘 | コードを直す。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| `gitleaksScan` が失敗する | 秘密情報をコミットした | 値を環境変数に移し、履歴からも取り除く |
| `osvScan` が失敗する | 実行時の依存に重大度 High 以上の脆弱性など（README の判定の表） | 版を上げる。開発時だけの依存は警告にとどまる（team.md の Deployment） |
| `./gradlew e2eTest` がブラウザの無いことで失敗する | Chromium が入っていない | `(cd frontend && npx playwright install chromium)` |

## Sources

- `README.md`（前提の道具、取得と準備、1コマンドの検査、依存関係の脆弱性の判定、E2E、対象DB の結合テストとコンテナの実行環境、コンテナの資源の上限）
- `backend/build.gradle.kts`（`maxHeapSize`、`packagesJudgedByTotal`、カバレッジの下限）
- `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`
- `aidlc/spaces/default/memory/team.md`（Way of Working、Testing Posture、Deployment、Code Style）・`aidlc/spaces/default/memory/project.md`（Way of Working、Testing Posture、Forbidden、Mandated）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-and-test-questions.md`（Q1: A）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/test-results.md`（実測の時間・VM のメモリ・NFR12.3 の失敗）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/build-instructions.md`（書き方の見本）

## Assumptions & Open Questions

- VM のメモリの実測は、配備したアプリを動かしたままの1回（1回目の `verify`）だけである。配備したアプリを止めた状態は測っていない（余裕が大きいため、Q1: A の判断には影響しないと見た）。
