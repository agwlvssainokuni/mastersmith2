# Code Generation Plan — U1 アプリの骨格（u1-app-skeleton）

## 1. 概要

U1 は、アプリが起動し、内部DB（組み込みの H2）につながり、ヘルスチェックに応答し、決めた形式で構造化ログを出し、画面の骨組み（make-you-chic-ui の AppShell とログイン用レイアウト）が表示されるところまでを作る。あわせて、共通のエラー応答、Spring Security のフィルターの連鎖と U2・U3 のための差し込み口、分散トレースと外部エクスポート（既定は無効）、1コマンドの検査（`./gradlew verify`）、pre-commit、CI（GitHub Actions）、コンテナ（Dockerfile・`compose.yaml`）を用意する。

- 対象の要件: FR1.1、FR1.2、FR2.3、FR10.1〜FR10.4（本スコープはユーザーストーリーを作っていないため、手順と要件の対応は FR・BR・NFR の ID で示す）。
- 対象の決まり: U1 の BR1.1〜BR7.9、NFR1.1〜NFR1.11、NFR3.1〜NFR3.15、NFR6.1、NFR7.1、NFR8.1、NFR9.1〜NFR9.5、NFR10.1〜NFR10.14。
- ワークスペースは新規（greenfield）である。アプリのコードはリポジトリのルート直下の `backend/`（Java）と `frontend/`（React + TypeScript）、およびルートの設定ファイルに置き、`aidlc/` の下には置かない。
- アプリの骨格がまだ無いため、チームの進め方（`aidlc/spaces/default/memory/team.md` の Walking Skeleton）の一式をすべてこの単位に含める。特別な承認の関門は設けない。

| Walking Skeleton の一式 | 実現する手順 |
|---|---|
| バックエンドの起動、内部DBへの接続、ヘルスチェックの応答 | Step 2、Step 5、Step 7、Step 10 |
| 決めた形式での構造化ログの出力 | Step 2、Step 13、Step 14 |
| make-you-chic-ui を組み込んだ画面のビルドとログイン画面の枠（`resolve.dedupe` を含む） | Step 3、Step 17、Step 18 |
| フォーマット・リンタ・ライセンスヘッダー・秘密情報の検出を含む検査とテストを1コマンドで | Step 19、Step 22 |
| カバレッジの計測と下限を下回ったら失敗する検証 | Step 4、Step 19、Step 22 |
| DB を使うテスト（内部DBは本番と同じ組み込みの H2。team.md の学びのとおり Testcontainers は使わない）とヘルスチェックの起動確認テスト | Step 6、Step 12 |
| 共通のエラー応答（`@RestControllerAdvice` と Problem Details と `code`） | Step 9、Step 10 |
| 同じ検査が CI（GitHub Actions）でも通ること | Step 21、Step 22 |

## 2. 前提と守ること

