# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-25 |
| Intent | `261025-deps-perf-carryover`（brownfield、深さ Minimal。前の Intent `261016-business-data-views` からの持ち越し。第1の束: Spring Boot 4.1.1 → 4.1.2 と固定している部品の見直し。第2の束: MySQL・MariaDB の一覧の件数の取り方・読み取りのプール・perf） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `bc484c9f39ef3fd5327fc9e06763d5123057ee5e`（`develop` の HEAD。アーキテクトが `git rev-parse HEAD` で確かめた。スナップショットの source の識別（`git:…`）はコミットではないため、ここには書かない） |
| 走査の広さ・深さ | 全体の読み直し（Full rescan）・Minimal。全体の構成は一覧で把握し、今回の Intent に関わる範囲だけを深く読んだ |
| 手順 | 開発担当のコードスキャン（`aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合。9 文書はこの回の調査から作り直した（wholesale replace） |
| サブモジュール | `vendor/make-you-chic-ui` `ecb07e9248af9f1203a0bd2d79673d35547a4416`・`vendor/java-mustache-processor` `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（0.1.0）。`git submodule status` で確かめた |

## 確かめ方

- ファイルの読み取り（Read・grep・`ls`・`find`・`git ls-files`・`git submodule status`・`git rev-parse`）で確かめた。Gradle・npm・Docker・git の状態を変える操作は行っていない。`.env`・鍵ファイルは開いていない。
- アーキテクトが自分で確かめ直したもの: `RowSqlBuilder.count`/`page` と `RowSqlExecutor.search` の流れ、`TargetDbProperties` の `Pool`・`QueryTimeout`、`TargetDataSourceConfig` のプールの設定、`ReadPurpose` の4値、`PageRequest` のページの大きさ（20・50・100）と番号の上限、`ContainsPattern`、`RowPageResponse.total`（`long`）、画面の件数の表示（`BusinessDataListPage.tsx` 207〜225 行）、業務データの controller の6口と `@ApiAccess(AUTHENTICATED)`、`businessdata` の import の先（`common`・`dsl`・`role`・`targetdb`）、`audit` が `businessdata` を import すること、境界テストのファイル数（19）と `@DisplayName`、`packagesJudgedByTotal` の7パッケージ、`vitest.config.ts` の `thresholds`、`libs.versions.toml` の版とコメント、`backend/gradle.lockfile` の主な版、`.github/dependabot.yml` の `ignore`、`Dockerfile` の ENTRYPOINT、`docker/perf/compose.yaml` の資源の上限、`perf/README.md` の (c) の節（1003・1021 行）。
- 事実と見立て（仮説）は、開発担当の記録どおり各所見の本文で分けて書いた。Spring Boot 4.1.2 の BOM・release notes・OpenTelemetry の版は、このリポジトリにもキャッシュにも無く読んでいない（K-66 は上げた後の lockfile の差で確かめる）。

## 前の記録との関係

- 前のコード知識ベースは Intent `261016-business-data-views` の開始時のもの（記録のコミット `09b795d`）。その時点では業務データの機能がコードに無かったため、K-49〜K-61 の「業務データの仕組みが無い」という記述は今は成り立たない（`businessdata` が入っている）。今回は 9 文書を作り直し、前の文は残していない。
- 部品 ID は前回の 47 個に `businessdata` を足した 48 個。前の ID の名前は変えていない。
- 前回までの所見 K-1〜K-61 の本文はこの知識ベースに無い（前の Intent の記録と git の履歴に残る）。前回の持ち越しのうち今回深く読んでいないもの（K-53 `admin_flag`、K-59 `memory.peak` の測り方、K-60 make-you-chic-ui と axe-core の取り込み）は、`business-overview.md` に参照先だけを書いた。

## 開発担当の記録との差

- 開発担当は「`perf/README.md` の依頼の (c) の grep の形の該当行は未特定」としたが、`perf/README.md` に見出し `(c)` は1つだけ（1003 行）で、grep は 1021 行にある。前の Intent の `feedback-loop.md` 34・152 行が同じものを指している。K-68 に書いた。
- 開発担当は `.github/workflows/ci.yml` を「Analyzed deeply」に挙げたが、「構成のみ」と注記されているため、`shallow.paths` に入れた。`Dockerfile` も ENTRYPOINT の行だけの確認のため `shallow.paths` に入れた。
- 業務データの controller の読み（`BusinessDataReadController`・`BusinessDataWriteController`・`BusinessDataPaths`）、`PageRequest`・`ContainsPattern` は、開発担当の Analyzed deeply には無く、アーキテクトが追加で読んだ。`analyzed.paths` に入れた（スナップショットは `./` のため範囲内）。
- 開発担当は「個別のエンドポイント数は未確認」としたが、controller の Mapping から 6 口（読み取り 3、書き込み 3）と確かめた。

## 所見の番号

前回の所見は K-61 で終わるため、今回は **K-62〜K-68**。一覧と持ち主の文書は `business-overview.md`、本文は持ち主の文書に1か所だけ書いた（`architecture.md`: K-62・K-63、`api-documentation.md`: K-64、`component-inventory.md`: K-65、`technology-stack.md`: K-66 の版の表、`dependencies.md`: K-66 の見送り、`code-quality-assessment.md`: K-67・K-68）。

## 範囲の要約

- 深く読んだ: 23 のパス（すべてファイル）、5 部品（`businessdata`・`targetdb`・`frontend-feature-tables`・`build-and-verify`・`perf-and-monitoring`）。いずれも部品の一部だけを読んだ「一部」の深さ。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（Full rescan を選んでも、前回までと同じ扱い）。
- `fingerprint` は、最終の `analyzed.paths`（23）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261025-deps-perf-carryover
fingerprint: 7b25215781847d8c15bffc8ba6b5b3dfd7ab2434
analyzed:
  paths:
    - gradle/libs.versions.toml
    - backend/gradle.lockfile
    - backend/build.gradle.kts
    - .github/dependabot.yml
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlExecutor.java
    - backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataReadService.java
    - backend/src/main/java/cherry/mastersmith/businessdata/web/RowPageResponse.java
    - backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataReadController.java
    - backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataWriteController.java
    - backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataPaths.java
    - backend/src/main/java/cherry/mastersmith/businessdata/domain/PageRequest.java
    - backend/src/main/java/cherry/mastersmith/businessdata/domain/ContainsPattern.java
    - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java
    - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java
    - backend/src/main/java/cherry/mastersmith/targetdb/domain/ReadPurpose.java
    - frontend/src/features/tables/BusinessDataListPage.tsx
    - frontend/src/features/tables/BusinessDataTable.tsx
    - frontend/package.json
    - frontend/vitest.config.ts
    - perf/k6/scenarios.js
    - perf/README.md
    - docker/perf/compose.yaml
  components:
    - businessdata
    - targetdb
    - frontend-feature-tables
    - build-and-verify
    - perf-and-monitoring
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/main/resources/
    - backend/src/test/java/cherry/mastersmith/
    - backend/config/
    - frontend/src/app/
    - frontend/src/shared/
    - frontend/src/features/
    - frontend/e2e/
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - docker/
    - perf/
    - config/
    - compose.yaml
    - Dockerfile
    - .env.example
    - .github/workflows/ci.yml
    - settings.gradle.kts
    - build.gradle.kts
    - frontend/package-lock.json
    - README.md
```
