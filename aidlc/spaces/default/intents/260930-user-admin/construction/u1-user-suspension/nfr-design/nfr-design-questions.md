# NFR Design の質問 — u1-user-suspension

単位 U1（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 library）の NFR 設計のための質問です。library の単位のため、成果物は `security-design.md`・`logical-components.md`・`traceability.json` の3つです（性能・信頼性の作りは、NFR 要件と同じく `security-design.md` の別の節に置きます）。確かめた資料は、この単位の承認済みの NFR 要件 `construction/u1-user-suspension/nfr-requirements/`（`security-requirements.md` の NFR1.1〜NFR11.2・残る危険 R1〜R3・承認の場の決定 R-01〜R-05 と申し送り、`tech-stack-decisions.md` の NFR9.5・NFR9.6）、NFR 要件の承認の場の依頼者の決定（V7・V8 の移行と後方互換のテストと複写の置き場を消す、業務の確かめは業務のテストへ移す、V9 には移行や戻しを想定したテストを作らない）、機能設計 `construction/u1-user-suspension/functional-design/`（`functional-spec.md` の 2.1〜2.7・6節・7節・承認の場の決定、機能設計のレビューの新しい Minor R-06）、契約 `inception/contract-design/contract-summary.md` の C1・C7、Bolt の計画 `inception/delivery-planning/bolt-plan.md` の B1、今のコード（`backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`・`auth/service/TokenRefreshService.java`・`auth/web/AccessTokenAuthenticationProvider.java`・`auth/repository/LoginAttemptStateRepository.java`・`auth/repository/RefreshTokenRepository.java`・`user/repository/UserRepository.java`・`user/service/UserSummary.java`・`access/domain/AccessDeniedReason.java`・`audit/domain/AuditEventFactory.java`、`backend/src/main/resources/db/migration/` の V1〜V8、`backend/src/main/resources/application.yaml` の `ddl-auto: validate` と `validate-on-migrate: true`、`backend/build.gradle.kts` の `packagesJudgedByTotal`、テストの `user/repository/V7MigrationIT.java`・`V7BackwardCompatibilityIT.java`・`UserSchemaIT.java`、`invitation/repository/V8MigrationIT.java`・`V8BackwardCompatibilityIT.java`・`InvitationSchemaIT.java`、`invitation/service/InvitationConcurrencyIT.java`、`auth/repository/LoginAttemptStateRepositoryIT.java`、`README.md` の V7・V8 の説明）、決まり `aidlc/spaces/default/memory/team.md`・`project.md` です。配備先は開発者の PC 上のコンテナで、この単位に基盤の論点はありません。新しく決める論点は、依頼者の決定で形が変わった移行の確かめまわりの3点です。

## 設計の要点（案）

### 3つの入口の停止の判定（NFR1.1・NFR1.2・NFR2.1〜NFR2.3・NFR5.1）

1. **判定の材料**: `UserSummary` に `suspended` を足し、`UserAccountService` の `verifyPassword`（`findByEmail`）と `findById` が今の読み取りのまま値を載せる。3つの入口はこの要約だけを見るため、内部DB の問い合わせは増えない（NFR5.1）。`UserSummary` の `toString` には真偽をそのまま出す（個人に関する値ではない）。`UserSummary` を作る既存の箇所は、引数が1つ増えるだけで振る舞いは変えない。
2. **ログインの照合（`LoginService#decide`）**: 本人のロックの状態の行を排他つきで読んだ直後、`LockPolicy.decide` の前に `user.suspended()` を見る。停止中なら、今の「存在しないメールアドレス」の枝と同じ形で、読んだ値のまま `attemptRepository.update` を1回呼び、`LOGIN_FAILED`（`ACCOUNT_SUSPENDED`）を1件知らせ、`Decision.FAILED` を返す。`update` は今と同じく明示の更新の問い合わせのため、同じ値でも更新の文が1回出る（エンティティの変更の検出に頼らない）。行が無いときの `ROW_MISSING` → 行を作る → やり直しの流れは停止の判定より前にあるため、パスワードの誤りと同じ回数になる（NFR2.2）。確かめは `LoginServiceTest` の形（`CountingPasswordEncoder`・`SqlStatementCounter`）で、停止中（正誤・ロック中・行なし）とパスワードの誤りの回数を並べて比べる。
3. **トークンの更新（`TokenRefreshService#refresh`）**: `findById` の直後に `suspended` を見て、今の失敗と同じ例外（401 REFRESH_FAILED）を投げる。例外でトランザクションが巻き戻るため、直前の `revokeIfActive` も戻る（BR3.1）。監査は出さない。
4. **アクセストークンの認証（`AccessTokenAuthenticationProvider#authenticate`）**: `findById` の直後に `suspended` を見て、`TokenAuthenticationException(TokenFailureReason.USER_SUSPENDED)` を投げる。入口と応答（401 AUTHENTICATION_REQUIRED）は今のまま。`AccessDeniedReason` の変換の `switch` に `USER_SUSPENDED -> Optional.empty()`（`TOKEN_EXPIRED` と同じく理由なし）を足し、管理の API でもアクセスの拒否の監査に残さない（NFR2.3）。列挙を尽くす単体テストで変換を確かめる。
5. **監査の理由**: `LoginFailureReason`（`auth.domain`）と `AuditFailureReason`（`audit.domain`）に `ACCOUNT_SUSPENDED` を足し、`AuditEventFactory` の変換に1行足す。`audit_events.failure_reason` は `VARCHAR(32)` で CHECK の制約が無いため、スキーマの変更は要らない（17 文字）。経路はパスワードの誤りと同じ確定の後の2本目の接続で、接続を2本使う新しい経路は足さない（NFR6.1・NFR6.2）。

