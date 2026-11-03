# 業務の決まり — u3-target-db-time

出典: 要件（`inception/requirements-analysis/requirements.md` の FR2.4・FR2.5・FR5・FR6・NFR2.1・NFR4・NFR5.1）、契約 C4、設計の決定 ADR-002・ADR-003・ADR-004・ADR-009（`inception/domain-design/decisions.md`）、質問と答え（`functional-design-questions.md` の Q1: B・Q2: B・Q3: B・Q4: B）、`team.md` の Code Style・Testing Posture。

```yaml
rules:
  - id: BR3.1
    statement: PostgreSQL の対象DB の接続は、読み取りと書き込みの両方で、セッションのタイムゾーンを UTC に明示に設定する。JVM の既定のタイムゾーンに頼らない。MySQL・MariaDB は今の設定（接続を作るときに UTC）を保つ。
    category: constraint
    applies_to: TargetDbSession（読み取りと書き込みの接続）
    trigger: 対象DB の接続を作るとき
    logic: IF 接続の DB が PostgreSQL THEN 接続を作るときにセッションのタイムゾーンを UTC にする（設定のやり方は NFR 設計）。ELSE MySQL・MariaDB は接続を作るときに UTC にする今のまま。読み取りのプールと書き込みのプールのどちらも対象にする。
    violation_behaviour: 設定に失敗した接続は借りられない（既存の借りる失敗と同じ扱い。新しい code は足さない）。
    source: FR5.1・AC2.2.4・AC5.1.1・ADR-004

  - id: BR3.2
    statement: 接続のタイムゾーンの変更の前後で、時差を持たない型（datetime・timestamp without time zone・date・time）の読み書きの値は変わらない。3種類の対象DB で、同じ時点は同じ UTC の値として読み書きされる。
    category: constraint
    applies_to: 業務データの読み書き（時点を表す型・時差を持たない型）
    trigger: PostgreSQL の接続の設定を足したとき・日時の値を読み書きするとき
    logic: IF 接続のタイムゾーンを UTC にした THEN 時差を持たない型は DB の値のまま読み書きされ、時点を表す型は同じ時点が3種類の対象DB で同じ UTC の文字になる。変わるなら、既存の値を壊さない移行の段取りを計画に足す（ADR-009）。
    violation_behaviour: 変わることが分かったら、設計を直して計画に足す（結合テストが落ちる形にする）。
    source: FR5.2・AC2.1.2・AC2.2.1・AC2.2.4・AC5.1.1・ADR-004・ADR-009

  - id: BR3.3
    statement: 日時・時刻の値の応答の秒の端数は、読むときの結果の列情報（DB の定義の桁）に固定し、末尾の 0 を落とさない。時点を表す型・時差を持たない日時・時差を持たない時刻・timetz のすべてに当てる。応答の項目名・形は変えない。
    category: calculation
    applies_to: 応答の日時・時刻の値
    trigger: 日時・時刻のカラムの値を応答にするとき
    logic: IF 値が読めて、結果の列情報が桁（0〜6）を返す THEN 応答の端数を、その桁の数字で並べる（端数が 0 でも `.000` のように桁を保つ。桁が 0 なら端数を付けない）。DSL の精度には頼らない（Q1: B）。
    violation_behaviour: 桁が得られないときは BR3.4。
    source: FR2.5・AC2.1.4・AC5.1.2・C4・Q1: B

  - id: BR3.4
    statement: 結果の列情報が桁を返さないときは、DB が送った値の桁のまま（今の動きのとおり末尾の 0 を落とさない範囲で）出す。DSL の精度へ切り替えない。
    category: policy
    applies_to: 応答の日時・時刻の値
    trigger: BR3.3 で桁が得られないとき
    logic: IF 結果の列情報が桁を返さない THEN DB が送った値の桁で出し、桁を足したり落としたりしない。
    violation_behaviour: なし（得られないことは結合テストで3種類の対象DB について確かめ、得られない DB があれば設計に戻す）。
    source: FR2.5・Q1: B

  - id: BR3.5
    statement: 時点を表す型の要求の値は、時差つき（Z・z・±hh:mm）で受けて UTC の時点に直して保存・比較する。時差の無い値は UTC とみなして受ける。日時として読めない値は拒否する。
    category: validation
    applies_to: 時点を表す型の作成・更新・検索の値
    trigger: 作成・更新・検索の要求を読むとき
    logic: IF 値が時差つきの日時として読める THEN UTC の時点にして使う。ELSE IF 時差の無い日時として読める THEN UTC とみなす。ELSE 入力の誤りとして、その項目の誤りで拒否する。夏時間の存在しない時刻の拒否は足さない（画面の側だけ。ADR-002）。
    violation_behaviour: 400 `VALIDATION_FAILED`（どの項目かを示す。保存しない・検索しない）。値は応答・ログに含めない。
    source: FR3.1・FR3.2・AC2.2.3・AC2.3.5・C4・ADR-002

  - id: BR3.6
    statement: PostgreSQL の timetz は、表示のタイムゾーンへ変換せず、DB に入っている時差のまま `HH:mm:ss[.fraction]±hh:mm` で返す（UTC にそろえない）。書き込みは時差つきの文字で受け、時差を落とさず保存する。時差の無い値は UTC とみなす。
    category: calculation
    applies_to: timetz のカラムの読み書き
    trigger: timetz の値を読む・書くとき
    logic: IF timetz の値を読む THEN 時差を DB の値のまま付けて文字にする（秒の端数は BR3.3）。IF timetz の値を書く THEN 時差つきの文字を、時差ごと保存する。時差の無い値は UTC の時差を付けて保存する。
    violation_behaviour: 読めない文字は BR3.5 の拒否と同じ（400 `VALIDATION_FAILED`）。
    source: FR2.4・AC2.2.2・AC2.2.3・C4・A1

  - id: BR3.7
    statement: 桁つきの時差つきの型（`timestamp(n) with time zone`・`timestamptz(n)`・`timetz(n)`）は、桁を落としたうえで、時差つきの型として扱う（時点を表す型・time_with_zone）。型の名前の括弧の部分だけを取り除き、括弧の後ろの `with time zone` を落とさない。
    category: calculation
    applies_to: 型の名前から受け渡しの型を決める部分
    trigger: DSL のカラムの型の名前を分類するとき
    logic: IF 型の名前が `timestamp(n) with time zone` THEN 時点を表す型にする。IF `time(n) with time zone` THEN time_with_zone にする。符号なしの印（`int(10) unsigned`）の扱いは今のまま。確かめて不具合でなければ直さず、確かめた結果を記録する（BR3.18）。
    violation_behaviour: なし（分類の誤りは結合テストが落ちる形にする）。
    source: FR6.2・AC5.1.3・R-08

  - id: BR3.8
    statement: 既定の DSL の生成は、MySQL・MariaDB・PostgreSQL の日時・時刻のカラムの秒の端数の桁を DB の定義から読み、DB の製品の既定の桁（PostgreSQL 6・MySQL/MariaDB 0）と違うときだけ、DSL の `dbType.precision` に書く。既定と同じ桁は書かない。
    category: calculation
    applies_to: 既定の DSL の生成（日時・時刻のカラム）
    trigger: 対象DB から既定の DSL を生成するとき
    logic: IF DB の定義の端数の桁が、その製品の既定の桁と違う THEN `dbType.precision` にその桁を書く。ELSE 書かない（既存の DSL との差分を、端数のある列だけにする。Q2: B）。生成した DSL を保存・往復しても端数が落ちない。確かめて不具合でなければ直さず、結果を記録する（BR3.18）。
    violation_behaviour: 桁を読めない DB・列は書かない（生成は失敗させない）。
    source: FR6.1・AC5.1.2・R-08・Q2: B

  - id: BR3.9
    statement: 書き込み（作成・更新）の値の秒の端数が、DSL に明示した桁（型の名前の `(n)` または `dbType.precision`）を超えるときは、その項目の入力の誤りとして拒否する。DSL に桁が明示されていない列は、確かめずに DB に任せる。
    category: validation
    applies_to: 日時・時刻のカラムの作成・更新
    trigger: 書き込みの要求の日時・時刻の値を読むとき
    logic: IF 列に DSL の桁が明示されていて、値の端数の桁がそれより長い THEN 拒否する（丸めない・切らない）。ELSE 確かめない（桁の無い DSL を、製品の既定の桁と見なして拒否しない）。確かめは書き込みの文の前に、DSL の値だけで行い、問い合わせを足さない。
    violation_behaviour: 400 `VALIDATION_FAILED`（どの項目かを示す。保存しない。値は応答・ログに含めない）。
    source: FR2.5・Q3: B

  - id: BR3.10
    statement: 日時・時刻の値が型として読めないとき（MySQL のゼロの日時・範囲外の値、time の 24 時間を超える値・負の値、PostgreSQL の infinity など）は、読めなかったセルだけ、DB が送った文字をそのまま返す。ほかの行・ほかのセルは普通に読み、要求全体を失敗させない。応答の項目は増やさない。
    category: policy
    applies_to: 業務データの一覧・詳細・参照の選択肢の日時・時刻のセル
    trigger: 結果の日時・時刻を型として読めないとき
    logic: IF セルを型として読めない THEN そのセルの値に DB が送った文字をそのまま入れる。IF NULL THEN null。読めなかった値は、ログ・エラー応答・トレースの属性に出さない（値の型だけを出す）。画面は、日時の形でない文字を読めない値として元のまま出す（U1 の、読めない時点を元の文字で返す決まり）。
    violation_behaviour: なし（読めない値は失敗ではない）。ただし文字の取り出し自体の失敗は既存の読み取りの失敗のまま。
    source: AC2.1.6・K-99・C4・Q4: B

  - id: BR3.11
    statement: 書き込みの値は、BR3.10 で返す読めない文字の形を、日時として読めない値として拒否する。読めない値を送り返しても、その値は保存されない。
    category: validation
    applies_to: 日時・時刻のカラムの作成・更新・検索
    trigger: 書き込み・検索の要求の値が日時として読めないとき
    logic: IF 値が型として読めない（ゼロの日時・24 時間を超える time・infinity など）THEN BR3.5 と同じ拒否にする。読めない値のままの更新は、その項目を送らない（画面が変えていない欄を送らない。U4）ことで避ける。
    violation_behaviour: 400 `VALIDATION_FAILED`（どの項目かを示す。値は含めない）。
    source: AC2.1.6・AC2.3.5・Q4: B

  - id: BR3.12
    statement: 主キーのカラムの値が読めない行（BR3.10）は、読んだ値を送り返して行を特定できないため、詳細・更新・削除を、行を1件に決められない操作と同じ拒否にする。一覧には出す。
    category: constraint
    applies_to: 主キーに日時・時刻のカラムを持つ行（読めない値を持つ行）
    trigger: 読めない主キーの値の行に対して、詳細・更新・削除を要求したとき
    logic: IF 要求が指す主キーの値が型として読めない THEN 行を特定せず拒否する。新しい code・状態コードは足さず、行を1件に決められない操作の既存の拒否を使う。
    violation_behaviour: 既存の「行を1件に決められない操作」の拒否のとおり（`BusinessDataProblemTypes` の既存の code）。
    source: AC2.1.6・AC2.5.3・Q4: B

  - id: BR3.13
    statement: 読んだ値をそのまま更新に送っても、対象DB の値は変わらない（往復）。時点を表す型・時差を持たない型・timetz のすべてで、秒の端数（0・3・6 桁）も落ちず・丸められない。主キー・同時更新の判定の値・参照の値の時点は、読んだままの文字を送り返しても、行の特定と衝突の判定が変わらない。
    category: constraint
    applies_to: 日時・時刻のカラムの読み書きの往復
    trigger: 読んだ値を更新・検索・主キー・参照の値に送り返したとき
    logic: IF 値を読んだ文字のまま（BR3.3 の桁で）送る THEN 読み直しても同じ値になり、更新の前後で対象DB の値が変わらない。桁を固定するため、読んだ値の桁は書き込みで超えない（BR3.9 で拒否されない）。
    violation_behaviour: 変わる型・桁の組み合わせは結合テストと性質ベースのテストが落ちる形にする。
    source: FR3.3・AC2.5.2・AC2.5.3・AC2.5.5・C4

  - id: BR3.14
    statement: DB が日時の範囲外・形の誤りで保存を拒んだとき（MySQL の timestamp の 2038 年の上限を含む）は、今の分類のとおりに応答する。この単位は新しい code・状態コードを足さず、上限の値を決め打ちして先に拒否しない。
    category: policy
    applies_to: 日時・時刻のカラムの書き込み
    trigger: 書き込みの文を DB が日時の範囲・形で拒んだとき
    logic: IF DB が日時の範囲外・形の誤りで保存を拒む THEN 既存の分類で、形の誤りは 400、保存の拒否は 409 `BUSINESS_SAVE_REJECTED` にする。MariaDB は版によって timestamp の上限が違うため、サーバー側で上限を決め打ちしない。
    violation_behaviour: 既存の書き込みの失敗の分類のとおり（`RowSqlFailureClassifier`）。
    source: AC2.1.6・K-99・team.md の code の決まり

  - id: BR3.15
    statement: 日時・時刻の値（読めない文字・端数を超えた値を含む）・対象DB の接続情報・JDBC の例外のメッセージは、アプリのログ・監査・トレースの属性・エラー応答に含めない。誤りは、項目の名前と誤りの種類だけで示す。
    category: constraint
    applies_to: この単位の読み書きと生成の失敗・拒否
    trigger: 拒否・読み取りの失敗・生成の失敗を応答・ログにするとき
    logic: IF 値の誤り・読めない値・接続の失敗が起きる THEN 値・接続情報・例外のメッセージを出さず、項目の名前・種類・SQLState・例外のクラスの名前だけを出す。日時・時刻を持つ型は `toString` で値を伏せる。
    violation_behaviour: なし（漏えいは TRACE を有効にした `XxxSecretLeakIT` で確かめる）。
    source: team.md の Code Style（業務データの行の値・JDBC の例外）・project.md の Forbidden

  - id: BR3.16
    statement: 日時の型の扱いを直しても、業務データの API が使う対象DB・内部DB への問い合わせの数を増やさず、読み取りの目標を変えない。
    category: constraint
    applies_to: 日時・時刻の読み書き
    trigger: 日時の値を読み書きするとき
    logic: IF 結果の列情報から桁を決める THEN 読む文の結果から取り、新しい問い合わせを足さない。接続の初期化は接続を作るときに1回だけ。
    violation_behaviour: 数が増えたら、`*ConnectionUsageIT` と同じ形の結合テストが落ちる。
    source: AC2.1.7・AC5.3.3・NFR2.1

  - id: BR3.17
    statement: 日時の型の対応・読み書きの部品は、JVM の既定のタイムゾーンを暗に使わない。時差と時計は明示に渡す。
    category: constraint
    applies_to: 日時・時刻の型の対応と読み書きの部品
    trigger: 日時の値を読み書きするとき・テストするとき
    logic: IF 日時を読み書きする THEN タイムゾーン（UTC）を明示に使い、JVM の `user.timezone` に結果を預けない。テストは時差（`ZoneId`）と時計（`Clock`）を注入して確かめる。
    violation_behaviour: なし（`user.timezone` を変えても値が変わらないことをテストで確かめる）。
    source: FR5.1・team.md の Code Style・Testing Posture

  - id: BR3.18
    statement: K-95（PostgreSQL の接続の時差）・K-98（秒の端数・桁つきの時差つきの型）は、未検証のため、NFR 設計の捨ての試しで先に確かめ、結果の要点を成果物に写す。不具合でなければ直さず、確かめた結果と直さなかったことを Build and Test に記録する。不具合なら、直しを同じ Intent のこの単位に入れる。
    category: policy
    applies_to: BR3.1・BR3.2・BR3.3・BR3.7・BR3.8
    trigger: NFR 設計・Build and Test
    logic: IF 確かめて不具合 THEN この単位で直す。ELSE 直さず、確かめた結果を記録する。
    violation_behaviour: 確かめずに「直した」「直さなくてよい」とは記録しない。
    source: FR6.3・AC5.1.4・AC5.1.5・ADR-009

  - id: BR3.19
    statement: この単位は、実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）を変えない。変えるときは4か所を同じ値にそろえ、配備の記録に書く。
    category: constraint
    applies_to: 実行時のタイムゾーンの設定
    trigger: タイムゾーンの設定を変えるとき
    logic: IF この単位で設定を変える必要が出た THEN 4か所を同じ値にそろえ、配備の記録に書く。ELSE 変えない（見込み）。接続のタイムゾーン（BR3.1）は接続の初期化で行い、JVM の設定には頼らない。
    violation_behaviour: 4か所がそろわないときは変更を取り込まない。
    source: AC5.3.4・team.md の Deployment

reverse:
  - { id: BR3.9, status: N/A, target: "Q3: B で足した決まり（端数超過の拒否。要件 FR2.5 の保存の側）" }
  - { id: BR3.11, status: N/A, target: "Q4: B で足した決まり（読めない形の書き込みの拒否）" }
  - { id: BR3.12, status: N/A, target: "Q4: B で足した決まり（主キーが読めない行）" }
  - { id: BR3.15, status: N/A, target: "技術の決まり（漏えいの防止。team.md・project.md の Forbidden）" }
  - { id: BR3.17, status: N/A, target: "技術の決まり（JVM の既定のタイムゾーンに頼らない）" }
```

