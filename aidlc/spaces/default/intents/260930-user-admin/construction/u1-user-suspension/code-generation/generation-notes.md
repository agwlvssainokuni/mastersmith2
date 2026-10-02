# 生成の記録 — U1 利用停止の状態と3つの入口（u1-user-suspension）

B1 のコード生成の途中の決定・計画との差・実測の値を、手順の順に追記する。パスはリポジトリのルートからの相対パス。

## Step 1: 作業の場の用意と、変更の前の基準（2026-10-02）

### 作業の場

- `develop` の先頭: `f226bc67e31f6bc48e381212604407eaeb73f384`（作業ブランチ `feature/260930-user-admin-b1` の作成の時点で同じ）。
- `git status`: アプリのソースに未コミットの変更は無い。変更はワークフローの記録（監査ログ `audit/sakura-local-4e42a93f87ce.md`）だけ。
- ブランチの作成は済み（依頼者の承認を得てリードが作成）。

### E2E の報告の片付け（`gate-decisions.md` の U5 R-02 の決定）

- `frontend/playwright-report/` を消した: ファイル 1 件（HTML の報告）。
- `frontend/test-results/` を消した: JSON のファイル 2 件（`e2e-results.json` と隠しファイルの JSON）と、空のディレクトリ 104 個。
- どちらも `.gitignore` の対象（114・115 行目）で、コミットに影響しない。中身は開かず、誰にも共有していない。

### Dependabot の知らせ（読み取りだけ）

- GitHub の開いているプルリクエストは 0 件（`gh pr list --state open`）。`origin` に残る `dependabot/*` の追跡ブランチ 6 本は、どれも閉じたプルリクエスト（#18〜#22 など、2026-09-29 に閉じた）の残り。
- ただし、変更の前の基準の検査（下）の OSV-Scanner が、Jackson の重大度 High（CVSS 7.5）の脆弱性 4 件で失敗した。Jackson の BOM は `.github/dependabot.yml` の `ignore` にあり Dependabot は知らせないため、OSV-Scanner で気づく形（同じファイルのコメントのとおり）。

| 部品 | 版 | 知らせ | 公表 | 直った版（3.1 系） |
|---|---|---|---|---|
| `tools.jackson.core:jackson-core` | 3.1.6 | GHSA-7hhh-6rmp-j9qf | 2026-10-01 | 3.1.7 |
| `tools.jackson.core:jackson-core` | 3.1.6 | GHSA-p6pp-m3f8-5c89 | 2026-10-01 | 3.1.7 |
| `tools.jackson.core:jackson-databind` | 3.1.6 | GHSA-cxp5-3px4-pw24 | 2026-09-30 | 3.1.7 |
| `tools.jackson.core:jackson-databind` | 3.1.6 | GHSA-wv8q-qhhj-9h54 | 2026-09-30 | 3.1.7 |

- 計画の Step 1（`team.md` の「重大度 High 以上は次の Bolt に入る前に取り込む」）に当たるため、ここで止めて依頼者に諮る。3.1.7 は Spring Boot の管理の 3.1 系のパッチの版で、閉じたプルリクエスト #20（`tools.jackson:jackson-bom` 3.1.6 → 3.1.7）と同じ版。
- 扱い（依頼者の決定）: 計画の Step 1（`team.md` の「重大度 High 以上は次の Bolt に入る前に取り込む」）に当たるため止めて諮り、B1 の前に別のブランチで Jackson の BOM を 3.1.7 に上げ、`develop` に統合した（f299400「Jackson を 3.1.7 に上げ、Gitleaks の監査ログの誤検知を除外する」、監査の記録 782a9f6）。作業ブランチ `feature/260930-user-admin-b1` は `develop`（782a9f6）まで fast-forward した。

### 変更の前の基準（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）

#### 基準とする値（782a9f6 で測った。Jackson 3.1.7 と Gitleaks の除外の後）

- 測った版: Jackson 3.1.7 と Gitleaks の除外のコミット（f299400）の後の `develop` の HEAD 782a9f6。統合の前にリードが流した。
- 結果: **BUILD SUCCESSFUL**（6 分 26 秒）。`gitleaksScan`・`osvScan` も通った。
- テストの件数: バックエンドの単体 1243・結合 566（失敗・飛ばした 0）。
- カバレッジの基準の値は、下の 1 回目（f226bc6）の実測を使う（1 回目もテストとカバレッジの段までは通っており、782a9f6 の差は依存の版と `.gitleaks.toml` だけで、アプリとテストのソースは同じ。依頼者の決定）。

#### 1 回目（f226bc6、失敗。記録として残す）

- 結果: **失敗**（30 分 17 秒）。テスト・カバレッジの段（`verifyCoverage`）までは通り、セキュリティの段の `gitleaksScan` で失敗した。続く `osvScan`・`spotbugsGate` は流れなかったため、別に流した: `spotbugsGate` は通り、`osvScan` は上の Jackson の 4 件で失敗した。
- Gitleaks の指摘（値は表示せず、`--redact` の報告の場所だけを見た）: 1 件、規則 `generic-api-key`、ファイル `aidlc/spaces/default/intents/260930-user-admin/audit/sakura-local-4e42a93f87ce.md` の 18118 行目、コミット 7f3de43（NFR Requirements の記録、2026-10-01）。中身はレビューの記録のディレクトリの名前（16 桁の16進数の識別子、`.aidlc-reviews > nfr-requirements > units > u3-user-admin-api > …`）で、秘密情報ではない誤検知と見立てる。直し方（`.gitleaks.toml` の除外）は計画に無いため、依頼者に諮った。依頼者の決定で、Jackson と同じ f299400 で、パスと値の形の両方で絞った除外を `.gitleaks.toml` に足した。
- テストの件数（JUnit の結果の XML から）:

| 種類 | 件数 | 失敗 | エラー | 飛ばした |
|---|---|---|---|---|
| バックエンドの単体（`test`） | 1243 | 0 | 0 | 0 |
| バックエンドの結合（`integrationTest`） | 566 | 0 | 0 | 0 |
| フロントエンド（Vitest、91 ファイル） | 732 | 0 | — | 0 |

- カバレッジ（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）:

| 範囲 | 行 | 分岐 |
|---|---|---|
| 全体 | 98.8%（5607/5676） | 94.4%（2056/2178） |
| `auth.domain` | 97.9%（94/96） | 100.0%（18/18） |
| `auth.repository` | 93.9%（31/33） | 50.0%（1/2） |
| `auth.service` | 99.0%（199/201） | 91.7%（55/60） |
| `auth.web` | 100.0%（120/120） | 95.0%（19/20） |
| `access.domain` | 100.0%（52/52） | 100.0%（28/28） |
| `audit.domain` | 99.5%（212/213） | 97.7%（43/44） |
| `user.domain` | 99.5%（206/207） | 97.6%（121/124） |
| `user.repository` | 計数なし（実装の行を持たないインターフェースだけ） | 計数なし |
| `user.service` | 100.0%（227/227） | 95.5%（84/88） |
| `invitation.repository` | 100.0%（2/2） | 分岐なし |
| `invitation.service` | 100.0%（332/332） | 93.5%（87/93） |
| `invitation.web` | 97.3%（107/110） | 87.0%（20/23） |

- フロントエンドの全体のカバレッジ（参考）: 文 97.21%・分岐 92.67%・関数 97.66%・行 97.44%。

### `INFORMATION_SCHEMA.COLUMNS` を読むテストの検索し直し（NFR3.1、NFR 要件の R-02）

- 読むテストは `audit/service/AuditSecretLeakIT`・`audit/domain/AuditSchemaIT`・`dslmanage/repository/DslManageRepositoryIT`（いずれも `AUDIT_EVENTS`・DSL の表）と、Step 6 で消す `user/repository/V7MigrationIT`（`USERS`）・`invitation/repository/V8BackwardCompatibilityIT`（`INVITATIONS` 以外の全部の表の閉じた一覧。`USERS` を含む）だけ。計画 2.1 の結果は変わらない（消すテストのほかに `users` の列を閉じた一覧で確かめるテストは無い）。

## Step 2: テストの実行の準備

