# セキュリティの要件 — U2 u2-reference-write-check

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR1・NFR5、FR3.x）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（US3.1〜US3.3・US1.5・AC3.5.5）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/`（`rules.md` の BR1〜BR7、`functional-spec.md` の 2節〜4節）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C3・C5・C6）
- U1 の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/`（継ぎ目の `check`・`countState` の扱いだけ。U1 の ID はこの単位では使わない）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/nfr-requirements-questions.md`（Q1〜Q3 はどれも A と「決まっていること」）
- `aidlc/spaces/default/memory/team.md` の他テーブル参照の必須のテスト (a)〜(k)、`aidlc/spaces/default/memory/project.md` の Forbidden・Mandated

## ID の振り方

- この単位の枝番は、どの上流の NFR でも `.21` から振り、`.40` までに収める（U1 は `.1`〜`.20`、U3 は `.41`〜。「決まっていること」）。上流の要件 NFR1〜NFR9 は枝番を持たないため、番号の意味の重なりは無い。
- 上流に当たる NFR が無い監視の要件は、U1 と同じく意味の近い NFR に寄せた: ログの出し方は NFR1、p95 の区画は NFR2、503 の警報は NFR3。

| 上流の ID | この単位での扱い | この単位の ID | 置いた文書 |
|---|---|---|---|
| NFR1 セキュリティ | 当たる | NFR1.21〜NFR1.31 / NFR1.32 | security / observability |
| NFR2 性能 | 当たる | NFR2.21〜NFR2.25 / NFR2.26〜NFR2.28 / NFR2.29 | performance / scalability / observability |
| NFR3 接続 | 当たる | NFR3.21〜NFR3.23 / NFR3.24〜NFR3.27 / NFR3.28 | scalability / reliability / observability |
| NFR4 アクセシビリティ | 当たらない（画面は U3） | — | — |
| NFR5 テスト | 当たる | NFR5.21・NFR5.22 / NFR5.23 | security / performance |
| NFR6 多言語 | 部分（code の決まりだけ。文言は U3） | NFR6.21 | reliability |
| NFR7 互換性 | 当たる | NFR7.21・NFR7.22 | reliability |
| NFR8 運用 | 当たらない（コンテナと JWT の伏せ字は U4・U5） | — | — |
| NFR9 供給網 | 当たる（新しい依存を足さない） | NFR9.21 | tech-stack-decisions |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.21 | 保存前の参照の確かめは、作成・更新の要求ごとにサーバー側で、①〜④ で取った同じ権限の写し（作業ロール）で判定する（BR4.2・C6）。参照元のカラムを書ける権限だけを持ち、参照先を読めない利用者の値は保存しない（`team.md` の (g)、BR4.4）。画面で選択肢を出さないことを、この判定の代わりにしない（`project.md` の Mandated）。既存の作成・更新の API の 401・403 の判定の順（BR1.1 の ①〜⑨）は変えない | パラメーターを使う表の結合テストで、参照先の表が読めない・`valueColumn` の主権限が NONE・参照先の表が DSL から消えた、の各利用者（要る権限だけを欠き、参照元は FULL）の作成・更新が 409 になり、行が増えない・変わらないことを、アプリとは別の接続で読み直して確かめる。作業ロールを切り替えた直後の次の要求で結果が切り替わること | Code Generation |
| NFR1.22 | 参照先に無い値・読めない参照先の値・参照先の型として読めない値の拒否は、状態コード 409・code `BUSINESS_REFERENCE_NOT_FOUND`・項目の誤り `REFERENCE_NOT_FOUND`・本文の構造とメッセージを同一にし、存在と権限を応答から推測させない（BR7.2・BR4.4・BR4.6） | 3つの場合の応答の本文を比べる結合テスト（状態コード・`code`・`fieldErrors` の構造・メッセージが同じ）。読めない場合と型として読めない場合に参照先への問い合わせが流れないこと | Code Generation |
| NFR1.23 | 確かめる参照と参照先（スキーマ・テーブル・`valueColumn`）は、要求1件の権限の写しの DSL から引き、要求の本文の名前や値から参照先を作らない（`team.md` の (b)・(g) の IDOR、BR2.1）。本文で別の参照先を指すように値を差し替えても、その参照のカラムの DSL の参照先でしか確かめない。本文に DSL に無いカラムを足しても既存の検証で拒否され、状態は変わらない | 他の参照先にだけある値を送った作成・更新が 409 になり状態が変わらないこと、存在しないカラムを足した本文が既存の 400 になることを結合テストで確かめる | Code Generation |
| NFR1.24 | 参照先の値はバインド変数で渡し、識別子は DSL と照らした名前だけを引用する（`project.md` の Forbidden）。U2 は SQL を組み立てず、U1 の口（`check`・`countState`）と既存の行の SQL の部品だけを使う。`java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所、SpotBugs の除外、既存の境界テスト（`BusinessDataBoundaryArchitectureTest` など）を緩めない（`team.md` の (k)） | 参照の値に `'`・`"`・`;`・`--`・バッククォート・改行を含めた作成が SQL として解釈されず 409 か保存になること（3種類の DB）。差分で境界テストと `backend/config/spotbugs-exclude.xml` が足す方向にも変わっていないことを、コード生成の計画とレビューで確かめる | Code Generation |
| NFR1.25 | 参照の値・参照先の行の値・本文の行の値と、対象DB の接続情報を、アプリのログ・トレースの属性・エラー応答・監査の行に含めない（`team.md` の (d)、`project.md` の Forbidden、BR5.2・BR7.1）。JDBC の例外のメッセージは応答にもログにも出さない。値を持つ新しい型は `toString` で値を伏せ、行を汎用の `Map` のまま層をまたいで渡さない | TRACE を有効にした `XxxSecretLeakIT`（例: `ReferenceWriteSecretLeakIT`。U1 の漏えいのテストに経路を足す形でもよい）で、参照つきの作成・更新の成功・拒否・時間切れの経路を流し、架空の目印の値がログに出ないことを確かめる。外部へ送る設定を有効にした結合テストで、トレースの属性に値が残らないこと。監査の行の全部の列に目印が無いこと | Code Generation |
| NFR1.26 | 作成の必須の参照項目（NOT NULL）が読めない（`NO_PERMISSION`）・多すぎる（`TOO_MANY`）とき、値を送れば参照先を問い合わせずに NFR1.22 と同じ 409 にし、空なら 409 `NOT_EDITABLE_NOT_NULL` にする（BR3.3・BR3.4）。権限を欠く利用者が値を試して参照先の存在を知る道を作らない（行き止まりを許容する決定） | `functional-spec.md` 4.3節の表の各升目を、パラメーターを使う結合テストで確かめる。`NO_PERMISSION` で値を送ったときに `check` の問い合わせが流れないこと | Code Generation |
| NFR1.27 | 参照先が複合の主キー・主キーの無いテーブル・ビュー・自己参照・`valueColumn` が一意でないテーブルのとき、保存前の確かめを 500 にせず、決めた動作（U1 の `check` の読み方に従う。U2 は比べ直さない。BR4.3）に固定する（`team.md` の (h)）。自己参照の作成では、同じ要求で作る行を参照先として数えない | 形ごとに決めた動作を明示した結合テスト（3種類の DB）。自己参照の表で、まだ無い自分の行の値を参照にした作成が 409 になること | Code Generation |
| NFR1.28 | 保存前の確かめを MySQL・MariaDB・PostgreSQL のすべてで確かめる（`team.md` の (i)）。参照元と参照先の型の違い（`INT` と `BIGINT`、数字の文字列と整数）、大文字と小文字だけ・末尾の空白だけが違う値、`1.0` と `1` の小数、境界の値（`BIGINT` の最大値・桁の多い `DECIMAL`）、NULL と空文字列 `""` を含め、DB ごとに保存の可否が割れない（BR2.3・BR2.5・BR4.3） | Testcontainers の版を固定したイメージで、3種類すべてを `./gradlew verify` の中で流す（H2 やモックで代用しない。`project.md` の Mandated）。書き込みのテストの決まり（テストのクラスごとに名前の重ならないスキーマを作って消す、書いた後の値は別の接続で読み直す。`team.md`）に従う | Code Generation |
| NFR1.29 | 確かめと保存の間に参照先の行が消えたときは、既存の外部キー違反の変換（409 `BUSINESS_REFERENCE_NOT_FOUND`）を最後の守りにし、行を保存しない（BR6.1、AC3.1.6）。外部キーの無い参照先ではこのすき間は既知の制約として受け入れる | 確かめの後・書き込みの前で待ち合わせる差し込み口（本番のコードを変えない形。置き場は NFR 設計）で、別の接続から参照先の行を消してから書き込ませ、409 になり行が増えないことを確かめる。重なりはスレッドの数に頼らず待ち合わせで作る | NFR 設計、Code Generation |
| NFR1.30 | 参照の確かめの拒否は、既存の作成・更新の出来事に結果 `FAILURE`・理由 `BUSINESS_REFERENCE_NOT_FOUND` で残し、確かめのための読み取りそのものは監査に残さない（BR5.1・BR5.3）。監査の書き込みは対象DB の接続を返した後の別の短いトランザクションで行う（BR4.2） | 拒否・成功・時間切れのそれぞれで、監査の行の数・種類・結果・理由を数える結合テスト。参照の無い作成・更新の監査が今と同じであること | Code Generation |
| NFR1.31 | 判定の純粋な関数（値が更新前と同じかを型つきの値で比べる関数、必須の参照項目の状態と本文から結果を決める関数、確かめの結果を拒否と code に写す関数）に、jqwik の性質ベースのテストを当てる（`team.md`）。性質: 同じ値は確かめない・違う値は必ず確かめる、`NO_PERMISSION` で値があれば必ず拒否、権限を足しても保存できる組が減らない、`1.0` と `1` は同じ、大文字と小文字・末尾の空白の違いは違う | jqwik のテストで、失敗時の乱数の種を記録する | Code Generation |
| NFR5.21 | 行カバレッジ 80% 以上・分岐カバレッジ 70% 以上を、全体と、この単位が足す・変えるパッケージごとに満たす（`team.md`、AC3.5.5）。`businessdata` の各パッケージはパッケージごとの下限の対象 | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する（`project.md`） | Code Generation、Build and Test |
| NFR5.22 | 手を入れる見込みのパッケージ（`businessdata.service`・`businessdata.domain`・`businessdata.web` など）は `packagesJudgedByTotal` の一覧（7 パッケージ）に入っていない。計画で実際に手を入れるパッケージが一覧のものに当たったときは、テストを足して下限を満たし、一覧から外す（`team.md`） | コード生成の計画で手を入れるパッケージを洗い出して一覧と突き合わせ、当たったパッケージの今の値を実測して見積もる | Code Generation |

## 後の段に回すこと

- **NFR 設計**: NFR1.29 の待ち合わせの差し込み口の置き場（本番のコードを変えない形）。
- **Code Generation**: 上のテスト。漏えいのテストを U1 の漏えいのテストに足すか、別のクラスにするか。
