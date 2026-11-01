# 異常の検出（anomaly-config）

前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/anomaly-config.md` と、それが正とする前々回までの記録）を正とします。機械学習による異常の検出は置かず、固定の閾値の警報とダッシュボード・問い合わせで見ます。

## 1. 手元では異常の検出を行わない理由（前の Intent と同じ）

| 理由 | 中身 |
|---|---|
| 監視を常に動かしていない | 手元の監視（lgtm 0.38.0）は、見たいときだけ compose の profile `monitoring` で起動します（`aidlc/spaces/default/memory/project.md` の Deployment）。異常の検出の元になる平常の値の連なりがたまりません |
| 利用者が1人 | 配備したアプリを使うのは依頼者だけで、要求の数が少なく、平常の幅を学べません |
| 通知の先が無い | 配備先が決まるまで警報の通知の先を置きません |
| 設計で決めた | 新しい警報は足さない（U1 の `monitoring-design.md` 3節・U2 の `monitoring-design.md` 3節・U3 の `monitoring-design.md` 2節、NFR2.10・NFR3.8・NFR2.29・NFR3.28） |

## 2. 気づきたいことと見る場所（この Intent の差）

| 気づきたいこと | 見る場所 |
|---|---|
| 参照の読み取りで対象DB に届かない・答えない（ピッカーの「検索できません」、ラベルが値だけになる、作成・更新の画面が開けない） | 警報 `ms-biz-target-db`（5 分の窓に業務データの API の 503 が1件でもあれば鳴る）。パネル 47（参照の API の 503）と 42。原因は `log-queries.md` 2節の R1（`ReferenceLookup` の WARN の `reason`・`budgetExceeded`） |
| 保存前の確かめで対象DB に届かない（作成・更新の 503） | 同じ警報とパネル 42。⑩（保存前の確かめ）か ⑪（書き込み）かは `log-queries.md` 2節の R2（`BusinessDataWriteService` の WARN の `purpose`・`stage`） |
| 「選べない値です」の急な増え（409 `BUSINESS_REFERENCE_NOT_FOUND`） | パネル 43 と、code ごとの件数（`log-queries.md` 2節の R3）。警報にはしない（利用者の入力による業務の誤り）。監査の行（`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED` の `FAILURE`）でも数えられる |
| 参照の API が遅い | パネル 41（業務データの p95。目安の線 1000 ms）。ピッカーの部分一致の検索の目標 3,000 ms はバケットの境界（2,000・5,000）に無く、2,000〜5,000 ms の間を見分けられない（`dashboards.md` 3節、Q4: A）。判定は Performance Validation の k6 |
| 参照の API の量の急な変化 | パネル 46（参照の API の要求の数）。定義の取得は `VIEW` を含み、ピッカーとラベルは同じ道のため、内訳は指標では分けられない |
| 対象DB の読み取りのプールが詰まりかけている | パネル 44 の時間切れの系列。参照の読み取りと保存前の確かめは、一覧・詳細と同じ読み取りのプールを使う |

- 新しく現れた数の系列の最初の点は `increase` に数えられないため、件数の少ない異常（対象DB が止まった直後の 503 など）は、最初の 1〜2 分は 0 に見えることがあります。今回の確かめで `ms-biz-target-db` が鳴るまでの時間は `alarms.md` 2節のとおりです。

## 3. 配備先が決まったときの測り方

1. 監視を常に動かし、配備したアプリの外部エクスポートを有効にする（`slo-config.md` 2節）。
2. 2〜4 週間、パネル 40〜47 の要求の数・p95・403・409・503 の平常の幅を取り、固定の閾値を見直す。部分一致の検索の 3,000 ms の境界のバケットを足すかも、このときに決め直す（`dashboards.md` 3節）。
3. 配備先の監視の仕組みに異常の検出（季節性のある要求の数など）があれば、そのときに候補にする。

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/observability-setup-questions.md`（2節、Q4: A）
- この段の `dashboards.md`・`alarms.md`・`log-queries.md`・`slo-config.md`・`tracing-config.md`
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/anomaly-config.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/monitoring-design.md`・`construction/u2-reference-write-check/infrastructure-design/monitoring-design.md`・`construction/u3-reference-screens/infrastructure-design/monitoring-design.md`
- NFR 設計: U1・U2 の `reliability-design.md`（503 と 409 への写し）・`performance-design.md`（目標）

## Assumptions & Open Questions

None.
