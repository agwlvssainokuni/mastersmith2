# セキュリティの試験の手順（security-test-instructions）

この Intent は、JMX の操作の道具を足し、HikariCP の MBean を既定で有効にした。新しい入口（HTTP の API）は作らない（依頼者の決定 Q2: A）。統合の前の関門の検査に加えて、JMX の公開の範囲と、道具の出力に秘密情報が無いことを確かめる。

## 1. 検査（verify の中）

- 秘密情報の検出（Gitleaks）、Java の静的解析（SpotBugs＋FindSecBugs。priority 1 と `SQL_` の指摘で止める）、依存関係の脆弱性（OSV-Scanner、実行時の依存は High 以上で止める）。
- コマンドは `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（build-instructions.md）。

## 2. JMX の公開の範囲（NFR3 の代わりの守り）

- `application.yaml` に遠隔の JMX（`com.sun.management.jmxremote.*`）が無く、`compose.yaml`・`docker/perf/compose.yaml` の `ports` に JMX の番号が無いこと（読み取りで確かめる）。
- 道具はアプリのコンテナと PID・ネットワークの名前空間を共有し、同じ利用者の番号（10001）で attach する。操作できるのは、この PC で Docker を使える人だけ。

## 3. 道具の出力（NFR2）

- `docker/hikari-pool.sh status`・`compact` の出力が、接続の本数・ファイルの大きさ・時間だけで、パスワード・トークン・接続先の資格情報を含まないこと（使い捨ての環境の出力 `build/perf-results/bt/storage/postgres/compact.txt`・`compact-login.txt` を読む）。

## Sources

- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（Testing Contract・D1〜D9・Build and Test に引き継ぐこと）
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
- 生の結果（コミットしない）: `build/perf-results/bt/`
