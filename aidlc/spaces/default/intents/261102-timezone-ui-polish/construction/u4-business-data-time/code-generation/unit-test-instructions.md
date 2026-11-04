# 単体テストの手順 — U4 u4-business-data-time

対象: 計画（`code-generation-plan.md`）の Step 2〜14 が作る・直すコードのテスト。テストの方針は `team.md` の Testing Posture（test-after、層ごとに実装 → その層のテスト → 通ってから次へ）、Test Strategy は Standard。テストの説明文は英語、テストデータは日本語でよい。

## 道具と準備

| 層 | 道具 | 準備 |
|---|---|---|
| サーバー | JUnit・AssertJ・Mockito・jqwik・ArchUnit（Gradle の `test` と `integrationTest`） | `businessdata` の結合テストは対象DB のコンテナを使うため、colima が動いていること。`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（README の colima の設定）をシェルに渡す |
| 画面 | Vitest・Testing Library（jsdom）・user-event・`vitest-axe`・`fast-check` | `npm ci`（`frontend/`）済み |
| E2E | Playwright（Chromium）。`verify` と CI の外。この単位では書くだけで、流すのは Build and Test | 見本の対象DB（PostgreSQL）と Mailpit がコンテナで動いていること |

日時のテストは機械のタイムゾーンに結果を預けない: 画面のテストはタイムゾーン（`Asia/Tokyo`・`America/New_York`・`Asia/Kathmandu`（`+05:45`）など）と今の時刻を引数で明示に渡す。手元は `Asia/Tokyo`、CI の機械は UTC のため、`TZ` を変えても同じ結果になることを1件確かめる（Step 5）。

## この単位のテストを流す命令（この単位の範囲に絞ったもの）

| 層 | 命令 | 範囲 |
|---|---|---|
| サーバーの単体 | `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.businessdata.domain.Reference*' --tests 'cherry.mastersmith.businessdata.service.ReferenceLookupTest' --tests 'cherry.mastersmith.businessdata.web.BusinessDataReadControllerTest' --tests 'cherry.mastersmith.businessdata.BusinessDataBoundaryArchitectureTest'` | `ReferenceTypesTest`・`ReferencePolicyTest`・`ReferencePolicyPropertyTest`・`ReferenceLookupTest`・`BusinessDataReadControllerTest`・`BusinessDataBoundaryArchitectureTest` |
| サーバーの結合 | `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.Reference*IT'` | `ReferenceConnectionUsageIT`（1件足す）・`ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`・`ReferenceWriteConnectionUsageIT` ほか参照の結合テスト |
| 画面の関数（Step 5〜11） | `npm --prefix frontend run test -- src/shared/format src/features/tables/valueFormat src/features/tables/rowLabel src/features/tables/formDiff src/features/tables/formValidation src/features/tables/searchConditions src/features/tables/businessDataParse src/features/tables/resolveDateTimeInput src/features/tables/references` | 書式・入力の判定・フォームの値・検索の条件・写し・参照（性質ベースを含む） |
| 画面の部品と画面（Step 12・13） | `npm --prefix frontend run test -- src/features/tables` | `DateTimeField`・`TimeZoneNotice`・`useTablesTimeZone`・一覧・詳細・フォーム・検索・ピッカー（`vitest-axe` を含む） |
| 型と静的解析 | `npm --prefix frontend run typecheck`・`npm --prefix frontend run lint`・`npm --prefix frontend run lint:css`・`npm --prefix frontend run format:check`・`npm --prefix frontend run license:check` | `tsc`（E2E を含む）・oxlint・ESLint・Stylelint・Prettier・ライセンスヘッダー |
| まとめの関門（統合の前） | `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 全体（件数とカバレッジを実測する） |
| E2E（この単位では流さない） | `./gradlew e2eTest` | Intent の最後（Build and Test）とリリースの前に全体を流す |

画面の範囲の命令（`vitest run <範囲>`）は全体のカバレッジの閾値の判定をしない。命令は「この単位の範囲」に絞り、画面の全体と `./gradlew verify` は統合の前の関門として別に流す。

## テストの量（Standard: 部品ごとに 5〜8 件程度、層ごとに実装の後）

| 対象 | 種類 | 内容（要点） |
|---|---|---|
| `ReferenceField`・`ReferencePolicy` | 単体 | 型の有無の形の決まり（`AVAILABLE` のときだけ）・ラベルの列の型・列が無いとき・`toString` に値とラベルが出ない |
| `ReferenceLookup`・`ReferenceFieldResponse` | 単体・結合 | 状態ごとの型の有無・JSON の `NON_NULL`・接続の数が今と同じ（`ReferenceConnectionUsageIT` に1件） |
| `shared/format` の薄い関数 | 単体 | 端数 0・3・6 桁・日付の変わり目・重なる時刻・読めない値・`TZ` に依らない |
| `formatCell`・`rowLabel` | 単体 + fast-check | D41 の表示の値・危ない文字・`error` で整形 0 回 |
| `resolveDateTimeInput` | 単体 + fast-check | 読めない・存在しない・重なる（作成・一致は未選択で誤り、範囲は自動）・1つに決まる、性質3つ |
| `formDiff`・`formValidation` | 単体 + fast-check | 初めの値・送る値・変えていない欄を送らない・2択の選択 |
| `searchConditions` | 単体 | 両端・向き・存在しない時刻・重なる時刻・既定の値・ページ送りで変換が増えない |
| 参照（`optionText` ほか）・写し | 単体 | 型の有無・送る値が元の文字・写しの5通り |
| `DateTimeField`・`TimeZoneNotice`・`useTablesTimeZone` | 部品（Testing Library + `vitest-axe`） | 状態・文・リンク・`aria-invalid`・2択・キーボード・フォーカス・危ない文字・`ja`/`en` |
| 一覧・詳細・フォーム・検索・ピッカー | 部品 | 待つ・帯・固定のタイムゾーン・失敗の間はフォームを開かない・往復・差し替えで切り替わる |

## 期待するカバレッジ（下げない）

- 画面: 全体の合計の `thresholds`（行 80%・分岐 70%）を満たす。判定は Build and Test が全体のテストで行う。この単位の範囲の命令は閾値を判定しない。閾値を下げない。`src/features/tables/` の値は Build and Test で記録する。
- サーバー: `businessdata.domain`・`businessdata.service`・`businessdata.web` は、パッケージごとの下限（行 80%・分岐 70%）の対象（`packagesJudgedByTotal` に無い）。全体の合計も満たす。実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の後の `jacocoTestReport` で読み、値を記録する。
- 除外を増やさない（`team.md`）。

## モック・見本のデータ

- サーバー: 既存の参照の見本（`ReferenceLookupTest`・`ReferencePolicyTest` の DSL のビューの見本）に、ラベルの列が時点を表す型の参照を足す。対象DB の結合テストは既存の部品（コンテナ・テストのクラスごとのスキーマ）を使う。
- 画面: API は既存の替え物で差し替え、表示のタイムゾーンの提供元には固定の名前（`Asia/Tokyo` など）を渡す。応答の見本は本物と同じ形（`labelValueType` の有無、端数の桁、`+00:00` の UTC の時点）にする（`project.md` の学び）。
- 時刻の値は架空の値だけ。危ない文字列（`<script>`・`<img onerror>`・`"`・`'`・`javascript:`）はテストの中の定数として持つ。

## 流す前の注意

- 長いテスト（`verify`）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` でスリープが無かったかを確かめる（`project.md`）。
- 対象DB のコンテナが要るテストでは、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。無いと対象DB のテストが `SKIPPED` になり、パッケージごとのカバレッジの下限で失敗する。
- `verify` 全体と E2E の全体は各1回にまとめ、途中は範囲を絞った命令で流す。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 計画: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/code-generation/code-generation-plan.md`
- 設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/`・`nfr-design/`・`infrastructure-design/cicd-pipeline.md`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `resolveDateTimeInput` などの新しいファイルの名前は計画の論理の名前。実装で名前が変わったら、この表の範囲の命令を同じ名前に直す（範囲は `src/features/tables` の中のまま）。
- 未解決の点は無い。
