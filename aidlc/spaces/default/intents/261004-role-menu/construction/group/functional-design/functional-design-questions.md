# 機能設計の質問 — U3 group

対象の単位: U3 group（kind: service）。グループの作成・名前の変更・削除、メンバーの足し外し、メンバーの読み取りの口、削除してよいかを問う口（インターフェース）の定義、管理の API（`/api/admin/groups` の下）、グループの監査の種類と出来事、監査の表と `AuditEvent` の変更（列・移行・`detail`、契約 C10 で U3 の持ち物）、`GroupBoundaryArchitectureTest`。作るのは B3（B1 dsl-v2・B2 cross-cutting の後、B4〜B6 の U4 role の前）。

質問は 6 問です。上流・承認の場・`team.md`・`project.md`・コードで決まっている点は、下の「決まっていること」に書き、質問にしていません。

---

## 決まっていること

### 業務の決まり（要件・ストーリー・ADR）

- 管理者（管理者の印を持つ利用者）だけが、グループの作成・名前の変更・削除と、メンバーの足し・外しを行える（FR4.1、FR11.1、要件 C1）。ロールは管理の権限を与えない（stories.md の「ロールは管理の権限を与えない」）。
- 同じ名前のグループは作れない。名前の規則（長さ・大文字と小文字・使える文字）は FR1.1（ロール）と同じ考え方で機能設計で決める（FR4.1。Q1）。名前が空・空白だけ・上限を1文字超えるときは拒否し、上限ちょうどは受け付ける（AC2.1.2）。
- メンバーかロールの割り当てが1つでも残っているグループは削除できない。拒否は業務の誤り（4xx）で、グループ・メンバー・割り当ては変わらない（FR4.1a、AC2.1.3）。拒否の応答で残りの数（メンバー・ロール）が分かる（AC2.1.7、契約の `GROUP_IN_USE` の `members`・`assignedRoles`）。
- 削除の確かめのうちロールの割り当ての有無は、`group` の service の層が定義する「削除してよいかを問う口」を `role` が実装して答える。`group` は `role` を知らない。実装が無いときに「削除してよい」と答える既定は置かない（ADR-002、契約 C4）。B3 の時点でどう起動させるかは Q4。
- メンバーに足せるのは登録の終わった利用者（`users` の行がある人）だけ。招待中の人は利用者の行が無く、足せない・候補に出ない。利用停止の利用者は足せる（AC2.1.11、components.md の GroupManagement、要件との差 D7）。
- 変えるものが無い操作（今と同じ名前への変更・すでにメンバーの利用者の追加・メンバーでない利用者の外し）は、業務の誤りとして拒否し、監査に残す（stories.md の SM4 C、AC2.1.13）。
- メンバーを外すと、その人のグループ経由のロールが次の要求から外れる。作業ロールの読み替えは U4 の解決の口が受け持ち、U3 は何もしない（FR4.3・FR4.4、AC2.1.9・AC2.1.10、契約 C5）。
- 削除と、メンバー・ロールの割り当ての追加が同時に重なっても、終わった後に「消えたグループにメンバー・割り当てが残る」「メンバー・割り当てが残るのにグループが消える」のどちらも起きない。負けた側は業務の理由の code の 4xx で返り、500 にならない。重なりはスレッドの数に頼らず待ち合わせで作って確かめる（AC2.1.5、NFR3.1、`team.md`）。守り方は Q2。
- グループ・メンバー・割り当ての YAML の受け渡しは範囲の外（RF2）。

### API（契約 C6）

