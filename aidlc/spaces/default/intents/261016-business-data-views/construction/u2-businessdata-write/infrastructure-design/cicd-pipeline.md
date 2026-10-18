# CI/CD Pipeline — U2 u2-businessdata-write

この文書は、U2 の次のことを示します。

- 検査の流れ（CI と1コマンドの検査）
- E2E と、E2E の書き込みのアカウント
- 負荷の試験の台本と準備
- 統合・配備・戻し方・秘密
- B4 で確かめること

U2 は書き込みの3本の API を持つ service の単位で、B4 で作ります。CI と1コマンドの検査の段・関門は **変えません**。この文書は、既にある仕組みの記録として、どの段で何を確かめるかを書きます（`project.md` の Deployment）。配備先は開発者の PC の上のコンテナだけです。

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません（決まっていること）。

| 項目 | 今の形 | U2 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない |
| 実行 | `./gradlew verify`（`timeout-minutes: 60`、42行・84行） | 変えない。3種類の対象DB の結合テスト（Testcontainers）は CI でも必ず流す |
| 依存の入れ方 | lockfile どおり | 変えない。U2 は依存を足さない（ドライバーと spring-tx は既存の版） |
| 秘密 | 使わない | 変えない。結合テストの資格情報は、テストの中で作る乱数の値 |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない |

