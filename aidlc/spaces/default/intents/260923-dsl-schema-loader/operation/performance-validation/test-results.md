# 負荷の試験の結果（test-results）

`load-test-plan.md` の計画で、2026-09-25（UTC では 2026-09-24T17 時台）に行った結果。生データは `build/perf-results/pv-setup/`（環境の作成と1回ずつの時間）と `build/perf-results/pv/`（k6 の結果・記録）にある。

## 1. 条件

| 項目 | 値 |
|---|---|
| 版 | 試験の時点のコミット `bab11e8`。イメージは配備と同じ `sha256:1585bd4ef3dd…`（`build/perf-results/pv-setup/env.txt`） |
| VM | colima CPU 4・メモリ 6GiB |
| 対象DB | PostgreSQL（使い捨て、100 テーブル × 100 カラムのスキーマ `large`） |
| DSL | 想定の規模: 生成した DSL `generated.yaml`。10MB: `valid-10mb.yaml`（`perf/make-large-dsl.mjs` で作ったもの） |
| 配備したアプリ | 02:29 に止め、02:55 に起動し直して 9 秒で healthy、`{"status":"UP"}`（止まっていたのは約 26 分） |

## 2. NFR1.10 — 軽い API の 95 パーセンタイル

### 2.1 `dslCycle`（適用・破棄、1人・180 秒・上限 2g）

| 指標 | 95 パーセンタイル | 最大 | 件数 |
|---|---|---|---|
| 適用（`dslApply`） | **6.9ms** | 10.0ms | 135 |
| 破棄（`dslDiscard`） | **5.1ms** | 28.7ms | 135 |
| 1周（投入2回を含む） | 1,481ms | 2,248ms | 135 |

- 要求 811 件の失敗 0、`checks` 810 件すべて成功、k6 の終わり方 0。閾値（`p(95)<1000`）は越えなかった。
- 135 周で、履歴は上限の 20 件に達した（その後は古いものから消える）。
- 内部DB のファイルは、始めの 942,706,688 バイトから終わりの 2,402,402,304 バイトに増えた（U4-STORAGE-RUN。投入のたびに増え、動いている間は縮まない。4節）。

### 2.2 `dslLight`（今の状態・履歴、同時 5・120 秒・上限 2g）

先に、想定の規模の DSL を投入してプレビュー中にした（`201`、0.94 秒）。適用中は `dslCycle` の最後に適用した想定の規模の DSL、履歴は 20 件。

| 指標 | 95 パーセンタイル | 最大 | 件数 |
|---|---|---|---|
| 今の状態（`dslStatus`） | **1.1ms** | 53.2ms | 521,123 |
| 履歴（`dslHistory`） | **1.1ms** | 36.9ms | 521,123 |

- 要求の失敗 0、`checks` すべて成功、k6 の終わり方 0。待ち時間を置かずに繰り返したため、秒あたり約 4,300 周（利用の実態よりはるかに多い）。
- 内部DB のファイルが 2.4GB に膨らんだ状態で測った（本文を読まない射影の問い合わせのため、ファイルの大きさの影響は見られなかった）。

### 2.3 参考: スリープで中断した1回目の `dslCycle`

- 1回目は、流している途中の 02:30:36 から 414 秒、PC がバッテリー駆動のまま自動のスリープに入った（`pmset -g log` の `Entering Sleep state due to 'Idle Sleep'` と `Wake from Deep Idle`）。その間の要求の1件が 6分52秒かかり、k6 は予定の 180 秒を過ぎても終わらなかったため、割り込みで止めた（終わり方 105）。
- アプリの GC の記録とログにも、その 412 秒の間の動きは無い（アプリの問題ではない）。スリープをまたがなかった適用（95 パーセンタイル 13.0ms、81 件）と破棄（8.1ms、80 件）は目標の内だったが、判定には 2.1 のやり直しの回を使う。やり直しからは `caffeinate -i` でスリープを防いだ。
- 記録は `build/perf-results/pv/sleep-dslCycle-2g.*`。

