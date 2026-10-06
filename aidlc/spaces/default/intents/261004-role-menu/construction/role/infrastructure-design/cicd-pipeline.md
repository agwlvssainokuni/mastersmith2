# CI/CD Pipeline — U4 role

U4 の検査の流れ（CI と1コマンドの検査）、内部DB の移行、負荷の試験の台本・使い捨ての環境・データの用意、Tomcat の共通の設定の確かめ、E2E、統合、戻し方、B4〜B6 で確かめることを示します。

U4 は service の単位で、B4・B5・B6 の3つの Bolt で作ります。新しい依存・秘密・イメージは足さず、CI と1コマンドの検査の段・関門は **変えません**。この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。

この単位で増えるものは次のとおりです。

- 内部DB の移行2つ（3節）
- `application.yaml` の2行（ログは B6、Tomcat の読み捨ての上限は B1 で足す共通の設定。6節）
- k6 の場面と準備の台本（5節）
- テスト（2節）

配備先は開発者の PC 上のコンテナだけです（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A・Q2: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/role/nfr-design/performance-design.md`（2節）
  - `construction/role/nfr-design/scalability-design.md`（2節）
  - `construction/role/nfr-design/reliability-design.md`（2.5・2.6・4節・5節）
  - `construction/role/nfr-design/security-design.md`（2〜6節）
  - `construction/role/nfr-design/observability-design.md`
  - `construction/role/nfr-design/logical-components.md`（1節・5節）
  - `construction/role/nfr-requirements/tech-stack-decisions.md`（NFR6.2）
  - `construction/role/functional-design/functional-spec.md`
  - `inception/domain-design/components.md`
  - `inception/contract-design/contract-summary.md`（C3・C4・C5・C7・C8・C10）
  - `inception/delivery-planning/bolt-plan.md`（B4〜B6）
  - group の基盤の設計の成果物（`cicd-pipeline.md`）と、その読み直しの指摘 R-01〜R-05
- 既にある仕組み（正とする。読むだけ）:
  - `.github/workflows/ci.yml`
  - `build.gradle.kts`・`backend/build.gradle.kts`（`test` は `*Test`、`integrationTest` は `*IT` だけ）
  - `backend/src/main/resources/db/migration/`・`application.yaml`
  - `perf/README.md`（手順 2''）・`perf/k6/scenarios.js`（場面の一覧ごとの `setupTimeout`）
  - `docker/perf/compose.yaml`・`frontend/e2e/`

## 1. CI（GitHub Actions）

`.github/workflows/ci.yml` は変えません。

| 項目 | 今の形 | U4 での扱い |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | 変えない。CI は統合の後の再確認（`team.md` の Way of Working） |
| 実行 | `./gradlew verify`（`ubuntu-latest`、制限時間 60 分） | 変えない。上限切れと 10 MiB のテストで時間が延びる（7節） |
| サブモジュール・依存の入れ方 | 固定先のコミット、lockfile どおり | 変えない。U4 は lockfile を変えない（`JdbcTemplate` は今の spring-jdbc） |
| 秘密 | CI は秘密を使わない | 変えない（9節） |
| 成果物 | WAR をコミットのハッシュの名前で保存 | 変えない。WAR に移行のファイルが2つ増える |

CI が失敗したときは、次の Bolt に進む前に `team.md` の Testing Posture「不安定なテストと CI の失敗」の決まりで扱います。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

段・関門・Gradle のテストの振り分けは、増やさず変えません。どれか1つでも失敗したら全体を失敗とします。

| 段 | U4 で確かめること | 関門（失敗の条件） | 当たる要件 |
|---|---|---|---|
| 0〜3 準備・フォーマット・リンタ・ライセンスヘッダー | 足す Java と移行の SQL が既存の検査の対象になる | 既存のとおり | — |
| 4 ビルド | `role.*`（`role.store`・`role.transfer` を含む）と `audit` の変更のコンパイル | コンパイルの誤り | — |
| 5 単体テスト | `RoleStoreClassificationTest`、`RoleBoundaryArchitectureTest`、U1 の `ApiAccessArchitectureTest`、jqwik の4つ（`PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest`。名前の変更は 11節。失敗のときの乱数の種は既存の `exceptionFormat = FULL` で残る）、監査の種類の名前の長さ・detail の切り替えの単体テスト | 1件でも失敗 | NFR1.3・NFR1.5・NFR1.8・NFR5.5・NFR6.2・NFR6.5 |
| 6 結合テスト | `logical-components.md` 5節と `reliability-design.md` 4節の `*IT`（認可の表・一括代入・IDOR・同時の重なり・上限切れ・違反の読み替え・`RoleStoreTransactionsIT`・`RoleConnectionUsageIT`・`EffectivePermissionConsistencyIT`・`EffectivePermissionQueryCountIT`・`RoleAdminQueryCountIT`・`RoleTransferAtomicityIT`・`RoleTransferStaleIT`・`RoleTransferBusyIT`・`RoleTransferLimitsApiIT`・`RoleSecretLeakIT`・`RoleBusyLogTraceIT`・`WorkRoleSwitchAuditIT`・`RoleAuditWriteFailureIT`）、U1 の `ApiAccessConsistencyIT`、6節の Tomcat の確かめ | 1件でも失敗 | NFR1.1〜NFR1.11・NFR2.3・NFR2.6・NFR2.11・NFR3.1〜NFR3.6・NFR3.8・NFR5.1〜NFR5.6・NFR6.1 |
| 7 カバレッジ | 4節のとおり | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない | NFR6.4 |
| 8 安全の検査 | SpotBugs ＋ FindSecBugs（`SQL_` は priority にかかわらず止める。JDBC のバッチも名前の付いた引数）、OSV-Scanner、Gitleaks を除外なしで通す | 既存の基準 | NFR1.11・NFR6.6 |
| 9 成果物 | 既存の `bootWar`・`verifyDslSchemaInWar`・初回の読み込みの量 | 既存のとおり | — |

補足:

- 6段の結合テストは、組み込みの H2 の一時の内部DB だけで動きます。コンテナを使わないため、コンテナの実行環境が無くても飛ばされません。
- 同時の重なりは、4つの点の待ち合わせの口（`RoleBarrier`、テストでは `role/testsupport` の `@Primary` の部品）で、時間に頼らずに作ります（`reliability-design.md` 4.2）。

## 3. 内部DB の移行

| 項目 | 扱い | 出典 |
|---|---|---|
| 置き場と番号 | `backend/src/main/resources/db/migration/` の、その時点の次の空き番号。B3 の `V10__u3_group.sql` の後、B4 で1つ（見込み `V11__u4_role.sql`）、B5 で1つ（見込み `V12__u4_role_assignment.sql`） | 要件 NFR3.6、`reliability-design.md` 5節 |
| 中身 | B4: `roles`・`permission_settings`。B5: `user_role_assignments`・`group_role_assignments`・`work_role_selections`。表を足すだけで、`users.admin_flag` を残す。監査の表に列は足さない（U3 の3列を使う。NULL 可が前提） | `reliability-design.md` 5節、`infrastructure-specification.md` 3節 |
| 確かめ | 段 6 の起動で毎回当たる。移行の後に既存の行が読めることは、既存の結合テストがそのまま通ることで確かめる | 既存の Flyway の設定（変えない） |
| 前の版の起動 | 配備の段の戻しの練習（`infrastructure-specification.md` 4節）。Flyway の既定 `*:future` で V10〜V12 を無視して起動することを確かめる | group の読み直しの R-03 |

## 4. カバレッジ（段 7）

| 対象 | 今の扱い | U4 での扱い |
|---|---|---|
| `packagesJudgedByTotal`（7 パッケージ） | 全体の合計で判定する既存のパッケージの一覧 | 変えない。本体に手を入れる `role.*`・`audit.domain`・`audit.service` は一覧に無い。`access`・`auth`・`common.web` の本体には手を入れない |
| `role.*`（新しい） | 無い | 自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。B4 は `role.*` の一部だけを作るため、その Bolt の終わりで作ったパッケージが下限を満たす |
| 計測の除外 | 起動クラス・設定値だけのクラス・自動生成コード・`vendor/` | 増やさない |

実測は、各 Bolt で `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で行い、値を記録します（`team.md` の Testing Posture、`project.md` の学び）。

