# 生成の記録（Generation Notes）— U3 グループ（group）

計画: `code-generation-plan.md`（同じディレクトリ、依頼者の Approve Plan で承認済み。11節の Q1〜Q5 はすべて A）。生成の担当（開発担当）が手順ごとのコマンドと結果、計画との差を書き足す。パスはリポジトリのルートからの相対。

- 依頼の範囲（1回目の依頼）: Step 1〜13（業務処理のテストまで）。Step 13 が通ったら止めて報告する。
- 長いコマンドは `caffeinate -i` で包み、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して流した。
- テストの件数は `backend/build/test-results/{test,integrationTest}/*.xml` を集計した実測の値（UP-TO-DATE の飛ばしを避けるため、名指しの実行でも `:backend:cleanTest`・`:backend:cleanIntegrationTest` を付けた）。

## Step 1: 作業の場の用意

- ブランチ: `feature/261004-role-menu-b3`（指揮役が作成済み）。開始の時点の HEAD は `757d5ed7d704a407616fad4a599b8c098ec511d4`（「B3 group のコード生成の計画を承認」。`develop` の先頭と同じ）。
- `git status --short`: アプリのソースに未コミットの変更なし。変更は `aidlc/spaces/default/intents/261004-role-menu/audit/sakura-local-4e42a93f87ce.md`（監査ログの追記。ワークフローの記録として外して判断）だけ。
- `frontend/playwright-report`・`frontend/test-results`: どちらも無い。

## Step 2: テストの実行の準備

- colima: `colima status` は running（macOS Virtualization.Framework、aarch64、docker、mountType sshfs）。`colima ssh -- ls <リポジトリ>/docker` でホームの共有が付いていることを確かめた（`check-container-limits.sh`・`hikari-pool.sh`・`jmx`・`monitoring`・`otel-collector`・`perf`・`targetdb` が見えた）。
- `unit-test-instructions.md` 2節のコマンド（`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直した）:
  - 単体 `UserProblemTypesTest`・`UserAdminProblemTypesTest`・`AuditEventTest`: 3 クラス・11 件、失敗 0・飛ばし 0。
  - 結合 `AuditSchemaIT`・`UserAdminListQueryCountIT`: 2 クラス・11 件、失敗 0・飛ばし 0。
- 洗い出しの検索の流し直し（計画の 4.3・4.5 の一覧との増減）:
  - `git grep -n -F 'UserAdminProblemTypes.USER_NOT_FOUND' -- backend frontend perf`: `useradmin/web/UserAdminController.java:112` と `useradmin/domain/UserAdminProblemTypesTest.java:50` の2件。計画どおり（増減なし）。
  - `git grep -n -e 'TARGET_INVITATION_ID' -e 'containsOnlyKeys' -- backend/src/test`: 監査の列の一覧を持つのは `AuditSchemaIT`・`AuditSecretLeakIT`（計画の 4.5 に入っている）。`containsOnlyKeys` の `AuditEventListenerTest:777` は起動時の出来事の ERROR の項目（計画の 6節 A8 のとおり、足す3項目は値があるときだけ載せるため当たらない）。ほかは監査に関係しない。増減なし。
  - `git grep -n -e 'resideOutsideOfPackages' -- backend/src/test`: 境界テストは 7 か所（access・appearance・invitation・mail・user・useradmin）。`group` に当たりうるのは `UserBoundaryArchitectureTest:82`（計画の 6節 A1、11節 Q2: A で一覧に `group` を足す）だけ。増減なし。
  - `ls backend/src/main/resources/db/migration/`: V1〜V9 だけ（`V9__u1_user_suspension.sql` が最後）。

## Step 3: 変更の前の基準

- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（07:16:54〜07:25:32、約 8 分 38 秒、exit 0）。
- 件数: 単体 186 クラス・1,646 件、結合 144 クラス・723 件。失敗 0・飛ばし 0（対象DB の3種類の結合テストも飛ばされていない）。
- カバレッジ（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `audit.domain` | 99.6%（262/263） | 98.4%（61/62） |
| `audit.service` | 100.0%（178/178） | 82.5%（33/40） |
| `user.domain` | 99.6%（264/265） | 98.0%（147/150） |
| `user.service` | 99.7%（370/371） | 95.7%（135/141） |
| `useradmin.domain` | 100.0%（83/83） | 100.0%（52/52） |
| `useradmin.web` | 98.8%（83/84） | 93.8%（15/16） |
| 全体 | 98.7%（6,739/6,829） | 94.6%（2,525/2,670） |

- `backend/build.gradle.kts` の `packagesJudgedByTotal` は 7 パッケージのまま（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。手を入れるパッケージ（計画の 5節: `audit.domain`・`audit.service`・`user.domain`・`user.service`・`useradmin.domain`・`useradmin.web`）は入っていない。
- 次の空き移行番号は V10（V1〜V9 だけがある）。計画の D-13 のとおり `V10__u3_group.sql` とする。

## Step 4: 境界の口の移し替え — 実装

- `backend/src/main/java/cherry/mastersmith/user/domain/UserProblemTypes.java`: `USER_NOT_FOUND`（404、code・状態コード・日英の文言は `useradmin` の定義のまま）を移し、`all()` を `PASSWORD_CURRENT_MISMATCH`・`USER_NOT_FOUND` の2つにした。
- `backend/src/main/java/cherry/mastersmith/useradmin/domain/UserAdminProblemTypes.java`: `USER_NOT_FOUND` の定義を消し、`of(USER_NOT_FOUND)` は `UserProblemTypes.USER_NOT_FOUND` を返す。`all()` から外した。クラスの説明に移した経緯を書いた。2つの一覧の付け替えは同じ変更（code の二重の定義で起動が止まらない）。
- `backend/src/main/java/cherry/mastersmith/useradmin/web/UserAdminController.java`: 1か所（氏名・言語の変更の `NotFound`）を `UserProblemTypes.USER_NOT_FOUND` に替えた。
- `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`: `findSummariesByIds(Set<Long>)` を足した（計画の D-2。既存の `UserRepository.findAllById` の1回の読み取り、空の集合は DB を読まずに空、いない ID は含めない、`UserAdminSummary` を利用者 ID の昇順で返す。エンティティから要約を作る `toAdminSummary(User)` を足した）。`user.repository` には手を入れていない。
- 計画との差: なし。

## Step 5: 境界の口の移し替え — テスト

- 書き換え: `user/domain/UserProblemTypesTest`（一覧に `USER_NOT_FOUND` 404、文言が前の定義のままであること）、`user/service/UserProblemTypeCatalogTest`（一覧と登録に `USER_NOT_FOUND`）、`useradmin/domain/UserAdminProblemTypesTest`（一覧から外れ、`of(USER_NOT_FOUND)` が `UserProblemTypes.USER_NOT_FOUND` と同じもの）、`useradmin/service/UserAdminProblemTypeCatalogTest`（一覧に `USER_NOT_FOUND` が無いこと、user と useradmin の2つの一覧を合わせても登録で重ならないこと）。
- 新しい: `user/service/UserSummariesByIdsIT`（3件。3人をまとめて1つの文 `select users` で読み ID の順、いない ID・0・負は含めず空の集合は文を出さない、toString にメールアドレス・氏名・ハッシュが出ない）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests '…UserProblemTypesTest' --tests '…UserProblemTypeCatalogTest' --tests '…UserAdminProblemTypesTest' --tests '…UserAdminProblemTypeCatalogTest' :backend:integrationTest --tests '…UserSummariesByIdsIT' --tests 'cherry.mastersmith.useradmin.*'`
- 結果: 単体 4 クラス・12 件、結合 14 クラス・61 件。失敗 0・飛ばし 0（exit 0）。
- 計画との差: user と useradmin の2つの一覧を合わせた登録の確かめは、user のテストから useradmin を参照しないよう `UserAdminProblemTypeCatalogTest` に置いた（useradmin → user の向きは既存の依存のとおり）。コミットの区切り C1 の終わり。

