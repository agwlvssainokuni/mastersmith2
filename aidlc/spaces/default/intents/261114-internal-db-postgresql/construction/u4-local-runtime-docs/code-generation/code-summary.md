# コードの要約 — u4-local-runtime-docs

手元の compose に、内部DB 用の PostgreSQL（サービス `internal-db-postgres`・profile `internal-db-postgres` で選んだときだけ起動）を足し、`.env.example` の項目と、PostgreSQL の内部DB の使い方・バックアップと戻し・H2 だけの運用の明記を文書に書いた。`backend/`・`frontend/`・`.github/workflows/`・lockfile・依存・`TargetDbImages`・H2 の運用の道具・`.env`・`.env.targetdb` は変えていない（`git diff --stat`）。新しいテストのクラスは足していない（NFR5.1）。

## 作ったもの・変えたもの

| ファイル | 変更 |
|---|---|
| `compose.yaml` | サービス `internal-db-postgres`（profile・版とダイジェストは `targetdb-postgres` と同じ・`ports` なし・`mem_limit: 512m`・`restart: "no"`・ログは json-file 10m・3 本・`healthcheck` は `CMD-SHELL` の `pg_isready -h 127.0.0.1`・資格情報は `${MASTERSMITH_INTERNAL_DB_POSTGRES_*:-}`）、ボリューム `mastersmith-internal-db-postgres`、`app` の `depends_on`（`required: false`）、冒頭の使い方の 1 行 |
| `.env.example` | 「内部DB を PostgreSQL にする」の節（3 項目をコメントのまま。値なし）、H2 の節の `;DEFRAG_ALWAYS=TRUE` は H2 のときだけの注記 |
| `.github/dependabot.yml` | コメントだけ（固定先の一覧に内部DB 用の postgres を足し、一緒に上げると書いた）。設定の値は変えていない |
| `README.md` | 冒頭の説明、新しい節「内部DB を PostgreSQL にする（任意）」（起動・既知の制約の要点）、文書の目次の `docs/internal-db.md` の行 |
| `docs/internal-db.md` | 題・冒頭の説明、「内部DB を PostgreSQL にする」（手順・設定の表・H2 に戻す・止める・消す・既知の制約・困ったとき）、「PostgreSQL の内部DB のバックアップと戻し」、「H2 のときだけの運用」の表、H2 の 3 節の「H2 のときだけ」の明記 |
| `docs/configuration.md` | `MASTERSMITH_DB_*` の行に PostgreSQL のときの値の形、新しい 3 変数 |
| `docs/operations.md` | 監査ログの確かめ方の H2 だけの明記と PostgreSQL への案内、消してはいけない操作、資源の 1 行 |
| `docs/deployment.md` | 戻し方に、内部DB の種類の選び方と PostgreSQL のときの戻し（`--profile` を付ける） |
| `docs/verification.md` | 「内部DB の PostgreSQL の結合テスト（明示して流す）」（`./gradlew :backend:internalDbPostgresTest`・colima の 2 変数・CI の手動の入力 `internal_db_postgres`・31 クラス・76 件・約 1 分半） |
| `docs/dsl.md` | 内部DB のファイルの大きさの既知の制約が H2 のときだけであることを 1 行 |

## 確かめの結果

### Step 1 の使い捨ての確かめ（公式の `postgres` のイメージ。計画と違った点は太字）

| 確かめ | 結果 |
|---|---|
| 初回の初期化の間の `pg_isready` | `-h 127.0.0.1` 付きは一時のサーバーの間は失敗し、本起動の後に成功した。`-h` なしは一時のサーバーの間（約 0.25 秒）にも成功した（誤判定の窓がある）。healthcheck は `-h 127.0.0.1` に決めた（N-01 の答えが通った） |
| `POSTGRES_PASSWORD` が空・未設定 | 起動を拒み、ログに「パスワード未設定」の理由が出て終了した（compose でも `Exited (1)` を確かめた） |
| `POSTGRES_USER`・`POSTGRES_DB` が空 | **起動は拒まず、`postgres`・`postgres` になった**（`.env.example` と文書に「3つとも値を入れる」と書いた） |
| 初期化済みのボリュームに別の値で再起動 | 値は効かず「初期化を飛ばす」。別の利用者名で接続すると `role does not exist` で失敗した。**このとき `pg_isready` は認証を見ないため healthy のまま**（文書の「困ったとき」に書いた）。元の値で起動し直すとデータが残った（ボリュームの永続も確認） |
| `pg_dump -Fc`・`pg_restore`・`psql` | コンテナの中の `POSTGRES_USER` と、パスワードなしの接続で動いた。出力は壊れず（`pg_restore --list` で読めた）、`--clean --if-exists` で戻り、件数が一致した。パスワードは手元のシェルにも引数にも書いていない |
| `pg_restore --clean` の戻しの範囲 | **複写にある物だけを消して作り直す。複写の後に足した表は残る**。完全に同じにするには、ボリュームを作り直して空の DB に `--clean` なしで戻す。これも一致を確かめ、文書に書いた |
| `/var/lib/postgresql` のマウント | コンテナを作り直しても、ボリュームのデータが残った |

