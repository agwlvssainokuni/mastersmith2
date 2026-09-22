# NFR Requirements — Questions（U1 アプリの骨格 / u1-app-skeleton）

U1 の非機能要件と、技術の選定で決まっていない点を確認します。U1 はアプリの土台（起動・内部DB・ログ・トレース・共通のエラー応答・画面の骨組み・ビルド・検査・CI）を受け持つため、アプリ全体で使う技術の多くをここで決めます。

決定済みの事項（再確認はしません）: バックエンドは Java・Spring Boot・Gradle（Kotlin DSL）、フロントエンドは React・TypeScript・make-you-chic-ui（Vite）、内部DBは H2（既定は組み込み・ファイル保存）、ログは SLF4J/logback で JSON、トレースは Micrometer Tracing（W3C Trace Context）、外部エクスポートは Actuator 経由の OTEL（既定は無効）、成果物は実行可能 WAR、CI は GitHub Actions、テストは JUnit・jqwik・Testcontainers・Vitest・Testing Library・fast-check、カバレッジは JaCoCo・`@vitest/coverage-v8`、フォーマッタ・リンタ・静的解析・秘密情報の検出はチームの進め方のとおり。性能の目標は NFR1（ログインと更新の API で p95 1秒以内、利用者50名・同時ログイン10名）。

### Q1. Java・Spring Boot・Node.js の版は、どれにしますか？

- A. 最新の長期サポート版でそろえる: Java 25、Spring Boot 4 系、Node.js 24（npm）
- B. 一つ前の長期サポート版でそろえる: Java 21、Spring Boot 3.5 系、Node.js 22（npm）
- X. Other (please specify)

[Answer]: A. 最新の長期サポート版でそろえる: Java 25、Spring Boot 4 系、Node.js 24（npm）

### Q2. 内部DBのスキーマの変更（前進のみ）には、どの道具を使いますか？

- A. Flyway（SQL のファイルを番号順に当てる）
- B. Liquibase（変更を XML・YAML などで書く）
- X. Other (please specify)

[Answer]: A. Flyway（SQL のファイルを番号順に当てる）

### Q3. JSON のログは、どの仕組みで出しますか？

- A. Spring Boot に組み込みの構造化ログ（Elastic Common Schema などの決まった形式）を使う
- B. logstash-logback-encoder を加えて使う（項目の並びや名前を細かく決められる）
- X. Other (please specify)

[Answer]: B. logstash-logback-encoder を加えて使う（項目の並びや名前を細かく決められる）

### Q4. 内部DB（H2）のファイルの既定の置き場所は、どこにしますか？（要件定義の未解決の論点 OQ5）

- A. アプリを起動した場所からの相対パス `./data/`（コンテナでは `/app/data` をボリュームにする）
- B. 利用者のホームの下 `~/.mastersmith/`
- X. Other (please specify)

[Answer]: A. アプリを起動した場所からの相対パス `./data/`（コンテナでは `/app/data` をボリュームにする）

### Q5. 画面からの一連の操作を確かめるテスト（E2E）の道具は、どれにしますか？（要件定義の未解決の論点 OQ5）

- A. Playwright
- B. Cypress
- X. Other (please specify)

[Answer]: A. Playwright

### Q6. 画面の文言の日本語・英語の切り替えには、どの仕組みを使いますか？

- A. i18next と react-i18next を使う
- B. 小さな自前の辞書の仕組みを作る（依存を増やさない）
- X. Other (please specify)

[Answer]: A. i18next と react-i18next を使う

### Q7. HTTPS（TLS）は、いつ決めますか？

リフレッシュトークンの Cookie は Secure のため、当面の動作確認は `http://localhost` で行うことにしています（U2 の決まり 5.2）。

- A. 配備先が決まるまで HTTPS は扱わない。開発・CI・E2E は `http://localhost` で行い、配備先が決まったときに TLS の終端の方法を決める
- B. 今の段階で、開発用にも HTTPS を用意する（手元で TLS を終端する仕組みを入れる）
- X. Other (please specify)

[Answer]: A. 配備先が決まるまで HTTPS は扱わない。開発・CI・E2E は `http://localhost` で行い、配備先が決まったときに TLS の終端の方法を決める

## Consolidated Summary Confirmation

- 版: Java 25、Spring Boot 4 系、Node.js 24（npm）。それぞれ Code Generation の時点の最新の修正版を使い、lockfile などで固定する
- スキーマの変更: Flyway（SQL のファイルを番号順に当てる。前進のみ）
- JSON のログ: logstash-logback-encoder で出す（項目は U1 の Functional Design の決まり 3.2 のとおり）
- H2 のファイルの既定の置き場所: `./data/`（コンテナでは `/app/data` をボリュームにする）
- E2E の道具: Playwright
- 文言の切り替え: i18next と react-i18next
- HTTPS: 配備先が決まるまで扱わない。開発・CI・E2E は `http://localhost` で行う

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## Q8. 内部DBへのアクセスには、どの仕組みを使いますか？

サマリー確認の後、成果物の確認の中で依頼者から「内部DBアクセスはJPAを使おう。」と指示があったため記録した質問。

- A. Spring Data JPA（Hibernate）。スキーマは Flyway が正本とし、Hibernate にはスキーマを作らせない
- B. Spring JDBC（JdbcClient）
- X. Other (please specify)

[Answer]: A. Spring Data JPA（Hibernate）。スキーマは Flyway が正本とし、Hibernate にはスキーマを作らせない

## Q9. メソッドの呼び出しの追跡（TraceAspect）で、TRACE を有効にしたときにパスワードやトークンをログに出さない対策は、どうしますか？

U1 の成果物の完了の後、依頼者から「メソッドの呼び出し・復帰・例外を TRACE でログに出す仕組み（TraceAspect。Spring の CustomizableTraceInterceptor に処理を任せるアスペクト）を組み込みたい」と依頼があったため追加した質問。この仕組みは引数と戻り値をそのまま文字列にして出すため、何もしないとパスワード・トークンがログに出て、project.md の Forbidden に反する。

- A. 秘密情報を持つ型（ログインの要求、トークンの応答など）の文字列化で、その項目を伏せ字にする。伏せ忘れをテストで確かめる
- B. 認証・利用者の機能のパッケージを追跡の対象から外す
- C. A と B の両方
- X. Other (please specify)

[Answer]: A. 秘密情報を持つ型（ログインの要求、トークンの応答など）の文字列化で、その項目を伏せ字にする。伏せ忘れをテストで確かめる
