# 環境の確かめの結果（validation-report）

Intent 261004-role-menu の配備先の環境（開発者の PC 上のコンテナ）を、承認済みの設計の値と1つずつ突き合わせた結果です。確かめは 2026-10-14 に読み取りだけで行いました。アプリの入れ替え・戻し先のタグ付け・DSL の移し替え・スモークテスト・戻しの練習は、次の Deployment Execution の段で行います（`operation/deployment-pipeline/cd-config.md` の 3節）。書き方は前の Intent の同じ段の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/validation-report.md`）を正としました。

**判定: 合格**（設計の値を満たせない点は無し。承認済みの記録との書き方の差が 1 件（4節）、条件つきの点が 1 件（E17 の lgtm）、気づきが 3 件（6節））

この段の役割（基盤）に、セキュリティ（devsecops）とコンプライアンスの観点を合わせて確かめました。

## 1. 設計の値との突き合わせ

| # | 項目 | 設計の値（出典） | 実際の値（確かめ方） | 判定 |
|---|---|---|---|---|
| E1 | VM の資源 | CPU 4・メモリ 6GiB（group・role の `infrastructure-specification.md` 1節の Resource sizing、`cd-config.md` 1節） | CPU 4・メモリ 6GiB・ディスク 100GiB、Running（`colima list`）。VM の中の全体 5,910 MiB | 満たす |
| E2 | アプリの資源の上限 | メモリ 2g・CPU 4（group・role の 1節、`compose.yaml` の既定、`cd-config.md` 1節） | Memory 2,147,483,648 B・NanoCpus 4,000,000,000（`docker inspect`）。`docker compose config app` も 2g・4。`.env` の2項目の値は既定と同じ（`grep -c` で一致だけを確かめた） | 満たす |
| E3 | 最大ヒープ | `MaxRAMPercentage=50.0`、ヒープ約 1 GiB（role の 1節の Compute model） | `Dockerfile` の ENTRYPOINT に `-XX:MaxRAMPercentage=50.0`。`.env` に `MASTERSMITH_JAVA_OPTIONS` が無い（0 件）ため上書きされない。2g の 50% で 1,024 MB | 満たす |
| E4 | ヒープが尽きたときの動き | `-XX:+ExitOnOutOfMemoryError`、`restart: "no"` のまま（Build and Test の Q2: A、`construction/build-and-test/fix-notes.md`、`cd-config.md` 6節） | `Dockerfile` の ENTRYPOINT にある（`git diff 273c021 HEAD`）。`compose.yaml` は `restart: "no"`、今のコンテナの RestartPolicy も `no`。動いているコンテナで効いていることは入れ替えの後に確かめる | 予定どおり（確かめるのは Deployment Execution の順 6） |
| E5 | 動いている版 | `mastersmith:local`（`fefeafbd299b`、healthy）。前の Intent で配備した `273c021` の版（`cd-config.md` 1節） | イメージ ID `fefeafbd299b…`、healthy、再起動 0 回、OOMKilled なし（`docker inspect`） | 満たす |
| E6 | 実行の形 | 既存のアプリのコンテナ1つ・単一のインスタンス。新しいコンテナ・profile を足さず、compose・`docker/perf/compose.yaml` を変えない（全単位の 1節の Compute model・Instances・IaC approach） | `compose.yaml`・`docker/`・`docker/perf/compose.yaml` は `273c021` から差が無い。`docker compose ... config --quiet`（profile 4つ）と `docker/perf/compose.yaml` の `config --quiet` が通る | 満たす |
| E7 | 番号の公開 | 外から届くのはアプリの 8080 だけ、同じオリジン、CORS なし（全単位の 1節の Networking） | `PortBindings` は `127.0.0.1:8080`（app）・`127.0.0.1:8025`・`127.0.0.1:1025`（Mailpit）だけ。PC で待ち受けているのもこの3つ（`lsof`）。見本の対象DB は番号を開かない | 満たす |
| E8 | 内部DB の置き場 | 既存の名前付きボリューム、表と列が増えるだけ（group・role の 1節の Storage） | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 満たす |
| E9 | スキーマ | V10（U3）・V11・V12（U4）を前進のみで足す。表と空を許す列を足すだけで、`users.admin_flag` を残す（group の 1節・3節、role の 1節・3節、`cd-config.md` 1節） | `backend/src/main/resources/db/migration/` に `V10__u3_group.sql`・`V11__u4_role.sql`・`V12__u4_role_assignment.sql` が加わった（`git diff --stat 273c021 HEAD`）。今の内部DB は V9 | 予定どおり（当てるのは Deployment Execution の順 6。H2 は DDL を巻き戻せないため、入れ替えの前の複写を省かない） |
| E10 | 設定の項目 | `.env.example` を変えない。新しい環境変数・秘密は無い（全単位の 1節の Configuration、dsl-v2 の `cicd-pipeline.md` 9節） | `.env.example`・`.env.targetdb.example` は差が無い。`.env` は 16 項目で前の記録と同じ名前、最後の更新は 2026-09-30 23:48。`.env.example` にあって `.env` に無いのは `MASTERSMITH_DB_PASSWORD` だけ（前からの決定） | 満たす |
| E11 | `application.yaml` | role の 1節の Configuration の2行（`server.tomcat.max-swallow-size`、`StatementCreatorUtils: OFF`）だけを足す | `git diff 273c021 HEAD -- backend/src/main/resources/application.yaml` は、`max-swallow-size: 11MB` と `org.springframework.jdbc.core.StatementCreatorUtils: OFF`（と説明のコメント）だけ。イメージの中に入り、`.env` では変えない | 満たす |
| E12 | 同梱する資源 | `navigation/allowed-icons.txt` を WAR に入れる（navigation の 1節の Bundled resource） | `backend/src/main/resources/navigation/allowed-icons.txt` がある。WAR への同梱は Build and Test の verify・E2E で起動できたことで裏付ける | 満たす |
| E13 | 接続プール | 変えない（上限の既定 30・借りる待ち 5000 ms。group・role の 2節） | `.env` に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` が無く、既定の 30 が効く | 満たす |
| E14 | Flyway | 変えない（`validate-on-migrate: true`、既定の `*:future`）。前の版は V10〜V12 を無視して起動する（role の 2節・3節） | `application.yaml` の差に Flyway の設定は無い。前の版の起動は戻しの練習（`rollback-runbook.md` の 6.2 の D1）で確かめる | 予定どおり（確かめるのは Deployment Execution の順 9） |
| E15 | 公開する管理の口 | actuator は Web に health だけを出す（cross-cutting の NFR1.7） | `/actuator/health` は 200、`/actuator/metrics` は 404（`curl`。どちらもログインなしの GET で、監査に残らない） | 満たす |
| E16 | ヘルスチェック | bash の `/dev/tcp` で `/actuator/health` の 200（`compose.yaml`、変えない） | 間隔 30 秒・時間切れ 5 秒・3回・猶予 40 秒・始めの間隔 2 秒（`docker inspect` の `Healthcheck`） | 満たす |
| E17 | 戻しの練習で同時に動かせる量 | 配備したアプリを止めずに、練習のアプリ 2g と練習の対象DB 512m を並べる（`rollback-runbook.md` の 6節） | 上限の合計は 5,376 MiB（配備のアプリ 2,048・Mailpit 256・見本の対象DB 512・練習のアプリ 2,048・練習の対象DB 512）で、VM の 5,910 MiB に入る。コンテナの外の使用約 340 MiB を足して約 5,716 MiB | **条件つきで満たす**: 練習の間は `lgtm`（1,536 MiB）・ほかの対象DB・k6 を起動しない（`environment-inventory.md` の 9節） |
| E18 | 練習の環境の作り | `docker/perf/compose.yaml` を `-p mastersmith-rollback` で使い、練習の対象DB の環境ファイルに `POSTGRES_PASSWORD` と `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` の2行を置く（`rollback-runbook.md` の 6.1） | `docker/perf/compose.yaml` の `targetdb-postgres` は `MASTERSMITH_PERF_TARGETDB_ENV_FILE` を `env_file` で読み、`docker/targetdb/postgres/02-reader-account.sh` は `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` を読む。練習の名前のボリュームは残っていない。18080 は空き | 満たす |
| E19 | 配備に使う WAR | Build and Test で確かめた `005730a` の成果物（Q1: A） | `backend/build/libs/mastersmith.war` の `RoleTransferLimits` の定数が 5,242,880・500,000。SHA-256 の先頭は `517c847b47647338` | 満たす（Deployment Execution の入れ替えの前に確かめ直す。`environment-inventory.md` の 5節） |
| E20 | 入れ替えに使うイメージ | 土台 `eclipse-temurin:25.0.4_7-jre-noble`（`Dockerfile`） | 手元にある（前の配備・戻しの練習で使ったもの） | 満たす |
| E21 | 配備に要るディスクの空き | イメージの作り直し（約 650MB）、入れ替えの前と配備の後の複写、練習のボリューム | VM の中の空き 69G、PC の空き 40GiB | 満たす |
| E22 | Mailpit と見本の対象DB | 入れ替えない（`cd-config.md` 1節） | Mailpit は healthy・上限 256MiB・ダイジェスト固定。見本の対象DB は Up・上限 512MiB・ダイジェスト固定 | 満たす |

