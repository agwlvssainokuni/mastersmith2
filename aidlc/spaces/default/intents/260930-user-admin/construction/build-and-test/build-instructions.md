# ビルド手順（build-instructions）

Intent `260930-user-admin`（U1〜U5、Bolt B1〜B5）のソースを開発者の PC で取得し、次の3つを行うまでの手順です。

- 統合の前の関門である1コマンドの検査（`./gradlew verify`）を通す
- 成果物（画面を同梱した実行可能 WAR）を得る
- ビルドした WAR で画面の確認（`./gradlew e2eTest`）を流す

前の Intent（`260929-log-deps-cleanup`）の手順と大きく違うのは、次の4点です。

- **make-you-chic-ui の固定先が `3d9521a` になった**（U5、C2 `364e9d6`）。取得し直さないと、画面のビルドとテストが前の版の部品で動きます。
- **`frontend/.npmrc` に `ignore-scripts=true` が入った**（U5、C1 `17af97d`）。npm のインストールでパッケージのスクリプト（install・postinstall など）が動きません。`frontend/` の中で行う `npm ci` だけに効きます。
- **内部DB の移行 V9 が足された**（U1、`V9__u1_user_suspension.sql`、`users.suspended`）。前進のみの変更で、起動のときに Flyway が当てます。
- **E2E が 13 ファイル・152 件になった**（U4 の 130、U5 の 110・120）。E2E の報告は html を作らず、trace は既定で off です（U5 の E2E の設定の直し）。

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
- `./gradlew verify` は次のときに、入れ方を示して失敗します（`checkToolchain`）。検査を黙って飛ばしません。
  - Node.js の版が 24 でない
  - Gitleaks・OSV-Scanner が見つからない
- この Intent で新しく足した道具・依存はありません（`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` は Intent の始め（`ded2653` の親）から `7689ade` まで差がありません。U2 の NFR9.11、U4 の NFR9.6、U5 の NFR9.2）。
- 内部DB（H2）を使うテストは、本番と同じ組み込みの H2 で動きます。コンテナを使うのは、対象DB（MySQL・MariaDB・PostgreSQL）の結合テストだけです（`team.md` の Testing Posture）。

## 2. 取得と依存関係の用意

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき（この Intent で make-you-chic-ui の固定先が 3d9521a に上がった）
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

| サブモジュール | 固定先 | 組み方 |
|---|---|---|
| `vendor/make-you-chic-ui` | `3d9521a`（この Intent の U5 で `077f5b4` から上げた。計画の `3481488` はその祖先として含まれる） | npm の `file:` の依存 |
| `vendor/java-mustache-processor` | `8d44c36`（タグ 0.1.0。この Intent では変えていない） | Gradle の composite build（`settings.gradle.kts` の `includeBuild`） |

- どちらのサブモジュールも、中身はこのリポジトリから変更しません（`project.md` の Forbidden）。`verifyPrepare` の `vendorUnchanged`・`mustacheVendorUnchanged` が、追跡されるファイルが変わっていないことを確かめます。
- make-you-chic-ui の閉じた後のフォーカスの直し（N-19）を含む版は、この Intent では取り込んでいません（`construction/code-generation/gate-decisions.md` の4節、後の Intent への持ち越し）。
- 依存関係は lockfile（`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`・`backend/gradle.lockfile`）で版を固定しています。CI も lockfile どおりに入れます（`npm ci`）。
- Gradle の依存の取得元は Maven Central だけです（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。ビルドのプラグインは Gradle Plugin Portal から取ってよい、と読みます（`project.md` の Corrections）。
- `frontend/.npmrc` の `ignore-scripts=true` は `frontend/` の中の npm のインストールだけに効きます。`vendor/make-you-chic-ui` の中の `npm ci`（`vendorInstall`）には効きません（U5 の NFR9.4）。

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

- この2つを渡さないと、対象DB のテストが SKIPPED になります。その結果、パッケージごとのカバレッジの下限で失敗します（`project.md` の Testing Posture）。
- **対象DB のテストを飛ばした状態では統合しません**（`team.md` の Way of Working）。

### 3.2 Mailpit（E2E の前に必要）

```bash
docker compose --profile mail up -d mailpit    # 画面と API は http://127.0.0.1:8025、SMTP は 127.0.0.1:1025
```

