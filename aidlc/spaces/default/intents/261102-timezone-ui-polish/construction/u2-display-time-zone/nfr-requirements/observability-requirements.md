# 観測の要件 — u2-display-time-zone

出典: 機能設計（`functional-design/rules.md` の BR2.8・BR2.9・BR2.15）、要件（`inception/requirements-analysis/requirements.md` の NFR2.1）、`security-requirements.md`、`team.md`・`project.md`（Deployment・Forbidden）。

新しい経路は、既存の `/api/me/preferences` の項目を1つ足すことと、画面のログイン後の読み取り1回だけ。既存の HTTP の指標・警報・ダッシュボード・構造化ログ・トレースがそのまま新しい項目を含めて効くため、**新しい指標・警報・ダッシュボードの決まりは足さない**。番号の付け方は `security-requirements.md` の冒頭のとおり。上流の NFR2 に当たる行が無い追加の要件は NFR2.3 の続きから振る。

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR2.3.5 | 既存の HTTP 指標（リクエスト数・時間・状態コード別）が `GET`・`PUT /api/me/preferences` を今のまま数える。5xx の割合の警報・p95 の警報（既存の決まり）が、新しい項目を含む要求に今のまま効く。指標の名前・ラベル・警報の式を変えない | 既存の監視の決まり（`docker/monitoring`） | 既存の警報の決まりのテスト（式の写しの確かめ）が変更なしで通ること。新しい指標・警報の追加が差分に無いこと |
| NFR2.3.6 | 構造化ログとトレースにタイムゾーンの名前の値を載せない。拒否（400）は想定内の業務エラーとして WARN 以下でスタックトレースなし、境界で1回だけ出す。項目の名前（`displayTimeZone`）と誤りの種類（`INVALID_VALUE`）だけを出す。監査には残さない（`security-requirements.md` の NFR1.4.4）。トレースの道の属性は今の道の雛形のまま（道に値を載せない。名前は本文で運ぶ） | BR2.4・BR2.8・`team.md` Code Style（例外のログは変換する境界で1回だけ） | TRACE を有効にした `PreferencesSecretLeakIT` と、外部へ送る設定を有効にした結合テスト（`security-requirements.md` の NFR1.4.3）。400 の1回のログの件数と水準を確かめる結合テスト |
| NFR2.3.7 | 画面の読み込み失敗（`error` 状態）は、画面に短い文と再読み込みを出すだけで、画面の側から診断の情報をサーバーへ送らない（新しい API を足さない）。サーバー側で気づく手段は、既存の `GET /api/me/preferences` の 5xx・401 の指標と警報 | BR2.15・BR2.14 | 画面の単体テスト: `error` のとき追加の要求が出ないこと。診断の送信先が無いこと |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 同じ段の文書: `security-requirements.md`・`performance-requirements.md`

## Assumptions & Open Questions

- [assumption] 手元の監視（otel-lgtm）は常に動かしていないため、既存の警報が新しい項目に効くことの実機の確かめは、この Intent では行わない（流れに Observability Setup の段が無い）。式が変わらないことは構造の確かめで足りるとみなす。
- 未解決の点は無い。