## Step 6: データの形 — 実装

- `backend/src/main/resources/db/migration/V10__u3_group.sql`（新しい）: `groups`（`group_id` 自動・`name` VARCHAR(128)・`name_key` VARCHAR(256)・`created_at`・`updated_at`、`uk_groups_name_key`）、`group_members`（`pk_group_members(group_id, user_id)`・`fk_group_members_group`（ON DELETE RESTRICT）・`fk_group_members_user`・`ix_group_members_user_id(user_id)`）、`audit_events` に `target_role_id` BIGINT・`target_group_id` BIGINT・`detail` VARCHAR(16384) を NULL 可・既定値なしで足した。説明のコメントは日本語。
- `audit/domain/AuditEvent.java`: 定数 `MAX_DETAIL_LENGTH = 16_384`、項目 `targetRoleId`・`targetGroupId`・`detail`（列の対応、`updatable = false`）、ファクトリー `withRoleGroupTarget(…)`（detail は `String.length()` で上限を超えたら `IllegalArgumentException`、文は長さだけで中身を載せない。切り詰めない）、getter、`toString` に `targetRoleId`・`targetGroupId`・`detailLength` を足した（中身は出さない。計画の D-10）。
- `audit/domain/AuditEventType.java`: `GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`。
- `audit/domain/AuditFailureReason.java`: `GROUP_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`。
- 種類の網羅の `switch` の追従（コンパイルのため）: `AuditEventFactory.resultOf`（グループの5つの種類は結果を出来事が持つため例外の側）、`AuditEventListener.isDslOperation`（偽の側）。group のクラスには依存しない（C2 の区切り。受け取り `onGroupAuditEvent` と写し方は Step 12）。
- 計画との差: `resultOf`・`isDslOperation` の網羅の `switch` に値を足したこと（計画の 4.3 の表に無いが、列挙に値を足すとコンパイルで必ず要る追従。振る舞いは既存の利用者の管理の種類と同じ扱い）。

## Step 7: データの形 — テスト

- 新しい: `audit/domain/AuditEventGroupTargetTest`（5件。ファクトリーの項目、サロゲートペアを含む 16,384 ちょうどは受け付け 16,385・16,386 は断り文に中身を載せない、切り詰めない、空を許す、toString は長さだけ）、`audit/domain/AuditMigrationCompatibilityIT`（4件。Spring を起動せず一時の H2 に Flyway を直接当てる。V1〜V9 だけを写した場所を読ませた前の版の Flyway（`validateOnMigrate(true)`、ほかは既定）が V10 まで当てた内部DB で検証・移行とも止まらず実行 0 件、V10 の前に書いた行が V10 の後も読めて3列が空、V10 の後に前の版の列だけで追記できる、`groups`・`group_members` の制約の名前・`user_id` の索引・列の長さ 128／256 と `SELECT … FROM groups` が通る）。
- 書き換え: `audit/domain/AuditSchemaIT`（2件を足した。3列の NULL 可・既定値なし・型（BIGINT・CHARACTER VARYING(16384)）とサロゲートペアの 16,384 の保存と読み戻し、前の版の列だけの INSERT で3列が空。基盤の設計の読み直しの R-02）、`audit/service/AuditSecretLeakIT`（列の一覧に `TARGET_ROLE_ID`・`TARGET_GROUP_ID`・`DETAIL`、行の文字列化に detail を足した。グループの操作の後の行の確かめは Step 15）、`audit/testsupport/AuditRows`（行の後ろに `actorUserId`・`targetUserId`・`targetRoleId`・`targetGroupId`・`detail` を足した。既存の呼び出しは変えていない）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.audit.*' :backend:integrationTest --tests 'cherry.mastersmith.audit.*'`
- 結果: 単体 9 クラス・119 件、結合 14 クラス・68 件。失敗 0・飛ばし 0（既存の監査のテストを含む。exit 0）。
- 計画の 6節 A4 の確かめ: 表名 `groups` は H2 2.4.240 で予約語として断られず、移行・`SELECT`・`… FOR UPDATE WAIT 3` が通った（手元で H2 のシェルでも確かめた）。
- 計画との差: `AuditRows` には detail と3列のほか、`GroupAuditIT` で使う `actor_user_id`・`target_user_id` も足した（読み取りの列を増やしただけで既存の呼び出しは変えない）。`AuditMigrationCompatibilityIT` の内部DB は Spring の起動を使わず、`DriverManagerDataSource` と Flyway の API で作った（アプリの起動は V10 を当ててしまい、V10 の前の状態を作れないため）。コミットの区切り C2 の終わり。

## Step 8: group のドメイン — 実装

