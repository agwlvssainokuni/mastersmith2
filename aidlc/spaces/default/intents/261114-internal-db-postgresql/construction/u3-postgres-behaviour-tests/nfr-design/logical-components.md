# 論理の部品と、NFR の決まりを当てる場所 — u3-postgres-behaviour-tests

この単位が持つ部品は、`src/test/java` の下のテストの部品とテストのクラスだけです。`src/main`・インフラの部品（サーバー・キュー・キャッシュ）は足しません。「DD-n」は `reliability-design.md`（DD-1〜7）・`performance-design.md`（DD-8〜11、DD-1 を参照）・`security-design.md`（DD-12〜18）・`scalability-design.md`（DD-19・20）・`observability-design.md`（DD-21・22）の設計の決定の番号です。

## 部品の一覧と受け持つ NFR

| 部品（目安の名前） | 置き場 | 受け持つ NFR | 設計の決定 |
|---|---|---|---|
| `PgSharedApp`（JVM で共有のアプリの持ち主） | `internaldb/testsupport` | NFR4.4・NFR4.5・NFR5.3・NFR7.2 | 最初の使用時に 1 回起動、JVM の終了で閉じて `DROP DATABASE`（DD-5） |
| `ConflictProbe`（保持側の接続・待ちの観測・待つ側のスレッド） | `internaldb/testsupport` | NFR4.1〜NFR4.3 | 観測は `pg_stat_activity` を 50 ms の間隔で最大 10 秒、`ROLLBACK` して閉じる（DD-4） |
| `WaitWindow`（呼び出しの開始から測る時間の判定） | `internaldb/testsupport` | NFR1.2・NFR5.2・NFR5.4 | 呼び出しの開始から測り、幅を広げない。`INTERNAL_DB_PG_WAIT` の行を出す（DD-1・DD-10） |
| `PortableFixture`（利用者・ロール・グループ・招待・DSL の用意） | `<機能>/testsupport` の下の PostgreSQL 用 | NFR9.1・NFR9.3 | アプリの API・標準の SQL だけ。既存の H2 向けの部品に依存しない |
| 機能ごとの流れ・違反・待ち・書き直し・型のテスト（24 クラス） | `<機能>/postgres` など | NFR1.2・NFR2.x・NFR4.x | 機能設計の一覧のとおり。待ちのクラスはクラスごとのアプリ（DD-5） |
| 時刻のテスト（JVM の既定の時差を変える） | `internaldb/postgres` | NFR4.4 | 専用のクラス、`TimeZone.setDefault` を `@BeforeAll` / `@AfterAll`（DD-3） |
| `PgSecretLeakIT`（PostgreSQL の経路の出力の検査） | `internaldb/postgres` | NFR2.1 | U1 の `InternalDbSecretLeakIT` と同じ形（DD-12） |
| `PgTestBoundaryArchitectureTest`（構造のテスト、`*Test`） | `internaldb/` | NFR3.1・NFR4.6・NFR5.1・NFR6.2・NFR9.1〜NFR9.3 | 下の「構造のテスト」 |

## 構造のテスト（NFR9.1・NFR4.6・NFR6.2・NFR3.1）

- 対象のクラス: `@InternalDbPostgresIT` が付く U3 のクラスと、`internaldb/testsupport`・`<機能>/testsupport` のうち PostgreSQL 用の部品。
- 禁止する依存: `org.h2..`、H2 向けの既存の部品（`H2SessionWaits`・`H2BlockedSessions`・`RoleFixtures`・`UserAdminFixtures`・`BusinessDataWriteTables`・`FailingSettingTrigger`・`TestLoginAttemptBarrier`・`TestInvitationBarrier`・`TestUserAdminBarrier`）、`TestDatabase.url` と `TestDatabase.register`（`TestDatabase.randomSecret` は許す。DD-14）。
- 許す依存（読むだけ）: `HttpTestClient`・`JsonLogRecords`・`MutableClock`・`LogEvents` など H2 に触れない既存の共通の部品。
- 確かめること: (1) 対象のクラスに `@InternalDbPostgresIT` が付く（NFR4.6）、(2) 禁止する依存が無い（NFR9.1）、(3) 文字列 `postgres:` のイメージの記述が U3 のテストに無い（NFR3.1。ソースの読み取りの単体のテスト）。
- 既存の ArchUnit の境界テストは緩めない（NFR9.2）。許す・禁止する一覧に曖昧さが残らないよう、Code Generation の計画に、一覧を表で置く。

## 失敗の範囲（failure domain）と影響の範囲

| 失敗 | 影響の範囲 | 守り |
|---|---|---|
| 待ちの確かめが後に残る（保持側の接続が閉じない） | 後のテストの待ち | `try`-`finally` の `ROLLBACK`、`@AfterEach` で待ちが残らないことの確認（DD-4） |
| JVM の既定の時差の戻し忘れ | 後のテストの時刻 | `finally` の復元と、復元の確かめ（DD-3） |
| 共有のアプリの状態の汚れ | 共有のアプリを使う流れ | 流れごとに自分の利用者を作る。全体の状態に触れる流れはクラスごとのアプリへ（DD-5） |
| 待ちの設定を変えるテスト | そのクラスのアプリだけ | クラスごとのアプリ（DD-5） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/reliability-design.md`・`security-design.md`・`performance-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/functional-spec.md`（テストの一覧）

## Assumptions & Open Questions

None.
