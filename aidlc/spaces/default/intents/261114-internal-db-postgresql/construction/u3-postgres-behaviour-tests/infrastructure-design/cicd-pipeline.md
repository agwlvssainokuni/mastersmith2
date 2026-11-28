# 配送の流れ（CI） — u3-postgres-behaviour-tests

この単位は CI の流れ（`.github/workflows/ci.yml`）を変えません。U1 が足した手動の実行の入力 `internal_db_postgres` で、U3 のテストも流れます（U3 のクラスは `@InternalDbPostgresIT` が付き、U1 の `internalDbPostgresTest` に入る）。普段の `develop` へのプッシュの流れには、構造のテスト 1 件（`*Test`）だけが入ります。

## 段と関門の対応

| 段 | 流れ | 関門 | この単位の変更 |
|---|---|---|---|
| 取得・準備 | checkout（サブモジュールつき）→ JDK 25 → Node 26 → Gradle → Gitleaks・OSV-Scanner | — | なし |
| 検査 | `./gradlew verify -PitForks=$IT_FORKS` | 失敗で全体を失敗 | 構造のテスト `PgTestBoundaryArchitectureTest`（1 件・コンテナを使わない）が単体テストに入る。PostgreSQL のテスト（24 クラス）は `integrationTest` から除外される |
| 成果物 | WAR の保存・規模とカバレッジの要約・HTML の報告 | — | なし（WAR に入るものが無い） |
| PostgreSQL の結合テスト | 手動の実行の入力 `internal_db_postgres`（U1）→ `./gradlew :backend:internalDbPostgresTest` | 失敗で手動の実行が失敗。コンテナの実行環境が無いときは失敗（CI） | U1 の 8 クラスに、U3 の 24 クラス（流れ 11・違反 2・待ち 4・失敗後 1・書き直し 3・型 3）が加わる。JaCoCo の記録は取らない |

## 変更の中身

- `ci.yml`・`build.gradle.kts`・依存・lockfile に手を入れない。足すのはテストのコード（`backend/src/test/java/` の `internaldb/testsupport`・`internaldb/postgres` と、機能ごとの `testsupport` の下の PostgreSQL 用の部品・テスト）だけ。
- 使う Actions は今のコミットのハッシュ固定のまま。秘密情報は使わない。

## 時間の扱い（NFR5.1〜NFR5.3）

- 通常の `push` の検査の job の時間は、構造のテスト 1 件の分（1 秒未満を、Gradle の結果の XML の `time` で記録する）だけ増える。Build and Test で、CI の検査の job の時間を基準（中央値 21分32秒）の 1.1 倍（約 23分42秒）と約 42 分の線と並べて記録する。
- 手動の実行（`internal_db_postgres`）の時間は、普段の検査の時間に含めず別に記録する（NFR5.2・NFR5.4）。待ちの確かめの 1 件ごとの時間は標準出力の `INTERNAL_DB_PG_WAIT` の行から表にする。

## デプロイ・戻し・昇格

- この単位は配備の流れを変えない（WAR に入るものが無い）。戻す対象が無い。
- 環境の昇格・フィーチャーフラグ・blue-green は、配備先が決まるまで無い（`team.md` の Deployment）。

## 秘密情報と固定先（Dependabot）

- 新しい依存・コンテナのイメージ・Action を足さない。固定先（`.github/dependabot.yml`）に変更は要らない。
- `docs/verification.md` の書き足し（U1 の入力名の説明と、U3 のテストを流す手順・待ちの確かめの時間の目安）は U4 が持つ。U1・U2 から持ち越した「`cicd-pipeline.md` の入力名の誤り（`internal_db_postgresql` は誤りで、正しくは `internal_db_postgres`）」は、U3 でも直さず（U1 の記録を書き換えない）、U4 の文書で正しい名前を書く。

## Sources

- `.github/workflows/ci.yml`・`docs/verification.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/performance-design.md`（DD-9・DD-10）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/cicd-pipeline.md`
- `aidlc/spaces/default/memory/team.md`（Deployment）・`project.md`（Deployment）

## Assumptions & Open Questions

None.
