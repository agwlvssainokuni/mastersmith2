# Code Generation Plan — U4 ロール（role）

- 単位: U4 role（kind: service、大きさ XL）。設計の段は1つの単位として通し、コード生成で3つの Bolt に分ける（`project.md` の学び、`bolt-plan.md`）。
- Bolt: **B4**（ロールと設定と木の API）→ **B5**（割り当て・作業ロール・有効な作業ロール・解決の口・自分の権限の API・`RoleGroupDeletionGuard` の本物の実装）→ **B6**（権限の YAML の書き出し・確かめ・適用）。先に作った Bolt はどれも `develop` へ統合済み（B2 cross-cutting 65a76c5、B1 dsl-v2 44677cc、B3 group f9d1134）。計画の時点の `develop` の先頭は f9d1134。
- 範囲と量: スコープ classic、Test Strategy Standard（部品ごとに 5〜8 件、要所の結合テスト）、方法は test-after（末尾の Testing Contract）。
- この計画は Part 1（計画）だけで、計画の承認の前にコード・テスト・設定を書かない。手順の番号は3つの部を通して振る（B4 は Step 1〜23、B5 は Step 24〜45、B6 は Step 46〜67）。
- 計画の承認の前に依頼者に確かめた判断は 13節（Q1〜Q9、どれも A）。4〜12節は答えで確定した形にしてある。計画の承認の場で確かめる点は 13節の後（D-11・D-12）。
- 計画の最後（13節の後）の Testing Contract は `aidlc engine testing-posture render` の出力（`tc4.md`）をそのまま連結し、バイトの一致を確かめた。

## 1. 入力にした設計

| 段 | 文書（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下） | 使う所 |
|---|---|---|
| Functional Design | `construction/role/functional-design/functional-spec.md`（1〜11節）・`rules.md`（BR1.1〜BR13.2）・`entities.md`・`traceability.json` | 作るものの形と流れ（2.1〜2.16）、決まり、7節のテストの観点、8節の契約との差、9節の Bolt との対応、11節の承認の場の直し |
| NFR Requirements | `construction/role/nfr-requirements/` の `security-requirements.md`（NFR1.1〜NFR1.11）・`reliability-requirements.md`（NFR3.1〜NFR3.8）・`performance-requirements.md`（NFR2.2〜NFR2.6・NFR2.10）・`scalability-requirements.md`（NFR2.1・NFR2.7〜NFR2.9・NFR2.11）・`observability-requirements.md`（NFR5.1〜NFR5.6）・`tech-stack-decisions.md`（NFR6.1・NFR6.2・NFR6.4〜NFR6.6、コード生成への引き継ぎ、契約との差） | 確かめの要件、Bolt ごとの持ち分、新しい依存を足さないこと |
| NFR Design | `construction/role/nfr-design/` の `reliability-design.md`（捨ての試し U1〜U5、2.1〜2.6、3節、4.1〜4.3、5節、6節）・`security-design.md`（1〜7節）・`scalability-design.md`（1〜2.4）・`performance-design.md`（1〜4節）・`observability-design.md`（1〜4節）・`logical-components.md`（L1〜L15、4〜6節）・`nfr-design-questions.md`（Q1〜Q5、どれも A） | 部品の置き場（`role.store`・`role.transfer`）、結果の型、待ち合わせの4つの点、`RoleStoreTransactions`、`RoleTransferSlot`、14行の重なりの表、k6 の場面と合否 |
| Infrastructure Design | `construction/role/infrastructure-design/` の `infrastructure-specification.md`（1〜6節）・`cicd-pipeline.md`（1〜11節）・`monitoring-design.md`（1〜7節）・`infrastructure-design-questions.md`（Q1: A・Q2: A） | 移行の番号と中身、`verify` の段、jqwik のテストの名前、負荷の試験のデータの用意、Tomcat の確かめ、E2E、統合、変えないもの |
| Inception | `inception/units-generation/unit-of-work.md`（U4）・`unit-of-work-story-map.md`、`inception/requirements-analysis/requirements.md`（FR1〜FR8・FR12、NFR1〜NFR6、C1・C2、前提 A6）、`inception/user-stories/stories.md`（US1.1・US1.2・US2.2・US3.1・US4.1・US4.2、関わる US2.1・US5.1）、`inception/contract-design/contract-summary.md`（C3・C4・C5・C7・C8・C10）、`inception/domain-design/components.md`（RoleManagement・GroupManagement・DslDefinition・AuditLog・UserAccount）・`decisions.md`（ADR-001〜ADR-007）、`inception/delivery-planning/bolt-plan.md`（B4〜B6） | 範囲・ID・終わりの条件・見せるもの |
| 先に作った Bolt の記録（読み取りだけ） | `construction/{cross-cutting,dsl-v2,group}/code-generation/` の `code-generation-plan.md`・`code-summary.md`・`generation-notes.md`。group のコード生成のレビュー `.aidlc-reviews/code-generation/units/group/152fd96411988a79/1.json`（R-01〜R-06） | 計画の形、コミットの区切り、途中で止まった所、group の形（`GroupStoreTransactions`・`FirstStep`・`GroupBarrier`・`UncommittedWrite`・区分の SQLState 23503・23506 と H2 の索引名）、B5 への引き継ぎ |
| 読み直しの記録 | `.aidlc-reviews/functional-design/units/role/`（2回）・`.aidlc-reviews/nfr-requirements/units/role/`（2回）・`.aidlc-reviews/nfr-design/units/role/`（2回、最後は `a0741e66bbcee445`、R-11〜R-16 が New）・`.aidlc-reviews/infrastructure-design/units/role/6c988a88edfc1ecc/`（R-01〜R-07 が New） | 2.2 の残った指摘の扱い |
| 監査ログ | `audit/sakura-local-4e42a93f87ce.md` の GATE_APPROVED・GATE_REJECTED（Functional Design・NFR Requirements・NFR Design・Infrastructure Design） | 2.1 |
| 既存のコード（読み取りだけで確かめた） | `backend/src/main/java/cherry/mastersmith/` の `group/**`（`GroupStore`・`StoreFailureClassifier`・`StoreOutcome`・`GroupStoreTransactions`・`FirstStep`・`GroupBarrier`・`GroupMembershipQuery`（`lockForAssignment` は呼び出し元のトランザクションの中だけ、`memberUserIds`）・`GroupDeletionGuard`・`DeletionDecision`・`GroupRowLock`・`GroupRequestContextResolver`）、`role/service/RoleGroupDeletionGuard`（B3 の仮の実装、`TODO(B5)` の1か所）、`audit/**`（`AuditEvent.withRoleGroupTarget`・`AuditEventType`（27 値）・`AuditFailureReason`（21 値）・`AuditEventFactory`・`AuditDetailJson`・`AuditEventListener` の `roleGroupFields`）、`dsl/service/**`（`ActiveDslModelProvider.current()` → `ActiveDsl.Present(model, dslHash)`／`Absent`、`SafeYamlReader.read(byte[], SafeYamlLimits)` → `SafeYamlResult.Parsed(JsonNode, YamlPositions)`／`Rejected(kind, line, column, path)`、`ActiveDslModelHolder`）、`dsl/domain/DslModel`・`DslSchema`・`DslTable`（`columns` は DSL の順の対応表）、`user/service/UserAccountService`（`findById`・`findSummariesByIds`）・`UserAdminSummary`・`UserSummary`、`user/domain/RequestOrigin`・`RedactedText`、`common/persistence/RowLockFailures`（`warn(Logger, String, Throwable)` は例外が要る）、`common/web/RequestBodyLimitRoute`・`RequestSizeLimitFilter`、`common/security/ApiAccess`・`ApiAccessLevel`、`access/domain/AdminPaths`、`common/error/web/GlobalExceptionHandler`（想定外の 5xx は `setCause` つきの ERROR）、`common/observability/TraceAspect`（`..web..`・`..service..`・`..domain..`・`..repository..` だけが対象）、`dslmanage/web/DslHeavyOperationGate`（`Semaphore(1)` の形）・`DslWebConfig`（`RequestBodyLimitRoute` の登録の形）・`dslmanage/service/DslLifecycle`（適用は対象DB に接続しない）・`DslPreviewAnalysis`（照合に失敗しても警告になるだけ）、`useradmin/web/UserAdminController`（`/api/admin/users/{userId}/…` の道）、`user/web/MeController`（`/api/me` の下）。`backend/src/main/resources/application.yaml`（`server.tomcat.max-swallow-size: 11MB` は B1 で入った、`org.hibernate.orm.jdbc.error: OFF`）、`db/migration/`（V1〜V10）、`backend/build.gradle.kts`（`*Test`・`*IT` の名前の分け方、計測の除外 `**/*Properties.class`、`packagesJudgedByTotal` の7パッケージ、テストの JVM のヒープ 1g）。テストは `ArchitectureTest`・`ApiAccessArchitectureTest`（`MIN_ENDPOINTS = 34`）・`ApiAccessConsistencyIT`・`ApiAccessRulesTest`・`PublicApiInventoryTest`・15 の `*BoundaryArchitectureTest`（`UserBoundaryArchitectureTest` の「user に依存してよい機能」は `audit`・`auth`・`dslmanage`・`group`・`invitation`・`useradmin`）、`role/RoleBoundaryArchitectureTest`（B3 の1規則）・`role/service/RoleGroupDeletionGuardTest`、`group/web/GroupAdminAuthorizationApiIT`（28 行）、`group/testsupport/**`（`ConnectionHoldRecorder`・`UncommittedWrite`・`TestGroupBarrier`・`TestGroupDeletionGuard`・`GroupActors`）、`auth/testsupport/SqlStatementCounter`、`audit/testsupport/**`、`invitation/testsupport/RawHttp`、`common/testsupport/TestDatabase`。`perf/k6/scenarios.js`（1,261 行。場面の一覧ごとの `setupTimeout`、`GROUP_SCENARIOS`）・`perf/README.md`（手順 2''）・`perf/make-large-dsl.mjs`（版 2）、README（「グループの管理の API」「スキーマの変更（Flyway）」「監査ログ（U4）」）、`frontend/e2e/`（14 ファイル） | 影響の範囲と当たりそうな所（6節） |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログの GATE_APPROVED・GATE_REJECTED と各文書の「承認の場の決定」の節から洗い出したもの）

| 段（日付） | 監査ログの User Input・Feedback | この単位に当たる中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Delivery Planning（2026-10-04） | Approve | B4〜B6 の終わりの条件・確かめること・見せるもの（`bolt-plan.md`）。B4 のロールの削除は割り当てが無い前提で確かめ、B5 で割り当てが残るときの拒否を足す。Should（AC1.2.7・AC1.2.16・US4.2）は Bolt の中に入れ、時間が足りなければ計画の承認で諮る | 各部の「終わりの条件」にそのまま写す。Should は 13節 Q9 | 全体 |
| Functional Design（2026-10-05） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | R-01: 割り当ての一覧のグループ経由の利用者は `GroupMembershipQuery.memberUserIds` でまとめて1回（BR6.11）。R-02: 保存がすでに同じロールを指すときだけ何もしない成功、読み替え中は保存を書き監査を残す（BR7.6）。`GET /api/admin/roles/{roleId}` を足す（BR3.7）。木の名前は問い合わせの引数で受け、長さで拒否しない（BR4.12）。外しで存在しないグループは `ROLE_NO_CHANGE`（BR6.6）。`nameKey` は Java の1か所の関数で作る通常の列（BR1.4） | そのまま作る | Step 4〜15（B4）、Step 27〜38（B5） |
| NFR Requirements（2026-10-05） | GATE_REJECTED「Major 11 件だけを直す」→ Approve | R-01: `resolve` は写しを使わず、決める読み取り 4 回と祖先の行 1 回の合計 5 回以内（契約 C5 との差）。R-02: `RoleConnectionUsageIT`（書き込み 2・読み取り 1、3 以上は見積もりの誤り）とテストの構成だけで `DataSource` を包む部品。`rolePoolLimit` は補助の確かめ | `EffectivePermissionResolverImpl` を2つの読み方で作る（Step 33）。`RoleConnectionUsageIT` は B4・B5・B6 でそれぞれの経路を足す（Step 13・36・58） | Step 13・33・36・58 |
| NFR Design（2026-10-06） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | そろえる点1（接続の合否）: 合否の回は上限 11・書き込み 5 VU・2 分で、時間切れの累計 0・500 が 0 件・`acquire` の最大 20 ms 未満。上限 10・20 VU の回は記録だけ（40 VU の流し直しは1回まで）。そろえる点2: 巻き戻しの印は `RoleStoreTransactions` に集める（`status.setRollbackOnly()`）。そろえる点3: 一意の鍵・主キーの待ちは H2 の既定の約 2 秒のまま受け入れ、NFR3.3 の 3 秒は行の排他のヒントだけ。`RoleTransferSlot`（Q5: A）。待ち合わせの口は4点。#9（`MERGE`）は B5 の始めに確かめる | テストの期待は「行 3 秒・鍵約 2 秒」を前提にし、合否は経過の時間で決めない（409・状態・監査の行・WARN）。`rolePoolLimit` の台本と README は2つの回を書き分ける（Step 39）。`MERGE` は Step 28 で確かめる | Step 9〜15、28〜38、39、53〜60 |
| Infrastructure Design（2026-10-06） | Approve（まとめの確認は Looks correct。承認のコミット c97f760「Major 5 件の直し方は承認の場の決定としてコード生成の計画へ」） | role の読み直し（`6c988a88edfc1ecc`）は READY で Major なし。Major 5 件はほかの単位のもの（group の計画 2.1 の決定 (2) など）。role に当たるのは、Q1: A（`max-swallow-size` は B1 で足し、B6 で実際の Tomcat で確かめる。B1 で入ったことを確かめた）、Q2: A（負荷の試験のデータは DSL を DSL の管理の API、ロールと権限を import の API で入れる）、jqwik のテストを `*PropertyTest` の名前にすること（`cicd-pipeline.md` 11節）、監視は `docker/monitoring/` に触れないこと | `PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest` の名前で作る（承認済みの `tech-stack-decisions.md` NFR6.2・`logical-components.md` 5節の `*Properties` との差は D-12）。読み直しの Minor は 2.2 | Step 7・30・52・58（Tomcat）・21・43・65（単体テストの結果に4つが出ること） |
| Code Generation の始め（依頼者の決定、指揮役から伝達） | B2 → B1 → B3 → B4 → B5 → B6 の順 | B2 の `ApiAccess` の構造の検査と実行時の検査が入った（足す口はすべて印を付けないと落ちる）。B1 で `SafeYamlReader`・版 2 の DSL・`max-swallow-size` が入った。B3 で `GroupMembershipQuery`・`GroupDeletionGuard`・監査の3列・仮の問う口が入った | role の口すべてに印を付け（Step 12・35・57）、B2 の検査を各 Bolt の境界の手順で流す | Step 14・37・59 |

### 2.2 読み直しで残った指摘の扱い（承認の場で直していないもの）

| 読み直し | ID | 中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| group のコード生成 | R-04（B5 へ持ち越し） | 403 を確かめる利用者が「要る権限だけを欠く利用者」になっていない。仮の問う口は置き換えを忘れても落ちるテストが無い | **B5 で手当てする**（13節 Q3）。(1) B5 で「全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールに持ち、管理者の印だけを欠く利用者」を作る手伝いを足し、role の管理の口・group の7つの口・既存の管理の口すべての 403 をその利用者で確かめる。(2) 本物の割り当ての表に行を作って本番の Bean（`@Primary` の差し替えなし）の `canDelete`・`assignedRoleCounts` とグループの削除の API を確かめる `RoleGroupDeletionGuardIT`・`GroupDeletionWithRoleIT` を足す。仮の実装（常に `Allowed`・0）が残っていればこの2つが落ちる。(3) `TODO(B5)` が残っていないことを検索で確かめる。どれも B5 の終わりの条件に入れる | Step 33・34・36・41・43 |
| group のコード生成 | R-05 | 区分が H2 の例外の文の制約名・表名に頼り、H2 を上げると `UNEXPECTED` に落ちうる | role の区分も同じ形のため、**記録だけ**: `code-summary.md` の「後に回すこと」に、role の区分も H2 の文に頼ること、見張りは `RoleStoreConstraintIT`・`RoleStoreKeyGuardIT` であることを書く（`project.md` の Tech Stack の H2 の項への追記は学びの段で依頼者に諮る） | Step 20・42・64 |
| group のコード生成 | R-01 | 前の版が監査の行を一覧で読む口の呼び出しが main に無い | role は監査の表に列を足さない。配備の段の合格の条件は group の引き継ぎのまま（「前の版が監査の行を実体として読む経路が無いことの再確認」）。role の種類（`ROLE_*`・`WORK_ROLE_SWITCHED`）の行も同じ扱いと引き継ぐ | 配備の段へ |
| NFR Design | R-11（Minor） | 入口は本文を読む前に取るとあるが、`RequestSizeLimitFilter` は Content-Length の無い分割送信を上限まで先に読む | **記録だけ（B6）**: 受け入れた制約として `code-summary.md` と README の役割の受け渡しの節に「分割送信の本文は既存のフィルターが上限（10 MiB）まで先に読む。入口はその後の読み込み・木・適用を守る」と書く | Step 62・64 |
| NFR Design | R-12（Minor） | 入口の `ROLE_BUSY` の WARN に `RowLockFailures.warn` を使えない（例外が要る、決まった文が合わない） | **B6 で手当てする**（D-20）: 入口の WARN は role の中の専用の出し方（キーは `lockKind=ROLE_TRANSFER_SLOT` だけ、文は「権限の受け渡しの入口を取れませんでした」）。`RoleBusyLogTraceIT` は入口の経路の期待（`exceptionClass` を持たない）を分けて書く | Step 55・58 |
| NFR Design | R-13（Minor） | `RoleStoreTransactions` は TraceAspect の対象のため、`RoleStoreOutcome` とその値が TRACE で文字列にされる | **B4 で手当てする**（D-5）: `RoleStoreOutcome` の各 record と `RoleFirstStep` の toString を種類と件数だけにする。`Done` の値の型（エンティティ・名前の組など）も toString で値を出さない。`RoleSecretLeakIT` の TRACE の確かめに含める | Step 8・10・13 |
| NFR Design | R-14（Minor） | 業務の拒否を `Done` にするか非 `Done` にするかが group と違い、一覧が無い。重ねての割り当ての違反から `ROLE_NO_CHANGE` と監査までを通すテストが API から無い | **B4 で手当てする**（13節 Q2: A）。group の実装と同じ形（業務の拒否も巻き戻し、2つ目のトランザクションで失敗の出来事）。拒否と違反の一覧を 4.3 の表で決める。違反の経路は `RoleAssignmentServiceTest`（store が `AlreadyAssigned` を返す）と `RoleStoreTransactionsIT` で通す | Step 10・11・33・34 |
| NFR Design | R-15（Minor） | 作業ロールの保存の `MERGE INTO … KEY(user_id)` は未確定の同じ主キーで待つか、違反か、ほかの誤り（90131 など）かを試していない | **B5 の始めに確かめる**（D-19）。Step 28 の `WorkRoleSelectionMergeIT` で、未確定の行を `UncommittedWrite` で持ったまま `MERGE` を流し、SQLState と誤りの番号を記録する。23505・上限切れ以外が出たら、行の有無を読んでから更新か挿入を行う形に切り替え（主キーの違反は `Busy("WORK_ROLE_SELECTION_KEY")`）、差を `code-summary.md` に書く | Step 28・31 |
| NFR Design | R-16（Suggestion） | 本文をゆっくり送る管理者が入口を持ち続ける | **記録だけ（B6）**: 受け入れた制約に1行（本文の読み込みの時間切れは Tomcat の既定に頼る） | Step 62・64 |
| Infrastructure Design | R-01（Minor） | Tomcat の読み捨ての確かめの送り手が未定 | **B6 で手当てする**（D-24）: 生のソケット（`invitation/testsupport/RawHttp` と同じ考え方の `role/testsupport/RawTransferPost`）で、要求の行・ヘッダー・本文 10 MiB を書き切ってから応答を読む。時間ではなく送り手の作りで決定的にする | Step 58 |
| Infrastructure Design | R-02（Minor） | `max-swallow-size` で未認証の要求も最大 11MB を読み捨てる影響が書かれていない | **記録だけ（B6）**: B1 の記録に影響が書かれているかを Step 47 で確かめ、無ければ README の役割の受け渡しの節に受け入れる影響（未認証でも最大 11MB を読み捨てる、スレッドの占有が延びる、配備先が決まったときに見直す）を書く | Step 47・62 |
| Infrastructure Design | R-03（Minor） | 戻しの引き継ぎに、前の版へ戻すと DSL の版 2 が読めず DSL が無い状態で起動することへの参照が無い | **引き継ぎ（配備の段）**: 末尾の「配備の段へ引き継ぐこと」に、戻した版の DSL の状態（U2 の引き継ぎ）を確かめる対象として書く | — |
| Infrastructure Design | R-04（Minor） | 「戻したときの権限のずれ」の確かめの対象が曖昧 | **引き継ぎ（配備の段）**: 確かめる対象は「戻した版で管理の API の 403／200 が `users.admin_flag` のとおりであること。データの権限は前の版に無いこと」と書く。role は `admin_flag` から行を移さない（移行は表を足すだけ）ため、`team.md` の「行を移す移行」の結合テストは当たらない（D-27） | — |
| Infrastructure Design | R-05（Minor） | `roleTransferLarge` の後に準備の状態が変わる | **B6 で手当てする**: `perf/README.md` に、`roleTransferLarge` を最後に流す（流し直すときは使い捨ての環境を作り直して準備からやり直す）と書く | Step 61 |
| Infrastructure Design | R-06（Suggestion） | `acquire` を「秒」と固定して読む | **B5 で手当てする**: `perf/README.md` の `rolePoolLimit` の節に「`baseUnit` を確かめて秒にそろえてから 0.020 と比べる」と書く（合否の値は変えない） | Step 39 |
| Infrastructure Design | R-07（Suggestion） | 準備の DSL の適用に照合の対象DB が要るかを B4 の計画の最初に確かめる | **この計画で確かめた**（6節 A20）: DSL の管理の適用（`DslLifecycle.apply`）は対象DB に接続せず、プレビューの照合（`DslPreviewAnalysis`）は対象DB に届かなくても警告になるだけ。準備の台本は対象DB を要らない（使い捨ての環境の PostgreSQL の profile の表は作らない） | Step 61 |

### 2.3 この計画での読み方