- `backend/src/main/java/cherry/mastersmith/group/package-info.java`（依存してよい先・依存されてよい先）、`group/domain/package-info.java`。
- `group/domain/Group.java`（`@Entity(name = "UserGroup")`・`@Table(name = "groups")`。計画の D-3。名前と鍵は `GroupName` からだけ受ける。`rename` は名前・鍵・更新の日時だけを書き換える）、`GroupMembership.java`（`@IdClass(GroupMembershipId.class)`、作った後に変える手段なし）、`GroupMembershipId.java`（`Serializable` の主キー）。
- `group/domain/GroupName.java`・`GroupNameValidation.java`（sealed、`Valid(name)`・`Invalid(reason)`、理由は `INVALID_BLANK`・`INVALID_TOO_LONG`・`INVALID_CONTROL_CHARACTER`）。前後の Unicode の White_Space（全角の空白を含む。`user.domain.DisplayName` と同じ範囲を group の中に持った）を取り除き、判定の順は空 → 64 コードポイント超え → 制御文字。鍵は `toLowerCase(Locale.ROOT)` で、作るのはこのクラスだけ。同じ名前の判定 `sameAs`（文字どおり）。
- `group/domain/GroupOperation.java`・`GroupAuditEvent.java`（record。成否と理由、メンバーの操作だけが対象の利用者を持つ、成功は対象のグループを持つ、グループ・利用者が無い拒否だけが detail を持たない、を作るときに確かめる）・`GroupAuditFailure.java`・`GroupAuditDetail.java`（sealed、`Name`・`Rename`・`Membership`・`InUse`。数は 0 以上）。
- `group/domain/GroupProblemTypes.java`（`GROUP_NOT_FOUND` 404・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`・`GROUP_NO_CHANGE`・`GROUP_BUSY` 409、日英の文言。`of(GroupRejection)` は `USER_NOT_FOUND` に `UserProblemTypes.USER_NOT_FOUND` を返し、`all()` には含めない）。
- `group/domain/GroupMember.java`（record、toString で氏名・メールアドレスを伏せる）・`GroupRejection.java`（業務の拒否の理由と監査の失敗の理由の対応）。
- 計画との差:
  - BR1.3 の「そのほかの Unicode の制御文字」を、一般の区分 Cc に加えて行・段落の区切り（Zl・Zp。U+2028・U+2029）とした（「改行」として扱うため）。見えない書式の文字（Cf）は BR1.3 に無いため拒否しない（`DisplayName` は Cf も拒否するが、group の決まりに合わせた）。
  - `GroupMember` の氏名・メールアドレスは、伏せ字の型（`RedactedText` など）ではなく文字列で持ち、record の toString で伏せた（`team.md` の Code Style「record は toString を上書きして値を伏せる」の形。`UserAdminSummary` と同じ）。
  - `GroupAuditEvent` の組み合わせの確かめ（上の4つ）は設計に書かれていない足した守り。BR8.4・BR8.5 の書き方を型で守るため。

## Step 9: group のドメイン — テスト

- 新しい（`backend/src/test/java/cherry/mastersmith/group/domain/`）: `GroupNameTest`（16件。64 コードポイントちょうど（サロゲートペアだけで 128 単位）は受け付け 65 は拒否、取り除いた後で数える、全角の空白・NBSP の取り除き、空・空白だけ・null、内側の改行・タブ・CR・NUL・BEL・DEL・U+2028 の拒否、理由の順、「Sales」「sales」「SALES」は同じ鍵で「Ｓａｌｅｓ」は別、`İ` の言語に依らない小文字化、`sameAs`、値の等しさ）、`GroupNamePropertyTest`（jqwik の 5 性質。受け付けた名前は前後に空白が無く 1〜64・制御文字なし、受け付けの可否が決まりと一致、受け付けた名前を読み直しても同じ値と鍵、大文字と小文字だけの違いは同じ鍵、名前は 128・鍵は 256 単位に収まる）、`GroupAuditEventTest`（5件）、`GroupAuditDetailTest`（4件。4つの形、決めたキーだけ）、`GroupProblemTypesTest`（4件。code と状態コード、拒否の理由ごとに1つの code・`USER_NOT_FOUND` は user の定義、監査の失敗の理由の対応、日英の文言に値の差し込みが無い）、`GroupMemberTest`（5件。toString で伏せる、エンティティの値）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:test --tests 'cherry.mastersmith.group.domain.*'`
- 結果: 単体 6 クラス・39 件、失敗 0・飛ばし 0（exit 0）。`GroupNamePropertyTest` は単体テストの結果に入っている（Infrastructure Design の承認の場の決定 (2)。jqwik の既定の 1,000 回）。
- 計画との差: なし。

## Step 10: DB アクセス — 実装

- `group/repository/`（読み取りだけ。`org.springframework.data.repository.Repository` を継ぎ、書き込みの方法・`@Modifying`・排他を置かない。計画の D-4）:
  - `GroupRepository`: `countAll`・`findPage(Pageable)`（ID の順）・`findView`・`findViews`（ID の順、いない ID は含めない）・`existsGroup`・`findIdByNameKey`。
  - `GroupMemberRepository`: `countByGroup`・`countByGroups`（`GROUP BY` の1回）・`findMembers`（`added_at, user_id` の順）・`isMember`・`findGroupIdsOfUser`（`user_id` の索引）・`findMemberPairs`（グループの表からの左の外部結合の1回。存在しないグループは行なし、メンバー 0 は利用者 ID が null の1行）。
  - 投影の record: `GroupRowView`・`GroupMemberCount`・`GroupMemberRow`・`GroupMemberPair`（ID・名前・数・日時だけ。氏名・メールアドレスを持たない）。
- `group/store/`（TraceAspect の対象の外）: `GroupStore`（`@Component`。`EntityManager` をコンストラクターで受ける。方法は排他 `lockGroup`（`PESSIMISTIC_WRITE`・ヒント 3,000 ms）と、書き込みと flush の `insertGroup`・`renameGroup`・`deleteGroup`・`insertMember`・`deleteMember`（JPQL の一括の削除で、消したかを返す）に分けた。計画の D-5）、`StoreOutcome`（sealed、`Done`・`GroupMissing`・`NameTaken`・`AlreadyMember`・`Referenced`・`Busy(lockKind)`）、`StoreFailureClassifier`（区分の順は `RowLockFailures.isLockFailure` → SQLState と制約の名前 → 想定外。上限切れは `RowLockFailures.warn` で WARN を1回、想定外はクラスの名前だけの例外に包み直す）、`GroupStoreUnexpectedException`（元の例外・文・SQLState・制約の名前を持たない。11節 Q5: A）、`package-info.java`。
- 排他の種類（WARN の `lockKind`）: 行の排他と削除は `GROUP_ROW`、作成・名前の変更は `GROUP_NAME_KEY`、メンバーの追加と外しは `GROUP_MEMBER_KEY`。
- 計画との差:
  - 外部キーの違反の SQLState に 23506 を足した。H2 2.4 は「子が残る親の削除」を 23503、「親の無い子の追加」を 23506 にするため（計画・設計は 23503 だけを書く）。読み替えは制約の名前 `fk_group_members_group` に当たるときだけ（利用者への外部キー `fk_group_members_user` の違反は想定外のまま）。
  - 主キーの違反の見分けは、H2 が主キーの索引に付ける名前（`PRIMARY_KEY_xx`）が制約の名前 `pk_group_members` と違うため、制約の名前か表の名前 `GROUP_MEMBERS` で見分ける。制約の名前は Hibernate の `ConstraintViolationException.getConstraintName()` と連なりの `SQLException` の文から探し、このクラスの中で照らすだけでログ・例外・戻り値に載せない。
  - `deleteMember` は消した行の有無を返し、業務処理は偽を「メンバーでない」（`GROUP_NO_CHANGE`）に読む（外しの前の読み取りを1回省く。グループの行を排他した後のため、結果は同じ）。

