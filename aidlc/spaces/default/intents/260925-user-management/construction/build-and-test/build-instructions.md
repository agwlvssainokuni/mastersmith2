# ビルド手順（build-instructions）

Intent `260925-user-management`（U1〜U8）のソースを開発者の PC で取得し、次の3つを行うまでの手順です。

- 統合の前の関門である1コマンドの検査（`./gradlew verify`）を通す
- 成果物（画面を同梱した実行可能 WAR）を得る
- ビルドした WAR で画面の確認（`./gradlew e2eTest`）を流す

前の Intent（`260923-dsl-schema-loader`）の手順と大きく違うのは、次の3点です。

- **サブモジュールが2つになった**：`vendor/java-mustache-processor` を Gradle の composite build で組みます（U1）。サブモジュールを取得していないと、Gradle の構成の段階で失敗します。
- **メールのテストが増えた**：JVM の中で起動するテスト用の SMTP の受け手（SubEtha SMTP）で、メールを実際に受けます（U1・U3）。このテストはコンテナを使いません。
- **E2E の前に Mailpit が必要になった**：`./gradlew e2eTest` の前に、手元のメールの受け手 Mailpit（compose の profile `mail`）を起動しておきます（U3・U6）。

すべてのコマンドはリポジトリのルート（`mastersmith2/`）で実行します。

## 1. 前提の道具

| 道具 | 版 | 入れ方の例（macOS） | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `brew install --cask temurin@25`、または SDKMAN の `sdk install java 25.0.4-tem` | ビルド・テスト |
| Node.js・npm | 24 | `brew install node@24`、または `nvm install 24` | 画面のビルド・テスト |
| Gitleaks | 8.30.1 | `brew install gitleaks` | 秘密情報の検出（`verifySecurity`） |
| OSV-Scanner | 2.6.0 | `brew install osv-scanner` | 依存関係の脆弱性の検査（`verifySecurity`） |
| Python と pre-commit | pre-commit 4.x | `brew install pre-commit` | コミットの前の検査 |
| colima と Docker CLI | Compose v2 | `brew install colima docker docker-compose` | 対象DB の結合テスト（`verifyIntegrationTest`）、Mailpit、コンテナでの起動、負荷の試験 |
| Playwright の Chromium | `@playwright/test` と同じ版 | `(cd frontend && npx playwright install chromium)` | E2E・実際のブラウザの検査・画面の時間の測り（`./gradlew e2eTest`） |
| k6 | コンテナのイメージ `grafana/k6:2.3.0` | 入れる必要はない（`docker run` で使う） | 負荷の試験（`perf/README.md`） |

- Gradle は Wrapper（`./gradlew`）で入るため、別に入れる必要はありません。
- `./gradlew verify` は次のときに、入れ方を示して失敗します（`checkToolchain`）。
  - Node.js の版が 24 でない
  - Gitleaks・OSV-Scanner が見つからない
- 内部DB（H2）を使うテストは、本番と同じ組み込みの H2 で動きます。コンテナを使うのは、対象DB（MySQL・MariaDB・PostgreSQL）の結合テストだけです。

## 2. 取得と依存関係の用意

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき（この Intent で java-mustache-processor が増えた）
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

| サブモジュール | 固定先 | 組み方 |
|---|---|---|
| `vendor/make-you-chic-ui` | `735ef04` | npm の `file:` の依存 |
| `vendor/java-mustache-processor` | `8d44c36`（タグ 0.1.0） | Gradle の composite build（`settings.gradle.kts` の `includeBuild`） |

- どちらのサブモジュールも、中身はこのリポジトリから変更しません（project.md の Forbidden）。`verifyPrepare` が、追跡されるファイルが変わっていないことを確かめます。
- 依存関係は lockfile（`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`・`backend/gradle.lockfile`）で版を固定しています。CI も lockfile どおりに入れます。
- Gradle の依存の取得元は Maven Central だけです（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。ビルドのプラグインは Gradle Plugin Portal から取ってよい、と読みます（project.md の Corrections）。
- make-you-chic-ui の側では、コントラストの直し（`7865c28`・`310e1ec`）がすでに main にあります。固定先の更新は、この Intent の後の別の作業とします（`build-and-test-questions.md` の Q7・F1）。この段は `735ef04` で確かめます。

依存関係は、`./gradlew verify` の 0 の段（`verifyPrepare`）が自動で用意します。個別に動かしたいときだけ、次を実行します。

```bash
(cd vendor/make-you-chic-ui && npm ci && npm run build)
(cd frontend && npm ci)
```

## 3. 環境の設定

### 3.1 コンテナの実行環境（ビルドとテストに必要）

