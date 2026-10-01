# Security Design — U1 利用停止の状態と3つの入口（u1-user-suspension）

U1 のセキュリティの設計です。承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/nfr-requirements/security-requirements.md`（NFR1.1〜NFR11.2、残る危険 R1〜R3、承認の場の決定 R-01〜R-05）と `tech-stack-decisions.md`（NFR9.5・NFR9.6）を満たす作りを決めます。U1 は種類 library の単位のため、性能・信頼性・観測の作りは別の文書にせず、この文書の 6節・7節に置きます。部品の一覧と障害の範囲は `logical-components.md` にあります。

この段の質問 `nfr-design-questions.md` の設計の要点 1〜18 と答え（Q1 B・Q2 A・Q3 A・Q4 A）、まとめの確認（Looks correct）で決めました。プラットフォームの視点（配備先は開発者の PC 上のコンテナ）は 8.4 に重ねて書きました。

出典の略号: NR は `nfr-requirements/security-requirements.md`、TS は `nfr-requirements/tech-stack-decisions.md`、FS は `functional-design/functional-spec.md`、BR は `functional-design/rules.md` の決まり、要点 n はこの段の `nfr-design-questions.md` の設計の要点、CS は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。コードのパスは `backend/src/main/java/cherry/mastersmith/` の下を `cherry.mastersmith` からのパッケージ名で書きます。

## 1. 信頼の境界と守りの配置

| 境界 | 入ってくるもの | 守り | 置き場 |
|---|---|---|---|
| ログインの要求（`POST /api/auth/login`） | メールアドレス・パスワード | 停止の判定をロックの判定より前に置き、ほかの失敗と同じ応答と読み書きの回数にする | `auth.service.LoginService#decide`（2.1） |
| トークンの更新（`POST /api/auth/session/refresh`） | リフレッシュトークン | 利用者を読んだ直後に停止を判定し、例外で巻き戻す | `auth.service.TokenRefreshService#refresh`（2.2） |
| Bearer の要求すべて | アクセストークン | 利用者を読んだ直後に停止を判定し、既存の 401 の入口で返す | `auth.web.AccessTokenAuthenticationProvider#authenticate`（2.3） |
| U3 の止める・解く処理（アプリの中の呼び出し） | 利用者 ID・真偽 | 呼び出し元のトランザクションを必須にし、停止の列だけを書く。利用者自身の API には経路を作らない | `user.service.UserAccountService`・`auth.service.RefreshTokenRevocationService`（4節） |
| 監査（アプリの中の出来事） | LOGIN_FAILED と理由 | 既存の確定の後の記録の経路をそのまま使う | 既存の AuditLog（6節） |

停止の状態は、内部DB の `users.suspended` だけが正です。アプリのメモリに写し（キャッシュ）を持たず、要求ごとに3つの入口がすでに読んでいる利用者の要約から判定します（TS の「選ばなかったもの」）。

## 2. 3つの入口の停止の判定（NFR1.1・NFR1.2・NFR2.1〜NFR2.3・NFR5.1）

### 2.1 判定の材料（要点 1）

- `user.service.UserSummary` に `boolean suspended` を足す。`UserAccountService` の `verifyPassword`（`findByEmail` で読む）と `findById` は、今の読み取りで得たエンティティ `User` から値を写すだけにする。読み取りの回数は変わらない（NFR5.1）。
- `UserSummary` の `toString` は、真偽をそのまま出す（個人に関する値ではない）。メールアドレスと氏名を伏せる今の形は変えない。
- `UserSummary` を作る既存の箇所（ログイン・更新の応答の写しなど）は、引数が1つ増えるだけで振る舞いを変えない。
- 判定はどの入口でも「要約の `suspended` が true なら拒否」の1つだけとし、入口ごとに別の問い合わせを足さない。

### 2.2 ログインの照合（要点 2、NFR1.1・NFR2.1・NFR2.2）

`LoginService#decide` で、本人のロックの状態の行を排他つきで読んだ直後、`LockPolicy.decide` の前に停止を判定します。停止中は、今の「存在しないメールアドレス」の枝と同じ形で、読んだ値のまま1回書き戻し、出来事を1件知らせて失敗にします。

```java
// decide の中、本人の行を lockForUpdate で読んだ後（説明用）
if (user.suspended()) {
    attemptRepository.update(user.userId(), current.consecutiveFailures(), current.lockedUntil());
    publishFailure(verification.email(), user.userId(), LoginFailureReason.ACCOUNT_SUSPENDED, now, client);
    return Decision.FAILED;
}
LockDecision decision = LockPolicy.decide(/* 今までどおり */);
```

