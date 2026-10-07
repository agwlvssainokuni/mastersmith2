# コードの要約（Code Summary）— U2 DSL の書式の版 2（dsl-v2、Bolt B1）

計画: `code-generation-plan.md`（同じディレクトリ）。Step ごとのコマンドと結果は `generation-notes.md`。方法は test-after（Testing Contract のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した）。作業ブランチは `feature/261004-role-menu-b1`（`develop` の 65a76c5 から。計画の承認の記録は 3b7e219）。コードはまだコミットしていない（コミットの区切りは Step 28 で依頼者に提案する）。

## 1. 作ったもの・変えたもの

アプリのソースの道の一覧は `source-manifest.json`（111 件。`dsl-schema-v1.json` の削除と `dsl-schema-v2.json` を含む）。

### 1.1 バックエンドの本体

| 場所 | 種類 | 中身 |
|---|---|---|
| `dsl/parse/`（`YamlLimits` を足す、`SafeYamlParser`・`LimitingParser`・`YamlTreeConverter`） | 足す・変える | 読み込みの上限（大きさ・深さ・別名・展開後の節）を引数で受ける形。`dsl.parse` は `DslFormat` の上限の定数を読まない |
| `dsl/service/`（`SafeYamlReader`・`DefaultSafeYamlReader`・`SafeYamlLimits`・`SafeYamlResult`・`SafeYamlRejectionKind`・`YamlPositions` を足す、`DslReader`・`DefaultDslReader`・`DslModelMapper`） | 足す・変える | 上限つきの安全な YAML の読み込みの口（U4 role が使う。結果の型で返し、例外の文と木の中身を出さない）。版 2 の読み方と、起動時だけ深さを外して枝を落とす `readAtStartup` |
| `dsl/domain/`（`DslSchema`・`TableRef`・`MenuDepth`・`DslStartupReadResult` を足す、`DslFormat`・`DslModel`・`DslMenuItem`・`DslMessageKeys`） | 足す・変える | 版 2 のモデル（スキーマの階層、メニューの組 `{schema, name}`、`findTable`）、深さの数え方と枝の落とし方、版 2・深さ 5・v2 の道の定数、深さ・スキーマの数の鍵 |
| `dsl/validate/DslSemanticValidator.java` | 変える | スキーマの数・メニューの組・深さ・同じスキーマの中の参照の検証 |
| `resources/dsl/dsl-schema-v2.json`（`dsl-schema-v1.json` から `git mv`） | 変える | 版 2 の JSON Schema（`const: 2`、`schemas`、メニューの組） |
| `dslmanage/generate/DslTreeBuilder.java` | 変える | 既定の DSL を版 2 で生成（設定のスキーマ名を1つ、平らなメニューの組） |
| `dslmanage/domain/`（`PreviewView`・`DslStatus`） | 変える | 要約のスキーマの数、スキーマの階層の違い、メニューの組、`SCHEMA_MISMATCH`、`appliedUnreadable` |
| `dslmanage/service/`（`UnreadableAppliedRevision` を足す、`DslLifecycle`・`DslStartupLoader`・`DslDiffCalculator`・`DslSummaryCalculator`・`DslReconciler`・`DslErrorMessages`） | 足す・変える | 読めない適用中の版の置き場、プレビューの表示と適用の前の読み直し（通らなければ 422）、起動時の WARN（深すぎる枝）、スキーマごとの違いと要約、名前の違うスキーマの `SCHEMA_MISMATCH`、版の誤り・深さ・スキーマの数の文言 |
| `dslmanage/web/DslResponses.java` | 変える | 今の状態の応答に `appliedUnreadable` |
| `resources/application.yaml` | 変える | `server.tomcat.max-swallow-size: 11MB`（9節 D-10） |
| `backend/build.gradle.kts` | 変える | JSON Schema の名指しを v2 に（3か所とコメント）。下限・除外・タスク・テストの JVM は変えていない |

### 1.2 バックエンドのテスト

- 足した: `MenuDepthTest`・`MenuDepthPropertyTest`（jqwik）・`SafeYamlReaderTest`・`SafeYamlReaderSecretLeakIT`・`UnreadableAppliedRevisionTest`。
- 版 2 に書き換えた: `dsl` の層（`DslModelTest`・`DslResultTypesTest`・`DefaultDslReaderTest`・`DslSchemaValidatorTest`・`DslSemanticValidatorTest`・`SafeYamlParserTest` ほか）、`dslmanage` の層（`DslLifecycleTest`・`DslStartupLoaderTest`・`DslReconcilerTest`・`DslSummaryAndDiffTest`・生成のテスト・`DslAdminApiIT`・`DslStartupIT`・`DslTargetDbIT` ほか）、見本 `valid-sample.yaml`・`dept_mst.yaml`、手伝い `DslYaml`・`DslSamples`・`ValidationTestSupport`。
- 既存の ArchUnit の境界テストは変えていない（`DslBoundaryArchitectureTest` などはそのまま通る）。

