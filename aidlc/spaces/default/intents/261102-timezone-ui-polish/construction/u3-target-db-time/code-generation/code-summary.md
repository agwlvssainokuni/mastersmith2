# コード生成のまとめ — u3-target-db-time

## 作った・変えたファイル

`source-manifest.json` のとおり。要点:

- 接続の初期化: `targetdb/config/TargetDataSourceConfig`（種類 × 用途 → 初期化の文を引く1か所の対応 `connectionInitSql`、PostgreSQL も `SET TIME ZONE 'UTC'`）・`TargetWriteDataSourceConfig`
- 読み取り: 新しい `businessdata/repository/TemporalCells`（桁ごとの書式・桁を固定した整形・文字で受けた値の読み取り。JDBC を使わない）、`CellValue.Raw`、`ColumnValueCodec.readCell(..., Integer digits)`、`RowDialect` に `fractionDigits`・`isUnreadableSentinel`・`comparisonValue` を追加（`PostgresRowDialect`・`MysqlFamilyRowDialect`）、`RowSqlExecutor`（桁の配列を結果ごとに1回、`Raw` を行へ入れる）
- 書き込み・検索: `timetz` を時差ごと保持（`parseTimeTz`・`format`）、比較の値だけ PostgreSQL で UTC に直す（`RowSqlBuilder`・`RowWriteSqlBuilder`の5か所）、端数の確かめは明示のない桁を確かめない（D-P8）
- 型の名前: `RowDialect.baseName` が `timestamp(3) with time zone` などの時差の有無を残す（BR3.7）
- 既定の DSL の生成: 両 `SchemaQueries` の精度の列を `COALESCE(NUMERIC_PRECISION, DATETIME_PRECISION)` にし、`DslTreeBuilder` が日時・時刻の分類の列だけ、製品の既定と違う桁を `dbType.precision` に書く（日付・interval には書かない）

新しい依存・API・表・列・code は足していない。`build.gradle.kts`・`gradle.lockfile`・`db/migration`・Dockerfile・compose・`.env.example`・`spotbugs-exclude.xml`・ArchUnit の境界テスト・`packagesJudgedByTotal` は変更なし（`git diff` で確認、Step 21）。

## テストの数と実測

- バックエンド全体を `:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 付き、`caffeinate -dims`）で実行: 単体 2868 件 + 結合 1673 件 = 4541 件、失敗 0（所要約 36 分）。
- 実測のカバレッジ（行 / 分岐）: `targetdb.config` 100.0% / 99.1%、`targetdb.repository` 96.0% / 87.5%、`businessdata.repository` 96.9% / 90.1%、`businessdata.domain` 98.2% / 91.5%、`dslmanage.generate` 99.4% / 100.0%。いずれも下限（行 80%・分岐 70%）以上。カバレッジの除外は増やしていない。
- 新規・追加した主なテスト: `TemporalCellsTest`、`CellValueTest`、`TemporalCellCostTest`（2）、`TemporalCellPropertyTest`（jqwik 4）、`ColumnValueCodecTest`・`RowDialectTest`・`RowSqlExecutorTest`・`RowWriteSqlBuilderTest`・`DslTreeBuilderTest`・両 `*DataSourceConfigTest` の追加と書き換え、結合: `AbstractBusinessDataReadIT.temporalDigitsAndUnreadableCells`（3種類の対象DB）、`AbstractBusinessDataWriteIT.temporalWrites`（3種類）、`TargetDbTimeZoneIT`（3種類、JVM の既定のタイムゾーンを変えて不変）、`TargetDbInitFailureIT`。

## 変換の費用の測り（NFR2.1.3・NFR2.1.4）

環境: JDK 25.0.5 / aarch64 / 8 CPU。暖機の後 5 回の中央値。通常の混在 1 万セル: 中央値 3.15 ms（平均 0.315 µs）、最悪（1 万個すべて読めない値）: 中央値 50.5 ms（平均 5.05 µs）。目標（合計 500 ms・平均 0.05 ms）以内。暖機前の 1 回目は 9.1 ms（通常）・101 ms（最悪）。目標は緩めていない。

## 計画との差・決めたこと

- 読めない主キーの行の拒否は計画どおり既存の 400 `VALIDATION_FAILED`（D-P2。機能設計 BR3.12 の 409 `BUSINESS_ROW_OPERATION_NOT_ALLOWED` とは状態コードが違う。承認の場の決定）。service に特例の分岐は足していない。Raw の主キーを送り返す経路の専用テストは書いていない（既存の主キーの値の読み取りが型として読めず空にする経路に依存。未確認）。
- D-P8: 桁の明示のない列は端数を確かめない（DB が丸める）。承認の場の決定どおり。DB の丸めの動作を固定する結合テストは足していない。
- D-P7（BR3.18）: 先に失敗するテストで確かめる手順は踏めなかった。既存の `RowDialectTest.baseName` が `timestamp(3) with time zone` → `timestamp`（時差を持たない日時）を期待しており、これが不具合の現状を示す記録。直した後は `timestamp with time zone` を期待。生成が日時の桁を読まない点（D-P6）も、先に失敗させる手順は踏まず、直した後の表の単体テストと結合（`AbstractSchemaQueriesIT` 等は通過）で確かめた。
- 既存テストの期待の書き換え（計画の「影響の範囲」内）: 両 `*DataSourceConfigTest`（PostgreSQL も初期化の文）、`ColumnValueCodecTest`（`timetz` は時差を保つ、`Raw`、明示のない桁は null）、`RowDialectTest`（baseName）、`AbstractBusinessDataReadIT`（ゼロ日付・838 時間は 409 ではなくそのセルだけ DB の文字、`timestamptz`/`timetz` の桁つきの文字、`timetz` は DB の時差）。
- 計画より縮めた結合テスト（呼び出し元の判断を仰ぐ）: Step 14〜16・19 は、専用の抽象クラス＋3具象ではなく、既存の `AbstractBusinessDataReadIT`/`WriteIT`（3種類で実行）に1件ずつと `BusinessDataReadTables` に `times` 表を足す形にした。足していないもの: 桁の境界（1桁超は 400）・DSL の桁 < DB の桁・`timetz` の非 UTC の主キー更新（検索は既存の `timeWithTimeZone` が時差違いの行の一致を確かめる）・`timestamp` の範囲外の製品別の動作・既定の桁の列への端数・日時専用の `*SecretLeakIT`/`*TraceExposureIT` の追加・接続の数の追加の確認（既存の `BusinessDataConnectionUsageIT` 等は通過）。Build and Test で足すか判断が要る。
- `ColumnValueCodec.fractionDigits` の `product` 引数は使われなくなった（呼び出しの互換のため残した）。

## 未実行

- `./gradlew verify` 全体（フォーマットは `spotlessApply` で整形済み。SpotBugs・OSV-Scanner・Gitleaks・フロントエンドの検査・全体のカバレッジの床の判定は未実行）: 呼び出し元が流す。
- `./gradlew e2eTest`: 呼び出し元が流す（Step 22・23 は未チェック）。
- 既存の p95 の前後の値（NFR2.1.5）は Build and Test、初期化の失敗の見分け方の手順書は Incident Response へ引き継ぎ。
