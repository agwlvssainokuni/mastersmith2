# CI/CD Pipeline — U4 DSL の管理（u4-dsl-management）

U4 の検査と配備の流れ。既存の流れ（`./gradlew verify`、GitHub Actions、手元のコンテナへの配備）に足すものだけを示す。

## 1. 検査の流れ（既存の `./gradlew verify`）

| 段 | U4 のテスト |
|---|---|
| `test`（単体） | 違いの求め方（テーブル・カラムの区分）、要約（表示名の未設定の先頭 100 件と総数）、照合の警告、ダウンロードのファイル名、道ごとの本文の上限の表 |
| `integrationTest`（結合） | 各 API の 401・403・200、投入の 10MB の境界と 413（`DSL_TOO_LARGE`）と操作した人つきの監査、`DSL_BUSY`（待ち合わせで重なりを作る）、適用のトランザクションと同時の適用（1件だけ成功）、適用の途中の失敗で巻き戻し、起動時の適用中の読み込み（読めないときは無いで起動）、監査の出来事と監査の書き込みの失敗、照合の打ち切り（応答しない対象DB。U1 の Testcontainers の DB を使う） |
| ArchUnit | `dslmanage` の層の境界（`web` から `repository` を呼ばない、トランザクションは `service` だけ） |
| 静的解析・脆弱性・秘密情報 | 既存（SpotBugs・OSV-Scanner・Gitleaks） |

- 一括のアクセス制御のテスト（C6 の 11 本の API を並べた 401・403・200）は U5 の Bolt で書く（U5 の機能設計の BR8.1a）。
- 時間（生成 30 秒・プレビュー 10 秒・軽い API 1 秒・ダウンロード 2 秒）、10MB の DSL のメモリ、H2 の大きさ、接続の2本使いのプールの余裕は Build and Test で測る。

## 2. 配備（手元のコンテナ）

- 既存の手順のまま（WAR を作ってイメージを作り、compose で起動）。V5・V6 はアプリの起動時に Flyway が当てる。
- 配備の後に、ヘルスチェックと、DSL の管理画面の今の状態の表示（スモークテスト）を確かめる（team.md の Deployment）。
- 戻し方は infrastructure-specification.md 5節。
