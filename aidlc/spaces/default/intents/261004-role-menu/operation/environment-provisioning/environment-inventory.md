# 環境の一覧（environment-inventory）

Intent 261004-role-menu（F: ロールベースの権限、I: N 階層のメニュー。U1〜U7、Bolt B1〜B9）の配備先の環境です。配備先は、これまでどおり開発者の PC 上のコンテナです（`aidlc/spaces/default/memory/project.md` の Deployment）。クラウドの基盤（IaC・検証環境）は作りません。

- 書き方は前の Intent の同じ段の記録（`aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/environment-inventory.md`。以下「前の記録」）を正とし、今回の差を中心に書きます。
- 値は 2026-10-14 に読み取りだけで確かめました。この段では、コンテナ・イメージ・ボリュームを作る・止める・消す操作は行っていません（片付けも行わない。`validation-report.md` の 5節）。
- 秘密の値と個人に関する値は書きません。`.env` は開かず、項目の名前と件数だけを数えました。
- 決定は `environment-provisioning-questions.md`（Q1: A、Q2: A、まとめの確認 Looks correct）です。

## 1. 実行環境

| 項目 | 値 | 前の記録との差 |
|---|---|---|
| コンテナの実行環境 | colima（プロファイル `default`、aarch64、ランタイム docker）。Running | 同じ |
| VM の資源 | CPU 4・メモリ 6GiB・ディスク 100GiB | 同じ |
| VM の中のメモリ | 全体 5,910 MiB（MemTotal 6,052,116 kB）・使用 988 MiB・使える量 4,921 MiB | 新しく記録 |
| VM の中のディスク | 96G のうち 27G 使用、空き 69G | 新しく記録 |
| Docker | クライアント・サーバーとも 29.8.1 | 同じ |
| PC のディスク | `/System/Volumes/Data` が 460GiB のうち 379GiB 使用、空き 40GiB（91%） | 空きが 30GiB から 40GiB に増えた |
| compose のプロジェクト | `mastersmith`（配備した環境、`compose.yaml`）。一時の環境は `docker/perf/compose.yaml`（負荷の試験と戻しの練習。練習は `-p mastersmith-rollback`）と E2E（WAR を PC の上で直接起動） | 同じ |

## 2. コンテナ（今）

| コンテナ | イメージ | 状態 | 上限 | 今の使用 | PC に開く番号 |
|---|---|---|---|---|---|
| `mastersmith-app-1` | `mastersmith:local`（`fefeafbd299b`） | Up・healthy（2026-10-04 13:41 JST に作成、2026-10-08 01:08 JST に起動、再起動 0 回、OOMKilled なし） | メモリ 2GiB（2,147,483,648 B）・CPU 4 | 555 MiB | `127.0.0.1:8080` |
| `mastersmith-mailpit-1` | `axllent/mailpit:v1.31.2`（ダイジェスト固定 `74d609a4…`） | Up・healthy（profile `mail`） | 256MiB | 41 MiB | `127.0.0.1:8025`・`127.0.0.1:1025` |
| `mastersmith-targetdb-postgres-1` | `postgres:18.6`（ダイジェスト固定 `5a5a84b1…`） | Up（profile `targetdb-postgres`） | 512MiB | 55 MiB | 開かない |
| `mastersmith-lgtm-1` | `grafana/otel-lgtm:0.34.0` | Exited（10 日前に止めた。profile `monitoring`） | 1,536MiB | — | `127.0.0.1:3000`（起動したときだけ） |

- `mastersmith-app-1` は、前の Intent（261004-safety-carryover）で配備した `273c021` の版のままです（`operation/deployment-pipeline/cd-config.md` の 1節と同じイメージ ID）。
- アプリのコンテナは root でない利用者 `10001:10001` で動き、特権なし・再起動しない（`restart: "no"`）設定です。

## 3. アプリのコンテナの設定

