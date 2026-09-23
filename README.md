# MasterSmith

MasterSmith（マスタ管理アプリ）のリポジトリです。バックエンドは Java 25・Spring Boot 4（`backend/`）、画面は React＋TypeScript とデザインシステム make-you-chic-ui（`frontend/`、`vendor/make-you-chic-ui`）で作り、画面を同梱した実行可能 WAR を1つのコンテナで動かします。

現在の内容はアプリの骨格（U1）です。アプリの起動、内部DB（組み込みの H2）、ヘルスチェック、1行1件の JSON のログ、分散トレースと外部エクスポート（既定は無効）、共通のエラー応答、画面の骨組み（AppShell とログイン用レイアウト）、後の単位（U2 認証・U3 アクセス制御・U4 監査ログ）が使う差し込み口を持ちます。

## 前提の道具

| 道具 | 版 | 入れ方の例（macOS） | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `brew install --cask temurin@25`、または SDKMAN の `sdk install java 25.0.4-tem` | ビルド・テスト |
| Node.js・npm | 24 | `brew install node@24`、または `nvm install 24` | 画面のビルド・テスト |
| Gitleaks | 8.30.1 | `brew install gitleaks` | 秘密情報の検出（`./gradlew verify`） |
| OSV-Scanner | 2.6.0 | `brew install osv-scanner` | 依存関係の脆弱性の検査（`./gradlew verify`） |
| Python と pre-commit | pre-commit 4.x | `brew install pre-commit` | コミットの前の検査 |
| Docker（Docker Desktop または colima） | Compose v2 | `brew install colima docker docker-compose` | コンテナで動かすとき |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium` | ビルドした WAR での画面の確認（`./gradlew e2eTest`） |

- Gradle は Wrapper（`./gradlew`）を使うため、別に入れる必要はありません。
- `./gradlew verify` は、Node.js の版が 24 でない、または Gitleaks・OSV-Scanner が見つからないときは、入れ方を示して失敗します（検査を黙って飛ばしません）。
- コンテナの CPU の上限は既定で 4 です。colima を使う場合は、VM を CPU 4・メモリ 6GiB にしてください（`colima start --cpu 4 --memory 6`。確かめ方と、`.env` での上限の合わせ方は「コンテナの資源の上限」）。VM の CPU を増やせないときは、`.env` の `MASTERSMITH_CONTAINER_CPUS` で上限を下げて起動できます（ログインの照合の時間の目標は 4 が前提です）。
- U1 のテストはコンテナの実行環境を必要としません（内部DBは組み込みの H2 を使います）。

## 取得と準備

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

`vendor/make-you-chic-ui` の中身はこのリポジトリから変更しません。サブモジュールの固定先の更新は、承認を得た専用のコミットで行い、更新の前後のコミットのハッシュを記録します。

## 1コマンドの検査（統合の前の関門）

```bash
./gradlew verify
```

次の順に実行し、1つでも失敗したら後ろの段は実行しません。CI（GitHub Actions）も同じタスクを呼びます。

| 順 | 段（タスク） | 内容 |
|---|---|---|
| 0 | 準備（`verifyPrepare`） | 道具の確認、make-you-chic-ui の `npm ci`・ビルドと、サブモジュールの追跡されるファイルが変わっていないことの確認、画面の `npm ci` |
| 1 | フォーマット（`verifyFormat`） | Spotless（palantir-java-format、ライセンスヘッダーを含む）、Prettier |
| 2 | リンタ（`verifyLint`） | oxlint、ESLint、Stylelint |
| 3 | ライセンスヘッダー（`verifyLicense`） | 画面のファイルのヘッダー（Java・Gradle の Kotlin DSL は 1 の段の Spotless が確かめる） |
| 4 | ビルド（`verifyBuild`） | Java のコンパイル、`tsc --noEmit`、Vite のビルド |
| 5 | 単体テスト（`verifyUnitTest`） | JUnit（`*Test`）、Vitest |
| 6 | 結合テスト（`verifyIntegrationTest`） | Spring と組み込みの H2 を起動するテスト（`*IT`） |
| 7 | カバレッジの下限（`verifyCoverage`） | JaCoCo・`@vitest/coverage-v8`。行 80%・分岐 70% を下回ったら失敗 |
| 8 | 安全の検査（`verifySecurity`） | SpotBugs＋FindSecBugs（重大度 High で失敗）、OSV-Scanner（下の判定）、Gitleaks（リポジトリの履歴全体） |
| 9 | 成果物と量の確認（`verifyArtifact`） | 画面を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）、初回の読み込みの JavaScript の量（500KB を超えたら警告だけ） |

各段だけを実行することもできます（例: `./gradlew verifyFormat`）。報告は `backend/build/reports/`（テスト・JaCoCo・SpotBugs）、`frontend/coverage/`、`build/reports/osv-scanner/osv.json` に出ます。

### 依存関係の脆弱性の判定（OSV-Scanner）

検査の対象は `backend/gradle.lockfile`、`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json` です。

| 対象 | 統合を止める（失敗） | 警告だけ |
|---|---|---|
| バックエンド（Gradle） | 重大度 High 以上（CVSS 7.0 以上） | それ未満 |
| 画面の実行時の依存関係（`frontend/package.json` の `dependencies` とその依存。make-you-chic-ui の実行時の依存関係を含む） | 重大度 High 以上 | それ未満 |
| 画面の開発用の依存関係（`devDependencies` とその依存。make-you-chic-ui の開発用の依存関係を含む） | 成果物を作る道具（`config/npm-build-tools.txt` に書いたもの）の重大度 High 以上 | それ以外のすべて |
| すべての npm の依存関係 | 悪意のあるパッケージ（OSV の ID が `MAL-` で始まるもの）は重大度によらず失敗 | — |

- 実行時か開発用かは、lockfile の各項目の印（`dev`）で判定します。lockfile で見つからないものは実行時として扱います。
- 重大度の分からないものは警告として表示します。警告も毎回すべて表示されるので、定期的に見直してください。
- 画面を作る道具（Vite など）を足したり入れ替えたりしたときは、`config/npm-build-tools.txt` も見直してください。

## ビルドした WAR での画面の確認（E2E）

`./gradlew verify` と CI には入れていません。統合の前と、リリースの前に手で実行します。

```bash
cd frontend && npx playwright install chromium && cd ..   # 初回と Playwright の更新のとき
./gradlew e2eTest
```

WAR をビルドし、一時ディレクトリの内部DBで起動して、`frontend/e2e/` のすべての確認（U1 の画面の骨格、U2 のログインとログアウト、U3 の代表の流れ「ログイン → 管理画面に入れるか → ログアウト」）を CSP 違反やスクリプトのエラーなしに通ることを確かめます（番号は 18081。`E2E_PORT` で変えられます）。

## 開発時の起動

```bash
# バックエンド（http://localhost:8080、内部DBは ./backend/data/）
./gradlew :backend:bootRun

