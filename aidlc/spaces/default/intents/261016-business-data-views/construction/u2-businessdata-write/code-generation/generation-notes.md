# 生成の記録 — U2 u2-businessdata-write（Bolt B4）

承認済みの計画（`code-generation-plan.md`）を書き換えず、計画に無い変更・計画と違う判断・依頼者に確かめたいことをここに記録する（`project.md` の学び）。計画のチェックボックスの印のほかは、計画を変えていない。

## 1. 1回目の依頼（Step 1〜16）

### 1.1 進め方の差

| # | 計画 | 実際 | 理由 |
|---|---|---|---|
| N1 | Step 1「`aidlc/` の下の未コミットの変更を確かめ、依頼者にコミットの承認を得てから進む」 | 指揮役がコミット c4f3d99 で済ませた後の始めの時点で、`aidlc/spaces/default/intents/261016-business-data-views/audit/sakura-local-4e42a93f87ce.md`（監査ログ）に未コミットの変更があった。指揮役とフックが書く記録で、生成の担当はコミットしない決まりのため、触れずに進めた | 統合の前にコミットする（計画の Step 33） |
| N2 | Step 2「`TargetDbSettings`: 読み取りが使えるうえで、書き込みのユーザー名とパスワードの両方があるときだけ『書き込みが使える』を返す口」 | 書き込みの点検を、別の sealed interface `TargetDbWriteSettings`（`NotConfigured`・`Incomplete`・`Usable`）と静的な `inspect` にした。Bean は `TargetDataSourceConfig#targetDbWriteSettings`（片方だけの WARN をここで1回出す）、作る条件は `TargetDbWriteUsableCondition` | 読み取りの `TargetDbSettings` の3つの場合（使わない・不正・使える）と、書き込みの場合を混ぜないため。読み取りの点検の結果と WARN は変えない |
| N3 | Step 3 の単体「`TargetDbSettingsTest`（書き込みの両方・片方・無し）」 | 新しいクラス `TargetDbWriteSettingsTest`（7件）に置いた。`TargetDbSettingsTest` は引数の数の直しだけ | N2 の型に合わせた |
| N4 | 7節の C1 に「`README.md` の環境変数の表の4行」 | この依頼（Step 1〜16）では README を変えていない。Step 28（文書）でまとめて直す | 依頼の範囲が Step 16 まで。C1 の区切りの中身は、提案のときに理由を添えて示す |
| N5 | Step 2「`QueryTimeout.write`（既定 5 秒、秒の単位で 1 秒以上）」 | 書き込みの上限が 1 秒未満、書き込みのプールの上限が 1 未満のときは、書き込みだけを「欠け・不正」（`Incomplete`）にし、項目の名前（`mastersmith.target-db.query-timeout.write`・`mastersmith.target-db.write.pool.maximum-size`）を WARN に出す。読み取りの点検（`TargetDbSettings.Invalid`）には入れない | 書き込みの設定の誤りで読み取りを止めない（BR9.1「どちらの場合も起動を続け、読み取りは動く」） |
| N6 | 既存の `TargetDbStartupIT` は対象DB の7つの項目を起動の引数で明示する | 起動の引数に `write.username`・`write.password` も（空の値で）足した | 開発者の環境変数に書き込みの設定があっても影響しないため |

### 1.2 計画の部品の一覧（1.1節）に無い型（足したもの）

| 置き場 | 型 | 理由 |
|---|---|---|
| `targetdb.config` | `TargetDbWriteSettings`・`TargetDbWriteUsableCondition` | N2 |
| `targetdb.config` | `TargetDbProperties.Write`・`TargetDbProperties.WritePool` | 書き込みの資格情報とプールの上限の設定の型（`mastersmith.target-db.write.*`） |
| `targetdb.domain` | `TargetWriteFailure`・`SavepointMarker` | 口が受け持つ段の失敗（段・SQLState・業者の番号・クラスの名前・時間切れか断絶か）と、`TargetWriteSession` のセーブポイントの操作の口 |
| `targetdb.domain` | `TargetWriteStage` の値 | 計画の4つ（借りる・確定・セーブポイントへの戻し・巻き戻し）に、処理の想定外の例外（`WORK`）と確かめの処理の想定外の例外（`INSPECT`）を足した |
| `businessdata.domain` | `WriteRejection`・`WriteValidation`・`WriteOrigin`・`ForeignKeyRef`・`BusinessDataAuditResult` | 書き込みの拒否（web の層が `switch` で場合を尽くす）、確かめの結果、送り手の情報（businessdata は user の `RequestOrigin` に依存できないため同じ形を持つ）、違反の後の確かめに使う外部キー、監査の結果（SUCCESS・FAILURE・UNKNOWN） |

