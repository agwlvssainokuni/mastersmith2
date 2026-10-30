# セキュリティとアクセシビリティの設計 — U3 u3-reference-screens

U3 の NFR 要件のうち、セキュリティ（NFR1.41〜NFR1.47）・アクセシビリティ（NFR4.41〜NFR4.45）・テスト（NFR5.41〜NFR5.43）・多言語（NFR6.41・NFR6.42）・互換性（NFR7.41）・供給網（NFR9.41）を、どの作りで満たすかを決める。U3 は種類 `ui` の単位で、段の定義により性能と論理の部品のほかはこの文書にまとめる。文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR4.45`）。

U3 は SQL・対象DB の接続・権限の判定・ログ・トレースを持たない。画面の守りは、(1) 値を文字で描く、(2) 参照先の行の値を印・道・保存・記録・E2E の報告へ漏らさない、(3) 画面の出し分けを判定とみなさない、(4) 誤りの文から参照先の存在や名前を推測させない、(5) E2E の資格情報を報告に残さない、の5つ（NFR 要件の脅威の見立て）。権限を欠く利用者に選択肢を返さないこと・識別子の照合・件数・時間切れ・検索のエスケープ・保存前の確かめ・接続の数（`team.md` の他テーブル参照の (a)〜(e)・(g)〜(k)）は U1・U2 が受け持つ。

## 冒頭の対応表

| 要件 | この文書の節 | 決め（質問） |
|---|---|---|
| NFR1.41 文字で描く | 1.1 | 機能設計の D6・D25・D28・D34 のまま |
| NFR1.42 値を置かない場所 | 1.2 | 同上 |
| NFR1.43 本文で送る | 1.3 | 同上 |
| NFR1.44 出し分けは判定でない | 1.4 | 同上 |
| NFR1.45 応答の形の確かめ | 1.5 | 同上 |
| NFR1.46 誤りの文 | 1.6 | 同上 |
| NFR1.47 E2E の資格情報 | 1.7 | 同上 |
| NFR4.41〜NFR4.43 キーボード・名前・vitest-axe | 3.1〜3.3・2.5 | 同上。結び付けの受け手は承認の場の決定（R-02） |
| NFR4.44 E2E 195 の axe | 3.4 | Q1: A・Q3: A で状態の一覧をそろえた。前提と保存を起こさないことは R-04 |
| NFR4.45 開いた直後の必須の入力不可の誤り | 2節・承認の場の決定 | **Q1: A**。見た目は承認の場で受け入れた（R-03） |
| NFR5.41〜NFR5.43 テスト | 4節 | Q2: A・Q3: A の確かめを足した |
| NFR6.41・NFR6.42 多言語 | 5節 | 機能設計の D36・D27・D31〜D33 のまま |
| NFR7.41 互換性 | 6節 | 同上 |
| NFR9.41 供給網 | 7節 | 依存を足さず、make-you-chic-ui への依頼も出さない |

## 1. セキュリティ（NFR1）

### 1.1 文字で描く（NFR1.41）

- 参照先の行の値（選択肢の値とラベル・結果のセル・選んだ行のラベル・ラベルの1件読みの答え）とカラムの表示名は、React の文字の子として描く。`dangerouslySetInnerHTML` を使わない（oxlint の `react/no-danger`）。値から `href`・`src`・`style` を作らない。
- 文の組み立ては `optionText(option)`（「ラベル (値)」、ラベルが `null` なら値）で文字列を作り、HTML を作らない。i18next の補間（`{{label}}`・`{{field}}`）は既定の `escapeValue` を切らない。React が文字として描くため、二重のエスケープで `&lt;` が見えることが無いかを部品のテストで確かめる（`<` を含む見本がそのまま `<` として読めること）。
- XSS の見本（`<script>`・`<img onerror>`・`"`・`'`・`javascript:`）を `features/tables/testing/` の1つの見本にまとめ、`ReferenceSelectField`・`LookupPickerDialog`・`LookupField`（ラベル）の3つの部品のテストで共有する（D37）。確かめは、文字として出る・`a` 要素が無い・`img` 要素が無い・スクリプトが動かない（`window` に印が立たない）こと。

### 1.2 値を置かない場所（NFR1.42）

