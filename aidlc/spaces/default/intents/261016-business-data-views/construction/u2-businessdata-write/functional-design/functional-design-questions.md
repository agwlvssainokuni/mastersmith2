# Functional Design の質問 — U2 u2-businessdata-write

U2 は業務データの書き込み（作成・更新・削除）を受け持つ単位で、U1 に依存します。上流は次の文書です（`aidlc/spaces/default/intents/261016-business-data-views/inception/` の下）。

- `units-generation/unit-of-work.md`
- `requirements-analysis/requirements.md`
- `user-stories/stories.md`
- `domain-design/decisions.md`（ADR-003・ADR-008）
- `contract-design/contract-summary.md`（C3・C5・C6）

U1 の機能設計（`construction/u1-businessdata-read/functional-design/`）は、まだ承認されていません。

## 前の段から持ち越した論点

- ADR-008・Domain Design のレビュー R-01: 書き込みの DataSource とトランザクションの管理者の持ち方。→ Q1・Q2
- U1 のレビュー（承認の場へ持ち越し）:
  - R-02: 値を文字のまま渡すか、型つきで渡すか。→ Q3
  - R-03: 接続を貸す形。→ Q2 の前提
  - R-05: 名前の一覧をスキーマ・テーブル・カラムごとに分ける。U2 は直した後の形を前提にする。
- 契約 C5: 監査の行の欄の置き方。→ Q4
- 要件 FR6.2a: DB の制約が保存を拒んだときの誤りのカラム。→ Q5
- 契約 C3 の未決の点: 書き込みの設定が無いときと、定義が古いときの拒否。→ Q6
- AC2.2.9: 何も変えずに保存したとき。→ Q7
- FR10.4: `pattern` の当てはめ方。→ Q8

## 決まっていること（質問にしない）

**道と判定**
- 道は固定し、スキーマ名・テーブル名・主キー・値はすべて本文で運ぶ（U1 Q1: A）。
  - 作成: `POST /api/tables/rows`
  - 更新: `PATCH /api/tables/rows`
  - 削除: `POST /api/tables/rows/delete`
- 3本とも「ログインだけ」に分類する。
- 要求1件で権限の写しを1回だけ取り、その写しの DSL で判定と照合を行う（U1 Q2: A）。許す操作は、U1 の出し分けの関数の結果だけで判定する。

**本文の値の扱い**
- 書けないカラム（NONE・READ・DSL に無い・隠す・更新の主キー・BINARY・OTHER）を本文に入れたら、どれも同じ 400 `VALIDATION_FAILED` にする。`fieldErrors` の理由は `NOT_ALLOWED`。
- 作成は、入れたカラムだけを送る。送らないカラムは DB の既定値に任せる。
- 更新は、変えたカラムだけを送る。空にした文字の型は `""`、ほかの型は `null` にする。
- `password` は、入れたときだけ変え、アプリでは変換しない。
- 時差つきの型は UTC として読む。

**一意と同時の更新**
- DSL の `unique` のカラムは、保存の前に同じトランザクションの中で問い合わせる。
- DB の一意の制約の違反も、同じ「同じ値がある」の誤りにする。制約の無いカラムで同時に重なるのは、既知の制約にする（要件 Q9・F1）。
- 同時の更新は、後の人の値で上書きする（要件 Q6: B）。

**行の数と、対象DB の誤り**
- 更新・削除で当たった行が1行でなければ、巻き戻して `BUSINESS_ROW_NOT_UNIQUE` にし、監査に残す。
- 件数は「一致した行」で数え、MySQL・MariaDB のドライバーの設定で固定する。
- 対象DB の誤りは、U1 の code（UNAVAILABLE 503・TIMEOUT 503・MISMATCH 409）を使い回す（U1 Q7: A）。

**監査**
- 監査に残すのは、作成・更新・削除の成功と、業務の失敗（接続できない・時間切れを含む）。401・403・400 と、DSL に無いものの 404 は残さない（FR12.4・SQ6）。
- 監査の出来事は、対象DB の確定（または巻き戻し）の後に、対象DB の接続を返してから出す。
  - 理由: 確定の後に受け取る仕組みは、どのトランザクションにも結び付く。トランザクションの中で出すと、巻き戻した失敗の監査が残らない。
