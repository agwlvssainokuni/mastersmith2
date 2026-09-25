# 健全性の確かめ（health-check-report）

2026-09-25、配備した版 `87cc0fc`。

## 1. 状態

| 対象 | 確かめ方 | 結果 |
|---|---|---|
| アプリ（`mastersmith-app-1`） | compose の healthcheck（`/actuator/health` が 200） | healthy。入れ替えから約 7 秒で healthy。監査イベントの確かめで一度止めた後も healthy に戻った |
| アプリの健全性の API | `curl -s http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| 見本の対象DB（`mastersmith-targetdb-postgres-1`） | `docker compose logs targetdb-postgres` | `database system is ready to accept connections`、ERROR 0 |
| アプリの上限 | `docker inspect` | メモリ 2147483648（2g）・CPU 4（`NanoCpus` 4000000000） |
| アプリの環境変数（名前だけ） | `docker inspect`（名前だけを取り出して数えた） | `MASTERSMITH_SAMPLE_TARGETDB_` 0 件（FR6.2） |
| ログの形 | `docker compose logs app` | すべて1行の JSON、メッセージに改行を含む行 0（FR4.1・NFR4） |

## 2. 戻し方の前提

`mastersmith:pre-followup` を使い捨てのコンテナ（新しい `.env`、一時のボリューム）で起動し、約 9 秒で `/actuator/health` が 200、ERROR 0 を確かめた（`deployment-log.md` 3節）。

## 3. 続けて見ること

- `origin` へのプッシュの後の CI（GitHub Actions）の結果。
- 既知の制約: 動いている間の内部DB のファイルの伸び（U4-STORAGE-RUN）と、10MB の DSL とログインの重ねでのメモリ（2g の 93%）は、前の振り返りの束 2 のまま。

## Sources

- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-execution/deployment-log.md`
- `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-execution/smoke-test-results.md`

## Assumptions & Open Questions

- 常時の監視は動かしていない（手元の監視は見たいときだけ起動する。project.md の Deployment）。
