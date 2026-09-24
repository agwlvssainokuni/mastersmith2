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
| Docker（Docker Desktop または colima） | Compose v2 | `brew install colima docker docker-compose` | コンテナで動かすとき、対象DB の結合テスト（`./gradlew verify`） |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium` | ビルドした WAR での画面の確認（`./gradlew e2eTest`） |

- Gradle は Wrapper（`./gradlew`）を使うため、別に入れる必要はありません。
- `./gradlew verify` は、Node.js の版が 24 でない、または Gitleaks・OSV-Scanner が見つからないときは、入れ方を示して失敗します（検査を黙って飛ばしません）。
- コンテナの CPU の上限は既定で 4 です。colima を使う場合は、VM を CPU 4・メモリ 6GiB にしてください（`colima start --cpu 4 --memory 6`。確かめ方と、`.env` での上限の合わせ方は「コンテナの資源の上限」）。VM の CPU を増やせないときは、`.env` の `MASTERSMITH_CONTAINER_CPUS` で上限を下げて起動できます（ログインの照合の時間の目標は 4 が前提です）。
- `./gradlew verify` はコンテナの実行環境（colima）が動いていることを前提にします。対象DB（MySQL・MariaDB・PostgreSQL）の結合テストを、版を固定したイメージのコンテナ（Testcontainers）で毎回実行するためです。内部DB を使うテストは、これまでどおり組み込みの H2 で動きます。設定と、届かないときの扱いは「対象DB（利用者の業務データの DB）」を参照してください。

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
| 6 | 結合テスト（`verifyIntegrationTest`） | Spring と組み込みの H2 を起動するテスト、対象DB（MySQL・MariaDB・PostgreSQL）のコンテナを使うテスト（`*IT`） |
| 7 | カバレッジの下限（`verifyCoverage`） | JaCoCo・`@vitest/coverage-v8`。行 80%・分岐 70% を下回ったら失敗。バックエンドは全体の合計に加えて、新しく作るパッケージ（`cherry.mastersmith.targetdb` 以後）ごとにも同じ下限を当てる（既存のパッケージは全体の合計で判定する。`backend/build.gradle.kts` の `packagesJudgedByTotal`） |
| 8 | 安全の検査（`verifySecurity`） | SpotBugs＋FindSecBugs（重大度 High と、SQL インジェクション系（パターン名が `SQL_` で始まる）の指摘は priority によらず失敗）、OSV-Scanner（下の判定）、Gitleaks（リポジトリの履歴全体） |
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

WAR をビルドし、一時ディレクトリの内部DBで起動して、`frontend/e2e/` のすべての確認を CSP 違反やスクリプトのエラーなしに通ることを確かめます（番号は 18081。`E2E_PORT` で変えられます）。1つの WAR と内部DBを共有するため、ファイル名の番号の順に1本ずつ実行します。

| ファイル | 確かめる流れ |
| --- | --- |
| `010-skeleton.e2e.ts` | 画面の骨格（ログイン画面の枠と CSP の応答ヘッダー） |
| `020-auth.e2e.ts` | 初期管理者のログインとログアウト、誤ったパスワードの表示 |
| `030-admin-access.e2e.ts` | 代表の流れ「ログイン → 管理画面に入れるか → ログアウト」 |
| `040-dsl-admin.e2e.ts` | DSL の管理「ログイン → DSL の管理 → 貼り付けで投入 → プレビュー → 適用 → 今の状態が適用中 → ログアウト」（対象DB は設定しないため、照合は「接続先が設定されていません」の警告になる） |

## 開発時の起動

```bash
# バックエンド（http://localhost:8080、内部DBは ./backend/data/）
./gradlew :backend:bootRun

