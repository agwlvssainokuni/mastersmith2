# NFR Design — U5 DSL の管理画面（u5-dsl-admin-ui）— 設計の要点の確認

U5 の非機能の要件（`construction/u5-dsl-admin-ui/nfr-requirements/`）は、画面の時間の目標とセキュリティ・日英・アクセシビリティの決まりまで決まっている。新しく依頼者に尋ねる論点は無いため、作りの方針を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。

## 設計の要点（案）

- **描き方**（NFR1.19・NFR1.21）: 違いの表は、テーブルの行だけを描き、カラムの違いは行を開いたときだけ描く（開いていない行のカラムは描かない）。誤りの一覧は先頭 100 件だけを描く。メニューの木は既定で1段目だけを開く
- **ファイルの大きさ**（NFR1.20）: ファイルの大きさ（`File.size`、バイト数）で 10MB を先に判定し、超えたら読まずに案内する。貼り付けは文字列を UTF-8 のバイト列にした長さで判定する。送るときはファイルを文字列にせず、バイト列（`Blob`）のまま送る
- **画面の時間**（NFR1.18）: 画面を開いたときに今の状態とプレビューの2つを同時に要求する。時間は Build and Test の E2E（実際のブラウザー）で測って記録し、統合の関門のテストにしない
- **文字として表示**（NFR3.9）: DSL・対象DB・サーバーから来る文字列は React の文字として描く（`dangerouslySetInnerHTML` を使わない。リンタで止める）。画面部品のテストで、`<script>` を含む表示名が文字のまま出ることを確かめる
- **トークン**（NFR3.10）: API とダウンロードは既存の ApiClient だけを通す。ダウンロードは受け取ったバイト列から一時的な URL を作って保存させ、使い終わったらすぐに捨てる
- **JSON Schema のリンク**（NFR3.11）: 投入のタブに、同じオリジンの固定のパス（`/dsl/dsl-schema-v1.json`）への普通のリンクを置く（`download` 属性つき）。外の URL を組み立てない
- **`DSL_BUSY`**（NFR5.6）: 生成・投入・戻し・プレビューの表示で受けたら、画面の中の Alert で「ほかの処理中です。少し待ってからやり直してください」を示し、状態は読み直さない。入力は残す
- **日英**（NFR9.1）: 文言はすべて登録の `messages` に ja・en を対で置き、既存の登録の検査で欠けを止める。`DSL_BUSY` と 10MB の文言を足す（承認済みの U5 の機能設計との差は NFR 要件の tech-stack-decisions.md 2節）
- **アクセシビリティ**（NFR10.1）: 部品ごとに vitest-axe を1件。開閉のボタンは `aria-expanded`、処理中と結果は読み上げの領域（機能設計の BR7.x）
- **成果物**: U5 は ui のため、performance-design.md・security-design.md・logical-components.md・traceability.json を作る

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U5 の NFR 設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

---

## 確認の後の直し（2026-09-24、NFR Design の Request Changes）

上の要点の「ファイルの大きさ（NFR1.20）」にある「送るときはファイルを文字列にせず、バイト列（`Blob`）のまま送る」は、承認済みの機能設計の BR2.2（テキストとして読んで送る）と食い違うとレビューで指摘され、依頼者の Request Changes で取り消した。正は performance-design.md の NFR1.20（読む前に `File.size` で判定し、上限の内なら BR2.2 のとおりテキストとして読んで送る）。上の確認の記録は書き換えずに残す。
