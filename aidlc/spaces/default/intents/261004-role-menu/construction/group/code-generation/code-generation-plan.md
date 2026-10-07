# Code Generation Plan — U3 グループ（group）

- 単位: U3 group（kind: service、大きさ M）
- Bolt: B3（承認済みの Bolt の計画の番号）。実行の順は B2（cross-cutting、65a76c5）→ B1（dsl-v2、44677cc）→ B3。どちらも `develop` へ統合済み。
- 範囲と量: スコープ classic、Test Strategy Standard（部品ごとに 5〜8 件、要所の結合テスト）、方法は test-after（11節の後の Testing Contract）。
- この計画は Part 1（計画）だけで、計画の承認の前にコード・テスト・設定を書かない。計画の承認の前に依頼者に確かめた判断は 11節（Q1〜Q5、どれも A）。11節の後の Testing Contract は `aidlc engine testing-posture render` の出力をそのまま貼った（バイトの一致を確かめた）。

## 1. 入力にした設計

| 段 | 文書（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下） | 使う所 |
|---|---|---|
| Functional Design | `construction/group/functional-design/functional-spec.md`（1〜10節）・`rules.md`（BR1.1〜BR10.2）・`entities.md`・`traceability.json` | 作るものの形と決まり、7節のテストの観点、8節の契約との差、10節の承認の場の直し |
| NFR Requirements | `construction/group/nfr-requirements/` の `security-requirements.md`（NFR1.1〜NFR1.11）・`reliability-requirements.md`（NFR3.1・NFR3.3〜NFR3.7・NFR6.1）・`performance-requirements.md`（NFR2.3・NFR2.5・NFR2.6）・`scalability-requirements.md`（NFR2.1・NFR2.7・NFR2.8）・`observability-requirements.md`（NFR5.1〜NFR5.5）・`tech-stack-decisions.md`（NFR6.4〜NFR6.7、コード生成への引き継ぎ） | 確かめの要件、新しい依存を足さないこと |
| NFR Design | `construction/group/nfr-design/` の `reliability-design.md`（捨ての試し T1〜T7、2.1〜2.3、4.1〜4.3、5節、6節）・`security-design.md`（1〜6節）・`scalability-design.md`（2.1〜2.4、3節）・`performance-design.md`（1〜3節）・`observability-design.md`（1〜5節）・`logical-components.md`（L1〜L12、2節・4〜7節） | 部品の置き場（`group.store`）、結果の型、待ち合わせの4つの点、`GroupStoreTransactions`、テストの一覧、k6 の場面 |
| Infrastructure Design | `construction/group/infrastructure-design/` の `infrastructure-specification.md`（1〜6節）・`cicd-pipeline.md`（1〜10節）・`monitoring-design.md`（1〜7節）・`infrastructure-design-questions.md`（Q1: A） | 移行の中身と番号、`verify` の段と関門、使い捨ての環境、E2E、統合、変えないもの |
| Inception | `inception/units-generation/unit-of-work.md`（U3）・`unit-of-work-story-map.md`、`inception/requirements-analysis/requirements.md`（FR4.1・FR4.1a・FR4.3〜FR4.5・FR11.1・FR12.1〜FR12.3・NFR1〜NFR6・C1・C2）、`inception/user-stories/stories.md`（US2.1、US2.2 のグループの部分）、`inception/contract-design/contract-summary.md`（C1・C4・C6・C10、業務の理由の拒否の code）、`inception/domain-design/components.md`（GroupManagement・AuditLog・UserAccount）・`decisions.md`（ADR-001・ADR-002・ADR-006）、`inception/delivery-planning/bolt-plan.md`（B3） | 範囲・ID・終わりの条件・見せるもの |
| 他の単位の設計（読み取りだけ） | `construction/role/nfr-design/logical-components.md`（L5 `RoleGroupDeletionGuard`）・`construction/role/functional-design/functional-spec.md`（仮の実装の置き換え、BR10） | B3 の仮の実装の名前と置き場 |
| 先に作った2つの Bolt の記録（読み取りだけ） | `construction/cross-cutting/code-generation/`・`construction/dsl-v2/code-generation/` の `code-generation-plan.md`・`code-summary.md`・`generation-notes.md` | 計画の形、コミットの区切り、途中で止まった所（6節） |
| 読み直しの記録 | `.aidlc-reviews/functional-design/units/group/`（2回）・`.aidlc-reviews/nfr-requirements/units/group/`（2回）・`.aidlc-reviews/nfr-design/units/group/`（2回、最後は `a0741e66bbcee445`）・`.aidlc-reviews/infrastructure-design/units/group/6c988a88edfc1ecc/` | 2.2 の残った指摘の扱い |
| 監査ログ | `audit/sakura-local-4e42a93f87ce.md` の GATE_APPROVED・GATE_REJECTED（Functional Design・NFR Requirements・NFR Design・Infrastructure Design） | 2.1 |
| 既存のコード（読み取りだけで確かめた） | `backend/src/main/java/cherry/mastersmith/` の `audit/**`（`AuditEvent`・`AuditEventType`・`AuditFailureReason`・`AuditEventFactory`・`AuditEventListener`）、`useradmin/**`（`UserAdminService` の `TransactionTemplate` と待ち合わせの口、`UserAdminProblemTypes`・`UserAdminController`）、`user/**`（`UserProblemTypes`・`UserProblemTypeCatalog`・`UserAccountService`・`UserAdminSummary`）、`common/persistence/**`（`RowLockFailures`・`RowLockAttempt`）、`common/paging/Paging.java`、`common/error/domain/BusinessException.java`（応答の追加の項目）、`invitation/lock/InvitationLockQueriesImpl.java`（`EntityManager` で排他する用途名の下位パッケージの形）、`common/observability/TraceAspect.java`（対象の層の式）、`backend/src/main/resources/application.yaml`（`org.hibernate.orm.jdbc.error: OFF`）、`backend/src/main/resources/db/migration/`（V1〜V9）、`backend/build.gradle.kts`（`*Test`・`*IT` の名前の分け方、計測の除外 `**/*Properties.class`、`packagesJudgedByTotal` の7パッケージ）。テストは `ArchitectureTest`・`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`・12 の `*BoundaryArchitectureTest`、`audit/**`（`AuditSchemaIT`・`AuditSecretLeakIT`・`AuditEventTest`・`AuditEventListenerTest`・`audit/testsupport/**`）、`useradmin/**`（`UserAdminBusyApiIT`・`UserAdminBusyLogTraceIT`・`UserAdminListQueryCountIT`・`useradmin/testsupport/**`）、`user/**`（`UserUniqueViolationSecretLeakIT`）、`auth/testsupport/SqlStatementCounter`、`appearance/testsupport/ConnectionAcquireCounter`。`perf/k6/scenarios.js`・`perf/README.md`（利用者の管理の節の 2'' の SQL、`baseUnit` の読み方、`setupTimeout` の一覧）、README（「利用者の管理の API」「スキーマの変更（Flyway）」「監査ログ（U4）」） | 影響の範囲と当たりそうな所（6節） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログの GATE_APPROVED・GATE_REJECTED と各文書の「承認の場の決定」の節から洗い出したもの）

| 段（日付） | 監査ログの User Input・Feedback | この単位に当たる中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Delivery Planning（2026-10-04） | Approve | B3 の終わりの条件（7つの口の 401/403/200・拒否の code・監査、問う口のインターフェースとメンバーが残る削除の拒否（割り当ての有無は B3 ではテスト用の実装で確かめる）、監査の表に列を足す移行と `AuditEvent` の項目とファクトリー・`detail` の形と伏せる規則・漏えいのテスト、`GroupBoundaryArchitectureTest`、`verify`）。確かめること「監査の表を広げても前の版のアプリと既存の監査が壊れない」。見せるもの「API でグループを作りメンバーを足し、監査に残る」 | そのまま終わりの条件にする。確かめることは Step 7（前の版の列だけの書き込みと Flyway の確かめ、11節 Q4）と既存の監査のテストの全体（Step 17・22）。見せるものは Step 15 の `GroupAuditIT` と、承認の場で示す `curl` の手順（`code-summary.md`） | 全体 |
| Functional Design（2026-10-05） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | R-01: 違反の後は巻き戻し、書き込みの無い新しいトランザクションで失敗の出来事だけを出す（BR5.7）。R-02: 一意の鍵・主キーの待ちの上限切れも `GROUP_BUSY`（BR5.2）。R-03: 名前の列 UTF-16 の 128・鍵の列 256。R-04: 鍵は Java の `GroupName` だけが作り、通常の列に一意の制約。承認の場の決定: `GroupMembershipQuery.memberUserIds` を足す（BR6.5） | そのまま作る。列の長さは V10（Step 6）、鍵は `GroupName`（Step 8）、BR5.7 は `GroupStoreTransactions` と2つ目の `TransactionTemplate`（Step 12）、`memberUserIds` は Step 12・13 | Step 6・8・12〜15 |
| NFR Requirements（2026-10-05） | GATE_REJECTED「Major 11 件だけを直す」→ Approve | R-01: k6 は操作ごとに1回の繰り返しに要求1つの8場面、判定は `iteration_duration{scenario:…}` の p95 < 1000 ms と `checks` の率 1、トークンは `setup()`、場面は 3 分まで。R-02: `GroupConnectionUsageIT`（書き込み 2・読み取り 1、3 以上は見積もりの誤り）と負荷の場面 `groupPoolLimit` | 台本は Step 18、結合テストは Step 15 | Step 15・18 |
| NFR Design（2026-10-06） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | R-01（そろえる点1）: 接続の合否の回は上限 11・書き込み 5 VU で、時間切れ 0・500 が 0 件・`acquire` の最大 20 ms 未満。上限 10・20 VU の回は記録だけ（流し直しは1回まで）。R-02: 同時の重なりは時間に頼らない形（待たない違反・先の側を放さない上限切れ）、待ち合わせの口は4点（`beforeLock`・`afterLock`・`afterCheck`・`afterWrite`）。R-05（そろえる点2）: 巻き戻しの印は `GroupStoreTransactions` に集め、書き込みの前の拒否も2つ目のトランザクションで出す。そろえる点3: 一意の鍵・主キーの待ちは H2 の既定の約 2 秒のまま受け入れ（`SET LOCK_TIMEOUT` を送らない）、同じ名前の重なりの 409 の code が `GROUP_NAME_DUPLICATE` か `GROUP_BUSY` に分かれることを受け入れる | テストの期待は「行の上限切れ 3 秒・鍵の上限切れ約 2 秒」を前提にし、合否は経過の時間で決めない（409・状態・監査の行・WARN）。台本の `groupPoolLimit` は2つの回を README に書き分ける | Step 11〜15・18 |
| Infrastructure Design（2026-10-06） | Approve（まとめの確認は Looks correct。承認のコミット c97f760「Major 5 件の直し方は承認の場の決定としてコード生成の計画へ」） | 決定 (2): group の読み直しの R-01（`GroupNameProperties` は名前が `*Test` でも `*IT` でもないため `verify` で一度も動かない）は、`GroupNamePropertyTest` に直して段 5（単体テスト）で流す（指揮役から伝達。監査ログの承認の場の記録は User Input の Approve だけで、決定の本文は記録に無い）。ほかの決定（(1) の dsl-v2 など）はこの単位に当たらない。あわせて Q1: A（ダッシュボードの区画は Observability Setup が足し、B3 は `docker/monitoring/` に触れない） | jqwik の性質ベースのテストを `GroupNamePropertyTest` という名前で作る（承認済みの `logical-components.md` 5節・`cicd-pipeline.md` 2節の `GroupNameProperties` との差は 10節 D-12）。Step 20 で `GroupNameProperties` の名前が残っていないことと、Step 22 の単体テストの結果に `GroupNamePropertyTest` が含まれることを記録する | Step 9・20・22 |
| Code Generation の始め（依頼者の決定、指揮役から伝達） | B2 → B1 → B3 の順 | B2 で `ApiAccess`・構造の検査・実行時の検査が入った。B3 の7つの口は `@ApiAccess(ADMIN)` を付けないと `ApiAccessArchitectureTest` で落ちる。B1 は `application.yaml` に `server.tomcat.max-swallow-size` を足したが、B3 には影響しない | 7つの口に印を付ける（Step 14）。B2 の検査が通ることを Step 16・17 で確かめる | Step 14・16・17 |

