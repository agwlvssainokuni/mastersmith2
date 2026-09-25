# Interaction Spec — user-management

画面は `mockups.md`（S1〜S5・E1）。形式は `.claude/knowledge/aidlc-design-agent/component-spec-template.md` に従う。幅はパソコンの幅を主な対象にし、狭い幅（768px 未満）でも崩れずに読めて操作できるようにする（`refined-mockups-questions.md` の Q5: B）。共通の決まりはストーリーの CR6（`aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`）。

## 1. 共通の動き

| 場面 | 動き | 出典 |
|---|---|---|
| 送信の間 | 押したボタンを押せなくし、ボタンの文言を「送信しています」などに変え、`aria-busy` を付ける | CR6.3 |
| 成功 | Toast（`aria-live="polite"`、5 秒で消える）で知らせる | CR6.4 |
| 失敗 | 画面の中に `role="alert"` の表示で残す。Toast だけで失敗を知らせない | CR6.4 |
| 入力の誤り | 項目の下に文字で出し `aria-describedby`・`aria-invalid` で結び付ける。送信で誤りが返ったら最初の誤りの項目にフォーカス。入れた値は消さない | CR6.1 |
| 言語 | ログインの前の画面は画面の言語を要求に付けて送り、エラーの言語を画面にそろえる。`<html lang>` を画面の言語に合わせる | CR1.2・CR1.3 |
| 表示の設定を当てる | ログインしたら内部DB の設定を当て、ブラウザにも保存する。ログインの前はブラウザに最後に保存された値 | FR5.4・FR5.5 |
| アニメーション | `prefers-reduced-motion` のときは切り替えの動きを止める | WCAG |

## 2. InvitationList（S1 の一覧）

| Field | Value |
|---|---|
| Component | InvitationList |
| Description | 招待中の招待を20件ずつ表で示し、行ごとに送り直し・取り消しを行う |
| Category | display |

### States

| State | Description | Trigger |
|---|---|---|
| default | 招待の行を表で示す | 画面を開く・操作の後に読み直す |
| loading | 読み込み中の表示（文字つき） | 一覧の取得中 |
| empty | 「招待中の人はいません。『招待する』から招待できます。」 | 招待が0件 |
| error | `role="alert"` の失敗の表示と「もう一度読み込む」 | 一覧の取得の失敗 |
| disabled（行の操作） | 送り直すを押せない | 招待を使えない設定（FR1.8）・その行の送信中 |
| row-busy | その行の送り直すを押せなくし「送信しています」 | 送り直しの送信中 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| page | number | yes | 1 | 表示するページ（20件ずつ） |
| invitationEnabled | boolean | yes | — | 招待を使える設定か（FR1.8） |
| onResend | function | yes | — | 送り直し |
| onCancel | function | yes | — | 取り消し（S1-M2 を開く） |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 表は横に動かして見る。操作のボタンは行の最後の列のまま |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 図のとおり |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 素の `table`（`caption` に「招待中の人」、列見出しは `th scope="col"`） |
| Keyboard interaction | Tab で行の操作のボタンへ、Enter/Space で押す。ページ送りはボタン |
| Label / aria-label | 行のボタンは「a@example.test への招待を送り直す」のように対象を含む名前 |
| Contrast ratio | WCAG AA（4.5:1 文字、3:1 部品） |
| Screen reader | ページ送りの後に「21〜40 件目 / 全 43 件」を `aria-live="polite"` で伝える |
| Focus management | 送り直し・取り消しの後は、同じ行（無くなったら表の見出し）にフォーカスを置く |

## 3. InviteDialog（S1-M1）

