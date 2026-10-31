# コード生成のまとめ — U2 u2-reference-write-check

単位 U2（service・M）を、計画 `code-generation-plan.md` のとおり Bolt B3 の1つで作り、`develop` へ squash で統合した（f35b7b8。前の `develop` の先頭は 67dc8a9）。材料は `generation-notes.md`（生成の担当と指揮役の記録）と `git diff 67dc8a9 f35b7b8`（`aidlc/` を除く）。計画の承認は「plan approval off」の記録（`code-generation-questions.md`）で、計画 9節の Q1〜Q5 はすべて推奨の A で作った。

| Bolt | 統合のコミット | 作業ブランチの上のコミット | 中身 |
|---|---|---|---|
| B3 | f35b7b8 | ad3c896（C1）・5ba46e4（C2）・845e83e（C3）・4cd5451（C4） | 作成・更新の保存前の参照先の確かめ（判定の順の ⑩）、必須の参照項目の行き止まり、更新の「値が変わった参照だけ」、409 の項目の誤りと監査、3種類の対象DB と横断の結合テスト、perf の場面 `refCreateChecked` |

B3 の CI（f35b7b8）: run 41146703033、1回目の試行で success、検査の job 35分32秒（42 分の線の内）、再実行なし。間の 67dc8a9（記録だけ）の run 41129322895 も1回目で success（32分46秒）

## 1. 作ったもの

- **判定の順の ⑩（保存前の参照の段）**: 既存の ①〜⑨ の後、書き込みの接続を借りる前に、読み取りの接続（`ReadPurpose.REFERENCE`）を1本だけ借りて参照先を確かめる。読むものが無ければ借りない（BR1.2）。⑩ の中は 失敗 → 行が無い → 400 → 409 の順（BR1.3）。拒否は読み取りの接続を返した後に既存の監査の出来事に `BUSINESS_REFERENCE_NOT_FOUND` で残し、`Proceed` のときだけ既存の `withWrite` を呼ぶ（2本を同時に持たない。BR4.1）。
- **作成**: NULL でない参照をすべて DSL の並びで U1 の `check` に順に渡す。必須（NOT NULL）の参照項目は U1 の `countState`（参照先の鍵ごとに1回。BR4.7）で状態を決め、読めない（`NO_PERMISSION`）・多すぎる（`TOO_MANY`）ときは、値ありは 409 `REFERENCE_NOT_FOUND`、空は 409 `NOT_EDITABLE_NOT_NULL`（BR3.3・BR3.4）。
- **更新**: 同じ接続で更新前の行を読み（無ければ参照の拒否より先に 404。BR2.6）、型つきの値で比べて値が変わった NULL でない参照だけを確かめる（BR2.4・BR2.5）。
- **全体の上限**: ⑩ の最初の読み取りの前に締め切り（10 秒）を決め、各読み取りの前に `narrow`。足りなければ流さず `ReferenceReadFailure.ofBudgetExceeded()` で 503 TIMEOUT（NFR3.25）。失敗はその場で止め、何も保存せず、書き込みを借りない（BR4.5）。
- **純粋な判定の部品**（`businessdata.domain`）: `ReferenceWritePolicy`（`decide`・`combine`）、`ReferenceStepOutcome`、`ReferenceCheckItem`、`RequiredReferenceState`、`PreviousReferenceValues`。`WriteRequestValidator` に保留の引数 `Hold` を足した7引数の多重定義（6引数の形は残した）。
- **口の追加**: `ReferenceLookup.writeTargets`・`accessOf`（対象DB を読まない）。`BusinessDataWriteService` の構成子に `TargetReadAccess`・`ReferenceLookup` を足した。
- **文書**: `README.md` の書き込みの API の節に保存前の確かめの1行、`perf/README.md` に「他テーブル参照の保存前の確かめの場面」の節。
- **perf**: `perf/biz/make-biz-seed.sh`（参照元 `biz_refcreate` と `COUNTS`）、`perf/make-biz-dsl.mjs`（`biz_refcreate`）、`perf/k6/scenarios.js`（`refCreateChecked`）、`perf/k6/thresholds-test.sh`（期待の表に1行）。

### 変えていないもの（計画 1節・3.3節・3.4節）