### 2.2 読み直しで残った指摘の扱い（承認の場で直していないもの）

| 読み直し | ID | 中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Functional Design | R-03・R-05〜R-11 | detail の数え方・入口の判定の後の窓・0 以下の ID・USER_NOT_FOUND の移し替え・移行の番号・詳細の全件返し・B5 の 403・2つ目のトランザクションの担当 | NFR 要件（NFR5.5・NFR1.10・NFR1.4・`tech-stack-decisions.md`・NFR3.6・NFR2.8・NFR3.4）と NFR 設計で手当て済み。そのとおりに作る。USER_NOT_FOUND の移し替えは「useradmin の一覧から外す」と「user の一覧に足す」を同じコミット（C1）で行う | Step 4・5・6・12 |
| NFR Requirements | R-02〜R-08 | 20 VU の期待・B3 の 403 の差・受け入れた制約・`groupIdsOfUser` の時間・警報の名指し・409 の code の確定・台本の待ちと名前 | NFR 設計（`scalability-design.md` 2.2・`security-design.md` 2節・5節・`performance-design.md` 1節・2.3・`reliability-design.md` 5節）で手当て済み。警報の名指しは `monitoring-design.md` 7節のとおり Observability Setup | Step 15・18 |
| NFR Design | R-02（Resolved、残り） | 待ち合わせの #1・#5 は後の側が排他の待ちに入る前に放されうるため、待つ経路を通ったことは保証されない（期待は同じで合否は決定的） | **この Bolt で手当てする**（記録だけ）。`GroupConcurrencyIT` の Javadoc に「待つ経路の確かめは #6（行の上限切れ）が受け持つ」と書く | Step 15 |
| NFR Design | R-04 | 外部キーの違反 `Referenced` の読み替え先が1つに読める（承認済みの BR5.4 は削除なら `GROUP_IN_USE`、追加なら `GROUP_NOT_FOUND`） | **この Bolt で手当てする**（10節 D-6）。`Referenced` は操作ごとに読み替える。`GroupStoreConstraintIT` に、削除の前にメンバーを確定させた外部キーの違反（→ 削除の読み替え）と、グループを消した後のメンバーの追加の外部キーの違反（→ 追加の読み替え）の2行を置き、`GroupAdminServiceTest` で読み替え先を確かめる | Step 11・13 |
| NFR Design | R-06 | 想定外の例外をクラスの名前だけの例外に包み直すと、SQLState・制約の名前も落ちる | **先送り**（11節 Q5: A、10節 D-22）。承認済みの設計どおりクラスの名前だけにし、SQLState と制約の名前は `code-summary.md` の「後に回すこと」に書く | Step 10・21 |
| NFR Design | R-07 | NFR 設計の traceability.json に、NFR 要件で N/A とした ID の行が無い | **この Bolt で手当てする**。コード生成の `traceability.json` に NFR1.5・NFR2.2・NFR2.4・NFR3.2・NFR4.1〜NFR4.3・NFR6.2・NFR6.3 を理由つきの N/A で載せる | Step 21 |
| NFR Design | R-09 | `application.yaml` の `org.hibernate.orm.jdbc.error: OFF` のコメントに group の理由が無い（意見） | **先送り**。基盤の設計（`cicd-pipeline.md` 9節 (vi)）が `application.yaml` を変えないことを B3 の確かめにしているため、コメントも変えない。守りは `GroupUniqueViolationSecretLeakIT`。理由を `code-summary.md` の「後に回すこと」に書く | Step 20・21 |
| NFR Design | R-10 | `StoreOutcome` に書き込みの前の業務の拒否の変種が無く、`inFirst` の型と合わない。store の呼び出しの粒度が書かれていない | **この Bolt で手当てする**（10節 D-5）。store の方法を「排他」「書き込みと flush」に分け、`inFirst` のラムダの中で待ち合わせの口と業務の判定を挟む。1つ目のトランザクションの結果は `group.service` の型 `FirstStep<T>`（`Done`・`Rejected(GroupRejection)`・`Store(StoreOutcome の Done 以外)`）にし、`Done` 以外で必ず巻き戻しの印を付ける | Step 12・13 |
| Infrastructure Design | R-01 | `GroupNameProperties` は `verify` で動かない | 2.1 の決定 (2) のとおり `GroupNamePropertyTest` | Step 9 |
| Infrastructure Design | R-02 | 監査の表の3列が NULL を許すか書かれていない | **この Bolt で手当てする**。V10 の3列は NULL 可・既定値なし（V6・V7 と同じ形）。`AuditSchemaIT` に3列の `IS_NULLABLE = YES` と型、前の版の列だけを並べた INSERT が通り3列が空のままであることを足す | Step 6・7 |
| Infrastructure Design | R-03 | 前の版が新しい移行（V10）を Flyway の既定（`*:future` を無視）で通ること、前の版が新しい `event_type` の行を読まないことが前提のまま | **一部この Bolt**（11節 Q4: A）。V1〜V9 だけを置いた場所を読ませた Flyway（`validate-on-migrate` はアプリと同じ）が、V10 まで当てた内部DB で止まらないことを結合テストで確かめる。前の版のイメージでの起動は配備の段（戻しの練習）。`AuditEventRepository` の読み取りが `findById` だけであることは `code-summary.md` に1行で記録する | Step 7 |
| Infrastructure Design | R-04 | `hikaricp.connections.acquire` の単位（外部エクスポートの有無で seconds と milliseconds が入れ替わる） | **この Bolt で手当てする**。`perf/README.md` のグループの節に「`baseUnit` を見てミリ秒にそろえてから 20 ms・10 ms と比べる」と書き、既存の読み取りの関数（`baseUnit` が seconds なら 1,000 倍）を使う | Step 18 |
| Infrastructure Design | R-05 | 利用者の入れ方（手順 2 は 11 名）と `setupTimeout`（場面の一覧ごとに 10m） | **この Bolt で手当てする**。利用者は利用者の管理の節の 2'' と同じ SQL の形で数を書く（10節 D-15）。台本に `GROUP_SCENARIOS` の一覧を足し、`setupTimeout` を `10m` にする。`k6 inspect` で確かめる | Step 18 |
| Infrastructure Design | R-06 | ダッシュボードの区画の式の形を U4・U5 とどうそろえるか | **先送り**（Observability Setup。`monitoring-design.md` 5節のとおり B3 は `docker/monitoring/` に触れない） | — |

### 2.3 この計画での読み方

- **設計の文書どおりに作るもの**: グループとメンバーの表と移行（`entities.md`、`infrastructure-specification.md` 2節）、名前の規則（BR1.1〜BR1.5）、認可と入力（BR2）、メンバー（BR3）、削除（BR4）、排他と同時の重なり（BR5、`reliability-design.md` 2節）、境界の口（BR6、C4）、一覧と詳細（BR7）、監査（BR8、`observability-design.md` 1節）、漏えい（BR9、`security-design.md` 4節）、誤りの応答（BR10）、k6 の場面（`performance-design.md` 2節・`scalability-design.md` 2.2）。
- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。この単位の層は「既存の部品の口の移し替え（user・useradmin）→ データの形（移行と監査の列。audit.domain）→ group のドメイン（名前・出来事・code・表のエンティティ）→ DB アクセス（`group.repository` の読み取りと `group.store` の排他・書き込み）→ 業務処理（`group.service`・仮の問う口・監査の受け取り）→ API（`group.web`）→ 境界と構造の検査」の順とする。画面の層は持たない（Testing Contract の `plan_profile` の「Frontend behavior」は該当しない層として省く。画面は U6 の B8）。
- **例外の文の経路**: 排他と、違反を起こしうる書き込み・flush は `cherry.mastersmith.group.store`（TraceAspect の対象の外）の `GroupStore` だけが行う。`group.repository` は読み取りだけ。`group.service` は `EntityManager` を使わない（`GroupBoundaryArchitectureTest`）。
- **トランザクション**: `@Transactional` を付けず、`group.service` の `TransactionTemplate` で組む（`UserAdminService` と同じ形）。store を呼ぶ1つ目のトランザクションはすべて `GroupStoreTransactions` を通す。失敗の出来事は1つ目を終えてから2つ目の `TransactionTemplate`（書き込みなし）で出す。入れ子の REQUIRES_NEW は使わない。
- **変えないもの**（4.7）: `application.yaml`、`backend/build.gradle.kts`（`packagesJudgedByTotal`・計測の除外・テストの名前の分け方・テストの JVM の設定）、依存と lockfile、CI、`compose.yaml`・`docker/**`、画面（`frontend/**`）と E2E のファイル。

## 3. Bolt・ブランチ・統合・コミットの区切り

