# セキュリティの要件 — U1 u1-reference-read

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR1・NFR5・NFR9、FR1.3・FR1.4・FR1.6・FR2.2・FR2.2a・FR2.6）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（US1.4 の AC1.4.1〜AC1.4.7、US2.1 の AC2.1.8〜AC2.1.16、US3.5 の AC3.5.5）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 2節・3節・5節、`rules.md` の BR1・BR2・BR3・BR5・BR7・BR9・BR10・BR11）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C2・C5・C6）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/nfr-requirements-questions.md`（Q1〜Q4: A と「決まっていること」）
- `aidlc/spaces/default/memory/team.md`（Testing Posture の他テーブル参照の必須のテスト (a)〜(k)、Code Style の SQL の組み立て・`toString` の伏せ字・境界テスト）と `aidlc/spaces/default/memory/project.md`（Forbidden・Mandated）
- コード（読み取りだけで確かめた）: `backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataWebConfig.java`（読み取りの道の本文の上限 `MAX_BODY_BYTES` = 65,536 バイト）、`businessdata/domain/SearchRequestValidator.java`（検索の値の上限 1,000 文字）、`businessdata/domain/PageRequest.java`（`MAX_PAGE` = 1,000,000）

## ID の振り方

- この単位の枝番は、どの上流の NFR でも `.1` から振り、`.20` までに収める（U2 は `.21`〜、U3 は `.41`〜、U4 は `.61`〜。「決まっていること」）。上流の要件 NFR1〜NFR9 は枝番を持たないため、番号の意味の重なりは無い。
- 上流に当たる NFR が無い監視の要件は、意味の近い NFR に寄せた: ログの出し方は NFR1（漏えい）、p95 の区画は NFR2（性能）、プールの待ちの区画は NFR3（接続）。要件 NFR8（運用）はコンテナの入れ替えと JWT の伏せ字の要件で、この単位には当たらない。

| 上流の ID | この単位での扱い | この単位の ID | 置いた文書 |
|---|---|---|---|
| NFR1 セキュリティ | 当たる | NFR1.1〜NFR1.14 / NFR1.15 | security / observability |
| NFR2 性能 | 当たる | NFR2.1〜NFR2.5 / NFR2.6〜NFR2.9 / NFR2.10 | performance / scalability / observability |
| NFR3 接続 | 当たる | NFR3.1〜NFR3.3 / NFR3.4〜NFR3.7 / NFR3.8 | scalability / reliability / observability |
| NFR4 アクセシビリティ | 当たらない（画面は U3） | — | — |
| NFR5 テスト | 当たる | NFR5.1・NFR5.2 / NFR5.3 | security / performance |
| NFR6 多言語 | 部分（code の決まりだけ。文言は U3） | NFR6.1 | reliability |
| NFR7 互換性 | 当たる | NFR7.1〜NFR7.3 | reliability |
| NFR8 運用 | 当たらない（コンテナと JWT は U4・U5 の受け持ち） | — | — |
| NFR9 供給網 | 当たる（新しい依存を足さない） | NFR9.1 | tech-stack-decisions |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.1 | 参照先を読む2本の API（定義の `purpose` が `CREATE`・`UPDATE` の選択肢と、`POST /api/tables/reference/rows` の検索とラベルの1件読み）は、作業ロールの権限の写しで要求ごとにサーバー側で判定する（`team.md` の (a)、FR1.4、BR3.1・BR3.4）。参照元の権限だけを持ち参照先の権限を欠く利用者には、選択肢・ピッカーの情報・ラベルを返さない（`NO_PERMISSION`、BR3.1・BR3.5）。参照先の主権限が NONE のカラムは応答に含めない | 検索 API の 401・403・200 を、パラメーターを使う表の結合テストで確かめる。403 の利用者は「要る権限だけを欠く利用者」を、rules.md BR3.1 の欠ける条件ごとに1人ずつにする: 参照元のカラムが FULL でない・参照先の表が読めない・`valueColumn` が NONE・ラベルに使うカラムが NONE・`lookupList` のカラムが NONE・使える検索の条件が残らない（`searchFields` のカラムがすべて NONE）。あわせて、権限に依らず参照を提供しない形（`valueColumn` がバイナリの型・password）も、同じ表のテストで 403 になることを確かめる。停止中の利用者は権限があっても 401。作業ロールの切り替えの直後・割り当ての変更の直後の次の要求で結果が切り替わる（AC2.1.10・AC2.1.12）。定義の API では、同じ利用者で項目の状態が `NO_PERMISSION` になり要求は 200 のままであること | Code Generation |
| NFR1.2 | 権限が無い・参照元の名前が DSL に無い・参照のカラムでない・REFERENCE に検索・条件のカラムが `searchFields` に無い・ラベルの値を型として読めない・ラベルの行が無い、の各場合は、同じ 403 `ACCESS_DENIED`（同じ本文・`fieldErrors` なし）で返し、存在や権限を推測させない（AC2.1.11、BR1.3・BR6.4・BR10.4）。本文の形の誤りは名前に依らない 400 を 403 より前に返す（BR1.2） | 結合テストで各場合の状態コード・`code`・本文の構造とメッセージが同一であることを比べる。判定の順（401 → 400 形 → 403 → 400 値の型 → 対象DB → 403 行なし）を表のテストで確かめる | Code Generation |
| NFR1.3 | 参照先のスキーマ・テーブル・`valueColumn`・ラベルのカラム・検索と一覧の列は、要求から受け取らず、読み出しのたびに同じ写しの DSL から引き、DSL の名前と照らしてから引用する（`team.md` の (b)、AC1.4.1、BR2.1〜BR2.3）。値はバインド変数で渡す（`project.md` の Forbidden） | 名前を差し替えた要求（DSL に無い表・カラム、別の参照先のカラム）が NFR1.2 の 403 になり、参照先の読み取りが行われないこと（SQL が流れた数 0）を結合テストで確かめる。識別子と値に `'`・`"`・`;`・`--`・バッククォート・改行を含めても SQL として解釈されないこと | Code Generation |
| NFR1.4 | 要求の大きさと入力値に上限を置く: 新しい道の本文の上限は、既存の読み取りの道と同じ 65,536 バイト（`RequestBodyLimitRoute` に道を1つ足す。超えたら既存の `PAYLOAD_TOO_LARGE`）。検索とラベルの値は 1,000 文字まで、ページは 1〜1,000,000（BR1.4、FR2.6、機能設計の D1）。読む行の数の上限（`team.md` の (c)）は `scalability-requirements.md` の NFR2.6、読み取り側の `valueColumn` の値の長さの上限（10,001 文字に切って読み、10,000 文字を超える行を外す。F1: A）は同じ文書の NFR2.8 | 上限ちょうどは受け付け、上限＋1 は拒否する境界の結合テスト（本文 65,536・65,537 バイト、値 1,000・1,001 文字、ページ 0・1・1,000,000・1,000,001） | Code Generation |
| NFR1.5 | 参照先の行の値・ラベル・セル・主キーの値と、対象DB の接続情報を、アプリのログ・トレースの属性・エラー応答に含めない（`team.md` の (d)、`project.md` の Forbidden、AC1.4.2・AC1.4.6、BR10.1〜BR10.3）。値を持つ新しい型は `toString` で値を伏せ、行を汎用の `Map` のまま層をまたいで渡さない。値は本文で運び、道（パス・クエリ）に載せない | TRACE を有効にした `XxxSecretLeakIT`（例: `ReferenceSecretLeakIT`）で、定義の選択肢・検索・ラベル・保存前の確かめの口（`check`・`countState` を直接呼ぶ）の4経路を流し、架空の目印の値がログに出ないことを確かめる。外部へ送る設定を有効にした結合テストで、トレースの道の属性に値が残らないことを確かめる（AC2.1.15）。新しい型の `toString` の単体テスト | Code Generation |
| NFR1.6 | JDBC の例外のメッセージは、応答にもアプリのログにも出さない。既存の `RowQueryFailure`（メッセージと原因を捨てた形）だけを上へ渡す（AC1.4.3、BR10.2、`project.md` の Forbidden） | 応答しない対象DB（テストの中で開いた `ServerSocket`）と、型の食い違いで失敗させ、応答とログに例外のメッセージの目印が出ないことを確かめる | Code Generation |
| NFR1.7 | ピッカーの部分一致（`CONTAINS`）の値の `%`・`_`・エスケープの文字は、一致の条件として働かない（`team.md` の (e)、FR2.6、BR5.5）。既存の部分一致の書き方を使い回す | 3種類の DB の結合テストで、`%`・`_`・`\` を含む値がその文字どおりにだけ一致すること（AC2.1.7） | Code Generation |
| NFR1.8 | ラベルと値は信頼できない入力として扱い、サーバーは加工せず JSON の文字列としてだけ返す（`team.md` の (f)）。HTML として描かない・リンクにしないことの確かめは画面の受け持ち（U3） | `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含む参照先の値とラベルが、応答の JSON に同じ文字列のまま入ることを結合テストで確かめる（部分。画面の側は U3） | Code Generation |
| NFR1.9 | U2 が呼ぶ保存前の確かめの口 `check` は、参照でないカラム・`NO_PERMISSION`・型として読めない値・厳密に一致する行が無い場合・一致する行の値が 10,000 文字を超える場合（`scalability-requirements.md` の NFR2.8）をすべて `NOT_FOUND_OR_DENIED` の1つで返し、存在と権限を区別させない（`team.md` の (g) の U1 の分、BR9.2〜BR9.4）。保存の拒否と 409 への写しは U2 | `check` を直接呼ぶ結合テストで、各場合が同じ結果になり、型として読めない値・`NO_PERMISSION` では問い合わせが流れないこと | Code Generation |
| NFR1.10 | 参照先が複合の主キー・主キーの無いテーブル・ビュー・自己参照・`valueColumn` が一意でないテーブルのとき、REFERENCE と LOOKUP の両方で 500 にせず、決めた動作（形では拒否せず読む。同じ値は最初の1つ。ラベルは並びの最初の行。BR2.4・BR4.4・BR6.3・BR9.4）に固定する（`team.md` の (h)、AC1.4.4） | 形ごとに決めた動作を明示した結合テスト（3種類の DB）。行の値がログに出ないことは NFR1.5 の中で併せて確かめる | Code Generation |
| NFR1.11 | 選択肢・検索・ラベル・`check` を MySQL・MariaDB・PostgreSQL のすべてで確かめる（`team.md` の (i)、AC2.1.16、AC1.4.5）。識別子の大文字と小文字・引用符を含む名前・NULL の並び・境界の値（`BIGINT` の最大値・桁の多い `DECIMAL`）、参照元と参照先の型の違い（`INT` と `BIGINT`、数字の文字列と整数）、大文字と小文字だけ・末尾の空白だけが違う値、`1.0` と `1` の小数を含め、DB ごとに結果が割れない（BR7.1・BR7.6・BR9.4） | Testcontainers の版を固定したイメージで、3種類すべてを `./gradlew verify` の中で流す（H2 やモックで代用しない。`project.md` の Mandated）。並びを確かめる値は半角英数字にする | Code Generation |
| NFR1.12 | 既存の境界テスト（`BusinessDataBoundaryArchitectureTest`・`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest` など）と `backend/config/spotbugs-exclude.xml` を緩めない。`prepareStatement`・`createStatement` を呼んでよい場所を増やさず、行の SQL の新しいメソッドを作らない（`team.md` の (k)、AC1.4.7、BR11.2・BR11.3）。新しいクラス（`ReferenceLookup` など）は、コード生成の計画に明記して依頼者の承認を得る | 差分が足す方向だけであることを、コード生成の計画とレビューで確かめる。境界テストと SpotBugs の関門（`SQL_` は priority にかかわらず止める）が `verify` で通ること | Code Generation |
| NFR1.13 | 新しい API は「ログインだけ」（`@ApiAccess(AUTHENTICATED)`）に明示して分類し、権限は業務処理の層で判定する（BR11.1、AC2.1.14） | 既存の API の分類の網羅のテスト（`ApiAccessArchitectureTest`）が、分類を書き忘れると落ちる形のまま通ること | Code Generation |
| NFR1.14 | 判定の純粋な関数（`accessOf`・同じ値を落とす関数・使える条件を選ぶ関数・固定の並びを作る関数・`sameReferenceValue`）に、jqwik の性質ベースのテストを当てる（`team.md` の性質ベースのテスト）。性質: 権限を足しても `AVAILABLE` が `NO_PERMISSION` に変わらない、NONE の列が条件と一覧に出ない、出力の値に重なりが無い、大文字と小文字・末尾の空白の違いは一致しない、`1.0` と `1` は一致する | jqwik のテストで、失敗時の乱数の種を記録する | Code Generation |
| NFR5.1 | 行カバレッジ 80% 以上・分岐カバレッジ 70% 以上を、全体と、この単位が足す・変えるパッケージごとに満たす（`team.md`、AC3.5.5）。`businessdata` の各パッケージは前の Intent で作った新しいパッケージのため、パッケージごとの下限の対象 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する（`project.md`） | Code Generation、Build and Test |
| NFR5.2 | この単位が手を入れる見込みのパッケージ（`businessdata.*`・`targetdb.domain`・`targetdb.config`）は、`packagesJudgedByTotal` の一覧（7 パッケージ）に入っていない。計画で実際に手を入れるパッケージが一覧のものに当たったときは、テストを足して下限を満たし、一覧から外す（`team.md`） | コード生成の計画で手を入れるパッケージを洗い出し、一覧と突き合わせる。当たったパッケージの今の値を実測して見積もる | Code Generation |

## 後の段に回すこと

- **NFR 設計**: 値を伏せる型の置き場（web の層の受け取りの時点から伏せる型で受ける。`project.md` の学び）、`ReferenceSecretLeakIT` などの試験の形。
- **Code Generation**: 上の要件のテストと、新しいクラスの承認。
- **U2**: `check` の結果の 409 と監査への写し、保存の拒否（`team.md` の (g) の残り）。
- **U3**: 画面でラベルと値を文字として出すこと（(f) の画面の側）、権限を欠く項目の入力不可の表示。
