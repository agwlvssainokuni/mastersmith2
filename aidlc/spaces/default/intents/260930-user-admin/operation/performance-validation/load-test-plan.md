# 負荷の試験の計画（load-test-plan）

Intent `260930-user-admin` の性能の目標のうち、Build and Test から引き継いだ Unverified と、Observability Setup から移された警報の確かめを、使い捨ての環境で確かめる計画です。決定は `performance-validation-questions.md`（Q1〜Q4 すべて A、確認済みの要約）です。手順の正は `perf/README.md`（「利用者の管理の場面」「利用者の設定と招待の場面」）と、前の Intent の同じ段（`aidlc/spaces/default/intents/260925-user-management/operation/performance-validation/`）です。

## 1. 対象の目標

閾値は出典の値のままで、緩めません（p95 1000 ms と `checks` の率 1）。

| 目標 | 内容 | 場面 |
|---|---|---|
| U3-NFR5.1 | 一覧（利用者 1,000 名）の同時 10 件で p95 1 秒以内。区分 a（検索なしの1ページ目）・b（最後のページ）・c（多く当たる検索）・d（当たらない検索）ごと | `userAdminList`（`LIST_CASE` a〜d） |
| U3-NFR5.3 | 氏名と言語の変更の同時 10 件で p95 1 秒以内（成功 204・入力の誤り 400） | `userAdminProfile` |
| U3-NFR5.4 | 5つの操作を操作ごとに p95 1 秒以内、204 の率 1（409・5xx が 0 件） | `userAdminOps`（同時 10・操作ごとに 100 回） |
| U3-NFR5.5・U1-NFR5.2 | 止める操作の悪い側（未無効 100 件・無効 1,000 件）で p95 1 秒以内 | `userAdminSuspendWorst`（100 名を1回ずつ） |
| U3-NFR5.6 | 目標の負荷で `USER_ADMIN_BUSY` が 0 件 | `userAdminOps`・`userAdminPool` の `checks` |
| U3-NFR6.2 | 5つの操作と一覧を同時 10 件で流しても接続プール（上限 30）が尽きず、監査が欠けない | `userAdminPool` の前後の hikaricp、監査の件数 |
| U3-NFR6.3 | 上限 10 で (A) 同時 5 は時間切れ 0・件数一致、(B) 同時 10 は2本目の待ちが出る | `userAdminOps` を上限 10 で `VUS=5`・`VUS=10` |
| U1-NFR5.1b・U4-NFR5.1b | 停止の判定を足した後もログイン・更新の p95 1 秒以内 | `loginSuccess`・`refresh` |
| U1-NFR6.1b | 既存のログインの負荷でプールの使い方が変わらない | `loginSuccess` の前後の hikaricp |
| U2-NFR5.1b | 招待の一覧の1ページ目と最後のページの p95 1 秒以内 | `invitationList` |
| U1-NFR5.4・U3-NFR5.8 | 想定の規模を1台で処理する（上の場面の結果で判定） | 上の場面の総合 |
| U3-NFR5.11 | SLO の基準の値を記録する（observability-setup と共同） | 上の場面の k6 の p95 を記録 |
| U5-NFR5.1・NFR5.2 | 画面の時間の本番での判定 | 測らない（Q4: A）。E2E の値を参考に記録 |
| 警報 `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` | U3 の失敗で鳴ること（Observability Setup から移した） | 上限 10 の (B) を 5 分以上、手元の監視を動かして |
| BUSY の2行の結び付き | L3 と L4 の行が `traceId` で結び付くこと | (B) で BUSY が起きたときだけ |

## 2. 環境

| 項目 | 値 |
|---|---|
| 場所 | colima の VM（CPU 4・メモリ 6GiB）。配備した環境とは別の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`、profile `mail` の Mailpit） |
| イメージ | `mastersmith:local`（`f386b55f16a8`、配備したものと同じ `cc28d1f`）。作り直さない（`perf/README.md` の手順 0 との差。配備したイメージのタグを上書きしないため） |
| 上限 | CPU 4・メモリ 2g（配備と同じ） |
| データ | 空の内部DB に、仮の管理者 1 名、`perf-ua-0001`〜`1000`、`perf-uaop01`〜`10`（管理者）・`perf-uat01`〜`10`・`perf-uapf01`〜`10`、`perf-uasw-01-01`〜`10-10`（1人に未無効 100 件・無効 1,000 件のリフレッシュトークン、合わせて 110,000 行）、`perf-user01`〜`11`（Q2: A）。メールアドレスは `@example.test` だけ。仮の署名鍵 |
| 一時の置き場 | ホームの下（権限 700）。環境ファイル・k6 の結果・内部DB の複写を置き、終わったら消す。値は表示しない |
| 使い捨てのアプリの設定の差 | メールの受け手・差出人・ベース URL（`perf/README.md` の手順 1'）、`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`。上限 10 の場面だけ `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` と外部エクスポート（`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・送り先 `http://lgtm:4318`）を足して作り直す |
| 配備したアプリ | k6 の間は止める（Q1: A）。配備した環境の Mailpit と見本の PostgreSQL は動かしたまま |
| 手元の監視 | 上限 30 の場面は止めたまま。上限 10 の場面だけ lgtm（profile `monitoring`）を起動し、使い捨ての環境の網に別名 `lgtm` でつなぐ（Q3: A）。終わったら外して止める |
| 負荷の元 | k6 `grafana/k6:2.3.0` を同じネットワークのコンテナで流す。台本の全体を `caffeinate -i` で包む。k6・Mailpit・lgtm はアプリと同じ VM の CPU を分け合う |