- **版**: Java 25、Spring Boot 4 系、Node.js 24。Gradle のバージョンカタログ（`gradle/libs.versions.toml`）と Gradle の依存関係の固定（lockfile）、npm の `package-lock.json` で版を固定する。生成の時点の最新の修正版を使う。フロントエンドの道具（Prettier・oxlint・ESLint・Stylelint・TypeScript・Vite・Vitest・React・React Router）は `vendor/make-you-chic-ui` と同じ系列を基準にする。
- **版の組み合わせが合わないとき**: 部品が Java 25・Spring Boot 4 系と組み合わせて動かない（起動しない、JSON のログが決めた項目で出ない、Flyway の変更が H2 に当たらない、など）と分かったら、その場で生成を止め、「その部品を一つ前の版にする案」と「代わりの部品を使う案」を示して依頼者の判断を仰ぐ（`tech-stack-decisions.md` の「版の組み合わせの確認」）。Spring Boot 4 系での starter の名前（AOP・OpenTelemetry など）と外部エクスポートの設定の名前も、使う版で確かめてから書く。
- **ライセンスヘッダー**: 生成するすべてのソースファイルの先頭に、言語のコメント構文に合わせた Apache License 2.0 の標準ヘッダー（年 `2026`、著作権者 `agwlvssainokuni`）を入れる。Java・TypeScript・JavaScript・CSS は `/* ... */` の形（`/** ... */` は使わない）。Kotlin DSL も `/* ... */`。YAML・Dockerfile・シェル・TOML は `#`、SQL は `--`、XML・HTML は `<!-- -->`。コメントを書けない JSON（`package.json` など）は対象外とする。
- **言語**: コメント（Javadoc・JSDoc を含む）、画面の文言（日本語・英語の両方を文言の鍵で持つ）、README は日本語。テストの説明文（テスト名、`@DisplayName`、`describe` / `it`）は英語。コミットメッセージは日本語。
- **バックエンドの書き方**: ルートパッケージは `cherry.mastersmith`。共通部品は `common`、Spring の設定は `config` に置き、各機能の中を `web`・`service`・`domain`・`repository` に分ける。Lombok は使わない（`record` とコンストラクター）。依存性の注入はコンストラクター注入だけ。ロガーは `LoggerFactory.getLogger`。可変の値はキーと値でログに渡す。時刻は注入できる `Clock` から得る。
- **フロントエンドの書き方**: 名前付きのエクスポートだけ（`export default` を使わない）、`enum` を使わず文字列リテラルの union。CSS は部品と同じ場所に素の CSS。画面に HTML を直接埋め込まない。操作できる要素に `data-testid`（`{部品}-{役割}` の kebab-case）を付ける。
- **秘密情報**: ソース・設定ファイルに値を書かない。設定ファイルには環境変数の参照だけを置き、見本は値を空にした `.env.example`。ログ・トレースの属性・外部エクスポート・エラー応答に秘密情報を載せない。
- **`vendor/make-you-chic-ui` は変更しない**。サブモジュールの固定先も変えない。
- **品質の目標は下げない**: カバレッジの下限（行 80%・分岐 70%）、ヘルスの制限時間、要求の本文の上限などの値を、手順を通すために緩めたり外したりしない。届かないときはテストを足すか、差を依頼者に示す。カバレッジの計測から外すのは、起動クラス、設定値だけのクラス（`@ConfigurationProperties` の record など）、自動生成コード、`vendor/` の下に限る。
- **コミットとプッシュ**: コミットはファイル変更のまとまりごとに提案し、依頼者の承認を得てから行う（日本語のメッセージ）。`origin` への `git push` は行わない。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "auth-audit-foundation",
  "test_strategy": "standard",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:25ea3f7583fa9dae61eb335f3373b648c506cb380b4e6c284ca079c3e54d5d34",
  "contract_sha256": "sha256:996000910b5fb215f929c890fc5f17bdde4a6dab5a0e9e7a0e7de8f7b25a4e16"
}
```

## 3. 手順の並べ方（Testing Contract との対応）

方法は test-after である。テスト可能な層ごとに「実装する → 同じ層のテストを書いて実行し、すべて通す → 次の層へ」の順に進める。最初のテストの手順（Step 6）より前に、テストの実行の枠組み（Step 4）を用意し、この単位に絞った実行のコマンドを `unit-test-instructions.md` に記録する。

| Testing Contract の段 | 本計画の手順 |
|---|---|
| Project structure and production configuration skeleton | Step 1、Step 2、Step 3 |
| Bootstrap the minimal test runner/configuration | Step 4 |
| Data model / database behavior（実装 → テスト） | Step 5 → Step 6 |
| Repository / data access | 該当なし。U1 は業務のデータ・表・repository を持たない（`entities.md`）。DB への接続と確認の問い合わせは Step 5 と Step 7 で扱う |
| Business logic（実装 → テスト） | Step 7 → Step 8 |
| API / endpoint（実装 → テスト） | 3つに分ける。エラー応答と説明ページ Step 9 → Step 10、フィルターの連鎖・配信・Actuator Step 11 → Step 12、ログ・トレース・外部エクスポート Step 13 → Step 14 |
| Frontend behavior（実装 → テスト） | 2つに分ける。判断の関数と文言 Step 15 → Step 16、画面の部品 Step 17 → Step 18 |
| Environment/build configuration | Step 19、Step 20、Step 21、Step 22 |
| Documentation and traceability | Step 23 |

## 4. 実装の手順

パッケージ名・クラス名・ファイル名は計画上の名前であり、意味と置き場所（パッケージ・ディレクトリ）を変えない範囲で生成時に整えてよい。テストの置き場所は `unit-test-instructions.md` の絞り込みの範囲（バックエンドは `cherry.mastersmith.common` と `cherry.mastersmith.config` の下と `cherry.mastersmith.ArchitectureTest`、フロントエンドは `frontend/src/app/` の下）から外さない。

### 4.1 プロジェクトの構成と本番の設定の骨組み

- [x] **Step 1 — リポジトリのルートとビルドの骨組み**
  - Gradle Wrapper（Java 25 に対応する版）、`settings.gradle.kts`（ルートの名前、`backend` を含める）、ルートの `build.gradle.kts`（後の Step 19 で `verify` を置く場所）、`gradle/libs.versions.toml`（版の一覧）、依存関係の固定（`dependencyLocking` で全構成を固定し、`gradle.lockfile` をコミットする）。
  - `.editorconfig`（UTF-8、LF、Java 4 桁・120 文字、TS 2 桁・100 文字）、`.gitattributes`（`* text=auto eol=lf`、Gradle Wrapper の jar などは binary）。
  - `.gitignore` に追加: `.env`、`.env.*`、`!.env.example`、`*.pem`、`*.key`、`*.p12`、`*.jks`、`.gradle/`、`backend/build/`、`data/`、`frontend/coverage/`、Playwright の出力（`frontend/test-results/`・`frontend/playwright-report/`）。既存の AI-DLC の区画は変えない。
  - ライセンスヘッダーのひな形を1か所（例: `config/license-header.txt`）に置き、Spotless と画面側の検査スクリプトの両方が使う。
  - 対応: NFR3.2、NFR3.13、team.md Code Style（リポジトリ構成とビルド）

- [x] **Step 2 — バックエンドの本番の設定の骨組み**
  - `backend/build.gradle.kts`: Spring Boot 4 系、`war` と実行可能 WAR、Java 25 の toolchain、依存（Spring MVC、Spring Security、Actuator、Spring Data JPA、Flyway、H2、Micrometer Tracing の OpenTelemetry ブリッジ、OTLP の送信、OpenTelemetry の logback 用の出力、logstash-logback-encoder、AOP）、Spotless（palantir-java-format、`licenseHeader`、Kotlin DSL のヘッダー）。Lombok は入れない。
  - 起動クラス `cherry.mastersmith.MastersmithApplication`（WAR 用の `SpringBootServletInitializer` を含む）。
  - `backend/src/main/resources/application.yaml`: アプリ名、要求の本文の上限（`mastersmith.web.max-request-body-size`、既定 1MB）、ベースURL（`mastersmith.web.base-url`、既定なし）、転送元のヘッダーを信頼するか（`mastersmith.web.trust-forwarded-headers`、既定 false）、ヘルスの制限時間（`mastersmith.health.db-timeout`、既定 2s）、外部エクスポートの有効化と送り先（`mastersmith.observability.export.enabled`、既定 false、`...endpoint`）、サンプリング率（既定 1.0）、TraceAspect の出力形式（`mastersmith.trace.*`）、CSP の値（`mastersmith.security.content-security-policy`）。穏やかな停止（`server.shutdown=graceful`、待ち 30 秒）、gzip（HTML・JS・CSS・JSON）、Actuator は health だけを公開し内訳を出さない、H2 のコンソールを明示的に無効、ログのレベルの既定 INFO。
  - 秘密情報は環境変数の参照だけにする（例: `${MASTERSMITH_DB_PASSWORD:}`）。環境変数の名前の案: `MASTERSMITH_DB_URL`、`MASTERSMITH_DB_USERNAME`、`MASTERSMITH_DB_PASSWORD`、`MASTERSMITH_HEALTH_DB_TIMEOUT`、`MASTERSMITH_WEB_BASE_URL`、`MASTERSMITH_WEB_TRUST_FORWARDED_HEADERS`、`MASTERSMITH_WEB_MAX_REQUEST_BODY_SIZE`、`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`、`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT`、`MASTERSMITH_TRACING_SAMPLING_PROBABILITY`（正確な名前は生成時に確定し、Step 20 の `.env.example` と Step 23 の README に一覧を書く）。
  - `backend/src/main/resources/logback-spring.xml`: 標準出力への出力を1つだけ置き、logstash-logback-encoder で1行1件の JSON にする。項目は `timestamp`（ISO 8601、タイムゾーン付き）、`level`、`logger`、`thread`、`message`、`traceId`・`spanId`（MDC のうちこの2つだけ）、`exception`（あれば）、呼び出し側のキーと値。書き出しは同期。呼び出し元の場所・ホスト名は出さない。ファイルへの出力は持たない。
  - `config` と `common` のパッケージの骨組み（中身は後の手順で書く）。
  - 対応: FR1.1、FR10.1、BR3.1、BR3.2、NFR1.6、NFR1.10、NFR1.11、NFR3.5、NFR3.6、NFR10.1、NFR10.2、NFR10.4

- [x] **Step 3 — フロントエンドの本番の設定の骨組み**
  - `frontend/package.json`（`engines` で Node.js 24 を求める、名前付きのスクリプト: `format:check`・`lint`・`lint:css`・`typecheck`・`build`・`test`・`license:check`・`bundle:size`）と `package-lock.json`。依存: `react`・`react-dom`（19 系、`vendor/make-you-chic-ui` の peerDependencies に合わせる）、`react-router`、`i18next`、`react-i18next`、`make-you-chic-ui`（`file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`、組み込みガイドの手順 A）、`@fontsource/noto-sans-jp`（組み込みガイドのとおり自己ホスティング。英語の表示のため latin のサブセットも読み込む）。
  - `frontend/vite.config.ts`: React の plugin、`resolve.dedupe: ['react', 'react-dom']`、開発サーバーのプロキシ（`/api` と `/actuator` を `http://localhost:8080` へ。元の Host を保ったまま転送し、BR5.10 のベースURLが要求の Host から組み立てられるようにする）、ビルドの出力を `dist`（ハッシュ付きのファイルは `assets/` の下）、`index.html` に埋め込みのスクリプトを置かない出力、初回読み込みの量を測るための manifest の出力。CORS の設定は置かない。
  - `frontend/tsconfig.json`（`strict` 系、`noUnusedLocals` など make-you-chic-ui と同じ値）、`.prettierrc.json`（セミコロンなし、シングルクォート、末尾カンマ all、100 文字、インデント2）、`.oxlintrc.json`（make-you-chic-ui の値を複製し、`react/no-danger`、`no-eval`、`no-implied-eval`、`no-new-func` などのセキュリティ系のルールを追加）、`eslint.config.js`（react-hooks の推奨ルールに加え、`export default` と `enum` を禁じる `no-restricted-syntax`）、`.stylelintrc.json`（stylelint-config-standard）。いずれも `vendor/` を参照せず `frontend/` に複製し、`vendor/` を対象から外す。
  - `frontend/index.html`（`lang="ja"`、アプリ名の title）、`frontend/src/main.tsx`（入口。Step 17 で中身を書く）。
  - `frontend/scripts/check-license-header.mjs`（TS・TSX・JS・MJS・CSS・HTML のヘッダーを確かめ、無い・形が違うファイルを示して失敗させる。`node_modules`・`dist`・`coverage` は対象外）。
  - `vendor/make-you-chic-ui` のビルド: 組み込みガイドの手順 A のとおり、サブモジュールの中で `npm ci` と `npm run build` を行い `packages/make-you-chic-ui/dist/` を作る（Step 19 で Gradle のタスクにする）。できるのはサブモジュール自身の `.gitignore` が無視する `node_modules/`・`dist/` だけで、追跡されるファイルは変えない（7章の C1 の決定（A）で了承済み）。
  - 対応: FR2.3、NFR1.5、NFR3.10、team.md Code Style（フロントエンド）、project.md Tech Stack

