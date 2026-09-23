# ビルド手順（build-instructions）

本 Intent（F2 の修正: 接続プールの上限の引き上げと再現テスト）のソースをビルドし、統合の前の関門（`./gradlew verify`）を通すまでの手順。すべてのコマンドはリポジトリのルートで実行する。

## 1. 前提の道具

README の「前提の道具」と同じ（JDK 25、Node.js 24、Gitleaks 8.30.1、OSV-Scanner 2.6.0、pre-commit）。本 Intent で新しく要る道具は無い。

- テストはコンテナの実行環境を必要としない。内部DB（H2）は本番と同じ組み込み・ファイル保存で動かす。
- Docker（colima）が要るのは、後の段（Deployment Execution）でコンテナで起動して確かめるときだけである。

## 2. 取得と依存関係

```bash
git submodule update --init
```

依存関係は `./gradlew verify` の準備の段（`verifyPrepare`）が lockfile どおりに入れる。本 Intent で依存関係は変えていない。

## 3. 環境の設定

ビルドとテストに環境変数は要らない。本 Intent で加えた設定は次の1つで、既定値のままでよい。

| 変数 | 既定 | 用途 |
|---|---|---|
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` | `30` | 内部DBの接続プール `mastersmith-db` の上限。`backend/src/main/resources/application.yaml` の `spring.datasource.hikari.maximum-pool-size` が `${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}` で読む |

- 起動するときの `.env` の用意は README の「コンテナでの起動と確認」のとおり。`.env` はコミットしない。
- テストでは、この変数を付けて実行すると上限が変わる（`integration-test-instructions.md` の確かめ方で使う）。

## 4. ビルドと検査のコマンド

```bash
# 統合の前の1コマンドの検査（CI と同じ）。テストの件数とカバレッジを実測するため、テストのタスクを消してから実行する
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

段の並び（準備 → フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジの下限 → 安全の検査 → 成果物）は README の「1コマンドの検査」のとおり。1つでも失敗したら全体が失敗する。

## 5. ビルドの確かめ方

- `BUILD SUCCESSFUL` で終わること。
- 成果物 `backend/build/libs/mastersmith.war` ができていること。
- 報告: `backend/build/reports/tests/`（テスト）、`backend/build/reports/jacoco/`（カバレッジ）、`backend/build/reports/spotbugs/`、`build/reports/osv-scanner/osv.json`。

## 6. うまくいかないとき

- `verify` がテストのタスクを UP-TO-DATE で飛ばし、件数が出ないとき: `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直す。
- 再現テスト `ConcurrentLoginAuditIT` だけが失敗するとき: 環境変数 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` がシェルに残っていないか確かめる（10 などが残っていると、F2 が再現してテストが失敗する。これは期待どおりの検出である）。
- Node.js の版や Gitleaks・OSV-Scanner が無いと、`verify` は入れ方を示して失敗する（README の「前提の道具」）。
