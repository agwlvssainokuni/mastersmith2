# 監視の設計 — u4-local-runtime-docs

この単位は、アプリの指標・警報・ダッシュボード・ログの項目を足しません。手元の配備で内部DB の PostgreSQL を選んだときの、確かめの見方（ヘルスチェックとログ）だけを決めます。

## Metrics & Alerting

| 項目 | 決めたこと | 理由 |
|---|---|---|
| アプリの指標・警報・ダッシュボード | 変更なし | アプリのコードを変えない |
| コンテナの資源の見方 | Deployment Execution の起動確認のとき、`docker stats --no-stream`（内部DB 用のコンテナ）の値を 1 回記録し、メモリの上限 512m の内であることを確かめる | NFR2.6（NFR 設計 DD-8） |
| 起動の時間の基準 | U1 の `INTERNAL_DB_PG_BASELINE`（起動からヘルス UP まで・移行の時間）を、Deployment Execution の起動確認の時間と並べて記録する。合否には使わない | NFR1.1 |

## Logs & Tracing

| 項目 | 決めたこと | 理由 |
|---|---|---|
| ログ | 新しいログを足さない。起動時の Flyway の INFO に、資格情報を含まない接続先の URL が出ることを、既知の制約として `docs/internal-db.md` に書く（利用者・パスワードは出ない） | NFR2.4・U3 のレビュー |
| コンテナのログ | json-file（10m・3 本）。内部DB 用のコンテナのログには SQL の文・行の値を出さない既定のまま（`log_statement` を足さない） | NFR2.1 |

## Health & Failure

| 項目 | 決めたこと | 理由 |
|---|---|---|
| ヘルスチェック | アプリ: 変更なし（U1 の PostgreSQL のヘルスチェックの結果を `/actuator/health` が返す）。内部DB 用のコンテナ: `pg_isready -h 127.0.0.1`（`CMD-SHELL`）で `healthy` を返す。アプリの `depends_on` が `service_healthy` で待つ | NFR4.1（`infrastructure-specification.md`） |
| 起動に失敗したとき | 空の資格情報のまま profile を選ぶと、PostgreSQL のコンテナが起動を拒み、`docker compose logs internal-db-postgres` に理由（パスワード未設定）が出る。アプリは `depends_on` で起動しない。文書の「困ったとき」に、この見方を 1 行書く | NFR2.1 |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md`（DD-8）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-requirements/security-requirements.md`（NFR2.4）

## Assumptions & Open Questions

None.
