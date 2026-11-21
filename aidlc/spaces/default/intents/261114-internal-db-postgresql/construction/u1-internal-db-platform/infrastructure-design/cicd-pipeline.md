# 配送の流れ（CI） — u1-internal-db-platform

この単位が CI に足すのは、PostgreSQL の結合テストを明示して流す入口だけです。通常の `develop` へのプッシュの流れ（`./gradlew verify`）は変えません。

## 段と関門の対応

| 段 | 流れ | 関門 | この単位の変更 |
|---|---|---|---|
| 取得・準備 | checkout（サブモジュールつき）→ JDK 25 → Node 26 → Gradle → Gitleaks・OSV-Scanner | — | なし |
| 検査 | `./gradlew verify -PitForks=$IT_FORKS`（フォーマット・リンタ・ライセンスヘッダー・ビルド・単体と結合のテスト・カバレッジ下限・秘密情報・静的解析・依存の脆弱性） | 失敗で全体を失敗 | **PostgreSQL の結合テストを含めない**（タグ `internal-db-postgres` は `integrationTest` から除く。NFR5.2）。足す単体テストと H2 の結合テストは入る |
| 成果物 | WAR の保存・規模とカバレッジの要約・HTML の報告 | — | なし |
| 内部DB の PostgreSQL の結合テスト（新しい段） | 手動の実行（`workflow_dispatch`）で入力 `internal_db_postgres` が真のときだけ、`./gradlew :backend:internalDbPostgresTest` を流す | 失敗で全体を失敗。コンテナの実行環境が無いときも飛ばさず失敗（NFR4.8、BR6.4） | **足す**（BR6.8） |

## 変更の中身（コード生成で ci.yml に入れる形）

- `on.workflow_dispatch.inputs.internal_db_postgres`: `type: boolean`・`default: false`・説明は日本語（内部DB の PostgreSQL の結合テストを流すか）。
- 新しい手順（`verify` の後）: `if: inputs.internal_db_postgres == true` の 1 手順で `./gradlew :backend:internalDbPostgresTest`。入力の値はシェルの文に埋め込まない（`if` の式だけで使い、コマンドの引数に渡さない）。`push`・タグでは入力が無く（空）、手順は流れない。
- 専用のタスクは、コンテナの実行環境が無いとき CI では失敗にする（環境変数 `CI` が設定されている実行を CI とみなす。実際の判定は `InternalDbPostgresRuntimeCheck`）。
- 手順を `verify` の job の中に置く（別の job にしない）。理由: WAR・ビルドの出力・Gradle のキャッシュを使い回せ、`timeout-minutes: 60` の枠も 1 つで済む。別の job にすると同じ準備を重ねる。
- 使う Actions は今のコミットのハッシュ固定のまま。新しい Action は足さない（`gradle` を直接呼ぶ）。
- 秘密情報は使わない。テストの資格情報はコンテナの起動ごとの値（NFR2.9）。

## 時間の扱い（NFR5.1）

- 通常の `push` の検査の job の時間は、PostgreSQL の手順が流れないため、足す単体テストと H2 の結合テストの分だけ増える。Build and Test で、CI の検査の job の時間を基準（中央値 21分32秒）の 1.1 倍（約 23分42秒）と約 42 分の線と並べて記録する。
- PostgreSQL の手順が流れる手動の実行は、通常の検査の時間の判定に使わない（`docs/verification.md` の CI の時間の表では手動の実行は参考の扱い）。

## デプロイ・戻し・昇格

- この単位は配備の流れを変えない。成果物（WAR）の保存は今のまま。
- 戻し: アプリの配備は、直前の版のイメージで再配備する今の手順のまま。この単位の変更は H2 の既定の動作を変えず、前方互換（NFR4.7）。
- 環境の昇格・フィーチャーフラグ・blue-green は、配備先が決まるまで無い（`team.md` の Deployment）。

## 秘密情報と固定先（Dependabot）

- 新しいコンテナのイメージの固定先は足さない。テストの PostgreSQL は `TargetDbImages.postgres()` を使い回すため、既存の固定先（`.github/dependabot.yml` の docker-compose の項と `TargetDbImages`）で上げ漏れが起きない（NFR3.2）。U4 が compose に内部DB 用の PostgreSQL を足すときは、同じ版・ダイジェストで書き、固定先の一覧に足す。
- `.github/workflows/ci.yml` の変更は、コード生成の統合の前の 1 コマンドの検査では流れないため、Build and Test で手動の実行（`internal_db_postgres=true`）を 1 回流して確かめる（NFR5.4 の時間の記録と兼ねる）。

## Sources

- `.github/workflows/ci.yml`（入力 `it_forks`・`verify` の job）
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/performance-design.md`（DD-7）・`reliability-design.md`
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/functional-design/rules.md`（BR6.1〜BR6.8）
- `docs/verification.md`（CI の時間の基準・手動の実行の扱い）
- `aidlc/spaces/default/memory/team.md`（Deployment）・`project.md`（Deployment）

## Assumptions & Open Questions

- [assumption] 手動の実行の `internal_db_postgres` の手順は、`verify` の後に同じ job で流す形（別の job にしない）。`inputs.internal_db_postgres == true` の式は、型が boolean の入力で成り立つ。ci.yml を書くコード生成で、手動の実行 1 回で確かめる。