| 場所 | 作り | 確かめ |
|---|---|---|
| `data-testid`・DOM の `id` | テストの印は位置の番号（`tables-form-field-c<n>`・`r<n>`）だけで作る。`radio` の `name` も `tables-form-r<index>`（既存の形） | 部品のテストで、見本の値が `data-testid`・`id`・`name` に無いこと |
| `document.title`・道・履歴の状態 | 作成・更新の画面の題と道は変えない。ピッカーの開閉は画面の状態だけで持ち、道に出さない | 部品のテストで `document.title` と `location` に値が無いこと |
| ブラウザの保存・機能の中の記憶 | 選択肢・ラベル・ピッカーの状態は React の状態にだけ持つ。`purpose` つきの定義は `businessDataStore` を読まず書かない（D2）。ダイアログは開くたびに作り直す（D21） | コードの検索で `localStorage`・`sessionStorage`・`indexedDB`・`businessDataStore` の呼び出しを洗い出す。部品のテストで開き直した後に前の結果が無いこと |
| `console` | 出さない（既存の lint の決まりのまま） | コードの検索 |
| E2E の報告 | `test.step` の題・注記・添付・標準出力・失敗の文に行の値を入れない。190 で作る参照先の行の値（ラベル）を `recordSecretValues` の `businessValue` の種類で値のファイルに書き、報告の部品が報告を検索する | E2E の全件の実行で報告の部品が通ること |

### 1.3 本文で送る（NFR1.43）

- `searchReferenceRows` と `getReferenceLabel` は、参照元のスキーマ・テーブル・カラム・条件の値・ラベルを引く値を POST の本文だけに置く。道は固定の `/api/tables/reference/rows`（契約 C2）で、問い合わせを付けない。本文は既存の `postWithinLimit`（`MAX_READ_BODY_BYTES`）を通す。
- 確かめは `businessDataApi.test.ts` で、呼んだ道が固定の文字列と一致し、本文に値が入ること。

### 1.4 出し分けは判定でない（NFR1.44）

- 部品は定義の応答（`editableOnCreate`・`editableOnUpdate`・`truncatedColumns`、`reference.kind`・`status`）だけで `chooseFieldKind` が選ぶ（D5）。画面は権限を判定しない。
- 入力不可の参照の項目の値は、`formDiff` に入力不可のカラムの集合を渡して作成・更新の本文から外す（D15）。
- 画面の確かめ（空の条件・1,000 文字・型・必須）は要求を減らすためだけのもので、サーバー（U1・U2）の判定を正とする。サーバーが同じ理由で拒否したときの文は 1.6 で出す。

### 1.5 応答の形の確かめ（NFR1.45）

- 応答の本文は `unknown` で受け、`businessDataParse.ts` の写しで形を確かめてから型にする。
  - `reference` の形の崩れ、`searchFields`・`listColumns` の `valueType` の欠けと語彙に無い値: 通信の失敗
  - 知らない `status`・`kind`: `NO_PERMISSION`（入力不可・権限不足の説明文）に倒す
  - ラベルの1件読み（`parseReferenceLabel`）: `rows` がちょうど1件で `value`・`label` の形が正しいときだけラベル。それ以外は値だけ
- 確かめは写しの単体テストで、正しい形・`null`・形の崩れ・知らない値を各1件以上。

### 1.6 誤りの文（NFR1.46）

- 誤りの文は `problemMessage.ts` の `referenceErrorKey` と既存の分け方で鍵を選び、DB の文・内部の語・参照先の表とカラムの名前を出さない。
- 検索の 403 は「この項目の候補を検索できません。画面を開き直してください」の1つの文で、行が無いことと読めないことを見分けない（D27）。保存の 409 `REFERENCE_NOT_FOUND` は「選べない値です。選び直してください」の1つの文（D31）。ラベルの1件読みの 403 は文を出さず値だけ（D18）。
- 確かめは 403・409・503 の応答の見本で、決めた文だけが出る部品のテスト。

### 1.7 E2E の資格情報（NFR1.47）

- 資格情報と接続先は Playwright の `webServer.env` に置かず、プロセスの環境変数で渡す。名前は `frontend/playwright.config.ts` の `SECRET_ENV_NAMES` に並べ、報告の部品（`frontend/playwright-secret-check-reporter.ts`）が json の報告と `test-results/` を検索する。
- 190 で作る利用者のパスワード・メールアドレス・氏名と実行の印、参照先のラベルは、`frontend/e2e/support/secretValues.ts` の `recordSecretValues`（種類 `email`・`password`・`displayName`・`runTag`・`businessValue`）で値のファイルに書く。
- 見本の行は架空の値だけ、メールアドレスは `example.com` だけにする。2語以上の現実に近いデータも、実在しそうな氏名ではなく品名などの架空の値にする（例「青い 木製の椅子」）。