## 決まりの要約（導出）

| 決まり | 対象 | 要点 |
|---|---|---|
| BR3.1・BR3.2 | 接続 | PostgreSQL も UTC を明示。時差を持たない型の値は変わらない |
| BR3.3・BR3.4 | 応答の端数 | 結果の列情報の桁に固定。得られないときは DB の桁のまま |
| BR3.5・BR3.6 | 要求の値 | 時点は時差つき・無ければ UTC。timetz は時差ごと保存 |
| BR3.7・BR3.8 | 型の名前・生成 | 桁つきの時差つきの型を時点の型に。生成は既定と違う桁だけ書く |
| BR3.9 | 書き込みの端数 | DSL に明示した桁を超えたら 400 |
| BR3.10〜BR3.12 | 読めない値 | 読めないセルだけ元の文字。書き込みは拒否。主キーが読めない行の操作は拒否 |
| BR3.13・BR3.14 | 往復・範囲外 | 読んだまま送り返して不変。範囲外の保存は今の分類 |
| BR3.15〜BR3.17 | 横断 | 漏えいなし・問い合わせを増やさない・JVM の既定に頼らない |
| BR3.18・BR3.19 | 確かめと設定 | 捨ての試しで確かめ記録。実行時のタイムゾーンの設定は変えない |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 単位: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`（U3）
- 契約・決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C4）・`inception/domain-design/decisions.md`（ADR-002・003・004・009）
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-design-questions.md`（Q1: B・Q2: B・Q3: B・Q4: B）

## Assumptions & Open Questions

- [assumption] BR3.7・BR3.8 の不具合の見込み（括弧以降を落とす分類、生成が日時の精度を読まない）は、コードを読んだ内容で未実行。NFR 設計の捨ての試しで確かめる（BR3.18）。
- [assumption] BR3.4 の「桁が得られない」DB・型があるかは未確認。得られる想定で、3種類の対象DB の結合テストで確かめる。
- [assumption] BR3.9 は、DSL の桁を明示しない列を確かめない（Q3: B の「桁が分からない列」）。Q2: B で既定と同じ桁は DSL に書かれないため、書かれていない = 製品の既定の桁と等しい列とは限らない手書きの DSL を拒否しないための読み方。
- [assumption] BR3.12 の拒否の code は既存の「行を1件に決められない操作」のものを使う（新しい code を足さない）。具体の名前は Code Generation で既存の定義を確かめて決める。