| 項目 | 扱い | 出典 |
|---|---|---|
| Bolt の番号 | B3 | `bolt-plan.md` |
| 作業ブランチ | `develop` の先頭（計画の時点で 44677cc）から `feature/261004-role-menu-b3`。同じ作業フォルダで作り、worktree は使わない。作るのは依頼者の承認の後 | `team.md` の Way of Working、`cicd-pipeline.md` 8節 |
| 統合の前の関門 | colima を起動し、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（対象DB の3種類のテストを飛ばさない。SKIPPED が出たら統合しない）。続けて E2E の全体（`docker compose --profile mail up -d mailpit` の後に `caffeinate -i ./gradlew e2eTest`）。どちらも通るまで統合しない | `team.md`・`project.md` の学び、`cicd-pipeline.md` 2・7節 |
| 統合の形 | `develop` への squash（1 Bolt が `develop` の1コミット）。サブモジュールの更新を含まないため fast-forward の例外に当たらない。単位は1つ | `team.md` |
| 統合コミットの件名（案） | `B3 グループ: グループとメンバーの管理の API・問う口と読み取りの口・監査の表の列と detail・k6 の場面`（日本語） | `team.md` |
| 統合の後 | 作業ブランチを消す。`origin` への push は依頼者が行う。push の後の CI の失敗は `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | `team.md` |
| 作業ブランチの上のコミットの区切り（11節 Q3: A で確定） | **場所ごとの4つ**で、どのコミットの時点でもコンパイルと、それまでに流したテストが通る区切りにする。<br>**C1** user・useradmin: USER_NOT_FOUND の移し替えと `user.service` のまとめて読む口、そのテスト（Step 4・5）。<br>**C2** 内部DB と audit: V10（グループ・メンバーの表と監査の3列）、`AuditEvent` の項目とファクトリー・detail の上限・`AuditEventType`・`AuditFailureReason` の値、監査の既存のテストの追従と互換の確かめ（Step 6・7）。group のクラスに依存しない形にする（受け取り `onGroupAuditEvent` は C3）。<br>**C3** group と role: `group.*`（ドメイン・store・repository・service・web）、`role.service` の仮の問う口、`audit` の受け取りと写し方、`UserBoundaryArchitectureTest` の追従、境界テストとすべてのテスト（Step 8〜17）。<br>**C4** 負荷の台本と文書: `perf/k6/scenarios.js`・`perf/README.md`・README（Step 18・19）。<br>前の2つの Bolt では手順ごとの区切りが型の追従でビルドの通らない区切りになり、場所ごとに変えた（B1 は場所ごとの4つ）。C3 は大きいが、group の層の間で型が行き来するため、層ごとに分けるとビルドの通らない区切りが生じる | `project.md` の Change Control、前の Bolt の経験 (b) |
| コミットの進め方 | 生成の担当はコミットしない。生成の後に指揮役が C1〜C4 の区切りを依頼者に提案し、承認を得てから行う（段の記録のコミットも別に提案する）。監査ログは記録のコミットの直後にも追記されるため（経験 (d)）、`aidlc/` の未コミットの変更（監査ログを含む）は squash の前にコミットしておき、`git restore` で消さない | `project.md` の Change Control の学び |
| 承認の場の時点 | コード生成の段の承認の場は、最後のコードのコミット（C4）の後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く | `project.md` の学び |

## 4. 作るもの・手を入れるもの

パスはリポジトリのルートからの相対。新しいソースの先頭には Apache License 2.0 のヘッダー（2026、agwlvssainokuni、`/* ... */`、SQL は `--`）を置く。コメントと Javadoc は日本語、テストの説明文は英語。

### 4.1 新しいパッケージ `group`（`backend/src/main/java/cherry/mastersmith/group/`）

| パッケージ | ファイル（案） | 中身 | 出典 |
|---|---|---|---|
| `group`（直下） | `package-info.java` | 機能の説明（依存してよい先・依存されてよい先） | Code Style |
| `group.domain` | `Group.java`・`GroupMembership.java`・`GroupMembershipId.java` | JPA のエンティティ（表 `groups`・`group_members`。JPQL のエンティティ名は `UserGroup`・`GroupMembership`、10節 D-3）。書き換えの方法は store だけが呼ぶ | `entities.md` |
| `group.domain` | `GroupName.java`・`GroupNameValidation.java`（結果の型。`INVALID_BLANK`・`INVALID_TOO_LONG`・`INVALID_CONTROL_CHARACTER`） | 前後の空白（全角を含む Unicode の空白）の取り除き、コードポイントで 1〜64、制御文字の拒否、言語に依らない小文字化の鍵。鍵を作る唯一の場所 | BR1.1〜BR1.4、NFR1.11 |
| `group.domain` | `GroupOperation.java`・`GroupAuditEvent.java`・`GroupAuditFailure.java`・`GroupAuditDetail.java`（sealed、`Name`・`Rename`・`Membership`・`InUse`） | 監査の出来事と detail の型。メールアドレス・氏名・秘密を型に持たない | BR8、C10 |
| `group.domain` | `GroupProblemTypes.java` | `GROUP_NOT_FOUND` 404・`GROUP_NAME_DUPLICATE` 409・`GROUP_IN_USE` 409・`GROUP_NO_CHANGE` 409・`GROUP_BUSY` 409（日本語と英語の文言。説明文に名前・ID を載せない） | BR10.1 |
| `group.domain` | `GroupMember.java`・`GroupRejection.java`（拒否の理由） | 詳細のメンバー1人分（氏名・メールアドレスは toString で伏せる）、業務の拒否の理由 | BR3・BR9.1 |
| `group.repository` | `GroupRepository.java`・`GroupMemberRepository.java`（`org.springframework.data.repository.Repository` を継ぐ読み取りだけ）・`GroupRowView.java` などの射影 | 件数・ページ（ID の順）・ページの ID のメンバーの数（`GROUP BY` 1回）・詳細のメンバー（`added_at, user_id` の順）・`groupIdsOfUser`・`memberUserIds`・`exists`・`summaries`・名前の鍵の読み取り。書き込みの方法・`@Modifying` を置かない | BR7、NFR2.3・NFR2.6、`security-design.md` 4.1 |
| `group.store` | `GroupStore.java`・`StoreOutcome.java`・`StoreFailureClassifier.java`・`GroupStoreUnexpectedException.java`・`package-info.java` | 行の排他（`PESSIMISTIC_WRITE`・ヒント 3,000 ms）、作成・名前の変更・削除・メンバーの追加と外しの書き込みと flush。例外を中で受けて `StoreOutcome`（`Done`・`GroupMissing`・`NameTaken`・`AlreadyMember`・`Referenced`・`Busy(lockKind)`）に変える。区分の順は `RowLockFailures.isLockFailure` → SQLState 23505・23503 と制約の名前 → 想定外（クラスの名前だけの例外に包み直す）。上限切れは `RowLockFailures.warn`（`GROUP_ROW`・`GROUP_NAME_KEY`・`GROUP_MEMBER_KEY`）。`EntityManager` はコンストラクターで受ける | `reliability-design.md` 2.2、T6・T7、`security-design.md` 4.1 |
| `group.service` | `GroupAdminService.java`・`GroupStoreTransactions.java`・`FirstStep.java`・結果の型（`GroupCreateResult`・`GroupChangeResult`・`GroupListResult`・`GroupDetailResult` など、sealed の record） | 業務処理。入力の判定 → 排他 → 待ち合わせの口 → 拒否の判定 → 書き込み → 監査の出来事。1つ目は `GroupStoreTransactions`、失敗の出来事は2つ目の `TransactionTemplate`。一覧・詳細は読み取りだけのトランザクション1つ | FS 2.1〜2.7、BR5.7、NFR3.4 |
| `group.service` | `GroupBarrier.java`・`NoOpGroupBarrier.java` | 待ち合わせの口（4点）。本番は何もしない `@Component` | `reliability-design.md` 2.1 |
| `group.service` | `GroupMembershipQuery.java`・`GroupMembershipQueryImpl.java`・`GroupSummary.java`・`GroupRowLock.java` | U4 への読み取りと排他の口（`groupIdsOfUser`・`exists`・`summaries`・`memberUserIds`・`lockForAssignment`。最後は呼ぶ側のトランザクションの中だけ） | C4、BR5.3・BR6.4・BR6.5 |
| `group.service` | `GroupDeletionGuard.java`・`DeletionDecision.java` | role が実装する問う口（`canDelete`・`assignedRoleCounts`）。group に既定の実装を置かない | ADR-002、BR6.1・BR6.2 |
| `group.service` | `GroupProblemTypeCatalog.java` | 起動時の code の収集 | BR10.1 |
| `group.web` | `GroupAdminController.java`・`GroupNameRequest.java`・`MemberAddRequest.java`・`GroupResponse.java`・`GroupPageResponse.java`・`GroupRowResponse.java`・`GroupDetailResponse.java`・`GroupMemberResponse.java`・`GroupFieldErrors.java` | 7つの口（すべて `@ApiAccess(ADMIN)`、道は `/api/admin/groups` の下）。結果の型を `switch` で尽くして `BusinessException` に変える。`GROUP_IN_USE` の応答に `members`・`assignedRoles`。伏せ字の値を取り出すのはここだけ | C6、BR2・BR4.2・BR10、L1 |

### 4.2 新しいパッケージ `role`（B3 は仮の実装だけ）

| ファイル | 中身 | 出典 |
|---|---|---|
| `backend/src/main/java/cherry/mastersmith/role/service/RoleGroupDeletionGuard.java`（11節 Q1） | `GroupDeletionGuard` の仮の実装。割り当ての表がまだ無いため、`assignedRoleCounts` は渡した ID のすべてに 0、`canDelete` は `Allowed`。Javadoc に「B3 の仮の実装。B5 で本物に置き換える（BR6.3、role の BR10）」と `TODO(B5)` の印 | BR6.3、role の `logical-components.md` L5 |
| `backend/src/main/java/cherry/mastersmith/role/package-info.java`・`role/service/package-info.java` | 機能の説明 | Code Style |

### 4.3 既存の本体に手を入れるもの

| ファイル | 変える中身 | 出典 |
|---|---|---|
| `backend/src/main/java/cherry/mastersmith/user/domain/UserProblemTypes.java` | `USER_NOT_FOUND`（404、code・状態コード・文言は今のまま）を移して `all()` に足す | BR10.2、R-07 |
| `backend/src/main/java/cherry/mastersmith/useradmin/domain/UserAdminProblemTypes.java` | `USER_NOT_FOUND` の定義を消し、`of(USER_NOT_FOUND)` は `UserProblemTypes.USER_NOT_FOUND` を返す。`all()` から外す。Javadoc の説明を直す | BR10.2 |
| `backend/src/main/java/cherry/mastersmith/useradmin/web/UserAdminController.java` | 112 行の `UserAdminProblemTypes.USER_NOT_FOUND` を `UserProblemTypes.USER_NOT_FOUND` に替える（1か所） | BR10.2 |
| `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java` | まとめて読む口 `findSummariesByIds(Set<Long>)` を足す（既存の `UserRepository.findAllById` の1回の読み取りで、伏せ字の toString を持つ `UserAdminSummary` を返す。10節 D-2） | `security-design.md` 4.2、`performance-design.md` 1節 |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java` | 項目 `targetRoleId`・`targetGroupId`・`detail` と列の対応、ファクトリー `withRoleGroupTarget(…)`（detail は `String.length()` で 16,384 を超えたら `IllegalArgumentException`、切り詰めない）、getter、`toString` に `targetRoleId`・`targetGroupId` と detail の長さだけを足す（10節 D-10） | C10、BR8.4〜BR8.6、NFR5.5 |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` | `GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED` | BR8.1・BR8.9 |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java` | `GROUP_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`（`USER_NOT_FOUND`・`NO_CHANGE` は既存の値） | BR8.2 |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java`・`AuditDetailJson.java`（新しい） | `from(GroupAuditEvent)`（種類・理由・対象・detail の写し方）、`GroupAuditDetail` を決めたキーだけの JSON にする（Jackson、record ごとに決まったキー） | `observability-design.md` 1節、BR8.5 |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | `onGroupAuditEvent`（確定の後、既存の `onUserAdminAuditEvent` と同じ形）と、書き込みの失敗の ERROR の項目に `targetRoleId`・`targetGroupId`・`detail` を値があるときだけ足す（10節 D-10） | BR8.1、NFR3.5 |

### 4.4 リソース

| ファイル | 中身 |
|---|---|
| `backend/src/main/resources/db/migration/V10__u3_group.sql`（新しい。番号は Step 3 で次の空き番号を確かめる） | `groups`（`group_id` BIGINT 自動、`name` VARCHAR(128) NOT NULL、`name_key` VARCHAR(256) NOT NULL、`created_at`・`updated_at` TIMESTAMP WITH TIME ZONE NOT NULL、制約 `uk_groups_name_key`）、`group_members`（`group_id`・`user_id`・`added_at`、主キー `pk_group_members(group_id, user_id)`、外部キー `fk_group_members_group`（削除の制限）・`fk_group_members_user`、索引 `ix_group_members_user_id(user_id)`）、`audit_events` に `target_role_id` BIGINT・`target_group_id` BIGINT・`detail` VARCHAR(16384) を NULL 可・既定値なしで足す（参照の制約と索引は置かない）。説明のコメントを日本語で書く |

### 4.5 バックエンドのテスト（`backend/src/test/java/cherry/mastersmith/`）

足すもの（名前は `*Test` が単体、`*IT` が結合。`build.gradle.kts` の名前の分け方に合わせる）:

| 層 | テスト |
|---|---|
| 境界の口の移し替え | `user/service/UserSummariesByIdsIT`（まとめて1回・存在しない ID は含めない・toString に値が出ない） |
| データの形 | `audit/domain/AuditEventGroupTargetTest`（ファクトリー・detail の 16,384 ちょうどと超え（サロゲートペアを含む）・toString）、`audit/domain/AuditMigrationCompatibilityIT`（11節 Q4: 前の版の列だけの INSERT、V1〜V9 だけの Flyway が止まらない、既存の行が読めて3列が空） |
| group のドメイン | `group/domain/GroupNameTest`・`GroupNamePropertyTest`（jqwik）・`GroupAuditEventTest`・`GroupAuditDetailTest`・`GroupProblemTypesTest`・`GroupMemberTest` |
| DB アクセス | `group/store/StoreFailureClassifierTest`（#9 を含む）・`group/store/GroupStoreConstraintIT`（#7）・`group/repository/GroupRepositoryIT`（並び・数・`user_id` の索引の存在） |
| 業務処理 | `group/service/GroupAdminServiceTest`（store・repository をモック、結果の型と出来事）・`GroupStoreTransactionsIT`（T5）・`GroupMembershipQueryIT`（5つの口、外した直後の `groupIdsOfUser`）・`GroupMembershipQueryCountIT`・`GroupProblemTypeCatalogTest`、`role/service/RoleGroupDeletionGuardTest`、`audit/domain/AuditGroupEventFactoryTest`・`audit/service/AuditGroupEventListenerTest` |
| API | `group/web/GroupAdminApiIT`（7つの口の成功・拒否の code・応答の形・Problem Details）・`GroupAdminAuthorizationApiIT`（28 行）・`GroupAdminMassAssignmentIT`・`GroupAdminIdorApiIT`・`GroupConcurrencyIT`（#1・#5・#6）・`GroupNameConflictIT`（#2〜#4）・`GroupBusyLogIT`・`GroupConflictAuditIT`・`GroupAdminListQueryCountIT`・`GroupAdminMetricsIT`・`GroupConnectionUsageIT`・`GroupSecretLeakIT`・`GroupUniqueViolationSecretLeakIT`、`audit/service/GroupAuditIT`・`GroupAuditWriteFailureIT` |
| 境界 | `group/GroupBoundaryArchitectureTest`、`role/RoleBoundaryArchitectureTest`（11節 Q1） |
| 手伝い | `group/testsupport/TestGroupBarrier`（`@Primary`、4点で止める・合図を待つ、待ちの上限 20 秒）・`TestGroupDeletionGuard`（`@Primary`、数と Blocked を決められる）・`GroupApi`（要求の組み立て）・`GroupFixtures`・`ConnectionHoldRecorder`（テストだけで `DataSource` を包み、同時に借りている本数の最大を記録する。10節 D-9） |

書き換えるもの（既存のテスト。本体の変更に追従させる）:

| テスト | 書き換え | 理由 |
|---|---|---|
| `user/domain/UserProblemTypesTest`・`user/service/UserProblemTypeCatalogTest` | `USER_NOT_FOUND`（404）を一覧の期待に足す | BR10.2 |
| `useradmin/domain/UserAdminProblemTypesTest`・`useradmin/service/UserAdminProblemTypeCatalogTest` | 一覧の期待から `USER_NOT_FOUND` を外し、`of(USER_NOT_FOUND)` が `UserProblemTypes.USER_NOT_FOUND` と同じものを返すことに直す | BR10.2 |
| `audit/service/AuditSecretLeakIT` | 列の一覧に `TARGET_ROLE_ID`・`TARGET_GROUP_ID`・`DETAIL` を足し、グループの操作の後の行（detail を含む）に秘密・メールアドレス・氏名が無いことの確かめを足す | NFR1.7 |
| `audit/domain/AuditSchemaIT` | 3列の NULL 可と型、前の版の列だけの INSERT（Infrastructure Design の R-02） | NFR3.6 |
| `audit/testsupport/AuditRows` | 行の読み取りに3列を足す（既存の呼び出しは変えない） | `GroupAuditIT` が使う |
| `user/UserBoundaryArchitectureTest` | 「user に依存してよい機能」に `group` を足す（**既存の境界テストを緩める変更**。11節 Q2: A で承認済み。ほかの規則は緩めない） | group → user.service（メンバーの有無とまとめて読む口）。設計の依存の向き（`functional-spec.md` 1節） |

`AuditEventTest` の「32 文字以内」のテストは列挙の全値を回すため、足した値も自動で対象になる（書き換えない。AC1.1.16）。

### 4.6 負荷の道具・文書

| ファイル | 中身 |
|---|---|
| `perf/k6/scenarios.js` | 場面 `groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`・`groupPoolLimit`。一覧 `GROUP_SCENARIOS` と `setupTimeout = '10m'`。閾値は8場面に `iteration_duration{scenario:…}` の `p(95)<1000` と `checks{scenario:…}` の `rate==1`（`http_req_duration{name:…}` は名前のタグを付けて並べて記録するだけ）。`groupPoolLimit` は閾値なしで状態コードの件数を `groupPoolLimit_204`・`_409`・`_500`・`_other` に数える。繰り返しの中に `sleep` を置かない。名前は VU の番号を含めて一意（`perf-rename-<VU>-a`・`-b`、`perf-create-<VU>-<回>`）。トークンは `setup()` で試験用の管理者から取る（初期管理者は使わない） |
| `perf/README.md` | 「グループの管理の場面（Intent 261004-role-menu の U3）」の節: 場面の表、データの用意（SQL の形と数、10節 D-15）、`setup()` が作るもの、使い捨ての環境の一時の `app.env`（合否の回 `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`、記録の回 `10`、どちらも `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`）、判定（`iteration_duration` と `checks`、接続の合否の回は時間切れ 0・500 が 0 件・`acquire` の最大 20 ms 未満、記録の回は `acquire` 10 ms 以上で届いた証拠・40 VU の流し直しは1回まで）、`baseUnit` の読み方、`caffeinate -i` で台本全体を包むこと、片付け |
| `README.md` | 「グループの管理の API（Intent 261004-role-menu の U3）」の節（7つの口・code・監査・一意の鍵の待ちが約 2 秒・同じ名前の重なりの 409 の code が分かれること）、「スキーマの変更（Flyway）」に V10、「監査ログ（U4）」に足した種類と列 |

### 4.7 変えないもの（Step 20 で差が無いことを確かめる）

`backend/src/main/resources/application.yaml`、`backend/build.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`、`.github/**`、`compose.yaml`・`docker/**`（`docker/monitoring/` を含む）・`Dockerfile`、`frontend/**`（E2E のファイルを含む）、`vendor/**`、`common/**` の本体（とくに `packagesJudgedByTotal` の `common.error.*`・`common.web`・`common.health`）、`audit/repository/**`（`packagesJudgedByTotal` にある）、既存の境界テスト（`UserBoundaryArchitectureTest` の Q2 の1か所を除く）と `ArchitectureTest`。

## 5. カバレッジの一覧と境界テスト

| パッケージ | 本体に手が入るか | `packagesJudgedByTotal` にあるか | 扱い |
|---|---|---|---|
| `group.domain`・`group.repository`・`group.store`・`group.service`・`group.web`（新しい） | 作る | 無い | 自動でパッケージごとの下限（行 80%・分岐 70%） |
| `role.service`（新しい） | 作る（仮の実装1つ） | 無い | 同上。`RoleGroupDeletionGuardTest` で満たす |
| `audit.domain`・`audit.service` | 手を入れる | 無い（Intent 260925-user-management の B2 で外れた） | パッケージごとの下限のまま。Step 3 の基準と Step 22 の値を並べる |
| `user.domain`・`user.service` | 手を入れる | 無い（同上） | 同上 |
| `useradmin.domain`・`useradmin.web` | 手を入れる（`useradmin.web` は1行。設計の一覧に無かったパッケージ、10節 D-1） | 無い | 同上 |
| `audit.repository`・`user.repository`・`common.*` | 手を入れない | `audit.repository`・`common.error.*`・`common.web`・`common.health`・`common.i18n.domain` はある | 一覧の作業（下限を満たして外す）は付かない。Step 20 で本体の差が無いことを確かめる |

境界テスト: 新しい機能 `group` に `GroupBoundaryArchitectureTest`（`logical-components.md` 2節の6項目）。`role` に `RoleBoundaryArchitectureTest`（11節 Q1）。手を入れる既存の機能 `user`・`useradmin`・`audit` は境界テストを持っている（`team.md` の「無ければ足す」は当たらない）。

## 6. 当たりそうな所の洗い出し（前の2つの Bolt の経験 (a)〜(e) を受けて、計画の段で読んだもの）

| # | 当たりそうな所 | 読んで分かったこと | 手当て | 手順 |
|---|---|---|---|---|
| A1 | 既存の境界テスト `UserBoundaryArchitectureTest.onlyKnownFeaturesDependOnUser` | `user` に依存してよい機能を `audit`・`auth`・`dslmanage`・`invitation`・`useradmin` に限っている。group が `user.service` を使った時点で落ちる | 11節 Q2: A（承認済み）のとおり、一覧に `group` だけを足す | Step 12・16 |
| A2 | 既存のテストの名前の分け方（`*Test`・`*IT`） | 名前が `Properties` で終わるテストはどちらにも当たらず動かない（Infrastructure Design の R-01） | `GroupNamePropertyTest`（決定 (2)）。Step 20 で新しいテストのクラスの名前がすべて `Test` か `IT` で終わることを検索で確かめる | Step 9・20 |
| A3 | 計測の除外 `**/*Properties.class` | 本体のクラスの名前が `Properties` で終わると計測から外れる | 本体に `…Properties` という名前のクラスを作らない（除外を広げない） | Step 8〜14 |
| A4 | JPQL の予約語 | `Group` は JPQL・HQL の `GROUP BY` と重なる名前。H2 の `GROUPS`（窓の範囲の語）も予約語の候補 | エンティティ名を `UserGroup` にする（10節 D-3）。表名 `groups` は捨ての試しで動いたが、Step 7 の移行のテストと Step 11 の store のテストで確かめ、動かなければ止めて依頼者に諮る（表名は承認済みの設計の値） | Step 6・7・11 |
| A5 | `ArchitectureTest` の「依存はコンストラクターだけ」「トランザクションの境界は service だけ」「web は repository を使わない」 | `@PersistenceContext` の項目の注入は違反になる。`TransactionTemplate` は注釈ではないため当たらない | `GroupStore` は `EntityManager` をコンストラクターで受ける（`InvitationLockQueriesImpl` と同じ形） | Step 10 |
| A6 | `TraceAspect` の対象の式 | `..web..`・`..service..`・`..domain..`・`..repository..` の中の方法の引数・戻り値・例外の文を TRACE に出す。`group.store` は外 | 例外は store の中で区分に変え、外へはクラスの名前だけを持つ例外を出す。`GroupMember`・`GroupDetail` などの record は toString で氏名・メールアドレスを伏せる | Step 8・10・12 |
| A7 | `group.repository` に書き込みの方法を置かない決まり | `JpaRepository`・`CrudRepository` を継ぐと `save`・`delete` を持つ | `Repository` を継いで読み取りの `@Query` だけを置く（10節 D-4）。`GroupBoundaryArchitectureTest` で確かめる | Step 10・16 |
| A8 | `AuditEventListenerTest` の `containsOnlyKeys`（起動時の出来事の ERROR の項目） | 書き込みの失敗の ERROR の項目を一律に増やすと落ちる | 足す3つの項目は値があるときだけ載せる（10節 D-10） | Step 12 |
| A9 | `AuditSecretLeakIT` の列の一覧、`AuditSchemaIT` | 列を足すと必ず食い違う（前例: V6 のとき計画に無い変更になった） | 4.5 の書き換えに入れた（C2） | Step 7 |
| A10 | useradmin の既存のテストの code の期待 | `UserAdminOperationsApiIT`・`UserAdminProfileApiIT` は code の文字 `USER_NOT_FOUND` と文言だけを見る（移し替えで変わらない）。`UserAdminProblemTypesTest` は一覧の中身と `of` の同一性を見る | 4.5 の書き換えに入れた。画面の `features/useradmin` は code の文字だけを使うため変えない | Step 5 |
| A11 | 問い合わせの数の道具 | 設計は「Hibernate の統計」と書くが、既存の `UserAdminListQueryCountIT` は `auth/testsupport/SqlStatementCounter`（StatementInspector）を使う | 既存の道具に合わせる（10節 D-8） | Step 13・15 |
| A12 | 同時に持つ接続の数を数える道具 | 既存の `ConnectionAcquireCounter` は借りた回数だけで、同時の本数は数えない | テストだけの `ConnectionHoldRecorder` を足す（10節 D-9） | Step 15 |
| A13 | SpotBugs の関門 | JPQL の文字列の連結は `SQL_` で止まる。record の可変の集合は EI 系の指摘になりうる（priority 1 以外は警告） | JPQL は定数と名前の付いた引数だけ。集合は `Set.copyOf`・`List.copyOf` で受けて渡す | Step 10・12 |
| A14 | `verify` の時間 | 上限切れのテストは行 3 秒・鍵約 2 秒を実際に待つ | 上限切れのテストは #3・#4・#6 と `GroupBusyLogIT`・`GroupStoreConstraintIT` の上限切れの行に限る。時間は Step 22 で実測して記録する（目標は置かない） | Step 15・22 |
| A15 | colima のホームの共有が外れる（経験 (e)） | 外れると、ホームの下のファイルをコンテナに渡す処理（対象DB の初期化の SQL など）が空振りする。`verify` の Testcontainers は手元のファイルをコンテナに渡さないが、E2E の Mailpit と後の段の負荷の試験は compose を使う | Step 2・22・23 の前に `colima status` と `colima ssh -- ls <リポジトリの絶対パス>/docker` で共有を確かめ、外れていれば止めて依頼者に諮る（colima の再起動は配備したアプリも止めるため） | Step 2・22・23 |
| A16 | PC のスリープ（経験 (c)） | 長いコマンドが止まる | `verify`・基準の実測・E2E・`k6 inspect` は `caffeinate -i` で包む | Step 3・17・22・23 |
| A17 | 監査ログの追記（経験 (d)） | 記録のコミットの直後にも追記される | 3節のとおり、squash の前に `aidlc/` をコミットし、`git restore` を使わない | Step 24 |
| A18 | 画面・lint（経験 (a) の B1・B2 の例） | B3 は `frontend/` に触れない。`perf/k6/scenarios.js` は ESLint・Prettier の対象の外（`verify` の検査は `frontend/` だけ） | 既存の書き方（セミコロンなし・シングルクォート・インデント2）に手で合わせ、`k6 inspect` で読めることを確かめる | Step 18 |
| A19 | E2E の生成物 | `frontend/playwright-report`・`frontend/test-results` が残ると承認の場が止まる（`project.md` の学び） | Step 1 で無いことを確かめ、Step 23 の後に消す | Step 1・23 |

## 7. 手順

各手順の「対応」は8節の ID。チェックボックスは生成のときに付ける。

### Step 1: 作業の場の用意（ブランチの作成は依頼者の承認を得てから）
- [ ] 計画の承認と、計画までの記録のコミット（依頼者の承認を得て指揮役が行う）の後に、`develop` の先頭から `feature/261004-role-menu-b3` を作る。開始の時点の HEAD を `generation-notes.md` に記録する。
- [ ] `git status --short` でアプリのソースに未コミットの変更が無いことを確かめる（監査ログの追記はワークフローの記録として外して判断する）。`frontend/playwright-report`・`frontend/test-results` が無いことを確かめる。
- 対応: Testing Contract の「Project structure and production configuration skeleton」（新しいパッケージは Step 8 以降で作る）。

### Step 2: テストの実行の準備（最初のテストより前）
- [ ] `unit-test-instructions.md` 2節のコマンド（既存のテストを名指し）が動くことを確かめ、結果（件数・失敗 0）を記録する。
- [ ] colima の状態とホームの共有を確かめる（6節 A15）。
- [ ] 洗い出しの検索を流し直し、4.3・4.5 の一覧と増減が無いかを記録する: `git grep -n -F 'UserAdminProblemTypes.USER_NOT_FOUND' -- backend frontend perf`、`git grep -n -e 'TARGET_INVITATION_ID' -e 'containsOnlyKeys' -- backend/src/test`、`git grep -n -e 'resideOutsideOfPackages' -- backend/src/test`、`ls backend/src/main/resources/db/migration/`。
- 対応: Testing Contract の runner の手順。

### Step 3: 変更の前の基準
- [ ] colima の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` を流し、件数（単体・結合、失敗・飛ばし）と `backend/build/reports/jacoco/test/jacocoTestReport.xml` の `audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain`・`useradmin.web` の行と分岐の値、全体の値を記録する（`project.md` の学び）。
- [ ] `backend/build.gradle.kts` の `packagesJudgedByTotal` が7パッケージのままで、手を入れるパッケージ（5節）が入っていないことを記録する。
- [ ] 次の空き移行番号が V10 であることを確かめる（V1〜V9 だけがある）。違えば番号を合わせ、10節 D-13 に記録する。
- 対応: NFR6.4、NFR3.6、Infrastructure Design (i)。