**満たさない値: 無し。**

## 2. セキュリティの確かめ（devsecops の観点）

| # | 確かめ | 確かめ方 | 結果 |
|---|---|---|---|
| S1 | PC に開く番号は `127.0.0.1` だけ。見本の対象DB は開かない | `docker inspect` の `PortBindings`、`lsof -iTCP -sTCP:LISTEN` | 満たす（8080・8025・1025 だけ。3000・18080・18081・5432 は待ち受けなし） |
| S2 | 秘密を含む設定のファイルは Git の管理外で、権限 600 | `ls -l`、`git check-ignore -v`、`git ls-files` | 満たす（`.env`・`.env.targetdb` とも 600、`.gitignore` の 90 行目 `.env`・91 行目 `.env.*`。Git が持つ `.env` の名前のファイルは値を空にした見本の `.env.example`・`.env.targetdb.example` の2つだけ） |
| S3 | 秘密がイメージに焼き込まれていない | `docker image inspect` の `Config.Env` の名前 | 満たす（`PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL` だけ）。新しいイメージも `Dockerfile` の差は JVM の引数の1つだけで、秘密を足さない |
| S4 | 見本の対象DB の管理者のパスワードがアプリのコンテナに渡っていない | アプリのコンテナの環境変数の名前 | 満たす（`MASTERSMITH_SAMPLE_*` は 0 件） |
| S5 | アプリのコンテナは root で動かず、特権を持たない | `docker inspect` の `Config.User`・`Privileged` | 満たす（`10001:10001`、Privileged false） |
| S6 | 配備したアプリで指標の口を開いていない | `curl /actuator/metrics` | 満たす（404） |
| S7 | この Intent の新しい設定が秘密を出さない | `application.yaml` の差 | 満たす。足したのは読み捨ての上限と、JDBC の引数の値を TRACE に出すログを止める設定で、秘密を出す側の変更ではない（`StatementCreatorUtils: OFF` は権限の YAML の中身をログに出さないための守り。role の NFR1.9） |
| S8 | 秘密の値を画面・記録に出さない | この段の出力 | 満たす（`.env` は開かず、項目の名前ごとの件数と、既定との一致の件数だけを数えた。`docker compose config` は `--quiet` と上限の項目だけに絞った） |

