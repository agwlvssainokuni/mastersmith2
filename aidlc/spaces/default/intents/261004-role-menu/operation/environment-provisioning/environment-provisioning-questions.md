# Environment Provisioning の質問（261004-role-menu）

配備先は、これまでどおり開発者の PC 上のコンテナです。この段は、Docker の実行環境（colima）・イメージ・ボリューム・`.env`・`compose.yaml` の設定と読み替え、設計の値を1つずつ確かめます（`project.md` の Deployment の学び）。クラウドの基盤は作りません。前の Intent の同じ段の記録（直近は `aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/`。`261003-user-admin-followup`・`261004-safety-carryover` にはこの段が無い）を正とし、今回の差だけを扱います。アプリの入れ替えは次の Deployment Execution の段で行います。

## 調べたこと（読み取りだけ、2026-10-14）

値（秘密・個人に関する値）は表示していません。`.env` は開かず、項目の名前と件数だけを数えました。

| 対象 | 結果 |
|---|---|
| colima の VM | CPU 4・メモリ 6GiB・ディスク 100GiB（`aarch64`、runtime docker）。VM の中のメモリは全体 5,910 MiB・使用 988 MiB・使える量 4,921 MiB、ディスクは 96G 中 27G 使用（空き 69G） |
| PC のディスク | 空き 40GiB（使用 91%。前の Intent の 28GiB より増えた） |
| Docker のディスクの使用 | イメージ 30 個・9.7GB（片付けられる 4.4GB）、コンテナ 4 個、ボリューム 64 個・10.1GB（片付けられる 9.3GB）、ビルドのキャッシュ 2.1GB（片付けられる 0.9GB） |
| 動いているコンテナ | `mastersmith-app-1`（`mastersmith:local`、healthy、6 日前から）・`mastersmith-mailpit-1`（healthy）・`mastersmith-targetdb-postgres-1`。`mastersmith-lgtm-1` は止まっている（Exited） |
| 実際の使用量 | app 555MiB / 2GiB、mailpit 41MiB / 256MiB、見本の対象DB 55MiB / 512MiB |
| 配備したアプリの上限 | `docker inspect`: Memory 2,147,483,648（2g）・NanoCpus 4,000,000,000（CPU 4）・restart `no`。`docker compose config app` も `mem_limit` 2g・`cpus` 4 |
| `.env` の上限の項目 | `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` が各1行あり、値は既定と同じ（2g・4）。`MASTERSMITH_JAVA_OPTIONS` は無い（0 件）ため、新しいイメージでも `-XX:MaxRAMPercentage=50.0` と `-XX:+ExitOnOutOfMemoryError` が上書きされずに効く見込み（ヒープの上限 1,024 MB） |
| 今のイメージ | `mastersmith:local` = `fefeafbd299b`（作成 2026-10-04 12:42、前の Intent で配備した `273c021` の版）。戻し先のタグ `mastersmith:pre-role-menu` はまだ無い（Deployment Execution の順 2 で付ける） |
| 設定のファイルの差 | `git diff 273c021 HEAD` で変わったのは `Dockerfile`（`-XX:+ExitOnOutOfMemoryError`）と `backend/src/main/resources/application.yaml`（Tomcat の読み捨ての上限、ログ）だけ。`compose.yaml`・`docker/`・`.env.example`・`.env.targetdb.example` は変わっていない |
| `.env` と `.env.example` の項目の差 | `.env.example` にあって `.env` に無いのは `MASTERSMITH_DB_PASSWORD` の1つだけ（Intent `260922-auth-audit-base` の決定で、内部DB は空のパスワードで作られた。入れると開けなくなる）。`.env` にだけある項目は無い。**この Intent で新しく要る設定の項目は無い** |
| 戻しの練習に使う項目 | `.env` に `MASTERSMITH_WEB_BASE_URL`・`MASTERSMITH_TARGET_DB_PASSWORD` が各1行ある（練習では複写の末尾に同じ項目を足して上書きする。`rollback-runbook.md` 6.1 の 3）。`.env`・`.env.targetdb` の権限は `-rw-------` |
| ボリューム | `mastersmith_mastersmith-data`（内部DB）・`mastersmith_mastersmith-monitoring`・`mastersmith_mastersmith-targetdb-postgres`。練習の名前 `mastersmith-rollback_*` のボリュームは残っていない |
| 複写の置き場 | `~/.mastersmith-backup`（権限 `drwx------`、中に 10 個） |
| WAR | `backend/build/libs/mastersmith.war`（2026-10-14 00:45 作成）。中の `RoleTransferLimits` の定数が 5,242,880・500,000 で、`005730a` の直し（5 MiB・節 50 万）を含む。`Dockerfile` はこの WAR を写すだけで、`docker compose up --build` は WAR を作り直さない（Q1） |
| 練習の環境の練習の対象DB | `docker/perf/compose.yaml` の `targetdb-postgres` は `MASTERSMITH_PERF_TARGETDB_ENV_FILE` の `POSTGRES_PASSWORD` を直接読み、`docker/targetdb/postgres/02-reader-account.sh` は `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` を読む。`rollback-runbook.md` 6.1 の 2 の2行の形と合う |

