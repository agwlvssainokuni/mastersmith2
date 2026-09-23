# 負荷の試験（k6）

使い捨ての環境（`docker/perf/compose.yaml`）に k6 で負荷をかけ、性能の目標（95 パーセンタイル）を確かめる。配備した環境（`compose.yaml`）のデータと監査ログには触れない。計画と結果は `aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/` にある。

## 手順

```bash
# 0. 配備したアプリを止め（資源を取り合わないため）、WAR とイメージを用意する
docker compose stop app
./gradlew :backend:bootWar && docker compose build app

# 1. リポジトリの外に一時の環境ファイルを作る（値は乱数。表示しない）
D=$(mktemp -d) && chmod 700 "$D"
AP=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24); UP=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
( umask 077
  printf 'MASTERSMITH_CONTAINER_CPUS=2\nMASTERSMITH_AUTH_SIGNING_KEY=%s\nMASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL=perf-admin@example.test\nMASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 32)" "$AP" > "$D/app.env"
  printf 'PERF_ADMIN_EMAIL=perf-admin@example.test\nPERF_ADMIN_PASSWORD=%s\nPERF_USER_PASSWORD=%s\n' "$AP" "$UP" > "$D/k6.env" )
export MASTERSMITH_PERF_ENV_FILE="$D/app.env" MASTERSMITH_CONTAINER_CPUS=2

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
- `refresh` の場面は、CPU の上限 2・メモリ 1GB の設定で 30〜40 秒ほどでコンテナがメモリの上限で止まる（2026-09-23 の試験の結果）。
