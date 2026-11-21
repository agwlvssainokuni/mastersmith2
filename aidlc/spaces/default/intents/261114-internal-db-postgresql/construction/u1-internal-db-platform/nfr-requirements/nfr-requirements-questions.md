# NFR Requirements の質問 — u1-internal-db-platform

## 決まっていること（質問にしない）

- 性能・資源の目標は H2 のときのまま変えず、PostgreSQL のときの値は合否に使わない（要件 NFR1、Requirements Analysis の Q6: A）。
- 通常の `./gradlew verify`・`verifyQuick`・CI では PostgreSQL の結合テストを流さない（要件 NFR5、FR5.2）。検査の時間の基準は CI の検査の job の中央値 21分32秒の 1.1 倍（約 23分42秒）以内、かつ約 42 分の線の内。
- 接続情報は環境変数（`.env`）だけから受け取り、ログ・監査ログ・エラー応答に出さない（`project.md` の Forbidden、機能設計 BR5.1〜BR5.7）。起動時のログの漏れは設計で止め、動作中の失敗のログは今のまま README の既知の制約に回す（機能設計 Q1: A）。
- 接続先が空のときは起動を止める（機能設計 Q2: A）。
- 内部DB は H2 と PostgreSQL の 18 系だけを対応の範囲とし、アプリのインスタンスは1つの前提のまま（要件 NFR3・NFR7）。
- 足す依存 `flyway-database-postgresql` は、Apache License 2.0（親 POM の `licenses`）で、依存は `flyway-core` だけ。Spring Boot 4.1.2 の BOM が版を管理する（Maven Central の POM で確認、下調べ 7 節）。PostgreSQL の JDBC ドライバー 42.7.14 は BSD-2-Clause（lockfile に載っている）。
- 起動時の移行は、接続ごとの待ちの上限（`lock_timeout` 2 秒）を設定した状態でも通る形にする（要件 NFR4）。移行の接続だけ上限を外すかは、NFR 設計で決める（機能設計 BR3.4 の持ち越し）。

## 質問

この単位で依頼者に決めてもらう点は 1 つだけです。性能の測り方（要件 NFR1 の「測るかどうかと測り方は NFR 要件の段で決める」）。

### Q1. [性能] PostgreSQL のときの値を、基準として測るか

この Intent には Performance Validation の段がありません。k6 の台本（`perf/k6/scenarios.js`）と使い捨ての環境（`docker/perf/compose.yaml`）は H2 の内部DB を前提にしており、PostgreSQL の内部DB で流すには perf の compose の変更が要ります（それは配備と文書を持つ U4 の範囲の手前になります）。

推奨: A。PostgreSQL の結合テストが空の PostgreSQL への移行（V1〜V14）をどのみち実行するため、その所要時間だけを基準の値として記録すれば、perf の台本や compose に手を入れずに「PostgreSQL のときの起動の重さ」を残せます。応答時間は、合否に使わない決定（Q6: A）のとおり、この Intent では測りません。

A. 移行の所要時間（空の PostgreSQL に V1〜V14 を流す時間）と、起動からヘルスチェックが通るまでの時間だけを、PostgreSQL の結合テストで測って記録する（合否には使わない）。k6 は流さない
B. 代表の k6 の場面（`health`・`loginSuccess`・`bizSearchLast` など）を、PostgreSQL の内部DB で1回流して基準を記録する（合否には使わない）。perf の compose に PostgreSQL の選び方を足す必要があり、U4 の仕事が増える
C. 測らない。測っていないことと、測らなかった理由を記録するだけにする
X. Other (please specify)

[Answer]: A