## 2. 開いた直後の必須の入力不可の誤り（NFR4.45、Q1: A）

### 2.1 決め

作成の画面で、`ReadonlyReferenceField` の項目が必須（`nullable` が偽）のとき（機能設計の D13）:

| 時点 | 出し方 | `role` | `aria-invalid` | 結び付け |
|---|---|---|---|---|
| 開いた直後（保存をまだ押していない） | D13 の文を `FormField` の **`helperText`** として出す（D12 の説明文の代わり。併記しない） | 無し | 付けない | `FormField` が `aria-describedby` で欄に結ぶ |
| 保存を押して画面の検証が拒否したとき | 同じ文を `FormField` の **`error`** として出し、誤りのまとめにも並べ、まとめへフォーカスを移す。要求は送らない（D13） | 項目の誤りとまとめの両方が `role="alert"`（既存） | 付く | `FormField` が `aria-describedby` で欄に結ぶ |
| サーバーが拒否したとき（400 `REQUIRED`・409 `NOT_EDITABLE_NOT_NULL`） | 既存のとおり `error` とまとめ（D31・D32） | `role="alert"` | 付く | 同上 |

- `ReadonlyReferenceField` の props の読み方を変える: 機能設計の `frontend-components.md` 4.2 の「`error ?? (blockedRequired ? 固定の誤り : undefined)` を `error` に渡す」を、「`error` だけを `FormField` の `error` に渡し、`helperText` には `blockedRequired` なら D13 の文、そうでなければ D12 の説明文を渡す」にする。保存を押した後の誤りは、`BusinessDataFormPage` の画面の検証が作る `error`（D13 の同じ文）で入る。
- 更新の画面では、従来どおり D13 の文を出さない（D12 の説明文だけ）。
- 理由（Q1: A）: 画面を開くたびに読み上げが割り込まず、欄へフォーカスしたときに必ず読まれる。利用者が保存を押して初めて「拒否」として知らせる。行き止まり（M1: A）そのものは変えない。

### 2.2 make-you-chic-ui の口の確かめ（固定先 `4c63d11`、ソースで確かめた）

| 確かめた点 | ソース | 結論 |
|---|---|---|
| 誤りの文は常に `role="alert"` で、外す口が無い | `packages/make-you-chic-ui/src/components/FormField/FormField.tsx` 71〜75 行（`<span … id={errorId} role="alert">`）、`FormFieldProps`（20〜33 行）に `role` を変える項目が無い | 開いた直後に `error` を使うと読み上げが割り込む |
| 説明文（`helperText`）は `role` を持たず、誤りが無いときだけ出る | `FormField.tsx` 51 行（`helperTextId` は `error` が無いときだけ）、76〜80 行（`role` の無い `span`） | 開いた直後は `helperText` で出せる |
| 説明文は欄に `aria-describedby` で結ばれる | `FormField.tsx` 53〜58 行（文脈に `helperTextId`）、`useFieldProps.ts` 40〜46 行（`errorId` と `helperTextId` を `aria-describedby` に並べる。`aria-invalid` は誤りがあるときだけ） | 結び付けは自動。`aria-invalid` は開いた直後に付かない |
| `TextInput` は文脈の結び付けを受ける | `TextInput.tsx` 35・46 行（`useFieldProps()` を `<input>` に広げる） | `ReadonlyReferenceField` の `readOnly` の `TextInput` にそのまま効く |

**結論: `FormField` の今の口（`helperText`）で Q1: A を満たせる。make-you-chic-ui への依頼は要らない。**

選ばなかった案: `FormField` の外に自前の説明文の要素を置き、`TextInput` に `aria-describedby` を渡して文脈の値を上書きする形。`TextInput.tsx` 46〜47 行は `{...fieldProps}` の後に `{...rest}` を広げるため上書きはできるが、(1) 広げる順という部品の内側に頼る、(2) 誤りが出たときに上書きを外し忘れると `errorId` が結び付けから落ちる、の2つの危うさがあるため選ばなかった。

### 2.3 見た目

