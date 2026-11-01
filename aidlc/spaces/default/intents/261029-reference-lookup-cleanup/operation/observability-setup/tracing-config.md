# トレース（tracing-config）

前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/tracing-config.md` と、それが正とする前々回までの記録）を正とします。**トレースの設定は変えていません**。

- Micrometer Tracing、`MASTERSMITH_TRACING_SAMPLING_PROBABILITY` の既定 1.0、W3C の伝え方は前回のまま。
- この Intent で新しいスパン・属性は足していません。参照の道（`POST /api/tables/reference/rows`）と定義の道（`POST /api/tables/definition`）は変数を持たない固定の道で、参照先の値・ラベル・検索の値は本文で運びます（U1 の `monitoring-design.md` 5節、U2 の `monitoring-design.md` 5節、U3 の `monitoring-design.md` 5節、契約 C2）。

## 1. 今回の確かめ（使い捨ての環境、2026-11-01 UTC）

Q1: A の使い捨ての環境（`dashboards.md` 2.1節）で、Tempo を `{ span.uri =~ "/api/tables.*" }` で検索し、span の属性 `uri` の値と件数だけを数えました（span の本文・ほかの属性の値は読んでいません）。数えた道具はリポジトリの外の `~/.mastersmith-tools/tq.sh` です。

| 属性 `uri` の値 | 件数（通常の流れの最初の約 20 分の検索の結果。1 トレースあたり最大 5 span） |
|---|---|
| `/api/tables/definition` | 52 |
| `/api/tables/reference/rows` | 26 |
| `/api/tables/rows` | 32 |

- 属性 `uri` の値は**3つの固定の道だけ**で、問い合わせの部分や参照先の値を含む値は 0 件でした。参照の道が増えても、トレースの道の属性に値が載らないこと（U1 の BR10.3、U3-NFR1.43）が、手元の監視に届いた形でも確かめられました。
- 漏えいの確かめの正は、外部エクスポートを有効にした結合テストと `ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`（Build and Test で Met）です。この段の確かめは、手元の監視に届いた形の確かめです。

## 2. 要求1件の追い方

1. Grafana の Explore（Loki）で、`log-queries.md` 2節の問い合わせから気になる行（参照の 503 の診断の WARN、409 の変換の WARN）を開く。
2. 行の `trace_id` から Tempo のトレースを開き、時間と状態（`status`）を見る。
3. 参照の読み取り（定義の取得・ピッカー・ラベル）は監査に残らないため、ログとトレースだけで追う（U1 の BR1.7）。参照つきの作成・更新は監査の行（`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`）の `trace_id` で結び付く（前の Intent までの記録のとおり）。

## 3. 外部エクスポート（決まっていること）

- 配備したアプリの外部エクスポートは**有効にしていません**。配備した `.env` は開いておらず、配備したアプリも作り直していません（`observability-setup-questions.md` 2節）。
- 確かめでは、使い捨てのアプリにだけ、ホームの下の一時の環境ファイル（権限 600）で `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318` を渡しました。
- 使い捨ての lgtm（`mastersmith-perf-lgtm`、0.38.0）は使い捨ての網に別名 `lgtm` でつなぎ、**網につないだ後にアプリを起動しました**（`project.md` の学び）。lgtm を網につなぐ前の送信の失敗の ERROR は出ていません（`log-queries.md` 1節）。
- 要求は 1 VU・要求ごとに 1〜2 秒の待ちで送り、lgtm の受け手が止められることはありませんでした（`dashboards.md` 2.1節）。

## 4. 設計との差

なし。U1〜U3 の `monitoring-design.md` 5節（新しいスパンを足さない、道は固定）のとおりです。

## Sources

- この段の質問 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/observability-setup-questions.md`（2節、Q1: A・Q2: A）
- この段の `dashboards.md`・`log-queries.md`
- 前の Intent の同じ段の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/observability-setup/tracing-config.md`
- 基盤の設計: `construction/u1-reference-read/infrastructure-design/monitoring-design.md`（5節）・`construction/u2-reference-write-check/infrastructure-design/monitoring-design.md`（5節）・`construction/u3-reference-screens/infrastructure-design/monitoring-design.md`（5節）
- NFR 設計: U1 の `security-design.md`・U2 の `security-design.md`（4.2 道の属性）
- `backend/src/main/java/cherry/mastersmith/businessdata/web/BusinessDataPaths.java`（道の定数）
- 実行したコマンドの出力: `~/.mastersmith-tools/tq.sh`（Tempo の検索。`uri` の値と件数だけ）

## Assumptions & Open Questions

- Tempo の検索の結果で span の名前は空で返りました（`name` を読んでいない形の結果）。道は属性 `uri` で確かめたため、判定には影響しません。
