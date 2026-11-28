# コード生成の計画 — u4-local-runtime-docs

## Summary

- Builds: 手元の compose に内部DB 用の PostgreSQL（サービス `internal-db-postgres`・profile で選んだときだけ起動）と、`.env.example` の項目、PostgreSQL の内部DB の使い方・バックアップと戻し・H2 だけの運用の明記を書いた文書
- Touches: `compose.yaml`・`.env.example`・`.github/dependabot.yml`（コメントだけ）・`README.md`・`docs/internal-db.md`・`docs/configuration.md`・`docs/operations.md`・`docs/deployment.md`・`docs/verification.md`・`docs/dsl.md`（H2 だけの注意の 1 行）
- Tests: 新しいテストのクラスは足さない（NFR5.1）。確かめは、`docker compose config` の突き合わせ（profile あり・なし）、使い捨てのコンテナでの手順の実行（`pg_isready`・`pg_dump`・`pg_restore`・`psql`・空の資格情報での起動の拒否）、`grep` での版・ダイジェストの一致と秘密の不在、既存の `./gradlew verifyQuick` の全件
- 変えない: `backend/` と `frontend/` のすべて、H2 の運用の道具（`docker/hikari-pool.sh`・`perf/`・`scripts/count-db-copy.mjs`）、`ci.yml`・依存・lockfile、`TargetDbImages`、`app` サービスの `environment`・`volumes`・`healthcheck`（`depends_on` の追加だけ）

## 計画の前提（設計の段の決定と、持ち越した審査の指摘の受け方）

NFR 要件・NFR 設計・インフラ設計の文書は承認の場の前の初稿で、承認の場の決定はまだ無い（各段の審査はすべて READY で、指摘は持ち越し）。この計画は、設計の文書を書き換えずに、次のとおり各段の決定と指摘を受ける。承認の場で設計の文書を直すときは、段のやり直しを先に行い、この計画との差を確かめ直す。この単位には機能設計がない（packaging の単位のため）。

### 依頼者が決めたこと

| 決定 | 内容 | 反映先 |
|---|---|---|
| Practices Discovery（2026-11-14） | 内部DB 用の PostgreSQL を compose の profile で選べる形にする。既定の内部DB は H2 のまま。PostgreSQL のバックアップと戻しの手順を文書に足す | Step 2〜7 |
| U3 の承認の場（2026-11-28） | 起動の出力の接続先 URL（資格情報を含まない）は既知の制約として README・文書に書く。検索の Unicode の端の 3 つの差（`İ`・語末シグマ・`ǅ`）は文書に書く | Step 5・6 |

### この計画が決める点（設計の審査の指摘の答え。依頼者の承認で確定する）

| 指摘 | この計画の決め | 反映先 |
|---|---|---|
| NFR 設計 N-01（healthcheck の形） | `CMD-SHELL` と `pg_isready -h 127.0.0.1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"`。使い捨てのコンテナで、初回の初期化の間は失敗し本起動の後に成功することを確かめる | Step 1・2 |
| NFR 設計 N-02（`docker compose exec` の変数の展開） | `docker compose exec -T internal-db-postgres sh -c '…'` の単一引用符の形。出力が壊れないこと・戻しで件数が一致することを使い捨てのコンテナで確かめる | Step 1・5 |
| NFR 設計 N-03〜N-06 | `traceability` の一覧は機械が決める（設計側の文書の直し）。`${VAR:-}`・`.env` の戻し方・ログの記述は、インフラ設計の記述のとおり | Step 2・3 |
| インフラ設計 N-01（H2 に戻すときのパスワード） | 「H2 を作ったときの値（既定は空）に戻す」と書く。H2 を空でないパスワードで作っていた場合は、その値に戻す | Step 3・5 |
| インフラ設計 N-02（空の資格情報で起動を拒むのは初回の初期化のときだけ） | 文書の「困ったとき」と `.env.example` のコメントに、「変えるときはボリューム `mastersmith_mastersmith-internal-db-postgres` を作り直す（中のデータは消える）」と書き、使い捨てのコンテナで、初期化済みのボリュームに別の値を渡しても効かないことを確かめる | Step 1・3・5 |
| インフラ設計 N-03（Info。イメージの揃え方） | 文書と `dependabot.yml` に「`internal-db-postgres` と `targetdb-postgres` と `TargetDbImages` は同じ版・ダイジェストに一緒に上げる」の 1 文を書く | Step 4・5 |
| U1・U2・U3 から持ち越した文書の項目 | (1) Flyway の INFO に接続先 URL が出る既知の制約、(2) 検索の Unicode の端の差、(3) `cicd-pipeline.md` の入力名の誤記の訂正を文書に書く（U1 の記録は書き換えない）、(4) `docs/verification.md` に `internalDbPostgresTest`・入力 `internal_db_postgres` と時間の目安（U3 の全件は約 1 分半）、(5) `flyway-database-postgresql` のライセンスは Build and Test に残す | Step 5・6・8 |

