# 性能の測り直しの記録（Intent 261015-deps-ui-carryover）

`perf/README.md` の「性能の測り直し（Intent 261015-deps-ui-carryover の FR7.2・FR8・FR9.1・FR9.4）」の (a)(b)(c) と、新しい jdk のイメージでの `docker/hikari-pool.sh status` を、2026-10-16（JST）に流した記録。コードとテストは変えていない。結果のファイルは `build/perf-results/`（Git の対象外）にある。時刻は UTC（括弧内は JST）。

## まとめ

| 測り | 値 | 合否 |
|---|---|---|
| (a) ロールの書き込みの待ちの最大（上限 11・VUS 5・2 分 × 5 回） | acquire の MAX の最大 8.02・9.59・4.55・9.96・12.62 ms。時間切れの増分 0（5 回とも）、`rolePoolLimit_500` 0 件（5 回とも） | `Met`（FR8.4） |
| (b) 10 MiB の DSL の投入・悪い形の 5 MiB の権限の YAML の確かめ・適用のヒープ | 投入 201・確かめ 400・適用 400。ヒープの読んだ値の最大 657.4 MB（投入）・682.0 MB（YAML）、最大ヒープ 1,073,741,822 バイト。OutOfMemoryError 0 件、`OOMKilled=false`、終了・再起動なし | `Met`（FR9.4 の OOM・停止の条件） |
| (b) `dslMixed` の `memory.peak / memory.max` | 2,026,926,080 / 2,147,483,648 = **94.4%**（90% 以上）。checks 1,984/1,984（重い側 162・ログイン 1,822）、`OOMKilled=false`、OutOfMemoryError 0 件 | **`Not Met`**（FR9.4 の 90% 未満の条件） |
| (c) ロールの3場面（手順書の順・単独） | どちらも 3 場面の checks の fails 0、閾値の越えなし、setup の止まりなし | `Met`（FR7.2） |
| 新しい jdk のイメージで `hikari-pool.sh status` | 終わりの値 0、出力「使用中 0・待機 30・合計 30・借りる待ち 0、ファイル 0.1MiB」 | 動いた（下の注のとおり、相手のアプリは前の版の JRE） |

- 目標は緩めていない。FR9.3（メモリの上限と最大ヒープの割合を変えるか）は、`dslMixed` が 90% 以上のため、変えないとは決められない。計画（performance-test-instructions.md）のとおり、ここで止めて依頼者に諮る。

## 時刻と止めた時間

| 出来事 | 時刻 |
|---|---|
| 台本の事前の確かめ（配備したアプリを動かしたまま） | 21:43〜21:46Z（06:43〜06:46） |
| 配備したアプリを止めた（`docker compose stop app`） | 21:46:57Z（06:46:57） |
| (a) r1〜r5 | 21:47:07Z〜21:58:45Z |
| (b) ヒープ | 21:59:24Z〜21:59:38Z |
| (b) `dslMixed` | 22:00:08Z〜22:03:09Z |
| (c) (1) role-run.sh（9場面） | 22:03:39Z〜22:31:09Z |
| (c) (2) role-three.sh（単独の3場面） | 22:31:09Z〜22:40:23Z |
| 使い捨ての環境を片付け、配備したアプリを起動した（`docker compose start app`） | 22:41:03Z（07:41:03） |
| 配備したアプリが healthy、`/actuator/health` 200 | 22:41:14Z |

- 配備したアプリを止めた時間は約 54 分（21:46:57Z〜22:41:03Z）。全体は 2 時間の目安の内に終わった。
- 使い捨てのイメージは `mastersmith:deps-ui-carryover`（`docker build`。WAR は develop の HEAD fb6f5e7 の時点で `:backend:bootWar` が UP-TO-DATE）。JVM は `25.0.4.1`（Temurin-25.0.4.1+1）。コンテナの上限は CPU 4・メモリ 2g。VM は CPU 4・6 GiB。

## 台本の事前の確かめ（コード生成のレビュー R-05）