### Step 4: 境界の口の移し替え — 実装（user.domain・user.service・useradmin.domain・useradmin.web）
- [ ] `UserProblemTypes` に `USER_NOT_FOUND` を移し、`UserAdminProblemTypes` から外す（`of` は user の定義を返す）。`UserAdminController` の1か所を替える。2つの一覧の付け替えを同じ変更で行う（code の二重の定義で起動が止まらないため）。
- [ ] `UserAccountService.findSummariesByIds(Set<Long>)` を足す（10節 D-2）。
- 対応: BR10.2、BR9.1、NFR1.6。

### Step 5: 境界の口の移し替え — テスト
- [ ] 4.5 の書き換え（4つのテスト）と `UserSummariesByIdsIT` を書く。
- [ ] `unit-test-instructions.md` 3.1 の Step 5 のコマンド（user・useradmin の単体と結合）を流し、通るまで直す。
- 対応: BR10.2、NFR1.6。コミットの区切り C1 の終わり。

### Step 6: データの形 — 実装（移行と監査の列。audit.domain）
- [ ] `V10__u3_group.sql` を 4.4 のとおり書く。
- [ ] `AuditEvent` の3項目・`withRoleGroupTarget`・detail の上限・toString、`AuditEventType`・`AuditFailureReason` の値を足す。group のクラスには依存しない（C2 の区切り）。
- 対応: BR8.4・BR8.6・BR8.8・BR8.9、NFR3.6・NFR5.1・NFR5.5、C10。

