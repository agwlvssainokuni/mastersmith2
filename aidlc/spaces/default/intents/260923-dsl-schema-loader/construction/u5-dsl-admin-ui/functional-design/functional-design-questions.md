# Functional Design — U5 DSL の管理画面（u5-dsl-admin-ui）— 設計の要点の確認

U5 の画面の組み立て・状態・部品・アクセシビリティは、Refined Mockups（`inception/refined-mockups/` の mockups.md・interaction-spec.md・design-system-mapping.md・accessibility-checklist.md）と契約 C6 で決まっている。新しく決めるのは、画面の中の細部（置き場・状態の持ち方・読み直し・ファイルの読み方・API の受け渡しの広げ方）である。これを案として示して確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。

## 設計の要点（案）

- **置き場**: 画面の機能 `frontend/src/features/dsl/`。URL は `/admin/dsl`、`layout: 'SHELL'`・`access: 'ADMIN'`。サイドバーの項目「DSL」は `visibleWhen: 'ADMIN'` で、「管理」（order 200）の次の order 210。
- **状態の持ち方**: 画面の中の状態（今の状態・プレビューの中身・履歴・処理中・誤りの一覧・入力）は、画面の部品の中で持つ（アプリ全体の状態の置き場は作らない）。画面を開いたときに今の状態を読み、プレビューのタブを開いたときにプレビューの中身、履歴のタブを開いたときに履歴を読む。
- **読み直し**: 読み込み・投入・戻し・適用・破棄の成功の後、適用の拒否（409）の後、プレビューが無い（404）の応答の後に、今の状態と開いているタブの中身を読み直す。自動の定期的な読み直しはしない。
- **適用の拒否の見分け**: 409 を受けたら今の状態を読み直し、プレビューが別のもの（識別が違う）なら「置き換えられた」、無ければ「破棄された」と表示を分ける（mockups.md の 3.6）。
- **ファイルの読み方**: 選んだファイルは、画面でテキストとして読み、`application/yaml` の本文として送る（`source=UPLOAD`）。貼り付けは `source=PASTE`。送る前の大きさの案内は、ファイルのバイト数と、貼り付けの文字列を UTF-8 にしたときのバイト数で判定する（案内だけで、サーバー側の検査の代わりにしない）。
- **ダウンロード**: API の応答をファイルとして保存させる。ファイル名は応答の指定（`Content-Disposition`）に従う。
- **API の受け渡しの広げ方**（ApiClient の拡張）: 今は誤りの応答の `code` と状態しか画面に渡していない。Problem Details の追加の項目（`errors`・`total` など）も、呼び出し元が型を決めて受け取れるようにする。既存の呼び出し方と戻り値の形は変えない（項目を足すだけ）。
- **文言**: 機能の登録の `messages` に ja・en を対で置く。サーバーの `code` ごとの文言と、誤りの `kind` ごとの見出しを持つ。誤りの一つ一つの内容は、サーバーが表示言語で返した文言をそのまま示す。
- **日時の表示**: UTC で受け取り、表示言語に合わせた書式で、利用者の時差で表示し、時差の略号を添える。
- **確かめる表示・処理中・アクセシビリティ**: interaction-spec.md と accessibility-checklist.md のとおり。

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U5 の機能設計の文書（functional-spec.md（画面の部品の構成を含む）・traceability.json）を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
