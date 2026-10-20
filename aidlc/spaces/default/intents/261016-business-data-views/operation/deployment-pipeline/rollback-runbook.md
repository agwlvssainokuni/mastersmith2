# 戻しの手順書（rollback-runbook）

Intent 261016-business-data-views の配備を戻す手順と、配備の後に行う戻しの練習です。README の「戻し方」「内部DBのバックアップと戻し方」「既にある見本の DB に書き込みのアカウントを足す（U2）」と、前の手順（`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/rollback-runbook.md`、練習の形は `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/rollback-runbook.md` の 6節）を正とし、差を中心に書きます。

## 1. 戻すきっかけ

- 起動が healthy にならない、Flyway が V13 で失敗する、ERROR が出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、スモークテスト（`deployment-strategy.md` の S1〜S13）が通らない、配備の後に性能の問題が出る（配備の前の k6 を省いたため。`cd-config.md` の 3.3）。
- 戻すかどうかは依頼者に諮って決める。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（7節）。
- 書き込みだけに問題があるときは、戻す前に、`.env` の書き込みの2行を外して書き込みだけを止める手もある（3節の 3.1）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-business-data-views`（イメージ ID `f103aee1702a…`、前の Intent で配備した `6dd9bb0` の版。アプリのコードは `776738e`）。
- 内部DB はそのまま使い、スキーマは戻さない（V13 のまま。前進のみ）。
- `.env` の書き込みの2項目、`.env.targetdb` の書き込みのパスワード、見本の PG の `mastersmith_writer`、`TEMPORARY` の取り上げは、戻しても残す（前の版は使わない。U2 の基盤の設計 Q4: A）。
- 見本の PG に新しい版で書いた行は、どちらの戻しでも戻らない（見本の対象DB は戻しの対象外。README の「戻し方」）。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-business-data-views --format '{{.Id}}'   # sha256:f103aee1702a… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（README の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz）
MASTERSMITH_IMAGE_TAG=pre-business-data-views docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-business-data-views docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-business-data-views` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、`6dd9bb0` を `git worktree add` で取り出して WAR とイメージを作り直します。
- 開いているタブを読み込み直す（業務データの画面が無くなり、画面の部品が前の版に戻るため）。
- サブモジュールの固定先は戻さない（イメージに画面のビルドの結果が同梱されているため）。

### 2.2 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200。ERROR 0 件。Flyway は `Migrating schema` 0 件で、「Schema "PUBLIC" has a version (13) that is newer than the latest available migration (12) !」の WARN が1件出る見込み（前の Intent の練習と同じ形） |
| R2 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと利用者の管理の画面が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.3 新しい版へ上げ直すとき

- 移行は当たらない（V13 のまま）。`.env` の書き込みの2項目は残っているため、`docker compose --profile targetdb-postgres up -d --build app`（直した WAR で）でそのまま書き込みも使える。

## 3. 書き込みの設定と見本の PG の戻し方

### 3.1 書き込みだけを止める（アプリは新しい版のまま）

- `.env` の書き込みの2行を外すと、書き込みの API は 409 `BUSINESS_WRITE_NOT_CONFIGURED` になり、読み取りは動き続ける。README のとおり、配備の前に取った `.env` の複写を戻す。

```bash
(umask 077 && cp "$HOME/.mastersmith-env-before-writer" .env)    # 中身は表示しない
grep -c '^MASTERSMITH_TARGET_DB_WRITE_' .env                     # 0 であること
docker compose --profile targetdb-postgres up -d --force-recreate --no-build app
```

- 複写 `$HOME/.mastersmith-env-before-writer` は、配備と戻しの練習が済み、戻す見込みが無くなってから消す（README の確かめのとおり。消すのは Deployment Execution の終わり）。

### 3.2 書き込みのアカウントを消す（依頼者が決めたときだけ）

- 通常は消さない（前の版は使わず、残っても害が無いため）。消すときは README の「消し方」のとおり、管理者で付与を取り消して `DROP ROLE mastersmith_writer` を行い、`.env` の書き込みの2行も外してアプリを作り直す。`TEMPORARY` は、戻す必要があるときだけ `GRANT TEMPORARY ON DATABASE business TO PUBLIC` で戻す（E2E が取り上げを前提にするため、通常は戻さない）。
- `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` は残す（空だと、新しいボリュームの初期化が `03-writer-account.sh` で止まるため）。

## 4. 配備の前の複写を戻すとき（第二の手）

使うのは、移行（Flyway）が V13 の途中で失敗したとき・データが壊れたときだけです（H2 は DDL を巻き戻せない）。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
  sh -c 'rm -rf /data/* && tar xzf /backup/<mastersmith-data-<日時>-pre-business-data-views.tgz> -C /data && chown -R 10001:10001 /data'
