## Developer Code Scan Results

**Collaborator:** developer-agent

対象 Intent: 261027-perf-targets-review（bugfix・Minimal）。読み取り専用の Focused scan。git・gradlew・docker は実行していない。

### Scan Coverage

**Deep（スナップショットの paths の内側のみ）**
- `perf/`（README.md 1320 行、k6/scenarios.js 2595 行、dsl-mixed-memory.sh、versions.env、biz/、lib/ ほか）
- `docker/perf/`（compose.yaml）
- `backend/src/main/java/cherry/mastersmith/businessdata/`（domain・repository・service・web）
- `backend/src/main/java/cherry/mastersmith/targetdb/`（config・domain・repository・service）
- `backend/build.gradle.kts`、`gradle/libs.versions.toml`、`.github/dependabot.yml`、`frontend/package.json`、`README.md`（README.md は性能の箇所を見ていない。ファイル名だけ把握）

**Shallow（analyzed.paths に入れない）**
- 既存コードベース知識（`aidlc/spaces/default/codekb/mastersmith2/` の9成果物、STALE）の参照
- `backend/src/main/java` のうち上記以外のパッケージ、`frontend/src`、`compose.yaml`、`.github/workflows`

**部品 ID（deep）**: `perf-and-monitoring`、`businessdata`、`targetdb`、`build-and-verify`

### 部品の役割とパッケージ

- `businessdata`（`web`・`service`・`domain`・`repository`）: 業務データの一覧・詳細・書き込み。`repository` に SQL を閉じ込めた部品が集まる。`RowSqlBuilder`・`RowSqlExecutor`・`RowWriteSqlBuilder`・`RowSqlFailureClassifier`・`ColumnValueCodec`、DB 種類別の `RowDialect` 実装（`MysqlFamilyRowDialect`・`PostgresRowDialect`）、`Row*Result` 群。
- `targetdb`（`config`・`domain`・`repository`・`service`）: 対象DB（MySQL・MariaDB・PostgreSQL）の接続と読み取り。
- 2つのパッケージ合わせて Java ファイル 156 件。

### k6 の合否の線の定義場所

- `perf/k6/scenarios.js` の `thresholdsFor(name)`（約 522 行〜）が全場面の閾値を持つ。
- 業務データの読み取り場面は `BIZ_SCENARIOS` 分岐（約 579〜586 行）。次の 3 条件で判定する。
  - `iteration_duration{scenario:<name>}` の `p(95)<1000`
  - `checks{scenario:<name>}` の `rate==1`
  - `http_req_duration{name:<name>}` は `max>=0`（記録のみ）
- `bizSearchWide` だけは応答時間を記録のみ（`max>=0`）。
- 対象の場面関数の位置: `bizSearchContains`（約 2280 行）、`bizSearchLast`（2289）、`bizSearchLastSorted`（2297）。
- 書き込み場面は `BIZ_WRITE_JUDGED` に含まれるものだけ `p(95)<1000`。
- `dslMixed` は 552 行付近の分岐。memory.peak の判定は k6 の閾値ではなく手順側（README と `perf/dsl-mixed-memory.sh`）にある。
- 場面ごとの共通の物差しは `iteration_duration`（単調な時計）。VM の時計のずれを避けるため、`http_req_duration` は並べて記録するだけにしている（project.md の学び）。

### perf/README.md の memory.peak・anon の記述箇所

- 119 行付近: コンテナのメモリの最大は cgroup の `memory.peak`（起動からの最大）で読む。
- 151 行: `--storage` の `memory.current` はページキャッシュを含む。止まるかどうかは `anon` と `state.txt`（OOMKilled）で見る。
- 860〜865 行（FR9.4 の節）: 合否は「OOM なし・`OOMKilled` が false・`dslMixed` の `memory.peak` が上限の 90% 未満」。読み方は `docker exec … cat /sys/fs/cgroup/memory.peak`。
- 931〜935 行: `memory.peak / memory.max` が 0.9 未満であること。
- 942〜946 行（Intent 261016-business-data-views の U5）: 合否は `memory.peak / memory.max < 0.9` を既定の2回で確認する。物差しは `anon` に替えない（team.md の Testing Posture）。ページキャッシュを減らす試し（H2 の `RETENTION_TIME=0`）は合否に使わない。
- `perf/dsl-mixed-memory.sh`（約 16〜190 行）が内訳を記録する。`memory.stat` の `anon`・`file`・`inactive_file`・`active_file`・`kernel` と、`anon_max_ratio` を並べ、合否は peak/max だけにしている。
- 物差しの変更は、team.md により要件定義で依頼者が明示に決めたときだけ。変えた後も1回は前の物差しの値を並べて記録する。
- 直近の事実（project.md の Interpretations/Deviations）: `dslMixed` の `memory.peak` は 94.4% と 93.1%（Not Met）。内訳は anon 68.3%・回収できるページキャッシュ 23.7%。依頼者のメモリ上の決定として、p95・memory.peak・anon の緩和が 2026-10-28 に決まっている（MEMORY.md）。次の要件定義で明示して記録する必要がある。

### docker/perf/compose.yaml の対象DB の資源上限（既定値）

| サービス | イメージ | mem_limit の既定 | 備考 |
|---|---|---|---|
| `app` | `mastersmith:${MASTERSMITH_IMAGE_TAG:-local}` | 2g、cpus 4 | `MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS` |
| `targetdb-postgres` | postgres:18.6（ダイジェスト固定） | 512m | `shared_buffers` 既定 128MB |
| `targetdb-mysql` | mysql:26.10.0（ダイジェスト固定） | 768m | `innodb-buffer-pool-size` 既定 128M |
| `targetdb-mariadb` | mariadb:13.0.2（ダイジェスト固定） | 512m | バッファープール既定 128M |
| `mailpit` | axllent/mailpit:v1.32.0 | 256m | 固定 |