### 1.3 計画との差（型の形）

| # | 計画 | 実際 | 理由 |
|---|---|---|---|
| N7 | `WriteStep` の `RollbackToSavepointThenInspect(inspect, onInspectFailure(RowWriteStage))` | `onFailure` の引数を `targetdb.domain` の `TargetWriteFailure` にした | `targetdb` は `businessdata` に依存できない（境界）。D4 の分け方（時間切れ・断絶か）に要る印 `interrupted` を口が付ける |
| N8 | `TargetWriteOutcome.Failed(stage, sqlState, vendorCode, exceptionClass)` | `Failed(TargetWriteFailure)`（同じ項目に `interrupted` を足した1つの型） | N7 と同じ型を使い回した |
| N9 | 処理の答えが「巻き戻し」で、巻き戻しが失敗したとき | 口は処理の結果のまま `Done` を返す（接続は捨てる） | reliability-design.md 2節の表「巻き戻し（件数が 1 でないなどで巻き戻しを答えた後）・どんな失敗も・元の答えのまま」 |
| N10 | 監査の理由の値への写しを「応答の code から」 | 業務処理の層の拒否の列挙 `WriteRejection` から `BusinessDataAuditReason.of` で写す（`switch` で場合を尽くす1か所） | 応答の code（`ProblemType`）は web の層の型で、domain の層の写しの元にできないため。応答の code と拒否は1対1（web の層の `switch`） |
| N11 | `ColumnValidation`・`TableAccessPolicy` に `ColumnWriteRules` から写して入れる | `TableAccessPolicy` が DSL のカラムと `ColumnKind` から `ColumnWriteRulesFactory` で材料を作り、`ColumnAccess.validation` と `TableAccessView.writeRules`（足した項目）の両方に使う。`TableAccessView` に `foreignKeys` も足した。`TableAccessInput` に参照先のカラムの名前（`referencedColumns`）を足し、前の形の作り方も残した | 権限の写しは要求1件で1回だけ取るため、書き込みの業務処理が要る材料（pattern・unique・外部キー）を判定の材料に載せて渡す。定義と検証が同じ材料から作られる（BR14.2） |
| N12 | `BusinessDataFieldErrorReason` に U2 の理由を足す | 足した。U1 の `SearchRequestValidatorTest#keyNotCheckedWithoutDetail` が値の数（5）を確かめていたため、読み取りの5つの値がそのままであることを確かめる形に直した（計画 1.3 の一覧に無いテストの直し） | 計画の Step 7 が理由を足すと決めている。U1 の NFR4.5（読み取りの値を増やさない）の確かめは保った |
| N13 | `ColumnKind`（U1） | 秒の端数の桁の上限 `fractionDigits` を足した（前の作り方も残した） | 秒の端数の検証（NFR 設計 Q6: A）は DB の種類と型の精度で決まり、`TableAccessPolicy` は DB の種類を持たないため、型と一緒に渡す |

### 1.4 計画・設計で決まっていなかった点の判断（依頼者に確かめたいこと）