- `zsh -n` で、手順書から heredoc をそのまま取り出した `heap-run.sh`・`role-pool-5.sh`・`role-three.sh` と、前提の `gr-pool.sh`・`role-pool.sh`（sed の置き換えは各1か所）・`role-run.sh`・`heap-funcs.sh`（`typeset -f` で書き出し）の構文が通った。
- VM の空き（available 約 4.9 GiB）を確かめ、配備したアプリを動かしたまま使い捨ての環境を起動して、1回目を短く流した（結果は `build/perf-results/precheck/`。合否には使わない）。
  - `role-pool-5.sh`: 1 回・`ROLE_DURATION=10s` の写しで、metrics・gc・stats・k6 の要約・回の時刻のファイルが埋まった（このときは上限の行を足す前で、既定の上限）。
  - `heap-run.sh`: そのまま流し、投入 201・確かめ 400・適用 400、ヒープの記録と最大ヒープのファイルが埋まった。
  - `role-three.sh`: `ROLE_DURATION=10s` の写しで、3場面の要約が埋まり checks の fails 0。

## (a) ロールの書き込みの待ちの最大（FR8）

上限 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`・`VUS=5`・`ROLE_DURATION=2m`、回ごとに `--force-recreate`。acquire の応答の `baseUnit` は `seconds` で、台本の読み取りの関数がミリ秒にそろえた。

| 回 | acquire の MAX の最大（ms） | その時刻 | 時間切れの増分 | `rolePoolLimit_204` | `_500` | 前後の active | iteration p95（ms） | 回の後の `jvm.gc.pause` の MAX（s） | 回の後の CPU・メモリ |
|---|---|---|---|---|---|---|---|---|---|
| r1 | 8.02 | 21:47:14Z | 0 | 745,497 | 0 | 0・0 | 1.60 | 0.023 | 0.45%・906 MiB |
| r2 | 9.59 | 21:49:40Z | 0 | 648,276 | 0 | 0・0 | 2.10 | 0.014 | 1.08%・688 MiB |
| r3 | 4.55 | 21:53:32Z | 0 | 601,359 | 0 | 0・0 | 2.25 | 0.022 | 0.37%・784 MiB |
| r4 | 9.96 | 21:54:26Z | 0 | 553,121 | 0 | 0・0 | 2.61 | 0.044 | 0.48%・760 MiB |
| r5 | 12.62 | 21:57:47Z | 0 | 535,880 | 0 | 0・0 | 2.73 | 0.027 | 0.66%・867 MiB |

- 5 回すべてで 20 ms 未満のため `Met`。20 ms 以上の回が無いため、FR8.2 の見立ては要らない。5〜20 ms の回（r1・r2・r4・r5）は合格のまま値を記録する（グループの節の決まりと同じ扱い）。
- 前の Intent の値（1回目 23.55 ms、流し直し 8.55 ms）との比べ: 今回の 5 回は 4.55〜12.62 ms。1回目の 23.55 ms がどの条件で起きたかは確かめていない（仮説: 回の値の揺れの範囲の外れ。事実としては今回 5 回で再現しなかった）。
- `rolePoolLimit_409`・`_other` は 5 回とも 0 件（要約に出ない）。回を追うごとに 204 の件数が減っている（745,497 → 535,880）。原因は確かめていない（仮説: 回ごとに作り直しても内部DB のボリュームは残るため、作業ロールの切り替えと割り当ての監査の行が積み上がり、書き込みが少しずつ重くなる）。

## (b) 大きな入力のヒープと memory.peak（FR9.1・FR9.4）

### ヒープ（heap-run.sh）

上限の行を外して配備と同じ既定の接続プールに戻し、`--force-recreate` の直後に流した。

| 測り | 応答 | 時間 | ヒープの読んだ値の最大 | 測りの始め・終わりの値 |
|---|---|---|---|---|
| 10 MiB の DSL の投入（`valid-10mb.yaml`、10,485,760 バイト） | 201 | 1.04 s | 657,396,704 バイト（21:59:31Z） | 418,321,376 → 657,396,704 |
| 悪い形の 5 MiB の権限の YAML の確かめ | 400 | 0.45 s | 682,038,240 バイト（21:59:32Z。確かめと適用の通しの最大） | 657,396,704 → 502,207,456 |
| 同じ YAML の適用（指紋は形だけ正しい 0 × 64） | 400 | 0.38 s | （同上） | （同上） |

- 最大ヒープ（`jvm.memory.max?tag=area:heap`）1,073,741,822 バイト（コンテナ 2g の 50%）。読んだ値の最大は最大ヒープの約 64%。前の Intent の見積もり 256〜384 MB を超える（GC の前の読んだ値で、GC の後の値ではない）。
- `docker inspect`: `running 0 OOMKilled=false restarts=0`。アプリのログの `OutOfMemoryError` は 0 件。
- 悪い形の YAML: 流れの書き方の並びで節 500,000 ちょうど（要素 1,000 個ごとに改行）＋ 1行 1,000 バイトまでのコメントの行で 5,242,880 バイトちょうど。行の最長 2,008 文字（65,536 以下）。前の Intent の悪い形（49.7 万の節＋1.5 MB の1行のコメント）とは行の分け方と埋め草の形が違う。
- 400 の本文の code（`ROLE_TRANSFER_INVALID` の見込み）は、台本が本文を捨てる（`-o /dev/null`）ため確かめていない。状態コードだけで判定した。
- heapwatch の読み取りは1回に 0.5 秒より長くかかり、6 秒で 9〜10 点だった。短い山を取りこぼしうる。

### dslMixed の memory.peak

`--force-recreate` の直後（`memory.peak` を戻した後）に、`VUS=10`・`DURATION=3m`・`DSL_FILE=/data/valid-10mb.yaml` で流した。

| 項目 | 値 |
|---|---|
| `memory.peak` / `memory.max` | 2,026,926,080 / 2,147,483,648 = **94.4%** |
| checks | 1,984 / 1,984（`dslHeavy` 162・`logins` 1,822）、fails 0 |
| ログインの VU ごとの利用者 | 10 行、重なり 0 件 |
| 状態 | `running 0 OOMKilled=false restarts=0`、`OutOfMemoryError` 0 件 |
| 接続プール（終わりの時点） | 時間切れの累計 0、acquire の MAX 6.0 ms（`baseUnit` seconds の 0.006015585） |
| iteration_duration（両側の合計） | p95 1.47 s、max 4.03 s |
| 終わりの後の内訳（`memory.current`・`memory.stat`） | current 2,023,206,912、anon 1,476,595,712、file 525,438,976（inactive_file 525,348,864）、kernel 21,237,760 |
| 終わりの後の JVM | heap used 722,262,336、nonheap used 178,835,152 |

- 判定: 90% 以上のため `Not Met`。OOM・JVM の停止・OOMKilled は無い。
- 見立て（事実）: 終わりの後の `memory.current` は上限の約 94% で、その約 26%（約 501 MiB）は回収できるページキャッシュ（inactive_file）、anon（プロセスのメモリ）は約 1,408 MiB（上限の 68.8%）。`memory.peak` はページキャッシュを含む値で、前の Intent（261004-role-menu）の同じ測りは 1,869 MB（87%）だった。
- 見立て（仮説、確かめていない）: (1) 10 MiB の投入を 3 分くり返す間に H2 のファイルへの書き込みでページキャッシュが積み上がり、cgroup の上限の近くまで回収されないまま残るため、`memory.peak` が 90% を超えた。(2) 新しい土台のイメージ（Temurin 25.0.4.1）と、この Intent で上げた依存の版で、anon（ヒープの外の使い方を含む）が前より増えた。どちらが効いているかは、`memory.stat` を回の間に時系列で記録すれば切り分けられる。
- FR9.3: この結果では「メモリの上限と最大ヒープの割合を変えない」とは決められない。直す形（上限・割合を変える、判定を anon で行うかを決め直す、など）は依頼者に諮る。目標は緩めていない。

## (c) ロールの3場面を手順書の順と単独で（FR7.2）

(1) は `role-run.sh` の 9 場面（`workRoleSwitch` の後に3場面）、(2) は同じ環境のまま `role-three.sh` で1場面ずつ。接続プールは既定の上限。

| 場面 | 流し方 | checks（passes・fails） | iteration_duration の p95（ms） |
|---|---|---|---|
| `workRoleRead` | 手順書の順 | 1,433,684・0 | 2.7 |
| `workRoleRead` | 単独 | 1,542,706・0 | 2.4 |
| `myPermissionsTree` | 手順書の順 | 19,690・0 | schemas 122.8・tables 126.6・columns 125.1 |
| `myPermissionsTree` | 単独 | 21,253・0 | schemas 113.8・tables 117.2・columns 117.4 |
| `roleAssignmentsRead` | 手順書の順 | 1,061,594・0 | 3.7 |
| `roleAssignmentsRead` | 単独 | 1,172,032・0 | 3.2 |

- どちらの流し方も fails 0、閾値の越えなし。k6 の出力に `ROLE_NO_CHANGE`・setup の失敗・error の行は 0 件。`Met`。
- ほかの6場面（参考）: どれも fails 0・閾値の越えなし。p95 は `roleTreeRead` 70.1〜74.7 ms（columns 11.5）、`rolePermissionSave` 21.2、`roleAdminRead` 2.8〜3.6、`roleAdminOps` 2.9、`workRoleSwitch` 3.4、`roleAssignOps` 2.6〜3.1（ms）。
- 使い捨てのアプリのログ（片付けの前）: `perf-gr-` 0 件、`perf-user` 0 件、`Bearer ` 0 件、ERROR 0 行、`OutOfMemoryError` 0 件、WARN 43 行。WARN の内訳は片付けの前に読まず、環境を消したため取り直せない（setup の 409 `ROLE_NO_CHANGE` と (b) の 400 の業務の誤りの WARN の見込みだが確かめていない）。
- 使い捨ての環境の監査の件数は数えずに片付けた（今回の合否の条件に無いため）。

## 新しい jdk のイメージ（hikari-pool.sh status）

- 配備したアプリの起動し直しの後に `./docker/hikari-pool.sh status` を流し、`eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:589ff4cc…` を初めて取得して動き、終わりの値 0。出力は「使用中 0・待機 30・合計 30・借りる待ち 0、ファイル 0.1MiB」。
- 注: 配備したアプリのイメージ `mastersmith:local` の JVM は `25.0.4`（この Intent の土台の更新の前の版）で、道具の JDK `25.0.4.1` から前の版の JVM への attach を確かめたことになる。新しい土台（25.0.4.1 の JRE）のアプリへの attach は、使い捨ての環境を片付けた後だったため確かめていない（Deployment Execution で新しいイメージを配備した後に確かめる機会がある）。
- 注: 出力のファイルの大きさ 0.1MiB は、配備した内部DB の大きさとしては小さく見える。道具の既定の `--db-file` が指す場所と実際のファイルが合っているかは確かめていない。

## 計画との差

- DSL のファイルは作り直さず、2026-10-15 07:08 に Intent 261004-role-menu の Performance Validation の `perf/dsl-timing.sh` が作った `build/perf-results/pv/dsl/postgres/` の `generated.yaml`（7,261,390 バイト）と `valid-10mb.yaml`（10,485,760 バイト）を `build/perf-results/dsl-k6/postgres/` と `role-data/dsl.yaml` に写して使った。`perf/make-large-dsl.mjs`・`perf/dsl-timing.sh` はその後に変わっておらず、行の最長はそれぞれ 58・99 文字で 1行の上限の内。対象DB を起動する手間と時間を省くため。
- `dslMixed` は、手順書の `ui.env`（`perf/dsl-timing.sh` の一時ディレクトリ）の代わりに、同じ3つの値（管理者のメールアドレス・パスワード、試験用の利用者のパスワード）を持つ `k6.env` を渡した。長さは前の Intent の計画と同じ `VUS=10`・`DURATION=3m`。
- 試験用の利用者は 2G の候補 1,000 名・`perf-graop01` に加えて、`perf-roleop01` と `dslMixed` の `perf-user01`〜`11` を同じ SQL でまとめて入れた（合計 1,014 名、初期管理者を含む）。
- (b) と (c) は、(a) で足した上限 11 の行を外し、既定の接続プール（配備と同じ）で流した。手順書は (b) の前の上限を書いていない。
- 手順書の判定の `grep -o '"checks": {[^}]*}'`・`grep -o '"rolePoolLimit_500": {[^}]*}'` は、k6 の要約の JSON が複数行のため何も出さない。代わりに python で JSON を読んで判定した（手順書の直しは別に要る）。
- `rolePrepare` と台本の事前の確かめは、配備したアプリを止める前（並べて動かした状態）で流した。判定の回はすべて止めた後。

## 眠りの確かめ

- 流す前の `pmset -g assertions`: `PreventUserIdleSystemSleep`・`PreventSystemSleep`・`PreventUserIdleDisplaySleep` が 1、caffeinate の表明あり。AC 電源・充電 100%。
- 流した後の `pmset -g log`: 試験の窓（21:43Z〜22:41Z、06:43〜07:41 JST）に `Sleep`・`DarkWake` は無い。直前の出来事は 06:11:19 の `Wake`（DarkWake から FullWake。始める前）。
- 外した回は無い（眠り・失敗による流し直しは無し）。

## 片付け

- 結果のファイル（69 個）とログの件数を確かめてから、`docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v` と一時ディレクトリ（ホームの下、権限 700）の削除を行った。秘密の値（仮の署名鍵・仮の資格情報）は表示していない。
- 配備したアプリは `docker compose start app` で同じコンテナのまま起動し直し、healthy と `/actuator/health` の 200 を確かめた。

## memory.peak の内訳（依頼者の決定 A）

依頼者の決定「A. 内訳を取ってから決める」で、前と同じ条件の `dslMixed` を1回流し、`memory.stat` などを時系列で記録した。設定は変えていない。合否は `memory.peak / memory.max` で判定したまま。結果のファイルは `build/perf-results/peakA-*`。

### 条件と時刻

- 使い捨ての環境を作り直した（新しい仮の鍵・資格情報。試験用の利用者 `perf-user01`〜`11` と `perf-roleop01`、`rolePrepare` で DSL の適用と準備のロール。checks 4/4）。イメージ `mastersmith:deps-ui-carryover`、上限 CPU 4・メモリ 2g、`MaxRAMPercentage` 50、接続プールは既定（上限の行なし）。
- `--force-recreate` の直後に、記録の台本（5 秒ごと）を始め、20 秒後に `dslMixed`（`VUS=10`・`DURATION=3m`・`valid-10mb.yaml`）、終わりの後 60 秒まで記録した。台本の全体を `caffeinate -dims` で包んだ。
- 配備したアプリを止めた時間: 23:01:05Z〜23:06:23Z（08:01:05〜08:06:23 JST、約 5 分）。起動し直した後 23:06:34Z に healthy、`/actuator/health` は 200。
- k6: 23:01:34Z〜23:04:35Z。
- 前回との差: 内部DB は作り直したもの（前回は (a)(b) の後の同じボリュームで、利用者 1,014 名と前の投入が残っていた）。電源は AC からバッテリー（81%）に変わっていた。`pmset -g assertions` は caffeinate を始める前に読んだため `PreventSystemSleep` が 0 と出た（守りの確かめは下の `pmset -g log` で行った）。

### 結果

| 項目 | 値 |
|---|---|
| `memory.peak` / `memory.max` | 1,999,577,088 / 2,147,483,648 = **93.1%**（前回 94.4%） |
| 判定 | **`Not Met`**（90% 以上） |
| checks | 2,074 / 2,074（`dslHeavy` 180・`logins` 1,894）、fails 0 |
| 状態 | `running 0 OOMKilled=false restarts=0`、`OutOfMemoryError` 0 件 |
| iteration_duration の p95（両側の合計） | 1,225.8 ms |

peak が出た時点（記録の上で peak が最後に上がった 23:05:16Z。その時点の値は直前の 23:05:11Z と同じ）の内訳:

| 内訳 | MiB | 上限 2,048 MiB に対する割合 |
|---|---|---|
| `memory.current` | 1,903 | 92.9% |
| anon（プロセスのメモリ） | 1,398 | 68.3% |
| file（ページキャッシュ） | 485（inactive_file 484・active_file 1） | 23.7% |
| kernel | 20 | 1.0% |
| shmem | 0 | 0% |
| JVM の heap used・nonheap used（読んだ値） | 872・167 | — |

時系列の要点（MiB、5〜6 秒ごと。全体は `peakA-memory.tsv`）:

| 時刻 | current | anon | file | heap used |
|---|---|---|---|---|
| 23:01:30Z（負荷の前） | 793 | 789 | 0 | 370 |
| 23:01:41Z（k6 の始めの 7 秒後） | 1,295 | 1,269 | 21 | 806 |
| 23:01:53Z | 1,439 | 1,356 | 75 | 758 |
| 23:02:33Z | 1,669 | 1,365 | 290 | 737 |
| 23:03:13Z | 1,849 | 1,392 | 439 | 660 |
| 23:04:33Z（k6 の終わり） | 1,894 | 1,398 | 475 | 836 |
| 23:05:32Z（終わりの 1 分後） | 1,903 | 1,398 | 485 | 873 |

### 見立て

- 事実:
  - anon は負荷を始めて約 20 秒で 1,356 MiB まで上がり、その後の約 3 分は 1,356〜1,398 MiB でほぼ横ばい（上限の 66〜68%）。heap used は 224〜962 MiB の間で上下し、最大ヒープは 1,024 MiB。
  - file は負荷の間ずっと増え続け（初めの 1 分半は約 5.6 MiB/秒）、終わりに 485 MiB。ほぼすべて inactive_file。負荷が終わった後も減らない。
  - `memory.current` は上限の 2,048 MiB に届かず（最大 1,903 MiB）、ページキャッシュが回収される場面は無かった。peak の 93.1% のうち約 24 ポイントがページキャッシュ。
  - 前回（94.4%）も終わりの後の内訳は anon 1,408 MiB・file 501 MiB で、今回と同じ形だった。
- 仮説（確かめていない）:
  - file は、10 MiB の DSL の投入（3 分で 180 回）で H2 のファイルへ書いた分のページキャッシュで、上限に届けばカーネルが先に回収するため、OOM の危険には直接つながらない。
  - anon の約 1,400 MiB は、負荷でヒープが最大（1 GiB）近くまで確保されたことと、ヒープの外（nonheap 約 167 MiB・スレッドのスタック・GC の管理の領域・ネイティブの確保）の合計で、上限 2g の下での頭打ちの形に見える。

### WARN の内訳（前回の確かめ漏れの取り直し）

環境を消す前に、使い捨てのアプリのログ（今回の `--force-recreate` の後のコンテナ）を数えた。

| 件数 | ロガー | 文言（先頭） |
|---|---|---|
| 1 | `PostProcessorRegistrationDelegate$BeanPostProcessorChecker` | Bean 'stringOrNumberMigrationVersionConverter' of type [org.springframework.boot.flyway.autoconfigure.FlywayAutoConfigur…（起動時） |
| 1 | `Database`（Flyway） | Using H2 2.4.240 which is newer than the version Flyway has been verified with. The latest verified version of H2 is 2.3…（起動時） |

- 合計 WARN 2 行・INFO 217 行・ERROR 0 行・`OutOfMemoryError` 0 件・`perf-user` 0 件・`perf-roleop` 0 件・`Bearer ` 0 件。個人に関する値・トークンは文言に含まれていなかった。
- `dslMixed` の負荷の間の WARN は 0 行だった。前回の WARN 43 行は、`dslMixed` の前に作り直したコンテナで、その後に (c) のロールの 12 場面（role-run.sh と role-three.sh）も流した分。起動時の 2 行と同じものを除いた約 41 行は (c) の間に出たと見られるが、今回は (c) を流していないため内訳は取り直せていない（仮説: setup が受け入れる 409 `ROLE_NO_CHANGE` などの想定内の業務の誤りの WARN）。

### 眠りの確かめ

- `pmset -g log`: 試験の窓（23:01Z〜23:06Z、08:01〜08:06 JST）に Sleep・DarkWake は無い。直前の出来事は 07:44:56 の Sleep と 07:58:02 の Wake（どちらも始める前）。外した回は無い。

### 片付け

- 結果とログの件数を確かめてから `down -v` と一時ディレクトリの削除を行い、配備したアプリを `docker compose start app` で起動し直した。
