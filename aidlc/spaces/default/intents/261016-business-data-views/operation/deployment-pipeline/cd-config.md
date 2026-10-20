# 配備の流れの構成（cd-config）

Intent 261016-business-data-views（業務データの一覧・詳細・作成・更新・削除の画面と、持ち越しの直し）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の Intent の配備の手順（`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/` と、その元の `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/`。以下「前の手順」）と、リポジトリの `Dockerfile`・`compose.yaml`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q5 すべて A、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:f103aee1702a…`、healthy）。前の Intent で配備した `main` の先頭 `6dd9bb0`（アプリのコードは `776738e`） |
| 配備する版 | `develop` の先頭（この段の時点で `7f2575c`）。`main` からの差は 39 コミットで、`aidlc/` の外を変えたのは 13 コミット。アプリのコードは `d220447`（B7）と同じ（`22de65f` はテスト・`vitest.config.ts`・README だけ） |
| 内部DB のスキーマ | V12 → **V13**（`audit_events` に空を許す3列 `target_schema`・`target_table`・`work_role_id` を足すだけ。前進のみ・後方互換） |
| `Dockerfile`・`compose.yaml` | 変わらない（メモリの上限 2g、`MaxRAMPercentage` 50、`ExitOnOutOfMemoryError`、`restart: "no"`） |
| `.env` | 書き込みの2項目 `MASTERSMITH_TARGET_DB_WRITE_USERNAME`（値 `mastersmith_writer`）・`MASTERSMITH_TARGET_DB_WRITE_PASSWORD` を足す。時間の上限（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL`・`_WRITE`、既定 5s）と書き込みのプール（`MASTERSMITH_TARGET_DB_WRITE_POOL_MAXIMUM_SIZE`、既定 5）は足さず既定のまま |
| `.env.targetdb` | `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` を足す（`.env` の書き込みのパスワードと同じ値） |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（`postgres:18.6`、ボリュームは 2026-09-25 作成）。ボリュームを残したまま書き込みのアカウント `mastersmith_writer` を足す（U2 の基盤の設計 Q4: A）。`TEMPORARY` の PUBLIC からの取り上げは B6 で済んでいる。PC に番号は開けない（E2E の上乗せ `docker/e2e/compose.targetdb-port.yaml` は使わない） |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-business-data-views`（`f103aee1702a`） |
| Mailpit・otel-lgtm | 入れ替えない（`compose.yaml` の版は変わらない） |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB（2026-10-21 に読み取りで確かめた）。`app` の上限は CPU 4・メモリ 2g |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| 業務データの読み取りの API と画面（U1・U3） | 業務のメニューの項目から一覧・詳細を開けるようになる。前提として、適用中の DSL に見本の `sales` のテーブルがあり、作業ロールにテーブルの権限があること（4節で数える）。スモークテストで確かめる（`deployment-strategy.md` 3節） |
| 業務データの作成・更新・削除（U2・U3） | 書き込みの接続 `mastersmith-target-db-write`（上限 5、起動時は接続しない）が増える。`.env` の書き込みの2項目が両方あるときだけ使い、無ければ書き込みの API は 409 `BUSINESS_WRITE_NOT_CONFIGURED`（読み取りは動く）。この2項目が書き込みの機能の実質の入れ切りで、ほかの機能の切り替えの仕組みは持たない |
| 内部DB の移行 V13 | 入れ替えの起動で1件当たる。H2 は DDL を巻き戻せないため、入れ替えの前の複写を必ず取る（3節の順 5） |
| 監査の種類と値 | `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED`、結果 `UNKNOWN`、失敗の理由 `BUSINESS_*` が増える。前の版は監査の行を読まない（`rollback-runbook.md` 4節） |
| make-you-chic-ui `b0a1e79`・行の操作の名前・axe-core 4.14.0 | 画面の部品が替わるため、入れ替えの後に開いているタブを読み込み直す |
| 見本の初期化の台本 `03-writer-account.sh` | 既にあるボリュームでは動かないため、手で流す（3節の順 7） |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）と、`git diff --stat d220447 HEAD -- . ':!aidlc'` が `22de65f` の4ファイル（README・`AbstractBusinessDataReadIT.java`・`frontend/vitest.config.ts`・`perf/README.md`）だけであることを確かめる。verify・CI・E2E は Build and Test と CI Pipeline の結果を正とし、流し直さない（3.1） | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で WAR を作り直し（Build and Test で最新の扱いで作り直されなかったため）、SHA-256 を記録する。WAR の中に今回のクラス（`businessdata` の web・service・repository）、`V13__u2_business_data_audit_columns.sql`、画面のビルドがあることを中身の一覧で確かめる（`Dockerfile` は手元の WAR を写すだけ。`project.md` の学び）。アプリが止まる時間を短くするため、停止の前に行う | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-business-data-views`。付けた後に ID が `f103aee1702a` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる（前の手順） | AI と依頼者 | — |
| 5 | 止めて内部DB を複写（4節） | 監査の数え始めの時刻 T0 を控え、アプリを止め、内部DB を `~/.mastersmith-backup/`（権限 700）へ複写する（配備の前のバックアップ）。複写を読み取りで開き、4節の数だけを読む | AI | 依頼者の承認（アプリが止まるため。順 5〜9 をまとめて承認） |
| 6 | `.env` の複写と書き足し（Q1: A・Q2: A） | `.env` を `$HOME/.mastersmith-env-before-writer`（権限 600）へ複写し、乱数のパスワードを表示せずに `.env.targetdb` と `.env` に足す。**流す前に、3.2 のコマンドを依頼者に見せて承認を得る**。足した後は、項目の名前と値が空でないことだけを数える | AI | 依頼者の承認（コマンドを見せて） |
| 7 | 見本の PG に書き込みのアカウントを足す | 見本の PG のコンテナだけを作り直して新しい変数を渡し（データは残る）、`03-writer-account.sh` を流して、ロール・付与・`TEMPORARY` が無いことを件数と真偽値で確かめる（3.2） | AI | 順 5 の承認に含む |
| 8 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` で順 2 の WAR からイメージを作り直してアプリだけを入れ替える（新しい `.env` もここで読む） | AI | 順 5 の承認に含む |
| 9 | 健全性 | healthy を待ち、`/actuator/health` の 200 を確かめる。起動のログで V13 が1件当たったこと、書き込みの設定の WARN が無いこと、救済が見込みどおりかを確かめる（`deployment-strategy.md` の S1・S2） | AI | — |
| 10 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節）と、配備の後の複写（監査の件数の数え） | AI と依頼者 | 監査に残る操作は前もって伝える |
| 11 | 戻しの練習（Q3: A） | 順 10 の配備の後の複写で、前の版を別のコンテナ・番号で起動して確かめる（`rollback-runbook.md` 6節）。配備したアプリは止めない | AI と依頼者 | 依頼者の承認 |
| 12 | 配備の記録のコミット（Q5: A） | Deployment Execution の記録をコミットする | AI | 依頼者の承認（コミットの前に提案） |
| 13 | `main` への取り込み（Q5: A） | 取り込みの前に、前の取り込み（`6dd9bb0`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する。`main` が `develop` の祖先であることを確かめ、`main` を `develop` の先頭へ fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 14 | 取り込みの結果の追記 | 取り込みと push の結果を、`main` の後の `develop` だけのコミットで追記する | AI | 依頼者の承認（コミットの前に提案） |
| 15 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは順 5 から順 9 の healthy まで（見込み約 3〜4 分。見本の PG の作り直しと台本、イメージの作り直しを含む）と、順 10 の配備の後の複写（約 10 秒）。
- 順 7 の見本の PG の作り直しは、アプリが止まっている間に行うため、アプリの読み取りの接続が途中で切れることは無い。
- 開いているタブは、順 9 の後に読み込み直す。
- どの段のコミットも、行う前に依頼者に提案して承認を得る（`project.md` の Change Control）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew verify` | Build and Test の直しの後（`develop` と同じ中身）で通過（22分26秒。単体 2,589・結合 1,389・画面 1,975 件、失敗 0） | `construction/build-and-test/build-and-test-summary.md` |
| CI | `31d434c` まで確かめ済み（`288736d`・`22de65f` は `31d434c` の run で確かめた。`05a9b37` の1回目の失敗は `construction/ci-pipeline/ci-config.md` 6.2 に記録）。`7f2575c` 以降は `aidlc/` だけで、push の後に順 13 で確かめる | `construction/ci-pipeline/ci-config.md` |
| E2E | 256 件 passed（B7 の後。その後のアプリのコードの変更は無いため、`team.md` の「リリースの前に E2E」はこれで満たす） | `construction/build-and-test/test-results.md` 2.3・2.4 |
| k6（負荷の試験） | **流さない**（3.3） | — |

