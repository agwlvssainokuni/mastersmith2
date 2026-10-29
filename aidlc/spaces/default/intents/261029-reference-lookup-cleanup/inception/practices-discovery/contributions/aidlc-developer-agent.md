**Collaborator:** aidlc-developer-agent

## Contribution

観点: 命名、層の境界、エラー処理、ファイルの置き場、コードスタイル。根拠は `backend/src/test/java/cherry/mastersmith/businessdata/BusinessDataBoundaryArchitectureTest.java`、`targetdb/TargetDbBoundaryArchitectureTest.java`、`role/RoleBoundaryArchitectureTest.java` を読み取りで確かめた結果。

### 境界を緩めずに参照先の読み取りを足せるか（結論: 足せる。ただし置き場を決めておく必要がある）

- `businessdata` の許可する依存は、すでに `targetdb.service`・`targetdb.domain`・`role.service`・`role.domain`・`dsl.domain`・`dsl.service`・`common` と標準・Spring・SLF4J まで。参照先の読み取りは対象DB の読み取りの口（`targetdb.service`）と権限の解決（`role.service`）と DSL の参照先の定義（`dsl.domain`）しか要らないため、`allowedDependencies` を広げずに収まる。`RoleBoundaryArchitectureTest` の「`role` に依存してよい相手」も増やさずに済む。
- JDBC の文を作る・実行してよいクラスは `ALLOWED_STATEMENT_OWNERS`（`RowSqlExecutor` ほか）の一覧で固定されている。参照先の SQL を新しいクラスに置くとこの一覧の変更（と SpotBugs の `SQL_` の除外の追加）が要り、team.md の「除外を足す・広げるときはコード生成の計画に明記して承認」に当たる。緩めない道は、参照先の SQL を既存の `RowSqlExecutor` と DB の種類ごとの `RowDialect`（`MysqlFamilyRowDialect`・`PostgresRowDialect`）に新しいメソッドとして足すこと。これなら一覧も除外も変わらない。
- 参照先は読み取りだけ。書き込みの接続・`@Transactional`・`targetdb` の「書き込みなし」の境界テストには触れない。読み取りの目的（`ReadPurpose`・`TargetDbProperties.QueryTimeout`）に「参照の選択肢」の目的を足す形が、team.md の「目的ごとの時間の上限」の決まりに沿う。
- 層の置き場: 参照先の識別子の照合（`table`・`valueColumn`・`labelColumn` を適用中の DSL の名前と照らす）は `businessdata.domain` の既存の `KnownIdentifiers` の系に寄せる。参照先の権限判定は既存の `TableAccessPolicy`・`ColumnAccess` を再利用し、参照先でも「読めない（主権限 NONE）カラムを応答に含めない」を同じ関数で当てる。controller は DTO の変換と状態コードだけ、業務の失敗は sealed interface の結果の型で返し `switch` で `BusinessException` に直す（新しい機能の決まり。`businessdata` は今この形）。
- エラーの code: `BusinessDataProblemTypes`・`BusinessDataProblemTypeCatalog` に足す。403 は共通の `ACCESS_DENIED` を使い回す。参照先が DSL に無い・権限が無いときの状態コードと code を、既存の「403 と 404 の使い分け」の決めた側にそろえる（新しい差を作らない）。
- 値の型: 参照の選択肢・ピッカーの行（値とラベル）を持つ型は `toString` を伏せ、`Map` のまま層をまたがない。BIGINT・DECIMAL は文字列、ラベルの長さには上限（値は設計）。
- 暫定の解消: `ColumnWriteRulesFactory` が `lookup` を `text` に落としている箇所（K-74）と、定義の応答 `OptionResponse` に参照先の欄が無い点が、変更の入口。作成・更新の本文の検証（参照先の存在確認をするか）は要件の論点で、実践ではなく要件へ回す。

### フロントエンド

- ピッカーは `features/tables` の中で使い、ほかの機能でも使うなら `src/shared/` に置く（機能どうしの import は ESLint で止まる）。値は `any` を使わず `unknown`/union、ラベルは文字として描く（`react/no-danger` が守る）。make-you-chic-ui に検索付きの選択の部品が無いときは、team.md の決まり（設計の段で足りない点を一覧にして上流へ先に依頼、`vendor/` は直接変更しない）を使う。依頼は Refined Mockups で出すのが決まりなので、この Intent の流れに Refined Mockups が有るかを先に確かめる必要がある。

### 初稿の案への見立て

