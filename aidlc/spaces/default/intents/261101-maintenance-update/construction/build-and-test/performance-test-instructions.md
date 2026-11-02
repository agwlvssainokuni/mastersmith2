# 性能の試験の手順 — Build and Test（Intent 261101-maintenance-update）

要件 FR10.1〜FR10.4。Java 25.0.5・Node 26・依存・コンテナのイメージを上げた後に、判定の場面を3種類の対象DB で1回ずつ流し、前の Intent の値と並べる。この Intent の流れに Performance Validation の段が無いため、持ち主はこの段（`project.md` の学び）。手順の正は `perf/README.md`（「手順」「業務データの場面」「業務データの書き込みの場面」「他テーブル参照の読み取りの場面」「他テーブル参照の保存前の確かめの場面」「`dslMixed` の `memory.peak` と `memory.stat` の記録」）。

## 1. 線と物差し（変えない）

始めに `k6 inspect --include-system-env-vars` で場面ごとの閾値を照らす（`SCENARIO=<場面>` を渡す）。あわせて `./perf/k6/thresholds-test.sh` を流す。

| 判定 | 線 | 物差し |
|---|---|---|
| 業務データの読み取り（`bizDefinition`・`bizSearchFirst`・`bizDetail`） | p95 < 1,000 ms | `iteration_duration{scenario:<場面>}` |
| `bizSearchContains` | p95 < 3,000 ms | 同上 |
| `bizSearchLast`・`bizSearchLastSorted` | p95 < 2,000 ms | 同上 |
| 書き込み（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`）・参照（`refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refCreateChecked`） | p95 < 1,000 ms | 同上 |
| `refPickerContains` | p95 < 3,000 ms | 同上 |
| すべての場面 | `checks` の率 1 | k6 の `checks{scenario:<場面>}` |
| `bizSearchWide`・`bizCreateLarge` | アプリの `memory.peak` / `memory.max` < 0.9 | cgroup |
| `dslMixed` | `memory.peak` / `memory.max` < 0.95（前の線 0.9 も並べる）・`anon` の最大 85% | `perf/dsl-mixed-memory.sh` の `judge`・`judge_090`・`anon_max_ratio` |
| 対象DB のコンテナ | `anon` の最大 / `memory.max` ≤ 92%・`OOMKilled` false・`oom_kill` 0 | cgroup・`docker inspect` |
| プール | 待ちの時間切れの累計の増分 0・503 が 0 件 | `/actuator/metrics` の `hikaricp.connections.timeout`（使い捨てのアプリにだけ公開） |

## 2. 流し方

1. 配備したアプリを `docker compose stop app` で止め、止めた時刻と、止める前のイメージ ID・healthy を記録する。
2. 手元の WAR から試験のイメージを別のタグで作る（`docker build -t mastersmith:perf-maintenance-update .`。`mastersmith:local` と戻し先 `mastersmith:pre-reference-lookup-cleanup` は変えない）。
3. 全体を1つの `caffeinate -dims` で包み、種類ごとに `perf/README.md` の 1B〜6B の順で流す:
   1. 対象DB を業務データの試験の値で起動し、種を入れ、種の確かめ（行の数 100,000・目印の語の当たり・最長 2,000,000 字）。
   2. 使い捨てのアプリに試験用の利用者を入れ、`bizPrepare`。表の大きさ・設定の実際の値を記録し、暖める。
   3. 業務データの読み取りの場面（読み取りのプールは既定の上限 10。前の Intent 261027 の測り直しと同じ）。`bizSearchWide` の前にアプリを作り直す。
   4. 読み取りのプールの上限 5 の行を足してアプリを作り直し、暖め（判定に数えない）の後に `hikaricp.connections.max` が 5 であることを確かめてから、参照の読み取りの5場面・`refCreateChecked`・書き込みの4場面（前の Intent 261029 と同じ）。`bizCreateLarge` の前にアプリを作り直す。
   5. 場面ごとに前後の時間切れの累計を読み、対象DB の `anon` を1秒ごとに記録する。片付けの前に、使い捨てのアプリのログの WARN・ERROR を件数とロガー・code ごとに数え、試験用の利用者の名前・行の値・`eyJ` が 0 件であることを確かめる。
4. `dslMixed` は `perf/README.md` の手順で既定の環境（`KEEP=1 ./perf/dsl-timing.sh postgres`）を作り、`perf/dsl-mixed-memory.sh` で1回流す。
5. 線を割った場面だけ、環境を作り直して2回目を流す。2回目も割れたら Not Met のまま記録し、目標と物差しは変えない。アプリが止まる・極端に遅いなどは、原因をログと状態で確かめてから記録する。
6. 配備したアプリを `docker compose start app` で戻し、healthy・`/actuator/health` 200・イメージ ID が止める前と同じことを確かめる。

**暖めの形の注意**: 暖めに `SELECT * FROM bizperf.biz_item` を対象DB のコンテナの中の道具で受けると、道具が結果の全体を持ってコンテナの上限に当たる。この段の1回目で、PostgreSQL（768m）は psql が強制終了されて `oom_kill` が数えられ、MariaDB（1408m）はサーバーごと OOM で止まった。暖めは結果を1行にする集計の形にする（PG: `SELECT COUNT(*), SUM(LENGTH(t::text)) FROM bizperf.biz_item t`、MySQL・MariaDB: `SELECT COUNT(*), SUM(LENGTH(CONCAT_WS('', id, c_001, …, c_099))) FROM bizperf.biz_item`）。

## 3. 記録（`evidence/`）に置くもの

参照の場面の summary json だけ（前の Intent の Q3: C と同じ）。置く前に伏せる。

```bash
node perf/redact-k6-evidence.mjs <入力の json> <evidence の下の名前>
node perf/redact-k6-evidence.mjs --scan <evidence>
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 <evidence>
grep -rc 'eyJ' <evidence>
```

## 4. 前の値の出どころ

- 業務データの読み取り・書き込み（PostgreSQL・MySQL・MariaDB の全場面）: Intent 261016-business-data-views の Performance Validation（`operation/performance-validation/test-results.md`。読み取りのプールの上限 5）。
- MySQL・MariaDB の `bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`、`dslMixed`、対象DB の `anon`（読み取りの範囲）: Intent 261027-perf-targets-review の Build and Test（`construction/build-and-test/evidence/remeasure-results.md`）。
- 参照の6場面と書き込みの4場面（読み取りのプールの上限 5）: Intent 261029-reference-lookup-cleanup の Performance Validation（`operation/performance-validation/test-results.md`）。

## Sources

- 要件 `inception/requirements-analysis/requirements.md` の FR10.1〜FR10.4
- `construction/code-generation/code-generation-plan.md` の「Build and Test に引き継ぐこと」4（線の表）
- `perf/README.md`・`perf/k6/scenarios.js`
- `aidlc/spaces/default/memory/project.md`・`team.md`（使い捨ての環境・眠り・伏せ字・片付けの前の確かめ）

## Assumptions & Open Questions

- [assumption] 業務データの読み取りの場面は、`perf/README.md` の「業務データの場面」のとおり読み取りのプールの既定の上限（10）で流した。Intent 261016 の値（上限 5）と並べるときは上限が違う。
