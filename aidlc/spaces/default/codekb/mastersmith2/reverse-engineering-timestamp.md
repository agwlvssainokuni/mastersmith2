# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日時 | 2026-09-25 |
| Intent | `260925-storage-memory-fixes`（scope bugfix、内部DB のファイルの伸び・10MB の DSL とログインの重ねでのメモリ・`AccessTokenApiIT` の一時的な失敗・`perf/dsl-timing.sh` の説明の4件） |
| 対象 | mastersmith2（プロジェクトルート） |
| 記録するコミット | `4970f3b13f34af7b8783f2be0c6b08b0de67855d`（`develop` の HEAD。読み取りの前に取ったスナップショットの source の識別は `git:cb55a970719c657a021c78dfa134d632d3da826e`） |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `edb1f943c0e66293494fa974605f34fcd7e258d7` |
| 走査の広さ・深さ | 全体の読み直し（Full rescan、スナップショットの範囲は `./`）・Minimal |
| 手順 | 開発担当のコードスキャン → アーキテクトによる統合（この9つの成果物） |

## 確かめ方

- ファイルの読み取りだけで確かめた。Gradle・npm・Docker は実行していない。テストの件数はファイルを数えた値である。
- `.env`・`.env.targetdb` と鍵ファイルは開いていない。`aidlc/`・`.claude/` と生成物は対象の外。
- アーキテクトは、4件の主な所見（`application.yaml` の接続先、`V5` の表、`PLACE_SQL`・`COPY_SQL`、`DslRecordStore.apply`、`DslContent` の複写、`SafeYamlParser` の読み込み、`DslPreviewCache`・`ActiveDslModelStore`、`HttpTestClient`、テストの JVM の設定、`Dockerfile` の JVM の指定、`perf/dsl-timing.sh` 33・55 行、`README.md` 452 行）と、パッケージ間の `import` を読み直して確かめた。

## 前の記録との関係

前のコード知識ベースは Intent `260924-followup-fixes` のとき（partial、7 部品）のもの。依頼者が全体の読み直しを選んだため、9つの成果物をすべて置き換え、下の Scope of Analysis は今回のスキャンだけから作った。今回の深い範囲は `dslmanage` の保存・`dsl` の読み込み・内部DB の設定・結合テストの基盤・`Dockerfile`・`perf/dsl-timing.sh` で、前回深く読んだ `auth`・`audit`・`frontend-feature-dsl` などは流し読みにした。前回と比べると重ならない部品が出る（比べると部品が「失われた」と表示される見込み）。部品の ID は前回に合わせ、新しく `backend-test-support` を足した。

## 範囲の要約

- 深く読んだ: 29 のファイル（設定・スキーマ変更 V5・V6・DSL の保存と読み込み・投入の上限のフィルター・結合テストの補助と `AccessTokenApiIT`・ビルドの設定と lockfile の該当の行・`Dockerfile`・`perf/dsl-timing.sh`）。
- 流し読み: バックエンドの残り、テストの残り、スキーマ変更の残り、画面、`vendor/make-you-chic-ui`、`docker/`・`perf/` の残り、README、リポジトリの付属の設定。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` である。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260925-storage-memory-fixes
fingerprint: 31d00628ba38dba9a8b75179bef8dc548aa8b445
analyzed:
  paths:
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/db/migration/V5__u4_dsl_management.sql
    - backend/src/main/resources/db/migration/V6__u4_dsl_audit_columns.sql
    - backend/src/main/java/cherry/mastersmith/dslmanage/repository/DslPreviewRepository.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/repository/DslAppliedRevisionRepository.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/service/DslRecordStore.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/service/DslLifecycle.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/service/DslPreviewCache.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslContent.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslPreviewRecord.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslAppliedRevisionRecord.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/web/DslAdminController.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/web/DslHeavyOperationGate.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/web/DslWebConfig.java
    - backend/src/main/java/cherry/mastersmith/common/web/RequestSizeLimitFilter.java
    - backend/src/main/java/cherry/mastersmith/dsl/service/DefaultDslReader.java
    - backend/src/main/java/cherry/mastersmith/dsl/service/ActiveDslModelStore.java
    - backend/src/main/java/cherry/mastersmith/dsl/parse/SafeYamlParser.java
    - backend/src/main/java/cherry/mastersmith/dsl/parse/PositionMap.java
    - backend/src/test/java/cherry/mastersmith/auth/web/AccessTokenApiIT.java
    - backend/src/test/java/cherry/mastersmith/auth/testsupport/AuthApi.java
    - backend/src/test/java/cherry/mastersmith/common/testsupport/HttpTestClient.java
    - backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/SilentServer.java
    - backend/build.gradle.kts
    - gradle/libs.versions.toml
    - backend/gradle.lockfile
    - Dockerfile
    - perf/dsl-timing.sh
  components:
    - config
    - common-web
    - dsl
    - dslmanage
    - backend-test-support
    - build-and-verify
    - container-runtime
    - perf-and-monitoring
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/main/resources/db/migration/
    - backend/src/main/resources/dsl/
    - backend/src/main/resources/logback-spring.xml
    - backend/src/test/
    - backend/config/spotbugs-exclude.xml
    - compose.yaml
    - docker/
    - .env.example
    - perf/
    - README.md
    - build.gradle.kts
    - settings.gradle.kts
    - .github/
    - frontend/
    - vendor/make-you-chic-ui/
    - .gitignore
    - .gitleaks.toml
    - .pre-commit-config.yaml
    - .editorconfig
    - .gitattributes
    - .dockerignore
    - config/
```
