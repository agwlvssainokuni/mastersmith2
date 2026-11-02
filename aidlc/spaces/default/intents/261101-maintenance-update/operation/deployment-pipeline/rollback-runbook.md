# 戻しの手順書（rollback-runbook）

Intent 261101-maintenance-update の配備を戻す手順です。`docs/deployment.md` の「戻し方」と、前の手順（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差を中心に書きます。今回はスキーマも `.env` も変わらないため、戻しは**イメージだけ**で、戻しの練習は行いません（`cd-config.md` の 4節）。

## 1. 戻すきっかけ

- 起動が healthy にならない、ERROR が出続ける、ログに秘密・メールアドレスそのもの・業務データの値が出る、スモークテスト（`deployment-strategy.md` の S1〜S6）が通らない。
- 招待のメールの本文が崩れる（`{{` が残る・件名が空）。
- 一覧の並べ替えが 5xx になる。
- JRE の版の差による想定外の動き（起動の失敗・メモリの使い方の変化）。
- 戻すかどうかは依頼者に諮って決める。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（5節）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-maintenance-update`（イメージ ID `d8a467b60f6f…`、前の Intent `261029-reference-lookup-cleanup` で配備した版）。
- 内部DB はそのまま使い、スキーマは戻さない（この Intent は移行を足していない）。
- `.env`・`.env.targetdb`・見本の PG・Mailpit は戻さない。見本の PG と Mailpit は Build and Test までに新しい版で作り直し済みで、前の版のアプリからも同じ形で使える（対象DB の接続と SMTP の口は変えていない）。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-maintenance-update --format '{{.Id}}'   # sha256:d8a467b60f6f… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（docs/internal-db.md の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz、置き場は ~/.mastersmith-backup/、複写は権限 600）
MASTERSMITH_IMAGE_TAG=pre-maintenance-update docker compose up -d --no-build --no-deps app
MASTERSMITH_IMAGE_TAG=pre-maintenance-update docker compose ps app   # healthy を待つ
docker inspect mastersmith-app-1 --format '{{.Image}}'   # sha256:d8a467b60f6f… であること。違えば --force-recreate を足して作り直す
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-maintenance-update` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- **コマンドの外でこの変数を `export` したままにしないでください。** 戻した後は `unset MASTERSMITH_IMAGE_TAG` で消し、`env | grep -c MASTERSMITH_IMAGE_TAG` が 0 であることを確かめる。
- 入れ替えの後は、コンテナのイメージ ID が戻し先の ID と一致することを `docker inspect` で確かめる（構成が同じだと作り直されず古いイメージのまま起動することがある。`project.md` の学び）。
- 戻し先のタグのイメージが無いときは、`docs/deployment.md` の「戻し方」のとおり、`main`（`80e1ec8c`）を `git worktree add` で取り出して WAR とイメージを作り直す。
- 開いているタブを読み込み直す（前の版の画面のファイルを読むため）。
- **片付け（`cd-config.md` の順 13）では、戻し先 `mastersmith:pre-maintenance-update` を消さない。** 戻し先の土台の JRE のイメージ（`eclipse-temurin:25.0.4.1_1-jre-noble`、`d9a39a236346`）はタグを消しても戻し先のイメージの層として残るため、戻しに取り直しは要らない。

### 2.2 戻したときに変わること

| 変わること | 内容 |
|---|---|
| JRE | 25.0.4.1 に戻る |
| メールのテンプレートの描画 | java-mustache-processor 0.1.0 に戻る（招待・パスワードなどのメールの本文） |
| 画面 | make-you-chic-ui の固定先 `cab4207` と前の frontend の依存に戻る（画面の機能は同じ） |
| 一覧の並び（P1） | 前の形に戻る。MySQL で長い値（約 8,000 字を超える）のカラムを並べ替えると、1ページ目から 1038（Out of sort memory）で 500 になりうる。見本の PG では起きない |
| 参照の確かめの WARN（T3） | `stage` が null のとき項目が出ない前の形に戻る |
| 監査・業務データの行・招待 | 戻しでは変わらない。戻す前に残った行はそのまま残る（監査は追記だけ） |

