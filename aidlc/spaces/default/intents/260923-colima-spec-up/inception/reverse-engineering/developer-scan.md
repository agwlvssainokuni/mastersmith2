## Developer Code Scan Results

対象の Intent: 高い負荷でアプリのコンテナがメモリの上限で止まる（F3）と、CPU 2 でログインが目標の 1 秒を超える（F4）を、colima の VM の性能を上げて直す。スキャンの幅は Focused scan（コンテナの資源・JVM・ログインの CPU 負荷・負荷試験・監視に絞る）。コードと共有の知識ベースは変更していない。`.env` は開いていない。

### Scan Coverage
- **Analyzed deeply**:
  - `Dockerfile`
  - `compose.yaml`
  - `docker/perf/compose.yaml`
  - `docker/monitoring/`（`provisioning/alerting/mastersmith.yaml`、`provisioning/dashboards/mastersmith.yaml`、`dashboards/mastersmith-overview.json` はパネルの題と式だけ）
  - `docker/otel-collector/config.yaml`
  - `perf/`（`perf/README.md`、`perf/k6/scenarios.js`）
  - `README.md`（前提の道具・環境変数・手元の監視・監査ログの既知の制約の節）
  - `.env.example`
  - `.dockerignore`
  - `backend/src/main/resources/application.yaml`
  - `backend/src/main/java/cherry/mastersmith/config/`（6 ファイル）
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`
- **Skimmed only**:
  - `backend/src/main/java/cherry/mastersmith/user/service/`（`UserAccountConfig.java` で `BCryptPasswordEncoder(properties.bcryptCost())` を確認しただけ。`UserAccountService` の照合と、存在しない利用者でのダミー照合は読んでいない）
  - トークンの更新の処理（F3 が起きた `refresh` の場面の経路。読んでいない）
  - Gradle のビルドの設定（`build.gradle.kts` 等。README と規則の記述からの把握のみ）
  - `frontend/`、`vendor/make-you-chic-ui`（対象外）
  - 前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/operation/performance-validation/test-results.md` の F3・F4 の行、`aidlc/spaces/default/intents/260923-audit-pool-exhaustion/operation/deployment-execution/deployment-log.md` のメモリの行）
  - この PC の実行環境（読み取りのみ）: `colima list` → CPU 2・メモリ 2GiB・aarch64、`docker info` → NCPU 2・MemTotal 約 1.9GiB

### Packages Found
- `backend` — アプリ（実行可能 WAR）— Java 25 / Spring Boot 4 — API・画面の配信・内部DB（組み込みの H2）
- `frontend` — 画面（SPA、WAR に同梱）— React + TypeScript — 今回は対象外（流し読み）
- コンテナの定義 — `Dockerfile`・`compose.yaml`（app・otel-collector・lgtm）— 配備（開発者の PC の colima）
- 使い捨ての負荷試験環境 — `docker/perf/compose.yaml`・`perf/k6/scenarios.js` — k6 で性能の目標を確かめる

### Build System
- **Type**: Gradle（Kotlin DSL、Wrapper）。入口は `./gradlew verify`、WAR は `./gradlew :backend:bootWar`。イメージは WAR をコピーするだけで、イメージの中ではビルドしない（`Dockerfile` 冒頭のコメント）
- **Config Files**: `Dockerfile`、`compose.yaml`、`docker/perf/compose.yaml`、`.dockerignore`（WAR 1 つだけを送る）、`.env.example`、`backend/src/main/resources/application.yaml`
- **Build Dependencies**: `frontend`（dist）→ `backend`（WAR に同梱）→ イメージ `mastersmith:${MASTERSMITH_IMAGE_TAG:-local}` → 配備（`compose.yaml`）と負荷試験（`docker/perf/compose.yaml`）が同じイメージを使う

### APIs Discovered
- REST — `LoginService.login` の呼び元 `/api/auth/login` ほか、k6 の台本が叩く 6 つ（`/actuator/health`、`/api/auth/login`、`/api/auth/session/refresh`、`/api/admin/check`）— 今回の範囲では新しい API は無い

