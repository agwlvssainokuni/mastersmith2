# 負荷の試験の結果（test-results）

2026-10-03 に、`load-test-plan.md` のとおり使い捨ての環境で流した結果です。生データ（k6 の結果・hikaricp の値・警報の状態の記録・監査の件数）はホームの下の一時の置き場に置き、結果を見てから一時の置き場ごと消しました（リポジトリには置いていません）。

## 1. 条件

| 項目 | 値 |
|---|---|
| 期間 | 23:08〜23:33。配備したアプリは **23:08:19 に止め、23:32:51 に healthy に戻した（約 24 分 32 秒）** |
| 環境 | colima の VM（CPU 4・メモリ 6GiB）。使い捨てのアプリ `mastersmith:local`（`f386b55f16a8`、配備と同じ `cc28d1f`、上限 CPU 4・メモリ 2g）、Mailpit（profile `mail`）。配備した環境の Mailpit と見本の PostgreSQL は動かしたまま |
| 負荷 | k6 `grafana/k6:2.3.0`、同時 10（上限 10 の (A) だけ 5）。時間で終わる場面は 60 秒ずつ。台本の全体を `caffeinate -i` で包んだ |
| 内部DB | 利用者 1,142 名（仮の管理者 1・`perf-ua-` 1,000・操作する管理者 10・対象 10・氏名と言語の対象 10・止める悪い側 100・`perf-user` 11）、リフレッシュトークン 110,000 行を入れた悪い側の条件のまま、場面を重ねた（試験の後の内部DB のファイル 77MB） |
| 手元の監視 | 上限 30 の場面は止めたまま。上限 10 の場面だけ lgtm を起動し、使い捨てのアプリだけ外部エクスポートを有効にした（23:19:49〜23:32:37） |
| スリープ | `pmset -g log` に、23:08〜23:40 の Sleep・Wake は無かった（バッテリー駆動） |
| 共有 | k6・Mailpit・lgtm はアプリと同じ VM の CPU を分け合う。測った値には、その分が混ざりうる |

- 最初に、配備したアプリを止める前（23:08 より前）に `k6 inspect --include-system-env-vars` を流し直し、使う8つの場面（`loginSuccess`・`refresh`・`invitationList`・`userAdminList`・`userAdminProfile`・`userAdminOps`・`userAdminSuspendWorst`・`userAdminPool`）がすべて読み込めて、場面の名前・実行の形・閾値が出ることを確かめました（`node --check` も通過。`VUS=5` の `userAdminOps` は 1つの VU が 20 回）。
- 同時のログインの前に、`perf-user01`〜`11`・`perf-uaop01`〜`10`・仮の管理者を1人ずつログインさせて、ロックの状態の行を作りました（22 件すべて 200）。

## 2. 場面ごとの結果（上限 30、手元の監視なし）

どの場面も k6 の閾値（p95 と `checks` の率 1）をすべて満たし、終わりのコードは 0 でした。`loginSuccess`・`refresh` の台本は閾値を持たないため、p95 を k6 の結果から読んで判定しました。