## 決まっていること（質問にしない）

- **`.env`・`compose.yaml` は変えない**。新しく要る設定の項目は無い（上の表）。`MASTERSMITH_DB_PASSWORD` を入れないのは前からの決定のまま。
- **新しいイメージでも上限は変わらない**。`mem_limit` は既定の 2g（`.env` の値も 2g）、CPU 4、`MASTERSMITH_JAVA_OPTIONS` が無いため最大ヒープは 1,024 MB。Build and Test の測り（`bt-q1-limits.md`）は同じ上限で行い、権限の YAML の上限ちょうどの悪い形でヒープの山が 1,024 MB に届いた（OOM・Full GC は無し）。これは Build and Test で受け入れた制約で、`ExitOnOutOfMemoryError` で尽きたときは JVM が止まり起き直さない（`cd-config.md` 6節、`rollback-runbook.md` 7節）。入れ替えの後に `/proc/1/cmdline` の件数で効いていることを確かめるのは Deployment Execution の順 6。
- **戻しの練習で、配備したアプリを止めずに前の版と練習の対象DB を同時に動かせる**。上限の合計は、配備したアプリ 2,048 MiB・Mailpit 256 MiB・見本の対象DB 512 MiB・練習のアプリ 2,048 MiB・練習の対象DB 512 MiB の 5,376 MiB で、VM の全体 5,910 MiB に入る（今のコンテナの外の使用は約 340 MiB）。ただし余裕は約 0.5 GiB で、**手元の監視（`lgtm`、上限 1,536 MiB）は練習の間は起動しない**（`compose.yaml` のコメントの「負荷の試験の間は止める」と同じ扱い）。Build and Test の測りも、配備したアプリ・Mailpit・見本の対象DB を動かしたまま 2g の使い捨ての環境を並べて行った（`bt-q1-limits.md` の片付けの節）。
- **ディスクは足りる**。イメージの作り直し（約 650MB）、配備の前と後の複写、練習のボリュームに対して、VM の空き 69G・PC の空き 40GiB がある。
- 戻し先のタグ・内部DB の複写・DSL の移し替え・確かめは、`cd-config.md` の順 2〜9 で Deployment Execution の段が行う。この段では、上の値を `environment-inventory.md` と `validation-report.md` に記録する。

## Q1. 配備に使う WAR

`cd-config.md` の順 6 は「`docker compose ... up -d --build app` で WAR とイメージを作り直し」と書いていますが、`Dockerfile` は手元の `backend/build/libs/mastersmith.war` を写すだけで、WAR は作り直しません（README の「コンテナでの起動と確認」は `./gradlew verify` で作った WAR を使う手順）。今の WAR は 2026-10-14 00:45 に作られたもので、中のクラスに `005730a` の直し（権限の YAML の上限 5 MiB・節 50 万）が入っていることを確かめました。`005730a` の後にアプリのコードの変更はありません（README の1行だけ）。配備にどの WAR を使いますか？

