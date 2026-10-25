# NFR の確かめの表（nfr-validation-matrix）

Build and Test から引き継いだ `Unverified` 12 件（`construction/build-and-test/build-and-test-summary.md`）を、目標と物差しを変えずに判定した表です。

- 実測の詳細は `test-results.md`、環境と試験の値は `load-test-plan.md` にあります。
- Source は `aidlc/spaces/default/intents/261016-business-data-views/` を省いた相対パスで書きます。
- 判定の決まり（Q2: A）:
  - 種類ごとに2回流し、両方の回で満たせば Met です。
  - 片方だけ満たさないときは、原因を確かめて3回目を流してから判定します（MySQL の2場面。`test-results.md` 3.3）。

## 1. 引き継いだ 12 件

| ID | Source | 目標 | 実測 | 判定 |
|---|---|---|---|---|
| U1-NFR1.1 | construction/u1-businessdata-read/nfr-requirements/performance-requirements.md | 読み取りの6場面（`bizDefinition` は PG だけ）を 10 VU・3 分、`iteration_duration` の p95 < 1000 ms・`checks` の率 1 | **PG**: 6場面とも2回満たす（最大の p95 は `bizSearchContains` 732.6 ms）。**MySQL**: `bizSearchContains` が 1,627.0・5,513.4 ms（2回目は503 が 37 件）。`bizSearchFirst`・`bizSearchLast` は2回目だけ満たさず、新しい環境の3回目で 302.1・733.9 ms。ほかは満たす。**MariaDB**: `bizSearchContains` 4,698.2・4,708.5 ms（503 が 1・2 件）、`bizSearchLast` 2,026.8・2,417.1 ms、`bizSearchLastSorted` 2,964.3・2,212.3 ms。`bizSearchFirst`（650・603 ms）・`bizDetail` は満たす | **Not Met** |
| U1-NFR1.3 | 同上 | 全体の件数（`bizSearchFirst`）と深い OFFSET（`bizSearchLast`）を NFR1.1 の測りに含めて満たす | 件数を含む `bizSearchFirst` は3種類とも満たす（MySQL は2回目の後に3回目で確かめた）。深い OFFSET の `bizSearchLast` は PG・MySQL では満たすが、MariaDB で2回とも 1 秒を超える | **Not Met**（MariaDB の深い OFFSET） |
| U1-NFR3.2 | 同上 | 対象DB の4場面（First・Contains・Last・Detail）が3種類のどれでも NFR1.1 を満たす | PG は4場面とも満たす。MySQL は `bizSearchContains` が満たさない。MariaDB は `bizSearchContains`・`bizSearchLast` が満たさない | **Not Met** |
| U1-NFR2.1 | construction/u1-businessdata-read/nfr-requirements/scalability-requirements.md | 悪い側の規模（`biz_item` 100,000・`biz_pair` 1,000・`biz_wide` 200）を種で作り、3種類で行の数を記録 | 3種類とも、種の出力で 100,000・1,000・200（`biz_ref` 100）。MySQL・MariaDB は `utf8mb4` を指定した写しの手順で入れた（`test-results.md` 3.0） | Met |
| U1-NFR2.4 | 同上 | 読み取りのプール 5 のまま、10 VU で p95 < 1 秒・待ちの時間切れの累計の増分 0・503 の件数 0 | PG は時間切れ 0・503 0 で、p95 も満たす。MariaDB は `bizSearchContains` で時間切れ 1・2、503 が 1・2 件。MySQL は2回目の `bizSearchContains` で時間切れ 37、`bizSearchLast` で 5（503 が 42 件）。待ちの最大は最大 3,007 ms（記録だけ） | **Not Met** |
| U1-NFR2.5b | 同上 | `bizSearchWide` を 10 VU で、アプリの `memory.peak` < 2g の 90%（1,843.2 MiB）。`anon` の最大を並べる | `memory.peak` の最大は 969.7 MiB（MariaDB の2回目、47.3%）。PG 883.8・942.2、MySQL 890.2・910.8、MariaDB 937.8・969.7 MiB。`anon` の最大は 952.1 MiB。OOMKilled なし | Met |
| U2-NFR1.4 | construction/u2-businessdata-write/nfr-requirements/performance-requirements.md | `bizCreate`・`bizUpdate`・`bizDelete` を 10 VU で p95 < 1000 ms・`checks` の率 1 | 3種類 × 2回とも満たす。p95 の最大は `bizDelete` 57.1 ms（MariaDB の1回目）。`checks` の失敗 0 | Met |
| U2-NFR1.5 | 同上 | `bizWritePoolLimit` を 10 VU・3 分で p95 < 1 秒（409 を合格）と、U2-NFR2.7 の条件 | p95 は PG 12.7・12.5、MySQL 12.3・20.9、MariaDB 17.6・11.4 ms。`checks` の失敗 0。U2-NFR2.7 の条件も満たす | Met |
| U2-NFR3.4 | 同上 | NFR1.4・NFR1.5 の4場面が3種類のどれでも満たす | 4場面 × 3種類 × 2回のすべてで満たす | Met |
| U2-NFR2.7 | construction/u2-businessdata-write/nfr-requirements/scalability-requirements.md | 書き込みのプール 5、10 VU で待ちの時間切れの累計の増分 0・503 の件数 0 | 3種類 × 2回の書き込みの5場面で、時間切れの増分 0・503 0。待ちの最大は最大 1,591.0 ms（MySQL の2回目の `bizCreateLarge`、記録だけ） | Met |
| U2-NFR2.8 | 同上 | `bizCreateLarge`（4 MiB 近い本文）を 10 VU で、アプリの `memory.peak` < 2g の 90%（`anon` を並べる）。対象DB は OOMKilled false・`oom_kill` 0 | アプリの `memory.peak` は PG 632.5・672.5、MySQL 884.3・886.5、MariaDB 713.2・686.6 MiB（最大 43.3%）。対象DB は3種類とも OOMKilled false・`oom_kill` 0。対象DB の `anon` の最大は MariaDB 1,197.0 MiB（**85.01%**）、MySQL 2,426.9 MiB（**90.3%**）で、R-04 の線（85%）を超えた | Met（合格の条件で判定。F2: A。MySQL の 90.3% の扱いは承認の場で諮る） |
| U2-NFR2.9 | 同上 | 監査の行の数と内部DB の大きさを数え、見積もり（1行 約 0.5 KB）と並べる | 書き込みの2回の後に、閉じた複写を読み取りで数えた（下の表）。1行 約 108 B で、見積もりの 約 0.5 KB より小さく、前の段の実測（約 100 B）に近い | Met（記録の目標） |

