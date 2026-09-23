# コードの品質の評価（mastersmith2）

テストの件数はファイルと注釈を数えたもので、テストを実行した結果ではない（今回のスキャンでは Gradle・npm を実行していない）。実測の件数とカバレッジは Build and Test で取る（`project.md` の Testing Posture）。

## テストとカバレッジ

| 対象 | 置き場 | 数（ファイルを数えた値） | 道具 |
|---|---|---|---|
| バックエンドの単体テスト | `backend/src/test/java/cherry/mastersmith/` の `*Test` | 60 クラス | JUnit 5・AssertJ・jqwik（`@Property` 18 件）・ArchUnit |
| バックエンドの結合テスト | 同上の `*IT` | 46 クラス | `@SpringBootTest(webEnvironment = RANDOM_PORT)`＋`HttpTestClient`（実際の Tomcat に HTTP を送る）、組み込みの H2 |
| テストの補助 | `common/testsupport/` ほか | 残り（全体で 130 ファイル、約 16,000 行） | `TestDatabase`・`HttpTestClient`・`MutableClock` など |
| 画面の単体テスト | `frontend/src/**/*.test.ts(x)` | 29 ファイル | Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe＋fast-check |
| E2E | `frontend/e2e/*.e2e.ts` | 3 ファイル（骨組み・認証・管理者向け領域） | Playwright（Chromium）。`./gradlew e2eTest` だけで、`verify` と CI の外 |
| 負荷の試験 | `perf/k6/scenarios.js` | — | k6。テストの関門の外 |

- 結合テストの DB は、テストのクラスごとの一時ディレクトリに作る組み込みの H2 である（`@TempDir` と `TestDatabase.register` の `@DynamicPropertySource`）。Testcontainers は使っていない（`team.md` の決まりでは、内部DB は H2 で、コンテナは対象DB のテストに使う）。
- カバレッジの下限は、行 80%・分岐 70% を両方に当てている。
  - バックエンド: JaCoCo。`test.exec` と `integrationTest.exec` を合わせ、`jacocoTestCoverageVerification` で検証する。除外は `MastersmithApplication*` と `*Properties`（設定値の record）だけ。
  - 画面: `@vitest/coverage-v8` の `thresholds`。除外は `src/main.tsx`・`*.d.ts`・テストのファイル。
- テストの説明文は英語（`@DisplayName`・`describe`／`it`）。時刻は `Clock`（テストでは `MutableClock`）で差し替える。
- 認証・認可・監査の失敗の場合のテスト（ロックの境界、トークンの改ざん、401／403／200、監査の書き込みの失敗など）は、テストのクラスの名前から見て揃っている（例: `access/web/AdminAccessIT.java`、`AuditAuthenticationEventsIT`・`AuditTraceIdIT`・`AuditRollbackIT`・`AuditWriteFailureIT`）。中身は、`AdminAccessIT` 以外は流し読みである。

## リンタと静的解析

| 対象 | 道具 | 設定の場所 | 関門 |
|---|---|---|---|
| Java のフォーマット・ヘッダー | Spotless（palantir-java-format、ライセンスヘッダー、末尾の空白） | `backend/build.gradle.kts`（Gradle の Kotlin DSL はルートの `build.gradle.kts`） | 違反で失敗 |
| Java の静的解析 | SpotBugs（effort MAX、報告は LOW から）＋FindSecBugs | `backend/build.gradle.kts`、除外は `backend/config/spotbugs-exclude.xml`（空） | `spotbugsGate` が priority 1（High）で失敗。テストのコードは対象外 |
| 層の境界 | ArchUnit | `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` | web は repository を使わない、`@Transactional` は service の層だけ、コントローラーはエンティティを返さない、フィールド・メソッドへの注入の禁止、Lombok の禁止 |
| 画面 | oxlint（correctness を error、react・jsx-a11y・typescript、`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url`） | `frontend/.oxlintrc.json` | 違反で失敗 |
| 画面 | ESLint（react-hooks の推奨、`no-implied-eval`、`export default` と `enum` の禁止） | `frontend/eslint.config.js` | 違反で失敗 |
| 画面 | Prettier・Stylelint・TypeScript `strict`・ヘッダーの検査 | `frontend/.prettierrc.json`・`.stylelintrc.json`・`tsconfig.json`・`frontend/scripts/check-license-header.mjs` | 違反で失敗 |
| 秘密情報 | Gitleaks | `.gitleaks.toml`（既定の規則＋ワークフローの記録の目印の誤検出の除外）、`.pre-commit-config.yaml` | 検出で失敗 |
| 依存関係 | OSV-Scanner、Dependabot | `build.gradle.kts` の `osvScan`、`config/npm-build-tools.txt`、`.github/dependabot.yml` | 判定の決まりは `dependencies.md` |