### C1 の口と永続化の文脈（NFR11.2・NFR5.3・NFR3.2・機能設計の R-06）

6. **`setSuspended`**: `UserRepository` に、停止の列だけを書き換える名前つきの引数の JPQL の更新を足し、`@Modifying(clearAutomatically = true, flushAutomatically = true)` にする（既存の `updatePreferences` などは `clearAutomatically` だけ）。実行の前に未反映の変更を書き出し、実行の後に永続化の文脈を空にするため、後の `isSuspended`・`findById` は内部DB から読み直す（BR1.4）。更新した行が 0 なら `IllegalStateException`。`UserAccountService` の口は `Propagation.MANDATORY`。
7. **`revokeAllRefreshTokens`**: `auth.service` に新しく作る `RefreshTokenRevocationService` が、`RefreshTokenRepository` に足す「利用者 ID で引き、`revokedAt` が空の行だけに今の時刻を入れる」1回の更新（既存の `ix_refresh_tokens_user_id` で引く。`revokeIfActive` と同じ `@Modifying` の属性）を呼び、件数を `RevokeAllResult(int revoked)` で返す。時刻は注入した `Clock`。ログは DEBUG で利用者 ID と件数だけ。
8. **呼び出し元（U3）への約束（R-06）**: 2つの口は呼び出し元の永続化の文脈を空にするため、U3 が口を呼ぶ前に読み込んだエンティティは切り離される。U3 は口を呼んだ後にエンティティの変更を書かず、必要なら読み直す。排他（C8 の行の排他）は内部DB の側で確定まで続くため、文脈を空にしても失われない。この約束を `logical-components.md` の C1 の節に書き、確かめは U3 のコード生成の計画で扱う（申し送りのとおり）。U1 の側の確かめは、同じトランザクションで書いて読むと書いた値が返る結合テスト（NFR9.2・R-05）。

### スキーマの変更 V9（NFR10.1〜NFR10.3）

9. **V9 の書き方**: `V9__u1_user_suspension.sql` に `ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL` の1文だけを置く（H2 は既存の行に既定の値を入れる）。V1〜V8 は書き換えない。エンティティ `User` の `suspended` は `@Column(nullable = false)` で、Java の側でも作るときに false を持たせる（JPA の追記はすべての列を書くため、既定の値に頼らない）。
10. **V9 の確かめ**: 依頼者の決定により、移行や戻しを想定した自動のテスト（V1〜V8 の複写、1つ前の版の Flyway、前の版の追記の形）は作らない。確かめは、起動時の Flyway の `validate-on-migrate` と Hibernate の `ddl-auto: validate`、`UserSchemaIT` に足すテストで行う。何を足すかは（Q1）。NFR10.2 の (1)（自動の結合テスト）との差は、B1 のコード生成の計画に書く。

### V7・V8 の移行のテストの片付け