### 2.3 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200、コンテナのイメージ ID が `d8a467b60f6f…`、`java -version` が 25.0.4.1。ERROR 0 件。Flyway の `Migrating schema` 0 件。救済の WARN 0 件。WARN は前の版から出ているもののほかに無い |
| R2 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと業務データの一覧（`customers`）と詳細が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.4 新しい版へ上げ直すとき

- 移行は当たらない。直した WAR で `docker compose up -d --build --no-deps app` を行い（`MASTERSMITH_IMAGE_TAG` を付けない）、イメージ ID の一致を確かめる。

## 3. 片付けの後に戻すとき

- 片付け（`cd-config.md` の順 13）は配備の完了の後に行い、戻し先 `mastersmith:pre-maintenance-update` は残す。そのため、片付けの後も 2節の手順で戻せる。
- 片付けで消した古い版の依存のイメージ（対象DB・Mailpit・lgtm・collector・k6・JRE の古い版）は、戻した版のアプリの動作には要らない（アプリのイメージは自分の層を持ち、見本の PG と Mailpit は新しい版のまま使う）。
- 前の Intent の `docker/perf/compose.yaml` などで古い版の依存のイメージを使う必要が出たときは、固定先のダイジェストで取り直す（`docker pull <名前>@sha256:…`）。

## 4. 配備の前の複写を戻すとき（第二の手）

- 移行も H2 の版の変更も無いため、内部DB が壊れる見込みは低く、この手は通常使わない。入れ替えの起動で内部DB が壊れたと分かったときは、戻す前に依頼者に諮る。
- 使えるのは配備の前の複写 `…-pre-maintenance-update.tgz`（`cd-config.md` の順 5）。戻すと、T0 の後のデータ（スモークテストの操作、その後の利用者・ロールの変更）と監査の行を失う。
- 戻し方は `docs/internal-db.md` の「内部DBのバックアップと戻し方」のとおり。アプリを止めてボリュームへ展開し、戻し先のイメージで起動する（2.1）。
- 配備の後の複写 `…-after-maintenance-update.tgz`（順 10）は、スモークテストの後の状態を保つバックアップで、戻しの手には使わない。

## 5. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md` 7節）のとおり。
- ヒープが尽きると JVM が止まる（終了コード 3・OOMKilled false）。`restart: "no"` のため起き直さない。
- `docker compose start app` で約 10 秒で戻る。
- JRE の版を上げたため、止まりが繰り返すときは、Build and Test の `dslMixed` の `memory.peak`（0.9077、線 0.95）と合わせて戻すかを諮る。
- 対象DB（見本の PG）が止まったとき（`OOMKilled` true など）は、アプリは動き続け、業務データの API が 503 を返す。`docker compose --profile targetdb-postgres up -d --no-deps targetdb-postgres` で起動し直す（`compose.yaml` の記述と同じ版で作り直される）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- 要件: `inception/requirements-analysis/requirements.md`（FR11.1・FR11.2、NFR4）
- Build and Test: `construction/build-and-test/test-results.md`、`construction/code-generation/code-summary.md`（P1・T3）
- リポジトリ: `docs/deployment.md`（「戻し方」）、`docs/internal-db.md`（「内部DBのバックアップと戻し方」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`・`restart: "no"`）、`Dockerfile`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 戻し先のイメージ `d8a467b60f6f` は、今の内部DB でそのまま起動する。今の本物のデータで動いているイメージそのもので、この Intent は移行を足していない。戻しの練習は行わない。
- [assumption] 見本の PG（`postgres:18.6` の新しいダイジェスト）と Mailpit（`v1.33.0`）は、戻した版のアプリからも前と同じ形で使える。Build and Test の間、前の版のアプリ（`d8a467b6`）はこの見本の PG と Mailpit で healthy のまま動いていた。
