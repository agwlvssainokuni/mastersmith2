# 負荷の試験の結果（test-results）

Intent `261004-role-menu` の Performance Validation の負荷の試験の結果です。計画は `load-test-plan.md`、目標ごとの判定は `nfr-validation-matrix.md` にあります。時刻は UTC です（括弧の中は JST）。値・秘密は書きません。結果のファイルは `build/perf-results/pv/` にあります（git の対象外）。

## 1. 条件

| 項目 | 内容 |
|---|---|
| 版 | `develop` の `954028c`。イメージ `mastersmith:local` = `e0a5825d257c`（配備したものと同じ。使い捨ての環境にもこのイメージを使った） |
| VM | colima、CPU 4・メモリ 6GiB。使い捨てのアプリは上限 CPU 4・メモリ 2g（最大ヒープ 1,024 MB、G1） |
| 配備したアプリ | 22:07:10（07:07:10）に `docker compose stop app`、23:51:17（08:51:17）に `docker compose start app` で起こし、healthy と `/actuator/health` の 200 を確かめた。止まっていたのは約 1 時間 44 分。Mailpit と見本の対象DB は止めていない |
| 眠り | 台本の全体を `caffeinate -dims` で包んだ。始める前の `pmset -g assertions` は `PreventUserIdleSystemSleep`・`PreventUserIdleDisplaySleep` が 1。07:00 JST 以降の `pmset -g log` は `Assertions` の行だけで、`Sleep`・`DarkWake`・`Wake` は 0 件（流し直しの要なし）。PC は電源につないだまま |
| 片付け | 環境 B（DSL）は 22:23:58、環境 A は 23:51 頃に `down -v` と一時ディレクトリの削除で片付けた。`mastersmith-perf` のコンテナ・ボリューム・網と一時ディレクトリが残っていないことを確かめた |

## 2. 環境 B: DSL（22:07〜22:19）

### 2.1 1回ずつの時間（`perf/dsl-timing.sh --ui --lang --busy postgres`、U2 の NFR2.5・NFR2.7・NFR2.8）

対象DB は PostgreSQL の 100 テーブル × 100 カラム（外部キー・コメントあり）。

| 目標（U2 の NFR2.5） | 実測の最大 | 判定 |
|---|---|---|
| 生成の全体 30 秒以内 | 2.69 s（3 回: 2.49・2.69・1.90） | 満たす |
| 投入・戻し・プレビューの表示（照合を含む）10 秒以内 | 投入 1.16 s（10 MiB）、戻し 0.92 s、プレビューの表示 0.21 s。誤りを含む 10 MiB の投入（422）は 0.78 s | 満たす |
| ダウンロード 2 秒以内 | 0.046 s（10 MiB） | 満たす |
| 画面の表示 3 秒以内（照合を含む表示 11 秒以内） | 今の状態 110 ms、プレビューの表示まで 380 ms（サイドバーから・3 回の最大） | 満たす |
| 違いの表の行を開いてから 0.5 秒以内 | 35 ms（100 カラム） | 満たす |
| 10 MiB のファイルを選んでから送り始めるまで 2 秒以内 | 「投入」を押してから送るまで 649 ms、置き換えの確かめから 543 ms | 満たす |

- **既定の DSL の大きさ（U2 の NFR2.7）**: 7,261,390 バイト（4 回の生成で同じ）。上限 10,485,760 バイトの内で満たす。ただし設計の試算の 約 6.3 MB より 約 15% 大きい。
- **英語の文言（U2 の NFR2.8）**: `ui-lang.json` は `pass: true`。仮の管理者の言語を `en` にしてから測り（元は `ja`、終わりに戻した `languageRestored: true`）、`<html lang>` は `en`。5つの段（照合の警告・`SCHEMA_MISMATCH`・誤りの一覧・書式の版 1・メニューの深さ）で、応答の `message` と画面の文字に日本語の文字・全角の記号が無く、画面に応答の `message` がそのまま出た。
- **同時の投入の排他（`--busy`、U2 の NFR2.6 の一部）**: 1 回目の組で 201 と 503 `DSL_BUSY` が1つずつ（`pass`）。終わった後も `OOMKilled=false`・healthy。
- **ヒープ（dsl-v2 の読み直し R-03 の記録）**: 10 MiB の正しい DSL の投入と表示の区間の GC の後の値は 573〜746 MB（8 回の GC）、GC の直前の最大 911 MB。見積もり（256〜384 MB）を超えた。測り方の違い（前の生成・投入の残りを含むヒープ全体の値）も含むため、見積もりの直しは feedback-optimization へ渡す。全体の GC の直前の最大は 1,020 MB、Full GC 0 回、`memory.peak` 1,514 MB、`OOMKilled=false`。