| 項目 | 今（`273c021` の版） | 配備の後（この Intent の版） |
|---|---|---|
| 起動の入口 | `java -XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1 ${MASTERSMITH_JAVA_OPTIONS:-} -jar /app/mastersmith.war` | 上に `-XX:+ExitOnOutOfMemoryError` が加わる（`Dockerfile`。Build and Test の Q2: A） |
| 最大ヒープ | 1,024 MB（2g の 50%） | 同じ。`.env` に `MASTERSMITH_JAVA_OPTIONS` が無い（0 件）ため上書きされない |
| ヒープが尽きたとき | JVM は動き続けうる | JVM が止まり、`restart: "no"` のため起き直さない（`operation/deployment-pipeline/rollback-runbook.md` の 7節） |
| ヘルスチェック | bash の `/dev/tcp` で `GET /actuator/health` の 200。間隔 30 秒・時間切れ 5 秒・3回・猶予 40 秒・始めの間隔 2 秒 | 同じ（`compose.yaml` は変わらない） |
| ボリュームの結び付け | `mastersmith_mastersmith-data` → `/app/data`（読み書き） | 同じ |
| ログ | json-file、10m × 3 | 同じ |
| 環境変数（名前だけ） | `.env` から来る 16 項目と `TZ`、イメージの `PATH`・`JAVA_HOME`・`JAVA_VERSION`・`LANG`・`LANGUAGE`・`LC_ALL` | 同じ |

## 4. 設定のファイル（値は書かない）

| ファイル | 権限・管理 | 項目 | この Intent での差 |
|---|---|---|---|
| `.env` | 600、Git の管理外（`.gitignore` の `.env`）。最後の更新は 2026-09-30 23:48 | 16 項目（前の記録と同じ名前。初期管理者の2つ・署名鍵・コンテナの CPU とメモリ・ベース URL・メール3つ・対象DB の7つ）。どれも1件ずつ。`MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` の値は既定と同じ（2g・4。値の一致だけを `grep -c` で確かめた） | 変えない。新しく要る項目は無い |
| `.env` に無い項目 | — | `MASTERSMITH_DB_PASSWORD`（Intent `260922-auth-audit-base` の決定で内部DB は空のパスワードで作られた。入れると開けなくなる）・`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`（既定 30 が効く）・`MASTERSMITH_JAVA_OPTIONS`（空）・`SPRING_MAIL_USERNAME`・`SPRING_MAIL_PASSWORD`（Mailpit は資格情報なし） | 同じ |
| `.env.targetdb` | 600、Git の管理外（`.gitignore` の `.env.*`） | 見本の対象DB の2項目。アプリのコンテナには渡らない | 変えない |
| `.env.example`・`.env.targetdb.example` | Git の管理（Git が持つ `.env` の名前のファイルはこの2つだけ） | — | 差が無い（`git diff 273c021 HEAD`） |
| `compose.yaml`・`docker/perf/compose.yaml`・`docker/` | Git の管理 | — | 差が無い。`config --quiet` が通る |
| `Dockerfile` | Git の管理 | — | `-XX:+ExitOnOutOfMemoryError` を足した（3節） |
| `backend/src/main/resources/application.yaml` | Git の管理（イメージの中に入る） | — | `server.tomcat.max-swallow-size: 11MB` と、`org.springframework.jdbc.core.StatementCreatorUtils: OFF` を足した。`.env` では変えない |
| `backend/src/main/resources/db/migration/` | Git の管理 | — | `V10__u3_group.sql`・`V11__u4_role.sql`・`V12__u4_role_assignment.sql` を足した。表と空を許す列を足すだけで、既存の列と行は変えない。`users.admin_flag` は残す。入れ替えの起動で Flyway が当てる（今の内部DB は V9） |
| `backend/src/main/resources/navigation/allowed-icons.txt` | Git の管理（WAR に入る） | — | 新しく足した（U5。起動時に1回読み、誤りなら起動を止める） |

## 5. 配備に使う WAR（Q1: A）

| 項目 | 値 |
|---|---|
| ファイル | `backend/build/libs/mastersmith.war`（2026-10-14 00:45 作成、約 102MB） |
| SHA-256（先頭 16 文字） | `517c847b47647338` |
| 中身の確かめ | `WEB-INF/classes/cherry/mastersmith/role/service/RoleTransferLimits.class` の定数が `DEFAULT_MAX_BYTES = 5242880`・`MAX_EXPANDED_NODES = 500000`（`javap -constants`）。`005730a` の直し（権限の YAML の上限 5 MiB・節 50 万）を含む |
| 由来 | Build and Test の verify・E2E で確かめた成果物。`005730a` の後にアプリのコードの変更は無い（`git diff 005730a HEAD -- . ':!aidlc'` は README の1行だけ） |
| 使い方 | 作り直さずに使う。`docker compose ... up -d --build app` は、この WAR を `Dockerfile` で写してイメージを作るだけ（WAR は作り直さない） |
| Deployment Execution で確かめ直すこと | 入れ替えの前に、WAR の SHA-256 が上と同じこと（全体の値を記録する）と、`RoleTransferLimits` の2つの定数が上と同じことを、もう一度確かめる。違えば入れ替えを止めて依頼者に諮る |