- `./gradlew :backend:test --tests 'cherry.mastersmith.access.domain.AccessDeniedReasonTest'`: 9 件、すべて通過。
- `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.repository.LoginAttemptStateRepositoryIT'`: 8 件（既存 7 件と Step 4 の 1 件）、すべて通過。
- 差: Step 2 を流した時点で Step 3〜5 の下書きが作業ブランチにあったため、結合テストの確かめは Step 4 の 1 件を含む形になった。初回はこの下書きの `InvitationSchemaIT` のコンパイルの誤り（`SQLException` が `Iterable` でもあるため `assertThat` があいまい）で止まり、直してから流した。道具（`test`・`integrationTest` のタスクと名前の決まり）は変えていない。

## Step 3〜6: 移行のテストの片付け

### 作ったもの・変えたもの

- `backend/src/test/java/cherry/mastersmith/auth/testsupport/H2SessionWaits.java`（新しい）: `awaitExecuting(String url, String statementPattern, Duration timeout, BooleanSupplier waiterDone)`。接続の資格情報はテストの内部DB の既定（`sa`・空）。絞り込みは `LOWER(EXECUTING_STATEMENT) LIKE LOWER(?)` で、絞り込みの文字は引数で渡すため、見回す側の文は自分に一致しない。待っている側が先に終わったとき・上限を過ぎたときは、絞り込みの文字だけを書いた `AssertionError` で失敗にする。`Thread.onSpinWait` で見回し、`sleep` は使わない。
- `auth/repository/LoginAttemptStateRepositoryIT` に `when every dummy row is held, a random dummy row is waited for and returned` を足した。別のスレッドのトランザクションでダミーの行 −1〜−8 を `lockForUpdate` で排他し、もう1つのスレッドの `lockDummyForUpdate` の待ちを `H2SessionWaits` で確かめる（絞り込み `%from login_attempt_states%subject_id=?%for update%`、上限 2 秒）。絞り込みは、空いた行を探す `SKIP LOCKED` の文（`subject_id<0`）には一致しない。
- `invitation/repository/InvitationSchemaIT`: `stateChangesAndUniqueness` の2件目の招待中の追記に、原因の連なりの `SQLException` の SQLState `23505` と、文言が `UK_INVITATIONS_PENDING_EMAIL` を含むことの確かめを足した。(b) を `rows that are not PENDING may share the email, have no pending email, and an unknown state is rejected` として JDBC の形で足した（`CANCELLED`・`REPLACED`・`COMPLETED` の3行は `pending_email` が空で同じメールアドレスで重ねて追記でき、続けて `PENDING` を1件追記できる。`EXPIRED` は SQLState `23513` で拒否され、行が増えない）。
- 書式は `./gradlew :backend:spotlessJavaApply` でそろえた（変わったのは上の3ファイルだけ）。

### 消したもの（Step 6）

| 消したもの | テストの数 |
|---|---|
| `user/repository/V7MigrationIT.java` | 4 |
| `user/repository/V7BackwardCompatibilityIT.java` | 4 |
| `invitation/repository/V8MigrationIT.java` | 6 |
| `invitation/repository/V8BackwardCompatibilityIT.java` | 3 |
| `backend/src/test/resources/db/migration-through-v6/`（V1〜V6 の複写 6 ファイル） | — |
| `backend/src/test/resources/db/migration-through-v7/`（V1〜V7 の複写 7 ファイル） | — |

- 2つを消して空になった `backend/src/test/resources/db/` も消した（Git は空のディレクトリを持たない）。
- 結合テストの数の見込み: 基準の 566 から 17 件を消して 2 件を足すため 551（Step 23 の verify で実測する）。
- 参照の確かめ: 消したクラスの名前と `migration-through-v6`・`migration-through-v7` を、`backend/`（`build` を除く）・`build.gradle.kts`・`settings.gradle.kts`・`.gitleaks.toml`・`.github/` で検索した結果、一致は 0 件。`H2SessionWaits` の Javadoc は、消したクラスの名前を出さずに「消した V8 の移行のテスト」と書いた。残る参照は `README.md` の 741・742 行目（V7・V8 の確かめの記述）だけで、計画どおり Step 21 で直す。ワークフローの記録（`aidlc/` の下）は直さない。

### 待ちの確かめの置き場（Step 3、Q-A A。`code-summary.md` へ写す）

- `V8MigrationIT` を消した後は、同じ形の待ちの確かめが2か所になる: `auth/testsupport/H2SessionWaits`（引数で接続の URL・絞り込み・上限・待っている側の終わりの判定を受ける）と `invitation/testsupport/TestInvitationBarrier#waitingForLock`（変えていない）。
- まとめない理由: 招待のテストの手伝いを `auth/testsupport` に依存させると、テストの手伝いの置き場の決まり（`<機能>/testsupport`）をまたぐため（計画 8節の D-11、9節の Q-A の決定）。

### `V8MigrationIT` の (a)〜(d) の行き先（Step 5、Q2 A。`code-summary.md` へ写す）

| 消した確かめ | 行き先 |
|---|---|
| (a) 同じメールアドレスの2件目の招待中は一意の違反（23505・制約の名前） | `InvitationSchemaIT#stateChangesAndUniqueness`（SQLState と制約の名前の確かめを足して強めた） |
| (b) 招待中でない行は同じメールアドレスで重ねられ、知らない状態は CHECK で拒否（23513） | `InvitationSchemaIT#endedRowsMayRepeat`（新しい） |
| (c) 取り消した後に同じメールアドレスで招待中を作れる | `InvitationSchemaIT#stateChangesAndUniqueness`（確かめ済みのため移さない） |
| (d) 同時の追記の待ちと一意の違反 | `invitation/service/InvitationConcurrencyIT` の `two simultaneous invitations to the same email create one, the other is AlreadyPending with its id`（業務の層で確かめているとみなし、移さない） |
| V1〜V7 の複写と本番の一致、V8 の当て方・後方互換（`V7*`・`V8BackwardCompatibilityIT` を含む） | 移さない（NFR 設計の Q1 B・Q3 A・Q4 A。V9 の確かめは起動時の検証とエンティティの読み書きだけ） |

### 流したテスト

| コマンド | 結果 |
|---|---|
| `./gradlew :backend:integrationTest --tests '…LoginAttemptStateRepositoryIT'` を5回 | 毎回 8 件通過。足した1件は 0.010〜0.016 秒 |
| `./gradlew :backend:jacocoTestReport`（上の結合テストの後） | `LoginAttemptStateRepository#lockDummyForUpdate` の分岐 2/2・行 10/10（足りなかった「空いたダミーの行が無い」側を通った） |
| unit-test-instructions 2.2 の1つ目（`LoginAttemptStateRepositoryIT`・`InvitationSchemaIT`・`InvitationConcurrencyIT`） | 8・4・1 件、すべて通過 |
| unit-test-instructions 2.2 の2つ目（`user.repository.*`・`invitation.repository.*`）に上の2つを足して、書式をそろえた後に流し直した | `UserSchemaIT` 8・`UserRepositoryIT` 11・`InvitationSchemaIT` 4・`InvitationRepositoryIT` 5・`LoginAttemptStateRepositoryIT` 8・`InvitationConcurrencyIT` 1、計 37 件すべて通過（失敗・飛ばした 0） |

## Step 7: 片付けの区切りの確かめ

- `git diff --stat HEAD`: 21 ファイル（追加 207 行・削除 1129 行）。`backend/src/main` の変更は 0 件で、テストのソースと資源、ワークフローの記録だけ。`packagesJudgedByTotal` の扱いに関わらない。
- C1 の区切りの中身（コミットは生成の後に依頼者の承認を得てから。ここでは行わない）:
  - 新しい: `backend/src/test/java/cherry/mastersmith/auth/testsupport/H2SessionWaits.java`
  - 変えた: `backend/src/test/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepositoryIT.java`、`backend/src/test/java/cherry/mastersmith/invitation/repository/InvitationSchemaIT.java`
  - 消した: `V7MigrationIT`・`V7BackwardCompatibilityIT`・`V8MigrationIT`・`V8BackwardCompatibilityIT` と `backend/src/test/resources/db/migration-through-v6/`・`migration-through-v7/`
  - 記録（C1 に含めず、記録のコミットに回す想定）: `code-generation-plan.md` のチェックボックス、この `generation-notes.md`

## Step 8: ドメインの層の前の確かめ

