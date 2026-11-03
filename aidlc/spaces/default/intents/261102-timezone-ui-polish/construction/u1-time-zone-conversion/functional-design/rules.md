# 業務の決まり — u1-time-zone-conversion

出典: 要件（`inception/requirements-analysis/requirements.md` の FR1.5・FR2.1〜FR2.5・FR3.1・FR3.2・FR3.4・FR3.6・FR4.2・NFR5.1）、契約 C1・C4、設計の決定 ADR-002・ADR-003（`inception/domain-design/decisions.md`）、質問と答え（`functional-design-questions.md` の Q1: A・Q2: A）。

```yaml
rules:
  - id: BR1.1
    statement: 時点は、表示のタイムゾーンの時刻と時差の文字 `YYYY-MM-DD HH:mm:ss[.fraction] ±HH:mm` に直す。秒の端数は元の文字の桁のまま出し、丸めず・落とさない。
    category: calculation
    applies_to: formatInstant
    trigger: 時点を表示するとき
    logic: IF 入力の時点の文字が読め、タイムゾーンが解釈できる THEN 時点を表示のタイムゾーンの壁時計と時差に直し、端数は元の桁で並べる。fractionDigits が渡されたら、元の桁がそれより短いときだけ 0 を足して桁をそろえ、長いときは元の桁を保つ。同じ時点と同じタイムゾーンは、いつ・どの環境で呼んでも同じ文字になる。
    violation_behaviour: 読めない文字は BR1.5、解釈できないタイムゾーンは BR1.12
    source: FR2.1・FR2.2・FR2.5

  - id: BR1.2
    statement: 時差は、時間と分の形 `±HH:mm` で出す。分の端数を持つ時差（`+05:45`・`+09:30`・`-03:30`）もそのまま出す。時差の秒が 0 でない（歴史的な時差）ときだけ `±HH:mm:ss` で出す。
    category: calculation
    applies_to: formatInstant
    trigger: 時差を添えるとき
    logic: IF 表示のタイムゾーンのその時点の時差の秒が 0 THEN `±HH:mm`。ELSE `±HH:mm:ss`。UTC は `+00:00`。
    violation_behaviour: なし（純粋な関数）
    source: FR2.2・NFR5.1

  - id: BR1.3
    statement: 表示の日付は、表示のタイムゾーンで決める。UTC の日付と異なってよい（日付の変わり目）。
    category: calculation
    applies_to: formatInstant
    trigger: 時点が UTC と表示のタイムゾーンで別の日付になるとき
    logic: IF 時点が `2026-11-01T15:00:00+00:00` で表示のタイムゾーンが `Asia/Tokyo` THEN 表示は `2026-11-02 00:00:00 +09:00`。月末・年末・うるう日（2月29日）でも、同じく表示のタイムゾーンの暦で決める。
    violation_behaviour: なし
    source: FR2.1・NFR5.1

  - id: BR1.4
    statement: 夏時間のある地域では、時差は時点ごとに決める。切り替わりの前後で `-04:00` と `-05:00`（など）が切り替わる。
    category: calculation
    applies_to: formatInstant
    trigger: 夏時間の切り替わりの前後の時点を表示するとき
    logic: IF 表示のタイムゾーンが夏時間を持つ THEN 時点ごとにその時点の時差を使う（タイムゾーンの固定の時差を使わない）。
    violation_behaviour: なし
    source: FR2.2・NFR5.1

  - id: BR1.5
    statement: 時点の文字を日時として読めないときは、元の文字をそのまま返す（kind が unreadable、raw に元の文字）。呼ぶ側は元の文字をそのまま出す。
    category: validation
    applies_to: formatInstant
    trigger: 入力の文字が `YYYY-MM-DDTHH:mm:ss[.fraction](Z|z|±hh:mm)` の形でない、または日付・時刻が範囲外
    logic: IF 読めない THEN 例外を投げず、`{kind: "unreadable", raw: 元の文字}` を返す。変換も丸めもしない。
    violation_behaviour: なし（読めないことが結果）
    source: FR2.1（読めない値の見せ方。ストーリー AC2.1.6 のうち画面の側）・質問 Q2: A

  - id: BR1.6
    statement: ローカル時刻の入力は、`YYYY-MM-DD HH:mm:ss`（区切りは半角の空白1つ）の1つの形だけを許す。端数 1〜6 桁は任意。それ以外（T の区切り・秒の省略・端数 7 桁以上・全角の数字・前後の空白）は読めない値（invalid）にする。
    category: validation
    applies_to: parseLocalDateTime
    trigger: ローカル時刻の文字を時点に直すとき
    logic: IF 文字が正規の形に一致し、日付が暦に存在し（月ごとの日数・うるう日）、時が 00〜23、分と秒が 00〜59（うるう秒の 60 は許さない）、年が 0001〜9999 THEN 次の決まりへ進む。ELSE `{kind: "invalid", reason: "format" | "out-of-range"}`。書式に合わないときは format、範囲外のときは out-of-range。
    violation_behaviour: invalid を返す（画面は欄の下に誤りを出す。サーバーも読めない値は 400。契約 C4）
    source: FR3.1・FR3.2・FR3.6・質問 Q1: A

  - id: BR1.7
    statement: ローカル時刻が表示のタイムゾーンで存在しない（夏時間の切り替えで飛ばされる）ときは、gap を返す。黙ってずらさない。
    category: validation
    applies_to: parseLocalDateTime
    trigger: 夏時間の切り替えで進む区間の時刻を入れたとき
    logic: IF そのローカル時刻を壁時計に持つ時点が、表示のタイムゾーンに存在しない THEN `{kind: "gap"}`。例: `America/New_York` の `2026-03-08 02:30:00`。
    violation_behaviour: gap を返す。画面は欄の下に「この時刻はありません」を出し、保存させない。サーバーはこの拒否をしない（時点は必ず存在するため。ADR-002）
    source: FR3.4・前提 A2

  - id: BR1.8
    statement: ローカル時刻が夏時間の切り替えで2回ある（重なる）ときは、先（夏時間側）と後（標準時側）の2つの時点を返し、overlapChoice を渡されたときだけ、選んだ側の時点で ok にする。
    category: validation
    applies_to: parseLocalDateTime
    trigger: 夏時間の切り戻しで重なる区間の時刻を入れたとき
    logic: IF 重なる THEN overlapChoice が無いなら `{kind: "overlap", earlier, later}`、`earlier` なら先の時点で ok、`later` なら後の時点で ok。重ならない時刻に overlapChoice を渡しても、無視して ok にする。
    violation_behaviour: overlap を返す（画面は先・後の2択を出し、選ぶまで保存させない）
    source: FR3.4・R-03（ストーリー AC2.3.4）

  - id: BR1.9
    statement: ローカル時刻が決まる（ok）ときの時点は、UTC の時差つきの文字 `YYYY-MM-DDTHH:mm:ss[.fraction]+00:00` で返す。端数は入力の桁のまま。
    category: calculation
    applies_to: parseLocalDateTime
    trigger: ローカル時刻から時点が決まったとき
    logic: IF ok THEN 時点を UTC に直し、`+00:00` の形（サーバーの応答と同じ形）で返す。入力の端数の桁を変えない。サーバーへ送る値は、この時点の文字だけ（ADR-002）。
    violation_behaviour: なし
    source: FR3.1・FR3.2・ADR-002

  - id: BR1.10
    statement: タイムゾーンの名前は、今のブラウザの Intl が IANA の名前として知っているときだけ「解釈できる」とする。固定のオフセット表記・空の文字列・知らない名前は解釈できない。
    category: validation
    applies_to: canInterpretTimeZone
    trigger: 保存済みの名前を画面が使う前
    logic: IF `Intl.DateTimeFormat` が、その名前を timeZone として受け付ける（例外を出さない）かつ、固定のオフセット表記でない THEN 真。ELSE 偽。
    violation_behaviour: 偽を返す（呼ぶ側は保存の値を変えず、ブラウザのタイムゾーンで見せ、解釈できないことを知らせる。DisplayTimeZoneContext）
    source: FR1.5・R-02（ストーリー AC1.2.4）

  - id: BR1.11
    statement: 時点の元の文字は、変換せずにそのまま保つ。表示用の文字から時点を作り直して送り返さない。
    category: constraint
    applies_to: preserveInstant
    trigger: 主キー・同時更新の判定の値・参照の値・変えていない欄を送り返すとき
    logic: preserveInstant(raw) は raw をそのまま返す（正規化・丸め・タイムゾーンの変換をしない）。画面は、読んだ値を、表示用の文字とは別にこの元の文字として持ち、送り返す。変えていない欄は送らない。
    violation_behaviour: なし（恒等）
    source: FR3.3・R-01・ストーリー AC2.5.3・AC2.5.1

  - id: BR1.12
    statement: すべての関数は、タイムゾーンを明示に受け取る。ブラウザの既定のタイムゾーン・既定のロケールに暗に頼らない。解釈できないタイムゾーンを渡されたときは、既定へ黙って代えず、invalid-time-zone を返す。
    category: constraint
    applies_to: formatInstant・parseLocalDateTime
    trigger: 関数を呼ぶとき
    logic: IF timeZone を解釈できない THEN `{kind: "invalid-time-zone"}`（例外を投げない）。数字は半角（ラテン数字）で整形し、ロケールの数字・暦に影響されない。
    violation_behaviour: invalid-time-zone を返す（呼ぶ側の誤り）
    source: FR4.2・NFR1・`team.md`（変換を1か所に閉じ、既定のタイムゾーンを暗に使わない）

  - id: BR1.13
    statement: 変換は往復で時点を失わない。時点を表示の文字にしても、表示の文字（時差つき）を再び時点に直すと、同じ時点に戻る。
    category: constraint
    applies_to: formatInstant・parseLocalDateTime
    trigger: 性質ベースのテスト
    logic: FOR ALL 時点 t・タイムゾーン z: parse(format(t, z) のローカル部分, z, overlapChoice = format の時差が先か後か) が ok で、時点が t に等しい（重なる時刻では、時差で先・後を決めて戻す）。時差を持たない型は変換の対象外のため、タイムゾーンを変えても値が変わらない（呼ぶ側が通さない）。
    violation_behaviour: テストが落ちる
    source: FR3.3・NFR5.1・ストーリー AC2.5.5

  - id: BR1.14
    statement: 変換の部品は純粋である。状態を持たず、現在時刻・乱数・グローバルな状態に頼らない。同じ入力は同じ結果を返す。
    category: constraint
    applies_to: 全関数
    trigger: 実装・テスト
    logic: 関数は引数だけから結果を決める。例外を投げない。
    violation_behaviour: なし
    source: FR4.2・NFR5.2

  - id: BR1.15
    statement: 範囲の検索の始まりと終わりの前後は、時点の大小で比べる（始まりが終わりより後なら誤り）。比べは、表示の文字ではなく時点（UTC の時差つきの文字）で行う。
    category: validation
    applies_to: compareInstants
    trigger: 時点を表す型の範囲の検索で、始まりと終わりを入れたとき
    logic: compareInstants(a, b) は、2つの時点の文字を時点として比べ、a が前なら負、同じなら 0、後なら正を返す。読めない文字を渡されたら、読めないことを返す（呼ぶ側が入力の誤りにする）。端は含む（始まり＝終わりは誤りにしない）。
    violation_behaviour: 呼ぶ側が「始まりが終わりより後」の誤りを出す
    source: FR3.2・ストーリー AC2.4.2（Q7: A 両端を含む）
```

