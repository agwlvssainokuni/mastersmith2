# 健全性の確かめ（health-check-report）

配備した版は `develop` の `581b006`（`deployment-log.md`）。手順は `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md` 2節の手順 6〜8。

## 1. コンテナと JVM

| 項目 | 結果 | 基準 |
|---|---|---|
| 健全性 | 入れ替えから約 10 秒で `healthy`、`/actuator/health` は `UP` | `healthy`（最長約 2 分） |
| コンテナの上限 | メモリ 2,147,483,648・CPU 4,000,000,000 | 2g・4（`.env` の値のまま） |
| 最大ヒープ | `MaxRAMPercentage=50.000000`・`MaxHeapSize=1073741824` | 1,024MiB（要件 FR2、コード生成の D2: M1） |
| イメージ | `mastersmith:local` = `sha256:305fddf4aa93f…`、`mastersmith:pre-storage-memory` = `sha256:a9cfa9dc97544…` | 戻し先が元の ID のまま |

## 2. 詰め直しの道具（Q1: A）

```text
[19:31:06.015] 状態: 使用中 0・待機 30・合計 30・借りる待ち 0、ファイル 0.0MiB
[19:31:14.352] 始める前: 使用中 0・待機 30・合計 30・借りる待ち 0、ファイル 0.0MiB
[19:31:14.357] 一時停止しました（この間、内部DB を使う要求は再開まで待ちます）
[19:31:14.362] 接続の破棄を求めました: 使用中 0・待機 0・合計 0・借りる待ち 0、ファイル 0.0MiB
[19:31:14.363] 接続が 0 本になりました（9 ms）。H2 が閉じて詰め直すのを待ちます
[19:31:17.493] ファイルが落ち着きました: 0.0MiB（3137 ms）
[19:31:17.501] 再開しました: 使用中 0・待機 0・合計 0・借りる待ち 0、ファイル 0.0MiB
[19:31:17.502] 結果: 詰め直しました、大きさ 0.0MiB → 0.0MiB、一時停止から再開まで 3149 ms
compact_exit=0
[19:31:18.137] 状態: 使用中 0・待機 20・合計 21・借りる待ち 0、ファイル 0.0MiB
```

- 配備した環境で HikariCP の MBean が見え、`status` と `compact` が動いた。`compact` は終わりの値 0、一時停止から再開まで 3,149 ms。その後も `healthy`。
- 配備した内部DB のファイルは小さく（36,864 バイト。道具の表示は MiB の小数1桁のため 0.0MiB）、大きさの変化は見えない。詰め直しで縮むことは Build and Test の使い捨ての環境で確かめ済み（465.9MiB → 15.2MiB）。
- 道具の出力は接続の本数・大きさ・時間だけで、秘密情報を含まない。

## 3. 既知の制約（運用の注意）

- 内部DB のファイルは、動いている間は DSL の投入と適用、トークンの更新の多い使い方で伸びる（Build and Test の観察）。README の「内部DBのファイルの詰め直し」の手順で、アプリを止めずに `./docker/hikari-pool.sh compact` で詰め直す。詰め直しの操作は監査ログに残らない。
- 最大ヒープを 50% にしたため、Build and Test の `refresh` の毎秒の件数は前の Intent の記録より少なかった（判定には使っていない）。

## Sources

- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/build-and-test/test-results.md`
- 生の結果（コミットしない）: `build/perf-results/deploy/`（`vm-flags.txt`・`tool-*.txt`・`rollback-check.txt`・`app.log`・`verify.log`）