A. 今の WAR をそのまま使う（Build and Test の verify・E2E で確かめた成果物と同じものを配備する。入れ替えの前に WAR の SHA-256 を記録し、`005730a` の直しの定数が入っていることを Deployment Execution でもう一度確かめる。`cd-config.md` の書き方との差を記録に残す）
B. 配備の直前に `./gradlew :backend:bootWar` で `develop` の先頭から WAR を作り直してから、イメージを作る（数分。ソースとの一致は確かになるが、配備するのは verify・E2E を流した WAR そのものではなくなる）
C. 配備の直前に `./gradlew verify` を流し直して WAR を作る（README の手順どおり。対象DB の結合テストを含め数十分かかる）
X. Other (please specify)

推奨は A です。確かめた成果物そのものを配備でき、中身が `005730a` の直しを含むことも確かめられるためです。

[Answer]: A

## Q2. 古いイメージ・ボリュームの片付け

Docker の中に、前の Intent までの戻し先のタグ付きのイメージ（`mastersmith:pre-*` など 17 個）、この Intent の Construction で作ったイメージ（`mastersmith:role-menu-b7`・`mastersmith:perf-dsl`）、テストで作られたボリュームが残っています（片付けられる量はイメージ 4.4GB・ボリューム 9.3GB・ビルドのキャッシュ 0.9GB）。ディスクは足りています（VM の空き 69G・PC の空き 40GiB）。この段で片付けますか？

A. 前の Intent（Q1: B）と同じく、使われていない名前の無いイメージとビルドのキャッシュだけを片付ける（`docker image prune`・`docker builder prune`。タグ付きのイメージとボリュームは残す）。片付ける前後の量を記録する
B. 片付けない（今の量を記録だけする）
C. A に加えて、どのコンテナにも使われていないボリュームも片付ける（`mastersmith_*` の3つは残す。片付ける一覧を先に見せて、あなたの承認を得てから消す）
X. Other (please specify)

推奨は A です。前の Intent と同じ扱いで、戻し先に使うタグ付きのイメージと内部DB などのボリュームに触れずに済むためです。

[Answer]: A

## Sources

- この Intent の配備の段: `operation/deployment-pipeline/cd-config.md`（1節・3節の順 2〜9・6節）・`deployment-strategy.md`・`rollback-runbook.md`（2節・6節・7節）
- この Intent の基盤の設計: `construction/group/infrastructure-design/infrastructure-specification.md`（4節）、`construction/role/infrastructure-design/infrastructure-specification.md`（4節）、`construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（8節・9節）
- Build and Test: `construction/build-and-test/fix-notes.md`（`Dockerfile` の `-XX:+ExitOnOutOfMemoryError`）、`bt-q1-limits.md`（scratchpad。配備と同じ上限 2g・50% での権限の YAML の測り）
- 前の同じ段: `aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/environment-provisioning-questions.md`（Q1: B）、`aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-inventory.md`（`MASTERSMITH_DB_PASSWORD`）
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`、`docker/targetdb/postgres/02-reader-account.sh`、`.env.example`、README（コンテナでの起動と確認、戻し方）、`git diff --stat 273c021 HEAD`、`git diff --stat 005730a HEAD`、`backend/build/libs/mastersmith.war` の中の `RoleTransferLimits.class`（`javap -constants`）
- 実行環境（読み取りだけ、2026-10-14）: `colima list`、`colima ssh -- free -m`・`df -h`・`/proc/meminfo`、`docker system df`、`docker ps -a`、`docker stats --no-stream`、`docker inspect mastersmith-app-1`、`docker image inspect mastersmith:local`、`docker images`、`docker volume ls`、`docker compose config app`、`.env` の項目の名前と件数（`grep -c`）、`df -h`（PC）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Deployment・Corrections の学び）

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（Build and Test で確かめた今の WAR をそのまま使い、入れ替えの前に SHA-256 と直しの値を Deployment Execution で確かめ直し、cd-config.md の書き方との差を記録する）、Q2 A（名前の無いイメージとビルドのキャッシュだけを片付け、戻し先のイメージとボリュームには触れない）。設計の値はどれも満たし、この Intent で新しく要る .env の設定は無い。戻しの練習の間は lgtm を起動しない。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