## Step 11: DB アクセス — テスト

- 新しい: `group/store/StoreFailureClassifierTest`（7件。上限切れ（型・H2 の誤りの番号 50200・行き詰まり 40001）を先に、名前の鍵と主キーの違反を制約の名前で、外部キーは 23503・23506 の両方、待った後の違反（#9）も同じ区分、利用者への外部キー・ほかの一意の制約・ほかの SQLState・ほかの例外は想定外、包み直した例外は原因を持たず文にクラスの名前だけ（SQLState・制約の名前・値なし）、上限切れの WARN は1件で `lockKind` と `exceptionClass` だけ・例外を渡さない）、`group/store/GroupStoreConstraintIT`（6件。#7: 主キーの待たない違反 → `AlreadyMember`、別の接続が未確定で持つ主キーの待ちの上限切れ → `Busy(GROUP_MEMBER_KEY)` と WARN 1件（約 2.0 秒）、削除の前にメンバーを確定させた外部キー → `Referenced` で両方が残る、グループを消した後のメンバーの追加の外部キー → `Referenced`、名前の鍵の違反 → `NameTaken`、通常の経路）、`group/repository/GroupRepositoryIT`（6件。ID の順のページと件数、メンバーの数（0 のグループは行なし）、詳細の並び（足した順・同じなら ID の順）、所属・メンバーか・有無・要約、組の読み取り（存在しないグループを含めず空のグループは利用者なしの1行）、名前の鍵の読み取りと `user_id` の索引）。
- 手伝い（`group/testsupport/`）: `GroupFixtures`（利用者・グループ・メンバーを本番の書き込みを通さずに作る。名前の鍵は `GroupName` から作る。メールアドレスは `example.com`）、`UncommittedWrite`（別の接続で書き込みを未確定のまま持ち続け、閉じると巻き戻す）。計画の 4.5 の手伝いの一覧に `UncommittedWrite` は無い（#7 の「先の側を放さない」をスレッドを使わずに作るために足した）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.group.store.*' :backend:integrationTest --tests 'cherry.mastersmith.group.store.*' --tests 'cherry.mastersmith.group.repository.*'`
- 結果: 単体 1 クラス・7 件、結合 2 クラス・12 件。失敗 0・飛ばし 0（exit 0）。
- 捨ての試し（NFR3.7）の本物での確かめ: T3（主キーの違反と約 2 秒の上限切れ）・T4 (ii)（排他を通らない追加の後の削除の外部キーの違反）・T6（区分の後に値が出ない）・T7（型での区分）をこの段で確かめた。T1・T2・T4 (i)・T5 は Step 13・15。

## Step 12: 業務処理 — 実装

- `group/service/`（`@Transactional` を付けず `TransactionTemplate` だけ。`EntityManager` を使わない）:
  - `GroupAdminService`: 作成・名前の変更・削除・メンバーの追加と外し・一覧・詳細。1つ目のトランザクションは `GroupStoreTransactions.inFirst` で組み、排他 → 待ち合わせの口 → 業務の判定 → 書き込みと flush → 成功の出来事の順。業務の拒否と違反の読み替えは、1つ目を巻き戻した後に2つ目の `TransactionTemplate`（書き込みなし）で失敗の出来事だけを出す。`Busy` は出来事なし。`Referenced` は操作ごとに読み替え（削除 → `GROUP_IN_USE`、追加 → `GROUP_NOT_FOUND`。計画の D-6）。利用者の有無は既存の `UserAccountService.findById`（D-18）。一覧と詳細は読み取りだけの1つのトランザクション。
  - `GroupStoreTransactions`（`Done` 以外で必ず `setRollbackOnly`）・`FirstStep`（sealed、`Done`・`Rejected(rejection, detail)`・`Store(outcome, detail)`。D-5）。
  - `GroupBarrier`（`beforeLock`・`afterLock`・`afterCheck`・`afterWrite`）・`NoOpGroupBarrier`。鍵は作成と名前の変更では名前の鍵、メンバーの操作では `<グループの ID>:<利用者 ID>`、削除ではグループの ID。
  - `GroupMembershipQuery`・`GroupMembershipQueryImpl`（`groupIdsOfUser`・`exists`・`summaries`・`memberUserIds`・`lockForAssignment`。最後は呼び出し元のトランザクションの中だけで、無ければ `IllegalStateException`）・`GroupSummary`・`GroupRowLock`。
  - `GroupDeletionGuard`・`DeletionDecision`（group に既定の実装なし）・`GroupProblemTypeCatalog`。
  - 結果の型: `GroupCreateResult`・`GroupChangeResult`（`Done`・`Invalid`・`Rejected`・`InUse(members, assignedRoles)`・`Busy`）・`GroupListResult`（`Page`・`Row` を中に持つ）・`GroupDetailResult`・`GroupView`。