11. **消すもの**: `user/repository/V7MigrationIT.java`・`V7BackwardCompatibilityIT.java`、`invitation/repository/V8MigrationIT.java`・`V8BackwardCompatibilityIT.java`、テストの資源 `backend/src/test/resources/db/migration-through-v6`・`migration-through-v7`。消さないと、V9 を足した時点で V8 の2つ（今の移行のすべてを当てて版 8 を期待する）が落ちる。NFR 要件の申し送り「V8 のテストを版 8 に止める直し」は、消すことで要らなくなる。
12. **移す業務の確かめ**: 調べた結果、V7 の側で移すものは無い（列の必須と既定の値は `UserSchemaIT` の `preferencesColumnsRequiredAndDefaults` がすでに確かめている。既存の行への初期値・1回だけ当たる・前の版の追記は移行そのものの確かめ）。V8 の側は `V8MigrationIT` の (a)〜(d) のうち、(a) 同じメールアドレスの招待中の2行目の拒否と、(c) 取り消しの後の招待し直しは `InvitationSchemaIT` の `stateChangesAndUniqueness` がすでに確かめている。残るのは (b)（終わった状態の行は同じメールアドレスを重ねてよく `pending_email` が空、知らない状態の値は CHECK の制約で拒否）と (d)（同時の挿入の待ちと一意の違反）で、どこへ移すかは（Q2）。
13. **README**: `README.md` の V7・V8 の説明にある「確かめは2段」の (1) の自動の結合テストの名前を、消したことに合わせて直し、V9 の行を足す（V9 は移行のテストを作らないこと、戻している間は停止が効かないこと NFR10.3）。
14. **どの Bolt で行うか**: （Q3）。

### カバレッジと関門（NFR9.4・NFR9.5）

15. **一覧から外す**: 手を入れる見込みの `auth.domain`（`LoginFailureReason`・`TokenFailureReason`）・`auth.repository`（`RefreshTokenRepository`）・`access.domain`（`AccessDeniedReason`）を `packagesJudgedByTotal` から外す。`auth.web`・`auth.service`・`user.*`・`audit.domain` はもとから一覧の外で、手を入れた後も下限を満たす。外す対象は生成の最後に、実際に `src/main` を変えたパッケージと突き合わせて決める。
16. **`auth.repository` の足りない分岐**: `LoginAttemptStateRepository#lockDummyForUpdate` の「空いたダミーの行が無い」側を、`LoginAttemptStateRepositoryIT` に足す結合テストで通す。別のスレッドのトランザクションでダミーの行8つ（`subject_id` が負）をすべて排他し、もう1つのスレッドで `lockDummyForUpdate` を呼ぶと、待たない読み取りが空になり乱数で選んだ行の排他の待ち（上限 3 秒）に入る。待ちに入ったことを H2 のセッションの一覧（`INFORMATION_SCHEMA.SESSIONS` の実行中の文）で上限の時間つきで確かめてから、排他を持つ側を確定し、ダミーの行が1つ返ることを確かめる。`sleep` に頼らない。この待ち合わせの手伝いは今 `V8MigrationIT` の中にあるため、消す前に `auth/testsupport` に移して使う（Q2 で B を選ぶと `invitation/testsupport` からも使う）。
17. **静的解析**: 足す問い合わせはすべて名前つきの引数の JPQL で、文字列をつなげない。新しい除外は足さない。既存の `PREDICTABLE_RANDOM` の除外（`lockDummyForUpdate`）はそのまま。

### 性能・観測（NFR5.2・NFR5.4・NFR5.5）