# 画面の開発サーバー（http://localhost:5173。/api と /actuator はバックエンドへ転送する）
cd frontend && npm run dev
```

開発サーバーは元の Host を保ったまま転送するため、エラー応答の `type` の URL は開発サーバーの Host から組み立てられます。

## コンテナでの起動と確認

配備は手で行い、コンテナに入れる WAR は手元で `./gradlew verify` を通して作ったものを使います。

```bash
git status --porcelain           # 何も表示されないこと（未コミットの変更があれば配備しない）
git rev-parse --short HEAD       # 配備する版のコミットのハッシュを控える（戻すときに使う）
./gradlew verify                 # 検査を通して WAR を作る（backend/build/libs/mastersmith.war）
cp .env.example .env             # 初回だけ。値を入れる（.env はコミットしない）
# 2回目以降は、ここで内部DBのデータを複写する（「内部DBのバックアップと戻し方」）
docker compose up -d --build
docker compose ps                # app が healthy になれば起動の完了（最長で約 2 分）
```

- 動いている版は、控えたコミットのハッシュで見分けます。イメージのタグは `local` のままです。
- ブラウザで `http://localhost:8080/` を開き、ログイン画面が表示されることを確かめます。ヘルスチェックの応答が UP で、下のスモークテストが通るまで、配備の完了とはみなしません。
- 初めての起動では、`.env` に `MASTERSMITH_AUTH_SIGNING_KEY` と初期管理者（`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）を入れておきます。起動のログに「初期管理者を作成しました」の INFO が出ることを確かめます（2回目以降の起動では作られません）。
- 配備の確認（スモークテスト、手で行う）: ログイン画面から初期管理者でログインし、ホームが表示されること、メニューの「管理」で管理者向け領域が開けること、ユーザーメニューのログアウトでログイン画面に戻ることを確かめます。あわせて、そのログインとログアウトの監査イベント2件（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）が記録されていることを「監査ログの確かめ方」の手順で確かめ、`docker compose logs app` に ERROR が出ていないことを見ます。
- ログは `docker compose logs -f app`（1行1件の JSON）で見ます。1つの要求のログは `traceId` で絞り込めます。
- 止めるときは `docker compose down`（内部DBのデータはボリュームに残ります）。

### 戻し方

直前の版のコミット（前回の配備で控えたハッシュ）を別の場所に取り出して WAR を作り直し、`docker compose up -d --build` でイメージを作り直して起動します。

```bash
docker compose stop app
# 内部DBのデータを複写する（次の節）
git worktree add ../mastersmith-rollback <直前の版のハッシュ>
(cd ../mastersmith-rollback && git submodule update --init && ./gradlew :backend:bootWar)
cp ../mastersmith-rollback/backend/build/libs/mastersmith.war backend/build/libs/mastersmith.war
docker compose up -d --build
docker compose ps                # healthy になったら、上のスモークテストを行う
git worktree remove ../mastersmith-rollback
```

スキーマの変更は前進のみ・後方互換のため、1つ前の版のアプリが今のスキーマで動きます。スキーマは戻しません。データが壊れたときだけ、配備の前に取ったバックアップを展開してデータも戻します（次の節。バックアップの後の記録は失われます）。

### 内部DBのバックアップと戻し方

アプリを止めてから、ボリュームのファイルを複写します。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$PWD":/backup eclipse-temurin:25.0.4_7-jre-noble \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M).tgz -C /data .
docker compose start app
```

