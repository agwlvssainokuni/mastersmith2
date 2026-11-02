# Deployment Pipeline の質問（Intent 261101-maintenance-update）

この Intent はメンテナンスです。Java 25 の最新のパッチ、依存と道具の更新、サブモジュールの固定先の更新、コンテナのイメージの更新、前の Intent からの小さな直し（P1・T1〜T3）、README を `docs/` に分ける書き直しを行いました。この質問は、その配備の流れを決めるためのものです。

前の Intent（`261029-reference-lookup-cleanup`）の配備の手順（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）と、既にある `Dockerfile`・`compose.yaml`・`docs/deployment.md` を正とします。質問は、今回の差で判断が分かれる点だけです（`project.md` の Deployment の学び）。CI Pipeline と Infrastructure Design の段はこの Intent の流れに無いため、上流は Build and Test の記録とリポジトリです。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。1つのコンテナを入れ替える形（ブルーグリーン・カナリアは使わない） | `team.md`・`project.md` の Deployment |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`sha256:d8a467b60f6f…`、前の Intent で配備した版）で healthy。見本の PG（`postgres:18.6@sha256:3a4264520e12…`）と Mailpit（`v1.33.0@sha256:9b3a463be153…`）が動いている。lgtm（`0.39.0`）は作っただけで止まっている。どれも `compose.yaml` の記述とダイジェストまで一致する | `docker inspect`（読み取り） |
| 配備する版 | `develop` の先頭（今は `dc21dfca`）。`main`（`80e1ec8c`）から 16 コミット先。アプリのコードの最後の変更は `5950d786`（④ 小さな直し）。その後のコミットは文書・`.idea/`・`aidlc/` だけ | `git log main..develop` |
| 配備に入る変更 | Dockerfile の JRE（`25.0.4.1_1` → `25.0.5_8`）、java-mustache-processor（0.1.0 → 0.1.1、メールのテンプレートの描画）、make-you-chic-ui（`cab4207` → `cae5fed`）、frontend の依存、P1（一覧の並びを文字の型は先頭 100 字で比べる。R-01 の既知の振る舞いの変化）、T3（参照の確かめの WARN の `stage` が null のとき `UNKNOWN`）、`application.yaml` のコメント。Spring Boot（4.1.2）・H2・HikariCP の版は変わらない | `git diff main..develop`、`code-summary.md` |
| 内部DB の移行 | **無い**（`backend/src/main/resources/db` の差 0） | `git diff --stat` |
| `.env` | **変えない**。`.env.example` はコメントの参照先だけの変更 | `git diff main..develop -- .env.example` |
| 監査の種類 | `AuditEventType` に変更は無い | `git diff --stat` |
| WAR | `develop` の先頭から作り直し（`./gradlew :backend:bootWar --rerun-tasks`）、SHA-256 を記録する。Build and Test の WAR（先頭 `fe3382c51f62d33e`、`45a5872a` から作成）と比べる（その後にアプリのコードの変更は無い）。WAR の中の JRE は無く、JRE はイメージの側で替わる | `project.md` の学び（WAR の確かめ） |
| 入れ替え | 入れ替えの前に今のイメージ（`d8a467b60f6f`）に戻し先のタグ `mastersmith:pre-maintenance-update` を付ける。`docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID の一致を確かめ、食い違えば `--force-recreate --no-deps` で作り直す。イメージの中の JRE の版（`java -version`）が 25.0.5 であることも確かめる | `project.md` の学び、FR11.1 |
| 配備の前の k6 | **流さない**。Build and Test が、配備するものと同じソースの WAR（`fe3382c5…`）のイメージ `mastersmith:perf-maintenance-update` を、配備と同じ上限で 19 場面すべて流して満たした | `project.md` の学び（試験済みのときは省く）、`test-results.md` |
| 配備の前の検査 | verify・E2E・CI は Build and Test の結果を正とし流し直さない（verify 通過・E2E 277 件 passed・この Intent の 6 つの CI はすべて1回目で成功） | `test-results.md` |
| 戻し | **イメージだけ**（`mastersmith:pre-maintenance-update` で `app` を作り直す）。`.env` は変えない。戻すと JRE が 25.0.4.1 に、メールのテンプレートの部品が 0.1.0 に、一覧の並びが前の形（MySQL で長い値のカラムを並べ替えると 1038 の 500 が出うる）に戻る | FR11.1、NFR4 |
| 古いイメージの片付け | 配備の後（Deployment Execution の終わり）に行う。使うコンテナが 0 件であることを1つずつ確かめ、消す一覧の承認を得てから消す。名前の無いボリュームは残す。前後の `docker system df` を記録する（今はイメージ 17.35GB のうち回収できる 11.78GB） | FR11.2（要件の Q4: A） |
| `main` への取り込み | 前の取り込み（`80e1ec8c`）の後のすべてのコミットの CI を試行ごとに確かめてから、依頼者の承認を得て fast-forward で行う。タグは付けない。`git push` は依頼者が行う | FR11.3、`team.md` |
| 秘密・個人に関する値 | `.env` の値、初期管理者のメールアドレス、業務データの行の値を表示しない。ログと複写は件数・キーの名前だけで見る。内部DB の複写はリポジトリの外（`~/.mastersmith-backup/`、置き場 700・ファイル 600） | `project.md` の Forbidden と学び |

## Q1. スモークテストの中身

今回は画面の機能と API の形を変えていません（NFR4）。一方で、実行環境（JRE）、メールのテンプレートの部品（java-mustache-processor 0.1.1）、画面の部品（make-you-chic-ui）、一覧の並び（P1）が変わりました。

調べた事実:

- `.env` には、メールの接続先（`SPRING_MAIL_HOST`）と差出人（`MASTERSMITH_MAIL_FROM`）の項目に値が入っている（項目の有無だけを数えた。値は見ていない）。Mailpit は動いている。そのため、招待のメールは Mailpit に届く（実在の宛先・外部の SMTP には送らない）。
- 招待の発行と取り消しは監査に残る（`INVITATION_ISSUED`・`INVITATION_CANCELLED`）。取り消した招待の行は内部DB に残る。
- 業務データの一覧・詳細の読み取りは監査に残らない。

**推奨: A。** 理由:

- 0.1.1 の部品は、メールの本文の描画にだけ使われます。A では、配備した版で実際にメールを1通描いて確かめます。
- 一覧の並び（R-01）は、画面で並べ替えて確かめます。
- 監査に残る操作は、招待の発行と取り消しの2件だけです。
- C は前の Intent の流れ（ロールの作成から業務データの作成・更新・削除まで）で、今回変えていない機能を重ねて見るだけになります。

A の流れ:

| 順 | 項目 | 誰が | 確かめ |
|---|---|---|---|
| S1 | healthy・起動のログ・JRE の版 | AI | healthy と `/actuator/health` の 200。ERROR 0・Flyway の `Migrating schema` 0 件・救済の WARN 0。`java -version` が 25.0.5 |
| S2 | ログインと画面の骨組み | 依頼者 | ログインでき、サイドバー・トップバーが出る（make-you-chic-ui の更新） |
| S3 | 業務データの一覧の並び（R-01） | 依頼者 | `customers` の一覧で、文字のカラムの見出しを押して昇順・降順に並べ替えられ、詳細が開く |
| S4 | ★招待のメール | 依頼者 | 架空の宛先（`smoke-maintenance@example.com`）に招待を発行し、Mailpit の画面で件名と本文が出ること、本文に `{{` が残っていないことを確かめる。招待の URL は開かない |
| S5 | ★招待の取り消し | 依頼者 | S4 の招待を取り消し、一覧で取り消し済みになる |
| S6 | 監査の確かめ | AI | 配備の後の内部DB の複写（Q2）で、T0 以降の監査を種類と結果ごとに数える |

A の監査の期待: `INVITATION_ISSUED` 1・`INVITATION_CANCELLED` 1・`LOGIN_SUCCEEDED` はログインした回数（0 もありうる）。`FAILURE`・`UNKNOWN` は 0。★の操作の前に依頼者に伝え、予定に無い操作をしたときはその場で聞いて期待を合わせる。

A. 上の S1〜S6（招待のメールで 0.1.1 の描画、業務データの一覧で R-01、画面の骨組みで make-you-chic-ui を確かめる。監査に残るのは招待の2件とログイン）
B. 読み取りだけにする（S1〜S3）。監査に残るのはログインだけ。メールの描画は結合テストと E2E で確かめ済みとし、差を `cd-config.md` に記録する
C. 前の Intent と同じ流れ（ロール「スモーク確認乙」の作成から、業務データの作成・更新・削除と片付けまで）に、S4・S5 を足す
X. Other (please specify)

[Answer]: A

## Q2. 内部DB の複写

移行が無いため、戻し（イメージだけ）に複写は要りません。前の Intent では、配備の前（前提と T0 の数え）と配備の後（監査の確かめ）の2回取り、どちらもバックアップを兼ねました。

**推奨: A。** 理由:

- 配備の前の複写は、入れ替えの停止に重ねられるため、止まる時間はほぼ増えません。
- 配備の後の複写は、Q1 の A・C で監査の件数を数えるのに要ります。
- 取り方の道具（`eclipse-temurin` の JRE のイメージ）は、新しい版 `25.0.5_8` のダイジェストに替えます。

A. 配備の前（入れ替えの停止の間）と配備の後（スモークテストの後）の2回取る。前は T0 と初期管理者の状態（救済の見込み）の数え、後は監査の確かめに使い、どちらもバックアップを兼ねる（ファイル 600）。戻しの練習はしない
B. 配備の前の1回だけ取る（バックアップと T0）。監査は確かめない（Q1 が B のとき）
C. 取らない
X. Other (please specify)

[Answer]: A

## Q3. 片付けの候補の範囲（FR11.2）

要件は「`compose.yaml` などの固定先から外れた古い版のイメージ」と「`mastersmith` の戻し先より古いタグ」です。ただし、この PC には、このリポジトリのどこにも固定していないイメージもあります（`alpine:3`・`busybox`・`curlimages/curl`・`otel/opentelemetry-collector-contrib:0.116.1`・`testcontainers/ryuk:0.14.0`）。消す一覧は Deployment Execution で示して承認を得ます。ここでは、どの範囲から候補を作るかを決めます。

今の時点の見込みの候補（A の範囲）:

- `mastersmith` のタグ: `pre-reference-lookup-cleanup`（`e0ede6ddd5c4`。新しい戻し先 `pre-maintenance-update` より古い）と、試験用の `perf-maintenance-update`（`0adebf1b4cb9`。Build and Test の k6 だけに使った）。
- 名前（リポジトリ）が固定先と同じで、版かダイジェストが固定先と違うイメージ:
  - `eclipse-temurin` の古い 4 つ
  - `mysql` の古い 2 つ、`mariadb`・`postgres`・`axllent/mailpit`・`grafana/otel-lgtm` の古い各1つ
  - `otel/opentelemetry-collector:0.162.0`
  - `grafana/k6:2.3.0` の古いダイジェスト（`9c2dee7f8ed7`。固定先は同じ 2.3.0 の `2dd4756ec6e1`）

**推奨: A。** 理由:

- 要件の「固定先から外れた古い版」に当たるのは、名前が固定先と同じイメージです。
- 固定していないイメージは、ほかのプロジェクトや道具が使うことがあります。たとえば `ryuk` は Testcontainers の部品で、verify のたびに使われます。
- 今は使うコンテナが 0 件でも、消すと次の実行で取り直しになります。

A. 名前が固定先と同じで版かダイジェストが違うイメージと、`mastersmith` の上の2つのタグを候補にする。固定していないイメージは残す
B. A に加えて、固定していないイメージのうち使うコンテナが 0 件のもの（`ryuk` を除く）も候補にする
C. A から `mastersmith:perf-maintenance-update` を外す（試験のイメージを残す）
X. Other (please specify)

[Answer]: A
