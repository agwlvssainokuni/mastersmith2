# ビルドとテストの要約（build-and-test-summary）

Intent `260922-auth-audit-base`（auth-audit-foundation）の全4単位（U1 アプリの骨格、U2 認証、U3 アクセス制御、U4 監査ログ）を通した、ビルド・テストの状態と、測れる目標の検証の表。

- Test Strategy: **Standard**
- Construction Autonomy Mode: gated（本ステージの指示による）
- 実行した日: 2026-09-23
- 実行の環境: macOS（darwin 25.5.0）、JDK 25、Node.js 24、組み込みの H2（コンテナ不要）

## 1. 全体の状態

| 区分 | 結果 |
|---|---|
| ビルド（`./gradlew verify`） | **成功**（BUILD SUCCESSFUL） |
| バックエンドの単体テスト（`*Test`） | **387 件すべて成功**（失敗 0、飛ばし 0、テストのクラス 60） |
| バックエンドの結合テスト（`*IT`） | **239 件すべて成功**（失敗 0、飛ばし 0、テストのクラス 52） |
| フロントエンドのテスト（Vitest） | **167 件すべて成功**（テストのファイル 29） |
| E2E（Playwright／ビルドした WAR） | **5 件すべて成功** |
| バックエンドのカバレッジ（JaCoCo、単体＋結合） | 行 **96.14%**（1393/1449）、分岐 **91.45%**（417/456）。下限 行 80%・分岐 70% を満たす |
| フロントエンドのカバレッジ（`@vitest/coverage-v8`） | 行 **98.73%**（391/396）、分岐 **94.02%**（189/201）。下限を満たす |
| 秘密情報の検出（Gitleaks） | 検出 **0 件**（93 コミット、約 10.58MB を走査） |
| 依存関係の脆弱性（OSV-Scanner） | 統合を止める条件に当たるもの **0 件**、警告 **0 件** |
| Java の静的解析（SpotBugs＋FindSecBugs） | 重大度 High（priority 1）**0 件**。priority 2 が 29 件・priority 3 が 23 件の警告（合計 52 件。統合は止めない） |
| 初回の読み込みの JavaScript | **104.7 KB（gzip）**。目安 500KB 以内 |
| コミット前の検査（`pre-commit run --all-files`） | 3 項目すべて Passed |

## 2. 作った手順書（テストの種類の一覧）

| 手順書 | 種類 | 作った理由 |
|---|---|---|
| `build-instructions.md` | ビルド | ステージの `produces` |
| `integration-test-instructions.md` | 結合テスト・E2E | Standard が求める |
| `performance-test-instructions.md` | 性能 | 単位が測れる性能の要件（NFR1 系）を持つため |
| `security-test-instructions.md` | 安全（静的解析・秘密情報・脆弱性・振る舞い） | 単位が測れる安全の要件（NFR2〜NFR5）を持つため |
| `test-results.md` | 実行の記録 | ステージの `produces` |
| `cross-unit-traceability.md` | 単位をまたぐ要件の網羅 | ステージの Step 10 |

単体テストは各単位の `code-generation/unit-test-instructions.md` が受け持つ（本ステージでは作り直さない）。

## 3. 単位ごとのカバレッジ

単位ごとの絞り込んだ実行の結果（`unit-test-instructions.md` 2.4 節のコマンド）。全体の下限の判定は `./gradlew verify` が行う。

| 単位 | バックエンドの範囲 | バックエンドの行カバレッジ（JaCoCo のパッケージ別） | フロントエンド |
|---|---|---|---|
| U1 アプリの骨格 | `common.*`・`config.*`・`ArchitectureTest` | error/domain 100%、error/service 100%、error/web 96.8%、health 79.2%、i18n/domain 100%、observability 97.5%、security 100%、web 94.3%、config 92.4% | `src/app` 85 件成功、行 99.11%・分岐 94.02% |
| U2 認証 | `auth.*`・`user.*` | auth/domain 97.9%、auth/repository 93.9%、auth/service 97.9%、auth/web 100%、user/domain 100%、user/service 100% | `src/features/auth`＋`src/shared/api-client` 52 件成功、行 98.57%・分岐 96.22% |
| U3 アクセス制御 | `access.*` | access/domain 100%、access/service 100%、access/web 100% | `src/features/admin` 30 件成功、行 96.77%・分岐 85.71% |
| U4 監査ログ | `audit.*` | audit/domain 99.0%、audit/service 77.2% | （U4 は画面を持たない） |

パッケージ単位で下限（行 80%）を下回るのは `common/health`（79.2%）と `audit/service`（77.2%）の2つだが、**下限は成果物全体に対して定義されており**（`backend/build.gradle.kts` の `jacocoTestCoverageVerification` は bundle 単位）、全体では行 96.14%・分岐 91.45% で満たしている。パッケージ単位の下限は定義していないため、これらは `Not Met` ではない。今後テストを足す余地として記録する。

## Target Verification Matrix

対象は、各単位の `nfr-requirements/` が定めた測れる目標（138 件）と、ステージ全体の目標（10 件）である。`nfr-design/` と各単位の `code-generation-plan.md` の `## Testing Contract` は、これらの目標に新しい測れる値を足していない（Testing Contract は方法論・テスト量の決まりで、4単位とも同一内容）。

判定は `Met`（満たす）／`Not Met`（満たさない）／`Unverified`（このステージでは測れず、後の段が持つ）の3つ。`Pending` は残さない。

