# セキュリティテストの手順（security-test-instructions）

Intent `260923-dsl-schema-loader` の安全に関わる確かめ。道具による自動の検査（`./gradlew verify` の 8 の段と pre-commit）と、振る舞いを確かめるテスト（`verify` の中の単体・結合・画面のテスト）の2つからなる。テストのクラスの名前は `backend/src/test/java/` と `frontend/src/` に実在するものを確かめて書いた。

この Intent で新しく入った危険の面は3つある: (1) 利用者が投入する DSL（YAML）という信頼できない入力、(2) 対象DB の接続情報という秘密情報、(3) 管理者だけの DSL の管理の API（10 本）。

## 1. 道具による自動の検査

```bash
# 8 の段だけ（SpotBugs＋FindSecBugs・OSV-Scanner・Gitleaks）
./gradlew verifySecurity
# 統合の前の関門（8 の段を含む全検査）
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
# コミットの前の検査（Gitleaks とフォーマット）
pre-commit run --all-files
```

| 検査 | 道具とタスク | 統合を止める基準 | 実測（2026-09-24、Loop-back 1 の後の `verify`（`8961cb2`）と `osvScan --rerun`） |
|---|---|---|---|
| 秘密情報の検出 | Gitleaks（`gitleaksScan`、履歴全体）。pre-commit でも実行 | 検出 1件以上 | 143 コミットを走査して 0 件 |
| Java の静的解析 | SpotBugs＋FindSecBugs（`spotbugsGate`、`backend/build/reports/spotbugs/main.xml`） | priority 1 の指摘、パターン名が `SQL_` で始まる指摘（priority によらず） | priority 1 は 0 件、`SQL_` は 0 件。priority 2 が 66 件・priority 3 が 43 件の警告（統合は止めない） |
| 依存関係の脆弱性 | OSV-Scanner（`osvScan`、`build/reports/osv-scanner/osv.json`） | 実行時の依存の High 以上、`MAL-` で始まるもの、`config/npm-build-tools.txt` の道具の High 以上（README の判定の表） | `./gradlew osvScan --rerun` で走査し直した（UP-TO-DATE ではない）。`backend/gradle.lockfile` 244・`frontend/package-lock.json` 395・`vendor/make-you-chic-ui/package-lock.json` 404 パッケージで脆弱性 0 |
| 画面の危険な書き方 | oxlint の `react/no-danger`・`no-eval` 系（`verifyLint`） | error 1件以上 | 0 件 |
| ライセンス | 採用の前の確認（team.md の Code Style）と WAR の中の文書 | Apache License 2.0 と異なるものは採用の理由を記録する | MariaDB Connector/J（LGPL 2.1）は採用の理由を U1 の技術の決定に記録済み。WAR の `WEB-INF/classes/META-INF/third-party-licenses/` に LGPL 2.1 の文書、MySQL Connector/J の jar に `LICENSE`、PostgreSQL JDBC の jar に `META-INF/LICENSE` がある |

- SpotBugs の priority 2・3 の指摘のうち、この Intent の新しいパッケージのものは、`EI_EXPOSE_REP`（`record` の一覧の受け渡し）・`CT_CONSTRUCTOR_THROW`・`SPRING_ENDPOINT`（管理の API の口）・`IMPROPER_UNICODE` などで、注入の系統（`SQL_`）は無い。
- OSV-Scanner は依存が変わらない限り `verify` の中では UP-TO-DATE になる。新しい脆弱性のデータを反映させたいときは `./gradlew osvScan --rerun` を実行する（この段でも実行した）。
- CodeQL などのより広い静的解析、起動したアプリへの動的検査（OWASP ZAP）、コンテナイメージの検査は採用しない（team.md の Code Style）。

## 2. 振る舞いを確かめるテスト（既に生成されているもの）

すべて `./gradlew verify` の中で実行され、Loop-back 1 の後（`8961cb2`）も含めて3回の実行とも成功した（`test-results.md` 2.2）。表の「クラス」は `backend/src/test/java/cherry/mastersmith/` または `frontend/src/` からの相対。

