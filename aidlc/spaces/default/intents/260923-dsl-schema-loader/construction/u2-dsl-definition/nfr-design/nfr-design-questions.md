# NFR Design — U2 DSL の定義（u2-dsl-definition）— 設計の要点の確認

U2 の非機能の要件（`construction/u2-dsl-definition/nfr-requirements/`）は、部品の選定と数値まで決まり、ADR-008 の試しで作り方も確かめた。新しく依頼者に尋ねる論点は無いため、作りの方針を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。

## 設計の要点（案）

- **読み込みの流れ**: 本文のバイト数で 10MB を確かめる → SnakeYAML の安全な読み込み（`LoaderOptions`: 深さ 50・コレクションを指す別名 100・重複キーの禁止・タグの検査で全拒否・文字数の上限は 10MB）で節の木を作る（`compose`。値の型は作らない）→ 節の木から検証用の JSON の形（Jackson 3 の `JsonNode`）と位置の対応表（パス → 行・列）を同時に作る。別名を展開した後の節の数を数え、1,000,000 を超えたら ALIAS_LIMIT で打ち切る。別名で参照した値の位置は、参照を書いたキーの位置にする（試しで確かめた作り）
- **位置の対応表**: キーは JSON Pointer の形のパス（networknt の誤りのパスと同じ形）。値は行・列（1 から）。必須の欠けは対象の対応表のキーの位置、知らない項目はその項目のキーの位置を引く
- **JSON Schema の検証**: networknt 3.0.6 の `SchemaRegistry`（2020-12、外部を取りに行かない設定を明示）で、同梱の JSON Schema を起動時に1回だけ読み込んで使い回す（検証のたびに読まない）。誤りは「キーワード・パス・埋める値」から文言の鍵にし、部品の文言は使わない
- **意味の検証**: 検証用の JSON の形ではなく、読み込んだモデル（変更できない値の木）の手前の中間の形で行い、誤りのパスから同じ対応表で行・列を引く
- **正規表現の確かめ**（NFR3.6）: 長さ 1,000 文字を超えたら SEMANTIC。組み立て（`Pattern.compile`）は時間の上限つきの別のスレッド（アプリで1つの小さな実行器）で行い、100 ミリ秒で待つのをやめて SEMANTIC にする。組み立ては長さで上限があるため、待ちをやめた後の処理も短く終わる
- **識別**: SHA-256 を `MessageDigest` で、本文のバイト列から 16 進数の小文字 64 文字にする
- **適用中のモデルの保持**（BR5.3）: `AtomicReference` で1つを持ち、差し替えは参照の置き換え1回（読み手は常に完全なモデルか「無い」を見る）。モデルは変更できない値の木なので、取り出した後の差し替えの影響を受けない
- **JSON Schema の公開**: 正本は `backend/src/main/resources/dsl/dsl-schema-v1.json` に1つだけ置き、ビルドで WAR の静的なファイルの置き場（`WEB-INF/classes/static/dsl/`）へ複写する。既存のセキュリティの決まりで `/dsl/dsl-schema-v1.json` だけをログインなしで許す。画面の開発サーバーでは、プロキシでバックエンドから取る
- **依存の整理**: networknt の推移依存の `jackson-dataformat-yaml` と `snakeyaml-engine` は、使わないため依存から外す（外せなければ ArchUnit で `dsl` から使わないことを確かめる）
- **成果物**: U2 は library のため、security-design.md・logical-components.md・traceability.json を作る

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U2 の NFR 設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
