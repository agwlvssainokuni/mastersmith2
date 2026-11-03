# エンティティ — u1-time-zone-conversion

出典: 契約 C1（`inception/contract-design/contract-summary.md`）、部品 TimeZoneConversion（`inception/domain-design/components.md`）、質問と答え（`functional-design-questions.md` の Q1: A・Q2: A）。

U1 は状態を持たない純粋な関数の単位で、永続するデータは持たない。ここでいうエンティティは、関数の入力と結果の**値の型**（保存しない）。名前は論理の名前で、実装の型名は Code Generation で決める。

```yaml
entities:
  - name: TimeZoneName
    description: 表示のタイムゾーンを指す IANA の地域の名前。関数は必ず明示に受け取る（暗に既定を使わない）。
    attributes:
      - { name: value, type: string, required: true, constraints: "IANA の地域の名前（例 Asia/Tokyo・UTC）。固定のオフセット表記（+09:00・Z）・空の文字列は、解釈できない名前として扱う" }
    relationships: []

  - name: InstantText
    description: 時点を表す文字。サーバーが返す形（業務データの時点を表す型）と、画面が送る形が同じ。元の文字を変えずに保つ。
    attributes:
      - { name: raw, type: string, required: true, constraints: "ISO-8601 の日時 YYYY-MM-DDTHH:mm:ss[.fraction]（fraction は 1〜9 桁）に、Z・z・±hh:mm の時差が付く。読み方はこの形だけ" }
      - { name: fractionDigits, type: integer, required: false, constraints: "raw の端数の桁（0 なら端数なし）。raw から決まる" }
    relationships: []

  - name: FormattedInstant
    description: 表示のタイムゾーンの時刻と時差の文字。画面に出すためだけの値で、送り返さない。
    attributes:
      - { name: text, type: string, required: true, constraints: "YYYY-MM-DD HH:mm:ss[.fraction] ±HH:mm。端数は元の桁のまま（丸めない・落とさない）。時差の秒が 0 でないときだけ ±HH:mm:ss" }
    relationships:
      - { entity: InstantText, cardinality: "1:1 の導出（InstantText と TimeZoneName から決まる）", direction: derived }

  - name: LocalDateTimeText
    description: 利用者が入れる表示のタイムゾーンのローカル時刻の文字。
    attributes:
      - { name: text, type: string, required: true, constraints: "YYYY-MM-DD HH:mm:ss（区切りは半角の空白1つ）、端数 1〜6 桁は任意。T の区切り・秒の省略・7 桁以上は読めない値（Q1: A）。年は 0001〜9999" }
    relationships: []

  - name: OverlapChoice
    description: 夏時間で2回ある時刻のうち、どちらの時点かを指す選択。
    attributes:
      - { name: value, type: "enum", required: false, constraints: "earlier（先。夏時間の側）・later（後。標準時の側）。省略できる" }
    relationships: []

  - name: ParseResult
    description: ローカル時刻から時点への変換の結果。例外を投げず、種別で返す。
    attributes:
      - { name: kind, type: "enum", required: true, constraints: "ok・gap・overlap・invalid・invalid-time-zone のどれか" }
      - { name: instant, type: string, required: false, constraints: "kind が ok のとき。UTC の時差つきの文字 YYYY-MM-DDTHH:mm:ss[.fraction]+00:00（端数は入力の桁のまま）" }
      - { name: earlier, type: string, required: false, constraints: "kind が overlap のとき。先の時点（UTC の時差つきの文字）" }
      - { name: later, type: string, required: false, constraints: "kind が overlap のとき。後の時点（UTC の時差つきの文字）" }
      - { name: reason, type: "enum", required: false, constraints: "kind が invalid のとき。format（書式に合わない）・out-of-range（日付・時刻・年の範囲外）" }
    relationships:
      - { entity: LocalDateTimeText, cardinality: "1:1 の導出", direction: derived }

  - name: FormatResult
    description: 時点から表示の文字への変換の結果。例外を投げず、種別で返す。
    attributes:
      - { name: kind, type: "enum", required: true, constraints: "ok・unreadable・invalid-time-zone のどれか" }
      - { name: text, type: string, required: false, constraints: "kind が ok のとき。FormattedInstant の文字" }
      - { name: raw, type: string, required: false, constraints: "kind が unreadable のとき。サーバーが返した元の文字そのまま（Q2: A）" }
    relationships:
      - { entity: InstantText, cardinality: "1:1 の導出", direction: derived }
```

## 要約

- 永続するエンティティは無い。値の型は、**時点（InstantText）・表示の文字（FormattedInstant）・ローカル時刻（LocalDateTimeText）・名前（TimeZoneName）・夏時間の選択（OverlapChoice）・結果の2種（ParseResult・FormatResult）**。
- 元の時点の文字（InstantText.raw）は、変換せずに保つ値で、表示の文字（FormattedInstant）とは別（往復と、主キー・同時更新の判定・参照の値のため。契約 C1 の `preserveInstant`）。