## 3. 流し方

1. 配備したアプリを止める前に `k6 inspect --include-system-env-vars` を流し直し、使う8つの場面が読み込めて、scenarios と thresholds が出ることを確かめる。
2. 配備したアプリを `docker compose stop app` で止め、時刻を記録する。
3. 使い捨ての環境を起動し、止めて利用者とトークンの行を入れ、起動し直す。同時のログインの前に、試験用の利用者（`perf-user01`〜`11`・`perf-uaop01`〜`10`）と仮の管理者を1人ずつログインさせて、ロックの状態の行を作る（`project.md` の Testing Posture）。
4. 上限 30（手元の監視なし）で、次の順に流す。どれも同時 10。時間で終わる場面は 60 秒ずつ。各場面の前後で `hikaricp.connections.timeout`（累計）・`acquire`（最大）・`pending`・`max` を読む。
   1. `loginSuccess`・`refresh`・`invitationList`（内部DB が膨らんだ悪い側の条件のまま。Q2: A）
   2. `userAdminList` の a・b・c・d
   3. `userAdminProfile`
   4. `userAdminOps`（`UA_ROUNDS` 既定 10、操作ごとに 100 回）
   5. `userAdminSuspendWorst`（100 名を1回ずつ）
   6. `userAdminPool`
5. 使い捨てのアプリを止め、内部DB を複写して読み取りで開き、監査の件数を数える（1回目）。
6. 上限 10 と外部エクスポートを足して使い捨てのアプリを作り直し、lgtm を起動して網につなぐ。
7. (A) `userAdminOps` を `VUS=5` で流し、前後の hikaricp の値と ERROR のログの件数を、止めずに読む。
8. (B) `userAdminOps` を `VUS=10` で 5 分以上続ける（`UA_ROUNDS` を大きくし、6分半で k6 を止める）。30 秒ごとに Grafana の警報の状態と hikaricp の値を読み、終わってから最長 5 分見る。
9. 使い捨てのアプリを止め、監査の件数を数える（2回目）。秘密が出ていないことを件数で確かめる。
10. 結果を見てから片付け、配備したアプリを `docker compose start app` で起動し直して healthy を確かめる。

## 4. 判定の決まり

- Met: 閾値をすべて満たし、`checks` の率が 1。接続プールは時間切れの累計と借りるまでの待ちの最大で判断し、数秒ごとの使用中の数では判断しない。
- Not Met: 閾値を満たさない。目標を緩めず、環境を起動し直して再現させ、原因をログと状態で確かめてから記録し、承認の場で相談する。
- Unverified: この段で確かめられなかったもの（理由と持ち主を書く）。
- (B) は待ちを起こす場面のため、p95 と件数の一致に数えない。欠けた監査・ERROR の件数・時間切れの累計を記録する。
- 準備のログインの失敗（`{name:userAdminPrepLogin}`）は p95 と `checks` に数えない。
- 正とする値は k6 の値（クライアント側）。

## 5. 片付け

- 確かめの結果を見てから、使い捨ての環境（コンテナ・ボリューム・網）を `down -v` で消し、lgtm を網から外して止め、一時の置き場を消す。
- 配備したアプリを起動し直して healthy を確かめ、`docker compose -p mastersmith ps` で3つが元どおりであることを確かめる。

## Sources

- `operation/performance-validation/performance-validation-questions.md`（Q1〜Q4、確認済みの要約）
- `construction/build-and-test/build-and-test-summary.md`（Unverified の一覧）・`performance-test-instructions.md`
- `construction/u3-user-admin-api/nfr-requirements/performance-requirements.md`・`reliability-requirements.md`・`scalability-requirements.md`
- `construction/u1-user-suspension/nfr-requirements/security-requirements.md`（NFR5.2・NFR5.4）
- `construction/u5-user-admin-ui/nfr-requirements/performance-requirements.md`
- `operation/observability-setup/alarms.md`（2節）・`log-queries.md`（2節）・`dashboards.md`（2節）・`tracing-config.md`
- `perf/README.md`・`perf/k6/scenarios.js`・`docker/perf/compose.yaml`・`docker/monitoring/provisioning/alerting/mastersmith.yaml`
- 前の Intent の同じ段: `aidlc/spaces/default/intents/260925-user-management/operation/performance-validation/`

## Assumptions & Open Questions

None.
