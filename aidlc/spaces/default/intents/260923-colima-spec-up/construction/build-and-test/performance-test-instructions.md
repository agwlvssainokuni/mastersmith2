# 性能の試験の手順（performance-test-instructions）

本 Intent の要件 FR5（ログインの p95 1 秒以内・高い負荷で止まらない）と FR4（F3 の原因の内訳）を、この段で使い捨ての環境に対して確かめる。この Intent の流れには Performance Validation の段が無いため、持ち主はこの段である。

## 1. 道具と環境

- k6 `grafana/k6:2.3.0`（`perf/k6/scenarios.js`、`constant-vus`、同時 10、考える時間なし、場面ごとに 60 秒）。手順は `perf/README.md`。
- 使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`）。仮の署名鍵・仮の利用者（10 名、bcrypt cost 12）を使い、終わったら `down -v` で消す（`project.md` の Testing Posture）。
- 条件: colima の VM は CPU 4・メモリ 6GiB。コンテナの上限は配備と同じ CPU 4・メモリ 2g（`export MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g`）。測定の間は配備したアプリを止める（CPU の取り合いを避ける）。
- NMT: `app.env` に `MASTERSMITH_JAVA_OPTIONS=-XX:NativeMemoryTracking=summary` を足し、JDK のイメージの `jcmd` を PID の名前空間を共有して動かす（`perf/README.md` の「メモリの内訳を測る」）。NMT は応答時間の判定の回とは分ける。

## 2. 回の並び

| 回 | 条件 | 場面 | 目的 |
|---|---|---|---|
| judge | 2g・CPU 4・NMT なし | loginSuccess、loginFailure、refresh | FR5.2 の判定（NFR1・NFR2） |
| judge2 | 同上 | refresh | 止まらないことの再確認（2回目） |
| pre1g | 1g・CPU 4・NMT なし | refresh | 修正の前の上限での再現（F3 が VM を上げても起きるか） |
| nmt-1g | 1g・CPU 4・NMT あり | refresh | FR4.1 の内訳（F3 が起きる条件） |
| nmt-2g | 2g・CPU 4・NMT あり | refresh | FR4.1 の内訳（修正の後の条件） |
| simultaneous | 使い捨て 2g（待機）＋配備したアプリ＋lgtm（待機） | なし | NFR3 の見積もりの確かめ（VM の空きメモリ） |

## 3. 合格の条件

| 目標 | 合格の条件 | 要件 |
|---|---|---|
| ログインの応答時間 | loginSuccess・loginFailure の `http_req_duration` の p95 がどちらも 1,000 ms 以内 | FR5.2、NFR1 |
| 高い負荷で止まらない | refresh を 60 秒流して、コンテナが止まらない（OOMKilled false）、想定と違う応答（`checks` の失敗）が 0 件 | FR5.2、NFR2 |
| F3 の内訳 | 1g と 2g のそれぞれで、ヒープとヒープ以外の使用量の推移（開始時と、止まる直前または終わり）が記録され、見立てが書かれている | FR4.1 |
| VM の見積もり | 3つを同時に動かして VM のメモリに空きがある | NFR3 |

## 4. 結果の扱い

- 結果は `test-results.md` に、前回の値（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/test-results.md`）と並べて記録する（FR5.4）。生の出力は `build/perf-results/260923-colima/`（Git 管理外）に置く。
- 合格の条件を満たさないときは、目標を緩めず、作業を止めて依頼者に相談する（FR5.3）。
- アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する（`project.md` の Testing Posture）。
