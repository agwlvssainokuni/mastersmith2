# 負荷の試験（k6）

使い捨ての環境（`docker/perf/compose.yaml`）に k6 で負荷をかけ、性能の目標（95 パーセンタイル）を確かめる。配備した環境（`compose.yaml`）のデータと監査ログには触れない。計画と結果は `aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/` にある。

## 手順

**PC の眠りの守り（すべての節に共通）**: 長い試験（k6・`perf/dsl-timing.sh`）は、台本の全体を `caffeinate -dims` で包む（場面ごとに起こし直さない。切れ目で守りが外れるため）。`caffeinate -i` だけでは画面の消灯と DarkWake の Thermal Emergency のスリープを止められず、試験が止まった例がある（Intent 261004-role-menu の B8）。流す前に `pmset -g assertions` で眠らない状態（`PreventUserIdleSystemSleep` などが 1）を確かめ、流した後に `pmset -g log` で試験の時間の窓に `Sleep`・`DarkWake` が無いことを確かめる。入っていたら、その回の結果は使わずに流し直す。

```bash
# 流した後の眠りの確かめ（始めと終わりの時刻は台本が記録した UTC を手元の時刻に直して読む）
pmset -g log | grep -E ' (Sleep|DarkWake|Wake) ' | tail -n 20
```

```bash
# 0. 配備したアプリを止め（CPU を取り合って測定の値がぶれないため）、WAR とイメージを用意する。
#    イメージは Intent ごとの別のタグで作る（docker compose build app は配備したアプリのタグ local を上書きし、
#    後の docker compose up で配備したアプリが試験の版に作り直されるため）。タグは手順 1 の MASTERSMITH_IMAGE_TAG で渡す
docker compose stop app
./gradlew :backend:bootWar && docker build -t mastersmith:<Intent の名前> .   # 例: mastersmith:user-admin-followup

# 1. リポジトリの外に一時の環境ファイルを作る（値は乱数。表示しない）。置き場はホームの下（権限 700）にする
#    （macOS の一時ディレクトリは colima の VM から見えず、コンテナとの受け渡しが空振りするため）
D=$(mktemp -d "$HOME/.mastersmith-perf-XXXXXX") && chmod 700 "$D"
AP=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24); UP=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
( umask 077
  printf 'MASTERSMITH_AUTH_SIGNING_KEY=%s\nMASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=perf-admin@example.test\nMASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 32)" "$AP" > "$D/app.env"
  printf 'PERF_ADMIN_EMAIL=perf-admin@example.test\nPERF_ADMIN_PASSWORD=%s\nPERF_USER_PASSWORD=%s\n' "$AP" "$UP" > "$D/k6.env" )
# コンテナの上限は配備と同じ値（CPU 4・メモリ 2g）。-f で指定する compose は .env を読まないため、シェルの環境変数で渡す
export MASTERSMITH_PERF_ENV_FILE="$D/app.env" MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g \
  MASTERSMITH_IMAGE_TAG=<Intent の名前>   # 手順 0 で作ったタグ（docker/perf/compose.yaml の既定は local）

# 2. 使い捨ての環境を起動し（初期管理者が作られる）、止めて試験用の利用者 11 名を入れる（dslMixed が VUS + 1 名を使う。ほかの場面は 01〜10）
#    V7 の後は氏名（display_name）が必須のため、メールアドレスと同じ値を入れる（言語・テーマ・文字の大きさは既定の値）
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait
docker compose -p mastersmith-perf -f docker/perf/compose.yaml stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES $(for i in $(seq -w 1 11); do printf "('perf-user%s@example.test', '%s', FALSE, CURRENT_TIMESTAMP, 'perf-user%s@example.test')," "$i" "$HASH" "$i"; done | sed 's/,$//')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait

# 3. 場面ごとに流す（health / loginSuccess / loginFailure / refresh / adminCheck / forbidden / appearance）
#    U2・U3 の場面は、下の節「利用者の設定と招待の場面」の手順（Mailpit と利用者の追加）で流す
mkdir -p build/perf-results && chmod 777 build/perf-results
docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=health -e DURATION=60s \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
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
nmt() { docker run --rm --pid container:mastersmith-perf-app-1 -u 10001:10001 eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:589ff4cc3f71aab462e7048a47a0d10edf57fbccde3fceea2281e610bf5880b4 jcmd 1 "$@"; }
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

台本と道具が作る・送る DSL は、すべて書式の**版 2**（Intent 261004-role-menu の U2 dsl-v2。`version: 2`、スキーマはちょうど1つで、テーブルは `schemas.<スキーマ>.tables` の下、メニューのテーブルは `{schema, name}`）。版 1 の DSL は書式の版の誤り（422）になる。

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
- 10MB の DSL は `perf/make-large-dsl.mjs` が、生成した版 2 の DSL のスキーマの `tables` の下の先頭のテーブルを、同じスキーマの下に別名（`x_<番号>_<元の名前>`）で写して足し、足りない分（数十 KB）を末尾の YAML のコメントで埋めて作る。誤りを含むものは、写したテーブルのすべてのカラムの `formPart` を書式に無い値にしたもの（JSON Schema の誤りが多数）と、最後に写したテーブルの主キーを無いカラムにしたもの（意味の誤りが末尾に1件）。リポジトリには置かず、結果の置き場（`build/` の下）にだけ作る。
- ヒープの使用（`heap.tsv`）: 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics/jvm.memory.used?tag=area:heap` の `VALUE`（バイト。その時点の使用量で、GC の前後で上下する）を、10MB の正しい DSL の投入の後（`after-submit-10mb-valid`）・その表示の後（`after-preview-10mb-first`）・`--busy` の後（`after-busy`）に記録する。Intent 261004-role-menu の U2 dsl-v2 の見積もり（10MB の投入・表示で 256〜384 MB 程度）と並べて読む記録で、判定は今までどおり OOMKilled・`memory.peak` で行う。配備したアプリの公開の範囲は変えない。
- 結果は `build/perf-results/dsl-<日時>/` に置く。`timings.tsv`（要求ごとの時間）・`heap.tsv`（ヒープの使用）・`busy.tsv`（`--busy` のとき）・`summary.md`（`perf/dsl-timing-report.mjs` のまとめ）・種類ごとの `app.log`・`gc.log`・`generate-breakdown.log`・`memory-peak.txt`・`state.txt`（OOMKilled の有無）・応答の本文（`resp-*.body`）・画面の時間（`ui-timing.json`）。応答とログに秘密情報は含まれない（仮の管理者のメールアドレスは `perf-admin@example.test`）。
- 画面の時間（`perf/ui/dsl-ui-timing.mjs`、`--ui` のとき）: 想定の規模のプレビュー（生成した DSL）を置いてから、Chromium（画面なし）でログインし、サイドバーの「DSL」から開く場合と `/admin/dsl` を読み直す場合のそれぞれで、今の状態とプレビューが表示されるまでの時間（NFR1.18）、違いの表の最初の行を開いてカラムの表が出るまでの時間（NFR1.19）、10MB のファイルを選んで「投入」と置き換えの確かめの後に送り始めるまでの時間（NFR1.20）を測る。照合を除く時間は、表示までの時間から `GET /preview` のサーバーの時間（送ってから最初のバイトまで）を引いて出す。

前提と注意:

- **コンテナの上限は配備と同じ CPU 4・メモリ 2g**（`MASTERSMITH_CONTAINER_MEMORY` の既定を 2g にしている）。U4 の NFR1.12 の条件は、前の Intent の Performance Validation の決定（1g では OOMKilled で止まった）により配備の既定 2g とした。比べるために 1g で測るときは `MASTERSMITH_CONTAINER_MEMORY=1g` を付けて流す（1g では JVM の GC が G1 ではなく Serial になる。前の節を参照）。
- **配備したアプリ（プロジェクト `mastersmith`）は止めない**。colima の VM（CPU 4・メモリ 6GiB）には、配備したアプリ（上限 2g）と、使い捨てのアプリ（2g）・対象DB 1つ（最大 768MB）が同時に収まる。台本は対象DB を1種類ずつ起動し、測り終えたら消してから次の種類に進む。配備したアプリは待機中のため CPU の取り合いは小さいが、測った値には影響が混ざりうる。負荷の試験（k6）で CPU を使い切るときは、前の節の手順 0 のとおり配備したアプリを止める。
- 使い捨ての環境だけに要求を送る（`127.0.0.1:18080`）。配備したアプリ・その内部DB・監査ログには触れない。
- 途中で残したいときは `KEEP=1` を付ける（種類は1つだけ指定する）。終わったら、表示された一時ディレクトリの場所を使って片付ける（`docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v` と一時ディレクトリの削除。`-f` の compose は `MASTERSMITH_PERF_ENV_FILE` が要るため、`MASTERSMITH_PERF_ENV_FILE=/dev/null` を付けて呼ぶ）。

### 追加の確かめ（--lang・--pattern・--busy・--storage・--compact）

`perf/dsl-timing.sh` に次のオプションを付けると、上の測定の後に続けて行う（組み合わせてよい。PostgreSQL の1種類で足りる）。

```bash
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --lang --pattern --storage postgres
# 同時の投入（重い処理の排他）の確かめ
MASTERSMITH_IMAGE_TAG=perf-dsl ./perf/dsl-timing.sh --busy postgres
```

| オプション | すること | 目標 | 結果のファイル |
|---|---|---|---|
| `--lang` | 仮の管理者の表示の言語を API（`PUT /api/me/preferences`）で `en` にしてから（ログインの後は利用者の言語がブラウザーの言語より優先されるため。終わったら元の値に戻す。設定の保存は監査に残らない）、英語のロケール（`en-US`）の Chromium でログインし、`<html lang>` が `en` になったことを確かめて `/admin/dsl` を開き、対象DB に無いテーブルを持つ DSL を貼り付けて投入（照合の警告）、名前の違うスキーマの DSL を投入（照合の警告 `SCHEMA_MISMATCH`）、続けて誤りを含む DSL・書式の版 1 の DSL（`UNSUPPORTED_VERSION`）・メニューの深さが 6 段の DSL（`SEMANTIC`）を貼り付けて投入（どれも 422）。応答の `message` と、画面の警告・誤りの一覧に日本語の文字が無いこと、画面に応答の `message` がそのまま出ることを確かめる（`perf/ui/dsl-ui-lang.mjs`） | U5-LANG-E2E | `ui-lang.json`（`pass`） |
| `--pattern` | 1,000 文字近くの重い正規表現（深い入れ子の繰り返し・`(a+)+` の並び・大きな繰り返しの回数・Unicode の文字の種類の積・遅延の繰り返しの選択）を `pattern` に 2,000 個持つ DSL と、同じ形で `pattern` の無い DSL を作り（`perf/make-pattern-dsl.mjs`）、普通 3 回 → 重い 3 回 → 重いものの直後に待たずに普通 → 普通 3 回の順に投入する | U2-PATTERN-COMPILE（重い投入が短く終わり、直後の普通の投入が遅れない。アプリのログに「正規表現の確かめを時間の上限で打ち切りました」「受け付けられませんでした」が出ない） | `timings.tsv` の `pattern-*`・`pattern-dsl.txt`・`app.log` |
| `--busy` | 組ごとにログインを1回して、10MB の正しい DSL の投入を2つ並べて送る（要求ごとに別の応答のファイル `resp-busy-<組>-a.body`・`-b.body`。状態コード（curl の `-w '%{http_code}'`）と curl の終了コードを両方記録）。判定は、成功（201）1つと 503 `DSL_BUSY` 1つで `pass`。2つとも成功は重ならなかった回（先の処理が終わってから後が届いた）として `not-overlapped` にし、`BUSY_ATTEMPTS` 回（既定 5）までやり直す。5 回とも重ならなければ不合格にせず `not-overlapped-5-times（依頼者に諮る）` と記録する。2つとも 503・`DSL_BUSY` 以外の 5xx・状態コードが取れない（`000`）回は `fail-candidate`（不合格の候補）としてやり直さずに記録する。後に `memory.peak`・OOMKilled・終了の状態・健全性とヒープの使用を記録する | U2 dsl-v2 の NFR2.5・NFR2.6（503 が送り手に届く。止まらない）。`memory.peak` は1件の投入（`--busy` を付けずに流した `memory-peak.txt`）と比べる（後の要求は本文を読まずに断るため、大きく増えない見込み） | `busy.tsv`（組ごとに1行）・`<種類>/busy-state.txt`・`resp-busy-*.body`・`busy-*.result`・`busy-*.stderr`・`heap.tsv` の `after-busy` |
| `--storage` | 10MB の DSL（埋め草の先頭の行に回の番号を入れ、大きさは同じ）の投入→適用を `STORAGE_ROUNDS` 回（既定 21）くり返し、最後にプレビューも1件置く（プレビュー1件と履歴 20 件の最大の状態）。回ごとに H2 のファイル（`/app/data/mastersmith.mv.db`）の大きさ、コンテナのメモリ（cgroup の `memory.current`・`memory.peak`、`memory.stat` の `anon`（プロセスのメモリ）と `file`（ページキャッシュ。回収できる））、履歴の件数を記録する。最後にアプリを `docker compose stop`（`stop_grace_period` 45 秒）で止め、止めるのにかかった秒数と終わり方（exit code。SIGTERM で正常に終われば 143、猶予切れや OOM の SIGKILL は 137）・止めている間の H2 のファイルの大きさ（同じイメージの一時のコンテナでボリュームを読むだけ）・起動から healthy までの秒数・起動し直した後の大きさを記録し、止める前と後のデータ（適用中の DSL とプレビューの DSL の SHA-256、プレビューの previewId、履歴の版・dslHash・件数）を比べる。その後、履歴のすべての版を戻して、プレビューの本文の SHA-256 が履歴の dslHash と一致する数を記録する（`restore_all_match`。詰め直しで本文が壊れていないか） | U4-STORAGE | `storage.tsv`・`memory-peak-before-restart.txt`・`memory-stat-before-restart.txt`・`stop-state.txt`・`data-while-stopped.txt`・`snapshot-before-stop.txt`・`snapshot-after-restart.txt`・`history-after-restart.txt`・`app-before-restart.log` |
| `--compact` | `--storage` と組み合わせる。最後のプレビューを置いた後、止める前に、アプリを止めずに内部DB を詰め直す道具（`docker/hikari-pool.sh compact --container mastersmith-perf-app-1`）を流す。道具の出力（接続の本数・前と後の大きさ・かかった時間）と終わりの値・道具全体の秒数を記録し、`storage.tsv` に `after-compact` の行を足し、詰め直しの前後のデータ（適用中の DSL とプレビューの DSL の SHA-256、previewId、履歴）を比べ、詰め直しの後の健全性を記録する。その後の `--storage` の止める・起動し直す・`restore_all_match` はそのまま続く（詰め直しの後の本文も確かめる） | FR1.4・NFR1（Intent 260925-storage-memory-fixes） | `compact.txt`・`snapshot-before-compact.txt`・`snapshot-after-compact.txt` |

- `--lang` の画面は、開いたときにプレビューの表示（重い処理）を読みに行く。読み終わる前に投入すると 503 `DSL_BUSY` になるため、台本は最初の読み込みが終わるのを待ってから投入する。
- `--storage` の `memory.current` はページキャッシュを含むため、H2 のファイルへの書き込みでコンテナの上限の近くまで上がることがある。止まるかどうかは `anon` と、最後の `state.txt`（OOMKilled）で見る。
- `--storage` はアプリを起動し直すため、GC の記録（`gc.log`）は起動し直す前の分（`gc-before-restart.log`）と後の分をつないで残す。
- `--storage` で内部DB の接続先を比べるときは、`PERF_DB_URL` で `MASTERSMITH_DB_URL` を上書きする（例: DEFRAG_ALWAYS なしの `PERF_DB_URL=jdbc:h2:file:/app/data/mastersmith`）。指定しなければ application.yaml の既定（DEFRAG_ALWAYS=TRUE）で動く。使った値は `env.txt` の `db_url` に残る。`PERF_DB_URL` が受け付ける形は、前の部分が `jdbc:h2:file:/app/data/mastersmith` ちょうどで、後ろに `;` で `DEFRAG_ALWAYS=TRUE` と `RETENTION_TIME=<1〜9 桁の整数>` だけを続けたもの（名前は大文字、同じ設定は1回まで、空白・`\`・`'`・`"` と空の要素は不可。`perf/lib/perf-db-url.sh`）。外れたら、台本は一時ディレクトリと使い捨ての環境を作る前に終了コード 1 で止まり、外れた決まりの名前だけを出す（値は表示しない）。確かめの関数の試しは `bash perf/lib/perf-db-url-test.sh`。
- `--storage` のアプリのログは、止める前の分を `app-before-restart.log`、起動し直した後の分を `app.log` に残す。止めるときの終わり方は `app-before-restart.log` の終わりの行（Hikari の shutdown など）と `stop-state.txt` の exit code で見る。

### k6 の DSL の場面（Performance Validation で使う）

`perf/k6/scenarios.js` に DSL の場面を足した。Build and Test では測定として流していない。

Intent 261004-role-menu の Performance Validation（Q3: A）で、`dslLight`・`dslCycle` を**1回の繰り返しに要求1つ**にし、繰り返しの始めに op のタグを付けて **op ごとの `iteration_duration`**（単調な時計）と `checks` の率 1 で判定する形に直した。colima の VM の時計が負荷の間にずれて合わせ直されると `http_req_duration` が崩れるため（`aidlc/spaces/default/memory/project.md` の学び）、`http_req_duration{name:…}` は要約に並べるだけ（閾値 `max>=0`）にした。管理者のトークンは setup で1回取るため、場面の長さはトークンの有効期限（5 分）より短くする（`DURATION=3m`）。目標の出典は Intent 261004-role-menu の U2 dsl-v2 の NFR2.6（`construction/dsl-v2/nfr-requirements/tech-stack-decisions.md`。前の Intent の U4 の NFR1.10・NFR1.12 を保つ）。