TODO・FIXME・HACK は見当たらない。`@SuppressWarnings` は `DefaultErrorResponseWriter` の1か所（型の変換）だけ。1ファイルの最大は約 220 行（`AuditEvent.java`・`GlobalExceptionHandler.java`）で、大きすぎるクラスは無い。

## CI/CD と検査の関門

`./gradlew verify` が、ローカルの統合前の関門と CI の両方の入口である（`project.md` の Way of Working）。段は `mustRunAfter` で次の順に並ぶ。

| 段 | タスク | 中身 |
|---|---|---|
| 0 | `verifyPrepare` | `checkToolchain` → `vendorInstall` → `vendorBuild` → `vendorUnchanged`、`frontendInstall` |
| 1 | `verifyFormat` | Spotless（backend・ルート）、Prettier |
| 2 | `verifyLint` | oxlint＋ESLint、Stylelint |
| 3 | `verifyLicense` | 画面のヘッダー（Java・Gradle は段 1 の Spotless） |
| 4 | `verifyBuild` | `compileJava`・`compileTestJava`・`tsc --noEmit`・Vite のビルド |
| 5 | `verifyUnitTest` | `:backend:test`・Vitest |
| 6 | `verifyIntegrationTest` | `:backend:integrationTest`（組み込みの H2） |
| 7 | `verifyCoverage` | JaCoCo・`@vitest/coverage-v8` |
| 8 | `verifySecurity` | `spotbugsGate`・`osvScan`・`gitleaksScan` |
| 9 | `verifyArtifact` | `bootWar`・初回読み込み量の確認 |

- CI（`.github/workflows/ci.yml`）: `develop` へのプッシュ、`v*` のタグ、手動の実行で `./gradlew verify` を動かす。サブモジュールは固定先で取得し、Gitleaks と OSV-Scanner は版と SHA-256 で、Actions はコミットのハッシュで固定する。WAR を成果物として保存し、タグではリリースに添付する。
- 配備: `Dockerfile`（WAR をコピーするだけの1段）と `compose.yaml` で、開発者の PC に限る。戻し方・スモークテスト・監視の手順は README にある。

## 文書

- `README.md`: 前提の道具、1コマンドの検査、E2E、開発時とコンテナでの起動、バックアップと戻し方、環境変数、監視、Flyway、アクセス制御、監査ログ、差し込み口の一覧。今回深く読んだのは「スキーマの変更（Flyway）」「API のアクセス制御（U3）」「監査ログ（U4）」「後の単位が使う差し込み口」の節だけで、ほかは見出しだけ。
- `perf/README.md`、`frontend/src/features/README.md`（機能の足し方）。
- Java のクラスと公開メソッドには日本語の Javadoc があり、元の設計の番号（BR・NFR・ADR・WF）への参照が多い。TypeScript のファイルの先頭にも目的の説明がある。

## 技術的負債

