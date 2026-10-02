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

# 2. 使い捨ての環境を起動し（初期管理者が作られる）、止めて試験用の利用者 11 名を入れる（dslMixed が VUS + 1 名を使う。ほかの場面は 01〜10）
#    V7 の後は氏名（display_name）が必須のため、メールアドレスと同じ値を入れる（言語・テーマ・文字の大きさは既定の値）
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait
docker compose -p mastersmith-perf -f docker/perf/compose.yaml stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES $(for i in $(seq -w 1 11); do printf "('perf-user%s@example.test', '%s', FALSE, CURRENT_TIMESTAMP, 'perf-user%s@example.test')," "$i" "$HASH" "$i"; done | sed 's/,$//')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4_7-jre-noble java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait

# 3. 場面ごとに流す（health / loginSuccess / loginFailure / refresh / adminCheck / forbidden / appearance）
#    U2・U3 の場面は、下の節「利用者の設定と招待の場面」の手順（Mailpit と利用者の追加）で流す
mkdir -p build/perf-results && chmod 777 build/perf-results
docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=health -e DURATION=60s \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
  run --quiet --summary-export=/out/health.json /scripts/scenarios.js

# 4. 片付け
docker compose -p mastersmith-perf -f docker/perf/compose.yaml down -v
rm -rf "$D"
docker compose up -d --wait
```

- `appearance` の場面（Intent 260925-user-management の U8）は、トークンを付けない `GET /api/appearance` を同時 `VUS`（既定 10）で `DURATION` の間くり返し、全件が 200 で本文の項目がちょうど `brandColor`・`fontFamily` の2つであることを checks で確かめる。試験用の利用者とトークンは要らず、監査ログも増えない。目標は U8 の NFR6.1（95% が 300 ミリ秒以内）で、閾値は `http_req_duration{name:appearance}` の `p(95)<300`。軽い API の決まりどおり、内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。
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

- **コンテナの上限は配備と同じ CPU 4・メモリ 2g**（`MASTERSMITH_CONTAINER_MEMORY` の既定を 2g にしている）。U4 の NFR1.12 の条件は、前の Intent の Performance Validation の決定（1g では OOMKilled で止まった）により配備の既定 2g とした。比べるために 1g で測るときは `MASTERSMITH_CONTAINER_MEMORY=1g` を付けて流す（1g では JVM の GC が G1 ではなく Serial になる。前の節を参照）。
- **配備したアプリ（プロジェクト `mastersmith`）は止めない**。colima の VM（CPU 4・メモリ 6GiB）には、配備したアプリ（上限 2g）と、使い捨てのアプリ（2g）・対象DB 1つ（最大 768MB）が同時に収まる。台本は対象DB を1種類ずつ起動し、測り終えたら消してから次の種類に進む。配備したアプリは待機中のため CPU の取り合いは小さいが、測った値には影響が混ざりうる。負荷の試験（k6）で CPU を使い切るときは、前の節の手順 0 のとおり配備したアプリを止める。
- 使い捨ての環境だけに要求を送る（`127.0.0.1:18080`）。配備したアプリ・その内部DB・監査ログには触れない。
- 途中で残したいときは `KEEP=1` を付ける（種類は1つだけ指定する）。終わったら、表示された一時ディレクトリの場所を使って片付ける（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v` と一時ディレクトリの削除。`-f` の compose は `MASTERSMITH_PERF_ENV_FILE` が要るため、`MASTERSMITH_PERF_ENV_FILE=/dev/null` を付けて呼ぶ）。

### 追加の確かめ（--lang・--pattern・--storage・--compact）

`perf/dsl-timing.sh` に次のオプションを付けると、上の測定の後に続けて行う（組み合わせてよい。PostgreSQL の1種類で足りる）。

```bash
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --lang --pattern --storage postgres
```