| 場面 | 目標（p95） | p95 | p99 | 最大 | 件数 | checks | 判定 |
|---|---|---|---|---|---|---|---|
| `loginSuccess` | 1 秒 | 939.6 ms | 980.2 ms | 1,043 ms | 765 | 765 / 0 | Met（余裕 60 ms） |
| `refresh` | 1 秒 | 2.8 ms | 6.4 ms | 1,151 ms | 531,010 | 531,000 / 0 | Met |
| `invitationList`（1ページ目） | 1 秒 | 2.7 ms | 5.1 ms | 350 ms | 245,132 | 980,528 / 0（場面全体） | Met |
| `invitationList`（最後のページ） | 1 秒 | 2.6 ms | 4.9 ms | 260 ms | 245,132 | 同上 | Met |
| `userAdminList` a（検索なしの1ページ目） | 1 秒 | 3.6 ms | 6.4 ms | 284 ms | 359,073 | 718,146 / 0 | Met |
| `userAdminList` b（最後のページ、page 50） | 1 秒 | 4.1 ms | 6.7 ms | 290 ms | 316,498 | 632,996 / 0 | Met |
| `userAdminList` c（多く当たる検索、1,000 件） | 1 秒 | 6.1 ms | 9.2 ms | 137 ms | 216,455 | 432,910 / 0 | Met |
| `userAdminList` d（当たらない検索、0 件） | 1 秒 | 3.0 ms | 5.2 ms | 211 ms | 426,278 | 852,556 / 0 | Met |
| `userAdminProfile`（成功 204） | 1 秒 | 3.1 ms | 4.7 ms | 94 ms | 146,293 | 292,586 / 0（場面全体） | Met |
| `userAdminProfile`（入力の誤り 400） | 1 秒 | 3.9 ms | 5.8 ms | 78 ms | 146,293 | 同上 | Met |
| `userAdminOps` 印を付ける | 1 秒 | 7.4 ms | 84.6 ms | 102 ms | 100 | 500 / 0（5つの操作） | Met |
| `userAdminOps` 印を外す | 1 秒 | 7.3 ms | 14.5 ms | 63 ms | 100 | 同上 | Met |
| `userAdminOps` 止める | 1 秒 | 6.9 ms | 14.2 ms | 54 ms | 100 | 同上 | Met |
| `userAdminOps` 停止を解く | 1 秒 | 5.8 ms | 10.3 ms | 15 ms | 100 | 同上 | Met |
| `userAdminOps` 失敗回数を戻す | 1 秒 | 4.8 ms | 10.7 ms | 13 ms | 100 | 同上 | Met |
| `userAdminSuspendWorst`（未無効 100・無効 1,000） | 1 秒 | 106.9 ms | 145.9 ms | 261 ms | 100 | 200 / 0 | Met |
| `userAdminPool`（5つの操作と一覧を同時に） | `checks` の率 1 | 6.7 ms（全要求） | 12.5 ms | 1,688 ms | 115,658 | 228,653 / 0 | Met |

- `loginSuccess` の p95 は 939.6 ms で、前の Intent（904 ms）より 36 ms 長く、目標までの余裕は 60 ms です。最大は 1,043 ms でした。停止の判定を足した影響か、ぶれの範囲かは切り分けていません。bcrypt を計算する API の余裕が小さいことは前の Intent からの申し送りのままです。
- `userAdminPool` の全要求の最大 1,688 ms は、VU ごとの最初のログイン（bcrypt）と、5つの操作の準備のログインの失敗（`{name:userAdminPrepLogin}`、判定に数えない）の分です。
- `invitationList` の招待中は、setup が足した 45 件（最後のページは 3）でした。前の Intent（招待中 35,168 件、p95 約 90 ms）より件数が少ない条件で、目標の判定には足ります（NFR は件数を決めていない）。
- `userAdminOps` の流した回数は、操作ごとに 100 回（`UA_ROUNDS` 既定 10 × 10 VU）です。`userAdminPool` は、k6 の要求と `checks` の数から、5つの操作の組が 377 回でした（監査の件数と一致。5節）。
- 409（`USER_ADMIN_BUSY`・`USER_ADMIN_NO_CHANGE`）と 5xx は、どの場面でも 0 件でした（`checks` の失敗 0、アプリのログの BUSY・NO_CHANGE 0 件・ERROR 0 件）。

## 3. 接続プール

使い捨てのアプリの `/actuator/metrics` を、各場面の前後で読みました。

**単位の注意**: `hikaricp.connections.acquire` の単位（`baseUnit`）は、外部エクスポートを無効にした上限 30 の場面では **秒**、外部エクスポートを有効にした上限 10 の場面では **ミリ秒** でした（同じ指標の名前で、使う計測の登録の仕方で変わる）。下の表はミリ秒にそろえています。1回目に読んだときは単位を取り違えかけ、手元の監視の `hikaricp_connections_acquire_max_milliseconds`（6.005625）と値が一致したことで単位の違いに気づきました。

