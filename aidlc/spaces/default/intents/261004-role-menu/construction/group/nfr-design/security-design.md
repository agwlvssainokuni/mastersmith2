# セキュリティの設計 — U3 group

## 出典

- この単位の承認済みの NFR 要件 `construction/group/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.4・NFR1.6〜NFR1.11）と、NFR 要件の読み直し（R-03・R-04）
- この単位の承認済みの機能設計（`functional-spec.md`・`rules.md` の BR2・BR8・BR9）
- `contract-summary.md`（C1・C6・C10）、`components.md`（GroupManagement）
- この段の答え: `nfr-design-questions.md` の Q3: A（違反・上限切れを起こしうる書き込みと排他を TraceAspect の対象の外にまとめる）とまとめの確認
- 捨ての試しの結果（`reliability-design.md` の1節。T6・T7）
- コード: `common/observability/TraceAspect.java`、`backend/src/main/resources/application.yaml`（`exception-message`・`org.hibernate.orm.jdbc.error: OFF`）、`invitation/lock/InvitationLockQueriesImpl.java`

## 1. 守りの層（深さで守る）

| 層 | 守り | 要件 |
|---|---|---|
| 入口（Spring Security） | 既存の `AdminPaths` と管理者の印・停止の判定。`/api/admin/groups` の下は管理者だけ | NFR1.1・NFR1.2 |
| 分類の印 | 7つの口すべてに `ApiAccess(ADMIN)`。U1 の構造の検査と実行時の検査が網羅を確かめる | NFR1.3 |
| web（DTO） | 要求の DTO は `name` だけ・`userId` だけの record。ほかの項目は型に無いため読まれない。道の `groupId` と本文の `userId` は long で受け、0 以下も存在しない ID として扱う | NFR1.4 |
| service | 業務の判定（存在・重なり・使用中・変えるものが無い）を結果の型で返す。個人に関する値は伏せ字の型のまま渡す | NFR1.4・NFR1.6 |
| store（TraceAspect の外） | 排他と違反を起こしうる書き込みをまとめ、例外を中で区分に変える。ログには例外のクラスの名前だけ | NFR1.8 |
| audit | `detail` は決めた型からだけ作る。メールアドレス・氏名・秘密を型に持たない | NFR1.7 |

## 2. 認可とテスト（NFR1.1〜NFR1.3）

- 判定は既存の入口の決まりだけで行い、group に `SecurityRuleContributor` は足さない。ロール・作業ロールは判定に使わない。
- `GroupAdminAuthorizationApiIT` は、7つの口 × 4つの主体（未認証・管理者の印を持たない利用者・管理者・停止中の管理者）の 28 行を、パラメーターを使う表で書く。403 の後は対象の行（グループ・メンバー）を読み直して変わらないことを確かめる。
- **B3 と B5 の差**（NFR 要件の読み直しの R-03）: `team.md` は、403 を「要る権限だけを欠く利用者（ほかの権限は持つ）」で確かめるとしている。しかし B3 の時点ではロールと作業ロールが無いため、「管理者の印を持たない利用者」で確かめる。B5 で、全スキーマを FULL にしたロールを作業ロールにする「管理者の印だけを欠く利用者」の行を、7つの口のすべてに足す（U4 への引き継ぎ、`logical-components.md` の6節）。B3〜B4 の間はこの差が残る。

## 3. 一括代入と IDOR（NFR1.4）

- 要求の DTO は `GroupNameRequest(String name)`・`MemberAddRequest(Long userId)` の record だけで、余分な項目は Jackson が型に当てずに捨てる（既存の設定どおり）。`GroupAdminMassAssignmentIT` で、ID・作成の時刻を足しても名前だけが変わることを確かめる。
- 道と本文の ID が 0 以下・存在しないときは、`group/store` の排他の読み取り（行が無い）か `user.service` の有無の確かめで「無い」となり、`GROUP_NOT_FOUND`・`USER_NOT_FOUND`（404、変える操作は監査に FAILURE）にする。400 にはしない。ダミーの行に触れない。`GroupAdminIdorApiIT` に 0・負の値・存在しない ID・招待中の人（利用者の行が無い）を入れる。

## 4. 漏えいの守り（NFR1.6〜NFR1.9）

### 4.1 例外の文の経路（Q3: A）

- 捨ての試し（`reliability-design.md` の T6）で、一意・主キー・外部キーの違反の文と、行の排他の上限切れの連なりの奥に、行の値（名前・ID）が入ることを確かめた。TraceAspect は `web`・`service`・`domain`・`repository` の方法から投げられた例外の文を TRACE に出す（`exception-message` の既定が `$[exception]` を含む）。
- そのため、排他と、違反を起こしうる書き込み・flush（作成・名前の変更・メンバーの追加・外し・削除）は、TraceAspect の対象の外の用途名の下位パッケージ `cherry.mastersmith.group.store` に1つの部品 `GroupStore` としてまとめる。`EntityManager` を直接使い、例外を中で受けて結果の型（`reliability-design.md` の 2.2）に変えて返す。既存の `invitation/lock` と同じ形。
- Spring Data の書き込みの方法（`save`・`saveAndFlush`・`delete`・`@Modifying` の問い合わせ）は、グループの表とメンバーの表に対して置かない。読み取り（一覧・詳細・所属・数）だけを `group.repository` の Spring Data に置く。読み取りは違反を起こさず、問い合わせの時間切れは想定外として扱う。
- 想定外の例外は、クラスの名前だけを持つ例外に包み直して投げる（元の連なりの文を持ち出さない）。
- **構造の検査**（`GroupBoundaryArchitectureTest`）で、次を確かめる。
  - `group.service` から `EntityManager` を使わない。
  - `group.repository` に書き込みの方法（`save`・`delete`・`@Modifying` の付いた方法）を置かない。
  - `group.store` を使ってよいのは `group.service` だけ。
- Hibernate のロガー `org.hibernate.orm.jdbc.error` は、違反の文（値つき）をアプリへ渡す前に WARN で出す（T6）。本番の `application.yaml` は OFF（261003-user-admin-followup の FR8.2）のままにし、`GroupUniqueViolationSecretLeakIT` で、違反の後のログに名前と ID の値の文が無いことを確かめる（この設定が消されると落ちる）。

### 4.2 個人に関する値（NFR1.6）

- メンバーの氏名・メールアドレスは、`user.service` に足すまとめて読む口が、伏せ字の型（既存の `RedactedText`・`DisplayName`・`EmailAddress` の型、または toString を伏せた record）で返す。`group.service` の戻り値の `GroupDetail`・`GroupMember` も toString で伏せる。値を取り出すのは web の層で応答の DTO を作るときだけ。
- `GroupSecretLeakIT`（TRACE を有効）で、詳細を読み・メンバーを足し外ししても、アプリのログにメールアドレス・氏名が0件であることを確かめる。

### 4.3 監査と応答（NFR1.7）

- 監査の `detail` は `GroupAuditDetail` の4つの形（Name・Rename・Membership・InUse）から、決めたキーだけの JSON にする。グループの名前は個人に関する値として扱わない（NFR 要件のまとめの確認で受け入れ）。
- 既存の `AuditSecretLeakIT` の列の一覧に `target_role_id`・`target_group_id`・`detail` を足し、グループの操作の後の行に秘密・メールアドレス・氏名が無いことを確かめる。
- 応答の DTO はパスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を型に持たない。

### 4.4 静的解析（NFR1.9）

- `GroupStore` の問い合わせは JPQL の名前の付いた引数だけで組む。SpotBugs の `SQL_` の関門を通る。

## 5. 受け入れた制約（NFR1.10、NFR 要件の読み直しの R-04）

- 要求の入口で管理者の印を確かめた後、書き込みまでの間に別の管理者に印を外されても、その1件の要求は通る。グループの操作は管理者の印・停止を変えず、次の要求からは入口で拒否されるため、この窓を受け入れる（NFR 要件のまとめの確認）。検証の対象（テスト）には置かない。要件の網羅の連鎖では、この節を受け入れの記録として指す。

## 6. 名前の入力（NFR1.11）

- `GroupName` が前後の空白を取り除き、1〜64 コードポイント、制御文字を拒否し、鍵を言語に依らない小文字化で作る。正規化と鍵の作りは `GroupName` の1か所だけ（機能設計の R-04 の直し）。jqwik の性質ベースのテスト（`GroupNameProperties`）を当てる。