- 道と状態コードは契約 C6 のとおり: `GET /api/admin/groups`（一覧 200）、`POST /api/admin/groups`（作成 201、本文は作ったもの）、`GET /api/admin/groups/{groupId}`（詳細 200）、`PUT /api/admin/groups/{groupId}`（名前の変更 204）、`DELETE /api/admin/groups/{groupId}`（削除 204）、`POST /api/admin/groups/{groupId}/members`（本文 `{userId}`、204）、`DELETE /api/admin/groups/{groupId}/members/{userId}`（204）。
- 一覧は `{ items, page, size, total }`、行は `groupId`・`name`・`memberCount`・`assignedRoleCount`。詳細は `groupId`・`name`・`members`（`userId`・`displayName`・`email`・`suspended`）。グループに割り当てたロールの一覧は U4 の `GET /api/admin/groups/{groupId}/roles`（契約 C7）が答え、U3 は持たない。
- メンバーの候補の検索は既存の利用者の管理の一覧（`GET /api/admin/users`）を使い、U3 は候補の API を足さない（契約 C6 の notes）。
- 拒否の code と状態コードは契約の表のとおり: `GROUP_NOT_FOUND`（404）・`GROUP_NAME_DUPLICATE`（409）・`GROUP_IN_USE`（409）・`GROUP_NO_CHANGE`（409）・`USER_NOT_FOUND`（404）。入力の誤りは既存の `VALIDATION_FAILED`（400）、未認証は `AUTHENTICATION_REQUIRED`（401）、管理者でない・停止中は `ACCESS_DENIED`（403）を使い回す（`team.md` の Code Style）。
- 管理の API はすべて、U1 の注釈 `ApiAccess` に値 `ApiAccessLevel.ADMIN` を付ける（契約 C1、`aidlc/spaces/default/intents/261004-role-menu/construction/cross-cutting/functional-design/functional-spec.md` の BR1）。`/api/admin/**` は既存の `access.domain.AdminPaths` と管理者の印の決まりで守られるため、`SecurityRuleContributor` は足さない。
- 認可は表のパラメーターのテストで、未認証 401・管理者の印だけを欠く利用者（ロールと作業ロールを持つ）403・管理者 200 などの成功・停止中の管理者は通らない、を API ごとに確かめる（AC2.1.6、NFR1.2、stories.md の「認可」の読み方）。
- 一括代入の防止: 作成・名前の変更の本文は `name` だけを受け、ほかの項目（ID・作成の時刻など）を足しても反映しない。メンバーの追加の本文は `userId` だけ（`team.md` の役割・権限の必須のテスト、AC1.1.10 と同じ形）。

### 契約 C4（U3 → U4 の口）

- `group.service` に読み取りの口 `GroupMembershipQuery`（`groupIdsOfUser(userId)`・`exists(groupId)`・`summaries(groupIds)`）を出す。U4 が利用者のロールの和を求めるのに使う。
- `group.service` に `GroupDeletionGuard`（`canDelete(groupId)` → `Allowed` か `Blocked(assignedRoles)`）を定義し、U4 が実装する。口を広げるかは Q3。

### 監査（契約 C10、FR12）

- 監査の出来事はほかの機能と同じく Spring の出来事で出し、`audit` が確定の後に別のトランザクションで記録する（今の `AuditEventListener` の形）。`audit` は `group.domain` の出来事の型に依存し、`group` は `audit` に依存しない（今の `useradmin` と同じ向き）。
- 監査の種類は契約 C10 の名前のとおり `GROUP_CREATED`・`GROUP_RENAMED`・`GROUP_DELETED`・`GROUP_MEMBER_ADDED`・`GROUP_MEMBER_REMOVED`（いずれも 32 文字以内。一度決めたら変えない）。
- U3 が持つ監査の表の変更: `audit_events` に `target_role_id`・`target_group_id`・`detail` の列を足す（空を許す、参照の制約と索引は置かない。前進のみで、前の版のアプリが動く形）。`AuditEvent` に同じ項目とロール・グループ向けのファクトリーを足す。`detail` の形・上限・伏せる規則を決め、漏えいのテストを置く（契約 C10 の audit-changes-owner）。形と上限は Q6。
- 監査の行と出来事に、パスワード・トークン・ハッシュ値を含めない（FR12.3、`project.md` の Forbidden）。

### 漏えいと TRACE のログ（`team.md`・`project.md`）