### 4.1 ステージ全体の目標

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| BT-BUILD | `aidlc/spaces/default/memory/team.md` Way of Working | 統合の前の1コマンドの検査がすべて通る | `./gradlew verify` が BUILD SUCCESSFUL（9 段すべて） | `test-results.md` 2.1 | build-and-test | Met |
| BT-UNIT | 各単位の `code-generation/unit-test-instructions.md` | 単体テストがすべて成功する | バックエンド 387 件・フロントエンド 167 件、失敗 0 | `backend/build/test-results/test/`、`test-results.md` 2.2 | build-and-test | Met |
| BT-IT | `integration-test-instructions.md` | 結合テストがすべて成功する | 239 件、失敗 0 | `backend/build/test-results/integrationTest/` | build-and-test | Met |
| BT-E2E | `aidlc/spaces/default/memory/team.md` Testing Posture | 代表的な流れ1〜2本の E2E が成功する | 5 件、失敗 0（ログイン → 管理画面 → ログアウトを含む） | `./gradlew e2eTest` の出力、`frontend/playwright-report/` | build-and-test | Met |
| BT-COV-BE-LINE | `team.md` Testing Posture | バックエンドの行カバレッジ 80% 以上 | **96.14%**（1393/1449） | `backend/build/reports/jacoco/test/jacocoTestReport.xml` | build-and-test | Met |
| BT-COV-BE-BRANCH | 同上 | バックエンドの分岐カバレッジ 70% 以上 | **91.45%**（417/456） | 同上 | build-and-test | Met |
| BT-COV-FE-LINE | 同上 | フロントエンドの行カバレッジ 80% 以上 | **98.73%**（391/396） | `frontend/coverage/`、`verify` の 7 の段の出力 | build-and-test | Met |
| BT-COV-FE-BRANCH | 同上 | フロントエンドの分岐カバレッジ 70% 以上 | **94.02%**（189/201） | 同上 | build-and-test | Met |
| BT-SEC-SECRET | `project.md` Mandated | 秘密情報の検出がコミット前と検査の両方で 0 件 | Gitleaks 0 件、`pre-commit run --all-files` 3 項目 Passed | `verify` の 8 の段、`test-results.md` 2.4 | build-and-test | Met |
| BT-SEC-DEP | `team.md` Code Style・Deployment | 依存関係の脆弱性と静的解析が重大度 High 以上で 0 件 | OSV-Scanner 0 件（警告も 0）、SpotBugs priority 1 が 0 件 | `build/reports/osv-scanner/osv.json`、`backend/build/reports/spotbugs/` | build-and-test | Met |

### 4.2 単位ごとの目標