### 3.2 順 6・順 7 のコマンド（値を表示しない形）

README の「既にある見本の DB に書き込みのアカウントを足す（U2）」の手順を、入れ替えの停止の中に置いたものです。**順 6 のコマンドは、流す前に依頼者に見せて承認を得ます**（Q2: A）。`.env`・`.env.targetdb` は開かず、`cat`・`grep` で値を出しません。

```bash
# 順 6: 前提（書き込みの項目がまだ無いこと。名前の件数だけ）
grep -c '^MASTERSMITH_TARGET_DB_WRITE_' .env                       # 0 であること
grep -c '^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=' .env.targetdb   # 0 であること

# 順 6: .env をリポジトリの外へ複写し、乱数のパスワードを表示せずに足す
(umask 077 && cp .env "$HOME/.mastersmith-env-before-writer")
W=$(openssl rand -hex 24)
(umask 077 && printf 'MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=%s\n' "$W" >> .env.targetdb)
(umask 077 && printf 'MASTERSMITH_TARGET_DB_WRITE_USERNAME=mastersmith_writer\nMASTERSMITH_TARGET_DB_WRITE_PASSWORD=%s\n' "$W" >> .env)
unset W

# 順 6: 足したことの確かめ（値が空でない行の件数だけ）
grep -cE '^MASTERSMITH_TARGET_DB_WRITE_USERNAME=.+' .env              # 1
grep -cE '^MASTERSMITH_TARGET_DB_WRITE_PASSWORD=.+' .env              # 1
grep -cE '^MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=.+' .env.targetdb   # 1
ls -l "$HOME/.mastersmith-env-before-writer"                          # 権限 -rw------- であること（中身は開かない）

# 順 7: 見本の PG のコンテナだけを作り直して新しい変数を渡す（データは残る。上乗せの番号は付けない）
docker compose --profile targetdb-postgres up -d --force-recreate --no-deps targetdb-postgres
docker compose --profile targetdb-postgres exec -T targetdb-postgres pg_isready -U target_admin -d business   # accepting connections を待つ
docker compose --profile targetdb-postgres exec -T targetdb-postgres bash /docker-entrypoint-initdb.d/03-writer-account.sh

# 順 7: 件数と真偽値だけで確かめる
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
  "SELECT (SELECT COUNT(*) FROM pg_roles WHERE rolname='mastersmith_writer'),
          has_table_privilege('mastersmith_writer','sales.customers','INSERT'),
          has_table_privilege('mastersmith_writer','sales.customers','UPDATE'),
          has_table_privilege('mastersmith_writer','sales.customers','DELETE'),
          has_schema_privilege('mastersmith_writer','sales','CREATE'),
          has_database_privilege('mastersmith_writer','business','CREATE'),
          has_database_privilege('public','business','TEMP')"
#   期待: 1|t|t|t|f|f|f
docker compose --profile targetdb-postgres ps targetdb-postgres     # Up であること、PORTS が空であること
```

