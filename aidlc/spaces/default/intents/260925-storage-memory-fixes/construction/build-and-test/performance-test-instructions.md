# 性能・負荷の試験の手順（performance-test-instructions）

この Intent には Performance Validation の段が無いため、持ち主はこの段である（project.md の学び、要件 FR5.1）。計画の「Build and Test に引き継ぐこと」の4点（D1 のメモリの目標、D6 の詰め直しの直後の大きさと時間、詰め直しの最中のログイン、`refresh` で止まらないこと）と `dslCycle` を、使い捨ての環境で確かめる。

## 1. 道具と環境

- k6 `grafana/k6:2.3.0`（`perf/k6/scenarios.js`）、`perf/dsl-timing.sh`（`--storage --compact`）、詰め直しの道具 `docker/hikari-pool.sh`。
- 使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`、対象DB は PostgreSQL と 100×100 のスキーマ）。仮の署名鍵・仮の管理者・仮の利用者を乱数で作り、終わったら `down -v` と一時ディレクトリの削除で消す。
- イメージは `mastersmith:storage-memory-fix`（最大ヒープ 50%）。コンテナの上限は配備と同じ CPU 4・メモリ 2g。
- 試験の間は配備したアプリを `docker compose stop app` で止め、終わったら `docker compose start app` で起動し直す（依頼者の判断）。長い実行は `caffeinate -i`。
- 手順はコミットしない一時の台本 `build/perf-results/bt/run-perf.sh` にまとめた（下の順）。

## 2. 手順

```bash
docker compose stop app
# 1) 10MB の DSL の投入→適用を 40 回、プレビュー1件の後、止めずに詰め直す（FR1.4・NFR1）
STORAGE_ROUNDS=40 MASTERSMITH_IMAGE_TAG=storage-memory-fix OUT_DIR=$PWD/build/perf-results/bt/storage ./perf/dsl-timing.sh --storage --compact postgres
# 2) 計画の前の測定と同じ前段（KEEP=1）→ 試験用の利用者 11 名（ロックの状態の行は作らない）→ コンテナを作り直して dslMixed（VUS=10、120 秒）
#    その間 4 秒ごとに cgroup の memory.current・memory.peak・memory.stat の anon と file・memory.events の max を記録する（FR2.4）
# 3) コンテナを作り直して refresh（VUS=10、60 秒）を2回（止まらないこと）
# 4) 使い捨てのアプリに compact を流し、一時停止を確かめてからログインを送る（D3: 再開まで待って 200）
# 5) 記録を確かめてから片付ける
# 6) dslCycle（VUS=1、120 秒、生成した DSL）。前段は KEEP=1 の dsl-timing
docker compose start app
```

## 3. 合格の条件

| 目標 | 合格の条件 | 要件・決定 |
|---|---|---|
| メモリ | `dslMixed` の `checks` がすべて成功、`OOMKilled` false、`anon` の最大が 1,536MiB 以下、`memory.events` の `max` が 0 | FR2.4・D1（T1） |
| 詰め直しの直後の大きさ | 40 回の後の `compact` の直後のファイルが 250MiB 以下、本文が壊れていない（`restore_all_match` がすべて一致） | FR1.4・D6（G1）・FR1.8 |
| 詰め直しの時間 | 一時停止から再開までが全体の上限 45 秒の内側。実測を README の目安と比べる | NFR1・D5 |
| 詰め直しの最中の要求 | 一時停止の後に送ったログインが、再開の後に 200 | FR1.5・D3（W1） |
| 高い負荷で止まらない | `refresh` の2回とも `OOMKilled` false、`checks` がすべて成功 | FR5・NFR6 |
| DSL の軽い操作 | `dslCycle` の `checks` がすべて成功、適用・破棄の p95 が 1 秒以内 | NFR6 |

## Sources

- 計画: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-plan.md`（Testing Contract・D1〜D9・Build and Test に引き継ぐこと）
- 単体テストの手順: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/unit-test-instructions.md`
- 生成のまとめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-summary.md`
- 生の結果（コミットしない）: `build/perf-results/bt/`