| 場面 | 上限 | 待ちの時間切れの累計 | 借りるまでの待ちの最大 | 待ちの数（読んだ時点） |
|---|---|---|---|---|
| `loginSuccess` の前 → 後 | 30 | 0 → 0 | 0.03 ms → 8.0 ms | 0 |
| `refresh`・`invitationList` の後 | 30 | 0 | 8.0 ms・7.1 ms | 0 |
| `userAdminList` a〜d の後 | 30 | 0 | 9.0・9.0・9.0・6.0 ms | 0 |
| `userAdminProfile`・`userAdminOps`・`userAdminSuspendWorst` の後 | 30 | 0 | 4.9 ms | 0 |
| `userAdminPool` の前 → 後 | 30 | 0 → 0 | 4.9 ms → 4.9 ms | 0 |
| (A) `userAdminOps` `VUS=5` の前 → 後 | 10 | 0 → 0 | 0.11 ms → 3.0 ms | 0 |
| (B) `userAdminOps` `VUS=10` の前 → 後 | 10 | 0 → 0 | 3.0 ms → 6.0 ms | 0（30 秒ごとに 22 回読んで、すべて 0） |

- 上限 30 の場面は、すべて時間切れ 0・待ちの最大 9 ms 以下で、5 秒より十分小さいです（U3 の NFR6.2、U1 の NFR6.1b）。`loginSuccess` の後の値（時間切れ 0・最大 8.0 ms）は、前の Intent（時間切れ 0・最大 12.5 ms）と同じ水準です。
- 使用中の本数の最大は測っていません（決まりどおり、時間切れの累計と待ちの最大で判断）。

## 4. 上限 10 の場面（U3 の NFR6.3）と警報の確かめ

### 4.1 (A) 同時 5（23:20:10〜23:20:24）

| 項目 | 値 |
|---|---|
| 5つの操作の p95 | 印を付ける 33.0・外す 28.1・止める 33.9・解く 25.5・戻す 23.6 ms（各 100 回） |
| checks | 500 / 0（すべて 204） |
| 待ちの時間切れの累計 | 0 |
| 借りるまでの待ちの最大 | 3.0 ms |
| ERROR のログ・監査の失敗のログ | 0 件・0 件（止めずにアプリのログを数えた） |
| 監査の件数 | 5つの種類それぞれ 100 件増え、流した件数と一致（5節） |

(A) は判定の条件（時間切れ 0・件数の一致）を満たしました。

### 4.2 (B) 同時 10（23:20:52〜23:26:40、5分48秒）

5 分以上続けるため `UA_ROUNDS=400` にしました（操作ごとに 4,000 回）。6分半で k6 を止める用意をしていましたが、その前に回数を終えて自然に終わりました。

| 項目 | 値 |
|---|---|
| 5つの操作 | 操作ごとに 4,000 回、checks 20,000 / 0（すべて 204）。p95 は 6.2〜7.4 ms、最大 43〜121 ms |
| 待ちの時間切れの累計 | 0（30 秒ごとに読んで、ずっと 0） |
| 借りるまでの待ちの最大 | 6.0 ms（借りた回数 72,099、待ちの合計 53.0 ms） |
| 待ちの数 | 30 秒ごとの 22 回すべて 0。手元の監視の `max_over_time(hikaricp_connections_pending[15m])` も 0、`hikaricp_connections_active` の最大（1 分ごとの値）は 2 |
| ERROR のログ・監査の失敗のログ | 0 件・0 件（アプリのログ。Loki の L6 の式も 0 件） |
| BUSY・行の排他の WARN | 0 件・0 件（アプリのログ。Loki の L3 も 0 件） |
| 欠けた監査 | 0 件（5つの種類それぞれ 4,000 件増え、流した件数と一致。5節） |

- **(B) で期待した「2本目を借りる待ち」は出ませんでした**。待ちの最大 6.0 ms は、上限 30 で待ちが起こりえない場面の値（4.9〜9.0 ms）と同じ水準で、上限に届いたことを示しません。時間切れと欠けた監査も 0 件でした。
- 見立て（確かめていない）: 1つの組（5つの操作と準備のログインの失敗）の時間の大半は、準備のログインの失敗の bcrypt（約 0.5 秒）と k6 の順の送信で、5つの操作そのものは 1 件 1 ms 前後で終わります。そのため、サーバーの中で同時に動く操作は 10 件に届かず、上限 10 の接続を使い切る重なり（同時 5〜6 件の操作）が起きなかったと見ています。見積もり（1件に2本）が誤っている証拠ではありません。借りた回数は要求1件あたり約 3.0 回（(A) 1,821 回 / 607 件、(B) 72,099 回 / 24,022 件）で、操作が監査の記録で2本目を借りていることとは矛盾しません。
- 決まりどおり、設定や台本を変えて流し直していません。当初の判定は Not Met で、依頼者の決定で Unverified とし後の Intent へ持ち越した（`nfr-validation-matrix.md` 3.1節）。