| 場面 | すること（1回の繰り返しに要求1つ） | 判定（閾値は緩めない） |
|---|---|---|
| `dslPrepare` | 1 VU・1 回。`DSL_FILE` の投入と適用を、履歴が `DSL_HISTORY_MIN`（既定 20。履歴の上限）件になるまで繰り返し、最後に同じ DSL を投入してプレビューに置く（`dslLight` の条件を作る準備） | `checks{scenario:dslPrepare}` の率 1 |
| `dslLight` | 今の状態（op `status`）と履歴（op `history`）を、繰り返しごとに交互に同時 `VUS` で送る。setup で条件（適用中の DSL・プレビュー・履歴 `DSL_HISTORY_MIN` 件）を確かめ、そろっていなければ始める前に止まる | `iteration_duration{scenario:dslLight,op:status}`・`{…,op:history}` の p95 < 1000 ms と `checks` の率 1 |
| `dslCycle` | 投入（op `submit`）→ 適用（op `apply`）→ 投入 → 破棄（op `discard`）→ 履歴（op `history`）を繰り返しの番号で1つずつ送る。適用の `previewId` は直前の投入の応答から受ける。投入は重い処理（同時に1つ）のため `VUS=1` で使う | op `apply`・`discard`・`history` の `iteration_duration` の p95 < 1000 ms と `checks` の率 1。投入（op `submit`）は記録だけ（NFR2.5 の 10 秒は `perf/dsl-timing.sh` で測る） |
| `dslMixed` | 10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 `VUS` 名のログインを重ねる | 失敗しない（閾値は場面ごとの `checks` の率 1）。コンテナの上限は配備の既定 2g で流し、止まらないこと（OOMKilled）と Hikari の値を別に記録する |

```bash
# 1種類だけ残して起動し（生成した DSL と 10MB の DSL が結果の置き場にできる）、同じ一時ディレクトリの ui.env（管理者のメールアドレスと
# パスワード）で k6 を流す。<タグ> は使い捨て用のイメージのタグ（配備した版をそのまま使うときは local）
KEEP=1 MASTERSMITH_IMAGE_TAG=<タグ> OUT_DIR="$PWD/build/perf-results/dsl-k6" ./perf/dsl-timing.sh postgres
D=<表示された一時ディレクトリ>
k6d() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/ui.env" "$@" \
    -v "$PWD/build/perf-results/dsl-k6/postgres:/data:ro" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$name.json /scripts/scenarios.js
}
# 流す順: dslCycle（適用と破棄）→ dslPrepare（dslLight の条件をそろえる）→ dslLight → dslMixed。台本の全体を caffeinate -dims で包む
k6d dslCycle -e SCENARIO=dslCycle -e VUS=1 -e DURATION=3m -e DSL_FILE=/data/generated.yaml
k6d dslPrepare -e SCENARIO=dslPrepare -e DSL_FILE=/data/generated.yaml
k6d dslLight -e SCENARIO=dslLight -e VUS=10 -e DURATION=3m
```

- `dslLight` は `DSL_FILE` が要らない。条件（履歴 20 件と想定の規模のプレビュー・適用中の DSL）は直前の `dslPrepare` が作る。
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
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfc up -d --wait

# 3'. 場面ごとに流す（caffeinate -dims で PC のスリープを防ぐ）。k6 は同じネットワークの中から app と mailpit に届く
mkdir -p build/perf-results && chmod 777 build/perf-results
for s in preferencesGet preferencesSave preferencesInvalid passwordChange passwordMismatch passwordInvalid \
  invite invitationResend invitationList invitationCancel registrationVerify registrationComplete registrationInvalid registrationRejected; do
  caffeinate -dims docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=$s -e DURATION=60s \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
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
| `userAdminPoolLimit` | 印を付ける → 外す → 止める → 解くの4つをくり返す（どれも 204 で状態が戻る。準備のログインの失敗と失敗回数を戻す操作は入れない）。`constant-vus`（`VUS`・`DURATION`） | 閾値なし（接続の時間切れを起こすための場面。Intent 261003-user-admin-followup の FR4。状態コードの件数を `userAdminPoolLimit_204`・`_409`・`_500`・`_other` で出す） | 対象 `perf-uat<VU>`（`userAdminOps` と同じ）。手順は下の「接続プールの上限を下げて流す」 |

