# 生成の記録（Generation Notes）— U5 業務のメニュー（navigation）、Bolt B7

計画: `code-generation-plan.md`（同じディレクトリ、依頼者の Approve Plan で承認済み。13節の Q1〜Q6 はすべて A）。生成の担当（開発担当）が手順ごとのコマンドと結果、計画との差を書き足す。パスはリポジトリのルートからの相対。

- 依頼の範囲（1回目の依頼）: Step 1〜13。Step 13 が通ったら止めて報告する。
- 長いコマンドは `caffeinate -i` で包んだ。対象DB のテストを含む実行では README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡した。
- テストの件数は `backend/build/test-results/{test,integrationTest}/*.xml` を集計した実測の値（名指しの実行でも `:backend:cleanTest`・`:backend:cleanIntegrationTest` を付けた）。
- テストを流している間に、同じ作業フォルダで別のビルドを流していない。

## Step 1: 作業の場の用意

- ブランチ: `feature/261004-role-menu-b7`（指揮役が作成済み）。開始の時点の HEAD は `e64382e`（「B7 navigation のコード生成の計画を承認」）。
- `git status --short`: アプリのソースに未コミットの変更なし。変更は `aidlc/spaces/default/intents/261004-role-menu/audit/sakura-local-4e42a93f87ce.md`（監査ログの追記。ワークフローの記録として外して判断）だけ。
- `frontend/playwright-report`・`frontend/test-results`: どちらも無い。
- `vendor/make-you-chic-ui` の固定先: `e82b651`（計画どおり）。

## Step 2: テストの実行の準備

- `./gradlew :backend:test --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest'`: 8 件、失敗 0・飛ばし 0。
- `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.role.web.MyPermissionsApiIT'`: 4 件、失敗 0・飛ばし 0。
- `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/validateRegistrations.test.ts`: 1 ファイル・19 件 passed。

## Step 3: 変更の前の基準

- colima: `colima status` は running（macOS Virtualization.Framework、aarch64、docker、mountType sshfs）。`colima ssh -- ls <リポジトリ>/docker` でホームの共有が付いていることを確かめた。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（01:36:04〜01:48:23、約 12 分 19 秒、exit 0）。コンパイルは開始の時点のソース（`e64382e`）で行われた（`backend/build/classes/java/main` に `navigation` が無いことを確かめた。Step 4 以降のファイルは、コンパイルが終わってテストを流している間に書いた）。
- 件数: 単体 232 クラス・2,015 件、結合 214 クラス・1,109 件。失敗 0・飛ばし 0（対象DB の3種類の結合テストも飛ばされていない）。
- カバレッジ（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `role.domain` | 94.2%（600/637） | 85.4%（305/357） |
| `role.service` | 96.6%（976/1,010） | 90.3%（346/383） |
| `dsl.domain` | 100.0%（321/321） | 96.1%（148/154） |
| `dsl.service` | 96.6%（229/237） | 92.4%（97/105） |
| 全体 | 97.9%（10,340/10,560） | 92.4%（3,914/4,236） |

- lockfile の SHA-256: `backend/gradle.lockfile` は `d415205433ec3a81601fe066540fcd59d1bd8cefdf08fabf4aa834f27f5475d5`、`frontend/package-lock.json` は `8a83a269d5df7c36adfb4816f4952807393733a6c99ba9c77df80fb2cc0b7ddb`。
- role の `EffectivePermissionConsistencyIT`（`backend/src/test/java/cherry/mastersmith/role/service/EffectivePermissionConsistencyIT.java`）があることを確かめた（NFR1.12 の引き継ぎ）。

## Step 4: ドメイン — 実装

- 作ったもの（`backend/src/main/java/cherry/mastersmith/navigation/`）: `package-info.java`、`domain/package-info.java`・`NavTableRef`・`DisplayLabel`・`EmptyReason`・`NavNode`・`NavigationMenu`・`TableAccessResult`・`MenuFilterInput`・`NavIconPolicy`・`MenuFilter`。
- 形の決め方:
  - `NavNode` はコンストラクターで「`navigable` と `table` の有無が一致する」「移れない項目は子を1つ以上持つ」を確かめる。`toString` は位置の道・移れるか・子の数だけ（D-6）。
  - `NavigationMenu` は「`items` が空のときだけ `emptyReason` を持つ」を確かめ、`toString` は項目の数と空の理由だけ（D-6）。
  - `MenuFilterInput` は `BiFunction<String, String, MainPermission>`（スキーマ, テーブル → 主権限）と `UnaryOperator<String>`（アイコン）の組。
  - `MenuFilter` は Spring の部品ではない静的な純粋な関数（TraceAspect の対象外）。主権限の操作が null を返したら例外（本番は写しの `main` で null を返さない）。
  - `NavIconPolicy` は Spring の部品ではない値の型。既定の名前 `list` を含まない集まりでは作れない。
