# 負荷の試験の手順（261003-user-admin-followup、T1・FR4.2）

- この Intent の流れには Performance Validation の段が無く、使い捨ての環境を手元で用意できるため、T1（FR4.2）の k6 の試験の持ち主は Build and Test とした（`project.md` の学び、計画 7.1節）。
- 手順の正本は `perf/README.md` の節「接続プールの上限を下げて流す（Intent 261003-user-admin-followup の FR4）」と、その前提の手順 0・1・1''・2''、後の 4''・5''。この段で試験の実際に合わせて直した版（`develop` の `c50e64c`）を使う。
- 実施の結果は `construction/build-and-test/t1-load-test-results.md` にある。この文書は、何を・どう流し・何で判定するかをまとめる。

## 1. 目的と合否の基準（計画 7.1節、依頼者の決定 D2: A）

操作1件は1本目の接続を持ったまま、確定の後の監査の記録で2本目を借りる。接続プールの上限を 4 に下げ、同時 12 の操作をかけると、全員が2本目を待ち、`connection-timeout`（5 秒）で時間切れになる。この状態で、指標と警報が設計どおりに働くかを確かめる（上限に届く形を含める。`project.md` の学び 2026-10-01）。

| ID | 基準 | 読む値 |
|---|---|---|
| FR4.2(a) | 試験の間の `hikaricp.connections.timeout` の累計が 1 以上、または `hikaricp.connections.acquire` の最大が 1,000 ms 以上 | `/actuator/metrics`（使い捨てのアプリにだけ公開）。`baseUnit` を見てミリ秒にそろえる（FR7.1） |
| FR4.2(b) | 本物の警報の決まりのまま、`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` の3件が、試験の始めから終わりの後 5 分までに少なくとも1回 `Alerting` になる | Grafana の警報の状態を 30 秒ごとに読む |
| FR4.2(c) | 409 `USER_ADMIN_BUSY` が出たら、L3（エラー応答への変換の行）の各行に同じ `traceId` の L4（行の排他を取れなかった WARN）がちょうど1件あり、件数が一致する | アプリのログ（JSON） |

- 警報の決まりの値（しきい値・続く時間）は変えない（Q2: A）。
- (c) で BUSY が 0 件のときは `Unverified` と記録し、(c) のためだけに場面を変えて流し直さない（依頼者の判断）。
- 警報が鳴らなかったときは、まず条件が起きたかを指標とログで確かめ、起きたのに鳴らなければ `Not Met`、起きなければ1時間の上限の中で1回だけ上限 2・同時 20・8 分で流し直す。

## 2. 環境

- 使い捨ての環境: `docker compose -p mastersmith-perf -f docker/perf/compose.yaml`。上限 CPU 4・メモリ 2g（配備と同じ）。仮の署名鍵・仮の利用者で、終わったら消す。本物のデータと監査ログを汚さない。
- イメージ: Intent ごとのタグ（`mastersmith:user-admin-followup`）。配備したアプリのタグ `local` は上書きしない。
- 使い捨てのアプリの `app.env` にだけ足す行（秘密ではない値）: `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318`。
- 手元の監視: `grafana/otel-lgtm` を使い捨ての環境のネットワークに名前 `lgtm`・画面 `127.0.0.1:13000` で起動し、`docker/monitoring/provisioning/alerting/mastersmith.yaml` を読み取りでそのまま読ませる。
- 試験用の利用者: 手順 2''（`perf-uaop`・`perf-uat`・`perf-uapf` は 01〜20）。メールアドレスは予約のドメインだけ。資格情報は権限 700 の一時ディレクトリの中で乱数で作り、表示しない。
- 配備したアプリ: colima の VM の余裕のため、試験のあいだ止める（Q3: A。`docker compose stop app`、試験の後に `start` で同じコンテナを起動し直す。止める前と起動し直す前に依頼者に知らせる）。

## 3. 流し方