- README の記述（perf/README.md 1217 行付近、project.md）では、業務データの試験時に上限を環境変数で引き上げる運用がある。実績: MySQL は 2304m、プール 1280M、上限 2688m まで調整。compose の既定と README の値が異なるため、「どれが正か」を要件定義で決める必要がある。
- 対象DB の anon の最大は MariaDB 85.01%、MySQL 90.3%（OOMKilled false）。

### 性能目標の Not Met の構造（コードから確かめた範囲）

- `bizSearchContains`: 部分一致は索引が効かず、1回の検索で件数とページの2問い合わせが表を2回全読みする（README 1178 行）。読み取りプールの借りる待ち 3 秒（MySQL では確かめで 6 秒）を超えると 503 `BUSINESS_TARGET_DB_UNAVAILABLE` になる。
- `bizSearchLast`: 深い OFFSET で読み飛ばし行が多い（MariaDB で Not Met 傾向）。
- `bizSearchLastSorted`: 索引の無い `c_001` で並べ替えた最後のページ。悪い側の条件のまま測り、超えたら Not Met とする方針（D2: A）。
- 読み取りプールは上限 10 に引き上げ済み（Intent 261025 で 5→10）。
- 流す順の見直し案（README 1303 行）: 書き込みの後の読み取りは悪い側に寄る。

### Dependabot の設定（.github/dependabot.yml）

- エコシステム: gradle・npm（/frontend）・github-actions・docker・docker-compose（いずれも weekly）。
- gradle の `exclude-paths: vendor/**`（composite build のサブモジュールの依存を更新できないため）。
- gradle の `ignore`: `opentelemetry-logback-appender-1.0`（全更新）と `com.networknt:json-schema-validator`（全更新）。外す時期はコメントに書かれている。
- npm の `ignore`: `typescript` の semver-major だけ。axe-core は ignore に入れず、更新時は E2E 全件と vitest-axe を流す例外手順がある。
- docker の `ignore`: `eclipse-temurin` の semver-major。
- `groups` は使われていない。`open-pull-requests-limit: 10`。
- team.md の決まり: Dependabot の PR は GitHub 画面でマージせず、手元で版と lockfile を更新して `./gradlew verify` を通してから `develop` に統合し、PR は閉じる。重大度 High 以上は次の Bolt の前に取り込む。
- 開いている PR の洗い出しは、この読み取り専用の調査では実行できない（`gh` を使っていない）。後の段で確かめる。
- Dependabot が見ない固定先: `docker/perf/compose.yaml`、`TargetDbImages.java`、`perf/versions.env`（k6・JRE）、`perf/README.md`、`README.md`、`docker/hikari-pool.sh`。

### ビルド・設定・指標

- `gradle/libs.versions.toml`: Java 25、Spring Boot 4.1.2、jacoco 0.8.15、spotbugs-tool 4.10.4、findsecbugs 1.14.0、snakeyaml 2.7、networknt 3.0.6（固定）、opentelemetry-instrumentation 2.28.1-alpha（固定）、subethasmtp 7.2.2。
- `backend/build.gradle.kts`: jacoco の全体の下限は行 80%・分岐 70%（233〜253 行）。`packagesJudgedByTotal` は 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）で、team.md の記述と一致する。SpotBugs はゲート（`spotbugsGate`）で止める。
- `frontend/package.json`: react ^19.2.8、vite ^8.3.5、vitest ^5.0.5、typescript ^6.0.3、axe-core ^4.14.0。
- この Intent は性能の物差し・perf 文書・Dependabot の洗い出しが中心で、`backend/src/main` の変更は想定しにくい。`common.health` などの `packagesJudgedByTotal` に手を入れる見込みは今回は低い。

### 技術的負債の兆候

- `perf/README.md` が 1320 行、`perf/k6/scenarios.js` が 2595 行と大きい。Intent ごとの追記で、どの値が現行の基準か読み取りにくい（例: 同じ指標に前の Intent の結果と現行の判定が混在）。
- 値の二重管理: compose の既定（対象DB mem_limit）と README の運用値（2304m など）が食い違う。env 変数で上書きする運用のため、文書が正になっている。
- 場面の閾値は `p(95)<1000` が広く共通。DB 種類別の線は無い（MySQL・MariaDB の部分一致は同じ線で判定される）。
- 読み取り専用の調査のため、実測値や PR の状態は確かめていない。上記の Not Met 数値は project.md の記録に基づく。

## Handoff Summary

- deep の範囲は指定された paths の内側のみ。部品 ID は `perf-and-monitoring`・`businessdata`・`targetdb`・`build-and-verify`。
- 合否の線: k6 の `thresholdsFor` の BIZ 分岐（`p(95)<1000` と checks 率 1）。memory.peak の線: README 860〜946 行と `perf/dsl-mixed-memory.sh`（peak/max<0.9、anon に替えない）。
- 対象DB の資源上限の既定は postgres 512m・mysql 768m・mariadb 512m で、README の運用値（MySQL 2304m など）と食い違う。
- Dependabot は groups 未使用、ignore は 4 件（otel appender・networknt・typescript major・eclipse-temurin major）。開いている PR は未確認。
- 物差しの変更は team.md により要件定義で依頼者が明示決定し、前の物差しの値を並べて記録する必要がある。
- 次の段（要件定義）への論点: 緩和の対象と値、DB 種類別の線の要否、Dependabot の開いている PR の洗い出し方法（gh が必要）。
