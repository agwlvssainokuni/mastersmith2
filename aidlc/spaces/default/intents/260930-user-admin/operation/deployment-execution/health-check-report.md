# ヘルスチェックの報告（260930-user-admin）

| 時刻（UTC） | 対象 | 確かめ | 結果 |
|---|---|---|---|
| 12:54:19Z | app（前の版 `c77c1bb247f9`） | 入れ替えの前 | healthy（2026-09-30 から動いていた） |
| 12:55:13Z | app（新しい版 `f386b55f16a8`） | 入れ替えの後、Docker のヘルスチェック | healthy（入れ替えの開始から約 17 秒） |
| 12:55 頃 | app | `GET http://127.0.0.1:8080/actuator/health` | 200・`{"status":"UP"}` |
| 13:04:42Z | app | S5 の複写のための停止（約 10 秒）の後、起動し直し | healthy |
| 13:05 頃 | mailpit・targetdb-postgres | `docker compose ps` | mailpit は healthy、targetdb-postgres は Up（どちらも配備では触っていない） |

- 起動のログに ERROR は無い（`smoke-test-results.md` の S2）。
- PC に開いている番号は前と同じ `127.0.0.1` の 8080・8025・1025（Environment Provisioning の確かめから変えていない）。
- 判定: 合格。

## Sources

- `aidlc/spaces/default/intents/260930-user-admin/operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/validation-report.md`

## Assumptions & Open Questions

None.
