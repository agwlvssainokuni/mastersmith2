# CI/CD Pipeline — U1 u1-businessdata-read

この文書は、U1 の次のことを示します。

- 検査の流れ（CI と1コマンドの検査）
- E2E
- 負荷の試験の台本と準備
- 統合・戻し方・秘密
- B2 で確かめること

U1 は読み取りだけの3本の API を持つ service の単位で、B2 で作ります。CI と1コマンドの検査の段・関門は **変えません**。この文書は、既にある仕組みの記録として、どの段で何を確かめるかを書きます（`project.md` の Deployment）。配備先は開発者の PC の上のコンテナだけです。

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません（決まっていること）。

| 項目 | 今の形 | U1 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| 実行 | `./gradlew verify`（`timeout-minutes: 60`、42行・84行） | 変えない。3種類の対象DB の結合テスト（Testcontainers）は CI でも必ず流す |
| 依存の入れ方 | lockfile どおり | 変えない。U1 は依存を足さない（ドライバーは既存の版） |
| 秘密 | 使わない | 変えない。結合テストの資格情報はテストの中だけの値 |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない |

CI が失敗したときは、`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで扱います。CI の結果は、試行ごと（`gh run view --json attempt,jobs`）に記録します（`project.md` の学び）。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず、変えません。

| 段 | U1 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0〜3 準備・フォーマット・リンタ・ライセンスヘッダー | 足す Java のファイル | 既存のとおり | — |
| 4 ビルド | `businessdata.*` と `targetdb` の変更のコンパイル。`application.yaml` の `list`・`detail` | コンパイルの誤り | NFR4.4 |
| 5 単体テスト | 許す操作の集合の関数と識別子の照合の性質ベースのテスト（jqwik。失敗のときの乱数の種を残す）、部分一致のエスケープの関数、`BusinessDataBoundaryArchitectureTest`、変える `RoleBoundaryArchitectureTest`、既存の `TargetDbBoundaryArchitectureTest`・`ApiAccessArchitectureTest` | 1件でも失敗 | NFR4.1・NFR4.6・NFR7.1・NFR7.2 |
| 6 結合テスト | 3種類の対象DB（MySQL・MariaDB・PostgreSQL。版を固定したイメージ）で、定義・一覧・詳細の振る舞い・型の差・時差・境界の値・401/403/200、`BusinessDataConnectionUsageIT`、`BusinessDataSecretLeakIT`（TRACE）、外部へ送る設定を有効にしたトレースの属性の確かめ、応答しない対象DB（ServerSocket・つないだ後に止まる中継）、時間の上限の設定 | 1件でも失敗。コンテナの実行環境が無い途中の実行で飛ばしたときは統合しない | NFR2.2・NFR2.3・NFR3.1・NFR3.3・NFR4.2〜NFR4.5・NFR4.8・NFR8.2 |
| 7 カバレッジ | 3節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR7.1 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（`SQL_` の指摘は priority にかかわらず止める）、OSV-Scanner、Gitleaks | 既存の基準。`SQL_` の除外は、SQL を組み立てて実行する部品のクラス・メソッド・パターン名の3つで絞る（B2 の計画で承認） | NFR4.1 |
| 9 成果物 | 既存の `bootWar` | 既存のとおり | — |

補足:

- 対象DB の結合テストは、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消します。作る名前には、実行ごとに一意の値を入れます（`team.md`）。
- 100 カラム・数行の小さな表で確かめます。100,000 行の種は verify に入れません（`performance-design.md` 5節）。
- つないだ後に止まる中継のテストは、1回で 10〜20 秒かかります。置く DB の種類（1種類か3種類か）は、B2 の計画で U2 とあわせて決めます。
- colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡してから流します（`project.md` の学び）。

## 3. カバレッジ（段 7）

| 対象 | 扱い |
|---|---|
| `packagesJudgedByTotal` | 変えない。`businessdata.*` は新しいパッケージで一覧に無い |
| `businessdata.*` | 自動で、パッケージごとの下限（行 80%・分岐 70%）の対象になる |
| `targetdb.*` | B2 で手を入れる（`ReadPurpose`・`QueryTimeout`・読み取りの口）。一覧に無いため、パッケージごとの下限の対象のまま |
| 計測の除外 | 増やさない |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、値を記録します。

## 4. 時間の記録（NFR7.3）

- B2 の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録します。直近の verify は 14 分 1 秒です（Intent 261015-deps-ui-carryover）。
- CI の上限の7割（約 42 分）を超えたら、テストの分け方を依頼者に諮ります。
- 長い実行は `caffeinate -dims` で包みます。流した後に `pmset -g log` で `Sleep`・`DarkWake` が無いことを確かめます。

## 5. E2E と負荷の試験（verify と CI の外）

### 5.1 E2E（`./gradlew e2eTest`）

- E2E は意図して verify と CI の外に置いています。代わりに、統合の前と、Intent の最後とリリースの前に、手元で流します（`team.md`）。
- B2 は、認証済みの利用者の API と業務データのパッケージを足します。そのため、統合の前に E2E の全体を流して通します。
- U1 のために E2E のファイルは足しません。代表の流れ 180 は U3 が持ちます。

### 5.2 負荷の試験の持ち主と場面

| 作業 | 持ち主 | 出典 |
|---|---|---|
| `perf/k6/scenarios.js` に `bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizDetail`・`bizSearchWide` を足す。1回の繰り返しに要求1つ、判定は `iteration_duration` | B2 | NFR1.2 |
| `bizSearchLast` に、主キー以外で並べ替える深いページを足す | B2 | レビューの R-05 |
| `bizPrepare` の種の台本（`perf/` の下）。種は台本で決めた値から作り、値はすべて架空にする。中心は型ごとの短い値で、大きな値と NULL を含める | B2 | `performance-design.md` 4節 |
| `docker/perf/compose.yaml` の対象DB の設定の口（`infrastructure-specification.md` 2.1。既定の値は今のまま） | B2 | Q1: A |
| `perf/README.md` に業務データの節を足す | B2 | Q1: A・Q3: A |
| `k6 inspect --include-system-env-vars` と短い試し走り | B2 | `project.md` の学び |
| 使い捨ての環境で3種類を1つずつ流して判定し、記録する | Performance Validation | `monitoring-design.md` 3節 |

README の業務データの節に書くこと:

1. 配備したアプリを止める。
2. 業務データの試験の値（MySQL 768M・1792m、MariaDB 512M・1g、PG 256MB・768m）を export する。
3. MySQL・MariaDB の回では lgtm を動かさない。指標は、使い捨てのアプリにだけ公開する actuator の metrics で読む。
4. 対象DB の接続先は、一時の環境ファイル（`MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`）で渡す。

### 5.3 流す順（対象DB の種類ごと）

1. 対象DB を、業務データの試験の値で起動する。
2. `bizPrepare` で種を入れる。
3. 表のデータと索引の大きさを測る。プールの 80% を超えたら上げて起動し直す（`infrastructure-specification.md` 2.2）。
4. 設定の実際の値・上限・表の大きさを記録する。
5. 表を1回全部読んで暖める。
6. 合否の回を流す。待ちの最大のような揺れる値があるため、複数回流す。
7. `bizSearchWide` の前に、アプリのコンテナを作り直して前の最大を消す。
8. 確かめの結果（待ちの時間切れの累計・`memory.peak`・OOMKilled・WARN の件数）を見てから片付ける。

そのほかの決まり:

- 台本の全体を `caffeinate -dims` で包みます。
- 場面の setup は、前の場面が残した状態に頼りません。
- 負荷の試験の資格情報は、ホームの下の一時の環境ファイル（権限 700）で渡し、表示しません。

## 6. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261016-business-data-views-b2`） | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）と E2E の全体（5.1） | `team.md` |
| 統合の形 | `develop` への squash（1コミット） | `team.md`・`inception/delivery-planning/bolt-plan.md`（B2） |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順のまま。配備の `.env` は変えない | `infrastructure-specification.md` 1節 |
| 戻し | 直前の版のイメージ（移行と `.env` の変更が無い） | 同上 |
| 秘密 | 足さない。`.env` や一時の環境ファイルをコミットしない | `project.md` の Forbidden |
| 依存 | 足さない | — |

