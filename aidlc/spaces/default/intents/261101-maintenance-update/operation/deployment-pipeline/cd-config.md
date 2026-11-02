# 配備の流れの構成（cd-config）

Intent 261101-maintenance-update の配備の流れです。この Intent はメンテナンスで、Java 25 の最新のパッチ、依存と道具、サブモジュールの固定先、コンテナのイメージを上げ、前の Intent からの小さな直し（P1・T1〜T3）と README の書き直しを行いました。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の Intent の配備の手順（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/`）と、リポジトリの `Dockerfile`・`compose.yaml`・`docs/deployment.md`・`docs/internal-db.md` を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **上流**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い。CI の設定と関門は前の Intent の `construction/ci-pipeline/`（`261029-reference-lookup-cleanup`）のまま。この Intent の上流は Build and Test の記録（`construction/build-and-test/`）と要件の FR11 である（5節）。
- **決定**: `deployment-pipeline-questions.md`（Q1: A、Q2: A、Q3: A）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`sha256:d8a467b60f6f…`、前の Intent で配備した版、healthy）。見本の PG（`postgres:18.6@sha256:3a4264520e12…`）と Mailpit（`v1.33.0@sha256:9b3a463be153…`）が動いている。lgtm（`0.39.0`）は作っただけで止まっている。3つとも `compose.yaml` の記述とダイジェストまで一致する |
| 配備する版 | `develop` の先頭（この段を書いた時点で `dc21dfca`）。`main`（`80e1ec8c`）から 16 コミット先。アプリのコードの最後の変更は `5950d786`（④ 小さな直し）。その後のコミットは文書（⑤）・`.idea/`・`aidlc/` だけ |
| 変わるもの（配備に入る） | `Dockerfile` の JRE（`25.0.4.1_1` → `25.0.5_8`）。java-mustache-processor（0.1.0 → 0.1.1、メールのテンプレートの描画）。make-you-chic-ui の固定先（`cab4207` → `cae5fed`）と frontend の依存。P1（一覧の並びを、文字の型は先頭 100 字で比べる。R-01 の既知の振る舞いの変化）。T3（参照の確かめの WARN の `stage` が null のとき `UNKNOWN`）。`application.yaml` のコメント |
| 変わらないもの | Spring Boot（4.1.2）・H2（2.4.240）・HikariCP（7.0.2）の版、画面の機能と API の応答の形（NFR4）、`AuditEventType` |
| 配備に入らない変更 | 検査の道具の版（palantir-java-format・SpotBugs・JaCoCo・Gitleaks・OSV-Scanner・Playwright など）、CI、`perf/`、`docs/`・README、E2E、対象DB・Mailpit・lgtm・collector・k6 のイメージの固定先（見本の PG と Mailpit は Build and Test までに新しい版で作り直し済み） |
| 内部DB のスキーマ | **変更なし**（`backend/src/main/resources/db` の差 0） |
| `.env` | **変更なし**（`.env.example` はコメントの参照先だけの変更） |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-maintenance-update`（`d8a467b60f6f`） |
| 試験用のタグ | `mastersmith:perf-maintenance-update`（`0adebf1b4cb9`、Build and Test の k6 だけに使った）。順 13 の片付けの候補（Q3: A） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| JRE 25.0.5 | イメージの土台が替わる。`Dockerfile` の ENTRYPOINT の引数（最大ヒープ 50%・タイムゾーン・`-Dh2.compactThreads=1`）は変えない。起動の後に `java -version` で版を確かめる |
| java-mustache-processor 0.1.1 | 招待のメールの本文の描画に使う。スモークテストの S4 で、配備した版で1通描いて確かめる |
| make-you-chic-ui・frontend の依存 | 画面の静的なファイル。WAR に同梱される。S2 で画面の骨組みを確かめる |
| P1（一覧の並び） | MySQL で長い値のカラムを並べ替えても 1038（500）にならない。並びは文字の型を先頭 100 字で比べ、先頭 100 字が同じ値どうしは主キーの順になる（R-01）。S3 で並べ替えを確かめる |
| T3（ログの `stage`） | 参照の確かめの WARN の項目が増えるだけ。配備の手順には影響しない |
| 検査の道具・CI・文書 | 配備されない |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）を確かめる。verify・E2E・k6・CI は Build and Test の結果を正とし、流し直さない（3.1） | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar --rerun-tasks` で `develop` の先頭から WAR を作り直し、SHA-256 を記録して Build and Test の WAR（先頭 `fe3382c51f62d33e`）と比べる。違うときは、WAR の中の `BOOT-INF/lib` の一覧と `static/` のファイルの一覧を比べて、差がビルドの時刻だけかを確かめてから進む（その後にアプリのコードの変更は無い）。`Dockerfile` は手元の WAR を写すだけ（`project.md` の学び）。停止の前に行う。シェルに `MASTERSMITH_IMAGE_TAG` が残っていないこと（`env \| grep -c MASTERSMITH_IMAGE_TAG` が 0）を確かめる | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-maintenance-update`。付けた後に ID が `d8a467b60f6f` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる。順 5 の複写の数えで裏付ける | AI と依頼者 | — |
| 5 | 止めて配備の前の複写（Q2: A） | T0 を控え、`docker compose stop app`。内部DB を `~/.mastersmith-backup/`（権限 700）へ `…-pre-maintenance-update.tgz`（権限 600）として複写する（4節）。複写を読み取りで開き、4.1 の数を出す（値は表示しない） | AI | 依頼者の承認（アプリが止まるため。順 5〜7 をまとめて承認） |
| 6 | 入れ替え | `docker compose up -d --build --no-deps app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps app` で作り直す（`project.md` の学び）。見本の PG・Mailpit は作り直さない | AI | 順 5 の承認に含む |
| 7 | 健全性 | `deployment-strategy.md` の S1（healthy・`/actuator/health` の 200・JRE の版・起動のログ） | AI | — |
| 8 | 4.1 の結果での合わせ直し | 4.1 の数が前提と違うとき（初期管理者の状態・招待中の行）は、スモークテストの前に依頼者と合わせ直し、監査の期待を直す | AI と依頼者 | 依頼者の決定 |
| 9 | スモークテスト（Q1: A） | `deployment-strategy.md` の S2〜S5。★の操作は監査に残るため、行う前に期待の件数を伝える | AI と依頼者 | 監査に残る操作は前もって伝える |
| 10 | 配備の後の複写（Q2: A） | アプリを止めて内部DB を `…-after-maintenance-update.tgz`（権限 600）として複写し、すぐ起動し直す（約 10 秒、依頼者に伝えてから）。起動し直した後もイメージ ID の一致を確かめる。複写を読み取りで開き、`audit_events` の種類と結果ごとの件数を 4.1 の数と比べる（`deployment-strategy.md` の S6） | AI | 依頼者に伝えてから |
| 11 | 配備の記録のコミット | Deployment Execution の記録をコミットする | AI | 段の成果物の初稿と段の承認の時は尋ねずに行う。それ以外は提案して承認を得る |
| 12 | 片付けの前の `docker system df` | 片付けの前の `docker system df` を記録する | AI | — |
| 13 | 古いイメージの片付け（FR11.2・Q3: A） | 6節の範囲から候補を作り、1つずつ使うコンテナが 0 件（`docker ps -a --filter ancestor=<ID>` が 0 件）であることを確かめる。消す一覧を示して承認を得てから `docker rmi` で消す。名前の無いボリュームは残す。消した後に `docker system df` を記録する | AI | 依頼者の承認（消す一覧） |
| 14 | `main` への取り込み（FR11.3） | 前の取り込み（`80e1ec8c`）の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する。記録だけのコミットの CI も確かめる（新しい脆弱性の公表は、ソースを変えなくても次の CI で落ちる。`project.md` の学び）。`main` が `develop` の祖先であることを確かめ、fast-forward する。タグは付けない | AI | 依頼者の承認 |
| 15 | 取り込みの結果の追記 | 取り込みと push の結果を、`main` の後の `develop` だけのコミットで追記する | AI | 依頼者の承認（コミットの前に提案） |
| 16 | プッシュ | `origin` へ `develop` と `main` をプッシュするのは依頼者 | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは、順 5 から順 7 の healthy までと、順 10 の複写の間。前者は複写と数えが入るため見込み約 2〜3 分、後者は約 10 秒。WAR の作り直しは停止の前に置く。
- 開いているタブは、順 7 の後に読み込み直す（新しい画面のファイルを読むため）。
- 順 13 の片付けは、配備が完了してから行う（戻し先のタグ `mastersmith:pre-maintenance-update` は残す）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`45a5872a`） | 通過（27 分 34 秒。単体 2,809・結合 1,663〈SKIPPED 10。DB の種類別の前提〉・画面 238 ファイル 2,312、失敗 0） | `construction/build-and-test/test-results.md` |
| E2E | 277 件 passed（14.6 分、実際の Chromium、axe の違反 0、axe-core 4.14.0） | 同 |
| k6（負荷の試験） | 3種類の対象DB で 19 場面すべてが閾値を満たした。試験のイメージ `mastersmith:perf-maintenance-update` の WAR は、`45a5872a` から作った WAR と同じ SHA-256（`fe3382c5…`）で、配備と同じ上限で流した | 同 |
| CI | この Intent の 6 つの run はすべて1回目で成功（検査の job の最長 37 分 05 秒）。`82987fde`・`dc21dfca` は `aidlc/`・`.idea/` だけで、順 14 で試行ごとに確かめる | 同、`gh run list` |

