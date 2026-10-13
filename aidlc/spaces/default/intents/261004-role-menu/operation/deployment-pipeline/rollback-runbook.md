# 戻しの手順書（rollback-runbook）

Intent 261004-role-menu の配備を戻す手順、配備の後に行う戻しの練習、アプリが止まったとき（`ExitOnOutOfMemoryError`）の手順です。README の「戻し方」「内部DBのバックアップと戻し方」と、前の Intent の `rollback-runbook.md`（`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/rollback-runbook.md`。以下「前の手順」）を正とし、差だけを書きます。練習の環境の作り方は、`aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/rollback-runbook.md` の 5節の形を使います。

## 1. 戻すきっかけ

前の手順の 1節と同じです（起動しない、スモークテストが通らない、ERROR が出続ける・ログに秘密やメールアドレスそのものが出る、後で重大な問題が見つかりその場で直せない）。加えて、配備の後の性能の問題（配備の前の k6 を省いたため。`cd-config.md` の 3.2）もきっかけになりえます。戻す前に、依頼者に伝えて戻すかを決めてもらいます。

- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻しません。7節の手順で原因を確かめます（同じ入力で繰り返し止まるときは、戻しても直らない見込み）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-role-menu`（イメージ ID `fefeafbd299b…`、前の Intent で配備した `273c021` の版）。
- `.env` は変えていないため戻さない。内部DB はそのまま使い、スキーマは戻さない（V12 のまま。前進のみ）。

### 2.1 手順

1. **戻す前に、版 2 のプレビューがあれば依頼者が DSL の画面で破棄する**（`DSL_PREVIEW_DISCARDED`。前の版はこれを読めず、表示が 500・適用が失敗するため。`construction/dsl-v2/nfr-design/logical-components.md` 7節の 2）。適用中の版 2 の DSL を手元に残したいときは、DSL の画面の履歴のタブからダウンロードしておく（任意）。
2. アプリを戻す。

```bash
docker image inspect mastersmith:pre-role-menu --format '{{.Id}}'   # sha256:fefeafbd299b… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（README の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz）
MASTERSMITH_IMAGE_TAG=pre-role-menu docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-role-menu docker compose --profile targetdb-postgres ps app   # healthy を待つ
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-role-menu` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、`273c021` を `git worktree add` で取り出して WAR とイメージを作り直します。

3. 開いているタブを読み込み直す（画面の部品が前の版に戻るため）。
4. 戻した後の DSL（Q1: A）: 前の版の起動のログに ERROR「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」が1件出る（版 2 を読めないため。想定どおり）。依頼者が前の版の DSL の画面で「スキーマを読み込む」で既定の DSL（版 1）を生成し、プレビューを確かめて適用する（`DSL_GENERATED`・`DSL_APPLIED`）。監査に残るため、行う前に伝える。
5. 4.1 の確かめを行う。

### 2.2 新しい版へ上げ直すとき

- 戻している間に適用した版 1 の DSL は、新しい版でまた読めなくなる。上げ直した後は `cd-config.md` の 5節の手順で、版 2 を生成し直して適用する（版 1 のプレビューが残っていれば先に破棄する）。
- 上げ直すときに移行は当たらない（V12 のまま）。

## 3. 配備の前の複写を戻すとき（第二の手）

使うのは、移行（Flyway）が途中で失敗したとき・データが壊れたときだけです（H2 は DDL を巻き戻せないため、V10〜V12 の途中の失敗にはこの手を使う）。

```bash
docker compose stop app
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4_7-jre-noble@sha256:b573af9e331196fbc42e246da4df24df9b6c556c73e7efddfde0511f1c9508c5 \
  sh -c 'rm -rf /data/* && tar xzf /backup/<mastersmith-data-<日時>-pre-role-menu.tgz> -C /data && chown -R 10001:10001 /data'