| オプション | すること | 目標 | 結果のファイル |
|---|---|---|---|
| `--lang` | 英語のロケール（`en-US`）の Chromium で `/admin/dsl` を開き、対象DB に無いテーブルを持つ DSL を貼り付けて投入（照合の警告）、続けて誤りを含む DSL を貼り付けて投入（422）。応答の `message` と、画面の警告・誤りの一覧に日本語の文字が無いこと、画面に応答の `message` がそのまま出ることを確かめる（`perf/ui/dsl-ui-lang.mjs`） | U5-LANG-E2E | `ui-lang.json`（`pass`） |
| `--pattern` | 1,000 文字近くの重い正規表現（深い入れ子の繰り返し・`(a+)+` の並び・大きな繰り返しの回数・Unicode の文字の種類の積・遅延の繰り返しの選択）を `pattern` に 2,000 個持つ DSL と、同じ形で `pattern` の無い DSL を作り（`perf/make-pattern-dsl.mjs`）、普通 3 回 → 重い 3 回 → 重いものの直後に待たずに普通 → 普通 3 回の順に投入する | U2-PATTERN-COMPILE（重い投入が短く終わり、直後の普通の投入が遅れない。アプリのログに「正規表現の確かめを時間の上限で打ち切りました」「受け付けられませんでした」が出ない） | `timings.tsv` の `pattern-*`・`pattern-dsl.txt`・`app.log` |
| `--storage` | 10MB の DSL（埋め草の先頭の行に回の番号を入れ、大きさは同じ）の投入→適用を `STORAGE_ROUNDS` 回（既定 21）くり返し、最後にプレビューも1件置く（プレビュー1件と履歴 20 件の最大の状態）。回ごとに H2 のファイル（`/app/data/mastersmith.mv.db`）の大きさ、コンテナのメモリ（cgroup の `memory.current`・`memory.peak`、`memory.stat` の `anon`（プロセスのメモリ）と `file`（ページキャッシュ。回収できる））、履歴の件数を記録する。最後にアプリを `docker compose stop`（`stop_grace_period` 45 秒）で止め、止めるのにかかった秒数と終わり方（exit code。SIGTERM で正常に終われば 143、猶予切れや OOM の SIGKILL は 137）・止めている間の H2 のファイルの大きさ（同じイメージの一時のコンテナでボリュームを読むだけ）・起動から healthy までの秒数・起動し直した後の大きさを記録し、止める前と後のデータ（適用中の DSL とプレビューの DSL の SHA-256、プレビューの previewId、履歴の版・dslHash・件数）を比べる。その後、履歴のすべての版を戻して、プレビューの本文の SHA-256 が履歴の dslHash と一致する数を記録する（`restore_all_match`。詰め直しで本文が壊れていないか） | U4-STORAGE | `storage.tsv`・`memory-peak-before-restart.txt`・`memory-stat-before-restart.txt`・`stop-state.txt`・`data-while-stopped.txt`・`snapshot-before-stop.txt`・`snapshot-after-restart.txt`・`history-after-restart.txt`・`app-before-restart.log` |
| `--compact` | `--storage` と組み合わせる。最後のプレビューを置いた後、止める前に、アプリを止めずに内部DB を詰め直す道具（`docker/hikari-pool.sh compact --container mastersmith-perf-app-1`）を流す。道具の出力（接続の本数・前と後の大きさ・かかった時間）と終わりの値・道具全体の秒数を記録し、`storage.tsv` に `after-compact` の行を足し、詰め直しの前後のデータ（適用中の DSL とプレビューの DSL の SHA-256、previewId、履歴）を比べ、詰め直しの後の健全性を記録する。その後の `--storage` の止める・起動し直す・`restore_all_match` はそのまま続く（詰め直しの後の本文も確かめる） | FR1.4・NFR1（Intent 260925-storage-memory-fixes） | `compact.txt`・`snapshot-before-compact.txt`・`snapshot-after-compact.txt` |

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
| `dslMixed` | 10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 `VUS` 名のログインを重ねる | U4 の NFR1.12・U4-POOL（失敗しない。閾値は場面ごとの `checks` の率 1）。コンテナの上限は配備の既定 2g で流し、止まらないこと（OOMKilled）と NMT・Hikari の値を別に記録する |

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
- `dslMixed` は試験用の利用者 11 名（`perf-user01`〜`11`）と `PERF_USER_PASSWORD` が要る。前の節の手順 2（アプリを止めて内部DB に入れる）で入れ、`PERF_USER_PASSWORD` を `ui.env` に足してから流す。ログインの VU は試験全体で重ならない番号（`exec.vu.idInTest`）で利用者を選び、番号は `VUS + 1` まで取りうるため、利用者は `VUS + 1` 名要る。`VUS` を変えるときは利用者を足し、`-e PERF_USER_COUNT=<入れた数>`（既定 11）を渡す（足りないと始める前に止まる）。
- `dslMixed` の試験用の利用者は、ロックの状態の行が無いまま流す（先に1人ずつログインして行を作らない）。同じ利用者の初めてのログインが同時に来ても 500 にならないこと（Intent 260924-followup-fixes の FR2 の直し）の確かめを兼ねる。合格はログインの `checks` の率 1（500 が 0 件）。
- 利用者が VU の間で重ならないことは、k6 の出力の `loginLoop-user vu=<番号> user=<利用者>` の行（VU ごとの最初の回に1行）で確かめる。行の数が `VUS` と同じで、利用者の重なりが 0 件であること（例: `grep -o 'loginLoop-user vu=[0-9]* user=[^ "]*' <k6 の出力> | sed 's/.* user=//' | sort | uniq -d` が何も出さない）。