## 7. B2 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 2節の段 5・6 のテストが、3種類の対象DB ですべて通る | verify | 原因を直す |
| (ii) カバレッジの実測と verify・CI の時間 | 3節・4節 | 除外を増やさずテストを足す。42 分を超えたら諮る |
| (iii) `application.yaml` の `list`・`detail` と `.env.example` のコメントの行が 1.1 の形で、配備の `.env` を変えていない | コード生成のレビュー | 直す |
| (iv) `docker/perf/compose.yaml` の既定の値が今のまま（DSL の時間の測りが変わらない） | `docker compose -f docker/perf/compose.yaml config` を変数なしで読む | 直す |
| (v) k6 の場面と種の台本が、`k6 inspect` と試し走りで動く | 5.2 | 台本を直す |
| (vi) E2E の全体 | 5.1 | 統合しない。原因を直す |
| (vii) `docker/monitoring/`・`ci.yml`・`packagesJudgedByTotal` が変わっていない | コード生成のレビュー | 元に戻す |

## 8. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `nfr-design/logical-components.md` 5節 | 「`.env` の項目は増えない」 | `application.yaml` で `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL` を受け、`.env.example` にコメントの行を足す。配備の `.env` は変えない | Q4: A。「`.env` に値を入れなくてよい」と読む |
| `nfr-design/observability-design.md` 2節 | 指標を `http.server.requests` と書く | 手元の監視の式の名前は `http_server_requests_milliseconds_*` | 既存の式の名前（`monitoring-design.md` 7節） |
| レビューの R-06 | PG の共有バッファーの中身が書かれていない | PG は `shared_buffers` 256MB・上限 768m で、Performance Validation で測る | Q2: A |

