# CI/CD Pipeline — U1 対象DB（u1-target-db）

U1 の検査の流れと、対象DB を手元で動かす環境を示す。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作らない（`aidlc/spaces/default/memory/project.md` の Deployment）。答えは `infrastructure-design-questions.md`（Q1・Q2）。

## 1. 検査の流れ（既存の `./gradlew verify` に足すもの）

| 段 | 足すもの | 場所 |
|---|---|---|
| ビルド | JDBC ドライバー3つ（Spring Boot の BOM の版）と Testcontainers（MySQL・MariaDB・PostgreSQL のモジュールと JUnit 5 の連携）を依存に足し、lockfile を更新する | `backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile` |
| 結合テスト | 対象DB の結合テスト（`*IT`）を3種類の DB それぞれで実行する。テスト（またはクラス）ごとに名前の重ならないスキーマを作って消す（NFR12.2） | `backend/src/test/java/cherry/mastersmith/targetdb/` |
| コンテナの実行環境の確かめ | Docker に届かないときは警告を出して対象DB のテストだけ飛ばす（黙って飛ばさない）。CI では飛ばさない（届かなければ失敗にする） | Testcontainers の JUnit 5 の連携の設定と、CI の環境変数 |
| 静的解析 | SpotBugs の `SQL_` の指摘で止める（既存の関門。U1 の固定の問い合わせで指摘が出ないこと） | 既存 |
| 依存の脆弱性とライセンス | OSV-Scanner（既存）。ドライバーのライセンスの文書を配布物に含める作り（コード生成で決める） | 既存と `backend/build.gradle.kts` |

CI（GitHub Actions、`ubuntu-latest`、制限時間 60 分）は今の `./gradlew verify` のまま。Testcontainers は runner の Docker を使う。イメージの取得は毎回で、キャッシュは足さない。3種類の DB の起動と結合テストの時間は Build and Test で測る。`verify` の全体が 45 分（制限時間 60 分の 75%）を超えたら、対象DB のテストの実行の場（毎回か、統合の前だけか）を見直す（team.md の Testing Posture）。

## 2. テストの DB のイメージ（NFR12.1、Q2: A）

| DB | イメージ（版） | 固定 |
|---|---|---|
| MySQL | `mysql:8.4.x` | 版の名前とダイジェスト（`@sha256:...`） |
| MariaDB | `mariadb:11.8.x` | 同上 |
| PostgreSQL | `postgres:18.x` | 同上 |

- 細かい版とダイジェストはコード生成で決める。テストのコードの中では1か所の定数にまとめ、compose の見本の DB（3節）と同じ値を使う。
- Dependabot の docker の更新の通知（既存、毎週）で上げる。上げたら全検査を通してから統合する。

## 3. 手元で試す対象DB（Q1: B）

| profile | サービス | 用途 |
|---|---|---|
| `targetdb-postgres` | PostgreSQL 18 | 画面からスキーマの読み込みを試す、Build and Test の時間を測る |
| `targetdb-mysql` | MySQL 8.4 | 同上 |
| `targetdb-mariadb` | MariaDB 11.8 | 同上 |

- 必要なものを1つずつ起動する（`docker compose --profile targetdb-postgres up -d`）。3つ同時に起動すると VM のメモリ（6GiB）のうち約 1.5〜3GiB を使う。
- ポートはホストに開けない。アプリのコンテナから compose の中の名前（例 `targetdb-postgres:5432`）で接続する。
- 起動時に見本のスキーマを入れる SQL を DB の種類ごとにリポジトリに置く（テーブル・ビュー・外部キー・コメント・記号を含む名前）。Build and Test 用に 100 テーブル × 100 カラムを作る SQL（または作るスクリプト）も置く。
- 読み取りだけのアカウントを作り、アプリはそのアカウントで接続する（U1 の NFR4.6）。
- データはボリュームに置き、要らなくなったら profile ごとに消せるようにする。
- `compose.yaml` はほかの単位（U4 の設定、既存の監視の profile）と共有するファイル。U1 はこの3つの profile のサービスとボリュームだけを足し、既存の `app` のサービスには対象DB の環境変数（4節）を足す以外は触れない。見本の DB の版は2節の定数と同じ値にする（2節を正とする）。

## 4. 設定と秘密情報（NFR4.1・NFR4.5）

- アプリの対象DB の設定は `MASTERSMITH_TARGET_DB_*`（種類・ホスト・ポート・DB 名・スキーマ名・ユーザー名・パスワード、目的ごとの待ち時間）で渡す。`application.yaml` には環境変数の参照だけを置く。
- 見本の DB の管理者と読み取りのアカウントのパスワードは `.env` だけで渡し、コミットしない。`.env.example` には項目名を空の値で置く。
- Gitleaks（既存、コミット前と CI）で秘密情報がコミットされないことを確かめる。

## 5. README に書くこと

- 手元で試す対象DB の起動・停止・消し方と、アプリへの設定のしかた
- Testcontainers が colima の Docker に届く設定（`DOCKER_HOST` など）と、届かないときの警告の意味（その状態では統合しない）
- 対象DB のアカウントを読み取りの権限だけにすることの推奨
