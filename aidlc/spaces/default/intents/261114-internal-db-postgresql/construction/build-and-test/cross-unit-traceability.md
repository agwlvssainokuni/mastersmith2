# 単位をまたぐ最終の網羅の確認 — Intent 261114-internal-db-postgresql

`aidlc engine sensor-traceability --stage build-and-test` の出力（2 回目、2026-11-28。u3 の `traceability.json` を直した後）から書いた。

## 判定

**fail**（`pass: false`）。満たされていないのは **NFR1・NFR2・NFR4・NFR5** の 4 件です。FR のすべて（FR1.1〜FR5.5）と NFR3・NFR6・NFR7・NFR8・NFR9 は満たされました（1 回目の fail の原因だった u3 の対象の書き方は直った）。残りの 4 件は、実装が作られていないことではなく、対象がファイルを持たない枝番（測っていない・受け入れ・CI の時間）が `not_built` になっていることによるものです。

## 満たされていない要素と持ち主

| 要件 | `not_built` の枝番 | 中身 | 持ち主の段 | `Target Verification Matrix` の行 |
|---|---|---|---|---|
| NFR1 | u2 NFR1.4 | PostgreSQL のときの書き込みの所要時間は U2 では測らない（基準の値は U1 の起動の時間だけ）。測っていないことをこの記録に書く、という確かめ | build-and-test（この記録に書いて確かめた） | `U2-NFR1.4`（Met・今回足した行）、`REQ-NFR1-PG`（N/A）、`U1-NFR1.2`（Met） |
| NFR2 | u3 NFR2.1 | u3 が受け持つテストの出力に接続先・資格情報を出さない確かめ。接続先（資格情報を含まない）が起動のログに出る点は、依頼者の決定（2026-11-28）で既知の制約として受け入れた | 承認の場の決定（受け入れ済み） | `REQ-NFR2-HOST`（Not Met・受け入れ済み）、`U1-NFR2`（Met）、`U2-U3-NFR2`（Met） |
| NFR4 | u2 NFR4.9 | 書き込みの手順の変更が監査の内容・タイミング・件数・種類を変えない。確かめは既存の監査の結合テストの通過で、ファイルでなくテスト群 | build-and-test（確かめた） | `U2-NFR4.9`（Met・今回足した行）、`U2-NFR4`（Met） |
| NFR5 | u1 NFR5.1・NFR5.3・NFR5.4、u2 NFR5.1〜5.3、u3 NFR5.1 | 検査の時間（CI の検査の job の時間と基準 21分32秒の 1.1 倍・約 42 分の線との比較、増分、手元の全件の `verify` の時間）。対象がコードでなく測定の記録 | CI の時間は ci-pipeline（push 後に測る）。手元の時間の記録は build-and-test（済み） | `U1-NFR5.1`（Unverified・ci-pipeline）、`U1-NFR5.3`（Unverified・ci-pipeline）、`U1-NFR5.2`（Met）、`U1-NFR5.4`（Met）、`U3-NFR5.3`（Met）、`TC-CI-MANUAL`（Unverified・ci-pipeline） |

N/A として受け入れられている枝番（センサーの `not_applicable`）: u1 NFR1.3（k6・perf の環境を変えない）、u1 NFR2.7（動作中の経路は今のまま）、u1 NFR5.5（判断の手順）、u1 NFR7.1・7.2・7.5（コードを持たない）。

## 満たされている要素（ID ごとの対応）

| ID | 持ち主の単位 | 対象のファイル（代表） |
|---|---|---|
| FR1.1〜FR1.4 | u1 | `InternalDbH2PreviousVersionIT`・`InternalDbUrlResolverTest`・`InternalDbStartupFailureIT`・`InternalDbConfigurationFilesTest`（`backend/src/test/java/cherry/mastersmith/internaldb/…`） |
| FR2.1〜FR2.5 | u1 | `InternalDbPostgresMigrationIT`・`H2MigrationFilesUnchangedTest`・`MigrationSetsConsistencyTest`・`InternalDbPostgresMigrationFailureIT` |
| FR3.1・FR3.4・FR3.8 | u3 | 実在する 1 つのファイルに直った（`PgAuthFlowIT`・`PgFailureTransactionIT`・`PgUserSearchIT` など） |
| FR3.2・FR3.3・FR3.6・FR3.7・FR3.9・FR3.10 | u1・u2 | `db/migration-postgresql/`・`InternalDbPoolCustomizer`・`DslManageRepositoryIT`・`InternalDbPostgresHealthIT`・`StartupFailureReporter` |
| FR3.5 | u2 | `KeyedUpsertExecutorTest` |
| FR4.1〜FR4.5 | u4 | `compose.yaml`・`.github/dependabot.yml`・`docs/internal-db.md`・`README.md` |
| FR5.1〜FR5.5 | u1・u2 | `InternalDbPostgresContainer`・`backend/build.gradle.kts`・`InternalDbPostgresRuntimeCheck`・`WorkRoleSelectionMergeIT`・`StartupFailureReporterTest` |
| NFR3 | u3 | u3 の NFR3.1（直しで足した） |
| NFR6 | u3 | u3 の NFR6.1・NFR6.2（直しで足した） |
| NFR7 | u1・u2・u3 | 枝番 NFR7.1〜7.5 ほか |
| NFR8 | u2・u3 | u2 NFR8.1・u3 NFR8.1 |
| NFR9 | u2・u3 | u2 NFR9.2・u3 NFR9.1〜9.3 |

## 承認の場に出す所見

- 判定が fail のまま残る 4 件は、上の表のとおり持ち主が決まっています。NFR1・NFR4 は、この記録で確かめて `Target Verification Matrix` に Met の行を足しました（センサーは対象がファイルでない枝番を `not_built` と読むため、判定は変わらない）。NFR2 は依頼者が受け入れ済みで、NFR5 は ci-pipeline（push 後）で埋まります。
- この fail は、実装の欠けを示すものではありません。承認の場で、この読みを受け入れるかを依頼者が決めます。