- `role/service/RoleGroupDeletionGuard`（仮の実装。渡した ID のすべてに 0、`canDelete` は `Allowed`。Javadoc に B3 の仮の実装と B5 での置き換え、`TODO(B5)` の印は1か所）・`role/package-info.java`・`role/service/package-info.java`。
- `audit`: `AuditDetailJson`（Jackson 3 の木から決めたキーだけの JSON。D-11）、`AuditEventFactory.from(GroupAuditEvent)`・`groupEventTypeOf`・`groupFailureReasonOf`、`AuditEventListener.onGroupAuditEvent`（確定の後、`fallbackExecution = true`）と、書き込みの失敗の ERROR に `targetRoleId`・`targetGroupId`・`detail` を値があるときだけ足す形（D-10）。
- `backend/src/test/java/cherry/mastersmith/user/UserBoundaryArchitectureTest.java`: 「user に依存してよい機能」の一覧に `group` だけを足し、説明に承認の経緯を書いた（11節 Q2: A、D-20）。ほかの規則は変えていない。
- 計画との差:
  - 結果の型の名前と形を決めた（計画は `GroupCreateResult`・`GroupChangeResult`・`GroupListResult`・`GroupDetailResult` など、とだけ書く）。使用中の削除の拒否は残りの数を持つ `GroupChangeResult.InUse` で返す（応答の `members`・`assignedRoles` のため）。一覧の行とページは `GroupListResult` の中の record にした。
  - `FirstStep.Store` に detail を持たせた（違反の読み替えの失敗の出来事に、1つ目の中で分かった名前（変える前の名前など）を渡すため）。削除の外部キーの違反の読み替えでは、残りの数を2つ目のトランザクションで数え直す（1つ目は違反で使えないため）。
  - 外しは `GroupStore.deleteMember` が消した行の有無を返し、無ければ `GROUP_NO_CHANGE`（外しの前の読み取りを省いた。Step 10 の記録のとおり）。

## Step 13: 業務処理 — テスト

- 新しい（単体）: `group/service/GroupAdminServiceTest`（13件。名前の誤りはトランザクションも store も出来事も無し、作成の順と成功の出来事、事前の判定と違反の両方の名前の重なり（1つ目の巻き戻しの後の2つ目で失敗の出来事）、5つの操作の `Busy` は出来事なしで巻き戻し、名前の変更の判定（無い・同じ名前・ほかのグループの鍵・大文字と小文字だけの変更は許す・待ち合わせの口の順）、名前の変更の違反、削除は問う口に必ず聞き残りの数を返す（メンバー・Blocked・両方）、空の削除と外部キーの違反の数え直し、追加の判定の順（グループ → 利用者 → メンバー）、追加の違反の操作ごとの読み替え、外し、一覧（読み取りだけの1つのトランザクション、最後のページより後は問い合わせを省く）、詳細（足した順・toString で伏せる・無ければ利用者を読まない））、`group/service/GroupProblemTypeCatalogTest`（3件）、`role/service/RoleGroupDeletionGuardTest`（2件）、`audit/domain/AuditGroupEventFactoryTest`（5件。種類・理由・対象、4つの形のキーだけ、引用符と `<`・`&`・`'` のエスケープと読み戻し、`GROUP_NOT_FOUND`・`USER_NOT_FOUND` の detail は空）、`audit/service/AuditGroupEventListenerTest`（4件。確定の後の受け取り、1回の追記、書き込みの失敗の ERROR に対象のグループと detail、空のときは載せない）。
- 新しい（結合）: `group/service/GroupStoreTransactionsIT`（3件。T5: 本物の一意の違反で1つ目が例外なく巻き戻り2つ目が確定、`Done` 以外は書いたものも巻き戻る、印を付けないと `UnexpectedRollbackException`）、`group/service/GroupMembershipQueryIT`（5件。外した直後の `groupIdsOfUser`（業務処理の外しを通す）、有無と要約、`memberUserIds`、`lockForAssignment` の Locked(真・偽) とトランザクションの外の拒否、別の接続が行を持つときの `Busy`（約 3 秒））、`group/service/GroupMembershipQueryCountIT`（3件。所属 1 と 20、メンバー 1 と 50、グループ 1 と 20 で、どれも文 1 回）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests 'cherry.mastersmith.group.service.*' --tests 'cherry.mastersmith.role.service.*' --tests 'cherry.mastersmith.audit.domain.AuditGroupEventFactoryTest' --tests 'cherry.mastersmith.audit.service.AuditGroupEventListenerTest' :backend:integrationTest --tests 'cherry.mastersmith.group.service.*'`
- 結果: 単体 5 クラス・27 件、結合 3 クラス・11 件。失敗 0・飛ばし 0（exit 0。1回目は `GroupMembershipQueryIT` の AssertJ の `assertThat` のあいまいさでコンパイルが通らず、値を変数に受けてから比べる形に直して流し直した）。
- 追加の確かめ（Step 12 で既存の audit と境界テストに手を入れたため）: `:backend:test --tests 'cherry.mastersmith.audit.*' --tests '…UserBoundaryArchitectureTest' --tests '…ArchitectureTest' :backend:integrationTest --tests 'cherry.mastersmith.audit.*'` → 単体 13 クラス・136 件、結合 14 クラス・68 件、失敗 0・飛ばし 0。
- 捨ての試し（NFR3.7）の本物での確かめ: T5 をこの段で確かめた。T1・T2・T4 (i) は Step 15（API の同時の重なり）。
- まだ流していないもの: `spotlessApply`・`spotlessCheck`（Step 17）、全体のテスト（Step 17・22）。手で書いた形が palantir-java-format と食い違う所は Step 17 の `spotlessApply` でそろう見込み。

## 2回目の依頼（Step 14〜23）

指揮役の2回目の依頼で Step 14〜23 を行う。Step 23 の後で止める（Step 24 のコミットの提案は指揮役）。Step 1〜13 の計画との差は、依頼者に後でまとめて伝えるため `code-summary.md` の差の節に載せる。

## Step 14: API — 実装

- `group/web/GroupAdminController`（7つの口。クラスに `@ApiAccess(ApiAccessLevel.ADMIN)`、道は `/api/admin/groups` の下。結果の型を `switch` で尽くして `BusinessException` に変える。`GROUP_IN_USE` の応答に `members`・`assignedRoles`。`userId` が無ければ 400 と `fieldErrors`）。
- DTO: `GroupNameRequest(name)`・`MemberAddRequest(userId)`（決めた項目だけ）、`GroupResponse`・`GroupPageResponse`・`GroupRowResponse`・`GroupDetailResponse`・`GroupMemberResponse`（氏名・メールアドレスの値を取り出すのはここだけ、toString で伏せる）。
- `GroupFieldErrors`（`UserAdminFieldErrors` と同じ形。名前の理由は `INVALID_BLANK` → `REQUIRED`、`INVALID_TOO_LONG` → `TOO_LONG`、`INVALID_CONTROL_CHARACTER` → `INVALID_CHARACTER`）。
- `GroupRequestContextResolver`（操作した管理者の利用者 ID と送り手の情報。`useradmin.web` の同じ形の部品を group の中に持った）。
- `./gradlew :backend:compileJava` は通った。
- 計画との差: `GroupRequestContextResolver` は計画の 4.1 の一覧に無い（group は useradmin に依存しないため、`UserAdminRequestContextResolver` を使えず、同じ形を group の中に置いた）。本文の値が整数でない（`{"userId":"abc"}`）ときは、既存の本文の読み取りの誤りの扱いで 400 `MALFORMED_REQUEST` になる（計画・設計は「400」とだけ書く）。

## Step 15: API — テスト（結合）

- 手伝い（`group/testsupport/`）: `TestGroupBarrier`（`@Primary`。4つの点のどれかで最初に来た1件を止める `hold` と、通ったことを知らせる `signal`。待ちの上限 20 秒で、届いたらテストの作りの誤りとして例外）、`TestGroupDeletionGuard`（`@Primary`、グループごとの割り当ての数を決められる）、`GroupApi`（要求の組み立て）、`GroupActors`（管理者・管理者の印を持たない利用者・停止中の管理者・メンバーにする利用者）、`ConnectionHoldRecorder`（テストだけで接続を貸す部品を `DelegatingDataSource` で包み、スレッドごとの同時の本数の最大を記録する）。
- 新しい（`group/web/`）: `GroupAdminApiIT`（8件）・`GroupAdminAuthorizationApiIT`（28 行）・`GroupAdminMassAssignmentIT`（3件）・`GroupAdminIdorApiIT`（6件）・`GroupConcurrencyIT`（#1 の2つの順・#5・#6 の4件）・`GroupNameConflictIT`（#2・#3・#4 の違反と上限切れの4件）・`GroupBusyLogIT`（1件、3つの排他の種類）・`GroupConflictAuditIT`（3件）・`GroupAdminMetricsIT`（1件）・`GroupConnectionUsageIT`（3件）・`GroupSecretLeakIT`（1件）・`GroupUniqueViolationSecretLeakIT`（INFO と TRACE の2件）。`group/service/GroupAdminListQueryCountIT`（2件）。`audit/service/GroupAuditIT`（3件）・`GroupAuditWriteFailureIT`（2つの失敗の形 × 2件）。`audit/service/AuditSecretLeakIT` にグループの操作の後の7行の確かめを1件足した。
- コマンド: `caffeinate -i ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.group.*' --tests 'cherry.mastersmith.audit.service.GroupAuditIT' --tests 'cherry.mastersmith.audit.service.GroupAuditWriteFailureIT' --tests 'cherry.mastersmith.audit.service.AuditSecretLeakIT'`
- 結果: 1回目は 100 件中 2 件が落ちた（`GroupAuditWriteFailureIT` の「再試行はしない」の数え。下ごしらえのグループの作成の書き込みも数えていた。テストの作りの誤りとして、数えを切り替えの直前に 0 に戻す形に直した）。その前の途中の流しで `GroupAdminIdorApiIT` の招待中の人の確かめが落ちた（招待の ID を利用者 ID として送ったため、既にいる利用者の ID と重なった。テストの作りの誤り）。招待中の人は利用者の行も利用者 ID も持たないことを確かめ、使われていない ID で拒否を確かめる形に直した。直した後は全件通過（Step 17 の全体の流しで確かめた）。
- 計画との差:
  - 停止中の管理者は 403 ではなく **401 `AUTHENTICATION_REQUIRED`** になる。既存のアクセストークンの認証の入口が停止中の利用者を断るため（Intent 260930-user-admin の決まり。利用者の管理の API の `UserAdminOperationsApiIT` も 401 を確かめている）。機能設計の 6節・7節と計画の 8節（AC2.1.6「停止中の 403」）の書き方と違う。group のコードでは変えられない入口の振る舞いのため、実際の振る舞い（401）を期待にした。**承認の場で確かめたい点**。
  - 招待中の人は利用者 ID を持たないため、「招待中の人の ID で足す」要求は作れない。招待の行を作り利用者の行が無いことを確かめたうえで、使われていない利用者 ID の拒否で確かめた。
  - `GroupAdminListQueryCountIT` は API ではなく業務処理を直接呼んで数え、`group/service` に置いた（認証の入口の読み取りを数えに混ぜないため）。一覧の文は Hibernate の文として3つ（件数・ページ・メンバーの数）で、4つ目の問う口は B3 の仮の実装が DB を読まない。
  - `GroupConnectionUsageIT` は、確定の後の監査の途中を `AuditWriteBarrierConfig` で止めずに、スレッドごとの同時の本数の最大で決定的に数えた（計画の D-9 の手段の差。監査の2本目は同じスレッドで借りるため最大に表れる）。
  - `GroupBusyLogIT` の WARN の項目は、ログの共通の項目（timestamp・level・logger・thread・message・traceId・spanId）と `lockKind`・`exceptionClass` の中に収まることを確かめた（計画の「キーが3つだけ」を、共通の項目を除いた形で読んだ）。
  - `audit/repository/AuditEventRepository` の読み取りは `findById` に加えて `findAllByOrderByOccurredAtAsc` もある（計画の 2.2 の R-03 の行は「`findById` だけ」と書く。どちらも `event_type` で絞らず、前の版が知らない種類の行を読むと列挙に無い値になりうる。前の版のアプリがこの口を画面や API で使っているかは配備の段で確かめる）。