### 4.3 警報（`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`）

lgtm を 23:19:49 に起動して使い捨ての網に別名 `lgtm` でつなぎ、(B) の始まりから終わりの後 5 分（23:31:46）まで、30 秒ごとに Grafana の警報の評価（`/api/prometheus/grafana/api/v1/rules`）を 22 回読みました。

| 警報 | 状態 | 理由 |
|---|---|---|
| `ms-pool-pending` | 22 回すべて `inactive`（Normal） | 待ちの数が 0 を超えなかった |
| `ms-error-logs` | 22 回すべて `inactive`（Normal） | ERROR のログが 0 件（`logback_events_total{level="error"}` の増加 0） |
| `ms-audit-fail` | 22 回すべて `inactive`（Normal (NoData)） | 監査の失敗のログが 0 件 |

- 外部エクスポートは働いていました（Loki に「要求をエラー応答に変換しました」が 4,100 件届き、アプリのログの件数と一致。Prometheus に hikaricp の指標が届いていた）。
- 3つの警報が U3 の失敗で鳴ることは、失敗が起きなかったため確かめられていません（Unverified）。
- BUSY が起きなかったため、L3 と L4 の2行の `traceId` での結び付きも確かめていません（Unverified、Q3: A のとおり feedback-optimization へ渡す）。

## 5. 監査の件数の突き合わせ

使い捨てのアプリを止め、内部DB を複写して読み取り（`ACCESS_MODE_DATA=r`）で数えました。1回目は上限 10 の場面に入る前（23:19:37）、2回目は最後（23:32 の片付けの前）です。

| 監査 | 1回目 | 2回目 | 流した成功の件数 | 一致 |
|---|---|---|---|---|
| `USER_ADMIN_GRANTED`（成功） | 477 | 4,577 | `userAdminOps` 100 ＋ `userAdminPool` 377 ＋ (A) 100 ＋ (B) 4,000 | 一致 |
| `USER_ADMIN_REVOKED`（成功） | 477 | 4,577 | 同上 | 一致 |
| `USER_SUSPENDED`（成功） | 577 | 4,677 | 上の 477・4,577 ＋ `userAdminSuspendWorst` 100 | 一致 |
| `USER_RESUMED`（成功） | 577 | 4,677 | 同上 | 一致 |
| `LOGIN_FAILURES_RESET`（成功） | 477 | 4,577 | 上の `USER_ADMIN_GRANTED` と同じ | 一致 |
| `LOGIN_FAILED`（`PASSWORD_MISMATCH`） | 477 | 4,577 | 準備のログインの失敗（5つの操作の件数には数えない） | 一致 |
| `INVITATION_ISSUED`（成功） | 45 | 45 | `invitationList` の setup の招待 45 | 一致 |
| `LOGIN_SUCCEEDED` | 892 | 919 | `loginSuccess` 765 ＋ 前もってのログイン 22 ＋ 各場面の setup と VU ごとのログイン | 突き合わせの対象外 |

- 失敗の監査（`FAILURE`）は `LOGIN_FAILED` だけで、5つの操作の拒否は 0 件でした。氏名と言語の変更は監査の種類が無く、監査に残りません（`AuditEventType` で確かめた）。
- 止める悪い側の 100 名は、止めたときにリフレッシュトークンがすべて無効になり、試験の後の未無効の行は試験のログインで出た 919 行だけでした。

## 6. 秘密の確かめ

値は表示せず、件数だけを数えました。

| 確かめ | 上限 30 の場面のログ | 上限 10 の場面のログ |
|---|---|---|
| 試験用の利用者の名前の部分 `perf-ua` | 0 | 0 |
| `MVStoreException` | 0 | 0 |
| `@example.com`（招待の宛先） | 0 | 0 |
| `register#token=` | 0 | 0 |
| JWT の形（`eyJ`） | 0 | 0 |
| `@example.test` | 2 | 1 |
| k6 の結果（json・ログ）の `register#token=`・`eyJ`・`PERF_USER_PASSWORD` | 0 ファイル | 0 ファイル |