MASTERSMITH_IMAGE_TAG=pre-role-menu docker compose --profile targetdb-postgres up -d --no-build app
```

- 内部DB は配備の前の状態（V9、版 1 の適用中の DSL）に戻る。**配備の後のデータ（ロール・グループ・割り当て・作業ロール、利用者の変更、版 2 の DSL の履歴）と、配備の後の監査の行はすべて失われる。** 戻す前に依頼者の承認を得る。
- `.env` は変えていないため戻さない。

## 4. 戻したときの状態

| 項目 | 戻している間の動き |
|---|---|
| 足した表と列（V10〜V12） | 残ったまま。前の版はグループ・ロール・権限の設定・割り当て・作業ロールの表と、監査の3列（`target_role_id`・`target_group_id`・`detail`）を見ない。Flyway は自分より新しい適用済みの移行を既定（`*:future`）で無視して起動する見込み（6節の練習で確かめる） |
| 管理の権限 | 前の版も新しい版も `users.admin_flag` で決まるため、管理の画面と管理の API の可否は変わらない（要件 C1） |
| ロール・グループ・業務のメニュー | 前の版には画面も API も無い。設定と割り当ては表に残り、上げ直すとそのまま使われる |
| DSL | 版 2 の適用中の DSL は読めず、DSL が無い状態で起動する。2.1 の 4 で版 1 を生成し直す |
| 監査 | 配備の後に記録した `GROUP_*`・`ROLE_*`・`WORK_ROLE_SWITCHED`・`ROLE_TRANSFER_APPLIED` の行は残る。前の版はこれらの種類を書かず、監査の行を実体として読む経路も無い（`273c021` の `backend/src/main` で `AuditEventRepository.findAllByOrderByOccurredAtAsc` は宣言だけで、呼ぶ箇所は無い。G-19 の前提。6節の練習で再確認する） |
| JVM | 前の版のイメージには `ExitOnOutOfMemoryError` が無い。ヒープが尽きても JVM は止まらず動き続けうる（7節は当たらない） |
| ログ | `StatementCreatorUtils` の OFF は前の版に無いが、前の版は `JdbcTemplate` を使わないため出ない |

### 4.1 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy。ERROR は「適用中の DSL を読めない…」の1件だけ（レベルとロガーとメッセージと件数だけを見る）。Flyway の誤りが無い |
| R2 | ★ログインと管理の画面 | 依頼者 | 初期管理者でログインし、利用者の管理の画面が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |
| R3 | ★DSL | 依頼者 | 2.1 の 4 の生成と適用ができ、DSL の画面が使える |

## 5. 戻したときのそのほかの注意

| 注意 | 中身 |
|---|---|
| `main` | `main` を取り込んだ後にアプリを戻しても、`main` は戻さない（タグは付けていない）。直した版を配備し直すときに `main` を進める |
| 見本の対象DB・Mailpit | 戻しの対象外。動かしたままでよい |
| 救済 | 前の版にも同じ救済の口がある。`.env` を変えていないため、戻しの起動で救済が働く条件は配備のときと同じ |

## 6. 戻しの練習（Q3: A。`U3-NFR3.6b`・`U4-NFR3.6b`・G-19・AC6.1.8）

配備とスモークテストの後に、配備の後の複写（`deployment-strategy.md` の S11 で取った `…-after-role-menu.tgz`）で前の版を起動して確かめます。**配備したアプリは止めません**（VM 6GiB に、配備したアプリ 2g と練習のアプリ 2g・練習の対象DB 512m が入る）。練習での操作は練習の環境の監査にだけ残り、環境ごと消えます。

### 6.1 練習の環境を作る

1. 配備の `.env` を、中身を表示せずにホームの下の練習用の場所（権限 700）へ複写する。`mktemp -d` は使わない（colima の VM から見えない）。`.env.targetdb` は複写しない。
2. 練習の対象DB のための一時の環境ファイルを、同じ場所に乱数のパスワードで作る（`perf/dsl-timing.sh` の `targetdb.env` と同じ形。値は表示しない）。中身は `POSTGRES_PASSWORD`（練習の DB の管理者）と `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`（読み取りのアカウント）の2行。
3. `.env` の複写の末尾に次の2行を足す（同じ項目が2行あると後の行が効く）。
   - `MASTERSMITH_WEB_BASE_URL=http://localhost:18080`（足さないと 18080 からのログインが 403）
   - `MASTERSMITH_TARGET_DB_PASSWORD=<2 の読み取りのアカウントと同じ乱数>`。`.env` の複写のほかの `MASTERSMITH_TARGET_DB_*` は接続先 `targetdb-postgres:5432`・DB `business`・スキーマ `sales`・アカウント `mastersmith_reader` を指し、練習の網の中では練習の対象DB（初めての起動で見本のスキーマと読み取りのアカウントを作る）に当たる。
   そのうえで、環境変数を渡す（`-f` の compose は `.env` を読まないため）。
   ```bash
   export MASTERSMITH_PERF_ENV_FILE="$HOME/<練習用の .env の複写>" \
          MASTERSMITH_PERF_TARGETDB_ENV_FILE="$HOME/<練習用の対象DB の環境ファイル>" \
          MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g
   ```
