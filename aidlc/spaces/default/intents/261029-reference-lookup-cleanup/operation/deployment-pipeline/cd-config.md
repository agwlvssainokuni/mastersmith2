# 配備の流れの構成（cd-config）

Intent 261029-reference-lookup-cleanup（他テーブル参照の REFERENCE・LOOKUP の読み取り・保存前の確かめ・画面、perf の記録の伏せ字の道具、コンテナと perf の文書の整理）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の Intent の配備の手順（`aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-pipeline/`、その元は `261025-deps-perf-carryover`・`261016-business-data-views`）と、リポジトリの `Dockerfile`・`compose.yaml`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。この Intent の上流の CI Pipeline（`construction/ci-pipeline/ci-config.md`・`quality-gates.md`）と各単位の Infrastructure Design（`construction/u*/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`）が残した点を、ここで一本にまとめる。
- **決定**: `deployment-pipeline-questions.md`（Q1: A、Q2: B、Q3: A、Q4: A）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:e0ede6ddd5c4…`、前の Intent で配備した版、healthy）。見本の PG（`postgres:18.6`、E2E の口の重ね合わせ `127.0.0.1:15432` のまま）と Mailpit（`v1.32.0`）が動き、lgtm（`0.38.0`）は止まっている |
| 配備する版 | `develop` の先頭 `23519159`。`main`（`d797ef74`）から 64 コミット先 |
| 変わるもの | `backend/src/main`（`businessdata` の参照の読み取り・保存前の確かめ・新しい道 `POST /api/tables/reference/rows`、`POST /api/tables/definition` の `purpose`、`targetdb` の目的 `REFERENCE` と2つの設定）、`frontend/src`（参照の部品）、`frontend/package.json`・lockfile（i18next・React など）、`vendor/make-you-chic-ui` の固定先（`ecb07e9` → `cab4207`）。WAR とイメージの中身が変わる |
| 変わらないもの（配備に入らない） | `build.gradle.kts` の perf の道具の検査、`perf/`、E2E（180・190・195）、`README.md`・`.env.example` のコメント |
| 内部DB のスキーマ | **変更なし**（`backend/src/main/resources/db` の差 0、V13 のまま） |
| `.env` | **変更なし**。足した2つの設定 `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`（既定 5s）・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（既定 10s）は既定で動く（U1 の Q1: A）。`.env` は開かず、2つの項目名の有無だけを数える（見込みはどちらも 0 件） |
| 監査の種類 | `AuditEventType` に新しい種類は無い。保存前の参照の確かめの拒否は既存の `BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED` の `FAILURE`（理由 `BUSINESS_REFERENCE_NOT_FOUND`）に残る。参照先の読み取りは監査に残らない |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-reference-lookup-cleanup`（`e0ede6ddd5c4`） |
| 試験用のタグ | `mastersmith:reference-lookup-cleanup`（`eab1676f26de`、Build and Test の試し走りの版 `df79634c`。Tooltip の直し `cb23466b` を含まない）。順 12 で消す（Q4: A） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| U1 参照の読み取り | 作成・更新の画面の定義の取得（`purpose` が `CREATE`・`UPDATE`）で、参照の項目があるときだけ対象DB の読み取りの接続を1本借りる。2つの設定の 1 秒未満の値は対象DB 全体を使えなくする（R-01）。既定で動くため `.env` に足さない |
| U2 保存前の参照の確かめ | 作成・更新の保存の前に参照先を確かめる。拒否は 409 `BUSINESS_REFERENCE_NOT_FOUND` で監査に `FAILURE` が残る。移行・設定の追加は無い |
| U3 参照の画面 | 画面の静的なファイルだけ。WAR に同梱され、U1・U2 と同じイメージに入る |
| U4 perf の伏せ字の道具 | 配備されない（`verify` と CI の検査だけ） |
| U5 コンテナと perf の文書 | Mailpit・lgtm の入れ替えは U5 で済み。アプリの配備は無い |
| make-you-chic-ui の固定先 | 画面のビルドに入る（Tooltip の後片付け、依頼7） |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）を確かめる。verify・E2E・CI は Build and Test と CI Pipeline の結果を正とし流し直さない（3.1）。`23519159` の CI の結果を確かめる | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で `develop` の先頭から WAR を作り直し、SHA-256 を記録する。WAR の中の `application.yaml` に `reference: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE:5s}` と `reference-read-total: ${MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL:10s}` があること、画面のビルド（`static/`）に参照の部品の文言が入っていることを中身で確かめる（`Dockerfile` は手元の WAR を写すだけ。`project.md` の学び）。停止の前に行う。シェルに `MASTERSMITH_IMAGE_TAG` が残っていないこと（`env \| grep -c MASTERSMITH_IMAGE_TAG` が 0）を確かめる | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-reference-lookup-cleanup`。付けた後に ID が `e0ede6ddd5c4` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。順 5 の複写の数えで裏付ける | AI と依頼者 | — |
| 5 | 止めて配備の前の複写（Q3: A） | T0 を控え、`docker compose stop app`。内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-pre-reference-lookup-cleanup.tgz`（権限 600）として複写する（4節）。複写を読み取りで開き、4.1 の数を出す（値は表示しない） | AI | 依頼者の承認（アプリが止まるため。順 5〜7 をまとめて承認） |
| 6 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps app` で作り直す（`project.md` の学び）。見本の PG・Mailpit は作り直さない | AI | 順 5 の承認に含む |
| 7 | 健全性 | `deployment-strategy.md` の S1（healthy・`/actuator/health` の 200、起動のログ: ERROR 0・Flyway の `Migrating schema` 0 件で版 13・救済の WARN 0・点検の WARN に `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` が 0 件） | AI | — |
| 8 | 4.1 の結果での合わせ直し | 4.1 の数が前提と違うとき（適用中の DSL に参照が無い・初期管理者の割り当てや作業ロールが前と違う）は、スモークテストの前に依頼者と合わせ直し、監査の期待を直す | AI と依頼者 | 依頼者の決定 |
| 9 | スモークテスト（Q1: A） | `deployment-strategy.md` の S2〜S8。★の操作は監査に残るため、行う前に期待の件数を伝える | AI と依頼者 | 監査に残る操作は前もって伝える |
| 10 | 配備の後の複写（Q3: A） | アプリを止めて内部DB を `…-after-reference-lookup-cleanup.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒、依頼者に伝えてから）。起動し直した後もイメージ ID の一致を確かめる。複写を読み取りで開き、T0 以降の `audit_events` を種類と結果ごとに数え、ロール・割り当て・作業ロールの数を4.1 と比べる（`deployment-strategy.md` の S9） | AI | 依頼者に伝えてから |
| 11 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 依頼者の承認（コミットの前に提案） |
| 12 | 試験用タグの片付け（Q4: A） | どのコンテナも `eab1676f26de` を使っていないこと（`docker ps -a --filter ancestor=…` が 0 件）を確かめてから `docker rmi mastersmith:reference-lookup-cleanup` | AI | 実行の直前に確かめる |
| 13 | `main` への取り込み | 取り込みの前に、前の取り込み（`d797ef74`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する（`a75baec4` の1回目 `ReferenceSameResponseIT`、`e1e028d1` の1回目の画面のテストの未処理の例外を含む）。記録だけのコミットの CI も確かめる（新しい脆弱性の公表は記録だけのコミットでも落ちる。`project.md` の学び）。`main` が `develop` の祖先であることを確かめ、fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 14 | 取り込みの結果の追記 | 取り込みと push の結果を、`main` の後の `develop` だけのコミットで追記する | AI | 依頼者の承認（コミットの前に提案） |
| 15 | プッシュ | `origin` へ `develop` と `main` をプッシュするのは依頼者 | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは順 5 から順 7 の healthy まで（見込み約 2〜3 分。複写と数えが入る）と、順 10 の複写（約 10 秒）。WAR の作り直しは停止の前に置く。
- 開いているタブは、順 7 の後に読み込み直す（新しい画面のファイルを読むため）。
- どの段のコミットも、行う前に依頼者に提案して承認を得る（`project.md` の Change Control）。段の成果物の初稿と段の承認の時のコミットだけは尋ねずに行う。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 通過（26 分 4 秒。単体 2,807・結合 1,654〈SKIPPED 10。DB の種類別の前提〉・画面 238 ファイル 2,312、失敗 0） | `construction/build-and-test/test-results.md` 2節 |
| E2E（Build and Test） | 277 件 passed（14.0 分、実際の Chromium） | 同 3節 |
| 固定先の更新の後の verify と E2E | 短命のブランチ `fix/261029-reference-lookup-cleanup-tooltip-timer` で verify 通過・E2E 277 件 passed（14.1 分、Sleep・DarkWake 0） | `construction/ci-pipeline/ci-config.md` 6.4節 |
| CI | `cb23466b`（run 41270200479）が1回目で success（検査の job 28分49秒）。その後の `1e8e42bb`・`23519159` は `aidlc/` だけ。`23519159` の CI は確認中 | 同 6.4節、`gh run list` |
| k6（負荷の試験） | **配備の前には流さない**（3.2） | — |