- `attemptRepository.update` は今と同じ明示の更新の問い合わせのため、同じ値でも更新の文が1回出る（エンティティの変更の検出に頼らない）。そのため、停止中の読み書きは「照合1回・排他つきの読み取り1回・更新1回・出来事1件」になり、パスワードの誤りとそろう（NFR2.2、FS の 2.1 の表）。
- 本人の行が無いときの「何も書かずに終える → 別の短いトランザクションで行を作る → やり直す」流れは停止の判定より前にあるため、停止中とパスワードの誤りは同じ回数のままになる。
- 停止はロックより前に判定するため、停止中かつロック中の利用者の理由は `ACCOUNT_SUSPENDED` になる（FS の 3節）。
- 停止中のログインの試みは、失敗回数に数えない（読んだ値のまま書き戻すため、回数は増えずロックも掛からない。FS の 3節）。停止を解いた直後は、止める前の回数から続く。この振る舞いはテストの名前にも入れて確かめる（例: `suspended login attempts do not increase the failure count`）。承認の場の決定 R-06。
- 応答は、失敗のすべてと同じ 401 AUTHENTICATION_FAILED（既存の変換のまま）。
- 判定に使う `suspended` は、トランザクションの外の照合で読んだ要約の値で、判定のトランザクションで読み直さない（受け入れた隙、3節）。

確かめ: 既存の `LoginServiceTest` の形で、`auth/testsupport` の `CountingPasswordEncoder`・`SqlStatementCounter` を使い、停止中（正しいパスワード・誤ったパスワード・ロック中・本人の行なし）とパスワードの誤りの、照合と読み書きの回数が同じことを並べて比べる。時間は測らない（NR の S-D1、残る危険 R3）。

### 2.3 トークンの更新（要点 3、NFR1.1・NFR2.1）

- `TokenRefreshService#refresh` で、`userAccountService.findById` の直後に `suspended` を見て、今の失敗と同じ例外（401 REFRESH_FAILED）を投げる。
- 例外でトランザクションが巻き戻るため、直前の `revokeIfActive` による条件つきの無効化も戻り、送られたリフレッシュトークンは変わらない（BR3.1）。
- 監査は出さない（契約 C7 の not_recorded、BR3.2）。

### 2.4 アクセストークンの認証（要点 4、NFR1.1・NFR2.1・NFR2.3）

- `auth.domain.TokenFailureReason` に `USER_SUSPENDED` を足す。`AccessTokenAuthenticationProvider#authenticate` は `findById` の直後に `suspended` を見て、`TokenAuthenticationException(TokenFailureReason.USER_SUSPENDED)` を投げる。入口と応答（401 AUTHENTICATION_REQUIRED）は今のまま。
- `access.domain.AccessDeniedReason` の変換の `switch` に `USER_SUSPENDED -> Optional.empty()` を足す（`TOKEN_EXPIRED` と同じく理由なし）。管理の API（`/api/admin/` の下）でも、アクセスの拒否の監査に残らない（NFR2.3、FS の 8節の D2）。
- 変換は区分を尽くす `switch` のため、区分を足して変換を書き忘れるとコンパイルが止まる。加えて、列挙の値を尽くす単体テストで変換の結果を確かめる（TS、要点 4）。
- 停止中の管理者の要求は、認証の段で 401 になり、認可と業務の処理に届かない（BR4.4）。
- 停止を解いた確定の後の次の要求から、同じ読み取りで受け付ける（NFR1.2）。

### 2.5 応答をそろえる（NFR2.1）

| 入口 | 停止中の応答 | 同じ応答になるほかの失敗 | 応答に載せないもの |
|---|---|---|---|
| ログイン | 401 AUTHENTICATION_FAILED | パスワードの誤り・ロック中・存在しないメールアドレス | 停止を示す値・メールアドレス |
| 更新 | 401 REFRESH_FAILED | 無い・無効・期限切れ・同時の更新に負けた | 停止を示す値・トークンの値 |
| アクセストークンの認証 | 401 AUTHENTICATION_REQUIRED | 形式・署名・期限切れ・利用者がいない | 停止を示す値・失敗の区分 |

新しいエラーの code と説明文は足しません（BR6.1）。確かめは入口ごとに、停止中とほかの失敗の状態コード・code・本文の項目が同じことを結合テストで比べます。

## 3. 止める前のトークンと受け入れた隙（NFR1.3・NFR1.5）

- リフレッシュトークン: 止めるときに U3 が同じトランザクションで `revokeAllRefreshTokens` を呼び、未無効の行をまとめて無効にする（4.2）。停止を解いても無効にした行は戻さない。解いた後の更新は、既存の「無効にした行」の判定で 401 REFRESH_FAILED になる。
- アクセストークン: 停止中は 2.4 で拒否する。解いた後は有効期限（既定 5 分）まで使える。失効の一覧は持たない（PM の Decided）。
- 隙（NFR1.5、R1）: 止める確定の前に同時に走った更新、またはログインの照合の後・判定の前に止める処理が確定した場合に作られた新しいトークンは残りうる。判定のトランザクションでの読み直しは足さない（足すと 2.2 の回数がそろわなくなり NFR2.2・NFR5.1 に響く）。残ったトークンは停止の間は次の要求で拒否される。
- 確かめ: 時刻は `auth/testsupport` の `MutableClock`（注入した時計）で動かし、FS の 2.4 の例（期限の直前は受け付け、ちょうど以後は拒否）を `sleep` と実時刻に頼らずに確かめる（NFR9.3）。受け入れた隙はテストで固定しない。

## 4. 停止の状態を変える口と一括代入の防止（NFR1.4・NFR11.2）

### 4.1 `setSuspended`・`isSuspended`（要点 6）