- **受け入れの条件（NFR5.4）**: 操作する管理者（`perf-uaop<VU>`）はどの操作の対象にもしない。初期管理者（`perf-admin@example.test`）は操作する人にも対象にもしない。対象は VU ごとに分け、2つの VU が同じ利用者を対象にしない。台本は VU の番号でこれを守る（`VUS` は `PERF_UA_COUNT`（既定 10）以下。超えると始める前に止まる）。
- **準備のログインの失敗**（`{name:userAdminPrepLogin}`、401）は、失敗回数を戻す組で失敗回数を 1 以上にするためのもので、`checks` にも p95 の判定にも数えない。監査に `LOGIN_FAILED` が1件ずつ残る（5つの操作の件数には数えない）。
- **対象の利用者 ID**: setup が `perf-uaop01` で一覧の API を検索して引く（ID だけを持ち、値は出力しない）。足りないと始める前に止まる。
- **止める悪い側のくり返し**: 止めるとトークンがすべて無効になるため、回ごとに別の対象を使う。流し直すときは、環境を作り直して（手順 5'' と 2''）入れ直す。
- **接続プール（NFR6.2・NFR6.3）**: 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics` の `hikaricp.connections.timeout`（待ちの時間切れの累計）と `hikaricp.connections.acquire`（借りるまでの待ちの最大）で判断する。数秒ごとの使用中の数では判断しない。NFR6.2 は上限 30（既定）で `userAdminPool` を流し、時間切れの累計 0・500 が 0 件・流した5つの操作の成功の件数と監査の `SUCCESS` の件数の一致を見る。`hikaricp.connections.acquire` の値は、応答の `baseUnit` を見て読む（外部エクスポートを無効にしたときは `seconds`、有効にしたときは `milliseconds` になる。同じ数が単位で 1,000 倍違って見えるため、ミリ秒にそろえてから比べる）。NFR6.3 は使い捨てのアプリの上限を `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` にして `userAdminOps` を (A) `VUS=5`（見積もり 10 本で上限ちょうど。時間切れの累計 0・件数が一致）と (B) `VUS=10`（見積もり 20 本で上限の2倍。2本目の待ちが出る）で流す。(B) は待ちを起こす場面のため、欠けた監査と ERROR の件数を記録し、p95 と件数の一致に数えない。(A)・(B) の後は上限を既定に戻して起動し直すか、環境を作り直す。配備したアプリの公開の範囲と上限は変えない。
- 長い試験は `caffeinate -dims` で台本の全体を包む（場面ごとに起こし直さない）。遅れが出たら `pmset -g log` でスリープを確かめる。
- k6 はアプリと同じ VM の CPU を分け合うため、測った値にその分が混ざりうる。結果に明記する。
- 目標に届かないときは目標を緩めず、原因をログと状態で確かめて依頼者に相談する。一覧が NFR5.1 に届かなければ、`users (created_at, user_id)` の索引を足す直しを諮る。

```bash
# 0・1. 上の「手順」の 0・1 と同じ（配備したアプリを止め、WAR とイメージを用意し、一時の環境ファイルを作る）。
#       U3 はメールを送らないため Mailpit（profile mail）は起動しない
# 1''. 接続プールを見る場面（userAdminPool・上限 10 の場面）では、使い捨てのアプリにだけ次の行を足す
( umask 077; printf 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics\n' >> "$D/app.env" )

# 2''. 使い捨ての環境を起動し、止めて、試験用の利用者とトークンの行を入れる（メールアドレスは予約のドメインだけ）。
#      perf-ua-0001〜1000（一覧）、perf-uaop01〜20（操作する管理者）、perf-uat01〜20（5つの操作の対象）、
#      perf-uapf01〜20（氏名と言語の対象）、perf-uasw-01-01〜10-10（止める悪い側の対象。1人に未無効 100 件・無効 1,000 件）
#      uaop・uat・uapf を 20 名ずつ入れるのは、下の「接続プールの上限を下げて流す」が VUS=12（流し直しは VUS=20）で流すため。
#      台本は VUS が PERF_UA_COUNT（既定 10）を超えると始める前に止まるため、その場面では k6 に -e PERF_UA_COUNT=20 を渡す。
#      ほかの場面（VUS 10 以下）は既定の PERF_UA_COUNT=10 のままでよい（01〜10 だけを使う）
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
    'perf-' || K || LPAD(CAST(X AS VARCHAR), 2, '0') FROM SYSTEM_RANGE(1, 20), (VALUES 'uaop', 'uat', 'uapf') V(K);
INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-uasw-' || LPAD(CAST(A.X AS VARCHAR), 2, '0') || '-' || LPAD(CAST(B.X AS VARCHAR), 2, '0') || '@example.test',
    '$HASH', FALSE, CURRENT_TIMESTAMP, 'perf-uasw' FROM SYSTEM_RANGE(1, 10) A, SYSTEM_RANGE(1, 10) B;
INSERT INTO refresh_tokens (user_id, token_hash, issued_at, expires_at, revoked_at)
  SELECT u.user_id, HASH('SHA-256', STRINGTOUTF8(u.email || '-' || r.X)), CURRENT_TIMESTAMP, DATEADD('DAY', 30, CURRENT_TIMESTAMP),
    CASE WHEN r.X <= 100 THEN NULL ELSE CURRENT_TIMESTAMP END FROM users u, SYSTEM_RANGE(1, 1100) r WHERE u.email LIKE 'perf-uasw-%'"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfu up -d --wait

# 3''. 場面ごとに流す。台本の全体を caffeinate -dims で包む（場面ごとに起こし直さない）
mkdir -p build/perf-results && chmod 777 build/perf-results
cat > "$D/ua-run.sh" <<'EOS'
D="$1"
k6run() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" "$@" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$name.json /scripts/scenarios.js
}
metrics() {   # 接続プールの値（待ちの時間切れの累計・借りるまでの待ちの最大）を記録する。acquire は baseUnit を見て読む
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
caffeinate -dims zsh "$D/ua-run.sh" "$D"

# 3'''. 上限 10 の場面（NFR6.3）。使い捨てのアプリの上限を 10 にして起動し直し、(A) VUS=5 と (B) VUS=10 で 5つの操作を流す
( umask 077; printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10\n' >> "$D/app.env" )
perfu up -d --wait --force-recreate app
cat > "$D/ua-pool10.sh" <<'EOS'
D="$1"
for v in 5 10; do
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=userAdminOps -e VUS=$v \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/userAdminOps-pool10-vus$v.json /scripts/scenarios.js
  for m in hikaricp.connections.timeout hikaricp.connections.acquire; do   # acquire は baseUnit を見て読む
    curl -s "http://127.0.0.1:18080/actuator/metrics/$m" > "build/perf-results/pool10-vus$v-$m.json"
  done
done
EOS
caffeinate -dims zsh "$D/ua-pool10.sh" "$D"
docker logs mastersmith-perf-app-1 2>&1 | grep -c '監査イベントの記録に失敗しました'   # (B) の欠けた監査の件数と突き合わせる

# 3''''. 接続プールの上限を下げて流す（下の節「接続プールの上限を下げて流す」。上限 4・userAdminPoolLimit・VUS=12・5 分）

# 4''. 秘密が出ていないことを件数で確かめる（下の既知の例外を除き、どれも 0 であること）。ログはファイルに残さず、数だけを見る
#      （初期管理者の起動のログには伏せ字のメールアドレスが出るため、試験用の利用者の名前の部分で数える）
#      監査の記録に失敗した場面（接続の待ちの時間切れなど）では、`監査イベントの記録に失敗しました` の ERROR のキー enteredEmail に
#      メールアドレスそのものが載る（手で記録を補うための既知の例外。aidlc/spaces/default/memory/project.md の決定）。そのため
#      perf-ua の件数は「その ERROR で enteredEmail に perf-ua を持つ行の数」と一致し、それ以外の行では 0 であることを見る
docker logs mastersmith-perf-app-1 2>&1 | grep -c 'perf-ua'
docker logs mastersmith-perf-app-1 2>/dev/null | python3 -c '
import json, sys
known = other = 0
for line in sys.stdin:
    if "perf-ua" not in line:
        continue
    try:
        r = json.loads(line)
    except ValueError:
        other += 1
        continue
    hits = [k for k, v in r.items() if isinstance(v, str) and "perf-ua" in v]
    if r.get("message") == "監査イベントの記録に失敗しました" and hits == ["enteredEmail"]:
        known += 1
    else:
        other += 1
print("既知の例外 enteredEmail", known, "それ以外（0 であること）", other)'
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

### 接続プールの上限を下げて流す（Intent 261003-user-admin-followup の FR4）

上の上限 10 の場面（`userAdminOps`）は準備のログインを含み、同時の操作の数が上限に届きにくかった（前の Intent の (A)）。この場面は、使い捨てのアプリの上限を **4** に下げ、準備のログインを含まない `userAdminPoolLimit` を **`VUS=12`・`DURATION=5m`** で流して、接続の待ちの時間切れと、手元の監視の警報3件（`ms-pool-pending`・`ms-audit-fail`・`ms-error-logs`）が鳴ることを確かめる。合否の基準は Intent の code-generation-plan.md の 7.1 節（Build and Test が判定する）。

- **なぜ届くか**: 操作1件は1本目の接続を持ったまま、確定の後の監査の記録で2本目を借りる。同時の操作が上限（4）以上になると全員が2本目を待ち、`connection-timeout`（5 秒）で時間切れになる。同時 12 は上限の3倍。5 分は、`ms-pool-pending`（1 分ごとの値で `for: 1m`）が指標の送信の時点を2回以上またぐため。
- **警報の決まりは変えない**: 手元の監視（`grafana/otel-lgtm`）に、リポジトリの `docker/monitoring/provisioning/alerting/mastersmith.yaml` をそのまま読み込ませる（写しを作ってしきい値を変えることはしない）。外部エクスポートは使い捨てのアプリにだけ有効にする。
- **VM のメモリ**: アプリ（上限 2g）・手元の監視（1536m）・k6 を同じ VM で動かす。始める前に `docker info` と `docker stats --no-stream` で余裕を読み取りで確かめ、足りなければ配備したアプリを止める（止める前に依頼者に伝え、`docker compose stop app` で止めて、終わったら `docker compose start app` で戻す）。
- **警報の状態**は、試験の始めから終わりの後 5 分までを 30 秒ごとに Grafana の `api/prometheus/grafana/api/v1/rules` で読み、題ごとの `state` を記録する。この API の題の `state` は `inactive`・`pending`・`firing` の3つで、`Alerting` という値は返らない（`Alerting`・`Normal` は題の下の警報の実体 `alerts[].state` の名前）。**題の `state` が `firing` になったことを「`Alerting` になった」と読む**。台本は、題の `state` に加えて実体の `state` と `activeAt` も記録する。
- **待ちの最大（(a) の判定）**: `hikaricp.connections.acquire` の `MAX` は直近の時間の窓（数分）の最大で、時間が経つと下がる（Intent 261003-user-admin-followup の T1 では、k6 の終わりに 5,049.8 ms、終わりの後 5 分に 0.22 ms）。終わりの後の値だけでは判定できないため、台本は試験の間 30 秒ごとに `timeout`・`acquire`・`pending` を記録し（`limit-metrics-timeline.txt`）、k6 の終わりの直後の値（`limit-end-*.json`）も残す。(a) は、`timeout` の `COUNT`（累計。下がらない）の k6 の前からの増分が 1 以上、または 30 秒ごとの記録と k6 の終わりの値の中の `acquire` の `MAX` の最大が 1,000 ms 以上（`baseUnit` を見てミリ秒にそろえる。外部エクスポートを有効にしたこの場面では `milliseconds`）で判定する。
- **利用者の数**: `VUS=12` は台本の既定の `PERF_UA_COUNT`（10）を超えるため、手順 2'' で `perf-uaop`・`perf-uat` を 20 名ずつ入れ、k6 に `-e PERF_UA_COUNT=20` を渡す（流し直しの `VUS=20` も同じ）。渡さないと setup で止まる。
- **BUSY の数え方**: 409 `USER_ADMIN_BUSY` が出たときは、L3（`要求をエラー応答に変換しました` で `code` が `USER_ADMIN_BUSY` の行）の `traceId` ごとに、同じ `traceId` の L4（`行の排他を取れませんでした` の WARN）がちょうど1件ずつあり、件数が一致することを見る（Loki の L3・L4 の問い合わせでも、アプリの標準出力の JSON でもよい。下は標準出力で数える例で、値は出さず件数だけを出す）。BUSY が 0 件のときは判定できない（`Unverified`）。
- **鳴らなかったとき**: 決まりは変えない。まず条件（待ちの数・時間切れ・ERROR の件数・監査の失敗の行）が起きたかを指標とログで確かめる。起きたのに鳴らなければ警報の側の不具合として記録する。起きなかったときだけ、上限 2・`VUS=20`・`DURATION=8m` で1回流し直す（台本の引数を変える）。
- 配備したアプリのイメージ（タグ `local`）は上書きしない（手順 0 のとおり、Intent ごとのタグで作り `MASTERSMITH_IMAGE_TAG` で渡す）。
- 片付けの前に、結果（指標・警報の状態・L3/L4 の件数・監査の件数）を確かめて記録してから消す。

```bash
# 前提: 上の手順 0・1・1''・2'' を済ませ（2'' は uaop・uat を 20 名ずつ）、使い捨ての環境（mastersmith-perf）が動いていること
#       （ネットワーク mastersmith-perf_default ができている。シェルに MASTERSMITH_PERF_ENV_FILE と MASTERSMITH_IMAGE_TAG が渡っている）。
# a. 使い捨てのアプリにだけ、上限 4 と外部エクスポート（送り先は下の手元の監視）を足す（秘密ではない値）
( umask 077; printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4\nMASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true\nMASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318\n' >> "$D/app.env" )

# b. 手元の監視を、使い捨ての環境のネットワークに名前 lgtm で起動する（警報の決まりはリポジトリのファイルをそのまま読み込む。
#    画面は 127.0.0.1:13000 だけに結び付け、配備した環境の手元の監視（3000）と重ねない。ボリュームは置かない）
docker run -d --name mastersmith-perf-lgtm --network mastersmith-perf_default --network-alias lgtm \
  -p 127.0.0.1:13000:3000 --memory 1536m \
  -e GF_AUTH_ANONYMOUS_ENABLED=true -e GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer -e GF_AUTH_DISABLE_LOGIN_FORM=true \
  -v "$PWD/docker/monitoring/provisioning/alerting/mastersmith.yaml:/otel-lgtm/grafana/conf/provisioning/alerting/mastersmith.yaml:ro" \
  grafana/otel-lgtm:0.37.0@sha256:57b5ab1ca6619beb180174e2fccce04762470750669d043f544b4726d0582d07
until curl -sf http://127.0.0.1:13000/api/health > /dev/null; do sleep 5; done
perfu up -d --wait --force-recreate app   # 上限 4 と外部エクスポートで起動し直す
curl -s http://127.0.0.1:18080/actuator/metrics/hikaricp.connections.max   # VALUE が 4 であること
# 警報の決まりが読み込まれ（題がすべて inactive）、指標が Prometheus に届いていること（値が返るまで待つ）
until curl -s -G http://127.0.0.1:13000/api/datasources/proxy/uid/prometheus/api/v1/query \
  --data-urlencode 'query=max(hikaricp_connections_pending{service_name="mastersmith"})' | grep -q '"value"'; do sleep 5; done

# c. 台本の全体を caffeinate -dims で包み、k6（5 分）を流しながら、警報・接続プールの値・メモリを 30 秒ごとに読む（終わった後も 5 分読む）。
#    引数は 結果の名前・VUS・DURATION（流し直しは limit2 20 8m のように渡す）。時刻は UTC で書く
cat > "$D/ua-limit.sh" <<'EOS'
D="$1"; TAG="$2"; VUSN="$3"; DUR="$4"; R=build/perf-results
for m in hikaricp.connections.timeout hikaricp.connections.acquire; do
  curl -s "http://127.0.0.1:18080/actuator/metrics/$m" > "$R/$TAG-before-$m.json"
done
echo "k6 start $(date -u +%FT%TZ)" > "$R/$TAG-times.txt"
docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" \
  -e SCENARIO=userAdminPoolLimit -e VUS=$VUSN -e DURATION=$DUR -e PERF_UA_COUNT=20 \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
  run --quiet --summary-export=/out/$TAG.json /scripts/scenarios.js > "$R/$TAG-k6.log" 2>&1 &
K6=$!
watch() {   # 3件の警報（題の state と実体の state・activeAt）、接続プールの値、コンテナのメモリを1回ずつ書く
  curl -s http://127.0.0.1:13000/api/prometheus/grafana/api/v1/rules | python3 -c '
import json, sys, time
names = {"コネクションプールの待ち", "監査の書き込みの失敗", "ERROR のログの増加"}
now = time.strftime("%H:%M:%SZ", time.gmtime())
for group in json.load(sys.stdin)["data"]["groups"]:
    for rule in group["rules"]:
        if rule["name"] in names:
            print(now, rule["state"], rule["name"], [(a.get("state"), a.get("activeAt")) for a in rule.get("alerts", [])])' >> "$R/$TAG-alerts.txt"
  T=$(date -u +%H:%M:%SZ)
  for m in hikaricp.connections.timeout hikaricp.connections.acquire hikaricp.connections.pending; do
    curl -s "http://127.0.0.1:18080/actuator/metrics/$m" | python3 -c 'import json, sys
d = json.load(sys.stdin); print(sys.argv[1], d["name"], d.get("baseUnit", "-"), {x["statistic"]: x["value"] for x in d["measurements"]})' "$T" >> "$R/$TAG-metrics-timeline.txt"
  done
  docker stats --no-stream --format "$T {{.Name}} {{.MemUsage}}" >> "$R/$TAG-mem.txt"
}
while kill -0 $K6 2> /dev/null; do watch; sleep 30; done
wait $K6; echo "k6 exit $? $(date -u +%FT%TZ)" >> "$R/$TAG-times.txt"
for m in hikaricp.connections.timeout hikaricp.connections.acquire; do   # k6 の終わりの直後の値（acquire の MAX はまだ下がっていない）
  curl -s "http://127.0.0.1:18080/actuator/metrics/$m" > "$R/$TAG-end-$m.json"
done
for i in $(seq 1 10); do watch; sleep 30; done   # 終わった後の 5 分
for m in hikaricp.connections.timeout hikaricp.connections.acquire; do   # 参考（acquire の MAX は下がっている）
  curl -s "http://127.0.0.1:18080/actuator/metrics/$m" > "$R/$TAG-after-$m.json"
done
echo "watch end $(date -u +%FT%TZ)" >> "$R/$TAG-times.txt"
EOS
mkdir -p build/perf-results && chmod 777 build/perf-results
caffeinate -dims zsh "$D/ua-limit.sh" "$D" limit 12 5m
# (b): 題ごとに firing になった回数（3件とも 1 以上であること）。Alerting では数えない（題の state に出ないため）
awk '$2 == "firing"' build/perf-results/limit-alerts.txt | sed 's/ \[.*//' | cut -d' ' -f3- | sort | uniq -c
# (a): 時間切れの累計の増分と、acquire の MAX の最大（ミリ秒。baseUnit が seconds のときは 1,000 倍する）
grep ' hikaricp.connections.timeout ' build/perf-results/limit-metrics-timeline.txt | tail -1
python3 -c '
import ast, json
mx = 0.0
for line in open("build/perf-results/limit-metrics-timeline.txt"):
    t, name, unit, rest = line.split(" ", 3)
    if name == "hikaricp.connections.acquire":
        mx = max(mx, ast.literal_eval(rest)["MAX"] * (1000 if unit == "seconds" else 1))
d = json.load(open("build/perf-results/limit-end-hikaricp.connections.acquire.json"))
end = {x["statistic"]: x["value"] for x in d["measurements"]}["MAX"] * (1000 if d.get("baseUnit") == "seconds" else 1)
print("acquire MAX の最大（ms）", max(mx, end))'

# d. BUSY の L3 と L4 を traceId で突き合わせる（件数だけを出す。L3 のすべてが L4 をちょうど1件持つこと）
docker logs mastersmith-perf-app-1 2>/dev/null | python3 -c '
import collections, json, sys
l3, l4 = [], collections.Counter()
for line in sys.stdin:
    try:
        r = json.loads(line)
    except ValueError:
        continue
    if r.get("message") == "要求をエラー応答に変換しました" and r.get("code") == "USER_ADMIN_BUSY":
        l3.append(r.get("traceId"))
    elif r.get("message") == "行の排他を取れませんでした":
        l4[r.get("traceId")] += 1
print("L3", len(l3), "L4", sum(l4.values()), "L4 をちょうど1件持つ L3", sum(1 for t in l3 if l4.get(t) == 1),
      "L3 の無い L4", sum(1 for t in l4 if t not in set(l3)))'

# e. 結果を確かめて記録してから、手元の監視を消し、上の手順 4''・5'' で片付ける（app.env の3行は環境ごと消える）
#    5'' の最後の docker compose up -d --wait は配備したアプリを起動し直すもので、試験のタグのイメージは使わない
docker rm -f mastersmith-perf-lgtm
```

## グループの管理の場面（Intent 261004-role-menu の U3）

`perf/k6/scenarios.js` のグループの管理の場面を、使い捨ての環境で流す。目標の出典は `aidlc/spaces/default/intents/261004-role-menu/construction/group/` の `nfr-requirements/performance-requirements.md`（NFR2.5）・`scalability-requirements.md`（NFR2.7・NFR2.8）と、`nfr-design/performance-design.md`（2節）・`scalability-design.md`（2節）。どの場面も1回の繰り返しに要求1つで、繰り返しの中に待ちを置かない。判定は場面ごとの `iteration_duration{scenario:…}` の p95 < 1000 ms と `checks{scenario:…}` の率 1（期待の状態コードだけを合格にする）で、閾値は緩めない。`http_req_duration{name:…}` は colima の VM の時計のずれで崩れうるため、要約に並べて記録するだけにする（台本の閾値 `max>=0` は表示のためで、判定に使わない）。Build and Test では台本と手順を用意し、`k6 inspect`（`--include-system-env-vars` を付ける）で場面の名前・閾値・`setupTimeout` を確かめるだけにする。流して判定するのは Performance Validation。

| 場面 | すること | 用意（`setup()` が API で作る） | 形 |
|---|---|---|---|
| `groupListFirst` | 一覧の1ページ目（200、20 件） | グループを `GROUP_LIST_MIN`（既定 1,000）件以上にする | `constant-vus`（`VUS`・`DURATION`。`DURATION=3m` で流す） |
| `groupListLast` | 一覧の最後のページ（200） | 同上（最後のページの番号は setup が数える） | 同上 |
| `groupDetail` | メンバー 1,000 人のグループの詳細（200、メンバー 1,000 人） | グループを1つ作り、候補 `perf-gr-0001`〜`1000` を全員足す | 同上 |
| `groupRename` | VU ごとに自分のグループの名前を2つの名前（`perf-rename-<実行>-<VU>-a`・`-b`）で交互に変える（204） | VU ごとにグループを1つ | 同上 |
| `groupCreate` | VU ごとに名前 `perf-create-<実行>-<VU>-<回>` で作る（201） | なし | `per-vu-iterations`（`VUS` × `GROUP_ROUNDS`（既定 100）回、`maxDuration` 3 分） |
| `groupDelete` | VU ごとに空のグループを1件ずつ消す（204） | VU ごとに空のグループ `GROUP_ROUNDS` 件 | 同上 |
| `groupMemberAdd` | VU ごとに自分のグループへ候補を1人ずつ足す（204） | VU ごとにグループ1つ。候補は VU ごとに `GROUP_ROUNDS` 人ずつ割り当てる（VU 1 は 0001〜0100 …） | 同上 |
| `groupMemberRemove` | VU ごとにメンバーを1人ずつ外す（204） | VU ごとにグループ1つとメンバー `GROUP_ROUNDS` 人 | 同上 |
| `groupPoolLimit` | VU ごとに自分のグループで、自分の候補1人の追加と外しを交互にくり返す | VU ごとにグループ1つと候補1人 | `constant-vus`。閾値なし。状態コードの件数を `groupPoolLimit_204`・`_409`・`_500`・`_other` で出す |

- **名前**: グループの名前の鍵は大文字と小文字を区別せず全体で一意のため、どの名前も実行ごとの識別（setup の時刻から作る `runId`）と VU の番号を含める。同じ使い捨ての環境で流し直しても重ならない。
- **トークン**: 操作する管理者は試験用の管理者 `perf-graop01`（初期管理者は使わない）。setup は用意に時間がかかるため4分ごとにログインし直し、最後に場面で使うトークンを取り直す。場面は 3 分までにする（アクセストークンは 5 分で切れ、場面の途中でログインし直さない）。setup の時間の上限は 10 分（`setupTimeout`）。
- **候補の利用者**: `perf-gr-0001`〜`perf-gr-1000`（予約のドメイン `example.test`、管理者の印なし）を SQL で入れる（利用者を作る API は招待とメールの流れのため使わない）。台本は利用者の一覧の検索で ID を引き、足りなければ始める前に止まる。
- **接続プール（NFR2.7、`scalability-design.md` 2.2）**: `groupPoolLimit` を2回流す。**合否の回**は使い捨てのアプリの上限を `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`（健全性の確かめの1本を見込む）にし、`VUS=5`・`DURATION=2m`。合格は、待ちの時間切れの累計（`hikaricp.connections.timeout`）の増分 0、`groupPoolLimit_500` が 0 件、借りるまでの待ちの最大（`hikaricp.connections.acquire` の `MAX`）が 20 ms 未満。5〜20 ms のときは合格のまま値を記録し、承認の場で目安を見直す。不合格に読む前に、健全性の確かめ以外の接続の使い手が無かったことを、試験の前後の `hikaricp.connections.active` が 0 であることとアプリのログで切り分ける。**記録の回**（合否に使わない）は上限 `10`・`VUS=20`・`DURATION=2m` で、上限に届いた証拠（`acquire` の最大 10 ms 以上）を見る。届かなければ `VUS=40` で1回だけ流し直し、それでも届かなければ記録して承認の場で諮る。時間切れの数・500 の数・409 の数は書き込みの2本使いの既知の制約の大きさとして記録する。`pending` は瞬間値のため参考にとどめる。
- **`acquire` の単位**: `/actuator/metrics/hikaricp.connections.acquire` の応答の `baseUnit` を見て、ミリ秒にそろえてから 20 ms・10 ms と比べる（外部エクスポートを無効にしたときは `seconds`、有効にしたときは `milliseconds`。同じ数が 1,000 倍違って見える）。下の手順の読み取りの関数は、`baseUnit` が `seconds` なら 1,000 倍する。
- 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡す（配備したアプリの公開の範囲は変えない）。
- 台本の全体を `caffeinate -dims` で包む（場面ごとに起こし直さない）。遅れが出たら `pmset -g log` でスリープを確かめる。手順 0 のとおり配備したアプリを止める（VM の CPU を分け合わないため）。
- グループの操作は監査に残る（使い捨ての環境の監査で、配備した環境の監査には残らない）。片付けの前に件数を数える。

```bash
# 0・1. 上の「手順」の 0・1 と同じ（配備したアプリを止め、WAR とイメージを用意し、一時の環境ファイルを作る）。
#       U3 はメールを送らないため Mailpit（profile mail）は起動しない
# 1G. 使い捨てのアプリにだけ指標の公開を足す（秘密ではない値）
( umask 077; printf 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics\n' >> "$D/app.env" )

# 2G. 使い捨ての環境を起動し、止めて、メンバーの候補 perf-gr-0001〜1000 と操作する管理者 perf-graop01 を入れる
#     （利用者の管理の節の 2'' と同じ SQL の形。メールアドレスは予約のドメインだけ。氏名は必須のためメールアドレスの名前の部分）
perfu() { docker compose -p mastersmith-perf -f docker/perf/compose.yaml "$@"; }
docker info --format '{{.NCPU}} CPU / {{.MemTotal}} bytes'   # VM の余裕を読み取りで確かめる（アプリ 2g・k6）
perfu up -d --wait
perfu stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-gr-' || LPAD(CAST(X AS VARCHAR), 4, '0') || '@example.test', '$HASH', FALSE,
    DATEADD('SECOND', X, CURRENT_TIMESTAMP), 'perf-gr-' || LPAD(CAST(X AS VARCHAR), 4, '0') FROM SYSTEM_RANGE(1, 1000);
INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  VALUES ('perf-graop01@example.test', '$HASH', TRUE, CURRENT_TIMESTAMP, 'perf-graop01')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfu up -d --wait

# 3G. 応答時間の8場面を流す（constant-vus の場面は 3 分、回数の場面は VU ごとに 100 回）。台本の全体を caffeinate -dims で包む
mkdir -p build/perf-results && chmod 777 build/perf-results
cat > "$D/gr-run.sh" <<'EOS'
D="$1"
k6run() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" "$@" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$name.json /scripts/scenarios.js
}
for s in groupListFirst groupListLast groupDetail groupRename; do k6run $s -e SCENARIO=$s -e DURATION=3m; done
for s in groupCreate groupDelete groupMemberAdd groupMemberRemove; do k6run $s -e SCENARIO=$s; done
EOS
caffeinate -dims zsh "$D/gr-run.sh" "$D"

# 3G'. 接続プールの合否の回（上限 11・VUS=5・2 分）と記録の回（上限 10・VUS=20・2 分。届かなければ VUS=40 で1回だけ流し直す）。
#      上限を変えて使い捨てのアプリを作り直し、k6 の前・間（5 秒ごと）・後に接続プールの値を読む
cat > "$D/gr-pool.sh" <<'EOS'
D="$1"; TAG="$2"; VUSN="$3"; R=build/perf-results
read_metrics() {   # 引数: 時刻の印。acquire は baseUnit を見てミリ秒にそろえる
  for m in hikaricp.connections.timeout hikaricp.connections.acquire hikaricp.connections.pending hikaricp.connections.active; do
    curl -s "http://127.0.0.1:18080/actuator/metrics/$m" | python3 -c 'import json, sys
d = json.load(sys.stdin); scale = 1000 if d.get("baseUnit") == "seconds" else 1
print(sys.argv[1], d["name"], {x["statistic"]: (x["value"] * scale if d["name"].endswith("acquire") and x["statistic"] in ("MAX", "TOTAL_TIME") else x["value"]) for x in d["measurements"]})' "$1" >> "$R/$TAG-metrics.txt"
  done
}
read_metrics before
docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=groupPoolLimit -e VUS=$VUSN -e DURATION=2m \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
  run --quiet --summary-export=/out/$TAG.json /scripts/scenarios.js > "$R/$TAG-k6.log" 2>&1 &
K6=$!
while kill -0 $K6 2> /dev/null; do read_metrics "$(date -u +%H:%M:%SZ)"; sleep 5; done
wait $K6; read_metrics end
EOS
( umask 077; printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11\n' >> "$D/app.env" )
perfu up -d --wait --force-recreate app
caffeinate -dims zsh "$D/gr-pool.sh" "$D" gr-pool11 5
sed -i '' 's/^MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11$/MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10/' "$D/app.env"
perfu up -d --wait --force-recreate app
caffeinate -dims zsh "$D/gr-pool.sh" "$D" gr-pool10 20
# 判定: 時間切れの累計の増分（before と end の COUNT の差）、acquire の MAX の最大（ミリ秒）、k6 の要約の groupPoolLimit_500 の件数
python3 -c '
import ast, sys
for tag in ("gr-pool11", "gr-pool10"):
    rows = [line.split(" ", 2) for line in open(f"build/perf-results/{tag}-metrics.txt")]
    timeout = [ast.literal_eval(r[2])["COUNT"] for r in rows if r[1] == "hikaricp.connections.timeout"]
    acquire = [ast.literal_eval(r[2])["MAX"] for r in rows if r[1] == "hikaricp.connections.acquire"]
    print(tag, "時間切れの増分", timeout[-1] - timeout[0], "acquire MAX の最大（ms）", max(acquire))'
grep -h -o '"groupPoolLimit_[0-9a-z]*": {[^}]*}' build/perf-results/gr-pool11.json build/perf-results/gr-pool10.json

# 4G. 秘密が出ていないことを件数で確かめ（どれも 0）、アプリを止めて監査の件数を読み取りだけで数えてから片付ける
perfu logs app 2>&1 | grep -c 'perf-gr-'
perfu logs app 2>&1 | grep -c -i -e 'Unique index or primary key violation' -e 'UK_GROUPS_NAME_KEY'
#   SELECT event_type, result, failure_reason, COUNT(*) FROM audit_events WHERE event_type LIKE 'GROUP_%'
#     GROUP BY event_type, result, failure_reason
perfu down -v
rm -rf "$D"
docker compose up -d --wait
```

## ロールの管理の場面（Intent 261004-role-menu の U4）

`perf/k6/scenarios.js` のロールの管理の場面を、使い捨ての環境で流す。目標の出典は `aidlc/spaces/default/intents/261004-role-menu/construction/role/` の `nfr-requirements/performance-requirements.md`（NFR2.2・NFR2.5・NFR2.10）と `nfr-design/performance-design.md`（2節）。この節は Bolt B4 の4つの場面、B5 で足した割り当て・作業ロールの6つの場面、B6 で足した権限の受け渡しの場面と準備の場面の分。どの場面も、準備の場面（`rolePrepare`。B6 の受け渡しの口で悪い側のデータを入れる）を流した後に、Performance Validation でまとめて流す（`infrastructure-design/cicd-pipeline.md` 5.3）。Code Generation では `k6 inspect`（`--include-system-env-vars` を付ける）で場面の名前・閾値・`setupTimeout` を確かめるだけにする。

| 場面 | すること（1回の繰り返しに要求1つ） | 判定（閾値は緩めない） |
|---|---|---|
| `roleTreeRead` | 悪い側のロール（1万カラムに明示の値）の木で、スキーマ（op `schemas`）・スキーマの下のテーブル（op `tables`）・100 カラムのテーブル（op `columns`）を順に開く（200） | `iteration_duration{scenario:roleTreeRead,op:…}` の p95 < 1000 ms（op ごと）と `checks` の率 1 |
| `rolePermissionSave` | VU ごとに自分のロール（setup が `perf-save-<実行>-<VU>` を作る）の 100 カラムのテーブルの表を、READ と FULL で交互に保存する（204。変わる点が必ずある） | `iteration_duration{scenario:rolePermissionSave}` の p95 < 1000 ms と `checks` の率 1 |
| `roleAdminRead` | 一覧の1ページ目（op `listFirst`）・最後のページ（op `listLast`）・1件（op `one`）・グループのロール（op `groupRoles`）・利用者のロール（op `userRoles`。B5）を順にくり返す（200）。setup が悪い側のロールをメンバーの候補 `perf-gr-0001` と新しいグループに割り当てる | op ごとの p95 < 1000 ms と `checks` の率 1 |
| `roleAdminOps` | VU ごとに作成（op `create`、201）・自分のロールの名前の変更（op `rename`、2つの名前で交互、204）・作ったロールの削除（op `delete`、204）を順にくり返す（状態が戻る） | op ごとの p95 < 1000 ms と `checks` の率 1 |
| `workRoleSwitch`（B5） | VU ごとに利用者 `perf-gr-<VU>` の2つのロール（setup が作って割り当てる）へ作業ロールを交互に切り替える（どれも保存を書き 204） | `iteration_duration{scenario:workRoleSwitch}` の p95 < 1000 ms と `checks` の率 1 |
| `workRoleRead`（B5） | 利用者 `perf-gr-<VU>` が自分のロールと今の作業ロールを読む（200。setup が割り当てた2つのロールが一覧に含まれることを確かめる。前の場面が残したほかのロールがあっても数では落とさない。Intent 261015-deps-ui-carryover の FR7.1） | 同上 |
| `myPermissionsTree`（B5） | 悪い側のロールを割り当てた利用者 `perf-gr-<VU>`（setup の最後に作業ロールを悪い側のロールへ切り替える。前の場面が保存した作業ロールに頼らない。FR7.1）が、自分の権限の木のスキーマ（op `schemas`）・テーブル（op `tables`）・100 カラムのテーブル（op `columns`）を順に開く（200） | op ごとの p95 < 1000 ms と `checks` の率 1 |
| `roleAssignmentsRead`（B5） | 悪い側のロールの割り当ての一覧（利用者 VUS 名と、その全員をメンバーにしたグループ）を読む（200） | `iteration_duration` の p95 < 1000 ms と `checks` の率 1 |
| `roleAssignOps`（B5） | VU ごとに自分のロール（setup が作る）を、利用者 `perf-gr-<VU>` に割り当てる（op `assignUser`）・外す（op `unassignUser`）・自分のグループ（setup が作る）に割り当てる（op `assignGroup`）・外す（op `unassignGroup`）を順にくり返す（どれも 204 で状態が戻る） | op ごとの p95 < 1000 ms と `checks` の率 1 |
| `rolePoolLimit`（B5） | VU の番号を 5 で割った余りが 1〜3 の VU は作業ロールの切り替え、4・0 の VU は自分のグループへの割り当てと外しを交互にくり返す（下の 3R'） | 閾値を置かない。`rolePoolLimit_204`・`_409`・`_500`・`_other` の件数と、接続プールの値で判定する |

- **形**: どの場面も `constant-vus`（`VUS` 既定 10・`ROLE_DURATION` 既定 3 分）。繰り返しの中に待ちを置かない。操作が2つ以上の場面は、繰り返しの始めに `exec.vu.metrics.tags.op` に操作の名前を入れる（`op` のタグが `iteration_duration` に付くことは B4 の Code Generation の Step 16 で、要求を送らない台本で確かめた）。`http_req_duration{scenario:…}` は colima の VM の時計のずれで崩れうるため、要約に並べて記録するだけにする（閾値 `max>=0` は表示のため）。
- **データ**: 悪い側のロール（名前 `ROLE_WORST_NAME`、既定 `perf-role-worst`）と、版 2 の DSL のスキーマ `ROLE_TREE_SCHEMA`（既定 `large`）・100 カラムのテーブル `ROLE_TREE_TABLE`（既定 `t_001`）は、準備の場面 `rolePrepare`（下の B6 の手順）が入れる（既定の名前は B6 で `perf/dsl-timing.sh` が生成する DSL の名前にそろえた）。名前を変えたときは、同じ値を `-e` で k6 に渡す。台本は一覧のページを順に読んでロールの ID を名前で探し、見つからなければ始める前に止まる。
- **トークン**: 操作する管理者は試験用の管理者 `perf-roleop01`（初期管理者は使わない。手順 2'' の SQL の形で入れる）。setup の最後にトークンを取り直し、場面は 3 分までにする（アクセストークンは 5 分で切れる）。setup の時間の上限は 10 分（`setupTimeout`）。
- **名前**: ロールの名前の鍵は大文字と小文字を区別せず全体で一意のため、作る名前は実行ごとの識別（setup の時刻から作る `runId`）と VU の番号を含める。
- **前の場面の状態に頼らない setup**（Intent 261015-deps-ui-carryover の FR7.1）: `roleAdminRead`・`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead` の setup の割り当ては、割り当て済み（409 `ROLE_NO_CHANGE`）も受け入れる（台本の `roleAssignEnsured`）。手順書の順のまま流しても、1つだけを単独で流しても同じ前提で始まる。
- **利用者の側の場面（B5）**: 作業ロールと自分の権限の場面、割り当ての場面は、グループの管理の節の 2G で入れたメンバーの候補 `perf-gr-0001`〜（パスワードは `PERF_USER_PASSWORD`）を VU の番号の順に使う。setup が操作する管理者の API でロール・グループを作って割り当て、最後に利用者のトークンを VU ごとに取る。作業ロールの切り替えと割り当ては使い捨ての環境の監査に残る。
- 台本の全体を `caffeinate -dims` で包む（場面ごとに起こし直さない）。手順 0 のとおり配備したアプリを止める。使い捨ての環境で流し、配備した環境のデータと監査ログには触れない。ロールの操作は使い捨ての環境の監査に残る。片付けの前に件数を数える。

```bash
# B4・B5 の場面の流し方（先に B6 の準備の場面 rolePrepare を1回流す。下の B6 の手順の role-b6-run.sh がこの台本も呼ぶ。前提: グループの管理の節の 0・1・1G と同じ用意、perf-roleop01 を入れてあること）
cat > "$D/role-run.sh" <<'EOS'
D="$1"
for s in roleTreeRead rolePermissionSave roleAdminRead roleAdminOps \
  workRoleSwitch workRoleRead myPermissionsTree roleAssignmentsRead roleAssignOps; do
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=$s \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$s.json /scripts/scenarios.js
done
EOS
caffeinate -dims zsh "$D/role-run.sh" "$D"

# 3R'. 接続プールの合否の回と記録の回（B5。NFR2.7・NFR2.8、scalability-design.md 2.3・2.4）。グループの管理の節の 3G' の gr-pool.sh を
#      SCENARIO=rolePoolLimit・ROLE_DURATION=2m に替えた写し（role-pool.sh）で流す。判定の値の読み方は 3G' と同じ。
#   合否の回: MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11、VUS=5（作業ロールの切り替え 3 VU・グループへの割り当てと外し 2 VU）、2 分。
#     合格は、時間切れの累計の増分 0・rolePoolLimit_500 が 0 件・acquire の MAX の最大が 20 ms 未満。
#   記録の回: MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10、VUS=20（同じ構成）、2 分。判定はせず値を記録する。acquire の MAX が 10 ms 以上に
#     届いたこと（上限に届いた証拠）を確かめ、届かなければ VUS=40 で1回だけ流し直す。
#   acquire は応答の baseUnit を確かめて秒にそろえてから 0.020 と比べる（3G' の read_metrics はミリ秒にそろえるため、20 と比べるのと
#     同じ。baseUnit が seconds でないときは値をそのまま使わず、単位を書き留めてから換算する）。
#   健全性の確かめ（/actuator/health）のほかに接続を使う者が無かったことを、試験の前後の active が 0 であることと、試験の間のアプリの
#     ログに role・group の操作のほかの要求が無いことで切り分ける。
sed -e 's/SCENARIO=groupPoolLimit/SCENARIO=rolePoolLimit/' -e 's/-e DURATION=2m/-e ROLE_DURATION=2m/' "$D/gr-pool.sh" > "$D/role-pool.sh"
( umask 077; sed -i '' '/^MASTERSMITH_DB_MAXIMUM_POOL_SIZE=/d' "$D/app.env"; printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11\n' >> "$D/app.env" )
perfu up -d --wait --force-recreate app
caffeinate -dims zsh "$D/role-pool.sh" "$D" role-pool11 5
sed -i '' 's/^MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11$/MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10/' "$D/app.env"
perfu up -d --wait --force-recreate app
caffeinate -dims zsh "$D/role-pool.sh" "$D" role-pool10 20
grep -h -o '"rolePoolLimit_[0-9a-z]*": {[^}]*}' build/perf-results/role-pool11.json build/perf-results/role-pool10.json
#   SELECT event_type, result, failure_reason, COUNT(*) FROM audit_events
#     WHERE event_type LIKE 'ROLE_%' OR event_type = 'WORK_ROLE_SWITCHED' GROUP BY event_type, result, failure_reason
```

#### 警報の確かめの回（Intent 261004-role-menu の Performance Validation、Q6: A）

Observability Setup から持ち主を移された3つの警報（`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`）が、ロール・グループの書き込みの2本使いで鳴ることを確かめる。判定の回（上の 3R' の上限 11・10 の回と、上限 30 の p95 の場面）とは別の回にし、判定の回の条件は変えない。形は「接続プールの上限を下げて流す」（Intent 261003-user-admin-followup の FR4）と同じで、場面だけを `rolePoolLimit`（作業ロールの切り替えとグループへの割り当て・外し。どれも確定の後の監査で2本目の接続を借りる）にする。

- **上限 4・`VUS=12`・5 分**: 同時の書き込みが上限の3倍で全員が2本目を待ち、`connection-timeout`（5 秒）で時間切れになって監査の書き込みの失敗の ERROR が出る。5 分は `ms-pool-pending`（1 分ごとの値で `for: 1m`）が指標の送信の時点を2回以上またぐため。鳴らなければ、条件が起きたかを指標とログで確かめ、起きなかったときだけ上限 2・`VUS=20`・8 分で1回流し直す。
- **利用者の数**: `rolePoolLimit` は VU ごとにメンバーの候補 `perf-gr-<VU の番号>` を使う（setup の `roleMembers`。`VUS` 人を候補 `perf-gr-0001` から引く）。グループの管理の節の 2G で 1,000 人入れてあるため、`VUS=12`（流し直しの 20）でも足りる。足すものは無い。
- **トークンの有効期限**: `rolePoolLimit` は setup で取ったトークンを使い続け、アクセストークンの有効期限は既定 5 分のため、5 分（流し直しの 8 分）の回では終わりの近くで 401（`rolePoolLimit_other`）が混ざる。この回だけ、使い捨てのアプリに `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL=15m` を足す（接続プールの使い方は変わらない。配備したアプリの設定は変えない）。
- **手元の監視**: lgtm を先に使い捨ての環境の網に別名 `lgtm` でつないでから、使い捨てのアプリを上限 4 と外部エクスポートで起動し直す（先にアプリを起動するとログの送信の失敗の ERROR が出て `ms-error-logs` の数に混ざる）。警報の決まりのファイルはそのまま読み込ませる。
- **BUSY**: 409 `ROLE_BUSY`・`GROUP_BUSY` が出たときは、L3 と L4 の結び付きを `traceId` で数える。0 件のときは `Unverified`（feedback-optimization へ渡す）。
- 終わったら、上限・外部エクスポート・有効期限の4行を `app.env` から消して使い捨てのアプリを起動し直すか、環境ごと片付ける。

```bash
# 前提: 上の 3R' までを済ませ、使い捨ての環境（mastersmith-perf）が動いていること（ネットワーク mastersmith-perf_default）。
# a. 使い捨てのアプリにだけ、上限 4・外部エクスポート・トークンの有効期限を足す（秘密ではない値）
( umask 077; sed -i '' '/^MASTERSMITH_DB_MAXIMUM_POOL_SIZE=/d' "$D/app.env"
  printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=4\nMASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true\nMASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318\nMASTERSMITH_AUTH_ACCESS_TOKEN_TTL=15m\n' >> "$D/app.env" )
# b. 「接続プールの上限を下げて流す」の b と同じ手順で lgtm を起動し（先に網につなぐ）、その後にアプリを起動し直して上限 4 を確かめる
# c. 同じ節の c の ua-limit.sh を、場面だけ替えた写し（role-limit.sh）にして流す
sed -e 's/SCENARIO=userAdminPoolLimit/SCENARIO=rolePoolLimit/' -e 's/-e DURATION=\$DUR -e PERF_UA_COUNT=20/-e ROLE_DURATION=$DUR/' \
  "$D/ua-limit.sh" > "$D/role-limit.sh"
grep -c 'SCENARIO=rolePoolLimit' "$D/role-limit.sh"   # 1 であること
grep -c 'ROLE_DURATION=\$DUR' "$D/role-limit.sh"      # 1 であること
caffeinate -dims zsh "$D/role-limit.sh" "$D" role-limit 12 5m
# 判定の読み方は同じ節の (a)・(b) と同じ（結果の名前の limit を role-limit に替える）。件数は k6 の要約の rolePoolLimit_204・_409・_500・_other
grep -h -o '"rolePoolLimit_[0-9a-z]*": {[^}]*}' build/perf-results/role-limit.json
# d. BUSY の L3 と L4 を traceId で突き合わせる（同じ節の d の USER_ADMIN_BUSY を ROLE_BUSY・GROUP_BUSY に替える。件数だけを出す）
docker logs mastersmith-perf-app-1 2>/dev/null | python3 -c '
import collections, json, sys
l3, l4 = [], collections.Counter()
for line in sys.stdin:
    try:
        r = json.loads(line)
    except ValueError:
        continue
    if r.get("message") == "要求をエラー応答に変換しました" and r.get("code") in ("ROLE_BUSY", "GROUP_BUSY"):
        l3.append(r.get("traceId"))
    elif r.get("message") == "行の排他を取れませんでした":
        l4[r.get("traceId")] += 1
print("L3", len(l3), "L4", sum(l4.values()), "L4 をちょうど1件持つ L3", sum(1 for t in l3 if l4.get(t) == 1))'
# e. 結果を確かめて記録してから lgtm を消す（docker rm -f mastersmith-perf-lgtm）。監査の件数を数えてから片付ける
```

### 権限の受け渡しの場面と準備の場面（B6）

目標の出典は `construction/role/nfr-requirements/performance-requirements.md` の NFR2.4（上限ちょうどの YAML の確かめ 15 秒・適用 30 秒）・NFR2.5（書き出し）と `scalability-requirements.md` の NFR2.9、`nfr-design/performance-design.md` 2.3。

| 場面 | すること | 判定（閾値は緩めない） |
|---|---|---|
| `rolePrepare` | 1 VU・1 回。`DSL_FILE` の版 2 の DSL を投入して適用し、準備の YAML を台本の中で作って確かめ・適用する（下の「準備のデータ」） | `checks{scenario:rolePrepare}` の率 1 |
| `roleExport` | 書き出し（200）。10 VU・3 分（`ROLE_DURATION`） | `iteration_duration{scenario:roleExport}` の p95 < 1000 ms と `checks` の率 1 |
| `roleTransferLargeCheck` | 上限ちょうど（5,242,880 バイト）の YAML（`ROLE_YAML_FILE`）の確かめ（200）。1 VU・1 回 | `iteration_duration{scenario:roleTransferLargeCheck}` の最大 < 15000 ms と `checks` の率 1 |
| `roleTransferLargeApply` | 同じ YAML の適用（200。置き換え 19 ロール × 1万カラム）。setup で同じ YAML を確かめて指紋を受ける。1 VU・1 回 | `iteration_duration{scenario:roleTransferLargeApply}` の最大 < 30000 ms と `checks` の率 1 |

- **準備のデータ**（`rolePrepare` が作る。ロールの合計 `ROLE_PREPARE_TOTAL`、既定 1,000）: 悪い側のロール `perf-role-worst`（スキーマ `large` の主権限と、`t_001`〜`t_100` × `c_001`〜`c_100` の1万カラムに READ と FULL を交互に明示）、置き換える先のロール `perf_large_00001`〜`perf_large_00019`（`ROLE_LARGE_PREFIX`・`ROLE_LARGE_COUNT`、既定 19。スキーマの主権限 NONE だけ）、業務のメニューの悪い側のロール `perf-nav-worst`（B7 で足した。下の「業務のメニューの場面」）、残りは設定の無い埋めるロール `perf-fill-NNNN`。グループ・メンバー・割り当ては、B5 の各場面の setup がその場面に合わせて API で作る（準備の場面では作らない）。
- **DSL**: `perf/dsl-timing.sh` が対象DB の 100 テーブル × 100 カラムのスキーマ `large` から生成した版 2 の DSL（`<結果の置き場>/postgres/generated.yaml`）を `DSL_FILE` で渡す。生成には対象DB が要るが、使い捨ての環境の準備と各場面は対象DB を要らない（DSL の適用は対象DB に接続しない。プレビューの照合は届かなくても警告になるだけ。基盤の読み直しの R-07）。生成した DSL が手元にあれば作り直さなくてよい。
- **上限ちょうどの YAML**: テストの手伝いの1ファイル `backend/src/test/java/cherry/mastersmith/role/testsupport/LargeRoleTransferYaml.java` を、1ファイルのソースの起動で呼んで作る（テストと同じ部品。計画の 13節 Q7: A）。ロール `perf_large_00001`〜`00019` に、`large` の `t_001`〜`t_100` × `c_001`〜`c_100`（1万カラム）を READ で明示し、末尾の YAML のコメント（1行 1,000 バイトまでに分ける。Intent 261015-deps-ui-carryover の FR9.2 の1行の長さの上限 65,536 の内に収めるため）で 5,242,880 バイトちょうどにする（上限は Build and Test の Q1 で 10 MiB・節 1,000,000 から 5 MiB・節 500,000 に下げた）。名前は既定の短い形（`c_001`）のため、中身は上限の約 96%（約 39 万の節）で、残り約 185 KB がコメントになる。節の数が上限に近い形と時間は Build and Test で1回測る（`reliability-design.md` 1.3）。
- **順番**: 準備の場面を1回 → B4・B5 の場面 → `roleExport` → **`roleTransferLargeCheck` → `roleTransferLargeApply` を最後に流す**（適用で 19 ロールの設定が入れ替わり、準備の状態が変わるため。流し直すときは使い捨ての環境を作り直して、準備からやり直す。基盤の読み直しの R-05）。
- **メモリ**: 上限ちょうどの YAML の確かめ・適用は、テストの JVM ではなく配備と同じ形（コンテナの上限 2g・最大ヒープの割合 50%）の使い捨てのアプリで流し、確かめ・適用の間のヒープの最大（`/actuator/metrics/jvm.memory.used?tag=area:heap` を数秒ごと）と OOMKilled の有無を記録する（単体・結合のテストでは、節の上限の境界を Spring を起動しない単体テストで確かめた。依頼者の決定 A）。
- 台本の全体を `caffeinate -dims` で包む。権限の受け渡しの適用は使い捨ての環境の監査に `ROLE_TRANSFER_APPLIED` として残る。

```bash
# 前提: グループの管理の節の 0・1・1G・2G と同じ用意（perf-roleop01 を入れてあること）。生成した版 2 の DSL を用意する
#   （手元に無ければ: KEEP=1 MASTERSMITH_IMAGE_TAG=perf-dsl OUT_DIR="$PWD/build/perf-results/dsl-k6" ./perf/dsl-timing.sh postgres の後に片付ける）
GEN=build/perf-results/dsl-k6/postgres/generated.yaml
mkdir -p build/perf-results/role-data && cp "$GEN" build/perf-results/role-data/dsl.yaml
java backend/src/test/java/cherry/mastersmith/role/testsupport/LargeRoleTransferYaml.java \
  build/perf-results/role-data/large-5mb.yaml 19 10000 0 5242880 large t_ 100 perf_large_ c_
wc -c build/perf-results/role-data/large-5mb.yaml   # 5242880 であること

cat > "$D/role-b6-run.sh" <<'EOS'
D="$1"
k6r() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" "$@" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" -v "$PWD/build/perf-results/role-data:/data:ro" \
    grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$name.json /scripts/scenarios.js
}
k6r rolePrepare -e SCENARIO=rolePrepare -e DSL_FILE=/data/dsl.yaml
zsh "$D/role-run.sh" "$D"                                   # B4・B5 の場面（上の手順）
k6r roleExport -e SCENARIO=roleExport
k6r roleTransferLargeCheck -e SCENARIO=roleTransferLargeCheck -e ROLE_YAML_FILE=/data/large-5mb.yaml
k6r roleTransferLargeApply -e SCENARIO=roleTransferLargeApply -e ROLE_YAML_FILE=/data/large-5mb.yaml
EOS
caffeinate -dims zsh "$D/role-b6-run.sh" "$D"
#   SELECT event_type, result, failure_reason, COUNT(*) FROM audit_events WHERE event_type = 'ROLE_TRANSFER_APPLIED'
#     GROUP BY event_type, result, failure_reason
```


## 性能の測り直し（Intent 261015-deps-ui-carryover の FR7.2・FR8・FR9.1・FR9.4）

前の Intent（261004-role-menu）の Performance Validation から持ち越した3つの測り直しを、新しい土台のイメージ（eclipse-temurin `25.0.4.1_1-jre-noble`）の使い捨ての環境で流す。Performance Validation の段が無いため、流すのは Build and Test（`aidlc/spaces/default/memory/project.md` の学び）。目標は緩めない。測れなかった目標は `Unverified`、外れた目標は `Not Met` とする。

- **共通の前提**: 上の「手順」の 0・1 と、グループの管理の節の 1G（指標の公開）・2G（メンバーの候補と `perf-roleop01`）、ロールの管理の節の B6 の準備（`rolePrepare`）を済ませる。配備したアプリは手順 0 のとおり止める。台本の全体を `caffeinate -dims` で包み、流す前に `pmset -g assertions` で眠らない状態か、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていた回は数えずに流し直し、外した回と理由を記録する。
- **1行の長さの上限**（FR9.2）: DSL と権限の YAML の1行は 65,536 コードポイントまで（`DslFormat.MAX_LINE_LENGTH`）。超えると DSL は 422 `DSL_INVALID`（誤りの種類 `SIZE_LIMIT`）、権限の YAML は 413 `PAYLOAD_TOO_LARGE` で拒否される。前の Intent の悪い形（1.5 MB の1行のコメント）と、流れの書き方の並びを1行に詰めた形は、読む前に拒否されるため使えない。下の (b) の悪い形は行を分けて作る。

### (a) ロールの書き込みの待ちの最大の合否の回を 5 回（FR8）

ロールの管理の節の 3R' の合否の回（上限 11・`VUS=5`・2 分、`rolePoolLimit`）を 5 回流す。数 ms の目標は1回の値が揺れるため、合否は 5 回の値で決める（`project.md` の学び）。

- **合否**（FR8.4）: 5 回すべてで acquire の MAX の最大が 20 ms 未満なら `Met`、1回でも 20 ms 以上なら `Not Met`。あわせて各回の時間切れの累計の増分 0・`rolePoolLimit_500` が 0 件であること。
- **回ごとに作り直す**: 指標の MAX と累計を回ごとにそろえるため、各回の前に使い捨てのアプリを作り直す（`--force-recreate`）。
- **20 ms 以上の回の見立て**（FR8.2）: その回の `role-pool11-r<回>-metrics.txt` で MAX が最大になった時刻を読み、その前後 30 秒の (1) アプリのログの WARN・ERROR の件数（`監査イベントの記録に時間がかかりました` を含む）、(2) `jvm.gc.pause` の MAX（下の台本が回の前後に記録する）、(3) `docker stats` の CPU を並べる。見立ては事実（値）と仮説を分けて書く。直す形は依頼者に諮る（FR8.3）。

```bash
# 前提: 3R' の role-pool.sh を作ってあること（ロールの管理の節）
( umask 077; sed -i '' '/^MASTERSMITH_DB_MAXIMUM_POOL_SIZE=/d' "$D/app.env"; printf 'MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11\n' >> "$D/app.env" )
cat > "$D/role-pool-5.sh" <<'EOS'
D="$1"; R=build/perf-results
gc() { curl -s "http://127.0.0.1:18080/actuator/metrics/jvm.gc.pause" >> "$R/$1-gc.json"; echo >> "$R/$1-gc.json"; }
for n in 1 2 3 4 5; do
  docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait --force-recreate app
  date -u +"r$n start %Y-%m-%dT%H:%M:%SZ" >> "$R/role-pool-5-times.txt"
  gc role-pool11-r$n
  zsh "$D/role-pool.sh" "$D" role-pool11-r$n 5
  gc role-pool11-r$n
  docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' mastersmith-perf-app-1 >> "$R/role-pool11-r$n-stats.txt"
  date -u +"r$n end %Y-%m-%dT%H:%M:%SZ" >> "$R/role-pool-5-times.txt"
done
EOS
pmset -g assertions | grep -E 'PreventUserIdleSystemSleep|PreventSystemSleep'
caffeinate -dims zsh "$D/role-pool-5.sh" "$D"
pmset -g log | grep -E ' (Sleep|DarkWake) ' | tail -20   # 回の始めと終わりの時刻（role-pool-5-times.txt）の間に無いこと
# 判定: 回ごとの時間切れの増分・acquire の MAX の最大（ミリ秒）と、rolePoolLimit_500 の件数
python3 -c '
import ast
for n in range(1, 6):
    tag = f"role-pool11-r{n}"
    rows = [line.split(" ", 2) for line in open(f"build/perf-results/{tag}-metrics.txt")]
    timeout = [ast.literal_eval(r[2])["COUNT"] for r in rows if r[1] == "hikaricp.connections.timeout"]
    acquire = [(r[0], ast.literal_eval(r[2])["MAX"]) for r in rows if r[1] == "hikaricp.connections.acquire"]
    at, peak = max(acquire, key=lambda x: x[1])
    print(tag, "時間切れの増分", timeout[-1] - timeout[0], "acquire MAX の最大（ms）", round(peak, 2), "時刻", at,
          "20ms 以上" if peak >= 20 else "")'
grep -h -o '"rolePoolLimit_500": {[^}]*}' build/perf-results/role-pool11-r*.json
```

### (b) 大きな入力のヒープ（FR9.1・FR9.4）

10 MiB の DSL の投入と、1行の長さの上限の内で作る悪い形の 5 MiB の権限の YAML の確かめ・適用の間のヒープを、配備と同じ上限（コンテナ 2g・最大ヒープの割合 50%）の使い捨てのアプリで測る。続けて `dslMixed` の `memory.peak` を取る。

- **合否**（FR9.4）: どの測りでも OutOfMemoryError と JVM の停止（`ExitOnOutOfMemoryError` の終了コード 3）が無く、`OOMKilled` が false。`dslMixed` の `memory.peak` が、FR9.3 の見直しの後のメモリの上限（変えないときは 2g）の 90% 未満。
- **ヒープの記録**: `jvm.memory.used?tag=area:heap` を 0.5 秒ごとに読み、測りの間の最大を記録する（GC の後の値ではなく読んだ値の最大。前の Intent の見積もり 256〜384 MB との差と、GC の後でも残った値を並べる）。最大ヒープは `jvm.memory.max?tag=area:heap` で読む。
- **悪い形の権限の YAML**: 流れの書き方の並びで節の上限（500,000）ちょうどを作り、要素 1,000 個ごとに改行して続ける。残りを1行 1,000 バイトまでのコメントの行で埋めて 5,242,880 バイトちょうどにする。前の悪い形（49.7 万の節＋1.5 MB の1行のコメント）とは、行の分け方と埋め草の形が違う（形の違いを結果に記録する）。中身は `roles` が並びのため、確かめ・適用とも 400 `ROLE_TRANSFER_INVALID` で返る（読み込みと節の数え上げは最後まで行う。適用の指紋は比べる前に同じ上限で拒否される）。
- **memory.peak**: 前の最大の値を消すため、`dslMixed` の前に使い捨てのアプリを作り直す（`project.md` の学び）。読み方は `docker exec mastersmith-perf-app-1 cat /sys/fs/cgroup/memory.peak`。

```bash
# 前提: 共通の前提に加え、DSL の時間の節の手順で 10 MiB の DSL（valid-10mb.yaml）を build/perf-results/dsl-k6/postgres/ に作ってあること
# 悪い形の 5 MiB の権限の YAML を作る（結果の置き場だけに書き、リポジトリには置かない）
python3 - build/perf-results/role-data/worst-5mb.yaml <<'EOS'
import sys
target, nodes = 5_242_880, 500_000
items = nodes - 5   # 根・2つのキー・版の値・並びの5節と要素（RoleTransferReaderTest の flowSequence と同じ数え方）
parts = ["version: 1\nroles: ["]
for i in range(items):
    parts.append(("1" if i == 0 else (",\n  1" if i % 1000 == 0 else ",1")))
parts.append("]\n")
text = "".join(parts)
rest = target - len(text.encode())
while rest > 0:
    n = min(rest, 1000)
    text += "\n" if n == 1 else "#" + "x" * (n - 2) + "\n"
    rest -= n
open(sys.argv[1], "w").write(text)
EOS
wc -c build/perf-results/role-data/worst-5mb.yaml                                    # 5242880 であること
awk '{ if (length($0) > m) m = length($0) } END { print m }' build/perf-results/role-data/worst-5mb.yaml   # 65536 以下であること

# ヒープを 0.5 秒ごとに読む（引数: 結果の名前。止めるときは kill）
heapwatch() {
  while :; do
    printf '%s\t%s\n' "$(date -u +%H:%M:%SZ)" \
      "$(curl -s 'http://127.0.0.1:18080/actuator/metrics/jvm.memory.used?tag=area:heap' | python3 -c 'import json,sys; print(int(json.load(sys.stdin)["measurements"][0]["value"]))')" \
      >> "build/perf-results/$1-heap.tsv"
    sleep 0.5
  done
}
# 管理者のトークン（k6.env の管理者でログインする。値は表示しない）
token() {
  set -a; . "$D/k6.env"; set +a
  curl -s -H 'Content-Type: application/json' -H 'Origin: http://127.0.0.1:18080' \
    -d "{\"email\":\"$PERF_ADMIN_EMAIL\",\"password\":\"$PERF_ADMIN_PASSWORD\"}" \
    http://127.0.0.1:18080/api/auth/login | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])'
}
cat > "$D/heap-run.sh" <<'EOS'
D="$1"; R=build/perf-results; B=http://127.0.0.1:18080
source "$D/heap-funcs.sh"
curl -s "$B/actuator/metrics/jvm.memory.max?tag=area:heap" > "$R/heap-max.json"
T=$(token)
heapwatch dsl10 & W=$!
curl -s -o /dev/null -w 'dsl10 %{http_code} %{time_total}s\n' -H "Authorization: Bearer $T" -H 'Origin: http://127.0.0.1:18080' \
  -H 'Content-Type: application/yaml' --data-binary @build/perf-results/dsl-k6/postgres/valid-10mb.yaml "$B/api/admin/dsl/preview?source=UPLOAD" >> "$R/heap-codes.txt"
sleep 5; kill $W
heapwatch role5 & W=$!
FP=$(printf '0%.0s' $(seq 64))   # 適用は形の正しい指紋（16 進数の小文字 64 文字）の見出しが要る。中身は比べる前に上限と検証で止まる
for p in check apply; do
  curl -s -o /dev/null -w "role5-$p %{http_code} %{time_total}s\n" -H "Authorization: Bearer $T" -H 'Origin: http://127.0.0.1:18080' \
    -H "X-Role-Transfer-Fingerprint: $FP" -H 'Content-Type: application/yaml' \
    --data-binary @build/perf-results/role-data/worst-5mb.yaml "$B/api/admin/role-transfer/$p" >> "$R/heap-codes.txt"
done
sleep 5; kill $W
unset T
EOS
typeset -f heapwatch token > "$D/heap-funcs.sh"
caffeinate -dims zsh "$D/heap-run.sh" "$D"
cat build/perf-results/heap-codes.txt                                                # 投入 201・確かめと適用 400 であること
for n in dsl10 role5; do sort -t$'\t' -k2 -n "build/perf-results/$n-heap.tsv" | tail -1; done   # 測りごとのヒープの最大（バイト）
docker inspect --format '{{.State.Status}} {{.State.ExitCode}} OOMKilled={{.State.OOMKilled}}' mastersmith-perf-app-1
docker logs mastersmith-perf-app-1 2>&1 | grep -c 'OutOfMemoryError'                 # 0 であること

# dslMixed の memory.peak。使い捨てのアプリを作り直してから、DSL の節の手順（k6d の関数と ui.env、試験用の利用者 11 名）で
# dslMixed を流し（台本の全体を caffeinate -dims で包む）、流した後に読む
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait --force-recreate app
#   （ここで DSL の節の k6d で dslMixed を流す）
docker exec mastersmith-perf-app-1 cat /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.max   # peak / max が 0.9 未満であること
pmset -g log | grep -E ' (Sleep|DarkWake) ' | tail -20
```

- 道（`/api/admin/role-transfer/check`・`/apply`）・指紋の見出し（`X-Role-Transfer-Fingerprint`）・ログインの応答の項目（`accessToken`）は、Code Generation で `RoleTransferPaths`・`RoleTransferController`・`perf/k6/scenarios.js` の `login` と照らした。この節の台本（`heap-run.sh`・`role-pool-5.sh`・`role-three.sh`）は Code Generation では流していないため、最初の回で値が取れているかを結果のファイルで確かめてから続ける。
- FR9.3（メモリの上限と最大ヒープの割合を変えるか）は、この測りの結果をもとに Build and Test で決める。変えるときは値・根拠・戻し方（設定の値を戻す）を記録する。

#### `dslMixed` の `memory.peak` と `memory.stat` の記録（Intent 261016-business-data-views の U5）

前の Intent で Not Met のまま持ち越した `dslMixed` の `memory.peak`（FR16）を、内訳（`memory.stat`）と内部DB のファイルの大きさを1秒ごとに記録しながら測る。1回の測りは `perf/dsl-mixed-memory.sh` が受け持ち、既定の接続先で2回、ページキャッシュを減らす手の試し（H2 の `RETENTION_TIME=0`）で2回、合わせて4回流す。測るのは Build and Test（この Intent には Performance Validation の段があるが、`memory.peak` の内訳の測りは依頼者の決定（Build and Test の質問の Q2: A）で Build and Test で行った）。

- **合否**（BR3.1・BR3.5）: 既定の2回で `memory.peak / memory.max < 0.9` が2回とも成り立てば Met。物差しは変えない（`anon` に替えない。team.md の Testing Posture）。試しの2回は合否に使わず、効いたか（2回とも 0.9 未満か）を見て、取り込むかを依頼者に諮る（BR4.2・BR4.3）。コンテナの上限 2g は上げない。
- **並べる値**（BR3.2）: `anon` の最大 / `memory.max`、peak の時（`memory_current` が最大の行）の `anon`・`file`・`inactive_file`・`kernel`、`file` の始めと最大の差、内部DB のファイル（`mv_db_bytes`）の始めと最大の差。どれも `summary.txt` に出る。
- **記録の列**（`memory.tsv`。1秒ごと、時刻は VM の時計の UTC）: `time_utc`・`memory_current`・`memory_peak`・`anon`・`file`・`inactive_file`・`active_file`・`kernel`・`mv_db_bytes`。`memory.stat` は名前で引いてこの順に出し（ファイルの並びに頼らない）、無いキーは `NA` になる。ループはコンテナの中の bash の1つのループを `docker exec` 1回で回し、`--privileged`・`--user`・追加の権限は付けない。秒の上限（既定 320 = 前置き 20 + 場面 180 + 後置き 60 + 余裕 60）で自分で終わり、台本も後置きの 60 秒の後に止める。
- **1回の流れ**（台本がすること）: 使い捨てのアプリを作り直す（`--force-recreate`。前の peak を消す）→ 始める前の確かめ（`memory.peak`・`memory.current`・`memory.max`・`memory.stat` が読め、`memory.stat` に5つのキーがあり、`mastersmith.mv.db` があること。外れたら `invalid.txt` に理由を書いて終了コード 3 で止まり、その回は合否に使わない）→ 時刻の差（始め）→ ループを始め 20 秒後に `dslMixed`（VUS 10・3 分・10 MiB の DSL）→ 終わりの後 60 秒 → ループを止める → 時刻の差（終わり）→ `memory.peak`・`memory.max`・状態（OOMKilled・終了コード）→ まとめ → WARN・ERROR・OutOfMemoryError の件数とロガーごとの WARN の件数 → アプリを止めて内部DB の設定の値を読む。配備したアプリの停止と起動し直し、`caffeinate -dims`、`pmset` の確かめ、環境の作り分け、片付けは、下の手順で行う。
- **記録の置き場**: `<DSL の置き場>` の隣の `memory/<回の名前>/`（例: `build/perf-results/dsl-k6/memory/default-1/`）。`run.txt`（条件）・`clock.tsv`（各回の始めと終わりの、手元の時刻（前）・VM の時刻・手元の時刻（後）と範囲 VM−後・VM−前）・`memory.tsv`・`k6.txt`（k6 の始めと終わりの時刻・終了コード・ログインの利用者の行の数と重なりの数・後置きの欠けの秒）・`dslMixed.json`（k6 の要約）・`peak.txt`・`summary.txt`・`warn.txt`・`settings.txt`（`RETENTION_TIME` の値）。k6 の出力は利用者の名前を含むため記録に残さず、件数だけを `k6.txt` に書く。新しい記録に書くのは数値・時刻・状態・ロガーの名前と件数だけ。
- **後置きの欠け**: k6 が遅れて後置きの 60 秒がループの上限に入りきらなかったときは、`k6.txt` の `post_missing_seconds` が 1 より大きくなる。最後の完全な行の時刻が読めなかったときは `NA`。`memory.peak` はループを止めた直後に cgroup から先に読むため欠けない。
- **切れた行**: ループを止めたときに書きかけで切れた最後の行（改行で終わらない行）と、9 列・時刻と数か `NA` の形でない行は、まとめと後置きの計算に使わず、捨てた数を `summary.txt` の `dropped_rows` に出す（`memory.tsv` には残る）。`./perf/dsl-mixed-memory.sh --tsv-check <TSV>` で、完全な行の数・最後の完全な行の時刻・まとめだけを出せる（Docker を使わない）。
- **ループの後始末**: 台本が途中で止まっても（失敗・Ctrl-C・TERM）、`trap` で手元の `docker exec` と、コンテナの中の目印（台本の1回ごとに一意の引数）を持つ記録のループの処理だけを止める。アプリの処理には触れない。
- **設定の値の確かめ**（BR4.1）: 台本は peak と stat を読んだ後でアプリを `docker compose stop app` で止め（止めると peak が消えるため、順は必ず「peak と stat を読む → 止める → 設定を読む」）、内部DB のボリュームを読み取り専用（`:ro`、`ACCESS_MODE_DATA=r`）で開いて `INFORMATION_SCHEMA.SETTINGS` の `RETENTION_TIME` を読む。H2 の `Shell` は測るアプリのイメージ（同じ JRE）で動かす。`RETENTION_TIME` は接続先の URL の設定で DB に保存され、アプリの起動で DB を開いた時点で当たるため、測りの後に読んだ値を測りの間に効いていた値とみなす。既定の回は H2 の既定の値（45000）、試しの回は 0 であること。値が違う・読めなかった試しの回は、試しの結果に使わない。
- **環境の分け方**: 既定の2回は1つの `KEEP=1` の環境（内部DB のボリュームは膨らんだまま流す。悪い側の条件）、試しの2回は既定の環境を消してから `PERF_DB_URL` を付けて作り直した環境で流す。`OUT_DIR` を分ける（`build/perf-results/dsl-k6` と `build/perf-results/dsl-k6-retention`）。`perf/dsl-timing.sh` は既存の環境があると止まるため、必ず消してから作る。
- **一時ファイルと秘密**: `perf/dsl-timing.sh` の一時ディレクトリ（ホームの下、権限 700）には、`app.env`（署名鍵・仮の管理者のパスワード）・`targetdb.env`（対象DB の管理者と読み取りのアカウントのパスワード）・`app-targetdb.env`（対象DB の読み取りの資格情報）・`login.json`（仮の管理者のパスワード）・`ui.env`（仮の管理者のパスワード。この手順で `PERF_USER_PASSWORD` を足す）・`auth.header`（アクセストークン）・`apply.json`（前の適用の previewId。秘密ではない）がある。`KEEP=1` はこの手順の前提のため、環境を消すまでの間は秘密が一時ディレクトリに残る。終わりに手で `down -v` と一時ディレクトリの削除を行い、消えたことを確かめる。結果の置き場（`OUT_DIR`）の `resp-*.body`・`busy-*.result`・`busy-*.stderr`（応答・状態コード・curl の標準エラー。秘密ではない）と、この手順の記録は、下の秘密の検索の対象に入れる。

```bash
# 前提: 上の「手順」の 0（配備したアプリを止める・WAR と使い捨て用のタグのイメージを作る）を済ませる。
#       配備したアプリは測る間だけ止め、4回が終わったら起動し直す（下の 6）。
export MASTERSMITH_IMAGE_TAG=<タグ> MASTERSMITH_CONTAINER_CPUS=4 MASTERSMITH_CONTAINER_MEMORY=2g
export K6_IMAGE=grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34   # k6d と同じ固定の値

# 1. 試験用の利用者 11 名を入れ、ui.env に PERF_USER_PASSWORD を足す関数（D は perf/dsl-timing.sh が表示した一時ディレクトリ）。
#    zsh ではコマンドを変数に入れても単語に分かれないため、関数にする。値は表示しない
perf_users() {
  local up hash sql
  up=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
  hash=$(htpasswd -nbBC 12 x "$up" | cut -d: -f2)
  sql="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name) VALUES $(for i in $(seq -w 1 11); do printf "('perf-user%s@example.test', '%s', FALSE, CURRENT_TIMESTAMP, 'perf-user%s@example.test')," "$i" "$hash" "$i"; done | sed 's/,$//')"
  MASTERSMITH_PERF_ENV_FILE="$D/app.env" MASTERSMITH_PERF_TARGETDB_ENV_FILE="$D/targetdb.env" MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE="$D/app-targetdb.env" \
    docker compose -p mastersmith-perf -f docker/perf/compose.yaml stop app
  cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
  docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
    eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url "$(grep '^MASTERSMITH_DB_URL=' "$D/app.env" | cut -d= -f2- | sed 's#/app/data#/data#' | grep . || echo jdbc:h2:file:/data/mastersmith)" -user sa -password "" -sql "$sql" > /dev/null
  rm build/h2-perf.jar
  ( umask 077; printf 'PERF_USER_PASSWORD=%s\n' "$up" >> "$D/ui.env" )
}

# 2. 既定の環境を作り（10 MiB の DSL も build/perf-results/dsl-k6/postgres/ にできる）、利用者を入れる
KEEP=1 OUT_DIR="$PWD/build/perf-results/dsl-k6" ./perf/dsl-timing.sh postgres
D=<表示された一時ディレクトリ>
perf_users

# 3. 既定の2回（台本の全体を caffeinate -dims で包む。流す前と後に眠りを確かめる）
pmset -g assertions | grep -E 'PreventUserIdleSystemSleep|PreventSystemSleep'
caffeinate -dims sh -c 'for r in default-1 default-2; do ./perf/dsl-mixed-memory.sh "$1" "$r" build/perf-results/dsl-k6/postgres || exit $?; done' _ "$D"
pmset -g log | grep -E ' (Sleep|DarkWake) ' | tail -20   # 各回の clock.tsv の始めと終わりの間に無いこと。入っていた回は使わずに流し直す
cat build/perf-results/dsl-k6/memory/default-*/summary.txt build/perf-results/dsl-k6/memory/default-*/settings.txt

# 4. 秘密の検索（今回の OUT_DIR だけを対象に、件数だけを出す。どれも 0 であること）。その後、既定の環境を消して消えたことを確かめる
secret_counts() {   # 引数: OUT_DIR
  local k v
  for k in MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD MASTERSMITH_AUTH_SIGNING_KEY; do
    v=$(grep "^$k=" "$D/app.env" | cut -d= -f2-)
    printf '%s %s\n' "$k" "$(grep -rF -- "$v" "$1" | wc -l | tr -d ' ')"
  done
  v=$(grep '^PERF_USER_PASSWORD=' "$D/ui.env" | cut -d= -f2-)
  printf 'PERF_USER_PASSWORD %s\n' "$(grep -rF -- "$v" "$1" | wc -l | tr -d ' ')"
  unset v
  printf 'env.txt と TSV の password・PASSWORD・jdbc:mysql・jdbc:mariadb・jdbc:postgresql %s\n' \
    "$(cat "$1/env.txt" "$1"/memory/*/memory.tsv "$1"/memory/*/clock.tsv | grep -c -E 'password|PASSWORD|jdbc:mysql|jdbc:mariadb|jdbc:postgresql')"
}
secret_counts "$PWD/build/perf-results/dsl-k6"
MASTERSMITH_PERF_ENV_FILE=/dev/null docker compose -p mastersmith-perf -f docker/perf/compose.yaml \
  --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v
rm -rf "$D"; ls -d "$D" 2> /dev/null; docker volume ls --format '{{.Name}}' | grep -c '^mastersmith-perf_'   # 何も出ず、0 であること

# 5. 試しの環境（接続先に RETENTION_TIME=0 を足す。DEFRAG_ALWAYS=TRUE は保つ）を作り、利用者を入れて2回流す
KEEP=1 OUT_DIR="$PWD/build/perf-results/dsl-k6-retention" \
  PERF_DB_URL='jdbc:h2:file:/app/data/mastersmith;DEFRAG_ALWAYS=TRUE;RETENTION_TIME=0' ./perf/dsl-timing.sh postgres
D=<表示された一時ディレクトリ>
perf_users
pmset -g assertions | grep -E 'PreventUserIdleSystemSleep|PreventSystemSleep'
caffeinate -dims sh -c 'for r in retention-1 retention-2; do ./perf/dsl-mixed-memory.sh "$1" "$r" build/perf-results/dsl-k6-retention/postgres || exit $?; done' _ "$D"
pmset -g log | grep -E ' (Sleep|DarkWake) ' | tail -20
grep db_url build/perf-results/dsl-k6-retention/env.txt
cat build/perf-results/dsl-k6-retention/memory/retention-*/summary.txt build/perf-results/dsl-k6-retention/memory/retention-*/settings.txt   # RETENTION_TIME が 0 であること
secret_counts "$PWD/build/perf-results/dsl-k6-retention"
MASTERSMITH_PERF_ENV_FILE=/dev/null docker compose -p mastersmith-perf -f docker/perf/compose.yaml \
  --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v
rm -rf "$D"; ls -d "$D" 2> /dev/null; docker volume ls --format '{{.Name}}' | grep -c '^mastersmith-perf_'   # 何も出ず、0 であること

# 6. 配備したアプリを起動し直す（試験のタグのイメージは使わない）
docker compose start app && docker compose ps app
```

- 各回の WARN は `warn.txt` で数えてから片付ける（数え終わるまで `down -v` をしない。`project.md` の学び）。台本はアプリを止めて終わるが、コンテナは消さないため、`docker logs mastersmith-perf-app-1` も片付けの前なら読める。
- 試験用の利用者の行は `perf_users` の関数で入れる。内部DB の接続先は `app.env` の `MASTERSMITH_DB_URL` を道だけ `/data` に替えて使う（試しの環境でも同じ設定で開く）。
- `perf/dsl-mixed-memory.sh` は `--loop-only <コンテナの名前> <秒> <TSV の置き場>` で記録のループだけを流せる（Code Generation の空回しで、配備したアプリのコンテナに 5 秒向けて列を確かめた）。

### (c) ロールの3場面を手順書の順と単独で（FR7.2）

`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead` を、(1) ロールの管理の節の `role-run.sh` の順（`workRoleSwitch` の後）のまま、(2) 1場面ずつ単独で、の2通りに流す。合否は、どちらでも 3場面の `checks` の率が 1 で、setup が `ROLE_NO_CHANGE` や確かめの不一致で止まらないこと。

```bash
# (1) 手順書の順（B4・B5 の場面の全部。上の role-run.sh）
caffeinate -dims zsh "$D/role-run.sh" "$D"
# (2) 単独（同じ環境のまま、前の流しが残した割り当てと作業ロールの上で1場面ずつ）
cat > "$D/role-three.sh" <<'EOS'
D="$1"
for s in workRoleRead myPermissionsTree roleAssignmentsRead; do
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" -e SCENARIO=$s \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$s-alone.json /scripts/scenarios.js
done
EOS
caffeinate -dims zsh "$D/role-three.sh" "$D"
for s in workRoleRead myPermissionsTree roleAssignmentsRead; do
  grep -h -o '"checks": {[^}]*}' "build/perf-results/$s.json" "build/perf-results/$s-alone.json"   # どれも passes が 1 以上・fails が 0
done
```

## 業務のメニューの場面（Intent 261004-role-menu の U5）

`perf/k6/scenarios.js` の業務のメニューの場面を、使い捨ての環境で流す。目標の出典は `aidlc/spaces/default/intents/261004-role-menu/construction/navigation/` の `nfr-requirements/performance-requirements.md`（NFR2.2・NFR2.5）・`scalability-requirements.md`（NFR2.1）と `nfr-design/performance-design.md`（3節）。Code Generation（B7）では `k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめ、各場面を 1 VU・10 秒で試し走りしただけで、本番の長さ（10 VU・3 分）で流して判定するのは Performance Validation。

| 場面 | すること（1回の繰り返しに要求1つ） | 判定（閾値は緩めない） |
|---|---|---|
| `navPrepare` | 1 VU・1 回。悪い側の DSL（`DSL_FILE`）の投入と適用、グループ 100 個（`NAV_GROUP_COUNT`）の作成と試験用の利用者 `perf-nav-01`〜`10` 全員の所属、どのグループにもロール `perf-nav-worst` の割り当て、各利用者の作業ロールの保存（1人ずつログインする） | `checks{scenario:navPrepare}` の率 1 |
| `navMenu` | 業務のメニュー（`GET /api/me/navigation`、200 で項目が1つ以上）。10 VU・3 分 | `iteration_duration{scenario:navMenu}` の p95 < 1000 ms と `checks` の率 1 |
| `tableAccessVisible` | 読めるテーブル（番号が奇数の `t_001`・`t_003`…を VU と繰り返しの番号で回す）の置き場の問い合わせ（200 で `main` が `READ`）。10 VU・3 分 | 同上 |
| `tableAccessDenied` | `NONE` のテーブル（番号が偶数）の置き場の問い合わせ（403 `ACCESS_DENIED`。場面の始めに `http.setResponseCallback(http.expectedStatuses(403))` で期待の状態コードを宣言）。10 VU・3 分 | 同上 |
| `navMenuBaseline` | setup で目安の DSL（`DSL_FILE`）を適用し直した後の業務のメニュー（200）。10 VU・3 分 | 同上 |

- **データ**（使った規模は結果に記録する）:
  - 悪い側の DSL: `rolePrepare` の版 2 の DSL（スキーマ `large`、100 テーブル × 100 カラム）の最上位の `menus:` の塊を `perf/make-nav-dsl.mjs --worst` で置き換えたもの。menus 1,000 項目・深さ 5（最上位のまとまり 40 個 × 25 項目。鎖の項目もテーブルを指す）で、100 テーブルのそれぞれを、ちょうど 10 回ずつ別々のまとまりから指す。
  - 目安の DSL: 同じ DSL の `menus:` を `--baseline` で置き換えたもの（menus 100 項目・深さ 1、各項目がテーブルを1つ指す）。
  - ロール `perf-nav-worst`: `rolePrepare` の準備の YAML が入れる（計画の 13節 Q5: A）。1万カラムに READ と FULL を交互に明示し、テーブルの番号が偶数なら `NONE`・奇数なら `READ` を明示する（テーブルの半分が `NONE`）。スキーマには明示しない。準備の YAML のロールの合計（`ROLE_PREPARE_TOTAL`、既定 1,000）は変えず、埋めるロールを1つ減らした。
  - グループ・所属・割り当て・作業ロールの保存は `navPrepare` が API で行う（使い捨ての環境の監査に残る）。
- **準備の順**（基盤の設計の読み直しの R-02）:
  1. 手順 2'' と同じ SQL の形で、試験用の利用者 `perf-nav-01`〜`10`（管理者の印なし）と、操作する管理者 `perf-roleop01`（管理者の印あり）を入れる（下の 2N）
  2. 使い捨てのアプリを起動する
  3. `rolePrepare`（大きな DSL の適用と、`perf-nav-worst` を含む準備の YAML の確かめと適用）
  4. `navPrepare`（悪い側の DSL の適用、グループと所属、割り当て、1人ずつログインして作業ロールの保存）
- **流す順**（`cicd-pipeline.md` 5.3 との差。基盤の設計の読み直しの R-01 の手当てで、依頼者の指示）: 準備 → 大きな DSL の読み取りの場面（`navMenu`・`tableAccessVisible`・`tableAccessDenied` と、ロールの管理の節の B4・B5 の場面）→ `roleExport` → `roleTransferLargeCheck` → `roleTransferLargeApply` → **`navMenuBaseline` を最後**。`navMenuBaseline` は目安の DSL に入れ替えるため、その後は 1万カラムのロールの前提が崩れる。ロールの場面は `perf-role-worst`（スキーマ `large` の DSL）を使い、`navPrepare` の悪い側の DSL もスキーマとテーブルは同じため、ロールの場面の前提を崩さない。
- **p95 を場面ごとに別に残す**（NFR 設計の読み直しの R-08）: `tableAccessDenied` は表示名のための DSL の読み取り（`current()`）をしないため、`tableAccessVisible` と合わせずに場面ごとの p95 を記録する。置き場の p95 の実測値は基準の値として記録し、目標（1 秒）より桁違いに小さければ後の Intent で目標を見直すかを結果に書く（`performance-design.md` 3節）。
- **判定**: どの場面も `iteration_duration`（単調な時計）で判定し、`http_req_duration{scenario:…}` は colima の VM の時計のずれで崩れうるため並べて記録するだけ（閾値 `max>=0` は表示のため）。
- **VM の余裕**: 試し走り（B7、2026-10-12）は、配備したアプリ（上限 2g）を止めずに、使い捨てのアプリ（上限 2g）を並べて流した（VM 6 GiB のうち空きは約 4.9 GiB）。本番の長さの判定の回は、手順 0 のとおり配備したアプリを止めて CPU を取り合わないようにする。
- 台本の全体を `caffeinate -dims` で包む（場面ごとに起こし直さない）。

```bash
# 前提: 上の「手順」の 0・1 と同じ用意（一時の環境ファイル。置き場はホームの下（権限 700）。VM から見えるようにするため）。
#       生成した版 2 の DSL（rolePrepare と同じ。ロールの管理の節の B6 の手順で build/perf-results/role-data/dsl.yaml に置く）
# 2N. 使い捨ての環境を起動し、止めて、試験用の利用者と操作する管理者を入れる
perfu() { docker compose -p mastersmith-perf -f docker/perf/compose.yaml "$@"; }
docker info --format '{{.NCPU}} CPU / {{.MemTotal}} bytes'   # VM の余裕を読み取りで確かめる（アプリ 2g・k6）
perfu up -d --wait
perfu stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-nav-' || LPAD(CAST(X AS VARCHAR), 2, '0') || '@example.test', '$HASH', FALSE,
    DATEADD('SECOND', X, CURRENT_TIMESTAMP), 'perf-nav-' || LPAD(CAST(X AS VARCHAR), 2, '0') FROM SYSTEM_RANGE(1, 10);
INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  VALUES ('perf-roleop01@example.test', '$HASH', TRUE, CURRENT_TIMESTAMP, 'perf-roleop01')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfu up -d --wait

# 3N. 業務のメニューの DSL を作る（どちらも版 2 の DSL の menus: の塊だけを置き換える）
mkdir -p build/perf-results/nav-data
node perf/make-nav-dsl.mjs --worst build/perf-results/role-data/dsl.yaml build/perf-results/nav-data/worst.yaml
node perf/make-nav-dsl.mjs --baseline build/perf-results/role-data/dsl.yaml build/perf-results/nav-data/baseline.yaml

# 4N. 準備 → 読み取りの場面 → ロールの受け渡しの場面 → 目安の場面を最後に（台本の全体を caffeinate -dims で包む）
cat > "$D/nav-run.sh" <<'EOS'
D="$1"
k6r() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" "$@" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" \
    -v "$PWD/build/perf-results/role-data:/data:ro" -v "$PWD/build/perf-results/nav-data:/nav:ro" \
    grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/$name.json /scripts/scenarios.js
}
k6r rolePrepare -e SCENARIO=rolePrepare -e DSL_FILE=/data/dsl.yaml
k6r navPrepare -e SCENARIO=navPrepare -e DSL_FILE=/nav/worst.yaml
for s in navMenu tableAccessVisible tableAccessDenied; do k6r $s -e SCENARIO=$s; done
zsh "$D/role-run.sh" "$D"                                   # ロールの管理の節の B4・B5 の場面（流すときだけ）
k6r roleExport -e SCENARIO=roleExport
k6r roleTransferLargeCheck -e SCENARIO=roleTransferLargeCheck -e ROLE_YAML_FILE=/data/large-5mb.yaml
k6r roleTransferLargeApply -e SCENARIO=roleTransferLargeApply -e ROLE_YAML_FILE=/data/large-5mb.yaml
k6r navMenuBaseline -e SCENARIO=navMenuBaseline -e DSL_FILE=/nav/baseline.yaml   # 最後（DSL を目安に入れ替える）
EOS
caffeinate -dims zsh "$D/nav-run.sh" "$D"
# 試し走りのときは、各場面に -e VUS=1 -e ROLE_DURATION=10s を足す

# 5N. 秘密と名前が出ていないことを件数で確かめる（どれも 0）。片付けは上の「手順」の 4 と同じ
perfu logs app 2>&1 | grep -c 'perf-nav-[0-9]'
perfu logs app 2>&1 | grep '"level":"\(WARN\|ERROR\)"' | grep -c 't_0'
#   SELECT event_type, result, COUNT(*) FROM audit_events WHERE event_type LIKE 'ROLE_%' OR event_type = 'WORK_ROLE_SWITCHED'
#     OR event_type LIKE 'GROUP_%' GROUP BY event_type, result
```

- **試し走りの結果**（B7 の Code Generation、2026-10-12。1 VU・10 秒、判定ではない）: どの場面も閾値と checks を通った。`navPrepare` の DSL の適用は、対象DB に届かない照合の警告のまま通った（基盤の設計の読み直しの R-07）。繰り返しの時間の p95 は `navMenu` 29.4 ms・`tableAccessVisible` 3.3 ms・`tableAccessDenied` 3.3 ms・`navMenuBaseline` 25.1 ms。アプリのログに試験用の利用者のメールアドレスと、WARN・ERROR にテーブル名は出なかった（どちらも 0 件、ERROR は 0 行）。

## 業務データの場面（Intent 261016-business-data-views の U1）

`perf/k6/scenarios.js` の業務データの読み取りの場面を、使い捨ての環境で対象DB の種類ごとに流す。目標の出典は `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/` の `nfr-requirements/performance-requirements.md`（NFR1.1・NFR1.2・NFR1.3・NFR3.2）・`scalability-requirements.md`（NFR2.1・NFR2.4・NFR2.5）と、`infrastructure-design/infrastructure-specification.md`（2節）・`cicd-pipeline.md`（5.2・5.3）。Code Generation（B2）では `k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめ、PostgreSQL の1種類だけを既定の設定で試し走り（各場面 1 VU・10 秒）しただけで、本番の長さ（10 VU・3 分）で3種類を流して判定するのは Performance Validation。

| 場面 | すること（1回の繰り返しに要求1つ） | 判定（閾値は緩めない） |
|---|---|---|
| `bizPrepare` | 1 VU・1 回。DSL（`DSL_FILE`）の投入と適用、ロール `perf-biz-reader` の用意とスキーマ `bizperf` への READ の保存、試験用の利用者 `perf-biz-01`〜`10` への割り当て、1人ずつログインしての作業ロールの保存と3つの表の定義の確かめ。前の回が残したロールと割り当ても受け入れる | `checks{scenario:bizPrepare}` の率 1 |
| `bizDefinition` | 定義（`POST /api/tables/definition`、200 で一覧のカラム 100）。対象DB に接続しないため PostgreSQL の回だけで流す | `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と `checks` の率 1 |
| `bizSearchFirst` | `biz_item` の一覧の1ページ目（100 件・100 カラム・条件なし・既定の並び。全体の件数を含む） | 同上 |
| `bizSearchContains` | `c_010` の部分一致（目印の語。表の全部を読む形） | 同上 |
| `bizSearchLast` | 主キーの順の最後のページ（読み飛ばす行の数が多い形。ページの番号は setup が全体の件数から決める） | 同上 |
| `bizSearchLastSorted` | 索引の無い `c_001` で並べ替えた最後のページ（NFR 設計のレビュー R-05。計画の D2: A。悪い側の条件のまま測り、目標を超えたら Not Met として扱いを諮る） | 同上 |
| `bizDetail` | 主キーで1行の詳細（`POST /api/tables/rows/get`。VU と繰り返しの番号で行を回し、2,000,000 字の値の行も含む） | 同上 |
| `bizSearchWide` | `biz_wide` の1ページ目（100 件 × 100 カラム × 100 字、およそ 3 MB）。メモリの確かめの場面 | アプリのコンテナの `memory.peak` が上限 2g の 90% 未満（NFR2.5。`memory.stat` の `anon` の最大も並べる）と `checks` の率 1。応答時間は記録だけ |

- **データ**（値はすべて架空。使った規模は結果に記録する）:
  - 種は `perf/biz/make-biz-seed.sh <postgres|mysql|mariadb> [行の数]` が SQL を標準出力に出す（乱数を使わず、行の番号とカラムの番号だけから決まる）。スキーマ `bizperf`（MySQL・MariaDB ではデータベース）に、`biz_item`（主キー `id` と `c_001`〜`c_099` の 100 カラム、既定 100,000 行）・`biz_pair`（複合の主キー、1,000 行）・`biz_wide`（主キー `id` と 100 字を超える文字の `w_001`〜`w_099`、200 行）を作り、`mastersmith_reader` に読み取りの権限を与え、最後に表ごとの行の数を出す（NFR2.1 の記録）。
  - `biz_item` の文字の長い型は5本（`c_010`・`c_030`・`c_050`・`c_070`・`c_090`）で、150〜270 字。`c_010` の 10 行だけ 2,000,000 字（MySQL・MariaDB では `LONGTEXT`）。`(n + i) % 11 = 0` の値は NULL。並べ替えのカラム `c_001` には索引を作らない（主キーの索引だけ）。
  - DSL は `node perf/make-biz-dsl.mjs <postgres|mysql|mariadb> <出力>` で作る（書式の版 2。型の名前は既定の DSL の生成と同じ名前）。種と DSL の形は同じでなければならない（片方を変えるときは両方を変える）。
  - ロール・割り当て・作業ロールの保存は `bizPrepare` が API で行う（使い捨ての環境の監査に残る）。種のスキーマへの読み取りの権限は、種の SQL が対象DB の管理者で与える。
- **対象DB の設定とメモリの上限**（`infrastructure-specification.md` 2.1、計画の 4.2 の決定）: `docker/perf/compose.yaml` は次の変数を受ける。渡さなければ既定（今の値）のままで、`perf/dsl-timing.sh` は始めにこれらを `unset` してから測る。業務データの試験では次の値を export してから起動する。

| DB | 変数 | 既定 | 業務データの試験の値 |
|---|---|---|---|
| MySQL | `MASTERSMITH_PERF_TARGETDB_MYSQL_BUFFER_POOL`（`--innodb-buffer-pool-size`） | 128M | 768M |
| MySQL | `MASTERSMITH_PERF_TARGETDB_MYSQL_MEMORY`（`mem_limit`） | 768m | 2304m（U2 の試しの結果。U1 の設計の文書の 1792m とは違う） |
| MariaDB | `MASTERSMITH_PERF_TARGETDB_MARIADB_BUFFER_POOL` | 128M | 512M |
| MariaDB | `MASTERSMITH_PERF_TARGETDB_MARIADB_MEMORY` | 512m | 1g |
| PostgreSQL | `MASTERSMITH_PERF_TARGETDB_POSTGRES_SHARED_BUFFERS`（`-c shared_buffers`） | 128MB | 256MB |
| PostgreSQL | `MASTERSMITH_PERF_TARGETDB_POSTGRES_MEMORY` | 512m | 768m |

- **VM の余裕**（`infrastructure-specification.md` 2.3）: 業務データの試験の間は配備したアプリを止める（上の「手順」の 0 と同じ）。MySQL・MariaDB の回では手元の監視（lgtm）を動かさない。指標は、使い捨てのアプリにだけ公開する actuator の metrics（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`）で読む。MySQL（2304m）の回の上限の合計は perf のアプリ 2048 + MySQL 2304 + 見本の PG 512 + Mailpit 256 = 5,120 MiB（docker から見える量 約 5,910 MiB に収まる）。
- **流す順**（対象DB の種類ごと。`cicd-pipeline.md` 5.3）:
  1. 対象DB を、業務データの試験の値で起動する
  2. 種を入れ（`make-biz-seed.sh`）、`bizPrepare` を流す
  3. 表のデータと索引の大きさを測る。プール（PG は共有バッファー）の 80% を超えたら 128MiB ずつ上げ、メモリの上限も同じだけ上げて起動し直す
  4. 設定の実際の値（`@@innodb_buffer_pool_size`・`SHOW shared_buffers`）・コンテナのメモリの上限（`docker inspect` の Memory）・表の大きさを記録する
  5. 表を1回全部読んで暖める
  6. 合否の回を流す（待ちの最大のような揺れる値があるため、複数回流す）。場面ごとに、前後で対象DB のプール（`pool="mastersmith-target-db"`）の待ちの時間切れの累計と待ちの最大を読む（NFR2.4。合格は時間切れの累計の増分 0・503 の件数 0）。プールは最初に借りるときに作られるため、指標は最初の要求の後に読む
  7. `bizSearchWide` の前に、アプリのコンテナを作り直して前の `memory.peak` を消す
  8. 確かめの結果（待ちの時間切れの累計・`memory.peak`・OOMKilled・WARN の件数・ログに行の値と試験用の利用者のメールアドレスが無いこと）を見てから片付ける
- **借りる待ち**: 対象DB の読み取りのプールは上限 5・借りる待ち 3 秒（MySQL で接続の確かめが走るときは 6 秒）で、10 VU の場面は上限に必ず届く。待ちが 3 秒を超えると 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる。
- **判定**: どの場面も `iteration_duration`（単調な時計）で判定し、`http_req_duration{name:…}` は colima の VM の時計のずれで崩れうるため並べて記録するだけ（閾値 `max>=0` は表示のため）。
- 台本の全体を `caffeinate -dims` で包み、流した後に `pmset -g log` で眠りが無かったかを確かめる（上の「手順」の冒頭）。流した後は、export した変数を `unset` するか、別のシェルで流す（DSL の時間の測りに持ち込まないため。`perf/dsl-timing.sh` も始めに `unset` する）。

```bash
# 前提: 上の「手順」の 0・1 と同じ用意（配備したアプリを止め、WAR とイメージを用意し、一時の環境ファイル D を作る。
#       置き場はホームの下（権限 700）。VM から見えるようにするため）。KIND は postgres・mysql・mariadb のどれか
KIND=postgres
perfu() { docker compose -p mastersmith-perf -f docker/perf/compose.yaml "$@"; }
# 1B. 対象DB の資格情報（乱数。表示しない）と、アプリの対象DB の接続先（読み取りのアカウントと、U2 の書き込みのアカウント）を一時の
#     環境ファイルに書く。書き込みのパスワードが空だと、対象DB の初期化の台本 03-writer-account.sh が止まる（読み取りだけの回でも作る）
DBA=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24); DBR=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
DBW=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
( umask 077
  printf 'POSTGRES_PASSWORD=%s\nMYSQL_ROOT_PASSWORD=%s\nMARIADB_ROOT_PASSWORD=%s\nMASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD=%s\n' \
    "$DBA" "$DBA" "$DBA" "$DBR" > "$D/targetdb.env"
  printf 'MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=%s\n' "$DBW" >> "$D/targetdb.env"
  case "$KIND" in
    postgres) T=postgresql P=5432 DB=business ;;
    mysql) T=mysql P=3306 DB=bizperf ;;
    mariadb) T=mariadb P=3306 DB=bizperf ;;
  esac
  printf 'MASTERSMITH_TARGET_DB_TYPE=%s\nMASTERSMITH_TARGET_DB_HOST=targetdb-%s\nMASTERSMITH_TARGET_DB_PORT=%s\n' "$T" "$KIND" "$P" > "$D/app-targetdb.env"
  printf 'MASTERSMITH_TARGET_DB_DATABASE=%s\nMASTERSMITH_TARGET_DB_SCHEMA=bizperf\nMASTERSMITH_TARGET_DB_USERNAME=mastersmith_reader\n' "$DB" >> "$D/app-targetdb.env"
  printf 'MASTERSMITH_TARGET_DB_PASSWORD=%s\n' "$DBR" >> "$D/app-targetdb.env"
  printf 'MASTERSMITH_TARGET_DB_WRITE_USERNAME=mastersmith_writer\nMASTERSMITH_TARGET_DB_WRITE_PASSWORD=%s\n' "$DBW" >> "$D/app-targetdb.env"
  printf 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics\n' >> "$D/app.env" )
unset DBA DBR DBW
export MASTERSMITH_PERF_TARGETDB_ENV_FILE="$D/targetdb.env" MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE="$D/app-targetdb.env"
# 業務データの試験の値（試し走りでは export しない。計画の D4: A）
export MASTERSMITH_PERF_TARGETDB_MYSQL_BUFFER_POOL=768M MASTERSMITH_PERF_TARGETDB_MYSQL_MEMORY=2304m \
  MASTERSMITH_PERF_TARGETDB_MARIADB_BUFFER_POOL=512M MASTERSMITH_PERF_TARGETDB_MARIADB_MEMORY=1g \
  MASTERSMITH_PERF_TARGETDB_POSTGRES_SHARED_BUFFERS=256MB MASTERSMITH_PERF_TARGETDB_POSTGRES_MEMORY=768m

# 2B. 対象DB を起動して種を入れる（100,000 行で数分。行の数は最後の行に出る）
perfu --profile "targetdb-$KIND" up -d --wait "targetdb-$KIND"
case "$KIND" in
  postgres) client=(psql -q -v ON_ERROR_STOP=1 -U target_admin -d business) ;;
  mysql) client=(sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root') ;;
  mariadb) client=(sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --user=root') ;;
esac
./perf/biz/make-biz-seed.sh "$KIND" | perfu exec -T "targetdb-$KIND" "${client[@]}"

# 3B. アプリを起動し、止めて、試験用の利用者 perf-biz-01〜10 と操作する管理者 perf-roleop01 を入れる（上の「手順」の 2 と同じ H2 の道具）
perfu up -d --wait app
perfu stop app
HASH=$(htpasswd -nbBC 12 x "$UP" | cut -d: -f2)
SQL="INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  SELECT 'perf-biz-' || LPAD(CAST(X AS VARCHAR), 2, '0') || '@example.test', '$HASH', FALSE,
    DATEADD('SECOND', X, CURRENT_TIMESTAMP), 'perf-biz-' || LPAD(CAST(X AS VARCHAR), 2, '0') FROM SYSTEM_RANGE(1, 10);
INSERT INTO users (email, password_hash, admin_flag, created_at, display_name)
  VALUES ('perf-roleop01@example.test', '$HASH', TRUE, CURRENT_TIMESTAMP, 'perf-roleop01')"
cp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.4.240/*/h2-2.4.240.jar build/h2-perf.jar
docker run --rm -u 10001:10001 -v mastersmith-perf_perf-data:/data -v "$PWD/build/h2-perf.jar:/h2.jar:ro" \
  eclipse-temurin:25.0.4.1_1-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab java -cp /h2.jar org.h2.tools.Shell -url jdbc:h2:file:/data/mastersmith -user sa -password "" -sql "$SQL" > /dev/null
rm build/h2-perf.jar; unset AP UP HASH SQL
perfu up -d --wait app

# 4B. DSL を作り、準備 → 表の大きさの記録 → 暖め → 場面（台本の全体を caffeinate -dims で包む）
mkdir -p build/perf-results/biz-data && chmod 777 build/perf-results
node perf/make-biz-dsl.mjs "$KIND" build/perf-results/biz-data/dsl.yaml
cat > "$D/biz-run.sh" <<'EOR'
D="$1"; KIND="$2"
k6r() {   # 引数: 結果の名前 と k6 に渡す -e の組
  local name="$1"; shift
  docker run --rm --network mastersmith-perf_default --env-file "$D/k6.env" "$@" \
    -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" -v "$PWD/build/perf-results/biz-data:/data:ro" \
    grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
    run --quiet --summary-export=/out/biz-$KIND-$name.json /scripts/scenarios.js
}
k6r bizPrepare -e SCENARIO=bizPrepare -e DSL_FILE=/data/dsl.yaml
# （ここで 5.3 の 3〜5: 表の大きさの実測と 80% の決まり、設定の実際の値の記録、暖め）
[ "$KIND" = postgres ] && k6r bizDefinition -e SCENARIO=bizDefinition
for s in bizSearchFirst bizSearchContains bizSearchLast bizSearchLastSorted bizDetail; do k6r $s -e SCENARIO=$s; done
# bizSearchWide の前にアプリのコンテナを作り直して前の memory.peak を消し、流した後に memory.peak・memory.stat を読む。
# 作り直すと前のコンテナのログが消えるため、先にログをリポジトリの外（$D）に残す（6B の件数の確かめに使う。表示しない）
( umask 077; docker compose -p mastersmith-perf -f docker/perf/compose.yaml logs --no-log-prefix app > "$D/app-before-wide.log" 2>&1 )
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait --force-recreate app
k6r bizSearchWide -e SCENARIO=bizSearchWide
EOR
caffeinate -dims zsh "$D/biz-run.sh" "$D" "$KIND"
# 試し走りのときは、k6r の各場面に -e VUS=1 -e BIZ_DURATION=10s を足す

# 5B. 表の大きさ（データと索引。PG の例。MySQL・MariaDB は information_schema.TABLES の DATA_LENGTH・INDEX_LENGTH）
perfu exec -T targetdb-postgres psql -U target_admin -d business -c \
  "SELECT relname, pg_size_pretty(pg_table_size(oid)), pg_size_pretty(pg_indexes_size(oid)) FROM pg_class WHERE relnamespace = 'bizperf'::regnamespace AND relkind = 'r'"

# 6B. 秘密・行の値・名前が出ていないことを件数で確かめる（どれも 0）。確かめの結果を見てから片付ける（上の「手順」の 4 と同じだが、
#     対象DB も消すため perfu --profile "targetdb-$KIND" down -v にする）。export した変数も外す
perfu logs --no-log-prefix app > "$D/app-after-wide.log" 2>&1
for f in "$D/app-before-wide.log" "$D/app-after-wide.log"; do
  echo "$(basename "$f"): 利用者 $(grep -c 'perf-biz-[0-9]' "$f")・行の値 $(grep -c -e '目印' -e 'いろはにほへと' -e '幅[0-9]' "$f")・WARN $(grep -c '"level":"WARN"' "$f")・ERROR $(grep -c '"level":"ERROR"' "$f")"
done
#   SELECT event_type, result, COUNT(*) FROM audit_events WHERE event_type LIKE 'ROLE_%' OR event_type = 'WORK_ROLE_SWITCHED'
#     GROUP BY event_type, result
unset MASTERSMITH_PERF_TARGETDB_MYSQL_BUFFER_POOL MASTERSMITH_PERF_TARGETDB_MYSQL_MEMORY MASTERSMITH_PERF_TARGETDB_MARIADB_BUFFER_POOL \
  MASTERSMITH_PERF_TARGETDB_MARIADB_MEMORY MASTERSMITH_PERF_TARGETDB_POSTGRES_SHARED_BUFFERS MASTERSMITH_PERF_TARGETDB_POSTGRES_MEMORY
```

- **試し走りの結果**（B2 の Code Generation、2026-10-19。PostgreSQL の1種類、既定の設定（`shared_buffers` 128MB・上限 512m、業務データの試験の値は export しない）、配備したアプリを止めずに、各場面 1 VU・10 秒。判定ではない）: 2回流し、2回ともどの場面も閾値と checks を通った（checks の失敗 0、`http_req_failed` 0）。種の SQL は 7 秒で、行の数は `biz_item` 100,000・`biz_pair` 1,000・`biz_wide` 200、表の大きさは `biz_item` 196 MiB（索引 2.2 MiB）・`biz_wide` 4.3 MiB。2回目の繰り返しの時間の p95 は `bizDefinition` 2.1 ms・`bizSearchFirst` 24.1 ms・`bizSearchContains` 162.7 ms（1回目 272.1 ms）・`bizSearchLast` 13.1 ms・`bizSearchLastSorted` 47.9 ms・`bizDetail` 24.6 ms・`bizSearchWide` 48.2 ms。1回目に対象DB のプールの待ちの時間切れの累計は 0。2回目は作り直す前と後のログをどちらも残して数え、試験用の利用者のメールアドレスと行の値は 0 件、WARN は起動ごとの2行（Flyway の H2 の版、Bean の後処理の案内）だけ、ERROR は 0 行だった（1回目は作り直す前のログを残しておらず、その反省で上の台本は作り直す前にログを残す形にした）。`bizSearchWide` の後の `memory.peak` は 1回目 621 MiB・2回目 506 MiB（1 VU、OOMKilled なし）。MySQL・MariaDB の種の SQL は、出した SQL の形と行の数の指定（100,000・1,000・200）を読んで確かめただけで、流すのは Performance Validation（計画の D4: A）。

## 業務データの書き込みの場面（Intent 261016-business-data-views の U2）

`perf/k6/scenarios.js` の業務データの書き込みの場面を、上の「業務データの場面」と同じ使い捨ての環境で、読み取りの場面の後に対象DB の種類ごとに流す。目標の出典は `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/` の `nfr-requirements/performance-requirements.md`（NFR1.4・NFR1.5）・`scalability-requirements.md`（NFR2.7・NFR2.8・NFR2.9）と、`infrastructure-design/cicd-pipeline.md`（5節）・`monitoring-design.md`。Code Generation（B4）では `k6 inspect --include-system-env-vars` で場面と閾値の名前を確かめ、PostgreSQL の1種類だけを既定の設定で試し走り（各場面 1 VU・10 秒、`bizCreateLarge` を含む）しただけで、本番の長さで3種類を流して判定するのは Performance Validation。

| 場面 | すること（1回の繰り返しに要求1つ） | 判定（閾値は緩めない） |
|---|---|---|
| `bizCreate` | `biz_new` の作成（100 カラムの表の FULL のカラム。一意の `u_code` と参照先にある `ref_id`）。10 VU・3 分 | `iteration_duration{scenario:<名前>}` の p95 < 1000 ms と `checks` の率 1 |
| `bizUpdate` | 更新（VU ごとに setup で作った別の行の `n_001` を変える。同じ行の排他の待ちを混ぜない）。10 VU・3 分 | 同上 |
| `bizDelete` | 削除（setup で `ITERATIONS` 件（既定 100）の行を作り、1回に1行を消す） | 同上 |
| `bizWritePoolLimit` | 参照先に無い `ref_id` での作成（外部キーの違反 → セーブポイントへの戻し → 同じ接続での確かめ。最も長く接続を持つ道）。10 VU で書き込みのプールの上限 5 に必ず届く。409 `BUSINESS_REFERENCE_NOT_FOUND` を正しい答えとして数える（期待の状態コード）。10 VU・3 分 | 同上（NFR2.7） |
| `bizCreateLarge` | 4 MiB 近い本文の作成（97 カラム × 10,000 字の 4 バイトの架空の字、約 3.9 MB）。10 VU × `BIZ_LARGE_ITERATIONS` 回（既定 3） | アプリのコンテナの `memory.peak` が上限 2g の 90% 未満（NFR2.8。`anon` の最大も並べる）と `checks` の率 1。応答時間は記録だけ |

- **データ**: 種の台本（`make-biz-seed.sh`）が、`biz_ref`（参照先、100 行）と `biz_new`（自動の採番の主キー `id`・一意の制約のある `u_code`・`biz_ref` を指す外部キー `ref_id`・文字の `n_001`〜`n_097` の 100 カラム、行は入れない）を作り、書き込みのアカウント `mastersmith_writer`（対象DB の初期化の台本 `03-writer-account.sh` が作る）に `bizperf` の行の操作（PG は sequence の USAGE も）を与える。DSL（`make-biz-dsl.mjs`）にも同じ形の2つの表を書く。値はすべて架空。
- **setup の前提**（前の場面の状態に頼らない）: どの書き込みの場面も、setup で書き込みのロール `perf-biz-writer`（スキーマ `bizperf` を READ、`biz_new`・`biz_ref` を FULL と CREATE・DELETE）を用意して試験用の利用者 `perf-biz-01`〜に割り当て、1人ずつログインして作業ロールを保存し、`biz_new` の定義で書き込みが使える（`writeConfigured` と CREATE・UPDATE・DELETE）ことを確かめる。DSL の適用は `bizPrepare` が行う（先に流す）。`bizUpdate` は VU ごとに1行、`bizDelete` は `ITERATIONS` 件の行を、setup が API で作る。どれも使い捨ての環境の監査に残る。
- **アプリの書き込みの設定**: 上の 1B のとおり、アプリの一時の環境ファイルに `MASTERSMITH_TARGET_DB_WRITE_USERNAME=mastersmith_writer` と `MASTERSMITH_TARGET_DB_WRITE_PASSWORD`（対象DB の一時の環境ファイルの `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` と同じ値）を入れる。値は表示しない。
- **流す順**（`cicd-pipeline.md` 5節）: 上の「業務データの場面」の 4B の台本の後（読み取りの場面の後）に、`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` を流す。待ちの最大のような揺れる値があるため、合否の回は複数回流す。`bizCreateLarge` の前に、アプリのコンテナを作り直して前の `memory.peak` を消す（作り直す前にログを `$D` に残す）。MySQL の回のメモリの上限は 2304m（業務データの試験の値。上の表）のまま。
- **片付ける前に見るもの**（確かめの結果を見てから片付ける。NFR2.9）: 書き込みのプール（`pool="mastersmith-target-db-write"`。最初に借りるまで出ないため、書き込みを1回流した後に読む）の待ちの時間切れの累計と待ちの最大、アプリの `memory.peak`、対象DB のコンテナの OOMKilled と `memory.events` の `oom_kill`・`max`・`memory.stat` の `anon` の最大、WARN の件数、監査の行の数（`event_type LIKE 'BUSINESS_%'` の種類と結果ごと）、内部DB のファイルの大きさ。対象DB のメモリは「OOMKilled が false、かつ `oom_kill` が 0」で判断し、`anon` の最大が上限の 85% を超えたら依頼者に諮る（基盤の設計のレビューの R-04）。k6 のコンテナの使用量（`docker stats` の CPU・メモリ）も記録する（大きな本文の場面は k6 の側のメモリも使う）。
- **本文の上限**: 作成・更新の本文の上限は 4 MiB（4,194,304 バイト）。k6 の `JSON.stringify` は日本語や 4 バイトの字を `\u` でエスケープせずに送るため、`bizCreateLarge` の本文は約 3.9 MB に収まる。字を `\uXXXX` でエスケープする作り方の送り手では、4 バイトの字が 12 バイトになり、同じ値でも 413 `PAYLOAD_TOO_LARGE` になる（NFR 設計のレビューの R-09）。

```bash
# 前提: 上の「業務データの場面」の 1B〜4B を流した後（bizPrepare と読み取りの場面が済んでいる）。k6r と D・KIND も同じ
for s in bizCreate bizUpdate bizDelete bizWritePoolLimit; do k6r $s -e SCENARIO=$s; done
( umask 077; docker compose -p mastersmith-perf -f docker/perf/compose.yaml logs --no-log-prefix app > "$D/app-before-large.log" 2>&1 )
docker compose -p mastersmith-perf -f docker/perf/compose.yaml up -d --wait --force-recreate app
k6r bizCreateLarge -e SCENARIO=bizCreateLarge
# 試し走りのときは、k6r の各場面に -e VUS=1 -e BIZ_DURATION=10s を足す（bizDelete は -e ITERATIONS=10、bizCreateLarge は
# -e BIZ_LARGE_ITERATIONS=1。試験用の利用者は perf-biz-01 だけが要る）
# 書き込みのプールの指標（使い捨てのアプリにだけ公開した actuator の metrics。値は数だけ）
curl -s "http://127.0.0.1:18080/actuator/metrics/hikaricp.connections.timeout?tag=pool:mastersmith-target-db-write"
#   監査の行の数（内部DB の複写を読み取りで開いて数える。上の「手順」の 4 と同じ）
#   SELECT event_type, result, failure_reason, COUNT(*) FROM audit_events WHERE event_type LIKE 'BUSINESS_%'
#     GROUP BY event_type, result, failure_reason
```
