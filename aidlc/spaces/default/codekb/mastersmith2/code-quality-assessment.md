# コードの品質の評価（mastersmith2）

## テスト

今回はテストのコードを読んでいない。次は前回（コミット `6afbf97` 時点）の記録で、その後 F2 の修正で結合テストが足されている（`DataSourcePoolIT` の「既定で 30 本を同時に借りられる」ほか、`aidlc/spaces/default/memory/project.md` の Corrections）。

- バックエンド: `*Test`（単体）、`*IT`（Spring と組み込み H2 を起動する結合）。前回は 60 件・44 件。結合テストの DB は `TestDatabase.register`（`@TempDir` の H2 ファイル）。
- フロントエンド: `*.test.ts(x)`。E2E は Playwright（`verify` と CI の外）。
- カバレッジの下限: JaCoCo（行 80%・分岐 70%）、Vitest（lines 80・branches 70）。
- 境界の検査: `ArchitectureTest`・`AuthBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`（ArchUnit）。
- 性能: `perf/k6/scenarios.js`（6 場面、同時 10・60 秒）。合否は k6 の `thresholds` ではなく、`summaryTrendStats` の p95 を人が目標と比べて判定する。`verify` と CI の外で、開発者の PC の使い捨ての環境に対して行う。
- コンテナの資源（メモリの上限・CPU の上限・JVM の引数）を確かめる自動のテストは無い。

## 検査の道具と CI

- Java: Spotless（palantir-java-format ＋ ライセンスヘッダー）、SpotBugs ＋ FindSecBugs（High だけで失敗）。
- 画面: oxlint・ESLint・Prettier・Stylelint、ライセンスヘッダーの検査スクリプト。
- 秘密情報: Gitleaks（コミット前と CI）。依存の脆弱性: OSV-Scanner、Dependabot（gradle・npm・github-actions・docker）。
- CI: `.github/workflows/ci.yml`。`develop` へのプッシュと `v*` タグで `./gradlew verify`。コンテナの起動と負荷の試験は CI の外。
- コンテナのイメージの検査は採用しない（`team.md` の Code Style）。

## 文書

`README.md`（前提の道具、colima の割り当て、環境変数の表、手元の監視、監査ログの既知の制約、U 間の連携の表）、`perf/README.md`（使い捨ての環境の手順）。`Dockerfile`・両 compose・`application.yaml`・`.env.example` には日本語のコメントで理由が書かれている。Java は全クラスに日本語の Javadoc がある。

## 技術的な負債とリスク

### 監査と接続（前回の記録。F2 は対応済み）

前回の TD-1〜TD-5 の要約。F2 は Intent `260923-audit-pool-exhaustion` でプールの上限の既定を 30 に上げて対応した（コードと監査の決まりは変えていない）。

| 番号 | 内容 | 現在の状態 |
|---|---|---|
| TD-1 | ログインの接続を持ったまま、確定の後に監査が2本目を借りる（`LoginService` → `AuditEventListener` → `AuditEventRecorder` の REQUIRES_NEW） | 形は残る。上限 30 に達すると再び監査の行が欠けうる（README 281〜284 行の既知の制約） |
| TD-2 | 同じ2本使いが `LOGIN_FAILED`・`LOGGED_OUT` にもある | 同上 |
| TD-3 | 不具合を再現するテストが無かった | F2 の修正で再現テストが足された（前回 Intent の記録） |
| TD-4 | ヘルスチェックが業務と同じプールを使い、枯渇中は DOWN を返しうる | 変わらず |
| TD-5 | プールの上限を上げると、メモリの上限 1GB（F3）とヘルスチェックへの影響を見る必要がある | README 284 行に注意書きがある。F3 は本 Intent の対象 |

### 監査の修正が守るべき既存の決まりとテスト（前回の記録）

| 決まり | 確かめているテスト |
|---|---|
| 記録は要求と同じスレッドで、INSERT は1回 | `AuditAuthenticationEventsIT` |
| 監査の行のトレースIDが要求のものと一致 | `AuditTraceIdIT` |
| 元のトランザクションが取り消されたら記録しない | `AuditRollbackIT` |
| 書き込みの失敗でも応答は変わらず、ERROR 1件、再試行なし（BR3.1） | `AuditWriteFailureIT` |
| 200ms 超で WARN | `AuditWriteTimingIT`・`SlowAuditWriteConfig` |
| 追記は新しいトランザクション（REQUIRES_NEW） | `AuditEventRecorderTest` |
| audit の `@Transactional` は `audit.service` だけ、トランザクションの境界は service の層だけ | `AuditBoundaryArchitectureTest`、`ArchitectureTest` |

