# インフラの仕様 — u2-display-time-zone

出典: NFR Design（`construction/u2-display-time-zone/nfr-design/` の `logical-components.md`・`reliability-design.md`・`scalability-design.md`・`performance-design.md`）、既存の実物（読み取りだけ: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/db/migration/`・`backend/build.gradle.kts`）、`team.md`・`project.md`（Deployment）。検査と配備の流れは `cicd-pipeline.md`、監視は `monitoring-design.md`。

U2 は、既存のアプリのコンテナの中で動く単位（種類 service）。内部DB の `users` に NULL 可の列を 1 つ足し、既存の API の項目を 1 つ足し、画面の配布と表示を足す。新しい資源は作らない。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作らない（`project.md` Deployment）。

## Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形（compute model） | 変更なし。既存のアプリのコンテナ（Eclipse Temurin の JRE 25、`/app/mastersmith.war`）。バックエンドの変更と画面の変更は同じ WAR に入る | 埋め込み。別には配備しない |
| ネットワーク | 変更なし。新しい道・ポート・外向きの通信を足さない。既存の `GET`・`PUT /api/me/preferences` の応答の項目を 1 つ足し、画面がログイン後に `GET` を 1 回増やす | 契約 C2・C3。`SecurityConfig` の分類（ログインだけが要る）も変えない |
| 保存（storage） | 内部DB（H2、既存のボリューム）の `users` に `display_time_zone VARCHAR(64)`（NULL 可・既定なし・索引なし・制約なし）を 1 列足す。移行 `V14__u2_display_time_zone.sql`（`ALTER TABLE` 1 文）。新しい表・ボリューム・ファイルは無い | 前進のみ。広げる側だけで、前の版が使う列は消さない（`team.md`） |
| 環境 | 開発者の PC 上のコンテナ 1 つだけ。環境差は無い | 配備先が決まるまでの方針 |
| IaC | 作らない（新しい資源が無い）。`Dockerfile`・`compose.yaml`・`.env.example` は変えない | 変更が無い |
| 資源の大きさ | 変更なし。列 1 つ（最大 64 文字）と応答の 1 項目で、CPU・メモリ・接続・ディスクの増えは無視できる。内部DB の接続は 1 要求で 1 本のまま（監査を書かないため、確定の後の 2 本目は無い）。ログイン後の `GET` が 1 回増える | 保存は 1 文で読み取りを足さない（`LC3`）。`SC1`〜`SC4` |
| 実行時の設定 | **変更なし**。環境変数・`.env` に項目を足さない。タイムゾーンの 4 か所（`Dockerfile` の `-Duser.timezone`・`compose.yaml` の `TZ`・テストの JVM の `user.timezone`・E2E の起動）は触れない。保存する時刻は UTC の時点のまま。表示のタイムゾーンは利用者ごとの値で、サーバーの JVM のタイムゾーンとは別 | `team.md` Deployment。差分で触れていないことを配備の記録に書く |
| 組み立ての経路 | `./gradlew verify`（画面のビルドを含む）→ `mastersmith.war` → `Dockerfile` の `COPY`。`Dockerfile` は手元の WAR を写すだけで WAR を作り直さない | 実物で確かめた。配備の前に WAR の中身（V14 の移行のファイル）を確かめる（`cicd-pipeline.md` 3.2節） |
| 移行の適用 | アプリの起動時に Flyway が V14 を 1 件適用する。失敗したら起動しない。複写は入れ替えのための停止の時間に取る | `cicd-pipeline.md` 3.1・3.2節 |
| 戻し | 直前の版のイメージ（戻し用のタグ）での再配備だけ。列は消さず、前の版は列を読まずに動く | `cicd-pipeline.md` 3.3節 |

## Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（H2） | database（既存） | 列 1 つの追加。接続の数・プールの上限・ボリュームは変えない。`-Dh2.compactThreads=1` は触れない | 保存の 1 文は H2 の `FINAL TABLE (UPDATE …)`。行の排他の待ちの上限切れは既存の想定外の例外の扱いに乗る（`RD2`） |
| 対象DB（MySQL・MariaDB・PostgreSQL） | なし（使わない） | 変更なし | 業務データの日時の変換は別の単位が受け持つ。この単位は対象DB に触れない |
| キャッシュ・キュー・検索・CDN・DNS・ロードバランサー | なし | 新しく置かない | 画面の整形器のキャッシュ（時刻変換の部品。上限 128 個、いっぱいで全部消す）は画面の中だけで、承認済みの決まりのまま変えない |
| メール（SMTP） | なし | 変更なし | 送らない |
| 手元の監視（otel-lgtm） | 既存を使う | 変更なし | `monitoring-design.md` |

## Shared Infrastructure

共有する資源は、内部DB の `users` の表（既存の利用者の管理・プリファレンスと同じ表）だけ。新しい接続・キャッシュ・状態を共有しない。

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| `users.display_time_zone`（列） | u2-display-time-zone | 業務データの日時の変換を受け持つ単位（読まない。この Intent では接続しない） | 書くのは保存の 1 文だけ（列は更新不可の印で読み取りの対応づけ）。応答は本人の分だけ（認証の主体から決める） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-design/`
- 部品の定義（components）: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/components.md`（表示のタイムゾーンの保存と配布の部品。新しい部品は足さず、列 1 つの追加だけ）
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C2・C3）
- 既存の実物（読み取り）: `Dockerfile`・`compose.yaml`・`backend/src/main/resources/db/migration/`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 列 1 つの追加は H2 で表を書き換えず、起動の時間と内部DB のファイルの大きさに目に見える影響が無い。配備の記録で起動の時間を確かめる。
- [assumption] 業務データの日時の変換を受け持つ単位は、この列を読まない（表示のタイムゾーンは画面の側の値）。違えば共有の表を書き直す。
- 未解決の点は無い。
