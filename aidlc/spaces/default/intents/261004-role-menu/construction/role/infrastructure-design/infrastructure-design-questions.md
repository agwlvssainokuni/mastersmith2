# 基盤の設計の質問 — U4 role

単位 U4 role（kind: service。B4・B5・B6 の3つの Bolt に分けて作る）の基盤の設計の前に、決まっていない点を確かめます。作る成果物は、段の定義の `produces_kinds` により `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` の4つです。

読んだもの:

- この単位の承認済みの NFR 設計の7つ: `nfr-design/performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`。各ファイル末尾の「読み直し1回目の直し」「承認の場の決定と直し」も読んだ。
- NFR 要件（`nfr-requirements/`）、機能設計（`functional-design/functional-spec.md`・`entities.md`）
- `inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C3・C4・C5・C7・C8・C10）、`inception/delivery-planning/bolt-plan.md`（B4〜B6）
- group の基盤の設計の成果物（読み取りだけ）と、その読み直しの指摘（R-01〜R-05）
- 既存のもの（読み取りだけ）:
  - `.github/workflows/ci.yml`、`backend/build.gradle.kts`（`test` は `*Test`、`integrationTest` は `*IT` だけ）
  - `db/migration/`（V1〜V9）
  - `application.yaml`（ログの設定・Hikari・Flyway・Tomcat）
  - `common/web/RequestSizeLimitFilter.java`
  - `compose.yaml`・`docker/perf/compose.yaml`・`docker/monitoring/`
  - `perf/README.md`（手順 2''）・`perf/k6/scenarios.js`（場面の一覧ごとの `setupTimeout`）
  - `backend/gradle.lockfile`（Flyway 12.4.0）
- `team.md`・`project.md`

## 決まっていること（質問にしない）

### 配備と基盤の範囲

- 配備先が決まるまで、基盤の設計は開発者の PC 上のコンテナの範囲に限ります。クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません（`project.md` の Deployment）。
- 次のものは変えません。
  - 配備の形（1つのアプリのコンテナ、組み込みの H2、単一のインスタンス）
  - `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`.env.example`
  - 接続プール（上限の既定 30・借りる待ち 5000 ms）
- 新しい依存・秘密・外への接続は足しません。`JdbcTemplate` は main では初めて使う書き方ですが、今の依存（Spring Boot の JDBC）に含まれています（`performance-design.md` 4節）。
- `RoleTransferSlot`（確かめと適用を同時に1つずつ）は、単一のインスタンスの前提で、アプリの中の1つで足ります（`reliability-design.md` 2.6）。

### application.yaml の変更（設計で決まっている1行）

- B6 で `logging.level` に `org.springframework.jdbc.core.StatementCreatorUtils: OFF` を足します（`reliability-design.md` 2.5）。
  - 既存の `org.hibernate.orm.jdbc.error: OFF` と同じ書き方で、なぜ・障害の調べ方をコメントに書きます。
  - `RoleSecretLeakIT` が TRACE を `org.springframework.jdbc` にも広げて、YAML の目印が 0 件であることを確かめます。
- ほかのログ・Hikari・Flyway の設定は変えません。Tomcat の設定は Q1 で決めます。

### 内部DB の移行・戻し

- 移行は、コード生成の時点の次の空き番号で、Bolt ごとに1つずつです。今の見込みは次の2つです（`reliability-design.md` 5節）。名前は既存の形 `V<番号>__u<単位の番号>_<内容>.sql` に合わせます。
  - B3（group）の `V10__u3_group.sql` の後、B4 で `roles`・`permission_settings`（見込み `V11__u4_role.sql`）
  - B5 で `user_role_assignments`・`group_role_assignments`・`work_role_selections`（見込み `V12__u4_role_assignment.sql`）
