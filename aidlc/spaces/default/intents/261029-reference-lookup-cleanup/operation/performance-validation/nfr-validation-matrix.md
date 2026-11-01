# NFR の確かめの表（nfr-validation-matrix）

Build and Test で `Unverified` とし、持ち主をこの段にした行（`construction/build-and-test/build-and-test-summary.md`）を、目標と物差しを変えずに判定した表です。

- 実測の詳細は `test-results.md`、環境と試験の値は `load-test-plan.md` にあります。
- Source は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/` を省いた相対パスで書きます。
- 判定の決まり（計画 5節）: 種類ごとに2回流し、両方の回で満たせば Met。片方だけ満たさない場面は無く、3回目は流していません。
- 物差し（`iteration_duration` の p95・`checks` の率・`memory.peak` / `memory.max`）は変えていません。前の物差しを並べる記録は要りません（物差しの変更が無いため）。
- 結果: **Met 10 件・Not Met 0 件・Unverified 0 件**。

## 1. 判定

| ID | Source | 目標 | 実測（3種類 × 2回） | 判定 |
|---|---|---|---|---|
| U1-NFR2.1 | construction/u1-reference-read/nfr-requirements/performance-requirements.md | `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel` を 10 VU・3 分で `iteration_duration` の p95 < 1,000 ms、`refPickerContains` は p95 < 3,000 ms、どれも `checks` の率 1 | 4場面の p95 の最大は PG の `refLabel` 84.31 ms（1回目）。`refPickerContains` は PG 496.26・475.07、MySQL 1,337.34・1,385.05、MariaDB 1,463.77・1,141.35 ms。`checks` の失敗は全場面 0。k6 の終了コードはすべて 0 | Met |
| U1-NFR2.3 | construction/u1-reference-read/nfr-requirements/performance-requirements.md | 3種類の対象DB（MySQL・MariaDB・PostgreSQL）のどれでも U1-NFR2.1 を満たす。読み取りのプールの上限 5 | 3種類 × 2回のすべてで満たす。上限 5 は暖めの後に 12 回読み、すべて 5.0 | Met |
| AC-U1-AC3.5.4 | 同上（U1-NFR2.3 と同じ受け入れ基準） | 同上 | 同上 | Met |
| U1-NFR2.5 | 同上 | 場面ごとに Met・Not Met を記録する（満たさなくても緩めない） | 場面ごとの値と判定を `test-results.md` 3節に記録した。線は緩めていない | Met（記録の目標） |
| U2-NFR2.21 | construction/u2-reference-write-check/nfr-requirements/performance-requirements.md | 保存前の確かめつきの作成（`refCreateChecked`）の p95 < 1,000 ms・`checks` の率 1 | p95 は PG 14.77・13.36、MySQL 12.59・9.21、MariaDB 9.40・7.45 ms。`checks` の失敗 0。読み取り・書き込みのプールの時間切れの増分 0 | Met |
| U2-NFR2.24 | 同上 | U2-NFR2.21 を3種類のどれでも満たす | 3種類 × 2回で満たす | Met |
| U2-NFR2.25 | 同上 | U2-NFR2.21 の Met・Not Met を記録する | `test-results.md` 3節に記録した | Met（記録の目標） |
| U2-NFR2.23 | 同上 | 既存の書き込みの場面の意味を保つ。`bizWritePoolLimit` の 409 の数と書き込みのプールの待ちを前の Intent と並べ、`bizCreate`・`bizUpdate` が遅くならないことを並べて記録する | `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` は3種類 × 2回で p95 < 1,000 ms・`checks` の失敗 0（最大の p95 は MySQL の `bizDelete` 15.33 ms）。`bizCreate`・`bizUpdate` の p95 は3種類とも前の Intent の値以下（例: PG `bizCreate` 12.48・15.10 → 6.95・8.44 ms）。`bizWritePoolLimit` の 409 は PG 417,901・359,373、MySQL 299,224・318,148、MariaDB 472,164・447,322（前は 295,114・301,998 / 291,399・189,113 / 198,836・328,047）で、ログの `BUSINESS_REFERENCE_NOT_FOUND` と一致。書き込みのプールの時間切れの増分 0、待ちの最大は最大 661.7 ms（MySQL の2回目の `bizCreateLarge`、記録だけ）。比べた表は `test-results.md` 6節 | Met |
| BT-K6-JUDGE | construction/build-and-test/build-and-test-summary.md | 参照の6場面の判定の回（10 VU・3 分、業務データの試験の値で起動、合否の回 2 回） | 3種類 × 2回 × 6場面 = 36 の判定の回をすべて流し、すべて閾値と `checks` を通った | Met |
| BT-MEMPEAK-JUDGE | construction/build-and-test/build-and-test-summary.md（Q5: B） | `bizSearchWide`・`bizCreateLarge` のアプリの `memory.peak` / `memory.max` < 0.9 | `bizSearchWide` は PG 43.8%・43.3%、MySQL 44.5%・44.0%、MariaDB 45.8%・44.1%。`bizCreateLarge` は PG 33.4%・32.6%、MySQL 44.5%・43.9%、MariaDB 36.6%・40.5%。最大は 45.8%（983,625,728 B）。`checks` の失敗 0、OOMKilled false・`oom_kill` 0 | Met |
| REQ-NFR2（性能の分） | inception/requirements-analysis/requirements.md | 上の単位の行がすべて決まれば決まる | 上の行がすべて Met | Met |

（行の数え方: 判定の行は U1-NFR2.1・U1-NFR2.3・U1-NFR2.5・U2-NFR2.21・U2-NFR2.24・U2-NFR2.25・U2-NFR2.23・BT-K6-JUDGE・BT-MEMPEAK-JUDGE・REQ-NFR2 の 10 件。AC-U1-AC3.5.4 は U1-NFR2.3 と同じ判定です）

## 2. あわせて記録したもの（判定の行ではない）

| ID | 中身 | 実測 |
|---|---|---|
| U1-NFR3.3 | 読み取りのプールの待ちの時間切れの累計の前後の差と、借りる待ちの最大 | 全場面で増分 0。待ちの最大は `refPickerContains` で最大 1,412.5 ms（MariaDB の1回目。上限 3 秒の内）、ほかの参照の場面は 5.6〜271.4 ms |
| U2-NFR3.23 | 書き込みのプールの同じ値 | 全場面で増分 0。`refCreateChecked` の後の待ちの最大は 5.9〜108.5 ms |
| BT-K6-POOL5 | 判定の回ごとに読み取りのプールの上限 5 を確かめてから流す（R-03） | 12 回とも 5.0。中止は無い |
| BT-K6-BASELINE | Intent 261016（上限 5）と並べた表 | `test-results.md` 6節 |
| 対象DB のコンテナの線（どの回も） | `anon` の最大 / `memory.max` ≤ 92%・OOMKilled false・`oom_kill` 0 | PG 13.9%・14.0%、MySQL 75.2%・78.5%、MariaDB 81.8%・87.0%。3種類とも OOMKilled false・`oom_kill` 0（前の Intent は MySQL 90.3%・MariaDB 85.01%） |

## 3. 承認の場で確かめたいこと

- MariaDB の対象DB の `anon` の最大が2回目に 87.0% で、前の Intent（85.01%）より高かったこと。線（92% 以下）の内のため合格の条件は満たしています。
- PC は電池で始め、途中で一度電源につながり、終わりには電池に戻っていました（眠りは 0 件で、流し直した回はありません）。

## Sources

- `operation/performance-validation/test-results.md`・`load-test-plan.md`・`performance-validation-questions.md`
- `construction/build-and-test/build-and-test-summary.md`
- `construction/u1-reference-read/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `construction/u2-reference-write-check/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/test-results.md`（比べる相手）
- `perf/README.md`（今の基準の一覧）

## Assumptions & Open Questions

- 503 は `checks` の失敗 0 とログの WARN の code 0 件で「0 件」と読みました。
- AC-U1-AC3.5.4 は U1-NFR2.3 と同じ中身として同じ判定にしました。
