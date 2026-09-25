# Design System Mapping — user-management

デザインシステム make-you-chic-ui（`vendor/make-you-chic-ui`、Git サブモジュール）の部品との対応を示す。サブモジュールの中身はこのリポジトリから変更しない（`aidlc/spaces/default/memory/project.md` の Forbidden）。make-you-chic-ui に無い部品・値は `frontend/` の側で作る。画面は `mockups.md`、動きは `interaction-spec.md`。出典の答えは `refined-mockups-questions.md`、ストーリーは `aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`。

## 1. 使う部品

| 画面の要素 | make-you-chic-ui の部品 | 使い方 |
|---|---|---|
| S1・S4・S5 の画面の枠、サイドバーの「利用者の招待」 | AppShell | 機能の登録（`features/<featureId>/registration.ts`）で S1 は `layout: 'SHELL'`・`access: 'ADMIN'`、サイドバーは管理者だけに出す。S4・S5 は `access: 'LOGGED_IN'` |
| ユーザーメニューの「プリファレンス」「パスワードの変更」 | AppShell のユーザーメニュー（`AppShellUser`・Dropdown） | 機能の登録のユーザーメニューの項目に足す。表示する名前は氏名（M7） |
| 「招待する」「登録を完了する」「保存する」「変更する」「ログイン」 | Button（primary） | 区切りごとに主な操作は1つ |
| 「やめる」「元に戻す」「送り直す」「前へ」「次へ」 | Button（secondary） | 補助の操作 |
| 「取り消す」（行と確かめの Modal） | Button（危険な操作の variant） | make-you-chic-ui の Button の variant に合わせる |
| メールアドレス・氏名・パスワードの入力 | TextInput と FormField | label・案内・誤りの結び付けは FormField に従う。パスワードは `type="password"` と autocomplete |
| 言語・テーマ・文字の大きさの選択 | RadioGroup・Radio | 選択肢が2〜3個のため Select ではなく RadioGroup |
| S1-M1・S1-M2 | Modal（ModalStackProvider） | 背景のクリックで閉じない設定、初期フォーカスの指定 |
| 成功の知らせ | Toast（useToast） | 招待・送り直し・取り消し・保存・変更の成功 |
| 失敗・招待を使えない警告・「このリンクは使えません」・登録の完了の知らせ | Alert | 種類（失敗・警告・情報・成功）を使い分け、記号と文言を必ず付ける |
| 送信の結果・期限切れの印 | Badge | 文字で示し、色は補助（CR6.5） |
| 登録の完了・ログインの画面の枠 | Card | AppShell の外の単独の画面の中央のカード |
| 記号 | Icon | 文字の代わりにしない |
| テーマ・文字の大きさ・ブランドカラー・フォントファミリーを当てる | ThemeProvider・useTheme | 下の「3. 表示の設定の当て方」 |

## 2. 画面の側で作る部品・仕組み（make-you-chic-ui に無いもの）

| 部品・仕組み | 理由 | 作り方の方針 |
|---|---|---|
| 招待の一覧の表とページ送り | make-you-chic-ui の Table はページ送りの文言が日本語に固定で、英語の表示を満たせない（前の Intent と同じ判断） | 見た目を make-you-chic-ui の Table に合わせた素の `table` と、文言を ja・en で持つページ送り（20件ずつ、Q7: B） |
| テーマ `system` | make-you-chic-ui の ThemeMode は light・dark の2値だけで、開いている間の OS の切り替えに追従しない（`aidlc/spaces/default/codekb/mastersmith2/component-inventory.md` の K-4） | frontend の側で `system` を持ち、`prefers-color-scheme` を見て `setTheme` に light・dark を渡す。`system` そのものは frontend の側で保存する。開いている間の追従は機能設計で決める |
| 利用者の設定をブラウザに保存する口 | make-you-chic-ui は軸ごとに localStorage に保存するが、`system` と言語を持たない | frontend の側で「最後に保存された表示の設定」（言語・テーマ・文字の大きさ）をまとめて持ち、make-you-chic-ui の保存と食い違わないように当てる（仕組みは機能設計） |
| 表示の言語の切り替えの口 | 今の I18nProvider は起動時に1回だけ言語を決め、切り替えの口が無い（K-5） | frontend の側に切り替えの口を作り、`<html lang>` とログインの前の要求の言語もあわせて変える |
| LoginLanguageSwitch | make-you-chic-ui に無い（Could） | Button の組か RadioGroup で作る |

## 3. 表示の設定の当て方

| 軸 | 値（make-you-chic-ui） | 決め方 | 出典 |
|---|---|---|---|
| テーマ | light・dark（system は frontend の側で解く） | ログインの後は利用者の設定、ログインの前はブラウザに最後に保存された値 | FR5.3〜FR5.5 |
| 文字の大きさ | sm・md・lg（画面の文言は「小・標準・大」） | 同上 | FR5.2 |
| ブランドカラー | blue・green・purple・orange | `application.yml` のインスタンスの設定（無い・誤りは blue） | FR8、CR2（Should） |
| フォントファミリー | sans・serif | 同上（無い・誤りは sans） | FR8、CR2（Should） |

- ブランドカラーとフォントファミリーを画面へ渡す方法（ログインなしで読める API か、配信する画面への埋め込みか）と、前に保存された値で一瞬描かれることの防ぎ方は機能設計で決める（ストーリーの「後の段に回す点」）。
- make-you-chic-ui のブランドカラー・フォントファミリーの localStorage の値は、インスタンスの設定で上書きする（利用者ごとには変えられない、FR8.1）。

## 4. 文言

- 新しい画面の文言は ja・en の両方を機能の登録の文言（鍵は `<featureId>.` で始める）として持つ（NFR8、`aidlc/spaces/default/codekb/mastersmith2/code-structure.md`）。
- テーマの選択肢の文言は「OS に合わせる」「ライト」「ダーク」／「Match OS」「Light」「Dark」、文字の大きさは「小」「標準」「大」／「Small」「Medium」「Large」とする。
