# 環境の確かめの結果（validation-report）

Intent 261016-business-data-views の配備先の環境（開発者の PC 上のコンテナ）を、承認済みの設計の値と1つずつ突き合わせた結果です。

- 確かめは 2026-10-21 に読み取りだけで行いました。
- 次のことは、次の Deployment Execution の段で行います（`operation/deployment-pipeline/cd-config.md` の 3節）。
  - WAR の作り直し
  - 戻し先のタグ付け
  - `.env`・`.env.targetdb` の書き足し
  - 見本の PG の作り直しと書き込みのアカウントの追加
  - アプリの入れ替え
  - スモークテストと戻しの練習
- 書き方は、前の Intent の同じ段の記録（`aidlc/spaces/default/intents/261004-role-menu/operation/environment-provisioning/validation-report.md`）を正としました。

**判定: 合格**

- 設計の値を満たせない点は無し。
- 条件つきの点が 1 件（E19 の lgtm）。
- 申し送りが 1 件（E20 の WAR の作り直し）。
- 気づきが 3 件（6節）。

この段の役割（基盤）に、セキュリティ（devsecops）とコンプライアンスの観点を合わせて確かめました。

## 1. 設計の値との突き合わせ

| # | 項目 | 設計の値（出典） | 実際の値（確かめ方） | 判定 |
|---|---|---|---|---|
| E1 | VM の資源 | CPU 4・メモリ 6GiB（`cd-config.md` 1節） | CPU 4・メモリ 6GiB・ディスク 100GiB、Running（`colima list`）。VM の中の全体 5,910 MiB | 満たす |
| E2 | アプリの資源の上限 | メモリ 2g・CPU 4、新しいコンテナは足さない（U1・U2 の `infrastructure-specification.md` 1節の Compute model、`cd-config.md` 1節） | Memory 2,147,483,648 B・NanoCpus 4,000,000,000（`docker inspect`）。`docker compose config` も 2g・4 | 満たす |
| E3 | 最大ヒープとヒープが尽きたときの動き | `MaxRAMPercentage` 50、`ExitOnOutOfMemoryError`、`restart: "no"`（`cd-config.md` 1節） | 今のイメージの Entrypoint に両方ある。`Dockerfile` も同じ。`.env` に `MASTERSMITH_JAVA_OPTIONS` が無い（0 件）。RestartPolicy は `no` | 満たす |
| E4 | 動いている版 | `mastersmith:local`（`f103aee1702a`、healthy）。前の Intent で配備した `6dd9bb0`（`cd-config.md` 1節） | イメージ ID は `f103aee1702a…`。healthy、再起動 0 回、OOMKilled なし（`docker inspect`） | 満たす |
| E5 | 番号の公開 | 外から届くのはアプリの 8080 だけ。見本の PG は PC に番号を開けない（U1 の 1節の Networking、`cd-config.md` 1節） | `PortBindings` は `127.0.0.1:8080`（app）と Mailpit の `127.0.0.1:8025`・`1025` だけ。見本の PG の ports は空。PC で待ち受けているのもこの3つ（`lsof`） | 満たす |
| E6 | 見本の PG の資源 | `mem_limit` 512m は変えない（U3 の 1節の Sizing） | 536,870,912 B（`docker inspect`）。今の使用は 26 MiB | 満たす |
| E7 | 内部DB の置き場とスキーマ | 既存の名前付きボリュームに、V13（空を許す3列）を前進のみで足す（U2 の 1節の Storage、`cd-config.md` 1節） | `mastersmith_mastersmith-data` → `/app/data`。今は V12。`V13__u2_business_data_audit_columns.sql` がリポジトリと今の WAR にある | 予定どおり。当てるのは Deployment Execution の順 8。H2 は DDL を巻き戻せないため、入れ替えの前の複写を省かない |
| E8 | `.env` に足す項目 | `MASTERSMITH_TARGET_DB_WRITE_USERNAME`・`_PASSWORD` の2つだけ。時間の上限とプールの上限は既定（U2 の 1節の Configuration、`cd-config.md` 1節） | 今は `MASTERSMITH_TARGET_DB_WRITE_*` が 0 件（名前だけを数えた）。`.env` は 16 項目で、前の記録と同じ名前。`.env.example` にあって `.env` に無いのは `MASTERSMITH_DB_PASSWORD` だけ（前からの決定） | 満たす（足す前提がそろっている。足すのは順 6） |
| E9 | `.env.targetdb` に足す項目 | `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`（U2 の 1節の Secrets） | 今は 0 件。`.env.targetdb.example` にあって無いのはこの1つだけ | 満たす（足すのは順 6） |
| E10 | 書き込みのアカウント | `mastersmith_writer` に、DSL の対象のスキーマの行の操作だけを与え、DDL の権限を与えない（`project.md` の Mandated、U2 の 1節・3節） | 見本の PG に `mastersmith_writer` は 0 件。台本 `03-writer-account.sh` は `sales` の `SELECT`・`INSERT`・`UPDATE`・`DELETE` と順序の `USAGE` だけを与え、`CREATE` を与えない | 満たす（作るのは順 7。期待は `1|t|t|t|f|f|f`） |
| E11 | PUBLIC の `TEMPORARY` | 取り上げる（U2 の基盤の設計、B6） | `has_database_privilege('public','business','TEMP')` が `f` | 満たす |
| E12 | 見本の PG に書き込みのパスワードを渡す仕組み | `.env.targetdb` を `env_file`（`required: false`）で読み、コンテナだけを作り直す（`cd-config.md` 3.2 の順 7） | `compose.yaml` の `targetdb-postgres` は `.env.targetdb` を読む。今のコンテナの環境の項目は4つで、書き込みのパスワードはまだ無い。ローカルのソケットは `trust` で、`exec` で台本を流せる。ボリュームは初期化済みで、台本は自動では動かない | 満たす（順 7 の `--force-recreate --no-deps` で読み直す） |
| E13 | 見本の PG の状態 | E2E の残り（`e2e_biz_*`・`mastersmith_e2e_*`）が無い（U3 の 1節） | `pg_%` 以外のロールは 2 つ、`sales`・`public` 以外の利用者のスキーマは 0 | 満たす |
| E14 | 実行の形 | 既存のアプリのコンテナ1つ・単一のインスタンス。`Dockerfile`・`compose.yaml` は変えない（U1・U2 の 1節、`cd-config.md` 1節） | `docker compose --profile targetdb-postgres config` が通る。`app` の設定は今のコンテナと一致する | 満たす |
| E15 | ヘルスチェック | bash の `/dev/tcp` で `/actuator/health` の 200（`compose.yaml`、変えない） | `compose.yaml` の `app` の healthcheck にある。`/actuator/health` は 200（`curl`。ログインなしの GET で、監査に残らない） | 満たす |
| E16 | 戻し先のイメージ | 入れ替えの前に `mastersmith:pre-business-data-views`（`f103aee1702a`）を付ける（`cd-config.md` の順 3） | タグはまだ無い。`f103aee1702a` は手元にある | 予定どおり（付けるのは順 3） |
| E17 | 戻し先のイメージの健全性の確かめ方 | `running` を健全と読まない（`project.md` の学び、`rollback-runbook.md` 2節・6.1 の 4） | `f103aee1702a` に HEALTHCHECK は無い。戻し（`compose.yaml`）も練習（`docker/perf/compose.yaml`）も compose のヘルスチェック（`/dev/tcp` で 200）で healthy を待つ | 満たす |
| E18 | 練習の環境の作り | `docker/perf/compose.yaml` を `-p mastersmith-rollback`・18080 で使う。一時の環境ファイルは3つ（`rollback-runbook.md` 6.1） | 3つの一時の環境ファイルの形と合う。<br>・`app`: `MASTERSMITH_IMAGE_TAG` でイメージを選ぶ。`MASTERSMITH_PERF_ENV_FILE` の後に `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE` を読む（後の値が効く）<br>・`targetdb-postgres`: `POSTGRES_PASSWORD` を直接読み、同じ初期化の台本（01〜03）を使う<br>18080 は空き。練習の名前のボリュームは残っていない | 満たす |
| E19 | 戻しの練習で同時に動かせる量 | 配備したアプリを止めずに、練習のアプリ 2g と練習の対象DB 512m を並べる（`rollback-runbook.md` の 6節） | 上限の合計は 5,376 MiB（配備のアプリ 2,048・Mailpit 256・見本の PG 512・練習のアプリ 2,048・練習の PG 512）で、VM の 5,910 MiB に入る。コンテナの外の使用約 430 MiB を足すと約 5,806 MiB で、余裕は約 100 MiB | **条件つきで満たす**: 練習の間は `lgtm`（1,536 MiB）・ほかの対象DB・k6 を起動しない（`environment-inventory.md` の 10節） |
| E20 | 配備に使う WAR | 停止の前に `./gradlew :backend:bootWar --rerun-tasks` で作り直し、SHA-256 と中身を確かめる（`cd-config.md` の順 2、`project.md` の学び） | `Dockerfile` は `backend/build/libs/mastersmith.war` を写すだけ。今の WAR（SHA-256 `6426111d…`）は 2026-10-20 22:56:37 作成で、B7 の `d220447`（23:31:57）より前のため、配備する版と同じか確かめられない | 予定どおり（今の WAR は使わない。作り直すのは順 2） |
| E21 | アプリのソースの差 | `git diff --stat d220447 HEAD -- . ':!aidlc'` が `22de65f` の4ファイルだけ（`cd-config.md` の順 1） | README・`AbstractBusinessDataReadIT.java`・`frontend/vitest.config.ts`・`perf/README.md` の4ファイルだけ。未コミットは `aidlc/` の下だけ | 満たす（順 1 で確かめ直す） |
| E22 | 配備・確かめに使うイメージ | 土台と複写の道具 `eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23…`（`Dockerfile`、`cd-config.md` の 4節） | 手元にある（`d9a39a236346`） | 満たす |
| E23 | 配備に要るディスクの空き | WAR とイメージの作り直し、入れ替えの前と配備の後の複写、練習のボリューム | VM の中の空き 69G、PC の空き 35GiB | 満たす |
| E24 | Mailpit | 入れ替えない（`cd-config.md` 1節） | healthy・上限 256MiB・ダイジェスト固定（v1.31.5） | 満たす |