## 利用者の設定と招待の場面（Intent 260925-user-management の U2・U3）

`perf/k6/scenarios.js` の U2（自分の設定・パスワードの変更）と U3（招待と登録の完了）の場面を、使い捨ての環境で流す。目標の出典は `aidlc/spaces/default/intents/260925-user-management/construction/` の `u2-user-preferences/nfr-requirements/performance-requirements.md`（表の U2-）と `u3-invitation/nfr-requirements/performance-requirements.md`（表の U3-）。どれも同時 `VUS`（既定 10）で、場面ごとに p95 を判定する（1つの場面の遅さをほかの場面で薄めない）。閾値は出典の値のままで、緩めない。あわせて場面ごとに `checks` の率 1 を閾値に置く（状態コードの誤りを混ぜた p95 で合格にしない）。Build and Test では台本と手順を用意し、`k6 inspect` で読み込めることだけを確かめた。測定は performance-validation で行う。

| 場面 | すること | 目標（閾値） | 用意 |
|---|---|---|---|
| `preferencesGet` | `GET /api/me/preferences`（200） | U2-NFR6.1（`{name:preferencesGet}` の p95 1 秒） | `perf-user01`〜`10` を VU ごとに当てる |
| `preferencesSave` | `PUT /api/me/preferences` の成功（200。テーマを回ごとに変える） | U2-NFR6.2（p95 1 秒） | 同上 |
| `preferencesInvalid` | 同じ API の入力の誤り（言語 `xx`、400 `VALIDATION_FAILED`） | U2-NFR6.2（p95 1 秒） | 同上 |
| `passwordChange` | `POST /api/me/password` の成功（204） | U2-NFR6.3（p95 **2 秒**） | 専用の `perf-pw01`〜`10` を VU ごとに1人（`VUS` 名以上。`PERF_PW_USER_COUNT`、既定 10）。変更の前後のパスワード（`PERF_USER_PASSWORD` と、その後ろに `-alt` を足した値）を交互に使う |
| `passwordMismatch` | 今のパスワードの誤り（400 `PASSWORD_CURRENT_MISMATCH`。監査に1件ずつ残る） | U2-NFR6.4（p95 1 秒） | `perf-user01`〜`10` |
| `passwordInvalid` | 入力の誤り（新しいパスワードが短く確かめと違う、400 `VALIDATION_FAILED`） | U2-NFR6.4（p95 1 秒） | 同上 |
| `invite` | `POST /api/admin/invitations`（201、送信の結果 `SENT`） | U3-NFR6.1（p95 **5 秒**） | 回ごとに違う宛先（`perf-inv<VU>-<実行の識別>-<回>@example.com`）で、409 を混ぜない |
| `invitationResend` | 送り直し（200、`SENT`） | U3-NFR6.1（p95 **5 秒**） | setup が `VUS` 件の招待を出し、VU ごとに1件を送り直す |
| `invitationList` | 一覧の1ページ目と最後のページ（200） | U3-NFR6.3（`{name:invitationListFirst}`・`{name:invitationListLast}` の p95 1 秒） | setup が招待中を `LIST_PENDING_MIN`（既定 45）件以上にする（20 件より多く） |
| `invitationCancel` | 取り消し（204） | U3-NFR6.3（p95 1 秒） | setup が `ITERATIONS`（既定 100）件の招待を出し、全 VU で1回ずつ取り消す（回数で終わる。`DURATION` は使わない） |
| `registrationVerify` | リンクの確かめ（有効なトークン 200、形の誤り・見つからないトークン 404） | U3-NFR6.3（3つの name ごとに p95 1 秒） | setup が `VUS` 件の招待を出し、Mailpit で受けたメールからトークンを取り出す（確かめは招待を消費しない） |
| `registrationComplete` | 登録の完了の成功（204） | U3-NFR6.4（p95 1 秒） | setup が `ITERATIONS` 件の招待を出し、Mailpit で受けたメールからトークンを取り出す。全 VU で1回ずつ使い切る（回数で終わる） |
| `registrationInvalid` | 登録の完了の入力の誤り（400 `VALIDATION_FAILED`。内部DB を引かない） | U3-NFR6.5（p95 1 秒） | なし |
| `registrationRejected` | 登録の完了のリンクの拒否（見つからない・形の誤り、404 `REGISTRATION_LINK_INVALID`。監査に1件ずつ残る） | U3-NFR6.5（2つの name ごとに p95 1 秒） | なし |