- `user.repository.UserRepository` に、停止の列だけを書き換える名前つきの引数の JPQL の更新を足す。属性は `@Modifying(clearAutomatically = true, flushAutomatically = true)` とする。既存の `updatePreferences`・`updatePasswordHashIfUnchanged` は `clearAutomatically` だけだが、こちらは実行の前に未反映の変更を書き出す点が違う（既存の `RefreshTokenRepository#revokeIfActive` と同じ属性）。
- `UserAccountService#setSuspended(long userId, boolean suspended)` は `@Transactional(propagation = Propagation.MANDATORY)`。更新した行が 0 なら `IllegalStateException`。トランザクションの外から呼ぶと Spring が `IllegalTransactionStateException` を投げる。
- `UserAccountService#isSuspended(long userId)` は読み取りだけのトランザクション。利用者がいなければ `IllegalStateException`（FS の 8節の D3）。
- 更新の後に永続化の文脈を空にするため、同じトランザクションの後の `isSuspended`・`findById` は内部DB から読み直し、書いた値を返す（BR1.4、R-05）。

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE User u SET u.suspended = :suspended WHERE u.userId = :userId")
int updateSuspended(@Param("userId") long userId, @Param("suspended") boolean suspended);
```

### 4.2 `revokeAllRefreshTokens`（要点 7）

- `auth.service` に `RefreshTokenRevocationService` を新しく作り、`revokeAllRefreshTokens(long userId)` を `@Transactional(propagation = Propagation.MANDATORY)` で置く。戻り値は `auth.service` の record `RevokeAllResult(int revoked)`。
- `RefreshTokenRepository` に「利用者 ID で引き、`revokedAt` が空の行だけに今の時刻を入れる」1回の更新を足す。既存の `ix_refresh_tokens_user_id` で引き、期限切れの行も対象にする。属性は `revokeIfActive` と同じ。時刻は注入した `Clock` から読む。
- 0 件でも成功とし、監査の出来事は出さない（BR5.1・BR5.3）。

### 4.3 一括代入の防止（NFR1.4）

停止の状態を変える経路は 4.1 の更新の問い合わせだけです。利用者自身の API（`/api/me` など）の要求の DTO は停止の項目を持たず、JSON の知らない項目は Jackson の既定で読み捨てます。エンティティ `User` の `suspended` には、外から呼べる書き換えのメソッドを作りません。確かめは、既存の利用者自身の API の本文に `suspended` を足して送り、状態が変わらないことをサーバー側の結合テストで見ます。

呼び出し元（U3）が守ること（機能設計のレビューの R-06）は `logical-components.md` の 3節に書きます。

## 5. 秘密と個人に関する値（NFR3.1・NFR3.2）

| 出るところ | 出してよいもの | 出さないもの |
|---|---|---|
| 停止中のログインの出来事（LOGIN_FAILED・ACCOUNT_SUSPENDED）と監査の行 | 利用者 ID・理由・送り手の情報・トレースID。監査の行の入れたメールアドレスの列（`entered_email`）は、パスワードの誤りと同じく既存の形のまま入る（BR2.5、12節の S-8） | パスワード（平文・ハッシュ）・トークンの値。出来事の文字列化（TRACE・ログ）でのメールアドレス（5.1 で `AuthenticationEvent` の `toString` を上書きして伏せる）。新しい項目は足さない |
| 3つの入口のアプリのログ | 既存の変換の境界の WARN（code だけ） | メールアドレス・パスワード・トークンの値・停止を示す値 |
| まとめての無効化のログ | DEBUG で利用者 ID と件数だけ | トークンの値・ハッシュ |
| TRACE のログ（`TraceAspect`） | C1 の口の引数と戻り値（利用者 ID・真偽・件数）、`UserSummary` の真偽 | `UserSummary` のメールアドレス・氏名（今の `toString` で伏せる） |
| エラー応答 | 既存の Problem Details | 停止を示す値・内部の例外のメッセージ |

- C1 の口は、個人に関する値を受け渡さない型だけにする（利用者 ID・真偽・`RevokeAllResult`）。伏せ字の型は要らない（NFR3.2）。
- 確かめ: 既存の `*SecretLeakIT` の形で、TRACE を有効にして停止中のログイン・更新・アクセストークンの認証・まとめての無効化を通し、ログと応答に値が無いことを見る。確かめる値は、パスワード（平文・ハッシュ）・トークンの値と、出来事の文字列化でのメールアドレス（5.1）とする。
- 既存の漏えいのテストの列の一覧には V9 の列を足さない。列を閉じた一覧で確かめるのは `AuditSecretLeakIT` の監査の表だけで、V9 は利用者の表を変えるため当たらない。コード生成の計画を書くときに、`INFORMATION_SCHEMA.COLUMNS` を読むテストを名前で検索し直す（NR の R-02）。

### 5.1 既存の `AuthenticationEvent` の文字列化の直し（承認の場の決定、レビューの R-01）

今の `auth.domain.AuthenticationEvent` は `toString` を上書きしていない record で、`enteredEmail` がそのまま文字列に出ます。`TraceAspect` は `..service..` の下を対象にするため、`audit.service.AuditEventListener#onAuthenticationEvent(AuthenticationEvent)` の引数として、TRACE を有効にするとメールアドレスがアプリのログに出る見込みです（レビューで読みだけで確かめた）。停止中のログインに限らず、既存のログインの失敗と成功のすべてに当たる既存の漏えいで、依頼者の決定でこの Intent の B1 で直します。

