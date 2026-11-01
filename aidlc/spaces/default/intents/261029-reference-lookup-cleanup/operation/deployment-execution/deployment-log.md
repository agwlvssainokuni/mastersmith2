# 配備の記録（deployment-log）

Intent 261029-reference-lookup-cleanup。流れは `operation/deployment-pipeline/cd-config.md` の 3節（順 1〜15）。時刻は UTC。

> 0節は指揮役が配備に使った手順メモ（コマンドの一覧）。結果は 1節〜3節。値（`.env` の値・メールアドレス・業務データの値・DSL の本文・ロールの名前）は書かない。

## 0. 指揮役の手順メモ（実行用）

共通の決まり:

- 作業の場所はリポジトリのルート（`/Users/agawa/Documents/project/git/mastersmith2`）。Bash の呼び出しの間で変数は残らないため、T0 などは `~/.mastersmith-backup/` の下の権限 600 のファイルに書いて読む。
- `docker compose` でアプリを触るときは**必ずサービス名 `app` を付ける**（付けないと見本の PG が上乗せの無い構成で作り直され 15432 が閉じる。環境の段の H2）。`--profile targetdb-postgres` を付ける。
- `MASTERSMITH_IMAGE_TAG` をシェルに残さない（戻すときだけ、コマンドの頭に付ける）。
- 値（`.env` の値・メールアドレス・業務データの値・DSL の本文・ロールの名前）を表示しない。ログは `jq` で項目名と件数だけを出す。JSON でない行があるため `jq -R 'fromjson? | …'` で読む。
- 待つときは `sleep` ではなく Monitor の until ループで待つ。
- コミットは行う前に依頼者に提案して承認を得る（順 11・14）。

### 順 1 関門（AI、止めない）

```bash
cd /Users/agawa/Documents/project/git/mastersmith2
git status --porcelain -- . ':!aidlc' | wc -l                     # 0
git rev-parse --short=8 HEAD main origin/develop
git -C vendor/make-you-chic-ui rev-parse --short HEAD              # cab4207
env | grep -c MASTERSMITH_IMAGE_TAG                                 # 0
grep -c '^MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE=\|^MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL=' .env   # 0（件数だけ）
gh run view 41273010129 --json status,conclusion,attempt --jq '"\(.status) \(.conclusion) a\(.attempt)"'   # 23519159
pmset -g batt | head -1                                             # AC であること（Q 前の確かめ）
docker ps --format '{{.Names}} {{.Image}} {{.Status}}'
```

### 順 2 WAR の作り直し（AI、止めない）

```bash
W=backend/build/libs/mastersmith.war
shasum -a 256 "$W"                                                  # 前（4cd5c416…）
caffeinate -dims ./gradlew :backend:bootWar --rerun-tasks           # 画面のビルドを含む
shasum -a 256 "$W"                                                  # 後（記録する）
unzip -p "$W" WEB-INF/classes/application.yaml | grep -cF 'reference: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE:5s}'   # 1
unzip -p "$W" WEB-INF/classes/application.yaml | grep -cF 'reference-read-total: ${MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL:10s}'   # 1
unzip -l "$W" | grep -c 'WEB-INF/classes/static/'                  # 画面のファイルの数（記録する）
unzip -p "$W" 'WEB-INF/classes/static/assets/*.js' | grep -c '/api/tables/reference/rows'          # 1 以上
unzip -p "$W" 'WEB-INF/classes/static/assets/*.js' | grep -c '参照先を読む権限がないため変更できません'   # 1 以上
pmset -g log | grep -E ' Sleep  |DarkWake' | tail -3                # 作り直しの間に眠っていないこと
```

### 順 3 戻し先のタグ（AI）

```bash
docker tag mastersmith:local mastersmith:pre-reference-lookup-cleanup
docker image inspect mastersmith:pre-reference-lookup-cleanup --format '{{.Id}}'   # sha256:e0ede6ddd5c4…
```

### 順 4 救済の見込み（依頼者）

- `deployment-execution-questions.md` の Q1。順 5 の数え（`init_admin_suspended` 0・`init_admin_flag` 1）で裏付ける。

### 順 5 止めて配備の前の複写と数え（AI、依頼者の承認 Q2 の後）