- 詳細の応答のメンバーのメールアドレス・氏名は個人に関する値で、`TraceAspect` が web・service・domain・repository の引数と戻り値を TRACE に出すため、すべての経路で伏せ字になる型で受け渡す（既存の `UserSummary` は `toString` で伏せている）。応答に、パスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない。TRACE を有効にした `GroupSecretLeakIT`（仮の名前）で、メンバーのメールアドレス・氏名がアプリのログに出ないことを確かめる（AC2.1.12、NFR1.6、FR4.5）。
- 一意の制約の違反などの例外の連なり（H2 の例外の文）には行の値が入りうるため、例外は `TraceAspect` の対象の層の外へ出さず、受けた所でクラスの名前だけをログに出す（`project.md` の学び。既存の `InvitationUniqueViolationSecretLeakIT`・`UserUniqueViolationSecretLeakIT` と同じ形）。

### 作りの決まり（`team.md` の Code Style）

- パッケージは `cherry.mastersmith.group` の下に `web`・`service`・`domain`・`repository`。トランザクションの境界は service の層だけ。DTO は `record` の `XxxRequest`・`XxxResponse`。コンストラクター注入のみ。
- 業務処理の層は想定内の失敗を結果の型（sealed interface の record）で返し、web の層が `switch` で場合を尽くして `BusinessException` に変える。code の定数は `group.domain.GroupProblemTypes`、起動時に集める一覧は `group.service.GroupProblemTypeCatalog`。
- `group` のテストに `GroupBoundaryArchitectureTest` を置き、`group` が依存してよい機能（`user` の service と domain、`common`）と、`group` に依存してよい機能（`role`・`audit`）を書く。既存の境界テストは緩めない（`useradmin` の境界テストには「`useradmin` の外で `useradmin` に依存してよいのは `audit` だけ」がある）。
- テストの手伝い（待ち合わせの口・問う口のテスト用の実装など）は `src/test/java/cherry/mastersmith/group/testsupport` に置く。同時の重なりの待ち合わせは、既存の `UserAdminBarrier`（本番は何もしない `NoOpUserAdminBarrier`、テストは `@Primary` の `TestUserAdminBarrier`）と同じ形にする。

### カバレッジとほかの機能への手入れ（`team.md` の Testing Posture）

- B3 で本体（`src/main`）に手が入る見込みのパッケージは、`group.*`（新しい）・`audit.domain`（`AuditEvent`・`AuditEventType`・`AuditFailureReason`・ファクトリー）・`audit.service`（`AuditEventListener` の受け取り）と、下の USER_NOT_FOUND の移し替えで `user.domain`・`useradmin.domain`。どれも `packagesJudgedByTotal`（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）に入っていない。`AuditEventRepository` は `save` で足りるため `audit.repository` には手を入れない見込みで、カバレッジの下限の作業は付かない見込み（手を入れることになったら下限を満たして一覧から外す）。
- `audit` には `AuditBoundaryArchitectureTest`、`useradmin` には `UserAdminBoundaryArchitectureTest` がすでにある。`user` の境界テストは B2（U1）で足される（`aidlc/spaces/default/intents/261004-role-menu/construction/cross-cutting/functional-design/functional-design-questions.md`）。
- 既存の `AuditSecretLeakIT` は監査の表の列の一覧を確かめているため、3列を足すと必ず食い違う。B3 で列の一覧に足す（前の Intent の学び。計画に明記する）。

### Flyway と戻し（`team.md` の Deployment）

- 今の移行の最後は `V9__u1_user_suspension.sql`。B3 の移行は V10 から（グループの表とメンバーの表、監査の表の列）。表と列を足すだけで、既存の列は消さない。`spring.jpa.hibernate.ddl-auto: validate` は余分な列・表を許し、Flyway は既定で前の版が知らない新しい移行（future）を無視するため、1つ前の版のアプリは今までどおり起動する見込み（戻しの確かめは配備の段）。

---

## この段で決める設計の要点（質問にしない案。まとめの確認で確かめる）