# 画面の開発サーバー（http://localhost:5173。/api と /actuator と /dsl はバックエンドへ転送する）
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
| `MASTERSMITH_DB_URL` | `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE` | 内部DBの接続先（コンテナでは `/app/data/mastersmith`）。`;DEFRAG_ALWAYS=TRUE` は、アプリの停止時（DB を閉じるとき）にファイルを詰め直す指定。付けないと、DSL の履歴の古い行を消しても H2 のファイルが縮まず、投入と適用を重ねるたびに大きくなる（起動し直しても縮まない）。上書きするときも `;DEFRAG_ALWAYS=TRUE` を付ける |
| `MASTERSMITH_DB_USERNAME` | `sa` | 内部DBの利用者 |
| `MASTERSMITH_DB_PASSWORD` | 空 | 内部DBのパスワード（秘密情報） |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` | `30` | 内部DBの接続プールの接続の数の上限。同時の要求がこの数に達すると監査の記録が欠けうる（「監査ログ（U4）」の「既知の制約」を参照） |
| `MASTERSMITH_HEALTH_DB_TIMEOUT` | `2s` | ヘルスチェックの内部DBの確認の制限時間 |
| `MASTERSMITH_WEB_BASE_URL` | なし | エラー応答の `type` の URL のベースURL（無ければ要求から組み立てる） |
| `MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS` | `false` | 転送元のヘッダー（`X-Forwarded-*`・`Forwarded`）を信頼するか |
| `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE` | `1MB` | 要求の本文の大きさの上限（DSL の投入の API を除く。ログインと認可の確かめの後に確かめる。「DSL の管理の API（U4）」を参照） |
| `MASTERSMITH_DSL_MAX_SUBMIT_SIZE` | `10MB` | DSL の投入の API（`POST /api/admin/dsl/preview`）だけの要求の本文の上限（10,485,760 バイト） |
| `MASTERSMITH_DSL_HISTORY_LIMIT` | `20` | DSL の適用の履歴の件数の上限（1 以上）。超えた分は古いものから消す |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED` | `false` | 外部エクスポート（トレース・ログ・指標の OTLP の送信）を有効にするか |
| `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT` | `http://localhost:4318` | OTLP の受け手（HTTP）のベースURL |
| `MASTERSMITH_TRACING_SAMPLING_PROBABILITY` | `1.0` | 外部へ送るトレースの割合（トレースIDは割合によらずすべての要求に付く） |
| `MASTERSMITH_TRACE_USE_DYNAMIC_LOGGER` | `true` | メソッドの呼び出しの追跡で、対象のクラスの名前のロガーを使うか |
| `MASTERSMITH_TRACE_HIDE_PROXY_CLASS_NAMES` | `true` | 追跡で、代理のクラス（プロキシ）の名前を隠すか |
| `MASTERSMITH_TRACE_LOG_EXCEPTION_STACK_TRACE` | `true` | 追跡で、例外のときにスタックトレースを出すか |
| `MASTERSMITH_TRACE_ENTER_MESSAGE` | `ENTER $[targetClassShortName]#$[methodName]($[arguments])` | 追跡の入るときの文言 |
| `MASTERSMITH_TRACE_EXIT_MESSAGE` | `EXIT  $[targetClassShortName]#$[methodName](): $[returnValue]` | 追跡の出るときの文言 |
| `MASTERSMITH_TRACE_EXCEPTION_MESSAGE` | `EXCEPTION $[targetClassShortName]#$[methodName](): $[exception]` | 追跡の例外のときの文言 |
| `MASTERSMITH_TARGET_DB_TYPE` | 空 | 対象DB の種類（`mysql`・`mariadb`・`postgresql`）。7つの項目（種類・ホスト・番号・DB の名前・スキーマ・ユーザー名・パスワード）がすべて空なら対象DB を使わない |
| `MASTERSMITH_TARGET_DB_HOST` | 空 | 対象DB のホスト名（英数字と `.`・`-`・`_`、または `[...]` の IPv6） |
| `MASTERSMITH_TARGET_DB_PORT` | 空 | 対象DB の番号（1〜65535） |
| `MASTERSMITH_TARGET_DB_DATABASE` | 空 | 接続する DB の名前（MySQL・MariaDB はスキーマと同じ） |
| `MASTERSMITH_TARGET_DB_SCHEMA` | 空 | 読み取るスキーマの名前 |
| `MASTERSMITH_TARGET_DB_USERNAME` | 空 | 対象DB のユーザー名（読み取りの権限だけのアカウントを勧める） |
| `MASTERSMITH_TARGET_DB_PASSWORD` | 空 | 対象DB のパスワード（秘密情報） |
| `MASTERSMITH_TARGET_DB_CONNECT_TIMEOUT` | `3s` | 対象DB への接続の待ちの上限 |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_GENERATE` | `20s` | 既定の DSL の生成で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_COMPARE` | `5s` | 照合で、問い合わせ1回の待ちの上限（1 秒以上） |
| `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` | `5` | 対象DB の接続の数の上限（内部DB のプールとは別） |
| `MASTERSMITH_TARGET_DB_POOL_IDLE_TIMEOUT` | `60s` | 使っていない対象DB の接続を閉じるまでの時間（10 秒以上） |
| `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` | 空 | 手元で試す対象DB（compose の profile `targetdb-*`）の管理者のパスワード（秘密情報。アプリは使わない） |
| `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` | 空 | 手元で試す対象DB の読み取りだけのアカウント `mastersmith_reader` のパスワード（秘密情報） |
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

## 対象DB（利用者の業務データの DB）

アプリは、利用者の業務データの DB（対象DB。MySQL・MariaDB・PostgreSQL）を、読み取り専用の接続で読みます（今は、設定したスキーマのテーブル・ビュー・カラム・キー・コメントのメタデータだけ）。接続先は環境変数 `MASTERSMITH_TARGET_DB_*`（「環境変数」の表）だけから受け取り、画面・API から受け取りません。