## 5. 負荷の試験（verify と CI の外）

### 5.1 持ち主

| 作業 | 持ち主 | 出典 |
|---|---|---|
| `roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps` の台本 | B4 | `performance-design.md` 2.1 |
| 台本の前の `k6 inspect --include-system-env-vars` と短い試し走り（`op` のタグが `iteration_duration` に付くか。付かなければ操作ごとに場面を分ける） | B4 | `performance-design.md` 2.2 |
| `workRoleSwitch`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`myPermissionsTree`・`rolePoolLimit` の台本 | B5 | `performance-design.md` 2.1、`scalability-design.md` 2.2 |
| `roleTransferLarge`・`roleExport` の台本と、上限ちょうど（10,485,760 バイト）の YAML の生成の部品（テストと k6 で共用） | B6 | `performance-design.md` 2.3 |
| 5.3 の準備の台本と `perf/README.md` の手順 | B6（照合の対象DB の要否は B4 の計画で確かめる） | この段の Q2: A |
| 節の数が上限に近い短い名前の YAML を1回測る | Build and Test | `performance-design.md` 2.3 |
| 使い捨ての環境で流して判定する | Performance Validation | `monitoring-design.md` 3節 |

### 5.2 使い捨ての環境

| 項目 | 扱い |
|---|---|
| 環境 | `docker/perf/compose.yaml`（変えない）。配備した環境のデータと監査ログには触れない。終わったら `down -v` で消す |
| 一時の `app.env` に足す値 | 合否の回は `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`、記録の回は `10`。どちらも `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`（使い捨てのアプリにだけ） |
| `acquire` の読み方 | `/actuator/metrics/hikaricp.connections.acquire` の `MAX` を秒で読み、20 ms は 0.020 と比べる。記録に単位を添える（`monitoring-design.md` 3節、group の読み直しの R-04） |
| 試験用の利用者 | `perf/README.md` の手順 2''（使い捨ての環境を止めて、H2 の道具で SQL で入れる）の形で入れる。対象は、メンバー 1,000 人のグループの利用者、100 のグループに属する利用者、VU ごとの利用者、操作する管理者（初期管理者は使わない）（group の読み直しの R-05） |
| `setupTimeout` | `setup()` の要求が多い場面を、既存の `INVITATION_SETUP_SCENARIOS`・`USER_ADMIN_SCENARIOS` と同じく、場面の一覧（例 `ROLE_SETUP_SCENARIOS`）に足して広げる（group の読み直しの R-05） |
| 判定 | `iteration_duration` の p95（`roleTransferLarge` は最大）と `checks` の率。`http_req_duration` は並べて記録する。台本全体を `caffeinate -i` で包む。手順 0 のとおり配備したアプリを止める |