### 4.2 テストの実行の枠組み

- [x] **Step 4 — テストの実行の枠組みを用意し、この単位のコマンドを記録する**
  - バックエンド: JUnit 5、AssertJ、Spring Boot Test、jqwik、ArchUnit を入れる。テストは `backend/src/test/java` に置き、Gradle の `test` タスクは `*Test`（単体）だけ、新しい `integrationTest` タスクは `*IT`（Spring と組み込みの H2 を起動する結合テスト）だけを実行する（同じソースの組を使い、名前で分ける）。jqwik は失敗時の乱数の種をテストの出力に残す設定にする。
  - JaCoCo: `test` と `integrationTest` の実行記録を合わせて報告と下限の検証（行 80%・分岐 70%）を行うタスクを用意する。計測から外すのは起動クラスと設定値だけのクラスに限る。
  - フロントエンド: Vitest、Testing Library（jsdom）、user-event、vitest-axe、`@testing-library/jest-dom`、fast-check、`@vitest/coverage-v8` を入れる。`frontend/vitest.config.ts`（`vite.config.ts` と同じ `resolve.dedupe`、jsdom、`setupFiles`、`coverage.thresholds` に行 80・分岐 70、計測の対象は `src/**`、外すのは入口の `src/main.tsx` と型の宣言だけ、`e2e/` はテストの対象外）、`frontend/vitest.setup.ts`（vitest-axe と jest-dom の照合を登録）。`test` のスクリプトは make-you-chic-ui と同じく `NODE_OPTIONS=--no-experimental-webstorage` を付ける。
  - `unit-test-instructions.md` の「実行の枠組みの確認」のコマンドを実行し、コンパイルと実行の枠組みが動くことを確かめる（この時点ではテストはまだ無い。常に通るだけのテストは書かない）。
  - 対応: NFR9.4、team.md Testing Posture

### 4.3 データモデル・DB の振る舞い

- [x] **Step 5 — 内部DBの接続とスキーマの変更の仕組みを実装する**
  - DataSource: 既定は組み込み・ファイル保存（`jdbc:h2:file:./data/mastersmith`。コンテナでは作業ディレクトリ `/app` の下の `/app/data`）。H2 の TCP サーバー・自動の複数プロセス接続は使わない。接続の URL・利用者・パスワードは設定（環境変数）だけで切り替えられる。
  - HikariCP: 最大 10 本、接続を借りる待ち 5 秒。JPA: `open-in-view` 無効、Hibernate はスキーマを作らず検証だけ（`ddl-auto=validate`）、問い合わせの上限 10 秒。
  - Flyway: 起動時に適用し、失敗したら起動を止める。場所は `backend/src/main/resources/db/migration`。単位ごとにファイルを分ける名前の決まり（例: `V1__u1_baseline.sql`、以降の単位は `V<番号>__<単位>_<内容>.sql`）を README に書く。U1 は業務の表を持たないため、U1 のファイルは表を作らない基準線（コメントだけ）とし、Flyway が H2 に当たることを確かめる目的で置く。
  - 対応: FR1.2、BR2.1、BR2.2、BR2.3、NFR1.7、NFR1.8、NFR3.7、NFR6.1、NFR9.1

- [x] **Step 6 — データモデル・DB の振る舞いのテストを書いて実行する**
  - `DatabasePersistenceIT`（組み込みの H2 を一時ディレクトリのファイルで使う）: 既定の形がファイル保存であること、書き込んだデータがアプリの再起動（コンテキストの作り直し）の後も残ること（FR1.2 の受け入れ基準1）、接続設定だけで別の H2 に切り替わること（受け入れ基準2）、Flyway の基準線が適用されること、Hibernate の検証で起動できること、内部DBのパスワードを設定して起動してもログに値が出ないこと（BR2.3）。
  - 実行のコマンドは `unit-test-instructions.md` の「DB の振る舞い」。すべて通るまで次へ進まない。
  - 対応: FR1.2、BR2.1〜BR2.3、NFR6.1、NFR9.1

### 4.4 業務処理（バックエンドの domain・service と共通部品）

- [x] **Step 7 — 業務処理の層を実装する**
  - 問題の種類（`common.error.domain`）: `ProblemType`（record。`code`・`slug`・`status`・`title`・`description`・`resolution`）、`LocalizedText`（record。`ja`・`en`）、`code` の形の検査（`^[A-Z][A-Z0-9_]*$`）と `slug` の導出（小文字にしアンダースコアをハイフンに）、状態コードの範囲（400〜599）、日英の両方が必須。後の単位が問題の種類の定義を置くための `ProblemTypeCatalog`（定義の一覧を返す Bean の型）と、共通の業務エラーの型 `BusinessException`（問題の種類と、利用者に見せてよい detail を持つ）。
  - U1 の問題の種類の定義 `CommonProblemTypes`: `VALIDATION_FAILED`（400）、`NOT_FOUND`（404）、`PAYLOAD_TOO_LARGE`（413）、`INTERNAL_ERROR`（500）。7章の P1 の決定（A）により `MALFORMED_REQUEST`（400）、`METHOD_NOT_ALLOWED`（405）、`NOT_ACCEPTABLE`（406）、`UNSUPPORTED_MEDIA_TYPE`（415）も加える。すべて日英の title・description・resolution を持つ（BR5.14）。
  - `ProblemTypeRegistry`（`common.error.service`）: 起動時にすべての `ProblemTypeCatalog` を集め、`code`・`slug` の重複があれば、重複した値を示して起動を失敗させる。`code`・`slug` から引く。
  - `AcceptLanguageResolver`（`common.i18n.domain`）: Accept-Language を q 値の大きい順（q=0 は除く、同じ q は書かれた順）に見て、言語部分が最初に ja または en に当たったものを返す。無い・形が正しくない・当たらない場合は ja（BR6.3）。
  - `TimeBoundedDbHealthIndicator`（`common.health`）: Actuator の既定の DB の確認を置き換える。専用のスレッド1本の実行の枠で、プールから接続を借りて `SELECT 1` を1回行い、制限時間（既定 2 秒）だけ待つ。時間内に終わらなければ中断して DOWN、前の確認が終わっていなければ新しく始めずに DOWN、例外なら DOWN。応答は状態だけ。
  - `TraceIdProvider`（`common.observability`）: `Tracer` の現在のスパンからトレースIDを返す。スパンが無ければ「無し」を返し、失敗させない（`functional-spec.md` 6.1）。
  - 差し込み口の型（`common.security`）: `SecurityRuleContributor`（`Ordered` を継承、`contribute(HttpSecurity)`）、`ApiDefaultAccess`（`requireAuthentication()`）、`ErrorResponseWriter`（`write(HttpServletRequest, HttpServletResponse, ProblemType)`）。起動時の検査 `SecurityExtensionValidator`: `SecurityRuleContributor` の `order` の重複と、`ApiDefaultAccess` が2つ以上あることを検出して起動を失敗させ、呼ぶ順に並べる。order の割り当て（U2 は 100 台、U3 は 200 台）を Javadoc に書く（6章）。
  - 外へ送る記録の秘密情報の除去（`common.observability`）: 外部エクスポートの手前でスパンの例外の記録からメッセージとスタックトレースを取り除く `SanitizingSpanExporter`（送信の仕組みを包む）と、要求の URL の属性から問い合わせの部分（`?` 以降）を取り除く観測のフィルター。
  - 対応: BR1.1〜BR1.3、BR4.3、BR5.2、BR5.9（slug）、BR5.14、BR5.16、BR6.3、NFR1.2、NFR3.1、NFR3.4、NFR10.6、security-design 3章・5章