- **USER_NOT_FOUND の置き場**: 既存の code `USER_NOT_FOUND`（404）は `useradmin.domain.UserAdminProblemTypes` にある。`ProblemTypeRegistry` は同じ code の二重の定義で起動を止め、`UserAdminBoundaryArchitectureTest` は `audit` の外から `useradmin` への依存を禁じるため、`group`（と後の U4）はそのまま使えない。そこで定義を利用者の持ち主の `user.domain.UserProblemTypes` へ移し（code・状態コード・文言は変えない）、`useradmin` と `group`（U4 も）がそれを使う。一覧に入れるのは `user.service.UserProblemTypeCatalog` だけにする。境界テストは緩めない。
- **一覧のページ送り**: 既存の共通の `common.paging.Paging`（`page` だけを受け、1ページ 20 件に固定）に合わせる。契約 C6 の `parameters: [page, size]` の `size` は受け取らず、応答の `size` は 20 を返す（利用者の管理の一覧と同じ）。契約との差として成果物に書く。並びはグループの ID の順（作った順。利用者の一覧が作った順なのとそろえる）。`page` の誤りは既存の一覧と同じ扱い（`VALIDATION_FAILED`）。
- **詳細のメンバーの並び**: 足した順（`addedAt`、同じなら利用者の ID）。メンバーはページ送りせず全件を返す（NFR2.1 の利用者 50 名程度の前提。画面の候補で「メンバー済み」を示すのにも全件が要る）。
- **拒否の判定の順**: 名前の変更は 入力（名前の規則）→ グループの有無 → 変えるものが無い → 名前の重なり。メンバーの追加は グループの有無 → 利用者の有無 → すでにメンバー。メンバーの外しは グループの有無 → メンバーでない（存在しない利用者もメンバーでないとして `GROUP_NO_CHANGE`。契約 C6 の外しの応答に `USER_NOT_FOUND` は無い）。削除は グループの有無 → メンバーの数と問う口の答え（どちらかが 0 でなければ `GROUP_IN_USE`。数を返すため、メンバーが残っていても問う口に聞く）。
- **作成・追加の同時の重なり**: 同じ名前の作成が重なったときは名前の一意の制約で1つだけを通し、制約の違反を `GROUP_NAME_DUPLICATE` に読み替える。同じメンバーの追加が重なったときは主キー（グループと利用者の組）の違反を `GROUP_NO_CHANGE` に読み替える。どちらも違反の例外の文を外へ出さない。
- **操作した人の確かめ直しはしない**: 利用者の管理（`useradmin`）は管理者の印を変える操作のため、トランザクションの中で操作した人を確かめ直した。グループの操作は管理者の印を変えないため、要求の入口の既存の判定（管理者の印・停止の確かめ）だけとする。
- **表**: `groups`（ID・名前・作成と更新の時刻）と `group_members`（グループの ID・利用者の ID・足した時刻。主キーは組。グループと利用者へ外部キー）。保存する時刻は UTC の時点（`project.md` の学び）。グループの名前の一意は Q1 の答えに合わせて作る（大文字と小文字を区別しないなら、小文字にした名前の生成列に一意の制約。既存の `invitations.pending_email` と同じ作り）。
- **監査の失敗の理由**: `audit_events.failure_reason` は列挙 `AuditFailureReason`（32 文字の列）。Q5 で残すと決めた拒否の理由を足す（例: `GROUP_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`。`USER_NOT_FOUND`・`NO_CHANGE` は既存の値を使う）。
- **監査の行の対象**: メンバーの追加・外しは `target_group_id` と `target_user_id` を埋める。そのほかは `target_group_id`。削除の後もどのグループだったか分かるよう、作成・名前の変更・削除の `detail` にグループの名前を入れる（Q6 の形で）。メールアドレス・氏名は `detail` に入れない（対象の利用者は `target_user_id` で分かる）。
- **業務のログ**: グループの操作は業務のログを出さない（監査が記録。利用者の管理と同じ）。想定外の失敗だけを例外のまま投げ、既存の `@RestControllerAdvice` が ERROR で1回出す。