### Step 7: データの形 — テスト
- [ ] `AuditEventGroupTargetTest`、`AuditSchemaIT`・`AuditSecretLeakIT`（列の一覧）・`AuditRows` の書き換え、`AuditMigrationCompatibilityIT`（11節 Q4）を書く。移行の後に `groups`・`group_members` の表・制約の名前・`user_id` の索引があることも `AuditMigrationCompatibilityIT` か `GroupRepositoryIT` で確かめる（6節 A4）。
- [ ] `unit-test-instructions.md` 3.1 の Step 7 のコマンドを流し、通るまで直す。既存の監査のテスト（`cherry.mastersmith.audit.*`）を全部流し、既存の監査が壊れないことを確かめる。
- 対応: NFR3.6、NFR1.7（列）、NFR5.5、AC1.1.16。コミットの区切り C2 の終わり。

### Step 8: group のドメイン — 実装（group.domain）
- [ ] 4.1 の `group.domain` のファイルを作る（エンティティ・`GroupName`・出来事・detail・code・拒否の理由・メンバーの値）。
- 対応: BR1.1〜BR1.5、BR8.5・BR8.7、BR9.1、BR10.1、NFR1.11。

### Step 9: group のドメイン — テスト
- [ ] `GroupNameTest`（64 コードポイントちょうど（サロゲートペアを含む）・65・空・全角の空白だけ・改行・タブ・制御文字・「Sales」と「sales」と「SALES」の鍵・全角の「Ｓａｌｅｓ」は別の鍵）、`GroupNamePropertyTest`（jqwik。取り除いた結果に前後の空白が無い、受け付けた名前は 1〜64、鍵は大文字と小文字だけの違いで等しい、鍵の長さが 256 に収まる、名前の長さ（UTF-16）が 128 に収まる。失敗のときの乱数の種は既存の `exceptionFormat = FULL` で残る）、`GroupAuditEventTest`・`GroupAuditDetailTest`・`GroupProblemTypesTest`・`GroupMemberTest`（toString に値が出ない）を書く。
- [ ] Step 9 のコマンドを流す。
- 対応: AC2.1.2、BR1.1〜BR1.4、NFR1.11・NFR6.5（決定 (2)）、BR10.1。