## Step 16: 境界と構造の検査

- 新しい: `group/GroupBoundaryArchitectureTest`（6規則。`logical-components.md` 2節の6項目。各規則に「規則が依存を見分けている」確かめを添えた）、`role/RoleBoundaryArchitectureTest`（1規則。role がアプリの中で依存してよいのは `group.service` と `common` だけ）。
- コマンド: `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test --tests '…GroupBoundaryArchitectureTest' --tests '…RoleBoundaryArchitectureTest' --tests 'cherry.mastersmith.*BoundaryArchitectureTest' --tests '…ArchitectureTest' --tests '…ApiAccessArchitectureTest' --tests '…ApiAccessRulesTest' --tests '…PublicApiInventoryTest' :backend:integrationTest --tests '…ApiAccessConsistencyIT'`
- 結果: 単体 18 クラス・87 件、結合 1 クラス・5 件。失敗 0（B2 の構造の検査と実行時の検査で、7つの口の `ADMIN` の印と管理者の道が合った）。
- 計画との差: `RoleBoundaryArchitectureTest` に「role に依存してよい機能」の規則は置かなかった（B4・B5 で U4 の口を使う機能が増えると、既存の境界テストを緩める承認が要る形になるため。計画の Q1: A の「最小」の範囲）。

## Step 17: バックエンドの区切りの確かめ

