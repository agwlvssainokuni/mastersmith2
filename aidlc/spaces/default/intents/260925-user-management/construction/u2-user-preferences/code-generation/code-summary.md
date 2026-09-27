# Code Summary — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

承認済みの `code-generation-plan.md` の Step 1〜21 を順に実行した記録です（Bolt B2）。作業のブランチは `develop`（d12a342）から作った `feature/260925-user-management-b2` で、コミットはしていません（Step 22 のコミットの区切りは依頼者の承認を待ちます）。パスはリポジトリのルートからの相対パスです。

## 1. 作ったもの

| 層 | パッケージ | 作った・手を入れたもの |
|---|---|---|
| ドメイン | `user.domain` | 新しい: `DisplayName`（氏名の純粋な関数）、`Language`・`Theme`・`FontSize`（列挙）と `PreferenceValue`・`PreferenceValueRules`（共通の判定）、`LanguageConverter`・`ThemeConverter`・`FontSizeConverter`、`FieldError`・`FieldErrorReason`、`PreferencesValidation`・`PasswordChangeValidation`、`Preferences`、`RequestOrigin`、`PasswordChangedEvent`・`PasswordChangeOutcome`・`PasswordChangeFailureReason`、`PasswordHash`、`UserProblemTypes`。手を入れた: `User`（4列） |
| ドメイン | `audit.domain` | `AuditEvent`（`target_user_id`・`target_invitation_id` と作り方 `withTarget`）、`AuditEventType.PASSWORD_CHANGED`、`AuditFailureReason.CURRENT_PASSWORD_MISMATCH`、`AuditEventFactory.from(PasswordChangedEvent)` |
| データの形 | `db/migration` | `V7__u2_user_preferences.sql`（users の4列、audit_events の2列） |
| DB アクセス | `user.repository` | `UserRepository.updatePreferences`（4列だけ）・`updatePasswordHashIfUnchanged`（条件つき） |
| 業務処理 | `user.service` | 新しい: `UserPreferencesService`、`PreferencesResult`・`PasswordChangeResult`、`PreferencesCommand`・`PasswordChangeCommand`、`PasswordChangeBarrier`・`NoOpPasswordChangeBarrier`、`NewUser`・`CreateUserResult`、`UserProblemTypeCatalog`。手を入れた: `UserAccountService`（`createUser(NewUser)`、`findDisplayName`・`findLanguage`、要約の4つ）、`UserSummary`、`InitialAdminInitializer` |
| 業務処理 | `audit.service` | `AuditEventListener.onPasswordChangedEvent`、失敗の ERROR の項目の組み立て |
| API | `user.web`（新しい） | `MeController`、`MeRequestContextResolver`、`PreferencesRequest`・`PreferencesResponse`・`PasswordChangeRequest` |
| API | `auth.web` | `CurrentUserResponse`（4つ）・`TokenResponse`、`AuthenticatedUserToken#getName()` の Javadoc |
| ビルド | `backend/build.gradle.kts` | `packagesJudgedByTotal` から7パッケージを外した |
| 文書 | `README.md`・`perf/README.md` | U2 の節・監査ログ・戻し方・Flyway、仮の利用者の SQL の `display_name` |

テストは単体・結合・テストの支え（`user/testsupport` の `TestUserAccounts`・`MeApi`・`TestPasswordChangeBarrier`）を足し、既存のテストを新しい形に直しました（一覧は `source-manifest.json`）。

## 2. 実測（Step 1 と Step 21）