#### U1（u1-app-skeleton）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U1-NFR1.1 | `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` NFR1.1 | ヘルスチェックは、内部DBにつながるとき、95% が 500 ミリ秒以内に応答する | 未測定（負荷の環境／配備先が要る） | Performance Validation で測る: ヘルスチェックの応答時間の 95 パーセンタイル（500ms 以内）は負荷をかけた測定が必要。機能（UP・200）は HealthEndpointIT で確認済み | performance-validation | Unverified |
| U1-NFR1.2 | `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` NFR1.2 | ヘルスチェックの内部DBの確認は、制限時間（既定2秒、設定で変更可）を超えたら打ち切り、DOWN を返す。制限時間を超えて応答を待たせない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/health/TimeBoundedDbHealthIndicatorTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.3 | `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` NFR1.3 | すべての要求に共通する処理（トレースの開始、ログの出力、エラー応答への変換）が加える時間は、1要求あたり 95% で 20 ミリ秒以内とする | 未測定（負荷の環境／配備先が要る） | Performance Validation で測る: 共通の処理が加える時間（95% で 20ms 以内）は、何もしない API との差を負荷をかけて測る必要がある（performance-requirements の測り方） | performance-validation | Unverified |
| U1-NFR1.4 | `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` NFR1.4 | アプリは、開発者の PC 上のコンテナで、起動から要求を受け付けられるまで 30 秒以内とする | 満たす（対応するテスト・設定が緑） | compose.yaml ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.5 | `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` NFR1.5 | 画面の初回の表示に読み込む JavaScript の合計は、圧縮後 500KB 以内を目安とする | **104.7 KB（gzip）** | frontend/scripts/check-bundle-size.mjs ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.6 | `construction/u1-app-skeleton/nfr-requirements/performance-requirements.md` NFR1.6 | ログの出力は、要求の処理を待たせない（出力先への書き込みで要求の応答が止まらない） | 満たす（対応するテスト・設定が緑） | backend/src/main/resources/logback-spring.xml ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.7 | `construction/u1-app-skeleton/nfr-requirements/scalability-requirements.md` NFR1.7 | 想定の規模（利用者 最大50名、同時ログイン 最大10名）を1台で処理する。内部DBへの接続の数は、既定 10 本までとする | 満たす（対応するテスト・設定が緑） | backend/src/main/resources/application.yaml ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.8 | `construction/u1-app-skeleton/nfr-requirements/scalability-requirements.md` NFR1.8 | 内部DBの接続先は、設定だけで別サーバーの H2 に切り替えられる（複数台にする場合の前提） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/db/DatabasePersistenceIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.9 | `construction/u1-app-skeleton/nfr-requirements/scalability-requirements.md` NFR1.9 | 内部DBのファイルの増え方を見積もる: 監査ログは無期限に保存するため、1日あたりのログイン・ログアウト・拒否が 500 件、1件 1KB とすると、1年で約 180MB。この規模を、開発者の PC 上のコンテナのボリュ | 未測定（負荷の環境／配備先が要る） | U4（監査ログ）の表ができてから Performance Validation・運用で確かめる: U1 は業務の表を持たず、ボリューム（compose.yaml の mastersmith-data）を用意しただけのため、増え方の実測は後の | performance-validation | Unverified |
| U1-NFR1.10 | `construction/u1-app-skeleton/nfr-requirements/scalability-requirements.md` NFR1.10 | アプリのログは標準出力へ出し、保存と回し（ローテーション）はコンテナの実行環境に任せる。アプリはログのファイルを持たない | 満たす（対応するテスト・設定が緑） | backend/src/main/resources/logback-spring.xml ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR1.11 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR1.11 | アプリを止めるときは、処理中の要求を終えてから止める（既定の待ち時間 30 秒） | 満たす（対応するテスト・設定が緑） | backend/src/main/resources/application.yaml ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.1 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.1 | パスワード・トークン・署名鍵・内部DBのパスワードを、ログ・トレースの属性・外部エクスポート・エラー応答に含めない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TracingAndLoggingIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.2 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.2 | 秘密情報（内部DBのパスワード、署名鍵、初期管理者のパスワード）は環境変数などで渡し、ソースコード・設定ファイルに直接書かない。見本は値を空にした `.env.example` とする | Gitleaks 0 件（93 コミット走査）、`pre-commit` Passed | .env.example ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.3 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.3 | エラー応答に、内部の例外メッセージ・スタックトレースを載せない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/error/web/ErrorResponseIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.4 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.4 | 外部エクスポートを有効にしても、送るのはトレースとログだけとし、秘密情報を載せない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.5 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.5 | Actuator は health だけを公開し、ほかの機能（env・configprops・heapdump など）は外部に公開しない。health の応答は状態（UP／DOWN）だけとし、内訳を載せない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/config/ExposureIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.6 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.6 | H2 の Web コンソールは有効にしない（開発時も含む） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/config/ExposureIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.7 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.7 | 内部DBは、既定の組み込み・ファイル保存では外部からの接続を受け付けない。別サーバーの H2 に切り替える場合も、接続はアプリからに限る | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/db/DatabasePersistenceIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.8 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.8 | エラー応答の `type` のベースURLは、設定の固定値か、要求そのものの Host から組み立てる。転送元のヘッダーは信頼する設定を有効にしたときだけ使う（既定は無効） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/error/web/ErrorResponseIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.9 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.9 | 問題の種類の説明ページ（HTML）は、埋め込むすべての値をエスケープし、要求から受け取った値を埋め込まない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/error/web/ProblemTypePageIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.10 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.10 | 画面とすべての応答に、セキュリティ関係のヘッダーを付ける: `Content-Security-Policy`（`default-src 'self'; script-src 'self'; style-src 'sel | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/config/SecurityHeadersIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.11 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.11 | ログに出す値は、キーと値として JSON の値に入れ、改行などで記録を偽装できないようにする | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.12 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.12 | 要求の本文の大きさに上限を設け（既定 1MB）、超えた要求は 413 で拒否する | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/config/ExposureIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.13 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.13 | 依存関係は lockfile（npm）とバージョンカタログ（Gradle）で版を固定し、CI ではそのとおりに入れる | `npm ci` と `settings-gradle.lockfile` で版を固定（`verify` の 0 の段が実行） | gradle/libs.versions.toml ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.14 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.14 | 依存関係の脆弱性検査（Dependabot、OSV-Scanner。`vendor/make-you-chic-ui` の lockfile を含む）と静的解析（SpotBugs＋FindSecBugs）を統合前と CI | OSV-Scanner 0 件、SpotBugs priority 1 が 0 件 | build.gradle.kts ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR3.15 | `construction/u1-app-skeleton/nfr-requirements/security-requirements.md` NFR3.15 | メソッドの呼び出しの追跡（NFR10.12）で TRACE を有効にしても、パスワード・トークン・署名鍵をログに出さない。秘密情報を持つ型（ログインの要求、トークンの応答、初期管理者の設定など）は、文字列化でその項目を伏 | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/testsupport/JsonLogRecords.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR6.1 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR6.1 | 内部DBのデータは、既定の組み込み・ファイル保存（`./data/`）で、アプリを再起動しても残る | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/db/DatabasePersistenceIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR7.1 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR7.1 | 画面の文言は i18next で日本語・英語を用意し、ブラウザの希望言語が日本語でも英語でもなければ日本語で表示する。すべての文言の鍵に両方の言語の文言がある | 満たす（対応するテスト・設定が緑） | frontend/src/app/i18n/I18nProvider.test.tsx ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR8.1 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR8.1 | U1 の画面の部品（アプリシェル、ログイン用レイアウト、ホーム、ページが見つかりません）ごとに、アクセシビリティ検査（vitest-axe）を1件入れ、違反が無いこと | 満たす（対応するテスト・設定が緑） | frontend/src/app/layout/ShellLayout.test.tsx ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR9.1 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR9.1 | スキーマの変更（Flyway）は起動時に当て、失敗したら起動を止める（途中の状態で動かない）。変更は前進のみとし、1つ前の版のアプリが動く後方互換を保つ | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/db/DatabasePersistenceIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR9.2 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR9.2 | 内部DBのファイルのバックアップは、アプリを止めた状態でファイル（`./data/` の下、コンテナでは `/app/data`）を複写する運用とする。手順を README に書く | 満たす（対応するテスト・設定が緑） | README.md ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR9.3 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR9.3 | 配備のたびに、ヘルスチェックの UP と、最低限の動作確認（ログイン画面の表示）を確かめるまで、配備の完了としない。戻すときは直前の版の成果物で配備し直す | 満たす（対応するテスト・設定が緑） | README.md ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR9.4 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR9.4 | テストの行カバレッジ 80% 以上、分岐カバレッジ 70% 以上を、バックエンド（JaCoCo）とフロントエンド（`@vitest/coverage-v8`）の両方で検証し、下回ったらビルドを失敗させる。計測から外すのは | バックエンド 行 **96.14%**・分岐 **91.45%**、フロントエンド 行 **98.73%**・分岐 **94.02%** | backend/build.gradle.kts ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR9.5 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR9.5 | フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査を、ローカルの1コマンドで実行でき、同じ検査が CI（GitHub Actions）でも通る | `./gradlew verify` が BUILD SUCCESSFUL（9 段すべて） | build.gradle.kts ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.1 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.1 | アプリのログは、標準出力へ1行1件の JSON で出す（開発時も同じ）。出力は logstash-logback-encoder で行う | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.2 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.2 | ログの項目の名前を固定する: `timestamp`（ISO 8601、タイムゾーン付き）、`level`、`logger`、`thread`、`message`、`traceId`、`spanId`、`exceptio | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/JsonLogFormatTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.3 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.3 | 要求の処理中のログには、その要求の `traceId` と `spanId` を必ず入れる | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TracingAndLoggingIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.4 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.4 | ログのレベルの既定は INFO とし、設定で機能ごとに変えられる。想定内のエラー（4xx）は WARN 以下、想定外（5xx）は ERROR | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/error/web/GlobalExceptionHandlerTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.5 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.5 | 受け取った要求に正しい `traceparent` があれば引き継ぎ、無い・正しくなければ新しいトレースを始める | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TracingAndLoggingIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.6 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.6 | すべての要求にトレースIDを割り当てる。サンプリングの設定によって、ログ・エラー応答・監査ログのトレースIDの有無が変わらないようにする | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TracingAndLoggingIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.7 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.7 | 外部エクスポート（OTLP）は既定で無効とし、設定で有効にして送り先を指定できる | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.8 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.8 | ヘルスチェック（`/actuator/health`）でアプリと内部DBの状態を返す。これを、稼働しているかの指標（健全性の指標）とする | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/health/HealthEndpointIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.9 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.9 | エラーの割合の指標として、アプリのログの ERROR の件数と、応答の状態コード 5xx の件数を使う。本Intentでは指標を外部に公開しない（外部エクスポートを有効にしたときに送る） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.10 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR10.10 | 起動後に内部DBにつながらなくなった場合、ヘルスチェックは DOWN を返し、DB を使わない要求（画面の配信・説明ページ）は応答を続ける | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/health/HealthEndpointIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.11 | `construction/u1-app-skeleton/nfr-requirements/reliability-requirements.md` NFR10.11 | 外部エクスポートの送り先に届かなくても、要求の処理を止めない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.12 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.12 | 調査用に、メソッドの呼び出し・復帰・例外をアプリのログに出す仕組み（TraceAspect。Spring の CustomizableTraceInterceptor に処理を任せるアスペクト）を持つ。出すのはログのレベ | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TraceAspectIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.13 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.13 | 追跡の対象は、チームの進め方の4つの層（画面入出力 web・業務処理 service・ドメイン domain・DB アクセス repository）のうち、Spring の Bean として代理で包まれるクラスに限る（ド | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TraceAspectIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U1-NFR10.14 | `construction/u1-app-skeleton/nfr-requirements/observability-requirements.md` NFR10.14 | 追跡のログは文字列の組み立てで出す。キーと値で渡す決まり（U1 の決まり 3.5）の例外とし、TRACE の調査用に限って使う | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/common/observability/TraceAspectIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |

