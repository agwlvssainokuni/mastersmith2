# セキュリティとその他の非機能の設計 — u4-business-data-time

出典: NFR 要件（`construction/u4-business-data-time/nfr-requirements/security-requirements.md` の NFR3.1.1〜NFR6.1.1、レビュー `nfr-requirements/reviews/review-01.md`）、機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`、レビュー `functional-design/reviews/review-01.md`）、契約（`inception/contract-design/contract-summary.md` の C1・C2・C4）、質問と答え（`nfr-design-questions.md`: Q1: A・Q2: Looks correct）、make-you-chic-ui の部品のソース（`FormField`・`useFieldProps`・`RadioGroup`。読み取りだけ）、`team.md`・`project.md`。

## 1. 防御の層（どこで何を守るか）

| 層 | 守るもの | 守り方 |
|---|---|---|
| サーバーの定義の応答 | 参照先の情報 | `labelValueType` は `AVAILABLE` のときだけ。型の名前だけで値・ラベルを足さない（2.1） |
| サーバーのログ・トレース | 業務データの値 | 応答の record の `toString` は値・ラベルを出さない（今のまま）。SQL と例外の扱いは変えない（2.2） |
| 画面の描画 | 画面の構造 | 値・名前を文字として描く。リンクの先は定数（2.3） |
| 画面の入力 | 送る時点の正しさ | 書式・範囲・夏時間を画面で確かめ、時点（UTC の時差つき）だけを送る。サーバーは今の確かめのまま（2.4） |
| 画面・E2E の記録 | 業務データの値・資格情報 | コンソール・誤りの記録・E2E の題・注記・添付・報告に値を出さない（2.5） |

## 2. 要件ごとの設計

### 2.1 NFR5.1.3・NFR4.2.1: `labelValueType` の出し方と互換

- `ReferenceField`（domain）に `labelValueType`（`ValueType`、無いときは `null`）を足す。作るのは `ReferencePolicy` の `AVAILABLE` の経路だけで、`NO_PERMISSION`・`TOO_MANY` の作り方（今の静的な作り方）は `null` のまま。
- `ReferenceFieldResponse` に `labelValueType` を足し、`@JsonInclude(NON_NULL)`（`options`・`picker` と同じ）。`toString` は種類・状態・選択肢の数・ピッカーの有無に、型の名前を足してよい（値・ラベルは出さない）。
- 画面の写し（`businessDataParse.ts`）は、項目が無い・`null`・`VALUE_TYPES` に無い文字を `null` として読み、応答を拒否しない。
- 確かめ: `ReferencePolicyTest`（状態ごとの有無と値、ラベルの列の型と一致）、応答の JSON の形の単体テスト、画面の写しのテスト（無い・`null`・知らない値）。既存の `ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`・他テーブル参照の認可のテストが変更なしで通る。

### 2.2 NFR5.1.4（サーバー側）: ログ・トレースに値を出さない

- サーバーで変えるのは record の項目の追加だけで、SQL・例外・ログの文を足さない。`TraceAspect` が出す `ReferenceField`・`ReferenceFieldResponse` の文字列は、型の名前が増えるだけ。
- 境界テスト（`BusinessDataBoundaryArchitectureTest`・既存の他の境界テスト）と SpotBugs の除外は変えない・緩めない。

### 2.3 NFR5.1.5: 画面に出す値

- 時刻の値・読めない値の元の文字・参照のラベル・タイムゾーンの名前・時差は、React の文字のノードとしてだけ描く（`dangerouslySetInnerHTML` を使わない。lint の `react/no-danger` で止まる）。文言の差し込み（`{zone}`・`{offset}`）も文字として差し込む。
- 案内の1行のリンクの先は、プリファレンスの登録の道の定数だけ。値・名前から道を作らない。
- 確かめ: `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含む読めない値・ラベル・タイムゾーンの名前で、要素が増えず文字として出ること（画面の単体テスト）。

### 2.4 入力の確かめ（NFR5.1.1 の夏時間・範囲・往復の部分）

- 書式・範囲・夏時間の判定は U1 の `parseLocalDateTime` だけ。送るのは `ok` の時点だけで、`invalid`・`gap`・未選択の `overlap` では送らない（保存・検索をさせない）。
- サーバーは今の確かめ（読めない値は `VALIDATION_FAILED`、DSL に明示した桁を超える端数は `TOO_MANY_DIGITS`）のまま。夏時間で存在しない時刻を API を直接呼んで送る人の値は、別の時点として保存されうる（ADR-002 で受け入れた危険。脅威の表）。
- 往復: 主キーは `RowKey`、参照の値は返された `value` の文字のまま送り返す。更新で初めの値の文字のままの欄は送らない。

### 2.5 NFR5.1.4（画面側）: 値を記録に出さない

- 新しい部品は `console` に何も出さない（lint の `no-console` の今の設定のまま）。変換の結果の誤りは種類（`invalid`・`gap`・`overlap`）だけで扱い、値を誤りの記録・例外の文に入れない。
- E2E の 200 は、作った行の値に目印を付けて値のファイル（180 と同じ `recordSecretValues` の仕組み）に書き、`test.step` の題・注記・添付に値を入れない。資格情報はプロセスの環境変数で受け、`webServer.env` に置かない。報告の json を文字列で検索し、値・資格情報が入っていないことを確かめる。