### Step 10: DB アクセス — 実装（group.repository・group.store）
- [ ] 読み取りの repository（10節 D-4）と、`GroupStore`・`StoreOutcome`・`StoreFailureClassifier`・`GroupStoreUnexpectedException`（クラスの名前だけを持つ。11節 Q5: A）を作る。store の方法は「排他（`lockGroup`）」と「書き込みと flush（`insertGroup`・`renameGroup`・`deleteGroup`・`insertMember`・`deleteMember`）」に分ける（10節 D-5）。
- 対応: BR5.1・BR5.2・BR5.4・BR5.5、BR7、BR9.3、NFR1.8・NFR1.9・NFR2.3・NFR2.6・NFR3.3。

### Step 11: DB アクセス — テスト
- [ ] `StoreFailureClassifierTest`（上限切れ・23505 の制約の名前ごと・23503・想定外・待った後の違反 #9・包み直した例外が元の文を持たない）、`GroupStoreConstraintIT`（#7: 主キーの待たない違反と上限切れ、外部キーの2つの経路（10節 D-6）、上限切れの WARN の `lockKind`）、`GroupRepositoryIT`（ID の順・メンバーの数・詳細の並び・`groupIdsOfUser`・`memberUserIds` で存在しないグループを含めない）を書く。
- [ ] Step 11 のコマンドを流す。
- 対応: AC2.1.5（最後の守り）、BR5.2・BR5.4・BR5.5、BR7.2〜BR7.4、BR6.5、NFR1.8・NFR3.3・NFR3.7（T1〜T7 の本物での確かめ。8節の表）。

### Step 12: 業務処理 — 実装（group.service・role.service・audit の受け取り）
- [ ] `GroupAdminService`・`GroupStoreTransactions`・`FirstStep`・結果の型、`GroupBarrier`・`NoOpGroupBarrier`、`GroupMembershipQuery`・実装、`GroupDeletionGuard`・`DeletionDecision`、`GroupProblemTypeCatalog` を作る。
- [ ] `role.service.RoleGroupDeletionGuard`（仮の実装、11節 Q1: A）を作る。
- [ ] `AuditEventFactory.from(GroupAuditEvent)`・`AuditDetailJson`、`AuditEventListener.onGroupAuditEvent` と失敗の ERROR の項目（10節 D-10）を足す。
- [ ] `UserBoundaryArchitectureTest` の「user に依存してよい機能」の一覧に `group` だけを足す（11節 Q2: A で承認済み、D-20）。ほかの規則は変えない。
- 対応: BR3・BR4・BR5.1〜BR5.7・BR6.1〜BR6.5・BR7・BR8.1〜BR8.5、NFR3.4・NFR5.1、C4・C10。

### Step 13: 業務処理 — テスト
- [ ] `GroupAdminServiceTest`（操作ごとの判定の順（BR3.5）・拒否と出来事・`Referenced` の操作ごとの読み替え・`Busy` で出来事なし・`FirstStep` の `Done` 以外で巻き戻しの印）、`GroupStoreTransactionsIT`（T5）、`GroupMembershipQueryIT`（`lockForAssignment` の Locked(exists 真・偽) と Busy、外した直後の `groupIdsOfUser`）、`GroupMembershipQueryCountIT`（1回）、`GroupProblemTypeCatalogTest`、`RoleGroupDeletionGuardTest`、`AuditGroupEventFactoryTest`（種類・理由・対象・detail のキー、`GROUP_NOT_FOUND`・`USER_NOT_FOUND` の detail は空）、`AuditGroupEventListenerTest`（確定の後・失敗の ERROR の項目）を書く。
- [ ] Step 13 のコマンドを流す。
- 対応: AC2.1.3・AC2.1.9・AC2.1.10・AC2.2.1・AC2.2.10、BR3.5・BR3.6・BR4.1〜BR4.4・BR5.3・BR5.7・BR6.1〜BR6.5・BR8.1〜BR8.5、NFR2.3・NFR3.4。

### Step 14: API — 実装（group.web）
- [ ] `GroupAdminController` と DTO、入力の誤りの `fieldErrors`（`UserAdminFieldErrors` と同じ形）、`GROUP_IN_USE` の `members`・`assignedRoles`（`BusinessException` の追加の項目）。7つの口すべてに `@ApiAccess(ADMIN)`。
- 対応: AC2.1.6・AC2.1.7・AC2.1.8、AC1.1.15、BR2.1〜BR2.3、BR4.2、BR7.1、BR10.1、NFR1.1・NFR1.3・NFR1.4、C6。

### Step 15: API — テスト（結合）
- [ ] 4.5 の API の層のテストを書く。同時の重なり（#1〜#6）は `TestGroupBarrier` で作り、合否は 409 と code・状態が変わらないこと・監査の行・WARN で決める（経過の時間で決めない。#1・#5 の Javadoc に2.2 の NFR Design R-02 の注記）。問う口の Blocked と数は `TestGroupDeletionGuard`（`@Primary`）で作る。
- [ ] `AuditSecretLeakIT` のグループの行の確かめ（4.5）を流す。
- [ ] Step 15 のコマンドを流す。
- 対応: AC2.1.1〜AC2.1.8・AC2.1.11〜AC2.1.13、BR1.4・BR1.5・BR2・BR3.1〜BR3.4・BR4・BR5.2・BR5.5・BR5.7・BR7・BR8.1〜BR8.4・BR9・BR10、NFR1.1〜NFR1.4・NFR1.6〜NFR1.8・NFR2.6・NFR2.7・NFR3.1・NFR3.3〜NFR3.5・NFR5.1〜NFR5.3・NFR6.1。