- **設計の文書どおりに作るもの**: 5つの表と移行（`entities.md`、`infrastructure-specification.md` 2・3節）、名前の規則（BR1）、認可と入力（BR2）、ロールの管理（BR3）、権限の設定と木（BR4）、解決（BR5、NFR2.3 の2つの読み方）、割り当て（BR6）、作業ロール（BR7）、排他と待ち（BR8、`reliability-design.md` 2節）、YAML（BR9、`security-design.md` 5節）、問う口（BR10）、監査（BR11、`observability-design.md` 1節）、漏えい（BR12、`security-design.md` 4節）、誤りの code（BR13）、k6 の場面（`performance-design.md` 2節・`scalability-design.md` 2.2）。
- **層の順（test-after）**: Testing Contract の `ordering` のとおり、各 Bolt の中で層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。各 Bolt の層は「データの形（移行とエンティティ）→ ドメイン（純粋な関数・値・出来事・code、audit の写し方）→ DB アクセス（`role.repository` の読み取りと `role.store` の排他・書き込み）→ 業務処理（`role.service`）→ API（`role.web`）→ 境界と構造の検査」の順。B6 はドメインの後に `role.transfer`（YAML の純粋な部品）を置く。画面の層は持たない（Testing Contract の `plan_profile` の「Frontend behavior」は該当しない層として省く。画面は U6・U7）。
- **例外の文の経路**: 排他と、違反を起こしうる書き込み・flush・`@Modifying` の問い合わせ・import の JDBC のバッチは `cherry.mastersmith.role.store`（TraceAspect の対象の外）の `RoleStore` だけが行う。`role.repository` は読み取りだけ。`role.service` は `EntityManager`・`JdbcTemplate` を使わない（`RoleBoundaryArchitectureTest`）。`role.transfer` は DB に触れない。
- **トランザクション**: `@Transactional` を付けず、`role.service` の `TransactionTemplate` で組む。store を呼ぶ1つ目のトランザクションはすべて `RoleStoreTransactions` を通す。失敗の出来事の出し方は 13節 Q2: A のとおり（group の実装と同じく、`Done` 以外はすべて巻き戻し、2つ目の `TransactionTemplate`（書き込みなし）で失敗の出来事を出す）。入れ子の REQUIRES_NEW は使わない。
- **group の口の使い方**: role は `group.service` の `GroupMembershipQuery`（`groupIdsOfUser`・`exists`・`summaries`・`memberUserIds`・`lockForAssignment`）と `GroupDeletionGuard`（実装する側）だけを使い、`group.store`・`group.repository` に触れない。`lockForAssignment` は role の1つ目のトランザクションの中で、ロールの行の排他の後に呼ぶ（BR8.2）。
- **変えないもの**（各部の「変えないもの」と Step 19・41・63）: `backend/build.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend` の lockfile（新しい依存を足さない、NFR6.6）、`.github/**`、`compose.yaml`・`docker/**`（`docker/monitoring/` を含む）・`Dockerfile`、画面（`frontend/**`、E2E のファイルを含む）、`vendor/**`、`common/**` の本体（`packagesJudgedByTotal` の `common.error.*`・`common.web`・`common.health`・`common.i18n.domain` を含む。13節 Q5: A のとおり `common.error.web` にも手を入れない）、`access/**`・`auth/**` の本体、`audit/repository/**`、`users.admin_flag`。`application.yaml` は B4 のコメント1か所と B6 の1行だけ。

## 3. Bolt・ブランチ・統合・コミットの区切り

| 項目 | 扱い | 出典 |
|---|---|---|
| Bolt の番号と作業ブランチ | B4 は `feature/261004-role-menu-b4`、B5 は `feature/261004-role-menu-b5`、B6 は `feature/261004-role-menu-b6`。どれも、その時点の `develop` の先頭（前の Bolt を squash で統合した後）から、同じ作業フォルダで作る。worktree は使わない。作るのは依頼者の承認の後 | `team.md` の Way of Working、`cicd-pipeline.md` 9節 |
| 統合の前の関門（各 Bolt） | colima の状態とホームの共有を確かめ（6節 A15）、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（対象DB の3種類のテストを飛ばさない。SKIPPED が出たら統合しない）。続けて E2E の全体（`docker compose --profile mail up -d mailpit` の後に `caffeinate -i ./gradlew e2eTest`）。どちらも通るまで統合しない | `team.md`・`project.md` の学び、`cicd-pipeline.md` 2・8節 |
| 統合の形 | Bolt ごとに `develop` へ squash（B4・B5・B6 でそれぞれ1コミット）。サブモジュールの更新を含まないため fast-forward の例外に当たらない | `team.md`、`cicd-pipeline.md` 9節 |
| 統合コミットの件名（案） | B4: `B4 ロールの管理: ロールと権限の設定・木の API、継承の解決、移行 V11`。B5: `B5 割り当てと作業ロール: 割り当て・作業ロール・解決の口・自分の権限の API、問う口の本物の実装、移行 V12`。B6: `B6 権限の受け渡し: 書き出し・確かめ・適用の API、上限つきの読み込みと指紋` | `team.md`（日本語、内容が分かる件名） |
| 統合の後 | 作業ブランチを消す。`origin` への push は依頼者が行う。push の後の CI の失敗は `team.md` の「不安定なテストと CI の失敗」の決まりで扱い、次の Bolt に入る前に片付ける | `team.md` |
| 作業ブランチの上のコミットの区切り（13節 Q8: A で確定） | **各 Bolt で場所ごとの3つ**。どのコミットの時点でもコンパイルと、それまでに流したテストが通る区切りにする（前の Bolt の経験 (b)）。import の向きを確かめて決めた（下の表） | `project.md` の Change Control、経験 (b) |
| コミットの進め方 | 生成の担当はコミットしない。生成の後に指揮役が区切りを依頼者に提案し、承認を得てから行う（段の記録のコミットも別に提案する）。監査ログは記録のコミットの直後にも追記されるため（経験 (d)）、`aidlc/` の未コミットの変更（監査ログを含む）は squash の前にコミットしておき、`git restore` で消さない | `project.md` の Change Control の学び |
| Bolt の区切りでの確認 | B4・B5 の終わりは、見せるもの（`curl` の手順）・`code-summary.md` のその Bolt の節・コミットの区切りを依頼者に見せ、コミットと squash の統合の承認を得る。コード生成の段の承認の場は単位で1回で、B6 の最後のコードのコミットの後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く | `project.md` の学び |

コミットの区切り（import の向きを確かめた結果）:

| Bolt | コミット | 中身 | ビルドが通る理由 |
|---|---|---|---|
| B4 | C1 データの形・ドメイン・DB アクセス | V11、`role.domain`（エンティティ・名前・継承・出来事・detail・code）、`audit` の写し方と受け取り（`role.domain` だけに依存）、`role.repository`・`role.store`、`application.yaml` のコメント、`UserBoundaryArchitectureTest`（Q1）と `RoleBoundaryArchitectureTest` の追従、これらのテスト（Step 4〜9） | `audit` が使う型（`RoleAuditEvent` など）は同じコミットの `role.domain` にある。`role.domain` は `user.domain.RequestOrigin` に依存するため、境界テストの追従も同じコミットに入れる。`role.service` が無くても、store と repository は単独でコンパイルできる |
| B4 | C2 業務処理・API・境界 | `role.service`・`role.web`、境界と構造の検査、すべてのテスト（Step 10〜15） | C1 の型だけを使う |
| B4 | C3 負荷の台本と文書 | `perf/k6/scenarios.js`・`perf/README.md`・README（Step 17〜18） | Java に依存しない |
| B5 | C1 データの形・ドメイン・DB アクセス | V12、`role.domain` の追加、`audit` の追加、`role.repository`・`role.store` の追加、テスト（Step 27〜32） | 同上 |
| B5 | C2 業務処理・API・境界・group のテストの書き換え | `role.service`（`RoleGroupDeletionGuard` の本物を含む）・`role.web`、Q4 の `group.store` の1か所、`group` のテストの書き換え（Q3）、境界、すべてのテスト（Step 33〜38） | 同上 |
| B5 | C3 負荷の台本と文書 | Step 39〜40 | 同上 |
| B6 | C1 YAML の部品とドメイン | `role.transfer`、`role.domain` の追加、`audit` の追加、上限ちょうどの YAML の生成の部品（テストの手伝い）、テスト（Step 49〜52） | `role.transfer` は `role.domain` と Jackson の木だけを使う |
| B6 | C2 store・業務処理・API・設定 | `role.store` のバッチ、`role.service`・`role.web`、`application.yaml` の1行、テスト（Step 53〜60） | 同上 |
| B6 | C3 負荷の台本と文書 | Step 61〜62 | 同上 |

## 4. 3つの Bolt に共通の作り

パスはリポジトリのルートからの相対。新しいソースの先頭には Apache License 2.0 のヘッダー（2026、agwlvssainokuni、`/* ... */`、SQL は `--`）を置く。コメントと Javadoc は日本語、テストの説明文は英語。各 Bolt で作るファイルの一覧は 7〜9節。

### 4.1 新しいパッケージ `role` の全体（`backend/src/main/java/cherry/mastersmith/role/`）

| パッケージ | 受け持ち（`logical-components.md` の ID） | TraceAspect | 作る Bolt |
|---|---|---|---|
| `role`（直下） | `package-info.java`（依存してよい先・依存されてよい先） | — | B3 で作った。B4 で説明を直す |
| `role.domain` | エンティティ（`Role`・`PermissionSetting`(`Id`)、B5 で `UserRoleAssignment`(`Id`)・`GroupRoleAssignment`(`Id`)・`WorkRoleSelection`）、値（`RoleName`・`PermissionTarget`・`MainPermission`・`PermissionValues`・`InheritedFrom`・`EffectivePermission`・`WorkRoleRef`・`RoleSource`）、純粋な関数（L6 `PermissionInheritance`・B5 `WorkRoleChooser`・`PermissionTreeBuilder`）、監査（L11 `RoleOperation`・`RoleAuditEvent`・`RoleAuditFailure`・`RoleAuditDetail`）、code（`RoleProblemTypes`・`RoleRejection`） | 対象 | B4（B5・B6 で足す） |
| `role.repository` | L10 読み取りだけ（Spring Data の `Repository` を継ぎ、`@Query` の射影だけ。書き込みの方法と `@Modifying` を置かない） | 対象 | B4（B5 で足す） |
| `role.store` | L2 排他（ヒント 3,000 ms）・違反を起こしうる書き込みと flush・`@Modifying`・B6 の JDBC のバッチ。例外を中で受けて `RoleStoreOutcome` を返す。`role.service` からだけ呼ばれる | **対象の外** | B4（B5・B6 で足す） |
| `role.service` | L15 `RoleStoreTransactions`、L3 `RoleAdminService`（B4）・`RoleAssignmentService`・`WorkRoleService`（B5）・`RoleTransferService`（B6）、L4 `EffectivePermissionResolver`(`Impl`)（B5）、L5 `RoleGroupDeletionGuard`（B5 で本物）、L8 `RoleBarrier`・`NoOpRoleBarrier`、L9 `RoleTransferSlot`（B6）、L12 `RoleProblemTypeCatalog`、結果の型（sealed の record） | 対象 | B4（B5・B6 で足す） |
| `role.transfer` | L7 `RoleTransferReader`（木の検証と `TransferError`）・`RoleTransferWriter`（書き出しの文字列、二重引用符の規則）・`TransferFingerprint`・`TransferPlanner`（変わる点の一覧）、受け渡しの伏せる型（`RoleTransferPayload`・`RoleTransferTree`・`TransferPlan`・`TransferErrors`）。DB に触れない | **対象の外**（受け渡しは伏せる型） | B6 |
| `role.web` | L1 `RoleAdminController`・`RolePermissionController`（B4）、`RoleAssignmentController`・`RoleLookupController`（`/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles`）・`MeWorkRoleController`・`MyPermissionsController`（B5）、`RoleTransferController`・L13 `RoleWebConfig`（B6）、要求・応答の record、`RoleFieldErrors`、`RoleRequestContextResolver` | 対象 | B4（B5・B6 で足す） |

- 依存（`RoleBoundaryArchitectureTest`、NFR6.5）: role が依存してよいのは `group.service`・`group.domain`・`user.service`・`user.domain`・`dsl.service`・`dsl.domain`・`common`。`dsl.parse`・`dsl.validate`・`group.store`・`group.repository`・`user.repository` には依存しない。role に依存してよい機能は `audit`（`role.domain` の出来事）と、U5 の `navigation`（`role.service` の解決の口。B7 で作る）。
- 依存性の注入はコンストラクターだけ。`RoleStore` は `EntityManager`（B6 で `JdbcTemplate` も）をコンストラクターで受ける（`ArchitectureTest`）。
- 個人に関する値・秘密を持つ record（割り当ての一覧の利用者、YAML の中身の受け渡し）は toString で値を伏せる（`team.md` の Code Style）。氏名・メールアドレスは `user.service` の伏せ字の型（`UserAdminSummary`）で受け、応答の DTO を組み立てる時だけ文字列にする（BR12.1）。

### 4.2 誤りの code と状態コード（`role.domain.RoleProblemTypes`、BR13.1）

| code | 状態 | 足す Bolt | 監査 |
|---|---|---|---|
| `ROLE_NOT_FOUND` | 404 | B4 | 変える操作は FAILURE、1件の読み取りと木は残さない |
| `ROLE_NAME_DUPLICATE` | 409 | B4 | FAILURE |
| `ROLE_IN_USE` | 409 | B4（B4 では割り当ての表が無く起きない。B5 で働く） | FAILURE |
| `ROLE_NO_CHANGE` | 409 | B4 | FAILURE（理由は既存の `NO_CHANGE`） |
| `ROLE_BUSY` | 409 | B4 | 残さない（WARN） |
| `PERMISSION_TARGET_NOT_IN_DSL` | 409 | B4 | FAILURE |
| `DSL_NOT_APPLIED` | 409 | B4 | 保存・適用は FAILURE、木・確かめは残さない |
| `ROLE_NOT_ASSIGNED` | 409 | B5 | FAILURE |
| `ROLE_TRANSFER_INVALID` | 400 | B6 | 残さない |
| `ROLE_TRANSFER_STALE` | 409 | B6 | FAILURE |

共通の code（`VALIDATION_FAILED`・`MALFORMED_REQUEST`・`AUTHENTICATION_REQUIRED`・`ACCESS_DENIED`・`PAYLOAD_TOO_LARGE`・`UNSUPPORTED_MEDIA_TYPE`）、`USER_NOT_FOUND`（`user.domain`）、`GROUP_NOT_FOUND`・`GROUP_BUSY`（`group.domain`）は使い回し、role で重ねて定義しない（BR13.2）。0 以下の ID は存在しない ID として 404（NFR1.10、BR2.4 との差。D-13）。

### 4.3 拒否・違反・上限切れの扱いの一覧（NFR 設計の読み直しの R-14 の手当て。13節 Q2: A の形）

1つ目のトランザクションの結果は `role.service.RoleFirstStep<T>`（group の `FirstStep` と同じ形: `Done`・`Rejected(RoleRejection, detail)`・`Store(RoleStoreOutcome の Done 以外, detail)`）にし、`RoleStoreTransactions.inFirst` が `Done` 以外で必ず `status.setRollbackOnly()` を付ける。

| 経路 | 例 | 1つ目の結果 | 巻き戻し | 失敗の出来事 | ログ |
|---|---|---|---|---|---|
| 入力の誤り | 名前・値・ID の形・本文の項目（400） | トランザクションを始めない | — | 出さない | 既存の WARN（`GlobalExceptionHandler`） |
| 行が無い | 排他の読み取りで `RoleMissing`（`ROLE_NOT_FOUND`） | `Rejected` | する | 2つ目で FAILURE | 同上 |
| 業務の判定での拒否（書き込みの前） | `ROLE_NO_CHANGE`・事前の判定の `ROLE_NAME_DUPLICATE`・`ROLE_IN_USE`・`PERMISSION_TARGET_NOT_IN_DSL`・`DSL_NOT_APPLIED`（保存・適用）・`USER_NOT_FOUND`・`GROUP_NOT_FOUND`・`ROLE_NOT_ASSIGNED`・`ROLE_TRANSFER_STALE` | `Rejected` | する | 2つ目で FAILURE | 同上 |
| 違反の読み替え | `NameTaken` → `ROLE_NAME_DUPLICATE`、`AlreadyAssigned` → `ROLE_NO_CHANGE`、`Referenced(ROLE)` → 削除なら `ROLE_IN_USE`・割り当てなら `ROLE_NOT_FOUND`、`Referenced(USER)` → `USER_NOT_FOUND`、`Referenced(GROUP)` → `GROUP_NOT_FOUND` | `Store` | する | 2つ目で FAILURE | 同上 |
| 待ちの上限切れ | `Busy(ROLE_ROW・ROLE_NAME_KEY・ROLE_ASSIGNMENT_KEY・WORK_ROLE_SELECTION_KEY)`、`lockForAssignment` の `Busy`（`GROUP_BUSY`） | `Store` | する | 出さない | `RowLockFailures.warn` の WARN を1回（グループの行は `GroupStore` が出す） |
| 入口の取り損ね | 確かめ・適用の `RoleTransferSlot`（`ROLE_BUSY`） | トランザクションを始めない | — | 出さない | role の専用の WARN（`lockKind=ROLE_TRANSFER_SLOT`、D-20） |
| 何も変えない切り替え | 保存がすでに選んだロールを指す（BR7.6） | `Done`（書かない） | しない | 出さない | なし |
| 成功 | 変える操作 | `Done` | しない | 1つ目の中で SUCCESS を出し、確定の後に `AuditEventListener` が記録する | なし |
| 読み取り | 一覧・1件・木・割り当て・作業ロールの読み取り・自分の権限・書き出し・確かめ | 読み取りだけのトランザクション1つ | — | 出さない | なし |

- 13節 Q2: A で、承認済みの `reliability-design.md` 2.4（業務の拒否は `Done` の値で返し、1つ目の中で FAILURE を出して確定）ではなく、group の実装と同じこの一覧の形にした（D-4）。
- 違反の経路の失敗の監査は、API から届く経路（同じ名前の同時の作成 #2）を `RoleConflictAuditIT` で、API から届かない経路（割り当ての主キー）を `RoleAssignmentServiceTest`（store の偽物が `AlreadyAssigned` を返す）と `RoleStoreTransactionsIT` で通す（R-14 の後半）。

### 4.4 待ち合わせの口（`RoleBarrier`、`reliability-design.md` 2.2）

| 点 | 呼ぶ所 | 使う操作 | 鍵 |
|---|---|---|---|
| `beforeLock(op, roleId)` | ロールの行の排他の直前 | 排他を取る操作すべて | ロールの ID |
| `afterLock(op, roleId)` | ロールの行の排他を取った直後（グループの行の前） | 同上 | ロールの ID |
| `afterCheck(op, key)` | 業務の判定の後、書き込みの前 | 作成・名前の変更・作業ロールの切り替え・割り当て | 作成と名前の変更は名前の鍵、割り当ては `<ロールの ID>:U<利用者 ID>`・`<ロールの ID>:G<グループの ID>`、切り替えは利用者 ID の文字列 |
| `afterWrite(op, key)` | 書き込みと flush の後、確定の前 | 作成・名前の変更・作業ロールの切り替え・割り当て・保存・import の適用 | 同上（保存はロールの ID、適用は指紋の先頭 8 文字） |

本番は何もしない `NoOpRoleBarrier`（`@Component`）。テストは `role/testsupport/TestRoleBarrier`（`@Primary`、group の `TestGroupBarrier` と同じ `hold`・`signal`、待ちの上限 20 秒）で差し替える。引数は操作の区分と ID・鍵だけで、TRACE に個人に関する値は出ない。

### 4.5 移行（前進のみ、表を足すだけ、`users.admin_flag` を残す）

| Bolt | ファイル（番号は Step 3・26 で次の空き番号を確かめる） | 中身 |
|---|---|---|
| B4 | `backend/src/main/resources/db/migration/V11__u4_role.sql` | `roles`（`role_id` BIGINT 自動、`name` VARCHAR(128) NOT NULL、`name_key` VARCHAR(256) NOT NULL、`created_at`・`updated_at` TIMESTAMP WITH TIME ZONE NOT NULL、`uk_roles_name_key`）、`permission_settings`（`role_id`、`schema_name` VARCHAR(256) NOT NULL、`table_name` VARCHAR(256) NOT NULL DEFAULT ''、`column_name` VARCHAR(256) NOT NULL DEFAULT ''、`main_permission` VARCHAR(8) NULL、`create_permission`・`delete_permission` BOOLEAN NULL、`updated_at`、主キー `pk_permission_settings(role_id, schema_name, table_name, column_name)`、`fk_permission_settings_role`（削除の制限）、検査の制約 `ck_permission_settings_any`（3つの値の少なくとも1つが NULL でない）・`ck_permission_settings_level`（`table_name` が空なら `column_name` も空）・`ck_permission_settings_column_aux`（`column_name` が空でない行は補助権限が NULL）・`ck_permission_settings_main`（`main_permission` は NULL・NONE・READ・FULL）） |
| B5 | `V12__u4_role_assignment.sql` | `user_role_assignments`（`role_id`・`user_id`・`assigned_at`、`pk_user_role_assignments(role_id, user_id)`、`fk_user_role_assignments_role`・`fk_user_role_assignments_user`、`ix_user_role_assignments_user_id`）、`group_role_assignments`（`role_id`・`group_id`・`assigned_at`、`pk_group_role_assignments(role_id, group_id)`、`fk_group_role_assignments_role`・`fk_group_role_assignments_group`、`ix_group_role_assignments_group_id`）、`work_role_selections`（`user_id` 主キー `pk_work_role_selections`、`role_id` BIGINT NOT NULL（外部キーなし、Q3: A）、`updated_at`、`fk_work_role_selections_user`） |
| B6 | なし | — |

- 監査の表に列は足さない（U3 の3列を使う）。説明のコメントを日本語で書く。
- 前の版のアプリとの互換は 13節 Q6: A のとおり `RoleMigrationCompatibilityIT`（B4 と B5 で1つずつ足す）で確かめる。前の版のイメージでの起動は配備の段の戻しの練習（`infrastructure-specification.md` 4節）。

### 4.6 監査の写し方（`audit`）

