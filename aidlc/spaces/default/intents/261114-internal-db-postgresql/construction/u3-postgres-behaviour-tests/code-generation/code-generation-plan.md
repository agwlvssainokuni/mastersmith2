# コード生成の計画 — u3-postgres-behaviour-tests

## Summary

- Builds: 内部DB を PostgreSQL で動かす経路の振る舞い（11 機能の代表の流れ・違反の区分・待ちの上限と行き詰まり・失敗後のトランザクション・書き直した SQL と 3 つの鍵の重なり・10MB と 32 バイト・時刻・検索）を、実際の PostgreSQL 18.6 の結合テストで確かめる
- Touches: `backend/src/test/java/cherry/mastersmith/` の下だけ（`internaldb/testsupport`・`internaldb/postgres`・各機能の `postgres` のパッケージと `testsupport` の PostgreSQL 用の部品）。`src/main`・H2 のテストと部品・`build.gradle.kts`・`ci.yml`・依存・lockfile は変えない
- Tests: PostgreSQL の結合テスト 24 クラス（流れ 11・違反 2・待ち 4・失敗後 1・書き直し 3・型 3）と出力の検査 1 クラス、普段の `verify` に入る構造のテスト 1 クラス。1 クラスは 3〜8 件の目安
- 変えない: `src/main` のすべて、H2 の既存の結合テストとテストの部品、`TestDatabase`・`HttpTestClient` などの既存の共通の部品（読むだけ）、`internalDbPostgresTest` のタスクの設定、CI

## 計画の前提（設計の段の決定と、持ち越した審査の指摘の受け方）

機能設計・NFR 要件・NFR 設計・インフラ設計の文書は承認の場の前の初稿で、承認の場の決定はまだ無い（各段の審査はすべて READY で、指摘は持ち越し）。この計画は、設計の文書を書き換えずに、次のとおり各段の決定と指摘を受ける。承認の場で設計の文書を直すときは、段のやり直しを先に行い、この計画との差を確かめ直す。

### 依頼者が決めたこと

| 質問 | 決定 | 反映先 |
|---|---|---|
| 機能設計 Q1（2026-11-28） | 機能ごとの代表の流れは、PostgreSQL 用の新しいテストを書く（期待は H2 の既存のテストの値を写す。H2 のテストは変えない） | Step 3〜9 |
| U2 の Q3 | 検索の Unicode の端の 3 つ（`İ`・語末シグマ・`ǅ`）は確かめの対象に入れない（U4 の文書に書く） | Step 9 |
| 今回の承認の場（2026-11-28） | 計画の前の本体への手入れは無い（`src/main` を変えない） | 全体 |

### この計画が決める点（設計の審査の指摘の答え。依頼者の承認で確定する）

