# Security Design — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 の非機能の要件（`construction/u3-default-dsl-generation/nfr-requirements/`）を満たす作りの方針を示す。実装はコード生成で行う。要点の確認は `nfr-design-questions.md`。

## 1. 流れ

```text
generate():
  schema = targetSchemaReader.readSchema(GENERATE)      // U1: UNCONFIGURED / UNAVAILABLE -> return as result
  tree   = build(schema)                                 // rules BR1.x-BR4.x, LinkedHashMap in fixed order
  bytes  = HEADER_COMMENT + yaml(fixedDumperOptions).dump(tree)  // UTF-8, LF
  if bytes.length > 10 MiB -> unexpected failure (no partial DSL)
  result = dslReader.read(bytes)                         // U2 validation (BR5.2)
  if INVALID -> unexpected failure, WARN log without body / connection info
  -> GENERATED(bytes, hash)
```

どこかで失敗したら、部分的な DSL を返さない（NFR2.5）。10MB の確かめは U2 の検証（BR5.2、大きさの正の確かめ）でも行うが、U3 は書き出した直後に先に確かめ、U2 に大きな本文を渡す前に打ち切る（二重に確かめるのは早めに止めるため。正は U2）。

## 2. 書き出し（NFR4.7・BR5.1）

| 項目 | 作り |
|---|---|
| 部品 | SnakeYAML 2.6 の `Yaml.dump`（U2 と同じ部品。新しい依存は足さない） |
| 形 | `DumperOptions` を固定: ブロックの形、字下げ 2、1行の幅は無制限（折り返さない）、改行は LF、別名（アンカー）を作らない、Unicode の文字をそのまま書く |
| 並び | 組み立ては `LinkedHashMap`・`List` で行い、項目の並びを書式の例の順に固定する。テーブルは物理名の順（BR1.3・BR1.4） |
| 変わる値 | 日時・乱数・実行の環境の値を入れない。先頭に固定の文字列のコメントを1行だけ付ける |
| 引用 | 名前・コメントは文字列として部品に渡し、引用が要るかは部品が決める。文字列の連結で YAML を組み立てない |

同じ写しからは同じバイト列になる（性質ベースのテストで、同じ写しを2回生成して比べる）。任意の文字列（`:`・`#`・`-`・改行・引用符・`&`・`*`・`!` を含む）のコメントと名前で生成した DSL が、U2 の検証を通り、読み直した値が元と同じになることも性質ベースのテストで確かめる（jqwik）。

## 3. コメントの扱い（NFR4.8）

- 改行・タブを除く C0（U+0000〜U+001F）・C1（U+0080〜U+009F）の制御文字と U+007F を取り除く。長さは切り詰めない。
- 空の文字列・空白だけになったら、コメントが無いものとして扱う（機能設計の BR2.5 と同じ扱い）。
- 承認済みの機能設計の BR1.2 に制御文字の扱いは無い。書き換えずに差をこの文書に記録し、コード生成で NFR4.8 に従う（NFR 要件の承認で決めた扱い）。

## 4. 接続情報を出さない（NFR4.9）

- U3 は U1 の写し（TargetSchema）だけを受け取り、接続の設定を知らない。DSL の書式に接続先の項目は無い。
- 失敗のログは、失敗の段と誤りの種類だけを出し、DSL の本文・写しの値・接続先を出さない。

## 5. 時間（NFR1.6・NFR1.7）

- 組み立てと書き出しを合わせて 5 秒以内（100 × 100、約 5.3MB）。本文は1回の書き出しでバイト列にし、途中で文字列を何度も作り直さない。
- 内訳（U1 の読み取り・組み立て・書き出し・U2 の検証）の時間を DEBUG のログに出し、Build and Test で内訳ごとに測れるようにする。全体の時間と結果は U4 の指標（`operation=generate`）で見る。

## 6. 承認済みの文書との差

| 決定 | 文書 | 承認済みの記述 | 実装での扱い |
|---|---|---|---|
| コメントの制御文字を取り除く（NFR4.8） | `construction/u3-default-dsl-generation/functional-design/rules.md` の BR1.2 | 制御文字の扱いは無い | 3節のとおり取り除く |
| 読み取りに目的を渡す（U1 の NFR 設計） | `inception/contract-design/contract-summary.md` の C1 | `readSchema()` に引数なし | `readSchema(GENERATE)` を呼ぶ |
