# Tech Stack Decisions — U5 DSL の管理画面（u5-dsl-admin-ui）

U5 の技術の選定と、承認済みの U5 の機能設計との差を示す。

## 1. 選定

U5 は新しい依存を足さない。既存の React・TypeScript・make-you-chic-ui・react-i18next・Vitest・Testing Library・vitest-axe を使う（`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）。make-you-chic-ui に無い部品（ファイルの選択・違いの表の行の開閉・メニューの木）は frontend の側で作る（Refined Mockups の方針。サブモジュールは変更しない）。

## 2. 承認済みの U5 の機能設計との差

承認済みの文書は書き換えない（`aidlc/spaces/default/memory/project.md` の Way of Working）。コード生成でこの表に合わせて実装する。

| 決定 | 出どころ | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| DSL の大きさの上限 10MB | U3 の NFR 要件（Q1: B） | frontend-components.md の BR2.3（5 × 1024 × 1024 バイト）、functional-spec.md の 6節・9節（5MB）、Refined Mockups の入力の欄の案内（5MB まで） | 10 × 1024 × 1024 バイト、文言は「10MB まで」 |
| code `DSL_BUSY`（503） | U4 の NFR 要件（Q2: A） | functional-spec.md の 6節に無い | 文言「ほかの処理中です。少し待ってからやり直してください」（ja・en）。生成・投入・戻し・プレビューの表示で受けたら画面の中の Alert で示し、状態は読み直さない（何も変わっていないため） |
| JSON Schema のリンク | U2 の NFR 要件（Q5: B・F1: A） | 画面の部品に無い | 投入のタブに「DSL の書式（JSON Schema）」のリンクを置く。ログインなしで取れる静的なファイルを、普通のリンクで開く |
