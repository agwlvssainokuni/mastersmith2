# インフラの仕様 — u6-app-icon-assets

出典: NFR Design（`construction/u6-app-icon-assets/nfr-design/security-design.md`）、NFR Requirements（`nfr-requirements/tech-stack-decisions.md`）、既存の実物（読み取りだけ: `Dockerfile`・`compose.yaml`・`backend/build.gradle.kts` の `bootWar`）、`project.md`（Deployment）。検査の流れは `cicd-pipeline.md`、監視は `monitoring-design.md`。

U6 は素材の単位（種類 packaging）。新しい資源は作らず、実行可能 WAR の中の静的なファイルとして既存のコンテナに入る。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない（`project.md` Deployment）。

## Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形（compute model） | 変更なし。既存のアプリのコンテナ（Eclipse Temurin の JRE 25、`/app/mastersmith.war`）。素材は WAR の `WEB-INF/classes/static/` に入る | 埋め込み。別には配備しない（`unit-of-work.md`） |
| ネットワーク | 変更なし。素材は既存のアプリのポートの `/favicon.ico` などで、ログインなしの静的なファイルとして配る（`SecurityConfig` の `anyRequest().permitAll()`、`WebConfig` の資源の取り扱い） | ADR-008: サーバーに新しい道・コントローラーを足さない |
| 保存（storage） | 変更なし。素材はリポジトリの `frontend/public/` に置いてコミットし、Vite が `dist` へ、`bootWar` が WAR へ複写する。内部DB・ボリュームには置かない | バイナリは小さく変わらない（SVG 4 KiB・`.ico` 32 KiB・`.png` 16 KiB 以内） |
| 環境 | 開発者の PC 上のコンテナ 1 つだけ。環境差は無い | 配備先が決まるまでの方針 |
| IaC | 作らない（新しい資源が無い）。Dockerfile・compose.yaml は変えない | 変更が無い |
| 資源の大きさ | 変更なし。WAR の増えは素材の合計で数十 KiB 以下。CPU・メモリ・接続の増えは無い | 上限は素材のテストで機械的に守る |
| 実行時の設定 | 変更なし。環境変数・`.env`・タイムゾーンの 4 か所の設定（Dockerfile・compose・テストの JVM・E2E の起動）は触れない | 新しい秘密・設定が無い |
| 組み立ての経路 | `frontend/public/` → `frontendBuild` → `frontend/dist` → `bootWar` → `mastersmith.war` → Dockerfile の `COPY`。Dockerfile は手元の WAR を写すだけで、WAR を作り直さない | 実物で確かめた。配備の前に WAR の中身を確かめる（`cicd-pipeline.md` 3節） |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（H2） | なし（使わない） | 変更なし | 表・列・移行ファイルを足さない |
| 対象DB | なし（使わない） | 変更なし | 業務データに触れない |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | なし | 新しく置かない | 静的なファイルをアプリが直接返す。ブラウザの保持は `CacheControlFilter` の `no-cache`（`/assets/` 以外）のまま |
| メール（SMTP） | なし | 変更なし | 送らない |
| 手元の監視（otel-lgtm） | 既存を使う | 変更なし | `monitoring-design.md` |

## Shared Infrastructure

共有する資源は無い（新しい接続・キャッシュ・状態を持たない）。このため、共有の資源の表は作らない。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- この単位には functional-spec・rules が無い（素材と検査だけの単位）
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/tech-stack-decisions.md`
- 設計の決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-008）
- 既存の実物（読み取り）: `Dockerfile`・`backend/build.gradle.kts`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 素材の合計は数十 KiB 以下で、WAR の大きさと起動の時間に目に見える影響は無い。
- 未解決の点は無い。