```bash
# 前提: perf/README.md の手順 0・1・1''・2'' を済ませる（イメージは Intent のタグ、export MASTERSMITH_IMAGE_TAG=user-admin-followup）
# 節「接続プールの上限を下げて流す」の a〜e のとおり:
#   a. app.env に上限 4 などの行を足す  b. lgtm を起動し、使い捨てのアプリを作り直す
#   c. 台本 ua-limit.sh を caffeinate -i で包んで流す（台本の全体を包む。場面ごとに起こし直さない）
caffeinate -i zsh "$D/ua-limit.sh" "$D" limit 12 5m
#   d. L3・L4 を「L4 をちょうど1件持つ L3」の件数で数える  e. 警報の状態の記録を読む
# 4''. 秘密の件数を数える  5''. アプリを止め、H2 の道具で監査の件数を読み取りだけで数えてから片付ける
```

- k6: `grafana/k6:2.3.0`、`SCENARIO=userAdminPoolLimit`・`VUS=12`・`DURATION=5m`・`-e PERF_UA_COUNT=20`（台本は VUS が `PERF_UA_COUNT` を超えると始める前に止まる）。
- 台本は 30 秒ごとに `timeout`・`acquire`・`pending` の指標、警報の状態、`docker stats` を記録する。`acquire` の `MAX` は直近の窓の最大で時間とともに下がるため、(a) は試験の間の記録と k6 の終わりの直後の値で判定する（終わりの後 5 分の値では判定できない）。
- 警報の題の `state` は `inactive`・`pending`・`firing` で、`firing` を `Alerting` と読む。警報の実体の `state`・`activeAt` も記録する。
- 片付けの前に、判定に使う値（指標・警報・ログの件数・監査の件数）を `build/perf-results/` に残すか読み取ってから環境を消す（`project.md` の学び 2026-09-25）。

## 4. 秘密の確かめ（手順 4''）

- アプリのログの `perf-ua` の件数は、`監査イベントの記録に失敗しました` の ERROR のキー `enteredEmail`（既知の例外。`project.md` の Corrections 2026-09-29）の行の数と一致し、それ以外の行では 0 であること。
- `MVStoreException`・`password`・`Bearer `・`eyJ` は 0 件。k6 の出力の `@example` は 0 件。
- ログはファイルに残さず、件数だけを見る。値は表示しない。

## 5. 結果の要約（この段の実測。詳細は `t1-load-test-results.md`）

| ID | 実測 | 判定 |
|---|---|---|
| FR4.2(a) | `timeout` の累計 0 → 662、`acquire` の MAX 最大 5,049.8 ms（`milliseconds`） | Met |
| FR4.2(b) | 3件とも `firing`（実体は `Alerting`）。`ms-audit-fail` 23:37:06Z、`ms-error-logs` 23:38:12Z、`ms-pool-pending` 23:39:17Z | Met |
| FR4.2(c) | 409 `USER_ADMIN_BUSY` 0 件（L3 0・L4 0） | Unverified（依頼者の決定で次の Intent へ持ち越す） |

- (b) が鳴ったため、上限 2・同時 20・8 分の流し直しは行っていない。
- 使い捨てのアプリは止まらなかった（`OOMKilled=false`、メモリの最大 571.1MiB / 2GiB）。
- 応答時間の閾値は置かない場面のため、p95（5,745 ms）は判定に使わない。

## Sources

- `perf/README.md`（手順 0・1・1''・2''・4''・5''、節「接続プールの上限を下げて流す」、`baseUnit` の注意書き）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/t1-load-test-results.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-generation-plan.md`（7.1節、4節 D2）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/build-and-test/build-and-test-questions.md`（Q3）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`ms-error-logs`・`ms-audit-fail` は `for: 0s`、`ms-pool-pending` は `for: 1m`）
- `aidlc/spaces/default/memory/project.md`（Testing Posture の学び）

## Assumptions & Open Questions

- (c) の BUSY は行の排他の待ちの上限切れで出るもので、接続の待ちだけの場面では出ない見込みだった（計画 7.1節）。実測でも 0 件で、確かめるには別の場面が要る。次の Intent で扱う（依頼者の決定、2026-10-04）。
- 試験の間、アプリのログに Tomcat の `Servlet.service() … threw exception` の ERROR が 239 件出た。例外のログを境界で1回だけ出す決まり（team.md の Code Style）と重なる出力の可能性があるが、原因は確かめていない。次の Intent へ持ち越す（依頼者の決定、記録だけ）。
