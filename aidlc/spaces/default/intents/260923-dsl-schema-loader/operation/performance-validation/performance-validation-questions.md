# Performance Validation — 質問

Intent `260923-dsl-schema-loader` の性能の目標を、負荷をかけて確かめるための質問。

## 前提（読み取りだけで調べた結果。2026-09-25）

- この段が持ち主の未検証の目標は 3 件（Build and Test から引き継ぎ。`construction/build-and-test/build-and-test-summary.md` の B 表）。
  - **NFR1.10**: 今の状態・履歴・破棄・適用の 95% が 1 秒以内（想定の規模の DSL が適用中・プレビュー中、履歴 20 件）。
  - **NFR1.12**: 10MB の DSL を1つ処理するとき、**1g のコンテナ**（ヒープ 75%）で、ほかの利用（ログイン）と同時でも失敗しない。要件には「足りなければコンテナの上限の既定の見直しを依頼者に諮る」とある。
  - **U4-POOL**: 適用などの操作で内部DB のプール（上限 30）をログインと共有しても尽きない。
- ほかの時間の目標（NFR1.4〜1.8・1.11 など）は Build and Test で 1回ずつの時間として Met 済み。目標は 95 パーセンタイルと「失敗しない」で決まっており、p50・p99 や秒あたりの件数の目標は無い。利用者は管理者が数名で、DSL の操作は手動でまれ（`u4-dsl-management/nfr-requirements/scalability-requirements.md`）。重い処理（生成・投入・戻し・プレビューの表示）は同時に1つで、重なると 503 `DSL_BUSY`（NFR1.13）。
- 台本は用意済み: k6 の場面 `dslLight`（今の状態と履歴）・`dslCycle`（投入 → 適用 → 投入 → 破棄 → 履歴、1人）・`dslMixed`（10MB の投入と表示の1人に、別々の利用者のログインを重ねる）。使い捨ての環境は `perf/dsl-timing.sh` を `KEEP=1` で起動して作る（手順は `perf/README.md`）。
- Build and Test の参考値: 上限 2g でも 10MB の処理の間の `memory.peak` は 1,732〜1,869MB、動き続けると `anon` が上限の約 92%（U4-STORAGE-RUN）。1g では余裕が無い見込みが高い。1g では JVM の GC が G1 ではなく Serial になる。
- 手元の環境: colima の VM は CPU 4・メモリ 6GiB・ディスク 100GiB。いま動いているのは配備したアプリ（上限 2g）と見本の PostgreSQL（512MiB）。手元の監視は止まっている。VM のメモリには、配備したアプリと使い捨ての環境（アプリ 1g または 2g・対象DB 1つ 最大 768MB）と k6 が同時に収まる。
- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない（project.md の Testing Posture）。

## Q1. NFR1.12 のコンテナのメモリの上限

A. 要件どおり 1g で流し、あわせて配備と同じ 2g でも流す。1g で失敗（OOMKilled や要求の失敗）したら Not Met として記録し（目標は緩めない）、要件の「コンテナの上限の既定の見直し」を承認の場で依頼者に諮る
B. 1g だけで流す
C. 配備と同じ 2g だけで流し、1g は Unverified のまま残す
X. Other (please specify)

[Answer]: A

## Q2. 負荷の大きさと時間

A. 利用の実態（管理者が数名）に合わせる。`dslLight` は同時 5・120 秒、`dslCycle` は1人・履歴が上限の 20 件に達するまで（25 周以上、約 3〜5 分）、`dslMixed` は重い側1人＋ログイン同時 10・120 秒（前の Intent のログインの試験と同じ同時 10）
B. 強めにかける。`dslLight` は同時 10・300 秒、`dslCycle` は1人・300 秒、`dslMixed` は重い側1人＋ログイン同時 10・300 秒
X. Other (please specify)

[Answer]: A

## Q3. 対象DB の種類

軽い API（今の状態・履歴・破棄・適用）は対象DB を使わない。対象DB を使うのは投入とプレビューの表示の照合だけで、3種類の時間は Build and Test で測り済み。

A. PostgreSQL の1種類で行う（配備の見本と同じ種類）
B. 3種類（MySQL・MariaDB・PostgreSQL）それぞれで行う（時間は約3倍）
X. Other (please specify)

[Answer]: A

## Q4. 試験の間の配備したアプリ

A. k6 を流す間は配備したアプリを止め（`docker compose stop app`、見本の PostgreSQL は動かしたまま）、終わったら `docker compose start app` で起動し直して healthy を確かめる。CPU の取り合いで測定の値がぶれないため（`perf/README.md` の手順 0）。止まっている時間は 30〜60 分ほど
B. 配備したアプリは止めない（待機中のため取り合いは小さいが、測った値に影響が混ざりうることを記録する）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. 使い捨ての環境（プロジェクト名 `mastersmith-perf`、仮の署名鍵・仮の管理者・試験用の利用者 10 名、終わったら消す）を `perf/dsl-timing.sh` の `KEEP=1` で PostgreSQL の1種類だけ起動して行う（Q3: A）。本物の配備の環境のデータと監査ログには触れない。
2. k6 を流す間は配備したアプリを止め（`docker compose stop app`、見本の PostgreSQL は動かしたまま）、終わったら `docker compose start app` で起動し直して healthy を確かめる（Q4: A）。
3. 試験の組み立て（Q2: A）:
   - NFR1.10: `dslCycle`（1人、想定の規模の生成した DSL で、履歴が上限の 20 件に達するまで 25 周以上）で適用・破棄の 95 パーセンタイル。その後、想定の規模の DSL を適用中・プレビュー中・履歴 20 件の状態にして、`dslLight`（同時 5・120 秒）で今の状態・履歴の 95 パーセンタイル。
   - NFR1.12・U4-POOL: `dslMixed`（10MB の DSL の投入とプレビューの表示の1人＋別々の利用者のログイン同時 10・120 秒）を、アプリのメモリの上限 **1g** と **2g** でそれぞれ流す（Q1: A）。どちらも、要求の失敗（`checks` の率）・OOMKilled・コンテナのメモリ（`memory.peak`・`anon`）・Hikari の使用中と待ちの数・ログの ERROR を記録する。
4. 判定は Met・Not Met・Unverified のどれか。1g で失敗したら目標を緩めず Not Met とし、要件にあるコンテナの上限の既定の見直しを、承認の場で依頼者に諮る（Q1: A）。
5. 成果物は `load-test-plan.md`（場面・条件・手順）・`test-results.md`（実測）・`nfr-validation-matrix.md`（3件の判定と、ほかの目標の扱い）。結果の生データは `build/perf-results/` の下（リポジトリ管理外）に置く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