- 起動のログに秘密と初期管理者のメールアドレスが出ていないことの確かめ（前の記録の S4）は、この段では行っていません。`.env` の値を読む必要があり、今のログは前の版のものだからです。新しい版の起動のログでの確かめは、Deployment Execution のスモークテストの起動のログの確かめ（`deployment-strategy.md` の S1）で、値を表示せずに件数だけで行います。
- 戻しの練習では、練習の対象DB のパスワードを乱数で作り、値を表示せずにホームの下（権限 700）に置き、練習の後に消します（`rollback-runbook.md` の 6.1・6.3）。`mktemp -d` は使いません（colima の VM から見えない）。
- 要求の回数の制限が無いことは、PC の `127.0.0.1` に限って公開する今の形で受け入れたままです（前の記録と同じ）。

## 3. 個人に関する値の扱い（コンプライアンスの観点）

- この Intent の配備で内部DB に加わるのは、グループ・ロール・権限の設定・割り当て・作業ロールの表と、監査の3列（`target_role_id`・`target_group_id`・`detail`）です。`detail` には決めた型からだけ値を作り、パスワード・トークン・ハッシュ値・メールアドレス・氏名を入れません（group の NFR1.7）。新しい種類の個人に関する値は加わりません。
- 入れ替えの前と配備の後の2つの複写（`~/.mastersmith-backup/`、権限 700）は、利用者のメールアドレス・氏名・パスワードのハッシュ値・監査の記録を含みます。複写のファイルの権限は 600 で作ります（前の Intent までのファイルには 644 のものがある。6節の N2）。
- 戻しの練習の環境は配備の後の複写から作るため、練習のボリュームにも同じ値が入ります。練習の後に `down -v` で消します（`rollback-runbook.md` の 6.3）。
- 外部の法令の枠組み（GDPR など）に当たる扱いは、配備先が開発者の PC の間は対象外です。配備先が決まったときに見直します（前の記録と同じ）。