- `./gradlew e2eTest` は、始める前に Mailpit の API に届くかを確かめます。届かなければ、起動の手順を示して失敗します。
- 110（利用者の管理の代表の流れ）は、招待のメールを Mailpit から受けて利用者 U を自分で作ります。前提が無いときは理由の種類だけを注記（`skip-reason`）して飛ばします。統合の前とリリースの前の実行では、飛ばした注記が無いことを確かめます（U5 の NFR9.8）。
- E2E は Mailpit のメールを消しません。片付けるときは Mailpit を止めて消します（`docker compose stop mailpit && docker compose rm -f mailpit`）。もとから動いていた Mailpit は、確かめのために止めません。

### 3.3 起動するときだけ要るもの

ビルドとテストだけなら、環境変数は要りません。**起動する**ときは、`.env.example` を `.env` に写して値を入れます（`.env` は Git 管理外。コミットしません）。この Intent で足した環境変数はありません（新しい設定は無い。U1〜U5 の `code-summary.md`）。

- 起動のときに V9（`users.suspended`、既定 false）が当たり、既存の利用者は有効のままです（U1 の NFR10.1）。
- V9 の後に1つ前の版のイメージへ戻すと、利用停止が効かなくなります（U1 の NFR10.3、README の戻しの節）。戻す前に停止中の利用者を確かめる手順は deployment-pipeline で決めます。

## 4. ビルドのコマンド

| 目的 | コマンド | 出力 |
|---|---|---|
| 画面のビルド | `(cd frontend && npm run build)` | `frontend/dist/` |
| 成果物（画面を同梱した実行可能 WAR） | `./gradlew :backend:bootWar` | `backend/build/libs/mastersmith.war` |
| 統合の前の関門（ビルドを含む全検査） | `./gradlew verify` | 下の表のとおり |
| 件数とカバレッジを実測で報告するとき | `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | テストのタスクを UP-TO-DATE で飛ばさない（`project.md` の Testing Posture） |
| 依存関係の脆弱性の検査だけを流し直す | `caffeinate -i ./gradlew osvScan --rerun-tasks` | lockfile が変わらないと `verify` の中では UP-TO-DATE になるため |
| E2E・実際のブラウザの検査・画面の時間 | `caffeinate -i ./gradlew e2eTest`（Mailpit を起動して） | `frontend/test-results/e2e-results.json`（html の報告は作らない） |

`./gradlew verify` は次の順に実行します。1つでも失敗したら、後ろの段は実行しません。CI（GitHub Actions）も同じタスクを呼びます。

| 順 | 段（タスク） | この Intent で増えた確かめ |
|---|---|---|
| 0 | `verifyPrepare` | make-you-chic-ui の固定先 `3d9521a` の追跡されるファイルが変わっていないこと |
| 1 | `verifyFormat` | （変わらず）palantir-java-format・Prettier |
| 2 | `verifyLint` | （変わらず）oxlint（セキュリティ系の決まりを含む）・ESLint・Stylelint |
| 3 | `verifyLicense` | （変わらず）画面のファイルのヘッダー |
| 4 | `verifyBuild` | `useradmin`・`common.paging`・`common.persistence`・`invitation.lock` のパッケージ、画面の `features/useradmin`・`src/shared/paging`・`src/app/admin-forbidden` |
| 5 | `verifyUnitTest` | U1〜U5 の単体テスト（JUnit・Vitest。jqwik・fast-check の性質ベースを含む） |
| 6 | `verifyIntegrationTest` | 利用停止の3つの入口、利用者の管理の7つの API、排他と最後の管理者の保護、監査、上限切れの漏えいの結合テスト |
| 7 | `verifyCoverage` | 行 80%・分岐 70%。この Intent で `packagesJudgedByTotal` を 12 から 7 に減らした（B1 で `auth.domain`・`auth.repository`・`access.domain`、B4 で `common.error.web`・`common.observability` を外した）。外したパッケージと新しいパッケージはパッケージごとの下限の対象 |
| 8 | `verifySecurity` | （変わらず）Gitleaks・SpotBugs＋FindSecBugs（priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらない）・OSV-Scanner |
| 9 | `verifyArtifact` | WAR と初回の JavaScript の量（500KB を超えたら警告だけ。この段の値は 125.5 KB） |

- E2E は `verify` と CI に入れていません。画面・認証に関わる変更とサブモジュールの固定先の更新を統合する前と、リリースの前に手で実行します（`team.md` の Testing Posture、U5 の NFR9.8）。
- テストの JVM・E2E の WAR・Dockerfile は、どれも `-Dh2.compactThreads=1` を付けて動きます（`project.md` の Tech Stack）。

## 5. ビルドの確認

| 確かめること | 方法 | 期待 |
|---|---|---|
| 1コマンドの検査が通る | `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | `BUILD SUCCESSFUL`（この段の実測は `test-results.md`） |
| 対象DB のテストが飛ばされていない | `backend/build/test-results/integrationTest/` の報告 | 飛ばし 0 件 |
| 移行 V9 が当たる | 結合テストの起動（`validate-on-migrate`・`ddl-auto: validate`）と `UserSchemaIT` | 起動が通り、`users.suspended` の読み書きができる |
| 成果物ができている | `ls -l backend/build/libs/mastersmith.war` | ファイルがある |
| コンテナで起動できる | `docker compose up --build -d` のあと、`curl -s localhost:8080/actuator/health` | `{"status":"UP"}` |

