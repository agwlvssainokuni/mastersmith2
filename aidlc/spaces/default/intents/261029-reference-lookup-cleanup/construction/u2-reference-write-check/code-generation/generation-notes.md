# 生成の記録 — U2 u2-reference-write-check（Bolt B3）

コード生成の計画 `code-generation-plan.md` の Step 1〜11 を行った記録。Step 12 の `./gradlew verify`・`./gradlew e2eTest`・コミット・統合・push・CI と、Step 13 の `traceability.json` の確かめは指揮役が行う（依頼の範囲の外）。計画の承認の答え（9節 Q1〜Q5 はすべて A）のとおりに作った。

## 1. 手順ごとの記録

| Step | したこと |
|---|---|
| 1 | 作業ブランチ `feature/261029-reference-lookup-cleanup-b3` は指揮役が作成済み（`develop` に 549d38b・b048ec1・13883f6 があることを確かめた）。`NOT_EDITABLE_NOT_NULL` を使う所は計画の 4節の一覧（backend の2つと frontend の4ファイル）だけ。既存の書き込みのテストの DSL（`BusinessDataWriteTables`・`*BusinessDataWrite*IT`・`BusinessDataSampleSchema`）に REFERENCE・LOOKUP の options は無い（SQL の外部キーだけ） |
| 2 | `BusinessDataWriteServiceTest` と `PostgresBusinessDataWriteIT` が流せることを確かめた（4節の businessdata 全体の回帰に含む）。3.4節: 手を入れたのは `businessdata.domain`・`businessdata.service` だけで、`packagesJudgedByTotal` の7パッケージに触れていない |
| 3 | domain の新しい型 5 つ（`ReferenceCheckItem`・`RequiredReferenceState`・`PreviousReferenceValues`・`ReferenceStepOutcome`・`ReferenceWritePolicy`）、`WriteRequestValidator` の7引数の多重定義と入れ子の record `Hold`、`WriteValidation.Valid.held` |
| 4 | `ReferenceWritePolicyTest`・`ReferenceWritePolicyPropertyTest`・`ReferenceWriteTypesTest` を足し、`WriteRequestValidatorTest` に6件、`WriteRequestValidatorPropertyTest` に1件を足した |
| 5 | `ReferenceLookup.writeTargets`・`accessOf`、`BusinessDataWriteService` の ⑩（構成子に `TargetReadAccess`・`ReferenceLookup` を足した） |
| 6 | `BusinessDataWriteServiceTest` の構成子を直し、`BusinessDataWriteReferenceTest`（19件）を足し、`ReferenceLookupTest` に2件を足した |
| 7 | `BusinessDataWriteControllerTest` に2件。`README.md` の書き込みの API の節に保存前の確かめの1行を足した（`BusinessDataWriteController` の本体は変えていない） |
| 8 | `ReferenceTables` に書き込みの組（`createWrite()`・`writeTables()`・`writeSettings(...)`）と、時間切れ・食い違いの組（`createFailing()`・`failingTables()`）を足した。テストだけの部品 `PausingTargetWriteAccess`・`PausingWriteAccessConfig` |
| 9 | `AbstractReferenceWriteIT` と3種類の子のクラス |
| 10 | `ReferenceWriteAuthorizationApiIT`・`ReferenceWriteConnectionUsageIT`・`ReferenceWriteTimeoutIT`・`ReferenceWriteSecretLeakIT`・`ReferenceWriteTraceExposureIT`・`ReferenceWriteAuditIT` |
| 11 | `perf/biz/make-biz-seed.sh`（`biz_refcreate` と `COUNTS`）、`perf/make-biz-dsl.mjs`（`biz_refcreate`）、`perf/k6/scenarios.js`（`refCreateChecked`）、`perf/k6/thresholds-test.sh`（期待の表に1行）、`perf/README.md`（「他テーブル参照の保存前の確かめの場面」の節） |

## 2. 任された範囲の中で決めたこと