### 5.3 悪い側のデータの用意（Q2: A）

`perf/` に準備の台本を足し、k6 の場面の前に1回流します。

1. **利用者**: 5.2 のとおり手順 2'' で入れる。
2. **DSL**:
   - 版 2 の DSL（100 テーブル × 100 カラム）を、B1 で版 2 に書き換える生成の部品（`perf/make-large-dsl.mjs` の考え方）で作る。
   - DSL の管理の API で投入し、プレビューを適用する。
   - 照合の対象DB が要るかは B4 の計画で確かめる。要るなら、使い捨ての環境の PostgreSQL の profile に同じ形の表を作る。
3. **ロールと権限**:
   - 生成した権限の YAML（ロール 1,000、うち1つは1万カラム明示。`roleTransferLarge` の置き換える先の 25 ロール）を、`/api/admin/role-transfer/check` → `/apply` で一度に入れる。
   - 上限ちょうどの YAML の生成の部品を使い回す。
4. **グループと割り当て**:
   - グループとメンバーは group の手順（`setup()` で API）で作る。
   - グループと利用者への割り当ては、場面の `setup()` で API を通して作る。

- 入るのはアプリの検証を通ったデータだけで、台本は表の形に縛られません。
- 準備は B6 の import の口に頼るため、B4・B5 の場面も、Performance Validation（全 Bolt の後）で流します。
- 準備の監査の行は、使い捨ての環境の中だけに残ります。