- `AuditEventType` に足す値: B4 `ROLE_CREATED`・`ROLE_RENAMED`・`ROLE_DELETED`・`ROLE_PERMISSION_CHANGED`、B5 `ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`WORK_ROLE_SWITCHED`、B6 `ROLE_TRANSFER_APPLIED`。
- `AuditFailureReason` に足す値: B4 `ROLE_NOT_FOUND`・`ROLE_NAME_DUPLICATE`・`ROLE_IN_USE`・`PERMISSION_TARGET_NOT_IN_DSL`・`DSL_NOT_APPLIED`、B5 `ROLE_NOT_ASSIGNED`、B6 `ROLE_TRANSFER_STALE`（`USER_NOT_FOUND`・`GROUP_NOT_FOUND`・`NO_CHANGE` は既存の値）。どれも 32 文字以内（既存の `AuditEventTest` が全値を回す）。
- `AuditEventFactory.from(RoleAuditEvent)`（`AuditEvent.withRoleGroupTarget` で `targetRoleId`・`targetUserId`・`targetGroupId`・detail を写す。BR11.4）、`roleEventTypeOf`・`roleFailureReasonOf`（網羅の `switch`）。既存の網羅の `switch`（`resultOf`・`isDslOperation` など）に足した種類の追従が要る（group の G-2 と同じ）。
- `AuditDetailJson.of(RoleAuditDetail)`（決めたキーだけの JSON。全件の JSON を作ってから長さ（`String.length()`）を見て、16,384 を超えれば要約の型に切り替え、途中で切らない。D-11）。
- `AuditEventListener.onRoleAuditEvent`（確定の後、`fallbackExecution = true`、`onGroupAuditEvent` と同じ形）と、組み立てに失敗したときの ERROR の項目（`fields(RoleAuditEvent)`。`targetRoleId` が値を持つ枝は B3 の引き継ぎどおりテストで通す）。

## 5. カバレッジの一覧と境界テスト

| パッケージ | 本体に手が入るか | `packagesJudgedByTotal` にあるか | 扱い |
|---|---|---|---|
| `role.domain`・`role.repository`・`role.store`・`role.service`・`role.web`（B4 で作る）、`role.transfer`（B6 で作る） | 作る | 無い | 自動でパッケージごとの下限（行 80%・分岐 70%）。各 Bolt の終わりで、その時点のすべての `role.*` が下限を満たす |
| `audit.domain`・`audit.service` | B4・B5・B6 で手を入れる | 無い | パッケージごとの下限のまま。各 Bolt の基準（Step 3・26・48）と終わり（Step 21・43・65）の値を並べる。B3 の後の値は行 99.7%・分岐 96.8%（domain）、行 99.5%・分岐 84.6%（service）（group の `code-summary.md` 3.1） |
| `group.store` | B5 で `StoreFailureClassifier` の1か所（13節 Q4: A） | 無い | パッケージごとの下限のまま（B3 の後 行 97.8%・分岐 94.6%）。基準と終わりを並べる |
| `group.*` のテスト | B5 で `GroupAdminAuthorizationApiIT` などのテストだけを書き換える（Q3） | — | テストだけの変更は「手を入れる」に当たらない |
| `user.*`・`dsl.*` | 手を入れない（既存の口だけを使う） | 無い | 作業は付かない |
| `access.*`・`auth.*`・`common.*`・`audit.repository` | 手を入れない（13節 Q5: A のとおり `common.error.web` も変えない） | `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web` はある | 一覧の作業（下限を満たして外す）は付かない。Step 19・41・63 で本体の差が無いことを確かめる |

境界テスト:

| テスト | 扱い | Bolt |
|---|---|---|
| `role/RoleBoundaryArchitectureTest`（B3 の最小の1規則） | role の自分の境界テスト。承認済みの NFR6.5 のとおりに広げる（B3 の計画 11節 Q1: A の「B4 以降は role の Bolt が規則を足す」）。B4: 依存してよい先（`group.service`・`group.domain`・`user.service`・`user.domain`・`dsl.service`・`dsl.domain`・`common`）、`dsl.parse`・`dsl.validate`・`group.store`・`group.repository`・`user.repository` に依存しない、role に依存してよいのは `audit`（`role.domain` だけ）と `navigation`（`role.service` だけ）、`role.store` を使うのは `role.service` だけ、`role.service` が `EntityManager`・`JdbcTemplate` を使わない、`role.repository` に書き込みの方法・`@Modifying` が無く `CrudRepository` を継がない、トランザクションの境界は `role.service` の `TransactionTemplate` だけ、`role.store` を使う `role.service` のクラスは `RoleStoreTransactions` と読み取りの部品だけ。B5: `group.service` の口だけ（`GroupMembershipQuery`・`GroupDeletionGuard`・その型）を使う。B6: `role.transfer` が DB の部品（`EntityManager`・`JdbcTemplate`・`role.repository`・`role.store`）と `dsl.parse` に依存しない。各規則に「規則が依存を見分けている」確かめを添える（既存の境界テストと同じ形） | B4・B5・B6 |
| `user/UserBoundaryArchitectureTest` | 「user に依存してよい機能」の一覧に `role` だけを足す（**既存の境界テストを緩める変更**、13節 Q1）。ほかの規則（user が依存してよい先、common が user に依存しない）は緩めない | B4 |
| `group/GroupBoundaryArchitectureTest` | 変えない（「group に依存してよいのは role・audit だけ」に role は入っている） | — |
| `ArchitectureTest`・`ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`ApiAccessConsistencyIT`・`PublicApiInventoryTest`・ほかの境界テスト | 変えない。各 Bolt の Step 14・37・59 で流す | — |

## 6. 当たりそうな所の洗い出し（前の3つの Bolt の経験 (a)〜(e) を受けて、計画の段で読んだもの）

| # | 当たりそうな所 | 読んで分かったこと | 手当て | 手順 |
|---|---|---|---|---|
| A1 | `UserBoundaryArchitectureTest.onlyKnownFeaturesDependOnUser` | 「user に依存してよい機能」は `audit`・`auth`・`dslmanage`・`group`・`invitation`・`useradmin`。role は B4 の時点で `user.domain.RequestOrigin`（出来事と要求の文脈）、B5 で `user.service`（利用者の有無・伏せ字の要約）を使うため、B4 の C1 で落ちる | 13節 Q1。一覧に `role` だけを足す | Step 6・14 |
| A2 | `RoleBoundaryArchitectureTest.dependsOnlyOnGroupServiceAndCommon`（B3） | role がアプリの中で依存してよいのを `group.service` と `common` に限る。B4 の C1 で `role.domain` → `user.domain`、`role.service` → `dsl.service` になった時点で落ちる | 5節のとおり NFR6.5 の形に広げる（同じコミット C1 で直す） | Step 6・14 |
| A3 | テストの名前の分け方（`*Test`・`*IT`）と計測の除外 `**/*Properties.class` | `Properties` で終わるテストは動かない。本体の `…Properties` は計測から外れる | jqwik のテストは `*PropertyTest`（D-12）。本体で `Properties` で終わるクラスは、B6 の設定値だけの record（`RoleTransferLimitProperties`、D-21）だけにする（除外の決まりの内。除外を広げない）。Step 19・41・63 で検索で確かめる | 各 Bolt |
| A4 | JPQL・H2 の予約語 | group は `Group` が `GROUP BY` と重なりエンティティ名を `UserGroup` にした。`Role`・`roles`・`permission_settings` は予約語の一覧に当たらない見込み | Step 5 の `RoleSchemaIT` と Step 9 の store のテストで `SELECT … FROM roles` と JPQL の `Role` が通ることを確かめる。動かなければ止めて依頼者に諮る（表名は承認済みの設計の値） | Step 5・9 |
| A5 | `ArchitectureTest`（依存はコンストラクターだけ・トランザクションの境界は service だけ・web は repository を使わない・コントローラーはエンティティを返さない） | `@PersistenceContext` の項目の注入は違反。`TransactionTemplate` は注釈ではないため当たらない | `RoleStore` は `EntityManager`・`JdbcTemplate` をコンストラクターで受ける。応答は record の DTO だけ | Step 8・12・53 |
| A6 | `TraceAspect`（`..web..`・`..service..`・`..domain..`・`..repository..` の引数・戻り値・例外の文を TRACE に出す） | `RoleStoreTransactions`・`RoleTransferService`・`EffectivePermissionResolverImpl` は対象。`role.store`・`role.transfer` は外 | 結果の型と受け渡しの型の toString を種類と件数だけにする（R-13、NFR1.9）。利用者の値は伏せ字の型のまま（BR12.1）。`RoleSecretLeakIT` で TRACE を有効にして確かめる | Step 8・10・13・36・51・58 |
| A7 | `role.repository` に書き込みの方法を置かない決まり | `JpaRepository`・`CrudRepository` を継ぐと `save`・`delete` を持つ | `org.springframework.data.repository.Repository` を継いで読み取りの `@Query` だけ（group の D-4 と同じ）。`RoleBoundaryArchitectureTest` で確かめる | Step 8・14 |
| A8 | `AuditEventListenerTest` の `containsOnlyKeys` | 書き込みの失敗の ERROR の項目を一律に増やすと落ちる | role の出来事の項目は `roleGroupFields` の「値があるときだけ載せる」形に乗る（group の D-10 と同じ） | Step 6・7 |
| A9 | `AuditSecretLeakIT`・`AuditSchemaIT` | role は監査の表に列を足さないため、列の一覧は変わらない | 書き換えない。role の行の秘密の確かめは `RoleSecretLeakIT` に置く | — |
| A10 | `group/web/GroupAdminAuthorizationApiIT`（28 行）の 403 の主体 | 管理者の印を持たない利用者（ロールなし）で 403 を確かめている（group の D-16） | 13節 Q3。B5 で主体を「全スキーマを FULL のロールを作業ロールに持ち、管理者の印だけを欠く利用者」にする | Step 36 |
| A11 | `role/service/RoleGroupDeletionGuardTest`（B3、数 0・`Allowed` を確かめる） | 本物に置き換えると必ず落ちる | B5 で本物の振る舞いの単体テストに書き換え、`RoleGroupDeletionGuardIT`・`GroupDeletionWithRoleIT` を足す。group のテストの `TestGroupDeletionGuard`（`@Primary`）はそのまま使える | Step 33・34 |
| A12 | group の `StoreFailureClassifier` は外部キーの違反を `fk_group_members_group` のときだけ `Referenced` にする | B5 で `fk_group_role_assignments_group` ができると、割り当てが残るグループの削除が問う口をすり抜けた場合の外部キーの違反は `UNEXPECTED`（500）になる（API の経路ではグループの行の排他で届かない最後の守り） | 13節 Q4 | Step 33・34 |
| A13 | 問い合わせの数の道具 | 設計は「Hibernate の統計」と書くが、既存の `UserAdminListQueryCountIT`・`GroupAdminListQueryCountIT` は `auth/testsupport/SqlStatementCounter`（StatementInspector）を使う。B6 の `JdbcTemplate` の文は StatementInspector に見えない | 既存の道具に合わせる（D-8）。数えるのは JPA の経路だけ（B4・B5）。B6 の適用の文の数は数えない（`performance-design.md` 1節の表の「適用の書き込みは 1,000 件ずつのバッチ」はバッチの件数の単体テストで確かめる） | Step 11・13・34・36 |
| A14 | 同時に持つ接続の数を数える道具 | `group/testsupport/ConnectionHoldRecorder`（スレッドごとの同時の本数の最大）がある | 使い回す（D-9）。role のテストから group のテストの手伝いを読む（テストのソースは境界テストの対象外。group が `auth/testsupport` を使ったのと同じ） | Step 13・36・58 |
| A15 | colima のホームの共有が外れる（経験 (e)） | 外れると、ホームの下のファイルをコンテナに渡す処理が空振りする。`verify` の Testcontainers は手元のファイルを渡さないが、E2E の Mailpit と後の段の負荷の試験は compose を使う | 各 Bolt の Step 2・21・22（B5 は 25・43・44、B6 は 47・65・66）の前に `colima status` と `colima ssh -- ls <リポジトリの絶対パス>/docker` で共有を確かめ、外れていれば止めて依頼者に諮る（colima の再起動は配備したアプリも止めるため。dsl-v2 の D-26） | 各 Bolt |
| A16 | PC のスリープ（経験 (c)） | 長いコマンドが止まる | `verify`・基準の実測・E2E・`k6 inspect`・op のタグの確かめは `caffeinate -i` で包む | 各 Bolt |
| A17 | 監査ログの追記（経験 (d)） | 記録のコミットの直後にも追記される | 3節のとおり、squash の前に `aidlc/` をコミットし、`git restore` を使わない | Step 23・45・67 |
| A18 | 負荷の台本の書式と確かめ（経験 (a)） | `perf/k6/scenarios.js` は ESLint・Prettier の対象の外（`verify` の検査は `frontend/` だけ）。ホストに k6 が無いため、group は `grafana/k6:2.3.0` のコンテナで `k6 inspect` を流した（group の G-23） | 既存の書き方（セミコロンなし・シングルクォート・インデント2）に手で合わせ、同じコンテナで `k6 inspect --include-system-env-vars` を場面ごとに流す | Step 17・39・61 |
| A19 | E2E の生成物 | `frontend/playwright-report`・`frontend/test-results` が残ると承認の場が止まる（`project.md` の学び）。計画の時点では無い | 各 Bolt の Step 1 で無いことを確かめ、E2E の後に消す | 各 Bolt |
| A20 | 負荷の試験の準備の DSL に照合の対象DB が要るか（基盤の読み直しの R-07） | `DslLifecycle.apply` は「対象DB には接続しない」。プレビューの照合（`DslPreviewAnalysis.analyze`）は対象DB に届かなくても `DslReconciler` の警告になるだけで、投入と適用は止まらない | 準備の台本は対象DB を要らない。使い捨ての環境の PostgreSQL の profile に表を作らない | Step 61 |
| A21 | `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` | 本番の口はすべて `ApiAccess` の印をちょうど1つ持ち、ADMIN の印と `AdminPaths`（`/api/admin`・`/api/admin/**`）が両方向で合い、AUTHENTICATED は `/api/` の下で管理者の道の外にある。静的な検査と実行時の口の一覧が一致する | 管理の口はクラスに `@ApiAccess(ADMIN)`、`/api/me/…` の口はクラスに `@ApiAccess(AUTHENTICATED)`。`/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles` も ADMIN | Step 12・35・57 |
| A22 | 既存の道との重なり | `UserAdminController` は `/api/admin/users`・`/{userId}/profile` など、`GroupAdminController` は `/api/admin/groups/{groupId}`・`/{groupId}/members`、`MeController` は `/api/me/preferences`・`/password`。role の道とは重ならない | Step 13・36 の `ApiAccessConsistencyIT` と結合テストで、既存の口の応答が変わらないことを確かめる | Step 14・37 |
| A23 | `RequestBodyLimitRoute` | 道は文字どおり一致（`RequestSizeLimitFilter.relativePath`）、状態コードは 413 だけ。`DslWebConfig` が Bean で登録する形 | `RoleWebConfig` で確かめ・適用の2つの道を登録する | Step 57 |
| A24 | `SafeYamlReader.read(byte[], SafeYamlLimits)` | 本文はバイト列で渡す（`maxBytes` は int） | 入口を取った後に、本文の流れを上限まで読んでバイト列にして渡す（`reliability-design.md` 2.6） | Step 55 |
| A25 | `GlobalExceptionHandler` の想定外の 5xx の ERROR | `setCause(ex)` つきで、包み直した例外の文とスタックトレースが出る | 包み直した例外の文に値（名前・ID）を入れない。13節 Q5: A のとおり、文に入れるのは SQLState と role の既知の制約の名前の一覧に当たった名前だけ | Step 8・9 |
| A26 | `GroupMembershipQueryImpl` の口は自分の `TransactionTemplate`（読み取りだけ）を持つ | role のトランザクションの中で呼ぶと既存のトランザクションに加わる（REQUIRED）。`lockForAssignment` は呼び出し元のトランザクションが無いと `IllegalStateException`。上限切れは `GroupStore` の中で WARN（`GROUP_ROW`）を出し、Hibernate がトランザクションに巻き戻しの印を付ける | `lockForAssignment` は `RoleStoreTransactions.inFirst` の中で呼び、`Busy` は `RoleFirstStep.Store` にして巻き戻す（印は `RoleStoreTransactions` が付ける） | Step 33・34 |
| A27 | `AuditEventFactory`・`AuditEventListener` の網羅の `switch` | 種類を足すとコンパイルで追従が要る（group の G-2） | 4.6 のとおり Step 6・29・49 で追従する | Step 6・29・49 |
| A28 | テストの JVM のヒープ（1g） | 10 MiB の YAML のテスト（上限ちょうど・Tomcat の確かめで2回）で足りるかは未確認（`cicd-pipeline.md` 7節） | 足りなければ計画に無い変更として止めて依頼者に確かめる（ヒープを黙って上げない） | Step 58・60 |
| A29 | `verify` の時間 | 上限切れのテストは行 3 秒・鍵約 2 秒を実際に待つ。10 MiB のテストも延びる | 上限切れのテストは `reliability-design.md` 4.2 の #3・#5・#6・#9・#12 と `RoleBusyApiIT`・`RoleBusyLogTraceIT` の上限切れの行に限る。時間は Step 21・43・65 で実測して記録する（目標は置かない） | Step 21・43・65 |
| A30 | 作業ロールの保存の `MERGE INTO … KEY(user_id)`（NFR 設計の読み直しの R-15） | 未確定の同じ主キーに対する振る舞いを試していない | Step 28 で確かめてから store の形を決める（D-19） | Step 28・31 |

## 7. 部 B4 — ロールと設定と木の API

### 7.1 範囲と終わりの条件（`bolt-plan.md` の B4、`functional-spec.md` 9節）

