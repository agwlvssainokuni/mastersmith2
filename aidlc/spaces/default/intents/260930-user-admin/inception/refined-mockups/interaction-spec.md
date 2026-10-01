# 操作の仕様 — user-admin（利用者の管理）

部品ごとの仕様は `.claude/knowledge/aidlc-design-agent/component-spec-template.md` の形で書く。画面の見た目と受け入れ基準の対応は `mockups.md`、部品の対応は `design-system-mapping.md`。部品の名前は仮のもので、置き場と名前は機能設計で決める。

## 1. 利用者の流れ

### F1 利用を止める（P1 管理者）

- **きっかけ**: 退職・異動の連絡を受ける。
- **手順**:
  1. S1 → 検索に氏名を入れる（〔Should〕）か、ページを送る → 対象の行が出る
  2. 行の「操作」 → S2 のメニュー → 「利用を止める」
  3. S3（止める） → 対象と効き目を読み「利用を止める」 → 送信中は押せない
  4. 成功 → S5 の知らせ「〔氏名〕さんの利用を止めました」 → 一覧を読み直し、行は [利用停止]、操作は「停止を解く」に変わる
- **失敗の道**:
  - 自分の行 → 「利用を止める」は押せない（理由つき）
  - 最後の有効な管理者 → S5 の失敗の文言 → 先にほかの利用者に印を付ける
  - すでに停止中（ほかの管理者が先に止めた） → 「すでにこの状態です」 → 一覧を読み直す
  - 403 → S6

### F2 ロックを解除する（P1 管理者）

- **きっかけ**: 「ログインできない」の問い合わせ。
- **手順**: S1 → 対象の行に [ロック中] 〇〇まで → 「操作」 → 「ロックを解除（失敗回数を戻す）」 → S3 → 「ロックを解除」 → S5 の知らせ → 行の [ロック中] が消え、メニューから項目が消える → 相手に「もう入れます」と伝える。
- **失敗の道**: 解除の予定の時刻を過ぎて回数も消えていた（ほかの管理者が戻した） → 「すでにこの状態です」 → 一覧を読み直す。

### F3 管理者の印を付け替える（P1 管理者）

- **手順**: 新しい担当の行 → 「管理者の印を付ける」 → S3 → 実行 → 前の担当の行 → 「管理者の印を外す」 → S3 → 実行。
- **失敗の道**: 停止中の利用者 → 印の項目が出ない（先に停止を解く）。前の担当が自分 → 「管理者の印を外す」は押せない（ほかの管理者に頼む）。

### F4 管理の画面が使えなくなる（P2 元の管理者）

- **きっかけ**: ほかの管理者に印を外された後、開いていた管理の画面で操作する。
- **手順**: 操作 → 403 → S6「この画面を使う権限がありません」と「ホームへ戻る」 → 管理のメニューが消える → 自分の設定などは今までどおり使える。

### F5 止められている（P2 利用者）

- 開いていた画面で操作 → 401 → トークンの更新も 401 → ログインの画面へ（止められた旨は出さない。既存の動き、AC3.2.9）。ログインの画面ではパスワードを誤ったときと同じ文言（AC3.2.10）。

## 2. 部品の仕様

### UserAdminPage（利用者の管理の画面）

| Field | Value |
|---|---|
| Component | UserAdminPage |
| Description | 利用者の一覧・検索・ページ送りと、行の操作・確かめ・入力・結果の知らせをまとめる画面 |
| Category | layout |

#### States