- `./gradlew :backend:spotlessApply` を流した（手で書いた形を palantir-java-format にそろえた）。
- `TODO(B5)` は `role/service/RoleGroupDeletionGuard.java` の1か所だけ（`grep -rn 'TODO(B5)' backend/src`。新しいファイルは git の追跡の外のため `git grep` ではなく `grep` で数えた）。
- colima のホームの共有を確かめ（`colima ssh -- ls <リポジトリ>/docker`）、環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:spotlessCheck :backend:test :backend:integrationTest`（08:21:12〜08:30:42、約 9分30秒、exit 0）。
- 結果: 単体 201 クラス・1,734 件、結合 166 クラス・829 件。失敗 0・飛ばし 0。コミットの区切り C3 の終わり。

## Step 18: 負荷の台本

- `perf/k6/scenarios.js`: 9つの場面（`groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`・`groupPoolLimit`）、一覧 `GROUP_SCENARIOS`、`setupTimeout = '10m'`、8場面の閾値、`groupPoolLimit` の件数（`groupPoolLimit_204`・`_409`・`_500`・`_other`）、`setupGroups()`（グループとメンバーを API で作り、候補の利用者 ID は利用者の一覧の検索で引く）を足した。先頭のコメントに場面の一覧を書いた。繰り返しの中に `sleep` は無い。
- `perf/README.md`: 「グループの管理の場面（Intent 261004-role-menu の U3）」の節（場面の表、データの SQL（`perf-gr-0001`〜`1000` と `perf-graop01`）、setup が作るもの、一時の `app.env`（合否の回 11、記録の回 10、指標の公開）、判定、`baseUnit` の読み方、`active` での切り分け、`caffeinate -i`、監査の数え方と片付け）。
- 確かめ: ホストに k6 が無いため、手順書と同じ版を固定した `grafana/k6:2.3.0@sha256:9c2dee7f…` のコンテナで `SCENARIO=<場面> k6 inspect --include-system-env-vars /scripts/scenarios.js` を9場面について流した（使い捨ての環境での実行はしていない）。
  - `groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`: `constant-vus`、10 VU（`DURATION` の既定 1m。手順書で 3m を渡す）。
  - `groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove`: `per-vu-iterations`、10 VU × 100 回、`maxDuration` 3m0s。
  - 8場面の閾値: `iteration_duration{scenario:<場面>}` `p(95)<1000`、`checks{scenario:<場面>}` `rate==1`、`http_req_duration{name:<場面>}` `max>=0`。
  - `groupPoolLimit`: `constant-vus`、閾値なし。
  - 9場面とも `setupTimeout` 10m0s。
- 計画との差:
  - 名前に実行ごとの識別（`runId`、setup の時刻）を足した（`perf-rename-<runId>-<VU>-a`・`-b`、`perf-create-<runId>-<VU>-<回>`。計画は `perf-rename-<VU>-a` の形）。名前の鍵は全体で一意のため、同じ使い捨ての環境で流し直すと 409 になるのを避けるため。
  - `http_req_duration{name:…}` に常に通る閾値 `max>=0` を置いた（判定には使わない）。k6 の要約（`--summary-export`）にタグで絞った値を出すには閾値が要るため。
  - setup の用意に時間がかかるため、setup の中では4分ごとにログインし直し、最後に場面で使うトークンを取り直す（場面の間のログインし直しはしない。設計どおり）。

## Step 19: 文書

- `README.md` の3か所を書いた。(1) 新しい節「グループの管理の API（Intent 261004-role-menu の U3）」（「利用者の管理の API」の後、7つの口と成功・主な失敗の表、名前・メンバー・削除・本文・同時の操作（行 3 秒、一意の鍵・主キー 約 2 秒、同じ名前の重なりで `GROUP_NAME_DUPLICATE` と `GROUP_BUSY` に分かれること）・監査・指標）、(2)「スキーマの変更（Flyway）」に V10、(3)「監査ログ（U4）」にグループの5つの種類・失敗の理由・列の使い方・detail のキー・記録しないもの・ERROR の項目と、V10 の3列。
- 書く前に、表と列の名前は `V10__u3_group.sql`、種類と理由は `AuditEventType`・`AuditFailureReason`、code は `GroupProblemTypes`、応答の項目は `group/web` の record、ERROR の項目は `AuditEventListener`、確かめの範囲は `AuditMigrationCompatibilityIT` の `@DisplayName` で確かめた（`project.md` の学び）。
- 設計の文書（承認済み）は書き換えていない。
- README に書いた振る舞いの差: 停止中の管理者は 401（既存の `/api/admin/**` の決まり）と書いた（Step 15 の差と同じ）。1つ前の版を V10 の後の内部DB で起動する確かめは配備の段で扱うと書いた。

## Step 20: 取り残しと変えないものの確かめ

- `git diff --stat develop -- backend/src/main/resources/application.yaml backend/build.gradle.kts build.gradle.kts gradle/libs.versions.toml backend/gradle.lockfile .github compose.yaml docker Dockerfile frontend vendor backend/src/main/java/cherry/mastersmith/common backend/src/main/java/cherry/mastersmith/audit/repository backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`: 差なし。同じ範囲に git の追跡の外の新しいファイルも無い（`git status --porcelain --untracked-files=all -- <同じ一覧>` が空）。
- 既存の境界テストで差があるのは `user/UserBoundaryArchitectureTest.java` だけ（11節 Q2: A の1か所。+7 −2）。
- `packagesJudgedByTotal` は 7 パッケージのまま（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。計測の除外は `backend/build.gradle.kts` に差が無いため変わっていない。`application.yaml` の `org.hibernate.orm.jdbc.error: OFF` もそのまま。
- 新しいテストのクラスは 37（git の追跡の外の `*Test.java`・`*IT.java`）。`testsupport` の手伝いを除き、名前が `Test` か `IT` で終わらないテストのファイルは無い。`GroupNameProperties` の名前は `backend` に残っていない（新しいファイルが追跡の外のため `git grep` ではなく `grep -rn` で確かめた）。jqwik の性質のテストは `group/domain/GroupNamePropertyTest.java`。
- 本体（`backend/src/main`）に名前が `Properties` で終わる新しいクラスは無い。
- 新しい依存と lockfile の変更は無い（`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/` に差なし）。
- `TODO(B5)` は1か所のまま。

## Step 21: 記録

- 1ファイルずつ書いて保存した: `code-summary.md`（作ったもの、実装で決めたこと、計画との差 G-1〜G-23、確かめたいこと、後に回すこと（Q5 の SQLState と制約の名前、R-09 のコメント、`RoleGroupDeletionGuard` の B5 での置き換え）、`curl` の手順。3.1・3.2 は Step 22・23 の後に書き足す）→ `source-manifest.json`（アプリのソースの道 124 件。`aidlc/` の外の変更と新しいファイルのすべて）→ `traceability.json`（118 件: AC 29・BR 48・NFR 41。OK 94・Deferred 15（AC2.2.x の 14 件は U4・U6、NFR5.4 は Observability Setup）・N/A 9（NFR1.5・NFR2.2・NFR2.4・NFR3.2・NFR4.1〜NFR4.3・NFR6.2・NFR6.3、理由つき。NFR 設計の R-07）。target に書いたテストのファイルがすべてあることを確かめた）。

## Step 22: 1コマンドの検査

- colima は動いていて（`colima status`）、VM からリポジトリが見える（`colima ssh -- ls <リポジトリ>/docker`）。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（08:39:58〜08:51:43、11 分 44 秒、BUILD SUCCESSFUL、1回目で通過）。
- 件数: バックエンドの単体 201 クラス・1,734 件、結合 166 クラス・829 件、失敗 0・飛ばし 0（対象DB の3種類のテストの SKIPPED 0 件）。ログに「コンテナの実行環境」の警告 0 件。画面 113 ファイル・1,043 件 passed（全体の行 97.36%・分岐 92.68%）。単体テストの結果に `GroupNamePropertyTest` がある。
- 時間: Step 3 の基準（テストとカバレッジだけ、8 分 38 秒）とは中身が違うため直接は比べられない。前の Bolt の `verify` は B1 が 9 分 41 秒、B2 が 13 分 56 秒。
- カバレッジ（Step 3 → Step 22）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `group.domain`（新しい） | 97.0%（159/164） | 89.7%（87/97） |
| `group.repository`（新しい） | 100.0%（8/8） | 分岐なし |
| `group.store`（新しい） | 97.8%（91/93） | 94.6%（35/37） |
| `group.service`（新しい） | 95.7%（309/323） | 80.4%（82/102） |
| `group.web`（新しい） | 96.6%（84/87） | 87.5%（21/24） |
| `role.service`（新しい） | 100.0%（8/8） | 100.0%（2/2） |
| `audit.domain` | 99.6% → 99.7%（324/325） | 98.4% → 96.8%（91/94） |
| `audit.service` | 100.0% → 91.3%（189/207） | 82.5% → 73.1%（38/52） |
| `user.domain` | 99.6% → 99.6%（265/266） | 98.0% → 98.0%（147/150） |
| `user.service` | 99.7% → 99.7%（385/386） | 95.7% → 95.8%（137/143） |
| `useradmin.domain` | 100.0% → 100.0%（82/82） | 100.0% → 100.0%（52/52） |
| `useradmin.web` | 98.8% → 98.8%（83/84） | 93.8% → 93.8%（15/16） |
| 全体 | 98.7% → 98.3%（7,486/7,618） | 94.6% → 93.7%（2,789/2,978） |

- `audit.service` は下限（行 80%・分岐 70%）の内だが、分岐が 82.5% から 73.1% に下がり下限に近い（`AuditEventListener` の `roleGroupFields` の値の有無の枝と、失敗の ERROR の項目の組み立ての枝の一部をテストが通らない）。承認の場で伝える。

## Step 23: E2E

- Mailpit（`mastersmith-mailpit-1`）はすでに動いていて healthy だったため、`docker compose --profile mail up -d mailpit` は流さなかった（止める・作り直すことを避けた）。配備したアプリ・見本の対象DB にも触れていない。
- 流す前に `frontend/playwright-report`・`frontend/test-results` が無いことを確かめた。
- `caffeinate -i ./gradlew e2eTest`（08:52:26〜08:59:50、exit 0）: 14 ファイル・177 件 passed（7.3 分。skipped 0・unexpected 0・flaky 0）。
- 報告の確かめの道具の出力: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・確かめたファイル: json の報告 1・json の報告の添付 299・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。
- 終わった後に `rm -rf frontend/playwright-report frontend/test-results` で消した（HTML の報告は作られない設定のため、消したのは `test-results` だけ）。
- `code-summary.md` の 3.1・3.2 に Step 22・23 の実測を書き足した。Step 24（コミットの提案）は指揮役が行うため、ここで止める。

## 依頼者の答えを受けた追加（Step 22 の後）

指揮役から依頼者の答えが届いた: G-14 は A（401 を受け入れる）、G-4 は A（この範囲でよい）、G-19 は A（配備の段の戻しの練習の合格の条件に入れる）、`audit.service` の分岐 73.1% は A（この Bolt でテストを足して戻す）。

- `backend/build/reports/jacoco/test/jacocoTestReport.xml` の `audit/service` の行ごとの値で、通っていない分岐を洗い出した。今回の変更で増えたものは、`AuditEventListener.fields(GroupAuditEvent)`（グループの出来事の組み立てに失敗したときの項目。出来事が無い・成功か失敗か・対象の利用者の有無の3つの枝、全体が通っていなかった）と、`roleGroupFields` の `targetRoleId` の有無の枝。ほかの通っていない枝（招待などの既存の出来事の「出来事が無い」の枝 7 つ）は変更の前からのもの。
- `audit/service/AuditGroupEventListenerTest` に3件を足した（出来事が無いとき、detail が 16,384 文字を超えるメンバーの外しの成功、detail が長すぎる削除の拒否。どれも追記せず ERROR を1回で、項目を確かめる）。本体のコードとカバレッジの除外は変えていない。`targetRoleId` が値を持つ枝は、B3 では送り手が無く本体を変えないと通せないため、U4 に引き継ぐ（`code-summary.md` 8節）。
- `./gradlew :backend:spotlessApply :backend:test --tests '…AuditGroupEventListenerTest'` → 7 件通過。
- colima の共有を確かめ（`colima ssh -- ls <リポジトリ>/docker`）、環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流し直した（21:03:19〜21:14:06、10 分 46 秒、BUILD SUCCESSFUL）。
  - 単体 201 クラス・1,737 件、結合 166 クラス・829 件、失敗 0・飛ばし 0。「コンテナの実行環境」の警告 0 件。画面 113 ファイル・1,043 件 passed。
  - `audit.service`: 行 91.3% → 99.5%（206/207）、分岐 73.1% → 84.6%（44/52。変更の前は 82.5%）。全体: 行 98.5%（7,503/7,618）・分岐 93.9%（2,795/2,978）。ほかのパッケージの値は1回目と同じ。
- E2E はバックエンドの単体テストの追加だけのため流し直していない（指揮役の指示）。
- `code-summary.md`（3.1 の値、3.2 の追加、5節を確定の答えに、8節の引き継ぎ）、`source-manifest.json`、`traceability.json` を直した。