| 指摘 | この計画の決め | 反映先 |
|---|---|---|
| 機能設計 R-01・NFR 要件 R（時刻の JVM の分け方）・NFR 設計 DD-3 と Minor（`*Test` では IT の後の既定の時差を読めない） | 時刻のテストは専用のクラス `PgTimeRoundTripIT`（クラスごとのアプリ）。`@BeforeAll` で `TimeZone.setDefault` を変えてからアプリを起動し、`@AfterAll` の `finally` で元に戻し、**同じクラスの `@AfterAll` で復元できたこと（既定の時差が起動前の値）を `assertThat` で確かめる**（`*Test` の単体テストは作らない。DD-9 の「普段の `verify` に加わるのは構造のテスト 1 件」を保つ）。共有のアプリの接続プールは別の時差で作った接続を持ちうるが、時刻のテストは共有のアプリに触れないため影響しない | Step 9 |
| 機能設計 R-02・NFR 設計 Major（DD-2 の合否が不安定になりうる・アプリの API のどの経路で「先に 1 行を持って次の行を待つ」を作るか不明） | 合否は「ちょうど一方が `40P01` 相当の排他の失敗で終わり、もう一方は続けられる」にする（どちらが犠牲でもよい）。先にアプリの経路を待たせ、`pg_stat_activity` で待ちを観測した直後に保持側が輪を閉じる順は、犠牲側を決めやすくするためだけで合否には使わない。`SHOW deadlock_timeout` が 1 秒であることを確かめる行を足す。アプリの経路は Step 2 の使い捨ての試しで決める（候補: `GroupRoleAssignmentConcurrencyIT`・ロール割り当ての H2 の参照のテストが使う、1 つのトランザクションで複数の行を取る API）。アプリの API に複数の行を順に取る経路が無いと分かったときは、単独の `RowLockFailures` 分類（Step 2 の確かめ）に切り替えて依頼者に諮る | Step 2・7 |
| 機能設計 R-03〜（BR5.2 の合否の形・待ちの幅の測り・BR3.4 の出どころ・共有アプリの共有の状態・BR9.2 の一覧・FR5.1 の対応） | BR5.2 は (a)(b) と (c) を別のテストメソッドにする（収束する・収束しない）。待ちの幅は呼び出しの開始から測り、観測は待ちに入ったことの確認だけ（DD-1）。BR3.4（SKIP LOCKED）の出どころは、H2 の `ConcurrentLoginAuditIT` と `LoginApiIT` の応答の形と、`LoginAttemptStateRepository` の本体の読み取り（Step 2）。全体の状態に触れる流れ（DSL の適用・メニューの入れ替え）はクラスごとのアプリ（DD-5）。構造のテストの許す依存は表で固定する（Step 4）。FR5.1 の対応は Step 10 の traceability.json で BR2〜BR8 を足す | Step 2・4・7・10 |
| NFR 要件 Major（待ちの測りの起点） | 呼び出しの開始から測る（DD-1）。要件の NFR1.2 との差を `code-summary.md` の「上流との差」に書く | Step 7・`code-summary.md` |
| NFR 要件 Major（`randomSecret` の食い違い） | `TestDatabase.randomSecret()` を許す側（DD-14）。`TestDatabase.url` / `register` は禁止 | Step 4 |
| NFR 設計 Major（NFR6.1 の決定が無い） | `internalDbPostgresTest` の JaCoCo 無効を変えない・計測の除外を足さない。確かめは Step 10 の `git diff` で `build.gradle.kts` に差分が無いこと | Step 10 |
| インフラ設計 Minor（「U1 の 8 クラス」の根拠なし・NFR5.3 の対応先） | 数は `@InternalDbPostgresIT` を付けたクラスを Step 1 で数えて `code-summary.md` に書く（設計の文書の数は参考）。`*IT` で終わらない名前のクラスが漏れないよう、命名の決まりを構造のテストに入れる（Step 4） | Step 1・4 |
| 前提データの部品の置き場（計画の決め） | 各機能の `testsupport` ではなく `internaldb/testsupport` にまとめる（H2 の機能別の部品と重ならない名前にするため） | Step 3 |
| 各段の Minor（実装寄りの記述・番号の食い違い・`cicd-pipeline.md` の入力名の誤記など） | 設計側の文書の直し（承認の場で、段のやり直しの後に直す）。コードの手順は変わらない | 承認の場 |

### 上流との差（`code-summary.md` の「上流との差」に 1 か所で書く）

1. NFR 要件 NFR1.2（観測の時点から測る）は、NFR 設計 DD-1 で「呼び出しの開始から測る」に変えた。この計画は NFR 設計に従う。
2. NFR 要件 NFR9.1 の `randomSecret` は NFR 設計 DD-14 で許す側に変えた。この計画は NFR 設計に従う。
3. 機能設計 BR3.3（行き詰まり）は「検出された側が失敗し、もう一方は続けられる」までを合否とし、NFR 設計 DD-2 の「アプリが犠牲」は合否にしない（この計画の決め）。

## 要件との対応

| 要件 | 計画の手順 |
|---|---|
| FR3.1・FR5.1 | Step 5（11 の機能の流れ） |
| FR3.2 | Step 6（違反・大文字と小文字） |
| FR3.3 | Step 7（書き込みの待ち・行の排他・行き詰まり・SKIP LOCKED） |
| FR3.4 | Step 7（失敗後のトランザクション） |
| FR3.5 | Step 8（書き直した SQL・3 つの重なり・55P03） |
| FR3.6 | Step 9（10MB・32 バイト） |
| FR3.7 | Step 9（時刻） |
| FR3.8 | Step 9（検索） |
| FR5.4 | Step 1・10（H2 のテストに差分が無いこと） |
| NFR1.x・NFR5.x | Step 3・7・10（待ちの測り・時間の記録） |
| NFR2.x | Step 3・5〜9（値・接続情報を出さない。`PgSecretLeakIT`） |
| NFR3.1・NFR8.1 | Step 4・10（版の文字列・lockfile に差分が無いこと） |
| NFR4.x | Step 3・4・7（待ち合わせ・後始末） |
| NFR6.x・NFR9.x | Step 4・10（構造のテスト・JaCoCo・H2 のテストに差分が無いこと） |

