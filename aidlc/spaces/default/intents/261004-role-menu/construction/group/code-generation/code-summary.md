# コードの要約（Code Summary）— U3 グループ（group、Bolt B3）

コミットは依頼者の承認を得て3つにした（524e0f9 user・useradmin、5022030 移行 V10・監査・group・role・境界テスト、153d181 負荷の台本と README）。計画 3節の区切りは4つ（C1〜C4）だったが、監査のクラス（`AuditEventFactory`・`AuditDetailJson`・`AuditEventListener`）が group の型を使うため C2 だけではビルドが通らず、依頼者の決定で C2 と C3 を合わせた。どのコミットの時点でもビルドが通る。develop へは squash で1コミットになる。

計画: `code-generation-plan.md`（同じディレクトリ、11節の Q1〜Q5 はすべて A）。Step ごとのコマンドと結果は `generation-notes.md`。方法は test-after（Testing Contract のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流した）。作業ブランチは `feature/261004-role-menu-b3`（`develop` の 757d5ed から）。

## 1. 作ったもの・変えたもの

アプリのソースの道の一覧は `source-manifest.json`。

### 1.1 バックエンドの本体

| 場所 | 種類 | 中身 |
|---|---|---|
| `user/domain/UserProblemTypes`・`useradmin/domain/UserAdminProblemTypes`・`useradmin/web/UserAdminController` | 変える（C1） | `USER_NOT_FOUND` を user へ移した（code・状態コード・文言は変えない。useradmin の `of(USER_NOT_FOUND)` は user の定義を返す） |
| `user/service/UserAccountService` | 変える（C1） | `findSummariesByIds(Set<Long>)`（既存の `findAllById` の1回の読み取り、利用者 ID の昇順、toString で伏せる `UserAdminSummary`） |
| `resources/db/migration/V10__u3_group.sql` | 足す（C2） | `groups`・`group_members`（`uk_groups_name_key`・`pk_group_members`・`fk_group_members_group`（削除の制限）・`fk_group_members_user`・`ix_group_members_user_id`）、`audit_events` に `target_role_id`・`target_group_id`・`detail`（16,384） |
| `audit/domain/`（`AuditEvent`・`AuditEventType`・`AuditFailureReason`・`AuditEventFactory`、`AuditDetailJson` を足す） | 変える・足す（C2・C3） | 3列と `withRoleGroupTarget`（detail は 16,384 を超えたら断り、切り詰めない。toString は長さだけ）、グループの5つの種類と3つの理由、グループの出来事からの写し方、detail を決めたキーだけの JSON にする部品 |
| `audit/service/AuditEventListener` | 変える（C3） | `onGroupAuditEvent`（確定の後、`fallbackExecution = true`）。書き込みの失敗の ERROR に `targetRoleId`・`targetGroupId`・`detail` を値があるときだけ足す |
| `group/domain/` | 足す（C3） | `Group`（エンティティ名 `UserGroup`、表 `groups`）・`GroupMembership`(`Id`)・`GroupName`・`GroupNameValidation`・`GroupOperation`・`GroupAuditEvent`・`GroupAuditFailure`・`GroupAuditDetail`・`GroupProblemTypes`・`GroupMember`・`GroupRejection` |
| `group/repository/` | 足す（C3） | 読み取りだけ（Spring Data の `Repository` を継ぐ。書き込み・排他なし）。`GroupRepository`・`GroupMemberRepository` と投影の record |
| `group/store/` | 足す（C3） | TraceAspect の対象の外。排他（ヒント 3,000 ms）と書き込み・flush の `GroupStore`、区分 `StoreOutcome`・`StoreFailureClassifier`、想定外の例外をクラスの名前だけにする `GroupStoreUnexpectedException` |
| `group/service/` | 足す（C3） | `GroupAdminService`（7つの操作。1つ目のトランザクションは `GroupStoreTransactions.inFirst`、拒否と違反の読み替えは2つ目のトランザクションで失敗の出来事だけ）、`FirstStep`、待ち合わせの口 `GroupBarrier`・`NoOpGroupBarrier`、U4 に出す `GroupMembershipQuery`（`Impl`）・`GroupSummary`・`GroupRowLock`、問う口 `GroupDeletionGuard`・`DeletionDecision`、`GroupProblemTypeCatalog`、結果の型 |
| `group/web/` | 足す（C3） | `GroupAdminController`（`@ApiAccess(ApiAccessLevel.ADMIN)`、`/api/admin/groups` の下の7つの口）、要求・応答の record、`GroupFieldErrors`、`GroupRequestContextResolver` |
| `role/service/RoleGroupDeletionGuard` | 足す（C3） | B3 の仮の問う口（割り当ての数 0・`Allowed`）。`TODO(B5)` は1か所 |

