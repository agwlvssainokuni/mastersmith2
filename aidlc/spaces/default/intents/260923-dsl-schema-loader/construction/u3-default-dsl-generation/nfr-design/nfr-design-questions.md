# NFR Design — U3 既定の DSL の生成（u3-default-dsl-generation）— 設計の要点の確認

U3 の非機能の要件（`construction/u3-default-dsl-generation/nfr-requirements/`）は、部品（SnakeYAML の書き出し）と数値まで決まっている。新しく依頼者に尋ねる論点は無いため、作りの方針を案として確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。

## 設計の要点（案）

- **流れ**: U1 の `readSchema(GENERATE)` で写しを受け取る → 機能設計の決まり（BR1.x〜BR4.x）で DSL の値の木を組み立てる → SnakeYAML で YAML の本文に書き出す → 本文の大きさ（10MB）を確かめる → U2 の `read` で検証する → 結果の型で返す。どこかで失敗したら、部分的な DSL を返さない
- **書き出しの形**（BR5.1、NFR4.7）: SnakeYAML の `DumperOptions` を固定する（ブロックの形、字下げ 2、1行の幅は無制限、改行は LF、別名を作らない、文字は UTF-8 のまま）。組み立ては順序を持つ対応表（`LinkedHashMap`）で行い、項目の並びを書式の例の順に固定する。日時など実行のたびに変わる値を入れない。先頭に生成したことを示すコメントを1行だけ入れる（本文の組み立ての外で、固定の文字列を前に付ける）
- **引用**: 名前・コメントは SnakeYAML に文字列として渡し、引用が要るかは部品に任せる（`:`・`#`・改行・`&`・`*`・`!` などを含んでも構造が変わらない）。文字列の連結で YAML を組み立てない
- **コメントの扱い**（NFR4.8）: 改行・タブを除く制御文字（C0・C1）を取り除く。長さは切り詰めない。承認済みの機能設計の BR1.2 にこの決まりは無いため、書き換えずに差を記録し、コード生成で NFR4.8 に従う（NFR 要件の承認で決めた扱い）
- **大きさ**（NFR2.5）: 書き出した本文のバイト数が 10MB を超えたら、想定外の失敗（生成の失敗）にする
- **時間**（NFR1.6・NFR1.7）: 組み立てと書き出しを合わせて 5 秒以内（100 × 100）。本文は1回の書き出しでバイト列にし、途中で文字列を何度も作り直さない。内訳ごとの時間を測れるよう、U4 の指標（`operation=generate`）とは別に、組み立て・書き出し・検証の時間をデバッグのログに出す
- **検証**（BR5.2）: 生成した本文は必ず U2 の `read` を通す。通らなければ U3 の作りの誤りとして想定外の失敗にし、誤りの内容をログ（WARN、本文と接続先を含めない）に出す
- **成果物**: U3 は library のため、security-design.md・logical-components.md・traceability.json を作る

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U3 の NFR 設計の文書を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