4. `docker compose -p mastersmith-rollback -f docker/perf/compose.yaml create app` でボリュームを作り、配備の後の複写を `mastersmith-rollback_perf-data` へ展開して、持ち主を 10001 にする。
5. 前の版と練習の対象DB を起動する。
   ```bash
   MASTERSMITH_IMAGE_TAG=pre-role-menu docker compose -p mastersmith-rollback -f docker/perf/compose.yaml --profile targetdb-postgres up -d --wait app targetdb-postgres
   ```
   `127.0.0.1:18080` に起動する。`--wait` は compose のヘルスチェック（`/dev/tcp` で `/actuator/health` の 200）を待つ。イメージに HEALTHCHECK が無いため、`running` を健全と読まない（`project.md` の学び）。

### 6.2 確かめと合格の条件

| # | 確かめ | 行う人 | 合格の条件 |
|---|---|---|---|
| D1 | 前の版の起動 | AI | `/actuator/health` が 200。Flyway の誤りが無く、V10〜V12 を無視して起動している（`Migrating schema` が 0 件、検証の失敗が無い）。Hibernate の検証で起動が止まらない |
| D2 | DSL が無い状態での起動（AC6.1.8） | AI | ERROR は「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」1件だけ（キー `dsl.errorKinds` の名前だけを見る） |
| D3 | ★管理の可否（権限のずれ、`team.md` の Deployment） | 依頼者と AI | 依頼者が `http://localhost:18080` で初期管理者としてログインし、利用者の管理の画面が開ける（管理の API が 200）。練習を止めた後に練習の H2 を読み取りで開き、`users` の `admin_flag` が真の行の数が、配備の前の複写（`cd-config.md` の 4節）と同じ |
| D4 | ★G-19（前の版が新しい監査の行のある内部DB で動く） | AI と依頼者 | `GROUP_*`・`ROLE_*` の行と新しい3列の値がある複写で、D1〜D3 と D5 が失敗しない。`git grep` で `273c021` の `backend/src/main` に監査の行を実体として読む呼び出し（`findAllByOrderByOccurredAtAsc`・`findAll` など）が無いことを再確認する |
| D5 | ★前の版での DSL の生成し直し（Q1: A） | 依頼者 | 前の版の DSL の画面で「スキーマを読み込む」→ プレビューを確かめて「適用する」ができ、適用の後に DSL の画面が使える。練習の監査に `DSL_GENERATED`・`DSL_APPLIED` が各 1 件 |
| D6 | ログアウト | 依頼者 | 練習の画面でログアウトする（署名鍵が同じため、練習で出したトークンを配備したアプリで使えないよう、リフレッシュトークンを無効にしてから消す） |

- D3 の 403 の側（管理者でない利用者が管理の API で 403）は、練習の環境に管理者でない利用者でログインできる手段（パスワードを依頼者が知る利用者）があるときだけ確かめる。無いときは、前の版の結合テストで確かめ済みの振る舞いとして、行えなかったことを記録する。
- 練習の監査は、止めた後に練習の H2 を読み取りで開いて種類ごとに数える（`LOGIN_SUCCEEDED`・`LOGGED_OUT`・`DSL_GENERATED`・`DSL_APPLIED`）。

### 6.3 片付け

結果を Deployment Execution の記録に書いてから、`docker compose -p mastersmith-rollback -f docker/perf/compose.yaml --profile targetdb-postgres down -v` で練習の環境を消し、練習用の `.env` の複写と対象DB の環境ファイルも消します。配備の前と配備の後の複写は残します。消す前に、記録に要る数を取り終えたかを確かめます（`project.md` の学び）。

## 7. アプリが止まったとき（`ExitOnOutOfMemoryError`、Q6: A）

