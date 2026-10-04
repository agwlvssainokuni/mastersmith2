# 技術の構成（mastersmith2）

版は 2026-10-04（コミット `d5aea52`）に、`gradle/libs.versions.toml` の `[versions]`（今回深く読んだ）と `frontend/package.json` の指定（範囲の指定。流し読み）から抜いた値である。lockfile で解決した版は今回確かめていないため書かない。依存の向きと管理の決まりは `dependencies.md`。

## 言語と実行環境

| 分類 | 技術 | 版 | 備考 |
|---|---|---|---|
| バックエンドの言語 | Java（Temurin） | 25（`libs.versions.toml` の `java`） | 実行のイメージは `Dockerfile`（今回は読み直していない） |
| 画面の言語 | TypeScript | 範囲 `^6.0.3` | `strict` 系の設定、`any` の禁止（`team.md` の Code Style） |
| ビルド | Gradle（Kotlin DSL）＋ npm | — | 入口は `./gradlew verify`。E2E は `./gradlew e2eTest` |

## バックエンド

| 分類 | 部品 | 版 | 備考 |
|---|---|---|---|
| 土台 | Spring Boot（webmvc・security・oauth2-resource-server・data-jpa・flyway・validation・aspectj・actuator・opentelemetry・mail） | 4.1.1 | |
| 認証と認可 | Spring Security（Spring Boot の管理の版） | — | アクセストークンは HS256 の JWT（oauth2-resource-server の仕組み）。認可は URL の決まりと `AuthorizationManager`。`GrantedAuthority`・メソッドの認可（`@PreAuthorize`・`@EnableMethodSecurity`）は使っていない（K-32・K-33） |
| 組み込みの Tomcat | tomcat-embed-core | 11.0.26 | 脆弱性のため Spring Boot の管理の版を上書き |
| JSON | Jackson | 3.1.7 | Jackson の BOM（platform）で上書き（`project.md` の Tech Stack） |
| 内部DB ／ 移行 | H2 ／ Flyway | Spring Boot の管理の版 | ファイルの H2。移行は V1〜V9（次は V10） |
| ログ | logstash-logback-encoder ／ opentelemetry-logback-appender | 9.0 ／ 2.28.1-alpha | appender は固定（`project.md` の Tech Stack） |
| DSL | SnakeYAML ／ networknt json-schema-validator | 2.7 ／ 3.0.6 | メニューの木の定義も DSL の JSON Schema で検証する（K-37） |
| メール | cherry-mustache（`vendor/java-mustache-processor`） | 0.1.0 | composite build |

## バックエンドのテストと検査

| 部品 | 版 | 備考 |
|---|---|---|
| JUnit 5 ／ Spring Boot Test ／ spring-security-test | Spring Boot の管理の版 | 認可の 401・403・200 の結合テスト（`AdminAccessIT` ほか） |
| ArchUnit | 1.5.1 | 層と機能の境界の検査（`access` の境界テストは無い。K-38） |
| jqwik | 1.10.1 | 性質ベースのテスト |
| Testcontainers | Spring Boot の管理の版 | 対象DB の結合テストだけ |
| SubEthaSMTP | 7.2.2 | JVM の中のテスト用の SMTP の受け手 |
| JaCoCo | 0.8.15 | 全体とパッケージごとの下限（K-38） |
| Spotless ／ palantir-java-format | 8.10.3 ／ 2.98.0 | |
| SpotBugs（プラグイン）／ SpotBugs ／ FindSecBugs | 6.5.12 ／ 4.10.4 ／ 1.14.0 | 関門 `spotbugsGate` |

## 画面

| 分類 | 部品 | 版（`package.json` の範囲） | 備考 |
|---|---|---|---|
| 土台 | React ／ react-dom ／ react-router | `^19.2.8` ／ `^19.2.8` ／ `^8.3.0` | 振り分けは骨組みの `decideRoute`（K-35） |
| 表示言語 | i18next ／ react-i18next | `^26.4.2` ／ `^17.0.15` | サイドバーの項目は `labelKey` で文言を引く |
| デザインシステム | make-you-chic-ui（`vendor/make-you-chic-ui`、`file:` の依存） | サブモジュールの固定先 `e82b651` | `AppShell` のサイドバーは平ら、アイコンは 18 種類（K-36） |
| ビルド | Vite ／ `@vitejs/plugin-react` | `^8.3.1` ／ `^6.0.5` | |
| テスト | Vitest ／ `@vitest/coverage-v8` ／ Testing Library（react・dom・user-event・jest-dom）／ vitest-axe・axe-core ／ fast-check ／ jsdom | `^5.0.2` ／ `^5.0.2` ／ `^16.3.3` ほか ／ `^0.1.0`・`^4.13.0` ／ `^4.10.2` ／ `^30.1.1` | |
| E2E | `@playwright/test` | `^1.63.0` | `verify` と CI の外 |
| 書式と静的検査 | Prettier ／ oxlint ／ ESLint（react-hooks）／ Stylelint | `^3.9.9` ／ `^1.78.0` ／ `^10.8.1` ／ `^17.14.1` | |

## 検査の外部の道具

| 分類 | 部品 | 備考 |
|---|---|---|
| 秘密情報の検出 | Gitleaks | pre-commit と `verify`（前回までの記録） |
| 依存の脆弱性 | OSV-Scanner | `verify` の中。依存の脆弱性の関門はこれだけ（`team.md` の Way of Working） |

コンテナのイメージ・監視・負荷の試験の道具の版は、今回は読み直していない（前回の記録は Intent `261004-safety-carryover` の時点のもの）。

今回の Intent の論点（役割・権限、N 階層のメニュー）は、今ある Spring Security と画面の土台で扱える見込み（見立て）。新しい依存（木の表示の部品など）を足すときは、ライセンスと推移依存を確かめる（`team.md` の Code Style、`project.md` の Corrections）。