戻すときは、アプリを止め、ボリュームの中身を消してからバックアップを展開します。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$PWD":/backup eclipse-temurin:25.0.4_7-jre-noble \
  sh -c 'rm -rf /data/* && tar xzf /backup/<バックアップのファイル> -C /data && chown -R 10001:10001 /data'
docker compose start app
```

コンテナの外で動かしている場合は、アプリを止めて `./data/`（`bootRun` なら `backend/data/`）を複写します。

### コンテナの資源の上限（colima の VM・メモリ・JVM）

colima の VM の大きさはリポジトリの外の設定のため、コミットでは固定できません。各自の PC で次のとおりにします。

```bash
# VM を CPU 4・メモリ 6GiB にする（VM を止めると、動いているコンテナも止まる）
docker compose stop app
colima stop
colima start --cpu 4 --memory 6
colima list                                              # CPUS が 4、MEMORY が 6GiB
docker info --format '{{.NCPU}} {{.MemTotal}}'           # 4 と約 6GB（VM の OS の分だけ 6GiB より少し小さい）
docker compose up -d --wait                              # app が healthy になるまで待つ
```

- VM の大きさの見積もり: 配備したアプリ（2GB）・負荷の試験の環境（2GB、`perf/README.md`）・手元の監視 `lgtm`（900MB）を合わせて約 4.9GB で、6GiB の内側に収まります。
- VM を広げた PC では、`.env` でコンテナの上限を `MASTERSMITH_CONTAINER_CPUS=4`・`MASTERSMITH_CONTAINER_MEMORY=2g` にし、`docker compose up -d --wait` でコンテナを作り直します。上限は `docker inspect mastersmith-app-1 --format '{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}'`（`4000000000 2147483648`）で確かめます。
- 照合に使える CPU は、コンテナの `cpus` と VM の CPU の数の小さい方で頭打ちになります。VM だけを広げても、`MASTERSMITH_CONTAINER_CPUS` が小さいままではログインは速くなりません。
- JVM の設定は `.env` の `MASTERSMITH_JAVA_OPTIONS` で足します（イメージの作り直しは要らず、コンテナの作り直しで効きます）。最大ヒープは既定でメモリの上限の 75% です。ヒープ以外（メタ領域・スレッドのスタック・直接バッファなど）はこの外側で使うため、割合を下げる（例: `-XX:MaxRAMPercentage=70.0`）か、ヒープ以外の上限（例: `-XX:MaxMetaspaceSize=256m`）を足して調整します。値は空白で区切り、空白を含む値は扱いません。JVM 標準の `JAVA_TOOL_OPTIONS` は使いません（コマンド行の 75% に上書きされ、起動の時に JSON でない行をログに出すため）。

#### 既知の制約（メモリの上限 1g と高い負荷）

- メモリの上限の既定は 1g のままです。1g では、最大ヒープ 768MB に対してヒープ以外に使えるのは約 256MB です。
- CPU の上限 2・メモリの上限 1g で、トークンの更新を同時 10 件・考える時間なし（毎秒約 3,000 件）で流したところ、約 35 秒でコンテナがメモリの上限で止まりました（OOMKilled、2026-09-23 の負荷の試験）。`restart: "no"` のため、止まったままになります。
- 同じくらいの負荷がかかりうるときは、`MASTERSMITH_CONTAINER_MEMORY` で上限を上げてください（VM を CPU 4・メモリ 6GiB にした PC では `2g`）。上限を上げると、最大ヒープも 75% の割合で増えます。2g での負荷の試験の結果は `perf/README.md` の末尾に記録します。メモリの内訳（ヒープとヒープ以外）の測り方も同じ文書にあります。
- 止まったかどうかは `docker inspect mastersmith-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'`（`true 137` なら上限で止まった）で確かめます。