- 計画との差: 無い。

## Step 5: ドメイン — テスト

- 作ったもの（`backend/src/test/java/cherry/mastersmith/navigation/domain/`）: `MenuFilterTest`（12 件）・`MenuFilterPropertyTest`（8 つの性質 (a)〜(h)）・`NavIconPolicyTest`（14 件。パラメーターの表）・`NavNodeTest`（5 件。`NavTableRef`・`DisplayLabel`・`NavigationMenu`・`TableAccessResult` の形も確かめる）。
- `MenuFilterPropertyTest` の生成器: 深さ 1〜5 段・子の数 0〜5・テーブルの重なり（5 つの DSL のテーブルと DSL に無いテーブル 1 つを回して指す）・アイコンの値（許した名前・大文字違い・空・null・一覧に無い名前）。(e) は DSL のテーブルを1つ選んで NONE なら READ に強める。(g)(h) は同じ木に2通りの権限を当て、両方に残る `id` の項目が同じ表示名・アイコンを持ち、`id` が元の木の位置の項目を指すことを確かめる。
- `backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java`: `onlyAuditAndNavigationDependOnRole` の navigation の規則を `role.service` だけから `role.service`・`role.domain` だけに広げ、`@DisplayName` と説明文を合わせた（13節 Q1: A）。`role.repository`・`role.store`・`role.transfer`・`role.web` への依存は今までどおり禁じる。規則が空振りしないよう「navigation は実際に `role.domain` の型を使っている」の確かめを1つ足した。ほかの規則は変えていない。
- コマンド: `./gradlew :backend:spotlessApply` の後、`caffeinate -i ./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.navigation.domain.*' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest'`。
- 結果: 5 クラス・47 件（`MenuFilterPropertyTest` 8・`MenuFilterTest` 12・`NavIconPolicyTest` 14・`NavNodeTest` 5・`RoleBoundaryArchitectureTest` 8）、失敗 0・飛ばし 0。性質ベースのテストの失敗は無く、記録する種は無い。

## Step 6: 業務処理 — 実装

- 作ったもの: `backend/src/main/resources/navigation/allowed-icons.txt`（`#` の行の Apache License 2.0 のヘッダー（年 2026・著作権者 agwlvssainokuni、13節 Q6: A）と説明の行、18 個の名前。`frontend/src/app/registry/allowedIcons.ts` の `ALLOWED_ICONS` と同じ並び）、`navigation/service/package-info.java`・`AllowedNavIconList`・`NavigationBarrier`・`NoOpNavigationBarrier`・`NavigationService`。
- `AllowedNavIconList`: コンストラクターでクラスパスの `navigation/allowed-icons.txt` を1回読む（引数の無いコンストラクター1つ）。読む口は静的な `load(Resource)`（無い・読めないを見分け、誤りのときは ERROR に `reason` の鍵で種類だけを出す）と `parse(String)`（ログは出さない）。誤りは `InvalidIconListException`（`IllegalStateException` の下位、文は種類だけ）で、種類は `MISSING`・`UNREADABLE`・`EMPTY`・`NO_DEFAULT`・`DUPLICATE`。
- `NavigationService`: メニューは `current()` 1回 →（`Absent`・menus が空なら `NOT_CONFIGURED`）→ `afterDslRead()` → `snapshotFor` 1回 → `MenuFilter`。置き場は `resolve(userId, PermissionTarget.table(…))` 1回 → `afterResolve()` → NONE なら `NotVisible`、READ・FULL なら `current()` 1回で `DslModel.findTable` を引き、無い・DSL が無いなら `NotVisible`。トランザクションを開かない。
- 計画との差: 無い。

## Step 7: 業務処理 — テスト