```bash
date -u +%Y-%m-%dT%H:%M:%SZ > ~/.mastersmith-backup/T0-reference-lookup-cleanup && chmod 600 ~/.mastersmith-backup/T0-reference-lookup-cleanup
cat ~/.mastersmith-backup/T0-reference-lookup-cleanup               # T0 を記録する
docker compose --profile targetdb-postgres stop app
TS=$(date +%Y%m%d%H%M); echo "$TS" > ~/.mastersmith-backup/TS-reference-lookup-cleanup
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
  tar czf /backup/mastersmith-data-$TS-pre-reference-lookup-cleanup.tgz -C /data .
chmod 600 ~/.mastersmith-backup/mastersmith-data-$TS-pre-reference-lookup-cleanup.tgz
ls -l ~/.mastersmith-backup/mastersmith-data-$TS-pre-reference-lookup-cleanup.tgz   # 600・大きさ（中身は開かない）
~/.mastersmith-tools/count-db3.sh ~/.mastersmith-backup/mastersmith-data-$TS-pre-reference-lookup-cleanup.tgz "$(cat ~/.mastersmith-backup/T0-reference-lookup-cleanup)"
ls ~/.mastersmith-backup | grep -c '^work-'                         # 0（展開したディレクトリが消えている）
# ここでは start しない（順 6 へ）
```

4.1 の見込み（違えば順 8 で依頼者と合わせ直す）:

| 出力の行 | 見込み |
|---|---|
| `schema_version` | 13 |
| `dsl_option_source REFERENCE sales.orders.customer_id -> customers` | ある（前の Intent の複写では `sales.order_items.order_id -> orders` と合わせて 2 件） |
| `dsl_hits[REFERENCE]`・`[orders]`・`[customer_id]` | どれも 1 以上 |
| `dsl_previews` | 0 |
| `roles` | 3 |
| `init_admin_rows`・`init_admin_suspended`・`init_admin_flag` | 1・0・1 |
| `admin_user_roles`・`admin_group_roles`・`admin_work_role`・`admin_work_role_in_assigned` | 0・3・1・1 |
| `audit_events_total` | 基準として記録（前の Intent の配備の後は 295） |
| `audit_since_t0` | 0 |

### 順 6 入れ替え（AI）

```bash
date -u +%Y-%m-%dT%H:%M:%SZ > ~/.mastersmith-backup/T1-reference-lookup-cleanup
docker compose --profile targetdb-postgres up -d --build app
docker inspect mastersmith-app-1 --format '{{.Image}}'; docker image inspect mastersmith:local --format '{{.Id}}'   # 一致すること
# 食い違えば:
#   docker compose --profile targetdb-postgres up -d --no-build --force-recreate --no-deps app
docker run --rm --entrypoint sha256sum mastersmith:local /app/mastersmith.war   # 順 2 の SHA-256 と同じ
docker ps --format '{{.Names}} {{.Image}} {{.Status}}'             # 見本の PG・Mailpit が作り直されていない（Up の時間が前のまま）
```

### 順 7 健全性 S1（AI）

```bash
T1=$(cat ~/.mastersmith-backup/T1-reference-lookup-cleanup)
# healthy を Monitor で待つ: until [ "$(docker inspect -f '{{.State.Health.Status}}' mastersmith-app-1)" = healthy ]; do …; done
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/actuator/health        # 200
docker inspect mastersmith-app-1 --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}} {{.RestartCount}} {{.State.OOMKilled}}'   # 2147483648 4000000000 0 false
L() { docker logs --since "$T1" mastersmith-app-1 2>&1; }
L | jq -rR 'fromjson? | select(.level=="ERROR") | .logger' | sort | uniq -c            # 0 行
L | jq -rR 'fromjson? | select(.level=="WARN") | .logger' | sort | uniq -c             # Flyway の Database・BeanPostProcessorChecker だけ
L | jq -cR 'fromjson? | select(.message|test("Migrating schema"))' | wc -l              # 0
L | jq -rR 'fromjson? | select(.logger|test("flyway")) | .message' | grep -E 'Current version|up to date'   # 版 13
L | jq -cR 'fromjson? | select(.level=="WARN" and (.logger|endswith("TargetDataSourceConfig")))' | wc -l   # 0（点検の WARN そのもの）
L | grep -cE 'query-timeout\.reference|reference-read-total|QUERY_TIMEOUT_REFERENCE|REFERENCE_READ_TOTAL'   # 0
L | jq -cR 'fromjson? | select(.level=="WARN" and (.logger|endswith("InitialAdminInitializer")))' | wc -l   # 0（救済）
E=$(grep '^MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=' .env | head -1 | cut -d= -f2- | sed -e "s/^[\"']//" -e "s/[\"']$//"); [ -n "$E" ] && L | grep -ciF -- "$E"; unset E   # 0（値は表示しない）
```