## 実施の手順（Testing Contract: test-after。層ごとに実装を書き、その層のテストを書いて流す）

当たる層は、DB・repository の振る舞い（PostgreSQL での確かめ）と API の振る舞い（11 の機能の流れ）。本体を書かない単位のため、「実装」はテストの部品、「テスト」は結合テストそのものになる。画面の層は当たらない。

- [x] **Step 1: 実行の確かめと、影響の洗い出し（骨組み）**
  - `unit-test-instructions.md` の命令が使えることを確かめる: U1 の `InternalDbPostgresBaselineIT` 1 クラスだけを `internalDbPostgresTest --tests` で流して通る（colima の 2 つの環境変数付き）。
  - `@InternalDbPostgresIT` を付けた既存のクラスの数と名前、`*IT` で終わらないものの有無を数えて書く（インフラ設計 Minor の答え）。
  - H2 の参照のテスト（`rules.md` BR1.1 の一覧）の期待（状態コード・`code`・項目の名前）を、機能ごとに読んで表にする（`ParityExpectation`）。H2 の部品に頼る前提データの作り方（`RoleFixtures` など）は、API での作り方に置き換える一覧にする。
  - 既存の H2 の部品・テストを変えないことを確かめる準備: `git status` で Step 開始時の差分が `aidlc/` だけであることを記録する。
  - 依存・lockfile・`ci.yml`・`build.gradle.kts` に差分が無いことを `git diff --stat` で確かめる（NFR8.1・NFR6.1）。

- [x] **Step 2: 使い捨ての確かめ（リポジトリの外。`project.md` の Corrections の「DB の振る舞いに頼る前提は先に試す」）**
  - 確かめる点と、外れたときの扱い（外れたものは本体の差として記録し、依頼者に諮って U2 に戻す。テストの期待を弱めて通さない。BR9.4）:
    1. 行の排他の読み取り（3 秒のヒント）が PostgreSQL で 3 秒以上 4 秒未満で切れ、切れた後に接続の `lock_timeout` が 2 秒へ戻ること（アプリの既存の経路で）。
    2. 行き詰まり（`40P01`）が、アプリの既存の分類（`RowLockFailures`・各 `Classifier`・`GlobalExceptionHandler`）で排他の失敗に分けられること。アプリの API に、1 つのトランザクションで複数の行を順に取る経路があるか（候補: ロールの割り当て・グループの割り当て）。無ければ DD-2 の替わりの形を依頼者に諮る。
    3. 大文字と小文字だけが違うメールアドレス・ロールの名前・グループの名前を、H2 のアプリで作って結果（受け付ける・拒否する）を表にする（BR2.2。H2 のアプリを使い捨ての起動で試す）。
    4. ログインの状態の SKIP LOCKED の経路（`LoginAttemptStateRepository`）を、PostgreSQL で実際に動かして期待どおりかを確かめる。
    5. `SHOW deadlock_timeout` の値、`pg_stat_activity` の観測で `wait_event_type = 'Lock'` が見える利用者の権限（専用の利用者で自分のデータベースの待ちが見えるか）。
    6. 共有のアプリ 1 つ + クラスごとのアプリ 1 つ + 保持側の接続で、`pg_stat_activity` の最大の接続の数（`max_connections` 100 の内か）。
  - 結果の要点（版・設定・数値・設計への意味）を `code-summary.md` に写す。

- [x] **Step 3: 共有のテストの部品（テストの部品。実装）**
  - `internaldb/testsupport/` に PostgreSQL 用の部品を足す（すべて Apache License 2.0 のヘッダー付き。H2 の部品に依存しない）:
    - `PgSharedApp`: JVM で共有のアプリ（最初の使用時に 1 回起動、JVM の終了で閉じて `DROP DATABASE`。起動の時間を `INTERNAL_DB_PG_SHARED_APP startupMs=` の 1 行で出す。DD-5・DD-10）。ポート・初期管理者の扱いは `InternalDbPostgresBaselineIT` と同じ起動の引数を使う。
    - `ConflictProbe`: 保持側の接続（`InternalDbPostgresDatabase.connect()`。`BEGIN` して確定しない）・待つ側を別のスレッドで走らせる部品・`pg_stat_activity`（`wait_event_type = 'Lock'`）を 50 ms の間隔で最大 10 秒観測する部品。観測が 10 秒で終わらなければ失敗にする。保持側は `try`-`finally` で `ROLLBACK` して閉じる。クラスごとの `@AfterEach` で待ちが残っていないことを確かめる口を持つ（DD-4）。
    - `WaitWindow`: 呼び出しの開始から失敗が戻るまでの時間を測り、幅（下限以上・上限未満）を判定し、`INTERNAL_DB_PG_WAIT <テスト名> waitMs=<値>` の 1 行を出す（DD-1・DD-10）。幅を広げる口を持たない。
  - 機能ごとの前提データの部品（`PortableFixture`）: 各機能の `testsupport` の下ではなく、まず `internaldb/testsupport` に **HTTP の API と標準の SQL だけ**で利用者・ロール・グループ・招待・DSL を作る部品を足す（初期管理者でログインして API で作る。H2 の `RoleFixtures`・`UserAdminFixtures` に依存しない）。一意の値は実行ごとの乱数で作る（`TestDatabase.randomSecret()` は許す。DD-14）。メールアドレスは予約のドメインだけ。
  - 接続の情報・行の値を出力・例外の文に出さない（DD-12・DD-13）。