| # | 論点 | 判断 | 確かめたいこと |
|---|---|---|---|
| G1 | 秒の端数の「DB の型の精度」が DSL に無い（既定の DSL の生成は時刻の型の精度を読まない） | 精度は、型の名前の `(n)` → DSL の `dbType.precision` → DB の種類の既定（PostgreSQL 6、MySQL・MariaDB 0）の順に決める（`ColumnValueCodec.kind`。Step 9）。DB の種類が分からないときは 0（狭める向き） | MySQL・MariaDB の `datetime(6)` などの端数を持つカラムは、DSL の型の名前か精度に書き足さないと端数を受け付けない。この読み方でよいか |
| G2 | BR3.6 は `min`・`max` を日付・日時・時刻の型にも当てるが、DSL の `min`・`max` は数の値しか持たない | 日付・日時・時刻の型には `min`・`max` を当てず、定義の項目も `null` にした（数の型だけ） | — |
| G3 | 締め切りの包みで照合しても、入れ子の深い式でスタックが尽きる（`StackOverflowError`）ことがある | `PATTERN_TIMEOUT` として扱う。組み立てられない式（`PatternSyntaxException`）は `PATTERN_MISMATCH`（閉じる向き） | — |
| G4 | 文字の型の `minLength` を空の文字に当てるか | 当てない（pattern と同じ。必須は BR3.3 で見る） | — |
| G5 | BR6.4 は、違反の後の確かめの失敗に「field を空にした FieldViolation だけを付け」るとするが、理由の値が決まっていない | 409 `BUSINESS_SAVE_REJECTED` のうち、確かめの失敗・CHECK などの拒否・形の誤りは `fieldErrors` を付けない（カラムを示さない誤りとして返す）。`fieldErrors` を付けるのは NOT NULL（下の G6）だけ | 確かめの失敗にも `{field: "", reason: …}` を1件付けるか。付けるなら理由の値（`DUPLICATE`・`REFERENCE_NOT_FOUND` のどちらか、または別の値） |
| G6 | NOT NULL の違反のカラムを、例外の文を読まずにどう決めるか | 検証が入力できるカラムの NULL を通さないため、作成の違反は入力できないカラムが原因として `{field: "", reason: NOT_EDITABLE_NOT_NULL}`（入力できないカラムの名前は示さない。NONE のカラムの名前を漏らさないため）。更新は NULL を送ったカラムがちょうど1つならそのカラムの `NOT_NULL`、ほかは `field` を空にした `NOT_NULL` | — |
| G7 | 違反の後の外部キーの確かめで、複合の外部キーと一部だけ送った外部キーの扱い | 外部キーのカラムをすべて送り、どれも NULL でない外部キーだけを確かめる（一部だけ送った外部キーは候補にしない）。候補が1つなら、その外部キーの先頭のカラムを `field` にする | — |
| G8 | MySQL・MariaDB は自動の採番の値1つしか返さない | 送らなかった主キーのカラムが2つ以上のときは、作った行の主キーを返さない（応答の `key` を出さず、監査の detail も空。失敗にはしない） | — |
| G9 | ⑥ の「主キーとビューの決まり」の判定 | 作成はビューのとき、更新・削除はビュー・DETAIL が許されない（主キーが無い・主キーのカラムに NONE・主キーの型が行を指せない）とき。⑥ の監査は NFR4.14 のとおり（作成は CREATE、削除は DELETE、更新は FULL のカラムを持つ人だけ） | — |
| G10 | 確定の段の競合（PostgreSQL の確定の時の 40001 など） | D3 のとおり、確定の段の失敗は類 23 のほかはすべて `TARGET_DB_TIMEOUT`（監査 `UNKNOWN`）にした（競合の D1 は文の段だけに当てる） | — |
| G11 | セーブポイントを置く処理が失敗したとき | 書き込みの文を送る前として、事前の問い合わせの段（`PRECHECK`）で分ける（時間切れ・断絶は `UNAVAILABLE`、ほかは想定外） | — |
| G12 | 書き込みの要求に定義の識別（`definitionId`）が無いとき | 読み取りでは「今と同じ」とみなすが、書き込みでは識別を必須（entities.md の WriteRequest）とし、無いときは 409 `BUSINESS_DEFINITION_CHANGED` にした | — |

### 1.5 計画との差（テストと進め方）

| # | 計画 | 実際 | 理由 |
|---|---|---|---|
| N14 | Step 10「`RowSqlExecutorTest`（書き込みのメソッドの結果の型…）」 | 書き込みのメソッドのテストを新しいクラス `RowSqlExecutorWriteTest`（12件）に置いた。`RowSqlExecutorTest` は D5 の直しだけ。書き込みの分類の表は `RowSqlFailureClassifierTest` に足した | 読み取りのテストの準備（モックの答え）と、書き込みの準備（セーブポイント・生成された鍵）が違うため |
| N15 | 3節の A2（`onlyRowSqlExecutorCreatesStatements` の一覧を広げる）は7節の C4 | Step 12（監査の境界テストを流す時点）で入れた。書き込みのメソッドが入った時点で、今の一覧の確かめが落ちるため | コミットの区切りでは、A2 を書き込みのメソッドと同じ C2 に入れる必要がある（C2 の時点で境界テストを通すため）。A1 は C3 のまま |
| N16 | U1 の `TableAccessResolver` は書き込みの設定を常に偽で渡していた | `TargetWriteAccess` を受け、`product()` の有無を `writeConfigured` に渡す。外部キーの参照先の名前も渡す。U1 の `TableAccessResolverTest` の `writeConfigured` の確かめを、偽の書き込みの口に設定がある前提の真に直し、設定が無い場合のテストを足した | 計画の Step 13「`TableAccessResolver`（写しの DSL から `ColumnWriteRules` を作る材料を渡す）」の続き。BR1.7 |
| N17 | Step 15「`ColumnDefinitionResponse`: `NON_NULL` で省かない」 | `required`・`nullable`・`hasPattern` を真偽値（null にならない型）に、`choices` を空の一覧にした。ほかの検証の項目は null のまま出す | U3 は `required`・`nullable` が欠けたら形の崩れとみなす（承認の場の決定） |
| N18 | — | `AuditEvent` の文字列に `targetSchema`・`targetTable`・`workRoleId` を足した | 写しの DSL の名前と ID で、行の値・接続先を含まない。detail は今までどおり長さだけ |