MASTERSMITH_IMAGE_TAG=pre-business-data-views docker compose --profile targetdb-postgres up -d --no-build app
```

- 内部DB は配備の前の状態（V12）に戻る。**配備の後のデータ（ロール・割り当て・作業ロール、利用者の変更、DSL の操作）と、配備の後の監査の行（`BUSINESS_ROW_*` を含む）はすべて失われる。** 戻す前に依頼者の承認を得る。
- 見本の PG に書いた行は、この手でも戻らない。

## 5. 戻したときの状態

| 項目 | 戻している間の動き |
|---|---|
| V13 の3列 | 残ったまま。前の版は `audit_events` の `target_schema`・`target_table`・`work_role_id` を見ない（空を許す列のため、前の版の追記も失敗しない）。Flyway は自分より新しい適用済みの移行を既定（`*:future`）で無視して起動する見込み（6節の練習で確かめる） |
| 監査の新しい値 | `BUSINESS_ROW_*`・結果 `UNKNOWN`・失敗の理由 `BUSINESS_*` の行は残る。前の版（`6dd9bb0`）の `backend/src/main` で `AuditEventRepository` を呼ぶのは `AuditEventRecorder` の `save` だけで、監査の行を実体として読む経路は無い（2026-10-21 に `git grep` で確かめた。6節の練習で再確認する） |
| 業務データの画面と API | 前の版には無い。業務のメニューの項目は前の版の準備中の画面へ移る |
| 書き込みの設定 | `.env` の書き込みの2項目は前の版が読まない（Spring の設定の結び付けに無い項目は無視される。6節で確かめる）。書き込みの接続は作られない |
| ロール・作業ロール | 前の版も同じ表を使うため、そのまま使える |
| 見本の PG | `mastersmith_writer` と `TEMPORARY` の取り上げは残る。前の版は読み取りのアカウントだけを使う |

## 6. 戻しの練習（Q3: A）

配備とスモークテストの後に、配備の後の複写（`deployment-strategy.md` の S13 で取った `…-after-business-data-views.tgz`）で前の版を別のコンテナ・番号で起動して確かめます。**配備したアプリは止めません**（VM 6GiB に、配備したアプリ 2g・見本の PG 512m・Mailpit と、練習のアプリ 2g・練習の対象DB 512m が入る。前の Intent の練習と同じ組み合わせに見本の PG が加わった形）。練習での操作は練習の環境の監査にだけ残り、環境ごと消えます。

### 6.1 練習の環境を作る

1. 練習用の場所をホームの下に作り（権限 700。`mktemp -d` は使わない）、配備の `.env` を中身を表示せずに `app.env` として複写する。`.env.targetdb` は複写しない。
   ```bash
   R="$HOME/.mastersmith-rollback-practice"; mkdir -p "$R" && chmod 700 "$R"
   (umask 077 && cp .env "$R/app.env")
   ```
2. 練習の対象DB のための一時の環境ファイルを乱数のパスワードで作る（`perf/dsl-timing.sh` の `targetdb.env` と同じ形。値は表示しない）。練習の対象DB も初めての起動で `03-writer-account.sh` を流すため、書き込みのパスワードも入れる（空だと初期化が止まる）。あわせて、アプリの接続先の上書きのファイル `app-targetdb.env` を作る（`docker/perf/compose.yaml` は `MASTERSMITH_PERF_ENV_FILE` の後に `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE` を読むため、同じ項目は後の値が効く）。
   ```bash
   A=$(openssl rand -hex 24); RD=$(openssl rand -hex 24); WR=$(openssl rand -hex 24)
   (umask 077 && printf 'POSTGRES_PASSWORD=%s\nMASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD=%s\nMASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=%s\n' "$A" "$RD" "$WR" > "$R/targetdb.env")
   (umask 077 && printf 'MASTERSMITH_WEB_BASE_URL=http://localhost:18080\nMASTERSMITH_TARGET_DB_PASSWORD=%s\nMASTERSMITH_TARGET_DB_WRITE_PASSWORD=%s\n' "$RD" "$WR" > "$R/app-targetdb.env")
   unset A RD WR
   export MASTERSMITH_PERF_ENV_FILE="$R/app.env" \
          MASTERSMITH_PERF_TARGETDB_ENV_FILE="$R/targetdb.env" \
          MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE="$R/app-targetdb.env" \
          MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g
   ```
   - `MASTERSMITH_WEB_BASE_URL` を足さないと、18080 からのログインが 403 になる（README の V8 の注意）。
   - `app.env` の書き込みの2項目（`mastersmith_writer` と配備のパスワード）は残したまま、パスワードだけを練習の値で上書きする。前の版が書き込みの項目を読まずに起動できることを確かめるため、項目は消さない。
3. `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml create app` でボリュームを作り、配備の後の複写を `mastersmith-rollback_perf-data` へ展開して、持ち主を 10001 にする。
   ```bash
   docker run --rm -v mastersmith-rollback_perf-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
     sh -c 'rm -rf /data/* && tar xzf /backup/<mastersmith-data-<日時>-after-business-data-views.tgz> -C /data && chown -R 10001:10001 /data'
   ```
4. 前の版と練習の対象DB を起動する。
   ```bash
   MASTERSMITH_IMAGE_TAG=pre-business-data-views docker compose -p mastersmith-rollback -f docker/perf/compose.yaml --profile targetdb-postgres up -d --wait app targetdb-postgres
   ```
   `127.0.0.1:18080` に起動する。`--wait` は compose のヘルスチェック（`/dev/tcp` で `/actuator/health` の 200）を待つ。イメージに HEALTHCHECK が無いため、`running` を健全と読まない（`project.md` の学び）。

### 6.2 確かめと合格の条件

| # | 確かめ | 行う人 | 合格の条件 |
|---|---|---|---|
| D1 | 前の版の起動 | AI | `/actuator/health` が 200。ERROR 0 件。Flyway は `Migrating schema` 0 件・検証の失敗なしで、「newer than the latest available migration (12)」の WARN が1件。Hibernate の検証で起動が止まらない |
| D2 | 書き込みの項目があっても誤りが出ない | AI | 起動のログに `MASTERSMITH_TARGET_DB_WRITE_` の項目の名前を含む行と、設定の結び付けの誤りが無い（件数だけを見る）。WARN は D1 の Flyway の1件と、前の版から出ているもの（Spring の Bean の案内、Flyway の H2 の版の案内）のほかに無い |
| D3 | ★ログインと画面 | 依頼者 | `http://localhost:18080` で初期管理者としてログインでき、ホームと利用者の管理の画面が開ける。業務のメニューの項目を選ぶと前の版の準備中の画面になる |
| D4 | ★新しい監査の行のある内部DB での動き | AI と依頼者 | `BUSINESS_ROW_*` の行・結果の値と V13 の3列の値がある複写で、D1〜D3 が失敗しない（ログインの `LOGIN_SUCCEEDED` の追記も成功する）。`git grep` で `6dd9bb0` の `backend/src/main` に監査の行を実体として読む呼び出し（`findAllByOrderByOccurredAtAsc`・`findById`・`findAll` など）が無いことを再確認する |
| D5 | ログアウト | 依頼者 | 練習の画面でログアウトする（署名鍵が同じため、練習で出したトークンを配備したアプリで使えないよう、リフレッシュトークンを無効にしてから消す） |

