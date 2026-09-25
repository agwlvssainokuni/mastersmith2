# テストの結果（test-results）— 内部DB の詰め直し・最大ヒープ 50%・テストの JVM の IPv4

対象のコミットは `fix/260925-storage-memory-fixes` の `ab758ff`（C1〜C5）。実行は 2026-09-25 16:55〜17:23。

## 1. ビルドと全テスト（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`）

| 項目 | 結果 |
|---|---|
| 終わりの値 | 0（BUILD SUCCESSFUL、5 分 19 秒） |
| バックエンドの単体テスト | 733 件（102 クラス）、失敗・誤り・飛ばし 0 |
| バックエンドの結合テスト | 385 件（75 クラス）、失敗・誤り・飛ばし 0（対象DB のテストも飛ばされていない） |
| バックエンドのカバレッジ（JaCoCo） | 行 98.08%（3,941／4,018）、分岐 93.83%（1,369／1,459）。パッケージごとの下限の検証も通過 |
| 画面のテスト | 316 件（47 ファイル）成功 |
| 画面のカバレッジ（v8） | 行 97.86%（961／982）、分岐 93.6%（585／625） |
| Gitleaks | no leaks found |
| SpotBugs の関門 | 止める指摘なし（priority 2・3 の警告は既存のもの） |
| OSV-Scanner | 通過（入力が変わらず UP-TO-DATE。依存は変えていない） |
| E2E（`./gradlew e2eTest`） | 6 件成功（20.2 秒） |

手順書（`unit-test-instructions.md`）のコマンドも1回ずつ流した: `HikariJmxSettingsTest` 4・`H2DefragOnCloseTest` 3・`LoopbackPortCollisionTest` 3 件成功、結合の4クラス（`H2CompactionByPoolSuspensionIT`・`DataSourcePoolIT`・`DslManageRepositoryIT`・`DslAdminApiIT`）の終わりの値 0、`AccessTokenApiIT` のクラスだけのくり返し 10 回すべて成功（各 6 件）。`check-container-limits.sh` は `mastersmith:storage-memory-fix` ですべて期待どおり（最大ヒープ 50%）。`perf/dsl-timing.sh` の説明（36 行）と既定の値（58 行）はどちらも 2g。

## 2. 負荷の試験（使い捨ての環境、`mastersmith:storage-memory-fix`、CPU 4・メモリ 2g）

### 2.1 メモリ（`dslMixed`、VUS=10、120 秒。FR2.4・D1）

| 項目 | 直す前（計画の前の測定、最大ヒープ 75%） | 直した後（最大ヒープ 50%） |
|---|---|---|
| k6 の `checks` | 1,254 件すべて成功 | 1,304 件すべて成功（`http_req_failed` 0／1,305） |
| 全要求の p95・最大 | 1,436 ms・4,081 ms | 1,270 ms・3,730 ms |
| `anon` の最大 | 1,893.5MiB（92.5%） | **1,384.9MiB（67.6%）** |
| `memory.events` の `max` | 1,535 回 | **0 回** |
| `memory.peak` | 2,048.7MiB | 1,757.2MiB |
| `OOMKilled` | false | false |
| GC（120 秒） | 316 回、確保の失敗 2 回 | 590 回、完全な GC 3 回、確保の失敗 43 回 |

- 直した後は、前段（`dsl-timing.sh`）の後に内部DB の状態を同じにし、コンテナを作り直してから流した（`memory.peak` を消すため。project.md の学び）。直す前の回は NMT を有効にしていた（約 10MiB）。
- ヒープが小さくなった分、GC の回数と確保の失敗が増えたが、要求の失敗は無く、p95 は短くなった。

### 2.2 内部DB の詰め直し（`--storage --compact`、`STORAGE_ROUNDS=40`。FR1.4・NFR1・FR1.8）