| State | Description | Trigger |
|---|---|---|
| loading | 初めの読み込み。行の形の置き換えと「利用者を読み込んでいます…」 | 画面を開く |
| reloading | 前の行を残し、読み込み中の文言を出し、行の操作を押せなくする | 操作の後・ページ送り・検索 |
| populated | 一覧を表示 | 一覧の応答 200 |
| empty-search | 検索で 0 件。〔Should〕 | 検索の応答 200・0 件 |
| empty-page | 最後のページより後。「このページに利用者はいません」 | 応答 200・0 件・全体は1件以上 |
| load-error | 読み込みの失敗。前の行は出さない | 5xx・通信の失敗 |
| forbidden | S6 に置き換え | 403 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| （なし） | — | — | — | 画面の登録（`features/<id>/registration.ts`）から開く。ページの番号と検索の文字は画面の状態に持つ |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 狭い画面向けの形は作らない。表ははみ出したら横にずれて見え、文字や操作が重ならない（RQ7 C） |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 既定の形 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | 見出しは `h1`「利用者の管理」。一覧は `table`（`Table`）、読み込み中の文言は `role="status"`、読み込みの失敗と業務の失敗は `Alert` |
| Keyboard interaction | 検索の入力 → 検索 → 検索を消す → 表の行の「操作」 → ページ送り、の順に Tab で移れる |
| Label / aria-label | 検索の入力に見える `label`（「検索」）と説明（上限の文字数） |
| Contrast ratio | WCAG AA（4.5:1 text, 3:1 UI components） |
| Screen reader | 読み込み中・読み込みの失敗・成功・失敗を読み上げる。一覧に `aria-busy` |
| Focus management | 読み直しの後は、操作した行の「操作」ボタンへ。行が見えなくなったら一覧の見出しへ |

### UserTable（利用者の一覧の表）

| Field | Value |
|---|---|
| Component | UserTable |
| Description | make-you-chic-ui の `Table` で利用者の行を表示し、ページ送りを持つ |
| Category | display |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 行を表示 | 一覧の応答 |
| busy | 行の操作を押せない | reloading・送信中 |
| empty | `Table` の空の表示（文言は画面が渡す） | 0 件 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| rows | object[] | yes | — | 利用者の要約（メールアドレス・氏名・印・停止・ロック中・解除の予定の時刻・戻せるか・登録した日時・自分か） |
| totalCount | number | yes | — | 全体の件数 |
| page | number | yes | — | 今のページ（1 から） |
| onPageChange | (page: number) => void | yes | — | ページ送り |
| busy | boolean | no | false | 行の操作を押せなくする |
| labels | TableLabels | yes | — | ページ送り・空の文言を利用者の言語で渡す |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 表の外側を横にずらせる。列は減らさない |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 既定 |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `table`・`columnheader`・`row`（`Table` が持つ）。表に見出し（`caption` または `aria-label`「利用者の一覧」） |
| Keyboard interaction | Tab で行の「操作」とページ送りへ |
| Label / aria-label | `Badge` の文字（[管理者] など）をそのまま読み上げる。「—」は「なし」と読ませる |
| Contrast ratio | WCAG AA。`Badge` の文字と背景は light・dark の両方で 4.5:1 |
| Screen reader | ページの状態「1 / 3ページ（全 47 件）」を読み上げる |
| Focus management | ページ送りの後は一覧の見出しへ |

### UserRowActions（行の操作のメニュー）

| Field | Value |
|---|---|
| Component | UserRowActions |
| Description | 行の「操作」ボタンと、その行で今できる操作のメニュー。自分の行の押せない項目を理由つきで出す |
| Category | navigation |

#### States