- 監査の失敗で、操作は失敗させない。
- 監査の書き込みの失敗の ERROR のログは、今は `detail` を含む全項目を出している。業務データの出来事では、主キーの値を出さない形に `AuditEventListener` を変える（FR12.3・project.md の Forbidden）。

**接続とアカウント**
- 内部DB の接続を持ったまま、対象DB を待たない（team.md）。
- 書き込みのアカウントには、DSL の対象のスキーマの `SELECT`・`INSERT`・`UPDATE`・`DELETE` だけを与え、DDL を与えない（project.md の Mandated）。見本の対象DB の初期化と README に書く。

**構造の検査と漏えい**
- SQL を実行してよいのは `RowSqlExecutor` だけにする。SpotBugs の `SQL_` の除外を書き込みのメソッドに広げる変更は、コード生成の計画で承認を得る。
- `TargetDbBoundaryArchitectureTest` は緩めない。
- `audit` の境界テストは、`audit` が依存してよい先を絞っていないため、緩める変更は要らない。
- 行の値と主キーを持つ型は、`toString` で値を伏せる。JDBC の例外のメッセージは、応答・ログ・監査に出さない。

## Q1. 書き込みの接続の設定を、`.env` でどう受け取るか

A. 接続先（種類・ホスト・番号・DB の名前・スキーマ）は読み取りの設定をそのまま使う。書き込みの分として、ユーザー名・パスワード・プールの大きさ・時間の上限だけを足す（例: `MASTERSMITH_TARGET_DB_WRITE_USERNAME`）。読み取りと書き込みが別の DB を指すことが起きない。ユーザー名とパスワードの片方だけがあれば「設定が無い」扱いにし、項目の名前だけを WARN で出す（推奨）
B. 書き込みの接続先も、別に受け取る（別の DB へ書けるが、設定の誤りを防げない）
X. Other (please specify)

[Answer]: A

## Q2. 対象DB のトランザクション（まとめて確定・巻き戻しする単位）をどう持つか

A. targetdb の設定の中に、書き込みの接続と専用のトランザクションの管理者を置く（どちらも既定の候補にしない、名前つきの部品）。targetdb の口は「処理を受け取り、接続を貸し、1つのトランザクションで確定か巻き戻しをして閉じる」形にする。業務処理の層はこの口を1回呼び、監査の出来事は口から戻った後に出す。既存の内部DB の注入に影響せず、targetdb の境界テストも緩めない（推奨）
B. 業務処理の層のメソッドに、管理者を名指しした `@Transactional` を付ける（短いが、監査の出来事を出す場所に気を付ける必要がある）
C. `RowSqlExecutor` の中で、確定と巻き戻しを直接書く（team.md の「対象DB 用の別のトランザクションの管理者」と合わない）
X. Other (please specify)

[Answer]: A

## Q3. 作成・更新の値と主キーを、対象DB にどの形で渡すか（U1 の R-02）

A. 値はサーバーでカラムの型として読んで確かめ、型つきで渡す（数は桁を保つ形、日時は日時の型、真偽値は真偽値）。型の対応は、1か所（`ColumnValueCodec`）で決める。DB が黙って丸めたり型を変えたりしない。書き込みでは `stringtype=unspecified` に頼らない（推奨）
B. すべて文字のまま渡し、型の変換は DB に任せる（MySQL・MariaDB の設定によっては黙って丸めることがあり、3種類で振る舞いが分かれうる）
X. Other (please specify)

[Answer]: A

## Q4. 業務データの監査の行に、スキーマ・テーブル・主キー・作業ロールをどう置くか

A. 監査の表に、スキーマ・テーブル・作業ロールの列を足す。主キーの値は今の `detail` の列（JSON）に入れ、上限を超えたら切り詰めて印を付ける。種類は `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED` の3つにし、失敗の理由は今の `failure_reason` に値を足す。テーブルごとに監査を探しやすくなる（推奨）
B. 列は足さず、すべてを `detail` の JSON に入れる（表の移行が要らない代わりに、絞り込みに JSON を読む必要がある）
C. 主キーの値にも専用の列を足す（複合の主キーを列で表しにくい）
X. Other (please specify)

[Answer]: A