### 2.6 NFR3.1.1〜NFR3.1.3: アクセシビリティ

- **時刻の欄**: `FormField`（ラベル・必須・案内・誤り）の中に `TextInput` を1つだけ置く。`FormField` が `id`・`aria-describedby`・`aria-invalid` を結ぶ。誤りの文は `FormField` の `role="alert"` で読まれる。誤りの間は案内が出ないため、存在しない時刻・重なる時刻の文にタイムゾーンの名前を入れ、読めない値の文に書式を入れる（Q2: Looks correct の 2）。
- **2択**: 時刻の欄の `FormField` の外に、もう1つの `FormField`（ラベル「{名前} では、この時刻は2回あります。どちらですか」、未選択の誤り「どちらの時刻かを選んでください」）を置き、中に見えない legend の `RadioGroup` を1つだけ置く（今のフォームのラジオと同じ組み方。`FormField` に入力を2つ入れると同じ `id` が2つの部品に付くため）。この組み方が axe を通るかはコード生成の最初に vitest-axe で確かめ、通らなければ legend なしの `role="radiogroup"` の形へ切り替える（コード生成で確かめ、誤り・選択済みの状態で違反 0 だった。実際のブラウザの axe は 185 で Build and Test に確かめる）。保存・検索の押下で未選択なら、2択の最初の選択肢へフォーカスを移す（legend はフォーカスを受けないため。読み上げは fieldset の名前と誤りを読む。Q2: Looks correct の 1）。
- **読み込みの失敗**（Q1: A）: 時点を表す型の欄を持つフォームは開かず、読み込み中の表示の位置に失敗の文と `DisplayTimeZoneErrorBar`（「再読み込み」）を出す。一覧・詳細は帯と短い文（D11 のまま）。影響の範囲: 失敗の間は、時点を表す型の欄を持つ表の作成・更新が一切できない（時点に触れない更新、例えば名前だけの直しも含む）。利用者は帯の「再読み込み」で戻る。依頼者は Q1: A でこの形を選び、U4 の確かめの場（2026-11-04）の Request Changes でもコードを変えない（文書だけを直す）とした。
- **axe（185 に状態を足す）**: 一覧・詳細（案内の1行の3つの文、端数 6 桁・`+05:45` の値、長いカラムの名前、読めない値）、作成・更新（読めない値の誤り・存在しない時刻の誤り・2択の未選択の誤り・選択済み）、検索（時刻の条件の誤り）、表示のタイムゾーンの読み込みの失敗の帯。ブランドカラーとテーマのすべての組。375 px で `scrollWidth` が画面の幅を超えないこと。規則・要素を外さない。
- **部品のテスト**: キーボードだけで入力 → 2択の選択（矢印・Space）→ 保存。押下の後のフォーカスの行き先。`vitest-axe` を部品ごとに1件（誤りと2択の状態を含める）。

### 2.7 NFR5.1.1・NFR5.1.2・NFR5.2.1: テストの形

- 画面の単体テストは、タイムゾーンと今の時刻を引数（props か注入の関数）で渡し、機械の既定のタイムゾーンに頼らない。Node の `TZ` を変えても同じ結果になることを、U1 と同じ形で1件確かめる。
- 性質ベースのテスト（fast-check）は、初めの値・送る値・検索の条件を作る純粋な関数に当てる（D43 の4つの性質）。失敗したときの種と道（`seed`・`path`）を出力に残す。
- E2E は Playwright の `timezoneId` を今の値のまま（`Asia/Tokyo`）にし、流れの中でプリファレンスの表示のタイムゾーンを `America/New_York` にする（ブラウザと表示のタイムゾーンが違う状態を通す）。

### 2.8 NFR5.3.1: E2E の代表の流れ（200）

- ファイル: `frontend/e2e/200-business-data-time-flow.e2e.ts`（Q2: Looks correct の 6）。
- 表: E2E の実行のスキーマの初期化（`support/businessDataSchema.sql`）に `e2e_event`（主キー・名前・時点を表す型の6桁のカラム）を足す。行は流れが作る分だけ（見本の行は置かない）。
- DSL: 共有の `businessDataDsl.ts` は変えず、この流れの専用の DSL（版の印 `E2ET_`）を作り、流れの中で投入・適用する。180・190・195 の DSL・見本・メニューの件数に影響しない（180 より後のファイルは適用中の DSL に頼らない今の決まりのまま）。決まり: 200 は E2E の最後に流れる前提で、適用中の DSL を専用のものに置き換える。200 より後の番号の流れは足さない（足すなら先頭で自分の DSL を適用し、適用中の DSL に頼らない）。ファイルの先頭のコメントにこの決まりを書く。表 `e2e_event` を足した後も 180・185・190・195 が通ることは、Intent の最後の E2E で確かめる（既存の DSL は表を明示に列挙するため、表の追加は既存の流れに影響しない）。
- 前提: 利用者・ロール（`e2e_event` に FULL、作成・更新あり）・割り当てを流れが自分で作る。初期管理者の状態・ロールは変えない。後始末はしない（180 と同じ）。
- 流れ: NFR5.3.1 のとおり（プリファレンスで `America/New_York` → 存在しない時刻の誤り → 重なる時刻を「後（-05:00）」で保存 → 一覧で `-05:00` → 名前だけ直して更新し、日時が変わらない）。日時が変わらないことは、一覧の表示と、更新の要求の本文に日時の欄が無いこと（要求を見張って確かめる。値は記録に出さない）で確かめる。