### Frameworks & Libraries
- Eclipse Temurin JRE — `25.0.4_7-jre-noble`（`Dockerfile` でタグ固定）— 実行環境
- Spring Boot — 4.1.1（規則 `project.md` の Tech Stack による）— アプリの基盤
- Spring Security `BCryptPasswordEncoder` — cost 既定 12（`application.yaml` の `mastersmith.auth.password.bcrypt-cost`、環境変数 `MASTERSMITH_AUTH_PASSWORD_BCRYPT_COST`、4〜31）— パスワードの照合
- HikariCP — `maximum-pool-size` 既定 30（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`）、`connection-timeout: 5000` — 接続プール
- H2 — 2.4.240（`perf/README.md` の手順の jar の版）— 内部DB
- grafana/k6 — 2.3.0 — 負荷の試験
- grafana/otel-lgtm — 0.33.1（`mem_limit: 900m`、profile `monitoring`）— 手元の監視
- otel/opentelemetry-collector — 0.161.0（profile `observability`、debug 出力のみ）— 外部エクスポートの確認

### Test Coverage
- **Test Directories**: 今回の範囲では読んでいない（`backend/src/test/java` の `XxxTest`・`XxxIT` の分け方は規則による）。性能は `perf/k6/scenarios.js` の 6 場面（同時 `VUS` 既定 10、`DURATION` 既定 60s、考える時間なし）
- **Test Frameworks**: k6（性能）。単体・結合は JUnit 系（範囲外）
- **Coverage Config**: 範囲外（JaCoCo の下限は規則による）。性能の目標は k6 の `summaryTrendStats` で p95 を出して人が判定する（k6 の `thresholds` による自動の合否は無い）

### Code Quality Indicators
- **Linting**: 範囲外（Spotless・SpotBugs 等は規則による）
- **CI/CD**: 範囲外（GitHub Actions）。コンテナの起動と負荷試験は CI の外で、開発者の PC で行う
- **Documentation**: `README.md` に colima の VM の割り当ての例、`perf/README.md` に使い捨て環境の手順がある。設定ファイルには日本語のコメントで理由が書かれている

### 資源とログイン負荷に関する事実（今回の中心）

1. **コンテナのメモリの上限は 1GB で、変数になっていない**
   - `compose.yaml` の `app` は `mem_limit: 1g`、`docker/perf/compose.yaml` の `app` も `mem_limit: 1g`（どちらも固定値。CPU の上限だけが `${MASTERSMITH_CONTAINER_CPUS:-4}` で変えられる）。
   - `lgtm` は `mem_limit: 900m`（コメント「colima の VM（メモリ 約 2GiB）の中で、アプリ（上限 1GB）と一緒に動かすための上限」）。
2. **JVM のヒープはコンテナのメモリの 75%**
   - `Dockerfile` の `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=Asia/Tokyo", "-jar", "/app/mastersmith.war"]`。1g なら最大ヒープ 768MB で、ヒープ以外（メタ領域・スレッドのスタック・コードキャッシュ・直接バッファ等）の上限は指定していない。
   - `JAVA_TOOL_OPTIONS` はどこにも設定されていない（`Dockerfile`・両 compose・`.env.example`・README の環境変数の表のいずれにも無い）。なお `JAVA_TOOL_OPTIONS` はコマンド行より先に読まれるため、仮に足しても `ENTRYPOINT` の `-XX:MaxRAMPercentage=75.0` がそれを上書きする（JVM の一般的な挙動。今回は起動して確かめていない）。
   - Tomcat の同時処理のスレッドの上限は既定の 200（README「既知の制約」）。スレッドが増えるほどヒープ以外の使用量が増える。
   - 前の Intent の記録（F3）: トークンの更新を毎秒約 3,000 件で約 35 秒流すと OOMKilled（終了コード 137、最大 1021MiB / 1GiB）。原因の見立ては「最大ヒープ 768MB とヒープ以外の合計が 1GB を超える」。通常の負荷では 353.8MiB（負荷中）・287.6MiB（配備後）で OOMKilled なし。
3. **CPU の上限の既定は 4 だが、この PC の VM は 2**
   - `compose.yaml` と `docker/perf/compose.yaml` の `cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}`。コメント「照合の時間の目標は 4 が前提」。
   - `colima list` は CPU 2・メモリ 2GiB。VM の CPU より大きい `cpus` は Docker が受け付けないため、この PC では `.env` 側で 2 以下に下げて動かしているはず（`.env` は開いていないため未確認）。
   - `perf/README.md` の手順は一時の `app.env` に `MASTERSMITH_CONTAINER_CPUS=2` を書き、`export MASTERSMITH_CONTAINER_CPUS=2` もしている（手順が CPU 2 を前提に固定されている）。