- 流れ: 2.1〜2.7（2.4a を含む）と 2.16 のロールと設定の種類。決まり: BR1・BR2・BR3（BR3.2 は割り当ての表が無いため数は 0 の前提、BR3.3 の作業ロールの保存の削除は B5）・BR4・BR5.1〜BR5.3・BR5.6・BR8.1・BR8.3〜BR8.6・BR11（`ROLE_CREATED`・`ROLE_RENAMED`・`ROLE_DELETED`・`ROLE_PERMISSION_CHANGED`）・BR12・BR13。
- 終わりの条件:
  - ロールの作成・名前の変更・削除・一覧・1件の読み取りの管理の API（401/403/200、拒否の code、監査）。削除は割り当てが無い前提（B5 で割り当てが残るときの拒否を足す）。
  - 主権限と補助権限の保存（差分）と消す（Should、Q9）、木の3つの API（明示・実効・継承の元・`inMenu`・`inCurrentDsl`・`hasChildren`、名前は問い合わせの引数）。継承の解決の関数と性質ベースのテスト（AC1.2.9）。
  - 移行 V11（表を足すだけ）と前の版との互換の確かめ（Q6）。
  - `RoleBoundaryArchitectureTest` を NFR6.5 の形に広げる。`UserBoundaryArchitectureTest` は Q1 の1か所だけ。
  - k6 の `roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps` の台本（流すのは Performance Validation）と、op のタグの確かめの結果。
  - `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と E2E の全体が通る。
- 確かめること: 設定を名前で持ち、その都度 DSL と照らす形で、木の1階層の読み込みが速い（目標の判定は Performance Validation）。
- 見せるもの: API でロールを作り、テーブルの主権限を設定して、実効の値と継承の元が返る（`code-summary.md` の `curl` の手順）。

### 7.2 作るもの・手を入れるもの

| 場所 | ファイル（案） | 中身 | 出典 |
|---|---|---|---|
| `role.domain` | `Role.java`・`PermissionSetting.java`・`PermissionSettingId.java` | JPA のエンティティ（表 `roles`・`permission_settings`、JPQL のエンティティ名 `Role`・`PermissionSetting`）。書き換えの方法は store だけが呼ぶ | `entities.md` |
| `role.domain` | `RoleName.java`・`RoleNameValidation.java`（`INVALID_BLANK`・`INVALID_TOO_LONG`・`INVALID_CONTROL_CHARACTER`） | 前後の空白（全角を含む Unicode の空白）の取り除き、コードポイントで 1〜64、制御文字の拒否（group の G-4 と同じ範囲: Cc と U+2028・U+2029、Cf は拒否しない）、言語に依らない小文字化の鍵（鍵を作る唯一の場所） | BR1.1〜BR1.4 |
| `role.domain` | `PermissionTarget.java`・`PermissionTargetName.java`・`MainPermission.java`・`PermissionValues.java`・`InheritedFrom.java`・`EffectivePermission.java` | 対象の名前の組（下位の名前の無い階層は空の文字列で持つ）、名前の検証（1〜128 コードポイント、制御文字なし、大文字と小文字を区別、前後の空白を取り除かない）、値（主権限 null・NONE・READ・FULL、補助権限 null・真・偽） | BR4.1〜BR4.3、BR5 |
| `role.domain` | `PermissionInheritance.java` | 純粋な関数: 主権限は column ?? table ?? schema ?? NONE、補助権限は table ?? schema ?? false を CREATE と DELETE で別に、継承の元（EXPLICIT・TABLE・SCHEMA・DEFAULT）を主権限と補助権限で別に求める | BR5.1・BR5.2・BR5.6、BR4.11 |
| `role.domain` | `PermissionTreeBuilder.java`・`PermissionNode.java` | 純粋な関数: DSL の節と設定の行を名前で合わせ、各節に明示・実効・継承の元・`inMenu`（テーブルの節だけ、実効の主権限が NONE でないとき真）・`inCurrentDsl`・`hasChildren` を付ける。並びは DSL の順、今の DSL に無いものはその後に名前のコードポイントの順。今の DSL に無い節の実効は NONE・不可 | BR4.10・BR4.11・BR5.3 |
| `role.domain` | `RoleOperation.java`・`RoleAuditEvent.java`・`RoleAuditFailure.java`・`RoleAuditDetail.java`（sealed: `Name`・`Rename`・`InUse`・`PermissionChanges`・`PermissionChangesSummary`。B5・B6 で形を足す） | 監査の出来事と detail の型（メールアドレス・氏名・秘密を型に持たない）。出来事のファクトリーは `user.domain.RequestOrigin` を受ける（group と同じ形）。`PermissionChanges.summarize(int)` は先頭の n 件と変わった数の要約を返す純粋な方法 | BR11、`entities.md` |
| `role.domain` | `RoleProblemTypes.java`・`RoleRejection.java` | 4.2 の B4 の7つの code（日本語と英語の文言。説明文に名前・ID を載せない）、拒否の理由と code・監査の失敗の理由の対応 | BR13.1 |
| `role.repository` | `RoleRepository.java`・`PermissionSettingRepository.java`・射影の record（`RoleRowView`・`PermissionSettingRow` など） | 件数・ページ（ID の順、20 件）・1件・名前の鍵の読み取り、設定の行の範囲の読み取り（スキーマの階層・テーブルの階層・カラムの階層をそれぞれ1回）、保存の前の今の値の読み取り（そのロールの scope の範囲）。書き込みの方法と `@Modifying` を置かない | BR3.5・BR4.10、NFR2.6 |
| `role.store` | `RoleStore.java`・`RoleStoreOutcome.java`・`Referent.java`・`RoleStoreClassifier.java`・`RoleStoreUnexpectedException.java`・`package-info.java` | 行の排他（`PESSIMISTIC_WRITE`・ヒント 3,000 ms）、作成・名前の変更・削除（設定の行 → ロールの行の順）・設定の差分の書き込み（足す・変える・すべて設定なしになった行を消す）・消すの書き込みと flush。例外を中で受けて `RoleStoreOutcome`（`Done`・`RoleMissing`・`NameTaken`・`AlreadyAssigned`・`Referenced(Referent)`・`Busy(lockKind)`）に変える。区分の順は `RowLockFailures.isLockFailure` → SQLState 23505・23503・23506 と制約の名前・表の名前（H2 の主キーの索引の名前は `PRIMARY_KEY_xx` のため表の名前でも見分ける。group の G-7・G-8）→ 想定外（13節 Q5 の形に包み直す）。上限切れは `RowLockFailures.warn`（`ROLE_ROW`・`ROLE_NAME_KEY`）。`RoleStoreOutcome` の toString は種類だけ（D-5） | `reliability-design.md` 2.3、`security-design.md` 4.1 |
| `role.service` | `RoleStoreTransactions.java`・`RoleFirstStep.java` | 1つ目のトランザクションの入口（`Done` 以外で必ず `status.setRollbackOnly()`）。toString は種類だけ | L15、4.3 |
| `role.service` | `RoleAdminService.java`・結果の型（`RoleCreateResult`・`RoleChangeResult`・`RoleListResult`・`RoleDetailResult`・`PermissionTreeResult`・`PermissionSaveCommand` など、sealed の record） | 作成・名前の変更・削除・一覧・1件・木の3つ・保存・消す。変える操作は 入力の判定 → 排他 → 待ち合わせの口 → 拒否の判定 → 書き込み → 成功の出来事（1つ目）、拒否・違反は 4.3 の形。DSL は `ActiveDslModelProvider.current()` で読む（保存では排他の後に読む）。一覧・1件・木は読み取りだけのトランザクション1つ | 2.1〜2.7、BR8.1 |
| `role.service` | `RoleBarrier.java`・`NoOpRoleBarrier.java` | 待ち合わせの口（4.4） | BR8.6、`reliability-design.md` 2.2 |
| `role.service` | `RoleProblemTypeCatalog.java` | 起動時の code の収集 | BR13.1 |
| `role.web` | `RoleAdminController.java`（`/api/admin/roles`: 一覧・作成・1件・名前の変更・削除）・`RolePermissionController.java`（`/api/admin/roles/{roleId}/permissions`: `schemas`・`tables?schema=`・`columns?schema=&table=`・PUT・`clear`） | 10 の口、どちらもクラスに `@ApiAccess(ADMIN)`。結果の型を `switch` で尽くして `BusinessException` に変える。`ROLE_IN_USE` の応答に `assignedUsers`・`assignedGroups`（B4 では起きない） | C7、BR2・BR4.12 |
| `role.web` | `RoleNameRequest.java`・`PermissionSaveRequest.java`・`PermissionEntryRequest.java`・`PermissionClearRequest.java`・`RoleResponse.java`・`RolePageResponse.java`・`RoleRowResponse.java`・`PermissionNodesResponse.java`・`PermissionNodeResponse.java`・`RoleFieldErrors.java`・`RoleRequestContextResolver.java` | 決めた項目だけの record（足した項目は Jackson の既定で無視）。入力の誤りの `fieldErrors`（`GroupFieldErrors` と同じ形）。主体と要求の元は要求の文脈から読む（`GroupRequestContextResolver` と同じ形） | BR2.3、NFR1.4 |
| リソース | `db/migration/V11__u4_role.sql` | 4.5 の B4 の行 | NFR3.6 |
| リソース | `application.yaml` | `org.hibernate.orm.jdbc.error: OFF` のコメントに role の理由（ロールの名前と ID・対象の名前が違反の文に入る）を足す。設定の値は変えない | `security-design.md` 4.1（group の読み直しの R-09 の手当て） |
| `audit` | `AuditEventType`・`AuditFailureReason`・`AuditEventFactory`・`AuditDetailJson`・`AuditEventListener` | 4.6 の B4 の分 | C10、BR11 |
| 境界テスト | `role/RoleBoundaryArchitectureTest`・`user/UserBoundaryArchitectureTest` | 5節の B4 の分（Q1） | NFR6.5 |

### 7.3 B4 で足すテスト（`backend/src/test/java/cherry/mastersmith/`）

| 層 | テスト |
|---|---|
| データの形 | `role/RoleSchemaIT`（表・制約の名前・検査の制約・列の長さ 128／256、`SELECT … FROM roles`、`users.admin_flag` が残る）、`role/RoleMigrationCompatibilityIT`（13節 Q6: A。V1〜V10 だけを写した場所を読ませた前の版の Flyway（`validateOnMigrate(true)`、ほかは既定）が V11 まで当てた内部DB で止まらず実行 0 件、前の版の列だけで監査の行を足せる、既存の行が読める。group の `AuditMigrationCompatibilityIT` と同じく Spring を起動しない） |
| ドメイン | `role/domain/RoleNameTest`・`RoleNameKeyPropertyTest`（jqwik: 大文字と小文字だけの違いは同じ鍵、全角と半角は別の鍵、受け付けた名前は 1〜64・前後に空白なし、名前 128・鍵 256 単位に収まる）・`PermissionTargetNameTest`・`PermissionInheritanceTest`・`PermissionInheritancePropertyTest`（jqwik: BR5.1・BR5.2 の式に等しい、下位の明示が上位を上書きする、CREATE と DELETE は互いに影響しない、カラムの補助権限はテーブルの値）・`PermissionTreeBuilderTest`（並び・今の DSL に無い節・`inMenu`・`hasChildren`・継承の元）・`RoleAuditEventTest`・`RoleAuditDetailTest`・`RoleProblemTypesTest`、`audit/domain/AuditRoleEventFactoryTest`（種類・理由・対象・detail のキー）・`AuditRoleDetailJsonTest`（16,384 ちょうどは全件、超えると要約、要約も上限に収まる、サロゲートペアを含む名前、JSON として読める）、`audit/service/AuditRoleEventListenerTest`（確定の後、書き込みの失敗の ERROR に `targetRoleId` が載る枝。group の引き継ぎ） |
| DB アクセス | `role/store/RoleStoreClassificationTest`（上限切れ・23505 の制約の名前ごと・23503・23506・想定外・待った後の違反 #14・包み直した例外が元の文と値を持たない・toString が種類だけ）、`role/store/RoleStoreConstraintIT`（名前の鍵の待たない違反と上限切れを `UncommittedWrite` で作る、設定の検査の制約、上限切れの WARN の `lockKind`）、`role/repository/RoleRepositoryIT`（ID の順・20 件・範囲の読み取りの行・今の DSL に無いスキーマの名前） |
| 業務処理 | `role/service/RoleAdminServiceTest`（store・repository・DSL をモック。判定の順 BR3.6、4.3 の一覧どおりの巻き戻しと出来事、`Busy` で出来事なし、保存の差分と `ROLE_NO_CHANGE`、DSL が無い・今の DSL に無い対象、消す）、`RoleStoreTransactionsIT`（`TransactionTemplate` の中で違反・上限切れを起こし、`UnexpectedRollbackException` が出ないことと、2つ目の失敗の出来事が確定すること）、`RoleProblemTypeCatalogTest`、`RoleAdminQueryCountIT`（一覧はロール 1 件と 20 件で同じ数、木の1階層は設定の行の読み取り1回） |
| API | `role/web/RoleAdminApiIT`（作成・名前の変更・削除・一覧・1件の成功と拒否の code・Problem Details）・`RolePermissionApiIT`（木の3つ、名前に `/`・`..`・`;`・`%`・空白を含む名前で 400 にならない、引数の欠けは 400、DSL が無い 409、今の DSL に無い対象の保存の拒否と消す、差分の保存）・`RoleAdminAuthorizationApiIT`（B4 の 10 の口 × 401・403（管理者の印を持たない利用者）・成功・停止中の管理者（401。group の G-14 と同じ読み）を表のパラメーターで）・`RoleMassAssignmentApiIT`（作成・名前の変更・保存の本文に ID・作成者・時刻・組み込みの印を足しても反映されない）・`RoleIdorApiIT`（存在しない・0・-1 の roleId、読み直して状態が変わらない）・`RoleConcurrencyIT`（#7 同じロールの同時の保存）・`RoleConflictAuditIT`（#2 同じ名前の待たない違反、名前の変更でも）・`RoleBusyApiIT`（#3 鍵の上限切れ、ロールの行の上限切れ（保存・名前の変更））・`RoleBusyLogTraceIT`（WARN が1件、`lockKind` があり例外の文が無い）・`RoleAuditIT`（B4 の4種類の成功と主な拒否の行の列、監査に残さないもの）・`RoleAuditWriteFailureIT`（作成）・`RoleMetricsIT`（`http.server.requests` の `uri` の型の系列）・`RoleConnectionUsageIT`（B4 の経路: 書き込み 2・読み取り 1・違反の読み替え 2・業務の拒否 2）・`RoleUniqueViolationSecretLeakIT`（同じ名前の同時の作成で、違反の文に入る名前がログと応答に 0 件。INFO と TRACE）・`RoleSecretLeakIT`（B4 の分: 応答・監査の行に秘密が無い、TRACE の行に `RoleStoreOutcome` の値が出ない） |
| 境界 | `role/RoleBoundaryArchitectureTest`（5節の B4 の規則）、`user/UserBoundaryArchitectureTest`（Q1 の1か所） |
| 手伝い | `role/testsupport/TestRoleBarrier`（`@Primary`、`hold`・`signal`、待ちの上限 20 秒）・`RoleActors`（管理者・管理者の印を持たない利用者・停止中の管理者。本番の作成の口で作り、メールアドレスは `example.com`）・`RoleApi`（要求の組み立て。ID は文字列でも受ける）・`RoleFixtures`（ロールと設定の行を本番の書き込みを通さずに作る）・`RoleDslFixture`（版 2 の DSL を `ActiveDslModelHolder` に置く、外す。名前に記号を含むスキーマ・テーブルの見本を持つ） |

### 7.4 手順（B4）

各手順の「対応」は 10節の ID。チェックボックスは生成のときに付ける。

#### Step 1: 作業の場の用意（ブランチの作成は依頼者の承認を得てから）
- [x] 計画の承認と、計画までの記録のコミット（依頼者の承認を得て指揮役が行う）の後に、`develop` の先頭から `feature/261004-role-menu-b4` を作る。開始の時点の HEAD を `generation-notes.md` に記録する。
- [x] `git status --short` でアプリのソースに未コミットの変更が無いことを確かめる（監査ログの追記はワークフローの記録として外して判断する）。`frontend/playwright-report`・`frontend/test-results` が無いことを確かめる。
- 対応: Testing Contract の「Project structure and production configuration skeleton」（新しいパッケージは Step 4 以降で作る）。

#### Step 2: テストの実行の準備（最初のテストより前）
- [x] `unit-test-instructions.md` 2節のコマンド（既存のテストを名指し）が動くことを確かめ、結果（件数・失敗 0）を記録する。
- [x] colima の状態とホームの共有を確かめる（6節 A15）。
- [x] 洗い出しの検索を流し直し、7.2・7.3 の一覧と増減が無いかを記録する: `git grep -n -e 'onlyKnownFeaturesDependOnUser' -e 'dependsOnlyOnGroupServiceAndCommon' -- backend/src/test`、`git grep -n -e 'containsOnlyKeys' -- backend/src/test/java/cherry/mastersmith/audit`、`git grep -n 'TODO(B5)' -- backend`、`git grep -n 'org.hibernate.orm.jdbc.error' -- backend/src/main/resources`、`ls backend/src/main/resources/db/migration/`。
- 対応: Testing Contract の runner の手順。

#### Step 3: 変更の前の基準
- [x] colima の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` を流し、件数（単体・結合、失敗・飛ばし）と `backend/build/reports/jacoco/test/jacocoTestReport.xml` の `audit.domain`・`audit.service`・`role.service`・全体の行と分岐の値を記録する（`project.md` の学び）。
- [x] `backend/build.gradle.kts` の `packagesJudgedByTotal` が7パッケージのままで、B4 で手を入れるパッケージ（5節）が入っていないことを記録する。
- [x] 次の空き移行番号が V11 であることを確かめる（V1〜V10 だけがある）。違えば番号を合わせ、12節 D-1 に記録する。
- 対応: NFR6.4、NFR3.6。

#### Step 4: データの形 — 実装（移行とエンティティ）
- [x] `V11__u4_role.sql` を 4.5 のとおり書く。
- [x] `role.domain` の `Role`・`PermissionSetting`・`PermissionSettingId`（列の対応、書き換えの方法、toString は ID と件数だけ）を作る。
- [x] `application.yaml` の `org.hibernate.orm.jdbc.error: OFF` のコメントに role の理由を足す（値は変えない）。
- 対応: BR4.2、NFR3.6、NFR1.8、`entities.md`。

#### Step 5: データの形 — テスト
- [x] `RoleSchemaIT` と `RoleMigrationCompatibilityIT`（13節 Q6: A）を書く（7.3）。
- [x] `unit-test-instructions.md` 3.1 の Step 5 のコマンドを流し、通るまで直す。既存の Flyway・監査のテスト（`cherry.mastersmith.audit.*` の結合）も流し、既存の移行が壊れないことを確かめる。
- 対応: NFR3.6、6節 A4。

#### Step 6: ドメイン — 実装（role.domain と audit の写し方）
- [x] 7.2 の `role.domain` の値・純粋な関数・出来事・detail・code を作る。
- [x] 4.6 の B4 の分を `audit` に足す（種類4つ・理由5つ・`from(RoleAuditEvent)`・`AuditDetailJson.of(RoleAuditDetail)`・`onRoleAuditEvent`・網羅の `switch` の追従）。
- [x] `RoleBoundaryArchitectureTest` を 5節の B4 の規則に広げ、`UserBoundaryArchitectureTest` の一覧に `role` だけを足す（13節 Q1: A）。
- 対応: BR1.1〜BR1.5、BR4.1・BR4.3、BR5.1〜BR5.3・BR5.6、BR11.4〜BR11.9、BR13、NFR5.1・NFR5.5・NFR6.5、C10。

#### Step 7: ドメイン — テスト
- [x] 7.3 のドメインの層のテストを書く。`RoleNameKeyPropertyTest`・`PermissionInheritancePropertyTest` は jqwik の既定の回数で、失敗のときの乱数の種は既存の `exceptionFormat = FULL` で残る。
- [x] Step 7 のコマンドを流す。単体テストの結果に2つの `*PropertyTest` が出ることを確かめる。
- 対応: AC1.1.9・AC1.1.14・AC1.1.16・AC1.2.2〜AC1.2.4・AC1.2.9、BR1・BR5.1・BR5.2・BR5.6・BR11.5、NFR5.5・NFR6.2、Infrastructure Design の名前の決定（D-12）。

#### Step 8: DB アクセス — 実装（role.repository・role.store）
- [x] 読み取りの repository（Spring Data の `Repository` を継ぎ、`@Query` の射影だけ）と、`RoleStore`・`RoleStoreOutcome`・`Referent`・`RoleStoreClassifier`・`RoleStoreUnexpectedException` を作る。store の方法は「排他（`lockRole`）」と「書き込みと flush（`insertRole`・`renameRole`・`deleteRole`・`writePermissions`・`clearPermissions`）」に分ける。JPQL は定数と名前の付いた引数だけ。
- 対応: BR3.3（設定の削除）・BR4.2・BR4.9・BR8.1・BR8.3〜BR8.5・BR12.3、NFR1.8・NFR1.11・NFR2.6・NFR3.3。

#### Step 9: DB アクセス — テスト
- [x] `RoleStoreClassificationTest`・`RoleStoreConstraintIT`・`RoleRepositoryIT` を書く（7.3）。
- [x] Step 9 のコマンドを流す。
- 対応: AC1.1.11（最後の守り）、BR8.3〜BR8.5、NFR1.8・NFR3.3・NFR3.7（T1・T7・U1 の本物での確かめ。10.3 の表）。

#### Step 10: 業務処理 — 実装（role.service）
- [x] `RoleStoreTransactions`・`RoleFirstStep`・`RoleAdminService`・結果の型・`RoleBarrier`・`NoOpRoleBarrier`・`RoleProblemTypeCatalog` を作る。拒否・違反・上限切れは 4.3 の一覧（13節 Q2: A）どおりに組む。
- 対応: 2.1〜2.7、BR1.4・BR1.5・BR3・BR4.4〜BR4.9・BR8.1・BR8.5・BR8.6・BR11.1〜BR11.3、NFR3.4。

#### Step 11: 業務処理 — テスト
- [x] `RoleAdminServiceTest`・`RoleStoreTransactionsIT`・`RoleProblemTypeCatalogTest`・`RoleAdminQueryCountIT` を書く（7.3）。
- [x] Step 11 のコマンドを流す。
- 対応: AC1.1.2・AC1.1.14・AC1.2.5・AC1.2.6・AC1.2.13・AC1.2.17・AC1.2.18、BR3.6・BR4.4〜BR4.9・BR8.5、NFR2.6・NFR3.4。

#### Step 12: API — 実装（role.web）
- [x] `RoleAdminController`・`RolePermissionController` と DTO、`RoleFieldErrors`、`RoleRequestContextResolver`。2つのクラスに `@ApiAccess(ADMIN)`。木の名前は `@RequestParam` で受け、長さで拒否しない。
- 対応: AC1.1.1・AC1.1.5・AC1.1.10・AC1.1.12・AC1.1.15・AC1.1.17・AC1.2.1・AC1.2.10・AC1.2.11、BR2・BR3.1・BR3.5・BR3.7・BR4.10〜BR4.12・BR13、NFR1.1・NFR1.3・NFR1.4・NFR1.10、C7。

#### Step 13: API — テスト（結合）
- [x] 7.3 の API の層のテストを書く。同時の重なりは `TestRoleBarrier` で作り、合否は 409 と code・状態が変わらないこと・監査の行・WARN で決める（経過の時間で決めない。`reliability-design.md` 4.2 の注のとおり、負けた側の `ROLE_BUSY` も受け入れる行を分けて書く）。
- [x] Step 13 のコマンドを流す。
- 対応: AC1.1.1〜AC1.1.6（削除は割り当てなし）・AC1.1.9〜AC1.1.12・AC1.1.14・AC1.1.16・AC1.1.17・AC1.2.1〜AC1.2.8・AC1.2.10〜AC1.2.13・AC1.2.16・AC1.2.17、NFR1.1・NFR1.2（B4 の差は D-17）・NFR1.4・NFR1.6〜NFR1.8・NFR1.10・NFR2.11・NFR3.1 (b)(c)・NFR3.3〜NFR3.5・NFR5.1〜NFR5.3・NFR5.5。