- 開いた直後の文は `helperText` の見た目（`FormField.css` 34〜37 行、`--color-text-muted`）になり、誤りの赤（`--color-danger-text`）では出ない。文そのものが「必須の参照項目に値を入れられません」で始まり、理由と次の手を言うため、色に頼らずに伝わる（WCAG 1.4.1）。必須の印（`*`）は既存のとおり `--color-danger-text`。
- frontend の CSS で make-you-chic-ui の部品の内側のクラス（`mycui-form-field-helper-text`）を指して色を替えることはしない（今の frontend の CSS は部品の内側のクラスを指していない。部品の更新で崩れるため）。
- 見た目を誤りの赤にしたいときは、make-you-chic-ui に「`role` の無い誤りの見た目の文」の口を依頼する案になる。今回は出さない（機能設計の 11節の結論と、Q1: A の推奨の範囲で足りるため）。見た目は承認の場で依頼者が説明文の色のまま受け入れた（「承認の場の決定」）。
- 状態 7 の axe は、説明文の色（`--color-text-muted`）の文字のコントラストを、ブランドカラーとテーマのすべての組で初めて通す。Code Generation で状態 7 の axe の結果を先に見て、`color-contrast` が出た組があれば、make-you-chic-ui への依頼の要否をその時点で依頼者に諮る（規則は外さない。「後の段に回すこと」、R-03）。

### 2.4 確かめ

- 画面の部品のテスト（`ReadonlyReferenceField.test.tsx` と `BusinessDataFormPage.test.tsx`）:
  - 作成・必須・入力不可で開いた直後: D13 の文が出る、`role="alert"` の要素が画面に1つも無い（誤りのまとめも出ていない）、欄の `aria-describedby` がその文の `id` を指す、欄に `aria-invalid` が無い、D12 の説明文が併記されない。
  - 保存を押した後: 要求が送られない、誤りのまとめ（`role="alert"`）にその文が並びフォーカスがまとめへ移る、項目の誤り（`role="alert"`）として同じ文が出て `aria-describedby` が誤りの `id` を指し `aria-invalid` が付く。
  - 更新の画面で同じ定義を開いたとき: D13 の文が出ない。
  - vitest-axe で、開いた直後と保存を押した後の2つの状態で違反が 0 件。
- E2E 195: 3.4 の状態 7（開いた直後）と状態 8（保存を押した後）。状態 7 では axe に加え、`role="alert"` の要素が無いことを確かめる。

### 2.5 参照の部品ごとの結び付けの受け手（レビューの R-02）

`FormField` の文脈（`id`・`aria-describedby`・`aria-invalid`）は `useFieldProps` を呼ぶ部品だけが受ける。make-you-chic-ui のソース（固定先 `4c63d11`）では `TextInput`・`Textarea`・`Select`・`Checkbox`・`Switch`・`RadioGroup` が `useFieldProps` を呼び、`Button` は呼ばない（`Button.tsx` は `...rest` を広げるだけ）。1つの `FormField` の中で `useFieldProps` を呼ぶ部品を2つ置くと同じ `id` が2つになるため、部品ごとに受け手を1つに決める。

| 部品 | 受け手（`useFieldProps` を呼ぶ要素） | ソース | 受けない要素 |
|---|---|---|---|
| `ReadonlyReferenceField` | `readOnly` の `TextInput` の `<input>` | `TextInput.tsx` 35・46 行 | — |
| `ReferenceSelectField`（`select`） | `Select` の `<select>` | `Select.tsx` 46・57 行（`{...fieldProps}` を `<select>` に広げる） | — |
| `ReferenceSelectField`（`radio`） | `RadioGroup` の `<fieldset>`（見えない `legend` つき） | `RadioGroup.tsx` 59・84〜95 行（`fieldset` に `id`・`aria-describedby`・`aria-invalid`） | 中の各 `<input type="radio">`（`Radio` は `useFieldProps` を呼ばない） |
| `LookupField` | **読み取り専用の `TextInput` の欄**（「ラベル (値)」を出す欄。`readOnly`・`aria-readonly="true"`） | `TextInput.tsx` 35・46 行、`FormField.tsx` 61 行（`<label htmlFor={fieldId}>` がこの欄を指す） | 「選ぶ…」「クリア」の `Button`（名前は見える文字で始まる D17 の文。`aria-describedby` を付けない） |