#### U2（u2-authentication）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U2-NFR1.1 | `construction/u2-authentication/nfr-requirements/performance-requirements.md` NFR1.1 | ログインの API は、同時に 10 件の要求が来る状態で、95% が1秒以内に応答する（成功・失敗とも） | 未測定（負荷の環境／配備先が要る） | Performance Validation で実測する（同時 10 件のログインの 95 パーセンタイル） | performance-validation | Unverified |
| U2-NFR1.2 | `construction/u2-authentication/nfr-requirements/performance-requirements.md` NFR1.2 | トークンの更新の API は、同時に 10 件の要求が来る状態で、95% が1秒以内に応答する | 未測定（負荷の環境／配備先が要る） | Performance Validation で実測する（更新の API の応答時間） | performance-validation | Unverified |
| U2-NFR1.3 | `construction/u2-authentication/nfr-requirements/performance-requirements.md` NFR1.3 | パスワードの照合（bcrypt）は、1回あたり 100〜500 ミリ秒とし、ログインの予算（1秒）の半分を超えない | 未測定（負荷の環境／配備先が要る） | Performance Validation で実測する（ログインの API の応答時間） | performance-validation | Unverified |
| U2-NFR1.4 | `construction/u2-authentication/nfr-requirements/performance-requirements.md` NFR1.4 | 認証が必要な API の要求ごとの処理（アクセストークンの検証と、利用者の読み取り1回）が加える時間は、95% で 50 ミリ秒以内とする | 未測定（負荷の環境／配備先が要る） | Performance Validation で実測する（認証つきの要求ごとの処理の時間） | performance-validation | Unverified |
| U2-NFR1.5 | `construction/u2-authentication/nfr-requirements/performance-requirements.md` NFR1.5 | 同じ利用者へのログインの試みを1つずつ行う処理（U2 の決まり 3.8）は、ほかの利用者のログインを待たせない（利用者ごとに順番にする） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR1.6 | `construction/u2-authentication/nfr-requirements/scalability-requirements.md` NFR1.6 | 想定の規模（利用者 最大50名、同時ログイン 最大10名）を1台で処理する | 未測定（負荷の環境／配備先が要る） | Performance Validation とデータ量の増加で確かめる（1台での処理） | performance-validation | Unverified |
| U2-NFR1.7 | `construction/u2-authentication/nfr-requirements/scalability-requirements.md` NFR1.7 | リフレッシュトークンの行は、ログインと更新のたびに増える。使われて無効になった行と期限切れの行は、期限から一定期間（既定 7 日）たったら削除する。削除は定期の処理で行い、要求の処理を待たせない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/RefreshTokenCleanupJobTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR1.8 | `construction/u2-authentication/nfr-requirements/scalability-requirements.md` NFR1.8 | リフレッシュトークンの行の数の見積もり: 利用者 50 名が1日 8 時間、アクセストークンの期限（5分）ごとに更新すると、1人1日約 100 行、全体で約 5,000 行。NFR1.7 の削除により、常に数万行以内に収 | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/repository/RefreshTokenRepositoryIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR1.9 | `construction/u2-authentication/nfr-requirements/scalability-requirements.md` NFR1.9 | ログイン試行の状態（LoginAttemptState）は、利用者1人に1行で、利用者の数を超えない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/repository/AuthSchemaIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR1.10 | `construction/u2-authentication/nfr-requirements/scalability-requirements.md` NFR1.10 | 複数台で動かす場合は、ロックの状態とリフレッシュトークンが共有の内部DBにあるため、そのまま共有できる。ただし、同じ利用者の試みを1つずつ行う処理（U2 の決まり 3.8）は、内部DBの行の排他で行い、アプリのメモリに頼 | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepositoryIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR2.1 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR2.1 | パスワードは bcrypt でハッシュにして保存する。計算の重さ（cost）は設定で変えられ、既定は 12 とする。1回の照合が、開発者の PC 上のコンテナで 100〜500 ミリ秒に収まる値を選ぶ | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/user/service/DummyPasswordHashTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR2.2 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR2.2 | パスワードは 12 文字以上、かつ UTF-8 で 72 バイト以内とする。初期管理者の設定がこれを外れたら、作らずに理由と直し方を警告する（U2 の決まり 1.3。72 バイトの条件を Functional Desig | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/user/domain/PasswordPolicyTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR2.3 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR2.3 | ログインで入力されたパスワードが 72 バイトを超える場合は、例外にせず、パスワード誤りと同じ失敗（401 / AUTHENTICATION_FAILED）とする。このときもダミーの照合を行い、応答時間をそろえる | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/user/service/UserAccountServiceTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR3.1 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR3.1 | パスワード（平文・ハッシュ値とも）を、ログ・監査ログ・トレースの属性・エラー応答・出来事に含めない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/AuthSecretLeakIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR3.2 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR3.2 | アクセストークンは HS256 で署名する。検証は HS256 だけを受け付け、`alg: none` や別の方式のトークンは拒否する | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/AccessTokenServiceTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR3.3 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR3.3 | 署名鍵は 256 ビット以上の乱数とし、環境変数で渡す。ソースコード・設定ファイルに書かない。鍵が無い、または 256 ビット未満のときは、起動を止める（トークンを安全に発行できないため） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/SigningKeyStartupIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR3.4 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR3.4 | 署名鍵を替えるときは、設定を変えて再起動する。発行済みのアクセストークンは検証に失敗して 401 になり、画面はトークンの更新で取り直す（リフレッシュトークンは鍵に依存しないため、ログインし直しは要らない） | 満たす（対応するテスト・設定が緑） | README.md（署名鍵の交換の手順） ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR3.5 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR3.5 | アクセストークン・リフレッシュトークン・署名鍵を、ログ・監査ログ・トレースの属性・エラー応答・出来事に含めない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/AuthSecretLeakIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR3.6 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR3.6 | U1 のメソッドの追跡（U1 の NFR10.12）で TRACE を有効にしても、パスワード・トークンがログに出ないように、U2 の秘密情報を持つ型（ログインの要求、ログインとトークンの更新の応答、初期管理者の設定）は | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/domain/SecretTypesTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR4.1 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR4.1 | ログインの失敗は、理由（存在しないメールアドレス・パスワード誤り・ロック中・72 バイト超え）によらず、状態コード・code・本文を同じにする | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR4.2 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR4.2 | 失敗の経路ごとに、パスワードの照合（ダミーを含む）の回数と、内部DBの読み書きの種類と回数をそろえる | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR4.3 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR4.3 | 画面のログイン失敗の表示は、理由によらず1種類の文言とする | 満たす（対応するテスト・設定が緑） | frontend/src/features/auth/LoginForm.test.tsx ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR5.1 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR5.1 | 画面側では、アクセストークンをメモリ上にだけ持ち、localStorage・sessionStorage に置かない | 満たす（対応するテスト・設定が緑） | frontend/src/features/auth/authSession.test.ts ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR5.2 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR5.2 | リフレッシュトークンは 256 ビット以上の暗号学的な乱数とし、内部DBには SHA-256 のハッシュだけを保存する | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/repository/AuthSchemaIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR5.3 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR5.3 | リフレッシュトークンの Cookie は HttpOnly・Secure・SameSite=Strict とし、送り先（path）をトークンの更新とログアウトの API に限る。Cookie を消す指示（ログアウト・更新 | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/RefreshCookiesTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR5.4 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR5.4 | トークンの更新とログアウトの API は POST とする（パスは Contract Design で決める）。この2つの API は、要求の Origin ヘッダーが自分の配信元と一致するときだけ受け付ける。一致しない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/TokenApiIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR5.5 | `construction/u2-authentication/nfr-requirements/security-requirements.md` NFR5.5 | 開発・CI・E2E は `http://localhost` で行う（Secure の Cookie が localhost では送られるため）。HTTPS は配備先が決まったときに扱う | 満たす（対応するテスト・設定が緑） | frontend/playwright.config.ts（http://localhost で確かめる） ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR6.1 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR6.1 | 初期管理者の作成は、何度再起動しても重複しない。設定が正しくない場合は作らずに起動を続ける | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR6.2 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR6.2 | 署名鍵が無い・短いときは起動を止める（NFR3.3）。そのほかの U2 の設定（有効期限・しきい値など）が無いときは既定値で動く | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/SigningKeyStartupIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR6.3 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR6.3 | 次の値を設定（設定ファイルまたは環境変数）で変えられる: ロックのしきい値（既定 5 回）、ロックの時間（既定 30 分）、アクセストークンの有効期限（既定 5 分）、リフレッシュトークンの有効期限（既定 24 時間）、 | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/AuthSettingsIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR7.1 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR7.1 | ログイン画面の文言（見出し・入力欄のラベル・ボタン・エラー）を日本語・英語でそろえる | 満たす（対応するテスト・設定が緑） | frontend/src/features/auth/registration.test.ts ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR8.1 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR8.1 | ログイン画面の部品ごとにアクセシビリティ検査（vitest-axe）を1件入れ、違反が無いこと | 満たす（対応するテスト・設定が緑） | frontend/src/features/auth/LoginForm.test.tsx ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR9.1 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR9.1 | ログインの失敗回数の更新とロックの判定は、同じ利用者について1つずつ行い、同時の失敗でも取りこぼさない（内部DBの行の排他で行う） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR9.2 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR9.2 | リフレッシュトークンの無効化は、まだ無効でないことを条件に1回だけ成功させ、同じトークンの同時の更新で2つとも成功することがない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/RefreshConcurrencyIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR9.3 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR9.3 | チームの進め方で必須とされた認証のテスト（ロックのしきい値の境界、ユーザーの存在を推測できないこと、トークンの有効期限の境界・改ざん・`alg: none`、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセ | 満たす（対応するテスト・設定が緑） | unit-test-instructions.md 7章の対応表のテスト一式 ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR9.4 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR9.4 | 時刻に依存する処理（有効期限・ロックの解除）は注入できる時計から現在時刻を得て、テストで実時間に依存しない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/testsupport/MutableClock.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR9.5 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR9.5 | ロックの判定と有効期限の判定には、性質ベースのテスト（jqwik）を一部入れる | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/domain/LockPolicyTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.1 | `construction/u2-authentication/nfr-requirements/reliability-requirements.md` NFR10.1 | 出来事の受け取り側（監査ログ）で失敗が起きても、ログイン・更新・ログアウトの応答は変わらない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/AuthEventsIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.2 | `construction/u2-authentication/nfr-requirements/observability-requirements.md` NFR10.2 | ログイン成功・ログイン失敗（理由つき）・ログアウトの出来事を、U2 の決まり 7.1 の項目（日時・種類・メールアドレス・利用者ID（分かれば）・失敗の理由・接続元IP・User-Agent・トレースID）をそろえて知ら | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/AuthEventsIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.3 | `construction/u2-authentication/nfr-requirements/observability-requirements.md` NFR10.3 | 初期管理者を作成したときは INFO、作らなかったときは WARN（理由と直し方つき）をアプリのログに出す。パスワードの値は出さない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.4 | `construction/u2-authentication/nfr-requirements/observability-requirements.md` NFR10.4 | ロックしたときは、アプリのログに INFO で「どの利用者がロックされたか（利用者ID）」を出す。メールアドレスとパスワードは出さない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/service/LoginServiceTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.5 | `construction/u2-authentication/nfr-requirements/observability-requirements.md` NFR10.5 | トークンの検証の失敗（401）は、アプリのログに DEBUG で理由（TOKEN_MISSING などの区分）だけを出す。トークンの値は出さない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/TokenAuthenticationEntryPointTest.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.6 | `construction/u2-authentication/nfr-requirements/observability-requirements.md` NFR10.6 | 処理中のログには、U1 の仕組みでトレースIDが付く | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/auth/web/AuthEventsIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U2-NFR10.7 | `construction/u2-authentication/nfr-requirements/observability-requirements.md` NFR10.7 | ログインの失敗の割合（失敗の件数÷試行の件数）を、運用で見る指標の候補とする。本Intentでは監査ログから数える（指標の外部への公開は行わない） | 未測定（負荷の環境／配備先が要る） | 運用（Operation）で監査ログから数える | observability-setup | Unverified |