#### Step 14: 境界と構造の検査
- [x] `RoleBoundaryArchitectureTest`（5節の B4 の規則。各規則に依存を見分けている確かめ）と `UserBoundaryArchitectureTest` を流す。
- [x] `cherry.mastersmith.*BoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`ApiAccessConsistencyIT`・`PublicApiInventoryTest` を流す（B2 の検査で 10 の口の印と管理者の道が合うこと）。
- 対応: NFR1.3・NFR6.5、AC1.1.15。

#### Step 15: バックエンドの区切りの確かめ
- [x] `./gradlew :backend:spotlessApply` の後、colima の環境変数を付けて `caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest` を流し、全体が通ることを確かめる。
- 対応: コミットの区切り B4 の C2 の終わり。

#### Step 16: op のタグの確かめ（台本の前。リポジトリの外の捨ての台本）
- [x] スクラッチの置き場に、要求を送らない最小の台本（2つの op を `exec.vu.metrics.tags.op` に入れて交互に回し、`iteration_duration{scenario:x,op:a}` の閾値を置く）を作り、`caffeinate -i docker run --rm -v <スクラッチ>:/s grafana/k6:2.3.0 run --summary-export /s/summary.json /s/tag-check.js` で流す。要約に op ごとの `iteration_duration` の値が出るかを記録する（`performance-design.md` 2.2、D-23）。リポジトリの作業フォルダでは k6 を流さない。
- [x] 付かなければ、操作ごとに場面を分ける名前（`roleAdminOpsCreate` など、`performance-design.md` 2.2）で Step 17 を書く。
- 対応: NFR2.10。

#### Step 17: 負荷の台本（verify と CI の外。流すのは Performance Validation）
- [x] `perf/k6/scenarios.js` に `roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps` と一覧 `ROLE_SCENARIOS`（`setupTimeout = '10m'`）・閾値（`iteration_duration{scenario:…}`（op ごと）の `p(95)<1000` と `checks{scenario:…}` の `rate==1`、`http_req_duration{name:…}` は並べて記録）を足す。1回の繰り返しに要求1つ、状態を戻す操作は交互、繰り返しの中に `sleep` を置かない、名前は実行ごとの識別と VU の番号で一意（group の G-21）、トークンは `setup()` で試験用の管理者から取る（初期管理者は使わない）、場面は 3 分。データは B6 の準備の台本が入れる前提（`cicd-pipeline.md` 5.3。B6 で準備の台本を書く）。
- [x] `perf/README.md` に「ロールの管理の場面（Intent 261004-role-menu の U4）」の節の B4 の分（場面の表・判定・使い捨ての環境・`caffeinate -i` で台本全体を包むこと）を書く。
- [x] 場面ごとに `SCENARIO=<場面> k6 inspect --include-system-env-vars perf/k6/scenarios.js` を `grafana/k6:2.3.0` のコンテナで流し、場面の名前・executor・閾値の式・`setupTimeout: 10m` を記録する。使い捨ての環境での実行は行わない。
- 対応: NFR2.1・NFR2.2・NFR2.5・NFR2.10。

#### Step 18: 文書
- [x] README に「ロールと権限の API（Intent 261004-role-menu の U4）」の節（B4 の 10 の口と成功・主な失敗の表、名前・設定・木・DSL が無いとき・同時の操作（行 3 秒、一意の鍵 約 2 秒、同じ名前の重なりで `ROLE_NAME_DUPLICATE` と `ROLE_BUSY` に分かれること）・監査・指標）、「スキーマの変更（Flyway）」に V11、「監査ログ（U4）」に足した種類と理由と detail のキーを書く。設計の文書（承認済み）は書き換えない。
- 対応: NFR6.1（引き継ぎ）。

#### Step 19: 取り残しと変えないものの確かめ
- [x] 2.3 の「変えないもの」が `develop` と差が無いこと（`git diff --stat develop -- <一覧>`）。`application.yaml` の差がコメントだけであること。`packagesJudgedByTotal`・計測の除外が変わっていないこと。
- [x] 新しいテストのクラスの名前がすべて `Test` か `IT` で終わること、`Properties` で終わるテストのクラスと本体のクラスが無いこと（`git grep -n -e 'class [A-Za-z]*Properties\b' -- backend/src`）。新しい依存と lockfile の変更が無いこと。
- 対応: NFR6.6、6節 A3。

#### Step 20: 記録（コード生成の段の成果物の B4 の分）
- [x] `code-summary.md` の B4 の節（作ったもの・計画との差・承認の場で確かめること・後に回すこと・見せるものの `curl` の手順）、`traceability.json`（B4 で OK にする行、B5・B6 の分は Deferred）、`source-manifest.json`（作った・変えたアプリのソース）、`generation-notes.md`（手順ごとのコマンドと結果）を書く。
- 対応: Testing Contract の「Documentation and traceability」。

#### Step 21: 1コマンドの検査（統合の前の関門）
- [x] colima の状態と共有を確かめ（6節 A15）、README の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。対象DB の3種類のテストの SKIPPED が 0 件、ログに「コンテナの実行環境」の警告が 0 件であることを確かめる。
- [x] 件数（単体・結合・画面、失敗・飛ばし）、カバレッジ（Step 3 の基準と並べる。新しい `role.*` と `audit.domain`・`audit.service`、全体）、`verify` の時間と Step 3 からの延びを記録する。単体テストの結果に `RoleNameKeyPropertyTest`・`PermissionInheritancePropertyTest` があることを確かめる。
- 対応: NFR6.4、`cicd-pipeline.md` 10節の B4 の行。

#### Step 22: E2E（統合の前に手元で）
- [x] `docker compose --profile mail up -d mailpit` の後に `caffeinate -i ./gradlew e2eTest` を流し、全体（今は 14 ファイル・177 件）が通ることを確かめる（認可と管理の API に手が入るため。030・110・120・130 を含む）。報告の確かめの道具の出力を記録する。
- [x] `frontend/playwright-report`・`frontend/test-results` を消す。
- 対応: `cicd-pipeline.md` 8節。

#### Step 23: コミットの提案・統合の提案（B4）
- [ ] 指揮役が B4 の C1〜C3 の区切り（3節）を依頼者に提案し、承認を得てコミットする。見せるものと `code-summary.md` の B4 の節を示す。
- [ ] `aidlc/` の未コミットの変更（監査ログを含む）をコミットしてから、`develop` への squash の統合を提案する。統合の後にブランチを消す。push は依頼者。push の後の CI を確かめてから B5 に入る。
- 対応: `team.md`・`project.md` の Change Control。

## 8. 部 B5 — 割り当て・作業ロール・解決の口・自分の権限・問う口の本物

### 8.1 範囲と終わりの条件（`bolt-plan.md` の B5、`functional-spec.md` 9節）

- 流れ: 2.3 の割り当ての確かめと作業ロールの保存の削除、2.4 の数、2.8〜2.12。決まり: BR3.2（割り当ての数）・BR3.3（作業ロールの保存の削除）・BR3.4・BR5.4・BR5.5・BR6・BR7・BR8.2・BR10・BR11（`ROLE_ASSIGNED`・`ROLE_UNASSIGNED`・`WORK_ROLE_SWITCHED`）・BR12.1。
- 終わりの条件:
  - 利用者・グループへの割り当てと外し、出どころつきの読み取り（割り当ての一覧・グループのロール・利用者のロール）、割り当てが残るロールの削除の拒否（`ROLE_IN_USE`）と、削除で作業ロールの保存を消すこと。
  - 作業ロールの読み取りと切り替え（`/api/me/work-role`）、有効な作業ロールの決め方（C5）、解決の口（`effectiveWorkRole`・`snapshotFor`・`resolve`、NFR2.3 の2つの読み方）、自分の権限の API（Should、Q9）。
  - 同時の重なり（削除と割り当て、同じ組の割り当て、切り替えと外し、同じ利用者の初めての切り替え、グループの削除とグループへの割り当て）の守り（NFR3.1）。
  - **group の読み直しの R-04 の手当て**: (1) 403 を確かめる利用者を「全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールに持ち、管理者の印だけを欠く利用者」にした表が、role の管理の口・group の7つの口・既存の管理の口すべてについてある（13節 Q3）。(2) 本物の割り当ての表に行を作って、本番の Bean の問う口とグループの削除の API を確かめる `RoleGroupDeletionGuardIT`・`GroupDeletionWithRoleIT` が通る（仮の実装（常に `Allowed`・0）が残っていれば必ず落ちる形）。(3) `git grep -n 'TODO(B5)' -- backend` が 0 件。
  - 移行 V12（表を足すだけ）と前の版との互換の確かめ（Q6）。`MERGE` の確かめの結果の記録（R-15）。
  - k6 の `workRoleSwitch`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`myPermissionsTree`・`rolePoolLimit`（合否の回と記録の回）の台本。
  - `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と E2E の全体が通る。
- 確かめること: 割り当て・作業ロールの変更が次の要求からすぐ効き、要求ごとの解決が重くない。
- 見せるもの: 利用者に2つのロールを割り当て、作業ロールを切り替えると、自分の権限の API の結果が変わる。

### 8.2 作るもの・手を入れるもの

| 場所 | ファイル（案） | 中身 | 出典 |
|---|---|---|---|
| `role.domain` | `UserRoleAssignment.java`(`Id`)・`GroupRoleAssignment.java`(`Id`)・`WorkRoleSelection.java` | JPA のエンティティ（表 `user_role_assignments`・`group_role_assignments`・`work_role_selections`） | `entities.md` |
| `role.domain` | `WorkRoleRef.java`・`RoleSource.java`（`Direct`・`Group(groupId, groupName)`）・`UserRoleView.java`・`PermissionSnapshot.java` | 解決の口の型（契約 C5 から `workRole`（ID と名前）と `dslHash` を持つ形。navigation への引き継ぎどおり）。写しは名前の組をキーにした対応表で持ち、toString は作業ロールの ID と行の数だけ | C5、`tech-stack-decisions.md` の U5 への引き継ぎ |
| `role.domain` | `WorkRoleChooser.java` | 純粋な関数: 利用者のロールの ID の集合と保存から、有効な作業ロールを決める（保存が集合にあればそれ、無ければ最小の ID、空なら無し） | BR7.1・BR7.2 |
| `role.domain` | `RoleOperation`・`RoleAuditFailure`・`RoleAuditDetail`（`Assignment`・`WorkRoleSwitch` を足す）・`RoleProblemTypes`（`ROLE_NOT_ASSIGNED`）・`RoleRejection` | B5 の分を足す | BR11、BR13.1 |
| `role.repository` | `RoleAssignmentRepository.java`・`WorkRoleSelectionRepository.java`、`PermissionSettingRepository` の追加 | 利用者の直接のロール（ロールの名前を結合して読む）・グループの ID の集合でグループの割り当て（同じく名前を結合）・保存の読み取り（以上で有効な作業ロールを決める4回、D-15）、作業ロールの設定の行をまとめて1回（写し）、対象の祖先の行（最大3行）を1回（`resolve`）、ロールの ID の集合で直接の割り当ての数（一覧の `userCount`・`groupCount`）、ロールの利用者とグループ、グループの ID の集合で割り当ての数（問う口） | NFR2.3・NFR2.6、`performance-design.md` 1節 |
| `role.store` | `RoleStore` の追加 | 利用者・グループへの割り当ての書き込みと flush、外し（消した行の有無を返す）、作業ロールの保存（Step 28 の結果で `MERGE INTO … KEY(user_id)` か「読んでから更新か挿入」、D-19）、削除の拡張（設定 → 作業ロールの保存 → ロールの順）。区分に割り当ての主キー（`AlreadyAssigned`、`ROLE_ASSIGNMENT_KEY`）、外部キー（`Referenced(ROLE・USER・GROUP)`）、作業ロールの保存の主キー（`Busy("WORK_ROLE_SELECTION_KEY")`）を足す | BR6.3・BR6.7・BR8.5、`reliability-design.md` 2.3 |
| `role.service` | `RoleAssignmentService.java` | 割り当て（入力 → ロールの行の排他 → 利用者は `UserAccountService.findById`（招待中の人は行が無く `USER_NOT_FOUND`、停止中は受け付ける）、グループは `GroupMembershipQuery.lockForAssignment` → 待ち合わせの口 → すでに割り当て済み → 書き込み）、外し（グループは `lockForAssignment`、`exists` が偽でも続け `ROLE_NO_CHANGE`）、割り当ての一覧（直接 ∪ `memberUserIds` を1回、利用者は `findSummariesByIds` を1回、出どころつき）、グループのロール（`GroupMembershipQuery.exists`）、利用者のロール（`findById`） | 2.8、BR6、BR8.2、BR12.1 |
| `role.service` | `WorkRoleService.java` | 読み取り（`roles` は ID の順、`current` は解決の口の結果）と切り替え（排他を取らない。集合に無ければ `ROLE_NOT_ASSIGNED`、保存が同じなら書かずに 204、それ以外は保存を書いて `WORK_ROLE_SWITCHED`） | 2.10、BR7.5〜BR7.8 |
| `role.service` | `EffectivePermissionResolver.java`・`EffectivePermissionResolverImpl.java` | 契約 C5 の口。Javadoc に「`resolve` は1回の要求で対象が1つのときに使ってよい。2つ以上の対象は `snapshotFor` を1回」と理由（問い合わせの数・同じ作業ロールで答える一貫性）を書く。DSL が無い・作業ロールが無い・名前が今の DSL に無いときは NONE・不可。失敗は例外のまま投げる。読み取りだけ | 2.9、BR5.3〜BR5.5、NFR2.3、`logical-components.md` 4節 |
| `role.service` | `RoleGroupDeletionGuard.java`（**本物に書き換える**） | `canDelete`（グループへの直接の割り当ての数が 0 なら `Allowed`、そうでなければ `Blocked(数)`）、`assignedRoleCounts`（まとめて1回、渡した ID のすべてにキー）。`TODO(B5)` と仮の実装の説明を消す | BR10.1〜BR10.3、2.12 |
| `role.service` | `RoleAdminService` の変更 | 削除の割り当ての数の確かめ（`ROLE_IN_USE`、応答に数、detail は `InUse`）と保存の掃除、一覧の数 | BR3.2〜BR3.4 |
| `role.web` | `RoleAssignmentController.java`（`/api/admin/roles/{roleId}/assignments`: GET・POST、`users/{userId}`・`groups/{groupId}` の DELETE）・`RoleLookupController.java`（`/api/admin/groups/{groupId}/roles`・`/api/admin/users/{userId}/roles`）・`MeWorkRoleController.java`（`/api/me/work-role`: GET・PUT）・`MyPermissionsController.java`（`/api/me/permissions/schemas`・`tables?schema=`・`columns?schema=&table=`）と DTO（`AssignmentRequest(userId, groupId)`・`WorkRoleRequest(roleId)`・`RoleAssignmentsResponse`・`UserRoleResponse`・`RoleRefResponse`・`WorkRoleResponse`・`MyPermissionNodesResponse` など） | 11 の口。管理の口は `@ApiAccess(ADMIN)`、`/api/me` の口は `@ApiAccess(AUTHENTICATED)`。主体は要求の文脈から読み、利用者を指す値を受け取らない。伏せ字の値を取り出すのは DTO の組み立ての時だけ | C7・C8、BR2.2・BR12.1 |
| `group.store`（13節 Q4: A） | `StoreFailureClassifier.java` の1か所 | 外部キー `FK_GROUP_ROLE_ASSIGNMENTS_GROUP` も `Referenced` に区分する（削除なら `GROUP_IN_USE`、残りの数は2つ目のトランザクションで数え直す既存の形のまま） | group の BR5.4 の最後の守り |
| リソース | `db/migration/V12__u4_role_assignment.sql` | 4.5 の B5 の行 | NFR3.6 |
| `audit` | 4.6 の B5 の分 | 種類3つ・理由1つ・`WorkRoleSwitch`・`Assignment` の写し方 | C10 |
| 境界テスト | `role/RoleBoundaryArchitectureTest` | 5節の B5 の分 | NFR6.5 |

### 8.3 B5 で足すテスト・書き換えるテスト

| 層 | テスト |
|---|---|
| データの形 | `role/RoleSchemaIT` の追加（3つの表・制約・索引の名前）、`role/RoleMigrationCompatibilityIT` の追加（V1〜V10 の前の版の Flyway が V12 まで当てた内部DB で止まらない、`users.admin_flag` の値が変わらない）、`role/store/WorkRoleSelectionMergeIT`（R-15: 未確定の同じ主キーの行を `UncommittedWrite` で持ったまま `MERGE INTO work_role_selections KEY(user_id)` を流し、待って上限切れか・23505 か・ほかの誤りかの SQLState と誤りの番号を記録し、どれでも区分が `Busy` か想定外かを確かめる。先を確定させてからの `MERGE` は更新になること） |
| ドメイン | `role/domain/WorkRoleChooserTest`・`WorkRoleResolutionPropertyTest`（jqwik: 結果は集合の中か無し、保存が集合にあればそれ、無ければ最小の ID、明示で選んだ後は作業ロール以外のロールの足し外しで結果が変わらない（AC4.1.21））・`PermissionSnapshotTest`（写しの値が `PermissionInheritance` と同じ、DSL に無い名前は NONE、toString に名前が出ない）・`RoleAuditDetailTest` の追加、`audit/domain/AuditRoleEventFactoryTest`・`audit/service/AuditRoleEventListenerTest` の追加（切り替え・割り当ての項目） |
| DB アクセス | `role/store/RoleStoreClassificationTest` の追加（割り当ての主キー・外部キーの相手・作業ロールの保存の主キー）、`role/store/RoleStoreKeyGuardIT`（#6: ロールの行を排他せずに store の割り当ての書き込みを2つ。待たない違反と上限切れ）、`role/repository/RoleAssignmentRepositoryIT`（名前を結合した読み取り・ID の順・祖先の行・数） |
| 業務処理 | `role/service/RoleAssignmentServiceTest`（判定の順 BR6.9、外しの `ROLE_NO_CHANGE`、`lockForAssignment` の `Busy` と `exists` 偽、store の `AlreadyAssigned`（違反の経路）が2つ目のトランザクションで `ROLE_NO_CHANGE` の失敗の出来事になる（R-14）、`Referenced` の相手ごとの読み替え）・`WorkRoleServiceTest`（BR7.5〜BR7.7、保存が同じなら書かない）・`EffectivePermissionResolverImplTest`・`RoleGroupDeletionGuardTest`（**書き換え**: 数で `Allowed`・`Blocked`、渡した ID のすべてにキー）、`RoleGroupDeletionGuardIT`（本番の Bean、本物の表に割り当ての行を作って `Blocked(1)` と数）・`EffectivePermissionConsistencyIT`（`reliability-design.md` 3節の8つの組で、`resolve` と写しの値がテストの用意から書いた期待の表に等しい）・`EffectivePermissionQueryCountIT`（割り当て 1 と 100、グループ 1 と 100、設定の行 1 と 1 万で、決める読み取りが 4 回以内、`resolve` は祖先の行の1回、`snapshotFor` は設定の行の1回）・`RoleDeleteCleanupIT`（削除で設定と作業ロールの保存が消える、AC1.1.4・AC1.1.13・AC4.1.14） |
| API | `role/web/RoleAssignmentApiIT`（割り当て・外し・一覧の出どころ・グループのロール・利用者のロール・招待中の人は `USER_NOT_FOUND`・停止中の利用者は割り当てられる・存在しないグループの外しは `ROLE_NO_CHANGE`）・`WorkRoleSwitchApiIT`（読み取りと切り替え、`ROLE_NOT_ASSIGNED`、存在しないロールも同じ、読み替え、また割り当てたら戻る、AC4.1.x）・`MyPermissionsApiIT`（写しからの木、DSL が無いと空の items、作業ロールが無いと `workRole` が null で NONE、記号を含む名前）・`RoleAdminAuthorizationApiIT`（B5 の 7 つの管理の口を足し、403 の主体を Q3 の利用者にする）・`RoleMeAuthorizationApiIT`（`/api/me` の4つの道 × 未認証 401・停止中 401・ログイン済み 200/204）・`RoleGrantsNoAdminAccessIT`（AC2.2.14: `RequestMappingHandlerMapping` から ADMIN の印を持つ本番の口をすべて集め、道の変数に 1 を入れて、Q3 の利用者で 403 になること。口が増えても自動で対象になる、D-28）・`RoleSelfOperationIT`（AC2.2.12・AC2.2.13）・`RoleChangeVisibilityIT`（割り当て・外し・切り替え・保存・グループのメンバーの外しの直後に、変更の前のアクセストークンのまま解決の口・自分の権限・作業ロールの API の結果が変わる）・`RoleMassAssignmentApiIT`・`RoleIdorApiIT` の追加（割り当ての本文、切り替えの本文の `userId`、`PUT /api/me/preferences` にロール・作業ロールの項目、0・-1・存在しない ID）・`RoleConcurrencyIT` の追加（#1 の2つの順、#4）・`RoleBusyApiIT` の追加（#5）・`WorkRoleConcurrencyIT`（#8・#9）・`GroupRoleAssignmentConcurrencyIT`（#10、group の `TestGroupBarrier` と role の `TestRoleBarrier` を両方使う）・`GroupDeletionWithRoleIT`（`TestGroupDeletionGuard` を使わず、ロールを割り当てたグループの削除が 409 `GROUP_IN_USE`・`assignedRoles` 1、外した後は 204）・`WorkRoleSwitchAuditIT`（保存が同じときは行が増えない／読み替え中に同じロールを選ぶと1件増え `storedBeforeRoleId` が書く前の保存）・`RoleAuditIT` の追加・`RoleAuditWriteFailureIT` の追加（切り替え）・`RoleSecretLeakIT` の追加（TRACE で割り当ての一覧と利用者のロールを読み、試験の利用者のメールアドレス・氏名が 0 件）・`RoleConnectionUsageIT` の追加（割り当て・外し（利用者・グループ）・書く切り替えは 2、書かない切り替えと読み取りは 1）・`RoleAdminQueryCountIT` の追加（割り当ての一覧は利用者 1 人と 50 人、グループ 1 と 10 で同じ数）・`RoleMetricsIT` の追加（`/api/me/work-role`） |
| group のテストの書き換え | `group/web/GroupAdminAuthorizationApiIT`（13節 Q3: A。403 の主体を Q3 の利用者に置き換え、印もロールも無い利用者の行は残さない）。`group/testsupport/TestGroupDeletionGuard` は変えない |
| 境界 | `role/RoleBoundaryArchitectureTest` の追加（5節の B5 の規則） |
| 手伝い | `role/testsupport/RoleActors` の追加（`adminFlagMissingFullRole()`: 管理者の印を持たず、テストの DSL のすべてのスキーマに FULL・CREATE と DELETE を可にしたロールを割り当て、作業ロールに選んだ利用者）・`RoleFixtures` の追加（割り当て・保存の行）・`RoleApi` の追加 |

### 8.4 手順（B5）

#### Step 24: 作業の場の用意
- [ ] B4 を `develop` へ squash で統合し、push の後の CI を確かめた後に、`develop` の先頭から `feature/261004-role-menu-b5` を作る。開始の時点の HEAD を記録する。`git status --short` と E2E の生成物が無いことを確かめる。
- 対応: Testing Contract の「Project structure…」。

#### Step 25: テストの実行の準備
- [ ] `unit-test-instructions.md` 2節の B5 のコマンド（B4 で足したテストを名指し）が動くことを確かめ、結果を記録する。colima の状態と共有を確かめる。
- [ ] 洗い出しの検索を流し直す: `git grep -n 'TODO(B5)' -- backend`（1件）、`git grep -n 'GroupDeletionGuard' -- backend/src`、`git grep -n -e 'FK_GROUP_MEMBERS_GROUP' -- backend/src/main`、`git grep -n -e 'nonAdmin' -- backend/src/test/java/cherry/mastersmith/group/web/GroupAdminAuthorizationApiIT.java`。
- 対応: Testing Contract の runner の手順。

#### Step 26: 変更の前の基準
- [ ] Step 3 と同じコマンドで件数とカバレッジ（`role.*`・`audit.domain`・`audit.service`・`group.store`・全体）を記録する。次の空き移行番号が V12 であることを確かめる。
- 対応: NFR6.4・NFR3.6。

#### Step 27: データの形 — 実装
- [ ] `V12__u4_role_assignment.sql`（4.5）と、`UserRoleAssignment`・`GroupRoleAssignment`・`WorkRoleSelection` のエンティティを作る。
- 対応: BR6.2・BR6.7、NFR3.6、`entities.md`。

#### Step 28: データの形 — テスト（`MERGE` の確かめを含む）
- [ ] `RoleSchemaIT`・`RoleMigrationCompatibilityIT` の追加と `WorkRoleSelectionMergeIT` を書いて流す（8.3）。`MERGE` の結果（SQLState・誤りの番号・待ったか）を `generation-notes.md` に記録し、Step 31 の保存の形（D-19）を決める。23505・上限切れ以外が出たときは「読んでから更新か挿入」の形にし、`code-summary.md` の計画との差に書く。
- 対応: NFR3.6・NFR3.7（R-15）、`reliability-design.md` 4.2 の #9。

#### Step 29: ドメイン — 実装
- [ ] 8.2 の `role.domain` の追加と、4.6 の B5 の分を `audit` に足す（網羅の `switch` の追従を含む）。
- 対応: BR6.5・BR7.1・BR7.2・BR11.4、C5・C10。

#### Step 30: ドメイン — テスト
- [ ] 8.3 のドメインの層のテストを書いて流す。単体テストの結果に `WorkRoleResolutionPropertyTest` が出ることを確かめる。
- 対応: AC4.1.16・AC4.1.21、BR7.1〜BR7.4、NFR6.2。

#### Step 31: DB アクセス — 実装
- [ ] 8.2 の `role.repository`・`role.store` の追加。有効な作業ロールを決める4回の読み取りはロールの名前を結合して読む（D-15）。写しと祖先の行は行の値だけの射影（エンティティにしない、`performance-design.md` 4節）。
- 対応: BR3.3・BR6.3・BR6.7・BR8.5、NFR2.3・NFR2.6。

