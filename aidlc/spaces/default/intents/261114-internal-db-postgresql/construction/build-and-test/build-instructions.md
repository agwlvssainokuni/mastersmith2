# ビルド手順 — Intent 261114-internal-db-postgresql

内部DB を H2 に加えて PostgreSQL でも動かせるようにした版のビルドと確かめの手順です。ビルドの入口は Gradle の 1 コマンドで、フロントエンドの検査（npm）と外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼びます。

## 1. 依存と環境

| 項目 | 内容 |
|---|---|
| JDK・Gradle | Gradle Wrapper（`./gradlew`）。JDK はリポジトリの設定どおり |
| Node.js・npm | `frontend/` の `package-lock.json` どおり（`npm ci`。`frontend/.npmrc` の `ignore-scripts=true` は外さない） |
| サブモジュール | `vendor/make-you-chic-ui`・`vendor/java-mustache-processor` を固定先のコミットで取得（`git submodule update --init`） |
| コンテナの実行環境 | colima などが動いていること（対象DB の結合テストと、内部DB の PostgreSQL の結合テストが Testcontainers で使う） |
| 追加した依存 | `org.flywaydb:flyway-database-postgresql` 12.4.0（Spring Boot の BOM が管理し、版は `backend/gradle.lockfile` で固定。推移依存の増減は無し） |
| 内部DB | 既定は組み込みの H2（設定は何も要らない）。PostgreSQL にするときだけ `MASTERSMITH_DB_URL` と利用者・パスワードを環境変数（`.env`）で渡す（`docs/internal-db.md`） |

colima の PC では、検査を流すシェルに次の 2 つを渡します（書き方は `docs/target-db.md`）。渡さないと、コンテナで動かすテストが警告つきで飛ばされ、全件の `verify` はパッケージごとのカバレッジの下限で失敗します。

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 2. ビルドのコマンド

| 目的 | コマンド |
|---|---|
| 全件の検査（フォーマット・リンタ・ライセンスヘッダー・型検査・ビルド・単体テスト・結合テスト・カバレッジの下限・Gitleaks・OSV-Scanner・SpotBugs・成果物の確認） | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` |
| 統合の前の速い検査（結合テストとバックエンドのカバレッジの下限を除く） | `./gradlew verifyQuick` |
| WAR だけを作る | `./gradlew :backend:bootWar`（フロントエンドの `dist` を同梱） |
| 内部DB の PostgreSQL の結合テスト（明示したときだけ） | `./gradlew :backend:internalDbPostgresTest`（`integration-test-instructions.md`） |

長い実行は、スリープで途切れないよう `caffeinate -dims` で包みます（`project.md` の Testing Posture）。流す前に `pmset -g assertions` で眠らない状態を、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直します。

```bash
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

件数とカバレッジを報告するときは、テストのタスクが UP-TO-DATE で飛ばされないよう、上のとおり `:backend:cleanTest :backend:cleanIntegrationTest` を付けます。

## 3. ビルドの確かめ

- 終了コードが 0 で `BUILD SUCCESSFUL` が出る。
- `backend/build/libs/` に実行可能 WAR ができ、`:backend:verifyPostgresMigrationsInWar` が通る（PostgreSQL 用の移行 V1〜V14 が WAR に入っている）。
- バックエンドのカバレッジは `backend/build/reports/jacoco/`（全体とパッケージごとの下限 行 80%・分岐 70%）。
- H2 の移行（`backend/src/main/resources/db/migration/`）が書き換わっていない（`H2MigrationFilesUnchangedTest` が単体テストで確かめる）。

## 4. 困ったとき

| 症状 | 原因と対処 |
|---|---|
| コンテナで動かすテストが SKIPPED になり、`verify` がパッケージごとのカバレッジで失敗する | `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` が渡っていない。上の 2 変数を渡して流し直す |
| `:backend:internalDbPostgresTest` が「該当なし」の案内で失敗する | `-PitTests` の絞り込みがタグ付きのクラスだけ、または存在しない名前に当たった。名前を直すか、専用のタスクを使う |
| `verifyQuick` が `-PitTests` で失敗し、コンテナの警告が出る | コンテナの実行環境が動いていない。colima を起動して流し直す（飛ばしたまま統合しない） |
| E2E が 2 秒で失敗する | 見本の PostgreSQL（`127.0.0.1:15432`）が開いていない。`integration-test-instructions.md` の上乗せの compose で開く |
| 結果が `UP-TO-DATE` で件数が出ない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直す |
| Hibernate の起動で `ddl-auto: validate` が失敗する（PostgreSQL） | 列の型と写し方の食い違い。`backend/src/main/resources/db/migration-postgresql/` の移行と、エンティティの `@JdbcTypeCode` を確かめる |