- `AuthenticationEvent` に `toString` の上書きを足し、`enteredEmail` を `***` に伏せる（`UserSummary` と同じ形）。ほかの項目（種類・日時・利用者 ID・理由・送り手の情報・トレースID）は今のまま出す。`auth.domain` は `user.domain` に依存させないため、`EmailAddress.mask` は使わず固定の `***` とする。
- record の項目・`of`・監査の行への写し（`AuditEventFactory#from`）は変えない。監査の行の `entered_email` には今までどおり値が入る（12節の S-8、R-03 の受け入れ）。
- 確かめ: TRACE を有効にした漏えいのテストで、ログインの成功・パスワードの誤り・停止中のログインを通し、入れたメールアドレスが `AuditEventListener#onAuthenticationEvent` の TRACE の行に出ないことを見る。既存の `AuthSecretLeakIT` を広げる（`logging.level.cherry.mastersmith.audit=TRACE` を足し、`ENTER AuditEventListener#onAuthenticationEvent` の行が出ていることと、その行にメールアドレスが無いことを確かめる）か、新しいテストを足すかは、コード生成の計画で決める。あわせて `AuthenticationEvent#toString` の単体テスト（伏せ字になる・ほかの項目は出る）を1件足す。
- 確かめの範囲: 確かめるのは `AuthenticationEvent` の文字列化の行だけとする。`auth` には、ほかにもメールアドレスを持ち `toString` を上書きしていない record（`auth.service.LoginCommand`・`auth.domain.AuthenticatedUser`・`auth.web.CurrentUserResponse`）があり、TRACE のログ全体からメールアドレスが無いことを確かめると、これらで落ちる見込みである。これらはこの決定の外で、扱いを承認の場への申し送りとする（末尾の「承認の場の決定」の節）。
- `auth.domain` に手を入れることは 9.2 の見込みと同じで、一覧から外すパッケージは変わらない。

## 6. 監査と信頼性（NFR6.1・NFR6.2）

- `auth.domain.LoginFailureReason` と `audit.domain.AuditFailureReason` に `ACCOUNT_SUSPENDED` を足し、`audit.domain.AuditEventFactory` の変換に1行足す（要点 5）。`audit_events.failure_reason` は `VARCHAR(32)` で CHECK の制約が無く、`ACCOUNT_SUSPENDED` は 17 文字のため、スキーマの変更は要らない（BR7.2）。
- 経路は、パスワードの誤りと同じ「確定の後に2本目の接続で記録する」既存の経路だけで、接続を2本使う新しい経路は足さない。更新とアクセストークンの認証は監査を出さないため、2本目を借りない。接続プール（上限 30）の使い方は変わらない（PM の Corrections の「接続を2本使う経路」に当たる新しい経路は無い）。
- 監査の書き込みに失敗しても、停止中のログインの応答と判定の結果は変えない。失敗は既存どおりアプリのログに ERROR で残る（既存の AuditLog の決まり）。
- 確かめ: 停止中のログインの監査の行が、パスワードの誤りと同じ経路で残ること（結合テスト）、監査の書き込みの失敗のテストの形で応答が変わらないこと。

## 7. 性能・規模・観測（NFR5.1〜NFR5.5）

| ID | 作り | 確かめと持ち主 |
|---|---|---|
| NFR5.1 | 3つの入口は今の読み取りで得た要約を見るだけで、内部DB の問い合わせを増やさない（2.1） | `SqlStatementCounter` で3つの入口の問い合わせの回数が変わらないこと（code-generation）。既存の k6 のログイン・更新の場面（performance-validation） |
| NFR5.2 | まとめての無効化に U1 だけの時間の目標は置かず、U3 の止める操作の p95 1 秒に含める | U3 の止める操作の場面を、未無効 100 件・無効 1,000 件の悪い側の条件で測る（performance-validation） |
| NFR5.3 | 利用者 ID の索引で引く1回の更新、未無効の行だけ、0 件でも成功（4.2） | 結合テストで件数だけ確かめる。時間は比べない（code-generation） |
| NFR5.4 | 利用者の行に列を1つ足すだけで行の数は増えない。1人の未無効の行は約 80 件の見込み | 見込みの根拠は NR の NFR5.4 のとおり |
| NFR5.5 | 新しい指標・警報・キャッシュを足さない。停止中のログインと更新は既存の HTTP の指標と `ms-login-p95`・`ms-refresh-p95` に含まれ、理由は監査ログで追える | `docker/monitoring/provisioning/alerting/mastersmith.yaml` が変わらないことをレビューで確かめる（code-generation） |

## 8. スキーマの変更 V9 と移行のテストの片付け（NFR10.1〜NFR10.3、Q1〜Q4）

### 8.1 V9 の書き方（要点 9、NFR10.1）