どちらも colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` で測りました。対象DB のテストは飛ばされていません。

| 項目 | Step 1（変更の前） | Step 21（変更の後） |
|---|---|---|
| 単体テスト | 890 件（失敗 0・飛ばし 0） | 989 件（失敗 0・飛ばし 0） |
| 結合テスト | 411 件（失敗 0・飛ばし 0） | 472 件（失敗 0・飛ばし 0） |
| 全体の行カバレッジ | 98.1%（4323/4407） | 98.6%（4753/4822） |
| 全体の分岐カバレッジ | 94.3%（1620/1718） | 94.4%（1802/1908） |
| verify | 成功（5分41秒） | 成功（4分50秒） |
| 秘密情報の検出（Gitleaks） | 検出なし | 検出なし |
| 依存関係の脆弱性検査（OSV-Scanner） | 通過 | 通過（依存と lockfile を変えていないため UP-TO-DATE） |

Step 3 の単位のコマンド（変更の前）は、単体 205 件・結合 127 件がすべて通りました。

## 3. パッケージごとのカバレッジと一覧から外したパッケージ

`packagesJudgedByTotal` から `audit.domain`・`audit.service`・`auth.service`・`auth.web`・`user.domain`・`user.repository`・`user.service` を外しました。`audit.repository` はコードに手を入れていないため一覧に残しました。新しい `user.web` は一覧に無いため自動で対象です。計測の除外は足していません。

| パッケージ | Step 1 の行・分岐 | Step 21 の行・分岐 | 下限（行 80%・分岐 70%） |
|---|---|---|---|
| user.domain | 100.0%（25/25）・100.0%（16/16） | 99.5%（195/196）・97.5%（115/118） | 満たす |
| user.repository | 0/0（インターフェースだけ） | 0/0 | 満たす |
| user.service | 100.0%（94/94）・100.0%（28/28） | 100.0%（225/225）・95.5%（84/88） | 満たす |
| user.web（新しい） | — | 96.5%（55/57）・84.6%（11/13） | 満たす |
| audit.domain | 99.2%（128/129）・97.1%（33/34） | 99.4%（154/155）・97.4%（38/39） | 満たす |
| audit.service | 80.9%（76/94）・83.3%（10/12） | 100.0%（122/122）・95.5%（21/22） | 満たす |
| auth.service | 98.0%（197/201）・91.7%（55/60） | 98.0%（197/201）・91.7%（55/60） | 満たす |
| auth.web | 100.0%（118/118）・95.0%（19/20） | 100.0%（120/120）・95.0%（19/20） | 満たす |

`audit.service` の前の記録（行 77.2%）は Intent 260923-dsl-schema-loader の時点の値で、Step 1 では 80.9% でした。組み立てに失敗したときの項目の経路（既存の分岐を含む）のテストを足して 100% にしました。

## 4. 計画の9節の決定の反映

| 決定 | 反映 |
|---|---|
| 1 作業の場 | Step 1 で `feature/260925-user-management-b2` を作った。コミット・push はしていない。統合は squash |
| 2 契約 C4・C8 の差 | 実装は `security-design.md` 3節の `fieldErrors` と BR7.2 の `target_user_id` のとおり。`contract-summary.md` は書き換えず、差を README の「利用者のプリファレンスとパスワードの変更（U2）」の「契約との差」とこの文書（5節）に記録した |
| 3 既存のログのメールアドレス | **依頼者の判断で据え置き。** `InitialAdminInitializer` の INFO のキー `email` と、`AuditEventListener` の失敗の ERROR の `enteredEmail` は変えていない。`AuditWriteFailureIT` の期待も変えていない。`project.md` の Forbidden（メールアドレスをアプリのログに含めない、2026-09-25）との食い違いは残る。U2 が足したログ（PASSWORD_CHANGED の失敗の ERROR を含む）にはメールアドレスを載せていない（出来事がメールアドレスを持たない） |
| 4 止めて諮る場面 | (a) V6 までの Flyway の `validate`・`migrate` は V7 の後の内部DB で失敗しなかった（見込みどおり）。(b) 全パッケージが下限を満たした。(c) ArchUnit を緩めずに作れた。どの場面でも止めていない |

## 5. 契約との差（C4・C8）

- **C4**: 400 / `VALIDATION_FAILED` に `fieldErrors: [{field, reason}]` を足した。`reason` は `REQUIRED`・`TOO_SHORT`・`TOO_LONG`・`INVALID_CHARACTER`・`INVALID_VALUE`・`MISMATCH`。入れた値は載せない。本文が JSON として読めないときは今までどおり `MALFORMED_REQUEST`（`fieldErrors` なし）。
- **C8**: `PASSWORD_CHANGED` に `target_user_id`（本人と同じ値）を記録する。`actor_user_id` も本人。

## 6. 上流・計画との差（この段で決めたこと）

| 対象 | 計画・設計の形 | 実装 | 理由 |
|---|---|---|---|
| 言語・テーマ・文字の大きさの `null`・空 | 計画の Step 4 は「空・null は INVALID_VALUE、無い値は REQUIRED」 | `null` と空の文字列は `REQUIRED`、それ以外の一致しない値（`" "` を含む）は `INVALID_VALUE` | 承認済みの `security-design.md` 3節の表（REQUIRED＝項目が無い・null・空の文字列）に合わせた。Java の record では「項目が無い」と `null` を区別できない |
| 更新の問い合わせの引数 | 計画の Step 8 は4列の値・ハッシュを名前つきの引数で渡す形 | `updatePreferences(userId, Preferences)`・`updatePasswordHashIfUnchanged(userId, PasswordHash, PasswordHash)` とし、JPQL で SpEL（`:#{#preferences.displayName()}` など）で取り出す | メソッドの呼び出しの追跡（`TraceAspect`）が DB アクセスの層の引数を TRACE で文字列にするため、ハッシュ・氏名（既存の利用者はメールアドレスと同じ値）を `String` で渡すと漏れる。`PasswordHash`（新しい、文字列にすると伏せる）と `Preferences` で渡す。問い合わせは文字列の連結を使わない |
| 本人の利用者 ID の取り方 | `security-design.md` 2節の例は `currentUserId(Authentication auth)` | `MeRequestContextResolver.currentUserId()` が要求のスレッドの認証の文脈（`SecurityContextHolder`）から読む。コントローラーは `Authentication` を引数に取らない | 主体 `AuthenticatedUser` の文字列化はメールアドレスを含み、TRACE の追跡が引数を文字列にするため。判定の中身（認証が無い・匿名・数でない名前は 401）は設計どおり |
| 文字列化で伏せる項目 | 計画の 2.2 は `UserSummary` のメールアドレスを伏せる | `UserSummary`・`NewUser`・`Preferences`・`PreferencesCommand`・`PreferencesRequest`・`PreferencesResponse` で氏名も伏せる（`NewUser` はパスワードも） | 既存の利用者と初期管理者の氏名の初期値がメールアドレスのため。TRACE で確かめた（`MeSecretLeakIT`） |
| `AuditEvent` の作り方 | 計画の Step 6 は「作るときの引数」 | 静的な作り方 `AuditEvent.withTarget(...)` | 既存の DSL の10引数の作り方と引数の並びがぶつかり、`null` を渡すと呼び分けがあいまいになるため |
| `createUser` の旧い形 | — | 旧い `createUser(email, Password, admin)` と `User` の4引数の作り方は消した。既存のテストの呼び出し（18 ファイル）は `TestUserAccounts.create` に置き換えた | 呼び出しの道を C2 の1つにするため。確かめは緩めていない |
| `createUser` の前提の確かめ | BR5.1（氏名・値・パスワード） | メールアドレスの形式（`EmailAddress.isValid`）も確かめる | 契約 C2 の入力が `EmailAddress` のため |
| 一意の制約の受け止め | 「一意の制約（uk_users_email）に当たったとき」 | 制約の名前に `UK_USERS_EMAIL` を含む誤りだけを `EmailAlreadyUsed` にし、ほかの制約の誤りは伝える | ほかの誤りを登録済みと取り違えないため |
| 監査の失敗の ERROR の項目 | 生成に任せる（`observability-design.md` 2節） | 操作した人がいる出来事のうち、DSL の操作だけ DSL の項目を並べ、PASSWORD_CHANGED は `actorUserId` だけを足す。対象がある出来事は `targetUserId`・`targetInvitationId` を足す。既存の出来事の項目は変わらない | 空の DSL の項目を並べないため |
| 既存のテストの JDBC の追記 | — | `AuthSchemaIT`・`LoginAttemptStateRepositoryIT`・`RefreshTokenRepositoryIT` の利用者の追記に `display_name` を足した。`UserSchemaIT.requiredColumns` は `password_hash` だけが無い形に直し、`email` の必須も確かめる | V7 で `display_name` が必須になったため。直さないと、別の理由で失敗して元の確かめにならない |
| `LoginResponsePreferencesIT` の SQL の数 | 「変更の前と同じ」 | ログインと更新の SQL の種類と数を、実測の並びで固定した（ログイン5件・更新4件、利用者の表の読み取りはどちらも1回）。変更の前の実行とは比べていない | 変更の前の版を動かすには git の操作（worktree・stash）が要り、依頼の範囲の外のため。ログインと更新の問い合わせのコードは変えていない |
| `MeSecretLeakIT` の件数 | 5〜6 件 | 1件のテストに6つの場面（変更の入力の誤り・今のパスワードの誤り・成功、保存の成功・誤り、取得）をまとめた | 前準備のログインの後から出たログだけを確かめる形のため。場面の網羅は計画どおり |
| 監査の値の長さ | 既存の `AuditEventTest` か新しいテスト | 既存の `AuditEventTest` に足した | — |

- 計画の Step 2 の「設定の値と環境変数は足さない」はそのとおり（`application.yaml`・`.env.example` は変えていない）。
- 新しい依存は足していない。SpotBugs の除外（`backend/config/spotbugs-exclude.xml`）は変えていない。ArchUnit の境界テストは変えていない。

## 7. 既知の制約と Build and Test に引き継ぐこと

- **Hibernate の `validate` が余分な列を許すこと（承認の場の U2 R-02）**: 自動のテストでは1つ前の版のエンティティを持てないため、統合の前には確かめられない。既知の制約として記録する。戻しの練習（deployment-pipeline・deployment-execution）で外れたと分かった時点で、速やかに依頼者に諮る。
- 計画の「Build and Test に引き継ぐこと」の表のとおり（カバレッジと verify の時間の再実測、k6 の場面とパスワードの変更の専用の仮の利用者、性能と接続の測定、指標の名前、V7 の後方互換の実地の確かめ、バックアップ、契約の反映の確かめ、U3 の呼び出し元の前提）。
- 残る危険 R1（今のパスワードの総当たり）は受け入れ済み。見つけ方の問い合わせを README の U2 の節に置いた。
