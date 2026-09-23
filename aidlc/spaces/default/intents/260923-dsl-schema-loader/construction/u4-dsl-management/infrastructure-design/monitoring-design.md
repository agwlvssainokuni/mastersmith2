# Monitoring Design — U4 DSL の管理（u4-dsl-management）

U4 の監視の作り。手元の監視（grafana/otel-lgtm を compose の profile で必要なときだけ起動し、ダッシュボードと警報の決まりはファイルでリポジトリに置く）に従う（`aidlc/spaces/default/memory/project.md` の Deployment）。指標とログの中身は U4 の NFR 設計の observability-design.md。

## 1. 指標

| 指標 | 型 | タグ | 見るもの |
|---|---|---|---|
| `mastersmith.dsl.operation` | Timer（95 パーセンタイル） | `operation`（generate・submit・restore・apply・discard・compare）、`outcome`（success・rejected・failed・busy） | 操作ごとの件数と時間、拒否・失敗・`DSL_BUSY` の件数 |
| `http.server.requests`（既存） | Timer | 既存 | API ごとの応答時間と状態コード |

## 2. ダッシュボード

| 置き場 | 足すもの |
|---|---|
| `docker/monitoring/dashboards/mastersmith-overview.json`（既存） | DSL の操作の行（パネルのまとまり）: 操作ごとの件数（時間の推移）、結果ごとの件数、操作ごとの時間の 95 パーセンタイル（生成 30 秒・プレビュー 10 秒・軽い API 1 秒の目標の線つき） |

- パネルの式は、指標の名前とタグを実際に起動して確かめてから書き、書いた後にすべての式を実行して確かめる（project.md の Corrections）。
- 画面からは変えない（ファイルが正）。

## 3. 警報

足さない（管理者の手動の操作で、まれなため。U4 の NFR 要件の Q4: A）。SLO と警報は配備先が決まってから決める（team.md の Deployment）。

## 4. ログ

- 操作ごとに INFO 1件（キーと値）。本文・接続先・パスワードを出さない。
- 想定内の失敗は WARN 以下、想定外は ERROR（既存の共通の変換）。
- 手元の監視のログの画面で、`dsl.operation` のキーで絞って見られることを確かめる。
