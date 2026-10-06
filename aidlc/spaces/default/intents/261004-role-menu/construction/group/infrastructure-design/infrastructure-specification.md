# 基盤の仕様 — U3 group

U3 group は service の単位で、B3 で作ります。グループとメンバーの管理の7つの API と、U4 role への読み取りと排他の口（契約 C4）を持ちます。

使うのは既存のアプリのコンテナと組み込みの H2 だけです。足すのは、内部DB の移行1つ（表2つ・索引1つ・監査の表の3列）だけです。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・IaC・検証環境は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/group/nfr-design/` の7つ: `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`
  - `construction/group/nfr-requirements/`（`reliability-requirements.md` の「障害と戻し」、`tech-stack-decisions.md`）
  - `construction/group/functional-design/functional-spec.md`・`entities.md`
  - `inception/domain-design/components.md`（GroupManagement・AuditLog・UserAccount）
  - `inception/contract-design/contract-summary.md`（C1・C4・C6・C10）
  - `inception/delivery-planning/bolt-plan.md`（B3）
- 既存のもの（正とする。読むだけ）: `compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`backend/src/main/resources/application.yaml`・`backend/src/main/resources/db/migration/`（V1〜V9）・README（「戻し方」「内部DBのバックアップと戻し方」）

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のアプリのコンテナ1つ（実行可能 WAR、`Dockerfile` の `MaxRAMPercentage=50.0`・`-Dh2.compactThreads=1`）。変えない | グループの API は既存のアプリの中の機能。新しいプロセス・コンテナは要らない |
| Instances | 単一のインスタンス | 内部DB が組み込みの H2 のため、横に広げない（`scalability-design.md` 1節） |
| Networking | 既存のまま。外から届くのはアプリの 8080 だけ。グループの API は `/api/admin/groups` の下で、同じオリジン | 外への呼び出しは無い（`logical-components.md` 3節） |
| Storage | 既存の名前付きボリューム（内部DB のファイル `/app/data/mastersmith.mv.db`）。表と列が増えるだけ | `reliability-design.md` 3節 |
| Environments | 配備は開発者の PC の `compose.yaml`。負荷の試験は使い捨ての環境（`docker/perf/compose.yaml`）。CI はテストだけ | `team.md` の Deployment、`project.md` の学び |
| IaC approach | 作らない。`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile` は変えない | 配備先が決まっていない（`project.md` の Deployment） |
| Resource sizing | 既存のまま（アプリ `mem_limit` の既定 2g・CPU 4、colima の VM CPU 4・メモリ 6GiB） | グループの規模の目安はグループ 100・利用者 50。一覧はページ送り、詳細はメンバー 1,000 人でも 1 秒の見込み（`scalability-design.md` 1節） |
| Configuration | `application.yaml`・`.env.example` は変えない。新しい環境変数・秘密は無い | `observability-design.md` 3節（`org.hibernate.orm.jdbc.error: OFF` を変えない）、`security-design.md` 4.1 |
| Schema migration | Flyway の移行を1つ足す。番号はコード生成の時点の次の空き番号（今の見込み `V10__u3_group.sql`）。前進のみ（3節） | 要件 NFR3.6、`reliability-design.md` 3節 |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。足した表と列は前の版から見えないだけで、データは残る | `reliability-requirements.md` の「障害と戻し」、`team.md` の Deployment |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（組み込みの H2、Spring Boot の管理の版） | database | 足す表: `groups`（名前は UTF-16 の 128、名前の鍵の列は 256 で一意）、`group_members`（主キー `(group_id, user_id)`、外部キー2つ、`user_id` の索引）。`audit_events` に `target_role_id`・`target_group_id`・`detail` の3列 | 行の排他は問い合わせのヒント 3,000 ms。一意の鍵・主キーの待ちは H2 の既定の約 2,000 ms のまま（`SET LOCK_TIMEOUT` は送らない。`reliability-design.md` 1.3・5節） |
| 接続プール（HikariCP `mastersmith-db`） | database（接続） | 変えない。上限は `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の既定 30、借りる待ち 5000 ms | 書き込みは2本使い（操作と確定の後の監査）、読み取りは1本。見積もりは `scalability-design.md` 2.1。上限に届いたときの時間切れは既知の制約（`project.md` の Decided） |
| Flyway | database（移行） | 変えない（`enabled: true`、`validate-on-migrate: true`、`classpath:db/migration`） | 前の版のアプリは、自分の知らない新しい移行を既定で無視する。`ddl-auto: validate` は余分な表と列を許す |
| 監査の記録（`AuditEventListener`） | 既存の部品（確定の後に別のトランザクションで記録） | 受け取り `onGroupAuditEvent` を足す。記録の仕組みは変えない | 契約 C10。監査の書き込みの失敗は既存の扱い（操作は成功のまま、ERROR） |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | 外への接続と非同期の処理は無い |

## 3. 移行と戻しの扱い（表 1・2 の補足）

| 項目 | 扱い | 出典 |
|---|---|---|
| 番号 | V10 と固定しない。先に作る B1・B2 は移行を足さないため、今の見込みは V10。名前は既存の形 `V<番号>__u<単位の番号>_<内容>.sql`。U4 role の移行はその後の空き番号 | 要件 NFR3.6、`tech-stack-decisions.md` |
| 中身 | 表と列を足すだけ。既存の列と行は変えない。広げてから縮める二段の「広げる」側だけ | `team.md` の Deployment、`reliability-design.md` 3節 |
| 確かめ（B3） | 結合テストで、移行の後に既存の監査の行が読め、足した3列が空であることを確かめる | `reliability-requirements.md` NFR3.6 |
| 前の版の起動（配備の段） | 前の版のイメージを、移行の後の内部DB の複写で起動し、ヘルスチェックが通ることを戻しの練習で確かめる | 4節 |
| 戻した後 | 前の版にはグループとロールの機能が無いため、グループ経由のロールは効かない。グループのデータは残り、次の版へ上げ直すと再び使える | `reliability-requirements.md` の「障害と戻し」 |

## 4. 配備の段への引き継ぎ

中身は deployment-pipeline・deployment-execution の段で決めます。この Intent は B1〜B9 をまとめて配備する見込みのため、U4 の移行と合わせて1回で扱います。

1. **入れ替えの前の内部DB の複写**: アプリを止めて内部DB を複写します。これがバックアップを兼ねます（README の「内部DBのバックアップと戻し方」、前の Intent の手順）。スキーマが変わるため省けません（`project.md` の学び）。
2. **戻しの練習**: 前の版のイメージを、移行の後の内部DB の複写と一時のボリュームで起動し、`/actuator/health` の 200 を待って確かめます。イメージに HEALTHCHECK が無いため、`running` を健全と読みません（`project.md` の学び）。
3. **監査の確かめ**: 配備の後のスモークテストで、グループの操作を行うかを決めます。グループの操作は監査に残るため、行う前に依頼者に伝え、期待の件数を合わせてから数えます（`project.md` の学び）。

## 5. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 内部DB の `groups`・`group_members` の表 | U3 group | U4 role（`group_role_assignments` などの外部キーが `groups` を指す見込み） | 書き込みと排他は `group.store`（`GroupStore`）だけ。U4 は `GroupMembershipQuery` の口（`groupIdsOfUser`・`exists`・`summaries`・`memberUserIds`・`lockForAssignment`）だけを使い、表に直接触れない（契約 C4、`logical-components.md` 6節） |
| `audit_events` の3列（`target_role_id`・`target_group_id`・`detail`）と `AuditEvent` のファクトリー | U3 group（B3 で足す） | U4 role（同じ列とファクトリーを使う） | 記録は `audit.service` の `AuditEventListener` だけが行う。`detail` は決めた型からだけ作り、16,384 文字（UTF-16 の単位）まで（`observability-design.md` 1節） |
| 接続プール `mastersmith-db`（上限 30） | 既存（アプリ全体） | 全機能。U3 は書き込みで2本・読み取りで1本 | 設定は変えない。上限に届く確かめは k6 の `groupPoolLimit`（`scalability-design.md` 2.2）。U4 の `rolePoolLimit` はグループへの割り当ての書き込みも含む |
| 削除してよいかを問う口（`GroupDeletionGuard`） | U3 group（インターフェース） | U4 role が実装（B3 は仮の実装） | 依存の向きは role → group だけ。group は role を知らない（`GroupBoundaryArchitectureTest`） |
| `user.service` のまとめて読む口（`findSummariesByIds`） | 既存の user（U3 が足す） | U3 group | 伏せ字の型で返し、値を取り出すのは web の層だけ（`security-design.md` 4.2） |
| 手元の監視のダッシュボード（`docker/monitoring/dashboards/mastersmith-overview.json`） | Observability Setup（この Intent の管理の API の区画、Q1: A） | U3・U4・U5 の API | `monitoring-design.md` 5節 |

## 6. 上流との差

この文書の範囲（配備・内部DB・接続プール・共有の資源）では、承認済みの NFR 設計と違う作りはありません。監視の上流との差は `monitoring-design.md` 7節に書きます。
