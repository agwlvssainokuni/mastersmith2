# ビルドの手順（build-instructions）

Intent `261004-role-menu`（U1〜U7、Bolt B1〜B9）のソースを開発者の PC で取得し、次の3つを行うまでの手順です。

- 統合の前の関門である1コマンドの検査（`./gradlew verify`）を通す
- 成果物（画面を同梱した実行可能 WAR）とコンテナのイメージを得る
- ビルドした WAR で画面の確認（`./gradlew e2eTest`）を流す

前の Intent（`261004-safety-carryover`）の手順と大きく違うのは、次の5点です。

- **make-you-chic-ui の固定先が `5bf1ffe` になった**（U7、B9 の最初の専用のコミット `e911e59`。`e82b651` から上げた。入れ子のサイドバー `navSections`）。取得し直さないと、画面のビルドとテストが前の版の部品で動きます。
- **内部DB の移行 V10〜V12 が足された**（U3 の V10: グループ・メンバーと監査の表の3列、U4 の V11: ロールと権限の設定、V12: 割り当てと作業ロール）。前進のみで表と列を足すだけです。`users.admin_flag` は残っています。
- **DSL の書式が版 2 になった**（U2。スキーマの階層とメニューの組。版 1 は受け付けない）。E2E と負荷の試験は版 2 の DSL を使います。
- **E2E が 18 ファイル・230 件になった**（U2 の 135、U6 の 140・150、U7 の 160・170。B9 の関門の実測）。
- **この段で、Dockerfile の JVM の既定の引数に `-XX:+ExitOnOutOfMemoryError` を足した**（`build-and-test-questions.md` の Q2: A）。テストの JVM には足していません。

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
- `./gradlew verify` は、Node.js の版が 24 でない、または Gitleaks・OSV-Scanner が見つからないときは、入れ方を示して失敗します（`checkToolchain`）。検査を黙って飛ばしません。
- この Intent で、実行時の新しい依存は足していません（各単位の NFR 要件の「新しい依存を足さない」の行。U7 の data router は既存の react-router にある）。依存の版は lockfile（`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`・`backend/gradle.lockfile`）で固定し、CI も lockfile どおりに入れます（`npm ci`）。
- 内部DB（H2）を使うテストは、本番と同じ組み込みの H2 で動きます。コンテナを使うのは、対象DB（MySQL・MariaDB・PostgreSQL）の結合テストだけです（`team.md` の Testing Posture）。

## 2. 取得と依存関係の用意

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき（この Intent で make-you-chic-ui の固定先が 5bf1ffe に上がった）
git submodule update --init
git submodule status        # 下の表の固定先になっていること

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

| サブモジュール | 固定先 | 組み方 |
|---|---|---|
| `vendor/make-you-chic-ui` | `5bf1ffe`（この Intent の B9 で `e82b651` から上げた。前後のハッシュは `construction/app-frame-ui/code-generation/code-summary.md` の 5節） | npm の `file:` の依存 |
| `vendor/java-mustache-processor` | `8d44c36`（タグ 0.1.0。この Intent では変えていない） | Gradle の composite build（`settings.gradle.kts` の `includeBuild`） |

- どちらのサブモジュールも、中身はこのリポジトリから変更しません（`project.md` の Forbidden）。`verifyPrepare` の `vendorUnchanged`・`mustacheVendorUnchanged` が、追跡されるファイルが変わっていないことを確かめます。
- make-you-chic-ui への追加の依頼（`make-you-chic-ui-request-2.md` の 3.2・3.3、Dropdown を画面に収める口）は上流に無く、この Intent では取り込みません（U7 の `code-summary.md` の 9節）。
- Gradle の依存の取得元は Maven Central だけです（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。ビルドのプラグインは Gradle Plugin Portal から取ってよい、と読みます（`project.md` の Corrections）。

### 2.1 `frontend/.npmrc`

```text
engine-strict=true
ignore-scripts=true
```

