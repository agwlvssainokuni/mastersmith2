# 健全性の確かめ（health-check-report）

配備した版 `7bc1b68` の健全性の確かめ（2026-09-25、UTC では 2026-09-24T16 時台）。

## 1. 結果

| 確かめ | 期待 | 実測 | 判定 |
|---|---|---|---|
| アプリのコンテナの健全性 | `healthy` | 起動から約 9 秒で `healthy`。監査イベントの確かめで止めて起動し直した後も `healthy` | 合格 |
| ヘルスチェックの応答 | `{"status":"UP"}` | `{"status":"UP"}` | 合格 |
| 見本の対象DB | `Up`、上限 512MiB | `Up`、`536870912` | 合格 |
| アプリの上限 | メモリ 2GiB・CPU 4 | `2147483648 4000000000` | 合格 |
| 内部DB のスキーマ | 版 6（V5・V6 を当てる） | Flyway が 4 → 6、`Successfully applied 2 migrations ... now at version v6` | 合格 |
| 内部DB の接続先 | 既定に `;DEFRAG_ALWAYS=TRUE`、`.env` で上書きしない | WAR の中の `application.yaml` にあり、コンテナの環境変数に `MASTERSMITH_DB_URL` は無い | 合格 |
| ログの ERROR | 0 件 | 0 件 | 合格 |
| 止まっていた時間 | 数十秒〜2分 | 配備で約 20 秒、監査イベントの確かめで数秒 | 合格 |

## 2. 見ておくこと

- 動いている間の内部DB のファイルの大きさ（U4-STORAGE-RUN、既知の制約）。大きな DSL の投入と適用を重ねたらディスクの空きを確かめ、アプリを起動し直す。
- VM（colima）のディスクの空きは 64G（Environment Provisioning の時点）。

## Sources

- `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/deployment-execution/deployment-log.md`・`smoke-test-results.md`
- 実行したコマンド: `docker inspect`・`docker compose --profile targetdb-postgres ps`・`curl -s http://localhost:8080/actuator/health`・`docker compose logs app`・`docker exec mastersmith-app-1 sh -c 'env | grep -c "^MASTERSMITH_DB_URL="'`

## Assumptions & Open Questions

None.