### Step 16: 境界と構造の検査
- [ ] `GroupBoundaryArchitectureTest`（`group` が `role`・`audit`・`useradmin` に依存しない、`group` に依存してよいのは `role`・`audit` だけ、`group.store` を使うのは `group.service` だけ、`group.service` が `EntityManager` を使わない、`group.repository` に書き込みの方法・`@Modifying` が無く `CrudRepository` を継がない、トランザクションの境界は `group.service` の `TransactionTemplate` だけ。規則が依存を見分けていることの確かめを各規則に添える（既存の境界テストと同じ形））と `RoleBoundaryArchitectureTest`（11節 Q1: A。role がアプリの中で依存してよいのは `group.service` と `common` だけ、規則が依存を見分けていることの確かめを添える）を書く。
- [ ] `cherry.mastersmith.*BoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`ApiAccessConsistencyIT`・`PublicApiInventoryTest` を流す（B2 の検査で7つの口の印と管理者の道が合うこと）。
- 対応: NFR6.6、NFR1.3、AC1.1.15。

### Step 17: バックエンドの区切りの確かめ
- [ ] `./gradlew :backend:spotlessApply` の後、colima の環境変数を付けて `caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest` を流し、全体が通ることを確かめる。`TODO(B5)` は `RoleGroupDeletionGuard` の1か所だけであることを検索で確かめる。
- 対応: コミットの区切り C3 の終わり。

### Step 18: 負荷の台本（verify と CI の外。流すのは Performance Validation）
- [ ] `perf/k6/scenarios.js` に 4.6 の9つの場面と `GROUP_SCENARIOS`・`setupTimeout`・閾値を足す。`perf/README.md` にグループの節を書く（4.6、10節 D-15、2.2 の Infrastructure Design R-04・R-05）。
- [ ] 場面ごとに `SCENARIO=<場面> k6 inspect --include-system-env-vars perf/k6/scenarios.js` を流し、場面の名前・executor・閾値の式（`iteration_duration{scenario:…}`・`checks{scenario:…}`）・`setupTimeout: 10m` が意図どおりであることを記録する（`project.md` の学び）。使い捨ての環境での実行は行わない。
- 対応: NFR2.5・NFR2.7・NFR2.8、Infrastructure Design (iv)。

### Step 19: 文書
- [ ] README の3か所（4.6）を書く。設計の文書（承認済み）は書き換えない。
- 対応: NFR6.7。

### Step 20: 取り残しと変えないものの確かめ
- [ ] 4.7 のファイルが `develop` と差が無いこと（`git diff --stat develop -- <一覧>`）。`packagesJudgedByTotal`・計測の除外・`org.hibernate.orm.jdbc.error: OFF` が変わっていないこと。
- [ ] 新しいテストのクラスの名前がすべて `Test` か `IT` で終わること、`GroupNameProperties` の名前が残っていないこと（`git grep -n GroupNameProperties -- backend`）。本体に `Properties` で終わるクラスを作っていないこと。
- [ ] 新しい依存と lockfile の変更が無いこと。
- 対応: Infrastructure Design (vi)、決定 (2)、NFR6.4。

### Step 21: 記録（コード生成の段の成果物）
- [ ] `code-summary.md`（作ったもの・計画との差・承認の場で確かめること・後に回すこと（11節 Q5: A の SQLState と制約の名前、NFR 設計の R-09 のコメント、`RoleGroupDeletionGuard` の B5 での置き換え）・見せるものの `curl` の手順）、`traceability.json`（8節の対応。NFR 要件の N/A の ID も理由つきで載せる、2.2 の NFR Design R-07）、`source-manifest.json`（作った・変えたアプリのソースのすべて）、`generation-notes.md`（手順ごとのコマンドと結果）を書く。
- 対応: Testing Contract の「Documentation and traceability」。

### Step 22: 1コマンドの検査（統合の前の関門）
- [ ] colima の状態と共有を確かめ（6節 A15）、README の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。対象DB の3種類のテストの SKIPPED が 0 件、ログに「コンテナの実行環境」の警告が 0 件であることを確かめる。
- [ ] 件数（単体・結合・画面、失敗・飛ばし）、カバレッジ（Step 3 の基準と並べる。新しいパッケージと手を入れたパッケージ、全体）、`verify` の時間と Step 3 からの延びを記録する。単体テストの結果に `GroupNamePropertyTest` があることを確かめる。
- 対応: NFR6.4、Infrastructure Design (ii)・(iii)。

### Step 23: E2E（統合の前に手元で）
- [ ] `docker compose --profile mail up -d mailpit` の後に `caffeinate -i ./gradlew e2eTest` を流し、全体（今は 14 ファイル・177 件）が通ることを確かめる（認可と `USER_NOT_FOUND` の移し替えに手が入るため。110・120・130 を含む）。報告の確かめの道具の出力を記録する。
- [ ] `frontend/playwright-report`・`frontend/test-results` を消す。
- 対応: Infrastructure Design (v)、`cicd-pipeline.md` 7節。

### Step 24: コミットの提案・承認の場・統合の提案
- [ ] 指揮役が C1〜C4 の区切りを依頼者に提案し、承認を得てコミットする。承認の場は C4 の後、記録だけのコミットの前に開く。
- [ ] 承認の後、`aidlc/` の未コミットの変更（監査ログを含む）をコミットしてから、`develop` への squash の統合を提案する。統合の後にブランチを消す。push は依頼者。
- 対応: `team.md`・`project.md` の Change Control。

## 8. ストーリー・要件と手順の対応

| ID | 中身（要旨） | この単位の決まり・要件 | 手順 |
|---|---|---|---|
| AC2.1.1 | 作成とメンバーの追加、監査に残る（API の部分） | BR3.1・BR8.1 | 12〜15 |
| AC2.1.2 | 名前の重なり・空・上限 | BR1.1〜BR1.4 | 8・9・12〜15 |
| AC2.1.3 | メンバーか割り当てが残る削除の拒否 | BR4.1・BR4.3 | 12・13・15 |
| AC2.1.4 | 空のグループの削除と監査 | BR4.4・BR8.1 | 12・13・15 |
| AC2.1.5 | 削除とメンバーの追加の同時の重なり（ロールの側は B5） | BR5.1〜BR5.5・BR5.7 | 10・11・15 |
| AC2.1.6 | 401・403・成功・停止中の 403 の表（B3 は管理者の印の無い利用者で 403） | BR2.1 | 14・15・16 |
| AC2.1.7 | 拒否の応答に残りの数（文言は U6） | BR4.2 | 14・15 |
| AC2.1.8 | 詳細のメンバーの全件（候補の絞り込みは U6 と既存の一覧） | BR7.4 | 10・11・15 |
| AC2.1.9・AC2.1.10 | 外した後の所属の読み取り（作業ロールは U4） | BR3.6 | 12・13 |
| AC2.1.11 | 存在しない ID・招待中の人の拒否、停止中は足せる | BR2.3・BR3.1・BR3.2 | 15 |
| AC2.1.12 | 応答とログの漏えい | BR9.1〜BR9.3 | 15 |
| AC2.1.13 | 変えるものが無い3つの操作と監査 | BR1.5・BR3.3・BR3.4・BR8.2 | 12・13・15 |
| AC2.2.1 | 所属の読み取りの口（割り当ては U4） | BR6.4 | 12・13 |
| AC2.2.10 | グループの有無と排他の口（ほかの ID は U4） | BR5.3・BR6.4 | 12・13 |
| AC2.2.2〜AC2.2.9・AC2.2.11〜AC2.2.16 | 割り当て・作業ロール・画面 | — | Deferred（U4 role・U6 role-admin-ui）。`traceability.json` に Deferred で載せる |
| AC1.1.15 | API の分類の網羅 | BR2.1 | 14・16 |
| AC1.1.16 | 監査の種類・理由の名前が 32 文字以内 | BR8.9 | 7 |
| NFR1.1〜NFR1.4・NFR1.6〜NFR1.11 | 認可・分類・一括代入と IDOR・個人に関する値・監査と応答・例外の文・静的解析・入口の後の窓（受け入れ）・名前の入力 | `security-design.md` | 4・5・8〜16・22（NFR1.10 は検証の対象にしない受け入れの記録） |
| NFR2.1・NFR2.5・NFR2.7・NFR2.8 | 規模・応答時間・接続プール・件数の上限を置かない | `performance-design.md` 2節・`scalability-design.md` | 15（`GroupConnectionUsageIT`）・18（台本）。流すのは Performance Validation |
| NFR2.3・NFR2.6 | 問い合わせの数 | `performance-design.md` 1節 | 10・11・13・15 |
| NFR3.1・NFR3.3〜NFR3.6 | 同時の重なり・上限切れ・違反の後の確定・監査の失敗・移行 | `reliability-design.md` 2〜4節 | 6・7・10〜15 |
| NFR3.7 | 捨ての試しの結果（T1〜T7）を本物で確かめる | `reliability-design.md` 1節 | 下の表 |
| NFR5.1〜NFR5.3・NFR5.5 | 監査・指標・WARN・detail の数え方 | `observability-design.md` | 6・7・12・13・15 |
| NFR5.4 | 警報と SLO | — | Observability Setup（`monitoring-design.md` 7節） |
| NFR6.1・NFR6.4〜NFR6.7 | 必須のテスト・カバレッジ・性質ベース・境界・引き継ぎ | `tech-stack-decisions.md` | 3・9・15・16・22・21 |

捨ての試し（T1〜T7）を本物のテストで確かめる所:

| 試し | 本物のテスト（手順） |
|---|---|
| T1 同じ名前の同時の作成（待たない違反・上限切れ） | `GroupNameConflictIT` #2・#3（Step 15） |
| T2 同じ名前への同時の変更 | `GroupNameConflictIT` #4（Step 15） |
| T3 同じメンバーの同時の追加（主キー） | API では `GroupConcurrencyIT` #5（行の排他で並ぶ）、主キーの違反と待ちは `GroupStoreConstraintIT` #7（Step 11・15） |
| T4 削除とメンバーの追加の重なり、外部キー、行の上限 3 秒 | `GroupConcurrencyIT` #1・#6、`GroupStoreConstraintIT` の外部キーの2経路（Step 11・15） |
| T5 違反の後の2つ目のトランザクション | `GroupStoreTransactionsIT`・`GroupConflictAuditIT`（Step 13・15） |
| T6 例外の文に行の値が入る | `GroupUniqueViolationSecretLeakIT`・`GroupBusyLogIT`・`StoreFailureClassifierTest`（Step 11・15） |
| T7 例外の型での区分 | `StoreFailureClassifierTest`（Step 11） |

## 9. テストの量（Standard）

| 部品 | 単体 | 結合 |
|---|---|---|
| `GroupName` | `GroupNameTest` 8 件前後、`GroupNamePropertyTest` 5 性質 | — |
| 出来事・detail・code・メンバーの値 | 各 3〜6 件 | — |
| `group.store` | `StoreFailureClassifierTest` 6〜8 件 | `GroupStoreConstraintIT` 5 件前後 |
| `group.repository` | — | `GroupRepositoryIT` 5〜6 件 |
| `group.service` | `GroupAdminServiceTest` 8〜12 件（操作が5つのため） | `GroupStoreTransactionsIT` 2 件、`GroupMembershipQueryIT` 6 件前後、`GroupMembershipQueryCountIT` 3 件 |
| `group.web` | — | `GroupAdminApiIT` 8〜10 件、認可の表 28 行、一括代入 3 件、IDOR 6 件前後、同時の重なり 6 件（#1〜#6）、ログ・監査・指標・接続・漏えいの各 IT 2〜5 件 |
| `audit`（足す分） | 単体 3 クラス 4〜6 件ずつ | `GroupAuditIT` 6〜8 件、`GroupAuditWriteFailureIT` 1〜2 件、`AuditMigrationCompatibilityIT` 3 件 |
| user・useradmin（移し替え） | 書き換え 4 クラス | `UserSummariesByIdsIT` 3 件 |
| `role.service`（仮） | `RoleGroupDeletionGuardTest` 2 件 | — |
| 境界 | `GroupBoundaryArchitectureTest` 6 規則、`RoleBoundaryArchitectureTest` 2〜3 規則 | — |

E2E は足さない（画面は U6。`team.md` の本数の数え方で B3 は 0 本）。

## 10. この計画で決めたこと・承認済みの文書との差

承認済みの設計の文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 決めたこと | 理由・差 |
|---|---|---|
| D-1 | 本体に手が入るパッケージに `useradmin.web`（1行）を足す | 承認済みの `tech-stack-decisions.md`・`logical-components.md` 1節の一覧に無いが、`USER_NOT_FOUND` を参照しているため。`packagesJudgedByTotal` に無く、作業は付かない |
| D-2 | `user.service.findSummariesByIds(Set<Long>)` は既存の `UserRepository.findAllById` の1回の読み取りで作り、`UserAdminSummary`（toString で伏せる既存の型）の一覧を返す。`user.repository` には手を入れない | 新しい問い合わせを足さずに1回で読めるため。`User` のエンティティは toString を上書きしておらず値が出ない |
| D-3 | JPQL のエンティティ名は `UserGroup`（クラスは `Group`、表は `groups`） | `Group` が JPQL・HQL の `GROUP BY` と重なるため。表名は承認済みのまま。動かなければ止めて諮る（6節 A4） |
| D-4 | `group.repository` は `org.springframework.data.repository.Repository` を継ぎ、読み取りの `@Query` だけを置く | 書き込みの方法を置かない決まり（`security-design.md` 4.1）を、継ぐ方法でも守るため |
| D-5 | store の方法を排他と書き込み・flush に分け、`GroupStoreTransactions.inFirst` のラムダの中で待ち合わせの口と業務の判定を挟む。1つ目の結果は `group.service` の `FirstStep<T>`（`Done`・`Rejected`・`Store`）にし、`Done` 以外で必ず巻き戻しの印を付ける | NFR 設計の読み直しの R-10（2.3 の断片の型と粒度）。設計の形（`GroupStoreTransactions` に印を集める）は変えない |
| D-6 | `StoreOutcome.Referenced` は操作ごとに読み替える（削除 → `GROUP_IN_USE`、メンバーの追加 → `GROUP_NOT_FOUND`） | 承認済みの BR5.4 のとおり。NFR 設計の読み直しの R-04 |
| D-7 | 主キー・外部キーの確かめ（#7）は、テストの手伝いから `GroupStore` を `TransactionTemplate` の中で直接呼ぶ | API 経由では届かない重なり（`reliability-design.md` 4.1） |
| D-8 | 問い合わせの数は既存の `auth/testsupport/SqlStatementCounter` で数える | 設計の「Hibernate の統計」との差。既存の `UserAdminListQueryCountIT` と同じ道具にそろえる |
| D-9 | `GroupConnectionUsageIT` は、テストだけで `DataSource` を包む `group/testsupport/ConnectionHoldRecorder` で同時に借りている本数の最大を記録する。確定の後の監査の途中は `audit/testsupport/AuditWriteBarrierConfig` で止めて数える | 既存の `ConnectionAcquireCounter` は回数だけで同時の本数を数えないため |
| D-10 | 監査の書き込みの失敗の ERROR の項目に `targetRoleId`・`targetGroupId`・`detail` を値があるときだけ足す。`AuditEvent.toString` には `targetRoleId`・`targetGroupId` と detail の長さだけを足す（中身は出さない） | ERROR は「記録しようとした全項目」を出す既存の決まり（NFR10.3）に合わせる。値が無いときに足さないのは、既存の `AuditEventListenerTest` の `containsOnlyKeys` を変えないため。toString は TRACE の行が 16,384 文字まで膨らむのを避けるため（U4 の detail も同じ） |
| D-11 | detail の JSON は `audit.domain.AuditDetailJson` が Jackson で `GroupAuditDetail` の record ごとに決めたキーだけから作る | `observability-design.md` 1節（audit が写す）。部品の名前を決めただけ |
| D-12 | jqwik のテストの名前を `GroupNamePropertyTest` にする | Infrastructure Design の承認の場の決定 (2)。承認済みの `logical-components.md` 5節・`tech-stack-decisions.md` NFR6.5・`cicd-pipeline.md` 2節の `GroupNameProperties` との差 |
| D-13 | 移行は `V10__u3_group.sql`（Step 3 で次の空き番号を確かめる）。制約と索引の名前は 4.4 のとおり | NFR3.6。store の区分は制約の名前で読み替え先を決める（T7） |
| D-14 | 前の版との互換を、結合テスト（前の版の列だけの INSERT と、V1〜V9 だけの Flyway の確かめ）で確かめる | 11節 Q4。承認済みの設計は「前の版の起動は配備の段」だけ。B3 の「確かめること」（`bolt-plan.md`）を B3 の中で一部裏付ける |
| D-15 | k6 のデータ: 利用者は利用者の管理の節 2'' と同じ SQL の形で、メンバーの候補 `perf-gr-0001`〜`perf-gr-1000`（予約のドメイン `example.test`）と、操作する試験用の管理者 `perf-graop01`（管理者の印つき）を入れる。グループとメンバーは場面ごとの `setup()` が API で作る（一覧の場面はグループ 1,000、詳細の場面はメンバー 1,000 人のグループ1つ、削除は VU ごとに空のグループ 100、追加は VU ごとにグループ1つ（候補 100 人ずつを割り当て）、外しは VU ごとにメンバー 100 人のグループ、`groupPoolLimit` は VU ごとにグループ1つと利用者1人） | `cicd-pipeline.md` 5.2・10節、Infrastructure Design の R-05。初期管理者は使わない |
| D-16 | B3 の認可の表の 403 の主体は「管理者の印を持たない利用者」 | `security-design.md` 2節の B3 と B5 の差のまま。B5 で U4 が行を足す |
| D-17 | `application.yaml` を変えない（コメントも） | `cicd-pipeline.md` 9節 (vi)。NFR 設計の読み直しの R-09 は先送り |
| D-18 | メンバーの追加の利用者の有無は既存の `UserAccountService.findById` で確かめる（新しい口を足さない） | 招待中の人は `users` の行が無く、同じ判定で `USER_NOT_FOUND` になる |
| D-19 | 仮の問う口は `role.service.RoleGroupDeletionGuard`。B3 で最小の `RoleBoundaryArchitectureTest`（role がアプリの中で依存してよいのは `group.service` と `common` だけ）を足す | 11節 Q1: A。承認済みの設計は置き場を「`role` のパッケージ」とだけ書き、B3 での role の境界テストは書いていない（足す側の差） |
| D-20 | 既存の境界テスト `UserBoundaryArchitectureTest` の「user に依存してよい機能」の一覧に `group` だけを足す。ほかの規則は緩めない | 11節 Q2: A（`team.md` の「既存の境界テストを緩めるときは計画に明記して承認を得る」の承認）。承認済みの設計の依存の向き（group → user）に合わせるための差 |
| D-21 | コミットは場所ごとの4つ（C1〜C4）。どの時点でもコンパイルと流したテストが通る | 11節 Q3: A |
| D-22 | 想定外の DB の例外は、承認済みの設計どおりクラスの名前だけの例外に包み直す。SQLState と制約の名前を ERROR に出すことは後に回す | 11節 Q5: A。NFR 設計の読み直しの R-06 は先送り（上流との差は無い） |

## 11. 依頼者の答え（計画の承認の前に確かめたこと）

依頼者は5つの問いにすべて A と答えた（指揮役から伝達）。計画はこの答えで確定した形にしてある。

| 問い | 背景 | 答え | 反映先 |
|---|---|---|---|
| Q1 仮の問う口の置き場と、role の境界テスト | 承認済みの設計は「B3 は `role` のパッケージに仮の実装」とだけ書く。role の設計は本物を `role.service.RoleGroupDeletionGuard` と名付けている。`team.md` は新しく作る機能に `<機能>BoundaryArchitectureTest` を置くとしている | **A**. `role.service.RoleGroupDeletionGuard` に仮の実装を置く（B5 が本体を書き換える）。B3 で最小の `RoleBoundaryArchitectureTest`（role がアプリの中で依存してよいのは `group.service` と `common` だけ）を足す。B4 以降は role の Bolt が規則を足す | 4.2・4.5、Step 12・13・16、D-19 |
| Q2 既存の境界テスト `UserBoundaryArchitectureTest` を緩める | group が `user.service`（メンバーの有無とまとめて読む口）に依存するため、「user に依存してよい機能」の一覧に `group` を足さないと落ちる。`team.md` は既存の境界テストを緩めるときに計画に明記して承認を得るとしている | **A**. 一覧に `group` だけを足す。それ以外の規則（user が依存してよい先、common が user に依存しない、規則が依存を見分けていることの確かめ）は緩めない。U4 が user に依存するなら B4 以降で同じ承認を得る | 4.5、Step 12・16、D-20 |
| Q3 コミットの区切り | 前の Bolt では手順ごとの区切りがビルドの通らない区切りになった | **A**. 場所ごとの4つ（C1 user・useradmin／C2 移行と audit／C3 group・role と境界／C4 負荷の台本と文書）。どの時点でもコンパイルと、それまでに流したテストが通る | 3節、Step 5・7・17・19・24、D-21 |
| Q4 前の版との互換を B3 でどこまで確かめるか | `bolt-plan.md` の B3 の「確かめること」は前の版のアプリと既存の監査が壊れないこと。承認済みの基盤の設計は、前の版のイメージでの起動を配備の段の戻しの練習としている（基盤の読み直しの R-03） | **A**. B3 で結合テスト `AuditMigrationCompatibilityIT` を足す（前の版の列だけの INSERT が通り3列が空、V1〜V9 だけを読ませた Flyway がアプリと同じ設定で V10 まで当てた内部DB で止まらない、既存の行が読める）。前の版のイメージでの起動は配備の段に残す | 4.5、Step 7、D-14 |
| Q5 想定外の DB の例外の ERROR の手がかり（NFR 設計の読み直しの R-06） | 承認済みの設計は、想定外の例外をクラスの名前だけの例外に包み直す。SQLState・制約の名前は値を含まないが、落ちると調べの手がかりが減る | **A**. 承認済みの設計どおりクラスの名前だけにする。SQLState と制約の名前は先送りとして `code-summary.md` の「後に回すこと」に書く | 2.2、Step 10・11・21、D-22 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:1b1210c2e61bcacd9398989674a4d7c1a18abbfde6a25bf393014ab8e8f2f363",
  "contract_sha256": "sha256:0800cfde8b8915bab9d374a3c5479c6961cc23a534522745ba4170d1b16e7349"
}
```