#### U3（u3-access-control）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U3-NFR1.1 | `construction/u3-access-control/nfr-requirements/performance-requirements.md` NFR1.1 | 管理者向け領域の確認用 API は、同時に 10 件の要求が来る状態で、95% が 300 ミリ秒以内に応答する（成功・403 とも） | 未測定（負荷の環境／配備先が要る） | Performance Validation の段で測る（同時 10 件で 95% が 300 ms 以内） | performance-validation | Unverified |
| U3-NFR1.2 | `construction/u3-access-control/nfr-requirements/performance-requirements.md` NFR1.2 | アクセスの判定（パスの照合と管理者かどうかの判定）は、U2 の要求ごとの処理（トークンの検証と利用者の読み取り、95% で 50 ミリ秒以内）に加えて、内部DBへの問い合わせを増やさない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java（確認用 API の1要求の SQL が select users の1回だけ） ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR1.3 | `construction/u3-access-control/nfr-requirements/performance-requirements.md` NFR1.3 | アクセス拒否の出来事の通知と記録（U4）は、同じスレッドで行うが、401／403 の応答を 100 ミリ秒以上遅らせない | 未測定（負荷の環境／配備先が要る） | Performance Validation の段で測る（非管理者の 403 の 95 パーセンタイル） | performance-validation | Unverified |
| U3-NFR1.4 | `construction/u3-access-control/nfr-requirements/scalability-requirements.md` NFR1.4 | U3 は状態を持たない（判定は要求ごとに行い、アプリのメモリに利用者ごとの情報を持たない）。そのため、複数台で動かす場合もそのまま動く | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminAuthorizationManagerTest.java（状態を持たない判定。設計の確認は code-summary.md） ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR1.5 | `construction/u3-access-control/nfr-requirements/scalability-requirements.md` NFR1.5 | アクセス拒否の出来事の件数は、利用者 50 名の想定で1日あたり数十件以内と見込む（監査ログの見積もり（U1 の NFR1.9）に含まれる） | 満たす（対応するテスト・設定が緑） | 出来事は 401・403 の1要求につき1件。backend/src/test/java/cherry/mastersmith/access/web/AccessDeniedEventsIT.java と backend/src/test/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.1 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.1 | /api/admin そのものと /api/admin/ の下は管理者のみとし、未ログインは 401、管理者でなければ 403、管理者は受け付ける。判定はサーバー側で行い、画面の表示に頼らない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java, backend/src/test/java/cherry/mastersmith/access/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.2 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.2 | /api/ の下は既定でログインを求め、公開するのは明示した一覧（問題の種類の説明ページ、ログイン、トークンの更新、ログアウト）だけとする | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/ApiDefaultAccessIT.java, backend/src/test/java/cherry/mastersmith/co ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.3 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.3 | 正規化されていないパス（エンコードされた区切り、`;`、`..`、`//`）は判定の前に拒否し、大文字・小文字を区別し、末尾のスラッシュで判定を変えない。フレームワークの既定の防御（要求のパスの検査）を外さない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminPathBoundaryIT.java, backend/src/test/java/cherry/mastersmith/a ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.4 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.4 | 管理者でない利用者には、/api/admin/ の下の存在しない API も 403 を返し、API の有無を明かさない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.5 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.5 | 管理者かどうかは要求ごとに内部DBから読んだ値で判断し、画面から送られた値やトークンの中身を信用しない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java, backend/src/test/java/cherry/mastersmith/access/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.6 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.6 | 401／403 の応答は、U1 の共通の組み立ての仕組みで、ほかのエラー応答と同じ形（type・code・traceId）で返し、内部の情報を載せない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java（応答に内部の情報が無い）, backend/src/test/java/cherry/master ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.7 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.7 | アクセス拒否の出来事に、アクセストークンの値・パスワードなどの秘密情報を載せない。接続元IP は、U1 の決まり 5.10 と同じく転送元のヘッダーを既定で信頼しない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AccessSecretLeakIT.java, backend/src/test/java/cherry/mastersmith/ac ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR3.8 | `construction/u3-access-control/nfr-requirements/security-requirements.md` NFR3.8 | /api/ の外で応答するのは、画面の配信と /actuator/health だけとする | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/ApiDefaultAccessIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR7.1 | `construction/u3-access-control/nfr-requirements/reliability-requirements.md` NFR7.1 | 「管理」、管理者向け領域の見出しと説明の文言を日本語・英語でそろえる | 満たす（対応するテスト・設定が緑） | frontend/src/features/admin/registration.test.ts, frontend/src/features/admin/AdminPlaceholder.test.tsx, frontend/src/fe ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR8.1 | `construction/u3-access-control/nfr-requirements/reliability-requirements.md` NFR8.1 | 管理者向け領域の部品ごとにアクセシビリティ検査（vitest-axe）を1件入れ、違反が無いこと。確認中であることを支援技術に伝える | 満たす（対応するテスト・設定が緑） | frontend/src/features/admin/AdminPlaceholder.test.tsx, frontend/src/features/admin/AdminAreaPage.test.tsx（vitest-axe。確認中 ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR9.1 | `construction/u3-access-control/nfr-requirements/reliability-requirements.md` NFR9.1 | 管理者向け領域は、確認用 API が成功するまで中身を表示しない。通信の失敗などのエラーでは、一般的なエラーの文言を表示し、画面全体を止めない | 満たす（対応するテスト・設定が緑） | frontend/src/features/admin/AdminAreaPage.test.tsx（確認中・204・403・5xx・通信の失敗） ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR9.2 | `construction/u3-access-control/nfr-requirements/reliability-requirements.md` NFR9.2 | チームの進め方で必須とされた認可のテスト（未認証 401、管理者フラグなし 403、管理者 200 をサーバー側で確かめる）と、パスの照合の境界（NFR3.3）のテストを入れる | 満たす（対応するテスト・設定が緑） | 必須の認可のテストと境界のテスト: backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java, backend/src/test/java/cherry/m ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR10.1 | `construction/u3-access-control/nfr-requirements/reliability-requirements.md` NFR10.1 | 出来事の受け取り側（監査ログ）で失敗が起きても、401／403 の応答は変わらない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AccessDeniedEventsIT.java, backend/src/test/java/cherry/mastersmith/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR10.2 | `construction/u3-access-control/nfr-requirements/observability-requirements.md` NFR10.2 | 管理者のみの API での 403 と、有効期限切れ以外の 401 を、アクセス拒否の出来事（監査ログの記録項目にそろえる）として知らせる。有効期限切れの 401 は知らせない | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AccessDeniedEventsIT.java ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR10.3 | `construction/u3-access-control/nfr-requirements/observability-requirements.md` NFR10.3 | 403 を返したときは、アプリのログに WARN で、code とトレースIDを出す（利用者の特定は監査ログで行い、アプリのログにはメールアドレスを出さない） | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AccessSecretLeakIT.java, backend/src/test/java/cherry/mastersmith/ac ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR10.4 | `construction/u3-access-control/nfr-requirements/observability-requirements.md` NFR10.4 | 処理中のログには、U1 の仕組みでトレースIDが付く | 満たす（対応するテスト・設定が緑） | backend/src/test/java/cherry/mastersmith/access/web/AdminPathBoundaryIT.java（応答と WARN の同じトレースID）, backend/src/test/java/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U3-NFR10.5 | `construction/u3-access-control/nfr-requirements/observability-requirements.md` NFR10.5 | 管理画面へのアクセス拒否の件数（監査ログの ACCESS_DENIED の件数）を、運用で見る指標の候補とする。本Intentでは指標を外部に公開しない | 未測定（負荷の環境／配備先が要る） | 拒否の件数は U4 の監査ログで見る。目標と警報は配備先が決まったときに Operation の段で定める | observability-setup | Unverified |