- [x] **Step 8 — 業務処理の層のテストを書いて実行する**
  - 単体テスト（`*Test`）: `ProblemTypeTest`（jqwik で code から slug の導出の性質を確かめる）、`ProblemTypeRegistryTest`（U1 の定義がすべて日英を持つことを含む）、`AcceptLanguageResolverTest`（jqwik で任意の文字列でも例外にならず ja・en のどちらかを返す性質を含む）、`TimeBoundedDbHealthIndicatorTest`、`TraceIdProviderTest`、`SecurityExtensionValidatorTest`、`SanitizingSpanExporterTest`、`UrlQueryStrippingObservationFilterTest`。
  - `ProblemTypeDuplicateStartupIT`: code・slug が重複する定義を置いたときにアプリの起動が失敗すること（BR5.16）。
  - 時間の上限のテストは `sleep` を使わない。確認の問い合わせをラッチで止めた偽の DataSource と短い制限時間（例: 50 ミリ秒）で DOWN を確かめ、ラッチを外して後始末する。
  - 実行のコマンドは `unit-test-instructions.md` の「業務処理」。すべて通るまで次へ進まない。
  - 対応: 上の Step 7 と同じ

### 4.5 API・エンドポイント（エラー応答と説明ページ）

- [x] **Step 9 — 共通のエラー応答と問題の種類の説明ページを実装する**
  - `ProblemBaseUrlResolver`: 設定のベースURLがあればそれを、無ければ要求のスキーム・Host・ポートから組み立てる。転送元のヘッダーは、`mastersmith.web.trust-forwarded-headers=true` のときだけ Spring の `ForwardedHeaderFilter` を登録して反映する（既定では登録しない。U2・U3 の接続元IP も同じ方針に乗る）。
  - `ErrorResponseFactory`: `ProblemDetail` に `type`（ベースURL＋`/api/problems/`＋slug）、`title`（表示言語に応じた問題の種類の title）、`status`、`detail`（利用者に見せてよい説明だけ）、`instance`（要求のパス）、`code`、`traceId`（`TraceIdProvider` から。要求の処理中は常に割り当てられている）を入れる。`Content-Type` は `application/problem+json`。
  - `GlobalExceptionHandler`（`@RestControllerAdvice`、1か所）: 入力の検証の失敗 → 400 `VALIDATION_FAILED`、存在しない API（`NoResourceFoundException` など）→ 404 `NOT_FOUND`、`BusinessException` → その問題の種類、本文の上限超え → 413 `PAYLOAD_TOO_LARGE`、それ以外 → 500 `INTERNAL_ERROR`。フレームワークの標準の 4xx は 7章の P1 の決定（A: 状態コードを保ち専用の code を付ける）に従う。ログは変換する場所で1回だけ: 4xx は WARN（スタックトレースなし、`code`・`status` をキーと値で）、5xx は ERROR（スタックトレース付き）。応答に例外のメッセージとスタックトレースを載せない。
  - `DefaultErrorResponseWriter`（`ErrorResponseWriter` の実装）: フィルターの段階（413 や、U2・U3 の 401・403）でも同じ形の ErrorResponse を書く。
  - `ProblemTypeController`（`GET /api/problems/{slug}`、認証を求めない）: 定義があれば 200 で説明を返す。Accept が HTML を優先すれば HTML、そうでなければ JSON（`ProblemTypeResponse` の record）。言語は `AcceptLanguageResolver`。定義が無ければ 404 `NOT_FOUND`。
  - `ProblemTypeHtmlRenderer`: 決まった形のひな形に、問題の種類の定義の値だけをすべて HTML としてエスケープして埋め込む。要求から受け取った値（slug を含む）は埋め込まない。`<html lang>` を表示言語にし、埋め込みのスタイル・スクリプトを持たない（CSP に合わせる）。
  - 対応: BR5.1〜BR5.16、BR6.3、NFR3.3、NFR3.8、NFR3.9、FR10.2（エラー応答のトレースID）

- [x] **Step 10 — エラー応答と説明ページのテストを書いて実行する**
  - 単体テスト: `ProblemBaseUrlResolverTest`、`ErrorResponseFactoryTest`、`GlobalExceptionHandlerTest`（Spring を起動しない MockMvc の standalone 構成）、`DefaultErrorResponseWriterTest`、`ProblemTypeHtmlRendererTest`（HTML の特殊文字を含む定義がエスケープされる）。
  - 結合テスト `ErrorResponseIT`: 存在しない API が 404 `NOT_FOUND`、入力の検証の失敗が 400 `VALIDATION_FAILED`、想定外の例外が 500 `INTERNAL_ERROR` で例外のメッセージが応答に無い、`BusinessException` がその問題の種類になる、`type` の URL が要求の Host から組み立てられ転送元のヘッダーが既定で無視される、設定のベースURLが優先される、`traceId` が載る、4xx は WARN でスタックトレースなし・5xx は ERROR でスタックトレース付きで1回だけログに出る、P1 の決定（A）の各例外の扱い。テスト用のコントローラーはテストのソースの中にだけ置く。
  - 結合テスト `ProblemTypePageIT`: 未ログインで見られる、JSON と HTML の切り替え、Accept-Language による日英の切り替えと既定の ja、未定義の slug は 404 `NOT_FOUND`、応答に要求の値が無い。
  - 対応: 上の Step 9 と同じ

### 4.6 API・エンドポイント（フィルターの連鎖、画面の配信、Actuator）

- [x] **Step 11 — フィルターの連鎖・配信・Actuator を実装する**
  - `SecurityConfig`（`config`）: `SecurityFilterChain` を1つだけ定義する。状態を持たない（セッションを作らない）、フォームのログイン・Basic 認証・CSRF の仕組みは無効、要求の検査（正規化されていないパスの拒否）は既定のまま。ヘッダー: `Content-Security-Policy`（設定の固定値）、`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: same-origin`。Spring Security の既定のキャッシュの指定は外す。`Strict-Transport-Security` は付けない。
  - アクセスの決まりの並び: U1 の公開の決まり（`/actuator/health`、`/api/problems/**`）→ `SecurityRuleContributor` を `order` の小さい順に呼ぶ → `ApiDefaultAccess` による `/api/**` の既定（無ければ許可、あれば `requireAuthentication()` に従う）→ `/api/**` 以外（画面の配信）は許可。U2・U3 はヘッダー・セッション・CSRF の設定を変えない。
  - `RequestSizeLimitFilter`（連鎖の中、ヘッダーを書く処理の後）: `Content-Length` が上限を超えれば本文を読まずに 413、`Content-Length` が無い送り方では読んだ量を数えて上限を超えた時点で読むのをやめて 413。応答は `ErrorResponseWriter` で書く。
  - `CacheControlFilter`: `/api/**` と `/actuator/**` は `no-store`、`/assets/**` は `public, max-age=31536000, immutable`、それ以外（`index.html` と画面の URL への応答）は `no-cache`。
  - 画面の配信（`WebConfig`）: 静的なファイルを配り、見つからない要求は `/api/`・`/actuator/` の下でなければ `index.html` を返す（画面の URL の直接の表示と再読み込みのため）。`/api/` の下で見つからないものは 404 `NOT_FOUND` のエラー応答になる。
  - Actuator: 公開は health だけ、内訳を出さない。既定の DB の確認は無効にし、Step 7 の `TimeBoundedDbHealthIndicator` を使う。指標は集めるが窓口は公開しない。
  - 対応: BR1.3、BR1.4、BR5.1、BR5.12、NFR1.1、NFR3.5〜NFR3.7、NFR3.10、NFR3.12、NFR10.8、NFR10.10、security-design 2章〜4章・6章、performance-design 5章