4. **ログインは bcrypt の照合1回で CPU を使う**
   - `LoginService.login`（`backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` 117〜124 行）は、`userAccountService.verifyPassword(...)` をトランザクションと排他の外で1回呼び、その後 `TransactionTemplate` の短いトランザクションで判定・更新・トークン発行を行う。CPU の重い部分は照合だけで、ロックの外にある。
   - cost 12 で照合1回 約 278ms（前の記録）。同時 10 件を CPU 2 で分け合うと 1件 約 1,400ms（278 × 10 ÷ 2）と見積もられ、実測の p95 は 約 1.6 秒（F4）。CPU 4 では約半分の見込みだが測っていない。
   - 照合の CPU 時間は VM の CPU の数とコンテナの `cpus` の両方で頭打ちになる。VM を上げても `MASTERSMITH_CONTAINER_CPUS` を上げなければ効かない。
5. **接続プール**: `application.yaml` の `hikari.maximum-pool-size: ${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}`、`connection-timeout: 5000`。ログインは確定の後に監査で2本目を借りる（コメントと README の既知の制約）。今回の変更で触れる必要は見当たらないが、同時の数を増やす試験をすれば 30 に近づく。
6. **負荷試験の手順と前提（`perf/README.md`・`docker/perf/compose.yaml`・`perf/k6/scenarios.js`）**
   - 手順の 0 で配備したアプリを `docker compose stop app` で止める（2GiB の VM で 1g + 1g を同時に動かせないため。規則 `project.md` の Deployment の学びとも一致）。
   - 使い捨て環境は別のプロジェクト名 `mastersmith-perf`・別ボリューム `perf-data`・ポート `127.0.0.1:18080`。仮の署名鍵・仮の管理者はリポジトリの外の一時ファイルから読む。
   - k6 は同じ VM の中のコンテナ（`--network mastersmith-perf_default`）で動くため、k6 自身も VM の CPU とメモリを使う。CPU 2 の VM では k6 とアプリが CPU を取り合い、F4 の測定値に混ざっている可能性がある。
   - 末尾の注記「`refresh` の場面は、CPU の上限 2・メモリ 1GB の設定で 30〜40 秒ほどでコンテナがメモリの上限で止まる（2026-09-23 の試験の結果）」がある。直した後はこの注記の更新が要る。
7. **README の colima の記述**
   - 21 行目: 「colima を使う場合は、VM に CPU を 4 つ以上割り当ててください（例: `colima start --cpu 4 --memory 4`）」。
   - 232 行目（手元の監視）: 「止まる・遅いときは VM のメモリを増やしてください（例: `colima stop` → `colima start --cpu 2 --memory 4`）」。
   - `.env.example` の `MASTERSMITH_CONTAINER_CPUS` のコメント「例: colima の既定の 2」。
8. **監視（`docker/monitoring/`）**
   - 警報 `ms-heap`（JVM のメモリの不足の兆し）: `sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"}) > 0.85`、`for: 5m`。ヒープだけを見ており、コンテナ全体のメモリ（ヒープ以外を含む）や OOMKilled は見ていない。F3 のような短時間の急増は `for: 5m` の前に止まるため捉えにくい。
   - 警報 `ms-login-p95`（ログインの応答の遅れ）: `/api/auth/login` の p95 が 1000ms 超、`for: 5m`。F4 の目標 1 秒と対応する。
   - 警報 `ms-app-absent`（指標が 5 分届かない）が、止まったこと自体の間接の検知になる。
   - ダッシュボードには「JVM のヒープの使用率」「コネクションプールの待ち」「ログインの API」の p95 のパネルがある。コンテナのメモリ・CPU の指標（cAdvisor 等）は無い。
   - 手元の監視は `lgtm`（900m）をアプリ（1g）と同じ VM で動かす前提。VM が 2GiB では余裕がない（README 232 行目）。