- **`ReferenceStepOutcome` の結果のまとめ**: `ReferenceWritePolicy.combine(List<ReferenceStepOutcome>)` は部分の結果を受け、失敗 → 行が無い → 400（保留した誤りを並べる）→ 409（拒否をすべて並べる）→ 進む の順で最初のものを返す純粋な関数にした（jqwik の性質の対象）。失敗と行が無いが2つ以上あれば最初のもの。
- **必須の参照項目の決め方**: `ReferenceWritePolicy.decide(state, held)` が `RequiredDecision`（`CHECK`・`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`・`RETURN_HELD`・`PROCEED`）を返す。`CHECK` だけは確かめの結果で決まるため、`ofDecision` は `CHECK` を受け付けない。
- **`countState` のまとめの鍵**: 参照先の鍵（スキーマ・テーブル・`valueColumn`）で要求1件の中で1回だけ呼ぶ。LOOKUP と読めない（`NO_PERMISSION`）参照先は `countState` を呼ばず、⑨ の前に取った `accessOf` の結果をそのまま状態にする（LOOKUP は問い合わせない。締め切りの `narrow` も通さない）。
- **更新前の値の読み方**: `detail` の行の値（切られたカラムは読めない扱い）、無ければ主キーの値から読み、`ColumnValueCodec.read` で型つきの値にする。NULL・読めない・詳細に出ないカラムは `PreviousReferenceValues` に入れない（「違う」として確かめる。BR2.5 の安全側）。
- **読み取りの設定が無い（`NotConfigured`）の写し**: 例外の値が無く `ReferenceReadFailure` の決まり（`budgetExceeded` が偽なら `exceptionClass` が必須）で作れないため、WARN は `purpose`・`stage=BORROW`・`reason=UNAVAILABLE`・`budgetExceeded=false` だけを出し、結果の型には `exceptionClass` に `TargetReadOutcome.NotConfigured` のクラスの名前を入れた失敗の値を持たせた（ログには出さない。写しの先は `TARGET_DB_UNAVAILABLE`）。
- **⑩ の想定外の失敗**（`detail` の `INVALID_VALUE`・`UNEXPECTED`、`check`・`countState` が投げる `BusinessDataUnexpectedException`）: 借りた接続の中では例外を投げずに空を返し、口の外で `BusinessDataUnexpectedException` にする（既存の読み取りと同じ 500。診断の WARN は出さない）。
- **WARN のキー**: `purpose=REFERENCE`（文字の定数 `REFERENCE_PURPOSE`）・`stage`（`ReferenceStepOutcome.Stage`）・`reason`・あるときだけ `sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`（いつも出す。U1 と同じ）。
- **`WriteRequestValidator` の保留**: 作成で `Hold.columns()` のカラムの値が空（本文に無い・null・`""`）のときに出る `REQUIRED`・`NOT_NULL`・`INVALID_VALUE` だけを保留する。ほかに誤りがあれば `droppedOnInvalid` のカラムの保留した誤りを落とし、残りは元の位置のまま `Invalid` に並べる。更新・削除では `Hold` を無視する。
- **`PausingTargetWriteAccess` の合図**: `AtomicReference<Gate>` の `getAndSet(null)` で一度きり、待ちは上限 20 秒の `CountDownLatch`、上限切れは元の `withWrite` を呼ばずに例外にして `timedOut()` の印を残す。テストは `@AfterEach` で `reset()`（合図を外し放す）、`finally` で `release()` する。
- **結合テストの表**: 9節 Q2: A のとおり `ReferenceTables` に足した。テストのクラスごとに `BusinessDataSampleSchema` の一意のスキーマに作り、終わったら消える。値は架空。

## 3. 計画との差（依頼者に伝えること）