- 点検の WARN の項目の名前は、ログでは Spring の項目の名前（`mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total`）で出る（`TargetDbSettings.java` 70・73 行目）。`deployment-strategy.md` S1 の書き方（`QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL`）との差を 3節に書く。
- 依頼者に、開いているタブを読み込み直すよう伝える。

### 順 8 合わせ直し（4.1 が見込みと違うとき）

- `deployment-strategy.md` 3.3 の表で依頼者と決め、監査の期待（3.2）を直してから順 9 へ。

### 順 9 スモークテスト S2〜S8（依頼者）と AI の裏付け

S2 の後（AI）:

```bash
date -u +%Y-%m-%dT%H:%M:%SZ > ~/.mastersmith-backup/T2-reference-lookup-cleanup   # S2 を始める前に控える
# S2 の後:
T2=$(cat ~/.mastersmith-backup/T2-reference-lookup-cleanup)
docker logs --since "$T2" mastersmith-app-1 2>&1 | jq -rR 'fromjson? | select(.level=="ERROR" or .level=="WARN") | "\(.level) \(.logger)"' | sort | uniq -c   # ERROR 0
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc "SELECT COUNT(*) FROM sales.orders"   # 件数を控える（見込み 1）
```

S3〜S8 の後（AI）: 依頼者に「予定に無い操作（保存の押し直し・ログインの誤り・ログアウト）」の有無を聞き、3.2 の期待を合わせる。

```bash
docker logs --since "$T2" mastersmith-app-1 2>&1 | jq -rR 'fromjson? | select(.level=="ERROR" or .level=="WARN") | "\(.level) \(.logger)"' | sort | uniq -c
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc "SELECT COUNT(*) FROM sales.orders"   # S2 と同じ
```

### 順 10 配備の後の複写と数え（AI、依頼者に伝えてから）S9

```bash
docker compose --profile targetdb-postgres stop app
TS2=$(date +%Y%m%d%H%M)
docker run --rm -v mastersmith_mastersmith-data:/data -v "$HOME/.mastersmith-backup":/backup \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab \
  tar czf /backup/mastersmith-data-$TS2-after-reference-lookup-cleanup.tgz -C /data .
docker compose --profile targetdb-postgres start app                # up ではなく start（作り直さない）
chmod 600 ~/.mastersmith-backup/mastersmith-data-$TS2-after-reference-lookup-cleanup.tgz
docker inspect mastersmith-app-1 --format '{{.Image}}'; docker image inspect mastersmith:local --format '{{.Id}}'   # 一致
# healthy と /actuator/health 200 を待つ（順 7 と同じ）
~/.mastersmith-tools/count-db3.sh ~/.mastersmith-backup/mastersmith-data-$TS2-after-reference-lookup-cleanup.tgz "$(cat ~/.mastersmith-backup/T0-reference-lookup-cleanup)"
```

S9 の判定: `audit_by_type` が `deployment-strategy.md` 3.2（依頼者の操作で合わせた後）と一致、FAILURE・UNKNOWN 0、`business_row_columns` の空の数がすべて 0、`roles` 3・`admin_user_roles` 0・`admin_group_roles` 3・`admin_work_role` 1・`admin_work_role_in_assigned` 1（4.1 と同じ）、`sales.orders` の件数が S2 と同じ。

### 順 11 配備の記録のコミット（依頼者の承認）

- この段の記録（`deployment-log.md`・`smoke-test-results.md`・`health-check-report.md`・質問の答え）をコミットする。`~/.mastersmith-backup/T*-reference-lookup-cleanup`・`TS-*` は記録を書いた後に消す。

### 順 12 試験用のタグの片付け（AI）

```bash
docker ps -a --filter ancestor=eab1676f26de --format '{{.Names}}' | wc -l   # 0
docker rmi mastersmith:reference-lookup-cleanup
docker image ls --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep mastersmith   # local・pre-reference-lookup-cleanup が残る
```

### 順 13 `main` への取り込み（依頼者の承認）