### 1.6 テストの結果（この依頼の終わりの時点。2026-10-20）

| 層 | コマンドの範囲 | 結果 |
|---|---|---|
| 走らせ方の確かめ（Step 1） | `targetdb.config.*`・`businessdata.domain.*`・`AuditEventTest` | 117 件 成功 |
| 設定の層（Step 3、単体） | `targetdb.config.*`・`TargetSchemaReaderTest`・`JdbcTargetReadAccessTest` | 82 件 成功 |
| 設定の層（Step 3、結合） | `TargetWriteTransactionManagerInjectionIT`（先に通した）→ `TargetDbStartupIT`・`TargetDbSecretLeakIT` | 4 件 → 8 件 成功 |
| 書き込みの口（Step 6） | `TargetWriteSessionTest`・`TargetWriteOutcomeTest`・`WriteStepTest`・`JdbcTargetWriteAccessTest`・`TargetDbBoundaryArchitectureTest` | 25 件 成功 |
| domain の層（Step 8） | `businessdata.domain.*`（性質ベースを含む） | 117 件 成功（U2 で足したもの 49 件） |
| repository の層（Step 10） | `businessdata.repository.*` と `spotbugsMain`・`spotbugsGate` | 153 件 成功。SpotBugs の `SQL_` の指摘は A3 の見込みどおりの5つのメソッド・2つのパターンで、除外を広げて 0 件 |
| 監査（Step 12、単体） | `audit.*`・`BusinessDataBoundaryArchitectureTest` | 177 件 成功 |
| 監査（Step 12、結合） | `AuditSchemaIT`・`AuditMigrationCompatibilityIT`・`AuditSecretLeakIT`・`AuditWriteFailureIT`・`GroupAuditWriteFailureIT`・`UserAdminAuditWriteFailureIT` | 35 件 成功 |
| service の層（Step 14） | `businessdata.service.*` | 50 件 成功（`BusinessDataWriteServiceTest` 15 件） |
| web の層（Step 16、単体） | `businessdata.web.*`・`ApiAccessArchitectureTest`・`BusinessDataBoundaryArchitectureTest` | 48 件 成功 |
| web の層（Step 16、結合） | `ApiAccessConsistencyIT`・`ProblemTypeDuplicateStartupIT` | 8 件 成功 |
| 単体テストの全体（確かめ） | `./gradlew :backend:test` | 2,582 件 成功 |
| U1 の読み取りの結合テスト（D5 の早めの確かめ） | `Postgres`・`Mysql`・`MariadbBusinessDataReadIT` | 45 件（DB の種類ごとの飛ばし 4 件を含む）成功。MySQL・MariaDB の時差の確かめは、接続を作るときの初期化の文で通った |

### 1.7 依頼者の答え（1回目の依頼の後）

| # | 答え | 扱い |
|---|---|---|
| G1 | A（型の名前の `(n)` → DSL の精度 → DB の種類の既定の読み方でよい） | 変えない |
| G5 | A（違反の後の確かめが失敗したときは誤りを付けず、409 `BUSINESS_SAVE_REJECTED` だけを返す） | 今の形のまま。BR6.4（「field を空にした FieldViolation だけを付け」る）との差として、ここと `code-summary.md` に記録する |
| G6〜G12 | 承認の場で確かめる点として記録したまま | 変えない |

## 2. 2回目の依頼（Step 17〜31）

### 2.1 進め方の差

