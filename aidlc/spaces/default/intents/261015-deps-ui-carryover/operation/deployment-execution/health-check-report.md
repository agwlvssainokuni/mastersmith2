# ヘルスチェックの記録（health-check-report）

## 入れ替えの後

| 確かめ | 結果 |
|---|---|
| コンテナの状態 | `mastersmith-app-1`（`mastersmith:local`）が healthy（入れ替えの命令から約 20 秒、停止から 22 秒） |
| `/actuator/health` | HTTP 200、`{"status":"UP"}` |
| 起動のログ | ERROR 0、WARN 2（既知）。救済は働いていない |
| JVM | Java 25.0.4.1、`ExitOnOutOfMemoryError`・`MaxRAMPercentage=50.0` |
| 接続プール | 使用中 0・待機 30・合計 30・借りる待ち 0（`docker/hikari-pool.sh status`） |
| Mailpit | `mastersmith-mailpit-1`（`axllent/mailpit:v1.31.5`）が healthy |
| 見本の対象DB | `mastersmith-targetdb-postgres-1` は入れ替えず、動いたまま |

## 配備の後の複写の後（S10）

| 確かめ | 結果 |
|---|---|
| コンテナの状態 | `docker compose start app` の後に healthy（停止から 11 秒） |

## 判定

健全。アプリが止まったのは、入れ替えで 22 秒、配備の後の複写で 11 秒の計 33 秒（Deployment Pipeline の見込みの約 2 分と約 10 秒の内）。

## Sources

- `operation/deployment-execution/deployment-log.md`（時刻）、`smoke-test-results.md`（S1〜S3・S10）

## Assumptions & Open Questions

- 手元の監視（lgtm）はこの段では起動していない。