## 6. Tomcat の読み捨ての上限（Q1: A）

| 項目 | 扱い |
|---|---|
| 設定 | `application.yaml` に `server.tomcat.max-swallow-size` を足す（道ごとの上限の最大 10 MiB より少し大きい値、例 11MB）。アプリ全体に効く共通の設定で、先に作る B1（dsl-v2）で足す（`infrastructure-specification.md` 5節） |
| 目的 | 本文を読む前に断る応答（`RoleTransferSlot` の 409 `ROLE_BUSY`、`RequestSizeLimitFilter` の 413、dsl-v2 の 503 `DSL_BUSY`）を、10 MiB までの本文でも送り手に届ける。既定の 2 MB を超えて読まれない本文が残ると、Tomcat は接続を閉じる |
| B6 の確かめ | 実際の Tomcat（`@SpringBootTest` の `RANDOM_PORT`）で次を確かめる結合テストを足す（`RoleTransferBusyIT` に足すか別にするかは B6 の計画で決める） |
| B1 の確かめ | dsl-v2 の側は、B1 の短い試走で `DSL_BUSY` が届くことを確かめる（dsl-v2 の承認の場で同じ形にそろえる） |
| 受け入れること | 断る要求でも、サーバーは最大 10 MiB を受け取って読み捨てる（流れを読むだけで、ヒープに貯めない）。上限を大きく超える本文（例 100 MB）の 413 は、読み捨ての上限を超えるため、今までどおり接続を閉じる |

B6 の結合テストで確かめる内容は次の3つです。

- 1つ目の確かめを待ち合わせで止めたまま、10 MiB の2つ目を送ると、409 `ROLE_BUSY` の Problem Details を受け取れる。
- 上限を少し超える本文で、413 の Problem Details を受け取れる。
- そのあと同じ接続か新しい接続で、次の要求が通る。

## 7. 検査の時間

- `verify` の時間が延びる要因は次の2つです。
  - 上限切れのテスト: 行は 3 秒、一意の鍵・主キーは約 2 秒まで待ちます。
  - 上限ちょうどの YAML のテスト（10 MiB）と、Tomcat の確かめ（10 MiB を2回送る）。
- 目標の数値は置かず、各 Bolt の Build and Test で実測して記録します。CI の `timeout-minutes: 60` は変えません。
- テストの JVM のヒープ（1g）も変えません。上限ちょうどの YAML のテストで足りなければ、計画に無い変更として依頼者に確かめます。

## 8. E2E（`./gradlew e2eTest`、verify と CI の外）

- E2E は意図して `verify` と CI の外に置いています。代わりに、統合の前とリリースの前に手元で流します（`team.md` の Testing Posture）。
- B4・B5・B6 は、どれも認可（`ApiAccess`）と管理の API に手が入ります。B5 は作業ロールの切り替え（`/api/me/work-role`）も足します。そのため、各 Bolt の統合の前に、手元で `./gradlew e2eTest` の全体を流します。
- U4 のために E2E のファイルは足しません。画面は U6・U7 が作ります（要件 NFR6.3 は N/A）。

## 9. 統合・配備・戻し方・秘密