- `backend/src/main/resources/db/migration/V9__u1_user_suspension.sql` に、`ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL` の1文だけを置く。H2 は既存の行に既定の値を入れる。
- 適用済みの V1〜V8 は書き換えない。前進のみ。
- エンティティ `User` の `suspended` は `@Column(nullable = false)` とし、Java の側でも作るときに false を持たせる（JPA の追記はすべての列を書くため、DB の既定の値に頼らない）。

### 8.2 V9 の確かめ（Q1 B・Q4 A）

- 確かめは、起動時の Flyway の `validate-on-migrate` と Hibernate の `ddl-auto: validate`（`backend/src/main/resources/application.yaml` の今の設定）と、エンティティでの停止の状態の読み書き（true・false を書いて読み戻せる）だけで行う。
- 列の定義（BOOLEAN・必須・既定 FALSE）を `INFORMATION_SCHEMA` で確かめるテストは足さない（Q1 B）。「既存の利用者は false になる」ことは、8.1 の1文の書き方をコードのレビューで確かめる。
- 移行や戻しを想定した自動のテスト（V1〜V8 の複写、1つ前の版の Flyway、前の版の追記の形）は作らない。戻しの練習（1つ前の版のイメージを V9 の後の内部DB で起動する確かめ）も行わない（Q4 A）。どちらも NFR 要件の承認の場の依頼者の決定（マスタ管理の機能本体がまだ無いため、戻す場合を想定したテストは不要）による。差は 12節の S-1・S-2・S-9。
- このため、NFR10.1 の「既存の利用者が false になる」と NFR10.2 の2段の確かめは、依頼者の決定で確かめない（`traceability.json` では `Deferred`）。起動時の検証（`validate-on-migrate`・`ddl-auto: validate`）は列の有無と型を見るが、必須かどうかと既定の値は見ないため、この裏付けにはならない。
- 事後の裏付け: 配備の後に、deployment-execution のスモークテストで「既存の初期管理者でログインでき、利用者の読み取り（`/api/me` など）が通る」ことを、V9 の後の確認として記録する。持ち主は deployment-execution（手順は deployment-pipeline の手順書に書く）。テストは足さない。V9 の前から居る利用者の `suspended` が false で読めていることの、お金のかからない裏付けとする（承認の場の決定 R-02）。

### 8.3 V7・V8 の移行のテストの片付け（要点 11・12、Q2 A・Q3 A）

B1 の作業ブランチの中で、V9 を足す前の最初の手順として行います（Q3 A）。B1 の squash の1コミットに入り、件名と計画に「移行のテストの片付けを含む」と書きます。

| 対象 | 扱い | 理由 |
|---|---|---|
| `user/repository/V7MigrationIT.java`・`V7BackwardCompatibilityIT.java`（テスト） | 消す | 依頼者の決定。移す業務の確かめは無い（列の必須と既定の値は `UserSchemaIT` が確かめ済み） |
| `invitation/repository/V8MigrationIT.java`・`V8BackwardCompatibilityIT.java`（テスト） | 消す | 依頼者の決定。今の移行のすべてを当てて版 8 を期待するため、V9 を足すと落ちる |
| `backend/src/test/resources/db/migration-through-v6`・`migration-through-v7` | 消す | 消すテストだけが使う複写 |
| `V8MigrationIT` の (a)・(c) | (c) は移さない。(a) は `InvitationSchemaIT` の `stateChangesAndUniqueness` の一意の違反の確かめに、SQLState が 23505 であることと制約の名前 `UK_INVITATIONS_PENDING_EMAIL` を含むことの確かめを足す | `stateChangesAndUniqueness` は今は例外の型だけで確かめており、消す側の (a) より弱いため、弱まらないように足す（承認の場の決定 R-05） |
| `V8MigrationIT` の (b)（終わった状態の行は同じメールアドレスを重ねてよく `pending_email` が空、知らない状態の値は CHECK の制約で拒否） | `InvitationSchemaIT` に1件足す | Q2 A。JDBC の追記だけで書ける |
| `V8MigrationIT` の (d)（同時の挿入の待ちと一意の違反） | 移さない。`InvitationConcurrencyIT` が業務の層で確かめているとみなし、対応をコード生成の計画に書く | Q2 A。同じ決まりの確かめを重ねない |
| `V8MigrationIT` の待ちの確かめの手伝い（`INFORMATION_SCHEMA.SESSIONS` で待ちに入ったことを上限の時間つきで見る） | `auth/testsupport` に移し、`LoginAttemptStateRepositoryIT` の新しいテストで使う。移すときに、今の作り（実行中の文が `INSERT INTO invitations%` のセッションを数える、接続先は `url(tempDir)` に固定）を書き直し、絞り込みの表の名前（文の絞り込み）・接続の URL・上限の時間を引数にする | 9.2 の `auth.repository` の分岐で `login_attempt_states` の `SELECT ... FOR UPDATE` の待ちに使うため。消す前に移す。待ちに入ったことの確かめは、`lockDummyForUpdate` の待ちの上限（3 秒）より前に終わる順序にする（承認の場の決定 R-04） |
| `README.md` の V7・V8 の説明 | 自動の結合テストの名前を消したことに合わせて直し、V9 の行を足す（移行のテストを作らないこと、戻している間は停止が効かないこと） | 要点 13、NFR10.3 |