- 中身は表を足すだけで、既存の列を消しません。`users.admin_flag` も残します（`team.md` の Deployment の「広げてから縮める」）。
- 監査の表は、U3 が足した3列（`target_role_id`・`target_group_id`・`detail`）を使います。role は列を足さず、種類と失敗の理由の値（文字列の列）だけを足します（`observability-design.md` 1節）。
  - group の読み直しの R-02 の手当て: 3列は NULL 可でなければなりません。前の版のアプリは3列を知らないまま監査の行を書くためです。U3 の移行でそうなっていることを、role の側の前提として書きます。
  - 前の版のアプリは監査の行を書くだけで読まないため（`AuditEventRecorder` の書き込みだけ）、知らない種類の値の行があっても起動と動作に影響しません。
- 前の版のアプリが V10〜V12 の後の内部DB で動く理由は次の2つです。
  - `ddl-auto: validate` は余分な表と列を許します。
  - group の読み直しの R-03 の手当て: Flyway（12.4.0）の `validate-on-migrate: true` のもとで、前の版が知らない新しい移行（future）は既定の `ignore-migration-patterns`（`*:future`）で無視されます。今の `application.yaml` にこの既定を変える設定はありません。
  - この前提は、配備の段の戻しの練習で、前の版のイメージが移行の後の内部DB の複写で起動しヘルスチェックが通ることによって確かめます。
- 戻しは直前の版のイメージだけで行います。ロール・設定・割り当て・作業ロールの保存は残り、上げ直すとそのまま使えます（`reliability-requirements.md` の「障害と戻し」）。
- **配備の段への引き継ぎ**: 入れ替えの前の内部DB の複写（バックアップを兼ねる）と前の版のイメージでの戻しの練習は、U3 と U4 の移行（V10〜V12）を合わせて1回で扱います（group の `infrastructure-specification.md` 4節）。

### CI と1コマンドの検査（既にあるものの記録）

- CI・Gradle のタスク・`packagesJudgedByTotal` は変えません。本体に手が入る `role.*`（新しい。`role.store`・`role.transfer` を含む）・`audit.domain`・`audit.service` は、パッケージごとの下限がそのまま当たります（`logical-components.md` 1節）。
- **テストの名前**（group の読み直しの R-01 の手当て）:
  - 承認済みの設計の jqwik のテスト4つは、名前が `Properties` で終わります: `PermissionInheritanceProperties`・`RoleNameKeyProperties`・`WorkRoleResolutionProperties`・`RoleTransferRoundTripProperties`。
  - このままでは、`test`（`*Test`）にも `integrationTest`（`*IT`）にも当たらず、一度も動きません。
  - 既存の jqwik のテストの形（`SafeYamlParserPropertyTest`・`DslGenerationPropertyTest` など）にそろえ、`PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest` の名前で `test`（段 5）に入れます。
  - Gradle のテストの振り分けは変えません。名前の変更は、承認済みの設計との差としてこの段の成果物に書きます。
