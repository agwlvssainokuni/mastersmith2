# 監視の設計 — u2-portable-sql

この単位が足す監視の口はありません。新しい指標・警報・ダッシュボード・トレースの属性・ログの口を足さず、既存の口が変わらないことを確かめるだけです（`nfr-design/observability-design.md` と同じ）。

## Metrics & KPIs

- 内部DB の接続プール `mastersmith-db` の指標（`hikaricp_connections_*`）の名前・ラベルは変わらない。書き込みの手順が接続を新しく借りないため、借りる回数・待ちの指標の値も変わらない見込み（NFR4.8・NFR7.2）。
- 既存の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml` の `hikaricp_connections_pending`・`ms-app-absent`）とダッシュボードは書き換えない。確かめ: `docker/monitoring/` に差分が無いこと（コード生成の最後に `git diff --stat` で確かめる）。
- `/actuator/health` の内部DB の確認（`TimeBoundedDbHealthIndicator`）は変えない。

## Logs & Tracing

- 新しいログを足さない（鍵の重なり・やり直し・上限切れを出さない。NFR2.6）。失敗は今までの経路（`GlobalExceptionHandler`・各機能の分類）で、変換する境界で 1 回だけ、例外のクラス名だけが出る。
- TRACE のログ: `TraceAspect` の対象の層（`user.repository` の `UserRepositoryImpl` を含む）で、氏名を伏せ字の型のまま引数に取る今の形を保つ。手順の部品（`common.persistence`）は層の外のため TRACE に出ない。確かめ: `PreferencesSecretLeakIT` が書き直しの後も通ること。

## Alerts

- 新しい警報を足さない。書き込みの失敗の増加は、既存の経路（ログのレベル・状態コード）で見る。起動の失敗はこの単位に無い（U1 が持つ）。

## Dashboards・SLI・SLO

- 新しいダッシュボードを足さない。数値の SLO は置かない（配備先が決まっていないため）。確かめる項目は、ヘルスチェックの UP・DOWN と接続プールの待ちが変わらないこと。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/observability-design.md`・`security-design.md`
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/monitoring-design.md`
- `aidlc/spaces/default/memory/project.md`（Code Style・Deployment）

## Assumptions & Open Questions

None.