境界テスト（ArchUnit）・`backend/config/spotbugs-exclude.xml`・lockfile・`gradle/libs.versions.toml`・移行ファイル・DSL の JSON Schema・`application.yaml`・`.env.example`・frontend・E2E に差は無い。新しい依存も足していない（NFR9.21）。`BusinessDataWriteController` の本体は変えていない。手を入れたパッケージは `businessdata.domain`・`businessdata.service` だけで、`packagesJudgedByTotal` の7パッケージに触れていない（NFR5.22）。

## 2. 作った・変えたファイル

アプリのソースの一覧は `source-manifest.json`（37 パス）。

| 区分 | ファイル |
|---|---|
| main・新規 | `backend/src/main/java/cherry/mastersmith/businessdata/domain/` の `PreviousReferenceValues.java`・`ReferenceCheckItem.java`・`ReferenceStepOutcome.java`・`ReferenceWritePolicy.java`・`RequiredReferenceState.java` |
| main・変更 | `businessdata/domain/WriteRequestValidator.java`・`businessdata/domain/WriteValidation.java`・`businessdata/service/BusinessDataWriteService.java`・`businessdata/service/ReferenceLookup.java` |
| 単体テスト・新規 | `domain/ReferenceWritePolicyTest.java`・`domain/ReferenceWritePolicyPropertyTest.java`・`domain/ReferenceWriteTypesTest.java`・`service/BusinessDataWriteReferenceTest.java` |
| 単体テスト・変更 | `domain/WriteRequestValidatorTest.java`・`domain/WriteRequestValidatorPropertyTest.java`・`service/BusinessDataWriteServiceTest.java`（構成子だけ）・`service/ReferenceLookupTest.java`・`web/BusinessDataWriteControllerTest.java` |
| テストの部品 | `testsupport/PausingTargetWriteAccess.java`・`testsupport/PausingWriteAccessConfig.java`（新規）、`testsupport/ReferenceTables.java`（変更） |
| 結合テスト・新規 | `web/AbstractReferenceWriteIT.java` と `Postgres`・`Mysql`・`Mariadb` の `ReferenceWriteIT`、`web/ReferenceWriteAuthorizationApiIT.java`・`ReferenceWriteConnectionUsageIT.java`・`ReferenceWriteTimeoutIT.java`・`ReferenceWriteSecretLeakIT.java`・`ReferenceWriteTraceExposureIT.java`・`ReferenceWriteAuditIT.java` |
| 文書・perf | `README.md`・`perf/README.md`・`perf/biz/make-biz-seed.sh`・`perf/make-biz-dsl.mjs`・`perf/k6/scenarios.js`・`perf/k6/thresholds-test.sh` |

テストのパスはどれも `backend/src/test/java/cherry/mastersmith/businessdata/` の下。

## 3. テストと確かめの結果

### 3.1 全体（指揮役が作業ブランチの上で流した）

- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数・`caffeinate -dims`）: **成功、26分48秒**（U1 の後の 23分34秒から約 3 分の伸び。CI の線の約 42 分の内）。**単体 2,804 件・結合 1,652 件、失敗 0**。SKIPPED 10 件は DB の種類ごとの前からの `assumeTrue`（コンテナが無いための飛ばしは 0）。カバレッジの下限（全体とパッケージごとの行 80%・分岐 70%）の関門も含めて通った。
- `./gradlew e2eTest`: **256 件すべて通過**（13.2 分）。frontend は変えていないが、業務データのパッケージの `src/main` に触れるため統合の前に流した（`team.md`）。流した 08:03〜08:43 に Sleep・DarkWake は無かった。
- `spotlessCheck`・`spotbugsGate` が通った（新しい `SQL_` の指摘は無い）。

### 3.2 この単位が足したテスト（生成の担当の実測）

| クラス | 件数 |
|---|---|
| `ReferenceWritePolicyTest` | 17 |
| `ReferenceWritePolicyPropertyTest`（jqwik） | 6 |
| `ReferenceWriteTypesTest` | 3 |
| `WriteRequestValidatorTest` | 21（+6） |
| `WriteRequestValidatorPropertyTest`（jqwik） | 4（+1） |
| `BusinessDataWriteReferenceTest` | 19 |
| `ReferenceLookupTest` | 24（+2） |
| `BusinessDataWriteControllerTest` | 22（+2） |
| `Postgres`・`Mysql`・`Mariadb` の `ReferenceWriteIT` | 各 34 |
| `ReferenceWriteAuthorizationApiIT` | 6 |
| `ReferenceWriteConnectionUsageIT` | 5 |
| `ReferenceWriteTimeoutIT`（3種類） | 9 |
| `ReferenceWriteSecretLeakIT`・`ReferenceWriteTraceExposureIT` | 各 1 |
| `ReferenceWriteAuditIT` | 5 |