### 1.2 バックエンドのテスト

- 新しいテストのクラス 37（単体と結合）と手伝い 7（`group/testsupport/`）。主なもの: `GroupNameTest`・`GroupNamePropertyTest`（jqwik）、`StoreFailureClassifierTest`・`GroupStoreConstraintIT`、`GroupRepositoryIT`、`GroupAdminServiceTest`・`GroupStoreTransactionsIT`・`GroupMembershipQueryIT`・`GroupMembershipQueryCountIT`・`GroupAdminListQueryCountIT`、API の `GroupAdminApiIT`・`GroupAdminAuthorizationApiIT`（28 行）・`GroupAdminMassAssignmentIT`・`GroupAdminIdorApiIT`・`GroupConcurrencyIT`・`GroupNameConflictIT`・`GroupBusyLogIT`・`GroupConflictAuditIT`・`GroupAdminMetricsIT`・`GroupConnectionUsageIT`・`GroupSecretLeakIT`・`GroupUniqueViolationSecretLeakIT`、監査の `GroupAuditIT`・`GroupAuditWriteFailureIT`・`AuditMigrationCompatibilityIT`、境界の `GroupBoundaryArchitectureTest`（6規則）・`RoleBoundaryArchitectureTest`（1規則）、`UserSummariesByIdsIT`。
- 書き換え: `UserProblemTypesTest`・`UserProblemTypeCatalogTest`・`UserAdminProblemTypesTest`・`UserAdminProblemTypeCatalogTest`（`USER_NOT_FOUND` の移し替え）、`AuditSchemaIT`（3列）・`AuditSecretLeakIT`（列の一覧とグループの行の確かめ）・`AuditRows`（読む列）。
- 既存の境界テストで変えたのは `UserBoundaryArchitectureTest` の「user に依存してよい機能」に `group` を足した1か所だけ（11節 Q2: A）。`ArchitectureTest` とほかの境界テストは変えていない。

### 1.3 負荷の道具と文書

- `perf/k6/scenarios.js`: 9つの場面（`groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`・`groupPoolLimit`）、`GROUP_SCENARIOS`、`setupTimeout = '10m'`、閾値、`setupGroups()`。
- `perf/README.md`: 「グループの管理の場面（Intent 261004-role-menu の U3）」の節。
- `README.md`: 「グループの管理の API（Intent 261004-role-menu の U3）」の節、「スキーマの変更（Flyway）」の V10、「監査ログ（U4）」のグループの種類と V10 の3列。
- 画面（`frontend/`）・`docker/`・`application.yaml`・ビルドの設定・依存は変えていない（Step 20）。

## 2. 実装で決めたこと

- 計画 10節の D-1〜D-22 はそのとおりに作った（手段を変えた D-9 は 4節の差）。承認済みの設計の文書は書き換えていない。
- 1つ目のトランザクションは、排他 → 待ち合わせの口 → 業務の判定 → 書き込みと flush → 成功の出来事の順。`Done` 以外は必ず巻き戻しの印を付ける。業務の拒否と違反の読み替えは、巻き戻した後の書き込みの無い2つ目のトランザクションで失敗の出来事だけを出す。`GROUP_BUSY` は出来事を出さない。
- 例外の区分の順は「上限切れ（`RowLockFailures.isLockFailure`）→ SQLState と制約の名前 → 想定外」。一意の違反（23505）は `uk_groups_name_key` で `NameTaken`、主キーで `AlreadyMember`。外部キー（23503・23506）は `fk_group_members_group` のときだけ `Referenced`。例外の文・制約の名前・SQLState は、区分の中で照らすだけで外へ出さない。
- 排他の種類（WARN の `lockKind`）は、行の排他と削除が `GROUP_ROW`、作成・名前の変更が `GROUP_NAME_KEY`、メンバーの追加と外しが `GROUP_MEMBER_KEY`。
- 待ち合わせの口の鍵は、作成と名前の変更が名前の鍵、メンバーの操作が `<グループの ID>:<利用者 ID>`、削除と排他の点がグループの ID。テストの `TestGroupBarrier` は最初に来た1件だけを止める（待ちの上限 20 秒）。