## 6. イメージ

### 6.1 `mastersmith`（アプリ）

| タグ | ID | 作成 | 使い道 |
|---|---|---|---|
| `local` | `fefeafbd299b` | 2026-10-04 | 今配備している版（`273c021`）。Deployment Execution で、入れ替えの前に `pre-role-menu` のタグを付けて戻し先にする（**まだ付けていない**） |
| `role-menu-b7` | `63365496e5c4` | 2 日前 | この Intent の Construction で作った試しのイメージ |
| `perf-dsl` | `e1fd63046527` | 6 日前 | この Intent の試しのイメージ |
| `safety-carryover`・`pre-safety-carryover`・`user-admin-followup`・`pre-user-admin-followup`・`pre-user-admin`・`pre-log-deps-cleanup`・`pre-quality-followup` | — | 9〜14 日前 | 前の Intent までの戻し先・試し |
| `storage-memory-fix`・`pre-user-management`・`pre-storage-memory`・`storage-memory-base`・`followup-fixes`・`pre-followup`・`pre-dsl` | — | 2 週間前 | それより前の戻し先・試し |

17 個のタグはすべて残します（Q2: A はタグ付きのイメージを消さない）。

### 6.2 配備と確かめで使うほかのイメージ

前の記録の 5.2 と同じです（`eclipse-temurin:25.0.4_7-jre-noble`、Mailpit・postgres・mysql・mariadb のダイジェスト固定のイメージ、`grafana/otel-lgtm:0.34.0`、`grafana/k6:2.3.0`）。ダイジェスト固定のイメージは `docker images` でタグが `<none>` と出ますが、名前の無いイメージ（dangling）ではありません。名前の無いイメージは今 0 個です。

### 6.3 量

| 種類 | 量 |
|---|---|
| イメージ | 30 個・9.729GB（回収できる 4.394GB） |
| コンテナ | 4 個（動いている 3） |
| ボリューム | 64 個・10.12GB（回収できる 9.262GB） |
| ビルドのキャッシュ | 22 個・2.126GB（`docker system df` では回収できる 889.7MB、`docker builder du` では 2.126GB） |

## 7. ボリュームとバックアップ

| 名前 | 中身 | 大きさ |
|---|---|---|
| `mastersmith_mastersmith-data` | 内部DB（H2）。今はスキーマ V9。V10〜V12 は入れ替えの起動で当たる | 81.92kB |
| `mastersmith_mastersmith-targetdb-postgres` | 見本の対象DB | 65.77MB |
| `mastersmith_mastersmith-monitoring` | 手元の監視のデータ | 796.5MB |
| ほかのボリューム（61 個） | 名前の無いボリューム（使い捨ての環境・テストの名残）とほかのプロジェクトのもの | 消さない（Q2: A） |
| 練習の名前のボリューム（`mastersmith-rollback_*`） | 無い（前の練習の残りは無い） | — |
| `~/.mastersmith-backup/`（権限 700） | 前の Intent までの内部DB の複写 9 件と、`.env` の複写 1 件（権限 600）。この Intent では、入れ替えの前（`…-pre-role-menu.tgz`）と配備の後（`…-after-role-menu.tgz`）の2件が増える（`cd-config.md` の 4節・`deployment-strategy.md` の S11） | — |

## 8. 番号（PC の側の待ち受け）

| 番号 | 使うもの | 今 |
|---|---|---|
| 8080 | アプリ | `127.0.0.1` で待ち受け中 |
| 8025・1025 | Mailpit | `127.0.0.1` で待ち受け中 |
| 3000 | Grafana（profile `monitoring`） | 空き（止めている） |
| 18080 | 使い捨ての環境・戻しの練習 | 空き |
| 18081 | E2E の WAR | 空き |
| 5432 | 見本の対象DB | PC に開かない |

## 9. 戻しの練習のメモリの見積もり

`rollback-runbook.md` の 6節の練習は、配備したアプリを止めずに、前の版（`mastersmith:pre-role-menu`）と練習の対象DB を `docker/perf/compose.yaml` で起動します。