### 上流との差（`code-summary.md` の「上流との差」に 1 か所で書く）

1. NFR 設計 DD-1 の「ログは json-file（他のサービスと同じ）」は、app・lgtm・mailpit と同じ形であり、`targetdb-postgres` とは同じではない（インフラ設計で訂正済み）。
2. NFR 設計 DD-3 の「3 行をコメントに戻す」は、`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消す（またはコメントにする）、`MASTERSMITH_DB_PASSWORD` は H2 を作ったときの値に戻す、に読み替える（インフラ設計で訂正済み）。
3. テストの定数は新しく足さず、既存の `TargetDbImages.postgres()` を使う（NFR 設計 DD-9）。

## 要件との対応

| 要件 | 計画の手順 |
|---|---|
| FR4.1 | Step 2（compose のサービス・profile・ボリューム・資格情報の変数） |
| FR4.2 | Step 2・4（同じ版・ダイジェスト、`dependabot.yml` の固定先の一覧） |
| FR4.3 | Step 2・8（既定は H2 のまま。profile なしの `docker compose config` が変わらない） |
| FR4.4 | Step 5・6（バックアップと戻し・H2 だけの運用の 5 項目の明記） |
| FR4.5 | Step 5・6（README・`docs/internal-db.md`・設定・配備・検証の文書） |
| NFR1.1 | Step 8（profile なしの `docker compose config` の突き合わせ） |
| NFR2.x | Step 2・3・5・8（資格情報を書かない・ポートを開けない・別のサービス・複写の置き場・`mem_limit`） |
| NFR3.1・NFR8.1 | Step 2・4・8（版・ダイジェストの一致、依存を足さない） |
| NFR4.1・NFR4.2 | Step 1・2・8（healthcheck と `depends_on`、バックアップ・戻しの練習は Deployment Execution） |
| NFR5.1・NFR6.1・NFR9.1 | Step 8（`backend/` に差分が無い・テストを足さない） |

## 実施の手順（Testing Contract: test-after。層ごとに実装を書き、その層の確かめを流す）

当たる層は、配備の設定（compose・`.env.example`・`dependabot.yml`）と文書。アプリの層（API・画面・業務処理）は当たらない。この単位は新しいテストのクラスを足さず（NFR5.1）、各 Step の「確かめ」を流して通す。

- [x] **Step 1: 実行の確かめと、使い捨ての確かめ（骨組み）**
  - `unit-test-instructions.md` の命令が使えることを確かめる: `docker compose config`（profile なし・あり）と `docker compose config --profiles` が動く。変更の前の `docker compose config`（profile なし）の結果を、リポジトリの外（scratchpad）に保存する（Step 8 の突き合わせの基準）。
  - 使い捨てのコンテナ（`docker run --rm -d --name u4trial …`。名前 `mastersmith-*` の既存のコンテナには触れない。終わったら停止・削除する）で確かめる（`project.md` の Corrections の「DB の振る舞いは先に試す」）:
    1. 公式の `postgres:18.6` が、初回の初期化の間は `pg_isready -h 127.0.0.1` が失敗し、本起動の後に成功すること（`-h` 無しとの差も見る）。
    2. `POSTGRES_PASSWORD` が空のとき、起動を拒む（ログに理由が出る）こと。`POSTGRES_USER`・`POSTGRES_DB` が空のときの既定の挙動。
    3. 初期化済みのボリュームに、別のパスワード・利用者の値を渡して再起動しても効かないこと（インフラ設計の審査 N-02）。
    4. `docker exec -T <コンテナ> sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > ダンプ` が壊れずに出力でき（`pg_restore --list` で読める）、`docker exec -T … sh -c 'pg_restore -U … -d … --clean --if-exists' < ダンプ` で戻り、`psql -At -c` の件数が一致すること。パスワードを引数にも環境にも手元のシェルで書かないこと。
    5. 見本の `/var/lib/postgresql` へのマウント（PostgreSQL 18 のデータの置き場）で、ボリュームを作り直して再起動してもデータが残ること。
  - 結果の要点を `code-summary.md` の「確かめの結果」に書く。手順の例（文書）は、この確かめで通った形だけを書く。

- [x] **Step 2: compose にサービスを足す（配備の設定。実装）**
  - `compose.yaml`: サービス `internal-db-postgres`（`profiles: [internal-db-postgres]`・`image: postgres:18.6@sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42`・`ports` なし・`mem_limit: 512m`・`restart: "no"`・`environment`: `POSTGRES_USER`・`POSTGRES_PASSWORD`・`POSTGRES_DB` を `${MASTERSMITH_INTERNAL_DB_POSTGRES_USER:-}` などの空の既定値の形で・`volumes: mastersmith-internal-db-postgres:/var/lib/postgresql`・`healthcheck`（`CMD-SHELL`・`pg_isready -h 127.0.0.1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"`・`interval: 5s`・`timeout: 5s`・`retries: 12`・`start_period: 10s`）・ログは json-file（10m・3 本））。サービスの上に、起動・停止・消し方・ポートを開けない理由・資格情報の渡し方をコメントで書く（`mailpit` の形）。
  - ファイルの先頭の使い方のコメントに、内部DB 用の起動の 1 行を足す。`volumes:` に `mastersmith-internal-db-postgres:` を足す。
  - `app` サービスに `depends_on: internal-db-postgres: {condition: service_healthy, required: false}` を足す（既存の設定は変えない）。
  - 確かめ: `docker compose config`（profile なし）に内部DB 用のサービス・ボリューム・依存が出ないこと、`docker compose --profile internal-db-postgres config` に出ること、`ports` が無いこと。

