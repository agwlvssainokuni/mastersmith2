# 負荷の試験（k6）

使い捨ての環境（`docker/perf/compose.yaml`）に k6 で負荷をかけ、性能の目標（95 パーセンタイル）を確かめる。配備した環境（`compose.yaml`）のデータと監査ログには触れない。計画と結果は `aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/` にある。

## 手順

```bash
# 0. 配備したアプリを止め（CPU を取り合って測定の値がぶれないため）、WAR とイメージを用意する
docker compose stop app
./gradlew :backend:bootWar && docker compose build app

# 1. リポジトリの外に一時の環境ファイルを作る（値は乱数。表示しない）
D=$(mktemp -d) && chmod 700 "$D"
AP=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24); UP=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
( umask 077
  printf 'MASTERSMITH_AUTH_SIGNING_KEY=%s\nMASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=perf-admin@example.test\nMASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 32)" "$AP" > "$D/app.env"
  printf 'PERF_ADMIN_EMAIL=perf-admin@example.test\nPERF_ADMIN_PASSWORD=%s\nPERF_USER_PASSWORD=%s\n' "$AP" "$UP" > "$D/k6.env" )
# コンテナの上限は配備と同じ値（CPU 4・メモリ 2g）。-f で指定する compose は .env を読まないため、シェルの環境変数で渡す
export MASTERSMITH_PERF_ENV_FILE="$D/app.env" MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g

# 2. 使い捨ての環境を起動し（初期管理者が作られる）、止めて試験用の利用者 10 名を入れる
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait
docker compose -p mastersmith-perf -f docker/perf/compose.yaml stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at) VALUES $(for i in $(seq -w 1 10); do printf "('perf-user%s@example.test', '%s', FALSE, CURRENT_TIMESTAMP)," "$i" "$HASH"; done | sed 's/,$//')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4_7-jre-noble java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait

# 3. 場面ごとに流す（health / loginSuccess / loginFailure / refresh / adminCheck / forbidden）
mkdir -p build/perf-results && chmod 777 build/perf-results
docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=health -e DURATION=60s \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
  run --quiet --summary-export=/out/health.json /scripts/scenarios.js

# 4. 片付け
docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v
rm -rf "$D"
docker compose up -d --wait
```

- 監査の書き込みの時間を測るときは、`app.env` に `LOGGING_LEVEL_CHERRY_MASTERSMITH_AUDIT_SERVICE=TRACE` を足して起動し直し、`AuditEventListener` の `ENTER`・`EXIT` の時刻の差を集める。TRACE は応答時間を遅くするため、応答時間の判定の回とは分ける。
- 前提: colima の VM は CPU 4・メモリ 6GiB（README の「コンテナの資源の上限」）。VM の大きさでは配備したアプリ（2g）と同時に動かせるが、CPU 4 を分け合うと測定の値に影響しうるため、手順 0 で配備したアプリを止める。
- k6 も同じ VM の CPU を使う（上限の指定なし）。測った値には k6 の分が混ざりうる。
- JVM の設定を変えて測るときは、`app.env` に `MASTERSMITH_JAVA_OPTIONS=<JVM の引数>`（空白で区切る）を足して起動し直す（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait`）。配備と同じイメージ・同じ口で渡る。
- 修正の前の結果（2026-09-23）: `refresh` の場面は、CPU の上限 2・メモリ 1GB の設定で 30〜40 秒ほどでコンテナがメモリの上限で止まった（OOMKilled）。
- 修正の後の結果（2026-09-23、VM は CPU 4・メモリ 6GiB、上限 CPU 4）: メモリの上限 2g では `refresh` を 60 秒・2回流しても止まらなかった（毎秒 約 11,000 件）。上限 1g のままでは、CPU 4 でも 20 秒以内に OOMKilled で止まった。ログインの p95 は成功 940 ms・失敗 926 ms（同時 10）。記録は `aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`。
- メモリの上限 1g では、JVM は G1 ではなく Serial の GC を選ぶ（コンテナのメモリ 1792MB 未満のときの JVM の既定）。2g では G1 になる。上限を変えると GC の方式も変わることに注意する。

## メモリの内訳を測る（Native Memory Tracking）

コンテナがメモリの上限で止まるときに、ヒープとヒープ以外（メタ領域・スレッド・コードキャッシュ・直接バッファなど）のどちらが伸びているかを、JVM の Native Memory Tracking（NMT）で測る。NMT は応答時間を少し遅くするため、応答時間の判定の回とは分ける。上の手順 3 の後、手順 4 の片付けの前に行う。

```bash
# 1. 上の手順 1 の app.env に NMT を有効にする行を足し、使い捨ての環境を起動し直す（手順 2 の利用者はボリュームに残っている）
( umask 077; printf 'MASTERSMITH_JAVA_OPTIONS=-XX:NativeMemoryTracking=summary\n' >> "$D/app.env" )
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait

# 2. イメージ（JRE）には jcmd が無いため、同じ版の JDK のイメージを、アプリのコンテナと PID の名前空間を共有して動かす（java は PID 1）
nmt() { docker run --rm --pid container:mastersmith-perf-app-1 -u 10001:10001 eclipse-temurin:25.0.4_7-jdk-noble jcmd 1 "$@"; }
nmt VM.native_memory baseline

# 3. k6 の場面（例: refresh）を別の端末で流しながら（その端末では D に同じ場所を入れる）、5 秒ごとに内訳とコンテナのメモリを記録する（コンテナが止まると終わる）
while docker exec mastersmith-perf-app-1 true 2> /dev/null; do
  T=$(date +%H%M%S)
  nmt VM.native_memory summary.diff > "build/perf-results/nmt-$T.txt" 2>&1
  docker stats --no-stream --format "$T {{.MemUsage}}" mastersmith-perf-app-1 >> build/perf-results/nmt-mem.txt
  sleep 5
done
docker inspect mastersmith-perf-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'   # true 137 なら上限で止まった
```

- `summary.diff` は、手順 2 の基準からの増減を分類ごとに出す。`Java Heap` がヒープ、それ以外（`Class`・`Thread`・`Code`・`GC`・`Other`（直接バッファ）など）がヒープ以外。
- メモリの上限で止まると（SIGKILL）、止まった瞬間の値は取れない。最後に取れた記録を「止まる直前」とする。
- メモリの上限を変えて比べるときは、`export MASTERSMITH_CONTAINER_MEMORY=1g` などとして、この節の手順 1 の起動からやり直す。

## DSL の時間を測る（Intent 260923-dsl-schema-loader）

DSL の機能（既定の DSL の生成・プレビューの表示（照合）・投入・戻し・ダウンロード・適用）の時間を、使い捨ての環境で測る。Build and Test では1回ずつの時間（U3・U4 の NFR1.4〜NFR1.8・NFR1.11・NFR2.5 と U5 の画面の時間 NFR1.18〜NFR1.20）を `perf/dsl-timing.sh` で測る。95 パーセンタイル（U4 の NFR1.10）と同時の実行（U4 の NFR1.12・U4-POOL）は Performance Validation で、下の k6 の DSL の場面を使って測る。記録は `aidlc/spaces/default/intents/260923-dsl-schema-loader/construction/build-and-test/` にある。

### 1回ずつの時間（perf/dsl-timing.sh）

```bash
# 0. WAR と、使い捨て用のタグのイメージを作る（配備に使うタグ local は上書きしない）
./gradlew :backend:bootWar && docker build -t mastersmith:perf-dsl .