| 項目 | 結果 |
|---|---|
| 40 回の投入と適用＋プレビュー1件の後 | 465.9MiB（488,497,152 バイト）。1回あたり約 10.8MB で伸び続けた（直す前と同じ。操作しなければ縮まない） |
| `compact` の直後 | **15.2MiB**（`storage.tsv` の after-compact 15,921,152 バイト） |
| 一時停止から再開まで | **3,537 ms**（0 本になるまで 5 ms、ファイルが落ち着くまで 3,527 ms）。道具の全体 4.6 秒 |
| データ | 詰め直しの前後で一致（`compact_data_intact=yes`）、直後も healthy。止めて起動し直した後の履歴 20 版の戻しは 20／20 一致 |
| 詰め直しの間の `anon` | 1,458MiB → 1,453MiB（ほぼ変わらない） |

- この本文（10MB の DSL）は圧縮がよく効く。圧縮の効かない本文では、計画の前の測定（3.2 節、乱数の本文 21 件）で閉じた後 210.4MiB、コード生成の結合テスト（乱数 2MiB×24 回）で 46.7〜48.8MB → 8.4MB だった。

### 2.3 詰め直しの最中の要求（FR1.5・D3）

- 使い捨てのアプリに `compact` を流し、「一時停止しました」を確かめてから送ったログインは、**5.1 秒待って 200**（`suspended_before_login=yes`）。
- このときの詰め直しは 1,318.1MiB → 111.9MiB、一時停止から再開まで 4,881 ms（下の 2.4 の `refresh` の後のため、ファイルが大きい）。

### 2.4 高い負荷（`refresh`、VUS=10、60 秒×2）

| 回 | `checks` | 毎秒の件数 | p95 | `OOMKilled` |
|---|---|---|---|---|
| 1 | 506,201 件すべて成功 | 8,437 | 2.9 ms | false |
| 2 | 457,781 件すべて成功 | 7,629 | 2.8 ms | false |

- `anon` の最大 1,079.3MiB（52.7%）、完全な GC 0 回。`memory.events` の `max` は 845 回で、ページキャッシュ（`file`）の回収による（`anon` は上限の半分）。
- 参考: 前の Intent（260923-colima-spec-up、最大ヒープ 75%）の `refresh` は毎秒 10,948〜11,295 件・p95 1.8〜1.9 ms だった。条件（内部DB の状態）が同じではないため比べの判定には使わないが、今回は約 25〜30% 少ない。
- 観察: `refresh` の 2 分で内部DB のファイルが 1,318MiB まで伸びた（トークンの更新は1回ごとに内部DB に書く）。ファイルの伸びは DSL だけでなく、トークンの更新の多い使い方でも起きる。`compact` で 111.9MiB に戻った。

### 2.5 DSL の軽い操作（`dslCycle`、VUS=1、120 秒）

- `checks` 546 件すべて成功。適用の p95 8.65 ms、破棄の p95 7 ms（閾値 1 秒）。`OOMKilled` false。

### 2.6 配備したアプリ

- 試験の間だけ止めた（17:04〜17:18、17:19〜17:23）。どちらも `docker compose start app` で同じコンテナ（`mastersmith:local`）を起動し直し、healthy を確かめた。配備したアプリの内部DB と監査ログには要求を送っていない。

## 3. セキュリティ

- JMX の遠隔の接続の設定は無く（`application.yaml` のコメントだけ）、compose の `ports` は `127.0.0.1:8080`・`127.0.0.1:18080`・`127.0.0.1:3000` だけで JMX の番号は無い。
- 道具の出力（`compact.txt`・`compact-login.txt`）に、パスワード・トークン・接続先の文字列は 0 件。

## 4. 失敗と直し

- なし（失敗したコマンドは無い。段の中の直しもしていない）。

## Sources

- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（Testing Contract・D1〜D9・Build and Test に引き継ぐこと）
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
- 計画の前の測定: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/baseline-measurement.md`
- 生の結果（コミットしない）: `build/perf-results/bt/`