CI が失敗したときは、`team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで扱います。CI の結果は、試行ごと（`gh run view --json attempt,jobs`）に記録します（`project.md` の学び）。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・設定は増やさず、変えません。

| 段 | U2 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0〜3 準備・フォーマット・リンタ・ライセンスヘッダー | 足す Java のファイルと、3つの `03-writer-account.sh`（ライセンスヘッダー） | 既存のとおり | — |
| 4 ビルド | `businessdata.*`・`targetdb.config`・`targetdb.domain`・`targetdb.service`・`audit` の変更のコンパイル。`application.yaml` の書き込みの項目 | コンパイルの誤り | NFR4.4・NFR4.11 |
| 5 単体テスト | 検証と pattern の打ち切りの性質ベースのテスト（jqwik。失敗のときの乱数の種を残す）、`BusinessDataBoundaryArchitectureTest` の書き込みの側、変えない `TargetDbBoundaryArchitectureTest`・`ApiAccessArchitectureTest`、通信の待ちの計算（書き込みの上限を変えたときに「上限 + 5 秒」になる） | 1件でも失敗 | NFR4.1・NFR4.4・NFR4.10・NFR7.6 |
| 6 結合テスト | 3.1 の表 | 1件でも失敗。コンテナの実行環境が無い途中の実行で飛ばしたときは統合しない | NFR2.6・NFR3.5・NFR3.6・NFR4.2・NFR4.3・NFR4.11〜NFR4.15・NFR7.5 |
| 7 カバレッジ | 4節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR7.5 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（`SQL_` の指摘は priority にかかわらず止める）、OSV-Scanner、Gitleaks | 既存の基準。`SQL_` の除外は `RowSqlExecutor` の書き込みのメソッドとパターン名に絞って広げる（B4 の計画で承認。BR13.3） | NFR4.1 |
| 9 成果物 | 既存の `bootWar` | 既存のとおり | — |

### 2.1 段 6 の結合テスト

| テスト | 確かめること | 出典 |
|---|---|---|
| 名前つきの管理者の注入 | 書き込みの管理者を足しても、型で注入する内部DB の管理者が1つのままで、既存の `@Transactional` が内部DB を使う。**書き込みの部品より前の手順に置き、先に通す** | NFR 設計のレビューの R-06 |
| 書き込みの振る舞い（3種類） | 作成・更新・削除、判定の順、409・503 の出し分け、セーブポイントでの違反の確かめ、時差、件数。テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作って消し、別の接続で読み直す | NFR3.5・NFR3.6・NFR4.12・`team.md` |
| **見本の初期化の台本（3種類。Q6: A）** | 実際の `docker/targetdb/<種類>/` の台本を初期化の場所に写したコンテナで起動する。`mastersmith_writer` は見本の表の DML が通り、CREATE・DROP・ALTER・TRUNCATE・INDEX・VIEW・SCHEMA は拒まれ、PG の一時の表も拒まれる。`mastersmith_reader` では書けない。パスワードはテストの中の乱数 | NFR4.3・F2: A（NFR 設計）・AC3.6.7 |
| 書き込みの設定の欠け | 資格情報が両方・片方・無しの起動と応答（409 `BUSINESS_WRITE_NOT_CONFIGURED`、項目の名前だけの WARN） | NFR4.11 |
| `BusinessDataConnectionUsageIT` | 作成・更新・削除の3行。書き込みの DataSource を数える列を足す（U1 と1つのクラス） | NFR2.6 |
| `BusinessDataWriteSecretLeakIT` | TRACE を有効にしたログに、値・主キー・接続先・例外のメッセージが無い。監査の書き込みの失敗の ERROR に cause が無い。外部へ送る設定のトレースの属性も確かめる | NFR4.2・NFR8.4 |
| 応答しない対象DB | 答えない ServerSocket と、つないだ後に止まる中継（確定の途中で止める形を足す）。置く DB の種類は U1 と合わせて決める | NFR4.4・NFR4.15 |
| 監査 | 3列・種類・理由の値・`UNKNOWN` を足す移行（V13）の後方互換。既存の `AuditMigrationCompatibilityIT`・`AuditSecretLeakIT` の列の一覧への影響 | NFR4.13・NFR4.15、NFR 要件のレビューの R-06 |

補足:
- 見本の初期化の台本の結合テストで、verify にコンテナが3つ増えます（約 1 分の見込み）。B4 の後に実測して記録します（3節）。
- colima の PC では、README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡してから流します（`project.md` の学び）。

## 3. 時間の記録（NFR7.4）

- B4 の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録します。
- CI の上限の7割（約 42 分）を超えたら、テストの分け方を依頼者に諮ります。
- 長い実行は `caffeinate -dims` で包みます。流した後に `pmset -g log` で `Sleep`・`DarkWake` が無いことを確かめます。

## 4. カバレッジ（段 7）

| 対象 | 扱い |
|---|---|
| `businessdata.*`・`targetdb.*` | パッケージごとの下限（行 80%・分岐 70%）の対象のまま |
| `audit.repository`（`packagesJudgedByTotal` にある） | V13 に合わせて本体に手を入れたら、テストを足して下限を満たし、一覧から外す（B4 の DoD） |
| `common.web`（同上） | 道ごとの本文の上限で手を入れたら、同じ扱い |
| 計測の除外 | 増やさない |

実測は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、値を記録します。

## 5. E2E と負荷の試験（verify と CI の外）

### 5.1 E2E（`./gradlew e2eTest`）

- B4 は `businessdata` の `src/main` に触れます。そのため、統合の前に E2E の全体を流して通します（`team.md`）。
- U2 は E2E のファイルを足しません。代表の流れ 180 は U3 が持ちます。
- **E2E の書き込みのアカウント（Q5: A。U3 と共有）**

| 項目 | 扱い |
|---|---|
| 作り方 | E2E が実行ごとに、管理者の資格情報で一時のロールを作る（書き込み用。読み取りも同じ形） |
| パスワード | 乱数。プロセスの環境変数で渡し、`frontend/playwright.config.ts` の `SECRET_ENV_NAMES` に足す（`webServer.env` に置かない） |
| 付与 | その実行の専用のスキーマの DML だけ。DDL は与えない |
| 片付け | 終わったら消す。始めに、前の実行の残り（ロールと専用のスキーマ）を消す |
| 頼らないもの | 見本の `mastersmith_writer` があるかどうか（`team.md` の「前提を自分で作る」） |

この答えは U3 の基盤の設計に渡します。

### 5.2 負荷の試験の持ち主と場面

| 作業 | 持ち主 | 出典 |
|---|---|---|
| `perf/k6/scenarios.js` に `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge` を足す。1回の繰り返しに要求1つ、判定は `iteration_duration` | B4 | `nfr-design/performance-design.md` 4節 |
| **setup の前提（U1 の基盤の設計のレビューの R-02 を先に避ける）**: 100 カラムの表と参照先のある表を含む DSL の適用、FULL のカラムと CREATE・DELETE の補助権限を持つロール、その割り当て・作業ロール・トークン、`bizDelete` の回数以上の行。setup が自分で作り、前の場面の状態に頼らない | B4 | `project.md` の学び |
| `bizPrepare` の種のスキーマに、書き込みのアカウントの DML を付与する（MySQL・MariaDB は種の DB、PG は種のスキーマと sequence） | B4 | `infrastructure-specification.md` 3.2 |
| `perf/dsl-timing.sh` と `perf/README.md` の一時の環境ファイルに、`MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` の生成を足す。アプリの一時の環境ファイルに `MASTERSMITH_TARGET_DB_WRITE_*` を足す | B4 | Q2: A |
| `perf/README.md` の業務データの節に、MySQL の上限 2304m を書く（U1 の口の環境変数で export する） | B4 | F1: A |
| `k6 inspect --include-system-env-vars` と短い試し走り | B4 | `project.md` の学び |
| 使い捨ての環境で3種類を1つずつ流して判定し、記録する | Performance Validation | `monitoring-design.md` 3節 |

用意の操作と書き込みは、使い捨ての環境の監査に残ります（配備した内部DB は汚しません）。

### 5.3 流す順（対象DB の種類ごと）

U1 の `cicd-pipeline.md` 5.3 の順に、U2 の場面を足します。

1. 対象DB を、業務データの試験の値（`infrastructure-specification.md` 2.2）で起動する。
2. `bizPrepare` で種を入れ、書き込みのアカウントに付与する。
3. 表の大きさを測り、設定の実際の値・上限・表の大きさを記録する。
4. 表を1回全部読んで暖める。
5. U1 の読み取りの場面の後に、`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` を流す。待ちの最大のような揺れる値があるため、複数回流す。
6. `bizCreateLarge` の前に、アプリのコンテナを作り直して前の最大を消す。
7. 確かめの結果を見てから片付ける。見るものは、待ちの時間切れの累計・アプリの `memory.peak`・対象DB の OOMKilled と `oom_kill`・`anon` の最大・`memory.events` の `max`・WARN の件数・監査の行の数。

そのほかの決まり:
- 台本の全体を `caffeinate -dims` で包みます。
- 試験の間は配備したアプリを止め、MySQL・MariaDB の回は lgtm と同時に動かしません。
- 負荷の試験の資格情報は、ホームの下の一時の環境ファイル（権限 700）で渡し、表示しません。

## 6. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（例 `feature/261016-business-data-views-b4`） | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）と E2E の全体（5.1） | `team.md` |
| 統合の形 | `develop` への squash（1コミット） | `inception/delivery-planning/bolt-plan.md`（B4） |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順に、`infrastructure-specification.md` 4節の手順（見本の PG への書き込みのアカウントの適用と `.env` の2行）を足す | Q4: A |
| 配備の前のバックアップ | V13 があるため、内部DB を複写する（Deployment Pipeline の決まりで扱う） | `team.md` の Deployment |
| 戻し | 直前の版のイメージ。V13・書き込みのアカウント・`.env` の行は残る（前の版は使わない） | 前進だけの移行 |
| 秘密 | `.env`・`.env.targetdb`・一時の環境ファイルをコミットしない。値を表示しない | `project.md` の Forbidden |
| 依存 | 足さない | — |

## 7. B4 で確かめること

| 確かめ | 方法 | 成り立たないとき |
|---|---|---|
| (i) 2節の段 5・6 のテストが、3種類の対象DB ですべて通る。名前つきの管理者の IT が先に通る | verify | 原因を直す |
| (ii) カバレッジの実測と verify・CI の時間 | 3節・4節 | 除外を増やさずテストを足す。42 分を超えたら諮る |
| (iii) `application.yaml`・`.env.example`・`.env.targetdb.example` が `infrastructure-specification.md` 1.1 の形で、配備の `.env` を変えていない | コード生成のレビュー | 直す |
| (iv) `03-writer-account.sh` を2回流しても同じ結果になる | 見本の初期化の台本の IT（同じコンテナで2回流す） | 直す |
| (v) `docker/perf/compose.yaml` の既定の値が今のまま | ダミーの `MASTERSMITH_PERF_ENV_FILE` と3つの `--profile` を付けて `docker compose -f docker/perf/compose.yaml config` を読み、`command` と `mem_limit` が今の値と同じことを見る（U1 のレビューの R-04 の形） | 直す |
| (vi) `perf/dsl-timing.sh` の一時の環境ファイルで、perf の対象DB の初期化が通る | 書き込みのパスワードが入ることを、台本を読んで確かめる | 直す |
| (vii) k6 の場面と setup が、`k6 inspect` と試し走りで動く | 5.2 | 台本を直す |
| (viii) E2E の全体 | 5.1 | 統合しない。原因を直す |
| (ix) `docker/monitoring/`・`ci.yml` が変わっていない | コード生成のレビュー | 元に戻す |

## 8. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `nfr-design/logical-components.md` L11 | 書き込みのプールの socketTimeout 10 秒 | 「書き込みの上限 + 5 秒」で計算する（既定は 10 秒で同じ）。段 5 の単体テストで確かめる | U1 の基盤の設計のレビューの R-01 を先に避ける |
| `nfr-design/logical-components.md` 5節 | `.env.example` の書き込みの項目は後の段で決める | `.env` の項目が4つ、`.env.targetdb` の項目が1つ増える | Q1: A |
| `nfr-requirements/security-requirements.md` NFR4.16 | 付与を絞れるかを確かめる | 絞れる（試しの W5）。見本はスキーマ全体に付与し、残る広さと絞る書き方を README に書く。`bizPrepare` の種にも付与を足す | Q3: A |
| `README.md` の3か所（566行・588行、`.env.example` 43行） | 読み取りだけの記述 | 書き込みのアカウントの記述に直す（`infrastructure-specification.md` 6節） | 事実と合わなくなる |
| U1 の `infrastructure-design/infrastructure-specification.md` 2.1・2.3、`cicd-pipeline.md` 5.2（承認待ち） | perf の MySQL の上限 1792m（根拠は「プール以外 約 614 MiB」）。README の業務データの節に 1792m を書く | 2304m（プール以外は約 840 MiB） | 試しで崩れた（F1: A）。U1 の承認の場でそろえる |
| U1 の `infrastructure-design/cicd-pipeline.md` 5.3 の 8 | 片付けの前に `memory.peak`・OOMKilled を見る | 対象DB は「OOMKilled false、かつ `oom_kill` 0」で判断し、`memory.peak`・`anon` の最大・`memory.events` の `max` は記録だけ | F2: A |
| `nfr-design/security-design.md` 3節 | 書き込みのアカウントで表を作る操作が拒まれることの結合テスト | 実際の見本の初期化の台本で起動したコンテナで確かめる（3種類） | Q6: A |

## 9. 後の段に回すこと

- **コード生成の計画（B4）**
  - 名前（`infrastructure-specification.md` 1.1）と、設定・見本・README の直し
  - 3つの `03-writer-account.sh` と、`perf/dsl-timing.sh`・`perf/README.md` の直し
  - k6 の5場面と、setup の前提と、`bizPrepare` の付与（5.2）
  - SpotBugs の `SQL_` の除外を広げることの承認（BR13.3）
  - 名前つきの管理者の IT を、書き込みの部品より前の手順に置く（2.1）
  - 中継のテストを置く DB の種類（U1 と合わせる）
  - NFR 設計の承認の場の決定（U2 の R-01 デッドロックなどの分類は、レビューの推奨の方向で直す）
  - `AuditMigrationCompatibilityIT`・`AuditSecretLeakIT`・`AuditEventTest`、`targetdb` の設定を使う既存のテストの洗い出し
- **Deployment Pipeline・Deployment Execution**
  - `infrastructure-specification.md` 4節の手順の実地（初めての確かめ）と、V13 があるための配備の前のバックアップ
  - スモークテストの書き込みが、見本の PG の行と監査に残ることの伝え方と、期待の件数
- **Performance Validation**
  - 5.3 の実測と記録、合否の判定。Not Met は諮る
- **Observability Setup**
  - ダッシュボードの区画と、既存の警報の実測（`monitoring-design.md` 2節・5節）

## 10. U1・U3 とそろえる点

- **U1**
  - `bizPrepare` の種・perf の値と変数の名前・README の業務データの節を共用する。MySQL の上限だけを 2304m にそろえる（U1 の承認の場）。
  - 5.3 の流す順と記録をそろえ、対象DB のメモリの判断（F2: A）を U1 の場面にも当てる。
  - 中継のテストの DB の種類と `BusinessDataConnectionUsageIT` は、1つにまとめる。
  - 統合の前の E2E は、B2・B4 の両方で全体を流す。
- **U3**
  - E2E の書き込みのアカウントは 5.1 の形にし、U3 の基盤の設計に渡す（専用のスキーマ・管理者の資格情報の渡し方・040 の前提は U3 が決める）。
  - `SECRET_ENV_NAMES` に、書き込みと管理者の資格情報を足す。
  - 書き込みのアカウントに DDL を与えない決まりは、E2E のためにも緩めない。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/infrastructure-design/infrastructure-design-questions.md`（Q1〜Q7: A、F1: A、F2: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261016-business-data-views/` の下）
  - `construction/u2-businessdata-write/nfr-design/` の `performance-design.md`（4節・7節）・`security-design.md`（3節・9節・11節）・`scalability-design.md`（1節・8節）・`reliability-design.md`（9節）・`logical-components.md`（L11・L13・5節）
  - `construction/u2-businessdata-write/nfr-requirements/`（NFR4.3・NFR4.11・NFR4.16・NFR7.4・NFR7.5）
  - `construction/u2-businessdata-write/functional-design/rules.md`（BR13.3・BR13.4）
  - `construction/u1-businessdata-read/infrastructure-design/cicd-pipeline.md`（2節・5.2・5.3・10節）
  - `construction/nfr-design/memory.md`（承認の場の決定）
  - `.aidlc-reviews/infrastructure-design/units/u1-businessdata-read/4bcccff4724084fb/1.json`（R-01・R-02・R-04）
  - `.aidlc-reviews/nfr-design/units/u2-businessdata-write/df13e747868fc8fa/1.json`（R-01・R-06）
  - `construction/u3-businessdata-screens/nfr-design/security-design.md`（8節・後の段に回すこと）
  - `inception/delivery-planning/bolt-plan.md`（B4）
- 既にある仕組み（正とする。読むだけ）: `.github/workflows/ci.yml`・`perf/README.md`・`perf/k6/scenarios.js`・`perf/dsl-timing.sh`・`docker/perf/compose.yaml`・`docker/targetdb/`・`frontend/playwright.config.ts`（47〜62行）・`frontend/e2e/040-dsl-admin.e2e.ts`（21行）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture）、`project.md`（caffeinate -dims、片付けは確かめた後、待ちの最大は複数回、k6 の setup は前の状態に頼らない、CI の試行ごとの記録）
- 捨ての試し: `infrastructure-specification.md` 5節

## Assumptions & Open Questions

- 前提: 見本の初期化の台本の結合テストで増える3つのコンテナは、verify を約 1 分延ばすと見立てた。B4 の後に実測する。
- 前提: Testcontainers のコンテナで、`docker-entrypoint-initdb.d` に写した台本が初めての起動で動く（公式のイメージの仕組み）。台本の中の管理者のパスワードの変数は、テストの中で直接渡す。
- 前提: `bizPrepare` の種と `bizCreateLarge` を重ねても、2304m の MySQL は止まらない（試しはプールを埋めた状態で 1,607 MiB）。Performance Validation で F2: A の決まりで確かめる。
- 諮る点は無い。