## 2. セキュリティの確かめ（devsecops の観点）

| # | 確かめ | 確かめ方 | 結果 |
|---|---|---|---|
| S1 | PC に開く番号は `127.0.0.1` だけで、見本の PG は開かない | `docker inspect` の `PortBindings`、`lsof -iTCP -sTCP:LISTEN` | 満たす。待ち受けは 8080・8025・1025 だけ。3000・18080・5432・15432 は待ち受けなし |
| S2 | 秘密を含む設定のファイルは Git の管理外で、権限 600 | `ls -l`、`git check-ignore -v`、`git ls-files` | 満たす。`.env`・`.env.targetdb` とも 600 で、`.gitignore` の 90 行目 `.env`・91 行目 `.env.*` に当たる。Git が持つ `.env` の名前のファイルは、値を空にした見本の `.env.example`・`.env.targetdb.example` の2つだけ |
| S3 | 秘密がイメージに焼き込まれていない | `docker image inspect` の `Config.Env` の名前 | 満たす。`PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL` だけ。新しいイメージも `Dockerfile` が変わらず、秘密を足さない |
| S4 | 見本の PG の管理者のパスワードがアプリのコンテナに渡っていない | アプリのコンテナの環境変数の名前 | 満たす。`MASTERSMITH_SAMPLE_*` は 0 件。書き込みのパスワードは、アプリには `.env` の `MASTERSMITH_TARGET_DB_WRITE_PASSWORD` として、見本の PG には `.env.targetdb` として別々に渡す（U2 の 1節の Secrets） |
| S5 | アプリのコンテナは root で動かず、特権を持たない | `docker inspect` の `Config.User`・`Privileged` | 満たす。`10001:10001`、Privileged false |
| S6 | 配備したアプリで指標の口を開いていない | `curl /actuator/metrics` | 満たす。404 |
| S7 | 書き込みのアカウントの権限が最小 | 台本 `03-writer-account.sh` を読む | 満たす。`sales` の行の操作と順序の `USAGE` だけを与え、スキーマ・データベースの `CREATE` を与えない。`TEMPORARY` を PUBLIC から取り上げる（`project.md` の Mandated）。入れ替えの前に順 7 の SQL で件数と真偽値で確かめる |
| S8 | 秘密の値を画面・記録に出さない | この段の出力 | 満たす。`.env`・`.env.targetdb` は開かず、項目の名前ごとの件数だけを数えた。`docker compose config` は項目の名前と上限だけに絞った。見本の PG は件数と真偽値だけを読んだ |

