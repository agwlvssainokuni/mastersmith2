# 戻しの手順書（rollback-runbook）

Intent 261029-reference-lookup-cleanup の配備を戻す手順です。README の「戻し方」と、前の手順（`aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-pipeline/rollback-runbook.md`）を正とし、差を中心に書きます。今回はスキーマも `.env` も変わらないため、戻しは**イメージだけ**で、戻しの練習は行いません（`cd-config.md` の 4節・5節）。U1・U2・U3 は同じ WAR とイメージに入るため、**必ず一緒に戻ります**（U3 の画面だけ・U1・U2 のサーバー側だけを戻す形は無い。U3 の `cicd-pipeline.md` 5節 R-01）。

## 1. 戻すきっかけ

- 起動が healthy にならない、ERROR が出続ける、ログに秘密・メールアドレスそのもの・業務データの値・参照先の値が出る、スモークテスト（`deployment-strategy.md` の S1〜S9）が通らない。
- 参照の画面・保存で 503 や想定外の 409 が続く、参照先の読み取りで接続のプールが尽きる（Performance Validation の判定で目標を大きく外れた場合も含む）。
- 戻すかどうかは依頼者に諮って決める。
- 2つの新しい設定の誤り（1 秒未満で対象DB 全体が使えない）だけが原因のときは、イメージを戻さず 3節の手を先に使う。
- アプリが `ExitOnOutOfMemoryError` で止まっただけのときは、すぐには戻さない（5節）。

## 2. 戻しの形（第一の手）: イメージだけ

- 戻し先は、配備の前に付けたタグ `mastersmith:pre-reference-lookup-cleanup`（イメージ ID `e0ede6ddd5c4…`、前の Intent `261027-perf-targets-review` で配備した版）。
- 内部DB はそのまま使い、スキーマは戻さない（V13 のまま。この Intent は移行を足していない）。
- `.env`・`.env.targetdb`・見本の PG は変えていないため、戻すことに伴う作業は無い。2つの新しい設定は前の版では読まれないだけ（U1 の `reliability-design.md` 3節）。

### 2.1 手順

```bash
docker image inspect mastersmith:pre-reference-lookup-cleanup --format '{{.Id}}'   # sha256:e0ede6ddd5c4… であること
docker compose stop app
# 調べるために、今の内部DB を複写する（README の「内部DBのバックアップと戻し方」。名前は …-before-rollback.tgz、置き場は ~/.mastersmith-backup/、複写は権限 600）
MASTERSMITH_IMAGE_TAG=pre-reference-lookup-cleanup docker compose --profile targetdb-postgres up -d --no-build app
MASTERSMITH_IMAGE_TAG=pre-reference-lookup-cleanup docker compose --profile targetdb-postgres ps app   # healthy を待つ
docker inspect mastersmith-app-1 --format '{{.Image}}'   # sha256:e0ede6ddd5c4… であること。違えば --force-recreate --no-deps を付けて作り直す
```

- **戻している間は、アプリを起動・作り直す `docker compose` のコマンドに毎回 `MASTERSMITH_IMAGE_TAG=pre-reference-lookup-cleanup` を付けます。** 付けずに `docker compose up -d` を行うと、`mastersmith:local`（新しい版）で作り直されます。
- **`--no-build` を必ず付けます**（付けないと、戻し先のイメージが上書きされる）。
- **コマンドの外でこの変数を `export` したままにしないでください。** 戻した後は `unset MASTERSMITH_IMAGE_TAG` で消し、`env | grep -c MASTERSMITH_IMAGE_TAG` が 0 であることを確かめる。
- 入れ替えの後は、コンテナのイメージ ID が戻し先の ID と一致することを `docker inspect` で確かめる（構成が同じだと作り直されず古いイメージのまま起動することがある。`project.md` の学び）。
- 戻し先のタグのイメージが無いときは、README の「戻し方」のとおり、`main`（`d797ef74`）を `git worktree add` で取り出して WAR とイメージを作り直す。
- 開いているタブを読み込み直す（前の版の画面のファイルを読むため）。

### 2.2 戻したときに変わること

| 変わること | 内容 |
|---|---|
| 作成・更新の画面 | 参照の部品（選択・ピッカー・読み取りだけの参照の項目）が無くなり、参照のカラムは前の形（前の版の部品）に戻る |
| 保存前の参照の確かめ | 無くなる。外部キーの無い参照先へ、参照先に無い値を保存できる前の状態に戻る（外部キーのある参照先は DB の制約で拒否される） |
| `POST /api/tables/reference/rows` | 無くなる（404） |
| 監査 | 戻した後の拒否に `BUSINESS_REFERENCE_NOT_FOUND` の保存前の確かめは出ない。戻す前に残った行はそのまま残る（追記だけ） |
| 2つの設定 | 前の版は読まない。`.env` に書いていても害は無い |
| 業務データの行 | 戻しでは変わらない。スモークテストで作った行が残っていれば、戻した版の一覧・削除で消せる |