| ID | 所見 | 場所 | 影響 |
|---|---|---|---|
| TD-1 | 内部DB の SQL が H2 に依存している（`MERGE INTO ... USING (VALUES ...)`、`FETCH FIRST :limit ROWS ONLY`、Hibernate の `SKIP LOCKED` の指定） | `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java:104`、`RefreshTokenRepository.java:62` | 内部DB は H2 のまま使う前提なので今は問題ない。同じ書き方は MySQL・MariaDB・PostgreSQL では動かない |
| TD-2 | 監査ログが認証とアクセス拒否に特化している（種類は `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED` の4つ。組み立てと受け取りは2種類の出来事の型だけを網羅の `switch` で扱う。表に操作対象を表す列が無い） | `audit/domain/AuditEventType.java`・`AuditEventFactory.java`、`audit/service/AuditEventListener.java`、`V4__u4_audit_event.sql` | `project.md` の DECIDED により、共通化は業務データを扱う後続 Intent で検討することになっており、まだ手を付けていない |
| TD-3 | ログイン・ログアウトでは、確定の後の監査の記録のために1要求で接続を2本使う | `audit/service/AuditEventListener.java`・`AuditEventRecorder.java`、`application.yaml` の `maximum-pool-size`（既定 30） | README に既知の制約として記録されている。同時の数が上限に達すると監査の行が欠けうる |
| TD-4 | `TraceAspect` が層の Bean の引数と戻り値を TRACE のときに文字列にする | `common/observability/TraceAspect.java:48` | 秘密情報を持つ型は `toString` で伏せ字にする必要がある（既存の `AuthProperties`・`LoginRequest`・`TokenResponse` などは対応済み） |
| TD-5 | 画面の振り分けは、登録した URL と完全一致（`matchPath(..., end: true)`）で1画面を表示するだけ | `frontend/src/app/routing/decideRoute.ts` | 入れ子のルートや、画面の中の段階の URL は機能の側で工夫が要る |
| TD-6 | 管理者向け領域 `/admin` の中身は置き場だけ | `frontend/src/features/admin/AdminPlaceholder.tsx` | 管理機能を置く場所として用意されている |
| TD-7 | 作業ツリーに生成物（`frontend/coverage/`・`frontend/playwright-report/`・`frontend/test-results/`・`build/`・`.idea/`）がある | 作業ツリー | どれも `.gitignore` の対象で、コミットされない |

## この Intent に向けた懸念

この Intent（DSL のスキーマ定義、対象DB からの既定 DSL の生成とリセット、DSL の読み込み・検証、プレビュー→適用）に関わる事実である。どう扱うかは、要件と設計の段で決める（ここでは決めない）。