#### Step 32: DB アクセス — テスト
- [ ] `RoleStoreClassificationTest` の追加・`RoleStoreKeyGuardIT`・`RoleAssignmentRepositoryIT` を書いて流す。
- 対応: AC2.2.15（最後の守り）、BR6.3・BR8.5、NFR3.7（T3・T4 の本物での確かめ）。

#### Step 33: 業務処理 — 実装
- [ ] `RoleAssignmentService`・`WorkRoleService`・`EffectivePermissionResolver`(`Impl`) を作り、`RoleAdminService` の削除と一覧を直す。
- [ ] `RoleGroupDeletionGuard` を本物に書き換え、`TODO(B5)` と仮の実装の説明を消す。
- [ ] group の `StoreFailureClassifier` に `FK_GROUP_ROLE_ASSIGNMENTS_GROUP` を足す（13節 Q4: A）。
- 対応: 2.3・2.8〜2.12、BR3.2〜BR3.4・BR5.4・BR5.5・BR6・BR7.5〜BR7.8・BR8.2・BR10、C4・C5。

#### Step 34: 業務処理 — テスト
- [ ] 8.3 の業務処理の層のテスト（`RoleGroupDeletionGuardTest` の書き換え、`RoleGroupDeletionGuardIT`・`EffectivePermissionConsistencyIT`・`EffectivePermissionQueryCountIT`・`RoleDeleteCleanupIT` を含む）を書いて流す。13節 Q4: A のとおり group の `StoreFailureClassifierTest` に1件足し、`GroupStoreConstraintIT` に「割り当ての行が残るグループの削除の外部キーの違反が `Referenced` になる」1件を足す。
- 対応: AC1.1.3・AC1.1.4・AC1.1.13・AC2.1.3・AC2.1.9・AC2.1.10・AC2.2.2・AC2.2.4・AC4.1.14・AC4.1.15・AC4.1.18・AC5.1.14、BR10.1〜BR10.3、NFR2.3・NFR3.4。

#### Step 35: API — 実装
- [ ] 8.2 の `role.web` の4つのコントローラーと DTO。管理の口は `@ApiAccess(ADMIN)`、`/api/me` の口は `@ApiAccess(AUTHENTICATED)`。
- 対応: AC2.2.5・AC4.1.12・AC4.1.19・AC4.2.3・AC4.2.5、BR2・BR12、C7・C8。

#### Step 36: API — テスト（結合）と group のテストの書き換え
- [ ] 8.3 の API の層のテストを書く。`GroupAdminAuthorizationApiIT` と `RoleAdminAuthorizationApiIT` の 403 の主体を Q3 の利用者に置き換え（13節 Q3: A）、`RoleGrantsNoAdminAccessIT` を足す。
- [ ] Step 36 のコマンドを流す（group の結合テストの全体を含める）。
- 対応: AC1.1.6・AC2.1.5・AC2.2.1〜AC2.2.8・AC2.2.10〜AC2.2.16・AC4.1.1〜AC4.1.6・AC4.1.11〜AC4.1.13・AC4.1.17・AC4.1.19・AC4.1.20・AC4.2.1〜AC4.2.6、NFR1.1・NFR1.2（B4 の差の解消、D-17）・NFR1.4・NFR1.6・NFR1.10・NFR2.6・NFR2.11・NFR3.1 (a)(d)(e)(g)・NFR3.4・NFR3.5・NFR5.1・NFR5.2・NFR5.6、group の読み直しの R-04。

#### Step 37: 境界と構造の検査
- [ ] `RoleBoundaryArchitectureTest`（B5 の規則）と、Step 14 と同じ一式を流す（B5 の 11 の口の印と道が合うこと）。
- 対応: NFR1.3・NFR6.5、AC1.1.15。

#### Step 38: バックエンドの区切りの確かめ
- [ ] `spotlessApply` の後、colima の環境変数を付けて `caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest` を流す。
- 対応: コミットの区切り B5 の C2 の終わり。

#### Step 39: 負荷の台本
- [ ] `perf/k6/scenarios.js` に `workRoleSwitch`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`myPermissionsTree`・`rolePoolLimit` を足す（Step 16 の op のタグの結果に合わせる）。`rolePoolLimit` は閾値なしで状態コードの件数（204・409・500・そのほか）を数える。作業ロールの切り替え 3 VU・グループへの割り当てと外しを交互に 2 VU（合否の回）、同じ構成の 20 VU（記録の回）。
- [ ] `perf/README.md` の role の節に B5 の分を書く: 合否の回（`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=11`、時間切れの累計 0・500 が 0 件・`acquire` の最大 20 ms 未満）と記録の回（上限 10・20 VU、`acquire` 10 ms 以上が届いた証拠、40 VU の流し直しは1回まで）。`acquire` は応答の `baseUnit` を確かめて秒にそろえてから 0.020 と比べる（基盤の読み直しの R-06）。健全性の確かめ以外の接続の使い手が無かったことを、試験の前後の `active` とアプリのログで切り分ける。
- [ ] 場面ごとに `k6 inspect --include-system-env-vars` を流して記録する。
- 対応: NFR2.2・NFR2.3・NFR2.5・NFR2.7・NFR2.8・NFR2.10。

#### Step 40: 文書
- [ ] README の role の節に B5 の 11 の口・作業ロールの決め方・解決の口の使い分け（navigation への案内）・同時の操作（ロール → グループの順の排他、切り替えは排他しない）・監査を足し、「スキーマの変更（Flyway）」に V12、「監査ログ（U4）」に B5 の種類と理由を書く。
- 対応: NFR6.1（引き継ぎ）。

#### Step 41: 取り残しと変えないものの確かめ
- [ ] Step 19 と同じ確かめ。加えて `git grep -n 'TODO(B5)' -- backend` が 0 件、`RoleGroupDeletionGuard` が仮の実装の説明を持たないこと、group の本体の差が Q4: A の1か所だけであること。
- 対応: BR10.3、group の読み直しの R-04。

#### Step 42: 記録（B5 の分）
- [ ] `code-summary.md` の B5 の節（`MERGE` の確かめの結果を含む）、`traceability.json`、`source-manifest.json`、`generation-notes.md` を更新する。
- 対応: Testing Contract の「Documentation and traceability」。

#### Step 43: 1コマンドの検査（統合の前の関門）
- [ ] Step 21 と同じ。単体テストの結果に B4・B5 の3つの `*PropertyTest` があること、B5 の終わりの条件（8.1）をすべて確かめたことを記録する。
- 対応: NFR6.4、`cicd-pipeline.md` 10節の B5 の行。

#### Step 44: E2E
- [ ] Step 22 と同じ（作業ロールの API と認可の表に手が入るため）。生成物を消す。
- 対応: `cicd-pipeline.md` 8節。

#### Step 45: コミットの提案・統合の提案（B5）
- [ ] Step 23 と同じ形で、B5 の C1〜C3 を提案・コミットし、`aidlc/` をコミットしてから squash の統合を提案する。push の後の CI を確かめてから B6 に入る。
- 対応: `team.md`・`project.md` の Change Control。

## 9. 部 B6 — 権限の YAML の書き出し・確かめ・適用

### 9.1 範囲と終わりの条件（`bolt-plan.md` の B6、`functional-spec.md` 9節）

- 流れ: 2.13〜2.15。決まり: BR9・BR11.7（`ROLE_TRANSFER_APPLIED`）・BR12.3。NFR: NFR1.5・NFR1.9・NFR2.4・NFR2.9・NFR3.2・NFR3.8・NFR5.6、`reliability-design.md` 2.5・2.6。
- 終わりの条件:
  - 書き出し（ロールを ID の順、対象を名前のコードポイントの順、設定のある対象だけ、型や特別な意味を持ちうる名前は二重引用符、上限を超えたら `X-Role-Transfer-Exceeds-Import-Limit: true`）・確かめ（指紋、保存しない）・適用（検証し直して指紋が違えば拒否、1つのトランザクション、途中の失敗で何も変わらない）。
  - 信頼できない入力の守り（大きさ 10 MiB・深さ 10・コレクションの別名 0・展開後の節 1,000,000 の境界、タグ、重複キー、知らない項目、版の誤り、413・415、部品の例外の文を含めない）。
  - 確かめと適用を同時に1つずつしか通さない入口（`RoleTransferSlot`）と、実際の Tomcat で 10 MiB の2つ目に 409 `ROLE_BUSY` が届くこと（`max-swallow-size`、基盤の Q1: A）。
  - `application.yaml` の `org.springframework.jdbc.core.StatementCreatorUtils: OFF` と、TRACE を `org.springframework.jdbc` に広げた漏えいの確かめ。
  - k6 の `roleExport`・`roleTransferLarge` の台本、準備の台本（基盤の Q2: A）と上限ちょうどの YAML の生成の部品（13節 Q7）。
  - `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と E2E の全体が通る。
- 確かめること: 書き出しと読み込みの往復で設定が変わらず、確かめの後の変更を取りこぼさない。
- 見せるもの: 書き出した YAML を別のロール名に直して読み込み、確かめの一覧（足す・変わる・消える）を見てから適用する。

### 9.2 作るもの・手を入れるもの

| 場所 | ファイル（案） | 中身 | 出典 |
|---|---|---|---|
| `role.domain` | `RoleAuditDetail`（`Transfer`・`TransferSummary` を足す）・`RoleOperation`・`RoleAuditFailure`・`RoleProblemTypes`（`ROLE_TRANSFER_INVALID`・`ROLE_TRANSFER_STALE`）・`TransferError.java`（`reason`・`line`・`column`・`path`。入れた値は持たない） | B6 の分 | BR9.5・BR11.7、`entities.md` |
| `role.transfer` | `RoleTransferDocument.java`（検証済みの木）・`RoleTransferReader.java`・`RoleTransferWriter.java`・`TransferPlanner.java`・`TransferPlan.java`・`TransferRolePlan.java`・`TransferChange.java`・`TransferFingerprint.java`・`RoleTransferPayload.java`・`RoleTransferTree.java`・`TransferErrors.java`・`package-info.java` | 読み取り: ルートは `version`（1 だけ）と `roles`、ロールは `schemas`、スキーマとテーブルは `main`・`create`・`delete` と下位、カラムは主権限の値だけ。主権限は文字列 `NONE`・`READ`・`FULL` だけ、補助権限は真偽だけ（文字列の `'true'`・null・数は `INVALID_VALUE`）、知らない項目・欠け・カラムの補助権限・ロールの名前の規則・鍵の重なり・対象の名前の誤りを最大 100 件（位置は `YamlPositions` から）。書き出し: 別名・タグを作らず、`security-design.md` 5節の名前を二重引用符で囲む。計画: 鍵で照らして CREATE・REPLACE、ADDED・CHANGED・CLEARED・`inCurrentDsl`・`untouchedRoles`。指紋: BR9.9 の並び（`dslHash` と置き換えるロールの ID を含む）の SHA-256 の 16 進数の小文字。受け渡しの型の toString は大きさと件数だけ | BR9.1・BR9.5〜BR9.9・BR9.14、NFR1.9、`security-design.md` 4.3・5節 |
| `role.store` | `RoleStore` の追加（`applyTransfer`） | 置き換えるロールの行を ID の昇順に排他、作るロールの行の書き込み（名前の鍵の違反は `NameTaken`）、`EntityManager.flush()` の後に `JdbcTemplate`（コンストラクターで受ける、`setQueryTimeout(10)`）で、置き換えるロールごとに設定の行を1文で消し、作る行を 1,000 件ずつのバッチで書き、`updatedAt` を書く。同じトランザクションの中で JDBC の行をエンティティとして読まない | `reliability-design.md` 2.5、`performance-design.md` 2.3 |
| `role.service` | `RoleTransferService.java`・`RoleTransferSlot.java`・`RoleTransferLimits.java`・結果の型（`TransferCheckResult`・`TransferApplyResult`・`TransferExport`） | 判定の順（NFR3.8）: 本文の型・大きさ（web と既存のフィルター）→ 入口（`tryAcquire`、取れなければ `ROLE_BUSY` と専用の WARN）→ DSL の有無 → 本文の流れを上限まで読む → `SafeYamlReader.read` → 中身の検証 → （適用だけ）ロールの排他 → 一覧の作り直し → DSL の有無（もう一度）→ 指紋 → 変える点の有無。入口は `finally` で必ず放す。書き出しは読み取りだけのトランザクション1つで、入口に入らない（受け入れた制約） | 2.13〜2.15、BR9.2〜BR9.13、`reliability-design.md` 2.6 |
| `role.web` | `RoleTransferController.java`（`/api/admin/role-transfer`: `export`（GET、`application/yaml`、添付のファイル名、上限超えのヘッダー）・`check`・`apply`（POST、`consumes = application/yaml`、適用は `X-Role-Transfer-Fingerprint`（16 進数の小文字 64 文字、無い・形の誤りは 400））・`RoleWebConfig.java`（`RequestBodyLimitRoute` を確かめと適用の道に、上限は `RoleTransferLimits` と同じ値）・`RoleTransferLimitProperties.java`（`mastersmith.role-transfer.max-bytes`、既定 10,485,760。テストで上限を小さくするためだけの設定値の record、D-21）・DTO（`TransferCheckResponse`・`TransferResultResponse`、`ROLE_TRANSFER_INVALID` の `errors`） | 3つの口、`@ApiAccess(ADMIN)`。本文は要求の入力の流れを伏せる型（`RoleTransferPayload` の元）で service に渡し、web で読まない | C7 との差（`functional-spec.md` 8節）、BR9.2・BR9.4、NFR2.9 |
| リソース | `application.yaml` | `logging.level.org.springframework.jdbc.core.StatementCreatorUtils: OFF` と理由のコメント（JDBC のバッチの引数の値を TRACE に出さない） | `security-design.md` 4.3 |
| `audit` | 4.6 の B6 の分 | 種類1つ・理由1つ・`Transfer`・`TransferSummary` の写し方 | C10 |
| 境界テスト | `role/RoleBoundaryArchitectureTest` | 5節の B6 の分 | NFR6.5 |
| 手伝い（テストと負荷の試験で共用） | `backend/src/test/java/cherry/mastersmith/role/testsupport/LargeRoleTransferYaml.java`（13節 Q7: A） | ロールの数・カラムの数・名前の長さ・目標のバイト数を引数に取り、バイト数をちょうどに合わせた YAML を作る。プロジェクトのほかのクラスに依存しない1ファイルにし、テストからは方法として呼び、負荷の試験からは `java <このファイル> …`（1ファイルのソースの起動）で呼ぶ | `performance-design.md` 2.3 |

### 9.3 B6 で足すテスト

| 層 | テスト |
|---|---|
| ドメイン | `role/domain/RoleAuditDetailTest` の追加（`Transfer`・`TransferSummary`）、`audit/domain/AuditRoleDetailJsonTest` の追加（import の detail が上限を超えると要約、要約も上限に収まる）、`audit/domain/AuditRoleEventFactoryTest` の追加（対象の列が空で detail に書く、BR11.4） |
| YAML の部品 | `role/transfer/RoleTransferReaderTest`（13 の理由ごと、最大 100 件、位置、入れた値を載せない）・`RoleTransferWriterTest`（並び・設定のある対象だけ・二重引用符の名前の一覧・別名とタグを作らない）・`TransferPlannerTest`（CREATE・REPLACE・ADDED・CHANGED・CLEARED・`inCurrentDsl`・`untouchedRoles`・空の一覧）・`TransferFingerprintTest`（同じ中身は同じ指紋、対応表の順に依らない、`dslHash` と置き換えるロールの ID で変わる、16 進数の小文字 64 文字）・`RoleTransferRoundTripPropertyTest`（jqwik: 書き出し → 読み込みで変わる点が無い。名前に `true`・`false`・`null`・`~`・`123`・`yes`・`on`・日付・`:`・`#`・`&`・`*`・`!`・`|`・`>`・`'`・`"`・`%`・`@`・`` ` ``・前後の空白を含める）、`role/testsupport/LargeRoleTransferYamlTest`（目標のバイト数ちょうど、読み込めて検証を通る） |
| DB アクセス | `role/store/RoleStoreTransferBatchIT`（1,000 件ずつのバッチ、置き換えの削除、`updatedAt`、名前の鍵の違反の区分、文の上限 10 秒の設定が付いていること） |
| 業務処理 | `role/service/RoleTransferServiceTest`（判定の順 NFR3.8、DSL が無い・外れた・指紋の違い・空の一覧・`Rejected` の区分の写し方・入口を放すこと）・`RoleTransferSlotTest`・`RoleTransferAtomicityIT`（テストだけで内部DB に H2 のトリガー（テストのクラス）を置き、決めた目印の対象の行（例 1,000 件目）の書き込みで失敗させる。適用の後にすべてのロールと設定の行が前と同じで、`ROLE_TRANSFER_APPLIED` の成功の行が無いこと。JDBC のバッチと Hibernate の書き込みが同じトランザクションで巻き戻ること。本番のコードは変えない、D-25）・`RoleTransferStaleIT`（#13: 確かめ → 別の要求で保存・削除・同名の作成・DSL の適用し直し → 適用は `ROLE_TRANSFER_STALE`、DSL を外したときは `DSL_NOT_APPLIED`、状態は別の要求の後のまま）・`RoleTransferBusyIT`（#11 適用どうし・確かめと適用、#12 適用中の同じロールへの割り当てが 3 秒で `ROLE_BUSY`、1つ目が例外で終わった後に次が通る） |
| API | `role/web/RoleTransferApiIT`（書き出し・確かめ・適用の成功、`Content-Type`・添付のファイル名、415、指紋のヘッダーの欠け・形の誤りの 400、`ROLE_NO_CHANGE`、作るロールの名前の重なり、DSL が無いときの書き出しは書ける）・`RoleTransferLimitsApiIT`（10,485,760 バイトちょうどは受け付け 1 バイト超えは 413（Content-Length つきは既存のフィルター、分割送信は読み込みの上限）、深さ 10 と 11、コレクションの別名 0 と 1、スカラーの別名は受け付ける、展開後の節 1,000,000 と 1,000,001（流れの書き方の並び `[1,1,…]` で作る。ちょうどの方は節の上限に当たらず中身の誤りになることで確かめる）、`!!` のタグ、ロールと対象の重複キー、知らない項目、版の誤り、応答に部品の例外の文が無い）・`RoleTransferExportLimitIT`（`mastersmith.role-transfer.max-bytes` をテストだけで小さくし、上限ちょうどの書き出しにはヘッダーが無く 1 バイト超えには付く、超えたファイルを2つに分けてそれぞれ確かめと適用ができる）・`RoleTransferTomcatSwallowIT`（`RANDOM_PORT` の実際の Tomcat に、生のソケットで要求の行・ヘッダー・本文を書き切ってから応答を読む。1つ目の確かめを待ち合わせで止めたまま 10 MiB の2つ目で 409 `ROLE_BUSY` の Problem Details、上限を少し超える本文で 413 の Problem Details、そのあと同じ接続か新しい接続で次の要求が通る。D-24）・`RoleTransferAuditIT`（適用の1行と detail の項目、確かめ・書き出しで行が増えない、要約への切り替え）・`RoleSecretLeakIT` の追加（目印の名前 `LEAKCHECK_ROLE_7f3a` を入れた YAML で確かめと適用を流し、TRACE を `cherry.mastersmith` と `org.springframework.jdbc` に広げて、ログに目印が 0 件）・`RoleAdminAuthorizationApiIT` の追加（3つの口）・`RoleConnectionUsageIT` の追加（適用は 2、確かめ・書き出しは 1）・`RoleBusyLogTraceIT` の追加（入口の経路の WARN は `lockKind=ROLE_TRANSFER_SLOT` だけで `exceptionClass` を持たない、R-12） |
| 境界 | `role/RoleBoundaryArchitectureTest` の追加（5節の B6 の規則） |
| 手伝い | `role/testsupport/RawTransferPost`（生のソケットで POST を送る。`invitation/testsupport/RawHttp` と同じ考え方）・`role/testsupport/FailingSettingTrigger`（H2 のトリガー） |

### 9.4 手順（B6）

#### Step 46: 作業の場の用意
- [ ] B5 を `develop` へ squash で統合し、push の後の CI を確かめた後に、`develop` の先頭から `feature/261004-role-menu-b6` を作る。開始の時点の HEAD を記録する。`git status --short` と E2E の生成物が無いことを確かめる。
- 対応: Testing Contract の「Project structure…」。

#### Step 47: テストの実行の準備
- [ ] `unit-test-instructions.md` 2節の B6 のコマンドが動くことを確かめる。colima の状態と共有を確かめる。
- [ ] `application.yaml` に `server.tomcat.max-swallow-size: 11MB` があることと、B1 の記録（dsl-v2 の `code-summary.md` と README）に読み捨ての影響（未認証でも最大 11MB を読み捨てる・スレッドの占有が延びる）が書かれているかを確かめて記録する（基盤の読み直しの R-02）。
- 対応: Testing Contract の runner の手順。

#### Step 48: 変更の前の基準
- [ ] Step 3 と同じコマンドで件数とカバレッジを記録する。移行は足さない。
- 対応: NFR6.4。

#### Step 49: ドメイン — 実装
- [ ] 9.2 の `role.domain` の追加と、4.6 の B6 の分を `audit` に足す。
- 対応: BR9.5・BR11.5・BR11.7、C10。

#### Step 50: ドメイン — テスト
- [ ] 9.3 のドメインの層のテストを書いて流す。
- 対応: NFR5.5・NFR5.6。

#### Step 51: YAML の部品 — 実装（role.transfer）
- [ ] 9.2 の `role.transfer` と、上限ちょうどの YAML の生成の部品（13節 Q7: A の Java の1ファイル）を作る。`role.transfer` は DB の部品と `dsl.parse` に依存しない（Jackson の木と `role.domain` と `dsl.service` の `YamlPositions` だけ）。
- 対応: BR9.1・BR9.5〜BR9.9・BR9.14、NFR1.9。

#### Step 52: YAML の部品 — テスト
- [ ] 9.3 の YAML の部品の層のテストを書いて流す。単体テストの結果に `RoleTransferRoundTripPropertyTest` が出ることを確かめる。
- 対応: AC3.1.6・AC3.1.12・AC3.1.14・AC3.1.15、BR9.9、NFR3.7（U1・U2 の本物での確かめ）、NFR6.2。

#### Step 53: DB アクセス — 実装
- [ ] `RoleStore.applyTransfer`（9.2）と、`application.yaml` の `StatementCreatorUtils: OFF`（理由のコメントつき）。
- 対応: BR9.7・BR9.11、NFR1.8・NFR1.11、`reliability-design.md` 2.5。

#### Step 54: DB アクセス — テスト
- [ ] `RoleStoreTransferBatchIT` を書いて流す。
- 対応: NFR3.2（一部）、NFR3.7（U4）。

