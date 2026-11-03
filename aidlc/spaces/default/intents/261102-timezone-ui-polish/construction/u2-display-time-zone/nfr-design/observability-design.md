# 観測の設計 — u2-display-time-zone

出典: NFR Requirements の `observability-requirements.md`（NFR2.3.5〜NFR2.3.7）・`security-requirements.md`（NFR1.4.3・NFR1.4.4）、機能設計（`rules.md` の BR2.4・BR2.8・BR2.9・BR2.15）、`team.md`・`project.md`（Deployment・Forbidden）、同じ段の `security-design.md`・`reliability-design.md`・`logical-components.md`、既存のコード（読み取りだけ: `GlobalExceptionHandler`・`docker/monitoring` の警報の決まり）。

新しい経路は、既存の `/api/me/preferences` の項目を 1 つ足すことと、画面のログイン後の読み取り 1 回だけ。既存の HTTP の指標・警報・ダッシュボード・構造化ログ・トレースが、新しい項目を含めてそのまま効くため、**新しい指標・警報・ダッシュボードの決まりを足さない**。決定の番号は `OD<n>`。

## OD1. 既存の指標・警報がそのまま効く（NFR2.3.5）

- `GET`・`PUT /api/me/preferences` は、既存の HTTP 指標（リクエスト数・時間・状態コード別）が今のまま数える。5xx の割合の警報・p95 の警報（既存の決まり）が、新しい項目を含む要求に今のまま効く。指標の名前・ラベル・警報の式を変えない。
- 確かめ: 既存の警報の決まりのテスト（式の写しの確かめ）が **変更なしで** 通ること。差分に新しい指標・警報・ダッシュボードの追加が無いこと。手元の監視（otel-lgtm）は常に動かしていないため、既存の警報が新しい項目に効くことの実機の確かめは、この Intent では行わない（流れに Observability Setup の段が無い。式が変わらないことを構造の確かめで足りるとみなす）。

## OD2. ログとトレース（NFR2.3.6・NFR1.4.3）

- 構造化ログとトレースに、タイムゾーンの **名前の値を載せない**。検証の前の値は型の伏せ字（`SD2`）で TRACE にも出ず、検証を通った名前も項目としては出さない。
- 拒否（400）は想定内の業務エラーで、WARN 以下・スタックトレースなし・境界（`GlobalExceptionHandler`）で 1 回だけ出す。項目の名前（`displayTimeZone`）と誤りの種類（`INVALID_VALUE`）だけをキーと値で渡し、値を出さない。
- 排他の待ちの上限切れなどの想定外の例外は、既存の扱い（例外のクラスの名前だけを ERROR に出し、行の値と内部の例外の文を出さない）のまま。保存の 1 文でも同じ道を通る（`RD2`）。
- トレースの道の属性は今の道の雛形のまま。名前は本文で運び、道・問い合わせに載せない。
- 監査には残さない（`SD4`）。監査の種類・列を足さない。
- 確かめ: TRACE を有効にした `PreferencesSecretLeakIT` と、外部へ送る設定を有効にした結合テスト（`SD4`）。400 のログが 1 回だけで水準が WARN 以下であること（件数と水準を確かめる結合テスト）。

## OD3. 画面の読み込み失敗（NFR2.3.7）

- `error` 状態は、画面に短い文と再読み込みを出すだけで、**画面の側から診断の情報をサーバーへ送らない**（新しい API を足さない。ビーコン・エラー送信なし）。画面は `console` に値や応答を出さない（`SD7`）。
- サーバー側で気づく手段は、既存の `GET /api/me/preferences` の 5xx・401 の指標と警報だけ。配布の読み取りがログインのたびに 1 回出るため、`GET` の件数はログインの回数に比例して増える（`SC1`）。これは想定内の増加で、警報のしきい値は変えない。
- 確かめ: 画面の単体テストで、`error` のとき追加の要求が出ないこと、診断の送信先が無いこと（`fetch` の呼び出しが再読み込みの操作の分だけ）。

## SLI・SLO・ダッシュボード

- この単位は SLI・SLO を新設しない。応答時間の目標は既存の場面の目標（p95 1 秒）のままで、測りは性能の設計の `PD6`（Build and Test が持ち主。Deferred）。手元の監視を常に動かしていない間の SLO の判定は、既存の決まり（Unverified として基準の値を並べる）に従う。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/observability-requirements.md`・`security-requirements.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`
- 同じ段の文書: `security-design.md`・`reliability-design.md`・`logical-components.md`・`scalability-design.md`
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 手元の監視は常に動かしていないため、既存の警報が新しい項目に効くことの実機の確かめは、この Intent では行わない（式が変わらないことは構造の確かめで足りるとみなす）。
- [assumption] 配備の記録（Deployment の段）の実行時のタイムゾーンの設定の 4 か所は、この単位が変えない（NFR5.2.3）。
- 未解決の点は無い。
