# 配備の流れの構成（cd-config）

Intent 261104-tz-options-perf の配備の流れです。この Intent は bugfix で、表示のタイムゾーンの選択肢の組み立てを Temporal を使って速くしました（`develop` の 985b7e6c）。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の配備の手順（`aidlc/spaces/default/intents/261101-maintenance-update/operation/deployment-pipeline/`）と、リポジトリの `Dockerfile`・`compose.yaml`・`docs/deployment.md`・`docs/internal-db.md` を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **上流**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い。CI の設定と関門は前の記録（`261029-reference-lookup-cleanup` の `construction/ci-pipeline/`）のまま。この Intent の上流は Build and Test の記録（`construction/build-and-test/`）と、配備を引き受ける Intent 261102 の記録である（5節）。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q4・F1・F2 すべて A）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:ffd2b8380460…`、Intent 261101 で配備した版、healthy）。見本の PG（`postgres:18.6`）と Mailpit（`v1.33.0`）が動いている |
| 配備する版 | `develop` の先頭（この段を書いた時点で `dca49bf2`）。`main`（`f388fb8d`、261101 の配備）から 49 コミット先。アプリのコードの最後の変更は `985b7e6c`（この Intent）。その後のコミットは `aidlc/` だけ |
| 引き受ける配備（Q1: A） | Intent 261102-timezone-ui-polish の変更のすべて。あの Intent の範囲（classic）は配備の段を初めから飛ばす形だったため、まだ配備も `main` への取り込みもしていない。この Intent の直しは 261102 の部品の上にあり、分けて配備できない |
| 変わるもの（配備に入る） | 261102: 利用者ごとの表示のタイムゾーン（プリファレンスの画面の選択と保存、`/api/me/preferences` の `displayTimeZone`）、管理の画面と業務データの画面の時刻の表示・入力・検索（表示のタイムゾーン・夏時間・時差の表記）、対象DB の日時の型の受け渡し（サーバーの `ColumnValueCodec` など）、アプリのアイコンと favicon、make-you-chic-ui の固定先の更新、内部DB の移行 V14。261104: 選択肢の組み立て（`offsetLabel.ts`・`buildTimeZoneOptions.ts`） |
| 変わらないもの | `Dockerfile`・`compose.yaml`・`.env.example`（`main..develop` の差 0）、Spring Boot・H2・HikariCP・JRE の版 |
| 配備に入らない変更 | E2E・`perf/`・検査の道具と ESLint の規則・`docs/`・`aidlc/` |
| 内部DB のスキーマ | **V14 が1つ**（`users` に空を許す列 `display_time_zone VARCHAR(64)` を足すだけ。既定値・制約・索引なし。前進のみ・後方互換） |
| `.env` | **変更なし** |
| 監査の種類 | 変更なし。表示のタイムゾーンの保存は監査に残らない（当たる `AuditEventType` が無い） |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-tz-options-perf`（`ffd2b8380460`） |
| 片付けの候補（Q4: A） | `mastersmith:pre-maintenance-update`（`d8a467b60f6f`、新しい戻し先より古い）と `mastersmith:perf-tz-bt`（`3a3fd38094b8`、261102 の Build and Test の k6 だけに使った） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| V14 の移行 | 起動のときに Flyway が1回当てる。配備の前に内部DB を複写する（4節）。起動のログで `Migrating schema` が1件・版 14 になることを確かめる |
| 表示のタイムゾーン（261102 の U2） | 既存の利用者の列は空（「ブラウザに合わせる」）のまま。スモークテストの S3・S4 で選択・保存・表示の切り替えを確かめる |
| 時刻の表示・入力・検索（261102 の U3・U4） | 業務データの一覧・詳細・作成と更新の欄・検索の範囲の時刻（見本の PG の `sales.customers.created_at` は `timestamptz`）。PostgreSQL の接続を作るときに `SET TIME ZONE 'UTC'` を足した（U3）。既定の DSL の生成で端数のある列に `dbType.precision` が出る（U3）。`deployment-strategy.md` の S4・S5 で確かめる |
| ホームのカード・メニューのアイコン・ロゴ・`/admin` の廃止（261102 の U5） | ホームとサイドバーの表示。`/admin` は「見つからない」になる。S6 で確かめる |
| アイコンと favicon（261102 の U6） | 画面の静的なファイル。`/favicon.ico`・`/favicon.svg` を未認証で返す。S7 で確かめる |
| 選択肢の組み立て（261104） | Chrome・Edge・Firefox（Temporal を持つ版）は Temporal の道、Safari は Intl の道。S3 と S8 でブラウザごとに確かめる（Q3: A） |
| make-you-chic-ui の固定先 | 画面の部品。S2 で画面の骨組みを確かめる |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）を確かめる。verify・E2E・k6・CI は Build and Test の結果を正とし、流し直さない（3.1） | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で `develop` の先頭から WAR を作り直し、SHA-256 を記録する。WAR の中に `db/migration/V14__u2_display_time_zone.sql` があり、`static/` の画面の資産の名前が今の `frontend/dist` と同じこと（`offsetLabel` の入った版）を確かめる。`Dockerfile` は手元の WAR を写すだけ（`project.md` の学び）。停止の前に行う。シェルに `MASTERSMITH_IMAGE_TAG` が残っていないこと（`env \| grep -c MASTERSMITH_IMAGE_TAG` が 0）を確かめる | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-tz-options-perf`。付けた後に ID が `ffd2b8380460` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。順 5 の複写の数えで裏付ける | AI と依頼者 | — |
| 5 | 止めて配備の前の複写（Q2: A） | T0 を控え、`docker compose stop app`。内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-pre-tz-options-perf.tgz`（権限 600）として複写する（4節）。複写を読み取りで開き、4.1 の数を出す（値は表示しない） | AI | 依頼者の承認（アプリが止まり、移行が当たるため。順 5〜7 をまとめて承認） |
| 6 | 入れ替え | `docker compose up -d --build --no-deps app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps app` で作り直す（`project.md` の学び）。見本の PG・Mailpit は作り直さない | AI | 順 5 の承認に含む |
| 7 | 健全性 | `deployment-strategy.md` の S1（healthy・`/actuator/health` の 200・V14 の当たり・起動のログ） | AI | — |
| 8 | 4.1 の結果での合わせ直し | 4.1 の数が前提と違うとき（初期管理者の状態など）は、スモークテストの前に依頼者と合わせ直し、監査の期待を直す | AI と依頼者 | 依頼者の決定 |
| 9 | スモークテスト（Q3・F1・F2: A） | `deployment-strategy.md` の S2〜S9。監査に残る操作は、ログイン・DSL の生成と破棄・業務データの作成・更新・削除（`deployment-strategy.md` の 3.2）。行う前に期待の件数を伝え、予定に無い操作をしたときはその場で聞いて期待を合わせる | AI と依頼者 | 監査に残る操作は前もって伝える |
| 10 | 配備の後の複写（Q2: A） | アプリを止めて内部DB を `…-after-tz-options-perf.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒、依頼者に伝えてから）。起動し直した後もイメージ ID の一致を確かめる。複写を読み取りで開き、`audit_events` の種類と結果ごとの件数を 4.1 の数と比べ、`display_time_zone` に値の入った行の数を数える（`deployment-strategy.md` の S10） | AI | 依頼者に伝えてから |
| 11 | 戻しの練習（Q2: A） | `rollback-runbook.md` の 3節。配備の後の複写を使い捨てのボリュームへ展開し、戻し先 `mastersmith:pre-tz-options-perf` を使い捨てのコンテナで起動して、healthy と起動のログを確かめる。動いているアプリと内部DB には触れない。終わったら使い捨てのコンテナ・ボリューム・作業用の展開を消す | AI | 依頼者に伝えてから |
| 12 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 段の成果物の初稿と段の承認の時は尋ねずに行う。それ以外は提案して承認を得る |
| 13 | 古いイメージの片付け（Q4: A） | 片付けの前の `docker system df` を記録する。1節の2つの候補について、使うコンテナが 0 件（`docker ps -a --filter ancestor=<ID>` が 0 件）であることを確かめ、消す一覧を示して承認を得てから `docker rmi` で消す。消した後に `docker system df` を記録する | AI | 依頼者の承認（消す一覧） |
| 14 | `main` への取り込み | 前の取り込み（`f388fb8d`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する。記録だけのコミットの CI も確かめる（`project.md` の学び）。この範囲には、261102 の途中で失敗した run（`f2f2eb9b` の 41823570241。この Intent が直した性能のテスト）が含まれ、その記録も載せる。`main` が `develop` の祖先であることを確かめ、fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 15 | 取り込みの結果の追記 | 取り込みと push の結果を、`main` の後の `develop` だけのコミットで追記する | AI | 依頼者の承認（コミットの前に提案） |
| 16 | プッシュ | `origin` へ `develop` と `main` をプッシュするのは依頼者 | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは、順 5 から順 7 の healthy までと、順 10 の複写の間。前者は複写と数えと移行が入るため見込み約 2〜3 分、後者は約 10 秒。
- 開いているタブは、順 7 の後に読み込み直す（新しい画面のファイルを読むため）。
- 順 13 の片付けは、配備が完了し、戻しの練習が済んでから行う（戻し先のタグ `mastersmith:pre-tz-options-perf` は残す）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（この Intent、`fix/tz-options-perf`） | 通過（29 分 4 秒。単体 2,936・結合 1,716〈飛ばし 10。DB の種類別の前提〉・画面 274 ファイル 3,063、失敗 0） | `construction/build-and-test/test-results.md` |
| E2E | 298 件 passed（15.6 分、実際の Chromium、flaky 0） | 同 |
| k6（負荷の試験） | 261102 の Build and Test で、PostgreSQL の 19 の行と MySQL・MariaDB の 9 場面 × 2 種類がすべて 2 回とも Met（試験のイメージ `mastersmith:perf-tz-bt`、WAR `e2563954…`）。この Intent の差は画面の側の関数だけで、サーバーの処理を変えていない | `261102-timezone-ui-polish/construction/build-and-test/evidence/perf-results.md` |
| CI | この Intent の push の後の run（`2c01c33c`・`985b7e6c`）はどちらも1回目で成功（検査の job の最長 38 分 14 秒）。前の取り込みの後のすべての run は順 14 で試行ごとに確かめる | `construction/build-and-test/test-results.md` |

- 配備の前の k6 は、`project.md` の学び（「Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは省く」）に当てて流さない。ただし、試験の WAR は 261102 の `2a6e05f7` から作ったもので、この Intent の直し（画面の側の JavaScript だけ）を含まない。サーバーの処理と内部DB は同じため、負荷の結果は変わらないと判断した（差の記録）。

## 4. 内部DB の複写（Q2: A）

- V14 が当たるため、配備の前の複写は移行の前のバックアップを兼ねる（戻しの第二の手。`rollback-runbook.md` の 4節）。配備の後の複写は、監査の確かめと戻しの練習に使う。
- 取り方と数え方は `docs/internal-db.md` の「内部DBのバックアップと戻し方」「内部DBの複写を数える」のとおり。置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）。`mktemp -d` の macOS の一時ディレクトリは使わない（colima の VM から見えない。`project.md` の学び）。取り方のイメージは前の配備と同じ `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de9…`（`Dockerfile` は変わっていない）。

```bash
date -u +%Y-%m-%dT%H:%M:%SZ          # T0 を控える
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-pre-tz-options-perf.tgz -C /data .
chmod 600 ~/.mastersmith-backup/*-pre-tz-options-perf.tgz
ls -l ~/.mastersmith-backup/          # ファイルができたこと・権限 600（中身は開かない）
# ここでは start しない（順 6 へ進む）
```

### 4.1 配備の前の複写で数えるもの

複写をホームの下の権限 700 の作業用のディレクトリに展開し、`docs/internal-db.md` の道具（H2 2.4.240 の JDBC、読み取りだけ）で開いて、数だけを出す。値（メールアドレス・ロールの名前・DSL の本文）は表示しない。表と列の名前は道具が `INFORMATION_SCHEMA` で確かめ、無いときは誤りの文を出さない（`project.md` の学び）。数え終えたら作業用のディレクトリを消す。

| 数えるもの | 見込み | 違ったとき |
|---|---|---|
| `flyway_schema_history` の最大の版 | 13（V14 はまだ当たっていない） | 違えば止めて依頼者に諮る |
| `users` に列 `display_time_zone` があるか | 無い | あれば止めて諮る |
| `audit_events` の種類（`event_type`）と結果（`result`）ごとの件数 | — | 配備の後の数え（順 10）と比べる基準 |
| 初期管理者の停止の有無と管理の印（数だけ。どの利用者かは `.env` の値を表示せずに照らす） | 停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |

## 5. 決まりと上流との差（記録）

- **上流の段が無い**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い（scope `bugfix`）。段の定義の `consumes`（`ci-config`・`quality-gates`・`infrastructure-specification`・`cicd-pipeline`）はこの Intent の記録に無く、前の配備の手順とリポジトリの `Dockerfile`・`compose.yaml`・`docs/`、Build and Test の記録を正とした（`project.md` の学び）。CI の設定（`.github/workflows/ci.yml`）は `main..develop` で変わっていない。
- **261102 の配備を引き受けた（Q1: A）**: 261102 の範囲（classic）は配備の段を初めから飛ばす形で、あの Intent の記録には配備の段の成果物が無い。あの Intent の Infrastructure Design は、U1〜U4 の `infrastructure-design/cicd-pipeline.md` に配備の見込み（WAR に同梱、戻しは前の版のイメージ、V14 は前進のみ）を書いており、この文書はそれと食い違わない。
- **261102 が配備の段に回した確かめを引き受けた**:
  - 1つ前の版のアプリを V14 の後の内部DB で起動する確かめ（`docs/deployment.md` の V14 の項）→ 順 11 の戻しの練習（Q2: A）。
  - Firefox・Safari での画面の確かめ（261102 の U1 の `infrastructure-design/cicd-pipeline.md`、Unverified）→ `deployment-strategy.md` の S5（Q3: A）。
- **この Intent の残りを引き受けた**: Build and Test で Safari などの Intl の道は jsdom の単体テストでしか確かめていない。S5 の Safari で実際の画面を確かめる。
- **スモークテストの範囲（F1: A）**: 261102 の U2〜U6 の設計（各単位の `infrastructure-design/cicd-pipeline.md` の「スモークテスト」と U6 の `nfr-design/security-design.md` の SD4.3）が配備の段に回した確かめを、すべて `deployment-strategy.md` に入れた。項目の番号は単位ごとの番号から振り直し、対応を `deployment-strategy.md` の 3.3 に書いた。
- **監視の区画の代わり（F2: A）**: U4・U5 の S7（手元の監視 lgtm のダッシュボードで 5xx を見る）は、lgtm を起動せず、スモークテストの間のアプリのログで業務データとメニューの API の 5xx と ERROR を数えて代える。監視の設定（`docker/`）は `main..develop` で変わっておらず、colima の VM のメモリの余裕が少ない（U2 の記録で約 100 MiB）ため。
- **スモークテストで確かめない経路**: 役割・権限・参照の選択・招待とメールは、261102 とこの Intent で変えていない（時刻の型の受け渡しを除く）。どれも Build and Test の verify（結合テスト）と E2E 298 件で確かめ済み。U2 の S6（無効な名前の拒否）は画面から作れないため省き、結合テストと 261102 の k6 の `invalid` で確かめ済みとする（U2 の設計のとおり）。
- **実行時のタイムゾーンの設定（4 か所）**: Dockerfile の `-Duser.timezone=Asia/Tokyo`、`compose.yaml` の `TZ: Asia/Tokyo`、テストの JVM の `user.timezone`（`backend/build.gradle.kts`）、E2E の起動は、`main..develop` で変わっていない。E2E のブラウザに同じ値 `timezoneId: 'Asia/Tokyo'` を明示する設定（`frontend/playwright.config.ts`）が足された（261102 の U2）。利用者ごとの表示のタイムゾーンは画面の側の値で、サーバーの JVM のタイムゾーンとは別。保存する時刻は UTC の時点のまま（U2 の設計の H12。Deployment Execution の記録にも書く）。
- **E2E**: 配備の前の E2E は Build and Test の 298 件で満たす（流し直さない）。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。
- 版はコミットのハッシュで識別する。アプリのコードは `985b7e6c`、配備を行う記録は `dca49bf2` 以降。
- どのコミットをリリースしたかは、`main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261101-maintenance-update/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）
- 引き受けた Intent: `aidlc/spaces/default/intents/261102-timezone-ui-polish/aidlc-state.md`（範囲と飛ばした段）、同じ Intent の `construction/u1-time-zone-conversion/infrastructure-design/cicd-pipeline.md`、`construction/build-and-test/build-and-test-summary.md`・`evidence/perf-results.md`
- 質問と答え: `deployment-pipeline-questions.md`（Q1〜Q4・F1・F2）
- 手順の続き: `deployment-strategy.md`（起動の確かめとスモークテスト）・`rollback-runbook.md`（戻しと戻しの練習）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`・`test-results.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`.env.example`、`docs/deployment.md`（「戻し方」と V14 の項）、`docs/internal-db.md`、`backend/src/main/resources/db/migration/V14__u2_display_time_zone.sql`、`backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`、`docker/targetdb/postgres/01-sample-schema.sql`、`git log`・`git diff --stat main..develop`、`docker ps`・`docker images`・`docker inspect`（読み取り）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment・Change Control・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 初期管理者の状態は前の配備のまま。救済の見込みは順 4 で依頼者に確かめ、4.1 で裏付ける。
- [assumption] 配備したアプリで適用中の DSL に `sales.customers` が載り、`created_at` が一覧・詳細・作成と更新の欄・検索の対象になっている。載っていなければ S4・S5 は成り立たないため、Deployment Execution で、`sales.customers` を含む DSL を使い捨ての確かめとして適用してよいかを依頼者に諮る（勝手に適用しない。U4 の設計のとおり）。
- [assumption] 見本の対象DB に書き込みのアカウントがあり、アプリの `.env` に書き込みの接続の項目がある（項目の有無だけを見る）。依頼者の利用者が `sales.customers` の作成・更新・削除の権限を持つ。
- [assumption] Firefox の版が Temporal を持つかは、S5 の前に依頼者のブラウザの版で確かめる（持たない版なら Intl の道を通る。どちらの道でも同じ表示になることを確かめる）。