| # | 計画 | 実際 | 理由 |
|---|---|---|---|
| N19 | Step 17「`BusinessDataWriteTables`」と「書き込みのアカウント（テストのクラスごと）」 | `TargetDbTestDatabase` に `createWriterAccount`（PG はスキーマの USAGE・表の DML・sequence の USAGE、MySQL・MariaDB は DB の DML）を足した。`BusinessDataSampleSchema` は書き込みのアカウントを毎回作る（読み取りだけの U1 の結合テストでは使わない）。競合（D1）は、トリガー（PG の `RAISE … USING ERRCODE`、MySQL・MariaDB の `SIGNAL`）と、管理者の接続で行を排他して書き込みのアカウントの待ちの上限（PG はロールの `lock_timeout = '2s'`、MySQL・MariaDB はテストのコンテナの `innodb_lock_wait_timeout = 2`）を切らせる形の両方にした（計画の前提の形を決めた） | 計画の Assumptions の「形は生成で決め」 |
| N20 | 1回目の引き継ぎ「`BusinessDataTimeoutIT` の MySQL の通すまとまりの数を直す」 | DB の general_log と中継のまとまりの大きさで確かめ、MySQL・MariaDB とも 0 にした。1回の一覧は MySQL 9.7（Connector/J）が「`SET sql_select_limit=1`・件数・`SET sql_select_limit=20`・ページ」、MariaDB（3.5）が「件数・ページ」。D5 の後に 2・1 のままだと、MySQL は `SET sql_select_limit=20`、MariaDB はページの問い合わせで止まっていた（どちらも 503 TIMEOUT で通るため気づけなかった）。MySQL で件数の問い合わせそのものを止める 1 は、下の G15 の形で次の要求が落ちた | D5 で貸すたびの `SET time_zone` が無くなったため |
| N21 | Step 17「`StallingRelay` に確定の途中で止める形（D7）と止めた段を診断の出力に残す形」 | 止めた位置（合図の後の何番目のまとまりで、何バイトか）を返す `stallReport()` と、目印を含むまとまりを通した次で止める `armAfter(String)` を足した。PostgreSQL のドライバーは `COMMIT` を名前つきで準備して（`S_1`）文の文字を送らないため、確定は INSERT の次のまとまりで止める。MariaDB は平文の通信で、`READ COMMITTED` の文字に当たらないよう問い合わせの種類の 1 バイトを付けた `\u0003COMMIT` で止める。MySQL は TLS のため通す数で止め、1回の作成は「分離の水準の読み・READ COMMITTED・autocommit=0・SAVEPOINT・INSERT・COMMIT・autocommit=1・REPEATABLE READ」の8つの 40 バイト以上のまとまりだった（Connector/J 9.7）。書き込みの文は4つ、確定と違反の後の戻し（外部キーの違反の後の `ROLLBACK TO SAVEPOINT`）は5つを通した次で止める | 手元の確かめ（general_log と中継のまとまりの大きさ）の結果 |
| N22 | Step 21「3種類とも 25 秒以内（実測の時間を DB ごとに記録）」 | 実測（2026-10-20）: PostgreSQL は書き込みの文 10.03 秒・確定 10.01 秒・違反の後 10.03 秒、MySQL は 10.06・10.04・10.05 秒、MariaDB は 10.03・10.03・10.03 秒。どれも書き込みの通信の待ち（文の上限 5 秒 + 5 秒）で返った | NFR 設計のレビューの R-07 |
| N23 | Step 23「`BusinessDataWriteTraceExposureIT`（外部へ送る設定を有効にする）」 | U1 の `BusinessDataTraceExposureIT` と同じく、終わったスパンの属性をテストだけの集め手（`SpanProcessor`）で集めて確かめた。外へ送る部品に渡る前の属性と同じものを見る | U1 と形をそろえた（外へ送る部品の設定を足さない） |
| N24 | Step 26「`TargetDbSampleInitScriptIT`」 | パッケージ `cherry.mastersmith.targetdb` に置いた。台本はテストの作業ディレクトリから上へたどって `docker/targetdb/<種類>/` を探し、版を固定したイメージのコンテナに写す。(b) の `exec` のパスワードは `ExecConfig` の環境変数で渡し、コマンド行に出さない。空のパスワードで exit 1 になることも足した | — |
| N25 | Step 27「書き込みのアカウントへの種のスキーマの DML の付与（`bizPrepare` の中）」 | 付与は種の SQL（`make-biz-seed.sh`。読み取りのアカウントへの付与と同じ場所）に置いた。k6 の `bizPrepare` はアプリの API しか呼べないため。書き込みのロール `perf-biz-writer`（スキーマを READ、`biz_new`・`biz_ref` を FULL と CREATE・DELETE）・割り当て・作業ロール・トークンは、各書き込みの場面の setup が作る（前の場面の状態に頼らない）。DSL の適用だけは `bizPrepare` が行う（DSL のファイルは初期化の段でしか読めないため）。表の名前は `biz_new`（100 カラム）と `biz_ref`。`make-biz-dsl.mjs` のカラムに `required`・`unique`、表に外部キーを書ける口を足した | k6 で GRANT を流せない |
| N26 | Step 18「書いた後の値は、アプリとは別の接続で読み直して確かめる」 | MySQL の Connector/J の `getString` は DECIMAL を指数の形（`-1E-10`）で返すため、小数と日時は SQL で文字にして（PG は `::text`、MySQL・MariaDB は `CAST(… AS CHAR)`）読み直した | テストの読み直しの側の差（アプリの値は 1 桁も変わっていない） |
| N27 | Step 18 の「日時の範囲」 | MySQL・MariaDB の `TIMESTAMP` の範囲の外は 2107 年で確かめた。MariaDB 11.5 からの `TIMESTAMP` は 2106 年まで入り、2040 年は受け付けたため | 手元の確かめ |
| N28 | 1回目の引き継ぎ「定義の API の結合テストへの検証の材料の確かめ」 | `AbstractBusinessDataReadIT#definitionValidationItems` を足した（すべてのカラムに検証の材料の項目があり、`required`・`nullable`・`hasPattern` が真偽値。READ の利用者のため値は null）。入力できるカラムの値は `AbstractBusinessDataWriteIT#definitionForWrites` で確かめる | — |
| N29 | Step 24「`BusinessDataConnectionUsageIT` に作成・更新・削除の3行」 | 違反の後の確かめを通る作成を独立の行（`CREATE_VIOLATION`）にし、PG の4行と MySQL の2行（作成と違反の作成）を足した。アプリは書き込みのアカウントつきで起動し、利用者は FULL と CREATE・DELETE を持つ（読み取りの行の値は変わらない） | — |
| N30 | Step 19「対象DB に文が出ないこと（数える包みで文の数 0）」 | 書き込みの接続を借りた回数（`CountingDataSource#borrows`）が 0 であることで確かめた（文の数は数えない）。書き込みの接続を借りなければ文は出ない | 既存の数える包みの形 |