- BR7.4 の経路（完了の時点で同じメールアドレスの利用者がいる拒否）は場面に入れない（U3 の NFR 設計の4節、Unverified）。
- 既存のログイン・トークンの更新の目標（U2-NFR6.5）は、既存の `loginSuccess`・`refresh` を流し直して確かめる。
- トークンの取り出し: setup が管理者として招待の API で招待を出し（全件が 201 で送信の結果が `SENT` でなければ止める）、使い捨ての環境の Mailpit の API（`MAILPIT_URL`、既定 `http://mailpit:8025`。PC にポートを公開していないため、k6 はコンテナの名前で届く）で宛先が完全に一致するメールを探し、本文のリンク（`/register#token=…`）からトークンを取り出す。トークン・リンク・本文は、ログ・`console.log`・止めるときの文言・結果の出力に出さない（出すのは件数だけ）。

```bash
# 0・1. 上の「手順」の 0・1 と同じ（配備したアプリを止め、WAR とイメージを用意し、一時の環境ファイルを作る）

# 1'. app.env にメールの受け手・差出人・ベース URL の行を足す（秘密ではない値）。ベース URL を k6 の BASE_URL と同じにし、
#     k6 が付ける Origin のまま既存のログイン・更新の場面が 403 にならないようにする
( umask 077
  printf 'SPRING_MAIL_HOST=mailpit\nSPRING_MAIL_PORT=1025\nMASTERSMITH_MAIL_FROM=perf-noreply@example.com\nMASTERSMITH_WEB_BASE_URL=http://app:8080\n' >> "$D/app.env" )
# 接続の待ちも見るとき（performance-validation の U2-NFR5.2・U3-NFR5.3）は、使い捨てのアプリにだけ次の行を足す
# ( umask 077; printf 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics\n' >> "$D/app.env" )

# 2'. 上の手順 2 の代わりに行う。profile mail で Mailpit ごと起動し、アプリを止めて、試験用の利用者 11 名
#     （perf-user01〜11）とパスワードの変更の専用の利用者 10 名（perf-pw01〜10）を入れる（氏名は必須のためメールアドレスと同じ値）。
#     zsh ではコマンドを変数に入れても単語に分かれないため、関数にする
perfc() { docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile mail "$@"; }
docker info --format '{{.NCPU}} CPU / {{.MemTotal}} bytes'   # VM の余裕を読み取りで確かめる（アプリ 2g・Mailpit 256m・k6）
perfc up -d --wait
perfc stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES $(
  { for i in $(seq -w 1 11); do echo "perf-user$i"; done; for i in $(seq -w 1 10); do echo "perf-pw$i"; done; } |
  while read -r u; do printf "('%s@example.test', '%s', FALSE, CURRENT_TIMESTAMP, '%s@example.test')," "$u" "$HASH" "$u"; done | sed 's/,$//')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4_7-jre-noble java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfc up -d --wait

# 3'. 場面ごとに流す（caffeinate -i で PC のスリープを防ぐ）。k6 は同じネットワークの中から app と mailpit に届く
mkdir -p build/perf-results && chmod 777 build/perf-results
for s in preferencesGet preferencesSave preferencesInvalid passwordChange passwordMismatch passwordInvalid \
  invite invitationResend invitationList invitationCancel registrationVerify registrationComplete registrationInvalid registrationRejected; do
  caffeinate -i docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=$s -e DURATION=60s \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
    run --quiet --summary-export=/out/$s.json /scripts/scenarios.js
done

# 4'. 秘密が出ていないことを件数で確かめる（どれも 0 であること）。ログはファイルに残さず、数だけを見る
docker logs mastersmith-perf-app-1 2>&1 | grep -c '/register#token='
docker logs mastersmith-perf-app-1 2>&1 | grep -c '@example.com'
grep -l 'register#token=' build/perf-results/*.json | wc -l

# 5'. 片付け（確かめの結果を見てから行う。監査の件数を数えるときは、アプリを止めて H2 の道具で読んでから消す）
perfc down -v
rm -rf "$D"
docker compose up -d --wait
```