消すのはテストだけで `src/main` は変わらないため、`packagesJudgedByTotal` の扱いには関わりません。`invitation.*`・`user.repository` は一覧の外で、消した後も下限を満たすことを B1 の実測で確かめます。

### 8.4 プラットフォームの視点（配備先は開発者の PC 上のコンテナ）

- 基盤の変更は無い。新しい環境変数・ボリューム・ポート・イメージの設定を足さない。V9 は起動時に Flyway がコンテナの中の内部DB（H2 のファイル）に当てる。
- 前進のみの列の追加のため、1つ前の版のイメージは V9 の後の内部DB でも起動できる見込み（列を知らない追記は既定の値で入る）。ただし Q4 A により、この見込みは戻しの練習で確かめない。未確認の前提として 11節の R4 に残す。
- 戻している間は停止が効かない（NFR10.3、R2）。戻す前に停止中の利用者を確かめる手順と、戻しの手順そのもの（イメージだけか、内部DB の写しを使うか、配備の前のバックアップを取るか）は deployment-pipeline の持ち物で、この段では決めない。

## 9. 構造と検査（NFR11.1・NFR9.4・NFR9.5）

### 9.1 境界（NFR11.1）

- `user` は `auth` を知らない境界を保つ。まとめての無効化は `auth.service`、停止の口は `user.service` に置く。`auth` → `user.service` の向きは既存のまま。
- 新しい機能のパッケージを作らないため、新しい `<機能>BoundaryArchitectureTest` は足さない。既存の `ArchitectureTest` と機能ごとの境界テスト（`AuthBoundaryArchitectureTest` など）は緩めず、変更なしで通ることを確かめる。

### 9.2 カバレッジ（NFR9.5、要点 15・16）

- 手を入れる見込みの `auth.domain`（`LoginFailureReason`・`TokenFailureReason`）・`auth.repository`（`RefreshTokenRepository`）・`access.domain`（`AccessDeniedReason`）を `backend/build.gradle.kts` の `packagesJudgedByTotal` から外す。外す対象は生成の最後に、実際に `src/main` を変えたパッケージと突き合わせて決める。`access.service` は手を入れず一覧に残る。
- `auth.repository` の足りない分岐（`LoginAttemptStateRepository#lockDummyForUpdate` の「空いたダミーの行が無い」側）は、`LoginAttemptStateRepositoryIT` に足す結合テストで通す。別のスレッドのトランザクションでダミーの行8つ（`subject_id` が負）をすべて排他し、もう1つのスレッドで `lockDummyForUpdate` を呼ぶ。待ちに入ったことを 8.3 で移した手伝いで上限の時間つきで確かめてから、排他を持つ側を確定し、ダミーの行が1つ返ることを確かめる。`sleep` に頼らない。
- 一覧を増やさない。計測の除外を増やさない（TM の Testing Posture）。

### 9.3 静的解析（NFR9.4、要点 17）

- 足す問い合わせ（停止の列だけの更新、まとめての無効化）はすべて名前つきの引数の JPQL で、文字列をつなげない。SpotBugs ＋ FindSecBugs の関門（priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず）を、除外を足さずに通す。既存の `PREDICTABLE_RANDOM` の除外（`lockDummyForUpdate`）はそのまま。
- 新しい依存は足さない（TS）。秘密情報の検出と依存関係の脆弱性検査はそのまま通す。

## 10. セキュリティのテストの対応（NFR9.1〜NFR9.3・NFR9.6）

| 確かめること | 形 | 要件 |
|---|---|---|
| 停止中の利用者は3つの入口のすべてで拒否（ログインは正しいパスワード・誤ったパスワード・ロック中） | 入口ごとのサーバー側の結合テスト | NFR1.1・NFR9.1 |
| 停止を解いた直後は3つの入口のすべてで受け付ける | 入口ごとのサーバー側の結合テスト | NFR1.2・NFR9.1 |
| 止める前のリフレッシュトークンは解いた後も拒否、アクセストークンは解いた後は期限まで | 結合テスト、`MutableClock` | NFR1.3・NFR9.3 |
| 停止中とほかの失敗の応答が同じ | 結合テスト | NFR2.1 |
| 停止中とパスワードの誤りの回数が同じ | `LoginServiceTest` の形、`CountingPasswordEncoder`・`SqlStatementCounter` | NFR2.2 |
| `USER_SUSPENDED` は管理の API の監査に残らない、変換は区分を尽くす | 結合テストと列挙を尽くす単体テスト | NFR2.3 |
| 利用者自身の API から停止を変えられない | 結合テスト | NFR1.4 |
| ログと応答に値が出ない | `*SecretLeakIT` の形（TRACE を有効） | NFR3.1・NFR3.2 |
| 出来事の文字列化で入れたメールアドレスが伏せられる（`AuthenticationEvent#toString` の単体テストと、`AuditEventListener#onAuthenticationEvent` の TRACE の行を見る漏えいのテスト） | 単体テストと `*SecretLeakIT` の形（5.1） | NFR3.1 |
| 停止中のログインの試みは失敗回数に数えない | `LoginService` のテスト（2.2） | NFR1.1・NFR9.1 |
| C1 の口の MANDATORY・巻き戻し・存在しない利用者 ID・書いた後の読み取り | 結合テスト | NFR11.2・NFR9.2 |
| 停止中の利用者のメールアドレスへの招待が登録済みの拒否のまま | 回帰の結合テスト1件 | NFR9.2 |
| まとめての無効化の件数（未無効 100 件・無効の行とほかの利用者の行は変わらない・0 件） | 結合テスト | NFR5.3 |