colima の VM を CPU 4・メモリ 6GiB で起動します（README の「コンテナの資源の上限」）。

```bash
colima start --cpu 4 --memory 6
colima list          # CPUS が 4、MEMORY が 6GiB
```

Testcontainers は Docker の context を読みません。そのため、Docker の接続先を環境変数で渡します（シェルの設定ファイルに書いておきます）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- この2つを渡さないと、対象DB のテストが SKIPPED になります。その結果、パッケージごとのカバレッジの下限で失敗します（project.md の Testing Posture）。
- **対象DB のテストを飛ばした状態では統合しません**（team.md の Way of Working）。

### 3.2 Mailpit（E2E の前に必要）

```bash
docker compose --profile mail up -d mailpit    # 画面と API は http://127.0.0.1:8025、SMTP は 127.0.0.1:1025
```

- `./gradlew e2eTest` は、始める前に Mailpit の API に届くかを確かめます。届かなければ、起動の手順を示して失敗します。
- 1回の E2E で、Mailpit に招待のメールが届きます。E2E は Mailpit のメールを消しません（U5・U6 の基盤の設計の Q1）。片付けるときは、次のように Mailpit を止めて消します。
  ```bash
  docker compose stop mailpit && docker compose rm -f mailpit
  ```

### 3.3 起動するときだけ要るもの

ビルドとテストだけなら、環境変数は要りません。**起動する**ときは、`.env.example` を `.env` に写して値を入れます（`.env` は Git 管理外。コミットしません）。この Intent で足した主な変数は次のとおりです（全体は README の「環境変数」）。

| 変数 | 用途 |
|---|---|
| `SPRING_MAIL_HOST`・`SPRING_MAIL_PORT`・`MASTERSMITH_MAIL_FROM`（ほか `SPRING_MAIL_*`） | メールの送信（U1）。接続先と差出人の両方が無ければ送りません |
| `MASTERSMITH_WEB_BASE_URL` | 招待のリンクの元（U3）。Origin の確かめとエラー応答の `type` と共有します。入れたら `http://localhost:8080` で開きます |
| `MASTERSMITH_INVITATION_VALIDITY`・`_RETENTION`・`_CLEANUP_CRON` | 招待の有効期限・保存の期間・定期の削除（U3） |
| `MASTERSMITH_APPEARANCE_BRAND_COLOR`・`_FONT_FAMILY` | インスタンスの見た目（U8） |

## 4. ビルドのコマンド

| 目的 | コマンド | 出力 |
|---|---|---|
| 画面のビルド | `(cd frontend && npm run build)` | `frontend/dist/` |
| 成果物（画面を同梱した実行可能 WAR） | `./gradlew :backend:bootWar` | `backend/build/libs/mastersmith.war` |
| 統合の前の関門（ビルドを含む全検査） | `./gradlew verify` | 下の表のとおり |
| 件数とカバレッジを実測で報告するとき | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | テストのタスクを UP-TO-DATE で飛ばさない（project.md の Testing Posture） |
| E2E・実際のブラウザの検査・画面の時間 | `./gradlew e2eTest`（Mailpit を起動して） | `frontend/test-results/e2e-results.json`・`frontend/playwright-report/` |

`./gradlew verify` は次の順に実行します。1つでも失敗したら、後ろの段は実行しません。CI（GitHub Actions）も同じタスクを呼びます。