#### U4（u4-audit-log）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U4-NFR1.1 | `construction/u4-audit-log/nfr-requirements/performance-requirements.md` NFR1.1 | 監査イベント1件の書き込み（別のトランザクションの開始から確定まで）は、同時に 10 件の要求が来る状態で、95% が 50 ミリ秒以内とする | 未測定（負荷の環境／配備先が要る） | Performance Validation の段で測る（1件の書き込みが同時 10 件で 95% が 50 ミリ秒以内） | performance-validation | Unverified |
| U4-NFR1.2 | `construction/u4-audit-log/nfr-requirements/performance-requirements.md` NFR1.2 | 監査イベントの書き込みは、ログインとトークンの更新の API の目標（95% が1秒以内。U2 の NFR1.1・1.2）の内側に収まる。あわせて、アクセス拒否の経路（フィルターの段階でトランザクションの外から、応答の前 | 未測定（負荷の環境／配備先が要る） | Performance Validation の段で測る（呼び出し元の予算への影響、コネクションプールの待ち） | performance-validation | Unverified |
| U4-NFR1.3 | `construction/u4-audit-log/nfr-requirements/performance-requirements.md` NFR1.3 | 監査イベントの書き込みは、1件につき内部DBへの追記1回だけとし、ほかの表の読み取りを伴わない | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/repository/AuditEventRepository.java / backend/src/test/java/cherry/maste ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR1.4 | `construction/u4-audit-log/nfr-requirements/scalability-requirements.md` NFR1.4 | 監査イベントの件数の見積もりは U1 の NFR1.9 のとおり（1日あたり約 500 件、1年で約 180MB）とし、無期限に保存しても当面の配備先（開発者の PC 上のコンテナのボリューム）で扱える | 未測定（負荷の環境／配備先が要る） | 記録の量（1年 約 180〜250MB の見積もり）は運用の中で確かめる（monitoring-design.md 5章） | observability-setup | Unverified |
| U4-NFR1.5 | `construction/u4-audit-log/nfr-requirements/scalability-requirements.md` NFR1.5 | 後続Intentで監査ログを見る画面を作るときに備え、発生の日時で絞り込めるようにする（日時に索引を付ける） | 満たす（対応するテスト・設定が緑） | backend/src/main/resources/db/migration/V4__u4_audit_event.sql（ix_audit_events_occurred_at） / backend/src/test/java/cher ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR1.6 | `construction/u4-audit-log/nfr-requirements/scalability-requirements.md` NFR1.6 | 保存期間と古い記録の扱い（要件定義の未解決の論点 OQ1）は後続Intentで決める。それまでは削除しない | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/repository/AuditEventRepository.java（削除の操作を持たない） / backend/src/test/java/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR3.1 | `construction/u4-audit-log/nfr-requirements/security-requirements.md` NFR3.1 | 監査イベントに、パスワード・トークン・ハッシュ値を含めない。書き込みに失敗したときにアプリのログへ出す内容にも含めない | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java, backend/src/main/java/cherry/mastersmith/audit/se ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR3.2 | `construction/u4-audit-log/nfr-requirements/security-requirements.md` NFR3.2 | アプリは監査イベントを変更・削除する処理と API を持たない。DB アクセスの部品も追記と読み取りだけを持つ | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/repository/AuditEventRepository.java, backend/src/main/java/cherry/master ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR3.3 | `construction/u4-audit-log/nfr-requirements/security-requirements.md` NFR3.3 | 監査ログの改ざんへの備えは、NFR3.2 と、内部DBのファイルを OS の権限で守ること（コンテナのボリュームの権限をアプリの実行者だけに限る）とする。ハッシュの連鎖などの改ざんの検知は行わない | 未測定（負荷の環境／配備先が要る） | ボリュームの権限は U1 の配備の設計。配備の手順で確かめる（README の「監査ログの確かめ方」「消してはいけない操作」） | deployment-execution | Unverified |
| U4-NFR3.4 | `construction/u4-audit-log/nfr-requirements/security-requirements.md` NFR3.4 | 攻撃者が値を決められる項目（入力されたメールアドレス、User-Agent）は、決めた長さ（254・512 文字、文字を分断しない）で切り詰めて保存し、アプリのログには JSON の値として出す（改行などで記録を偽装させ | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/domain/AuditText.java / backend/src/test/java/cherry/mastersmith/audit/do ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR3.5 | `construction/u4-audit-log/nfr-requirements/security-requirements.md` NFR3.5 | 監査ログを見る画面・API は本Intentでは作らない。後続Intentで作るときは、管理者のみ（U3 の /api/admin/ の下）とし、表示の際に値を無害化する | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/ に web の層を作らない / backend/src/test/java/cherry/mastersmith/audit/AuditBoun ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR9.1 | `construction/u4-audit-log/nfr-requirements/reliability-requirements.md` NFR9.1 | チームの進め方で必須とされた監査ログのテスト（対象のイベントごとに必須の項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること、書き込みに失敗しても操作が失敗しないこと）を入れる | 満たす（対応するテスト・設定が緑） | team.md の必須の監査ログのテストを backend/src/test/java/cherry/mastersmith/audit/service/AuditAuthenticationEventsIT.java, backend/s ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR9.2 | `construction/u4-audit-log/nfr-requirements/reliability-requirements.md` NFR9.2 | 監査イベントの記録（内部DB）とアプリのログを別の仕組みとし、監査イベントをアプリのログで代用しない | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java / backend/src/test/java/cherry/mastersmit ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR10.1 | `construction/u4-audit-log/nfr-requirements/reliability-requirements.md` NFR10.1 | 監査イベントの書き込みに失敗しても、元の操作（ログイン・ログアウト・401／403）の応答は変わらない。失敗の例外を出来事を知らせた側へ伝えない | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java / backend/src/test/java/cherry/mastersmit ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR10.2 | `construction/u4-audit-log/nfr-requirements/reliability-requirements.md` NFR10.2 | 元の操作の内部DBの更新が確定した後に、別のトランザクションで記録する。元の操作が取り消された場合は記録しない | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java / backend/src/test/java/cherry/mastersmit ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR10.3 | `construction/u4-audit-log/nfr-requirements/observability-requirements.md` NFR10.3 | 監査イベントの書き込みに失敗したときは、アプリのログに ERROR で1回、失敗したことと記録しようとした全項目（メールアドレスを含む）をキーと値で出す | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java（9つのキーと値＋exceptionType、メールアドレスを含む） / backe ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR10.4 | `construction/u4-audit-log/nfr-requirements/observability-requirements.md` NFR10.4 | 監査イベントのトレースIDは、同じ要求のアプリのログのトレースIDと一致する | 満たす（対応するテスト・設定が緑） | backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java / backend/src/test/java/cherry/mastersmith/ ／ `test-results.md` の実行の記録 | build-and-test | Met |
| U4-NFR10.5 | `construction/u4-audit-log/nfr-requirements/observability-requirements.md` NFR10.5 | 監査イベントの書き込みの失敗の件数（アプリのログの該当する ERROR の件数）を、運用で見る指標の候補とする。本Intentでは指標を外部に公開しない | 未測定（負荷の環境／配備先が要る） | 運用で見る指標と警報は、配備先が決まったときに Operation の段で定める。当面は ERROR と遅れの WARN をログで見る（README の「監査ログ（U4）」） | observability-setup | Unverified |