```bash
git fetch origin
gh run list --branch develop --limit 80 --json headSha,databaseId,attempt,conclusion,status --jq '.[] | "\(.headSha[0:8]) \(.databaseId) a\(.attempt) \(.status) \(.conclusion)"'
# d797ef74 の後の run ごと（a2 以上のもの）に試行を確かめる:
gh run view <id> --attempt 1 --json conclusion,jobs --jq '.conclusion, (.jobs[] | "\(.name) \(.conclusion) \(.startedAt) \(.completedAt)")'
gh run view <id> --attempt 1 --log-failed | grep -E 'FAILED|✗|×|Error' | head   # 失敗したテストの名前だけ
git merge-base --is-ancestor main develop && echo ff-ok
git fetch . develop:main                                            # fast-forward だけ（作業ブランチを切り替えない）
git rev-parse --short=8 main develop                                # 同じ
```

- 既に分かっている試行: `a75baec4`（run 41161783903、2 回目で success、1 回目 `ReferenceSameResponseIT`）、`cf887ad8`（run 41025421073、2 回目で success）、`e1e028d1`（run 41264314230、2 回とも failure。1 回目は画面のテストの未処理の例外。`cb23466b` で直り1回目で success）。1 回目の失敗のテストの名前を記録する。
- `23519159` 以降（`cba9d95f`〜`5458e3eb` と順 11 のコミット）の CI は、プッシュの後にしか動かない（Q5）。

### 順 14・15

- 取り込みと push の結果を `develop` だけのコミットで追記する（依頼者の承認）。
- `git push origin develop main` は依頼者。

### 戻すとき

- `rollback-runbook.md` 2.1（`MASTERSMITH_IMAGE_TAG=pre-reference-lookup-cleanup … up -d --no-build app`、イメージ ID の一致、`unset`）。2つの設定の誤りだけなら 3節。

## 1. 結果

- **配備した版**: `develop` の `5458e3eb` から作り直した WAR（アプリのコードは `cb23466b`）。WAR の SHA-256 は **`4cd5c41626f4dd0513656e551b39ec1a71038fedb5622901ea5db8543008222f`**（作り直しの前後・新しいイメージの中で同じ。同じソースのため）。
- **動いているコンテナ**: `mastersmith-app-1`、イメージ `mastersmith:local`（`sha256:d8a467b60f6f…`）、メモリ 2 GiB・CPU 4、restart 0、healthy。
- **戻し先**: `mastersmith:pre-reference-lookup-cleanup`（`sha256:e0ede6ddd5c4…`、前の Intent で配備した版）。
- **アプリが止まった時間**: 入れ替えで 05:00:57Z〜05:01:24Z の約 27 秒（見込みの 2〜3 分より短い）、配備の後の複写で 05:09:27Z〜05:09:37Z の約 10 秒。
- **スモークテスト**: S1〜S9 はすべて通過（`smoke-test-results.md`）。健全性は `health-check-report.md`。
- **変わらないもの**: `.env`、内部DB のスキーマ（版 13）、見本の PG・Mailpit のコンテナ（作り直していない）。
- **質問の答え**（`deployment-execution-questions.md`）: Q1 A・Q2 A・Q3 A・Q4 A・Q5 A。
- **残り**: 順 11（記録のコミット）、順 13（`main` への取り込み）、順 14（結果の追記）、順 15（プッシュ）はこの記録の時点でまだ行っていない。

## 2. 手順の記録

