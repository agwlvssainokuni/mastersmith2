# 結合テストの流し方 — Intent 261114-internal-db-postgresql

この Intent で、内部DB の結合テストは 2 つのバリエーションになりました。H2 の結合テスト（全件・既存）と、内部DB を PostgreSQL で動かす経路に固有の結合テスト（明示したときだけ）です。

## 共通の前提

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- コンテナの実行環境（colima）が動いていること。無いときは、手元では警告を出してコンテナで動かすテストだけを飛ばす（黙って飛ばさない）。飛ばした状態では統合しない。CI では飛ばさず失敗にする。
- 長い実行は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめる。

## 1. H2 の結合テスト（既存。全件が通ること）

```bash
caffeinate -dims ./gradlew :backend:cleanIntegrationTest :backend:integrationTest
```

- 全件の `./gradlew verify` の中でも流れる。`-PitTests=<クラス名の形>`（カンマ区切り）で絞れる（当たるものが無ければ失敗）。`-PitForks=<N>` で N 個の JVM に分けて流せる（CI は `it_forks` 3）。
- 内部DB を使うテストは組み込みの H2。対象DB（MySQL・MariaDB・PostgreSQL）の読み書きのテストは、版を固定したコンテナで流れ、他の種類のものは条件付きで飛ばされる（件数の「スキップ」はこれ）。
- この Intent で足した H2 の上の確かめ: `internaldb.platform`（接続先の種類の判定・起動の失敗・TRACE の漏れ・前の版の H2 ファイルでの起動）、`common.persistence`（書き込みの手順）、`WorkRoleSelectionMergeIT` ほか。

## 2. 内部DB の PostgreSQL の結合テスト（明示したときだけ）

```bash
caffeinate -dims ./gradlew :backend:internalDbPostgresTest
```

- 版を固定したイメージ（`TargetDbImages.postgres()`。`compose.yaml` の `internal-db-postgres`・`targetdb-postgres` と同じ版とダイジェスト）のコンテナで起動した実際の PostgreSQL に対して流す。タグは `internal-db-postgres`。
- 通常の `./gradlew verify`・`./gradlew verifyQuick`（`-PitTests` を付けても）・CI の通常の実行（`develop` へのプッシュ）には入らない。統合の前の承認の確かめでは `./gradlew verifyQuick :backend:internalDbPostgresTest` のように明示して付ける。
- 規模の目安: 31 クラス・76 件（U1 の 5 クラス・13 件、U3 の 26 クラス・63 件）、約 1 分半。
- 絞る: `./gradlew :backend:internalDbPostgresTest --tests '<クラス名>'`。各クラスは単独でも全体でも同じ結果になる。
- 標準出力に基準の値を出す（合否には使わない）: `INTERNAL_DB_PG_BASELINE`（起動からヘルス UP まで・移行の所要時間）、`INTERNAL_DB_PG_WAIT`（待ちの確かめの時間）、`INTERNAL_DB_PG_SHARED_APP`（共有のアプリの起動）。
- テストデータ: 資格情報は実行ごとの乱数、データベースはテスト（クラス）ごとの専用で、終わったら消す。メールアドレスは `example.com` だけ。

### CI で流すとき

GitHub Actions の手動の実行（`workflow_dispatch`）で、入力 `internal_db_postgres` を true にする。

```bash
gh workflow run ci.yml --ref <ブランチ> -f internal_db_postgres=true
```

入力名は `internal_db_postgres`（`internal_db_postgresql` ではない）。CI ではコンテナの実行環境が無いと飛ばさず失敗する。

## 3. 対象DB の結合テスト

DSL の対象の業務DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3 種類とも全件の `./gradlew verify` と CI で毎回流れる。統合の前の `verifyQuick` では、`-PitTests` で絞ったときに当たるものだけ流れる。この Intent は対象DB の接続・プール・テストを変えていない。

## 4. 画面からの一連の操作（E2E）

E2E は Playwright（実ブラウザ Chromium）で書き、`./gradlew verify` と CI の外に置く。Intent の最後（Build and Test）とリリースの前に全体を 1 回流す。内部DB は H2 のまま（PostgreSQL の内部DB で E2E は流さない）。

```bash
cd frontend && npx playwright install chromium && cd ..   # 初回と Playwright の更新のとき
docker compose --profile mail up -d mailpit                # メールの受け手（Mailpit）
# 見本の PostgreSQL を E2E の間だけ 127.0.0.1:15432 に開ける（.env.targetdb が要る）
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml \
  --profile targetdb-postgres up -d targetdb-postgres
caffeinate -dims ./gradlew e2eTest
docker compose --profile targetdb-postgres up -d targetdb-postgres   # 流し終えたら上乗せ無しで作り直してポートを閉じる
```

- 前提の確かめは `./gradlew e2eTest` が始める前に行い、満たさなければ手順を示して止まる（見本の PostgreSQL に届かない・Mailpit に届かないなど）。
- E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json に値が入っていないことを確かめる。

## 5. 期待するカバレッジ

バックエンドは全体とすべてのパッケージごとに行 80%・分岐 70% 以上（JaCoCo、全件の `verify` で判定）。フロントエンドは全体の合計に当てる `thresholds`（`verifyQuick` でも判定）。計測から外すのは、アプリの起動クラス・設定値だけのクラス・自動生成コード・`vendor/` に限り、除外を増やして下限を満たすことはしない。

## 既知の制約（依頼者の決定で受け入れ済み）

起動のログ（Flyway の INFO・Hibernate の INFO HHH10001005）に資格情報を含まない接続先が出る。結合テストの「URL・資格情報が出ない」の確かめ（`InternalDbStartupFailureIT` など）は、起動の失敗の経路と資格情報（利用者・パスワード）を対象にしている。`README.md`・`docs/internal-db.md` に書いてある。