- 7つの項目がすべて空なら対象DB を使わず、起動は続きます。一部だけ空・不正なら、問題のある項目の名前（例: `mastersmith.target-db.schema`）だけを WARN で1件出し、対象DB を使いません（値はログに出しません）。直したら再起動します。
- 起動時には対象DB に接続しません。ヘルスチェックは内部DB だけで判断し、対象DB の状態は応答に含めません。
- ログイン・監査ログ・Flyway は内部DB を使い続け、対象DB には表を作りません。
- 待ちの上限は、接続の待ち（既定 3 秒）と、問い合わせ1回ごとの待ち（既定の DSL の生成 20 秒・照合 5 秒）だけです。読み取りの全体の上限はありません。読み取りは問い合わせ4回のため、応答しない対象DB では、照合でも最悪 接続 3 秒＋5 秒×4 回（約 23 秒）かかることがあります（照合の 10 秒の目標を超えることを許す決定です）。
- 読めなかったときは、原因の種類（`TIMEOUT`・`CONNECTION_FAILED`）と SQLState だけを WARN で出します。接続先・ユーザー名・パスワード・ドライバーの例外の文言は出しません。
- 3つのドライバー（MariaDB・PostgreSQL・MySQL）自身のログは、`backend/src/main/resources/application.yaml` の `logging.level` で止めています（`org.mariadb.jdbc`・`org.postgresql`・`com.mysql.cj` を `OFF`）。ドライバーのログは接続先・ユーザー名・例外の文言を含むことがあり、MariaDB のドライバーは認証の失敗をユーザー名つきで WARN に出すことを結合テストで確かめました。MySQL・PostgreSQL のドライバーも同じ扱いにそろえています。
- 読めない原因を調べるときは、まず読み取りの口（`JdbcTargetSchemaReader`）の WARN の `reason` と `sqlState` で絞ります（`08` で始まる: 接続できない、`28` で始まる: 認証の失敗、`57014`: 問い合わせの打ち切り、`TIMEOUT` で SQLState が無い: 接続の待ちの間に応答が無い）。さらに調べるときは、同じネットワークから DB の付属のクライアント（`psql`・`mysql`・`mariadb`）で接続を試すか、対象DB の側のログを見てください。配備した環境でドライバーのログを有効にしないでください（接続情報がログに残ります）。
- 対象DB のアカウントは、読み取りの権限だけにしてください（MySQL・MariaDB は `GRANT SELECT, SHOW VIEW ON <DB>.*`、PostgreSQL は `GRANT USAGE ON SCHEMA` と `GRANT SELECT ON ALL TABLES IN SCHEMA`）。アプリは接続を読み取り専用にし、書き込み・DDL を発行するコードを持ちませんが、アカウントの権限は調べません。

### 対象DB の結合テストとコンテナの実行環境

対象DB の結合テストは、版とダイジェストを固定したイメージ（`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`）の MySQL 8.4・MariaDB 11.8・PostgreSQL 18 を Testcontainers で起動して行います。`./gradlew verify` の中で3種類とも毎回実行します（CI も同じ）。

Testcontainers は Docker の context を読まないため、colima を使う PC では、Docker の接続先を環境変数で渡してください（シェルの設定ファイルに書いておくと便利です）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
# 後片付けのコンテナ（Ryuk）が VM の中の Docker の接続口を使うため
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- Docker に届かないとき、開発中（環境変数 `CI` が無い）は、対象DB のテストだけを中断（`SKIPPED`）にし、「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない」という警告を出します。**この状態では統合しません。** `colima start` で起動し、上の環境変数を確かめてから `./gradlew verify` をやり直してください。
- CI（GitHub Actions は `CI=true` を設定する）では、Docker に届かなければ飛ばさずに失敗します。
- 単位のテストだけを実行するとき: `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'`（1種類だけなら `'cherry.mastersmith.targetdb.*Postgres*'` など）。

### 手元で試す対象DB（compose の profile）

画面からスキーマの読み込みを試すときや、読み取りの時間を測るときは、見本の対象DB を compose の profile で1つずつ起動します。ポートは PC に開けず、アプリのコンテナから compose の中の名前で接続します。

1. `.env` に `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` と `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` を入れる（空だと DB を作れません）。
2. 起動する（初めての起動で、見本のスキーマと読み取りだけのアカウント `mastersmith_reader` を `docker/targetdb/<種類>/` の SQL と台本で作ります）。

   ```bash
   docker compose --profile targetdb-postgres up -d targetdb-postgres   # PostgreSQL（DB business・スキーマ sales）
   docker compose --profile targetdb-mysql up -d targetdb-mysql         # MySQL（DB・スキーマ business）
   docker compose --profile targetdb-mariadb up -d targetdb-mariadb     # MariaDB（DB・スキーマ business）
   ```

