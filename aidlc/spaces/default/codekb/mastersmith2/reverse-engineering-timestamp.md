# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-29 |
| Intent | `261029-reference-lookup-cleanup`（深さ Standard、brownfield。業務データの選択肢の他テーブル参照 REFERENCE と参照ピッカー LOOKUP の本実装、持ち越しの片付け: perf の JWT の自動の伏せ字・コンテナの版・Dependabot の i18next 系2件） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `15a25aaf0822bd68243a6a2cf5dd259e5d7c7cf2`（`develop` の HEAD。`git rev-parse HEAD` で確かめた。スナップショットの source の識別（`git:…`）はコミットではないため、ここには書かない） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Standard。既存の知識ベース（STALE）の散文は残し、今回の範囲の節を更新・拡張した |
| 手順 | 開発担当のコードスキャン（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（既存の9文書への更新） |
| サブモジュール | 今回は固定先のコミットの確認だけ（開発担当）。`vendor/make-you-chic-ui` は `ecb07e9`、`vendor/java-mustache-processor` は前回（2026-10-25）の `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（0.1.0）のまま。make-you-chic-ui の使える部品の一覧は読んでいない |

## 確かめ方

- ファイルの読み取り（Read・grep・`ls`・`wc`）と、読み取りの git・`docker ps` / `docker images` の一覧だけで確かめた。ビルド・`gradlew`・コンテナの起動・`docker inspect` は行っていない。`.env`・鍵ファイルは開いていない。
- アーキテクトが自分で確かめ直したもの: `ColumnWriteRulesFactory` の `lookup` → `text` の置き換え（48 行）と `choices()`（106〜113 行）、呼び出し元が `TableAccessPolicy`（64 行）であること、`OptionResponse` の項目（`value`・`label` だけ）、`OptionSource` の種類ごとの必須、`PermissionSnapshot.main(schema, table, column)`（132 行）、`DslSemanticValidator` の参照先の照合、`frontend/package.json` の i18next 系の版、`compose.yaml` の lgtm・Mailpit の `image:` 行、`businessdata` のファイル一覧、`perf/README.md` の行数（1,359 行）。
- 事実と仮説は各所見の本文で分けて書いた。開発担当が「未検証」とした項目（参照先を読む経路の再利用、`handleSummary`、コンテナのダイジェスト、i18n 2件のリリースノート、k6 の標準出力への JWT の出方）は、成果物でも未検証の仮説のまま書いた。

## 前の記録との関係

- 前のコード知識ベースは Intent `261027-perf-targets-review` のもの（記録のコミット `bb7e9ce`）。その後に同 Intent と後続の変更が入り（`develop` が進んだ）、スナップショットの判定が STALE だったため、今回の範囲の節を更新した。
- 部品 ID は 48 個のまま（増減なし）。名前は変えていない。今回の範囲の外の部品の記述は前回のまま残した。
- 所見は前回までの K-62〜K-73 に、今回 K-74〜K-80 を足した（K-74 REFERENCE・LOOKUP の現状、K-75 参照先を読む経路の候補、K-76 画面の流用点、K-77 perf の JWT、K-78 コンテナの版、K-79 i18next 系2件、K-80 境界テスト・関門）。K-62〜K-73 は今回再確認していない（K-73 の `perf/README.md` の行数だけ 1,359 行に更新）。
- `ColumnWriteRulesFactory` が `formPart: lookup` を `text` に置き換え、REFERENCE・LOOKUP の選択肢を検証から外している点（BR3.7）は、前の Intent `261016-business-data-views` の意図的な暫定として記録した（今回の Intent で本実装に置き換える対象）。

## 開発担当の記録との差

- 開発担当の報告のうち、アーキテクトが読み直して食い違わなかったもの: `ColumnWriteRulesFactory` の置き換え、`OptionResponse` に参照先の欄が無いこと、`PermissionSnapshot` の口、`dsl` の選択肢の型。
- `ColumnWriteRulesFactory` を呼ぶのは `BusinessDataReadService` の直下ではなく `TableAccessPolicy`（`TableAccessResolver` の下）である。開発担当の「定義の応答は選択肢を FIXED のものだけ `choices` に載せる」は、この経路の結果として整合する。
- 開発担当は `frontend/src/shared` を「構成の一覧」だけ読んだとしたが、`analyzed.paths` には指示に従って入れた（実質は浅い読みである。懸念として残す）。同様に `README.md` と `gradle/libs.versions.toml`・`config/` も浅い（`libs.versions.toml`・`config/` は `shallow` に残した）。

## 範囲の要約

- 深く読んだ: 14 のパス、8 部品（`businessdata`・`dsl`・`targetdb`・`role`・`frontend-feature-tables`・`perf-and-monitoring`・`container-runtime`・`build-and-verify`）。いずれも部品の一部に近い読み。前回深く読んだ範囲のうち今回の `analyzed.paths` が覆わないもの（`gradle/libs.versions.toml`）は `shallow.paths` に降格して併記した。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。
- `fingerprint` は、最終の `analyzed.paths`（14）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261029-reference-lookup-cleanup
fingerprint: 02db8d87c51075884e23259e30bb48fe7e165190
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/businessdata/
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/role/
    - backend/src/main/resources/
    - frontend/src/features/tables/
    - frontend/src/shared/
    - perf/
    - docker/perf/
    - compose.yaml
    - .github/
    - frontend/package.json
    - backend/build.gradle.kts
    - README.md
  components:
    - businessdata
    - dsl
    - targetdb
    - role
    - frontend-feature-tables
    - perf-and-monitoring
    - container-runtime
    - build-and-verify
shallow:
  paths:
    - gradle/libs.versions.toml
    - backend/gradle.lockfile
    - frontend/vitest.config.ts
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - backend/config/
    - frontend/src/
    - frontend/src/app/
    - frontend/src/features/
    - frontend/e2e/
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - docker/
    - config/
    - Dockerfile
    - .env.example
    - settings.gradle.kts
    - build.gradle.kts
    - frontend/package-lock.json
```
