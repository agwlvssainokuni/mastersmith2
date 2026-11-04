# インフラの仕様 — u4-business-data-time

出典: NFR Design（`construction/u4-business-data-time/nfr-design/logical-components.md` の 4〜5 節・`performance-design.md`・`security-design.md`）、機能設計（`functional-design/functional-spec.md` の D30・5 節）、部品の一覧（`inception/domain-design/components.md` の BusinessDataTimeView）、契約（`inception/contract-design/contract-summary.md` の C4）、既存の実物（読み取りだけ: `Dockerfile`・`compose.yaml`・ルートの `build.gradle.kts` の `e2eTest` の準備・`frontend/e2e/support/businessDataSchema.sql`）、`project.md`（Deployment）。検査の流れは `cicd-pipeline.md`、監視は `monitoring-design.md`。

U4 は、業務データの画面の時刻の表示・入力・検索を直し（画面）、定義の応答の参照の項目に `labelValueType` を1項目足す（サーバー）単位（種類 ui）。新しい資源（表・列・環境変数・コンテナ・ポート・ボリューム・ネットワーク）は作らない。E2E の実行のスキーマに表を1つ足すだけ（テストの資源）。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤は作らない（`project.md` Deployment）。新しい設計の論点が無いため、質問は作らず、既にある資源と流れの記録として書く（`project.md`）。

## Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形（compute model） | 変更なし。既存のアプリのコンテナ（`/app/mastersmith.war`）。画面の変更（`dist`）とサーバーの変更（`businessdata` の応答の1項目）は同じ実行可能 WAR に入る | 埋め込み（`unit-of-work.md`）。サーバーのコードも変えるため、WAR を作り直して入れ替える |
| ネットワーク | 変更なし。新しい道・ポート・ヘッダーの規則は無い。業務データの API の道・認証・認可は今のまま | `functional-spec.md` 5 節 |
| 保存（storage） | 変更なし。内部DB・対象DB・ボリュームに新しい表・列・移行を足さない。業務データの値の形（C4）も変えない | `logical-components.md` 4 節 |
| 環境 | 開発者の PC 上のコンテナ 1 つだけ。環境差は無い | 配備先が決まるまでの方針 |
| IaC | 作らない。`Dockerfile`・`compose.yaml`・`docker/` は変えない | 変更が無い |
| 資源の大きさ | 変更なし。定義の応答は参照の項目1つにつき最大 29 バイト増える。画面の変換はブラウザの中で、サーバーの CPU・メモリ・接続の増えは無い | `performance-design.md` 1.2 |
| 実行時の設定 | 変更なし。環境変数・`.env`・タイムゾーンの4か所の設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM・E2E の起動）は触れない | `team.md` Deployment |
| 組み立ての経路 | `frontend/src` → `frontendBuild` → `frontend/dist` → `bootWar` → `mastersmith.war` → Dockerfile の `COPY`。Dockerfile は手元の WAR を写すだけ | `project.md` の学び。配備の前に WAR を確かめる（`cicd-pipeline.md` 3 節） |
| 前の版との互換・戻し | 内部DB・設定の変更が無く、応答は項目を足すだけ。戻しは前の版のイメージへの入れ替えだけ（画面とサーバーがそろって戻る） | NFR4.2.1 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（H2） | database（今のまま） | 変更なし | 表・列・移行を足さない |
| 対象DB（MySQL・MariaDB・PostgreSQL） | database（今のまま） | 変更なし | 読み書きの SQL・接続の数・接続のタイムゾーン（U3 で UTC に明示済み）を変えない |
| E2E の見本の対象DB（PostgreSQL） | database（テスト） | `./gradlew e2eTest` が作る実行のスキーマに、`businessDataSchema.sql` で表 `e2e_event` を足す。権限は今の準備の「スキーマのすべての表への GRANT」で付く（読み取りと書き込みの一時のロール） | 見本の行は置かない。流れ 200 が作る行だけ。片付けは今の `e2eTest` の片付けのタスク（スキーマごと消す）のまま |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | なし | 新しく置かない | 定義の応答はキャッシュしない（今のまま） |
| 手元の監視（otel-lgtm） | 既存を使う | 変更なし | `monitoring-design.md` |
| make-you-chic-ui（サブモジュール） | 画面の部品 | 固定先は変えない（`FormField`・`TextInput`・`RadioGroup` は今の固定先にある） | 固定先を上げない。上流への依頼は無い |

## Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| E2E の実行のスキーマ（見本の PostgreSQL） | 既存（前の Intent の業務データの E2E） | U4（表 `e2e_event` と流れ 200）・既存の 180・185・190・195 | 表の追加だけ。既存の表 `e2e_item` と行・共有の DSL（`businessDataDsl.ts`）は変えない。200 はアプリで適用中の DSL を専用のもの（版の印 `E2ET_`）に置き換える。200 は最後に流れる前提で、200 より後の番号の流れは足さない（足すなら先頭で自分の DSL を適用する） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`
- 機能設計・部品・契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`、`inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`
- 既存の実物（読み取り）: `Dockerfile`・`compose.yaml`・`build.gradle.kts`（`e2eTest` の準備）・`frontend/e2e/support/businessDataSchema.sql`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 配備先は開発者の PC 上のコンテナのままで、この単位の間に決まらない。
- 未解決の点は無い。