- 台本が「…が空です」で止まったときは、`.env.targetdb` の書き足しが見本の PG のコンテナに渡っていない。順 7 の作り直しをやり直す（ボリュームは消さない。既にあるボリュームでは初期化は動かないため、`rollback-runbook.md` の初期化の止まりの手当ては当たらない）。
- 順 7 の確かめが期待と違うときは、入れ替え（順 8）に進まず依頼者に諮る。前の版のまま起動し直すときは `docker compose --profile targetdb-postgres start app`（`.env` の書き込みの2行は前の版が読まないため、そのままでよい）。

### 3.3 配備の前の k6 を流さないこと（`project.md` の決まりとの差）

- `project.md` の Deployment の学びは「Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは、配備の前の k6 を省く」。今回の業務データの k6 の場面は、Build and Test の Q3: A で Performance Validation に回し、試験済みではない（Unverified 12 件。持ち主 Performance Validation）。
- それでも配備の前には流さず、Performance Validation に任せる（前の Intent `260925-user-management` の Q2: B、`261004-role-menu` の Q4: A と同じ扱い）。Performance Validation はこの Intent の流れで Deployment Execution の後にある。
- 代わりに次を拠り所にする: `dslMixed` の `memory.peak` は Build and Test で Met（0.873・0.870。0.9 までの余裕は約 55〜62 MiB と小さい）、書き込みのプールは上限 5 で対象DB を守る、ヒープが尽きても `ExitOnOutOfMemoryError` で JVM が止まり中途半端に動き続けない。
- 配備の後に性能の問題が出たときは、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- `RETENTION_TIME=0` は配備物に取り込まない（後の Intent で決める。`construction/build-and-test/memory-peak-measurement.md` 3節）。

## 4. 入れ替えの前の内部DB の複写（順 5）