- [x] **Step 12 — フィルターの連鎖・配信・Actuator のテストを書いて実行する**
  - 単体テスト: `RequestSizeLimitFilterTest`（上限ちょうどは受け付け、上限＋1バイトは 413。`Content-Length` あり・なしの両方）、`CacheControlFilterTest`。
  - 結合テスト `HealthEndpointIT`（ヘルスチェックの起動確認テスト）: 起動して未ログインで `/actuator/health` が 200・`{"status":"UP"}` だけ、内訳が無い、起動後に内部DBが応答しない状態（ヘルスの確認が使う接続を、問い合わせが止まる偽のものに差し替えたコンテキスト。起動時の Flyway は本物の H2 を使う）で制限時間内に 503・DOWN、そのときも画面の配信と説明ページは応答する。
  - 結合テスト `SecurityHeadersIT`: API・画面・説明ページ・health・エラー応答（413 を含む）のそれぞれに CSP などのヘッダーが付く、キャッシュの指定が表のとおり。
  - 結合テスト `ExposureIT`: `/actuator/env`・`/actuator/configprops`・`/actuator/heapdump`・`/actuator/metrics` に届かない、`/h2-console` が H2 のコンソールでない（`index.html` が返る）、画面の URL の直接の表示で `index.html` が返る、存在しない `/api/...` が 404 `NOT_FOUND`、本文の上限超えが 413 `PAYLOAD_TOO_LARGE` の ErrorResponse。
  - 結合テスト `SecurityExtensionIT`: U1 だけの状態で `/api/**` が許可、テスト用の `SecurityRuleContributor`（order 100 と 200）と `ApiDefaultAccess`（true）を置いた状態で決まりが order の順に当たり `/api/**` がログイン必須になる、order の重複と `ApiDefaultAccess` の2つ以上で起動が失敗する。
  - 対応: 上の Step 11 と同じ

### 4.7 API・エンドポイント（ログ・トレース・外部エクスポート）

- [x] **Step 13 — ログ・トレース・外部エクスポート・TraceAspect を実装する**
  - トレース: Micrometer Tracing（OpenTelemetry のブリッジ）と W3C Trace Context を使い、仕組み自体は常に有効にする。サンプリング率の既定は 100%（設定で変更可）。率はトレースの送信の割合だけに効き、トレースIDはすべての要求でログとエラー応答に載る。MDC の `traceId`・`spanId` は要求の終わりに取り除く。
  - 外部エクスポート: `mastersmith.observability.export.enabled`（既定 false）1つで、トレース・ログ・指標の OTLP の送信をまとめて切り替える。送り先は1つの設定で指定し、信号ごとに上書きできる。無効のときは送信の仕組みを作らない。有効のときは送信を要求と切り離し、上限付きの待ち行列から送る（送信1回の上限 10 秒、あふれたら捨てる、失敗は警告のログ）。ログの送信は、有効のときだけ OpenTelemetry の logback 用の出力を加える（標準出力はそのまま）。指標は送る間隔 60 秒、共通のタグにアプリ名、タグに利用者の ID・生の URL を使わない。Spring Boot 4 系の対応する設定の名前は使う版で確かめて対応づける。
  - `TraceAspect`（`common.observability`）: Spring の `CustomizableTraceInterceptor` に処理を任せる。対象は `cherry.mastersmith` の下の `web`・`service`・`domain`・`repository` の層の Bean（Spring Data の repository はインターフェースに宣言されたメソッド）。外すもの: Spring Security のフィルターとフィルター全般、`@Configuration` のクラス、起動クラス、追跡の仕組み自身。対象のクラスの名前のロガーを使い、TRACE のときだけ文字列を組み立てて出す。入る・出る・例外の文言とスタックトレースの有無は `mastersmith.trace.*` で変えられる。
  - テストの補助（`backend/src/test/java` の `cherry.mastersmith.common.testsupport`）: 標準出力の JSON のログを捕まえて項目を取り出し、指定した秘密の値が含まれないことを確かめる部品。U2 以降も使う（NFR3.15）。
  - 対応: FR10.1〜FR10.4、BR3.1〜BR3.5、BR4.1〜BR4.5、NFR3.1、NFR3.4、NFR3.11、NFR3.15、NFR10.1〜NFR10.7、NFR10.9、NFR10.11〜NFR10.14

- [x] **Step 14 — ログ・トレース・外部エクスポート・層の構造のテストを書いて実行する**
  - 単体テスト `JsonLogFormatTest`: 1件が改行を含まない1行の JSON、項目の名前（`timestamp`・`level`・`logger`・`thread`・`message`・`traceId`・`spanId`・`exception`・キーと値）、`timestamp` がタイムゾーン付き、改行を含む値が1行に収まる、MDC のほかの値が出ない。
  - 結合テスト `TracingAndLoggingIT`: 形の正しい `traceparent` 付きの要求でログとエラー応答のトレースIDがヘッダーの値と一致する、`traceparent` が無い要求でも新しいトレースIDがログとエラー応答で一致する、形の正しくない `traceparent`（桁数違い・16進数でない・すべて0）で要求が拒否されず新しいトレースになる、1要求の間のすべてのログに同じトレースIDが入る、サンプリング率 0 でもトレースIDが入る、ログにパスワード・トークンの値が出ない（テストの補助を使う）。
  - 結合テスト `ExternalExportIT`: 既定の設定で OTLP の送信の仕組みが作られず外部へ何も送らない、有効にして届かない送り先を指定しても要求が通常どおり応答する。
  - 結合テスト `TraceAspectIT`: 既定（INFO）では追跡のログが出ない、TRACE を有効にするとテスト用の service の Bean の入る・出る・例外が出る、フィルターと設定クラスが追跡の対象にならずフィルターを通る要求が正しく動く。
  - 単体テスト `ArchitectureTest`（ArchUnit）: `web` の層が `repository` の層を直接使わない、`@Transactional` は `service` の層にだけ置く、`@Entity` の型を API の応答として返さない、フィールド注入・セッター注入を使わない、`lombok` に依存しない。U1 の段階でまだ無い層でも失敗しないよう、空の対象を許す設定にする。
  - 対応: 上の Step 13 と同じ、team.md Code Style（層の境界）

### 4.8 画面（フロントエンド）

- [ ] **Step 15 — 画面の判断の関数・登録の型・文言を実装する**
  - 登録の型（`frontend/src/app/registry/types.ts`）: `FeatureRegistration`、`RouteRegistration`（`layout: 'SHELL' | 'STANDALONE'`、`access: 'PUBLIC' | 'LOGGED_IN' | 'ADMIN'`、`role?: 'LOGIN'`）、`SidebarItemRegistration`（`visibleWhen: 'LOGGED_IN' | 'ADMIN'`）、`UserMenuItemRegistration`、`LoginStateProvider`（`loggedIn`・`admin`・`displayName`）。7章の C2 の決定（A）により、機能ごとの文言（`messages`、任意）を加える。
  - `loadRegistrations`: `import.meta.glob` の結果（`frontend/src/features/*/registration.ts` の決まった場所・名前）を受け取り、登録の一覧にする。glob の呼び出しと変換を分け、変換はテストできる純粋な関数にする。
  - `validateRegistrations`: 画面の URL、サイドバーとユーザーメニューの項目の id、featureId の重複、ログイン状態の提供元・ログイン画面（role=LOGIN）の2つ以上、LOGIN の画面が STANDALONE・PUBLIC でない、サイドバーの項目の path が登録済みでない、を検出し、どの登録が問題かを示すエラーにする。
  - `resolveLanguage`: ブラウザの希望言語を順に見て、最初に ja または en で始まるものを使い、無ければ ja（地域付きは先頭の言語部分で判定）。
  - 文言（`frontend/src/app/i18n/messages/ja.ts`・`en.ts`）: アプリ名、ログイン画面の見出し、ホームの見出しと説明、「ページが見つかりません」、「ホームへ」、サイドバーの「ホーム」、画面の起動の失敗の表示。i18next の初期化（`i18n.ts`）。
  - `decideRoute`: URL・登録の一覧・ログイン状態から、表示する画面、ログイン画面への移動（role=LOGIN があればその path、無ければログイン用レイアウトだけ）、「ページが見つかりません」（ログイン中）、を決める。`/` はホーム（LOGGED_IN・SHELL）。ADMIN の画面はログイン中の非管理者には「ページが見つかりません」。
  - サイドバーとユーザーメニューの項目を作る関数: 先頭に「ホーム」、続いて visibleWhen を満たす項目を order の順。ユーザーメニューは order の順。
  - 対応: FR2.3、BR6.1、BR6.2、BR7.1〜BR7.6、BR7.8、BR7.9、NFR7.1