| コンテナ | メモリの上限 |
|---|---|
| 配備したアプリ（`mastersmith-app-1`） | 2,048 MiB |
| Mailpit | 256 MiB |
| 見本の対象DB | 512 MiB |
| 練習のアプリ（`mastersmith-rollback-app-1`、`MASTERSMITH_CONTAINER_MEMORY=2g`） | 2,048 MiB |
| 練習の対象DB | 512 MiB |
| **合計** | **5,376 MiB** |

- VM の全体 5,910 MiB に入ります。今のコンテナの外の使用は約 340 MiB（使用 988 MiB から3つのコンテナの使用 651 MiB を引いた値）で、上限の合計と合わせて約 5,716 MiB です。余裕は約 0.2〜0.5 GiB です。
- 上限はコンテナが使える最大で、実際の使用はこれより小さい見込みです（今は配備したアプリで 555 MiB）。練習では大きな入力を流さないため、練習のアプリがヒープの上限まで使う見込みは低いです。
- Build and Test の測り（`bt-q1-limits.md`）も、配備したアプリ・Mailpit・見本の対象DB を動かしたまま、上限 2g の使い捨ての環境を並べて行い、配備したアプリは動いたままでした。
- **条件: 手元の監視（`lgtm`、上限 1,536 MiB）は練習の間に起動しない**。起動すると上限の合計が 6,912 MiB になり、VM を超えます（`compose.yaml` のコメントの「負荷の試験の間は止める」と同じ扱い）。練習を始める前に `mastersmith-lgtm-1` が Exited であることを確かめます。
- 同じ理由で、練習の間は見本の対象DB のほかの種類（`targetdb-mysql` 768MiB・`targetdb-mariadb` 512MiB）や k6 を起動しません。

## Sources

- 読み取りの結果（2026-10-14）: `colima list`、`colima ssh -- free -m`・`df -h`・`/proc/meminfo`、`docker version`、`docker ps -a`、`docker stats --no-stream`、`docker inspect`（`mastersmith-app-1`・`mastersmith-mailpit-1`・`mastersmith-targetdb-postgres-1`）、`docker image inspect mastersmith:local`、`docker images`（`dangling=true` を含む）、`docker volume ls`、`docker system df`（`-v` を含む）、`docker builder du`、`docker compose config`（`--quiet` と `app` の上限の項目だけ）、`lsof`、`df -h`、`ls -l`、`git check-ignore`、`git ls-files`、`grep -c '^NAME=' .env`、`shasum -a 256` と `javap -constants`（WAR）、`git diff 273c021 HEAD`、`git diff 005730a HEAD`
- `environment-provisioning-questions.md`（調べたこと、決まっていること、Q1: A、Q2: A、まとめの確認）
- `operation/deployment-pipeline/cd-config.md`（1節・3節・4節・6節）・`deployment-strategy.md`（S11）・`rollback-runbook.md`（2節・6節・7節）
- `construction/group/infrastructure-design/infrastructure-specification.md`（1節・2節・4節）
- `construction/role/infrastructure-design/infrastructure-specification.md`（1節〜4節）
- `construction/navigation/infrastructure-design/infrastructure-specification.md`（1節・4節）
- `construction/role-admin-ui/infrastructure-design/infrastructure-specification.md`（1節・4節）
- `construction/app-frame-ui/infrastructure-design/infrastructure-specification.md`（1節・4節）
- `construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（8節・9節）
- `construction/build-and-test/fix-notes.md`、Build and Test の `bt-q1-limits.md`（scratchpad）
- 前の記録: `aidlc/spaces/default/intents/260930-user-admin/operation/environment-provisioning/environment-inventory.md`、`aidlc/spaces/default/intents/260922-auth-audit-base/operation/environment-provisioning/environment-inventory.md`（`MASTERSMITH_DB_PASSWORD`）
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`、`docker/targetdb/postgres/02-reader-account.sh`、`.env.example`、`.gitignore`、README（コンテナでの起動と確認、戻し方）

## Assumptions & Open Questions

- [assumption] 練習のアプリの実際のメモリの使用は、配備したアプリと同じくらい（数百 MiB）に収まる。上限の合計（5,376 MiB）で VM に入ることを前提にしており、実際の値は Deployment Execution の練習で `docker stats` で記録する。
- [assumption] WAR の SHA-256 の全体の値と定数は、Deployment Execution の入れ替えの前まで変わらない（この段の後に `./gradlew` を流さない）。変わっていれば 5節のとおり入れ替えを止める。
