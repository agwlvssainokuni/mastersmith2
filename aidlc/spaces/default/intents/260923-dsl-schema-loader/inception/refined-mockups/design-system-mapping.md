# Design System Mapping — DSL の管理画面

デザインシステム make-you-chic-ui（`vendor/make-you-chic-ui`、Git サブモジュール）の部品との対応を示す。サブモジュールの中身はこのリポジトリから変更しない（`aidlc/spaces/default/memory/project.md` の Forbidden）。make-you-chic-ui に無い部品は、`frontend/` の側で作る。

## 1. 使う部品

| 画面の要素（`mockups.md`） | make-you-chic-ui の部品 | 使い方 |
|---|---|---|
| 画面の枠・サイドバーの「DSL」 | AppShell | 既存の管理者向け領域と同じく、機能の登録（`features/<featureId>/registration.ts`）で `layout: 'SHELL'`・`access: 'ADMIN'`、サイドバーは `visibleWhen: 'ADMIN'` |
| 今の状態の枠 | Card | 見出し「今の状態」を持つ Card |
| スキーマを読み込む・投入する・適用する | Button（primary） | 画面の区切りごとに主な操作は1つ |
| ダウンロード・やめる・最新のプレビューを表示する | Button（secondary） | 補助の操作 |
| 破棄する | Button（destructive に当たる見た目） | 危険な操作。見た目の種類は make-you-chic-ui の Button の variant に合わせる |
| タブ（プレビュー・投入・履歴） | Tabs | 3つのタブ |
| 区分（増えた・減った・変わった・変わらない）、適用中の目印 | Badge | 文字で示す。色は補助 |
| 検証を通った・警告・誤りの件数・拒否 | Alert | 成功・警告・失敗の種類を使い分ける。記号と文言を必ず付ける |
| 成功の知らせ | Toast | 読み込み・投入・戻し・適用・破棄の成功 |
| 違いの表・誤りの一覧・履歴の表 | Table | 列見出しつき。狭い幅では横に動かす |
| 入力のしかた（ファイル・貼り付け） | RadioGroup | どちらか一方を選ぶ |
| 貼り付けの欄 | Textarea（FormField と組み合わせる） | label と案内・誤りの結び付けは FormField に従う |
| すべて表示 | Switch | 違いの表の表示の切り替え |
| 確かめる表示 | Modal | 置き換え・適用・破棄 |
| 識別の全体の表示 | Tooltip | ハッシュ値の全体 |
| 開閉・状態の記号 | Icon | 記号は文字の代わりにしない（文字と並べる） |

## 2. 画面の側で作る部品（make-you-chic-ui に無いもの）

| 部品 | 理由 | 作り方の方針 |
|---|---|---|
| ファイルの選択（FilePicker） | make-you-chic-ui に無い | ネイティブの `input type="file"` を見える label と Button の見た目で包む。受け付ける形は `.yaml`・`.yml`、5MB を超えるときは送る前に知らせる |
| 違いの表の行の開閉（DiffRow） | Table に行の開閉が無い | Table の行の先頭に開閉のボタン（`aria-expanded`）を置き、開いたときに次の行としてカラムの違いの表を出す |
| メニューの木（MenuTree） | 木の表示の部品が無い | 入れ子のリスト（`ul`）と開閉のボタンで作る。ARIA の `tree` の役割は使わず、リストとボタンで表す（キーボードの操作を単純にするため） |

これらの部品は `frontend/` の中に置き、make-you-chic-ui の見た目の値（色・余白・文字の大きさ）を使う。同じ部品が後続の Intent でも要るようになったら、make-you-chic-ui の側への追加を依頼者と相談する（このリポジトリからは変更しない）。

## 3. 見た目の値（デザイントークン）

- 色・余白・文字の大きさ・角の丸み・影は、make-you-chic-ui の値だけを使い、この画面で独自の値を作らない。
- 区分のバッジの色は、「増えた」「減った」「変わった」「変わらない」で make-you-chic-ui の Badge の種類を使い分ける。色だけで区別しない（文字を必ず出す）。
- 狭い幅での配置の切り替えは、AppShell の振る舞いに合わせる（`mockups.md` の7節）。

## 4. 画面の文言

- 文言は機能の登録の `messages` に ja・en を対で持つ（既存の `features/admin/registration.ts` と同じ形）。
- 物理名（テーブル名・カラム名）と、DSL の中の場所（例: `tables.dept_mst.columns.dept_name`）は訳さない。
- サーバーの `code` から文言を選ぶ（`interaction-spec.md` の共通の決まり）。
