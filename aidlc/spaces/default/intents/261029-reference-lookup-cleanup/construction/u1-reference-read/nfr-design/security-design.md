# セキュリティの設計 — U1 u1-reference-read

参照先の読み取りで、権限の判定・識別子の照合・入力の上限・値の漏えいの防ぎを、既存の部品と境界を緩めずにどう作るかを決める。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.14・NFR5.1・NFR5.2）と `observability-requirements.md`（NFR1.15）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 2節・3節・5節、`rules.md` の BR1〜BR3・BR5・BR7・BR9〜BR11）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C2・C6）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/nfr-design-questions.md` と試しの結果 `trial-results.md`
- `aidlc/spaces/default/memory/team.md`（他テーブル参照の必須のテスト (a)〜(k)、Code Style の SQL の組み立て・`toString` の伏せ字・境界テスト）、`project.md`（Forbidden・Mandated）

## 1. 守りの層

| 層 | 守り | 要件 |
|---|---|---|
| 入口（web） | 新しい道 `POST /api/tables/reference/rows` を `@ApiAccess(AUTHENTICATED)` に分類。本文の上限 65,536 バイトを既存の `RequestBodyLimitRoute` に道を1つ足して当てる。本文の形の確かめ（BR1.4）は名前に依らない 400 で、403 より前 | NFR1.4・NFR1.13 |
| 判定（service） | 権限の写しを1回だけ取り（`TableAccessResolver`）、参照元と参照先の両方を同じ写しで判定（`accessOf`）。拒否は同じ 403 `ACCESS_DENIED` | NFR1.1・NFR1.2 |
| SQL（repository） | 識別子は同じ写しの DSL から引き、`SqlIdentifiers.quoteKnown` で照らしてから引用。値はバインド変数。SQL を組み立てて流すのは既存の `RowSqlExecutor` の `search` だけ | NFR1.3・NFR1.7・NFR1.12 |
| 出口 | 値・ラベル・セルは JSON の文字列として加工せず返す。誤りは既存の code だけ。JDBC の例外のメッセージは捨てる | NFR1.5・NFR1.6・NFR1.8 |

## 2. 権限の判定の設計

- `accessOf(参照元の判定の材料, 参照先の判定の材料, 参照の定義)` を domain の純粋な関数にし、`AVAILABLE`・`NO_PERMISSION` を返す（BR3.1）。欠ける条件は機能設計の BR3.1 のとおり（参照元のカラムが FULL でない・参照先の表が読めない・`valueColumn` が NONE・ラベルのカラムが NONE・`lookupList` のカラムが NONE・使える検索の条件が残らない・`valueColumn` がバイナリか password）。
- 判定は要求ごとにサーバー側で行い、画面の表示に頼らない（`project.md` の Mandated）。作業ロールの切り替え・割り当ての変更の直後の次の要求で結果が変わる。写しをキャッシュしない。
- 定義の取得では、参照先の権限の不足を項目の状態（`NO_PERMISSION`）にし、要求は 200 のまま（BR1.1）。検索 API では 403 の同じ応答にする（BR1.3）。
- 403 の応答は、権限が無い・名前が DSL に無い・参照のカラムでない・REFERENCE に検索・条件のカラムが `searchFields` に無い・ラベルの値を型として読めない・ラベルの行が無い、のすべてで本文の構造とメッセージを同じにし、`fieldErrors` を付けない（NFR1.2）。

## 3. SQL の組み立ての設計

- 参照先のスキーマ・テーブル・`valueColumn`・ラベル・検索と一覧の列は、要求から受け取らない。要求に来るのは参照元の `schema`・`table`・`column` だけで、それも同じ写しの DSL で照らす（NFR1.3）。
- この段で足す SQL の部品（`performance-design.md` 1.2節の `lengthWithin`・`sortExpression`）は、照らした後の引用済みのカラムの参照と、コードの中の定数（10,000・100）だけを受ける。利用者の入力を受けないため、SQL の組み立ての新しい経路を作らない。
- `prepareStatement`・`createStatement` を呼んでよい場所は今のまま（`RowSqlExecutor`）。行の SQL の新しいメソッドを作らず、`search` に `ReferenceReadSpec` を渡す（BR11.2）。`backend/config/spotbugs-exclude.xml` の `SQL_` の除外は足さず広げない。足す口が既存の除外の範囲（クラス・メソッド・パターン名）に入るかは、コード生成で SpotBugs を流して確かめる。入らずに指摘が出たときは、除外を足さずに組み立ての形を直す（直せなければ、コード生成の計画に明記して依頼者の承認を得る。`team.md`）。
- 部分一致は既存の `ContainsPattern` と `LIKE ? ESCAPE` を使い回す（NFR1.7）。

## 4. 入力と読む量の上限

| 上限 | 値 | 超えたとき | 要件 |
|---|---|---|---|
| 本文 | 65,536 バイト | 既存の `PAYLOAD_TOO_LARGE` | NFR1.4 |
| 検索・ラベルの値 | 1,000 文字 | 400 `VALIDATION_FAILED` | NFR1.4 |
| ページ | 1〜1,000,000 | 400 `VALIDATION_FAILED` | NFR1.4 |
| 読む行 | 選択肢・`check`・`countState` 201、ピッカー 21、ラベル 1（`LIMIT` と `setMaxRows`） | 読まない | NFR2.6 |
| 値のカラムの長さ | 10,000 字（SQL の条件で除く。読むのは 10,001 字まで） | その行を返さない・`check` は `NOT_FOUND_OR_DENIED` | NFR2.8・NFR1.9 |

値のカラムの長さの上限は、アプリの外で入れた長い値で読む量とヒープが膨らむのを防ぐ守りでもある（試しで、切らずに 201 行を読むと1要求で約 420 MB を割り当てた。`trial-results.md` 2.4）。

## 5. 漏えいの防ぎ

- 値を持つ新しい型（`ReferenceValue`・`ReferenceOption`・`ReferenceRow`・`ReferenceCondition`・`ReferenceRowsQuery`・`ReferenceRows` など）は `toString` を上書きして値を伏せる。`TraceAspect` が web・service・domain・repository の引数と戻り値を TRACE に出すため、controller は受け取る時点から伏せる型（要求の record の `toString` が伏せる形）で受け、中で `String` に戻して層をまたがない（`project.md` の学び）。行を汎用の `Map` のまま渡さない。
- 道は固定で、値は本文で運ぶ（BR10.3）。トレースの道の属性に値が残らない。
- JDBC の例外は、既存の `RowSqlFailureClassifier` が SQLState・業者の番号・例外のクラスの名前だけを持つ `RowQueryFailure` に変え、メッセージと原因を捨てる。試しで、時間切れの例外は3種類で形が違ったが（PostgreSQL `57014`、MySQL `MySQLTimeoutException`、MariaDB `SQLTimeoutException` の `70100`/1969）、どれもメッセージを使わずに分けられる（`trial-results.md` 2.3）。
- ログは `observability-design.md` 2節の WARN の1行だけで、名前・値・ラベル・接続先・例外のメッセージを出さない。読み取りは監査に残さない（BR1.7）。

## 6. 確かめるテスト（`team.md` の (a)〜(k) との対応）

| 区分 | テスト | 要件 |
|---|---|---|
| (a) 認可 | 検索 API の 401・403・200 をパラメーターの表で。403 の利用者は BR3.1 の欠ける条件ごとに1人ずつ（要る権限だけを欠く利用者）と、`valueColumn` がバイナリ・password の形。停止中の利用者は 401。切り替えの直後の次の要求で変わる。定義では `NO_PERMISSION` で 200 | NFR1.1 |
| (b) 識別子 | DSL に無い表・カラム、別の参照先のカラムへの差し替えが 403 で、SQL が1つも流れない | NFR1.3 |
| (c) 件数 | `reliability-design.md` 4節の行の数の境界（200・201、長い値の行が混ざる形） | NFR2.6・NFR2.8 |
| (d) 漏えい | `ReferenceSecretLeakIT`（TRACE を有効）で、選択肢・検索・ラベル・`check`・`countState` の経路で架空の目印の値がログに出ない。外部へ送る設定を有効にした結合テストで、トレースの属性に値が無い。新しい型の `toString` の単体テスト | NFR1.5・NFR1.15 |
| (e) エスケープ | `%`・`_`・`\` を含む値が文字どおりにだけ一致（3種類） | NFR1.7 |
| (f) 画面に出す値 | `<script>` などを含む値・ラベルが応答の JSON に同じ文字列で入る（画面の側は U3） | NFR1.8 |
| (g) 確かめの口 | `check` の各場合が `NOT_FOUND_OR_DENIED` の1つになり、型として読めない値・`NO_PERMISSION` では問い合わせが流れない。10,001 字の行しか一致しない値も `NOT_FOUND_OR_DENIED` | NFR1.9 |
| (h) 主キーの形 | 複合の主キー・主キーの無い表・ビュー・自己参照・一意でない `valueColumn` で 500 にならない | NFR1.10 |
| (i) 3種類の DB | 選択肢・検索・ラベル・`check` を MySQL・MariaDB・PostgreSQL で。MariaDB の PAD SPACE で末尾の空白だけが違う行（`x` と `x`＋空白 9,999 個・10,000 個）を入れ、`check` が厳密な一致と長さの条件で決めた結果になる | NFR1.11 |
| (j) 接続の数 | `scalability-design.md` 2節 | NFR3.1 |
| (k) 境界 | `BusinessDataBoundaryArchitectureTest`・`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・SpotBugs の除外を緩めない。差分が足す方向だけ | NFR1.12 |
| 入力の上限 | 本文 65,536・65,537 バイト、値 1,000・1,001 文字、ページ 0・1・1,000,000・1,000,001 | NFR1.4 |
| 403 の同一性 | 各場合の状態コード・`code`・本文の構造とメッセージを比べる。判定の順の表のテスト | NFR1.2 |
| API の分類 | `ApiAccessArchitectureTest` が分類の書き忘れで落ちる形のまま通る | NFR1.13 |
| 性質ベース | `accessOf`・同じ値を落とす関数・使える条件を選ぶ関数・固定の並びを作る関数・`sameReferenceValue` に jqwik（失敗時の種を記録） | NFR1.14 |
| カバレッジ | `businessdata.*`・`targetdb.domain`・`targetdb.config` のパッケージごとに行 80%・分岐 70%。手を入れるパッケージが `packagesJudgedByTotal` に当たらないことを計画で確かめる | NFR5.1・NFR5.2 |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 機能設計 BR4.2・BR5.8（カラムそのもので並べる） | 文字の型は先頭の 100 字で並べる（F1: A） | `performance-design.md` 2節。セキュリティの守り（識別子の照合・バインド）は変わらない |

## 後の段に回すこと

- **Code Generation**: 6節のテストの実装、新しいクラス（`ReferenceLookup` など）と方言の口の追加のコード生成の計画での承認（BR11.3）、SpotBugs の除外が変わらないことの確かめ。
- **U2**: `check` の結果の 409 と監査への写し（(g) の残り）。
- **U3**: 画面でラベルと値を文字として出すこと（(f) の画面の側）。
