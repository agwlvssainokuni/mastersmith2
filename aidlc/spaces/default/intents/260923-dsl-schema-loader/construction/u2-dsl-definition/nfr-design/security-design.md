# Security Design — U2 DSL の定義（u2-dsl-definition）

U2 の非機能の要件（`construction/u2-dsl-definition/nfr-requirements/`）を満たす作りの方針を示す。実装はコード生成で行う。要点の確認は `nfr-design-questions.md`。作りは ADR-008 の試し（NFR 要件の tech-stack-decisions.md 3節）で確かめたものに合わせる。

## 1. 読み込みの流れ（NFR2.1〜NFR2.4、NFR3.1・NFR3.2・NFR3.5）

```text
read(bytes):
  if bytes.length > 10 MiB           -> SIZE_LIMIT (no parsing)
  root = snakeYaml(safeOptions).compose(utf8(bytes))   // node tree only, no object construction
      // depth 50, collection aliases 100, duplicate keys rejected, every tag rejected
  (json, positions) = convert(root, maxNodes = 1_000_000)  // expands aliases, counts nodes
  version check -> schema check (networknt) -> semantic check
  -> VALID(model, sha256(bytes)) or INVALID(errors with line/column)
```

| 段 | 作り | 当たったとき |
|---|---|---|
| 大きさ | 本文のバイト数で 10MB と比べ、読まない（承認済みの要件 NFR2・機能設計 BR1.1・契約 C4 の 5MB との差は U3 の NFR 要件の tech-stack-decisions.md 3節） | SIZE_LIMIT（行・列なし） |
| YAML の読み込み | SnakeYAML の `compose`（節の木だけを作り、Java の型を作らない）。`LoaderOptions`: 入れ子の深さ 50、コレクションを指す別名 100、重複キーを許さない、タグの検査で全拒否（`!!` の型の指定と独自のタグ）、文字数の上限は 10MB 相当 | DEPTH_LIMIT・ALIAS_LIMIT・DUPLICATE_KEY・FORBIDDEN_TAG・SYNTAX（部品の位置から行・列） |
| 変換 | 節の木を辿り、検証用の JSON の形（Jackson 3 の `JsonNode`）と位置の対応表を作る。別名は展開し、展開後の節の数が 1,000,000 を超えたら打ち切る | ALIAS_LIMIT |

- Jackson の YAML の読み込みは使わない（別名を文字列にし、重複キーを上書きするため）。networknt の推移依存の `jackson-dataformat-yaml` と `snakeyaml-engine` は依存から外し、外せなければ ArchUnit で `dsl` から使わないことを確かめる。
- 部品の例外（`YAMLException` など）は、種類ごとに U2 の誤りの種類へ写し、部品の文言は使わない（NFR5.1）。

## 2. 位置の対応表（ADR-008）

- キーは JSON Pointer の形のパス（例 `/tables/dept_mst/columns/code`）で、networknt の誤りのパスと同じ形。値は行・列（1 から）。
- 対応表のキーの位置を記録する（値ではなくキー）。必須の欠けは対象の対応表のキーの位置、知らない項目はその項目のキーの位置、型の違いはその値のキーの位置を引く。
- 別名で参照した値の中は、参照を書いたキーの位置を使う（BR4.2）。
- 対応表の大きさは節の数に比例し、展開後の節の上限（1,000,000）の内に収まる。

## 3. JSON Schema の検証（NFR3.3・NFR3.4）

- networknt 3.0.6 の `SchemaRegistry`（2020-12）。外部を取りに行かない設定（`fetchRemoteResources(false)`）を明示する。
- 同梱の JSON Schema は、起動時に1回だけクラスパスから読み、組み立てた `Schema` を使い回す（検証のたびに読まない）。
- 誤りは「キーワード・パス・埋める値」から文言の鍵にする。埋める値は、DSL の中の場所と、利用者が書いた値の先頭 100 文字まで。知らない項目の値は埋めない（NFR5.2）。
- 検証はサーバー側が正で、画面の検証の有無にかかわらず、API に届いた本文をすべて通す（NFR3.4、U4 の API から必ず呼ぶ）。

## 4. 意味の検証と正規表現（NFR3.6）

- 意味の検証（機能設計の BR3.x）は、変換した中間の形で行い、誤りのパスから同じ対応表で行・列を引く。
- 正規表現（`pattern`）は長さ 1,000 文字を超えたら SEMANTIC。組み立て（`Pattern.compile`）は、アプリで1つの小さな実行器（上限つきのスレッド）で行い、100 ミリ秒で待つのをやめて SEMANTIC にする。組み立ては長さで上限があるため、待ちをやめた後の処理も短く終わる。DSL の値に当てはめない。
- 実行器の待ちの時間はテストで差し替えられるようにし、打ち切りの道をテストで確かめる。
- 「待ちをやめた後の組み立ても短く終わる」前提は、Build and Test で、1,000 文字の重い正規表現（入れ子の繰り返しなど）の組み立て時間を測って確かめる。前提が外れたら、実行器のスレッドの数と待ち行列の上限を見直す。

## 5. 識別と適用中のモデル（BR5.1・BR5.3）

- 識別は本文のバイト列の SHA-256（`MessageDigest`）を 16 進数の小文字 64 文字にする。
- 適用中のモデルは `AtomicReference` で1つを持ち、差し替えは参照の置き換え1回。モデルは変更できない値の木で、取り出した後の差し替えの影響を受けない。

## 6. JSON Schema の公開（U2 の NFR 要件の Q5: B・F1: A）

- 正本は `backend/src/main/resources/dsl/dsl-schema-v1.json` の1つ。ビルドで WAR の静的なファイルの置き場（`WEB-INF/classes/static/dsl/`）へ複写する（既存の画面の `dist` の同梱と同じ仕組みに足す）。
- 既存のセキュリティの決まりで、`GET /dsl/dsl-schema-v1.json` だけをログインなしで許す。ほかの `/dsl/` の下は既存の扱いのまま。
- 画面の開発サーバーでは、プロキシでバックエンドから取る。
- 秘密は含まれない。応答には既存の `X-Content-Type-Options: nosniff` が付く。
