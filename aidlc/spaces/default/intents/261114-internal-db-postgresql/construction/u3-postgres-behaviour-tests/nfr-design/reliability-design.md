# 信頼性の設計 — u3-postgres-behaviour-tests

NFR4.1〜NFR4.6 を、テストの待ち合わせと後始末の設計に落とします。この単位はテストだけで、アプリの回復の仕組みは足しません。「DD-n」は設計の決定の番号です（`logical-components.md` が部品の持ち主を対応づける）。

## 使い捨ての試しの結果（質問ではありません）

リポジトリの外で、PostgreSQL 18.6（`postgres:18.6`）の使い捨てのコンテナと pgjdbc 42.7.14 を使って確かめました。機能設計・NFR 要件のレビューからの持ち越し（R-01・R-02 と待ちの測り方）に答えるためです。

- **T1・T2 行き詰まりの犠牲側**: 2 つのトランザクションが互いの行を待つ組で、犠牲になる（40P01 で失敗する）のは、**先に待ち始めた側の `deadlock_timeout`（既定 1 秒）が、輪が閉じた後に最初に切れる側**でした。輪が閉じるのが、先の側の待ちの開始から 1 秒より後なら、先の側は輪が閉じる前に検査を終えているため、後の側が犠牲になる。先の側の待ちの開始から 1 秒以内に輪を閉じると、**先の側が犠牲**になる。5 回の繰り返し（先の側が 0.5 秒で待ち、後の側が 0.9 秒で輪を閉じる）で、先の側が 5 回とも犠牲だった。後の側の `deadlock_timeout` を 30 秒にしても、先の側の待ちが 1 秒を過ぎた後に輪を閉じると、後の側が犠牲になった（先の側の検査が輪の前に終わっているため）。したがって、犠牲側は**入る順ではなく、輪が閉じる時刻と先の側の検査の時刻の前後**で決まる。
- **T3 待ちの観測の遅れ**: 保持側が行を持ち、待つ側が `lock_timeout = 2s` で更新する。待つ側が `pg_stat_activity` に `wait_event_type = 'Lock'` として現れるまで約 0.13 秒（50 ms の間隔での観測）。呼び出しの開始から失敗の戻りまでが 2.15 秒、観測した時点からが 2.02 秒だった。つまり `lock_timeout` は待ちの開始（呼び出しの開始とほぼ同時）から数える。観測の時点から測ると、観測の遅れの分だけ短くなり、下限（2 秒）を割る場合がある。
- **T4 時刻の往復**: JVM の `TimeZone.setDefault` を `UTC`・`Asia/Tokyo`・`America/New_York` に変え、接続のたびに `OffsetDateTime` の時点（アメリカの夏時間の切り替えの境 `2026-03-08T07:30:00Z`）を保存して読むと、3 つとも同じ時点に戻った。さらに PostgreSQL のセッションの `TimeZone` を `Asia/Kolkata` に変えても同じだった。pgjdbc は接続を作るときの JVM の既定の時差を読むため、`TimeZone.setDefault` を接続の前に変え、後で戻す形で足りる。

## 設計の決定