報告の出る場所は次のとおりです。

- テスト: `backend/build/reports/tests/`、`backend/build/test-results/`
- カバレッジ: `backend/build/reports/jacoco/test/jacocoTestReport.xml`、`frontend/coverage/coverage-summary.json`
- SpotBugs: `backend/build/reports/spotbugs/main.xml`
- OSV-Scanner: `build/reports/osv-scanner/osv.json`
- E2E: `frontend/test-results/e2e-results.json`（Git 管理外。値を含みうるため共有しない。記録してから消す。`integration-test-instructions.md` の 4節）

## 6. うまくいかないときの手当て

| 症状 | 原因 | 手当て |
|---|---|---|
| 画面のテスト・E2E で make-you-chic-ui の部品の形が違う（Dropdown の押せない項目と理由の文が無い など） | サブモジュールが前の固定先のまま | `git submodule update --init` で `3d9521a` にする |
| `vendorUnchanged`・`mustacheVendorUnchanged` が失敗する | サブモジュールの追跡されるファイルを変えた | 変更を取り消す。変更はそれぞれのリポジトリ側で行う |
| 対象DB のテストが SKIPPED になり、パッケージごとの下限で失敗する | colima が止まっている、`DOCKER_HOST` が無い・違う | colima を起動し、3.1 の環境変数を渡してやり直す |
| `./gradlew e2eTest` が始める前に失敗する | Mailpit が起動していない | 3.2 の手順で起動する |
| 110 に `skip-reason` の注記が付く | 招待・Mailpit の前提が無い | 前提をそろえて流し直す。飛ばした状態で統合しない |
| テストの件数が前回と同じまま、時間が極端に短い | テストのタスクが UP-TO-DATE で飛ばされた | `:backend:cleanTest :backend:cleanIntegrationTest` を付けてやり直す |
| `verifyCoverage` が失敗する | 行 80%・分岐 70% を下回った | **テストを足して満たす**。下限を下げる・計測の除外を増やす・`packagesJudgedByTotal` を増やすことはしない（`team.md` の Testing Posture） |
| `spotbugsGate` が失敗する | priority 1、または `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 | コードを直す。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| `osvScan` が失敗する | 実行時の依存に重大度 High 以上の脆弱性など | 版を上げる。開発時だけの依存は警告にとどまる（`team.md` の Deployment） |
| `MailConfigurationIT` などの出力を捕まえるテストが1回だけ落ちる | 別のテストの文脈の背景のスレッドの出力（OTLP の指標の送信の WARN）が紛れ込みうる（U3 の B4 で1回。見立ては未検証） | `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。同じテストが二度目に落ちたら原因を直すまで進まない（後の Intent への持ち越し） |
| AccessTokenApiIT などが接続の失敗で落ちる | colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり | テストの JVM は `-Djava.net.preferIPv4Stack=true` で動く（`project.md` の Testing Posture）。流し直して再現するかを確かめ、原因を記録する |

## Sources

- 各単位の `code-generation-plan.md`（「Build and Test に引き継ぐこと」）・`unit-test-instructions.md`・`code-summary.md`（`aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/` 〜 `u5-user-admin-ui/` の `code-generation/`）
- `aidlc/spaces/default/intents/260930-user-admin/construction/code-generation/gate-decisions.md`（1節・4節）
- `README.md`（前提の道具、1コマンドの検査、E2E、対象DB の結合テストとコンテナの実行環境、戻しの節）
- `git submodule status`・`git diff --stat ded2653~1 7689ade`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Way of Working、Testing Posture、Deployment、Code Style）・`aidlc/spaces/default/memory/project.md`（Testing Posture、Tech Stack、Forbidden、Corrections）
- `aidlc/spaces/default/intents/260925-user-management/construction/build-and-test/build-instructions.md`（書き方の見本）

## Assumptions & Open Questions

None.