- [ ] **Step 16 — 判断の関数と文言のテストを書いて実行する**
  - `resolveLanguage.test.ts`（fast-check で任意の言語の並びでも ja・en のどちらかを返す性質を含む）、`messages.test.ts`（ja と en の鍵の集まりが同じ、空の文言が無い）、`loadRegistrations.test.ts`、`validateRegistrations.test.ts`（fast-check で重複を含む登録の組が必ず拒否される性質を含む）、`decideRoute.test.ts`、`navigationItems.test.ts`。fast-check は失敗時の種（seed）を出力に残す。
  - 実行のコマンドは `unit-test-instructions.md` の「画面の判断の関数」。すべて通るまで次へ進まない。
  - 対応: 上の Step 15 と同じ

- [ ] **Step 17 — 画面の部品を実装する**
  - `App`（`frontend/src/app/App.tsx`）: make-you-chic-ui の `ThemeProvider`・`ToastProvider`・`ModalStackProvider`、`I18nProvider`、`FeatureRegistryProvider`、`LoginStateGate`、React Router、`AppRouter` をつなぐ。登録の検査に失敗したら、画面の起動を止めて問題の登録を示す表示にする（BR7.2）。
  - `I18nProvider`: 表示言語を決め、`document.documentElement.lang` を合わせ、文言の鍵から文言を引く手段を提供する。
  - `LoginStateGate`: 提供元が登録されていれば問い合わせ、無ければ未ログイン（loggedIn=false、admin=false）。
  - `AppRouter`: `decideRoute` に従って振り分ける。SHELL の画面は `ShellLayout` の中、STANDALONE の画面は `StandaloneLayout` の中。組み込みガイドのレイアウトルートの形を使う。
  - `ShellLayout`: make-you-chic-ui の `AppShell`。サイドバーにホームと条件を満たす項目（React Router で画面を移る）、ユーザーメニューに登録された項目、ユーザーの表示名。
  - `StandaloneLayout`、`LoginLayout`（アプリ名、表示言語に応じた見出し、U2 の入力欄を置く場所。入力欄とボタンは持たない）、`HomePage`（見出しと短い説明）、`NotFoundPage`（「ページが見つかりません」と「ホームへ」のリンク）。部品と同じ場所に素の CSS。
  - `frontend/src/main.tsx`: make-you-chic-ui の `style.css` とフォントを読み込み、`App` を描画する。
  - 後の単位の登録の置き場所 `frontend/src/features/`（U1 は登録を置かない。空のディレクトリを保つための説明のファイルを置く）。
  - 対応: FR2.3、BR6.1、BR6.2、BR7.1〜BR7.9、NFR1.5、NFR7.1、NFR8.1、project.md Decided（AppShell 構成）

- [ ] **Step 18 — 画面の部品のテストを書いて実行する**
  - `I18nProvider.test.tsx`、`LoginStateGate.test.tsx`、`AppRouter.test.tsx`、`ShellLayout.test.tsx`、`StandaloneLayout.test.tsx`、`LoginLayout.test.tsx`、`HomePage.test.tsx`、`NotFoundPage.test.tsx`、`App.test.tsx`。画面の部品ごとに vitest-axe のアクセシビリティ検査を1件入れる（NFR8.1）。英語のブラウザ設定で英語、日本語でも英語でもない設定で日本語になること（FR2.3 の受け入れ基準）、U1 だけの状態で `/` を開くとログイン用レイアウトだけが出ること、未ログインで LOGGED_IN・ADMIN の画面を開くとログイン画面へ移ること、ログイン中の非管理者に ADMIN の画面を出さないこと、登録の重複で起動が止まること、を確かめる。ログイン状態の提供元はテストの中の偽の提供元で与える。
  - ビルドした WAR で CSP 違反が出ずに画面が表示されることを確かめる Playwright のテスト（`frontend/e2e/u1-skeleton.e2e.ts`）を書く。7章の P2 の決定（A）により、別の Gradle タスク `e2eTest` で実行し、`./gradlew verify` と CI には入れない。
  - 実行のコマンドは `unit-test-instructions.md` の「画面の部品」。すべて通るまで次へ進まない。
  - 対応: 上の Step 17 と同じ、NFR3.10

### 4.9 環境・ビルドの設定