### 2.2 計画に無い本体の直し（結合テストで見つかった不具合）

`team.md` の Mandated（不具合を直すときは再現するテストを同じコミットに含める）のとおり、どちらも直す前に落ちたテストを直した後に通した。

| # | 見つけたテスト | 直したもの | 再現のテスト |
|---|---|---|---|
| F1 | `PostgresBusinessDataWriteIT#boundaryValues`（BIGINT の上限 + 1 が 400 でなく 409 になった） | `ColumnValueCodec` の `int8`（PostgreSQL）の範囲を符号つきの 64 ビットにした（符号なしの上限との和を当てていた）。BR3.5「整数の型は DB の型の範囲を超えたら OUT_OF_RANGE」 | `ColumnValueCodecTest#readNumbers` の3行と、`AbstractBusinessDataWriteIT#boundaryValues` |
| F2 | `MysqlBusinessDataWriteIT#databaseRejections`（形の誤りのトリガーが 500 になった） | `RowSqlFailureClassifier` の MySQL・MariaDB の形の誤りの SQLState に `22001` を足した。Connector/J はサーバーの類 22 の失敗を `MysqlDataTruncation`（SQLState 22001）にまとめるため、D2: A の `22007`・`22018` が MySQL では当たらなかった | `RowSqlFailureClassifierTest` の1行（`MYSQL, WRITE, 22001, 1644`）と、`AbstractBusinessDataWriteIT#databaseRejections` |

### 2.3 計画・設計で決まっていなかった点の判断（依頼者に確かめたいこと）