- `LookupField` の欄を受け手にする理由: 項目名の `label` が `htmlFor` で指す先と、保存の 409 `REFERENCE_NOT_FOUND`（D31）・400 `REQUIRED`・409 `NOT_EDITABLE_NOT_NULL` の誤りが結ぶ先を同じ要素にでき、Tab で欄に止まったときに項目名・今の値・誤りが一緒に読まれる。ボタンは欄の後ろに続くため、誤りを聞いた後に「選ぶ…」へ進める。ボタンに `aria-describedby` を足すと誤りが2回読まれるため付けない。
- `LookupField` の欄の `aria-busy`（ラベルの読み込みの間）は `TextInput` の `...rest` で渡す。`id`・`aria-describedby`・`aria-invalid` は上書きしない（2.2 の「選ばなかった案」と同じ理由）。
- 確かめ（画面の部品のテスト。R-02 の求め）:
  - `ReferenceSelectField.test.tsx`（`select` と `radio` の両方）: `error` を渡したとき、受け手（`select`・`fieldset`）の `aria-describedby` が誤りの文の要素の `id` を指し、`aria-invalid="true"` が付き、誤りの文が `role="alert"`。0 件の説明文（D9）を `helperText` で渡したとき、受け手の `aria-describedby` が説明文の `id` を指し、`aria-invalid` が無い。`radio` では中の各 `<input type="radio">` に `aria-invalid` と同じ `id` が無い。
  - `LookupField.test.tsx`: `error`（409 `REFERENCE_NOT_FOUND` の文）を渡したとき、読み取り専用の欄の `aria-describedby` が誤りの `id` を指し `aria-invalid="true"` が付く。項目名の `label` が欄を指す（`getByLabelText` で欄が取れる）。「選ぶ…」「クリア」のボタンに `aria-describedby`・`aria-invalid` が無く、`id` が欄と重ならない。誤りが無いときは欄に `aria-invalid` が無い。
  - `ReadonlyReferenceField.test.tsx`: 2.4 のとおり（開いた直後は説明文を結び `aria-invalid` 無し、保存を押した後は誤りを結び `aria-invalid` 有り）。
  - いずれも vitest-axe で、誤りのある状態と無い状態の両方で違反が 0 件。

## 3. アクセシビリティ（NFR4.41〜NFR4.44）

### 3.1 キーボードとフォーカス（NFR4.41）

- ダイアログは make-you-chic-ui の `Modal`（`initialFocusRef`・`finalFocusRef`・`closeOnBackdropClick={false}`・Escape で `onClose`）。開いたら最初の条件の入力、閉じたら「選ぶ…」へ（D21）。「クリア」の後は欄へ（D19）。
- 入力不可の項目は `readOnly`・`aria-readonly="true"` で、`disabled` にしない（Tab で止まる。D11）。
- REFERENCE の `radio` はネイティブの `radio` の群で、Tab で1回止まり矢印のキーで移る（`performance-design.md` の 3節）。
- 確かめは user-event のキーボード操作の部品のテストと、E2E 190 のキーボードだけの流れ。

### 3.2 名前と状態の知らせ（NFR4.42）

- ボタンの名前は見える文字で始める（「選ぶ…（{項目名}）」「クリア（{項目名}）」、行の「{行のラベル}を選ぶ」。D17・D28）。axe の `label-content-name-mismatch` を外さずに流す。
- 「検索中」・「条件を入れてください」・0 件・初期の案内は `role="status"`、検索の失敗と 403 は `role="alert"`、件数の変化は `aria-live="polite"`。ピッカーの中の `role="alert"` は検索の失敗のときだけで、Q1: A の決め（開いた直後に `alert` を出さない）とそろう。
- 結果の表は `src/shared/scroll-region` で包み（`role="region"`・`tabIndex=0`・名前）、表に `aria-label`。ページ送りは総ページと件数を言わず、今のページと続きの有無を言う（D25）。
- 入力不可の理由は文字で分け、`aria-describedby` で結ぶ（D11・D12。2節のとおり `FormField` の `helperText`）。

### 3.3 vitest-axe（NFR4.43）

- `ReferenceSelectField`（`select`・`radio` 200 件）・`ReadonlyReferenceField`（権限不足・候補が多い・作成の必須の開いた直後・保存を押した後）・`LookupField`・`LookupPickerDialog`（初期・結果・0 件・失敗）。部品ごとに1件の決まりに、状態ごとの件を足す。