## 決まりの一覧（導出。`rules.md` の YAML が正）

| ID | 種類 | 適用先 | 要点 |
|---|---|---|---|
| BR1.1 | calculation | formatInstant | 時点→表示の文字（端数は桁のまま） |
| BR1.2 | calculation | formatInstant | 時差の形（分の端数・歴史的な秒） |
| BR1.3 | calculation | formatInstant | 日付は表示のタイムゾーンで決める |
| BR1.4 | calculation | formatInstant | 夏時間は時点ごとの時差 |
| BR1.5 | validation | formatInstant | 読めない時点は元の文字（Q2: A） |
| BR1.6 | validation | parseLocalDateTime | 入力は厳密に1つの形（Q1: A） |
| BR1.7 | validation | parseLocalDateTime | 存在しない時刻は gap |
| BR1.8 | validation | parseLocalDateTime | 重なる時刻は overlap（先・後） |
| BR1.9 | calculation | parseLocalDateTime | 決まる時点は UTC の時差つき |
| BR1.10 | validation | canInterpretTimeZone | ブラウザが知る IANA の名前だけ |
| BR1.11 | constraint | preserveInstant | 元の時点は変換せず保つ |
| BR1.12 | constraint | 全関数 | 既定のタイムゾーンに頼らない |
| BR1.13 | constraint | 全関数 | 往復で時点を失わない（性質ベース） |
| BR1.14 | constraint | 全関数 | 純粋な関数 |
| BR1.15 | validation | compareInstants | 範囲の前後は時点で比べる（両端を含む） |