- `ignore-scripts=true` は、`frontend/` の中の npm のインストールでパッケージのスクリプト（install・postinstall など）を動かさないための設定です（`team.md` の Code Style）。
- `vendor/make-you-chic-ui` の中の `npm ci`（`vendorInstall`）には効きません（`project.md` の学び。`--ignore-scripts` は付けない）。
- `engine-strict=true` のため、Node.js が 24 でないとインストールで止まります。

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

Testcontainers は Docker の context を読みません。そのため、Docker の接続先を環境変数で渡します（README の「対象DB の結合テストとコンテナの実行環境」。シェルの設定ファイルに書いておきます）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- この2つを渡さないと、対象DB のテストが SKIPPED になります。その結果、パッケージごとのカバレッジの下限で失敗します（`project.md` の Testing Posture）。
- **対象DB のテストを飛ばした状態では統合しません**（`team.md` の Way of Working）。飛ばしてよいのは開発中の途中の実行だけです。CI では必ず実行します。

### 3.2 Mailpit（E2E の前に必要）

```bash
docker compose --profile mail up -d mailpit    # 画面と API は http://127.0.0.1:8025、SMTP は 127.0.0.1:1025
```

- `./gradlew e2eTest` は、始める前に Mailpit の API に届くかを確かめます。届かなければ、起動の手順を示して失敗します（Mailpit の起動・停止はしません）。
- 150・170 は、招待のメールを Mailpit から受けて利用者を自分で作ります。E2E は Mailpit のメールを消しません。片付けるときは Mailpit を止めて消します（`docker compose stop mailpit && docker compose rm -f mailpit`）。もとから動いていた Mailpit は、確かめのために止めません。

### 3.3 起動するときだけ要るもの

ビルドとテストだけなら、環境変数は要りません。**起動する**ときは、`.env.example` を `.env` に写して値を入れます（`.env` は Git 管理外。コミットしません。値は表示しません）。

- この Intent で `.env.example`・`compose.yaml` に足した環境変数はありません（`0415b94` までの差なし）。権限の YAML の本文の上限は設定 `mastersmith.role-transfer.max-bytes`（既定 5,242,880。この段の Q1 の決定で 10,485,760 から下げる直しの途中）で、環境変数 `MASTERSMITH_ROLETRANSFER_MAXBYTES` で上書きできますが、運用では設定しません。Q1 の測りでは、使い捨ての環境のアプリにだけ一時に上げて使いました。
- 起動のときに V10〜V12 が当たります。前の版のイメージへ戻すと、前の版は版 2 の DSL を読めず DSL が無い状態で起動し、ロール・グループ・メニューの画面も無くなります（戻しの手順と練習は deployment-pipeline・deployment-execution）。

## 4. ビルドのコマンド

| 目的 | コマンド | 出力 |
|---|---|---|
| 画面のビルド | `(cd frontend && npm run build)` | `frontend/dist/` |
| 成果物（画面を同梱した実行可能 WAR） | `./gradlew :backend:bootWar` | `backend/build/libs/mastersmith.war` |
| コンテナのイメージ（配備のタグ `local`） | `./gradlew :backend:bootWar && docker compose build app` | `mastersmith:local`（配備したコンテナは作り直さない） |
| 試験用のイメージ（配備のタグを上書きしない） | `./gradlew :backend:bootWar && docker build -t mastersmith:<この段の名前> .` | 例 `mastersmith:bt-role-menu`。負荷の試験・上限の測りの使い捨ての環境で使う（`perf/README.md` の手順 0） |
| 統合の前の関門（ビルドを含む全検査） | `./gradlew verify` | 下の表のとおり |
| 件数とカバレッジを実測で報告するとき | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | テストのタスクを UP-TO-DATE で飛ばさない（`project.md` の Testing Posture） |
| 依存関係の脆弱性の検査だけを流し直す | `caffeinate -dims ./gradlew osvScan --rerun-tasks` | lockfile が変わらないと `verify` の中では UP-TO-DATE になるため |
| E2E・実際のブラウザの検査・画面の時間 | `caffeinate -dims ./gradlew e2eTest`（Mailpit を起動して） | `frontend/test-results/e2e-results.json`（html の報告は作らない） |
| イメージの JVM の設定の確かめ | `./docker/check-container-limits.sh`（`MASTERSMITH_IMAGE_TAG=<タグ>` で別のタグ） | 期待と違う点があれば失敗する |

