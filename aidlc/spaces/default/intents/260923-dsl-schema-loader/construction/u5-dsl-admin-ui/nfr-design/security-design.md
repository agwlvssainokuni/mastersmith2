# Security Design — U5 DSL の管理画面（u5-dsl-admin-ui）

画面のセキュリティ・日英・アクセシビリティの要件（`construction/u5-dsl-admin-ui/nfr-requirements/security-requirements.md`）を満たす作りの方針。

| ID | 作り |
|---|---|
| NFR3.9 | DSL・対象DB・サーバーから来る文字列は React の文字として描く。`dangerouslySetInnerHTML` を使わない（リンタの `react/no-danger` で止める）。画面部品のテストで、`<script>` を含む表示名・コメント・誤りの文言が文字のまま出ることを確かめる |
| NFR3.10 | API とダウンロードは既存の ApiClient だけを通す（アクセストークンは ApiClient の中だけ）。ダウンロードは受け取ったバイト列から一時的な URL を作って保存させ、保存の後すぐに捨てる。トークンを URL・部品の状態・ログに入れない |
| NFR3.11 | 投入のタブに、同じオリジンの固定のパス `/dsl/dsl-schema-v1.json` への普通のリンク（`download` 属性つき）を置く。外の URL を組み立てない。ログインなしで取れる静的なファイルなので、トークンは付けない |
| NFR5.6 | 失敗の文言は `code` から選ぶ。`DSL_BUSY` を生成・投入・戻し・プレビューの表示で受けたら、画面の中の Alert で「ほかの処理中です。少し待ってからやり直してください」を示し、状態を読み直さず、入力は残す。`DSL_TOO_LARGE` の文言は「10MB まで」。`detail`・内部の文言・接続先は出さない |
| NFR9.1 | 文言はすべて登録の `messages` に ja・en を対で置き、既存の登録の検査で欠けを止める。物理名・識別は訳さない。英語にした画面部品のテストを置く |
| NFR10.1 | 部品ごとに vitest-axe を1件。開閉のボタンは `aria-expanded`、処理中と結果は読み上げの領域、確かめる表示は Modal のフォーカスの決まり（機能設計の BR7.x） |

## 承認済みの U5 の機能設計との差

NFR 要件の tech-stack-decisions.md 2節のとおり（10MB、`DSL_BUSY`、JSON Schema のリンク）。承認済みの機能設計は書き換えず、コード生成でこの文書に合わせる。