- [ ] **Step 19 — 1コマンドの検査 `./gradlew verify` を組み立てる**
  - ルートの `verify` タスクが次の順に実行し、1つでも失敗したら後ろを実行しない: 1 フォーマット（Spotless の確認、Prettier の確認）→ 2 リンタ（oxlint、ESLint、Stylelint）→ 3 ライセンスヘッダー（Spotless の `licenseHeader`、`check-license-header.mjs`）→ 4 ビルド（Java のコンパイル、`tsc --noEmit`、Vite のビルド。make-you-chic-ui のビルドを先に行う）→ 5 単体テスト（`test`、Vitest）→ 6 結合テスト（`integrationTest`）→ 7 カバレッジの下限（JaCoCo、`@vitest/coverage-v8` の `thresholds`）→ 8 安全の検査（SpotBugs＋FindSecBugs、OSV-Scanner で Gradle の lockfile・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`、Gitleaks でリポジトリ全体）→ 9 成果物と量の確認（`dist` を同梱した実行可能 WAR、初回読み込みの JavaScript の圧縮後の合計を測り 500KB を超えたら警告だけ）。
  - 重大度 High 以上で失敗させ、それ未満は警告にする。フォーマッタ・リンタ・静的検査は `vendor/` を対象から外す。
  - npm は PC の Node.js 24 を Gradle から呼び、`npm ci` で入れる。Node.js の版が合わなければ失敗させる。Gitleaks・OSV-Scanner は PC の実行ファイルを呼び、見つからなければ入れ方を示して失敗させる（黙って飛ばさない）。
  - make-you-chic-ui のビルドの後に、サブモジュールの追跡されるファイルが変わっていないこと（`git -C vendor/make-you-chic-ui status --porcelain` が空）を確かめる。
  - WAR への同梱: フロントエンドのビルド結果 `frontend/dist` を WAR のクラスパスの静的なファイルの場所に入れる。
  - `.gitleaks.toml`（既定の規則を引き継ぐ）、`.pre-commit-config.yaml`（Gitleaks と、変更したファイルに対するフォーマットの確認。道具の版を固定）。pre-push のフックは置かない。
  - 対応: NFR1.5、NFR3.2、NFR3.13、NFR3.14、NFR9.4、NFR9.5、cicd-pipeline 2章・3章、team.md Code Style（静的解析とセキュリティ検査）

- [ ] **Step 20 — コンテナで動かすための設定を書く**
  - `Dockerfile`（1段）: Eclipse Temurin の JRE 25（Ubuntu ベース、版の番号までタグで固定）。root 以外の専用の利用者（UID 10001）、作業ディレクトリ `/app`、`/app/data` をその利用者だけが読み書きできるようにする。Gradle で作った WAR をコピーし、イメージの中ではビルドしない。起動は `exec` 形式で、最大ヒープをコンテナのメモリの 75%、`-Duser.timezone=Asia/Tokyo`。
  - `compose.yaml`: `app`（番号 `127.0.0.1:8080:8080`、`.env` の読み込み、環境変数 `TZ=Asia/Tokyo`、名前付きボリュームを `/app/data`、ヘルスチェックは `/actuator/health` を 30 秒ごと・起動の猶予 40 秒、停止の猶予 45 秒、CPU の上限 4・メモリ 1GB、再起動しない、ログは json-file で 10MB × 3）と、profile `observability` のときだけ起動する `otel-collector`（版を固定。受け取ったものを標準出力に出すだけの設定ファイル `docker/otel-collector/config.yaml`）。ヘルスチェックに使う道具がイメージに無い場合は、入れるか代わりの確かめ方を選ぶ。
  - CPU の上限 4 とタイムゾーン `Asia/Tokyo`（`TZ` と `-Duser.timezone` の両方）は、U2 の `infrastructure-specification.md` 1章・4章による U1 の設計の上書き・追加であり、U1 の「CPU 2」より優先する。保存する時刻はタイムゾーンに依存しない時点（UTC）として扱う。
  - `.env.example`: Step 2 の環境変数の名前だけを置き、値は空。U2 の署名鍵・初期管理者の名前の欄も用意し、署名鍵の作り方をコメントで書く（値は書かない）。
  - 対応: NFR1.4、NFR1.11、NFR3.2、NFR3.7、NFR6.1、NFR9.2、NFR9.3、infrastructure-specification 1章〜4章、monitoring-design 4章

- [ ] **Step 21 — CI と依存関係の更新の知らせを書く**
  - `.github/workflows/ci.yml`: きっかけは `develop` へのプッシュ、`v*` のタグのプッシュ、手動の実行。ランナーは `ubuntu-latest`。サブモジュールを固定先のコミットで取得し、JDK 25（Temurin）・Node.js 24・Gitleaks・OSV-Scanner を版を固定して入れ、Gradle と npm のキャッシュを使う。`./gradlew verify` を実行する。WAR を名前にコミットのハッシュを入れて成果物として保存する（30 日）。権限は `contents: read`、タグのときだけ動くリリースのジョブは `contents: write` で GitHub のリリースに WAR を添付する。秘密情報は使わない。使う Actions は版を固定する。
  - `.github/dependabot.yml`: Gradle、npm（`frontend/`）、GitHub Actions、Docker（ベースイメージ）。
  - 対応: NFR3.13、NFR3.14、NFR9.5、cicd-pipeline 4章

- [ ] **Step 22 — すべての検査を通し、コンテナで起動を確かめる**
  - `./gradlew verify` をリポジトリのルートで実行し、すべての段（カバレッジの下限と安全の検査を含む）が通ることを確かめる。通らないときは原因を直す（下限や重大度の基準を下げない）。
  - `docker compose up` で起動し、コンテナのヘルスチェックが healthy になること、ブラウザで `http://localhost:8080/` を開くとログイン用レイアウトが表示されることを確かめる。起動から health が UP になるまでの時間を記録する（NFR1.4 の 30 秒）。`docker compose --profile observability up` で外部エクスポートを有効にしたとき、トレース・ログ・指標が受け手に届き秘密情報が載らないことを確かめる。
  - 対応: NFR1.4、NFR9.3、NFR9.5、FR10.4、Walking Skeleton の一式

### 4.10 文書とトレーサビリティ

- [ ] **Step 23 — README とトレーサビリティの材料をそろえる**
  - `README.md`（日本語）: 概要、前提の道具（JDK 25、Node.js 24、Python と pre-commit、Gitleaks、OSV-Scanner、Docker）と入れ方、サブモジュールの取得、`pre-commit install`、`./gradlew verify` の実行、開発時の起動（バックエンドと Vite の開発サーバー）、コンテナでの起動と確認と戻し方、環境変数の一覧、内部DBのバックアップと戻し方（アプリを止めて `/app/data` を複写する）、Flyway のファイルの名前の決まり、外部エクスポートの確かめ方（profile `observability`）、プロキシを置く配備でのベースURLと転送元のヘッダーの設定、U2・U3 が使う差し込み口の説明（6章）。
  - 公開する型・差し込み口の Javadoc と JSDoc（日本語）。
  - 本ステージの手順5で書く `code-summary.md`・`source-manifest.json`・`traceability.json` の材料（作った・変えたファイルの一覧、FR・BR・NFR ごとの実装とテストのファイル）をそろえる。
  - 対応: NFR9.2、NFR9.3、traceability

## 5. 要件と手順の対応（トレーサビリティ）

| 要件・決まり | 実装の手順 | テストの手順 |
|---|---|---|
| FR1.1 起動とヘルスチェック（BR1.1〜BR1.4、NFR1.1、NFR1.2、NFR10.8、NFR10.10） | Step 2、Step 7、Step 11 | Step 8、Step 12 |
| FR1.2 内部DB（BR2.1〜BR2.3、NFR1.7、NFR1.8、NFR3.7、NFR6.1、NFR9.1） | Step 5 | Step 6 |
| FR2.3 日英の文言（BR6.1〜BR6.3、NFR7.1） | Step 7、Step 15、Step 17 | Step 8、Step 16、Step 18 |
| FR10.1 1行1件の JSON のログ（BR3.1、BR3.2、BR3.5、NFR10.1、NFR10.2、NFR10.4、NFR3.11） | Step 2、Step 13 | Step 14 |
| FR10.2 ログのトレースID（BR3.3、NFR10.3、NFR10.6） | Step 13、Step 9 | Step 14、Step 10 |
| FR10.3 W3C Trace Context（BR4.1、BR4.2、NFR10.5） | Step 13 | Step 14 |
| FR10.4 外部エクスポート（BR4.3〜BR4.5、NFR3.4、NFR10.7、NFR10.11） | Step 7、Step 13、Step 20 | Step 8、Step 14、Step 22 |
| 共通のエラー応答（BR5.1〜BR5.16、NFR3.3、NFR3.8、NFR3.9） | Step 7、Step 9 | Step 8、Step 10 |
| 画面の骨組みと差し込み口（BR7.1〜BR7.9、NFR8.1） | Step 15、Step 17 | Step 16、Step 18 |
| 応答のヘッダー・本文の上限・公開する範囲（NFR3.5、NFR3.6、NFR3.10、NFR3.12） | Step 11 | Step 12、Step 18（E2E） |
| 秘密情報を出さない（NFR3.1、NFR3.2、NFR3.15、BR3.4、BR4.3、BR5.4） | Step 2、Step 7、Step 13、Step 19、Step 20 | Step 6、Step 10、Step 14、Step 19 |
| メソッドの呼び出しの追跡（NFR10.12〜NFR10.14） | Step 13 | Step 14 |
| 性能（NFR1.3〜NFR1.6） | Step 2、Step 3、Step 11、Step 19 | Step 19、Step 22（NFR1.3 は Performance Validation で測る） |
| 品質と検査（NFR9.4、NFR9.5、NFR3.13、NFR3.14） | Step 4、Step 19、Step 21 | Step 22 |
| 配備と戻し方（NFR1.11、NFR9.2、NFR9.3） | Step 2、Step 20、Step 23 | Step 22 |

## 6. 単位どうしのつなぎ目（U1 が U2・U3・U4 に提供する約束）

本ワークフローは Contract Design を行わないため、U1 の `security-design.md` 3章と `functional-spec.md` 6章で決めた約束を、ここでつなぎ目として記録する。U2・U3・U4 は U1 のファイルを書き換えずにこれらを使う。

