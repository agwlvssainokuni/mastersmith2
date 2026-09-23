# Functional Design — U2 DSL の定義（u2-dsl-definition）— 設計の要点の確認

U2 は DSL の書式そのもの（ロードマップの Intent A）を決める単位で、後続の Intent I・J・K の土台になる。論点の多くは要件（FR1・FR4）・Domain Design（ADR-003・ADR-005・ADR-007・ADR-008）・Contract Design（C4・C8）で決まっているため、質問は作らず、DSL の形と検証の決まりを案として示して確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。上限の数値（入れ子の深さ・別名の数）とハッシュのアルゴリズムは NFR の段で決める。

## DSL の形（案）

YAML の最上位の形は次のとおり。テーブル・カラムは物理名をキーにした対応表で書く（同じテーブル・カラムの二重の定義は、重複キーの誤りとして止まる）。

```yaml
version: 1                      # 書式の版（必須）
menus:                          # メニュー（N 階層）
  - label: { ja: 基本マスタ, en: Basic masters }
    icon: folder                # 任意
    items:                      # 子のメニュー（任意、何階層でも）
      - label: { ja: 部署, en: Departments }
        table: dept_mst         # テーブルに紐付く項目
tables:
  dept_mst:                     # 物理名
    label: { ja: 部署, en: dept_mst }
    view: false                 # ビューなら true（読み取り専用）
    primaryKey: [dept_code]
    foreignKeys: []
    columns:
      dept_code:
        label: { ja: 部署コード, en: dept_code }
        dbType: { name: VARCHAR, length: 10, precision: null, scale: null, nullable: false }
        formPart: text          # text・textarea・checkbox・switch・radio・select・date・datetime・number・password・lookup
        search: { enabled: true, operator: EQUALS, default: null, collapsed: false }
        list: { visible: true, order: 1, width: null, sortable: true, defaultSort: null, format: null }
        detail: { visible: true }
        validations:
          - { type: required, origin: DB }
          - { type: maxLength, value: 10, origin: DB }
        options: null           # 選択肢の出どころ（select・radio・lookup で使う）
```

- **表示名**: `ja`・`en` の2つとも必須（空の文字列は許すが、プレビューで「未設定」と示す）。
- **検索の演算子**: `EQUALS`（完全一致）・`CONTAINS`（部分一致）・`RANGE`（範囲）・`CHOICE`（選択肢）・`IN`。
- **表示の書式**（`list.format`）: 日付の形・数値の桁区切り・真偽値のラベルを、決まった種類から選ぶ（種類の一覧は U2 の機能設計で確定する）。
- **バリデーション**: `required`・`minLength`・`maxLength`・`min`・`max`・`pattern`・`unique`。`origin` は `DB`（DB から導いた）か `MANUAL`（人が足した）。任意で、誤りの文言（ja・en）を持てる。
- **選択肢の出どころ**（`options`）: `source` が `FIXED`（`items` に値と表示名 ja・en を並べる）・`REFERENCE`（参照先のテーブル・値のカラム・表示のカラム）・`LOOKUP`（参照ピッカー。参照先のテーブル・値のカラムに加え、専用の検索条件と一覧表示の項目を持つ）。
- **接続先**: DSL の書式に接続先の項目は無い。知らない項目は構文の誤りにする（JSON Schema で追加の項目を許さない）。
- **権限**: DSL に含めない（後続の Intent F）。

## 検証の決まり（案）

- **順序**: 読み込みの上限（大きさ・入れ子の深さ・別名の数・タグ・重複キー）→ 書式の版 → 構文（JSON Schema）→ 意味。前の段に誤りがあれば、後の段は行わない（誤りの一覧は、止まった段の誤りだけになる）。構文の誤りは、見つかったものをすべて返す。
- **意味の検証**: メニューが DSL に無いテーブルを指していない／`primaryKey`・`foreignKeys`・`list.order`・参照（`REFERENCE`・`LOOKUP`）の先のテーブル・カラムが DSL にある／`list.order` がテーブルの中で重ならない／`select`・`radio` は `FIXED` か `REFERENCE`、`lookup` は `LOOKUP` の選択肢を持つ／`min` ≦ `max`・`minLength` ≦ `maxLength`・`pattern` が正規表現として正しい。
- **誤りの行・列**: YAML を読むときの位置の対応表から引く。別名（アンカー）を参照した値の誤りは、参照した場所（`*` を書いた場所）の行・列を示す。位置が得られない誤り（大きさの上限など）は行・列なし。
- **文言**: 誤りは文言の鍵と埋める値で持ち、表示言語（ja・en）の文言にするのは応答を作るとき。部品の例外のメッセージは入れない。
- **識別**: DSL の識別（ハッシュ値）は、YAML の本文のバイト列から求める（ADR-003）。
- **モデル**: 検証を通った DSL を、変更できない値の木（メニュー・テーブル・カラム）に変える。適用中のモデルの保持と提供口（C8）は、一度に切り替わる差し替えにする。

## Consolidated Summary Confirmation

- 上の「DSL の形（案）」と「検証の決まり（案）」のとおりに、U2 の機能設計の文書（entities.md・rules.md・functional-spec.md・traceability.json）を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
