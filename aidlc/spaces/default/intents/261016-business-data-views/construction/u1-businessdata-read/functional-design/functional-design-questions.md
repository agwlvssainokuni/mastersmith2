# Functional Design の質問 — U1 u1-businessdata-read

U1 は業務データの読み取り（定義・一覧と検索・詳細）を受け持つ単位です。ここで決めるのは、業務の決まりと、データの受け渡しの形です。上流は次の文書です（いずれも `aidlc/spaces/default/intents/261016-business-data-views/inception/` の下）。

- `units-generation/unit-of-work.md`
- `requirements-analysis/requirements.md`
- `user-stories/stories.md`
- `domain-design/components.md`・`decisions.md`（ADR-001〜008）
- `contract-design/contract-summary.md`（C1〜C7）

## 前の段から持ち越した論点

- Contract Design のレビューの R-10: 道に入る名前と、入口の検査（StrictHttpFirewall）。→ Q1
- Domain Design のレビューの R-03・契約 C7: 権限の写しの DSL を、SQL の名前の照合にも使う。→ Q2
- R-04: 全体の件数の重さと、バイナリ・大きな文字列。→ Q4・Q5
- 契約の未決の点: 業務の失敗の code、型の分け方、行を見分ける値。→ Q5〜Q8
- コードで新しく見つけた点:
  - DSL は、要件に無い検索の演算子 `IN` と一覧の書式 `BOOLEAN_ON_OFF` を受け付ける（`SearchOperator.java`・`ListFormat.java`）。→ Q3
  - PostgreSQL の `uuid` などは型の分類の外。→ Q6
- 要件 FR13.1 は「今の置き場の API と同じ」と書くが、今の置き場の API は理由を区別せず 403 にしている。承認済みの判定の順（404 → 403）を正とし、差を成果物に書く。

## 決まっていること（質問にしない）

- **置き場**: 新しい機能 `businessdata` に、web・service・domain・repository の層を置く（ADR-001）。
- **API**: 読み取りの API は3本で、すべて「ログインだけ」に分類する。権限はテーブルごとに業務処理の層で判定する（C2）。
- **権限**: 要求1件で権限の写し（`snapshotFor`）を1回だけ取り、判定に使う（C7）。作業ロールが無ければ、すべて NONE・不可にする。
- **判定の順**: 401 → 404（DSL が無い・DSL に無い名前）→ 403 `ACCESS_DENIED`（テーブルが NONE）→ 409（主キーとビューの決まり）→ 400 → 404（行が無い）→ 対象DB とうまくいかないとき（ADR-006）。
- **出し分けと許す操作**: 1つの純粋な関数で求め、同じ関数で API の受け付けも判定する（ADR-005）。性質ベースのテスト（jqwik）を当てる。
- **書けない・検索できない・並べ替えできないカラム**: 要求に入れたら、どれも 400 `VALIDATION_FAILED` で、`fieldErrors` も同じ形にする（C2）。
- **詳細の API**: 更新の画面も、詳細の API で値を読む。
- **行の値の運び方**: 行の値は本文で運び、道にも問い合わせの部分にも載せない（CQ1）。
- **ページ**: 番号は 1 から。1ページの件数は 20・50・100 だけを受け付ける。
- **定義の識別**: 要求の `definitionId` が今の定義と違えば、`definitionChanged: true` を付ける。
- **SQL の組み立て**: 名前は DSL で照らしてから、既存の `SqlIdentifiers.quoteKnown` で引用し、値はバインド変数で渡す（project.md の Forbidden）。
- **読み取りの接続**: `targetdb.service` に読み取りの接続の口を足す。一覧・詳細の目的を `ReadPurpose` と時間の上限に足す（値は NFR 要件の段で決める）。
- **内部DB の接続**: 内部DB の接続を持ったまま、対象DB を待たない。
- **漏えいの防止**: 行の値の型は `toString` で値を伏せ、JDBC の例外のメッセージはログにも応答にも出さない（team.md）。
- **構造の検査**: `BusinessDataBoundaryArchitectureTest` を足し、SQL を実行してよい場所を1つの部品に絞る。`RoleBoundaryArchitectureTest` に `businessdata` を足す変更は、コード生成の計画で承認を得る。
- **3種類の対象DB の差**: NULL の並び（昇順で最後）、部分一致の `LIKE ? ESCAPE '!'`。大文字と小文字の扱いは照合順序に任せる。
- **内部DB の表**: 内部DB に新しい表は持たない。entities.md には、保存しない値の型だけを書く。