- 作ったもの: `backend/src/test/java/cherry/mastersmith/navigation/testsupport/` の `CountingDslProvider`・`CountingResolver`（T3。写しは本物の `PermissionSnapshot` で作る。例外を投げる場合も持つ）・`NavigationModels`（版 2 のモデルとメニューの項目を組み立てる手伝い。テーブル・スキーマは role の `RoleDslFixture` の形を使う）、`navigation/service/` の `AllowedNavIconListTest`（6 件）・`NavigationCallCountTest`（8 件）・`NavigationServiceTest`（9 件）。
- `NavigationCallCountTest` の結果: `performance-design.md` 1節の表のとおり（メニュー 1 項目・1,000 項目は `current()` 1・`snapshotFor` 1・`resolve` 0、DSL 無し・menus が空は `current()` 1・ほか 0、置き場の 200 は `resolve` 1・`current()` 1、403 の NONE・組が無い・DSL 無しは `resolve` 1・`current()` 0）。
- `NavigationServiceTest`: 空の理由の3つ、絞った木（止める点の順）、`afterDslRead` で写しの DSL を差し替えたときに古い menus を新しい写しで絞り `current()` を読み直さないこと（BR1.5）、置き場の 200・`NotVisible` の3つ、`afterResolve` で DSL から組を消すと `NotVisible`・残すと新しい表示名、解決の口の例外がそのまま出ること、`NoOpNavigationBarrier` で最後まで流れること。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.navigation.service.*'`。
- 結果: 3 クラス・23 件、失敗 0・飛ばし 0。
- 計画との差: `NavigationModels`（単体テストのモデルの組み立ての手伝い）は計画の 4.4 の一覧に無い。C1 の単体テストで使い、結合テストの `NavigationDslFixture`（Step 9）もこれを使う。
- C1 のコミットの提案（実行は依頼者の承認の後。生成の担当はコミットしない）: Step 4〜7 の `navigation.domain`・`navigation.service`・`allowed-icons.txt`・その単体テスト・`navigation/testsupport` の `CountingDslProvider`・`CountingResolver`・`NavigationModels`、`RoleBoundaryArchitectureTest` の広げ。件名の案は「B7 C1 業務のメニューのドメインと業務処理、アイコンの一覧」。
- 層の順の補い: web の層（Step 8）の下書きを Step 3 の待ちの間に書いたが、作業フォルダの外（スクラッチの置き場）に置き、Step 7 のテストが通るまで作業フォルダに入れなかった（C1 にも含まない）。

## Step 8: API — 実装

- 作ったもの（`backend/src/main/java/cherry/mastersmith/navigation/web/`）: `package-info.java`・`NavigationController`・`NavigationResponse`・`NavNodeResponse`・`NavTableRefResponse`・`DisplayLabelResponse`・`TableAccessResponse`。
- `NavigationController`: `@ApiAccess(AUTHENTICATED)`、道の頭 `/api/me`。主体は `@AuthenticationPrincipal AuthenticatedUser` の `userId` だけ。`schema`・`table` は `@RequestParam(required = false)` で受け、欠け・空は `BusinessException(CommonProblemTypes.VALIDATION_FAILED)`（`fieldErrors` なし。13節 Q3: A）。空白だけは空と見なさない（Q4: A）。結果の型を `switch` で尽くし、`NotVisible` はコンストラクターで `ProblemTypeRegistry.findByCode("ACCESS_DENIED")` から引いた種類の `BusinessException`（無ければ起動を止める。Q2: A、D-3）。
- 応答の `NavigationResponse`・`NavNodeResponse` の `toString` は項目の数・位置の道だけ（D-6）。`emptyReason` は列挙の名前の文字列。
- コマンド: `./gradlew :backend:spotlessApply :backend:compileJava`（成功）。
- 計画との差: 無い。

## Step 9: テストの部品

- 作ったもの（`backend/src/test/java/cherry/mastersmith/navigation/testsupport/`）:
  - T1: `ConnectionCountingDataSource`（`DelegatingDataSource` の包み。スレッドごとの借りている数、印ごとの最大と印の始まりの借りている数を持つ。`reset()` で全部消す。`reset()` の後に返った接続で数を 0 未満に下げない）・`MeasureIdFilter`（要求の頭 `X-Test-Measure-Id` を受けたときだけ `begin`、`finally` で `end`。`ThreadLocal`）・`ConnectionCountingConfig`（`@TestConfiguration`。`dataSource` を包む `BeanPostProcessor` と、最も高い順のフィルターの登録。`NavigationConnectionUsageIT` だけが `@Import`）。R-09 の (1)〜(3) の手当て。
  - T2: `TestNavigationBarrier`（`@Primary` の設定 `Config` つき。点 `AFTER_DSL_READ`・`AFTER_RESOLVE` に最初に来た1件を止め、合図で放す。待ちの上限 20 秒は守りだけ）。
  - `NavigationDslFixture`（`ActiveDslModelHolder` に版 2 のモデルを直に置く・外す。悪い側のメニュー `worstMenus`（最上位のまとまり 40 個 × 25 項目 = 1,000 項目・深さ 5）、明示の設定を SQL でまとめて入れる `insertTableSettings`・`insertColumnSettings`）。
  - `NavigationActors`（role の `RoleActors`・`RoleFixtures` を使って、ロール・割り当て・作業ロールの利用者を作り、2本の API を送る。置き場の引数は `URLEncoder` で正しくエンコードする）。
- 計画との差: `NavigationActors` にスキーマの階層の明示の値でロールを作る `schemaRole` を足した（Step 10 の名前の列の長さの件のため）。

## Step 10: API — テスト（結合）