### Technical Debt Signals
- メモリの上限 `mem_limit: 1g` が `compose.yaml` と `docker/perf/compose.yaml` に固定で書かれ、CPU と違って環境変数で変えられない。VM を大きくしても、コンテナの上限を変えるにはファイルの編集が要る。
- JVM のメモリはヒープの割合（75%）だけを決め、ヒープ以外の上限（`-XX:MaxMetaspaceSize`、`-Xss`、`-XX:MaxDirectMemorySize` 等）もスレッド数の上限も決めていない。高い負荷でヒープ以外が伸びると 1g を超える（F3 の見立て）。
- `ENTRYPOINT` にヒープの割合が直書きで、`JAVA_TOOL_OPTIONS` などで配備ごとに変える口が無い（変えるにはイメージの作り直しが要る）。
- 設計の前提（CPU 4）と、この PC の実際（colima の VM が CPU 2・メモリ 2GiB）が食い違っている。`cpus` の既定 4 は VM が 2 のままでは起動できない値。
- README の colima の例が2か所で食い違う（21 行目は `--cpu 4 --memory 4`、232 行目は `--cpu 2 --memory 4`）。
- 負荷試験の手順が CPU 2 を固定で書いている（`perf/README.md` の `MASTERSMITH_CONTAINER_CPUS=2`）。VM を上げた後は手順の値の見直しが要る。
- k6 と対象のアプリが同じ VM で CPU を取り合うため、測定値が VM の大きさに影響される。
- ヒープ以外を含むコンテナのメモリの監視が無い（`ms-heap` はヒープだけ）。

## Handoff Summary
- **Intent-relevant finding**: F3・F4 はどちらも「colima の VM（CPU 2・メモリ 2GiB。`colima list` で確認）」と「コンテナの上限（`compose.yaml`・`docker/perf/compose.yaml` の `mem_limit: 1g` 固定、`cpus: ${MASTERSMITH_CONTAINER_CPUS:-4}`）」と「JVM の設定（`Dockerfile` の `ENTRYPOINT` の `-XX:MaxRAMPercentage=75.0` だけ）」の組み合わせで決まる。ログインの CPU 負荷は `LoginService.login`（117〜124 行）の bcrypt の照合1回（cost 12、約 278ms）で、ロックとトランザクションの外にある。VM の CPU を上げるだけでは効かず、`MASTERSMITH_CONTAINER_CPUS`（`.env`）も合わせて上げる必要がある。メモリは `mem_limit` が固定のため、VM を大きくしても F3（ヒープ 768MB＋ヒープ以外が 1GB を超える）は、`mem_limit` か JVM の設定を変えない限り同じ負荷で再び起きる見込み。
- **Risks / follow-up**:
  - VM を上げるだけで F3 が直るかは疑わしい。F3 はコンテナの上限 1g の内側の問題で、VM の大きさは「使い捨て環境と配備したアプリを同時に動かせる」ことと `lgtm` の余裕にしか効かない。`mem_limit` を上げる・変数にする、または JVM の割合を変えるかを要件で決める必要がある。
  - `.env` は開いていないため、この PC の `MASTERSMITH_CONTAINER_CPUS` の現在の値は未確認。VM を CPU 4 にした後、`.env` の値の扱い（消して既定の 4 に戻すか）を手順に含める必要がある。
  - `JAVA_TOOL_OPTIONS` では `ENTRYPOINT` のコマンド行の指定を上書きできない（JVM の一般的な挙動で、今回は起動して確かめていない）。JVM の設定を配備ごとに変える口が要るなら `Dockerfile` の変更になる。
  - F3 が起きた `refresh` の経路のコードは今回読んでいない（流し読みの範囲外）。メモリの伸びがコードに起因するかは未確認。
  - 直した後に `perf/README.md` の CPU 2 固定の手順と末尾の F3 の注記、README 21・232 行目と `.env.example` の colima の記述、`compose.yaml` の `lgtm` のコメント（約 2GiB）を実態に合わせて直す必要がある。
  - colima の VM の設定は PC の上の操作でリポジトリの外にあり、コミットで固定できない。手順（README）として残し、`colima list` 等で確かめる形になる。
  - 監視の `ms-heap` はヒープだけを見るため、コンテナのメモリの上限への接近は捉えられない。今回の範囲に含めるかは要件で判断する。
  - 前の Intent の CPU 4 での性能は未測定。F4 が CPU 4 で 1 秒以内に収まることは、VM を上げた後に負荷試験で確かめる必要がある（k6 も同じ VM の CPU を使う点に注意）。
