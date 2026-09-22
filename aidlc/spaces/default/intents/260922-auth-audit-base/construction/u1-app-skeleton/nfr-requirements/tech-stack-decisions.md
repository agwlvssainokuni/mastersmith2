# Tech Stack Decisions — U1 アプリの骨格（u1-app-skeleton）

U1 はアプリの土台を受け持つため、アプリ全体で使う技術をここで決める。U2〜U4 もこの選定に従う。判断の元は、チームの進め方（`aidlc/spaces/default/memory/team.md`）、要件定義書の制約、`nfr-requirements-questions.md` の確定回答（Q1〜Q7）である。版は、Code Generation の時点の最新の修正版を使い、Gradle のバージョンカタログと npm の lockfile で固定する（project.md の Mandated）。

## バックエンド

| 分類 | 選定 | 理由 | 出典 |
|---|---|---|---|
| 言語 | Java 25（LTS） | 最新の長期サポート版。長く使える | Q1 |
| フレームワーク | Spring Boot 4 系 | チームの進め方。Java 25 に対応する最新の系列 | Q1、TP |
| ビルド | Gradle（Kotlin DSL）、Gradle Wrapper | チームの進め方。Java 25 に対応する版を使う | TP |
| 成果物 | 実行可能 WAR（フロントエンドのビルド結果を同梱） | チームの進め方 | TP |
| Web | Spring MVC | 同期の処理で足り、規模（NFR1）にも合う | NFR1 |
| 認証・認可 | Spring Security（U2・U3 が使う） | アクセス制御・トークン検証・セキュリティヘッダーを備える | U2・U3 の Functional Design |
| 内部DB | H2（既定は組み込み・ファイル保存、`./data/`） | 要件（FR1.2）。ファイルの置き場所は Q4 | FR1.2、Q4 |
| DB アクセス | Spring Data JPA（Hibernate） | 依頼者の決定。スキーマは Flyway が正本とし、Hibernate にスキーマを作らせない（検証だけ行う）。要求の処理中に画面の描画まで DB の接続を持ち越す設定（open-in-view）は無効にする。発行する SQL を明確に制御したい箇所（U2 の決まり 2.7 の読み書きの回数をそろえる処理、3.8 の同じ利用者の試みを1つずつ行う処理、5.6 の条件付きの無効化）は、明示的な問い合わせ・更新の問い合わせ・行の排他を使って書き、テストで SQL の回数を確かめる | 依頼者の決定、U2 の決まり 2.7・3.8・5.6 |
| スキーマの変更 | Flyway（SQL のファイル、前進のみ） | Q2。チームの進め方の「前進のみ・後方互換」に合う | Q2、TP |
| コネクションプール | HikariCP（Spring Boot の既定） | 既定で十分 | — |
| ログ | SLF4J＋logback、logstash-logback-encoder で1行1件の JSON | Q3。項目の名前と並びを決められる | FR10.1、Q3 |
| トレース | Micrometer Tracing（OpenTelemetry のブリッジ）、W3C Trace Context | 要件（FR10.3）。トレースIDの取得は U1 functional-spec.md 6.1 | FR10.3 |
| 外部エクスポート | Actuator 経由の OTLP エクスポート（既定は無効） | 要件（FR10.4）の制約 | FR10.4 |
| ヘルスチェック | Actuator の health（`/actuator/health`、DB の確認を含む） | U1 の決まり 1.1〜1.4、U3 の決まり 1.5 | FR1.1 |
| エラー応答 | Spring の ProblemDetail、`@RestControllerAdvice` と共通の組み立ての仕組み | U1 の決まり 5.1 | TP |

## フロントエンド

| 分類 | 選定 | 理由 | 出典 |
|---|---|---|---|
| 実行環境 | Node.js 24（LTS）、npm（`npm ci`） | Q1。lockfile どおりに入れる | Q1、project.md |
| 言語 | TypeScript（strict） | チームの進め方 | TP |
| UI | React、make-you-chic-ui（`vendor/make-you-chic-ui`） | チームの進め方 | project.md Tech Stack |
| ビルド | Vite（`resolve.dedupe` で React の二重読み込みを防ぐ） | make-you-chic-ui の組み込み手順 | TP |
| ルーティング | React Router | 画面の登録（差し込み口）と URL ごとの振り分けに使う | U1 の決まり 7.x |
| 多言語 | i18next、react-i18next | Q6 | FR2.3、Q6 |
| 登録用ファイルの自動読み込み | Vite の `import.meta.glob` | 後の単位が U1 のファイルを変えずに登録できる（U1 の決まり 7.1） | U1 Q5 |

## テスト・品質

| 分類 | 選定 | 出典 |
|---|---|---|
| バックエンドのテスト | JUnit 5、AssertJ、Spring Boot Test、jqwik（性質ベース） | TP |
| 内部DBを使うテスト | 本番と同じ組み込みの H2 を使う（テストごとにデータを用意して巻き戻す）。Testcontainers は本Intentでは使わず、後続Intent（D・E）で対象DBを扱うときに使う | 要件定義の制約（RQ1、RQ16）、レビュー指摘 R-01 |
| フロントエンドのテスト | Vitest、Testing Library、user-event、vitest-axe、fast-check | TP |
| E2E | Playwright（代表の流れ1〜2本、`http://localhost` で実行） | Q5、Q7 |
| カバレッジ | JaCoCo、`@vitest/coverage-v8`（行 80%・分岐 70%） | TP、NFR9 |
| 構造の検査 | ArchUnit | TP |
| フォーマット・リンタ | Spotless（palantir-java-format、licenseHeader）、Prettier、oxlint、ESLint（react-hooks）、Stylelint | TP |
| 静的解析・脆弱性 | SpotBugs＋FindSecBugs、Dependabot、OSV-Scanner、Gitleaks（pre-commit と CI） | TP |

## 実行・配備

| 分類 | 選定 | 出典 |
|---|---|---|
| CI | GitHub Actions | TP |
| 当面の配備先 | 開発者の PC 上のコンテナ。H2 のファイルは `/app/data` をボリュームにする | TP、Q4 |
| HTTPS | 配備先が決まるまで扱わない。開発・CI・E2E は `http://localhost` | Q7 |

## 版の組み合わせの確認

Java 25・Spring Boot 4 系・logstash-logback-encoder・Flyway（H2）は、いずれも最新の版の組み合わせである。最初の単位のビルドで、この組み合わせで起動できること、JSON のログが決めた項目で出ること、Flyway の変更が H2 に当たることを確かめる。合わない部品があった場合は、その部品を一つ前の版にする案と代わりの部品を使う案を示し、依頼者が決める（レビュー指摘 R-03）。

## 検討して採らなかったもの

| 候補 | 採らなかった理由 |
|---|---|
| Java 21・Spring Boot 3.5 | 最新の長期サポート版を選んだ（Q1） |
| Liquibase | SQL のファイルで足りる（Q2） |
| Spring Boot 組み込みの構造化ログ | 項目の名前と並びを細かく決めたい（Q3） |
| Spring JDBC（JdbcClient） | 依頼者の決定で Spring Data JPA を採った |
| Cypress | Q5 |
| 自前の多言語の仕組み | Q6 |
