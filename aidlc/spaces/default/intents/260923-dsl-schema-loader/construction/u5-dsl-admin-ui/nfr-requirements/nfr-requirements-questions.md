# NFR Requirements — U5 DSL の管理画面（u5-dsl-admin-ui）— 設計の要点の確認

U5 の受け持ちは、要件の NFR9（日英）と NFR10（アクセシビリティ）です（`inception/units-generation/unit-of-work.md`）。前の単位（U2〜U4）の NFR 要件で決まったことのうち、画面に関わるものを受け、画面の性能の目標を足します。新しく依頼者に尋ねる論点は無いため、要点を案として確かめます（`aidlc/spaces/default/memory/project.md` の Way of Working）。

## 前の単位から受けること（承認済みの U5 の機能設計との差）

承認済みの U5 の機能設計（`construction/u5-dsl-admin-ui/functional-design/`）は書き換えず、差をこの単位の tech-stack-decisions.md に記録し、コード生成で実装する（project.md の Way of Working）。

| 決定 | 出どころ | U5 での扱い |
|---|---|---|
| DSL の大きさの上限 10MB | U3 の NFR 要件（Q1: B） | 送る前の案内（BR2.3）を 10MB（10 × 1024 × 1024 バイト）に、入力の欄の案内と `DSL_TOO_LARGE` の文言を「10MB まで」にする |
| 新しい code `DSL_BUSY`（503） | U4 の NFR 要件（Q2: A） | 文言「ほかの処理中です。少し待ってからやり直してください」（ja・en）を足す。生成・投入・戻し・プレビューの表示で受けたら、画面の中の Alert で示し、状態は読み直さない（何も変わっていないため） |
| JSON Schema のダウンロードのリンク | U2 の NFR 要件（Q5: B・F1: A） | 投入のタブに「DSL の書式（JSON Schema）」のリンクを置く（ログインなしで取れる静的なファイル、例 `/dsl/dsl-schema-v1.json`）。ダウンロードは普通のリンク（アクセストークンは不要） |

## 設計の要点（案）

- **画面の性能**: DSL の管理画面を開いてから、今の状態とプレビューが表示されるまで、想定の規模のプレビューで 3 秒以内（API の 10 秒の照合を含まないとき。照合を含む表示は API の 10 秒に画面の 1 秒を足した 11 秒以内）。違いの表は、開いた行のカラムだけを描く（100 テーブル × 100 カラムを一度に描かない）。10MB のファイルを選んでから送るまでの読み込みは 2 秒以内。Build and Test（E2E または画面部品のテスト）で測る
- **日英**（NFR9）: 画面の文言はすべて登録の `messages` に ja・en を対で置き、欠けがないことを単体テストで確かめる（既存の登録の検査）。物理名・識別は訳さない
- **アクセシビリティ**（NFR10）: 部品ごとに vitest-axe で違反 0 件（機能設計の BR7.4）。WCAG 2.1 AA（Refined Mockups）
- **セキュリティ**: DSL・対象DB から来る文字列（表示名・コメント・誤りの文言・警告）は文字として表示し、HTML として埋め込まない（`react/no-danger`）。アクセストークンは既存の ApiClient だけが扱う。JSON Schema のリンクは同じオリジンの静的なファイルだけを指す
- **成果物**: U5 は ui のため、performance-requirements.md・security-requirements.md・tech-stack-decisions.md（差の一覧を含む）・traceability.json を作る

## Consolidated Summary Confirmation

- 上の「前の単位から受けること」と「設計の要点（案）」のとおりに、U5 の NFR 要件の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