- 配備の前の k6 は、`project.md` の学び（「Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのときは省く」）に当たるため流さない。前の Intent（`261029-reference-lookup-cleanup`）は判定の回を流さないまま省いたが、今回は決まりとの差は無い。

## 4. 内部DB の複写（Q2: A）

- 移行が無いため、戻し（イメージだけ）に複写は要らない。今回は、T0 と初期管理者の状態の数え（配備の前）と、監査の確かめ（配備の後）のために2回取り、どちらもバックアップを兼ねる。戻しの練習はしない。
- 取り方と数え方は `docs/internal-db.md` の「内部DBのバックアップと戻し方」「内部DBの複写を数える」のとおり。置き場は `~/.mastersmith-backup/`（権限 700、複写は 600）。`mktemp -d` の macOS の一時ディレクトリは使わない（colima の VM から見えない。`project.md` の学び）。取り方のイメージは、この Intent で上げた `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de9…`、数えの道具は `eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb7…`。

```bash
date -u +%Y-%m-%dT%H:%M:%SZ          # T0 を控える
docker compose stop app
mkdir -p ~/.mastersmith-backup && chmod 700 ~/.mastersmith-backup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce \
  tar czf /backup/mastersmith-data-$(date +%Y%m%d%H%M)-pre-maintenance-update.tgz -C /data .
chmod 600 ~/.mastersmith-backup/*-pre-maintenance-update.tgz
ls -l ~/.mastersmith-backup/          # ファイルができたこと・権限 600（中身は開かない）
# ここでは start しない（順 6 へ進む）
```