### Step 7: 文書の手順の実行（`docker compose -p u4trial`。既存の `mastersmith` には触れていない）

- 起動（`up -d --wait internal-db-postgres`）が healthy になった（`$$POSTGRES_USER` の healthcheck が動いた）。
- 実際の PostgreSQL 用の移行 SQL（`backend/src/main/resources/db/migration-postgresql/` の V1〜V14）を `psql` で適用し、`audit_events` に 30 行（3 種類×10）を入れ、文書の件数の問い合わせの出力を取った。
- 文書の形のバックアップ（`exec -T … sh -c 'pg_dump …' > ファイル`、権限 600）→ 変更（行の削除・表の追加）→ `pg_restore --clean --if-exists` → 件数が一致した（`later` の表は残る）。
- `stop`・`rm -sf`・ボリューム削除・`up`・`--clean` なしの `pg_restore` → 件数が一致し、`later` の表は無い。
- `docker compose exec` は `--profile` を付けなくても動く（文書の例は付けている。害はない）。
- 空のパスワードのまま profile を選ぶと、コンテナが `Exited (1)` になりログに理由が出た。
- 終わりにコンテナ・ボリューム・ネットワーク・一時ファイル（資格情報の入った env・複写）をすべて消した。`mastersmith-*` の既存のコンテナ・ボリュームには触れていない。
- アプリのコンテナの結合（`app` から内部DB に接続する）は、計画どおり Deployment Execution で行う。`docker compose -p u4trial up --dry-run -d app`（profile なし・あり）は通る。

### Step 8: 全体の確かめ

| 確かめ | 結果 |
|---|---|
| profile なしの `docker compose config` と変更前の突き合わせ（環境変数の値を伏せて比べた） | 差は `app` の `depends_on`（`required: false`）の 4 行だけ。内部DB 用のサービス・ボリュームは出ない（**計画の「依存が出ない」は、`required: false` の依存が設定に残る形に読み替え。起動は変わらない**） |
| profile ありの `docker compose --profile internal-db-postgres config` | `internal-db-postgres`・ボリューム `mastersmith_mastersmith-internal-db-postgres` が出る。`ports` なし |
| 版・ダイジェストの一致（`grep`） | `compose.yaml` の `internal-db-postgres` と `targetdb-postgres` と `TargetDbImages` の `POSTGRES_VERSION`・`POSTGRES_DIGEST` が一致。`docker/perf/compose.yaml` も同じ（変えていない）。`docs/` と `README.md` に版の文字列を書いていない（`postgres:18` の記述は `compose.yaml` の 2 サービスと `docker/perf/compose.yaml` だけ） |
| `git diff --stat`（`backend/`・`frontend/`・`.github/workflows/`・`vendor/`） | 差分なし |
| `.github/dependabot.yml` の差分 | コメント行（`#`）の追加だけ |
| 秘密情報（追加行の `grep`: `PGPASSWORD`・パスワードの値の直書き） | 0 件 |
| `./gradlew verifyQuick`（colima の 2 変数つき・`caffeinate -dims`） | `BUILD SUCCESSFUL in 3m 36s`（フォーマット・リンタ・ライセンスヘッダー・型検査・単体テスト・画面のカバレッジ・Gitleaks（`no leaks found`）・OSV-Scanner・SpotBugs・成果物が通った。除外は足していない） |

## 文書ごとのチェックリスト

### FR4.4 の H2 だけの運用の 5 項目

