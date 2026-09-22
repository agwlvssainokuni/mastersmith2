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
- コンテナの CPU の上限は 4 です。colima を使う場合は、VM に CPU を 4 つ以上割り当ててください（例: `colima start --cpu 4 --memory 4`）。
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

WAR をビルドし、一時ディレクトリの内部DBで起動して、ログイン用レイアウトが CSP 違反やスクリプトのエラーなしに表示されることを確かめます（番号は 18081。`E2E_PORT` で変えられます）。

## 開発時の起動

```bash
# バックエンド（http://localhost:8080、内部DBは ./backend/data/）
./gradlew :backend:bootRun

# 画面の開発サーバー（http://localhost:5173。/api と /actuator はバックエンドへ転送する）
cd frontend && npm run dev
```

開発サーバーは元の Host を保ったまま転送するため、エラー応答の `type` の URL は開発サーバーの Host から組み立てられます。

## コンテナでの起動と確認

```bash
./gradlew verify                 # または ./gradlew :backend:bootWar（WAR を作る）
cp .env.example .env             # 初回だけ。値を入れる（.env はコミットしない）
docker compose up -d --build
docker compose ps                # app が healthy になれば起動の完了
```

- ブラウザで `http://localhost:8080/` を開き、ログイン画面（U1 の段階ではログイン用レイアウト）が表示されることを確かめます。ヘルスチェックの応答が UP で、ログイン画面が表示されるまで、配備の完了とはみなしません。
- ログは `docker compose logs -f app`（1行1件の JSON）で見ます。1つの要求のログは `traceId` で絞り込めます。
- 止めるときは `docker compose down`（内部DBのデータはボリュームに残ります）。

### 戻し方

直前の版の WAR（CI の成果物 `mastersmith-<コミットのハッシュ>`、またはリリースに添付した WAR）を `backend/build/libs/mastersmith.war` に置き、`docker compose up -d --build` でイメージを作り直して起動します。スキーマの変更は前進のみ・後方互換のため、1つ前の版のアプリが今のスキーマで動きます。版を替える前に、内部DBのデータを複写しておきます（次の節）。

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

## 環境変数

秘密情報は `.env`（Git 管理外）から環境変数で渡します。見本は `.env.example` です。値を空にした変数は「空の値」として渡るため、既定値を使う設定は `.env` に書かないでください。

| 環境変数 | 既定値 | 内容 |
|---|---|---|
| `MASTERSMITH_DB_URL` | `jdbc:h2:file:./data/mastersmith` | 内部DBの接続先（コンテナでは `/app/data/mastersmith`） |
| `MASTERSMITH_DB_USERNAME` | `sa` | 内部DBの利用者 |
| `MASTERSMITH_DB_PASSWORD` | 空 | 内部DBのパスワード（秘密情報） |
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

- メソッドの呼び出しの追跡は、対象のクラスのロガーを TRACE にしたときだけ出ます（例: `LOGGING_LEVEL_CHERRY_MASTERSMITH_COMMON_ERROR=TRACE`）。
- 信号ごとの送り先の上書きは Spring Boot の設定で行えます（例: `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`）。
- ログのレベルは `LOGGING_LEVEL_<パッケージ>` で機能ごとに変えられます（既定は INFO）。
- U2 の署名鍵・初期管理者の設定は、U2 で追加します（`.env.example` に説明があります）。

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

## プロキシを置く配備

- エラー応答の `type` の URL を固定するときは、`MASTERSMITH_WEB_BASE_URL` にベースURLを設定します。
- プロキシの内側からだけ受け付ける配備では、`MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS=true` にすると、転送元のヘッダーのスキーム・Host・接続元を使います。外から直接届く配備では有効にしないでください（ヘッダーを偽装できるため）。
- HTTPS（`Strict-Transport-Security` を含む）は、配備先が決まったときに扱います。

## スキーマの変更（Flyway）

- 置き場所: `backend/src/main/resources/db/migration`
- ファイルの名前: `V<番号>__<単位>_<内容>.sql`（例: `V2__u2_user_account.sql`）。単位ごとにファイルを分けます。
- 前進のみとし、適用済みのファイルは書き換えません（書き換えると起動時の検証で起動が止まります）。1つ前の版のアプリが動く後方互換を保ちます。
- Hibernate はスキーマを作らず、検証だけ行います。

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

- 秘密情報を持つ型は、文字列化（`toString`）でその項目を伏せ字にしてください（メソッドの呼び出しの追跡が引数と戻り値を文字列にするため）。
- 画面での表示の制御はサーバー側の権限の確認の代わりになりません。データは API の側で守ります。

## ライセンス

Apache License 2.0（`LICENSE`）。
