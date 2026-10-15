# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-15 |
| Intent | `261015-deps-ui-carryover`（scope bugfix、深さ Minimal、brownfield。依頼「第1の束と第2の束を同じintentで対応」。束は Intent `261004-role-menu` の振り返り `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節の U1・P1〜P7・K1〜K5） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `4f6c27d71f5fb201f5c5d18d7569cfb37c0aa536`（`develop` の HEAD。アーキテクトが読み取りだけの `git rev-parse HEAD` で取った）。開発担当が読んだ時点は `425ab64`（`develop` の1つ前の HEAD）で、`425ab64`→`4f6c27d` の差は監査の記録のファイル1つ（`aidlc/` の下）だけのため、アプリのソースは同じである。スキャンの前に取ったスナップショットの source の値（`git:8709bcf…`）はコミットではないため、ここには書かない |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベースの判定は UNVERIFIED。束の項目に関わるファイルだけを深く読んだ |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合。9 文書は前回（Intent `261004-role-menu` の開始時）の文を残し、今回読んだ範囲の節を更新・追加した |

## 確かめ方

- ファイルの読み取り（Read・grep・`ls`・`git submodule status`・`git rev-parse`・`git diff --stat`）で確かめた。Gradle・npm・Docker・git の状態を変える操作は行っていない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。
- アーキテクトが自分で確かめ直したもの: 深く読んだ 22 のパスがすべて存在すること、`role`（domain 35・repository 14・service 37・store 8・transfer 14・web 34 ファイル）・`group`・`navigation` のパッケージの構成、`YamlLimits` の4項目（31 行）、`SafeYamlParser` の `setNestingDepthLimit`・`setMaxAliasesForCollections`（90・91 行）と `setCodePointLimit`（96 行）と `decode`（101 行）、`DslFormat.MAX_MENU_DEPTH = 5`（35 行）と `MAX_ARGUMENT_LENGTH = 100`（56 行）、`DslError` が `toString` を上書きしない record であること（35 行）、`useSlowNotice.ts` とそのテストが3つの機能にあること、`WorkRoleSwitcher.css` の `:has()` の上書き（70〜73 行）、`SharedTreeNodeItem.tsx` の失敗の表示が `<p>` であること（62 行付近）、`perf/k6/scenarios.js` の `roleAssign`（1547〜1550 行）と `workRoleRead`（1692〜1695 行）、`RoleStore`・`GroupStore` の `LOCK_TIMEOUT_MILLIS = 3000` と `PESSIMISTIC_WRITE`、`RoleStoreTransactions` のクラスの説明（入れ子の REQUIRES_NEW を使わない）、`backend/build.gradle.kts` の `maxHeapSize`（129 行 `1g`・158 行 `1536m`）と `packagesJudgedByTotal`（230 行）、`navigation` の record 11 個のうち `toString` を上書きするのが4つであること、`MenuFilter` の Javadoc（40 行）、`docker/hikari-pool.sh` 48 行と `Dockerfile` 17 行の eclipse-temurin の版、`.github/dependabot.yml` の ignore（3 項）、サブモジュールの固定先（`5bf1ffe`・`8d44c36`）。
- 事実と見立て（仮説）は、開発担当の記録どおり各所見の本文で分けて書いた。Dependabot のプルリクエストの件数（20 件）は開発担当の `gh pr list` の結果で、アーキテクトは数え直していない。

## 前の記録との関係

- 前のコード知識ベースは Intent `261004-role-menu` の開始時のもの（partial、21 部品・59 パス、記録のコミット `d5aea52`）。その後の Intent `261004-role-menu` の実装（`role`・`group`・`navigation` のパッケージ、画面の `roleadmin`・`groupadmin`・`roletransfer`・`mypermissions`・`tables`・`app/work-role`・`shared/tree`、make-you-chic-ui の固定先 `e82b651`→`5bf1ffe`）は前の文書に入っていなかった。
- 今回は Focused scan のため、9 文書を作り直さず、今回読んだ範囲の節を更新・追加した。範囲の外の前の文は残し、今の実装と食い違うと分かった前の文（「役割の仕組みは無い」など）には、その場に「前回までの記録」「`261004-role-menu` で変わった」と書き添えた。前の文の中身は今回確かめ直していない。
- Scope of Analysis の `analyzed` は今回の走査だけから作り、前回の `analyzed.paths`（59）は確かめ直せなかったため `shallow.paths` に降ろした（今回深く読んだパスと重なるものを除く）。

## 開発担当の記録との差

- 記録するコミット: 開発担当は `425ab64` で読んだ。記録には HEAD の `4f6c27d` を書いた（差は `aidlc/` の下の監査の記録だけ。上の表）。
- `RoleTransferLimits` の置き場: 開発担当は場所を書いていないが、`role/transfer/` ではなく `role/service/RoleTransferLimits.java` にある（`find` で確かめた）。中身（上限の値）は開発担当の記録どおりとした（今回は値を読み直していない）。
- `MenuFilter` の Javadoc の「DSL の深さの上限（5 段）」: 開発担当は「`MenuFilter` の外の上限に頼る前提」と書いた。上限そのものは `dsl/domain/DslFormat.java` の `MAX_MENU_DEPTH = 5`（35 行）にあり、Javadoc の値と一致する（`MAX_DEPTH = 50` は YAML の入れ子の上限で別物）。K-46 にこの形で書いた。
- `DslError` の抜粋: 抜粋の長さの上限は `DslFormat.MAX_ARGUMENT_LENGTH = 100`（56 行）で、開発担当の「先頭 100 文字まで」と一致する。
- 部品: 開発担当は部品 ID を挙げていない。深く読んだファイルを持つ部品を前回の ID に当て、前回の一覧に無い新しいパッケージ・機能に ID を足した（下の「部品 ID の扱い」）。

## 所見の番号

前回の所見は K-38 で終わるため、今回は **K-39〜K-48** とした。一覧と束の項目（U1・P1〜P7・K1〜K5）との対応は `business-overview.md`、本文は持ち主の文書に1か所だけ書いた。

## 部品 ID の扱い

- 前回の 39 個の ID をそのまま使い、`261004-role-menu` で増えたコードに 8 個を足した（計 47 個）: `role`・`group`・`navigation`・`frontend-feature-roleadmin`・`frontend-feature-groupadmin`・`frontend-feature-roletransfer`・`frontend-feature-mypermissions`・`frontend-feature-tables`。前の ID の名前は変えていない。
- 前の ID の場所に足したもの（ID は変えていない）: `frontend/src/shared/tree/` を `frontend-app-core` に、`frontend/src/app/work-role/` を `frontend-app-layout-i18n` に。
- `analyzed.components` は、今回深く読んだファイルを持つ 11 個。どれも部品の一部だけを読んだ「一部」の深さである。

## 範囲の要約

- 深く読んだ: 22 のパス（すべてファイル）、11 の部品。パスは開発担当の Scan Coverage の「Analyzed deeply」で、どれも控えの範囲（今回のスナップショットの paths）の中にある。範囲の外で見たもの（`gradle/wrapper/gradle-wrapper.properties`・`backend/src/test/`・`frontend/vitest.config.ts`・`README.md`）は `analyzed` に入れず `shallow` に入れた。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（前回までと同じ扱い）。
- 前回の深い範囲（認可・利用者・監査・認証・画面の骨組み・make-you-chic-ui の Sidebar など 21 部品・59 パス）は今回確かめ直していないため、記録上の範囲から外れる（NARROWER になる見込み）。
- `fingerprint` は、最終の `analyzed.paths`（22）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261015-deps-ui-carryover
fingerprint: 23c7ed1656562203f646e9f813c6607bf85f9b9e
analyzed:
  paths:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - backend/build.gradle.kts
    - backend/src/main/java/cherry/mastersmith/role/store/RoleStore.java
    - backend/src/main/java/cherry/mastersmith/role/service/RoleStoreTransactions.java
    - backend/src/main/java/cherry/mastersmith/dsl/parse/SafeYamlParser.java
    - backend/src/main/java/cherry/mastersmith/dsl/parse/YamlLimits.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslError.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/MenuFilter.java
    - frontend/package.json
    - frontend/src/app/work-role/WorkRoleSwitcher.css
    - frontend/src/shared/tree/SharedTreeView.tsx
    - frontend/src/shared/tree/SharedTreeNodeItem.tsx
    - frontend/src/features/groupadmin/useSlowNotice.ts
    - frontend/src/features/roleadmin/useSlowNotice.ts
    - frontend/src/features/roletransfer/useSlowNotice.ts
    - Dockerfile
    - .github/dependabot.yml
    - .github/workflows/ci.yml
    - config/npm-build-tools.txt
    - perf/k6/scenarios.js
  components:
    - build-and-verify
    - role
    - dsl
    - navigation
    - frontend-app-core
    - frontend-app-layout-i18n
    - frontend-feature-groupadmin
    - frontend-feature-roleadmin
    - frontend-feature-roletransfer
    - container-runtime
    - perf-and-monitoring
shallow:
  paths:
    - backend/gradle.lockfile
    - frontend/package-lock.json
    - compose.yaml
    - docker/
    - perf/
    - backend/src/main/java/cherry/mastersmith/role/
    - backend/src/main/java/cherry/mastersmith/group/
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/dslmanage/
    - backend/src/main/java/cherry/mastersmith/navigation/
    - backend/src/main/resources/application.yaml
    - frontend/src/app/navigation/
    - frontend/src/app/layout/ShellLayout.tsx
    - gradle/wrapper/gradle-wrapper.properties
    - frontend/vitest.config.ts
    - README.md
    - backend/src/main/java/cherry/mastersmith/access/
    - backend/src/main/java/cherry/mastersmith/common/
    - backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/
    - backend/src/main/java/cherry/mastersmith/appearance/
    - backend/src/main/java/cherry/mastersmith/invitation/
    - backend/src/main/java/cherry/mastersmith/user/
    - backend/src/main/java/cherry/mastersmith/useradmin/
    - backend/src/main/java/cherry/mastersmith/audit/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/mail/
    - backend/src/main/resources/dsl/dsl-schema-v1.json
    - backend/src/main/resources/db/migration/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/app/registry/
    - frontend/src/app/routing/
    - frontend/src/app/admin-forbidden/
    - frontend/src/app/pages/
    - frontend/src/app/display-settings/
    - frontend/src/app/i18n/
    - frontend/src/shared/
    - frontend/src/features/
    - frontend/e2e/
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
```