## 9. 後の段に回すこと

- **コード生成の計画（B2）**
  - 環境変数の名前を決める（perf の対象DB の設定と上限、`list`・`detail`）。
  - `perf/README.md` の業務データの節
  - k6 の場面（5.2。R-05 の並べ替えを含む）と `bizPrepare` の種
  - SpotBugs の `SQL_` の除外と、`RoleBoundaryArchitectureTest` の変更の承認
  - 中継のテストを置く DB の種類
  - NFR 設計の承認の場の決定（R-01・R-02 をレビューの推奨の方向で直す）
- **Performance Validation**
  - 5.3 の実測と記録（設定・表の大きさ・`memory.peak`・待ちの時間切れの累計）
  - 合否の判定。Not Met は諮る
- **Observability Setup**
  - ダッシュボードの区画
  - R-05 の実測（`monitoring-design.md` 2節・5節）

## 10. U2・U3 とそろえる点

- **U2**
  - `bizPrepare` の種と、perf の対象DB の設定の値・変数の名前・README の節を共用する。U2 の場面（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`）も、5.3 の順と同じ記録で流す。
  - 書き込みのアカウントの初期化（`docker/targetdb/*/02-*.sh`）・`REVOKE TEMPORARY`・GRANT は、U2 が足す。
  - 中継のテストの DB の種類と `BusinessDataConnectionUsageIT` は、U1 と1つにまとめる。
  - 統合の前の E2E は、U2 の B4 でも全体を流す。
- **U3**
  - E2E 180 は U3 が足し、見本の PG を使う（U3 の基盤の設計）。U1 の perf の変更は E2E に響かない。

## 11. 未確認の点

- **VM の中の空き**: VM の OS・dockerd・k6 のコンテナの使う量は測っていない。5.3 の間に詰まれば、見本の PG・Mailpit も止める。
- **MariaDB の実測**: 上限 1g は見立ての値。5.3 の `memory.peak` で確かめる。
- **MySQL の丸め**: バッファープールの実際の値は、5.3 の 4 で確かめて記録する。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q4: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u1-businessdata-read/nfr-design/` の `performance-design.md`（4節・5節・7節）・`scalability-design.md`（3節・5.3・7節）・`reliability-design.md`（7節）・`security-design.md`（8節・10節）・`observability-design.md`（6節）・`logical-components.md`（5節・6節）
  - `construction/u1-businessdata-read/nfr-requirements/`（NFR1.2・NFR7.1〜NFR7.3）
  - `construction/nfr-design/memory.md`（承認の場の決定）
  - `.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`（R-05・R-06）
  - `inception/delivery-planning/bolt-plan.md`（B2）
- 既にある仕組み（正とする。読むだけ）: `.github/workflows/ci.yml`・`perf/README.md`・`perf/k6/scenarios.js`・`perf/dsl-timing.sh`・`docker/perf/compose.yaml`
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture）、`project.md`（caffeinate -dims、片付けは確かめた後、待ちの最大は複数回、CI の試行ごとの記録）
- 直近の verify の時間: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md`（288行）

## Assumptions & Open Questions

- 前提: 3種類の対象DB の結合テストが増えても、verify は 42 分を下回ると見立てた（B2 の後に実測する）。
- 前提: `bizPrepare` の種を入れる時間は、Performance Validation の時間の中に収まる（台本の形は B2 で決める）。
- 依頼者の決定（Q1〜Q4: A）は、5節・6節・8節に反映した。
- 諮る点は無い。