### 3.2 配備の前の k6 を流さないこと（Q2: B、`project.md` の決まりとの差）

- `project.md` の学びは「Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは、配備の前の k6 を省く」。今回の Build and Test は参照の6場面の試し走り（1 VU・10 秒）と種の確かめまでで、判定の回（10 VU・3 分、合否の回 2 回）は Performance Validation（この Intent の後の段）に回した（Build and Test の Q3: A）。そのため、**試験していないまま配備の前の k6 を省く**ことになり、決まりと違う。
- 依頼者の決定（Q2: B）で、判定は Performance Validation に任せる。理由: 判定の回は3種類の対象DB で配備したアプリを数時間止める重さがあり、同じ回を二度流すことになる。移行と `.env` の変更が無く、戻しはイメージだけで約 1〜2 分で済む。試し走りでは 18 個の summary json がそろい、k6 の終了コードはすべて 0 だった（`construction/build-and-test/k6-trial-record.md` 4.2節）。
- 配備の後に参照の画面・保存で遅い・503 などの問題が出たときは、`rollback-runbook.md` の 2節（イメージだけ）で戻す。前の Intent `261016-business-data-views` と同じ扱い。

## 4. 内部DB の複写（Q3: A）

- 移行が無いため、戻し（イメージだけ）に複写は要らない。今回は、スモークテストの前提と T0 の数え（配備の前）と、監査の確かめ（配備の後）のために2回取り、どちらもバックアップを兼ねる。戻しの練習はしない。
- 置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）。`mktemp -d` は使わない（colima の VM から見えない。`project.md` の学び）。取り方は README の「内部DBのバックアップと戻し方」（`eclipse-temurin` の版とダイジェストを固定した `docker run … tar czf`）。