- [x] **Step 3: `.env.example` に項目を足す（配備の設定。実装）**
  - 「内部DB（H2）」の節の近くに、「内部DB を PostgreSQL にする（compose の profile `internal-db-postgres`）」の節を足す。`MASTERSMITH_INTERNAL_DB_POSTGRES_USER`・`MASTERSMITH_INTERNAL_DB_POSTGRES_PASSWORD`・`MASTERSMITH_INTERNAL_DB_POSTGRES_DATABASE` を、値を空のまま（`# 秘密情報` の注記つき。他の項と同じくコメントのままにするか、空の行にするかは、`MASTERSMITH_DB_PASSWORD=` の既存の形に合わせて決める）足す。接続先の例（`jdbc:postgresql://internal-db-postgres:5432/<データベース名>` と、`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD` を同じ値にする）を、資格情報を含まない形でコメントに書く。H2 に戻すときの手順（`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` の行を消す、`MASTERSMITH_DB_PASSWORD` は H2 を作ったときの値に戻す）と、ボリュームを作り直すときのこと（初期化済みなら `.env` の値を変えても効かない）をコメントに書く。
  - 確かめ: `.env.example` に資格情報の値が無いこと（`grep` と Gitleaks）。

- [x] **Step 4: `dependabot.yml` のコメントを直す（配備の設定。実装）**
  - `.github/dependabot.yml` の docker の項のコメントの固定先の一覧に、内部DB 用の `postgres`（`compose.yaml` の `internal-db-postgres`）を足し、「`targetdb-postgres` と `TargetDbImages.postgres()` と同じ版・ダイジェストで、上げるときは一緒に上げる」と書く。設定の値（`ignore` など）は変えない。
  - 確かめ: `git diff` がコメントの行だけであること。