## 3. 個人に関する値の扱い（コンプライアンスの観点）

- 内部DB の複写と見本の PG の中身は開いていません。この段の確かめに、利用者のメールアドレス・氏名・業務データの値は使っていません。
- 入れ替えの前と配備の後の複写は、リポジトリの外の `~/.mastersmith-backup/`（権限 700）に置き、コミットしません（`team.md` の Deployment）。前の Intent の2件の権限は N2 のとおりです。
- スモークテストで作る業務データの行は架空の値にし、終わったら消します（`deployment-strategy.md` の Q4: A）。

## 4. 承認済みの記録との差

ありません。`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md` の書き方と、実際の環境は合っています。

- WAR の作り直し（順 2）は、`Dockerfile` が手元の WAR を写すだけであることを前提に書かれていて、実際と合います。
- 戻しの健全性を compose で待つ書き方も、イメージに HEALTHCHECK が無いことと合います。

## 5. 片付け（Q1: A）

| 項目 | 扱い |
|---|---|
| 対象 | 使われていないビルドのキャッシュだけ（`docker builder prune -f`）。タグ付きのイメージとボリュームは消さない。`docker image prune`・`docker volume prune`・`docker system prune` は使わない。名前の無いイメージは 0 個で、`docker image prune` では何も消えないため行わない |
| 行った時点 | この段（2026-10-20 23:37 UTC） |
| 前の量 | ・イメージ 24 個・9.851GB（回収できる 4.573GB）<br>・ボリューム 64 個・10.15GB（回収できる 9.262GB）<br>・ビルドのキャッシュ 24 個・2.059GB（`docker system df` で回収できる 700.8MB、`docker builder du` の Reclaimable 2.059GB） |
| 後の量 | ・イメージ 24 個・9.851GB（変わらない）<br>・ボリューム 64 個・10.15GB（変わらない）<br>・ビルドのキャッシュ 18 個・1.358GB（`docker system df` で回収できる 0B、`docker builder du` の Reclaimable 1.358GB） |
| 減った量 | ビルドのキャッシュ 700.8MB（6 個） |
| タグ付きのイメージとボリューム | 前後の一覧を比べて差が無い（`docker images` のタグと ID、`docker volume ls`） |
| コンテナ | 止めていない。片付けの後も app・Mailpit は healthy、見本の PG は Up |