### 3.4 E2E 195 の状態の一覧（NFR4.44。NFR4.45 で確かめ直した）

NFR 要件の NFR4.45 の求めどおり、Q1: A の決めと NFR4.44 の状態の一覧を突き合わせた。NFR4.44 の「作成の必須の入力不可の誤り」は、Q1: A で開いた直後は誤り（`role="alert"`）ではなく説明文になるため、そのままでは決めた動作と食い違う。開いた直後と保存を押した後の2つの状態に分けてそろえる。あわせて Q3: A で `radio` 200 件の状態を足す。

| # | 状態 | 作り方 |
|---|---|---|
| 1 | ピッカーを開いた直後（初期の案内） | 本物の応答 |
| 2 | 結果の一覧 | 本物の応答 |
| 3 | 0 件 | 本物の応答（当たらない条件） |
| 4 | 検索の失敗（503） | 差し替え |
| 5 | 入力不可（権限不足） | 差し替え（定義の `status`） |
| 6 | 入力不可（候補が多い） | 差し替え（定義の `status`） |
| 7 | 作成の必須の入力不可・開いた直後（説明文。`role="alert"` が無いことも確かめる） | 差し替え（定義） |
| 8 | 作成の必須の入力不可・保存を押した後（項目の誤りとまとめ） | 7 から保存を押す（要求は出ない） |
| 9 | 保存の 409 `REFERENCE_NOT_FOUND` の誤り | 差し替え（保存の応答） |
| 10 | 長い名前（100 文字以上、`<` を含む） | 本物の応答（E2E の専用のスキーマの行） |
| 11 | 2語以上の現実に近いデータ（架空の品名） | 本物の応答 |
| 12 | REFERENCE の `radio` 200 件 | 差し替え（定義の `formPart` と 200 件の `options`） |

- 組: ブランドカラーとテーマのすべての組（既存の `frontend/e2e/support/displayCombos.ts`）。幅は既定と 375px。
- 375px では、開いたダイアログの矩形が画面の中に収まり、画面全体が横にスクロールしないことを `frontend/e2e/support/overflow.ts` で確かめる。状態 12 と状態 10 でも画面全体の横のスクロールが無いこと。
- 差し替えの見本は、190 の流れで取った本物の応答と `shapeCompare` で形を比べる。
- axe の規則と要素を外さない（`disableRules`・規則の絞り込み・`exclude` を使わない）。違反が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから直す。
- E2E の前提の参照先の行（機能設計の 9節「行の数は NFR 設計」）: 専用のスキーマに、顧客の表 3 行（`select` の確かめ）、商品の表 25 行（1ページ 20 件で2ページ目に 5 件。うち1行は 100 文字以上で `<` を含む名前、1行は2語以上の品名）、参照元の注文の表を置く。値はすべて架空。
- 190・195 は、その流れで自分で作った利用者・ロール・専用のスキーマの行だけを対象にし、初期管理者（状態・ロールの割り当て・作業ロール）と見本の行を変えない（`team.md` の E2E の決まり。E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。差し替えで作る状態（4〜9・12）は画面の表示だけを作り、保存の要求を起こさない（状態 8 は画面の検証が要求を止める。状態 9 は保存の応答を差し替えるため対象DB に届かない）。確かめは、195 の流れの中で保存の道（作成・更新の POST）への要求が差し替えの外へ出ていないことを、Playwright の要求の記録で数える（R-04）。

## 4. テスト（NFR5.41〜NFR5.43）

- NFR5.41: 機能設計の `frontend-components.md` 7節のテストに、この段で決めた確かめを足す。
  - `useReferenceLabels.test.tsx`: 同時に 3 本までの5つの確かめ（`performance-design.md` の 2.1）。
  - `ReferenceSelectField.test.tsx`: `radio` 200 件の構造とキーボード（`performance-design.md` の 3節）。
  - `ReadonlyReferenceField.test.tsx`・`BusinessDataFormPage.test.tsx`: 2.4 の確かめ。
  - `ReferenceSelectField.test.tsx`（`select`・`radio`）・`LookupField.test.tsx`: 2.5 の結び付けの受け手の確かめ（R-02）。
  - `useReferenceLabels.test.tsx`: 飛んでいる最中の読み直しの確かめ（`performance-design.md` の 2.1 の (6)、R-01）。
  - 遅らせた応答は手で解く Promise で作り、`sleep` や時計に頼らない。描画の後の効果で出る値は `waitFor` で待つ。時間の上限を延ばさない。