- [x] **Step 4: 構造のテスト（構造のテスト。普段の `verify` に入る `*Test` 1 クラス）**
  - `internaldb/PgTestBoundaryArchitectureTest`（コンテナを使わない。ArchUnit。既存の境界テストは緩めない）。対象は `@InternalDbPostgresIT` を付けたクラスと、`internaldb/testsupport` の PostgreSQL 用の部品。
  - 禁止する依存（表で固定する）: `org.h2..`、`cherry.mastersmith.auth.testsupport.H2SessionWaits`・`cherry.mastersmith.common.testsupport.H2BlockedSessions`・`cherry.mastersmith.role.testsupport.RoleFixtures`・`FailingSettingTrigger`・`cherry.mastersmith.useradmin.testsupport.UserAdminFixtures`・`TestUserAdminBarrier`・`cherry.mastersmith.businessdata.testsupport.BusinessDataWriteTables`・`cherry.mastersmith.auth.testsupport.TestLoginAttemptBarrier`・`cherry.mastersmith.invitation.testsupport.TestInvitationBarrier`、`TestDatabase` の `url`・`register` の呼び出し（`randomSecret` は許す）。
  - 許す依存（固定の一覧。「など」で終えない）: `cherry.mastersmith.common.testsupport.HttpTestClient`・`JsonLogRecords`・`LogEvents`、`cherry.mastersmith.auth.testsupport.MutableClock`（読むだけ）、`cherry.mastersmith.internaldb.testsupport..`、`cherry.mastersmith.MastersmithApplication`、JDK・JUnit・AssertJ・Spring Boot のテストの部品・pgjdbc・Testcontainers。ここに無い既存のテストの部品を使うときは、この一覧と計画に足してから使う。
  - 確かめること: (1) `@InternalDbPostgresIT` が付くクラスの名前が `*IT` で終わる（Gradle のタスクの絞りに漏れない。インフラ設計 Info の答え）、(2) 禁止する依存が無い、(3) 文字列 `postgres:` のイメージの記述が U3 のテストに無い（ソースの読み取り）、(4) U3 のクラスに `@InternalDbPostgresIT` が付く。

- [x] **Step 5: 11 の機能の代表の流れ（API の層のテスト。結合テスト）**
  - `PgAuthFlowIT`（ログイン・トークンの更新・ログアウト）・`PgPasswordFlowIT`・`PgUserAdminFlowIT`（止める・戻す・ロックの解除）・`PgInvitationFlowIT`（招待と登録の完了）・`PgRoleFlowIT`（ロールと権限の割り当て）・`PgWorkRoleFlowIT`（作業ロールの選択）・`PgGroupFlowIT`・`PgNavigationFlowIT`・`PgDslFlowIT`（読み込みと適用）・`PgPreferencesFlowIT`（表示の設定）・`PgAuditFlowIT`（監査の記録の種類と件数）。
  - 各クラスに成功の流れ 1 本と代表の失敗 1 本（認可の拒否・検証の失敗・重なりのいずれか。H2 の参照のテストにあるもの）。期待は Step 1 の表の値（状態コード・`code`・応答の項目の名前の集合）。共有のアプリを使うが、全体の状態に触れる流れ（`PgDslFlowIT`・`PgNavigationFlowIT`）はクラスごとのアプリ（DD-5）。
  - `@DisplayName` は英語。失敗の文は状態コード・`code`・件数だけを出す（DD-13・DD-22）。