| ID | 決定 | 満たす NFR | 理由 |
|---|---|---|---|
| DD-1 | 待ちの時間は、**待つ側の呼び出しの開始**から失敗が戻るまでを測る（観測の時点から測らない）。観測は、保持側が行を持っていることと、待つ側が待ちに入ったことを確かめるためだけに使い、時間の起点にしない。幅は要件のまま（書き込み: 2 秒以上 3 秒未満、行の排他: 3 秒以上 4 秒未満）で、幅を広げない。下限・上限の両方を呼び出しの開始から測る。 | NFR1.2・NFR4.1 | T3 のとおり、観測の遅れで下限の割れが起きる。呼び出しの開始から測れば `lock_timeout` の数え方と一致する（実測 2.15 秒） |
| DD-2 | 行き詰まりのテストは、**アプリの経路を先に待たせ、待ちに入ったことを `pg_stat_activity` で観測した直後に、保持側が輪を閉じる**。観測から輪を閉じるまでの間に `sleep` を入れない。観測の遅れ（約 0.1 秒）は、先の側の `deadlock_timeout`（1 秒）より十分短く、先の側（アプリ）が犠牲になる（T1 の 5 回の繰り返し）。アプリの側が 40P01 の区分（排他の失敗）で失敗することと、保持側が続けられることを確かめる。保持側が犠牲になったときは、テストの失敗（輪が閉じた時刻が遅れた）として扱い、期待を犠牲側の入れ替えで通さない。 | NFR4.1・NFR4.2 | 犠牲側が入る順でなく輪が閉じる時刻で決まるため、観測を合図に輪を閉じる形にして、実時間の `sleep` に頼らずに決める |
| DD-3 | 時刻のテスト（JVM の既定の時差を変える）は、専用のクラス（クラスごとのアプリ）に分ける。`@BeforeAll` で `TimeZone.setDefault` を変えてからアプリを起動し、`@AfterAll` の `finally` で元に戻す。JVM を分ける別の Gradle のタスクは作らない。戻し忘れは、`@AfterAll` の後の別のテストが読む既定の時差が起動前の値であることを、構造のテスト（`PgTestBoundaryArchitectureTest` の隣の単体のテスト）で確かめる。共有のアプリのクラスは、時刻のテストの前に起動済みでも影響を受けない（接続のたびに読むため、共有のアプリの接続プールの接続は作成時の既定の時差のまま）。ただし、時刻のテストは他のテストと並列に流さない（`maxParallelForks` は 1、テストの並列実行を使わない）。 | NFR4.4・NFR4.5 | T4 のとおり `TimeZone.setDefault` で足りる。別の JVM は起動の時間が増えるだけで、効果が同じ |
| DD-4 | 待ち合わせの道具 `ConflictProbe` は、保持側の接続（`InternalDbPostgresDatabase.connect()`）・待つ側を別のスレッドで走らせる部品・観測（`pg_stat_activity` を 50 ms の間隔で最大 10 秒）を持つ。観測が 10 秒で終わらなければテストを失敗にする。保持側の接続は `try`-`finally` で `ROLLBACK` して閉じる。クラスごとに `@AfterEach` で、待ち（`wait_event_type = 'Lock'`）が残っていないことを確かめる。 | NFR4.2・NFR4.3 | 実時間の `sleep` に頼らず、待ちが後のテストに残らない |
| DD-5 | 共有のアプリ（流れ・違反・型）は、JVM ごとに最初の使用時に 1 回起動する保持の部品（遅延の初期化の持ち主）に置く。JVM の終了の時に閉じ、専用のデータベースを `DROP DATABASE` で消す。クラスごとのアプリは `@BeforeAll` / `@AfterAll`。待ちの設定・時差を変えるテストは必ずクラスごとのアプリを使い、共有のアプリに触れない。 | NFR4.4・NFR4.5・NFR5.3 | 機能設計の BR1.2 と、共有で守れない「全体の状態に触れる流れ」（DSL の適用など）をクラスごとのアプリに分ける。共有に置くかの判断は、流れのクラスごとに、全体の状態に触れるかどうかで決める（Code Generation の計画で一覧にする） |
| DD-6 | 1 クラスを単独で流して通ることを、Code Generation の確かめに含める（`--tests <クラス名>`）。 | NFR4.5 | 順序・前のテストの状態に頼らない |
| DD-7 | コンテナの実行環境が無いときの扱いは、U1 の `InternalDbPostgresRuntimeCheck`（`@InternalDbPostgresIT` に含まれる）のまま。U3 のクラスは `@InternalDbPostgresIT` を付け、構造のテストで確かめる。 | NFR4.6 | 要件 FR5.3 |

## 上流との差

- NFR 要件の NFR1.2 は「観測した時点から時間を測る」と書いた。T3 の結果、起点を**呼び出しの開始**に変えた（DD-1）。要件の文書はレビュー済みのため書き換えず、この差を成果物に残す（`project.md` の Way of Working）。
- NFR 要件の NFR4.4 の「別の JVM または別のクラス」は、**別のクラス**に決めた（DD-3）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-requirements/reliability-requirements.md`・`performance-requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/rules.md`（BR3・BR7・BR9）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/functional-design/reviews/review-01.md`・`nfr-requirements/reviews/review-01.md`
- 使い捨ての試し（リポジトリの外。PostgreSQL 18.6・pgjdbc 42.7.14）

## Assumptions & Open Questions

- [assumption] 行の排他の読み取り（3 秒のヒント）が PostgreSQL で実際に 3 秒台で切れる仕組み（Hibernate のヒントが `lock_timeout` を接続に設定して戻すか）は、アプリの既存の経路の結果で確かめる。外れたときは、本体の差として U2 に戻す（BR9.4）。Code Generation の最初の手順で、使い捨てのコードで確かめる。
- [assumption] 共有のアプリに置く流れと、クラスごとのアプリに分ける流れの一覧は、Code Generation の計画で決める。
