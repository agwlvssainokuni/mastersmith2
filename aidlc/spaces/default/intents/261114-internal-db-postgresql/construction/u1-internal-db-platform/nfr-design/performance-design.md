# 性能・検査の時間の設計 — u1-internal-db-platform

この単位は、実行時の処理を増やしません（接続を作るときの文が 1 つ増えるだけ）。キャッシュ・非同期・ページ送りなどの性能の手段は、この単位に当たらないため使いません。設計の中心は、PostgreSQL の基準の測り方と、PostgreSQL の結合テストを通常の検査に入れない仕組みです。

## 実行時の性能（NFR1.1・NFR1.4）

- H2 のときは何も変えない（接続のたびの文なし。NFR1.1）。
- PostgreSQL のときの `SET lock_timeout = '2s'` は、接続プールが物理の接続を作るときに 1 回だけ流れる（Hikari の `connectionInitSql`）。借りる経路には何も足さない（NFR1.4）。接続プールの上限 30 は変えず、接続は使い回されるため、1 回の文の費用は無視できる。測らない。
- 対象DB のプールには当てない（reliability-design.md の DD-1）。

## DD-6 PostgreSQL の基準の測り方（NFR1.2・NFR1.3、質問 Q1: A）

測るのは 2 つだけ。合否には使わない。

| 測る値 | 測り方 | 出し方 |
|---|---|---|
| 移行の所要時間（空の PostgreSQL に V1〜V14） | `InternalDbMigrationStrategy` が `flyway.migrate()` の前後の単調時計（`System.nanoTime`）の差を取る | INFO の構造化ログ 1 行（キー `migrationMillis`・`migrationCount`）。値は時間と件数だけで、接続先を含まない |
| 起動からヘルスチェックが UP になるまで | PostgreSQL の結合テスト 1 本（`InternalDbPostgresBaselineIT`）が、アプリの起動の前後の時刻と、`/actuator/health` が UP になった時刻の差を取る | テストの標準出力の 1 行 `INTERNAL_DB_PG_BASELINE startupToHealthMs=<値>` |

- `migrationMillis` の INFO は H2 のときも同じ形で出る（H2 との比較に使える）。記録の置き場は Build and Test の成果物（明示して流した回の値を転記する）。
- k6・`docker/perf/`・`perf/README.md` には手を入れない（NFR1.3）。PostgreSQL のときの応答時間・メモリは測らず、測っていないことを Build and Test の成果物に書く。
- 「ヘルスチェックが UP」の測りの置き場は、既存の起動確認のテストを流用せず、PostgreSQL の結合テストの 1 本として足す（コード生成の計画で確定。レビューの指摘への答え）。

| 採らなかった形 | 理由 |
|---|---|
| k6 の代表の場面を PostgreSQL で流す | perf の compose に PostgreSQL の選び方を足す必要があり、U4 の仕事が増える（質問 Q1: B） |
| 測らない | 起動の重さの基準が残らない（質問 Q1: C） |

## DD-7 PostgreSQL の結合テストを通常の検査に入れない仕組み（NFR5.2・NFR5.3・NFR5.4）

- 目印: JUnit のタグ `internal-db-postgres`（注釈 `@InternalDbPostgresIT` が付ける）。
- Gradle: 専用のタスク `:backend:internalDbPostgresTest`（`Test` 型）はこのタグだけを流す。既存の `integrationTest` はこのタグを除く。`verify`・`verifyQuick` の依存には専用のタスクを入れない。
- `-PitTests` の形がタグ付きのクラスだけに当たったときは、0 件で通さず、専用のタスクを案内して失敗する（BR6.3）。終わりの集計で件数 0 を検出して失敗にする（D-8）。
- CI: 手動の実行の入力 `internal_db_postgres`（真偽値、既定 false）が真のときだけ、専用のタスクを流す。`develop` へのプッシュでは流さない（BR6.8）。
- 通常の `verify` に足すテストは、コンテナを使わない単体テストと H2 の結合テストだけで、1 本あたり数秒以内（NFR5.3）。
- 時間の測り方（NFR5.1・NFR5.4）: Build and Test で、CI の検査の job の時間を基準（21分32秒）と並べ、手元の全件の `verify` の時間と専用のタスクの時間を測って記録する。1.1 倍（約 23分42秒）と約 42 分の線を超えたら、原因を確かめて依頼者に諮る（NFR5.5）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/performance-requirements.md`（NFR1.1〜NFR1.4、NFR5.1〜NFR5.5）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR3.1・BR6.1〜BR6.3・BR6.8）・`functional-spec.md`（D-7・D-8）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/nfr-requirements-questions.md`（Q1: A）
- `docs/verification.md`（CI の検査の時間の基準）・`perf/README.md`

## Assumptions & Open Questions

- [assumption] 単調時計の差で測る時間は、ログに出す値としては十分で、検査の合否には使わない。