- 監査の件数（performance-validation の U2-NFR5.2・U3-NFR5.3。流した成功の件数と `PASSWORD_CHANGED`・`INVITATION_ISSUED`・`REGISTRATION_COMPLETED` の成功の件数の突き合わせ）は、`perfc stop app` の後に手順 2' と同じ H2 の道具で `SELECT event_type, result, COUNT(*) FROM audit_events GROUP BY event_type, result` を読む（読み取りだけ）。数え終わるまで `down -v` をしない。
- `passwordChange` の前後のパスワード: VU の最初の回に、入れたときのパスワードでログインし、入れなければ後ろに `-alt` を足した値でログインする（前の実行が後ろの値で終わっていることがあるため）。そのときはログインの失敗が1件、監査に残る。以後は変更が 204 になるたびに今のパスワードを入れ替え、4分ごとのログインし直しも今のパスワードで行う。
- `passwordChange` の利用者は `perf-pw01`〜だけで、ほかの場面では使わない（同じ利用者を同時に変えると条件つきの更新で 400 が混ざるため。U2 の NFR 設計の7節）。`VUS` を増やすときは利用者を足し、`-e PERF_PW_USER_COUNT=<入れた数>` を渡す（足りないと始める前に止まる）。
- `invitationCancel`・`registrationComplete` の回数は `-e ITERATIONS=<回数>`（既定 100）で変える。setup は同じ数の招待を同時 `VUS` 件ずつ出すため、回数が多いほど setup が長くなる（上限 10 分）。
- 招待・送り直し・setup の招待は、招待中の行と Mailpit のメールを増やす。Mailpit はボリュームを持たず、`down -v` で消える（既定で古いメールから 500 通を超えた分を消す。setup は招待の直後に読むため影響しない）。
- k6・Mailpit はアプリと同じ VM の CPU とメモリを分け合うため、測った値にその分が混ざりうる。結果に明記する。
- `@example.com` は招待の宛先（予約されたドメイン）、`@example.test` は試験用の利用者と仮の管理者。アプリのログにはメールアドレスを出さない決まりのため、手順 4' の件数はどれも 0 になるはず。

## 利用者の管理の場面（Intent 260930-user-admin の U3）

`perf/k6/scenarios.js` の利用者の管理の場面を、使い捨ての環境で流す。目標の出典は `aidlc/spaces/default/intents/260930-user-admin/construction/u3-user-admin-api/nfr-requirements/` の `performance-requirements.md`（NFR5.1〜NFR5.7）と `reliability-requirements.md`（NFR6.2・NFR6.3）。どの場面も閾値は出典のまま（p95 1000 ms と `checks` の率 1）で、緩めない。正とする値は k6 の値（クライアント側）で、サーバー側の `http_server_requests` の p95 は参考として並べる。Build and Test では台本と手順を用意し、`k6 inspect`（`--include-system-env-vars` を付ける）で読み込めることと場面の名前だけを確かめる。測定は performance-validation で行う。

