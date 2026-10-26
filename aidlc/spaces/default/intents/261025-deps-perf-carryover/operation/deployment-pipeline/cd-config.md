# 配備の流れの構成（cd-config）

Intent 261025-deps-perf-carryover（依存の更新、小さな直し、読み取りのプール 10、Spring Boot 4.1.2。Bolt B1〜B5）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の Intent の配備の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/` と、その元の `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/`。以下「前の手順」）と、リポジトリの `Dockerfile`・`compose.yaml`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。この Intent の流れには CI Pipeline・Infrastructure Design の段が無い（bugfix）ため、既にあるものを検査した結果を根拠にする。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q4 すべて A、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:4cec01e79026…`、WAR の SHA-256 は `6426111d…`、healthy）。前の Intent で配備した版。Build and Test の負荷の試験のために止め、`docker compose start app` で戻した（`test-results.md` 9.8） |
| 配備する版 | `develop` の先頭（この段の時点で `bb06dc9`）。`main`（`bdba082`）からの差は、`aidlc/` の外では B1〜B5 の 5 コミット（`98ef3e5`・`3292282`・`3bd1f4c`・`5bebcab`・`9bf6b70`）。アプリのコードは `9bf6b70` と同じ |
| 変わるもの | Spring Boot 4.1.2（Tomcat 11.0.27・Jackson 3.1.7 は管理の版に任せ、上書きを外した）、読み取りのプールの既定 5 → **10**、文の時間切れ・断絶の後の接続の破棄、DSL の型名の符号なし、範囲の外の整数が 409 から 400、`GET /api/me/table-access` の削除、パスワード変更・表示の設定の成功の知らせ、DSL の検索の説明文、画面の依存（Playwright 以外の npm、vite 8.3.5、react-router 8.5.0）、`HikariPoolControl` の補充待ち |
| 内部DB のスキーマ | **変更なし**（移行は V13 のまま、移行ファイルの差は 0）。H2 の版も変わらない |
| `.env` | **変更なし**。今の `.env` に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` の項目は無い（項目の名前だけを数えた。値は開いていない）ため、読み取りのプールは既定の 10 で動く |
| `Dockerfile`・`compose.yaml` の `app` | 変わらない（メモリの上限 2g、`MaxRAMPercentage` 50、`ExitOnOutOfMemoryError`、`restart: "no"`）。`compose.yaml` の変更は collector・otel-lgtm・Mailpit・MySQL（perf と試験用）のイメージの版だけで、`app` と見本の PG には当たらない |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（`postgres:18.6`）。Build and Test の E2E のため、E2E の上乗せで作り直したまま `127.0.0.1:15432` が開いている。入れ替えの停止の間に、上乗せ無しで作り直して番号を閉じる（Q1: A） |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-deps-perf-carryover`（`4cec01e79026`） |
| Mailpit・otel-lgtm | 作り直さない（Q2: A）。動いている Mailpit は v1.31.5、止まっている otel-lgtm は 0.34.0 のコンテナで、`compose.yaml`（Mailpit v1.32.0、otel-lgtm 0.37.0）と差がある。アプリの配備と関係せず、次に使うときに作り直すと新しい版になる |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB（前の Intent で読み取り確認）。`app` の上限は CPU 4・メモリ 2g |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| Spring Boot 4.1.2・画面の依存の更新 | イメージの入れ替えで反映される。起動の確かめ（`deployment-strategy.md` の S1・S2）で見る。開いているタブは入れ替えの後に読み込み直す |
| 読み取りのプール 10 | 対象DB（見本の PG）への読み取りの接続が最大 10 本になる（見本の PG は 512m、`max_connections` の既定で足りる。負荷の試験で 3 種類の対象DB は OOMKilled false・`oom_kill` 0）。`.env` を変えなくても効く |
| `GET /api/me/table-access` の削除 | 画面が呼ばなくなった道。入れ替えの後は 404 になる。スモークテストで確かめる（S5） |
| 符号なしの型名・範囲の外の整数の 400 | 既存の DSL は変更なしで読める（後方互換）。適用中の DSL は変わらない |
| 内部DB | 変更なし。移行は当たらない。前の版（`mastersmith:pre-deps-perf-carryover`）でも読める |
| 監査 | 種類や値の追加は無い |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）と、`git diff --stat 9bf6b70 HEAD -- . ':!aidlc'` が空であることを確かめる。verify・osvScan・E2E 256 件と CI（`9bf6b70` の run 40352740558、1 回目の試行で success）は Build and Test の結果を正とし、流し直さない（3.1） | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で WAR を作り直し、SHA-256 を記録する。WAR の中に読み取りのプールの既定 10（`application.yaml` の `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE:10`）と、画面のビルドがあることを中身で確かめる（`Dockerfile` は手元の WAR を写すだけ。`project.md` の学び）。アプリが止まる時間を短くするため、停止の前に行う | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-deps-perf-carryover`。付けた後に ID が `4cec01e79026` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる（前の手順）。Build and Test の後で `docker compose start app` により起動し直しているため、救済が働くならその時点で働いている。配備の後の複写で `INITIAL_ADMIN_RESCUED` の件数を数えて裏付ける | AI と依頼者 | — |
| 5 | 止める | 監査の数え始めの時刻 T0 を控え、`docker compose stop app`。スキーマ・`.env` の変更が無いため、配備の前の内部DB の複写は取らない | AI | 依頼者の承認（アプリが止まるため。順 5〜8 をまとめて承認） |
| 6 | 見本の PG の番号を閉じる（Q1: A） | 上乗せ無しで `docker compose --profile targetdb-postgres up -d --force-recreate --no-deps targetdb-postgres`（データは残る）。`pg_isready` を待ち、`mastersmith_writer` の付与が残っていること（3.2 の問い合わせ）と、PORTS が `5432/tcp` だけで `127.0.0.1:15432` が無いことを確かめる | AI | 順 5 の承認に含む |
| 7 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` で順 2 の WAR からイメージを作り直してアプリだけを入れ替える | AI | 順 5 の承認に含む |
| 8 | 健全性 | healthy を待ち、`/actuator/health` の 200 を確かめる。起動のログで Flyway の移行が 0 件・現在の版が 13 であること、書き込みの設定の WARN が無いこと、救済が見込みどおりかを確かめる（`deployment-strategy.md` の S1・S2） | AI | — |
| 9 | スモークテスト（Q3: A） | `deployment-strategy.md` の 3節の S1〜S8。ロールは作らず、初期管理者の今の割り当てを使う。監査に残る操作は前もって伝える | AI と依頼者 | 監査に残る操作は前もって伝える |
| 10 | 配備の後の複写 | アプリを止めて内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-after-deps-perf-carryover.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒、依頼者に伝えてから）。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数える（S9） | AI | 依頼者に伝えてから |
| 11 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 依頼者の承認（コミットの前に提案） |
| 12 | `main` への取り込み（Q4: A） | 取り込みの前に、前の取り込み（`bdba082`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する。WAR を作ったコミットと `main` の先頭の差が `aidlc/` だけであることを確かめて記録する。`main` が `develop` の祖先であることを確かめ、`main` を `develop` の先頭へ fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 13 | 取り込みの結果の追記 | 取り込みと push の結果を、`main` の後の `develop` だけのコミットで追記する | AI | 依頼者の承認（コミットの前に提案） |
| 14 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは順 5 から順 8 の healthy まで（見込み約 1〜2 分。見本の PG の作り直しとイメージの作り直しを含む）と、順 10 の複写（約 10 秒）。
- 順 6 の見本の PG の作り直しは、アプリが止まっている間に行うため、アプリの読み取りの接続が途中で切れることは無い。
- 開いているタブは、順 8 の後に読み込み直す。
- どの段のコミットも、行う前に依頼者に提案して承認を得る（`project.md` の Change Control）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify osvScan --rerun` | `9bf6b70` で通過（verify 23分6秒、osvScan の失敗の条件 0 件）。それ以前の `5bebcab` で 22分5秒（単体 2,612・結合 1,375〈SKIPPED 10〉・画面 1,977、失敗 0） | `construction/build-and-test/test-results.md` |
| CI | `9bf6b70` の run 40352740558 が 1 回目の試行で success（検査の job 30分33秒）。`bb06dc9` は `aidlc/` だけの変更 | `construction/build-and-test/test-results.md` 10.3 |
| E2E | 256 件 passed（`9bf6b70`、12分59秒。vite・react-router の更新を含む） | 同 10.2 |
| k6（負荷の試験） | **流さない**（3.3） | — |

### 3.2 順 6 のコマンド（値を表示しない形）

```bash
# 順 6: 見本の PG のコンテナだけを、上乗せ無しで作り直す（データは残る。E2E の番号を閉じる）
docker compose --profile targetdb-postgres up -d --force-recreate --no-deps targetdb-postgres
docker compose --profile targetdb-postgres exec -T targetdb-postgres pg_isready -U target_admin -d business   # accepting connections を待つ

