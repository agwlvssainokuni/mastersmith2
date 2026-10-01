# デザインシステムの対応 — user-admin

デザインシステムは make-you-chic-ui（`vendor/make-you-chic-ui`、サブモジュール。このリポジトリから中身を変えない、PM）。画面の見本は既存の招待の管理の画面（`frontend/src/features/invitation/`）。テーマ（light・dark・system）・文字の大きさ（sm・md・lg）・ブランドカラーは既存の仕組みをそのまま使う。

## 1. 部品の対応

| 画面の要素 | make-you-chic-ui の部品 | 使い方 | 見本 |
|---|---|---|---|
| 利用者の一覧 | `Table`（`TableColumn`・`TableLabels`） | 列を render で描き、ページ送り（`page`・`pageSize`・`onPageChange`）と文言（`labels`）を画面から渡す | 招待の一覧（`InvitationList.tsx`） |
| 管理者の印・状態・ロック中・「あなた」 | `Badge` | 色と文字の両方で示す | 招待の一覧の状態 |
| 行の「操作」 | `Button`（secondary、小）＋ `Dropdown` | ボタンを `Dropdown` の trigger にする | — |
| 確かめの表示 | `Modal` ＋ `Button` | 背景のクリックで閉じない、はじめのフォーカスは「やめる」 | 招待の取り消し（`CancelConfirmDialog.tsx`） |
| 氏名・言語の入力 | `Modal`・`FormField`・`TextInput`・`RadioGroup`・`Button` | 入力欄ごとに `FormField` で見える label と誤りの文言 | 招待の入力（`InviteDialog.tsx`） |
| 成功の知らせ | `Toast`（`useToast`） | `role="status"` | 招待の画面 |
| 業務の失敗・読み込みの失敗・権限が無い | `Alert`（warning・error・info） | 操作の近くに残る形 | 招待の画面 |
| 検索 | `FormField`・`TextInput`・`Button` | `role="search"` | — |
| 画面の枠・サイドバー | `AppShell`（既存の骨組み） | サイドバーの管理のメニューに項目を足す | `features/<id>/registration.ts` |

## 2. 足りない部品（make-you-chic-ui に無いもの）

PM の学び（「make-you-chic-ui に無い機能を frontend の側で自前で作る設計にしたときは、承認の前に足りない点を一覧にし、make-you-chic-ui 側への取り込みを依頼者に諮る」）に従い、一覧にする。

| # | 足りない点 | 必要な理由 | 今の `Dropdown` | 案 |
|---|---|---|---|---|
| G1 | メニューの項目を押せない形にする（`disabled`・`aria-disabled`） | 自分の行の「印を外す」「止める」を押せない形で出す（RQ2 A・M6 B、AC2.1.9・AC3.1.8） | `MenuItem` は `label`・`href`・`onClick` だけで、押せない形を持たない | make-you-chic-ui の `MenuItem` に `disabled` を足す、または frontend の側でメニューを作る |
| G2 | メニューの項目に説明（理由）の文を添え、`aria-describedby` で結ぶ | 押せない理由を読めるようにする（AC2.1.9・AC3.1.8） | 説明の文を持たない | make-you-chic-ui の `MenuItem` に `description` を足す、または frontend の側で作る |
| G3 | 押せない項目にもキーボードのフォーカスが移ること | 読み上げで押せない理由を聞けるようにする（WAI-ARIA のメニューの推奨） | 押せない項目が無いため、移り方が決まっていない | G1 と合わせて決める |

G1〜G3 は同じ部品の拡張なので、まとめて扱う。

**扱いの決定（依頼者、この段の確認）**: make-you-chic-ui の側で検討する。依頼文は `aidlc/spaces/default/intents/260930-user-admin/inception/refined-mockups/make-you-chic-ui-request.md`。取り込みがコード生成に間に合わないときの扱いは、Delivery Planning とコード生成の計画で決める。

- 取り込む場合: make-you-chic-ui のリポジトリ側で直し、固定先の更新は承認を得た専用のコミットで行う（PM の Mandated）。固定先の更新を含む Bolt は fast-forward で統合してよい（TP）。
- frontend の側で作る場合: `Dropdown` を使わず、同じ見た目の小さなメニューを作る。後で make-you-chic-ui に取り込まれたら置き換える。

## 3. 文言と言語

- 画面の文言は ja・en の両方を、画面の機能の `messages.ts`（招待の画面と同じ形）に置く。
- `Table` のページ送り・空の文言は `labels` で利用者の言語を渡す（既定の日本語に固定しない）。
- サーバーの誤りの説明文の言語は、画面が要求に付ける言語で決まる（ストーリーの前提）。

## 4. 色・状態の表し方

| 状態 | `Badge` の文字（ja / en） | 色の役割 | 補足 |
|---|---|---|---|
| 管理者 | 管理者 / Admin | info | 印が無いときは「—」（読み上げ「なし」） |
| 有効 | 有効 / Active | success | |
| 利用停止 | 利用停止 / Suspended | neutral または warning | 色だけでなく文字で示す |
| ロック中 | ロック中 / Locked | warning | 解除の予定の時刻を文字で添える |
| あなた | あなた / You | neutral | 自分の行の氏名の横 |

色の役割の具体の値は make-you-chic-ui の `Badge` の種類から選び、light・dark の両方で文字と背景のコントラスト 4.5:1 を確かめる（`accessibility-checklist.md`）。