| 項目 | 扱い | 出典 |
|---|---|---|
| 作業ブランチ | Bolt ごとに `develop` から作る短命のブランチ（例 `feature/261004-role-menu-b4`）。worktree は使わない | `team.md` の Way of Working |
| 統合の前の関門 | `./gradlew verify`（コンテナの実行環境あり）と E2E の全体（8節） | `team.md` |
| 統合の形 | Bolt ごとに `develop` へ squash（B4・B5・B6 でそれぞれ1コミット）。サブモジュールの更新は無い | `team.md`、`project.md` の学び（大きい単位はコード生成の計画で Bolt に分ける） |
| プッシュ | 依頼者自身が行う | `team.md` |
| 配備 | 既存の手順のまま。起動で移行が当たる | `infrastructure-specification.md` 1節 |
| 配備の段への引き継ぎ | V10〜V12 を合わせて、入れ替えの前の内部DB の複写と前の版での戻しの練習を1回。戻した版での管理の可否のずれの確かめ | `infrastructure-specification.md` 4節 |
| 戻し | 直前の版のイメージで起動し直す（イメージだけ） | `reliability-requirements.md` の「障害と戻し」 |
| 秘密 | 足さない。CI に秘密を渡さない。負荷の試験の資格情報は、今までどおりリポジトリの外の一時の環境ファイル（値は乱数、表示しない）で渡す。試験の YAML とデータに個人に関する値を置かない（予約のドメインだけ）。Gitleaks の除外を足さない | `perf/README.md`、`project.md` の Forbidden |
| 依存 | 新しい依存を足さない | `logical-components.md`（NFR6.6） |

## 10. B4〜B6 で確かめること

| Bolt | 確かめ | 成り立たないとき |
|---|---|---|
| B4 | 移行の番号が次の空き番号で重ならない。jqwik のテストが `*PropertyTest` の名前で段 5 で実際に動いた（テストの報告に出る）。`op` のタグの確かめの結果 | 番号・名前を直す。タグが付かなければ場面を分ける |
| B4 | 照合の対象DB が、5.3 の DSL の準備に要るかを計画で確かめる | 要るなら PostgreSQL の profile の表を準備の台本に入れる |
| B5 | 移行の番号、`RoleConnectionUsageIT` の本数（書き込み 2・読み取り 1）、`rolePoolLimit` の台本 | 原因を直す。目安は緩めない |
| B6 | `application.yaml` の `StatementCreatorUtils: OFF` と `RoleSecretLeakIT`。6節の Tomcat の確かめ（B1 で設定が入っていることを含む）。10 MiB の YAML の生成の部品と準備の台本が `k6 inspect` で読める | 設定が無ければ足す（B1 で入っていなければ、この Bolt で足して差を記録する）。台本を直す |
| 各 Bolt | カバレッジの実測、`verify` の時間の延び、E2E の全体、依存・lockfile・`packagesJudgedByTotal`・`docker/monitoring/`・compose が変わっていないこと | 除外を増やさずテストを足す。元に戻す |

## 11. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `logical-components.md` 5節（B6）・`tech-stack-decisions.md`（NFR6.2）の jqwik のテストの名前 | `PermissionInheritanceProperties`・`RoleNameKeyProperties`・`WorkRoleResolutionProperties`・`RoleTransferRoundTripProperties` | `PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest` | `backend/build.gradle.kts` の `test` は `*Test`、`integrationTest` は `*IT` だけを流すため、`Properties` で終わる名前は一度も動かない（group の読み直しの R-01）。既存の jqwik のテストの形（`SafeYamlParserPropertyTest` など）にそろえ、Gradle の振り分けは変えない |
| 要件・NFR 設計（Tomcat の設定の記述は無い） | 本文を読む前に断る（`reliability-design.md` 2.6） | B1 で `server.tomcat.max-swallow-size` を足し、B6 で実際の Tomcat で確かめる（6節） | Q1: A |
| 要件 NFR2.1・NFR2.2・NFR2.4・NFR2.5（「台本とデータの用意は Code Generation」） | データの用意の仕方は決めていない | DSL は DSL の管理の API、ロールと権限は role の import の API で一度に入れる。利用者は手順 2''（5.3） | Q2: A。1万カラムを API の保存で入れると 2,500 回の要求になるため |
| `scalability-design.md` 2.2 | `acquire` の最大 20 ms 未満（単位の読み方は書いていない） | `/actuator/metrics` の `MAX` を秒で読み、0.020 と比べる（5.2） | group の読み直しの R-04 の手当て。合否の値は変えていない |