- 練習の監査は、止めた後に練習の H2 を読み取りで開いて種類ごとに数える（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）。配備の後の複写にあった `BUSINESS_ROW_*` の件数が変わっていないことも数える。

### 6.3 片付け

結果を Deployment Execution の記録に書き、記録に要る数を取り終えたかを確かめてから（`project.md` の学び）、練習の環境と一時のボリュームを消し、練習用の場所も消します。配備の前と配備の後の複写は残します。

```bash
docker compose -p mastersmith-rollback -f docker/perf/compose.yaml --profile targetdb-postgres down -v
rm -rf "$HOME/.mastersmith-rollback-practice"
unset MASTERSMITH_PERF_ENV_FILE MASTERSMITH_PERF_TARGETDB_ENV_FILE MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE
```

## 7. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順（`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/rollback-runbook.md` 7節）のとおり。ヒープが尽きると JVM が止まり（終了コード 3・OOMKilled false）、`restart: "no"` のため起き直さない。`docker compose --profile targetdb-postgres start app` で約 10 秒で戻る。
- 直前の操作に、業務データの大きな本文の作成・更新（上限まで詰めた値）が入ったことを依頼者に確かめて記録する。同じ入力で繰り返し止まるときは、戻しても直らない見込みのため、その操作を控えて戻すかを依頼者に諮る。
- 対象DB（見本の PG）が止まったとき（`OOMKilled` true など）は、アプリは動き続け、業務データの API が 503 を返す。`docker compose --profile targetdb-postgres start targetdb-postgres` で起動し直す（大きな本文の同時の書き込みは対象DB のメモリを使い切りうる既知の制約。README の「既知の制約」）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/rollback-runbook.md`（6節・7節）、`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-execution/deployment-log.md`（D1 の Flyway の WARN）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`（Q1〜Q3）
- この Intent の記録: `construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`（Rollback の行・5節）・`infrastructure-design-questions.md`（Q4: A）
- リポジトリ: README（「戻し方」「内部DBのバックアップと戻し方」「既にある見本の DB に書き込みのアカウントを足す（U2）」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`・`restart: "no"`）、`docker/perf/compose.yaml`（`MASTERSMITH_PERF_*_ENV_FILE`・18080）、`perf/dsl-timing.sh`（一時の環境ファイルの形）、`backend/src/main/resources/db/migration/V13__u2_business_data_audit_columns.sql`、`git grep`（`main` の `AuditEventRepository`・`AuditEventRecorder`）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 前の版（`6dd9bb0`）の Flyway は適用済みの V13 を既定で無視して起動し、Hibernate の検証も足した列で止まらない。6.2 の D1 で確かめる。
- [assumption] 前の版は `.env` の `MASTERSMITH_TARGET_DB_WRITE_*` を読まず、起動に影響しない。D2 で確かめる。
- 戻し先のイメージ `mastersmith:pre-business-data-views` は、配備の前（`cd-config.md` の順 3）に付ける。