## 3. テストの量とカバレッジ

- 層ごとの実行（各 Step、`generation-notes.md`）と、Step 17 のバックエンドの全体（単体 201 クラス・1,734 件、結合 166 クラス・829 件、失敗 0・飛ばし 0）。変更の前（Step 3）は単体 186 クラス・1,646 件、結合 144 クラス・723 件。
- 性質ベースのテスト: `GroupNamePropertyTest`（jqwik、5 性質）。

### 3.1 1コマンドの検査（Step 22）

colima の環境変数を付けた `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の実測。1回目（11 分 44 秒）の後、依頼者の答え（`audit.service` の分岐を戻す）で単体テストを3件足し、流し直した（10 分 46 秒、通過）。下の値は流し直しのもの:

- バックエンドの単体 201 クラス・1,737 件（変更の前 1,646 件から +91）、結合 166 クラス・829 件（723 件から +106）。失敗 0・飛ばし 0。対象DB の3種類のテストの SKIPPED 0 件、「コンテナの実行環境」の警告 0 件。単体テストの結果に `GroupNamePropertyTest` がある。
- 画面 113 ファイル・1,043 件 passed（B3 は `frontend/` に触れていない）。

| パッケージ | 行 | 分岐 |
|---|---|---|
| `group.domain` | 97.0% | 89.7% |
| `group.repository` | 100.0% | 分岐なし |
| `group.store` | 97.8% | 94.6% |
| `group.service` | 95.7% | 80.4% |
| `group.web` | 96.6% | 87.5% |
| `role.service` | 100.0% | 100.0% |
| `audit.domain` | 99.6% → 99.7% | 98.4% → 96.8% |
| `audit.service` | 100.0% → 99.5% | 82.5% → 84.6% |
| `user.domain` | 99.6% → 99.6% | 98.0% → 98.0% |
| `user.service` | 99.7% → 99.7% | 95.7% → 95.8% |
| `useradmin.domain` | 100.0% → 100.0% | 100.0% → 100.0% |
| `useradmin.web` | 98.8% → 98.8% | 93.8% → 93.8% |
| 全体 | 98.7% → 98.5% | 94.6% → 93.9% |

（矢印の左は Step 3 の変更の前の値。新しいパッケージは右の値だけ）。`audit.service` は1回目の流しで行 91.3%・分岐 73.1% だった（3.2）。

### 3.2 `audit.service` の分岐を戻したテスト（依頼者の答え A）

- 1回目の verify で通っていなかった、今回の変更で増えた分岐は、`AuditEventListener` のグループの出来事の組み立てに失敗したときの項目（`fields(GroupAuditEvent)`。出来事が無い・成功か失敗か・対象の利用者の有無）と、`roleGroupFields` の `targetRoleId` の有無だった。
- `AuditGroupEventListenerTest` に3件を足した: 出来事が無いとき（追記せず、空の項目で ERROR を1回）、detail が 16,384 文字を超えるメンバーの外しの成功（組み立てが断られ、出来事の種類・結果・操作した人・対象の利用者・対象のグループで ERROR を1回）、detail が長すぎる削除の拒否（失敗の理由と対象のグループ、対象の利用者なし）。本体のコードとカバレッジの除外は変えていない。
- 結果は行 91.3% → 99.5%（206/207）、分岐 73.1% → 84.6%（44/52）。残る分岐 8 のうち 7 は変更の前からの既存の出来事の「出来事が無い」の枝、1 は `targetRoleId` が値を持つ枝（B3 では出来事の送り手が無く通らない。U4 に引き継ぐ。8節）。

### 3.3 E2E（Step 23）

Mailpit はすでに動いていた（起動・停止・作り直しはしていない）。`caffeinate -i ./gradlew e2eTest`（08:52:26〜08:59:50、Playwright 7.3 分）で 14 ファイル・177 件 passed（skipped 0・unexpected 0・flaky 0、110・120・130 を含む）。報告の確かめの道具の出力は「E2E の報告に残してはならない値は含まれていません（値の種類 7・確かめたファイル: json の報告 1・json の報告の添付 299・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。終わった後に `frontend/playwright-report`（作られていない）・`frontend/test-results` を消した。3.2 のテストの追加はバックエンドの単体テストだけのため、E2E は流し直していない（指揮役の指示）。

## 4. 計画との差

承認済みの設計の文書は書き換えず、差をここに記録する（`project.md` の決まり）。

| # | Step | 差 | 理由・扱い |
|---|---|---|---|
| G-1 | 5 | user と useradmin の2つの一覧を合わせた登録の確かめを `UserAdminProblemTypeCatalogTest` に置いた | user のテストから useradmin を参照しないため（useradmin → user の向きは既存のとおり） |
| G-2 | 6 | `AuditEventFactory.resultOf`・`AuditEventListener.isDslOperation` の網羅の `switch` にグループの種類を足した | 列挙に値を足すとコンパイルで必ず要る追従（計画 4.3 の表に無い）。振る舞いは利用者の管理の種類と同じ扱い |
| G-3 | 7 | `AuditRows` に3列と detail のほか `actor_user_id`・`target_user_id` も足した。`AuditMigrationCompatibilityIT` は Spring を起動せず `DriverManagerDataSource` と Flyway の API で作った | アプリの起動は V10 まで当ててしまい、V10 の前の状態を作れないため |
| G-4 | 8 | BR1.3 の「そのほかの制御文字」を、一般の区分 Cc に加えて行・段落の区切り（U+2028・U+2029）とした。見えない書式の文字（Cf）は拒否しない | 「改行」として扱うため。Cf は BR1.3 に無い（`DisplayName` は Cf も拒否するが、group の決まりに合わせた）。承認の場で確かめたい |
| G-5 | 8 | `GroupMember` の氏名・メールアドレスは文字列で持ち、record の toString で伏せた | `team.md` の Code Style の形（`UserAdminSummary` と同じ） |
| G-6 | 8 | `GroupAuditEvent` に組み合わせの確かめ（メンバーの操作だけが対象の利用者を持つ、成功は対象のグループを持つ、404 の拒否だけが detail を持たない など）を足した | 設計に無い足した守り。BR8.4・BR8.5 を型で守るため |
| G-7 | 10 | 外部キーの違反の SQLState に 23506 を足した | H2 2.4 は「子が残る親の削除」を 23503、「親の無い子の追加」を 23506 にする（計画・設計は 23503 だけ）。読み替えは制約の名前 `fk_group_members_group` のときだけ |
| G-8 | 10 | 主キーの違反は、制約の名前か表の名前 `GROUP_MEMBERS` で見分ける | H2 が主キーの索引に付ける名前（`PRIMARY_KEY_xx`）が `pk_group_members` と違うため |
| G-9 | 10・12 | `GroupStore.deleteMember` は消した行の有無を返し、無ければ `GROUP_NO_CHANGE` | 外しの前の読み取りを1回省く。グループの行を排他した後のため結果は同じ |
| G-10 | 11 | テストの手伝い `UncommittedWrite`（別の接続で書き込みを未確定のまま持つ）を足した | 計画 4.5 の手伝いの一覧に無い。#7 の「先の側を放さない」をスレッドなしで作るため |
| G-11 | 12 | 結果の型の名前と形を決めた（`GroupChangeResult.InUse(members, assignedRoles)`、`GroupListResult` の中の `Page`・`Row`）。`FirstStep.Store` に detail を持たせた。削除の外部キーの違反の読み替えでは、残りの数を2つ目のトランザクションで数え直す | 計画は型の名前だけを書く。1つ目は違反で使えないため |
| G-12 | 14 | `GroupRequestContextResolver` を group の中に置いた | 計画 4.1 の一覧に無い。group は useradmin に依存しないため、`UserAdminRequestContextResolver` と同じ形を group に持った |
| G-13 | 14 | 本文の `userId` が整数でない（`{"userId":"abc"}`）ときは 400 `MALFORMED_REQUEST`、無いときは 400 `VALIDATION_FAILED`（`fieldErrors`） | 既存の本文の読み取りの誤りの扱い。計画・設計は「400」とだけ書く |
| G-14 | 15 | **停止中の管理者は 403 ではなく 401 `AUTHENTICATION_REQUIRED`** を期待にした | 既存のアクセストークンの認証の入口が停止中の利用者を断るため（Intent 260930-user-admin の決まり。`UserAdminOperationsApiIT` も 401）。機能設計の 6節・7節と計画 8節の AC2.1.6「停止中の 403」と違う。group のコードでは変えられない入口の振る舞い。承認の場で確かめたい |
| G-15 | 15 | 招待中の人の拒否は、招待の行を作り利用者の行が無いことを確かめたうえで、使われていない利用者 ID の拒否で確かめた | 招待中の人は利用者 ID を持たず、「招待中の人の ID で足す」要求を作れないため |
| G-16 | 15 | `GroupAdminListQueryCountIT` は業務処理を直接呼んで数え、`group/service` に置いた。一覧の文は3つ（件数・ページ・メンバーの数） | 認証の入口の読み取りを数えに混ぜないため。4つ目の問う口は B3 の仮の実装が DB を読まない |
| G-17 | 15 | `GroupConnectionUsageIT` は、確定の後の監査の途中を `AuditWriteBarrierConfig` で止めずに、スレッドごとの同時の本数の最大で数えた | 計画 D-9 の手段の差。監査の2本目は同じスレッドで借りるため最大に表れ、決定的に数えられる |
| G-18 | 15 | `GroupBusyLogIT` の WARN の項目は、ログの共通の項目（timestamp・level・logger・thread・message・traceId・spanId）と `lockKind`・`exceptionClass` の中に収まることを確かめた | 計画の「キーが3つだけ」を、共通の項目を除いた形で読んだ |
| G-19 | 15 | `audit/repository/AuditEventRepository` の読み取りは `findById` に加えて `findAllByOrderByOccurredAtAsc` もあった（計画 2.2 の R-03 の行は「`findById` だけ」） | どちらも `event_type` で絞らず、前の版が知らない種類の行を読むと列挙に無い値になりうる。前の版がこの口を画面や API で使っているかは配備の段で確かめる |
| G-20 | 16 | `RoleBoundaryArchitectureTest` は1規則（role がアプリの中で依存してよいのは `group.service` と `common` だけ）。「role に依存してよい機能」の規則は置かなかった | 計画 9節の「2〜3 規則」より少ない。B4・B5 で U4 の口を使う機能が増えると、既存の境界テストを緩める承認が要る形になるため（11節 Q1: A の「最小」） |
| G-21 | 18 | k6 の名前に実行ごとの識別（`runId`）を足した（`perf-rename-<runId>-<VU>-a`・`-b`、`perf-create-<runId>-<VU>-<回>`） | 名前の鍵は全体で一意のため、同じ使い捨ての環境で流し直すと 409 になるのを避ける |
| G-22 | 18 | `http_req_duration{name:…}` に常に通る閾値 `max>=0` を置いた | k6 の要約（`--summary-export`）にタグで絞った値を出すため。判定には使わない |
| G-23 | 18 | ホストに k6 が無いため、`k6 inspect` は手順書と同じ版を固定した `grafana/k6:2.3.0` のコンテナで流した | 計画は `k6 inspect` のコマンドだけを書く |

## 5. 依頼者の答え（確定）と承認の場で確かめること

依頼者は確かめたい点の4つにすべて A と答えた（指揮役から伝達）。

| 点 | 答え | 扱い |
|---|---|---|
| G-14 停止中の管理者は 401 | **A 受け入れる** | 実際の振る舞い（既存の認証の入口の 401）を期待にしたまま。B5 で U4 が認可の表に行を足すときも同じ読み方 |
| G-4 制御文字の範囲 | **A この範囲でよい**（Cc と U+2028・U+2029 を拒み、Cf は拒まない） | 実装のまま |
| G-19 前の版が新しい列のある監査の行を一覧で読めること | **A 配備の段の戻しの練習の合格の条件に入れる** | 8節に引き継ぎとして書いた |
| `audit.service` の分岐 73.1% | **A この Bolt でテストを足して戻す** | `AuditGroupEventListenerTest` に3件を足した（3.2）。本体のコードとカバレッジの除外は変えていない |

承認の場では、計画との差 G-1〜G-23 のほかの項目（とくに G-7・G-8 の区分、G-17 の数え方、G-20 の role の境界テストの規則の数）を確かめる。

## 6. 後に回すこと

- **想定外の DB の例外の手がかり**（11節 Q5: A、NFR 設計の読み直しの R-06）: 想定外の例外はクラスの名前だけの `GroupStoreUnexpectedException` に包み直し、SQLState と制約の名前は ERROR に出していない。落ちたときの調べの手がかりが減るため、出すかを後で決める。
- **`application.yaml` のコメント**（NFR 設計の読み直しの R-09、D-17）: `org.hibernate.orm.jdbc.error: OFF` のコメントにグループの理由を書いていない。B3 は `application.yaml` を変えない決まり（`cicd-pipeline.md` 9節 (vi)）のため。守りは `GroupUniqueViolationSecretLeakIT`（消されると落ちる）。
- **`RoleGroupDeletionGuard` の置き換え**: B3 の仮の実装（数 0・`Allowed`、`TODO(B5)` の1か所）を B5 で本物に置き換え、仮の実装が残っていないことを B5 の終わりの条件にする。

## 7. 見せるもの（`curl` の手順）

開発時の起動（`./gradlew :backend:bootRun`）か、ビルドした WAR で行う。操作は監査に残るため、配備した環境では行わない。管理者のアクセストークンは画面でログインした後のブラウザーの開発者の道具から取るか、ログインの API の応答の `accessToken` を使う（値を記録に残さない）。メンバーにする利用者の ID は `GET /api/admin/users` で引く。

```bash
B=http://localhost:8080; H=(-H "Authorization: Bearer $T" -H "Origin: $B" -H 'Content-Type: application/json')
curl -s "${H[@]}" -X POST "$B/api/admin/groups" -d '{"name":"営業部"}'                 # 201 と groupId
curl -s "${H[@]}" -X POST "$B/api/admin/groups" -d '{"name":"営業部"}'                 # 409 GROUP_NAME_DUPLICATE
curl -s "${H[@]}" -X POST "$B/api/admin/groups/<groupId>/members" -d '{"userId":<userId>}'   # 204
curl -s "${H[@]}" "$B/api/admin/groups/<groupId>"                                     # 200、members に1人
curl -s "${H[@]}" -X DELETE "$B/api/admin/groups/<groupId>"                           # 409 GROUP_IN_USE、members 1
curl -s "${H[@]}" -X DELETE "$B/api/admin/groups/<groupId>/members/<userId>"          # 204
curl -s "${H[@]}" -X DELETE "$B/api/admin/groups/<groupId>"                           # 204
curl -s "${H[@]}" "$B/api/admin/groups?page=1"                                         # 200 と1ページ
unset T
```

## 8. Build and Test・配備の段に引き継ぐこと

計画の「Build and Test に引き継ぐこと」「Performance Validation に引き継ぐこと」「Observability Setup に引き継ぐこと」「配備の段へ引き継ぐこと」「U4 role（B4・B5）へ引き継ぐこと」のとおり。加えて次の2つ。

- **配備の段（G-19、依頼者の答え A）**: 戻しの練習の合格の条件に、「前の版のアプリが、V10 の後の内部DB の複写で、新しい列（`target_role_id`・`target_group_id`・`detail`）とグループの種類（`GROUP_*`）を持つ監査の行がある状態でも、監査の行を一覧で読む口（`AuditEventRepository.findAllByOrderByOccurredAtAsc`）を使う経路で失敗しないこと」を入れる。前の版がこの口を画面や API で使っているかを先に確かめ、使っていなければその旨を記録する。
- **U4 role（B4・B5）**: `AuditEventListener` の `roleGroupFields` の `targetRoleId` が値を持つ枝は、B3 では出来事の送り手が無く通らない。U4 のロールの出来事を足す Bolt で、書き込みの失敗の ERROR に `targetRoleId` が載るテストを足す。
