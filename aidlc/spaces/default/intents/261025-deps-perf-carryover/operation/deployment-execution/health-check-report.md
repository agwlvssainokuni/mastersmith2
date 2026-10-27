# 健全性の確認の報告（health-check-report）

Intent 261025-deps-perf-carryover の配備の後の健全性の確認です。

| 確かめ | 時点 | 結果 |
|---|---|---|
| `docker compose ps` の app | 入れ替えの後（約 `2026-10-26T23:45:21Z`） | healthy（新しいイメージ `sha256:3075065a2653…`） |
| `/actuator/health` | 同上 | 200 |
| 起動のログのレベル | 入れ替えの直後 | INFO 37・WARN 2・ERROR 0 |
| 起動のログのレベル | S5〜S7 の後 | INFO 39・WARN 2・ERROR 0 |
| Flyway | 起動時 | 13 件を検証、現在の版 13、`Migrating schema` 0 件 |
| JVM の設定 | PID 1 のコマンド行 | `ExitOnOutOfMemoryError`・`MaxRAMPercentage=50.0` 各 1 件 |
| 読み取りのプール | WAR の既定 | 10（`.env` の上書きなし） |
| 配備の後の複写のための再起動 | 2026-10-27T15:52:08Z〜15:52:18Z | 約 10 秒で healthy に復帰 |
| 周辺のコンテナ | 配備の後 | 見本の PG は起動（公開は `5432/tcp` のみ）、Mailpit v1.31.5 は healthy、otel-lgtm は停止中 |
| 周辺のコンテナ（Request Changes の後） | Mailpit と otel-lgtm の作り直しの後 | Mailpit v1.32.0 は healthy（Messages 0）、otel-lgtm 0.38.0 は healthy（Grafana `/api/health` 200、メモリ約 0.8GiB / 1.5GiB）。アプリ（`sha256:3075065a2653…`）は healthy のまま |

WARN の 2 件は、前の版から出ている Flyway の H2 の版の案内と Spring の Bean の案内で、今回の変更によるものではない。健全性の問題は無い。