3. アプリに設定する（例: PostgreSQL）。`.env` に次を書き、`docker compose up -d app` でアプリのコンテナを作り直します。

   ```text
   MASTERSMITH_TARGET_DB_TYPE=postgresql
   MASTERSMITH_TARGET_DB_HOST=targetdb-postgres
   MASTERSMITH_TARGET_DB_PORT=5432
   MASTERSMITH_TARGET_DB_DATABASE=business
   MASTERSMITH_TARGET_DB_SCHEMA=sales
   MASTERSMITH_TARGET_DB_USERNAME=mastersmith_reader
   MASTERSMITH_TARGET_DB_PASSWORD=<MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD と同じ値>
   ```

   MySQL・MariaDB は `TYPE` を `mysql`・`mariadb`、`HOST` を `targetdb-mysql`・`targetdb-mariadb`、`PORT` を `3306`、`DATABASE` と `SCHEMA` を `business` にします。

4. 読み取りの時間を測るための、テーブル 100 個 × カラム 100 個のスキーマ `large` を作るとき（アプリの `SCHEMA`（MySQL・MariaDB は `DATABASE` も）を `large` にする）:

   ```bash
   ./docker/targetdb/generate-large-schema.sh postgres \
     | docker compose exec -T targetdb-postgres psql -v ON_ERROR_STOP=1 -U target_admin -d business
   ./docker/targetdb/generate-large-schema.sh mysql \
     | docker compose exec -T targetdb-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root'
   ./docker/targetdb/generate-large-schema.sh mariadb \
     | docker compose exec -T targetdb-mariadb sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --user=root'
   ```

5. 止める・消す: `docker compose --profile targetdb-postgres stop targetdb-postgres`。データごと消すときは `docker compose --profile targetdb-postgres rm -sf targetdb-postgres` の後に `docker volume rm mastersmith_mastersmith-targetdb-postgres`（mysql・mariadb も同じ形）。

メモリの上限は PostgreSQL・MariaDB が 512MB、MySQL が 768MB です。3つを同時に起動すると、colima の VM（6GiB）のうち約 2GB を使います。

## DSL の書式（JSON Schema）と読み込みの上限

DSL（YAML）の書式は JSON Schema（2020-12）で定めています。正本は `backend/src/main/resources/dsl/dsl-schema-v1.json` の1つで、アプリの検証もこれを読みます。ビルドのときに画面の静的なファイルの置き場へ複写し、ログインなしで次の URL から取れます（秘密は含みません）。

- 起動したアプリ: `http://<ホスト>:8080/dsl/dsl-schema-v1.json`（画面の開発サーバーでは `http://localhost:5173/dsl/dsl-schema-v1.json`）
- WAR の中の複写が正本と同じことは、`./gradlew verify` の 9 の段（`:backend:verifyDslSchemaInWar`）で確かめます。

エディタで DSL を書くときは、YAML の言語サーバー（VS Code の YAML の拡張など）に JSON Schema を指定すると、項目の補完と誤りの表示が使えます。DSL の先頭に次の1行を書くか、エディタの設定で `*.yaml` に JSON Schema を結び付けてください。

```yaml
# yaml-language-server: $schema=http://localhost:8080/dsl/dsl-schema-v1.json
version: 1
```

エディタの検証は補助です。正はサーバー側の検証で、画面や API で投入した DSL はすべてサーバー側で検証します。サーバー側では、エディタでは確かめられない次の決まりも確かめます。

| 段 | 決まり |
|---|---|
| 大きさ | 本文が 10MB（10,485,760 バイト）を超えたら読みません |
| 読み込み | 入れ子の深さは 50 まで。対応表・並びを指す別名（`*name`）は 100 個まで。別名を展開した後の節の数は 1,000,000 まで（別名の展開の爆発を止めます）。タグ（`!!str`・`!!java...`・`!custom` など）はすべて拒否します。1つの対応表の中の同じキーは誤りにします（後の値で上書きしません） |
| 書式の版 | `version` は 1 だけ |
| 構文 | JSON Schema に合うこと（書式に無い項目は誤り。接続先・ユーザー名・パスワードなどの項目も書けません） |
| 意味 | メニュー・主キー・外部キー・選択肢の参照の先のテーブルとカラムがあること、一覧の並び順が重ならないこと、フォーム部品と選択肢の出どころが合うこと、`min` ≦ `max`・`minLength` ≦ `maxLength`、`pattern` が正しい正規表現で 1,000 文字まで（確かめは 1 件 100 ミリ秒まで） |

- YAML は 1.1 の暗黙の型で読みます。`yes`・`no`・`on`・`off` は真偽値になるため、文字として書くときは `"yes"` のように引用符で囲んでください。
- 誤りは、YAML の行・列と DSL の中の場所（例 `tables.dept_mst.columns.dept_code.list.order`）と、文言の鍵（`dsl.` で始まる。一覧は `cherry.mastersmith.dsl.domain.DslMessageKeys`）で返します。誤りに埋める値は、項目の名前と、書いた値の先頭 100 文字だけです。