#### 設定の効き方の確かめ

`Dockerfile`・compose を変えたときは、次のスクリプトで、メモリの上限の変数と JVM の設定の口が効くことを確かめます。JVM の `-version` だけを小さな上限（512MB）で動かすため、アプリは起動せず、秘密情報も要りません。配備したアプリを止めずに実行できます。

```bash
./gradlew :backend:bootWar && docker compose build app   # イメージ mastersmith:local を作る（配備したコンテナは作り直さない）
./docker/check-container-limits.sh                        # 期待と違う点があれば、期待の値と実際の値を出して失敗する
```

- 確かめること: 両方の compose の `mem_limit`（変数なしで 1g、`MASTERSMITH_CONTAINER_MEMORY=768m` で 768m）、最大ヒープの割合（変数なし・空の値で 75%、`MASTERSMITH_JAVA_OPTIONS` で 60%）、ヒープ以外の上限（`-XX:MaxMetaspaceSize=128m`）、java がコンテナの PID 1 であること（停止の合図を直接受け取る）、タイムゾーンの引数が残ること。
- 別のタグのイメージは `MASTERSMITH_IMAGE_TAG=<タグ> ./docker/check-container-limits.sh` で確かめます。前提は Docker（Compose v2）と、そのイメージがあることです。`.env` は読みません。

## 環境変数

秘密情報は `.env`（Git 管理外）から環境変数で渡します。見本は `.env.example` です。値を空にした変数は「空の値」として渡るため、既定値を使う設定は `.env` に書かないでください。

