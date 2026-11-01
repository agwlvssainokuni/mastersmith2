# 仮の目標（slo-config）

前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/slo-config.md` と、それが正とする前々回までの記録）を正とします。仮の目標（稼働 99.5%・30 日など）は、配備先が決まったときに正式に決めます（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

- この Intent の承認済みの目標は、参照の API（選択肢つきの定義の取得・ピッカーの一致の検索・ラベルの1件読み・保存前の確かめつきの作成）の p95 1,000 ms 未満と、ピッカーの部分一致の検索の p95 3,000 ms 未満です（U1 の NFR2.1、U2 の NFR2.21。U1・U2 の `performance-design.md`）。**値は緩めません**。
- 判定は Performance Validation の段が、使い捨ての環境の k6 の `iteration_duration` で行います。サーバー側の指標は参考です（U1・U2 の `monitoring-design.md` 4節）。
- 手元の監視を常に動かしていないため、手元の監視での判定は `Unverified` とし、この段の値を基準の値として並べます（`project.md` の Deployment の学び）。
- ダッシュボードの「仮の目標の一覧」に1行を足しました（Q5: A、`dashboards.md` 1節）。3,000 ms のバケットは足していません（Q4: A、`dashboards.md` 3節）。

## 2. 運用の中の判定と基準の値

基準の値は、使い捨ての環境から手元の監視に送った値です（1 VU・要求ごとに 1〜2 秒の待ち。2026-11-01 07:27〜07:48 UTC。条件は `dashboards.md` 2.1節）。負荷の試験ではないため、目標の判定には使いません。配備したアプリには要求を送っていません（配備したアプリは healthy のまま。この段の終わりの `docker ps` で `Up 3 hours (healthy)`）。

| SLI | 目標（窓） | 判定 | 基準の値 | 配備先が決まったときの測り方 | 持ち主 |
|---|---|---|---|---|---|
| 選択肢つきの定義の取得・一致の検索・ラベル・保存前の確かめつきの作成の p95 | 1,000 ms 未満（k6 の場面ごと 3 分・10 VU、3種類の対象DB） | Unverified | パネル 41 は通常の間 95 前後（`le="100"` の中の補間の値）。参照の道の系列は部分一致の検索を含めて 227.5〜417.1 | パネル 41 と 1,000 ms 以内の割合（`le="1000"` の割合）を常時取る | performance-validation（k6）・feedback-optimization |
| ピッカーの部分一致の検索の p95 | 3,000 ms 未満（同上） | Unverified | 道がラベルと一致の検索と同じため、パネルでは分けられない。3,000 ms はバケットの境界に無い（`dashboards.md` 3節） | 足すバケットを決め直す（`anomaly-config.md` 3節） | performance-validation（k6） |
| 参照の API の 503 の数 | 平常で 0（警報 `ms-biz-target-db` の 5 分の窓） | 参考（手元の監視を動かしている間だけ） | 通常の流れの間は 0。PG を止めた間だけ 503（定義 6・参照の道 18・作成 21）で、81 秒で firing、戻して 4分50秒で inactive（`alarms.md` 2節） | パネル 47・42 と `ms-biz-target-db` を常時動かす | feedback-optimization |
| 対象DB の読み取り・書き込みのプールの時間切れ | 時間切れの増え方 0（performance-validation の合否の回） | Unverified | 通常の流れの間は 0。PG を止めた間だけ増え、503 の件数と一致した（読み取り 40.0・書き込み 7.4 の `increase` に対して、503 は 38・7） | パネル 44・45 | performance-validation |

- `increase` と `histogram_quantile` の値は、Prometheus の補間を含みます。件数の正は台本の数と監査の行です（`log-queries.md` 4節）。
- 新しく現れた系列の最初の点は `increase` に数えられません。

## 3. 内部DB の増え方（参考）

この段の確かめの回で、監査に残ったのは作成・更新の 293 行（`BUSINESS_ROW_CREATED` 225・`BUSINESS_ROW_UPDATED` 68）でした（`log-queries.md` 4節）。参照の読み取りは監査に残りません。1件 約 100 B の見積もり（前の Intent の `slo-config.md` 3節）は変えていません。

## 4. Build and Test から持ち越した Unverified の判定（持ち主 observability-setup の 6 行）

出典は `construction/build-and-test/build-and-test-summary.md` です。

| ID | 目標 | この段の確かめ | 判定 |
|---|---|---|---|
| OBS-PANELS | パネル 46・47 と区画の説明、警報 `ms-biz-target-db` が新しい道・定義の道・保存前の確かめの 503 で firing | パネル 46・47 を確かめた `uri` の値で足し、40・42・43 の説明と「仮の目標の一覧」を直した。Grafana に 47 件で読み込まれ、15 の式がすべて値を返した（`dashboards.md` 1節・2.2節・2.4節）。参照の道・定義の道・保存前の確かめつきの作成の 503 を含む 503 で、`ms-biz-target-db` が 81 秒で firing、PG を戻して 4分50秒で inactive（`alarms.md` 2節） | **Met** |
| U1-NFR2.10 | 参照の道が区画の既存の式（`uri=~"/api/tables/.*"`）に入ることを確かめて区画の説明に足す。警報は足さない。3,000 ms はバケットの境界に無いため p95 は参考 | 書く前に `uri` の値（`/api/tables/reference/rows`・`/api/tables/definition`）と `+Inf` 以外のバケット（100〜5000）を確かめた（`dashboards.md` 2.3節）。参照の道の系列がパネル 40・41・42 に入った。パネル 40・42 の説明に足した。警報は足していない | **Met**（3,000 ms の p95 は参考のまま。合否は Performance Validation の k6） |
| U1-NFR3.8 | 参照の読み取りの 503 を既存の `ms-biz-target-db` で拾う。定義の取得の 503 も同じ | `alarms.md` 5節。`UNAVAILABLE` で firing を確かめ、`TIMEOUT` は式が状態コードだけで数えることと結合テストで扱った（Q3: A） | **Met** |
| U2-NFR2.29 | 作成・更新の道が既存の区画に入ることを確かめる。新しい区画・警報は足さない | 保存前の確かめつきの作成は `POST /api/tables/rows`、更新は `PATCH /api/tables/rows` の系列に入り、`uri` の値は増えなかった（`dashboards.md` 2.3節・2.4節）。パネル 42・43 の説明に足した。区画・警報は足していない | **Met**（作成の p95 は参考。合否は k6） |
| U2-NFR3.28 | 保存前の確かめの 503 を既存の `ms-biz-target-db` で拾う | `alarms.md` 5節。保存前の確かめの 503 14 件（WARN の `purpose=REFERENCE`・`stage=BORROW`）を含む 503 で firing | **Met** |
| REQ-NFR3 | 要件 NFR3（接続）の監視の行 | U1-NFR3.8・U2-NFR3.28 が Met。ほかの行（U1-NFR3.1〜3.7・U2-NFR3.21〜3.27）は Build and Test で Met | **Met** |

- REQ-NFR2（要件 NFR2 性能）は、監視の行（U1-NFR2.10・U2-NFR2.29）がこの段で Met になりました。性能の判定の行（U1-NFR2.1・2.3・2.5、U2-NFR2.21・2.23〜2.25）は Performance Validation に残るため、REQ-NFR2 の全体は `Unverified`（持ち主 performance-validation）のままです。

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/observability-setup-questions.md`（2節、Q1〜Q5）
- この段の `dashboards.md`・`alarms.md`・`log-queries.md`・`anomaly-config.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/monitoring-design.md`（4節）・`construction/u2-reference-write-check/infrastructure-design/monitoring-design.md`（4節）・`construction/u3-reference-screens/infrastructure-design/monitoring-design.md`（4節）、U1・U2 の `infrastructure-specification.md`
- NFR 設計: U1・U2 の `performance-design.md`・`reliability-design.md`・`security-design.md`
- NFR 要件: U1・U2 の `nfr-requirements/observability-requirements.md`（NFR2.10・NFR3.8・NFR2.29・NFR3.28）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`
- 前の Intent の同じ段: `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/slo-config.md`

## Assumptions & Open Questions

None.