- `@example.test` は、起動ごとに1行出る初期管理者の INFO（伏せ字のメールアドレス `maskedEmail`、`project.md` の読み方で Forbidden に反しない）です。上限 30 の場面は起動を2回（利用者を入れる前と後）、上限 10 の場面は1回でした。
- JSON でない行（上限 10 の場面で 4 行）は、外部エクスポートの部品が出す JVM の警告（`sun.misc.Unsafe`）でした。

## 7. 片付け

確かめの結果を見てから片付けました（23:32:37〜23:32:51）。

1. lgtm を使い捨ての環境の網から外した（lgtm の網は `mastersmith_default` だけに戻った）。
2. 使い捨ての環境を消した（`down -v`）。コンテナ 0・ボリューム 0（`mastersmith-perf_perf-data`）・網 0（`mastersmith-perf_default`）。k6 のコンテナも残っていない。
3. lgtm を止めた（`Exited (0)`、元の状態）。lgtm のボリュームには、この段で使い捨てのアプリから送った指標・ログ・トレースが `service_name="mastersmith"` で残っています（利用者の値は含まない。6節）。
4. 配備したアプリを `docker compose start app` で起動し直し、8 秒で healthy、`/actuator/health` は 200 でした（イメージ `f386b55f16a8` のまま）。
5. `docker compose -p mastersmith ps` で、`mastersmith-app-1`（Up・healthy）・`mastersmith-mailpit-1`（Up・healthy）・`mastersmith-targetdb-postgres-1`（Up）が元どおりで、`mastersmith-lgtm-1` は止まっていることを確かめた。
6. ホームの下の一時の置き場（仮の署名鍵・仮のパスワード・ハッシュ・SQL・H2 の道具・k6 の結果・内部DB の複写）を消した。

- リポジトリのコードと設定ファイル（`perf/`・`docker/` を含む）は変えていません。`.env` は開いていません。

## 8. 手順書との差

| 差 | 理由 |
|---|---|
| イメージを作り直さず配備と同じ `mastersmith:local` を使った（`perf/README.md` の手順 0） | 配備した版の後に `aidlc/` の外の変更が無く、配備したイメージのタグを上書きしないため（質問の冒頭の決まっていること） |
| k6 の結果を `build/perf-results/` ではなくホームの下の一時の置き場に置いた | この段の依頼で、成果物と一時の置き場の他に書かないため |
| 利用者の管理の場面に Mailpit と `perf-user01`〜`11` を足し、既存の3つの場面を同じ環境で先に流した | Q2: A |
| 各場面の前後で hikaricp を読んだ（手順書は `userAdminPool` の前後だけ） | `loginSuccess` の前後（U1 の NFR6.1b）と、場面ごとの比較のため |
| (B) を `UA_ROUNDS=400` で流した | Q3: A（5 分以上続ける） |
| 内部DB を複写してから読んだ（手順書は直接読む） | 前の Intent と同じ。元のボリュームに触れないため |

## Sources

- `operation/performance-validation/load-test-plan.md`・`performance-validation-questions.md`
- 実行したコマンドの出力（一時の置き場に置き、見てから消した）: `docker run grafana/k6:2.3.0 inspect --include-system-env-vars …`・`run --summary-export …`、`curl http://127.0.0.1:18080/actuator/metrics/hikaricp.connections.*`、`curl http://127.0.0.1:3000/api/prometheus/grafana/api/v1/rules`、`docker exec mastersmith-lgtm-1 curl localhost:9090/api/v1/query …`・`localhost:3100/loki/api/v1/query …`、`java -cp h2-2.4.240.jar org.h2.tools.Shell …;ACCESS_MODE_DATA=r`、`docker logs mastersmith-perf-app-1`（件数だけ）、`docker inspect`・`docker stats`、`pmset -g log`
- `perf/README.md`・`perf/k6/scenarios.js`・`docker/perf/compose.yaml`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`
- `backend/src/main/resources/application.yaml`（`connection-timeout: 5000`・`maximum-pool-size`）、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`
- `operation/observability-setup/alarms.md`・`log-queries.md`

## Assumptions & Open Questions

- (B) で待ちが出なかった理由（4.2節の見立て）は確かめていません。扱いを依頼者に相談します。
- `loginSuccess` の p95 が前の Intent より 36 ms 長いことの原因（停止の判定を足した影響か、ぶれか）は切り分けていません。