### 1.3 画面

- `frontend/src/features/dsl/`: `api/types.ts`・`diffCounts.ts`・`DslStatusPanel`（読めない適用中の注意）・`DslPreviewPanel`（読めないプレビュー）・`DslWarningList`（`SCHEMA_MISMATCH`）・`DslConfirmDialog`（スキーマの件数の行）・`DslMenuTree`（「スキーマ名.テーブル名」）・`DslDiffTable`（スキーマの見出しの行）・`useDslAdmin.ts`（`previewInvalid`）・`DslAdminPage.tsx`・`messages.ts`・`submitInput.ts`（v2 の道）、既存の部品の直し `DslErrorList`・`DslStatusPanel.css`（4節 D-20・D-21）。
- テスト: 上の部品のテストの書き換えと追加、`diffCounts.test.ts`（新規）。
- E2E: `040-dsl-admin.e2e.ts` を版 2 に書き換え（版 1 の投入の確かめを1か所足した）、`135-dsl-admin-accessibility.e2e.ts`（新規。操作の流れではないため本数に数えない）。

### 1.4 負荷の道具と文書

- `perf/make-large-dsl.mjs`・`perf/make-pattern-dsl.mjs`・`perf/ui/dsl-ui-lang.mjs`・`perf/ui/dsl-ui-timing.mjs` を版 2 に。`perf/dsl-timing.sh` に `--busy` とヒープの使用の記録（`heap.tsv`）を足した。`perf/k6/scenarios.js`・`perf/dsl-timing-report.mjs` は変えていない。
- `README.md`（DSL の書式・生成・API・画面の節）・`perf/README.md`。

## 2. 実装で決めたこと

- 名前を案として置いた型・鍵・ログのキーは、計画の案のとおりにした（`MenuDepth`・`SafeYamlLimits`・`SafeYamlResult`・`SafeYamlRejectionKind`・`YamlPositions`・`DslStartupReadResult`・`YamlLimits`・`UnreadableAppliedRevision`、`dsl.prunedMenuItems`・`dsl.menuDepthLimit`）。9節 D-3 の変更は無い。
- `SafeYamlResult` の `toString` は、読めたときは JSON の木の節の数だけ（キーは数えない）、拒否のときは区分と行・列だけ（場所の道は利用者の書いたキーを含みうるため出さない）。
- 深さは `MenuDepth.overDepth` の1つの数え方を、意味の検証（JSON の木）とモデル（`DslMenuItem` の木）の両方で使う。枝を落とす `prune` は、子がすべて元のものと同じ（同一性）ときだけ元の項目を残す（性質ベースのテストが見つけた誤りの直し）。
- 適用の確かめの順は計画 D-4 のとおり（409 を 422 より先、確定の後は事前のモデルを使い履歴の本文を読み直さない）。プレビューの表示は、キャッシュに無ければ通常の読み方で読み直し、通らなければ 422 でプレビューの行を残す（今までの 500 の経路を無くした）。
- 照合はスキーマ名が大文字・小文字を区別して同じときだけテーブルを比べる。違えば `SCHEMA_MISMATCH` を1件（文には DSL のスキーマ名だけ）。
- 画面の `previewInvalid` は `preview` より優先し、計画 D-6 の契機で消す。`SCHEMA_MISMATCH` は「照合できなかった」に入れて件数に数えない（D-12）。

## 3. テストの量とカバレッジ

（Step 26・27 の実測は下の 3.1・3.2 に書く）

- 層ごとの実行（各 Step、`generation-notes.md`）: バックエンドの単体は変更の前の 1,577 件から 1,646 件（+69）、`dsl`・`dslmanage` の結合は 92 件（Step 15）。画面の `features/dsl` と `apiClient.download.test.ts` は 16 ファイル・138 件から 18 ファイル・172 件（+34）。E2E は 040（1 件）と 135（24 件）が通過。
- 性質ベースのテスト: `MenuDepthPropertyTest`（jqwik、5 性質 × 300 回）。生成の既存の性質ベースのテストにスキーマ名とメニューの組の確かめを足した。

### 3.1 1コマンドの検査（Step 26）