18. 新しい指標・警報・キャッシュは足さない。まとめての無効化の時間は、U3 の止める操作の場面（未無効 100 件・無効 1,000 件の悪い側の条件）として Performance Validation が測る。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 3つの入口は要約の suspended を見るだけで、問い合わせを増やさない。判定のトランザクションでの読み直しは足さない（受け入れた隙） | NFR5.1・NFR1.5、機能設計の承認の場の決定 R-03 |
| ログインは停止の判定をロックの判定より前に置き、照合1回・排他つきの読み取り1回・更新1回・出来事1件でパスワードの誤りとそろえる。時間は測らず回数を確かめる | NFR1.1・NFR2.2（Q1 A） |
| C1 の口の置き場・型・属性（`UserAccountService` の `isSuspended`・`setSuspended`、`RefreshTokenRevocationService` の `revokeAllRefreshTokens`、MANDATORY）と `@Modifying(clearAutomatically = true, flushAutomatically = true)` | 機能設計 2.5・2.6（承認の場の決定 R-01・R-05）、`tech-stack-decisions.md` |
| 口を呼んだ後に呼び出し元が先に読み込んだエンティティを使わないことの確かめは U3 のコード生成の計画 | 機能設計のレビューの R-06、NFR 要件の申し送り |
| V7・V8 の移行と後方互換のテスト4つと複写の置き場は消し、業務の決まりの確かめは業務のテストへ移す | NFR 要件の承認の場の依頼者の決定 |
| V9 には移行や戻しを想定した自動のテストを作らない。NFR10.2 の (1) との差は B1 のコード生成の計画に書く | NFR 要件の承認の場の依頼者の決定 |
| NFR10.2 の (2) 戻しの練習を行うか・その手順、戻している間は停止が効かないことの戻しの手順への記載は、この段で決めない | NFR10.2・NFR10.3 の持ち主（infrastructure-design・deployment-pipeline） |
| 既存の漏えいのテストの列の一覧に V9 の列を足さない。計画のときに列を読むテストを検索し直す | NFR3.1（承認の場の決定 R-02） |
| `auth.repository` の足りない分岐は、ダミーの行8つをすべて排他した状態を作るテストで上げる。一覧と計測の除外を増やさない | NFR9.5、`team.md` の Testing Posture |
| `user` は `auth` を知らない。既存の ArchUnit の境界テストを緩めない | NFR11.1、`AuthBoundaryArchitectureTest` |
| 新しい依存・指標・警報を足さない | `tech-stack-decisions.md`、NFR5.5 |

---

## Q1. V9 を、移行のテストを作らずに何で確かめますか？

依頼者の決定で、V9 を当てる前の状態を作るテスト（複写や1つ前の版の Flyway を使うもの）は作りません。一方、NFR10.1 は「既存の利用者は有効（false）になる」ことを確かめるとしています。起動時の Hibernate の `validate` は列の有無と型を見ますが、必須かどうかと既定の値は見ません。H2 では、既定の値つきの必須の列を足すと既存の行に既定の値が入るため、列の定義（必須・既定 false）を確かめれば、既存の行が false になることを移行のテストなしで裏付けられます。

A. 起動時の `validate-on-migrate` と Hibernate の `validate` に加え、`UserSchemaIT` に次を足す（推奨。移行のテストを作らずに NFR10.1 の中身を確かめられ、今の `preferencesColumnsRequiredAndDefaults` と同じ形のため）: (1) Flyway の履歴に版 9 が当たったと記録される、(2) `INFORMATION_SCHEMA.COLUMNS` で `users.suspended` が BOOLEAN・必須・既定 FALSE、(3) suspended を書かない JDBC の追記で false が入る、(4) エンティティで true・false を書いて読み戻せる
B. 起動時の `validate-on-migrate` と Hibernate の `validate`、エンティティの読み書き（A の (4)）だけにする。テストは最も少ないが、必須と既定の値は確かめず、「既存の利用者は false」の裏付けがコードのレビューだけになる
X. Other (please specify)

[Answer]: B

## Q2. `V8MigrationIT` の業務の確かめのうち、まだどこにも無い (b) と (d) をどこへ移しますか？

(a) と (c) は `InvitationSchemaIT` がすでに確かめています（要点 12）。(b) は JDBC の追記だけで書けます。(d) は2つの接続の確定の待ちを作るテストで、同じ決まりを業務の層では `InvitationConcurrencyIT` が確かめています（待ち合わせの部品で2つの招待の挿入を重ね、1件だけ作られ、もう一方が「招待中」になる）。

A. (b) を `InvitationSchemaIT` に1件足す。(d) は `InvitationConcurrencyIT` が業務の層で確かめているとみなして移さず、対応をコード生成の計画に書く（推奨。同じ決まりの確かめを重ねず、DB の待ちの細部は業務の結果で足りるため。消すテストの資源や待ち合わせの手伝いを運ぶ量も少ない）
B. (b) と (d) の両方を `InvitationSchemaIT` に足す。(d) はアプリの接続プールを使わず、同じ内部DB へ直接開いた2つの接続で作り（`SET LOCK_TIMEOUT` をプールの接続に残さないため）、待ちの確かめの手伝いを `invitation/testsupport` に置く。DB の制約そのものの同時の振る舞いまで残せるが、テストの量が増え、`InvitationConcurrencyIT` と確かめが重なる
C. `V8MigrationIT` を、Spring を起動せず今の移行だけを一時のファイルに当てる業務のテスト（例: `InvitationPendingEmailConstraintIT`）に名前を変えて (a)〜(d) を残し、複写の比べと「V7 の上に1回だけ当たる」の2件だけを消す。書き直しは最も少ないが、Flyway の API を直接使うため、形の上では移行のテストに近いまま残る
X. Other (please specify)