- NFR5.42: 性質ベースのテスト（fast-check）は機能設計の D37 のまま（`chooseFieldKind`・条件の組み立て・`pickerPaging`・`optionText`）。乱数の種を失敗のときに記録する。カバレッジの下限は今の `thresholds` のまま。
- NFR5.43: E2E は 190 の1本だけを足し、195（axe）は本数に数えない。DSL は 180 と共有する見本に参照の表を足し、180 の全件が通ることを確かめる（D39）。前提の行は 3.4 の最後のとおり。

## 5. 多言語（NFR6.41・NFR6.42）

- 文言は `features/tables/messages.ts` に日本語と英語で置く（`frontend-components.md` の 5節の鍵）。この段で鍵を足さない（Q4: A で「時間がかかっています」を足さず、Q1: A は既存の鍵 `tables.reference.requiredBlocked.*` を `helperText` と `error` の両方に使う）。
- 誤りの文は U1・U2 の `code`・`reason` と状態コードから選び、知らない `code` は既存の汎用の文にする。確かめは日英の鍵の集合が一致するテストと、英語に切り替えた部品のテスト。

## 6. 互換性（NFR7.41）

- 一覧・詳細の画面は `purpose` を付けず `reference` を読まない（D1）。`reference` が `null` のカラムは既存の部品で出す。知らない `status`・`kind` は入力不可に倒す（1.5）。`writeOutcome.ts` は変えない（D33）。
- 確かめは、`reference` を含む定義の見本（`features/tables/testing/sampleDefinition.ts`）を既存の一覧・詳細・作成・更新の画面のテストに流して通ること（AC3.4.3）。

## 7. 供給網（NFR9.41）

- 新しい依存を足さない。make-you-chic-ui の固定先は `4c63d11` のまま。この段の確かめ（2.2）で、Q1: A は `FormField` の `helperText` で満たせ、200 件の `radio` は `RadioGroup` で描けると分かったため、**make-you-chic-ui への新しい依頼は出さない**。
- `frontend/.npmrc` の `ignore-scripts=true` と lockfile の固定を保つ。確かめは `frontend/package.json` と `frontend/package-lock.json` の差に新しいパッケージが無いことを、コード生成の記録に書く。

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

1. **D13 の「開いた直後から、その項目の誤り（`FormField` の `error`）として」（機能設計 `functional-spec.md` の D13、`frontend-components.md` 4.2）**: 開いた直後は `FormField` の `helperText`（`role` 無し・`aria-invalid` 無し）で出し、保存を押して拒否したときだけ `error`（`role="alert"`）にする（Q1: A）。文の中身は変えない。
2. **画面イメージの確かめの一覧の M7「項目の誤りとして、理由と次の手が文字で読める」（`inception/refined-mockups/accessibility-checklist.md`）**: 開いた直後は「誤り」ではなく説明文として読める。保存を押した後は誤りとして読める。見た目は開いた直後に誤りの赤ではなく説明文の色になる（2.3）。
3. **NFR4.44 の axe の状態の一覧（NFR 要件 `security-requirements.md`）**: 「作成の必須の入力不可の誤り」を、開いた直後（状態 7）と保存を押した後（状態 8）に分け、`radio` 200 件（状態 12）を足した（3.4）。NFR4.45 の求めた突き合わせの結果。

## 承認の場の決定

- **R-03（開いた直後の D13 の文の見た目）**: 依頼者は承認の場で、開いた直後の D13 の文が説明文の色（`--color-text-muted`）で出て、誤りの赤（`--color-danger-text`）にならないことを受け入れた（2.3）。文の中身と必須の印（`*`）は残るため、色に頼らずに伝わる（WCAG 1.4.1）。make-you-chic-ui への「`role` の無い誤りの見た目の文」の口の依頼は、この決定では出さない。ただし状態 7 の axe で `color-contrast` が出たときは、下の「後の段に回すこと」のとおりその時点で依頼者に諮る。
- 同じ承認の場で、依頼者はレビューの R-01・R-02・R-04 を推奨の直し方で直すことを決めた（R-01 は `performance-design.md` の 2.1、R-02 は 2.5、R-04 は 3.4）。