新しい版はヒープが尽きると JVM が止まり、`restart: "no"` のため起き直しません。

1. 止まったことを確かめる。
   ```bash
   docker compose --profile targetdb-postgres ps -a app                       # Exited であること
   docker inspect mastersmith-app-1 --format '{{.State.ExitCode}} {{.State.OOMKilled}} {{.State.FinishedAt}}'
   docker compose logs --no-log-prefix app | grep -c 'OutOfMemoryError'        # 件数だけ
   docker compose logs --no-log-prefix app | grep -o 'java\.lang\.OutOfMemoryError: [A-Za-z ]*' | sort | uniq -c   # 種類だけ
   ```
   - 終了コード 3・OOMKilled false: JVM がヒープの不足で止まった（`ExitOnOutOfMemoryError`）。
   - OOMKilled true: コンテナのメモリの上限（2g）で止められた。JVM の外の使い方を疑う。
   - どちらでもない: ほかの原因。ログの ERROR のロガーと件数を見る。
2. 直前の操作（権限の YAML の確かめ・適用、DSL の投入・プレビューの表示、大きな書き出し など）を依頼者に確かめ、時刻と操作を記録する。止まった時刻の前後の監査の行は、確定していないものは残らない。
3. 起動し直す（同じコンテナ）。
   ```bash
   docker compose --profile targetdb-postgres start app
   docker compose --profile targetdb-postgres ps app      # healthy を待つ
   ```
   依頼者がログインでき、ホームが開けることを確かめる。
4. 同じ入力で繰り返し止まるときは、入力の大きさ・形の問題（Build and Test で受け入れた制約。権限の YAML の上限ちょうどの悪い形でヒープの余裕が小さい）として、その操作を控え、戻すかどうかを依頼者に諮る（前の版は止まらずに動き続けうるだけで、同じ入力のヒープの不足は直らない見込み）。
5. 記録は Incident Response・Feedback & Optimization の段に引き継ぐ。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/rollback-runbook.md`（5節の練習の環境）
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`（Q1・Q3・Q6）
- `construction/dsl-v2/nfr-design/logical-components.md`（7節）、`construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（3節・8節・9節）
- `construction/group/infrastructure-design/infrastructure-specification.md`（4節）、`construction/role/infrastructure-design/infrastructure-specification.md`（4節）・`cicd-pipeline.md`（3節）
- `construction/group/code-generation/code-summary.md`（G-19、4節・8節）とそのレビューの R-01
- `construction/build-and-test/build-and-test-summary.md`（`BT-Q1c`・`BT-Q2`・`U3-NFR3.6b`・`U4-NFR3.6b`）・`security-test-instructions.md`
- `inception/requirements-analysis/requirements.md`（C1）、`inception/user-stories/stories.md`（AC6.1.8）
- リポジトリ（読むだけ）: README（戻し方、内部DBのバックアップと戻し方、DSL の管理の API、スキーマの変更（Flyway））、`compose.yaml`（`restart: "no"`）、`docker/perf/compose.yaml`、`Dockerfile`、`git show 273c021:backend/src/main/java/cherry/mastersmith/dslmanage/service/DslStartupLoader.java`、`git grep findAllByOrderByOccurredAtAsc 273c021`
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 前の版（`273c021`）の Flyway は、自分より新しい適用済みの移行 V10〜V12 を既定（`*:future` を無視）で許し、Hibernate の検証も足した表と列で止まらない。6.2 の D1 で確かめる。
- [assumption] 前の版は版 2 の DSL を書式の誤りとして読めず、ERROR を1件出して DSL が無い状態で起動する（`273c021` の `DslStartupLoader` の作りから読んだ）。D2 で確かめる。
- D3 の 403 の側は、練習の環境で管理者でない利用者としてログインできる手段があるときだけ確かめる（6.2）。無いときは行えなかったことを記録する。
- [assumption] 配備の `.env` の `MASTERSMITH_TARGET_DB_*` は、README の「コンテナでの起動と確認」のとおり見本の対象DB（`targetdb-postgres:5432`・`business`・`sales`・`mastersmith_reader`）を指す（`.env` は開いていない）。違えば、6.1 の 3 で接続先の項目も練習の対象DB に合わせて足す。
