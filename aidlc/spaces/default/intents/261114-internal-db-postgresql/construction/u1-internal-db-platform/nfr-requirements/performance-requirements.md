# 性能・検査の時間の要件 — u1-internal-db-platform

上流の要件 NFR1（性能）と NFR5（検査の時間）を、この単位の枝番（`NFR1.x`・`NFR5.x`）に分けます。単位の種類は付いていない（`unit-of-work.md`）ため、5 つの観点の成果物はすべて作ります。

## 前提

- この Intent には Performance Validation の段が無く、PostgreSQL のときの応答時間は測りません（要件 NFR1、Requirements Analysis の Q6: A）。
- 性能の測り方は、この段の質問 Q1: A（`nfr-requirements-questions.md`）で決まりました。移行の所要時間と起動の時間だけを、PostgreSQL の結合テストで測って記録します。k6 は流しません。

## 要件

| ID | 要件 | 確かめ方 | 由来 |
|---|---|---|---|
| NFR1.1 | H2 の内部DB のときの性能・資源の目標（応答時間・メモリなど）は変えない。H2 のときの設定は 1 つも変えず（BR1.7）、接続のたびの文も足さない（BR3.2）。 | H2 の結合テストの全件が通ること（FR5.4）。`perf/README.md` の基準の一覧は、この単位では書き換えない | 要件 NFR1、BR1.7、BR3.2 |
| NFR1.2 | PostgreSQL のときの基準として、(a) 空の PostgreSQL に移行 V1〜V14 を流す所要時間、(b) アプリの起動から `/actuator/health` が UP になるまでの時間、の 2 つを PostgreSQL の結合テストで測って記録する。値は合否に使わない。 | PostgreSQL の結合テストの 1 本が 2 つの時間を測り、結果をテストのログとテストの報告に出す。記録の置き場は Build and Test の成果物（明示して流した回の値） | 要件 NFR1、質問 Q1: A |
| NFR1.3 | k6 の台本・使い捨ての環境（`docker/perf/compose.yaml`）・`perf/README.md` の基準の一覧は、この単位では変えない。PostgreSQL のときの応答時間・メモリは測らず、測っていないことを Build and Test の成果物に書く。 | 差分に `perf/`・`docker/perf/` が含まれないこと | 要件 NFR1、質問 Q1: A |
| NFR1.4 | PostgreSQL のときに足す接続のたびの文（`SET lock_timeout`）は、接続を作るときに 1 回だけ流れ、接続を借りるたびには流れない（BR3.1 の接続プールの設定）。借りる経路に処理を足さない。 | 単体テストで、設定した接続プールの初期化の文が 1 つだけであることを確かめる（BR7.3）。借りる経路の計測はしない | BR3.1、BR3.2 |
| NFR5.1 | Build and Test で測った CI の検査の job の時間が、基準（この Intent の前の中央値 21分32秒、`docs/verification.md`、`it_forks` 3）の 1.1 倍（約 23分42秒）以内で、かつ `team.md` の線（約 42 分）の内であること。 | Build and Test で CI の検査の job の時間を測り、基準と並べて記録する | 要件 NFR5 |
| NFR5.2 | 通常の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）では、PostgreSQL の結合テストを流さない。流れるのは、専用のタスク `:backend:internalDbPostgresTest` を明示したときと、CI の手動の実行で `internal_db_postgres` を真にしたときだけ。 | 通常の `verify` のテストの一覧に、タグ `internal-db-postgres` のクラスが入らないこと（BR6.1・BR6.2・BR6.8） | 要件 NFR5、FR5.2 |
| NFR5.3 | この単位が通常の `verify` に足すテスト（単体テストと H2 の結合テスト。BR7.3 の項目）は、コンテナを使わず、1 本あたり数秒以内で終わる形にする。足した分の時間は、NFR5.1 の 1.1 倍の中に収める。 | Build and Test で、通常の `verify` の時間の増分を測って記録する | 要件 NFR5 |
| NFR5.4 | 手元の全件の `./gradlew verify` の時間と、専用のタスク `:backend:internalDbPostgresTest` の時間を、Build and Test で測って記録する。専用のタスクの時間は合否に使わない（通常の検査に入らないため）。 | Build and Test の成果物に 2 つの時間を書く | 要件 NFR5 |
| NFR5.5 | NFR5.1 を超えたときは、原因を確かめて依頼者に諮る（要件 NFR5 のとおり）。再計測で通るかどうかで進めてよいかを決めることはしない。 | Build and Test の判定 | 要件 NFR5、`team.md` の Testing Posture |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md`（NFR1・NFR5、FR5.2、6 章）[desc]
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/nfr-requirements-questions.md`（Q1: A）[Q1]
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR1.7・BR3.1・BR3.2・BR6.1・BR6.2・BR6.8・BR7.3）
- `perf/README.md`（今の基準の一覧。H2 を前提にした k6 の台本）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture の検査の時間の線）

## Assumptions & Open Questions

- [assumption] `/actuator/health` が UP になる時間の測り方は、PostgreSQL の結合テストの中でアプリを起動して測る形を想定する。実際の置き場（既存の起動確認のテストの流用か、別の 1 本か）はコード生成の計画で決める。