| つなぎ目 | 形 | 使う単位 |
|---|---|---|
| 追加のアクセスの決まり | `SecurityRuleContributor`（`Ordered`、0個以上）。order は U2 が 100 台、U3 が 200 台。重複は起動の失敗。足してよいのはアクセスの決まり、トークンの検証、認証の入口と拒否の処理、要求の検査の拒否の処理。ヘッダー・セッション・CSRF は変えない | U2、U3 |
| API の既定の扱い | `ApiDefaultAccess`（0個か1個。2個以上は起動の失敗） | U3 |
| フィルターの段階のエラー応答 | `ErrorResponseWriter`（問題の種類から type・code・title を、要求から traceId を埋める） | U2、U3 |
| 想定内のエラー | `BusinessException` を起こし、問題の種類の定義を自分の `ProblemTypeCatalog` の Bean に置く（code・slug の重複は起動の失敗） | U2、U3、U4 |
| 要求中のトレースID | `TraceIdProvider`（無ければ「無し」） | U4 |
| 秘密情報がログに出ないことのテストの補助 | `cherry.mastersmith.common.testsupport` の部品 | U2 以降 |
| 画面の差し込み口 | `frontend/src/features/<featureId>/registration.ts` に `FeatureRegistration` を名前付きでエクスポートする（画面、サイドバーの項目、ユーザーメニューの項目、ログイン状態の提供元） | U2、U3 |
| ログイン用レイアウト | `LoginLayout`（role=LOGIN の画面が使う） | U2 |
| スキーマの変更 | `backend/src/main/resources/db/migration` に単位ごとのファイル | U2、U4 |

## 7. 判断を仰いだ点（決定済み）

依頼者の回答（`code-generation-questions.md` の Q1〜Q4）により、次のとおり決まった。

| 点 | 決定 |
|---|---|
| P1 フレームワークの標準の 4xx | A: 状態コードを保ち、専用の code（`METHOD_NOT_ALLOWED`・`NOT_ACCEPTABLE`・`UNSUPPORTED_MEDIA_TYPE`・`MALFORMED_REQUEST`）と日英の説明を付ける。WARN でスタックトレースなし。パラメータの不足・型の不一致は `VALIDATION_FAILED` |
| P2 ビルドした WAR での Playwright の確認 | A: 別の Gradle タスク `e2eTest`。`./gradlew verify` と CI には入れず、統合の前に手で実行する |
| C1 make-you-chic-ui のビルドの置き場所 | A: サブモジュールの中でビルドしてよい（できるのは無視される `node_modules/`・`dist/` だけ） |
| C2 画面の文言の差し込み | A: `FeatureRegistration` に任意の `messages` を加える（承認済みの `entities.md` との違いとして記録する） |

以下は、決定の前に示した提案の内容である。

### 提案 P1 — フレームワークの標準の 4xx の例外の扱い（決定が必要）

U1 の BR5.6 を文字どおりに読むと、BR5.7（入力の検証）・BR5.8（存在しない API）・413 以外の例外はすべて 500 / `INTERNAL_ERROR` になる。そうすると、利用者の送り方の誤り（許されないメソッド、受け付けない形式、壊れた本文）が 500 として返り、ERROR とスタックトレースのログになり、エラーの割合の指標（NFR10.9 の 5xx の件数）も利用者の誤りで増える。

**提案（A）**: フレームワークの標準の 4xx の例外は、その状態コードのまま、専用の安定した code と日英の問題の種類で返し、WARN でスタックトレースなしのログにする。BR5.6 の「変換の対象として決めていない例外」にこれらを「決めた対象」として加える形であり、BR5.14（すべての code に日英の説明）も満たす。

| 例外（Spring） | 状態コード | code |
|---|---|---|
| `HttpRequestMethodNotSupportedException` | 405（`Allow` ヘッダー付き） | `METHOD_NOT_ALLOWED` |
| `HttpMediaTypeNotAcceptableException` | 406（本文は `application/problem+json`） | `NOT_ACCEPTABLE` |
| `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| `HttpMessageNotReadableException`（本文の JSON が壊れている、読めない） | 400 | `MALFORMED_REQUEST` |
| `MissingServletRequestParameterException`、`MethodArgumentTypeMismatchException`、`MissingRequestHeaderException`、`MethodArgumentNotValidException`、`HandlerMethodValidationException` など | 400 | `VALIDATION_FAILED`（BR5.7 の入力の検証の失敗に含める） |
| `NoResourceFoundException`、`NoHandlerFoundException` | 404 | `NOT_FOUND`（BR5.8） |
| 上のいずれにも当たらないもの | 500 | `INTERNAL_ERROR`（BR5.6） |

**代わりの案（B）**: BR5.6 を文字どおりに当て、上の 405・406・415・壊れた本文を 500 / `INTERNAL_ERROR` にする（code を増やさない）。

A が採られた場合、Step 7 に4つの問題の種類を、Step 9・Step 10 に対応とテストを加える。B の場合はそれらを加えず、Step 10 で 500 になることを確かめる。

### 提案 P2 — ビルドした WAR での Playwright の確認の実行の場所（決定が必要）

`security-design.md` 9章は、ビルドした WAR で Playwright を動かし CSP 違反が出ないことを確かめるとしているが、`cicd-pipeline.md` の `./gradlew verify` の9つの段と CI には E2E の段が無い。

- **提案（A）**: Playwright のテストを Gradle の別のタスク（例: `./gradlew e2eTest`。WAR を起動して `frontend/e2e/` を実行する）として置き、`verify` と CI には入れない。Build and Test のステージと、リリースの前に実行する。承認済みの CI の設計を変えない。
- **代わりの案（B）**: `verify` の 9 の段の後に E2E の段を加え、CI でも実行する（CI に Playwright のブラウザを入れる。`cicd-pipeline.md` の段の一覧が変わる）。

### 確認事項 C1 — make-you-chic-ui のビルドの置き場所

組み込みガイドの手順 A のとおり、サブモジュールの中で `npm ci` と `npm run build` を行う。できるのはサブモジュールの `.gitignore` が無視する `node_modules/`・`dist/` だけで、追跡されるファイルとサブモジュールの固定先は変わらない（Step 19 で確かめる）。これを「`vendor/make-you-chic-ui` の中身を変更しない」に反しない扱いとして進める。反する扱いとするなら、代わりに make-you-chic-ui の sample-app と同じく、Vite と TypeScript の別名でサブモジュールのソースを直接読む形にする（組み込みガイドは推奨していない）。

### 確認事項 C2 — 後の単位の画面の文言の差し込み

BR6.2 により後の単位も画面の文言を日英の文言の鍵で持つが、`entities.md` の `FeatureRegistration` には文言を渡す項目が無い。U1 のファイルを書き換えずに文言を足せるよう、`FeatureRegistration` に任意の項目 `messages`（`{ ja: {...}, en: {...} }`、鍵は featureId の名前空間の下）を加え、骨組みが起動時に i18next へ登録する形で進める。重複した鍵は BR7.2 と同じく起動の失敗とする。

## 8. 生成中の進め方

- 手順は番号の順に1つずつ進め、終わった手順のチェックボックスに印を付ける（印を付ける以外の本書の変更は Plan Approval をやり直す）。
- 各テストの手順では、`unit-test-instructions.md` のこの単位に絞ったコマンドで実行し、すべて通ってから次の手順へ進む。不安定なテストは原因を直す。
- 版の組み合わせが合わない、品質の目標に届かない、設計書と食い違う、と分かったときは、生成を止めて案を示し、依頼者の判断を仰ぐ。
- 区切りのよいところ（例: Step 4、Step 6、Step 14、Step 18、Step 22、Step 23 の後）でコミットを提案し、承認を得てから行う。

## 参照した文書

- 単位の設計: `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/` の `functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`・`frontend-components.md`）、`nfr-requirements/`（`tech-stack-decisions.md` ほか）、`nfr-design/`（6つの設計書）、`infrastructure-design/`（`infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`）
- U1 の基盤の上書き・追加: `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/infrastructure-specification.md` の1章・4章（CPU の上限 4、タイムゾーン `Asia/Tokyo`）
- Inception: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/units-generation/`（`unit-of-work.md`・`unit-of-work-story-map.md`・`unit-of-work-dependency.md`）、`inception/domain-design/`（`components.md`・`decisions.md`）、`inception/requirements-analysis/requirements.md`
- デザインシステム: `vendor/make-you-chic-ui/docs/integration-guide.md`
- チームの決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