- 案 (a)〜(f)（team.md の Testing Posture の参照のテスト）は妥当。足りない点を追加する: (g) 参照先のテーブルの主キーなし・ビューでも読み取りが成り立つこと（行を1件に決めない読み取りなので許す側か、設計で決めて明示のテストにする）、(h) 参照先の識別子が3種類の DB で引用される（大文字小文字・予約語）こと、(i) 参照の読み取りの接続の数（`*ConnectionUsageIT` の形）、(j) 複合の外部キー・参照先が同じテーブル（自己参照）の扱い、(k) `BusinessDataBoundaryArchitectureTest` の許可の一覧と `ALLOWED_STATEMENT_OWNERS` が変わらないことそのもの（変えたら落ちる）。
- 案（SQL の部品は1つに閉じる）は妥当で、置き場を `RowSqlExecutor`/`RowDialect` に固定する書き方に具体化した方が、承認の要らない道がはっきりする。

### その他の項目の見立て

- Dependabot #81・#82: 初稿の案どおり既存の決まりで足りる。同じ系列の i18next と react-i18next は同時に更新し、lockfile で1つの版にそろえる。i18next の翻訳の型・初期化のコードに触れない更新なら、画面のテストと型の検査（verify）で足りる。
- JWT の自動の伏せ字: 置き場は後処理スクリプト（Q5 の A）が妥当。台本の `handleSummary` で伏せる案は、台本が 2602 行で `--summary-export` と `handleSummary` の出力が別の経路のため、伏せ漏れが出やすい。伏せ字の関数は純粋にして、`eyJ…` の形の正規表現を単体で確かめる（Bash/Node のスクリプトならテスト用の入力を付ける）。ファイルの置き場は `perf/` の下、ライセンスヘッダーの検査の対象かを確かめる。
- コンテナの版そろえ: Q6 の A は妥当だが「決まり」の置き場は project.md の Deployment の学び（既に `docker inspect` の確かめの記述あり）と重なる。新しい固い制約にせず、Deployment の手順書へ寄せる。
- perf 文書の整理: Q7 の B（設計で決める）に賛成。実践に足すなら「同じ値を複数の文書に書き写さない」程度に留める。

### インタビューで足すべき抜け

- 参照先の読み取りを、既存の `RowSqlExecutor`/`RowDialect` に足す（新しい JDBC の文のクラスを作らない）形を依頼者が望むか。新しいクラスが望ましいなら、ArchUnit の許可の変更と SpotBugs の除外の追加を伴うため、計画の承認が要ることを先に伝える。
- 参照先のテーブルを「読める」権限を、作成・更新の画面を開く権限とは別に当てるか（要件の論点だが、実践の上では 403 を確かめる利用者の作り方に影響）。
- パッケージごとのカバレッジの下限: `businessdata.*` は新しいパッケージ側（一覧の外）なので、足した分にもテストが要る。`packagesJudgedByTotal` の7パッケージのどれかに手を入れる見込み（`common.web`・`common.error.*` など）が有れば、Delivery Planning で実測する。
- E2E の1本が「参照ピッカーで選んで保存」を代表にする場合、見本の対象DB に参照先のテーブルが有るか（架空の値だけ）を確かめ、無ければ見本の初期化に足す。

## Positions

- AGREE: 新しい固い制約（ALWAYS/NEVER）を足さない見込み。既存の Mandated・Forbidden（SQL の組み立て、行の値を持つ型の伏せ字、NONE カラムを返さない）が参照にもそのまま当たる。
- AGREE: Q4（Dependabot は手元で取り込み PR を閉じる）。既存の決まりのとおりで、E2E は Build and Test で1回。
- AGREE: Q5 の A（後処理スクリプト＋伏せ字のテスト＋ `gitleaks dir`）。台本の出力で伏せる案より伏せ漏れの経路が少ない。
- AGREE: Q7 の B。基準の持ち主の整理は設計の仕事で、実践に足すほどの安定した決まりではない。
- OBJECT: 初稿の案 (a)〜(f) だけでは足りない。主キーなし・ビューの参照先、3種類の DB の識別子の引用、接続の数、自己参照・複合キー、境界テストの許可の一覧が変わらないこと、を (g)〜(k) として足したい。
- OBJECT: 「参照先の SQL の口が既存の部品の外に要るなら承認を得る」という条件づきの書き方。先に `RowSqlExecutor`/`RowDialect` に足す形を既定にし、新しいクラスを作るときだけ承認、と順序をはっきりさせたい（緩めない道が既定になる）。
- OBJECT: Q6 の A（Deployment に「毎回確かめる決まりを足す」）。project.md の Deployment に同種の学びが既にあるため、重ねて決まりを足さず手順書の項目にする方がよい。
