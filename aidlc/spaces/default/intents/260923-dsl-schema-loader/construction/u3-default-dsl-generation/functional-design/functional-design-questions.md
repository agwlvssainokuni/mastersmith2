# Functional Design — U3 既定の DSL の生成（u3-default-dsl-generation）— 設計の要点の確認

U3 は、U1 が読んだスキーマの写しから、U2 の書式の DSL（YAML の本文）を作る。表示名・メニュー・ビュー・DB 上の型の扱いは要件（FR3.3〜FR3.5）・ADR-005 で決まっている。新しく決めるのは、型から部品・バリデーション・検索・一覧への対応の規則と、並べ方の規則である。これを案として示して確かめる（`aidlc/spaces/default/memory/project.md` の Way of Working）。

## 設計の要点（案）

### 表示名・メニュー・ビュー（要件のとおり）

- 表示名は ja・en の両方に物理名。テーブル・カラムにコメントがあれば、ja はコメント。
- メニューは1階層で、テーブル（ビューを含む）ごとに1項目。項目の表示名はテーブルの表示名と同じ。
- **並べ方**: 物理名を、大文字・小文字を区別せずに Unicode の符号の順で並べる。区別しないと同じになる名前どうしは、元の名前の符号の順。
- ビューは `view: true`、主キーと外部キーは空。

### 型の分類（3種類の DB の型の名前を、次の分類に対応させる）

| 分類 | 主な型の名前（例） | フォーム部品 | 検索の演算子 | 一覧 |
|---|---|---|---|---|
| 短い文字列 | varchar・char・character varying・nvarchar（長さ 255 以下） | text | CONTAINS（部分一致） | 表示・並べ替え可 |
| 長い文字列 | 長さ 256 以上の文字列、text・mediumtext・longtext・clob | textarea | CONTAINS | 表示しない・並べ替え不可 |
| 整数・小数 | int・bigint・smallint・numeric・decimal・real・double・float | number | RANGE（範囲） | 表示・並べ替え可、書式は NUMBER_GROUPED |
| 真偽値 | boolean・bool・MySQL／MariaDB の tinyint(1)・bit(1) | checkbox | CHOICE（選択肢） | 表示・並べ替え可、書式は BOOLEAN_YES_NO |
| 日付 | date | date | RANGE | 表示・並べ替え可、書式は DATE |
| 日時 | timestamp・datetime（時差つきを含む） | datetime | RANGE | 表示・並べ替え可、書式は DATETIME |
| 時刻 | time | text | EQUALS | 表示・並べ替え可、書式は TIME |
| そのほか（対応外） | 2進（bytea・blob）・json・配列・位置の型 など | text | 検索しない | 表示しない・並べ替え不可、詳細にも出さない |

- 型の名前が分からないものは「そのほか」とする。
- DSL の `dbType` には、DB が返した型の名前・長さ・精度・桁・NULL を許すか を、そのまま入れる（分類は入れない）。

### 主キー・外部キー

- 主キーのカラムは、検索の演算子を EQUALS（完全一致）にする。一覧の既定の並べ替えは、主キーの最初のカラムの昇順。
- 1つのカラムだけの外部キーで、参照先が同じスキーマのテーブルなら、そのカラムの部品を select にし、選択肢の出どころを `REFERENCE`（参照先のテーブル・値のカラム＝参照先のカラム・表示のカラム＝参照先のカラム）にする。表示のカラムは、人が後で表示名のカラムに直す前提。
- 複数のカラムの外部キーは、部品を変えない（型の分類のまま）。

### バリデーション（DB から導いたもの、`origin: DB`）

- NOT NULL で既定値が無いカラムは `required`。
- 長さのある文字列は `maxLength`（長さ）。
- 数値の精度・桁からの範囲、一意の制約は作らない（U1 は一意の制約を読まない）。

### 検索・一覧・詳細の初期値

- 検索: 「そのほか」以外は検索できる（`enabled: true`）。初期値は無し、畳まない。
- 一覧: 表示するカラムに、定義の順で 1 から並び順を付ける。列幅は無し。
- 詳細: 「そのほか」以外は表示する。

### 生成した YAML の書き方

- 項目の並びは、U2 の書式の例（`version` → `menus` → `tables`、テーブルの中は `label` → `view` → `primaryKey` → `foreignKeys` → `columns`）のとおりに固定し、同じスキーマからは同じ本文（同じ識別）になるようにする。
- 先頭に、生成したことを示すコメント（日時は入れない。同じ本文にするため）を1行入れる。
- 生成した DSL は U2 で検証し、通らなければ作りの誤り（想定外の失敗）とする。
- 接続先の値は入れない。

## Consolidated Summary Confirmation

- 上の「設計の要点（案）」のとおりに、U3 の機能設計の文書（entities.md・rules.md・functional-spec.md・traceability.json）を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
