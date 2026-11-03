# エンティティ — u3-target-db-time

出典: 契約 C4（`inception/contract-design/contract-summary.md`）、部品 TargetDbSession・BusinessDataValueCodec・DslDefaultGenerator（`inception/domain-design/components.md`）、質問と答え（`functional-design-questions.md` の Q1: B・Q2: B・Q3: B・Q4: B）。

U3 は、対象DB の日時の値の読み書きと既定の DSL の生成の直しの単位で、**内部DB に新しく保存するデータは持たない**（NFR4.1 のとおり、内部DB の変更は U2 の列の追加だけ）。ここでいうエンティティは、読み書きの途中で受け渡す**値の型**（保存しない）と、既存の DSL の項目の意味。接続のタイムゾーンの設定値はフレームワークの接続の設定で、エンティティにせず決まり（BR3.1）として書く。名前は論理の名前で、実装の型名は Code Generation で決める。

```yaml
entities:
  - name: TimeColumnKind
    description: 対象DB のカラムの日時・時刻の型を、受け渡しの型として分けた分類。
    attributes:
      - { name: kind, type: "enum", required: true, constraints: "time_point（時点を表す型。PostgreSQL の timestamptz・timestamp(n) with time zone、MySQL・MariaDB の timestamp）・local_datetime（時差を持たない日時。MySQL・MariaDB の datetime、PostgreSQL の timestamp）・date・local_time（時差を持たない時刻。time）・time_with_zone（PostgreSQL の timetz）" }
      - { name: digitsApplies, type: boolean, required: true, constraints: "time_point・local_datetime・local_time・time_with_zone のとき true（秒の端数の桁を持つ型）。date は false" }
    relationships: []

  - name: FractionDigits
    description: 日時・時刻の値の秒の端数の桁（0〜6）。応答の値は、この桁に固定して出す。
    attributes:
      - { name: digits, type: integer, required: true, constraints: "0〜6。読むときの結果の列情報（DB の定義の桁）から決める（Q1: B）。桁が得られないときだけ BR3.4" }
    relationships:
      - { entity: TimeColumnKind, cardinality: "1:1（digitsApplies が true の型だけ）", direction: derived }

  - name: TimeCell
    description: 日時・時刻のセルを読んだ結果。例外にせず、種別で返す。
    attributes:
      - { name: kind, type: "enum", required: true, constraints: "text（型として読めた）・raw（型として読めず、DB が送った文字をそのまま返す。Q4: B）・null（値が無い）" }
      - { name: text, type: string, required: false, constraints: "kind が text のとき。time_point は UTC の `YYYY-MM-DDTHH:mm:ss[.fraction]+00:00`（桁は FractionDigits に固定）、local_datetime は `YYYY-MM-DDTHH:mm:ss[.fraction]`、local_time は `HH:mm:ss[.fraction]`、time_with_zone は DB の時差のまま `HH:mm:ss[.fraction]±hh:mm`、date は `YYYY-MM-DD`" }
      - { name: rawText, type: string, required: false, constraints: "kind が raw のとき。DB が送った文字そのまま（例 0000-00-00 00:00:00・838:59:59・infinity）。応答の値の項目に入れ、項目は増やさない。ログ・エラー応答・トレースの属性には出さない" }
    relationships:
      - { entity: TimeColumnKind, cardinality: "1:1 の導出", direction: derived }

  - name: TimeWriteText
    description: 作成・更新・検索で受ける日時・時刻の要求の文字。型として読んで保存・比較に使う。
    attributes:
      - { name: text, type: string, required: true, constraints: "time_point は時差つき（Z・z・±hh:mm）、時差が無ければ UTC とみなす（C4）。time_with_zone は時差つき（時差を落とさない）、時差が無ければ UTC。local_datetime・local_time・date は ISO の形だけ（時差を持たない）。日時として読めない文字は拒否" }
      - { name: fractionLength, type: integer, required: false, constraints: "text の端数の桁（端数なしは 0）" }
    relationships:
      - { entity: TimeColumnKind, cardinality: "1:1 の導出", direction: derived }

  - name: DslTimePrecision
    description: 既定の DSL の日時・時刻のカラムの `dbType.precision`（秒の端数の桁）。既存の DSL の項目で、新しい項目は足さない。
    attributes:
      - { name: precision, type: integer, required: false, constraints: "0〜6。無いとき（null）は、DB の製品の既定（PostgreSQL 6・MySQL/MariaDB 0）の意味のまま。生成は、DB の定義の桁が製品の既定と違うときだけ書く（Q2: B）" }
      - { name: explicit, type: boolean, required: true, constraints: "precision が書いてある、または型の名前に (n) がある。書き込みの端数の桁の確かめ（BR3.9）は explicit が true のときだけ行う" }
    relationships:
      - { entity: TimeColumnKind, cardinality: "1:1（digitsApplies が true の型だけ）", direction: derived }
```

## 要約

- 永続するエンティティは無い。値の型は、**日時の分類（TimeColumnKind）・端数の桁（FractionDigits）・読んだセル（TimeCell）・要求の文字（TimeWriteText）・DSL の桁（DslTimePrecision）**。
- 読んだセルは `text` か `raw` か `null` の3種。`raw` は応答の値の項目に入る文字で、画面（U1 の `unreadable`）が元の文字として出す。
- 応答の端数の桁（FractionDigits）は DB の定義が正で、DSL の桁（DslTimePrecision）は生成・書き込みの桁の確かめ・画面の案内に使う。2つは食い違ってもよく、応答は DSL に頼らない。