### 2.1 DSL（YAML）の信頼できない入力（team.md の Testing Posture の必須のテスト）

| 必須の確かめ | 値 | クラス |
|---|---|---|
| 大きさ: 上限ちょうどは受け付け、超えると拒否 | 10MB（10,485,760 バイト）ちょうどは受け付け、1 バイト超えは読まずに `SIZE_LIMIT`。API は 413 `DSL_TOO_LARGE`（本文を送らず `Content-Length` だけで断る）、投入の API 以外は 1MB | `dsl/service/DefaultDslReaderTest`、`dslmanage/web/DslAdminApiIT` |
| 入れ子の深さ: 上限ちょうどは受け付け、超えると拒否 | 深さ 50 は受け付け、51 は `DEPTH_LIMIT` | `dsl/parse/SafeYamlParserTest` |
| 別名（アンカー）: 数の上限と展開の爆発 | コレクションを指す別名 100 は受け付け、101 は `ALIAS_LIMIT`。展開後の節が 1,000,000 を超えたら打ち切り、爆発する DSL を 1 秒以内に `ALIAS_LIMIT` | `dsl/parse/SafeYamlParserTest` |
| タグ: 任意の型を作るタグを拒否 | `!!` の型の指定と独自のタグはすべて `FORBIDDEN_TAG`、6 種類のタグで記録用のクラスが作られない（0 件） | `dsl/parse/SafeYamlParserTest` |
| 重複キー: エラーにし、上書きしない | 2 回目の位置つきで `DUPLICATE_KEY`（同じテーブル・カラムの二重の定義も `DUPLICATE_KEY`） | `dsl/parse/SafeYamlParserTest` |
| JSON Schema の `$ref`: 外部を取りに行かない | テストの中の HTTP の受け口（`dsl/testsupport/CountingHttpServer`）への要求が 0 件。DSL の中の `$ref` は構文の誤り | `dsl/validate/DslSchemaValidatorTest` |
| 拒否の応答: Problem Details で、部品の例外の文言を含まない | 部品の例外の文言が誤りの一覧に現れない（性質ベース）、誤りに埋める値は先頭 100 文字まで、応答は Problem Details と `code` | `dsl/parse/SafeYamlParserPropertyTest`（jqwik）、`dsl/validate/DslSchemaValidatorTest`、`dslmanage/web/DslAdminApiIT` |
| 重い正規表現 | `pattern` は 1,000 文字まで、組み立ては 100 ミリ秒で打ち切り、値に当てはめない | `dsl/validate/PatternCheckerTest`（実時間は性能の U2-PATTERN-COMPILE で確かめた） |
| 安全な読み込みだけを使う | `dsl` から Jackson の YAML の読み込みを使わない（`jackson-dataformat-yaml` を依存から外した） | `dsl/DslBoundaryArchitectureTest` |
| サーバー側の検証が正 | 画面を通さず API を直接呼んでも本文をすべてサーバー側で検証 | `dslmanage/web/DslAdminApiIT` |

### 2.2 対象DB の接続情報を出さない・受け取らない（project.md の Forbidden）