```bash
date -u +%Y-%m-%dT%H:%M:%SZ          # T0 を控える
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-pre-reference-lookup-cleanup.tgz -C /data .
chmod 600 ~/.mastersmith-backup/*-pre-reference-lookup-cleanup.tgz
ls -l ~/.mastersmith-backup/          # ファイルができたこと・権限 600（中身は開かない）
# ここでは start しない（順 6 へ進む）
```

### 4.1 配備の前の複写で数えるもの

複写をホームの下の権限 700 の作業用のディレクトリに展開し、リポジトリの外の小さな Java の道具（`~/.mastersmith-tools/`、H2 2.4.240 の JDBC、`ACCESS_MODE_DATA=r`）で開いて、数だけを出す。値（DSL の本文・メールアドレス・ロールの名前）は表示しない。列の名前は問い合わせの前に `Cols.java` で確かめる（`users` の主キーは `user_id`。前の Intent で列の名前を誤り、H2 の誤りの表示にメールアドレスが出た。`project.md` の学び）。数え終えたら作業用のディレクトリを消す。

| 数えるもの | 見込み | 違ったとき |
|---|---|---|
| `flyway_schema_history` の最大の版 | 13 | 違えば止めて依頼者に諮る（入れ替えで移行は当たらない見込み） |
| `dsl_applied_revisions` の最新の行の本文に、文字列 `orders`・`customer_id`・`REFERENCE` が現れる回数（回数だけ） | どれも 1 回以上（適用中の DSL が既定の生成のもので、`orders.customer_id` が `REFERENCE`） | `REFERENCE` が 0 回なら、S4〜S6 の参照の流れが成り立たない。順 8 で依頼者に諮る（DSL は変えず、S4〜S6 を `customers` の作成・更新・削除に置き換えるかを決める） |
| `dsl_previews` の行の数 | 0 | 1 なら、スモークテストで DSL を触らない限りそのまま |
| `roles` の行の数 | 3（前の Intent の片付けの後） | 違えば記録し、確かめ用のロールの名前が重ならないかを依頼者と確かめる |
| `.env` の初期管理者（表示しない）の `user_role_assignments` の数と `group_role_assignments` 経由の割り当ての数、`work_role_selections` の行の有無 | 直接 0・グループ経由 3・保存 1（前の Intent の配備の後と同じ） | 違えば、作業ロールの切り替えの流れ（自動で選ばれるか）と `WORK_ROLE_SWITCHED` の期待が変わるため、順 8 で合わせ直す（`deployment-strategy.md` 3.2） |
| 初期管理者の `suspended`・`admin_flag` | 停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |
| `audit_events` の行の数（T0 の基準） | — | 配備の後の数えと比べる基準 |