## Q5. DB の制約（一意・外部キー・NOT NULL）が保存を拒んだとき、誤りのカラムをどう決めるか（FR6.2a）

A. 例外の文は読まず、DB の種類ごとの誤りの番号で、種類（一意・外部キー・NOT NULL・桁あふれ）を分ける。巻き戻した後に、DSL の一意・外部キーの定義と送ったカラムから、候補のカラムを問い合わせで確かめる。1つに決まればそのカラムを示し、決まらなければフォーム全体の誤りにする。3種類の DB で同じ結果になるが、失敗したときだけ問い合わせが増える（推奨）
B. PostgreSQL は制約の名前でカラムを決め、MySQL・MariaDB はいつもフォーム全体の誤りにする（DB によって分かれる）
C. DB が拒んだ保存は、いつもフォーム全体の誤りにする
X. Other (please specify)

[Answer]: A

## Q6. 対象DB に触れる前の拒否（書き込みの設定が無い・画面の定義が古い）の応答と監査

A. どちらも 409 にする（`BUSINESS_WRITE_NOT_CONFIGURED`・`BUSINESS_DEFINITION_CHANGED`）。判定は権限の確かめ（403）の後に置き、どちらも監査に残さない。対象DB に触れておらず、FR12.4 の失敗の一覧にも無いため（推奨）
B. A と同じ応答で、どちらも監査に失敗として残す
C. 書き込みの設定が無いときは 503 にして監査に残し、定義が古いときは A のとおりにする
X. Other (please specify)

[Answer]: A

## Q7. 何も変えずに更新の保存を押したとき（AC2.2.9）

A. 画面では、変えたカラムが無ければ保存を押せない。サーバーは、変えるカラムが1つも無い要求を 400 で拒否し、監査に残さない。今と同じ値を送った更新は、比べずに保存し、成功として監査に残す（推奨）
B. サーバーは何もせずに成功を返し、監査に残さない
C. 何もせずに成功を返し、監査にも成功として残す
X. Other (please specify)

[Answer]: A

## Q8. `pattern`（正規表現の検証）をどう当てはめるか

A. 値の全体が式に合うときだけ通す（画面の入力の `pattern` と同じ読み方）。照合には時間の上限を置き、超えたら検証の誤り（`PATTERN_TIMEOUT`）にする。打ち切りは、照合の中で文字を読むたびに締め切りを見る形にし、別のスレッドを残さない。上限の値は NFR 要件の段で決める（推奨）
B. 値の一部に式が合えば通す（DSL の書き手が `^…$` を付ける必要がある）
X. Other (please specify)

[Answer]: A

## Requested Changes Feedback

承認の場（2026-10-18）の Request Changes で、次の直しを決めた（Major と、単位の継ぎ目に関わる Minor）。

- R-01（Major）32 字の列: 監査の `failure_reason` の値は 32 字以内に収める。`BUSINESS_ROW_OPERATION_NOT_ALLOWED`（34 字）は、監査の値を `BUSINESS_ROW_OP_NOT_ALLOWED` にする（応答の code は U1 と同じまま。code と監査の理由の値は別の列挙）。足す監査の値がすべて 32 字以内であることを確かめる単体テストを置く。
- R-02（Major・継ぎ目）検証の材料: 定義の API のカラムの項目に、検証の材料（`formPart`・`required`・`nullable`・`minLength`・`maxLength`・`precision`・`scale`・`min`・`max`・`choices`・`hasPattern`）を U2（B4）が足す。U1 の型に置いた余地を使い、画面（U3）はそれを読む（U3 Q1: A）。
- R-03（Major）違反のカラムの確かめ: 誤りのカラムを決める問い合わせは、書き込みの口が貸している同じ接続の中で、巻き戻した後に行う（口の外に出ない。対象DB の接続は1本のまま）。確かめの問い合わせが失敗・時間切れになったら、カラムを示さず `BUSINESS_SAVE_REJECTED`（フォーム全体）にする。
- U1 R-03・R-05（継ぎ目）: 書き込みの口も貸す形、名前の一覧は種類ごと（すでに前提にしている形を確定として書く）。
- そのほかの Minor（R-04 監査の行の増え方、R-05 時間切れの監査の結果、R-06 MySQL・MariaDB のアカウントの範囲）は、この単位の記録に残し、後の段（NFR 要件・NFR 設計・基盤の設計）で拾う。