U2-NFR2.9 の内訳:

| 種類 | 監査の行の合計 | うち `BUSINESS_ROW_*` | `mastersmith.mv.db`（閉じた後） | 1行あたり |
|---|---|---|---|---|
| PostgreSQL | 2,166,909 | 2,166,482 | 233,635,840 B | 約 107.8 B |
| MySQL | 1,468,165 | 1,467,758 | 158,846,976 B | 約 108.2 B |
| MariaDB | 1,804,786 | 1,804,379 | 195,035,136 B | 約 108.1 B |

## 2. 集計

| 判定 | 件数 | ID |
|---|---|---|
| Met | 8 | U1-NFR2.1・U1-NFR2.5b・U2-NFR1.4・U2-NFR1.5・U2-NFR3.4・U2-NFR2.7・U2-NFR2.8・U2-NFR2.9 |
| Not Met | 4 | U1-NFR1.1・U1-NFR1.3・U1-NFR3.2・U1-NFR2.4 |
| Unverified | 0 | — |

- Not Met の4件は、どれも MySQL・MariaDB の読み取りの重い形（部分一致と、MariaDB の深い OFFSET）から来ています。PostgreSQL は全場面が満たしました。
- 原因の見立ては `test-results.md` 5節にあります。U1-NFR1.3 と U1-NFR2.4 の決まりのとおり、目標は緩めていません。次の扱いを承認の場で諮ります:
  - ADR-007 の切り替え先（件数を「多数」と出す）
  - 読み取りのプールの上限を 10 に上げる案
  - 索引の案内
  - 後の Intent での直し

## 3. 申し送り

- **feedback-optimization**:
  - 対象DB のメモリの余裕の見直し（MariaDB の `anon` 85.01%、MySQL の 90.3%、F2: A）
  - README の 2B の文字の組の指定と、流す順（書き込みの後の読み取りがプールを外れる）の見直し
  - 試験の値の表（README・基盤の設計 2.1）と実測の表の大きさの差
- **承認の場で諮ること**:
  - Not Met の4件の扱い
  - MySQL の `anon` 90.3% の扱い
  - README の直しをどの段で行うか

## 4. 承認の場の決定（2026-10-25、Approve。示した推奨のとおり）

1. Not Met の4件は Feedback & Optimization に引き継ぎ、直し方（件数を「多数」と出す・読み取りのプールを 10 にする・索引の案内）は後の Intent で決めます。
2. MySQL の対象DB の `anon` の最大 90.3% は F2 と同じ扱いとし、U2-NFR2.8 は合格の条件（OOMKilled false・`oom_kill` 0）で Met とします。余裕の見直しは Feedback & Optimization に引き継ぎます。
3. MySQL の `bizSearchFirst`・`bizSearchLast` は、原因を確かめたうえで作り直した3回目（設計の前提の状態）の結果で「満たす」と読みます。
4. README の 2B の文字の組の指定と、流す順の見直しは Feedback & Optimization で扱います。

## Sources

- `operation/performance-validation/test-results.md`（2〜6節）・`load-test-plan.md`・`performance-validation-questions.md`（Q1〜Q5・F1・F2）
- `construction/build-and-test/build-and-test-summary.md`（Unverified 12 件）
- `construction/u1-businessdata-read/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `construction/u2-businessdata-write/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `construction/u1-businessdata-read/nfr-design/performance-design.md`（試しの1本の時間）
- `build/perf-results/biz-pv/`（元の記録。git の対象外）

## Assumptions & Open Questions

- MySQL の `bizSearchFirst`・`bizSearchLast` は、2回目（書き込みで `biz_new` が増え、表がプールを超えた状態）で満たさず、新しい環境の3回目（設計の前提の状態）で満たしました。原因を確かめたうえで、設計の前提での結果で「満たす」と読みました（`test-results.md` 3.3）。この読み方は承認の場で確かめます。
- 503 の件数は、`checks` の失敗とアプリの WARN（`BUSINESS_TARGET_DB_UNAVAILABLE`）の件数で数えました。どちらもプールの時間切れの増分と一致しました。
- U2-NFR2.8 の対象DB の判定は、決まった合格の条件（OOMKilled false・`oom_kill` 0）で行い、`anon` の 85% の線は諮る線として扱いました（F2: A）。
