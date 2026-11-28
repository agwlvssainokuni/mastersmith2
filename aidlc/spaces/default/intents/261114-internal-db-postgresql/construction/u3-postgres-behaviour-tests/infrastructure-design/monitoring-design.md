# 監視の設計 — u3-postgres-behaviour-tests

この単位はテストだけで、アプリの監視（指標・警報・ダッシュボード・ログの項目）を足しません。足すのは、テストの実行の記録（標準出力の 1 行）だけです。

## Metrics & Alerting

| 項目 | 決めたこと | 理由 |
|---|---|---|
| アプリの指標・警報・ダッシュボード | 変更なし | アプリのコードを変えない |
| テストの実行の記録 | 標準出力に `INTERNAL_DB_PG_<種類> <キー>=<値>`（`WAIT`・`SHARED_APP`。値は数字だけ）。Build and Test が表にまとめる | NFR5.2・NFR5.4（DD-10・DD-21） |

## Logs & Tracing

| 項目 | 決めたこと | 理由 |
|---|---|---|
| ログ | 新しいログを足さない。テストの出力・ログ・失敗の文に、接続の情報・行の値・違反の文を出さない（共有のアプリの起動の出力の検査 `PgSecretLeakIT`、BR2・BR3・BR5 のクラスのログの検査を 1 件ずつ） | NFR2.1・NFR2.2・NFR2.6（DD-12・DD-13） |
| トレース・TRACE | 変更なし。`TraceAspect` の対象の層のコードを変えない | NFR2 |

## Health & Failure

| 項目 | 決めたこと | 理由 |
|---|---|---|
| ヘルスチェック | 変更なし。PostgreSQL のヘルスチェックの確かめ（UP・DOWN）は U1 の `InternalDbPostgresHealthIT` が持つ | FR3.9 は U1 |
| 失敗の見え方 | テストの失敗は、`@DisplayName`（英語）と `as("<機能>/<流れ>: <確かめる点>")` で、状態コード・`code`・件数だけを出す | NFR2.6（DD-22） |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/observability-design.md`・`security-design.md`

## Assumptions & Open Questions

None.