jqwik の失敗は無く、記録する種は無い。businessdata の結合テストの全体（39 クラス 495 件）も、既存の書き込みの結合テスト・`BusinessDataConnectionUsageIT`・U1 の `*Reference*IT`・`BusinessDataProblemTypeCatalogTest` を変えずに通った（T-C1・T-C2）。

### 3.3 この段で流していないテスト・確かめ

- **k6 の `refCreateChecked` の実行**: この段は `bash -n`・`node --check`・`thresholds-test.sh`（13 場面 ok）・`k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめただけ。種の流し込み・試し走り・前の Intent の値との比べは Build and Test、3種類の判定は Performance Validation（5節）。
- **カバレッジの値**（B3 の verify の後の `backend/build/reports/jacoco/test/jacocoTestReport.xml`、指揮役が読んだ）: `businessdata.domain` 行 98.2%・分岐 91.5%、`businessdata.service` 行 95.5%・分岐 88.8%。どちらも下限（行 80%・分岐 70%）を満たす。
- **CI**: f35b7b8 の CI は確認中（push は依頼者が行う）。

## 4. 計画と承認済みの設計との差（依頼者の決定つき）

設計の文書は書き換えていない（`project.md`）。

| 差 | 扱い |
|---|---|
| **計画との差 1（依頼者が受け入れた）**: `ReferenceWriteConnectionUsageIT` の「応答しない対象DB を待つ間の内部DB の使用中 0」を、T-G1・T-G2（`AbstractReferenceWriteIT`）で止めた時点の内部DB の使用中 0 で代えた。TIMEOUT の書き込みを借りた回数 0 は `ReferenceWriteTimeoutIT` に移した。UNAVAILABLE の接続の数の行は置いていない | 待ちの最中に数を読むには時刻に頼る待ち合わせが要るため（`sleep` を使わない決まり）。⑩ はトランザクションを開かず、写しの読み取りの後すぐ内部DB の接続を返す作り（BR4.2） |
| **計画との差 2（依頼者が受け入れた）**: T-F1 の「読み取りの設定が無い」は結合テストでは確かめず、単体テスト（`BusinessDataWriteReferenceTest` の BORROW の場合）で確かめた | 読み取りの設定が無いと判定の順の ②〜④・⑧ で先に拒まれ、⑩ に届く結合の形を作れないため |
| **計画との差 3（依頼者が受け入れた）**: 読み取りの設定が無い（`NotConfigured`）の失敗の値は、`exceptionClass` に `TargetReadOutcome.NotConfigured` のクラスの名前を入れて作った（ログには出さない）。WARN は `purpose`・`stage=BORROW`・`reason=UNAVAILABLE`・`budgetExceeded=false` だけ | `ReferenceReadFailure` の決まり（`budgetExceeded` が偽なら `exceptionClass` が必須）で例外の値が無いと作れないため。写しの先は `TARGET_DB_UNAVAILABLE` |
| 全体の上限の止めの作り方は設計の文書の `budgetExceeded()` ではなく `ReferenceReadFailure.ofBudgetExceeded()` | U1 のコード生成の依頼者の決定 A のとおり（計画 1.1・5節） |
| reliability-design.md 6節 T-T1「応答しない対象DB で 503 TIMEOUT」 | 眠る関数を含むビューで 503 `BUSINESS_TARGET_DB_TIMEOUT`、接続を拒む番号・`SilentServer` では 503 `BUSINESS_TARGET_DB_UNAVAILABLE` とした（U1 のコードのレビューの Minor の扱い、計画 5節）。接続を借りる段で止まると TIMEOUT にならないため |
| WARN の読み方 | 診断の WARN は ⑩ の失敗ごとにちょうど1行、キーは `purpose=REFERENCE`・`stage`（`BORROW`・`PREVIOUS_ROW`・`COUNT_STATE`・`CHECK`）・`reason`・あるときだけ `sqlState`・`vendorCode`・`exceptionClass`・いつも `budgetExceeded`（U1 と同じ）。拒否と 400 では 0 行。`ReferenceWriteSecretLeakIT` で `purpose=WRITE` の行が無いこと、`GlobalExceptionHandler` の WARN が1行であることも確かめた。⑩ の想定外の失敗（`INVALID_VALUE`・`UNEXPECTED` など）は既存の読み取りと同じ 500 で、診断の WARN は出さない |
| NFR2.29・NFR3.28・NFR1.27 の追跡（基盤の設計の承認の場 R-01） | `traceability.json` に載せた。NFR2.29・NFR3.28 は `Deferred`（Observability Setup）、NFR1.27 は `AbstractReferenceWriteIT` の `targetShapes` で `OK` |
| 読み取りのプールの上限 10 → 5 の注記（基盤の設計の承認の場 R-02） | `perf/README.md` の U2 の節に「前の Intent の書き込みの場面の値と並べるときは、読み取りのプールの上限が 10 から 5 に変わったことを書く」と書いた。5 は負荷の試験の使い捨ての環境の値（U1 の一時の環境ファイル）で、アプリの既定（読み取り 10・書き込み 5）は変えていない。上限 10 の回も流すかは Build and Test の計画で決める |
| 参照元の表が空であることの確かめ（基盤の設計の承認の場 R-03、計画 9節 Q3: A） | 種の台本の `COUNTS` に `biz_refcreate` を足した。種の後に参照先 `biz_code200` が 200 行、`biz_refcreate` が 0 行であることを確かめてから流すと `perf/README.md` に書いた |
| SpotBugs の priority 2・3 の警告（関門の外） | 足したクラスに、`ReferenceStepOutcome` の `UPM_UNCALLED_PRIVATE_METHOD`（interface の private static の部品を record の構成子から呼ぶ形の誤検知）と、既存と同じ形の `EI_EXPOSE_REP`・`EI_EXPOSE_REP2` が出る。関門（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）の外のため除外の設定は足していない |
| T-G1・T-G2 の「止める前に流れた文」 | MySQL・MariaDB は一般ログで参照先の表（`wt_tgt`）を含む文の有無を見た（文の数そのものは数えていない。ドライバーの設定の文が混ざるため） |
| `ReferenceWriteTimeoutIT` の表 | 眠るビュー・参照元を `ReferenceWriteTimeoutIT` の中で作り、`ReferenceTables.createFailing()` とは別に持つ（3つ目の参照が要るため。形は同じ） |
| `ReferenceWriteSecretLeakIT` | 全体の上限（`budgetExceeded`）は流さず、単体テスト（T-T3）だけ（計画どおり） |

## 5. 後の段に引き継ぐこと（持ち主つき）

| 持ち主 | 引き継ぐこと |
|---|---|
| Build and Test | `refCreateChecked` の種の流し込みと試し走り（k6 の実行）、既存の書き込みの場面と前の Intent の値との比べ（読み取りのプールの上限 10 → 5 の注記つき。上限 10 の回も流すかを計画で決める）、NFR2.22〜NFR2.24 の確かめ、`businessdata.domain`・`businessdata.service` のカバレッジの値、f35b7b8 の CI の検査の job の時間（NFR5.23）と試行ごとの結果 |
| Observability Setup | NFR2.29（作成・更新の道が既存の業務データの区画に入ること）、NFR3.28（保存前の確かめの 503 を既存の警報 `ms-biz-target-db` で拾うこと） |
| Performance Validation | 設計のとおり、`refCreateChecked` の3種類の対象DB での判定（NFR2.21 の p95 < 1,000 ms と `checks` の率 1、NFR2.25 の Unverified の扱い、AC3.5.4 の場面 (3)） |
| u3-reference-screens | AC1.5.1（画面の文言と送信の前の項目の誤り）、AC3.1.7（項目の近くの誤り・フォーカス・`aria-describedby`） |

## 6. 文書と実装の読み合わせ（計画 Step 13）

- `README.md` の書き込みの API の節の1行と `perf/README.md` の U2 の節は、実装（判定の順の ⑩、`biz_refcreate` の形、`refCreateChecked` の閾値、2つのプールの名前）と合う。
- `traceability.json` は全 90 行（OK 80・Deferred 10）で、センサー（traceability）が `passed`。OK の `target` はどれも1つの実在するファイル。測っていない性能の目標（NFR2.21・NFR2.22〜NFR2.25）は OK にせず `Deferred` にした。

## 7. 依頼者に確かめたいこと

新しく確かめたいことは無い。計画との差 3 つは依頼者が受け入れ済み（4節）。B3 の CI（f35b7b8）の結果は確認中で、失敗したときは `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。