| 順 | 時刻 | すること | 結果 |
|---|---|---|---|
| 1 | — | 関門 | 通過。アプリのソース（`aidlc/` の外）の未コミット 0 件。HEAD `5458e3eb`・`main` `d797ef74`・`origin/develop` `23519159`。`vendor/make-you-chic-ui` は `cab4207`。`MASTERSMITH_IMAGE_TAG` 0 件。`.env` の新しい2つの項目は 0 件（項目名の数だけ）。PC はバッテリー駆動のため、依頼者に電源をつなぐよう伝えた |
| — | 04:25:02〜05:00:18 | `23519159` の CI（run 41273010129）の確かめ（Q2: A） | 1 回目で success。検査の job は 35 分 16 秒（42 分の線の内）。その後に依頼者が `develop` を `5458e3eb` までプッシュした（Q5: A） |
| 2 | 04:55:25〜04:56:04 | `caffeinate -dims ./gradlew :backend:bootWar --rerun-tasks` | 成功。SHA-256 は前後とも `4cd5c416…`。WAR の中の `application.yaml` に2つの設定の既定つきの行が各 1 件、画面のファイル（`static/`）100 件、画面の JS に `/api/tables/reference/rows` 1 件・参照の部品の文言 1 件。作り直しの間の Sleep・DarkWake 0 件 |
| 3 | — | `docker tag mastersmith:local mastersmith:pre-reference-lookup-cleanup` | ID `e0ede6ddd5c4…` を確認 |
| 4 | — | 救済の見込みを依頼者に確認（Q1） | A（初期管理者について変更なし）。順 5 の数え（停止 0・印 1）と順 7 の救済の WARN 0 件で裏付けた |
| 5 | 05:00:57（T0） | T0 を控え `docker compose --profile targetdb-postgres stop app`。複写と数え | 複写 `~/.mastersmith-backup/mastersmith-data-202611011400-pre-reference-lookup-cleanup.tgz`（権限 600、38,778 バイト）。数えの結果は下の 2.1。展開した作業ディレクトリは消えた（`work-` 0 件） |
| 6 | 05:01:08（T1） | `docker compose --profile targetdb-postgres up -d --build app` | Recreated。コンテナのイメージ ID `d8a467b60f6f…` が `mastersmith:local` と一致（作り直しの追加は不要）。イメージの中の WAR の SHA-256 は `4cd5c416…` で一致。見本の PG・Mailpit は Up 4 hours のまま（作り直されていない） |
| 7 | 05:01:24 | 健全性 S1 | healthy、`/actuator/health` 200。ERROR 0 件。詳細は `health-check-report.md` |
| 8 | — | 4.1 の結果での合わせ直し | 不要（2.1 のとおり前提どおり） |
| 9 | 05:01:29（T2）〜 | スモークテスト S2〜S8（依頼者、Q3: A で S2 を先に報告） | S2: 依頼者「表示に問題なし、予定外の操作なし」。S3〜S8: 依頼者「済んだ、問題なし」。AI の裏付けは `smoke-test-results.md` |
| 10 | 05:09:27〜05:09:37 | `stop app` → 複写 → `start app` | 複写 `~/.mastersmith-backup/mastersmith-data-202611011409-after-reference-lookup-cleanup.tgz`（権限 600、39,772 バイト）。起動し直した後もイメージ ID が一致、healthy、200。数えの結果は 2.2（S9） |
| 11 | — | 配備の記録のコミット | まだ（依頼者の承認の後） |
| 12 | — | 試験用のタグの片付け（Q4: A） | `eab1676f26de` を使うコンテナ 0 件を確かめ、`docker rmi mastersmith:reference-lookup-cleanup`（Untagged・Deleted）。残る `mastersmith` のタグは `local`（`d8a467b60f6f`）・`pre-reference-lookup-cleanup`（`e0ede6ddd5c4`）と前の Intent までの戻し先 4 つ |
| 13 | — | `main` への取り込み | まだ（Q4: A。CI を試行ごとに確かめ、依頼者の承認の後） |
| 14・15 | — | 結果の追記・プッシュ | まだ |

### 2.1 配備の前の複写の数え（`cd-config.md` 4.1）

| 項目 | 見込み | 結果 |
|---|---|---|
| スキーマの版 | 13 | 13 |
| 適用済みの DSL の数 | — | 7 |
| 最新の DSL の `REFERENCE` のカラム | `sales.orders.customer_id -> customers` がある | `sales.orders.customer_id -> customers`・`sales.order_items.order_id -> orders` の 2 件。`LOOKUP` 0 件 |
| `dsl_previews` | 0 | 0 |
| `roles` | 3 | 3（`permission_settings` 5） |
| 利用者の数・有効な管理者 | — | 3・1 |
| 初期管理者の行・停止・印 | 1・0・1 | 1・0・1 |
| 初期管理者の直接の割り当て・グループ経由・作業ロールの保存・保存が割り当て済みのロール | 0・3・1・1 | 0・3・1・1 |
| `audit_events` の全体 | 基準 | 297 |
| T0 以降の監査 | 0 | 0 |

- `audit_events` の全体は、前の Intent の配備の後の記録（295）より 2 件多い。前の配備の後から今回の T0 までの操作（ログインなど）の分と読む（3節）。

### 2.2 配備の後の複写の数え（S9）

- `audit_events` の全体 309、T0 以降 12 件（内訳は `smoke-test-results.md` の 2節）。
- `roles` 3、初期管理者の割り当ては直接 0・グループ経由 3・作業ロールの保存 1・保存が割り当て済みのロール 1（2.1 と同じ。確かめ用のロールは残っていない）。展開した作業ディレクトリは消えた。