## Q1. スキーマ名・テーブル名を API のどこで運ぶか（R-10）

入口の検査（StrictHttpFirewall）は、道に入るエンコードした `/`・`%`・`;`・改行を 400 で拒みます。DSL の名前は、これらの文字を含みえます。

A. スキーマ名とテーブル名も本文で運ぶ。道は `/api/tables/definition`・`/api/tables/rows/search`・`/api/tables/rows/get` のように固定し、U2 の書き込みも同じ形にそろえる。どんな名前でも同じように扱え、AC3.6.1（`;`・改行を含む名前は 404）をそのまま満たせる。代わりに、承認済みの契約 C2・C3 の道の形と違う形になる（差を記録する）。定義の API は POST になる（推奨）
B. 契約どおり道に置く。入口の検査に拒まれる文字を含む名前のテーブルは開けない、という既知の制約にする
C. 契約どおり道に置き、入口の検査を緩める（前の Intent の安全の決まりを緩める）
X. Other (please specify)

[Answer]: A

## Q2. 権限の写しの DSL を、SQL の名前の照合にどう使うか（R-03・C7）

今の写し（`PermissionSnapshot`）は DSL の識別だけを外へ渡し、DSL のモデルそのものを渡す口がありません。

A. `role.domain` の `PermissionSnapshot` に、DSL のモデルを読み取りだけで渡す口を1つ足す。写し1つで判定と照合を行うので、2つの間で DSL がずれない。代わりに `role` の本体に小さな変更が入る（`role` の境界テストの変更と同じ Bolt で行う）（推奨）
B. `role` は変えない。DSL の提供口から今の DSL を読み、識別を写しと比べる。違えば写しを1回だけ取り直し、それでも違えば「定義が変わった」として扱う（手順とテストが増える）
C. B と同じだが、違えば取り直さず、すぐ「定義が変わった」にする（DSL を適用し直した直後の要求が失敗しやすい）
X. Other (please specify)

[Answer]: A

## Q3. 要件に無い DSL の値（検索の演算子 `IN`・一覧の書式 `BOOLEAN_ON_OFF`）

A. `IN` は `CHOICE` と同じに扱い、`BOOLEAN_ON_OFF` は画面で「オン／オフ」と出す。DSL に書いた設定がそのまま効く。代わりに、契約 C2 の列挙に値を足し、U3 の書式が1つ増える（推奨）
B. 対応しない。`IN` のカラムは検索の項目に出さず、`BOOLEAN_ON_OFF` は「はい／いいえ」で出す（DSL の設定が黙って効かなくなる）
C. `IN` だけ B のとおり出さず、`BOOLEAN_ON_OFF` は A のとおり対応する
X. Other (please specify)

[Answer]: A

## Q4. 全体の件数の数え方（ADR-007・FR4.2）

A. 検索のたびに、条件に合う全体の件数を正確に数える。一覧の時間の上限の中で、ページの行より先に数える。時間切れなら、要求全体を「時間切れ」の誤りにする。最後のページを超えたら、行を読まずに空のページを返す。要件どおりだが、件数の多い表では遅くなりうる（目標は NFR 要件の段で決めて確かめる）（推奨）
B. 件数は上限（例: 10,000 件）まで数え、超えたら「10,000 件以上」と返す（要件 FR4.2 の変更になる）
C. 件数を数えるのが時間切れになったときだけ、件数を空にして返す（応答と画面の場合分けが増える）
X. Other (please specify)

[Answer]: A

## Q5. バイナリと大きな文字列の値

A. バイナリの型は読まず、応答に値を入れない。カラムには型の印（`BINARY`）だけを付け、画面は「（バイナリ）」と出す。検索・並べ替え・入力にも使わない。大きな文字列は上限の文字数で切る（一覧は例 256 字、詳細は例 64K 字）。切ったことが分かる印を応答に付ける。上限の値は NFR 要件の段で決める。読む量とメモリが一定に収まる（推奨）
B. バイナリは A と同じにし、大きな文字列は詳細と同じ上限まで一覧でも全部返す（一覧の応答が重くなりうる）
C. バイナリも文字（Base64）にして上限まで返す（量が増え、伏せるかどうかの見分けも要る）
X. Other (please specify)