| 環境変数 | 既定値 | 内容 |
|---|---|---|
| `MASTERSMITH_CONTAINER_CPUS` | `4` | アプリのコンテナの CPU の上限（`docker compose` だけが使う）。Docker の VM の CPU が 4 に満たない PC では下げる。照合の時間の目標は 4 が前提 |
| `MASTERSMITH_CONTAINER_MEMORY` | `1g` | アプリのコンテナのメモリの上限（`docker compose` だけが使う。`2g`・`1536m` の形）。1g のままでは高い負荷で止まりうる（「コンテナの資源の上限」の「既知の制約」）。colima の VM を CPU 4・メモリ 6GiB にした PC では `2g` にする |
| `MASTERSMITH_JAVA_OPTIONS` | なし | JVM に足す引数（空白で区切る。例: `-XX:MaxRAMPercentage=70.0 -XX:MaxMetaspaceSize=256m`）。既定の引数（最大ヒープはメモリの上限の 75%、タイムゾーン Asia/Tokyo）の後ろに置くため、同じ指定は上書きになる。空白を含む値は扱わない。イメージの作り直しは要らず、コンテナの作り直し（`docker compose up -d`）で効く |
| `MASTERSMITH_DB_URL` | `jdbc:h2:file:./data/mastersmith` | 内部DBの接続先（コンテナでは `/app/data/mastersmith`） |
| `MASTERSMITH_DB_USERNAME` | `sa` | 内部DBの利用者 |
| `MASTERSMITH_DB_PASSWORD` | 空 | 内部DBのパスワード（秘密情報） |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` | `30` | 内部DBの接続プールの接続の数の上限。同時の要求がこの数に達すると監査の記録が欠けうる（「監査ログ（U4）」の「既知の制約」を参照） |
| `MASTERSMITH_HEALTH_DB_TIMEOUT` | `2s` | ヘルスチェックの内部DBの確認の制限時間 |
| `MASTERSMITH_WEB_BASE_URL` | なし | エラー応答の `type` の URL のベースURL（無ければ要求から組み立てる） |
| `MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS` | `false` | 転送元のヘッダー（`X-Forwarded-*`・`Forwarded`）を信頼するか |
| `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE` | `1MB` | 要求の本文の大きさの上限 |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` | `false` | 外部エクスポート（トレース・ログ・指標の OTLP の送信）を有効にするか |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT` | `http://localhost:4318` | OTLP の受け手（HTTP）のベースURL |
| `MASTERSMITH_TRACING_SAMPLING_PROBABILITY` | `1.0` | 外部へ送るトレースの割合（トレースIDは割合によらずすべての要求に付く） |
| `MASTERSMITH_TRACE_USE_DYNAMIC_LOGGER` | `true` | メソッドの呼び出しの追跡で、対象のクラスの名前のロガーを使うか |
| `MASTERSMITH_TRACE_HIDE_PROXY_CLASS_NAMES` | `true` | 追跡で、代理のクラス（プロキシ）の名前を隠すか |
| `MASTERSMITH_TRACE_LOG_EXCEPTION_STACK_TRACE` | `true` | 追跡で、例外のときにスタックトレースを出すか |
| `MASTERSMITH_TRACE_ENTER_MESSAGE` | `ENTER $[targetClassShortName]#$[methodName]($[arguments])` | 追跡の入るときの文言 |
| `MASTERSMITH_TRACE_EXIT_MESSAGE` | `EXIT  $[targetClassShortName]#$[methodName](): $[returnValue]` | 追跡の出るときの文言 |
| `MASTERSMITH_TRACE_EXCEPTION_MESSAGE` | `EXCEPTION $[targetClassShortName]#$[methodName](): $[exception]` | 追跡の例外のときの文言 |
| `MASTERSMITH_AUTH_SIGNING_KEY` | 空（必須） | アクセストークンの署名鍵（Base64、復元して 32 バイト以上。秘密情報。無い・短いと起動しない） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL` | 空 | 初期管理者のメールアドレス（無い・不正なら作らずに警告） |
| `MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD` | 空 | 初期管理者のパスワード（秘密情報。12 文字以上、UTF-8 で 72 バイト以内） |
| `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL` | `5m` | アクセストークンの有効期限 |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_TTL` | `24h` | リフレッシュトークンの有効期限（Cookie の寿命も同じ） |
| `MASTERSMITH_AUTH_LOCK_THRESHOLD` | `5` | ロックするまでの連続失敗回数（1 以上） |
| `MASTERSMITH_AUTH_LOCK_DURATION` | `30m` | ロックの時間 |
| `MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST` | `12` | パスワードのハッシュ（bcrypt）の cost（4〜31） |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_RETENTION` | `7d` | 無効・期限切れのリフレッシュトークンを、期限からどれだけ残してから消すか |
| `MASTERSMITH_AUTH_REFRESH_TOKEN_CLEANUP_CRON` | `0 30 3 * * *` | 使い終わったリフレッシュトークンの削除を行う時刻 |

- メソッドの呼び出しの追跡は、対象のクラスのロガーを TRACE にしたときだけ出ます（例: `LOGGING_LEVEL_CHERRY_MASTERSMITH_COMMON_ERROR=TRACE`）。
- 信号ごとの送り先の上書きは Spring Boot の設定で行えます（例: `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`）。
- ログのレベルは `LOGGING_LEVEL_<パッケージ>` で機能ごとに変えられます（既定は INFO）。
- 認証（U2）の署名鍵は必須です。`openssl rand -base64 32` で作った値を `.env` の `MASTERSMITH_AUTH_SIGNING_KEY` に入れます。値が無い・短いとアプリは起動しません。
- 署名鍵を替えるとき（鍵の交換）は、`.env` の値を替えてコンテナを作り直します。発行済みのアクセストークンは 401 になりますが、画面はトークンの更新で取り直すため、ログインし直しは要りません。
- 開発と E2E は `http://localhost` で行います。リフレッシュトークンの Cookie は `Secure` のため、localhost 以外のホスト名や IP への `http` ではログインが働きません。

## 外部エクスポートの確かめ方

受け取ったものを標準出力に出すだけの OTLP の受け手（OpenTelemetry Collector）を、profile `observability` で一緒に起動します。

```bash
# .env に次の2行を書く
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://otel-collector:4318
docker compose --profile observability up -d --build
curl -s http://localhost:8080/api/no-such-api > /dev/null
docker compose logs -f otel-collector      # トレース・ログはすぐ、指標は 60 秒ごとに届く
```

外部へ送るトレースからは、例外のメッセージとスタックトレース、要求の URL の問い合わせの部分を取り除いています。