### 実行環境の資源（今回の所見。F3・F4）

以下は事実の記録であり、直し方（何を変えるか）は要件と設計の段で決める。

#### TD-6（重大度: 高）コンテナのメモリの上限が固定値で、VM を大きくしても変わらない

- `compose.yaml` 60 行と `docker/perf/compose.yaml` 51 行の `mem_limit: 1g` は直書きで、CPU（`${MASTERSMITH_CONTAINER_CPUS:-4}`）と違って環境変数の口が無い。変えるにはファイルの編集が要る。
- F3 はこの 1g の内側で起きている（最大 1021MiB / 1GiB で OOMKilled、終了コード 137）。そのため **colima の VM のメモリを増やすだけでは F3 は直らない見込み** である。VM を大きくして効くのは「使い捨ての環境と配備したアプリを同時に動かせる」ことと、`lgtm`（900m）の余裕だけ。`mem_limit` か JVM の設定（TD-7）を変えない限り、同じ負荷で再び起きる見込み（未検証）。
- `mem_limit` を上げる場合、`-XX:MaxRAMPercentage=75.0` のため最大ヒープも比例して増える（例: 2g なら 1.5GB）。ヒープ以外の余裕も比例して増えるが、F3 の原因がヒープ以外の伸びかヒープかは確かめられていない（下の TD-7）。

#### TD-7（重大度: 高）JVM のヒープ以外の使用量とスレッドの数に上限が無い

- `Dockerfile` 34 行の `ENTRYPOINT` はヒープの割合（75%）だけを決め、メタ領域（`-XX:MaxMetaspaceSize`）、スレッドのスタック（`-Xss`）、直接バッファ（`-XX:MaxDirectMemorySize`）、コードキャッシュなどの上限を決めていない。
- Tomcat の同時処理のスレッドの上限は既定の 200（`application.yaml` に `server.tomcat.*` の設定なし、README 283 行）。スレッドが増えるほどスタックの分だけヒープ以外が増える。
- F3 の見立ては「最大ヒープ 768MB ＋ ヒープ以外の合計が 1GB を超える」。ヒープの 75% はコンテナのメモリに対して余裕が小さい部類で、ヒープ以外に使えるのは約 256MB である。
- 見立ては前の Intent の観察（`docker stats` と終了コード）によるもので、ヒープとヒープ以外の内訳（NMT 等）は測っていない。F3 が起きた `refresh` の経路のコード（`TokenRefreshService`）は今回読んでおらず、メモリの伸びがコードに起因するか（保持の漏れなど）は未確認。

#### TD-8（重大度: 中）JVM の引数を配備ごとに変える口が無い

- ヒープの割合は `ENTRYPOINT` の exec 形式に直書きで、変えるにはイメージの作り直しが要る。
- `JAVA_TOOL_OPTIONS` は `Dockerfile`・両 compose・`.env.example`・README の環境変数の表のどこにも無い。
- `JAVA_TOOL_OPTIONS` はコマンド行の引数より先に読まれるため、仮に `.env` で `-XX:MaxRAMPercentage` を渡しても、`ENTRYPOINT` の `-XX:MaxRAMPercentage=75.0` が後から上書きする（JVM の一般的な挙動。今回は起動して確かめていない）。コマンド行に無いオプション（ヒープ以外の上限など）は `JAVA_TOOL_OPTIONS` で足せる見込みだが、これも未検証。
- `.env` は `env_file` で渡るため、`JAVA_TOOL_OPTIONS` を足すこと自体はファイルの編集なしで可能。ただし `.env.example` と README の表に口として載っていない。

#### TD-9（重大度: 中）設計の前提（CPU 4）と、この PC の実際・文書の記述が食い違う

