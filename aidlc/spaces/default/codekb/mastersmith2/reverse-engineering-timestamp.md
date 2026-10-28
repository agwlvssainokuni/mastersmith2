# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-28 |
| Intent | `261027-perf-targets-review`（bugfix、深さ Minimal、brownfield。性能・資源の目標（k6 の応答時間の線、`memory.peak`・`anon` の物差し）の見直しと Dependabot の知らせの洗い出し） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `bb7e9ced804828807637356d44565f3871c57758`（`develop` の HEAD。`.git/refs/heads/develop` を読んで確かめた。スナップショットの source の識別（`git:…`）はコミットではないため、ここには書かない） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベース（STALE）の散文は残し、今回の範囲の節を更新・拡張した |
| 手順 | 開発担当のコードスキャン（`aidlc/spaces/default/intents/261027-perf-targets-review/inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（既存の9文書への更新） |
| サブモジュール | 今回は確かめていない。前回（2026-10-25）の記録: `vendor/make-you-chic-ui` `ecb07e9248af9f1203a0bd2d79673d35547a4416`・`vendor/java-mustache-processor` `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（0.1.0） |

## 確かめ方

- ファイルの読み取り（Read・grep・`ls`・`wc`）だけで確かめた。Gradle・npm・Docker・git の状態を変える操作は行っていない。`.env`・鍵ファイルは開いていない。`gh` を使っていないため、開いている Dependabot の PR は確かめていない。
- アーキテクトが自分で確かめ直したもの: `libs.versions.toml` の版とコメント、`backend/gradle.lockfile` の主な版（Spring Boot 4.1.2・Tomcat 11.0.27・Jackson 3.1.7・Hibernate 7.4.13.Final ほか）、`backend/build.gradle.kts` に Tomcat・Jackson の上書きが無いこと、`TargetDbProperties` の読み取りのプールの既定 10・書き込みのプールの既定 5、`RowSqlBuilder.count` と `RowSqlExecutor.search` が前回から変わっていないこと、`perf/k6/scenarios.js` の `thresholdsFor` の分岐、`perf/README.md` の `memory.peak` の記述箇所と対象DB の資源の表（1150〜1164 行）、`docker/perf/compose.yaml` の資源の上限とイメージ、`frontend/package.json` の版、`.github/dependabot.yml` の `ignore`。
- 事実と見立て（仮説）は各所見の本文で分けて書いた。実測値（Not Met の数値など）は `project.md` の記録によるもので、今回は再測定していない。

## 前の記録との関係

- 前のコード知識ベースは Intent `261025-deps-perf-carryover` のもの（記録のコミット `bc484c9`）。その後に同 Intent のコード変更が入り（読み取りのプールの既定 5 → 10、Spring Boot 4.1.1 → 4.1.2 と Tomcat・Jackson の上書きの除去、`dependabot.yml` の `jackson-bom` の `ignore` の除去、各種の依存の版の更新）、前回の記述の一部が合わなくなっていたため STALE だった。今回は該当箇所を更新した（K-63・K-66・技術の構成の版の表）。
- 部品 ID は 48 個のまま（増減なし）。今回の範囲の外の部品の記述は前回のまま残した。
- 所見は前回の K-62〜K-68 に、今回 K-69〜K-73 を足した。K-66 は「見直し済み」の内容に更新し、K-63 は既定の変化を反映した。K-68（`perf/README.md` の (c) の grep）は今回読み直していない。

## 開発担当の記録との差

- 開発担当は gradle の `ignore` を「appender と networknt」の2件、全体で4件と報告し、アーキテクトの再読でも同じ（`jackson-bom` の `ignore` は無い）。前回の記録は gradle の3件としていたため、今回の更新で直した。
- 開発担当は `README.md` を「性能の箇所は見ていない。ファイル名だけ把握」とした。指示に従い `analyzed.paths` には `README.md` を入れたが、実質は浅い読みである（懸念として残す）。
- 開発担当は compose の既定と README の運用値の食い違いを「どれが正かを要件定義で決める」とした。アーキテクトの再読では、README が「既定」と「業務データの試験の値」を表で併記しており（1153〜1164 行）、食い違いは設計のうえの二層と読めた。ただし何を基準に判定するかは未決（K-71）。

## 範囲の要約

- 深く読んだ: 9 のパス（ディレクトリ5・ファイル4）、4 部品（`perf-and-monitoring`・`businessdata`・`targetdb`・`build-and-verify`）。いずれも部品の一部に近い読みで、前回深く読んだ `frontend-feature-tables` ほかは流し読みに下がった（前回の `analyzed.paths` は `shallow.paths` に降格して併記）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。
- 既存の知識ベースが STALE のため、前回の深い範囲は再確認できないものとして `shallow.paths` に降格した（今回の `analyzed.paths` が覆うものは重ねて書かない）。
- `fingerprint` は、最終の `analyzed.paths`（9）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261027-perf-targets-review
fingerprint: 65a7ff1bff9669f53774e9654a84c5f1d4e8c72b
analyzed:
  paths:
    - perf/
    - docker/perf/
    - backend/src/main/java/cherry/mastersmith/businessdata/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/build.gradle.kts
    - gradle/libs.versions.toml
    - .github/dependabot.yml
    - frontend/package.json
    - README.md
  components:
    - perf-and-monitoring
    - businessdata
    - targetdb
    - build-and-verify
shallow:
  paths:
    - backend/gradle.lockfile
    - frontend/src/features/tables/BusinessDataListPage.tsx
    - frontend/src/features/tables/BusinessDataTable.tsx
    - frontend/vitest.config.ts
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/main/resources/
    - backend/src/test/java/cherry/mastersmith/
    - backend/config/
    - frontend/src/
    - frontend/src/app/
    - frontend/src/shared/
    - frontend/src/features/
    - frontend/e2e/
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - docker/
    - config/
    - compose.yaml
    - Dockerfile
    - .env.example
    - .github/workflows/
    - .github/workflows/ci.yml
    - settings.gradle.kts
    - build.gradle.kts
    - frontend/package-lock.json
```