## 3. NFR1.12・U4-POOL — 10MB の DSL とログインの重ね（`dslMixed`）

重い側1人（10MB の投入 → プレビューの表示）＋ログイン同時 10、120 秒。どちらの上限も、アプリのコンテナを作り直して（最大の値を消して）から流した。

### 3.1 結果

| 項目 | 上限 2g | 上限 1g |
|---|---|---|
| GC・ヒープの上限 | G1・上限の 75% | Serial・727MB（上限の 75%） |
| 重い側の `checks` | **92 / 92**（46 周） | **4 / 50** |
| ログインの `checks` | **1,227 / 1,227** | **81 / 1,481** |
| 要求の失敗 | 0 / 1,320 | 1,446 / 1,532（94.4%） |
| コンテナ | 動き続けた | **OOMKilled**（exit 137）。起動から 23 秒、負荷をかけ始めて約 13 秒で止められた |
| プロセスのメモリ（`anon`）の最大 | 1,996,275,712 バイト（上限の 93%） | 1,064,235,008 バイト（止まる直前。上限の 99%） |
| `memory.peak` | 2,147,934,208（上限に張り付く。ページキャッシュを含む） | 1,073,741,824（上限） |
| `memory.events` | `max 1652`・`oom 0`・`oom_kill 0` | （止まったため読めない。`docker inspect` の `OOMKilled=true`） |
| ログの ERROR | 0 件 | 0 件（止まる直前まで DSL の操作は `success`） |
| Hikari の待ちの時間切れ（累計） | **0** | 0（止まるまで） |
| 借りるまでの待ちの最大 | 6.0ms（4,019 回） | 読めない |
| 使用中・待ち（約 2 秒ごと） | 使用中の最大 2・待ち 0（上限 30） | 使用中の最大 2・待ち 0 |

- 1g の GC の記録（`build/perf-results/pv/dslMixed-1g-gc.log`）では、Full GC のたびにヒープは 103〜250MB まで下がっており、ヒープそのものは足りていた。止められたのは、ヒープの上限 727MB にヒープ以外（メタ領域・スレッド・コードキャッシュ・直接バッファなど）を足したプロセスの全体が、コンテナの上限 1g を超えたため。前の Intent（260923-colima-spec-up）で、ログインとトークンの更新だけでも 1g では止まることが分かり、配備の既定を 2g にした経緯と同じ形である。
- 2g では失敗は無いが、プロセスのメモリは上限の 93% に達し、`memory.events` の `max` が 1,652 回（上限に当たってページキャッシュを回収した回数）あった。余裕は小さい（Build and Test の U4-STORAGE-RUN の約 92% と同じ傾向）。

### 3.2 1回目の `dslMixed`（上限 2g）で見つかったこと

1回目は、ログイン 737 回のうち 1 回が 500 `INTERNAL_ERROR` になり、ERROR が 1 件出た（負荷をかけ始めて約 1 秒）。

| 項目 | 内容 |
|---|---|
| ログ | `DataIntegrityViolationException`: `login_attempt_states` の主キーの重複（`MERGE INTO login_attempt_states ... WHEN NOT MATCHED THEN INSERT`、利用者 ID 2） |
| 起きた道 | `LoginService` の `lockUserRow` は、ロックの状態の行が無いときに `LoginAttemptStateRepository.createIfAbsent` の MERGE で行を作る。同じ利用者の最初のログインが2つ同時に来ると、両方が「行が無い」と判断して挿入し、一方が主キーの重複で失敗して 500 になる |
| 起きた理由（試験の側） | (1) 試験用の利用者は SQL で内部DB に直接入れたため、利用者を作るときに一緒に作られるロックの状態の行（`LoginAttemptStateInitializer`）が無かった。(2) `perf/k6/scenarios.js` の `loginLoop` は利用者を `((exec.vu.idInTest - 1) % 10) + 1` で選ぶが、`dslMixed` では重い側の VU も番号を使うため、ログインの 10 VU のうち2つが同じ利用者（perf-user01）になった |
| アプリへの影響 | アプリで作った利用者は作るときに行ができるため、この道は通らない。ただし、行の無い利用者（直接入れた利用者など）が同時に初めてログインすると 500 になる。隠れた不具合として記録する（直すかは依頼者の判断。5節） |
| メモリ・プールとの関係 | 無い。重い側 44 / 44 成功、Hikari の待ちの時間切れ 0、借りるまでの待ちの最大 8.1ms、`anon` の最大 1,982,738,432 バイト、OOMKilled なし |