### 2.9 NFR6.1.1・NFR5.1.6

- 文言の鍵は `features/tables/messages.ts` に日英で足し、使わなくなる `tables.form.utc` は消す（ほかで使っていないことをコード生成の計画で確かめる）。
- 新しい依存は足さない。lockfile に差が出ないことを確かめる。

## 3. 脅威と対応

| 脅威 | 対応 | 残る危険 |
|---|---|---|
| 参照先の型の推測 | `AVAILABLE` のときだけ（2.1） | 参照先を読める利用者には、ラベルの列の型が見える（ピッカーの列の型と同じ扱い） |
| 値の漏えい | サーバーはログの文を足さない。画面は記録に出さない（2.2・2.5） | なし（既存の漏えいのテストで確かめる） |
| HTML の差し込み | 文字として描く（2.3） | なし |
| 夏時間の誤った時点の保存 | 画面で拒否・2択（2.4） | API を直接呼ぶ人の存在しない時刻（ADR-002 で受け入れた） |
| 読み込みの失敗の間の入力の意味の変わり | 失敗の間はフォームを開かない（Q1: A） | なし |

## 4. 上流との差（承認済みの文書は書き換えず、ここに記録する）

| # | 差 | 扱い |
|---|---|---|
| 1 | 機能設計の D11 は、読み込みの失敗のときもフォームを出し時点の欄だけ使えない状態にするとしたが、Q1: A で、時点を表す型の欄を持つフォームは失敗の間は開かない形にした（機能設計のレビューの Major の直し）。一覧・詳細の扱いは D11 のまま | 機能設計は承認の場の Request Changes で D11 を直す。この段はこの形で書く |
| 2 | interaction-spec.md の4節は、2択が未選択のとき legend へフォーカスを移すとしたが、2択の最初の選択肢へ移す（legend はフォーカスを受けない） | 部品の作りによる補足 |
| 3 | `frontend-components.md` の 3 節は、時刻の欄の下に2択を置くとしたが、時刻の欄の `FormField` とは別の `FormField` に置く（同じ `id` が2つの部品に付くのを避ける） | 部品の作りによる補足 |

## 5. 承認の場の決定

U4 の確かめの場（2026-11-04）で、依頼者が Request Changes を選び、レビュー（`reviews/review-01.md`）の指摘をまとめて直した。差1の機能設計の D11 は、同じ場で Q1: A の形に直した（`functional-design/functional-spec.md` の 8 節）。

| 指摘 | 直したところ |
|---|---|
| R-01 読み込みの失敗の影響の範囲 | 2.6 に、失敗の間は時点を持つ表の作成・更新が一切できないことと、コードを変えない決定を書いた |
| R-02 2択の組み方の axe | 2.6 に、コード生成の最初の確かめと代案、確かめの結果（vitest-axe で違反 0）を書いた |
| R-03 200 の DSL の決まり | 2.8 に、200 を最後に流す前提と、200 より後の流れの決まり、表の追加の確かめを書いた |
| R-04 接続の数の確かめの形 | `performance-design.md` の 1.3 を、`ReferenceConnectionUsageIT` に1件足す形に決めた（コード生成で足した） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/security-requirements.md`・`reviews/review-01.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`・`reviews/review-01.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-design/nfr-design-questions.md`（[Q1] A・[Q2] Looks correct）
- 部品のソース（読み取りだけ）: `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/FormField.tsx`・`useFieldProps.ts`・`RadioGroup/RadioGroup.tsx`
- 今のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/`（`domain/ReferenceField.java`・`domain/ReferencePolicy.java`・`web/ReferenceFieldResponse.java`）、`backend/src/test/java/cherry/mastersmith/businessdata/`（`*SecretLeakIT`・`BusinessDataBoundaryArchitectureTest`）、`frontend/e2e/180-business-data-flow.e2e.ts`・`185-business-data-accessibility.e2e.ts`・`support/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 流れの専用の DSL を投入・適用しても、190・195 は自分の前提を作り直すため影響を受けない（180 の「後のファイルは適用中の DSL に頼らない」の決まり）。コード生成の計画で、200 の後に流れるファイルが無いか・あれば適用中の DSL に頼らないかを確かめる。
- [assumption] 要求を見張って更新の本文に日時の欄が無いことを確かめる形は、Playwright の要求の見張り（`page.waitForRequest`）で、本文の値を記録に出さずに鍵の有無だけを見る。