### 4.1 配備の前の複写で数えるもの

複写をホームの下の権限 700 の作業用のディレクトリに展開し、`docs/internal-db.md` の道具（`CountRows.java`、H2 2.4.240 の JDBC、読み取りだけ）で開いて、数だけを出す。値（メールアドレス・ロールの名前・DSL の本文）は表示しない。表と列の名前は道具が `INFORMATION_SCHEMA` で確かめ、無いときは誤りの文を出さない（前の Intent までに、列の名前の誤りで H2 の誤りの表示にメールアドレスが出たことがある。`project.md` の学び）。数え終えたら作業用のディレクトリを消す。

| 数えるもの | 見込み | 違ったとき |
|---|---|---|
| `flyway_schema_history` の最大の版 | 前の配備と同じ（入れ替えで移行は当たらない） | 違えば止めて依頼者に諮る |
| `audit_events` の種類（`event_type`）と結果（`result`）ごとの件数 | — | 配備の後の数え（順 10）と比べる基準 |
| 招待中（取り消し・使用済みでない）の招待の行の数 | 前の Intent の配備の後と同じ | S4・S5 の後に同じ数に戻ることを確かめる基準 |
| 初期管理者の停止の有無と管理の印（数だけ。どの利用者かは `.env` の値を表示せずに照らす） | 停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |

## 5. 決まりと上流との差（記録）

- **上流の段が無い**: この Intent の流れには CI Pipeline と Infrastructure Design の段が無い（scope `bugfix`）。段の定義の `consumes`（`ci-config`・`quality-gates`・`infrastructure-specification`・`cicd-pipeline`）はこの Intent の記録に無く、前の Intent の手順とリポジトリの `Dockerfile`・`compose.yaml`・`docs/`、Build and Test の記録を正とした（`project.md` の学び）。CI の設定（`.github/workflows/ci.yml`）は、③ で Actions の版と道具の版を上げただけで、段の並びと関門は前の Intent の `ci-config.md`・`quality-gates.md` のまま。
- **スモークテストで確かめない経路（Q1: A）**: 役割・権限・業務データの作成・更新・削除・参照の選択は、この Intent で変えていないため、配備した版では確かめない。どれも Build and Test の verify（結合テスト）と E2E 277 件で確かめ済み。
- **E2E**: 配備の前の E2E は Build and Test の 277 件で満たす（流し直さない）。