# 1. 対象DB の種類ごとに、対象DB とアプリを起動し、100 × 100 のスキーマを作って測り、片付ける（1種類 3〜4 分）
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh postgres mysql mariadb
# 画面の時間も測るとき（frontend/ で npm ci と npx playwright install chromium を済ませておく）
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --ui postgres
```

- 台本がすること: ホームの下に一時ディレクトリ（権限 700）を作り、仮の署名鍵・仮の管理者・対象DB の管理者と読み取りのアカウントのパスワードを乱数で作って置く（値は表示しない）。種類ごとに `docker/perf/compose.yaml` の profile（`targetdb-postgres`・`targetdb-mysql`・`targetdb-mariadb`）で対象DB を起動し、`docker/targetdb/generate-large-schema.sh` でスキーマ `large`（100 テーブル × 100 カラム、外部キーとコメントあり）を作り、アプリの接続先を一時の環境ファイル（`MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`）で渡して起動する。終わったら `down -v` と一時ディレクトリの削除で片付ける（途中で止めても同じ）。
- 測るもの（curl の `time_total`。要求ごとにログインし直す。重い処理は同時に1つのため、1つずつ順に送る）:
  - 生成（`POST /preview/generate`）と、その後の表示（`GET /preview`、照合を含む。1回目と2回目）を `REPEAT` 回（既定 3）
  - 生成した DSL のダウンロード（プレビュー中）・適用・同じ DSL の投入と表示
  - 10MB ちょうど（10,485,760 バイト）の DSL の投入（正しいもの・誤りを含むもの2つ）・表示・プレビュー中のダウンロード・適用・適用中のダウンロード
  - 履歴からの戻し（生成した DSL の版と 10MB の版）と、その後の表示
- 生成の内訳は、アプリのログの `既定の DSL の生成の内訳`（`TargetSchemaDslGenerator` の DEBUG。`readMillis`・`buildMillis`・`writeMillis`・`validateMillis`・`bytes`）で読む。環境変数ではクラスの名前を指定できないため、パッケージ `cherry.mastersmith.dslmanage.generate` を DEBUG にしている。
- ヒープは、GC の記録（`-Xlog:gc`、エポックのミリ秒つき。`MASTERSMITH_JAVA_OPTIONS` で渡す）から、要求の間に起きた GC の直前の使用量の最大を読む。GC が起きなかった要求は値が無い（—）。コンテナのメモリの最大は cgroup の `memory.peak`（起動からの最大）で読む。
- 10MB の DSL は `perf/make-large-dsl.mjs` が、生成した DSL の先頭のテーブルを別名（`x_<番号>_<元の名前>`）で写して足し、足りない分（数十 KB）を末尾の YAML のコメントで埋めて作る。誤りを含むものは、写したテーブルのすべてのカラムの `formPart` を書式に無い値にしたもの（JSON Schema の誤りが多数）と、最後に写したテーブルの主キーを無いカラムにしたもの（意味の誤りが末尾に1件）。リポジトリには置かず、結果の置き場（`build/` の下）にだけ作る。
- 結果は `build/perf-results/dsl-<日時>/` に置く。`timings.tsv`（要求ごとの時間）・`summary.md`（`perf/dsl-timing-report.mjs` のまとめ）・種類ごとの `app.log`・`gc.log`・`generate-breakdown.log`・`memory-peak.txt`・`state.txt`（OOMKilled の有無）・応答の本文（`resp-*.body`）・画面の時間（`ui-timing.json`）。応答とログに秘密情報は含まれない（仮の管理者のメールアドレスは `perf-admin@example.test`）。
- 画面の時間（`perf/ui/dsl-ui-timing.mjs`、`--ui` のとき）: 想定の規模のプレビュー（生成した DSL）を置いてから、Chromium（画面なし）でログインし、サイドバーの「DSL」から開く場合と `/admin/dsl` を読み直す場合のそれぞれで、今の状態とプレビューが表示されるまでの時間（NFR1.18）、違いの表の最初の行を開いてカラムの表が出るまでの時間（NFR1.19）、10MB のファイルを選んで「投入」と置き換えの確かめの後に送り始めるまでの時間（NFR1.20）を測る。照合を除く時間は、表示までの時間から `GET /preview` のサーバーの時間（送ってから最初のバイトまで）を引いて出す。

前提と注意:

- **コンテナの上限は配備と同じ CPU 4・メモリ 2g**（`MASTERSMITH_CONTAINER_MEMORY` の既定を 2g にしている）。U4 の NFR1.12 の条件は 1g のコンテナ（ヒープ 75%）で、配備の上限（2g）と違う。1g で測るときは `MASTERSMITH_CONTAINER_MEMORY=1g` を付けて流す（1g では JVM の GC が G1 ではなく Serial になる。前の節を参照）。
- **配備したアプリ（プロジェクト `mastersmith`）は止めない**。colima の VM（CPU 4・メモリ 6GiB）には、配備したアプリ（上限 2g）と、使い捨てのアプリ（2g）・対象DB 1つ（最大 768MB）が同時に収まる。台本は対象DB を1種類ずつ起動し、測り終えたら消してから次の種類に進む。配備したアプリは待機中のため CPU の取り合いは小さいが、測った値には影響が混ざりうる。負荷の試験（k6）で CPU を使い切るときは、前の節の手順 0 のとおり配備したアプリを止める。
- 使い捨ての環境だけに要求を送る（`127.0.0.1:18080`）。配備したアプリ・その内部DB・監査ログには触れない。
- 途中で残したいときは `KEEP=1` を付ける（種類は1つだけ指定する）。終わったら、表示された一時ディレクトリの場所を使って片付ける（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v` と一時ディレクトリの削除。`-f` の compose は `MASTERSMITH_PERF_ENV_FILE` が要るため、`MASTERSMITH_PERF_ENV_FILE=/dev/null` を付けて呼ぶ）。

