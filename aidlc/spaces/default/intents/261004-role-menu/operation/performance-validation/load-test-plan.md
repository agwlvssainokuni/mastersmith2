# 負荷の試験の計画（load-test-plan）

Intent `261004-role-menu` の Performance Validation で、Build and Test から引き継いだ `Unverified` 20 件と DSL の時間（U2 の NFR2.5・NFR2.7・NFR2.8）、Observability Setup から移された警報3つを確かめる計画です。依頼者の答え（`performance-validation-questions.md` の Q1〜Q6 すべて A）と、`construction/build-and-test/performance-test-instructions.md`・`perf/README.md` の手順に従います。値・秘密は書きません。

## 1. 対象と環境

| 項目 | 内容 |
|---|---|
| 版 | `develop` の `954028c`。配備したイメージ `mastersmith:local` = `e0a5825d257c`（WAR の SHA-256 `4bf6e68c…`、`redeploy-log.md`）。使い捨ての環境もこのイメージを `MASTERSMITH_IMAGE_TAG=local` で使う（作り直さない。`perf/README.md` の手順 0 との差） |
| 台本 | `4ccabb5` で直した `perf/k6/scenarios.js`・`perf/ui/dsl-ui-lang.mjs`・`perf/README.md`（`script-fixes.md`） |
| VM | colima、CPU 4・メモリ 6GiB |
| 使い捨てのアプリ | `docker/perf/compose.yaml`（プロジェクト `mastersmith-perf`、`127.0.0.1:18080`）、上限 CPU 4・メモリ 2g（配備と同じ。最大ヒープ 50%）。仮の署名鍵・仮の管理者・試験用の利用者（予約のドメインだけ）。指標の公開は使い捨てのアプリだけ |
| 配備したアプリ | 測る間の全体で1回だけ `docker compose stop app`、最後に `docker compose start app`（Q1: A）。Mailpit と見本の対象DB は止めない |
| k6 | `grafana/k6:2.3.0`（ダイジェスト固定）、使い捨ての環境の網の中から送る |
| 眠りの守り | 台本の全体を `caffeinate -dims` で包む。流す前に `pmset -g assertions`、流した後に `pmset -g log` で `Sleep`・`DarkWake` を確かめ、入っていたらその場面を流し直す |

## 2. 流す順（Q2: A）

### 2.1 環境 B（DSL）

1. `KEEP=1 ./perf/dsl-timing.sh --ui --lang --busy postgres`（U2 の NFR2.5・NFR2.7・NFR2.8、NFR2.6 の同時の投入の排他）。生成した版 2 の DSL を残す。
2. 同じ環境で k6: `dslCycle`（VUS 1・3 分）→ `dslPrepare` → `dslLight`（VUS 10・3 分）。
3. アプリを止めて試験用の利用者 `perf-user01`〜`11` を入れ、アプリのコンテナを作り直して（`memory.peak` を戻す）`dslMixed`（VUS 10・3 分）。`memory.peak`・OOMKilled を記録する。
4. 秘密の件数を数えて片付ける。

### 2.2 環境 A（グループ・ロール・メニュー）

1. 用意: 指標の公開、利用者 `perf-gr-0001`〜`1000`・`perf-graop01`・`perf-roleop01`・`perf-nav-01`〜`10` を SQL で入れる。環境 B で生成した DSL から、上限ちょうど（5,242,880 バイト）の権限の YAML と、メニューの悪い側・目安の DSL を作る。
2. グループの8場面（3 分か VU ごとに 100 回）→ `groupPoolLimit` の合否の回（上限 11・VUS 5・2 分）と記録の回（上限 10・VUS 20・2 分）。
3. 上限を既定（30）に戻す → `rolePrepare` → `navPrepare` → `navMenu`・`tableAccessVisible`・`tableAccessDenied` → ロールの9場面 → `rolePoolLimit` の合否の回と記録の回 → 上限を戻す。
4. `roleExport` → アプリのコンテナを作り直してから `roleTransferLargeCheck` → `roleTransferLargeApply`（ヒープを1秒ごと、`memory.peak`・OOMKilled）。
5. 最後に `navMenuBaseline`。
6. 警報の確かめの回（Q6: A）: lgtm を先に網につなぎ、使い捨てのアプリにだけ上限 4・外部エクスポート・トークン 15 分を足して起動し直し、`rolePoolLimit` を VUS 12・5 分、終わった後も 5 分、30 秒ごとに警報の状態と接続プールの値を読む。
7. 秘密と試験用の名前の件数、監査の件数（アプリを止めて読み取りだけ）を数え、結果を確かめてから片付ける。配備したアプリを起こし、healthy と health 200 を確かめる。

## 3. 合否（目標は緩めない）

| 対象 | 合否 |
|---|---|
| 応答時間の場面（グループ・ロール・メニュー・`roleExport`・`dslLight`・`dslCycle`） | 場面（か op）ごとの `iteration_duration` の p95 < 1000 ms と `checks` の率 1。`http_req_duration` は並べて記録するだけ |
| `roleTransferLargeCheck`・`Apply` | `iteration_duration` の最大 < 15,000 ms・< 30,000 ms、`checks` の率 1、OOMKilled なし |
| `*PoolLimit` の合否の回 | 待ちの時間切れの累計の増分 0、`_500` 0 件、`acquire` の最大 < 20 ms（5〜20 ms は合格のまま記録） |
| `*PoolLimit` の記録の回 | 判定しない。`acquire` の最大 10 ms 以上に届いたこと（届かなければ VUS 40 で1回だけ流し直す） |
| `dslMixed`・`--busy` | `checks` の率 1、OOMKilled なし。`--busy` は 201 と 503 `DSL_BUSY` が1つずつ |
| `perf/dsl-timing.sh` | 値を U2 の NFR2.5 の各目標と比べる。NFR2.7 は既定の DSL の大きさが 10 MiB の内。NFR2.8 は `ui-lang.json` の `pass` |
| 警報 | `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` が `firing` になったか |

- 届かない場面は `Not Met` とし、環境を起動し直して再現させ、原因をログと状態で確かめてから記録する。
- 任意の重なり（DSL の投入と権限の YAML の確かめ、dsl-v2 R-07）は流さず、`Unverified` として feedback-optimization へ渡す（Q2: A）。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/performance-validation-questions.md`（Q1〜Q6 の答え）・`script-fixes.md`・`redeploy-log.md`
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/performance-test-instructions.md`・`build-and-test-summary.md`
- `perf/README.md`・`perf/k6/scenarios.js`・`perf/dsl-timing.sh`
- `aidlc/spaces/default/memory/project.md`（Testing Posture の学び）

## Assumptions & Open Questions

- 使い捨ての環境のイメージは配備したもの（`e0a5825d257c`）をそのまま使う。
- 警報の確かめの回のトークン 15 分は使い捨てのアプリだけの設定で、接続プールの使い方は変えない（`script-fixes.md`）。