---

## Q1 グループの名前の規則

背景: FR1.1・FR4.1 で「大文字と小文字の扱い・長さの上限・使える文字は機能設計で決める」とされ、U4 のロールの名前も同じ考え方でそろえる予定です（ここでの答えを U4 の質問の推奨にします）。グループの名前は管理者が入れる値で、画面にはそのまま出し（訳さない）、監査の `detail` にも入ります。

A. 前後の空白（全角の空白を含む）を取り除いてから判定・保存する。長さは取り除いた後で 1〜64 文字（コードポイントで数える）。改行・タブなどの制御文字を含む名前は拒否する。重なりは大文字と小文字を区別せずに判定する（「Sales」と「sales」は同じ名前。自分自身の大文字・小文字だけの変更は受け付ける）。全角と半角は別の文字として扱う（推奨: 画面の一覧で見分けにくい重なりを防ぎ、空白の付け忘れ・付けすぎで別の名前ができない。上限 64 は表の幅と監査の `detail` の大きさに収まる）
B. A と同じだが、重なりは大文字と小文字を区別する（「Sales」と「sales」は別の名前。DB の一意の制約だけで足りる）
C. 前後に空白がある名前は取り除かずに拒否する。長さ・制御文字・大文字と小文字は A と同じ
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 削除と、メンバー・ロールの割り当ての追加の同時の重なりの守り方

背景: 契約 C4 の未決（要件の確かめの R-08、AC2.1.5）。組み込みの H2 で、削除の確かめ（メンバー 0・割り当て 0）と、別の管理者のメンバーの追加（U3）・グループへのロールの割り当て（U4、B5）が重なる場合です。前の Intent の最後の管理者の保護は、行の排他（待ちの上限 3 秒、上限切れは 409 の `USER_ADMIN_BUSY` で巻き戻し、監査なし）と待ち合わせのテストで守りました。どちらの案も、H2 の振る舞い（排他の待ち・外部キーの待ち・上限切れの後の巻き戻し・例外の文に入る値）を NFR 設計で捨ての試しのコードで先に確かめます（`project.md` の学び）。

A. グループの行の排他を順番の決め手にする。削除・名前の変更・メンバーの追加・外しは、最初にグループの行を排他してから確かめと書き換えを行う。U4 のグループへのロールの割り当て・外しも、`group` が C4 に足す口（例: 「このグループを排他して有無を返す」）で同じ行を排他してから行う。排他の待ちの上限切れは新しい code `GROUP_BUSY`（409、巻き戻し、監査なし。利用者の管理と同じ扱い）。外部キー（メンバー・割り当てからグループへ）は壊れた行を作らないための最後の守りとして置く（推奨: 利用者の管理と同じ形で、どの経路でも順番が1か所で決まり、勝ち負けが確かめやすい）
B. 外部キー（削除の制限）だけで守る。排他の口は足さない。削除は確かめの後に行を消し、同時に入ったメンバー・割り当てによる外部キーの違反を受けたら `GROUP_IN_USE` に読み替える。追加の側は、消えかけのグループを指す外部キーの待ち・違反を `GROUP_NOT_FOUND` に読み替える（排他の口と `GROUP_BUSY` が要らない代わりに、勝ち負けが H2 の外部キーの待ちの振る舞いに頼り、U4 の割り当ての表もグループへの外部キーが必須になる）
C. 削除だけを1つの文の条件つきの削除（メンバーが無いときだけ消す）にし、ロールの割り当ての重なりは U4 の割り当ての表の外部キーで守る（`group` は `role` の表を知らないため、割り当ての側は B と同じ外部キーの読み替えになる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q3 一覧の「割り当てたロールの数」をどこから得るか