### 2.2 k6 の DSL の場面（U2 の NFR2.6）

| 場面 | 回数 | 判定の値（`iteration_duration` の p95・最大、ミリ秒） | `checks` | 判定 |
|---|---|---|---|---|
| `dslCycle`（VUS 1・3 分） | 681 | 適用 8.6・24.9、破棄 5.9・31.7、履歴 3.7・6.9（投入は記録だけ: 703.5・1,286.0） | 681/681 | 満たす |
| `dslPrepare` | 1 | — （投入と適用 0 回で履歴は既に 20 件。プレビューを置いた） | 1/1 | 準備 |
| `dslLight`（VUS 10・3 分） | 1,220,175 | 今の状態 3.5・120.3、履歴 3.4・116.6 | 1,220,175/1,220,175 | 満たす |
| `dslMixed`（10 MiB の投入とプレビュー 1 人 ＋ ログイン 10 人・3 分） | 1,433 | — | 重い側 116/116、ログイン 1,375/1,375 | 満たす |

- `dslLight` の setup は条件（適用中あり・プレビューあり・履歴 20 件）を確かめてから始めた。
- `dslMixed` は試験用の利用者 11 名をロックの状態の行が無いまま流し、ログインの 500 は 0 件。VU ごとの利用者は 10 行で重なり 0 件。直前にアプリのコンテナを作り直し、終わりの `memory.peak` は 1,869 MB（上限 2g の 87%）、`OOMKilled=false`・再起動 0。接続プールの待ちの時間切れの累計 0、借りるまでの待ちの最大 354.7 ms。
- `http_req_duration` は `iteration_duration` とほぼ同じ値だった（並べて記録）。
- アプリのログ: 仮の管理者のメールアドレス・`Bearer `・`password_hash`・`MVStoreException`・ERROR・対象DB の接続文字列は、どれも 0 件（`dsl-timing` の分と `dslMixed` の分）。

## 3. 環境 A: グループ・ロール・メニュー（22:24〜23:50）

データ: 利用者 1,013（`perf-gr-0001`〜`1000`、`perf-nav-01`〜`10`、操作する管理者 2、仮の初期管理者）、ロール 1,222（準備の 1,000 を含む。悪い側 `perf-role-worst` は 1万カラムに明示）、グループ 1,000 件以上（作成の監査 3,210）、メンバー 1,000 人のグループ、版 2 の DSL（7,261,390 バイト）、メニューの悪い側の DSL（menus 1,000 項目・深さ 5、7,449,128 バイト）と目安の DSL（menus 100 項目・深さ 1）、上限ちょうどの権限の YAML（5,242,880 バイト、19 ロール × 1万カラム）。

### 3.1 応答時間の場面（判定は `iteration_duration` の p95 < 1000 ms と `checks` の率 1）

