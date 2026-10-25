# 仮の目標（slo-config）

前の Intent の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/slo-config.md` と、それが正とする前々回までの記録）を正とします。仮の目標（稼働 99.5%・30 日など）は、配備先が決まったときに正式に決めます（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 1. 今回の差

- この Intent の承認済みの SLO は、業務データの6本の API の応答時間 p95 1 秒（U1 の NFR1.1〜NFR1.3、U2 の NFR1.4・NFR1.5）で、**値は緩めません**。
- 判定は performance-validation の段が、使い捨ての環境の k6 で行います。k6 の `iteration_duration` の値を正とし、サーバー側の指標は参考です（U1・U2 の `monitoring-design.md` 3節）。
- 手元の監視を常に動かしていないため、手元の監視の p95 の判定は `Unverified` とし、測った時点の値を基準の値として並べます（`project.md` の Deployment の学び）。
- ダッシュボードの「仮の目標の一覧」に1行を足しました（Q5: A、`dashboards.md` 1節）。区画の p95 のパネルの線 1000 ms は、この目標の目安です。

## 2. 運用の中の判定と基準の値

基準の値は、使い捨ての環境から手元の監視に送った値です。条件は次のとおりで、目標の判定には使いません。
- イメージは配備と同じ `mastersmith:local`、メモリの上限 1g、CPU 4
- 対象DB は使い捨ての PG（既定の設定・上限 512m、種 100,000 行）
- 前回: 2026-10-25 02:04〜03:12 UTC（`measurement-notes.md`）。この段の確かめの回: 同日 04:05〜04:28 UTC（1 VU、繰り返しごとに 1 秒待つ）

配備したアプリの値は、配備の記録 `operation/deployment-execution/health-check-report.md`（healthy、`/actuator/health` 200）と、この段の間の `docker stats`（使用 705 MiB／上限 2g。この段の間に要求は送っていない）を並べます。

| SLI | 目標（窓） | 判定 | 基準の値 | 配備先が決まったときの測り方 | 持ち主 |
|---|---|---|---|---|---|
| 読み取りの3本（定義・一覧・詳細）の p95 | 1000 ms 以内（k6 は場面ごと 3 分） | Unverified | 区画の p95（パネル 41）は成功の間 95.0〜95.9 ms（すべて `le="100"` の中の補間の値）。前回の1回目の k6（2 VU・3 分、判定ではない）の繰り返しの時間の p95 は `bizDefinition` 2.5 ms・`bizSearchFirst` 16.1 ms・`bizDetail` 60.6 ms | 区画の p95 と 1000 ms 以内の割合（`le="1000"` の割合）を、配備先の監視で常時取る | performance-validation（k6）・feedback-optimization |
| 書き込みの3本（作成・更新・削除）の p95 | 1000 ms 以内（同上） | Unverified | 区画の p95 は成功の間 95.0〜95.9 ms。前回の1回目の k6 の p95 は `bizCreate` 2.9 ms・`bizUpdate` 1.4 ms・`bizDelete` 20.4 ms（20 回）・`bizWritePoolLimit` 10.3 ms（2回目は 13.1 ms） | 同上 | 同上 |
| 対象DB に届かないときの応答 | 承認済みの目標なし（503 を返すこと） | 参考 | 503 は借りる待ちの約 3 秒で返り、区画の p95 は 4,850 ms（2000〜5000 ms のバケット） | 警報 `ms-biz-target-db` と区画の 503 の件数で見る | feedback-optimization |
| 区画の API の 5xx でない割合 | 配備先が決まったときに決める（全体の仮の目標は 99.5%・30 日） | Unverified | 対象DB を止めたときのほかは 503 0 件（この段の通常の流れ 771 回） | 区画の 503 の件数と全体の稼働のパネルを 30 日の幅で取る | feedback-optimization |
| 対象DB のプールの時間切れ | 時間切れの増え方 0（performance-validation の合否の回） | Unverified | 通常の流れの間は 0。PG を止めた間だけ増え、503 の件数と一致した（この段: 読み取り 48・書き込み 24、前回: 192・96） | 区画のプールのパネル（時間切れの系列）と `ms-biz-target-db` | performance-validation |
| 内部DB の大きさ | 承認済みの目標なし（U2 の NFR 要件のレビューの R-03 で基準の値を求められた） | 基準の値を記録 | 3節 | 3節 | feedback-optimization |

- `increase` と `histogram_quantile` の値は、Prometheus の補間を含みます。件数の正は監査の記録とログの件数です。
- 新しく現れた系列の最初の点は `increase` に数えられません。この段の確かめの回では、用意の `bizCreate` の 1,247 件が区画の要求の数に入りませんでした（`dashboards.md` 2.3）。
- 区画の p95 のパネルは、要求の無い 5 分には NaN になります。NaN を「要求が無いため」と決めつけず、要求の数のパネルで要求の有無を確かめます（`project.md` の学び）。
- 区画の p95 は最小のバケットが 100 ms のため、100 ms 未満の要求はすべて 95 前後に見えます。k6 の値（1.4〜60.6 ms）とは細かさが違います。

## 3. 内部DB の大きさの基準の値（Q6: A、`measurement-notes.md` 10節）

業務データの書き込みは、1件ごとに監査の行（主キーの値は持たず、`target_schema`・`target_table`・`work_role_id` の3列を足した行）を内部DB に残します。値（行の中身）は表示せず、大きさと件数だけを読みました。

| 時点 | `mastersmith.mv.db` | `audit_events` の行 | うち `BUSINESS_ROW_*` |
|---|---|---|---|
| **配備した内部DB**（Deployment Execution の配備の後の複写、2026-10-21） | 86,016 B | 280 | 3（作成・更新・削除の SUCCESS 1 件ずつ。スモークテストの分） |
| 使い捨て: 書き込みの前（`bizPrepare` と読み取りの場面の後） | 45,056 B | 42 | 0 |
| 使い捨て: 書き込みの後（前回の1回目） | 106,987,520 B | 1,030,802 | 1,030,701 |
| 使い捨て: 最後（前回の2回目の後） | 123,838,464 B | 1,195,954 | 1,195,805 |

- **1件あたりの増え方の見積もり**: 内部DB の増え方の大部分は監査の行です（業務データの行そのものは対象DB に入る）。
  - 書き込みの前から後: (106,987,520 − 45,056) ÷ 1,030,760 行 ≈ **約 104 B／行**
  - 2回目: (123,838,464 − 106,987,520) ÷ 165,152 行 ≈ **約 102 B／行**
  - 監査の行は 1 件 **約 100 B** と見積もります（アプリを止めて H2 が閉じるときに詰め直した後の値。動いている間のファイルは、詰め直す前のためこれより大きくなりうる。測っていない）。
- **100 万行を超えた事情**: 前回の1回目では、k6 の書き込みの場面（`bizCreate`・`bizUpdate`・`bizWritePoolLimit`）が繰り返しの間に待ちを置かないため、2〜10 VU でも毎秒 1,500〜2,200 件の書き込みになりました。その結果、約 12 分で監査の行が 100 万行を超えました（`bizWritePoolLimit` の 409 も FAILURE の監査の行として残る）。依頼者1人の画面の操作の量ではなく、負荷の試験の量です。
- 配備した内部DB の今の大きさ（86,016 B・280 行）から見ると、画面の操作で書き込みを1万件したときの増え方は約 1 MB の見込みです（1件 約 100 B として）。
- 配備先が決まったときの測り方: README の「監査ログの確かめ方」の手順で、配備ごとに `mastersmith.mv.db` の大きさと `audit_events` の件数（`BUSINESS_ROW_*` を含む）を記録し、増え方を見ます。詰め直しは README の内部DB の詰め直しの手順のままです。

## 4. 持ち越しの Unverified の判定（Build and Test から）

| ID | 目標 | この段の確かめ | 判定 |
|---|---|---|---|
| U1-NFR8.1 | 警報 `ms-forbidden` のしきい値（20）を見直す | 業務データの 403 も数に入ること、監査に残らないことを実測で確かめ、見分け方を書いた（`alarms.md` 4節） | **しきい値 20 のまま**（Q4: A）。見直しは配備先が決まり利用者が増えたときの申し送り |
| U1-NFR8.3 | ダッシュボードの業務データの区画（3本の p95・503 の数・プールの待ち）と、既存の警報で拾えること | 区画を足し、6つのパネルの式が値を返すことを確かめた（`dashboards.md`）。既存の警報は条件つきで拾った（`alarms.md` 3節） | **区画を足した**。既存の警報で拾えることは条件つきで、`ms-biz-target-db` を足して補った（F1: A） |
| U2-NFR8.5 | ダッシュボードに書き込みの3本の p95・409 と 503 の数・書き込みのプールの待ちを足す | 同じ区画に入れ、値が返ることを確かめた（`dashboards.md` 2.3） | **区画を足した** |
| U2-NFR8.6 | 既存の警報が書き込みも拾う | `alarms.md` 3節・6節 | `ms-pool-pending`・`ms-5xx-ratio`・`ms-forbidden` は使い捨ての環境で鳴った（条件つき）。`ms-audit-fail`・`ms-audit-slow` は式が出来事の種類によらないことを式の読み取りで確かめ、鳴ることは `Unverified` |

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/observability-setup-questions.md`（Q4: A・Q5: A・Q6: A・F1: A・F2: A、やり直したまとめの確認）
- この段の `measurement-notes.md`（2節・4節・7節・10節）・`dashboards.md`・`alarms.md`
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/observability-setup/slo-config.md`
- `construction/build-and-test/build-and-test-summary.md`（U1-NFR8.1・U1-NFR8.3・U2-NFR8.5・U2-NFR8.6）
- 基盤の設計: `construction/u1-businessdata-read/infrastructure-design/monitoring-design.md`（3節）・`construction/u2-businessdata-write/infrastructure-design/monitoring-design.md`（3節・7節）
- NFR 要件: `construction/u1-businessdata-read/nfr-requirements/performance-requirements.md`・`observability-requirements.md`、`construction/u2-businessdata-write/nfr-requirements/performance-requirements.md`・`observability-requirements.md`
- `operation/deployment-execution/health-check-report.md`
- 実行したコマンドの出力: `curl -s http://127.0.0.1:13000/api/datasources/proxy/uid/prometheus/api/v1/query_range?...`（区画の式）、`docker stats --no-stream`、`java -cp h2-2.4.240.jar org.h2.tools.Shell -url "jdbc:h2:file:/data/mastersmith;ACCESS_MODE_DATA=r"`（件数と大きさだけ）

## Assumptions & Open Questions

- 1件 約 100 B の見積もりは、閉じて詰め直した後のファイルの大きさから出したものです。動いている間の大きさは測っていません（`Unverified`）。
- U1-NFR8.3 の「区画を足した」とする判定（既存の警報の部分を新しい警報で補った形）は、承認の場で確かめます。
