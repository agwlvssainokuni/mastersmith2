# 技術の構成（mastersmith2）

## 版の出どころ

バックエンドは `gradle/libs.versions.toml` と `backend/gradle.lockfile`、画面は `frontend/package.json` の宣言（範囲の指定。実際の版は `frontend/package-lock.json`、今回は開いていない）、実行環境は `Dockerfile` と `.github/workflows/ci.yml` から読んだ。

## 言語と実行環境

| 対象 | 版 | 備考 |
|---|---|---|
| Java | 25 | toolchain。コンテナは `eclipse-temurin:25.0.4_7-jre-noble`、CI は temurin 25 |
| Node.js | 24 | CI（`ci.yml`）。画面のビルドと検査 |
| TypeScript | ^6.0.3 | `strict` 系 |
| Gradle | Wrapper、Kotlin DSL | lockfile で固定、取得元は Maven Central だけ |

## バックエンド

| 部品 | 版 | 用途 |
|---|---|---|
| Spring Boot | 4.1.1 | webmvc・security・oauth2-resource-server・actuator・data-jpa・flyway・validation・aspectj・opentelemetry |
| Spring Framework | 7.0.9 | lockfile |
| Spring Security | 7.1.1 | 認証と認可（JWT のリソースサーバーの仕組みでトークンを認証） |
| Hibernate ORM | 7.4.5.Final | JPA（`ddl-auto: validate`） |
| Flyway | 12.4.0 | 内部DB のスキーマ変更（V1〜V6、前進のみ） |
| H2 | 2.4.240 | 内部DB（組み込み・ファイル） |
| HikariCP | 7.0.2 | 内部DB の接続プール（上限 既定 30） |
| Nimbus JOSE JWT | 10.9.1 | JWT（HS256）の発行と検証 |
| Tomcat（組み込み） | 11.0.26 | Boot の管理の版から脆弱性の回避で引き上げ（`libs.versions.toml`） |
| Jackson | 3.1.5（`tools.jackson`） | JSON |
| logstash-logback-encoder | 9.0 | 1行1件の JSON のログ |
| opentelemetry-logback-appender | 2.28.1-alpha | ログの外部エクスポート（既定で無効）。版は固定（`project.md` の Tech Stack） |
| SnakeYAML | 2.6 | DSL の安全な読み込み |
| networknt json-schema-validator | 3.0.6 | DSL の JSON Schema の検証 |
| MySQL・MariaDB・PostgreSQL の JDBC ドライバー | Boot の管理 | 対象DB の読み取り |

### テストと品質の道具

| 部品 | 版 | 用途 |
|---|---|---|
| JUnit Jupiter | 6.0.3（lockfile） | 単体と結合のテスト |
| Spring Boot Test・Spring Security Test | Boot の管理 | 結合テスト |
| jqwik | 1.10.1 | 性質ベースのテスト |
| ArchUnit | 1.5.0 | 層と機能の境界 |
| Testcontainers | 2.0.5（lockfile） | 対象DB の結合テスト |
| Spotless ＋ palantir-java-format | 8.10.2 ＋ 2.98.0 | フォーマットとライセンスヘッダー |
| SpotBugs ＋ FindSecBugs | 4.10.4 ＋ 1.14.0 | 静的解析 |
| JaCoCo | 0.8.15 | カバレッジ（全体とパッケージごと） |

## 画面

| 部品 | 版（宣言） | 用途 |
|---|---|---|
| React・react-dom | ^19.2.8 | 画面 |
| react-router | ^8.3.0 | URL の振り分け |
| i18next・react-i18next | ^26.4.2・^17.0.15 | 日英の文言 |
| @fontsource/noto-sans-jp | ^5.3.0 | フォント（自己ホスティング。CSP は `font-src 'self'`） |
| make-you-chic-ui | `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`（サブモジュール、固定先 `edb1f943c0e66293494fa974605f34fcd7e258d7`） | デザインシステム |
| Vite・@vitejs/plugin-react | ^8.2.1・^6.0.5 | ビルド |
| Vitest・@vitest/coverage-v8 | ^4.1.11 | テストとカバレッジ |
| Testing Library・user-event・jsdom・vitest-axe・fast-check | ^16.3.3・^14.6.7・^30.1.1・^0.1.0・^4.10.2 | 画面のテスト・アクセシビリティ・性質ベース |
| Playwright | ^1.63.0 | E2E（`verify` と CI の外） |
| Prettier・oxlint・ESLint・Stylelint | ^3.9.6・^1.78.0・^10.8.1・^17.14.1 | フォーマッタとリンタ |

## 実行環境・検査・CI

| 道具 | 用途 |
|---|---|
| Docker（colima）・Compose | 開発者の PC でのコンテナ |
| Gitleaks・OSV-Scanner | 秘密情報の検出・依存関係の脆弱性（`verify` から呼ぶ。CI では版と SHA-256 を固定して入れる） |
| GitHub Actions・Dependabot・pre-commit | CI、更新の通知、コミット前の検査 |
| k6・grafana/otel-lgtm | 負荷の試験・手元の監視（流し読み） |

## 無いもの（K-3）

確かめた事実:

- **メール送信の仕組みは無い。** `spring-boot-starter-mail`・`jakarta.mail`・SMTP の設定・メールの受け手のコンテナ（mailpit など）は、`libs.versions.toml`・`backend/gradle.lockfile`・`application.yaml`・`compose.yaml`・`.env.example`・README のどこにも無い（`git ls-files` の全体を `mustache`・`jakarta.mail`・`starter-mail`・`smtp`・`mailpit` で検索し、`aidlc/`・`.claude/` の外で該当なし）。
- **Mustache のエンジン（java-mustache-processor を含む）は依存に無い。** lockfile にも `mustache`・`mail` の行は無い。
- 依存の取得元は Maven Central だけに固定されている（`settings.gradle.kts` の `RepositoriesMode.FAIL_ON_PROJECT_REPOS` と `mavenCentral()`）。依存の追加には `resolveAndLockAll --write-locks` による lockfile の書き直しが要る。

帰結（仮説と決まり）:

- java-mustache-processor を使うには Maven Central に公開されている必要がある。公開されているか・ライセンス・推移依存は確かめていない。
- 新しい依存は、採用の前にライセンスを確かめ、Apache License 2.0 と異なれば ADR に理由を残す（`team.md` の Code Style）。推移依存で既存の部品（Jackson など）の版を引き上げないかを依存の木で確かめる（`project.md` の Corrections）。
- 開発者の PC 上のコンテナだけの配備（`team.md` の Deployment）では、実際の SMTP の送り先が無い。手元の確かめには、メールを受けて見せるだけのコンテナを compose の profile で足す形が考えられる（仮説）。
- HTML のメールの本文は画面の CSP（`application.yaml` の `mastersmith.security.content-security-policy`）の対象外で、メールの利用者の環境で表示される。テンプレートへの値の差し込みは HTML のエスケープが要る（仮説。Mustache の `{{ }}` がエスケープするかはエンジンによる）。