| # | 論点 | 判断 | 確かめたいこと |
|---|---|---|---|
| G13 | MySQL・MariaDB の `bigint` は、DSL の型の名前（情報スキーマの `DATA_TYPE`）から符号なしかが分からない | サーバーの検証は U1 と同じく符号つきの下限と符号なしの上限の和のままにした。符号つきのカラムに 2^63 を送ると、DB が拒んで 409 `BUSINESS_SAVE_REJECTED` になる（BR3.5 の 400 `OUT_OF_RANGE` ではない）。PostgreSQL は F1 で 400 にした。`int` などの小さい整数の型も同じ（`int4` は PostgreSQL の名前のため符号つき） | この差を既知の制約として受け入れるか（DSL の型の名前に符号なしを持たせる直しは後の Intent） |
| G14 | F2 で MySQL の `22001` を保存の拒否に含めた | `22001` は MySQL では類 22 の全体（値が長い・範囲の外・形の誤り）を含む。業者の番号で分けている長さ（1406）・桁（1264）・日時（1292）・文字集合（1366）の拒否と同じ 409 `BUSINESS_SAVE_REJECTED` | D2: A の読み方として受け入れるか |
| G15 | U1 の読み取りで、MySQL の件数の問い合わせそのものが止まったとき（N20 の 1 の形） | Connector/J の文の上限（5 秒）で 503 `BUSINESS_TARGET_DB_TIMEOUT` になった後、止まった接続がプールに戻り、次の1回の要求が 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（08S01）になった。読み取りの口（U1）は文の上限の失敗で接続を捨てないため。U1 の本体のソースは今回の範囲の外のため直していない。書き込みの口は通信の待ち（10 秒）で返り、確定・巻き戻しの失敗で接続を捨てるため、書き込みの3つの段ではこの形は起きなかった | 後の Intent に持ち越すか（読み取りの口で、文の上限の時間切れの後に接続を捨てる直し） |
| G16 | 試し走りの `bizWritePoolLimit`（1 VU・10 秒、7,104 回）で、アプリのログに WARN が 7,120 行出た | 既存の `GlobalExceptionHandler` が 4xx の業務エラーごとに WARN を1行出すため（team.md の Code Style の決まりどおり）。書き込みの業務処理の WARN（段・理由・SQLState）は、違反の後の確かめを通る外部キーの違反では出さない | 負荷の試験の WARN の件数の判定で、`bizWritePoolLimit` の 409 の分を数えから外すか（Performance Validation の前に決めたい） |
| G17 | 試し走りの `http_req_failed`（判定に使わない）が `bizDelete` 13%・`bizCreateLarge` 36% など | setup の権限の保存の 409 `ROLE_NO_CHANGE`（前の場面が残した同じ設定）が数えに入るため。場面の要求は checks の率 1 | — |

### 2.4 テストの結果（この依頼の終わりの時点。2026-10-20）

| 層 | コマンドの範囲 | 結果 |
|---|---|---|
| 3種類の DB の書き込み（Step 18） | `Postgres`・`Mysql`・`MariadbBusinessDataWriteIT` | 72 件（DB の種類ごとの飛ばし 5 件を含む）成功 |
| 認可・IDOR・一括代入（Step 19） | `BusinessDataWriteAuthorizationApiIT` | 15 件 成功 |
| 本文と値の長さ（Step 20） | `BusinessDataWriteRequestLimitIT` | 5 件 成功 |
| 時間切れと失敗（Step 21） | `BusinessDataWriteTimeoutIT`・`BusinessDataWriteNotConfiguredIT`・`BusinessDataTimeoutIT`（U1、N20 の直しの後に3回） | 4 件・3 件・7 件 成功 |
| 監査（Step 22） | `BusinessDataWriteAuditIT` | 6 件 成功 |
| 漏えい（Step 23） | `BusinessDataWriteSecretLeakIT`・`BusinessDataWriteTraceExposureIT` | 1 件・1 件 成功 |
| 接続の数（Step 24） | `BusinessDataConnectionUsageIT` | 11 件（読み取り 5・書き込み 6）成功 |
| 構造の検査と静的解析（Step 25） | 境界テスト・`ArchitectureTest`・`ApiAccessArchitectureTest`・`ExtensionOrderArchitectureTest`、`spotbugsMain`・`spotbugsGate` | 92 件 成功、`SQL_` の残り 0 件 |
| 見本の初期化の台本（Step 26） | `TargetDbSampleInitScriptIT` | 6 件 成功 |
| 負荷の試験の台本（Step 27） | `k6 inspect` と PG の試し走り（D8: A） | 5場面の名前と閾値を確かめ、全場面が閾値と checks を通った（`code-summary.md` の 3節） |
| 1コマンドの検査（Step 29） | `:backend:cleanTest :backend:cleanIntegrationTest verify` | 通過、20 分 6 秒。単体 2,583 件・結合 1,383 件（飛ばし 9）・フロントエンド 1,633 件。カバレッジは `code-summary.md` の 3節 |
| E2E（Step 30） | `./gradlew e2eTest` | 232 件 通過、10.7 分 |

### 2.5 依頼者の答え（2回目の依頼の後）