## 既定の DSL の生成の決まり

対象DB のスキーマ（テーブル・ビュー・カラム・主キー・外部キー・コメント）から、既定の DSL を作ります（部品は `cherry.mastersmith.dslmanage.generate`）。作った DSL は、投入した DSL と同じくサーバー側の検証（前の節）を通したものだけを返します。接続先・ユーザー名・パスワード・スキーマ名は DSL に書きません。

- **表示名**: `ja` はテーブル・カラムのコメント、コメントが無ければ物理名。`en` はいつも物理名です。コメントの制御文字（改行・タブを除く）は取り除き、長さは切り詰めません。
- **メニューと並び**: メニューは1階層で、テーブル（ビューを含む）ごとに1項目です。メニューと `tables` は、物理名を大文字・小文字を区別せずに比べた順（同じなら元の名前の順）に並べます。
- **ビュー**: `view: true` で、主キー・外部キーを持ちません。
- **DB 上の型**: `dbType` には DB が返した型の名前・長さ・精度・桁・NULL を許すかをそのまま書きます（型の名前は MySQL・MariaDB が `DATA_TYPE`、PostgreSQL が `udt_name`）。`longtext` のように長さが 2147483647 を超える型は、長さを `null` にします。
- **バリデーション**: NOT NULL で既定値の無いカラムに `required`、長さのある文字列に `maxLength`（どちらも `origin: DB`）。数値の範囲・一意は作りません。
- **外部キー**: 1つのカラムだけの外部キーは、フォーム部品を `select`、選択肢を参照先のテーブルとカラム（`REFERENCE`）にします。複数のカラムの外部キーは `foreignKeys` に写すだけです。参照先が読めない（写しに無い）外部キーは写しません。
- **主キー**: 主キーのカラムの検索は `EQUALS`。一覧の既定の並べ替えは、主キーの最初のカラムの昇順です（そのカラムを一覧に出さないときは無し）。

型の分類とフォーム部品の初期値は次のとおりです（型の名前は大文字・小文字を区別しません）。

| 分類 | 型 | フォーム部品 | 検索 | 一覧 | 詳細 | 書式 |
|---|---|---|---|---|---|---|
| 短い文字列 | `char`・`varchar`（PostgreSQL の `bpchar` を含む）で長さ 255 以下 | text | 部分一致 | 表示・並べ替え可 | 表示 | 無し |
| 長い文字列 | 長さ 256 以上・長さの無い `varchar`、`tinytext`・`text`・`mediumtext`・`longtext`・`clob` | textarea | 部分一致 | 表示しない | 表示 | 無し |
| 数値 | `tinyint`・`smallint`・`mediumint`・`int`・`integer`・`bigint`・`decimal`・`numeric`・`float`・`double`・`real`、PostgreSQL の `int2`・`int4`・`int8`・`float4`・`float8` | number | 範囲 | 表示・並べ替え可 | 表示 | 桁区切り |
| 真偽値 | `boolean`・`bool`、MySQL・MariaDB の `tinyint(1)`・`bit(1)` | checkbox | 選択肢 | 表示・並べ替え可 | 表示 | はい・いいえ |
| 日付 | `date` | date | 範囲 | 表示・並べ替え可 | 表示 | 日付 |
| 日時 | `datetime`・`timestamp`・`timestamptz` | datetime | 範囲 | 表示・並べ替え可 | 表示 | 日時 |
| 時刻 | `time`・`timetz` | text | 完全一致 | 表示・並べ替え可 | 表示 | 時刻 |
| 対応外 | 上のどれでもない型（`json`・`enum`・`year`・`uuid`・`bytea`、PostgreSQL の `bit` など） | text | 検索しない | 表示しない | 表示しない | 無し |

- **`tinyint(1)` と `bit(1)`**: MySQL・MariaDB の `tinyint(1)` は、情報スキーマの `COLUMN_TYPE`（型の全体の表記）で見分けます。MySQL 8.4 は `tinyint(1) unsigned` の幅を落として `tinyint unsigned` と返すため、MySQL では符号なしの `tinyint(1)` は数値になります（MariaDB では真偽値）。`bit(1)` は精度 1 で見分けます。
- **大きさ**: 作った DSL も上限の 10MB の内に収めます。超えるほど大きなスキーマでは生成を失敗にします（目安: 100 テーブル × 100 カラムでコメントが無いとき約 6.7MB。コメントの分だけ増えます）。その場合は、対象のスキーマを分けるなどの運用で対応してください。

## DSL の管理の API（U4）

管理者だけが使う DSL の管理の API です（`/api/admin/dsl/` の下。ログインしていなければ 401、管理者でなければ 403 と `ACCESS_DENIED` の監査）。画面（U5）から使います。エラーは Problem Details（`code` つき）で返ります。

