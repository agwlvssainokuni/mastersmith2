# 信頼性の要件 — u3-postgres-behaviour-tests

上流の要件 NFR4（信頼性）を、この単位の `NFR4.x` に分けます。この単位は、PostgreSQL で上限なく待ち続ける経路が無いことを確かめる側で、確かめ自体が不安定にならないことが要件です。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR4.1 | PostgreSQL で上限なく待ち続ける経路が無いことを、書き込み・行の排他の読み取り・行き詰まり・SKIP LOCKED の 4 経路で確かめる（BR3.1〜BR3.4）。どの経路も、観測の時間の上限（10 秒）の内に失敗または続行で終わる。 | `PgWriteWaitIT`・`PgRowLockWaitIT`・`PgDeadlockIT`・`PgSkipLockedLoginIT` | 要件 NFR4、FR3.3 |
| NFR4.2 | 待ち合わせは `pg_stat_activity` / `pg_locks` の観測で行い、実時間の `sleep` に頼らない。観測の待ちには時間の上限（10 秒）を付け、超えたらテストを失敗にする（無限に待たない）。 | テストの部品（`ConflictProbe`）の単体の確かめと、コードの読み取り | `team.md` の Testing Posture（`sleep` に依存しない） |
| NFR4.3 | 保持側の接続は、成功・失敗のどちらでも必ず `ROLLBACK` して閉じ、待ちの状態が後のテストに残らない。 | `@AfterEach` の後に `pg_stat_activity` で待ちが残らないこと | 要件 NFR4 |
| NFR4.4 | 待ちの設定（`lock_timeout`・`user.timezone`）を変える確かめは、クラスごとの専用のアプリとデータベースで行い、共有のアプリに影響させない。JVM 全体の設定を変えるテスト（時刻）は、他のテストと別の JVM または別のクラスに分け、変えた値を後で必ず戻す。 | 時刻のテストが、流れのテストと同じ JVM の共有のアプリに触れないこと（構造のテストまたはコードの読み取り） | 要件 NFR4、`team.md` の「不安定なテスト」 |
| NFR4.5 | テストの順序・並列・前のテストの状態に頼らない。1 クラスだけを単独で流しても、全体を流しても結果が同じ。 | `-PitTests` 相当の絞り込み（`--tests`）でクラスを単独に流して通ること | `team.md` の Testing Posture |
| NFR4.6 | コンテナの実行環境が無いときの扱いは U1 の `InternalDbPostgresRuntimeCheck` のまま（手元は警告を出して飛ばし、CI は失敗にする）。U3 のテストも `@InternalDbPostgresIT` で同じ扱いを受ける。 | 構造のテストが、U3 のクラスに `@InternalDbPostgresIT` が付くこと | 要件 FR5.3 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR4・FR3.3・FR5.3）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md`（BR3・BR9）
- `aidlc/spaces/default/memory/team.md`（Testing Posture）

## Assumptions & Open Questions

- [assumption] 時刻のテスト（JVM の `user.timezone` を変える）を別のクラス（クラスごとのアプリ）に分け、変えた値を `@AfterAll` で戻す形で足りる。足りないときは Gradle の別の JVM に分けることを NFR 設計で決める（機能設計のレビュー R-01 の持ち越し）。
- [assumption] 行き詰まりで犠牲になる側をアプリ側にそろえる起こし方（アプリが先に行を取り、保持側が後から待つ順）は、NFR 設計の前の使い捨ての試しで確かめる（機能設計のレビュー R-02 の持ち越し）。