#### Step 55: 業務処理 — 実装
- [ ] `RoleTransferService`・`RoleTransferSlot`・`RoleTransferLimits`・結果の型。入口の WARN は専用の出し方（D-20）。
- 対応: 2.13〜2.15、BR9.2〜BR9.13・BR9.15、NFR3.2・NFR3.8、`reliability-design.md` 2.6。

#### Step 56: 業務処理 — テスト
- [ ] `RoleTransferServiceTest`・`RoleTransferSlotTest`・`RoleTransferAtomicityIT`・`RoleTransferStaleIT`・`RoleTransferBusyIT` を書いて流す。
- 対応: AC3.1.2〜AC3.1.5・AC3.1.8・AC3.1.10、NFR3.1 (f)・NFR3.2・NFR3.3・NFR3.8。

#### Step 57: API — 実装
- [ ] `RoleTransferController`・`RoleWebConfig`・`RoleTransferLimitProperties`・DTO。クラスに `@ApiAccess(ADMIN)`。
- 対応: AC3.1.1・AC3.1.7・AC3.1.16・AC3.1.17、BR9.2・BR9.4・BR9.13・BR9.14、NFR1.3・NFR1.5・NFR2.9。

#### Step 58: API — テスト（結合）
- [ ] 9.3 の API の層のテストを書いて流す。テストの JVM のヒープ（1g）が足りなければ止めて依頼者に確かめる（6節 A28）。
- 対応: AC3.1.1〜AC3.1.17（画面の分を除く）、NFR1.2・NFR1.5・NFR1.7・NFR1.9・NFR2.9・NFR2.11・NFR5.3・NFR5.6、基盤の読み直しの R-01、NFR 設計の読み直しの R-12。

#### Step 59: 境界と構造の検査
- [ ] `RoleBoundaryArchitectureTest`（B6 の規則）と、Step 14 と同じ一式を流す（B6 の3つの口）。
- 対応: NFR1.3・NFR6.5。

#### Step 60: バックエンドの区切りの確かめ
- [ ] `spotlessApply` の後、`caffeinate -i ./gradlew :backend:spotlessCheck :backend:test :backend:integrationTest` を流す。
- 対応: コミットの区切り B6 の C2 の終わり。

#### Step 61: 負荷の台本と準備の台本
- [ ] `perf/k6/scenarios.js` に `roleExport`（10 VU・3 分）と `roleTransferLarge`（確かめと適用を別の場面にし、1 VU・1 回。適用は確かめの指紋を `setup()` で受ける。判定は `iteration_duration` の最大で、確かめ 15 秒・適用 30 秒）を足す。準備の場面（1 VU・1 回。DSL の投入と適用、準備の YAML の確かめと適用、グループとメンバー、割り当て）を足す。準備の DSL は `perf/make-large-dsl.mjs`（版 2、100 テーブル × 100 カラム）で作り、対象DB は要らない（6節 A20）。準備の YAML（ロール 1,000、うち1つは1万カラム明示、置き換える先の 25 ロール）と `roleTransferLarge` の 10 MiB の YAML は、上限ちょうどの YAML の生成の部品で作る。
- [ ] `perf/README.md` の role の節に B6 の分と全体の順を書く: 利用者は手順 2'' の SQL の形（メンバー 1,000 人のグループの利用者、100 のグループに属する利用者、VU ごとの利用者、操作する試験用の管理者。初期管理者は使わない。予約のドメイン）、準備の場面を1回、各場面、**`roleTransferLarge` を最後に流す**（流し直すときは使い捨ての環境を作り直して準備からやり直す。基盤の読み直しの R-05）、台本全体を `caffeinate -i` で包むこと、片付け。
- [ ] 場面ごとに `k6 inspect --include-system-env-vars` を流し、生成の部品が上限ちょうどの YAML を作れることを記録する。
- 対応: NFR2.1・NFR2.4・NFR2.5・NFR2.9・NFR2.10、基盤の Q2: A。

#### Step 62: 文書
- [ ] README の role の節に B6 の3つの口・YAML の形と上限・指紋・送り方（`application/yaml` とヘッダー）・同時に1つずつ・上限超えの書き出しのヘッダーと分けて読み込むこと、受け入れた制約（分割送信の本文は既存のフィルターが上限まで先に読む（R-11）、本文をゆっくり送る管理者が入口を持ち続ける（R-16）、書き出しと DSL の管理の適用は入口に入らない、読み捨ての影響（Step 47 で B1 の記録に無かったとき、R-02））を書く。「監査ログ（U4）」に B6 の種類と理由を書く。
- 対応: NFR2.9、NFR 設計の読み直しの R-11・R-16、基盤の読み直しの R-02。

#### Step 63: 取り残しと変えないものの確かめ
- [ ] Step 19 と同じ確かめ。`application.yaml` の差が B4 のコメントと B6 の1行だけであること。B4〜B6 を通して lockfile が変わっていないこと（`git diff --stat f9d1134 -- backend/gradle.lockfile frontend/package-lock.json`）。
- 対応: NFR6.6。

#### Step 64: 記録（B6 の分と単位の全体）
- [ ] `code-summary.md` の B6 の節と単位の全体のまとめ（計画との差・承認の場で確かめること・後に回すこと（2.2 の記録だけの項目、group の R-05 の H2 の文への依存）・見せるもの）、`traceability.json`（単位の全体。Deferred は U5・U6・U7 と Performance Validation・Observability Setup）、`source-manifest.json`、`generation-notes.md`。
- 対応: Testing Contract の「Documentation and traceability」。

#### Step 65: 1コマンドの検査（統合の前の関門）
- [ ] Step 21 と同じ。単体テストの結果に4つの `*PropertyTest` があること、`verify` の時間の延び（上限切れと 10 MiB のテスト）を記録する。
- 対応: NFR6.4、`cicd-pipeline.md` 10節の B6 の行。

#### Step 66: E2E
- [ ] Step 22 と同じ。生成物を消す。
- 対応: `cicd-pipeline.md` 8節。

#### Step 67: コミットの提案・承認の場・統合の提案（B6）
- [ ] 指揮役が B6 の C1〜C3 を提案し、承認を得てコミットする。コード生成の段の承認の場は C3 の後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く（`project.md` の学び）。承認の場の前に、`code-summary.md` の B4〜B6 の「依頼者に確かめたいこと」「承認の場で確かめる」の節を洗い出して並べる（`project.md` の学び）。
- [ ] 承認の後、`aidlc/` の未コミットの変更（監査ログを含む）をコミットしてから、`develop` への squash の統合を提案する。統合の後にブランチを消す。push は依頼者。
- 対応: `team.md`・`project.md` の Change Control。

## 10. ストーリー・要件と手順の対応

### 10.1 受け入れ基準（機能設計の `traceability.json` の対応を Bolt と手順に写したもの）

| ID | 中身（要旨） | この単位の決まり | Bolt と手順 |
|---|---|---|---|
| AC1.1.1・AC1.1.2・AC1.1.5・AC1.1.9〜AC1.1.12・AC1.1.14・AC1.1.17 | 作成・名前の規則と重なり・認可・一括代入・IDOR・変えるものが無い・指標 | BR1・BR2・BR3.1・BR3.6・BR8.4・BR8.5 | B4: Step 6〜13 |
| AC1.1.15 | API の分類の網羅（role の分） | BR2.1・BR2.2 | B4・B5・B6: Step 12・14・35・37・57・59 |
| AC1.1.16 | 監査の種類と行（role の分）、名前の長さ | BR11.4・BR11.8・BR11.9 | B4・B5・B6: Step 6・7・13・29・36・49・58 |
| AC1.1.3・AC1.1.6・AC1.1.8 | 割り当てが残る削除の拒否・削除と割り当ての重なり・応答の数 | BR3.2・BR3.4・BR8.1 | B4（割り当てなしの削除）Step 10〜13、B5（拒否と重なり）Step 33〜36 |
| AC1.1.4・AC1.1.13 | 削除で設定と作業ロールの保存が消える | BR3.3・BR7.2 | B4（設定）Step 8〜13、B5（作業ロールの保存）Step 31〜34 |
| AC1.1.7 | 画面 | — | Deferred（U6） |
| AC1.2.1〜AC1.2.4・AC1.2.6・AC1.2.7・AC1.2.9〜AC1.2.13・AC1.2.16〜AC1.2.18 | 木・明示と実効と継承の元・DSL が無い・今の DSL に無い・継承の関数・値の検証・認可・後勝ち・`inMenu`・変わる点が無い・開いた階層だけ | BR4・BR5.1・BR5.2・BR5.6・BR8.1・BR11.6 | B4: Step 6〜13 |
| AC1.2.5 | 保存の監査と、次の要求から効く | BR5.4・BR11.6 | B4（監査）Step 13、B5（解決の口で次の要求から）Step 36 の `RoleChangeVisibilityIT` |
| AC1.2.8 | 解決の口で今の DSL に無い対象は NONE | BR5.3・BR4.6 | B4（保存の拒否）・B5（解決の口）Step 34 |
| AC1.2.14・AC1.2.15 | 画面 | — | Deferred（U6） |
| AC2.1.3・AC2.1.5（ロールの側） | 問う口とグループへの割り当ての重なり | BR6.6・BR8.2・BR10 | B5: Step 33・34・36 |
| AC2.1.9・AC2.1.10 | メンバーの外しの後の作業ロール | BR6.5・BR6.8・BR7.2 | B5: Step 34・36 |
| AC2.1.1・AC2.1.2・AC2.1.4・AC2.1.6〜AC2.1.8・AC2.1.11〜AC2.1.13 | グループの管理 | — | Deferred（U3、B3 で済み） |
| AC2.2.1〜AC2.2.8・AC2.2.10〜AC2.2.16 | 割り当て・出どころ・次の要求から効く・最初のロール・認可・漏えい・IDOR・一括代入・自分自身・管理の権限が増えない・重ね・招待中の人 | BR2・BR5.3・BR5.4・BR6・BR7.1・BR7.2・BR12 | B5: Step 29〜36 |
| AC2.2.9 | 画面 | — | Deferred（U6） |
| AC3.1.1〜AC3.1.10・AC3.1.12〜AC3.1.17 | 書き出し・確かめ・適用・指紋・DSL が無い・今の DSL に無い・上限・一括確定・要約・中身の誤り・部品の文・往復・並び・認可・上限の値 | BR9・BR11.7・BR12.3 | B6: Step 49〜58 |
| AC3.1.11 | 画面 | — | Deferred（U6） |
| AC4.1.1〜AC4.1.6・AC4.1.11〜AC4.1.21 | 作業ロールの切り替え・保存・選べるロール・無いとき・重なり・合算しない・存在しないロール・他人を指さない・明示の選び直し・削除・読み替え・最初のロール・次のログイン・DSL が無い・停止中・指標・性質 | BR2.2・BR3.3・BR5.3〜BR5.6・BR7 | B5: Step 29〜36 |
| AC4.1.7〜AC4.1.10 | 画面 | — | Deferred（U7） |
| AC4.2.1〜AC4.2.6 | 自分の権限の API（Should、Q9） | BR2.2・BR4.11・BR4.12・BR5.3・BR5.4 | B5: Step 35・36 |
| AC4.2.7 | 画面 | — | Deferred（U7） |
| AC5.1.14 | DSL に無いテーブルは解決の口が NONE | BR5.3 | B5: Step 34（`EffectivePermissionConsistencyIT` の (f)） |
| AC5.1.1〜AC5.1.13・AC5.1.15〜AC5.1.18 | 業務のメニュー | — | Deferred（U5） |

### 10.2 NFR の ID（NFR 要件の枝番）

| ID | 中身（要旨） | Bolt と手順 |
|---|---|---|
| NFR1.1・NFR1.2 | API ごとのサーバー側の判定と 401・403・200 の表（B4 の 403 の差は B5 で解消） | B4 Step 13、B5 Step 36、B6 Step 58 |
| NFR1.3 | `ApiAccess` の印と網羅の検査 | Step 14・37・59 |
| NFR1.4・NFR1.10 | 一括代入・IDOR・0 以下の ID は 404 | Step 13・36 |
| NFR1.5 | YAML の守り（上限の境界・タグ・重複キー・413） | Step 58 |
| NFR1.6 | 割り当ての一覧・利用者のロールの伏せ字の型 | Step 36 |
| NFR1.7 | 応答と監査の行の秘密 | Step 13・36・58 |
| NFR1.8 | 違反・上限切れの例外の文を外へ出さない | Step 9・13・32 |
| NFR1.9 | YAML の中身の TRACE | Step 52・58 |
| NFR1.11 | 静的解析の関門（`SQL_` など） | Step 21・43・65 |
| NFR2.1・NFR2.2・NFR2.4・NFR2.5・NFR2.10 | 規模・時間の目標・k6 の判定の決まり | 台本 Step 16・17・39・61、流すのは Performance Validation |
| NFR2.3 | 解決の口の問い合わせの数と時間 | Step 34（数）、Performance Validation（時間） |
| NFR2.6 | 一覧・読み取りの問い合わせの数 | Step 11・36 |
| NFR2.7・NFR2.11 | 接続プールの見積もり・1要求の本数 | Step 13・36・58（本数）、Step 39（台本） |
| NFR2.8 | 件数の上限を置かない | Step 39（`roleAssignmentsRead`）、Performance Validation |
| NFR2.9 | YAML の大きさの上限と書き出しのヘッダー | Step 57・58・62 |
| NFR3.1 | 同時の重なり (a)〜(g) | (b)(c) Step 13、(a)(d)(e)(g) Step 36、(f) Step 56 |
| NFR3.2 | import の一括確定 | Step 56 |
| NFR3.3 | `ROLE_BUSY` | Step 13・36・56 |
| NFR3.4 | 違反の後の2つ目のトランザクション | Step 11・13・34 |
| NFR3.5 | 監査の書き込みの失敗 | Step 13・36 |
| NFR3.6 | 移行（表を足すだけ、前の版との互換） | Step 4・5・27・28 |
| NFR3.7 | 捨ての試しの結果を本物で確かめる | 10.3 |
| NFR3.8 | import の判定の順 | Step 56 |
| NFR5.1・NFR5.6 | 監査の種類・列・線引き | Step 7・13・36・58 |
| NFR5.2 | 指標 | Step 13・36 |
| NFR5.3 | `ROLE_BUSY` の WARN | Step 13・58 |
| NFR5.4 | 警報と SLO | Observability Setup（`monitoring-design.md` 5節） |
| NFR5.5 | detail の数え方 | Step 7・50 |
| NFR6.1 | 必須のテスト | 全体 |
| NFR6.2 | 性質ベースのテスト4つ | Step 7・30・52 |
| NFR6.4 | カバレッジ | Step 3・21・26・43・48・65 |
| NFR6.5 | 境界テスト | Step 14・37・59 |
| NFR6.6 | 依存を足さない | Step 19・41・63 |

### 10.3 捨ての試しの結果を本物のテストで確かめる所（NFR3.7）

| 試し | 本物のテスト（手順） |
|---|---|
| U1 YAML のキーは型によらず文字列、`1` と `'1'` は重複、値の真偽の読み | `RoleTransferReaderTest`・`RoleTransferRoundTripPropertyTest`・`RoleTransferLimitsApiIT` の重複キー（Step 52・58） |
| U2 コレクションの別名 0 は拒否、スカラーの別名は受け付け | `RoleTransferLimitsApiIT`（Step 58） |
| U3・U4 上限に近い YAML の確かめと適用の時間、バッチ 1,000 件、文の最大 | 本物の時間は Performance Validation の `roleTransferLarge`。節の数が上限に近い短い名前の形を Build and Test で1回測る（`reliability-design.md` 1.3）。バッチと巻き戻しは `RoleStoreTransferBatchIT`・`RoleTransferAtomicityIT`（Step 54・56） |
| U5 `snapshotFor`・`resolve` の時間 | 問い合わせの数は `EffectivePermissionQueryCountIT`（Step 34）、時間は Performance Validation の `myPermissionsTree`・`workRoleSwitch` |
| group の T1 同じ名前の同時の作成（待たない違反・上限切れ） | `RoleConflictAuditIT` #2・`RoleBusyApiIT` #3（Step 13）、`RoleStoreConstraintIT`（Step 9） |
| group の T3 主キーの違反と待ち | `RoleStoreKeyGuardIT` #6（Step 32） |
| group の T4 行の上限 3 秒、外部キーは親の排他を待たない | `RoleBusyApiIT` #5、`RoleConcurrencyIT` #1（Step 36） |
| group の T5 違反の後の2つ目のトランザクション | `RoleStoreTransactionsIT`・`RoleConflictAuditIT`（Step 11・13） |
| group の T6 例外の文に行の値が入る | `RoleUniqueViolationSecretLeakIT`・`RoleBusyLogTraceIT`・`RoleStoreClassificationTest`（Step 9・13） |
| group の T7 例外の型での区分 | `RoleStoreClassificationTest`（Step 9・32） |
| #9 作業ロールの保存の `MERGE`（未確認） | `WorkRoleSelectionMergeIT`（Step 28）・`WorkRoleConcurrencyIT` #9（Step 36） |

## 11. テストの量（Standard）

| 部品 | 単体 | 結合 |
|---|---|---|
| `RoleName`・対象の名前 | `RoleNameTest` 8〜12 件、`RoleNameKeyPropertyTest` 4〜5 性質、`PermissionTargetNameTest` 5 件 | — |
| 継承・木・作業ロール・写し | `PermissionInheritanceTest` 6〜8 件と性質 4、`PermissionTreeBuilderTest` 6〜8 件、`WorkRoleChooserTest` 5 件と性質 4、`PermissionSnapshotTest` 4 件 | — |
| 出来事・detail・code | 各 3〜6 件 | — |
| `role.store` | `RoleStoreClassificationTest` 8〜10 件 | `RoleStoreConstraintIT` 5 件前後、`RoleStoreKeyGuardIT` 2 件、`WorkRoleSelectionMergeIT` 2〜3 件、`RoleStoreTransferBatchIT` 4 件前後 |
| `role.repository` | — | `RoleRepositoryIT` 5〜6 件、`RoleAssignmentRepositoryIT` 5〜6 件 |
| `role.service` | `RoleAdminServiceTest` 10〜14 件、`RoleAssignmentServiceTest` 8〜10 件、`WorkRoleServiceTest` 6〜8 件、`EffectivePermissionResolverImplTest` 5〜6 件、`RoleTransferServiceTest` 8〜10 件、`RoleTransferSlotTest` 3 件、`RoleGroupDeletionGuardTest` 3 件 | `RoleStoreTransactionsIT` 2〜3 件、`EffectivePermissionConsistencyIT` 8 組、`EffectivePermissionQueryCountIT` 4〜6 件、`RoleGroupDeletionGuardIT` 2 件、`RoleDeleteCleanupIT` 2 件、`RoleAdminQueryCountIT` 3〜4 件、`RoleTransferAtomicityIT` 1〜2 件、`RoleTransferStaleIT` 5 件、`RoleTransferBusyIT` 3 件 |
| `role.transfer` | `RoleTransferReaderTest` 13〜16 件、`RoleTransferWriterTest` 6〜8 件、`TransferPlannerTest` 6〜8 件、`TransferFingerprintTest` 4〜5 件、`RoleTransferRoundTripPropertyTest` 2〜3 性質、`LargeRoleTransferYamlTest` 2〜3 件 | — |
| `role.web`（結合） | — | 機能の IT 各 6〜10 件、認可の表（管理の口 24 × 4 の主体、`/api/me` の口 4 × 3）、`RoleGrantsNoAdminAccessIT` は ADMIN の口の数だけ、同時の重なり 14 行の表のうち API の 11 件、ログ・監査・指標・接続・漏えいの各 IT 2〜5 件、`RoleTransferLimitsApiIT` 12〜14 件、`RoleTransferTomcatSwallowIT` 3 件 |
| `audit`（足す分） | 単体 3 クラス 4〜8 件ずつ（Bolt ごとに足す） | `RoleAuditIT`・`RoleAuditWriteFailureIT`・`WorkRoleSwitchAuditIT`・`RoleTransferAuditIT` 各 2〜6 件 |
| 移行 | — | `RoleSchemaIT` 4〜6 件、`RoleMigrationCompatibilityIT` 3〜4 件（Q6） |
| 境界 | `RoleBoundaryArchitectureTest` 10 規則前後、`UserBoundaryArchitectureTest` の1か所 | — |

E2E は足さない（画面は U6・U7。`team.md` の本数の数え方で B4〜B6 は 0 本）。

## 12. この計画で決めたこと・承認済みの文書との差

承認済みの設計の文書は書き換えず、差をここと `code-summary.md` に記録する（`project.md` の決まり）。