| メソッドとパス | 内容 | 成功 | 主な失敗（`code`） |
|---|---|---|---|
| `GET /api/admin/dsl/status` | 今の状態（適用中の版とプレビュー。無ければ `null`） | 200 | — |
| `GET /api/admin/dsl/preview` | プレビューの中身（要約・適用中との違い・対象DB との照合の警告）。重い処理 | 200 | 404 `DSL_PREVIEW_NOT_FOUND`、503 `DSL_BUSY` |
| `POST /api/admin/dsl/preview?source=UPLOAD\|PASTE` | 投入（本文は `application/yaml`、10MB まで）。検証を通れば今のプレビューを置き換える。重い処理 | 201 | 413 `DSL_TOO_LARGE`、415 `UNSUPPORTED_MEDIA_TYPE`、422 `DSL_INVALID`（誤りの先頭 100 件 `errors` と総数 `total`）、503 `DSL_BUSY` |
| `DELETE /api/admin/dsl/preview` | プレビューの破棄 | 204 | 404 `DSL_PREVIEW_NOT_FOUND` |
| `POST /api/admin/dsl/preview/generate` | スキーマの読み込み（既定の DSL を作り、今のプレビューを置き換える）。重い処理 | 201 | 503 `TARGET_DB_UNCONFIGURED`・`TARGET_DB_UNAVAILABLE`・`DSL_BUSY` |
| `GET /api/admin/dsl/preview/download` | プレビュー中の DSL を、保存した本文のまま添付（`dsl-preview-<識別の先頭12文字>.yaml`）で返す | 200 | 404 `DSL_PREVIEW_NOT_FOUND` |
| `POST /api/admin/dsl/apply` | 適用（本文 `{"previewId": "..."}`。見たプレビューを指定する）。対象DB には接続しない | 200 | 409 `DSL_PREVIEW_CHANGED`（プレビューが置き換わった・破棄された・同時の適用に負けた） |
| `GET /api/admin/dsl/history` | 適用の履歴（新しい順、最大 `MASTERSMITH_DSL_HISTORY_LIMIT` 件、今適用中の版に `current: true`） | 200 | — |
| `POST /api/admin/dsl/history/{revisionId}/restore` | 履歴の版をプレビューに戻す（今の検証にかけ直し、通れば出どころ `RESTORE` で今のプレビューを置き換え、照合の警告つきの中身を返す）。重い処理 | 201 | 404 `DSL_REVISION_NOT_FOUND`（件数の上限で消えた版を含む）、422 `DSL_INVALID`（今の検証を通らない。プレビューは変わらない）、400 `VALIDATION_FAILED`（識別が UUID の形でない）、503 `DSL_BUSY` |
| `GET /api/admin/dsl/applied/download` | 適用中の DSL（今の状態の `applied` と同じ版）を、保存した本文のまま添付（`dsl-applied-<識別の先頭12文字>.yaml`）で返す | 200 | 404 `DSL_APPLIED_NOT_FOUND` |