### 対象としない（要件で受け入れた危険）

| ID | 内容 | 判断 |
|---|---|---|
| U2-NFR4.4 | 他人のメールアドレスで5回失敗させて、その利用者をロックさせる妨害（ロックの悪用）と、大量のログイン試行への追加の対策（接続元ごとの回数制限 | 受け入れた危険（ロックの悪用と重いハッシュの計算。security-design 7章） |
| U2-NFR5.6 | 盗まれたリフレッシュトークンの再利用を検知しても、その利用者の他のトークンは無効にしない | 受け入れた危険（使い回しを検知してもほかのトークンは無効にしない） |
| U4-NFR3.6 | 内部DBのファイルに直接触れられる者（OS の権限を持つ者）による監査イベントの書き換え・削除は、検知できない | 受け入れる危険（OS の権限を持つ者によるファイルの直接の書き換えは検知できない）。README の「消してはいけない操作」に記載 |
| U4-NFR3.7 | 元の操作の確定後、監査イベントを書く前にアプリが止まった場合、その1件は失われる | 受け入れる危険（確定の後、書き込みの前の停止で1件が失われる）。code-summary.md 7章と README に記載 |

### 4.3 判定のまとめ

| 判定 | 件数 | 内訳 |
|---|---|---|
| **Met** | **125** | ステージ全体 10 件 ＋ 単位ごと 115 件 |
| **Not Met** | **0** | — |
| **Unverified** | **17** | 負荷をかけた測定が要るもの 11 件（持ち主 `performance-validation`）、運用の指標・見積もり 5 件（持ち主 `observability-setup`）、配備の権限 1 件（持ち主 `deployment-execution`） |
| 対象としない | 4 | 要件で明示的に受け入れた危険（U2-NFR4.4、U2-NFR5.6、U4-NFR3.6、U4-NFR3.7） |