## Consolidated Summary Confirmation

- **接続の設定**: 書き込みの接続先は読み取りと同じにし、書き込み用のユーザー名・パスワード・プールの大きさ・時間の上限だけを足す。どちらかが欠ければ「設定が無い」扱いにする（Q1: A）。
- **トランザクション**: targetdb に書き込みの接続と専用の管理者を置く。どちらも既定の候補にしない名前つきの部品にする。targetdb の口が接続を貸し、1つのトランザクションで確定か巻き戻しをして閉じる。監査の出来事は、口から戻った後に出す（Q2: A）。
- **値の渡し方**: 値は型として読んで確かめ、型つきで渡す。型の対応は `ColumnValueCodec` の1か所で決める（Q3: A）。
- **監査**（Q4: A）:
  - 監査の表に、スキーマ・テーブル・作業ロールの列を足す。表の移行の番号はコード生成で決める。
  - 主キーは `detail` の JSON に入れ、長すぎるときは切り詰めて印を付ける。
  - 種類は `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED`。
  - 監査の書き込みが失敗したときの ERROR のログに、主キーの値を出さない。
- **DB が保存を拒んだとき**: 誤りの番号で種類を分け、DSL の定義から誤りのカラムを決める。決まらなければフォーム全体の誤りにする（Q5: A）。
- **対象DB に触れる前の拒否**（Q6: A）:
  - 書き込みの設定が無ければ 409 `BUSINESS_WRITE_NOT_CONFIGURED`、定義が古ければ 409 `BUSINESS_DEFINITION_CHANGED`。
  - どちらも監査に残さない。
  - 新しい code は、ほかに `BUSINESS_DUPLICATE_VALUE`・`BUSINESS_REFERENCE_NOT_FOUND`・`BUSINESS_SAVE_REJECTED`（いずれも 409）。
- **変えるものが無い更新**: 400 にし、監査に残さない（Q7: A）。
- **`pattern`**: 値の全体に合うときだけ通す。照合には時間の上限を置き、超えたら `PATTERN_TIMEOUT` にする（Q8: A）。
- **作成で空のままにした必須のカラム**: DB 由来の `required`（NOT NULL で既定値が無い）は、画面とサーバーの両方で「必須」の誤りにする。DB の既定値に任せるのは、`required` の無いカラムだけにする。ストーリーの X9（空のカラムは DB の既定値に任せる）を狭める読み方なので、承認の場でも確かめる。
- **件数と行の数**: 件数は「一致した行」で数え、MySQL・MariaDB のドライバーの設定で固定する。当たった行が1行でなければ巻き戻す。
- **見本の対象DB**: 書き込みのアカウントの初期化の台本（DML だけを許す）を足し、既存のボリュームへの足し方を README に書く。
- **決まり**: BR1〜BR13 の群で書く。
  - BR1 判定の順と code
  - BR2 書けるカラム
  - BR3 値の検証
  - BR4 `pattern`
  - BR5 一意
  - BR6 制約の違反
  - BR7 SQL
  - BR8 件数
  - BR9 接続とトランザクション
  - BR10 失敗の分け方
  - BR11 監査
  - BR12 漏えい
  - BR13 構造と設定
- **U1 との差**: U1 の型に、検証の材料（フォームの部品・検証・選択肢・NULL を許すか・長さと桁）を足す。差として記録する。
- **受け持つ受け入れ基準**: US2.1〜US2.5・US3.4 の受け入れ基準と、US3.1〜US3.3・US3.5・US3.6 の書き込みの部分。画面の部分は U3 へ、読み取りの部分は U1 へ、それぞれ Deferred にする。

- 承認の場の直し（Requested Changes Feedback のとおり）:
  - R-01: 監査の理由の値は 32 字以内（`BUSINESS_ROW_OP_NOT_ALLOWED`）、確かめの単体テストを置く
  - R-02: 検証の材料を定義の API のカラムの項目に U2 が足す
  - R-03: 違反のカラムの確かめは、貸している同じ接続の中で巻き戻した後に行い、失敗したらフォーム全体の誤り

Does this all look correct before I generate the functional design?

- Looks correct
- Request changes

[Answer]: Looks correct