| 場面 | 回数 | p95（ミリ秒） | 最大 | `checks` | 判定 |
|---|---|---|---|---|---|
| `groupListFirst` | 1,199,637 | 3.5 | 151.8 | すべて合格 | 満たす |
| `groupListLast` | 864,762 | 4.7 | 226.0 | すべて合格 | 満たす |
| `groupDetail`（メンバー 1,000 人） | 45,192 | 62.5 | 675.3 | すべて合格 | 満たす |
| `groupRename` | 1,179,922 | 3.5 | 147.0 | すべて合格 | 満たす |
| `groupCreate`（100 回 × 10） | 1,000 | 4.4 | 10.4 | すべて合格 | 満たす |
| `groupDelete` | 1,000 | 6.7 | 38.4 | すべて合格 | 満たす |
| `groupMemberAdd` | 1,000 | 5.8 | 12.6 | すべて合格 | 満たす |
| `groupMemberRemove` | 1,000 | 5.5 | 20.2 | すべて合格 | 満たす |
| `navMenu`（悪い側の DSL） | 16,439 | 181.1 | 565.1 | すべて合格 | 満たす |
| `tableAccessVisible` | 93,785 | 35.1 | 345.8 | すべて合格 | 満たす |
| `tableAccessDenied`（403） | 99,984 | 31.1 | 248.3 | すべて合格 | 満たす |
| `roleTreeRead`（op schemas・tables・columns） | 35,966 | 97.0・108.9・13.3 | 333.7 | すべて合格 | 満たす |
| `rolePermissionSave` | 135,254 | 24.9 | 261.4 | すべて合格 | 満たす |
| `roleAdminRead`（op listFirst・listLast・one・groupRoles・userRoles） | 1,057,850 | 4.1・4.1・3.2・3.5・3.9 | 249.2 | すべて合格 | 満たす |
| `roleAdminOps`（op create・rename・delete） | 1,060,124 | 3.8・3.8・3.9 | 234.1 | すべて合格 | 満たす |
| `workRoleSwitch` | 875,674 | 4.7 | 186.1 | すべて合格 | 満たす |
| `workRoleRead`（流し直し、4節） | 1,095,025 | 3.9 | 320.7 | すべて合格 | 満たす |
| `myPermissionsTree`（流し直し、op schemas・tables・columns） | 13,810 | 177.3・185.2・183.0 | 645.0 | すべて合格 | 満たす |
| `roleAssignmentsRead`（流し直し） | 841,727 | 4.7 | 180.7 | すべて合格 | 満たす |
| `roleAssignOps`（op assignUser・unassignUser・assignGroup・unassignGroup） | 988,544 | 4.1・3.7・4.3・4.1 | 197.0 | すべて合格 | 満たす |
| `roleExport` | 7,667 | 468.1 | 939.7 | すべて合格 | 満たす |
| `navMenuBaseline`（目安の DSL、最後） | 13,512 | 229.2 | 917.5 | すべて合格 | 満たす |

- 置き場の問い合わせの p95（35.1 ms・31.1 ms）は目標 1 秒の 約 30 分の 1 で、桁違いに小さい。目標を見直すかは後の Intent で決める（`performance-design.md` 3節の申し送り）。
- `navMenuBaseline`（menus 100 項目）の p95 229.2 ms は、悪い側の `navMenu`（1,000 項目）の 181.1 ms より大きかった。1回の要求の読み出しの数がメニューの大きさによらない設計（U5 の NFR2.3b）とは食い違わないが、目安の DSL へ入れ替えた後（権限の YAML の上限ちょうどの適用の後）の流れで、条件は同じではない。原因は確かめていない。
- `http_req_duration` は `iteration_duration` とほぼ同じ値だった（並べて記録）。

### 3.2 権限の YAML の上限ちょうど（U4 の NFR2.4）

直前にアプリのコンテナを作り直した（`memory.peak` を戻すため）。

| 場面 | `iteration_duration` の最大 | 目標 | `checks` | 判定 |
|---|---|---|---|---|
| `roleTransferLargeCheck`（確かめ） | 4,070 ms | 15,000 ms 未満 | 1/1 | 満たす |
| `roleTransferLargeApply`（適用、19 ロールの置き換え） | 8,744 ms | 30,000 ms 未満 | 1/1 | 満たす |

- ヒープ（`jvm.memory.used` の heap、1 秒ごとに読んだ値。読みが重く、取れたのは確かめ 4 点・適用 8 点）の最大は 674 MB。2 場面の後の `memory.peak` は 1,302 MB、`OOMKilled=false`・再起動 0。
- Build and Test の先の測り（BT-Q1c の正しい書き出しの最大: 確かめ 1.11 s・適用 2.35 s）より長い。内部DB が膨らんだ状態（グループ・ロールの場面の後）で測ったためと見るが、原因は確かめていない。

### 3.3 接続プール（`*PoolLimit`）