[Answer]: A

## Q3. V7・V8 のテストと複写を消す作業を、どの Bolt で行いますか？

V9 を足すと `V8MigrationIT`・`V8BackwardCompatibilityIT` が落ちるため、遅くとも V9 と同じ時点で消す必要があります。V7 の2つは版 7 で止めているため落ちませんが、決定で一緒に消します。消すのはテストだけで、`src/main` は変わらないため、カバレッジの一覧（`packagesJudgedByTotal`）の扱いには関わりません（`invitation.*`・`user.repository` は一覧の外で、消した後も下限を満たすことを B1 の実測で確かめる）。

A. B1 の作業ブランチの中で、V9 を足す前の最初の手順として行う（推奨。V9 で落ちるテストと同じ Bolt で片付き、待ち合わせの手伝いの移し（要点 16）も同じ流れで済む。squash で B1 の1コミットに入り、件名と計画に「移行のテストの片付けを含む」と書く）
B. B1 の前に別の短いブランチ（例: `fix/260930-user-admin-migration-tests`）で行い、`./gradlew verify` を通して `develop` へ squash で統合してから B1 を始める。B1 のコミットが U1 だけになり履歴を追いやすいが、統合の回数と検査の実行が1回増える
X. Other (please specify)

[Answer]: A

## Q4. V9 の戻しの練習を行うか

NFR 要件の承認の場で、依頼者は「戻す場合を想定したテストは不要（マスタ管理の機能本体がまだ無いため）」と決め、V9 の移行のテストを作らないことにしました。NFR10.2 はもともと、自動の結合テストと、配備の段での戻しの練習（前の版のイメージを V9 の後の内部DB で起動して確かめる）の2段でした。この決定に、戻しの練習も含まれるかが文面から読み切れません。
理由: 含めるかどうかで、infrastructure-design と deployment-execution の手順が変わるため。

A. 戻しの練習も行わない。V9 は起動時の検証と Q1 の確かめだけで確かめ、戻すときの扱い（前の版に戻した間は停止が効かない、など）は手順書に書くだけにする（推奨: 依頼者の決定の理由（マスタ管理の機能本体がまだ無い）は、練習にも当てはまるため）
B. 自動のテストは作らないが、配備の段の戻しの練習は前の Intent と同じく行う（前の版のイメージを、V9 の後の内部DB の写しで起動して健全になることを確かめる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U1 の NFR 設計の計画:

- 設計の要点（案）のとおりに作る。成果物は library の3つ（security-design・logical-components・traceability）。
- Q1 B: V9 は、起動時の検証（Flyway の validate-on-migrate と Hibernate の validate）と、エンティティでの停止の状態の読み書きだけで確かめる。列の定義（BOOLEAN・必須・既定 FALSE）を `INFORMATION_SCHEMA` で確かめるテストは足さない。
- Q2 A: 消す `V8MigrationIT` の (b)（招待中でない行は同じメールアドレスでよい）を `InvitationSchemaIT` に1件足す。(d)（同時の挿入）は業務の層で `InvitationConcurrencyIT` が確かめているとみなして移さず、その対応をコード生成の計画に書く。(a)(c) は `InvitationSchemaIT` が確かめ済み。
- Q3 A: V7・V8 の移行と後方互換のテスト4つと、複写の置き場を消す作業は、B1 の作業ブランチの中で、V9 を足す前の最初の手順として行う。待ちの確かめの手伝いを `auth/testsupport` へ移し、README の記述を直す。
- Q4 A: V9 の戻しの練習も行わない。戻すときの扱い（前の版に戻した間は停止が効かない など）は、手順書に書くだけにする。U1 の NFR10.2（2段の確かめ）との差として記録する。
- 停止の判定の置き場は、ログインが `LoginService#decide` のロックの判定の前、更新とアクセストークンの認証が利用者を読んだ直後。読み書きの回数はパスワードの誤りとそろう。
- 機能設計の R-06（文脈を空にした後の扱い）は、U3 への約束として `logical-components.md` に書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