## 手元の監視（Grafana）

指標・ログ・トレースを手元で見るときだけ、`grafana/otel-lgtm`（OTLP の受け手と Prometheus・Loki・Tempo・Grafana を1つにしたコンテナ）を profile `monitoring` で起動します。既定の起動（`docker compose up`）には含まれません。

```bash
# .env に次の2行を書く（見終わったら消す。送り先が無い間は送信の失敗の警告がログに出るため）
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true
#   MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318
docker compose --profile monitoring up -d --wait
# ブラウザで http://localhost:3000/ を開き、ダッシュボードの「MasterSmith」→「MasterSmith の概要」を見る
docker compose --profile monitoring stop lgtm   # 見終わったら止め、.env の2行を消して docker compose up -d で起動し直す
```

- 画面はログインなしの閲覧だけです（`127.0.0.1` にだけ結び付けています）。ダッシュボードと警報の決まりは `docker/monitoring/` のファイルで入れているため、画面からは変えられません。変えるときはファイルを直して `docker compose --profile monitoring up -d --force-recreate lgtm` で読み込み直します。
- 警報は外へは知らせません。Grafana の「Alerting」→「Alert rules」（フォルダー MasterSmith）で状態を見ます。
- 指標は 60 秒ごとに届きます。起動の直後は空のパネルがあります。起動より前のログ（Spring の起動のログ）は送られません。
- 監視のコンテナのメモリの上限は 900MB です。colima の VM は CPU 4・メモリ 6GiB を前提にしています（アプリ 2GB・負荷の試験の環境 2GB と合わせて約 4.9GB）。VM が小さいときは「コンテナの資源の上限」の手順で広げてください（`colima start --cpu 4 --memory 6`）。
- 集めたデータはボリューム `mastersmith_mastersmith-monitoring` に残ります。消すときは `docker volume rm mastersmith_mastersmith-monitoring`（アプリの内部DBのボリュームとは別です）。
- 外部エクスポートを有効にすると、JVM が `sun.misc.Unsafe` の警告を標準エラーに数行出します（送信に使う protobuf の部品による。1行1件の JSON ではありません）。

## プロキシを置く配備

- エラー応答の `type` の URL を固定するときは、`MASTERSMITH_WEB_BASE_URL` にベースURLを設定します。
- プロキシの内側からだけ受け付ける配備では、`MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS=true` にすると、転送元のヘッダーのスキーム・Host・接続元を使います。外から直接届く配備では有効にしないでください（ヘッダーを偽装できるため）。
- HTTPS（`Strict-Transport-Security` を含む）は、配備先が決まったときに扱います。

## スキーマの変更（Flyway）

- 置き場所: `backend/src/main/resources/db/migration`
- ファイルの名前: `V<番号>__<単位>_<内容>.sql`（例: `V2__u2_user_account.sql`）。単位ごとにファイルを分けます。
- 前進のみとし、適用済みのファイルは書き換えません（書き換えると起動時の検証で起動が止まります）。1つ前の版のアプリが動く後方互換を保ちます。
- Hibernate はスキーマを作らず、検証だけ行います。

## API のアクセス制御（U3）

`/api/` の下は**既定でログインが必要**です。ログインなしで呼べるのは、次の明示した一覧だけです。

| パス | 置く単位 | 理由 |
|---|---|---|
| `/actuator/health` | U1 | 起動確認 |
| `/api/problems/**` | U1 | 誰でも読める説明文書 |
| `/api/auth/login` | U2 | ログインする前に呼ぶ |
| `/api/auth/session/**` | U2 | 更新は Cookie で認証し、ログアウトは期限切れでも呼べる必要がある |

- **管理者のみの範囲**: `/api/admin` そのものと `/api/admin/**` は管理者だけが使えます。未ログインは 401 / `AUTHENTICATION_REQUIRED`、管理者でない利用者は 403 / `ACCESS_DENIED` になります。存在しない管理 API も、管理者でない利用者には 403 になります（有無を明かさないため）。管理者かどうかは、要求ごとに内部DBから読んだ値で判断します。
- 後の単位が管理者のみの API を足すときは、**`/api/admin/` の下に置いてください**（個々の API での宣言には頼りません）。それ以外の `/api/` の下は、置くだけでログインが必要になります。
- **確認用 API**: `GET /api/admin/check` は、管理者なら 204（内容なし）を返します。画面の管理者向け領域が、表示のたびにこれを呼びます。
- **正規化されていないパスの拒否**: エンコードされた区切り・`;`・`..`・連続した `//` などを含む要求は、判定の前に 400 / `REQUEST_REJECTED` で拒否します。応答はほかのエラーと同じ形（`type`・`code`・`traceId`）で、安全のためのヘッダーも付きます。拒否したパスはログに出しません。
- **画面**: サイドバーの「管理」は管理者にだけ表示されますが、これは表示の切り替えにすぎません。判定は必ずサーバー側で行われます。
- 環境変数は増えません。