| ID | 決めたこと | 理由・差 |
|---|---|---|
| D-1 | 移行は B4 の `V11__u4_role.sql`、B5 の `V12__u4_role_assignment.sql`（Step 3・26 で次の空き番号を確かめる）。制約・索引の名前は 4.5 のとおり。設定の表に検査の制約を4つ置く | NFR3.6、`entities.md` の表の制約。区分は制約の名前で読み替え先を決める |
| D-2 | JPQL のエンティティ名は `Role`・`PermissionSetting`・`UserRoleAssignment`・`GroupRoleAssignment`・`WorkRoleSelection` | 予約語に当たらない見込み。動かなければ止めて諮る（6節 A4） |
| D-3 | `role.repository` は `org.springframework.data.repository.Repository` を継ぎ、読み取りの `@Query` だけを置く | 書き込みの方法を置かない決まり（`security-design.md` 4.1）を継ぐ方法でも守る（group の D-4） |
| D-4 | 1つ目のトランザクションは `RoleStoreTransactions.inFirst(Function<RoleStore, RoleFirstStep<T>>)` にし、結果は `RoleFirstStep`（`Done`・`Rejected`・`Store`） | 承認済みの `reliability-design.md` 2.4 の断片は `first(Function<RoleStore, RoleStoreOutcome<T>>)` で、業務の拒否を `Done` の値で返す形。13節 Q2: A で、group の実装（`FirstStep`）にそろえた差 |
| D-5 | `RoleStoreOutcome`・`RoleFirstStep`・結果の型・`PermissionSnapshot`・受け渡しの型の toString は、種類・ID・件数だけを出す | NFR 設計の読み直しの R-13。TraceAspect の対象の `role.service` で文字列にされるため |
| D-6 | `Referenced` は制約の名前から相手（`ROLE`・`USER`・`GROUP`）を持つ。外部キーの SQLState は 23503 と 23506、主キーの違反は制約の名前か表の名前で見分ける | H2 2.4 の振る舞い（group の G-7・G-8）。`reliability-design.md` 2.3 は 23503 だけ |
| D-7 | 想定外の DB の例外は `RoleStoreUnexpectedException` に包み直し、文には SQLState と「role の既知の制約の名前の一覧に当たった名前」だけを入れる（H2 の文そのもの・値は入れない）。`common` は変えない（13節 Q5: A） | `security-design.md` 4.1 の「`GlobalExceptionHandler` の ERROR が SQLState・制約の名前をキーで出す」との差（ERROR の文とスタックトレースで見える形）。group の実装（クラスの名前だけ）とも違う |
| D-8 | 問い合わせの数は既存の `auth/testsupport/SqlStatementCounter` で数える | 設計の「Hibernate の統計」との差。既存の数えのテストと同じ道具（group の D-8） |
| D-9 | `RoleConnectionUsageIT` は `group/testsupport/ConnectionHoldRecorder` を使い回す | 同じ数え方（スレッドごとの同時の本数の最大。group の G-17） |
| D-10 | 監査の書き込みの失敗の ERROR に `targetRoleId`・`targetUserId`・`targetGroupId`・`detail` を値があるときだけ載せる | 既存の `containsOnlyKeys` を変えないため（group の D-10） |
| D-11 | detail は全件の JSON を作ってから長さを見て、16,384 を超えれば要約にする。要約の先頭の件数は「最大 20 件（権限の保存）・最大 50 件（import）のうち、上限に収まる件数」にする | `observability-design.md` 1節は先頭 20 件・50 件と書く。名前が長い（128 コードポイントの3階層）と 20 件でも上限を超えうるため、収まるまで減らす（切らない決まりは守る）。承認の場で確かめる |
| D-12 | jqwik のテストの名前は `PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest` | `cicd-pipeline.md` 11節（承認済み）。`tech-stack-decisions.md` NFR6.2・`logical-components.md` 5節の `*Properties` との差 |
| D-13 | 0 以下の ID は存在しない ID として 404（変える操作は監査に FAILURE） | NFR1.10。機能設計の BR2.4（0 以下は 400）との差 |
| D-14 | 木の名前は `@RequestParam` で受け、欠け・空は 400、長さで拒否しない | BR4.12（契約 C7・C8 の道の形との差、機能設計 8節） |
| D-15 | 有効な作業ロールを決める4回の読み取りのうち、直接の割り当てとグループの割り当ての読み取りはロールの名前を結合して読む。写しと祖先の行は行の値だけの射影 | `WorkRoleRef` の名前のために5回目の読み取りを足さない（NFR2.3 の 4 回以内）。`performance-design.md` 4節 |
| D-16 | `resolve` は写しを使わず、決める読み取り4回と祖先の行1回で答える | 承認済みの NFR2.3（契約 C5・機能設計 2.9 との差。`tech-stack-decisions.md` に記録済み）のとおり |
| D-17 | B4 の認可の表の 403 の主体は「管理者の印を持たない利用者」。B5 で 13節 Q3: A のとおり、全スキーマを FULL のロールを作業ロールに持ち管理者の印だけを欠く利用者に置き換える | `security-design.md` 2節の B4 と B5 の差のまま |
| D-18 | group の `StoreFailureClassifier` に `FK_GROUP_ROLE_ASSIGNMENTS_GROUP` を足し、`GROUP_IN_USE` に読み替える（13節 Q4: A） | group の本体の1か所に手が入る（承認済みの role の設計に無い。`group.store` は一覧に無く作業は付かない） |
| D-19 | 作業ロールの保存は、Step 28 の確かめで `MERGE INTO … KEY(user_id)` が上限切れか 23505 になれば `MERGE` のまま、ほかの誤りなら「行の有無を読んでから更新か挿入」にする。どちらも主キーの違反と上限切れは `Busy("WORK_ROLE_SELECTION_KEY")` | NFR 設計の読み直しの R-15 |
| D-20 | 入口（`RoleTransferSlot`）は `role.service` に置き、本文を読む前に `tryAcquire()`、`finally` で放す。取り損ねの WARN は role の専用の出し方（`lockKind=ROLE_TRANSFER_SLOT` と決まった文だけ） | NFR 設計の読み直しの R-12（`RowLockFailures.warn` は例外が要る） |
| D-21 | 大きさの上限は設定値だけの record `RoleTransferLimitProperties`（`mastersmith.role-transfer.max-bytes`、既定 10,485,760）で持ち、`RequestBodyLimitRoute` と `SafeYamlLimits` に同じ値を渡す。本番の `application.yaml` には書かない（既定の値のまま） | `RoleTransferExportLimitIT` で上限を小さくするため（NFR2.9 の確かめ方）。計測の除外は既存の `**/*Properties.class` の内で、除外を広げない |
| D-22 | 上限ちょうどの YAML の生成の部品は、ほかのクラスに依存しない Java の1ファイル `role/testsupport/LargeRoleTransferYaml.java`（`main` つき）にし、テストと負荷の試験の両方で使う（13節 Q7: A） | `performance-design.md` 2.3 の「テストと k6 で同じ部品」の作り方が決まっていない |
| D-23 | op のタグの確かめは、リポジトリの外の要求を送らない最小の台本を `grafana/k6:2.3.0` のコンテナで流して行う | `performance-design.md` 2.2 の「短い試し走り」。アプリを起動せずに、タグが `iteration_duration` に付くかだけを見る |
| D-24 | Tomcat の読み捨ての確かめは、生のソケットで本文を書き切ってから応答を読む | 基盤の読み直しの R-01（送り手の癖で落ちない、決定的な形） |
| D-25 | 適用の途中の失敗は、テストだけで内部DB に置く H2 のトリガーで、目印の行の書き込みで起こす | NFR3.2 の「テストだけの部品で N 件目の書き込みで失敗」。本番のコードに失敗の口を足さない |
| D-26 | 負荷の試験の準備は対象DB を要らない | 6節 A20（基盤の読み直しの R-07） |
| D-27 | role の移行は `users.admin_flag` から行を移さない。`team.md` の「行を移す移行には、前の版が使う列が一貫して読める結合テスト」は当たらず、戻しの確かめは配備の段（管理の可否は `admin_flag` のまま） | 基盤の読み直しの R-04 |
| D-28 | AC2.2.14 は `RoleGrantsNoAdminAccessIT` で、本番の ADMIN の口をすべて `RequestMappingHandlerMapping` から集めて確かめる | 口の名前を手で並べると、後で足した口を落とす |
| D-29 | コミットは各 Bolt で場所ごとの3つ（3節） | 13節 Q8: A |
| D-30 | `RoleName` は group の `GroupName` と同じ規則（制御文字は Cc と U+2028・U+2029、Cf は拒否しない）を role の中に持つ（group.domain の `GroupName` を使わない） | group の G-4（依頼者が受け入れた範囲）にそろえる。名前の型は機能ごとに持つ |
| D-31 | `application.yaml` の `org.hibernate.orm.jdbc.error: OFF` のコメントに role の理由を足す（B4） | `security-design.md` 4.1（group の読み直しの R-09 の role の分） |
| D-32 | 要求の文脈（主体・要求の元）の読み取りは `role.web.RoleRequestContextResolver` に持つ | group の G-12 と同じ（role は group.web・useradmin に依存しない） |
| D-33 | B4・B5 の設定・割り当ての書き込みは JPA のエンティティで行い、JDBC のバッチは B6 の import だけ | `performance-design.md` 4節（JDBC は import の書き込みだけ） |
| D-34 | 認可の表の「停止中の管理者」の期待は 401 `AUTHENTICATION_REQUIRED` | 既存のアクセストークンの認証の入口が停止中の利用者を断るため（group の G-14、依頼者が受け入れた読み） |
| D-35 | `RoleBoundaryArchitectureTest` の B3 の規則は、承認済みの NFR6.5 の形に広げる（role の自分の境界テスト） | B3 の計画 11節 Q1: A の「B4 以降は role の Bolt が規則を足す」。既存のほかの境界テストは `UserBoundaryArchitectureTest` の1か所（Q1）だけ |
| D-36 | `DSL_NOT_APPLIED` は `role.domain` に定義する | 今のアプリに同じ code は無い。U5 navigation が使うときは role の定義を使い回す（BR13.2） |
| D-37 | B4 のロールの削除では `ROLE_IN_USE` は起きない（割り当ての表が無い）。拒否の経路とテストは B5 | `bolt-plan.md` の B4 |
| D-38 | 展開後の節の上限の境界は、流れの書き方の並びで節を作って確かめる（ちょうどの方は節の上限に当たらず中身の誤りになることで受け付けを確かめる） | 10 MiB の内で 1,000,001 の節を作るため（対応表の形では大きさの上限が先に来る） |

## 13. 依頼者の答え（計画の承認の前に確かめたこと）

依頼者は9つの問いにすべて A と答えた（指揮役から伝達）。計画（4〜12節）と `unit-test-instructions.md` はこの答えで確定した形にしてある。

| 問い | 背景 | 選択肢（推奨は A） | 依頼者の答え | 反映先 |
|---|---|---|---|---|
| Q1 既存の境界テスト `UserBoundaryArchitectureTest` を緩める | role は B4 で `user.domain.RequestOrigin`（出来事・要求の元）、B5 で `user.service`（利用者の有無・伏せ字の要約）を使う。「user に依存してよい機能」に role が無いため、B4 の C1 で落ちる。`team.md` は既存の境界テストを緩めるときに計画に明記して承認を得るとしている（B3 の group は同じ形で承認された） | **A**. 一覧に `role` だけを足す。ほかの規則（user が依存してよい先、common が user に依存しない、規則が依存を見分けている確かめ）は緩めない。B. role に自前の要求の元の型を持たせ、B4 では user に依存しない（B5 で `user.service` が要るため、B5 で同じ承認が要る。型が重なる）。X. Other | **A**. 一覧に `role` だけを足す | 5節、Step 6・14、D-35 |
| Q2 業務の拒否のトランザクションの形（NFR 設計の読み直しの R-14） | 承認済みの `reliability-design.md` 2.4 は「書き込みの前の業務の拒否は1つ目の中で失敗の出来事を出して確定」とする。group の実装（B3）は、拒否も巻き戻して2つ目のトランザクションで失敗の出来事を出す形（`FirstStep.Rejected`）で、互いに「同じ形」と書いていて食い違う | **A**. group の実装と同じ形にする（`RoleFirstStep`。`Done` 以外はすべて巻き戻し、失敗の出来事は2つ目）。巻き戻しの印を付ける場所が1つになり、group と同じテストの形を使える。承認済みの 2.4 との差として記録する（D-4）。B. 承認済みの 2.4 のまま（拒否は `Done` の値で返し、1つ目の中で出して確定）。group と形が分かれる。X. Other | **A**. group の実装にそろえる（`RoleFirstStep`） | 4.3、Step 10・11、D-4 |
| Q3 403 を確かめる利用者の書き換え（group の読み直しの R-04） | `team.md` は「403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者」とする。B3 の group の7つの口の表（28 行）と B4 の role の表は、管理者の印もロールも無い利用者で 403 を確かめている（作業ロールを持てなかったため） | **A**. B5 で、role の管理の口・group の7つの口の表の 403 の主体を「全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールに持ち、管理者の印だけを欠く利用者」に**置き換える**。既存の管理の口すべては `RoleGrantsNoAdminAccessIT` で同じ利用者で確かめる。`ApiAccessConsistencyIT` の評価の主体（ロールの無い利用者）は認可の評価の仕組みの確かめのため変えない。B. 置き換えずに、その利用者の行を**足す**（印もロールも無い利用者の行も残す）。行が増えるが、B3 の承認済みのテストの行は変わらない。X. Other | **A**. 置き換える | Step 36、8.3、D-17 |
| Q4 group の外部キーの区分に role の外部キーを足すか | B5 で `group_role_assignments` から `groups` への外部キー（削除の制限）ができる。group の削除は問う口とグループの行の排他で守られ、API の経路では外部キーの違反に届かない。届いた場合（最後の守りが働いた場合）、group の `StoreFailureClassifier` は `fk_group_members_group` だけを読み替えるため、`UNEXPECTED`（500）になる | **A**. B5 で group の `StoreFailureClassifier` に `FK_GROUP_ROLE_ASSIGNMENTS_GROUP` を足し、`GROUP_IN_USE`（残りの数を数え直す既存の形）にする。group の本体の1か所とテスト2件が増える（`group.store` は一覧に無く、作業は付かない）。B. 足さずに受け入れる（届かない最後の守りのため 500 のまま。`code-summary.md` の「後に回すこと」に書く）。X. Other | **A**. 足す | 8.2、Step 33・34、D-18 |
| Q5 想定外の DB の例外の手がかり（`security-design.md` 4.1 と group の実装の食い違い） | 承認済みの role の設計は、包み直した例外の SQLState と制約の名前を `GlobalExceptionHandler` の ERROR にキーで出すとする（group の読み直しの R-06 の手当て）。group の実装は、承認どおりクラスの名前だけ（SQLState・制約の名前を出さない）。`GlobalExceptionHandler` は `common.error.web` にあり、想定外の 5xx は `setCause` つきの ERROR で例外の文とスタックトレースを出す | **A**. `common` は変えず、role の包み直した例外の文に、SQLState と「role の既知の制約の名前の一覧に当たった名前」だけを入れる（H2 の文から取った名前は一覧と照らすだけで、文そのものは入れない。値は入らない）。ERROR の文とスタックトレースで手がかりが見える。承認済みの「キーで出す」との差を記録する。B. `GlobalExceptionHandler` に、role の包み直した例外のときだけ `sqlState`・`constraint` のキーを足す（`common.error.web` は一覧に無く作業は付かないが、common が role の型を知らない形にする工夫が要る）。C. group と同じくクラスの名前だけ（承認済みの設計との差を記録する）。X. Other | **A**. 例外の文に SQLState と既知の制約の名前だけ（`common` は変えない） | 7.2（`RoleStoreUnexpectedException`）、Step 8・9、D-7 |
| Q6 前の版との互換を B4・B5 でどこまで確かめるか | `team.md` は DB スキーマの変更で1つ前の版が動く後方互換を求める。承認済みの基盤の設計は、前の版のイメージでの起動を配備の段の戻しの練習とする。B3 は `AuditMigrationCompatibilityIT`（JDBC と Flyway の水準）を足した（group の Q4: A） | **A**. B4・B5 で `RoleMigrationCompatibilityIT` を足す（V1〜V10 だけを読ませた前の版の Flyway が V11・V12 まで当てた内部DB で止まらない、`users.admin_flag` が残り値が変わらない、前の版の列だけで監査の行を足せる）。前の版のイメージでの起動は配備の段に残す。B. 既存の Flyway の検証だけにし、前の版との互換は配備の段に任せる。X. Other | **A**. B4・B5 で `RoleMigrationCompatibilityIT` を足す | 4.5、Step 5・28 |
| Q7 上限ちょうどの YAML の生成の部品の作り方 | 承認済みの `performance-design.md` 2.3 は「テストと k6 で同じ部品を使う」とする。テストは Java、負荷の試験の準備は `perf/` の台本で、作り方が決まっていない | **A**. テストの手伝いに、プロジェクトのほかのクラスに依存しない Java の1ファイル（`role/testsupport/LargeRoleTransferYaml.java`、`main` つき）を置き、テストからは方法として、負荷の試験からは `java <このファイル> …`（1ファイルのソースの起動）で呼ぶ。ビルドの設定は変えない。B. `perf/` に node の台本（`make-role-transfer.mjs`）と、テストの Java の手伝いを別に作り、同じ引数で同じバイト数になることを README の手順で確かめる。実装が2つになる。X. Other | **A**. Java の1ファイルを両方で使う | 9.2、Step 51・61、D-22 |
| Q8 コミットの区切り | 前の Bolt では手順ごとの区切りが型の追従でビルドの通らない区切りになった（B3 は4つを3つに合わせた）。import の向きを確かめて、各 Bolt で場所ごとの3つにした（3節） | **A**. 各 Bolt で3つ（C1 データの形・ドメイン・audit・DB アクセス／C2 業務処理・API・境界／C3 負荷の台本と文書）。どの時点でもコンパイルとそれまでのテストが通る。B. 各 Bolt で1つ（squash の前に作業ブランチの上でも1つにまとめる）。X. Other | **A**. 各 Bolt で3つ | 3節、Step 23・45・67、D-29 |
| Q9 Should の受け入れ基準を各 Bolt に入れるか | `bolt-plan.md` は Should（AC1.2.7 今の DSL に無い設定を消す・AC1.2.16 木の `inMenu`・US4.2 自分の権限の API）を Bolt の中に入れ、時間が足りなければ計画の承認で諮るとする。計画の見積もりでは B4・B5 に収まる | **A**. B4 に AC1.2.7・AC1.2.16、B5 に US4.2 を入れる（計画のとおり）。B. 外して後の Intent に回す（U6・U7 の画面の該当の部分も外れる）。X. Other | **A**. 入れる | 7.1・8.1 |

### 計画の承認の場で確かめる点

- **D-11**: 監査の detail の要約に入れる件数は「最大 20 件（権限の保存）・最大 50 件（import）のうち、16,384 文字の上限に収まる件数」にし、収まるまで減らす（承認済みの `observability-design.md` 1節の「先頭 20 件・50 件」との差。途中で切らない決まりは守る）。
- **D-12**: jqwik のテストの名前は `*PropertyTest`（`PermissionInheritancePropertyTest`・`RoleNameKeyPropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleTransferRoundTripPropertyTest`）。承認済みの `tech-stack-decisions.md` NFR6.2・`logical-components.md` 5節の `*Properties` との差（`cicd-pipeline.md` 11節のとおり）。

## Build and Test に引き継ぐこと

- `develop` での `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の流し直しと CI の確かめ（`team.md`）。`verify` の時間の延び（上限切れと 10 MiB のテスト）を Step 21・43・65 の値と並べる。
- カバレッジの実測の値（新しい `role.*` と、手を入れた `audit.domain`・`audit.service`、`group.store`）。
- 節の数が上限（1,000,000）に近い短い名前の YAML の確かめと適用の時間を1回測る（`reliability-design.md` 1.3、上限ちょうどの YAML の生成の部品を使う）。
- 要件の網羅は FR・NFR → 機能設計の BR と NFR 要件の枝番 → この段の `traceability.json` の2段の連鎖でたどる（`project.md` の学び）。Deferred は U5・U6・U7。

## Performance Validation に引き継ぐこと

- 準備の場面を1回流してから、`roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps`・`workRoleSwitch`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`myPermissionsTree`・`roleExport` を使い捨ての環境で流し、場面ごと（操作が2つ以上の場面は op ごと）に `iteration_duration` の p95 < 1000 ms と `checks` の率 1 で判定する（NFR2.2・NFR2.3・NFR2.5）。`http_req_duration` は並べて記録する。`roleTransferLarge`（確かめ 15 秒・適用 30 秒、`iteration_duration` の最大、確かめ・適用の時のヒープの最大を記録）は最後に流す。
- `rolePoolLimit` の合否の回（上限 11・書き込み 5 VU・2 分: 時間切れの累計 0・500 が 0 件・`acquire` の最大 20 ms 未満）と記録の回（上限 10・20 VU・2 分、`acquire` 10 ms 以上が届いた証拠、40 VU の流し直しは1回まで）。`acquire` は `baseUnit` を確かめて秒にそろえる（NFR2.7）。
- 台本全体を `caffeinate -i` で包み、配備したアプリを止めて流す。手順は `perf/README.md` の role の節。

## Observability Setup に引き継ぐこと

- ダッシュボードの区画に role の `uri` を足す（`monitoring-design.md` 5節。B4〜B6 は `docker/monitoring/` に触れない）。
- 既存の p95 の警報が role の API を拾わないこと（`monitoring-design.md` 2節）と SLO の `Unverified` の扱い（NFR5.4）。`ROLE_BUSY` の WARN の `lockKind` の一覧（`ROLE_TRANSFER_SLOT` を含む）。

## 配備の段へ引き継ぐこと

- U3・U4 の移行（V10〜V12）を合わせて、入れ替えの前の内部DB の複写（バックアップを兼ねる）と、前の版のイメージを移行の後の内部DB の複写と一時のボリュームで起動して `/actuator/health` の 200 を待つ戻しの練習を1回行う（`infrastructure-specification.md` 4節）。前の版が Flyway の検証で止まらないこと（V10〜V12 は future として無視される）を合格の条件に入れる。
- 戻したときの権限の確かめの対象は、戻した版で管理の API の 403／200 が `users.admin_flag` のとおりであること。データの権限（作業ロール）は前の版に無い（基盤の読み直しの R-04）。
- 戻した版の DSL の状態（前の版は版 2 の DSL を読めず、DSL が無い状態で起動する。U2 の引き継ぎ）を、role の権限の設定が名前で残ることとあわせて確かめる（基盤の読み直しの R-03）。
- 前の版が監査の行を実体として読む経路が無いことの再確認（group の引き継ぎ）に、role の種類（`ROLE_*`・`WORK_ROLE_SWITCHED`・`ROLE_TRANSFER_APPLIED`）の行も含める。
- スモークテストでロールの操作を行うときは監査に残るため、行う前に依頼者に伝え、期待の件数を合わせてから数える（`project.md` の学び）。

## ほかの単位（U5・U6・U7）へ引き継ぐこと

- **U5 navigation（B7）**: 解決の口 `EffectivePermissionResolver` は `role.service` にある。業務のメニューは `snapshotFor` を1回、テーブルの置き場は `resolve` を1回でよい（`logical-components.md` 4節・6節）。写しは `workRole`（ID と名前）と `dslHash` を持つ。`DSL_NOT_APPLIED` は `role.domain` の定義を使い回す。`RoleBoundaryArchitectureTest` は「role に依存してよい機能」に `navigation`（`role.service` だけ）を B4 で入れておく。
- **U6 role-admin-ui（B8）**: 木の名前は問い合わせの引数でエンコードして送る。`ROLE_BUSY`・`GROUP_BUSY` は「ほかの操作と重なった。もう一度」、確かめ・適用の入口の `ROLE_BUSY` は「ほかの読み込みが動いている。少し待ってからもう一度」。書き出しの応答に `X-Role-Transfer-Exceeds-Import-Limit: true` があれば分けて読み込む案内。確かめは最大 15 秒、適用は最大 30 秒。0 以下の ID は 404。
- **U7 app-frame-ui（B9）**: `GET /api/me/work-role` の `roles` は ID の順で、先頭が「最初のロール」。同じロールへの切り替えは 204（読み替え中なら保存が書かれる）。`ROLE_NOT_ASSIGNED` のときは作業ロールを読み直す。

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