### 追加の確かめ（--lang・--pattern・--storage）

`perf/dsl-timing.sh` に次のオプションを付けると、上の測定の後に続けて行う（組み合わせてよい。PostgreSQL の1種類で足りる）。

```bash
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --lang --pattern --storage postgres
```

| オプション | すること | 目標 | 結果のファイル |
|---|---|---|---|
| `--lang` | 英語のロケール（`en-US`）の Chromium で `/admin/dsl` を開き、対象DB に無いテーブルを持つ DSL を貼り付けて投入（照合の警告）、続けて誤りを含む DSL を貼り付けて投入（422）。応答の `message` と、画面の警告・誤りの一覧に日本語の文字が無いこと、画面に応答の `message` がそのまま出ることを確かめる（`perf/ui/dsl-ui-lang.mjs`） | U5-LANG-E2E | `ui-lang.json`（`pass`） |
| `--pattern` | 1,000 文字近くの重い正規表現（深い入れ子の繰り返し・`(a+)+` の並び・大きな繰り返しの回数・Unicode の文字の種類の積・遅延の繰り返しの選択）を `pattern` に 2,000 個持つ DSL と、同じ形で `pattern` の無い DSL を作り（`perf/make-pattern-dsl.mjs`）、普通 3 回 → 重い 3 回 → 重いものの直後に待たずに普通 → 普通 3 回の順に投入する | U2-PATTERN-COMPILE（重い投入が短く終わり、直後の普通の投入が遅れない。アプリのログに「正規表現の確かめを時間の上限で打ち切りました」「受け付けられませんでした」が出ない） | `timings.tsv` の `pattern-*`・`pattern-dsl.txt`・`app.log` |
| `--storage` | 10MB の DSL（埋め草の先頭の行に回の番号を入れ、大きさは同じ）の投入→適用を `STORAGE_ROUNDS` 回（既定 21）くり返し、最後にプレビューも1件置く（プレビュー1件と履歴 20 件の最大の状態）。回ごとに H2 のファイル（`/app/data/mastersmith.mv.db`）の大きさ、コンテナのメモリ（cgroup の `memory.current`・`memory.peak`、`memory.stat` の `anon`（プロセスのメモリ）と `file`（ページキャッシュ。回収できる））、履歴の件数を記録する。最後にアプリを `docker compose stop`（`stop_grace_period` 45 秒）で止め、止めるのにかかった秒数と終わり方（exit code。SIGTERM で正常に終われば 143、猶予切れや OOM の SIGKILL は 137）・止めている間の H2 のファイルの大きさ（同じイメージの一時のコンテナでボリュームを読むだけ）・起動から healthy までの秒数・起動し直した後の大きさを記録し、止める前と後のデータ（適用中の DSL とプレビューの DSL の SHA-256、プレビューの previewId、履歴の版・dslHash・件数）を比べる。その後、履歴のすべての版を戻して、プレビューの本文の SHA-256 が履歴の dslHash と一致する数を記録する（`restore_all_match`。詰め直しで本文が壊れていないか） | U4-STORAGE | `storage.tsv`・`memory-peak-before-restart.txt`・`memory-stat-before-restart.txt`・`stop-state.txt`・`data-while-stopped.txt`・`snapshot-before-stop.txt`・`snapshot-after-restart.txt`・`history-after-restart.txt`・`app-before-restart.log` |