- 手順は README の「内部DBのバックアップと戻し方」のとおり。名前は `mastersmith-data-<日時>-pre-business-data-views.tgz`。置き場をリポジトリの外のホームの下（権限 700）にし、`mktemp -d` は使わない（colima の VM から見えない。`project.md` の学び）。
- スキーマが変わる（V13）ため省かない。

```bash
date -u +%Y-%m-%dT%H:%M:%SZ          # T0 を控える
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-pre-business-data-views.tgz -C /data .
ls -l ~/.mastersmith-backup/          # ファイルができたこと（中身は開かない）
# ここでは start しない（順 6〜8 へ進む）
```

- 複写をホームの下の権限 700 の作業用のディレクトリに展開し、前の Intent と同じリポジトリの外の小さな Java の道具（H2 2.4.240 の JDBC、`ACCESS_MODE_DATA=r`）で開いて、次の数だけを出す。値（DSL の本文・メールアドレス・ロールの名前）は表示しない。数え終えたら作業用のディレクトリを消す。

| 数えるもの | 見込み | 使い道 |
|---|---|---|
| `flyway_schema_history` の最大の版 | 12 | 入れ替えの起動で V13 の1件が当たる見込みの裏付け |
| `dsl_applied_revisions` の行の数と、最新の行の本文に文字列 `customers`・`customer_orders` が現れる回数（回数だけ） | 1 行以上、どちらも 1 回以上 | 0 なら、スモークテストの前に DSL の画面で既定の DSL を生成して適用する（`DSL_GENERATED`・`DSL_APPLIED` を監査の期待に足す）。依頼者に諮る |
| `dsl_previews` の行の数 | 0 | 1 なら、スモークテストで DSL を触らない限りそのまま |
| `roles` の行の数、`.env` の初期管理者（表示しない）の `user_role_assignments` と `group_role_assignments` 経由の割り当ての数、`work_role_selections` の行の有無 | 前の Intent の片付けの状態のまま | 0 でなければ、作業ロールの自動の選び方と `WORK_ROLE_SWITCHED` の期待が変わるため、スモークテストの前に依頼者と合わせ直す（`deployment-strategy.md` 3.2） |
| 初期管理者の `suspended`・`admin_flag` | 停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |

## 5. 決まりと上流との差（記録）

- **配備の前の k6**: 3.3 のとおり、`project.md` の「試験済みのときだけ省く」と違い、試験していないまま配備の前の k6 を省く。
- **WAR の作り直しの時点**: 前の手順と同じく、アプリを止める前に作り直す（順 2）。停止の間はイメージの作り直し（`--build`）だけを行う。
- **E2E**: 配備の前の E2E は Build and Test の B7 の後の 256 件で満たす（流し直さない）。
- **書き込みのアカウントの足し方**: U2 の基盤の設計 Q4: A と README の手順のとおり。README の手順はアプリの作り直しを別に置くが、ここでは入れ替え（順 8）に含めて1回にした（Q1: A）。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。版はコミットのハッシュ（アプリのコードは `d220447`）で識別し、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/`、`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/`、`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-execution/deployment-log.md`
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/deployment-pipeline-questions.md`
- この Intent の基盤の設計: `construction/u2-businessdata-write/infrastructure-design/infrastructure-design-questions.md`（Q4: A）・`infrastructure-specification.md`・`cicd-pipeline.md` 6節、`construction/u1-businessdata-read/infrastructure-design/cicd-pipeline.md` 6節、`construction/u3-businessdata-screens/infrastructure-design/cicd-pipeline.md` 5節
- この Intent の Build and Test と CI: `construction/build-and-test/build-and-test-summary.md`・`build-and-test-questions.md`・`memory-peak-measurement.md`・`test-results.md`、`construction/ci-pipeline/ci-config.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`.env.example`、`.env.targetdb.example`、`backend/src/main/resources/application.yaml`、`backend/src/main/resources/db/migration/V13__u2_business_data_audit_columns.sql`、`docker/targetdb/postgres/03-writer-account.sh`、README（「内部DBのバックアップと戻し方」「既にある見本の DB に書き込みのアカウントを足す（U2）」「環境変数」）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment の学び・Change Control）

## Assumptions & Open Questions

- [assumption] 配備の `.env` の `MASTERSMITH_TARGET_DB_*` の7項目は、見本の対象DB（`targetdb-postgres:5432`・`business`・`sales`・`mastersmith_reader`）を指す（`.env` は開いていない。README の手順のとおり）。書き込みはこの接続先をそのまま使う。
- [assumption] 適用中の DSL は前の Intent で生成した版 2 で、見本の `sales` のテーブルを含む。順 5 の複写で回数を数えて確かめる。
- 救済の見込みは順 4 で依頼者に確かめる。