- 作ったもの（`backend/src/test/java/cherry/mastersmith/navigation/web/`）と結果（どれも失敗 0・飛ばし 0）:

| テスト | 件数 | 中身 |
|---|---|---|
| `NavigationAuthorizationApiIT` | 8 | パラメーターの表（メニュー: 未認証 401・停止中 401・通常 200、置き場: 未認証 401・停止中 401・READ 200・FULL 200・そのテーブルだけ NONE（ほかは READ）403）。停止中の拒否は既存の 401 `AUTHENTICATION_REQUIRED` |
| `NavigationApiIT` | 8 | 半分 NONE での絞り込み（id・label・icon・table・項目の鍵が6つだけ）、メニューに出ない READ の置き場 200、`userId` を足した要求（IDOR）、NONE・DSL に無い・DSL 無しの 403 の本文が `instance`・`traceId` を除き同じで表示名を含まない、作業ロールの切り替え・主権限の変更・割り当ての外しの直後の次の要求で切り替わる（変更の前のアクセストークンのまま）、深さ 5、空の理由の3つ、管理者の印だけの利用者に管理のメニューが出ない、監査の行が増えない |
| `NavigationUntrustedInputIT` | 26 | 試し N1 の 23 種の名前（`&` と `=` を別に数えた）を記号のスキーマ名の下に置き、READ は 200（名前がそのまま返る）・NONE は 403（23 件の表）、空白だけの名前（READ 200・NONE 403・DSL に無い空白 2 つ 403・スキーマも空白 403）、欠け・空の5通りが `fieldErrors` の無い 400 `VALIDATION_FAILED` で本文が `instance`・`traceId` を除き同じ、`label`・`displayName` の記号がそのまま、`icon` の照らし合わせ |
| `NavigationMenuAccessConsistencyIT` | 7 | 期待の表で7つの組（R-03 の手当て: 組1〜3・5・6 にどの項目も指さない READ 以上のテーブルを置き、そのことも確かめる。組6 は NONE の T0 と別に READ の T1）。組1 は 100 テーブル × 100 カラム、1万カラムに READ と FULL を交互に明示、テーブルは偶数が NONE・奇数が READ と FULL を交互、menus 1,000 項目・深さ 5（数と深さをテストの中で確かめる）、P099 はどの項目も指さない |
| `NavigationQueryExposureIT` | 1 | 目印の名前で置き場の 200・403・400 とメニューを送り、スパンの属性・`http.server.requests` のタグ・INFO 以上のログ・403 と 400 の本文に目印が無い。監査の行が増えない |
| `NavigationDslSwapIT` | 4 | 止めて DSL を差し替える4つの場合。メニューの2場合は「古い menus を新しい写しで絞った木（`label` は古い値）」を項目ごとに書いた（R-05 の手当て） |
| `NavigationFailureIT` | 1 | 解決の口を例外を投げる替え物（`@Primary`、`CountingResolver` を使う）にし、2本とも 500 `INTERNAL_ERROR` で例外の文を含まない |
| `NavigationConnectionUsageIT` | 1 | ログインの後に `reset()`、印を付けてメニュー・置き場の 200・403 を1つずつ送り、印ごとの最大がどれも 1、印の始まりの借りている数が 0 |

- コマンド: クラスごとに `caffeinate -i ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests '<クラス>'` で流し、Step 13 でまとめて流し直した。
- 途中で直したこと（テストの作りの誤り。本体は変えていない）:
  - `NavigationUntrustedInputIT` の1回目は 26 件すべて失敗した。300 文字のテーブルに明示の設定を `permission_settings` に入れようとして、列 `table_name` が `VARCHAR(256)` のため入らなかった。スキーマの階層の明示の値（READ・NONE）を継承させる形に直して通した。**記録しておく事実**: role の表の名前の列が 256 文字までのため、256 文字を超える名前のテーブルにはテーブルの階層の明示の設定を置けない（スキーマの継承でだけ権限が付く）。navigation の判定（長さで拒否しない、BR5.1）はこの形で確かめた。role の扱いは計画の範囲の外のため変えていない。
  - `NavigationAuthorizationApiIT` の AssertJ の非推奨の `asList()` を `asInstanceOf(InstanceOfAssertFactories.LIST)` に替えた。
- 計画との差:
  - `NavigationQueryExposureIT` のスパンの集め手: 計画は「既存の `TracingAndLoggingIT` と同じ、テストの集め手」としていたが、`TracingAndLoggingIT` はスパンを集める部品を持っていなかった。テストの中に `SpanProcessor` の `@TestConfiguration`（終わったスパンの属性をすべて集める。サニタイズの前の値）を置き、集まったスパンが空でないことも確かめた。ログは既存の `JsonLogRecords` と `OutputCaptureExtension` で読んだ。
  - 試し N1 の「23 種」は、手順書の並び（22 項目）の「`&` と `=`」を2つに分けて 23 とした。