`Unverified` の 17 件はいずれも、実行の計画に **EXECUTE** として入っている後の段が持つ（`aidlc-state.md` の OPERATION PHASE: `performance-validation` EXECUTE、`observability-setup` EXECUTE、`deployment-execution` EXECUTE）。証拠の置き場所は、それぞれの段の記録のディレクトリとする。

`Unverified` が残るため、ステージ定義の失敗の判定では本ステージは「成功」とはならない。人の判断のため、`test-results.md` の「止まった点と選べる手」に整理した。

## 5. 準備の度合い

| 区分 | 状態 | 根拠 |
|---|---|---|
| ビルドできる（build-ready） | **はい** | `./gradlew verify` が通り、画面を同梱した実行可能 WAR ができる |
| テストできる（test-ready） | **はい** | 単体 554 件（BE 387・FE 167）、結合 239 件、E2E 5 件がコマンド1つずつで再現でき、すべて緑。コンテナの実行環境は不要 |
| 配備できる（deployment-ready） | **一部** | 開発者の PC 上のコンテナでの起動・確認までは可能。クラウドなどの配備先が未決のため、本番への配備の可否は判断できない（team.md の Deployment） |

## 6. 残っている事柄・限界

1. **性能の目標 11 件が未測定**。負荷をかける道具が未決で、`performance-validation` の段で決めて測る。特に U4-NFR1.2（組み込みの H2 の書き込みの待ち合い）は、最も余裕の少ない経路として名指しで測る必要がある。
2. **運用で見る指標 5 件が未確定**。配備先が決まったときに `observability-setup` で目標（SLO）と警報のしきい値を定める。
3. **U1 の `unit-test-instructions.md` 2.4 節のコマンドが、そのままでは失敗する**。単位に絞った実行に全体のカバレッジの判定を含めているため。U2〜U4 の同じ節は意図的に外している。詳しくは `test-results.md` の「実行時に見つかった手順の不備」。
4. **`SecurityHeadersIT` の一過性の失敗**（U4 の `code-summary.md` 6章）は、本ステージの2回の完全な実行では再現しなかった（8 件すべて成功）。監視を続ける。
5. **パッケージ単位で行カバレッジが 80% を下回る箇所が2つ**（`common/health` 79.2%、`audit/service` 77.2%）。全体の下限は満たしているが、テストを足す余地として記録する。
6. **SBOM（部品表）は未実装**。配備先が決まってから入れる（team.md の Deployment）。
7. **CI（GitHub Actions）での同じ検査の実行**は、次の `ci-pipeline` の段が受け持つ。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/aidlc-state.md`（Test Strategy、実行の計画）
- 各単位の `construction/<unit>/nfr-requirements/*.md`（測れる目標の出どころ）
- 各単位の `construction/<unit>/nfr-design/*.md`（設計上の裏づけ）
- 各単位の `construction/<unit>/code-generation/code-generation-plan.md` の `## Testing Contract`、`unit-test-instructions.md`、`code-summary.md`、`traceability.json`
- 本ステージの実行の記録（`test-results.md`）、`backend/build/reports/`、`frontend/coverage/`、`build/reports/osv-scanner/`
- `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`

## Assumptions & Open Questions

- 負荷をかける道具（k6／Gatling／`hey` など）は未決。`performance-validation` の段で決める。
- 本番の稼働率の目標（SLO）と警報のしきい値は、配備先が決まってから `observability-setup` で定める。