| State | Description | Trigger |
|---|---|---|
| closed | 「操作」ボタンだけ | 既定 |
| open | メニューが開く | ボタンを押す・Enter・Space・↓ |
| item-disabled | 自分の行の「印を外す」「止める」。押せない形と理由 | 自分の行 |
| busy | ボタンが押せず読み上げの名前が「処理中」 | 送信中・reloading |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| user | object | yes | — | 行の利用者の要約 |
| isSelf | boolean | yes | — | 自分の行か |
| busy | boolean | no | false | 押せなくする |
| onSelect | (action: 'grantAdmin' \| 'revokeAdmin' \| 'suspend' \| 'resume' \| 'resetFailures' \| 'editProfile') => void | yes | — | 項目を選んだとき |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | メニューは画面からはみ出さない位置に開く（`Dropdown` の位置の計算） |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 行の右端の下に開く |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | ボタンは `aria-haspopup="menu"`・`aria-expanded`、メニューは `menu`・`menuitem`（WAI-ARIA のメニューの形） |
| Keyboard interaction | Enter・Space・↓ で開く。↑↓ で項目を移り、Enter で選ぶ。Escape で閉じてボタンへ戻る。押せない項目にもフォーカスは移り、読み上げで理由が分かる |
| Label / aria-label | ボタンの名前は「〔氏名〕（〔メールアドレス〕）の操作」 |
| Contrast ratio | WCAG AA。押せない項目も文字は 4.5:1 を保つ（押せないことは文字の理由でも示す） |
| Screen reader | 押せない項目は `aria-disabled="true"` と、理由を `aria-describedby` で読む |
| Focus management | 項目を選ぶとメニューを閉じ、確かめ・入力の表示へ。閉じたらボタンへ戻る |

### ConfirmActionDialog（確かめの表示）