## Step 11: 境界と構造の検査

- 作ったもの: `backend/src/test/java/cherry/mastersmith/navigation/NavigationBoundaryArchitectureTest.java`（5 件）。依存してよい先（`dsl.service`・`dsl.domain`・`role.service`・`role.domain`・`auth.domain`・`common`）、`access`・`dsl.parse`・`dsl.validate`・`role.repository`・`role.store`・`role.transfer`・`role.web` に依存しない（Q2: A）、どの機能も navigation に依存しない、`NavigationBarrier` の本番の実装は `NoOpNavigationBarrier` だけ、`auth`・`user` の型は web だけが読み service・domain に現れない（`NavigationService` の公開の方法の引数も確かめる）、層は domain・service・web の3つだけで DB の部品・`@Repository`・`@Transactional` を持たない、controller は `@ApiAccess` を持つ。各規則に「見分けている」ことの確かめを添えた。
- 1回目は自分の書いた確かめ（層の一覧に最上位の `cherry.mastersmith.navigation` を入れていた。最上位の `package-info` は ArchUnit の取り込みに現れない）で 1 件落ち、期待を3つの層に直して通した。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.navigation.NavigationBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.access.AccessBoundaryArchitectureTest' --tests 'cherry.mastersmith.dsl.DslBoundaryArchitectureTest' --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest'` と `caffeinate -i ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.ApiAccessConsistencyIT'`。
- 結果: 単体 7 クラス・34 件、結合 1 クラス・5 件、失敗 0。既存の境界テストは `RoleBoundaryArchitectureTest`（Step 5 の広げ）のほかは変えていない。

## Step 12: 画面のテスト

- 作ったもの: `frontend/src/app/registry/allowedNavIcons.test.ts`（3 件。読み方の確かめ、`ALLOWED_ICONS` と集まりとして一致・数が同じ、`list` があり重なりが無い）。`allowedIcons.ts` は変えていない。
- 計画との差: ファイルの道を `import.meta.url` から組み立てる計画だったが、画面のテストの環境（jsdom）では `import.meta.url` が file の形にならず `fileURLToPath` が失敗した（1回目、0 件で失敗）。既存の `src/eslintImportRules.test.ts` と同じ `import.meta.dirname` から組み立てる形に直した（どちらもこのファイルの場所に基づき、cwd に頼らない。R-04 の手当ての中身は同じ）。
- コマンド（`frontend/` で）: `NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app/registry/allowedNavIcons.test.ts`（3 passed）、`npx prettier --check`（OK）、`npx oxlint`（指摘 0）、`npx eslint`（OK）、`node scripts/check-license-header.mjs`（すべてにヘッダーあり）、`npm run -s typecheck`（OK）。

## Step 13: バックエンドの区切りの確かめ