| ID | 懸念 | 事実と根拠 |
|---|---|---|
| C-1 | 対象DB・DSL の既存コードが無い | `mastersmith.target-db` の設定、対象DB 用の `DataSource`、JDBC の `DatabaseMetaData` の読み取り、DSL（YAML）の読み込み・検証・保存、プレビュー・適用の仕組みと画面は、バックエンド・画面・設定（`application.yaml`・`.env.example`・`compose.yaml`）・テストのどこにも無い。`target-db`・`dsl`・`json schema`・`mysql`・`mariadb`・`postgres`・`testcontainers` を検索して、Gradle の「Kotlin DSL」の文言以外に当たりは無い（開発者のスキャン）。すべて新しく作る |
| C-2 | DataSource が1つだけ | 内部DB は Spring Boot の自動構成の `DataSource` 1つ（`application.yaml` の `spring.datasource`）。JPA・Flyway・ヘルスチェック・監査・既定の `PlatformTransactionManager` がすべてこれに結び付いている（`dependencies.md` の「トランザクションと接続の結び付き」）。2つ目の `DataSource` を Bean として足すと、自動構成が内部DB の `DataSource` を作らなくなる、あるいは注入の対象があいまいになる可能性があり、既存の `@Transactional`（名前の指定なし）と `LoginService` の `PlatformTransactionManager` が内部DB を指し続けるための配線が要る |
| C-3 | 足りない依存と、JSON Schema の検証の部品と Jackson 3 の相性 | 依存に、対象DB の JDBC ドライバー（MySQL・MariaDB・PostgreSQL）、JSON Schema の検証の部品、`jackson-dataformat-yaml` が無い（`backend/build.gradle.kts:54-84`、`backend/gradle.lockfile`）。Jackson は 3 系（`tools.jackson`）で、SnakeYAML 2.6 は推移依存としてだけある。JSON Schema の検証の部品の多くは Jackson 2 系（`com.fasterxml.jackson`）を前提にしているため、選ぶときに Jackson 3 との組み合わせ（2 系の併存を含む）を確かめる必要がある。足した部品は lockfile の更新と OSV の関門（High 以上で失敗）を通す必要がある。画面にも YAML の解析と JSON Schema の検証の部品は無い |
| C-4 | Testcontainers が未導入 | `team.md` の Testing Posture は、コンテナで動かす対象DB のテストに Testcontainers を使うとしている。今は依存も、ローカル（colima）と CI（GitHub Actions）でのコンテナの実行環境の前提の文書化も、`verify` の結合テストの段（段 6）への組み込みも無い。README には「U1 のテストはコンテナの実行環境を必要としない」とある。3種類の DB をすべてテストすると、`verify` の時間と colima の VM のメモリに影響する |
| C-5 | 対象DB への書き方と、層の決まり | 内部DB の生 SQL は H2 の方言（TD-1）で、流用できない。ArchUnit の決まり（`@Transactional` は service の層だけ、web は repository を使わない、エンティティを返さない、コンストラクター注入だけ）は `cherry.mastersmith` の下の全クラスに当たり、対象DB への読み書きの層も従う必要がある |
| C-6 | 複数の検証エラーの応答の形が無い | Problem Details の拡張の項目は `code`・`traceId` だけで、`detail` は1つの文字列である（`common/error/web/ErrorResponseFactory.java:65-81`）。入力の検証エラー（`VALIDATION_FAILED`）も項目ごとの一覧を持たない。画面の `ApiError` も `{ kind, status, code? }` だけで、本文のほかの項目を渡さない（`frontend/src/shared/api-client/apiClient.ts`）。DSL の検証エラーを行・項目ごとに返すなら、形を新しく決める必要がある。新しい `code` は機能ごとの `ProblemTypeCatalog` に日英の説明つきで登録する（重複で起動失敗） |
| C-7 | 要求の本文の上限が 1MB | `mastersmith.web.max-request-body-size`（既定 1MB、`application.yaml:25`）を `RequestSizeLimitFilter` が全要求に当て、超えたら 413。マルチパートの上限の設定は `application.yaml` に無く（Spring Boot の既定のまま）、`MaxUploadSizeExceededException` は 413 に変換される（`GlobalExceptionHandler.java:176`）。完成品の DSL の投入が 1MB を超えうるなら、上限か API の分け方を決める必要がある |
| C-8 | 監査の対象の拡張 | DSL の生成・リセット・適用を監査の対象にするなら、`AuditEventType`・`audit_events` の表（Flyway の `V5` 以降）・`AuditEventFactory`・`AuditEventListener` の拡張が要る（TD-2）。`project.md` の DECIDED（共通化は業務データの CRUD を扱う後続 Intent で検討）との関係を要件で整理する必要がある。内部DB に DSL（プレビューと適用済みの版など）を保存する場合も、Flyway の `V5` 以降に足す（前進のみ、Hibernate は `validate` のため食い違いは起動時に失敗） |
| C-9 | 対象DB の秘密情報とヘルスチェック、コンテナの構成 | 対象DB の接続情報（パスワード）は秘密情報で、`project.md` の Forbidden に当たる。既存の型は `application.yaml` に環境変数の参照だけを置き、`.env.example` に空の値で足し、設定の型の `toString` で伏せ字にしている（TD-4）。`/actuator/health` は内部DB だけを見ている（`common/health/`）。対象DB を含めると、対象DB の停止でアプリ全体が DOWN になる。`compose.yaml` に対象DB のサービスは無い。対象DB 用の接続プールを別に足すと、コンテナのメモリの上限（既定 1g、VM を広げた PC では 2g。`.env.example`・README）とプールの数の見積もりに関わる（TD-3） |
| C-10 | 画面の部品と置き場 | 管理者のみの画面は `features/<featureId>/registration.ts` に `layout: 'SHELL'`・`access: 'ADMIN'` で登録し、サイドバーは `visibleWhen: 'ADMIN'` にする。API は `/api/admin/` の下に置けば守られる（`access/domain/AdminPaths.java:30-36`）。振り分けは完全一致の1画面で、プレビュー→適用の段階の URL は機能の側で工夫が要る（TD-5）。画面にも make-you-chic-ui にも、ファイルの選択・差分の表示・コードエディターの部品は無く、make-you-chic-ui の中身は変更できない（`project.md` の Forbidden）。今ある部品は Table・Tabs・Textarea・Modal など（`component-inventory.md` の `make-you-chic-ui`） |

## 健全性のまとめ

- 層の境界・エラー応答・ログ・秘密情報の扱い・検査の関門は、決まりがコードとテストで確かめられており、健全である。
- 今回の Intent で手を入れる見込みの部品は、`component-inventory.md` で at-risk とした（`common-error`・`common-web`・`common-health`・`audit`・`frontend-app-core`・`frontend-api-client`・`frontend-feature-admin`・`make-you-chic-ui`・`build-and-verify`・`container-runtime`）。degraded（不具合あり）の部品は見当たらない。