## 6. 気づき（食い違いではない）

| # | 気づき | 扱い |
|---|---|---|
| N1 | 戻しの練習の間の VM のメモリの余裕は、上限の合計で約 100 MiB と小さい（前の記録の約 0.2〜0.5 GiB より小さい。コンテナの外の使用が約 340 MiB から約 430 MiB に増えた） | 練習の前に `mastersmith-lgtm-1` が Exited であることを確かめ、`lgtm`・ほかの対象DB・k6 を起動しない（E19）。練習の直前と最中に `docker stats --no-stream` で実際の使用を記録し、足りなければ依頼者に諮る |
| N2 | `~/.mastersmith-backup/` の前の Intent の複写2件（`…-pre-deps-ui-carryover.tgz`・`…-after-deps-ui-carryover.tgz`）が権限 644。置き場のディレクトリは 700 のため、ほかの利用者からは読めない。ほかの 15 件は 600 | 前の Intent（role-menu）では依頼者の答えで 600 に直した。この段では直していない（中身にも権限にも触れていない）。直すかは依頼者に諮る。この Intent で作る2件は 600 で作る |
| N3 | PC のディスクの空きが 35GiB（使用 92%）で、前の Intent より 5GiB 減った | 配備には足りる（E23）。タグ付きのイメージの片付けは Q1: A で行わないため、増え続けるときは後の Intent で扱いを決める |