[Answer]: A

## Q6. 型の分類の外の型（PostgreSQL の `uuid`・`json`・列挙の型など。AC1.4.8）

A. 値を文字として読み、読み取りだけの文字（`valueType` は `OTHER`）として出す。PostgreSQL の接続の設定に `stringtype=unspecified`（文字で渡した値の型を DB に決めさせる指定）を足し、主キーで行を指すことと、完全一致の検索ができるようにする。部分一致と範囲には使わない。代わりに、`targetdb` の接続の設定に1項目が入る（推奨）
B. SQL の中で、許す型の名前の一覧と照らして値を変換する（`CAST(? AS uuid)`。DB の種類ごとに型の名前の一覧が要る）
C. 検索・並べ替え・行の指し方に使わない（AC1.4.8 を満たせない）
X. Other (please specify)

[Answer]: A

## Q7. 読み取りで対象DB とうまくいかないときの code と状態コード

A. 3つに分ける。
- 接続できない（読み取りの設定が無いときを含む）: 503 `BUSINESS_TARGET_DB_UNAVAILABLE`
- 時間切れ: 503 `BUSINESS_TARGET_DB_TIMEOUT`
- DSL にある表やカラムが対象DB に無い: 409 `BUSINESS_TARGET_DB_MISMATCH`

画面は理由ごとに文を出し分けられ、U2 の書き込みも同じ code を使う（推奨）
B. A と同じだが、時間切れだけ 504 にする（ほかの機能に 504 の前例は無い）
C. 3つとも1つの code（503 `BUSINESS_TARGET_DB_UNAVAILABLE`）にまとめる（監査で接続できないと時間切れを分けられなくなる）
X. Other (please specify)

[Answer]: A

## Q8. 主キーが一覧の列に無いときの「行を見分ける値」（AC1.4.7・RQ6、U3・U4 と共通）

A. サーバーが代表のカラムを決め、定義の応答で返す。主キーのカラムがすべて一覧の列にあれば主キーを、無ければ一覧で最初に並ぶカラム（`password`・バイナリを除く）を使う。画面はそのセルを詳細へのリンクにし、「詳細: …」「操作: …」の名前に使う。代わりに、代表のカラムの値は行どうしで重なりうる（推奨）
B. いつも主キーの値を使う（一覧に出さない主キーでも名前と見出しに出す。意味の無い番号が名前に出やすい）
C. 画面（U3）が自分で決める（判定が画面に移る）
X. Other (please specify)

[Answer]: A

## Requested Changes Feedback

承認の場（2026-10-18）の Request Changes で、次の直しを決めた（依頼者の範囲の指定: Major と、単位の継ぎ目に関わる Minor）。

- R-01（Major）演算子と型の組み合わせ: 演算子と型の表を BR4 に置く。CONTAINS は文字の型だけ、RANGE は数・日付・日時・時刻だけ、EQUALS は password・BINARY を除くすべて、CHOICE・IN は固定の選択肢か真偽値のカラムだけ。表に無い組み合わせは定義の検索の項目から外し、要求に入っていれば 400 `VALIDATION_FAILED`（`NOT_ALLOWED`）にする。値は送る前に型として読んで確かめるため、型の誤りの SQLState（`22P02`・`22003`・`42883` など）は通常起きないが、起きたら値を出さずに 400 に写す。
- R-02（Major）`stringtype=unspecified`: 接続の設定には足さない（共有の接続とスキーマの読み取りに効かないようにする）。値は U2 Q3: A と同じく `ColumnValueCodec` で型として読み、型つきでバインドする。分類の外の型（`OTHER`）は、PostgreSQL ではその値だけを文の中で DB に型を決めさせる形（`setObject(..., Types.OTHER)`）で渡す。3種類の DB での確かめは NFR 設計の試しで行い、だめなら主キーの指し方と完全一致の検索から `OTHER` を外す（AC1.4.8 との差として記録）。
- R-03（Minor・継ぎ目）接続の口: 読み取りの接続の口は、接続を返さずに貸す形（処理を受け取り、貸して、閉じる）にする。件数とページは同じ1回の貸し出しの中で読む。
- R-04（Minor・継ぎ目）`definitionChanged`: 400 の誤りの応答（Problem Details）にも、契約 C2 の誤りの型のとおり `definitionChanged` を付ける（U3 Q2: A の前提）。
- R-05（Minor・継ぎ目）名前の一覧: `quoteKnown` に渡す名前の一覧を、スキーマ・テーブル・カラムごとに分ける。
- U2 R-02（継ぎ目）定義の応答: 定義の API のカラムの項目は契約 C2 の `ColumnDefinition` の形にする。U1（B2）は読み取りの項目を返し、検証の材料（フォームの部品・必須・長さ・桁・範囲・選択肢・pattern の有無）は U2（B4）が足す（U3 Q1: A）。U1 の型にその余地を置く。
- R-06（Minor）X2 の文言: `BOOLEAN_ON_OFF` は DSL v2 の JSON Schema に既にあるため、差を契約 C2 の列挙との差に直す。
- そのほかの Minor は、この単位の記録に残し、後の段で拾う。