| 項目 | 明記した場所 |
|---|---|
| `;DEFRAG_ALWAYS=TRUE` | `docs/internal-db.md` の「H2 のときだけの運用」の表と「内部DBのファイルの詰め直し」の冒頭と「接続先の指定」、`docs/configuration.md` の `MASTERSMITH_DB_URL`、`.env.example` |
| `docker/hikari-pool.sh` の compact | 同じ表と詰め直しの節の冒頭、`docs/dsl.md` の既知の制約 |
| `perf/h2-shell.sh` | 同じ表と詰め直しの節の最後の項（`perf/README.md` は変えていない） |
| ボリュームの `tar` の戻し | 同じ表と「内部DBのバックアップと戻し方」の冒頭、`docs/operations.md` の監査ログの確かめ方 |
| `-Dh2.compactThreads=1` | 同じ表と詰め直しの節の冒頭 |

### FR4.5 の 3 項目（接続先の設定・compose の選び方・対応する版）

| 項目 | 書いた場所 |
|---|---|
| 接続先の設定 | `docs/internal-db.md`（6 変数の表）、`docs/configuration.md`、`.env.example` |
| compose の選び方 | `README.md`、`docs/internal-db.md`、`compose.yaml` の冒頭 |
| 対応する版 | `docs/internal-db.md`（PostgreSQL 18 系だけ。イメージは `compose.yaml` を見る） |

## 上流との差

1. NFR 設計 DD-1 の「ログは json-file（他のサービスと同じ）」は、app・lgtm・mailpit と同じ形であり、`targetdb-postgres` とは同じではない（インフラ設計で訂正済み）。
2. NFR 設計 DD-3 の「3 行をコメントに戻す」は、`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消す（またはコメントにする）、`MASTERSMITH_DB_PASSWORD` は H2 を作ったときの値に戻す、に読み替えた（インフラ設計で訂正済み）。
3. テストの定数は新しく足さず、既存の `TargetDbImages.postgres()` を使う（NFR 設計 DD-9）。
4. 計画の Step 2 の確かめ「依存が出ないこと」は、profile なしの `docker compose config` に `required: false` の `depends_on` が残る（内部DB 用のサービスは出ない。`docker compose up --dry-run -d app` が通り、起動は変わらない）。
5. 文書の手順は、確かめで分かった次の 3 点を足した: 利用者・データベース名が空だと `postgres` になる、初期化済みのボリュームでは値が効かず `pg_isready` は認証を見ない、`--clean` は複写にない表を消さない。
6. 手順に使った `docker compose exec` の例は `--profile` を付けているが、付けなくても動く。

## 依頼者に確かめたいこと

- `.env.example` の新しい 3 項目は、他の既定値のある設定と同じくコメントのままにした（`MASTERSMITH_DB_PASSWORD=` のような有効な空の行にしなかった）。空の有効な行にすると、アプリのコンテナの環境にも空の値が渡るため。
- アプリのコンテナは `.env` を丸ごと読むため、内部DB 用の 3 変数を `.env` に入れると、アプリのコンテナの環境にも同じ値が入る（アプリは読まない。パスワードは `MASTERSMITH_DB_PASSWORD` と同じ値なのでもともとアプリに渡っている）。許してよいか。
- `docs/internal-db.md` の PostgreSQL の戻しの「完全に戻す」手順（ボリュームを作り直す）は、実際に使い捨てで通った形だが、Deployment Execution の戻しの練習で、アプリの移行の履歴（Flyway の履歴の表）まで同じに戻ることを、アプリを起動して確かめる。

## 後の単位・段へ渡すこと

- Build and Test: 全件の `./gradlew verify` と CI の時間（基準の中央値 21 分 32 秒の 1.1 倍・約 42 分の線と並べる）。`flyway-database-postgresql` のライセンスの確認（U1 から渡された。この単位は依存を足していない）。
- Deployment Execution: 使い捨ての PostgreSQL の内部DB を相手に、アプリを起動して起動確認（ヘルスチェック）とスモークテストを行う。`docker stats` の値（内部DB 用のコンテナのメモリが 512m の内か）を 1 回記録し、U1 の `INTERNAL_DB_PG_BASELINE`（起動からヘルス UP までと移行の時間）と並べる（合否には使わない）。バックアップ・戻し（`pg_dump`・`pg_restore`）の練習を 1 回行い、戻した後にアプリを起動して Flyway の履歴が同じであることを確かめる。
- `docs/` に書いた CI の入力名は `internal_db_postgres`（U1 の `cicd-pipeline.md` の `internal_db_postgresql` は誤記。U1 の記録は書き換えていない）。
- `perf/README.md` の `perf/h2-shell.sh` の説明は、この単位の対象外のため変えていない（H2 のときだけの明記は `docs/internal-db.md` に書いた）。