- `compose.yaml` 57〜59 行と `docker/perf/compose.yaml` 50 行の `cpus` の既定は 4（「照合の時間の目標は 4 が前提」）。一方この PC の colima の VM は CPU 2・メモリ 2GiB（`colima list`、`docker info` は NCPU 2）。VM の CPU より大きい `cpus` は Docker が受け付けないため、既定のままでは起動できない。この PC では `.env` の `MASTERSMITH_CONTAINER_CPUS` を 2 以下にして動かしているはずだが、`.env` は開いていないため値は未確認。
- 照合の CPU 時間は、コンテナの `cpus` と VM の CPU の数の小さい方で頭打ちになる。**VM の CPU を上げても、`.env` の `MASTERSMITH_CONTAINER_CPUS` を上げなければ F4 には効かない**。
- README の colima の例が食い違う: 21 行目は `colima start --cpu 4 --memory 4`（CPU 4 以上を求める）、232 行目（手元の監視）は `colima start --cpu 2 --memory 4`。
- `.env.example` 20 行目のコメントは「例: colima の既定の 2」と、CPU 2 の PC を例にしている。
- `compose.yaml` 96 行目の `lgtm` のコメントは「colima の VM（メモリ 約 2GiB）」を前提にしている。
- `perf/README.md` の手順は `MASTERSMITH_CONTAINER_CPUS=2` を一時の `app.env`（16 行）と `export`（18 行）の両方に固定で書いている。末尾（44 行）に「CPU の上限 2・メモリ 1GB で `refresh` の場面が 30〜40 秒で止まる」の注記がある。VM の性能を変えた後は、これらの記述が実態とずれる。
- colima の VM の設定は PC の上の操作でリポジトリの外にあり、コミットで固定できない。確かめる手段は `colima list`・`docker info` になる。
- CPU 4 での照合の時間・ログインの p95 は未測定。同時 10 件・CPU 4 の見積もりは約 700ms（278 × 10 ÷ 4）だが、これは見積もりにすぎない。

#### TD-10（重大度: 中）負荷の発生元と対象が同じ VM の資源を分け合う

- k6 は同じ VM の中のコンテナ（`--network mastersmith-perf_default`）で動き、上限の指定が無い。CPU 2 の VM では k6 とアプリが CPU を取り合い、F4 の測定値（p95 約 1.6 秒）にその分が混ざっている可能性がある。
- VM の大きさを変えると、アプリに使える資源と k6 の取り分の両方が変わるため、前後の測定値を比べるときはこの点を考える必要がある。
- VM が 2GiB では、使い捨ての環境（1g）と配備したアプリ（1g）を同時に動かせないため、手順 0 で配備したアプリを止める（`aidlc/spaces/default/memory/project.md` の Deployment の学びと一致）。

#### TD-11（重大度: 中）コンテナ全体のメモリを見る監視が無い

- 警報 `ms-heap`（`docker/monitoring/provisioning/alerting/mastersmith.yaml` 281〜312 行）は `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85` が 5 分続いたときだけ出る。ヒープだけを見ており、ヒープ以外を含むコンテナ全体のメモリや OOMKilled は見ていない。F3 のような 30〜40 秒の急な伸びは、5 分の前に止まるため捉えにくい。
- 止まったこと自体は `ms-app-absent`（指標が 5 分届かない、24〜43 行）で間接に分かる。`restart: "no"` のため、止まったままになる。
- ダッシュボードにはヒープの使用率・プールの待ち・ログインの p95 のパネルがあり、コンテナのメモリ・CPU の指標（cAdvisor 等）は無い。
- 警報 `ms-login-p95`（153〜184 行、p95 > 1000ms が 5 分）は F4 の目標 1 秒と対応している。
- 手元の監視は `lgtm`（900m）をアプリ（1g）と同じ VM で動かす前提で、VM 2GiB では余裕が少ない（README 232 行）。

### そのほか

- ルートに管理外の内部DBのバックアップ `mastersmith-data-*.tgz` と `.env` がある（前回の記録）。`.gitignore` で除外済み。コミットの対象にしないこと（中身は読んでいない）。
- 接続プールの上限 30 は、同時の数を増やす負荷の試験をすると近づく（`application.yaml` 103〜106 行のコメント）。

## 健全性のまとめ

検査・テスト・文書が揃った健全なコードベースである。監査と接続の負債（TD-1〜TD-5）は F2 の対応で緩和された。今回の対象の F3・F4 は、アプリのコードではなく、colima の VM の大きさ、コンテナの上限（`cpus` は変数・`mem_limit` は固定）、JVM の起動の引数（ヒープの割合だけ）の組み合わせで決まる（TD-6〜TD-11）。特に、VM を上げるだけでは F3 は直らない見込みであり、F4 もコンテナの `cpus` を合わせて上げる必要がある。部品ごとの評価は `component-inventory.md` を参照。