## Consolidated Summary Confirmation

- **API の道**: 道は固定し、スキーマ名・テーブル名・行の値は本文で運ぶ（Q1: A）。契約 C2・C3 の道の形との差は、functional-spec.md の「上流との差」に記録する。U2 も同じ形にそろえる。
- **権限の写し**: 要求1件で写しを1回だけ取る。`PermissionSnapshot` に、DSL のモデルを読み取りだけで渡す口を足し、判定と SQL の名前の照合の両方に同じ DSL を使う（Q2: A）。
- **IN と BOOLEAN_ON_OFF**: `IN` は `CHOICE` と同じに扱い、`BOOLEAN_ON_OFF` は画面で「オン／オフ」と出す。契約の列挙に足す（Q3: A）。
- **全体の件数**: 検索のたびに正確に数える。時間切れなら要求全体を時間切れの誤りにする。最後のページを超えたら、空のページを返す（Q4: A）。
- **大きな値**: バイナリは読まず `BINARY` の印だけにする。大きな文字列は、一覧と詳細で上限の文字数で切り、切ったことの印を付ける。上限の値は NFR 要件の段で決める（Q5: A）。
- **型の分類の外の型**: 文字として読み、読み取りだけの `OTHER` として出す。PostgreSQL の接続に `stringtype=unspecified` を足す（Q6: A）。
- **対象DB とうまくいかないとき**: 503 `BUSINESS_TARGET_DB_UNAVAILABLE`、503 `BUSINESS_TARGET_DB_TIMEOUT`、409 `BUSINESS_TARGET_DB_MISMATCH` に分ける。U2 も同じ code を使う（Q7: A）。
- **行を見分ける値**: サーバーが代表のカラムを決め、定義の応答で返す（Q8: A）。
- **決まり**: BR1〜BR11 の群で書く。
  - BR1 判定の順と code
  - BR2 出し分けと許す操作
  - BR3 名前の照合と SQL
  - BR4 検索
  - BR5 ページと並べ替え
  - BR6 詳細
  - BR7 値の受け渡し
  - BR8 接続と時間の上限
  - BR9 定義の識別
  - BR10 漏えいの防止
  - BR11 分類と構造の検査
- **エンティティ**: 内部DB に保存するエンティティは無い。要求の中だけで使う値の型（`TableAccessView`・`ColumnAccess`・`RowKey`・`RowValues`・`SearchCondition`・`PageRequest`・`DefinitionId`）を書く。
- **受け入れ基準の受け持ち**: US1.1〜US1.5・US3.1〜US3.3・US3.5・US3.6 の受け入れ基準のうち、サーバー側の部分を受け持つ。画面の部分は U3 へ、書き込みの部分は U2 へ、受け持たないものとして記録する（Deferred）。

- 承認の場の直し（Requested Changes Feedback のとおり）:
  - R-01: 演算子と型の表を BR4 に置き、表に無い組み合わせは検索の項目から外して 400 にする
  - R-02: `stringtype=unspecified` は足さず、値は型つきでバインドする（`OTHER` は PostgreSQL で `Types.OTHER`、NFR 設計の試しで確かめる）
  - R-03・R-04・R-05: 接続の口は貸す形、400 にも `definitionChanged`、名前の一覧は種類ごと
  - U2 R-02: 定義の応答は `ColumnDefinition` の形（検証の材料は U2 が足す）

Does this all look correct before I generate the functional design?

- Looks correct
- Request changes

[Answer]: Looks correct