- **問題の種類（`code`）**: `DSL_INVALID`（422）・`DSL_TOO_LARGE`（413）・`DSL_PREVIEW_NOT_FOUND`（404）・`DSL_PREVIEW_CHANGED`（409）・`DSL_APPLIED_NOT_FOUND`（404）・`DSL_REVISION_NOT_FOUND`（404）・`TARGET_DB_UNCONFIGURED`（503）・`TARGET_DB_UNAVAILABLE`（503）・`DSL_BUSY`（503）。説明は `/api/problems/<code を小文字とハイフンにしたもの>` で見られます。
- **誤りの文言**: 422 の `errors[].message` と照合の警告の `message` は、要求の `Accept-Language` の言語（日本語・英語、既定は日本語）です。YAML・JSON Schema の部品の例外の文言は入りません。
- **本文の大きさの上限の置き場**: 本文の大きさは、ログイン（アクセストークンの確かめ）と認可の後に確かめます。本文を読むのはログインしていてその API を使える人の要求だけです。そのため、**ログインしていない大きな要求は 413 ではなく 401** になります。投入の API だけ上限が `MASTERSMITH_DSL_MAX_SUBMIT_SIZE`（既定 10MB、`DSL_TOO_LARGE`）で、ほかの API は今までどおり `MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE`（既定 1MB、`PAYLOAD_TOO_LARGE`）です。`Content-Length` がある送り方では本文を読まずに断ります。
- **重い処理は同時に1つ**: 生成・投入・プレビューの表示・履歴からの戻しは、アプリ全体で同時に1つだけ処理します。重なった要求は待たずに 503 `DSL_BUSY` で断り、状態を変えず、監査の出来事も出しません。少し待ってからやり直してください。照合は対象DB が応答しないと最悪 23〜28 秒かかり（「対象DB」の節）、その間ほかの重い処理は `DSL_BUSY` になります。
- **適用**: 見たプレビューの識別（`previewId`）を指定します。1つのトランザクションで履歴への追加・プレビューの削除・上限を超えた古い履歴の削除を行い、確定の後にだけ適用中の DSL を切り替えて監査に記録します。適用中と同じ内容でも履歴に1件足します。
- **履歴からの戻し**: 戻した版は投入の一種として扱い、監査に `DSL_SUBMITTED`（出どころ `RESTORE`）を記録します。今の検証を通らない版（書式の版が変わった など）は投入と同じ誤りの一覧の 422 になり、プレビューは変わらず、受け付けなかった投入の出来事（`DSL_SUBMISSION_REJECTED`）も記録しません。戻したプレビューを適用すると、履歴には出どころ `RESTORE` の新しい版として足されます。
- **起動時**: 履歴の最新（適用した日時が最も新しい版）を適用中の DSL として読みます。今の検証を通らない（書式の版が変わった など）ときは、ERROR（`適用中の DSL を読めないため、適用中の DSL が無い状態で起動します`、識別の先頭 12 文字と誤りの種類だけ）を1件出し、適用中の DSL が無い状態で起動を続けます。
- **保存**: プレビュー（最大1件）と適用の履歴は内部DB の `dsl_previews`・`dsl_applied_revisions`（Flyway の V5）に、受け取ったバイト列のまま入れます。すべて 10MB なら最大約 210MB です。
- **既知の制約（動いている間の内部DB のファイルの大きさ）**: 上限を超えた古い履歴を消しても、アプリが動いている間は H2 がその場所を再利用せず、内部DB のファイルは投入と適用のたびに本文の大きさの分（10MB の DSL なら約 10.8MB）ずつ大きくなります（2026-09-25 の測定で、10MB の DSL の投入と適用を 21 回で約 278MB、40 回で約 483MB。頭打ちになりません）。アプリを止めると、接続先の `;DEFRAG_ALWAYS=TRUE`（「環境変数」の表の `MASTERSMITH_DB_URL`）でファイルが詰め直され、起動し直した後は小さくなります（同じ測定で約 16MB。試験の DSL は圧縮がよく効く内容のため、圧縮の効きにくい本文では残る履歴の大きさに近くなりえます。止めるのにかかった時間は約 1 秒）。大きな DSL の投入と適用を何度も重ねたときは、ディスクの空きを確かめ、`docker compose restart app` などでアプリを起動し直してください。依頼者が Build and Test で受け入れた制約です。

### DSL の操作の監査

生成・投入（履歴からの戻しを含む）・受け付けなかった投入・適用・破棄を、監査の表 `audit_events` に1件ずつ記録します（種類 `DSL_GENERATED`・`DSL_SUBMITTED`・`DSL_SUBMISSION_REJECTED`・`DSL_APPLIED`・`DSL_PREVIEW_DISCARDED`）。

- 列（Flyway の V6 で足した、NULL を許す列）: 操作した管理者の利用者 ID `actor_user_id`、DSL の識別 `dsl_hash`、出どころ `dsl_source`（`GENERATED`・`UPLOAD`・`PASTE`・`RESTORE`）、受け付けなかった投入の理由の種類 `rejection_kind`（最初の誤りの種類、大きさで断ったときは `SIZE_LIMIT`。そのときは `dsl_hash` は空）。ほかに既存の日時・種類・結果・接続元IP・User-Agent・トレースIDを記録します。
- DSL の本文と対象DB の接続先は記録しません。`DSL_BUSY` で断った要求、巻き戻った適用、検証を通らなかった履歴からの戻しは記録しません。
- 記録に失敗しても DSL の操作は成功し、アプリのログに ERROR（`監査イベントの記録に失敗しました`）が1回出ます（本文は載りません）。

### DSL の操作の指標とログ

- 指標 `mastersmith.dsl.operation`（Timer。Prometheus では `mastersmith_dsl_operation_milliseconds_*`）。タグは `operation`（`generate`・`submit`・`restore`・`apply`・`discard`・`compare`）と `outcome`（`success`・`rejected`・`failed`・`busy`）だけです。`compare` はプレビューの表示の中の照合だけの時間です。
- 操作ごとに INFO（`DSL の操作を終えました`）を1件、キー `dsl.operation`・`dsl.outcome`・`dsl.durationMs`・`dsl.hash`（先頭 12 文字）・`dsl.source` で出します。想定内の失敗（413・409・422・503）は WARN でスタックトレースなし、想定外（500）は ERROR です。
- 手元の監視のダッシュボード「MasterSmith の概要」の行「DSL の操作」に、操作ごとの件数・結果ごとの件数・操作ごとの時間の 95 パーセンタイル（目標の線 1 秒・10 秒・30 秒）があります。警報はありません。

## DSL の管理画面（U5）