## 後の段に回すこと

- **Code Generation**:
  - 2.1 の `ReadonlyReferenceField` の props の読み方の変更と 2.4 の確かめ。
  - 2.5 の結び付けの受け手（`LookupField` は読み取り専用の欄）と、その部品のテスト（R-02）。
  - **状態 7 の axe を先に見る（R-03）**: E2E 195 のうち状態 7（開いた直後の説明文）の axe を、ブランドカラーとテーマのすべての組で先に流して結果を見る。`color-contrast` が出た組があれば、make-you-chic-ui への依頼の要否をその時点で依頼者に諮る。依頼するときは `team.md` の手順（`<record>/make-you-chic-ui-request-<n>.md` に文面を置き、上流のソースで確かめてから固定先を上げる）で行う。どの場合も axe の規則・要素を外して通さない（`project.md` の Forbidden）。
  - 3.4 の 12 の状態と前提の行（顧客 3 行・商品 25 行・注文）、190・195 が自分で作った前提だけを使い保存を起こさないことの確かめ（R-04）。
  - 190 の参照先のラベルを `businessValue` として値のファイルに書く（1.7）。
- **Build and Test**: E2E 195 の全件の通過と違反 0 件の件数、axe-core の版が lockfile で1つにそろっていること。

## U1・U2 とそろえる点

- **U1**: 検索の 403 は行が無いことと読めないことを見分けない（画面も1つの文）。ラベルの1件読みの応答の形。`valueType` の語彙。
- **U2**: 409 の `reason`（`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`）と 400 `REQUIRED`、必須の判定は `nullable` が偽。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/nfr-design-questions.md`（Q1〜Q4 すべて A）
- NFR 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/security-requirements.md`（NFR1.41〜NFR9.41）、`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D5・D6・D11〜D13・D15・D17〜D19・D21・D24・D25・D27・D28・D31〜D34・D36・D37・D39、9節・11節・13節）、`frontend-components.md`（4.2節・5節・7節・8節）
- 画面イメージ: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/refined-mockups/accessibility-checklist.md`（M7）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- make-you-chic-ui のソース（固定先 `4c63d11`、読み取りだけ）: `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/FormField.tsx`（20〜33・49〜58・71〜80 行）、`FormField/useFieldProps.ts`（32〜47 行）、`FormField/FormField.css`（34〜42 行）、`TextInput/TextInput.tsx`（31〜50 行）、`RadioGroup/RadioGroup.tsx`（48〜110 行）、`Select/Select.tsx`（40〜62 行）、`Button/Button.tsx`（35・60 行。`useFieldProps` を呼ばない）
- レビュー: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/reviews/review-01.md`（R-01〜R-04。承認の場で依頼者がすべて推奨のとおり直すと決め、R-03 は見た目を受け入れた）
- frontend のコード（読み取りだけ）: `frontend/src/features/tables/BusinessDataFormPage.tsx`（FieldInput・誤りのまとめ）、`frontend/e2e/support/secretValues.ts`（`SECRET_KINDS`）、`frontend/e2e/support/displayCombos.ts`、`frontend/e2e/support/overflow.ts`、`frontend/playwright.config.ts`（`SECRET_ENV_NAMES`）
- 書き方の手本: `aidlc/spaces/default/intents/261016-business-data-views/construction/u3-businessdata-screens/nfr-design/security-design.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture の他テーブル参照の (f) と参照ピッカーの E2E、Way of Working の make-you-chic-ui の依頼の手順、Code Style のフロントエンド）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated、学び: axe の組・`webServer.env`・固定の部品のはみ出し）

## Assumptions & Open Questions

- [assumption] 開いた直後に `aria-invalid` を付けないことは、利用者がまだ何も入れていない欄を「無効」と告げないための読みで、axe の規則に触れない。Code Generation の vitest-axe と E2E 195 で確かめる。
- [assumption] 見本の 2語以上のデータは品名にし、実在しそうな氏名を置かない（`team.md`）。
- 開いた直後の文の見た目（説明文の色）は承認の場で依頼者が受け入れた（「承認の場の決定」）。状態 7 の axe で `color-contrast` が出るかは Code Generation で確かめる。
- 確かめていないため、NFR1.41〜NFR9.41 は Code Generation と Build and Test で確かめるまで `Unverified` とする。