- 区分の数や `switch` を前提にした既存のテストを検索で確かめた（`@EnumSource(`・`FailureReason.values()`）。計画の挙げた5つ（`TokenAuthenticationExceptionTest` の `fourReasons` と `@EnumSource`、`AccessDeniedReasonTest` の `everyTokenFailureReasonIsCovered`、`TokenAuthenticationEntryPointTest` の `@EnumSource`、`AuditEventFactoryTest` の `@EnumSource(LoginFailureReason.class)`）のほかに、`audit/domain/AuditEventTest#namesFitIntoTheColumns`（`AuditFailureReason` のすべての値が 32 文字以内）が新しい値を含む。書き換えは要らない（`ACCOUNT_SUSPENDED` は 17 文字）ため、Step 10 の実行に足して流した。
- 本体で `LoginFailureReason`・`TokenFailureReason` を `switch` で尽くすのは `access.domain.AccessDeniedReason` と `audit.domain.AuditEventFactory` の2つだけ（計画 2.2 の読み方どおり）。

## Step 9: ドメイン — 実装

- 計画どおり: `LoginFailureReason.ACCOUNT_SUSPENDED`、`TokenFailureReason.USER_SUSPENDED`、`AccessDeniedReason#of` の `case USER_SUSPENDED -> Optional.empty()`（クラスと `of` の Javadoc にも書いた）、`AuditFailureReason.ACCOUNT_SUSPENDED` と `AuditEventFactory` の変換の1行、`AuthenticationEvent#toString`（`enteredEmail=***`、ほかの項目は出す）、`AuthenticatedUser#toString`（`email=***`、`userId`・`admin` は出す）。
- `AuditFailureReason` の値の並びは、`ACCOUNT_LOCKED` の直後に置いた（DB には名前で入るため並びは記録に影響しない）。

## Step 10: ドメイン — テスト（単体）

- `TokenAuthenticationExceptionTest`: `fourReasons` を `five reasons are defined`（`fiveReasons`、値の名前を並びどおりに確かめる形）に直し、`USER_SUSPENDED` の誤りが `invalid_token` で説明が区分の名前になる1件を足した（`@EnumSource` の既存のテストも新しい値を含む）。
- `AccessDeniedReasonTest`: `USER_SUSPENDED` が理由なし（区分と例外の両方）になる1件と、アクセス拒否の理由に `USER_SUSPENDED` を足していないことの1件を足し、`everyTokenFailureReasonIsCovered` の期待を「`TOKEN_EXPIRED`・`USER_SUSPENDED` は空、ほかは同じ名前の理由」に直した。
- `AuditEventFactoryTest`: `ACCOUNT_SUSPENDED` の LOGIN_FAILED の監査の行の1件を足した。
- `SecretTypesTest`: `AuthenticationEvent#toString` の2件（値あり・メールアドレスなし）と `AuthenticatedUser#toString` の1件を足した。
- **計画との差（G-1）**: 計画の Step 10 は「操作した人に利用者 ID が入った監査の行」と書いているが、今の `AuditEventFactory#from(AuthenticationEvent)` はログインの出来事の利用者 ID を監査の行に写さない（操作した人の列は空。既存のテスト `occurredAtAndNoUserId` が「利用者IDは記録項目に含まれない」と確かめている）。計画 2.1 の NFR 設計の R-03 と `security-design.md` 5.1・6節は「`AuditEventFactory#from` を変えない」「既存のログインの失敗と同じ形」としているため、写し取りは変えず、テストは今の形（理由 `ACCOUNT_SUSPENDED`・結果 FAILURE・入れたメールアドレスあり・操作した人と対象の利用者は空）を確かめる形にした。出来事（`AuthenticationEvent`）の `userId` には本人の利用者 ID を載せる（Step 13）。依頼者に確かめる。
- 流したテスト（unit-test-instructions 2.3 に `AuditEventTest` を足した）:

```bash
./gradlew :backend:cleanTest :backend:test \
  --tests 'cherry.mastersmith.auth.domain.TokenAuthenticationExceptionTest' \
  --tests 'cherry.mastersmith.auth.domain.SecretTypesTest' \
  --tests 'cherry.mastersmith.access.domain.AccessDeniedReasonTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventFactoryTest' \
  --tests 'cherry.mastersmith.auth.web.TokenAuthenticationEntryPointTest' \
  --tests 'cherry.mastersmith.audit.domain.AuditEventTest'
```

| クラス | 件数 | 結果 |
|---|---|---|
| `AccessDeniedReasonTest` | 12 | 通過 |
| `AuditEventFactoryTest` | 32 | 通過 |
| `AuditEventTest` | 6 | 通過 |
| `SecretTypesTest` | 7 | 通過 |
| `TokenAuthenticationExceptionTest` | 9 | 通過 |
| `TokenAuthenticationEntryPointTest` | 8 | 通過 |

計 74 件、失敗・飛ばした 0。

## Step 11: スキーマ（V9）と DB アクセス — 実装

- `backend/src/main/resources/db/migration/V9__u1_user_suspension.sql`: 先頭は V1〜V8 と同じ SQL のコメントのライセンスヘッダー、続けて V7・V8 と同じ形の説明のコメント（Intent と単位、前進のみ、既存の行は false）、本文は `ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL;` の1文だけ。V1〜V8 は変えていない。
- `user.domain.User`: `@Column(name = "suspended", nullable = false) private boolean suspended;`（コンストラクターで false）と `isSuspended()`。書き換えのメソッドは作らず、クラスと `isSuspended` の Javadoc に「書き換えは `user.repository` の停止の列だけの更新の問い合わせで行う」と書いた。
- `user.repository.UserRepository#updateSuspended(long, boolean)`・`auth.repository.RefreshTokenRepository#revokeAllActiveByUserId(long, Instant)`: 計画どおりの JPQL と `@Modifying(clearAutomatically = true, flushAutomatically = true)`、名前つきの引数だけ（文字列の連結なし）。
- `./gradlew :backend:compileJava` が通ることを確かめた。

## Step 12: スキーマと DB アクセス — テスト（結合）

- `UserSchemaIT` に2件: `the V9 migration is applied and a new user entity reads back as not suspended`（`flyway_schema_history` の版 9 が成功、エンティティで作った直後と、停止の列を書かない JDBC の追記（V9 の前の形）のどちらも false）、`the suspended column is written by the update query and read back by the entity as true and false`。`INFORMATION_SCHEMA` の列の定義の確かめは足していない（Q1 B）。
- `UserRepositoryIT` に3件: 停止の列だけを書き換える（`ADMIN_FLAG`・氏名と表示の設定の4列・`PASSWORD_HASH`・`EMAIL`・`CREATED_AT` は変わらない。止めると解くの両方）、いない利用者で 0 を返しほかの利用者の行は変わらない、同じトランザクションで先に `findById` で読み込んだ後の更新で後の `findById` が書いた値を返す（true の後に false も）。あわせて、既存の `updatePreferencesTouchesOnlyFourColumns` の「変わらない列」に `SUSPENDED` を足した（読み取りの列の一覧 `row` に `suspended` を足したため）。
- `RefreshTokenRepositoryIT` に3件: 未無効 100 件（期限切れと期限内を半分ずつ）・無効 3 件・ほかの利用者の未無効 1 件の形で、件数 100・未無効の行はすべて指定の時刻・無効の行の日時とほかの利用者の行は変わらない。未無効の行が無い（と、いない利用者）で 0。2回目のまとめての無効化は新しい行だけを無効にする（計画の3件目の「0 件」に加えた確かめ）。時間は比べていない。
- 列を知らない JDBC の追記の確かめ（計画 Step 11 の最後の項目）: `UserSchemaIT`・`RefreshTokenRepositoryIT`（`@BeforeEach` で `suspended` を書かずに `users` に追記する）が既定の値で動くことを、この実行で確かめた。`LoginAttemptStateRepositoryIT` などのほかのクラスは Step 14 の終わりのまとめての実行で確かめる。
- 流したテスト（unit-test-instructions 2.4）:

```bash
./gradlew :backend:cleanIntegrationTest :backend:integrationTest \
  --tests 'cherry.mastersmith.user.repository.UserSchemaIT' \
  --tests 'cherry.mastersmith.user.repository.UserRepositoryIT' \
  --tests 'cherry.mastersmith.auth.repository.RefreshTokenRepositoryIT'
```

