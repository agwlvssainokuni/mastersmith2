# 配送の流れ（CI） — u2-portable-sql

この単位は CI を変えません（`.github/workflows/ci.yml` に手を入れない）。足すテストは、通常の `develop` へのプッシュの流れ（`./gradlew verify`）に入る単体テストと H2 の結合テストだけで、PostgreSQL のテストは足しません（U3 が U1 の専用タスクに足す）。

## 段と関門の対応

| 段 | 流れ | 関門 | この単位の変更 |
|---|---|---|---|
| 取得・準備 | checkout（サブモジュールつき）→ JDK 25 → Node 26 → Gradle → Gitleaks・OSV-Scanner | — | なし |
| 検査 | `./gradlew verify -PitForks=$IT_FORKS`（フォーマット・リンタ・ライセンスヘッダー・ビルド・単体と結合のテスト・カバレッジ下限・秘密情報・静的解析・依存の脆弱性） | 失敗で全体を失敗 | 新しい単体テストと H2 の結合テストが入る。依存の追加は無い（lockfile に差分が出ない） |
| 成果物 | WAR の保存・規模とカバレッジの要約・HTML の報告 | — | なし |
| PostgreSQL の結合テスト | 手動の実行の入力 `internal_db_postgres`（U1） | U1 のとおり | この単位は足さない |

## 変更の中身

- `ci.yml`・`build.gradle.kts`・依存・lockfile に手を入れない。足すのはテストのコード（`backend/src/test/java/` の `common/persistence`・`user`・`role`・`dslmanage`・`common/testsupport` の下）と本体のコードだけ。
- 使う Actions は今のコミットのハッシュ固定のまま。秘密情報は使わない。

## 時間の扱い（NFR5.1〜NFR5.3）

- 通常の `push` の検査の job の時間は、足す単体テストと H2 の結合テストの分だけ増える（全件の `verify` の増加が約 30 秒以内の目安。上限切れの確かめは 2 本まで）。Build and Test で、CI の検査の job の時間を基準（中央値 21分32秒）の 1.1 倍（約 23分42秒）と約 42 分の線と並べて記録する。
- U1 のテスト・U3 のテストを足した後の合計の時間は、Build and Test がまとめて測る。

## デプロイ・戻し・昇格

- この単位は配備の流れを変えない。成果物（WAR）の保存は今のまま。
- 戻し: 直前の版のイメージで再配備する今の手順のまま。この単位は表・列・移行を足さず、1 つ前の版が同じ表で動く（NFR4.6）。
- 環境の昇格・フィーチャーフラグ・blue-green は、配備先が決まるまで無い（`team.md` の Deployment）。

## 秘密情報と固定先（Dependabot）

- 新しい依存・コンテナのイメージ・Action を足さない。固定先（`.github/dependabot.yml`）に変更は要らない。
- `docs/verification.md` の書き足しは、この単位では行わない（U1 の入力 `internal_db_postgresql` の説明を含め、U4 が持つ）。

## Sources

- `.github/workflows/ci.yml`・`docs/verification.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u2-portable-sql/nfr-design/performance-design.md`（DD-5）・`reliability-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/infrastructure-design/cicd-pipeline.md`
- `aidlc/spaces/default/memory/team.md`（Deployment）・`project.md`（Deployment）

## Assumptions & Open Questions

None.