| 場面 | すること | 目標（閾値） | 用意 |
|---|---|---|---|
| `userAdminList` | 一覧（`GET /api/admin/users`、200 と `total`）。`LIST_CASE` で区分を選ぶ: `a` 検索なしの1ページ目、`b` 検索なしの最後のページ（`page=50`）、`c` 多く当たる検索（`q=perf-ua-`、1,000 件）、`d` ほとんど当たらない検索（`q=zz-no-such-user`、0 件） | NFR5.1（区分ごとに `{name:userAdminList}` の p95 1 秒、`checks` の率 1） | 試験用の利用者 1,000 名（`perf-ua-0001`〜`1000`）。区分ごとに流して判定する |
| `userAdminProfile` | 氏名と言語の変更の成功（204。言語を回ごとに変える）と入力の誤り（400 `VALIDATION_FAILED`） | NFR5.3（`{name:userAdminProfile}`・`{name:userAdminProfileInvalid}` の p95 1 秒） | VU ごとに `perf-uapf<VU>` |
| `userAdminOps` | 5つの操作を組で状態を戻しながらくり返す（印を付ける → 外す → 止める → 解く → ログインを1回失敗させて → 失敗回数を戻す。どれも 204） | NFR5.4・NFR5.6（操作ごとに `{name:userAdminGrant}`・`Revoke`・`Suspend`・`Resume`・`Reset` の p95 1 秒、`checks`（204）の率 1。409 `USER_ADMIN_BUSY`・`USER_ADMIN_NO_CHANGE` と 5xx が 0 件） | 操作する管理者 `perf-uaop<VU>`、対象 `perf-uat<VU>`（VU ごとに分ける）。1つの VU の回数は `UA_ROUNDS`（既定は 100 ÷ `VUS` の切り上げ。操作ごとに 100 回以上） |
| `userAdminSuspendWorst` | 未無効 100 件・無効 1,000 件のリフレッシュトークンを持つ対象を止めて解く（回ごとに別の対象） | NFR5.5（`{name:userAdminSuspendWorst}` の p95 1 秒、`checks` の率 1） | 対象 `perf-uasw-<VU>-<回>`（`VUS` × `UA_ROUNDS` 名、既定 100 名）とトークンの行 |
| `userAdminPool` | 奇数の VU が5つの操作の1回分、偶数の VU が一覧（`a`）を同時にくり返す | NFR6.2（`checks` の率 1。接続プールは hikaricp の値で判断する） | `userAdminOps` と `userAdminList` と同じ |