- colima と README の環境変数は Step 3 と同じ。名指しの実行のため対象DB のテストは含まない。
- `./gradlew :backend:spotlessCheck`: 成功。
- 単体: `caffeinate -i ./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.navigation.*' --tests 'cherry.mastersmith.role.RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.ArchitectureTest' --tests 'cherry.mastersmith.ApiAccessArchitectureTest' --tests 'cherry.mastersmith.ApiAccessRulesTest' --tests 'cherry.mastersmith.PublicApiInventoryTest' --tests 'cherry.mastersmith.access.AccessBoundaryArchitectureTest' --tests 'cherry.mastersmith.dsl.DslBoundaryArchitectureTest' --tests 'cherry.mastersmith.auth.AuthBoundaryArchitectureTest'` → 16 クラス・112 件、失敗 0・飛ばし 0（うち navigation 8 クラス・67 件）。
- 結合: `caffeinate -i ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.navigation.*' --tests 'cherry.mastersmith.ApiAccessConsistencyIT' --tests 'cherry.mastersmith.role.web.RoleGrantsNoAdminAccessIT' --tests 'cherry.mastersmith.role.web.MyPermissionsApiIT' --tests 'cherry.mastersmith.role.service.EffectivePermissionConsistencyIT'` → 12 クラス・74 件、失敗 0・飛ばし 0（うち navigation 8 クラス・56 件）。
- 早めの確かめとして `./gradlew :backend:spotbugsGate` も流した: 成功。navigation の指摘は priority 2・3 の警告だけ（`CT_CONSTRUCTOR_THROW`（`AllowedNavIconList`・`NavigationController` の起動を止める例外）、`EI_EXPOSE_REP`・`EI_EXPOSE_REP2`（応答の record の一覧）、`SPRING_ENDPOINT`）で、既存のコードと同じ種類。関門（priority 1・`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）に当たるものは無い。
- 計画の外の既存のテストの失敗: 無い。
- C2 のコミットの提案（実行は依頼者の承認の後。生成の担当はコミットしない）: Step 8〜13 の `navigation.web`、テストの部品（T1・T2・`NavigationDslFixture`・`NavigationActors`）、結合テスト8つ、`NavigationBoundaryArchitectureTest`、`frontend/src/app/registry/allowedNavIcons.test.ts`。件名の案は「B7 C2 業務のメニューの API と結合テスト、アイコンの一覧の一致のテスト」。

## 2回目の依頼（Step 14〜19。Step 20 の手前で止める）

- 指揮役の指示: 差の 1（`permission_settings.table_name` の長さ）は role の単位の事実として、code-summary.md の「承認の場で確かめること」に DSL が許すテーブル名の長さと並べて書く（role は変えない）。k6 の試し走りでは配備したアプリを止めない（VM の余裕を先に確かめる）。記録は1ファイルずつ書いて保存する。

## Step 14: 負荷の台本

- 作ったもの: `perf/make-nav-dsl.mjs`（`--worst`・`--baseline`。版 2 の DSL の最上位の `menus:` の塊だけを行の単位で置き換える。版が 2 でない・`menus:` が1つでない・スキーマが1つでないときは何も書かずに止まる）。
  - 生成の確かめ（`build/perf-results/dsl-v2-trial/postgres/generated.yaml` を入力に、YAML として読み直して数えた）: `--worst` は版 2・menus 1,000 項目・深さ 5・指すテーブル 100・各テーブルちょうど 10 回（7,449,128 バイト）、`--baseline` は 100 項目・深さ 1・各テーブル 1 回（7,264,582 バイト）。版 1 の入力（README）では「書式の版 2 の DSL ではありません」で止まった（exit 1）。
- `perf/k6/scenarios.js`: 場面 `navPrepare`（1 VU・1 回）・`navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline`（`constant-vus`、`VUS` 既定 10・`ROLE_DURATION` 既定 3 分）、閾値（`iteration_duration{scenario:…}` の p95 < 1000・`checks{scenario:…}` の率 1・`http_req_duration{scenario:…}` の `max>=0`）、`navPrepare`・`navMenuBaseline` の `setupTimeout` 10 分、`tableAccessDenied` の `http.setResponseCallback(http.expectedStatuses(403))`、`rolePrepare` の準備の YAML へのロール `perf-nav-worst`（1万カラムに READ と FULL を交互、テーブルの番号が偶数なら NONE・奇数なら READ。埋めるロールを1つ減らし合計 1,000 のまま）。試験用の利用者は `perf-nav-01`〜`10`。
- `k6 inspect --include-system-env-vars`（`grafana/k6:2.3.0` のイメージ）を6つの場面（`rolePrepare` と navigation の5つ）で流し、場面の名前・実行の形・閾値・`setupTimeout` が上のとおりであることを確かめた。
- 試し走り（使い捨ての環境）:
  - VM の余裕を先に読み取りで確かめた: CPU 4・メモリ 6 GiB、`free -m` で空き 4,970 MiB、配備したアプリ 536 MiB / 2 GiB。**配備したアプリ・mailpit・見本の対象DB は止めていない**（指揮役の指示。計画の Step 14 の「この間止める」との差）。
  - `./gradlew :backend:bootWar` と `docker build -t mastersmith:role-menu-b7 .`（配備のタグ `local` は上書きしていない）。
  - 一時の環境ファイルはホームの下（権限 700）に置き、値は表示しなかった。`perf/README.md` の 2N の SQL で `perf-nav-01`〜`10` と `perf-roleop01` を入れ、`rolePrepare` → `navPrepare` → `navMenu`・`tableAccessVisible`・`tableAccessDenied`（各 1 VU・10 秒）→ `navMenuBaseline`（1 VU・10 秒）を `caffeinate -i` で包んで流した。
  - 結果: どの場面も閾値と checks を通った（checks の失敗 0）。`rolePrepare` の checks 4/4、`navPrepare` の checks 4/4（要求 1,273 件）。繰り返しの時間の p95 は `navMenu` 29.4 ms（386 回）・`tableAccessVisible` 3.3 ms（3,451 回）・`tableAccessDenied` 3.3 ms（3,455 回、403 を期待の応答として数えた）・`navMenuBaseline` 25.1 ms（433 回）。`navPrepare` の版 2 の大きな DSL の適用は、対象DB に届かない照合の警告のまま通った（基盤の R-07）。
  - アプリのログ: 試験用の利用者のメールアドレス 0 件、ERROR 0 行、WARN・ERROR の行のテーブル名（`t_0`）0 件。
  - 片付け: `docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v`、一時の環境ファイルの置き場を消した。使い捨てのボリューム・コンテナが残っていないことと、配備した3つのコンテナが動いたままであることを確かめた。生成した DSL と結果は git の対象外の `build/perf-results/{role-data,nav-data,b7-trial}/` に残っている。試験のイメージ `mastersmith:role-menu-b7` は Performance Validation で使えるよう残した。

## Step 15: 文書

- `README.md`: 「業務のメニューの API（Intent 261004-role-menu の U5、Bolt B7）」の節（2本の API の表、項目・絞り方・空の理由・置き場・反映・アイコンの一覧・監査とログ・受け入れた制約）を「外部エクスポートの確かめ方」の前に足した。
- `perf/README.md`: 「業務のメニューの場面」の節（場面の表、データ、準備の番号つきの順（R-02）、流す順（`navMenuBaseline` を最後。D-8・基盤の R-01）、p95 を場面ごとに別に残す（R-08）、判定、VM の余裕、手順 2N〜5N、試し走りの結果）を末尾に足し、ロールの管理の節の「準備のデータ」に `perf-nav-worst` を書き足した。

## Step 16: 取り残しと変えないものの確かめ

- `git diff --stat develop` で 4.7 のファイル（`application.yaml`・`.env.example`・`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`docker/monitoring/`・`.github/workflows/ci.yml`・`backend/build.gradle.kts`・`backend/gradle.lockfile`・`frontend/package-lock.json`・`allowedIcons.ts`・`backend/src/main/resources/db`・`vendor`）と navigation の外の `backend/src/main/java` に差が無い。サブモジュールの固定先は e82b651・8d44c36 のまま。
- lockfile の SHA-256 は Step 3 と同じ。
- 変えた・足したアプリのファイル 55 件（`aidlc/` を除く）に `TODO`・`FIXME` が無い。新しいファイルのすべてに Apache License 2.0 のヘッダーがある。
- C3 のコミットの提案（実行は依頼者の承認の後）: `perf/make-nav-dsl.mjs`・`perf/k6/scenarios.js`・`perf/README.md`・`README.md`。件名の案は「B7 C3 業務のメニューの負荷の台本と文書」。
- 補い: `perf/` の JavaScript は、既存の `scenarios.js`・`make-large-dsl.mjs` も含めて Prettier の検査の対象の外で、既存のファイルも整形されていない。足した部分も今のファイルの書き方に合わせ、ファイル全体の整形はしていない。