- 3.1 の測定は、試験用の利用者 10 名を先に1人ずつログインさせて行を作ってから流した（台本は変えていない。同じ利用者を2つの VU が使う点は残り、行のロックで順に処理される）。
- 記録は `build/perf-results/pv/run1-dslMixed-2g.*`。

## 4. 見ておくこと

- 内部DB のファイルの伸び（U4-STORAGE-RUN、既知の制約）: `dslCycle` の 135 周（投入 270 回、想定の規模）で 2.4GB まで増えた。アプリを止めると 1 秒で 9,453,568 バイトに詰め直され（exit 143）、起動し直して 8 秒で healthy になった（`build/perf-results/pv/restart-before-mixed.txt`）。README の「DSL の管理」の目安（300MB で起動し直す）どおりの動き。
- 使い捨ての環境の1回ずつの時間（`build/perf-results/pv-setup/summary.md`）は、環境を作るために流したもので、この段の判定には使わない。

## 5. 見つかったことと依頼者に諮ること

| # | 内容 | 案 |
|---|---|---|
| F1 | NFR1.12 は要件の条件 1g では Not Met（OOMKilled）。2g では失敗なし | 要件の「足りなければコンテナの上限の既定の見直しを依頼者に諮る」に当たる。配備の既定は前の Intent で既に 2g にしてある |
| F2 | 2g でもプロセスのメモリが上限の 93% | 10MB の DSL を重ねる使い方の余裕は小さい。U4-STORAGE-RUN と合わせて後の Intent で見直す課題 |
| F3 | ロックの状態の行が無い利用者の同時の最初のログインで 500（3.2） | アプリの隠れた不具合。後の Intent で直す課題 |
| F4 | `dslMixed` の台本が、同じ利用者を2つの VU に割り当てる（3.2） | 試験の台本の不具合。F3 と合わせて直す |

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/performance-validation/load-test-plan.md`・`performance-validation-questions.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/u4-dsl-management/nfr-design/performance-design.md`・`scalability-design.md`
- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/observability-setup/dashboards.md`
- `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`（`lockUserRow`）・`backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java`（`createIfAbsent`）・`backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java`・`perf/k6/scenarios.js`（`loginLoop`）
- 生データ: `build/perf-results/pv-setup/`・`build/perf-results/pv/`（k6 の `*.json`・`*.console.txt`、記録 `*-sample.tsv`・`*-hikari.txt`・`*-state.txt`・`*-app.log`・`dslMixed-1g-gc.log`・`restart-before-mixed.txt`）
- 実行したコマンド: `docker tag`・`docker compose stop app`・`KEEP=1 MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres`・`docker run grafana/k6:2.3.0 run ...`・`curl /actuator/metrics/hikaricp.*`・`docker exec ... cat /sys/fs/cgroup/memory.*`・`docker inspect`・`pmset -g log`・`caffeinate -i`・`docker compose ... down -v`・`docker compose start app`

## Assumptions & Open Questions

- [assumption] 使用中と待ちの数は約 2 秒ごとの読み取りのため、その間の瞬間の値は取りこぼしうる。プールが尽きたかどうかは、累計の待ちの時間切れ（0）と借りるまでの待ちの最大（2g で 6.0ms）で判断した。