| 確かめ | クラス |
|---|---|
| 接続情報は `mastersmith.target-db.*` の設定だけから受け取り、`web` 層から設定の型を使わない | `targetdb/TargetDbBoundaryArchitectureTest`（ArchUnit） |
| パスワードは設定の型の文字列化と TRACE を含むログで伏せ字 | `targetdb/config/TargetDbSecretLeakIT` |
| 設定の欠け・不正は項目名だけを WARN 1件、値は出さない。設定が無くても起動する | `targetdb/config/TargetDbStartupIT` |
| 資格情報を含む JDBC の URL を組み立てない。URL で意味を持つ記号を含むホスト・DB 名は「不正」 | `targetdb/config/TargetDataSourceConfigTest` |
| 接続・問い合わせの失敗の結果とログに JDBC の例外の文言・接続先・ユーザー名を含めない（3つのドライバーのログは OFF） | `targetdb/service/MysqlTargetSchemaReaderIT`・`MariadbTargetSchemaReaderIT`・`PostgresTargetSchemaReaderIT`（親 `AbstractTargetSchemaReaderIT`） |
| 生成した DSL に接続先・ユーザー名・パスワード・スキーマ名が無い | `dslmanage/generate/DefaultDslGeneratorMysqlIT`・`DefaultDslGeneratorMariadbIT`・`DefaultDslGeneratorPostgresIT`、`dslmanage/generate/TargetSchemaDslGeneratorTest` |
| 応答・ダウンロード・ログ・監査に接続情報が無い | `dslmanage/web/DslTargetDbIT` |

### 2.3 注入（SQL・YAML の書き出し）

| 確かめ | クラス |
|---|---|
| 任意の文字列の識別子が引用符の外に出ない | `targetdb/domain/SqlIdentifiersTest`（jqwik） |
| 引用符・空白・セミコロンを含む名前でも読み取りが成功し、ほかのテーブルとデータは変わらない。接続は読み取り専用 | `targetdb/repository/MysqlSchemaQueriesIT`・`MariadbSchemaQueriesIT`・`PostgresSchemaQueriesIT`、`targetdb/service/*TargetSchemaReaderIT` |
| 値（スキーマ名）はプレースホルダーで渡し、文は固定の文字列 | コードの点検と SpotBugs の `SQL_` の関門（1節） |
| 任意の文字列の名前とコメントで生成した DSL が検証を通り、読み直した値が元と同じ（YAML の書き出しで構造を壊さない） | `dslmanage/generate/DslGenerationPropertyTest`（jqwik） |
| コメントの制御文字を取り除く | `dslmanage/generate/DslYamlWriterTest`・`DslTreeBuilderTest` |

### 2.4 認可（team.md の必須のテスト）

| 確かめ | クラス |
|---|---|
| DSL の管理の 10 本の API それぞれで、未認証 401・管理者でない 403（監査の行が1件増える）・管理者の結果。401・403 で状態が変わらない（31 件） | `dslmanage/web/DslAccessControlIT` |
| 代表の API での 401・403 と、ログインしていない大きな要求は 413 より先に 401 | `dslmanage/web/DslAdminApiIT` |
| 画面で管理メニューを隠すことはサーバー側の検査の代わりにしない（既存の仕組み） | `access/web/AdminAccessIT`・`access/web/ApiDefaultAccessIT` |
| JSON Schema の静的なファイルはログインなしで取れる（公開してよいもの）。`SecurityConfig` は変えていない | `dsl/DslSchemaPublicationIT` |

### 2.5 監査・ログに秘密を出さない

| 確かめ | クラス |
|---|---|
| 監査ログの全列（V6 の4列を足した）にパスワード・トークンの値が無い | `audit/service/AuditSecretLeakIT` |
| 操作ごとに INFO 1件（キーと値）、本文・接続先・パスワード・部品の文言を出さない | `dslmanage/web/DslAdminApiIT` |
| 監査の書き込みに失敗しても操作は成功し、アプリのログに1件（本文・接続先なし） | `dslmanage/web/DslAuditWriteFailureIT` |
| 既存の漏えいの確かめ（ログイン・トークン・アクセス拒否）は引き続き成功 | `auth/web/AuthSecretLeakIT`・`access/web/AccessSecretLeakIT` |

### 2.6 エラー応答（Problem Details）