`./gradlew verify` は次の順に実行します。1つでも失敗したら、後ろの段は実行しません。CI（GitHub Actions）も同じタスクを呼びます。

| 順 | 段（タスク） | この Intent で増えた確かめ |
|---|---|---|
| 0 | `verifyPrepare` | make-you-chic-ui の固定先 `5bf1ffe` の追跡されるファイルが変わっていないこと |
| 1 | `verifyFormat` | （変わらず）palantir-java-format・Prettier |
| 2 | `verifyLint` | ESLint の機能どうしの import の制限（U1。機能の中の深さ 0〜6 の決まり、深さの上限を超えるファイルで設定の読み込みが失敗する）、oxlint のセキュリティ系の決まり |
| 3 | `verifyLicense` | （変わらず）画面のファイルのヘッダー |
| 4 | `verifyBuild` | `common.security`・`group.*`・`role.*`・`navigation.*` のパッケージ、画面の `shared/tree`・`features/roleadmin`・`groupadmin`・`roletransfer`・`tables`・`mypermissions`・`app/work-role`・`app/navigation` |
| 5 | `verifyUnitTest` | U1〜U7 の単体テスト（JUnit・Vitest。jqwik・fast-check の性質ベース、ArchUnit の構造の検査を含む） |
| 6 | `verifyIntegrationTest` | API の分類の実行時の検査（`ApiAccessConsistencyIT`）、グループ・ロール・割り当て・作業ロール・受け渡し・業務のメニューの結合テスト、同時の重なり、監査、秘密の漏えい |
| 7 | `verifyCoverage` | 行 80%・分岐 70%。新しいパッケージ（`group.*`・`role.*`・`navigation.*`）はパッケージごとの下限の対象。`packagesJudgedByTotal` は 7 パッケージのまま |
| 8 | `verifySecurity` | （変わらず）Gitleaks・SpotBugs＋FindSecBugs（priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらない）・OSV-Scanner |
| 9 | `verifyArtifact` | WAR、WAR の中の DSL の JSON Schema（v2 の2か所だけ。`verifyDslSchemaInWar`）、初回の JavaScript の量（500KB を超えたら警告だけ。B9 の関門の値は 158.3 KB） |

- E2E は `verify` と CI に入れていません。画面・認証に関わる変更とサブモジュールの固定先の更新を統合する前と、リリースの前に手で実行します（`team.md` の Testing Posture）。この段の直し（Q3 A・D）は画面に触れるため、統合の前に E2E の全体を流します。
- テストの JVM の設定（`backend/build.gradle.kts`）: 単体テストのタスクのヒープは 1536m（B7 で上げた）、結合テストは 1g。どちらも `-Dh2.compactThreads=1` と `-Djava.net.preferIPv4Stack=true` を付けて動きます（`project.md` の Tech Stack・Testing Posture）。`-XX:+ExitOnOutOfMemoryError` は付けません（Q2: A）。

## 5. ビルドの確認