# 順 6: 件数と真偽値だけで確かめる（書き込みのアカウントと TEMPORARY の取り上げが残っていること）
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
  "SELECT (SELECT COUNT(*) FROM pg_roles WHERE rolname='mastersmith_writer'),
          has_table_privilege('mastersmith_writer','sales.customers','INSERT'),
          has_table_privilege('mastersmith_writer','sales.customers','UPDATE'),
          has_table_privilege('mastersmith_writer','sales.customers','DELETE'),
          has_schema_privilege('mastersmith_writer','sales','CREATE'),
          has_database_privilege('mastersmith_writer','business','CREATE'),
          has_database_privilege('public','business','TEMP')"
#   期待: 1|t|t|t|f|f|f
docker compose --profile targetdb-postgres ps targetdb-postgres     # Up であること、PORTS が 5432/tcp だけで 127.0.0.1:15432 が無いこと
```

- 期待と違うときは、入れ替え（順 7）に進まず依頼者に諮る。前の版のまま起動し直すときは `docker compose --profile targetdb-postgres start app`。

### 3.3 配備の前の k6 を流さないこと（`project.md` の決まりとの差）

- `project.md` の Deployment の学びは「Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは、配備の前の k6 を省く」。今回の負荷の試験は `mastersmith:carryover`（WAR の SHA-256 `80b19c53…`、バックエンドのコードは配備する版と同じ）で行い、読み取りのプール 10・3 種類の対象DB・`memory.peak`・`monitorLight` などを測った（`test-results.md` 9節）。
- 違いは、その後の B5（`9bf6b70`）が画面の依存（vite 8.3.5・react-router 8.5.0）と compose の Mailpit の版を更新したため、配備する WAR は画面の成果物が試験した WAR と違う点。バックエンドのコードは同じなので、配備の前の k6 は流さない（試験済みの扱い。差を記録する）。
- 配備の後に性能の問題が出たときは、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
- Not Met 5 件（`test-results.md`、`cross-unit-traceability.md`）は、配備しても解消しない。次の Intent に持ち越した目標で、配備の可否の条件ではない（依頼者の決定）。

## 4. 配備の前の内部DB の複写を取らないこと

- スキーマの変更も `.env` の変更も無く、前の版のイメージでそのまま動くため、配備の前の複写は取らない（要件 FR5.1・NFR4、前の Intent `260928-quality-followup` と同じ扱い）。
- 配備の後の複写（順 10）は、監査の件数を数えるために必ず取り、配備の後のバックアップを兼ねる。置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）で、`mktemp -d` は使わない（colima の VM から見えない。`project.md` の学び）。
- 入れ替えの起動で内部DB が壊れた場合の手当ては `rollback-runbook.md` の 4節（配備の前の複写が無いため、配備の後の複写では戻せない。この場合は依頼者に諮る）。H2 の版も移行も変わらないため、この危険は低いと見ている（見立て）。

## 5. 決まりと上流との差（記録）

- **配備の前の k6**: 3.3 のとおり。バックエンドのコードは試験した WAR と同じで、画面の依存の更新（B5）だけが違う。
- **戻しの確かめの手順**: 質問のファイルの「決まっていること」には、戻した版のイメージが起動することを一時のボリュームで確かめる手順を置くと書いたが、戻し先は今まさに動いている `mastersmith:local`（healthy）そのもので、起動することは本物のデータで確かめ済みのため、別の確かめの手順は置かない。戻しの手順だけを `rollback-runbook.md` に置く。
- **要件 FR5.2「`main` の先頭から WAR を作り直して配備する」**: Q4: A で、`develop` の先頭から作り直し、`main` の取り込みを配備の後に置く。WAR を作ったコミットと `main` の先頭の差が `aidlc/` だけであることを確かめて記録し、アプリのコードを同じにする。要件の文は書き換えない。
- **WAR の作り直しの時点**: 前の手順と同じく、アプリを止める前に作り直す（順 2）。停止の間はイメージの作り直し（`--build`）だけを行う。
- **E2E**: 配備の前の E2E は Build and Test の `9bf6b70` の 256 件で満たす（流し直さない）。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。版はコミットのハッシュ（アプリのコードは `9bf6b70`）で識別し、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）、`aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/`
- 質問と答え: `aidlc/spaces/default/intents/261025-deps-perf-carryover/operation/deployment-pipeline/deployment-pipeline-questions.md`
- この Intent の要件・Code Generation・Build and Test: `inception/requirements-analysis/requirements.md`（FR5・NFR4）、`construction/code-generation/code-summary.md`、`construction/build-and-test/build-and-test-summary.md`・`test-results.md`・`cross-unit-traceability.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`docker/e2e/compose.targetdb-port.yaml`、`backend/src/main/resources/application.yaml`（読み取りのプールの既定）、`gradle/libs.versions.toml`、`git diff main develop`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment の学び・Change Control）

## Assumptions & Open Questions

- [assumption] 配備の `.env` の対象DB の項目と書き込みの2項目は、前の Intent の配備のまま（`.env` は開いていない）。
- [assumption] 初期管理者の状態は前の配備のまま。Build and Test の後の `docker compose start app` で救済が働くなら、その時点で働いている。順 4 で依頼者に確かめる。
- 救済の見込みは順 4 で依頼者に確かめる。