| 順 | 段（タスク） | この Intent で増えた確かめ |
|---|---|---|
| 0 | `verifyPrepare` | java-mustache-processor の追跡されるファイルが変わっていないこと |
| 1 | `verifyFormat` | メールのテンプレートの Mustache のコメント（`{{! ... }}`）の形のライセンスヘッダー |
| 2 | `verifyLint` | （変わらず）oxlint・ESLint・Stylelint |
| 3 | `verifyLicense` | （変わらず）画面のファイルのヘッダー |
| 4 | `verifyBuild` | composite build の java-mustache-processor のコンパイル |
| 5 | `verifyUnitTest` | U1〜U8 の単体テスト（JUnit・Vitest。jqwik・fast-check の性質ベースを含む） |
| 6 | `verifyIntegrationTest` | SubEtha SMTP でメールを受ける結合テスト、招待と登録の完了、プリファレンス、見た目の設定の結合テスト |
| 7 | `verifyCoverage` | 行 80%・分岐 70%。この Intent で `packagesJudgedByTotal` から外したパッケージ（一覧は 22 から 12 に減った）も、パッケージごとの下限の対象 |
| 8 | `verifySecurity` | SpotBugs で `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を priority によらず失敗にする |
| 9 | `verifyArtifact` | （変わらず）WAR と初回の JavaScript の量（500KB を超えたら警告だけ） |

- E2E は `verify` と CI に入れていません。画面・認証に関わる変更を統合する前と、リリースの前に手で実行します（team.md の Testing Posture）。
- テストの JVM・E2E の WAR・Dockerfile は、どれも `-Dh2.compactThreads=1` を付けて動きます（project.md の Tech Stack、U3）。

## 5. ビルドの確認

| 確かめること | 方法 | 期待 |
|---|---|---|
| 1コマンドの検査が通る | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | `BUILD SUCCESSFUL`（この段の実測は `test-results.md`） |
| 対象DB のテストが飛ばされていない | `backend/build/test-results/integrationTest/` の報告 | 飛ばし 0 件 |
| メールのテストが飛ばされていない | 同上（`mail`・`invitation` の `*IT`） | 飛ばし 0 件（コンテナを使わないため、飛ばす作りは無い） |
| 成果物ができている | `ls -l backend/build/libs/mastersmith.war` | ファイルがある |
| WAR の中にメールのテンプレートがある | `unzip -l backend/build/libs/mastersmith.war 'WEB-INF/classes/mail/templates/*'` | `invitation_ja.html`・`invitation_en.html` |
| コンテナで起動できる | `docker compose up --build -d` のあと、`curl -s localhost:8080/actuator/health` | `{"status":"UP"}` |

報告の出る場所は次のとおりです。

- テスト: `backend/build/reports/tests/`、`backend/build/test-results/`
- カバレッジ: `backend/build/reports/jacoco/test/jacocoTestReport.xml`、`frontend/coverage/`
- SpotBugs: `backend/build/reports/spotbugs/main.xml`
- OSV-Scanner: `build/reports/osv-scanner/osv.json`
- E2E: `frontend/test-results/e2e-results.json`、`frontend/playwright-report/`（どちらも Git 管理外。トークンを含む URL が載りうるため、共有しない。U6 の基盤の設計の Q2）

## 6. うまくいかないときの手当て

| 症状 | 原因 | 手当て |
|---|---|---|
| Gradle の構成の段階で `vendor/java-mustache-processor` が見つからない | サブモジュールを取得していない | `git submodule update --init` |
| `vendorUnchanged` が失敗する | サブモジュールの追跡されるファイルを変えた | 変更を取り消す。変更はそれぞれのリポジトリ側で行う |
| 対象DB のテストが SKIPPED になり、パッケージごとの下限で失敗する | colima が止まっている、`DOCKER_HOST` が無い・違う | colima を起動し、3.1 の環境変数を渡してやり直す |
| `./gradlew e2eTest` が始める前に失敗する | Mailpit が起動していない | 3.2 の手順で起動する |
| `./gradlew e2eTest` がブラウザが無くて失敗する | Chromium が入っていない | `(cd frontend && npx playwright install chromium)` |
| テストの件数が前回と同じまま、時間が極端に短い | テストのタスクが UP-TO-DATE で飛ばされた | `:backend:cleanTest :backend:cleanIntegrationTest` を付けてやり直す |
| `verifyCoverage` が失敗する | 行 80%・分岐 70% を下回った | **テストを足して満たす**。下限を下げる・計測の除外を増やす・`packagesJudgedByTotal` を増やすことはしない（team.md の Testing Posture） |
| `spotbugsGate` が失敗する | priority 1、または `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 | コードを直す。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| `osvScan` が失敗する | 実行時の依存に重大度 High 以上の脆弱性など | 版を上げる。開発時だけの依存は警告にとどまる（team.md の Deployment） |
| AccessTokenApiIT などが接続の失敗で落ちる | colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり | テストの JVM は `-Djava.net.preferIPv4Stack=true` で動く（project.md の Testing Posture）。流し直して再現するかを確かめ、原因を記録する |

## Sources

- 各単位の `code-generation-plan.md`（「Build and Test に引き継ぐこと」）、`unit-test-instructions.md`、`code-summary.md`（U1〜U8、`construction/u*/code-generation/`）
- `README.md`（前提の道具、取得と準備、1コマンドの検査、E2E、環境変数、メール（U1）、対象DB の結合テストとコンテナの実行環境）
- `aidlc/spaces/default/memory/team.md`（Way of Working、Testing Posture、Deployment、Code Style）・`aidlc/spaces/default/memory/project.md`（Testing Posture、Tech Stack、Forbidden、Corrections）
- `construction/build-and-test/build-and-test-questions.md`（Q7・F1）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/build-instructions.md`（書き方の見本）

## Assumptions & Open Questions

None.