- **受け入れの条件（NFR5.4）**: 操作する管理者（`perf-uaop<VU>`）はどの操作の対象にもしない。初期管理者（`perf-admin@example.test`）は操作する人にも対象にもしない。対象は VU ごとに分け、2つの VU が同じ利用者を対象にしない。台本は VU の番号でこれを守る（`VUS` は `PERF_UA_COUNT`（既定 10）以下。超えると始める前に止まる）。
- **準備のログインの失敗**（`{name:userAdminPrepLogin}`、401）は、失敗回数を戻す組で失敗回数を 1 以上にするためのもので、`checks` にも p95 の判定にも数えない。監査に `LOGIN_FAILED` が1件ずつ残る（5つの操作の件数には数えない）。
- **対象の利用者 ID**: setup が `perf-uaop01` で一覧の API を検索して引く（ID だけを持ち、値は出力しない）。足りないと始める前に止まる。
- **止める悪い側のくり返し**: 止めるとトークンがすべて無効になるため、回ごとに別の対象を使う。流し直すときは、環境を作り直して（手順 5'' と 2''）入れ直す。
- **接続プール（NFR6.2・NFR6.3）**: 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics` の `hikaricp.connections.timeout`（待ちの時間切れの累計）と `hikaricp.connections.acquire`（借りるまでの待ちの最大）で判断する。数秒ごとの使用中の数では判断しない。NFR6.2 は上限 30（既定）で `userAdminPool` を流し、時間切れの累計 0・500 が 0 件・流した5つの操作の成功の件数と監査の `SUCCESS` の件数の一致を見る。NFR6.3 は使い捨てのアプリの上限を `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` にして `userAdminOps` を (A) `VUS=5`（見積もり 10 本で上限ちょうど。時間切れの累計 0・件数が一致）と (B) `VUS=10`（見積もり 20 本で上限の2倍。2本目の待ちが出る）で流す。(B) は待ちを起こす場面のため、欠けた監査と ERROR の件数を記録し、p95 と件数の一致に数えない。(A)・(B) の後は上限を既定に戻して起動し直すか、環境を作り直す。配備したアプリの公開の範囲と上限は変えない。
- 長い試験は `caffeinate -i` で台本の全体を包む（場面ごとに起こし直さない）。遅れが出たら `pmset -g log` でスリープを確かめる。
- k6 はアプリと同じ VM の CPU を分け合うため、測った値にその分が混ざりうる。結果に明記する。
- 目標に届かないときは目標を緩めず、原因をログと状態で確かめて依頼者に相談する。一覧が NFR5.1 に届かなければ、`users (created_at, user_id)` の索引を足す直しを諮る。

```bash
# 0・1. 上の「手順」の 0・1 と同じ（配備したアプリを止め、WAR とイメージを用意し、一時の環境ファイルを作る）。
#       U3 はメールを送らないため Mailpit（profile mail）は起動しない
# 1''. 接続プールを見る場面（userAdminPool・上限 10 の場面）では、使い捨てのアプリにだけ次の行を足す
( umask 077; printf 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics\n' >> "$D/app.env" )

# 2''. 使い捨ての環境を起動し、止めて、試験用の利用者とトークンの行を入れる（メールアドレスは予約のドメインだけ）。
#      perf-ua-0001〜1000（一覧）、perf-uaop01〜10（操作する管理者）、perf-uat01〜10（5つの操作の対象）、
#      perf-uapf01〜10（氏名と言語の対象）、perf-uasw-01-01〜10-10（止める悪い側の対象。1人に未無効 100 件・無効 1,000 件）
perfu() { docker compose -p mastersmith-perf -f docker/perf/compose.yaml "$@"; }
docker info --format '{{.NCPU}} CPU / {{.MemTotal}} bytes'   # VM の余裕を読み取りで確かめる（アプリ 2g・k6）
perfu up -d --wait
perfu stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-ua-' || LPAD(CAST(X AS VARCHAR), 4, '0') || '@example.test', '$HASH', FALSE,
    DATEADD('SECOND', X, CURRENT_TIMESTAMP), 'perf-ua-' || LPAD(CAST(X AS VARCHAR), 4, '0') FROM SYSTEM_RANGE(1, 1000);
INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-' || K || LPAD(CAST(X AS VARCHAR), 2, '0') || '@example.test', '$HASH', K = 'uaop', CURRENT_TIMESTAMP,
    'perf-' || K || LPAD(CAST(X AS VARCHAR), 2, '0') FROM SYSTEM_RANGE(1, 10), (VALUES 'uaop', 'uat', 'uapf') V(K);
INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-uasw-' || LPAD(CAST(A.X AS VARCHAR), 2, '0') || '-' || LPAD(CAST(B.X AS VARCHAR), 2, '0') || '@example.test',
    '$HASH', FALSE, CURRENT_TIMESTAMP, 'perf-uasw' FROM SYSTEM_RANGE(1, 10) A, SYSTEM_RANGE(1, 10) B;
INSERT INTO refresh_tokens (user_id, token_hash, issued_at, expires_at, revoked_at)
  SELECT u.user_id, HASH('SHA-256', STRINGTOUTF8(u.email || '-' || r.X)), CURRENT_TIMESTAMP, DATEADD('DAY', 30, CURRENT_TIMESTAMP),
    CASE WHEN r.X <= 100 THEN NULL ELSE CURRENT_TIMESTAMP END FROM users u, SYSTEM_RANGE(1, 1100) r WHERE u.email LIKE 'perf-uasw-%'"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4_7-jre-noble java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfu up -d --wait

# 3''. 場面ごとに流す。台本の全体を caffeinate -i で包む（場面ごとに起こし直さない）
mkdir -p build/perf-results && chmod 777 build/perf-results
cat > "$D/ua-run.sh" <<'EOS'
D="$1"
k6run() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" "$@" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
    run --quiet --summary-export=/out/$name.json /scripts/scenarios.js
}
metrics() {   # 接続プールの値（待ちの時間切れの累計・借りるまでの待ちの最大）を記録する
  for m in hikaricp.connections.timeout hikaricp.connections.acquire; do
    curl -s "http://127.0.0.1:18080/actuator/metrics/$m" > "build/perf-results/$1-$m.json"
  done
}
for c in a b c d; do k6run userAdminList-$c -e SCENARIO=userAdminList -e LIST_CASE=$c -e DURATION=60s; done
k6run userAdminProfile -e SCENARIO=userAdminProfile -e DURATION=60s
k6run userAdminOps -e SCENARIO=userAdminOps
k6run userAdminSuspendWorst -e SCENARIO=userAdminSuspendWorst
metrics before-pool
k6run userAdminPool -e SCENARIO=userAdminPool -e DURATION=60s
metrics after-pool
EOS
caffeinate -i zsh "$D/ua-run.sh" "$D"