- [x] **Step 6: 違反の区分（API の層のテスト。結合テスト）**
  - `PgViolationClassificationIT`: メールアドレス・ロールの名前・グループの名前の重なり、存在しない親への追加、参照の残るロール・利用者・グループの削除の応答（状態コードと `code`）が H2 の参照のテストと同じ。想定外（500）が出ない。
  - `PgCaseSensitivityIT`: Step 2 の表（H2 の実測）のとおり、大文字と小文字だけが違う組を受け付ける・拒否する。

- [x] **Step 7: 待ち・行き詰まり・失敗後のトランザクション（DB・repository の層のテスト。結合テスト）**
  - すべてクラスごとのアプリ（待ちの設定に触れないが、共有のアプリの接続を専有しないため）。`ConflictProbe`・`WaitWindow` を使う（呼び出しの開始から測る）。
  - `PgWriteWaitIT`: 保持側が行を確定せずに持つ状態で、アプリの書き込み（作業ロールの保存・プレビューを置く・表示の設定の保存）が 2 秒以上 3 秒未満で切れ、H2 と同じ排他の失敗の応答になる（BR3.1）。
  - `PgRowLockWaitIT`: 行の排他の読み取りが 3 秒以上 4 秒未満で切れ、同じ接続で続けた書き込みが 2 秒で切れる（上限が戻る。BR3.2）。
  - `PgDeadlockIT`: Step 2 で決めた経路で輪を作り、ちょうど一方が排他の失敗で終わり、もう一方は続けられる。`SHOW deadlock_timeout` が 1 秒であることを確かめる（BR3.3。上の「この計画が決める点」）。
  - `PgSkipLockedLoginIT`: Step 2 の確かめのとおり、ダミーの行が持たれているときの動き（待たずに別の行を選ぶ・全部持たれたら 3 秒で切れる）と応答の時間の揃え方（BR3.4）。
  - `PgFailureTransactionIT`: 複数の文の処理の 2 文目の失敗（重なり・上限切れ）で、1 文目の変更が残らない（件数）、失敗の監査（別のトランザクション）が残る、次の要求が `25P02` にならず通る（BR4.1）。

- [x] **Step 8: 書き直した SQL と鍵の重なり（DB・repository の層のテスト。結合テスト）**
  - `PgPortableSqlIT`: `savePreferences`（本人なし・変更なし・NULL への変更・通常の変更）、作業ロールの保存・プレビューを置く（初回・2 回目・同じ値の再保存）の結果が H2 のテストと同じ。文の数は U2 の `StatementCounter` を読むだけで使い、1 文・1 接続を確かめる（BR5.1）。
  - `PgKeyConflictIT`: 別々のテストメソッドで、(a) 作業ロールの主キー、(b) プレビューの主キーの重なり（保持側が同じ鍵を確定せずに挿入し、確定後にアプリ側が `UPDATED_AFTER_CONFLICT` で収束して後の値が残る。セーブポイントの後に同じトランザクションが続けられる）、(c) `preview_id` の一意の制約は重なりと扱わず失敗として出る（BR5.2）。
  - `PgLockFailureClassificationIT`: 上限切れの `55P03` が、`KeyedUpsertExecutor` の中の生の例外と、Hibernate が変換した例外の両方で、作業ロールは `Busy`、プレビューは排他の失敗の区分になる（BR5.3）。

