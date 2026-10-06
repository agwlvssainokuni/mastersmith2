# 基盤の仕様 — U4 role

U4 role は service の単位で、B4・B5・B6 の3つの Bolt で作ります。担う範囲は次のとおりです。

- ロールの管理と、権限の設定・木の API（契約 C7）
- 利用者・グループへの割り当て、作業ロール、自分の権限の API（C7・C8）
- 権限の解決の口（C5）
- 権限の YAML の書き出し・確かめ・適用（C7）

既存のアプリのコンテナと組み込みの H2 だけを使います。増やすのは次の3つです。

- 内部DB の移行2つ（B4 と B5）
- `application.yaml` のログの1行（B6）
- Tomcat の読み捨ての上限の1行（Q1: A。DSL と共通の設定で、B1 で足す）

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・検証環境は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、Q2: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/role/nfr-design/` の7つ: `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`
  - `construction/role/nfr-requirements/`（`reliability-requirements.md` の「障害と戻し」）
  - `construction/role/functional-design/functional-spec.md`・`entities.md`
  - `inception/domain-design/components.md`（RoleManagement・GroupManagement・DslDefinition・AuditLog・UserAccount）
  - `inception/contract-design/contract-summary.md`（C3・C4・C5・C7・C8・C10）
  - `inception/delivery-planning/bolt-plan.md`（B4〜B6）
  - group の基盤の設計の成果物（移行の番号、配備の段への引き継ぎ、ダッシュボードの区画）
- 既存のもの（正とする。読むだけ）:
  - `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`
  - `backend/src/main/resources/application.yaml`・`backend/src/main/resources/db/migration/`（V1〜V9）
  - `common/web/RequestSizeLimitFilter.java`
  - `backend/gradle.lockfile`（Flyway 12.4.0）

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のアプリのコンテナ1つ（実行可能 WAR、`MaxRAMPercentage=50.0`、ヒープ約 1 GiB）。変えない | role は既存のアプリの中の機能 |
| Instances | 単一のインスタンス | 組み込みの H2 のため。`RoleTransferSlot`（`Semaphore(1)`）はアプリの中の1つで足りる（`reliability-design.md` 2.6） |
| Networking | 既存のまま（8080 だけ、同じオリジン）。API は `/api/admin/roles`・`/api/admin/role-transfer`・`/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles`・`/api/me/work-role`・`/api/me/permissions` の下 | 外への呼び出しは無い（`logical-components.md` 3節） |
| Storage | 既存の名前付きボリューム（内部DB のファイル）。表が増えるだけ | `reliability-design.md` 5節 |
| Environments | 配備は `compose.yaml`、負荷の試験は使い捨ての環境（`docker/perf/compose.yaml`）、CI はテストだけ | `team.md` の Deployment |
| IaC approach | 作らない。compose・`Dockerfile` は変えない | 配備先が決まっていない |
| Resource sizing | 既存のまま（アプリ 2g・CPU 4、colima の VM CPU 4・メモリ 6GiB） | 上限の 96% の import の試しで、確かめは 1 秒未満・適用は 4 秒未満。確かめと適用を同時に1つにし、ヒープの重なりを防ぐ（`scalability-design.md` 1節） |
| Configuration | `application.yaml` に2行。B6 で `logging.level.org.springframework.jdbc.core.StatementCreatorUtils: OFF`。B1 で `server.tomcat.max-swallow-size`（Q1: A、5節）。ほかの設定・`.env.example` は変えない。新しい環境変数・秘密は無い | `reliability-design.md` 2.5、この段の Q1 |
| Schema migration | Flyway の移行を2つ（B4・B5）。番号はコード生成の時点の次の空き番号（今の見込み `V11__u4_role.sql`・`V12__u4_role_assignment.sql`）。前進のみ（3節） | 要件 NFR3.6、`reliability-design.md` 5節 |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。足した表は前の版から見えないだけで、データは残る | `reliability-requirements.md` の「障害と戻し」 |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（組み込みの H2） | database | B4: `roles`（名前 128・鍵 256 で一意）、`permission_settings`（主キー `(role_id, schema_name, table_name, column_name)`、対象の名前 256）。B5: `user_role_assignments`・`group_role_assignments`・`work_role_selections`。`audit_events` は U3 が足した3列を使い、列を足さない | 行の排他は問い合わせのヒント 3,000 ms。一意の鍵・主キーの待ちは H2 の既定の約 2,000 ms（`reliability-design.md` 2.1）。`group_role_assignments` は U3 の `groups` を外部キーで指す |
| JDBC のバッチ（`JdbcTemplate`） | database（書き込みの道） | import の適用だけで使う。1,000 件ずつ、`setQueryTimeout(10)`、JPA と同じ接続とトランザクション。`StatementCreatorUtils` のロガーを OFF | main では初めて使う書き方。依存は今の lockfile の spring-jdbc（`performance-design.md` 2.3・4節） |
| 接続プール（HikariCP `mastersmith-db`） | database（接続） | 変えない（上限の既定 30・借りる待ち 5000 ms） | 書き込みは2本使い、読み取りは1本、import の適用は2本（`scalability-design.md` 2.1） |
| Flyway | database（移行） | 変えない（`validate-on-migrate: true`、既定の `ignore-migration-patterns` `*:future`） | 前の版のアプリは、自分の知らない V10〜V12 を無視して起動する（3節） |
| Tomcat（埋め込み） | web サーバー | `server.tomcat.max-swallow-size` を足す（例 11MB。B1、Q1: A） | 本文を読まずに断る応答（`ROLE_BUSY`・413）を、10 MiB までの本文でも送り手に届ける（5節） |
| 監査の記録（`AuditEventListener`） | 既存の部品 | 受け取りと、種類・失敗の理由の値を足す。U3 の列とファクトリーを使う | 契約 C10 |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | `snapshotFor` は試しで p95 3 ms のため、キャッシュを置かない（`performance-design.md` 1節） |

## 3. 移行と戻しの扱い

| 項目 | 扱い | 出典 |
|---|---|---|
| 番号 | V10（U3）の後の空き番号。B4 と B5 で1つずつ。名前は既存の形 `V<番号>__u<単位の番号>_<内容>.sql` | 要件 NFR3.6、group の `infrastructure-specification.md` 3節 |
| 中身 | 表を足すだけ。既存の列を消さず、`users.admin_flag` も残す（広げてから縮める二段の「広げる」側） | `team.md` の Deployment、`reliability-design.md` 5節 |
| 監査の表 | 列を足さない。U3 が足した3列（`target_role_id`・`target_group_id`・`detail`）を使う。前の版のアプリは3列を知らずに監査の行を書くため、3列は NULL 可であることが前提（U3 の移行の決まり。group の読み直しの R-02） | `observability-design.md` 1節 |
| 前の版の起動 | `ddl-auto: validate` は余分な表を許す。Flyway の `validate-on-migrate: true` のもとでも、前の版が知らない新しい移行（future）は既定の `*:future` で無視される。今の `application.yaml` にこの既定を変える設定は無い（group の読み直しの R-03） | Flyway 12.4.0（`backend/gradle.lockfile`） |
| 監査の種類の値 | role が足す種類と失敗の理由の値は、文字列の列に入る。前の版のアプリは監査の行を書くだけで読まない（`AuditEventRepository` は `save` だけ）ため、知らない値の行があっても動く | コード（`audit/repository/AuditEventRepository.java`） |
| 戻した後 | 前の版にはロールの機能が無い。作業ロール・権限は効かず、管理の可否は `users.admin_flag` で決まる。上げ直すと、ロール・設定・割り当て・作業ロールの保存はそのまま使える | `reliability-requirements.md` の「障害と戻し」 |

## 4. 配備の段への引き継ぎ

中身は deployment-pipeline・deployment-execution の段で決めます。U3 と U4 の移行（V10〜V12）を合わせて、次を1回で扱います（group の `infrastructure-specification.md` 4節）。

1. **入れ替えの前の内部DB の複写**: アプリを止めて複写します。バックアップを兼ねます。
2. **戻しの練習**: 前の版のイメージを、移行の後の内部DB の複写と一時のボリュームで起動し、`/actuator/health` の 200 を待って確かめます。これで、Flyway が V10〜V12 を無視して起動することも確かめます。
3. **戻したときの権限**: 戻した版での管理の可否は、`users.admin_flag` の今の値で決まります。戻す直前のロールの割り当てとずれがないか（`team.md` の Deployment の戻しの確かめ）を確かめる手順を入れます。
4. **監査の確かめ**: 配備の後のスモークテストでロールの操作を行うなら、監査に残るため、前もって依頼者に伝えます。

## 5. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| `server.tomcat.max-swallow-size`（`application.yaml`、Q1: A） | B1（U2 dsl-v2）で足す共通の設定 | U2 dsl-v2（`DSL_BUSY`・413）、U4 role（`ROLE_BUSY`・413）、本文の大きさの上限を持つ既存の道すべて | 値は道ごとの上限の最大（10 MiB）より少し大きい値。役目は読み捨てだけで、ヒープに貯めない。上限を大きく超える本文の 413 は、今までどおり接続を閉じる。確かめは U4 の B6 の結合テスト（実際の Tomcat で 10 MiB の2つ目）と、B1 の短い試走 |
| 内部DB の `groups` と `GroupMembershipQuery` の口 | U3 group | U4 role | role は `group.service` の口（`groupIdsOfUser`・`exists`・`summaries`・`memberUserIds`・`lockForAssignment`）だけを使い、`group.store`・`group.repository` に触れない（`RoleBoundaryArchitectureTest`、契約 C4） |
| `GroupDeletionGuard` の実装 | U3 group（インターフェース） | U4 role が実装（`RoleGroupDeletionGuard`、B5 で仮の実装を置き換える） | 依存の向きは role → group だけ |
| 権限の解決の口（`EffectivePermissionResolver`、契約 C5） | U4 role | U5 navigation | 読み取りだけ。2つ以上の対象は `snapshotFor` を1回、対象1つは `resolve` を1回（`logical-components.md` 4節・6節） |
| DSL の写し（`ActiveDslModelProvider`）と `SafeYamlReader` | U2 dsl-v2 | U4 role | role は `dsl.service` の口だけを使う（`dsl.parse`・`dsl.validate` に依存しない） |
| `audit_events` の3列と `AuditEvent` のファクトリー | U3 group | U4 role | 記録は `AuditEventListener` だけ。`detail` は 16,384 文字（UTF-16 の単位）まで。超えるときは要約の型に切り替える（`observability-design.md` 1節） |
| 接続プール `mastersmith-db`（上限 30） | 既存（アプリ全体） | 全機能 | 設定は変えない。`rolePoolLimit` の合否の回はグループへの割り当ての書き込みを含む（`scalability-design.md` 2.2） |
| 手元の監視のダッシュボードの区画（この Intent の管理の API） | Observability Setup（group の Q1 A） | U3・U4・U5 | `monitoring-design.md` 5節 |

## 6. 上流との差

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| 承認済みの要件・NFR 設計（Tomcat の設定の記述は無い） | `RoleTransferSlot` と `RequestSizeLimitFilter` は本文を読む前に断る（`reliability-design.md` 2.6、`security-design.md` 1節） | 共通の設定 `server.tomcat.max-swallow-size` を B1 で足し、断りの応答が 10 MiB までの本文でも届くようにする | Q1: A。既定の 2 MB を超える読まれない本文で接続が閉じられ、`ROLE_BUSY` が届かない回がありうる（dsl-v2 の読み直しの R-01 と同じ） |

監視・テストの名前・データの用意の差は、`monitoring-design.md` 7節と `cicd-pipeline.md` 11節に書きます。