## Step 17: 記録（Step 18 と並べて書いた）

- `source-manifest.json`: 変えた・足したアプリのファイル 55 件（`aidlc/` を除く `git status` のすべて）。
- `traceability.json`: 90 行（AC5.1.1〜AC5.1.18、BR1.1〜BR7.4、集約の行 NFR1〜NFR6 と枝番 33）。OK 69・Deferred 8（AC5.1.8・5.1.10・5.1.12・5.1.16 は U7、AC5.1.18・NFR2.1・NFR2.2・NFR2.5 は Performance Validation）・N/A 13。OK の target はどれも実在する1つのファイルであることを確かめた。
- `code-summary.md`: 3節（テストの量とカバレッジ）は Step 18 の結果で書き足す予定（未記入のまま止めた）。

## Step 18: 1コマンドの検査 — 止めた（計画の外の既存のテストの失敗）

- colima の共有を `colima ssh -- ls <リポジトリ>/docker` で確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（02:45:47〜02:47:34、exit 1）。
- `:backend:test`（単体テスト）の実行の JVM が `java.lang.OutOfMemoryError: Java heap space` で止まった。止まった所は既存の `role/transfer/RoleTransferReaderTest`（`errors` の YAML の読み込み、`dsl.parse.YamlTreeConverter`・`JsonPointers.child` の文字列の連結）。テストの JVM のヒープは `backend/build.gradle.kts` の `maxHeapSize = "1g"`。結合テストより前で止まったため、結合テスト・カバレッジ・静的解析は流れていない。
- 再現の試み（上限 30 分と決めて行った。テストのファイルを一時的に作業フォルダの外へ退け、流した後に元に戻した。git の操作は使っていない）。どれも `:backend:cleanTest :backend:test`:

| 回 | 条件 | 結果 |
|---|---|---|
| 1 | 今の作業フォルダ（verify の中） | ヒープの不足で止まる |
| 2 | 今の作業フォルダ（単体だけ） | ヒープの不足で止まる |
| A | `NavigationBoundaryArchitectureTest` を外す | ヒープの不足で止まる |
| B | `MenuFilterPropertyTest` を外す | ヒープの不足で止まる |
| C | navigation のテストをすべて外す（本体はある） | ヒープの不足で止まる |
| D | navigation の本体（`src/main` の java とリソース）とテストを外す（Step 3 の基準と同じ本体） | ヒープの不足は起きない（232 クラスが流れ、落ちたのは navigation が無いことで当然落ちる `RoleBoundaryArchitectureTest` の1件だけ） |