## 7. 次の Deployment Execution の段への申し送り

| こと | 内容 |
|---|---|
| WAR | 今の `backend/build/libs/mastersmith.war`（`6426111d…`）は使わない。順 2 で `./gradlew :backend:bootWar --rerun-tasks` で作り直す。全体の SHA-256 と、中身（`businessdata` の web・service・repository のクラス、V13、画面のビルド）を記録する。PC がバッテリー駆動のため、`caffeinate` で包む（`project.md` の学び）。順 2 の後は順 8 まで `./gradlew` を流さない |
| 戻し先のタグ | `mastersmith:pre-business-data-views` は**まだ付けていない**。順 3 で付け、ID が `f103aee1702a` で始まることを確かめる |
| `.env`・`.env.targetdb` | 書き込みの3項目は今 0 件。順 6 で、`cd-config.md` 3.2 のコマンドを依頼者に見せて承認を得てから足す。その前に `.env` を `$HOME/.mastersmith-env-before-writer`（600）へ複写する |
| 見本の PG | 順 7 でコンテナだけを作り直して書き込みのパスワードを渡し（ボリュームは残す）、台本を流して `1|t|t|t|f|f|f` を確かめる |
| スキーマ | 今は V12。入れ替えの起動で V13 が1件当たることを、起動のログで確かめる。入れ替えの前の複写を省かない（`cd-config.md` の 4節） |
| 戻しの練習 | 練習の前に `mastersmith-lgtm-1` が Exited であることを確かめ、`lgtm`・ほかの対象DB・k6 を起動しない。練習のアプリと VM の使用を記録する（N1） |
| 複写の権限 | 新しい2件は 600 で作る。前の Intent の2件（644）の扱いは依頼者に諮る（N2） |

## Sources

- 読み取りの結果（2026-10-21）と片付けの前後の量: `environment-inventory.md` の Sources と同じコマンド。加えて `docker builder prune -f` と、その前後の `docker system df`・`docker builder du`・`docker images`・`docker volume ls`
- `environment-provisioning-questions.md`（この PC の実行環境、決まっていること、Q1: A、まとめの確認）
- `operation/deployment-pipeline/cd-config.md`（1節・3節・3.2・4節）・`deployment-strategy.md`（S13・Q4: A）・`rollback-runbook.md`（2節・3.1・6節）・`deployment-pipeline-questions.md`
- `construction/u1-businessdata-read/infrastructure-design/infrastructure-specification.md`（1節）
- `construction/u2-businessdata-write/infrastructure-design/infrastructure-specification.md`（1節〜3節）
- `construction/u3-businessdata-screens/infrastructure-design/infrastructure-specification.md`（1節・2節）
- 前の記録: `aidlc/spaces/default/intents/261004-role-menu/operation/environment-provisioning/validation-report.md`
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`、`docker/targetdb/postgres/03-writer-account.sh`、`.env.example`、`.env.targetdb.example`、`.gitignore`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections・Mandated の学び）

## Assumptions & Open Questions

- [assumption] 順 7 の作り直しの後も、見本の PG のローカルのソケットは `trust` のままで、台本を `exec` で流せる（ボリュームの `pg_hba.conf` は変わらない）。
- [assumption] 練習のアプリの実際のメモリの使用は上限よりかなり小さく、VM に収まる（N1）。
- N2 の前の複写2件の権限を直すかは、依頼者に諮る。
