# 性能の試験の手順 — Build and Test（Intent 261029-reference-lookup-cleanup）

手順の正は `perf/README.md`（「業務データの場面」1B〜4B、「他テーブル参照の読み取りの場面」、「他テーブル参照の保存前の確かめの場面」、「記録（evidence/）に置く前の伏せ字」、「今の基準の一覧」）。ここでは、この Intent の性能の目標と、この段と後の段（Performance Validation）の受け持ちの分け方を書く。この段で流した結果は `k6-trial-record.md`。

## 1. 目標と持ち主

| 目標 | 線（緩めない） | 物差し | この段（Build and Test） | 合否（Performance Validation） |
|---|---|---|---|---|
| U1-NFR2.1・NFR2.3（AC3.5.4 の (1)(2)） | `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel` は p95 < 1,000 ms、`refPickerContains` は p95 < 3,000 ms、`checks` の率 1 | `iteration_duration{scenario:<名前>}`（`http_req_duration{name}` は並べて記録するだけ） | 種・`k6 inspect`・試し走り（1 VU・10 秒、判定ではない） | 3種類の対象DB、10 VU・3 分、読み取りのプールの上限 5、合否の回は 2 回 |
| U2-NFR2.21〜2.25（AC3.5.4 の (3)） | `refCreateChecked` の p95 < 1,000 ms、`checks` の率 1。既存の書き込みの場面の意味を変えない | 同上 | 種（`biz_refcreate` 0 行）・試し走り | 同上。`bizWritePoolLimit` の 409 の数と書き込みのプールの待ちを前の Intent と並べる |
| `bizSearchWide`・`bizCreateLarge` の `memory.peak` | アプリのコンテナの `memory.peak` / `memory.max` < 0.9（Q5: B で 90% 未満のまま確定） | `memory.peak`（`anon` の最大も並べる） | 線の決定と `perf/README.md` の更新 | 判定の回で測る |

比べる相手は Intent 261016-business-data-views の Performance Validation（読み取りのプールの上限 5）の結果とし、上限 10 の回は流さない（Q4: A、`perf/README.md` 履歴 H61）。深いページのピッカーの参考の測りは手順から外した（Q6: A）。

目標・物差しを満たさないときも、線と物差しを緩めない。`Not Met` として記録し依頼者に諮る（`team.md`・`project.md`）。

## 2. 環境

- 配備した環境とは別の使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、終わったら `down -v`）で、1種類ずつ流す。
- 使い捨ての環境の合計（アプリ 2g・MySQL 2688m など 5,504 MiB）が VM（5,910 MiB）の空きに収まらないため、流す間は配備したアプリを `docker compose stop app` で止め、終わったら `start` する（作り直さない）。起動し直したら、救済の WARN の件数（値は表示しない）とイメージ ID が変わっていないことを確かめる。
- 試験のイメージは配備のタグ（`mastersmith:local`）と別のタグで作る（`perf/README.md` の手順 0）。
- 判定の回は業務データの試験の値（`MASTERSMITH_PERF_TARGETDB_*`）で起動する。試し走りでは export しない。
- 流す前に、使い捨てのアプリだけに `/actuator/metrics` を公開し、`hikaricp.connections.max`（`pool=mastersmith-target-db`）が 5 でなければ流さない。

## 3. 流す順（1種類ごと）

1. 種を流す（MySQL・MariaDB は `--default-character-set=utf8mb4`）。終了コード 0 と、`biz_item` 100,000・`biz_code200` 200・`biz_code201` 201・`biz_refcreate` 0、目印の当たり（909）・最長の文字数を数えてから表の大きさを測る。
2. `k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめる（`undefined` が無い）。`bash perf/k6/thresholds-test.sh`・`bash perf/dsl-mixed-memory-test.sh` が終了コード 0。
3. `bizPrepare`（1 VU・1 回）→ 暖め（`refPickerEquals`）→ 読み取りの5場面 → `refCreateChecked`（書き込みは読み取りの後。参照元の表が増えるため）。
4. 2つのプールの待ちの時間切れ（`hikaricp.connections.timeout`）を場面の前後で読む。
5. 片付ける前に、使い捨てのアプリのログをロガーごとの件数で数え（値は表示しない）、`OOMKilled`・`memory.events`・`memory.peak` を読む。結果を見てから `down -v` で消す。

台本の全体を `caffeinate -dims` で包み、流した後に `pmset -g log` で眠りが無いことを確かめる。

## 4. 記録（evidence/）に置く前の伏せ字

summary json には使い捨ての環境のアクセストークン（JWT）が入る。記録に置くときは道具の JSON の口だけを使い、置いた後に検査を2つ流して 0 件を確かめてからコミットを提案する（`project.md` の Forbidden、U4 の BR4.3）。

```bash
node perf/redact-k6-evidence.mjs build/perf-results/<名前>.json <記録の置き場>/evidence/<名前>.json
node perf/redact-k6-evidence.mjs --scan <記録の置き場>/evidence
gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 <記録の置き場>/evidence
```

## 5. この段の結果（要約）

- 3種類の対象DB で種・0 行の確かめ・`k6 inspect`（参照の6場面）・試し走り（18 の summary json に閾値の名前の指標がそろった）・MySQL の 1038 の確かめ（再現しなかった）・H4（`--scan` と `gitleaks dir` 0 件）を行った。配備したアプリを止めたのは約 13 分 52 秒、救済の WARN は 0 件。詳細は `k6-trial-record.md`。
- 合否（U1-NFR2.1・2.3・2.5、U2-NFR2.21・2.23〜2.25、AC3.5.4、`memory.peak`）は `Unverified` のまま Performance Validation へ引き継ぐ。
- 目録の期待の「78 場面の `k6 inspect`」は、この段では参照の6場面だけを inspect し、残りは `thresholds-test.sh` の期待の表との照合だけにした（`k6-trial-record.md` 8節 D7）。足りるかは承認の場で確かめる。