### 2.3 戻した後の確かめ

| # | 確かめ | 行う人 | 通る条件 |
|---|---|---|---|
| R1 | healthy と起動のログ | AI | `app` が healthy、`/actuator/health` が 200、コンテナのイメージ ID が `e0ede6ddd5c4…`。ERROR 0 件。Flyway は `Migrating schema` 0 件で版 13。救済の WARN 0 件。WARN は前の版から出ているもののほかに無い |
| R2 | ★ログインと画面 | 依頼者 | 初期管理者でログインし、ホームと業務データの一覧（`customers`）と詳細が開ける。`LOGIN_SUCCEEDED` が監査に残る（送る前に伝える） |

### 2.4 新しい版へ上げ直すとき

- 移行は当たらない。直した WAR で `docker compose --profile targetdb-postgres up -d --build app` を行い（`MASTERSMITH_IMAGE_TAG` を付けない）、イメージ ID の一致を確かめる。

## 3. 2つの新しい設定の誤りのとき（イメージを替えない手）

U1 の `cicd-pipeline.md` 4節のとおり。`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` に 1 秒未満の値を書くと、点検が項目の名前だけを WARN で出し、対象DB 全体（一覧・詳細・書き込み・参照）が使えなくなる（起動は続く）。今回の配備では `.env` に書かない（既定で動く）ため、配備の直後には起きない見込みで、後で値を書いたときの手として置く。

```bash
grep -c '^MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE=\|^MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL=' .env   # 件数だけ（値は表示しない）
# 該当の行を、値を表示せずに行ごと消す（消した値は既定に戻すだけで戻す必要が無いため、.env の複写は取らない）
docker compose --profile targetdb-postgres up -d --force-recreate --no-deps app
```

- 確かめ: 起動のログに2つの項目名の WARN が 0 件、`deployment-strategy.md` の S2（既存の業務データの一覧）が通る。
- 失うもの: 無い（既定の 5s・10s に戻る）。

## 4. 配備の前の複写を戻すとき（第二の手）

- 移行も H2 の版の変更も無いため、内部DB が壊れる見込みは低く、この手は通常使わない。入れ替えの起動で内部DB が壊れたと分かったときは、戻す前に依頼者に諮る。
- 使えるのは配備の前の複写 `…-pre-reference-lookup-cleanup.tgz`（`cd-config.md` の順 5）。戻すと、T0 の後のデータ（スモークテストの操作、その後の利用者・ロールの変更）と監査の行を失う。README の「内部DBのバックアップと戻し方」の戻し方で、アプリを止めてボリュームへ展開し、戻し先のイメージで起動する（2.1）。
- 配備の後の複写 `…-after-reference-lookup-cleanup.tgz`（順 10）は、スモークテストの後の状態を保つバックアップで、戻しの手には使わない。

## 5. アプリが止まったとき（`ExitOnOutOfMemoryError`）

- 前の手順（`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md` 7節）のとおり。ヒープが尽きると JVM が止まり（終了コード 3・OOMKilled false）、`restart: "no"` のため起き直さない。`docker compose --profile targetdb-postgres start app` で約 10 秒で戻る。
- 参照先の読み取りは選択肢を保存もキャッシュもせず、1要求で残る量の見積もりは今の上限の内（U1 の `infrastructure-specification.md` 1節）。止まりが繰り返すときは、Performance Validation の `memory.peak` の結果と合わせて戻すかを諮る。
- 対象DB（見本の PG）が止まったとき（`OOMKilled` true など）は、アプリは動き続け、業務データと参照の API が 503 を返す。`docker compose --profile targetdb-postgres start targetdb-postgres` で起動し直す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-pipeline/rollback-runbook.md`、`aidlc/spaces/default/intents/261016-business-data-views/operation/deployment-pipeline/rollback-runbook.md`
- `cd-config.md`・`deployment-strategy.md`・`deployment-pipeline-questions.md`
- 上流: `construction/u1-reference-read/infrastructure-design/cicd-pipeline.md`（4節）・`infrastructure-specification.md`（R-01）、`construction/u2-reference-write-check/infrastructure-design/cicd-pipeline.md`（4節）、`construction/u3-reference-screens/infrastructure-design/cicd-pipeline.md`（5節 R-01）
- リポジトリ: README（「戻し方」「内部DBのバックアップと戻し方」）、`compose.yaml`（`MASTERSMITH_IMAGE_TAG`・`restart: "no"`）、`backend/src/main/resources/application.yaml`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment）、`project.md`（Deployment・Corrections の学び）

## Assumptions & Open Questions

- [assumption] 戻し先のイメージ `e0ede6ddd5c4` は、今の内部DB（V13）でそのまま起動する。今の本物のデータで動いているイメージそのもので、この Intent は移行を足していない。戻しの練習は行わない。
- [assumption] 戻した版の作成・更新の画面は、DSL の `REFERENCE` のカラムを前の版の部品で扱う（前の Intent の配備で動いていた形）。