| 回 | 上限・VUS・時間 | 時間切れの累計の増分 | `_500` | `_409` | `_204` | 借りるまでの待ちの最大 | 判定 |
|---|---|---|---|---|---|---|---|
| `groupPoolLimit` の合否の回 | 11・5・2 分 | 0 | 0 | 0 | 520,334 | 4.24 ms | 満たす |
| `groupPoolLimit` の記録の回 | 10・20・2 分 | 388 | 189 | 163 | 841 | 5,022 ms | 記録（上限に届いた） |
| `rolePoolLimit` の合否の回（1 回目） | 11・5・2 分 | 0 | 0 | 0 | 437,170 | **23.55 ms** | **満たさない**（20 ms 以上） |
| `rolePoolLimit` の合否の回（流し直し） | 11・5・2 分 | 0 | 0 | 0 | 375,014 | 8.55 ms | 満たす（5〜20 ms のため値を記録） |
| `rolePoolLimit` の記録の回 | 10・20・2 分 | 371 | 182 | 51 | 743 | 5,087 ms | 記録（上限に届いた） |

- `acquire` の `baseUnit` は `seconds` で、1,000 倍してミリ秒にそろえた。どの回も試験の前後の使用中の接続（`active`）は 0。
- **`rolePoolLimit` の合否の回の 1 回目**: 待ちの最大が 23.55 ms になった。5 秒ごとの記録では、始めてから約 12 秒（23:13:14〜23:13:21）に 7.70 ms → 23.55 ms に上がり、その後は最大の窓の中で同じ値のままだった。時間切れ・500・409 は 0。上限を変えてアプリのコンテナを作り直した直後の始まりの1回と見るが、確かめていない（ログに ERROR は無い）。`project.md` の決まりどおり、同じ環境でアプリのコンテナを作り直して1回流し直したところ 8.55 ms で、再現しなかった。目標（20 ms 未満）は緩めず、1 回目を `Not Met` として記録し、扱いは承認の場で決める（`nfr-validation-matrix.md`）。
- 記録の回はどちらも上限に届いた（待ちの最大 10 ms 以上）。VUS 40 の流し直しは要らなかった。時間切れ・500・409 は、書き込みの2本使いの既知の制約の大きさとして記録する（監査の書き込みの失敗の ERROR を伴う。3.5）。

### 3.4 警報の確かめの回（Q6: A、23:38〜23:50）

lgtm（`grafana/otel-lgtm:0.34.0`）を先に使い捨ての環境の網に別名 `lgtm` でつなぎ（23:38:15 に healthy）、その後に使い捨てのアプリを上限 4・外部エクスポート・トークン 15 分で作り直した（`hikaricp.connections.max` が 4）。警報の決まりのファイルはリポジトリのものをそのまま読み込ませ、試験の前はすべての題が `inactive`。`rolePoolLimit` を VUS 12・5 分（23:39:05〜23:44:33）流し、終わった後も 5 分（〜23:50:01）、30 秒ごとに読んだ。

| 警報 | 初めて `firing` になった時刻 | `firing` の読みの数 | 判定 |
|---|---|---|---|
| `ms-audit-fail`（監査の書き込みの失敗） | 23:39:38（始めてから約 33 秒） | 19 | 鳴った |
| `ms-error-logs`（ERROR のログの増加） | 23:40:44（約 1 分 39 秒、その前は `inactive`） | 16 | 鳴った |
| `ms-pool-pending`（コネクションプールの待ち） | 23:41:49（約 2 分 44 秒。23:40:44〜23:41:17 は `pending`） | 7 | 鳴った。終わった後の 23:45:39 に `inactive` へ戻った |

- k6: 860 回、`rolePoolLimit_204` 401・`_409` 30（すべて `ROLE_NO_CHANGE`）・`_500` 429。時間切れの累計の増分 627、借りるまでの待ちの最大 5,015.5 ms（`connection-timeout` 5 秒）。アプリの ERROR は「想定外のエラーが起きました」429 件と「監査イベントの記録に失敗しました」198 件。
- メモリの最大: 使い捨てのアプリ 1.044 GiB、lgtm 1.074 GiB。
- **BUSY**（`ROLE_BUSY`・`GROUP_BUSY`）は 0 件で、L3 と L4 の結び付きは確かめられなかった（`Unverified`、feedback-optimization へ渡す）。

### 3.5 秘密・名前の件数と監査の件数

