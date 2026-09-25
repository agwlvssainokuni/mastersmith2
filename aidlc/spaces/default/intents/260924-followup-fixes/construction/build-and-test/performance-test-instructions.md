# 性能・負荷の試験の手順（performance-test-instructions）

要件 FR8.2（行が無い試験用の利用者のまま同時のログインを流し、500 が 0 件）と、FR3.1 の「流したときの記録」（ログインの VU の間で利用者が重ならない。レビューの R-01）を、この段で使い捨ての環境に対して確かめる。この Intent の流れには Performance Validation の段が無いため、持ち主はこの段である（project.md の Testing Posture の学び、要件 FR8.2）。あわせて FR4.1 の起動のログ（Hibernate の案内が1行1件）を、使い捨てのアプリの起動のログで確かめる。

## 1. 道具と環境

- k6 `grafana/k6:2.3.0` の `dslMixed`（10MB の DSL の投入とプレビューの表示（1人）に、別々の利用者 10 名のログインを重ねる）。台本は `perf/k6/scenarios.js`、手順は `perf/README.md`。
- 使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト名 `mastersmith-perf`、対象DB は PostgreSQL の1種類）。仮の署名鍵・仮の管理者・仮の利用者を乱数で作り、終わったら `down -v` と一時ディレクトリの削除で消す。配備したアプリ・その内部DB・監査ログには要求を送らない。
- イメージは `mastersmith:followup-fixes`（依頼者の判断。配備の `mastersmith:local` は上書きしない）。
- コンテナの上限は配備と同じ CPU 4・メモリ 2g（`MASTERSMITH_CONTAINER_MEMORY` の既定）。
- 測るあいだ、配備したアプリは `docker compose stop app` で止め、終わったら `docker compose start app` で同じコンテナを起動し直す（依頼者の判断）。見本の対象DB（`mastersmith-targetdb-postgres-1`）は止めない。
- 長い実行は `caffeinate -i` を付ける（project.md の学び）。

## 2. 手順

```bash
# 0. イメージを作る（配備のタグは上書きしない）
./gradlew :backend:bootWar && docker build -t mastersmith:followup-fixes .

# 1. 配備したアプリを止め、使い捨ての環境（PostgreSQL と 100×100 のスキーマ、生成した DSL と 10MB の DSL）を残して起動する
docker compose stop app
KEEP=1 MASTERSMITH_IMAGE_TAG=followup-fixes OUT_DIR="$PWD/build/perf-results/followup" caffeinate -i ./perf/dsl-timing.sh postgres
D=<表示された一時ディレクトリ>

# 2. アプリを止め、試験用の利用者 11 名（perf-user01〜11）を内部DB に SQL で入れる（ロックの状態の行は作らない）。
#    PERF_USER_PASSWORD を ui.env に足す（値は表示しない）。アプリを起動し直す
#    （perf/README.md の「手順」2 と同じ入れ方。使うプロジェクト名とボリュームは mastersmith-perf）

# 3. dslMixed を流す（重い側1人＋ログイン同時 10、120 秒）。出力を残す
docker run --rm --network mastersmith-perf_default --env-file "$D/ui.env" -e SCENARIO=dslMixed -e VUS=10 -e DURATION=120s \
  -e DSL_FILE=/data/<10MB の DSL> -v "$PWD/build/perf-results/followup/postgres:/data:ro" \
  -v "$PWD/perf/k6:/scripts:ro" -v "$PWD/build/perf-results:/out" grafana/k6:2.3.0 \
  run --summary-export=/out/followup-dslMixed.json /scripts/scenarios.js 2>&1 | tee build/perf-results/followup-dslMixed.console.txt

# 4. 利用者の重なりを確かめる（R-01）
grep -o 'loginLoop-user vu=[0-9]* user=[^ "]*' build/perf-results/followup-dslMixed.console.txt | wc -l      # 10 を期待
grep -o 'loginLoop-user vu=[0-9]* user=[^ "]*' build/perf-results/followup-dslMixed.console.txt | sed 's/.* user=//' | sort | uniq -d   # 何も出ないことを期待

# 5. 状態とログを記録する（OOMKilled、アプリのログの ERROR と 500、起動のログの Hibernate の案内）
docker inspect mastersmith-perf-app-1 --format '{{.State.OOMKilled}} {{.State.ExitCode}}'

# 6. 片付けと、配備したアプリの起動し直し
MASTERSMITH_PERF_ENV_FILE=/dev/null docker compose -p mastersmith-perf -f docker/perf/compose.yaml \
  --profile targetdb-postgres --profile targetdb-mysql --profile targetdb-mariadb down -v
rm -rf "$D"
docker compose start app   # healthy になるまで待つ
```

## 3. 合格の条件

| 目標 | 合格の条件 | 要件 |
|---|---|---|
| 同時の初めてのログインで 500 にならない | `dslMixed` のログインの `checks` の率が 1（失敗 0 件）。アプリのログに 500（`INTERNAL_ERROR`）と主キーの重複の ERROR が無い | FR8.2、FR2.1 |
| ログインの VU の間で利用者が重ならない | `loginLoop-user` の行の数が `VUS`（10）と同じで、利用者の重複が 0 件 | FR3.1（R-01） |
| 重い側も失敗しない | `dslMixed` の重い側の `checks` の率が 1、コンテナが止まらない（OOMKilled false） | NFR6（既存の動作の維持） |
| 起動の案内が1行1件 | 使い捨てのアプリの起動のログで、ロガー `org.hibernate.orm.connections.pooling` の行の `message` の値に改行が無く、` ⏎ ` で区切られている | FR4.1 |

- 性能の数値（p95 など）はこの Intent の目標ではない。結果は記録として残すが、判定には使わない。
- アプリのログの本文と秘密の値は表示しない（件数と、ロガー名・状態コードの集計だけを見る）。
