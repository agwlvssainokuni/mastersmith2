# インフラの仕様 — u5-menu-icons-home

出典: NFR Design（`construction/u5-menu-icons-home/nfr-design/logical-components.md` の 5 節・`performance-design.md`・`security-design.md`）、NFR Requirements（`nfr-requirements/tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md` の D6・D20・D32）、部品の一覧（`inception/domain-design/components.md`）、契約（`inception/contract-design/contract-summary.md` の C5）、既存の実物（読み取りだけ: `Dockerfile`・`compose.yaml`）、`project.md`（Deployment）。検査の流れは `cicd-pipeline.md`、監視は `monitoring-design.md`。

U5 は、メニューの応答に項目を 1 つ足し、画面の部品を足す・直す・外す単位（種類 ui）。新しい資源（表・列・環境変数・コンテナ・ポート・ボリューム・ネットワーク）は作らない。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない（`project.md` Deployment）。

## Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形（compute model） | 変更なし。既存のアプリのコンテナ（Eclipse Temurin の JRE 25、`/app/mastersmith.war`）。サーバーの変更（`navigation` の `tableKind`）と画面の変更（`dist`）は、同じ実行可能 WAR に入る | 埋め込み。別には配備しない（`unit-of-work.md`）。この単位は**サーバーのコードも変える**ため、画面だけの差し替えでは配備できず、WAR を作り直して入れ替える |
| ネットワーク | 変更なし。新しい道・ポート・ヘッダーの規則は無い。`GET /api/me/navigation` の応答に項目が増えるだけで、認証・認可の規則は今のまま。廃止する `/admin` の画面の道は、静的な画面の道（SPA の振り分け）で、サーバーの設定に載っていない | functional-spec の D25（サーバー側の管理の API と 403 の判定は残す） |
| 保存（storage） | 変更なし。内部DB・対象DB・ボリュームに新しい表・列・移行・ファイルを足さない。許すアイコンの一覧（`allowed-icons.txt`・`allowedIcons.ts`）は変えない（24 個でそろっている） | functional-spec の D7・D32 |
| 環境 | 開発者の PC 上のコンテナ 1 つだけ。環境差は無い | 配備先が決まるまでの方針 |
| IaC | 作らない（新しい資源が無い）。`Dockerfile`・`compose.yaml`・`docker/` は変えない | 変更が無い |
| 資源の大きさ | 変更なし。WAR の増えは画面の部品とサーバーの小さなコードで数 KiB 程度。応答の増加は 1,000 項目で約 20 KB（圧縮前、1 要求あたり）。CPU・メモリ・接続の増えは無い | `performance-design.md` 1.3 |
| 実行時の設定 | 変更なし。環境変数・`.env`・タイムゾーンの 4 か所の設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM・E2E の起動）は触れない。新しい秘密・設定が無い | `team.md` Deployment |
| 組み立ての経路 | `frontend/src` → `frontendBuild` → `frontend/dist` → `bootWar`（バックエンドのクラスと一緒） → `mastersmith.war` → Dockerfile の `COPY`。Dockerfile は手元の WAR を写すだけで、WAR を作り直さない | 実物で確かめた（`Dockerfile`）。配備の前に WAR を `./gradlew verify` の後の物に確かめる（`cicd-pipeline.md` 3 節） |
| 前の版との互換・戻し | 内部DB・設定の変更が無く、応答は項目を足すだけ。戻しは前の版のイメージへの入れ替えだけ（前の版の画面と前の版のサーバーはそろって戻り、仮の画面と `/admin` も戻る） | NFR4.2・functional-spec の D32 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（H2） | なし（使わない） | 変更なし | 表・列・移行ファイルを足さない。利用者の設定（表示のタイムゾーン）の列は U2 が足す |
| 対象DB | なし（使わない） | 変更なし | 業務データに触れない。ホームは行の数などを問い合わせない（D20） |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | なし | 新しく置かない | メニューの応答はキャッシュしない（今のまま、要求ごとに DSL と権限の写しを 1 回ずつ読む） |
| メール（SMTP） | なし | 変更なし | 送らない |
| 手元の監視（otel-lgtm） | 既存を使う | 変更なし | `monitoring-design.md` |
| make-you-chic-ui（サブモジュール） | 画面の部品 | 固定先 `d112795`（変更なし。依頼 8 の取り込みは済み） | この単位は固定先を上げない。専用のコミットの fast-forward の例外は使わない |

## Shared Infrastructure

共有する資源は無い。この単位が新しく持つ接続・状態・キャッシュは無い。既存の共有（適用中の DSL と権限の写しの 1 要求あたり各 1 回の読み出し、シェルが 1 回読む業務のメニューの状態）は、`logical-components.md` の 4 節のとおりで、増やさない。このため、共有の資源の表は作らない。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/tech-stack-decisions.md`
- 機能設計・部品・契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`
- 既存の実物（読み取り）: `Dockerfile`・`compose.yaml`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 配備先は開発者の PC 上のコンテナのままで、この単位の間に決まらない。
- 未解決の点は無い。