| クラス | 件数 | 結果 |
|---|---|---|
| `UserSchemaIT` | 10 | 通過 |
| `UserRepositoryIT` | 14 | 通過 |
| `RefreshTokenRepositoryIT` | 8 | 通過 |

計 32 件、失敗・飛ばした 0。1回目は `UserSchemaIT` の `assertThat(tx.execute(...))`（`Boolean` を返す）があいまいでコンパイルが止まり、値を `Boolean` の変数に受けて直した。

## Step 13: 業務処理 — 実装

- `user.service.UserSummary`: 最後に `boolean suspended` を足し、`toString` に `suspended=` を出す（メールアドレスと氏名を伏せる形は変えない）。Javadoc に `@param suspended` を足した。
- `user.service.UserAccountService`: `toSummary` が `isSuspended()` を写す（読み取りの回数は変わらない）。`isSuspended(long)`（`@Transactional(readOnly = true)`、いなければ `IllegalStateException`、文言は `userId=<ID>` だけ）と `setSuspended(long, boolean)`（`@Transactional(propagation = Propagation.MANDATORY)`、`updateSuspended` が 0 なら `IllegalStateException`）を足し、Javadoc に C1 の約束（呼ぶ前に読み込んだエンティティは切り離される、状態は読み直す）を書いた。
- `auth.service.RevokeAllResult`（`int revoked`、負の数は `IllegalArgumentException`）と `auth.service.RefreshTokenRevocationService`（`revokeAllRefreshTokens(long)`、MANDATORY、注入した `Clock` の時刻で `revokeAllActiveByUserId` を1回、DEBUG のログに `userId`・`revoked` のキーと値だけ、監査の出来事なし）を新しく作った。
- `auth.service.LoginService#decide`: ダミーの行の分岐（利用者がいない）の後、`LockPolicy.decide` の前に `user.suspended()` を見て、読んだ値のまま `update` を1回、`LOGIN_FAILED`・`ACCOUNT_SUSPENDED`・本人の利用者 ID の出来事を1件、`Decision.FAILED`。行が無いときの「作ってやり直す」流れは停止の判定より前のまま。判定のトランザクションで `users` を読み直さず、停止で拒否したことのアプリのログは足していない（D-9）。クラスの Javadoc の手順に停止の判定を足した。
- `auth.service.TokenRefreshService#refresh`: `findById` の直後に `suspended` なら `REFRESH_FAILED`（例外で `revokeIfActive` も巻き戻る、監査なし）。クラスの Javadoc と `@throws` に停止中を足した。
- `auth.service.LoginCommand#toString`（`email=***`、パスワードは `Password` の伏せた文字列化のまま）と `user.service.PasswordVerification#toString`（`email=***`、`user` は `UserSummary` の伏せた文字列化、`matched` は出す）を足した（Q-C B）。`verifyPassword(String, Password)`・`existsByEmail(String)` の `String` の引数は変えていない（後の Intent へ）。
- `new UserSummary(...)` を使うテストの5か所（`LoginServiceTest`・`LogoutServiceTest`・`TokenRefreshServiceTest`・`UserAccountServiceTest`・`DslLifecycleTest`）に `false` を足した。本体で `new UserSummary(` を使うのは `UserAccountService#toSummary` だけ。

## Step 14: 業務処理 — テスト（単体・結合）

### 作ったもの・変えたもの

- `auth/testsupport/TestUserSuspension`（新しい）: `suspend(userId)`（1つのトランザクションで `setSuspended(true)` と `revokeAllRefreshTokens`、無効にした件数を返す）と `resume(userId)`（`setSuspended(false)` だけ）。`TransactionTemplate`・`UserAccountService`・`RefreshTokenRevocationService` をコンストラクターで受け取る普通のクラス（Spring の Bean にはしない。使うテストが `@BeforeEach` で作る）。
- `UserAccountServiceTest`（5件足し、1件直した）: 要約に `suspended` が写る（`findById` の true・false、`verifyPassword` の停止中でも照合は行う）、`isSuspended` の値といない利用者の `IllegalStateException`（文言は利用者 ID だけ）、`setSuspended` が更新の問い合わせだけで書き 0 行で `IllegalStateException`、`UserSummary#toString` が `suspended=` を出しメールアドレスと氏名を出さない、`PasswordVerification#toString` の伏せ字（利用者がいる・いないの両方）。既存の `summaryFields`（要約の項目の並びを `containsExactly` で確かめる）に `suspended` を足した（D-8 に当たるテストだけの直し）。
- `UserSuspensionIT`（新しい、5件）: トランザクションの外の `setSuspended` は `IllegalTransactionStateException` で何も書かない、呼び出し元を巻き戻すと戻り確定すると残る、同じトランザクションで先に `findById` で読み込んだ後の `setSuspended(true)`・`(false)` の後に `isSuspended` と要約の `suspended` が書いた値を返す（R-05）、いない利用者で `IllegalStateException` と呼び出し元ごと巻き戻る（先に止めた別の利用者も戻る）、止めて解いても管理者の印・氏名と表示の設定・メールアドレス・ハッシュ・ロックの状態の行（失敗回数 3・ロックの期限あり）が変わらない（BR1.5）。
- `RefreshTokenRevocationServiceTest`（新しい、6件）: 注入した時計の時刻、件数を `RevokeAllResult` で返す、0 件も成功、DEBUG のログのキーが `userId`・`revoked` だけ（値 7・3）、INFO の既定では何も出ない、`RevokeAllResult` が負の数を拒み 0 を受け付ける。
- `RefreshTokenRevocationServiceIT`（新しい、4件）: 利用者を作り本物の `LoginService#login` を2回呼んで未無効のトークンを2つ作る形で、トランザクションの外は `IllegalTransactionStateException` で何も無効にしない、`setSuspended(true)` と同じトランザクションで巻き戻すと停止もトークンも戻る、確定すると両方残りほかの利用者のトークンは残る、停止を解いても無効にした行は戻らない（止め直すと 0 件）。
- `LoginServiceTest`（6件足した）: 停止中の4つの場合（正しいパスワード・誤ったパスワード・ロック中・本人の行が無い）で、`update` が読んだ値のまま1回（行が無いときは作ってやり直した後に1回、順を `InOrder` で確かめた）、出来事が `LOGIN_FAILED`・`ACCOUNT_SUSPENDED`・利用者 ID 7・入れたメールアドレスの1件、`AUTHENTICATION_FAILED`、リフレッシュトークンの保存なし。停止中かつロック中の理由は `ACCOUNT_SUSPENDED`。`suspended login attempts do not increase the failure count even one below the threshold`（説明文に計画の文言を含む。しきい値 5 の −1 の 4 回の状態で誤ったパスワードでも 4 のまま書き、ロックの書き込みもログも無い、R-06・AC3.2.8）。停止を解いた後（`suspended` が false の要約）は 4 回から数えて 5 回目でロックが掛かる。
- `TokenRefreshServiceTest`（2件足した）: 停止中の利用者で `REFRESH_FAILED`・`issueTokens` を呼ばない。あわせて、要約が無い（利用者が消えた）場合の同じ確かめを1件足した（既存のテストに無かった分岐）。
- `LoginCommandTest`（新しい、1件）: `toString` がメールアドレスとパスワードを含まず、値はそのまま読める。

### 計画との差

- G-2: `TestUserSuspension` の `suspend` は無効にした件数（`int`）を返す形にした（計画は戻り値を決めていない。`RefreshTokenRevocationServiceIT` で件数を確かめるため）。
- G-3: `TokenRefreshServiceTest` に、計画に無い「要約が無い（利用者が消えた）」の1件を足した（停止の判定の直前の分岐で、成功・停止中と合わせて境目を3つにするため。`phases/construction.md` の Testing Standards）。
- G-4: `RefreshTokenRevocationServiceTest` は計画の5件に「INFO の既定では何も出ない」を足して6件にした。
- `UserSuspensionIT`・`RefreshTokenRevocationServiceIT` は既存の `RefreshConcurrencyIT` と同じく `mastersmith.auth.password.bcrypt-cost=4` で起動する（テストの速さのため。本番の設定は変えていない）。

### 流したテスト

業務処理の層（unit-test-instructions 2.5）:

```bash
./gradlew :backend:cleanTest :backend:test \
  --tests 'cherry.mastersmith.user.service.UserAccountServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginServiceTest' \
  --tests 'cherry.mastersmith.auth.service.TokenRefreshServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LogoutServiceTest' \
  --tests 'cherry.mastersmith.auth.service.RefreshTokenRevocationServiceTest' \
  --tests 'cherry.mastersmith.auth.service.LoginCommandTest' \
  --tests 'cherry.mastersmith.dslmanage.service.DslLifecycleTest' \
  :backend:cleanIntegrationTest :backend:integrationTest \
  --tests 'cherry.mastersmith.user.service.UserSuspensionIT' \
  --tests 'cherry.mastersmith.auth.service.RefreshTokenRevocationServiceIT'
```

結果: 単体 85 件（`UserAccountServiceTest` 19・`LoginServiceTest` 18・`TokenRefreshServiceTest` 8・`LogoutServiceTest` 5・`RefreshTokenRevocationServiceTest` 6・`LoginCommandTest` 1・`DslLifecycleTest` 28）、結合 9 件（`UserSuspensionIT` 5・`RefreshTokenRevocationServiceIT` 4）、すべて通過。

### Step 8〜14 と片付け（unit-test-instructions 2.2）のテストをまとめて流した結果

`:backend:cleanTest :backend:cleanIntegrationTest` を付けた1回の実行で、Step 8〜14 で触れた単体のクラス（2.3・2.5 と `AuditEventTest`）と、結合のクラス（2.2 の2つのコマンドの全部、2.4、2.5）を流した。

| 種類 | クラスと件数 | 計 |
|---|---|---|
| 単体 | `AccessDeniedReasonTest` 12・`AuditEventFactoryTest` 32・`AuditEventTest` 6・`SecretTypesTest` 7・`TokenAuthenticationExceptionTest` 9・`TokenAuthenticationEntryPointTest` 8・`UserAccountServiceTest` 19・`LoginServiceTest` 18・`TokenRefreshServiceTest` 8・`LogoutServiceTest` 5・`RefreshTokenRevocationServiceTest` 6・`LoginCommandTest` 1・`DslLifecycleTest` 28 | 159 |
| 結合 | `LoginAttemptStateRepositoryIT` 8・`RefreshTokenRepositoryIT` 8・`InvitationSchemaIT` 4・`InvitationRepositoryIT` 5・`InvitationConcurrencyIT` 1・`UserSchemaIT` 10・`UserRepositoryIT` 14・`UserSuspensionIT` 5・`RefreshTokenRevocationServiceIT` 4 | 59 |

失敗・飛ばした 0（BUILD SUCCESSFUL、23 秒）。`LoginAttemptStateRepositoryIT`・`InvitationSchemaIT`（`users` に停止の列を書かずに JDBC で追記する）も既定の値で通った（Step 11 の最後の項目）。

参考（計画の外の確かめ）: バックエンドの単体テストの全体（`./gradlew :backend:cleanTest :backend:test`）も流し、1274 件（基準の 1243 から 31 件増）すべて通過した。`ArchitectureTest` と機能ごとの境界テストも、変えずにこの中で通った。結合テストの全体は Step 23 の verify で流す。

## 依頼者の決定（Step 14 の後、2026-10-02）

- **G-1**: 停止中のログインの監査の行の操作した人（`actor_user_id`）と対象の利用者（`target_user_id`）は空のままとする（`AuditEventFactory` を変えない。承認済みの設計と既存のログインの失敗にそろえる）。計画の Step 10 の文言（「操作した人に利用者 ID」）との差として記録する。Step 10 の `AuditEventFactoryTest` と Step 19 の `AuditAuthenticationEventsIT` は、この決定の形（理由 `ACCOUNT_SUSPENDED`・結果 FAILURE・入れたメールアドレスあり・操作した人と対象の利用者は空）を確かめる。
- **G-2〜G-4**: 追加（`TestUserSuspension#suspend` が件数を返す、`TokenRefreshServiceTest` の「要約が無い」の1件、`RefreshTokenRevocationServiceTest` の「INFO の既定では何も出ない」の1件）は受け入れ。

## Step 15: 認証の3つの入口（web）— 実装

- `auth.web.AccessTokenAuthenticationProvider#authenticate`: `findById` の直後に `user.suspended()` なら `TokenAuthenticationException(TokenFailureReason.USER_SUSPENDED)`。読み取りは今までどおり1回。入口と応答（401 `AUTHENTICATION_REQUIRED`）は変えていない。クラスの Javadoc に停止の判定を足した。
- `auth.web.CurrentUserResponse#toString`: `email=***`・`displayName=***`、`admin` と表示の設定3つは出す。JSON の応答の項目は変わらない。
- `auth.web.LoginRequest#toString`: `LoginRequest[email=***, password=***]` に直した（Q-C B）。Javadoc の `@param email` に「文字列化で伏せる」を足した。
- `AuthController` は変えていない。

## Step 16: 認証の3つの入口 — テスト（単体）

- `AccessTokenAuthenticationProviderTest`（新しい、7件）: 有効な利用者は主体（管理者の印は DB の値）、停止中は `USER_SUSPENDED`、停止中の管理者も `USER_SUSPENDED`、いない利用者は `USER_NOT_FOUND`、トークンの検証の失敗は利用者を読まない、停止の判定で `findById` は1回だけで `isSuspended` を呼ばない、`supports` は Bearer だけ。
- `WebSecretTypesTest`（3件。1件直し・1件直し・1件足し）: `the login request hides the password and the email`（D-8 の直し）、`TokenResponse` の確かめを「中の `CurrentUserResponse` のメールアドレスと氏名も出ない」に直した、`CurrentUserResponse#toString` の1件を足した。
- 計画との差（G-5）: 計画は `AccessTokenAuthenticationProviderTest` を5件としたが、「停止中の管理者」「検証の失敗では読まない」を足して7件にした（失敗・境目を増やすため）。
- 流したテスト: `./gradlew :backend:cleanTest :backend:test --tests '…AccessTokenAuthenticationProviderTest' --tests '…WebSecretTypesTest'` → 7・3 件、すべて通過。

## Step 17: 認証の3つの入口 — テスト（結合）

- `SuspendedUserAuthenticationIT`（新しい、7件）:
  - `everyEntryRejects`: ロック中（停止の前に5回失敗）の利用者を止め、止める前のアクセストークン → 401 `AUTHENTICATION_REQUIRED`、止める前のリフレッシュトークン → 401 `REFRESH_FAILED`、正しい・誤ったパスワード → 401 `AUTHENTICATION_FAILED`（トークンも新しい Cookie も無い）。更新とアクセストークンでは監査の行が増えず、ログインの2回で2行増える。
  - `responsesAreIndistinguishable`: 入口ごとに、停止中とほかの失敗（ログインはパスワードの誤り、更新は回して無効になったトークン、アクセストークンは改ざんしたトークン）の状態コード・本文の項目（`traceId`・`instance` を除く）が同じ。本文に `SUSPENDED` とメールアドレスが無い。
  - `refreshOfSuspendedUserRollsBack`: `setSuspended(true)` だけを確定した利用者の有効なリフレッシュトークンでの更新が 401 で、そのトークンは未無効のまま（巻き戻った）。
  - `resumeAcceptsAgain`: 停止の前の失敗 2 回 → 停止中の誤り3回・正しい1回は数えず 2 のまま → 解いた後の誤り1回で 3（続きから数える）→ 新しいログイン・その Cookie での更新・新しいアクセストークンの要求がすべて受け付け、止める前の Cookie は 401 `REFRESH_FAILED`。
  - `accessTokenTimeline`: `MutableClock` で t0 ログイン、t0+1 分に止め、t0+1 分と t0+2 分の1ミリ秒前は 401、t0+2 分に解いて 200、t0+5 分の1ミリ秒前は 200、t0+5 分ちょうどは 401。
  - `suspendedAdministrator`: 止めた管理者の止める前のトークンで `/api/admin/check`・招待の一覧・招待の作成がすべて 401 `AUTHENTICATION_REQUIRED`、監査の行・招待の行が増えず、停止と管理者の印は変わらない。
  - `noExtraQueries`: 有効・停止中のアクセストークンの要求がどちらも `select users` 1回、停止中の更新の `select users` は有効な更新と同じ1回。
