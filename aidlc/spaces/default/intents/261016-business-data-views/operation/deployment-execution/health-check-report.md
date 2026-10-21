# ヘルスチェックの記録（health-check-report）

## 入れ替えの後

| 確かめ | 結果 |
|---|---|
| コンテナの状態 | `mastersmith-app-1`（`mastersmith:local` = `sha256:4cec01e79026…`）が 2026-10-20T23:49:11Z に healthy（停止から約 53 秒） |
| `/actuator/health` | HTTP 200 |
| 起動のログ | ERROR 0、WARN 2（既知）。救済は働いていない。書き込みの項目の名前を含む行 0 |
| 移行 | Flyway の `Migrating schema` 1 件（版 13） |
| JVM | `ExitOnOutOfMemoryError`・`MaxRAMPercentage=50.0` |
| 見本の対象DB | `mastersmith-targetdb-postgres-1` を作り直し、書き込みのアカウントを足した。確かめ `1\|t\|t\|t\|f\|f\|f`、番号は開けていない（`5432/tcp`）。`sales.customers` 2 行 |

## 配備の後の複写の後（S13）

| 確かめ | 結果 |
|---|---|
| コンテナの状態 | 00:15:48Z に止めて複写し、00:15:59Z に healthy（約 11 秒） |

## 戻しの練習の後

| 確かめ | 結果 |
|---|---|
| 配備したアプリ | healthy、`/actuator/health` 200 のまま（練習の間も止めていない）。メモリ 475MiB |
| 練習の環境 | 消した（`rollback` の名前のコンテナ・ボリューム 0） |

## 判定

健全。アプリが止まったのは、入れ替えで約 53 秒、配備の後の複写で約 11 秒の計約 64 秒（Deployment Pipeline の見込みの約 3〜4 分と約 10 秒の内）。

## Sources

- `operation/deployment-execution/deployment-log.md`（時刻・戻しの練習）、`smoke-test-results.md`（S1〜S3・S12・S13）

## Assumptions & Open Questions

- 手元の監視（lgtm）はこの段では起動していない（戻しの練習の間は VM のメモリの余裕が小さいため。Environment Provisioning の申し送り）。