- [x] **Step 5: `docs/internal-db.md` を PostgreSQL に対応させる（文書。実装）**
  - タイトルと冒頭の説明を、「内部DB（H2 が既定。PostgreSQL は profile で選ぶ）」に合わせる。
  - 新しい節「内部DB を PostgreSQL にする」を足す: 対応する版（確かめた 18 系だけ。イメージは `compose.yaml` を見る）・起動の手順・接続先の設定（`.env` の 6 つの変数）・H2 に戻す手順・ボリュームを作り直すとき・既知の制約（Flyway の INFO に資格情報を含まない接続先の URL が出る、アプリの利用者がデータベースの所有者（スーパーユーザー）の 1 アカウントである、検索の Unicode の端の 3 つの差（`İ`・語末シグマ・`ǅ`）、MySQL・MariaDB は内部DB として対応しない）・困ったとき（空の資格情報で起動を拒む・初期化済みのボリュームでは値が効かない・`docker compose logs internal-db-postgres`）。
  - 新しい節「PostgreSQL の内部DB のバックアップと戻し」を足す: Step 1 で確かめた `docker compose exec -T … sh -c '…'` の形（置き場は `~/.mastersmith-backup/`・権限 700・コミットしない・失うもの）と、件数の数え方。
  - 既存の H2 の節（バックアップと戻し・複写を数える・ファイルの詰め直し）の見出しの直下に「H2 のときだけ」と明記する。`DEFRAG_ALWAYS`・`docker/hikari-pool.sh` の compact・`perf/h2-shell.sh`・ボリュームの `tar` の戻し・`-Dh2.compactThreads=1` の 5 項目すべてに当たること（チェックリスト: 各項目の文書の場所を `code-summary.md` に表にする）。
  - 確かめ: 例のコマンドに資格情報の値・`PGPASSWORD=` の直書きが無いこと。

- [x] **Step 6: README と周りの文書を直す（文書。実装）**
  - `README.md`: 内部DB の節（なければ「コンテナでの起動と確認」の中）に、種類（H2 が既定・PostgreSQL は profile で選ぶ）・起動の 1 行・`docs/internal-db.md` への案内・既知の制約の要点を書き、文書の目次の `docs/internal-db.md` の説明を直す。
  - `docs/configuration.md`: `MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME`・`MASTERSMITH_DB_PASSWORD` の行に PostgreSQL のときの値の形を足し、新しい 3 つの変数（compose 側）を足す。`;DEFRAG_ALWAYS=TRUE` は H2 のときだけと明記する。
  - `docs/operations.md`: バックアップの項の H2 の手順に「H2 のときだけ」と 1 行足し、PostgreSQL の手順への案内を書く。資源（内部DB 用 512m を使うときは使わない対象DB・監視・負荷の試験を止める）を 1 行。
  - `docs/deployment.md`: 内部DB の種類の選び方と、PostgreSQL のときの配備の戻しに 1 行。
  - `docs/verification.md`: 内部DB の PostgreSQL の結合テストの流し方（`./gradlew :backend:internalDbPostgresTest`・colima の 2 つの環境変数・CI の手動の入力 `internal_db_postgres`。誤記の `internal_db_postgresql` は使わない）と、時間の目安（全件で約 1 分半、U3 の待ちの確かめを含む）を足す。
  - `docs/dsl.md`: 「内部DB のファイルの大きさ」の既知の制約に、H2 のときだけであることを 1 行足す。
  - 確かめ: 変えた行が文書の記述だけで、版の文字列を重ねて書いていないこと（`grep` で `postgres:18` の記述が `compose.yaml` の 2 サービスと `TargetDbImages` 以外に無いこと）。