- 既存のクラスに足したもの: `LoginApiIT`（1件。停止中の正しい・誤ったパスワード・ロック中がパスワードの誤りと同じ応答・同じ文の並び（`select users`・`select login_attempt_states for update`・`update login_attempt_states`・`insert audit_events`）・照合1回。本人の行が無い停止中の利用者は、パスワードの誤り（行なし）と同じ並び・同じ応答・照合1回）、`AccessTokenApiIT`（1件。区分 `USER_SUSPENDED`、解いた後は 200）、`AccessDeniedEventsIT`（1件。停止中の管理者の有効なトークンで 401、アクセスの拒否の出来事なし）、`MePreferencesApiIT`・`MePasswordApiIT`（2件ずつ。本文の `suspended: true`・`admin: true` は無視され次の要求も通る、停止中の利用者の `suspended: false` は 401 のまま状態もパスワードも変わらない）、`InvitationAdminApiIT`（1件。AC3.2.6）。
- 計画との差:
  - G-6: 計画は「トークンと Cookie が無い」としたが、更新の失敗は今までどおり Cookie を消す指示（`mastersmith_refresh=; Max-Age=0`）を返す。確かめは「新しいリフレッシュトークンを渡さない（Set-Cookie があれば消す指示だけ）」にした。実装は変えていない。
  - G-7: `/api/admin/check` の成功は 200 ではなく 204（既存の作り）。テストは 204 を前提にした。
  - G-8: `MePreferencesApiIT`・`MePasswordApiIT` は計画の「1件ずつ」を、通る場合と停止中の場合に分けて2件ずつにした。`MePasswordApiIT` の本文に項目を足す送り方は、テストの中で `HttpTestClient` を直接使った（`MeApi` は変えていない）。
- 流したテスト（unit-test-instructions 2.6 の結合と 2.7 をまとめて、`:backend:cleanIntegrationTest` 付き）: `AccessDeniedEventsIT` 10・`AuditAuthenticationEventsIT` 7・`AuditWriteFailureIT` 8・`AccessTokenApiIT` 7・`LoginApiIT` 6・`SuspendedUserAuthenticationIT` 7・`InvitationAdminApiIT` 10・`MePasswordApiIT` 17・`MePreferencesApiIT` 13、計 85 件すべて通過（21 秒）。1回目は G-6・G-7 の期待の誤りで5件失敗し、テストの期待を直した。

## Step 18: AC3.2.6 の回帰テストの記録

- `invitation/web/InvitationAdminApiIT#suspendedUserEmailIsRegistered` を1件足した（停止中の利用者のメールアドレスへの招待は 409 `INVITATION_EMAIL_REGISTERED`、招待の行とメールが増えず、本文にメールアドレスと `SUSPENDED` が無い）。既存の `conflicts`（登録済みの拒否）は変えていない。`code-summary.md` へ写す。

## Step 19: 監査 — テスト（結合）

- `AuditAuthenticationEventsIT#suspendedLoginsAreRecorded`（1件）: 停止中の正しい・誤ったパスワードのログインが、それぞれ `LOGIN_FAILED`・`FAILURE`・`ACCOUNT_SUSPENDED`・入れたメールアドレス・送り手の情報・トレースID の1行で残り、`actor_user_id`・`target_user_id` は空（G-1 の決定。停止していない利用者のパスワードの誤りの行も同じく空）、要求のスレッドで insert 1回。
- 停止中の更新とアクセストークンの認証が監査の行を作らないことは、`SuspendedUserAuthenticationIT#everyEntryRejects` で確かめた。
- `AuditWriteFailureIT#suspendedLoginResponseIsUnchanged`（`APPEND_FAILURE`・`CONNECTION_FAILURE` の2件）: 書き込みの失敗でも停止中のログインの応答（401 `AUTHENTICATION_FAILED`）は変わらず、書き込みの試みは再試行なしの2回。
- 結果は Step 17 の表のとおり（すべて通過）。

## Step 20: 漏えいのテスト

- `auth/web/AuthSuspensionSecretLeakIT`（新しい、1件）。`AuthSecretLeakIT` と同じく `SpringApplicationBuilder` で起動し、`auth`・`user`・`audit`・`dslmanage` を TRACE にした。流す操作: 利用者のログインの成功・パスワードの誤り・更新、管理者のログインと DSL のプレビューの読み取り、`TestUserSuspension#suspend`、停止中のログイン（正しいパスワード）・更新・アクセストークンの要求（`/api/me/preferences`）。
- 確かめ1: 出力全体がすべて JSON の行で、パスワード（2人と誤った値）・ハッシュ（全利用者）・アクセストークン・リフレッシュトークン（Cookie の値とハッシュ）・署名鍵が無い。
- 確かめ2: 実際の出力で行の形を確かめてから書いた。`ENTER LoginService#login(`・`ENTER AuditEventListener#onAuthenticationEvent(`・`EXIT  AuthController#login(`・`EXIT  AuthController#refresh(`・`ENTER DslAdminController#preview(`・`ENTER/EXIT  RefreshTokenRevocationService#revokeAllRefreshTokens(`・`ENTER AuthController#login(`・`EXIT  UserAccountService#verifyPassword(` の行がすべて出ていて、どれにも2人のメールアドレスと氏名が無い。停止中のログインの出来事の行に `ACCOUNT_SUSPENDED` と `enteredEmail=***`、無効化の EXIT に `RevokeAllResult[revoked=1]` がある。
- 確かめ3: まとめての無効化の DEBUG の行は、同じスレッドの TRACE の行と比べて増えたキーが `userId`・`revoked` だけ（`revoked` は 1）。
- 確かめ4: 停止中の3つの入口の応答（401）の本文に、メールアドレス・トークンの値・`SUSPENDED` が無い。
- 残る漏えい（D-10）: 利用者のメールアドレスが出た行は、操作の範囲で `ENTER UserAccountService#verifyPassword(<メールアドレス>,***)` の 3 行だけ（利用者のログイン3回分。管理者のログインの1行を合わせると 4 行）。`existsByEmail` はこの流れでは呼ばれない。テストは「メールアドレスが出る行は `ENTER UserAccountService#verifyPassword`・`ENTER UserAccountService#existsByEmail` の行に限る」ことを確かめる形にし、残る漏えいの範囲を固定した（計画は件数の記録だけ。G-9）。
- 流したテスト: `./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests '…AuthSuspensionSecretLeakIT'` → 1 件通過。

## Step 21: 構造の検査と文書

- `ArchitectureTest` と機能ごとの境界テスト9つ（計画の3つに加え `appearance`・`dsl`・`dslmanage`（2つ）・`mail`・`targetdb`）を変えずに流し、45 件すべて通過。ArchUnit のテストは変えていない。
- `docker/monitoring/provisioning/alerting/mastersmith.yaml` に差は無い（`git diff --stat` で 0）。
- `README.md`: 「スキーマの変更（Flyway）」の V7・V8 の行の (1) を、B1 で消したことと理由に置き換え（V8 は移した先の `InvitationSchemaIT`・`InvitationConcurrencyIT` も書いた）、(2) は過去の戻しの練習として残した。V9 の行を足した（前進のみ、移行のテストと戻しの練習は置かない、戻している間は停止が効かない、無効にしたトークンは無効のまま、戻す前の確かめは配備の段で決める）。提供口の表の `AuthenticatedUser`（文字列化でメールアドレスを伏せる）と `TokenFailureReason`（`USER_SUSPENDED` と扱い）、監査の節に停止中のログインの記録を足した。戻しの節（V7・V8 の注意）は変えていない（deployment-pipeline の持ち物）。README に消したクラスと複写の置き場の名前は残っていない。
- `.idea/.gitignore` に `/dataSources.xml` の1行を足した。

## Step 22: カバレッジの一覧（`packagesJudgedByTotal`）

- `git diff --name-only develop -- backend/src/main` と新しいファイル（`auth.service` の2つと V9）で、本体を変えたパッケージは `access.domain`・`audit.domain`・`auth.domain`・`auth.repository`・`auth.service`・`auth.web`・`user.domain`・`user.repository`・`user.service`。このうち一覧にあったのは `access.domain`・`auth.domain`・`auth.repository` の3つで、計画の見込みどおり。
- `backend/build.gradle.kts` の `packagesJudgedByTotal` から3つを消し、説明文に1行足した（一覧は 12 から 9 へ）。計測の除外は増やしていない。