## 5. 決まりと上流との差（記録）

- **配備の前の k6**: 3.2 のとおり、`project.md` の「試験済みのときだけ省く」と違い、試験の判定の回を流していないまま省く（Q2: B）。判定の持ち主は Performance Validation。
- **スモークテストで確かめない経路（Q1: A）**: 保存前の確かめの拒否（409 `BUSINESS_REFERENCE_NOT_FOUND`）と LOOKUP（参照ピッカー）は、配備した版では確かめない。409 は画面の選択からは作れず（選べるのは参照先にある値だけ）、LOOKUP は既定の DSL の生成で作られず DSL の投入・適用が要るため。どちらも結合テストと E2E（190・195、実際の Chromium）で確かめ済み。U2 の `cicd-pipeline.md` 3節 順 6 の「参照つきの作成の成功と拒否を確かめるか」は、成功の経路だけを確かめる形に決めた。
- **見本の対象DB に参照の項目を持つ表を足すか**（U1・U2・U3 の基盤の設計が残した点）: 足さない。見本の PG の外部キー `sales.orders.customer_id → sales.customers` と既定の DSL の生成（1カラムの外部キーを `select`・選択肢の元 `REFERENCE` にする。`DslTreeBuilder`）で足りるため。
- **戻しの単位**（U3 の `cicd-pipeline.md` 5節 R-01）: U1・U2・U3 は同じ WAR とイメージで一緒に戻る。U1・U2 に移行と `.env` の差が無いため、Intent の全体の戻しはイメージだけで一本になる（`rollback-runbook.md`）。
- **E2E**: 配備の前の E2E は、固定先の更新の後の 277 件で満たす（流し直さない）。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。版はコミットのハッシュ（アプリのコードは `cb23466b`、配備を行う記録は `23519159` 以降）で識別し、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/cd-config.md`（配備の前の複写と数え、配備の前の k6 の差）
- 質問と答え: `deployment-pipeline-questions.md`
- 上流: `construction/ci-pipeline/ci-config.md`・`quality-gates.md`、`construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（3節・4節、R-01）、`construction/u2-reference-write-check/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（3節・4節）、`construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（5節）、`construction/u4-perf-evidence-redactor/infrastructure-design/cicd-pipeline.md`、`construction/u5-container-perf-docs/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- Build and Test: `construction/build-and-test/build-and-test-summary.md`・`test-results.md`・`k6-trial-record.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`.env.example`、`backend/src/main/resources/application.yaml`、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`backend/src/main/java/cherry/mastersmith/businessdata/domain/BusinessDataAuditReason.java`、`backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`、`docker/targetdb/postgres/01-sample-schema.sql`、README（「内部DBのバックアップと戻し方」）、`git diff --stat main..develop`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment・Change Control・Corrections の学び）

## Assumptions & Open Questions

- [assumption] `.env` に2つの新しい項目は無く、既定の 5s・10s で動く（順 1 で項目名の有無だけを数える）。
- [assumption] 適用中の DSL は既定の生成のもので、`orders.customer_id` が `REFERENCE`。4.1 で数えて裏付ける。
- [assumption] 初期管理者のロールの割り当て（直接 0・グループ経由 3）と作業ロールの保存（1）は前の Intent の配備の後のまま。4.1 で数えて裏付け、違えば順 8 で合わせ直す。
- [assumption] 初期管理者の状態は前の配備のまま。救済の見込みは順 4 で依頼者に確かめ、4.1 で裏付ける。
- `23519159` の CI の結果は確認中（順 1・順 13 で確かめる）。