| Field | Value |
|---|---|
| Component | InviteDialog |
| Description | メールアドレスと言語を入れて招待する Modal |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | メールアドレスは空、言語は管理者自身の言語 | 「招待する」を押す |
| error | 項目の下に誤り（形式・登録済み・招待中）。招待中のときは「一覧でこの招待を見る」のリンクを添える | 送信で入力の誤りが返る |
| navigate-to-row | Modal を閉じ、一覧をその招待の行のページへ移し、行を目立たせて行の「送り直す」にフォーカス | 招待中の誤りのリンクを押す（AC1.1.4） |
| loading | 「招待する」を押せなくし「送信しています」 | 送信中 |
| closed-success | 閉じて Toast、一覧に1行（AC1.1.8 との差は `mockups.md` の 10 節） | 招待と送信に成功 |
| closed-send-failed | 閉じて一覧の上に `role="alert"` | 招待は作られ送信に失敗 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| defaultLanguage | 'ja' または 'en' | yes | 管理者自身の言語 | 言語の初期値（AC1.1.1） |
| onInvited | function | yes | — | 成功・送信の失敗の後に一覧を読み直す |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | Modal は画面の幅いっぱい |
| tablet / desktop | 幅 480px 前後 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`（`aria-modal`、見出しで名前） |
| Keyboard interaction | 開いたらメールアドレスにフォーカス。Tab は Modal の中を回る。Esc で閉じる |
| Label / aria-label | 言語は RadioGroup（legend「招待メールの言語」）、選択肢は「日本語」「English」（`lang` 属性つき） |
| Contrast ratio | WCAG AA |
| Screen reader | 送信中は `aria-busy`、誤りは項目に結び付けて読む |
| Focus management | 閉じたら「招待する」ボタンに戻す。背景のクリックでは閉じない |

## 4. CancelConfirmDialog（S1-M2）

| Field | Value |
|---|---|
| Component | CancelConfirmDialog |
| Description | 招待の取り消しを確かめる Modal |
| Category | feedback |

### States

| State | Description | Trigger |
|---|---|---|
| default | 対象のメールアドレスと結果を説明 | 行の「取り消す」 |
| loading | 「取り消す」を押せなくする | 取り消しの要求中 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| email | string | yes | — | 対象の招待のメールアドレス |
| onConfirm | function | yes | — | 取り消しの実行 |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| すべて | InviteDialog と同じ |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `alertdialog` |
| Keyboard interaction | はじめのフォーカスは「やめる」。Esc で閉じる |
| Label / aria-label | 見出し「招待を取り消しますか」、本文を `aria-describedby` |
| Contrast ratio | WCAG AA |
| Screen reader | 開いたら見出しと本文を読む |
| Focus management | 閉じたら元の「取り消す」ボタンに戻す。背景のクリックでは閉じない（CR6.7） |

## 5. RegistrationForm（S2）

| Field | Value |
|---|---|
| Component | RegistrationForm |
| Description | 招待のリンクから氏名・パスワード・表示の設定を入れて登録を完了する |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| verifying | 読み込み中の表示 | リンクの確かめ中 |
| unavailable | 「このリンクは使えません。招待した管理者に招待の送り直しを依頼してください。」フォームは出さない | リンクが使えない（理由によらず同じ、AC3.2.2） |
| default | 初期値の入ったフォーム | リンクが有効 |
| error | 項目の下に誤り | 入力の誤り（11 文字・73 バイト・不一致・空の氏名） |
| loading | 「登録を完了する」を押せなくし「登録しています」 | 送信中 |
| completed | ログインの画面へ移る | 完了 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| invitationToken | string | yes | — | リンクから取り出した招待の値（URL のどこに入れるかは機能設計） |
| invitedEmail | string | yes | — | 表示と氏名の初期値 |
| invitedLanguage | 'ja' または 'en' | yes | — | 表示と言語の初期値 |

### 表示の設定の動き

| 操作 | 動き |
|---|---|
| 開いた時点 | 言語は招待の言語、テーマと文字の大きさはブラウザに最後に保存された値で表示（FR4.3）。フォームの初期値は system・md（FR4.2） |
| テーマ・文字の大きさを選ぶ | 選んだ時点で画面に当てる（Q3: A。表示とフォームの初期値の食い違いを解くため）。案内「テーマと文字の大きさは選ぶと画面に反映されます」 |
| 言語を選ぶ | 選んだ時点で画面の言語を切り替える（Q4: C。この画面に言語の切り替えを置かないため）。以降のエラーの言語も画面にそろえる。案内「選ぶとこの画面の言語が切り替わります」 |
| 完了 | 選んだ3つをブラウザに保存し（M4）、ログインの画面へ |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 1列に積む。ラジオの選択肢は折り返す |
| tablet / desktop | 中央の幅 480px 前後のカード |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `form`（見出し「登録を完了する」）、表示の設定は `fieldset`・`legend` ごと |
| Keyboard interaction | 上から順に Tab。ラジオは矢印キー |
| Label / aria-label | すべての項目に見える label。ログインに使うメールアドレスは変えられない値として示し `autocomplete="username"`（AC3.2.16）。パスワードは `autocomplete="new-password"`（CR6.9） |
| Contrast ratio | WCAG AA（テーマ・文字の大きさのどの組み合わせでも） |
| Screen reader | 表示の設定を選んで画面が変わったことは読み上げない（選んだ値が読まれるため）。誤りは項目に結び付けて読む |
| Focus management | 送信で誤りが返ったら最初の誤りの項目へ |

## 6. LoginLanguageSwitch（S3、Could）

| Field | Value |
|---|---|
| Component | LoginLanguageSwitch |
| Description | ログインの画面の右上で表示の言語を切り替える |
| Category | navigation |

### States

| State | Description | Trigger |
|---|---|---|
| default | 今の言語が選ばれている | 画面を開く |
| changed | 画面の言語が切り替わり、ブラウザに保存される | 選ぶ |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| current | 'ja' または 'en' | yes | ブラウザに保存された言語、無ければブラウザの言語設定 | 今の言語 |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| すべて | 画面の右上に固定 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 2つのボタンの組（`aria-pressed`）または RadioGroup（名前「表示の言語」） |
| Keyboard interaction | Tab で届き、Enter/Space で切り替え |
| Label / aria-label | 「日本語」「English」（それぞれ `lang` 属性つき） |
| Contrast ratio | WCAG AA |
| Screen reader | 切り替えた後は `<html lang>` が変わる |
| Focus management | 切り替えた後も同じボタンにフォーカス |

## 7. PreferencesForm（S4）

| Field | Value |
|---|---|
| Component | PreferencesForm |
| Description | 氏名・言語・テーマ・文字の大きさを変えて保存する |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| loading | 読み込み中 | 今の設定の取得 |
| default | 今の値が入ったフォーム | 取得の成功 |
| previewing | テーマ・文字の大きさが画面に当たっている（未保存） | 選ぶ（M8） |
| error | 項目の下に誤り、または `role="alert"` | 入力の誤り・保存の失敗 |
| saved | Toast「保存しました」、言語を切り替え、ユーザーメニューの名前を更新 | 保存の成功（M7・M8） |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| current | object（氏名・言語・テーマ・文字の大きさ） | yes | — | 今の設定 |

### 未保存の扱い

| 操作 | 動き |
|---|---|
| 「元に戻す」 | フォームと画面の表示を今の設定に戻す |
| 保存せずに画面を離れる | テーマ・文字の大きさの表示を今の設定に戻す（確かめは出さない [assumption]） |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 1列 |
| tablet / desktop | コンテンツの幅 640px 前後 |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `form`、項目のまとまりごとに `fieldset`・`legend` |
| Keyboard interaction | Tab と矢印キー |
| Label / aria-label | 見える label。言語の案内「保存すると切り替わります」、テーマの案内「選ぶと画面に反映されます」を `aria-describedby` |
| Contrast ratio | WCAG AA（どの組み合わせでも） |
| Screen reader | 保存の成功は Toast で伝える |
| Focus management | 保存の後もフォーカスは保存のボタン |

## 8. PasswordChangeForm（S5）

| Field | Value |
|---|---|
| Component | PasswordChangeForm |
| Description | 今のパスワードを確かめて新しいパスワードに変える |
| Category | input |

### States

| State | Description | Trigger |
|---|---|---|
| default | 3つの空の項目 | 画面を開く |
| error | 項目に結び付けた誤り（今のパスワードの誤り・11 文字・73 バイト・不一致） | 送信の結果 |
| loading | 「変更する」を押せなくする | 送信中 |
| success | Toast、3つの項目を空にする | 変更の成功 |

### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| — | — | — | — | 入力だけ |

### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| すべて | PreferencesForm と同じ |

### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `form` |
| Keyboard interaction | Tab |
| Label / aria-label | 見える label。今は `autocomplete="current-password"`、新しいのは `new-password`（CR6.9） |
| Contrast ratio | WCAG AA |
| Screen reader | 誤りは項目に結び付けて読む |
| Focus management | 今のパスワードの誤りは今のパスワードの項目へ。新しいパスワードの値は消さない（AC5.1.7） |

## 9. UserMenu（既存のトップバー）

- 表示する名前をメールアドレスから氏名に変える（M7）。氏名を保存したらすぐ変わる。
- 項目に「プリファレンス」「パスワードの変更」を足す（Q2: B）。既存のログアウトはそのまま。