## Step 23: 1コマンドの検査（統合の前の関門）

- コマンド: colima が動いていることを確かめ（`colima status`）、`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（作業ブランチ `feature/260930-user-admin-b1` の作業フォルダ、未コミットの変更を含む）。
- 結果: **BUILD SUCCESSFUL**（6 分 20 秒。基準 6 分 26 秒）。フォーマット・リンタ・ライセンスヘッダー・ビルド・単体・結合・カバレッジ・`gitleaksScan`・`spotbugsGate`・`osvScan`・成果物の段がすべて通った。`osvScan` は UP-TO-DATE（入力の lockfile が前の通過の実行から変わっていないため、Gradle が前の結果を使った）。対象DB のテストは飛ばされていない（飛ばした 0）。
- テストの件数（JUnit の結果の XML から）:

| 種類 | 基準（782a9f6） | 今回 | 失敗・エラー・飛ばした |
|---|---|---|---|
| バックエンドの単体 | 1243 | 1282（+39） | 0 |
| バックエンドの結合 | 566 | 587（+21） | 0 |
| フロントエンド（Vitest、91 ファイル） | 732 | 変更なし（91 ファイルすべて通過） | 0 |

- 件数の説明: 単体の +39 は Step 10〜14 の +31 と Step 16 の +8（`AccessTokenAuthenticationProviderTest` 7・`WebSecretTypesTest` 1）。結合の +21 は、消した V7・V8 のテスト4つの −17 と、足した +38（Step 4・5 の 2、Step 12 の 8、Step 14 の 9、Step 17 の 15、Step 19 の 3、Step 20 の 1）。
- カバレッジ（`backend/build/reports/jacoco/test/jacocoTestReport.xml`）:

| 範囲 | 基準 行 | 基準 分岐 | 今回 行 | 今回 分岐 |
|---|---|---|---|---|
| 全体 | 98.8%（5607/5676） | 94.4%（2056/2178） | 98.8%（5652/5719） | 94.5%（2069/2190） |
| `auth.domain`（一覧から外した） | 97.9%（94/96） | 100.0%（18/18） | 98.0%（98/100） | 100.0%（18/18） |
| `auth.repository`（一覧から外した） | 93.9%（31/33） | 50.0%（1/2） | 100.0%（33/33） | 100.0%（2/2） |
| `access.domain`（一覧から外した） | 100.0%（52/52） | 100.0%（28/28） | 100.0%（53/53） | 100.0%（29/29） |
| `auth.service` | 99.0%（199/201） | 91.7%（55/60） | 99.1%（221/223） | 92.4%（61/66） |
| （うち `RefreshTokenRevocationService`・`RevokeAllResult`） | — | — | 100.0%（11/11）・100.0%（4/4） | 分岐なし・100.0%（2/2） |
| `auth.web` | 100.0%（120/120） | 95.0%（19/20） | 100.0%（123/123） | 95.5%（21/22） |
| `audit.domain` | 99.5%（212/213） | 97.7%（43/44） | 99.5%（214/215） | 97.8%（44/45） |
| `user.domain` | 99.5%（206/207） | 97.6%（121/124） | 99.5%（208/209） | 97.6%（121/124） |
| `user.repository` | 計数なし | 計数なし | 計数なし（インターフェースだけ） | 計数なし |
| `user.service` | 100.0%（227/227） | 95.5%（84/88） | 100.0%（236/236） | 95.6%（86/90） |
| `invitation.repository` | 100.0%（2/2） | 分岐なし | 100.0%（2/2） | 分岐なし |
| `invitation.service` | 100.0%（332/332） | 93.5%（87/93） | 100.0%（332/332） | 93.5%（87/93） |
| `invitation.web` | 97.3%（107/110） | 87.0%（20/23） | 97.3%（107/110） | 87.0%（20/23） |

- 外した3つと、手を入れた一覧の外のパッケージ・テストを消したパッケージは、すべて行 80%・分岐 70% を満たした。テストを足す必要は無かった。全パッケージで下限を下回るのは `common.health`（行 79.2%、一覧に残る。手を入れていない）だけ。
- `backend/config/spotbugs-exclude.xml`・`backend/gradle.lockfile`・`frontend/package-lock.json` に差は無い（`git diff --stat` で 0）。

## Step 24: E2E（統合の前に手元で）

- Mailpit: このセッションの前から compose の profile `mail` で動いていた（`mastersmith-mailpit-1`、healthy、`/api/v1/info` が 200）。そのため起動はせず、終わった後も止めていない（もとから動いていたものは止めない、の依頼のとおり）。`.env` は開いていない。
- コマンド: `caffeinate -i ./gradlew e2eTest` → **BUILD SUCCESSFUL**（3 分 51 秒）。
- 結果（`frontend/test-results/e2e-results.json` の `stats`）: 成功 110・失敗 0・飛ばした 0・不安定 0（225 秒）。ファイルごと（すべて成功）:

| ファイル | 件数 |
|---|---|
| `010-skeleton.e2e.ts` | 2 |
| `020-auth.e2e.ts` | 2 |
| `030-admin-access.e2e.ts` | 1 |
| `040-dsl-admin.e2e.ts` | 1 |
| `050-display-accessibility.e2e.ts` | 21 |
| `060-invitation-accessibility.e2e.ts` | 21 |
| `070-registration-accessibility.e2e.ts` | 20 |
| `080-preferences-accessibility.e2e.ts` | 21 |
| `090-invitation-registration-flow.e2e.ts` | 1 |
| `100-app-text-contrast.e2e.ts` | 20 |

- 報告の片付け（`gate-decisions.md` の U5 R-02 の決定）: 結果を上に記録した後、`frontend/test-results/`（JSON のファイル 2 件と空のディレクトリ）と `frontend/playwright-report/`（HTML のファイル 1 件）を消した（ファイル 3 件、ディレクトリ 106 個）。読んだのは JSON の `stats` とファイルごとの結果の状態だけで、報告は誰にも共有していない。どちらも `.gitignore` の対象で、`git status` に出ない。

## 依頼者の決定（Step 24 の後、2026-10-02）

- **G-5〜G-9**: 差（`AccessTokenAuthenticationProviderTest` を 7 件、更新の失敗は Cookie を消す指示だけを返すことの確かめ、`/api/admin/check` の 204、`MePreferencesApiIT`・`MePasswordApiIT` を 2 件ずつ、`AuthSuspensionSecretLeakIT` の残る漏えいの範囲の確かめ）は受け入れ。
- **Step 1 の最後のチェックボックス**（「対応: B1 の共通の完了の条件、…」の行）: 付ける。B1 の共通の完了の条件は Step 23（verify）・Step 24（E2E）で満たした。

## OSV-Scanner の流し直し（統合の前、2026-10-02）

- Step 23 の verify では `osvScan` が UP-TO-DATE（前の通過の結果を使った）だったため、統合の前に `--rerun-tasks` で流し直した。
- 結果: **通過**。走査したパッケージの数は backend 252・frontend 382・make-you-chic-ui 404。

## Step 25: 記録（2026-10-02）

- `code-summary.md` を仕上げ（下書きの印を外し、依頼者の決定・コミットの区切りの案・統合の件名と本文の案を足した）、`source-manifest.json` と `traceability.json` を作った。
- コミット・`git add`・統合・push はしていない（依頼者の承認を得て後で行う）。計画の Step 25 のチェックボックスは、記録の項目（1つ目）だけ付けた。

## コード生成のレビューの指摘と依頼者の決定（2026-10-02）

レビュー（`aidlc-architecture-reviewer-agent`、1回目、判定 READY）の指摘と、依頼者の決定:

| ID | 重さ | 指摘の要点 | 依頼者の決定 |
|---|---|---|---|
| R-01 | Major | `UserAccountService#verifyPassword(String, Password)`・`#existsByEmail(String)` の `String` の引数が TRACE でメールアドレスを出し、Forbidden「メールアドレスをアプリのログに含めない」との差が残る。`AuthSuspensionSecretLeakIT` はこの漏れを許す形で固定している | **B1 で直す**（計画の Q-C B からの変更）。`verifyPassword` の引数を `RedactedText` にし、`existsByEmail(String)` を消して呼び出し側を `existsByEmail(RedactedText)` に替え、漏えいのテストを「出力のどの行にも出ない」に強める |
| R-02 | Major | V9 の後に1つ前の版へ戻すと、停止中の利用者が3つの入口で再び受け付けられる。README にその注意が無い | **README の戻しの節に注意を1〜2行足す**。手順は deployment-pipeline で決めると書く |
| R-03 | Minor | `setSuspended` の後に先に読み込んだエンティティが切り離される約束を、B4 で確かめる仕組みが無い | **B4 の計画で確かめを必須にする** |
| R-04 | Minor | `team.md` の「12 パッケージ」の記述が実際（9）とずれた | **`team.md` を書き換えない**（日付つきの記述のため） |
| R-05 | Minor | 区切りごとの verify を流していない | **squash の1コミットで統合するため、そのまま** |

