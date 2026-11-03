# 単体テストの手順 — U5 u5-menu-icons-home

対象: 計画（`code-generation-plan.md`）の Step 3〜26 が作る・直すコードのテスト。テストの方針は `team.md` の Testing Posture（test-after、層ごとに実装 → その層のテスト → 通ってから次へ）、Test Strategy は Standard。テストの説明文は英語、テストデータは日本語でよい。

## 道具と準備

| 層 | 道具 | 準備 |
|---|---|---|
| サーバー | JUnit・AssertJ・Mockito・jqwik・ArchUnit（Gradle の `test` と `integrationTest`） | 追加の準備なし。`navigation` のテストは内部DB（H2）だけを使い、コンテナは要らない |
| 画面 | Vitest・Testing Library（jsdom）・user-event・`vitest-axe`・`fast-check` | `npm ci`（`frontend/`）済み。`NODE_OPTIONS=--no-experimental-webstorage` は `package.json` の `test` スクリプトが付ける |
| E2E | Playwright（Chromium）。`verify` と CI の外 | 見本の対象DB（PostgreSQL）とメールの受け手（Mailpit）がコンテナで動いていること。起動していなければ `./gradlew e2eTest` が手順を示して失敗する（`team.md`） |

テストは機械のタイムゾーンに結果を預けない。この単位は日時を扱わないため、時刻に依存するテストは無い。

## この単位のテストを流す命令（この単位の範囲に絞ったもの）