## 3. 計画との差・気づき

1. **S1 の点検の WARN の探し方**: `deployment-strategy.md` の S1 は項目名 `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` で探すと書くが、ログ（`TargetDataSourceConfig` の WARN の `items`）に出るのは Spring の項目の名前 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total`（`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbSettings.java` 70・73 行目）。書いた文字列だけで探すと WARN が出ていても 0 件になる。承認済みの記録は書き換えず、`TargetDataSourceConfig` の WARN が 0 件であることと、両方の形の名前で探して 0 件であることで確かめた（どちらも 0 件）。環境の段の E6（「2つの項目名の WARN は 0 件」）も、`TargetDataSourceConfig` の WARN がそもそも 0 件のため結論は変わらない。
2. **`ROLE_PERMISSION_CHANGED` の件数**: 期待 1 に対し 2 件。依頼者に尋ねると、`orders` と `customers` を別々に保存していた（保存せずに左の木のテーブルを切り替えると設定が破棄される旨の警告が出るため）。`deployment-strategy.md` の S3 は「保存は1回」と書いたが、画面はテーブルごとに保存する作りで、2つのテーブルに権限を付ける手順では 2 回になる。操作と合うため S9 は合格と判定した（期待の件数は保存した回数。前の Intent `261016-business-data-views` と同じ扱い）。承認済みの記録は書き換えない。
3. **監査の全体の件数**: 配備の前の複写の `audit_events` は 297 で、前の Intent の配備の後の記録 295 より 2 件多い。前の配備の後から T0 までの間の操作の分と読み、T0 以降の数えには影響しない（種類は数えていない）。
4. **数える道具**: 前の Intent の `~/.mastersmith-backup/count-db2.sh` は使わず、`~/.mastersmith-tools/count-db3.sh`・`CountRefLookup.java`（権限 700・600）を用意した。初期管理者のメールアドレスを SQL の文に埋めず `PreparedStatement` の引数で渡し、SQL の誤りは例外のクラスの名前と SQLState だけを出す（前の Intent で、誤った列の名前の H2 の誤りの表示にメールアドレスが出たため）。DSL の参照は、字下げから鍵の道をたどって `REFERENCE`・`LOOKUP` のカラムと参照先のテーブルの名前（識別子だけ）を出す。列の名前は Flyway の移行（V2・V4〜V6・V9〜V13）で確かめた。使う前に前の Intent の配備の後の複写で、前の記録と同じ数が出ることを確かめた。今回の数えで値が表示されたことは無い。
5. **REFRESH_FAILED の WARN**: S2 の後のアプリのログに `GlobalExceptionHandler` の WARN（code `REFRESH_FAILED`、401）が 1 件出た。開いていたタブを読み込み直したときに、入れ替えの前の古いトークンの更新が断られたものと見立てた（想定内。監査には残らない種類）。その後は増えていない。
6. **止まった時間**: 見込み（約 2〜3 分）より短い約 27 秒。複写と数えが速く済んだため。
7. **WAR が作り直しの前後で同じ**: 今の WAR（`4cd5c416…`）は `cb23466b` の後に作られており、`5458e3eb` までの差は `aidlc/` の記録だけのため、作り直しても同じ SHA-256 になった（環境の段の H1 の見込みどおり）。

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `operation/environment-provisioning/environment-inventory.md`・`validation-report.md`（H1〜H4・N4・N5）
- 前の記録: `aidlc/spaces/default/intents/261027-perf-targets-review/operation/deployment-execution/`
- 実行したコマンドの出力（`docker inspect`・`docker logs`・`/actuator/health`・`gh run view`・複写の読み取り）と依頼者の報告
- `deployment-execution-questions.md`（Q1〜Q5）
- リポジトリ: `Dockerfile`、`compose.yaml`、`backend/src/main/resources/application.yaml`、`backend/src/main/resources/db/migration/`、`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbSettings.java`・`TargetDataSourceConfig.java`、`backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`、`frontend/src/features/tables/messages.ts`・`businessDataApi.ts`

## Assumptions & Open Questions

- [assumption] `audit_events` の全体の +2 は、前の配備の後から T0 までの操作の分（種類は数えていない）。
- 質問の答えは `deployment-execution-questions.md`（Q1〜Q5 すべて A）。