## 6. 片付けの範囲（FR11.2・Q3: A）

- **候補にするもの**:
  - (1) 名前（リポジトリ）が、このリポジトリの固定先（`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`perf/versions.env`・`docker/hikari-pool.sh`・`docs/`・`TargetDbImages`）と同じで、版かダイジェストが固定先と違うイメージ。
  - (2) `mastersmith` のタグのうち、新しい戻し先 `pre-maintenance-update` より古い `pre-reference-lookup-cleanup`（`e0ede6ddd5c4`）と、試験用の `perf-maintenance-update`（`0adebf1b4cb9`）。
- **残すもの**:
  - このリポジトリのどこにも固定していないイメージ（`alpine:3`・`busybox`・`curlimages/curl`・`otel/opentelemetry-collector-contrib:0.116.1`・`testcontainers/ryuk:0.14.0`。`ryuk` は verify のたびに Testcontainers が使う）。
  - `mastersmith:local` と戻し先 `mastersmith:pre-maintenance-update`。
  - 名前の無いボリューム（要件の Q4: A）。
- **書いた時点の見込みの候補**（Deployment Execution で `docker images --digests` と固定先を照らして作り直す）:
  - `eclipse-temurin` の古い 4 つ（`589ff4cc3f71`・`d9a39a236346`、`25.0.4_7-jdk-noble`・`25.0.4_7-jre-noble` のタグ）
  - `mysql` の古い 2 つ（`f51c9c01c16b`・`ade067ae2fb1`）、`mariadb`（`d4fdec0510ad`）、`postgres`（`5a5a84b19854`）、`axllent/mailpit`（`0790692be935`）、`grafana/otel-lgtm`（`75cdf892daff`）
  - `otel/opentelemetry-collector:0.162.0`（`310a800ad69e`）
  - `grafana/k6:2.3.0` の古いダイジェスト（`9c2dee7f8ed7`。固定先は同じ 2.3.0 の `2dd4756ec6e1`）
- **今の使用**（この段を書いた時点の `docker system df`）: イメージ 29 個・17.35GB（回収できる 11.78GB）、ボリューム 75 個・14.66GB（回収できる 13.84GB。残す）、ビルドのキャッシュ 1.478GB。

## 7. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。
- 版はコミットのハッシュで識別する。アプリのコードは `5950d786`、配備を行う記録は `dc21dfca` 以降。
- どのコミットをリリースしたかは、`main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/`（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）、同じ Intent の `construction/ci-pipeline/ci-config.md`・`quality-gates.md`
- 質問と答え: `deployment-pipeline-questions.md`
- 要件: `inception/requirements-analysis/requirements.md`（FR11.1〜FR11.3、NFR4）・`requirements-analysis-questions.md`（Q4）
- Build and Test: `construction/build-and-test/build-and-test-summary.md`・`test-results.md`、`construction/code-generation/code-summary.md`（P1・T1〜T3、R-01）
- リポジトリ: `Dockerfile`、`compose.yaml`、`.env.example`、`perf/versions.env`、`gradle/libs.versions.toml`、`backend/gradle.lockfile`、`backend/src/main/resources/application.yaml`、`docs/deployment.md`（「戻し方」）、`docs/internal-db.md`（「内部DBのバックアップと戻し方」「内部DBの複写を数える」）、`git diff --stat main..develop`、`docker inspect`・`docker images`・`docker system df`（読み取り）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment・Change Control・Corrections の学び）

## Assumptions & Open Questions

- [assumption] `.env` のメールの接続先・差出人・ベース URL の項目には値が入っている（項目の有無だけを数えた。値は見ていない）。そのため招待のメールは Mailpit に届く。
- [assumption] 初期管理者の状態は前の配備のまま。救済の見込みは順 4 で依頼者に確かめ、4.1 で裏付ける。
- [assumption] WAR を作り直しても、中身は Build and Test の WAR と同じ（その後にアプリのコードの変更は無い）。順 2 で SHA-256 を比べて確かめる。
- `82987fde`・`dc21dfca` の CI の結果は、順 14 で試行ごとに確かめる。