| 確かめること | 方法 | 期待 |
|---|---|---|
| 1コマンドの検査が通る | `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | `BUILD SUCCESSFUL`（この段の実測は `test-results.md`） |
| 対象DB のテストが飛ばされていない | `backend/build/test-results/integrationTest/` の報告と、ログの「コンテナの実行環境」の警告 | 飛ばし 0 件・警告 0 件 |
| 移行 V10〜V12 が当たる | 結合テストの起動（`validate-on-migrate`・`ddl-auto: validate`）と `RoleSchemaIT`・`AuditMigrationCompatibilityIT` | 起動が通り、前の版の互換の確かめが通る |
| 成果物ができている | `ls -l backend/build/libs/mastersmith.war` | ファイルがある |
| イメージの JVM の引数 | `docker run --rm <イメージ> -XX:+PrintFlagsFinal -version` の `ExitOnOutOfMemoryError`・`MaxRAMPercentage` の行 | `ExitOnOutOfMemoryError = true`・`MaxRAMPercentage = 50.0`（`fix-notes.md` の 2節） |
| コンテナで起動できる | `docker compose up --build -d --wait` のあと `curl -s localhost:8080/actuator/health` | `{"status":"UP"}`。配備した環境では行わず、配備の段で扱う |
| 長いテストの間に PC が眠っていない | 流した後に `pmset -g log` の該当の時刻 | Sleep・DarkWake が無い |

報告の出る場所は次のとおりです。

- テスト: `backend/build/reports/tests/`、`backend/build/test-results/`
- カバレッジ: `backend/build/reports/jacoco/test/jacocoTestReport.xml`、`frontend/coverage/coverage-summary.json`
- SpotBugs: `backend/build/reports/spotbugs/main.xml`
- OSV-Scanner: `build/reports/osv-scanner/osv.json`
- 入口の量: `./gradlew frontendBundleSize` の出力と `frontend/dist/.vite/manifest.json`
- E2E: `frontend/test-results/e2e-results.json`（Git 管理外。値を含みうるため共有しない。記録してから消す。`integration-test-instructions.md` の 4.3）

## 6. うまくいかないときの手当て

| 症状 | 原因 | 手当て |
|---|---|---|
| 画面のテスト・E2E でサイドバーの形が違う（入れ子の区画が無い） | make-you-chic-ui のサブモジュールが前の固定先のまま | `git submodule update --init` で `5bf1ffe` にする |
| `vendorUnchanged`・`mustacheVendorUnchanged` が失敗する | サブモジュールの追跡されるファイルを変えた | 変更を取り消す。変更はそれぞれのリポジトリ側で行う |
| 対象DB のテストが SKIPPED になり、パッケージごとの下限で失敗する | colima が止まっている、`DOCKER_HOST` が無い・違う | colima を起動し、3.1 の環境変数を渡してやり直す |
| 使い捨ての環境の対象DB・DSL の初期化のファイルが読めない、VM から手元のファイルが見えない | colima の VM の中でホームの共有（sshfs）が外れている（dsl-v2 の B1 の D-26） | `colima ssh -- ls "$HOME"` などで共有を確かめ、外れていれば依頼者に伝えてから `colima stop` → `colima start`（配備したアプリなどのコンテナも止まるため、`docker start` で起こし直す。ボリュームは触れない）。受け渡しの一時の場所は `mktemp -d` ではなくホームの下（権限 700）に置く（`project.md` の Corrections） |
| 長いテスト・負荷の試験の途中で時間切れ・大きな遅れが出る | PC の自動のスリープ・DarkWake（B8 で「Dark Wake Thermal Emergency」で 664 秒休止し、画面のテスト2件が時間切れで落ちた） | `caffeinate -dims` で包む（負荷の試験の台本は `caffeinate -i` で全体を包む）。流した後に `pmset -g log` で Sleep・DarkWake を確かめ、休止があった回の結果は使わずに流し直す |
| テストの途中で別のビルドの結果が混ざる・クラスの読み込みで落ちる | テスト中に同じ作業フォルダで `./gradlew` を別に動かした（`backend/build/` を書き換える） | テストを流している間は、同じフォルダでビルド・`bootWar`・`spotlessApply` を動かさない。別の確かめはテストが終わってから行う |
| 単体テストで `OutOfMemoryError`（Java heap space） | 境界テスト（ArchUnit）が読み込んだクラスを持ち続ける（navigation の読み直し R-02、B7 で 1g から 1536m に上げた） | 原因をヒープの使い方で確かめてから扱いを依頼者に諮る。下限・テストの量を減らして通すことはしない |
| テストの件数が前回と同じまま、時間が極端に短い | テストのタスクが UP-TO-DATE で飛ばされた | `:backend:cleanTest :backend:cleanIntegrationTest` を付けてやり直す |
| `verifyCoverage` が失敗する | 行 80%・分岐 70% を下回った | **テストを足して満たす**。下限を下げる・計測の除外を増やす・`packagesJudgedByTotal` を増やすことはしない（`team.md` の Testing Posture） |
| `spotbugsGate` が失敗する | priority 1、または `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 | コードを直す。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| `osvScan` が失敗する | 実行時の依存に重大度 High 以上の脆弱性など | 版を上げる。開発時だけの依存は警告にとどまる（`team.md` の Deployment） |
| 画面のテストが `verify` の中だけで落ち、単独では通る（この段の `ShellLayout.test.tsx` の作業ロールの置き場の幅のテスト） | Gradle が `:frontendTest` を型の検査などと並べて動かすため負荷が高く、描画の後に届く値を同期で確かめると落ちうる（見立て、未検証。`fix-notes.md` の 8節） | `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。同じテストが二度目に落ちたら原因を直すまで次へ進まない。描画の後に反映される値は `waitFor` で待つ。時間の上限を原因を確かめずに延ばさない |
| AccessTokenApiIT などが接続の失敗で落ちる | colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり | テストの JVM は `-Djava.net.preferIPv4Stack=true` で動く。流し直して再現するかを確かめ、原因を記録する |
| `./gradlew e2eTest` が始める前に失敗する | Mailpit が起動していない | 3.2 の手順で起動する |
| E2E の報告の部品が値を見つけて失敗する | 失敗の写し（`error-context.md`）に利用者の値が入った | 写しで原因を確かめた後に `frontend/test-results/` を消す（共有しない）。合否は失敗のまま |

## 7. 気づいた食い違い（直した）

- README の「取得と準備」の節は make-you-chic-ui の固定先を `e82b651` と書いたままで、B9 の `5bf1ffe` が経緯に無かった。
- README の「対象DB の結合テストとコンテナの実行環境」の節は対象DB の版を「MySQL 8.4・MariaDB 11.8・PostgreSQL 18」と書いていたが、`TargetDbImages.java` の注記は MySQL を 26.7 の系列としていた（Intent 260928-quality-followup で上げた）。
- どちらも、この段で指揮役が README を直した（make-you-chic-ui の固定先を `5bf1ffe` とその経緯に、対象DB の版を MySQL 26.7・MariaDB 13.0 に）。直す前の状態は上の2行のとおり。

## Sources

- 各単位の `code-generation-plan.md`（「Build and Test に引き継ぐこと」）・`unit-test-instructions.md`・`code-summary.md`（`aidlc/spaces/default/intents/261004-role-menu/construction/<単位>/code-generation/`）
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-questions.md`（Q2）・`fix-notes.md`（2節・8節）
- `README.md`（前提の道具、取得と準備、1コマンドの検査、E2E、コンテナの資源の上限、対象DB の結合テストとコンテナの実行環境）・`perf/README.md`（手順 0）・`frontend/.npmrc`・`backend/build.gradle.kts`・`Dockerfile`（読み取り）
- `git submodule status`・`git log --oneline`（読み取り）
- `aidlc/spaces/default/memory/team.md`（Way of Working、Testing Posture、Deployment、Code Style）・`aidlc/spaces/default/memory/project.md`（Testing Posture、Tech Stack、Forbidden、Corrections）
- `aidlc/spaces/default/intents/260930-user-admin/construction/build-and-test/build-instructions.md`（書き方の見本）

## Assumptions & Open Questions

None.