- [x] **Step 9: 型・時刻・検索・出力の検査（DB の振る舞いのテスト。結合テスト）**
  - `PgBinaryRoundTripIT`: 10,485,760 バイトちょうどの DSL の本文（全バイト値を含む）の保存と読み出し（長さと SHA-256 が一致）、上限 +1 バイトの拒否（既存の検証の応答）、全バイト値を含む 32 バイトのハッシュをリフレッシュと招待の両方で保存し照合できる（BR6.1・BR6.2）。本文・ハッシュの値は出力に出さない（DD-16）。
  - `PgTimeRoundTripIT`: 上の決め（時刻の JVM の分け方）のとおり。JVM の既定の時差を `UTC`・`Asia/Tokyo`・`America/New_York` に変え、PostgreSQL のセッションの `TimeZone` を変えても時点が同じ。夏時間の境の時刻が同じ時点に戻る。注入した時計（`MutableClock`）を、期限の直前・ちょうど・直後に進めて、招待・リフレッシュトークンの有効期限とログインのロックの解除が H2 の参照のテストと同じ結果（BR7.1・BR7.2）。
  - `PgUserSearchIT`: 見本の利用者（日本語・全角半角・カナ・記号・長い入力）に対し、`%`・`_`・`\`・大文字と小文字違い・連続空白の検索の結果の集合が固定の期待と同じ。Unicode の端の 3 つは対象に入れない（BR8.1）。
  - `PgSecretLeakIT`: 共有のアプリの起動の出力に、接続先・利用者・パスワードの文字列が無い（DD-12）。BR2・BR3・BR5 のクラスに、アプリのログに作った値が出ない確かめを 1 件ずつ足す（DD-13）。

- [x] **Step 10: 全体の確かめ・計測・記録**
  - 明示して全件を流す: `DOCKER_HOST=... TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=... caffeinate -dims ./gradlew :backend:internalDbPostgresTest`（U1 のクラスを含む全件）。通る。全体の時間と、待ちの確かめ 1 件ごとの `INTERNAL_DB_PG_WAIT` の行・共有のアプリの起動の時間を `code-summary.md` に表にする。
  - 1 クラスを単独で流して通ることを、代表で 3 クラス（流れ・待ち・時刻）で確かめる（DD-6）。
  - 承認済みの確かめのコマンド `./gradlew verifyQuick :backend:internalDbPostgresTest`（colima の 2 つの環境変数と `caffeinate -dims` つき）が通る。普段の `verify` に入る U3 のテストは構造のテスト 1 クラスで、`:backend:test` の結果の XML で 1 秒未満を記録する（DD-9）。
  - H2 のテストと部品に差分が無いこと、`build.gradle.kts`・`ci.yml`・依存・lockfile・`src/main` に差分が無いことを `git diff --stat` で確かめる（BR9.3・BR9.4・NFR6.1・NFR8.1）。
  - 本体の差が見つかったものは、期待を弱めずに一覧にして `code-summary.md` の「依頼者に確かめたいこと」に書く（U2 に戻す候補）。

- [x] **Step 11: 記録（`unit-test-instructions.md`・`code-summary.md`・`traceability.json`・`source-manifest.json`）**
  - `traceability.json` は `aidlc engine sensor-traceability --stage code-generation --unit u3-postgres-behaviour-tests --output-path <絶対パス> --write-upstream` で作り、上流の ID（機能設計の BR・NFRx.y・設計の決定）を、各テストのクラスに対応づける。測っていない目標は OK にしない（Deferred にして持ち主の段を書く）。FR5.1 には BR1〜BR9 の確かめを結びつける。
  - `source-manifest.json` に、この段で作った・変えたパスをすべて書く。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Methodology evidence**: 今の `team.md` で確定した決まりを引き継いだ。各 Bolt で層ごとに実装を書いてから同じ Bolt の中でその層のテストを書いて流す形で、これまでの Intent もこの形で進めてきた。今回の Intent（内部DB に PostgreSQL を足す）で進め方を変える理由は見つかっていない。\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる（外すパッケージは無い）。全体の合計で判定していた既存のパッケージの一覧（`backend/build.gradle.kts` の `packagesJudgedByTotal`。最後は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`））は、Intent 261108-ci-checks-release で `common.health` にテストを足して下限を満たし、一覧とそれを使った除外の仕組みを消した。パッケージを外す一覧を再び作らず、下限を満たさないパッケージはテストを足して満たす。（2026-11-08 Intent 261107-verify-quick-local で書き換え）（2026-11-09 Intent 261108-ci-checks-release で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB を使うテストは組み込みの H2 を基本とし、コンテナではなく本番と同じ組み込みの H2 で行う。H2 での結合テストの全件は今のまま残す。内部DB を PostgreSQL で動かす経路に固有の振る舞いを確かめる結合テストは、H2 の全件とは別のバリエーションとして作り、版を固定したイメージのコンテナで起動した実際の PostgreSQL で行う（固い制約は `project.md` の Mandated）。このテストは明示の指定（Gradle の指定。名前は設計で決める）を付けたときだけ流し、通常の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）では流さない。流す場面は決めず、必要と判断したときに明示して流す（CI で流すときも明示の指定を付けた実行に限る。形は設計で決める）。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストと、内部DB の PostgreSQL の結合テストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) （2026-11-14 Intent 261114-internal-db-postgresql で書き換え。H2 を基本とし、PostgreSQL に固有の結合テストを明示したときだけ流す形にした）\n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:552cb5658f2cb47e1e82a7327ac8ad923769dea2d1ef59be8f82d75eefe758af",
  "contract_sha256": "sha256:a556f2389fe8ebabfea24044e818005bb3c9a4fafaa7645490018229aa364acb"
}
```