- **足すテストの振り分け**:
  - `*Test` は段 5: 構造の検査、`RoleStoreClassificationTest`、上の4つ。
  - `*IT` は段 6（組み込みの H2 だけ、コンテナを使わない）: `RoleConnectionUsageIT`・`RoleTransferAtomicityIT`・`RoleTransferBusyIT`・`RoleTransferLimitsApiIT`・`EffectivePermissionConsistencyIT`・`EffectivePermissionQueryCountIT`・`RoleSecretLeakIT` など。
  - 上限切れのテスト（行 3 秒・鍵 約 2 秒）と、上限ちょうど（10 MiB）の YAML のテストで `verify` の時間が延びます。Build and Test で `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測して記録します。
  - テストの JVM のヒープ（1g）は変えません。上限ちょうどの YAML のテストでヒープが足りなければ、計画に無い変更として依頼者に確かめます。
- 統合は Bolt ごとに短命のブランチから `develop` へ squash で戻します（B4・B5・B6 でそれぞれ1コミット）。
- どの Bolt も認可（`ApiAccess`）と管理の API に手が入ります。B5 は作業ロールの切り替え（`/api/me/work-role`）も足します。そのため、各 Bolt の統合の前に手元で `./gradlew e2eTest` の全体を流します。U4 のために E2E のファイルは足しません（画面は U6・U7）。

### 負荷の試験（k6）と使い捨ての環境

- 台本は各 Bolt のコード生成が書き、流すのは Performance Validation です（`performance-design.md` 2節、`scalability-design.md` 2.2）。
  - B4: `roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps` など
  - B5: `workRoleSwitch`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`myPermissionsTree`・`rolePoolLimit`
  - B6: `roleTransferLarge`・`roleExport`
- B4 の台本の前に、`k6 inspect --include-system-env-vars` と短い試し走りで、`op` のタグが `iteration_duration` に付くかを確かめます。付かなければ、操作ごとに場面を分けます（`performance-design.md` 2.2）。
- 使い捨ての環境（`docker/perf/compose.yaml`、変えない）で流し、配備した環境のデータと監査ログには触れません。
- 接続プールの値は、一時の `app.env` で渡します。
  - 合否の回は `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`、記録の回は `10`。
  - どちらも `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`。
- **`acquire` の単位**（group の読み直しの R-04 の手当て）:
  - 合否の `hikaricp.connections.acquire` の最大は、使い捨てのアプリの `/actuator/metrics/hikaricp.connections.acquire` の `MAX` で読みます。外部エクスポートは無効のままです。
  - Micrometer のタイマーはこの口では秒（`baseUnit: seconds`）で返るため、20 ms は 0.020 として比べ、記録には単位を添えます。
  - 手元の監視（OTLP で送った系列）での名前と単位は、それとは別に Observability Setup で起動して確かめます。
- **試験用の利用者と `setupTimeout`**（group の読み直しの R-05 の手当て）:
  - 試験用の利用者（メンバー 1,000 人のグループ、100 のグループに属する利用者、VU ごとの利用者など）は、`perf/README.md` の手順 2''（使い捨ての環境を止めて、H2 の道具で SQL で入れる）の形で入れます。
  - `setup()` の要求が多い場面は、既存の `INVITATION_SETUP_SCENARIOS`・`USER_ADMIN_SCENARIOS` と同じく、場面の一覧（例 `ROLE_SETUP_SCENARIOS`）に足して `setupTimeout` を広げます。
  - ロール・権限の設定・DSL の用意の仕方は Q2 で決めます。
- 判定は `iteration_duration` の p95（`roleTransferLarge` は最大）と `checks` の率です。台本全体を `caffeinate -i` で包みます。
- Build and Test で、節の数が上限に近い短い名前の YAML を1回測ります（`performance-design.md` 2.3）。

### 監視・ログ・SLO（group の Q1 A にそろえる）

- 独自の指標は足しません。role の API は既存の `http.server.requests` に入り、`slo` のバケットで p95 を計算できます（`observability-design.md` 2節）。
- 新しい警報は足しません。拾うのは、既存の全体の `ms-5xx-ratio`・`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` です。role の API の p95 を拾う警報が無いことは、承認済みの設計（`observability-design.md` 4節、読み直しの R-09）が受け入れた制約と同じです。group と違い、上流との食い違いはありません。
- group の Q1 A のダッシュボードの区画（この Intent の管理の API）に、role の `uri`（`/api/admin/roles.*`・`/api/admin/role-transfer/.*`・`/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles`・`/api/me/work-role`・`/api/me/permissions/.*`）を足します。区画を書いて式を確かめるのは Observability Setup で、B4〜B6 は `docker/monitoring/` に触れません。
- SLO（p95 1 秒、import の確かめ 15 秒・適用 30 秒）は、手元の監視を常に動かしていない間は `Unverified` とし、Performance Validation と配備の後の値を基準の値として記録します（`project.md` の学び）。
- ログは `ROLE_BUSY` の WARN だけを足します（`lockKind` の6つ、`ROLE_TRANSFER_SLOT` を含む）。

## Q1 本文を読まずに断るときに、大きな本文で応答が届かない回の扱い（Tomcat の max-swallow-size）

### 背景

確かめと適用（`POST /api/admin/role-transfer/check`・`/apply`）は、本文を読む前に次の2つで断ります。

- `RoleTransferSlot` の `tryAcquire()`: 取れなければ 409 `ROLE_BUSY`（`reliability-design.md` 2.6）
- 既存の `RequestSizeLimitFilter`: `Content-Length` が上限を超えれば 413

このとき、読まれなかった本文（確かめ・適用は最大 10 MiB）が Tomcat に残ります。Tomcat は、残った本文を `server.tomcat.max-swallow-size`（既定 2 MB）までしか読み捨てず、超えると接続を閉じます。そのため、送り手が本文を送り終える前に接続が閉じられ、409 の応答を受け取れない回がありえます。この挙動は Tomcat の決まりから読んだもので、実際に起きることはまだ確かめていません。

- dsl-v2 の基盤の設計の読み直し（R-01 Major）で、同じことが `DslHeavyOperationGate` の 503 `DSL_BUSY` について指摘され、dsl-v2 の承認の場で扱います。
- 今の `application.yaml` に Tomcat の `max-swallow-size` の設定は無く、既定の 2 MB です。
- role の画面（U6）は `ROLE_BUSY` のとき「ほかの読み込みが動いている。少し待ってからもう一度」と案内する設計です。接続が閉じられると、利用者には案内ではなく通信の誤りが出ます。
- `RoleTransferBusyIT` の2つ目の要求が小さな本文なら、テストはこれに気づきません。

設定はアプリ全体（DSL の管理を含む）に効くため、dsl-v2 の承認の場の決定とそろえます。

### 選択肢

A. **`application.yaml` に `server.tomcat.max-swallow-size` を足し、確かめる（推奨）**
  - 道ごとの上限の最大（10 MiB）より少し大きい値（例 11MB）にして、10 MiB までの本文は読み捨ててから断りの応答を返し、接続を保ちます。
  - 足すのは、DSL と role の先に作る方の Bolt です。dsl-v2 の承認の場で同じ形に決まれば B1、そうでなければ B6 で足します。
  - B6 に、実際の Tomcat（`RANDOM_PORT`）で、1つ目を待ち合わせで止めたまま 10 MiB の2つ目を送り、409 `ROLE_BUSY` の Problem Details を受け取れることを確かめる結合テストを足します。上限を超える本文の 413 も同じ形で確かめます。
  - 理由: 設定1行で DSL と role の両方の案内を守れます。断る側のコード（本文を読む前に断る作り）を変えずに済みます。読み捨ては流れを読むだけでヒープを使わないため、`RoleTransferSlot` の目的（メモリの不足の広がりを防ぐ）を崩しません。代わりに、断る要求でもサーバーが最大 10 MiB を受け取ります。
  - 上限を大きく超える本文（例 100 MB）の 413 は、読み捨ての上限を超えるため、今までどおり接続を閉じます。これは受け入れます。

B. **断る前に、本文をアプリの中で読み捨てる**
  - `RoleTransferService` が `ROLE_BUSY` を返す前に、道ごとの上限まで本文の流れを読んで捨てます（ヒープに貯めない）。
  - 設定は変えず、role の確かめと適用だけに効きます。DSL の `DSL_BUSY` と、`RequestSizeLimitFilter` の 413 は別に手当てが要ります。

C. **今のまま受け入れる**
  - 接続が閉じられたときは、画面で通信の誤りとして扱い、利用者にやり直してもらいます。
  - 受け入れた制約として記録し、`RoleTransferBusyIT` は小さな本文で確かめます。
  - 作業は無い代わりに、U6 の `ROLE_BUSY` の案内が出ない回が残ります。

X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 負荷の試験のロール・権限の設定・DSL の用意の仕方

### 背景

承認済みの NFR 要件（NFR2.1・NFR2.2・NFR2.4・NFR2.5）の悪い側のデータは大きく、承認済みの文書は「台本とデータの用意は Code Generation」とだけ書いています。

- ロール 1,000（うち1つは1万カラムすべてに明示の値）
- `roleTransferLarge` の置き換える先のロール 25 個（各1万カラム明示）
- 100 テーブル × 100 カラムの版 2 の DSL（権限の対象は適用済みの DSL から出る）

権限の設定の API は、テーブルの1階層を保存する形（100 カラムの表を1回）です。1万カラムを API で入れるには、ロールごとに 100 回、25 ロールで 2,500 回の保存が要ります。group のように `setup()` で API を通して作ると、`setupTimeout` を大きく広げても時間がかかり、試験の前の監査の行も増えます（使い捨ての環境のため害はありません）。

DSL は、B1 で版 2 に書き換える負荷の道具（`perf/make-large-dsl.mjs` など）で作れます。ただし、プレビューと適用で照合に使う対象DB を、使い捨ての環境（PostgreSQL の profile）にどう置くかは、B1 の書き換えに頼ります。

### 選択肢

A. **DSL は DSL の管理の API、ロールと権限は role の import の API で入れる（推奨）**
  - 準備の台本（`perf/` に足す、k6 の場面の前に1回流す）が、次の順で入れます。
    1. 版 2 の DSL を生成の部品で作り、DSL の管理の API で投入・適用する。照合の対象DB の要否は B4 の計画で確かめ、要るなら PostgreSQL の profile に同じ形の表を作る。
    2. ロールと1万カラムの設定を、生成した権限の YAML で、確かめ → 適用（`/api/admin/role-transfer/check`・`/apply`）で一度に入れる。
  - 理由: アプリの検証を通った正しいデータだけが入り、表の形に台本が縛られません。試しの見通しで適用は数秒です（`performance-design.md` 2.3）。上限ちょうどの YAML の生成の部品（テストと k6 で共用、`performance-design.md` 2.3）を使い回せます。
  - 代わりに、準備が B6 の import の口に頼るため、B4・B5 の場面を Performance Validation で流すのは B6 の後になります（もともと Performance Validation は全 Bolt の後）。

B. **すべてを SQL で内部DB に直接入れる**
  - 手順 2'' と同じく、使い捨ての環境を止めて H2 の道具で、ロール・権限の設定・DSL の履歴の行を入れます。
  - 速くて import の口に頼りません。ただし、表と列の形（名前の鍵の作り方を含む）と DSL の保存の形に台本が縛られ、アプリの検証を通らない行が入りえます。DSL の適用中の状態を SQL で作るのは、起動時の読み直し（dsl-v2）の形にも頼ります。

C. **`setup()` で API を通して1つずつ作る**（group と同じ形）
  - 承認済みの group の形にそろいますが、1万カラムのロール 25 個の保存だけで 2,500 回の要求になります。`setupTimeout` を数十分に広げることになり、試験の時間が長くなります。

X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（role の基盤の設計）:

- Q1 A: `application.yaml` に `server.tomcat.max-swallow-size`（例 11MB）を足し、本文を読まずに断る応答（`ROLE_BUSY`・413、dsl-v2 の `DSL_BUSY`）が大きな本文でも届くようにする。アプリ全体に効く設定のため DSL と role で共通にし、先に作る Bolt（B1）で足す。B6 に、実際の Tomcat で 10 MiB の2つ目を送って 409 を受け取れることを確かめる結合テストを足す（dsl-v2 の側の確かめは B1 の短い試走）。dsl-v2 の基盤の設計の承認の場で同じ形にそろえる。
- Q2 A: 負荷の試験の悪い側のデータは、DSL を DSL の管理の API で投入・適用し、ロールと権限を role の import の API で一度に入れて用意する。照合の対象DB が要るかは B4 の計画で確かめる。
- 先に手当てした group の読み直しの指摘（jqwik のテスト4つを `*PropertyTest` に直して段 5 に入れる、監査の表の3列は NULL 可が前提で role は列を足さない、前の版の Flyway は既定 `*:future` で知らない移行を無視し戻しの練習で起動を確かめる、acquire は `/actuator/metrics` の MAX を秒で読み 20 ms は 0.020 と比べる、試験用の利用者は perf/README の 2'' の形で setupTimeout は場面の一覧に足す）と、決まっていること（移行は V11・V12 で表を足すだけ、配備の段で V10〜V12 を合わせて複写と戻しの練習を1回、B6 で `StatementCreatorUtils: OFF`、CI・Gradle・`packagesJudgedByTotal`・compose・依存・秘密は変えない、B4・B5・B6 ごとに squash で統合し各統合の前に E2E の全体、k6 の台本は各 Bolt が書き Performance Validation が流す、監視は group の Q1 A にそろえる）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
