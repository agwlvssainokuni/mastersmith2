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
