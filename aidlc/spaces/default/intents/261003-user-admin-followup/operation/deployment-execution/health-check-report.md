# ヘルスチェックの報告（261003-user-admin-followup）

| 時刻（UTC） | 対象 | 確かめ | 結果 |
|---|---|---|---|
| 2026-10-03T23:53:57Z | app（前の版 `f386b55f16a8`） | T1 の負荷の試験の後の起動し直し | healthy、`/actuator/health` 200 |
| 2026-10-04T00:15:09Z | app（前の版） | 入れ替えの前 | healthy |
| 00:15:19Z | app（新しい版 `f124296eb7ca`） | 入れ替えの後、Docker のヘルスチェック | healthy（入れ替えの開始から約 10 秒） |
| 00:15 頃 | app | `GET http://127.0.0.1:8080/actuator/health` | 200・`{"status":"UP"}` |
| 00:19:46Z | app | S6 の複写のための停止（約 10 秒）の後、起動し直し | healthy |
| 00:19 頃 | mailpit・targetdb-postgres | `docker ps` | mailpit は healthy、targetdb-postgres は Up（どちらも配備では触っていない） |

- 起動のログに ERROR は無い（`smoke-test-results.md` の S1）。
- 判定: 合格。

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/intents/261003-user-admin-followup/operation/deployment-pipeline/deployment-strategy.md`

## Assumptions & Open Questions

None.