| # | 答え | 扱い |
|---|---|---|
| G13 | A（既知の制約として受け入れる） | MySQL・MariaDB の `bigint` などの整数の型で、符号つきのカラムの範囲の外の値は、DB の拒否の 409 `BUSINESS_SAVE_REJECTED` のまま（BR3.5 の 400 `OUT_OF_RANGE` との差）。DSL の型の名前に符号なしを持たせる直しは後の Intent |
| G14 | A（読み方はこのままでよい） | 変えない |
| G15 | A（U1 の既知の制約として後の Intent へ持ち越す） | 読み取りの口で、文の上限の時間切れの後に接続を捨てる直しを後の Intent に持ち越す。U1 の本体は変えない |
| G16 | A（WARN の件数から、想定どおりの 409 を code で絞って外し、外した件数を並べて記録する） | Performance Validation で、`GlobalExceptionHandler` の WARN のうち `bizWritePoolLimit` の 409 `BUSINESS_REFERENCE_NOT_FOUND` を code で数えて外し、外した件数と残りの件数を並べて記録する |

## 3. コードのレビューの直し（READY・Minor 3。依頼者の決定で3件とも統合の前に直す）

レビューの記録: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/code-generation/units/u2-businessdata-write/bb969d769be0cb2e/1.json`。コミット C1〜C8（ac101b6〜6de27b3）の後の作業ツリーで直した（コミットはしていない）。

| # | 指摘 | 直したこと | 確かめ |
|---|---|---|---|
| R-01 | `docker/targetdb/{postgres,mysql,mariadb}/03-writer-account.sh` のライセンスヘッダーに最後の行（`limitations under the License.`）が無い | 3本に最後の行を足し、`02-reader-account.sh` と同じ形（1〜15 行目が同じ）にした。あわせて、`postgres/03-writer-account.sh` が `100644` で積まれていたため、ほかの台本と同じ `100755` にした（PostgreSQL のイメージは実行できない `.sh` を読み込んで動かすため動きは同じだが、そろえた） | 1〜15 行目を `02-reader-account.sh` と比べて同じ。`git diff --summary` で mode 100644 → 100755 |
| R-02 | `RowSqlExecutor.insert` が送らなかった主キーの名前を `String[]` でドライバーに渡す。大文字と小文字の混じる名前と予約語の名前で確かめる | **今の形のまま**（名前をそのまま渡す）にし、結合テストを足した。理由: PostgreSQL のドライバー（pgjdbc 42.7.13）は `Parser` が `RETURNING` の名前を `Utils.escapeIdentifier` で引用する（既定の `quoteReturningIdentifiers=true`。アプリは変えていない）。`quoteKnown` で引用した名前を渡すと二重に引用されて誤りになる。MySQL・MariaDB のドライバーは名前を使わず、生成された鍵を位置で返す（読む側も位置で読む） | `BusinessDataWriteTables` に、主キーが `"ItemId"`（大文字と小文字）の表 `w_CaseKey` と `"select"`（予約語）の表 `w_reserved`（どちらも自動の採番）を足し、`AbstractBusinessDataWriteIT#generatedKeyNames` で、3種類の DB とも 201 で主キーが返り、別の接続で読み直した行と詳細の API が一致することを確かめた |
| R-03 | `generatedKeys` を文字として読めないとき、例外で作成全体が巻き戻って 500 になる | 文字として読めない鍵（NULL・読めない型）は、MySQL 系で鍵を読めないときと同じく鍵を返さない形にした（201、`key` を出さない。行は作られている）。結果の読みの例外（`getGeneratedKeys` の失敗など）は今までどおり想定外の失敗 | `RowSqlExecutorWriteTest#insertGeneratedKeyNotText`（直す前は `IllegalStateException` で想定外の失敗になる形） |

`source-manifest.json`・`traceability.json` は、パスが増えていないため変えていない（変えたファイルは、どれも既に並べたパス）。

### 3.1 直した後の確かめ（2026-10-20）

| 範囲 | 結果 |
|---|---|
| 直したところ | `RowSqlExecutorWriteTest`（単体）成功。`Postgres`・`Mysql`・`MariadbBusinessDataWriteIT` は 75 件（`generatedKeyNames` を3つ足した。DB の種類の飛ばし 5 件を含む）成功 |
| `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 通過、21 分 4 秒（05:50〜06:12）。単体 2,584 件・結合 1,386 件（飛ばし 9。どれも DB の種類の `assumeTrue`）・フロントエンド 1,633 件。カバレッジは全体 行 97.6%・分岐 91.2%、`businessdata.repository` 96.2%・87.9%（ほかのパッケージは変わらない） |
| `caffeinate -dims ./gradlew e2eTest`（見本の PG と Mailpit が動いていることを先に確かめた） | 232 件 通過、10.6 分（06:12〜06:23）。失敗・flaky 0 |
| `pmset -g log` | どちらの時間の窓にも Sleep・DarkWake なし |
