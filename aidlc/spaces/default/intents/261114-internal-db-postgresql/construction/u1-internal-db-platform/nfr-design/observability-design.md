# 観測の設計 — u1-internal-db-platform

この単位が足す観測の口は、ログの 3 種類（起動の失敗・内部DB の種類・移行の時間）と、既存のヘルスチェック・接続プールの指標の継続です。新しい指標・警報・ダッシュボード・トレースは足しません。

## ログの設計

すべて `cherry.mastersmith.internaldb.platform` の下のロガー、SLF4J のキー・値 API（文字列の連結にしない。`team.md` の Code Style）。

| 出来事 | 水準 | メッセージ（固定） | キー | 出す部品 |
|---|---|---|---|---|
| 起動時の失敗 | ERROR | `Internal DB startup failed` | `failureKind`・`exceptionTypes`・`sqlState`・`migrationVersion`・`settingName`・`supportedKinds`（持つものだけ） | `StartupFailureReporter` |
| 内部DB の種類 | INFO | `Internal DB kind resolved` | `internalDbKind`（`H2`・`POSTGRESQL`） | `InternalDbPoolCustomizer`（NFR4.10） |
| 移行の時間 | INFO | `Internal DB migration finished` | `migrationMillis`・`migrationCount` | `InternalDbMigrationStrategy`（NFR1.2 の測り） |

- どのキーにも、接続先・利用者名・パスワード・ホスト・ポート・URL・例外の文を入れない（NFR2.2）。
- 関連付けの ID（トレースの ID）は、起動の時点では要求が無いため付かない。付ける設計は置かない。

## 指標・警報・ダッシュボード（NFR4.11）

- 内部DB の接続プール `mastersmith-db` の指標（`hikaricp_connections_*`）は、Hikari と Micrometer の共通の指標で、PostgreSQL のときも同じ名前・同じラベルで出る。接続プールの設定を変えないため、プール名も変わらない。
- 既存の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml` の `hikaricp_connections_pending` の式）とダッシュボードは書き換えない。`docker/monitoring/` に差分が無いことを確かめる。
- PostgreSQL の結合テストが、`MeterRegistry` に `hikaricp.connections` のメーターが `pool=mastersmith-db` のタグで 1 つ以上あることを 1 回確かめる。この単位では、そのための確かめの口（テストの基盤から指標を読める形）を用意し、振る舞いの確かめの本数は U3 が持つ（NFR4.11 の「この単位が満たす」とは、確かめを流せる基盤と、PostgreSQL の起動でメーターが出ることの 1 本まで）。
- 起動時の失敗は、アプリが起動しないため警報の対象にならない。運用者がログで見る。

## ヘルスチェック（NFR4.5）

- `/actuator/health` の内部DB の確認は、既存の `TimeBoundedDbHealthIndicator` を使う。種類によらず、届けば UP・届かなければ DOWN。

## TRACE のログ（NFR4.12）

- `TraceAspect` の TRACE の出力に、内部DB の接続先を出さない。接続先を扱う部品は層の外に置く（security-design.md）。TRACE を有効にして起動する確かめを 1 本置く。

## SLI・SLO

数値の SLO は、この Intent では置かない（配備先が決まっていないため）。確かめる項目:

| 項目 | 見方 |
|---|---|
| 内部DB の種類 | INFO の `internalDbKind` |
| 起動時の失敗の原因 | ERROR の `failureKind` |
| 移行の重さ | INFO の `migrationMillis`（基準として記録するだけ） |
| 内部DB の生死 | `/actuator/health` の UP・DOWN |
| 接続プールの待ち | `hikaricp_connections_pending` |

## 採らなかった形

| 形 | 理由 |
|---|---|
| 内部DB の種類を指標のラベルにする | 既存の警報・ダッシュボードの式を変える必要がある。ログの 1 行で足りる |
| 起動時の失敗の専用の警報 | 起動しないため送れない。運用者がログと終了の状態を見る |

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/observability-requirements.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/reliability-requirements.md`（NFR4.5・NFR4.10）
- `docker/monitoring/provisioning/alerting/mastersmith.yaml`（`hikaricp_connections_pending`）
- `aidlc/spaces/default/memory/team.md`（Code Style: 構造化ログ）

## Assumptions & Open Questions

- [assumption] ログのメッセージの文言は目安で、コード生成の計画で確定する。キーの名前と、値に接続先を入れないことは決まり。