## 監査ログ（U4）

認証（ログインの成功・失敗・ログアウト）と管理者のみの API へのアクセスの拒否を、内部DBの `audit_events` の表に1件ずつ**追記**します。

- **追記だけ**です。アプリは監査イベントを変える・消す処理を持ちません。保存の期間は無期限で、古い記録を消す仕組みもありません（保存の期間と古い記録の扱いは後続 Intent で決めます）。
- **監査ログを見る画面・API は、この Intent では作りません。** 当面の確認は、開発者が内部DBを読み取りで開いて行います。
- 記録する項目は、発生の日時・種類（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`）・結果・入力されたメールアドレス・失敗の理由・接続元IP・User-Agent・要求のパス（アクセスの拒否のときだけ）・トレースIDです。パスワード・トークン・ハッシュ値は記録しません。
- 監査イベントの `trace_id` は、同じ要求のアプリのログの `traceId` と一致します。1つの要求を追うときは、この値でログを絞り込みます。
- 記録に失敗しても、ログイン・ログアウト・401／403 の応答は変わりません。失敗したときは、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が1回出ます。**この ERROR には、記録しようとした項目（メールアドレスを含む）がキーと値で載ります**。後から手で記録を補えるようにするためで、U4 に限った扱いです。パスワード・トークンは載りません。
- 1件の書き込みに 200 ミリ秒を超えてかかった場合は、WARN（`監査イベントの記録に時間がかかりました`）が1回出ます。1日に数件以上出るときは、記録の量と内部DBの待ちを確かめてください。
- 環境変数は増えません。

### 既知の制約（同時の要求と接続プール）

- ログイン（成功・失敗）とログアウトでは、確定の後に同じスレッドで監査を記録します。このとき、業務で使った内部DBの接続（1本目）を持ったまま、記録のために2本目の接続を借ります。
- そのため、接続プールの上限（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`、既定 30）以上の同時の要求が来ると、2本目の接続の待ちが時間切れ（5 秒）になり、監査の記録が欠けることがあります。ログイン自体は成功し、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が出ます。上限は以前は 10 で、同時 10 件のログインでこの欠けが起きていました。
- Tomcat の同時処理のスレッドの上限は設定しておらず、既定の 200 です。プールの上限より多い要求が同時に来ることがあります。
- 同時の数を増やす場合は、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE` で上限を上げます。その際は、コンテナのメモリの上限の内側で動くこととヘルスチェックへの影響を、起動と負荷の試験で確かめてください。

### 監査ログの確かめ方

アプリを止めてボリュームを複写し、**複写したファイル**を読み取りで開きます（H2 のコンソールは使いません）。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$PWD":/backup eclipse-temurin:25.0.4_7-jre-noble \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M).tgz -C /data .
docker compose start app
# 複写を展開し、H2 の道具で読み取り（ACCESS_MODE_DATA=r）で開いて audit_events を読む
```

複写したファイルにもメールアドレスと接続元IPが残ります。実行の利用者と管理する者だけが読める場所に置いてください。

### 消してはいけない操作

- **ボリュームを消す操作（`docker compose down -v` など）を、バックアップの前に行わないでください。** 監査ログも一緒に消えます。
- 版を替える前は、上の「内部DBのバックアップと戻し方」の手順でボリュームを複写します。この手順で監査ログも一緒に守られます。
- OS の権限を持つ者によるファイルの直接の書き換えは、アプリでは検知できません（受け入れている危険です）。

## 後の単位（U2・U3・U4）が使う差し込み口

U1 のファイルは書き換えずに、次の型を使います。