- [x] **Step 7: 使い捨てで、文書の手順を実行して確かめる（確かめ）**
  - 使い捨ての内部DB（`docker compose --profile internal-db-postgres` と一時的な `.env` ではなく、`docker compose -p u4trial --env-file <scratchpad の一時ファイル>` で、プロジェクト名を別にして、使い捨ての値だけで起動する。既存の `mastersmith` プロジェクトのコンテナ・ボリュームには触れない）に対して、文書の起動・件数の数え・バックアップ・戻しの手順を実行して通ること。ここで使う値（パスワードなど）は一時ファイルにだけ置き、出力・記録に書かず、終わったらコンテナ・ボリューム・一時ファイルを消す。アプリのコンテナの結合（`app` を起動して内部DB に接続する）は、Deployment Execution で行う。
  - 通らない手順は文書を直す。結果の要点だけを `code-summary.md` に書く。

- [x] **Step 8: 全体の確かめ・記録**
  - 確かめ: (1) profile なしの `docker compose config` が、基準（Step 1）と、内部DB 用のサービス・ボリューム・依存を除いて同じ。(2) `grep` で、`compose.yaml` の `internal-db-postgres` と `targetdb-postgres` の `image:` の行と `TargetDbImages` の版・ダイジェストが一致。(3) `git diff --stat` で `backend/`・`frontend/`・`ci.yml`・lockfile・`TargetDbImages` に差分が無いこと。(4) 文書・設定に資格情報の値が無いこと（Gitleaks が通る）。(5) 承認済みの確かめのコマンド（`./gradlew verifyQuick`。この単位は `internalDbPostgresTest` を持たない）が通る。
  - `code-summary.md`（作ったもの・変えたもの・確かめの結果・上流との差・依頼者に確かめたいこと・後の単位へ渡すこと）・`traceability.json`（`aidlc engine sensor-traceability --stage code-generation --unit u4-local-runtime-docs --output-path <絶対パス> --write-upstream` で作る。測っていない目標は OK にせず Deferred にして持ち主の段を書く）・`source-manifest.json` を書く。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Methodology evidence**: 今の `team.md` で確定した決まりを引き継いだ。各 Bolt で層ごとに実装を書いてから同じ Bolt の中でその層のテストを書いて流す形で、これまでの Intent もこの形で進めてきた。今回の Intent（内部DB に PostgreSQL を足す）で進め方を変える理由は見つかっていない。\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- 統合の前の `./gradlew verifyQuick` は、バックエンドのカバレッジの下限を判定しない（単体テストの記録だけから参考値を `backend/build/reports/jacoco/quick/` に出して表示し、下回っても失敗にしない）。バックエンドの下限は、全件の `./gradlew verify` を流す場面（Way of Working）と CI で判定する。画面のカバレッジの下限は `verifyQuick` でも判定する。（2026-11-08 Intent 261107-verify-quick-local で追加）\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる（外すパッケージは無い）。全体の合計で判定していた既存のパッケージの一覧（`backend/build.gradle.kts` の `packagesJudgedByTotal`。最後は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`））は、Intent 261108-ci-checks-release で `common.health` にテストを足して下限を満たし、一覧とそれを使った除外の仕組みを消した。パッケージを外す一覧を再び作らず、下限を満たさないパッケージはテストを足して満たす。（2026-11-08 Intent 261107-verify-quick-local で書き換え）（2026-11-09 Intent 261108-ci-checks-release で書き換え）\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを全件の `./gradlew verify` の中と CI で毎回実行する。統合の前の `./gradlew verifyQuick` では、`-PitTests` で絞ったときに当たるものだけを流す。コンテナの実行環境が無いときの扱いは Way of Working のとおり。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- テストを足した Bolt の後は、統合の前に流した1コマンドの検査（`./gradlew verifyQuick`。全件の場面に当たる Bolt では `./gradlew verify`）の時間を、Build and Test では全件の `./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E を流す時点は Way of Working のとおり（Intent の最後とリリースの前）とする。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 権限ごとの API の認可のテストの共通の形（下の役割・権限、業務データ、他テーブル参照の一覧から「共通の形」として参照する）: 未認証（401）・要る権限だけを欠く利用者（403。権限が何も無い利用者ではなく、ほかの権限は持つ利用者）・権限を持つ（200）を、サーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書く。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB を使うテストは組み込みの H2 を基本とし、コンテナではなく本番と同じ組み込みの H2 で行う。H2 での結合テストの全件は今のまま残す。内部DB を PostgreSQL で動かす経路に固有の振る舞いを確かめる結合テストは、H2 の全件とは別のバリエーションとして作り、版を固定したイメージのコンテナで起動した実際の PostgreSQL で行う（固い制約は `project.md` の Mandated）。このテストは明示の指定（Gradle の指定。名前は設計で決める）を付けたときだけ流し、通常の `./gradlew verify`・`./gradlew verifyQuick`・CI（`develop` へのプッシュ）では流さない。流す場面は決めず、必要と判断したときに明示して流す（CI で流すときも明示の指定を付けた実行に限る。形は設計で決める）。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストと、内部DB の PostgreSQL の結合テストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) （2026-11-14 Intent 261114-internal-db-postgresql で書き換え。H2 を基本とし、PostgreSQL に固有の結合テストを明示したときだけ流す形にした）\n\n- 画面を廃止する・移すことで既存の E2E を書き換える・消すときは、本数に数えない。ただし、その画面で確かめていた検査（例: 管理の仮の画面の E2E 030・090・130 の 403 の表示・アクセシビリティ・管理の権限の確認の API）は、別の画面か別のテストへ移して残し、移す前と後の件数を Build and Test で記録する。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で実行し直し、実測の数字だけを報告する。`./gradlew verifyQuick` の JaCoCo の値は単体テストだけの参考値で、カバレッジの報告には使わない。 (learned 2026-09-23)  （2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。すべての組で行うのは、その Intent で変えた画面だけとし、変えていない画面は E2E の既存の検査に任せる（2026-11-07 依頼者の決定で緩めた）。 (learned 2026-09-28) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- 長いテスト（verify・E2E・k6）は、試験の台本全体を `caffeinate -dims` で包む（場面ごとに起こし直すと切れ目で守りが外れる）。流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（DarkWake の Thermal Emergency のスリープは `caffeinate -i` では止められない）。 (merged 2026-11-04)\n- colima の PC で `./gradlew verify`、または `-PitTests` を付けた `./gradlew verifyQuick` を流すときは、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`（書き方は `docs/target-db.md`）をシェルに渡す。渡さないと対象DB のテストが SKIPPED になり、`verify` ではパッケージごとのカバレッジの下限で失敗する。`-PitTests` を付けた `verifyQuick` では、バックエンドのカバレッジの下限を判定しないため、飛ばさずに失敗する（CI と同じ扱い）。 (merged 2026-11-04)（2026-11-08 Intent 261107-verify-quick-local で書き換え）\n- Playwright の `webServer.env` に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる。 (merged 2026-11-04)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:552cb5658f2cb47e1e82a7327ac8ad923769dea2d1ef59be8f82d75eefe758af",
  "contract_sha256": "sha256:a556f2389fe8ebabfea24044e818005bb3c9a4fafaa7645490018229aa364acb"
}
```