背景: 契約 C6 の一覧の行に `assignedRoleCount` があり、画面 S6 の一覧にも「ロール」の数の列があります。しかしグループへのロールの割り当ては `role` が持ち、`group` は `role` を知りません（ADR-002）。今の契約 C4 の問う口は `canDelete(groupId)` の1件ずつで、一覧の 20 件の数を求める口がありません。

A. `group` が定義し `role` が実装する口に、数を求める方法を足す。削除の問う口 `GroupDeletionGuard` を「グループのロールの割り当てを問う口」に広げ、`assignedRoleCounts(groupIds)`（グループごとの数をまとめて1回で返す）を足し、`canDelete` もその数で答える（契約 C4 に足すだけの互換の変更。実装は U4 の1つ）（推奨: 依存の向きを変えず、一覧を1回の問い合わせで埋め、問う口が1つで済む）
B. A と同じ数の方法を、`GroupDeletionGuard` とは別の新しい口として足す（責務は分かれるが、`role` が実装する口が2つになる）
C. 一覧の行から `assignedRoleCount` を外し、画面が C7 の `GET /api/admin/groups/{groupId}/roles` をグループごとに読む（契約 C6 を変える。1ページで画面の要求が最大 20 回増える）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q4 B3 の時点の、問う口の実装の置き方

背景: 問う口の実装は U4（B5）で作りますが、B3 の時点ではまだありません。ADR-002・契約 C4 は「実装が無いと起動に失敗する（既定の「削除してよい」は置かない）」と決めています。そのままでは B3〜B4 の間、本番のアプリと、Spring を起動するすべての結合テストが起動しません（`./gradlew verify` が通らない）。bolt-plan の B3 は「B3 の時点はテスト用の実装で確かめる」としています。B3 の時点では割り当ての表がまだ無く、グループに割り当てられたロールは実際に 0 件です。

A. B3 で `role` のパッケージに仮の実装を1つだけ置く（割り当ての表がまだ無いため、数は 0・削除してよいと答える）。B5 でこの仮の実装を本物に置き換え、仮の実装が残っていないことを B5 の終わりの条件に入れる。`group` の側には既定の実装を置かず、ADR-002 の「`group` は既定を持たない」は守る。割り当てが残るときの拒否はテスト用の実装（`group/testsupport`、`@Primary`）で確かめる（推奨: `develop` がどの時点でも起動し、問う口の抜けを起動の失敗で防ぐ決まりも残る）
B. 本番のコードには B5 まで実装を置かず、テストの側だけに実装を置いて、Spring を起動するすべてのテストが読み込む形にする（B3〜B4 の間は本番のアプリが起動しない。配備はすべての Bolt の後のため、その間の `develop` は配備しない前提）
C. ADR-002 を変え、`group` の側に「割り当て 0・削除してよい」と答える既定の実装（実装が別にあれば使わない形）を置く（起動はいつも通るが、U4 の実装の差し込み忘れを起動で防げなくなる。ADR の変更として記録が要る）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q5 業務の理由で拒否した操作を監査に残すか

背景: 変えるものが無い操作（`GROUP_NO_CHANGE`）は残すと決まっています（SM4 C）。それ以外の拒否を残すかは「今の利用者の管理の操作と同じ考え方で機能設計で決める」とされています（FR12.2）。利用者の管理は、業務の理由の拒否（利用者が無い・自分自身・停止中・変えるものが無い・最後の管理者）をすべて失敗として監査に残し、入力の誤りと排他の待ちの上限切れ（BUSY）は残していません。403 は既存の `ACCESS_DENIED` の監査が残ります。U4 のロールの拒否も、ここでの答えにそろえる予定です。