テストの説明文は英語、単体は `XxxTest`・結合は `XxxIT`、手伝いは `<機能>/testsupport`、メールアドレスは予約のドメインだけ（NFR9.6）。性質ベースのテストを当てる純粋な関数は無い（TS）。

## 11. 残る危険

NR の R1〜R3 はそのまま受け継ぎます。この段の決定で、R4 を足します。

| ID | 危険 | 受け入れる根拠 | 次に確かめる機会 |
|---|---|---|---|
| R1 | 止める確定と同時の更新・ログインが作ったトークンが残り、解いた後に使えうる | NR のとおり（機能設計の承認の場の決定 R-03） | NR のとおり |
| R2 | 1つ前の版に戻している間は停止が効かない | NR のとおり（同 R-02） | NR のとおり |
| R3 | 停止中とパスワードの誤りの応答時間のわずかな違いは測っていない | NR のとおり（Q1 A） | NR のとおり |
| R4 | V9 の既存の行が false になること、1つ前の版のイメージが V9 の後の内部DB で動くことを、自動のテストでも戻しの練習でも確かめない（8.2・8.4） | 依頼者の決定（マスタ管理の機能本体がまだ無い）。列の追加は前進のみで既存の列を変えず、H2 は既定の値つきの必須の列を足すと既存の行に既定の値を入れる。起動時の検証は列の有無と型だけを見て、必須かどうかと既定の値は見ないため、裏付けにはならない。`team.md` の Deployment の「1つ前の版のアプリが動く後方互換を保つ」も確かめないまま残る（12節の S-9） | 事後の裏付けとして、配備の後の deployment-execution のスモークテストで、既存の初期管理者でログインでき利用者の読み取りが通ることを記録する（8.2）。1つ前の版が動くことは、実際に前の版へ戻す必要が出たとき、または配備先が決まったときまで確かめない。戻すときは deployment-pipeline の手順で停止中の利用者を確かめる |

## 12. 上流との差

承認済みの上流の文書は書き換えず、この段の決定で上流と違う形になった点をここに書きます（PM の決まり）。

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| S-1 | NR の NFR10.2 | 確かめは2段: (1) V8 と同じ形の自動の結合テスト（V1〜V8 だけを知る Flyway の検証、列を知らない追記、既存の列を変えないこと）、(2) 配備の段の戻しの練習 | 2段とも行わない。(1) は作らず、(2) も行わない（8.2） | Q1 B・Q4 A。NFR 要件の承認の場の依頼者の決定（戻す場合を想定したテストは不要、マスタ管理の機能本体がまだ無いため）に沿う。(1) との差は B1 のコード生成の計画にも書く。未確認の前提は R4。`team.md` の後方互換の決まりとの差は S-9。`traceability.json` では `Deferred` |
| S-2 | NR の NFR10.1 | アプリが起動し既存の利用者が false になることを結合テストで確かめる | 起動時の検証とエンティティの読み書きだけで確かめ、既存の行が false になることはテストでは確かめず、V9 の1文のレビューで確かめる | Q1 B。列の定義を `INFORMATION_SCHEMA` で確かめる案（Q1 A）は選ばれなかった。起動時の検証は既定の値を見ないため、既存の行が false になることは確かめない扱い（`traceability.json` では `Deferred`）。事後の裏付けは deployment-execution のスモークテスト（8.2） |
| S-3 | NR の承認の場の申し送り | 既存の V8 のテスト2つを版 8 に止める直しを、コード生成の計画の影響の範囲に入れる | 版を止めず、V7・V8 の移行と後方互換のテスト4つと複写の置き場を消す。業務の確かめの (b) だけを `InvitationSchemaIT` へ移す（8.3） | NFR 要件の承認の場の依頼者の決定と Q2 A・Q3 A。消すことで版を止める直しは要らなくなる |
| S-4 | NR の承認の場の申し送り | V9 の後方互換の自動の結合テストの名前・置き場・写しの置き方は nfr-design、戻しの練習の手順は infrastructure-design で決める | テストを作らないため名前・置き場・写しは決めない。戻しの練習も行わないため、infrastructure-design に渡す手順は無い。戻しの手順そのもの（停止中の利用者の確かめを含む）だけが deployment-pipeline に残る | S-1 と同じ |
| S-5 | Delivery Planning の B1（`aidlc/spaces/default/intents/260930-user-admin/inception/delivery-planning/bolt-plan.md`） | B1 は U1 の作業 | B1 の最初の手順に、V7・V8 のテストと複写の削除、待ちの確かめの手伝いの `auth/testsupport` への移し、`InvitationSchemaIT` への1件の追加、README の直しが入る | Q3 A。V9 で落ちるテストと同じ Bolt で片付けるため。量はコード生成の計画で見積もる |
| S-6 | FS の 7節 | 「V9 の後方互換の確かめ方」の持ち主は nfr-design・infrastructure-design | この段で「確かめない」と決めて閉じる | S-1 と同じ |
| S-7 | 機能設計のレビューの R-06（承認の場で申し送り） | C1 の口が呼び出し元の永続化の文脈を空にすることの扱いが書かれていない | U3 への約束として `logical-components.md` の 3節に書く。U1 の側は同じトランザクションで書いて読む結合テストで確かめ、U3 の側の確かめは U3 のコード生成の計画に置く | 申し送りのとおり。契約 C1 の文書は書き換えない |
| S-8 | NR の NFR3.1 | 停止中のログインの出来事に、メールアドレスなどの値を出さない | 監査の行の `entered_email` の列には、既存のログインの失敗（パスワードの誤り・ロック中・存在しないメールアドレス）と同じく入れたメールアドレスが入る。出さないのは、アプリのログ・トレースの属性・エラー応答と、出来事の文字列化での値とした | 機能設計の BR2.5（入れたメールアドレスは既存のログインの失敗と同じ扱い）と `entities.md` の enteredEmail、今の `AuditEventFactory#from(AuthenticationEvent)` に合わせた読み。監査の行は内部DB の記録で、PM の Forbidden（アプリのログとエラー応答）の外にある。停止中だけ列を空にすると、パスワードの誤りと記録の形が変わる。承認の場で依頼者がこの読み（`project.md` の既知の例外の読み方と同じ）を受け入れた（R-03）。出来事の文字列化での値は 5.1 で伏せる |
| S-9 | TM の Deployment | DB スキーマの変更は前進のみとし、1つ前の版のアプリが動く後方互換を保つ | V9 は前進のみの列の追加で、後方互換は設計の上では保つ見込みだが、1つ前の版が動くことを確かめない（8.2・8.4） | Q1 B・Q4 A の依頼者の決定。決まりを緩めるのではなく、確かめを置かないことを差として記録する。未確認の前提は R4。事後の裏付けは deployment-execution のスモークテスト（既存の初期管理者のログインと利用者の読み取り） |