| 差し込み口 | 形 | 使う単位 |
|---|---|---|
| 追加のアクセスの決まり | `cherry.mastersmith.common.security.SecurityRuleContributor`（`Ordered`）の Bean。order は U2 が 100 台、U3 が 200 台。同じ order が2つあると起動が失敗する。足してよいのはアクセスの決まり、トークンの検証、認証の入口と拒否の処理、要求の検査の拒否の処理で、ヘッダー・セッション・CSRF の設定は変えない | U2、U3 |
| API の既定の扱い | `cherry.mastersmith.common.security.ApiDefaultAccess` の Bean（0個か1個。2個以上は起動の失敗）。無ければ `/api/**` は許可、`requireAuthentication()` が true ならログイン必須 | U3 |
| フィルターの段階のエラー応答 | `cherry.mastersmith.common.security.ErrorResponseWriter`。401・403 などを共通の ErrorResponse の形で書く | U2、U3 |
| 想定内のエラー | `cherry.mastersmith.common.error.domain.BusinessException` を起こす。問題の種類（`ProblemType`、日英の説明つき）は自分のパッケージの `ProblemTypeCatalog` の Bean に置く（code・slug の重複は起動の失敗） | U2、U3、U4 |
| 要求中のトレースID | `cherry.mastersmith.common.observability.TraceIdProvider`（無ければ空） | U4 |
| ログに秘密情報が出ないことのテストの補助 | `backend/src/test/java` の `cherry.mastersmith.common.testsupport`（`JsonLogRecords` など） | U2 以降 |
| 画面の差し込み口 | `frontend/src/features/<featureId>/registration.ts` に `FeatureRegistration`（`frontend/src/app/registry/types.ts`）を `registration` という名前でエクスポートする。画面・サイドバーの項目・ユーザーメニューの項目・ログイン状態の提供元・文言（鍵は `<featureId>.` で始める）を登録できる。重複は画面の起動の失敗 | U2、U3 |
| ログイン用レイアウト | `frontend/src/app/layout/LoginLayout.tsx`（role=LOGIN の画面が、入力欄とボタンを子として置く） | U2 |
| スキーマの変更 | 上の「スキーマの変更（Flyway）」の決まり | U2、U4 |
| 検証済みの利用者（U2 が提供） | `cherry.mastersmith.auth.domain.AuthenticatedUser`（`userId`・`email`・`admin`）。要求ごとに DB から読んだ値で、Spring Security の認証の結果の主体に置く | U3 |
| トークンの認証の失敗（U2 が提供） | `cherry.mastersmith.auth.domain.TokenAuthenticationException`（`AuthenticationException` の子）と `TokenFailureReason`（`TOKEN_MALFORMED`・`TOKEN_INVALID`・`TOKEN_EXPIRED`・`USER_NOT_FOUND`）。トークンが無い要求は U2 の検証を通らず、Spring Security の「認証が足りない」の例外が届く | U3 |
| 認証の出来事（U2 が提供） | `cherry.mastersmith.auth.domain.AuthenticationEvent` を U2 のトランザクションの中で知らせる。受け取りは `@TransactionalEventListener(phase = AFTER_COMMIT)` で確定の後に同じスレッドで行う | U4 |
| 時計（U2 が提供） | `java.time.Clock` の Bean（UTC、`cherry.mastersmith.auth.service.AuthClockConfig`）。ほかの単位は別に定義せずこれを使う | U3、U4 |
| API 呼び出しの共通部分（U2 が提供） | `frontend/src/shared/api-client/` の `apiFetch`・`apiRequest`（アクセストークンの付与、401 での更新と送り直し）と `{ status, code }` の形のエラー | U3（画面） |
| アクセス拒否の出来事（U3 が提供） | `cherry.mastersmith.access.domain.AdminAccessDeniedEvent`（`eventType`＝`ACCESS_DENIED`・`occurredAt`・`result`＝`FAILURE`・`failureReason`・`enteredEmail`（分かるときだけ）・`sourceIp`・`userAgent`・`requestPath`・`traceId`）を `ApplicationEventPublisher` で知らせる。受け取りは `@EventListener` で**要求と同じスレッド・応答を書く前**に行う（内部DBの更新を伴わないため、U2 の認証の出来事と異なり確定の後ではない）。受け取り側の失敗で 401／403 の応答は変わらない | U4 |
| 役割・権限の判定の置き場所（U3 が提供） | `cherry.mastersmith.access.web.AdminAuthorizationManager`。後続 Intent で役割・権限の判定を足すときはここに足す | 後続 Intent |

- 秘密情報を持つ型は、文字列化（`toString`）でその項目を伏せ字にしてください（メソッドの呼び出しの追跡が引数と戻り値を文字列にするため）。
- 画面での表示の制御はサーバー側の権限の確認の代わりになりません。データは API の側で守ります。

## ライセンス

Apache License 2.0（`LICENSE`）。