## Build and Test に引き継ぐこと

- `develop` での `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の流し直しと CI の確かめ（`team.md`）。`verify` の時間の延び（上限切れのテスト）を Step 22 の値と並べる。
- カバレッジの実測の値（新しい `group.*`・`role.service` と、手を入れた `audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain`・`useradmin.web`）。
- 要件の網羅は FR・NFR → 機能設計の BR と NFR 要件の枝番 → この段の `traceability.json` の2段の連鎖でたどる（`project.md` の学び）。AC2.2.x の Deferred は U4・U6。

## Performance Validation に引き継ぐこと

- k6 の8場面（`groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`）を使い捨ての環境で流し、場面ごとに `iteration_duration{scenario:…}` の p95 < 1000 ms と `checks` の率 1 で判定する（NFR2.5・NFR2.8）。`http_req_duration` は並べて記録する。
- `groupPoolLimit` の合否の回（上限 11・書き込み 5 VU・2 分: 時間切れの累計 0・500 が 0 件・`acquire` の最大 20 ms 未満）と記録の回（上限 10・20 VU・2 分、`acquire` 10 ms 以上が届いた証拠、40 VU の流し直しは1回まで）。`acquire` は `baseUnit` を見てミリ秒にそろえる（NFR2.7）。
- 台本全体を `caffeinate -i` で包み、配備したアプリを止めて流す。手順は `perf/README.md` のグループの節。

## Observability Setup に引き継ぐこと

- ダッシュボードの区画（`monitoring-design.md` 5節。B3 は `docker/monitoring/` に触れない）。U4・U5 の `uri` の足し方を最初に決める（基盤の読み直しの R-06）。
- 既存の p95 の警報がグループの API を拾わないこと（`monitoring-design.md` 7節）と SLO の `Unverified` の扱い（NFR5.4）。

## 配備の段へ引き継ぐこと

- 入れ替えの前の内部DB の複写（スキーマが変わるため省けない。バックアップを兼ねる）と、前の版のイメージを移行の後の内部DB の複写と一時のボリュームで起動して `/actuator/health` の 200 を待つ戻しの練習（`infrastructure-specification.md` 4節）。前の版が Flyway の検証で止まらないこと、前の版が監査の行を足せることを合格の条件に入れる（基盤の読み直しの R-02・R-03。B3 の `AuditMigrationCompatibilityIT` は JDBC と Flyway の水準の確かめ）。U4 の移行と合わせて1回で扱う。
- スモークテストでグループの操作を行うときは監査に残るため、行う前に依頼者に伝え、期待の件数を合わせてから数える（`project.md` の学び）。

## U4 role（B4・B5）へ引き継ぐこと

- `role.service.RoleGroupDeletionGuard` の仮の実装（数 0・Allowed、`TODO(B5)`）を B5 で本物に置き換え、仮の実装が残っていないことを B5 の終わりの条件にする。
- `GroupMembershipQuery.lockForAssignment` を必ず通す、`GroupStoreTransactions` と同じ形（`RoleStoreTransactions`）、一意の鍵・主キーの待ちは約 2 秒、`AuditEvent.withRoleGroupTarget` と3列を使う、detail は `String.length()` で 16,384 まで（`logical-components.md` 6節）。
- B5 でグループの管理の7つの口の認可の表に「全スキーマを FULL にしたロールを作業ロールにする、管理者の印だけを欠く利用者」の行を足す（D-16）。role が user に依存するなら、`UserBoundaryArchitectureTest` を緩める承認を同じように得る（11節 Q2）。
