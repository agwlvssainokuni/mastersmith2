# テスト結果 — Intent 261114-internal-db-postgresql

実行日: 2026-11-28。PC: colima の VM（CPU 4・メモリ 6GiB の想定）。長い実行はすべて `caffeinate -dims` で包み、実行の間に `pmset -g log` で Sleep・DarkWake が無いことを確かめた（10:55〜11:25 JST に無し）。この記録の数字は、実行した指揮役が取った実測だけを写したもの。

## ビルドの状態

成功。全件の検査（フォーマット・リンタ・ライセンスヘッダー・型検査・ビルド・単体テスト・結合テスト・カバレッジの下限・Gitleaks・OSV-Scanner・SpotBugs・`verifyPostgresMigrationsInWar` を含む成果物の確認）が通った。

## 実行したコマンドと終了コード

| # | コマンド | 結果 | 終了コード |
|---|---|---|---|
| 1 | `DOCKER_HOST=… TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=… caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`it_forks` 指定なし） | `BUILD SUCCESSFUL in 29m 48s`（scratchpad の bt-verify.log） | 0 |
| 2 | `./gradlew verifyQuick :backend:internalDbPostgresTest`（u1・u2・u3・u4 の各単位の承認の場。colima の環境変数と caffeinate つき） | 各約 2〜6 分で成功 | 0（4 回とも） |
| 3 | `./gradlew e2eTest`（見本の PostgreSQL を 127.0.0.1:15432 に開ける上乗せの compose を、依頼者の承認のもとで targetdb-postgres を作り直して用意したうえで） | `BUILD SUCCESSFUL in 16m 41s`（1 worker） | 0 |
| 3a | `./gradlew e2eTest`（初回） | 見本の PostgreSQL のポートが開いておらず、2 秒で失敗。環境の前提の不足で、コードの失敗ではない。依頼者に諮って作り直し、その後の 1 回（#3）で通った | 非 0（環境） |

## テスト結果

| 種類 | クラス数 | 件数 | 失敗 | エラー | スキップ |
|---|---|---|---|---|---|
| 単体テスト（backend `test`） | 326 | 3,079 | 0 | 0 | 0 |
| 結合テスト（backend `integrationTest`。内部DB は H2） | 285 | 1,732 | 0 | 0 | 10 |
| 内部DB の PostgreSQL の結合テスト（タグ `internal-db-postgres`。明示して流した結果） | 31（U1 の 5 + U3 の 26） | 76（13 + 63） | 0 | 0 | 0 |
| E2E（Playwright、実ブラウザ Chromium で実行） | – | 298 | 0 | 0 | 0 |

- 結合テストのスキップ 10 件は、対象DB の種類ごとの条件付きのテスト（MariaDB・MySQL・PostgreSQL の読み書きのテストが、他の種類のものを飛ばす）。コンテナの実行環境が無いことによる飛ばしではない。
- 内部DB の PostgreSQL の結合テストは `integrationTest` から除外され、全件の `verify` では流れない（`NFR5.2` のとおり）。この件数は単位の承認の確かめ（#2）で明示して流した結果で、U3 の承認の確かめの実行の時間は 1 分 32 秒。
- 待ちの確かめの値（`INTERNAL_DB_PG_WAIT`。合否の幅は要件 FR3.3）: 書き込みの待ち 2,019 / 2,016 ms（幅 2,000 以上 3,000 未満）、行の排他 3,041〜3,047 ms（3,000 以上 4,000 未満）、続く書き込み 2,049 ms、SKIP LOCKED 全部持たれて 3,040 ms・7 つ持たれて 21 ms、行き詰まり 1,060 ms。
- 基準の値（合否には使わない）: 共有アプリの起動 6,455 ms、`INTERNAL_DB_PG_BASELINE startupToHealthMs=1560`・`migrationMillis=224`。

## カバレッジ（JaCoCo、全件の `verify` の結果）

| 範囲 | 行 | 分岐 |
|---|---|---|
| バックエンド全体 | 15,157 / 15,506（97.7%） | 6,467 / 7,066（91.5%） |
| 72 パッケージ | 下限（行 80%・分岐 70%）を下回るものは無い。`audit.repository` は数える行が 0 のインターフェースだけで判定の対象外。最小の分岐は `config` の 76.7% | |
| `common.persistence` | 93 / 95（97.9%） | 47 / 48（97.9%） |
| `role.store` | 271 / 278（97.5%） | 88 / 96（91.7%） |
| `dslmanage.repository` | 72 / 72 | 4 / 4 |
| `user.repository` | 54 / 54 | 11 / 12（91.7%） |
| `internaldb.platform` | 152 / 156（97.4%） | 59 / 66（89.4%） |

計測の除外は増やしていない。フロントエンドのカバレッジの下限は `verify` の中で判定され、通った。

## 変えたもの・変えていないもの（読み取りで確かめた）

- 統合前の起点 `65b2ba65`（`a6eec16f` の親）から今の `develop` までの差分で、`perf/`・`docker/perf/`・`docker/monitoring/`・H2 の移行（`backend/src/main/resources/db/migration/`）・`frontend/src/` の変更は無い。`.github/dependabot.yml` はコメント 3 行の追加だけ。
- `src/main` の変更は U1・U2・U2 の小さな直し（40P01 の分類）だけ。U3・U4 は `src/test` と compose・文書だけ。依存・lockfile は U1 の `flyway-database-postgresql` の追加以外に変更なし。
- コード・設定のコミット（`develop`）: u1 a6eec16f・4f23a008・54308c37、u2 b419fff1・4f812c19・72839f5f・a974f6a9・3e6e03d0、u3 a323a052・ea5755a0・6e403d22・938c7e67、u4 520986b1・947fbfc8。

## 失敗の詳細

なし。（E2E の初回の失敗は上の 3a のとおり環境の前提の不足で、再実行ではなく環境を整えた後の 1 回で通った。不安定なテストの兆候は、この段の実行では無かった。）

## 実行していないもの

- CI の検査の job の時間、CI の手動の実行 `internal_db_postgres=true`: `develop` を依頼者が push した後に流す。
- アプリを PostgreSQL の内部DB に接続して行う起動確認・スモークテスト・バックアップと戻しの練習、内部DB 用コンテナの実メモリ（512m の内か）: Deployment Execution。
- `verifyPostgresMigrationsInWar` が、ファイルを欠けさせたときに失敗することの確認（正の確認だけ流れた）。依頼者の決定（2026-11-28）で持ち越す。

## 既知の制約（依頼者の決定で受け入れ、2026-11-28）

起動のログに、資格情報を含まない接続先が Flyway の INFO（`org.flywaydb.core.FlywayExecutor`）と Hibernate の INFO（`org.hibernate.orm.connections.pooling`、HHH10001005）の 2 か所に出る（H2 ではファイルのパス）。直さない。README・`docs/internal-db.md` に書いてある。

## Loop-Back Log

なし（loop-back は発生していない）。
