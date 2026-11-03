# 画面の部品 — u2-display-time-zone

出典: `functional-spec.md`（1.3〜1.5）、`rules.md`（BR2.12〜BR2.24）、契約 C1・C2、画面イメージ S1（`inception/refined-mockups/mockups.md`・`interaction-spec.md`・`design-system-mapping.md`）、今の画面のコード（読み取りだけ）。U2 は種類 service だが、画面の側の部品を持つため、この文書に画面の部品をまとめる（機能設計の定義では ui の単位向けの成果物。U2 は画面の部品の構成の置き場として作る）。

## 1. 部品の一覧

| 部品 | 置き場 | 役割 |
|---|---|---|
| `DisplayTimeZoneProvider` | `frontend/src/app/display-settings/` | 設定の読み込み・状態の決定・差し替え・再読み込み。ログイン後の画面の外側（`WorkRoleProvider` と同じ並び）に置く |
| `useDisplayTimeZone` | 同上 | 状態（`status`・`timeZone`・`source`・`savedName`）を返す。提供元の外では例外（`useDisplaySettings` と同じ） |
| `useDisplayTimeZoneActions` | 同上 | `applySaved(name \| null)`（差し替え）と `reload()`（再読み込み）を返す |
| `browserTimeZone` | 同上 | ブラウザのタイムゾーンを読む唯一の場所。提供元の引数で差し替え可。解釈できなければ `UTC` |
| `displayTimeZoneApi` | 同上 | `GET /api/me/preferences` を読み、`displayTimeZone` だけを取り出す。形が違えば形の誤り。プリファレンスの画面の API とは型を共有しない |
| `DisplayTime` | `frontend/src/app/display-settings/` | 時刻の欄の共通の部品。`loading`・`error`・`ready` で出し分ける（BR2.21）。管理の画面だけが使う |
| `DisplayTimeZoneErrorBar` | 同上 | `error` のとき画面の上の1か所に「再読み込み」を出す。`error` 以外では何も描かない |
| `formatInstantToMinutes` | `frontend/src/shared/format/` | U1 の `formatInstant` の文字から分までを取り出す薄い関数（BR2.19）。読めなければ元の文字 |
| `formatLockedUntilInZone` | `frontend/src/features/useradmin/` | ロックの解除の予定（BR2.20）。今の `lockedUntil.ts` の置き換え |
| `TimeZoneSelect` | `frontend/src/features/preferences/` | 選択（`Select`＋`FormField`）、選択肢の組み立て、誤りと警告 |
| `buildTimeZoneOptions` | 同上 | 選択肢を作る純粋な関数（BR2.17）。一覧・保存済みの名前・現在時刻を引数で受ける |
| `PreferencesForm`・`usePreferencesForm`・`preferencesApi` | 同上（既存の直し） | 5項目を扱う |

## 2. 配布（DisplayTimeZoneProvider）

- 値: `{ status: 'loading' | 'ready' | 'error', timeZone: string | null, source: 'saved' | 'browser' | 'saved-unresolvable' | null, savedName: string | null }`（`ready` のときだけ `timeZone`・`source` が入る）。
- 入力（props）: `api`（既定は `displayTimeZoneApi`）、`browserTimeZone`（既定は唯一の読み取り）。テストは固定の値を渡す。
- 読み込み: ログイン中になったとき1回。世代で古い答えを捨てる。未ログインで初期化する（`WorkRoleProvider` と同じ作り）。
- 決める関数: `decideDisplayTimeZone(savedName, browserName)` は純粋な関数で、`canInterpretTimeZone` だけを使う。結果は `ready` の3つの source のいずれか。ブラウザの名前も解釈できなければ `UTC`（BR2.12）。
- 差し替え: `applySaved` は、ログイン中なら `decide…` をやり直して `ready` にする。`error` からも回復する（BR2.15・BR2.16）。
- ログに出さない: 名前は個人に関する値ではないが、応答の本文をコンソールに出さない。

## 3. プリファレンスの画面（S1）

```text
プリファレンス
 氏名            [ 佐藤 花子                          ]
 言語            (●) 日本語  ( ) English
 表示のタイムゾーン
   [ ブラウザに合わせる                              ▼ ]   ← FormField（誤りは下に）
   時刻の欄に使うタイムゾーンです。…
 テーマ …                       文字の大きさ …
                                 [元に戻す] [保存する]
```

- 配置: 氏名・言語の次、テーマの案内の前。`FormField` のラベルは「表示のタイムゾーン」（英語 Display time zone）、説明文は `FormField` の補助の文（読み上げは名前と説明）。
- 状態:
  - 読み込み中・読み込みの失敗: 今のまま（フォーム全体）。
  - 解釈できない保存値（`source` が `saved-unresolvable`）: `Select` の上に `Alert`（warning）。文言は「保存したタイムゾーン（{名前}）をこの端末では使えないため、ブラウザのタイムゾーンで表示しています。」（英語は mockups の文）。`{名前}` は文字として出す（HTML として描かない）。保存の値は変えず、選び直せる。
  - 保存中: 「保存する」を loading、`Select` を `disabled`。
  - 保存の拒否: 選択を残し、`Select` の下に誤りの文（「このタイムゾーンは使えません。別のタイムゾーンを選んでください。」）。
  - 保存の成功: 今の知らせ。直後から画面の時刻が切り替わる。
