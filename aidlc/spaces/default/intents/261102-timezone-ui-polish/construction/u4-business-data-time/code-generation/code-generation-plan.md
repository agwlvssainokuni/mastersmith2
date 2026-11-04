# コード生成の計画 — U4 u4-business-data-time

## Summary

- Builds: 業務データの時刻を表示のタイムゾーンで見せ・入れ・探す画面と、参照のラベルの列の型（`labelValueType`）を返すサーバーの1項目
- Touches: `frontend/src/features/tables/`・`references/`・`frontend/src/shared/format/`・`backend/.../businessdata/`（domain・service・web）・`frontend/e2e/`（200 を足す・185 を広げる）
- Tests: サーバーの単体・結合 約 15 件、画面の関数の単体と性質ベース 約 70 件、部品のテストと vitest-axe 約 40 件、既存のテストの書き換え、E2E の流れ 1 本と axe の状態の追加

出典: 機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md` の D1〜D44・W1〜W5、`frontend-components.md`）、NFR 要件（`nfr-requirements/`）、NFR 設計（`nfr-design/`。Q1: A・Q2: Looks correct）、インフラの設計（`infrastructure-design/`）、契約（`inception/contract-design/contract-summary.md` の C1・C2・C4）、単位（`inception/units-generation/unit-of-work.md` の U4）、要件（`inception/requirements-analysis/requirements.md`）、`team.md`・`project.md`。

## 1. 前提と範囲

- Bolt B4（`inception/delivery-planning/bolt-plan.md`）。作業ブランチは `develop` から `feature/261102-timezone-ui-polish-b4`。統合は `develop` へ squash（1 Bolt が1コミット）。サブモジュールの固定先は上げない。
- テストの方針は Testing Contract（下の節）のとおり test-after: 層ごとに実装 → その層のテストを書いて流す → 通ってから次の層へ。
- 統合の前の関門は `./gradlew verify`。E2E は統合の前には流さず、Intent の最後（Build and Test）とリリースの前に全体を流す（2026-11-04 の依頼者の決定）。
- 新しい依存は足さない。サブモジュール（`vendor/`）は変えない。

### 1.1 設計の段の承認の場の決定とレビューの指摘（`project.md`: 計画の前に洗い出す）

U4 の設計の段の承認は、unit-major の流れでは U4 の確かめの場（コード生成の後）にまとめて来るため、まだ承認の場の決定は無い。段の中の答えとレビューの指摘を次のとおり計画に反映する。

| 出どころ | 内容 | この計画での扱い |
|---|---|---|
| NFR 設計 Q1: A（機能設計のレビューの Major） | 表示のタイムゾーンの読み込みの失敗の間は、時点を表す型の欄を持つフォームを開かず、帯を出す | Step 13 で実装する。機能設計の D11 の直しは承認の場で行う |
| NFR 設計 Q2（要点 7 項目） | 2択は別の `FormField`、誤りの文にタイムゾーンの名前、メモなし、今の時刻は開くときに1回、`labelValueType` の作り、E2E 200 と専用の DSL、カバレッジの記録 | Step 2〜14 で実装する |
| 機能設計のレビュー Minor | D28 の既定の値の入れる時期／CONTAINS・CHOICE が時刻の型に付いたとき／一致の検索の端数の案内／追跡の「部分」の書き方／時差の `±HH:mm:ss` | 既定の値は表示のタイムゾーンが `ready` になってから入れる（Step 10）。CONTAINS・CHOICE は今のまま（時刻の変換をしない。DSL の決まりで時刻の型には付かない見込みで、付いても文字として扱う）。一致の検索の案内に「端数を含む値は端数まで入れる」を添える（Step 12）。時差は U1 の書式のまま（秒の時差は `±HH:mm:ss`）。追跡の書き方は承認の場で直す |
| NFR 要件のレビュー Minor | 追跡の Deferred の分け方／カバレッジの行／E2E の前提の明記／D11 の直しの影響 | カバレッジは Step 15 で実測と記録。E2E の前提（利用者・ロール・権限・表を流れが作る）は Step 14 で守る。文書の直しは承認の場 |
| NFR 設計のレビュー Minor | Q1: A の影響の範囲／2択の `FormField` の axe／専用の DSL の決まり／接続の数の確かめ方 | 2択の組み方の axe を Step 12 の最初に確かめ、通らなければ legend なしの `role="radiogroup"` の形に切り替える。接続の数は `ReferenceConnectionUsageIT` に1件足す形に決める（Step 4）。200 の先頭のコメントに「200 より後のファイルは適用中の DSL に頼らない」と書く（Step 14） |
| インフラの設計のレビュー Major・Minor | スモークテストの前提／WAR の確かめ方／CI が UTC で手元が Asia/Tokyo／`e2e_event` の SQL の確かめ | 画面のテストはタイムゾーンを明示に渡し、`TZ=UTC` と `TZ=Asia/Tokyo` で同じ結果になることを1件確かめる（Step 5）。`businessDataSchema.sql` の追加は、構文を手元の見本の PostgreSQL の使い捨てのスキーマで先に流して確かめる（Step 14）。スモークテスト・WAR の確かめは Deployment の段 |

### 1.2 影響の範囲（検索で洗い出した既存のコードとテスト）

| 区分 | ファイル |
|---|---|
| 直す画面のコード | `features/tables/valueFormat.ts`・`formDiff.ts`・`formValidation.ts`・`searchConditions.ts`・`SearchPanel.tsx`・`BusinessDataFormPage.tsx`・`BusinessDataListPage.tsx`・`BusinessDataDetailPage.tsx`・`BusinessDataTable.tsx`・`rowLabel.ts`・`businessDataTypes.ts`・`businessDataParse.ts`・`messages.ts`・`references/pickerSearch.ts`・`references/referenceField.ts`（`optionText`）・`references/ReferenceSelectField.tsx`・`references/ReadonlyReferenceField.tsx`・`references/LookupField.tsx`・`references/LookupPickerDialog.tsx` |
| 書き換える画面のテスト | `valueFormat.test.ts`・`valueFormat.property.test.ts`・`formDiff.test.ts`（`datetime-local` の形・`+00:00` を付ける今の決まり）・`formValidation.test.ts`・`searchConditions.test.ts`・`rowLabel.property.test.ts`・`BusinessDataDetailPage.test.tsx`・`references/pickerSearch.test.ts`、ほか `formatCell` の引数が増えて直る部品のテスト |
| 直すサーバーのコード | `businessdata/domain/ReferenceField.java`・`ReferencePolicy.java`・`businessdata/service/ReferenceLookup.java`・`businessdata/web/ReferenceFieldResponse.java` |
| 直す・足すサーバーのテスト | `ReferenceTypesTest`・`ReferencePolicyTest`・`ReferenceLookupTest`・`BusinessDataReadControllerTest`・`ReferenceConnectionUsageIT`。`ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`・`BusinessDataBoundaryArchitectureTest` は変えずに通す |
| E2E | 足す: `frontend/e2e/200-business-data-time-flow.e2e.ts`・`support/businessDataTimeDsl.ts`。直す: `support/businessDataSchema.sql`（`e2e_event`）・`185-business-data-accessibility.e2e.ts`・`support/businessDataFixtures.ts`。E2E に `datetime-local` の操作は無い（検索で確かめた） |
| 同じ値を持つファイル（`project.md`） | 文言の鍵 `tables.form.utc` を使うのは `messages.ts`・`BusinessDataFormPage.tsx`・`pickerSearch.ts` だけ（3か所とも直す）。タイムゾーンの4か所の設定（Dockerfile・compose・テストの JVM・E2E の起動）は変えない |
| 設計との差（作りの事実） | `ReferenceField` を作るのは `ReferencePolicy` ではなく `ReferenceLookup`（service）。型は `ReferencePolicy` に足す純粋な関数（参照先のビューのラベルの列の `valueType`）で決め、`ReferenceLookup` が `choices`・`lookup` に渡す |

## 2. 手順（Step）

各 Step はチェックが付くまで終わりとしない。ストーリーとの対応は 3 節。

### サーバー（層: ドメイン → 業務処理 → API）

- [ ] **Step 1** 作業ブランチを作り、今のテストの基準を確かめる: `feature/261102-timezone-ui-polish-b4` を作る。`unit-test-instructions.md` のこの単位の命令（サーバーの `businessdata` の単体・結合、画面の `src/features/tables`・`src/shared/format`）が今の状態で通ることを確かめ、件数を記録する。
- [ ] **Step 2** ドメインの実装: `ReferenceField` に `labelValueType`（`ValueType`、`AVAILABLE` 以外は `null`）を足す。`choices(options, labelValueType)`・`lookup(picker, labelValueType)` にし、`noPermission`・`tooMany` は `null`。形の決まり（`AVAILABLE` でないときに型を持たない）を構成子で確かめる。`toString` に型の名前を足す（値・ラベルは出さない）。`ReferencePolicy` にラベルの列の型を返す純粋な関数を足す（参照先のビューの `column(labelColumn)` の `valueType`。列が無ければ `null`）。
- [ ] **Step 3** ドメインのテスト: `ReferenceTypesTest`（型の有無の形の決まり・`toString` に値が出ない）、`ReferencePolicyTest`（ラベルの列の型・列が無いとき）。流して通す。
- [ ] **Step 4** 業務処理と API の実装とテスト: `ReferenceLookup` が `choices`・`lookup` に型を渡す。`ReferenceFieldResponse` に `labelValueType`（`NON_NULL`）を足し、`toString` に型を足す。テスト: `ReferenceLookupTest`（REFERENCE・LOOKUP・NO_PERMISSION・TOO_MANY の型の有無）、`BusinessDataReadControllerTest`（JSON に `labelValueType` が `AVAILABLE` のときだけ出る）、`ReferenceConnectionUsageIT` に1件（型が付く定義の要求で接続の数が今と同じ）。`ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`・`BusinessDataBoundaryArchitectureTest` が変更なしで通ることを確かめる。

### 画面（層: 関数 → 部品 → 画面）

- [ ] **Step 5** `shared/format` の実装とテスト: `formatInstantForInput`（U1 の `formatInstant` の結果から時差を除く。読めなければ元の文字）、時差を持たない日時の初めの値（`T` を空白に）と送る形（空白を `T` に）、書式と範囲の確かめ（U1 と同じ規則。U1 の `parseLocalDateTime` に `UTC` を渡して書式と範囲だけを使う形で実装し、別の規則を作らない）。テスト: 端数 0・3・6 桁、日付の変わり目、夏時間の重なる時刻（どちらの時点でも壁時計の文字になる）、読めない値、`TZ=UTC` と `TZ=Asia/Tokyo` で同じ結果（1件。CI は UTC・手元は Asia/Tokyo）。
- [ ] **Step 6** 型と写しの実装とテスト: `businessDataTypes.ts` の `ReferenceField` に `labelValueType: ValueType | null`。`businessDataParse.ts` が項目なし・`null`・知らない値を `null` として読む。テスト: 5 通り（なし・null・`DATETIME_TZ`・`STRING`・知らない値）。
- [ ] **Step 7** 表示の書式の実装とテスト: `formatCell` にタイムゾーンの状態（`{status:'ready', timeZone}`・`{status:'error'}`）を引数で足す。`DATETIME_TZ` は `formatInstant` の `ok` の文字、`unreadable` は元の文字、`error` は短い文（種類 `unavailable`）。`DATETIME`・`TIME`・`TIME_TZ`・`DATE` は今のまま。`rowLabel` も同じ引数を受ける。テスト: `valueFormat.test.ts`・`valueFormat.property.test.ts`・`rowLabel` の書き換えと、D41 の値（日付の変わり目・端数・夏時間の前後・`+05:45`・`+09:30`・`-03:30`・読めない値・危ない文字）、整形の呼び出し回数（`error` で 0 回）。
- [ ] **Step 8** 入力の判定の実装とテスト: `resolveDateTimeInput`（文字・型 `instant`/`local`・タイムゾーン・用途 `form`/`range-from`/`range-to`/`equals`・2択の選択 → `ok`（送る値）・`invalid`・`gap`・`overlap-pending`）。範囲の始まりは先、終わりは後（Q4: A）。テスト: D41 の入力の値と、fast-check の性質（`ok` の送る値を表示の書式に戻すと入れた壁時計に戻る、範囲の重なる時刻は始まり ≤ 終わり、時差を持たない日時はタイムゾーンを変えても送る値が変わらない）。失敗の種と道を出力に残す。
- [ ] **Step 9** フォームの値の実装とテスト: `formDiff.ts`（初めの値は Step 5 の関数、送る値は Step 8 の `ok`、変えていない欄は送らない今の比べのまま、2択の選択をフォームの状態に持つ）、`formValidation.ts`（時刻の欄の確かめを `resolveDateTimeInput` に任せる）。テスト: `formDiff.test.ts`・`formValidation.test.ts` の書き換え、性質ベース（変えていない欄は送られない、時点の初めの値を時点に直すと元の時点に戻る（重なる時刻を除く））。
- [ ] **Step 10** 検索の条件の実装とテスト: `searchConditions.ts`（時点の条件を時点に直して送る、範囲の向きを `compareInstants` で、既定の値は表示のタイムゾーンが `ready` になってから壁時計の文字に直して入れる、一致の2択の選択を `SearchInput` に持つ、時差を持たない日時は `T` の区切りで送る）。テスト: `searchConditions.test.ts` の書き換えと、両端・向き・存在しない時刻・重なる時刻（範囲は自動、一致は未選択で誤り）・既定の値・ページ送りで変換が増えない。
- [ ] **Step 11** 参照の実装とテスト: `optionText` がラベルを `labelValueType`、値を参照元のカラムの型で書式にする（送る値は元の文字）。`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`（行のラベルと結果の列）も同じ。`pickerSearch.ts` の時点の一致の条件を Step 8 で直す（`tables.form.utc` を外す）。テスト: `pickerSearch.test.ts` の書き換え、型の有無、送る値が元の文字のまま。
- [ ] **Step 12** 部品の実装とテスト: まず `DateTimeField` の2択の組み方（時刻の欄の `FormField` とは別の `FormField` ＋見えない legend の `RadioGroup`）を `vitest-axe` で確かめ、違反が出れば legend なしの `role="radiogroup"` の形に切り替えて記録する。`useTablesTimeZone`・`TimeZoneNotice`・`DateTimeField`（＋CSS）・`messages.ts` の日英の鍵（`frontend-components.md` 5 節。一致の検索の案内に端数の注意）。`tables.form.utc` を消す。テスト: 3つの状態・案内の3つの文・リンクの先・誤りの文と `aria-invalid`・2択の出る／出ない・キーボードでの選択・押下の後のフォーカス・危ない文字・`vitest-axe`（誤りと2択の状態を含める）。
- [ ] **Step 13** 画面の実装とテスト: 一覧・詳細（時点の列があるときだけ待つ、案内の1行、`error` の帯と短い文）、フォーム（`ready` で開いたときのタイムゾーンを固定、`error` の間は時点の欄を持つフォームを開かず帯を出し、再読み込みの成功で開く、今の時刻は開くときに1回）、検索の画面とピッカー（時刻の欄を `DateTimeField` に）。テスト: `BusinessDataListPage`・`BusinessDataDetailPage`・`BusinessDataFormPage`・`SearchPanel`・`LookupPickerDialog` の部品のテストの書き換えと追加（状態ごと・往復で日時が送られない・タイムゾーンの差し替えで一覧の時刻が切り替わる・`waitFor` で待つ）。

### E2E・仕上げ

- [ ] **Step 14** E2E を書く（流さない。流すのは Build and Test）: `businessDataSchema.sql` に `e2e_event`（`id` の主キー・`name` の文字・`occurred_at timestamptz(6)`）を足し、構文を見本の PostgreSQL の使い捨てのスキーマ（`BEGIN … ROLLBACK`）で先に確かめる。`support/businessDataTimeDsl.ts`（版の印 `E2ET_`。`e2e_event` とメニュー）。`200-business-data-time-flow.e2e.ts`（NFR5.3.1 の流れ。利用者・ロール（`e2e_event` に FULL、作成・更新あり）・割り当てを自分で作る。初期管理者を変えない。資格情報はプロセスの環境変数。値は目印つきで値のファイルへ。更新の本文に日時の欄が無いことは要求の見張りで鍵の有無だけを見る。先頭のコメントに「200 より後のファイルは適用中の DSL に頼らない」）。`185-business-data-accessibility.e2e.ts` と `businessDataFixtures.ts` に時刻の状態（`security-design.md` 2.6 の一覧、375 px）を足す。型の検査・リンタ・書式は通す。
- [ ] **Step 15** まとめの検査: `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` を `caffeinate -dims` で包んで流し、通す。件数・カバレッジ（全体と `businessdata.domain`・`businessdata.service`・`businessdata.web`、画面の全体と `src/features/tables/`）・`verify` の時間を記録する。下限・除外は変えない。
- [ ] **Step 16** 記録: `code-summary.md`（作ったもの・差・依頼者に確かめたいこと・書き換えたテストの前と後の件数）、`source-manifest.json`、`traceability.json`。

### コミットの区切り（`project.md`: 場所・機能ごと、最初のコミットで型の検査が通る）

| コミット | 中身 | 型の検査 |
|---|---|---|
| C1 | サーバー（Step 2〜4） | Java のコンパイルとサーバーのテストが通る |
| C2 | 画面（Step 5〜13。`formatCell` の引数が呼ぶ側の全部に及ぶため、画面は1つにまとめる） | `tsc` と画面のテストが通る |
| C3 | E2E（Step 14） | `tsc`（E2E を含む）が通る |

コミットは積む前に依頼者に尋ねる（確かめたい点の問いとは別に）。

## 3. ストーリーとの対応

| Step | ストーリー・受け入れ基準 |
|---|---|
| 2〜4・11 | US2.1（AC2.1.3・AC2.1.7）・US2.5（AC2.5.3 の参照の値） |
| 5・7 | US2.1（AC2.1.1・AC2.1.2・AC2.1.4〜AC2.1.6）・US2.2（AC2.2.1・AC2.2.2） |
| 8・9・12・13 | US2.3（AC2.3.1〜AC2.3.5）・US2.2（AC2.2.3）・US2.5（AC2.5.1・AC2.5.3・AC2.5.4） |
| 10・12・13 | US2.4（AC2.4.1〜AC2.4.5） |
| 8・9 の性質ベース | US2.5（AC2.5.2・AC2.5.5 の画面の側） |
| 12・13・14 | US5.2（AC5.2.1〜AC5.2.3） |
| 14 | N6 の代表の E2E（NFR5.3.1） |

AC2.2.4 は U3、AC5.2.4 はアイコンが無いため対象外（機能設計の追跡のとおり）。

## 4. 承認の場の決定（U4 の確かめの場、2026-11-04）

依頼者が Request Changes を選び、設計の段から持ち越したレビューの指摘を文書だけで直した（機能設計・NFR 要件・NFR 設計・インフラ設計の各成果物の「承認の場の決定」の節）。**コードとテストは変えない**ため、Step 1〜16 はすべて済みのまま、新しい手順は足さない。

| 指摘 | 扱い |
|---|---|
| コード生成のレビュー R-01（E2E の未実行） | Build and Test で E2E の全体を1回流し、200 の通過・185 の axe の違反 0 件・180〜195 の通過と時間を記録する（インフラ設計の H2） |
| コード生成のレビュー R-02（D11/D12 の文書の食い違い） | 機能設計の D11・D12・W2 を、作った実装（NFR 設計の Q1: A）に合わせて直した。コードは変えない |
| コード生成のレビュー R-03（画面の `new Date()` の2か所） | 今の形で受け入れる。用途は入力の案内の今の時差で、タイムゾーンを暗に使うものではなく、時計はテストで注入できる |
| 再レビューで出た Minor（機能設計 R-07、NFR 要件 R-05・R-06、NFR 設計 R-05・R-06） | 文書の書き漏れで、コードに影響しない。次の承認の場で依頼者に示す |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/`・`nfr-requirements/`・`nfr-design/`・`infrastructure-design/`（各 `reviews/review-01.md` を含む）
- 契約・単位・要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`・`inception/units-generation/unit-of-work.md`・`inception/requirements-analysis/requirements.md`
- Bolt の計画: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/delivery-planning/bolt-plan.md`
- 今のコード（読み取りだけ）: `frontend/src/features/tables/`・`frontend/src/shared/format/`・`backend/src/main/java/cherry/mastersmith/businessdata/`・`backend/src/test/java/cherry/mastersmith/businessdata/`・`frontend/e2e/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 時刻の型（`DATETIME_TZ`）に CONTAINS・CHOICE の検索が付く DSL は今の DSL の検証で作れない見込み。付いた場合は今のまま文字として扱う（変換しない）。
- [assumption] 画面のテストの件数は見込み。Step 15 で実測して記録する。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、上の「共通の形」で確かめる。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API を上の「共通の形」で確かめる。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 他テーブル参照（REFERENCE・LOOKUP）の機能では、次のテストを必ず書く（業務データの一覧・詳細と同じ扱い）。参照先の権限の当て方（参照元の権限だけを持ち参照先の権限を欠く利用者の結果）は要件定義で決め、決めた動作を明示したテストにする。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - (a) 参照先の読み取りの認可: 参照先を読む API を、上の「共通の形」で確かめる。参照元の権限だけを持ち参照先の権限を欠く利用者には、選択肢を返さない（参照先の主権限が NONE のカラムも応答に含めない）。選択肢の取り出しの件数と検索の上限を置き、上限の境界（上限ちょうどは受け付け、超えると拒否）と対象DB の時間切れの応答を確かめる。\n  - (b) 識別子の照合: 参照先のスキーマ・テーブル・値のカラム・表示のカラム・検索の列は、読み出しのたびに適用中の DSL の名前と照らす。DSL に無い名前に差し替えた要求は拒否され、状態が変わらないこと。\n  - (c) 件数と時間切れ: 選択肢・ピッカーの取り出しの件数の上限と、JDBC の取り出しの上限を置き、上限を超える行を内部で読まないこと。\n  - (d) 漏えい: 参照先の行の値・ラベル・主キーの値と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性・エラー応答に含まれないこと。参照の値を道（URL）に載せない。\n  - (e) 検索のエスケープ: ピッカーの部分一致の検索で、`%`・`_`・エスケープの文字が一致の条件として働かないこと。\n  - (f) 画面に出す値: ラベルと値を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - (g) 参照元の書き込みでの検証: 参照先に存在しない値、権限の無い参照先の値、他の参照先を指す差し替え（IDOR）は拒否され、状態が変わらないこと。\n  - (h) 参照先の主キーの形: 複合の主キー・主キーの無いテーブル・ビュー・自己参照のときの扱いを、決めた側の動作を明示したテストにする。\n  - (i) 3種類の対象DB: 選択肢の取り出しを MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。識別子の大文字と小文字・引用符・NULL の並びと、境界の値を含める。\n  - (j) 接続の数: 選択肢の取り出しが1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT` と同じ形の結合テストで決定的に確かめる。\n  - (k) 境界テストの許可の一覧: `java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所、SpotBugs の除外、既存の境界テストが、参照先の本実装の前後で緩まず変わらないこと。\n- 参照ピッカー（LOOKUP）の画面の E2E は、流れが自分で作った参照先と参照元の前提（利用者・役割・専用のスキーマの行）だけを対象にし、見本の行と初期管理者を変えない。axe は、ピッカーを開いた状態・長い名前で、ブランドカラーとテーマのすべての組について行う。ピッカーが make-you-chic-ui の部品でないときは、キーボード操作と `aria` の属性を画面のテストで確かめる。参照先の権限を欠く利用者の確かめは E2E ではなく結合テストで行う。既存の E2E の書き換えは本数に数えない。\n- 負荷の試験（k6 の `--summary-export` など）の結果に入る認証の値（JWT・リフレッシュトークン・Authorization ヘッダー）を伏せる仕組みを入れるときは、仕組みそのものに伏せ字のテスト（JWT の形が残らない、すでに伏せた入力に流しても結果が変わらない（冪等）、入れ子の JSON・配列・長い文字列の中でも伏せる、JWT 以外の値（数値・場面名）を壊さない）を付け、`./gradlew verify` と CI に入れる。記録（`evidence/`）に置いたものには `gitleaks dir` を流して確かめる。仕組みの置き場は設計の段で決める。\n\n- 業務データの日時のタイムゾーンの変換を扱う機能では、次のテストを必ず書く（業務データの一覧・詳細と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 3種類の対象DB と型: MySQL・MariaDB の `datetime`・`timestamp`・`date`・`time`、PostgreSQL の `timestamp`・`timestamptz`・`date`・`time`・`timetz` のすべてを、一覧・詳細・作成・更新（と検索・主キー・参照の値の往復）で結合テストで確かめる。\n  - 日付の変わり目: 変換で日付が変わる値（例: UTC の 15:00 が日本時間の翌日 0:00 になる値、月末・年末・うるう日）を含める。\n  - 夏時間のある地域と端数の時差: 夏時間の切り替わりの前後（存在しない時刻・重なる時刻）と、分の端数を持つ時差（`+05:45`・`+09:30`・`-03:30` など）を含める。★表示に使う時差を利用者ごとに選べるか（選べないなら、夏時間の地域の確かめは変換の部品の単体テストに限る）\n  - 秒の端数: 0・3・6 桁の端数が、読み・書き・往復で落ちたり丸められたりしないこと（K-98）。\n  - 範囲の端: MySQL の `timestamp` の上限（2038 年）・`time` の 24 時間を超える値・負の値など、読めない値の扱いを決めた側の動作で確かめる（K-99）。\n  - 往復: 読んだ値をそのまま更新に送っても、対象DB の値が変わらないこと（同時の更新の判定の値を含む）。\n  - 性質ベースのテスト: 変換の関数（サーバー側・画面側）は純粋な関数にし、jqwik・fast-check で性質（変換して戻すと元の時点に戻る、時差の無い型は時差を変えても値が変わらない など）を確かめる。\n- 日時を扱うテストは、実行する機械の既定のタイムゾーンに結果を預けない。バックエンドはテストの JVM の `user.timezone`（今は `Asia/Tokyo`）に頼る処理を変換の部品に持ち込まず、時差（`ZoneId`）と時計（`Clock`）を注入して確かめる。画面のテスト（Vitest）と E2E（Playwright の `timezoneId`）は時差を固定し、別の時差を確かめるテストはその時差を明示に指定する（手元は `Asia/Tokyo`、CI の機械は UTC のため）。\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。 (merged 2026-11-04)\n- k6 の扱い: `k6 inspect` には `--include-system-env-vars` を付ける（無いと場面の名前が undefined になる）。colima の VM の時計が負荷の間にずれて `http_req_duration` が崩れるため、判定は1回の繰り返しに要求1つの場面の `iteration_duration` で行い（トークンは setup() で取る）、`http_req_duration` は並べて記録する。操作が2つ以上の場面は op のタグが `iteration_duration` に付くかを台本で確かめる。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (merged 2026-11-04)\n- 使い捨ての環境を消す前に、確かめの結果（問い合わせの結果・ログの WARN の件数と内訳など）を見て、取り直しが要らないことを確かめてから片付ける。消す前に数える項目は、台本に最初から書いておく。 (merged 2026-11-04)\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)\n- 負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:39cbc2af675a30d1ded8cb11b7357bf61c8d067539a082a327a8e577ae71a285",
  "contract_sha256": "sha256:3de25d424318f33fbd47f90eb592bafe14b0a583125a25b5874c2d53613d3f8c"
}
```