## 承認の場の決定（Request Changes、2026-10-02）

この段の承認の場で、依頼者が Request Changes を選び、前回のレビュー（R-01〜R-06）と既存のコードの漏えいについて次のとおり決めました。

| 対象 | 決定 | この文書での扱い |
|---|---|---|
| R-01（Major）・既存のコードの漏えい ① | 直す。この Intent の B1 で、既存の `AuthenticationEvent` にメールアドレスを伏せる `toString` を足し、TRACE を有効にした漏えいのテストで確かめる | 5.1 を足し、5節の表と確かめ、10節の表に反映。`logical-components.md` の 1節・6節・7節、`traceability.json` の NFR3.1 に結んだ |
| R-02（Major） | 直す。NFR10.1・NFR10.2 を、依頼者の決定で確かめないと分かる書き方にする | 8.2 に確かめない旨と事後の裏付け（deployment-execution のスモークテスト）を足し、11節の R4 と 12節の S-1・S-2 を直し、`team.md` の後方互換の決まりとの差を S-9 に足した。`traceability.json` の NFR10.1・NFR10.2 は `Deferred` |
| R-03（Minor） | 受け入れて記録する。監査の `entered_email` は既存のログインの失敗と同じく入れる（`project.md` の既知の例外の読み方と同じ） | 12節の S-8 に受け入れを書いた。コード生成の計画に、停止中だけ列を空にしないことを書く |
| R-04（Minor） | 直す。待ちの確かめの手伝いを移すときに、絞り込みの表の名前・接続の URL・上限の時間を引数にする | 8.3 の表に書いた。待ちの確かめは `lockDummyForUpdate` の上限（3 秒）より前に終える |
| R-05（Minor） | 直す。(a) の確かめが弱まらないよう、`InvitationSchemaIT` の確かめに SQLState 23505 と制約の名前を足す | 8.3 の表に書いた |
| R-06（Minor） | 直す。停止中のログインの試みを失敗回数に数えないことを書く | 2.2 に書き、10節の表に足した |

申し送り:

| 申し送り先 | 内容 |
|---|---|
| 承認の場（依頼者） | 5.1 の確かめの範囲の外に、メールアドレスを持ち `toString` を上書きしていない既存の record（`auth.service.LoginCommand`・`auth.domain.AuthenticatedUser`・`auth.web.CurrentUserResponse`）がある。TRACE で文字列に出る見込みだが、今回の決定（①）の外のため、直すかどうかを依頼者に確かめる |
| B1 のコード生成の計画 | 5.1 の漏えいのテストを `AuthSecretLeakIT` を広げる形にするか新しく足すかを決める。R-03・R-04・R-05 の作業を手順に入れる |
| deployment-pipeline・deployment-execution | V9 の後の確認として、既存の初期管理者のログインと利用者の読み取りをスモークテストで記録する（8.2、R4、S-9） |