- 選択肢: `buildTimeZoneOptions(names, savedName, now, canInterpret)`。先頭は「ブラウザに合わせる」（値は空の文字列。`Select` の値は文字列だけのため。送るときは `null`）。続けて名前の昇順の平らな並び（`optgroup` なし）に `UTC`（無いときだけ）、保存済みの名前（一覧に無いとき、先頭の次）。ラベルは `名前 (±HH:mm)`。時差は U1 の `formatInstant` に `now` を渡して得た文字の末尾の時差。解釈できない名前は時差なし。
- 名前の取り出し: ブラウザの `Intl.supportedValuesOf('timeZone')`（取り出しを差し替えられるようにする。無い環境では空にし、`UTC` と保存済みの名前だけが並ぶ）。
- キーボード・読み上げ: 標準の `select` の動き（Tab で入り、矢印・先頭の文字入力で移り、Enter で決める）。選んだ値と時差はラベルに含まれて読み上げられる。
- 型: `Preferences` に `displayTimeZone: string | null`。`PreferencesField` に `displayTimeZone` を足す。応答の形の確かめに5つ目（文字列か null）を足し、文字列でも null でもなければ形の誤り。
- 誤りの写し: 400 の `fieldErrors` の `displayTimeZone`（`INVALID_VALUE`）→ 文言の鍵。ほかの項目の誤りと同時に出る。
- dirty・元に戻す: 5項目の比べに含める。

## 4. 管理の画面の時刻

- 利用者の一覧（登録日時・ロックの解除の予定）、招待の一覧（招待日時・期限）、DSL の状態パネル・履歴の表・確認の表示（日時）が、`DisplayTime` を使う。画面の props の `timeZone` は外す。
- `DisplayTime`:
  - `loading`: 淡い短い文（共通の「読み込み中」。表の幅が崩れないよう1行）。読み上げには読み込み中と伝える。
  - `error`: 「表示のタイムゾーンを読み込めません」（淡い短い文）。画面の上の1か所に `DisplayTimeZoneErrorBar`（文言と「再読み込み」ボタン）。
  - `ready`: `formatInstantToMinutes(iso, timeZone)` の文字（`<time dateTime={iso}>` で包み、機械の読み取りを元の時点で保つ）。
- 利用者の一覧のロックの解除の予定は `formatLockedUntilInZone`（`now` は引数で受ける今の仕組みのまま）。
- 使わなくなる既存の整形（`formatDateTime`・`formatLockedUntil`）の扱いは Code Generation の計画で決める。

## 5. 文言の鍵（日本語・英語）

| 鍵の名前（論理） | 日本語 | 英語 |
|---|---|---|
| 選択のラベル | 表示のタイムゾーン | Display time zone |
| ブラウザに合わせる | ブラウザに合わせる | Follow the browser |
| 説明 | 時刻の欄に使うタイムゾーンです。業務データの日時と、利用者の一覧などの時刻に効きます。 | The time zone used for times. It applies to business data and to times in lists such as the user list. |
| 保存の拒否 | このタイムゾーンは使えません。別のタイムゾーンを選んでください。 | This time zone is not available. Choose another one. |
| 解釈できない保存値 | 保存したタイムゾーン（{名前}）をこの端末では使えないため、ブラウザのタイムゾーンで表示しています。 | The saved time zone ({name}) is not available on this device, so times are shown in the browser time zone. |
| 読み込み中の時刻 | 読み込み中 | Loading |
| 読み込み失敗の時刻 | 表示のタイムゾーンを読み込めません | The display time zone could not be loaded |
| 再読み込み | 再読み込み | Reload |

## 6. テスト（画面）

- 提供元: 状態の遷移（loading→ready の3つの source・error・再読み込み・差し替え・未ログイン・古い答えの破棄）を、固定のブラウザのタイムゾーンと差し替えた API で確かめる。`waitFor` で待つ。
- `decideDisplayTimeZone`・`buildTimeZoneOptions` は純粋な関数: fast-check の性質ベース（ready の `timeZone` は常に解釈できる、保存済みの名前は選択肢に必ず含まれる、選択肢の value は重ならない）。
- 選択: キーボードで選べる・ラベルの時差・保存の拒否の誤り・解釈できない保存値の Alert・5項目の送信（`null` と名前）・元に戻す。
- 管理の画面: 固定のタイムゾーンで分までの書式、loading・error の表示、ロックの解除の予定の今日・今日でない、読めない時点は元の文字。
- axe（`vitest-axe`）: 画面部品ごとに1件。実際のブラウザの axe は、ブランドカラーとテーマのすべての組・誤りの状態・解釈できない名前の Alert・長い名前の選択肢で行う。
- 日時を扱うテストは、タイムゾーンを明示に固定する（機械の既定に預けない）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位・契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`・`inception/contract-design/contract-summary.md`（C1・C2）
- 画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/refined-mockups/mockups.md`（S1）・`interaction-spec.md`・`design-system-mapping.md`
- 決まりと流れ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`・`functional-spec.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/functional-design-questions.md`

## Assumptions & Open Questions

- [assumption] `Select` の値は文字列だけのため、「ブラウザに合わせる」は空の文字列で表し、送るときに `null` にする。実在の IANA の名前に空の文字列は無いので衝突しない。
- [assumption] `Intl.supportedValuesOf` が無い環境（古いテストの環境）では選択肢が `UTC` と保存済みの名前だけになる。実行するブラウザは対応している前提。
- [assumption] 部品の名前・置き場は論理の案で、Code Generation で既存の並びに合わせて確定する。