# 3'''. 上限 10 の場面（NFR6.3）。使い捨てのアプリの上限を 10 にして起動し直し、(A) VUS=5 と (B) VUS=10 で 5つの操作を流す
( umask 077; printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10\n' >> "$D/app.env" )
perfu up -d --wait --force-recreate app
cat > "$D/ua-pool10.sh" <<'EOS'
D="$1"
for v in 5 10; do
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=userAdminOps -e VUS=$v \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
    run --quiet --summary-export=/out/userAdminOps-pool10-vus$v.json /scripts/scenarios.js
  for m in hikaricp.connections.timeout hikaricp.connections.acquire; do
    curl -s "http://127.0.0.1:18080/actuator/metrics/$m" > "build/perf-results/pool10-vus$v-$m.json"
  done
done
EOS
caffeinate -i zsh "$D/ua-pool10.sh" "$D"
docker logs mastersmith-perf-app-1 2>&1 | grep -c '監査イベントの記録に失敗しました'   # (B) の欠けた監査の件数と突き合わせる

# 4''. 秘密が出ていないことを件数で確かめる（どれも 0 であること）。ログはファイルに残さず、数だけを見る
#      （初期管理者の起動のログには伏せ字のメールアドレスが出るため、試験用の利用者の名前の部分で数える）
docker logs mastersmith-perf-app-1 2>&1 | grep -c 'perf-ua'
docker logs mastersmith-perf-app-1 2>&1 | grep -c 'MVStoreException'

# 5''. 監査の件数を数えてから片付ける（アプリを止め、手順 2'' と同じ H2 の道具で読み取りだけ。数え終わるまで down -v をしない）
#      SELECT event_type, result, failure_reason, COUNT(*) FROM audit_events WHERE event_type IN ('USER_ADMIN_GRANTED',
#        'USER_ADMIN_REVOKED', 'USER_SUSPENDED', 'USER_RESUMED', 'LOGIN_FAILURES_RESET') GROUP BY event_type, result, failure_reason
perfu stop app
perfu down -v
rm -rf "$D"
docker compose up -d --wait
```

- 監査の件数の突き合わせ（NFR6.2）: 流した5つの操作の成功の件数（k6 の結果の `checks` の成功の数。操作ごとの `{name:...}` の `http_reqs` の数）と、5つの種類の `SUCCESS` の件数が一致することを見る。(B) の欠けた件数は、アプリのログの `監査イベントの記録に失敗しました` の件数と、待ちの時間切れの累計に合うかを記録する。
- 監査の件数を数えるときは、上限 10 の場面の分も同じ内部DB に入る。場面ごとに分けて数えたいときは、場面の前後で件数を読む（アプリを止めて読み、起動し直す）。