- アプリのログ（コンテナを作り直す前ごとに数えた。9 回）: 試験用の利用者の名前（`perf-gr-`・`perf-nav-`・`perf-roleop`・`perf-graop`・`perf-admin`）、`Bearer `、`password_hash`、`MVStoreException`、`enteredEmail`、WARN・ERROR の中のテーブル名（`t_0`）は、どの回も 0 件。ERROR は、接続プールの記録の回（388 件・371 件）と警報の確かめの回（627 件）にだけあり、どれも上限に届かせた回の想定の失敗（想定外のエラー・監査の書き込みの失敗）。
- k6 の結果のファイルに、試験用の利用者の名前とトークンは 0 件。
- 監査（アプリを止めて `ACCESS_MODE_DATA=r` で読み取りだけ）: `GROUP_RENAMED` 1,179,922（`groupRename` の回数と同じ）、`ROLE_PERMISSION_CHANGED` 135,254（`rolePermissionSave` と同じ）、`ROLE_RENAMED` 353,376・`ROLE_DELETED` 353,371（`roleAdminOps` の op と同じ）、`ROLE_TRANSFER_APPLIED` 2（準備と上限ちょうどの適用）、`WORK_ROLE_SWITCHED` 1,359,489 など。`FAILURE` は `GROUP_MEMBER_ADDED` 67・`GROUP_MEMBER_REMOVED` 81・`ROLE_ASSIGNED` 25・`ROLE_UNASSIGNED` 32 で、上限に届かせた回の 409 に当たる。数えた結果は `build/perf-results/pv/audit-counts.txt`。

## 4. 計画との差（流し直し）

- **`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead` を流し直した**。手順書の順（1つの環境でロールの場面を続けて流す）では、前の場面が試験用の利用者 `perf-gr-0001`〜`0010` に割り当てたロールが残り、次のとおり判定できなかった。
  - `workRoleRead`: 応答はすべて 200 だが、台本の確かめ（ロールがちょうど 2 つ）が前の場面の割り当てで合わず、`checks` 0/1,151,455。
  - `myPermissionsTree`・`roleAssignmentsRead`: setup で悪い側のロールを割り当てるときに 409 `ROLE_NO_CHANGE`（`roleAdminRead` の setup が `perf-gr-0001` に割り当て済み）になり、始まらなかった。
- 手当て: 台本と目標は変えず、同じ環境（内部DB が膨らんだまま）で、場面の前ごとに `perf-gr-0001`〜`0010` に直接割り当てたロールを管理の API で外してから（外した数: 72・0・20・10・10）、3つの場面を流し直した。外した操作は使い捨ての環境の監査に `ROLE_UNASSIGNED` として残る。
- 台本の setup が前の場面の状態に頼る形は、後の Intent で直す候補（setup で割り当て済みなら受け入れる、ロールの数ではなく自分のロールの有無で確かめる）。
- **`rolePoolLimit` の合否の回を流し直した**（3.3）。

## Sources

- `build/perf-results/pv/`（`times.txt`・各場面の `*.json`・`*.log`・`*-metrics.txt`・`role-limit-*`・`logcounts.txt`・`unassign.txt`・`audit-counts.txt`・`dsl/summary.md`・`dsl/postgres/ui-lang.json`・`ui-timing.json`・`gc.log`）
- `aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/load-test-plan.md`・`performance-validation-questions.md`・`script-fixes.md`・`redeploy-log.md`
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/performance-test-instructions.md`
- `perf/README.md`・`perf/k6/scenarios.js`・`perf/dsl-timing.sh`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`
- 読み取りの確かめ: `pmset -g log`・`pmset -g assertions`、`docker inspect`・`docker stats`、`/actuator/metrics`（使い捨てのアプリだけ）

## Assumptions & Open Questions

- `rolePoolLimit` の合否の回の 1 回目（23.55 ms）を `Not Met` のまま残すか、流し直し（8.55 ms）を正とするかは承認の場で決める。
- 流し直した3つの場面は、割り当てを外した後の状態で測った。内部DB の大きさは同じだが、手順書の順のままの状態ではない。
- 10 MiB の DSL の投入のヒープ（GC の後 573〜746 MB）が見積もり（256〜384 MB）を超えた件と、BUSY の結び付き、任意の重なり（dsl-v2 R-07）は feedback-optimization へ渡す。
