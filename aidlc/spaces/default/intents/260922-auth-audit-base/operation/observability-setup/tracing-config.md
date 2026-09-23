# トレースの設定（tracing-config）

Intent `260922-auth-audit-base`（auth-audit-foundation）のトレースの設定と、外部エクスポートの確認の結果（Q2）。入力は、U1〜U4 の `construction/<単位>/infrastructure-design/monitoring-design.md`（monitoring-design）と `infrastructure-specification.md`（infrastructure-specification）、`construction/<単位>/nfr-design/performance-design.md`（performance-design）・`security-design.md`（security-design）・`reliability-design.md`（reliability-design）である。決定は `observability-setup-questions.md`（Q1〜Q7）にある。

## 1. 設定（実装済み、本段では変えていない）

| 項目 | 値 |
|---|---|
| トレースIDの割り当て | すべての要求（サンプリングによらない。U1 の NFR10.6） |
| 受け取った `traceparent` | 正しければ引き継ぐ（U1 の NFR10.5） |
| 外部への送信 | 既定は無効。`MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true` と送り先で有効（トレース・ログ・指標を OTLP の HTTP で送る） |
| サンプリング率 | `MASTERSMITH_TRACING_SAMPLING_PROBABILITY`（既定 1.0） |
| 秘密情報を載せない仕組み | 要求の URL の問い合わせの部分を取り除く、例外のメッセージとスタックトレースを送らない、利用者の ID・トークンを属性にしない（U1 の security-design 5章） |
| 送り先に届かないとき | 要求の処理は止めない（U1 の reliability-design 1章・2章） |
| 手元の送り先 | 確認用: profile `observability` の `otel-collector`（`http://otel-collector:4318`、標準出力に出すだけ）。見るため: profile `monitoring` の `lgtm`（`http://lgtm:4318`、Tempo に保存） |

## 2. 外部エクスポートの確認（Q2、2026-09-23 12:43〜12:48）

設計（U1 の monitoring-design 4章）の手順どおり、`.env` に外部エクスポートの2行を一時的に足し、`docker compose --profile observability up` で確認用の受け手と一緒に起動した。未ログインの要求（ヘルスチェック・画面・存在しない API・確認用 API。URL に問い合わせの部分 `?probe=secret-query-value` を付けた）を送り、依頼者がブラウザでログイン → 管理 → ログアウトを行った。

| 確かめたこと | 結果 | 判定 |
|---|---|---|
| トレースが届く | ResourceSpans 301 件（ログイン・更新・ログアウト・確認用 API・ヘルスチェックの経路を含む） | 合格 |
| 指標が届く | ResourceMetrics 4 回（60 秒ごと）。`http.server.requests`・`hikaricp.*`・`jvm.memory.*`・`logback.events`・`process.uptime` などを含む | 合格 |
| ログが届く | ResourceLog 4 回、LogRecord 11 件（INFO 9・WARN 2）。起動の後のログがすべて届いた。起動より前のログは送られない | 合格（起動の前のログは送られないことを注意に記録） |
| 秘密情報が載らない（値は表示せずに検索） | 初期管理者のパスワード 0 件、署名鍵 0 件、初期管理者のメールアドレス 0 件、JWT（`eyJ`）0 件、`Bearer ` 0 件、Cookie 0 件、問い合わせの値 0 件、`exception.message`・`exception.stacktrace` 0 件 | 合格 |

確認の後に `.env` を元に戻し（外部エクスポートの行は 0 件）、確認用の受け手を止めて消し、アプリを起動し直した。結合テスト（`ExternalExportIT`、`construction/build-and-test/`）の結果と一致する。

## 3. 見つかったこと

- 外部エクスポートを有効にすると、JVM が `sun.misc.Unsafe` の警告を4行、標準エラーに出す（送信に使う protobuf の部品による）。1行1件の JSON ではない。アプリのログ（U1 の NFR10.1）ではなく JVM の出力であり、既定（無効）では出ない。README に注意を書いた。消す場合は JVM の起動の引数（`--sun-misc-unsafe-memory-access=allow` など）で扱えるが、本段では変えていない。
- 監視のコンテナの画面（Grafana）の起動の中で、部品（grafana-llm-app）をインターネットから取得する。手元の監視を使うときは外への通信が起きる。

## 4. 1つの要求の追い方

1. 画面の不具合なら、エラー応答の `traceId`、またはアプリのログの `traceId` を控える。
2. 監視のコンテナを起動しているときは、Grafana の Explore で Tempo にトレースIDを入れて経路と時間を見る。ログからトレースへ、トレースからログへ移れる。
3. 監査イベントの `trace_id` もアプリのログと一致する（U4 の NFR10.4）。

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/observability-setup-questions.md`（Q1〜Q7、確認済みの要約）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/monitoring-design.md`・`infrastructure-specification.md`、`nfr-design/performance-design.md`・`security-design.md`・`reliability-design.md`
- `compose.yaml`、`docker/monitoring/`、`README.md` の「手元の監視（Grafana）」
- 2026-09-23 12:43〜12:57 に手元で実行した `docker compose`・Grafana の API・Prometheus と Loki の問い合わせ・H2 の Shell の出力

## Assumptions & Open Questions

None.