| 確かめ | クラス |
|---|---|
| エラー応答は Problem Details と安定した `code`、部品の文言・スタックトレースを載せない。誤りの一覧は表示言語の文言 | `dslmanage/web/DslAdminApiIT`、`common/error/web/ErrorResponseIT` |
| 想定内の失敗（413・409・422・503）は WARN 以下でスタックトレースなし、500 は ERROR でスタックトレース付き、境界で1回 | `common/error/web/BusinessExceptionPropertiesTest` |
| `DSL_BUSY` を 503 の1つの状態コードで登録し、起動時の重複の検査を通る | `dslmanage/web/DslConcurrencyIT`、`common/error/service/ProblemTypeDuplicateStartupIT` |
| ダウンロードは `Content-Disposition: attachment`・`application/yaml`・`nosniff` | `dslmanage/web/DslAdminApiIT` |

### 2.7 画面（U5）

| 確かめ | クラス |
|---|---|
| DSL・対象DB・サーバーから来る文字列は文字として描く（`<script>` を含む表示名が文字のまま出る） | `features/dsl/DslPreviewPanel.test.tsx`、1節の `react/no-danger` |
| アクセストークンは ApiClient だけが扱う。ダウンロードの一時的な URL はすぐに捨てる | `features/dsl/api/saveFile.test.ts`、`shared/api-client/apiClient.download.test.ts` |
| JSON Schema のリンクは同じオリジンの `/dsl/dsl-schema-v1.json` だけ | `features/dsl/DslSubmitForm.test.tsx` |
| 失敗の文言は `code` から選び、`detail`・内部の文言・接続先を出さない | `features/dsl/DslAdminPage.test.tsx`、`shared/api-client/apiError.problem.test.ts` |
| 画面の部品ごとの vitest-axe の検査で違反 0 件（安全と隣り合う品質） | `features/dsl/` の各 `*.test.tsx` |

## 3. 確かめていないこと・受け入れる危険

| 事柄 | 扱い |
|---|---|
| 問い合わせの段で止まる本物の対象DB の遅延 | 接続の段で応答しない相手（`SilentServer`）だけで確かめた。決定 B により、照合の全体の上限は作らず最悪 23〜28 秒を許す |
| コンテナの実行環境が無いときの動き（NFR12.3） | Loop-back 1 で直した（Met）。直す前は4クラスが失敗になっていた。今は警告を出して 10 クラスを SKIPPED にし、構造の検査 `ExtensionOrderArchitectureTest` が再発を防ぐ（`test-results.md` 2.8・3.1） |
| 既存の `config/ExposureIT` 2件・`config/SecurityHeadersIT` 1件 | 本文の大きさの上限を認証・認可の後に移した影響を受けるが、Infrastructure Design の決定 C により直していない（テスト用の決まり `PublicApiTestRules` で扱う） |
| アクセシビリティの手での確認（キーボード・Tab の順・フォーカス・拡大・読み上げ） | 依頼者が後で行う（Q6: B、Unverified） |

## 4. 後の段で確かめること

- CI（GitHub Actions）で同じ 8 の段が通ること（ci-pipeline）。
- 監視の画面で `dsl.operation` のログを絞り込めること、ログに秘密が出ないこと（observability-setup）。
- 配備の後に、対象DB の接続情報を `.env` だけで渡して起動・生成できること（deployment-execution）。

## Sources

- `backend/src/test/java/cherry/mastersmith/`（`targetdb`・`dsl`・`dslmanage`・`audit`・`auth`・`access`・`common/error`・`config` のテストのクラス。名前を確かめた）、`frontend/src/features/dsl/`・`frontend/src/shared/api-client/` のテスト
- `README.md`（1コマンドの検査、依存関係の脆弱性の判定、対象DB の結合テスト）
- `build/reports/osv-scanner/osv.json`・`backend/build/reports/spotbugs/`（実測）、`verify` の2回のログ（`test-results.md` 1節）
- `aidlc/spaces/default/memory/team.md`（Testing Posture の必須のテスト、Code Style の静的解析とセキュリティ検査）・`aidlc/spaces/default/memory/project.md`（Forbidden、Mandated）
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/cross-unit-traceability.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/build-and-test/security-test-instructions.md`（書き方の見本）

## Assumptions & Open Questions

None.