- `--lang` の画面は、開いたときにプレビューの表示（重い処理）を読みに行く。読み終わる前に投入すると 503 `DSL_BUSY` になるため、台本は最初の読み込みが終わるのを待ってから投入する。
- `--storage` の `memory.current` はページキャッシュを含むため、H2 のファイルへの書き込みでコンテナの上限の近くまで上がることがある。止まるかどうかは `anon` と、最後の `state.txt`（OOMKilled）で見る。
- `--storage` はアプリを起動し直すため、GC の記録（`gc.log`）は起動し直す前の分（`gc-before-restart.log`）と後の分をつないで残す。
- `--storage` で内部DB の接続先を比べるときは、`PERF_DB_URL` で `MASTERSMITH_DB_URL` を上書きする（例: DEFRAG_ALWAYS なしの `PERF_DB_URL=jdbc:h2:file:/app/data/mastersmith`）。指定しなければ application.yaml の既定（DEFRAG_ALWAYS=TRUE）で動く。使った値は `env.txt` の `db_url` に残る。
- `--storage` のアプリのログは、止める前の分を `app-before-restart.log`、起動し直した後の分を `app.log` に残す。止めるときの終わり方は `app-before-restart.log` の終わりの行（Hikari の shutdown など）と `stop-state.txt` の exit code で見る。

### k6 の DSL の場面（Performance Validation で使う）

`perf/k6/scenarios.js` に DSL の場面を足した。Build and Test では測定として流していない。k6 で読み込めること（`k6 inspect`、全場面）と、`dslLight`・`dslCycle` が動くこと（使い捨ての環境で 10 秒ずつ。条件（履歴 20 件など）をそろえていないため、目標の判定には使わない）だけを確かめた。

| 場面 | すること | 目標 |
|---|---|---|
| `dslLight` | 今の状態と履歴を同時 `VUS` で繰り返す | U4 の NFR1.10（今の状態・履歴の 95% が 1 秒以内）。閾値 `http_req_duration{name:dslStatus}`・`{name:dslHistory}` |
| `dslCycle` | 投入 → 適用 → 投入 → 破棄 → 履歴を繰り返す。投入は重い処理（同時に1つ）のため `VUS=1` で使う | U4 の NFR1.10（適用・破棄の 95% が 1 秒以内）。閾値 `{name:dslApply}`・`{name:dslDiscard}` |
| `dslMixed` | 10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 `VUS` 名のログインを重ねる | U4 の NFR1.12・U4-POOL（失敗しない。閾値は場面ごとの `checks` の率 1）。コンテナの上限 1g で流し、止まらないこと（OOMKilled）と NMT・Hikari の値を別に記録する |

```bash
# 例: 1種類だけ残して起動し（生成した DSL と 10MB の DSL が結果の置き場にできる）、同じ一時ディレクトリの ui.env（管理者のメールアドレスとパスワード）で k6 を流す
KEEP=1 MASTERSMITH_IMAGE_TAG=perf-dsl OUT_DIR="$PWD/build/perf-results/dsl-k6" ./perf/dsl-timing.sh postgres
D=<表示された一時ディレクトリ>
docker run --rm --network mastersmith-perf_default --env-file "$D/ui.env" -e SCENARIO=dslCycle -e VUS=1 -e DURATION=120s \
  -e DSL_FILE=/data/generated.yaml -v "$PWD/build/perf-results/dsl-k6/postgres:/data:ro" \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
  run --quiet --summary-export=/out/dslCycle.json /scripts/scenarios.js
```

- `dslLight` は `DSL_FILE` が要らない（履歴 20 件と想定の規模のプレビュー・適用中の DSL を先に置いておく。NFR1.10 の条件）。
- `dslMixed` は試験用の利用者 10 名（`perf-user01`〜`10`）と `PERF_USER_PASSWORD` が要る。前の節の手順 2（アプリを止めて内部DB に入れる）で入れ、`PERF_USER_PASSWORD` を `ui.env` に足してから流す。
