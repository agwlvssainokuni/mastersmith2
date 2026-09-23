# Functional Spec — U3 既定の DSL の生成（u3-default-dsl-generation）

U3 の振る舞いを示す。データの形は `entities.md`、決まりは `rules.md`、作る DSL の形は U2 の機能設計が正本。

## 1. 既定の DSL の生成（契約 C5）

1. U1 からスキーマの写しを読む。設定が無い・接続できないなら、その結果を返して終える（BR1.1）。
2. テーブル（ビューを含む）を、物理名を大文字・小文字を区別せずに比べた順に並べる（BR1.3・BR1.4）。
3. テーブルごとに:
   1. 表示名を決める（BR1.2）。ビューなら読み取り専用にし、キーを持たせない（BR1.5）。
   2. 主キーを写す（BR3.1）。外部キーを写し、1カラムの外部キーのカラムを覚えておく（BR3.2）。
   3. カラムごとに（定義の順）:
      - 表示名（BR1.2）、DB 上の型（BR2.1）を入れる。
      - 型の分類を決め（BR2.2）、フォーム部品（BR2.3・BR3.2）、検索（BR2.4）、一覧・詳細（BR2.5）の初期値を決める。
      - DB から導けるバリデーションを作る（BR4.1）。
      - 1カラムの外部キーなら、参照の選択肢を付ける（BR3.2）。
4. メニューを1階層で、テーブルごとに1項目作る（BR1.3）。
5. 固定の並びで YAML の本文に書き出す。先頭に生成を示すコメントを1行入れる（BR5.1）。接続先の値は入れない（BR5.3）。
6. U2 で検証し、識別を求める。通らなければ想定外の失敗、通れば「生成できた」と本文・識別を返す（BR5.2）。

## 2. 型の分類と初期値（`rules.md` の BR2.2〜BR2.5 から導いた表）

| 分類 | フォーム部品 | 検索 | 一覧 | 詳細 | 書式 |
|---|---|---|---|---|---|
| SHORT_TEXT | text | CONTAINS | 表示・並べ替え可 | 表示 | 無し |
| LONG_TEXT | textarea | CONTAINS | 表示しない・並べ替え不可 | 表示 | 無し |
| NUMBER | number | RANGE | 表示・並べ替え可 | 表示 | NUMBER_GROUPED |
| BOOLEAN | checkbox | CHOICE | 表示・並べ替え可 | 表示 | BOOLEAN_YES_NO |
| DATE | date | RANGE | 表示・並べ替え可 | 表示 | DATE |
| DATETIME | datetime | RANGE | 表示・並べ替え可 | 表示 | DATETIME |
| TIME | text | EQUALS | 表示・並べ替え可 | 表示 | TIME |
| UNSUPPORTED | text | 検索しない | 表示しない・並べ替え不可 | 表示しない | 無し |

主キーのカラムは検索を EQUALS、1カラムの外部キーのカラムは select（検索は CHOICE）にする。

## 3. 生成の例

写し: テーブル `dept_mst`（コメント「部署」）に、`dept_code VARCHAR(10) NOT NULL`（主キー、コメント「部署コード」）と `note TEXT NULL`。

```yaml
# generated from the target database schema by MasterSmith
version: 1
menus:
  - label: { ja: 部署, en: dept_mst }
    table: dept_mst
tables:
  dept_mst:
    label: { ja: 部署, en: dept_mst }
    view: false
    primaryKey: [dept_code]
    foreignKeys: []
    columns:
      dept_code:
        label: { ja: 部署コード, en: dept_code }
        dbType: { name: varchar, length: 10, precision: null, scale: null, nullable: false }
        formPart: text
        search: { enabled: true, operator: EQUALS, default: null, collapsed: false }
        list: { visible: true, order: 1, width: null, sortable: true, defaultSort: ASC, format: null }
        detail: { visible: true }
        validations: [ { type: required, origin: DB }, { type: maxLength, value: 10, origin: DB } ]
        options: null
      note:
        label: { ja: note, en: note }
        dbType: { name: text, length: null, precision: null, scale: null, nullable: true }
        formPart: textarea
        search: { enabled: true, operator: CONTAINS, default: null, collapsed: false }
        list: { visible: false, order: null, width: null, sortable: false, defaultSort: null, format: null }
        detail: { visible: true }
        validations: []
        options: null
```

（先頭のコメントの文言と、`null` の項目を書くか省くかは、U2 の JSON Schema に合わせてコード生成で決める。）

## 4. 失敗の場合とふるまい

| 場合 | ふるまい |
|---|---|
| 対象DB の設定が無い | TARGET_UNCONFIGURED |
| 対象DB に接続できない・応答しない | TARGET_UNAVAILABLE（理由つき） |
| テーブルが0件 | メニュー・テーブルが空の DSL を生成（U2 の検証を通ること） |
| 分からない型 | UNSUPPORTED として扱い、生成は続ける |
| 生成した DSL が U2 の検証を通らない | 作りの誤りとして想定外の失敗（テストで防ぐ） |

## 5. 決まりの要約

| 場面 | 決まり |
|---|---|
| 読めないとき | BR1.1 |
| 表示名・メニュー・並び・ビュー | BR1.2〜BR1.5 |
| 型と初期値 | BR2.1〜BR2.5 |
| キー | BR3.1・BR3.2 |
| バリデーション | BR4.1 |
| 書き出しと検証 | BR5.1〜BR5.3 |