## 4. 承認済みの記録との差（`cd-config.md` の順 6）

| 項目 | 承認済みの記録 | 実際 | 扱い |
|---|---|---|---|
| WAR の作り方 | `cd-config.md` の 3節の順 6:「`docker compose --profile targetdb-postgres up -d --build app` で WAR とイメージを作り直し」 | `Dockerfile` は `backend/build/libs/mastersmith.war` を写すだけで、`--build` は WAR を作り直さない（README の「コンテナでの起動と確認」は `./gradlew verify` で作った WAR を使う手順） | 依頼者の決定（Q1: A）で、今の WAR（Build and Test で確かめた `005730a` の成果物）をそのまま使う。`cd-config.md` は書き換えず（`project.md` の Way of Working の学び）、この差をここに記録する。Deployment Execution の入れ替えの前に、WAR の SHA-256 と `RoleTransferLimits` の2つの定数を確かめ直す（`environment-inventory.md` の 5節） |

- この差で、配備する中身は変わりません。`--build` で作られるイメージの中身は、今の WAR そのものです。

## 5. 片付け（Q2: A）

| 項目 | 扱い |
|---|---|
| 対象 | 名前の無いイメージ（`docker image prune -f`。`-a` なし）とビルドのキャッシュ（`docker builder prune -f`）だけ。タグ付きのイメージ（`mastersmith:*` の 17 個、戻し先の `pre-*` を含む）とボリュームは消さない。`docker volume prune`・`docker system prune` は使わない |
| 行う時点 | **この段では行わない**。Deployment Execution の終わり（スモークテスト・戻しの練習・練習の環境の片付けが済み、記録を書いた後）に行う |
| その時点にする理由 | 今は名前の無いイメージが 0 個で、片付けて減るのはビルドのキャッシュだけ。入れ替えの `--build` でキャッシュはまた作られ、名前の無いイメージは入れ替えの後に出うる（`pre-role-menu` のタグを付けずに作り直した場合など）。練習で前の版のイメージを使うため、練習が済む前にイメージの一覧に触れない |
| 記録 | 片付けの前後の `docker system df` の量（イメージ・ビルドのキャッシュ・ボリューム）と、タグ付きのイメージの一覧が前後で変わらないことを、Deployment Execution の記録に書く。前の Intent（260930-user-admin）のときと同じく、containerd の置き場ではタグの外れた古い参照が名前の無いイメージとして出うるため、前後でタグ付きのイメージの一覧を比べる |
| 今の量（参考） | イメージ 30 個・9.729GB（回収できる 4.394GB）、名前の無いイメージ 0 個、ビルドのキャッシュ 2.126GB、ボリューム 64 個・10.12GB |

## 6. 気づき（食い違いではない）

| # | 気づき | 扱い |
|---|---|---|
| N1 | 戻しの練習の間の VM のメモリの余裕は、上限の合計で約 0.2〜0.5 GiB と小さい | `lgtm`・ほかの対象DB・k6 を起動しない条件を練習の前に確かめる（E17）。練習のアプリの実際の使用を `docker stats` で記録する |
| N2 | `~/.mastersmith-backup/` の前の Intent までの複写 9 件のうち 7 件が権限 644（置き場のディレクトリは 700 のため、ほかの利用者からは読めない） | 依頼者の答え（A）で、前の複写の7件を 600 に直した（中身は変えていない。直した後に 600 でないファイルは 0 件。2026-10-14 に指揮役が chmod で直した）。この Intent で作る2件も 600 で作る。保存の期間・消し方は、配備先が決まったときか依頼者の判断で見直す |
| N3 | `docker system df` と `docker builder du` で、回収できるビルドのキャッシュの量が違う（889.7MB と 2.126GB） | 数え方の違い。片付けの前後の記録には両方を書く |

## 7. 次の Deployment Execution の段への申し送り