colima の環境変数を付けた `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（9 分 41 秒。B2 の 13 分 56 秒より短い。1回目で通過）の実測:

- テストの件数: バックエンドの単体 1,646 件（基準 1,577 件から +69）・結合 723 件（基準 712 件から +11）、失敗 0・飛ばし 0（対象DB の3種類も実行）。画面 113 ファイル・1,043 件 passed。
- バックエンドのカバレッジ（Step 3 の基準 → 今回、行・分岐）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `dsl.domain` | 100.0% → 100.0% | 94.8% → 96.1% |
| `dsl.parse` | 98.7% → 98.3% | 92.1% → 89.6% |
| `dsl.service` | 98.5% → 96.6% | 100.0% → 92.4% |
| `dsl.validate` | 97.4% → 97.7% | 90.7% → 91.5% |
| `dslmanage.domain` | 100.0% → 100.0% | 92.3% → 92.3% |
| `dslmanage.generate` | 99.3% → 99.4% | 100.0% → 100.0% |
| `dslmanage.service` | 100.0% → 97.8% | 98.5% → 97.2% |
| `dslmanage.web` | 100.0% → 100.0% | 86.7% → 86.7% |
| `dslmanage.repository`（記録だけ） | 100.0% → 100.0% | 100.0% → 100.0% |
| 全体 | 98.9% → 98.7% | 94.8% → 94.6% |

- 下がった3つ（どれも下限 行 80%・分岐 70% の上）: `dsl.parse` は上限の型 `YamlLimits` の範囲外の確かめなど、`dsl.service` は新しい `SafeYamlResult.Rejected`・`DefaultSafeYamlReader`・`YamlPositions.Location` のテストで通らない防御の道、`dslmanage.service` は `DslLifecycle` の適用で事前のモデルが無いときの道（計画 D-4）と `DslErrorMessages` の分岐。
- 画面の全体: 行 97.52%・分岐 92.68%。
- WAR の中の JSON Schema の照合（段 9 `verifyDslSchemaInWar`）は通り、WAR には v2 の2か所だけがある。4.8 のファイルに差は無い。下限・除外・テストの JVM のヒープは変えていない。

### 3.2 E2E（Step 27）

`caffeinate -i ./gradlew e2eTest`（6 分 53 秒）: 14 ファイル・177 件 passed、失敗 0・飛ばし 0・不安定 0（B2 の 153 件に 135 の 24 件）。報告の確かめの道具は見つかった件数 0。報告の置き場は消した。

## 4. 計画との差

| # | 差 | 理由・扱い |
|---|---|---|
| D-17 | Step 4 で `dsl/validate/ValidationTestSupport.java` の呼び出し1行を、Step 9 より先に上限を渡す形に直した | 上限の引数化でテストがコンパイルできなくなるため（計画 4.4 の一覧に載るファイル）。上限の値はテストの手伝い `DslSamples.DSL_YAML_LIMITS`（`DslFormat` の値） |
| D-18 | 意味の検証の誤りの並びを「スキーマの数 → メニューの組 → 深さ → スキーマごとのテーブル（同じスキーマの中の参照 BR1.5 と既存の決まり）」にした（計画の段の順は BR1.3 → BR1.5 → BR1.6 → BR2.1 → 既存） | BR1.5 はテーブルごとの既存の確かめの中で行うため。誤りはすべて返すので件数と中身は同じで、並びだけが違う。承認の場で確かめる |
| D-19 | Step 8 の型の追従の時点で、Step 9 の `dsl` の層のテストの書き換えを先に済ませた | テストのソースは `dsl` と `dslmanage` を一緒にコンパイルするため。層ごとのテストの実行の順（`dsl` → `dslmanage`）は守った |
| D-20 | 既存の `DslErrorList` の表の包みに `role="region"`・`tabIndex={0}`・読み上げの名前（ja・en）・フォーカスの見た目を足した。oxlint の `jsx-a11y/no-noninteractive-tabindex` は、その1行だけを行の除外のコメント（日本語の理由つき）で外した。`.oxlintrc.json` と ESLint の設定は変えていない。テストを4件足した | 依頼者の決定 A（2回）。135 の 360px で axe の `scrollable-region-focusable`（場所の長い誤りで表が横に動く）が出たため。計画 4.5 に無い既存の部品の直し。目標と幅は変えていない |
| D-21 | 既存の `DslStatusPanel.css` の適用中の行を 360px で折り返す形にした（値の列を `minmax(0, 1fr)`・`min-width: 0`・`overflow-wrap: anywhere`、識別を区画の中だけで折り返し、隠した全文の識別を識別の中に置く） | 依頼者の決定 A。135 の 360px で文書の横幅が 2px はみ出したため（骨組みのサイドバー 220px が 360px でも出たままで、本文の列は約 108px）。jsdom では幅を測れないため 135 で確かめた |
| D-22 | `dsl.confirm.schemaCounts` の ja を「スキーマ 増えた {{added}}・減った {{removed}}・変わった {{changed}}」にした（計画の案は「増・減・変」） | 計画の「文は既存の `dsl.confirm.tableCounts` にそろえる」に従った |
| D-23 | 計画 4.6 の `DslStatusPanel.test.tsx` の「注意があってもダウンロードできる」は、注意があっても「スキーマを読み込む」が押せることと適用中の行が残ることを確かめる形にした | 適用中のダウンロードは今の状態の区画ではなく履歴のタブにあり、この単位では変えないため |
| D-24 | 計画の「3種類の DB で生成した DSL にスキーマ名を書き接続の項目を書かない」は、3種類を `AbstractDefaultDslGeneratorIT`（MySQL・MariaDB・PostgreSQL）で確かめ、`DslTargetDbIT` は今までどおり PostgreSQL の1種類で照合と `SCHEMA_MISMATCH` を確かめた | `DslTargetDbIT` は前から PostgreSQL だけのテストのクラスのため |
| D-25 | `perf/ui/dsl-ui-timing.mjs` の結果の `diffExpand` に `schema` と `table` を分けて書く | 違いの表の行の識別が「スキーマ/テーブル」になったため |
| D-26 | Step 24 の短い試走の前に colima を再起動した（`colima stop` → `colima start`、設定は変えない）。配備したアプリ・mailpit・見本の対象DB は止まり、`docker start` で起こし直した（作り直していない、ボリュームは触れていない） | VM の中でホームの共有（sshfs）が外れていて、使い捨ての環境の対象DB の初期化の SQL が読めなかったため。依頼者の決定 B |

計画 9節の D-1〜D-16 はそのとおりに作った（D-16: `max-swallow-size` は Step 24 の `--busy` で、10 MiB の2つ目に 503 `DSL_BUSY` が送り手に届いた（curl の終了コード 0）ことを確かめた）。承認済みの設計の文書は書き換えていない。

## 5. 依頼者に確かめたいこと・承認の場で確かめること

1. 計画との差 D-17〜D-26（4節）。特に D-18（誤りの並び）と D-20（oxlint の1行の除外）。
2. 計画 10節 Q5 の答えの読み方（適用の確かめにスキーマの件数を「出す」で反映した。計画の初版の選択肢の表記とは A・B が逆だった）。
3. Step 24 のヒープの使用（記録のみ）が、10 MiB の投入の後 735,051,776 バイト・表示の後 752,877,568 バイト・`--busy` の後 835,715,072 バイトで、計画の比べ先（T4 の 256〜384 MB）より大きい。値はその時点の使用量で GC の前のごみを含むため、Build and Test で GC の記録（`gc.log`）の GC の後の値と合わせて判定する。`memory.peak` は終わりに 1,603,629,064 バイト（上限 2g の内）、OOMKilled は無かった。
4. `perf/dsl-timing.sh --lang`（`perf/ui/dsl-ui-lang.mjs`）が Step 24 で失敗した。英語のロケールのブラウザーでも画面が日本語で出て、「Submit」のタブが見つからなかった。ログインの後は、利用者の表示の設定（サーバーに保存した言語）がブラウザーの言語より優先されるため（Intent 260925-user-management の表示の設定の後、前から動かなくなっていたと見立てる。台本の最後の変更は 2026-09-24）。版 2 の変更とは関係しない。台本でその利用者の表示の言語を英語にする手順を足す直しは計画に無いため行っていない。Build and Test の NFR2.8 の確かめの前に直すかを決めたい。
5. 135 の 360px で見つかった既存の作りの問題（D-20・D-21）は、DSL の画面だけを直した。ほかの管理の画面（招待・利用者の管理など）の誤りの一覧や識別の表示が 360px で同じ問題を持つかは確かめていない。

## 6. 既知の制約（NFR 設計 R-07）

- 10 MiB の DSL の読み込みで OutOfMemoryError（`Error`）が起きたときは、既存の例外の変換の外のため、500 の Problem Details の整形は保証されず、アプリ全体（ほかの要求）に響きうる。重い処理は同時に1つ（`DslHeavyOperationGate`）で、2つ目の投入は本文を読まずに 503 で断るため、DSL の投入どうしが重なってヒープが2倍になることは無い。
- (b) 再起動の直後の適用と投入の重なり、または DSL の投入と role の権限の YAML の確かめの重なりの合計は、見積もりで約 490〜710 MB（ヒープの上限 1 GiB の内）。重ねる確かめは Build and Test の任意の項目に先送りした（role の B6 の後）。

## 7. 後の Intent に回すこと（Functional Design R-07）

- 起動時に深すぎるメニューの枝を落としても、管理者の画面には出ない（WARN のログだけ。AC5.2.3 の範囲内）。画面で知らせるかは後の Intent の候補。

## 8. Build and Test・配備の段に引き継ぐこと

計画の「Build and Test に引き継ぐこと」「配備の段へ引き継ぐこと」のとおり。加えて、5節の3（ヒープの読み方）と4（`--lang` の台本）。Step 24 の結果は `build/perf-results/dsl-v2-trial/`（git の対象外。秘密の値は含まない）に残した。