| Field | Value |
|---|---|
| Component | ConfirmActionDialog |
| Description | 5つの操作の前の確かめ。対象の氏名・メールアドレスと効き目を示す |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| open | 見出し・対象・効き目・やめる・実行 | メニューで操作を選ぶ |
| submitting | 両方のボタンを押せず、実行のボタンが「処理中」 | 実行を押す |
| closed | 閉じる | やめる・Escape・[×]・応答を受けた・403 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| action | 'grantAdmin' \| 'revokeAdmin' \| 'suspend' \| 'resume' \| 'resetFailures' | yes | — | 操作の種類（見出し・効き目・ボタンの文言が決まる） |
| user | object | yes | — | 対象の氏名・メールアドレス |
| onConfirm | () => Promise<void> | yes | — | 実行 |
| onCancel | () => void | yes | — | やめる |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | `Modal` の既定（画面の幅に合わせる） |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 中央に出す |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`・`aria-modal="true"`（`Modal`） |
| Keyboard interaction | フォーカスは表示の中に閉じ込める。Escape で閉じる。背景のクリックでは閉じない |
| Label / aria-label | `aria-labelledby` は見出し、`aria-describedby` は対象と効き目の文 |
| Contrast ratio | WCAG AA。危険のボタン（止める・印を外す）の文字と背景も 4.5:1 |
| Screen reader | 開いたら見出しと効き目を読む |
| Focus management | はじめは「やめる」。閉じたら開いた行の「操作」ボタンへ（読み直しで行が変わっても同じ利用者の行） |

### EditProfileDialog（氏名・言語の入力）

| Field | Value |
|---|---|
| Component | EditProfileDialog |
| Description | 対象の利用者の氏名と言語を直す入力 |
| Category | input |

#### States

| State | Description | Trigger |
|---|---|---|
| open | 今の値が入った入力 | メニューで「氏名・言語を直す」 |
| invalid | 入力欄のすぐ下に誤りの文言（入力は消えない） | 画面の確かめ・サーバーの入力の誤り |
| submitting | 保存とやめるを押せない | 保存を押す |
| not-found | 「対象の利用者が見つかりません。一覧を読み直してください。」 | 対象がいない |
| closed | 閉じる | 保存の成功・やめる・Escape・[×]・403 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| user | object | yes | — | 対象（氏名・言語・メールアドレス） |
| onSave | (input: { displayName: string; language: 'ja' \| 'en' }) => Promise<void> | yes | — | 保存 |
| onCancel | () => void | yes | — | やめる |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | `Modal` の既定 |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 中央に出す |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `dialog`（`Modal`）。言語は `RadioGroup` |
| Keyboard interaction | Tab で氏名 → 言語 → やめる → 保存。言語は矢印キーで選ぶ |
| Label / aria-label | 氏名・言語に見える `label`（`FormField`）。必須を文字で示す |
| Contrast ratio | WCAG AA。誤りの文字も light・dark で 4.5:1（PM の学び: dark の FormField の誤りの文字） |
| Screen reader | 誤りは `aria-describedby` と `aria-invalid` で読む |
| Focus management | はじめは氏名の入力欄。保存の誤りのときは最初の誤りの入力欄へ。閉じたら開いた行の「操作」ボタンへ |

### AdminForbiddenNotice（権限が無いときの表示）

| Field | Value |
|---|---|
| Component | AdminForbiddenNotice |
| Description | 管理の画面すべてで 403 のときに出す表示と「ホームへ戻る」 |
| Category | feedback |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 「この画面を使う権限がありません」と「ホームへ戻る」 | 管理の API の 403 |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| （なし） | — | — | — | 文言は利用者の言語。出すときにログインの状態の読み直しを呼ぶ |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 文言が折り返して読める |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 画面の見出しの下に出す |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `Alert`（info）。出したときに読み上げる |
| Keyboard interaction | 「ホームへ戻る」に Tab で移れる |
| Label / aria-label | 文言そのもの |
| Contrast ratio | WCAG AA |
| Screen reader | 「この画面を使う権限がありません」を読む |
| Focus management | 出したら画面の見出しへフォーカスを移す（403 の前のフォーカスの位置が消えるため） |

### UserSearchBox（検索、〔Should〕）

| Field | Value |
|---|---|
| Component | UserSearchBox |
| Description | メールアドレス・氏名の部分一致の検索 |
| Category | input |

#### States

| State | Description | Trigger |
|---|---|---|
| default | 入力・検索・検索を消す | 既定 |
| invalid | 上限を超える・サーバーの入力の誤り。入力欄のすぐ下に文言 | 入力・応答 |
| disabled | 押せない | reloading |

#### Props / Inputs

| Prop | Type | Required | Default | Description |
|---|---|---|---|---|
| value | string | yes | — | 今の検索の文字 |
| maxLength | number | yes | — | 上限（機能設計で決める） |
| onSearch | (text: string) => void | yes | — | 検索（1ページ目に戻す） |
| onClear | () => void | yes | — | 検索を消す |

#### Responsive Behaviour

| Breakpoint | Behaviour |
|---|---|
| mobile (<768px) | 入力とボタンが折り返して重ならない |
| tablet (768–1024px) | 同上 |
| desktop (>1024px) | 1行に並べる |

#### Accessibility

| Requirement | Implementation |
|---|---|
| ARIA role | `form`（`role="search"`）、入力は `TextInput` |
| Keyboard interaction | Enter で検索。検索を消すで入力を空にして全体に戻す |
| Label / aria-label | 見える `label`「検索」と、上限の説明を `aria-describedby` |
| Contrast ratio | WCAG AA |
| Screen reader | 結果の件数の変化を `role="status"` で読む（「47 件中 2 件」など） |
| Focus management | 検索の後も入力欄に残す |

## 3. 操作と送信の決まり（共通）

- 操作の送信中は、その行の「操作」と、開いている確かめ・入力のボタンを押せなくする（二重の送信の防止）。
- 応答を受けたら、成功・失敗のどちらでも一覧を読み直す（今のページと検索の文字を保つ）。
- 401 は今の共通の仕組み（トークンの更新を試み、だめならログインの画面へ）に任せる。
- 403 はどの管理の画面でも AdminForbiddenNotice に置き換え、ログインの状態を読み直す。
- 時間のかかる操作の知らせ: 送信から 5 秒を過ぎても応答が無いときは「時間がかかっています」を確かめ・入力の表示の中に出す（UX の決まり）。
- `prefers-reduced-motion` のときは、Modal とメニューの開閉の動きを出さない。
