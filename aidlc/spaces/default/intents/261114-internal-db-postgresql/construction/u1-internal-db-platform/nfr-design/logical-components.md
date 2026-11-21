# 論理の部品と、NFR の決まりを当てる場所 — u1-internal-db-platform

この単位が持つ部品は、`cherry.mastersmith.internaldb.platform`（`TraceAspect` の対象の層の外の用途名のパッケージ。D-1）の中だけです。インフラの部品（サーバー・キュー・キャッシュ）は足しません。ここでは、機能設計の部品（`functional-spec.md` の 3 節）に、NFR の決まりをどこで当てるかを書きます。

## 部品の一覧と受け持つ NFR

「DD-n」は設計の決定の番号で、`reliability-design.md`（DD-1・DD-2・DD-3・DD-5）と `performance-design.md`（DD-6・DD-7）に書く。

| 部品（目安の名前） | 起動のどこで動くか | 受け持つ NFR | 設計の決定 |
|---|---|---|---|
| `InternalDbKind`（列挙） | 値 | NFR4.1・NFR3.1 | 種類ごとの URL の先頭・移行の置き場・接続のたびの文を持つ。H2 の接続のたびの文は無し |
| `InternalDbUrlResolver`（純粋な判断） | `InternalDbPoolCustomizer` と `InternalDbSettings` から呼ばれる | NFR2.2・NFR4.4・NFR6.1 | 例外を投げない純粋関数。単体テストだけで全分岐を通す |
| `InternalDbSettings`（値） | Flyway の設定の差し替えが使う | NFR4.1 | `InternalDbUrlResolver` を呼んだ結果を 1 つ持つ（DD-2） |
| `InternalDbPoolCustomizer`（静的な `BeanPostProcessor`） | 内部DB の接続プールが作られた直後、最初の接続の前 | NFR4.1・NFR1.4・NFR2.2・NFR4.4・NFR4.10 | 当てる対象を絞る（DD-1）。EMPTY・UNSUPPORTED を報告して起動を止める（DD-3） |
| `InternalDbFlywayCustomizer`（`FlywayConfigurationCustomizer`） | Flyway の設定を組むとき | NFR4.6・NFR4.7 | 種類に応じた置き場（`classpath:db/migration` か `classpath:db/migration-postgresql`）を渡す |
| `InternalDbMigrationStrategy`（`FlywayMigrationStrategy`） | `flyway.migrate()` を呼ぶとき | NFR2.2・NFR2.3・NFR4.2・NFR4.3・NFR4.4・NFR1.2 | 失敗を受けて ENT-005 に写し、cause の無い例外で投げ直す。移行の時間を測る（DD-6） |
| `StartupFailureReporter`・`StartupFailureReport` | 失敗の記録 | NFR2.2・NFR2.4・NFR4.4 | ERROR 1 件・キーと値。文は読まない（版の番号の数字を取る所だけ例外） |
| `InternalDbStartupException` | 起動を止める | NFR2.3 | 文は failureKind と固定の説明だけ。cause 無し |
| テストの部品 `internaldb/testsupport`（`@InternalDbPostgresIT`・コンテナの保持・専用データベース） | テスト | NFR4.8・NFR4.9・NFR5.2・NFR2.9 | DD-7 |
| `InternalDbPostgresRuntimeCheck`（テスト用） | テスト | NFR4.8 | コンテナの実行環境の判定。内部DB 用の別クラス（D-7） |

## 失敗の範囲（failure domain）と影響の範囲

| 失敗 | 影響の範囲 | 守り |
|---|---|---|
| 接続先が空・対応しない種類 | 起動できない（アプリ全体） | 起動の入口で止め、ERROR 1 件（NFR4.4） |
| 内部DB に接続できない（起動時） | 起動できない | 同上。`CONNECTION_FAILED`（SQLState の先頭 08・28・3D） |
| 移行の失敗・外の接続に握られた | 起動できない | `MIGRATION_FAILED`（版の番号つき）。2 秒で止まる（DD-5） |
| 動作中に内部DB に届かない | その要求は失敗（ヘルスは DOWN） | 今のまま（NFR2.7）。待ちは `lock_timeout` 2 秒・接続の待ちは今の接続プールの設定 |
| 対象DB のプール | 影響を受けない | `InternalDbPoolCustomizer` は内部DB のプール以外に当てない（DD-1） |

## 共有する資源

- 内部DB の接続プール `mastersmith-db`（上限 30）は、内部DB を使う全機能で共有する今のまま。PostgreSQL の接続の数は 30 本までが内部DB 分。
- ログ出力（SLF4J）。ロガーの名前は `cherry.mastersmith.internaldb.platform` の下。

## 境界テスト

- `InternalDbBoundaryArchitectureTest` で、`internaldb.platform` が依存してよい先を固定する: JDK・Spring・Flyway・Hikari・SLF4J・`internaldb` の中だけ。業務の機能（`auth`・`user` など）と `targetdb` に依存せず、依存もされない（Spring の自動の組み立てを除く）。既存の `ArchitectureTest` と他の機能の境界テストは書き換えない（NFR9.2）。

## Sources

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/functional-spec.md`（3 節の部品、D-1〜D-10）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-requirements/tech-stack-decisions.md`・`reliability-requirements.md`
- `aidlc/spaces/default/memory/project.md`（Code Style: `TraceAspect`）・`team.md`（Code Style: 境界テスト）

## Assumptions & Open Questions

- [assumption] 部品の Java の名前は目安で、コード生成の計画で確定する。役割と置き場（`internaldb.platform`）は決まり。