- 見立て（未検証）: 単体テストは1つの JVM（1g）で流れ、境界テスト（15 クラス）がそれぞれ全体のクラスの取り込み（ArchUnit の `JavaClasses`）を静的な値として持ち続けるため、ヒープが上限の近くにある。navigation の本体のクラスが増えた分だけ各取り込みが大きくなり、大きな YAML を読む `RoleTransferReaderTest` の一時の確保で上限を超えたと見られる。navigation のテストを外しても起きるため、足したテストではなく、本体のクラスが増えたことが引き金になっている（前の Intent で「構造の検査がクラスを持ち続け、10MB 超えのテストでヒープが尽き、テストの JVM のヒープを 1g にした」のと同じ形）。ヒープの中身は測っていない。
- 計画の外の既存のテストの失敗で、直すには計画の 4.7 で変えないとした `backend/build.gradle.kts` に触れるか、既存のテストを書き換える必要があるため、直さずに止めて指揮役に選択肢を報告した。Step 18 の後半（件数・カバレッジ・時間の記録）と Step 19（E2E）は行っていない。

### Step 18 の続き（依頼者の決定 A の後）

- 依頼者の決定 A: 単体テストの JVM の `maxHeapSize` を 1.5g にする（計画 4.7 との差。code-summary.md の 4節に、再現の試みの表・見立て（未検証）・前例と一緒に記録した）。
- 変更: `backend/build.gradle.kts` の `tasks.test { … }` に `maxHeapSize = "1536m"`（1.5g）と説明のコメントを足した。初めに `"1.5g"` と書いた verify は、Gradle が小数の表記を読めず `Cannot parse memory amount notation: 1.5g` で単体テストの前に止まったため、同じ量の `"1536m"` に直した。共通の `tasks.withType<Test>().configureEach` の `maxHeapSize = "1g"` は変えていない（この行は単体と結合の両方に効くため、変えると結合テストの JVM も変わる。指示の「結合テストの JVM やほかの設定は変えない」に合わせて、単体テストのタスクにだけ値を置いた）。
- 前例: Intent 260923-dsl-schema-loader の U4 で、同じ形のヒープの不足のため、テストの JVM のヒープを 1g にした。
- verify（2回目、06:53:51〜06:55:29）: `maxHeapSize = "1.5g"` と書いたため、Gradle が `Cannot parse memory amount notation: 1.5g` で単体テストの前に止めた。同じ量の `"1536m"` に直した。
- verify（3回目）: colima の共有を確かめ、README の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（06:56:03〜07:10:33、14 分 30 秒、**exit 0**）。
  - 単体 240 クラス・2,082 件、結合 222 クラス・1,165 件、失敗 0・飛ばし 0。画面 114 ファイル・1,046 件 passed。
  - カバレッジ: 全体 行 97.9%（10,519/10,742）・分岐 92.4%（4,001/4,328）。`navigation.domain` 行 100.0%・分岐 92.9%、`navigation.service` 行 100.0%・分岐 95.5%、`navigation.web` 行 92.1%・分岐 100.0%。画面は行 97.52%・分岐 92.68%。値の表は code-summary.md の 3節。
  - Gitleaks no leaks found、SpotBugs の関門は通過、OSV-Scanner は lockfile が変わらず UP-TO-DATE。
- Step 17 の `code-summary.md` の 3節を書き足し、Step 17・Step 18 にチェックを付けた。

## Step 19: E2E

- 前提: `frontend/playwright-report`・`frontend/test-results` が無いこと、Mailpit の API（`http://127.0.0.1:8025/api/v1/info`）が 200 であることを確かめた（Mailpit は配備のものをそのまま使い、止め・起動はしていない）。落ちたときの再現の試みの上限は先に 30 分と決めた（B4 で 135 の `dsl admin green dark 360px` が1回落ちている）。
- コマンド: `caffeinate -i ./gradlew e2eTest`（07:11:36〜07:19:11、exit 0）。
- 結果: **177 passed（7.5 分）**、失敗・不安定（flaky）・飛ばし 0。不安定なテストの決まりに当たるものは無かった。
- 報告の確かめの部品: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・json の報告 1・添付 299・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。
- 片付け: git の対象外の報告の置き場 `frontend/test-results/`（`frontend/playwright-report/` は作られない）を中を開かずに消した。
- B7 は E2E を足していない（流れ I は U7 app-frame-ui が持つ。NFR6.3 は N/A）。今回の 177 件は既存の E2E の全体。