| 層 | 命令 | 範囲 |
|---|---|---|
| サーバーの単体 | `./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.navigation.*'` | `NavNodeTest`・`MenuFilterTest`・`MenuFilterPropertyTest`・`NavIconPolicyTest`・`AllowedNavIconListTest`・`NavigationServiceTest`・`NavigationCallCountTest`・`NavigationRecordsToStringTest`・`NavigationBoundaryArchitectureTest` |
| サーバーの結合 | `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.navigation.*'` | `NavigationApiIT`・`NavigationDslSwapIT`・`NavigationAuthorizationApiIT`・`NavigationMenuAccessConsistencyIT`・`NavigationUntrustedInputIT`・`NavigationSecretLeakIT`・`NavigationConnectionUsageIT`・`NavigationQueryExposureIT`・`NavigationFailureIT` |
| 管理の確認 API の既存のテスト（変えないことの確かめ） | `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.access.web.Admin*'` | `AdminPathBoundaryIT` ほか。変更なしで通る |
| 画面の層 2（関数と型） | `npm --prefix frontend run test -- src/app/navigation` | `navigationApi`・`navIcon`・`navDestination`・`buildNavSections`（性質ベースを含む）・`BusinessNavigationProvider` |
| 画面の層 3（部品） | `npm --prefix frontend run test -- src/app/pages src/app/layout src/app/routing src/app/registry src/app/admin-forbidden src/app/testing` | `HomePage`・`HomeCard`・`HomeCardList`・`homeCards`・`ShellLayout`・`AppRouter`・`decideRoute`・`validateRegistrations`・`allowedNavIcons`・`AdminForbidden*` |
| 管理の登録のテスト | `npm --prefix frontend run test -- src/features/useradmin src/features/invitation src/features/dsl src/features/groupadmin src/features/roleadmin src/features/roletransfer src/features/preferences src/features/registration` | 各 `registration.test.tsx`（アイコンが付く、`admin-area` が並びから消える）・`RoleDetailPage.test.tsx` |
| 型と静的解析 | `npm --prefix frontend run typecheck`・`npm --prefix frontend run lint`・`npm --prefix frontend run lint:css`・`npm --prefix frontend run format:check`・`npm --prefix frontend run license:check` | `tsc`・oxlint・ESLint・Stylelint・Prettier・ライセンスヘッダー。ビルドの後は `npm --prefix frontend run bundle:size`（入口の JavaScript の大きさの関門。ホームの部品を足しても超えないことを確かめる） |
| E2E（統合の前に必須） | `./gradlew e2eTest` | 全件（030・090・130・160 の書き換えと、補助の絞りの影響を受ける 060・110・120・135 を含む）。新しい流れは足さない |
| まとめの関門（統合の前） | `./gradlew verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付けて、件数とカバレッジを実測する） | 全体 |

命令は「この単位の範囲」に絞る。画面の全体（`npm --prefix frontend run test`）と `./gradlew verify` は、統合の前の関門として別に流す。

## テストの量（Standard: 部品ごとに 5〜8 件程度、層ごとに実装の後）

| 対象 | 種類 | 内容（要点） |
|---|---|---|
| `NavNode`・`NavTableKind` | 単体 | 形の決まり（`tableKind` は移れる項目だけ）・`toString` の形 |
| `MenuFilter` | 単体 + jqwik | `TABLE`・`VIEW`・フォルダ `null`・操作が `null` でも項目が落ちない・操作の呼び出しは移れる項目 1 つにつき 1 回・見られない項目に付かない・権限を足しても種類は変わらない |
| `NavigationService`・応答 | 単体・結合 | 読み出しの回数が変わらない・応答の `tableKind`・既存の項目の値が変わらない・差し替えの途中で木と種類がずれない・1 項目の追加分が 22 バイト以下 |
| `parseNode` | 単体 | `tableKind` の項目なし・`null`・`TABLE`・`VIEW`・知らない値（5 通り） |
| `resolveItemIcon` | 単体 + fast-check | D10 の 4 手順・`list`/空/知らない値・種類を先にする・許す一覧の名前だけが返る |
| `firstNavigablePath` | 単体 + fast-check | 移れる項目は自分の道・フォルダは最初の移れる子孫・訪問数 ≦ 項目数 |
| `buildHomeCards` | 単体 + fast-check | 応答に無い項目のカードが出ない・管理者でなければ管理のカードが空・順・道の形の確かめ |
| `HomePage`・`HomeCard`・`HomeCardList` | 部品（Testing Library + `vitest-axe`） | 各状態（読み込み中・案内・失敗と「もう一度」・業務のカード・管理のカード・作業ロールの有無）・`ja`/`en`・表示名の `<script>` などが文字として出る・API の呼び出しが増えない・`vitest-axe` を部品ごとに 1 件 |
| `ShellLayout`・各登録 | 部品・単体 | ロゴが出る・管理の区画に仮の項目が無い・6 項目にアイコンが付く |
| 画面の道 | 単体 | `/admin` が見つからない（`decideRoute`・`AppRouter`） |

## 期待するカバレッジ（下げない）

- 画面: 全体の合計の `thresholds`（行 80%・分岐 70%）を満たす。この判定は Build and Test が全体のテストで行う。この単位の範囲の命令（`vitest run <範囲>`）は閾値の判定をしない。閾値だけで終了コードが非 0 になって、選んだテストがすべて通っているときは、この単位のテストの結果に「閾値のみ」と記録し、失敗とは数えない。閾値を下げない。
- サーバー: `navigation.domain`・`navigation.service`・`navigation.web` は、パッケージごとの下限（行 80%・分岐 70%）の対象（`packagesJudgedByTotal` に無い）。全体の合計も満たす。除外を増やさない。実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の後の `jacocoTestReport` で読み、値を記録する。
- カバレッジの計測から外すのは、起動クラス・設定値だけのクラス・自動生成・`vendor/` だけ（`team.md`）。

## モック・見本のデータ

- サーバー: 内部DB の H2 を使う。`NavigationModels`・`NavigationDslFixture`・`NavigationActors` などの既存の見本を使い、差し替えの見本にビューを含むテーブルを足す。DSL・権限の読み出しの回数は、既存の「数える替え物」（`CountingDslProvider`・`CountingResolver`）で数える。
- 画面: API は `apiRequest` の替え物（既存の `frameTestSupport.tsx`・`renderWithProviders`）で差し替える。応答の見本には、本物と同じ形（`tableKind` を含む、子の有無のある木）を使う（`project.md` の学び: 見本が本物と違うと不具合を見逃す）。
- テストデータのメールアドレス・氏名は、予約のドメイン（`example.com`）と架空の値だけ。表示名の悪意のある文字列（`<script>`・`<img onerror>`・`"`・`'`・`&`・`javascript:`）はテストの中の定数として持つ。

## 流す前の注意

- 長いテスト（`verify`・E2E）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` でスリープが無かったかを確かめる（`project.md` の学び）。
- 対象DB のコンテナが要る `verify` では、`DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（README の colima の設定）をシェルに渡す。無いと対象DB のテストが `SKIPPED` になり、パッケージごとのカバレッジの下限で失敗する。
- E2E は、統合の前に流して通す。見本の PostgreSQL と Mailpit が動いていること。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 計画: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/code-generation/code-generation-plan.md`
- 設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`nfr-design/`・`infrastructure-design/cicd-pipeline.md`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `package.json` に `typecheck`・`lint`・`format:check` のスクリプトがある（`verify` の段 1・2・4 が使う）。名前が違えば、計画の Step 23 で実際のスクリプト名に直す。
- 未解決の点は無い。
