# 単位のテストの手引き — u4-local-runtime-docs

この単位は、手元の配備の設定（compose・`.env.example`・`dependabot.yml` のコメント）と文書だけを変えます（アプリのコードは変えない）。新しいテストのクラスは足しません（NFR5.1）。確かめは、設定の突き合わせと、使い捨てのコンテナでの手順の実行です。

## 前提（colima の PC）

```
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
```

## この単位だけのコマンド（最初の確かめの前に使えるもの）

- 設定の検査（profile なし。既定の配備）: `docker compose config`
- 設定の検査（profile あり。内部DB の PostgreSQL を選ぶ）: `docker compose --profile internal-db-postgres config`
- profile の一覧: `docker compose config --profiles`
- 版・ダイジェストの一致の確かめ（読み取りだけ）: `grep -n "postgres:18" compose.yaml backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`
- 変更の範囲の確かめ: `git diff --stat`（`backend/`・`frontend/`・`.github/workflows/`・lockfile に差分が無い）
- 承認済みの確かめのコマンド（Construction Verification Command）: `./gradlew verifyQuick`

## 使い捨ての確かめ（リポジトリの外）

- 使い捨てのコンテナ（名前 `u4trial`・プロジェクト名 `u4trial`）で、`pg_isready`・空の資格情報での起動の拒否・`pg_dump`/`pg_restore`/`psql` を試す。既存の `mastersmith-*` のコンテナ・ボリュームには触れない。値（パスワード）は一時ファイルにだけ置き、出力・記録に書かず、終わったらコンテナ・ボリューム・一時ファイルを消す。

## 見込みの件数

- 新しいテスト: なし。既存のテストの全件は `./gradlew verifyQuick`（フォーマット・リンタ・ライセンスヘッダー・単体テスト・Gitleaks・OSV-Scanner など）で確かめる。

## 期待するカバレッジ

- この単位は `backend/src/main` を変えず、計測の対象・下限は動かない。全体の判定は Build and Test の全件の `./gradlew verify`。

## データ・模擬の扱い

- 資格情報はソース・記録に書かず、使い捨ての値を一時ファイルにだけ置く。データは使い捨てのコンテナの中で作り、終わったら消す。