| こと | 内容 |
|---|---|
| WAR | 今の `backend/build/libs/mastersmith.war` をそのまま使う（Q1: A）。入れ替えの前に SHA-256（先頭 `517c847b47647338`）と `RoleTransferLimits` の定数（5,242,880・500,000）を確かめ直し、全体の SHA-256 を記録する。この段の後に `./gradlew` を流さない |
| 戻し先のタグ | `mastersmith:pre-role-menu` は**まだ付けていない**。入れ替えの前に付け、ID が `fefeafbd299b` で始まることを確かめる（`cd-config.md` の順 2） |
| スキーマ | 今は V9。入れ替えの起動で V10〜V12 が当たることを起動のログで確かめる。入れ替えの前の複写を省かない（`cd-config.md` の 4節） |
| `ExitOnOutOfMemoryError` | 入れ替えの後に `/proc/1/cmdline` の件数で確かめる（`cd-config.md` の 6節） |
| `.env` | 変えない |
| 戻しの練習 | 練習の前に `mastersmith-lgtm-1` が Exited であることを確かめ、`lgtm`・ほかの対象DB・k6 を起動しない。練習のアプリの使用を記録する |
| 片付け | Deployment Execution の終わりに、名前の無いイメージとビルドのキャッシュだけを片付け、前後の量を記録する（5節） |

## Sources

- 読み取りの結果（2026-10-14）: `colima list`、`colima ssh -- free -m`・`df -h`・`/proc/meminfo`、`docker version`、`docker ps -a`、`docker stats --no-stream`、`docker inspect`（`PortBindings`・`Config.User`・`Privileged`・`Healthcheck`・`Config.Env` の名前・`Memory`・`NanoCpus`・`RestartPolicy`）、`docker image inspect mastersmith:local`、`docker images`（`dangling=true` を含む）、`docker volume ls`、`docker system df`（`-v` を含む）、`docker builder du`、`docker compose config --quiet`（profile 4つと `docker/perf/compose.yaml`）、`curl`（`/actuator/health`・`/actuator/metrics`）、`lsof`、`df -h`、`ls -l`、`git check-ignore -v`、`git ls-files`、`grep -c`（`.env` の項目の名前と既定との一致）、`shasum -a 256` と `javap -constants`（WAR）、`git diff 273c021 HEAD`（`Dockerfile`・`application.yaml`・`db/migration`・`compose.yaml`・`docker/`・`.env.example`）
- `environment-provisioning-questions.md`（Q1: A、Q2: A、まとめの確認 Looks correct）・`environment-inventory.md`
- `operation/deployment-pipeline/cd-config.md`（1節・3節・4節・6節）・`deployment-strategy.md`（S1・S11）・`rollback-runbook.md`（6節・7節）
- `construction/group/infrastructure-design/infrastructure-specification.md`（1節〜4節）、`construction/role/infrastructure-design/infrastructure-specification.md`（1節〜4節）、`construction/navigation/infrastructure-design/infrastructure-specification.md`（1節・2節・4節）、`construction/role-admin-ui/infrastructure-design/infrastructure-specification.md`（1節・4節）、`construction/app-frame-ui/infrastructure-design/infrastructure-specification.md`（1節・4節）、`construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（8節・9節）
- `construction/cross-cutting/nfr-requirements/security-requirements.md`（NFR1.7）、`construction/group/nfr-requirements/security-requirements.md`（NFR1.7）、`construction/role/nfr-requirements/security-requirements.md`（NFR1.7〜NFR1.11）
- `construction/build-and-test/fix-notes.md`、Build and Test の `bt-q1-limits.md`（scratchpad）
- 前の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/validation-report.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Forbidden・Mandated・Corrections）
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`、`docker/targetdb/postgres/02-reader-account.sh`、`.gitignore`、`.env.example`、README（コンテナでの起動と確認）

## Assumptions & Open Questions

- [assumption] 前の版（`273c021`）が V10〜V12 のある内部DB で起動することは、設計（role の 3節）と Flyway の既定から読んだもので、この段では確かめていない。Deployment Execution の戻しの練習（`rollback-runbook.md` の 6.2 の D1）で確かめる。
- 新しい版の起動のログに秘密とメールアドレスが出ないことは、この段では確かめていない（2節）。Deployment Execution のスモークテストで、値を表示せずに件数だけで確かめる。
- 前の複写の権限（N2）は依頼者の答えで 600 に直した。保存の期間・消し方の見直しは、依頼者の判断で後で行う。片付け（Q2 A）は依頼者の答えで Deployment Execution の終わりに行う。