A. 業務の理由の拒否（`GROUP_NOT_FOUND`・`USER_NOT_FOUND`・`GROUP_NAME_DUPLICATE`・`GROUP_IN_USE`・`GROUP_NO_CHANGE`）をすべて、操作ごとの種類で結果 FAILURE と理由を付けて残す。入力の誤り（400 `VALIDATION_FAILED`）と排他の待ちの上限切れ（Q2 で A のときの `GROUP_BUSY`）は残さない（推奨: 利用者の管理と同じ線引きで、存在しない ID を差し替える IDOR の試みも監査に残る）
B. 変えるものが無い操作だけを残し、ほかの業務の拒否は残さない（監査の行は少ないが、存在しない ID の試みや使用中の削除の試みは残らない）
C. A に加えて、入力の誤り（400）も残す
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q6 監査の `detail` の形と大きさの上限

背景: 契約 C10 で、`detail`（変えた中身）の形・上限・伏せる規則は U3 が決め、U4 も同じ列を使います。U3 の中身は小さい（グループの名前、名前の変更の前後、使用中の削除の残りの数）一方、U4 では権限の設定の保存（1つの表の分、最大 100 カラムの前後の値。後勝ちの保存で前後の値を残す SM2）や import（数千の対象になりうる。要約か全件かは U4 が決める）が入ります。伏せる規則は決まっています（パスワード・トークン・ハッシュ値・メールアドレス・氏名を入れない。入れるのは ID・グループとロールの名前・権限の値・数だけ）。

A. JSON の文字列（キーを決めた型の record から作り、自由な Map は受けない）。上限は 16,384 文字。上限を超えそうなときは呼ぶ側（U4）が要約の形（数と先頭の何件か）に切り替え、途中で切って壊れた JSON にはしない。列は文字の列（`VARCHAR(16384)`）（推奨: 1つの表の分の権限の前後の値（目安 8 千文字）が収まり、import は U4 が要約にする前提で上限を固定できる）
B. JSON の文字列で、上限を 4,000 文字にする。権限の設定の保存も U4 で要約（変わった対象の数と先頭の数件）にする（行は小さいが、後勝ちの保存の前後の値を全件は残せない）
C. JSON の文字列で、列を大きな文字の型（CLOB）にして上限を置かない（import の全件も入れられるが、行の大きさと読み出しの重さの上限が無くなる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（group）:

- Q1 A: グループの名前は前後の空白（全角を含む）を取り除いて 1〜64 文字（コードポイント）。制御文字は拒否。重なりは大文字と小文字を区別せずに判定し、全角と半角は別の文字とする。ロールの名前も同じ規則にそろえる。
- Q2 A: 削除・名前の変更・メンバーの追加と外しは、最初にグループの行を排他する。role の割り当ても group が契約 C4 に足す口で同じ行を排他する。待ちの上限切れは新しい code `GROUP_BUSY`（409、巻き戻し、監査なし）。外部キーは最後の守り。H2 の振る舞いは NFR 設計で捨ての試しのコードで確かめる。
- Q3 A: 削除の問う口を「グループのロールの割り当てを問う口」に広げ、グループごとの数をまとめて返す方法を足す（契約 C4 に足すだけの互換の変更。実装は role）。
- Q4 A: B3 で role のパッケージに仮の実装（0 件・削除してよい）を置き、B5 で本物に置き換える（仮の実装が残っていないことを B5 の終わりの条件に入れる）。group には既定の実装を置かない。
- Q5 A: 業務の理由の拒否（グループが無い・利用者が無い・名前の重なり・使用中の削除・変えるものが無い）はすべて FAILURE と理由を付けて監査に残す。入力の誤りと `GROUP_BUSY` は残さない。ロールの拒否もそろえる。
- Q6 A: 監査の `detail` はキーを決めた型から作る JSON の文字列で、上限 16,384 文字。超えそうなときは role が要約に切り替え、途中で切らない。
- 「この段で決める設計の要点」（`USER_NOT_FOUND` を `user.domain.UserProblemTypes` へ移す、一覧は既存の `Paging` に合わせて `size` を受けず 20 件固定（契約 C6 との差として書く）、メンバーは足した順で全件、拒否の判定の順、一意の制約と主キーの違反の読み替え、操作した人の確かめ直しはしない、表の形、監査の失敗の理由と対象、業務のログを出さない）もこのまま設計に入れる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