管理者は、サイドバーの「DSL」（「管理」の次。管理者にだけ表示）から DSL の管理画面 `/admin/dsl` を開きます。画面は上の「DSL の管理の API（U4）」だけを使います。サイドバーの項目を隠すのは表示の切り替えで、使えるかどうかはサーバー側（401・403）で決まります。管理者でない利用者が URL を直接開くと「ページが見つかりません」になります。

- **今の状態**（画面の上部、どのタブでも表示）: 適用中の版とプレビューの識別（先頭 12 文字。全体はツールチップと読み上げで読めます）・出どころ・人・日時（端末の時差で、時差の略号つき）と、「スキーマを読み込む」。
- **プレビュー**のタブ: 検証を通ったこと、対象DB との食い違いの件数（照合できなかったときはその旨を先頭に）、要約（表示名が埋まっていない場所は先頭 100 件と総数）、適用中との違い（既定は違いのあるテーブルだけ。「すべて表示」で変わらないテーブルも表示。行を開くとカラムの違い）、照合の警告、メニューの木。「ダウンロード」「破棄する」「適用する」。
- **投入**のタブ: 「ファイルを選ぶ」と「貼り付ける」を切り替えて入力し、選んでいる方だけを送ります。10MB を超えるものは送る前に案内し、ファイルは読み込みません（上限の確かめは案内で、正はサーバー側の 413）。検証を通らなかったときは、誤りの件数と先頭 100 件（行・列・場所・種類・内容）を示し、入力は残ります。このタブに **DSL の書式（JSON Schema）** のリンク（`/dsl/dsl-schema-v1.json`、ログインなしで取れる静的なファイル）があります。
- **履歴**のタブ: 適用の履歴（新しい順）。適用中の版は「適用中」の文字で示し、その行から適用中の DSL をダウンロードできます。ほかの版は「プレビューに戻す」で今のプレビューに置けます（今の検証を通らないときは誤りの一覧をこのタブに示します）。
- **確かめる表示**: スキーマの読み込み・投入・戻しでプレビューを置き換えるとき（今のプレビューの出どころ・置いた人・日時を表示）、適用するとき（違いの件数と照合の警告の件数を表示）、破棄するときに出ます。はじめのフォーカスは「やめる」で、背景のクリックでは閉じません。
- **失敗の知らせ**: サーバーの `code` から文言を選びます（`DSL_BUSY` は「ほかの処理中です。少し待ってからやり直してください」で、状態を読み直さず入力は残ります）。知らない `code` や通信の失敗は一般の文言にし、`detail` や内部の文言は表示しません。適用が 409 のときは今の状態を読み直し、別の管理者による置き換えか破棄かを分けて示します（自動では適用しません）。
- **読み直し**: 操作の後と、プレビュー・版・適用中が無い（404）応答の後に、今の状態と開いているタブの中身を読み直します。定期的な自動の読み直しはしません。
- **表示言語**: ブラウザの言語が英語なら英語、それ以外は日本語です。テーブル名・カラム名・DSL の中の場所・識別は訳しません。

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
- DSL の操作（U4）の行の見方は「DSL の管理の API（U4）」の「DSL の操作の指標とログ」を参照してください。
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

実行可能 WAR には、対象DB の JDBC ドライバーを、変更せずに独立した jar のまま同梱します（採用の理由は Intent 260923-dsl-schema-loader の U1 の技術選定の記録）。

| 部品 | ライセンス | ライセンスの文書 |
|---|---|---|
| MySQL Connector/J（`com.mysql:mysql-connector-j`） | GPL v2 ＋ Universal FOSS Exception 1.0（Apache License 2.0 で公開するプロジェクトへの同梱を認める） | jar の中の `LICENSE` |
| MariaDB Connector/J（`org.mariadb.jdbc:mariadb-java-client`） | LGPL 2.1 以降 | jar に含まれないため `backend/src/main/resources/META-INF/third-party-licenses/` に置き、WAR の `WEB-INF/classes/META-INF/third-party-licenses/` に入る |
| PostgreSQL JDBC（`org.postgresql:postgresql`） | BSD 2-Clause | jar の中の `META-INF/LICENSE` |
| Checker Framework qualifiers（`org.checkerframework:checker-qual`。PostgreSQL JDBC の依存） | MIT | jar の中の `META-INF/LICENSE.txt` |

DSL の読み込み（U2）で使う次の部品は Apache License 2.0 で、このプロジェクトと同じライセンスです。

| 部品 | ライセンス |
|---|---|
| SnakeYAML（`org.yaml:snakeyaml`） | Apache License 2.0 |
| networknt JSON Schema Validator（`com.networknt:json-schema-validator`） | Apache License 2.0 |
| ITU（`com.ethlo.time:itu`。networknt の依存） | Apache License 2.0 |

テストだけで使う Testcontainers（MIT）は配布物に含めません。