| 計画 | 差 | 理由・扱い |
|---|---|---|
| Step 10 `ReferenceWriteConnectionUsageIT`（拒否・TIMEOUT・UNAVAILABLE の接続の数、応答しない対象DB を待つ間の内部DB の使用中 0） | 拒否の接続の数は確かめた。TIMEOUT の書き込みを借りた回数 0 は `ReferenceWriteTimeoutIT` に移した（`CountingDataSourceConfig` を足して確かめる）。UNAVAILABLE の接続の数の行は置いていない（接続を拒む別のアプリでは数えの部品を足していない）。「待つ間の内部DB の使用中 0」は、T-G1・T-G2（`AbstractReferenceWriteIT`）で止めた時点の内部DB の使用中 0 として確かめ、対象DB の読み取りの待ちの最中の値は確かめていない | 待ちの最中に数を読むには時刻に頼る待ち合わせが要るため（`sleep` を使わない決まり）。⑩ はトランザクションを開かず、写しの読み取りの後すぐ内部DB の接続を返す作り（BR4.2） |
| Step 10 T-F1「読み取りの設定が無い」 | 結合テストでは確かめていない（単体テスト `BusinessDataWriteReferenceTest` の BORROW の場合で確かめた） | 読み取りの設定が無いと、判定の順の ②〜④・⑧ で先に拒まれ、⑩ に届く結合の形を作れないため |
| Step 10 `ReferenceWriteSecretLeakIT`「全体の上限を除く失敗」 | 成功・409・400・TIMEOUT（眠るビュー）・MISMATCH・UNAVAILABLE（接続を拒む番号）を流した。全体の上限（`budgetExceeded`）は単体テスト（T-T3）だけ | 計画のとおり（全体の上限は除く） |
| Step 9 の T-G1・T-G2 の「止める前に流れた文」 | MySQL・MariaDB は一般ログで、読み取りのアカウントの文のうち参照先の表（`wt_tgt`）を含む文の有無を見た（T-G1 は有り、T-G2 は無し・`ws_main` の `detail` は有り）。文の数そのものは数えていない | ドライバーが接続を借りるたびに流す設定の文が混ざるため、表の名前で絞った |
| `ReferenceWriteTimeoutIT` の表 | 眠るビュー・参照元を `ReferenceWriteTimeoutIT` の中で作り、`ReferenceTables.createFailing()` とは別に持つ（同じ形） | 先に作った `ReferenceWriteTimeoutIT` が独自の3つ目の参照（`r3` → `wt_num`）を要るため。`createFailing()` は漏えい・監査のテストで使う |
| 設計 reliability-design.md 6節 T-T1「応答しない対象DB で 503 TIMEOUT」 | 計画 5節のとおり、眠るビューで 503 `BUSINESS_TARGET_DB_TIMEOUT`、接続を拒む番号で 503 `BUSINESS_TARGET_DB_UNAVAILABLE` にした | U1 のコードのレビュー（Minor）の扱い |

境界テスト（ArchUnit）・`backend/config/spotbugs-exclude.xml`・lockfile・`gradle/libs.versions.toml`・移行ファイル・DSL の JSON Schema・`application.yaml`・frontend に差は無い（`git diff develop` で確かめた）。`spotbugsGate` は通った（新しい `SQL_` の指摘は無い）。足したクラスの SpotBugs の警告（priority 2・3。関門の外）は、`ReferenceStepOutcome` の `UPM_UNCALLED_PRIVATE_METHOD`（interface の private static の部品を record の構成子から呼ぶ形の誤検知）と、既存と同じ形の `EI_EXPOSE_REP`・`EI_EXPOSE_REP2`。

## 4. テストの結果

- 単体テスト（`:backend:test`、`cherry.mastersmith.businessdata.*` と境界テスト5つ）: 51 クラス 598 件、失敗 0。うちこの単位で足した・変えたもの: `ReferenceWritePolicyTest` 17・`ReferenceWritePolicyPropertyTest` 6（jqwik）・`ReferenceWriteTypesTest` 3・`WriteRequestValidatorTest` 21（+6）・`WriteRequestValidatorPropertyTest` 4（+1、jqwik）・`BusinessDataWriteReferenceTest` 19・`ReferenceLookupTest` 24（+2）・`BusinessDataWriteControllerTest` 22（+2）・`BusinessDataWriteServiceTest` 16（構成子だけ）。jqwik の失敗は無く、記録する種は無い。
- この単位の結合テスト（`:backend:integrationTest`、2節の環境変数つき）:

| クラス | DB | 件数 | 失敗 |
|---|---|---|---|
| `PostgresReferenceWriteIT` | PostgreSQL | 34 | 0 |
| `MysqlReferenceWriteIT` | MySQL | 34 | 0 |
| `MariadbReferenceWriteIT` | MariaDB | 34 | 0 |
| `ReferenceWriteAuthorizationApiIT` | PostgreSQL | 6 | 0 |
| `ReferenceWriteConnectionUsageIT` | PostgreSQL・MySQL | 5 | 0 |
| `ReferenceWriteTimeoutIT` | 3種類 | 9 | 0 |
| `ReferenceWriteSecretLeakIT` | PostgreSQL | 1 | 0 |
| `ReferenceWriteTraceExposureIT` | PostgreSQL | 1 | 0 |
| `ReferenceWriteAuditIT` | PostgreSQL | 5 | 0 |

- businessdata 全体の結合テストの回帰: 5節に書く。
- perf の台本: `bash -n perf/biz/make-biz-seed.sh`・`node --check perf/make-biz-dsl.mjs`・`node --check perf/k6/scenarios.js` が通った。`node perf/make-biz-dsl.mjs postgres|mysql` で 11 テーブルの DSL を出せた（出力はリポジトリの外の一時の置き場）。`bash perf/k6/thresholds-test.sh` は 13 場面すべて ok（終了コード 0）。`SCENARIO=refCreateChecked` の `k6 inspect --include-system-env-vars`（`K6_IMAGE` の docker run）で、場面 `refCreateChecked`（constant-vus・10 VU・3m）と閾値 `iteration_duration{scenario:refCreateChecked}` の `p(95)<1000`・`checks{scenario:refCreateChecked}` の `rate==1`・`setupTimeout` 10m を確かめた。種の流し込みと場面の実行は Build and Test。

## 5. businessdata 全体の回帰と時間

- `caffeinate -dims ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.businessdata.*'`（2節の環境変数つき）: 39 クラス 495 件、失敗 0、飛ばし 10（`BusinessData{Read,Write}IT` の DB の種類ごとの `assumeTrue` で、ほかの DB の種類だけの場合を飛ばすもの。コンテナが無いための飛ばしは 0）。時間は 9分49秒。既存の書き込みの結合テスト・`BusinessDataConnectionUsageIT`・U1 の `*Reference*IT` は変えずに通った（T-C1）。`BusinessDataProblemTypeCatalogTest` も変えずに通った（T-C2）。
- 流した間（07:29〜07:53）に `pmset -g log` の Sleep・DarkWake は無かった（最後は 07:12）。
- `./gradlew :backend:spotlessApply :backend:spotlessCheck :backend:spotbugsGate` が通った。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と `./gradlew e2eTest`、カバレッジの値（`businessdata.domain`・`businessdata.service`）、`verify` の時間（U1 の後は 23分34秒）は指揮役が測る。

## B3 の確かめと統合 — 指揮役の記録

- 依頼者の決定: 計画との差 3 つ（接続の数の確かめを止めた時点の値で代える・読み取りの設定が無い場合は単体テスト・NotConfigured の失敗の値の exceptionClass）を受け入れた。
- コミット: ad3c896（C1）・5ba46e4（C2）・845e83e（C3）・4cd5451（C4）。C1・C2 の時点でコンパイルが通ることを確かめた。
- `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の環境変数・`caffeinate -dims`）: 成功、26分48秒（U1 の後の 23分34秒から約 3 分の伸び）。単体 2,804 件・結合 1,652 件、失敗 0。SKIPPED 10 件は DB の種類ごとの前からの `assumeTrue`。
- E2E の全件: 256 件すべて通過（13.2 分）。流した 08:03〜08:43 に Sleep・DarkWake は無かった。