### R-01 の直し

- `user.service.UserAccountService#verifyPassword` の引数を `(RedactedText email, Password password)` にした。`Objects.requireNonNull(email, "email")` の後に `email.value()` をそろえて使う。照合の手順（検索1回・照合1回・ダミーのハッシュ）は変えていない。
- `UserAccountService#existsByEmail(String)` を消した。`existsByEmail(RedactedText)` の説明文から「文字列の口は据え置く」を外し、初期管理者の経路も使うことを書いた。
- 呼び出し側: `auth.service.LoginService#login` は `new RedactedText(command.email())` で渡す（`RedactedText` は `user.domain` の record で、`AuthBoundaryArchitectureTest` が禁じる `user.domain` のエンティティではない）。`user.service.InitialAdminInitializer#createIfNeeded` は `existsByEmail(new RedactedText(email))` にした（初期管理者がいないときの作成は今までどおり `createUser` の中の `existsByRedactedEmail` で確かめる）。
- ほかの呼び出しが無いことを確かめた: `backend/src` で `verifyPassword(`・`existsByEmail(` を検索し、本体は `LoginService`・`InitialAdminInitializer`・`InvitationService`・`RegistrationService`（後の2つはもとから `RedactedText`）だけ、テストは下の直したものだけだった。
- `user.repository.UserRepository#existsByRedactedEmail` の説明文の「既存のログインと初期管理者の経路の `findByEmail(String)` は据え置く」を、初期管理者もこの口を使う（B1 で移した）、ログインの照合は `findByEmail(String)` を使う、に直した（説明文だけ。`user.repository` は一覧の外）。
- テスト:
  - `user/service/UserAccountServiceTest`: `verifyPassword` の呼び出しを `RedactedText` に替え、`existsByEmail(String)` のテスト1件を消し、`verifyPassword rejects a null email before any lookup or password check`・`the email lookups of the public operations take the email only as a redacted value, never as a String`（公開の `verifyPassword`・`existsByEmail` の最初の引数がすべて `RedactedText` であることをリフレクションで確かめる）の2件を足した（20 件）。
  - `auth/service/LoginServiceTest`・`user/service/InitialAdminInitializerTest`: モックの引数を `RedactedText` にした（`RedactedText` は record で値の等しさで比べる）。
  - `auth/web/AuthSuspensionSecretLeakIT`: 最後の「メールアドレスが出る行は2つの ENTER の行に限る」の確かめを、(1) `ENTER UserAccountService#verifyPassword(`（操作の範囲）と `ENTER UserAccountService#existsByEmail(`（起動の初期管理者の確かめ。出力の全体から探す）の行が出ていて `***` を含むこと、(2) 起動から最後の要求までの出力（`CapturedOutput#getAll`、標準出力と標準エラー）のどの行にも、利用者と初期管理者のメールアドレスが無いことに替えた。説明文（クラスの Javadoc）も合わせた。ほかに出る行は無く、止めて報告する場合には当たらなかった（ログインの照合の経路の `UserRepository#findByEmail(String)` も、出力にメールアドレスを出していない）。
  - `invitation/web/InvitationSecretLeakIT`: クラスの説明文の「初期管理者の `existsByEmail(String)` は据え置き」を直した（説明文だけ）。
- 既存の初期管理者の起動の確かめ: `InitialAdminInitializerTest`（8 件）・`InitialAdminIT`（3 件）・`AuthSecretLeakIT`（1 件）が通った。
- `README.md` の U3 の C2 の記述（`existsByEmail(String)` とログインの経路は据え置き）を、B1 で `RedactedText` で受ける形にそろえた（`existsByEmail(String)` は消した）ことに直した。

### R-02 の直し

- `README.md` の「戻し方」の節の V8 の注意の前に、V9 の段落を1つ足した: V9 を当てた後に1つ前の版へ戻すと、戻している間は利用停止が効かなくなる（1つ前の版は停止の列を読まないため、停止中の利用者も3つの入口で受け付けられる）。戻す前に停止中の利用者を確かめ、いたときの扱いを含む手順は deployment-pipeline で決める。

### 流したテストと実測

- 直したクラスのテスト: `./gradlew :backend:test --tests '…UserAccountServiceTest' --tests '…InitialAdminInitializerTest' --tests '…LoginServiceTest' --tests '…AuthBoundaryArchitectureTest' --tests '…ArchitectureTest' :backend:integrationTest --tests '…AuthSuspensionSecretLeakIT' --tests '…InitialAdminIT' --tests '…AuthSecretLeakIT' --tests '…LoginApiIT' --tests '…InvitationSecretLeakIT'` → すべて通過（単体 20・8・18・4・5、結合 1・3・1・6・2）。
- 1コマンドの検査: colima が動いていることを確かめ（`colima status`）、`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を渡して `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → **BUILD SUCCESSFUL**（6 分 14 秒）。すべての段が通った。`osvScan` は UP-TO-DATE。
  - 件数（JUnit の結果の XML）: 単体 1283（Step 23 から +1）・結合 587（同じ）、失敗・エラー・飛ばした 0。フロントエンド（Vitest）91 ファイル・732 件。
  - カバレッジ: 全体 行 98.8%（5653/5720）・分岐 94.5%（2069/2190）。`auth.service` 99.1%（222/224）・92.4%（61/66）、`auth.web` 100.0%（123/123）・95.5%（21/22）、`auth.domain` 98.0%（98/100）・100.0%（18/18）、`auth.repository` 100.0%（33/33）・100.0%（2/2）、`access.domain` 100.0%（53/53）・100.0%（29/29）、`user.service` 100.0%（236/236）・95.6%（86/90）、`user.domain` 99.5%（208/209）・97.6%（121/124）、`user.web` 96.5%（55/57）・84.6%（11/13）、`user.repository` 計数なし、`audit.domain` 99.5%（214/215）・97.8%（44/45）、`invitation.repository` 100.0%（2/2）、`invitation.service` 100.0%（332/332）・93.5%（87/93）、`invitation.web` 97.3%（107/110）・87.0%（20/23）。`user.config` というパッケージは無い。すべて下限（行 80%・分岐 70%）を満たす。
- OSV-Scanner: `caffeinate -i ./gradlew osvScan --rerun-tasks` → 通過（backend 252・frontend 382・make-you-chic-ui 404 のパッケージを走査、失敗の条件に当たるもの 0 件、警告 14 件。警告はすべて `vendor/make-you-chic-ui/package-lock.json` の開発用の npm の部品 brace-expansion・undici）。
- `backend/config/spotbugs-exclude.xml`・`backend/gradle.lockfile`・`frontend/package-lock.json`・`docker/monitoring/` に差は無い。
- E2E: Mailpit が動いている（`mastersmith-mailpit-1` が healthy、`/api/v1/info` が 200）ことを確かめ、`caffeinate -i ./gradlew e2eTest` → **BUILD SUCCESSFUL**（3 分 50 秒）。`frontend/test-results/e2e-results.json` の `stats`: 成功 110・失敗 0・飛ばした 0・不安定 0（225 秒）。ファイルごとの件数は Step 24 の表と同じで、すべて成功。結果を記録した後、`frontend/test-results/`・`frontend/playwright-report/` を消した（ファイル 3 件、ディレクトリ 106 個）。報告は誰にも共有していない。Mailpit はもとから動いていたため止めていない。
- コミット・`git add`・統合・push はしていない。計画（`code-generation-plan.md`）の本文は変えていない。
